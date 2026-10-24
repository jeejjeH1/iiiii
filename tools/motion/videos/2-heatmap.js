const ENTER = [{ t: 0, y: 2500, ry: 28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
window.CFG = {
  dur: 16, accent: '#FFB020', accent2: '#FF4D3D', blobs: ['#FF4D3D', '#FFB020', '#2BD576'],
  title: 'نقشه *حرارتی بازیکنان',
  captions: [
    { t0: 1.3, t1: 4.9, text: 'ببین هر بازیکن<br><b>کجای زمین</b> بیشتر بوده' },
    { t0: 4.9, t1: 9.4, text: 'روی بازیکن بزن؛<br>نقشه‌ی حرارتی‌اش روی <b>کل زمین</b>' },
    { t0: 9.4, t1: 13.2, text: 'سهم <b>هر بخش و هر سمت</b> زمین<br>و مقایسه با هم‌تیمی‌ها' },
  ],
  phone: [...ENTER, { t: 12.9, y: 1215, s: 1 }, { t: 13.6, y: 3300, s: .9 }],
  screens: [
    { t: 0, src: '42-big-lineup' },
    { t: 4.5, src: '44-heatmap', trans: 'up', dur: .6 },
    { t: 9.4, src: '46-heatmap-full', trans: 'scroll', from: 900, dur: .8 },
  ],
  lenses: [
    { t0: 5.5, t1: 9.2, src: '44-heatmap', crop: [10, 930, 780, 620], x: 50, y: 880, w: 980, label: '🔥 نقشه حرارتی' },
    { t0: 10.2, t1: 13.0, src: '46-heatmap-full', crop: [10, 30, 780, 600], x: 70, y: 880, w: 940, label: 'سهم هر بخش زمین' },
  ],
  rings: [{ t0: 2.6, t1: 4.3, r: [210, 1320, 140, 160] }],
  cursor: [{ t: 2.0, x: 640, y: 1600 }, { t: 3.4, x: 280, y: 1395 }, { t: 4.3, x: 280, y: 1395 }, { t: 5.2, x: 600, y: 1600 }],
  taps: [3.75],
};
