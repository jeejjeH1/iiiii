// Shared timeline: motion.js (visuals) and audio.js (sound) both read from here,
// so every slam, zap and pop lands on the same frame as its sound.
(function (G) {
  const BPM = 128;
  const BEAT = 60 / BPM;
  const BAR = BEAT * 4;
  const T = (bar, beat = 0) => +((bar * 4 + beat) * BEAT).toFixed(5);

  // countdown order: #5 first, #1 last
  const CONNECTORS = [
    {
      logo: 'figma', name: 'Figma', benefit: 'طرحت رو مستقیم به کد تبدیل کنه',
      ask: 'این صفحه‌ی فیگما رو به کد تبدیل کن', reply: 'کد صفحه آماده‌ست', kind: 'code',
    },
    {
      logo: 'notion', name: 'Notion', benefit: 'یادداشت‌هات رو مرتب و صفحه‌بندی کنه',
      ask: 'یادداشت‌های جلسه رو تو نوشن مرتب کن', reply: 'صفحه‌ی «جلسه‌ی دوشنبه» ساخته شد', kind: 'page',
    },
    {
      logo: 'googledrive', name: 'Google Drive', benefit: 'بین همه‌ی فایل‌هات جستجو کنه',
      ask: 'گزارش فروش ماه پیش رو پیدا کن و خلاصه کن', reply: 'فایلش رو پیدا کردم:', kind: 'file',
    },
    {
      logo: 'googlecalendar', name: 'Google Calendar', benefit: 'برنامه‌ات رو بدونه و وقت خالی پیدا کنه',
      ask: 'فردا کی وقت خالی دارم؟', reply: 'این سه تا بازه خالیه:', kind: 'slots',
    },
    {
      logo: 'gmail', name: 'Gmail', benefit: 'ایمیل‌هات رو بخونه و خلاصه کنه',
      ask: 'ایمیل‌های مهم این هفته رو خلاصه کن', reply: '۳ ایمیل مهم این هفته:', kind: 'list',
    },
  ];

  const SCENES = [{ id: 'hook', t0: 0, t1: T(2) }];
  CONNECTORS.forEach((c, i) => SCENES.push({ id: 'conn', k: i, t0: T(2 + 2 * i), t1: T(4 + 2 * i) }));
  SCENES.push({ id: 'how', t0: T(12), t1: T(13) });
  SCENES.push({ id: 'ask', t0: T(13), t1: T(14) });
  SCENES.push({ id: 'save', t0: T(14), t1: T(15) + 0.5 });
  const DURATION = T(15) + 0.5;

  // inside a connector scene (in beats from its start)
  const CB = { panelOut: 1, tileIn: 1, plug: 2, title: 2, benefit: 2.5, ask: 4, think: 5.5, reply: 6 };

  const SFX = [];
  const FX = [];
  const s = (t, type, o = {}) => SFX.push(Object.assign({ t, type }, o));
  const fx = (t, type, o = {}) => FX.push(Object.assign({ t, type }, o));
  const hit = (t, v = 1) => { s(t, 'hit', { v }); fx(t, 'shake', { amp: 14 * v, dur: 0.28 }); fx(t, 'punch', { amp: 0.03 * v, dur: 0.3 }); };
  const boom = (t, v = 1) => {
    s(t, 'impact', { v });
    fx(t, 'shake', { amp: 26 * v, dur: 0.5 });
    fx(t, 'flash', { a: 0.3 * v, dur: 0.22 });
    fx(t, 'punch', { amp: 0.06 * v, dur: 0.45 });
  };

  // hook: logo slam, three word slams, logo swarm, riser into the first drop
  boom(0, 0.9);
  hit(T(0, 1), 1); hit(T(0, 2), 0.7); hit(T(0, 3), 0.8);
  for (let i = 0; i < 5; i++) s(T(1, 0) + i * BEAT * 0.4, 'pop', { v: 0.5, f: 520 + i * 110 });
  s(T(1, 0), 'riser', { dur: BAR, v: 0.9 });
  s(T(1, 3), 'suck', { dur: BEAT, v: 0.6 });

  CONNECTORS.forEach((c, i) => {
    const t0 = T(2 + 2 * i);
    const b = (x) => t0 + x * BEAT;
    boom(t0, i === 4 ? 1.15 : 1);                // number slam on the drop
    s(b(CB.panelOut) - 0.1, 'whoosh', { dur: 0.25, v: 0.6 });
    s(b(CB.tileIn) + 0.15, 'swish', { dur: 0.3, v: 0.4 });
    s(b(CB.plug), 'zap', { v: 1 });                // plug clicks into Claude
    fx(b(CB.plug), 'shake', { amp: 10, dur: 0.25 });
    fx(b(CB.plug), 'punch', { amp: 0.025, dur: 0.25 });
    s(b(CB.plug) + 0.12, 'ding', { v: 0.5, f: 1320 });
    s(b(CB.ask), 'pop', { v: 0.6, f: 700 });
    s(b(CB.ask) + 0.08, 'type', { dur: BEAT * 1.3, v: 0.35 });
    s(b(CB.think), 'blip', { v: 0.3, f: 900 });
    s(b(CB.reply), 'pop', { v: 0.7, f: 880 });
    s(b(CB.reply) + 0.35, 'sparkle', { v: 0.5 });
    if (i < 4) { s(b(7) - 0.05, 'riser', { dur: BEAT + 0.05, v: 0.5 }); }
  });

  // how-to: title slam + three clicks
  hit(T(12), 0.9);
  for (let i = 0; i < 3; i++) { s(T(12, 1 + i), 'click', { v: 0.8 }); s(T(12, 1 + i) + 0.03, 'pop', { v: 0.45, f: 600 + i * 150 }); }
  s(T(12, 3), 'riser', { dur: BEAT, v: 0.6 });

  // ask: question slam, logos bounce on each beat
  boom(T(13), 0.9);
  for (let i = 0; i < 5; i++) s(T(13, 1) + i * BEAT * 0.5, 'pop', { v: 0.45, f: 500 + i * 120 });
  s(T(13, 3) + BEAT * 0.5, 'whoosh', { dur: 0.3, v: 0.6 });

  // save
  boom(T(14), 1);
  s(T(14, 1), 'pop', { v: 0.9, f: 650 });
  s(T(14, 1) + 0.15, 'sparkle', { v: 0.7 });
  hit(T(14, 2), 0.6);

  G.TL = { BPM, BEAT, BAR, T, DURATION, SCENES, CONNECTORS, CB, SFX, FX };
})(typeof window !== 'undefined' ? window : globalThis);
