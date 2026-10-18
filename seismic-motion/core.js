'use strict';
// Core: canvases, math, text, the Seismic logo geometry, UI primitives and a tiny 3D camera.
// Every frame is a pure function of t, so frames can render out of order / in parallel.
const W = 1920, H = 1080;
const { T, BEAT, BAR, DURATION, FX, K } = TL;
const SANS = '"Inter Tight", Inter, sans-serif';
const SERIF = '"Source Serif 4", "DejaVu Serif", serif';
const MONO = '"JetBrains Mono", "DejaVu Sans Mono", monospace';
// brand palette (from the Seismic brand guidelines)
const C = {
  mauve: '#825A6D', purple: '#523542', white: '#FFFFFF', g50: '#FCFCFC', g200: '#E8E8E8',
  g300: '#D4D4D4', g400: '#A4A3A1', g500: '#737373', g800: '#282826', black: '#161616',
};

// ---------------------------------------------------------------- canvases
const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const ctx = canvas.getContext('2d');
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const buf = mk(W, H), bx = buf.getContext('2d');
const tmp = mk(W, H), tx = tmp.getContext('2d');
const tmp2 = mk(W, H), tx2 = tmp2.getContext('2d');
const LA = mk(W, H), la = LA.getContext('2d');
const LB = mk(W, H), lb = LB.getContext('2d');
const sm = mk(480, 270), sx = sm.getContext('2d');
const sm2 = mk(240, 135), sx2 = sm2.getContext('2d');
const gl = mk(480, 270), gx = gl.getContext('2d'); // glass blur source

// ---------------------------------------------------------------- math
const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
const lerp = (a, b, t) => a + (b - a) * t;
const P = (t, a, d) => clamp((t - a) / d);
const E = {
  outCubic: (x) => 1 - Math.pow(1 - x, 3),
  inCubic: (x) => x * x * x,
  inQuad: (x) => x * x,
  inOut: (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2),
  inOutSine: (x) => -(Math.cos(Math.PI * x) - 1) / 2,
  outExpo: (x) => (x >= 1 ? 1 : 1 - Math.pow(2, -10 * x)),
  inExpo: (x) => (x <= 0 ? 0 : Math.pow(2, 10 * x - 10)),
  inOutExpo: (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2),
  outBack: (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); },
  outQuint: (x) => 1 - Math.pow(1 - x, 5),
};
const rnd = (i, j = 0) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
  h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const TAU = Math.PI * 2;
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix3 = (a, b, k) => [lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k)];
const rgba = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;

// ---------------------------------------------------------------- text
const layoutCache = new Map();
function setF(c, o) {
  c.font = `${o.italic ? 'italic ' : ''}${o.weight || 500} ${o.size || 60}px ${o.fam || SANS}`;
  c.letterSpacing = (o.ls != null ? o.ls : -(o.size || 60) * 0.02) + 'px';
}
function layout(c, s, o) {
  const key = c.font + '|' + c.letterSpacing + '|' + s;
  let v = layoutCache.get(key);
  if (!v) {
    const xs = [];
    for (let i = 0; i <= s.length; i++) xs.push(c.measureText(s.slice(0, i)).width);
    const ls = parseFloat(c.letterSpacing) || 0;
    v = { xs, w: xs[s.length] - ls };
    layoutCache.set(key, v);
  }
  return v;
}
function textW(c, s, o) { setF(c, o); return layout(c, s, o).w; }
function text(c, s, x, y, o = {}) {
  setF(c, o);
  const w = layout(c, s, o).w;
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  c.textAlign = 'left';
  c.textBaseline = 'middle';
  c.fillStyle = o.color || C.black;
  c.fillText(s, x0, y);
  return w;
}
// Word-by-word masked rise (premium line reveal)
function maskRise(c, s, x, y, o, t, t0, a = {}) {
  if (t < t0) return;
  const sz = o.size || 60;
  setF(c, o);
  const { xs, w } = layout(c, s, o);
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  const words = [];
  let st = 0;
  for (let i = 0; i <= s.length; i++) {
    if (i === s.length || s[i] === ' ') { if (i > st) words.push([st, i]); st = i + 1; }
  }
  const stg = a.stagger ?? 0.05, dur = a.dur ?? 0.55;
  let outQ = 0;
  if (a.out != null && t > a.out) outQ = E.inCubic(P(t, a.out, a.outDur || 0.22));
  if (outQ >= 1) return;
  c.save();
  c.beginPath();
  c.rect(x0 - sz, y - sz * 0.66, w + sz * 2, sz * 1.36);
  c.clip();
  c.textAlign = 'left'; c.textBaseline = 'middle';
  words.forEach(([i0, i1], k) => {
    const p = E.outExpo(P(t, t0 + k * stg, dur));
    if (p <= 0) return;
    const col = (a.colors && a.colors[k]) || o.color || C.black;
    c.fillStyle = col;
    c.globalAlpha = (o.alpha ?? 1) * clamp(p * 3);
    const dy = (1 - p) * sz * 1.25 - outQ * sz * 1.35;
    c.fillText(s.slice(i0, i1), x0 + xs[i0], y + dy);
  });
  c.restore();
}
const GLY = 'ABCDEF0123456789abcdef#$%&*+=<>/?';
function scramble(c, s, x, y, o, t, t0, dur, a = {}) {
  if (t < t0) return;
  setF(c, o);
  const { xs, w } = layout(c, s, o);
  const align = o.align || 'center';
  const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
  const p = (t - t0) / dur;
  c.save();
  c.textAlign = 'left'; c.textBaseline = 'middle';
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (ch === ' ') continue;
    const ri = i / s.length;
    if (p < ri * 0.4) break;
    let d = ch;
    if (p < 0.4 + ri * 0.6) {
      d = GLY[Math.floor(rnd(i * 7 + 3, Math.floor(t * 30)) * GLY.length)];
      c.fillStyle = a.scol || C.mauve;
    } else c.fillStyle = o.color || C.black;
    c.fillText(d, x0 + xs[i], y);
  }
  c.restore();
}
function typeOn(c, s, x, y, o, t, t0, dur, a = {}) {
  if (t < t0) return;
  setF(c, o);
  const { xs } = layout(c, s, o);
  const n = Math.floor(clamp((t - t0) / dur) * s.length);
  c.save();
  c.textAlign = 'left'; c.textBaseline = 'middle';
  c.fillStyle = o.color || C.black;
  c.fillText(s.slice(0, n), x, y);
  if (a.caret !== false && n < s.length) {
    c.fillStyle = a.caretColor || C.mauve;
    const sz = o.size || 60;
    c.fillRect(x + xs[n] + 3, y - sz * 0.5, 2.5, sz);
  }
  c.restore();
}

// ---------------------------------------------------------------- the logo
// Five facets traced from the brand mark, normalised so the mark spans y in [-1, 1].
const FACETS = [
  [[192, 107], [95, 233], [164, 345], [263, 203]],   // 0 big left
  [[190, 62], [122, 185], [187, 101]],               // 1 top-left sliver
  [[198, 50], [195, 100], [265, 190], [275, 84]],    // 2 top-right
  [[279, 115], [272, 201], [300, 232]],              // 3 right sliver
  [[270, 207], [171, 354], [262, 353], [300, 248]],  // 4 bottom-right
].map((f) => f.map(([x, y]) => [(x - 197.5) / 152, (y - 202) / 152]));
const FCOL = ['#58394A', '#634454', '#6F5262', '#8D7684', '#7A5E6E'];
const FCOL_RGB = FCOL.map(hex);
const FCENT = FACETS.map((f) => {
  let x = 0, y = 0;
  f.forEach((p) => { x += p[0]; y += p[1]; });
  return [x / f.length, y / f.length];
});
const LOGO_HW = 0.674; // half-width of the mark relative to r
function facetPath(c, i, x, y, r) {
  const f = FACETS[i];
  c.beginPath();
  c.moveTo(x + f[0][0] * r, y + f[0][1] * r);
  for (let k = 1; k < f.length; k++) c.lineTo(x + f[k][0] * r, y + f[k][1] * r);
  c.closePath();
}
function logo(c, x, y, r, o = {}) {
  c.save();
  if (o.alpha != null) c.globalAlpha *= o.alpha;
  for (let i = 0; i < 5; i++) {
    if (o.only && !o.only.includes(i)) continue;
    facetPath(c, i, x, y, r);
    c.fillStyle = o.mono || (o.cols ? o.cols[i] : FCOL[i]);
    c.fill();
  }
  c.restore();
}
function wordmark(c, x, y, size, color = C.black, align = 'left') {
  return text(c, 'Seismic', x, y, { fam: SERIF, weight: 500, size, color, align, ls: -size * 0.015 });
}
// logo + wordmark lockup centred at (cx, cy); size = wordmark font size
function lockupGeom(c, cx, size) {
  const r = size * 0.72;
  const gap = size * 0.24;
  const ww = textW(c, 'Seismic', { fam: SERIF, weight: 500, size, ls: -size * 0.015 });
  const total = r * LOGO_HW * 2 + gap + ww;
  const x0 = cx - total / 2;
  return { r, lx: x0 + r * LOGO_HW, wx: x0 + r * LOGO_HW * 2 + gap, ww, total };
}
function lockup(c, cx, cy, size, o = {}) {
  const g = lockupGeom(c, cx, size);
  logo(c, g.lx, cy, g.r, o);
  wordmark(c, g.wx, cy + size * 0.06, size, o.textColor || C.black);
  return g;
}

// ---------------------------------------------------------------- UI primitives
function rr(c, x, y, w, h, r) { c.beginPath(); c.roundRect(x, y, w, h, r); }
// Frosted glass panel: blurred copy of whatever is behind (gl), tinted light grey
function glass(c, x, y, w, h, o = {}) {
  const r = o.r ?? 8;
  c.save();
  if (o.alpha != null) c.globalAlpha *= o.alpha;
  c.shadowColor = 'rgba(20,8,16,0.35)';
  c.shadowBlur = 50; c.shadowOffsetY = 18;
  rr(c, x, y, w, h, r);
  c.fillStyle = 'rgba(40,20,30,0.2)';
  c.fill();
  c.shadowColor = 'transparent';
  rr(c, x, y, w, h, r);
  c.clip();
  c.drawImage(gl, 0, 0, W, H);
  c.fillStyle = o.tint || 'rgba(238,235,238,0.82)';
  c.fillRect(x, y, w, h);
  const g = c.createLinearGradient(x, y, x + w, y + h);
  g.addColorStop(0, 'rgba(255,255,255,0.35)');
  g.addColorStop(0.5, 'rgba(255,255,255,0.0)');
  g.addColorStop(1, 'rgba(255,255,255,0.12)');
  c.fillStyle = g;
  c.fillRect(x, y, w, h);
  c.restore();
  c.save();
  if (o.alpha != null) c.globalAlpha *= o.alpha;
  rr(c, x + 0.5, y + 0.5, w - 1, h - 1, r);
  c.strokeStyle = 'rgba(255,255,255,0.55)';
  c.lineWidth = 1;
  c.stroke();
  c.restore();
}
function updateGlass(src) {
  gx.filter = 'blur(5px)';
  gx.drawImage(src, -10, -6, 500, 282);
  gx.filter = 'none';
}
function pill(c, x, y, label, o = {}) {
  const sz = o.size || 19;
  const w = textW(c, label, { size: sz, weight: 450, ls: 0.2 }) + sz * 1.3;
  const h = sz * 1.7;
  const x0 = o.align === 'right' ? x - w : x;
  rr(c, x0, y - h / 2, w, h, 4);
  c.fillStyle = o.bg || 'rgba(255,255,255,0.55)';
  c.fill();
  text(c, label, x0 + w / 2, y + 1, { size: sz, weight: 450, color: o.color || C.g800, ls: 0.2 });
  return w;
}
function checkMark(c, x, y, s, p, col = C.mauve) {
  if (p <= 0) return;
  c.save();
  c.beginPath(); c.arc(x, y, s, -Math.PI / 2, -Math.PI / 2 + TAU * E.outCubic(clamp(p * 1.6)));
  c.strokeStyle = col; c.lineWidth = 2.5; c.stroke();
  const q = E.outCubic(P(p, 0.4, 0.6));
  if (q > 0) {
    c.beginPath(); c.arc(x, y, s * q, 0, TAU); c.fillStyle = col; c.fill();
    c.beginPath();
    c.moveTo(x - s * 0.42, y + s * 0.02);
    c.lineTo(x - s * 0.1, y + s * 0.34);
    c.lineTo(x + s * 0.45, y - s * 0.3);
    c.strokeStyle = '#fff'; c.lineWidth = 3; c.lineCap = 'round'; c.lineJoin = 'round';
    c.stroke();
  }
  c.restore();
}
// line icons for bundle services
function icon(c, kind, x, y, s, col) {
  c.save();
  c.translate(x, y);
  c.scale(s / 24, s / 24);
  c.strokeStyle = col; c.fillStyle = col; c.lineWidth = 1.8; c.lineCap = 'round'; c.lineJoin = 'round';
  c.beginPath();
  if (kind === 'accounts') { c.arc(0, -4, 4.5, 0, TAU); c.moveTo(-8, 10); c.bezierCurveTo(-8, 2, 8, 2, 8, 10); }
  else if (kind === 'payments') { c.moveTo(-9, -4); c.lineTo(9, -4); c.moveTo(5, -8); c.lineTo(9, -4); c.lineTo(5, 0); c.moveTo(9, 5); c.lineTo(-9, 5); c.moveTo(-5, 1); c.lineTo(-9, 5); c.lineTo(-5, 9); }
  else if (kind === 'cards') { c.roundRect(-10, -7, 20, 14, 2.5); c.moveTo(-10, -2); c.lineTo(10, -2); c.moveTo(-6, 3.5); c.lineTo(-1, 3.5); }
  else if (kind === 'fx') { c.arc(-3.5, -1, 6, 0, TAU); c.moveTo(9.5, 2); c.arc(3.5, 2, 6, 0, TAU); }
  else if (kind === 'compliance') { c.moveTo(0, -10); c.lineTo(8, -7); c.lineTo(8, 0); c.bezierCurveTo(8, 6, 3, 9, 0, 10); c.bezierCurveTo(-3, 9, -8, 6, -8, 0); c.lineTo(-8, -7); c.closePath(); c.moveTo(-3.5, 0); c.lineTo(-0.5, 3); c.lineTo(4, -2.5); }
  else if (kind === 'treasury') { c.moveTo(-10, -4); c.lineTo(0, -10); c.lineTo(10, -4); c.closePath(); c.moveTo(-7, -2); c.lineTo(-7, 6); c.moveTo(-2.3, -2); c.lineTo(-2.3, 6); c.moveTo(2.3, -2); c.lineTo(2.3, 6); c.moveTo(7, -2); c.lineTo(7, 6); c.moveTo(-10, 9); c.lineTo(10, 9); }
  else if (kind === 'lock') { c.roundRect(-7, -2, 14, 11, 2); c.moveTo(-4.5, -2); c.lineTo(-4.5, -5); c.arc(0, -5, 4.5, Math.PI, 0); c.lineTo(4.5, -2); }
  c.stroke();
  c.restore();
}
// Deck-style frame labels (as in the brand guidelines pages)
function deckFrame(c, label, al = 1, dark = false) {
  if (al <= 0) return;
  c.save();
  c.globalAlpha *= al;
  const col = dark ? 'rgba(255,255,255,0.55)' : C.g500;
  text(c, label, 64, 58, { fam: SERIF, weight: 400, size: 19, color: col, align: 'left', ls: 0 });
  const wx = W - 64 - textW(c, 'Seismic', { fam: SERIF, weight: 500, size: 19, ls: 0 });
  logo(c, wx - 12, 58, 12, dark ? { mono: 'rgba(255,255,255,0.7)' } : {});
  text(c, 'Seismic', W - 64, 59, { fam: SERIF, weight: 500, size: 19, color: col, align: 'right', ls: 0 });
  text(c, 'All rights reserved', 64, H - 50, { size: 15, weight: 400, color: col, align: 'left', ls: 0 });
  text(c, '© 2026', W - 64, H - 50, { size: 15, weight: 400, color: col, align: 'right', ls: 0 });
  c.restore();
}

// ---------------------------------------------------------------- glow sprites
function glowSprite(col, size = 64) {
  const s = mk(size, size), g = s.getContext('2d');
  const gr = g.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  gr.addColorStop(0, `rgba(${col},1)`);
  gr.addColorStop(0.15, `rgba(${col},0.75)`);
  gr.addColorStop(0.4, `rgba(${col},0.18)`);
  gr.addColorStop(1, `rgba(${col},0)`);
  g.fillStyle = gr; g.fillRect(0, 0, size, size);
  return s;
}
const SPR = {
  white: glowSprite('255,244,236'),
  warm: glowSprite('255,190,130'),
  red: glowSprite('255,70,60'),
  mauve: glowSprite('214,150,190'),
  cool: glowSprite('220,215,255'),
};
function sprite(c, s, x, y, r, a = 1) {
  if (a <= 0.003 || r < 0.3) return;
  c.globalAlpha = a;
  c.drawImage(s, x - r, y - r, r * 2, r * 2);
}

// ---------------------------------------------------------------- 3D camera
const NEAR = 0.6;
function camera(px, py, pz, yaw, pitch, roll = 0, fov = 60) {
  const cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
  const rot = (x, y, z) => {
    const x1 = x * cr - y * sr, y1 = x * sr + y * cr, z1 = z;
    const y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;
    return [x1 * cy + z2 * sy, y2, -x1 * sy + z2 * cy];
  };
  return { px, py, pz, r: rot(1, 0, 0), u: rot(0, 1, 0), f: rot(0, 0, -1), F: (W / 2) / Math.tan(fov * Math.PI / 360) };
}
function lookAt(px, py, pz, tx, ty, tz, roll = 0, fov = 60) {
  const dx = tx - px, dy = ty - py, dz = tz - pz, l = Math.hypot(dx, dy, dz);
  return camera(px, py, pz, Math.atan2(-dx, -dz), Math.asin(dy / l), roll, fov);
}
function toCam(cam, x, y, z) {
  const dx = x - cam.px, dy = y - cam.py, dz = z - cam.pz;
  const r = cam.r, u = cam.u, f = cam.f;
  return [dx * r[0] + dy * r[1] + dz * r[2], dx * u[0] + dy * u[1] + dz * u[2], dx * f[0] + dy * f[1] + dz * f[2]];
}
const projX = (cam, q) => W / 2 + cam.F * q[0] / q[2];
const projY = (cam, q) => H / 2 - cam.F * q[1] / q[2];
function proj(cam, x, y, z) {
  const q = toCam(cam, x, y, z);
  if (q[2] < NEAR) return null;
  return [projX(cam, q), projY(cam, q), q[2]];
}
// clip a camera-space polygon against the near plane, then trace it
function tracePoly(c, cam, qs) {
  let out = [];
  for (let i = 0; i < qs.length; i++) {
    const a = qs[i], b = qs[(i + 1) % qs.length];
    const ain = a[2] >= NEAR, bin = b[2] >= NEAR;
    if (ain) out.push(a);
    if (ain !== bin) {
      const k = (NEAR - a[2]) / (b[2] - a[2]);
      out.push([a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, NEAR]);
    }
  }
  if (out.length < 3) return false;
  c.beginPath();
  c.moveTo(projX(cam, out[0]), projY(cam, out[0]));
  for (let i = 1; i < out.length; i++) c.lineTo(projX(cam, out[i]), projY(cam, out[i]));
  c.closePath();
  return true;
}
function poly3(c, cam, pts) {
  return tracePoly(c, cam, pts.map((p) => toCam(cam, p[0], p[1], p[2])));
}
