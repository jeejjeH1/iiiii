// GoalLive API v1: FotMob's data in small, stable, documented shapes, the same for the app, the website or anyone else.
// Every route takes ?lang=fa for Persian team, league and stat names (from the app's own tables, see tools/fa.py).
import fotmobLib from '../../lib/fotmob.js';
import FA from './fa.json';

const { fotmob } = fotmobLib;
const get = async (path, params) => JSON.parse(await fotmob(path, params));

const LEAGUES_FA = {
  523: 'لیگ برتر ایران', 47: 'لیگ برتر انگلیس', 87: 'لالیگا', 55: 'سری آ', 54: 'بوندسلیگا', 53: 'لوشامپیونه',
  42: 'لیگ قهرمانان اروپا', 73: 'لیگ اروپا', 10216: 'لیگ کنفرانس اروپا', 525: 'لیگ نخبگان آسیا', 77: 'جام جهانی', 50: 'یورو',
  44: 'کوپا آمریکا', 289: 'جام ملت‌های آسیا', 9907: 'لیگ دسته اول ایران', 9080: 'جام حذفی ایران', 536: 'لیگ ستارگان قطر', 461: 'لیگ عربستان',
};
/** Leagues listed by /v1/leagues, in the app's order (Iran first). */
const POPULAR = [523, 47, 87, 55, 54, 53, 42, 73, 10216, 525, 461, 77, 50, 289];
const LEAGUES_EN = {
  523: 'Persian Gulf Pro League', 47: 'Premier League', 87: 'LaLiga', 55: 'Serie A', 54: 'Bundesliga', 53: 'Ligue 1', 42: 'Champions League',
  73: 'Europa League', 10216: 'Conference League', 525: 'AFC Champions League Elite', 461: 'Saudi Pro League', 77: 'World Cup', 50: 'EURO', 289: 'Asian Cup',
};

const logo = (id) => `https://images.fotmob.com/image_resources/logo/teamlogo/${id}.png`;
const leagueLogo = (id) => `https://images.fotmob.com/image_resources/logo/leaguelogo/${id}.png`;
const photo = (id) => `https://images.fotmob.com/image_resources/playerimages/${id}.png`;
const num = (v) => (v == null || v === '' || isNaN(+v) ? null : +v);
const clean = (s) => String(s ?? '').replace(/[‎‏]/g, '').replace('’', "'");

/** Names in the asked language. */
function namer(lang) {
  const fa = lang === 'fa';
  return {
    fa,
    team: (id, name) => (fa && (FA.ids[id] || FA.teams[name])) || name || '',
    league: (id, name) => (fa && LEAGUES_FA[id]) || name || LEAGUES_EN[id] || '',
    word: (s) => (fa && (FA.words[s] || FA.words[String(s).trim()])) || s,
  };
}

const team = (n, id, name, score) => ({ id: +id, name: n.team(+id, name), logo: logo(id), ...(score !== undefined && { score: score ?? null }) });

function state(s = {}) {
  if (s.cancelled) return s.reason?.short === 'PP' || /postp/i.test(s.reason?.long || '') ? 'postponed' : 'cancelled';
  if (s.finished) return 'finished';
  if (s.started) return 'live';
  return 'upcoming';
}

/** One match from a match list (data/matches, team fixtures, search). */
function listMatch(n, m, league) {
  const s = m.status || {};
  const st = state(s);
  return {
    id: +m.id,
    league,
    kickoff: s.utcTime || null,
    status: st,
    minute: st === 'live' ? clean(s.liveTime?.short) || null : null,
    home: team(n, m.home.id, m.home.longName || m.home.name, st === 'upcoming' ? null : m.home.score),
    away: team(n, m.away.id, m.away.longName || m.away.name, st === 'upcoming' ? null : m.away.score),
    ...(s.reason?.short === 'Pen' || /pen/i.test(s.reason?.long || '') ? { decidedBy: 'penalties' } : {}),
  };
}

// --- dates: "today" is Tehran's day unless ?tz= says otherwise
function ymd(date, tz) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(date).replace(/-/g, '');
}
function dayParam(q, tz) {
  const raw = (q.get('date') || 'today').toLowerCase();
  const shift = { yesterday: -1, today: 0, tomorrow: 1 }[raw];
  if (shift !== undefined) return ymd(new Date(Date.now() + shift * 86400000), tz);
  const d = raw.replace(/\D/g, '');
  if (d.length !== 8) throw new HttpError(400, 'date must be YYYY-MM-DD, today, yesterday or tomorrow');
  return d;
}

class HttpError extends Error { constructor(status, msg) { super(msg); this.status = status; } }

async function matches(n, q) {
  const tz = q.get('tz') || 'Asia/Tehran';
  const date = dayParam(q, tz);
  const only = q.get('league') && new Set(q.get('league').split(',').map(Number));
  const d = await get('data/matches', { date, timezone: tz });
  const leagues = [];
  for (const l of d.leagues || []) {
    const id = l.primaryId ?? l.id;
    if (only && !only.has(id) && !only.has(l.id)) continue;
    const league = { id, name: n.league(id, l.name), country: l.ccode || null, logo: leagueLogo(id) };
    leagues.push({ ...league, matches: (l.matches || []).map((m) => listMatch(n, m, { id, name: league.name })) });
  }
  // Iran first, then the big leagues, then FotMob's order
  const rank = (id) => (POPULAR.includes(id) ? POPULAR.indexOf(id) : 99);
  leagues.sort((a, b) => rank(a.id) - rank(b.id));
  return { body: { date: `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6)}`, timezone: tz, leagues }, ttl: 15 };
}

async function live(n, q) {
  const { body } = await matches(n, new URLSearchParams({ date: 'today', tz: q.get('tz') || 'Asia/Tehran' }));
  return { body: { matches: body.leagues.flatMap((l) => l.matches).filter((m) => m.status === 'live') }, ttl: 10 };
}

function statGroups(n, c) {
  const groups = c.stats?.Periods?.All?.stats || [];
  return groups.map((g) => ({
    group: n.word(g.title),
    items: (g.stats || []).filter((s) => s.stats && s.type !== 'title').map((s) => ({
      key: s.key, name: n.word(s.title), home: num(s.stats[0]) ?? s.stats[0], away: num(s.stats[1]) ?? s.stats[1],
    })),
  })).filter((g) => g.items.length);
}

function lineupSide(n, t) {
  if (!t) return null;
  const p = (x) => ({
    id: +x.id, name: x.name, number: num(x.shirtNumber), position: x.positionId ?? null,
    rating: num(x.performance?.rating), captain: !!x.isCaptain || undefined,
    subbedIn: x.performance?.substitutionEvents?.find((e) => e.type === 'subIn')?.time ?? undefined,
    subbedOut: x.performance?.substitutionEvents?.find((e) => e.type === 'subOut')?.time ?? undefined,
  });
  return {
    formation: t.formation || null,
    coach: t.coach ? { id: +t.coach.id, name: t.coach.name } : null,
    rating: num(t.rating),
    starters: (t.starters || []).map(p),
    subs: (t.subs || []).map(p),
    unavailable: (t.unavailable || []).map((x) => ({ id: +x.id, name: x.name, reason: x.unavailability?.type || null, expectedReturn: x.unavailability?.expectedReturn || null })),
  };
}

function event(e) {
  const side = e.isHome ? 'home' : 'away';
  const minute = e.time ?? null;
  const added = e.overloadTime || null;
  switch (e.type) {
    case 'Goal': return {
      type: e.ownGoal ? 'ownGoal' : e.isPenaltyShootoutEvent ? 'shootoutGoal' : 'goal', minute, added, team: side,
      player: { id: +(e.player?.id ?? e.playerId), name: e.nameStr || e.player?.name },
      assist: e.assistInput ? { name: e.assistInput, id: num(e.assistPlayerId) } : null,
      penalty: /pen/i.test(e.goalDescription || e.suffix || '') || undefined,
      score: e.newScore ? { home: e.newScore[0], away: e.newScore[1] } : null,
    };
    case 'Card': return {
      type: e.card === 'Red' ? 'redCard' : e.card === 'YellowRed' ? 'secondYellow' : 'yellowCard', minute, added, team: side,
      player: { id: +e.player?.id, name: e.nameStr || e.player?.name },
    };
    case 'Substitution': return {
      type: 'substitution', minute, added, team: side, injury: !!e.injuredPlayerOut || undefined,
      in: e.swap?.[0] && { id: +e.swap[0].id, name: e.swap[0].name }, out: e.swap?.[1] && { id: +e.swap[1].id, name: e.swap[1].name },
    };
    case 'VAR': return { type: 'var', minute, added, team: side, decision: e.VAR?.decision?.value || null };
    case 'Half': return { type: 'period', label: e.halfStrShort, score: { home: e.homeScore, away: e.awayScore } };
    case 'AddedTime': return { type: 'addedTime', minute, minutes: num(e.minutesAddedInput) };
    default: return null;
  }
}

async function match(n, id) {
  const d = await get('data/matchDetails', { matchId: id });
  const g = d.general || {}, h = d.header || {}, c = d.content || {}, s = h.status || {};
  const [ht, at] = h.teams || [];
  if (!ht) throw new HttpError(404, 'no such match');
  const st = state(s);
  const info = c.matchFacts?.infoBox || {};
  const potm = c.matchFacts?.playerOfTheMatch;
  return {
    body: {
      id: +id,
      league: { id: g.parentLeagueId || g.leagueId, name: n.league(g.parentLeagueId || g.leagueId, g.leagueName), round: g.leagueRoundName || null },
      kickoff: s.utcTime || g.matchTimeUTCDate || null,
      status: st,
      minute: st === 'live' ? clean(s.liveTime?.short) || null : null,
      home: team(n, ht.id, ht.name, st === 'upcoming' ? null : ht.score),
      away: team(n, at.id, at.name, st === 'upcoming' ? null : at.score),
      penalties: s.reason?.penalties ? { home: s.reason.penalties[0], away: s.reason.penalties[1] } : null,
      venue: info.Stadium ? { name: info.Stadium.name, city: info.Stadium.city, capacity: info.Stadium.capacity ?? null } : null,
      referee: info.Referee?.text || null,
      attendance: info.Attendance ?? null,
      colors: g.teamColors?.darkMode ? { home: g.teamColors.darkMode.home, away: g.teamColors.darkMode.away } : null,
      playerOfTheMatch: potm?.id ? { id: potm.id, name: potm.name?.fullName || potm.name, team: potm.teamId === +ht.id ? 'home' : 'away', rating: num(potm.rating?.num) } : null,
      events: (c.matchFacts?.events?.events || []).map(event).filter(Boolean),
      stats: statGroups(n, c),
      lineups: c.lineup ? { home: lineupSide(n, c.lineup.homeTeam), away: lineupSide(n, c.lineup.awayTeam) } : null,
      shots: (c.shotmap?.shots || []).map((x) => ({
        team: x.teamId === +ht.id ? 'home' : 'away', player: { id: x.playerId, name: x.playerName }, minute: x.min,
        result: x.eventType, xg: num(x.expectedGoals), onTarget: !!x.isOnTarget, x: x.x, y: x.y,
      })),
      momentum: (c.momentum?.main?.data || c.matchFacts?.momentum?.main?.data || []).map((x) => ({ minute: x.minute, value: x.value })),
    },
    ttl: st === 'live' ? 10 : st === 'finished' ? 300 : 60,
  };
}

async function table(n, id) {
  const d = await get('data/tltable', { leagueId: id });
  const root = (Array.isArray(d) ? d[0] : d)?.data;
  if (!root) throw new HttpError(404, 'no table for this league');
  const row = (r) => {
    const [gf, ga] = String(r.scoresStr || '0-0').split('-').map(Number);
    return {
      rank: r.idx, team: team(n, r.id, r.name), played: r.played, won: r.wins, drawn: r.draws, lost: r.losses,
      goalsFor: gf, goalsAgainst: ga, goalDiff: r.goalConDiff, points: r.pts, zone: r.qualColor || null,
    };
  };
  // group stages (Champions League, World Cup) have one table per group
  const tables = root.composite
    ? (root.tables || []).map((t) => ({ name: t.leagueName, rows: (t.table?.all || []).map(row) }))
    : [{ name: null, rows: (root.table?.all || []).map(row) }];
  return {
    body: {
      league: { id: +id, name: n.league(+id, root.leagueName), logo: leagueLogo(id) },
      legend: (root.legend || []).map((l) => ({ name: l.title, color: l.color })),
      tables,
    },
    ttl: 120,
  };
}

async function teamInfo(n, id) {
  const d = await get('data/teams', { id });
  const det = d.details || {};
  if (!det.id) throw new HttpError(404, 'no such team');
  const o = d.overview || {};
  const fx = (m) => listMatch(n, { ...m, home: { ...m.home }, away: { ...m.away } }, m.tournament ? { id: m.tournament.leagueId, name: n.league(m.tournament.leagueId, m.tournament.name) } : null);
  const all = d.fixtures?.allFixtures?.fixtures || [];
  return {
    body: {
      id: det.id, name: n.team(det.id, det.name), shortName: det.shortName, logo: logo(det.id), country: det.country,
      league: det.primaryLeagueId ? { id: det.primaryLeagueId, name: n.league(det.primaryLeagueId, det.primaryLeagueName) } : null,
      venue: o.venue?.widget ? { name: o.venue.widget.name, city: o.venue.widget.city, capacity: (o.venue.statPairs || []).find((p) => p[0] === 'Capacity')?.[1] ?? null } : null,
      form: (o.teamForm || []).map((f) => f.resultString),
      next: o.nextMatch ? fx(o.nextMatch) : null,
      last: o.lastMatch ? fx(o.lastMatch) : null,
      fixtures: all.map(fx),
      squad: (d.squad?.squad || []).map((g) => ({
        group: g.title,
        players: (g.members || []).map((p) => ({
          id: p.id, name: p.name, photo: photo(p.id), number: p.shirtNumber ?? null, age: p.age ?? null, country: p.cname || null,
          position: p.positionIdsDesc || p.role?.fallback || null, rating: num(p.rating), goals: p.goals ?? null, assists: p.assists ?? null,
          injured: p.injury ? { reason: p.injury.injuryId ? p.injury.name || 'injury' : 'injury', expectedReturn: p.injury.expectedReturn || null } : null,
        })),
      })),
    },
    ttl: 600,
  };
}

async function player(n, id) {
  const d = await get('data/playerData', { id });
  if (!d.id) throw new HttpError(404, 'no such player');
  const info = Object.fromEntries((d.playerInformation || []).map((x) => [x.title, x.value?.fallback ?? x.value?.numberValue]));
  const ml = d.mainLeague;
  return {
    body: {
      id: d.id, name: d.name, photo: photo(d.id),
      team: d.primaryTeam ? team(n, d.primaryTeam.teamId, d.primaryTeam.teamName) : null,
      position: d.positionDescription?.primaryPosition?.label || null,
      birthDate: d.birthDate?.utcTime?.slice(0, 10) || null,
      age: num(info.Age), height: info.Height || null, shirt: num(info.Shirt), foot: info['Preferred foot'] || null,
      country: info.Country || null, marketValue: info['Market value'] || null,
      contractEnd: info['Contract end']?.utcTime?.slice(0, 10) || null,
      injury: d.injuryInformation ? { reason: d.injuryInformation.name || null, expectedReturn: d.injuryInformation.expectedReturn?.expectedReturnFallback || null } : null,
      season: ml ? {
        league: { id: ml.leagueId, name: n.league(ml.leagueId, ml.leagueName) }, season: ml.season,
        // goals, assists, started, matches, minutesPlayed, rating, yellowCards, redCards
        stats: Object.fromEntries((ml.stats || []).map((s) => [
          (s.localizedTitleId || s.title).replace(/_uppercase$/, '').replace(/[_ ]+(\w)/g, (_, c) => c.toUpperCase()), s.value,
        ])),
      } : null,
      recentMatches: (d.recentMatches || []).slice(0, 10).map((m) => ({
        id: m.id, date: m.matchDate?.utcTime || null, opponent: m.opponentTeamId ? team(n, m.opponentTeamId, m.opponentTeamName) : null,
        league: m.leagueId ? { id: m.leagueId, name: n.league(m.leagueId, m.leagueName) } : null, home: !!m.isHomeTeam,
        score: { home: m.homeScore ?? null, away: m.awayScore ?? null },
        rating: num(m.ratingProps?.rating), goals: m.goals ?? null, assists: m.assists ?? null, minutes: m.minutesPlayed ?? null,
        playerOfTheMatch: !!m.playerOfTheMatch || undefined,
      })),
    },
    ttl: 1800,
  };
}

async function search(n, q) {
  const term = (q.get('q') || '').trim().slice(0, 60);
  if (term.length < 2) throw new HttpError(400, 'q must be at least 2 letters');
  const d = await get('data/search/suggest', { term });
  const all = (Array.isArray(d) ? d : []).flatMap((g) => g.suggestions || []);
  const out = { teams: [], players: [], leagues: [], matches: [] };
  const seen = new Set();
  for (const s of all) {
    // the same team or player comes back in several of FotMob's groups
    if (seen.has(s.type + s.id)) continue;
    seen.add(s.type + s.id);
    if (s.type === 'team') out.teams.push({ ...team(n, s.id, s.name), league: s.leagueId ? { id: s.leagueId, name: n.league(s.leagueId, s.leagueName) } : null });
    else if (s.type === 'player') out.players.push({ id: +s.id, name: s.name, photo: photo(s.id), team: s.teamId ? team(n, s.teamId, s.teamName) : null });
    else if (s.type === 'league') out.leagues.push({ id: +s.id, name: n.league(+s.id, s.name), logo: leagueLogo(s.id), country: s.ccode || null });
    else if (s.type === 'match') out.matches.push(listMatch(n, {
      id: s.id, status: s.status, home: { id: s.homeTeamId, name: s.homeTeamName, score: s.homeTeamScore }, away: { id: s.awayTeamId, name: s.awayTeamName, score: s.awayTeamScore },
    }, s.leagueId ? { id: s.leagueId, name: n.league(s.leagueId, s.leagueName) } : null));
  }
  return { body: { query: term, ...out }, ttl: 300 };
}

function leagues(n) {
  return { body: { leagues: POPULAR.map((id) => ({ id, name: n.league(id, LEAGUES_EN[id]), logo: leagueLogo(id) })) }, ttl: 86400 };
}

export const DOCS = {
  name: 'GoalLive API',
  version: 1,
  note: 'Add ?lang=fa to any route for Persian team, league and stat names. Times are UTC (ISO 8601). Data from FotMob.',
  routes: {
    'GET /v1/leagues': 'popular leagues (Iran first)',
    'GET /v1/matches?date=today|yesterday|tomorrow|YYYY-MM-DD&league=47,87&tz=Asia/Tehran': 'matches of a day, grouped by league',
    'GET /v1/live': 'matches being played right now',
    'GET /v1/matches/{id}': 'one match: score, events, stats, lineups, shots, momentum',
    'GET /v1/leagues/{id}/table': 'league table (one per group in cup group stages)',
    'GET /v1/teams/{id}': 'team: form, next and last match, all fixtures, squad',
    'GET /v1/players/{id}': 'player: profile, season stats, recent matches',
    'GET /v1/search?q=persepolis': 'teams, players, leagues and matches',
  },
};

/** Answers /v1/... : { body, ttl } or throws HttpError. */
export async function v1(url) {
  const n = namer(url.searchParams.get('lang'));
  const q = url.searchParams;
  const p = url.pathname.replace(/\/+$/, '').split('/').slice(2); // after "/v1"
  const id = (s) => { if (!/^\d+$/.test(s || '')) throw new HttpError(400, 'id must be a number'); return s; };
  if (p.length === 0) return { body: DOCS, ttl: 3600 };
  if (p[0] === 'leagues' && p.length === 1) return leagues(n);
  if (p[0] === 'leagues' && p[2] === 'table' && p.length === 3) return table(n, id(p[1]));
  if (p[0] === 'matches' && p.length === 1) return matches(n, q);
  if (p[0] === 'matches' && p.length === 2) return match(n, id(p[1]));
  if (p[0] === 'live' && p.length === 1) return live(n, q);
  if (p[0] === 'teams' && p.length === 2) return teamInfo(n, id(p[1]));
  if (p[0] === 'players' && p.length === 2) return player(n, id(p[1]));
  if (p[0] === 'search' && p.length === 1) return search(n, q);
  throw new HttpError(404, 'unknown route, see /v1');
}
export { HttpError };
