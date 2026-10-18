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
    void main(){
      vec4 base = texture2D(tDiffuse, vUv);
      vec4 c = texture2D(tId, vUv);
      float idc = floor(c.a * 255.0 + 0.5);
      float flagC = c.b > 0.999 ? 1.0 : 0.0; // reserved
      vec3 nc = c.rgb * 2.0 - 1.0;
      float dc = lin(vUv);
      float acc = 0.0;
      const int N = 16;
      for (int ring = 0; ring < 2; ring++) {
        float rad = width * (ring == 0 ? 1.0 : 0.55);
        for (int i = 0; i < N; i++) {
          float a = 6.2831853 * (float(i) + 0.5 * float(ring)) / float(N);
          vec2 uv = vUv + vec2(cos(a), sin(a)) * rad / res;
          vec4 s = texture2D(tId, uv);
          float ids = floor(s.a * 255.0 + 0.5);
          float e = 0.0;
          if (ids != idc) {
            e = 1.0;
          } else if (idc > 0.5) {
            vec3 ns = s.rgb * 2.0 - 1.0;
            if (dot(ns, nc) < creaseCos) e = 1.0;
            float ds = lin(uv);
            if (abs(ds - dc) > depthRel * min(ds, dc)) e = 1.0;
          }
          acc += e;
        }
      }
      float edge = smoothstep(0.0, 0.3, acc / float(2 * N));
      gl_FragColor = vec4(mix(base.rgb, lineColor, edge * strength), base.a);
    }`,
};

export class ToonPipeline {
  constructor(renderer, scene, camera, { width, height, lineWidth = 1.6, bloom = { strength: 0.55, radius: 0.35, threshold: 1.3 } } = {}) {
    this.renderer = renderer; this.scene = scene; this.camera = camera;
    this.idRT = new THREE.WebGLRenderTarget(width, height, { type: THREE.UnsignedByteType, depthBuffer: true });
    this.idRT.depthTexture = new THREE.DepthTexture(width, height);
    this.idRT.depthTexture.type = THREE.UnsignedIntType;
    const rt = new THREE.WebGLRenderTarget(width, height, { type: THREE.HalfFloatType, samples: 4 });
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
    this.composer.addPass(new OutputPass());
    this.smaa = new SMAAPass(width, height);
    this.composer.addPass(this.smaa);
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
    this.composer.render();
  }
}
