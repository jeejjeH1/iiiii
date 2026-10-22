// Compact list of today's matches for the Android "today" widget.
// GET /api/today?teams=8634,8633  ->  { matches: [{ id, league, kickoff, state, minute, home: {id,name,score}, away }] }
// Followed teams' matches first, then live and upcoming games from the big leagues; at most 5.
const { fotmob } = require('../lib/fotmob');

const BIG = new Set([42, 47, 87, 55, 54, 53, 73, 523, 77, 50, 44, 10216]);
// "today" as the user in Iran sees it
const ymd = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tehran' }).format(d).replace(/-/g, '');
const side = (t) => ({ id: t.id, name: t.shortName || t.name, score: t.score ?? null });

module.exports = async (req, res) => {
  const u = new URL(req.url, 'http://x');
  const teams = new Set((u.searchParams.get('teams') || '').split(',').map((x) => x.replace(/\D/g, '')).filter(Boolean));
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  try {
    const d = JSON.parse(await fotmob('data/matches', { date: ymd(new Date()), timezone: 'Asia/Tehran' }));
    const all = [];
    for (const l of d.leagues || []) for (const m of l.matches || []) {
      const s = m.status || {};
      if (s.cancelled) continue;
      const state = s.started && !s.finished ? 'live' : s.finished ? 'finished' : 'upcoming';
      const mine = teams.has(String(m.home.id)) || teams.has(String(m.away.id));
      const big = BIG.has(l.primaryId);
      if (!mine && !big) continue;
      all.push({
        id: m.id, league: l.name, kickoff: new Date(s.utcTime).getTime(), state,
        minute: (s.liveTime?.short || '').replace(/[‎‏]/g, '').replace('’', "'"),
        home: side(m.home), away: side(m.away), _rank: (mine ? 0 : 10) + (state === 'live' ? 0 : state === 'upcoming' ? 1 : 2),
      });
    }
    all.sort((a, b) => a._rank - b._rank || a.kickoff - b.kickoff);
    const matches = all.slice(0, 5).map(({ _rank, ...m }) => m);
    res.setHeader('Cache-Control', `public, s-maxage=${matches.some((m) => m.state === 'live') ? 20 : 120}, stale-while-revalidate=60`);
    res.end(JSON.stringify({ matches }));
  } catch (e) {
    res.statusCode = 502;
    res.end(JSON.stringify({ error: e.message }));
  }
};
