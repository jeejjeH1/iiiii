// node render.js <name> [fps] [--preview t1,t2,...]
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

(async () => {
  const name = process.argv[2];
  const fps = +(process.argv[3] || 30);
  const prevIdx = process.argv.indexOf('--preview');
  const preview = prevIdx > 0 ? process.argv[prevIdx + 1].split(',').map(Number) : null;
  const dir = __dirname;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('console', m => console.log('page:', m.text()));
  page.on('pageerror', e => { console.error('PAGE ERROR', e); process.exit(1); });
  await page.goto('file://' + path.join(dir, 'engine.html'));
  await page.addScriptTag({ path: path.join(dir, 'videos', name + '.js') });
  await page.evaluate(() => setup(window.CFG));
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map(i => i.complete ? 0 : new Promise(r => { i.onload = i.onerror = r; })));
  });
  const dur = await page.evaluate(() => window.CFG.dur);
  // the moments the sound follows: taps, screen changes, zooms, alerts
  const ev = await page.evaluate(() => ({ dur: CFG.dur, taps: CFG.taps || [], screens: CFG.screens.slice(1).map(s => s.t),
    lenses: (CFG.lenses || []).map(l => l.t0), dings: CFG.dings || [], swaps: CFG.swaps || [], fan: CFG.fan || null }));
  fs.writeFileSync(path.join(dir, 'out', name + '.json'), JSON.stringify(ev));
  if (preview) {
    fs.mkdirSync(path.join(dir, 'prev'), { recursive: true });
    for (const t of preview) {
      await page.evaluate(t => render(t), t);
      await page.screenshot({ path: path.join(dir, 'prev', `${name}-${t}.jpg`), type: 'jpeg', quality: 80 });
    }
    await browser.close(); return;
  }
  const out = path.join(dir, 'out', name + '.mp4');
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const n = Math.round(dur * fps);
  for (let i = 0; i < n; i++) {
    await page.evaluate(t => render(t), i / fps);
    const buf = await page.screenshot({ type: 'jpeg', quality: 94 });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (i % 60 === 0) console.log(name, i, '/', n);
  }
  ff.stdin.end();
  await new Promise(r => ff.on('close', r));
  await browser.close();
  console.log('done', out);
})();
