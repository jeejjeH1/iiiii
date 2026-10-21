// Competition themes (Champions League nights, World Cup…) and the "my team won a trophy" celebration.
import { esc, api, store, save, icon, crest, teamImgL } from './core.js';
import { club } from './clubs.js';

/* ---------- competitions ---------- */
export const COMPS = {
  42: { key: 'ucl', fa: 'لیگ قهرمانان اروپا', tag: 'شب لیگ قهرمانان', accent: '#9cc3ff', bg: '#040b24', glow: '#3c6bff' },
  73: { key: 'uel', fa: 'لیگ اروپا', tag: 'شب لیگ اروپا', accent: '#ff8a2a', bg: '#140902', glow: '#ff6a00' },
  10216: { key: 'uecl', fa: 'لیگ کنفرانس', tag: 'شب لیگ کنفرانس', accent: '#35e08a', bg: '#03120a', glow: '#12b864' },
  77: { key: 'wc', fa: 'جام جهانی', tag: 'جام جهانی', accent: '#f2c96b', bg: '#16050c', glow: '#b8862b' },
  78: { key: 'wc', fa: 'جام جهانی باشگاه‌ها', tag: 'جام جهانی باشگاه‌ها', accent: '#f2c96b', bg: '#0b0712', glow: '#b8862b' },
  50: { key: 'euro', fa: 'یورو', tag: 'یورو', accent: '#56c8ff', bg: '#021029', glow: '#1a73ff' },
  44: { key: 'copa', fa: 'کوپا آمریکا', tag: 'کوپا آمریکا', accent: '#ffd400', bg: '#00122b', glow: '#0057b8' },
  525: { key: 'afc', fa: 'لیگ نخبگان آسیا', tag: 'شب آسیایی', accent: '#e3bb52', bg: '#0e0818', glow: '#7a3cff' },
  9469: { key: 'afc', fa: 'لیگ قهرمانان آسیا ۲', tag: 'شب آسیایی', accent: '#e3bb52', bg: '#0e0818', glow: '#7a3cff' },
  290: { key: 'afc', fa: 'جام ملت‌های آسیا', tag: 'جام ملت‌های آسیا', accent: '#e3bb52', bg: '#0e0818', glow: '#7a3cff' },
};
const lum = (hex) => { const m = /^#?([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i.exec(hex || ''); if (!m) return 0.5; const [r, g, b] = m.slice(1).map((x) => parseInt(x, 16) / 255); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };

let pending = null, touched = false;
// Views call this with their competition id; the router applies it when the new page is swapped in
// (force) and again if the view only learned its competition after the swap.
export const useComp = (id) => { pending = COMPS[id] ? +id : null; touched = true; };
export function applyPendingComp(force) {
  if (!force && !touched) return;
  const c = COMPS[pending], b = document.body;
  pending = null; touched = false;
  if (!c) { delete b.dataset.comp; ['--accent', '--accent-ink', '--accent-soft', '--accent-glow', '--bg', '--comp-glow'].forEach((k) => b.style.removeProperty(k)); return; }
  b.dataset.comp = c.key;
  b.style.setProperty('--accent', c.accent);
  b.style.setProperty('--accent-ink', lum(c.accent) > 0.5 ? '#05070a' : '#fff');
  b.style.setProperty('--accent-soft', `color-mix(in srgb, ${c.accent} 15%, transparent)`);
  b.style.setProperty('--accent-glow', `color-mix(in srgb, ${c.accent} 45%, transparent)`);
  b.style.setProperty('--comp-glow', c.glow);
  if (document.documentElement.dataset.theme !== 'light') b.style.setProperty('--bg', c.bg);
}
export const compBadge = (id) => (COMPS[id] ? `<div class="comp-badge">${starball()}<span>${COMPS[id].fa}</span></div>` : '');
export const starball = () => `<svg class="starball" viewBox="0 0 40 40" aria-hidden="true">${Array.from({ length: 8 }, (_, i) => {
  const a = (i / 8) * Math.PI * 2, x = 20 + Math.cos(a) * 12, y = 20 + Math.sin(a) * 12;
  return `<path transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${(i * 45).toFixed(0)}) scale(.55)" d="M0-9l2.6 5.6 6.2.7-4.6 4.2 1.3 6.1L0 4.6l-5.5 3 1.3-6.1-4.6-4.2 6.2-.7z"/>`;
}).join('')}</svg>`;

// Home banner on big competition nights (e.g. Champions League match day).
export function compNightBanner(leagues) {
  const hit = (leagues || []).map((l) => ({ l, c: COMPS[l.primaryId] })).filter((x) => x.c && x.l.matches?.length)
    .sort((a, b) => b.l.matches.length - a.l.matches.length)[0];
  if (!hit) return '';
  const live = hit.l.matches.filter((m) => m.status.started && !m.status.finished).length;
  return `<a class="comp-night comp-${hit.c.key}" href="#/league/${hit.l.primaryId}" style="--ca:${hit.c.accent};--cg:${hit.c.glow};--cb:${hit.c.bg}">
    <span class="cn-stars"></span>${starball()}<div class="grow"><b>${hit.c.tag}</b><small>${hit.l.matches.length.toLocaleString('fa-IR')} بازی امروز${live ? ` · ${live.toLocaleString('fa-IR')} بازی زنده` : ''}</small></div>${icon('chev', 20)}</a>`;
}

/* ---------- championship celebration ---------- */
// Snapshot the favourite team's trophy counts; when one goes up, celebrate for five days.
export async function checkChampion() {
  const t = store.myTeam;
  if (!t) return;
  const today = new Date().toDateString();
  if (store.trophyChecked === today && store.trophySnapFor === t.id) return;
  try {
    const d = await api('teams', { id: t.id, ccode3: 'IRN' }, { maxAge: 36e5 });
    const now = Object.fromEntries((d.history?.trophyList || []).map((x) => [x.name, +x.won || 0]));
    const prev = store.trophySnapFor === t.id ? store.trophySnap : null;
    if (prev) {
      const won = Object.keys(now).find((k) => now[k] > (prev[k] || 0));
      if (won) store.champion = { team: t.id, name: t.name, title: won, count: now[won], until: Date.now() + 5 * 864e5 };
    }
    store.trophySnap = now; store.trophySnapFor = t.id; store.trophyChecked = today; save();
  } catch {}
}
export const championActive = () => store.champion && store.champion.until > Date.now() && store.champion.team === store.myTeam?.id;

let rain;
export function applyChampion() {
  document.body.classList.toggle('champion', !!championActive());
  if (championActive()) startGoldRain(); else stopGoldRain();
}
export function championBanner() {
  if (!championActive()) return '';
  const ch = store.champion, c = club(ch.team);
  return `<div class="champ-banner rise" style="--c1:${c?.c[0] || 'var(--accent)'}">
    <span class="cb-rays"></span>
    <div class="cb-trophy">🏆</div>
    <img src="${teamImgL(ch.team)}" alt="" onerror="this.style.visibility='hidden'">
    <b>${esc(c?.fa || ch.name)} قهرمان شد!</b>
    <small>${esc(ch.title)}${ch.count ? ` · ${(+ch.count).toLocaleString('fa-IR')}مین قهرمانی` : ''}</small>
    <button class="cb-off" data-champ-off>${icon('x', 16)}</button></div>`;
}
document.addEventListener('click', (e) => {
  if (!e.target.closest('[data-champ-off]')) return;
  store.champion = null; save(); applyChampion();
  e.target.closest('.champ-banner')?.remove();
});

function startGoldRain() {
  if (rain || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const cv = Object.assign(document.createElement('canvas'), { className: 'gold-rain' });
  document.body.append(cv);
  const ctx = cv.getContext('2d'), dpr = Math.min(devicePixelRatio || 1, 2);
  const size = () => { cv.width = innerWidth * dpr; cv.height = innerHeight * dpr; };
  size(); addEventListener('resize', size);
  const cols = ['#ffd75e', '#ffc53d', '#fff1b8', '#e8a317'];
  const ps = Array.from({ length: 46 }, () => ({ x: Math.random(), y: Math.random(), s: 2 + Math.random() * 4, v: 0.0007 + Math.random() * 0.0012, r: Math.random() * 6, w: Math.random() * 0.04, c: cols[(Math.random() * 4) | 0] }));
  let last = performance.now();
  rain = { cv, size, raf: 0 };
  (function frame(t) {
    const dt = Math.min(3, (t - last) / 16.7); last = t;
    if (!document.hidden) {
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, innerWidth, innerHeight);
      for (const p of ps) {
        p.y += p.v * dt; p.r += p.w * dt; if (p.y > 1.05) { p.y = -0.05; p.x = Math.random(); }
        ctx.save(); ctx.translate(p.x * innerWidth + Math.sin(p.r) * 12, p.y * innerHeight); ctx.rotate(p.r); ctx.globalAlpha = 0.75;
        ctx.fillStyle = p.c; ctx.fillRect(-p.s, -p.s / 3, p.s * 2, p.s / 1.5); ctx.restore();
      }
    }
    rain.raf = requestAnimationFrame(frame);
  })(last);
}
function stopGoldRain() {
  if (!rain) return;
  cancelAnimationFrame(rain.raf); removeEventListener('resize', rain.size); rain.cv.remove(); rain = null;
}
