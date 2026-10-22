'use strict';
// Deterministic canvas motion graphics: frame(t) depends only on t, so frames can be
// rendered out of order / in parallel and still match the audio exactly.
const W = 1080, H = 1920;
const { BEAT, DURATION, SCENES, CLOCK, MIDNIGHT, FX } = TL;
const FA = 'Vaz';  // Vazirmatn (Farsi digits)
const MONO = 'JBM'; // JetBrains Mono
const C = {
  bg0: '#05070f', bg1: '#0b0f1a', violet: '#8b5cf6', cyan: '#22d3ee', pink: '#f472b6',
  green: '#34d399', amber: '#fbbf24', red: '#fb7185', white: '#ffffff',
  dim: 'rgba(255,255,255,0.55)', faint: 'rgba(255,255,255,0.12)', card: 'rgba(20,26,52,0.78)',
};
// Optional page handle shown on the last frame, e.g. '@my.ai.page'
const HANDLE = '';

// ---------------------------------------------------------------- canvases
const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const ctx = canvas.getContext('2d');
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const buf = mk(W, H), bx = buf.getContext('2d');

// ---------------------------------------------------------------- math
const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
const lerp = (a, b, t) => a + (b - a) * t;
const P = (t, a, d) => clamp((t - a) / d);
const E = {
  outCubic: (x) => 1 - Math.pow(1 - x, 3),
  inCubic: (x) => x * x * x,
  inOut: (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2),
  outExpo: (x) => (x >= 1 ? 1 : 1 - Math.pow(2, -10 * x)),
  inExpo: (x) => (x <= 0 ? 0 : Math.pow(2, 10 * x - 10)),
  outBack: (x) => { const c1 = 1.9, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); },
  outElastic: (x) => (x <= 0 ? 0 : x >= 1 ? 1 : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * Math.PI) / 3) + 1),
};
const rnd = (i, j = 0) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
  h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const TAU = Math.PI * 2;
const beatPulse = (t, k = 7) => Math.exp(-((t % BEAT + BEAT) % BEAT) * k);
// pop-in scale with overshoot, 0 before `a`
const pop = (t, a, d = 0.35) => (t < a ? 0 : E.outBack(P(t, a, d)));

// ---------------------------------------------------------------- drawing helpers
function rr(c, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  c.beginPath();
  c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r);
  c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
}
function grad(c, x0, y0, x1, y1, a = C.violet, b = C.cyan) {
  const g = c.createLinearGradient(x0, y0, x1, y1);
  g.addColorStop(0, a); g.addColorStop(1, b);
  return g;
}
// glass card centred on (cx, cy)
function card(c, cx, cy, w, h, { r = 34, glow = 0.5, fill = C.card } = {}) {
  const x = cx - w / 2, y = cy - h / 2;
  c.save();
  c.shadowColor = `rgba(139,92,246,${0.55 * glow})`; c.shadowBlur = 60 * glow;
  rr(c, x, y, w, h, r); c.fillStyle = fill; c.fill();
  c.restore();
  c.save();
  rr(c, x, y, w, h, r); c.lineWidth = 2.5; c.strokeStyle = grad(c, x, y, x + w, y + h, 'rgba(139,92,246,0.9)', 'rgba(34,211,238,0.9)'); c.stroke();
  c.restore();
}
function font(c, size, weight = 900, fam = FA) { c.font = `${weight} ${size}px ${fam}`; }
// text with auto-fit, optional gradient fill and glow; RTL by default
function txt(c, s, x, y, o = {}) {
  const { size = 60, weight = 900, fam = FA, color = C.white, align = 'center', rtl = true, max = 900, glow = 0, gradFill = false, alpha = 1 } = o;
  c.save();
  c.globalAlpha *= alpha;
  c.direction = rtl ? 'rtl' : 'ltr';
  font(c, size, weight, fam);
  let w = c.measureText(s).width;
  if (w > max) { font(c, size * max / w, weight, fam); w = max; }
  c.textAlign = align; c.textBaseline = 'middle';
  let x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  if (rtl && align === 'start') x0 = x - w;
  c.fillStyle = gradFill ? grad(c, x0, y, x0 + w, y, C.cyan, C.violet) : color;
  if (glow) { c.shadowColor = gradFill ? 'rgba(139,92,246,0.9)' : 'rgba(180,160,255,0.8)'; c.shadowBlur = glow; }
  c.fillText(s, x, y);
  if (glow) { c.shadowBlur = 0; c.fillText(s, x, y); }
  c.restore();
  return w;
}
function measure(c, s, size, weight = 900, fam = FA) {
  c.save(); c.direction = 'rtl'; font(c, size, weight, fam); const w = c.measureText(s).width; c.restore(); return w;
}
// 4-point AI sparkle
function sparkle(c, x, y, r, rot = 0, fill = '#fff') {
  c.save(); c.translate(x, y); c.rotate(rot);
  c.beginPath();
  for (let i = 0; i < 4; i++) {
    const a = i * TAU / 4;
    c.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    c.quadraticCurveTo(0, 0, Math.cos(a + TAU / 8) * r * 0.001, Math.sin(a + TAU / 8) * r * 0.001);
  }
  c.closePath(); c.fillStyle = fill; c.fill();
  c.restore();
}
function check(c, x, y, r, p) {
  c.save();
  c.beginPath(); c.arc(x, y, r * E.outBack(clamp(p * 2)), 0, TAU); c.fillStyle = C.green; c.fill();
  const q = clamp(p * 2 - 0.6);
  if (q > 0) {
    c.beginPath();
    const pts = [[-0.45, 0.02], [-0.12, 0.34], [0.48, -0.3]];
    c.moveTo(x + pts[0][0] * r, y + pts[0][1] * r);
    if (q < 0.4) c.lineTo(x + lerp(pts[0][0], pts[1][0], q / 0.4) * r, y + lerp(pts[0][1], pts[1][1], q / 0.4) * r);
    else {
      c.lineTo(x + pts[1][0] * r, y + pts[1][1] * r);
      const k = (q - 0.4) / 0.6;
      c.lineTo(x + lerp(pts[1][0], pts[2][0], k) * r, y + lerp(pts[1][1], pts[2][1], k) * r);
    }
    c.lineWidth = r * 0.24; c.lineCap = 'round'; c.lineJoin = 'round'; c.strokeStyle = '#06281d'; c.stroke();
  }
  c.restore();
}
// prompt pill with RTL typewriter reveal and caret
function prompt(c, cx, cy, w, s, p, t, alpha = 1) {
  if (alpha <= 0) return;
  c.save(); c.globalAlpha *= alpha;
  const h = 96;
  rr(c, cx - w / 2, cy - h / 2, w, h, h / 2); c.fillStyle = 'rgba(255,255,255,0.07)'; c.fill();
  c.lineWidth = 2; c.strokeStyle = 'rgba(255,255,255,0.22)'; c.stroke();
  const right = cx + w / 2 - 34;
  sparkle(c, right - 18, cy, 22, t * 1.5, grad(c, right - 40, cy, right, cy));
  const tx = right - 58;
  const tw = measure(c, s, 38, 700);
  const shown = tw * clamp(p);
  c.save();
  c.beginPath(); c.rect(tx - shown, cy - 50, shown + 4, 100); c.clip();
  txt(c, s, tx, cy + 2, { size: 38, weight: 700, align: 'right', color: '#e8e6ff', max: w - 130 });
  c.restore();
  if (p < 1.15 && Math.floor(t * 4) % 2 === 0) { c.fillStyle = C.cyan; c.fillRect(tx - shown - 8, cy - 24, 4, 48); }
  // send button
  const bxp = cx - w / 2 + 52;
  c.beginPath(); c.arc(bxp, cy, 32, 0, TAU); c.fillStyle = grad(c, bxp - 32, cy, bxp + 32, cy); c.fill();
  c.beginPath(); c.moveTo(bxp + 12, cy); c.lineTo(bxp - 6, cy - 12); c.lineTo(bxp - 6, cy + 12); c.closePath();
  c.fillStyle = '#0b0f1a'; c.fill();
  c.restore();
}

// ---------------------------------------------------------------- background
function background(c, t) {
  const g = c.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, C.bg0); g.addColorStop(0.55, C.bg1); g.addColorStop(1, '#120a26');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  const bp = beatPulse(t, 5);
  const blob = (x, y, r, col, a) => {
    const rg = c.createRadialGradient(x, y, 0, x, y, r);
    rg.addColorStop(0, col.replace('A', a)); rg.addColorStop(1, col.replace('A', 0));
    c.fillStyle = rg; c.fillRect(x - r, y - r, r * 2, r * 2);
  };
  blob(250 + Math.sin(t * 0.4) * 120, 520 + Math.cos(t * 0.3) * 80, 700, 'rgba(139,92,246,A)', 0.28 + bp * 0.08);
  blob(850 + Math.cos(t * 0.35) * 120, 1250 + Math.sin(t * 0.45) * 100, 720, 'rgba(34,211,238,A)', 0.18 + bp * 0.06);
  // synthwave floor grid
  c.save();
  const hy = 1460;
  c.beginPath(); c.rect(0, hy, W, H - hy); c.clip();
  c.strokeStyle = 'rgba(139,92,246,0.22)'; c.lineWidth = 2;
  for (let i = -12; i <= 12; i++) {
    c.beginPath(); c.moveTo(W / 2 + i * 30, hy); c.lineTo(W / 2 + i * 260, H); c.stroke();
  }
  const sp = (t * 0.9) % 1;
  for (let k = 0; k < 9; k++) {
    const z = (k + sp) / 9, y = hy + Math.pow(z, 2.2) * (H - hy);
    c.globalAlpha = z; c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke();
  }
  c.restore();
  const hz = c.createLinearGradient(0, hy - 6, 0, hy + 6);
  hz.addColorStop(0, 'rgba(34,211,238,0)'); hz.addColorStop(0.5, 'rgba(34,211,238,0.35)'); hz.addColorStop(1, 'rgba(34,211,238,0)');
  c.fillStyle = hz; c.fillRect(0, hy - 6, W, 12);
  // floating particles
  for (let i = 0; i < 70; i++) {
    const x = rnd(i, 1) * W + Math.sin(t * 0.5 + i) * 20;
    const y = ((rnd(i, 2) * H - t * (20 + rnd(i, 3) * 50)) % H + H) % H;
    const a = 0.15 + 0.35 * rnd(i, 4);
    c.fillStyle = i % 3 ? `rgba(180,170,255,${a})` : `rgba(34,211,238,${a})`;
    c.fillRect(x, y, 2 + rnd(i, 5) * 2.5, 2 + rnd(i, 5) * 2.5);
  }
  // vignette
  const vg = c.createRadialGradient(W / 2, H / 2, 400, W / 2, H / 2, 1250);
  vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, 'rgba(0,0,0,0.6)');
  c.fillStyle = vg; c.fillRect(0, 0, W, H);
}

// ---------------------------------------------------------------- clock + 24h bar
const pad2 = (n) => String(n).padStart(2, '0');
const clockStr = (min) => pad2(Math.floor(min / 60) % 24) + ':' + pad2(min % 60);
function clockIndex(t) { let k = 0; for (let i = 0; i < CLOCK.length; i++) if (t >= CLOCK[i].t) k = i; return k; }
function drawClock(c, t) {
  const cy = 395, size = 196;
  const k = clockIndex(t);
  const cur = clockStr(CLOCK[k].min), prev = k > 0 ? clockStr(CLOCK[k - 1].min) : cur;
  const since = t - CLOCK[k].t;
  font(c, size, 800, MONO);
  const cw = c.measureText('0').width;
  const total = cw * 5;
  const x0 = W / 2 - total / 2;
  const dimHook = t < 3 ? 0.55 + 0.45 * P(t, 0, 0.4) : 1;
  const mid = t >= MIDNIGHT ? Math.exp(-(t - MIDNIGHT) * 2.5) : 0;
  c.save();
  c.globalAlpha = dimHook;
  // glow plate
  const gl = c.createRadialGradient(W / 2, cy, 0, W / 2, cy, 520);
  gl.addColorStop(0, `rgba(139,92,246,${0.22 + 0.25 * mid})`); gl.addColorStop(1, 'rgba(139,92,246,0)');
  c.fillStyle = gl; c.fillRect(0, cy - 300, W, 600);
  c.textAlign = 'center'; c.textBaseline = 'middle'; c.direction = 'ltr';
  const fill = mid > 0.05 ? `rgba(255,255,255,${0.5 + mid})` : null;
  for (let i = 0; i < 5; i++) {
    const x = x0 + cw * (i + 0.5);
    const g = fill || grad(c, x0, cy - 90, x0 + total, cy + 90, '#c4b5fd', C.cyan);
    c.fillStyle = g;
    c.shadowColor = mid > 0.05 ? 'rgba(255,255,255,0.9)' : 'rgba(139,92,246,0.95)';
    c.shadowBlur = 40;
    if (i === 2) { // colon blinks on the beat
      c.globalAlpha = dimHook * (0.35 + 0.65 * beatPulse(t, 3));
      c.fillText(':', x, cy - 10);
      c.globalAlpha = dimHook;
      continue;
    }
    const a = prev[i], b = cur[i];
    const p = a === b ? 1 : E.outBack(P(since, (i > 2 ? i - 1 : i) * 0.05, 0.38));
    c.save();
    c.beginPath(); c.rect(x - cw / 2 - 4, cy - size * 0.62, cw + 8, size * 1.24); c.clip();
    if (p < 1) { c.globalAlpha = dimHook * (1 - p); c.fillText(a, x, cy - p * size); c.globalAlpha = dimHook; }
    c.fillText(b, x, cy + (1 - p) * size);
    c.restore();
  }
  c.restore();

  // 24h progress bar (fills right-to-left)
  const by = 545, bw = 780, bx0 = W / 2 - bw / 2;
  const kNow = CLOCK[k].min, kPrev = k > 0 ? CLOCK[k - 1].min : kNow;
  const minutes = lerp(kPrev, kNow, E.inOut(P(since, 0, 0.6)));
  const frac = clamp(minutes / 1440);
  c.save(); c.globalAlpha = dimHook;
  rr(c, bx0, by - 5, bw, 10, 5); c.fillStyle = 'rgba(255,255,255,0.1)'; c.fill();
  for (let h = 0; h <= 24; h++) {
    const x = bx0 + bw - bw * h / 24, big = h % 6 === 0;
    c.fillStyle = h / 24 <= frac + 1e-6 ? 'rgba(34,211,238,0.85)' : 'rgba(255,255,255,0.25)';
    c.fillRect(x - 1.5, by + 14, 3, big ? 18 : 9);
  }
  const fw = bw * frac;
  rr(c, bx0 + bw - fw, by - 5, fw, 10, 5); c.fillStyle = grad(c, bx0 + bw, by, bx0 + bw - fw, by, C.violet, C.cyan); c.fill();
  const dx = bx0 + bw - fw;
  c.shadowColor = C.cyan; c.shadowBlur = 30;
  c.beginPath(); c.arc(dx, by, 11 + 4 * beatPulse(t, 6), 0, TAU); c.fillStyle = '#fff'; c.fill();
  c.restore();
  font(c, 26, 700, MONO);
  c.save(); c.globalAlpha = 0.45 * dimHook; c.fillStyle = '#fff'; c.textAlign = 'center'; c.textBaseline = 'top';
  [0, 6, 12, 18, 24].forEach((h) => c.fillText(pad2(h % 24 === 0 && h ? 24 : h), bx0 + bw - bw * h / 24, by + 40));
  c.restore();
}
function header(c, t) {
  const a = P(t, 0.05, 0.4);
  txt(c, '۲۴ ساعت با هوش مصنوعی', W / 2, 238, { size: 40, weight: 700, color: 'rgba(255,255,255,0.75)', alpha: a });
}

// ---------------------------------------------------------------- caption + tool chip
const CAP_Y = 1300, CHIP_Y = 1420;
function caption(c, sc, t) {
  if (!sc.cap) return;
  const u = t - sc.t0;
  const pin = E.outExpo(P(u, 0.08, 0.45));
  const out = P(t, sc.t1 - 0.12, 0.12);
  const a = pin * (1 - out);
  if (a <= 0) return;
  c.save();
  c.translate(0, (1 - pin) * 50);
  txt(c, sc.cap, W / 2, CAP_Y, { size: 82, weight: 900, color: '#fff', glow: 28, alpha: a, max: 880 });
  c.restore();
  // chip
  const cp = E.outBack(P(u, 0.35, 0.4)) * (1 - out);
  if (cp <= 0) return;
  font(c, 36, 700, MONO);
  const tw = c.measureText(sc.chip).width;
  const w = tw + 120, h = 74;
  c.save();
  c.translate(W / 2, CHIP_Y); c.scale(cp, cp);
  rr(c, -w / 2, -h / 2, w, h, h / 2); c.fillStyle = 'rgba(139,92,246,0.18)'; c.fill();
  c.lineWidth = 2.5; c.strokeStyle = grad(c, -w / 2, 0, w / 2, 0); c.stroke();
  sparkle(c, -w / 2 + 44, 0, 20, t * 2, C.cyan);
  c.fillStyle = '#fff'; c.textAlign = 'left'; c.textBaseline = 'middle'; c.direction = 'ltr';
  c.fillText(sc.chip, -w / 2 + 78, 2);
  c.restore();
}

// ---------------------------------------------------------------- scenes
const MID_Y = 930; // centre of the scene area

function sceneHook(c, t) {
  // neural ring behind the text
  const rp = E.outCubic(P(t, 0, 0.8));
  c.save(); c.translate(W / 2, MID_Y);
  for (let k = 0; k < 3; k++) {
    c.beginPath(); c.arc(0, 0, (260 + k * 70) * rp, 0, TAU);
    c.strokeStyle = `rgba(139,92,246,${0.25 - k * 0.07})`; c.lineWidth = 2; c.setLineDash([6, 14]);
    c.lineDashOffset = t * (k % 2 ? 40 : -40); c.stroke();
  }
  c.setLineDash([]);
  c.restore();
  const s1 = P(t, 0.15, 0.3), s2 = P(t, 0.65, 0.3), s3 = P(t, 1.5, 0.4);
  if (s1 > 0) {
    const k = lerp(1.6, 1, E.outExpo(s1));
    c.save(); c.translate(W / 2, MID_Y - 110); c.scale(k, k);
    txt(c, 'اگه یه روز کامل', 0, 0, { size: 104, weight: 900, glow: 20, alpha: clamp(s1 * 3) });
    c.restore();
  }
  if (s2 > 0) {
    const k = lerp(1.6, 1, E.outExpo(s2));
    c.save(); c.translate(W / 2, MID_Y + 40); c.scale(k, k);
    txt(c, 'فقط با AI کار کنی…', 0, 0, { size: 112, weight: 900, gradFill: true, glow: 40, alpha: clamp(s2 * 3) });
    c.restore();
  }
  if (s3 > 0) {
    txt(c, 'ببین چی می‌شه', W / 2, MID_Y + 200 + (1 - E.outCubic(s3)) * 30, { size: 54, weight: 700, color: C.dim, alpha: s3 });
    // little bouncing chevron
    const y = MID_Y + 290 + Math.abs(Math.sin(t * 6)) * 14;
    c.save(); c.globalAlpha = s3; c.strokeStyle = C.cyan; c.lineWidth = 6; c.lineCap = 'round'; c.lineJoin = 'round';
    c.beginPath(); c.moveTo(W / 2 - 22, y - 10); c.lineTo(W / 2, y + 10); c.lineTo(W / 2 + 22, y - 10); c.stroke();
    c.restore();
  }
}

function envelope(c, x, y, w, rot, s, badge) {
  const h = w * 0.66;
  c.save(); c.translate(x, y); c.rotate(rot); c.scale(s, s);
  rr(c, -w / 2, -h / 2, w, h, 12); c.fillStyle = '#1c2350'; c.fill();
  c.lineWidth = 3; c.strokeStyle = 'rgba(167,139,250,0.9)'; c.stroke();
  c.beginPath(); c.moveTo(-w / 2 + 8, -h / 2 + 8); c.lineTo(0, h * 0.08); c.lineTo(w / 2 - 8, -h / 2 + 8);
  c.strokeStyle = 'rgba(34,211,238,0.8)'; c.stroke();
  if (badge) { c.beginPath(); c.arc(w / 2 - 6, -h / 2 + 6, 13, 0, TAU); c.fillStyle = C.red; c.fill(); }
  c.restore();
}
function sceneMail(c, t, u) {
  const N = 16;
  const suck = P(u, 1.45, 0.5);
  const center = { x: W / 2, y: MID_Y };
  for (let i = 0; i < N; i++) {
    const row = Math.floor(i / 4), col = i % 4;
    const tx = W / 2 + (col - 1.5) * 175 + (rnd(i, 1) - 0.5) * 40 + (row % 2) * 30;
    const ty = 1150 - row * 100 + (rnd(i, 2) - 0.5) * 20;
    const land = P(u, 0.25 + i * 0.07 - 0.32, 0.32);
    if (land <= 0) continue;
    let x = tx, y = lerp(640, ty, E.inCubic(land) * 0.6 + E.outBack(land) * 0.4);
    const fa = clamp(land * 4);
    let rot = (rnd(i, 3) - 0.5) * 0.5, s = 1;
    if (suck > 0) {
      const k = E.inExpo(clamp(suck * 1.15 - rnd(i, 4) * 0.15));
      x = lerp(x, center.x, k); y = lerp(y, center.y, k); rot += k * 6; s = 1 - k;
    }
    if (s > 0.02) { c.save(); c.globalAlpha = fa; envelope(c, x, y, 150, rot, s, i % 3 === 0); c.restore(); }
  }
  // unread counter
  const cnt = Math.round(50 * E.outCubic(P(u, 0.2, 1.2)));
  const ca = 1 - P(u, 1.5, 0.25);
  if (ca > 0) {
    c.save(); c.globalAlpha = ca;
    rr(c, W / 2 - 190, 640, 380, 84, 42); c.fillStyle = 'rgba(251,113,133,0.16)'; c.fill();
    c.lineWidth = 2; c.strokeStyle = 'rgba(251,113,133,0.7)'; c.stroke();
    txt(c, `${cnt} ایمیل خونده‌نشده`, W / 2, 684, { size: 36, weight: 800, color: '#fecdd3' });
    c.restore();
  }
  // AI core absorbing the pile
  const core = P(u, 1.4, 0.3) * (1 - P(u, 2.0, 0.25));
  if (core > 0) {
    c.save(); c.globalAlpha = core;
    const r = 70 + 40 * Math.sin(u * 20);
    const rg = c.createRadialGradient(center.x, center.y, 0, center.x, center.y, r * 2.5);
    rg.addColorStop(0, 'rgba(255,255,255,0.9)'); rg.addColorStop(0.3, 'rgba(139,92,246,0.6)'); rg.addColorStop(1, 'rgba(34,211,238,0)');
    c.fillStyle = rg; c.fillRect(center.x - r * 2.5, center.y - r * 2.5, r * 5, r * 5);
    sparkle(c, center.x, center.y, 70 * core, u * 6, '#fff');
    c.restore();
  }
  // summary card
  const sp = pop(u, 2.0, 0.4);
  if (sp > 0) {
    c.save(); c.translate(W / 2, MID_Y); c.scale(sp, sp);
    card(c, 0, 0, 780, 420);
    const hx = 330;
    sparkle(c, hx, -150, 22, u * 2, grad(c, hx - 20, 0, hx + 20, 0));
    txt(c, 'خلاصه‌ی امروز', hx - 40, -148, { size: 44, weight: 900, align: 'right' });
    txt(c, '۳ مورد مهم', -320, -148, { size: 32, weight: 700, align: 'left', color: C.dim, rtl: true });
    c.fillStyle = 'rgba(255,255,255,0.1)'; c.fillRect(-350, -100, 700, 2);
    const lines = ['جلسه‌ی فردا ساعت ۱۰ صبح', 'فاکتور مشتری تأیید شد', 'تیم منتظر جواب توئه'];
    lines.forEach((s, i) => {
      const a = u - (2.3 + i * 0.25);
      if (a <= 0) return;
      const y = -30 + i * 100;
      check(c, hx - 6, y, 28, clamp(a / 0.4));
      const tw = measure(c, s, 42, 800);
      const rv = E.outCubic(clamp(a / 0.45)) * tw;
      c.save(); c.beginPath(); c.rect(hx - 56 - rv, y - 40, rv + 4, 80); c.clip();
      txt(c, s, hx - 56, y + 2, { size: 42, weight: 800, align: 'right', color: '#eef' });
      c.restore();
    });
    c.restore();
  }
}

function sceneSlides(c, t, u) {
  const pa = 1 - P(u, 2.3, 0.3);
  prompt(c, W / 2, 680, 840, 'یه ارائه درباره‌ی فروش فصل بساز', P(u, 0.05, 0.75), t, pa * E.outCubic(P(u, 0, 0.2)));
  // deck fan-out behind the main slide
  const fan = E.outBack(P(u, 2.4, 0.45));
  const sw = 760, sh = 428, sy = MID_Y + 70 - E.inOut(P(u, 2.3, 0.4)) * 60;
  const sp = pop(u, 1.0, 0.4);
  if (sp <= 0) return;
  for (let k = 3; k >= 1; k--) {
    if (fan <= 0) break;
    c.save(); c.translate(W / 2 + k * 30 * fan, sy + k * 22 * fan); c.rotate(-k * 0.03 * fan);
    c.globalAlpha = 0.9 - k * 0.2;
    card(c, 0, 0, sw, sh, { r: 22, glow: 0.2, fill: '#141a38' });
    c.restore();
  }
  c.save(); c.translate(W / 2, sy); c.scale(sp, sp);
  card(c, 0, 0, sw, sh, { r: 22, fill: '#121733' });
  const x0 = -sw / 2 + 40, y0 = -sh / 2 + 40;
  // title bar
  const p1 = E.outCubic(P(u, 1.15, 0.3));
  rr(c, -x0 - 420 * p1, y0, 420 * p1, 44, 12); c.fillStyle = grad(c, -x0, 0, -x0 - 420, 0, C.violet, C.cyan); c.fill();
  const p2 = E.outCubic(P(u, 1.3, 0.3));
  rr(c, -x0 - 260 * p2, y0 + 64, 260 * p2, 20, 10); c.fillStyle = 'rgba(255,255,255,0.3)'; c.fill();
  // bar chart (left side)
  const vals = [0.45, 0.62, 0.55, 0.88];
  vals.forEach((v, i) => {
    const g = E.outBack(P(u, 1.45 + i * 0.08, 0.4));
    const bh = 210 * v * g, bxp = x0 + i * 82;
    rr(c, bxp, sh / 2 - 50 - bh, 56, bh, 10);
    c.fillStyle = i === 3 ? C.cyan : 'rgba(167,139,250,0.75)'; c.fill();
  });
  // image block (right side)
  const p3 = pop(u, 1.6, 0.35);
  if (p3 > 0) {
    c.save(); c.translate(-x0 - 130, 40); c.scale(p3, p3);
    rr(c, -130, -90, 260, 180, 16); c.fillStyle = grad(c, -130, -90, 130, 90, '#4c1d95', '#0e7490'); c.fill();
    c.beginPath(); c.moveTo(-110, 70); c.lineTo(-40, -10); c.lineTo(10, 40); c.lineTo(50, 0); c.lineTo(110, 70); c.closePath();
    c.fillStyle = 'rgba(255,255,255,0.35)'; c.fill();
    c.beginPath(); c.arc(70, -45, 18, 0, TAU); c.fillStyle = C.amber; c.fill();
    c.restore();
  }
  // bullets
  for (let i = 0; i < 3; i++) {
    const b = E.outCubic(P(u, 1.8 + i * 0.08, 0.3));
    if (b <= 0) continue;
    const y = sh / 2 - 135 + i * 34;
    c.beginPath(); c.arc(-x0 - 6, y, 6, 0, TAU); c.fillStyle = C.cyan; c.fill();
    rr(c, -x0 - 24 - 220 * b, y - 7, 220 * b, 14, 7); c.fillStyle = 'rgba(255,255,255,0.22)'; c.fill();
  }
  c.restore();
  // "deck ready" badge
  const bp = pop(u, 2.55, 0.35);
  if (bp > 0) {
    c.save(); c.translate(W / 2, sy - sh / 2 - 62); c.scale(bp, bp);
    rr(c, -190, -38, 380, 76, 38); c.fillStyle = 'rgba(52,211,153,0.15)'; c.fill();
    c.lineWidth = 2; c.strokeStyle = 'rgba(52,211,153,0.8)'; c.stroke();
    check(c, 140, 0, 22, 1);
    txt(c, '۱۲ اسلاید آماده شد', 105, 2, { size: 34, weight: 800, align: 'right', color: '#d1fae5' });
    c.restore();
  }
}

// --- image scene: sketch → AI render
function mountains(c, s, filled) {
  // coordinates in a 600x600 frame centred on 0
  const far = [[-300, 120], [-200, -10], [-120, 60], [-20, -90], [80, 30], [170, -40], [300, 90]];
  const near = [[-300, 200], [-180, 70], [-60, 170], [60, 40], [190, 160], [300, 110]];
  const path = (pts, base) => { c.beginPath(); c.moveTo(pts[0][0], pts[0][1]); pts.slice(1).forEach((p) => c.lineTo(p[0], p[1])); if (base) { c.lineTo(300, 300); c.lineTo(-300, 300); c.closePath(); } };
  if (filled) {
    path(far, true); c.fillStyle = grad(c, 0, -90, 0, 300, '#6d28d9', '#1e1b4b'); c.fill();
    path(near, true); c.fillStyle = grad(c, 0, 40, 0, 300, '#0e7490', '#0b1023'); c.fill();
    return;
  }
  return { far, near, path };
}
function sceneImage(c, t, u) {
  prompt(c, W / 2, 640, 720, '«غروب کوهستان، سبک نئون»', P(u, 0.0, 0.5), t, E.outCubic(P(u, 0, 0.2)));
  const fp = pop(u, 0.0, 0.35);
  if (fp <= 0) return;
  const S = 480;
  const zoom = 1 + 0.06 * E.inOut(P(u, 2.2, 1.3));
  c.save(); c.translate(W / 2, MID_Y + 30); c.scale(fp, fp);
  card(c, 0, 0, S + 24, S + 24, { r: 30 });
  c.save();
  rr(c, -S / 2, -S / 2, S, S, 22); c.clip();
  c.scale(S / 600, S / 600);
  // sketch paper
  c.fillStyle = '#0d1126'; c.fillRect(-300, -300, 600, 600);
  const sk = P(u, 0.05, 1.15);
  const { far, near, path } = mountains(c, 1, false);
  const draw = (fn, len, p) => { if (p <= 0) return; c.setLineDash([len * p, len]); fn(); c.stroke(); c.setLineDash([]); };
  c.strokeStyle = 'rgba(255,255,255,0.85)'; c.lineWidth = 4; c.lineCap = 'round'; c.lineJoin = 'round';
  draw(() => { c.beginPath(); c.arc(110, -150, 60, Math.PI, Math.PI + TAU); }, 380, clamp(sk * 3));
  draw(() => path(far, false), 900, clamp(sk * 2 - 0.4));
  draw(() => path(near, false), 800, clamp(sk * 2 - 0.8));
  [[-170, -170], [-110, -200], [-60, -150]].forEach(([x, y], i) => draw(() => { c.beginPath(); c.moveTo(x - 18, y - 6); c.quadraticCurveTo(x - 8, y - 14, x, y); c.quadraticCurveTo(x + 8, y - 14, x + 18, y - 6); }, 60, clamp(sk * 4 - 3 + i * 0.2)));
  // AI render revealed by a scan line with a noisy "diffusion" band
  const sc = E.inOut(P(u, 1.3, 0.9));
  if (sc > 0) {
    const yLine = -300 + sc * 680;
    c.save();
    c.beginPath(); c.rect(-300, -300, 600, yLine + 300); c.clip();
    c.scale(zoom, zoom);
    const sky = c.createLinearGradient(0, -300, 0, 120);
    sky.addColorStop(0, '#1e1b4b'); sky.addColorStop(0.55, '#7c3aed'); sky.addColorStop(1, '#f472b6');
    c.fillStyle = sky; c.fillRect(-310, -310, 620, 620);
    for (let i = 0; i < 40; i++) { c.fillStyle = `rgba(255,255,255,${0.3 + rnd(i, 7) * 0.6})`; c.fillRect(-300 + rnd(i, 8) * 600, -300 + rnd(i, 9) * 220, 3, 3); }
    const sun = c.createRadialGradient(110, -150, 10, 110, -150, 190);
    sun.addColorStop(0, '#fff7ed'); sun.addColorStop(0.3, '#fbbf24'); sun.addColorStop(0.6, 'rgba(244,114,182,0.5)'); sun.addColorStop(1, 'rgba(244,114,182,0)');
    c.fillStyle = sun; c.fillRect(-200, -400, 600, 500);
    c.beginPath(); c.arc(110, -150, 60, 0, TAU); c.fillStyle = grad(c, 0, -210, 0, -90, '#fde68a', '#fb7185'); c.fill();
    for (let k = 0; k < 5; k++) { c.fillStyle = '#7c3aed'; c.fillRect(40, -140 + k * 14, 140, 4 + k); }
    mountains(c, 1, true);
    // neon rim on the ridges
    c.strokeStyle = C.cyan; c.lineWidth = 3; c.shadowColor = C.cyan; c.shadowBlur = 16;
    path(near, false); c.stroke(); c.shadowBlur = 0;
    // lake reflection
    const lake = c.createLinearGradient(0, 210, 0, 300);
    lake.addColorStop(0, 'rgba(244,114,182,0.55)'); lake.addColorStop(1, 'rgba(30,27,75,0.95)');
    c.fillStyle = lake; c.fillRect(-300, 220, 600, 80);
    for (let k = 0; k < 6; k++) { c.fillStyle = 'rgba(253,230,138,0.6)'; c.fillRect(70 - k * 6, 232 + k * 11, 80 + k * 12, 3); }
    c.restore();
    if (sc < 1) {
      for (let i = 0; i < 60; i++) for (let j = 0; j < 4; j++) {
        const px = -300 + i * 10, py = yLine + j * 10;
        c.fillStyle = `hsla(${250 + rnd(i * 7 + j, Math.floor(u * 30)) * 90},80%,65%,${0.7 - j * 0.15})`;
        c.fillRect(px, py, 10, 10);
      }
      c.fillStyle = '#fff'; c.shadowColor = C.cyan; c.shadowBlur = 30; c.fillRect(-300, yLine - 2, 600, 4); c.shadowBlur = 0;
    }
  }
  c.restore();
  c.restore();
  // sparkles after the render lands
  const sp = P(u, 2.25, 0.6);
  if (sp > 0 && sp < 1) {
    for (let i = 0; i < 6; i++) {
      const a = i / 6 * TAU + 0.4, r = 270 + sp * 60;
      c.save(); c.globalAlpha = 1 - sp;
      sparkle(c, W / 2 + Math.cos(a) * r, MID_Y + 30 + Math.sin(a) * r, 26 * (1 - sp * 0.5), sp * 3, i % 2 ? C.cyan : '#fff');
      c.restore();
    }
  }
}

function mic(c, x, y, s, t) {
  c.save(); c.translate(x, y); c.scale(s, s);
  for (let k = 0; k < 3; k++) {
    const ph = ((t * 1.2 + k / 3) % 1);
    c.beginPath(); c.arc(0, 0, 70 + ph * 90, 0, TAU); c.strokeStyle = `rgba(34,211,238,${0.5 * (1 - ph)})`; c.lineWidth = 3; c.stroke();
  }
  c.beginPath(); c.arc(0, 0, 70, 0, TAU); c.fillStyle = grad(c, -70, -70, 70, 70); c.fill();
  rr(c, -18, -40, 36, 58, 18); c.fillStyle = '#0b0f1a'; c.fill();
  c.beginPath(); c.arc(0, 0, 32, 0.15 * Math.PI, 0.85 * Math.PI); c.lineWidth = 7; c.strokeStyle = '#0b0f1a'; c.lineCap = 'round'; c.stroke();
  c.fillStyle = '#0b0f1a'; c.fillRect(-3.5, 30, 7, 16);
  c.restore();
}
function sceneVoice(c, t, u) {
  const vin = E.outBack(P(u, 0, 0.35));
  const up = E.inOut(P(u, 1.8, 0.5));
  mic(c, W / 2, lerp(720, 700, up), vin * lerp(1, 0.6, up), t);
  // waveform → collapses into subtitle
  const wf = 1 - P(u, 1.2, 0.35);
  const NB = 36, bw = 14, gap = 8, x0 = W / 2 - (NB * (bw + gap)) / 2;
  if (wf > 0) {
    for (let i = 0; i < NB; i++) {
      const env = Math.sin(Math.PI * (i + 0.5) / NB);
      const amp = (0.25 + 0.75 * Math.abs(Math.sin(u * 9 + i * 0.7) * Math.sin(u * 5.3 + i * 1.3))) * env;
      const h = Math.max(10, 190 * amp * vin * wf);
      const x = lerp(x0 + i * (bw + gap), W / 2, 1 - wf);
      rr(c, x, 940 - h / 2, bw, h, bw / 2);
      c.fillStyle = grad(c, 0, 940 - h / 2, 0, 940 + h / 2, C.cyan, C.violet); c.fill();
    }
  }
  // subtitle pill typed out
  const sub = 'سلام! امروز یه ترفند باحال دارم…';
  const sa = P(u, 1.3, 0.2) * (1 - P(u, 1.85, 0.2));
  if (sa > 0) {
    c.save(); c.globalAlpha = sa;
    rr(c, W / 2 - 420, 890, 840, 100, 50); c.fillStyle = 'rgba(255,255,255,0.08)'; c.fill();
    txt(c, sub, W / 2, 942, { size: 44, weight: 800 });
    c.restore();
  }
  // video player
  const vp = E.outBack(P(u, 1.9, 0.45));
  if (vp <= 0) return;
  const vw = 760, vh = 428, vy = lerp(1500, MID_Y + 60, vp);
  c.save(); c.translate(W / 2, vy);
  card(c, 0, 0, vw + 20, vh + 20, { r: 28 });
  c.save(); rr(c, -vw / 2, -vh / 2, vw, vh, 20); c.clip();
  c.fillStyle = grad(c, -vw / 2, -vh / 2, vw / 2, vh / 2, '#312e81', '#0e7490'); c.fillRect(-vw / 2, -vh / 2, vw, vh);
  // presenter silhouette, subtly talking
  const talk = Math.abs(Math.sin(u * 14)) * 4;
  c.fillStyle = 'rgba(255,255,255,0.9)';
  c.beginPath(); c.arc(0, -40 - talk * 0.3, 62, 0, TAU); c.fill();
  c.beginPath(); c.ellipse(0, 130, 150, 110, 0, Math.PI, TAU); c.fill();
  c.fillStyle = 'rgba(0,0,0,0.75)'; rr(c, -260, 120, 520, 62, 14); c.fill();
  txt(c, 'سلام! امروز یه ترفند باحال دارم…', 0, 152, { size: 32, weight: 800, max: 490 });
  c.restore();
  // progress + play
  const pr = P(u, 2.2, 1.3);
  rr(c, -vw / 2 + 30, vh / 2 - 26, vw - 60, 8, 4); c.fillStyle = 'rgba(255,255,255,0.25)'; c.fill();
  rr(c, vw / 2 - 30 - (vw - 60) * pr, vh / 2 - 26, (vw - 60) * pr, 8, 4); c.fillStyle = C.cyan; c.fill();
  const pa = 1 - P(u, 2.2, 0.25);
  if (pa > 0) {
    c.save(); c.globalAlpha = pa; c.scale(1 + (1 - pa) * 0.6, 1 + (1 - pa) * 0.6);
    c.beginPath(); c.arc(0, 0, 60, 0, TAU); c.fillStyle = 'rgba(0,0,0,0.5)'; c.fill();
    c.beginPath(); c.moveTo(-18, -28); c.lineTo(30, 0); c.lineTo(-18, 28); c.closePath(); c.fillStyle = '#fff'; c.fill();
    c.restore();
  }
  c.restore();
}

const CODE = [
  [['const ', '#c084fc'], ['app', '#67e8f9'], [' = ', '#fff'], ['createApp', '#fbbf24'], ['({', '#fff']],
  [['  name: ', '#e2e8f0'], ["'Todo'", '#86efac'], [',', '#fff']],
  [['  theme: ', '#e2e8f0'], ["'neon'", '#86efac'], [',', '#fff']],
  [['});', '#fff']],
  [['', '#fff']],
  [['app', '#67e8f9'], ['.', '#fff'], ['list', '#fbbf24'], ['(tasks)', '#fff']],
  [['  .', '#fff'], ['onCheck', '#fbbf24'], ['(done)', '#fff']],
  [['app', '#67e8f9'], ['.', '#fff'], ['deploy', '#fbbf24'], ['();', '#fff'], [' // ✓', '#64748b']],
];
function sceneCode(c, t, u) {
  const pa = 1 - P(u, 1.7, 0.3);
  prompt(c, W / 2, 640, 800, 'یه اپ لیست کارها برام بساز', P(u, 0.05, 0.55), t, pa * E.outCubic(P(u, 0, 0.2)));
  // editor
  const ep = pop(u, 0.55, 0.35);
  const slide = E.inOut(P(u, 1.8, 0.45));
  const ew = 800, eh = 470;
  if (ep > 0) {
    c.save(); c.translate(W / 2 - slide * 200, MID_Y + 60); c.scale(ep * lerp(1, 0.78, slide), ep * lerp(1, 0.78, slide));
    c.globalAlpha = lerp(1, 0.55, slide);
    card(c, 0, 0, ew, eh, { r: 24, fill: '#0d1124' });
    ['#fb7185', '#fbbf24', '#34d399'].forEach((col, i) => { c.beginPath(); c.arc(-ew / 2 + 36 + i * 30, -eh / 2 + 32, 9, 0, TAU); c.fillStyle = col; c.fill(); });
    font(c, 30, 500, MONO); c.textAlign = 'left'; c.textBaseline = 'middle'; c.direction = 'ltr';
    const totalChars = CODE.reduce((n, l) => n + l.reduce((m, s) => m + s[0].length, 0) + 1, 0);
    let budget = Math.floor(totalChars * P(u, 0.65, 1.1));
    let lastX = 0, lastY = 0;
    CODE.forEach((line, li) => {
      let x = -ew / 2 + 40; const y = -eh / 2 + 88 + li * 46;
      c.fillStyle = 'rgba(255,255,255,0.25)'; c.fillText(String(li + 1), x, y); x += 48;
      for (const [s, col] of line) {
        if (budget <= 0) break;
        const part = s.slice(0, budget); budget -= part.length;
        c.fillStyle = col; c.fillText(part, x, y); x += c.measureText(part).width;
        lastX = x; lastY = y;
      }
      budget -= 1;
    });
    if (Math.floor(t * 4) % 2 === 0) { c.fillStyle = C.cyan; c.fillRect(lastX + 2, lastY - 18, 3, 36); }
    c.restore();
  }
  // phone with the generated app
  if (slide <= 0) return;
  const ph = E.outBack(P(u, 1.85, 0.45));
  const pw = 320, phh = 530;
  c.save(); c.translate(lerp(W + 300, W / 2 + 215, ph), MID_Y + 25);
  c.save(); c.shadowColor = 'rgba(34,211,238,0.5)'; c.shadowBlur = 50;
  rr(c, -pw / 2, -phh / 2, pw, phh, 46); c.fillStyle = '#0a0d1c'; c.fill(); c.restore();
  rr(c, -pw / 2, -phh / 2, pw, phh, 46); c.lineWidth = 4; c.strokeStyle = grad(c, -pw / 2, -phh / 2, pw / 2, phh / 2); c.stroke();
  rr(c, -50, -phh / 2 + 16, 100, 22, 11); c.fillStyle = '#000'; c.fill();
  txt(c, 'کارهای امروز', pw / 2 - 30, -phh / 2 + 80, { size: 36, weight: 900, align: 'right' });
  const items = ['خرید', 'باشگاه', 'ارسال گزارش', 'زنگ به مامان'];
  items.forEach((s, i) => {
    const y = -phh / 2 + 152 + i * 80;
    rr(c, -pw / 2 + 20, y - 33, pw - 40, 66, 18); c.fillStyle = 'rgba(255,255,255,0.06)'; c.fill();
    const k = P(u, 2.35 + i * 0.22, 0.35);
    const bxp = pw / 2 - 52;
    if (k > 0) check(c, bxp, y, 20, k);
    else { c.beginPath(); c.arc(bxp, y, 18, 0, TAU); c.lineWidth = 3; c.strokeStyle = 'rgba(255,255,255,0.4)'; c.stroke(); }
    txt(c, s, bxp - 36, y + 2, { size: 30, weight: 800, align: 'right', color: k >= 1 ? 'rgba(255,255,255,0.45)' : '#fff' });
    if (k >= 1) { const sw = measure(c, s, 30, 800); c.fillStyle = 'rgba(255,255,255,0.5)'; c.fillRect(bxp - 36 - sw, y + 2, sw, 3); }
  });
  c.beginPath(); c.arc(-pw / 2 + 62, phh / 2 - 56, 32, 0, TAU); c.fillStyle = grad(c, -pw / 2 + 32, 0, -pw / 2 + 108, 0); c.fill();
  c.fillStyle = '#0b0f1a'; c.fillRect(-pw / 2 + 60, phh / 2 - 70, 4, 28); c.fillRect(-pw / 2 + 48, phh / 2 - 58, 28, 4);
  c.restore();
}

// --- end: neural brain with orbiting tool icons
const NODES = Array.from({ length: 34 }, (_, i) => {
  const a = rnd(i, 11) * TAU, r = Math.sqrt(rnd(i, 12));
  const side = i % 2 ? 1 : -1;
  return { x: side * (30 + Math.abs(Math.cos(a)) * r * 150), y: Math.sin(a) * r * 140 - 10 };
});
const EDGES = [];
NODES.forEach((a, i) => NODES.forEach((b, j) => { if (j > i && Math.hypot(a.x - b.x, a.y - b.y) < 95) EDGES.push([i, j]); }));
function miniIcon(c, k, r) {
  c.save(); c.strokeStyle = '#fff'; c.fillStyle = '#fff'; c.lineWidth = 4; c.lineCap = 'round'; c.lineJoin = 'round';
  if (k === 0) { rr(c, -r * 0.5, -r * 0.34, r, r * 0.68, 5); c.stroke(); c.beginPath(); c.moveTo(-r * 0.45, -r * 0.28); c.lineTo(0, r * 0.06); c.lineTo(r * 0.45, -r * 0.28); c.stroke(); }
  if (k === 1) { rr(c, -r * 0.5, -r * 0.34, r, r * 0.68, 5); c.stroke(); c.fillRect(-r * 0.3, r * 0.05, 7, r * 0.18); c.fillRect(-r * 0.08, -r * 0.08, 7, r * 0.31); c.fillRect(r * 0.14, -r * 0.18, 7, r * 0.41); }
  if (k === 2) { rr(c, -r * 0.48, -r * 0.4, r * 0.96, r * 0.8, 6); c.stroke(); c.beginPath(); c.moveTo(-r * 0.4, r * 0.3); c.lineTo(-r * 0.1, -r * 0.05); c.lineTo(r * 0.1, r * 0.15); c.lineTo(r * 0.4, r * 0.3); c.stroke(); c.beginPath(); c.arc(r * 0.2, -r * 0.18, 5, 0, TAU); c.fill(); }
  if (k === 3) { rr(c, -r * 0.14, -r * 0.42, r * 0.28, r * 0.5, r * 0.14); c.fill(); c.beginPath(); c.arc(0, 0, r * 0.28, 0.1 * Math.PI, 0.9 * Math.PI); c.stroke(); c.beginPath(); c.moveTo(0, r * 0.3); c.lineTo(0, r * 0.42); c.stroke(); }
  if (k === 4) { font(c, r * 0.55, 800, MONO); c.textAlign = 'center'; c.textBaseline = 'middle'; c.direction = 'ltr'; c.fillText('</>', 0, 2); }
  c.restore();
}
function sceneEnd(c, t, u) {
  const bin = E.outBack(P(u, 0, 0.5));
  const boom = t >= MIDNIGHT ? t - MIDNIGHT : -1;
  const cy = MID_Y - 10 - (boom >= 0 ? E.inOut(clamp(boom / 0.6)) * 60 : 0);
  const sc = 1.3 * bin * (boom >= 0 ? lerp(1.25, 0.82, E.outCubic(clamp(boom / 0.5))) : 1 + 0.15 * E.inCubic(P(u, 0.3, 1.7)));
  // orbit speeds up into midnight, then calms
  const spin = u < 2 ? 0.6 * u + 1.2 * Math.pow(u / 2, 3) * 2 : 2.4 + (u - 2) * 0.5;
  c.save(); c.translate(W / 2, cy); c.scale(sc, sc);
  // brain glow
  const rg = c.createRadialGradient(0, 0, 0, 0, 0, 320);
  rg.addColorStop(0, `rgba(139,92,246,${0.45 + 0.2 * beatPulse(t)})`); rg.addColorStop(1, 'rgba(139,92,246,0)');
  c.fillStyle = rg; c.fillRect(-320, -320, 640, 640);
  // edges with travelling pulses
  EDGES.forEach(([i, j], k) => {
    const a = NODES[i], b = NODES[j];
    c.strokeStyle = 'rgba(167,139,250,0.35)'; c.lineWidth = 2;
    c.beginPath(); c.moveTo(a.x, a.y); c.lineTo(b.x, b.y); c.stroke();
    const ph = (t * (0.8 + rnd(k, 13)) + rnd(k, 14)) % 1;
    c.fillStyle = C.cyan; c.fillRect(lerp(a.x, b.x, ph) - 2.5, lerp(a.y, b.y, ph) - 2.5, 5, 5);
  });
  NODES.forEach((n, i) => {
    c.beginPath(); c.arc(n.x, n.y, 5 + 3 * beatPulse(t + rnd(i, 15) * 0.3), 0, TAU);
    c.fillStyle = i % 4 ? '#e9d5ff' : C.cyan; c.fill();
  });
  c.fillStyle = 'rgba(255,255,255,0.18)'; c.fillRect(-2, -150, 4, 290); // hemisphere split
  // orbiting icons
  for (let k = 0; k < 5; k++) {
    const a = spin + k * TAU / 5;
    const x = Math.cos(a) * 290, y = Math.sin(a) * 120;
    const front = Math.sin(a) > 0;
    const ip = pop(u, 0.1 + k * 0.08, 0.35);
    if (ip <= 0) continue;
    c.save(); c.translate(x, y); c.scale(ip * (front ? 1 : 0.8), ip * (front ? 1 : 0.8));
    c.globalAlpha = front ? 1 : 0.6;
    c.beginPath(); c.arc(0, 0, 50, 0, TAU); c.fillStyle = '#151a3a'; c.fill();
    c.lineWidth = 3; c.strokeStyle = k % 2 ? C.cyan : C.violet; c.stroke();
    miniIcon(c, k, 56);
    c.restore();
  }
  c.restore();
  // pre-midnight line, glitches out at the impact
  if (boom < 0) {
    const a = E.outExpo(P(u, 0.2, 0.45));
    c.save(); c.translate(0, (1 - a) * 40);
    txt(c, '۲۴ ساعت، ۵ تا ابزار', W / 2, CAP_Y, { size: 82, weight: 900, glow: 28, alpha: a });
    c.restore();
  }
  // midnight shockwave
  if (boom >= 0 && boom < 0.9) {
    const r = E.outCubic(boom / 0.9) * 900;
    c.save(); c.globalAlpha = 1 - boom / 0.9; c.lineWidth = 10 * (1 - boom / 0.9) + 1;
    c.strokeStyle = '#fff'; c.beginPath(); c.arc(W / 2, MID_Y, r, 0, TAU); c.stroke();
    c.restore();
  }
  // question + arrow
  if (boom >= 0.35) {
    const q = E.outBack(P(boom, 0.35, 0.45));
    c.save(); c.translate(W / 2, CAP_Y); c.scale(q, q);
    txt(c, 'تو کدومشو استفاده می‌کنی؟', 0, 0, { size: 84, weight: 900, gradFill: true, glow: 30, max: 900 });
    c.restore();
    const a = P(boom, 0.7, 0.3);
    txt(c, 'تو کامنت بگو', W / 2, CAP_Y + 105, { size: 48, weight: 700, color: C.dim, alpha: a });
    const y = CAP_Y + 190 + Math.abs(Math.sin(t * 6)) * 16;
    c.save(); c.globalAlpha = a; c.strokeStyle = C.cyan; c.lineWidth = 8; c.lineCap = 'round'; c.lineJoin = 'round';
    c.shadowColor = C.cyan; c.shadowBlur = 20;
    c.beginPath(); c.moveTo(W / 2, y - 40); c.lineTo(W / 2, y + 18); c.moveTo(W / 2 - 24, y - 6); c.lineTo(W / 2, y + 18); c.lineTo(W / 2 + 24, y - 6); c.stroke();
    c.restore();
  }
}

function sceneSave(c, t, u) {
  // bookmark: outline draws, then fills with a pop
  const bp = E.outBack(P(u, 0.1, 0.4));
  const fill = P(u, 0.55, 0.25);
  const bw = 210, bh = 280, cy = 800;
  c.save(); c.translate(W / 2, cy); c.scale(bp * (1 + 0.12 * Math.exp(-Math.max(0, u - 0.55) * 8) * (u > 0.55)), bp);
  c.beginPath();
  c.moveTo(-bw / 2 + 24, -bh / 2); c.lineTo(bw / 2 - 24, -bh / 2); c.quadraticCurveTo(bw / 2, -bh / 2, bw / 2, -bh / 2 + 24);
  c.lineTo(bw / 2, bh / 2); c.lineTo(0, bh / 2 - 80); c.lineTo(-bw / 2, bh / 2); c.lineTo(-bw / 2, -bh / 2 + 24);
  c.quadraticCurveTo(-bw / 2, -bh / 2, -bw / 2 + 24, -bh / 2); c.closePath();
  c.shadowColor = 'rgba(139,92,246,0.9)'; c.shadowBlur = 50;
  if (fill > 0) { c.globalAlpha = fill; c.fillStyle = grad(c, 0, -bh / 2, 0, bh / 2, C.cyan, C.violet); c.fill(); c.globalAlpha = 1; }
  c.lineWidth = 12; c.lineJoin = 'round'; c.strokeStyle = '#fff'; c.stroke();
  c.restore();
  const sp = P(u, 0.7, 0.8);
  if (sp > 0 && sp < 1) for (let i = 0; i < 8; i++) {
    const a = i / 8 * TAU, r = 170 + sp * 120;
    c.save(); c.globalAlpha = 1 - sp; sparkle(c, W / 2 + Math.cos(a) * r, cy + Math.sin(a) * r, 22, sp * 4, i % 2 ? C.cyan : '#fff'); c.restore();
  }
  const s1 = P(u, 0.1, 0.3);
  if (s1 > 0) {
    const k = lerp(1.5, 1, E.outExpo(s1));
    c.save(); c.translate(W / 2, 1110); c.scale(k, k);
    txt(c, 'Save کن', 0, 0, { size: 140, weight: 900, gradFill: true, glow: 40, alpha: clamp(s1 * 3) });
    c.restore();
  }
  txt(c, 'که یادت نره', W / 2, 1250 + (1 - E.outCubic(P(u, 0.35, 0.4))) * 30, { size: 72, weight: 800, alpha: P(u, 0.35, 0.4) });
  if (HANDLE) txt(c, HANDLE, W / 2, 1370, { size: 44, weight: 700, fam: MONO, rtl: false, color: C.dim, alpha: P(u, 0.6, 0.4) });
}

const DRAW = { hook: sceneHook, mail: sceneMail, slides: sceneSlides, image: sceneImage, voice: sceneVoice, code: sceneCode, end: sceneEnd, save: sceneSave };

// ---------------------------------------------------------------- post FX
function fxAmt(t, type) {
  let v = 0, o = null;
  for (const f of FX) if (f.type === type && t >= f.t && t < f.t + f.dur) { const k = 1 - (t - f.t) / f.dur; if (k * (f.amp || f.a) > v) { v = k * (f.amp || f.a); o = f; } }
  return v;
}
function frame(t) {
  const c = bx;
  c.setTransform(1, 0, 0, 1, 0, 0);
  c.globalAlpha = 1; c.globalCompositeOperation = 'source-over';
  background(c, t);
  // camera: shake + punch zoom
  const sh = fxAmt(t, 'shake'), pu = fxAmt(t, 'punch');
  c.save();
  c.translate(W / 2 + (rnd(Math.floor(t * 60), 1) - 0.5) * 2 * sh, H / 2 + (rnd(Math.floor(t * 60), 2) - 0.5) * 2 * sh);
  c.scale(1 + pu, 1 + pu);
  c.translate(-W / 2, -H / 2);
  header(c, t);
  drawClock(c, t);
  for (const sc of SCENES) {
    if (t < sc.t0 || t >= sc.t1) continue;
    DRAW[sc.id](c, t, t - sc.t0);
    caption(c, sc, t);
  }
  c.restore();

  // composite + glitch slices
  ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 1;
  ctx.drawImage(buf, 0, 0);
  const g = fxAmt(t, 'glitch');
  if (g > 0) {
    const fr = Math.floor(t * 30);
    for (let i = 0; i < 14; i++) {
      const y = rnd(i, fr) * H, h = 20 + rnd(i, fr + 7) * 140, dx = (rnd(i, fr + 3) - 0.5) * 160 * g;
      ctx.drawImage(buf, 0, y, W, h, dx, y, W, h);
    }
    ctx.globalCompositeOperation = 'lighter';
    ctx.globalAlpha = 0.35 * g;
    ctx.drawImage(buf, 14 * g, 0); // ghost offset reads as RGB split on the neon edges
    ctx.globalCompositeOperation = 'source-over';
    for (let i = 0; i < 6; i++) {
      ctx.globalAlpha = 0.25 * g;
      ctx.fillStyle = i % 2 ? C.cyan : C.pink;
      ctx.fillRect(0, rnd(i, fr + 11) * H, W, 3 + rnd(i, fr + 12) * 10);
    }
    ctx.globalAlpha = 1;
  }
  const fl = fxAmt(t, 'flash');
  if (fl > 0) { ctx.globalAlpha = fl; ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
  // gentle fade at the very end so the loop back to the hook feels like a cut
  const endFade = P(t, DURATION - 0.25, 0.25);
  if (endFade > 0) { ctx.globalAlpha = endFade; ctx.fillStyle = '#000'; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
}

window.renderOnly = (t) => frame(t);
window.renderFrame = (t, q = 0.95) => { frame(t); return canvas.toDataURL('image/jpeg', q); };
window.DURATION = DURATION;
Promise.all([
  ...[500, 700, 800, 900].map((w) => document.fonts.load(`${w} 40px ${FA}`, 'سلام ۱۲')),
  ...[500, 800].map((w) => document.fonts.load(`${w} 40px ${MONO}`, '0123')),
]).then(() => { frame(0); window.motionReady = true; });
