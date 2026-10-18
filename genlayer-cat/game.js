// Truth Runner: an endless runner with the GenLayer cat.
// Collect trusted evidence (cyan), avoid fake data (red), and cross the gate whose answer the
// evidence supports. Track distance `s` grows forward; world z = -s.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createCharacter } from './character.js';
import { ToonPipeline } from './toon.js';
import { Sound } from './game-audio.js';

const $ = (id) => document.getElementById(id);
const TEST = new URLSearchParams(location.search).has('test');
const C = { pink: '#ff87ff', violet: '#dc00ff', trust: '#35e1f4', fake: '#ff4d6d', gold: '#ffd36b' };
const LANE_X = [-2.4, 0, 2.4];
const clamp = (x, a, b) => Math.min(b, Math.max(a, x));
const V = (x, y, z) => new THREE.Vector3(x, y, z);

function mulberry32(a) {
  return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
let rnd = mulberry32(TEST ? 7 : Date.now() & 0x7fffffff);
const pick = (a) => a[Math.floor(rnd() * a.length)];

// option 0 always backs YES, option 1 backs NO
const CLAIMS = [
  { q: 'Did it rain in Paris on Friday?', a: ['RAIN · 4 mm', 'SUNNY · 0 mm'] },
  { q: 'Did the cup final end in a draw?', a: ['1–1 FULL TIME', '2–0 FULL TIME'] },
  { q: 'Was the parcel delivered?', a: ['DELIVERED 14:02', 'LOST IN TRANSIT'] },
  { q: 'Did flight GL204 land on time?', a: ['LANDED 09:58', 'DELAYED 2 H'] },
  { q: 'Is the old bridge open today?', a: ['OPEN TO TRAFFIC', 'CLOSED FOR REPAIR'] },
  { q: 'Did the rocket reach orbit?', a: ['ORBIT REACHED', 'LAUNCH SCRUBBED'] },
  { q: 'Did the concert sell out?', a: ['SOLD OUT', '300 SEATS LEFT'] },
  { q: 'Was the marathon record broken?', a: ['NEW RECORD', 'RECORD STANDS'] },
];

// ---------------------------------------------------------------- renderer + scene
const canvas = $('scene');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'high-performance', preserveDrawingBuffer: TEST });
renderer.toneMapping = THREE.NeutralToneMapping;
renderer.toneMappingExposure = 2.0;
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
scene.environmentIntensity = 0.5;
scene.add(new THREE.HemisphereLight('#ffffff', '#3a1a55', 0.42));
// key from the front-top (the menu shows the face); rims from ahead of the runner outline the silhouette
const key = new THREE.DirectionalLight('#fff6ff', 0.62); key.position.set(3, 7, -8); scene.add(key);
const back = new THREE.DirectionalLight('#e9dcff', 0.22); back.position.set(-2, 6, 8); scene.add(back);
const rimA = new THREE.DirectionalLight(C.pink, 0.75); rimA.position.set(-6, 3, -8); scene.add(rimA);
const rimB = new THREE.DirectionalLight(C.violet, 0.85); rimB.position.set(7, 4, -7); scene.add(rimB);
const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 500);

// quality: 2 = hi-dpi + ink/bloom, 1 = 1x + ink/bloom, 0 = plain render
let level = TEST ? 1 : 2, pipe = null;
function resize() {
  const w = Math.max(1, canvas.clientWidth), h = Math.max(1, canvas.clientHeight);
  const dpr = level === 2 ? Math.min(window.devicePixelRatio || 1, 1.75) : 1;
  renderer.setPixelRatio(dpr);
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
  if (pipe) { pipe.dispose(); pipe = null; }
  if (level > 0) {
    pipe = new ToonPipeline(renderer, scene, camera, {
      width: Math.round(w * dpr), height: Math.round(h * dpr), lineWidth: Math.max(1.2, 1.5 * dpr * Math.min(1, h / 820)),
      bloom: { strength: 0.6, radius: 0.45, threshold: 1.3 }, grade: true,
    });
    pipe.grade.uniforms.grain.value = 0.02;
    pipe.grade.uniforms.vignette.value = 0.3;
    pipe.grade.uniforms.ca.value = 0.0012;
  }
}
const toneChunk = `#include <tonemapping_fragment>\n#include <colorspace_fragment>`;

// sky dome (follows the camera)
const sky = new THREE.Mesh(new THREE.SphereGeometry(300, 32, 16), new THREE.ShaderMaterial({
  side: THREE.BackSide, depthWrite: false, fog: false,
  vertexShader: `varying vec3 vD; void main(){ vD = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: `varying vec3 vD;
    void main(){
      float h = vD.y, a = atan(vD.x, -vD.z);
      vec3 c = mix(vec3(0.042, 0.007, 0.065), vec3(0.004, 0.0015, 0.012), smoothstep(0.0, 0.5, h));
      c += vec3(0.86, 0.0, 1.0) * 0.05 * exp(-abs(h) * 22.0);
      c += vec3(0.2, 0.75, 0.85) * 0.02 * exp(-pow(a * 1.2, 2.0)) * exp(-abs(h - 0.08) * 12.0);
      // two ranges of rolling purple hills
      float far = 0.05 + 0.03 * sin(a * 3.0 + 1.3) + 0.016 * sin(a * 7.0 + 0.4) + 0.008 * sin(a * 17.0);
      float near = 0.03 + 0.022 * sin(a * 5.0 + 2.1) + 0.01 * sin(a * 11.0 + 1.0);
      if (h < far) c = mix(vec3(0.05, 0.01, 0.085), vec3(0.11, 0.025, 0.16), smoothstep(far - 0.03, far, h));
      if (h < near) c = mix(vec3(0.02, 0.004, 0.035), vec3(0.06, 0.012, 0.09), smoothstep(near - 0.025, near, h));
      c += vec3(0.9, 0.3, 1.0) * 0.08 * exp(-abs(h - far) * 260.0) * step(near, h);
      gl_FragColor = vec4(c, 1.0);
      ${toneChunk}
    }`,
}));
scene.add(sky);

// floor: track with lanes + grid; follows the runner, pattern stays in world space
const floorU = { uTime: { value: 0 }, uPulse: { value: 0 }, uCat: { value: new THREE.Vector3() }, cA: { value: new THREE.Color(C.pink) }, cB: { value: new THREE.Color(C.violet) }, cT: { value: new THREE.Color(C.trust) } };
const floor = new THREE.Mesh(new THREE.PlaneGeometry(220, 420), new THREE.ShaderMaterial({
  uniforms: floorU,
  vertexShader: `varying vec3 vW; varying float vDist; void main(){ vec4 w = modelMatrix * vec4(position, 1.0); vW = w.xyz; vDist = distance(cameraPosition, w.xyz); gl_Position = projectionMatrix * viewMatrix * w; }`,
  fragmentShader: `uniform float uTime, uPulse; uniform vec3 uCat, cA, cB, cT; varying vec3 vW; varying float vDist;
    void main(){
      vec2 p = vW.xz; float ax = abs(p.x);
      float track = 1.0 - smoothstep(3.6, 3.75, ax);
      float patchy = 0.5 + 0.25 * sin(p.x * 0.37 + p.y * 0.21) + 0.25 * sin(p.x * 0.11 - p.y * 0.43);
      vec3 ground = mix(vec3(0.012, 0.002, 0.022), vec3(0.04, 0.008, 0.065), patchy);
      vec3 col = mix(ground, vec3(0.02, 0.006, 0.039), track);
      float dl = abs(ax - 1.2);
      float dash = step(0.45, fract(p.y * 0.2));
      col += cT * (1.0 - smoothstep(0.015, 0.05, dl)) * dash * 0.22 * track;
      float edge = 1.0 - smoothstep(0.02, 0.1, abs(ax - 3.68));
      col += mix(cA, cB, 0.5 + 0.5 * sin(p.y * 0.05 + uTime)) * edge * (0.7 + 0.5 * uPulse);
      float sd = length((p - uCat.xy) * vec2(1.0, 0.8)) / uCat.z;
      col *= 1.0 - 0.75 * exp(-sd * sd * 2.2);
      col = mix(col, vec3(0.03, 0.005, 0.048), smoothstep(90.0, 165.0, vDist));
      gl_FragColor = vec4(col, 1.0);
      ${toneChunk}
    }`,
}));
floor.rotation.x = -Math.PI / 2;
scene.add(floor);

// purple meadow: instanced grass tufts on both sides. Each tuft keeps a base track position and the
// vertex shader wraps it into a window around the camera, so the field repeats with no CPU work.
const GRASS_LEN = 220;
const grassU = { uTime: { value: 0 }, uRefS: { value: 0 } };
const grass = (() => {
  const BLADES = 5, SEGS = 3;
  const pos = [], hgt = [], idx = [];
  const r = mulberry32(99);
  for (let b = 0; b < BLADES; b++) {
    const ang = r() * Math.PI * 2, off = Math.sqrt(r()) * 0.32, tall = 0.65 + r() * 0.7, lean = 0.15 + r() * 0.3, w0 = 0.075 + r() * 0.04;
    const ox = Math.cos(ang) * off, oz = Math.sin(ang) * off, rot = r() * Math.PI;
    const cr = Math.cos(rot), sr = Math.sin(rot);
    const base = pos.length / 3;
    for (let k = 0; k <= SEGS; k++) {
      const t = k / SEGS, w = k === SEGS ? 0 : w0 * Math.pow(1 - t, 0.7), y = t * tall, bend = lean * t * t;
      for (const side of k === SEGS ? [0] : [-1, 1]) {
        const lx = side * w, lz = bend;
        pos.push(ox + lx * cr - lz * sr, y, oz + lx * sr + lz * cr);
        hgt.push(t);
      }
    }
    for (let k = 0; k < SEGS - 1; k++) { const a0 = base + k * 2; idx.push(a0, a0 + 1, a0 + 2, a0 + 1, a0 + 3, a0 + 2); }
    const a0 = base + (SEGS - 1) * 2; idx.push(a0, a0 + 1, a0 + 2);
  }
  const g = new THREE.InstancedBufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('aH', new THREE.Float32BufferAttribute(hgt, 1));
  g.setIndex(idx);
  const inst = [], tint = [];
  const add = (xmin, xmax, perMeter, smin, smax) => {
    const n = Math.round(perMeter * GRASS_LEN);
    for (let i = 0; i < n; i++) for (const side of [-1, 1]) {
      const x = side * (xmin + Math.pow(r(), 1.3) * (xmax - xmin));
      inst.push(x, r() * GRASS_LEN, r() * Math.PI * 2, smin + r() * (smax - smin));
      tint.push(r(), r() < 0.045 ? (r() < 0.5 ? 1 : 2) : 0);
    }
  };
  add(3.95, 13, 12, 0.9, 1.6);
  add(13, 48, 6, 1.6, 2.8);
  g.setAttribute('aBlade', new THREE.InstancedBufferAttribute(new Float32Array(inst), 4));
  g.setAttribute('aTint', new THREE.InstancedBufferAttribute(new Float32Array(tint), 2));
  g.instanceCount = inst.length / 4;
  const m = new THREE.Mesh(g, new THREE.ShaderMaterial({
    side: THREE.DoubleSide, uniforms: grassU,
    vertexShader: `attribute float aH; attribute vec4 aBlade; attribute vec2 aTint;
      uniform float uTime, uRefS;
      varying float vH, vDist, vTint, vGlow;
      void main(){
        float s = aBlade.y + ${GRASS_LEN.toFixed(1)} * floor((uRefS - aBlade.y) / ${GRASS_LEN.toFixed(1)} + 1.0);
        float c = cos(aBlade.z), sn = sin(aBlade.z);
        vec3 p = position;
        p.xz = mat2(c, -sn, sn, c) * p.xz;
        p *= aBlade.w;
        vec3 root = vec3(aBlade.x, 0.0, -s);
        float w = sin(uTime * 1.7 + root.x * 0.3 + root.z * 0.17) * 0.6 + sin(uTime * 2.9 + root.z * 0.5 + root.x * 0.8) * 0.25;
        float bend = aH * aH * aBlade.w;
        p.x += w * bend * 0.32;
        p.z += w * bend * 0.12;
        vec4 mv = viewMatrix * vec4(root + p, 1.0);
        vH = aH; vDist = -mv.z; vTint = aTint.x; vGlow = aTint.y;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `varying float vH, vDist, vTint, vGlow;
      void main(){
        vec3 base = vec3(0.016, 0.003, 0.03);
        vec3 mid = mix(vec3(0.16, 0.03, 0.32), vec3(0.3, 0.05, 0.42), vTint);
        vec3 tip = mix(vec3(0.55, 0.14, 0.8), vec3(0.8, 0.25, 0.85), vTint);
        vec3 col = mix(base, mid, smoothstep(0.0, 0.55, vH));
        col = mix(col, tip, smoothstep(0.55, 1.0, vH));
        if (vGlow > 0.5) {
          vec3 g = vGlow > 1.5 ? vec3(0.21, 0.88, 0.96) : vec3(1.0, 0.53, 1.0);
          col += g * smoothstep(0.75, 1.0, vH) * 1.6;
        }
        col = mix(col, vec3(0.03, 0.005, 0.048), smoothstep(80.0, 150.0, vDist));
        gl_FragColor = vec4(col, 1.0);
        ${toneChunk}
      }`,
  }));
  m.frustumCulled = false;
  return m;
})();
scene.add(grass);

// fireflies drifting over the meadow (same wrap trick as the grass)
const flyU = { uTime: { value: 0 }, uRefS: { value: 0 }, uScale: { value: 400 } };
const fireflies = (() => {
  const N = 320, r = mulberry32(5);
  const g = new THREE.BufferGeometry();
  const p = new Float32Array(N * 3), c = new Float32Array(N * 3), ph = new Float32Array(N);
  const pal = [C.pink, C.trust, C.violet, '#ffffff'].map((h) => new THREE.Color(h));
  for (let i = 0; i < N; i++) {
    p[i * 3] = (r() < 0.5 ? -1 : 1) * (4.6 + Math.pow(r(), 1.5) * 34); p[i * 3 + 1] = 0.4 + r() * 3.6; p[i * 3 + 2] = r() * GRASS_LEN;
    const col = pal[i % 4]; c[i * 3] = col.r * 2.2; c[i * 3 + 1] = col.g * 2.2; c[i * 3 + 2] = col.b * 2.2;
    ph[i] = r() * 100;
  }
  g.setAttribute('position', new THREE.BufferAttribute(p, 3));
  g.setAttribute('color', new THREE.BufferAttribute(c, 3));
  g.setAttribute('aPh', new THREE.BufferAttribute(ph, 1));
  const pts = new THREE.Points(g, new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, uniforms: flyU,
    vertexShader: `attribute vec3 color; attribute float aPh; uniform float uTime, uRefS, uScale; varying vec3 vC; varying float vA;
      void main(){
        float s = position.z + ${GRASS_LEN.toFixed(1)} * floor((uRefS - position.z) / ${GRASS_LEN.toFixed(1)} + 1.0);
        vec3 w = vec3(position.x + sin(uTime * 0.7 + aPh) * 0.6, position.y + sin(uTime * 1.1 + aPh * 1.7) * 0.35, -s + cos(uTime * 0.5 + aPh) * 0.6);
        vec4 mv = viewMatrix * vec4(w, 1.0);
        vC = color; vA = (0.35 + 0.65 * pow(0.5 + 0.5 * sin(uTime * 2.3 + aPh * 3.1), 3.0)) * (1.0 - smoothstep(70.0, 130.0, -mv.z));
        gl_PointSize = 0.16 * uScale / -mv.z;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `varying vec3 vC; varying float vA;
      void main(){ vec2 d = gl_PointCoord - 0.5; float a = exp(-dot(d, d) * 14.0) * vA; gl_FragColor = vec4(vC * a, a); }`,
  }));
  pts.frustumCulled = false;
  return pts;
})();
scene.add(fireflies);

// sparks kicked up behind a belly slide
const SPARKS = 48;
const sparkGeo = new THREE.BufferGeometry();
sparkGeo.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(SPARKS * 3), 3));
const sparks = new THREE.Points(sparkGeo, new THREE.PointsMaterial({ color: new THREE.Color(C.trust).multiplyScalar(2.4), size: 0.12, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false }));
sparks.frustumCulled = false;
const sparkData = Array.from({ length: SPARKS }, () => ({ t: -9, x: 0, s: 0, vx: 0, vy: 0 }));
let sparkNext = 0;
scene.add(sparks);

// ---------------------------------------------------------------- canvas textures
function canvasTex(w, h, draw) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4;
  return t;
}
function hazard(g, w, h, label, col) {
  g.fillStyle = '#1a0610'; g.fillRect(0, 0, w, h);
  g.save(); g.beginPath(); g.rect(0, 0, w, h); g.clip();
  g.fillStyle = col; g.globalAlpha = 0.85;
  for (let x = -h; x < w + h; x += 64) { g.beginPath(); g.moveTo(x, 0); g.lineTo(x + 32, 0); g.lineTo(x + 32 - h, h); g.lineTo(x - h, h); g.closePath(); g.fill(); }
  g.restore(); g.globalAlpha = 1;
  g.fillStyle = 'rgba(14,3,10,0.85)'; g.fillRect(w * 0.12, h * 0.22, w * 0.76, h * 0.56);
  g.fillStyle = '#fff'; g.font = `900 ${Math.round(h * 0.34)}px Unbounded, "Arial Black", sans-serif`;
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(label, w / 2, h / 2 + 2, w * 0.72);
}
let TEX = null;
function buildTextures() {
  TEX = {
    low: canvasTex(512, 220, (g, w, h) => hazard(g, w, h, 'FIREWALL', C.fake)),
    high: canvasTex(512, 268, (g, w, h) => hazard(g, w, h, 'BOT SWARM', '#ff8a3d')),
    block: canvasTex(512, 512, (g, w, h) => {
      g.fillStyle = '#22060f'; g.fillRect(0, 0, w, h);
      for (let i = 0; i < 26; i++) { g.fillStyle = i % 3 ? 'rgba(255,77,109,0.25)' : 'rgba(255,135,255,0.2)'; g.fillRect(0, rnd() * h, w, 4 + rnd() * 18); }
      g.strokeStyle = C.fake; g.lineWidth = 18; g.strokeRect(9, 9, w - 18, h - 18);
      g.lineWidth = 34; g.lineCap = 'round';
      g.beginPath(); g.moveTo(w * 0.3, h * 0.2); g.lineTo(w * 0.7, h * 0.55); g.moveTo(w * 0.7, h * 0.2); g.lineTo(w * 0.3, h * 0.55); g.stroke();
      g.fillStyle = '#fff'; g.font = '900 96px Unbounded, "Arial Black", sans-serif'; g.textAlign = 'center'; g.fillText('SPAM', w / 2, h * 0.82);
    }),
    wall: canvasTex(512, 640, (g, w, h) => {
      g.fillStyle = '#140823'; g.fillRect(0, 0, w, h);
      g.strokeStyle = C.gold; g.lineWidth = 16; g.strokeRect(8, 8, w - 16, h - 16);
      g.fillStyle = C.gold; g.font = '900 300px Unbounded, "Arial Black", sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('?', w / 2, h * 0.42);
      g.fillStyle = '#fff'; g.font = '800 44px Manrope, sans-serif'; g.fillText('PICK A SIDE', w / 2, h * 0.8);
    }),
  };
}

// shard label textures, cached by text + trust
const labelCache = new Map();
function labelTex(text, trusted) {
  const k = text + '|' + trusted;
  if (labelCache.has(k)) return labelCache.get(k);
  const t = canvasTex(512, 150, (g, w, h) => {
    const col = trusted ? C.trust : C.fake;
    g.fillStyle = trusted ? 'rgba(6,22,30,0.82)' : 'rgba(34,6,14,0.82)';
    g.strokeStyle = col; g.lineWidth = 6; g.shadowColor = col; g.shadowBlur = 16;
    g.beginPath(); g.roundRect(8, 8, w - 16, h - 16, 24); g.fill(); g.stroke(); g.shadowBlur = 0;
    g.fillStyle = col; g.font = '600 26px "IBM Plex Mono", monospace'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText(trusted ? '✓ VERIFIED SOURCE' : '✕ FAKE SOURCE', w / 2, 44);
    g.fillStyle = '#fff'; g.font = '800 44px Manrope, sans-serif';
    g.fillText(text, w / 2, 100, w - 40);
  });
  labelCache.set(k, t);
  return t;
}

// ---------------------------------------------------------------- entities (pooled)
let LID = 300; // ink-line ids for props
const MAT = {};
function buildMaterials() {
  const emissive = (tex, i = 0.9) => new THREE.MeshStandardMaterial({ map: tex, emissive: '#ffffff', emissiveMap: tex, emissiveIntensity: i, roughness: 0.5 });
  MAT.low = emissive(TEX.low, 0.75);
  MAT.high = emissive(TEX.high, 0.75);
  MAT.block = emissive(TEX.block, 0.6);
  MAT.wall = emissive(TEX.wall, 0.9);
  MAT.post = new THREE.MeshStandardMaterial({ color: '#2a1440', roughness: 0.4, metalness: 0.4 });
  MAT.trust = new THREE.MeshStandardMaterial({ color: C.trust, emissive: C.trust, emissiveIntensity: 1.6, roughness: 0.2, metalness: 0.1 });
  MAT.fake = new THREE.MeshStandardMaterial({ color: C.fake, emissive: C.fake, emissiveIntensity: 1.5, roughness: 0.3 });
  MAT.gateFrame = new THREE.MeshStandardMaterial({ color: C.gold, emissive: C.gold, emissiveIntensity: 0.9, roughness: 0.3 });
}
const GEO = {
  low: new THREE.BoxGeometry(2.1, 0.95, 0.35).translate(0, 0.62, 0),
  high: new THREE.BoxGeometry(2.1, 1.1, 0.3).translate(0, 3.0, 0),
  block: new THREE.BoxGeometry(2.0, 2.4, 1.8).translate(0, 1.2, 0),
  postLow: new THREE.CylinderGeometry(0.07, 0.07, 1.1, 8).translate(0, 0.55, 0),
  postHigh: new THREE.CylinderGeometry(0.07, 0.07, 3.7, 8).translate(0, 1.85, 0),
  shard: new THREE.OctahedronGeometry(0.42, 0),
  label: new THREE.PlaneGeometry(1.9, 0.56),
  wall: new THREE.BoxGeometry(2.0, 3.6, 0.6).translate(0, 1.8, 0),
  gatePost: new THREE.BoxGeometry(0.2, 4.2, 0.2).translate(0, 2.1, 0),
  gatePanel: new THREE.PlaneGeometry(2.3, 1.08),
};
function withId(m) { m.userData.lineId = LID; return m; }
const pools = { low: [], high: [], block: [], shard: [], gate: [] };
function makeObstacle(kind) {
  const g = new THREE.Group();
  const body = withId(new THREE.Mesh(GEO[kind], MAT[kind]));
  g.add(body);
  if (kind !== 'block') for (const s of [-1, 1]) {
    const p = withId(new THREE.Mesh(kind === 'low' ? GEO.postLow : GEO.postHigh, MAT.post));
    p.position.x = s * 1.08; g.add(p);
  }
  LID = LID >= 400 ? 300 : LID + 1;
  return g;
}
function makeShard() {
  const g = new THREE.Group();
  const gem = new THREE.Mesh(GEO.shard, MAT.trust);
  gem.userData.lineId = 460;
  gem.position.y = 1.15;
  const lab = new THREE.Mesh(GEO.label, new THREE.MeshBasicMaterial({ transparent: true, depthWrite: false }));
  lab.position.y = 2.15;
  lab.renderOrder = 5;
  g.add(gem, lab);
  g.userData.gem = gem; g.userData.lab = lab;
  return g;
}
function makeGate() {
  const g = new THREE.Group();
  const panels = [];
  for (const li of [0, 2]) {
    for (const s of [-1, 1]) {
      const p = withId(new THREE.Mesh(GEO.gatePost, MAT.gateFrame));
      p.position.set(LANE_X[li] + s * 1.15, 0, 0); g.add(p);
    }
    const c = document.createElement('canvas'); c.width = 640; c.height = 300;
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
    const panel = new THREE.Mesh(GEO.gatePanel, new THREE.MeshBasicMaterial({ map: t, transparent: true, side: THREE.DoubleSide }));
    panel.material.color.setScalar(1.3);
    panel.position.set(LANE_X[li], 3.55, 0);
    g.add(panel);
    panels.push({ c, t });
  }
  const wall = withId(new THREE.Mesh(GEO.wall, MAT.wall));
  g.add(wall);
  g.userData.panels = panels;
  return g;
}
function paintGatePanel(p, answer, evidence) {
  const g = p.c.getContext('2d'), w = p.c.width, h = p.c.height;
  g.clearRect(0, 0, w, h);
  g.fillStyle = 'rgba(20,8,34,0.9)'; g.strokeStyle = C.gold; g.lineWidth = 8; g.shadowColor = C.gold; g.shadowBlur = 20;
  g.beginPath(); g.roundRect(10, 10, w - 20, h - 20, 28); g.fill(); g.stroke(); g.shadowBlur = 0;
  g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillStyle = '#fff'; g.font = '900 104px Unbounded, "Arial Black", sans-serif'; g.fillText(answer, w / 2, 112);
  g.fillStyle = C.trust; g.font = '800 40px Manrope, sans-serif'; g.fillText(evidence, w / 2, 222, w - 50);
  p.t.needsUpdate = true;
}
const ents = []; // active: { kind, lane, s, obj, done, trusted?, opt? }
function spawn(kind, lane, s, extra = {}) {
  const pool = pools[kind];
  const obj = pool.pop() || (kind === 'shard' ? makeShard() : kind === 'gate' ? makeGate() : makeObstacle(kind));
  obj.visible = true;
  obj.position.set(kind === 'gate' ? 0 : LANE_X[lane], 0, -s);
  obj.rotation.set(0, 0, 0);
  scene.add(obj);
  const e = Object.assign({ kind, lane, s, obj, done: false }, extra);
  if (kind === 'shard') {
    obj.userData.gem.material = e.trusted ? MAT.trust : MAT.fake;
    obj.userData.lab.material.map = labelTex(e.text, e.trusted);
    obj.userData.lab.material.needsUpdate = true;
    obj.userData.gem.scale.setScalar(1);
  }
  ents.push(e);
  return e;
}
function release(e) {
  scene.remove(e.obj);
  pools[e.kind].push(e.obj);
}

// ---------------------------------------------------------------- the cat
const cat = createCharacter({ detail: 0.8 });
cat.visor.material.envMapIntensity = 0.22;
scene.add(cat.root);
const J = cat.J;
const qx = new THREE.Quaternion(), ex = new THREE.Euler();
const addRot = (j, x, y, z) => j.quaternion.multiply(qx.setFromEuler(ex.set(x, y, z)));
const visorCtx = $('visorCam').getContext('2d');
let faceKey = '';
function setFace(st) {
  const k = JSON.stringify(st, (n, v) => (typeof v === 'number' ? Math.round(v * 50) / 50 : v));
  if (k === faceKey) return;
  faceKey = k;
  cat.face.draw(st);
  visorCtx.drawImage(cat.face.canvas, 0, 0, visorCtx.canvas.width, visorCtx.canvas.height);
}

// ---------------------------------------------------------------- state
const sound = new Sound();
const G = {
  mode: 'loading', // loading | menu | run | paused | dying | over
  time: 0, runTime: 0, dist: 0, prevDist: 0, speed: 0,
  lane: 1, x: 0, y: 0, vy: 0, slide: 0, invuln: 0, roll: 0,
  lives: 3, mult: 1, score: 0,
  stats: { trusted: 0, fake: 0, gates: 0, gatesOk: 0 },
  nextRowS: 0, round: null, gen: null, lastClaim: -1,
  mood: null, moodUntil: 0, shake: 0, dieT: 0, camBlend: 0, phase: 0,
};
const hud = {};

function resetRun() {
  for (const e of ents) release(e);
  ents.length = 0;
  Object.assign(G, {
    runTime: 0, dist: 0, prevDist: 0, speed: 0, lane: 1, x: 0, y: 0, vy: 0, slide: 0, invuln: 0, roll: 0,
    lives: 3, mult: 1, score: 0, stats: { trusted: 0, fake: 0, gates: 0, gatesOk: 0 },
    nextRowS: 46, mood: null, moodUntil: 0, shake: 0, dieT: 0, phase: 0,
  });
  G.round = G.gen = makeRound(30, 14);
  setClaimHUD(false);
}

// A round is one claim: its evidence rows, then its gate. The next round is generated as soon as
// this round's gate is placed, so the track ahead is always filled.
function makeRound(s0, speed) {
  let ci;
  do { ci = Math.floor(rnd() * CLAIMS.length); } while (ci === G.lastClaim);
  G.lastClaim = ci;
  const len = clamp(Math.max(speed, 14) * 17, 230, 480);
  return { c: CLAIMS[ci], truth: rnd() < 0.5 ? 0 : 1, order: rnd() < 0.5 ? [0, 1] : [1, 0], s0, gateS: s0 + len, tally: [0, 0], fake: [0, 0], announced: false, gate: null, next: null };
}

const baseSpeed = () => Math.min(29, 13.5 + G.runTime * 0.16);

// one row of obstacles + an evidence chain, guaranteeing a free path
function genRow(s, R) {
  const d = clamp(G.runTime / 100, 0, 1);
  const r = rnd();
  const lanesBlocked = [];
  if (r < 0.1 + 0.08 * d && s > R.s0 + 30) {
    const kind = rnd() < 0.5 ? 'low' : 'high';
    for (const l of [0, 1, 2]) spawn(kind, l, s);
    // evidence floats just past the wall
    chain(Math.floor(rnd() * 3), s + 6, R);
    return;
  }
  const count = r < 0.5 + 0.2 * d ? 2 : 1;
  const order = [0, 1, 2].sort(() => rnd() - 0.5);
  for (let i = 0; i < count; i++) {
    const l = order[i];
    const kr = rnd();
    spawn(kr < 0.36 ? 'low' : kr < 0.66 ? 'high' : 'block', l, s);
    lanesBlocked.push(l);
  }
  const free = [0, 1, 2].filter((l) => !lanesBlocked.includes(l));
  if (rnd() < 0.85) chain(pick(free), s, R);
}
function chain(lane, s, R) {
  const d = clamp(G.runTime / 100, 0, 1);
  const trusted = rnd() < 0.7 - 0.1 * d;
  const opt = trusted ? R.truth : 1 - R.truth;
  const n = 2 + Math.floor(rnd() * 2);
  for (let k = 0; k < n; k++) spawn('shard', lane, s - 2.8 * (n - 1) + k * 2.8, { trusted, opt, text: R.c.a[opt], round: R });
}

function spawnAhead() {
  const horizon = G.dist + 150;
  while (G.nextRowS < horizon) {
    const R = G.gen;
    if (G.nextRowS >= R.gateS - 34) {
      // the gate: answers left and right, a wall in the middle
      R.gate = spawn('gate', 1, R.gateS, { round: R });
      R.order.forEach((opt, i) => paintGatePanel(R.gate.obj.userData.panels[i], opt === 0 ? 'YES' : 'NO', R.c.a[opt]));
      R.next = G.gen = makeRound(R.gateS + 34, G.speed);
      G.nextRowS = R.gateS + 46;
      continue;
    }
    genRow(G.nextRowS, R);
    G.nextRowS += Math.max(G.speed * 0.6, 11) + rnd() * 7;
  }
}

// ---------------------------------------------------------------- input
function move(dir) {
  const l = clamp(G.lane + dir, 0, 2);
  if (l !== G.lane) { G.lane = l; G.roll = -dir; sound.sfx('lane'); }
}
function jump() {
  if (G.y <= 0.001 && G.vy === 0) { G.vy = 10.6; G.slide = 0; sound.sfx('jump'); }
}
const SLIDE = 0.72;
function slide() {
  if (G.y > 0.05) G.vy = -18; // fast fall, slide on landing
  if (G.slide < SLIDE - 0.15) G.slide = SLIDE;
  sound.sfx('slide');
}
// 0..1: how far into the belly slide the cat is (dives in 0.12 s, gets up over the last 0.14 s)
function slideTilt() {
  if (G.slide <= 0 || G.y > 0.6) return 0;
  const t = SLIDE - G.slide;
  const k = Math.min(t / 0.12, G.slide / 0.14, 1);
  return k * k * (3 - 2 * k);
}
window.addEventListener('keydown', (e) => {
  const k = e.code;
  if (G.mode === 'run') {
    if (k === 'ArrowLeft' || k === 'KeyA') move(-1);
    else if (k === 'ArrowRight' || k === 'KeyD') move(1);
    else if (k === 'ArrowUp' || k === 'KeyW' || k === 'Space') jump();
    else if (k === 'ArrowDown' || k === 'KeyS') slide();
    else if (k === 'KeyP' || k === 'Escape') pause();
    else return;
    e.preventDefault();
  } else if (G.mode === 'paused' && (k === 'KeyP' || k === 'Escape' || k === 'Enter')) { resume(); e.preventDefault(); }
  else if ((G.mode === 'menu' || G.mode === 'over') && k === 'Enter') { startRun(); e.preventDefault(); }
});
let touch = null;
canvas.addEventListener('pointerdown', (e) => { touch = { x: e.clientX, y: e.clientY, used: false }; canvas.focus({ preventScroll: true }); });
canvas.addEventListener('pointermove', (e) => {
  if (!touch || touch.used || G.mode !== 'run') return;
  const dx = e.clientX - touch.x, dy = e.clientY - touch.y;
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 28) return;
  touch.used = true;
  if (Math.abs(dx) > Math.abs(dy)) move(dx > 0 ? 1 : -1); else if (dy < 0) jump(); else slide();
});
canvas.addEventListener('pointerup', () => { if (touch && !touch.used && G.mode === 'run') jump(); touch = null; });
document.addEventListener('visibilitychange', () => { if (document.hidden && G.mode === 'run') pause(); });

// ---------------------------------------------------------------- HUD
const PAW = '<svg class="paw" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="12" cy="15.5" rx="5.6" ry="4.8" fill="currentColor"/><circle cx="5.2" cy="9.5" r="2.4" fill="currentColor"/><circle cx="9.6" cy="5.6" r="2.4" fill="currentColor"/><circle cx="14.4" cy="5.6" r="2.4" fill="currentColor"/><circle cx="18.8" cy="9.5" r="2.4" fill="currentColor"/></svg>';
const ICON_ON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/></svg>';
const ICON_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M17 9l5 6M22 9l-5 6"/></svg>';
const shown = {};
function setText(id, v) { if (shown[id] !== v) { shown[id] = v; $(id).textContent = v; } }
function updateHUD() {
  setText('score', Math.floor(G.score).toLocaleString('en-US'));
  setText('dist', `${Math.floor(G.dist).toLocaleString('en-US')} m`);
  setText('mult', `×${G.mult}`);
  const lk = String(G.lives);
  if (shown.lives !== lk) {
    shown.lives = lk;
    $('lives').innerHTML = [0, 1, 2].map((i) => `<span style="color:${i < G.lives ? C.pink : '#6d5a80'}" class="${i < G.lives ? '' : 'lost'}">${PAW}</span>`).join('');
    $('lives').setAttribute('aria-label', `${G.lives} lives left`);
  }
  const R = G.round;
  if (R && R.announced) {
    const tk = R.tally.join(',') + '|' + R.fake.join(',');
    if (shown.tally !== tk) {
      shown.tally = tk;
      const box = $('tally');
      box.textContent = '';
      [0, 1].forEach((opt) => {
        const p = document.createElement('span'); p.className = 'pill';
        const lab = document.createElement('span'); lab.textContent = R.c.a[opt];
        const n = document.createElement('span'); n.className = 'n'; n.textContent = `×${R.tally[opt]}`;
        p.append(lab, n);
        if (R.fake[opt]) { const b = document.createElement('span'); b.className = 'bad'; b.textContent = `+${R.fake[opt]} fake`; p.append(b); }
        box.append(p);
      });
    }
  }
}
function setClaimHUD(on) {
  const el = $('claim');
  if (on) { $('claimQ').textContent = G.round.c.q; shown.tally = null; }
  el.dataset.state = on ? 'shown' : 'hidden';
}
function toast(text, cls, sub = '') {
  const t = document.createElement('div');
  t.className = 'toast ' + cls;
  t.textContent = text;
  if (sub) { const s = document.createElement('small'); s.textContent = sub; t.append(s); }
  $('toasts').append(t);
  t.addEventListener('animationend', () => t.remove());
  setTimeout(() => t.remove(), 1600);
}
function setSoundIcon() {
  $('soundBtn').innerHTML = sound.muted ? ICON_OFF : ICON_ON;
  $('soundBtn').setAttribute('aria-label', sound.muted ? 'Sound off' : 'Sound on');
}
$('soundBtn').addEventListener('click', () => { sound.unlock(); sound.setMuted(!sound.muted); setSoundIcon(); });
$('pauseBtn').addEventListener('click', () => pause());
$('resumeBtn').addEventListener('click', () => resume());
$('quitBtn').addEventListener('click', () => { $('pauseMenu').hidden = true; G.mode = 'run'; die(); });
$('playBtn').addEventListener('click', () => startRun());
$('againBtn').addEventListener('click', () => startRun());
$('copyBtn').addEventListener('click', () => {
  const text = $('shareText').value;
  $('shareBox').hidden = false;
  const done = () => { $('copyBtn').textContent = 'Copied'; setTimeout(() => { $('copyBtn').textContent = 'Copy score'; }, 1600); };
  const fallback = () => { $('shareText').focus(); $('shareText').select(); $('copyBtn').textContent = 'Select and copy'; };
  try { navigator.clipboard.writeText(text).then(done, fallback); } catch (e) { fallback(); }
});

function mood(m, dur) { G.mood = m; G.moodUntil = G.time + dur; }

// ---------------------------------------------------------------- flow
function show(id) { for (const m of ['startMenu', 'pauseMenu', 'overMenu']) $(m).hidden = m !== id; }
function startRun() {
  sound.unlock();
  sound.sfx('start');
  sound.startMusic();
  resetRun();
  G.mode = 'run';
  show(null);
  $('hud').hidden = false;
  canvas.focus({ preventScroll: true });
}
function pause() {
  if (G.mode !== 'run') return;
  G.mode = 'paused';
  sound.stopMusic();
  show('pauseMenu');
  $('resumeBtn').focus();
}
function resume() {
  G.mode = 'run';
  show(null);
  sound.startMusic();
  canvas.focus({ preventScroll: true });
}
function die() {
  G.mode = 'dying';
  G.dieT = 0;
  sound.stopMusic();
  sound.sfx('over');
  setClaimHUD(false);
}
function finish() {
  G.mode = 'over';
  $('hud').hidden = true;
  const score = Math.floor(G.score);
  $('overScore').textContent = score.toLocaleString('en-US');
  $('stTrust').textContent = String(G.stats.trusted);
  $('stGate').textContent = `${G.stats.gatesOk}/${G.stats.gates}`;
  $('stDist').textContent = `${Math.floor(G.dist).toLocaleString('en-US')} m`;
  const prev = localBest();
  const best = Math.max(prev, score);
  saveLocalBest(best);
  $('overEyebrow').textContent = score > prev && score > 0 ? 'New personal best' : 'Run over';
  $('overBest').textContent = `Best ${best.toLocaleString('en-US')}`;
  $('shareText').value = `I scored ${score.toLocaleString('en-US')} in Truth Runner with the @GenLayer cat 🐱 ${G.stats.gatesOk}/${G.stats.gates} claims settled by consensus. Verify, then trust.`;
  $('shareBox').hidden = true;
  show('overMenu');
  $('againBtn').focus();
  renderBoards();
  postScore(score);
}

// ---------------------------------------------------------------- simulation
const hitLog = [], claimLog = [];
function hit(e) {
  if (G.invuln > 0) return;
  if (TEST) hitLog.push({ kind: e.kind, lane: e.lane, s: Math.round(e.s * 10) / 10, dist: Math.round(G.dist * 10) / 10, x: Math.round(G.x * 100) / 100, catLane: G.lane, y: Math.round(G.y * 100) / 100, slide: Math.round(G.slide * 100) / 100, speed: Math.round(G.speed * 10) / 10, row: ents.filter((o) => Math.abs(o.s - e.s) < 0.5 && o.kind !== 'shard').map((o) => o.kind + o.lane).join(' ') });
  G.lives -= 1;
  G.invuln = 1.8;
  G.speed *= 0.65;
  G.shake = 0.45;
  G.mult = 1;
  mood('sad', 1.2);
  sound.sfx('hit');
  toast(e.kind === 'low' ? 'FIREWALL' : e.kind === 'high' ? 'BOT SWARM' : e.kind === 'gate' ? 'NO ANSWER' : 'SPAM BLOCK', 'fake small');
  if (G.lives <= 0) die();
}
function resolveGate(R) {
  const li = G.x < -1.2 ? 0 : G.x > 1.2 ? 2 : 1;
  G.stats.gates++;
  if (li === 1) { hit({ kind: 'gate' }); return; }
  const opt = R.order[li === 0 ? 0 : 1];
  if (opt === R.truth) {
    const pts = 500 * G.mult;
    G.score += pts;
    G.stats.gatesOk++;
    G.mult = Math.min(5, G.mult + 1);
    mood('happy', 1.6);
    sound.sfx('gateOk');
    toast('CONSENSUS ✓', 'trust', `5/5 VALIDATORS AGREE · +${pts}`);
  } else {
    G.lives -= 1;
    G.mult = 1;
    G.invuln = 1.0;
    mood('sad', 1.4);
    sound.sfx('gateBad');
    toast('APPEALED ✕', 'fake', 'THE EVIDENCE SAID OTHERWISE');
    if (G.lives <= 0) die();
  }
}
function step(dt) {
  G.time += dt;
  floorU.uTime.value = G.time;
  if (G.mode === 'run') {
    G.runTime += dt;
    G.speed += (baseSpeed() - G.speed) * Math.min(1, dt * (G.runTime < 1 ? 3 : 0.9));
    G.prevDist = G.dist;
    G.dist += G.speed * dt;
    G.score += G.speed * dt * 0.5;
    sound.energy = clamp((G.speed - 13) / 16, 0, 1);
    // lateral + vertical
    G.x += (LANE_X[G.lane] - G.x) * Math.min(1, dt * 16);
    G.roll *= Math.exp(-dt * 8);
    if (G.y > 0 || G.vy !== 0) {
      G.vy -= 30 * dt; G.y += G.vy * dt;
      if (G.y <= 0) { G.y = 0; G.vy = 0; }
    }
    if (G.slide > 0) G.slide = Math.max(0, G.slide - dt);
    if (G.invuln > 0) G.invuln = Math.max(0, G.invuln - dt);
    // rounds
    const R = G.round;
    if (!R.announced && G.dist >= R.s0 - 6) { R.announced = true; setClaimHUD(true); sound.sfx('claim'); toast('NEW CLAIM', 'pink small', R.c.q.toUpperCase()); if (TEST) claimLog.push(R.c.q); }
    spawnAhead();
    // collisions (swept along the track)
    const a = G.prevDist - 0.45, b = G.dist + 0.45;
    for (const e of ents) {
      if (e.done || e.s < a - 1 || e.s > b + 1) continue;
      if (e.kind === 'gate') {
        if (G.dist >= e.s) {
          e.done = true;
          resolveGate(e.round);
          G.round = e.round.next;
          setClaimHUD(false);
        }
        continue;
      }
      if (e.s < a || e.s > b) continue;
      const dx = Math.abs(G.x - LANE_X[e.lane]);
      if (e.kind === 'shard') {
        if (dx < 1.2 && G.y < 2.6) {
          e.done = true; e.taken = G.time;
          const R2 = e.round;
          if (e.trusted) {
            G.stats.trusted++; R2.tally[e.opt]++;
            G.score += 25 * G.mult;
            sound.sfx('collect', R2.tally[e.opt]);
            mood('happy', 0.35);
          } else {
            G.stats.fake++; R2.fake[e.opt]++;
            if (G.mult > 1) toast('FAKE DATA', 'fake small', 'MULTIPLIER RESET');
            G.mult = 1;
            sound.sfx('fake');
            mood('sad', 0.6);
            G.shake = Math.max(G.shake, 0.12);
          }
        }
        continue;
      }
      if (dx > 0.95) continue; // forgiving while switching lanes
      const clear = e.kind === 'low' ? G.y > 0.95 : e.kind === 'high' ? slideTilt() > 0.5 : false;
      if (!clear) { e.done = true; hit(e); if (G.mode !== 'run') break; }
    }
    // retire passed entities
    for (let i = ents.length - 1; i >= 0; i--) {
      const e = ents[i];
      // gone once behind the cat, so nothing sits between the chase camera and the runner
      if (e.s < G.dist - (e.kind === 'gate' ? 2.5 : 1.6) || (e.kind === 'shard' && e.taken && G.time - e.taken > 0.25)) { release(e); ents.splice(i, 1); }
    }
  } else if (G.mode === 'dying') {
    G.dieT += dt;
    G.speed *= Math.exp(-dt * 3);
    G.dist += G.speed * dt;
    if (G.dieT > 1.5) finish();
  }
  G.shake *= Math.exp(-dt * 6);
}

// ---------------------------------------------------------------- per-frame visuals
const P = cat.poses;
function animateCat(dt) {
  const running = G.mode === 'run' || (G.mode === 'dying' && G.dieT < 0.3);
  let pose = P.rest;
  if (G.mode === 'menu' || G.mode === 'loading') pose = cat.mixPose(P.rest, P.hero, 1);
  if (G.y > 0.05) pose = cat.mixPose(pose, P.cheer, 0.45);
  const tilt = slideTilt();
  if (tilt > 0) pose = cat.mixPose(pose, P.cheer, tilt * 0.9); // arms reach ahead while diving
  cat.applyPose(pose);
  const amp = (running ? 1 : G.mode === 'dying' ? 0.2 : 0) * (1 - tilt);
  G.phase += dt * (G.speed * 0.42 + (running ? 0 : 2.4));
  const sw = Math.sin(G.phase) * amp;
  const air = G.y > 0.05 ? 1 : 0;
  addRot(J.legs.L, sw * 0.85 * (1 - air) - 0.55 * air, 0, 0);
  addRot(J.legs.R, -sw * 0.85 * (1 - air) - 0.4 * air, 0, 0);
  addRot(J.arms.L.sh, -sw * 0.7, 0, 0);
  if (G.mode !== 'menu') addRot(J.arms.R.sh, sw * 0.7, 0, 0);
  J.hips.position.y += Math.abs(Math.cos(G.phase)) * 0.09 * amp * (1 - air);
  // belly slide: legs stretch back and kick a little, head lifts to look down the track
  addRot(J.legs.L, -0.35 * tilt + 0.12 * tilt * Math.sin(G.time * 18), 0, 0.12 * tilt);
  addRot(J.legs.R, -0.35 * tilt - 0.12 * tilt * Math.sin(G.time * 18), 0, -0.12 * tilt);
  addRot(J.torso, 0.13 * amp, -sw * 0.06, G.roll * 0.12);
  addRot(J.neck, -0.12 * amp - 0.42 * tilt + (G.mode === 'menu' ? 0.04 * Math.sin(G.time * 1.3) : 0), 0, G.mode === 'menu' ? 0.06 * Math.sin(G.time * 0.9) : 0);
  const tw = Math.max(0, Math.sin(G.time * 7)) * (Math.sin(G.time * 0.6) > 0.92 ? 1 : 0);
  addRot(J.ears.L, -0.25 * tw, 0, 0.15 * tw); addRot(J.ears.R, -0.25 * tw, 0, -0.15 * tw);
  cat.tail((p, u) => {
    const w = u * u;
    p.x += Math.sin(G.time * (running ? 9 : 2) + u * 4) * 0.18 * w;
    p.y += Math.sin(G.time * (running ? 7 : 1.6) + u * 3) * 0.1 * w + (running ? 0.25 * w : 0) + tilt * 0.5 * w;
    p.z -= running ? 0.35 * w : 0;
    return p;
  });
  // root: lane position, jump height, slide squash, lean into lane changes, hit blink
  let tumble = 0;
  if (G.mode === 'dying') tumble = Math.min(1, G.dieT / 0.6);
  const lift = 0.36 * tilt; // keeps the visor off the floor while lying on the belly
  cat.root.position.set(G.x, G.y + lift + (G.mode === 'dying' ? Math.sin(Math.min(1, G.dieT / 0.6) * Math.PI) * 0.6 : 0), -G.dist);
  cat.root.rotation.set(-tumble * 1.25 - tilt * 1.32, Math.PI + G.roll * -0.3, G.roll * 0.18 * (1 - tilt) - 0.42 * tilt);
  cat.root.scale.setScalar(1);
  cat.root.visible = !(G.invuln > 0 && G.mode === 'run' && Math.floor(G.time * 14) % 2 === 0);
  // visor
  const blink = (() => { const p = (G.time + 0.7) % 3.1; return p < 0.15 ? Math.sin((p / 0.15) * Math.PI) : 0; })();
  let st = { expr: 'angry', blink };
  if (G.mode === 'dying' || G.mode === 'over') st = { expr: 'sad', blink: 0 };
  else if (G.mood && G.time < G.moodUntil) st = { expr: G.mood, blink: 0, mouthOpen: G.mood === 'happy' ? 0.4 : 0 };
  else if (G.y > 0.6 || tilt > 0.3) st = { expr: 'wide', blink: 0 };
  setFace(st);
}

function updateWorld() {
  const s0 = G.dist;
  const refS = s0 - 60; // field window: 60 m behind the runner (the menu looks back) to 160 m ahead
  grassU.uTime.value = G.time; grassU.uRefS.value = refS;
  flyU.uTime.value = G.time; flyU.uRefS.value = refS; flyU.uScale.value = renderer.domElement.height * 0.5;
  // sparks: emit while sliding, then drift back and fade
  const tilt = slideTilt();
  if (tilt > 0.6 && G.mode === 'run' && G.time >= sparkNext) {
    for (let k = 0; k < 3; k++) {
      const d = sparkData.find((p) => G.time - p.t > 0.5) || sparkData[0];
      Object.assign(d, { t: G.time, x: G.x + (Math.random() - 0.5) * 0.9, s: s0 - 0.6, vx: (Math.random() - 0.5) * 2, vy: 1 + Math.random() * 2.2 });
    }
    sparkNext = G.time + 0.03;
  }
  const sp = sparkGeo.attributes.position.array;
  sparkData.forEach((p, i) => {
    const u = G.time - p.t;
    if (u > 0.5) { sp[i * 3 + 1] = -50; return; }
    sp[i * 3] = p.x + p.vx * u; sp[i * 3 + 1] = 0.1 + p.vy * u - 6 * u * u; sp[i * 3 + 2] = -(p.s - 3 * u);
  });
  sparkGeo.attributes.position.needsUpdate = true;
  // shards spin; fakes glitch
  for (const e of ents) {
    if (e.kind !== 'shard') continue;
    const g = e.obj.userData.gem;
    g.rotation.y = G.time * 2.4 + e.s;
    g.position.y = 1.15 + 0.12 * Math.sin(G.time * 3 + e.s);
    if (e.taken) { const u = Math.min(1, (G.time - e.taken) / 0.25); g.scale.setScalar(Math.max(0.01, 1 - u)); g.position.y += u * 2.2; e.obj.userData.lab.visible = false; }
    else {
      e.obj.userData.lab.visible = true;
      if (!e.trusted) { const j = Math.floor(G.time * 20 + e.s) % 7 === 0; g.scale.set(j ? 1.3 : 1, j ? 0.7 : 1, 1); e.obj.userData.lab.position.x = j ? 0.08 : 0; }
    }
    e.obj.userData.lab.quaternion.copy(camera.quaternion);
  }
  floor.position.set(0, 0, -s0 - 120);
  floorU.uCat.value.set(G.x, -G.dist, 0.75 / (1 + G.y * 0.35));
  floorU.uPulse.value = Math.exp(-((G.time * 2) % 1) * 4) * (G.mode === 'run' ? 1 : 0.3);
}

function updateCamera(dt) {
  const want = G.mode === 'run' || G.mode === 'paused' ? 1 : G.mode === 'dying' ? 0.6 : 0;
  G.camBlend += (want - G.camBlend) * Math.min(1, dt * (want > G.camBlend ? 2.2 : 0.9));
  const u = G.camBlend * G.camBlend * (3 - 2 * G.camBlend);
  const wide = camera.aspect > 1.05;
  // portrait screens sit higher and further back so the track ahead stays visible over the cat
  const chaseP = wide ? V(G.x * 0.55, 4.0 + G.y * 0.35, -G.dist + 8.4) : V(G.x * 0.45, 5.8 + G.y * 0.3, -G.dist + 11.5);
  const chaseL = wide ? V(G.x * 0.75, 1.9 + G.y * 0.3, -G.dist - 9) : V(G.x * 0.6, 1.0 + G.y * 0.25, -G.dist - 12);
  // during a belly slide the camera dips and swings a little so the dive reads in profile
  const st = slideTilt();
  G.camSlide = (G.camSlide || 0) + (st - (G.camSlide || 0)) * Math.min(1, dt * 7);
  chaseP.x += 2.4 * G.camSlide; chaseP.y -= 1.5 * G.camSlide; chaseP.z -= 1.2 * G.camSlide; chaseL.y -= 0.7 * G.camSlide;
  const frontP = wide ? V(G.x - 0.2, 2.35, -G.dist - 9.2) : V(G.x, 2.6, -G.dist - 11.5);
  const frontL = wide ? V(G.x + 2.4, 2.0, -G.dist) : V(G.x, 0.9, -G.dist);
  const p = frontP.lerp(chaseP, u), l = frontL.lerp(chaseL, u);
  const sh = G.shake;
  p.x += (Math.random() - 0.5) * sh; p.y += (Math.random() - 0.5) * sh;
  if (TEST && window.__sideCam) { p.set(G.x + 9, 1.8, -G.dist - 1.2); l.set(G.x, 1.4, -G.dist - 1.2); }
  camera.position.copy(p);
  camera.lookAt(l);
  const fov = (wide ? 34 : 46) + u * ((wide ? 58 : 66) - (wide ? 34 : 46) + clamp((G.speed - 13) / 16, 0, 1) * 9);
  if (Math.abs(camera.fov - fov) > 0.01) { camera.fov = fov; camera.updateProjectionMatrix(); }
  sky.position.copy(camera.position);
}

function render() {
  if (pipe) pipe.render(); else renderer.render(scene, camera);
}

// adaptive quality: step down once if frames run long
let perf = { n: 0, sum: 0 };
function watchPerf(ms) {
  if (TEST || G.mode !== 'run' || level === 0 || G.runTime < 2) return;
  perf.n++; perf.sum += ms;
  if (perf.n >= 120) {
    if (perf.sum / perf.n > 24) { level--; resize(); }
    perf = { n: 0, sum: 0 };
  }
}

let last = performance.now();
function frame(now) {
  const ms = now - last; last = now;
  const dt = Math.min(0.05, ms / 1000);
  if (G.mode !== 'paused') step(dt);
  animateCat(dt);
  updateWorld();
  updateCamera(dt);
  if (G.mode === 'run' || G.mode === 'paused') updateHUD();
  render();
  watchPerf(ms);
  requestAnimationFrame(frame);
}

// ---------------------------------------------------------------- leaderboard (shared when the viewer allows it)
const LB = { db: null, user: null, uid: null, rows: [], state: 'loading', myBest: 0, canPost: true, renderSeq: 0 };
function localBest() { try { return Number(localStorage.getItem('tr-best')) || 0; } catch (e) { return 0; } }
function saveLocalBest(v) { try { localStorage.setItem('tr-best', String(v)); } catch (e) { /* storage blocked */ } }
async function initLeaderboard() {
  renderBoards();
  const use = window.claude && window.claude.use ? (n) => window.claude.use(n) : null;
  if (!use) { LB.state = 'local'; renderBoards(); return; }
  const [db, user] = await Promise.all([use('db'), use('user')]);
  if (!db) { LB.state = 'local'; renderBoards(); return; }
  LB.db = db; LB.user = user;
  LB.uid = user ? await user.id() : null;
  db.collection('scores').orderBy('best', 'desc').limit(10).onSnapshot((snap) => {
    LB.rows = snap.docs.map((d) => ({ id: d.id, best: clampScore(d.data().best) })).filter((r) => r.best > 0);
    LB.state = 'ok';
    const me = LB.rows.find((r) => r.id === LB.uid);
    if (me) LB.myBest = Math.max(LB.myBest, me.best);
    renderBoards();
  }, () => { LB.state = 'local'; renderBoards(); });
  if (LB.uid) {
    try { const d = await db.doc('scores/' + LB.uid).get(); if (d.exists) LB.myBest = clampScore(d.data().best); } catch (e) { /* read failed; board still works */ }
  }
}
const clampScore = (v) => (Number.isFinite(v) ? Math.max(0, Math.min(9999999, Math.floor(v))) : 0);
async function postScore(score) {
  if (!LB.db || !LB.uid || !LB.canPost || score <= LB.myBest) return;
  try {
    await LB.db.doc('scores/' + LB.uid).set({ best: score, at: Date.now() });
    LB.myBest = score;
    renderBoards();
  } catch (e) {
    LB.canPost = false;
    renderBoards();
  }
}
async function renderBoards() {
  const seq = ++LB.renderSeq;
  let names = {};
  if (LB.state === 'ok' && LB.user && LB.rows.length) {
    try { names = await LB.user.profiles(LB.rows.map((r) => r.id)); } catch (e) { names = {}; }
  }
  if (seq !== LB.renderSeq) return;
  for (const id of ['boardStart', 'boardOver']) {
    const box = $(id);
    box.textContent = '';
    const head = document.createElement('div'); head.className = 'board-head';
    const h = document.createElement('h2'); h.textContent = 'Top runners';
    const mine = document.createElement('span'); mine.className = 'dist';
    mine.textContent = `Your best ${Math.max(localBest(), LB.myBest).toLocaleString('en-US')}`;
    head.append(h, mine);
    box.append(head);
    const note = (t) => { const p = document.createElement('p'); p.className = 'note'; p.textContent = t; box.append(p); };
    if (LB.state === 'loading') { note('Loading scores…'); continue; }
    if (LB.state === 'local') { note('Your best score is kept on this device.'); continue; }
    if (!LB.rows.length) { note('No runs yet. Finish a run to post the first score.'); }
    else {
      const ol = document.createElement('ol');
      LB.rows.forEach((r, i) => {
        const li = document.createElement('li');
        if (r.id === LB.uid) li.className = 'me';
        const rank = document.createElement('span'); rank.className = 'rank'; rank.textContent = String(i + 1);
        const who = document.createElement('span'); who.className = 'who';
        const p = names[r.id];
        if (p && p.avatarUrl) { const img = document.createElement('img'); img.src = p.avatarUrl; img.alt = ''; who.append(img); }
        const nm = document.createElement('span'); nm.textContent = (p && p.name) || (r.id === LB.uid ? 'You' : 'Runner'); who.append(nm);
        const pts = document.createElement('span'); pts.className = 'pts'; pts.textContent = r.best.toLocaleString('en-US');
        li.append(rank, who, pts);
        ol.append(li);
      });
      box.append(ol);
    }
    if (!LB.canPost) note('This link is view-only, so your score stays on this device.');
  }
}

// ---------------------------------------------------------------- boot
async function boot() {
  try {
    await Promise.race([
      Promise.all(['900 40px Unbounded', '800 40px Manrope', '600 20px "IBM Plex Mono"'].map((f) => document.fonts.load(f))),
      new Promise((r) => setTimeout(r, 2500)),
    ]);
  } catch (e) { /* fonts fall back */ }
  buildTextures();
  buildMaterials();
  resize();
  window.addEventListener('resize', resize);
  resetRun();
  G.mode = 'menu';
  updateWorld();
  setSoundIcon();
  $('loading').hidden = true;
  show('startMenu');
  initLeaderboard();
  if (TEST) {
    // test autopilot: dodges, collects trusted evidence, answers gates (skill 0..1 = chance to read a gate right)
    const pilot = (skill) => {
      if (G.mode !== 'run') return;
      const ahead = ents.filter((e) => !e.done && e.s > G.dist - 0.3).sort((a, b) => a.s - b.s);
      const gate = ahead.find((e) => e.kind === 'gate' && e.s - G.dist < G.speed * 1.6);
      if (gate) {
        if (gate.want === undefined) { const R = gate.round; gate.want = rnd() < skill ? R.truth : 1 - R.truth; }
        const lane = gate.round.order[0] === gate.want ? 0 : 2;
        if (G.lane !== lane && Math.abs(G.x - LANE_X[G.lane]) < 0.3) move(lane > G.lane ? 1 : -1);
        return;
      }
      const obs = ahead.filter((e) => e.kind !== 'shard' && e.kind !== 'gate');
      if (!obs.length) return;
      const rowS = obs[0].s, dz = rowS - G.dist;
      const row = obs.filter((e) => Math.abs(e.s - rowS) < 0.5);
      const at = (l) => row.find((e) => e.lane === l);
      const cost = (l) => { const o = at(l); return !o ? 0 : o.kind === 'block' ? 9 : 1; };
      // best lane for this row: free beats jump/slide beats block, then fewer moves
      const best = [0, 1, 2].sort((p, q) => cost(p) - cost(q) || Math.abs(p - G.lane) - Math.abs(q - G.lane))[0];
      if (best !== G.lane && cost(best) < cost(G.lane) && Math.abs(G.x - LANE_X[G.lane]) < 0.3) { move(best > G.lane ? 1 : -1); return; }
      const o = at(G.lane);
      if (o && Math.abs(G.x - LANE_X[G.lane]) < 0.6) {
        if (o.kind === 'low' && dz < G.speed * 0.2 + 1) jump();
        else if (o.kind === 'high' && dz < G.speed * 0.15 + 1.4 && G.slide < 0.25) slide();
        return;
      }
      if (dz > G.speed * 0.5) {
        const goal = ahead.find((e) => e.kind === 'shard' && e.trusted && e.s < rowS);
        const path = goal && Math.abs(goal.lane - G.lane) === 1 && cost(goal.lane) === 0;
        if (path && Math.abs(G.x - LANE_X[G.lane]) < 0.3) move(goal.lane > G.lane ? 1 : -1);
      }
    };
    window.__tr = {
      G, ents, startRun, move, jump, slide, hitLog, claimLog, spawn, slideTilt,
      tick(seconds, fps = 30, skill = -1) { for (let i = 0; i < seconds * fps; i++) { const dt = 1 / fps; if (skill >= 0) pilot(skill); if (G.mode !== 'paused') step(dt); animateCat(dt); updateWorld(); updateCamera(dt); } updateHUD(); render(); },
      shot() { return canvas.toDataURL('image/jpeg', 0.85); },
    };
    animateCat(0); updateCamera(1); render();
    window.__ready = true;
  } else {
    requestAnimationFrame((t) => { last = t; frame(t); });
  }
}
boot();
