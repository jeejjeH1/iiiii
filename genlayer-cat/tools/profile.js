// node tools/profile.js 18.8  -> per-stage frame timings
const { chromium } = require('playwright');
const { serve } = require('./serve.js');
const path = require('path');
(async () => {
  const { url, close } = await serve(path.join(__dirname, '..'));
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  await page.goto(url + '/index.html?render' + (process.env.Q || ''), { waitUntil: 'domcontentloaded', timeout: 600000 });
  await page.waitForFunction(() => window.motionReady === true, null, { timeout: 600000 });
  for (const t of (process.argv[2] || '18.8').split(',').map(Number)) {
    const r = await page.evaluate(async (tt) => (await import('./film.js')).profile(tt), t);
    console.log(t, JSON.stringify(Object.fromEntries(Object.entries(r).map(([k, v]) => [k, typeof v === 'number' ? Math.round(v) : v]))));
  }
  await browser.close(); close();
})();
