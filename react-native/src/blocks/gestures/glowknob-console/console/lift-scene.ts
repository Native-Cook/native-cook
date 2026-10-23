import * as THREE from "three/webgpu";
import {
  float,
  texture,
  uniform,
  uv,
  vec2,
  vec3,
  vec4,
} from "three/tsl";
import {
  Skia,
  type SkCanvas,
  type SkPicture,
  type SkSize,
} from "@shopify/react-native-skia";
import { makeSkiaTexture, type SkiaTexture } from "../gpu/skia-texture";
import { makeStudio, type StudioOptions } from "../gpu/studio";
import type {
  OnDemandScene,
  SceneDevice,
} from "../gpu/use-on-demand-three-scene";
import { referenceGeometry as g } from "./reference-motion";
import { paintConsoleBack, paintKnurl } from "./console-back";

export type Box = { x: number; y: number; width: number; height: number };

/** The console's light as a scene light: its leading edge (drawing units),
 * linear RGB color and strength (0…1). */
export type Glow = {
  tip: number;
  color: [number, number, number];
  power: number;
};

/** JS-side link between the lift choreography and the scene. */
export class LiftLink {
  /** While false the scene neither reads the pose nor renders. */
  live = false;
  /** A rendered frame exists (the renderer is up). */
  ready = false;
  /** A freshly recorded face to paint before the next render. */
  face: SkPicture | null = null;
  private handlers = { face: () => {}, render: () => {} };
  // [lift, yaw, pitch, tiltX, tiltY], pushed from the UI thread every
  // frame it changes. Reading the shared values from JS instead returns
  // stale copies, refreshed only every few frames: the object stepped.
  private latest: readonly number[] = [0, 0, 0, 0, 0];
  setPose(pose: readonly number[]) {
    this.latest = pose;
  }
  pose() {
    return this.latest;
  }
  /** Shows `picture` on the object's face and starts following the pose. */
  /** The console's own light at the moment it was picked up. */
  light: Glow | null = null;
  start(picture: SkPicture, light: Glow | null = null) {
    this.face = picture;
    this.light = light;
    this.live = true;
  }
  stop() {
    this.live = false;
  }
  takeFace() {
    const face = this.face;
    this.face = null;
    return face;
  }
  on(handlers: { face: () => void; render: () => void }) {
    this.handlers = handlers;
  }
  /** After a render; `revealed` when it is the first with a new face. */
  rendered(revealed: boolean) {
    this.ready = true;
    this.handlers.render();
    if (revealed) this.handlers.face();
  }
}

// Drawing units, as DeviceArt (housing 1000 × 284).
const ART_W = 1000,
  ART_H = 284;
// Body thickness, edge chamfer and knob height, in drawing units.
const DEPTH = 62,
  BEVEL = 7,
  KNOB = 22;
const FOV = 30;
// How far the console comes toward the viewer, as a fraction of the camera
// distance (about +12% apparent size), and its presenting tilt: the top edge
// turns toward the viewer.
const LIFT_Z = 0.11;
const LIFT_Y = 14;
const REST_PITCH = 0.14;
// Peak intensity of the track's light inside the scene (candela-ish, dp).
const LAMP = 3.2e4;

// Skia writes sRGB-encoded pixels into a non-sRGB texture format: decode.
type Sampled = ReturnType<typeof texture>;
type Rgba = ReturnType<typeof vec4>;
const decode = (node: Sampled) =>
  (node.colorSpaceToWorking(THREE.SRGBColorSpace) as unknown as Rgba).xyz;

function capsule(width: number, height: number) {
  const r = height / 2,
    x = width / 2 - r;
  const shape = new THREE.Shape();
  shape.moveTo(-x, -r);
  shape.lineTo(x, -r);
  shape.absarc(x, 0, r, -Math.PI / 2, Math.PI / 2, false);
  shape.lineTo(-x, r);
  shape.absarc(-x, 0, r, Math.PI / 2, (Math.PI * 3) / 2, false);
  return shape;
}

// UVs in face space for every vertex in `ranges` of an indexed or non-indexed
// geometry: (0,0) is the housing's top-left corner, (1,1) its bottom-right.
function faceUV(
  geometry: THREE.BufferGeometry,
  width: number,
  height: number,
  ranges?: { start: number; count: number }[],
) {
  const pos = geometry.getAttribute("position");
  const uvs = geometry.getAttribute("uv") as THREE.BufferAttribute;
  const index = geometry.getIndex();
  const set = (v: number) =>
    uvs.setXY(v, pos.getX(v) / width + 0.5, 0.5 - pos.getY(v) / height);
  if (!ranges) for (let v = 0; v < pos.count; v++) set(v);
  else
    for (const { start, count } of ranges)
      for (let i = start; i < start + count; i++)
        set(index ? index.getX(i) : i);
  uvs.needsUpdate = true;
}

// A dark studio over a white table: the floor bounce below gives the lower
// chamfer its rim, a soft box above and two strips draw the moving
// highlights across the glass and the metal.
const STUDIO: StudioOptions = {
  floor: [0.36, 0.355, 0.35],
  sky: [0.03, 0.03, 0.035],
  boxes: [
    { size: [16, 5], at: [0, 12, 10], power: 5 },
    { size: [2.4, 13], at: [-14, 3, 9], power: 3.2 },
    { size: [2.4, 15], at: [13, 2, -7], power: 2.4 },
  ],
};

export function makeLiftScene(
  renderer: THREE.WebGPURenderer,
  sizePx: SkSize,
  canvas: SkSize,
  housing: Box,
  link: LiftLink,
  { device, shared }: SceneDevice,
): OnDemandScene {
  renderer.setClearColor(0x000000, 0);
  const pixelRatio = sizePx.width / canvas.width;

  // World units are dp; the z = 0 plane maps 1:1 to the canvas.
  const scene = new THREE.Scene();
  const distance =
    canvas.height / 2 / Math.tan(THREE.MathUtils.degToRad(FOV / 2));
  const camera = new THREE.PerspectiveCamera(
    FOV,
    canvas.width / canvas.height,
    distance * 0.2,
    distance * 3,
  );
  camera.position.set(0, 0, distance);

  const s = housing.width / ART_W;
  const w = housing.width,
    h = ART_H * s;
  const depth = DEPTH * s,
    bevel = BEVEL * s;
  const front = depth / 2 + bevel;

  // Face textures sized to their largest on-screen footprint (lifted) so
  // they are sampled about 1:1: external textures have no mipmaps.
  const faceW = Math.min(2048, Math.round(w * pixelRatio * 1.2));
  const faceH = Math.round((faceW * ART_H) / ART_W);
  const faceTex = makeSkiaTexture(device, faceW, faceH, shared);
  const backTex = makeSkiaTexture(device, faceW, faceH, shared);
  const knurlTex = makeSkiaTexture(device, 256, 64, shared);
  knurlTex.texture.wrapS = THREE.RepeatWrapping;
  const paint = (tex: SkiaTexture, draw: (c: SkCanvas) => void) =>
    tex.draw((c) => {
      c.clear(Skia.Color("transparent"));
      c.save();
      c.scale(faceW / ART_W, faceH / ART_H);
      draw(c);
      c.restore();
    });
  paint(backTex, paintConsoleBack);
  knurlTex.draw((c) => paintKnurl(c, 256, 64));

  // Glass-coated face: the Skia artwork is emitted as is (it carries its own
  // painted light), and a clear coat adds the studio's real reflections.
  // Reflections grow with the lift: resting on the stage the face is the 2D
  // drawing pixel for pixel, so the handoff is invisible.
  const gloss = uniform(0);
  const faceMat = new THREE.MeshPhysicalNodeMaterial({
    roughness: 0.45,
    metalness: 0,
    clearcoatRoughness: 0.07,
  });
  faceMat.colorNode = vec3(0, 0, 0);
  faceMat.clearcoatNode = gloss.mul(0.8);
  faceMat.specularIntensityNode = gloss;
  faceMat.emissiveNode = decode(texture(faceTex.texture));
  // Anodized aluminium chamfer and walls.
  const bodyMat = new THREE.MeshPhysicalNodeMaterial({
    color: 0x55555a,
    metalness: 1,
    roughness: 0.3,
  });
  // The back is seen after a half turn about the vertical axis: mirror u.
  const backMat = new THREE.MeshPhysicalNodeMaterial({
    metalness: 0.35,
    roughness: 0.4,
  });
  backMat.colorNode = decode(
    texture(backTex.texture, vec2(float(1).sub(uv().x), uv().y)),
  );
  // Brushed rather than polished: the light's warmth spreads across the
  // flank instead of glinting off a few ridges.
  const knurlMat = new THREE.MeshPhysicalNodeMaterial({
    metalness: 0.55,
    roughness: 0.7,
  });
  knurlMat.colorNode = decode(texture(knurlTex.texture, uv().mul(vec2(3, 1))));

  const body = new THREE.ExtrudeGeometry(
    capsule(w - 2 * bevel, h - 2 * bevel),
    {
      depth,
      bevelEnabled: true,
      bevelThickness: bevel,
      bevelSize: bevel,
      bevelSegments: 6,
      curveSegments: 64,
    },
  );
  body.translate(0, 0, -depth / 2);
  faceUV(body, w, h);
  // ExtrudeGeometry puts both lids in group 0 (back lid first, then front)
  // and the walls in group 1. Split the lids: 0 face, 1 walls, 2 back.
  const lids = body.groups[0],
    walls = body.groups[1];
  const lid = lids.count / 2;
  body.clearGroups();
  body.addGroup(lids.start, lid, 2);
  body.addGroup(lids.start + lid, lid, 0);
  body.addGroup(walls.start, walls.count, 1);

  // The knob stands proud of the face; its top shows the knob artwork.
  const knobR = 100 * s,
    knobH = KNOB * s;
  const knob = new THREE.CylinderGeometry(knobR, knobR, knobH, 96, 1);
  knob.rotateX(Math.PI / 2);
  knob.translate(
    (g.dial.x - ART_W / 2) * s,
    (ART_H / 2 - g.dial.y) * s,
    front + knobH / 2,
  );
  faceUV(
    knob,
    w,
    h,
    knob.groups.filter((group) => group.materialIndex !== 0),
  );

  const console3d = new THREE.Group();
  console3d.add(new THREE.Mesh(body, [faceMat, bodyMat, backMat]));
  console3d.add(new THREE.Mesh(knob, [knurlMat, faceMat, bodyMat]));
  // The light in the track, as a light: it warms the knob's knurled flank and
  // the chamfer beside it. It sits just under the face so the glass itself
  // takes no hot spot (the artwork already paints the light). Set from the
  // picked-up state; off until then.
  // Short reach: it warms what is beside the light, never the far edges.
  const lamp = new THREE.PointLight(0xffffff, 0, w * 0.5, 2);
  console3d.add(lamp);
  scene.add(console3d);
  const home = new THREE.Vector3(
    housing.x + w / 2 - canvas.width / 2,
    canvas.height / 2 - (housing.y + h / 2),
    -front,
  );
  console3d.position.copy(home);

  let env: THREE.RenderTarget | null = null;
  let dirty = true;
  let reveal = false;
  let last = "";

  return {
    scene,
    camera,
    update: () => {
      if (!env) {
        env = makeStudio(renderer, STUDIO);
        scene.environment = env.texture;
        dirty = true;
      }
      const picture = link.takeFace();
      if (picture) {
        paint(faceTex, (c) => c.drawPicture(picture));
        picture.dispose();
        const glow = link.light;
        lamp.intensity = glow ? glow.power * LAMP : 0;
        if (glow) {
          lamp.color.setRGB(...glow.color);
          lamp.position.set(
            (glow.tip - 42 - ART_W / 2) * s,
            (ART_H / 2 - (g.track.y + g.track.height / 2)) * s,
            front - bevel * 0.5,
          );
        }
        reveal = true;
        dirty = true;
      }
      if (link.live) {
        const [lift, yaw, pitch, tx, ty] = link.pose();
        const key = `${lift.toFixed(4)} ${yaw.toFixed(4)} ${pitch.toFixed(4)} ${tx.toFixed(3)} ${ty.toFixed(3)}`;
        if (key !== last) {
          last = key;
          gloss.value = Math.min(1, lift * 1.4);
          console3d.position.set(
            home.x,
            home.y + LIFT_Y * lift,
            home.z + distance * LIFT_Z * lift,
          );
          console3d.rotation.set(
            pitch + (REST_PITCH + ty * 0.12) * lift,
            yaw + tx * 0.16 * lift,
            0,
          );
          dirty = true;
        }
      }
      const render = dirty;
      dirty = false;
      return render;
    },
    afterRender: () => {
      link.rendered(reveal);
      reveal = false;
    },
    dispose: () => {
      body.dispose();
      knob.dispose();
      for (const m of [faceMat, bodyMat, backMat, knurlMat]) m.dispose();
      for (const t of [faceTex, backTex, knurlTex]) t.destroy();
      env?.dispose();
    },
  };
}
