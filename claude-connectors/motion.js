'use strict';
// Deterministic canvas motion graphics: frame(t) depends only on t, so frames can be
// rendered out of order / in parallel and still match the audio exactly.
const W = 1080, H = 1920;
const { BEAT, BAR, T, DURATION, SCENES, CONNECTORS, CB, FX } = TL;
const FA = 'Vaz', SERIF = 'SSerif', MONO = 'JBM';
const C = {
  cream: '#F5F4ED', paper: '#FFFFFF', ink: '#141413', coral: '#D97757', coralD: '#C15F3C',
  muted: '#87867F', line: '#E3E1D6', text2: '#3D3D3A', green: '#3F9C5A', dark: '#1F1E1B',
};
// Optional page handle shown on the last frame, e.g. '@my.ai.page'
const HANDLE = '';

const canvas = document.getElementById('c');
canvas.width = W; canvas.height = H;
const ctx = canvas.getContext('2d');
const buf = document.createElement('canvas'); buf.width = W; buf.height = H;
const bx = buf.getContext('2d');
const PATHS = {};
for (const k in LOGOS) PATHS[k] = new Path2D(LOGOS[k].d);

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
};
const rnd = (i, j = 0) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
  h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const TAU = Math.PI * 2;
const beatPulse = (t, k = 7) => Math.exp(-((t % BEAT + BEAT) % BEAT) * k);
const pop = (t, a, d = 0.35) => (t < a ? 0 : E.outBack(P(t, a, d)));
// slam: big → 1 with a fast ease, returns [scale, alpha]
const slam = (t, a, from = 1.6, d = 0.22) => (t < a ? [0, 0] : [lerp(from, 1, E.outExpo(P(t, a, d))), clamp((t - a) / 0.06)]);

// ---------------------------------------------------------------- drawing helpers
function rr(c, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  c.beginPath();
  c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r);
  c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
}
function font(c, size, weight = 900, fam = FA) { c.font = `${weight} ${size}px ${fam}`; }
function txt(c, s, x, y, o = {}) {
  const { size = 60, weight = 900, fam = FA, color = C.ink, align = 'center', rtl = true, max = 920, alpha = 1 } = o;
  if (alpha <= 0) return 0;
  c.save();
  c.globalAlpha *= alpha;
  c.direction = rtl ? 'rtl' : 'ltr';
  font(c, size, weight, fam);
  let w = c.measureText(s).width;
  if (w > max) { font(c, size * max / w, weight, fam); w = max; }
  c.textAlign = align; c.textBaseline = 'middle';
  c.fillStyle = color;
  c.fillText(s, x, y);
  c.restore();
  return w;
}
function measure(c, s, size, weight = 900, fam = FA) {
  c.save(); c.direction = 'rtl'; font(c, size, weight, fam); const w = c.measureText(s).width; c.restore(); return w;
}
// text slammed in around its own centre
function slamTxt(c, s, x, y, t, a, o = {}) {
  const [k, al] = slam(t, a, o.from || 1.6);
  if (al <= 0) return;
  c.save(); c.translate(x, y); c.scale(k, k);
  txt(c, s, 0, 0, Object.assign({}, o, { alpha: al * (o.alpha ?? 1) }));
  c.restore();
}
function mark(c, x, y, size, rot = 0, color = C.coral) {
  c.save(); c.translate(x, y); c.rotate(rot); c.scale(size / 24, size / 24); c.translate(-12, -12);
  c.fillStyle = color; c.fill(PATHS.claude);
  c.restore();
}
function shadowCard(c, x, y, w, h, r, { fill = C.paper, border = C.line, sh = 0.12, blur = 40, oy = 14 } = {}) {
  c.save();
  c.shadowColor = `rgba(20,20,19,${sh})`; c.shadowBlur = blur; c.shadowOffsetY = oy;
  rr(c, x, y, w, h, r); c.fillStyle = fill; c.fill();
  c.restore();
  if (border) { rr(c, x, y, w, h, r); c.lineWidth = 2; c.strokeStyle = border; c.stroke(); }
}
function logoTile(c, key, x, y, size, { rot = 0, scale = 1, alpha = 1 } = {}) {
  if (scale <= 0 || alpha <= 0) return;
  c.save(); c.globalAlpha *= alpha; c.translate(x, y); c.rotate(rot); c.scale(scale, scale);
  shadowCard(c, -size / 2, -size / 2, size, size, size * 0.24);
  const g = size * 0.52;
  c.translate(-g / 2, -g / 2); c.scale(g / 24, g / 24);
  c.fillStyle = key === 'claude' ? C.coral : LOGOS[key].hex; c.fill(PATHS[key]);
  c.restore();
}
function check(c, x, y, r, p, col = C.green) {
  if (p <= 0) return;
  c.save();
  c.beginPath(); c.arc(x, y, r * E.outBack(clamp(p * 2)), 0, TAU); c.fillStyle = col; c.fill();
  const q = clamp(p * 2 - 0.6);
  if (q > 0) {
    const pts = [[-0.42, 0.02], [-0.12, 0.32], [0.45, -0.28]];
    c.beginPath(); c.moveTo(x + pts[0][0] * r, y + pts[0][1] * r);
    if (q < 0.4) c.lineTo(x + lerp(pts[0][0], pts[1][0], q / 0.4) * r, y + lerp(pts[0][1], pts[1][1], q / 0.4) * r);
    else {
      c.lineTo(x + pts[1][0] * r, y + pts[1][1] * r);
      const k = (q - 0.4) / 0.6;
      c.lineTo(x + lerp(pts[1][0], pts[2][0], k) * r, y + lerp(pts[1][1], pts[2][1], k) * r);
    }
    c.lineWidth = r * 0.26; c.lineCap = 'round'; c.lineJoin = 'round'; c.strokeStyle = '#fff'; c.stroke();
  }
  c.restore();
}
function burst(c, x, y, p, { n = 12, r0 = 30, r1 = 190, col = C.coral, w = 8 } = {}) {
  if (p <= 0 || p >= 1) return;
  c.save(); c.strokeStyle = col; c.lineCap = 'round';
  for (let i = 0; i < n; i++) {
    const a = i / n * TAU + 0.2;
    const a0 = lerp(r0, r1, E.outCubic(p)), a1 = lerp(r0, r1, E.outCubic(clamp(p * 1.6)));
    c.lineWidth = w * (1 - p);
    c.beginPath(); c.moveTo(x + Math.cos(a) * a0, y + Math.sin(a) * a0); c.lineTo(x + Math.cos(a) * Math.max(a0, a1), y + Math.sin(a) * Math.max(a0, a1)); c.stroke();
  }
  c.globalAlpha = 1 - p; c.lineWidth = 6 * (1 - p);
  c.beginPath(); c.arc(x, y, lerp(r0, r1 * 1.1, E.outCubic(p)), 0, TAU); c.stroke();
  c.restore();
}

// ---------------------------------------------------------------- background
function background(c, t) {
  c.fillStyle = C.cream; c.fillRect(0, 0, W, H);
  // dot grid drifting slowly upward
  c.fillStyle = 'rgba(20,20,19,0.07)';
  const off = (t * 18) % 48;
  for (let y = -48; y < H + 48; y += 48) for (let x = 24; x < W; x += 48) c.fillRect(x - 2, y - off - 2, 4, 4);
  // coral glow pumping with the kick once the beat drops
  const on = t >= T(2) ? 1 : P(t, 0, 0.5) * 0.5;
  const bp = beatPulse(t, 5);
  const blob = (x, y, r, a) => {
    const g = c.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `rgba(217,119,87,${a})`); g.addColorStop(1, 'rgba(217,119,87,0)');
    c.fillStyle = g; c.fillRect(x - r, y - r, 2 * r, 2 * r);
  };
  blob(W / 2 + Math.sin(t * 0.6) * 200, H + 120, 900, (0.18 + 0.14 * bp) * on);
  blob(-100, 300 + Math.cos(t * 0.5) * 120, 600, 0.1 * on);
  // drifting faint Claude marks
  for (let i = 0; i < 9; i++) {
    const x = rnd(i, 1) * W, y = ((rnd(i, 2) * H - t * (30 + rnd(i, 3) * 40)) % H + H) % H;
    c.save(); c.globalAlpha = 0.07 + 0.05 * rnd(i, 4);
    mark(c, x, y, 40 + rnd(i, 5) * 50, t * (rnd(i, 6) - 0.5), C.coral);
    c.restore();
  }
}

// ---------------------------------------------------------------- countdown progress row
function progressRow(c, t) {
  if (t < T(2) || t >= T(12)) return;
  const cur = Math.floor((t - T(2)) / (2 * BAR));
  const a = P(t, T(2, 1), 0.25);
  const y = 300, gap = 120, x0 = W / 2 + 2 * gap;
  c.save(); c.globalAlpha = a;
  c.fillStyle = C.line; c.fillRect(W / 2 - 2 * gap, y - 2, 4 * gap, 4);
  for (let i = 0; i < 5; i++) {
    const x = x0 - i * gap; // RTL: #5 on the right
    const n = 5 - i;
    let r = 34;
    if (i === cur) r = 42 + 5 * beatPulse(t, 6);
    c.beginPath(); c.arc(x, y, r, 0, TAU);
    if (i < cur) { c.fillStyle = C.ink; c.fill(); }
    else if (i === cur) { c.fillStyle = C.coral; c.fill(); }
    else { c.fillStyle = C.cream; c.fill(); c.lineWidth = 3; c.strokeStyle = C.line; c.stroke(); }
    txt(c, String(n), x, y + 3, { size: i === cur ? 46 : 38, weight: 900, color: i <= cur ? '#fff' : C.muted });
  }
  c.restore();
}

// ---------------------------------------------------------------- hook
const HOOK_Y = 760;
function sceneHook(c, t, u) {
  // swarm collapse into the mark, which then zooms through the screen
  const suck = E.inExpo(P(u, BAR + 3 * BEAT, BEAT));
  const zoom = E.inExpo(P(u, BAR + 3.5 * BEAT, BEAT * 0.5));
  const ms = pop(u, 0, 0.45);
  const textA = 1 - P(u, BAR + 2.8 * BEAT, 0.2);
  // shockwave rings from the opening slam
  for (let k = 0; k < 2; k++) {
    const p = P(u, k * 0.12, 0.8);
    if (p > 0 && p < 1) {
      c.save(); c.globalAlpha = 1 - p; c.lineWidth = 8 * (1 - p); c.strokeStyle = C.coral;
      c.beginPath(); c.arc(W / 2, HOOK_Y, 120 + p * 520, 0, TAU); c.stroke(); c.restore();
    }
  }
  // logo swarm orbiting
  const keys = CONNECTORS.map((k) => k.logo);
  keys.forEach((key, i) => {
    const a0 = BAR + i * BEAT * 0.4;
    const sp = pop(u, a0, 0.35);
    if (sp <= 0) return;
    const ang = -Math.PI / 2 + i * TAU / 5 + (u - BAR) * 1.6 + suck * 4;
    const rad = 360 * (1 - suck);
    const x = W / 2 + Math.cos(ang) * rad, y = HOOK_Y + Math.sin(ang) * rad * 0.55;
    logoTile(c, key, x, y, 150, { scale: sp * (1 - suck * 0.9), rot: Math.sin(u * 3 + i) * 0.12 });
  });
  const pulse = 1 + 0.08 * beatPulse(t, 6);
  mark(c, W / 2, HOOK_Y, 300 * ms * pulse * (1 + zoom * 14) * (1 + suck * 0.25), u * 0.6 - (1 - ms) * 2);
  // headline slams on beats 1-3
  c.save(); c.globalAlpha = textA;
  slamTxt(c, '۵ تا کانکتر', W / 2, 1150, t, BEAT, { size: 168, weight: 900, color: C.coral });
  slamTxt(c, 'که باید همین الان', W / 2, 1300, t, 2 * BEAT, { size: 84, weight: 900 });
  slamTxt(c, 'به Claude وصل کنی', W / 2, 1410, t, 3 * BEAT, { size: 84, weight: 900 });
  c.restore();
}

// ---------------------------------------------------------------- connector scene
const ROW_Y = 620, CL_X = 250, LG_X = 830, TILE = 230;
function cablePt(p) {
  const x0 = LG_X - TILE / 2 + 6, x3 = CL_X + TILE / 2 - 6, y = ROW_Y;
  const cp = [[x0, y], [x0 - 120, y + 170], [x3 + 120, y + 170], [x3, y]];
  const m = 1 - p;
  return [
    m * m * m * cp[0][0] + 3 * m * m * p * cp[1][0] + 3 * m * p * p * cp[2][0] + p * p * p * cp[3][0],
    m * m * m * cp[0][1] + 3 * m * m * p * cp[1][1] + 3 * m * p * p * cp[2][1] + p * p * p * cp[3][1],
  ];
}
function cable(c, p, t, live) {
  if (p <= 0) return;
  c.save();
  c.lineCap = 'round'; c.lineJoin = 'round';
  c.beginPath();
  for (let i = 0; i <= 60; i++) { const [x, y] = cablePt(p * i / 60); i ? c.lineTo(x, y) : c.moveTo(x, y); }
  c.lineWidth = 16; c.strokeStyle = C.ink; c.stroke();
  // energy pulses after the plug lands
  if (live > 0) {
    for (let k = 0; k < 4; k++) {
      const q = ((t * 1.1 + k / 4) % 1);
      const [x, y] = cablePt(q);
      c.beginPath(); c.arc(x, y, 7, 0, TAU); c.fillStyle = C.coral; c.globalAlpha = live; c.fill();
    }
    c.globalAlpha = 1;
  }
  // plug head
  const [hx, hy] = cablePt(p);
  const [px, py] = cablePt(Math.max(0, p - 0.02));
  const ang = Math.atan2(hy - py, hx - px);
  c.translate(hx, hy); c.rotate(ang);
  rr(c, -34, -20, 40, 40, 8); c.fillStyle = C.ink; c.fill();
  c.fillStyle = C.coral; c.fillRect(6, -12, 14, 6); c.fillRect(6, 6, 14, 6);
  c.restore();
}
function bubbleUser(c, s, u, a, tType) {
  const sp = pop(u, a, 0.3);
  if (sp <= 0) return;
  const size = 40, tw = Math.min(measure(c, s, size, 700), 760);
  const w = tw + 84, h = 100, right = W - 90, y = 1130;
  c.save(); c.translate(right, y); c.scale(sp, sp); c.translate(-right, -y);
  rr(c, right - w, y - h / 2, w, h, 36); c.fillStyle = C.coral; c.fill();
  c.beginPath(); c.moveTo(right - 30, y + h / 2 - 2); c.lineTo(right + 6, y + h / 2 + 22); c.lineTo(right - 4, y + h / 2 - 26); c.fill();
  const shown = tw * clamp(P(u, a + 0.05, tType));
  c.save(); c.beginPath(); c.rect(right - 42 - shown, y - 50, shown + 4, 100); c.clip();
  txt(c, s, right - 42, y + 2, { size, weight: 700, color: '#fff', align: 'right', max: 760 });
  c.restore();
  c.restore();
}
function replyBody(c, k, x, y, w, p, t) {
  const R = x + w - 40, L = x + 40;
  if (k.kind === 'code') {
    const lines = [
      [['export ', '#C15F3C'], ['function ', '#C15F3C'], ['Landing', '#2563eb'], ['() {', C.ink]],
      [['  return ', '#C15F3C'], ['<Hero ', '#3F9C5A'], ['title', '#7c3aed'], ['="…" />', C.ink]],
      [['}', C.ink]],
    ];
    c.save(); rr(c, L, y, w - 80, 150, 16); c.fillStyle = '#F0EEE6'; c.fill();
    font(c, 28, 500, MONO); c.textAlign = 'left'; c.textBaseline = 'middle'; c.direction = 'ltr';
    let budget = Math.floor(70 * p);
    lines.forEach((ln, i) => {
      let xx = L + 24;
      for (const [s, col] of ln) {
        if (budget <= 0) break;
        const part = s.slice(0, budget); budget -= part.length;
        c.fillStyle = col; c.fillText(part, xx, y + 32 + i * 44); xx += c.measureText(part).width;
      }
    });
    c.restore();
  } else if (k.kind === 'page') {
    c.save(); rr(c, L, y, w - 80, 150, 16); c.fillStyle = '#F0EEE6'; c.fill();
    for (let i = 0; i < 3; i++) {
      const q = E.outCubic(clamp(p * 3 - i));
      const yy = y + 34 + i * 42;
      rr(c, R - 46, yy - 12, 24, 24, 6); c.lineWidth = 3; c.strokeStyle = C.muted; c.stroke();
      rr(c, R - 64 - (360 - i * 60) * q, yy - 8, (360 - i * 60) * q, 16, 8); c.fillStyle = 'rgba(20,20,19,0.18)'; c.fill();
    }
    c.restore();
  } else if (k.kind === 'file') {
    c.save();
    rr(c, R - 360, y, 360, 70, 16); c.fillStyle = '#F0EEE6'; c.fill();
    rr(c, R - 64, y + 13, 40, 44, 6); c.fillStyle = '#dc2626'; c.fill();
    txt(c, 'PDF', R - 44, y + 37, { size: 15, weight: 900, fam: MONO, rtl: false, color: '#fff' });
    txt(c, 'گزارش فروش مهر', R - 80, y + 37, { size: 30, weight: 800, align: 'right' });
    for (let i = 0; i < 2; i++) {
      const q = E.outCubic(clamp(p * 2.5 - i - 0.3));
      const yy = y + 105 + i * 36;
      c.beginPath(); c.arc(R - 10, yy, 7, 0, TAU); c.fillStyle = C.coral; c.fill();
      rr(c, R - 30 - (520 - i * 140) * q, yy - 8, (520 - i * 140) * q, 16, 8); c.fillStyle = 'rgba(20,20,19,0.18)'; c.fill();
    }
    c.restore();
  } else if (k.kind === 'slots') {
    ['10:00', '14:30', '17:00'].forEach((s, i) => {
      const q = pop(p, i * 0.22, 0.35);
      if (q <= 0) return;
      const cx = R - 110 - i * 230, cy = y + 60;
      c.save(); c.translate(cx, cy); c.scale(q, q);
      rr(c, -100, -46, 200, 92, 46); c.fillStyle = i === 0 ? C.coral : '#F0EEE6'; c.fill();
      txt(c, s, 0, 3, { size: 44, weight: 900, color: i === 0 ? '#fff' : C.ink });
      c.restore();
    });
  } else if (k.kind === 'list') {
    ['قرارداد جدید منتظر امضاست', 'جلسه‌ی پنجشنبه جابه‌جا شد', 'فاکتور مهر پرداخت شد'].forEach((s, i) => {
      const q = clamp(p * 3 - i);
      if (q <= 0) return;
      const yy = y + 22 + i * 52;
      c.beginPath(); c.arc(R - 10, yy, 8 * E.outBack(q), 0, TAU); c.fillStyle = C.coral; c.fill();
      const tw = measure(c, s, 34, 700);
      c.save(); c.beginPath(); c.rect(R - 36 - tw * E.outCubic(q), yy - 30, tw * E.outCubic(q) + 4, 60); c.clip();
      txt(c, s, R - 36, yy + 2, { size: 34, weight: 700, align: 'right', color: C.text2 });
      c.restore();
    });
  }
}
function bubbleClaude(c, k, u, t) {
  const a = CB.think * BEAT;
  const sp = pop(u, a, 0.3);
  if (sp <= 0) return;
  const x = 90, y = 1225, w = W - 180, h = 270;
  c.save(); c.translate(x, y); c.scale(sp, sp); c.translate(-x, -y);
  shadowCard(c, x, y, w, h, 36, { sh: 0.1 });
  mark(c, x + w - 56, y + 56, 46, t * 0.8);
  const rp = P(u, CB.reply * BEAT, BEAT * 1.4);
  if (rp <= 0) {
    for (let i = 0; i < 3; i++) {
      const b = Math.abs(Math.sin(t * 9 - i * 0.7));
      c.beginPath(); c.arc(x + w - 120 - i * 34, y + 58 - b * 8, 9, 0, TAU); c.fillStyle = C.muted; c.fill();
    }
  } else {
    txt(c, k.reply, x + w - 100, y + 58, { size: 38, weight: 900, align: 'right', alpha: clamp(rp * 4), max: w - 140 });
    replyBody(c, k, x, y + 100, w, rp, t);
  }
  c.restore();
}
function numberPanel(c, t, u, i) {
  const out = P(u, CB.panelOut * BEAT - 0.06, 0.24);
  if (out >= 1) return;
  const shift = E.inCubic(out) * (W + 700);
  c.save();
  c.translate(-shift, 0);
  c.beginPath(); c.moveTo(-50, 0); c.lineTo(W + 250, 0); c.lineTo(W - 50, H); c.lineTo(-50, H); c.closePath();
  c.fillStyle = C.dark; c.fill();
  // coral trailing edge
  c.beginPath(); c.moveTo(W + 250, 0); c.lineTo(W + 420, 0); c.lineTo(W + 120, H); c.lineTo(W - 50, H); c.closePath();
  c.fillStyle = C.coral; c.fill();
  txt(c, 'کانکتر شماره', W / 2, 600, { size: 60, weight: 800, color: 'rgba(245,244,237,0.75)' });
  const [k] = slam(u + 1e-4, 0, 1.7, 0.2);
  c.save(); c.translate(W / 2, 1010); c.scale(k, k);
  txt(c, String(5 - i), 0, 0, { size: 640, weight: 900, color: C.coral });
  c.restore();
  if (i === 4) txt(c, 'بهترینش!', W / 2, 1420, { size: 70, weight: 900, color: C.cream, alpha: P(u, 0.08, 0.1) });
  c.restore();
}
function sceneConn(c, t, u, i) {
  const k = CONNECTORS[i];
  const b = (x) => x * BEAT;
  // Claude socket tile + incoming connector tile
  logoTile(c, 'claude', CL_X, ROW_Y, TILE, { scale: pop(u, b(CB.tileIn) - 0.05, 0.35) * (1 + 0.06 * beatPulse(t, 6) * (u > b(CB.plug))) });
  const fly = E.outBack(P(u, b(CB.tileIn), 0.4));
  const plug = E.inOut(P(u, b(CB.tileIn) + 0.18, b(CB.plug) - b(CB.tileIn) - 0.18));
  const live = P(u, b(CB.plug), 0.2);
  cable(c, plug, t, live);
  logoTile(c, k.logo, lerp(W + 250, LG_X, fly), ROW_Y, TILE, { rot: (1 - fly) * 0.6 });
  burst(c, CL_X + TILE / 2, ROW_Y, P(u, b(CB.plug), 0.45));
  // "connected" pill above the cable
  const cp = pop(u, b(CB.plug) + 0.04, 0.3);
  if (cp > 0) {
    c.save(); c.translate(W / 2 + 20, ROW_Y - 150); c.scale(cp, cp);
    rr(c, -130, -36, 260, 72, 36); c.fillStyle = '#E6F2E9'; c.fill();
    check(c, 84, 0, 20, P(u, b(CB.plug) + 0.1, 0.3));
    txt(c, 'وصل شد', 52, 3, { size: 36, weight: 900, color: C.green, align: 'right' });
    c.restore();
  }
  // name + benefit
  slamTxt(c, k.name, W / 2, 870, t, t - u + b(CB.title), { size: 112, weight: 700, fam: SERIF, rtl: false, max: 900 });
  const bp = P(u, b(CB.benefit), 0.3);
  if (bp > 0) {
    const tw = measure(c, k.benefit, 52, 800);
    const hl = E.outCubic(P(u, b(CB.benefit) + 0.1, 0.35)) * (tw + 30);
    rr(c, W / 2 + tw / 2 + 15 - hl, 985, hl, 22, 6); c.fillStyle = 'rgba(217,119,87,0.35)'; c.fill();
    txt(c, k.benefit, W / 2, 978 + (1 - E.outCubic(bp)) * 24, { size: 52, weight: 800, color: C.text2, alpha: bp });
  }
  bubbleUser(c, k.ask, u, b(CB.ask), BEAT * 1.3);
  bubbleClaude(c, k, u, t);
}

// ---------------------------------------------------------------- how-to
function cursor(c, x, y, s = 1) {
  c.save(); c.translate(x, y); c.scale(s, s);
  c.beginPath(); c.moveTo(0, 0); c.lineTo(0, 54); c.lineTo(14, 41); c.lineTo(25, 64); c.lineTo(34, 60); c.lineTo(23, 37); c.lineTo(41, 37); c.closePath();
  c.fillStyle = C.ink; c.fill(); c.lineWidth = 3; c.strokeStyle = '#fff'; c.stroke();
  c.restore();
}
function sceneHow(c, t, u) {
  slamTxt(c, 'چطوری وصلش کنم؟', W / 2, 600, t, t - u, { size: 100, weight: 900 });
  const steps = ['Settings', 'Connectors', 'Connect'];
  const ys = [840, 1010, 1180];
  steps.forEach((s, i) => {
    const a = (1 + i) * BEAT - 0.12;
    const sp = pop(u, a, 0.3);
    if (sp <= 0) return;
    const clicked = u >= (1 + i) * BEAT;
    c.save(); c.translate(W / 2, ys[i]); c.scale(sp, sp);
    const w = 520, h = 112;
    shadowCard(c, -w / 2, -h / 2, w, h, 30, clicked && i === 2 ? { fill: C.coral, border: null } : {});
    txt(c, s, 0, 2, { size: 54, weight: 700, fam: SERIF, rtl: false, color: clicked && i === 2 ? '#fff' : C.ink });
    txt(c, String(i + 1), -w / 2 + 54, 4, { size: 40, weight: 900, color: clicked && i === 2 ? '#fff' : C.coral });
    c.restore();
    if (i < 2) {
      const yy = ys[i] + 85;
      c.save(); c.globalAlpha = sp; c.strokeStyle = C.muted; c.lineWidth = 6; c.lineCap = 'round'; c.lineJoin = 'round';
      c.beginPath(); c.moveTo(W / 2 - 16, yy - 6); c.lineTo(W / 2, yy + 8); c.lineTo(W / 2 + 16, yy - 6); c.stroke(); c.restore();
    }
    burst(c, W / 2 + 160, ys[i] + 10, P(u, (1 + i) * BEAT, 0.35), { n: 8, r0: 10, r1: 70, w: 6 });
  });
  // cursor hops between the three steps
  let cy = ys[0], cx = W / 2 + 250;
  for (let i = 0; i < 3; i++) {
    const a = (1 + i) * BEAT;
    if (u >= a - 0.3) { const q = E.inOut(P(u, a - 0.3, 0.26)); cy = lerp(i ? ys[i - 1] : 1500, ys[i], q); cx = W / 2 + 150; }
  }
  if (u > BEAT - 0.35) cursor(c, cx, cy, 1.25 * (1 - 0.15 * Math.exp(-((u % BEAT)) * 12)));
  txt(c, 'روی claude.ai یا اپ Claude', W / 2, 1360, { size: 44, weight: 700, color: C.muted, alpha: P(u, 0.2, 0.3) });
}

// ---------------------------------------------------------------- question + save
function sceneAsk(c, t, u) {
  slamTxt(c, 'تو کدومو اول', W / 2, 700, t, t - u, { size: 110, weight: 900 });
  slamTxt(c, 'وصل می‌کنی؟', W / 2, 840, t, t - u + 0.12, { size: 130, weight: 900, color: C.coral });
  CONNECTORS.forEach((k, i) => {
    const a = BEAT + i * BEAT * 0.5;
    const sp = pop(u, a, 0.35);
    const x = W / 2 + (2 - i) * 180;
    const hop = Math.max(0, Math.sin((u - a) * Math.PI / BEAT)) * 26 * (u > a + 0.3);
    logoTile(c, k.logo, x, 1080 - hop, 150, { scale: sp });
  });
  txt(c, 'تو کامنت بگو', W / 2, 1290, { size: 62, weight: 900, alpha: P(u, 2 * BEAT, 0.2) });
  const a = P(u, 2.3 * BEAT, 0.2);
  const y = 1390 + Math.abs(Math.sin(t * 6.7)) * 16;
  c.save(); c.globalAlpha = a; c.strokeStyle = C.coral; c.lineWidth = 9; c.lineCap = 'round'; c.lineJoin = 'round';
  c.beginPath(); c.moveTo(W / 2, y - 42); c.lineTo(W / 2, y + 18); c.moveTo(W / 2 - 26, y - 8); c.lineTo(W / 2, y + 18); c.lineTo(W / 2 + 26, y - 8); c.stroke();
  c.restore();
}
function sceneSave(c, t, u) {
  // coral circle wipe from the centre
  const wp = E.outCubic(P(u, 0, 0.3));
  c.save(); c.beginPath(); c.arc(W / 2, 900, wp * 1300, 0, TAU); c.fillStyle = C.coral; c.fill(); c.restore();
  if (wp <= 0) return;
  const bp = pop(u, BEAT - 0.1, 0.4);
  const bw = 200, bh = 260, cy = 640;
  c.save(); c.translate(W / 2, cy); c.scale(bp, bp);
  c.beginPath();
  c.moveTo(-bw / 2 + 22, -bh / 2); c.lineTo(bw / 2 - 22, -bh / 2); c.quadraticCurveTo(bw / 2, -bh / 2, bw / 2, -bh / 2 + 22);
  c.lineTo(bw / 2, bh / 2); c.lineTo(0, bh / 2 - 74); c.lineTo(-bw / 2, bh / 2); c.lineTo(-bw / 2, -bh / 2 + 22);
  c.quadraticCurveTo(-bw / 2, -bh / 2, -bw / 2 + 22, -bh / 2); c.closePath();
  c.fillStyle = '#fff'; c.fill();
  c.restore();
  burst(c, W / 2, cy, P(u, BEAT, 0.5), { n: 10, r0: 140, r1: 230, col: '#fff', w: 8 });
  slamTxt(c, 'Save کن', W / 2, 960, t, t - u + 0.08, { size: 160, weight: 900, color: '#fff' });
  slamTxt(c, 'که یادت نره', W / 2, 1110, t, t - u + 2 * BEAT, { size: 78, weight: 800, color: C.cream, from: 1.3 });
  mark(c, W / 2, 1300, 90 * pop(u, 0.3, 0.4), t * 0.8, '#fff');
  if (HANDLE) txt(c, HANDLE, W / 2, 1420, { size: 44, weight: 700, fam: MONO, rtl: false, color: '#fff', alpha: P(u, 0.6, 0.3) });
}

// ---------------------------------------------------------------- post FX + frame
function fxAmt(t, type) {
  let v = 0;
  for (const f of FX) if (f.type === type && t >= f.t && t < f.t + f.dur) v = Math.max(v, (1 - (t - f.t) / f.dur) * (f.amp ?? f.a));
  return v;
}
function frame(t) {
  const c = bx;
  c.setTransform(1, 0, 0, 1, 0, 0); c.globalAlpha = 1;
  background(c, t);
  const sh = fxAmt(t, 'shake'), pu = fxAmt(t, 'punch');
  const fr = Math.floor(t * 60);
  c.save();
  c.translate(W / 2 + (rnd(fr, 1) - 0.5) * 2 * sh, H / 2 + (rnd(fr, 2) - 0.5) * 2 * sh);
  c.scale(1 + pu, 1 + pu);
  c.translate(-W / 2, -H / 2);
  progressRow(c, t);
  for (const sc of SCENES) {
    if (t < sc.t0 || t >= sc.t1) continue;
    const u = t - sc.t0;
    if (sc.id === 'hook') sceneHook(c, t, u);
    else if (sc.id === 'conn') { sceneConn(c, t, u, sc.k); }
    else if (sc.id === 'how') sceneHow(c, t, u);
    else if (sc.id === 'ask') sceneAsk(c, t, u);
    else if (sc.id === 'save') sceneSave(c, t, u);
  }
  c.restore();
  // number panel sits outside the camera shake so its edges never show
  for (const sc of SCENES) if (sc.id === 'conn' && t >= sc.t0 && t < sc.t1) {
    c.save();
    const k = 1 + pu * 1.5;
    c.translate(W / 2 + (rnd(fr, 3) - 0.5) * 2 * sh, H / 2 + (rnd(fr, 4) - 0.5) * 2 * sh); c.scale(k, k); c.translate(-W / 2, -H / 2);
    numberPanel(c, t, t - sc.t0, sc.k);
    c.restore();
  }
  ctx.drawImage(buf, 0, 0);
  const fl = fxAmt(t, 'flash');
  if (fl > 0) { ctx.globalAlpha = fl; ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
  const endFade = P(t, DURATION - 0.25, 0.25);
  if (endFade > 0) { ctx.globalAlpha = endFade; ctx.fillStyle = C.coral; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
}

window.renderOnly = (t) => frame(t);
window.renderFrame = (t, q = 0.95) => { frame(t); return canvas.toDataURL('image/jpeg', q); };
window.DURATION = DURATION;
Promise.all([
  ...[500, 700, 800, 900].map((w) => document.fonts.load(`${w} 40px ${FA}`, 'سلام ۱۲')),
  ...[600, 700].map((w) => document.fonts.load(`${w} 40px ${SERIF}`, 'Notion')),
  document.fonts.load(`500 40px ${MONO}`, 'code'),
]).then(() => { frame(0); window.motionReady = true; });
