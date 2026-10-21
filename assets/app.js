import {
  esc, fa, num, ymd, parseYmd, dateOf, tz, api, store, save, icon, crest, lgLogo, face, matchRow, isLive, isFav, starBtn,
  skeleton, empty, errorBox, seg, toast, sheet, isDark, teamImg, teamImgL, statusShort, timeOf, notifySupported, notifyPermission, showNotification, pushSupported, syncPush, testPush,
} from './core.js';
import { matchView } from './match.js';
import { club, derbyName } from './clubs.js';
import { celebrate } from './motion.js';
import { leagueView, teamView, playerView } from './pages.js';

const $ = (s, el = document) => el.querySelector(s);
const view = $('#view');
const REFRESH = 20000;

/* ---------- theme ---------- */
function applyTheme() {
  if (store.theme) document.documentElement.dataset.theme = store.theme;
  else delete document.documentElement.dataset.theme;
  $('meta[name="theme-color"]').content = isDark() ? '#05070a' : '#e9eee9';
  $('#themeBtn').innerHTML = icon(isDark() ? 'sun' : 'moon');
}
$('#themeBtn').onclick = () => { store.theme = isDark() ? 'light' : ''; save(); applyTheme(); };
applyTheme();

addEventListener('scroll', () => $('.top').classList.toggle('scrolled', scrollY > 4), { passive: true });
$('#backBtn').onclick = () => (history.length > 1 ? history.back() : (location.hash = '#/'));
$('#settingsBtn').onclick = openSettings;

/* ---------- live goal feed (today's matches, used for notifications on every page) ---------- */
const prevScore = new Map();
const goalFlash = new Map(); // match id -> until timestamp
let todayData = null, todayKey = '';
const feedListeners = new Set();

function beep() {
  if (!store.sound) return;
  try {
    const ac = new (window.AudioContext || window.webkitAudioContext)();
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = 'triangle'; o.frequency.setValueAtTime(880, ac.currentTime); o.frequency.setValueAtTime(1320, ac.currentTime + 0.12);
    g.gain.setValueAtTime(0.0001, ac.currentTime); g.gain.exponentialRampToValueAtTime(0.25, ac.currentTime + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, ac.currentTime + 0.45);
    o.connect(g).connect(ac.destination); o.start(); o.stop(ac.currentTime + 0.5);
  } catch {}
}
const followed = (m, l) => isFav('matches', m.id) || isFav('teams', m.home.id) || isFav('teams', m.away.id) || isFav('leagues', l.primaryId);

async function alertUser(title, body, matchId, tag) {
  beep();
  if (!document.hidden) toast(`${title} ${body}`);
  // with Web Push active the server sends the system notification (even when closed); avoid doubles
  if (store.notify && !store.push) try { await showNotification(title, body, matchId, tag); } catch {}
}

// Scorer + minute for a fresh goal, from the match details (best effort).
async function goalText(m) {
  const base = `${m.home.name} ${m.status.scoreStr} ${m.away.name}`;
  try {
    const d = await api('matchDetails', { matchId: m.id }, { maxAge: 0 });
    const g = (d.content?.matchFacts?.events?.events || []).filter((e) => e.type === 'Goal').pop();
    if (g) return `${base}\n${g.nameStr || g.player?.name || ''} ${g.timeStr}'${g.ownGoal ? ' (گل به خودی)' : ''}`;
  } catch {}
  return base;
}

function detectGoals(data, notify) {
  for (const l of data.leagues || []) for (const m of l.matches || []) {
    const s = m.status.scoreStr;
    if (!s) continue;
    const before = prevScore.get(m.id);
    if (before && before !== s && isLive(m.status)) {
      goalFlash.set(m.id, Date.now() + 3400);
      if (notify && store.notifyGoals !== false && followed(m, l)) {
        const [ph, pa] = before.split(' - ').map(Number), [h, a] = s.split(' - ').map(Number);
        if (h + a > ph + pa && !location.hash.startsWith('#/match/' + m.id)) {
          const scorer = h > ph ? m.home : m.away, other = scorer === m.home ? m.away : m.home;
          celebrate({ sub: `${m.home.name} ${s} ${m.away.name}`, c1: teamColor(scorer.id), c2: club(scorer.id)?.c[1] || teamColor(other.id) });
        }
        goalText(m).then((t) => alertUser('⚽ گل!', t, m.id, 'goal-' + m.id + '-' + s));
      }
    }
    prevScore.set(m.id, s);
  }
}

// Lineups come out ~1h before kick-off: watch followed matches from 90 minutes out, announce once per match.
const lineupChecked = new Map(); // match id -> last check time
async function detectLineups(data) {
  if (store.notifyLineups === false) return;
  store.lineupSent ||= {};
  const now = Date.now();
  for (const l of data.leagues || []) for (const m of l.matches || []) {
    const ko = new Date(m.status.utcTime) - now;
    if (m.status.started || m.status.cancelled || ko > 90 * 6e4 || ko < -15 * 6e4) continue;
    if (!followed(m, l) || store.lineupSent[m.id] || now - (lineupChecked.get(m.id) || 0) < 110e3) continue;
    lineupChecked.set(m.id, now);
    try {
      const d = await api('matchDetails', { matchId: m.id }, { maxAge: 0 });
      const L = d.content?.lineup;
      if (!L?.homeTeam?.starters?.length || !L?.awayTeam?.starters?.length || (L.lineupType && L.lineupType !== 'standard')) continue;
      store.lineupSent[m.id] = now; save();
      const team = (T) => `${T.name}${T.formation ? ` (${T.formation})` : ''}: ${T.starters.map((p) => p.lastName || p.name).join('، ')}`;
      const fav = [L.homeTeam, L.awayTeam].filter((T) => isFav('teams', T.id));
      const body = (fav.length ? fav : [L.homeTeam, L.awayTeam]).map(team).join('\n');
      alertUser(`📋 ترکیب ${m.home.name} - ${m.away.name} اعلام شد`, body, m.id, 'lineup-' + m.id);
    } catch {}
  }
  // forget old entries
  for (const [id, t] of Object.entries(store.lineupSent)) if (now - t > 2 * 864e5) delete store.lineupSent[id];
}

const hasFollows = () => Object.keys(store.teams).length + Object.keys(store.leagues).length + Object.keys(store.matches).length > 0;
let feedTimer;
async function pollToday(force) {
  clearTimeout(feedTimer);
  const key = ymd(new Date());
  if (key !== todayKey) { todayKey = key; prevScore.clear(); }
  try {
    const d = await api('matches', { date: key, timezone: tz, ccode3: 'IRN' }, { maxAge: force ? 0 : 10000 });
    detectGoals(d, !!todayData);
    todayData = d;
    feedListeners.forEach((f) => f(d));
    if (hasFollows()) detectLineups(d);
  } catch (e) {
    feedListeners.forEach((f) => f(null, e));
  }
  // keep watching in a background tab (slower) so followed teams still get alerts
  if (!document.hidden) feedTimer = setTimeout(pollToday, REFRESH);
  else if (hasFollows() && store.notify) feedTimer = setTimeout(pollToday, 60e3);
}
document.addEventListener('visibilitychange', () => { if (!document.hidden) pollToday(true); });

/* ---------- home: ticker + big games ---------- */
const BIG_LEAGUES = new Set([42, 47, 87, 55, 54, 53, 523, 73, 77, 50, 44, 289, 10216]);
const hue = (id) => `hsl(${(id * 47) % 360} 55% 34%)`;
const teamColor = (id) => club(id)?.c[0] || hue(+id);

function tickerHtml(leagues) {
  const live = leagues.flatMap((l) => l.matches.filter((m) => isLive(m.status)).map((m) => ({ m, l })))
    .sort((a, b) => BIG_LEAGUES.has(b.l.primaryId) - BIG_LEAGUES.has(a.l.primaryId)).slice(0, 24);
  if (!live.length) return '';
  const item = ({ m }) => `<a href="#/match/${m.id}"><img src="${teamImg(m.home.id)}" alt="" onerror="this.remove()">${esc(m.home.shortName || m.home.name)}
    <b>${fa(m.home.score)}-${fa(m.away.score)}</b>${esc(m.away.shortName || m.away.name)}<img src="${teamImg(m.away.id)}" alt="" onerror="this.remove()"><i>${statusShort(m.status).replace(/<[^>]+>/g, '')}</i></a>`;
  return `<div class="ticker"><span class="ticker-tag"><span class="dot pulse"></span>LIVE</span>
    <div class="ticker-track" style="--dur:${Math.max(20, live.length * 7)}s">${live.map(item).join('')}</div></div>`;
}

function featuredHtml(leagues) {
  const score = ({ m, l }) => (isLive(m.status) ? 100 : 0) + (club(m.home.id) ? 30 : 0) + (club(m.away.id) ? 30 : 0) +
    (derbyName(m.home.id, m.away.id) ? 40 : 0) + (BIG_LEAGUES.has(l.primaryId) ? 15 : 0) + (followed(m, l) ? 50 : 0) - (m.status.finished ? 20 : 0);
  const picks = leagues.flatMap((l) => l.matches.filter((m) => !m.status.cancelled).map((m) => ({ m, l })))
    .filter((x) => score(x) >= 30).sort((a, b) => score(b) - score(a)).slice(0, 8);
  if (!picks.length) return '';
  return `<div class="section-title">بازی‌های بزرگ</div><div class="featured">${picks.map(({ m, l }, i) => {
    const s = m.status, live = isLive(s), started = s.started && !s.cancelled;
    const mid = started ? `${score2(m)}<small>${live ? statusShort(s) : statusShort(s)}</small>` : `${timeOf(s.utcTime)}<small>${dateOf(s.utcTime, { weekday: 'short' })}</small>`;
    return `<a class="fcard rise" href="#/match/${m.id}" style="--i:${i};--c1:${teamColor(m.home.id)};--c2:${teamColor(m.away.id)}"><span class="shine"></span>
      <div class="tag"><span>${esc(l.name)}</span>${live ? '<span class="pill live"><span class="dot"></span>زنده</span>' : ''}</div>
      <div class="vs"><div><img src="${teamImgL(m.home.id)}" alt="" onerror="this.style.visibility='hidden'"><span>${esc(m.home.name)}</span></div>
        <div class="mid">${mid}</div>
        <div><img src="${teamImgL(m.away.id)}" alt="" onerror="this.style.visibility='hidden'"><span>${esc(m.away.name)}</span></div></div>
      ${derbyName(m.home.id, m.away.id) ? `<div class="derby">⚡ ${derbyName(m.home.id, m.away.id)}</div>` : ''}</a>`;
  }).join('')}</div>`;
}
const score2 = (m) => `<span class="scr"><span>${fa(m.home.score)}</span><span class="sep">-</span><span>${fa(m.away.score)}</span></span>`;

/* ---------- home: matches by day ---------- */
const homeState = { filter: 'all', sort: 'league' };

async function homeView(el, _p, q) {
  const today = ymd(new Date());
  const day = /^\d{8}$/.test(q.get('d') || '') ? q.get('d') : today;
  const isToday = day === today;
  let data = isToday ? todayData : null, error = null, lastOk = data ? Date.now() : 0;

  const dates = [];
  const base = parseYmd(day);
  for (let i = -3; i <= 3; i++) { const d = new Date(base); d.setDate(d.getDate() + i); dates.push(d); }
  const label = (d) => {
    const k = ymd(d), diff = Math.round((parseYmd(k) - parseYmd(today)) / 864e5);
    const top = diff === 0 ? 'امروز' : diff === -1 ? 'دیروز' : diff === 1 ? 'فردا' : d.toLocaleDateString('fa-IR', { weekday: 'short' });
    return `<a class="date ${k === day ? 'on' : ''}" href="#/?d=${k}">${top}<b>${d.toLocaleDateString('fa-IR', { day: 'numeric', month: 'short' })}</b></a>`;
  };
  el.innerHTML = `
    <div id="ticker"></div>
    <div id="featured"></div>
    <div class="dates">${dates.map(label).join('')}
      <label class="date date-pick" title="انتخاب تاریخ">${icon('cal')}<input type="date" value="${day.slice(0, 4)}-${day.slice(4, 6)}-${day.slice(6)}"></label></div>
    <div class="toolbar"><div id="filterSeg"></div>
      <button class="icon-btn" id="sortBtn" title="مرتب‌سازی">${icon(homeState.sort === 'time' ? 'list' : 'trophy')}</button></div>
    <div id="list">${skeleton(6)}</div>
    <div class="status-line" id="upd"></div>`;
  $('.date.on', el)?.scrollIntoView({ inline: 'center', block: 'nearest' });
  $('.date-pick input', el).onchange = (e) => { if (e.target.value) location.hash = '#/?d=' + e.target.value.replaceAll('-', ''); };
  $('#sortBtn', el).onclick = () => { homeState.sort = homeState.sort === 'league' ? 'time' : 'league'; $('#sortBtn', el).innerHTML = icon(homeState.sort === 'time' ? 'list' : 'trophy'); toast(homeState.sort === 'time' ? 'مرتب‌سازی بر اساس زمان' : 'مرتب‌سازی بر اساس لیگ'); render(); };
  el.addEventListener('click', (e) => {
    const b = e.target.closest('#filterSeg button');
    if (b) { homeState.filter = b.dataset.v; render(); return; }
    const h = e.target.closest('[data-toggle]');
    if (h && !e.target.closest('a,button')) {
      const id = h.dataset.toggle;
      store.collapsed[id] = !store.collapsed[id]; save();
      h.parentElement.classList.toggle('collapsed', !!store.collapsed[id]);
    }
  });

  let painted = false;
  const setIfChanged = (box, html) => { if (box && box.dataset.html !== html) { box.dataset.html = html; box.innerHTML = html; } };
  function render() {
    const list = $('#list', el);
    if (!list) return;
    if (!data) { list.innerHTML = error ? errorBox(error) : skeleton(6); return; }
    const leagues = (data.leagues || []).map((l) => ({ ...l, matches: l.matches || [] }));
    setIfChanged($('#ticker', el), tickerHtml(leagues));
    setIfChanged($('#featured', el), featuredHtml(leagues));
    // list items animate in on the first paint only, not on every live refresh
    let n = 0;
    const idx = () => (painted ? undefined : n++);
    const all = leagues.flatMap((l) => l.matches);
    const liveN = all.filter((m) => isLive(m.status)).length;
    const favN = leagues.reduce((n, l) => n + l.matches.filter((m) => followed(m, l)).length, 0);
    $('#filterSeg', el).innerHTML = seg('filter', [
      ['all', `همه <span class="mute">${num(all.length)}</span>`],
      ['live', `زنده ${liveN ? `<span class="count">${num(liveN)}</span>` : ''}`],
      ['fav', `${icon('star', 15)} دنبال‌شده‌ها ${favN ? `<span class="mute">${num(favN)}</span>` : ''}`],
    ], homeState.filter);

    const keep = (m, l) => homeState.filter === 'all' || (homeState.filter === 'live' ? isLive(m.status) : followed(m, l));
    const now = Date.now();
    const flash = (html, m) => ((goalFlash.get(m.id) || 0) > now ? html.replace('class="m ', 'class="m goal ') : html);

    if (homeState.sort === 'time') {
      const rows = leagues.flatMap((l) => l.matches.filter((m) => keep(m, l)).map((m) => ({ m, l })))
        .sort((a, b) => (isLive(b.m.status) - isLive(a.m.status)) || (new Date(a.m.status.utcTime) - new Date(b.m.status.utcTime)));
      list.innerHTML = rows.length ? `<div class="card">${rows.map(({ m, l }) =>
        flash(matchRow(m, { i: idx(), extra: `<span class="agg">${esc(l.name)}</span>` }), m)).join('')}</div>` : emptyFor();
      painted = true;
      return;
    }
    const favL = (l) => (isFav('leagues', l.primaryId) ? 2 : 0) + (l.matches.some((m) => followed(m, l)) ? 1 : 0);
    const blocks = leagues.map((l) => ({ l, ms: l.matches.filter((m) => keep(m, l)) })).filter((x) => x.ms.length)
      .sort((a, b) => favL(b.l) - favL(a.l));
    list.innerHTML = blocks.map(({ l, ms }) => {
      const coll = store.collapsed[l.id] && homeState.filter === 'all';
      const live = ms.filter((m) => isLive(m.status)).length;
      return `<section class="card lg ${coll ? 'collapsed' : ''} ${painted ? '' : 'rise'}" style="--i:${painted ? 0 : n++}">
        <div class="lg-h" data-toggle="${l.id}">
          ${lgLogo(l.primaryId, '')}
          <a class="grow ellipsis" href="#/league/${l.primaryId}"><span class="name">${esc(l.name)}</span> <span class="cc">${esc(l.ccode === 'INT' ? '' : l.ccode)}</span></a>
          ${live ? `<span class="pill live"><span class="dot"></span>${num(live)}</span>` : ''}
          ${starBtn('leagues', l.primaryId, l.name)}
          <span class="chev">${icon('down', 18)}</span>
        </div>
        <div class="lg-body">${ms.map((m) => flash(matchRow(m, { i: idx() }), m)).join('')}</div></section>`;
    }).join('') || emptyFor();
    painted = true;
  }
  const emptyFor = () => homeState.filter === 'live' ? empty('الان بازی زنده‌ای در جریان نیست', 'live')
    : homeState.filter === 'fav' ? empty('هنوز تیم، لیگ یا بازی‌ای را دنبال نکرده‌اید. روی ستاره‌ها بزنید.', 'star') : empty('برای این روز مسابقه‌ای پیدا نشد');

  const upd = () => {
    const u = $('#upd', el);
    if (!u) return;
    if (error) { u.className = 'status-line bad'; u.innerHTML = `${icon('info', 14)} اتصال قطع شد؛ دوباره تلاش می‌کنیم`; return; }
    if (!lastOk) return;
    const s = Math.round((Date.now() - lastOk) / 1000);
    u.className = 'status-line';
    u.innerHTML = `<span class="dot pulse" style="color:var(--accent)"></span> ${s < 5 ? 'همین الان به‌روز شد' : `${num(s)} ثانیه پیش به‌روز شد`} · به‌روزرسانی خودکار`;
  };
  const clock = setInterval(upd, 1000);

  let timer;
  if (isToday) {
    const onFeed = (d, e) => { if (d) { data = d; error = null; lastOk = Date.now(); } else error = e; render(); upd(); };
    feedListeners.add(onFeed);
    if (data) render(); else pollToday(true);
    return () => { feedListeners.delete(onFeed); clearInterval(clock); };
  }
  const load = async () => {
    clearTimeout(timer);
    try {
      data = await api('matches', { date: day, timezone: tz, ccode3: 'IRN' }, { maxAge: 0 });
      detectGoals(data, false); error = null; lastOk = Date.now();
    } catch (e) { error = e; }
    render(); upd();
    // past/future days only need polling while something is still being played
    const anyLive = (data?.leagues || []).some((l) => l.matches.some((m) => isLive(m.status)));
    if (anyLive && !document.hidden) timer = setTimeout(load, REFRESH);
  };
  load();
  return () => { clearTimeout(timer); clearInterval(clock); };
}

/* ---------- leagues directory ---------- */
async function leaguesView(el) {
  el.innerHTML = `<div class="search">${icon('search')}<input id="lq" placeholder="جستجوی لیگ یا کشور…" autocomplete="off"></div><div id="ll">${skeleton(8)}</div>`;
  let d;
  try { d = await api('allLeagues', { locale: 'en', country: 'IRN' }, { maxAge: 36e5 }); } catch (e) { $('#ll', el).innerHTML = errorBox(e); return; }
  const row = (l) => `<a class="list-row" href="#/league/${l.id}">${lgLogo(l.id, 'crest')}<span class="grow ellipsis">${esc(l.localizedName || l.name)}</span>${starBtn('leagues', l.id, l.name)}</a>`;
  const favs = Object.entries(store.leagues);
  const draw = (q = '') => {
    q = q.trim().toLowerCase();
    const match = (l) => !q || (l.localizedName || l.name).toLowerCase().includes(q);
    if (q) {
      const hits = [...d.popular, ...d.international.flatMap((c) => c.leagues), ...d.countries.flatMap((c) => c.leagues.map((l) => ({ ...l, cname: c.name })))]
        .filter((l, i, a) => a.findIndex((x) => x.id === l.id) === i)
        .filter((l) => match(l) || (l.cname || '').toLowerCase().includes(q));
      $('#ll', el).innerHTML = hits.length ? `<div class="card">${hits.slice(0, 80).map(row).join('')}</div>` : empty('چیزی پیدا نشد', 'search');
      return;
    }
    $('#ll', el).innerHTML = `
      ${favs.length ? `<div class="section-title">لیگ‌های دنبال‌شده</div><div class="card">${favs.map(([id, name]) => row({ id, name })).join('')}</div>` : ''}
      <div class="section-title">محبوب</div><div class="card">${d.popular.map(row).join('')}</div>
      <div class="section-title">بین‌المللی</div><div class="card">${d.international.flatMap((c) => c.leagues).map(row).join('')}</div>
      <div class="section-title">کشورها</div><div class="card">${d.countries.map((c) => `
        <details><summary class="list-row" style="cursor:pointer;list-style:none"><img class="crest" src="${'https://images.fotmob.com/image_resources/logo/teamlogo/' + c.ccode.toLowerCase() + '.png'}" alt="" loading="lazy" onerror="this.style.visibility='hidden'">
          <span class="grow">${esc(c.localizedName || c.name)}</span><span class="end">${num(c.leagues.length)} ${icon('down', 16)}</span></summary>
          ${c.leagues.map(row).join('')}</details>`).join('')}</div>`;
  };
  draw();
  $('#lq', el).oninput = (e) => draw(e.target.value);
}

/* ---------- favourites ---------- */
async function favView(el) {
  const teams = Object.entries(store.teams), leagues = Object.entries(store.leagues), matches = Object.entries(store.matches);
  if (!teams.length && !leagues.length && !matches.length) {
    el.innerHTML = `<div class="section-title">دنبال‌شده‌ها</div><div class="card">${empty('هنوز چیزی دنبال نکرده‌اید. روی ⭐ کنار تیم‌ها، لیگ‌ها و بازی‌ها بزنید تا اینجا جمع شوند و گل‌هایشان اعلان شود.', 'star')}</div>`;
    return;
  }
  el.innerHTML = `
    ${teams.length ? `<div class="section-title">تیم‌ها</div><div id="favTeams">${teams.map(([id, name]) => `
      <div class="card" data-team="${id}"><a class="list-row" href="#/team/${id}">${crest(id, 'md')}<div class="grow"><b>${esc(name)}</b><div class="meta" data-next>…</div></div>${starBtn('teams', id, name)}</a></div>`).join('')}</div>` : ''}
    ${leagues.length ? `<div class="section-title">لیگ‌ها</div><div class="card">${leagues.map(([id, name]) => `
      <a class="list-row" href="#/league/${id}">${lgLogo(id, 'crest md')}<span class="grow"><b>${esc(name)}</b></span>${starBtn('leagues', id, name)}</a>`).join('')}</div>` : ''}
    ${matches.length ? `<div class="section-title">بازی‌ها</div><div class="card">${matches.map(([id, name]) => `
      <a class="list-row" href="#/match/${id}">${icon('ball')}<span class="grow">${esc(name)}</span>${starBtn('matches', id, name)}</a>`).join('')}</div>` : ''}`;
  // show each followed team's next match
  for (const [id] of teams) {
    api('teams', { id, ccode3: 'IRN' }, { maxAge: 3e5 }).then((t) => {
      const n = t.overview?.nextMatch, box = $(`[data-team="${id}"] [data-next]`, el);
      if (!box) return;
      if (!n) { box.textContent = ''; return; }
      box.innerHTML = `بازی بعد: ${esc(n.opponent?.name || '')} · ${dateOf(n.status.utcTime)} ${new Date(n.status.utcTime).toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' })}`;
    }).catch(() => {});
  }
}

/* ---------- search ---------- */
async function searchView(el, _p, q) {
  el.innerHTML = `<div class="search">${icon('search')}<input id="sq" placeholder="تیم، بازیکن، لیگ…" autocomplete="off" value="${esc(q.get('q') || '')}"></div><div id="sr"></div>`;
  const input = $('#sq', el), out = $('#sr', el);
  let t, seq = 0;
  const run = async () => {
    const term = input.value.trim();
    history.replaceState(null, '', '#/search' + (term ? '?q=' + encodeURIComponent(term) : ''));
    if (term.length < 2) { out.innerHTML = empty('حداقل دو حرف بنویسید (نام‌ها انگلیسی هستند، مثل Perspolis یا Messi)', 'search'); return; }
    const my = ++seq;
    out.innerHTML = skeleton(4);
    try {
      const d = await api('suggest', { term }, { maxAge: 6e4 });
      if (my !== seq) return;
      const all = (d.find((g) => g.title?.key === 'all') || d[0])?.suggestions || [];
      const groups = { team: [], player: [], league: [], match: [] };
      for (const s of d.flatMap((g) => g.suggestions)) if (groups[s.type] && !groups[s.type].some((x) => x.id === s.id)) groups[s.type].push(s);
      if (!all.length && !Object.values(groups).some((g) => g.length)) { out.innerHTML = empty('نتیجه‌ای پیدا نشد', 'search'); return; }
      const sec = (title, arr, fn) => arr.length ? `<div class="section-title">${title}</div><div class="card">${arr.map(fn).join('')}</div>` : '';
      out.innerHTML =
        sec('تیم‌ها', groups.team, (s) => `<a class="list-row" href="#/team/${s.id}">${crest(s.id, 'md')}<div class="grow"><b>${esc(s.name)}</b><div class="meta">${esc(s.leagueName || '')}</div></div>${starBtn('teams', s.id, s.name)}</a>`) +
        sec('بازیکنان', groups.player, (s) => `<a class="list-row" href="#/${s.isCoach ? 'player' : 'player'}/${s.id}">${face(s.id)}<div class="grow"><b>${esc(s.name)}</b><div class="meta">${esc(s.teamName || '')}${s.isCoach ? ' · مربی' : ''}</div></div></a>`) +
        sec('لیگ‌ها', groups.league, (s) => `<a class="list-row" href="#/league/${s.id}">${lgLogo(s.id, 'crest md')}<div class="grow"><b>${esc(s.name)}</b><div class="meta">${esc(s.countryCode || '')}</div></div>${starBtn('leagues', s.id, s.name)}</a>`) +
        sec('بازی‌ها', groups.match, (s) => matchRow({ id: s.id, home: { id: s.homeTeamId, name: s.homeTeamName, score: s.homeScore }, away: { id: s.awayTeamId, name: s.awayTeamName, score: s.awayScore }, status: s.status }, { date: true }));
    } catch (e) { if (my === seq) out.innerHTML = errorBox(e); }
  };
  input.oninput = () => { clearTimeout(t); t = setTimeout(run, 280); };
  input.focus();
  run();
}

/* ---------- install as an app ---------- */
let installEvt = null;
const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone || !!window.Capacitor?.isNativePlatform?.();
addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installEvt = e; $('#installBtn')?.classList.remove('hide'); });
addEventListener('appinstalled', () => { installEvt = null; $('#installBtn')?.classList.add('hide'); toast('گل‌لایو نصب شد'); });
async function installApp() {
  if (installEvt) { installEvt.prompt(); await installEvt.userChoice; installEvt = null; return; }
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  sheet(`<div class="card-h" style="font-size:16px">نصب گل‌لایو<span class="end"><button class="icon-btn" data-close>${icon('x')}</button></span></div>
    <div style="padding:4px 16px 20px;line-height:2">${standalone ? 'گل‌لایو روی این دستگاه نصب شده است.' : ios
      ? '۱. این صفحه را در <b>Safari</b> باز کنید.<br>۲. دکمه <b>اشتراک‌گذاری</b> (مربع با فلش رو به بالا) را بزنید.<br>۳. گزینه <b>Add to Home Screen</b> را انتخاب کنید.'
      : '۱. منوی مرورگر (سه نقطه بالای صفحه) را باز کنید.<br>۲. گزینه <b>Install app</b> یا <b>Add to Home screen</b> را بزنید.<br><span class="mute">بهترین نتیجه با Chrome است.</span>'}</div>`);
}
$('#installBtn').onclick = installApp;

/* ---------- settings ---------- */
function openSettings() {
  const sw = (on) => `<span class="switch ${on ? 'on' : ''}"></span>`;
  const notifOk = notifySupported();
  const s = sheet(`<div class="card-h" style="font-size:16px">تنظیمات<span class="end"><button class="icon-btn" data-close>${icon('x')}</button></span></div>
    <div class="settings">
      <div class="kv-h">ظاهر</div>
      <div style="padding:0 16px 12px">${seg('theme', [['', '🌙 بازی شبانه'], ['light', '☀️ بازی روز']], store.theme === 'light' ? 'light' : '')}</div>
      <div class="kv-h">اعلان‌ها</div>
      <div class="list-row" data-set="notify">${icon('bell')}<div class="grow"><b>اعلان‌ها</b><div class="meta">${!notifOk ? 'مرورگر شما از اعلان پشتیبانی نمی‌کند'
        : pushSupported() ? 'برای تیم‌ها، لیگ‌ها و بازی‌های دنبال‌شده؛ حتی وقتی اپ بسته است'
        : window.Capacitor ? 'در این نسخه فقط وقتی اپ باز است. برای اعلان با اپ بسته، سایت را در Chrome باز کنید و «Install app» را بزنید.' : 'برای دنبال‌شده‌ها، وقتی اپ باز است'}</div></div>${sw(store.notify)}</div>
      <div class="list-row" data-set="notifyGoals">${icon('ball')}<div class="grow"><b>گل‌ها</b><div class="meta">با نام گلزن و دقیقه</div></div>${sw(store.notifyGoals !== false)}</div>
      <div class="list-row" data-set="notifyLineups">${icon('shirt')}<div class="grow"><b>اعلام ترکیب</b><div class="meta">حدود یک ساعت قبل از بازی</div></div>${sw(store.notifyLineups !== false)}</div>
      <div class="list-row" data-set="notifyEvents">${icon('whistle')}<div class="grow"><b>شروع و پایان بازی</b></div>${sw(store.notifyEvents !== false)}</div>
      ${pushSupported() ? `<div class="list-row" data-set="testPush">${icon('info')}<div class="grow"><b>ارسال اعلان آزمایشی</b><div class="meta">برای اطمینان از رسیدن اعلان‌ها</div></div></div>` : ''}
      <div class="list-row" data-set="sound">${icon('live')}<div class="grow"><b>صدای گل</b></div>${sw(store.sound)}</div>
      <div class="kv-h">اپلیکیشن</div>
      <div class="list-row" data-set="install">${icon('shirt')}<div class="grow"><b>نصب گل‌لایو روی گوشی</b><div class="meta">${standalone ? 'نصب شده است' : 'مثل یک اپ، با آیکون روی صفحه اصلی و تمام‌صفحه'}</div></div></div>
      <div class="kv-h">داده‌ها</div>
      <div class="list-row" data-set="clear">${icon('x')}<div class="grow"><b>پاک کردن علاقه‌مندی‌ها</b></div></div>
      <div class="list-row" style="cursor:default">${icon('info')}<div class="grow meta">داده‌ها از FotMob گرفته می‌شود (غیررسمی). بازی‌های زنده هر ۲۰ ثانیه به‌روز می‌شوند.</div></div>
    </div>`);
  s.addEventListener('click', async (e) => {
    const tb = e.target.closest('[data-seg="theme"] button');
    if (tb) { store.theme = tb.dataset.v; save(); applyTheme(); s.querySelectorAll('[data-seg="theme"] button').forEach((b) => b.classList.toggle('on', b === tb)); return; }
    const r = e.target.closest('[data-set]');
    if (!r) return;
    const k = r.dataset.set;
    if (k === 'install') { s.remove(); installApp(); return; }
    if (k === 'testPush') {
      if (!store.notify) { toast('اول اعلان‌ها را روشن کنید'); return; }
      toast('در حال ارسال…');
      const ok = (await syncPush()) && (await testPush());
      toast(ok ? 'اعلان آزمایشی ارسال شد' : 'ارسال نشد؛ اتصال یا اجازه اعلان را بررسی کنید');
      return;
    }
    if (k === 'clear') {
      if (confirm('همه علاقه‌مندی‌ها پاک شوند؟')) { store.teams = {}; store.leagues = {}; store.matches = {}; save(); toast('پاک شد'); route(); }
      return;
    }
    if (k === 'notify' && !store.notify && notifOk) {
      if (!(await notifyPermission(true))) { toast('اجازه اعلان داده نشد'); return; }
    }
    store[k] = k.startsWith('notify') && k !== 'notify' ? store[k] === false : !store[k]; save();
    r.querySelector('.switch').classList.toggle('on', store[k] !== false && !!store[k]);
    if (k === 'notify' && store.notify) pollToday(true);
    if (k.startsWith('notify')) syncPush().then((ok) => { if (k === 'notify' && store.notify && pushSupported()) toast(ok ? 'اعلان‌ها حتی با اپ بسته هم می‌آیند' : 'اعلان فقط وقتی اپ باز است کار می‌کند'); });
  });
}

/* ---------- router ---------- */
const routes = [
  [/^\/?$/, homeView, 'matches'],
  [/^\/match\/(\d+)(?:\/(\w+))?$/, matchView, 'matches'],
  [/^\/league\/(\d+)(?:\/(\w+))?$/, leagueView, 'leagues'],
  [/^\/team\/(\d+)(?:\/(\w+))?$/, teamView, ''],
  [/^\/player\/(\d+)$/, playerView, ''],
  [/^\/leagues$/, leaguesView, 'leagues'],
  [/^\/favorites$/, favView, 'favorites'],
  [/^\/search$/, searchView, 'search'],
];
let cleanup = null, lastPath = '';
async function route() {
  const raw = location.hash.slice(1) || '/';
  const [path, qs] = raw.split('?');
  const q = new URLSearchParams(qs || '');
  const hit = routes.find(([re]) => re.test(path)) || routes[0];
  const params = path.match(hit[0]) || [];
  if (typeof cleanup === 'function') cleanup();
  cleanup = null;
  const samePage = path.split('/').slice(0, 3).join('/') === lastPath.split('/').slice(0, 3).join('/');
  lastPath = path;
  document.querySelectorAll('.nav a').forEach((a) => a.classList.toggle('on', a.dataset.nav === hit[2]));
  document.body.classList.toggle('has-back', hit[1] !== homeView && !['leagues', 'favorites', 'search'].includes(path.slice(1)));
  const el = document.createElement('div');
  el.className = samePage ? '' : 'view';
  view.replaceChildren(el);
  if (!samePage) scrollTo(0, 0);
  try { cleanup = await hit[1](el, params.slice(1), q); } catch (e) { el.innerHTML = errorBox(e); console.error(e); }
}
addEventListener('hashchange', route);
route();
pollToday(true);

if ('serviceWorker' in navigator && (location.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(location.hostname))) {
  navigator.serviceWorker.register('/sw.js').then(() => { if (store.notify) syncPush(); }).catch(() => {});
}
