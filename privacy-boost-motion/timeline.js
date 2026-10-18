// Shared timeline: visuals (scenes.js / post.js) and audio (audio.js) all read from here,
// so every slam, impact and whoosh lands on the same frame as its sound.
(function (G) {
  const BPM = 128;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);
  const DURATION = T(34);

  const SFX = [];   // audio cues
  const FX = [];    // post-processing cues (shake, flash, glitch, punch, whip, flare)

  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));

  const hit = (t, v = 1) => {
    s(t, 'hit', { v });
    fx(t, 'shake', { amp: 12 * v, dur: 0.3 });
    fx(t, 'punch', { amp: 0.03 * v, dur: 0.3 });
  };
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    s(t, 'braam', { v: 0.8 * v });
    fx(t, 'shake', { amp: 28 * v, dur: 0.6 });
    fx(t, 'flash', { a: Math.min(0.9, 0.75 * v), dur: 0.45 });
    fx(t, 'glitch', { amp: 0.9, dur: 0.22 });
    fx(t, 'punch', { amp: 0.07 * v, dur: 0.5 });
    fx(t, 'flare', { a: 1, dur: 1.1 });
  };
  const whip = (t, v = 0.6) => {
    s(t - 0.12, 'whoosh', { dur: 0.32, v });
    fx(t - 0.1, 'whip', { amp: 1, dur: 0.22 });
  };

  // ---- shared layout ----
  // transactions fired from the wallet in the exposure scene (and replayed, shielded, later)
  const r01 = (i, j) => {
    let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
    h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
  const TX_MSG = [
    ['Salary received', '+ 4,200 USDC'], ['Swap', '12.0 ETH → USDC'], ['Rent paid', '− 1,850 USDC'],
    ['NFT purchase', '− 2.4 ETH'], ['Bridge', '8,000 USDC → L2'], ['Sent to 0x91c…e04', '− 640 USDC'],
    ['Balance', '38.42 ETH'], ['Medical bill', '− 312 USDC'],
  ];
  const L = { tx: [] };
  for (let j = 0; j < 8; j++) {
    const a = -Math.PI / 2 + j * (Math.PI * 2 / 8) + (r01(j, 1) - 0.5) * 0.35;
    const d = 330 + r01(j, 2) * 120;
    L.tx.push({
      t: T(8, 1) + j * BEAT,
      a, d,
      x: 960 + Math.cos(a) * d * 1.55,
      y: 540 + Math.sin(a) * d * 0.72,
      title: TX_MSG[j][0], val: TX_MSG[j][1],
    });
  }

  // ======================= 0. COLD OPEN (bars 0-4) =======================
  s(0, 'sub', { v: 0.9 });
  s(0.02, 'heartbeat', { v: 0.8 });
  s(T(1) - 0.02, 'heartbeat', { v: 0.9 });
  fx(T(1), 'flare', { a: 0.7, dur: 1.6 });
  s(T(1), 'scramble', { dur: 0.9 });
  s(T(1), 'braam', { v: 0.55 });
  fx(T(1), 'shake', { amp: 6, dur: 0.6 });
  s(T(2), 'whoosh', { dur: 0.6, v: 0.35 });
  s(T(2) + 0.02, 'heartbeat', { v: 1 });
  s(T(3), 'type', { n: 20, dur: 0.6 });
  hit(T(3, 2), 1);
  hit(T(3, 3), 0.7);
  s(T(3), 'riser', { dur: BAR, v: 0.9 });
  s(T(3, 2), 'rev', { dur: BEAT * 2, v: 0.9 });

  // ======================= 1. TRANSPARENCY (bars 4-7) =======================
  boom(T(4), 1.1);
  s(T(4), 'chime', { v: 0.5 });
  [T(4, 2), T(5), T(6)].forEach((t, i) => {
    hit(t, 0.9);
    s(t, 'lock', { n: i * 2, v: 0.5 });
  });
  for (let i = 0; i < 12; i++) s(T(4) + i * BEAT, 'tick', { v: 0.35, f: 1 + (i % 4) * 0.12 });
  s(T(6, 2), 'whoosh', { dur: 0.5, v: 0.4 });

  // ======================= 2. THE TURN (bar 7) =======================
  whip(T(7));
  s(T(7), 'type', { n: 26, dur: 0.9 });
  s(T(7, 2), 'glitch', { dur: 0.5, v: 0.8 });
  fx(T(7, 2), 'glitch', { amp: 0.8, dur: 0.5 });
  s(T(7, 2), 'riser', { dur: BEAT * 2, v: 0.6 });
  fx(T(7, 3.5), 'glitch', { amp: 1, dur: 0.25 });

  // ======================= 3. EXPOSURE (bars 8-13) =======================
  boom(T(8), 1.2);
  s(T(8), 'alarm', { v: 0.5 });
  L.tx.forEach((p, j) => {
    s(p.t, 'zap', { v: 0.6, pan: Math.cos(p.a) * 0.7 });
    s(p.t + 0.18, 'pop', { v: 0.55, f: 0.8 + j * 0.08 });
    fx(p.t, 'shake', { amp: 5, dur: 0.2 });
  });
  // the scanner locks on to each footprint
  for (let j = 0; j < 4; j++) {
    s(T(10, 2) + j * BEAT, 'beep', { v: 0.6, f: 1 + j * 0.1 });
    hit(T(10, 2) + j * BEAT + BEAT / 2, 0.5);
    s(T(10, 2) + j * BEAT + BEAT / 2, 'stamp', { v: 0.6 });
  }
  boom(T(11, 2), 1);
  hit(T(12), 0.9);
  s(T(12), 'type', { n: 40, dur: 1.1 });
  s(T(12, 2), 'heartbeat', { v: 1 });
  fx(T(12, 2), 'glitch', { amp: 0.4, dur: 0.15 });

  // ======================= 4. INFRASTRUCTURE (bar 13) =======================
  s(T(13), 'sub', { v: 0.8 });
  s(T(13), 'riser', { dur: BAR, v: 1.1 });
  s(T(13), 'rev', { dur: BAR, v: 1 });
  s(T(13, 1), 'scramble', { dur: 0.7 });
  hit(T(13, 1), 0.6);
  fx(T(13, 2), 'glitch', { amp: 0.3, dur: BEAT * 1.5 });

  // ======================= 5. THE REVEAL (bars 14-17) =======================
  boom(T(14), 1.35);
  s(T(14), 'shatter', { v: 0.9 });
  for (let i = 0; i < 8; i++) s(T(14) + 0.06 * i, 'lock', { n: i, v: 0.45 });
  hit(T(14, 2), 1.1);
  s(T(14, 2), 'sparkle', { v: 0.8 });
  hit(T(15), 0.9);
  s(T(15), 'slash', { v: 0.8 });
  s(T(15, 2), 'scramble', { dur: 0.6 });
  s(T(16), 'type', { n: 50, dur: 1.2 });
  s(T(16, 2), 'chime', { v: 0.6 });

  // ======================= 6. SHIELD (bars 17-20) =======================
  whip(T(17));
  s(T(17), 'bubble', { v: 1 });
  s(T(17), 'sparkle', { v: 0.6 });
  for (let j = 0; j < 6; j++) {
    const t = T(17, 2) + j * BEAT * 0.5;
    s(t, 'zap', { v: 0.4, pan: (j % 2 ? 0.5 : -0.5) });
    s(t + 0.2, 'scramble', { dur: 0.3, v: 0.8 });
  }
  hit(T(18, 2), 0.8);
  s(T(18, 2), 'denied', { v: 0.7 });
  hit(T(19), 0.6);
  s(T(19), 'type', { n: 40, dur: 1 });

  // ======================= 7. BIGGER PICTURE (bars 20-22) =======================
  whip(T(20), 0.5);
  s(T(20), 'sub', { v: 0.7 });
  s(T(20), 'scramble', { dur: 0.7 });
  s(T(20, 2), 'type', { n: 34, dur: 0.9 });
  for (let i = 0; i < 10; i++) s(T(20, 1) + i * BEAT * 0.5, 'pop', { v: 0.3, f: 1.2 + (i % 5) * 0.12 });
  s(T(21), 'type', { n: 46, dur: 1.2 });
  s(T(21), 'riser', { dur: BAR, v: 0.8 });
  s(T(21, 2), 'rev', { dur: BEAT * 2, v: 0.8 });

  // ======================= 8. WHAT USERS NEED (bars 22-24) =======================
  boom(T(22), 1);
  s(T(22), 'chime', { v: 0.4 });
  hit(T(22, 1), 0.6);
  s(T(22, 1), 'tick', { v: 0.8, f: 1.2 });
  hit(T(22, 2), 0.6);
  s(T(22, 2), 'tick', { v: 0.8, f: 1.35 });
  s(T(22, 3), 'whoosh', { dur: 0.35, v: 0.5 });
  boom(T(23), 0.9);
  s(T(23), 'lock', { n: 5, v: 0.7 });
  s(T(23, 1), 'type', { n: 44, dur: 1.1 });

  // ======================= 9. BALANCE (bars 24-28) =======================
  whip(T(24));
  hit(T(24), 0.7);
  hit(T(24, 2), 1);
  s(T(24, 2), 'creak', { v: 0.8 });
  hit(T(25, 2), 1);
  s(T(25, 2), 'creak', { v: 0.8 });
  s(T(26), 'creak', { v: 0.6 });
  s(T(26, 2), 'scramble', { dur: 0.6 });
  hit(T(26, 2), 0.6);
  boom(T(27), 1.1);
  s(T(27), 'chime', { v: 0.7 });
  s(T(27), 'sparkle', { v: 0.8 });

  // ======================= 10. BARRIER (bars 28-30) =======================
  whip(T(28), 0.5);
  hit(T(28), 0.8);
  s(T(28), 'brick', { v: 0.6 });
  s(T(28, 1), 'brick', { v: 0.5 });
  s(T(28), 'riser', { dur: BAR, v: 1 });
  s(T(28, 2), 'rev', { dur: BEAT * 2, v: 0.9 });
  fx(T(28, 3), 'glitch', { amp: 0.35, dur: BEAT });
  boom(T(29), 1.35);
  s(T(29), 'shatter', { v: 1 });
  s(T(29), 'brick', { v: 0.8 });
  hit(T(29, 2), 0.9);
  s(T(29, 2), 'sparkle', { v: 0.6 });

  // ======================= 11. OUTRO (bars 30-34) =======================
  whip(T(30), 0.5);
  s(T(30), 'type', { n: 46, dur: 1.3 });
  s(T(30, 2), 'riser', { dur: BEAT * 2, v: 0.8 });
  boom(T(31), 1.2);
  for (let i = 0; i < 8; i++) s(T(31) + 0.06 * i, 'lock', { n: i, v: 0.4 });
  hit(T(31, 2), 0.9);
  s(T(31, 2), 'sparkle', { v: 0.7 });
  s(T(32), 'scramble', { dur: 0.6 });
  s(T(32, 2), 'chime', { v: 0.6 });
  s(T(33), 'heartbeat', { v: 0.6 });

  SFX.sort((a, b) => a.t - b.t);

  G.TL = { BPM, BEAT, BAR, T, DURATION, SFX, FX, CUR: { POS: [], ALPHA: [] }, L };
})(typeof window !== 'undefined' ? window : globalThis);
