#!/usr/bin/env python3
"""
Builds GoalLive's season rankings from every match of the big competitions, rated with the app's own formula (rate.py).

  python3 tools/rankings/build.py STORE.json OUT.json

STORE keeps each rated match (so a daily run only fetches the new ones); OUT is what the app reads:
the world's best players by GoalLive rating, the best of the Iranian league, the best per position, the Golden Shoe race,
GoalLive's own Ballon d'Or pick for the last full season, and the history of both awards.
"""
import concurrent.futures as cf
import datetime as dt
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rate import rate_match  # noqa: E402

UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36'
TOP5 = [47, 87, 55, 54, 53]
SEASONS = [
    # the last full season runs to the end of the World Cup, like the Ballon d'Or year
    dict(key='2025/2026', start='2025-08-01', end='2026-07-20', leagues=TOP5 + [42, 73, 77]),
    dict(key='2026/2027', start='2026-08-01', end=None, leagues=TOP5 + [42, 73]),
]
BIG_STAGE = {42: 1.5, 77: 1.5, 73: 1.2}
# the Iranian league has no line-ups or player numbers in the data, so it cannot be rated (kept out of the world list if it ever appears)  # Champions League and World Cup matches count more for the Ballon d'Or pick
IRAN = 523
NATIONAL = {77, 50, 44, 289}


def fotmob(path, **params):
    url = 'https://www.fotmob.com/api/' + path + ('?' + urllib.parse.urlencode(params) if params else '')
    for k in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json', 'Referer': 'https://www.fotmob.com/'})
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read())
        except Exception as e:  # noqa: BLE001
            if k == 3: raise
            time.sleep(1.5 * (k + 1))


def days(start, end):
    d = dt.date.fromisoformat(start)
    while d <= end:
        yield d
        d += dt.timedelta(days=1)


def collect(store):
    today = dt.datetime.utcnow().date()
    todo = []
    for s in SEASONS:
        end = min(today, dt.date.fromisoformat(s['end'])) if s['end'] else today
        for d in days(s['start'], end):
            key = d.strftime('%Y%m%d')
            if store['days'].get(key) == 'done': continue
            todo.append((key, s))
    print('days to scan', len(todo), flush=True)

    def scan(item):
        key, s = item
        try:
            data = fotmob('data/matches', date=key, timezone='UTC')
        except Exception as e:  # noqa: BLE001
            print('day failed', key, e, flush=True)
            return key, s, None
        found = []
        for lg in data.get('leagues') or []:
            lid = lg.get('primaryId') or lg.get('id')
            if lid not in s['leagues']: continue
            for m in lg.get('matches') or []:
                st = m.get('status') or {}
                found.append((m['id'], lid, bool(st.get('finished')) and not st.get('cancelled'), bool(st.get('cancelled'))))
        return key, s, found

    pending = []
    with cf.ThreadPoolExecutor(8) as ex:
        for key, s, found in ex.map(scan, todo):
            if found is None: continue
            unfinished = False
            for mid, lid, finished, cancelled in found:
                if cancelled: continue
                if not finished: unfinished = True; continue
                if str(mid) not in store['matches']: pending.append((mid, lid, key, s['key']))
            # a day is closed once every match in it is over (and it is not today)
            if not unfinished and key < today.strftime('%Y%m%d'): store['days'][key] = 'done'
    print('matches to rate', len(pending), flush=True)

    def rate(item):
        mid, lid, key, season = item
        try:
            players = rate_match(fotmob('data/matchDetails', matchId=mid))
        except Exception as e:  # noqa: BLE001
            print('match failed', mid, e, flush=True)
            return None
        return str(mid), dict(league=lid, date=key, season=season, players={
            str(pid): [p['name'], p['team'], p['teamName'], p['role'], p['minutes'], p['rating'], p['goals'], p['assists']] for pid, p in players.items()})

    with cf.ThreadPoolExecutor(8) as ex:
        for k, res in enumerate(ex.map(rate, pending)):
            if res: store['matches'][res[0]] = res[1]
            if k % 200 == 0: print('rated', k, flush=True)


ROLE_GROUP = {'GK': 'GK', 'CB': 'DEF', 'FB': 'DEF', 'DM': 'MID', 'CM': 'MID', 'AM': 'MID', 'W': 'ATT', 'ST': 'ATT'}


def aggregate(matches, keep, stage_weight=None):
    """Per player: apps, minutes, minute-weighted average rating (big stages weighted when asked), goals, assists, best match."""
    agg = {}
    for mid, m in matches.items():
        if not keep(m): continue
        sw = (stage_weight or {}).get(m['league'], 1.0)
        for pid, (name, team, team_name, role, mins, rating, goals, assists) in m['players'].items():
            a = agg.setdefault(pid, dict(id=int(pid), name=name, team=team, teamName=team_name, roles={}, apps=0, minutes=0, wsum=0.0, wmin=0.0,
                                         goals=0, assists=0, best=None, last=''))
            a['apps'] += 1; a['minutes'] += mins; a['goals'] += goals; a['assists'] += assists
            a['wsum'] += rating * mins * sw; a['wmin'] += mins * sw
            a['roles'][role] = a['roles'].get(role, 0) + mins
            # the club from his latest club game (a World Cup game would show the national team)
            if m['league'] not in NATIONAL and m['date'] >= a['last']: a['last'] = m['date']; a['team'] = team; a['teamName'] = team_name; a['name'] = name
            if mins >= 30 and (a['best'] is None or rating > a['best']['rating']): a['best'] = dict(match=int(mid), rating=rating, date=m['date'])
    return agg


def ranked(agg, min_minutes, prior=6.6, k=450, limit=100):
    out = []
    for a in agg.values():
        if a['minutes'] < min_minutes or a['wmin'] <= 0: continue
        avg = a['wsum'] / a['wmin']
        # a steady run of games beats one great afternoon: shrink towards an ordinary rating until the minutes pile up
        score = (a['wsum'] + prior * k) / (a['wmin'] + k)
        role = max(a['roles'], key=a['roles'].get)
        out.append(dict(id=a['id'], name=a['name'], team=a['team'], teamName=a['teamName'], role=role, group=ROLE_GROUP[role], apps=a['apps'],
                        minutes=a['minutes'], avg=round(avg, 2), score=round(score, 2), goals=a['goals'], assists=a['assists'], best=a['best']))
    out.sort(key=lambda x: -x['score'])
    return out[:limit] if limit else out


def golden_shoe(matches, season):
    """Goals in the big five leagues this season, times two (the Golden Shoe weight for Europe's strongest leagues)."""
    goals = {}
    for m in matches.values():
        if m['season'] != season or m['league'] not in TOP5: continue
        for pid, p in m['players'].items():
            if p[6] <= 0: continue
            g = goals.setdefault(pid, dict(id=int(pid), name=p[0], team=p[1], teamName=p[2], goals=0, league=m['league'], last=''))
            g['goals'] += p[6]
            if m['date'] >= g['last']: g.update(team=p[1], teamName=p[2], league=m['league'], last=m['date'])
    out = sorted(goals.values(), key=lambda x: -x['goals'])[:15]
    for g in out: g['points'] = g['goals'] * 2; g.pop('last')
    return out


def find_ids(awards):
    """FotMob ids for the award winners, so the app can show their photos."""
    cache = {}
    for lst in awards.values():
        for x in lst:
            for name in (x.get('name') or '').split(' · ')[:1]:
                if not name: continue
                if name not in cache:
                    try:
                        res = fotmob('data/search/suggest', term=name)
                        hit = next((s for g in res for s in g.get('suggestions') or [] if s.get('type') == 'player'), None)
                        cache[name] = int(hit['id']) if hit else None
                    except Exception:  # noqa: BLE001
                        cache[name] = None
                if cache[name]: x['id'] = cache[name]


def main(store_path, out_path):
    store = json.load(open(store_path)) if os.path.exists(store_path) else dict(days={}, matches={})
    collect(store)
    json.dump(store, open(store_path, 'w'), separators=(',', ':'))
    awards = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'awards.json')))
    find_ids(awards)
    seasons = {}
    for s in SEASONS:
        ms = {k: m for k, m in store['matches'].items() if m['season'] == s['key']}
        world = aggregate(ms, lambda m: m['league'] != IRAN)
        most = max([a['minutes'] for a in world.values()] or [0])
        floor = max(180, int(most * 0.3))
        top = ranked(world, floor, limit=None)
        entry = dict(matches=len(ms), world=top[:100], minMinutes=floor,
                     roles={g: [p for p in top if p['group'] == g][:15] for g in ('GK', 'DEF', 'MID', 'ATT')},
                     goldenShoe=golden_shoe(ms, s['key']))
        iran = aggregate(ms, lambda m: m['league'] == IRAN)
        if iran:
            im = max(a['minutes'] for a in iran.values())
            entry['iran'] = ranked(iran, max(180, int(im * 0.3)), limit=50)
        if s['end']:
            # GoalLive's Ballon d'Or: the whole season, Champions League and World Cup games weighing more, regulars only
            big = aggregate(ms, lambda m: m['league'] != IRAN, BIG_STAGE)
            entry['ballonDor'] = ranked(big, max(1800, int(most * 0.45)), prior=6.6, k=900, limit=10)
        seasons[s['key']] = entry
        print(s['key'], 'matches', len(ms), 'ranked', len(top), flush=True)
    out = dict(updated=dt.datetime.utcnow().strftime('%Y-%m-%dT%H:%MZ'), current=SEASONS[-1]['key'], last=SEASONS[0]['key'],
               seasons=seasons, awards=awards)
    json.dump(out, open(out_path, 'w'), ensure_ascii=False, separators=(',', ':'))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
