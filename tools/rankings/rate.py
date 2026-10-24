"""GoalLive match rating, the same formula as the app (android/.../ui/Rating.kt): keep the two in step."""
import math

WEIGHTS = {
    'GK': {'pas': 0.6, 'de': 0.6, 'du': 0.6},
    'CB': {'pas': 0.9, 'de': 1.2, 'du': 1.1},
    'FB': {'cre': 1.1, 'pas': 0.9, 'team': 0.8},
    'DM': {'pas': 1.1, 'de': 1.1, 'team': 0.5},
    'CM': {'cre': 1.1, 'de': 0.9, 'du': 0.9, 'team': 0.3},
    'AM': {'cre': 1.2, 'pas': 0.9, 'de': 0.7, 'du': 0.8, 'team': 0.2},
    'W': {'cre': 1.1, 'pas': 0.8, 'de': 0.7, 'du': 0.8, 'team': 0.1},
    'ST': {'pas': 0.7, 'de': 0.6, 'du': 0.9, 'team': 0.1},
}
GOAL = {'GK': 1.6, 'CB': 1.4, 'FB': 1.3, 'DM': 1.25, 'CM': 1.15, 'AM': 1.0, 'W': 1.0, 'ST': 0.9}
FIT_A, FIT_B = 6.5, 0.62
KNEE, ABOVE = 8.0, 0.7
# evens out the roles, so a centre-back is not marked lower than a winger for an equally good game
ROLE_FIX = {'GK': -0.3, 'CB': 0.25, 'FB': 0.15, 'DM': 0.05}
# what a player in each role usually does in 90 minutes (FotMob, top five leagues 2026/27): only more than that earns points
BASE = {
    'GK': dict(tk=0.0, ic=0.0, cl=1.2, rc=8.8, bl=0.0, box=0.0, kp=0.0, dr=0.0, lbp=2.9, f3=5.2, ap=23.6, aw=0.3, gw=0.2, lb=6.2, cr=0.0, acc=0.70),
    'CB': dict(tk=1.5, ic=1.3, cl=5.5, rc=3.8, bl=0.8, box=1.0, kp=0.3, dr=0.2, lbp=4.1, f3=6.0, ap=50.1, aw=2.4, gw=2.4, lb=2.6, cr=0.1, acc=0.88),
    'FB': dict(tk=2.0, ic=1.0, cl=2.7, rc=4.0, bl=0.4, box=1.3, kp=1.0, dr=0.5, lbp=3.3, f3=5.6, ap=34.3, aw=1.0, gw=3.5, lb=1.5, cr=0.7, acc=0.83),
    'DM': dict(tk=1.9, ic=1.2, cl=1.6, rc=4.7, bl=0.4, box=1.4, kp=1.0, dr=0.7, lbp=4.5, f3=6.4, ap=42.6, aw=0.9, gw=3.6, lb=2.0, cr=0.4, acc=0.86),
    'CM': dict(tk=1.9, ic=1.0, cl=1.4, rc=4.4, bl=0.3, box=2.1, kp=1.1, dr=0.7, lbp=2.1, f3=4.7, ap=32.2, aw=0.8, gw=3.8, lb=1.5, cr=0.6, acc=0.84),
    'AM': dict(tk=1.1, ic=0.4, cl=0.6, rc=3.7, bl=0.1, box=3.6, kp=1.5, dr=1.3, lbp=2.9, f3=4.1, ap=29.6, aw=0.7, gw=4.0, lb=1.2, cr=0.5, acc=0.83),
    'W': dict(tk=1.5, ic=0.6, cl=0.7, rc=3.8, bl=0.1, box=4.0, kp=1.6, dr=1.4, lbp=2.0, f3=2.9, ap=24.2, aw=0.7, gw=4.2, lb=1.0, cr=0.7, acc=0.81),
    'ST': dict(tk=0.7, ic=0.2, cl=0.9, rc=2.2, bl=0.1, box=4.8, kp=1.1, dr=0.8, lbp=0.8, f3=2.1, ap=14.7, aw=2.1, gw=2.8, lb=0.5, cr=0.2, acc=0.76),
}


def role_of(position_id, usual):
    if position_id is None:
        return {0: 'GK', 1: 'CB', 3: 'ST'}.get(usual, 'CM')
    p, last = position_id, position_id % 10
    if p < 20: return 'GK'
    if p < 60: return 'FB' if last in (1, 2, 8, 9) or p // 10 == 5 else 'CB'
    if p < 70: return 'DM'
    if p < 80: return 'CM'
    if p < 100: return 'W' if last in (2, 3, 7, 8) else 'AM'
    return 'W' if p < 110 and last in (1, 2, 3, 7, 8, 9) else 'ST'


def state_weight(diff, minute, kind):
    if kind == 'goal':
        w = {-1: 1.3, 0: 1.35, 1: 1.05}.get(diff, 0.75 if diff >= 2 else 0.95)
        if minute >= 80 and diff in (-1, 0): w *= 1.25
        return w
    if kind == 'red': return 1.25 if -1 <= diff <= 1 else 0.75
    if kind == 'save': return 1.3 if diff in (0, 1) else (1.0 if diff == -1 else 0.7)
    return 1.0


def is_pen(e):
    return (e.get('goalDescriptionKey') == 'penalty' or e.get('suffixKey') == 'penalty_short'
            or 'pen' in (e.get('suffix') or '').lower() or (e.get('shotmapEvent') or {}).get('situation') == 'Penalty')


def rate_match(d):
    """{player id: dict(name, team id, home, role, minutes, rating, goals, assists)} for one matchDetails answer."""
    c = d.get('content') or {}
    stats = c.get('playerStats') or {}
    lu = c.get('lineup')
    if not stats or not lu: return {}
    events = ((c.get('matchFacts') or {}).get('events') or {}).get('events') or []
    shots = (c.get('shotmap') or {}).get('shots') or []
    goals, h = [], 0
    for e in events:
        if e.get('type') != 'Goal' or e.get('isPenaltyShootoutEvent'): continue
        ns = e.get('newScore')
        home_scored = ns[0] > h if ns and len(ns) == 2 else bool(e.get('isHome'))
        if ns and len(ns) == 2: h = ns[0]
        goals.append((e.get('time') or 0, home_scored, e))

    def diff_at(minute, home):
        hh = sum(1 for t, s, _ in goals if s and t < minute); aa = sum(1 for t, s, _ in goals if not s and t < minute)
        return hh - aa if home else aa - hh

    out = {}
    for side, home in (('homeTeam', True), ('awayTeam', False)):
        team = lu.get(side) or {}
        for p, starter in [(x, True) for x in team.get('starters') or []] + [(x, False) for x in team.get('subs') or []]:
            pid = p.get('id')
            ps = stats.get(str(pid))
            if not ps: continue
            st = {}
            for g in ps.get('stats') or []:
                for t, one in (g.get('stats') or {}).items():
                    s = one.get('stat') or {}
                    if one.get('key'): st[one['key']] = (s.get('value') or 0, s.get('total') or 0)
            v = lambda k: st.get(k, (0, 0))[0] or 0
            tot = lambda k: st.get(k, (0, 0))[1] or 0
            mins = int(v('minutes_played'))
            if mins <= 0: continue
            subs = (p.get('performance') or {}).get('substitutionEvents') or []
            tin = next((s.get('time') for s in subs if s.get('type') == 'subIn'), 0 if starter else None)
            if tin is None: continue
            tout = next((s.get('time') for s in subs if s.get('type') == 'subOut'), 200)
            role = role_of(p.get('positionId') if starter else None, p.get('usualPlayingPositionId'))
            if ps.get('isGoalkeeper'): role = 'GK'
            scale = min(2.0, math.sqrt(90.0 / max(mins, 20)))
            items = []
            add = lambda g, pts: items.append((g, pts)) if abs(pts) >= 0.005 else None
            n_goals = n_assists = 0
            for t, hs, e in goals:
                if e.get('playerId') == pid and not e.get('ownGoal'):
                    n_goals += 1
                    add('att', 1.15 * GOAL[role] * state_weight(diff_at(t, home), t, 'goal') * (0.75 if is_pen(e) else 1.0))
                if e.get('assistPlayerId') == pid:
                    n_assists += 1
                    add('cre', 0.85 * state_weight(diff_at(t, home), t, 'goal'))
                if e.get('ownGoal') and e.get('playerId') == pid: add('disc', -1.2)
            base = BASE[role]; f90 = mins / 90.0
            # a count against what the role usually does in these minutes: more earns up to [cap], less costs up to [floor]
            def over(val, key, per, cap, floor=0.2):
                return max(-floor, min(cap, per * (val - base[key] * f90) * (scale if val > base[key] * f90 else 1.0)))
            xg = v('expected_goals_non_penalty') or v('expected_goals')
            add('att', 0.45 * xg)
            add('att', 0.35 * (v('expected_goals_on_target_variant') - v('expected_goals')))
            add('att', min(0.4, 0.08 * v('ShotsOnTarget') * scale))
            add('att', -0.05 * v('ShotsOffTarget'))
            add('att', -0.3 * v('big_chance_missed_title'))
            dr, drt = v('dribbles_succeeded'), tot('dribbles_succeeded')
            add('att', over(dr, 'dr', 0.1, 0.5, 0.1)); add('att', -0.07 * max(0, drt - dr))
            add('att', over(v('touches_opp_box'), 'box', 0.03, 0.3, 0.15))
            add('att', min(0.25, 0.04 * v('was_fouled')))
            add('att', -0.06 * v('Offsides')); add('att', -0.08 * v('dispossessed'))
            missed = sum(1 for s in shots if s.get('playerId') == pid and s.get('situation') == 'Penalty' and s.get('eventType') != 'Goal')
            add('att', -0.9 * missed)
            add('cre', 0.5 * v('expected_assists'))
            add('cre', over(v('chances_created'), 'kp', 0.13, 0.6, 0.15))
            add('cre', 0.2 * v('big_chance_created_team_title'))
            ap, at = v('accurate_passes'), tot('accurate_passes')
            if at >= 8: add('pas', max(-0.45, min(0.3, (ap / at - base['acc']) * 2.0)) * min(1.0, at / 35))
            add('pas', over(ap, 'ap', 0.006, 0.3, 0.2))
            add('pas', over(v('line_breaking_passes'), 'lbp', 0.04, 0.4))
            add('pas', over(v('passes_into_final_third'), 'f3', 0.03, 0.3))
            lb, lbt = v('long_balls_accurate'), tot('long_balls_accurate')
            add('pas', over(lb, 'lb', 0.035, 0.25, 0.1) - 0.025 * max(0, lbt - lb))
            cr, crt = v('accurate_crosses'), tot('accurate_crosses')
            add('pas', over(cr, 'cr', 0.07, 0.3, 0.1) - 0.03 * max(0, crt - cr))
            add('de', over(v('matchstats.headers.tackles'), 'tk', 0.1, 0.5))
            add('de', over(v('interceptions'), 'ic', 0.1, 0.5))
            add('de', over(v('shot_blocks'), 'bl', 0.12, 0.4, 0.1))
            add('de', over(v('clearances'), 'cl', 0.04, 0.4))
            add('de', over(v('recoveries'), 'rc', 0.03, 0.3))
            add('de', 0.5 * v('last_man_tackle'))
            add('de', -0.12 * v('dribbled_past'))
            aw, awt, gw, gwt = v('aerials_won'), tot('aerials_won'), v('ground_duels_won'), tot('ground_duels_won')
            add('du', over(aw, 'aw', 0.06, 0.35, 0.1)); add('du', -0.05 * max(0, awt - aw))
            add('du', over(gw, 'gw', 0.05, 0.35, 0.1)); add('du', -0.05 * max(0, gwt - gw))
            add('du', -0.08 * v('fouls'))
            add('disc', -0.6 * v('conceded_penalties'))
            if role == 'GK':
                saves = [s for s in shots if s.get('keeperId') == pid and s.get('isOnTarget') and s.get('eventType') != 'Goal' and not s.get('isBlocked')]
                sv = 0.2 * v('saves') if not saves else sum(
                    0.18 * (1 + 2 * (s.get('expectedGoalsOnTarget') or 0)) * state_weight(diff_at(s.get('min') or 0, home), s.get('min') or 0, 'save') for s in saves)
                add('gk', sv); add('gk', 0.55 * v('goals_prevented')); add('gk', 0.08 * v('keeper_high_claim'))
                add('gk', 0.04 * v('punches')); add('gk', 0.08 * v('keeper_sweeper'))
            conceded = sum(1 for t, s, _ in goals if s != home and tin <= t <= tout)
            on = min(tout, 95) - tin
            cs = {'GK': 0.6, 'CB': 0.5, 'FB': 0.4, 'DM': 0.25, 'CM': 0.12}.get(role)
            if cs and on >= 60 and conceded == 0: add('team', cs)
            per = {'GK': 0.35, 'CB': 0.3, 'FB': 0.25, 'DM': 0.12}.get(role)
            if per: add('team', -per * conceded)
            fd = diff_at(1000, home)
            add('team', 0.2 if fd > 0 else (-0.35 if fd <= -3 else (-0.2 if fd < 0 else 0)))
            for e in events:
                if e.get('type') == 'Card' and (e.get('player') or {}).get('id') == pid:
                    t = e.get('time') or 0
                    add('disc', -0.35 if e.get('card') == 'Yellow' else -1.8 * state_weight(diff_at(t, home), t, 'red'))
            add('disc', -1.6 * v('errors_led_to_goal'))
            dist = v('physical_metrics_distance_covered')
            if dist and mins >= 20: add('phys', max(-0.1, min(0.15, (dist / mins * 90 - 10500) / 10000)))
            spr = v('physical_metrics_number_of_sprints')
            if spr and mins >= 20: add('phys', max(0.0, min(0.1, (spr / mins * 90 - 20) / 100)))
            # 9 and above only for a match-deciding performance
            decisive = any((e.get('playerId') == pid and not e.get('ownGoal') or e.get('assistPlayerId') == pid) and diff_at(t, home) in (-1, 0)
                           for t, hs, e in goals if hs == home) or n_goals + n_assists >= 3 or (role == 'GK' and v('goals_prevented') >= 1.0 and fd >= 0)
            w = WEIGHTS[role]
            r = FIT_A + ROLE_FIX.get(role, 0.0) + sum(pts * w.get(g, 1.0) * FIT_B for g, pts in items)
            if r > KNEE: r = KNEE + (r - KNEE) * ABOVE
            if not decisive: r = min(r, 8.9)
            r = max(3.0, min(10.0, r))
            out[pid] = dict(name=p.get('name') or '', team=team.get('id'), teamName=team.get('name') or '', home=home, role=role,
                            minutes=mins, rating=round(r, 2), goals=n_goals, assists=n_assists)
    return out
