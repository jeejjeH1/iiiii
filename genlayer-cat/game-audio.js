// Live WebAudio for Truth Runner: a 120 BPM loop (F minor) that thickens with speed, plus SFX.
// Everything is synthesized; nothing is loaded.
const BPM = 120, STEP = 60 / BPM / 4; // 16th note
const PROG = [[53, 56, 60, 41], [53, 56, 61, 37], [51, 56, 60, 44], [51, 55, 58, 39]]; // Fm Db Ab Eb
const ARP = [0, 1, 2, 3, 2, 1, 2, 4, 0, 1, 2, 3, 4, 3, 2, 1];
const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);

export class Sound {
  constructor() {
    this.ctx = null;
    this.muted = false;
    try { this.muted = localStorage.getItem('tr-muted') === '1'; } catch (e) { /* storage blocked */ }
    this.music = false;
    this.energy = 0; // 0..1, follows run speed
    this.step = 0;
    this.nextTime = 0;
  }
  // must run inside a user gesture
  unlock() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      this.ctx = new AC();
      const c = this.ctx;
      this.comp = c.createDynamicsCompressor();
      this.comp.threshold.value = -14; this.comp.ratio.value = 4;
      this.master = c.createGain();
      this.master.gain.value = this.muted ? 0 : 0.8;
      this.comp.connect(this.master).connect(c.destination);
      this.musicBus = c.createGain(); this.musicBus.gain.value = 0.55; this.musicBus.connect(this.comp);
      this.sfxBus = c.createGain(); this.sfxBus.gain.value = 0.9; this.sfxBus.connect(this.comp);
      // short room for space
      this.verb = c.createConvolver();
      const len = c.sampleRate * 1.6, ir = c.createBuffer(2, len, c.sampleRate);
      for (let ch = 0; ch < 2; ch++) { const d = ir.getChannelData(ch); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3); }
      this.verb.buffer = ir;
      this.verbGain = c.createGain(); this.verbGain.gain.value = 0.25;
      this.verb.connect(this.verbGain).connect(this.comp);
      this.noise = c.createBuffer(1, c.sampleRate, c.sampleRate);
      const nd = this.noise.getChannelData(0); for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;
      this.timer = setInterval(() => this.schedule(), 25);
    }
    if (this.ctx.state === 'suspended') this.ctx.resume();
  }
  setMuted(m) {
    this.muted = m;
    try { localStorage.setItem('tr-muted', m ? '1' : '0'); } catch (e) { /* storage blocked */ }
    if (this.master) this.master.gain.setTargetAtTime(m ? 0 : 0.8, this.ctx.currentTime, 0.05);
  }
  startMusic() { if (!this.ctx) return; if (!this.music) { this.music = true; this.step = 0; this.nextTime = this.ctx.currentTime + 0.08; } }
  stopMusic() { this.music = false; }

  // ---------------------------------------------------------------- music
  schedule() {
    if (!this.music || !this.ctx) return;
    while (this.nextTime < this.ctx.currentTime + 0.12) { this.playStep(this.step, this.nextTime); this.nextTime += STEP; this.step++; }
  }
  playStep(s, t) {
    const bar = Math.floor(s / 16) % 4, k = s % 16, ch = PROG[bar], e = this.energy;
    if (k % 4 === 0) this.kick(t, 0.9);
    if (k % 4 === 2) this.hat(t, 0.5, true);
    if (e > 0.25 && k % 2 === 1) this.hat(t, 0.22, false);
    if (e > 0.55 && (k === 4 || k === 12)) this.clap(t);
    if (k % 4 === 2 || (e > 0.4 && k % 4 === 3)) this.bass(t, mtof(ch[3] + 12), STEP * 1.6);
    if (k === 0) this.pad(t, ch, STEP * 16);
    if (e > 0.12 || k % 2 === 0) this.pluck(t, mtof([ch[0], ch[1], ch[2], ch[0] + 12, ch[1] + 12][ARP[k]] + 12), k % 2 ? 0.35 : -0.35);
  }
  env(g, t, a, peak, d) { g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(peak, t + a); g.gain.exponentialRampToValueAtTime(0.0001, t + a + d); }
  kick(t, v) {
    const c = this.ctx, o = c.createOscillator(), g = c.createGain();
    o.frequency.setValueAtTime(150, t); o.frequency.exponentialRampToValueAtTime(44, t + 0.12);
    this.env(g, t, 0.004, v, 0.32);
    o.connect(g).connect(this.musicBus); o.start(t); o.stop(t + 0.4);
  }
  noiseHit(t, freq, type, q, v, d, bus = this.musicBus, pan = 0) {
    const c = this.ctx, n = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain(), p = c.createStereoPanner();
    n.buffer = this.noise; f.type = type; f.frequency.value = freq; f.Q.value = q; p.pan.value = pan;
    this.env(g, t, 0.002, v, d);
    n.connect(f).connect(g).connect(p).connect(bus); n.start(t, Math.random() * 0.5); n.stop(t + d + 0.05);
    return { f, g };
  }
  hat(t, v, open) { this.noiseHit(t, 8000, 'highpass', 0.7, v * 0.35, open ? 0.12 : 0.04, this.musicBus, open ? 0.15 : -0.25); }
  clap(t) { const h = this.noiseHit(t, 1300, 'bandpass', 1.2, 0.5, 0.18); h.g.connect(this.verb); }
  bass(t, f, d) {
    const c = this.ctx, o = c.createOscillator(), lp = c.createBiquadFilter(), g = c.createGain();
    o.type = 'sawtooth'; o.frequency.value = f; lp.type = 'lowpass'; lp.Q.value = 6;
    lp.frequency.setValueAtTime(1800, t); lp.frequency.exponentialRampToValueAtTime(220, t + d);
    this.env(g, t, 0.005, 0.32, d);
    o.connect(lp).connect(g).connect(this.musicBus); o.start(t); o.stop(t + d + 0.05);
  }
  pad(t, ch, d) {
    const c = this.ctx, lp = c.createBiquadFilter(), g = c.createGain();
    lp.type = 'lowpass'; lp.frequency.value = 900 + this.energy * 1600;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.06, t + 0.4); g.gain.setValueAtTime(0.06, t + d - 0.3); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    lp.connect(g).connect(this.musicBus); g.connect(this.verb);
    for (const m of [ch[0], ch[1], ch[2], ch[0] + 12]) for (const det of [-6, 6]) {
      const o = c.createOscillator(); o.type = 'sawtooth'; o.frequency.value = mtof(m); o.detune.value = det;
      o.connect(lp); o.start(t); o.stop(t + d + 0.05);
    }
  }
  pluck(t, f, pan) {
    const c = this.ctx, o = c.createOscillator(), lp = c.createBiquadFilter(), g = c.createGain(), p = c.createStereoPanner();
    o.type = 'square'; o.frequency.value = f; lp.type = 'lowpass'; lp.Q.value = 3; p.pan.value = pan;
    lp.frequency.setValueAtTime(600 + 3200 * (0.3 + this.energy), t); lp.frequency.exponentialRampToValueAtTime(300, t + 0.18);
    this.env(g, t, 0.003, 0.05, 0.2);
    o.connect(lp).connect(g).connect(p).connect(this.musicBus); g.connect(this.verb);
    o.start(t); o.stop(t + 0.25);
  }

  // ---------------------------------------------------------------- sfx
  tone(f0, f1, d, { type = 'sine', v = 0.3, at = 0, verb = 0.2, pan = 0 } = {}) {
    if (!this.ctx) return;
    const c = this.ctx, t = c.currentTime + at, o = c.createOscillator(), g = c.createGain(), p = c.createStereoPanner();
    o.type = type; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + d);
    p.pan.value = pan; this.env(g, t, 0.004, v, d);
    o.connect(g).connect(p).connect(this.sfxBus);
    if (verb) { const vg = c.createGain(); vg.gain.value = verb; g.connect(vg).connect(this.verb); }
    o.start(t); o.stop(t + d + 0.05);
  }
  sfx(name, arg = 0) {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    switch (name) {
      case 'collect': { const b = 880 * Math.pow(2, Math.min(arg, 12) / 12); this.tone(b, b * 1.5, 0.09, { v: 0.22 }); this.tone(b * 1.5, b * 2, 0.16, { v: 0.16, at: 0.06, verb: 0.4 }); break; }
      case 'fake': this.tone(420, 140, 0.25, { type: 'square', v: 0.12 }); this.noiseHit(t, 2400, 'bandpass', 2, 0.25, 0.18, this.sfxBus); break;
      case 'jump': this.tone(260, 760, 0.18, { v: 0.16 }); break;
      case 'slide': this.noiseHit(t, 900, 'bandpass', 1.2, 0.35, 0.3, this.sfxBus); break;
      case 'lane': this.tone(1500, 1100, 0.04, { v: 0.06, verb: 0 }); break;
      case 'hit': this.tone(140, 40, 0.4, { v: 0.6, verb: 0.3 }); this.noiseHit(t, 700, 'lowpass', 0.8, 0.6, 0.3, this.sfxBus); break;
      case 'gateOk': [0, 4, 7, 12].forEach((s, i) => this.tone(mtof(72 + s), mtof(72 + s), 0.5, { v: 0.14, at: i * 0.06, verb: 0.5, pan: (i - 1.5) * 0.3 })); break;
      case 'gateBad': this.tone(220, 110, 0.5, { type: 'sawtooth', v: 0.12 }); this.tone(233, 116, 0.5, { type: 'sawtooth', v: 0.1 }); break;
      case 'claim': this.tone(660, 990, 0.12, { v: 0.12, verb: 0.4 }); this.tone(990, 1320, 0.12, { v: 0.1, at: 0.1, verb: 0.4 }); break;
      case 'start': [0, 3, 7, 12, 15].forEach((s, i) => this.tone(mtof(65 + s), mtof(65 + s), 0.25, { v: 0.12, at: i * 0.07, verb: 0.4 })); break;
      case 'over': [12, 8, 5, 0].forEach((s, i) => this.tone(mtof(65 + s), mtof(64 + s), 0.35, { type: 'triangle', v: 0.16, at: i * 0.14, verb: 0.4 })); break;
      default: break;
    }
  }
}
