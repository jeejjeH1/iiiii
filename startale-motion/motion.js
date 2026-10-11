'use strict';
// Deterministic canvas motion graphics: frame(t) depends only on t, so frames can be
// rendered out of order / in parallel and still match the audio exactly.
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

// ---------------------------------------------------------------- cursor
function cursorAt(t) {
  const K = CUR.POS;
  let x = K[0].x, y = K[0].y, hand = false;
  if (t >= K[K.length - 1].t) { const k = K[K.length - 1]; x = k.x; y = k.y; hand = !!k.hand; }
  else if (t > K[0].t) {
    let i = 0;
    while (i < K.length - 2 && K[i + 1].t <= t) i++;
    const a = K[i], b = K[i + 1];
    const u = (t - a.t) / Math.max(1e-6, b.t - a.t);
    const e = E.inOut(u);
    x = lerp(a.x, b.x, e); y = lerp(a.y, b.y, e);
    // natural hand arc
    const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy);
    if (d > 1) { const arc = Math.sin(Math.PI * e) * d * 0.09; x += (-dy / d) * arc; y += (dx / d) * arc; }
    hand = !!(u > 0.75 ? b.hand : a.hand);
  }
  // alpha
  const A = CUR.ALPHA;
  let al = 0;
  if (t <= A[0].t) al = A[0].a;
  else if (t >= A[A.length - 1].t) al = A[A.length - 1].a;
  else {
    let i = 0;
    while (i < A.length - 2 && A[i + 1].t <= t) i++;
    const a = A[i], b = A[i + 1];
    al = lerp(a.a, b.a, clamp((t - a.t) / Math.max(1e-6, b.t - a.t)));
  }
  let press = 0;
  for (const k of K) if (k.click) { const d = t - k.t; if (d > -0.07 && d < 0.12) press = Math.max(press, 1 - Math.abs(d) / 0.12); }
  return { x, y, a: al, hand, press };
}
const CLICKS = CUR.POS.filter((k) => k.click);
function drawCursorShape(c, hand) {
  if (!hand) {
    const p = [[0, 0], [0, 24], [5.6, 18.6], [9.6, 27.6], [13.2, 26], [9.4, 17.2], [17, 17.2]];
    c.beginPath(); p.forEach(([px, py], k) => (k ? c.lineTo(px, py) : c.moveTo(px, py))); c.closePath();
    c.lineJoin = 'round';
    c.lineWidth = 2.6; c.strokeStyle = '#000'; c.stroke();
    c.fillStyle = '#fff'; c.fill();
  } else {
    const parts = (f) => {
      c.save(); c.translate(-1.5, 0);
      f(-2.6, 0, 5.2, 15, 2.6);
      f(2.6, 7.2, 4.4, 10, 2.2);
      f(7, 8.4, 4.2, 9, 2.1);
      f(11.2, 10, 3.8, 8, 1.9);
      f(-2.6, 12, 17.6, 12.5, 4.5);
      c.restore();
      c.save(); c.translate(-1.5, 0); c.translate(-2.4, 17); c.rotate(-0.75); f(-9, -2.3, 10, 4.6, 2.3); c.restore();
    };
    c.lineWidth = 3.4; c.strokeStyle = '#000';
    parts((x, y, w, h, r) => { rr(c, x, y, w, h, r); c.stroke(); });
    c.fillStyle = '#fff';
    parts((x, y, w, h, r) => { rr(c, x, y, w, h, r); c.fill(); });
  }
}
function drawCursor(c, t) {
  const cu = cursorAt(t);
  // click ripples
  for (const k of CLICKS) {
    const d = t - k.t;
    if (d < 0 || d > 0.6) continue;
    const p = d / 0.6, e = E.outCubic(p);
    c.save();
    c.strokeStyle = `rgba(11,179,207,${(1 - p) * 0.95})`;
    c.lineWidth = 5 * (1 - p) + 1;
    c.beginPath(); c.arc(k.x, k.y, 10 + e * 70, 0, TAU); c.stroke();
    c.strokeStyle = `rgba(255,61,154,${(1 - p) * 0.8})`;
    c.beginPath(); c.arc(k.x, k.y, 6 + e * 42, 0, TAU); c.stroke();
    c.fillStyle = `rgba(255,255,255,${Math.max(0, 0.5 - p * 2)})`;
    c.beginPath(); c.arc(k.x, k.y, 22 * (1 - p), 0, TAU); c.fill();
    for (let s = 0; s < 8; s++) {
      const a = s * TAU / 8 + 0.3;
      c.strokeStyle = `rgba(123,92,255,${(1 - p) * 0.9})`;
      c.lineWidth = 3;
      c.beginPath();
      c.moveTo(k.x + Math.cos(a) * (18 + e * 40), k.y + Math.sin(a) * (18 + e * 40));
      c.lineTo(k.x + Math.cos(a) * (28 + e * 58), k.y + Math.sin(a) * (28 + e * 58));
      c.stroke();
    }
    c.restore();
  }
  if (cu.a <= 0.01) return;
  // motion trail
  for (let k = 4; k >= 1; k--) {
    const pc = cursorAt(t - k * 0.014);
    const dd = Math.hypot(pc.x - cu.x, pc.y - cu.y);
    if (dd < 6) continue;
    c.save();
    c.globalAlpha = cu.a * 0.16 * (5 - k) / 4;
    c.translate(pc.x, pc.y); c.scale(2.3, 2.3);
    drawCursorShape(c, cu.hand);
    c.restore();
  }
  c.save();
  c.globalAlpha = cu.a;
  c.translate(cu.x, cu.y);
  const sc = 2.3 * (1 - cu.press * 0.14);
  c.scale(sc, sc);
  c.shadowColor = 'rgba(0,0,0,0.55)'; c.shadowBlur = 10; c.shadowOffsetY = 3;
  drawCursorShape(c, cu.hand);
  c.restore();
}

// =================================================================== SCENES
function sceneOut(t, tEnd, d = 0.14) { return 1 - E.inCubic(P(t, tEnd - d, d)); }

// ---- 0 INTRO
function sIntro(c, t) {
  bgBase(c, `rgba(70,35,160,${0.25 + 0.2 * P(t, T(1), 2)})`);
  dust(c, t, 110, 1, 0.55);
  const t1 = T(1);
  if (t < t1) {
    // boot: dotted target reticle
    const p = P(t, 0.4, 1.2);
    c.save();
    c.globalAlpha = p * 0.5;
    c.strokeStyle = C.cyan; c.lineWidth = 2; c.setLineDash([6, 10]);
    c.beginPath(); c.arc(960, 540, 120 + Math.sin(t * 6) * 6, 0, TAU); c.stroke();
    c.setLineDash([]);
    c.restore();
    scramble(c, 'INITIALIZING', 960, 760, { size: 26, weight: 600, ls: 12, color: C.dim }, t, 0.5, 0.8);
  }
  ring(c, 960, 540, t, t1, 1.1, 1100, '11,179,207', 8);
  ring(c, 960, 540, t, t1 + 0.07, 1.3, 1400, '138,92,255', 4);
  ring(c, 960, 540, t, t1 + 0.14, 1.5, 1600, '255,61,154', 3);

  const R = 150;
  const zP = E.inExpo(P(t, T(3, 2), T(4) - T(3, 2)));
  const zs = 1 + zP * 26;
  if (t > T(3)) rays(c, 960, 540, t, { a: 0.16 * P(t, T(3), 0.3), n: 16 });
  warp(c, t, P(t, T(3, 1), BAR * 0.6) * 1.2, 11);
  c.save();
  c.translate(960, 540);
  c.scale(zs, zs);
  c.rotate(zP * 1.2 + E.outBack(P(t, T(3), 0.5)) * Math.PI / 4 * 0 + E.inOut(P(t, T(3), 0.6)) * Math.PI / 2);
  const cols = [C.cyan, C.violet, C.pink, C.cyan, C.violet, C.pink, C.cyan, C.violet];
  logo(c, 0, 0, R, {
    sq: (i, a, m) => {
      const li = T(1, 1 + i), fl = 0.42;
      if (t < li - fl) return { a: 0 };
      const q = E.outExpo(P(t, li - fl, fl));
      const dir = a + (i % 2 ? 0.6 : -0.6);
      const d0 = 1500;
      const sx0 = Math.cos(dir) * d0, sy0 = Math.sin(dir) * d0;
      const done = P(t, T(3), 0.25);
      const landed = t >= li;
      const fl2 = landed ? Math.max(0, 1 - (t - li) / 0.25) : 0;
      const col = done > 0 ? C.ink : landed ? cols[i] : C.ink;
      return {
        x: lerp(sx0, m.x, q), y: lerp(sy0, m.y, q),
        rot: m.rot + (1 - q) * 6 * (i % 2 ? 1 : -1),
        s: 1 + (1 - q) * 1.5 + beatPulse(t) * 0.06 * (t > T(3) ? 1 : 0),
        a: clamp((t - (li - fl)) / 0.08), col, flash: Math.max(fl2, done < 1 && done > 0 ? 1 - done : 0),
      };
    },
  });
  c.restore();
  // landing mini-rings
  for (let i = 0; i < 8; i++) {
    const li = T(1, 1 + i);
    const p = logoSquarePos(i, R);
    ring(c, 960 + p.x, 540 + p.y, t, li, 0.5, 120, '123,92,255', 4);
  }
  // completion text
  if (t > T(3) && zP < 0.4) {
    c.save();
    c.globalAlpha = 1 - zP * 2.5;
    scramble(c, 'STARTALE', 960, 800, { size: 54, weight: 900, ls: 26 }, t, T(3), 0.5);
    c.restore();
  }
  // white-out into drop
  const wo = P(t, T(4) - 0.18, 0.18);
  if (wo > 0) { c.fillStyle = `rgba(255,255,255,${wo})`; c.fillRect(0, 0, W, H); }
}

// ---- 1 HOOK
function sHook(c, t) {
  const fast = t >= T(4, 2) && t < T(5);
  bgBase(c, fast ? 'rgba(20,120,200,0.45)' : 'rgba(40,40,160,0.4)');
  warp(c, t, 0.35 + Math.exp(-(t - T(4)) * 2) * 1.2 + (fast ? 1.0 : 0), 3);
  dust(c, t, 60, 2, 0.4);
  const o1 = { size: 168, weight: 900, italic: true };
  // phase 1
  if (t < T(5)) {
    const out = T(5) - 0.16;
    slam(c, 'BLOCKCHAIN', 960, 330, o1, t, T(4), { out, outDir: 'left' });
    slam(c, 'EVOLVED', 960, 520, o1, t, T(4, 1), { out, outDir: 'left' });
    if (t >= T(4, 2)) {
      // speed lines behind FAST
      c.save();
      for (let i = 0; i < 26; i++) {
        const y = 640 + rnd(i, 4) * 170, len = 200 + rnd(i, 5) * 700;
        const x = ((rnd(i, 6) * 2400 - (t - T(4, 2)) * (2600 + rnd(i, 7) * 2000)) % 2400 + 2400) % 2400 - 240;
        c.fillStyle = `rgba(${i % 2 ? '11,179,207' : '123,92,255'},${0.25 + rnd(i, 8) * 0.5})`;
        c.fillRect(x, y, len, 2 + rnd(i, 9) * 4);
      }
      c.restore();
    }
    slam(c, 'FAST.', 960, 730, { ...o1, size: 220, gradient: G_COOL }, t, T(4, 2), { out, outDir: 'left', ca: 0 });
  } else if (t < T(6)) {
    const out = T(6) - 0.16;
    slam(c, 'SO WHY IS', 960, 400, { size: 80, weight: 700, ls: 18, color: C.dim }, t, T(5), { out, outDir: 'left' });
    slam(c, 'CRYPTO ADOPTION', 960, 560, { size: 170, weight: 900, gradient: G_BRAND }, t, T(5, 1), { out, outDir: 'left' });
    rise(c, 'still struggling?', 960, 720, { size: 64, weight: 500, color: C.ink, italic: true }, t, T(5, 2), { out });
  } else {
    const out = T(7) - 0.16;
    const o = { size: 132, weight: 900 };
    slam(c, 'STILL', 960, 250, o, t, T(6, 0), { out, outDir: 'up' });
    slam(c, 'FACING', 960, 400, o, t, T(6, 1), { out, outDir: 'up' });
    slam(c, 'MAJOR', 960, 550, o, t, T(6, 2), { out, outDir: 'up' });
    if (t >= T(6, 3)) {
      const g = Math.max(0, 1 - (t - T(6, 3)) * 2.2);
      c.save();
      if (g > 0.05) c.translate((rnd(1, Math.floor(t * 30)) - 0.5) * 40 * g, 0);
      slam(c, 'CHALLENGES?', 960, 735, { ...o, size: 170, color: C.red }, t, T(6, 3), { out, outDir: 'up', ca: 10 * g + 3 });
      c.restore();
    }
  }
}

// ---- 2 COMPLEXITY
function tape(c, y, rot, t, dir, label, al) {
  c.save();
  c.globalAlpha = al;
  c.translate(960, y);
  c.rotate(rot);
  c.fillStyle = C.yellow;
  c.fillRect(-1400, -40, 2800, 80);
  c.save();
  c.beginPath(); c.rect(-1400, -40, 2800, 80); c.clip();
  c.fillStyle = '#111';
  const off = (t * 260 * dir) % 80;
  for (let x = -1500; x < 1500; x += 80) {
    c.beginPath();
    c.moveTo(x + off, -40); c.lineTo(x + off + 30, -40); c.lineTo(x + off - 10, 40); c.lineTo(x + off - 40, 40);
    c.closePath(); c.fill();
  }
  c.fillStyle = C.yellow;
  c.fillRect(-1400, -24, 2800, 48);
  c.restore();
  setF(c, { size: 30, weight: 900, ls: 8 });
  c.fillStyle = '#111'; c.textBaseline = 'middle'; c.textAlign = 'left';
  const step = 620, toff = ((t * 180 * dir) % step + step) % step;
  for (let x = -1400 - step; x < 1400; x += step) c.fillText(label, x + toff, 2);
  c.restore();
}
function sComplex(c, t) {
  const red = Math.exp(-Math.max(0, t - T(7, 2)) * 1.5);
  bgBase(c, `rgba(${t > T(7, 2) ? '180,30,60' : '40,40,140'},${0.35 + red * 0.25})`);
  dust(c, t, 60, 4, 0.4);
  const out = T(9) - 0.14;
  scramble(c, 'ONE OF THE BIGGEST REASONS', 960, 170, { size: 44, weight: 700, ls: 12, color: C.dim }, t, T(7), 0.7, { out });
  if (t >= T(7, 2)) {
    const tp = E.outExpo(P(t, T(7, 2), 0.45));
    const oa = sceneOut(t, T(9));
    tape(c, 330 + (1 - tp) * -500, -0.07, t, 1, 'WARNING  ⚠  TOO COMPLEX  ⚠', oa);
    tape(c, 850 + (1 - tp) * 500, 0.06, t, -1, 'NEWCOMERS  ✕  BLOCKED  ✕', oa);
    const g = Math.max(0, 1 - (t - T(7, 2)) * 3);
    slam(c, 'COMPLEXITY', 960, 545, { size: 250, weight: 900 }, t, T(7, 2), { out, ca: 4 + g * 14 + 6 * beatPulse(t, 10), dur: 0.42, from: 3 });
  }
  typeOn(c, 'OF THE WEB3 USER EXPERIENCE', 960, 700, { size: 54, weight: 800, ls: 6, color: C.cyan }, t, T(8), 0.85, { out });
}

// ---- 3 NIGHTMARE
const SEED_WORDS = ['orbit', 'maple', 'verify', 'canyon', 'silent', 'rocket', 'glide', 'fossil', 'humble', 'ivory', 'pulse', 'zebra'];
const NETS = [['Ethereum', '#8a9cff', '1'], ['Arbitrum', '#2d9cff', '42161'], ['Optimism', '#ff4d4d', '10'], ['Base', '#2f6bff', '8453'], ['Polygon', '#9b5cff', '137'], ['BNB Chain', '#f0b90b', '56'], ['Soneium', '#17132e', '1868'], ['Avalanche', '#ff5050', '43114']];
function stepWindow(c, i, t) {
  const t0 = T(10 + i);
  if (t < t0) return;
  const w = L.win[i], WW = L.winW, WH = L.winH;
  const p = P(t, t0, 0.35);
  const s = 0.6 + 0.4 * E.outBack2(p);
  const isTop = t < T(11 + i) || i === 4;
  const dimA = isTop ? 1 : 0.55;
  c.save();
  c.translate(w.x, w.y);
  c.scale(s, s);
  c.globalAlpha *= clamp((t - t0) / 0.08) * dimA;
  // shake window on errors
  let ex = 0;
  for (const et of [T(12, 3) + 0.08, T(13, 2) + 0.06, T(13, 3) + 0.06]) {
    const d = t - et;
    if (d > 0 && d < 0.35 && ((i === 2 && et < T(13)) || (i === 3 && et > T(13)))) ex += Math.sin(d * 90) * 16 * (1 - d / 0.35);
  }
  c.translate(ex, 0);
  const danger = i === 3 && t > T(13, 2) ? 1 : i === 4 ? 0.5 + 0.5 * Math.sin(t * 12) : 0;
  const titles = ['Create a wallet', 'Back up your recovery phrase', 'Select the right network', 'Insufficient gas', 'Signature request'];
  windowFrame(c, WW, WH, titles[i], { danger });
  // step badge
  c.save();
  rr(c, WW / 2 - 168, -WH / 2 + 11, 150, 36, 18);
  c.fillStyle = 'rgba(138,92,255,0.45)'; c.fill();
  text(c, `STEP ${i + 1} / 5`, WW / 2 - 93, -WH / 2 + 30, { size: 19, weight: 800, ls: 3, color: '#e4dbff' });
  c.restore();
  const cu = cursorAt(t);
  if (i === 0) {
    const fields = [['Wallet name', 'my_first_wallet', false], ['Password', '••••••••••', true], ['Confirm password', '••••••••••', true]];
    fields.forEach(([lab, val], k) => {
      const fy = -95 + k * 82;
      text(c, lab, -300, fy - 22, { size: 20, weight: 600, align: 'left', color: 'rgba(23,19,46,0.55)' });
      rr(c, -300, fy - 6, 600, 46, 10); c.fillStyle = 'rgba(123,92,255,0.06)'; c.fill();
      c.strokeStyle = 'rgba(23,19,46,0.18)'; c.lineWidth = 1.5; c.stroke();
      const ts = k === 0 ? T(10, 0.6) : T(10, 1.5) + (k - 1) * 0.15;
      const n = Math.floor(P(t, ts, k === 0 ? 0.6 : 0.3) * val.length);
      text(c, val.slice(0, n), -284, fy + 17, { size: 24, weight: 500, align: 'left', fam: k === 0 ? FONT : MONO });
    });
    const clicked = t > T(10, 3);
    const hov = Math.abs(cu.x - w.x) < 160 && Math.abs(cu.y - (w.y + 175)) < 35;
    button(c, 0, 175, 320, 66, clicked ? '' : 'Create wallet', { hover: hov, scale: 1 - cursorAt(t).press * 0.05 * (hov ? 1 : 0) });
    if (clicked) spinner(c, 0, 175, 16, t);
  } else if (i === 1) {
    text(c, 'Write these 12 words down. Never share them.', 0, -140, { size: 22, weight: 600, color: C.red });
    SEED_WORDS.forEach((wd, k) => {
      const cx = -270 + (k % 4) * 180, cy = -70 + Math.floor(k / 4) * 62;
      rr(c, cx - 80, cy - 23, 160, 46, 10); c.fillStyle = 'rgba(123,92,255,0.09)'; c.fill();
      text(c, String(k + 1), cx - 60, cy + 1, { size: 16, weight: 700, color: 'rgba(23,19,46,0.4)' });
      scramble(c, wd, cx + 10, cy + 1, { size: 22, weight: 600, fam: MONO, scol: C.pink }, t, T(11, 0.3) + k * 0.05, 0.6);
    });
    text(c, '⚠  Lose it = lose everything', 0, 125, { size: 22, weight: 700, color: C.orange });
    const hov = Math.abs(cu.x - w.x) < 160 && Math.abs(cu.y - (w.y + 190)) < 35;
    button(c, 0, 190, 300, 62, "I've saved it", { hover: hov });
  } else if (i === 2) {
    NETS.forEach(([nm, col, id], k) => {
      const ry = -150 + k * 44;
      const hov = t < T(13) && Math.abs(cu.y - (w.y + ry)) < 22 && Math.abs(cu.x - w.x) < 340;
      if (hov) { rr(c, -330, ry - 20, 660, 40, 8); c.fillStyle = 'rgba(138,92,255,0.35)'; c.fill(); }
      c.fillStyle = col; c.beginPath(); c.arc(-300, ry, 11, 0, TAU); c.fill();
      text(c, nm, -275, ry + 1, { size: 23, weight: 600, align: 'left' });
      text(c, 'Chain ID ' + id, 320, ry + 1, { size: 17, weight: 500, align: 'right', fam: MONO, color: 'rgba(23,19,46,0.45)' });
    });
    if (t > T(12, 3) + 0.08) {
      const p2 = E.outBack(P(t, T(12, 3) + 0.08, 0.3));
      c.save(); c.translate(0, 215); c.scale(p2, p2);
      rr(c, -300, -28, 600, 56, 12); c.fillStyle = 'rgba(255,51,85,0.95)'; c.fill();
      text(c, '✕  Wrong network — switch required', 0, 1, { size: 22, weight: 800 });
      c.restore();
    }
  } else if (i === 3) {
    // gas gauge
    c.save();
    c.translate(0, -30);
    c.lineWidth = 22; c.lineCap = 'round';
    c.strokeStyle = 'rgba(23,19,46,0.1)';
    c.beginPath(); c.arc(0, 0, 120, Math.PI, TAU); c.stroke();
    c.strokeStyle = C.red;
    c.beginPath(); c.arc(0, 0, 120, Math.PI, Math.PI + 0.12); c.stroke();
    const na = Math.PI + 0.1 + Math.sin(t * 20) * 0.03;
    c.strokeStyle = C.ink; c.lineWidth = 6;
    c.beginPath(); c.moveTo(0, 0); c.lineTo(Math.cos(na) * 95, Math.sin(na) * 95); c.stroke();
    c.fillStyle = C.ink; c.beginPath(); c.arc(0, 0, 10, 0, TAU); c.fill();
    text(c, 'E', -150, 20, { size: 26, weight: 900, color: C.red });
    text(c, 'F', 150, 20, { size: 26, weight: 900, color: 'rgba(23,19,46,0.4)' });
    icon(c, 'gas', 0, -50, 44, C.ink);
    c.restore();
    text(c, 'You need ETH to pay network fees', 0, 60, { size: 26, weight: 700 });
    text(c, 'Balance: 0.0000 ETH', 0, 100, { size: 22, weight: 600, fam: MONO, color: C.red });
    const err = t > T(13, 2) + 0.06;
    const hov = Math.abs(cu.x - w.x) < 150 && Math.abs(cu.y - (w.y + 175)) < 35;
    button(c, 0, 175, 280, 64, err ? 'Transaction failed' : 'Confirm', { hover: hov, fill: err ? C.red : null, size: err ? 24 : 30 });
  } else if (i === 4) {
    text(c, 'unknown-site.xyz wants your signature:', 0, -150, { size: 22, weight: 600, color: C.dim });
    rr(c, -320, -120, 640, 168, 12); c.fillStyle = '#1b1733'; c.fill();
    for (let l = 0; l < 6; l++) {
      let hx = '';
      for (let k = 0; k < 40; k++) hx += '0123456789abcdef'[Math.floor(rnd(l * 50 + k, 9) * 16)];
      text(c, (l === 0 ? '0x' : '  ') + hx, -300, -98 + l * 26, { size: 17, weight: 500, fam: MONO, align: 'left', color: 'rgba(160,255,200,0.75)' });
    }
    const pul = 0.75 + 0.25 * Math.sin(t * 14);
    c.save(); c.globalAlpha *= pul;
    text(c, '⚠  Is this transaction malicious?', 0, 88, { size: 26, weight: 800, color: C.red });
    c.restore();
    const hovS = Math.abs(cu.x - (w.x + 150)) < 130 && Math.abs(cu.y - (w.y + 175)) < 35;
    const hovR = Math.abs(cu.x - (w.x - 150)) < 130 && Math.abs(cu.y - (w.y + 175)) < 35;
    button(c, -150, 175, 250, 64, 'Reject', { fill: '#8b86a3', hover: hovR });
    button(c, 150, 175, 250, 64, 'Sign', { hover: hovS });
  }
  c.restore();
}
function sNightmare(c, t) {
  const chaos = P(t, T(15), T(16, 2) - T(15));
  const step = clamp(Math.floor((t - T(10)) / BAR) + 1, 0, 5);
  bgBase(c, `rgba(${Math.round(lerp(50, 200, chaos))},${Math.round(lerp(40, 20, chaos))},${Math.round(lerp(140, 50, chaos))},${0.35 + chaos * 0.25})`);
  // desktop dot grid
  c.save();
  c.fillStyle = 'rgba(23,19,46,0.08)';
  for (let y = 40; y < H; y += 48) for (let x = 40; x < W; x += 48) c.fillRect(x, y, 2, 2);
  c.restore();
  const out = T(17) - 0.14;
  // intro: one simple thing
  if (t < T(10) + 0.4) {
    const fade = 1 - P(t, T(10), 0.3);
    c.save();
    c.globalAlpha = fade;
    typeOn(c, 'Imagine you just want to do', 960, 330, { size: 62, weight: 700 }, t, T(9), 0.6, { caret: false });
    rise(c, 'ONE simple thing.', 960, 420, { size: 74, weight: 900, gradient: G_COOL }, t, T(9) + 0.55);
    if (t > T(9, 1.5)) {
      const p = E.outBack2(P(t, T(9, 1.5), 0.35));
      const cu = cursorAt(t);
      const hov = Math.abs(cu.x - L.send.x) < L.send.w / 2 && Math.abs(cu.y - L.send.y) < L.send.h / 2;
      button(c, L.send.x, L.send.y, L.send.w, L.send.h, 'Send $10  →', { scale: p * (1 - cu.press * 0.06), size: 44, hover: hov });
    }
    c.restore();
  }
  // HUD
  if (t > T(10)) {
    const hp = E.outCubic(P(t, T(10), 0.3)) * sceneOut(t, T(16, 2), 0.1);
    c.save();
    c.globalAlpha = hp;
    text(c, 'GOAL: SEND $10', 70, 70, { size: 26, weight: 800, ls: 6, align: 'left', color: C.dim });
    c.strokeStyle = C.red; c.lineWidth = 4;
    if (t > T(15)) { const sp = E.outCubic(P(t, T(15), 0.3)); c.beginPath(); c.moveTo(66, 72); c.lineTo(66 + 300 * sp, 72); c.stroke(); }
    let n = step;
    if (t > T(15)) n = 5 + L.chaos.filter((p) => t >= p.t).length;
    text(c, 'STEPS', 1850, 60, { size: 22, weight: 800, ls: 8, align: 'right', color: C.dim });
    const bump = Math.exp(-((t - T(10)) % BAR) * 8) * (t < T(15) ? 1 : 0);
    text(c, String(n), 1850, 135, { size: 110 * (1 + bump * 0.25), weight: 900, align: 'right', color: n > 5 ? C.red : C.ink });
    c.restore();
  }
  for (let i = 0; i < 5; i++) stepWindow(c, i, t);
  // chaos popups
  L.chaos.forEach((p, j) => {
    if (t < p.t) return;
    const q = E.outBack2(P(t, p.t, 0.25));
    c.save();
    c.translate(p.x, p.y);
    c.scale(q, q);
    c.rotate((rnd(j, 33) - 0.5) * 0.12);
    panel(c, 0, 0, 440, 140, { border: 'rgba(255,46,91,0.9)', lw: 3, top: '#ffffff', bot: '#fff0f4', r: 16 });
    rr(c, -220, -70, 440, 40, [16, 16, 0, 0]); c.fillStyle = 'rgba(255,51,85,0.9)'; c.fill();
    text(c, 'ERROR', -200, -50, { size: 18, weight: 900, ls: 4, align: 'left', color: C.white });
    text(c, '✕', 196, -50, { size: 20, weight: 900, color: C.white });
    icon(c, 'warn', -170, 20, 40, C.orange);
    text(c, p.msg, -130, 20, { size: 25, weight: 700, align: 'left' });
    c.restore();
  });
  // red alarm vignette
  if (chaos > 0) {
    const pul = 0.5 + 0.5 * Math.sin(t * 16);
    const g = c.createRadialGradient(960, 540, 300, 960, 540, 1100);
    g.addColorStop(0, 'rgba(255,0,40,0)');
    g.addColorStop(1, `rgba(255,0,40,${chaos * (0.35 + 0.25 * pul)})`);
    c.fillStyle = g; c.fillRect(0, 0, W, H);
  }
  // FREEZE
  if (t >= T(16, 2)) {
    const f = E.outCubic(P(t, T(16, 2), 0.25));
    c.save();
    c.globalCompositeOperation = 'saturation';
    c.fillStyle = `rgba(128,128,128,${f})`;
    c.fillRect(0, 0, W, H);
    c.restore();
    c.fillStyle = `rgba(248,245,255,${0.78 * f})`;
    c.fillRect(0, 0, W, H);
    slam(c, '...JUST TO COMPLETE A', 960, 420, { size: 74, weight: 800, ls: 4, color: C.dim }, t, T(16, 2), { out });
    slam(c, 'SIMPLE', 960, 560, { size: 200, weight: 900 }, t, T(16, 3), { out, from: 3 });
    slam(c, 'OPERATION.', 960, 720, { size: 130, weight: 900, gradient: G_BRAND }, t, T(16, 3) + 0.12, { out });
  }
}

// ---- 4 CONTRAST
function stickFigure(c, x, y, s, col) {
  c.save();
  c.translate(x, y); c.scale(s, s);
  c.strokeStyle = col; c.lineWidth = 5; c.lineCap = 'round';
  c.beginPath(); c.arc(0, -95, 16, 0, TAU); c.stroke();
  c.beginPath(); c.moveTo(0, -78); c.lineTo(0, -30); c.lineTo(-18, 0); c.moveTo(0, -30); c.lineTo(18, 0);
  c.moveTo(0, -65); c.lineTo(-20, -40); c.moveTo(0, -65); c.lineTo(22, -78); c.stroke();
  c.restore();
}
function sContrast(c, t) {
  bgBase(c, 'rgba(255,255,255,0)');
  const out = T(19) - 0.14;
  const oa = sceneOut(t, T(19));
  const sp = E.outExpo(P(t, T(17), 0.4));
  // halves
  let g = c.createRadialGradient(480, 540, 0, 480, 540, 800);
  g.addColorStop(0, `rgba(120,230,210,${0.45 * sp})`); g.addColorStop(1, 'rgba(120,230,210,0)');
  c.fillStyle = g; c.fillRect(0, 0, 960, H);
  const rp = P(t, T(17, 2), 0.3);
  g = c.createRadialGradient(1440, 540, 0, 1440, 540, 800);
  g.addColorStop(0, `rgba(255,120,150,${0.4 * rp})`); g.addColorStop(1, 'rgba(255,120,150,0)');
  c.fillStyle = g; c.fillRect(960, 0, 960, H);
  // divider
  c.save();
  c.globalAlpha = oa;
  const dg = c.createLinearGradient(0, 0, 0, H);
  dg.addColorStop(0, 'rgba(23,19,46,0)'); dg.addColorStop(0.5, 'rgba(23,19,46,0.6)'); dg.addColorStop(1, 'rgba(23,19,46,0)');
  c.fillStyle = dg;
  c.fillRect(958, H / 2 - sp * H / 2, 4, sp * H);
  c.restore();
  // left
  slam(c, 'CRYPTO VETERANS', 480, 250, { size: 40, weight: 800, ls: 10, color: C.cyan }, t, T(17), { out, dur: 0.3, from: 1.6 });
  rise(c, '"Feels normal."', 480, 380, { size: 92, weight: 900, italic: true }, t, T(17, 0.5), { out });
  if (t > T(17, 1)) {
    const cp = E.outCubic(P(t, T(17, 1), 0.45));
    c.save();
    c.globalAlpha = oa;
    c.strokeStyle = C.green; c.lineWidth = 12; c.lineCap = 'round';
    c.beginPath(); c.arc(480, 640, 120, -Math.PI / 2, -Math.PI / 2 + TAU * cp); c.stroke();
    const kp = P(t, T(17, 1) + 0.2, 0.3);
    if (kp > 0) {
      c.beginPath();
      const pts = [[420, 640], [465, 690], [550, 595]];
      c.moveTo(...pts[0]);
      if (kp < 0.4) c.lineTo(lerp(420, 465, kp / 0.4), lerp(640, 690, kp / 0.4));
      else { c.lineTo(...pts[1]); const k2 = (kp - 0.4) / 0.6; c.lineTo(lerp(465, 550, k2), lerp(690, 595, k2)); }
      c.stroke();
    }
    c.restore();
    rise(c, 'Wallets, seed phrases, gas, networks...', 480, 840, { size: 30, weight: 500, color: C.dim }, t, T(17, 1.5), { out, stagger: 0.012 });
  }
  // right
  slam(c, 'NEWCOMERS', 1440, 250, { size: 40, weight: 800, ls: 10, color: C.red }, t, T(17, 2), { out, dur: 0.3, from: 1.6 });
  if (t > T(17, 2)) {
    c.save();
    c.globalAlpha = oa;
    stickFigure(c, 1060, 980, 1.25, C.ink);
    if (t > T(18, 2)) { text(c, '?', 1090, 830, { size: 60, weight: 900, color: C.red }); }
    c.restore();
  }
  const BR = [T(17, 3), T(18, 0), T(18, 0.5), T(18, 1), T(18, 1.5)];
  BR.forEach((bt, row) => {
    if (t < bt - 0.2) return;
    const q = P(t, bt - 0.2, 0.2);
    const by = 1000 - row * 82;
    const dy = (1 - E.inCubic(q)) * -900;
    const bounce = t > bt ? Math.sin((t - bt) * 30) * 10 * Math.exp(-(t - bt) * 12) : 0;
    c.save();
    c.globalAlpha = oa;
    for (let k = 0; k < 5; k++) {
      const bw = 168, x0 = 1150 + k * 172 - (row % 2 ? 86 : 0);
      const x1 = Math.max(1150, x0), x2 = Math.min(1150 + 5 * 172 - 4, x0 + bw);
      if (x2 <= x1) continue;
      rr(c, x1, by - 38 + dy + bounce, x2 - x1, 76, 6);
      const bg = c.createLinearGradient(0, by - 38, 0, by + 38);
      bg.addColorStop(0, '#ff8aa8'); bg.addColorStop(1, '#e84a74');
      c.fillStyle = bg; c.fill();
      c.strokeStyle = 'rgba(255,255,255,0.7)'; c.lineWidth = 2; c.stroke();
    }
    c.restore();
    // dust puff
    if (t > bt && t < bt + 0.5) {
      const dp = (t - bt) / 0.5;
      c.save(); c.globalAlpha = (1 - dp) * 0.6 * oa; c.fillStyle = '#e84a74';
      for (let k = 0; k < 14; k++) {
        const a = rnd(k, row) * Math.PI, sp2 = 60 + rnd(k, row + 9) * 160;
        c.fillRect(1150 + rnd(k, row + 3) * 860, by + 38 - Math.sin(a) * sp2 * dp, 5, 5);
      }
      c.restore();
    }
  });
  if (t > T(18, 2)) {
    const q = E.outExpo(P(t, T(18, 2), 0.35));
    c.save();
    c.globalAlpha = oa;
    c.translate(1440, 470);
    c.rotate(-0.04);
    c.scale(lerp(1.8, 1, q), lerp(1.8, 1, q));
    rr(c, -440, -70, 880, 140, 10);
    c.fillStyle = C.red; c.fill();
    text(c, 'BARRIER TO ENTRY', 0, 4, { size: 82, weight: 900, color: C.white });
    c.restore();
  }
}

// ---- 5 TRANSITION (orb)
function sTrans(c, t) {
  const p = P(t, T(19), BAR);
  bgBase(c, `rgba(90,40,200,${0.15 + p * 0.35})`, 960, 540, 900);
  // particles sucked into center
  c.save();
  for (let i = 0; i < 260; i++) {
    const a = rnd(i, 21) * TAU + t * 0.4;
    const ph = (rnd(i, 22) + t * (0.5 + rnd(i, 23) * 0.6) * (1 + p * 2)) % 1;
    const r = (1 - ph) * (500 + rnd(i, 24) * 700);
    const x = 960 + Math.cos(a) * r, y = 540 + Math.sin(a) * r;
    const x2 = 960 + Math.cos(a) * (r + 20 + p * 60), y2 = 540 + Math.sin(a) * (r + 20 + p * 60);
    c.strokeStyle = `rgba(${i % 3 === 0 ? '11,179,207' : i % 3 === 1 ? '138,92,255' : '255,61,154'},${ph * 0.9})`;
    c.lineWidth = 2;
    c.beginPath(); c.moveTo(x, y); c.lineTo(x2, y2); c.stroke();
  }
  // orb
  const orR = 30 + E.inCubic(p) * 70 + beatPulse(t, 9) * 10;
  const og = c.createRadialGradient(960, 540, 0, 960, 540, orR * 3);
  og.addColorStop(0, 'rgba(255,255,255,1)');
  og.addColorStop(0.25, 'rgba(160,140,255,0.9)');
  og.addColorStop(1, 'rgba(80,40,255,0)');
  c.fillStyle = og;
  c.beginPath(); c.arc(960, 540, orR * 3, 0, TAU); c.fill();
  c.restore();
  bubble(c, 960, 540, orR * 3.2, t * 3, 1);
  // tiny logo inside orb
  logo(c, 960, 540, orR * 0.45, { color: '#1a1030', rot: t * 2 });
  typeOn(c, 'This is where projects like...', 960, 830, { size: 60, weight: 700 }, t, T(19), 1.1);
}

// ---- 6 REVEAL
function sReveal(c, t) {
  bgBase(c, 'rgba(110,60,255,0.55)', 960, 470, 1100);
  rays(c, 960, 470, t, { a: 0.2, n: 20, speed: 0.25 });
  gridFloor(c, t, { hy: 760, a: 0.35, sp: 1.2 });
  dust(c, t, 120, 6, 0.7);
  const out = T(22) - 0.14;
  const oa = sceneOut(t, T(22));
  for (let k = 0; k < 8; k++) ring(c, 960, 470, t, T(20, k), 0.9, 700, k % 2 ? '11,179,207' : '255,61,154', 5);
  ring(c, 960, 470, t, T(20), 1.2, 1600, '123,92,255', 14);
  c.save();
  c.globalAlpha = oa;
  const bp = beatPulse(t, 7);
  logo(c, 960, 470, 170, {
    scale: (1 + bp * 0.05) * (1 + (1 - oa) * 0.4),
    rot: (1 - E.outExpo(P(t, T(20), 0.9))) * -Math.PI,
    sq: (i, a, m) => {
      const q = E.outBack2(P(t, T(20) + i * 0.025, 0.55));
      return { x: m.x * q, y: m.y * q, rot: m.rot + (1 - q) * 4, s: 0.3 + 0.7 * q, flash: Math.max(0, 1 - (t - T(20)) * 3) };
    },
  });
  c.restore();
  scramble(c, '@StartaleGroup', 960, 760, { size: 104, weight: 900, gradient: G_BRAND }, t, T(20, 2), 0.75, { out });
  rise(c, 'becomes interesting.', 960, 870, { size: 54, weight: 500, italic: true, color: C.dim }, t, T(21), { out });
}

// ---- 7 APP
function phone(c, t, x, y, sc, rot) {
  const P_ = L.phone, w = P_.w, h = P_.h;
  c.save();
  c.translate(x, y);
  c.rotate(rot);
  c.scale(sc, sc);
  // glow
  c.save();
  c.shadowColor = 'rgba(138,92,255,0.8)'; c.shadowBlur = 80;
  rr(c, -w / 2, -h / 2, w, h, 64); c.fillStyle = '#0b0b12'; c.fill();
  c.restore();
  c.lineWidth = 6; c.strokeStyle = '#2b2b3a'; c.stroke();
  // screen
  rr(c, -w / 2 + 16, -h / 2 + 16, w - 32, h - 32, 50);
  c.save(); c.clip();
  const sg = c.createLinearGradient(0, -h / 2, 0, h / 2);
  sg.addColorStop(0, '#221a4d'); sg.addColorStop(1, '#0e0c1c');
  c.fillStyle = sg; c.fillRect(-w / 2, -h / 2, w, h);
  const clicked = t >= T(23);
  const cu = cursorAt(t);
  if (!clicked) {
    logo(c, 0, -150, 70, { rot: Math.sin(t * 2) * 0.1, color: C.white });
    text(c, 'Startale App', 0, -10, { size: 40, weight: 800, color: C.white });
    text(c, 'Your way onchain', 0, 38, { size: 22, weight: 500, color: C.dimW });
    const hov = Math.abs(cu.x - P_.x) < 150 && Math.abs(cu.y - (P_.y + 300)) < 40;
    button(c, 0, 300, 320, 76, 'Get started', { hover: hov, scale: 1 - cu.press * 0.06, size: 30 });
  } else {
    const k = E.outBack2(P(t, T(23), 0.4));
    text(c, 'Smart Wallet', 0, -330, { size: 26, weight: 700, color: C.dimW });
    c.save(); c.translate(0, -200); c.scale(k, k);
    c.fillStyle = 'rgba(18,184,134,0.15)'; c.beginPath(); c.arc(0, 0, 80, 0, TAU); c.fill();
    c.strokeStyle = C.green; c.lineWidth = 7; c.beginPath(); c.arc(0, 0, 80, 0, TAU); c.stroke();
    icon(c, 'check', 0, 0, 80, C.green, 10);
    c.restore();
    rise(c, 'Wallet ready', 0, -70, { size: 42, weight: 900, color: C.white }, t, T(23) + 0.1);
    rise(c, 'in one tap', 0, -22, { size: 24, weight: 500, color: C.dimW }, t, T(23) + 0.25);
    const tiles = [['swap', 'Swap', C.cyan], ['star', 'Earn', C.green], ['apps', 'Collect', C.violet], ['play', 'Play', C.pink]];
    tiles.forEach(([ic, lab, col], i) => {
      const tt = T(23, 2) + i * BEAT / 2;
      if (t < tt) return;
      const q = E.outBack2(P(t, tt, 0.3));
      const tx = (i % 2 ? 90 : -90), ty = 110 + Math.floor(i / 2) * 170;
      c.save(); c.translate(tx, ty); c.scale(q, q);
      rr(c, -78, -70, 156, 140, 24); c.fillStyle = 'rgba(255,255,255,0.07)'; c.fill();
      c.strokeStyle = col; c.lineWidth = 2; c.stroke();
      icon(c, ic, 0, -14, 52, col);
      text(c, lab, 0, 44, { size: 22, weight: 700, color: C.white });
      c.restore();
    });
    // confetti burst
    const cp = t - T(23);
    if (cp < 1.2) {
      for (let i = 0; i < 50; i++) {
        const a = rnd(i, 41) * TAU, v = 200 + rnd(i, 42) * 400;
        const px = Math.cos(a) * v * cp, py = -200 + Math.sin(a) * v * cp + 500 * cp * cp;
        c.save(); c.translate(px, py); c.rotate(cp * 10 + i);
        c.globalAlpha = 1 - cp / 1.2;
        c.fillStyle = [C.cyan, C.pink, C.green, C.yellow, C.violet][i % 5];
        c.fillRect(-6, -3, 12, 6);
        c.restore();
      }
    }
  }
  c.restore();
  // notch
  rr(c, -70, -h / 2 + 30, 140, 32, 16); c.fillStyle = '#000'; c.fill();
  c.restore();
}
function sApp(c, t) {
  bgBase(c, 'rgba(60,40,200,0.45)', 1360, 540, 1000);
  gridFloor(c, t, { hy: 820, a: 0.25, col: '11,179,207', sp: 0.6 });
  dust(c, t, 80, 8, 0.5);
  const out = T(24) - 0.14;
  const oa = sceneOut(t, T(24));
  const pin = E.outExpo(P(t, T(22), 0.7));
  const px = lerp(2500, L.phone.x, pin), rot = (1 - pin) * 0.5 + Math.sin(t * 1.4) * 0.02;
  c.save();
  c.globalAlpha = oa;
  phone(c, t, px + (1 - oa) * 600, L.phone.y + Math.sin(t * 2) * 8, 1, rot);
  c.restore();
  scramble(c, 'PRODUCT SPOTLIGHT', 160, 340, { size: 28, weight: 800, ls: 10, align: 'left', color: C.cyan }, t, T(22), 0.4, { out });
  const oS = { size: 124, weight: 900 }, oA = { size: 124, weight: 900, gradient: G_BRAND };
  slam(c, 'STARTALE', 156 + textW(c, 'STARTALE', oS) / 2, 440, oS, t, T(22), { out, outDir: 'left', from: 1.8 });
  slam(c, 'APP', 156 + textW(c, 'APP', oA) / 2, 565, oA, t, T(22, 0.5), { out, outDir: 'left', from: 1.8 });
  typeOn(c, 'Making onchain services', 160, 690, { size: 46, weight: 600, align: 'left' }, t, T(22, 2), 0.5, { out, caret: false });
  typeOn(c, 'simpler & more accessible.', 160, 750, { size: 46, weight: 600, align: 'left', color: C.cyan }, t, T(22, 2) + 0.5, 0.5, { out });
}

// ---- 8 CARDS
function sCards(c, t) {
  bgBase(c, 'rgba(40,60,200,0.4)');
  gridFloor(c, t, { hy: 880, a: 0.2, sp: 0.5 });
  dust(c, t, 80, 10, 0.5);
  const out = T(26) - 0.14;
  scramble(c, 'THE BIGGER PICTURE', 960, 150, { size: 40, weight: 800, ls: 14, color: C.cyan }, t, T(24), 0.55, { out });
  typeOn(c, 'goes beyond building another application', 960, 225, { size: 52, weight: 700 }, t, T(24, 1), 0.85, { out });
  const cards = [
    ['wallet', 'SMART WALLETS', ['Reduce the complexity of', 'blockchain interactions'], C.cyan],
    ['bolt', 'SIMPLIFIED ACCESS', ['Makes Web3 apps', 'easier to explore'], C.violet],
    ['nodes', 'CONNECTED ECOSYSTEM', ['Bridges users, apps &', 'infrastructure'], C.pink],
  ];
  const times = [T(24, 2), T(25, 0), T(25, 2)];
  cards.forEach(([ic, title, lines, col], i) => {
    const t0 = times[i];
    if (t < t0 - 0.1) return;
    const q = E.outBack(P(t, t0 - 0.1, 0.45));
    const cx = 420 + i * 540, cy = 620;
    let oa = 1, oy = 0;
    if (t > out) { const k = E.inCubic(P(t, out, 0.14)); oa = 1 - k; oy = k * 200; }
    c.save();
    c.globalAlpha = oa * clamp((t - t0 + 0.1) / 0.1);
    c.translate(cx, cy + (1 - q) * 300 + oy + Math.sin(t * 2 + i) * 6);
    c.scale(Math.max(0.01, q), 1);
    const glow = Math.exp(-Math.max(0, t - t0) * 4);
    c.save();
    c.shadowColor = col; c.shadowBlur = 30 + glow * 60;
    panel(c, 0, 0, 470, 520, { border: col, lw: 3, r: 28 });
    c.restore();
    // sheen
    const sh = P(t, t0 + 0.1, 0.6);
    if (sh > 0 && sh < 1) {
      c.save();
      rr(c, -235, -260, 470, 520, 28); c.clip();
      const gx = lerp(-500, 500, sh);
      const g = c.createLinearGradient(gx - 120, 0, gx + 120, 0);
      g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(0.5, 'rgba(255,255,255,0.22)'); g.addColorStop(1, 'rgba(255,255,255,0)');
      c.fillStyle = g; c.fillRect(-235, -260, 470, 520);
      c.restore();
    }
    c.fillStyle = col.replace(')', ',0.15)').replace('#', '#');
    c.save(); c.globalAlpha *= 0.18; c.fillStyle = col; c.beginPath(); c.arc(0, -120, 82, 0, TAU); c.fill(); c.restore();
    icon(c, ic, 0, -120, 90, col);
    text(c, title, 0, 30, { size: 33, weight: 900, ls: 1 });
    lines.forEach((ln, k) => text(c, ln, 0, 105 + k * 44, { size: 30, weight: 500, color: C.dim }));
    text(c, `0${i + 1}`, 190, -220, { size: 26, weight: 800, color: col, align: 'right' });
    c.restore();
  });
}

// ---- 9 NETWORK
function sNet(c, t) {
  bgBase(c, 'rgba(30,90,200,0.4)');
  dust(c, t, 90, 12, 0.6);
  const out = T(27) - 0.14;
  const oa = sceneOut(t, T(27));
  const hubs = [[360, 560, 'USERS', 'users', C.cyan], [960, 560, 'APPS', 'apps', C.violet], [1560, 560, 'INFRASTRUCTURE', 'server', C.pink]];
  const ht = [T(26, 0), T(26, 0.5), T(26, 1)];
  c.save();
  c.globalAlpha = oa;
  // links
  const lp = E.inOut(P(t, T(26, 1), BEAT));
  if (lp > 0) {
    c.save();
    for (let k = 0; k < 2; k++) {
      const [x1, y1] = hubs[k], [x2, y2] = hubs[k + 1];
      const g = c.createLinearGradient(x1, 0, x2, 0);
      g.addColorStop(0, hubs[k][4]); g.addColorStop(1, hubs[k + 1][4]);
      c.strokeStyle = g; c.lineWidth = 6;
      c.beginPath(); c.moveTo(x1 + 110, y1); c.lineTo(lerp(x1 + 110, x2 - 110, lp), y2); c.stroke();
      if (lp >= 1) {
        for (let m = 0; m < 4; m++) {
          const ph = ((t * 1.4 + m / 4 + k * 0.13) % 1);
          const dir = m % 2 ? ph : 1 - ph;
          const xx = lerp(x1 + 110, x2 - 110, dir);
          c.fillStyle = C.ink;
          c.beginPath(); c.arc(xx, y1, 7, 0, TAU); c.fill();
        }
      }
    }
    c.restore();
  }
  hubs.forEach(([x, y, lab, ic, col], i) => {
    if (t < ht[i]) return;
    const q = E.outBack2(P(t, ht[i], 0.4));
    // satellites
    for (let k = 0; k < 7; k++) {
      const a = k / 7 * TAU + t * (0.5 + i * 0.1) + i;
      const rr2 = 160 + 20 * Math.sin(t * 2 + k);
      const sx2 = x + Math.cos(a) * rr2 * q, sy2 = y + Math.sin(a) * rr2 * q;
      c.strokeStyle = col; c.globalAlpha = oa * 0.35; c.lineWidth = 1.5;
      c.beginPath(); c.moveTo(x, y); c.lineTo(sx2, sy2); c.stroke();
      c.globalAlpha = oa;
      c.fillStyle = col; c.beginPath(); c.arc(sx2, sy2, 7, 0, TAU); c.fill();
    }
    c.save();
    c.translate(x, y); c.scale(q, q);
    c.fillStyle = '#ffffff'; c.shadowColor = 'rgba(90,60,170,0.25)'; c.shadowBlur = 30; c.beginPath(); c.arc(0, 0, 100, 0, TAU); c.fill(); c.shadowColor = 'rgba(0,0,0,0)';
    c.strokeStyle = col; c.lineWidth = 5; c.stroke();
    icon(c, ic, 0, 0, 80, col);
    c.restore();
    rise(c, lab, x, y + 160, { size: 34, weight: 900, ls: 6 }, t, ht[i] + 0.1, { out });
  });
  c.restore();
  slam(c, 'BRIDGING THE GAP', 960, 220, { size: 104, weight: 900, gradient: G_BRAND }, t, T(26, 2), { out });
}

// ---- 10 PILLARS
function sPillars(c, t) {
  bgBase(c, 'rgba(70,40,170,0.4)', 960, 900, 1200);
  dust(c, t, 70, 14, 0.5);
  const out = T(29) - 0.14;
  typeOn(c, 'Of course, a better user experience alone', 960, 150, { size: 50, weight: 600 }, t, T(27), 0.75, { out, caret: false });
  typeOn(c, 'does not guarantee success.', 960, 215, { size: 50, weight: 800, color: C.orange }, t, T(27) + 0.75, 0.6, { out });
  scramble(c, 'EQUALLY IMPORTANT', 960, 320, { size: 34, weight: 800, ls: 14, color: C.cyan }, t, T(27, 3), 0.45, { out });
  const PL = [['shield', ['SECURITY'], C.green], ['bolt', ['REAL', 'UTILITY'], C.orange], ['access', ['ACCESSI-', 'BILITY'], C.cyan], ['trust', ['USER', 'TRUST'], C.pink]];
  PL.forEach(([ic, lab, col], i) => {
    const t0 = T(28, i);
    if (t < t0 - 0.15) return;
    const q = E.outBack(P(t, t0 - 0.15, 0.4));
    let oy = 0, oa = 1;
    if (t > out) { const k = E.inCubic(P(t, out, 0.14)); oy = k * 700; oa = 1 - k; }
    const x = 330 + i * 420, top = 420;
    c.save();
    c.globalAlpha = oa;
    c.translate(x, (1 - q) * 800 + oy);
    const g = c.createLinearGradient(0, top, 0, H);
    g.addColorStop(0, 'rgba(255,255,255,0.88)'); g.addColorStop(1, 'rgba(255,255,255,0.35)');
    rr(c, -165, top, 330, H - top + 40, [24, 24, 0, 0]);
    c.fillStyle = g; c.fill();
    c.strokeStyle = col; c.lineWidth = 3; c.stroke();
    c.fillStyle = col; c.fillRect(-165, top, 330, 8);
    const glow = Math.exp(-Math.max(0, t - t0) * 5);
    if (glow > 0.02) { c.save(); c.globalAlpha = glow * 0.3; c.fillStyle = col; rr(c, -165, top, 330, H - top, [24, 24, 0, 0]); c.fill(); c.restore(); }
    icon(c, ic, 0, top + 130, 100, col);
    lab.forEach((ln, k) => text(c, ln, 0, top + 270 + k * 52 - (lab.length - 1) * 26, { size: 44, weight: 900, ls: 2 }));
    c.restore();
  });
}

// ---- 11 INSIGHT
const JARGON = ['seed phrase', 'gas fees', 'RPC', 'nonce', 'chain ID', 'signatures', 'bridges', 'approvals', 'slippage', 'networks', 'private keys', 'L2s'];
function tanglePt(u, k) {
  const tx = 960 + 520 * (Math.sin(u * 23.1 + 0.4) * 0.55 + Math.sin(u * 7.3 + 1.1) * 0.45);
  const ty = 510 + 200 * (Math.cos(u * 19.7) * 0.55 + Math.sin(u * 5.9 + 2.0) * 0.45);
  const lx = 300 + 1320 * u, ly = 510 + Math.sin(u * TAU) * 10 * (1 - k);
  return [lerp(tx, lx, k), lerp(ty, ly, k)];
}
function sInsight(c, t) {
  bgBase(c, 'rgba(20,60,150,0.45)');
  dust(c, t, 80, 16, 0.45);
  const p1end = T(31) - 0.14;
  if (t < T(31)) {
    const oa = sceneOut(t, T(31));
    scramble(c, 'THE KEY INSIGHT IS SIMPLE', 960, 140, { size: 34, weight: 800, ls: 14, color: C.cyan }, t, T(29), 0.6, { out: p1end });
    const draw = E.inOut(P(t, T(29), BAR * 0.8));
    const k = E.inOut(P(t, T(30), 0.9));
    const N = 600;
    c.save();
    c.globalAlpha = oa;
    c.lineWidth = 4 + k * 3; c.lineCap = 'round'; c.lineJoin = 'round';
    const g = c.createLinearGradient(300, 0, 1620, 0);
    if (k < 1) { g.addColorStop(0, `rgba(255,51,85,${1 - k})`); g.addColorStop(1, `rgba(255,61,154,${1 - k})`); c.strokeStyle = g; }
    c.beginPath();
    const n = Math.floor(N * draw);
    for (let i = 0; i <= n; i++) { const [x, y] = tanglePt(i / N, k); i ? c.lineTo(x, y) : c.moveTo(x, y); }
    if (k < 1) c.stroke();
    if (k > 0) {
      const g2 = c.createLinearGradient(300, 0, 1620, 0);
      g2.addColorStop(0, `rgba(11,179,207,${k})`); g2.addColorStop(1, `rgba(18,184,134,${k})`);
      c.strokeStyle = g2; c.stroke();
    }
    // flowing particles once straight
    if (k >= 1) {
      c.fillStyle = C.violet;
      for (let m = 0; m < 12; m++) { const u = (t * 0.5 + m / 12) % 1; const [x, y] = tanglePt(u, 1); c.beginPath(); c.arc(x, y, 5, 0, TAU); c.fill(); }
    }
    c.restore();
    // jargon
    JARGON.forEach((j, i) => {
      const jt = T(29) + i * 0.12;
      if (t < jt) return;
      const a = clamp((t - jt) / 0.2) * (1 - k);
      if (a <= 0) return;
      const [x, y] = tanglePt((i + 0.5) / JARGON.length, 0);
      const fly = k * 300;
      c.save();
      c.globalAlpha = a;
      rr(c, x - 100 + (rnd(i, 1) - 0.5) * fly * 2, y - 54 - fly * rnd(i, 2), 200, 52, 26);
      c.fillStyle = 'rgba(255,51,85,0.25)'; c.fill();
      c.strokeStyle = 'rgba(255,51,85,0.8)'; c.lineWidth = 2; c.stroke();
      text(c, j, x + (rnd(i, 1) - 0.5) * fly * 2, y - 28 - fly * rnd(i, 2), { size: 26, weight: 700 });
      c.restore();
    });
    if (t < T(30, 1)) typeOn(c, "People don't want to deal with blockchain complexity.", 960, 880, { size: 48, weight: 600 }, t, T(29, 2), 1.2, { out: T(30, 1) - 0.12 });
    else {
      typeOn(c, 'They want it to work seamlessly', 960, 850, { size: 54, weight: 800 }, t, T(30, 1), 0.65, { out: p1end, caret: false });
      typeOn(c, 'in the background.', 960, 920, { size: 54, weight: 800, color: C.cyan }, t, T(30, 1) + 0.65, 0.5, { out: p1end });
    }
    return;
  }
  // internet analogy
  const end = T(33) - 0.1;
  const oa = sceneOut(t, T(33), 0.1);
  const bp = E.outExpo(P(t, T(31), 0.5));
  const web3 = t >= T(32);
  c.save();
  c.globalAlpha = oa;
  c.translate(0, (1 - bp) * -400);
  panel(c, 960, 300, 980, 300, { r: 22, border: web3 ? 'rgba(123,92,255,0.8)' : 'rgba(11,179,207,0.5)', lw: 3 });
  rr(c, 470, 150, 980, 56, [22, 22, 0, 0]); c.fillStyle = 'rgba(123,92,255,0.07)'; c.fill();
  ['#ff5f57', '#febc2e', '#28c840'].forEach((col, k) => { c.fillStyle = col; c.beginPath(); c.arc(500 + k * 26, 178, 8, 0, TAU); c.fill(); });
  rr(c, 620, 162, 700, 32, 16); c.fillStyle = 'rgba(23,19,46,0.06)'; c.fill();
  text(c, web3 ? 'your-favorite-web3-app' : 'any-website.com', 970, 179, { size: 18, weight: 500, fam: MONO, color: C.dim });
  const lab = web3 ? 'Web3 app  —  it just works' : 'Internet  —  it just works';
  text(c, lab, 960, 320, { size: 54, weight: 800 });
  icon(c, 'check', 960, 390, 50, C.green, 8);
  c.restore();
  const layersA = ['HTTP', 'DNS', 'TLS', 'TCP / IP'];
  const layersB = ['GAS', 'RPC NODES', 'SIGNATURES', 'BRIDGES'];
  for (let i = 0; i < 4; i++) {
    const lt = T(31, 0.5 + i * 0.5);
    if (t < lt) continue;
    const q = E.outExpo(P(t, lt, 0.4));
    const y = 545 + i * 78;
    const sink = web3 ? E.inOut(P(t, T(32, 2), 0.8)) : 0;
    c.save();
    c.globalAlpha = oa * (1 - sink * 0.55) * (0.95 - i * 0.12);
    c.translate((i % 2 ? 1 : -1) * (1 - q) * 1600, sink * 40);
    c.beginPath();
    c.moveTo(560, y - 30); c.lineTo(1400, y - 30); c.lineTo(1440, y + 30); c.lineTo(520, y + 30); c.closePath();
    c.fillStyle = web3 ? 'rgba(138,92,255,0.22)' : 'rgba(11,179,207,0.14)'; c.fill();
    c.strokeStyle = web3 ? C.violet : 'rgba(11,179,207,0.6)'; c.lineWidth = 2; c.stroke();
    if (web3) scramble(c, layersB[i], 980, y + 2, { size: 28, weight: 800, ls: 10 }, t, T(32) + i * 0.08, 0.5);
    else text(c, layersA[i], 980, y + 2, { size: 28, weight: 800, ls: 10 });
    c.restore();
  }
  if (t > T(31, 2)) {
    c.save(); c.globalAlpha = oa * P(t, T(31, 2), 0.3) * (web3 ? 1 : 0.8);
    text(c, web3 ? 'IN THE BACKGROUND' : 'PROTOCOLS YOU NEVER THINK ABOUT', 1490, 860, { size: 20, weight: 800, ls: 6, align: 'right', color: web3 ? C.violet : 'rgba(11,179,207,0.8)' });
    c.restore();
  }
  if (!web3) typeOn(c, 'You use the internet without understanding its protocols.', 960, 965, { size: 42, weight: 600 }, t, T(31), 1.2, { out: T(32) - 0.1 });
  else slam(c, 'Web3 should feel the same.', 960, 970, { size: 72, weight: 900, gradient: G_COOL }, t, T(32, 1), { out: end, from: 1.6 });
  // dive at end
  const dv = E.inExpo(P(t, T(32, 3), BEAT));
  if (dv > 0) { c.fillStyle = `rgba(255,255,255,${dv})`; c.fillRect(0, 0, W, H); }
}

// ---- 12 THESIS
function sThesis(c, t) {
  bgBase(c, 'rgba(20,160,120,0.35)', 960, 540, 1000);
  warp(c, t, 0.5 + Math.exp(-(t - T(33)) * 2), 31, '18,184,134');
  dust(c, t, 60, 18, 0.4);
  const out = T(35) - 0.14;
  rise(c, 'The next phase of crypto adoption may belong to', 960, 190, { size: 44, weight: 600, color: C.dim }, t, T(33), { stagger: 0.008, out });
  rise(c, 'projects that make blockchain', 960, 255, { size: 44, weight: 600, color: C.dim }, t, T(33) + 0.35, { stagger: 0.01, out });
  if (t >= T(33, 2)) rays(c, 960, 480, t, { a: 0.18 * Math.exp(-(t - T(33, 2)) * 1.2) + 0.05, n: 16 });
  slam(c, 'EASIER', 960, 480, { size: 300, weight: 900, gradient: G_GREEN }, t, T(33, 2), { out, from: 3, dur: 0.45 });
  slam(c, 'NOT JUST MORE', 960, 700, { size: 52, weight: 800, ls: 14, color: C.dim }, t, T(34), { out, from: 1.6 });
  slam(c, 'POWERFUL', 960, 820, { size: 140, weight: 900, color: 'rgba(23,19,46,0.7)' }, t, T(34, 1), { out });
  if (t >= T(34, 2)) {
    const sp = E.outExpo(P(t, T(34, 2), 0.25));
    const oa = sceneOut(t, T(35));
    c.save();
    c.globalAlpha = oa;
    c.translate(960, 822); c.rotate(-0.06);
    c.fillStyle = C.red;
    c.fillRect(-400, -9, 800 * sp, 18);
    c.restore();
  }
}

// ---- 13 POLL
function sPoll(c, t) {
  bgBase(c, 'rgba(90,40,200,0.45)', 960, 400, 1100);
  gridFloor(c, t, { hy: 900, a: 0.3, sp: 0.6 });
  dust(c, t, 90, 20, 0.5);
  const out = T(39) - 0.14;
  slam(c, "WHAT'S THE BIGGEST BARRIER", 960, 200, { size: 88, weight: 900 }, t, T(35), { out });
  slam(c, 'TO MAINSTREAM CRYPTO ADOPTION?', 960, 310, { size: 64, weight: 900, gradient: G_BRAND }, t, T(35, 1), { out });
  const opts = [['A', 'Wallet complexity', 'wallet', C.cyan], ['B', 'Gas fees', 'gas', C.orange], ['C', 'Security', 'shield', C.green], ['D', 'User experience', 'cursor', C.pink]];
  const ts = [T(35, 2), T(35, 3), T(36, 0), T(36, 1)];
  const cu = cursorAt(t);
  const PW = L.pollW, PHh = L.pollH;
  opts.forEach(([ltr, lab, ic, col], i) => {
    if (t < ts[i]) return;
    const p = L.poll[i];
    const q = E.outBack2(P(t, ts[i], 0.35));
    const hov = cu.a > 0.5 && Math.abs(cu.x - p.x) < PW / 2 && Math.abs(cu.y - p.y) < PHh / 2;
    let oa = 1;
    if (t > out) oa = 1 - E.inCubic(P(t, out, 0.14));
    c.save();
    c.globalAlpha = oa;
    c.translate(p.x, p.y);
    const hs = hov ? 1.05 : 1;
    c.scale(q * hs, q * hs);
    if (hov) { c.save(); c.shadowColor = col; c.shadowBlur = 60; panel(c, 0, 0, PW, PHh, { border: col, lw: 4, r: 30 }); c.restore(); }
    else panel(c, 0, 0, PW, PHh, { border: 'rgba(123,92,255,0.22)', lw: 2, r: 30 });
    if (hov) { c.save(); c.globalAlpha = 0.1; c.fillStyle = col; rr(c, -PW / 2, -PHh / 2, PW, PHh, 30); c.fill(); c.restore(); }
    c.fillStyle = col;
    c.beginPath(); c.arc(-PW / 2 + 80, 0, 42, 0, TAU); c.fill();
    text(c, ltr, -PW / 2 + 80, 2, { size: 40, weight: 900, color: C.white });
    text(c, lab, -PW / 2 + 150, 2, { size: 48, weight: 800, align: 'left' });
    icon(c, ic, PW / 2 - 70, 0, 56, hov ? col : 'rgba(23,19,46,0.4)');
    c.restore();
  });
  if (t >= T(38, 2)) {
    slam(c, 'TELL US IN THE REPLIES', 960, 970, { size: 56, weight: 900, ls: 6 }, t, T(38, 2), { out, from: 1.7 });
    const b = Math.abs(Math.sin((t - T(38, 2)) * 7)) * 14;
    c.save(); c.globalAlpha = sceneOut(t, T(39));
    icon(c, 'arrowDown', 1460, 968 + b, 44, C.violet, 6);
    icon(c, 'arrowDown', 460, 968 + b, 44, C.violet, 6);
    c.restore();
  }
}

// ---- 14 OUTRO
function sOutro(c, t) {
  bgBase(c, 'rgba(110,60,255,0.5)', 960, 430, 1100);
  rays(c, 960, 430, t, { a: 0.14, n: 22, speed: 0.12 });
  gridFloor(c, t, { hy: 820, a: 0.28, sp: 0.5 });
  dust(c, t, 120, 22, 0.6);
  ring(c, 960, 430, t, T(39), 1.2, 1600, '123,92,255', 14);
  for (let k = 0; k < 6; k++) ring(c, 960, 430, t, T(39, k * 2), 1.4, 650, k % 2 ? '11,179,207' : '255,61,154', 4);
  const bp = beatPulse(t, 6) * (t < T(41) ? 1 : 0);
  logo(c, 960, 430, 155, {
    scale: 1 + bp * 0.04,
    rot: Math.sin((t - T(39)) * 0.6) * 0.06,
    sq: (i, a, m) => {
      const q = E.outBack2(P(t, T(39) + i * 0.06, 0.45));
      return { x: m.x * (0.2 + 0.8 * q), y: m.y * (0.2 + 0.8 * q), s: q, a: clamp(q * 2), rot: m.rot + (1 - q) * 3, flash: Math.max(0, 1 - (t - T(39) - i * 0.06) * 3) };
    },
  });
  scramble(c, '@StartaleGroup', 960, 690, { size: 108, weight: 900, gradient: G_BRAND }, t, T(39, 1), 0.75);
  typeOn(c, 'Worth watching as the onchain ecosystem evolves.', 960, 800, { size: 42, weight: 600, color: C.dim }, t, T(39, 3), 1.1, { caret: false });
  const f = E.inOut(P(t, T(41), DURATION - T(41) - 0.05));
  if (f > 0) { c.fillStyle = `rgba(247,244,255,${f})`; c.fillRect(0, 0, W, H); }
}

const SCENES = [
  [0, T(4), sIntro],
  [T(4), T(7), sHook],
  [T(7), T(9), sComplex],
  [T(9), T(17), sNightmare],
  [T(17), T(19), sContrast],
  [T(19), T(20), sTrans],
  [T(20), T(22), sReveal],
  [T(22), T(24), sApp],
  [T(24), T(26), sCards],
  [T(26), T(27), sNet],
  [T(27), T(29), sPillars],
  [T(29), T(33), sInsight],
  [T(33), T(35), sThesis],
  [T(35), T(39), sPoll],
  [T(39), 1e9, sOutro],
];

// =================================================================== POST FX
function fxState(t) {
  const s = { shake: 0, flash: 0, glitch: 0, punch: 0, whip: 0 };
  for (const e of FX) {
    const d = t - e.t;
    if (d < 0 || d > e.dur) continue;
    const k = 1 - d / e.dur;
    if (e.type === 'shake') s.shake += e.amp * k * k;
    else if (e.type === 'flash') s.flash = Math.max(s.flash, e.a * k * k);
    else if (e.type === 'glitch') s.glitch = Math.max(s.glitch, e.amp * k);
    else if (e.type === 'punch') s.punch += e.amp * k * k * k;
    else if (e.type === 'whip') s.whip = Math.max(s.whip, Math.sin(Math.PI * (d / e.dur)) * e.amp);
  }
  return s;
}
// grain tiles
const GRAIN = [];
for (let g = 0; g < 4; g++) {
  const gc = mk(256, 256), gx = gc.getContext('2d');
  const id = gx.createImageData(256, 256);
  for (let i = 0; i < 256 * 256; i++) {
    const v = Math.floor(rnd(i, g + 100) * 255);
    id.data[i * 4] = id.data[i * 4 + 1] = id.data[i * 4 + 2] = v;
    id.data[i * 4 + 3] = 255;
  }
  gx.putImageData(id, 0, 0);
  GRAIN.push(ctx.createPattern(gc, 'repeat'));
}
const VIG = (() => {
  const v = mk(W, H), vx = v.getContext('2d');
  const g = vx.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 1.0);
  g.addColorStop(0, 'rgba(120,90,200,0)'); g.addColorStop(1, 'rgba(120,90,200,0.22)');
  vx.fillStyle = g; vx.fillRect(0, 0, W, H);
  return v;
})();

function rgbSplit(dst, src, off, oy) {
  const ch = [['#ff0000', -off, -oy], ['#00ff00', 0, 0], ['#0000ff', off, oy]];
  dst.save();
  dst.fillStyle = '#000'; dst.fillRect(0, 0, W, H);
  for (const [col, dx, dy] of ch) {
    tx2.globalCompositeOperation = 'source-over';
    tx2.drawImage(src, 0, 0);
    tx2.globalCompositeOperation = 'multiply';
    tx2.fillStyle = col; tx2.fillRect(0, 0, W, H);
    dst.globalCompositeOperation = 'lighter';
    dst.drawImage(tmp2, dx, dy);
  }
  dst.restore();
}

function render(t) {
  // 1. scene
  bx.setTransform(1, 0, 0, 1, 0, 0);
  bx.globalAlpha = 1;
  bx.globalCompositeOperation = 'source-over';
  bx.filter = 'none';
  for (const [a, b, fn] of SCENES) {
    if (t >= a && t < b) { bx.save(); fn(bx, t); bx.restore(); }
  }
  const f = fxState(t);
  let src = buf;
  // 2. glitch
  if (f.glitch > 0.03) {
    const seed = Math.floor(t * 30);
    rgbSplit(tx, buf, 6 + f.glitch * 28 * rnd(seed, 1), (rnd(seed, 2) - 0.5) * 8 * f.glitch);
    const nb = Math.floor(3 + f.glitch * 10);
    for (let i = 0; i < nb; i++) {
      const y = rnd(seed, 10 + i) * H, h = 8 + rnd(seed, 30 + i) * 90 * f.glitch;
      const dx = (rnd(seed, 50 + i) - 0.5) * 240 * f.glitch;
      tx.drawImage(buf, 0, y, W, h, dx, y, W, h);
    }
    src = tmp;
  }
  // 3. bloom source
  sx.filter = 'blur(6px)';
  sx.clearRect(0, 0, 480, 270);
  sx.drawImage(src, 0, 0, 480, 270);
  sx.filter = 'none';
  sx2.filter = 'blur(10px)';
  sx2.clearRect(0, 0, 240, 135);
  sx2.drawImage(src, 0, 0, 240, 135);
  sx2.filter = 'none';
  // 4. composite with camera
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  const shx = (Math.sin(t * 93.1) * 0.6 + Math.sin(t * 57.3 + 1) * 0.4) * f.shake;
  const shy = (Math.sin(t * 71.7 + 2) * 0.6 + Math.sin(t * 113.9) * 0.4) * f.shake;
  const z = 1 + f.punch + 0.012;
  ctx.save();
  ctx.translate(W / 2 + shx, H / 2 + shy);
  ctx.scale(z, z);
  ctx.rotate(f.shake * 0.0006 * Math.sin(t * 40));
  ctx.translate(-W / 2, -H / 2);
  if (f.whip > 0.02) {
    const n = 7;
    for (let k = 0; k < n; k++) {
      ctx.globalAlpha = k === 0 ? 1 : 0.32;
      ctx.drawImage(src, (k - (n - 1) / 2) * f.whip * 70, 0);
    }
    ctx.globalAlpha = 1;
  } else ctx.drawImage(src, 0, 0);
  ctx.globalCompositeOperation = 'screen';
  ctx.globalAlpha = 0.16;
  ctx.drawImage(sm, 0, 0, W, H);
  ctx.globalAlpha = 0.14;
  ctx.drawImage(sm2, 0, 0, W, H);
  ctx.restore();
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  // 5. cursor
  drawCursor(ctx, t);
  // 6. vignette, scanlines on glitch, grain, flash
  ctx.drawImage(VIG, 0, 0);
  if (f.glitch > 0.05) {
    ctx.globalAlpha = f.glitch * 0.25;
    ctx.fillStyle = '#000';
    for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 2);
    ctx.globalAlpha = 1;
  }
  ctx.save();
  ctx.globalCompositeOperation = 'overlay';
  ctx.globalAlpha = 0.045;
  const gi = Math.floor(t * 30) % 4;
  ctx.translate(Math.floor(rnd(gi, Math.floor(t * 30)) * 256), Math.floor(rnd(gi + 9, Math.floor(t * 30)) * 256));
  ctx.fillStyle = GRAIN[gi];
  ctx.fillRect(-256, -256, W + 512, H + 512);
  ctx.restore();
  if (f.flash > 0.01) {
    ctx.fillStyle = `rgba(255,255,255,${f.flash})`;
    ctx.fillRect(0, 0, W, H);
  }
}

window.renderFrame = (t, q = 0.93) => { render(t); return canvas.toDataURL('image/jpeg', q); };
window.renderOnly = (t) => { render(t); return true; };
window.DURATION = DURATION;
window.motionReady = true;
