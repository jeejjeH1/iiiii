const ENTER = [{ t: 0, y: 2500, ry: 28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
const CARDS = ['28a-share-teams-story', '28b-share-night-portrait', '28c-share-gold-square', '28d-share-light-story', '28e-share-pitch-portrait', '28f-share-neon-square'];
const SWAPS = [5.3, 6.5, 7.7, 8.9, 10.1];          // a tap on the next design, the card turns to it
const SW_X = [547, 396, 271, 91, 91];
window.CFG = { fan: 11.4,
  dur: 18.5, accent: '#C77DFF', accent2: '#FF5CA8', blobs: ['#7B2CBF', '#FF5CA8', '#3A86FF'],
  title: 'استودیوی *اشتراک‌گذاری',
  captions: [
    { t0: 1.3, t1: 5.0, text: 'عکس نتیجه‌ی بازی را<br><b>هر طور دوست داری</b> بساز' },
    { t0: 5.0, t1: 11.0, text: '<b>۶ طرح</b>، ۳ اندازه،<br>نوشته و استیکر دلخواه' },
    { t0: 11.0, t1: 15.7, text: 'برای <b>استوری و پست</b><br>با یک لمس بفرست یا ذخیره کن' },
  ],
  phone: [...ENTER, { t: 3.6, x: 540, y: 1215, s: 1 }, { t: 4.4, x: 360, y: 1260, s: .9 }, { t: 10.9, x: 360, y: 1260, s: .9 }, { t: 11.6, x: 540, y: 3000, s: .8 }],
  screens: [{ t: 0, src: '28z-share-studio' }],
  rings: [],
  cursor: [{ t: 4.6, x: 700, y: 1400 }, ...SWAPS.flatMap((s, i) => [{ t: s - .5, x: SW_X[i], y: 1190 }, { t: s - .05, x: SW_X[i], y: 1190 }]), { t: 10.6, x: 400, y: 1500 }],
  taps: SWAPS.map(s => s - .25),
  setup() {
    // one big card beside the phone, then all six fanned out
    const st = $('#extraFront');
    this._big = CARDS.map(c => { const d = el('div', 'abs', st); d.style.cssText += 'border-radius:26px;overflow:hidden;box-shadow:0 40px 90px rgba(0,0,0,.7),0 0 0 3px rgba(255,255,255,.15)'; const i = el('img', '', d); i.src = 'shots/' + c + '.png'; i.style.width = '100%'; i.style.display = 'block'; return d; });
    this._fan = CARDS.map(c => { const d = el('div', 'abs', st); d.style.cssText += 'border-radius:20px;overflow:hidden;box-shadow:0 30px 70px rgba(0,0,0,.7),0 0 0 2px rgba(255,255,255,.18)'; const i = el('img', '', d); i.src = 'shots/' + c + '.png'; i.style.width = '100%'; i.style.display = 'block'; return d; });
  },
  tick(t) {
    const W = 430;
    this._big.forEach((d, i) => {
      const start = i === 0 ? 4.4 : SWAPS[i - 1], end = i < 5 ? SWAPS[i] : 11.0;
      const kin = easeOut(seg(t, start, start + .5)), kout = seg(t, end, end + .35);
      d.style.width = W + 'px';
      const vis = t >= start && (i === 5 ? t < 11.4 : t < end + .35);
      const fade = i === 5 ? easeIn(seg(t, 10.9, 11.4)) : 0;
      set(d, { x: 620 - (1 - kin) * 120, y: 640 + (1 - kin) * 60, r: lerp(10, 4, kin) - kout * 8, s: lerp(.85, 1, back(kin)) * (1 - kout * .15), o: vis ? kin * (1 - kout) * (1 - fade) : 0 });
      d.style.zIndex = 10 + i;
    });
    // the fan
    this._fan.forEach((d, i) => {
      const k = easeOut(seg(t, 11.4 + i * .12, 12.3 + i * .12)), out = easeIn(seg(t, 15.4, 15.9));
      const a = (i - 2.5) * 11, w = 300;
      d.style.width = w + 'px';
      const cx = 540 + (i - 2.5) * 140, cy = 1180 + Math.abs(i - 2.5) * 40;
      set(d, { x: cx - w / 2, y: lerp(2100, cy - 260, k) - out * 200, r: a * k, o: k * (1 - out) });
      d.style.zIndex = 30 + i;
    });
  },
};
