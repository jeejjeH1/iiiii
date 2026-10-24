const ENTER = [{ t: 0, y: 2500, ry: -28, rx: 12, s: .9 }, { t: 1.3, y: 1215, ry: 0, rx: 0, s: 1, ease: k => 1 - Math.pow(1 - k, 4) }];
const BUZZ = [3.9, 5.9, 7.7, 9.1];
const NOTES = [
  [3.9, '⏱', 'سوت شروع بازی', 'تاتنهام - کاونتری · لیگ برتر'],
  [5.9, '⚽', 'گل! تاتنهام ۱ - ۰ کاونتری', 'جیمز مادیسون · دقیقه ۱۴'],
  [7.7, '⚽', 'گل! تاتنهام ۲ - ۱ کاونتری', 'عمر مارموش · دقیقه ۵۸'],
  [9.1, '🏁', 'پایان بازی', 'تاتنهام ۳ - ۱ کاونتری'],
];
const LOCK = `<div style="position:absolute;inset:0;background:radial-gradient(ellipse at 30% 20%,#3b1f6b,transparent 60%),radial-gradient(ellipse at 80% 80%,#0f4c5c,transparent 60%),linear-gradient(160deg,#141a2e,#07090f);font-family:V">
  <div style="position:absolute;top:150px;width:100%;text-align:center;font-weight:500;font-size:30px;color:rgba(255,255,255,.8)">شنبه ۲ آبان</div>
  <div style="position:absolute;top:185px;width:100%;text-align:center;font-weight:900;font-size:170px;line-height:1.1;color:#fff">۲۱:۴۷</div>
  <div style="position:absolute;top:420px;width:100%;text-align:center;font-size:26px;color:rgba(255,255,255,.55)">🔒 برنامه بسته است</div>
  <div id="nlist" style="position:absolute;left:24px;right:24px;top:480px"></div>
  <div style="position:absolute;bottom:40px;left:50%;width:200px;margin-left:-100px;height:8px;border-radius:4px;background:rgba(255,255,255,.6)"></div></div>`;
window.CFG = { dings: BUZZ,
  dur: 17, accent: '#FF3358', accent2: '#FFB020', blobs: ['#FF3358', '#7B2CBF', '#FFB020'],
  title: 'اعلان گل، *همیشه',
  captions: [
    { t0: 1.3, t1: 5.6, text: 'برنامه بسته است و گوشی قفل…' },
    { t0: 5.6, t1: 10.0, text: 'ولی گل که بخورد<br><b>همان لحظه</b> خبردار می‌شوی' },
    { t0: 10.0, t1: 14.2, text: 'گل، ترکیب، شروع و پایان بازی<br>هر کدام با <b>صدای خودش</b>' },
  ],
  phone: [...ENTER, ...BUZZ.flatMap(b => [{ t: b, y: 1215, rz: 0 }, { t: b + .06, y: 1215, rz: 2.2 }, { t: b + .14, y: 1215, rz: -2.2 }, { t: b + .22, y: 1215, rz: 1.5 }, { t: b + .3, y: 1215, rz: 0 }]),
    { t: 13.9, y: 1215, s: 1, rz: 0 }, { t: 14.6, y: 3300, s: .9, rz: 0 }],
  screens: [
    { t: 0, html: LOCK, tick(t, e) {
      const list = e.querySelector('#nlist');
      if (!list._made) {
        list._made = NOTES.map(([at, ic, h, s]) => { const d = document.createElement('div');
          d.style.cssText = 'position:absolute;left:0;right:0;border-radius:30px;padding:22px 24px;background:rgba(30,34,44,.78);backdrop-filter:blur(20px);border:1px solid rgba(255,255,255,.12);display:flex;gap:18px;align-items:center;box-shadow:0 20px 40px rgba(0,0,0,.35)';
          d.innerHTML = `<div style="width:66px;height:66px;flex:none;border-radius:18px;background:#fff url(assets/logo.png) center/86% no-repeat"></div><div style="flex:1;min-width:0"><div style="display:flex;justify-content:space-between;font-size:20px;color:rgba(255,255,255,.55)"><span>گل‌لایو</span><span>اکنون</span></div><div style="font-weight:900;font-size:29px;color:#fff;margin-top:2px">${ic} ${h}</div><div style="font-size:24px;color:rgba(255,255,255,.75)">${s}</div></div>`;
          list.appendChild(d); return [at, d]; });
      }
      // the newest on top, the older ones pushed down
      list._made.forEach(([at, d], i) => {
        const k = easeOut(seg(t, at, at + .45));
        const newer = list._made.filter(([a2]) => a2 > at && t >= a2).length;
        const push = list._made.filter(([a2]) => a2 > at).reduce((s, [a2]) => s + easeOut(seg(t, a2, a2 + .45)) * 150, 0);
        set(d, { y: lerp(-120, 0, k) + push, s: lerp(.9, 1, k) * (1 - newer * .03), o: k * (1 - newer * .12) });
        d.style.zIndex = 10 + i;
      });
    } },
    { t: 10.0, src: '11-settings', trans: 'fade' },
  ],
  lenses: [
    { t0: 10.8, t1: 13.9, src: '11-settings', crop: [20, 520, 760, 960], x: 90, y: 820, w: 900, label: '🔔 همه‌ی اعلان‌ها' },
  ],
  tick(t) {
    // a ring of light at each alert
    if (!this._pulse) { this._pulse = el('div', 'abs', $('#extraBack')); this._pulse.style.cssText += 'width:900px;height:900px;border-radius:50%;border:6px solid var(--accent)'; }
    let o = 0, s = 1;
    BUZZ.forEach(b => { const k = seg(t, b, b + 1.1); if (k > 0 && k < 1) { o = (1 - k) * .7; s = lerp(.4, 1.6, easeOut(k)); } });
    set(this._pulse, { x: 90, y: 760, s, o });
  },
};
