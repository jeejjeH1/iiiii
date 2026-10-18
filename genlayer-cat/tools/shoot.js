// Turnaround check: renders the model with the same framing as the reference sheet crops.
//   node tools/shoot.js [outdir] [views=front,side,back,hero34]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { serve } = require('./serve.js');

(async () => {
  const out = path.resolve(process.argv[2] || 'build/turnaround');
  const views = (process.argv[3] || 'front,side,back,hero34').split(',');
  fs.mkdirSync(out, { recursive: true });
  const { url, close } = await serve(path.join(__dirname, '..'));
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 1700 } });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.error('CONSOLE', m.text()); });
  await page.goto(url + '/turnaround.html');
  await page.waitForFunction(() => window.ready === true, null, { timeout: 120000 });
  for (const v of views) {
    const t0 = Date.now();
    const data = await page.evaluate((n) => window.renderView(n), v);
    fs.writeFileSync(path.join(out, v + '.png'), Buffer.from(data.split(',')[1], 'base64'));
    console.log(v, ((Date.now() - t0) / 1000).toFixed(1) + 's');
  }
  await browser.close();
  close();
})().catch((e) => { console.error(e); process.exit(1); });
