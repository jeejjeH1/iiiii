const ENTER = [{ t: 0, y: 2500, ry: -28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
window.CFG = {
  dur: 17, accent: '#38BDF8', accent2: '#C6FF3D', blobs: ['#0EA5E9', '#22C55E', '#6366F1'],
  title: 'نقشه‌ی *شوت‌ها',
  captions: [
    { t0: 1.3, t1: 5.0, text: 'همه‌ی شوت‌های هر تیم<br>روی <b>زمین واقعی</b>' },
    { t0: 5.0, t1: 9.6, text: 'هرچه دایره <b>بزرگ‌تر</b><br>موقعیت گل خطرناک‌تر' },
    { t0: 9.6, t1: 14.2, text: 'گل، مهار، بلاک یا به بیرون<br>با <b>دقیقه و کیفیت</b> هر شوت' },
  ],
  phone: [...ENTER, { t: 13.9, y: 1215, s: 1 }, { t: 14.6, y: 3300, s: .9 }],
  screens: [
    { t: 0, src: '41-big-stats' },
    { t: 2.9, src: '41b-big-shots', trans: 'fade' },
    { t: 4.2, src: '41c-big-shots-0', trans: 'scroll', from: 600, dur: .7 },
    { t: 11.4, src: '41c-big-shots-2', trans: 'scroll', from: 700, dur: .7 },
  ],
  lenses: [
    { t0: 5.0, t1: 8.6, src: '41c-big-shots-0', crop: [20, 830, 760, 720], x: 60, y: 820, w: 960, label: '🎯 نقشه شوت' },
    { t0: 8.9, t1: 11.2, src: '41c-big-shots-0', crop: [20, 500, 760, 290], x: 70, y: 1050, w: 940, label: 'مقایسه دو تیم' },
    { t0: 12.0, t1: 14.0, src: '41c-big-shots-2', crop: [20, 540, 760, 700], x: 80, y: 840, w: 920, label: 'همه‌ی شوت‌ها' },
  ],
  rings: [{ t0: 2.95, t1: 4.0, r: [280, 820, 240, 92] }],
  cursor: [{ t: 1.7, x: 640, y: 1400 }, { t: 2.5, x: 400, y: 866 }, { t: 3.2, x: 400, y: 866 }, { t: 3.9, x: 600, y: 1300 }],
  taps: [2.6],
};
