// Motion-graphics moments: the GOAL takeover and animated counters.
const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

// Full-screen goal celebration in the scoring team's colours.
export function celebrate({ title = 'گـــل!', sub = '', c1 = '#c6ff3d', c2 = '#ff3358' } = {}) {
  if (reduced() || document.hidden) return;
  document.querySelector('.goal-fx')?.remove();
  const el = document.createElement('div');
  el.className = 'goal-fx';
  el.style.cssText = `--gc1:${c1};--gc2:${c2}`;
  el.innerHTML = `<div class="flash"></div><div class="bar"></div><div class="bar b2"></div><canvas></canvas><div class="word">${title}</div>${sub ? `<div class="sub"></div>` : ''}`;
  if (sub) el.querySelector('.sub').textContent = sub;
  document.body.append(el);
  confetti(el.querySelector('canvas'), [c1, c2, '#ffffff']);
  navigator.vibrate?.([80, 40, 160]);
  setTimeout(() => el.remove(), 2800);
}

function confetti(canvas, colors) {
  const dpr = Math.min(devicePixelRatio || 1, 2), W = innerWidth, H = innerHeight;
  canvas.width = W * dpr; canvas.height = H * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
  const parts = Array.from({ length: 140 }, (_, i) => {
    const fromLeft = i % 2 === 0;
    return { x: fromLeft ? -10 : W + 10, y: H * (0.55 + Math.random() * 0.4), vx: (fromLeft ? 1 : -1) * (6 + Math.random() * 9), vy: -(10 + Math.random() * 12),
      r: 4 + Math.random() * 6, rot: Math.random() * 6, vr: (Math.random() - 0.5) * 0.4, c: colors[i % colors.length], shape: i % 3 };
  });
  const t0 = performance.now();
  (function frame(t) {
    const dt = Math.min(2, (t - (frame.last || t)) / 16.7); frame.last = t;
    ctx.clearRect(0, 0, W, H);
    for (const p of parts) {
      p.vy += 0.35 * dt; p.vx *= 0.99; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt;
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot); ctx.fillStyle = p.c;
      if (p.shape === 0) ctx.fillRect(-p.r, -p.r / 3, p.r * 2, p.r / 1.5);
      else if (p.shape === 1) { ctx.beginPath(); ctx.arc(0, 0, p.r / 1.6, 0, 7); ctx.fill(); }
      else { ctx.beginPath(); ctx.moveTo(0, -p.r); ctx.lineTo(p.r, p.r); ctx.lineTo(-p.r, p.r); ctx.fill(); }
      ctx.restore();
    }
    if (t - t0 < 2700 && canvas.isConnected) requestAnimationFrame(frame);
  })(t0);
}

// Count numbers up when they scroll into view: <b data-count="23">
const io = 'IntersectionObserver' in window ? new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    io.unobserve(e.target);
    const el = e.target, to = +el.dataset.count, dec = +(el.dataset.dec || 0), t0 = performance.now(), dur = 1100;
    (function step(t) {
      const p = Math.min(1, (t - t0) / dur), v = to * (1 - Math.pow(1 - p, 3));
      el.textContent = v.toLocaleString('fa-IR', { minimumFractionDigits: dec, maximumFractionDigits: dec, useGrouping: !('plain' in el.dataset) });
      if (p < 1) requestAnimationFrame(step);
    })(t0);
  }
}, { threshold: 0.4 }) : null;
export function animateCounts(root) {
  if (!io || reduced()) return;
  root.querySelectorAll('[data-count]').forEach((el) => io.observe(el));
}
