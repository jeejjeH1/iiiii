// Shared timeline for "The Dispute" (GenLayer cat, idea 4). Read by film.js (browser) and audio.js (node),
// so every impact, zap and stamp lands on the same frame as its sound. 120 BPM: 1 beat = 0.5s, 1 bar = 2s.
(function (G) {
  const BPM = 120;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);
  const DURATION = 44.5;

  // story beats (seconds)
  const K = {
    aliceIn: 0.5, bobIn: 1.0, zaps: [2.0, 2.5, 3.0, 3.25, 3.5],
    who: 4.0, beam: 5.0, land: 6.0, boot: 6.35,
    pawUp: 8.0, panel: 8.35, typeStart: 8.9, typeEnd: 11.2, web: [9.6, 10.0, 10.4], streamEnd: 12.6, propose: 12.9,
    spawn: [14.6, 15.0, 15.4, 15.8, 16.2], think: 16.6, answers: [17.5, 17.9, 18.3, 18.7, 19.1],
    fly: 22.0, merge: 23.5, unify: 24.0,
    stamp: 28.0, payout: 28.6, hop: 30.0, hop2: 31.0,
    beamOut: 34.0, hero: 35.0, logo: [36.0, 36.5, 37.0], title: 37.5, sub: 38.5, url: 39.5, fade: 42.5,
  };

  const SFX = [];
  const FX = [];
  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    fx(t, 'shake', { amp: 0.16 * v, dur: 0.7 });
    fx(t, 'flash', { a: 0.42 * v, dur: 0.4 });
    fx(t, 'glitch', { amp: 1, dur: 0.2 });
  };
  const hit = (t, v = 1) => { s(t, 'hit', { v }); fx(t, 'shake', { amp: 0.06 * v, dur: 0.3 }); };
  const whoosh = (t, v = 0.6, pan = 0.7) => s(t - 0.14, 'whoosh', { dur: 0.34, v, pan });

  // 0 intro: the bet
  s(0, 'sub', { v: 0.6 });
  s(0.05, 'glitch', { dur: 0.3, v: 0.5 }); fx(0.05, 'glitch', { amp: 0.8, dur: 0.3 });
  whoosh(K.aliceIn, 0.6, -0.8); s(K.aliceIn, 'pop', { f: 1.2 });
  whoosh(K.bobIn, 0.6, 0.8); s(K.bobIn, 'pop', { f: 0.9 });
  s(1.5, 'coin', { v: 0.7 });
  K.zaps.forEach((t, i) => { s(t, 'zap', { v: 0.7 + 0.1 * i }); fx(t, 'glitch', { amp: 0.35, dur: 0.1 }); fx(t, 'shake', { amp: 0.03, dur: 0.15 }); });
  s(2.0, 'glitch', { dur: 0.25, v: 0.6 });
  // 1 who decides
  s(K.who, 'hit', { v: 0.5 }); s(K.who + 0.02, 'whoosh', { dur: 0.5, v: 0.4, pan: 0 });
  s(K.beam - 1.0, 'riser', { dur: 2.0 });
  s(K.land - 0.45, 'fall', { dur: 0.45 });
  boom(K.land, 1.1);
  s(K.boot, 'boot');
  s(K.boot + 0.3, 'chime', { v: 0.6 });
  // 2 contract
  whoosh(K.pawUp + 0.1, 0.5, -0.3);
  s(K.panel, 'holo', { v: 0.8 });
  s(K.typeStart, 'type', { n: 44, dur: K.typeEnd - K.typeStart, v: 0.9 });
  K.web.forEach((t, i) => { s(t, 'holo', { v: 0.5 }); whoosh(t, 0.35, [-0.7, 0, 0.7][i]); });
  s(K.web[2] + 0.1, 'stream', { dur: K.streamEnd - K.web[2] - 0.1 });
  s(K.propose, 'lock', { n: 4, v: 1.1 });
  // 3 validators
  s(K.spawn[0] - 0.6, 'whoosh', { dur: 0.6, v: 0.5, pan: 0 });
  K.spawn.forEach((t, i) => { s(t, 'beam', { v: 0.8, pan: (i - 2) * 0.35 }); });
  s(K.think, 'scramble', { dur: 0.85, v: 0.8 });
  K.answers.forEach((t, i) => s(t, 'lock', { n: i, v: 0.95 }));
  // 4 equivalence
  s(K.fly - 0.1, 'whoosh', { dur: 0.6, v: 0.5, pan: 0 });
  s(K.merge - 1.0, 'riser', { dur: 1.0, v: 0.7 });
  s(K.merge, 'impact', { v: 0.6 }); fx(K.merge, 'flash', { a: 0.3, dur: 0.35 }); fx(K.merge, 'shake', { amp: 0.06, dur: 0.3 });
  s(K.unify, 'chime', { v: 0.9 }); s(K.unify, 'sparkle', { v: 0.8 });
  // 5 verdict
  s(K.stamp - 1.0, 'rev', { dur: 1.0 });
  boom(K.stamp, 1.0); s(K.stamp, 'stamp');
  s(K.payout, 'coin', { v: 1 }); whoosh(K.payout + 0.3, 0.4, -0.6);
  s(K.payout + 0.75, 'cash', { v: 0.9 });
  [K.hop, K.hop2].forEach((t) => { s(t, 'jump', { v: 0.8 }); hit(t + 0.5, 0.5); });
  s(K.hop + 0.5, 'sparkle', { v: 1 }); s(K.hop + 0.5, 'confetti', { v: 0.8 });
  // 6 outro
  K.spawn.forEach((t, i) => s(K.beamOut + i * 0.12, 'beam', { v: 0.45, pan: (i - 2) * 0.35, up: 1 }));
  whoosh(K.hero, 0.5, 0.3);
  K.logo.forEach((t, i) => { s(t, 'slam', { v: 0.9 + 0.1 * i }); fx(t, 'shake', { amp: 0.05 + 0.02 * i, dur: 0.25 }); });
  s(K.title, 'chime', { v: 0.8 });
  s(K.sub, 'tick', { v: 0.6 }); s(K.url, 'tick', { v: 0.6, f: 1.3 });
  s(K.fade - 0.5, 'sub', { v: 0.45 });

  // captions: [t0, t1, text, tag]
  const CAP = [
    [0.35, 1.9, 'Two people. One bet.'],
    [2.0, 3.85, 'Zero agreement.', null, 'glitch'],
    [4.05, 5.8, 'Who decides what’s true?'],
    [6.55, 7.95, 'Meet the Intelligent Contract.', 'GENLAYER'],
    [8.6, 10.95, 'It reads the web…', 'INTELLIGENT CONTRACTS'],
    [11.0, 13.9, '…and reasons with AI.', 'INTELLIGENT CONTRACTS'],
    [14.6, 17.9, 'Validators running different AI models re-check it.', 'VALIDATORS'],
    [18.0, 21.9, 'This is Optimistic Democracy.', 'CONSENSUS'],
    [22.2, 24.9, 'Different words. Same meaning.', 'CONSENSUS'],
    [25.0, 27.9, 'The Equivalence Principle.', 'CONSENSUS'],
    [28.4, 31.4, 'Dispute settled — on-chain.', 'VERDICT'],
    [31.5, 33.9, 'No middleman needed.', 'VERDICT'],
  ];

  G.TL = { BPM, BEAT, BAR, T, DURATION, K, SFX, FX, CAP };
})(typeof window !== 'undefined' ? window : globalThis);
