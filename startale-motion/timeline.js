// Shared timeline: visuals (motion.js), cursor, and audio (audio.js) all read from here,
// so every slam, click and whoosh lands on the same frame as its sound.
(function (G) {
  const BPM = 128;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);
  const DURATION = T(42);

  const SFX = [];   // audio cues
  const FX = [];    // post-processing cues (shake, flash, glitch, punch, whip)
  const POS = [];   // cursor position keyframes
  const ALPHA = []; // cursor visibility keyframes

  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));
  const kf = (t, x, y, o = {}) => POS.push(Object.assign({ t, x, y }, o));
  const va = (t, a) => ALPHA.push({ t, a });

  const hit = (t, v = 1) => {
    s(t, 'hit', { v });
    fx(t, 'shake', { amp: 12 * v, dur: 0.3 });
    fx(t, 'punch', { amp: 0.03 * v, dur: 0.3 });
  };
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    fx(t, 'shake', { amp: 28 * v, dur: 0.6 });
    fx(t, 'flash', { a: Math.min(0.9, 0.75 * v), dur: 0.45 });
    fx(t, 'glitch', { amp: 0.9, dur: 0.22 });
    fx(t, 'punch', { amp: 0.07 * v, dur: 0.5 });
  };
  const whip = (t, v = 0.6) => {
    s(t - 0.12, 'whoosh', { dur: 0.32, v });
    fx(t - 0.1, 'whip', { amp: 1, dur: 0.22 });
  };

  // ---- shared layout (cursor targets must match what motion.js draws) ----
  const L = {
    send: { x: 960, y: 660, w: 440, h: 112 },
    win: [
      { x: 690, y: 520 }, { x: 810, y: 545 }, { x: 930, y: 570 },
      { x: 1050, y: 595 }, { x: 1170, y: 620 },
    ],
    winW: 760, winH: 520,
    phone: { x: 1360, y: 545, w: 430, h: 860 },
    poll: [
      { x: 580, y: 600 }, { x: 1340, y: 600 },
      { x: 580, y: 800 }, { x: 1340, y: 800 },
    ],
    pollW: 700, pollH: 160,
    chaos: [],
  };
  const CHAOS_MSG = [
    'Transaction failed', 'Nonce too low', 'RPC error -32603', 'Switch network?',
    'Approve token spending?', 'Gas estimation failed', 'Unknown signature',
    'Bridge pending... 23 min', 'Slippage too high', 'Insufficient funds',
    'Wrong chain ID', 'Session expired',
  ];
  // deterministic pseudo-random for layout
  const r01 = (i, j) => {
    let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(j + 0x632be5ab, 0xc2b2ae35);
    h ^= h >>> 16; h = Math.imul(h, 0x7feb352d); h ^= h >>> 15; h = Math.imul(h, 0x846ca68b); h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
  for (let j = 0; j < 12; j++) {
    L.chaos.push({
      t: T(15) + j * BEAT / 2,
      x: 330 + r01(j, 11) * 1260,
      y: 210 + r01(j, 12) * 660,
      msg: CHAOS_MSG[j],
    });
  }

  // ======================= 0. INTRO (bars 0-4) =======================
  s(0, 'glitch', { dur: 0.4, v: 0.55 });
  fx(0, 'glitch', { amp: 1, dur: 0.35 });
  s(0.05, 'sub', { v: 0.7 });
  va(0.35, 0); va(0.65, 1);
  kf(0.35, 1620, 880); kf(0.75, 1620, 880);
  kf(1.72, 960, 540); kf(T(1), 960, 540, { click: 1 });
  va(T(1) + 0.35, 1); va(T(1) + 0.75, 0);
  boom(T(1), 0.75);
  for (let i = 0; i < 8; i++) {
    const t = T(1, 1 + i);
    s(t - 0.2, 'whoosh', { dur: 0.24, v: 0.32, pan: i % 2 ? 0.6 : -0.6 });
    s(t, 'lock', { n: i });
    fx(t, 'shake', { amp: 5 + i * 1.2, dur: 0.22 });
    fx(t, 'punch', { amp: 0.012, dur: 0.2 });
  }
  hit(T(3), 0.9);
  fx(T(3), 'flash', { a: 0.35, dur: 0.3 });
  s(T(3), 'riser', { dur: BAR, v: 0.9 });
  s(T(3, 2), 'rev', { dur: BAR / 2, v: 0.8 });
  fx(T(3, 3), 'glitch', { amp: 0.4, dur: BEAT });

  // ======================= 1. HOOK (bars 4-7) =======================
  boom(T(4), 1.2);
  hit(T(4, 1), 0.9);
  hit(T(4, 2), 1.1);
  s(T(4, 2), 'whoosh', { dur: 0.6, v: 0.6 });
  whip(T(5));
  hit(T(5), 0.5);
  hit(T(5, 1), 1.1);
  whip(T(6));
  hit(T(6, 0), 0.8); hit(T(6, 1), 0.85); hit(T(6, 2), 0.9);
  boom(T(6, 3), 0.9);
  s(T(6, 3), 'glitch', { dur: 0.45, v: 0.6 });
  fx(T(6, 3), 'glitch', { amp: 0.7, dur: 0.45 });

  // ======================= 2. COMPLEXITY (bars 7-9) =======================
  whip(T(7));
  s(T(7), 'scramble', { dur: 0.7 });
  boom(T(7, 2), 1.0);
  s(T(7, 2), 'alarm', { v: 0.35 });
  s(T(8), 'type', { n: 26, dur: 0.85 });
  fx(T(8, 3), 'glitch', { amp: 0.4, dur: 0.15 });

  // ======================= 3. THE NIGHTMARE (bars 9-17) =======================
  whip(T(9));
  s(T(9), 'type', { n: 34, dur: 1.0 });
  s(T(9, 1.5), 'pop', { v: 0.8 });
  va(T(9, 1), 0); va(T(9, 1.4), 1);
  kf(T(9, 1), 1500, 980);
  kf(T(9, 2.7), L.send.x, L.send.y, { hand: 1 });
  kf(T(9, 3), L.send.x, L.send.y, { click: 1, hand: 1 });

  const WB = [
    { x: L.win[0].x, y: L.win[0].y + 175 },
    { x: L.win[1].x, y: L.win[1].y + 190 },
    null,
    { x: L.win[3].x, y: L.win[3].y + 175 },
  ];
  for (let i = 0; i < 5; i++) {
    s(T(10 + i), 'pop', { v: 1 });
    s(T(10 + i), 'whoosh', { dur: 0.2, v: 0.25 });
    hit(T(10 + i), 0.45);
    s(T(10 + i, 0.15), 'tick', { v: 0.5, f: 1 + i * 0.25 });
  }
  // step 1: create wallet
  kf(T(10, 0.3), L.send.x, L.send.y);
  kf(T(10, 2.6), WB[0].x, WB[0].y, { hand: 1 });
  kf(T(10, 3), WB[0].x, WB[0].y, { click: 1, hand: 1 });
  s(T(10, 0.6), 'type', { n: 15, dur: 0.6, v: 0.5 });
  s(T(10, 1.5), 'type', { n: 10, dur: 0.4, v: 0.5 });
  // step 2: recovery phrase
  s(T(11, 0.3), 'scramble', { dur: 0.9, v: 0.7 });
  kf(T(11, 0.3), WB[0].x, WB[0].y);
  kf(T(11, 2.6), WB[1].x, WB[1].y, { hand: 1 });
  kf(T(11, 3), WB[1].x, WB[1].y, { click: 1, hand: 1 });
  // step 3: choose network (confused wiggle)
  kf(T(12, 0.3), WB[1].x, WB[1].y);
  kf(T(12, 0.9), 880, 470, { tick: 1 });
  kf(T(12, 1.3), 1010, 520, { tick: 1 });
  kf(T(12, 1.7), 870, 560, { tick: 1 });
  kf(T(12, 2.1), 1000, 610, { tick: 1 });
  kf(T(12, 2.5), 910, 520, { tick: 1 });
  kf(T(12, 2.8), 930, 650, { hand: 1 });
  kf(T(12, 3), 930, 650, { click: 1, hand: 1 });
  s(T(12, 3) + 0.08, 'error', { v: 0.8 });
  fx(T(12, 3) + 0.08, 'shake', { amp: 10, dur: 0.3 });
  // step 4: gas
  kf(T(13, 0.3), 930, 650);
  kf(T(13, 1.7), WB[3].x, WB[3].y, { hand: 1 });
  kf(T(13, 2), WB[3].x, WB[3].y, { click: 1, hand: 1 });
  kf(T(13, 2.5), WB[3].x + 6, WB[3].y + 4, { hand: 1 });
  kf(T(13, 3), WB[3].x, WB[3].y, { click: 1, hand: 1 });
  s(T(13, 2) + 0.06, 'error', { v: 0.9 });
  s(T(13, 3) + 0.06, 'error', { v: 1.0 });
  fx(T(13, 2) + 0.06, 'shake', { amp: 14, dur: 0.3 });
  fx(T(13, 3) + 0.06, 'shake', { amp: 18, dur: 0.35 });
  fx(T(13, 3) + 0.06, 'glitch', { amp: 0.35, dur: 0.15 });
  // step 5: signature (nervous hovering)
  const W5 = L.win[4];
  kf(T(13, 3.3), WB[3].x, WB[3].y);
  kf(T(14, 0.7), W5.x + 150, W5.y + 175, { hand: 1, tick: 1 });
  kf(T(14, 1.3), W5.x - 150, W5.y + 175, { hand: 1, tick: 1 });
  kf(T(14, 1.9), W5.x + 150, W5.y + 175, { hand: 1, tick: 1 });
  kf(T(14, 2.5), W5.x - 150, W5.y + 175, { hand: 1, tick: 1 });
  kf(T(14, 3.4), W5.x, W5.y + 110);
  s(T(14, 2), 'alarm', { v: 0.3 });
  // chaos: popups everywhere
  L.chaos.forEach((p, j) => {
    s(p.t, 'pop', { v: 0.9 });
    if (j % 2) s(p.t + 0.04, 'error', { v: 0.45 });
    fx(p.t, 'shake', { amp: 6 + j, dur: 0.2 });
    fx(p.t, 'glitch', { amp: 0.12 + j * 0.03, dur: 0.1 });
    kf(p.t + 0.17, p.x + 170, p.y - 48, { click: j % 2 === 0 ? 1 : 0, hand: 1 });
  });
  s(T(15), 'riser', { dur: BAR * 1.5, v: 0.6 });
  boom(T(16, 2), 0.8);
  s(T(16, 2), 'glitch', { dur: 0.3, v: 0.6 });
  va(T(16, 2), 1); va(T(16, 2) + 0.15, 0);
  hit(T(16, 3), 1.2);

  // ======================= 4. CONTRAST (bars 17-19) =======================
  whip(T(17));
  hit(T(17), 0.6);
  s(T(17, 1), 'chime', { v: 0.55 });
  hit(T(17, 2), 0.8);
  const BRICK = [T(17, 3), T(18, 0), T(18, 0.5), T(18, 1), T(18, 1.5)];
  BRICK.forEach((t, i) => {
    s(t, 'brick', { v: 0.8 + i * 0.05 });
    fx(t, 'shake', { amp: 14 + i * 2, dur: 0.3 });
  });
  boom(T(18, 2), 0.9);

  // ======================= 5. TRANSITION (bar 19) =======================
  whip(T(19), 0.4);
  s(T(19), 'type', { n: 28, dur: 1.1 });
  s(T(19), 'riser', { dur: BAR, v: 1 });
  s(T(19, 2), 'rev', { dur: BEAT * 2, v: 0.9 });
  va(T(19, 1), 0); va(T(19, 1.5), 1);
  kf(T(19, 1), 1460, 930);
  kf(T(19, 3.6), 960, 540, { hand: 1 });
  kf(T(20), 960, 540, { click: 1, hand: 1 });
  va(T(20) + 0.25, 1); va(T(20) + 0.6, 0);
  fx(T(19, 3), 'glitch', { amp: 0.35, dur: BEAT });

  // ======================= 6. REVEAL (bars 20-22) =======================
  boom(T(20), 1.35);
  s(T(20, 2), 'scramble', { dur: 0.75 });
  hit(T(21), 0.5);
  s(T(21), 'sparkle', { v: 0.7 });

  // ======================= 7. STARTALE APP (bars 22-24) =======================
  whip(T(22));
  hit(T(22), 0.7);
  s(T(22, 2), 'type', { n: 46, dur: 1.0 });
  const PH = L.phone;
  va(T(22, 2), 0); va(T(22, 2.5), 1);
  kf(T(22, 2), 1760, 1010);
  kf(T(22, 3.7), PH.x, PH.y + 300, { hand: 1 });
  kf(T(23), PH.x, PH.y + 300, { click: 1, hand: 1 });
  s(T(23) + 0.05, 'chime', { v: 0.8 });
  s(T(23) + 0.05, 'sparkle', { v: 0.8 });
  fx(T(23) + 0.05, 'flash', { a: 0.25, dur: 0.25 });
  fx(T(23) + 0.05, 'punch', { amp: 0.02, dur: 0.3 });
  for (let i = 0; i < 4; i++) s(T(23, 2) + i * BEAT / 2, 'pop', { v: 0.6, f: 1 + i * 0.2 });
  kf(T(23, 1), PH.x + 40, PH.y + 330);
  va(T(23, 3), 1); va(T(23, 3.5), 0);

  // ======================= 8. BIGGER PICTURE (bars 24-26) =======================
  whip(T(24));
  s(T(24), 'scramble', { dur: 0.55 });
  s(T(24, 1), 'type', { n: 40, dur: 0.85 });
  [T(24, 2), T(25, 0), T(25, 2)].forEach((t) => {
    s(t - 0.12, 'whoosh', { dur: 0.25, v: 0.4 });
    hit(t, 0.85);
  });

  // ======================= 9. ECOSYSTEM (bar 26) =======================
  whip(T(26));
  s(T(26, 0), 'pop'); s(T(26, 0.5), 'pop', { f: 1.2 }); s(T(26, 1), 'pop', { f: 1.4 });
  s(T(26, 1), 'riser', { dur: BEAT, v: 0.35 });
  hit(T(26, 2), 0.85);
  s(T(26, 2), 'chime', { v: 0.45 });

  // ======================= 10. PILLARS (bars 27-29) =======================
  whip(T(27));
  s(T(27), 'type', { n: 60, dur: 1.4 });
  s(T(27, 3), 'scramble', { dur: 0.45 });
  for (let i = 0; i < 4; i++) {
    hit(T(28, i), 0.9);
    s(T(28, i), 'brick', { v: 0.45 });
  }

  // ======================= 11. KEY INSIGHT (bars 29-33) =======================
  whip(T(29), 0.5);
  s(T(29), 'sub', { v: 0.8 });
  s(T(29), 'scramble', { dur: 0.6 });
  s(T(29, 0.5), 'glitch', { dur: 0.25, v: 0.3 });
  s(T(29, 2), 'type', { n: 52, dur: 1.2 });
  s(T(30), 'chime', { v: 0.7 });
  s(T(30), 'whoosh', { dur: 0.8, v: 0.4 });
  s(T(30, 1), 'type', { n: 52, dur: 1.2 });
  whip(T(31), 0.45);
  for (let i = 0; i < 4; i++) s(T(31, 0.5 + i * 0.5), 'pop', { v: 0.5, f: 0.8 + i * 0.15 });
  s(T(31), 'type', { n: 50, dur: 1.2 });
  s(T(32), 'scramble', { dur: 0.6 });
  hit(T(32, 1), 0.5);
  s(T(32), 'riser', { dur: BAR, v: 1 });
  s(T(32, 2), 'rev', { dur: BEAT * 2, v: 0.9 });
  fx(T(32, 3), 'glitch', { amp: 0.35, dur: BEAT });

  // ======================= 12. THESIS (bars 33-35) =======================
  boom(T(33), 1.2);
  hit(T(33, 2), 1.2);
  s(T(33, 2), 'sparkle', { v: 0.8 });
  hit(T(34), 0.6);
  hit(T(34, 1), 0.9);
  s(T(34, 2), 'slash', { v: 1 });
  fx(T(34, 2), 'glitch', { amp: 0.6, dur: 0.25 });
  fx(T(34, 2), 'shake', { amp: 18, dur: 0.35 });

  // ======================= 13. POLL (bars 35-39) =======================
  whip(T(35));
  hit(T(35), 0.8);
  hit(T(35, 1), 1);
  [T(35, 2), T(35, 3), T(36, 0), T(36, 1)].forEach((t, i) => {
    s(t, 'pop', { v: 0.8, f: 1 + i * 0.15 });
    hit(t, 0.35);
  });
  const PW = L.pollW;
  va(T(36), 0); va(T(36, 0.5), 1);
  kf(T(36), 960, 1120);
  const hov = [T(36, 2), T(37, 0), T(37, 2), T(38, 0)];
  hov.forEach((t, i) => {
    const p = L.poll[i];
    kf(t - BEAT * 0.25, p.x + PW * 0.28, p.y + 18, { hand: 1, tick: 1 });
    kf(t + BEAT * 1.2, p.x + PW * 0.28 + 14, p.y + 24, { hand: 1 });
  });
  kf(T(38, 2), 960, 975);
  hit(T(38, 2), 0.8);
  va(T(38, 3), 1); va(T(38, 3.6), 0);

  // ======================= 14. OUTRO (bars 39-42) =======================
  boom(T(39), 1.1);
  for (let i = 0; i < 8; i++) s(T(39) + 0.06 * i, 'lock', { n: i, v: 0.45 });
  s(T(39, 1), 'scramble', { dur: 0.75 });
  s(T(39, 3), 'type', { n: 46, dur: 1.1 });
  s(T(40, 2), 'sparkle', { v: 0.6 });

  // cursor clicks/ticks -> sounds
  POS.sort((a, b) => a.t - b.t);
  ALPHA.sort((a, b) => a.t - b.t);
  for (const k of POS) {
    if (k.click) s(k.t, 'click', { v: 1 });
    if (k.tick) s(k.t, 'tick', { v: 0.55 });
  }
  SFX.sort((a, b) => a.t - b.t);

  G.TL = { BPM, BEAT, BAR, T, DURATION, SFX, FX, CUR: { POS, ALPHA }, L };
})(typeof window !== 'undefined' ? window : globalThis);
