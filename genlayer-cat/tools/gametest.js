// Drives game.html?test headless: menu, a scripted run, and screenshots into build/gametest/.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { serve } = require('./serve.js');
(async () => {
  const out = path.join(__dirname, '..', 'build', 'gametest');
  fs.mkdirSync(out, { recursive: true });
  const { url, close } = await serve(path.join(__dirname, '..'));
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const vp = (process.env.VP || '1280x720').split('x').map(Number);
  const page = await browser.newPage({ viewport: { width: vp[0], height: vp[1] } });
  const errs = [];
  page.on('pageerror', (e) => errs.push('PAGE ERROR ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') errs.push('CONSOLE ' + m.text().slice(0, 300)); });
  await page.goto(url + '/game.html?test', { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 180000 });
  const snap = async (name) => { await page.screenshot({ path: path.join(out, name + '.jpg'), quality: 80, type: 'jpeg' }); console.log('shot', name); };
  await snap('0_menu');
  const steps = JSON.parse(process.env.STEPS || '[["start"],["tick",2],["shot","1_run"],["tick",3],["shot","2_run"]]');
  for (const [op, a] of steps) {
    if (op === 'start') await page.evaluate(() => window.__tr.startRun());
    else if (op === 'tick') await page.evaluate(([s, k]) => window.__tr.tick(s, 30, k ?? -1), Array.isArray(a) ? a : [a]);
    else if (op === 'shot') await snap(a);
    else if (op === 'do') await page.evaluate((f) => window.__tr[f](f === 'move' ? 1 : undefined), a);
    else if (op === 'eval') console.log(JSON.stringify(await page.evaluate(a)));
  }
  console.log(errs.length ? errs.join('\n') : 'no page errors');
  await browser.close(); close();
})().catch((e) => { console.error(e); process.exit(1); });
