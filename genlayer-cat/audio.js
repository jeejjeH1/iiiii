// Synthesizes the soundtrack (120 BPM, F minor) and every SFX from timeline.js.
// Output: build/audio.wav (48 kHz, 16-bit stereo)
require('./timeline.js');
const fs = require('fs');
const path = require('path');
const { BEAT, BAR, T, DURATION, SFX, K } = globalThis.TL;

const SR = 48000;
const N = Math.ceil((DURATION + 0.5) * SR);
const L = new Float32Array(N), R = new Float32Array(N);
const RVL = new Float32Array(N), RVR = new Float32Array(N); // reverb send
const DLL = new Float32Array(N), DLR = new Float32Array(N); // delay send
const TAU = Math.PI * 2;

// ---------------------------------------------------------------- dsp utils
let seed = 777;
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
    else { b0 = al; b1 = 0; b2 = -al; }
    this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
  }
  p(x) {
    const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
    this.x2 = this.x1; this.x1 = x; this.y2 = this.y1; this.y1 = y;
    return y;
  }
}
function blep(t, dt) {
  if (t < dt) { t /= dt; return t + t - t * t - 1; }
  if (t > 1 - dt) { t = (t - 1) / dt; return t * t + t + t + 1; }
  return 0;
}
class Saw { constructor(ph = 0) { this.ph = ph; } p(f) { const dt = f / SR; this.ph += dt; if (this.ph >= 1) this.ph -= 1; return 2 * this.ph - 1 - blep(this.ph, dt); } }

function put(t0, len, gen, { gain = 1, pan = 0, rev = 0, dly = 0, duck = null } = {}) {
  const s0 = Math.floor(t0 * SR), n = Math.floor(len * SR);
  const gl = Math.cos((pan + 1) * Math.PI / 4) * Math.SQRT2, gr = Math.sin((pan + 1) * Math.PI / 4) * Math.SQRT2;
  for (let i = 0; i < n; i++) {
    const k = s0 + i;
    if (k < 0) continue;
    if (k >= N) break;
    let v = gen(i / SR, i) * gain;
    if (duck) v *= duck[k];
    let l = v * gl, r = v * gr;
    if (typeof pan === 'function') { const p = pan(i / SR); l = v * Math.cos((p + 1) * Math.PI / 4) * Math.SQRT2; r = v * Math.sin((p + 1) * Math.PI / 4) * Math.SQRT2; }
    L[k] += l; R[k] += r;
    if (rev) { RVL[k] += l * rev; RVR[k] += r * rev; }
    if (dly) { DLL[k] += l * dly; DLR[k] += r * dly; }
  }
}

// ---------------------------------------------------------------- arrangement (bars of 2 s)
// Fm  Db  Ab  Eb   (three upper voices + bass root)
const PROG = [[65, 68, 72, 41], [65, 68, 73, 37], [63, 68, 72, 44], [63, 67, 70, 39]];
const chordAt = (bar) => PROG[bar % 4];
function sec(bar) {
  if (bar < 2) return 'intro';
  if (bar < 3) return 'build';
  if (bar < 7) return 'drop1';
  if (bar < 11) return 'drive';
  if (bar < 13) return 'air';
  if (bar < 14) return 'build2';
  if (bar < 17) return 'drop2';
  return 'outro';
}
const BARS = 23;
const FULL = new Set(['drop1', 'drive', 'drop2']);
// silence just before the landing and the stamp
const muted = (t) => (t >= K.land - 0.28 && t < K.land) || (t >= K.stamp - 0.22 && t < K.stamp);

// ---------------------------------------------------------------- drums
const kicks = [];
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (muted(t)) continue;
    if (FULL.has(s)) kicks.push([t, 1]);
    else if (s === 'intro' && (b === 0 || b === 2)) kicks.push([t, 0.4]);
    else if (s === 'air' && bar === 12 && b === 0) kicks.push([t, 0.6]);
    else if (s === 'build2' && (b === 0 || b === 2)) kicks.push([t, 0.75]);
    else if (s === 'outro' && bar < 21 && (b === 0 || (b === 2 && bar % 2 === 1))) kicks.push([t, 0.85]);
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
  put(t, 0.5, (x) => {
    const f = 44 + 130 * Math.exp(-x * 30);
    ph += f / SR;
    const body = Math.sin(TAU * ph) * Math.exp(-x * 6) * Math.min(1, x * 800);
    return Math.tanh((body + noise() * Math.exp(-x * 380) * 0.35) * 1.9);
  }, { gain: 0.95 * v });
}
function clap(t, v) {
  const bp = new BQ('bp', 1200, 1.1);
  put(t, 0.38, (x) => {
    let e = Math.exp(-x * 14) * 0.6;
    for (const o of [0, 0.012, 0.024]) if (x >= o) e += Math.exp(-(x - o) * 200);
    return bp.p(noise()) * e * 3;
  }, { gain: 0.48 * v, rev: 0.4 });
}
function hat(t, v, open, pan = 0) {
  const hp = new BQ('hp', 7800, 0.8);
  put(t, open ? 0.26 : 0.06, (x) => hp.p(noise()) * Math.exp(-x * (open ? 15 : 75)), { gain: 0.2 * v, pan });
}
function snare(t, v) {
  const bp = new BQ('bp', 1850, 0.9);
  let ph = 0;
  put(t, 0.22, (x) => { ph += 185 / SR; return (bp.p(noise()) * 2.2 + Math.sin(TAU * ph) * 0.6) * Math.exp(-x * 22); }, { gain: 0.36 * v, rev: 0.25 });
}
function crash(t, v) {
  const hp = new BQ('hp', 4300, 0.7);
  put(t, 3.0, (x) => hp.p(noise()) * Math.exp(-x * 1.7), { gain: 0.22 * v, rev: 0.35 });
}
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (FULL.has(s) && !muted(t)) {
      if (b === 1 || b === 3) clap(t, 1);
      if (!muted(t + BEAT / 2)) hat(t + BEAT / 2, 1, true, 0.15);
      for (const q of [0.25, 0.75]) if (!muted(t + BEAT * q)) hat(t + BEAT * q, s === 'drive' ? 0.8 : 0.5, false, -0.3);
      if (s === 'drop2') hat(t, 0.4, false, 0.3);
    }
    if (s === 'intro') { hat(t + BEAT / 2, 0.45, false, 0.25); if (bar === 1) hat(t + BEAT * 0.75, 0.3, false, -0.2); }
    if (s === 'air') hat(t + BEAT / 2, 0.35, false, 0.2);
    if (s === 'outro' && bar < 21) { if (b === 2) clap(t, 0.8); hat(t + BEAT / 2, 0.5, true, 0.15); }
  }
}
function roll(bar, v = 1, until = 4) {
  const hits = [];
  for (let k = 0; k < 4; k++) hits.push(T(bar, k));
  for (let k = 0; k < 8; k++) hits.push(T(bar, 2 + k * 0.25));
  [...new Set(hits)].filter((t) => t < T(bar, until) && !muted(t)).sort((a, b) => a - b)
    .forEach((t) => { const u = (t - T(bar)) / BAR; snare(t, v * (0.3 + 0.8 * u)); });
}
roll(2, 0.8); roll(10, 0.9); roll(13, 1);
[3, 14].forEach((b) => crash(T(b), 1.2));
[7, 11, 17].forEach((b) => crash(T(b), 0.7));

// ---------------------------------------------------------------- bass + sub
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar), root = ch[3];
  const t0 = T(bar);
  if (FULL.has(s) || s === 'outro' && bar < 21) {
    let sp = 0;
    put(t0, BAR, (x) => { sp += mtof(root) / SR; const e = Math.min(1, x * 50) * Math.min(1, (BAR - x) * 50); return Math.sin(TAU * sp) * e * (muted(t0 + x) ? 0 : 1); },
      { gain: s === 'outro' ? 0.28 : 0.36, duck: SC });
  }
  if (!FULL.has(s)) continue;
  const steps = s === 'drive' ? [1, 2, 3] : [2];
  for (let b = 0; b < 4; b++) {
    for (const st of steps) {
      const t = T(bar, b + st * 0.25);
      if (muted(t)) continue;
      const len = s === 'drive' ? BEAT * 0.22 : BEAT * 0.42;
      const o1 = new Saw(), o2 = new Saw(0.3), lp = new BQ('lp', 900, 1.4);
      const f = mtof(root + 12);
      put(t, len, (x, i) => {
        if (i % 16 === 0) lp.set(240 + 2200 * Math.exp(-x * 26), 2.2);
        const e = Math.min(1, x * 400) * Math.exp(-x * 7) * Math.min(1, (len - x) * 300);
        return lp.p(o1.p(f) * 0.6 + o2.p(f * 1.006) * 0.6) * e;
      }, { gain: 0.5 });
    }
  }
}

// ---------------------------------------------------------------- pads (supersaw)
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  if (bar > 21) continue;
  const notes = [ch[0] - 12, ch[0], ch[1], ch[2], ch[3] + 24];
  let cut = 2400, g = 0.055, duck = SC;
  if (s === 'intro') { cut = 450 + 700 * bar; g = 0.065; duck = null; }
  else if (s === 'build' || s === 'build2') { cut = 1400; g = 0.07; duck = null; }
  else if (s === 'air') { cut = 1700; g = 0.08; duck = null; }
  else if (s === 'outro') { cut = 1900; g = 0.07; duck = bar < 21 ? SC : null; }
  else if (s === 'drop2') { cut = 3800; g = 0.06; }
  const len = bar === 21 ? BAR * 2.4 : BAR;
  notes.forEach((m, ni) => {
    [-0.12, -0.05, 0, 0.05, 0.12].forEach((d, vi) => {
      const o = new Saw((ni * 0.13 + vi * 0.37) % 1), lp = new BQ('lp', cut, 0.8);
      const f = mtof(m + d), t0 = T(bar);
      put(t0, len + 0.02, (x, i) => {
        if ((s === 'build' || s === 'build2') && i % 64 === 0) lp.set(cut + 5000 * (x / BAR) ** 2, 0.8);
        if (s === 'intro' && i % 64 === 0) lp.set(cut + 500 * (x / BAR), 0.8);
        const e = Math.min(1, x * 25) * Math.min(1, (len + 0.02 - x) * 40) * (bar === 21 ? Math.exp(-x * 0.8) : 1);
        return lp.p(o.p(f)) * e * (muted(t0 + x) ? 0.15 : 1);
      }, { gain: g, pan: (vi - 2) * 0.35, rev: 0.28, duck });
    });
  });
}

// ---------------------------------------------------------------- arp pluck
const ARP = [0, 1, 2, 3, 2, 1, 2, 4, 0, 1, 2, 3, 4, 3, 2, 1];
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  if (bar > 20) continue;
  const tones = [ch[0], ch[1], ch[2], ch[0] + 12, ch[1] + 12];
  for (let k = 0; k < 16; k++) {
    const t = T(bar, k * 0.25);
    if (muted(t)) continue;
    if ((s === 'intro' || s === 'air' || s === 'outro') && k % 2) continue;
    const o = new Saw(), lp = new BQ('lp', 3000, 1.5);
    const f = mtof(tones[ARP[k]]);
    const bright = s === 'intro' ? 700 + bar * 500 : s === 'air' ? 1500 : s === 'drive' ? 3000 : 3800;
    put(t, 0.3, (x, i) => {
      if (i % 16 === 0) lp.set(300 + bright * Math.exp(-x * 20), 1.6);
      return lp.p(o.p(f) + (o.ph < 0.5 ? 0.25 : -0.25)) * Math.exp(-x * 12) * Math.min(1, x * 600);
    }, { gain: s === 'intro' ? 0.08 : 0.1, pan: k % 2 ? 0.35 : -0.35, rev: 0.22, dly: 0.4, duck: FULL.has(s) ? SC : null });
  }
}
// bell melody in the 'air' section (consensus)
[[72, 0], [75, 1], [77, 1.5], [80, 2], [77, 3], [75, 3.5], [77, 4], [72, 5], [70, 6], [72, 7]].forEach(([m, b]) => {
  const t = T(11, b);
  put(t, 1.6, (x) => (Math.sin(TAU * mtof(m) * x) + 0.3 * Math.sin(TAU * mtof(m) * 2.76 * x) * Math.exp(-x * 6)) * Math.exp(-x * 2.6) * Math.min(1, x * 300),
    { gain: 0.09, pan: 0.2, rev: 0.5, dly: 0.3 });
});

// ---------------------------------------------------------------- SFX
const SFXF = {
  sub(e) { let ph = 0; put(e.t, 2.2, (x) => { ph += (30 + 30 * Math.exp(-x * 2)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 1.4) * Math.min(1, x * 200); }, { gain: 0.5 * (e.v || 1) }); },
  glitch(e) {
    for (let t = 0; t < e.dur;) {
      const seg = 0.015 + (noise() + 1) * 0.012, kind = Math.floor((noise() + 1) * 1.5), f = 120 + (noise() + 1) * 1500;
      if (kind !== 2) put(e.t + t, seg, (x) => (kind === 0 ? (Math.sin(TAU * f * x) > 0 ? 1 : -1) : Math.round(noise() * 3) / 3), { gain: 0.1 * (e.v || 1), pan: noise() * 0.6 });
      t += seg;
    }
  },
  whoosh(e) {
    const bp = new BQ('bp', 500, 1.1), d = e.dur || 0.3, pn = e.pan ?? 0.7;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) bp.set(400 * Math.pow(10, Math.sin(Math.PI * u)), 1.1);
      return bp.p(noise()) * Math.pow(Math.sin(Math.PI * u), 2) * 2.5;
    }, { gain: 0.5 * (e.v || 1), pan: (x) => -pn + 2 * pn * (x / d), rev: 0.15 });
  },
  pop(e) {
    let ph = 0; const f0 = 320 * (e.f || 1);
    put(e.t, 0.15, (x) => { ph += (f0 + f0 * 2.4 * Math.min(1, x / 0.05)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 30) + noise() * Math.exp(-x * 300) * 0.3; }, { gain: 0.3 * (e.v || 1), rev: 0.12 });
  },
  coin(e) {
    [1, 2.76, 5.4, 8.93].forEach((r, k) => {
      const f = 1650 * r;
      put(e.t + k * 0.004, 0.9, (x) => Math.sin(TAU * f * x) * Math.exp(-x * (5 + k * 3)), { gain: 0.07 * (e.v || 1) / (1 + k * 0.6), pan: 0.15 * (k - 1.5), rev: 0.35 });
    });
    put(e.t + 0.09, 0.7, (x) => Math.sin(TAU * 2480 * x) * Math.exp(-x * 7), { gain: 0.06 * (e.v || 1), rev: 0.35 });
  },
  zap(e) {
    const bp = new BQ('bp', 3000, 3), o = new Saw();
    put(e.t, 0.22, (x, i) => {
      if (i % 16 === 0) bp.set(800 + 5000 * Math.exp(-x * 18) + 1500 * noise(), 3);
      const f = 90 + 40 * Math.sin(x * 400);
      return (bp.p(noise()) * 3 + o.p(f) * 0.5) * Math.exp(-x * 14) * (Math.sin(x * 620) > -0.3 ? 1 : 0.2);
    }, { gain: 0.3 * (e.v || 1), pan: noise() * 0.4, rev: 0.2 });
  },
  hit(e) {
    let ph = 0; const bp = new BQ('bp', 1600, 0.8);
    put(e.t, 0.6, (x) => { ph += (48 + 140 * Math.exp(-x * 26)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 7) * 1.6) + bp.p(noise()) * Math.exp(-x * 18) * 1.4; }, { gain: 0.4 * (e.v || 1), rev: 0.22 });
  },
  riser(e) {
    const bp = new BQ('bp', 300, 2), lp = new BQ('lp', 1000, 1), o = new Saw(), o2 = new Saw(0.5), d = e.dur;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) { bp.set(300 * Math.pow(25, u), 2.5); lp.set(400 + 6000 * u * u, 1.2); }
      const f = 110 * Math.pow(8, u);
      return (bp.p(noise()) * 2.2 + lp.p(o.p(f) + o2.p(f * 1.01)) * 0.25) * u * u * (0.7 + 0.3 * Math.sin(TAU * x * (4 + 20 * u)));
    }, { gain: 0.3 * (e.v || 1), rev: 0.3 });
  },
  fall(e) {
    let ph = 0; const d = e.dur;
    put(e.t, d, (x) => { const u = x / d; ph += (1800 * Math.pow(0.12, u)) / SR; return Math.sin(TAU * ph) * u * (1 - u * 0.3); }, { gain: 0.1 * (e.v || 1), rev: 0.3 });
  },
  impact(e) {
    let ph = 0; const lp = new BQ('lp', 2500, 0.7);
    put(e.t, 2.6, (x) => {
      ph += (26 + 60 * Math.exp(-x * 3)) / SR;
      return Math.tanh((Math.sin(TAU * ph) * Math.exp(-x * 1.9) * 1.4 + lp.p(noise()) * Math.exp(-x * 5) * 1.2) * 1.3);
    }, { gain: 0.6 * (e.v || 1), rev: 0.45 });
    crash(e.t, 0.9 * (e.v || 1));
  },
  boot(e) {
    let ph = 0;
    put(e.t, 0.45, (x) => { ph += (200 + 1800 * (x / 0.45) ** 2) / SR; return Math.sin(TAU * ph) * Math.min(1, x * 40) * (1 - x / 0.45); }, { gain: 0.12, rev: 0.3 });
    [0, 0.08, 0.16, 0.3].forEach((o, k) => put(e.t + 0.2 + o, 0.05, (x) => (Math.sin(TAU * (1200 + 400 * k) * x) > 0 ? 1 : -1) * Math.exp(-x * 60), { gain: 0.05, pan: (k % 2 ? 0.3 : -0.3) }));
  },
  chime(e) {
    [1396.9, 2093, 2793.8].forEach((f, k) => {
      put(e.t + k * 0.06, 1.4, (x) => (Math.sin(TAU * f * x) + 0.25 * Math.sin(TAU * f * 2.01 * x)) * Math.exp(-x * 3.5) * Math.min(1, x * 400), { gain: 0.09 * (e.v || 1), pan: (k - 1) * 0.4, rev: 0.45, dly: 0.2 });
    });
  },
  holo(e) {
    const bp = new BQ('bp', 800, 4); let ph = 0;
    put(e.t, 0.5, (x, i) => {
      const u = x / 0.5;
      if (i % 32 === 0) bp.set(600 + 6000 * u, 4);
      ph += (500 + 1500 * u) / SR;
      return (bp.p(noise()) * 1.5 + Math.sin(TAU * ph) * 0.3) * Math.sin(Math.PI * u);
    }, { gain: 0.16 * (e.v || 1), rev: 0.3, pan: noise() * 0.3 });
  },
  type(e) {
    for (let k = 0; k < e.n; k++) {
      const t = e.t + (k + noise() * 0.3) * e.dur / e.n;
      const bp = new BQ('bp', 2600 + (noise() + 1) * 1500, 2);
      put(t, 0.03, (x) => bp.p(noise()) * Math.exp(-x * 260) * 3, { gain: 0.12 * (e.v || 1), pan: noise() * 0.3 });
    }
  },
  stream(e) {
    for (let t = 0; t < e.dur; t += 0.045) {
      const f = 1800 + (noise() + 1) * 2600, a = Math.sin(Math.PI * t / e.dur);
      put(e.t + t + noise() * 0.01, 0.03, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 120), { gain: 0.035 * a, pan: noise() * 0.8, rev: 0.25 });
    }
  },
  lock(e) {
    const notes = [72, 75, 77, 80, 84, 87];
    const f = mtof(notes[e.n % 6]), o = new Saw(), lp = new BQ('lp', 5000, 2);
    let ph = 0;
    put(e.t, 0.7, (x, i) => {
      if (i % 16 === 0) lp.set(500 + 6000 * Math.exp(-x * 14), 2);
      ph += 80 / SR;
      return lp.p(o.p(f)) * Math.exp(-x * 6) * 0.8 + Math.sin(TAU * ph) * Math.exp(-x * 28) * 0.9 + noise() * Math.exp(-x * 400) * 0.4;
    }, { gain: 0.28 * (e.v || 1), pan: (e.n % 2 ? 0.3 : -0.3), rev: 0.3, dly: 0.25 });
  },
  beam(e) {
    const o = new Saw(), lp = new BQ('lp', 1000, 3), d = 0.55, up = e.up;
    put(e.t - (up ? 0 : 0.25), d, (x, i) => {
      const u = up ? 1 - x / d : x / d;
      if (i % 32 === 0) lp.set(300 + 5000 * u, 3);
      return (lp.p(o.p(110 * Math.pow(4, u)) + noise() * 0.5)) * Math.sin(Math.PI * x / d);
    }, { gain: 0.16 * (e.v || 1), pan: e.pan || 0, rev: 0.35 });
    if (!up) SFXF.pop({ t: e.t + 0.08, f: 1.4, v: 0.6 });
  },
  scramble(e) {
    for (let t = 0; t < e.dur; t += 0.034) {
      const f = 900 + (noise() + 1) * 1400;
      put(e.t + t, 0.022, (x) => (Math.sin(TAU * f * x) > 0 ? 1 : -1) * Math.exp(-x * 120), { gain: 0.045 * (e.v || 1), pan: noise() * 0.6 });
    }
  },
  sparkle(e) {
    for (let k = 0; k < 20; k++) {
      const f = 3000 + (noise() + 1) * 2600, t = e.t + (noise() + 1) * 0.4;
      put(t, 0.15, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 30), { gain: 0.04 * (e.v || 1), pan: noise() * 0.8, rev: 0.5 });
    }
  },
  rev(e) {
    const hp = new BQ('hp', 2500, 0.7), d = e.dur;
    put(e.t, d, (x) => hp.p(noise()) * Math.pow(x / d, 3) * 1.4, { gain: 0.32 * (e.v || 1) });
  },
  stamp(e) {
    let ph = 0; const bp = new BQ('bp', 900, 0.9);
    put(e.t, 0.5, (x) => { ph += (70 + 90 * Math.exp(-x * 30)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 9) * 2) + bp.p(noise()) * Math.exp(-x * 30) * 2; }, { gain: 0.42, rev: 0.2 });
  },
  cash(e) { [0, 0.07, 0.14, 0.21].forEach((o, k) => SFXF.coin({ t: e.t + o, v: (e.v || 1) * (1 - k * 0.15) })); SFXF.chime({ t: e.t + 0.1, v: 0.7 }); },
  jump(e) {
    let ph = 0;
    put(e.t, 0.32, (x) => { ph += (260 + 900 * (x / 0.32)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 7) * Math.min(1, x * 300); }, { gain: 0.14 * (e.v || 1), rev: 0.2 });
  },
  confetti(e) {
    for (let k = 0; k < 40; k++) {
      const t = e.t + Math.abs(noise()) * 0.9;
      const bp = new BQ('bp', 2000 + Math.abs(noise()) * 5000, 3);
      put(t, 0.03, (x) => bp.p(noise()) * Math.exp(-x * 200) * 3, { gain: 0.05 * (e.v || 1), pan: noise() * 0.9, rev: 0.3 });
    }
  },
  slam(e) {
    let ph = 0; const lp = new BQ('lp', 1800, 0.8);
    put(e.t, 1.4, (x) => {
      ph += (38 + 90 * Math.exp(-x * 14)) / SR;
      return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 3.2) * 1.8) + lp.p(noise()) * Math.exp(-x * 12) * 1.2 + Math.sin(TAU * 523 * x) * Math.exp(-x * 5) * 0.15;
    }, { gain: 0.5 * (e.v || 1), rev: 0.4 });
  },
  tick(e) { const f = 2400 * (e.f || 1); put(e.t, 0.05, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 160), { gain: 0.2 * (e.v || 1), rev: 0.1 }); },
};
for (const e of SFX) { if (SFXF[e.type]) SFXF[e.type](e); else console.warn('no sfx', e.type); }

// ---------------------------------------------------------------- delay (dotted 8th ping-pong)
{
  const d = Math.floor(BEAT * 0.75 * SR), fb = 0.36;
  const lpL = new BQ('lp', 3500), lpR = new BQ('lp', 3500);
  for (let i = d; i < N; i++) { DLL[i] += lpR.p(DLR[i - d]) * fb; DLR[i] += lpL.p(DLL[i - d]) * fb; }
  for (let i = d; i < N; i++) { L[i] += DLR[i - d] * 0.5; R[i] += DLL[i - d] * 0.5; RVL[i] += DLR[i - d] * 0.15; RVR[i] += DLL[i - d] * 0.15; }
}
// ---------------------------------------------------------------- reverb (freeverb)
function freeverb(inp, spread) {
  const sc = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0, f: 0 }));
  const aps = [556, 441, 341, 225].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0 }));
  const out = new Float32Array(N), room = 0.87, damp = 0.25;
  for (let k = 0; k < N; k++) {
    const x = inp[k] * 0.015;
    let y = 0;
    for (const c of combs) { const o = c.b[c.i]; c.f = o * (1 - damp) + c.f * damp; c.b[c.i] = x + c.f * room; if (++c.i >= c.b.length) c.i = 0; y += o; }
    for (const a of aps) { const o = a.b[a.i]; a.b[a.i] = y + o * 0.5; if (++a.i >= a.b.length) a.i = 0; y = o - y; }
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
const g = 0.24 / rms;
const fadeIn = Math.floor(0.05 * SR), fadeOut = Math.floor(1.2 * SR);
const out = Buffer.alloc(44 + N * 4);
let pk2 = 0;
for (let i = 0; i < N; i++) {
  let fo = Math.min(1, i / fadeIn);
  if (i > N - fadeOut) fo *= (N - i) / fadeOut;
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
console.log(`audio: ${(N / SR).toFixed(2)}s raw peak ${peak.toFixed(2)} rms ${rms.toFixed(3)} gain ${g.toFixed(2)} out peak ${pk2.toFixed(2)} sfx=${SFX.length}`);
