// Synthesizes the soundtrack (120 BPM cinematic/electronic score in D minor + all SFX) from timeline.js.
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
const PROG = [[62, 65, 69, 38], [62, 65, 70, 34], [60, 65, 69, 41], [60, 64, 67, 36]];   // Dm Bb F C
const TENS = [[62, 65, 69, 38], [62, 65, 70, 34], [62, 67, 70, 43], [61, 64, 69, 33]];   // Dm Bb Gm A
const chordAt = (bar) => (bar >= 6 && bar < 11 ? TENS[(bar - 6) % 4] : PROG[bar % 4]);
function sec(bar) {
  if (bar < 2) return 'intro';
  if (bar < 4) return 'ignite';
  if (bar < 5) return 'build';
  if (bar < 6) return 'reveal';
  if (bar < 7) return 'break';
  if (bar < 10) return 'tension';
  if (bar < 11) return 'break';
  if (bar < 21) return 'drop';
  if (bar < 22) return 'break';
  if (bar < 23) return 'stabs';
  if (bar < 25) return 'drop';
  return 'outro';
}
const FULL = new Set(['reveal', 'tension', 'drop']);
const BARS = 28;
// breath / stop-time windows (drums + bass drop out)
const muted = (t) => (t >= T(8, 1.5) && t < T(8, 2)) || (t >= T(16, 1.5) && t < T(16, 2)) || (t >= T(24, 2) && t < T(25));

// ---------------------------------------------------------------- drums
const kicks = [];
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (muted(t)) continue;
    if (FULL.has(s)) kicks.push([t, 1]);
    else if (s === 'ignite' && (b === 0 || b === 2)) kicks.push([t, 0.55]);
    else if (s === 'build' && b === 0) kicks.push([t, 0.7]);
  }
}
kicks.push([T(22), 1], [T(22, 2), 1], [T(22, 3), 1], [T(25), 1.1]);
// sidechain envelope
const SC = new Float32Array(N).fill(1);
for (const [t, v] of kicks) {
  const s0 = Math.floor(t * SR), n = Math.floor(0.28 * SR);
  for (let i = 0; i < n && s0 + i < N; i++) {
    const u = i / n;
    const g = 1 - 0.8 * Math.min(1, v) * Math.pow(1 - u, 2.2);
    SC[s0 + i] = Math.min(SC[s0 + i], g);
  }
}
for (const [t, v] of kicks) {
  let ph = 0;
  put(t, 0.55, (x) => {
    const f = 44 + 130 * Math.exp(-x * 30);
    ph += f / SR;
    const body = Math.sin(TAU * ph) * Math.exp(-x * 6) * Math.min(1, x * 800);
    const click = noise() * Math.exp(-x * 380) * 0.35;
    return Math.tanh((body + click) * 1.9);
  }, { gain: 0.95 * v });
}
function clap(t, v) {
  const bp = new BQ('bp', 1250, 1.2);
  put(t, 0.4, (x) => {
    let e = Math.exp(-x * 14) * 0.6;
    for (const o of [0, 0.011, 0.022]) if (x >= o) e += Math.exp(-(x - o) * 220);
    return bp.p(noise()) * e * 3;
  }, { gain: 0.5 * v, rev: 0.4 });
}
function hat(t, v, open, pan = 0) {
  const hp = new BQ('hp', 7800, 0.8);
  put(t, open ? 0.25 : 0.06, (x) => hp.p(noise()) * Math.exp(-x * (open ? 16 : 75)), { gain: 0.2 * v, pan });
}
function snare(t, v) {
  const bp = new BQ('bp', 1800, 0.9);
  let ph = 0;
  put(t, 0.24, (x) => { ph += 185 / SR; return (bp.p(noise()) * 2.2 + Math.sin(TAU * ph) * 0.6) * Math.exp(-x * 20); }, { gain: 0.36 * v, rev: 0.3 });
}
function tom(t, v, f0 = 90) {
  let ph = 0;
  put(t, 0.5, (x) => { ph += (f0 + f0 * 0.8 * Math.exp(-x * 18)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 7) * 1.5); }, { gain: 0.5 * v, rev: 0.35 });
}
function crash(t, v) {
  const hp = new BQ('hp', 4300, 0.7);
  put(t, 3, (x) => hp.p(noise()) * Math.exp(-x * 1.7), { gain: 0.24 * v, rev: 0.35 });
}
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar);
  for (let b = 0; b < 4; b++) {
    const t = T(bar, b);
    if (FULL.has(s) && !muted(t)) {
      if (b === 1 || b === 3) clap(t, 1);
      if (!muted(t + BEAT / 2)) hat(t + BEAT / 2, 1, true, 0.15);
      for (const q of [0.25, 0.75]) if (!muted(t + BEAT * q)) hat(t + BEAT * q, s === 'tension' ? 0.85 : 0.5, false, -0.3);
      if (s === 'drop') hat(t, 0.35, false, 0.3);
      // trailer toms on the tension bars
      if (s === 'tension' && (b === 0 || b === 2)) tom(t + BEAT * 0.75, 0.5, 80);
    }
    if (s === 'ignite') hat(t + BEAT / 2, 0.35, false, 0.2);
  }
}
function roll(t0, len, v = 1) {
  const n = Math.round(len / (BEAT / 4));
  for (let k = 0; k < n; k++) {
    const t = t0 + k * BEAT / 4;
    const u = k / n;
    snare(t, v * (0.3 + 0.8 * u));
    if (u > 0.5) snare(t + BEAT / 8, v * (0.2 + 0.7 * u));
  }
}
roll(T(4, 2), BAR / 2, 0.9); roll(T(10, 2), BAR / 2, 1); roll(T(24, 2), BAR / 2, 1);
// stabs on RELIABLE / SCALABLE / SECURE
for (const t of [T(22), T(22, 2), T(22, 3)]) { clap(t, 1.1); tom(t, 0.8, 70); }
[5, 7, 11, 17, 23, 25].forEach((b) => crash(T(b), 1.15));
[13, 15, 19].forEach((b) => crash(T(b), 0.55));

// ---------------------------------------------------------------- bass + sub
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar), root = ch[3];
  if (!FULL.has(s) && s !== 'ignite') continue;
  const t0 = T(bar);
  let sp = 0;
  put(t0, BAR, (x) => { sp += mtof(root) / SR; const e = Math.min(1, x * 50) * Math.min(1, (BAR - x) * 50); return Math.sin(TAU * sp) * e * (muted(t0 + x) ? 0 : 1); },
    { gain: s === 'ignite' ? 0.22 : 0.36, duck: SC });
  const steps = s === 'tension' ? [1, 2, 3] : s === 'ignite' ? [2] : [1, 2, 3];
  for (let b = 0; b < 4; b++) {
    for (const st of steps) {
      const t = T(bar, b + st * 0.25);
      if (muted(t)) continue;
      const len = BEAT * 0.22;
      const o1 = new Saw(), o2 = new Saw(0.3), lp = new BQ('lp', 900, 1.4);
      const f = mtof(root + 12);
      const bright = s === 'tension' ? 2600 : s === 'ignite' ? 900 : 2000;
      put(t, len, (x, i) => {
        if (i % 16 === 0) lp.set(220 + bright * Math.exp(-x * 30), 2.2);
        const e = Math.min(1, x * 400) * Math.exp(-x * 7) * Math.min(1, (len - x) * 300);
        return lp.p(o1.p(f) * 0.6 + o2.p(f * 1.006) * 0.6) * e;
      }, { gain: s === 'ignite' ? 0.35 : 0.5 });
    }
  }
}

// ---------------------------------------------------------------- pads (supersaw strings)
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  if (s === 'intro') continue;
  const notes = [ch[0] - 12, ch[0], ch[1], ch[2], ch[0] + 12];
  let cut = 2200, g = 0.055, duck = SC;
  if (s === 'ignite') { cut = 900 + 600 * (bar - 2); g = 0.06; }
  else if (s === 'build') { cut = 1400; g = 0.065; duck = null; }
  else if (s === 'break') { cut = 1300; g = 0.075; duck = null; }
  else if (s === 'stabs') { cut = 2600; g = 0.06; }
  else if (s === 'outro') { cut = 2000; g = 0.08; duck = null; }
  else if (s === 'drop') { cut = 3400; g = 0.055; }
  if (s === 'outro' && bar > 25) continue;
  const len = s === 'outro' ? BAR * 3 + 0.6 : BAR;
  notes.forEach((m, ni) => {
    [-0.12, -0.05, 0, 0.05, 0.12].forEach((d, vi) => {
      const o = new Saw((ni * 0.13 + vi * 0.37) % 1);
      const lp = new BQ('lp', cut, 0.8);
      const f = mtof(m + d);
      const t0 = T(bar);
      put(t0, len + 0.02, (x, i) => {
        if ((s === 'build' || s === 'break') && i % 64 === 0) lp.set(cut + 4500 * (x / BAR) ** 2, 0.8);
        const e = Math.min(1, x * 25) * Math.min(1, (len + 0.02 - x) * 40) * (s === 'outro' ? Math.exp(-x * 0.55) : 1);
        return lp.p(o.p(f)) * e;
      }, { gain: g, pan: (vi - 2) * 0.35, rev: 0.3, duck });
    });
  });
}

// ---------------------------------------------------------------- arp pluck
const ARP = [0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 4, 3, 2, 1, 2];
for (let bar = 0; bar < BARS; bar++) {
  const s = sec(bar), ch = chordAt(bar);
  const on = ['ignite', 'build', 'reveal', 'tension', 'drop', 'break'].includes(s) && bar !== 6;
  if (!on) continue;
  const tones = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[0] + 24, ch[1] + 24];
  for (let k = 0; k < 16; k++) {
    const t = T(bar, k * 0.25);
    if (muted(t)) continue;
    if ((s === 'break' || s === 'ignite') && k % 2) continue;
    const m = tones[ARP[k]];
    const o = new Saw(), lp = new BQ('lp', 3000, 1.5);
    const f = mtof(m);
    const bright = s === 'ignite' ? 900 + (bar - 2) * 600 : s === 'tension' ? 2400 : s === 'break' ? 1300 : 3600;
    put(t, 0.3, (x, i) => {
      if (i % 16 === 0) lp.set(300 + bright * Math.exp(-x * 22), 1.6);
      const sq = (o.ph < 0.5 ? 1 : -1) * (s === 'tension' ? 0.5 : 0.15);
      return lp.p(o.p(f) + sq) * Math.exp(-x * 13) * Math.min(1, x * 600);
    }, { gain: s === 'ignite' ? 0.08 : 0.11, pan: k % 2 ? 0.35 : -0.35, rev: 0.22, dly: 0.4, duck: FULL.has(s) ? SC : null });
  }
}

// ---------------------------------------------------------------- SFX
const SFXF = {
  click(e) {
    put(e.t - 0.004, 0.06, (x) => Math.sin(TAU * 3400 * x) * Math.exp(-x * 260) * 0.7 + noise() * Math.exp(-x * 600) * 0.6, { gain: 0.4 * (e.v || 1), rev: 0.08 });
  },
  tick(e) {
    const f = 2200 * (e.f || 1);
    put(e.t, 0.08, (x) => (Math.sin(TAU * f * x) + 0.4 * Math.sin(TAU * f * 2.7 * x)) * Math.exp(-x * 120), { gain: 0.16 * (e.v || 1), rev: 0.35, dly: 0.3, pan: noise() * 0.5 });
  },
  pop(e) {
    let ph = 0; const f0 = 380 * (e.f || 1);
    put(e.t, 0.15, (x) => { ph += (f0 + f0 * 2.2 * Math.min(1, x / 0.05)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 30) + noise() * Math.exp(-x * 300) * 0.3; }, { gain: 0.28 * (e.v || 1), rev: 0.15, pan: noise() * 0.6 });
  },
  hit(e) {
    let ph = 0; const bp = new BQ('bp', 1500, 0.8);
    put(e.t, 0.7, (x) => { ph += (46 + 140 * Math.exp(-x * 26)) / SR; return Math.tanh(Math.sin(TAU * ph) * Math.exp(-x * 6) * 1.7) + bp.p(noise()) * Math.exp(-x * 16) * 1.4; }, { gain: 0.42 * (e.v || 1), rev: 0.3 });
  },
  impact(e) {
    let ph = 0; const lp = new BQ('lp', 2200, 0.7);
    put(e.t, 3, (x) => {
      ph += (24 + 62 * Math.exp(-x * 3)) / SR;
      const sub = Math.sin(TAU * ph) * Math.exp(-x * 1.6);
      const crack = lp.p(noise()) * Math.exp(-x * 4.5) * 1.2;
      return Math.tanh((sub * 1.5 + crack) * 1.3);
    }, { gain: 0.6 * (e.v || 1), rev: 0.5 });
    crash(e.t, 0.8 * (e.v || 1));
  },
  // trailer "braam": detuned low brass stack with a filter that blares open then closes
  braam(e) {
    const d = e.dur || 3;
    [[38, 0], [45, 0.15], [50, -0.15], [26, 0]].forEach(([m, pan], ni) => {
      [-0.15, 0, 0.13].forEach((det, vi) => {
        const o = new Saw((ni * 0.3 + vi * 0.21) % 1), lp = new BQ('lp', 200, 1.3);
        const f = mtof(m + det);
        put(e.t, d, (x, i) => {
          if (i % 32 === 0) lp.set(120 + 2600 * Math.exp(-x * 1.6) * Math.min(1, x * 25), 1.3);
          const env = Math.min(1, x * 30) * Math.exp(-x * 0.9) * Math.min(1, (d - x) * 4);
          return Math.tanh(lp.p(o.p(f)) * 2.2) * env;
        }, { gain: 0.16 * (e.v || 1) * (m < 30 ? 1.2 : 1), pan: pan + (vi - 1) * 0.25, rev: 0.4 });
      });
    });
  },
  drone(e) {
    const d = e.dur;
    [[26, 0], [38, -0.3], [45, 0.3]].forEach(([m, pan], ni) => {
      const o = new Saw(ni * 0.3), o2 = new Saw(ni * 0.7), lp = new BQ('lp', 120, 1.2);
      const f = mtof(m);
      put(e.t, d, (x, i) => {
        const u = x / d;
        if (i % 64 === 0) lp.set(110 + 900 * u * u + 60 * Math.sin(x * 1.3), 1.2);
        const env = Math.min(1, x * 1.2) * Math.min(1, (d - x) * 1.5);
        return lp.p(o.p(f) + o2.p(f * 1.004)) * env * 0.5;
      }, { gain: 0.22 * (e.v || 1), pan, rev: 0.35 });
    });
    const bp = new BQ('bp', 400, 3);
    put(e.t, d, (x, i) => { if (i % 64 === 0) bp.set(300 + 2500 * (x / d) ** 2, 4); return bp.p(noise()) * Math.min(1, x) * Math.min(1, (d - x) * 2); }, { gain: 0.12 * (e.v || 1), rev: 0.5 });
  },
  pad(e) {
    [62, 65, 69, 74].forEach((m, k) => {
      const f = mtof(m);
      put(e.t, 2.5, (x) => (Math.sin(TAU * f * x) + 0.3 * Math.sin(TAU * f * 2 * x)) * Math.min(1, x * 3) * Math.exp(-x * 1.4), { gain: 0.05 * (e.v || 1), pan: (k - 1.5) * 0.4, rev: 0.6 });
    });
  },
  whoosh(e) {
    const bp = new BQ('bp', 500, 1.1); const d = e.dur || 0.3; const pn = e.pan ?? 0.7;
    put(e.t, d, (x, i) => {
      const u = x / d;
      if (i % 32 === 0) bp.set(400 * Math.pow(10, Math.sin(Math.PI * u) * 1.0), 1.1);
      return bp.p(noise()) * Math.pow(Math.sin(Math.PI * u), 2) * 2.5;
    }, { gain: 0.5 * (e.v || 1), pan: (x) => -pn + 2 * pn * (x / d), rev: 0.2 });
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
      const f = 98 * Math.pow(8, u);
      const trem = 0.7 + 0.3 * Math.sin(TAU * x * (4 + 20 * u));
      return (bp.p(noise()) * 2.2 + lp.p(o.p(f) + o2.p(f * 1.01)) * 0.25) * u * u * trem;
    }, { gain: 0.3 * (e.v || 1), rev: 0.35 });
  },
  lock(e) {
    const notes = [62, 65, 69, 72, 74, 77, 81, 86];
    const f = mtof(notes[e.n % 8] + 12); const o = new Saw(), lp = new BQ('lp', 5000, 2);
    let ph = 0;
    put(e.t, 0.7, (x, i) => {
      if (i % 16 === 0) lp.set(500 + 5000 * Math.exp(-x * 14), 2);
      ph += 80 / SR;
      return lp.p(o.p(f)) * Math.exp(-x * 6) * 0.7 + Math.sin(TAU * ph) * Math.exp(-x * 28) * 0.8 + noise() * Math.exp(-x * 400) * 0.35;
    }, { gain: 0.27 * (e.v || 1), pan: (e.n % 2 ? 0.35 : -0.35), rev: 0.35, dly: 0.25 });
  },
  type(e) {
    for (let k = 0; k < e.n; k++) {
      const t = e.t + (k + (noise() * 0.3)) * e.dur / e.n;
      const bp = new BQ('bp', 2600 + (noise() + 1) * 1500, 2);
      put(t, 0.03, (x) => bp.p(noise()) * Math.exp(-x * 260) * 3, { gain: 0.11 * (e.v || 1), pan: noise() * 0.3 });
    }
  },
  scramble(e) {
    for (let t = 0; t < e.dur; t += 0.032) {
      const f = 900 + (noise() + 1) * 1400;
      put(e.t + t, 0.022, (x) => (Math.sin(TAU * f * x) > 0 ? 1 : -1) * Math.exp(-x * 120), { gain: 0.045 * (e.v || 1), pan: noise() * 0.5 });
    }
  },
  error(e) {
    const lp = new BQ('lp', 2200, 0.8);
    put(e.t, 0.22, (x) => { const f = x < 0.1 ? 247 : 196; const sq = Math.sin(TAU * f * x) > 0 ? 1 : -1; return lp.p(sq) * Math.min(1, (0.22 - x) * 60) * (x % 0.1 < 0.085 ? 1 : 0.2); }, { gain: 0.13 * (e.v || 1), rev: 0.12, pan: noise() * 0.6 });
  },
  chime(e) {
    [1174.7, 1760, 2349.3].forEach((f, k) => {
      put(e.t + k * 0.06, 1.5, (x) => (Math.sin(TAU * f * x) + 0.25 * Math.sin(TAU * f * 2.01 * x)) * Math.exp(-x * 3.2) * Math.min(1, x * 400), { gain: 0.09 * (e.v || 1), pan: (k - 1) * 0.4, rev: 0.5, dly: 0.2 });
    });
  },
  shimmer(e) {
    const hp = new BQ('hp', 6000, 0.7);
    put(e.t, 2.2, (x) => hp.p(noise()) * Math.exp(-x * 1.8) * Math.min(1, x * 10), { gain: 0.07 * (e.v || 1), rev: 0.6 });
    for (let k = 0; k < 14; k++) {
      const f = mtof([86, 89, 93, 98][k % 4] + (k > 7 ? 12 : 0)), t = e.t + k * 0.045;
      put(t, 1.2, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 4) * Math.min(1, x * 300), { gain: 0.03 * (e.v || 1), pan: (k % 2 ? 0.6 : -0.6), rev: 0.6, dly: 0.3 });
    }
  },
  sparkle(e) {
    for (let k = 0; k < 18; k++) {
      const f = 3000 + (noise() + 1) * 2500, t = e.t + (noise() + 1) * 0.35;
      put(t, 0.15, (x) => Math.sin(TAU * f * x) * Math.exp(-x * 30), { gain: 0.04 * (e.v || 1), pan: noise() * 0.8, rev: 0.5 });
    }
  },
  glitch(e) {
    for (let t = 0; t < e.dur;) {
      const seg = 0.015 + (noise() + 1) * 0.012, kind = Math.floor((noise() + 1) * 1.5), f = 120 + (noise() + 1) * 1500;
      if (kind !== 2) put(e.t + t, seg, (x) => (kind === 0 ? (Math.sin(TAU * f * x) > 0 ? 1 : -1) : Math.round(noise() * 3) / 3), { gain: 0.1 * (e.v || 1), pan: noise() * 0.6 });
      t += seg;
    }
  },
  alarm(e) {
    const lp = new BQ('lp', 2600, 0.7);
    put(e.t, 0.95, (x) => { const f = 600 + 280 * Math.sin(TAU * x * 2.2); let ph = f * x; return lp.p(Math.sin(TAU * ph) > 0 ? 1 : -1) * Math.min(1, x * 30) * Math.min(1, (0.95 - x) * 10); }, { gain: 0.07 * (e.v || 1), rev: 0.3 });
  },
  sub(e) {
    let ph = 0;
    put(e.t, 2.4, (x) => { ph += (30 + 32 * Math.exp(-x * 2)) / SR; return Math.sin(TAU * ph) * Math.exp(-x * 1.3) * Math.min(1, x * 200); }, { gain: 0.5 * (e.v || 1) });
  },
  // price crash: pitch dive + bitcrushed noise
  drop(e) {
    const o = new Saw(), lp = new BQ('lp', 3000, 1.5);
    put(e.t, 1.1, (x, i) => {
      if (i % 32 === 0) lp.set(300 + 3500 * Math.exp(-x * 4), 1.5);
      const f = 900 * Math.exp(-x * 4.5) + 40;
      return Math.tanh(lp.p(o.p(f)) * 2.5) * Math.exp(-x * 1.8);
    }, { gain: 0.3 * (e.v || 1), rev: 0.3 });
  },
  crash(e) { crash(e.t, e.v || 1); },
  // data streaming toward the core
  stream(e) {
    const d = e.dur;
    for (let t = 0; t < d; t += 0.07) {
      const f = 1800 + (noise() + 1) * 1800;
      const g = Math.min(1, t * 2) * Math.min(1, (d - t) * 2);
      put(e.t + t, 0.05, (x) => Math.sin(TAU * f * x * (1 + x * 8)) * Math.exp(-x * 90), { gain: 0.05 * g * (e.v || 1), pan: noise() * 0.8, rev: 0.3, dly: 0.2 });
    }
  },
  // laser beam into the block
  beam(e) {
    const o = new Saw(), o2 = new Saw(0.4), lp = new BQ('lp', 4000, 2);
    put(e.t - 0.06, 1.3, (x, i) => {
      if (i % 32 === 0) lp.set(600 + 5000 * Math.exp(-x * 3), 2);
      const f = 110 + 1400 * Math.exp(-x * 9);
      return lp.p(o.p(f) + o2.p(f * 1.5)) * Math.exp(-x * 2.2) * Math.min(1, x * 200);
    }, { gain: 0.2 * (e.v || 1), rev: 0.4, dly: 0.2 });
    SFXF.sub({ t: e.t, v: 0.8 });
  },
  // hexagonal coin: metallic FM ring
  coin(e) {
    [[1568, 0], [2349, 0.08], [3136, 0.16]].forEach(([f, dt], k) => {
      put(e.t + dt, 1.8, (x) => Math.sin(TAU * f * x + 2.2 * Math.sin(TAU * f * 1.414 * x) * Math.exp(-x * 5)) * Math.exp(-x * 2.4) * Math.min(1, x * 500), { gain: 0.08 * (e.v || 1), pan: (k - 1) * 0.5, rev: 0.5, dly: 0.25 });
    });
  },
};
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
// ---------------------------------------------------------------- reverb (freeverb, big hall)
function freeverb(inp, spread) {
  const sc = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0, f: 0 }));
  const aps = [556, 441, 341, 225].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0 }));
  const out = new Float32Array(N), room = 0.9, damp = 0.28;
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
const g = 0.25 / rms;
const fadeOut = Math.floor(1.0 * SR);
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
