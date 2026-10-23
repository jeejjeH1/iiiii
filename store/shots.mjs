// Bazaar screenshots: each app screen in a phone frame under a one-line headline. Usage: PW=<playwright dir> node shots.mjs
import { createRequire } from 'module';
import { readFileSync, mkdirSync } from 'fs';
const { chromium } = createRequire(import.meta.url)(process.env.PW);
const A = (f) => 'data:image/png;base64,' + readFileSync(new URL('../promo/assets/' + f, import.meta.url)).toString('base64');
const font = (f) => 'data:font/ttf;base64,' + readFileSync(new URL('../promo/assets/' + f, import.meta.url)).toString('base64');
const SHOTS = [
  ['01-home.png', 'نتیجه زنده همه بازی‌ها', 'لحظه‌به‌لحظه، بدون نیاز به رفرش', '#2bd46b'],
  ['02b-match-pitch.png', 'زمین زنده بازی', 'ترکیب و اتفاق‌ها روی زمین', '#22c3ff'],
  ['02-match-summary.png', 'گزارش کامل هر بازی', 'گل‌ها، کارت‌ها و تعویض‌ها', '#ffb020'],
  ['23-match-radar.png', 'رادار حریف', 'نقاط قوت و ضعف قبل از بازی', '#ff4d6d'],
  ['22b-assistant-answer.png', 'دستیار فوتبالی', 'بپرس، فوری جواب بگیر', '#9b6bff'],
  ['15-compare.png', 'مقایسه بازیکن‌ها', 'آمار دو بازیکن کنار هم', '#ff8a3d'],
  ['17-fantasy.png', 'فانتزی و کوئیز', 'هر روز سؤال تازه', '#22c3ff'],
];
const page = (img, h, s, c) => `<!doctype html><html dir="rtl"><head><style>
@font-face{font-family:V;src:url(${font('vazir_black.ttf')})}@font-face{font-family:VB;src:url(${font('vazir_bold.ttf')})}
*{margin:0;box-sizing:border-box}body{width:1080px;height:1920px;overflow:hidden;font-family:V;color:#fff;
background:radial-gradient(900px 700px at 50% 0%,${c}55,transparent 70%),linear-gradient(#0b1016,#05070a);display:flex;flex-direction:column;align-items:center}
h1{font-size:92px;margin-top:110px;line-height:1.2;text-align:center}p{font-family:VB;font-size:46px;color:#b8c2cc;margin-top:18px}
.ph{margin-top:70px;width:700px;height:1505px;border-radius:70px;background:#111;padding:18px;box-shadow:0 0 0 4px ${c}aa,0 40px 120px ${c}55}
.ph img{width:100%;height:100%;object-fit:cover;object-position:top;border-radius:54px;display:block}
.bar{width:140px;height:10px;border-radius:5px;background:${c};margin-top:26px}</style></head>
<body><h1>${h}</h1><div class="bar"></div><p>${s}</p><div class="ph"><img src="${A(img)}"></div></body></html>`;
mkdirSync('screens', { recursive: true });
const b = await chromium.launch({ executablePath: process.env.CHROME });
const pg = await b.newPage({ viewport: { width: 1080, height: 1920 } });
for (const [i, [img, h, s, c]] of SHOTS.entries()) {
  await pg.setContent(page(img, h, s, c)); await pg.evaluate(() => document.fonts.ready);
  await pg.screenshot({ path: `screens/${i + 1}.png` });
}
await b.close();
