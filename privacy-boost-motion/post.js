'use strict';
// Post-processing and frame compositing: glitch, bloom, camera shake/punch/whip, flares,
// light leaks, letterbox, grain.
// =================================================================== POST FX
function fxState(t) {
  const s = { shake: 0, flash: 0, glitch: 0, punch: 0, whip: 0, flare: 0 };
  for (const e of FX) {
    const d = t - e.t;
    if (d < 0 || d > e.dur) continue;
    const k = 1 - d / e.dur;
    if (e.type === 'shake') s.shake += e.amp * k * k;
    else if (e.type === 'flash') s.flash = Math.max(s.flash, e.a * k * k);
    else if (e.type === 'glitch') s.glitch = Math.max(s.glitch, e.amp * k);
    else if (e.type === 'punch') s.punch += e.amp * k * k * k;
    else if (e.type === 'flare') s.flare = Math.max(s.flare, e.a * Math.pow(k, 1.5));
    else if (e.type === 'whip') s.whip = Math.max(s.whip, Math.sin(Math.PI * (d / e.dur)) * e.amp);
  }
  return s;
}
// grain tiles
const GRAIN = [];
for (let g = 0; g < 4; g++) {
  const gc = mk(256, 256), gx = gc.getContext('2d');
  const id = gx.createImageData(256, 256);
  for (let i = 0; i < 256 * 256; i++) {
    const v = Math.floor(rnd(i, g + 100) * 255);
    id.data[i * 4] = id.data[i * 4 + 1] = id.data[i * 4 + 2] = v;
    id.data[i * 4 + 3] = 255;
  }
  gx.putImageData(id, 0, 0);
  GRAIN.push(ctx.createPattern(gc, 'repeat'));
}
const VIG = (() => {
  const v = mk(W, H), vx = v.getContext('2d');
  const g = vx.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 1.0);
  g.addColorStop(0, 'rgba(120,90,200,0)'); g.addColorStop(1, 'rgba(120,90,200,0.22)');
  vx.fillStyle = g; vx.fillRect(0, 0, W, H);
  return v;
})();

function rgbSplit(dst, src, off, oy) {
  const ch = [['#ff0000', -off, -oy], ['#00ff00', 0, 0], ['#0000ff', off, oy]];
  dst.save();
  dst.fillStyle = '#000'; dst.fillRect(0, 0, W, H);
  for (const [col, dx, dy] of ch) {
    tx2.globalCompositeOperation = 'source-over';
    tx2.drawImage(src, 0, 0);
    tx2.globalCompositeOperation = 'multiply';
    tx2.fillStyle = col; tx2.fillRect(0, 0, W, H);
    dst.globalCompositeOperation = 'lighter';
    dst.drawImage(tmp2, dx, dy);
  }
  dst.restore();
}

function render(t) {
  NOW = t;
  // 1. scene
  bx.setTransform(1, 0, 0, 1, 0, 0);
  bx.globalAlpha = 1;
  bx.globalCompositeOperation = 'source-over';
  bx.filter = 'none';
  for (const [a, b, fn] of SCENES) {
    if (t >= a && t < b) { bx.save(); fn(bx, t); bx.restore(); }
  }
  const f = fxState(t);
  let src = buf;
  // 2. glitch
  if (f.glitch > 0.03) {
    const seed = Math.floor(t * 30);
    rgbSplit(tx, buf, 6 + f.glitch * 28 * rnd(seed, 1), (rnd(seed, 2) - 0.5) * 8 * f.glitch);
    const nb = Math.floor(3 + f.glitch * 10);
    for (let i = 0; i < nb; i++) {
      const y = rnd(seed, 10 + i) * H, h = 8 + rnd(seed, 30 + i) * 90 * f.glitch;
      const dx = (rnd(seed, 50 + i) - 0.5) * 240 * f.glitch;
      tx.drawImage(buf, 0, y, W, h, dx, y, W, h);
    }
    src = tmp;
  }
  // 3. bloom source
  sx.filter = 'blur(6px)';
  sx.clearRect(0, 0, 480, 270);
  sx.drawImage(src, 0, 0, 480, 270);
  sx.filter = 'none';
  sx2.filter = 'blur(10px)';
  sx2.clearRect(0, 0, 240, 135);
  sx2.drawImage(src, 0, 0, 240, 135);
  sx2.filter = 'none';
  // 4. composite with camera
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  const shx = (Math.sin(t * 93.1) * 0.6 + Math.sin(t * 57.3 + 1) * 0.4) * f.shake;
  const shy = (Math.sin(t * 71.7 + 2) * 0.6 + Math.sin(t * 113.9) * 0.4) * f.shake;
  const z = 1 + f.punch + 0.012;
  ctx.save();
  ctx.translate(W / 2 + shx, H / 2 + shy);
  ctx.scale(z, z);
  ctx.rotate(f.shake * 0.0006 * Math.sin(t * 40));
  ctx.translate(-W / 2, -H / 2);
  if (f.whip > 0.02) {
    const n = 7;
    for (let k = 0; k < n; k++) {
      ctx.globalAlpha = k === 0 ? 1 : 0.32;
      ctx.drawImage(src, (k - (n - 1) / 2) * f.whip * 70, 0);
    }
    ctx.globalAlpha = 1;
  } else ctx.drawImage(src, 0, 0);
  ctx.globalCompositeOperation = 'screen';
  ctx.globalAlpha = 0.16;
  ctx.drawImage(sm, 0, 0, W, H);
  ctx.globalAlpha = 0.14;
  ctx.drawImage(sm2, 0, 0, W, H);
  ctx.restore();
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  // 5. anamorphic flare on impacts + drifting light leaks
  flare(ctx, W / 2, H / 2, f.flare);
  ctx.save();
  ctx.globalCompositeOperation = 'screen';
  [[0.15, '255,150,210'], [0.85, '140,220,255']].forEach(([k, col], i) => {
    const lx = W * (k + 0.12 * Math.sin(t * 0.21 + i * 2)), ly = H * (0.3 + 0.4 * Math.sin(t * 0.13 + i));
    const g = ctx.createRadialGradient(lx, ly, 0, lx, ly, 700);
    g.addColorStop(0, `rgba(${col},0.10)`); g.addColorStop(1, `rgba(${col},0)`);
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  });
  ctx.restore();
  // 6. vignette, scanlines on glitch, grain, flash
  ctx.drawImage(VIG, 0, 0);
  if (f.glitch > 0.05) {
    ctx.globalAlpha = f.glitch * 0.25;
    ctx.fillStyle = '#000';
    for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 2);
    ctx.globalAlpha = 1;
  }
  ctx.save();
  ctx.globalCompositeOperation = 'overlay';
  ctx.globalAlpha = 0.045;
  const gi = Math.floor(t * 30) % 4;
  ctx.translate(Math.floor(rnd(gi, Math.floor(t * 30)) * 256), Math.floor(rnd(gi + 9, Math.floor(t * 30)) * 256));
  ctx.fillStyle = GRAIN[gi];
  ctx.fillRect(-256, -256, W + 512, H + 512);
  ctx.restore();
  if (f.flash > 0.01) {
    ctx.fillStyle = `rgba(255,255,255,${f.flash})`;
    ctx.fillRect(0, 0, W, H);
  }
  // 7. letterbox
  const lb = letterbox(t);
  if (lb > 0.5) {
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, Math.ceil(lb));
    ctx.fillRect(0, H - Math.ceil(lb), W, Math.ceil(lb));
  }
}

window.renderFrame = (t, q = 0.93) => { render(t); return canvas.toDataURL('image/jpeg', q); };
window.renderOnly = (t) => { render(t); return true; };
window.DURATION = DURATION;
window.motionReady = true;
