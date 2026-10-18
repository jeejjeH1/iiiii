// Synthesizes the soundtrack (120 BPM dark cinematic electronic track + all SFX) from timeline.js.
// Output: build/audio.wav (48 kHz, 16-bit stereo)
require('./timeline.js');
const fs = require('fs');
const path = require('path');
const { BEAT, BAR, T, DURATION, SFX } = globalThis.TL;

const SR = 48000;
const N = Math.ceil((DURATION + 0.4) * SR);
const L = new Float32Array(N), R = new Float32Array(N);
const RVL = new Float32Array(N), RVR = new Float32Array(N); // reverb send
const DLL = new Float32Array(N), DLR = new Float32Array(N); // delay send
const TAU = Math.PI * 2;

// ---------------------------------------------------------------- utils
let seed = 12345;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) | 0; return (seed >>> 0) / 2147483648 - 1; };
const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
class BQ {
  constructor(type, f, q = 0.707) { this.type = type; this.x1 = this.x2 = this.y1 = this.y2 = 0; this.set(f, q); }
  set(f, q = this.q) {
    this.q = q;
    f = Math.min(Math.max(f, 10), SR * 0.45);
    const w = TAU * f / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
    let b0, b1, b2;
    const a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
    if (this.type === 'lp') { b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = b0; }
    else if (this.type === 'hp') { b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = b0; }
    else { b0 = al; b1 = 0; b2 = -al; } // bp
    this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
  }
  p(x) {
    const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
    this.x2 = this.x1; this.x1 = x; this.y2 = this.y1; this.y1 = y;
    return y;
  }
}
// poly-BLEP saw
function blep(t, dt) {
  if (t < dt) { t /= dt; return t + t - t * t - 1; }
  if (t > 1 - dt) { t = (t - 1) / dt; return t * t + t + t + 1; }
  return 0;
}
class Saw { constructor(ph = 0) { this.ph = ph; } p(f) { const dt = f / SR; this.ph += dt; if (this.ph >= 1) this.ph -= 1; return 2 * this.ph - 1 - blep(this.ph, dt); } }

// write a mono generator into the mix
function put(t0, len, gen, { gain = 1, pan = 0, rev = 0, dly = 0, duck = null } = {}) {
  const s0 = Math.floor(t0 * SR), n = Math.floor(len * SR);
  const gl = Math.cos((pan + 1) * Math.PI / 4) * Math.SQRT2, gr = Math.sin((pan + 1) * Math.PI / 4) * Math.SQRT2;
  for (let i = 0; i < n; i++) {
    const k = s0 + i;
    if (k < 0) continue;
    if (k >= N) break;
    let v = gen(i / SR, i) * gain;
    if (duck) v *= duck[k];
    const p = typeof pan === 'function' ? pan(i / SR) : null;
    let l = v * gl, r = v * gr;
    if (p !== null) { l = v * Math.cos((p + 1) * Math.PI / 4) * Math.SQRT2; r = v * Math.sin((p + 1) * Math.PI / 4) * Math.SQRT2; }
    L[k] += l; R[k] += r;
    if (rev) { RVL[k] += l * rev; RVR[k] += r * rev; }
    if (dly) { DLL[k] += l * dly; DLR[k] += r * dly; }
  }
}

// ---------------------------------------------------------------- arrangement (D minor)
const PROG = [[62, 65, 69, 38], [58, 62, 65, 34], [57, 60, 65, 41], [55, 60, 64, 36]]; // Dm Bb F C
const DARK = [[62, 65, 69, 38], [58, 62, 65, 34], [55, 58, 62, 31], [57, 61, 64, 33]]; // Dm Bb Gm A
const chordAt = (bar) => (bar < 8 || (bar >= 24 && bar < 28) ? DARK[bar % 4] : PROG[bar % 4]);
function sec(bar) {
  if (bar < 4) return 'intro';
  if (bar < 6) return 'quake';
  if (bar < 8) return 'reveal';
  if (bar < 12) return 'g1';
  if (bar < 16) return 'g2';
  if (bar < 19) return 'g3';
  if (bar < 20) return 'build';
  if (bar < 24) return 'drop';
  if (bar < 27) return 'g2';
  if (bar < 28) return 'break';
  if (bar < 31) return 'drop';
  return 'outro';
}
const FULL = new Set(['g1', 'g2', 'g3', 'drop']);
const BARS = 36;
const muted = (t) => (t >= T(3, 3.5) && t < T(4)) || (t >= T(19, 3.5) && t < T(20)) || (t >= T(27, 3.5) && t < T(28));

// ---------------------------------------------------------------- drums
const kicks = [];
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (muted(t)) continue;
    if (FULL.has(s)) kicks.push([t, s === 'g1' ? 0.8 : 1]);
    else if (s === 'reveal' && bar === 7 && (b === 0 || b === 2)) kicks.push([t, 0.5]);
    else if (s === 'build') kicks.push([t, 0.85]);
    else if (s === 'break' && b < 2) kicks.push([t, 0.4]);
  }
}
const SC = new Float32Array(N).fill(1);
for (const [t, v] of kicks) {
  const s0 = Math.floor(t * SR), n = Math.floor(0.28 * SR);
  for (let i = 0; i < n && s0 + i < N; i++) {
    const u = i / n;
    SC[s0 + i] = Math.min(SC[s0 + i], 1 - 0.8 * v * Math.pow(1 - u, 2.2));
  }
}
for (const [t, v] of kicks) {
  let ph = 0;
  put(t, 0.55, (x) => {
    const f = 42 + 130 * Math.exp(-x * 30);
    ph += f / SR;
    const body = Math.sin(TAU * ph) * Math.exp(-x * 5.5) * Math.min(1, x * 800);
    const click = noise() * Math.exp(-x * 380) * 0.3;
    return Math.tanh((body + click) * 2);
  }, { gain: 0.95 * v });
}
function clap(t, v) {
  const bp = new BQ('bp', 1200, 1.1);
  put(t, 0.4, (x) => {
    let e = Math.exp(-x * 14) * 0.6;
    for (const o of [0, 0.012, 0.024]) if (x >= o) e += Math.exp(-(x - o) * 220);
    return bp.p(noise()) * e * 3;
  }, { gain: 0.45 * v, rev: 0.45 });
}
function hat(t, v, open, pan = 0) {
  const hp = new BQ('hp', 8000, 0.8);
  put(t, open ? 0.25 : 0.06, (x) => hp.p(noise()) * Math.exp(-x * (open ? 16 : 80)), { gain: 0.2 * v, pan });
}
function snare(t, v) {
  const bp = new BQ('bp', 1800, 0.9);
  let ph = 0;
  put(t, 0.22, (x) => { ph += 180 / SR; return (bp.p(noise()) * 2.2 + Math.sin(TAU * ph) * 0.6) * Math.exp(-x * 22); }, { gain: 0.36 * v, rev: 0.3 });
}
function crash(t, v) {
  const hp = new BQ('hp', 4200, 0.7);
  put(t, 3.2, (x) => hp.p(noise()) * Math.exp(-x * 1.7), { gain: 0.22 * v, rev: 0.35 });
}
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (s === 'intro' && bar >= 1) { hat(t, 0.35, false, 0.3); hat(t + BEAT / 2, 0.22, false, -0.3); }
    if (!FULL.has(s) || muted(t)) continue;
    if (s !== 'g1' && (b === 1 || b === 3)) clap(t, 1);
    if (!muted(t + BEAT / 2)) hat(t + BEAT / 2, 1, true, 0.15);
    if (s === 'g3' || s === 'drop') for (const q of [0.25, 0.75]) hat(t + BEAT * q, 0.55, false, -0.3);
    if (s === 'drop') hat(t, 0.35, false, 0.3);
  }
}
function roll(bar, v = 1) {
  const hits = [];
  for (let k = 0; k < 4; k++) hits.push(T(bar, k));
  for (let k = 0; k < 6; k++) hits.push(T(bar, 2 + k * 0.25));
  [...new Set(hits)].sort((a, b) => a - b).forEach((t) => { if (!muted(t)) { const u = (t - T(bar)) / BAR; snare(t, v * (0.3 + 0.8 * u)); } });
}
roll(19, 1); roll(27, 0.8);
[8, 12, 16, 24].forEach((b) => crash(T(b), 0.7));

// ---------------------------------------------------------------- drone (intro / quake)
{
  const lp = new BQ('lp', 300, 0.7);
  let p1 = 0, p2 = 0;
  const len = T(8);
  put(0, len, (x, i) => {
    if (i % 64 === 0) lp.set(180 + 500 * clamp01(x / T(4)) + 300 * Math.sin(x * 0.7) ** 2, 0.9);
    p1 += mtof(26) / SR; p2 += mtof(38.07) / SR;
    const e = Math.min(1, x / 1.5) * (x > T(6) ? Math.max(0, 1 - (x - T(6)) / T(2)) : 1) * (muted(x) ? 0.25 : 1);
    return (Math.sin(TAU * p1) * 0.7 + Math.sin(TAU * p2) * 0.35 + lp.p(noise()) * 0.6) * e;
  }, { gain: 0.32, rev: 0.25 });
}
function clamp01(x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

// ---------------------------------------------------------------- bass + sub
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), root = chordAt(bar)[3];
  const t0 = T(bar);
  if (FULL.has(s) || s === 'build' || s === 'break' || s === 'quake') {
    let sp = 0;
    put(t0, BAR, (x) => { sp += mtof(root) / SR; const e = Math.min(1, x * 50) * Math.min(1, (BAR - x) * 50); return Math.sin(TAU * sp) * e * (muted(t0 + x) ? 0 : 1); },
      { gain: s === 'quake' || s === 'break' ? 0.25 : 0.4, duck: FULL.has(s) ? SC : null });
  }
  if (!FULL.has(s) && s !== 'build') continue;
  const steps = s === 'g3' || s === 'drop' || s === 'build' ? [1, 2, 3] : [2];
  for (let b = 0; b < 4; b++) {
    for (const st of steps) {
      const t = T(bar, b + st * 0.25);
      if (muted(t)) continue;
      const len = steps.length > 1 ? BEAT * 0.2 : BEAT * 0.4;
      const o1 = new Saw(), o2 = new Saw(0.3), lp = new BQ('lp', 900, 1.4);
      const f = mtof(root + 12);
      const bright = s === 'drop' ? 2800 : 1800;
      put(t, len, (x, i) => {
        if (i % 16 === 0) lp.set(220 + bright * Math.exp(-x * 26), 2.2);
        const e = Math.min(1, x * 400) * Math.exp(-x * 7) * Math.min(1, (len - x) * 300);
        return lp.p(o1.p(f) * 0.6 + o2.p(f * 1.006) * 0.6) * e;
      }, { gain: 0.5 });
    }
  }
}

// ---------------------------------------------------------------- pads (supersaw)
for (let bar = 4; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  const notes = [ch[0], ch[1], ch[2], ch[0] + 12, ch[3] + 24];
  let cut = 2200, g = 0.055, duck = SC;
  if (s === 'quake') { cut = 600 + (bar - 4) * 500; g = 0.07; duck = null; }
  else if (s === 'reveal') { cut = 1800; g = 0.07; duck = null; }
  else if (s === 'break' || s === 'build') { cut = 1500; g = 0.07; duck = null; }
  else if (s === 'outro') { cut = 1700; g = 0.075; duck = null; }
  else if (s === 'drop') { cut = 3600; g = 0.065; }
  else if (s === 'g1') { cut = 1500; }
  const last = bar === BARS - 1;
  const len = last ? BAR + 0.3 : BAR;
  notes.forEach((m, ni) => {
    [-0.12, -0.05, 0, 0.05, 0.12].forEach((d, vi) => {
      const o = new Saw((ni * 0.13 + vi * 0.37) % 1);
      const lp = new BQ('lp', cut, 0.8);
      const f = mtof(m + d);
      const t0 = T(bar);
      put(t0, len + 0.02, (x, i) => {
        if ((s === 'build' || s === 'break') && i % 64 === 0) lp.set(cut + 5000 * (x / BAR) ** 2, 0.8);
        const e = Math.min(1, x * 30) * Math.min(1, (len + 0.02 - x) * 40) * (last ? Math.exp(-x * 1.4) : 1) * (s === 'outro' ? Math.max(0.15, 1 - (bar - 31) * 0.18) : 1);
        return lp.p(o.p(f)) * e * (muted(t0 + x) ? 0 : 1);
      }, { gain: g, pan: (vi - 2) * 0.35, rev: 0.3, duck });
    });
  });
}

// ---------------------------------------------------------------- arp pluck
const ARP = [0, 2, 1, 3, 2, 4, 3, 1, 0, 2, 1, 3, 4, 3, 2, 1];
for (let bar = 6; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  if (s === 'outro' && bar > 33) continue;
  const tones = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[0] + 24, ch[1] + 24];
  for (let k = 0; k < 16; k++) {
    const t = T(bar, k * 0.25);
    if (muted(t)) continue;
    if ((s === 'reveal' || s === 'break' || s === 'outro') && k % 2) continue;
    const m = tones[ARP[k]];
    const o = new Saw(), lp = new BQ('lp', 3000, 1.5);
    const f = mtof(m);
    const bright = s === 'reveal' ? 1200 : s === 'g1' ? 1800 : s === 'outro' || s === 'break' ? 1400 : s === 'drop' ? 4200 : 2800;
    put(t, 0.3, (x, i) => {
      if (i % 16 === 0) lp.set(300 + bright * Math.exp(-x * 22), 1.6);
      const sq = (o.ph < 0.5 ? 1 : -1) * 0.18;
      return lp.p(o.p(f) + sq) * Math.exp(-x * 12) * Math.min(1, x * 600);
    }, { gain: s === 'reveal' || s === 'outro' ? 0.08 : 0.11, pan: k % 2 ? 0.35 : -0.35, rev: 0.25, dly: 0.4, duck: FULL.has(s) ? SC : null });
  }
}

// ---------------------------------------------------------------- SFX
const SFXF = {
  click(e) {
    put(e.t - 0.004, 0.06, (x) => Math.sin(TAU * 3400 * x) * Math.exp(-x * 260) * 0.7 + noise() * Math.exp(-x * 600) * 0.6 + Math.sin(TAU * 950 * x) * Math.exp(-x * 110) * 0.6, { gain: 0.42 * (e.v || 1), rev: 0.08 });
  },
  tick(e) {
    const f = 2400 * (e.f || 1);
    put(e.t, 0.05, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 160), { gain: 0.2 * (e.v || 1), rev: 0.1 });
  },
  pop(e) {
    let ph = 0; const f0 = 320 * (e.f || 1);
    put(e.t, 0.15, (x) => { ph += (f0 + f0 * 2.4 * Math.min(1, x / 0.05)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 30) + noise() * Math.exp(-x * 300) * 0.3; }, { gain: 0.32 * (e.v || 1), rev: 0.12 });
  },
  hit(e) {
    let ph = 0; const bp = new BQ('bp', 1600, 0.8);
    put(e.t, 0.6, (x) => { ph += (48 + 140 * Math.exp(-x * 26)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 7) * 1.6) + bp.p(noise()) * Math.exp(-x * 18) * 1.4; }, { gain: 0.42 * (e.v || 1), rev: 0.22 });
  },
  impact(e) {
    let ph = 0; const lp = new BQ('lp', 2500, 0.7);
    put(e.t, 2.6, (x) => {
      ph += (26 + 60 * Math.exp(-x * 3)) / SR;
      const sub = Math.sin(TAU * ph) * Math.exp(-x * 1.9);
      const crack = lp.p(noise()) * Math.exp(-x * 5) * 1.2;
      return Math.tanh((sub * 1.4 + crack) * 1.3);
    }, { gain: 0.62 * (e.v || 1), rev: 0.45 });
    crash(e.t, 0.9 * (e.v || 1));
  },
  whoosh(e) {
    const bp = new BQ('bp', 500, 1.1); const d = e.dur || 0.3; const pn = e.pan ?? 0.7;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) bp.set(400 * Math.pow(10, Math.sin(Math.PI * u) * 1.0), 1.1);
      return bp.p(noise()) * Math.pow(Math.sin(Math.PI * u), 2) * 2.5;
    }, { gain: 0.5 * (e.v || 1), pan: (x) => -pn + 2 * pn * (x / d), rev: 0.15 });
  },
  rev(e) {
    const hp = new BQ('hp', 2500, 0.7); const d = e.dur;
    put(e.t, d, (x) => { const u = x / d; return hp.p(noise()) * Math.pow(u, 3) * 1.4; }, { gain: 0.35 * (e.v || 1) });
  },
  riser(e) {
    const bp = new BQ('bp', 300, 2), lp = new BQ('lp', 1000, 1); const o = new Saw(), o2 = new Saw(0.5); const d = e.dur;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) { bp.set(300 * Math.pow(25, u), 2.5); lp.set(400 + 6000 * u * u, 1.2); }
      const f = 110 * Math.pow(8, u);
      const trem = 0.7 + 0.3 * Math.sin(TAU * x * (4 + 20 * u));
      return (bp.p(noise()) * 2.2 + lp.p(o.p(f) + o2.p(f * 1.01)) * 0.25) * u * u * trem;
    }, { gain: 0.32 * (e.v || 1), rev: 0.3 });
  },
  lock(e) {
    const notes = [69, 72, 74, 76, 79, 81, 84, 88];
    const f = mtof(notes[e.n % 8]); const o = new Saw(), lp = new BQ('lp', 5000, 2);
    let ph = 0;
    put(e.t, 0.7, (x, i) => {
      if (i % 16 === 0) lp.set(500 + 6000 * Math.exp(-x * 14), 2);
      ph += 80 / SR;
      return lp.p(o.p(f) + o.p(f * 2) * 0) * Math.exp(-x * 6) * 0.8 + Math.sin(TAU * ph) * Math.exp(-x * 28) * 0.9 + noise() * Math.exp(-x * 400) * 0.4;
    }, { gain: 0.3 * (e.v || 1), pan: (e.n % 2 ? 0.3 : -0.3), rev: 0.3, dly: 0.25 });
  },
  type(e) {
    for (let k = 0; k < e.n; k++) {
      const t = e.t + (k + (noise() * 0.3)) * e.dur / e.n;
      const bp = new BQ('bp', 2600 + (noise() + 1) * 1500, 2);
      put(t, 0.03, (x) => bp.p(noise()) * Math.exp(-x * 260) * 3, { gain: 0.13 * (e.v || 1), pan: noise() * 0.3 });
    }
  },
  scramble(e) {
    for (let t = 0; t < e.dur; t += 0.032) {
      const f = 900 + (noise() + 1) * 1400;
      put(e.t + t, 0.022, (x) => (Math.sin(TAU * f * x) > 0 ? 1 : -1) * Math.exp(-x * 120), { gain: 0.05 * (e.v || 1), pan: noise() * 0.5 });
    }
  },
  error(e) {
    const lp = new BQ('lp', 2200, 0.8);
    put(e.t, 0.3, (x) => { const f = x < 0.13 ? 233 : 185; const sq = Math.sin(TAU * f * x) > 0 ? 1 : -1; return lp.p(sq) * Math.min(1, (0.3 - x) * 60) * (x % 0.13 < 0.115 ? 1 : 0.2); }, { gain: 0.16 * (e.v || 1), rev: 0.1 });
  },
  chime(e) {
    [1318.5, 1975.5, 2637].forEach((f, k) => {
      put(e.t + k * 0.06, 1.4, (x) => (Math.sin(TAU * f * x) + 0.25 * Math.sin(TAU * f * 2.01 * x)) * Math.exp(-x * 3.5) * Math.min(1, x * 400), { gain: 0.1 * (e.v || 1), pan: (k - 1) * 0.4, rev: 0.45, dly: 0.2 });
    });
  },
  sparkle(e) {
    for (let k = 0; k < 18; k++) {
      const f = 3000 + (noise() + 1) * 2500, t = e.t + (noise() + 1) * 0.35;
      put(t, 0.15, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 30), { gain: 0.045 * (e.v || 1), pan: noise() * 0.8, rev: 0.5 });
    }
  },
  glitch(e) {
    for (let t = 0; t < e.dur;) {
      const seg = 0.015 + (noise() + 1) * 0.012, kind = Math.floor((noise() + 1) * 1.5), f = 120 + (noise() + 1) * 1500;
      if (kind !== 2) put(e.t + t, seg, (x) => (kind === 0 ? (Math.sin(TAU * f * x) > 0 ? 1 : -1) : Math.round(noise() * 3) / 3), { gain: 0.11 * (e.v || 1), pan: noise() * 0.6 });
      t += seg;
    }
  },
  alarm(e) {
    const lp = new BQ('lp', 3000, 0.7);
    put(e.t, 0.6, (x) => { const f = Math.floor(x / 0.12) % 2 ? 660 : 880; return lp.p(Math.sin(TAU * f * x) > 0 ? 1 : -1) * Math.min(1, (0.6 - x) * 20); }, { gain: 0.09 * (e.v || 1), rev: 0.25 });
  },
  sub(e) {
    let ph = 0;
    put(e.t, 2.2, (x) => { ph += (30 + 30 * Math.exp(-x * 2)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 1.4) * Math.min(1, x * 200); }, { gain: 0.5 * (e.v || 1) });
  },
  brick(e) {
    let ph = 0; const lp = new BQ('lp', 900, 0.7);
    put(e.t, 0.45, (x) => { ph += (45 + 70 * Math.exp(-x * 20)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 10) * 2) + lp.p(noise()) * Math.exp(-x * 14) * 1.6; }, { gain: 0.48 * (e.v || 1), rev: 0.15 });
  },
  slash(e) {
    const bp = new BQ('bp', 6000, 1.5);
    put(e.t - 0.05, 0.3, (x, i) => { if (i % 32 === 0) bp.set(7000 * Math.exp(-x * 12) + 600, 1.5); return bp.p(noise()) * Math.exp(-x * 9) * 3; }, { gain: 0.4 * (e.v || 1), rev: 0.2 });
    SFXF.hit({ t: e.t, v: 0.7 });
  },
};
Object.assign(SFXF, {
  thump(e) {
    let ph = 0; const lp = new BQ('lp', 400, 0.7);
    put(e.t, 0.5, (x) => { ph += (40 + 50 * Math.exp(-x * 25)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 8) * 1.8) + lp.p(noise()) * Math.exp(-x * 30) * 0.5; }, { gain: 0.6 * (e.v || 1), rev: 0.1 });
  },
  scan(e) {
    const bp = new BQ('bp', 800, 6); const d = e.dur;
    put(e.t, d, (x, i) => { const u = x / d; if (i % 32 === 0) bp.set(600 + 3000 * u, 8); return bp.p(noise()) * Math.sin(Math.PI * u) * 2 + Math.sin(TAU * (1200 + 600 * u) * x) * 0.05 * Math.sin(Math.PI * u); }, { gain: 0.25 * (e.v || 1), pan: (x) => -0.7 + 1.4 * x / d, rev: 0.4 });
  },
  quake(e) {
    const lp = new BQ('lp', 140, 0.9), lp2 = new BQ('lp', 900, 0.7); let ph = 0;
    put(e.t, 4.2, (x) => {
      ph += (24 + 18 * Math.exp(-x * 1.5)) / SR;
      const env = Math.min(1, x * 40) * Math.exp(-x * 0.85);
      const trem = 0.65 + 0.35 * Math.sin(TAU * x * (9 - x * 1.5));
      const crackle = (noise() > 0.995 ? noise() * 2 : 0) * Math.exp(-x * 1.2);
      return Math.tanh((lp.p(noise()) * 5 * trem + Math.sin(TAU * ph) * 1.2 + lp2.p(crackle) * 2) * env * 1.4);
    }, { gain: 0.7 * (e.v || 1), rev: 0.3 });
  },
  shatter(e) {
    for (let k = 0; k < 40; k++) {
      const t = e.t + Math.pow((noise() + 1) / 2, 2) * 0.9, f = 2500 + (noise() + 1) * 3500;
      const bp = new BQ('bp', f, 14);
      put(t, 0.25, (x) => bp.p(noise()) * Math.exp(-x * 18) * 6 + Math.sin(TAU * f * x) * Math.exp(-x * 30) * 0.4, { gain: 0.05 * (e.v || 1), pan: noise() * 0.9, rev: 0.4 });
    }
    const hp = new BQ('hp', 3000, 0.7);
    put(e.t, 0.5, (x) => hp.p(noise()) * Math.exp(-x * 9), { gain: 0.3 * (e.v || 1), rev: 0.3 });
  },
  shimmer(e) {
    [74, 77, 81, 86, 89, 93, 98].forEach((m, k) => {
      const f = mtof(m);
      put(e.t + k * 0.07, 1.6, (x) => (Math.sin(TAU * f * x) + 0.3 * Math.sin(TAU * f * 2.003 * x)) * Math.exp(-x * 2.6) * Math.min(1, x * 300), { gain: 0.05 * (e.v || 1), pan: (k / 3 - 1) * 0.6, rev: 0.6, dly: 0.3 });
    });
  },
  glass(e) {
    SFXF.whoosh({ t: e.t - 0.05, dur: 0.35, v: 0.25 * (e.v || 1), pan: 0.3 });
    [1760, 2637].forEach((f, k) => put(e.t + k * 0.04, 0.9, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 6) * Math.min(1, x * 500), { gain: 0.07 * (e.v || 1), pan: k ? 0.3 : -0.3, rev: 0.5 }));
  },
  count(e) {
    let t = 0, k = 0;
    while (t < e.dur) {
      const f = 2000 + k * 40;
      put(e.t + t, 0.03, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 200), { gain: 0.1 * (e.v || 1), pan: (k % 2 ? 0.2 : -0.2) });
      t += 0.025 + Math.pow(t / e.dur, 2.5) * 0.18; k++;
    }
  },
  beam(e) {
    const bp = new BQ('bp', 400, 3); const d = e.dur; let ph = 0;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) bp.set(300 + 5000 * Math.pow(u, 0.6), 3);
      ph += (180 + 1800 * (1 - Math.exp(-u * 4))) / SR;
      const env = Math.min(1, x * 30) * Math.exp(-u * 2.2);
      return (bp.p(noise()) * 2 + Math.sin(TAU * ph) * 0.35) * env;
    }, { gain: 0.4 * (e.v || 1), pan: (x) => -0.6 + 1.2 * x / d, rev: 0.45, dly: 0.2 });
  },
});
for (const e of SFX) { if (SFXF[e.type]) SFXF[e.type](e); else console.warn('no sfx', e.type); }

// ---------------------------------------------------------------- delay (dotted 8th ping-pong)
{
  const d = Math.floor(BEAT * 0.75 * SR), fb = 0.38;
  const lpL = new BQ('lp', 3500), lpR = new BQ('lp', 3500);
  for (let i = d; i < N; i++) {
    DLL[i] += lpR.p(DLR[i - d]) * fb;
    DLR[i] += lpL.p(DLL[i - d]) * fb;
  }
  for (let i = d; i < N; i++) { L[i] += DLR[i - d] * 0.5; R[i] += DLL[i - d] * 0.5; RVL[i] += DLR[i - d] * 0.15; RVR[i] += DLL[i - d] * 0.15; }
}
// ---------------------------------------------------------------- reverb (freeverb)
function freeverb(inp, spread) {
  const sc = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0, f: 0 }));
  const aps = [556, 441, 341, 225].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0 }));
  const out = new Float32Array(N), room = 0.86, damp = 0.25;
  for (let k = 0; k < N; k++) {
    const x = inp[k] * 0.015;
    let y = 0;
    for (const c of combs) {
      const o = c.b[c.i];
      c.f = o * (1 - damp) + c.f * damp;
      c.b[c.i] = x + c.f * room;
      if (++c.i >= c.b.length) c.i = 0;
      y += o;
    }
    for (const a of aps) {
      const o = a.b[a.i];
      a.b[a.i] = y + o * 0.5;
      if (++a.i >= a.b.length) a.i = 0;
      y = o - y;
    }
    out[k] = y;
  }
  return out;
}
const rl = freeverb(RVL, 0), rrv = freeverb(RVR, 23);
for (let i = 0; i < N; i++) { L[i] += rl[i] * 2.2; R[i] += rrv[i] * 2.2; }

// ---------------------------------------------------------------- master
const hpL = new BQ('hp', 25), hpR = new BQ('hp', 25);
let peak = 0, sum = 0;
for (let i = 0; i < N; i++) { L[i] = hpL.p(L[i]); R[i] = hpR.p(R[i]); peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i])); sum += L[i] * L[i] + R[i] * R[i]; }
const rms = Math.sqrt(sum / (2 * N));
// target ~ -12 dBFS RMS, with soft clipping on peaks
const g = 0.25 / rms;
const fadeOut = Math.floor(0.4 * SR);
const out = Buffer.alloc(44 + N * 4);
let pk2 = 0;
for (let i = 0; i < N; i++) {
  let fo = 1;
  if (i > N - fadeOut) fo = (N - i) / fadeOut;
  const l = Math.tanh(L[i] * g) * 0.97 * fo, r = Math.tanh(R[i] * g) * 0.97 * fo;
  pk2 = Math.max(pk2, Math.abs(l), Math.abs(r));
  out.writeInt16LE(Math.round(l * 32767), 44 + i * 4);
  out.writeInt16LE(Math.round(r * 32767), 46 + i * 4);
}
out.write('RIFF', 0); out.writeUInt32LE(36 + N * 4, 4); out.write('WAVE', 8);
out.write('fmt ', 12); out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(2, 22);
out.writeUInt32LE(SR, 24); out.writeUInt32LE(SR * 4, 28); out.writeUInt16LE(4, 32); out.writeUInt16LE(16, 34);
out.write('data', 36); out.writeUInt32LE(N * 4, 40);
fs.mkdirSync(path.join(__dirname, 'build'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'build', 'audio.wav'), out);
console.log(`audio: ${(N / SR).toFixed(2)}s raw peak ${peak.toFixed(2)} rms ${rms.toFixed(3)} gain ${g.toFixed(2)} out peak ${pk2.toFixed(2)}  sfx=${SFX.length}`);
