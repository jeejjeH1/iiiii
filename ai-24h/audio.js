// Synthesizes the soundtrack (120 BPM synthwave loop + every SFX) from timeline.js.
// Output: build/audio.wav (music + SFX) and build/sfx.wav (SFX only, for using a trending track).
require('./timeline.js');
const fs = require('fs');
const path = require('path');
const { BEAT, BAR, DURATION, MIDNIGHT, SFX } = globalThis.TL;

const SR = 48000;
const N = Math.ceil(DURATION * SR);
const TAU = Math.PI * 2;
const mk = () => ({ L: new Float32Array(N), R: new Float32Array(N), RV: new Float32Array(N), DL: new Float32Array(N) });
const MUS = mk(), FXB = mk();

let seed = 777;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) | 0; return (seed >>> 0) / 2147483648 - 1; };
const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
class BQ {
  constructor(type, f, q = 0.707) { this.type = type; this.x1 = this.x2 = this.y1 = this.y2 = 0; this.set(f, q); }
  set(f, q = this.q) {
    this.q = q;
    f = Math.min(Math.max(f, 20), SR * 0.45);
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

// write a mono generator gen(time, index) into bus B
function put(B, t0, len, gen, { gain = 1, pan = 0, rev = 0, dly = 0 } = {}) {
  const s0 = Math.floor(t0 * SR), n = Math.floor(len * SR);
  const gl = Math.cos((pan + 1) * Math.PI / 4) * Math.SQRT2, gr = Math.sin((pan + 1) * Math.PI / 4) * Math.SQRT2;
  for (let i = 0; i < n; i++) {
    const k = s0 + i;
    if (k < 0) continue;
    if (k >= N) break;
    const v = gen(i / SR, i) * gain;
    B.L[k] += v * gl; B.R[k] += v * gr;
    if (rev) B.RV[k] += v * rev;
    if (dly) B.DL[k] += v * dly;
  }
}

// ---------------------------------------------------------------- arrangement
const CHORDS = [[57, 60, 64, 45], [53, 57, 60, 41], [48, 52, 55, 48], [55, 59, 62, 43]]; // Am F C G
const chordAt = (t) => CHORDS[Math.floor(t / BAR) % 4];
const BUILD0 = MIDNIGHT - BAR; // drums drop for the run-up into midnight
const drumsOn = (t) => t >= 3 - 1e-6 && !(t >= BUILD0 && t < MIDNIGHT) && t < DURATION - BEAT;
const fullOn = (t) => t >= 3 - 1e-6;

// kick
function kick(t, v = 1) {
  put(MUS, t, 0.45, (x) => {
    const ph = TAU * (48 * x + 120 * (1 - Math.exp(-x * 28)) / 28); // pitch sweep 168 -> 48 Hz
    return (Math.sin(ph) * Math.exp(-x * 6.5) + 0.25 * noise() * Math.exp(-x * 120)) * v;
  }, { gain: 0.95 });
}
function snare(t, v = 1) {
  const bp = new BQ('bp', 1900, 0.8);
  put(MUS, t, 0.35, (x) => (bp.p(noise()) * 1.6 * Math.exp(-x * 13) + Math.sin(TAU * 185 * x) * 0.5 * Math.exp(-x * 22)) * v, { gain: 0.5, rev: 0.35 });
}
function hat(t, v = 1, open = false) {
  const hp = new BQ('hp', 7500, 0.7);
  put(MUS, t, open ? 0.22 : 0.06, (x) => hp.p(noise()) * Math.exp(-x * (open ? 14 : 70)) * v, { gain: 0.22, pan: 0.25 });
}
function clap(t, v = 1) {
  const bp = new BQ('bp', 1300, 1.2);
  put(MUS, t, 0.3, (x) => {
    const e = x < 0.03 ? (Math.floor(x / 0.01) % 2 ? 0.4 : 1) : Math.exp(-(x - 0.03) * 16);
    return bp.p(noise()) * e * v;
  }, { gain: 0.35, rev: 0.4, pan: -0.15 });
}
for (let b = 0; b * BEAT < DURATION; b++) {
  const t = b * BEAT;
  if (drumsOn(t)) {
    kick(t);
    if (b % 2 === 1) { snare(t); clap(t + 0.004, 0.7); }
  } else if (t >= 1.5 && t < 3) kick(t, 0.6); // hook pickup
  if (t >= 0.5 && !(t >= BUILD0 && t < MIDNIGHT - BAR / 2)) {
    hat(t + BEAT / 2, 0.9, b % 4 === 3);
    if (fullOn(t)) { hat(t + BEAT / 4, 0.4); hat(t + 3 * BEAT / 4, 0.4); }
  }
}
// snare roll into midnight
for (let i = 0; i < 16; i++) {
  const t = MIDNIGHT - BAR / 2 + i * (BAR / 2) / 16;
  snare(t, 0.25 + 0.6 * i / 16);
}
// crash at midnight
{
  const hp = new BQ('hp', 4500, 0.5);
  put(MUS, MIDNIGHT, 2.2, (x) => hp.p(noise()) * Math.exp(-x * 2.2), { gain: 0.3, rev: 0.4 });
}

// bass: saw through a pumping low-pass, eighth notes
{
  const lp = new BQ('lp', 300, 1.6);
  const s1 = new Saw(), s2 = new Saw(0.3);
  put(MUS, 3, DURATION - 3 - BEAT, (x) => {
    const t = 3 + x;
    if (t >= BUILD0 && t < MIDNIGHT) return 0;
    const root = chordAt(t)[3] - 12;
    const step = Math.floor(t / (BEAT / 2));
    const n = root + (step % 4 === 3 ? 12 : 0);
    const st = t % (BEAT / 2);
    const env = Math.exp(-st * 6) * 0.7 + 0.3;
    const side = clamp((t % BEAT) / 0.18); // sidechain duck after each kick
    lp.set(180 + 900 * Math.exp(-st * 10), 1.4);
    return lp.p(s1.p(mtof(n)) + 0.6 * s2.p(mtof(n) * 1.005)) * env * (0.35 + 0.65 * side);
  }, { gain: 0.42 });
}

// pad: detuned supersaw, slow filter, sidechained
{
  const voices = [];
  for (let k = 0; k < 4; k++) for (let d = 0; d < 3; d++) voices.push({ k, det: [0.994, 1, 1.006][d], s: new Saw((noise() + 1) / 2) });
  const lpL = new BQ('lp', 1200, 0.9);
  put(MUS, 0, DURATION, (t) => {
    const ch = chordAt(t);
    let v = 0;
    for (const vo of voices) v += vo.s.p(mtof(vo.k < 3 ? ch[vo.k] : ch[0] + 12) * vo.det);
    const open = t < 3 ? 500 + 500 * t / 3 : t >= BUILD0 && t < MIDNIGHT ? 900 + 2600 * (t - BUILD0) / (MIDNIGHT - BUILD0) : 1600 + 500 * Math.sin(t * 0.7);
    lpL.set(open, 0.9);
    const side = drumsOn(t) ? 0.4 + 0.6 * clamp((t % BEAT) / 0.3) : 1;
    const fade = clamp(t / 0.6) * clamp((DURATION - t) / 0.6);
    return lpL.p(v / 12) * side * fade;
  }, { gain: 0.32, rev: 0.5 });
}

// arp: plucky square-ish 16ths over the chord, with delay
{
  const lp = new BQ('lp', 3000, 1);
  const pattern = [0, 1, 2, 3, 2, 1, 0, 2];
  const sq = new Saw(), sq2 = new Saw(0.5);
  put(MUS, 0, DURATION, (t) => {
    if (t >= DURATION - BEAT) return 0;
    const step = Math.floor(t / (BEAT / 4));
    const st = t % (BEAT / 4);
    const ch = chordAt(t);
    const idx = pattern[step % 8];
    const n = (idx === 3 ? ch[0] + 12 : ch[idx]) + 12;
    const f = mtof(n);
    const pulse = sq.p(f) - sq2.p(f); // saw - shifted saw = pulse
    const cut = t < 3 ? 900 + 1400 * (t / 3) : 2600;
    lp.set(cut, 1.2);
    return lp.p(pulse) * Math.exp(-st * 22) * (t < 3 ? 0.6 : 1);
  }, { gain: 0.11, dly: 0.45, pan: -0.2 });
}

// ---------------------------------------------------------------- SFX
const SYN = {
  tick: (o) => put(FXB, o.t, 0.12, (x) => (Math.sin(TAU * 2400 * x) * 0.6 + Math.sin(TAU * 5200 * x) * 0.3) * Math.exp(-x * 60), { gain: 0.45 * o.v, rev: 0.15 }),
  click: (o) => put(FXB, o.t, 0.05, (x) => (Math.sin(TAU * 1800 * x) + noise() * 0.4) * Math.exp(-x * 120), { gain: 0.35 * o.v }),
  pop: (o) => put(FXB, o.t, 0.18, (x) => Math.sin(TAU * (o.f * (1 + 1.2 * Math.exp(-x * 40))) * x) * Math.exp(-x * 26), { gain: 0.45 * o.v, rev: 0.2 }),
  plop: (o) => put(FXB, o.t, 0.1, (x) => Math.sin(TAU * o.f * (1 - x * 3) * x) * Math.exp(-x * 45), { gain: 0.6 * o.v, pan: (o.f % 3 - 1) * 0.4 }),
  ding: (o) => put(FXB, o.t, 0.6, (x) => (Math.sin(TAU * o.f * x) + 0.4 * Math.sin(TAU * o.f * 2.01 * x)) * Math.exp(-x * 7), { gain: 0.3 * o.v, rev: 0.4 }),
  blip: (o) => put(FXB, o.t, 0.09, (x) => Math.sin(TAU * o.f * x + 3 * Math.sin(TAU * o.f * 0.5 * x)) * Math.sin(Math.PI * x / 0.09), { gain: 0.5 * o.v }),
  sparkle: (o) => { for (let i = 0; i < 6; i++) SYN.ding({ t: o.t + i * 0.045, f: 1600 + i * 330, v: o.v * (1 - i * 0.12) }); },
  hit: (o) => {
    const lp = new BQ('lp', 1800, 0.8);
    put(FXB, o.t, 0.5, (x) => (Math.sin(TAU * (55 + 90 * Math.exp(-x * 30)) * x) * Math.exp(-x * 7) + lp.p(noise()) * Math.exp(-x * 18) * 0.5), { gain: 0.6 * o.v, rev: 0.3 });
  },
  impact: (o) => {
    const lp = new BQ('lp', 900, 0.7);
    put(FXB, o.t, 2.5, (x) => (Math.sin(TAU * (38 + 120 * Math.exp(-x * 14)) * x) * Math.exp(-x * 2.4) + lp.p(noise()) * Math.exp(-x * 4) * 0.7), { gain: 0.85 * o.v, rev: 0.5 });
  },
  whoosh: (o) => {
    const bp = new BQ('bp', 500, 1.2);
    put(FXB, o.t, o.dur, (x) => { const k = x / o.dur; bp.set(400 + 4000 * k, 1.2); return bp.p(noise()) * Math.sin(Math.PI * k); }, { gain: 0.5 * o.v });
  },
  swish: (o) => SYN.whoosh(o),
  suck: (o) => {
    const bp = new BQ('bp', 3000, 2);
    put(FXB, o.t, o.dur, (x) => { const k = x / o.dur; bp.set(3500 - 3000 * k, 2); return bp.p(noise()) * k * k * 1.5; }, { gain: 0.5 * o.v });
  },
  glitch: (o) => put(FXB, o.t, o.dur, (x) => { const step = Math.floor(x * 90); return (((step * 37) % 7) / 3.5 - 1) * Math.sign(Math.sin(TAU * (200 + (step % 5) * 180) * x)) * 0.5 + noise() * 0.2; }, { gain: 0.4 * o.v }),
  type: (o) => { const n = Math.floor(o.dur / 0.055); for (let i = 0; i < n; i++) SYN.click({ t: o.t + i * 0.055 + (noise() * 0.01), v: o.v * (0.6 + 0.4 * Math.abs(noise())) }); },
  pencil: (o) => {
    const bp = new BQ('bp', 3500, 0.9);
    put(FXB, o.t, o.dur, (x) => bp.p(noise()) * (0.5 + 0.5 * Math.abs(Math.sin(x * 22))) * clamp(x / 0.05) * clamp((o.dur - x) / 0.1), { gain: 0.35 * o.v });
  },
  scan: (o) => put(FXB, o.t, o.dur, (x) => { const k = x / o.dur; return Math.sin(TAU * (300 + 1500 * k) * x) * 0.3 * Math.sin(Math.PI * k) * (0.6 + 0.4 * Math.sin(TAU * 30 * x)); }, { gain: 0.4 * o.v, rev: 0.3 }),
  riser: (o) => {
    const bp = new BQ('bp', 300, 1.5);
    const s = new Saw();
    put(FXB, o.t, o.dur, (x) => { const k = x / o.dur; bp.set(300 + 6000 * k * k, 1.5); return (bp.p(noise()) * 0.8 + s.p(110 * Math.pow(4, k)) * 0.12) * k * k; }, { gain: 0.6 * o.v, rev: 0.3 });
  },
};
for (const o of SFX) SYN[o.type](o);

// ---------------------------------------------------------------- effects + mix
function fxBus(B) {
  // dotted-eighth ping-pong delay
  const D = Math.floor(BEAT * 0.75 * SR);
  const dl = new Float32Array(N), dr = new Float32Array(N);
  for (let i = 0; i < N; i++) {
    const inL = B.DL[i] + (i >= D ? dr[i - D] * 0.42 : 0);
    const inR = i >= D ? dl[i - D] * 0.42 : 0;
    dl[i] = inL; dr[i] = inR;
    B.L[i] += dl[i] * 0.5; B.R[i] += dr[i] * 0.5;
  }
  // small Schroeder reverb (parallel combs + allpasses), decorrelated L/R
  const combs = [1116, 1188, 1277, 1356, 1422, 1491].map((n) => Math.floor(n * SR / 44100));
  for (const [out, off] of [[B.L, 0], [B.R, 23]]) {
    const acc = new Float32Array(N);
    for (const n0 of combs) {
      const n = n0 + off; const buf = new Float32Array(n); let idx = 0, lp = 0;
      for (let i = 0; i < N; i++) { const y = buf[idx]; lp = y * 0.7 + lp * 0.3; buf[idx] = B.RV[i] + lp * 0.8; idx = (idx + 1) % n; acc[i] += y; }
    }
    for (const n0 of [556, 441]) {
      const n = n0 + off; const buf = new Float32Array(n); let idx = 0;
      for (let i = 0; i < N; i++) { const b = buf[idx]; const y = -acc[i] + b; buf[idx] = acc[i] + b * 0.5; idx = (idx + 1) % n; acc[i] = y; }
    }
    for (let i = 0; i < N; i++) out[i] += acc[i] * 0.09;
  }
}
fxBus(MUS); fxBus(FXB);

function write(file, buses) {
  const L = new Float32Array(N), R = new Float32Array(N);
  for (const [B, g] of buses) for (let i = 0; i < N; i++) { L[i] += B.L[i] * g; R[i] += B.R[i] * g; }
  let peak = 0;
  for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  const norm = 0.89 / (peak || 1);
  const out = Buffer.alloc(44 + N * 4);
  out.write('RIFF', 0); out.writeUInt32LE(36 + N * 4, 4); out.write('WAVE', 8); out.write('fmt ', 12);
  out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(2, 22); out.writeUInt32LE(SR, 24);
  out.writeUInt32LE(SR * 4, 28); out.writeUInt16LE(4, 32); out.writeUInt16LE(16, 34); out.write('data', 36); out.writeUInt32LE(N * 4, 40);
  const fadeN = Math.floor(0.25 * SR);
  for (let i = 0; i < N; i++) {
    const f = i > N - fadeN ? (N - i) / fadeN : 1;
    const sat = (x) => Math.tanh(x * norm * 1.15) / Math.tanh(1.15); // soft limiter
    out.writeInt16LE(Math.round(clamp(sat(L[i]) * f, -1, 1) * 32767), 44 + i * 4);
    out.writeInt16LE(Math.round(clamp(sat(R[i]) * f, -1, 1) * 32767), 46 + i * 4);
  }
  fs.writeFileSync(file, out);
  console.log(file, 'peak', peak.toFixed(2));
}
const BUILD = path.join(__dirname, 'build');
fs.mkdirSync(BUILD, { recursive: true });
write(path.join(BUILD, 'audio.wav'), [[MUS, 0.8], [FXB, 1.0]]);
write(path.join(BUILD, 'sfx.wav'), [[FXB, 1.0]]);
