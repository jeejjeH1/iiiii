'use strict';
// Scenes for the Startale × Privacy Boost post. Each scene draws a full frame for time t.

// ---------------------------------------------------------------- brand marks
const PB = { navy: '#0b0b14', blue: '#3b47ff', ink: '#16151f' };

// Privacy Boost mark: dark disc, white needle with a concave "spark" in the middle,
// blue light trailing on the lower left. Coordinates are in units of the radius.
function pbLogo(c, x, y, r, o = {}) {
  c.save();
  c.translate(x, y);
  if (o.rot) c.rotate(o.rot);
  if (o.scale != null) c.scale(o.scale, o.scale);
  c.globalAlpha *= o.a ?? 1;
  if (o.glow) {
    const g = c.createRadialGradient(0, 0, r * 0.6, 0, 0, r * 2.2);
    g.addColorStop(0, `rgba(80,90,255,${0.45 * o.glow})`);
    g.addColorStop(1, 'rgba(80,90,255,0)');
    c.fillStyle = g;
    c.beginPath(); c.arc(0, 0, r * 2.2, 0, TAU); c.fill();
  }
  c.beginPath(); c.arc(0, 0, r, 0, TAU);
  c.fillStyle = PB.navy;
  c.fill();
  c.save();
  c.clip();
  // blue light, left of the needle
  const sweep = o.sweep ?? 1;
  c.save();
  c.beginPath();
  c.moveTo(-1.1 * r, -0.02 * r);
  c.lineTo(-0.3 * r, -0.02 * r);
  c.lineTo(-0.62 * r, 0.76 * r);
  c.lineTo(-1.1 * r, 1.1 * r);
  c.closePath();
  c.clip();
  const bg = c.createLinearGradient(-r, 0, -0.25 * r, 0);
  bg.addColorStop(0, `rgba(59,71,255,${0.95 * sweep})`);
  bg.addColorStop(0.55, `rgba(59,71,255,${0.6 * sweep})`);
  bg.addColorStop(1, 'rgba(59,71,255,0)');
  c.fillStyle = bg;
  c.fillRect(-1.1 * r, -0.02 * r, 0.9 * r, 1.2 * r);
  const vg = c.createLinearGradient(0, -0.02 * r, 0, 0.3 * r);
  vg.addColorStop(0, `rgba(120,130,255,${0.35 * sweep})`);
  vg.addColorStop(1, 'rgba(120,130,255,0)');
  c.fillStyle = vg;
  c.fillRect(-1.1 * r, -0.02 * r, 0.9 * r, 0.4 * r);
  c.restore();
  // needle + spark
  const k = o.needle ?? 1;
  const P1 = [0.62 * r, -0.72 * r], P3 = [-0.64 * r, 0.78 * r];
  const L1 = [-0.31 * r, 0.03 * r], R1 = [0.27 * r, 0.1 * r];
  const cx = -0.02 * r, cy = 0.07 * r;
  const lerpPt = (a, b, u) => [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u];
  const p1 = lerpPt([cx, cy], P1, k), p3 = lerpPt([cx, cy], P3, k);
  c.beginPath();
  c.moveTo(p1[0], p1[1]);
  c.quadraticCurveTo(cx + 0.02 * r, cy - 0.12 * r, L1[0], L1[1]);
  c.quadraticCurveTo(cx - 0.04 * r, cy + 0.06 * r, p3[0], p3[1]);
  c.quadraticCurveTo(cx + 0.02 * r, cy + 0.1 * r, R1[0], R1[1]);
  c.quadraticCurveTo(cx + 0.05 * r, cy - 0.04 * r, p1[0], p1[1]);
  c.closePath();
  c.fillStyle = '#fff';
  c.fill();
  c.restore();
  // rim highlight
  if (o.rim) {
    const cg = c.createConicGradient(NOW * 1.2, 0, 0);
    ['rgba(255,120,200,0.9)', 'rgba(120,220,255,0.9)', 'rgba(140,240,190,0.9)', 'rgba(190,150,255,0.9)', 'rgba(255,120,200,0.9)'].forEach((col, i) => cg.addColorStop(i / 4, col));
    c.strokeStyle = cg;
    c.globalAlpha *= o.rim;
    c.lineWidth = Math.max(2, r * 0.05);
    c.beginPath(); c.arc(0, 0, r * 1.04, 0, TAU); c.stroke();
  }
  c.restore();
}
// "Privacy Boost" wordmark with the blue foot on the P
function pbWord(c, x, y, size, o = {}) {
  const f = { size, weight: 700, color: o.color || PB.ink, ls: -size * 0.01 };
  const w = textW(c, 'Privacy Boost', f);
  c.save();
  c.globalAlpha *= o.a ?? 1;
  text(c, 'Privacy Boost', x, y, f);
  c.fillStyle = PB.blue;
  const x0 = x - w / 2;
  c.fillRect(x0 + size * 0.075, y + size * 0.2, size * 0.13, size * 0.17);
  c.restore();
}
// Startale 8-square mark, assembling square by square from t0
function stLogo(c, x, y, r, t, t0, o = {}) {
  logo(c, x, y, r, {
    color: o.color || C.ink,
    scale: o.scale,
    rot: o.rot,
    sq: (i, a, m) => {
      const q = E.outBack2(P(t, t0 + i * 0.06, 0.45));
      if (q <= 0) return null;
      return { x: m.x * (0.2 + 0.8 * q), y: m.y * (0.2 + 0.8 * q), s: q, a: clamp(q * 2) * (o.a ?? 1), rot: m.rot + (1 - q) * 3, flash: Math.max(0, 1 - (t - t0 - i * 0.06) * 3) };
    },
  });
}

// ---------------------------------------------------------------- cinematic helpers
function cam(c, sc, cx = W / 2, cy = H / 2, dx = 0, dy = 0) {
  c.translate(cx + dx, cy + dy);
  c.scale(sc, sc);
  c.translate(-cx, -cy);
}
function bgDark(c, t, tint = '255,46,91', ti = 1) {
  c.fillStyle = '#07061a';
  c.fillRect(0, 0, W, H);
  const blobs = [[0.2, 0.25, '123,92,255', 900], [0.85, 0.3, '11,179,207', 800], [0.5, 0.85, '255,61,154', 900]];
  blobs.forEach(([bx2, by2, rgb, r], i) => {
    const x = W * bx2 + Math.sin(t * 0.3 + i * 2) * 120, y = H * by2 + Math.cos(t * 0.25 + i) * 90;
    const g = c.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `rgba(${rgb},0.22)`); g.addColorStop(1, `rgba(${rgb},0)`);
    c.fillStyle = g; c.fillRect(0, 0, W, H);
  });
  const g = c.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, 1000);
  g.addColorStop(0, `rgba(${tint},${0.16 * ti})`); g.addColorStop(1, `rgba(${tint},0)`);
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  dust(c, t, 160, 31, 0.9, '200,190,255');
}
// scrolling hex "ledger" texture
function ledger(c, t, a, col = '255,255,255') {
  if (a <= 0.01) return;
  c.save();
  c.font = `500 18px ${MONO}`;
  c.textAlign = 'left'; c.textBaseline = 'top';
  const HEX = '0123456789abcdef';
  for (let row = 0; row < 40; row++) {
    const y = ((row * 30 - t * 40) % 1200 + 1200) % 1200 - 60;
    let sline = '';
    const seed = row + Math.floor((row * 30 - t * 40) / 1200) * 97;
    for (let k = 0; k < 120; k++) sline += k % 9 === 8 ? ' ' : HEX[Math.floor(rnd(seed, k) * 16)];
    c.fillStyle = `rgba(${col},${a * (0.25 + 0.75 * rnd(seed, 999))})`;
    c.fillText(sline, -20, y);
  }
  c.restore();
}
function darkCard(c, x, y, w, h, o = {}) {
  c.save();
  rr(c, x - w / 2, y - h / 2, w, h, o.r ?? 18);
  c.fillStyle = o.fill || 'rgba(18,14,40,0.82)';
  c.shadowColor = o.glow || 'rgba(255,46,91,0.45)';
  c.shadowBlur = 30;
  c.fill();
  c.shadowBlur = 0; c.shadowColor = 'rgba(0,0,0,0)';
  c.lineWidth = 2;
  c.strokeStyle = o.border || 'rgba(255,90,130,0.7)';
  c.stroke();
  c.restore();
}
function foot(c, x, y, ang, s, left) {
  c.save();
  c.translate(x, y);
  c.rotate(ang + Math.PI / 2);
  c.translate(left ? -s * 0.45 : s * 0.45, 0);
  c.beginPath(); c.ellipse(0, s * 0.15, s * 0.32, s * 0.55, 0, 0, TAU); c.fill();
  for (let k = 0; k < 4; k++) {
    const tx3 = (k - 1.5) * s * 0.17 * (left ? -1 : 1);
    c.beginPath(); c.arc(tx3, -s * 0.55 - Math.abs(k - 1) * s * 0.04, s * 0.085, 0, TAU); c.fill();
  }
  c.restore();
}
function orb(c, x, y, r, o = {}) {
  c.save();
  const dark = o.dark;
  const g = c.createRadialGradient(x - r * 0.3, y - r * 0.35, r * 0.1, x, y, r);
  if (dark) { g.addColorStop(0, '#3a2f7a'); g.addColorStop(1, '#120d33'); }
  else { g.addColorStop(0, '#ffffff'); g.addColorStop(1, '#e6dcff'); }
  c.fillStyle = g;
  c.shadowColor = o.glow || (dark ? 'rgba(255,46,91,0.7)' : 'rgba(123,92,255,0.5)');
  c.shadowBlur = 50;
  c.beginPath(); c.arc(x, y, r, 0, TAU); c.fill();
  c.shadowBlur = 0;
  c.lineWidth = 4;
  c.strokeStyle = dark ? 'rgba(255,120,160,0.9)' : 'rgba(123,92,255,0.6)';
  c.stroke();
  c.restore();
  icon(c, 'user', x, y + r * 0.02, r * 0.9, dark ? '#fff' : C.ink, Math.max(3, r * 0.07));
}
function reticle(c, x, y, r, t, col = '255,46,91', a = 1) {
  c.save();
  c.translate(x, y);
  c.globalAlpha *= a;
  c.strokeStyle = `rgba(${col},0.95)`;
  c.lineWidth = 3;
  c.rotate(t * 1.5);
  for (let k = 0; k < 4; k++) {
    c.beginPath(); c.arc(0, 0, r, k * Math.PI / 2 + 0.25, k * Math.PI / 2 + Math.PI / 2 - 0.25); c.stroke();
  }
  c.rotate(-t * 3);
  c.setLineDash([6, 10]);
  c.beginPath(); c.arc(0, 0, r * 0.7, 0, TAU); c.stroke();
  c.setLineDash([]);
  c.rotate(t * 1.5);
  c.beginPath();
  c.moveTo(-r * 1.3, 0); c.lineTo(-r * 0.4, 0); c.moveTo(r * 0.4, 0); c.lineTo(r * 1.3, 0);
  c.moveTo(0, -r * 1.3); c.lineTo(0, -r * 0.4); c.moveTo(0, r * 0.4); c.lineTo(0, r * 1.3);
  c.stroke();
  c.restore();
}
function stamp(c, label, x, y, t, t0, col = '#ff2e5b', rot = -0.18) {
  if (t < t0) return;
  const p = E.outExpo(P(t, t0, 0.22));
  c.save();
  c.translate(x, y);
  c.rotate(rot);
  const sc = lerp(2.4, 1, p);
  c.scale(sc, sc);
  c.globalAlpha *= clamp(p * 3);
  const f = { size: 34, weight: 900, color: col, ls: 4 };
  const w = textW(c, label, f) + 36;
  c.lineWidth = 4; c.strokeStyle = col;
  rr(c, -w / 2, -30, w, 60, 8); c.stroke();
  text(c, label, 0, 2, f);
  c.restore();
}
// iridescent glass block for the chain
function block(c, x, y, s, idx, a = 1, o = {}) {
  const d = s * 0.28;
  c.save();
  c.globalAlpha *= a;
  // top
  c.beginPath();
  c.moveTo(x - s / 2, y - s / 2); c.lineTo(x - s / 2 + d, y - s / 2 - d); c.lineTo(x + s / 2 + d, y - s / 2 - d); c.lineTo(x + s / 2, y - s / 2); c.closePath();
  c.fillStyle = o.dark ? 'rgba(90,70,190,0.55)' : 'rgba(255,255,255,0.9)'; c.fill();
  // side
  c.beginPath();
  c.moveTo(x + s / 2, y - s / 2); c.lineTo(x + s / 2 + d, y - s / 2 - d); c.lineTo(x + s / 2 + d, y + s / 2 - d); c.lineTo(x + s / 2, y + s / 2); c.closePath();
  c.fillStyle = o.dark ? 'rgba(40,30,100,0.7)' : 'rgba(205,190,255,0.85)'; c.fill();
  // front
  const g = c.createLinearGradient(x - s / 2, y - s / 2, x + s / 2, y + s / 2);
  if (o.dark) { g.addColorStop(0, 'rgba(60,40,140,0.85)'); g.addColorStop(1, 'rgba(25,18,70,0.9)'); }
  else { g.addColorStop(0, 'rgba(255,236,250,0.95)'); g.addColorStop(0.5, 'rgba(232,246,255,0.95)'); g.addColorStop(1, 'rgba(226,255,240,0.95)'); }
  c.fillStyle = g;
  c.fillRect(x - s / 2, y - s / 2, s, s);
  // iridescent edges
  const cg = c.createConicGradient(NOW * 0.8 + idx, x, y);
  ['255,120,200', '120,220,255', '140,240,190', '190,150,255', '255,120,200'].forEach((col, k) => cg.addColorStop(k / 4, `rgba(${col},0.95)`));
  c.strokeStyle = cg;
  c.lineWidth = Math.max(2, s * 0.025);
  c.lineJoin = 'round';
  c.strokeRect(x - s / 2, y - s / 2, s, s);
  c.beginPath();
  c.moveTo(x - s / 2, y - s / 2); c.lineTo(x - s / 2 + d, y - s / 2 - d); c.lineTo(x + s / 2 + d, y - s / 2 - d); c.lineTo(x + s / 2 + d, y + s / 2 - d); c.lineTo(x + s / 2, y + s / 2);
  c.moveTo(x + s / 2, y - s / 2); c.lineTo(x + s / 2 + d, y - s / 2 - d);
  c.stroke();
  // content
  const ink = o.dark ? '#fff' : C.ink;
  text(c, '#' + (18402330 + idx).toLocaleString('en-US'), x, y - s * 0.2, { size: s * 0.11, weight: 700, color: ink, fam: MONO });
  c.fillStyle = o.dark ? 'rgba(255,120,160,0.8)' : 'rgba(123,92,255,0.35)';
  for (let k = 0; k < 3; k++) c.fillRect(x - s * 0.32, y + s * (0.02 + k * 0.12), s * (0.64 - rnd(idx, k) * 0.3), s * 0.05);
  c.restore();
}
// anamorphic flare streak (also used by post fx)
function flare(c, x, y, a, len = 1.0) {
  if (a <= 0.01) return;
  c.save();
  c.globalCompositeOperation = 'lighter';
  const g = c.createRadialGradient(x, y, 0, x, y, 260);
  g.addColorStop(0, `rgba(255,255,255,${0.75 * a})`);
  g.addColorStop(0.25, `rgba(170,160,255,${0.35 * a})`);
  g.addColorStop(1, 'rgba(120,100,255,0)');
  c.fillStyle = g;
  c.beginPath(); c.arc(x, y, 260, 0, TAU); c.fill();
  c.translate(x, y);
  c.scale(1, 0.018);
  const g2 = c.createRadialGradient(0, 0, 0, 0, 0, 1400 * len);
  g2.addColorStop(0, `rgba(255,255,255,${0.9 * a})`);
  g2.addColorStop(0.2, `rgba(140,170,255,${0.55 * a})`);
  g2.addColorStop(1, 'rgba(90,110,255,0)');
  c.fillStyle = g2;
  c.beginPath(); c.arc(0, 0, 1400 * len, 0, TAU); c.fill();
  c.restore();
  // ghosts
  c.save();
  c.globalCompositeOperation = 'lighter';
  [[-0.35, 40, '255,120,200'], [0.4, 26, '120,220,255'], [0.75, 60, '190,150,255']].forEach(([k, r, col]) => {
    const gx = x + (W / 2 - x) * 2 * k, gy = y + (H / 2 - y) * 2 * k;
    const gg = c.createRadialGradient(gx, gy, 0, gx, gy, r);
    gg.addColorStop(0, `rgba(${col},${0.35 * a})`); gg.addColorStop(1, `rgba(${col},0)`);
    c.fillStyle = gg;
    c.beginPath(); c.arc(gx, gy, r, 0, TAU); c.fill();
  });
  c.restore();
}
function shards(c, x, y, t, t0, n = 46, seed = 3, maxR = 1300) {
  const p = (t - t0) / 1.4;
  if (p <= 0 || p >= 1) return;
  c.save();
  for (let i = 0; i < n; i++) {
    const a = rnd(i, seed) * TAU, sp = 0.4 + rnd(i, seed + 1) * 0.6;
    const e = E.outCubic(p);
    const d = 60 + e * maxR * sp;
    const sx3 = x + Math.cos(a) * d, sy3 = y + Math.sin(a) * d + p * p * 200;
    const sz = 14 + rnd(i, seed + 2) * 46;
    c.save();
    c.translate(sx3, sy3);
    c.rotate(a + p * (rnd(i, seed + 3) - 0.5) * 12);
    c.globalAlpha = (1 - p) * 0.9;
    const cols = ['255,140,210', '130,220,255', '150,240,200', '200,160,255', '255,255,255'];
    c.fillStyle = `rgba(${cols[i % 5]},0.85)`;
    c.strokeStyle = 'rgba(255,255,255,0.9)';
    c.lineWidth = 1.5;
    c.beginPath();
    c.moveTo(0, -sz); c.lineTo(sz * 0.6, sz * 0.5); c.lineTo(-sz * 0.5, sz * 0.7); c.closePath();
    c.fill(); c.stroke();
    c.restore();
  }
  c.restore();
}
function shieldBubble(c, x, y, r, t, a = 1) {
  if (r <= 1 || a <= 0.01) return;
  c.save();
  c.globalAlpha *= a;
  const g = c.createRadialGradient(x - r * 0.3, y - r * 0.35, r * 0.1, x, y, r);
  g.addColorStop(0, 'rgba(255,255,255,0.05)');
  g.addColorStop(0.75, 'rgba(225,215,255,0.22)');
  g.addColorStop(0.95, 'rgba(200,180,255,0.55)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  c.fillStyle = g;
  c.beginPath(); c.arc(x, y, r, 0, TAU); c.fill();
  const cg = c.createConicGradient(t * 0.9, x, y);
  ['255,120,200', '120,220,255', '140,240,190', '190,150,255', '255,200,140', '255,120,200'].forEach((col, k) => cg.addColorStop(k / 5, `rgba(${col},0.85)`));
  c.strokeStyle = cg;
  c.lineWidth = Math.max(3, r * 0.03);
  c.beginPath(); c.arc(x, y, r * 0.985, 0, TAU); c.stroke();
  // hex mesh hint
  c.globalAlpha *= 0.18;
  c.strokeStyle = '#7b5cff';
  c.lineWidth = 1.5;
  c.save();
  c.beginPath(); c.arc(x, y, r * 0.97, 0, TAU); c.clip();
  const hs = 46;
  for (let row = -12; row <= 12; row++) {
    for (let col = -12; col <= 12; col++) {
      const hx = x + col * hs * 1.5, hy = y + row * hs * 1.732 + (col % 2 ? hs * 0.866 : 0);
      if (Math.hypot(hx - x, hy - y) > r) continue;
      c.beginPath();
      for (let k = 0; k < 6; k++) { const aa = k * Math.PI / 3; const px = hx + Math.cos(aa) * hs * 0.9, py = hy + Math.sin(aa) * hs * 0.9; k ? c.lineTo(px, py) : c.moveTo(px, py); }
      c.closePath(); c.stroke();
    }
  }
  c.restore();
  c.restore();
  c.save();
  c.globalAlpha *= a;
  c.translate(x - r * 0.42, y - r * 0.5);
  c.rotate(-0.7);
  c.fillStyle = 'rgba(255,255,255,0.85)';
  c.beginPath(); c.ellipse(0, 0, r * 0.2, r * 0.07, 0, 0, TAU); c.fill();
  c.restore();
}
function pill(c, label, x, y, t, t0, o = {}) {
  if (t < t0) return;
  const p = E.outBack(P(t, t0, 0.4));
  const f = { size: o.size || 26, weight: 800, color: o.color || '#fff', ls: 5 };
  const w = textW(c, label, f) + 60, h = (o.size || 26) * 2;
  c.save();
  c.translate(x, y);
  c.scale(p, p);
  c.globalAlpha *= clamp(p) * (o.a ?? 1);
  rr(c, -w / 2, -h / 2, w, h, h / 2);
  const g = c.createLinearGradient(-w / 2, 0, w / 2, 0);
  g.addColorStop(0, '#09a9c4'); g.addColorStop(0.5, C.violet); g.addColorStop(1, C.pink);
  c.fillStyle = o.fill || g;
  c.fill();
  text(c, label, 0, 2, f);
  c.restore();
}

// =================================================================== SCENES
// 0. COLD OPEN ---------------------------------------------------------
function sOpen(c, t) {
  const push = 1 + t * 0.012;
  c.save();
  cam(c, push);
  bgBase(c, 'rgba(123,92,255,0.5)');
  dust(c, t, 120, 4, 0.7);
  // hero bubble drifting toward camera
  const br = 90 + E.inOut(P(t, 0, T(4))) * 120;
  bubble(c, 960 + Math.sin(t * 0.7) * 30, 540 + Math.cos(t * 0.5) * 20 - 30, br, t, 0.9 * (1 - P(t, T(1), 0.4) * 0.55));
  c.restore();
  // darkness lifting: black → pastel
  const lift = E.inOut(P(t, 0.15, T(1) - 0.1));
  c.fillStyle = `rgba(6,5,18,${1 - lift * 0.92 - P(t, T(1), 0.3) * 0.08})`;
  c.fillRect(0, 0, W, H);
  if (t < T(1) + 0.3) {
    const a = clamp(P(t, 0.25, 0.6)) * (1 - P(t, T(1) - 0.2, 0.3));
    c.save(); c.globalAlpha = a;
    text(c, 'STARTALE APP  ×  PRIVACY BOOST', 960, 540, { size: 30, weight: 600, color: 'rgba(255,255,255,0.9)', ls: 14 });
    c.restore();
  }
  // PRIVACY
  if (t >= T(1)) {
    c.save();
    const z = 1 + (t - T(1)) * 0.035;
    cam(c, z, 960, 450);
    const outA = 1 - E.inCubic(P(t, T(3, 3.3), 0.25));
    c.globalAlpha = outA;
    flare(c, 960, 450, 0.35 * (1 - P(t, T(1), 1.4)) + 0.08 * beatPulse(t, 4));
    scramble(c, 'PRIVACY', 960, 450, { size: 250, weight: 900, gradient: G_BRAND, ls: 12 }, t, T(1), 0.9, { });
    c.restore();
  }
  rise(c, 'could become one of the most important factors', 960, 640, { size: 52, weight: 600, color: C.ink }, t, T(2), { out: T(3) - 0.2 });
  typeOn(c, 'in the next phase of', 960, 630, { size: 48, weight: 600, color: C.dim }, t, T(3), 0.6, { caret: false, out: T(3, 3.4) });
  slam(c, 'WEB3 ADOPTION', 960, 760, { size: 128, weight: 900, gradient: G_COOL }, t, T(3, 2), { out: T(3, 3.4) });
}

// 1. TRANSPARENCY -------------------------------------------------------
function chain(c, t, o = {}) {
  const speed = o.speed ?? 240, y = o.y ?? 660, s = o.s ?? 170, gap = o.gap ?? 330, a = o.a ?? 1;
  const off = t * speed;
  const n = Math.ceil(W / gap) + 3;
  const base = Math.floor(off / gap);
  // link
  c.save();
  c.globalAlpha *= a;
  c.strokeStyle = o.dark ? 'rgba(255,120,160,0.6)' : 'rgba(123,92,255,0.45)';
  c.lineWidth = 6;
  c.setLineDash([18, 12]);
  c.lineDashOffset = off;
  c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke();
  c.setLineDash([]);
  c.restore();
  for (let k = -1; k < n; k++) {
    const idx = base + k;
    const x = idx * gap - off + gap * 0.5;
    const intro = o.t0 != null ? E.outBack(P(t, o.t0 + (k + 1) * 0.05, 0.5)) : 1;
    if (intro <= 0) continue;
    c.save();
    c.translate(x, y);
    c.scale(intro, intro);
    block(c, 0, 0, s, idx, a, o);
    if (o.foot && rnd(idx, 77) > 0.35) {
      c.fillStyle = `rgba(255,46,91,${o.foot})`;
      foot(c, -s * 0.2, s * 0.32, 0, 9, true);
      foot(c, s * 0.12, s * 0.26, 0, 9, false);
    }
    c.restore();
  }
}
function sTransparency(c, t) {
  const t0 = T(4);
  c.save();
  cam(c, 1.06 - 0.06 * E.outCubic(P(t, t0, 1.2)) + (t - t0) * 0.006);
  bgBase(c, 'rgba(11,179,207,0.5)');
  rays(c, 960, 380, t, { a: 0.06, n: 20, speed: 0.08 });
  gridFloor(c, t, { hy: 760, a: 0.25, sp: 1.2 });
  dust(c, t, 100, 9, 0.6);
  chain(c, t * 0.6, { y: 520, s: 90, gap: 210, speed: 160, a: 0.35 });
  chain(c, t, { y: 690, s: 170, gap: 330, t0 });
  c.restore();
  rise(c, 'Blockchain technology offers', 960, 170, { size: 44, weight: 600, color: C.dim }, t, t0 + 0.1, { out: T(7) - 0.25 });
  const words = [['TRANSPARENCY', T(4, 2), T(5)], ['VERIFIABILITY', T(5), T(6)], ['OPEN ACCESS', T(6), T(7)]];
  words.forEach(([w, a, b], i) => {
    slam(c, w, 960, 340, { size: 150, weight: 900, gradient: [G_BRAND, G_COOL, G_GREEN][i] }, t, a, { out: b - 0.14, outDir: i % 2 ? 'left' : 'right' });
  });
  rise(c, 'to financial infrastructure', 960, 455, { size: 46, weight: 700, color: C.ink }, t, T(6, 0.5), { out: T(7) - 0.2 });
  // progress dots
  for (let i = 0; i < 3; i++) {
    const on = t >= words[i][1];
    c.fillStyle = on ? C.violet : 'rgba(123,92,255,0.25)';
    c.beginPath(); c.arc(930 + i * 30, 950, on ? 8 : 6, 0, TAU); c.fill();
  }
}

// 2. THE TURN -----------------------------------------------------------
function sTurn(c, t) {
  const t0 = T(7);
  const dk = E.inCubic(P(t, T(7, 2), BEAT * 2));
  c.save();
  cam(c, 1 + (t - t0) * 0.05);
  bgBase(c, 'rgba(255,46,91,0.5)');
  chain(c, T(7) + (t - T(7)) * 0.4, { y: 690, s: 170, gap: 330, foot: dk });
  c.restore();
  c.fillStyle = `rgba(7,6,26,${dk * 0.94})`;
  c.fillRect(0, 0, W, H);
  const col = dk < 0.5 ? C.ink : '#fff';
  typeOn(c, 'But there is another side…', 960, 420, { size: 86, weight: 800, color: col, dark: dk >= 0.5 }, t, t0, 0.85, { caretColor: C.pink });
  if (t > T(7, 2)) {
    const a = E.outCubic(P(t, T(7, 2), 0.4));
    c.save(); c.globalAlpha = a;
    text(c, 'to this transparency.', 960, 530, { size: 46, weight: 600, color: dk < 0.5 ? C.dim : 'rgba(255,255,255,0.7)' });
    c.restore();
  }
}

// 3. EXPOSURE ------------------------------------------------------------
function sExposure(c, t) {
  const t0 = T(8);
  const freeze = P(t, T(12, 2), 0.01);
  const slow = t > T(12, 2) ? T(12, 2) + (t - T(12, 2)) * 0.15 : t; // time slows during the freeze
  const zoom = 1.12 - 0.12 * E.outCubic(P(t, t0, 1.4)) + E.inOut(P(t, T(11, 2), BAR)) * 0.08;
  c.save();
  cam(c, zoom);
  bgDark(c, slow, '255,46,91', 1 + 0.6 * beatPulse(t, 5));
  ledger(c, slow, 0.06);
  gridFloor(c, slow, { hy: 760, a: 0.3, sp: 0.6, col: '255,46,91' });
  const cx = 960, cy = 540;
  // radar sweep
  c.save();
  c.translate(cx, cy);
  c.rotate(slow * 1.4);
  const sw = c.createConicGradient(0, 0, 0);
  sw.addColorStop(0, 'rgba(255,46,91,0.28)'); sw.addColorStop(0.12, 'rgba(255,46,91,0)'); sw.addColorStop(1, 'rgba(255,46,91,0)');
  c.fillStyle = sw;
  c.beginPath(); c.arc(0, 0, 900, 0, TAU); c.fill();
  c.restore();
  for (let k = 1; k <= 3; k++) {
    c.strokeStyle = `rgba(255,90,130,${0.12 + 0.05 * k})`;
    c.lineWidth = 1.5;
    c.beginPath(); c.ellipse(cx, cy, 200 * k * 1.3, 200 * k * 0.8, 0, 0, TAU); c.stroke();
  }
  // transactions + footprints
  const dim = 1 - 0.75 * P(t, T(11, 2), 0.3);
  L.tx.forEach((p, j) => {
    if (t < p.t) return;
    const q = E.outCubic(P(t, p.t, 0.28));
    const ex = cx + (p.x - cx) * q, ey = cy + (p.y - cy) * q;
    c.save();
    c.globalAlpha = dim;
    // beam
    const g = c.createLinearGradient(cx, cy, ex, ey);
    g.addColorStop(0, 'rgba(255,46,91,0.05)'); g.addColorStop(1, 'rgba(255,90,140,0.9)');
    c.strokeStyle = g; c.lineWidth = 3;
    c.beginPath(); c.moveTo(cx, cy); c.lineTo(ex, ey); c.stroke();
    // footprints along the path
    const ang = Math.atan2(p.y - cy, p.x - cx);
    const dist = Math.hypot(p.x - cx, p.y - cy);
    c.fillStyle = 'rgba(255,80,120,0.85)';
    c.shadowColor = 'rgba(255,46,91,0.9)'; c.shadowBlur = 12;
    for (let k = 0; k < 7; k++) {
      const u = (k + 1) / 8;
      if (u > q) break;
      foot(c, cx + Math.cos(ang) * dist * u, cy + Math.sin(ang) * dist * u, ang, 10, k % 2 === 0);
    }
    c.shadowBlur = 0;
    // packet head
    if (q < 1) { c.fillStyle = '#fff'; c.beginPath(); c.arc(ex, ey, 8, 0, TAU); c.fill(); }
    c.restore();
    // card
    const cp = E.outBack(P(t, p.t + 0.2, 0.35));
    if (cp > 0) {
      c.save();
      c.globalAlpha = clamp(cp) * dim;
      c.translate(p.x, p.y);
      c.scale(cp, cp);
      darkCard(c, 0, 0, 300, 104);
      text(c, p.title, -128, -20, { size: 22, weight: 600, color: 'rgba(255,255,255,0.7)', align: 'left' });
      text(c, p.val, -128, 18, { size: 30, weight: 800, color: '#fff', align: 'left', fam: FONT });
      c.fillStyle = '#ff2e5b';
      c.beginPath(); c.arc(124, -30, 6 + 2 * Math.sin(t * 8 + j), 0, TAU); c.fill();
      text(c, 'PUBLIC', 108, 30, { size: 14, weight: 800, color: '#ff5d84', ls: 2, align: 'right' });
      c.restore();
    }
  });
  // you
  const op = E.outBack(P(t, t0, 0.5));
  if (op > 0) {
    c.save(); c.translate(cx, cy); c.scale(op, op); c.translate(-cx, -cy);
    c.globalAlpha = dim;
    for (let k = 0; k < 3; k++) ring(c, cx, cy, ((slow - t0) % 1.2) + k * 0.4 + t0, t0, 1.2, 260, '255,46,91', 3);
    orb(c, cx, cy, 70, { dark: true });
    text(c, '0x7a3f…c91e', cx, cy + 112, { size: 24, weight: 700, color: '#fff', fam: MONO });
    c.restore();
  }
  // scanner locks onto footprints
  if (t >= T(10, 2) && t < T(11, 2) + 0.3) {
    const targets = [0, 2, 5, 6];
    const k = clamp(Math.floor((t - T(10, 2)) / BEAT), 0, 3);
    const from = k === 0 ? { x: cx, y: cy } : L.tx[targets[k - 1]];
    const to = L.tx[targets[k]];
    const u = E.outExpo(P(t, T(10, 2) + k * BEAT, BEAT * 0.45));
    reticle(c, lerp(from.x, to.x, u), lerp(from.y, to.y, u), 90, t, '255,46,91', 1 - P(t, T(11, 2), 0.3));
  }
  [0, 2, 5, 6].forEach((ti, k) => {
    const p = L.tx[ti];
    c.save(); c.globalAlpha = dim;
    stamp(c, 'TRACED', p.x + 10, p.y + 4, t, T(10, 2) + k * BEAT + BEAT / 2);
    c.restore();
  });
  c.restore();
  // headline (on a dark band for legibility)
  const band = E.outCubic(P(t, T(11, 2), 0.3));
  if (band > 0) {
    const g = c.createLinearGradient(0, 300, 0, 780);
    g.addColorStop(0, 'rgba(7,6,26,0)'); g.addColorStop(0.3, `rgba(7,6,26,${0.75 * band})`);
    g.addColorStop(0.7, `rgba(7,6,26,${0.75 * band})`); g.addColorStop(1, 'rgba(7,6,26,0)');
    c.fillStyle = g; c.fillRect(0, 300, W, 480);
  }
  slam(c, 'EVERY TRANSACTION', 960, 430, { size: 128, weight: 900, color: '#fff', dark: true }, t, T(11, 2), {});
  slam(c, 'LEAVES A PUBLIC FOOTPRINT', 960, 575, { size: 96, weight: 900, gradient: linGrad(['#ff2e5b', '#ff6aa8', '#ff9a5b']) }, t, T(12), {});
  typeOn(c, 'making financial activity easier to trace than many users would prefer.', 960, 700, { size: 36, weight: 600, color: 'rgba(255,255,255,0.78)' }, t, T(12, 0.6), 1.1, { caretColor: '#ff2e5b' });
  // freeze: red heartbeat vignette
  if (freeze > 0) {
    const hb = Math.exp(-((t - T(12, 2)) % 0.9) * 5);
    const g = c.createRadialGradient(960, 540, 300, 960, 540, 1100);
    g.addColorStop(0, 'rgba(255,0,60,0)'); g.addColorStop(1, `rgba(255,0,60,${0.25 + 0.25 * hb})`);
    c.fillStyle = g; c.fillRect(0, 0, W, H);
  }
}

// 4. INFRASTRUCTURE -------------------------------------------------------
function sInfra(c, t) {
  const t0 = T(13);
  const u = P(t, t0, BAR);
  c.save();
  cam(c, 1 + E.inExpo(u) * 0.25);
  bgDark(c, t, '123,92,255', 1 + u * 2);
  warp(c, t, 0.3 + u * 1.6, 7, '160,140,255');
  c.restore();
  rise(c, 'This is where', 960, 380, { size: 50, weight: 600, color: 'rgba(255,255,255,0.75)' }, t, t0);
  scramble(c, 'PRIVACY INFRASTRUCTURE', 960, 510, { size: 120, weight: 900, gradient: linGrad(['#7ff0ff', '#a98bff', '#ff8fd0']), scol: '#7ff0ff' }, t, T(13, 1), 0.7);
  rise(c, 'becomes important.', 960, 640, { size: 56, weight: 700, color: '#fff' }, t, T(13, 2));
  // implosion right before the drop
  const im = P(t, T(13, 3), BEAT);
  if (im > 0) {
    c.save();
    c.globalCompositeOperation = 'lighter';
    const r = (1 - E.inCubic(im)) * 1200;
    c.strokeStyle = `rgba(200,190,255,${im})`;
    c.lineWidth = 6 + im * 30;
    c.beginPath(); c.arc(960, 540, r, 0, TAU); c.stroke();
    c.restore();
    c.fillStyle = `rgba(7,6,26,${E.inCubic(im) * 0.85})`;
    c.fillRect(0, 0, W, H);
  }
}

// 5. THE REVEAL -----------------------------------------------------------
function sReveal(c, t) {
  const t0 = T(14);
  c.save();
  cam(c, 1.0 + (t - t0) * 0.012);
  bgBase(c, 'rgba(110,60,255,0.5)', 960, 470, 1200);
  rays(c, 960, 470, t, { a: 0.16, n: 24, speed: 0.1 });
  gridFloor(c, t, { hy: 800, a: 0.28, sp: 0.5 });
  dust(c, t, 140, 22, 0.7);
  ring(c, 960, 470, t, t0, 1.3, 1700, '123,92,255', 16);
  for (let k = 0; k < 6; k++) ring(c, 960, 470, t, t0 + k * BEAT, 1.4, 700, k % 2 ? '11,179,207' : '255,61,154', 4);
  shards(c, 960, 470, t, t0);
  const bp = beatPulse(t, 6);
  // Startale (left)
  const lx = 600, rx = 1320, ly = 450;
  stLogo(c, lx, ly, 125, t, t0, { scale: 1 + bp * 0.03, rot: Math.sin((t - t0) * 0.6) * 0.05 });
  // Privacy Boost (right)
  const pq = E.outBack2(P(t, T(14, 2), 0.55));
  if (pq > 0) {
    pbLogo(c, rx, ly, 150, { scale: pq * (1 + bp * 0.03), rot: (1 - pq) * -2.5, glow: 1, rim: clamp(pq), needle: E.outExpo(P(t, T(14, 2) + 0.15, 0.5)), sweep: E.outCubic(P(t, T(14, 2) + 0.3, 0.6)) });
    ring(c, rx, ly, t, T(14, 2), 0.9, 420, '59,71,255', 8);
  }
  // ×
  if (t >= T(15)) {
    const xp = E.outBack2(P(t, T(15), 0.35));
    c.save(); c.translate(960, ly); c.rotate((1 - xp) * 1.6); c.scale(xp, xp);
    c.strokeStyle = C.ink; c.lineWidth = 9; c.lineCap = 'round';
    c.beginPath(); c.moveTo(-38, -38); c.lineTo(38, 38); c.moveTo(38, -38); c.lineTo(-38, 38); c.stroke();
    c.restore();
    flare(c, 960, ly, 0.6 * (1 - P(t, T(15), 0.8)));
  }
  c.restore();
  // wordmarks
  scramble(c, 'Startale App', lx, 670, { size: 64, weight: 800, color: C.ink, scol: C.violet }, t, T(15, 2), 0.6);
  if (t >= T(15, 2)) {
    const a = E.outCubic(P(t, T(15, 2), 0.5));
    c.save();
    c.translate(0, (1 - a) * 30);
    pbWord(c, rx, 670, 64, { a });
    c.restore();
  }
  pill(c, 'NEW PARTNERSHIP', 960, 170, t, T(14, 2));
  typeOn(c, 'Bringing privacy capabilities closer to users in the Startale App ecosystem', 960, 840, { size: 36, weight: 600, color: C.dim }, t, T(16), 1.2, { caretColor: C.violet });
}

// 6. SHIELD -------------------------------------------------------------------
function sShield(c, t) {
  const t0 = T(17);
  const cx = 960, cy = 520;
  c.save();
  cam(c, 1.08 - 0.08 * E.outCubic(P(t, t0, 1)) + (t - t0) * 0.01);
  bgBase(c, 'rgba(59,71,255,0.5)', cx, cy, 1100);
  dust(c, t, 120, 41, 0.6);
  const sr = 250 * E.outElastic(P(t, t0, 1.1));
  // transactions leaving through the shield
  const txs = [1, 2, 3, 5, 6, 7].map((j) => L.tx[j]);
  txs.forEach((p0, j) => {
    const tt = T(17, 2) + j * BEAT * 0.5;
    if (t < tt) return;
    const px = cx + (p0.x - cx) * 1.05, py = cy + (p0.y - 540) * 1.0;
    const q = E.outCubic(P(t, tt, 0.3));
    const ex = cx + (px - cx) * q, ey = cy + (py - cy) * q;
    c.save();
    c.strokeStyle = 'rgba(123,92,255,0.5)'; c.lineWidth = 3; c.setLineDash([4, 10]);
    c.beginPath(); c.moveTo(cx, cy); c.lineTo(ex, ey); c.stroke();
    c.setLineDash([]);
    if (q < 1) { c.fillStyle = C.violet; c.beginPath(); c.arc(ex, ey, 7, 0, TAU); c.fill(); }
    c.restore();
    const cp = E.outBack(P(t, tt + 0.2, 0.35));
    if (cp > 0) {
      c.save();
      c.translate(px, py); c.scale(cp, cp); c.globalAlpha = clamp(cp);
      panel(c, 0, 0, 290, 100, { r: 18 });
      icon(c, 'key', -105, 0, 40, C.violet, 4);
      scramble(c, 'Private transfer', -70, -18, { size: 22, weight: 700, color: C.ink, align: 'left', scol: C.violet }, t, tt + 0.2, 0.35);
      text(c, '•••• ••••', -70, 18, { size: 30, weight: 900, color: C.violet, align: 'left' });
      c.restore();
    }
  });
  shieldBubble(c, cx, cy, sr, t, 1);
  orb(c, cx, cy, 70, {});
  if (sr > 10) pbLogo(c, cx, cy - sr - 4, 34, { scale: clamp(sr / 250), rim: 1 });
  // a tracker tries, and fails
  if (t >= T(18) && t < T(19, 2)) {
    const u = E.outExpo(P(t, T(18), BEAT * 1.6));
    const rx2 = lerp(1650, cx, u), ry2 = lerp(200, cy, u);
    const fail = P(t, T(18, 2), 0.6);
    const jitter = fail > 0 && fail < 1 ? Math.sin(t * 90) * 14 * (1 - fail) : 0;
    reticle(c, rx2 + jitter, ry2, 110, t, '255,46,91', 1 - fail);
    stamp(c, 'NO PUBLIC TRACE', cx, cy + 170, t, T(18, 2), '#12b886', 0.08);
  }
  c.restore();
  const cg2 = c.createLinearGradient(0, 880, 0, 1080);
  cg2.addColorStop(0, 'rgba(247,244,255,0)'); cg2.addColorStop(0.5, 'rgba(247,244,255,0.75)'); cg2.addColorStop(1, 'rgba(247,244,255,0.75)');
  c.fillStyle = cg2; c.fillRect(0, 880, W, 200);
  rise(c, 'Privacy capabilities, closer to users.', 960, 975, { size: 50, weight: 800, color: C.ink }, t, T(17, 1), { out: T(19) - 0.2 });
  rise(c, 'A more flexible onchain experience.', 960, 975, { size: 50, weight: 800, gradient: G_BRAND }, t, T(19));
}

// 7. BIGGER PICTURE -------------------------------------------------------------
function sBigger(c, t) {
  const t0 = T(20);
  const z = lerp(2.6, 1, E.inOut(P(t, t0, BAR * 0.9)));
  c.save();
  bgBase(c, 'rgba(123,92,255,0.4)');
  cam(c, z, 960, 560);
  // network of bubbles
  const nodes = [];
  for (let i = 0; i < 26; i++) {
    const x = i === 0 ? 960 : 160 + rnd(i, 5) * 1600, y = i === 0 ? 560 : 140 + rnd(i, 6) * 820;
    nodes.push({ x: x + Math.sin(t * 0.5 + i) * 12, y: y + Math.cos(t * 0.4 + i) * 10, r: i === 0 ? 70 : 22 + rnd(i, 7) * 36 });
  }
  c.strokeStyle = 'rgba(123,92,255,0.25)';
  c.lineWidth = 2;
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const d = Math.hypot(nodes[i].x - nodes[j].x, nodes[i].y - nodes[j].y);
      if (d < 330) {
        const reveal = P(t, t0 + 0.3 + (i + j) * 0.02, 0.4);
        if (reveal <= 0) continue;
        c.globalAlpha = reveal * (1 - d / 330);
        c.beginPath(); c.moveTo(nodes[i].x, nodes[i].y); c.lineTo(nodes[j].x, nodes[j].y); c.stroke();
      }
    }
  }
  c.globalAlpha = 1;
  nodes.forEach((n, i) => {
    const a = i === 0 ? 1 : E.outBack(P(t, t0 + 0.2 + i * 0.04, 0.4));
    if (a <= 0) return;
    bubble(c, n.x, n.y, n.r * a, t, 1);
    if (t > T(21) && i > 0 && i % 2 === 0) {
      const ua = E.outBack(P(t, T(21) + i * 0.03, 0.3));
      if (ua > 0) icon(c, 'user', n.x, n.y, n.r * 0.9 * ua, C.violet, 3);
    }
  });
  c.globalAlpha = 1 - P(t, t0 + BAR * 0.5, 0.4);
  if (c.globalAlpha > 0) orb(c, 960, 560, 46, {});
  c.restore();
  // veil for legibility
  c.fillStyle = 'rgba(247,244,255,0.35)';
  c.fillRect(0, 0, W, H);
  scramble(c, 'THE BIGGER PICTURE', 960, 400, { size: 130, weight: 900, gradient: G_BRAND }, t, t0, 0.7, { out: T(21) - 0.2 });
  typeOn(c, 'goes beyond a single partnership.', 960, 530, { size: 52, weight: 700, color: C.ink }, t, T(20, 2), 0.9, { out: T(21) - 0.2, caretColor: C.violet });
  rise(c, 'For Web3 to reach a broader audience,', 960, 470, { size: 64, weight: 800, color: C.ink }, t, T(21));
  rise(c, 'users need more than…', 960, 570, { size: 56, weight: 700, gradient: G_COOL }, t, T(21, 2));
}

// 8. WHAT USERS NEED ----------------------------------------------------------
function needCard(c, x, y, w, h, ic, label, t, t0, dim) {
  const p = E.outBack(P(t, t0, 0.45));
  if (p <= 0) return;
  c.save();
  c.translate(x, y + (1 - p) * 80);
  c.globalAlpha = clamp(p) * dim;
  panel(c, 0, 0, w, h, { r: 26 });
  c.save(); c.translate(-w / 2 + 70, 0);
  const g = c.createLinearGradient(-36, -36, 36, 36); g.addColorStop(0, '#09a9c4'); g.addColorStop(1, C.violet);
  c.fillStyle = g; rr(c, -40, -40, 80, 80, 22); c.fill();
  icon(c, ic, 0, 0, 44, '#fff', 4);
  c.restore();
  text(c, label, -w / 2 + 135, 0, { size: 40, weight: 800, color: C.ink, align: 'left' });
  // check
  const cp = E.outBack2(P(t, t0 + 0.25, 0.3));
  if (cp > 0) {
    c.save(); c.translate(w / 2 - 55, 0); c.scale(cp, cp);
    c.fillStyle = C.green; c.beginPath(); c.arc(0, 0, 26, 0, TAU); c.fill();
    icon(c, 'check', 0, 0, 30, '#fff', 5);
    c.restore();
  }
  c.restore();
}
function sNeeds(c, t) {
  const t0 = T(22);
  c.save();
  cam(c, 1 + (t - t0) * 0.012);
  bgBase(c, 'rgba(18,184,134,0.4)');
  gridFloor(c, t, { hy: 860, a: 0.22, sp: 1 });
  dust(c, t, 90, 61, 0.6);
  c.restore();
  rise(c, 'More than…', 960, 170, { size: 46, weight: 700, color: C.dim }, t, t0);
  const dim = 1 - 0.5 * E.outCubic(P(t, T(23), 0.3));
  needCard(c, 540, 330, 680, 140, 'bolt', 'Fast transactions', t, T(22, 1), dim);
  needCard(c, 1380, 330, 680, 140, 'apps', 'Easy-to-use apps', t, T(22, 2), dim);
  // the big one
  const p = E.outExpo(P(t, T(23), 0.4));
  if (p > 0) {
    c.save();
    c.translate(960, 680);
    c.scale(lerp(1.4, 1, p), lerp(1.4, 1, p));
    c.globalAlpha = clamp(p * 2);
    panel(c, 0, 0, 1460, 330, { r: 36, border: 'rgba(123,92,255,0.55)', lw: 4 });
    const g = c.createLinearGradient(-730, 0, 730, 0);
    g.addColorStop(0, 'rgba(11,179,207,0.10)'); g.addColorStop(1, 'rgba(255,61,154,0.10)');
    c.fillStyle = g; rr(c, -730, -165, 1460, 330, 36); c.fill();
    c.restore();
    pbLogo(c, 330, 680, 90 * E.outBack(P(t, T(23), 0.5)), { glow: 0.6, rim: 1 });
    slam(c, 'CONTROL', 1030, 625, { size: 150, weight: 900, gradient: G_BRAND }, t, T(23), {});
    typeOn(c, 'over how their financial information is exposed', 1030, 745, { size: 38, weight: 700, color: C.ink }, t, T(23, 1), 1.1, { caretColor: C.violet });
  }
}

// 9. BALANCE -------------------------------------------------------------------
function beamAngle(t) {
  let a = 0;
  if (t > T(24, 2)) a = lerp(0, -0.2, E.outElastic(P(t, T(24, 2), 1.1)));
  if (t > T(25, 2)) a = lerp(-0.2, 0.16, E.outElastic(P(t, T(25, 2), 1.1)));
  if (t > T(26)) { const u = t - T(26); a = 0.16 * Math.cos(u * 8) * Math.exp(-u * 1.6); }
  if (t > T(27)) a *= 1 - E.outCubic(P(t, T(27), 0.35));
  return a;
}
function sBalance(c, t) {
  const t0 = T(24);
  const balanced = E.outCubic(P(t, T(27), 0.4));
  c.save();
  cam(c, 1.04 - 0.04 * E.outCubic(P(t, t0, 1)) + balanced * 0.03);
  bgBase(c, 'rgba(123,92,255,0.45)', 960, 470, 1100);
  if (balanced > 0) rays(c, 960, 470, t, { a: 0.14 * balanced, n: 24, speed: 0.1 });
  gridFloor(c, t, { hy: 900, a: 0.22, sp: 0.5 });
  dust(c, t, 100, 71, 0.6);
  const px = 960, py = 470;
  const app = E.outBack(P(t, t0, 0.5));
  c.save();
  c.translate(px, py + (1 - app) * 400);
  c.globalAlpha = clamp(app);
  // post + base
  const pg = c.createLinearGradient(0, 0, 0, 420);
  pg.addColorStop(0, '#d9ccff'); pg.addColorStop(1, '#a58bff');
  c.fillStyle = pg;
  rr(c, -14, 0, 28, 400, 10); c.fill();
  rr(c, -170, 390, 340, 36, 18); c.fill();
  const a = beamAngle(t);
  c.rotate(a);
  const bl = 520;
  const bg2 = c.createLinearGradient(-bl, 0, bl, 0);
  bg2.addColorStop(0, '#09a9c4'); bg2.addColorStop(0.5, C.violet); bg2.addColorStop(1, C.pink);
  c.fillStyle = bg2;
  rr(c, -bl, -14, bl * 2, 28, 14); c.fill();
  c.fillStyle = '#fff';
  c.beginPath(); c.arc(0, 0, 26, 0, TAU); c.fill();
  c.strokeStyle = C.violet; c.lineWidth = 6; c.stroke();
  // pans hang straight down (counter-rotate)
  [[-bl + 40, 'L'], [bl - 40, 'R']].forEach(([ex, side]) => {
    c.save();
    c.translate(ex, 0);
    c.rotate(-a);
    c.strokeStyle = 'rgba(123,92,255,0.6)'; c.lineWidth = 3;
    c.beginPath(); c.moveTo(0, 0); c.lineTo(-120, 200); c.moveTo(0, 0); c.lineTo(120, 200); c.stroke();
    c.fillStyle = 'rgba(255,255,255,0.92)';
    c.beginPath(); c.ellipse(0, 205, 150, 24, 0, 0, TAU); c.fill();
    c.strokeStyle = 'rgba(123,92,255,0.5)'; c.stroke();
    if (side === 'L') {
      const q = E.outBack2(P(t, T(24, 2) - 0.25, 0.3));
      if (q > 0) {
        c.save(); c.translate(0, lerp(-500, 0, clamp(q))); block(c, 0, 125, 120, 7, 1); c.restore();
      }
    } else {
      const q = E.outBack2(P(t, T(25, 2) - 0.25, 0.3));
      if (q > 0) {
        c.save(); c.translate(0, lerp(-500, 0, clamp(q)));
        shieldBubble(c, 0, 115, 84, t, 1);
        pbLogo(c, 0, 115, 60, {});
        c.restore();
      }
    }
    c.restore();
  });
  c.restore();
  // pan labels (follow the beam ends)
  const ends = [-1, 1].map((sgn) => ({ x: px + Math.cos(a) * (bl - 40) * sgn, y: py + Math.sin(a) * (bl - 40) * sgn + 300 }));
  if (t >= T(24, 2)) {
    const q = E.outCubic(P(t, T(24, 2), 0.4));
    c.save(); c.globalAlpha = q;
    text(c, 'TRANSPARENCY', ends[0].x, ends[0].y, { size: 52, weight: 900, color: '#0a8fb0', ls: 2 });
    text(c, 'is essential for trust', ends[0].x, ends[0].y + 52, { size: 32, weight: 600, color: C.dim });
    c.restore();
  }
  if (t >= T(25, 2)) {
    const q = E.outCubic(P(t, T(25, 2), 0.4));
    c.save(); c.globalAlpha = q;
    text(c, 'PRIVACY', ends[1].x, ends[1].y, { size: 52, weight: 900, color: '#d6247c', ls: 2 });
    text(c, 'is essential for protecting users', ends[1].x, ends[1].y + 52, { size: 32, weight: 600, color: C.dim });
    c.restore();
  }
  c.restore();
  scramble(c, 'THE REAL CHALLENGE', 960, 150, { size: 64, weight: 900, color: C.ink, scol: C.pink }, t, T(26, 2), 0.6, { out: T(27) - 0.12 });
  if (t >= T(27)) {
    rise(c, 'is finding the right', 960, 110, { size: 40, weight: 700, color: C.dim }, t, T(27) + 0.05);
    slam(c, 'BALANCE', 960, 205, { size: 140, weight: 900, gradient: G_BRAND }, t, T(27), {});
    flare(c, 960, 470, 0.5 * (1 - P(t, T(27), 1.2)));
  }
}

// 10. BARRIER ----------------------------------------------------------------------
const WALL = { x: 1230, y: 560, cols: 7, rows: 9, bw: 128, bh: 64 };
function sBarrier(c, t) {
  const t0 = T(28), hitT = T(29);
  c.save();
  const shakeIn = t < hitT ? E.inExpo(P(t, T(28, 2), BEAT * 2)) * 6 * Math.sin(t * 70) : 0;
  cam(c, 1 + E.inExpo(P(t, t0, BAR)) * 0.06 - E.outCubic(P(t, hitT, 1)) * 0.04, 960, 540, shakeIn, 0);
  bgBase(c, 'rgba(255,61,154,0.4)');
  gridFloor(c, t, { hy: 860, a: 0.25, sp: t < hitT ? 0.6 : 2.4 });
  dust(c, t, 100, 81, 0.6);
  // light through the hole after impact
  if (t >= hitT) {
    const a = E.outCubic(P(t, hitT, 0.6));
    rays(c, WALL.x, WALL.y, t, { a: 0.2 * a, n: 26, speed: 0.15, col: '255,255,255' });
  }
  // wall
  const ww = WALL.cols * WALL.bw, wh = WALL.rows * WALL.bh;
  for (let r = 0; r < WALL.rows; r++) {
    for (let k = 0; k < WALL.cols; k++) {
      const idx = r * WALL.cols + k;
      const off = r % 2 ? WALL.bw / 2 : 0;
      let x = WALL.x - ww / 2 + k * WALL.bw + off - WALL.bw / 4, y = WALL.y + wh / 2 - (r + 1) * WALL.bh;
      // build up from the floor
      const rowT = t0 + (r < 5 ? 0 : 0.47) + r * 0.04;
      const up = E.outBack(P(t, rowT, 0.35));
      if (up <= 0) continue;
      y += (1 - up) * 500;
      let rot = 0, a = 1;
      if (t >= hitT) {
        const dt = t - hitT;
        const dx = x + WALL.bw / 2 - (WALL.x - ww / 2), dy = y + WALL.bh / 2 - WALL.y;
        const near = Math.exp(-Math.hypot(dx - 60, dy) / 380);
        const vx = (500 + rnd(idx, 1) * 1100) * (0.4 + near), vy = (rnd(idx, 2) - 0.6) * 1200 * (0.4 + near);
        x += vx * dt; y += vy * dt + 900 * dt * dt;
        rot = (rnd(idx, 3) - 0.5) * 9 * dt;
        a = 1 - P(dt, 0.6, 0.6);
      }
      if (a <= 0) continue;
      c.save();
      c.translate(x + WALL.bw / 2, y + WALL.bh / 2);
      c.rotate(rot);
      c.globalAlpha = a;
      const tint = 0.85 + rnd(idx, 9) * 0.15;
      c.fillStyle = `rgb(${Math.round(70 * tint)},${Math.round(56 * tint)},${Math.round(120 * tint)})`;
      rr(c, -WALL.bw / 2 + 3, -WALL.bh / 2 + 3, WALL.bw - 6, WALL.bh - 6, 6); c.fill();
      c.fillStyle = 'rgba(255,255,255,0.08)';
      c.fillRect(-WALL.bw / 2 + 6, -WALL.bh / 2 + 6, WALL.bw - 12, 8);
      c.restore();
    }
  }
  // wall label
  if (t < hitT + 0.05) {
    const a = E.outCubic(P(t, T(28, 1), 0.3));
    c.save(); c.globalAlpha = a;
    text(c, 'BARRIER TO', WALL.x, WALL.y - 50, { size: 52, weight: 900, color: 'rgba(255,255,255,0.85)', ls: 6 });
    text(c, 'MAINSTREAM ADOPTION', WALL.x, WALL.y + 20, { size: 60, weight: 900, color: '#fff', ls: 2 });
    c.restore();
  }
  shards(c, WALL.x - ww / 2, WALL.y, t, hitT, 40, 9, 1000);
  // the privacy orb charges, then strikes
  const charge = P(t, T(28), BAR);
  let ox = 380, oy = 560;
  if (t >= T(28, 3)) ox = lerp(380, WALL.x - ww / 2, E.inExpo(P(t, T(28, 3), BEAT)));
  if (t >= hitT) { ox = lerp(WALL.x - ww / 2, WALL.x + 240, E.outCubic(P(t, hitT, 0.8))); }
  const or = 80 + charge * 20;
  if (t < T(29, 2)) {
    if (ox > 400) {
      // motion streak
      const g = c.createLinearGradient(ox - 500, 0, ox, 0);
      g.addColorStop(0, 'rgba(123,92,255,0)'); g.addColorStop(1, 'rgba(123,92,255,0.6)');
      c.fillStyle = g; rr(c, ox - 500, oy - or * 0.7, 500, or * 1.4, or * 0.7); c.fill();
    }
    shieldBubble(c, ox, oy, or * 1.3, t, 1);
    pbLogo(c, ox, oy, or * 0.85, { glow: 0.5 + charge, rim: 1 });
    for (let k = 0; k < 3; k++) ring(c, ox, oy, t, T(28) + k * BEAT, 0.9, 260, '123,92,255', 4);
  }
  c.restore();
  if (t < hitT) {
    rise(c, 'Accessible privacy', 380, 330, { size: 46, weight: 800, color: C.ink }, t, T(28) + 0.05);
    rise(c, '+ blockchain verification', 380, 390, { size: 38, weight: 700, gradient: G_COOL }, t, T(28, 1));
  }
  if (t >= T(29, 2)) {
    c.save();
    c.fillStyle = `rgba(247,244,255,${0.45 * E.outCubic(P(t, T(29, 2), 0.3))})`;
    c.fillRect(0, 0, W, H);
    c.restore();
    rise(c, 'removing another major barrier to', 960, 420, { size: 48, weight: 700, color: C.dim }, t, T(29, 2));
    slam(c, 'MAINSTREAM ADOPTION', 960, 540, { size: 140, weight: 900, gradient: G_BRAND }, t, T(29, 2), {});
  }
}

// 11. OUTRO ------------------------------------------------------------------------
function sOutro(c, t) {
  const t0 = T(30), lock = T(31);
  c.save();
  cam(c, 1 + (t - t0) * 0.01);
  bgBase(c, 'rgba(110,60,255,0.5)', 960, 450, 1100);
  if (t >= lock) {
    rays(c, 960, 450, t, { a: 0.14, n: 22, speed: 0.12 });
    ring(c, 960, 450, t, lock, 1.2, 1600, '123,92,255', 14);
    for (let k = 0; k < 6; k++) ring(c, 960, 450, t, lock + k * BEAT * 2, 1.4, 650, k % 2 ? '11,179,207' : '255,61,154', 4);
    shards(c, 960, 450, t, lock, 36, 13);
  }
  gridFloor(c, t, { hy: 860, a: 0.28, sp: 0.5 });
  dust(c, t, 120, 22, 0.6);
  c.restore();
  rise(c, 'This is why privacy infrastructure', 960, 470, { size: 64, weight: 800, color: C.ink }, t, t0, { out: lock - 0.15 });
  rise(c, 'deserves more attention.', 960, 580, { size: 84, weight: 900, gradient: G_BRAND }, t, T(30, 1.5), { out: lock - 0.15 });
  if (t >= lock) {
    const bp = beatPulse(t, 6) * (t < T(33) ? 1 : 0);
    const lx = 670, rx = 1250, ly = 420;
    stLogo(c, lx, ly, 105, t, lock, { scale: 1 + bp * 0.03 });
    const pq = E.outBack2(P(t, lock + 0.15, 0.5));
    if (pq > 0) pbLogo(c, rx, ly, 125, { scale: pq * (1 + bp * 0.03), rot: (1 - pq) * -2, glow: 0.8, rim: 1 });
    const xp = E.outBack2(P(t, lock + 0.25, 0.35));
    if (xp > 0) {
      c.save(); c.translate(960, ly); c.scale(xp, xp); c.rotate((1 - xp) * 1.5);
      c.strokeStyle = C.ink; c.lineWidth = 8; c.lineCap = 'round';
      c.beginPath(); c.moveTo(-32, -32); c.lineTo(32, 32); c.moveTo(32, -32); c.lineTo(-32, 32); c.stroke();
      c.restore();
    }
    scramble(c, 'Startale App', lx, 610, { size: 58, weight: 800, color: C.ink, scol: C.violet }, t, T(31, 2), 0.5);
    if (t >= T(31, 2)) {
      const a = E.outCubic(P(t, T(31, 2), 0.5));
      c.save(); c.translate(0, (1 - a) * 30); pbWord(c, rx, 610, 58, { a }); c.restore();
    }
    scramble(c, 'Transparency for trust. Privacy for protection.', 960, 800, { size: 44, weight: 700, gradient: G_BRAND, scol: C.violet }, t, T(32), 0.6);
  }
  const f = E.inOut(P(t, T(33), DURATION - T(33) - 0.05));
  if (f > 0) { c.fillStyle = `rgba(247,244,255,${f})`; c.fillRect(0, 0, W, H); }
}

const SCENES = [
  [0, T(4), sOpen],
  [T(4), T(7), sTransparency],
  [T(7), T(8), sTurn],
  [T(8), T(13), sExposure],
  [T(13), T(14), sInfra],
  [T(14), T(17), sReveal],
  [T(17), T(20), sShield],
  [T(20), T(22), sBigger],
  [T(22), T(24), sNeeds],
  [T(24), T(28), sBalance],
  [T(28), T(30), sBarrier],
  [T(30), 1e9, sOutro],
];

// letterbox amount (px per bar) — cinematic 2.39:1 in the quiet/dark acts, open on the drops
const LB_FULL = Math.round((H - W / 2.39) / 2);
const LB_KEYS = [
  [0, H / 2], [1.4, LB_FULL], [T(4) - 0.05, LB_FULL], [T(4) + 0.25, 0],
  [T(7, 2), 0], [T(8), LB_FULL], [T(14) - 0.05, LB_FULL], [T(14) + 0.3, 0],
  [T(30), 0], [T(31) - 0.05, 0], [T(31) + 0.4, LB_FULL * 0.6], [T(33), LB_FULL * 0.6], [DURATION, H / 2],
];
function letterbox(t) {
  for (let i = 0; i < LB_KEYS.length - 1; i++) {
    const [a, va] = LB_KEYS[i], [b, vb] = LB_KEYS[i + 1];
    if (t >= a && t <= b) return lerp(va, vb, E.inOut((t - a) / Math.max(1e-6, b - a)));
  }
  return 0;
}
