const ENTER = [{ t: 0, y: 2500, ry: -28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
window.CFG = {
  dur: 17, accent: '#F5C542', accent2: '#FF9F1C', blobs: ['#F5C542', '#B8860B', '#FF9F1C'],
  title: 'رتبه‌بندی *جهانی گل‌لایو',
  captions: [
    { t0: 1.3, t1: 4.9, text: 'برترین بازیکنان دنیا<br>بر اساس <b>نمره‌های گل‌لایو</b>' },
    { t0: 4.9, t1: 9.6, text: 'پیش‌بینی گل‌لایو برای<br><b>توپ طلای امسال</b>' },
    { t0: 9.6, t1: 14.2, text: 'همه‌ی برندگان <b>توپ طلا</b> و <b>کفش طلا</b><br>از ۱۹۵۶ تا امروز' },
  ],
  phone: [...ENTER, { t: 13.9, y: 1215, s: 1 }, { t: 14.6, y: 3300, s: .9 }],
  screens: [
    { t: 0, src: '50-rank-best' },
    { t: 4.5, src: '51-rank-ballon', trans: 'fade' },
    { t: 9.6, src: '51b-rank-ballon-down', trans: 'scroll', from: 900, dur: .8 },
  ],
  lenses: [
    { t0: 2.2, t1: 4.2, src: '50-rank-best', crop: [20, 560, 760, 520], x: 70, y: 1000, w: 940, label: '⭐ برترین‌های جهان' },
    { t0: 5.6, t1: 9.4, src: '51-rank-ballon', crop: [25, 430, 750, 1060], x: 150, y: 770, w: 780, label: '🏆 پیش‌بینی گل‌لایو' },
    { t0: 10.4, t1: 13.9, src: '51b-rank-ballon-down', crop: [20, 700, 760, 840], x: 90, y: 860, w: 900, label: 'تاریخ توپ طلا' },
  ],
  cursor: [{ t: 3.6, x: 640, y: 1500 }, { t: 4.2, x: 396, y: 270 }, { t: 4.8, x: 396, y: 270 }, { t: 5.4, x: 600, y: 900 }],
  taps: [4.25],
};
