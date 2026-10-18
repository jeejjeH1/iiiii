// Render pipeline: colour pass (MSAA) -> ink lines from an id/normal/depth pass -> bloom -> output.
// The ink pass reproduces the sheet's black line-art on the 3D model: lines appear on silhouettes,
// between different parts (mesh ids), on sharp creases and on depth jumps.
import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { SMAAPass } from 'three/addons/postprocessing/SMAAPass.js';

const InkShader = {
  uniforms: {
    tDiffuse: { value: null }, tId: { value: null }, tDepth: { value: null },
    res: { value: new THREE.Vector2() }, width: { value: 1.6 }, strength: { value: 1 },
    lineColor: { value: new THREE.Color('#07060c') },
    cameraNear: { value: 0.1 }, cameraFar: { value: 100 }, isOrtho: { value: 0 },
    creaseCos: { value: 0.45 }, depthRel: { value: 0.035 },
  },
  vertexShader: /* glsl */`varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: /* glsl */`
    uniform sampler2D tDiffuse, tId, tDepth;
    uniform vec2 res; uniform float width, strength, cameraNear, cameraFar, isOrtho, creaseCos, depthRel;
    uniform vec3 lineColor;
    varying vec2 vUv;
    float lin(vec2 uv){
      float d = texture2D(tDepth, uv).x;
      if (isOrtho > 0.5) return cameraNear + d * (cameraFar - cameraNear);
      float z = d * 2.0 - 1.0;
      return 2.0 * cameraNear * cameraFar / (cameraFar + cameraNear - z * (cameraFar - cameraNear));
    }
    // 8 samples on the outer ring + 4 diagonals at half radius
    const vec2 OFF[12] = vec2[12](
      vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
      vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071),
      vec2(0.3536, 0.3536), vec2(-0.3536, 0.3536), vec2(-0.3536, -0.3536), vec2(0.3536, -0.3536));
    void main(){
      vec4 base = texture2D(tDiffuse, vUv);
      vec4 c = texture2D(tId, vUv);
      float idc = floor(c.a * 255.0 + 0.5);
      vec3 nc = c.rgb * 2.0 - 1.0;
      float dc = idc > 0.5 ? lin(vUv) : 0.0;
      float acc = 0.0;
      for (int i = 0; i < 12; i++) {
        vec2 uv = vUv + OFF[i] * width / res;
        vec4 s = texture2D(tId, uv);
        float ids = floor(s.a * 255.0 + 0.5);
        if (ids != idc) { acc += 1.0; continue; }
        if (idc < 0.5) continue;
        if (dot(s.rgb * 2.0 - 1.0, nc) < creaseCos) { acc += 1.0; continue; }
        float ds = lin(uv);
        if (abs(ds - dc) > depthRel * min(ds, dc)) acc += 1.0;
      }
      float edge = smoothstep(0.0, 0.3, acc / 12.0);
      gl_FragColor = vec4(mix(base.rgb, lineColor, edge * strength), base.a);
    }`,
};

// final look (replaces OutputPass): neutral tone mapping + sRGB, then chromatic aberration,
// vignette, film grain and fade, all in one full-screen pass
const GradeShader = {
  defines: { NEUTRAL_TONE_MAPPING: '', SRGB_TRANSFER: '' },
  uniforms: { tDiffuse: { value: null }, toneMappingExposure: { value: 1 }, time: { value: 0 }, ca: { value: 0.0018 }, vignette: { value: 0.32 }, grain: { value: 0.035 }, fade: { value: 0 }, res: { value: new THREE.Vector2() } },
  vertexShader: InkShader.vertexShader,
  fragmentShader: /* glsl */`
    // ShaderMaterial already prepends the tone-mapping / colour-space chunks
    uniform sampler2D tDiffuse; uniform float time, ca, vignette, grain, fade; uniform vec2 res;
    varying vec2 vUv;
    float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
    void main(){
      vec2 d = vUv - 0.5;
      float r2 = dot(d, d);
      vec2 o = d * ca * (1.0 + 8.0 * r2);
      vec3 c = vec3(texture2D(tDiffuse, vUv + o).r, texture2D(tDiffuse, vUv).g, texture2D(tDiffuse, vUv - o).b);
      c = NeutralToneMapping(c);
      c = sRGBTransferOETF(vec4(c, 1.0)).rgb;
      c *= 1.0 - vignette * smoothstep(0.08, 0.62, r2 * 1.6);
      float n = h(vUv * res + fract(time * 7.13) * 91.7) - 0.5;
      c += n * grain;
      c *= 1.0 - fade;
      gl_FragColor = vec4(c, 1.0);
    }`,
};

export class ToonPipeline {
  constructor(renderer, scene, camera, { width, height, lineWidth = 1.6, bloom = { strength: 0.55, radius: 0.35, threshold: 1.3 }, grade = false, smaa = true, samples = 4 } = {}) {
    this.renderer = renderer; this.scene = scene; this.camera = camera;
    this.idRT = new THREE.WebGLRenderTarget(width, height, { type: THREE.UnsignedByteType, depthBuffer: true });
    this.idRT.depthTexture = new THREE.DepthTexture(width, height);
    this.idRT.depthTexture.type = THREE.UnsignedIntType;
    const rt = new THREE.WebGLRenderTarget(width, height, { type: THREE.HalfFloatType, samples });
    this.composer = new EffectComposer(renderer, rt);
    this.composer.setSize(width, height);
    this.renderPass = new RenderPass(scene, camera);
    this.composer.addPass(this.renderPass);
    this.ink = new ShaderPass(InkShader);
    this.ink.uniforms.tId.value = this.idRT.texture;
    this.ink.uniforms.tDepth.value = this.idRT.depthTexture;
    this.ink.uniforms.res.value.set(width, height);
    this.ink.uniforms.width.value = lineWidth;
    this.composer.addPass(this.ink);
    this.bloom = new UnrealBloomPass(new THREE.Vector2(width, height), bloom.strength, bloom.radius, bloom.threshold);
    this.composer.addPass(this.bloom);
    if (grade) {
      this.grade = new ShaderPass(GradeShader);
      this.grade.uniforms.res.value.set(width, height);
      this.composer.addPass(this.grade);
    } else {
      this.composer.addPass(new OutputPass());
      if (smaa) this.composer.addPass(new SMAAPass(width, height));
    }
    this.idMats = new Map();
    this.clear = new THREE.Color(0, 0, 0);
  }
  setCamera(cam) { this.camera = cam; this.renderPass.camera = cam; }
  idMaterial(mesh) {
    const key = mesh.userData.lineId + (mesh.material.side === THREE.DoubleSide ? 1000 : 0) + (mesh.userData.noCrease ? 2000 : 0);
    let m = this.idMats.get(key);
    if (!m) {
      m = new THREE.ShaderMaterial({
        uniforms: { uId: { value: mesh.userData.lineId / 255 }, uFlat: { value: mesh.userData.noCrease ? 1 : 0 } },
        vertexShader: `varying vec3 vN; void main(){ vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
        fragmentShader: `uniform float uId, uFlat; varying vec3 vN; void main(){ vec3 n = normalize(vN); if (!gl_FrontFacing) n = -n; if (uFlat > 0.5) n = vec3(0.0, 0.0, 1.0); gl_FragColor = vec4(n * 0.5 + 0.5, uId); }`,
        side: mesh.material.side,
      });
      this.idMats.set(key, m);
    }
    return m;
  }
  render() {
    const { renderer, scene, camera } = this;
    // id pass: swap materials, hide anything without a line id
    const swapped = [], hidden = [];
    scene.traverseVisible((o) => {
      if (!o.isMesh && !o.isPoints && !o.isSprite && !o.isLine) return;
      if (o.isMesh && o.userData.lineId !== undefined) { swapped.push([o, o.material]); o.material = this.idMaterial(o); }
      else hidden.push(o);
    });
    hidden.forEach((o) => (o.visible = false));
    const bg = scene.background, fog = scene.fog;
    scene.background = null; scene.fog = null;
    const oldClear = renderer.getClearColor(new THREE.Color()), oldA = renderer.getClearAlpha();
    renderer.setRenderTarget(this.idRT);
    renderer.setClearColor(this.clear, 0);
    renderer.clear();
    renderer.render(scene, camera);
    renderer.setRenderTarget(null);
    renderer.setClearColor(oldClear, oldA);
    scene.background = bg; scene.fog = fog;
    swapped.forEach(([o, m]) => (o.material = m));
    hidden.forEach((o) => (o.visible = true));
    const u = this.ink.uniforms;
    u.cameraNear.value = camera.near; u.cameraFar.value = camera.far; u.isOrtho.value = camera.isOrthographicCamera ? 1 : 0;
    if (this.grade) this.grade.uniforms.toneMappingExposure.value = renderer.toneMappingExposure;
    this.composer.render();
  }
}
