// Web Push for followed teams: subscriptions + a poller that turns FotMob changes into notifications.
//   GET  /api/push?a=key            -> { key }            VAPID public key (generated once, kept in Blob storage)
//   POST /api/push?a=sub            -> save/refresh a subscription with what it follows
//   POST /api/push?a=unsub          -> remove a subscription
//   POST /api/push?a=test           -> send a test notification to one subscription
//   GET  /api/push?a=tick           -> poll FotMob once and send what changed (called every ~30s by a scheduler)
const crypto = require('crypto');
const webpush = require('web-push');
const { fotmob } = require('../lib/fotmob');

/* ---------- storage: Vercel Blob (private) in production, a temp folder locally ---------- */
const store = process.env.BLOB_READ_WRITE_TOKEN ? (() => {
  const blob = require('@vercel/blob');
  return {
    async read(path) {
      try {
        const r = await blob.get(path, { access: 'private', useCache: false });
        return r ? JSON.parse(await new Response(r.stream).text()) : null;
      } catch (e) { if (e instanceof blob.BlobNotFoundError) return null; throw e; }
    },
    async write(path, data, { overwrite = true } = {}) {
      await blob.put(path, JSON.stringify(data), { access: 'private', addRandomSuffix: false, allowOverwrite: overwrite, contentType: 'application/json' });
    },
    async del(path) { await blob.del(path).catch(() => {}); },
    async list(prefix) {
      const out = [];
      let cursor;
      do { const r = await blob.list({ prefix, cursor }); out.push(...r.blobs.map((b) => b.pathname)); cursor = r.hasMore ? r.cursor : null; } while (cursor);
      return out;
    },
  };
})() : (() => {
  const fs = require('fs'), path = require('path'), os = require('os');
  const dir = path.join(os.tmpdir(), 'goallive-push');
  const file = (p) => path.join(dir, p.replace(/\//g, '__'));
  fs.mkdirSync(dir, { recursive: true });
  return {
    async read(p) { try { return JSON.parse(fs.readFileSync(file(p), 'utf8')); } catch { return null; } },
    async write(p, d, { overwrite = true } = {}) { fs.mkdirSync(dir, { recursive: true }); if (!overwrite && fs.existsSync(file(p))) throw new Error('exists'); fs.writeFileSync(file(p), JSON.stringify(d)); },
    async del(p) { fs.rmSync(file(p), { force: true }); },
    async list(prefix) { return (fs.existsSync(dir) ? fs.readdirSync(dir) : []).map((f) => f.replace(/__/g, '/')).filter((f) => f.startsWith(prefix)); },
  };
})();

async function vapid() {
  let k = await store.read('push/vapid.json');
  if (!k) {
    const fresh = webpush.generateVAPIDKeys();
    // first writer wins; anyone racing re-reads the stored pair
    try { await store.write('push/vapid.json', fresh, { overwrite: false }); k = fresh; } catch { k = await store.read('push/vapid.json'); }
  }
  webpush.setVapidDetails('mailto:push@goallive.app', k.publicKey, k.privateKey);
  return k;
}

const subPath = (endpoint) => `push/subs/${crypto.createHash('sha256').update(endpoint).digest('hex').slice(0, 32)}.json`;
const ids = (a) => (Array.isArray(a) ? a.map(String).filter((x) => /^\d{1,10}$/.test(x)).slice(0, 200) : []);

async function readBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  let raw = '';
  for await (const c of req) { raw += c; if (raw.length > 50000) throw new Error('too large'); }
  return raw ? JSON.parse(raw) : {};
}
function send(res, status, body) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.end(JSON.stringify(body));
}

async function push(sub, payload) {
  try {
    await webpush.sendNotification(sub.sub, JSON.stringify(payload), { TTL: 900, urgency: 'high' });
    return true;
  } catch (e) {
    if (e.statusCode === 404 || e.statusCode === 410) await store.del(subPath(sub.sub.endpoint)); // expired subscription
    return false;
  }
}

/* ---------- the poller ---------- */
const ymd = (d) => d.toISOString().slice(0, 10).replace(/-/g, '');
const live = (s) => s.started && !s.finished && !s.cancelled;
const follows = (sub, m, leagueId) => sub.matches.includes(String(m.id)) || sub.teams.includes(String(m.home.id)) ||
  sub.teams.includes(String(m.away.id)) || sub.leagues.includes(String(leagueId));
const score = (s) => (s || '').split(' - ').map(Number);

async function details(id) { return JSON.parse(await fotmob('data/matchDetails', { matchId: id })); }

async function tick() {
  const state = (await store.read('push/state.json')) || { scores: {}, lineups: {}, started: {}, last: 0 };
  if (Date.now() - state.last < 15000) return { skipped: 'too soon' };
  state.last = Date.now();

  const paths = await store.list('push/subs/');
  const subs = (await Promise.all(paths.map((p) => store.read(p)))).filter((s) => s?.sub?.endpoint);
  if (!subs.length) { await store.write('push/state.json', state); return { subs: 0 }; }

  // today and yesterday (UTC) so matches running past midnight are still seen
  const now = new Date(), yest = new Date(now - 864e5);
  const lists = await Promise.all([ymd(yest), ymd(now)].map((date) => fotmob('data/matches', { date, timezone: 'UTC' }).then(JSON.parse).catch(() => ({ leagues: [] }))));
  const matches = new Map();
  for (const l of lists.flatMap((x) => x.leagues || [])) for (const m of l.matches || []) matches.set(m.id, { m, leagueId: l.primaryId, league: l.name });

  const sent = { goals: 0, lineups: 0, events: 0 };
  const fanout = async (m, leagueId, pref, payload) => {
    const targets = subs.filter((s) => s[pref] !== false && follows(s, m, leagueId));
    await Promise.all(targets.map((s) => push(s, payload)));
    return targets.length;
  };
  const seen = new Set();
  for (const { m, leagueId, league } of matches.values()) {
    if (!subs.some((s) => follows(s, m, leagueId))) continue;
    const id = String(m.id), s = m.status, url = `/#/match/${id}`;
    seen.add(id);
    const title = `${m.home.name} - ${m.away.name}`;

    // kick-off / full-time
    if (live(s) && !state.started[id] && state.scores[id] === undefined && Date.now() - new Date(s.utcTime) < 20 * 6e4) {
      sent.events += await fanout(m, leagueId, 'events', { title: `🟢 شروع بازی`, body: `${title}\n${league}`, url, tag: `ko-${id}` });
    }
    if (live(s)) state.started[id] = 1;
    if (s.finished && state.started[id] === 1) {
      state.started[id] = 2;
      sent.events += await fanout(m, leagueId, 'events', { title: '🏁 پایان بازی', body: `${m.home.name} ${s.scoreStr || ''} ${m.away.name}`, url, tag: `ft-${id}` });
    }

    // goals: any change of the score while the match is on
    const prev = state.scores[id];
    if ((live(s) || s.finished) && s.scoreStr) {
      if (prev !== undefined && prev !== s.scoreStr && live(s)) {
        const [ph, pa] = score(prev), [h, a] = score(s.scoreStr);
        let body = `${m.home.name} ${s.scoreStr} ${m.away.name}`;
        let head = '⚽ گل!';
        if (h + a < ph + pa) head = '🚫 گل مردود شد';
        else {
          try {
            const g = (await details(id)).content?.matchFacts?.events?.events?.filter((e) => e.type === 'Goal').pop();
            if (g) body += `\n${g.nameStr || g.player?.name || ''} ${g.timeStr}'${g.ownGoal ? ' (گل به خودی)' : ''}`;
          } catch {}
        }
        sent.goals += await fanout(m, leagueId, 'goals', { title: head, body, url, tag: `goal-${id}-${s.scoreStr}` });
      }
      state.scores[id] = s.scoreStr;
    }

    // lineups: from 90 minutes before kick-off, once per match
    const ko = new Date(s.utcTime) - Date.now();
    if (!s.started && !s.cancelled && ko < 90 * 6e4 && ko > -15 * 6e4 && !state.lineups[id]) {
      try {
        const L = (await details(id)).content?.lineup;
        if (L?.homeTeam?.starters?.length && L?.awayTeam?.starters?.length && (!L.lineupType || L.lineupType === 'standard')) {
          state.lineups[id] = Date.now();
          const team = (T) => `${T.name}${T.formation ? ` (${T.formation})` : ''}: ${T.starters.map((p) => p.lastName || p.name).join('، ')}`;
          for (const sub of subs.filter((x) => x.lineups !== false && follows(x, m, leagueId))) {
            const mine = [L.homeTeam, L.awayTeam].filter((T) => sub.teams.includes(String(T.id)));
            if (await push(sub, { title: `📋 ترکیب ${title}`, body: (mine.length ? mine : [L.homeTeam, L.awayTeam]).map(team).join('\n'), url, tag: `lineup-${id}` })) sent.lineups++;
          }
        }
      } catch {}
    }
  }
  // drop matches that left the two-day window
  for (const k of ['scores', 'lineups', 'started']) for (const id of Object.keys(state[k])) if (!seen.has(id) && !matches.has(+id)) delete state[k][id];
  await store.write('push/state.json', state);
  return { subs: subs.length, watched: seen.size, sent };
}

module.exports = async (req, res) => {
  const a = new URL(req.url, 'http://x').searchParams.get('a');
  try {
    const keys = await vapid();
    if (a === 'key') return send(res, 200, { key: keys.publicKey });
    if (a === 'tick') return send(res, 200, await tick());
    if (req.method !== 'POST') return send(res, 405, { error: 'POST only' });
    const b = await readBody(req);
    const endpoint = b.subscription?.endpoint || b.endpoint;
    if (typeof endpoint !== 'string' || !/^https:\/\//.test(endpoint) || endpoint.length > 1000) return send(res, 400, { error: 'bad subscription' });
    if (a === 'unsub') { await store.del(subPath(endpoint)); return send(res, 200, { ok: true }); }
    if (a === 'sub') {
      const s = b.subscription;
      if (!s?.keys?.p256dh || !s?.keys?.auth) return send(res, 400, { error: 'bad subscription' });
      await store.write(subPath(endpoint), {
        sub: { endpoint, keys: { p256dh: String(s.keys.p256dh), auth: String(s.keys.auth) } },
        teams: ids(b.teams), leagues: ids(b.leagues), matches: ids(b.matches),
        goals: b.goals !== false, lineups: b.lineups !== false, events: b.events !== false, updated: Date.now(),
      });
      return send(res, 200, { ok: true });
    }
    if (a === 'test') {
      const sub = await store.read(subPath(endpoint));
      if (!sub) return send(res, 404, { error: 'not subscribed' });
      const ok = await push(sub, { title: '✅ اعلان‌های گل‌لایو فعال است', body: 'گل‌ها و ترکیب تیم‌های دنبال‌شده اینجا می‌آید، حتی وقتی اپ بسته است.', url: '/#/favorites', tag: 'test' });
      return send(res, ok ? 200 : 502, { ok });
    }
    send(res, 400, { error: 'unknown action' });
  } catch (e) {
    send(res, 500, { error: e.message });
  }
};
