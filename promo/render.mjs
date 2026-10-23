// Renders promo/index.html frame by frame (30 fps) into promo/frames/*.jpg.
// Usage: node promo/render.mjs [from] [to] [step]   (seconds; step>1 frame only for previews)
import { createRequire } from 'module';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
const here = path.dirname(fileURLToPath(import.meta.url));
// PW points at a global install when there is no local node_modules
const { chromium } = createRequire(import.meta.url)(process.env.PW || 'playwright');
const [from = 0, to = 22, every = 1] = process.argv.slice(2).map(Number);
const FPS = 30;
const out = path.join(here, 'frames');
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined,
  args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
await page.goto('file://' + path.join(here, 'index.html'));
await page.evaluate(() => window.ready);
for (let f = Math.round(from * FPS); f < Math.round(to * FPS); f += every) {
  const data = await page.evaluate((t) => { window.render(t); return document.getElementById('c').toDataURL('image/jpeg', 0.92); }, f / FPS);
  fs.writeFileSync(path.join(out, `f${String(f).padStart(4, '0')}.jpg`), Buffer.from(data.split(',')[1], 'base64'));
}
await browser.close();
