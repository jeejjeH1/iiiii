'use strict';
// Shared drawing engine (math, text, primitives, pastel background, 8-square logo, icons).
// Deterministic: frame(t) depends only on t, so frames can be rendered out of order / in
// parallel and still match the audio exactly.
const W = 1920, H = 1080;
const { T, BEAT, BAR, DURATION, FX, CUR, L } = TL;
const FONT = '"Inter Display", Inter, sans-serif';
const MONO = '"DejaVu Sans Mono", monospace';
const C = {
  bg: '#f7f4ff', ink: '#17132e', cyan: '#0bb3cf', violet: '#7b5cff', pink: '#ff3d9a', red: '#ff2e5b',
  green: '#12b886', yellow: '#ffd23d', orange: '#f2780c', white: '#ffffff', dim: 'rgba(23,19,46,0.62)',
  dimW: 'rgba(255,255,255,0.65)',
};
let NOW = 0;

// ---------------------------------------------------------------- canvases
const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const ctx = canvas.getContext('2d');
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const buf = mk(W, H), bx = buf.getContext('2d');
const tmp = mk(W, H), tx = tmp.getContext('2d');
const tmp2 = mk(W, H), tx2 = tmp2.getContext('2d');
const sm = mk(480, 270), sx = sm.getContext('2d');
const sm2 = mk(240, 135), sx2 = sm2.getContext('2d');

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
  outBack: (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); },
  outBack2: (x) => { const c1 = 2.6, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); },
  outElastic: (x) => (x <= 0 ? 0 : x >= 1 ? 1 : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * Math.PI) / 3) + 1),
  outQuint: (x) => 1 - Math.pow(1 - x, 5),
};
const rnd = (i, j = 0) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
  h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const TAU = Math.PI * 2;
// beat pulse: 1 right on a beat, decaying
const beatPulse = (t, k = 8) => Math.exp(-((t % BEAT + BEAT) % BEAT) * k);

// ---------------------------------------------------------------- text
const layoutCache = new Map();
function setF(c, o) {
  c.font = `${o.italic ? 'italic ' : ''}${o.weight || 800} ${o.size || 60}px ${o.fam || FONT}`;
  c.letterSpacing = (o.ls || 0) + 'px';
}
function layout(c, s, o) {
  const key = c.font + '|' + c.letterSpacing + '|' + s;
  let v = layoutCache.get(key);
  if (!v) {
    const xs = [];
    for (let i = 0; i <= s.length; i++) xs.push(c.measureText(s.slice(0, i)).width);
    v = { xs, w: xs[s.length] - (o.ls || 0) };
    layoutCache.set(key, v);
  }
  return v;
}
function text(c, s, x, y, o = {}) {
  setF(c, o);
  c.textAlign = o.align || 'center';
  c.textBaseline = 'middle';
  c.fillStyle = o.color || C.ink;
  c.fillText(s, x, y);
}
function textW(c, s, o) { setF(c, o); return layout(c, s, o).w; }
function chromaText(c, s, x, y, o, amt) {
  c.save();
  if (o.dark) {
    // additive RGB split for light-on-dark text
    c.globalCompositeOperation = 'lighter';
    text(c, s, x - amt, y, { ...o, color: '#ff0000' });
    text(c, s, x, y + amt * 0.25, { ...o, color: '#00ff00' });
    text(c, s, x + amt, y, { ...o, color: '#0000ff' });
    c.restore();
    return;
  }
  c.globalCompositeOperation = 'multiply';
  text(c, s, x - amt, y, { ...o, color: '#00d8ff' });
  text(c, s, x, y + amt * 0.25, { ...o, color: '#ff1fb4' });
  text(c, s, x + amt, y, { ...o, color: '#2b2440' });
  c.restore();
}
// Slam-in text: scales down from big with ghost trails + chromatic split
function slam(c, s, x, y, o, t, t0, a = {}) {
  if (t < t0) return;
  const dur = a.dur || 0.38, from = a.from || 2.3;
  const p = E.outExpo(P(t, t0, dur));
  let sc = lerp(from, 1, p);
  let al = clamp((t - t0) / 0.05);
  let dx = 0, dy = 0;
  if (a.out != null && t > a.out) {
    const q = E.inCubic(P(t, a.out, a.outDur || 0.18));
    if (a.outDir === 'left') dx = -q * 1400;
    else if (a.outDir === 'right') dx = q * 1400;
    else if (a.outDir === 'up') dy = -q * 300;
    else sc *= 1 + q * 0.8;
    al *= 1 - q;
  }
  if (al <= 0.001) return;
  c.save();
  c.translate(x + dx, y + dy);
  c.globalAlpha *= al;
  if (p < 1) {
    for (let k = 3; k >= 1; k--) {
      c.save();
      const g = sc * (1 + k * 0.18 * (1 - p));
      c.scale(g, g);
      c.globalAlpha *= 0.22 * (1 - p);
      text(c, s, 0, 0, o);
      c.restore();
    }
  }
  c.scale(sc, sc);
  const ca = (a.chroma === false ? 0 : 1) * ((1 - p) * 22 + (a.ca || 0));
  if (ca > 0.6 && !o.gradient) chromaText(c, s, 0, 0, o, ca);
  else {
    if (o.gradient) o = { ...o, color: o.gradient(c, textW(c, s, o)) };
    text(c, s, 0, 0, o);
  }
  c.restore();
}
// per-char rise reveal
function rise(c, s, x, y, o, t, t0, a = {}) {
  if (t < t0) return;
  const stg = a.stagger ?? 0.022, dur = a.dur ?? 0.45;
  setF(c, o);
  const { xs, w } = layout(c, s, o);
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  let outA = 1, outDy = 0;
  if (a.out != null && t > a.out) { const q = E.inCubic(P(t, a.out, 0.2)); outA = 1 - q; outDy = -q * 40; }
  c.save();
  c.textAlign = 'left'; c.textBaseline = 'middle';
  c.fillStyle = o.gradient ? o.gradient(c, w, x0) : (o.color || C.ink);
  for (let i = 0; i < s.length; i++) {
    const p = E.outCubic(P(t, t0 + i * stg, dur));
    if (p <= 0) break;
    c.globalAlpha = p * outA * (o.alpha ?? 1);
    c.fillText(s[i], x0 + xs[i], y + (1 - p) * (o.size || 60) * 0.7 + outDy);
  }
  c.restore();
}
const GLY = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&*+=<>/?@';
function scramble(c, s, x, y, o, t, t0, dur, a = {}) {
  if (t < t0) return;
  setF(c, o);
  const { xs, w } = layout(c, s, o);
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  const p = (t - t0) / dur;
  let outA = 1;
  if (a.out != null && t > a.out) outA = 1 - E.inCubic(P(t, a.out, 0.2));
  c.save();
  c.globalAlpha *= outA;
  c.textAlign = 'left'; c.textBaseline = 'middle';
  const fill = o.gradient ? o.gradient(c, w, x0) : (o.color || C.ink);
  const n = s.length;
  for (let i = 0; i < n; i++) {
    const ch = s[i];
    if (ch === ' ') continue;
    const ri = i / n;
    if (p < ri * 0.45) break;
    let d = ch;
    if (p < 0.45 + ri * 0.55) {
      d = GLY[Math.floor(rnd(i * 7 + 3, Math.floor(t * 28)) * GLY.length)];
      c.fillStyle = o.scol || C.cyan;
    } else c.fillStyle = fill;
    c.fillText(d, x0 + xs[i], y);
  }
  c.restore();
}
function typeOn(c, s, x, y, o, t, t0, dur, a = {}) {
  if (t < t0) return;
  setF(c, o);
  const { xs, w } = layout(c, s, o);
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  const n = Math.floor(clamp((t - t0) / dur) * s.length);
  let outA = 1;
  if (a.out != null && t > a.out) outA = 1 - E.inCubic(P(t, a.out, 0.2));
  c.save();
  c.globalAlpha *= outA * (o.alpha ?? 1);
  c.textAlign = 'left'; c.textBaseline = 'middle';
  c.fillStyle = o.gradient ? o.gradient(c, w, x0) : (o.color || C.ink);
  c.fillText(s.slice(0, n), x0, y);
  if (a.caret !== false && (n < s.length || Math.floor(t * 3) % 2 === 0)) {
    c.fillStyle = a.caretColor || C.cyan;
    const sz = o.size || 60;
    c.fillRect(x0 + xs[n] + 4, y - sz * 0.48, Math.max(3, sz * 0.07), sz * 0.96);
  }
  c.restore();
}
const linGrad = (stops) => (c, w, x0 = -w / 2) => {
  const g = c.createLinearGradient(x0, 0, x0 + w, 0);
  stops.forEach((s, i) => g.addColorStop(i / (stops.length - 1), s));
  return g;
};
const G_BRAND = linGrad(['#09a9c4', C.violet, C.pink]);
const G_COOL = linGrad(['#09a9c4', '#4f6bff', C.violet]);
const G_GREEN = linGrad(['#0fa776', '#09a9c4', '#5b6cff']);

// ---------------------------------------------------------------- primitives
function rr(c, x, y, w, h, r) { c.beginPath(); c.roundRect(x, y, w, h, r); }
function ring(c, x, y, t, t0, dur, maxR, col, lw = 4) {
  const p = P(t, t0, dur);
  if (p <= 0 || p >= 1) return;
  const e = E.outCubic(p);
  c.save();
  c.strokeStyle = `rgba(${col},${(1 - p) * 0.8})`;
  c.lineWidth = lw * (1 - p) + 0.5;
  c.beginPath(); c.arc(x, y, e * maxR, 0, TAU); c.stroke();
  c.restore();
}
function rays(c, x, y, t, o = {}) {
  const n = o.n || 18, len = o.len || 1400, al = Math.min(0.9, (o.a ?? 0.12) * 3.2), col = o.col || '255,255,255';
  c.save();
  c.translate(x, y);
  c.rotate(t * (o.speed ?? 0.15));
  const g = c.createRadialGradient(0, 0, 0, 0, 0, len);
  g.addColorStop(0, `rgba(${col},${al})`);
  g.addColorStop(1, `rgba(${col},0)`);
  c.fillStyle = g;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * TAU, wdt = (TAU / n) * 0.32;
    c.beginPath(); c.moveTo(0, 0);
    c.arc(0, 0, len, a - wdt, a + wdt);
    c.closePath(); c.fill();
  }
  c.restore();
}
const BLOBS = [
  [0.18, 0.22, '255,186,224', 760], [0.82, 0.18, '168,240,214', 820], [0.55, 0.55, '236,224,255', 900],
  [0.12, 0.78, '178,228,255', 780], [0.88, 0.82, '255,196,230', 760], [0.45, 0.95, '176,240,200', 700],
  [0.7, 0.45, '214,196,255', 650],
];
function bubble(c, x, y, r, t, a = 1) {
  if (a <= 0.01) return;
  c.save();
  c.globalAlpha *= a;
  const g = c.createRadialGradient(x - r * 0.25, y - r * 0.3, r * 0.05, x, y, r);
  g.addColorStop(0, 'rgba(255,255,255,0.10)');
  g.addColorStop(0.7, 'rgba(255,255,255,0.18)');
  g.addColorStop(0.92, 'rgba(230,210,255,0.55)');
  g.addColorStop(1, 'rgba(255,255,255,0.0)');
  c.fillStyle = g;
  c.beginPath(); c.arc(x, y, r, 0, TAU); c.fill();
  const cg = c.createConicGradient(t * 0.7 + x * 0.003, x, y);
  ['255,120,200', '120,220,255', '140,240,190', '190,150,255', '255,200,140', '255,120,200'].forEach((col, k) => cg.addColorStop(k / 5, `rgba(${col},0.55)`));
  c.strokeStyle = cg;
  c.lineWidth = Math.max(1.5, r * 0.09);
  c.beginPath(); c.arc(x, y, r * 0.94, 0, TAU); c.stroke();
  const ig = c.createRadialGradient(x + r * 0.35, y + r * 0.4, 0, x + r * 0.35, y + r * 0.4, r * 0.8);
  ig.addColorStop(0, 'rgba(210,140,255,0.28)'); ig.addColorStop(1, 'rgba(210,140,255,0)');
  c.fillStyle = ig;
  c.beginPath(); c.arc(x, y, r * 0.95, 0, TAU); c.fill();
  c.translate(x - r * 0.38, y - r * 0.42);
  c.rotate(-0.7);
  c.fillStyle = 'rgba(255,255,255,0.9)';
  c.beginPath(); c.ellipse(0, 0, r * 0.22, r * 0.11, 0, 0, TAU); c.fill();
  c.restore();
  c.save();
  c.globalAlpha *= a * 0.7;
  c.fillStyle = '#fff';
  c.beginPath(); c.arc(x + r * 0.45, y + r * 0.5, r * 0.06, 0, TAU); c.fill();
  c.restore();
}
function bubbles(c, t, n = 7, seed = 50) {
  for (let i = 0; i < n; i++) {
    const r = 28 + Math.pow(rnd(i, seed), 2) * 170;
    const sp = 18 + rnd(i, seed + 1) * 40;
    const span = H + 2 * r + 200;
    const y = H + r + 100 - ((rnd(i, seed + 2) * span + t * sp) % span);
    const x = rnd(i, seed + 3) * W + Math.sin(t * 0.6 + i * 1.7) * 40;
    bubble(c, x, y, r, t, 1);
  }
}
function bgBase(c, col = 'rgba(60,30,140,0.45)', cx = W / 2, cy = H / 2, rad = 1100) {
  const t = NOW;
  c.fillStyle = C.bg;
  c.fillRect(0, 0, W, H);
  BLOBS.forEach(([bx, by, rgb, r], i) => {
    const x = W * bx + Math.sin(t * 0.35 + i * 2.1) * 140;
    const y = H * by + Math.cos(t * 0.3 + i * 1.3) * 110;
    const g = c.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `rgba(${rgb},0.95)`);
    g.addColorStop(0.55, `rgba(${rgb},0.45)`);
    g.addColorStop(1, `rgba(${rgb},0)`);
    c.fillStyle = g;
    c.fillRect(0, 0, W, H);
  });
  // scene mood tint (hue of the passed colour, kept light)
  const m = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(col);
  if (m) {
    const g = c.createRadialGradient(cx, cy, 0, cx, cy, rad);
    g.addColorStop(0, `rgba(${m[1]},${m[2]},${m[3]},0.16)`);
    g.addColorStop(1, `rgba(${m[1]},${m[2]},${m[3]},0)`);
    c.fillStyle = g;
    c.fillRect(0, 0, W, H);
  }
  bubbles(c, t, 7);
}
function dust(c, t, n, seed, alpha = 0.6, col = '140,110,240') {
  alpha *= 0.55;
  c.save();
  c.fillStyle = `rgb(${col})`;
  for (let i = 0; i < n; i++) {
    const x = (((rnd(i, seed) * W + t * (rnd(i, seed + 1) - 0.5) * 60) % W) + W) % W;
    const y = (((rnd(i, seed + 2) * H - t * (12 + rnd(i, seed + 3) * 40)) % H) + H) % H;
    const r = 1 + rnd(i, seed + 4) * 2.6;
    const tw = 0.5 + 0.5 * Math.sin(t * (1 + rnd(i, seed + 5) * 3) + i);
    c.globalAlpha = alpha * tw * (0.3 + 0.7 * rnd(i, seed + 6));
    c.fillRect(x, y, r, r);
  }
  c.restore();
}
function warp(c, t, amt, seed = 7, col = '110,90,230', cx = W / 2, cy = H / 2) {
  if (amt <= 0.01) return;
  c.save();
  c.lineCap = 'round';
  for (let i = 0; i < 240; i++) {
    const a = rnd(i, seed) * TAU, sp = 0.5 + rnd(i, seed + 1) * 0.9;
    const z = (rnd(i, seed + 2) + t * sp * 0.9) % 1;
    const r0 = 30 + Math.pow(z, 2.6) * 1500;
    const len = Math.pow(z, 1.6) * 520 * amt + 3;
    const ca = Math.cos(a), sa = Math.sin(a);
    c.strokeStyle = `rgba(${i % 3 === 0 ? '255,61,154' : i % 3 === 1 ? '11,179,207' : col},${Math.min(0.75, z * 0.9 * amt)})`;
    c.lineWidth = 0.8 + z * 3.2;
    c.beginPath();
    c.moveTo(cx + ca * r0, cy + sa * r0);
    c.lineTo(cx + ca * (r0 + len), cy + sa * (r0 + len));
    c.stroke();
  }
  c.restore();
}
function gridFloor(c, t, o = {}) {
  const hy = o.hy ?? 700, a = o.a ?? 0.32, col = o.col ?? '138,92,255', sp = o.sp ?? 0.8;
  c.save();
  c.beginPath(); c.rect(0, hy, W, H - hy); c.clip();
  const vg = c.createLinearGradient(0, hy, 0, H);
  vg.addColorStop(0, `rgba(${col},0)`); vg.addColorStop(1, `rgba(${col},${a})`);
  c.strokeStyle = vg;
  c.lineWidth = 1.6;
  c.beginPath();
  for (let i = -26; i <= 26; i++) { c.moveTo(960 + i * 6, hy); c.lineTo(960 + i * 170, H + 40); }
  c.stroke();
  c.strokeStyle = `rgba(${col},${a})`;
  const ph = (t * sp) % 1;
  for (let n = 0; n < 30; n++) {
    const d = n + 1 - ph;
    const y = hy + (H - hy) / d;
    if (y > H) continue;
    c.globalAlpha = clamp(1.6 / d) * clamp((y - hy) / 120);
    c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke();
  }
  c.restore();
  // horizon glow
  c.save();
  const hg = c.createLinearGradient(0, hy - 30, 0, hy + 30);
  hg.addColorStop(0, `rgba(${col},0)`);
  hg.addColorStop(0.5, `rgba(255,255,255,0.7)`);
  hg.addColorStop(1, `rgba(${col},0)`);
  c.fillStyle = hg;
  c.fillRect(0, hy - 30, W, 60);
  c.restore();
}

// ---------------------------------------------------------------- the logo
// 8 squares on a ring, each rotated so its diagonal points radially.
function logo(c, x, y, r, o = {}) {
  const side = r * 0.56;
  c.save();
  c.translate(x, y);
  if (o.scale) c.scale(o.scale, o.scale);
  if (o.rot) c.rotate(o.rot);
  for (let i = 0; i < 8; i++) {
    const a = -Math.PI / 2 + i * Math.PI / 4;
    let m = { x: Math.cos(a) * r, y: Math.sin(a) * r, rot: a + Math.PI / 4, s: 1, a: 1, col: o.color || C.ink };
    if (o.sq) { const mm = o.sq(i, a, m); if (!mm) continue; m = Object.assign(m, mm); }
    if (m.a <= 0) continue;
    c.save();
    c.translate(m.x, m.y);
    c.rotate(m.rot);
    c.scale(m.s, m.s);
    c.globalAlpha *= m.a;
    c.fillStyle = m.col;
    c.fillRect(-side / 2, -side / 2, side, side);
    if (m.flash > 0) {
      c.globalCompositeOperation = 'lighter';
      c.globalAlpha = m.flash;
      c.fillStyle = '#fff';
      c.fillRect(-side / 2, -side / 2, side, side);
    }
    c.restore();
  }
  c.restore();
}
const logoSquarePos = (i, r) => {
  const a = -Math.PI / 2 + i * Math.PI / 4;
  return { x: Math.cos(a) * r, y: Math.sin(a) * r, a };
};

// ---------------------------------------------------------------- icons (line icons, centered, size s)
function icon(c, kind, x, y, s, col = '#fff', lw = 0) {
  c.save();
  c.translate(x, y);
  const u = s / 2;
  c.strokeStyle = col; c.fillStyle = col;
  c.lineWidth = lw || Math.max(2, s * 0.075);
  c.lineCap = 'round'; c.lineJoin = 'round';
  const line = (pts, close) => { c.beginPath(); pts.forEach(([px, py], k) => (k ? c.lineTo(px * u, py * u) : c.moveTo(px * u, py * u))); if (close) c.closePath(); c.stroke(); };
  switch (kind) {
    case 'wallet':
      rr(c, -0.9 * u, -0.6 * u, 1.8 * u, 1.3 * u, 0.18 * u); c.stroke();
      line([[-0.9, -0.6], [0.5, -0.95], [0.6, -0.6]]);
      rr(c, 0.25 * u, -0.15 * u, 0.65 * u, 0.4 * u, 0.12 * u); c.stroke();
      c.beginPath(); c.arc(0.48 * u, 0.05 * u, 0.06 * u, 0, TAU); c.fill();
      break;
    case 'bolt':
      line([[0.15, -0.95], [-0.55, 0.12], [-0.02, 0.12], [-0.15, 0.95], [0.55, -0.15], [0.02, -0.15]], true);
      break;
    case 'nodes': {
      const pts = [[0, -0.7], [-0.75, 0.5], [0.75, 0.5], [0, 0.05]];
      line([pts[0], pts[3]]); line([pts[1], pts[3]]); line([pts[2], pts[3]]);
      line([pts[0], pts[1]]); line([pts[1], pts[2]]); line([pts[2], pts[0]]);
      pts.forEach(([px, py]) => { c.beginPath(); c.arc(px * u, py * u, 0.17 * u, 0, TAU); c.fill(); });
      break;
    }
    case 'shield':
      line([[0, -0.95], [0.8, -0.62], [0.72, 0.2], [0, 0.95], [-0.72, 0.2], [-0.8, -0.62]], true);
      line([[-0.32, 0.02], [-0.06, 0.3], [0.38, -0.25]]);
      break;
    case 'access':
      c.beginPath(); c.arc(0, 0, 0.92 * u, 0, TAU); c.stroke();
      c.beginPath(); c.arc(0, -0.45 * u, 0.13 * u, 0, TAU); c.fill();
      line([[-0.5, -0.2], [0.5, -0.2]]);
      line([[0, -0.2], [0, 0.18], [-0.3, 0.62]]);
      line([[0, 0.18], [0.3, 0.62]]);
      break;
    case 'trust':
      c.beginPath(); c.arc(0, 0, 0.9 * u, 0, TAU); c.stroke();
      line([[-0.38, 0.0], [-0.1, 0.3], [0.42, -0.28]]);
      break;
    case 'gas':
      rr(c, -0.7 * u, -0.85 * u, 0.95 * u, 1.75 * u, 0.12 * u); c.stroke();
      rr(c, -0.52 * u, -0.65 * u, 0.6 * u, 0.42 * u, 0.06 * u); c.fill();
      line([[0.25, -0.3], [0.55, -0.3], [0.62, 0.5], [0.8, 0.5], [0.8, -0.5], [0.55, -0.8]]);
      break;
    case 'user':
      c.beginPath(); c.arc(0, -0.38 * u, 0.36 * u, 0, TAU); c.stroke();
      c.beginPath(); c.arc(0, 0.95 * u, 0.78 * u, Math.PI * 1.08, Math.PI * 1.92); c.stroke();
      break;
    case 'users':
      c.beginPath(); c.arc(-0.32 * u, -0.3 * u, 0.28 * u, 0, TAU); c.stroke();
      c.beginPath(); c.arc(0.38 * u, -0.38 * u, 0.24 * u, 0, TAU); c.stroke();
      c.beginPath(); c.arc(-0.32 * u, 0.85 * u, 0.62 * u, Math.PI * 1.1, Math.PI * 1.9); c.stroke();
      c.beginPath(); c.arc(0.42 * u, 0.62 * u, 0.5 * u, Math.PI * 1.15, Math.PI * 1.85); c.stroke();
      break;
    case 'apps':
      [[-0.85, -0.85], [0.1, -0.85], [-0.85, 0.1], [0.1, 0.1]].forEach(([px, py]) => { rr(c, px * u, py * u, 0.75 * u, 0.75 * u, 0.18 * u); c.stroke(); });
      break;
    case 'server':
      [-0.85, -0.25, 0.35].forEach((py) => { rr(c, -0.85 * u, py * u, 1.7 * u, 0.5 * u, 0.1 * u); c.stroke(); c.beginPath(); c.arc(0.55 * u, (py + 0.25) * u, 0.07 * u, 0, TAU); c.fill(); });
      break;
    case 'check':
      line([[-0.6, 0.02], [-0.18, 0.45], [0.62, -0.42]]);
      break;
    case 'cross':
      line([[-0.5, -0.5], [0.5, 0.5]]); line([[0.5, -0.5], [-0.5, 0.5]]);
      break;
    case 'warn':
      line([[0, -0.9], [0.95, 0.75], [-0.95, 0.75]], true);
      line([[0, -0.3], [0, 0.18]]);
      c.beginPath(); c.arc(0, 0.45 * u, 0.07 * u, 0, TAU); c.fill();
      break;
    case 'cursor':
      line([[-0.45, -0.85], [-0.45, 0.6], [-0.1, 0.28], [0.18, 0.88], [0.38, 0.78], [0.1, 0.2], [0.55, 0.2]], true);
      break;
    case 'globe':
      c.beginPath(); c.arc(0, 0, 0.9 * u, 0, TAU); c.stroke();
      c.beginPath(); c.ellipse(0, 0, 0.4 * u, 0.9 * u, 0, 0, TAU); c.stroke();
      line([[-0.9, 0], [0.9, 0]]);
      line([[-0.75, -0.45], [0.75, -0.45]]); line([[-0.75, 0.45], [0.75, 0.45]]);
      break;
    case 'key':
      c.beginPath(); c.arc(-0.45 * u, 0, 0.38 * u, 0, TAU); c.stroke();
      line([[-0.07, 0], [0.9, 0], [0.9, 0.3]]); line([[0.6, 0], [0.6, 0.25]]);
      break;
    case 'swap':
      line([[-0.7, -0.3], [0.7, -0.3], [0.35, -0.65]]);
      line([[0.7, 0.3], [-0.7, 0.3], [-0.35, 0.65]]);
      break;
    case 'star':
      { const pts = []; for (let k = 0; k < 10; k++) { const a = -Math.PI / 2 + k * Math.PI / 5, rr2 = k % 2 ? 0.4 : 0.92; pts.push([Math.cos(a) * rr2, Math.sin(a) * rr2]); } line(pts, true); }
      break;
    case 'play':
      line([[-0.4, -0.65], [0.65, 0], [-0.4, 0.65]], true);
      break;
    case 'arrowDown':
      line([[0, -0.85], [0, 0.8]]); line([[-0.55, 0.25], [0, 0.82], [0.55, 0.25]]);
      break;
    default: break;
  }
  c.restore();
}

// ---------------------------------------------------------------- UI widgets
function panel(c, x, y, w, h, o = {}) {
  c.save();
  rr(c, x - w / 2, y - h / 2, w, h, o.r ?? 22);
  const g = c.createLinearGradient(0, y - h / 2, 0, y + h / 2);
  g.addColorStop(0, o.top || 'rgba(255,255,255,0.92)');
  g.addColorStop(1, o.bot || 'rgba(246,242,255,0.88)');
  c.fillStyle = g;
  if (!o.noShadow) { c.shadowColor = 'rgba(90,60,170,0.22)'; c.shadowBlur = 40; c.shadowOffsetY = 14; }
  c.fill();
  c.shadowColor = 'rgba(0,0,0,0)';
  c.lineWidth = o.lw || 2;
  c.strokeStyle = o.border || 'rgba(123,92,255,0.22)';
  c.stroke();
  c.restore();
}
function button(c, x, y, w, h, label, o = {}) {
  c.save();
  c.translate(x, y);
  const sc = o.scale ?? 1;
  c.scale(sc, sc);
  rr(c, -w / 2, -h / 2, w, h, o.r ?? h / 2);
  let fill = o.fill;
  if (!fill) {
    const g = c.createLinearGradient(-w / 2, 0, w / 2, 0);
    g.addColorStop(0, C.violet); g.addColorStop(1, '#3d8bff');
    fill = g;
  }
  c.fillStyle = fill;
  c.fill();
  if (o.hover) {
    c.fillStyle = 'rgba(255,255,255,0.1)';
    c.fill();
  }
  c.lineWidth = o.hover ? 4 : 2; c.strokeStyle = o.hover ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.25)'; c.stroke();
  text(c, label, 0, 1, { size: o.size || 30, weight: 700, color: o.color || C.white });
  c.restore();
}
function spinner(c, x, y, r, t) {
  c.save();
  c.lineWidth = 5; c.lineCap = 'round';
  c.strokeStyle = 'rgba(255,255,255,0.2)';
  c.beginPath(); c.arc(x, y, r, 0, TAU); c.stroke();
  c.strokeStyle = '#fff';
  c.beginPath(); c.arc(x, y, r, t * 9, t * 9 + 1.8); c.stroke();
  c.restore();
}
function windowFrame(c, w, h, title, o = {}) {
  panel(c, 0, 0, w, h, { border: o.danger ? `rgba(255,46,91,${0.5 + 0.5 * o.danger})` : 'rgba(123,92,255,0.25)', lw: o.danger ? 3 : 2 });
  c.save();
  rr(c, -w / 2, -h / 2, w, 58, [22, 22, 0, 0]);
  c.fillStyle = 'rgba(123,92,255,0.07)';
  c.fill();
  ['#ff5f57', '#febc2e', '#28c840'].forEach((col, k) => {
    c.fillStyle = col;
    c.beginPath(); c.arc(-w / 2 + 30 + k * 26, -h / 2 + 29, 8, 0, TAU); c.fill();
  });
  text(c, title, 0, -h / 2 + 30, { size: 24, weight: 700, color: C.ink });
  c.restore();
}
