// Compact "my team" match for the Android home-screen widget.
// GET /api/widget?team=ID -> { state: live|finished|upcoming|none, match: { id, league, kickoff, minute, home: {id,name,score}, away: {...} } }
const { fotmob } = require('../lib/fotmob');

const ymd = (d) => d.toISOString().slice(0, 10).replace(/-/g, '');
const side = (t) => ({ id: t.id, name: t.shortName || t.name, score: t.score ?? null });

module.exports = async (req, res) => {
  const id = (new URL(req.url, 'http://x').searchParams.get('team') || '').replace(/\D/g, '');
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (!id) { res.statusCode = 400; return res.end('{"error":"team"}'); }
  try {
    // today's and yesterday's lists (UTC) cover a match that is live right now or just finished
    const now = new Date();
    const lists = await Promise.all([new Date(now - 864e5), now].map((d) => fotmob('data/matches', { date: ymd(d), timezone: 'UTC' }).then(JSON.parse).catch(() => ({ leagues: [] }))));
    const found = [];
    for (const l of lists.flatMap((x) => x.leagues || [])) for (const m of l.matches || []) {
      if (String(m.home.id) === id || String(m.away.id) === id) found.push({ m, league: l.name });
    }
    const t = (x) => new Date(x.m.status.utcTime).getTime();
    const live = found.find((x) => x.m.status.started && !x.m.status.finished && !x.m.status.cancelled);
    const recent = found.filter((x) => x.m.status.finished && Date.now() - t(x) < 20 * 36e5).sort((a, b) => t(b) - t(a))[0];
    const today = found.filter((x) => !x.m.status.started && !x.m.status.cancelled).sort((a, b) => t(a) - t(b))[0];
    let pick = live || today || recent, state = live ? 'live' : today ? 'upcoming' : recent ? 'finished' : 'none';
    let match = null;
    if (pick) {
      const s = pick.m.status;
      match = { id: pick.m.id, league: pick.league, kickoff: t(pick), minute: (s.liveTime?.short || '').replace(/[‎‏]/g, '').replace('’', "'"),
        home: side(pick.m.home), away: side(pick.m.away) };
    } else {
      // nothing today: fall back to the team's next fixture
      const team = JSON.parse(await fotmob('data/teams', { id }));
      const n = team.overview?.nextMatch;
      if (n) { state = 'upcoming'; match = { id: n.id, league: n.tournament?.name || '', kickoff: new Date(n.status.utcTime).getTime(), minute: '', home: side(n.home), away: side(n.away) }; }
    }
    res.setHeader('Cache-Control', `public, s-maxage=${state === 'live' ? 20 : 300}, stale-while-revalidate=60`);
    res.end(JSON.stringify({ state, match }));
  } catch (e) {
    res.statusCode = 502;
    res.end(JSON.stringify({ error: e.message }));
  }
};
