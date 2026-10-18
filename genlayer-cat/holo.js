// Holographic UI panels (canvas-textured planes) and the 2D artwork drawn on them.
import * as THREE from 'three';
import { logoPath } from './character.js';

export const THEME = { pink: '#ff87ff', violet: '#dc00ff', cyan: '#35e1f4', ink: '#12051f', white: '#fbf4ff' };

export function rrect(g, x, y, w, h, r) {
  g.beginPath();
  g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}

// A plane with a canvas texture. draw(g, w, h, state) repaints only when state.key changes.
export class Holo {
  constructor(w, h, px, draw, { scale = 1.25, blending = THREE.NormalBlending, billboard = false } = {}) {
    this.canvas = document.createElement('canvas');
    this.canvas.width = px; this.canvas.height = Math.round(px * h / w);
    this.g = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.tex.anisotropy = 8;
    this.mat = new THREE.MeshBasicMaterial({ map: this.tex, transparent: true, depthWrite: false, blending, side: THREE.DoubleSide, toneMapped: true, fog: false });
    this.mat.color.setScalar(scale);
    this.mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), this.mat);
    this.mesh.renderOrder = 10;
    this.draw = draw;
    this.key = null;
    this.billboard = billboard;
  }
  update(state = {}) {
    const key = state.key ?? JSON.stringify(state);
    if (key !== this.key) {
      this.key = key;
      this.g.setTransform(1, 0, 0, 1, 0, 0);
      this.g.clearRect(0, 0, this.canvas.width, this.canvas.height);
      this.draw(this.g, this.canvas.width, this.canvas.height, state);
      this.tex.needsUpdate = true;
    }
  }
  // visibility 0..1: opacity + a vertical "unfold" with a little flicker while appearing
  show(v, flick = 0) {
    this.mesh.visible = v > 0.001;
    const f = v < 1 ? 0.75 + 0.25 * Math.sin(flick * 90) : 1;
    this.mat.opacity = Math.min(1, v * 1.4) * f;
    this.mesh.scale.y = 0.08 + 0.92 * Math.min(1, v * 1.15);
  }
}

// ---------------------------------------------------------------- panel artwork
function panelBase(g, W, H, { col = THEME.pink, col2 = THEME.violet, fill = 'rgba(22,6,38,0.78)', r = 34, lw = 6 } = {}) {
  const m = 18;
  g.save();
  rrect(g, m, m, W - 2 * m, H - 2 * m, r);
  g.fillStyle = fill; g.fill();
  const gr = g.createLinearGradient(0, 0, W, H);
  gr.addColorStop(0, col); gr.addColorStop(1, col2);
  g.shadowColor = col; g.shadowBlur = 22;
  g.lineWidth = lw; g.strokeStyle = gr; g.stroke();
  g.restore();
  // scanlines
  g.save();
  rrect(g, m, m, W - 2 * m, H - 2 * m, r); g.clip();
  g.fillStyle = 'rgba(255,255,255,0.035)';
  for (let y = m; y < H; y += 6) g.fillRect(0, y, W, 2);
  g.restore();
}

export function cloudIcon(g, x, y, s, { rain = true, col = '#fff', drop = THEME.cyan } = {}) {
  g.save(); g.translate(x, y); g.scale(s, s);
  g.fillStyle = col;
  g.beginPath();
  g.arc(-26, 6, 24, 0, Math.PI * 2); g.arc(4, -10, 32, 0, Math.PI * 2); g.arc(34, 8, 22, 0, Math.PI * 2);
  g.fill();
  g.fillRect(-26, 6, 60, 24);
  if (rain) {
    g.strokeStyle = drop; g.lineWidth = 7; g.lineCap = 'round';
    for (const dx of [-22, 2, 26]) { g.beginPath(); g.moveTo(dx, 44); g.lineTo(dx - 7, 64); g.stroke(); }
  }
  g.restore();
}
export function sunIcon(g, x, y, s, col = '#ffd36b') {
  g.save(); g.translate(x, y); g.scale(s, s);
  g.fillStyle = col; g.strokeStyle = col; g.lineWidth = 7; g.lineCap = 'round';
  g.beginPath(); g.arc(0, 0, 22, 0, Math.PI * 2); g.fill();
  for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; g.beginPath(); g.moveTo(Math.cos(a) * 32, Math.sin(a) * 32); g.lineTo(Math.cos(a) * 44, Math.sin(a) * 44); g.stroke(); }
  g.restore();
}
function avatar(g, x, y, r, letter, c1, c2) {
  const gr = g.createLinearGradient(x - r, y - r, x + r, y + r);
  gr.addColorStop(0, c1); gr.addColorStop(1, c2);
  g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill();
  g.fillStyle = '#fff'; g.font = `900 ${r * 1.05}px Inter`; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(letter, x, y + r * 0.05);
}
function wrap(g, text, x, y, maxW, lh) {
  const words = text.split(' ');
  let line = '', yy = y;
  for (const w of words) {
    const t = line ? line + ' ' + w : w;
    if (g.measureText(t).width > maxW && line) { g.fillText(line, x, yy); line = w; yy += lh; } else line = t;
  }
  g.fillText(line, x, yy);
}

// Alice / Bob bet cards. state: { who, msg, chip, chipCol, icon, mood }
export function drawBetCard(g, W, H, st) {
  const pink = st.who === 'ALICE';
  const c1 = pink ? THEME.pink : THEME.violet, c2 = pink ? '#ff5ad1' : '#8f3bff';
  panelBase(g, W, H, { col: c1, col2: c2 });
  avatar(g, 100, 112, 52, st.who[0], c1, c2);
  g.textAlign = 'left'; g.textBaseline = 'alphabetic';
  g.fillStyle = c1; g.font = '800 30px Inter';
  g.fillText(st.who, 172, 100);
  g.fillStyle = 'rgba(255,255,255,0.55)'; g.font = '600 22px Inter';
  g.fillText('@' + st.who.toLowerCase() + ' · just now', 172, 134);
  g.fillStyle = '#fff'; g.font = '800 44px Inter';
  wrap(g, st.msg, 60, 214, W - 260, 52);
  if (st.icon === 'rain') cloudIcon(g, W - 110, 126, 1.05);
  else sunIcon(g, W - 110, 120, 1.25);
  // chip
  g.font = '800 24px Inter';
  const cw = g.measureText(st.chip).width + 44;
  rrect(g, 60, H - 92, cw, 48, 24);
  g.fillStyle = st.chipCol || 'rgba(255,255,255,0.12)'; g.fill();
  g.fillStyle = st.chipText || '#fff'; g.textBaseline = 'middle';
  g.fillText(st.chip, 82, H - 67);
}

export function drawCoinFace(g, W) {
  const c = W / 2;
  const gr = g.createRadialGradient(c * 0.8, c * 0.7, 10, c, c, c);
  gr.addColorStop(0, '#ff9cff'); gr.addColorStop(0.55, '#d228e9'); gr.addColorStop(1, '#7a1fa8');
  g.fillStyle = gr; g.fillRect(0, 0, W, W);
  g.strokeStyle = 'rgba(255,255,255,0.75)'; g.lineWidth = W * 0.035;
  g.beginPath(); g.arc(c, c, c * 0.84, 0, Math.PI * 2); g.stroke();
  g.fillStyle = '#fff';
  logoPath(g, c, W * 0.24, W * 0.5, W * 0.47);
  g.fill('nonzero');
}

export function drawTag(g, W, H, st) {
  panelBase(g, W, H, { col: st.col || THEME.pink, col2: st.col2 || THEME.violet, r: H / 2 - 18, lw: 5 });
  g.fillStyle = '#fff'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.font = `800 ${st.size || 40}px Inter`;
  g.fillText(st.text, W / 2, H / 2 + 2);
}

const CODE = [
  [['k', 'class '], ['t', 'WeatherBet'], ['p', '(Contract):']],
  [],
  [['p', '    '], ['k', 'def '], ['f', 'resolve'], ['p', '(self):']],
  [['p', '        page = '], ['f', 'web.read'], ['p', '('], ['s', '"weather.example/paris"'], ['p', ')']],
  [['p', '        answer = '], ['f', 'ai.ask'], ['p', '(']],
  [['s', '            "Did it rain in Paris on Friday?"'], ['p', ', page)']],
  [['p', '        '], ['k', 'return '], ['p', 'answer  '], ['c', '# validators re-check']],
];
const CODE_COL = { k: '#ff87ff', t: '#7de9ff', f: '#c99bff', s: '#ffe08a', p: '#efe6ff', c: '#8d7aa6' };
export const CODE_CHARS = CODE.reduce((n, l) => n + l.reduce((m, [, s]) => m + s.length, 0), 0);

// state: { chars, status, statusCol, cursor }
export function drawContract(g, W, H, st) {
  panelBase(g, W, H, { col: THEME.pink, col2: THEME.violet, r: 30 });
  // title bar
  g.fillStyle = 'rgba(255,255,255,0.06)';
  g.fillRect(24, 24, W - 48, 76);
  ['#ff5f8f', '#ffd36b', '#7dffb2'].forEach((c, i) => { g.fillStyle = c; g.beginPath(); g.arc(70 + i * 34, 62, 11, 0, Math.PI * 2); g.fill(); });
  g.fillStyle = 'rgba(255,255,255,0.85)'; g.font = '700 28px Inter'; g.textAlign = 'left'; g.textBaseline = 'middle';
  g.fillText('weather_bet.py', 190, 63);
  g.fillStyle = THEME.pink; g.font = '800 22px Inter'; g.textAlign = 'right';
  g.fillText('INTELLIGENT CONTRACT', W - 56, 63);
  // code
  g.textAlign = 'left'; g.textBaseline = 'alphabetic';
  g.font = '30px "DejaVu Sans Mono"';
  const cw = g.measureText('M').width;
  let left = st.chars, cx = 0, cy = 0;
  CODE.forEach((line, li) => {
    const y = 160 + li * 52;
    g.fillStyle = 'rgba(255,255,255,0.25)';
    g.fillText(String(li + 1).padStart(2, ' '), 44, y);
    let x = 120;
    for (const [k, s] of line) {
      const n = Math.max(0, Math.min(s.length, left));
      if (n > 0) { g.fillStyle = CODE_COL[k]; g.fillText(s.slice(0, n), x, y); }
      if (left > 0) { cx = x + n * cw; cy = y; }
      x += s.length * cw;
      left -= s.length;
    }
    if (line.length === 0 && left > 0) { cx = 120; cy = y; }
  });
  if (st.cursor) { g.fillStyle = THEME.pink; g.fillRect(cx + 2, cy - 26, cw * 0.9, 32); }
  // status
  if (st.status) {
    g.fillStyle = 'rgba(255,255,255,0.07)';
    rrect(g, 44, H - 108, W - 88, 66, 18); g.fill();
    g.fillStyle = st.statusCol || THEME.cyan;
    g.beginPath(); g.arc(84, H - 75, 10, 0, Math.PI * 2); g.fill();
    g.font = '700 30px Inter'; g.textBaseline = 'middle';
    g.fillText(st.status, 110, H - 74);
  }
}

// mini browser windows. state.kind: 0 weather, 1 news, 2 chart
const URLS = ['weather.example/paris', 'news.example/paris-weekend', 'meteo.example/history'];
export function drawWeb(g, W, H, st) {
  panelBase(g, W, H, { col: THEME.cyan, col2: THEME.violet, r: 26, fill: 'rgba(10,8,30,0.82)' });
  g.fillStyle = 'rgba(255,255,255,0.07)'; g.fillRect(22, 22, W - 44, 62);
  ['#ff5f8f', '#ffd36b', '#7dffb2'].forEach((c, i) => { g.fillStyle = c; g.beginPath(); g.arc(56 + i * 26, 53, 8, 0, Math.PI * 2); g.fill(); });
  rrect(g, 150, 34, W - 190, 38, 19); g.fillStyle = 'rgba(255,255,255,0.1)'; g.fill();
  g.fillStyle = 'rgba(255,255,255,0.8)'; g.font = '600 21px Inter'; g.textAlign = 'left'; g.textBaseline = 'middle';
  g.fillText(URLS[st.kind], 170, 54);
  g.textBaseline = 'alphabetic';
  if (st.kind === 0) {
    cloudIcon(g, 140, 190, 1.25);
    g.fillStyle = '#fff'; g.font = '800 40px Inter'; g.fillText('Paris · Fri', 250, 180);
    g.fillStyle = THEME.cyan; g.font = '800 54px Inter'; g.fillText('Rain · 4 mm', 250, 244);
    g.fillStyle = 'rgba(255,255,255,0.5)'; g.font = '600 22px Inter'; g.fillText('Observed 06:00 – 18:00', 250, 290);
  } else if (st.kind === 1) {
    g.fillStyle = '#fff'; g.font = '800 38px Inter'; g.fillText('A wet Friday in Paris', 52, 150);
    g.fillStyle = 'rgba(255,255,255,0.18)';
    for (let k = 0; k < 4; k++) g.fillRect(52, 182 + k * 34, (k === 3 ? 0.55 : 0.95) * (W - 330), 16);
    rrect(g, W - 250, 168, 200, 150, 14); g.fillStyle = 'rgba(53,225,244,0.18)'; g.fill();
    cloudIcon(g, W - 150, 232, 0.9);
  } else {
    g.fillStyle = '#fff'; g.font = '800 30px Inter'; g.fillText('Precipitation (mm)', 52, 140);
    const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S'], v = [0, 1, 0, 0, 4, 2, 0];
    days.forEach((d, i) => {
      const x = 70 + i * ((W - 140) / 7), bh = 30 + v[i] * 34;
      g.fillStyle = i === 4 ? THEME.cyan : 'rgba(255,255,255,0.25)';
      g.fillRect(x, 320 - bh, 38, bh);
      g.fillStyle = i === 4 ? THEME.cyan : 'rgba(255,255,255,0.55)'; g.font = '700 22px Inter';
      g.fillText(d, x + 10, 350);
    });
  }
}

// validator label / answer cards. state: { col, title, sub, big }
export function drawLabel(g, W, H, st) {
  panelBase(g, W, H, { col: st.col, col2: st.col, r: 26, lw: 5, fill: 'rgba(14,6,28,0.8)' });
  g.textAlign = 'center'; g.textBaseline = 'middle';
  if (st.big) {
    g.fillStyle = '#fff'; g.font = '900 64px Inter'; g.fillText(st.big, W / 2, H / 2 - 16);
    g.fillStyle = st.col; g.font = '700 26px Inter'; g.fillText(st.sub, W / 2, H / 2 + 44);
  } else {
    g.fillStyle = '#fff'; g.font = '800 36px Inter'; g.fillText(st.title, W / 2, H / 2 - 16);
    g.fillStyle = st.col; g.font = '700 26px Inter'; g.fillText(st.sub, W / 2, H / 2 + 26);
  }
}

export function drawVerdict(g, W, H, st) {
  panelBase(g, W, H, { col: THEME.cyan, col2: THEME.pink, r: 36, lw: 7 });
  g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillStyle = THEME.cyan; g.font = '800 30px Inter';
  g.fillText('VERDICT', W / 2, 78);
  g.fillStyle = '#fff'; g.font = '900 96px Inter';
  g.fillText('IT RAINED', W / 2 - 40, 172);
  // check mark
  g.strokeStyle = THEME.cyan; g.lineWidth = 16; g.lineCap = 'round'; g.lineJoin = 'round';
  const x = W / 2 + 290, y = 172;
  g.beginPath(); g.moveTo(x - 30, y); g.lineTo(x - 6, y + 26); g.lineTo(x + 38, y - 30); g.stroke();
  g.fillStyle = 'rgba(255,255,255,0.65)'; g.font = '700 30px Inter';
  g.fillText(st.sub || '5 / 5 validators agree', W / 2, 262);
}

export function drawStamp(g, W, H) {
  g.save();
  g.strokeStyle = THEME.pink; g.lineWidth = 16; g.shadowColor = THEME.pink; g.shadowBlur = 26;
  rrect(g, 24, 24, W - 48, H - 48, 30); g.stroke();
  g.lineWidth = 5; rrect(g, 50, 50, W - 100, H - 100, 18); g.stroke();
  g.fillStyle = THEME.pink; g.font = '900 100px Inter'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText('SETTLED', W / 2, H / 2 + 5);
  g.restore();
}
