// The prediction league: accounts (a name to start, a phone number to keep it on any phone), everyone's picks,
// private leagues joined by code, and their tables. All of it in one SQLite-backed Durable Object.
//
// POST /p/guest   {name}                     → {token, user}       an account with just a name
// POST /p/otp     {phone}                    → {ok, wait, test?}   a login code by SMS (sms.ir)
// POST /p/verify  {phone, code}              → {token, user}       with the bearer of a guest account, the phone is added to it
// GET  /p/me                                 → {user}
// POST /p/delete                             → {ok}                the account and its picks, gone
// GET  /p/backup · POST /p/backup {data}     → {data, updated}     the app's other things (follows, my team, fantasy, tactics, settings)
// POST /p/name    {name}                     → {user}
// GET  /p/picks                              → {picks}             everything this account predicted (for a new phone)
// POST /p/picks   {picks:[{m,h,a,…}]}        → {saved}             picks of matches not started yet
// POST /p/leagues {name}                     → {league}            a new private league
// POST /p/join    {code}                     → {league}
// POST /p/leave   {id}                       → {ok}
// GET  /p/leagues                            → {leagues}           mine, with my place in each, and everyone's league
// GET  /p/league?id=…&span=week|all          → {league, rows}      a table ("all" is the league of every player)
// GET  /j/<code>                             → a page that opens the app on the league, or the app's page on Cafe Bazaar
// Writes need "Authorization: Bearer <token>".
import { DurableObject } from 'cloudflare:workers';

const BAZAAR = 'https://cafebazaar.ir/app/app.goallive.live';
const CODE_TTL = 10 * 60 * 1000;      // a login code lasts 10 minutes (SMS can be slow to arrive)
const RESEND_MS = 90 * 1000;          // and another can be asked for after a minute and a half
const PER_PHONE_DAY = 6, PER_IP_DAY = 20, ALL_DAY = 800;

const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
const sha = async (s) => hex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
const rand = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));
const today = () => new Date().toISOString().slice(0, 10);

/** An Iranian mobile number as 09xxxxxxxxx (Persian or Arabic digits, +98, 98 or 9xx… all accepted), or null. */
export function phoneOf(s) {
  let d = String(s || '').replace(/[۰-۹]/g, (c) => '۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).replace(/[٠-٩]/g, (c) => '٠١٢٣٤٥٦٧٨٩'.indexOf(c)).replace(/\D/g, '');
  if (d.startsWith('0098')) d = d.slice(4); else if (d.startsWith('98')) d = d.slice(2);
  if (d.startsWith('0')) d = d.slice(1);
  return /^9\d{9}$/.test(d) ? '0' + d : null;
}

const cleanName = (s) => String(s || '').replace(/[\u0000-\u001f<>]/g, '').replace(/\s+/g, ' ').trim().slice(0, 24);

/** The start of this football week in Tehran: Saturday 00:00 (UTC+3:30). */
function weekStart(now = Date.now()) {
  const off = 3.5 * 3600000;
  const t = new Date(now + off);
  const back = (t.getUTCDay() + 1) % 7; // Saturday → 0
  return Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), t.getUTCDate() - back) - off;
}

class Http extends Error { constructor(status, msg) { super(msg); this.status = status; } }

export class League extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.env = env;
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, phone TEXT UNIQUE, created INTEGER);
      CREATE TABLE IF NOT EXISTS tokens (hash TEXT PRIMARY KEY, user INTEGER, created INTEGER);
      CREATE TABLE IF NOT EXISTS otps (phone TEXT PRIMARY KEY, hash TEXT, expires INTEGER, tries INTEGER, sent INTEGER);
      CREATE TABLE IF NOT EXISTS limits (k TEXT PRIMARY KEY, day TEXT, n INTEGER);
      CREATE TABLE IF NOT EXISTS matches (id INTEGER PRIMARY KEY, time INTEGER, home TEXT, away TEXT, hid INTEGER, aid INTEGER, league TEXT,
        done INTEGER DEFAULT 0, rh INTEGER, ra INTEGER, checked INTEGER DEFAULT 0);
      CREATE TABLE IF NOT EXISTS picks (user INTEGER, match INTEGER, h INTEGER, a INTEGER, at INTEGER, PRIMARY KEY (user, match));
      CREATE TABLE IF NOT EXISTS leagues (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE, name TEXT, owner INTEGER, created INTEGER);
      CREATE TABLE IF NOT EXISTS members (league INTEGER, user INTEGER, joined INTEGER, PRIMARY KEY (league, user));
      CREATE INDEX IF NOT EXISTS picks_match ON picks (match);
      CREATE INDEX IF NOT EXISTS members_user ON members (user);
      CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT);
      CREATE TABLE IF NOT EXISTS backups (user INTEGER PRIMARY KEY, data TEXT, updated INTEGER);
      CREATE TABLE IF NOT EXISTS otps2 (phone TEXT PRIMARY KEY, prev TEXT, prev_expires INTEGER);
      CREATE TABLE IF NOT EXISTS fleagues (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE, name TEXT, owner INTEGER, comp INTEGER, created INTEGER);
      CREATE TABLE IF NOT EXISTS fmembers (league INTEGER, user INTEGER, joined INTEGER, PRIMARY KEY (league, user));
      CREATE TABLE IF NOT EXISTS fteams (user INTEGER, comp INTEGER, round TEXT, team TEXT, captain INTEGER, kickoff INTEGER, at INTEGER, PRIMARY KEY (user, comp, round));
      CREATE TABLE IF NOT EXISTS fpoints (user INTEGER, comp INTEGER, round TEXT, pts INTEGER, at INTEGER, PRIMARY KEY (user, comp, round));
      CREATE INDEX IF NOT EXISTS fpoints_comp ON fpoints (comp);
      CREATE TABLE IF NOT EXISTS smslog (at INTEGER, phone TEXT, ok INTEGER, http INTEGER, status INTEGER, message TEXT, id TEXT, body TEXT);
    `);
    // accounts can also be a Google account (its id and address)
    const cols = this.q('PRAGMA table_info(users)').map((c) => c.name);
    if (!cols.includes('google')) this.sql.exec('ALTER TABLE users ADD COLUMN google TEXT; ALTER TABLE users ADD COLUMN email TEXT');
    this.sql.exec('CREATE UNIQUE INDEX IF NOT EXISTS users_google ON users (google)');
    // once, before the app with the league is out: the accounts the first test runs left behind
    if (!this.one("SELECT v FROM meta WHERE k = 'launch'")) {
      for (const t of ['users', 'tokens', 'otps', 'picks', 'leagues', 'members']) this.sql.exec(`DELETE FROM ${t}`);
      this.sql.exec("INSERT INTO meta (k, v) VALUES ('launch', ?)", new Date().toISOString());
    }
  }

  q(s, ...a) { return this.sql.exec(s, ...a).toArray(); }
  one(s, ...a) { return this.q(s, ...a)[0]; }

  /* ---------- accounts ---------- */

  user(id) {
    const u = this.one('SELECT id, name, phone, email FROM users WHERE id = ?', id);
    if (!u) return null;
    const p = this.one(`SELECT COUNT(*) AS n, ${POINTS_SUM} AS pts, ${EXACT_SUM} AS exact FROM picks p JOIN matches m ON m.id = p.match WHERE p.user = ?`, id);
    return { id: u.id, name: u.name, phone: u.phone ? u.phone.slice(0, 4) + '•••' + u.phone.slice(-3) : null,
      email: u.email ? u.email.slice(0, 2) + '•••' + u.email.slice(u.email.indexOf('@')) : null, picks: p.n, points: p.pts || 0, exact: p.exact || 0 };
  }

  async newToken(user) {
    const token = rand(32);
    this.sql.exec('INSERT INTO tokens (hash, user, created) VALUES (?, ?, ?)', await sha(token), user, Date.now());
    return token;
  }

  async who(request) {
    const m = (request.headers.get('authorization') || '').match(/^Bearer ([a-f0-9]{64})$/);
    if (!m) return null;
    return this.one('SELECT user FROM tokens WHERE hash = ?', await sha(m[1]))?.user ?? null;
  }

  /** Counts one more of [k] today; false when it is over [max]. */
  bump(k, max) {
    const d = today();
    const r = this.one('SELECT day, n FROM limits WHERE k = ?', k);
    const n = r && r.day === d ? r.n : 0;
    if (n >= max) return false;
    this.sql.exec('INSERT INTO limits (k, day, n) VALUES (?, ?, ?) ON CONFLICT(k) DO UPDATE SET day = excluded.day, n = excluded.n', k, d, n + 1);
    return true;
  }

  async guest(b) {
    const name = cleanName(b.name);
    if (name.length < 2) throw new Http(400, 'name');
    const id = this.one('INSERT INTO users (name, created) VALUES (?, ?) RETURNING id', name, Date.now()).id;
    return { token: await this.newToken(id), user: this.user(id) };
  }

  async otp(b, ip) {
    const phone = phoneOf(b.phone);
    if (!phone) throw new Http(400, 'phone');
    const now = Date.now();
    const prev = this.one('SELECT sent FROM otps WHERE phone = ?', phone);
    if (prev && now - prev.sent < RESEND_MS) return { ok: false, wait: Math.ceil((RESEND_MS - (now - prev.sent)) / 1000) };
    if (!this.bump('p:' + phone, PER_PHONE_DAY) || !this.bump('i:' + ip, PER_IP_DAY) || !this.bump('all', ALL_DAY)) throw new Http(429, 'limit');
    const code = String(10000 + (crypto.getRandomValues(new Uint32Array(1))[0] % 90000));
    // the code before stays good until it runs out: a late SMS still logs in after "send again"
    const before = this.one('SELECT hash, expires FROM otps WHERE phone = ?', phone);
    if (before && before.expires > now) this.sql.exec('INSERT OR REPLACE INTO otps2 (phone, prev, prev_expires) VALUES (?, ?, ?)', phone, before.hash, before.expires);
    this.sql.exec('INSERT INTO otps (phone, hash, expires, tries, sent) VALUES (?, ?, ?, 0, ?) ON CONFLICT(phone) DO UPDATE SET hash = excluded.hash, expires = excluded.expires, tries = 0, sent = excluded.sent',
      phone, await sha(phone + ':' + code + ':' + (this.env.SMSIR_KEY || 'test')), now + CODE_TTL, now);
    // without the sms.ir key the server is in test mode: the code comes back to the app instead of by SMS
    if (!this.env.SMSIR_KEY) return { ok: true, wait: RESEND_MS / 1000, test: code };
    const sent = await sendSms(this.env, phone, code);
    // what sms.ir said, for /p/sms (the number without its middle, never the code)
    this.sql.exec('INSERT INTO smslog (at, phone, ok, http, status, message, id, body) VALUES (?, ?, ?, ?, ?, ?, ?, ?)', now, phone.slice(0, 4) + '***' + phone.slice(-3),
      sent.ok ? 1 : 0, sent.http || 0, sent.status ?? null, sent.message || sent.error || '', String(sent.id || ''), String(sent.body || '').slice(0, 300));
    this.sql.exec('DELETE FROM smslog WHERE at < ?', now - 7 * 86400000);
    if (!sent.ok) { this.sql.exec('DELETE FROM otps WHERE phone = ?', phone); throw new Http(502, 'sms: ' + sent.error); }
    return { ok: true, wait: RESEND_MS / 1000 };
  }

  async verify(b, me) {
    const phone = phoneOf(b.phone);
    const code = String(b.code || '').replace(/[۰-۹]/g, (c) => '۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).replace(/\D/g, '');
    if (!phone || code.length !== 5) throw new Http(400, 'code');
    const o = this.one('SELECT hash, expires, tries FROM otps WHERE phone = ?', phone);
    if (!o || o.expires < Date.now()) throw new Http(410, 'expired');
    if (o.tries >= 5) throw new Http(429, 'tries');
    const h = await sha(phone + ':' + code + ':' + (this.env.SMSIR_KEY || 'test'));
    const p2 = this.one('SELECT prev, prev_expires FROM otps2 WHERE phone = ?', phone);
    if (o.hash !== h && !(p2 && p2.prev === h && p2.prev_expires > Date.now())) {
      this.sql.exec('UPDATE otps SET tries = tries + 1 WHERE phone = ?', phone);
      throw new Http(401, 'wrong');
    }
    this.sql.exec('DELETE FROM otps WHERE phone = ?', phone);
    this.sql.exec('DELETE FROM otps2 WHERE phone = ?', phone);
    let id = this.one('SELECT id FROM users WHERE phone = ?', phone)?.id;
    if (id && me && me !== id) {
      // the number already has an account: this phone's guest picks and leagues join it
      this.absorb(id, me);
    } else if (!id && me) {
      this.sql.exec('UPDATE users SET phone = ? WHERE id = ?', phone, me);
      id = me;
    } else if (!id) {
      id = this.one('INSERT INTO users (name, phone, created) VALUES (?, ?, ?) RETURNING id', cleanName(b.name) || 'بازیکن ' + phone.slice(-4), phone, Date.now()).id;
    }
    return { token: await this.newToken(id), user: this.user(id) };
  }

  /** Logs in with a Google account: its sign-in token is checked with Google, then the same as a number (a returning account comes back). */
  async google(b, me) {
    const client = this.env.GOOGLE_CLIENT_ID;
    if (!client) throw new Http(404, 'google');
    const r = await fetch('https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(String(b.token || '')), { signal: AbortSignal.timeout(10000) }).catch(() => null);
    const t = r && r.ok ? await r.json().catch(() => ({})) : {};
    // the reason, short, for the app to show under the error (never the token)
    if (!r) throw new Http(502, 'google: no answer');
    if (!r.ok) throw new Http(401, 'google: check ' + r.status);
    if (t.aud !== client) throw new Http(401, 'google: aud ' + String(t.aud || '').slice(0, 14) + ' / ' + client.slice(0, 14));
    if (!t.sub || !['accounts.google.com', 'https://accounts.google.com'].includes(t.iss) || +t.exp * 1000 < Date.now()) throw new Http(401, 'google: token');
    const email = t.email_verified === 'true' || t.email_verified === true ? String(t.email || '').toLowerCase() : null;
    let id = this.one('SELECT id FROM users WHERE google = ?', t.sub)?.id;
    if (id && me && me !== id) this.absorb(id, me);
    else if (!id && me) { this.sql.exec('UPDATE users SET google = ?, email = ? WHERE id = ?', t.sub, email, me); id = me; }
    else if (!id) id = this.one('INSERT INTO users (name, google, email, created) VALUES (?, ?, ?, ?) RETURNING id', cleanName(b.name || t.name) || 'بازیکن', t.sub, email, Date.now()).id;
    return { token: await this.newToken(id), user: this.user(id) };
  }

  /** This phone's account [me] joins the account [id] that logged in: its picks, leagues and kept things, then it goes. */
  absorb(id, me) {
    this.sql.exec('INSERT OR IGNORE INTO picks (user, match, h, a, at) SELECT ?, match, h, a, at FROM picks WHERE user = ?', id, me);
    this.sql.exec('INSERT OR IGNORE INTO members (league, user, joined) SELECT league, ?, joined FROM members WHERE user = ?', id, me);
    this.sql.exec('UPDATE leagues SET owner = ? WHERE owner = ?', id, me);
    this.sql.exec('INSERT OR IGNORE INTO backups (user, data, updated) SELECT ?, data, updated FROM backups WHERE user = ?', id, me);
    this.sql.exec('INSERT OR IGNORE INTO fmembers (league, user, joined) SELECT league, ?, joined FROM fmembers WHERE user = ?', id, me);
    this.sql.exec('UPDATE fleagues SET owner = ? WHERE owner = ?', id, me);
    this.sql.exec('INSERT OR IGNORE INTO fteams (user, comp, round, team, captain, kickoff, at) SELECT ?, comp, round, team, captain, kickoff, at FROM fteams WHERE user = ?', id, me);
    this.sql.exec('INSERT OR IGNORE INTO fpoints (user, comp, round, pts, at) SELECT ?, comp, round, pts, at FROM fpoints WHERE user = ?', id, me);
    for (const t of ['picks', 'members', 'tokens', 'backups', 'fmembers', 'fteams', 'fpoints']) this.sql.exec(`DELETE FROM ${t} WHERE user = ?`, me);
    this.sql.exec('DELETE FROM users WHERE id = ?', me);
  }

  setName(b, me) {
    const name = cleanName(b.name);
    if (name.length < 2) throw new Http(400, 'name');
    this.sql.exec('UPDATE users SET name = ? WHERE id = ?', name, me);
    return { user: this.user(me) };
  }

  /** The account and everything in it, gone (its leagues stay for the others; one nobody is left in goes too). */
  deleteMe(me) {
    const mine = this.q('SELECT league FROM members WHERE user = ?', me).map((r) => r.league);
    for (const t of ['picks', 'members', 'tokens', 'backups', 'fmembers', 'fteams', 'fpoints']) this.sql.exec(`DELETE FROM ${t} WHERE user = ?`, me);
    this.sql.exec('DELETE FROM users WHERE id = ?', me);
    for (const l of mine) if (!this.one('SELECT 1 AS x FROM members WHERE league = ?', l)) this.sql.exec('DELETE FROM leagues WHERE id = ?', l);
    return { ok: true };
  }

  /* ---------- everything else the app keeps: follows, my team, fantasy, saved tactics, settings ---------- */

  getBackup(me) {
    const b = this.one('SELECT data, updated FROM backups WHERE user = ?', me);
    return b ? { data: b.data, updated: b.updated } : { data: null, updated: 0 };
  }

  putBackup(b, me) {
    const data = typeof b.data === 'string' ? b.data : '';
    if (!data || data.length > 262144) throw new Http(413, 'size');
    const now = Date.now();
    this.sql.exec('INSERT INTO backups (user, data, updated) VALUES (?, ?, ?) ON CONFLICT(user) DO UPDATE SET data = excluded.data, updated = excluded.updated', me, data, now);
    return { updated: now };
  }

  /* ---------- picks ---------- */

  /** What the server knows of a match, from FotMob the first time (its kick-off decides whether a pick is still open). */
  async match(id) {
    let m = this.one('SELECT * FROM matches WHERE id = ?', id);
    if (m) return m;
    const d = await matchDetails(id);
    if (!d) return null;
    this.sql.exec('INSERT OR REPLACE INTO matches (id, time, home, away, hid, aid, league, done, rh, ra, checked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
      id, d.time, d.home, d.away, d.hid, d.aid, d.league, d.done ? 1 : 0, d.rh, d.ra, Date.now());
    return this.one('SELECT * FROM matches WHERE id = ?', id);
  }

  async savePicks(b, me) {
    const list = Array.isArray(b.picks) ? b.picks.slice(0, 60) : [];
    let saved = 0;
    for (const p of list) {
      const id = +p.m, h = +p.h, a = +p.a;
      if (!(id > 0) || !(h >= 0 && h <= 20) || !(a >= 0 && a <= 20)) continue;
      const known = this.one('SELECT h, a FROM picks WHERE user = ? AND match = ?', me, id);
      if (known && known.h === h && known.a === a) { saved++; continue; }
      const m = await this.match(id);
      // a pick counts only if it was made before kick-off
      if (!m || m.done || Date.now() >= m.time) continue;
      this.sql.exec('INSERT INTO picks (user, match, h, a, at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(user, match) DO UPDATE SET h = excluded.h, a = excluded.a, at = excluded.at', me, id, h, a, Date.now());
      saved++;
    }
    return { saved };
  }

  picks(me) {
    return { picks: this.q('SELECT p.match AS m, p.h, p.a, m.time AS t, m.home AS hn, m.away AS an, m.hid AS hi, m.aid AS ai, m.league AS l, m.done, m.rh, m.ra FROM picks p JOIN matches m ON m.id = p.match WHERE p.user = ? ORDER BY m.time DESC LIMIT 300', me) };
  }

  /** Final scores for matches that should be over: a few at a time, from FotMob. */
  async settle(max = 12) {
    const now = Date.now();
    const due = this.q('SELECT id FROM matches WHERE done = 0 AND time < ? AND checked < ? AND id IN (SELECT match FROM picks) ORDER BY time LIMIT ?', now - 100 * 60000, now - 10 * 60000, max);
    for (const { id } of due) {
      const d = await matchDetails(id).catch(() => null);
      if (!d) { this.sql.exec('UPDATE matches SET checked = ? WHERE id = ?', now, id); continue; }
      this.sql.exec('UPDATE matches SET done = ?, rh = ?, ra = ?, time = ?, checked = ? WHERE id = ?', d.done ? 1 : 0, d.rh, d.ra, d.time, now, id);
    }
    return due.length;
  }

  /* ---------- leagues ---------- */

  newLeague(b, me) {
    const name = cleanName(b.name);
    if (name.length < 2) throw new Http(400, 'name');
    if (this.one('SELECT COUNT(*) AS n FROM leagues WHERE owner = ?', me).n >= 20) throw new Http(429, 'limit');
    let code;
    // six letters and digits without the ones that look alike
    do { code = [...crypto.getRandomValues(new Uint8Array(6))].map((x) => 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'[x % 31]).join(''); }
    while (this.one('SELECT 1 AS x FROM leagues WHERE code = ?', code));
    const id = this.one('INSERT INTO leagues (code, name, owner, created) VALUES (?, ?, ?, ?) RETURNING id', code, name, me, Date.now()).id;
    this.sql.exec('INSERT INTO members (league, user, joined) VALUES (?, ?, ?)', id, me, Date.now());
    return { league: this.leagueInfo(id, me) };
  }

  join(b, me) {
    const code = String(b.code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    const l = this.one('SELECT id FROM leagues WHERE code = ?', code);
    if (!l) throw new Http(404, 'code');
    if (this.one('SELECT COUNT(*) AS n FROM members WHERE league = ?', l.id).n >= 500) throw new Http(429, 'full');
    this.sql.exec('INSERT OR IGNORE INTO members (league, user, joined) VALUES (?, ?, ?)', l.id, me, Date.now());
    return { league: this.leagueInfo(l.id, me) };
  }

  leave(b, me) {
    this.sql.exec('DELETE FROM members WHERE league = ? AND user = ?', +b.id, me);
    // a league nobody is in any more goes
    if (!this.one('SELECT 1 AS x FROM members WHERE league = ?', +b.id)) this.sql.exec('DELETE FROM leagues WHERE id = ?', +b.id);
    return { ok: true };
  }

  leagueInfo(id, me) {
    const l = this.one('SELECT id, code, name, owner FROM leagues WHERE id = ?', id);
    if (!l) return null;
    const n = this.one('SELECT COUNT(*) AS n FROM members WHERE league = ?', id).n;
    const rows = this.table(id, 'all');
    const mine = rows.findIndex((r) => r.id === me);
    return { id: l.id, code: l.code, name: l.name, mine: l.owner === me, members: n, rank: mine >= 0 ? mine + 1 : null, leader: rows[0]?.name || null };
  }

  leagues(me) {
    const ids = this.q('SELECT league FROM members WHERE user = ? ORDER BY joined DESC', me).map((r) => r.league);
    const everyone = this.table(0, 'all');
    const at = everyone.findIndex((r) => r.id === me);
    return {
      leagues: ids.map((id) => this.leagueInfo(id, me)).filter(Boolean),
      everyone: { players: everyone.length, rank: at >= 0 ? at + 1 : null, leader: everyone[0]?.name || null },
    };
  }

  /** A league's table (0: everyone who made a pick), over the week or the whole season. */
  table(id, span) {
    const from = span === 'week' ? weekStart() : 0;
    const who = id ? 'u.id IN (SELECT user FROM members WHERE league = ?)' : 'u.id IN (SELECT DISTINCT user FROM picks)';
    const args = id ? [from, id] : [from];
    return this.q(`SELECT u.id, u.name, COUNT(m.id) AS played, ${POINTS_SUM} AS points, ${EXACT_SUM} AS exact,
        SUM(CASE WHEN m.id IS NOT NULL AND m.done = 0 AND m.time <= ${Date.now()} THEN 1 ELSE 0 END) AS live
      FROM users u LEFT JOIN picks p ON p.user = u.id LEFT JOIN matches m ON m.id = p.match AND m.time >= ?
      WHERE ${who} GROUP BY u.id ORDER BY points DESC, exact DESC, played DESC, u.id LIMIT 500`, ...args)
      .map((r) => ({ id: r.id, name: r.name, played: r.played, points: r.points || 0, exact: r.exact || 0, live: r.live || 0 }));
  }

  async league(url, me) {
    const id = +url.searchParams.get('id') || 0;
    const span = url.searchParams.get('span') === 'week' ? 'week' : 'all';
    if (id && !this.one('SELECT 1 AS x FROM members WHERE league = ? AND user = ?', id, me)) throw new Http(403, 'member');
    await this.settle(6);
    // picks still to come in this league: the next matches its players picked, with what each of them said
    const who = id ? 'p.user IN (SELECT user FROM members WHERE league = ?)' : 'p.user = ?';
    const next = this.q(`SELECT m.id, m.home, m.away, m.hid, m.aid, m.time, COUNT(*) AS n FROM picks p JOIN matches m ON m.id = p.match
      WHERE ${who} AND m.done = 0 GROUP BY m.id ORDER BY m.time LIMIT 6`, id || me);
    return { league: id ? this.leagueInfo(id, me) : { id: 0, name: 'everyone' }, span, week: weekStart(), rows: this.table(id, span), next };
  }

  /* ---------- fantasy leagues ---------- */

  /**
   * A gameweek's squad, sent while it can still change: the server keeps it only before the round's first kick-off (the
   * match the app names, checked with FotMob), so a team can't be picked after the results are known.
   */
  async fantasyTeam(b, me) {
    const comp = +b.comp, round = String(b.round || '').slice(0, 40), first = +b.first;
    const team = Array.isArray(b.team) ? b.team.map(Number).filter((x) => x > 0).slice(0, 15) : [];
    if (!(comp > 0) || !round || !(first > 0) || team.length < 1) throw new Http(400, 'team');
    const m = await this.match(first);
    if (!m) throw new Http(404, 'match');
    if (Date.now() >= m.time) throw new Http(409, 'locked');
    const bench = Array.isArray(b.bench) ? b.bench.map(Number).filter((x) => team.includes(x)).slice(0, 4) : [];
    this.sql.exec(`INSERT INTO fteams (user, comp, round, team, captain, kickoff, at) VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(user, comp, round) DO UPDATE SET team = excluded.team, captain = excluded.captain, kickoff = excluded.kickoff, at = excluded.at`,
      me, comp, round, JSON.stringify({ team, bench }), +b.captain || 0, m.time, Date.now());
    return { ok: true };
  }

  /** A gameweek's points, worked out on the phone from the real matches; only for a round whose team came in before it began. */
  fantasyPoints(b, me) {
    const comp = +b.comp, round = String(b.round || '').slice(0, 40), pts = Math.round(+b.pts);
    if (!(comp > 0) || !round || !Number.isFinite(pts) || pts < -60 || pts > 400) throw new Http(400, 'points');
    // a team not changed this week carries over from an earlier one (the app keeps it for the next round as it was)
    const t = this.one('SELECT kickoff FROM fteams WHERE user = ? AND comp = ? AND round = ?', me, comp, round)
      || this.one('SELECT 0 AS kickoff FROM fteams WHERE user = ? AND comp = ? AND kickoff < ? LIMIT 1', me, comp, Date.now());
    if (!t) throw new Http(404, 'team');
    if (Date.now() < t.kickoff) throw new Http(409, 'early');
    this.sql.exec('INSERT INTO fpoints (user, comp, round, pts, at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(user, comp, round) DO UPDATE SET pts = excluded.pts, at = excluded.at',
      me, comp, round, pts, Date.now());
    return { ok: true };
  }

  newFantasy(b, me) {
    const name = cleanName(b.name), comp = +b.comp;
    if (name.length < 2) throw new Http(400, 'name');
    if (!(comp > 0)) throw new Http(400, 'comp');
    if (this.one('SELECT COUNT(*) AS n FROM fleagues WHERE owner = ?', me).n >= 20) throw new Http(429, 'limit');
    let code;
    // "F" and five letters or digits, so an invite link can tell a fantasy league from a prediction league
    do { code = 'F' + [...crypto.getRandomValues(new Uint8Array(5))].map((x) => 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'[x % 31]).join(''); }
    while (this.one('SELECT 1 AS x FROM fleagues WHERE code = ?', code) || this.one('SELECT 1 AS x FROM leagues WHERE code = ?', code));
    const id = this.one('INSERT INTO fleagues (code, name, owner, comp, created) VALUES (?, ?, ?, ?, ?) RETURNING id', code, name, me, comp, Date.now()).id;
    this.sql.exec('INSERT INTO fmembers (league, user, joined) VALUES (?, ?, ?)', id, me, Date.now());
    return { league: this.fantasyInfo(id, me) };
  }

  joinFantasy(b, me) {
    const code = String(b.code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    const l = this.one('SELECT id FROM fleagues WHERE code = ?', code);
    if (!l) throw new Http(404, 'code');
    if (this.one('SELECT COUNT(*) AS n FROM fmembers WHERE league = ?', l.id).n >= 500) throw new Http(429, 'full');
    this.sql.exec('INSERT OR IGNORE INTO fmembers (league, user, joined) VALUES (?, ?, ?)', l.id, me, Date.now());
    return { league: this.fantasyInfo(l.id, me) };
  }

  leaveFantasy(b, me) {
    this.sql.exec('DELETE FROM fmembers WHERE league = ? AND user = ?', +b.id, me);
    if (!this.one('SELECT 1 AS x FROM fmembers WHERE league = ?', +b.id)) this.sql.exec('DELETE FROM fleagues WHERE id = ?', +b.id);
    return { ok: true };
  }

  fantasyInfo(id, me) {
    const l = this.one('SELECT id, code, name, owner, comp FROM fleagues WHERE id = ?', id);
    if (!l) return null;
    const rows = this.fantasyTable(id, l.comp, null);
    const mine = rows.findIndex((r) => r.id === me);
    return { id: l.id, code: l.code, name: l.name, comp: l.comp, mine: l.owner === me, members: rows.length, rank: mine >= 0 ? mine + 1 : null, leader: rows[0]?.name || null };
  }

  /** My fantasy leagues (all competitions), and where I stand among everyone playing [comp]. */
  fantasyLeagues(url, me) {
    const comp = +url.searchParams.get('comp') || 0;
    const ids = this.q('SELECT league FROM fmembers WHERE user = ? ORDER BY joined DESC', me).map((r) => r.league);
    const everyone = comp ? this.fantasyTable(0, comp, null) : [];
    const at = everyone.findIndex((r) => r.id === me);
    return {
      leagues: ids.map((id) => this.fantasyInfo(id, me)).filter(Boolean),
      everyone: { players: everyone.length, rank: at >= 0 ? at + 1 : null, leader: everyone[0]?.name || null },
    };
  }

  /** A fantasy table: everyone in the league (0: everyone with a team in [comp]), by season points or one round's. */
  fantasyTable(id, comp, round) {
    const who = id ? 'u.id IN (SELECT user FROM fmembers WHERE league = ?)' : 'u.id IN (SELECT DISTINCT user FROM fteams WHERE comp = ?)';
    return this.q(`SELECT u.id, u.name, COALESCE(SUM(f.pts), 0) AS total, COUNT(f.round) AS weeks,
        COALESCE(SUM(CASE WHEN f.round = ? THEN f.pts END), 0) AS last
      FROM users u LEFT JOIN fpoints f ON f.user = u.id AND f.comp = ?
      WHERE ${who} GROUP BY u.id ORDER BY ${round ? 'last DESC, total DESC' : 'total DESC, last DESC'}, u.id LIMIT 500`, round || '', comp, id || comp)
      .map((r) => ({ id: r.id, name: r.name, total: r.total, weeks: r.weeks, last: r.last }));
  }

  fantasyLeague(url, me) {
    const id = +url.searchParams.get('id') || 0;
    const round = url.searchParams.get('round') || null;
    let comp = +url.searchParams.get('comp') || 0;
    if (id) {
      if (!this.one('SELECT 1 AS x FROM fmembers WHERE league = ? AND user = ?', id, me)) throw new Http(403, 'member');
      comp = this.one('SELECT comp FROM fleagues WHERE id = ?', id)?.comp || 0;
    }
    if (!comp) throw new Http(400, 'comp');
    // the last round anyone in it has points for, when none is asked
    const last = round || this.one(`SELECT f.round FROM fpoints f WHERE f.comp = ? ${id ? 'AND f.user IN (SELECT user FROM fmembers WHERE league = ?)' : ''} ORDER BY f.at DESC LIMIT 1`,
      ...(id ? [comp, id] : [comp]))?.round || null;
    return { league: id ? this.fantasyInfo(id, me) : { id: 0, name: 'everyone', comp }, round: last, rows: this.fantasyTable(id, comp, last), byRound: !!round };
  }

  /* ---------- the way in ---------- */

  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const ip = request.headers.get('x-ip') || '?';
    try {
      const body = request.method === 'POST' ? await request.json().catch(() => ({})) : {};
      const me = await this.who(request);
      const need = () => { if (!me) throw new Http(401, 'login'); return me; };
      let out;
      switch (path) {
        case '/p/guest': out = await this.guest(body); break;
        case '/p/otp': out = await this.otp(body, ip); break;
        case '/p/verify': out = await this.verify(body, me); break;
        case '/p/google': out = await this.google(body, me); break;
        case '/p/config': out = { google: this.env.GOOGLE_CLIENT_ID || null }; break;
        case '/p/me': out = { user: this.user(need()) }; break;
        case '/p/name': out = this.setName(body, need()); break;
        case '/p/delete': out = this.deleteMe(need()); break;
        case '/p/backup': out = request.method === 'POST' ? this.putBackup(body, need()) : this.getBackup(need()); break;
        case '/p/picks': out = request.method === 'POST' ? await this.savePicks(body, need()) : this.picks(need()); break;
        case '/p/leagues': out = request.method === 'POST' ? this.newLeague(body, need()) : this.leagues(need()); break;
        case '/p/join': out = this.join(body, need()); break;
        case '/p/leave': out = this.leave(body, need()); break;
        case '/p/league': out = await this.league(url, need()); break;
        case '/p/fteam': out = await this.fantasyTeam(body, need()); break;
        case '/p/fpoints': out = this.fantasyPoints(body, need()); break;
        case '/p/fleagues': out = request.method === 'POST' ? this.newFantasy(body, need()) : this.fantasyLeagues(url, need()); break;
        case '/p/fjoin': out = this.joinFantasy(body, need()); break;
        case '/p/fleave': out = this.leaveFantasy(body, need()); break;
        case '/p/fleague': out = this.fantasyLeague(url, need()); break;
        case '/p/settle': out = { checked: await this.settle(20) }; break;
        case '/p/smslog': out = this.q('SELECT at, phone, ok, http, status, message, id, body FROM smslog ORDER BY at DESC LIMIT 10'); break;
        default: throw new Http(404, 'not found');
      }
      return Response.json(out);
    } catch (e) {
      return Response.json({ error: e.message || String(e) }, { status: e.status || 500 });
    }
  }
}

const POINTS = 'CASE WHEN m.done = 1 AND p.h = m.rh AND p.a = m.ra THEN 3 WHEN m.done = 1 AND (p.h > p.a) = (m.rh > m.ra) AND (p.h < p.a) = (m.rh < m.ra) THEN 1 ELSE 0 END';
const POINTS_SUM = `SUM(${POINTS})`;
const EXACT_SUM = 'SUM(CASE WHEN m.done = 1 AND p.h = m.rh AND p.a = m.ra THEN 1 ELSE 0 END)';

/** The parts of a FotMob match the league needs. */
async function matchDetails(id) {
  const r = await fetch(`https://www.fotmob.com/api/data/matchDetails?matchId=${id}`, {
    headers: { 'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36', accept: 'application/json', referer: 'https://www.fotmob.com/' },
    signal: AbortSignal.timeout(10000),
  });
  if (!r.ok) return null;
  const d = await r.json();
  const h = d.header || {}, s = h.status || {}, t = h.teams || [];
  const time = Date.parse(s.utcTime || d.general?.matchTimeUTCDate || '');
  if (!t[0] || !t[1] || !time) return null;
  return {
    time, home: t[0].name, away: t[1].name, hid: t[0].id, aid: t[1].id, league: d.general?.leagueName || '',
    done: !!s.finished && !s.cancelled, rh: t[0].score ?? null, ra: t[1].score ?? null,
  };
}

/** The login code by SMS, through sms.ir's verification service (a template approved in its panel). */
async function sendSms(env, phone, code) {
  try {
    const r = await fetch('https://api.sms.ir/v1/send/verify', {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'text/plain', 'x-api-key': env.SMSIR_KEY },
      body: JSON.stringify({ mobile: phone, templateId: +(env.SMSIR_TEMPLATE || 123456), parameters: [{ name: env.SMSIR_PARAM || 'Code', value: code }] }),
      signal: AbortSignal.timeout(10000),
    });
    const text = await r.text();
    let j = {};
    try { j = JSON.parse(text); } catch {}
    const info = { http: r.status, status: j.status, message: j.message, id: j.data?.messageId, body: text };
    return j.status === 1 ? { ok: true, ...info } : { ok: false, error: `${r.status} ${j.message || ''}`.trim(), ...info };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
}

/** Whether sms.ir answers this server, and the account's credit (for the deploy check). */
export async function smsCheck(env) {
  if (!env.SMSIR_KEY) return { key: false };
  try {
    const r = await fetch('https://api.sms.ir/v1/credit', { headers: { accept: 'application/json', 'x-api-key': env.SMSIR_KEY }, signal: AbortSignal.timeout(10000) });
    const j = await r.json().catch(() => ({}));
    return { key: true, http: r.status, status: j.status, message: j.message, credit: j.data };
  } catch (e) {
    return { key: true, error: String(e.message || e) };
  }
}

/** /p/* and /j/* on the worker. */
export async function league(request, env, url) {
  if (url.pathname.startsWith('/j/')) return new Response(joinPage(url.pathname.slice(3).replace(/[^A-Za-z0-9]/g, '').slice(0, 8)), { headers: { 'content-type': 'text/html; charset=utf-8' } });
  if (url.pathname === '/p/sms' ) {
    if (!env.DASH_KEY || url.searchParams.get('key') !== env.DASH_KEY) return new Response('wrong key', { status: 403 });
    const stub = env.LEAGUE.get(env.LEAGUE.idFromName('main'));
    const log = await (await stub.fetch('https://p/p/smslog')).json().catch(() => []);
    for (const l of log.slice(0, 5)) if (l.id) {
      try {
        const r = await fetch('https://api.sms.ir/v1/send/' + l.id, { headers: { accept: 'application/json', 'x-api-key': env.SMSIR_KEY }, signal: AbortSignal.timeout(8000) });
        // only the delivery state: never the number or the text (it holds the code)
        const j = await r.json().catch(() => ({}));
        l.report = { status: j.status, message: j.message, line: j.data?.lineNumber, delivery: j.data?.deliveryState ?? null, deliveredAt: j.data?.deliveryDateTime ?? null };
      } catch (e) { l.report = String(e.message || e); }
    }
    return Response.json({ ...(await smsCheck(env)), template: env.SMSIR_TEMPLATE || null, param: env.SMSIR_PARAM || null, log });
  }
  // the log and the scheduled run are for this server only
  if (url.pathname === '/p/smslog' || url.pathname === '/p/settle') return new Response('not found', { status: 404 });
  if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });
  if (+request.headers.get('content-length') > (url.pathname === '/p/backup' ? 300000 : 32768)) return new Response('too big', { status: 413 });
  const stub = env.LEAGUE.get(env.LEAGUE.idFromName('main'));
  const headers = new Headers(request.headers);
  headers.set('x-ip', request.headers.get('cf-connecting-ip') || '?');
  const res = await stub.fetch(new Request(request.url, { method: request.method, headers, body: request.method === 'POST' ? await request.text() : undefined }));
  const out = new Response(res.body, res);
  for (const [k, v] of Object.entries(CORS)) out.headers.set(k, v);
  out.headers.set('cache-control', 'no-store');
  return out;
}

const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'content-type, authorization' };

/** The page an invite link opens: the app on the league when it is installed, otherwise its page on Cafe Bazaar. */
function joinPage(code) {
  // a fantasy league's code starts with F
  const fantasy = code.startsWith('F');
  const what = fantasy ? 'لیگ فانتزی' : 'لیگ پیش‌بینی';
  const line = fantasy ? 'در گل‌لایو تیم فانتزی بچین و هر هفته با دوستانت رقابت کن.' : 'در گل‌لایو نتیجه‌ی بازی‌ها را پیش‌بینی کن و با دوستانت رقابت کن.';
  return `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>دعوت به ${what} گل‌لایو</title>
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0B0F14;color:#fff;font-family:Tahoma,sans-serif;text-align:center}
.c{padding:28px;max-width:360px}.k{font-size:34px;letter-spacing:6px;color:#C6FF3D;margin:14px 0;font-family:monospace}
a{display:block;margin:12px 0;padding:14px;border-radius:14px;text-decoration:none;font-weight:bold}
.p{background:#C6FF3D;color:#0B0F14}.s{background:#1B222C;color:#fff}</style></head>
<body><div class="c"><h2>دعوت به ${what}</h2><p>${line}</p>
<div>کد لیگ</div><div class="k">${code}</div>
<a class="p" href="goallive://join/${code}">باز کردن در گل‌لایو</a>
<a class="s" href="${BAZAAR}">نصب گل‌لایو از کافه‌بازار</a></div>
<script>setTimeout(function(){location.href="goallive://join/${code}"},300)</script></body></html>`;
}

// the scheduled run: final scores for the picked matches, so tables are right even when nobody opens them
export async function settleAll(env) {
  const stub = env.LEAGUE.get(env.LEAGUE.idFromName('main'));
  await stub.fetch('https://p/p/settle');
}

