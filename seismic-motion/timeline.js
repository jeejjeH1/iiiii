// Shared timeline: visuals (motion.js) and audio (audio.js) both read from here,
// so every quake, lock and whoosh lands on the same frame as its sound.
(function (G) {
  const BPM = 120;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);
  const DURATION = T(36);

  const SFX = [];   // audio cues
  const FX = [];    // post-processing cues (shake, flash, glitch, punch, whip, quake)

  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));

  const hit = (t, v = 1) => {
    s(t, 'hit', { v });
    fx(t, 'shake', { amp: 9 * v, dur: 0.3 });
    fx(t, 'punch', { amp: 0.025 * v, dur: 0.3 });
  };
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    fx(t, 'shake', { amp: 24 * v, dur: 0.7 });
    fx(t, 'flash', { a: Math.min(0.85, 0.7 * v), dur: 0.45 });
    fx(t, 'punch', { amp: 0.06 * v, dur: 0.5 });
  };
  const whip = (t, v = 0.6) => {
    s(t - 0.14, 'whoosh', { dur: 0.36, v });
    fx(t - 0.12, 'whip', { amp: 1, dur: 0.26 });
  };

  // ---- key moments (motion.js reads these by name) ----
  const K = {
    quake: T(4),
    facets: [T(5, 0), T(5, 1), T(5, 2), T(5, 3), T(6, 0)],
    white: T(6, 2),
    tagline: T(7),
    ch1: T(8), ch2: T(12), ch3: T(16),
    drop: T(20),
    principles: T(24),
    powering: T(27),
    finale: T(28),
    endcard: T(31),
  };

  // ======================= 0. SIGNAL (bars 0-4) =======================
  s(0, 'sub', { v: 0.5 });
  s(0.2, 'scan', { dur: 1.6, v: 0.5 });
  for (let b = 1; b < 4; b++) {
    s(T(b), 'thump', { v: 0.45 + b * 0.15 });
    s(T(b, 0.45), 'thump', { v: 0.3 + b * 0.12 });
    fx(T(b), 'quake', { amp: 0.25 * b, dur: 0.5 });
    fx(T(b), 'shake', { amp: 2 + b * 2, dur: 0.35 });
  }
  s(T(0, 2), 'type', { n: 38, dur: 1.2, v: 0.6 });
  s(T(2), 'type', { n: 36, dur: 1.2, v: 0.6 });
  s(T(2), 'glitch', { dur: 0.25, v: 0.4 });
  fx(T(2), 'glitch', { amp: 0.35, dur: 0.25 });
  s(T(3), 'riser', { dur: BAR - 0.25, v: 1 });
  s(T(3, 2), 'rev', { dur: BAR / 2 - 0.25, v: 0.9 });
  fx(T(3, 2), 'quake', { amp: 0.9, dur: 0.75 });
  fx(T(3, 2), 'shake', { amp: 7, dur: 0.75 });
  fx(T(3, 3), 'glitch', { amp: 0.5, dur: 0.25 });

  // ======================= 1. QUAKE + LOGO (bars 4-8) =======================
  s(K.quake, 'quake', { v: 1.2 });
  boom(K.quake, 1.25);
  fx(K.quake, 'quake', { amp: 2.4, dur: 1.4 });
  fx(K.quake, 'glitch', { amp: 1, dur: 0.3 });
  s(K.quake + 0.05, 'shatter', { v: 1 });
  s(T(4, 2), 'whoosh', { dur: 1.4, v: 0.5, pan: 0.9 });
  s(T(4, 2), 'riser', { dur: BAR * 0.75, v: 0.55 });
  K.facets.forEach((t, i) => {
    s(t - 0.22, 'whoosh', { dur: 0.26, v: 0.35, pan: i % 2 ? 0.6 : -0.6 });
    s(t, 'lock', { n: i });
    fx(t, 'shake', { amp: 5 + i * 1.5, dur: 0.22 });
    fx(t, 'punch', { amp: 0.014, dur: 0.22 });
  });
  s(T(6), 'shimmer', { v: 0.8 });
  s(T(6, 1), 'rev', { dur: BEAT, v: 0.7 });
  boom(K.white, 0.9);
  s(K.white, 'chime', { v: 1 });
  s(T(7), 'pop', { v: 0.5, f: 1 });
  s(T(7, 1), 'pop', { v: 0.5, f: 1.12 });
  s(T(7, 2), 'pop', { v: 0.55, f: 1.25 });
  s(T(7, 2), 'riser', { dur: BEAT * 2, v: 0.6 });

  // ======================= 2. CREATE ACCOUNT (bars 8-12) =======================
  whip(K.ch1, 0.7);
  hit(K.ch1, 0.9);
  s(T(8, 2), 'glass', { v: 0.8 });
  s(T(9), 'type', { n: 26, dur: 0.75 });
  s(T(9, 2), 'type', { n: 10, dur: 0.4 });
  s(T(10), 'type', { n: 12, dur: 0.4 });
  [T(10, 2), T(10, 3), T(11, 0)].forEach((t, i) => { s(t, 'tick', { v: 0.8, f: 1 + i * 0.12 }); fx(t, 'punch', { amp: 0.008, dur: 0.15 }); });
  hit(T(11, 2), 0.7);
  s(T(11, 2), 'chime', { v: 0.6 });

  // ======================= 3. SEND PAYMENTS (bars 12-16) =======================
  whip(K.ch2, 0.7);
  hit(K.ch2, 0.9);
  s(T(12, 2), 'glass', { v: 0.8 });
  s(T(13), 'count', { dur: 1.0, v: 0.7 });
  s(T(13, 3), 'scramble', { dur: 0.75 });
  s(T(14), 'lock', { n: 5, v: 0.7 });
  s(T(14, 1), 'beam', { dur: 1.3, v: 0.9 });
  fx(T(14, 1), 'shake', { amp: 5, dur: 0.6 });
  hit(T(15), 0.8);
  s(T(15), 'chime', { v: 0.7 });

  // ======================= 4. BUNDLE SERVICES (bars 16-20) =======================
  whip(K.ch3, 0.7);
  hit(K.ch3, 0.9);
  s(T(16, 2), 'glass', { v: 0.8 });
  for (let i = 0; i < 6; i++) {
    const t = T(17, i * 0.5);
    s(t, 'pop', { v: 0.6, f: 0.9 + i * 0.1 });
    fx(t, 'punch', { amp: 0.008, dur: 0.15 });
  }
  hit(T(18, 2), 0.8);
  s(T(18, 2), 'lock', { n: 7, v: 0.8 });
  s(T(19), 'riser', { dur: BAR - 0.25, v: 1 });
  s(T(19, 2), 'rev', { dur: BAR / 2 - 0.25, v: 1 });
  fx(T(19, 2), 'quake', { amp: 0.6, dur: 0.75 });
  fx(T(19, 3), 'glitch', { amp: 0.45, dur: 0.25 });

  // ======================= 5. ALL IN ONE PLACE (bars 20-24) =======================
  boom(K.drop, 1.15);
  fx(K.drop, 'quake', { amp: 1.2, dur: 0.8 });
  const words = [T(20), T(20, 2), T(21), T(21, 2), T(22)];
  words.forEach((t, i) => { if (i) { hit(t, 0.75); s(t - 0.1, 'whoosh', { dur: 0.2, v: 0.3, pan: i % 2 ? 0.5 : -0.5 }); } });
  hit(T(22, 2), 1);
  boom(T(23), 0.8);
  s(T(23), 'chime', { v: 0.7 });
  s(T(23, 2), 'rev', { dur: BEAT * 2, v: 0.6 });

  // ======================= 6. PRINCIPLES (bars 24-28) =======================
  whip(K.principles, 0.6);
  hit(K.principles, 0.8);
  hit(T(25), 0.7); whip(T(25), 0.45);
  hit(T(26), 0.7); whip(T(26), 0.45);
  s(T(27), 'sub', { v: 0.7 });
  s(T(27), 'shimmer', { v: 0.6 });
  s(T(27), 'riser', { dur: BAR - 0.25, v: 1.1 });
  s(T(27, 2), 'rev', { dur: BAR / 2 - 0.25, v: 1 });
  fx(T(27, 3), 'quake', { amp: 0.7, dur: 0.5 });

  // ======================= 7. FINALE (bars 28-36) =======================
  boom(K.finale, 1.2);
  s(K.finale, 'quake', { v: 0.8 });
  fx(K.finale, 'quake', { amp: 1.6, dur: 1.1 });
  s(T(29), 'shimmer', { v: 0.7 });
  s(T(30), 'whoosh', { dur: 1.2, v: 0.5 });
  s(T(30, 2), 'riser', { dur: BEAT * 2, v: 0.7 });
  boom(K.endcard, 1.0);
  s(K.endcard, 'chime', { v: 1 });
  for (let i = 0; i < 5; i++) s(K.endcard + 0.05 * i, 'lock', { n: i, v: 0.35 });
  s(T(32), 'pop', { v: 0.5, f: 1 });
  s(T(32, 1), 'pop', { v: 0.5, f: 1.2 });
  s(T(32, 3), 'type', { n: 16, dur: 0.6, v: 0.5 });
  s(T(33, 2), 'click', { v: 1 });
  s(T(34), 'sub', { v: 0.5 });

  SFX.sort((a, b) => a.t - b.t);
  G.TL = { BPM, BEAT, BAR, T, DURATION, SFX, FX, K };
})(typeof window !== 'undefined' ? window : globalThis);
