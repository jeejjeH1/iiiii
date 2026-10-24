'use strict';
// Deterministic canvas motion graphics: frame(t) depends only on t, so frames can be
// rendered out of order / in parallel and still match the audio exactly.
const W = 1920, H = 1080;
const { T, BEAT, BAR, DURATION, FX, EDGE_T, NODE_T, ROW_T, OP_T } = TL;
const FONT = '"Inter Display", Inter, sans-serif';
const MONO = '"JetBrains Mono", "DejaVu Sans Mono", monospace';
// RedStone brand palette (Colors page)
const C = {
  red: '#AE0822', maroon: '#290004', rasp: '#D1707F', blush: '#EBB3B9', shell: '#FFE3E3',
  hl: '#E41939', peach: '#FFCFC5', white: '#FFF6F6', bg: '#070001',
};
const R_HL = '228,25,57', R_RED = '174,8,34', R_PEACH = '255,207,197', R_WHITE = '255,244,244', R_RASP = '209,112,127';

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
const RA = mk(480, 270), ra = RA.getContext('2d');
const RB = mk(480, 270), rb = RB.getContext('2d');

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
  outQuint: (x) => 1 - Math.pow(1 - x, 5),
};
const rnd = (i, j = 0) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
  h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const TAU = Math.PI * 2;
const beatPulse = (t, k = 8) => Math.exp(-((t % BEAT + BEAT) % BEAT) * k);
const fade = (t, a, b, din = 0.25, dout = 0.25) => Math.min(P(t, a, din), 1 - P(t, b - dout, dout));
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
function rot3(p, rx, ry, rz) {
  let [x, y, z] = p, c, s;
  c = Math.cos(rx); s = Math.sin(rx); [y, z] = [y * c - z * s, y * s + z * c];
  c = Math.cos(ry); s = Math.sin(ry); [x, z] = [x * c + z * s, -x * s + z * c];
  c = Math.cos(rz); s = Math.sin(rz); [x, y] = [x * c - y * s, x * s + y * c];
  return [x, y, z];
}
const fmt = (v, d = 2) => v.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });

// ---------------------------------------------------------------- sprites
const SPR = {};
function sprite(rgb) {
  if (SPR[rgb]) return SPR[rgb];
  const s = mk(128, 128), x = s.getContext('2d');
  const g = x.createRadialGradient(64, 64, 0, 64, 64, 64);
  g.addColorStop(0, `rgba(${rgb},1)`); g.addColorStop(0.15, `rgba(${rgb},0.62)`);
  g.addColorStop(0.42, `rgba(${rgb},0.16)`); g.addColorStop(1, `rgba(${rgb},0)`);
  x.fillStyle = g; x.fillRect(0, 0, 128, 128);
  return (SPR[rgb] = s);
}
function glow(c, x, y, r, rgb, a = 1) {
  if (a <= 0.003 || r <= 0) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.globalAlpha = Math.min(1, a);
  c.drawImage(sprite(rgb), x - r, y - r, 2 * r, 2 * r);
  c.restore();
}
function hexPath(c, x, y, r, rot = 0) {
  c.beginPath();
  for (let k = 0; k < 6; k++) {
    const a = rot + k * Math.PI / 3;
    const px = x + r * Math.cos(a), py = y + r * Math.sin(a);
    k ? c.lineTo(px, py) : c.moveTo(px, py);
  }
  c.closePath();
}
const HEXS = {};
function hexSprite(rgb) {
  if (HEXS[rgb]) return HEXS[rgb];
  const s = mk(200, 200), x = s.getContext('2d');
  x.filter = 'blur(5px)';
  hexPath(x, 100, 100, 80, Math.PI / 6);
  x.fillStyle = `rgba(${rgb},0.85)`; x.fill();
  x.filter = 'blur(2px)';
  x.lineWidth = 5; x.strokeStyle = `rgba(${rgb},1)`; x.stroke();
  return (HEXS[rgb] = s);
}
const HEXGRID = (() => {
  const g = mk(W + 240, H + 240), x = g.getContext('2d');
  const r = 46, dx = r * 1.5, dy = r * Math.sqrt(3);
  x.strokeStyle = 'rgba(209,112,127,1)'; x.lineWidth = 1;
  for (let i = 0; i * dx < W + 300; i++) {
    for (let j = -1; j * dy < H + 300; j++) {
      hexPath(x, i * dx, j * dy + (i % 2 ? dy / 2 : 0), r - 3);
      x.stroke();
    }
  }
  return g;
})();

// ---------------------------------------------------------------- text
const LC = new Map();
function setF(c, o) {
  c.font = `${o.weight || 700} ${o.size || 60}px ${o.fam || FONT}`;
  c.letterSpacing = (o.ls || 0) + 'px';
}
function measure(c, full, o, nocache) {
  const key = c.font + '|' + c.letterSpacing + '|' + full;
  let v = nocache ? null : LC.get(key);
  if (!v) {
    const xs = [];
    for (let i = 0; i <= full.length; i++) xs.push(c.measureText(full.slice(0, i)).width);
    v = { xs, w: xs[full.length] - (full.length ? (o.ls || 0) : 0) };
    if (!nocache) { if (LC.size > 4000) LC.clear(); LC.set(key, v); }
  }
  return v;
}
function metal(c, y, size) {
  const g = c.createLinearGradient(0, y - size * 0.45, 0, y + size * 0.45);
  g.addColorStop(0, '#ffc2c8'); g.addColorStop(0.3, '#ff4d63'); g.addColorStop(0.55, '#E41939');
  g.addColorStop(0.8, '#AE0822'); g.addColorStop(1, '#6a0013');
  return g;
}
const segsOf = (s) => (typeof s === 'string' ? [{ s }] : s);
// Rich text line: segs = [{s, c}] where c is a color or 'metal'. ch(i,n) -> {a, dy, sc} per char.
function rich(c, segs, x, y, o = {}, ch = null) {
  segs = segsOf(segs);
  setF(c, o);
  const full = segs.map((g) => g.s).join('');
  const L = measure(c, full, o, o.nocache);
  const al = o.align || 'center';
  const x0 = al === 'center' ? x - L.w / 2 : al === 'right' ? x - L.w : x;
  const size = o.size || 60;
  c.textAlign = 'left'; c.textBaseline = 'middle';
  let i = 0;
  for (const g of segs) {
    const col = g.c || o.color || C.white;
    const fill = col === 'metal' ? metal(c, y, size) : col;
    if (!ch) {
      c.fillStyle = fill;
      if (o.stroke) { c.strokeStyle = fill; c.lineWidth = o.stroke; c.strokeText(g.s, x0 + L.xs[i], y); }
      else c.fillText(g.s, x0 + L.xs[i], y);
      i += g.s.length;
      continue;
    }
    for (let k = 0; k < g.s.length; k++, i++) {
      if (g.s[k] === ' ') continue;
      const r = ch(i, full.length);
      if (!r || r.a <= 0.003) continue;
      const cw = L.xs[i + 1] - L.xs[i];
      c.save();
      c.globalAlpha *= Math.min(1, r.a);
      c.fillStyle = fill;
      c.translate(x0 + L.xs[i] + cw / 2, y + (r.dy || 0));
      if (r.sc && r.sc !== 1) c.scale(r.sc, r.sc);
      c.fillText(g.s[k], -cw / 2, 0);
      c.restore();
    }
  }
  if (o.sweep != null && o.sweep > -0.2 && o.sweep < 1.2) {
    const u = o.sweep, bw = o.sweepW || 0.12;
    const g = c.createLinearGradient(x0 - 1, 0, x0 + L.w + 1, 0);
    const st = (p, a) => { if (p >= 0 && p <= 1) g.addColorStop(p, `rgba(255,236,236,${a})`); };
    g.addColorStop(0, 'rgba(255,236,236,0)'); g.addColorStop(1, 'rgba(255,236,236,0)');
    st(u - bw, 0); st(u, o.sweepA ?? 0.9); st(u + bw, 0);
    c.save();
    c.globalCompositeOperation = 'lighter';
    c.fillStyle = g;
    c.fillText(full, x0, y);
    c.restore();
  }
  return { x0, w: L.w, xs: L.xs };
}
function textW(c, segs, o) { setF(c, o); return measure(c, segsOf(segs).map((g) => g.s).join(''), o).w; }
// per-char rise reveal (+ optional exit)
function rise(c, segs, x, y, o, t, t0, a = {}) {
  if (t < t0) return;
  const stg = a.stagger ?? 0.02, dur = a.dur ?? 0.5, size = o.size || 60;
  let outA = 1, outDy = 0;
  if (a.out != null && t > a.out) { const q = E.inCubic(P(t, a.out, a.outDur || 0.25)); outA = 1 - q; outDy = -q * 30; }
  if (outA <= 0) return;
  rich(c, segs, x, y, o, (i) => {
    const p = E.outCubic(P(t, t0 + i * stg, dur));
    return { a: p * outA, dy: (1 - p) * size * 0.55 + outDy };
  });
}
// slam-in: scales down from big with ghost trails + chroma split
function slam(c, segs, x, y, o, t, t0, a = {}) {
  if (t < t0) return;
  const dur = a.dur || 0.42, from = a.from || 2.2;
  const p = E.outExpo(P(t, t0, dur));
  let sc = lerp(from, 1, p);
  let al = clamp((t - t0) / 0.05);
  let dx = 0, dy = 0;
  if (a.out != null && t > a.out) {
    const q = E.inCubic(P(t, a.out, a.outDur || 0.22));
    if (a.outDir === 'up') dy = -q * 200; else sc *= 1 + q * 0.6;
    al *= 1 - q;
  }
  if (al <= 0.003) return;
  c.save();
  c.translate(x + dx, y + dy);
  c.globalAlpha *= al;
  const oo = { ...o, align: 'center' };
  if (p < 1) {
    for (let k = 3; k >= 1; k--) {
      c.save();
      const g = sc * (1 + k * 0.16 * (1 - p));
      c.scale(g, g);
      c.globalAlpha *= 0.2 * (1 - p);
      rich(c, segs, 0, 0, oo);
      c.restore();
    }
  }
  c.scale(sc, sc);
  const ca = (1 - p) * 20 + (a.ca || 0);
  if (ca > 0.8) {
    c.save();
    c.globalCompositeOperation = 'lighter';
    c.globalAlpha *= 0.55;
    rich(c, segOnly(segs, '#ff1a3c'), -ca, 0, oo);
    rich(c, segOnly(segs, '#2ad4ff'), ca, 0, oo);
    c.restore();
  }
  rich(c, segs, 0, 0, { ...oo, sweep: a.sweep });
  c.restore();
}
const segOnly = (segs, col) => segsOf(segs).map((g) => ({ s: g.s, c: col }));
const GLY = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#$%&*+=<>/';
function scrambleSegs(segs, t, t0, dur) {
  segs = segsOf(segs);
  const n = segs.reduce((a, g) => a + g.s.length, 0);
  let i = 0;
  return segs.map((g) => {
    let s = '';
    for (const ch of g.s) {
      const tr = t0 + dur * (i / n);
      if (t >= tr || ch === ' ') s += ch;
      else if (t >= tr - dur * 0.5) s += GLY[Math.floor(rnd(i, Math.floor(t * 30)) * GLY.length)];
      else s += ' ';
      i++;
    }
    return { s, c: g.c };
  });
}
function typeOn(c, s, x, y, o, t, t0, dur, cur = true) {
  if (t < t0) return;
  const n = Math.floor(s.length * P(t, t0, dur));
  setF(c, o);
  const L = measure(c, s, o);
  const al = o.align || 'left';
  const x0 = al === 'center' ? x - L.w / 2 : x;
  rich(c, s.slice(0, n), x0, y, { ...o, align: 'left' });
  if (cur && (n < s.length || Math.floor(t * 3) % 2 === 0)) {
    c.fillStyle = o.cursor || C.hl;
    const sz = o.size || 30;
    c.fillRect(x0 + L.xs[n] + 4, y - sz * 0.5, sz * 0.55, sz);
  }
}
function label(c, s, x, y, o = {}) {
  rich(c, s, x, y, { fam: MONO, weight: 500, size: 20, ls: 5, color: C.rasp, align: 'left', ...o });
}

// ---------------------------------------------------------------- light
// anamorphic lens flare with hexagonal ghosts
function flare(c, x, y, k, o = {}) {
  if (k <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  const sw = (o.w || 1100) * k;
  const streaks = [[14, 0.5, R_HL], [4, 0.85, R_PEACH], [1.6, 1, R_WHITE]];
  for (const [hh, a, rgb] of streaks) {
    c.globalAlpha = Math.min(1, a * k);
    c.drawImage(sprite(rgb), x - sw, y - hh * 2.2, 2 * sw, hh * 4.4);
  }
  c.globalAlpha = 1;
  c.restore();
  glow(c, x, y, 220 * k * (o.core || 1), R_HL, 0.55 * k);
  glow(c, x, y, 70 * k * (o.core || 1), R_WHITE, 0.95 * k);
  // starburst (6 spokes = hexagon)
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.translate(x, y);
  c.rotate((o.rot || 0) + NOW * 0.15);
  for (let j = 0; j < 6; j++) {
    c.rotate(Math.PI / 3);
    c.globalAlpha = 0.28 * k;
    c.drawImage(sprite(R_PEACH), -260 * k, -2.5, 520 * k, 5);
  }
  c.restore();
  if (o.ghosts === false) return;
  const dx = W / 2 - x, dy = H / 2 - y;
  const G = [[0.45, 34, R_RASP, 0.16], [0.75, 80, R_HL, 0.09], [1.3, 130, R_PEACH, 0.06], [1.65, 52, R_RED, 0.14], [-0.3, 26, R_PEACH, 0.12], [2.0, 200, R_RASP, 0.04]];
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (const [p, r, rgb, a] of G) {
    c.globalAlpha = a * k;
    const rr = r * (0.7 + 0.3 * k);
    c.drawImage(hexSprite(rgb), x + dx * p - rr, y + dy * p - rr, rr * 2, rr * 2);
  }
  c.restore();
}
// radial-blur god rays from an emitter mask (mask drawn at full-res coords)
function zoomRays(c, cx, cy, strength, maskFn, o = {}) {
  if (strength <= 0.01) return;
  ra.setTransform(1, 0, 0, 1, 0, 0);
  ra.globalCompositeOperation = 'source-over';
  ra.globalAlpha = 1;
  ra.clearRect(0, 0, 480, 270);
  ra.save(); ra.scale(0.25, 0.25); maskFn(ra); ra.restore();
  rb.setTransform(1, 0, 0, 1, 0, 0);
  rb.globalAlpha = 1;
  rb.globalCompositeOperation = 'source-over';
  rb.clearRect(0, 0, 480, 270);
  rb.globalCompositeOperation = 'lighter';
  const N = o.n || 22, step = o.step || 0.045;
  const qx = cx / 4, qy = cy / 4;
  for (let i = 0; i < N; i++) {
    const s = 1 + i * step;
    rb.globalAlpha = Math.min(1, strength * 0.2 * Math.pow(1 - i / N, 1.4));
    rb.setTransform(s, 0, 0, s, qx * (1 - s), qy * (1 - s));
    rb.drawImage(RA, 0, 0);
  }
  rb.setTransform(1, 0, 0, 1, 0, 0);
  c.save();
  c.setTransform(1, 0, 0, 1, 0, 0);
  c.globalCompositeOperation = 'lighter';
  c.globalAlpha = 1;
  c.drawImage(RB, 0, 0, W, H);
  c.restore();
}
// spiky emitter used behind objects
function burstMask(x, y, r, seed, a = 1) {
  return (m) => {
    m.globalCompositeOperation = 'lighter';
    m.globalAlpha = a;
    m.drawImage(sprite(R_HL), x - r * 1.6, y - r * 1.6, r * 3.2, r * 3.2);
    m.drawImage(sprite(R_PEACH), x - r * 0.8, y - r * 0.8, r * 1.6, r * 1.6);
    m.save();
    m.translate(x, y);
    for (let i = 0; i < 40; i++) {
      m.save();
      m.rotate(rnd(i, seed) * TAU + NOW * 0.05 * (rnd(i, seed + 1) - 0.5));
      m.globalAlpha = a * (0.25 + 0.6 * rnd(i, seed + 2));
      const L = r * (0.8 + 1.2 * rnd(i, seed + 3));
      m.drawImage(sprite(rnd(i, seed + 4) > 0.5 ? R_PEACH : R_HL), 0, -r * 0.05, L, r * 0.1);
      m.restore();
    }
    m.restore();
  };
}
function shafts(c, t, a = 1, o = {}) {
  if (a <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  const n = o.n || 5;
  for (let i = 0; i < n; i++) {
    const x0 = W * ((o.x0 ?? 0.08) + (o.span ?? 0.84) * (i + 0.5) / n) + Math.sin(t * 0.3 + i * 1.7) * 70;
    const ang = (o.ang ?? 0.3) + Math.sin(t * 0.21 + i) * 0.05;
    const len = 1600, w0 = 24 + 40 * rnd(i, 77), w1 = 240 + 220 * rnd(i, 78);
    const al = a * (0.05 + 0.05 * rnd(i, 79)) * (0.65 + 0.35 * Math.sin(t * 0.9 + i * 3));
    c.save();
    c.translate(x0, -80);
    c.rotate(ang);
    const g = c.createLinearGradient(0, 0, 0, len);
    g.addColorStop(0, `rgba(255,150,155,${al})`); g.addColorStop(0.45, `rgba(228,25,57,${al * 0.5})`); g.addColorStop(1, 'rgba(228,25,57,0)');
    c.fillStyle = g;
    for (const k of [1, 0.6, 0.3]) {
      c.beginPath(); c.moveTo(-w0 * k / 2, 0); c.lineTo(w0 * k / 2, 0); c.lineTo(w1 * k / 2, len); c.lineTo(-w1 * k / 2, len); c.closePath(); c.fill();
    }
    c.restore();
  }
  c.restore();
}

// ---------------------------------------------------------------- atmosphere
function bg(c, t, o = {}) {
  const k = o.k ?? 1;
  c.fillStyle = C.bg; c.fillRect(0, 0, W, H);
  const cx = o.cx ?? W / 2, cy = o.cy ?? H / 2;
  const g = c.createRadialGradient(cx, cy, 0, cx, cy, 1150);
  g.addColorStop(0, `rgba(92,4,18,${0.8 * k})`); g.addColorStop(0.45, `rgba(41,0,4,${0.6 * k})`); g.addColorStop(1, 'rgba(5,0,1,0)');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 4; i++) {
    const x = W * (0.15 + 0.7 * rnd(i, 3)) + Math.sin(t * 0.13 + i * 2) * 240;
    const y = H * (0.25 + 0.5 * rnd(i, 4)) + Math.cos(t * 0.11 + i) * 130;
    const r = 520 + 280 * rnd(i, 5);
    c.globalAlpha = 0.09 * k * (o.fog ?? 1);
    c.drawImage(sprite(R_RED), x - r, y - r, 2 * r, 2 * r);
  }
  c.restore();
  if (o.grid !== false) {
    c.save();
    c.globalAlpha = 0.055 * k * (o.grid ?? 1);
    c.drawImage(HEXGRID, -120 + Math.sin(t * 0.05) * 60, -120 + ((t * 8) % 79.67) - 40);
    // light sweep through the grid
    const sx0 = ((t * 260) % (W + 1400)) - 700;
    c.globalAlpha = 0.16 * k * (o.grid ?? 1);
    c.beginPath(); c.moveTo(sx0, 0); c.lineTo(sx0 + 260, 0); c.lineTo(sx0 - 140, H); c.lineTo(sx0 - 400, H); c.closePath(); c.clip();
    c.globalCompositeOperation = 'lighter';
    c.drawImage(HEXGRID, -120 + Math.sin(t * 0.05) * 60, -120 + ((t * 8) % 79.67) - 40);
    c.restore();
  }
  // edge darkening
  const v = c.createRadialGradient(W / 2, H / 2, H * 0.3, W / 2, H / 2, H * 1.05);
  v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,0.75)');
  c.fillStyle = v; c.fillRect(0, 0, W, H);
}
function embers(c, t, n = 60, seed = 1, a = 1, o = {}) {
  if (a <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const sp = (25 + 85 * rnd(i, seed)) * (o.speed || 1);
    const life = (H + 200) / sp;
    const ph = (t + rnd(i, seed + 1) * life) % life;
    const y = H + 100 - ph * sp;
    const x = rnd(i, seed + 2) * W + Math.sin(t * (0.4 + rnd(i, seed + 3)) + i) * 50 * (1 + rnd(i, seed + 4));
    const z = rnd(i, seed + 5);
    const r = 1 + z * z * 3.2;
    const fl = 0.55 + 0.45 * Math.sin(t * (3 + 5 * rnd(i, seed + 6)) + i * 7);
    const al = a * fl * (0.35 + 0.65 * z) * clamp(ph / 1.2) * clamp((life - ph) / 1.5);
    glow(c, x, y, r * 7, z > 0.7 ? R_PEACH : R_HL, al * 0.55);
    c.globalAlpha = Math.min(1, al);
    c.fillStyle = z > 0.6 ? '#ffe2da' : '#ff5068';
    c.fillRect(x - r / 2, y - r / 2, r, r);
  }
  c.restore();
}
function bokeh(c, t, n = 7, seed = 9, a = 1) {
  if (a <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const r = 50 + 130 * rnd(i, seed);
    const x = ((rnd(i, seed + 1) * (W + 400) + t * (14 + 30 * rnd(i, seed + 2))) % (W + 400)) - 200;
    const y = rnd(i, seed + 3) * H + Math.sin(t * 0.4 + i) * 30;
    c.globalAlpha = a * (0.04 + 0.09 * rnd(i, seed + 4)) * (0.7 + 0.3 * Math.sin(t * 1.3 + i * 2));
    c.drawImage(hexSprite(rnd(i, seed + 5) > 0.5 ? R_RASP : R_HL), x - r, y - r, r * 2, r * 2);
  }
  c.restore();
}
// particles streaking toward a point (suction before a hit)
function warpIn(c, t, k, cx = W / 2, cy = H / 2, seed = 3) {
  if (k <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.lineCap = 'round';
  for (let i = 0; i < 90; i++) {
    const ang = rnd(i, seed) * TAU;
    const sp = 0.8 + rnd(i, seed + 1) * 1.6;
    const u = 1 - ((t * sp + rnd(i, seed + 2)) % 1);
    const d = 60 + u * 1300;
    const len = (80 + 260 * k) * (0.4 + u);
    const x1 = cx + Math.cos(ang) * d, y1 = cy + Math.sin(ang) * d;
    const x2 = cx + Math.cos(ang) * (d + len), y2 = cy + Math.sin(ang) * (d + len);
    const g = c.createLinearGradient(x1, y1, x2, y2);
    g.addColorStop(0, `rgba(255,214,206,${0.7 * k})`); g.addColorStop(1, 'rgba(228,25,57,0)');
    c.strokeStyle = g;
    c.lineWidth = 1 + 2.2 * rnd(i, seed + 3);
    c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke();
  }
  c.restore();
}
function hexRing(c, x, y, t, t0, dur, maxR, rgb = R_HL, lw = 6) {
  const u = P(t, t0, dur);
  if (u <= 0 || u >= 1) return;
  const r = maxR * E.outExpo(u);
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.strokeStyle = `rgba(${rgb},${(1 - u) * 0.9})`;
  c.lineWidth = lw * (1 - u) + 0.5;
  hexPath(c, x, y, r);
  c.stroke();
  c.strokeStyle = `rgba(255,240,240,${(1 - u) * 0.6})`;
  c.lineWidth = Math.max(0.5, lw * 0.3 * (1 - u));
  c.stroke();
  c.restore();
}

// ---------------------------------------------------------------- RedStone gem (3D hex frustum = the logo in 3D)
const HEXA = [0, 1, 2, 3, 4, 5].map((k) => k * Math.PI / 3);
function gemGeo(R, Hh, ri) {
  const v = [];
  for (const a of HEXA) v.push([R * Math.cos(a), R * Math.sin(a), Hh * R / 2]);
  for (const a of HEXA) v.push([ri * R * Math.cos(a), ri * R * Math.sin(a), -Hh * R / 2]);
  const faces = [[6, 7, 8, 9, 10, 11]];
  for (let k = 0; k < 6; k++) { const k1 = (k + 1) % 6; faces.push([k, k1, 6 + k1, 6 + k]); }
  faces.push([5, 4, 3, 2, 1, 0]);
  const edges = [];
  for (let k = 0; k < 6; k++) edges.push([k, (k + 1) % 6]);
  for (let k = 0; k < 6; k++) edges.push([k, 6 + k]);
  for (let k = 0; k < 6; k++) edges.push([6 + k, 6 + (k + 1) % 6]);
  return { v, faces, edges };
}
const shade = (rgb, m) => `rgb(${Math.min(255, rgb[0] * m) | 0},${Math.min(255, rgb[1] * m) | 0},${Math.min(255, rgb[2] * m) | 0})`;
function gem(c, x, y, R, o = {}) {
  const G = gemGeo(R, o.H ?? 0.42, o.ri ?? 0.5);
  const f = o.f ?? 1800;
  const rv = G.v.map((p) => rot3(p, o.rx || 0, o.ry || 0, o.rz || 0));
  const pv = rv.map((p) => { const k = f / (p[2] + f); return [x + p[0] * k, y + p[1] * k]; });
  const al = o.alpha ?? 1;
  if (al <= 0.003) return pv;
  const fill = o.fill ?? 1;
  const la = o.la ?? 0;
  const toL = norm(rot3([-0.5, -0.75, -0.6], 0, Math.sin(la) * 0.6, la * 0.3));
  const HV = norm(add(toL, [0, 0, -1]));
  const vis = new Set();
  const list = [];
  G.faces.forEach((F, fi) => {
    const a = rv[F[0]], b = rv[F[1]], cc = rv[F[2]];
    let n = norm(cross(sub(b, a), sub(cc, a)));
    const cen = [0, 0, 0];
    for (const i of F) { cen[0] += rv[i][0] / F.length; cen[1] += rv[i][1] / F.length; cen[2] += rv[i][2] / F.length; }
    if (dot(n, cen) < 0) n = [-n[0], -n[1], -n[2]];
    if (dot(n, sub(cen, [0, 0, -f])) >= 0) return;
    vis.add(fi);
    list.push({ F, n, z: cen[2], fi });
  });
  list.sort((p, q) => q.z - p.z);
  c.save();
  c.globalAlpha *= al;
  c.lineJoin = 'round';
  if (o.mask) {
    c.fillStyle = '#000';
    for (const { F } of list) { c.beginPath(); F.forEach((i, k) => (k ? c.lineTo(...pv[i]) : c.moveTo(...pv[i]))); c.closePath(); c.fill(); }
    c.restore();
    return pv;
  }
  if (fill > 0.003) {
    const ga = c.globalAlpha;
    c.globalAlpha = ga * fill;
    for (const { F, n, fi } of list) {
      const diff = Math.max(0, dot(n, toL));
      const spec = Math.pow(Math.max(0, dot(n, HV)), 26);
      const m = 0.18 + 0.95 * diff;
      const base = [lerp(41, 228, clamp(m)), lerp(0, 25, clamp(m)), lerp(4, 57, clamp(m))];
      const col = [base[0] + spec * 230, base[1] + spec * 170, base[2] + spec * 170];
      const p0 = pv[F[0]], p2 = pv[F[Math.floor(F.length / 2)]];
      const g = c.createLinearGradient(p0[0], p0[1], p2[0], p2[1]);
      g.addColorStop(0, shade(col, 1.25 * (o.light ?? 1)));
      g.addColorStop(0.55, shade(col, 0.85 * (o.light ?? 1)));
      g.addColorStop(1, shade(col, 0.55 * (o.light ?? 1)));
      c.fillStyle = g;
      c.beginPath(); F.forEach((i, k) => (k ? c.lineTo(...pv[i]) : c.moveTo(...pv[i]))); c.closePath(); c.fill();
      if (fi === 0) {
        // table facet inner fire
        const tc = [0, 0]; F.forEach((i) => { tc[0] += pv[i][0] / 6; tc[1] += pv[i][1] / 6; });
        c.save(); c.clip();
        c.globalCompositeOperation = 'lighter';
        c.globalAlpha = ga * fill * 0.5;
        const ox = Math.sin(la * 1.7) * R * 0.3;
        c.drawImage(sprite(R_PEACH), tc[0] - R * 0.6 + ox, tc[1] - R * 0.6, R * 1.2, R * 1.2);
        c.restore();
      }
    }
    c.globalAlpha = ga;
  }
  const ea = o.edges ?? 1;
  if (ea > 0.003) {
    const lw = o.lw ?? Math.max(1.2, R / 110);
    c.globalCompositeOperation = 'lighter';
    c.lineCap = 'round';
    G.edges.forEach(([i, j], ei) => {
      const p = o.draw ? o.draw(ei) : 1;
      if (p <= 0) return;
      const onVis = G.faces.some((F, fi) => vis.has(fi) && F.includes(i) && F.includes(j));
      const ka = ea * (onVis || fill < 0.5 ? 1 : 0.25) * (onVis ? 1 : 0.6);
      const a = pv[i], b = [lerp(pv[i][0], pv[j][0], p), lerp(pv[i][1], pv[j][1], p)];
      c.strokeStyle = `rgba(228,25,57,${0.35 * ka})`; c.lineWidth = lw * 5;
      c.beginPath(); c.moveTo(...a); c.lineTo(...b); c.stroke();
      c.strokeStyle = `rgba(255,214,214,${0.85 * ka})`; c.lineWidth = lw;
      c.beginPath(); c.moveTo(...a); c.lineTo(...b); c.stroke();
      if (p < 1) { glow(c, b[0], b[1], 26 + R * 0.08, R_WHITE, 0.95); glow(c, b[0], b[1], 90 + R * 0.2, R_HL, 0.7); }
    });
  }
  c.restore();
  return pv;
}
function logoMark(c, x, y, R, col = C.white, a = 1) {
  if (a <= 0.003) return;
  const lw = R * 0.115;
  c.save();
  c.globalAlpha *= a;
  c.strokeStyle = col; c.lineWidth = lw; c.lineJoin = 'miter'; c.lineCap = 'butt';
  const ro = R - lw / 2, ri = R * 0.5;
  hexPath(c, x, y, ro); c.stroke();
  hexPath(c, x, y, ri); c.stroke();
  c.beginPath();
  for (const a2 of HEXA) { c.moveTo(x + ro * Math.cos(a2), y + ro * Math.sin(a2)); c.lineTo(x + ri * Math.cos(a2), y + ri * Math.sin(a2)); }
  c.stroke();
  c.restore();
}
// logo + wordmark lockup, centered at (cx, cy). reveal: 0..1 wordmark wipe
function lockupGeo(c, size) {
  const R = size * 0.57, gap = size * 0.24;
  const tw = textW(c, 'RedStone', { size, weight: 600, ls: -size * 0.035 });
  const w = R * 2 + gap + tw;
  return { R, gap, tw, w };
}
function wordmark(c, x0, cy, size, reveal, sweep, col = C.white) {
  const o = { size, weight: 600, ls: -size * 0.035, color: col, align: 'left' };
  const tw = textW(c, 'RedStone', o);
  if (reveal <= 0) return;
  c.save();
  c.beginPath(); c.rect(x0 - 10, cy - size, (tw + 20) * reveal, size * 2); c.clip();
  rich(c, 'RedStone', x0 + (1 - E.outCubic(reveal)) * -60, cy + size * 0.02, { ...o, sweep, sweepW: 0.1 });
  c.restore();
  if (reveal < 1) { const ex = x0 + (tw + 20) * reveal; glow(c, ex, cy, size * 1.2, R_HL, 0.6); glow(c, ex, cy, size * 0.35, R_WHITE, 0.9); }
}

// ---------------------------------------------------------------- UI bits
function rr(c, x, y, w, h, r) { c.beginPath(); c.roundRect(x, y, w, h, r); }
function card(c, x, y, w, h, o = {}) {
  c.save();
  rr(c, x, y, w, h, o.r ?? 6);
  c.fillStyle = o.fill || 'rgba(26,2,6,0.72)';
  c.fill();
  c.strokeStyle = o.stroke || 'rgba(235,179,185,0.55)';
  c.lineWidth = o.lw || 1.5;
  c.stroke();
  c.restore();
}
function check(c, x, y, s, col, lw = 3, p = 1) {
  c.save();
  c.strokeStyle = col; c.lineWidth = lw; c.lineCap = 'round'; c.lineJoin = 'round';
  c.beginPath();
  const pts = [[-0.5, 0], [-0.15, 0.35], [0.55, -0.4]];
  c.moveTo(x + pts[0][0] * s, y + pts[0][1] * s);
  if (p < 0.4) c.lineTo(x + lerp(pts[0][0], pts[1][0], p / 0.4) * s, y + lerp(pts[0][1], pts[1][1], p / 0.4) * s);
  else {
    c.lineTo(x + pts[1][0] * s, y + pts[1][1] * s);
    const q = (p - 0.4) / 0.6;
    c.lineTo(x + lerp(pts[1][0], pts[2][0], q) * s, y + lerp(pts[1][1], pts[2][1], q) * s);
  }
  c.stroke();
  c.restore();
}
function cross2(c, x, y, s, col, lw = 3) {
  c.save(); c.strokeStyle = col; c.lineWidth = lw; c.lineCap = 'round';
  c.beginPath(); c.moveTo(x - s / 2, y - s / 2); c.lineTo(x + s / 2, y + s / 2); c.moveTo(x + s / 2, y - s / 2); c.lineTo(x - s / 2, y + s / 2); c.stroke();
  c.restore();
}

// ======================================================================= SCENES
// ---------------------------------------------------------------- 0. cold open (0 - T4)
function sOpen(c, t) {
  const ign = P(t, T(2), 0.25);                 // light ignites at T2
  bg(c, t, { k: 0.25 + 0.75 * E.outCubic(P(t, T(2), 1.2)), grid: 0.3 + 0.7 * ign });
  shafts(c, t, E.outCubic(P(t, T(2), 1.5)) * fade(t, 0, T(4), 0.1, 0.3));
  embers(c, t, 70, 1, 0.3 + 0.7 * ign);
  // scanner line before ignition
  if (t < T(2)) {
    const u = E.inOut(P(t, 0.1, 1.9));
    const y = lerp(260, 820, u);
    c.save();
    c.globalCompositeOperation = 'lighter';
    c.globalAlpha = 0.6 * fade(t, 0.1, T(2), 0.3, 0.3);
    c.drawImage(sprite(R_HL), -200, y - 6, W + 400, 12);
    c.drawImage(sprite(R_WHITE), 300, y - 1.5, W - 600, 3);
    c.restore();
  }
  // gem: rotate in 3D, then fly into the lockup at T3
  const geo = lockupGeo(c, 150);
  const lx = W / 2 - geo.w / 2 + geo.R, ly = 470;
  const m = E.inOut(P(t, T(3) - 0.25, 0.85));
  const gx = lerp(W / 2, lx, m), gy = lerp(500, ly, m);
  const R = lerp(250, geo.R, m);
  const rx = lerp(0.62 - t * 0.07, 0, m);
  const ry = lerp(-0.95 + t * 0.32, 0, m);
  const go = { rx, ry, rz: lerp(0.15 * Math.sin(t * 0.5), 0, m), fill: E.outCubic(P(t, T(2), 0.6)), la: t * 0.9,
    draw: (ei) => P(t, EDGE_T[ei], 0.32), edges: 1 - m * 0.6, alpha: 1 - E.inCubic(P(t, T(3) + 0.35, 0.3)) };
  if (ign > 0) {
    zoomRays(c, gx, gy, 1.4 * E.outCubic(ign) * (1 - 0.6 * m) * (0.85 + 0.15 * Math.sin(t * 3)), (mm) => {
      burstMask(gx, gy, R * 1.25, 11)(mm);
      mm.globalCompositeOperation = 'source-over';
      gem(mm, gx, gy, R, { ...go, mask: true, alpha: 1 });
    });
  }
  gem(c, gx, gy, R, go);
  hexRing(c, gx, gy, t, T(2), 1.2, 900, R_HL, 10);
  hexRing(c, gx, gy, t, T(2) + 0.12, 1.4, 600, R_PEACH, 4);
  // flat logo + wordmark lockup
  const la = E.outCubic(P(t, T(3) + 0.3, 0.35));
  logoMark(c, lx, ly, geo.R, C.white, la);
  if (la > 0) glow(c, lx, ly, geo.R * 2.2, R_HL, 0.35 * la);
  const wx0 = lx + geo.R + geo.gap;
  wordmark(c, wx0, ly, 150, E.outCubic(P(t, T(3) + 0.45, 0.55)), lerp(-0.2, 1.3, P(t, T(3) + 0.8, 1.0)));
  rise(c, [{ s: 'is building more than just another ', c: C.blush }, { s: 'oracle.', c: 'metal' }], W / 2, 690,
    { size: 52, weight: 500 }, t, T(3, 2), { out: T(4) - 0.2 });
  // flares
  flare(c, gx + R * 0.55, gy - R * 0.62, E.outCubic(ign) * (1 - P(t, T(2) + 1.4, 1.2) * 0.55) * (1 - m * 0.7), { w: 1300 });
  if (t > T(3)) flare(c, wx0 + geo.tw * lerp(-0.2, 1.3, P(t, T(3) + 0.8, 1.0)), ly - 50, 0.7 * Math.sin(Math.PI * P(t, T(3) + 0.8, 1.0)), { w: 900, ghosts: false });
  bokeh(c, t, 6, 21, 0.6 * ign);
}

// ---------------------------------------------------------------- 1. RedStone AVS (T4 - T6)
function sAVS(c, t) {
  bg(c, t, { k: 0.8 });
  const boom = P(t, T(5), 0.01);
  embers(c, t, 60, 2, 0.8, { speed: 1 + 2 * boom });
  shafts(c, t, 0.6 + 0.6 * boom);
  // typed lead-in
  typeOn(c, 'ONE OF THE MOST INTERESTING PARTS OF ITS ECOSYSTEM', W / 2, 330,
    { fam: MONO, weight: 500, size: 26, ls: 6, color: C.rasp, align: 'center' }, t, T(4) + 0.1, 1.4, t < T(5));
  // suction before the hit
  warpIn(c, t, E.inCubic(P(t, T(4, 2), T(5) - T(4, 2))) * (1 - boom), W / 2, 540);
  if (t < T(5)) glow(c, W / 2, 540, 40 + 260 * E.inExpo(P(t, T(4, 2), 1)), R_HL, 0.9 * P(t, T(4, 2), 1));
  if (t >= T(5)) {
    const u = t - T(5);
    zoomRays(c, W / 2, 540, 1.1 * Math.exp(-u * 1.6) + 0.22, burstMask(W / 2, 540, 260, 31, 0.8));
    hexRing(c, W / 2, 540, t, T(5), 1.3, 1300, R_HL, 14);
    hexRing(c, W / 2, 540, t, T(5) + 0.1, 1.5, 800, R_PEACH, 5);
    const sw = lerp(-0.2, 1.25, P(t, T(5) + 0.35, 1.1));
    slam(c, [{ s: 'RedStone ', c: C.white }, { s: 'AVS', c: 'metal' }], W / 2, 545, { size: 200, weight: 700, ls: -6 }, t, T(5), { sweep: sw, dur: 0.5 });
    rise(c, 'ACTIVELY  VALIDATED  SERVICE', W / 2, 700, { fam: MONO, weight: 500, size: 26, ls: 10, color: C.blush }, t, T(5) + 0.35, { stagger: 0.015 });
    flare(c, W / 2 + 470, 470, (0.55 + 0.45 * Math.exp(-u * 2)) * E.outCubic(P(t, T(5), 0.2)), { w: 1500 });
  }
  bokeh(c, t, 7, 41, 0.8);
}

// ---------------------------------------------------------------- 2. Why does it matter? (T6 - T7)
function sWhy(c, t) {
  bg(c, t, { k: 0.55, grid: 0.6 });
  embers(c, t, 40, 3, 0.6);
  const u = P(t, T(6), BAR);
  c.save();
  c.translate(W / 2, H / 2); c.scale(1 + 0.08 * u, 1 + 0.08 * u); c.translate(-W / 2, -H / 2);
  glow(c, W / 2, 540, 700, R_RED, 0.35 + 0.25 * E.inCubic(u));
  const segs = scrambleSegs([{ s: 'Why does it ', c: C.white }, { s: 'matter?', c: 'metal' }], t, T(6), 0.7);
  rich(c, segs, W / 2, 540, { size: 150, weight: 700, ls: -4, nocache: true });
  label(c, 'THE PROBLEM', W / 2, 400, { align: 'center', ls: 12, color: C.rasp, size: 22 });
  c.restore();
  glow(c, W / 2 + 560, 470, 60 + 40 * beatPulse(t, 6), R_HL, 0.6 * P(t, T(6) + 0.7, 0.3));
}

// ---------------------------------------------------------------- 3. Risk (T7 - T10)
const CH = { x0: 170, x1: 1750, y0: 860, y1: 330, lo: 2050, hi: 3520 };
const U_M = 0.6;   // where manipulation hits
const tHead = (t) => (t < T(8, 2) ? U_M * P(t, T(7), T(8, 2) - T(7)) : U_M + 0.32 * E.outCubic(P(t, T(8, 2), T(10) - T(8, 2))));
function priceAt(u) {
  const base = 3405 + 34 * Math.sin(u * 13) + 18 * Math.sin(u * 37 + 1) + 8 * Math.sin(u * 91 + 2) + u * 20;
  if (u < U_M) return base;
  const d = u - U_M;
  const crash = d < 0.03 ? E.outCubic(d / 0.03) : Math.max(0, 1 - E.outCubic((d - 0.03) / 0.08));
  return base - 1240 * crash * (1 + 0.04 * Math.sin(d * 400));
}
const chx = (u) => lerp(CH.x0, CH.x1, u);
const chy = (v) => lerp(CH.y0, CH.y1, (v - CH.lo) / (CH.hi - CH.lo));
function sRisk(c, t) {
  const manip = t >= T(8, 2);
  const liq = P(t, T(9), 0.3);
  bg(c, t, { k: 0.7, grid: 0.5 });
  embers(c, t, 40, 4, 0.5);
  // siren beams
  if (t > T(8, 3)) {
    const sa = P(t, T(8, 3), 0.3);
    c.save();
    c.globalCompositeOperation = 'lighter';
    for (const side of [-1, 1]) {
      c.save();
      c.translate(W / 2, -40);
      c.rotate(Math.PI / 2 + side * 0.25 + Math.sin(t * 5 + (side > 0 ? Math.PI : 0)) * 0.7);
      const g = c.createLinearGradient(0, 0, 1700, 0);
      g.addColorStop(0, `rgba(255,40,70,${0.3 * sa})`); g.addColorStop(1, 'rgba(255,40,70,0)');
      c.fillStyle = g;
      c.beginPath(); c.moveTo(0, 0); c.lineTo(1700, -260); c.lineTo(1700, 260); c.closePath(); c.fill();
      c.restore();
    }
    c.restore();
    c.fillStyle = `rgba(228,25,57,${0.08 * sa * (0.5 + 0.5 * Math.sin(t * 10))})`;
    c.fillRect(0, 0, W, H);
  }
  // headline
  const hdA = 1 - P(t, T(9) - 0.15, 0.2);
  c.save();
  c.globalAlpha = hdA;
  label(c, 'DEFI  ·  PRICE FEED', CH.x0, 150, { ls: 8 });
  rise(c, [{ s: 'DeFi protocols depend on ', c: C.white }, { s: 'accurate price data.', c: 'metal' }], CH.x0, 215, { size: 60, weight: 700, align: 'left', ls: -1 }, t, T(7) + 0.05, { stagger: 0.012 });
  c.restore();
  // chart
  const chA = (1 - 0.72 * liq) * E.outCubic(P(t, T(7), 0.4));
  c.save();
  c.globalAlpha = chA;
  c.strokeStyle = 'rgba(235,179,185,0.12)'; c.lineWidth = 1;
  setF(c, { fam: MONO, size: 15, weight: 500 }); c.fillStyle = 'rgba(235,179,185,0.45)'; c.textAlign = 'right'; c.textBaseline = 'middle';
  for (let v = 2200; v <= 3500; v += 260) {
    const y = chy(v);
    c.beginPath(); c.moveTo(CH.x0, y); c.lineTo(CH.x1, y); c.stroke();
    c.fillText(fmt(v, 0), CH.x0 - 14, y);
  }
  const hu = tHead(t);
  const N = Math.max(2, Math.floor(hu * 500));
  const pts = [];
  for (let i = 0; i <= N; i++) { const u = (i / N) * hu; pts.push([chx(u), chy(priceAt(u)), u]); }
  // area
  const ga = c.createLinearGradient(0, CH.y1, 0, CH.y0);
  ga.addColorStop(0, manip ? 'rgba(228,25,57,0.32)' : 'rgba(228,25,57,0.22)'); ga.addColorStop(1, 'rgba(228,25,57,0)');
  c.fillStyle = ga;
  c.beginPath(); c.moveTo(pts[0][0], CH.y0); pts.forEach((p) => c.lineTo(p[0], p[1])); c.lineTo(pts[pts.length - 1][0], CH.y0); c.closePath(); c.fill();
  // line
  c.globalCompositeOperation = 'lighter';
  for (const [lw, col] of [[10, 'rgba(228,25,57,0.25)'], [3, 'rgba(255,90,110,0.95)'], [1.2, 'rgba(255,230,230,0.9)']]) {
    c.lineWidth = lw; c.strokeStyle = col; c.lineJoin = 'round';
    c.beginPath(); pts.forEach((p, i) => (i ? c.lineTo(p[0], p[1]) : c.moveTo(p[0], p[1]))); c.stroke();
  }
  c.restore();
  const hp = pts[pts.length - 1];
  const hv = priceAt(hu);
  glow(c, hp[0], hp[1], 120, R_HL, 0.8 * chA);
  glow(c, hp[0], hp[1], 24, R_WHITE, chA);
  // ticker tag at head
  if (liq < 1) {
    c.save();
    c.globalAlpha = (1 - liq) * E.outCubic(P(t, T(7) + 0.2, 0.3));
    const bad = manip && hu < U_M + 0.11;
    const tw = 300, tx0 = Math.min(hp[0] + 26, W - tw - 30), ty0 = hp[1] - 64;
    card(c, tx0, ty0, tw, 48, { fill: bad ? 'rgba(120,0,16,0.9)' : 'rgba(26,2,6,0.85)', stroke: bad ? C.hl : 'rgba(235,179,185,0.6)' });
    setF(c, { fam: MONO, size: 20, weight: 600 }); c.textAlign = 'left'; c.textBaseline = 'middle';
    c.fillStyle = C.rasp; c.fillText('ETH/USD', tx0 + 16, ty0 + 24);
    c.fillStyle = bad ? '#fff' : C.white; c.fillText('$' + fmt(hv), tx0 + 128, ty0 + 24);
    c.restore();
  }
  // manipulation tag
  if (manip && liq < 1) {
    const mx = chx(U_M + 0.03), my = chy(priceAt(U_M + 0.03));
    const fl = Math.floor(t * 12) % 2 ? 1 : 0.55;
    const a = E.outBack(P(t, T(8, 2) + 0.1, 0.35)) * (1 - liq);
    c.save();
    c.globalAlpha = clamp(a) * fl;
    c.translate(mx - 30, my + 40);
    c.scale(clamp(a, 0, 1.2), clamp(a, 0, 1.2));
    card(c, -20, 0, 420, 64, { fill: 'rgba(228,25,57,0.92)', stroke: '#ffd0d6' });
    setF(c, { fam: MONO, size: 24, weight: 700, ls: 4 }); c.textAlign = 'left'; c.textBaseline = 'middle';
    c.fillStyle = '#fff'; c.fillText('⚠ MANIPULATED FEED', 2, 32);
    c.restore();
    glow(c, mx, my, 260, R_HL, 0.7 * Math.exp(-(t - T(8, 2)) * 2));
  }
  // liquidation cascade
  if (t >= T(9)) {
    const o = { size: 150, weight: 800, ls: -5, align: 'left' };
    c.save();
    slamLeft(c, [{ s: 'BAD', c: C.white }], 150, 340, o, t, T(9));
    slamLeft(c, [{ s: 'LIQUIDATIONS', c: 'metal' }], 150, 490, { ...o, size: 118 }, t, T(9) + 0.12);
    const cnt = 247381920 * E.outQuint(P(t, T(9) + 0.2, 1.6));
    const flick = 0.75 + 0.25 * Math.sin(t * 40);
    c.globalAlpha = E.outCubic(P(t, T(9) + 0.2, 0.2));
    label(c, 'VALUE AT RISK', 158, 640, { ls: 8, size: 22, color: C.blush });
    rich(c, '$' + fmt(cnt, 0), 150, 730, { fam: MONO, size: 92, weight: 700, color: '#ff4d63', align: 'left', ls: -2, nocache: true });
    glow(c, 500, 730, 420, R_HL, 0.25 * flick);
    rise(c, 'Millions put at risk.', 152, 830, { size: 44, weight: 600, color: C.blush, align: 'left' }, t, T(9, 1.5));
    c.restore();
    // feed
    const addrs = ['0x7a3…f19c', '0xb04…2e71', '0x1fd…9a0b', '0xe92…44c3', '0x5c8…d7e2', '0x38a…0f55', '0xc61…b8a4', '0x0de…71aa'];
    const amts = [1.84, 3.27, 0.96, 12.4, 5.12, 2.05, 7.73, 4.48];
    const px0 = 1080, pw = 700;
    c.save();
    c.globalAlpha = E.outCubic(P(t, T(9), 0.3));
    label(c, 'LIQUIDATIONS  ·  LIVE', px0, 300, { ls: 6, color: C.hl, size: 20 });
    glow(c, px0 + 345 + 14 * 6, 300, 14, R_HL, 0.8 + 0.2 * Math.sin(t * 12));
    c.restore();
    for (let k = 0; k < 8; k++) {
      const tk = T(9) + 0.12 + k * BEAT / 2;
      if (t < tk) break;
      const a = E.outCubic(P(t, tk, 0.25));
      const y = 345 + k * 66;
      c.save();
      c.globalAlpha = a;
      c.translate((1 - a) * 120, 0);
      const hot = t - tk < 0.25;
      card(c, px0, y, pw, 54, { fill: hot ? 'rgba(160,6,30,0.85)' : 'rgba(30,2,7,0.8)', stroke: hot ? C.hl : 'rgba(209,112,127,0.45)' });
      setF(c, { fam: MONO, size: 20, weight: 500 }); c.textBaseline = 'middle';
      c.textAlign = 'left'; c.fillStyle = C.blush; c.fillText(addrs[k], px0 + 20, y + 27);
      c.fillStyle = C.hl; c.font = `700 19px ${MONO}`; c.fillText('LIQUIDATED', px0 + 240, y + 27);
      c.textAlign = 'right'; c.fillStyle = C.white; c.font = `700 22px ${MONO}`; c.fillText('−$' + amts[k].toFixed(2) + 'M', px0 + pw - 20, y + 27);
      c.restore();
    }
  }
}
function slamLeft(c, segs, x, y, o, t, t0) {
  if (t < t0) return;
  const w = textW(c, segs, o);
  slam(c, segs, x + w / 2, y, { ...o, align: 'center' }, t, t0, { from: 1.8, dur: 0.38 });
}

// ---------------------------------------------------------------- 4. A different approach (T10 - T11)
function sApproach(c, t) {
  bg(c, t, { k: 0.35, grid: 0.4, fog: 0.6 });
  const u = P(t, T(10), BAR);
  embers(c, t, 40, 5, 0.5);
  warpIn(c, t, E.inCubic(P(t, T(10, 1), T(11) - T(10, 1))), W / 2, 540, 9);
  // horizontal light line opening
  const op = E.outExpo(P(t, T(10), 0.8));
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.globalAlpha = 0.9;
  c.drawImage(sprite(R_HL), W / 2 - 1100 * op, 540 - 10, 2200 * op, 20);
  c.drawImage(sprite(R_WHITE), W / 2 - 800 * op, 540 - 2, 1600 * op, 4);
  c.restore();
  flare(c, W / 2, 540, 0.8 + 0.4 * E.inCubic(P(t, T(10, 2), BEAT * 2)), { w: 1400, ghosts: true });
  rise(c, [{ s: 'RedStone AVS ', c: C.white }, { s: 'takes a', c: C.blush }], W / 2, 400, { size: 76, weight: 700, ls: -2 }, t, T(10) + 0.1, { stagger: 0.018 });
  rise(c, [{ s: 'different approach.', c: 'metal' }], W / 2, 700, { size: 110, weight: 800, ls: -3 }, t, T(10) + 0.45, { stagger: 0.022 });
  void u;
}

// ---------------------------------------------------------------- 5. Pipeline (T11 - T17)
const NODES = [3412.18, 3411.95, 3412.06, 3412.31, 3598.40, 3411.84, 3412.06];
const OUT = 4;
const STEPS = [
  [T(11), '01', 'COLLECT', 'Data is collected from multiple oracle nodes.'],
  [T(12, 2), '02', 'CHECK & AGGREGATE', 'Results are checked and aggregated.'],
  [T(14), '03', 'VALIDATE', 'AVS operators validate the outcome.'],
  [T(15, 2), '04', 'DELIVER', 'Verified data is delivered onchain.'],
];
const CAMK = [
  [T(11), { cx: 960, cy: 600, D: 2400, pitch: 0.5 }],
  [T(12, 2), { cx: 960, cy: 600, D: 2400, pitch: 0.5 }],
  [T(12, 2) + 0.7, { cx: 640, cy: 620, D: 2500, pitch: 0.55 }],
  [T(14), { cx: 640, cy: 620, D: 2500, pitch: 0.55 }],
  [T(14) + 0.7, { cx: 960, cy: 580, D: 3000, pitch: 0.45 }],
  [T(15, 2), { cx: 960, cy: 580, D: 3000, pitch: 0.45 }],
  [T(15, 2) + 0.8, { cx: 960, cy: 400, D: 3300, pitch: 0.62 }],
];
function camAt(t) {
  let a = CAMK[0], b = CAMK[0];
  for (let i = 0; i < CAMK.length; i++) { if (CAMK[i][0] <= t) { a = CAMK[i]; b = CAMK[Math.min(i + 1, CAMK.length - 1)]; } }
  const u = b[0] > a[0] ? E.inOut(P(t, a[0], b[0] - a[0])) : 0;
  const o = {};
  for (const k in a[1]) o[k] = lerp(a[1][k], b[1][k], u);
  o.f = o.D; o.yaw = 0.35 + (t - T(11)) * 0.09;
  return o;
}
function gproj(x, y, z, Pc) {
  const cy = Math.cos(Pc.yaw), sy = Math.sin(Pc.yaw);
  const X = x * cy - z * sy, Z = x * sy + z * cy, Y = -y;
  const cp = Math.cos(Pc.pitch), sp = Math.sin(Pc.pitch);
  const Y2 = Y * cp - Z * sp, Z2 = Y * sp + Z * cp;
  const k = Pc.f / (Z2 + Pc.D);
  return [Pc.cx + X * k, Pc.cy + Y2 * k, k, Z2];
}
function shieldIcon(c, x, y, s, on, a) {
  c.save();
  c.globalAlpha *= a;
  hexPath(c, x, y, s, Math.PI / 6);
  c.fillStyle = on ? 'rgba(174,8,34,0.9)' : 'rgba(30,2,7,0.85)';
  c.fill();
  c.strokeStyle = on ? '#ffd0d6' : 'rgba(209,112,127,0.6)';
  c.lineWidth = Math.max(1, s * 0.09);
  c.stroke();
  if (on) check(c, x, y + s * 0.05, s * 0.8, '#fff', Math.max(1.5, s * 0.12), on);
  c.restore();
}
function sPipe(c, t) {
  bg(c, t, { k: 0.75, grid: 0.5 });
  embers(c, t, 40, 6, 0.55);
  const Pc = camAt(t);
  const ringR = 470, opR = 690;
  const s2 = t >= STEPS[1][0], s3 = t >= STEPS[2][0], s4 = t >= STEPS[3][0];
  const dimRing = 1 - 0.45 * E.inOut(P(t, STEPS[3][0], 0.6));
  // ground rings
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (const [r, a] of [[ringR, 0.35], [opR, 0.22 * E.outCubic(P(t, STEPS[2][0], 0.6))], [180, 0.3]]) {
    if (a <= 0.01) continue;
    c.beginPath();
    for (let i = 0; i <= 96; i++) { const an = (i / 96) * TAU; const p = gproj(r * Math.cos(an), 0, r * Math.sin(an), Pc); i ? c.lineTo(p[0], p[1]) : c.moveTo(p[0], p[1]); }
    c.strokeStyle = `rgba(228,25,57,${a * dimRing})`; c.lineWidth = 2; c.stroke();
  }
  c.restore();
  const coreY = 190 + 10 * Math.sin(t * 1.6);
  const core = gproj(0, coreY, 0, Pc);
  const cg = gproj(0, 0, 0, Pc);
  // objects in depth order
  const objs = [];
  NODES.forEach((v, i) => {
    const an = i * TAU / 7;
    const y = 70 + 12 * Math.sin(t * 2 + i);
    const p = gproj(ringR * Math.cos(an), y, ringR * Math.sin(an), Pc);
    const g0 = gproj(ringR * Math.cos(an), 0, ringR * Math.sin(an), Pc);
    objs.push({ z: p[3], draw: () => drawNode(c, t, i, p, g0, dimRing) });
  });
  if (s3) {
    for (let j = 0; j < 12; j++) {
      const an = j * TAU / 12 + 0.13;
      const p = gproj(opR * Math.cos(an), 40, opR * Math.sin(an), Pc);
      objs.push({ z: p[3], draw: () => drawOp(c, t, j, p, core) });
    }
  }
  objs.push({ z: 0, draw: () => drawCore(c, t, core, cg, Pc) });
  objs.sort((a, b) => b.z - a.z);
  // data packets (S1-S2)
  const pk = () => {
    if (t < T(12) || t > T(14) + 0.3) return;
    const pa = fade(t, T(12), T(14) + 0.3, 0.3, 0.4);
    NODES.forEach((v, i) => {
      const an = i * TAU / 7;
      const nx = ringR * Math.cos(an), nz = ringR * Math.sin(an);
      for (let q = 0; q < 3; q++) {
        const u = ((t - T(12)) / 0.95 + q / 3 + rnd(i, 3) * 0.3) % 1;
        const pt = (uu) => gproj(lerp(nx, 0, uu), lerp(80, coreY, uu) + Math.sin(Math.PI * uu) * 150, lerp(nz, 0, uu), Pc);
        for (let k = 0; k < 6; k++) {
          const uu = u - k * 0.025;
          if (uu < 0) continue;
          const p = pt(uu);
          glow(c, p[0], p[1], (20 - k * 2.5) * p[2] * 1.4, i === OUT ? R_HL : R_PEACH, pa * (1 - k / 6) * 0.9);
        }
      }
    });
  };
  for (const o of objs) { if (o.z === 0) pk(); o.draw(); }
  // ---- step overlays
  stepHeader(c, t);
  if (s2 && t < STEPS[2][0] + 0.4) aggPanel(c, t);
  if (s3) quorum(c, t);
  if (s4) chain(c, t, core);
  progress(c, t);
}
function drawNode(c, t, i, p, g0, dim) {
  const tA = NODE_T[i];
  if (t < tA) return;
  const a = E.outBack(P(t, tA, 0.4));
  const bad = i === OUT && t >= T(13, 1.2);
  glow(c, g0[0], g0[1], 90 * g0[2], bad ? R_HL : R_RED, 0.5 * dim);
  const R = 46 * p[2] * clamp(a, 0, 1.3);
  gem(c, p[0], p[1], R, { rx: 0.35, ry: t * 0.9 + i, fill: 1, la: t + i, alpha: dim, light: bad ? 1.4 : 1 });
  if (t - tA < 0.6) hexRing(c, p[0], p[1], t, tA, 0.6, 140 * p[2], R_PEACH, 4);
  // labels
  const la = clamp(a) * dim * (1 - 0.6 * P(t, STEPS[2][0], 0.5));
  if (la <= 0.01) return;
  c.save();
  c.globalAlpha = la;
  setF(c, { fam: MONO, size: 14 * p[2] + 4, weight: 500, ls: 3 }); c.textAlign = 'center'; c.textBaseline = 'middle';
  c.fillStyle = bad ? C.hl : C.rasp;
  c.fillText('NODE 0' + (i + 1), p[0], p[1] - R - 44 * p[2]);
  setF(c, { fam: MONO, size: 18 * p[2] + 4, weight: 700 });
  c.fillStyle = bad ? '#ff4d63' : C.white;
  c.fillText('$' + fmt(NODES[i]), p[0], p[1] - R - 20 * p[2]);
  if (bad) cross2(c, p[0] + 60 * p[2], p[1] - R - 20 * p[2], 14, C.hl, 3);
  c.restore();
}
function drawCore(c, t, core, cg, Pc) {
  const charge = E.inCubic(P(t, T(15, 2.5), T(16, 2) - T(15, 2.5)));
  const lit = 0.4 + 0.6 * E.outCubic(P(t, T(12), 2)) + charge * 0.8 + 0.5 * Math.exp(-Math.max(0, t - T(13, 2.5)) * 3) * (t > T(13, 2.5) ? 1 : 0);
  glow(c, cg[0], cg[1], 260 * cg[2], R_HL, 0.35);
  // light column from ground to core
  c.save();
  c.globalCompositeOperation = 'lighter';
  const g = c.createLinearGradient(0, cg[1], 0, core[1]);
  g.addColorStop(0, 'rgba(228,25,57,0.0)'); g.addColorStop(1, `rgba(228,25,57,${0.25 * lit})`);
  c.fillStyle = g;
  c.fillRect(core[0] - 40 * core[2], core[1], 80 * core[2], cg[1] - core[1]);
  c.restore();
  const R = 120 * core[2];
  zoomRays(c, core[0], core[1], 0.55 * lit, (m) => {
    burstMask(core[0], core[1], R * 1.3, 51, 1)(m);
    m.globalCompositeOperation = 'source-over';
    gem(m, core[0], core[1], R, { rx: 0.4, ry: t * 0.6, mask: true });
  }, { n: 16 });
  gem(c, core[0], core[1], R, { rx: 0.4, ry: t * 0.6, fill: 1, la: t * 0.8, light: 1 + 0.3 * charge });
  glow(c, core[0], core[1], R * 1.6, R_HL, 0.3 * lit);
  void Pc;
}
function drawOp(c, t, j, p, core) {
  const tA = OP_T[j];
  const a = E.outCubic(P(t, T(14) + 0.1 + j * 0.03, 0.4)) * (1 - 0.5 * E.inOut(P(t, STEPS[3][0], 0.6)));
  if (a <= 0.01) return;
  const on = t >= tA ? E.outCubic(P(t, tA, 0.25)) : 0;
  if (on > 0) {
    // validation beam to the core
    c.save();
    c.globalCompositeOperation = 'lighter';
    const g = c.createLinearGradient(p[0], p[1], core[0], core[1]);
    g.addColorStop(0, `rgba(255,207,197,${0.7 * on * a})`); g.addColorStop(1, `rgba(228,25,57,${0.15 * a})`);
    c.strokeStyle = g; c.lineWidth = 2.2;
    c.beginPath(); c.moveTo(p[0], p[1]); c.lineTo(core[0], core[1]); c.stroke();
    const u = ((t - tA) * 1.4) % 1;
    glow(c, lerp(p[0], core[0], u), lerp(p[1], core[1], u), 18, R_PEACH, 0.9 * a);
    c.restore();
    if (t - tA < 0.5) hexRing(c, p[0], p[1], t, tA, 0.5, 90 * p[2], R_PEACH, 4);
    glow(c, p[0], p[1], 70 * p[2], R_HL, 0.6 * on * a);
  }
  shieldIcon(c, p[0], p[1], 30 * p[2], on, a);
}
function stepHeader(c, t) {
  let si = 0;
  for (let i = 0; i < 4; i++) if (t >= STEPS[i][0]) si = i;
  const [t0, num, title, desc] = STEPS[si];
  const tEnd = si < 3 ? STEPS[si + 1][0] : T(17);
  const outA = 1 - P(t, tEnd - 0.2, 0.2);
  c.save();
  c.globalAlpha = outA;
  const x = 110;
  rich(c, scrambleSegs([{ s: num, c: C.hl }, { s: ' / 04', c: 'rgba(209,112,127,0.6)' }], t, t0 + 0.05, 0.3), x, 120, { fam: MONO, size: 30, weight: 700, ls: 4, align: 'left', nocache: true });
  rise(c, [{ s: title, c: si === 3 ? 'metal' : C.white }], x, 190, { size: 72, weight: 800, ls: -1, align: 'left' }, t, t0 + 0.05, { stagger: 0.02, dur: 0.4 });
  rise(c, desc, x, 255, { size: 32, weight: 500, color: C.blush, align: 'left' }, t, t0 + 0.25, { stagger: 0.008, dur: 0.4 });
  c.restore();
}
function progress(c, t) {
  const names = ['COLLECT', 'AGGREGATE', 'VALIDATE', 'DELIVER'];
  const a = fade(t, T(11), T(17), 0.4, 0.2);
  c.save();
  c.globalAlpha = a;
  const x0 = 110, w = 1700, y = 1012, gap = 16, sw = (w - gap * 3) / 4;
  for (let i = 0; i < 4; i++) {
    const t0 = STEPS[i][0], t1 = i < 3 ? STEPS[i + 1][0] : T(17);
    const fill = P(t, t0, t1 - t0);
    const x = x0 + i * (sw + gap);
    c.fillStyle = 'rgba(209,112,127,0.22)'; c.fillRect(x, y, sw, 4);
    c.fillStyle = C.hl; c.fillRect(x, y, sw * fill, 4);
    if (fill > 0 && fill < 1) glow(c, x + sw * fill, y + 2, 26, R_PEACH, 0.9);
    label(c, `0${i + 1}  ${names[i]}`, x, y - 22, { size: 16, ls: 4, color: t >= t0 ? (fill < 1 ? C.white : C.blush) : 'rgba(209,112,127,0.5)' });
  }
  c.restore();
}
function aggPanel(c, t) {
  const t0 = STEPS[1][0];
  const a = E.outCubic(P(t, t0 + 0.3, 0.4)) * (1 - P(t, STEPS[2][0], 0.3));
  if (a <= 0.01) return;
  const x = 1150, y = 330, w = 660;
  c.save();
  c.globalAlpha = a;
  c.translate((1 - a) * 80, 0);
  card(c, x, y, w, 610, { fill: 'rgba(20,1,5,0.82)' });
  label(c, 'AGGREGATION  ·  ETH/USD', x + 30, y + 40, { size: 18, ls: 5 });
  NODES.forEach((v, i) => {
    const tr = ROW_T[i];
    if (t < tr) return;
    const ra2 = E.outCubic(P(t, tr, 0.25));
    const ry = y + 92 + i * 52;
    const bad = i === OUT;
    const rej = bad && t >= T(13, 1.2);
    c.save();
    c.globalAlpha *= ra2;
    setF(c, { fam: MONO, size: 21, weight: 500 }); c.textBaseline = 'middle'; c.textAlign = 'left';
    c.fillStyle = rej ? C.hl : C.rasp; c.fillText('NODE 0' + (i + 1), x + 30, ry);
    c.textAlign = 'right';
    c.fillStyle = rej ? '#ff4d63' : C.white; c.font = `700 23px ${MONO}`;
    c.fillText('$' + fmt(v), x + 440, ry);
    if (rej) {
      const sp = E.outCubic(P(t, T(13, 1.2), 0.25));
      c.fillStyle = C.hl; c.fillRect(x + 300, ry - 1, 150 * sp, 3);
      c.textAlign = 'left'; c.font = `700 16px ${MONO}`; c.fillText('OUTLIER ✕', x + 480, ry);
      glow(c, x + 380, ry, 120, R_HL, 0.5 * Math.exp(-(t - T(13, 1.2)) * 3));
    } else check(c, x + 500, ry, 18, C.rasp, 2.5, E.outCubic(P(t, tr + 0.1, 0.25)));
    c.restore();
  });
  // result
  const tr = T(13, 2.5);
  if (t >= tr) {
    const r = E.outBack(P(t, tr, 0.4));
    c.strokeStyle = 'rgba(235,179,185,0.4)'; c.lineWidth = 1;
    c.beginPath(); c.moveTo(x + 30, y + 470); c.lineTo(x + w - 30, y + 470); c.stroke();
    label(c, 'MEDIAN  ·  VERIFIED INPUT', x + 30, y + 505, { size: 17, ls: 4, color: C.blush });
    c.save();
    c.translate(x + 30, y + 562);
    c.scale(clamp(r, 0, 1.2), clamp(r, 0, 1.2));
    rich(c, [{ s: '$3,412.06', c: 'metal' }], 0, 0, { size: 64, weight: 800, align: 'left', ls: -1, sweep: lerp(-0.2, 1.3, P(t, tr + 0.2, 0.7)) });
    c.restore();
    check(c, x + w - 70, y + 560, 34, C.peach, 4, E.outCubic(P(t, tr + 0.15, 0.3)));
    glow(c, x + 200, y + 562, 260, R_HL, 0.45 * Math.exp(-(t - tr) * 2.5));
  }
  c.restore();
}
function quorum(c, t) {
  const a = E.outCubic(P(t, T(14) + 0.3, 0.4)) * (1 - P(t, STEPS[3][0], 0.3));
  if (a <= 0.01) return;
  const x = 1290, y = 140, w = 520;
  let n = 0; for (const tt of OP_T) if (t >= tt) n++;
  const pc = n / 12;
  c.save();
  c.globalAlpha = a;
  label(c, 'OPERATOR QUORUM', x, y, { size: 18, ls: 5 });
  rich(c, `${n}/12`, x + w, y, { fam: MONO, size: 22, weight: 700, color: C.white, align: 'right', nocache: true });
  c.fillStyle = 'rgba(209,112,127,0.22)'; c.fillRect(x, y + 28, w, 10);
  const g = c.createLinearGradient(x, 0, x + w, 0); g.addColorStop(0, C.red); g.addColorStop(1, C.hl);
  c.fillStyle = g; c.fillRect(x, y + 28, w * pc, 10);
  glow(c, x + w * pc, y + 33, 40, R_PEACH, 0.9);
  if (t >= T(15, 1)) {
    const r = E.outBack(P(t, T(15, 1), 0.35));
    c.save();
    c.translate(x + w / 2, y + 95);
    c.scale(clamp(r, 0, 1.3), clamp(r, 0, 1.3));
    card(c, -w / 2, -30, w, 60, { fill: 'rgba(174,8,34,0.92)', stroke: '#ffd0d6' });
    rich(c, '✓  CONSENSUS REACHED', 0, 1, { fam: MONO, size: 24, weight: 700, ls: 5, color: '#fff' });
    c.restore();
    glow(c, x + w / 2, y + 95, 300, R_HL, 0.5 * Math.exp(-(t - T(15, 1)) * 2.5));
  }
  c.restore();
}
function chain(c, t, core) {
  const t0 = STEPS[3][0], tb = T(16, 2);
  const slide = 1300 * (1 - E.outCubic(P(t, t0 + 0.2, tb - t0 - 0.2)));
  const y = 800, bw = 210, bh = 140, gap = 60;
  const a = E.outCubic(P(t, t0 + 0.1, 0.4)) * (1 - P(t, T(17) - 0.15, 0.15));
  c.save();
  c.globalAlpha = a;
  for (let j = -4; j <= 4; j++) {
    const x = 960 + j * (bw + gap) + slide;
    if (x < -bw || x > W + bw) continue;
    const ver = j === 0 && t >= tb;
    // link
    if (j < 4) {
      c.strokeStyle = 'rgba(209,112,127,0.5)'; c.lineWidth = 2;
      c.setLineDash([6, 6]); c.lineDashOffset = -t * 30;
      c.beginPath(); c.moveTo(x + bw / 2, y); c.lineTo(x + bw / 2 + gap, y); c.stroke();
      c.setLineDash([]);
    }
    // block (2.5D)
    c.fillStyle = ver ? 'rgba(120,4,22,0.95)' : 'rgba(30,2,7,0.9)';
    c.beginPath(); c.moveTo(x - bw / 2, y - bh / 2); c.lineTo(x - bw / 2 + 22, y - bh / 2 - 20); c.lineTo(x + bw / 2 + 22, y - bh / 2 - 20); c.lineTo(x + bw / 2, y - bh / 2); c.closePath(); c.fill();
    c.strokeStyle = ver ? '#ffd0d6' : 'rgba(209,112,127,0.5)'; c.lineWidth = 1.5; c.stroke();
    card(c, x - bw / 2, y - bh / 2, bw, bh, { r: 4, fill: ver ? 'rgba(174,8,34,0.95)' : 'rgba(22,1,5,0.92)', stroke: ver ? '#ffd0d6' : 'rgba(209,112,127,0.55)' });
    setF(c, { fam: MONO, size: 15, weight: 500, ls: 2 }); c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillStyle = ver ? C.shell : C.rasp;
    c.fillText('BLOCK #' + (21048331 + j), x, y - 40);
    if (ver) {
      const r = E.outBack(P(t, tb + 0.05, 0.35));
      c.save(); c.translate(x, y + 5); c.scale(clamp(r, 0, 1.3), clamp(r, 0, 1.3));
      setF(c, { fam: MONO, size: 22, weight: 800, ls: 3 }); c.fillStyle = '#fff'; c.fillText('VERIFIED ✓', 0, 0);
      setF(c, { fam: MONO, size: 17, weight: 600 }); c.fillStyle = C.shell; c.fillText('ETH/USD 3,412.06', 0, 34);
      c.restore();
    } else {
      c.fillStyle = 'rgba(209,112,127,0.35)';
      for (let k = 0; k < 3; k++) c.fillRect(x - 70, y - 5 + k * 20, 140 - k * 30, 6);
    }
  }
  c.restore();
  // beam
  const bA = t >= tb - 0.08 ? Math.exp(-Math.max(0, t - tb) * 1.6) * P(t, tb - 0.08, 0.08) : 0;
  if (bA > 0.01) {
    c.save();
    c.globalCompositeOperation = 'lighter';
    for (const [w, col] of [[90, `rgba(228,25,57,${0.35 * bA})`], [26, `rgba(255,140,150,${0.8 * bA})`], [6, `rgba(255,245,245,${bA})`]]) {
      const g = c.createLinearGradient(0, core[1], 0, y - bh / 2);
      g.addColorStop(0, col); g.addColorStop(1, col);
      c.fillStyle = g; c.fillRect(960 - w / 2, core[1], w, y - bh / 2 - core[1]);
    }
    c.restore();
    flare(c, 960, y - bh / 2, bA, { w: 1200 });
    hexRing(c, 960, y, t, tb, 1.0, 700, R_HL, 10);
  }
}

// ---------------------------------------------------------------- 6. Efficient + secure (T17 - T19)
function floor(c, t, a = 1, hor = 600) {
  c.save();
  c.globalCompositeOperation = 'lighter';
  const f = 900, h = 320;
  const off = (t * 520) % 200;
  for (let z = 200 - off; z < 5000; z += 200) {
    if (z < 60) continue;
    const y = hor + h * f / z;
    const al = a * 0.35 * clamp(1 - z / 5000) * clamp(z / 300);
    const sweep = Math.exp(-Math.pow((z - ((t * 1400) % 5000)) / 300, 2));
    c.strokeStyle = `rgba(228,25,57,${al + sweep * 0.5 * a})`;
    c.lineWidth = 1 + sweep * 2;
    c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke();
  }
  for (let X = -4000; X <= 4000; X += 260) {
    const x1 = 960 + X * f / 5000, x2 = 960 + X * f / 120;
    const g = c.createLinearGradient(0, hor + h * f / 5000, 0, H);
    g.addColorStop(0, 'rgba(228,25,57,0)'); g.addColorStop(1, `rgba(228,25,57,${0.4 * a})`);
    c.strokeStyle = g; c.lineWidth = 1.2;
    c.beginPath(); c.moveTo(x1, hor + h * f / 5000); c.lineTo(x2, hor + h * f / 120); c.stroke();
  }
  c.restore();
  glow(c, 960, hor + 5, 900, R_HL, 0.28 * a);
  c.save(); c.globalCompositeOperation = 'lighter'; c.globalAlpha = 0.7 * a;
  c.drawImage(sprite(R_HL), -200, hor - 8, W + 400, 16);
  c.drawImage(sprite(R_PEACH), 200, hor - 2, W - 400, 4);
  c.restore();
}
function sSecure(c, t) {
  bg(c, t, { k: 0.6, grid: 0.3, cy: 600 });
  floor(c, t, 1, 640);
  shafts(c, t, 0.9, { n: 6 });
  embers(c, t, 60, 7, 0.8, { speed: 1.4 });
  const sec = t >= T(18);
  label(c, 'THE GOAL', W / 2, 160, { align: 'center', ls: 14, size: 22, color: C.rasp });
  // EFFICIENT
  const ue = E.outCubic(P(t, T(17), 0.5)) * (1 - E.inCubic(P(t, T(18) - 0.1, 0.25)));
  if (ue > 0.01) {
    c.save();
    c.globalAlpha = ue;
    c.translate(0, (1 - ue) * -40);
    slam(c, [{ s: 'EFFICIENT', c: 'metal' }], W / 2, 400, { size: 230, weight: 900, ls: -6 }, t, T(17), { sweep: lerp(-0.2, 1.25, P(t, T(17) + 0.3, 1.1)) });
    rise(c, [{ s: 'More efficient ', c: C.white }, { s: 'oracle verification.', c: C.blush }], W / 2, 560, { size: 50, weight: 600 }, t, T(17) + 0.3);
    c.restore();
    flare(c, W / 2 + 560 * Math.cos(t * 0.8), 330, 0.7 * fade(t, T(17), T(18), 0.2, 0.2), { w: 1200 });
  }
  if (sec) {
    zoomRays(c, W / 2, 400, 1.3 * Math.exp(-(t - T(18)) * 1.5) + 0.3, burstMask(W / 2, 400, 260, 61));
    slam(c, [{ s: 'SECURE', c: 'metal' }], W / 2, 400, { size: 250, weight: 900, ls: -6 }, t, T(18), { sweep: lerp(-0.2, 1.25, P(t, T(18) + 0.3, 1.0)) });
    rise(c, [{ s: 'Stronger security for ', c: C.white }, { s: 'DeFi infrastructure.', c: C.blush }], W / 2, 560, { size: 50, weight: 600 }, t, T(18) + 0.3);
    hexRing(c, W / 2, 400, t, T(18), 1.2, 1100, R_HL, 12);
    flare(c, W / 2 - 360, 320, 0.9 * Math.exp(-(t - T(18)) * 1.2), { w: 1400 });
  }
  bokeh(c, t, 6, 71, 0.7);
}

// ---------------------------------------------------------------- 7. $RED (T19 - T21)
function coin(c, x, y, R, ry, rx, o = {}) {
  const d = R * 0.16;
  const f = 1600;
  const v = [];
  for (const a of HEXA) { const ang = a + Math.PI / 6; v.push([R * Math.cos(ang), R * Math.sin(ang), -d]); }
  for (const a of HEXA) { const ang = a + Math.PI / 6; v.push([R * Math.cos(ang), R * Math.sin(ang), d]); }
  const rv = v.map((p) => rot3(p, rx, ry, 0));
  const pv = rv.map((p) => { const k = f / (p[2] + f); return [x + p[0] * k, y + p[1] * k]; });
  const faces = [{ F: [0, 1, 2, 3, 4, 5], front: 1 }, { F: [11, 10, 9, 8, 7, 6], front: -1 }];
  for (let k = 0; k < 6; k++) { const k1 = (k + 1) % 6; faces.push({ F: [k, k1, 6 + k1, 6 + k], rim: 1 }); }
  const toL = norm([-0.5, -0.6, -0.65]);
  const list = [];
  for (const fc of faces) {
    const a = rv[fc.F[0]], b = rv[fc.F[1]], cc = rv[fc.F[2]];
    let n = norm(cross(sub(b, a), sub(cc, a)));
    const cen = [0, 0, 0];
    for (const i of fc.F) for (let q = 0; q < 3; q++) cen[q] += rv[i][q] / fc.F.length;
    if (dot(n, cen) < 0) n = n.map((q) => -q);
    if (dot(n, sub(cen, [0, 0, -f])) >= 0) continue;
    list.push({ ...fc, n, z: cen[2] });
  }
  list.sort((p, q) => q.z - p.z);
  for (const fc of list) {
    const diff = Math.max(0, dot(fc.n, toL));
    const m = 0.25 + 0.9 * diff;
    c.beginPath(); fc.F.forEach((i, k) => (k ? c.lineTo(...pv[i]) : c.moveTo(...pv[i]))); c.closePath();
    if (fc.rim) {
      c.fillStyle = shade([150, 6, 28], 0.5 + m); c.fill();
      c.strokeStyle = 'rgba(255,200,205,0.5)'; c.lineWidth = 1; c.stroke();
      continue;
    }
    const p0 = pv[fc.F[0]], p3 = pv[fc.F[3]];
    const g = c.createLinearGradient(p0[0], p0[1], p3[0], p3[1]);
    g.addColorStop(0, shade([255, 70, 95], 0.6 + 0.6 * m)); g.addColorStop(0.5, shade([200, 12, 40], 0.5 + 0.6 * m)); g.addColorStop(1, shade([110, 0, 20], 0.6 + 0.5 * m));
    c.fillStyle = g; c.fill();
    c.strokeStyle = 'rgba(255,225,228,0.9)'; c.lineWidth = 2.5; c.stroke();
    // emboss: inner hex + $RED mapped onto the face
    const ctr = fc.front > 0 ? [0, 0, -d] : [0, 0, d];
    const sx2 = fc.front > 0 ? 1 : -1;
    const P0 = rot3(ctr, rx, ry, 0), PX = rot3([ctr[0] + sx2, ctr[1], ctr[2]], rx, ry, 0), PY = rot3([ctr[0], ctr[1] + 1, ctr[2]], rx, ry, 0);
    const pj = (p) => { const k = f / (p[2] + f); return [x + p[0] * k, y + p[1] * k]; };
    const q0 = pj(P0), qx = pj(PX), qy = pj(PY);
    c.save();
    c.transform(qx[0] - q0[0], qx[1] - q0[1], qy[0] - q0[0], qy[1] - q0[1], q0[0], q0[1]);
    hexPath(c, 0, 0, R * 0.8, Math.PI / 6);
    c.strokeStyle = 'rgba(255,215,220,0.55)'; c.lineWidth = 3; c.stroke();
    setF(c, { size: R * 0.42, weight: 800, ls: -R * 0.01 }); c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillStyle = 'rgba(70,0,10,0.55)'; c.fillText('$RED', 3, 4);
    c.fillStyle = '#fff2f2'; c.fillText('$RED', 0, 0);
    c.restore();
    // glint
    const gl = Math.pow(Math.max(0, Math.sin(ry * 2 + 0.6)), 8);
    glow(c, q0[0] - R * 0.3, q0[1] - R * 0.35, R * 0.8, R_PEACH, 0.5 * gl * (o.a ?? 1));
  }
}
function sRed(c, t) {
  bg(c, t, { k: 0.85, grid: 0.6, cx: 1320 });
  shafts(c, t, 0.7, { n: 4, x0: 0.45, span: 0.5 });
  embers(c, t, 55, 8, 0.8);
  const cx = 1330, cy = 540;
  const intro = E.outCubic(P(t, T(19), 0.7));
  const spin = T(19) + 0.9;
  // spin fast then settle into a slow sway
  const ry = t < spin ? lerp(-TAU * 2, 0, E.outCubic(P(t, T(19), 0.9))) + 0.0 : 0.45 * Math.sin((t - spin) * 1.1);
  const rx = -0.12 + 0.06 * Math.sin(t * 0.9);
  const R = 230 * lerp(0.4, 1, intro);
  zoomRays(c, cx, cy, 1.1 * intro, (m) => {
    burstMask(cx, cy, R * 1.25, 81, 1)(m);
    m.globalCompositeOperation = 'source-over';
    m.fillStyle = '#000'; hexPath(m, cx, cy, R * 0.95 * Math.max(0.35, Math.abs(Math.cos(ry))), Math.PI / 6); m.fill();
  });
  // orbit ring (behind half)
  const orbit = (front) => {
    c.save();
    c.globalCompositeOperation = 'lighter';
    for (let i = 0; i < 90; i++) {
      const an = (i / 90) * TAU + t * (0.5 + 0.2 * rnd(i, 4));
      const rr2 = 360 + 40 * rnd(i, 5);
      const z = Math.sin(an);
      if ((z > 0) !== !front) continue;
      const px = cx + Math.cos(an) * rr2, py = cy + z * rr2 * 0.22 - Math.cos(an) * 40;
      glow(c, px, py, 10 + 10 * rnd(i, 6), rnd(i, 7) > 0.5 ? R_PEACH : R_HL, intro * (0.4 + 0.5 * (front ? 1 : 0.5)));
    }
    c.restore();
  };
  orbit(false);
  // security hex forms around the coin at T20
  const hx = E.inOut(P(t, T(20), 0.7));
  if (hx > 0) {
    c.save();
    c.globalCompositeOperation = 'lighter';
    const L = 6 * 390;
    c.setLineDash([L * hx, L]);
    c.lineWidth = 3; c.strokeStyle = 'rgba(255,190,198,0.9)';
    hexPath(c, cx, cy, 390, -Math.PI / 2); c.stroke();
    c.lineWidth = 14; c.strokeStyle = 'rgba(228,25,57,0.3)'; c.stroke();
    c.restore();
    hexRing(c, cx, cy, t, T(20), 1, 700, R_HL, 8);
  }
  c.save();
  c.globalAlpha = intro;
  coin(c, cx, cy, R, ry, rx, { a: intro });
  c.restore();
  orbit(true);
  flare(c, cx - R * 0.55, cy - R * 0.7, 0.8 * intro * (0.7 + 0.3 * Math.sin(t * 2)), { w: 1100 });
  // chips around the hex
  const chips = [['ECONOMIC LAYER', cx, cy - 470], ['INCENTIVES', cx - 380, cy + 300], ['NETWORK SECURITY', cx + 380, cy + 300]];
  chips.forEach(([s, x, y], i) => {
    const a = E.outBack(P(t, T(20) + 0.2 + i * 0.18, 0.35));
    if (a <= 0) return;
    c.save();
    c.translate(x, y); c.scale(clamp(a, 0, 1.2), clamp(a, 0, 1.2));
    const w = textW(c, s, { fam: MONO, size: 18, weight: 600, ls: 4 }) + 44;
    card(c, -w / 2, -24, w, 48, { fill: 'rgba(40,2,8,0.92)', stroke: 'rgba(235,179,185,0.8)', r: 24 });
    rich(c, s, 0, 1, { fam: MONO, size: 18, weight: 600, ls: 4, color: C.shell });
    c.restore();
  });
  // copy
  const x = 130;
  label(c, 'THE TOKEN', x, 280, { ls: 10, size: 22 });
  slamLeft(c, [{ s: '$RED', c: 'metal' }], x, 400, { size: 190, weight: 900, ls: -6 }, t, T(19));
  rise(c, [{ s: 'adds an ', c: C.white }, { s: 'economic layer', c: '#ff5a70' }], x, 560, { size: 56, weight: 700, align: 'left' }, t, T(19, 1));
  rise(c, 'incentivizing participation', x, 640, { size: 40, weight: 500, color: C.blush, align: 'left' }, t, T(19, 2));
  rise(c, 'in network security.', x, 694, { size: 40, weight: 500, color: C.blush, align: 'left' }, t, T(19, 2.4));
  bokeh(c, t, 6, 91, 0.7);
}

// ---------------------------------------------------------------- 8. Takeaway (T21 - T23)
function sTake(c, t) {
  bg(c, t, { k: 0.6, grid: 0.5 });
  embers(c, t, 45, 9, 0.7);
  shafts(c, t, 0.5);
  typeOn(c, 'MY TAKEAWAY', W / 2, 200, { fam: MONO, weight: 600, size: 26, ls: 12, color: C.hl, align: 'center' }, t, T(21) + 0.1, 0.5);
  rise(c, [{ s: 'Oracle infrastructure isn’t ', c: C.white }, { s: 'just', c: C.hl }, { s: ' about delivering prices.', c: C.white }], W / 2, 320,
    { size: 58, weight: 700, ls: -1 }, t, T(21) + 0.3, { stagger: 0.01 });
  rise(c, 'It’s about making the data behind DeFi', W / 2, 430, { size: 44, weight: 500, color: C.blush }, t, T(21, 2.5), { stagger: 0.01 });
  const words = [['RELIABLE', T(22)], ['SCALABLE', T(22, 2)], ['SECURE', T(22, 3)]];
  const xs = [400, 960, 1520];
  words.forEach(([w, tw], i) => {
    if (t < tw) return;
    const k = Math.exp(-(t - tw) * 2.2);
    glow(c, xs[i], 640, 360, R_HL, 0.5 * k + 0.12);
    slam(c, [{ s: w, c: 'metal' }], xs[i], 640, { size: 92, weight: 900, ls: -2 }, t, tw, { sweep: lerp(-0.2, 1.3, P(t, tw + 0.15, 0.8)) });
    hexRing(c, xs[i], 640, t, tw, 0.9, 420, R_HL, 8);
    flare(c, xs[i] + 200, 600, 0.9 * k, { w: 900, ghosts: false });
  });
  if (t > T(22) - 0.1) {
    const da = E.outCubic(P(t, T(22, 3) + 0.25, 0.4));
    c.save(); c.globalAlpha = da;
    for (const x of [690, 1230]) { glow(c, x, 640, 22, R_PEACH, 0.9); }
    c.restore();
  }
  bokeh(c, t, 6, 101, 0.6);
}

// ---------------------------------------------------------------- 9. Metrics (T23 - T25)
function sMetrics(c, t) {
  bg(c, t, { k: 0.7, grid: 0.6 });
  embers(c, t, 40, 10, 0.6);
  const suck = E.inExpo(P(t, T(24, 2), T(25) - T(24, 2)));
  c.save();
  c.translate(W / 2, 540); c.scale(1 - 0.75 * suck, 1 - 0.75 * suck); c.translate(-W / 2, -540);
  c.globalAlpha = 1 - suck;
  label(c, 'WHAT TO WATCH', W / 2, 140, { align: 'center', ls: 12, size: 22 });
  rise(c, [{ s: 'The real ', c: C.white }, { s: 'metrics', c: 'metal' }], W / 2, 215, { size: 76, weight: 800, ls: -2 }, t, T(23) + 0.05);
  const cards = [['01', 'ADOPTION', 'Protocols integrating', T(23, 1)], ['02', 'SECURITY', 'Strength of verification', T(23, 3)], ['03', 'VALUE SECURED', 'Value secured by the network', T(24, 1)]];
  const cw = 500, ch = 560, gap = 50, x0 = W / 2 - (cw * 3 + gap * 2) / 2, y0 = 330;
  cards.forEach(([n, ttl, sub2, tc], i) => {
    if (t < tc) return;
    const a = E.outCubic(P(t, tc, 0.45));
    const x = x0 + i * (cw + gap);
    c.save();
    c.globalAlpha *= a;
    c.translate(0, (1 - a) * 80);
    card(c, x, y0, cw, ch, { fill: 'rgba(22,1,5,0.85)', stroke: 'rgba(235,179,185,0.6)' });
    // border light sweep
    const u = P(t, tc, 0.9);
    if (u < 1) {
      c.save(); c.globalCompositeOperation = 'lighter';
      const per = 2 * (cw + ch), d = u * per;
      const pt = d < cw ? [x + d, y0] : d < cw + ch ? [x + cw, y0 + d - cw] : d < 2 * cw + ch ? [x + cw - (d - cw - ch), y0 + ch] : [x, y0 + ch - (d - 2 * cw - ch)];
      c.restore();
      glow(c, pt[0], pt[1], 50, R_PEACH, 0.9 * (1 - u));
    }
    label(c, n, x + 34, y0 + 46, { size: 20, color: C.hl, ls: 4 });
    label(c, ttl, x + 34, y0 + 92, { size: 30, color: C.white, ls: 6, weight: 600 });
    label(c, sub2.toUpperCase(), x + 34, y0 + 130, { size: 15, color: C.rasp, ls: 3 });
    const gx = x + 34, gy = y0 + 180, gw = cw - 68, gh = 330;
    const p = E.outCubic(P(t, tc + 0.15, 1.2));
    if (i === 0) {
      for (let b = 0; b < 9; b++) {
        const hgt = gh * (0.18 + 0.78 * Math.pow(b / 8, 1.5)) * E.outBack(P(t, tc + 0.1 + b * 0.06, 0.5));
        const bw2 = gw / 9 - 10, bx2 = gx + b * (gw / 9) + 5;
        const g = c.createLinearGradient(0, gy + gh - hgt, 0, gy + gh);
        g.addColorStop(0, b === 8 ? '#ff5a70' : C.hl); g.addColorStop(1, 'rgba(174,8,34,0.25)');
        c.fillStyle = g; c.fillRect(bx2, gy + gh - hgt, bw2, hgt);
      }
      glow(c, gx + gw - 25, gy + gh - gh * 0.96 * p, 70, R_PEACH, 0.6 * p);
    } else if (i === 1) {
      const R = 105;
      for (let k = 0; k < 3; k++) {
        const ru = ((t - tc) * 0.7 + k / 3) % 1;
        c.save(); c.globalCompositeOperation = 'lighter';
        c.strokeStyle = `rgba(228,25,57,${0.6 * (1 - ru)})`; c.lineWidth = 2;
        hexPath(c, gx + gw / 2, gy + gh / 2, R + ru * 120); c.stroke();
        c.restore();
      }
      gem(c, gx + gw / 2, gy + gh / 2, R, { rx: 0.35, ry: t * 0.9, fill: 1, la: t });
    } else {
      const pts = [];
      for (let k = 0; k <= 60; k++) {
        const u2 = k / 60 * p;
        pts.push([gx + u2 * gw, gy + gh - gh * (0.1 + 0.8 * Math.pow(u2, 1.6) + 0.04 * Math.sin(u2 * 22))]);
      }
      c.save();
      const g = c.createLinearGradient(0, gy, 0, gy + gh);
      g.addColorStop(0, 'rgba(228,25,57,0.4)'); g.addColorStop(1, 'rgba(228,25,57,0)');
      c.fillStyle = g;
      c.beginPath(); c.moveTo(gx, gy + gh); pts.forEach((q) => c.lineTo(q[0], q[1])); c.lineTo(pts[pts.length - 1][0], gy + gh); c.closePath(); c.fill();
      c.globalCompositeOperation = 'lighter';
      c.strokeStyle = '#ff5a70'; c.lineWidth = 3;
      c.beginPath(); pts.forEach((q, k) => (k ? c.lineTo(q[0], q[1]) : c.moveTo(q[0], q[1]))); c.stroke();
      c.restore();
      const hd = pts[pts.length - 1];
      glow(c, hd[0], hd[1], 70, R_HL, 0.8); glow(c, hd[0], hd[1], 16, R_WHITE, 1);
      label(c, 'TVS ↑', gx, gy + 20, { size: 22, color: C.shell, weight: 700 });
    }
    c.restore();
  });
  c.restore();
  warpIn(c, t, suck, W / 2, 540, 13);
  glow(c, W / 2, 540, 80 + 400 * suck, R_HL, suck);
}

// ---------------------------------------------------------------- 10. Outro (T25 - end)
function sOutro(c, t) {
  const u = t - T(25);
  bg(c, t, { k: 0.9 * (1 - P(t, T(27, 2), 1.2) * 0.7) });
  shafts(c, t, 1 - P(t, T(27, 2), 1));
  embers(c, t, 80, 11, 1 - P(t, T(27, 3), 1), { speed: 1 + 1.5 * Math.exp(-u) });
  const geo = lockupGeo(c, 160);
  const lx = W / 2 - geo.w / 2 + geo.R, ly = 470;
  const m = E.inOut(P(t, T(26) - 0.1, 0.9));
  const gx = lerp(W / 2, lx, m), gy = lerp(520, ly, m);
  const R = lerp(300, geo.R, m);
  const ry = lerp(-0.7 + u * 0.45, 0, m);
  const go = { rx: lerp(0.5 - u * 0.1, 0, m), ry, rz: lerp(0.12 * Math.sin(u), 0, m), fill: 1, la: t, alpha: 1 - E.inCubic(P(t, T(26) + 0.6, 0.35)) };
  zoomRays(c, gx, gy, (1.2 * Math.exp(-u * 1.1) + 0.35) * (1 - P(t, T(27, 2), 1.2)), (mm) => {
    burstMask(gx, gy, R * 1.3, 101)(mm);
    mm.globalCompositeOperation = 'source-over';
    gem(mm, gx, gy, R, { ...go, mask: true, alpha: 1 });
  });
  gem(c, gx, gy, R, go);
  hexRing(c, W / 2, 520, t, T(25), 1.4, 1400, R_HL, 14);
  hexRing(c, W / 2, 520, t, T(25) + 0.15, 1.6, 900, R_PEACH, 5);
  const la = E.outCubic(P(t, T(26) + 0.6, 0.35));
  logoMark(c, lx, ly, geo.R, C.white, la);
  if (la > 0) glow(c, lx, ly, geo.R * 2.4, R_HL, 0.35 * la);
  const wx0 = lx + geo.R + geo.gap;
  const sw = lerp(-0.2, 1.3, P(t, T(26) + 1.1, 1.1));
  wordmark(c, wx0, ly, 160, E.outCubic(P(t, T(26) + 0.6, 0.55)), sw);
  rise(c, [{ s: 'AVS', c: C.hl }, { s: '   ·   ', c: C.rasp }, { s: '$RED', c: C.hl }], W / 2, 640, { fam: MONO, size: 34, weight: 700, ls: 8 }, t, T(26, 2));
  rise(c, [{ s: 'The data behind DeFi — ', c: C.blush }, { s: 'reliable, scalable, secure.', c: C.white }], W / 2, 720, { size: 40, weight: 500 }, t, T(26, 3), { stagger: 0.008 });
  flare(c, gx + R * 0.5, gy - R * 0.6, (1.2 * Math.exp(-u * 1.2) + 0.25) * (1 - m * 0.6) * (1 - P(t, T(27, 2), 1)), { w: 1500 });
  if (t > T(26) + 1.1) flare(c, wx0 + geo.tw * sw, ly - 55, 0.75 * Math.sin(Math.PI * P(t, T(26) + 1.1, 1.1)), { w: 1000, ghosts: false });
  bokeh(c, t, 7, 111, 0.8 * (1 - P(t, T(27, 2), 1)));
}

const SCENES = [
  [0, T(4), sOpen],
  [T(4), T(6), sAVS],
  [T(6), T(7), sWhy],
  [T(7), T(10), sRisk],
  [T(10), T(11), sApproach],
  [T(11), T(17), sPipe],
  [T(17), T(19), sSecure],
  [T(19), T(21), sRed],
  [T(21), T(23), sTake],
  [T(23), T(25), sMetrics],
  [T(25), 1e9, sOutro],
];

// =================================================================== POST FX
let NOW = 0;
function fxState(t) {
  const s = { shake: 0, flash: 0, glitch: 0, punch: 0, whip: 0, ca: 0 };
  for (const e of FX) {
    const d = t - e.t;
    if (d < 0 || d > e.dur) continue;
    const k = 1 - d / e.dur;
    if (e.type === 'shake') s.shake += e.amp * k * k;
    else if (e.type === 'flash') s.flash = Math.max(s.flash, e.a * k * k);
    else if (e.type === 'glitch') s.glitch = Math.max(s.glitch, e.amp * k);
    else if (e.type === 'punch') s.punch += e.amp * k * k * k;
    else if (e.type === 'ca') s.ca = Math.max(s.ca, e.amp * k * k);
    else if (e.type === 'whip') s.whip = Math.max(s.whip, Math.sin(Math.PI * (d / e.dur)) * e.amp);
  }
  return s;
}
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
  const g = vx.createRadialGradient(W / 2, H / 2, H * 0.4, W / 2, H / 2, H * 1.05);
  g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,0.55)');
  vx.fillStyle = g; vx.fillRect(0, 0, W, H);
  return v;
})();
function rgbSplit(dst, src, off, oy) {
  const ch = [['#ff0000', -off, -oy], ['#00ff00', 0, 0], ['#0000ff', off, oy]];
  dst.save();
  dst.setTransform(1, 0, 0, 1, 0, 0);
  dst.globalAlpha = 1;
  dst.globalCompositeOperation = 'source-over';
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
function letterbox(t) {
  const open = E.inOut(P(t, T(2) - 0.05, 0.7));
  const close = E.inOut(P(t, T(27, 2), 1.4));
  return 132 * Math.max(1 - open, close);
}
function render(t) {
  NOW = t;
  bx.setTransform(1, 0, 0, 1, 0, 0);
  bx.globalAlpha = 1;
  bx.globalCompositeOperation = 'source-over';
  bx.filter = 'none';
  for (const [a, b, fn] of SCENES) {
    if (t >= a && t < b) { bx.save(); fn(bx, t); bx.restore(); }
  }
  const f = fxState(t);
  let src = buf;
  const caAmt = f.ca + f.glitch * 20;
  if (caAmt > 1.2 || f.glitch > 0.03) {
    const seed = Math.floor(t * 30);
    rgbSplit(tx, buf, caAmt * (0.6 + 0.4 * rnd(seed, 1)), (rnd(seed, 2) - 0.5) * 6 * f.glitch);
    if (f.glitch > 0.03) {
      const nb = Math.floor(3 + f.glitch * 10);
      for (let i = 0; i < nb; i++) {
        const y = rnd(seed, 10 + i) * H, h = 8 + rnd(seed, 30 + i) * 90 * f.glitch;
        const dx = (rnd(seed, 50 + i) - 0.5) * 260 * f.glitch;
        tx.drawImage(buf, 0, y, W, h, dx, y, W, h);
      }
    }
    src = tmp;
  }
  // bloom
  sx.filter = 'blur(5px)';
  sx.clearRect(0, 0, 480, 270);
  sx.drawImage(src, 0, 0, 480, 270);
  sx.filter = 'none';
  sx2.filter = 'blur(9px)';
  sx2.clearRect(0, 0, 240, 135);
  sx2.drawImage(src, 0, 0, 240, 135);
  sx2.filter = 'none';
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  const shx = (Math.sin(t * 93.1) * 0.6 + Math.sin(t * 57.3 + 1) * 0.4) * f.shake;
  const shy = (Math.sin(t * 71.7 + 2) * 0.6 + Math.sin(t * 113.9) * 0.4) * f.shake;
  // slow cinematic drift + punch
  const z = 1 + f.punch + 0.02 + 0.008 * Math.sin(t * 0.35);
  ctx.save();
  ctx.translate(W / 2 + shx + Math.sin(t * 0.23) * 6, H / 2 + shy + Math.cos(t * 0.19) * 4);
  ctx.scale(z, z);
  ctx.rotate(f.shake * 0.0005 * Math.sin(t * 40) + 0.002 * Math.sin(t * 0.3));
  ctx.translate(-W / 2, -H / 2);
  if (f.whip > 0.02) {
    const n = 7;
    for (let k = 0; k < n; k++) {
      ctx.globalAlpha = k === 0 ? 1 : 0.3;
      ctx.drawImage(src, (k - (n - 1) / 2) * f.whip * 80, 0);
    }
    ctx.globalAlpha = 1;
  } else ctx.drawImage(src, 0, 0);
  ctx.globalCompositeOperation = 'lighter';
  ctx.globalAlpha = 0.32;
  ctx.drawImage(sm, 0, 0, W, H);
  ctx.globalAlpha = 0.3;
  ctx.drawImage(sm2, 0, 0, W, H);
  // anamorphic haze: bloom smeared horizontally
  ctx.globalAlpha = 0.16;
  ctx.drawImage(sm2, -W * 0.6, 0, W * 2.2, H);
  ctx.restore();
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.drawImage(VIG, 0, 0);
  if (f.glitch > 0.05) {
    ctx.globalAlpha = f.glitch * 0.25;
    ctx.fillStyle = '#000';
    for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 2);
    ctx.globalAlpha = 1;
  }
  ctx.save();
  ctx.globalCompositeOperation = 'overlay';
  ctx.globalAlpha = 0.06;
  const gi = Math.floor(t * 24) % 4;
  ctx.translate(Math.floor(rnd(gi, Math.floor(t * 24)) * 256), Math.floor(rnd(gi + 9, Math.floor(t * 24)) * 256));
  ctx.fillStyle = GRAIN[gi];
  ctx.fillRect(-256, -256, W + 512, H + 512);
  ctx.restore();
  if (f.flash > 0.01) {
    ctx.fillStyle = `rgba(255,236,236,${f.flash})`;
    ctx.fillRect(0, 0, W, H);
  }
  const lb = letterbox(t);
  if (lb > 0.5) { ctx.fillStyle = '#000'; ctx.fillRect(0, 0, W, lb); ctx.fillRect(0, H - lb, W, lb); }
  // fade from / to black
  const fb = Math.max(1 - P(t, 0, 0.6), P(t, DURATION - 0.9, 0.8));
  if (fb > 0) { ctx.fillStyle = `rgba(0,0,0,${fb})`; ctx.fillRect(0, 0, W, H); }
}

window.renderFrame = (t, q = 0.93) => { render(t); return canvas.toDataURL('image/jpeg', q); };
window.renderOnly = (t) => { render(t); return true; };
window.DURATION = DURATION;
Promise.all([
  document.fonts.load(`700 60px ${FONT}`), document.fonts.load(`900 60px ${FONT}`), document.fonts.load(`600 60px ${FONT}`),
  document.fonts.load(`500 60px ${FONT}`), document.fonts.load(`800 60px ${FONT}`),
  document.fonts.load(`500 20px ${MONO}`), document.fonts.load(`700 20px ${MONO}`), document.fonts.load(`600 20px ${MONO}`),
]).catch(() => {}).then(() => { window.motionReady = true; });
