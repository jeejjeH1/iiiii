// standard entrance: the phone rises tilted and settles
const ENTER = [{ t: 0, y: 2500, ry: -28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
window.CFG = {
  dur: 17, accent: '#C6FF3D', accent2: '#2BD576',
  tag: 'تازه در گل‌لایو',
  title: 'نمره‌دهی *گل‌لایو',
  captions: [
    { t0: 1.3, t1: 5.2, text: 'بعد از هر بازی، به هر بازیکن<br><b>نمره‌ی اختصاصی گل‌لایو</b> می‌دهیم' },
    { t0: 5.2, t1: 9.4, text: 'بزن روی بازیکن و ببین<br>نمره‌اش <b>چند</b> شده' },
    { t0: 9.4, t1: 14.2, text: 'و ببین <b>چرا</b>؛ حمله، پاس، دفاع، نبرد و …<br>هر کدام جدا حساب می‌شود' },
  ],
  phone: [...ENTER, { t: 13.9, y: 1215, s: 1 }, { t: 14.6, y: 3300, s: .9, o: 1 }],
  screens: [
    { t: 0, src: '40-big-summary' },
    { t: 2.4, src: '40-big-down1', trans: 'scroll', from: 700, dur: .8 },
    { t: 6.0, src: '44-heatmap', trans: 'up', dur: .6 },
    { t: 8.6, src: '48-rating-why', trans: 'fade' },
  ],
  lenses: [
    { t0: 3.3, t1: 5.4, src: '40-big-down1', crop: [20, 1105, 760, 470], x: 80, y: 950, w: 940, label: '⭐ برترین‌های بازی' },
    { t0: 9.6, t1: 13.8, src: '48-rating-why', crop: [20, 470, 760, 720], x: 90, y: 830, w: 900, label: 'ریز نمره' },
  ],
  rings: [{ t0: 6.7, t1: 8.3, r: [18, 40, 150, 165] }],
  cursor: [{ t: 4.9, x: 650, y: 1500 }, { t: 5.6, x: 520, y: 1250 }, { t: 6.4, x: 520, y: 1250 }, { t: 7.2, x: 120, y: 160 }, { t: 7.8, x: 420, y: 370 }, { t: 8.3, x: 420, y: 370 }, { t: 9.3, x: 600, y: 900 }],
  taps: [5.65, 8.25],
};
