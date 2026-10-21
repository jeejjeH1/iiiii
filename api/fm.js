// Vercel serverless function: allowlisted proxy to FotMob (browsers can't call it directly: no CORS).
// GET /api/fm?e=<endpoint>&<params>   e.g. /api/fm?e=matchDetails&matchId=123
// Also used by server.js for local development, so it only relies on plain Node req/res.
const { fotmob } = require('../lib/fotmob');

const ENDPOINTS = {
  matches: { path: 'data/matches', params: ['date', 'timezone', 'ccode3'], ttl: 15 },
  matchDetails: { path: 'data/matchDetails', params: ['matchId'], ttl: 15 },
  tltable: { path: 'data/tltable', params: ['leagueId'], ttl: 60 },
  leagues: { path: 'data/leagues', params: ['id', 'season', 'ccode3'], ttl: 300 },
  teams: { path: 'data/teams', params: ['id', 'ccode3'], ttl: 300 },
  playerData: { path: 'data/playerData', params: ['id'], ttl: 600 },
  suggest: { path: 'data/search/suggest', params: ['term'], ttl: 300 },
  allLeagues: { path: 'data/allLeagues', params: ['locale', 'country'], ttl: 86400 },
};

// Drop the large parts of each payload the app never reads (SEO blobs, transfers, duplicated overviews).
const omit = (o, keys) => { for (const k of keys) delete o[k]; return o; };
const TRIM = {
  matchDetails: (d) => omit(d, ['seo']),
  leagues: (d) => {
    omit(d, ['overview', 'transfers', 'QAData', 'seostr']);
    if (d.stats) omit(d.stats, ['seasonStatLinks', 'seasonsWithLinks']);
    return d;
  },
  teams: (d) => {
    omit(d, ['transfers', 'QAData', 'seostr', 'stats']);
    if (d.overview) omit(d.overview, ['table', 'transfers', 'squad', 'overviewFixtures', 'newsSummary', 'featuredArticle']);
    return d;
  },
  playerData: (d) => omit(d, ['meta', 'relatedLinksData', 'matchFilters', 'initialMatchesPage', 'seostr']),
};

function send(res, status, body, cache) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (cache) res.setHeader('Cache-Control', cache);
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

module.exports = async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const ep = ENDPOINTS[url.searchParams.get('e')];
  if (!ep) return send(res, 400, { error: 'unknown endpoint' });

  const q = new URLSearchParams();
  for (const p of ep.params) {
    const v = url.searchParams.get(p);
    if (v) q.set(p, v.slice(0, 80));
  }
  try {
    const name = url.searchParams.get('e');
    let body = await fotmob(ep.path, q);
    let ttl = ep.ttl;
    if (TRIM[name] || name === 'matchDetails') {
      const d = JSON.parse(body);
      if (TRIM[name]) TRIM[name](d);
      // finished matches barely change; only live/upcoming ones need the short cache
      if (name === 'matchDetails' && d.header?.status?.finished) ttl = 600;
      body = JSON.stringify(d);
    }
    // shared edge cache, then serve stale while revalidating
    send(res, 200, body, `public, s-maxage=${ttl}, stale-while-revalidate=${ttl * 4}`);
  } catch (e) {
    send(res, 502, { error: e.message });
  }
};
