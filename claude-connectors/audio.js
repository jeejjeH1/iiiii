// Synthesizes the soundtrack (128 BPM future-house track + every SFX) from timeline.js.
// Output: build/audio.wav (music + SFX + voice), build/sfx.wav (SFX + voice, for adding a trending
// track in Instagram) and build/novoice.wav (music + SFX).
require('./timeline.js');
const fs = require('fs');
const path = require('path');
const { BEAT, BAR, T, DURATION, SFX } = globalThis.TL;

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
// F minor: Fm  Db  Ab  Eb  (three chord tones + bass root)
const CHORDS = [[53, 56, 60, 41], [49, 53, 56, 37], [48, 51, 56, 44], [51, 55, 58, 39]];
const chordAt = (t) => CHORDS[Math.floor(t / BAR) % 4];
const DROP = T(2), BREAK0 = T(12), BREAK1 = T(14), END = T(17);
const isDrop = (t) => (t >= DROP && t < BREAK0) || (t >= BREAK1 && t < END);
const isBreak = (t) => t >= BREAK0 && t < BREAK1;
const isBuild = (t) => t >= BAR && t < DROP;

function kick(t, v = 1) {
  put(MUS, t, 0.42, (x) => {
    const ph = TAU * (50 * x + 140 * (1 - Math.exp(-x * 30)) / 30); // pitch sweep 190 -> 50 Hz
    return (Math.sin(ph) * Math.exp(-x * 7) + 0.3 * noise() * Math.exp(-x * 140)) * v;
  }, { gain: 1.0 });
}
function snare(t, v = 1) {
  const bp = new BQ('bp', 2000, 0.8);
  put(MUS, t, 0.3, (x) => (bp.p(noise()) * 1.6 * Math.exp(-x * 15) + Math.sin(TAU * 200 * x) * 0.5 * Math.exp(-x * 25)) * v, { gain: 0.45, rev: 0.3 });
}
function clap(t, v = 1) {
  const bp = new BQ('bp', 1400, 1.1);
  put(MUS, t, 0.32, (x) => {
    const e = x < 0.03 ? (Math.floor(x / 0.01) % 2 ? 0.4 : 1) : Math.exp(-(x - 0.03) * 15);
    return bp.p(noise()) * e * v;
  }, { gain: 0.55, rev: 0.4 });
}
function hat(t, v = 1, open = false, pan = 0.25) {
  const hp = new BQ('hp', 8000, 0.7);
  put(MUS, t, open ? 0.2 : 0.05, (x) => hp.p(noise()) * Math.exp(-x * (open ? 16 : 80)) * v, { gain: 0.24, pan });
}
function crash(t, v = 1) {
  const hp = new BQ('hp', 4500, 0.5);
  put(MUS, t, 2.4, (x) => hp.p(noise()) * Math.exp(-x * 2) * v, { gain: 0.3, rev: 0.4 });
}

for (let b = 0; b * BEAT < END; b++) {
  const t = b * BEAT;
  if (t < BAR) kick(t, 0.85);                       // hook: kick under every slam
  else if (isBuild(t)) { if (b % 4 !== 3) kick(t, 0.7); }
  else if (isDrop(t)) {
    kick(t);
    if (b % 2 === 1) clap(t);
    hat(t + BEAT / 2, 1, true);
    hat(t + BEAT / 4, 0.45, false, -0.3); hat(t + 3 * BEAT / 4, 0.45, false, -0.3);
  } else if (isBreak(t)) {
    if (t >= T(13)) kick(t, 0.55);                // second bar of the breakdown builds back up
    if (b % 2 === 1) clap(t, 0.7);
    hat(t + BEAT / 2, 0.6);
  }
}
// snare roll building into the first drop: 8ths then 16ths
for (let i = 0; i < 4; i++) snare(BAR + i * BEAT / 2, 0.3 + i * 0.05);
for (let i = 0; i < 8; i++) snare(BAR + 2 * BEAT + i * BEAT / 4, 0.5 + i * 0.06);
// short roll at the end of the breakdown
for (let i = 0; i < 8; i++) snare(T(13, 2) + i * BEAT / 4, 0.35 + i * 0.08);
// one-beat fills at the end of every connector
for (let k = 0; k < 4; k++) for (let i = 0; i < 4; i++) snare(T(3 + 2 * k, 3) + i * BEAT / 4, 0.25 + i * 0.1);
[DROP, BREAK1, T(16)].forEach((t) => crash(t));
crash(END, 0.7);
kick(END, 1);

// offbeat house bass (saw + sub), pumping
{
  const lp = new BQ('lp', 300, 1.4);
  const s1 = new Saw(), s2 = new Saw(0.4);
  put(MUS, DROP, END - DROP, (x) => {
    const t = DROP + x;
    if (!isDrop(t)) return 0;
    const st = (t + BEAT / 2) % BEAT;           // time since the offbeat
    const root = chordAt(t)[3] - 12;
    const n = root + (Math.floor(t / (BEAT / 2)) % 4 === 3 ? 12 : 0);
    const env = Math.exp(-st * 5);
    lp.set(160 + 1400 * Math.exp(-st * 14), 1.5);
    const f = mtof(n);
    return lp.p(s1.p(f) + 0.5 * s2.p(f * 1.007)) * env * 0.8 + Math.sin(TAU * f * t) * env * 0.6;
  }, { gain: 0.48 });
}

// supersaw offbeat stabs (future-house pluck); on-beat filtered stabs in the hook
{
  const voices = [];
  for (let k = 0; k < 4; k++) for (let d = 0; d < 5; d++) voices.push({ k, det: [0.988, 0.994, 1, 1.006, 1.012][d], s: new Saw((noise() + 1) / 2) });
  const lp = new BQ('lp', 2000, 1.1);
  put(MUS, 0, END + BEAT, (t) => {
    let st, open, g;
    if (t < BAR) { st = t % BEAT; open = 900; g = 0.7; }
    else if (isDrop(t)) { st = (t + BEAT / 2) % BEAT; open = 3200; g = 1; }
    else if (t >= END) { st = t - END; open = 2500; g = 1.2; }
    else return 0;
    const env = Math.exp(-st * 9);
    if (env < 0.002) return 0;
    const ch = chordAt(Math.min(t, END - 0.01));
    let v = 0;
    for (const vo of voices) v += vo.s.p(mtof((vo.k < 3 ? ch[vo.k] : ch[0] + 12) + 12) * vo.det);
    lp.set(400 + open * env, 1.1);
    return lp.p(v / 20) * env * g;
  }, { gain: 0.5, rev: 0.35, dly: 0.25 });
}

// pad: always there, louder in the breakdown, sidechained in the drops
{
  const voices = [];
  for (let k = 0; k < 3; k++) for (let d = 0; d < 3; d++) voices.push({ k, det: [0.995, 1, 1.005][d], s: new Saw((noise() + 1) / 2) });
  const lp = new BQ('lp', 900, 0.8);
  put(MUS, 0, DURATION, (t) => {
    const ch = chordAt(Math.min(t, END - 0.01));
    let v = 0;
    for (const vo of voices) v += vo.s.p(mtof(ch[vo.k]) * vo.det);
    const open = isBreak(t) ? 900 + 2200 * (t - BREAK0) / (BREAK1 - BREAK0) : isBuild(t) ? 700 + 2000 * (t - BAR) / BAR : 1100;
    lp.set(open, 0.8);
    const side = isDrop(t) ? 0.25 + 0.75 * clamp((t % BEAT) / 0.3) : 1;
    const g = isBreak(t) ? 1.8 : isDrop(t) ? 0.7 : 1;
    const fade = clamp(t / 0.3) * clamp((DURATION - t) / 0.5);
    return lp.p(v / 9) * side * g * fade;
  }, { gain: 0.3, rev: 0.5 });
}

// 16th arp lead with ping-pong delay
{
  const lp = new BQ('lp', 3000, 1);
  const pattern = [0, 2, 1, 3, 0, 2, 3, 1];
  const a = new Saw(), b = new Saw(0.5);
  put(MUS, 0, END, (t) => {
    const intro = t < DROP;
    const step = Math.floor(t / (BEAT / 4));
    const st = t % (BEAT / 4);
    const ch = chordAt(t);
    const idx = pattern[step % 8];
    const n = (idx === 3 ? ch[0] + 12 : ch[idx]) + 24;
    const f = mtof(n);
    const cut = intro ? 800 + 2400 * clamp(t / DROP) : isBreak(t) ? 1500 + 3000 * (t - BREAK0) / (BREAK1 - BREAK0) : 3600;
    lp.set(cut, 1.3);
    const g = intro ? 0.55 : isBreak(t) ? 1.4 : 0.75;
    return lp.p(a.p(f) - b.p(f)) * Math.exp(-st * 24) * g;
  }, { gain: 0.1, dly: 0.45, pan: 0.15 });
}

// ---------------------------------------------------------------- SFX
const SYN = {
  tick: (o) => put(FXB, o.t, 0.12, (x) => (Math.sin(TAU * 2400 * x) * 0.6 + Math.sin(TAU * 5200 * x) * 0.3) * Math.exp(-x * 60), { gain: 0.45 * o.v, rev: 0.15 }),
  click: (o) => put(FXB, o.t, 0.05, (x) => (Math.sin(TAU * 1800 * x) + noise() * 0.4) * Math.exp(-x * 120), { gain: 0.35 * o.v }),
  pop: (o) => put(FXB, o.t, 0.18, (x) => Math.sin(TAU * (o.f * (1 + 1.2 * Math.exp(-x * 40))) * x) * Math.exp(-x * 26), { gain: 0.45 * o.v, rev: 0.2 }),
  plop: (o) => put(FXB, o.t, 0.1, (x) => Math.sin(TAU * o.f * (1 - x * 3) * x) * Math.exp(-x * 45), { gain: 0.6 * o.v, pan: (o.f % 3 - 1) * 0.4 }),
  ding: (o) => put(FXB, o.t, 0.6, (x) => (Math.sin(TAU * o.f * x) + 0.4 * Math.sin(TAU * o.f * 2.01 * x)) * Math.exp(-x * 7), { gain: 0.3 * o.v, rev: 0.4 }),
  blip: (o) => put(FXB, o.t, 0.09, (x) => Math.sin(TAU * o.f * x + 3 * Math.sin(TAU * o.f * 0.5 * x)) * Math.sin(Math.PI * x / 0.09), { gain: 0.5 * o.v }),
  zap: (o) => {
    const bp = new BQ('bp', 3200, 1.5);
    put(FXB, o.t, 0.32, (x) => {
      const buzz = Math.sign(Math.sin(TAU * (90 + 40 * Math.sin(TAU * 31 * x)) * x)) * 0.35;
      const crack = bp.p(noise()) * (x < 0.06 ? 2 : 1);
      return (crack + buzz) * Math.exp(-x * 11) + Math.sin(TAU * (120 + 900 * Math.exp(-x * 40)) * x) * Math.exp(-x * 20) * 0.6;
    }, { gain: 0.7 * o.v, rev: 0.25 });
  },
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
// ---------------------------------------------------------------- voiceover
// Phrases from voice/vo (made by voice/tts.py), each placed on its beat from voice/lines.json.
const VO = mk();
function readWav(file) {
  const b = fs.readFileSync(file);
  let o = 12, fmt = null;
  while (o < b.length) {
    const id = b.toString('ascii', o, o + 4), len = b.readUInt32LE(o + 4);
    if (id === 'fmt ') fmt = { ch: b.readUInt16LE(o + 10), sr: b.readUInt32LE(o + 12), bits: b.readUInt16LE(o + 22) };
    if (id === 'data') {
      if (!fmt || fmt.sr !== SR || fmt.bits !== 16) throw new Error(`${file}: expected ${SR} Hz 16-bit PCM`);
      const n = len / 2 / fmt.ch, out = new Float32Array(n);
      for (let i = 0; i < n; i++) out[i] = b.readInt16LE(o + 8 + i * 2 * fmt.ch) / 32768;
      return out;
    }
    o += 8 + len + (len & 1);
  }
  throw new Error(`${file}: no data chunk`);
}
const VO_DIR = path.join(__dirname, 'voice', 'vo');
const hasVoice = fs.existsSync(VO_DIR);
if (hasVoice) {
  const sceneStart = (id) => {
    const m = /^conn(\d)$/.exec(id);
    const sc = TL.SCENES.find((x) => (m ? x.id === 'conn' && x.k === +m[1] : x.id === id));
    return sc;
  };
  for (const line of JSON.parse(fs.readFileSync(path.join(__dirname, 'voice', 'lines.json'), 'utf8'))) {
    const sc = sceneStart(line.scene);
    let prevEnd = 0;
    line.segs.forEach((seg, i) => {
      const x = readWav(path.join(VO_DIR, `${line.scene}_${i}.wav`));
      const t0 = Math.max(sc.t0 + seg.at * BEAT, prevEnd + 0.06);
      prevEnd = t0 + x.length / SR;
      if (prevEnd > sc.t1) console.warn(`voice ${line.scene}_${i} runs ${(prevEnd - sc.t1).toFixed(2)}s past its scene`);
      put(VO, t0, x.length / SR, (_, k) => x[k], { rev: 0.12 });
    });
  }
}
fxBus(MUS); fxBus(FXB); fxBus(VO);

// voice sits ~3 dB over the music, and music/SFX duck under it
if (hasVoice) {
  const rms = (B, active) => { let s = 0, n = 0; for (let i = 0; i < N; i++) if (!active || Math.abs(B.L[i]) > 0.01) { s += B.L[i] * B.L[i] + B.R[i] * B.R[i]; n += 2; } return Math.sqrt(s / Math.max(n, 1)); };
  const voRms = rms(VO, true);
  const g = (1.4 * rms(MUS, false)) / voRms;
  const thr = 0.5 * voRms * g; // envelope level that counts as full ducking
  let env = 0;
  const att = Math.exp(-1 / (0.005 * SR)), rel = Math.exp(-1 / (0.22 * SR));
  for (let i = 0; i < N; i++) {
    VO.L[i] *= g; VO.R[i] *= g;
    const a = Math.abs(VO.L[i]);
    env = a > env ? att * env + (1 - att) * a : rel * env + (1 - rel) * a;
    const d = clamp(env / thr);
    MUS.L[i] *= 1 - 0.55 * d; MUS.R[i] *= 1 - 0.55 * d;
    FXB.L[i] *= 1 - 0.3 * d; FXB.R[i] *= 1 - 0.3 * d;
  }
}

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
write(path.join(BUILD, 'audio.wav'), [[MUS, 0.85], [FXB, 0.75], [VO, 0.85]]);
write(path.join(BUILD, 'sfx.wav'), [[FXB, 0.75], [VO, 0.85]]);
write(path.join(BUILD, 'novoice.wav'), [[MUS, 0.85], [FXB, 0.75]]);
