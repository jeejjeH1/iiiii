import { tr } from './fa.js';
export { tr };

/* ---------- formatting ---------- */
export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const faDigits = (s) => String(s).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
export const fa = (v) => (v == null || v === '' ? '' : faDigits(v));
export const num = (n, opts) => (n == null || Number.isNaN(+n) ? '–' : (+n).toLocaleString('fa-IR', opts));
export const dec = (n, d = 2) => num(n, { minimumFractionDigits: d, maximumFractionDigits: d });
export const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
export const timeOf = (iso) => new Date(iso).toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' });
export const dateOf = (iso, o = { weekday: 'short', day: 'numeric', month: 'short' }) => new Date(iso).toLocaleDateString('fa-IR', o);
export const ymd = (d) => `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
export const parseYmd = (s) => new Date(+s.slice(0, 4), +s.slice(4, 6) - 1, +s.slice(6, 8));
export const money = (v) => {
  if (!v) return '–';
  if (v >= 1e6) return `€${num(+(v / 1e6).toFixed(1))} م`;
  if (v >= 1e3) return `€${num(Math.round(v / 1e3))} هزار`;
  return `€${num(v)}`;
};
// home score first, rendered right-to-left so it sits on the home team's (right) side
export const score = (h, a) => `<span class="scr"><span>${fa(h)}</span><span class="sep">-</span><span>${fa(a)}</span></span>`;
export const age = (iso) => Math.floor((Date.now() - new Date(iso)) / 31557600000);

/* ---------- images ---------- */
const IMG = 'https://images.fotmob.com/image_resources';
export const teamImg = (id) => `${IMG}/logo/teamlogo/${id}_small.png`;
export const teamImgL = (id) => `${IMG}/logo/teamlogo/${id}.png`;
export const leagueImg = (id) => `${IMG}/logo/leaguelogo/${isDark() ? 'dark/' : ''}${id}.png`;
export const playerImg = (id) => `${IMG}/playerimages/${id}.png`;
export const flagImg = (cc) => `${IMG}/logo/teamlogo/${String(cc || '').toLowerCase()}.png`;
const hideOnErr = `onerror="this.style.visibility='hidden'"`;
export const crest = (id, cls = '') => `<img class="crest ${cls}" src="${cls === 'lg' ? teamImgL(id) : teamImg(id)}" alt="" loading="lazy" ${hideOnErr}>`;
export const lgLogo = (id, cls = 'crest sm') => `<img class="${cls}" src="${leagueImg(id)}" alt="" loading="lazy" onerror="this.src='${IMG}/logo/leaguelogo/${id}.png';this.onerror=()=>this.style.visibility='hidden'">`;
export const face = (id, cls = 'avatar') => `<img class="${cls}" src="${playerImg(id)}" alt="" loading="lazy" onerror="this.src='data:image/svg+xml,${encodeURIComponent(PERSON)}'">`;
const PERSON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" fill="#8883"/><circle cx="20" cy="16" r="7" fill="#8889"/><path d="M6 38c2-8 8-12 14-12s12 4 14 12" fill="#8889"/></svg>';
export const isDark = () => document.documentElement.dataset.theme !== 'light';

/* ---------- icons (24px stroke) ---------- */
const P = {
  ball: '<circle cx="12" cy="12" r="9"/><path d="M12 7l4 3-1.5 4.5h-5L8 10z"/><path d="M12 3v4M16 10l4.5-1.5M14.5 14.5l2.5 4M9.5 14.5l-2.5 4M8 10L3.5 8.5"/>',
  trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0z"/><path d="M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 21h8M9 17h6"/>',
  star: '<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  back: '<path d="M9 6l6 6-6 6"/>',
  chev: '<path d="M15 6l-6 6 6 6"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  bell: '<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  cal: '<rect x="3.5" y="5" width="17" height="15" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  stadium: '<ellipse cx="12" cy="8" rx="9" ry="3.5"/><path d="M3 8v7c0 2 4 3.5 9 3.5s9-1.5 9-3.5V8"/>',
  whistle: '<circle cx="9" cy="14" r="5"/><path d="M12 10l9-4v4l-7 2"/>',
  people: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.5-4 3.3-6 6.5-6s6 2 6.5 6"/><circle cx="17" cy="9" r="2.5"/><path d="M16 14c3 0 5 1.8 5.5 5"/>',
  cloud: '<path d="M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 9.5a4 4 0 0 1 0 8.5z"/>',
  swap: '<path d="M7 4v14M3 14l4 4 4-4"/><path d="M17 20V6M13 10l4-4 4 4"/>',
  shirt: '<path d="M8 3l-5 3 2 4 2-1v12h10V9l2 1 2-4-5-3a4 4 0 0 1-8 0z"/>',
  bandage: '<rect x="2.5" y="8" width="19" height="8" rx="4" transform="rotate(-45 12 12)"/><path d="M10.5 10.5h.01M13.5 13.5h.01M13.5 10.5h.01M10.5 13.5h.01"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  pin: '<path d="M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  live: '<circle cx="12" cy="12" r="3"/><path d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 16.2a6 6 0 0 0 0-8.4M5 5a10 10 0 0 0 0 14M19 19a10 10 0 0 0 0-14"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  share: '<circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
};
export const icon = (n, s = 20, extra = '') => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" ${extra}>${P[n] || ''}</svg>`;

/* ---------- API ---------- */
const mem = new Map();
export async function api(e, params = {}, { maxAge = 15000, force = false } = {}) {
  const qs = new URLSearchParams({ e, ...params }).toString();
  const hit = mem.get(qs);
  if (!force && hit && Date.now() - hit.t < maxAge) return hit.data;
  const r = await fetch('/api/fm?' + qs);
  let data;
  try { data = await r.json(); } catch { throw new Error('پاسخ نامعتبر از سرور'); }
  if (!r.ok) throw new Error(data?.error || 'HTTP ' + r.status);
  mem.set(qs, { t: Date.now(), data });
  if (mem.size > 60) mem.delete(mem.keys().next().value);
  return data;
}

/* ---------- persisted settings ---------- */
const LS = 'goallive:v2';
function load() { try { return JSON.parse(localStorage.getItem(LS)) || {}; } catch { return {}; } }
export const store = Object.assign({ teams: {}, leagues: {}, matches: {}, collapsed: {}, theme: '', notify: false, sound: true }, load());
export function save() { try { localStorage.setItem(LS, JSON.stringify(store)); } catch {} }
export function toggleFav(kind, id, name) {
  const m = store[kind];
  if (m[id]) delete m[id]; else m[id] = name || true;
  save();
  toast(m[id] ? 'به علاقه‌مندی‌ها اضافه شد' : 'از علاقه‌مندی‌ها حذف شد');
  syncPush();
  return !!m[id];
}
export const isFav = (kind, id) => !!store[kind][id];
export const starBtn = (kind, id, name, cls = '') =>
  `<button class="star icon-btn ${cls} ${isFav(kind, id) ? 'on' : ''}" data-fav="${kind}" data-id="${id}" data-name="${esc(name)}" aria-label="علاقه‌مندی">${icon('star', 18)}</button>`;

/* ---------- UI helpers ---------- */
let toastTimer;
export function toast(msg) {
  document.querySelector('.toast')?.remove();
  const el = Object.assign(document.createElement('div'), { className: 'toast', textContent: msg });
  document.body.append(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 2200);
}
export function sheet(html) {
  const bg = document.createElement('div');
  bg.className = 'sheet-bg';
  bg.innerHTML = `<div class="sheet" role="dialog"><div class="grab"></div>${html}</div>`;
  const close = () => { bg.remove(); removeEventListener('keydown', onKey); };
  const onKey = (e) => e.key === 'Escape' && close();
  bg.addEventListener('click', (e) => { if (e.target === bg || e.target.closest('[data-close]') || e.target.closest('a[href]')) close(); });
  addEventListener('keydown', onKey);
  document.body.append(bg);
  return bg;
}
export const skeleton = (n = 4) => `<div class="card">${Array.from({ length: n }, () =>
  '<div style="display:flex;gap:12px;padding:16px;align-items:center"><div class="sk" style="width:36px;height:36px;border-radius:50%"></div><div style="flex:1"><div class="sk" style="height:12px;width:60%"></div><div class="sk" style="height:10px;width:35%;margin-top:8px"></div></div></div>').join('')}</div>`;
export const empty = (msg, ic = 'ball') => `<div class="empty">${icon(ic, 40)}${esc(msg)}</div>`;
export const errorBox = (e) => `<div class="empty err">${icon('info', 40)}خطا در دریافت اطلاعات<br><small class="mute">${esc(e.message || e)}</small></div>`;
export const tabs = (base, list, cur) => `<nav class="tabs">${list.map(([k, label]) => `<a href="${base}/${k}" class="${k === cur ? 'on' : ''}">${label}</a>`).join('')}</nav>`;
export const seg = (name, list, cur) => `<div class="seg" data-seg="${name}">${list.map(([k, label]) => `<button data-v="${k}" class="${String(k) === String(cur) ? 'on' : ''}">${label}</button>`).join('')}</div>`;

/* ---------- match helpers ---------- */
export const isLive = (s) => s && s.started && !s.finished && !s.cancelled;
export function statusShort(s) {
  if (!s) return '';
  if (s.cancelled) return tr(s.reason?.short) || 'لغو';
  if (s.finished) return tr(s.reason?.short || 'FT');
  if (s.started) {
    const t = s.liveTime?.short || '';
    return t === 'HT' ? 'بین دو نیمه' : `<span class="min-ltr">${fa(t.replace(/[\u200e\u200f]/g, '').replace('\u2019', "'"))}</span>`;
  }
  return timeOf(s.utcTime);
}
export const ratingClass = (r) => (r >= 9 ? 'r-hi' : r >= 7.5 ? 'r-g' : r >= 6.5 ? 'r-ok' : r >= 5.5 ? 'r-m' : 'r-lo');
export const rating = (r) => (+r ? `<span class="rate ${ratingClass(+r)}">${fa((+r).toFixed(1))}</span>` : '');
export const formChips = (arr) => `<span class="form">${arr.map((f) => {
  const r = f.resultString || ({ 1: 'W', 0: 'D', '-1': 'L' }[f.result]);
  const id = (f.linkToMatch || '').split('#')[1];
  const label = { W: 'ب', D: 'م', L: 'ش' }[r]; // برد / مساوی / شکست
  return id ? `<a class="f-${r}" href="#/match/${id}" title="${esc(f.score || '')}">${label}</a>` : `<span class="f-${r}">${label}</span>`;
}).join('')}</span>`;
export const matchIdFromUrl = (u) => String(u || '').split('#')[1] || '';

// One compact row for any match-like object {id, home:{id,name,score}, away, status}
export function matchRow(m, { showFav = true, extra = '', date = false, i } = {}) {
  const s = m.status || {};
  const live = isLive(s);
  const started = s.started || s.finished;
  const hs = m.home.score, as = m.away.score;
  let sc;
  if (started && !s.cancelled) {
    const [h, a] = s.scoreStr ? s.scoreStr.split(' - ') : [hs, as];
    sc = `<span>${fa(h)}</span><span class="sep">-</span><span>${fa(a)}</span>`;
  } else sc = `<span class="kick">${s.cancelled ? '–' : timeOf(s.utcTime)}</span>`;
  const st = live ? statusShort(s) : s.finished ? statusShort(s) : s.cancelled ? statusShort(s) : '';
  const hl = s.finished && hs < as ? 'lose' : '', al = s.finished && as < hs ? 'lose' : '';
  const minute = live ? parseInt(s.liveTime?.short) : NaN;
  return `<a class="m ${live ? 'live' : ''} ${s.finished ? 'fin' : ''} ${i != null ? 'rise' : ''}" ${i != null ? `style="--i:${i}"` : ''} href="#/match/${m.id}" data-mid="${m.id}">
    <div class="st">${live ? '<span class="dot pulse" style="display:inline-block;margin-inline-end:3px;vertical-align:middle"></span>' : ''}${st || (date ? dateOf(s.utcTime, { day: 'numeric', month: 'short' }) : '')}</div>
    <div class="tm h ${hl}">${crest(m.home.id)}<span>${esc(m.home.shortName || m.home.name)}</span>${m.home.redCards ? '<i class="rc"></i>' : ''}</div>
    <div class="sc">${sc}</div>
    <div class="tm a ${al}">${crest(m.away.id)}<span>${esc(m.away.shortName || m.away.name)}</span>${m.away.redCards ? '<i class="rc"></i>' : ''}</div>
    ${showFav ? `<button class="fav star ${isFav('matches', m.id) ? 'on' : ''}" data-fav="matches" data-id="${m.id}" data-name="${esc(m.home.name + ' - ' + m.away.name)}" aria-label="دنبال کردن">${icon('star', 16)}</button>` : '<span></span>'}
    ${extra}${minute > 0 ? `<i class="minute-bar" style="width:${Math.min(100, minute / 0.9)}%"></i>` : ''}</a>`;
}

/* ---------- delegated interactions ---------- */
document.addEventListener('click', (e) => {
  const f = e.target.closest('[data-fav]');
  if (f) {
    e.preventDefault(); e.stopPropagation();
    const on = toggleFav(f.dataset.fav, f.dataset.id, f.dataset.name);
    document.querySelectorAll(`[data-fav="${f.dataset.fav}"][data-id="${f.dataset.id}"]`).forEach((b) => b.classList.toggle('on', on));
    if (on && f.dataset.fav !== 'leagues') askNotify();
  }
});
export function askNotify() {
  if (!notifySupported() || store.askedNotify) return;
  store.askedNotify = true; save();
  notifyPermission(true).then((ok) => { if (ok) { store.notify = true; save(); syncPush(); toast('اعلان گل و ترکیب برای دنبال‌شده‌ها فعال شد'); } });
}

/* ---------- Web Push: lets the server notify even when the app is closed ---------- */
export const pushSupported = () => !plugins() && 'serviceWorker' in navigator && 'PushManager' in window;
const b64 = (s) => Uint8Array.from(atob((s + '='.repeat((4 - (s.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
let syncTimer;
// Subscribe (or refresh) and tell the server what this device follows; unsubscribes when alerts are off.
export function syncPush() {
  clearTimeout(syncTimer);
  return new Promise((done) => { syncTimer = setTimeout(() => doSync().then(done, (e) => { console.warn('push', e); done(false); }), 400); });
}
async function doSync() {
  if (!pushSupported()) return false;
  const reg = await navigator.serviceWorker.ready;
  let sub = await reg.pushManager.getSubscription();
  if (!store.notify || Notification.permission !== 'granted') {
    if (sub) { await fetch('/api/push?a=unsub', { method: 'POST', body: JSON.stringify({ endpoint: sub.endpoint }) }); await sub.unsubscribe(); }
    store.push = false; save();
    return false;
  }
  if (!sub) {
    const { key } = await (await fetch('/api/push?a=key')).json();
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64(key) });
  }
  const r = await fetch('/api/push?a=sub', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({
    subscription: sub.toJSON(), teams: Object.keys(store.teams), leagues: Object.keys(store.leagues), matches: Object.keys(store.matches),
    goals: store.notifyGoals !== false, lineups: store.notifyLineups !== false, events: store.notifyEvents !== false,
  }) });
  store.push = r.ok; save();
  return r.ok;
}
export async function testPush() {
  const sub = pushSupported() && (await (await navigator.serviceWorker.ready).pushManager.getSubscription());
  if (!sub) return false;
  return (await fetch('/api/push?a=test', { method: 'POST', body: JSON.stringify({ endpoint: sub.endpoint }) })).ok;
}

/* ---------- notifications: browser, or native when running inside the Android app ---------- */
const plugins = () => (window.Capacitor?.isNativePlatform?.() ? window.Capacitor.Plugins : null);
export const notifySupported = () => !!plugins()?.LocalNotifications || 'Notification' in window;
export async function notifyPermission(ask) {
  const LN = plugins()?.LocalNotifications;
  if (LN) {
    let p = (await LN.checkPermissions()).display;
    if (p !== 'granted' && ask) p = (await LN.requestPermissions()).display;
    return p === 'granted';
  }
  if (!('Notification' in window)) return false;
  if (Notification.permission === 'default' && ask) return (await Notification.requestPermission()) === 'granted';
  return Notification.permission === 'granted';
}
export async function showNotification(title, body, matchId, tag) {
  const LN = plugins()?.LocalNotifications;
  if (LN) {
    return LN.schedule({ notifications: [{ id: Math.floor(Math.random() * 2e9), title, body, largeBody: body, smallIcon: 'ic_stat_ball', iconColor: '#20d27c', extra: { matchId } }] });
  }
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  // via the service worker where possible: Android Chrome only allows that route
  const opts = { body, icon: '/icons/icon-192.png', badge: '/icons/icon-192.png', tag, renotify: true, data: { url: `/#/match/${matchId}` }, dir: 'rtl', lang: 'fa' };
  const reg = await navigator.serviceWorker?.getRegistration();
  if (reg) return reg.showNotification(title, opts);
  const n = new Notification(title, opts);
  n.onclick = () => { focus(); location.hash = `#/match/${matchId}`; n.close(); };
}
if (plugins()) {
  const { LocalNotifications: LN, App } = plugins();
  LN?.addListener('localNotificationActionPerformed', (e) => { const id = e.notification?.extra?.matchId; if (id) location.hash = `#/match/${id}`; });
  App?.addListener('backButton', ({ canGoBack }) => (canGoBack ? history.back() : App.exitApp()));
}
