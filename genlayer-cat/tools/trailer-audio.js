// Trailer soundtrack (32 s, 120 BPM, F minor) synced to the shot cuts in trailer.js.
//   node tools/trailer-audio.js -> build/trailer_audio.wav
const fs = require('fs');
const path = require('path');

const SR = 48000, DUR = 32.6, N = Math.ceil(DUR * SR), TAU = Math.PI * 2;
const BEAT = 0.5, BAR = 2;
const T = (bar, beat = 0) => bar * BAR + beat * BEAT;
const L = new Float32Array(N), R = new Float32Array(N), RV = new Float32Array(N);
let seed = 4242;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) | 0; return (seed >>> 0) / 2147483648 - 1; };
const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
class BQ {
  constructor(type, f, q = 0.707) { this.type = type; this.x1 = this.x2 = this.y1 = this.y2 = 0; this.set(f, q); }
  set(f, q = this.q) {
    this.q = q; f = Math.min(Math.max(f, 10), SR * 0.45);
    const w = TAU * f / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
    let b0, b1, b2; const a0 = 1 + al;
    if (this.type === 'lp') { b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = b0; } else if (this.type === 'hp') { b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = b0; } else { b0 = al; b1 = 0; b2 = -al; }
    this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = -2 * cs / a0; this.a2 = (1 - al) / a0;
  }
  p(x) { const y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2; this.x2 = this.x1; this.x1 = x; this.y2 = this.y1; this.y1 = y; return y; }
}
class Saw { constructor(ph = 0) { this.ph = ph; } p(f) { this.ph += f / SR; if (this.ph >= 1) this.ph -= 1; return 2 * this.ph - 1; } }
function put(t0, len, gen, { gain = 1, pan = 0, rev = 0, duck = null } = {}) {
  const s0 = Math.floor(t0 * SR), n = Math.floor(len * SR);
  const gl = Math.cos((pan + 1) * Math.PI / 4) * Math.SQRT2, gr = Math.sin((pan + 1) * Math.PI / 4) * Math.SQRT2;
  for (let i = 0; i < n; i++) {
    const k = s0 + i; if (k < 0) continue; if (k >= N) break;
    let v = gen(i / SR, i) * gain; if (duck) v *= duck[k];
    L[k] += v * gl; R[k] += v * gr; if (rev) RV[k] += v * rev;
  }
}

// sections: intro 0-4, groove 4-20, dreamy 20-26 (roam), outro 26-32
const PROG = [[65, 68, 72, 41], [65, 68, 73, 37], [63, 68, 72, 44], [63, 67, 70, 39]];
const groove = (t) => t >= 4 && t < 20;
const kicks = [];
for (let b = 0; b < 64; b++) {
  const t = b * BEAT;
  if (groove(t)) kicks.push([t, 1]);
  else if (t >= 20 && t < 26 && b % 2 === 0) kicks.push([t, 0.7]);
  else if (t >= 2 && t < 4 && b % 2 === 0) kicks.push([t, 0.5]);
}
const SC = new Float32Array(N).fill(1);
for (const [t, v] of kicks) { const s0 = Math.floor(t * SR), n = Math.floor(0.26 * SR); for (let i = 0; i < n && s0 + i < N; i++) SC[s0 + i] = Math.min(SC[s0 + i], 1 - 0.75 * v * Math.pow(1 - i / n, 2)); }
for (const [t, v] of kicks) { let ph = 0; put(t, 0.45, (x) => { ph += (44 + 130 * Math.exp(-x * 30)) / SR; return Math.tanh((Math.sin(TAU * ph) * Math.exp(-x * 6) + noise() * Math.exp(-x * 380) * 0.3) * 1.9); }, { gain: 0.9 * v }); }
for (let b = 0; b < 64; b++) {
  const t = b * BEAT;
  if (groove(t)) {
    if (b % 2 === 1) { const bp = new BQ('bp', 1200, 1.1); put(t, 0.35, (x) => { let e = Math.exp(-x * 14) * 0.6; for (const o of [0, 0.012, 0.024]) if (x >= o) e += Math.exp(-(x - o) * 200); return bp.p(noise()) * e * 3; }, { gain: 0.45, rev: 0.4 }); }
    for (const q of [0.25, 0.5, 0.75]) { const hp = new BQ('hp', 7800); const open = q === 0.5; put(t + q * BEAT, open ? 0.24 : 0.05, (x) => hp.p(noise()) * Math.exp(-x * (open ? 15 : 75)), { gain: open ? 0.2 : 0.12, pan: open ? 0.15 : -0.25 }); }
  }
}
for (let bar = 0; bar < 16; bar++) {
  const ch = PROG[bar % 4], t0 = T(bar);
  const dreamy = t0 >= 20 && t0 < 26, out = t0 >= 26;
  // pad
  const cut = t0 < 4 ? 500 + t0 * 300 : dreamy ? 1500 : out ? 1800 : 2600;
  const len = out ? (bar === 13 ? 6.6 : 0) : BAR;
  if (!(out && bar !== 13)) {
    [ch[0] - 12, ch[0], ch[1], ch[2], ch[3] + 24].forEach((m, ni) => [-0.1, 0, 0.1].forEach((d, vi) => {
      const o = new Saw((ni * 0.13 + vi * 0.37) % 1), lp = new BQ('lp', cut, 0.8);
      put(t0, len + 0.05, (x) => lp.p(o.p(mtof(m + d))) * Math.min(1, x * 20) * Math.min(1, (len + 0.05 - x) * (out ? 0.7 : 40)), { gain: 0.06, pan: (vi - 1) * 0.5, rev: 0.3, duck: groove(t0) ? SC : null });
    }));
  }
  // bass
  if (groove(t0)) for (let b = 0; b < 4; b++) {
    const t = t0 + (b + 0.5) * BEAT, o = new Saw(), lp = new BQ('lp', 800, 1.4), f = mtof(ch[3] + 12);
    put(t, 0.22, (x, i) => { if (i % 16 === 0) lp.set(240 + 2200 * Math.exp(-x * 26), 2.2); return lp.p(o.p(f)) * Math.min(1, x * 400) * Math.exp(-x * 7); }, { gain: 0.5 });
  }
  // sub
  if (groove(t0)) { let sp = 0; put(t0, BAR, (x) => { sp += mtof(ch[3]) / SR; return Math.sin(TAU * sp) * Math.min(1, x * 50) * Math.min(1, (BAR - x) * 50); }, { gain: 0.33, duck: SC }); }
  // arp
  if (t0 >= 2 && t0 < 26) {
    const tones = [ch[0], ch[1], ch[2], ch[0] + 12, ch[1] + 12], ARP = [0, 1, 2, 3, 2, 1, 2, 4, 0, 1, 2, 3, 4, 3, 2, 1];
    for (let k = 0; k < 16; k++) {
      if ((t0 < 4 || dreamy) && k % 2) continue;
      const o = new Saw(), lp = new BQ('lp', 3000, 1.5), f = mtof(tones[ARP[k]] + 12);
      put(t0 + k * BEAT / 4, 0.28, (x, i) => { if (i % 16 === 0) lp.set(300 + (dreamy ? 1600 : 3400) * Math.exp(-x * 20), 1.6); return lp.p(o.p(f)) * Math.exp(-x * 12) * Math.min(1, x * 600); },
        { gain: 0.09, pan: k % 2 ? 0.35 : -0.35, rev: 0.3, duck: groove(t0) ? SC : null });
    }
  }
}
// sfx
function impact(t, v = 1) {
  let ph = 0; const lp = new BQ('lp', 2500, 0.7);
  put(t, 2.2, (x) => { ph += (28 + 60 * Math.exp(-x * 3)) / SR; return Math.tanh((Math.sin(TAU * ph) * Math.exp(-x * 2) * 1.4 + lp.p(noise()) * Math.exp(-x * 6)) * 1.3); }, { gain: 0.55 * v, rev: 0.4 });
  const hp = new BQ('hp', 4300); put(t, 2.4, (x) => hp.p(noise()) * Math.exp(-x * 1.8), { gain: 0.18 * v, rev: 0.3 });
}
function whoosh(t, d = 0.45, v = 0.6) {
  const bp = new BQ('bp', 500, 1.1);
  put(t - d * 0.6, d, (x, i) => { const u = x / d; if (i % 32 === 0) bp.set(400 * Math.pow(10, Math.sin(Math.PI * u)), 1.1); return bp.p(noise()) * Math.pow(Math.sin(Math.PI * u), 2) * 2.5; }, { gain: 0.45 * v, rev: 0.15 });
}
function riser(t, d) {
  const bp = new BQ('bp', 300, 2);
  put(t, d, (x, i) => { const u = x / d; if (i % 32 === 0) bp.set(300 * Math.pow(25, u), 2.5); return bp.p(noise()) * 2.2 * u * u; }, { gain: 0.28, rev: 0.3 });
}
function chime(t, v = 1) { [1396.9, 2093, 2793.8].forEach((f, k) => put(t + k * 0.06, 1.4, (x) => (Math.sin(TAU * f * x) + 0.25 * Math.sin(TAU * f * 2.01 * x)) * Math.exp(-x * 3.5) * Math.min(1, x * 400), { gain: 0.09 * v, pan: (k - 1) * 0.4, rev: 0.5 })); }
let sp0 = 0; put(0, 3, (x) => { sp0 += (30 + 20 * Math.exp(-x)) / SR; return Math.sin(TAU * sp0) * Math.min(1, x * 3) * Math.exp(-x * 0.8); }, { gain: 0.4 });
impact(1.0, 0.9); impact(1.25, 0.5); chime(2.0, 0.7);
riser(2.0, 2.0);
for (const t of [4, 10, 16, 20]) { whoosh(t); impact(t, t === 4 ? 1 : 0.55); }
riser(24, 2); whoosh(26, 0.6, 0.8); impact(26, 1.1); chime(26.4); chime(27.1, 0.8);
// reverb (simple feedback comb network)
{
  const taps = [1557, 1617, 1491, 1422, 1277, 1356].map((d) => Math.floor(d * SR / 44100) * 2);
  const buf = new Float32Array(N);
  for (let i = 0; i < N; i++) { let y = 0; for (const d of taps) if (i >= d) y += buf[i - d] * 0.13; buf[i] = RV[i] * 0.4 + y; }
  for (let i = 0; i < N; i++) { L[i] += buf[i]; R[i] += buf[Math.max(0, i - 240)]; }
}
let sum = 0; for (let i = 0; i < N; i++) sum += L[i] * L[i] + R[i] * R[i];
const g = 0.24 / Math.sqrt(sum / (2 * N));
const out = Buffer.alloc(44 + N * 4), fade = Math.floor(1.0 * SR);
for (let i = 0; i < N; i++) {
  const fo = i > N - fade ? (N - i) / fade : 1;
  out.writeInt16LE(Math.round(Math.tanh(L[i] * g) * 0.97 * fo * 32767), 44 + i * 4);
  out.writeInt16LE(Math.round(Math.tanh(R[i] * g) * 0.97 * fo * 32767), 46 + i * 4);
}
out.write('RIFF', 0); out.writeUInt32LE(36 + N * 4, 4); out.write('WAVE', 8); out.write('fmt ', 12); out.writeUInt32LE(16, 16);
out.writeUInt16LE(1, 20); out.writeUInt16LE(2, 22); out.writeUInt32LE(SR, 24); out.writeUInt32LE(SR * 4, 28); out.writeUInt16LE(4, 32); out.writeUInt16LE(16, 34);
out.write('data', 36); out.writeUInt32LE(N * 4, 40);
fs.mkdirSync(path.join(__dirname, '..', 'build'), { recursive: true });
fs.writeFileSync(path.join(__dirname, '..', 'build', 'trailer_audio.wav'), out);
console.log('trailer audio', (N / SR).toFixed(2) + 's');
