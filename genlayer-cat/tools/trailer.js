// Truth Runner trailer: drives game.html?test frame by frame (real gameplay, autopilot), draws
// motion-graphics titles on an overlay canvas, screenshots each frame into ffmpeg.
//   node tools/trailer.js  -> build/trailer_noaudio.mp4
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { serve } = require('./serve.js');

const FPS = 30, W = 1920, H = 1080;
const STEP = Number(process.env.STEP || 1); // >1: preview, one captured frame per STEP sim frames
const OUT = path.join(__dirname, '..', 'build', process.env.STEP ? 'trailer_preview.mp4' : 'trailer_noaudio.mp4');

// Shots on 2 s bars (120 BPM). The audio uses the same boundaries.
const SHOTS = [
  { id: 'title', t0: 0, t1: 4 },
  { id: 'run', t0: 4, t1: 10 },
  { id: 'gate', t0: 10, t1: 16 },
  { id: 'fast', t0: 16, t1: 20 },
  { id: 'roam', t0: 20, t1: 26 },
  { id: 'outro', t0: 26, t1: 32 },
];

const OVERLAY = `
(() => {
  const css = document.createElement('style');
  css.textContent = '*{transition:none!important;animation:none!important} #startMenu,#overMenu,#pauseMenu,#roamHud,.keys,#loading{display:none!important} #scene{transform-origin:50% 55%}';
  document.head.append(css);
  const c = document.createElement('canvas'); c.width = ${W}; c.height = ${H};
  Object.assign(c.style, { position: 'fixed', inset: '0', width: '100%', height: '100%', zIndex: 50, pointerEvents: 'none' });
  document.body.append(c);
  const g = c.getContext('2d');
  // capture the game's toasts and replay them deterministically on sim time
  const toasts = [];
  new MutationObserver((ms) => ms.forEach((m) => m.addedNodes.forEach((n) => {
    if (!n.classList || !n.classList.contains('toast')) return;
    const sub = n.querySelector('small');
    toasts.push({ text: n.firstChild.textContent, sub: sub ? sub.textContent : '', cls: n.className, t: window.__T });
    n.remove();
  }))).observe(document.getElementById('toasts'), { childList: true });
  const ease = (x) => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 3);
  const win = (t, a, b, f = 0.35) => Math.min(ease((t - a) / f), 1 - ease((t - (b - f)) / f));
  const PINK = '#ff87ff', VIOLET = '#dc00ff', CYAN = '#35e1f4', FAKE = '#ff4d6d';
  function grad(y0, y1) { const gr = g.createLinearGradient(0, y0, 0, y1); gr.addColorStop(0, '#ffffff'); gr.addColorStop(1, '#ffc6ff'); return gr; }
  function caption(t, a, b, eyebrow, line, col = PINK) {
    const v = win(t, a, b); if (v <= 0) return;
    g.save(); g.globalAlpha = v;
    const x = 110, y = 880, dx = (1 - v) * -60;
    const bg = g.createLinearGradient(0, 640, 0, ${H}); bg.addColorStop(0, 'rgba(10,2,18,0)'); bg.addColorStop(1, 'rgba(10,2,18,0.75)');
    g.fillStyle = bg; g.fillRect(0, 640, ${W}, ${H} - 640);
    g.fillStyle = col; g.fillRect(x + dx, y - 116, 64 * v, 4);
    g.font = '600 26px "IBM Plex Mono"'; g.letterSpacing = '8px'; g.fillText(eyebrow, x + 84 + dx, y - 104);
    g.letterSpacing = '0px'; g.font = '900 76px Unbounded'; g.fillStyle = grad(y - 70, y + 10);
    g.shadowColor = 'rgba(220,0,255,0.6)'; g.shadowBlur = 30;
    g.fillText(line, x + dx, y);
    g.restore();
  }
  function wipe(t, at) { // diagonal pink/violet bars sweeping across a cut
    const u = (t - at + 0.25) / 0.5; if (u <= 0 || u >= 1) return;
    g.save();
    [PINK, VIOLET, '#7a1fa8'].forEach((col, i) => {
      const p = Math.min(1, Math.max(0, u * 1.6 - i * 0.18));
      const x = -900 + p * (${W} + 1800);
      g.fillStyle = col; g.beginPath();
      g.moveTo(x - 500, 0); g.lineTo(x + 200, 0); g.lineTo(x - 100, ${H}); g.lineTo(x - 800, ${H}); g.closePath(); g.fill();
    });
    g.restore();
  }
  function flash(t, at, a = 0.7) { const u = (t - at) / 0.35; if (u < 0 || u > 1) return; g.fillStyle = 'rgba(255,236,255,' + (a * (1 - u)) + ')'; g.fillRect(0, 0, ${W}, ${H}); }
  function logo(x, y, s, col) { // GenLayer mark (two halves + diamond)
    const L = [[-0.049, 0], [-0.049, 0.343], [-0.219, 0.733], [-0.066, 0.818], [-0.506, 1]], D = [[0, 0.483], [0.094, 0.689], [0, 0.739], [-0.094, 0.689]];
    g.fillStyle = col; g.beginPath();
    for (const m of [1, -1]) { L.forEach(([u, v], i) => (i ? g.lineTo : g.moveTo).call(g, x + u * s * m, y + v * s)); g.closePath(); }
    D.forEach(([u, v], i) => (i ? g.lineTo : g.moveTo).call(g, x + u * s, y + v * s)); g.closePath(); g.fill('nonzero');
  }
  function drawToasts(T) {
    for (const k of toasts) {
      const u = (T - k.t) / 1.2; if (u < 0 || u > 1) continue;
      const big = !k.cls.includes('small');
      const col = k.cls.includes('trust') ? CYAN : k.cls.includes('fake') ? FAKE : k.cls.includes('gold') ? '#ffd36b' : PINK;
      const sc = u < 0.15 ? 0.6 + u / 0.15 * 0.46 : u < 0.25 ? 1.06 - (u - 0.15) * 0.6 : 1;
      const a = u < 0.15 ? u / 0.15 : u > 0.75 ? (1 - u) / 0.25 : 1;
      g.save(); g.globalAlpha = a; g.translate(${W / 2}, ${H * 0.36} - u * 30); g.scale(sc, sc);
      g.textAlign = 'center'; g.fillStyle = col; g.shadowColor = col; g.shadowBlur = 30;
      g.font = '900 ' + (big ? 92 : 40) + 'px Unbounded'; g.fillText(k.text, 0, 0);
      if (k.sub) { g.shadowBlur = 0; g.fillStyle = '#f7eeff'; g.font = '600 24px "IBM Plex Mono"'; g.fillText(k.sub, 0, big ? 50 : 36); }
      g.restore();
    }
  }
  window.__ov = (T, shot, lt) => {
    g.clearRect(0, 0, ${W}, ${H});
    document.getElementById('scene').style.transform = shot === 'title' ? 'scale(' + (1.12 - lt * 0.025) + ')' : shot === 'outro' ? 'scale(' + (1 + lt * 0.012) + ')' : 'none';
    drawToasts(T);
    if (shot === 'title') {
      const v = ease((lt - 0.9) / 0.5);
      g.fillStyle = 'rgba(10,2,18,' + (0.55 * Math.max(0, 1 - lt / 1.0) + 0.25) + ')'; g.fillRect(0, 0, ${W}, ${H});
      g.save(); g.textAlign = 'left';
      g.globalAlpha = ease(lt / 0.6); g.fillStyle = PINK; g.font = '600 28px "IBM Plex Mono"'; g.letterSpacing = '10px';
      g.fillText('GENLAYER COMMUNITY · NEW GAME', 120, 330);
      g.letterSpacing = '0px';
      g.globalAlpha = v; g.font = '900 210px Unbounded'; g.fillStyle = '#fff'; g.shadowColor = 'rgba(220,0,255,0.7)'; g.shadowBlur = 50;
      g.fillText('Truth', 110 - (1 - v) * 120, 540);
      const v2 = ease((lt - 1.25) / 0.5); g.globalAlpha = v2; g.shadowBlur = 0;
      g.lineWidth = 5; g.strokeStyle = CYAN; g.shadowColor = CYAN; g.shadowBlur = 30; g.strokeText('Runner', 110 + (1 - v2) * 120, 740);
      g.globalAlpha = ease((lt - 2.0) / 0.5); g.shadowBlur = 0; g.fillStyle = '#e8d6ff'; g.font = '700 36px Manrope';
      g.fillText('An endless runner where every claim needs evidence.', 120, 830);
      g.restore();
      flash(lt, 1.0, 0.55);
    }
    if (shot === 'run') caption(lt, 0.2, 6, 'HOW TO PLAY', 'Run the data track.');
    if (shot === 'gate') {
      caption(lt, 0.0, 2.9, 'EVIDENCE', 'Grab cyan, trusted data.', CYAN);
      caption(lt, 3.0, 6, 'CONSENSUS', 'Cross the gate that’s true.', '#ffd36b');
    }
    if (shot === 'fast') caption(lt, 0.0, 4, 'DODGE', 'Jump firewalls. Slide bots.', FAKE);
    if (shot === 'roam') caption(lt, 0.2, 6, 'FREE ROAM', 'Or just wander the meadow.', VIOLET);
    if (shot === 'outro') {
      g.fillStyle = 'rgba(10,2,18,' + Math.min(0.62, lt / 0.6 * 0.62) + ')'; g.fillRect(0, 0, ${W}, ${H});
      const a = ease((lt - 0.4) / 0.5);
      g.save(); g.globalAlpha = a; g.textAlign = 'center';
      logo(${W / 2}, 150 - (1 - a) * 40, 190, PINK);
      g.font = '900 150px Unbounded'; g.fillStyle = '#fff'; g.shadowColor = 'rgba(220,0,255,0.7)'; g.shadowBlur = 40;
      g.fillText('TRUTH RUNNER', ${W / 2}, 520);
      g.shadowBlur = 0;
      g.globalAlpha = ease((lt - 1.1) / 0.5);
      g.font = '800 46px Manrope'; g.fillStyle = CYAN; g.fillText('Verify, then trust. Play now.', ${W / 2}, 610);
      g.globalAlpha = ease((lt - 1.8) / 0.5);
      g.font = '600 30px "IBM Plex Mono"'; g.fillStyle = '#e8d6ff';
      g.fillText('Made by 0xMASTER82  ·  X @0xMASTER82  ·  Discord yapmster', ${W / 2}, 720);
      g.fillStyle = PINK; g.fillText('Built for the GenLayer community', ${W / 2}, 776);
      g.restore();
      const fo = Math.max(0, (lt - 5.2) / 0.8); if (fo > 0) { g.fillStyle = 'rgba(0,0,0,' + Math.min(1, fo) + ')'; g.fillRect(0, 0, ${W}, ${H}); }
    }
    for (const at of [4, 10, 16, 20, 26]) wipe(T, at);
    if (T < 0.4) { g.fillStyle = 'rgba(0,0,0,' + (1 - T / 0.4) + ')'; g.fillRect(0, 0, ${W}, ${H}); }
  };
})();
`;

(async () => {
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  const { url, close } = await serve(path.join(__dirname, '..'));
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  await page.goto(url + '/game.html?test', { waitUntil: 'domcontentloaded', timeout: 300000 });
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 300000 });
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(OVERLAY);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '14', '-pix_fmt', 'yuv420p', '-r', String(FPS), OUT], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  let frame = 0;
  const grab = async (T, shot, lt, skill) => {
    await page.evaluate(([T, shot, lt, skill, st]) => { window.__T = T; window.__tr.tick(st / 30, 30, skill); window.__ov(T, shot, lt); }, [T, shot, lt, skill, STEP]);
    const buf = await page.screenshot({ type: 'jpeg', quality: 92, timeout: 300000 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
    if (++frame % 30 === 0) console.log(`frame ${frame}/${32 * FPS}  ${((Date.now() - t0) / 60000).toFixed(1)} min`);
  };
  const ev = (f, a) => page.evaluate(f, a);
  for (const S of SHOTS) {
    console.log('shot', S.id, ((Date.now() - t0) / 60000).toFixed(1) + ' min');
    // set up each shot (fast-forwarding the simulation where needed, uncaptured)
    if (S.id === 'run') await ev(() => window.__tr.startRun());
    if (S.id === 'gate') await ev(() => { const G = window.__tr.G; let n = 0; while (G.dist < G.round.gateS - G.speed * 3.2 && n++ < 6000) window.__tr.tick(1 / 30, 30, 1); });
    if (S.id === 'fast') await ev(() => { window.__tr.tick(22, 30, 1); window.__tr.G.lives = 3; });
    if (S.id === 'roam') await ev(() => { window.__tr.startRoam(); window.__tr.roamKeys.add('KeyW'); window.__tr.roamKeys.add('ShiftLeft'); window.__tr.tick(0.6, 30, -1); });
    if (S.id === 'outro') await ev(() => { window.__tr.roamKeys.clear(); window.__tr.endRoam(); const G = window.__tr.G; G.x = 0; G.dist = 0; G.camBlend = 0; window.__tr.tick(0.5, 30, -1); });
    const n = Math.round((S.t1 - S.t0) * FPS);
    for (let i = 0; i < n; i += STEP) {
      const T = S.t0 + i / FPS, lt = i / FPS;
      if (S.id === 'roam') await ev((lt) => { const k = window.__tr.roamKeys; k.delete('KeyA'); k.delete('KeyD'); if (lt > 1.2 && lt < 2.4) k.add('KeyA'); if (lt > 3.4 && lt < 4.2) k.add('KeyD'); if (lt > 4.6 && lt < 4.7 && window.__tr.G.vy === 0) window.__tr.G.vy = 10.6; }, lt);
      await grab(T, S.id, lt, S.id === 'run' || S.id === 'gate' || S.id === 'fast' ? 1 : -1);
    }
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  await browser.close(); close();
  console.log('done', OUT);
})().catch((e) => { console.error(e); process.exit(1); });
