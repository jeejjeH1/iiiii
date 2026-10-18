// Synthesizes the soundtrack from timeline.js: drone bed, heartbeat pulse and every SFX
// (eyes, data blips, stone doors, absorbs, seal impact, crystal chimes, stomp, end pad).
// Output: build/audio.wav (48 kHz, 16-bit stereo)
const { T, CUBES, CRYSTALS, DURATION } = require('./timeline.js');
const fs = require('fs');
const path = require('path');

const SR = 48000;
const N = Math.ceil(DURATION * SR);
const L = new Float32Array(N), R = new Float32Array(N);
const RVL = new Float32Array(N), RVR = new Float32Array(N);
const TAU = Math.PI * 2;

let seed = 9001;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) | 0; return (seed >>> 0) / 2147483648 - 1; };
const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
const env = (t, a, d) => (t < a ? t / a : Math.exp(-(t - a) / d));
const smooth = (a, b, x) => { const k = Math.min(1, Math.max(0, (x - a) / (b - a))); return k * k * (3 - 2 * k); };
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

function put(t0, len, gen, { gain = 1, pan = 0, rev = 0 } = {}) {
  const s0 = Math.floor(t0 * SR), n = Math.floor(len * SR);
  const gl = Math.cos((pan + 1) * Math.PI / 4) * Math.SQRT2, gr = Math.sin((pan + 1) * Math.PI / 4) * Math.SQRT2;
  for (let i = 0; i < n; i++) {
    const k = s0 + i;
    if (k < 0) continue;
    if (k >= N) break;
    const v = gen(i / SR, i) * gain;
    L[k] += v * gl; R[k] += v * gr;
    if (rev) { RVL[k] += v * gl * rev; RVR[k] += v * gr * rev; }
  }
}

// ---------------------------------------------------------------- bed
// A minor drone: A1 + E2 + A2, opening up as the guardian wakes; ducks under the impacts.
const duck = (t) => {
  let d = 1;
  for (const ti of [T.close[1], T.stomp]) if (t > ti) d *= 1 - 0.7 * Math.exp(-(t - ti) * 3);
  return d;
};
{
  const lp = new BQ('lp', 200, 0.9);
  const ph = [0, 0, 0, 0];
  const fr = [mtof(33), mtof(40), mtof(45), mtof(45) * 1.004];
  put(0, DURATION, (t) => {
    lp.set(180 + 900 * smooth(3, 10, t) + 600 * smooth(15, 17, t) - 500 * smooth(25, 29, t));
    let v = 0;
    for (let j = 0; j < 4; j++) { ph[j] += fr[j] / SR; v += (2 * (ph[j] % 1) - 1) * [0.5, 0.3, 0.25, 0.25][j]; }
    const amp = 0.25 + 0.5 * smooth(0, 4, t);
    return lp.p(v) * amp * duck(t) * (1 - smooth(DURATION - 1.2, DURATION, t));
  }, { gain: 0.35, rev: 0.3 });
  // air: slow filtered noise
  const bp = new BQ('bp', 600, 0.6);
  put(0, DURATION, (t) => { bp.set(500 + 300 * Math.sin(t * 0.4)); return bp.p(noise()) * (0.5 + 0.5 * Math.sin(t * 0.7)); }, { gain: 0.05, rev: 0.4 });
}
// heartbeat pulse, 80 bpm, from the first cube to the stomp
for (let t = T.cubesIn; t < T.lift[0]; t += 0.75) {
  const strong = Math.round((t - T.cubesIn) / 0.75) % 2 === 0;
  const lvl = 0.5 + 0.5 * smooth(T.cubesIn, 9, t);
  put(t, 0.45, (x) => Math.sin(TAU * (48 + 60 * Math.exp(-x * 30)) * x) * env(x, 0.004, 0.11), { gain: (strong ? 0.5 : 0.3) * lvl });
}

// ---------------------------------------------------------------- SFX
function rumble(t0, len, gain, cutoff = 160) {
  const lp = new BQ('lp', cutoff, 0.8), lp2 = new BQ('lp', cutoff, 0.8);
  put(t0, len, (t) => lp2.p(lp.p(noise())) * Math.sin(Math.PI * Math.min(1, t / len)), { gain, rev: 0.2 });
}
function boom(t0, gain, f0 = 110, f1 = 34, dec = 0.7) {
  let ph = 0;
  put(t0, dec * 5, (t) => { ph += (f1 + (f0 - f1) * Math.exp(-t * 9)) / SR; return Math.sin(TAU * ph) * env(t, 0.003, dec); }, { gain, rev: 0.35 });
  const hp = new BQ('hp', 1800);
  put(t0, 0.25, (t) => hp.p(noise()) * env(t, 0.001, 0.035), { gain: gain * 0.5, rev: 0.4 });
}
function chime(t0, f, gain, pan = 0) {
  const parts = [[1, 1], [2.76, 0.45], [5.4, 0.25], [8.9, 0.12]];
  put(t0, 2.5, (t) => parts.reduce((s, [m, a]) => s + Math.sin(TAU * f * m * t) * a * Math.exp(-t * (1.6 + m * 0.6)), 0) * Math.min(1, t * 400), { gain, pan, rev: 0.55 });
}
function whoosh(t0, len, gain, up = true, pan = 0) {
  const bp = new BQ('bp', 400, 1.4);
  put(t0, len, (t) => { const k = t / len; bp.set(up ? 300 + 4000 * k * k : 4000 - 3600 * k); return bp.p(noise()) * Math.sin(Math.PI * k); }, { gain, pan, rev: 0.35 });
}
function grind(t0, len, gain) {
  const bp = new BQ('bp', 220, 3), lp = new BQ('lp', 900);
  put(t0, len, (t) => { bp.set(180 + 120 * Math.sin(t * 23) + 80 * noise()); return lp.p(bp.p(noise()) * (1 + 0.6 * Math.sin(t * 61))) * Math.sin(Math.PI * Math.min(1, t / len)); }, { gain, rev: 0.25 });
}

// wake: rumble while it straightens, ignite ping on the eyes, swell on the emblem
rumble(T.rise[0] - 0.4, T.rise[1] - T.rise[0] + 0.8, 1.1);
chime(T.eyes, mtof(88), 0.12, 0);
chime(T.eyes + 0.06, mtof(93), 0.08, 0.2);
whoosh(T.eyes - 0.5, 0.6, 0.12);
{
  const notes = [57, 64, 69, 71];
  for (const [j, m] of notes.entries()) put(T.emblem, 3.5, (t) => Math.sin(TAU * mtof(m) * t) * env(t, 0.6, 1.2) * (1 + 0.3 * Math.sin(t * 5 + j)), { gain: 0.05, rev: 0.6, pan: (j - 1.5) * 0.3 });
}

// data cubes: pentatonic blips as each one appears, a whoosh + pluck as it is absorbed
const PENTA = [81, 84, 86, 88, 91, 93];
for (let i = 0; i < CUBES; i++) {
  const t0 = T.cubesIn + i * T.cubeGap;
  const pan = i % 2 ? 0.55 : -0.55;
  const f = mtof(PENTA[(i * 2) % PENTA.length]);
  put(t0, 0.5, (t) => (Math.sin(TAU * f * t) + 0.3 * Math.sin(TAU * f * 2 * t)) * env(t, 0.004, 0.09), { gain: 0.09, pan, rev: 0.5 });
  const ta = T.absorb + i * T.absorbGap;
  whoosh(ta, T.absorbDur, 0.1, true, pan * 0.5);
  const fa = mtof(PENTA[i % PENTA.length] - 12);
  put(ta + T.absorbDur, 0.6, (t) => Math.sin(TAU * fa * t + Math.sin(TAU * fa * 2 * t) * Math.exp(-t * 20)) * env(t, 0.002, 0.12), { gain: 0.11, rev: 0.4 });
}

// doors: stone grind opening, hum from the core
grind(T.open[0] - 0.05, T.open[1] - T.open[0] + 0.4, 0.9);
boom(T.open[1] - 0.15, 0.25, 70, 40, 0.4);
put(T.open[0], T.close[1] - T.open[0], (t) => (Math.sin(TAU * 110 * t) * 0.5 + Math.sin(TAU * 165.2 * t) * 0.3 + Math.sin(TAU * 220.5 * t) * 0.2) * smooth(0, 1, t) * (1 - smooth(T.close[0] - T.open[0], T.close[1] - T.open[0], t)), { gain: 0.07, rev: 0.4 });

// seal: slam + rising shimmer while light sweeps up the edges
grind(T.close[0] - 0.05, T.close[1] - T.close[0] + 0.05, 0.8);
boom(T.close[1], 0.9, 130, 38, 0.6);
{
  const bp = new BQ('bp', 800, 2);
  put(T.sweep[0], T.sweep[1] - T.sweep[0] + 0.6, (t) => { const k = t / (T.sweep[1] - T.sweep[0]); bp.set(600 + 5000 * Math.min(1, k)); return bp.p(noise()) * Math.sin(Math.PI * Math.min(1, k * 0.9)); }, { gain: 0.18, rev: 0.6 });
}

// crystals: soft lift-off whoosh, glass chime when each lands in the chain
const CHIME = [76, 79, 81, 83, 86, 88, 91, 93, 95, 98, 100, 103];
for (let i = 0; i < CRYSTALS; i++) {
  const te = T.emit + i * T.emitGap;
  const pan = -0.8 + (i / (CRYSTALS - 1)) * 1.6;
  whoosh(te, 0.7, 0.05, true, pan * 0.4);
  chime(te + T.emitDur * 0.92, mtof(CHIME[i]), 0.09, pan);
}

// stomp: inhale riser, impact, crack, long rumble; the chain answers
whoosh(T.lift[0] - 0.3, T.stomp - T.lift[0] + 0.3, 0.2, true);
boom(T.stomp, 1.6, 150, 30, 1.1);
rumble(T.stomp, 3.2, 3.0, 120);
{
  const hp = new BQ('hp', 900);
  put(T.stomp, 1.2, (t) => hp.p(noise()) * env(t, 0.001, 0.08) * (noise() > 0.996 ? 3 : 1), { gain: 0.35, rev: 0.5 });
}
for (let i = 0; i < CRYSTALS; i++) chime(T.stomp + 0.35 + i * 0.05, mtof(CHIME[i] - 12), 0.05, -0.8 + i * 0.145);

// end card: open pad chord
{
  const chord = [45, 57, 64, 69, 71, 76];
  for (const [j, m] of chord.entries()) {
    const lp = new BQ('lp', 1200);
    let ph = j * 0.13;
    put(T.endCard, DURATION - T.endCard, (t) => { ph += mtof(m) * (1 + 0.002 * Math.sin(t * 3 + j)) / SR; return lp.p(2 * (ph % 1) - 1) * smooth(0, 1.6, t) * (1 - smooth(DURATION - T.endCard - 2.2, DURATION - T.endCard, t)); }, { gain: 0.07, rev: 0.7, pan: (j - 2.5) * 0.25 });
  }
  chime(T.endCard + 0.25, mtof(81), 0.14, 0);
}

// ---------------------------------------------------------------- reverb (freeverb)
function freeverb(inp, spread) {
  const sc = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0, f: 0 }));
  const aps = [556, 441, 341, 225].map((n) => ({ b: new Float32Array(Math.floor((n + spread) * sc)), i: 0 }));
  const out = new Float32Array(N), room = 0.9, damp = 0.3;
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
const rl = freeverb(RVL, 0), rr = freeverb(RVR, 23);
for (let i = 0; i < N; i++) { L[i] += rl[i] * 2.4; R[i] += rr[i] * 2.4; }

// ---------------------------------------------------------------- master
const hpL = new BQ('hp', 22), hpR = new BQ('hp', 22);
let sum = 0;
for (let i = 0; i < N; i++) { L[i] = hpL.p(L[i]); R[i] = hpR.p(R[i]); sum += L[i] * L[i] + R[i] * R[i]; }
const g = 0.2 / Math.sqrt(sum / (2 * N));
const fade = Math.floor(0.3 * SR);
const out = Buffer.alloc(44 + N * 4);
for (let i = 0; i < N; i++) {
  const fo = i > N - fade ? (N - i) / fade : 1;
  out.writeInt16LE(Math.round(Math.tanh(L[i] * g) * 0.97 * fo * 32767), 44 + i * 4);
  out.writeInt16LE(Math.round(Math.tanh(R[i] * g) * 0.97 * fo * 32767), 46 + i * 4);
}
out.write('RIFF', 0); out.writeUInt32LE(36 + N * 4, 4); out.write('WAVE', 8);
out.write('fmt ', 12); out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(2, 22);
out.writeUInt32LE(SR, 24); out.writeUInt32LE(SR * 4, 28); out.writeUInt16LE(4, 32); out.writeUInt16LE(16, 34);
out.write('data', 36); out.writeUInt32LE(N * 4, 40);
fs.mkdirSync(path.join(__dirname, 'build'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'build/audio.wav'), out);
console.log('audio done', DURATION, 's');
