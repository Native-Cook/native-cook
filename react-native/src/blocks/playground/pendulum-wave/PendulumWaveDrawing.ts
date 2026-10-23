// Drawing for the pendulum wave, as worklets on a Skia canvas: a perspective camera orbiting the
// scene, a walnut frame drawn as shaded boxes, V-strung brass bobs with light trails, their
// shadows on the table, and the comb. Everything is painted back to front (faces and bobs are
// sorted by depth each frame), lit by one light from above, a little to the left and front.

import { BlurStyle, PaintStyle, Skia, type SkCanvas, StrokeCap, TileMode, vec } from '@shopify/react-native-skia';

import { BEAM_Y, BOB_R, bobAt, type Camera, COUNT, type PendulumSim, rowZ, SPACING, TRAIL, V_HALF } from './PendulumWaveSim';

/** What the camera looks at. */
const TARGET_Y = 6.2;
// The table's extent, and the frame: beam, posts at the row's ends, feet.
const TABLE_X = 14;
const TABLE_Z = 16;
const POST_Z = ((COUNT - 1) / 2) * SPACING + 1.3;
// Toward the light (normalised in `light`).
const LIGHT = [-0.35, 1, 0.3];
const WOOD: [number, number, number] = [0.42, 0.25, 0.14];

export type View3 = {
  cx: number;
  cy: number;
  f: number;
  /** Camera position and its right, up and forward axes. */
  c: number[];
  r: number[];
  u: number[];
  w: number[];
};

function norm(v: number[]) {
  'worklet';
  const l = Math.hypot(v[0], v[1], v[2]);
  return [v[0] / l, v[1] / l, v[2] / l];
}

function light() {
  'worklet';
  return norm(LIGHT);
}

/** The camera for an orbit `cam`, on a canvas of the given size. */
export function makeView(cam: Camera, width: number, height: number, focal: number, centreY: number): View3 {
  'worklet';
  const yaw = (cam.yaw * Math.PI) / 180;
  const pitch = (cam.pitch * Math.PI) / 180;
  const c = [cam.dist * Math.cos(pitch) * Math.sin(yaw), TARGET_Y + cam.dist * Math.sin(pitch), cam.dist * Math.cos(pitch) * Math.cos(yaw)];
  const w = norm([-c[0], TARGET_Y - c[1], -c[2]]);
  // right = forward × up, up' = right × forward.
  const r = norm([-w[2], 0, w[0]]);
  const u = [r[1] * w[2] - r[2] * w[1], r[2] * w[0] - r[0] * w[2], r[0] * w[1] - r[1] * w[0]];
  return { cx: width / 2, cy: centreY, f: focal, c, r, u, w };
}

/** World point to screen: [x, y, depth]. */
export function project(v: View3, x: number, y: number, z: number) {
  'worklet';
  const dx = x - v.c[0];
  const dy = y - v.c[1];
  const dz = z - v.c[2];
  const depth = Math.max(0.1, dx * v.w[0] + dy * v.w[1] + dz * v.w[2]);
  const sx = dx * v.r[0] + dy * v.r[1] + dz * v.r[2];
  const sy = dx * v.u[0] + dy * v.u[1] + dz * v.u[2];
  return [v.cx + (v.f * sx) / depth, v.cy - (v.f * sy) / depth, depth];
}

function rgba(r: number, g: number, b: number, a: number) {
  'worklet';
  return new Float32Array([Math.min(1, r), Math.min(1, g), Math.min(1, b), a]);
}

function paint(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function line(color: Float32Array, width: number) {
  'worklet';
  const p = paint(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

/** A world-space polygon on screen. */
function polygon(v: View3, pts: number[][]) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < pts.length; i += 1) {
    const p = project(v, pts[i][0], pts[i][1], pts[i][2]);
    if (i === 0) b.moveTo(p[0], p[1]);
    else b.lineTo(p[0], p[1]);
  }
  b.close();
  return b.build();
}

type Box = { c: number[]; s: number[]; alpha: number };

/** The frame's boxes: the beam (faded by `beamAlpha`), the posts, the feet. */
function frameBoxes(beamAlpha: number): Box[] {
  'worklet';
  return [
    { c: [0, BEAM_Y + 0.3, 0], s: [0.6, 0.6, POST_Z * 2 + 0.5], alpha: beamAlpha },
    { c: [0, (BEAM_Y + 0.6) / 2, -POST_Z], s: [0.5, BEAM_Y + 0.6, 0.5], alpha: 1 },
    { c: [0, (BEAM_Y + 0.6) / 2, POST_Z], s: [0.5, BEAM_Y + 0.6, 0.5], alpha: 1 },
    { c: [0, 0.2, -POST_Z], s: [5.4, 0.4, 0.9], alpha: 1 },
    { c: [0, 0.2, POST_Z], s: [5.4, 0.4, 0.9], alpha: 1 },
  ];
}

// The six faces of a unit box: outward normal, then the two in-face axes.
const FACES = [
  [1, 0, 0, 0, 1, 0, 0, 0, 1],
  [-1, 0, 0, 0, 1, 0, 0, 0, 1],
  [0, 1, 0, 1, 0, 0, 0, 0, 1],
  [0, -1, 0, 1, 0, 0, 0, 0, 1],
  [0, 0, 1, 1, 0, 0, 0, 1, 0],
  [0, 0, -1, 1, 0, 0, 0, 1, 0],
];

/** One face of a box: its corners in the world. */
function faceCorners(b: Box, f: number[]) {
  'worklet';
  const hx = b.s[0] / 2;
  const hy = b.s[1] / 2;
  const hz = b.s[2] / 2;
  const h = [hx, hy, hz];
  const centre = [b.c[0] + f[0] * hx, b.c[1] + f[1] * hy, b.c[2] + f[2] * hz];
  const a = [f[3] * h[0], f[4] * h[1], f[5] * h[2]];
  const d = [f[6] * h[0], f[7] * h[1], f[8] * h[2]];
  return {
    centre,
    pts: [
      [centre[0] - a[0] - d[0], centre[1] - a[1] - d[1], centre[2] - a[2] - d[2]],
      [centre[0] + a[0] - d[0], centre[1] + a[1] - d[1], centre[2] + a[2] - d[2]],
      [centre[0] + a[0] + d[0], centre[1] + a[1] + d[1], centre[2] + a[2] + d[2]],
      [centre[0] - a[0] + d[0], centre[1] - a[1] + d[1], centre[2] - a[2] + d[2]],
    ],
    a,
    d,
  };
}

/** A walnut face, lit by the light and grained along its longer side. */
function drawFace(canvas: SkCanvas, v: View3, b: Box, f: number[]) {
  'worklet';
  const L = light();
  const fc = faceCorners(b, f);
  const lit = 0.32 + 0.68 * Math.max(0, f[0] * L[0] + f[1] * L[1] + f[2] * L[2]);
  const top = f[1] > 0.5 ? 1.12 : 1;
  const path = polygon(v, fc.pts);
  canvas.drawPath(path, paint(rgba(WOOD[0] * lit * top, WOOD[1] * lit * top, WOOD[2] * lit * top, b.alpha)));
  // Grain: a few darker lines along the face's longer axis.
  const la = Math.hypot(fc.a[0], fc.a[1], fc.a[2]);
  const ld = Math.hypot(fc.d[0], fc.d[1], fc.d[2]);
  const along = la > ld ? fc.a : fc.d;
  const across = la > ld ? fc.d : fc.a;
  const grain = line(rgba(0.12, 0.06, 0.03, 0.22 * b.alpha), 0.7);
  for (let i = 1; i <= 3; i += 1) {
    const t = -0.7 + i * 0.37;
    const o = [fc.centre[0] + across[0] * t, fc.centre[1] + across[1] * t, fc.centre[2] + across[2] * t];
    const p0 = project(v, o[0] - along[0] * 0.98, o[1] - along[1] * 0.98, o[2] - along[2] * 0.98);
    const p1 = project(v, o[0] + along[0] * 0.98, o[1] + along[1] * 0.98, o[2] + along[2] * 0.98);
    canvas.drawLine(p0[0], p0[1], p1[0], p1[1], grain);
  }
  // A light catch along the top edge of side faces.
  const edge = line(rgba(1, 0.86, 0.66, 0.12 * b.alpha * lit), 0.8);
  const e0 = project(v, fc.pts[3][0], fc.pts[3][1], fc.pts[3][2]);
  const e1 = project(v, fc.pts[2][0], fc.pts[2][1], fc.pts[2][2]);
  canvas.drawLine(e0[0], e0[1], e1[0], e1[1], edge);
}

/** One brass bob with its V of string and its light trail. */
function drawBob(canvas: SkCanvas, v: View3, s: PendulumSim, k: number, stringAlpha: number) {
  'worklet';
  const b = bobAt(s, k, s.x[k]);
  const p = project(v, b[0], b[1], b[2]);
  const z = rowZ(k);
  // The trail: newest sample first, fading and thinning with age.
  const head = s.trailHead;
  let prev = p;
  for (let i = 1; i < TRAIL; i += 1) {
    const x = s.trail[k][(head - i + TRAIL * 2) % TRAIL];
    const q0 = bobAt(s, k, x);
    const q = project(v, q0[0], q0[1], q0[2]);
    const age = i / TRAIL;
    const len = Math.hypot(q[0] - prev[0], q[1] - prev[1]);
    if (len > 0.3) canvas.drawLine(prev[0], prev[1], q[0], q[1], line(rgba(1, 0.86, 0.6, 0.5 * (1 - age) * (1 - age)), 2.2 * (1 - age) + 0.4));
    prev = q;
  }
  // The strings.
  const str = line(rgba(0.92, 0.9, 0.85, 0.5 * stringAlpha), 0.75);
  const a0 = project(v, 0, BEAM_Y, z - V_HALF);
  const a1 = project(v, 0, BEAM_Y, z + V_HALF);
  canvas.drawLine(a0[0], a0[1], p[0], p[1], str);
  canvas.drawLine(a1[0], a1[1], p[0], p[1], str);
  // The bob: brass lit from the upper left, a warm bounce from the table on its lower edge.
  const r = (v.f * BOB_R) / p[2];
  const brass = Skia.Paint();
  brass.setAntiAlias(true);
  brass.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(p[0] - r * 0.35, p[1] - r * 0.42),
      r * 1.45,
      [rgba(1, 0.96, 0.8, 1), rgba(0.89, 0.7, 0.33, 1), rgba(0.55, 0.37, 0.12, 1), rgba(0.2, 0.12, 0.03, 1)],
      [0, 0.22, 0.62, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(p[0], p[1], r, brass);
  const bounce = line(rgba(1, 0.7, 0.4, 0.35), Math.max(0.6, r * 0.18));
  bounce.setShader(
    Skia.Shader.MakeLinearGradient(vec(p[0], p[1]), vec(p[0], p[1] + r), [rgba(1, 0.7, 0.4, 0), rgba(1, 0.72, 0.42, 0.45)], null, TileMode.Clamp),
  );
  canvas.drawCircle(p[0], p[1], r * 0.9, bounce);
  const spec = paint(rgba(1, 1, 1, 0.9));
  spec.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(0.4, r * 0.1), true));
  canvas.drawCircle(p[0] - r * 0.38, p[1] - r * 0.44, r * 0.16, spec);
}

/** The comb: a brass rail through the bobs' line with a tooth between each pair. */
function drawComb(canvas: SkCanvas, v: View3, s: PendulumSim) {
  'worklet';
  const shown = s.combShown;
  if (shown <= 0.01) return;
  const sink = (1 - shown) * 0.8;
  const pts: number[][] = [];
  for (let k = -1; k <= COUNT; k += 1) {
    const kk = Math.min(COUNT - 1, Math.max(0, k));
    const b = bobAt(s, kk, s.combX);
    pts.push([s.combX, b[1] - sink, k < 0 ? rowZ(0) - 0.9 : k >= COUNT ? rowZ(COUNT - 1) + 0.9 : rowZ(k)]);
  }
  const rail = Skia.PathBuilder.Make();
  for (let i = 0; i < pts.length; i += 1) {
    const q = project(v, pts[i][0], pts[i][1], pts[i][2]);
    if (i === 0) rail.moveTo(q[0], q[1]);
    else rail.lineTo(q[0], q[1]);
  }
  const mid = project(v, s.combX, 5, 0);
  const w = (v.f * 0.09) / mid[2];
  const path = rail.build();
  canvas.drawPath(path, line(rgba(0.16, 0.1, 0.04, 0.5 * shown), w + 1.6));
  canvas.drawPath(path, line(rgba(0.86, 0.66, 0.32, shown), w));
  canvas.drawPath(path, line(rgba(1, 0.95, 0.8, 0.55 * shown), w * 0.3));
  // Teeth between the bobs, so each string sits in its own slot.
  const tooth = line(rgba(0.8, 0.6, 0.28, shown), Math.max(1, w * 0.55));
  for (let k = 0; k < COUNT - 1; k += 1) {
    const y = (pts[k + 1][1] + pts[k + 2][1]) / 2;
    const z = rowZ(k) + SPACING / 2;
    const t0 = project(v, s.combX, y - 0.5, z);
    const t1 = project(v, s.combX, y + 0.5, z);
    canvas.drawLine(t0[0], t0[1], t1[0], t1[1], tooth);
  }
}

/** Where a world point's shadow falls on the table. */
function shadowAt(x: number, y: number, z: number) {
  'worklet';
  const L = light();
  return [x - (L[0] * y) / L[1], 0.001, z - (L[2] * y) / L[1]];
}

/** Soft shadows on the table, cast away from the light. */
function drawShadows(canvas: SkCanvas, v: View3, s: PendulumSim, beamAlpha: number) {
  'worklet';
  const soft = paint(rgba(0.02, 0.01, 0, 0.42));
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, false));
  for (let k = 0; k < COUNT; k += 1) {
    const b = bobAt(s, k, s.x[k]);
    const c = shadowAt(b[0], b[1], b[2]);
    const r = BOB_R * 1.25;
    const ring: number[][] = [];
    for (let i = 0; i < 10; i += 1) {
      const a = (i / 10) * Math.PI * 2;
      ring.push([c[0] + Math.cos(a) * r, 0.001, c[2] + Math.sin(a) * r]);
    }
    canvas.drawPath(polygon(v, ring), soft);
  }
  // The posts and the beam.
  const wide = paint(rgba(0.02, 0.01, 0, 0.32));
  wide.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, false));
  for (let side = -1; side <= 1; side += 2) {
    const z = side * POST_Z;
    const t = shadowAt(0, BEAM_Y, z);
    canvas.drawPath(polygon(v, [[-0.25, 0.001, z], [0.25, 0.001, z], [t[0] + 0.25, 0.001, t[2]], [t[0] - 0.25, 0.001, t[2]]]), wide);
  }
  const b0 = shadowAt(0, BEAM_Y, -POST_Z);
  const b1 = shadowAt(0, BEAM_Y, POST_Z);
  wide.setAlphaf(0.32 * beamAlpha);
  canvas.drawPath(polygon(v, [[b0[0] - 0.3, 0.001, b0[2]], [b0[0] + 0.3, 0.001, b0[2]], [b1[0] + 0.3, 0.001, b1[2]], [b1[0] - 0.3, 0.001, b1[2]]]), wide);
}

/** The whole scene for camera `v`; `topness` (0…1) fades the beam for the view from above. */
export function drawScene(canvas: SkCanvas, v: View3, s: PendulumSim, width: number, height: number, topness: number) {
  'worklet';
  // The studio: a dark warm wall with a pool of light behind the frame.
  const wall = Skia.Paint();
  wall.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, height), [rgba(0.05, 0.055, 0.065, 1), rgba(0.085, 0.075, 0.07, 1), rgba(0.03, 0.025, 0.022, 1)], [0, 0.55, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), wall);
  const centre = project(v, 0, TARGET_Y, 0);
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient(vec(centre[0], centre[1]), height * 0.55, [rgba(0.32, 0.24, 0.16, 0.5), rgba(0.32, 0.24, 0.16, 0)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), glow);

  // The table: walnut, lit in a pool under the frame.
  const table = polygon(v, [[-TABLE_X, 0, -TABLE_Z], [TABLE_X, 0, -TABLE_Z], [TABLE_X, 0, TABLE_Z], [-TABLE_X, 0, TABLE_Z]]);
  const o = project(v, 0, 0, 0);
  const wood = Skia.Paint();
  wood.setAntiAlias(true);
  wood.setShader(Skia.Shader.MakeRadialGradient(vec(o[0], o[1]), (v.f * 15) / o[2], [rgba(0.3, 0.19, 0.11, 1), rgba(0.17, 0.1, 0.055, 1), rgba(0.06, 0.035, 0.02, 1)], [0, 0.55, 1], TileMode.Clamp));
  canvas.drawPath(table, wood);
  // Long grain lines across the table.
  const grain = line(rgba(0.05, 0.025, 0.01, 0.18), 0.8);
  for (let i = -8; i <= 8; i += 1) {
    const x = i * 1.05 + Math.sin(i * 3.7) * 0.2;
    const p0 = project(v, x, 0, -TABLE_Z);
    const p1 = project(v, x + Math.sin(i * 1.3) * 0.3, 0, TABLE_Z);
    canvas.drawLine(p0[0], p0[1], p1[0], p1[1], grain);
  }
  const beamAlpha = 1 - 0.78 * topness;
  drawShadows(canvas, v, s, beamAlpha);

  // Everything standing on the table, back to front.
  const boxes = frameBoxes(beamAlpha);
  const items: number[][] = [];
  for (let i = 0; i < boxes.length; i += 1) {
    for (let j = 0; j < FACES.length; j += 1) {
      const f = FACES[j];
      const fc = faceCorners(boxes[i], f);
      const toCam = [v.c[0] - fc.centre[0], v.c[1] - fc.centre[1], v.c[2] - fc.centre[2]];
      if (toCam[0] * f[0] + toCam[1] * f[1] + toCam[2] * f[2] <= 0) continue;
      // The beam's faces sort as if at the beam's middle line nearest the camera.
      const d = project(v, fc.centre[0], fc.centre[1], i === 0 ? Math.max(-POST_Z, Math.min(POST_Z, v.c[2])) : fc.centre[2])[2];
      items.push([d, 0, i, j]);
    }
  }
  let farthest = 0;
  for (let k = 0; k < COUNT; k += 1) {
    const b = bobAt(s, k, s.x[k]);
    const d = project(v, b[0], b[1], b[2])[2];
    farthest = Math.max(farthest, d);
    items.push([d, 1, k, 0]);
  }
  items.push([farthest + 0.05, 2, 0, 0]);
  // Farthest first (insertion sort: a few dozen items, nearly in order from the last frame).
  for (let i = 1; i < items.length; i += 1) {
    const it = items[i];
    let j = i - 1;
    while (j >= 0 && items[j][0] < it[0]) {
      items[j + 1] = items[j];
      j -= 1;
    }
    items[j + 1] = it;
  }
  const stringAlpha = 1 - 0.55 * topness;
  for (let i = 0; i < items.length; i += 1) {
    const it = items[i];
    if (it[1] === 0) drawFace(canvas, v, boxes[it[2]], FACES[it[3]]);
    else if (it[1] === 1) drawBob(canvas, v, s, it[2], stringAlpha);
    else drawComb(canvas, v, s);
  }
}
