import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, type SkCanvas, type SkImage, Skia, TileMode, VertexMode } from '@shopify/react-native-skia';

import { CLOTH_LEN, type ClothPose, DEPTH, DROP, LOOP, clamp } from './clothMotion';
import { fill, grain, linear, radial, rgba, stroke } from './clothPaint';

/*
 The supper table in 3D and the cloth as a mesh. World: x right, y away from the viewer across the
 table (front edge y = 0, back edge y = DEPTH), z up (table top z = 0). The camera looks down at
 46° from the front.

 The cloth is a grid in its own material coordinates: u across (CLOTH_W wide), s along its length
 from the back edge (0) to the hem (CLOTH_LEN). Pulled by d, a material point sits at p = s + d
 along its path: on the table while p ≤ DEPTH, then over the front edge and down (streaming toward
 the viewer by `fling`). A travelling ripple lifts the part on the table. The linen is a texture
 baked once (weave, a windowpane check, a terracotta hem stripe); a second mesh with colours only
 shades the folds and the hanging part (no vertex colours on the textured mesh).
 */

export const CLOTH_W = 460;
const COLS = 40;
const ROWS = 34;
export const TEX_SCALE = 1.5;
const ELEV = (46 * Math.PI) / 180;
const SIN_E = Math.sin(ELEV);
const COS_E = Math.cos(ELEV);
const CAM_D = 1000;
const TARGET_Y = DEPTH * 0.42;
/** Overall zoom of the view (the table runs off both sides of the screen). */
const ZOOM = 1.2;

export type Geo = { w: number; h: number; cx: number; cy: number; panelTop: number; headerBottom: number };

/** Screen point and perspective scale of a world point. */
export function project(g: { cx: number; cy: number }, x: number, y: number, z: number): [number, number, number] {
  'worklet';
  const ry = y - TARGET_Y;
  const yc = ry * SIN_E + z * COS_E;
  const s = (ZOOM * CAM_D) / (CAM_D + ry * COS_E - z * SIN_E);
  return [g.cx + x * s, g.cy - yc * s, s];
}

/** An affine matrix drawing the table plane at height z around (x, y) in world units. */
export function planeMatrix(g: { cx: number; cy: number }, x: number, y: number, z: number) {
  'worklet';
  const p = project(g, x, y, z);
  const px = project(g, x + 1, y, z);
  const py = project(g, x, y + 1, z);
  return Skia.Matrix([px[0] - p[0], py[0] - p[0], p[0], px[1] - p[1], py[1] - p[1], p[1], 0, 0, 1]);
}

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const panelTop = h - Math.max(bottom, 12) - 150;
  const frontY = panelTop - Math.min(140, h * 0.17);
  const off = project({ cx: 0, cy: 0 }, 0, 0, 0)[1];
  return { w, h, cx: w / 2, cy: frontY - off, panelTop, headerBottom: top + 112 };
}

// ——— Baked ———

function worldQuad(g: Geo, pts: number[][]) {
  const b = Skia.PathBuilder.Make();
  pts.forEach((q, i) => {
    const s = project(g, q[0], q[1], q[2]);
    if (i === 0) b.moveTo(s[0], s[1]);
    else b.lineTo(s[0], s[1]);
  });
  return b.close().build();
}

/** Wall, the walnut table (top and apron), the floor. */
export function drawRoom(canvas: SkCanvas, g: Geo) {
  const W = 900;
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [rgba(0x2a1d1a), rgba(0x22171a), rgba(0x140d0c)], [0, 0.5, 1]));
  // Damask-ish wall: soft vertical panels and a warm pool of candlelight.
  for (let i = 0; i < 6; i += 1) canvas.drawRect(Skia.XYWHRect((i * g.w) / 5 - 4, 0, 2, g.h), fill(0x000000, 0.12));
  const back = project(g, 0, DEPTH, 0);
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, back[1] - 30, g.w * 0.9, [rgba(0xb0703a, 0.32), rgba(0x6a3a22, 0.12), rgba(0x000000, 0)], [0, 0.5, 1]));
  // Floor under the table.
  const front = project(g, 0, 0, 0);
  canvas.drawRect(Skia.XYWHRect(0, front[1], g.w, g.h - front[1]), linear(0, front[1], 0, g.h, [rgba(0x1a100c), rgba(0x22150f), rgba(0x120b08)], [0, 0.4, 1]));
  // Apron: the table's front face, then the top.
  canvas.drawPath(worldQuad(g, [[-W, 0, 0], [W, 0, 0], [W, 0, -58], [-W, 0, -58]]), linear(0, front[1], 0, front[1] + 50, [rgba(0x4a2614), rgba(0x341a0d), rgba(0x1e0f07)], [0, 0.5, 1]));
  const apronBottom = project(g, 0, 0, -58);
  canvas.drawRect(Skia.XYWHRect(0, apronBottom[1], g.w, 40), linear(0, apronBottom[1], 0, apronBottom[1] + 40, [rgba(0x000000, 0.6), rgba(0x000000, 0)], null));
  const topPath = worldQuad(g, [[-W, 0, 0], [W, 0, 0], [W, DEPTH + 60, 0], [-W, DEPTH + 60, 0]]);
  canvas.drawPath(topPath, linear(0, back[1], 0, front[1], [rgba(0x5a2e17), rgba(0x6e3a1e), rgba(0x4f2812)], [0, 0.6, 1]));
  canvas.save();
  canvas.clipPath(topPath, ClipOp.Intersect, true);
  const wood = Skia.Paint();
  wood.setShader(Skia.Shader.MakeFractalNoise(0.008, 0.22, 3, 13, 0, 0));
  wood.setAlphaf(0.2);
  canvas.drawRect(Skia.XYWHRect(0, back[1] - 60, g.w, front[1] - back[1] + 60), wood);
  // Plank seams running away from the viewer.
  for (let x = -W; x <= W; x += 120) {
    const a = project(g, x, 0, 0);
    const b = project(g, x, DEPTH + 60, 0);
    canvas.drawLine(a[0], a[1], b[0], b[1], stroke(0x1c0c05, 1, 0.45));
  }
  canvas.restore();
  canvas.drawLine(0, front[1] + 0.5, g.w, front[1] + 0.5, stroke(0xd89a62, 1, 0.35));
  grain(canvas, 0, 0, g.w, g.h, 0.06, 2);
  // Vignette.
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, front[1] - 60, g.h * 0.75, [rgba(0x000000, 0), rgba(0x000000, 0.1), rgba(0x000000, 0.6)], [0, 0.55, 1]));
}

/** The linen in material space, at TEX_SCALE pixels per pt. */
export function drawLinen(canvas: SkCanvas) {
  const W = CLOTH_W;
  const L = CLOTH_LEN;
  canvas.drawRect(Skia.XYWHRect(0, 0, W, L), linear(0, 0, 0, L, [rgba(0xf7f2e8), rgba(0xf1ebdf), rgba(0xece4d6)], [0, 0.6, 1]));
  // Weave: two stretched noises, one per thread direction.
  for (const [fx, fy, seed] of [
    [1.6, 0.05, 3],
    [0.05, 1.6, 5],
  ]) {
    const p = Skia.Paint();
    p.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 2, seed, 0, 0));
    p.setAlphaf(0.16);
    p.setBlendMode(BlendMode.Multiply);
    canvas.drawRect(Skia.XYWHRect(0, 0, W, L), p);
  }
  // Slubs: uneven thread thickness, the irregular streaks of real linen.
  const slub = Skia.Paint();
  slub.setShader(Skia.Shader.MakeFractalNoise(0.6, 0.012, 2, 8, 0, 0));
  slub.setAlphaf(0.12);
  slub.setBlendMode(BlendMode.Multiply);
  canvas.drawRect(Skia.XYWHRect(0, 0, W, L), slub);
  // Windowpane check: a strong and a fine terracotta line each way.
  const strong = stroke(0xc0603a, 2.4, 0.42);
  const fine = stroke(0xc0603a, 0.9, 0.36);
  for (let x = 16; x < W; x += 64) {
    canvas.drawLine(x, 0, x, L, strong);
    canvas.drawLine(x + 6, 0, x + 6, L, fine);
  }
  for (let y = 20; y < L - 30; y += 64) {
    canvas.drawLine(0, y, W, y, strong);
    canvas.drawLine(0, y + 6, W, y + 6, fine);
  }
  // Hem: a woven band and a stitched edge.
  canvas.drawRect(Skia.XYWHRect(0, L - 26, W, 9), fill(0xb4532f, 0.85));
  canvas.drawRect(Skia.XYWHRect(0, L - 13, W, 2.5), fill(0xb4532f, 0.7));
  const stitch = stroke(0x8a3a1e, 0.8, 0.5);
  stitch.setPathEffect(Skia.PathEffect.MakeDash([3, 2], 0));
  canvas.drawLine(0, L - 4, W, L - 4, stitch);
  // Pressed creases from the linen cupboard.
  canvas.drawLine(W / 2, 0, W / 2, L, stroke(0xffffff, 1.4, 0.35));
  canvas.drawLine(W / 2 + 1.5, 0, W / 2 + 1.5, L, stroke(0x8a7a60, 0.8, 0.15));
  canvas.drawLine(0, DEPTH / 2, W, DEPTH / 2, stroke(0xffffff, 1.4, 0.3));
  canvas.drawLine(0, DEPTH / 2 + 1.5, W, DEPTH / 2 + 1.5, stroke(0x8a7a60, 0.8, 0.12));
}

// ——— The mesh ———

export type Mesh = { indices: number[]; texs: { x: number; y: number }[] };

export function makeMesh(): Mesh {
  const indices: number[] = [];
  const texs: { x: number; y: number }[] = [];
  for (let j = 0; j <= ROWS; j += 1) {
    for (let i = 0; i <= COLS; i += 1) texs.push({ x: (i / COLS) * CLOTH_W * TEX_SCALE, y: (j / ROWS) * CLOTH_LEN * TEX_SCALE });
  }
  for (let j = 0; j < ROWS; j += 1) {
    for (let i = 0; i < COLS; i += 1) {
      const a = j * (COLS + 1) + i;
      const b = a + 1;
      const c = a + COLS + 1;
      const d = c + 1;
      indices.push(a, b, c, b, d, c);
    }
  }
  return { indices, texs };
}

/** Where a material point (u across, s along) lies for a pose, and how it is shaded: [x, y, z, dark, light]. */
function clothPoint(pose: ClothPose, u: number, s: number, phase: number, sway: number) {
  'worklet';
  const TAU = 2 * Math.PI;
  const p = s + pose.d;
  let x = u;
  let y: number;
  let z: number;
  let dark = 0.03 + 0.035 * (0.5 + 0.5 * Math.sin(u / 83 + s / 61)) + 0.025 * (0.5 + 0.5 * Math.sin(u / 37 - s / 29 + 2));
  let light = 0;
  if (p <= DEPTH) {
    // On the table (or above it, thrown): a travelling ripple plus air under the middle.
    const k = TAU / 120;
    const across = 0.65 + 0.35 * Math.sin(u / 70 + 1.3);
    const arg = k * s - phase + u / 160;
    const bump = Math.sin((Math.PI * clamp(s, 0, DEPTH)) / DEPTH) * (0.75 + 0.25 * Math.cos(u / 90));
    const rip = pose.wave * across * (0.5 + 0.5 * Math.sin(arg));
    z = Math.max(0, pose.lift + pose.air * bump + rip);
    const slope = pose.wave * across * 0.5 * Math.cos(arg) * k + (pose.air * Math.cos((Math.PI * clamp(s, 0, DEPTH)) / DEPTH) * Math.PI) / DEPTH;
    dark += clamp(-slope * 1.3, 0, 0.34);
    light = clamp(slope * 1.1, 0, 0.24);
    y = DEPTH - p + pose.back;
    x += pose.wave * 0.1 * Math.sin(arg * 0.7);
  } else {
    // Over the front edge: hinged at the edge, hanging at `hang` (0 = flat forward, 1 = down),
    // in soft vertical folds that deepen toward the hem; streaming toward the viewer on a yank.
    const q = p - DEPTH;
    const f = pose.fling;
    const hem = Math.pow(clamp(q / DROP, 0, 1), 0.8);
    const beta = (pose.hang * Math.PI) / 2;
    const ez = pose.lift + pose.air * 0.15;
    const fa = u / 13 + 0.8 * Math.sin(u / 37 + 1.1);
    const foldDepth = (5 + 6 * hem) * Math.min(pose.hang, 1) * (1 - 0.5 * f);
    y = pose.back - q * Math.cos(beta) - (0.5 + 0.5 * Math.sin(fa)) * foldDepth - 1.5 - 3 * sway * hem * (0.6 + 0.4 * Math.sin(u / 90));
    z = ez - q * Math.sin(beta);
    // The yank: the drape streams out toward the viewer and a wave whips down it to the hem.
    const whip = q / 38 - pose.d / 26 + u / 150;
    const reach = clamp(q / 50, 0, 1);
    y -= q * f * 0.5 + f * 34 * Math.sin(whip) * reach;
    x += f * 12 * Math.sin(q / 50 - phase + u / 120);
    const fslope = Math.cos(fa) * (1 + ((0.8 * Math.cos(u / 37 + 1.1)) / 37) * 13);
    const wslope = f * Math.cos(whip) * reach;
    dark += 0.1 + 0.12 * clamp(q / 140, 0, 1) + clamp(-fslope, 0, 1) * 0.16 * Math.min(pose.hang, 1) * (1 - f) + clamp(-wslope, 0, 1) * 0.4;
    light = clamp(fslope, 0, 1) * 0.12 * Math.min(pose.hang, 1) * (1 - f) + clamp(wslope, 0, 1) * 0.3;
  }
  return [x, y, z, dark, light];
}

/** Builds and draws the cloth for a pose: the linen, then its shading; `alpha` < 1 for ghosts. */
export function drawCloth(canvas: SkCanvas, g: Geo, mesh: Mesh, linen: SkImage, pose: ClothPose, alpha = 1) {
  'worklet';
  const positions: { x: number; y: number }[] = [];
  const shade: Float32Array[] = [];
  const TAU = 2 * Math.PI;
  const phase = (TAU * 9 * pose.time) / LOOP;
  const sway = Math.sin((TAU * 2 * pose.time) / LOOP);
  let onScreen = false;
  for (let j = 0; j <= ROWS; j += 1) {
    const s = (j / ROWS) * CLOTH_LEN;
    for (let i = 0; i <= COLS; i += 1) {
      const u = (i / COLS - 0.5) * CLOTH_W;
      const c = clothPoint(pose, u, s, phase, sway);
      const sp = project(g, c[0], c[1], c[2]);
      if (sp[1] < g.panelTop + 40 && sp[1] > -40) onScreen = true;
      positions.push({ x: sp[0], y: sp[1] });
      shade.push(c[3] > c[4] ? Float32Array.of(0.2, 0.11, 0.06, c[3] * alpha) : Float32Array.of(1, 0.96, 0.88, c[4] * alpha));
    }
  }
  if (!onScreen) return;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(linen.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None));
  paint.setAlphaf(alpha);
  canvas.drawVertices(Skia.MakeVertices(VertexMode.Triangles, positions, mesh.texs, undefined, mesh.indices), BlendMode.SrcOver, paint);
  canvas.drawVertices(Skia.MakeVertices(VertexMode.Triangles, positions, null, shade, mesh.indices), BlendMode.Dst, Skia.Paint());
}

/** Speed streaks on the table behind the cloth's back edge while it is yanked. */
export function drawStreaks(canvas: SkCanvas, g: Geo, pose: ClothPose) {
  'worklet';
  const k = clamp(pose.speed / 2200, 0, 1);
  if (k <= 0.02) return;
  const edge = DEPTH - pose.d;
  if (edge < -DROP) return;
  for (let i = 0; i < 16; i += 1) {
    const h = Math.sin(i * 12.9898 + 4.7) * 43758.5453;
    const r = h - Math.floor(h);
    const x = -190 + (i / 15) * 380 + (r - 0.5) * 18;
    const y0 = Math.max(edge, 0) + 6;
    const len = (60 + 120 * r) * k;
    const a = project(g, x, y0, 1);
    const b = project(g, x, y0 + len, 1);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStrokeWidth(1.6 + 2.2 * r);
    p.setStyle(PaintStyle.Stroke);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: a[0], y: a[1] }, { x: b[0], y: b[1] }, [Float32Array.of(1, 0.97, 0.9, 0.75 * k), Float32Array.of(1, 0.97, 0.9, 0)], null, TileMode.Clamp));
    canvas.drawLine(a[0], a[1], b[0], b[1], p);
  }
}
