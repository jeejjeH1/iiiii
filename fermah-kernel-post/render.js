// node render.js  -> fermah-kernel-post.png (3200x1800)
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 2 });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  await page.goto('file://' + path.join(__dirname, 'index.html'), { waitUntil: 'networkidle' });
  await page.waitForFunction(() => window.ready === true);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(__dirname, 'fermah-kernel-post.png') });
  await browser.close();
})();
