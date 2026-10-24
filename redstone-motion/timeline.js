// Shared timeline: visuals (motion.js) and audio (audio.js) both read from here,
// so every braam, hit and whoosh lands on the same frame as its sound.
(function (G) {
  const BPM = 120;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);
  const DURATION = T(28) + 0.6;

  const SFX = [];   // audio cues
  const FX = [];    // post-processing cues (shake, flash, glitch, punch, whip, ca)

  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));

  const hit = (t, v = 1) => {
    s(t, 'hit', { v });
    fx(t, 'shake', { amp: 10 * v, dur: 0.35 });
    fx(t, 'punch', { amp: 0.025 * v, dur: 0.35 });
    fx(t, 'ca', { amp: 6 * v, dur: 0.3 });
  };
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    s(t, 'braam', { v: v * 0.9, dur: 3.2 });
    fx(t, 'shake', { amp: 26 * v, dur: 0.7 });
    fx(t, 'flash', { a: Math.min(0.85, 0.7 * v), dur: 0.5 });
    fx(t, 'punch', { amp: 0.06 * v, dur: 0.6 });
    fx(t, 'ca', { amp: 18 * v, dur: 0.6 });
  };
  const whip = (t, v = 0.7) => {
    s(t - 0.14, 'whoosh', { dur: 0.36, v });
    fx(t - 0.1, 'whip', { amp: 1, dur: 0.24 });
  };

  // ================== 0. COLD OPEN: gem assembles (bars 0-4) ==================
  s(0, 'sub', { v: 0.8 });
  s(0, 'drone', { dur: T(4), v: 0.9 });
  // 18 edges draw in: outer ring, spokes, inner ring
  const EDGE_T = [];
  for (let i = 0; i < 18; i++) {
    const t = 0.5 + i * 0.17;
    EDGE_T.push(t);
    s(t, 'tick', { f: 1 + (i % 6) * 0.12, v: 0.7 });
  }
  s(T(1, 2), 'riser', { dur: T(2) - T(1, 2) });
  boom(T(2), 0.85);                       // light ignites behind the gem
  fx(T(2), 'rays', { dur: 4 });
  whip(T(3), 0.6);
  s(T(3), 'shimmer', { v: 0.8 });
  hit(T(3), 0.6);
  s(T(3, 2), 'pad', { v: 0.6 });

  // ================== 1. AVS reveal (bars 4-6) ==================
  whip(T(4), 0.8);
  s(T(4) + 0.1, 'type', { n: 34, dur: 1.4, v: 0.8 });
  s(T(4, 2), 'rev', { dur: T(5) - T(4, 2) });
  boom(T(5), 1);
  s(T(5), 'shimmer', { v: 1 });

  // ================== 2. Why does it matter? (bar 6-7) ==================
  whip(T(6), 0.6);
  s(T(6), 'scramble', { dur: 0.7, v: 0.9 });
  s(T(6, 1), 'riser', { dur: T(7) - T(6, 1), v: 1.1 });
  fx(T(6, 3.5), 'glitch', { amp: 0.6, dur: 0.25 });
  s(T(6, 3.5), 'glitch', { dur: 0.25, v: 0.8 });

  // ================== 3. Risk: price feed → manipulation → liquidations (bars 7-10) ==================
  hit(T(7), 1);
  s(T(7), 'crash', { v: 1 });
  // manipulation
  fx(T(8, 2), 'glitch', { amp: 1, dur: 0.45 });
  s(T(8, 2), 'glitch', { dur: 0.45, v: 1.1 });
  s(T(8, 2), 'drop', { v: 1 });
  fx(T(8, 2), 'shake', { amp: 22, dur: 0.6 });
  fx(T(8, 2), 'ca', { amp: 20, dur: 0.7 });
  s(T(8, 3), 'alarm', { v: 1 });
  // liquidations cascade
  hit(T(9), 1.1);
  for (let k = 0; k < 8; k++) s(T(9) + 0.12 + k * BEAT / 2, 'error', { v: 0.55 + k * 0.05 });
  s(T(9, 2), 'alarm', { v: 1 });
  fx(T(9, 3.5), 'glitch', { amp: 0.8, dur: 0.3 });
  s(T(9, 3.5), 'glitch', { dur: 0.3, v: 0.9 });

  // ================== 4. A different approach (bar 10-11) ==================
  s(T(10), 'sub', { v: 1 });
  fx(T(10), 'flash', { a: 0.35, dur: 0.4 });
  s(T(10), 'shimmer', { v: 0.8 });
  s(T(10, 1), 'riser', { dur: T(11) - T(10, 1), v: 0.9 });
  s(T(10, 1), 'rev', { dur: T(11) - T(10, 1) });

  // ================== 5. Pipeline (bars 11-17) ==================
  boom(T(11), 0.8);
  const NODE_T = [];
  for (let i = 0; i < 7; i++) { const t = T(11, 0.75) + i * 0.27; NODE_T.push(t); s(t, 'lock', { n: i, v: 0.8 }); }
  s(T(12), 'stream', { dur: 2.6, v: 0.8 });
  // step 2: aggregate
  whip(T(12, 2), 0.5);
  hit(T(12, 2), 0.6);
  const ROW_T = [];
  for (let i = 0; i < 7; i++) { const t = T(12, 2.6) + i * 0.16; ROW_T.push(t); s(t, 'tick', { f: 1.4, v: 0.6 }); }
  s(T(13, 1.2), 'error', { v: 0.7 });      // outlier rejected
  hit(T(13, 2.5), 0.7);                    // median locked
  s(T(13, 2.5), 'chime', { v: 0.9 });
  // step 3: operators validate
  whip(T(14), 0.5);
  hit(T(14), 0.6);
  const OP_T = [];
  for (let i = 0; i < 12; i++) { const t = T(14, 0.5) + i * 0.15; OP_T.push(t); s(t, 'pop', { f: 1 + (i % 4) * 0.15, v: 0.55 }); }
  hit(T(15, 1), 0.8);                      // consensus reached
  s(T(15, 1), 'chime', { v: 1 });
  // step 4: delivered onchain
  whip(T(15, 2), 0.6);
  s(T(15, 2.5), 'riser', { dur: T(16, 2) - T(15, 2.5), v: 0.8 });
  s(T(16, 2), 'beam', { v: 1 });
  hit(T(16, 2), 1);
  fx(T(16, 2), 'flash', { a: 0.4, dur: 0.35 });
  s(T(16, 3), 'chime', { v: 0.9 });

  // ================== 6. Efficient + secure (bars 17-19) ==================
  boom(T(17), 0.9);
  hit(T(18), 0.9);
  s(T(18), 'shimmer', { v: 0.7 });

  // ================== 7. $RED economic layer (bars 19-21) ==================
  whip(T(19), 0.8);
  s(T(19), 'sub', { v: 0.9 });
  s(T(19), 'coin', { v: 1 });
  fx(T(19), 'flash', { a: 0.3, dur: 0.3 });
  s(T(19, 2), 'sparkle', { v: 1 });
  hit(T(20), 0.8);
  s(T(20), 'lock', { n: 5, v: 0.8 });

  // ================== 8. Takeaway (bars 21-23) ==================
  whip(T(21), 0.6);
  s(T(21) + 0.1, 'type', { n: 20, dur: 0.6, v: 0.7 });
  s(T(21, 1), 'riser', { dur: T(22) - T(21, 1), v: 0.8 });
  hit(T(22), 0.8);
  hit(T(22, 2), 1);
  hit(T(22, 3), 1.1);
  fx(T(22, 3), 'flash', { a: 0.3, dur: 0.3 });

  // ================== 9. Metrics to watch (bars 23-25) ==================
  whip(T(23), 0.7);
  hit(T(23, 1), 0.7);
  hit(T(23, 3), 0.75);
  hit(T(24, 1), 0.8);
  s(T(24, 2), 'rev', { dur: T(25) - T(24, 2), v: 1.1 });
  s(T(24, 2), 'riser', { dur: T(25) - T(24, 2), v: 1.1 });

  // ================== 10. Outro (bars 25-28) ==================
  boom(T(25), 1.15);
  fx(T(25), 'rays', { dur: 6 });
  s(T(25), 'shimmer', { v: 1.1 });
  s(T(26), 'chime', { v: 0.8 });
  s(T(27), 'sub', { v: 0.6 });

  G.TL = { BPM, BEAT, BAR, T, DURATION, SFX, FX, EDGE_T, NODE_T, ROW_T, OP_T };
  if (typeof module !== 'undefined') module.exports = G.TL;
})(typeof window !== 'undefined' ? window : globalThis);
