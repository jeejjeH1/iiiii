const ENTER = [{ t: 0, y: 2500, ry: 28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
const HAS_MOM = true; window.MOM_CROP = [10, 430, 780, 750];
window.CFG = {
  dur: 19, accent: '#2BD576', accent2: '#C6FF3D', blobs: ['#2BD576', '#0EA5E9', '#C6FF3D'],
  title: 'صفحه‌ی بازی، *از نو',
  captions: [
    { t0: 1.3, t1: 5.0, text: 'ترکیب تیم‌ها روی زمین<br>با <b>نمره و اتفاقات</b> هر بازیکن' },
    { t0: 5.0, t1: 9.2, text: 'رویدادهای بازی<br><b>دو طرفه و خوانا</b>' },
    { t0: 9.2, t1: 16.2, text: (HAS_MOM ? '<b>مومنتوم</b> و ' : '') + '<b>نقشه‌ی حمله</b><br>ببین کدام تیم بازی را در دست داشت' },
  ],
  phone: [...ENTER, { t: 15.9, y: 1215, s: 1 }, { t: 16.6, y: 3300, s: .9 }],
  screens: [
    { t: 0, src: '42-big-lineup' },
    { t: 5.0, src: '40-big-down0', trans: 'right', dur: .6 },
    ...(HAS_MOM ? [{ t: 9.2, src: '40m-step5', trans: 'scroll', from: 700, dur: .7 }, { t: 12.6, src: '40-big-down1', trans: 'scroll', from: 700, dur: .7 }]
      : [{ t: 9.2, src: '40-big-down1', trans: 'scroll', from: 700, dur: .7 }]),
  ],
  lenses: [
    { t0: 1.9, t1: 4.8, src: '42-big-lineup', crop: [10, 960, 780, 640], x: 70, y: 880, w: 940, label: '📋 ترکیب' },
    { t0: 5.8, t1: 8.9, src: '40-big-down0', crop: [10, 260, 780, 760], x: 80, y: 820, w: 920, label: '⏱ رویدادها' },
    ...(HAS_MOM ? [{ t0: 9.9, t1: 12.4, src: '40m-step5', crop: window.MOM_CROP, x: 60, y: 950, w: 960, label: '📈 مومنتوم' },
      { t0: 13.3, t1: 15.8, src: '40-big-down1', crop: [10, 350, 780, 700], x: 80, y: 860, w: 920, label: '⚔️ نقشه حمله' }]
      : [{ t0: 10.0, t1: 14.8, src: '40-big-down1', crop: [10, 350, 780, 700], x: 80, y: 860, w: 920, label: '⚔️ نقشه حمله' }]),
  ],
};
