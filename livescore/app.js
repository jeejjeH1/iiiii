const $ = s => document.querySelector(s);
const fa = n => String(n).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]);
const rnd = (a, b) => a + Math.floor(Math.random() * (b - a + 1));
const pick = a => a[rnd(0, a.length - 1)];
const TICK_MS = 2000;           // هر ۲ ثانیه = ۱ دقیقه بازی
const DAY = 86400000;

let favs = new Set();
try { favs = new Set(JSON.parse(localStorage.getItem('favs') || '[]')); } catch (e) {}
const saveFavs = () => { try { localStorage.setItem('favs', JSON.stringify([...favs])); } catch (e) {} };

const state = { day: 0, filter: 'all', q: '', view: 'matches', openId: null, tab: 'ev', tblLeague: 'irn' };
const matches = [];
let seq = 1;

function newStats() {
  return { possession: [50, 50], shots: [0, 0], shotsOn: [0, 0], corners: [0, 0], fouls: [0, 0] };
}

function makeMatch(league, day, status, startIn) {
  const t = [...league.teams].sort(() => Math.random() - .5);
  const m = { id: seq++, league: league.id, day, home: t[0], away: t[1], hs: 0, as: 0,
    status, minute: 0, startIn: startIn || 0, htLeft: 0, events: [], stats: newStats(),
    time: `${fa(String(rnd(15, 22)).padStart(2, '0'))}:${pick(['۰۰', '۳۰', '۱۵', '۴۵'])}` };
  if (status === 'ft') { m.minute = 90; simulateFull(m); }
  if (status === 'live') { m.minute = rnd(3, 80); simulateTo(m, m.minute); }
  return m;
}
function simulateTo(m, minute) { for (let i = 1; i <= minute; i++) { m.minute = i; step(m, true); } m.minute = minute; }
function simulateFull(m) { simulateTo(m, 90); m.status = 'ft'; }

function addEvent(m, type, side, minute) {
  m.events.push({ type, side, minute, player: pick(PLAYERS) });
}

// یک دقیقه بازی
function step(m, silent) {
  const s = m.stats;
  [0, 1].forEach(i => {
    if (Math.random() < .09) s.shots[i]++, Math.random() < .45 && s.shotsOn[i]++;
    if (Math.random() < .05) s.corners[i]++;
    if (Math.random() < .08) s.fouls[i]++;
    if (Math.random() < .027 + (i ? 0 : .004)) { i ? m.as++ : m.hs++; s.shots[i]++; s.shotsOn[i]++; addEvent(m, 'goal', i, m.minute); }
    else if (Math.random() < .012) addEvent(m, 'yellow', i, m.minute);
    else if (Math.random() < .0015) addEvent(m, 'red', i, m.minute);
  });
  const p = Math.max(35, Math.min(65, s.possession[0] + rnd(-2, 2)));
  s.possession = [p, 100 - p];
}

function tick() {
  matches.forEach(m => {
    if (m.status === 'up' && m.day === 0) {
      if (--m.startIn <= 0) { m.status = 'live'; m.minute = 1; m.stats.possession = [50, 50]; }
    } else if (m.status === 'ht') {
      if (--m.htLeft <= 0) { m.status = 'live'; m.minute = 46; }
    } else if (m.status === 'live') {
      m.minute++;
      if (m.minute === 46 && !m.htDone) { m.htDone = true; m.status = 'ht'; m.htLeft = 4; m.minute = 45; return; }
      step(m);
      if (m.minute >= 90 + (m.extra ??= rnd(1, 5))) m.status = 'ft';
    }
  });
  render();
  if (state.openId) renderDetail();
}

function init() {
  LEAGUES.forEach(l => {
    for (let d = -3; d <= 3; d++) {
      const n = rnd(2, 3);
      for (let i = 0; i < n; i++) {
        if (d < 0) matches.push(makeMatch(l, d, 'ft'));
        else if (d > 0) matches.push(makeMatch(l, d, 'up'));
        else {
          const r = Math.random();
          if (r < .35) matches.push(makeMatch(l, 0, 'live'));
          else if (r < .65) matches.push(makeMatch(l, 0, 'ft'));
          else matches.push(makeMatch(l, 0, 'up', rnd(5, 120)));
        }
      }
    }
  });
  matches.filter(m => m.status === 'live' && m.minute > 45).forEach(m => m.htDone = true);
  buildDays(); buildLeagueTabs(); bind(); render();
  setInterval(tick, TICK_MS);
}

function buildDays() {
  const fmt = new Intl.DateTimeFormat('fa-IR', { weekday: 'short', day: 'numeric', month: 'short' });
  const names = { '-1': 'دیروز', 0: 'امروز', 1: 'فردا' };
  $('#days').innerHTML = [-3, -2, -1, 0, 1, 2, 3].map(d => {
    const parts = fmt.format(Date.now() + d * DAY).split('،');
    const date = parts[parts.length - 1].trim();
    return `<button class="day ${d === state.day ? 'active' : ''}" data-d="${d}">${names[d] || parts[0]}<b>${date}</b></button>`;
  }).join('');
  const act = $('#days .active'); act && act.scrollIntoView({ inline: 'center', block: 'nearest' });
}

const badge = t => `<span class="badge" style="background:${t[1]}">${t[0].slice(0, 2)}</span>`;
const lname = id => LEAGUES.find(l => l.id === id);

function statusText(m) {
  if (m.status === 'live') return fa(m.minute) + '′';
  if (m.status === 'ht') return 'نیمه';
  if (m.status === 'ft') return 'پایان';
  return m.time;
}

function render() {
  const live = matches.filter(m => m.status === 'live' || m.status === 'ht').length;
  $('#liveCount').textContent = fa(live);
  const q = state.q.trim();
  let list = matches.filter(m => m.day === state.day);
  const f = state.filter;
  if (f === 'live') list = matches.filter(m => m.status === 'live' || m.status === 'ht');
  if (f === 'fav') list = list.filter(m => favs.has(m.id));
  if (f === 'ft') list = list.filter(m => m.status === 'ft');
  if (f === 'up') list = list.filter(m => m.status === 'up');
  if (q) list = list.filter(m => (m.home[0] + m.away[0] + lname(m.league).name).includes(q));
  if (!list.length) { $('#list').innerHTML = '<div class="empty">بازی‌ای پیدا نشد</div>'; return; }
  $('#list').innerHTML = LEAGUES.map(l => {
    const ms = list.filter(m => m.league === l.id).sort((a, b) => (a.status === 'live' ? 0 : 1) - (b.status === 'live' ? 0 : 1));
    if (!ms.length) return '';
    return `<div class="league"><div class="lh">${l.name} <small>${l.country}</small></div>${ms.map(row).join('')}</div>`;
  }).join('');
}

function row(m) {
  const started = m.status !== 'up';
  const cls = (m.status === 'live') ? 'live' : '';
  return `<div class="match" data-id="${m.id}">
    <button class="star ${favs.has(m.id) ? 'on' : ''}" data-fav="${m.id}">${favs.has(m.id) ? '★' : '☆'}</button>
    <div class="team h">${badge(m.home)}<span class="n">${m.home[0]}</span></div>
    <div class="score ${cls}">${started ? `${fa(m.hs)} - ${fa(m.as)}` : '–'}<span class="st ${m.status === 'live' ? 'pulse' : ''}">${statusText(m)}</span></div>
    <div class="team a">${badge(m.away)}<span class="n">${m.away[0]}</span></div></div>`;
}

function renderDetail() {
  const m = matches.find(x => x.id === state.openId);
  if (!m) return;
  const started = m.status !== 'up';
  const evIcon = { goal: '⚽', yellow: '🟨', red: '🟥' };
  const evs = [...m.events].reverse().map(e => `<div class="ev">
      <div class="r x">${e.side === 0 ? `${evIcon[e.type]} ${e.player}` : ''}</div>
      <div class="m">${fa(e.minute)}′</div>
      <div class="l x">${e.side === 1 ? `${e.player} ${evIcon[e.type]}` : ''}</div></div>`).join('');
  const stats = STATS_KEYS.map(([k, label, suf]) => {
    const [a, b] = m.stats[k]; const tot = (a + b) || 1;
    return `<div class="sb"><div class="row"><b>${fa(a)}${suf || ''}</b><span>${label}</span><b>${fa(b)}${suf || ''}</b></div>
      <div class="bar"><i style="width:${a / tot * 100}%;background:${m.home[1]}"></i><i style="width:${b / tot * 100}%;background:${m.away[1]}"></i></div></div>`;
  }).join('');
  $('#detail').innerHTML = `
    <div class="dh">${lname(m.league).name} · ${statusText(m)}</div>
    <div class="big"><div>${badge(m.home)}<div>${m.home[0]}</div></div>
      <div class="sc ${m.status === 'live' ? 'live' : ''}">${started ? `${fa(m.hs)} - ${fa(m.as)}` : fa(m.time)}</div>
      <div>${badge(m.away)}<div>${m.away[0]}</div></div></div>
    <div class="tabs"><button class="chip ${state.tab === 'ev' ? 'active' : ''}" data-tab="ev">رویدادها</button>
      <button class="chip ${state.tab === 'st' ? 'active' : ''}" data-tab="st">آمار</button></div>
    ${!started ? '<div class="empty">بازی هنوز شروع نشده است</div>' :
      state.tab === 'ev' ? (evs || '<div class="empty">رویدادی ثبت نشده</div>') : stats}`;
}

// جدول لیگ (نمایشی)
function standings(id) {
  const l = lname(id);
  const rows = l.teams.map((t, i) => {
    const p = 10 + (i % 2), w = Math.max(1, 8 - i), d = i % 3, ls = p - w - d;
    const gf = 22 - i * 2 + (i % 3), ga = 8 + i * 2;
    return { t, p, w, d, l: ls, gd: gf - ga, pts: w * 3 + d, gf, ga };
  }).sort((a, b) => b.pts - a.pts || b.gd - a.gd);
  return `<table><tr><th>#</th><th style="text-align:right">تیم</th><th>بازی</th><th class="hide-s">برد</th><th class="hide-s">مساوی</th><th class="hide-s">باخت</th><th>تفاضل</th><th>امتیاز</th></tr>
    ${rows.map((r, i) => `<tr><td>${fa(i + 1)}</td><td class="tn"><div class="team">${badge(r.t)}<span class="n">${r.t[0]}</span></div></td>
    <td>${fa(r.p)}</td><td class="hide-s">${fa(r.w)}</td><td class="hide-s">${fa(r.d)}</td><td class="hide-s">${fa(r.l)}</td><td style="direction:ltr">${fa(r.gd)}</td><td><b>${fa(r.pts)}</b></td></tr>`).join('')}</table>`;
}
function buildLeagueTabs() {
  $('#leagueTabs').innerHTML = LEAGUES.map(l => `<button class="chip ${l.id === state.tblLeague ? 'active' : ''}" data-l="${l.id}">${l.name}</button>`).join('');
  $('#standings').innerHTML = standings(state.tblLeague);
}

function bind() {
  document.addEventListener('click', e => {
    const t = e.target.closest('button, .match'); if (!t) return;
    if (t.dataset.fav) { const id = +t.dataset.fav; favs.has(id) ? favs.delete(id) : favs.add(id); saveFavs(); render(); return; }
    if (t.dataset.d !== undefined) { state.day = +t.dataset.d; if (state.filter === 'live') state.filter = 'all'; syncChips(); buildDays(); render(); return; }
    if (t.dataset.f) { state.filter = t.dataset.f; syncChips(); render(); return; }
    if (t.dataset.view) { state.view = t.dataset.view; document.querySelectorAll('.navbtn').forEach(b => b.classList.toggle('active', b === t));
      $('#view-matches').hidden = state.view !== 'matches'; $('#view-table').hidden = state.view !== 'table'; return; }
    if (t.dataset.l) { state.tblLeague = t.dataset.l; buildLeagueTabs(); return; }
    if (t.dataset.tab) { state.tab = t.dataset.tab; renderDetail(); return; }
    if (t.id === 'closeModal') { closeModal(); return; }
    if (t.classList.contains('match')) { state.openId = +t.dataset.id; state.tab = 'ev'; $('#modal').hidden = false; renderDetail(); }
  });
  $('#modal').addEventListener('click', e => { if (e.target.id === 'modal') closeModal(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });
  $('#search').addEventListener('input', e => { state.q = e.target.value; render(); });
}
function syncChips() { document.querySelectorAll('#filters .chip').forEach(c => c.classList.toggle('active', c.dataset.f === state.filter)); }
function closeModal() { state.openId = null; $('#modal').hidden = true; }

init();
