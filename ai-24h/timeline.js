// Shared timeline: motion.js (visuals) and audio.js (sound) both read from here,
// so every clock tick, glitch and pop lands on the same frame as its sound.
(function (G) {
  const BPM = 120;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const DURATION = 26.5;

  // scene windows (seconds); every cut sits on a beat
  const SCENES = [
    { id: 'hook', t0: 0, t1: 3 },
    { id: 'mail', t0: 3, t1: 6.5, cap: '۵۰ تا ایمیل ← ۳ خط خلاصه', chip: 'Claude · ChatGPT' },
    { id: 'slides', t0: 6.5, t1: 10, cap: 'ارائه در ۲ دقیقه', chip: 'Gamma' },
    { id: 'image', t0: 10, t1: 13.5, cap: 'از اسکچ تا تصویر', chip: 'Midjourney' },
    { id: 'voice', t0: 13.5, t1: 17, cap: 'صدات ← ویدیو', chip: 'ElevenLabs · Runway' },
    { id: 'code', t0: 17, t1: 20.5, cap: 'اپ بدون کدنویسی', chip: 'Claude Code · Cursor' },
    { id: 'end', t0: 20.5, t1: 24 },
    { id: 'save', t0: 24, t1: DURATION },
  ];

  // clock keyframes: at t the clock rolls to `min` (minutes since midnight)
  const CLOCK = [
    { t: 0, min: 7 * 60 + 59 },
    { t: 3, min: 8 * 60 },
    { t: 6.5, min: 10 * 60 },
    { t: 10, min: 13 * 60 },
    { t: 13.5, min: 16 * 60 },
    { t: 17, min: 20 * 60 },
    { t: 20.5, min: 23 * 60 + 59 },
    { t: 22.5, min: 24 * 60 },
  ];
  const MIDNIGHT = 22.5;

  const SFX = []; // audio cues
  const FX = [];  // post-processing cues
  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));

  // scene cuts: whoosh into a glitch + clock tick
  for (const c of CLOCK.slice(1)) {
    s(c.t - 0.18, 'whoosh', { dur: 0.3, v: 0.5 });
    s(c.t, 'tick', { v: 1 });
    s(c.t, 'glitch', { dur: 0.16, v: 0.45 });
    fx(c.t, 'glitch', { amp: 1, dur: 0.2 });
    fx(c.t, 'punch', { amp: 0.025, dur: 0.3 });
  }

  // hook: two text slams
  s(0.15, 'hit', { v: 0.8 }); fx(0.15, 'shake', { amp: 10, dur: 0.25 });
  s(0.65, 'hit', { v: 1 }); fx(0.65, 'shake', { amp: 14, dur: 0.3 });
  s(1.5, 'pop', { v: 0.5, f: 900 });

  // mail: envelopes landing, suck-in, summary pop + three checks
  for (let i = 0; i < 16; i++) s(3 + 0.25 + i * 0.07, 'plop', { v: 0.25, f: 500 + (i % 5) * 70 });
  s(3 + 1.45, 'suck', { dur: 0.5, v: 0.5 });
  s(3 + 2.0, 'pop', { v: 0.9, f: 700 });
  for (let i = 0; i < 3; i++) s(3 + 2.3 + i * 0.25, 'ding', { v: 0.4, f: 1200 + i * 200 });

  // slides: typing, send, elements popping, deck fan-out
  s(6.5 + 0.05, 'type', { dur: 0.75, v: 0.3 });
  s(6.5 + 0.9, 'click', { v: 0.6 });
  [1.0, 1.15, 1.3, 1.45, 1.6, 1.8].forEach((u, i) => s(6.5 + u, 'pop', { v: 0.45, f: 600 + i * 90 }));
  s(6.5 + 2.4, 'swish', { dur: 0.35, v: 0.5 });

  // image: pencil sketch, AI scan, sparkle
  s(10 + 0.05, 'pencil', { dur: 1.15, v: 0.35 });
  s(10 + 1.3, 'scan', { dur: 0.9, v: 0.45 });
  s(10 + 2.25, 'sparkle', { v: 0.6 });

  // voice: speech-like blips, morph, video slide-in
  for (let i = 0; i < 9; i++) s(13.5 + 0.1 + i * 0.12, 'blip', { v: 0.18, f: 300 + ((i * 7) % 5) * 60 });
  s(13.5 + 1.25, 'suck', { dur: 0.4, v: 0.35 });
  s(13.5 + 1.9, 'swish', { dur: 0.35, v: 0.5 });
  s(13.5 + 2.2, 'click', { v: 0.6 });

  // code: prompt typing, code typing, phone slide, todo checks
  s(17 + 0.05, 'type', { dur: 0.55, v: 0.3 });
  s(17 + 0.65, 'type', { dur: 1.1, v: 0.38 });
  s(17 + 1.85, 'swish', { dur: 0.35, v: 0.5 });
  for (let i = 0; i < 4; i++) s(17 + 2.35 + i * 0.22, 'ding', { v: 0.32, f: 1000 + i * 150 });

  // end: riser into midnight impact, then the question
  s(20.5, 'riser', { dur: 2.0, v: 0.9 });
  s(MIDNIGHT, 'impact', { v: 1 });
  fx(MIDNIGHT, 'flash', { a: 0.85, dur: 0.5 });
  fx(MIDNIGHT, 'shake', { amp: 26, dur: 0.6 });
  fx(MIDNIGHT, 'glitch', { amp: 1.2, dur: 0.25 });
  fx(MIDNIGHT, 'punch', { amp: 0.06, dur: 0.5 });
  s(MIDNIGHT + 0.5, 'pop', { v: 0.6, f: 800 });

  // save CTA
  s(24, 'whoosh', { dur: 0.3, v: 0.4 });
  s(24.1, 'hit', { v: 0.7 }); fx(24.1, 'glitch', { amp: 0.6, dur: 0.15 });
  s(24.55, 'pop', { v: 0.9, f: 650 });
  s(24.7, 'sparkle', { v: 0.6 });

  G.TL = { BPM, BEAT, BAR, DURATION, SCENES, CLOCK, MIDNIGHT, SFX, FX };
})(typeof window !== 'undefined' ? window : globalThis);
