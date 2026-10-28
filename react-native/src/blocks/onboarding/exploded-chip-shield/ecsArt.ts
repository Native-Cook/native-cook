import { BlurStyle, ClipOp, FillType, PaintStyle, Skia, TileMode, type SkCanvas, type SkColor, type SkPaint, type SkPath, type SkPoint, type SkRect } from '@shopify/react-native-skia';

import {
  BG_BOTTOM,
  BG_TOP,
  CALLOUTS,
  CLOSED_LIFT,
  CLOSED_ZOOM,
  DISC_SIDE,
  DISC_THICK,
  type Frame,
  GLOW,
  HOLE_SIDE,
  LAYERS,
  layerZ,
  LINE,
  OPEN_DROP,
  REST_YAW,
  SHIFT,
  SQUASH,
  TEAL,
  TRAY_HOLE,
  UNIT,
} from './ecsModel';

/*
 Draws the six isometric layers of the chip, its shadow, glow and callout lines (a port of
 ExplodedChipShieldArt.swift). Every shape is built flat in the layer's plane (centred on 0) and
 projected: turn by the yaw, squash y by 0.58, lift by the layer's height. Side faces are the convex
 hull of the top and bottom outlines. Helpers sit above the worklets that call them.
*/

export function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, Math.min(1, Math.max(0, a)));
}

function solid(v: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v, a));
  return p;
}

function stroke(v: number, a: number, width: number): SkPaint {
  'worklet';
  const p = solid(v, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

/** SwiftUI's blur radius as the sigma Android's BlurMaskFilter gives (1.5 × radius), so the stacks soften alike. */
function blurred(p: SkPaint, radius: number): SkPaint {
  'worklet';
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.57735 * radius * 1.5 + 0.5, true));
  return p;
}

/** Gradient stops: [at, hex, alpha] triples flattened. */
type Stops = number[];

function shaded(stops: Stops, a: SkPoint, b: SkPoint): SkPaint {
  'worklet';
  const colors: SkColor[] = [];
  const pos: number[] = [];
  for (let i = 0; i < stops.length; i += 3) {
    pos.push(stops[i]);
    colors.push(hex(stops[i + 1], stops[i + 2]));
  }
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient(a, { x: Math.max(b.x, a.x + 0.1), y: b.y }, colors, pos, TileMode.Clamp));
  return p;
}

function across(stops: Stops, r: SkRect): SkPaint {
  'worklet';
  return shaded(stops, { x: r.x, y: r.y + r.height / 2 }, { x: r.x + r.width, y: r.y + r.height / 2 });
}

function diagonal(stops: Stops, r: SkRect): SkPaint {
  'worklet';
  return shaded(stops, { x: r.x, y: r.y }, { x: r.x + r.width, y: r.y + Math.max(r.height, 0.1) });
}

// Projection

export type Proj = { cx: number; cy: number; yaw: number; scale: number };

function pt(pr: Proj, x: number, y: number, z: number): SkPoint {
  'worklet';
  const c = Math.cos(pr.yaw) * pr.scale;
  const s = Math.sin(pr.yaw) * pr.scale;
  return { x: pr.cx + x * c - y * s, y: pr.cy + SQUASH * (x * s + y * c) - z * pr.scale };
}

/** The plane → screen map at height z; `turn` rotates the drawing inside the plane first. */
function matrix(pr: Proj, z: number, turn: number) {
  'worklet';
  const a = pr.yaw + turn;
  const c = Math.cos(a) * pr.scale;
  const s = Math.sin(a) * pr.scale;
  return Skia.Matrix([c, -s, pr.cx, SQUASH * s, SQUASH * c, pr.cy - z * pr.scale, 0, 0, 1]);
}

function facing(pr: Proj, nx: number, ny: number) {
  'worklet';
  return Math.sin(pr.yaw) * nx + Math.cos(pr.yaw) * ny > 0.05;
}

function moved(src: SkPath, pr: Proj, z: number, turn: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addPath(src, matrix(pr, z, turn));
  return p;
}

// Outlines (plane): flat arrays [x0, y0, x1, y1, …] with matching normals.

export type Outline = { pts: number[]; nrm: number[] };

function roundedSquare(side: number, radius: number, perCorner: number): Outline {
  const h = side / 2;
  const r = radius;
  const cx = [h - r, h - r, -h + r, -h + r];
  const cy = [-h + r, h - r, h - r, -h + r];
  const pts: number[] = [];
  const nrm: number[] = [];
  for (let k = 0; k < 4; k++) {
    const a0 = -Math.PI / 2 + (k * Math.PI) / 2;
    for (let j = 0; j <= perCorner; j++) {
      const a = a0 + (j / perCorner) * (Math.PI / 2);
      nrm.push(Math.cos(a), Math.sin(a));
      pts.push(cx[k] + r * Math.cos(a), cy[k] + r * Math.sin(a));
    }
  }
  return { pts, nrm };
}

function circleOutline(d: number, n: number): Outline {
  const pts: number[] = [];
  const nrm: number[] = [];
  for (let j = 0; j < n; j++) {
    const a = (j / n) * 2 * Math.PI;
    nrm.push(Math.cos(a), Math.sin(a));
    pts.push((Math.cos(a) * d) / 2, (Math.sin(a) * d) / 2);
  }
  return { pts, nrm };
}

/** Outline points resampled about every `every` pt along the perimeter, with their normals. */
function denseOutline(o: Outline, every: number): Outline {
  const pts: number[] = [];
  const nrm: number[] = [];
  const n = o.pts.length / 2;
  for (let k = 0; k < n; k++) {
    const k2 = (k + 1) % n;
    const ax = o.pts[2 * k];
    const ay = o.pts[2 * k + 1];
    const bx = o.pts[2 * k2];
    const by = o.pts[2 * k2 + 1];
    const cnt = Math.max(1, Math.floor(Math.hypot(bx - ax, by - ay) / every));
    for (let j = 0; j < cnt; j++) {
      const t = j / cnt;
      pts.push(ax + (bx - ax) * t, ay + (by - ay) * t);
      nrm.push(o.nrm[2 * k] + (o.nrm[2 * k2] - o.nrm[2 * k]) * t, o.nrm[2 * k + 1] + (o.nrm[2 * k2 + 1] - o.nrm[2 * k + 1]) * t);
    }
  }
  return { pts, nrm };
}

function fract(x: number) {
  return x - Math.floor(x);
}

/** The shield of the mark, centred on 0, its point towards +y. */
export function shieldPath(w: number, h: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  const c = w * 0.16;
  p.moveTo(-w / 2, -h / 2 + c);
  p.quadTo(-w / 2, -h / 2, -w / 2 + c, -h / 2);
  p.lineTo(w / 2 - c, -h / 2);
  p.quadTo(w / 2, -h / 2, w / 2, -h / 2 + c);
  p.lineTo(w / 2, -h * 0.02);
  p.cubicTo(w / 2, h * 0.26, w * 0.22, h * 0.42, 0, h / 2);
  p.cubicTo(-w * 0.22, h * 0.42, -w / 2, h * 0.26, -w / 2, -h * 0.02);
  p.close();
  return p;
}

/** The keyhole inside the shield (relative to the shield's size). */
export function keyholePath(w: number, h: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  const r = w * 0.15;
  const cy = -h * 0.1;
  p.addOval(Skia.XYWHRect(-r, cy - r, 2 * r, 2 * r));
  p.moveTo(-w * 0.06, cy + r * 0.4);
  p.lineTo(w * 0.06, cy + r * 0.4);
  p.lineTo(w * 0.1, h * 0.22);
  p.lineTo(-w * 0.1, h * 0.22);
  p.close();
  return p;
}

/** Plane outlines and static plane paths, built once on the JS thread. */
export type Statics = {
  outlines: Outline[];
  trayInner: Outline;
  coreInset: Outline;
  disc: Outline;
  hole: Outline;
  glassInner: Outline;
  lidFrame: Outline;
  foot: Outline;
  halo114: Outline;
  halo120: Outline;
  knurl: Outline;
  frostDots: SkPath;
  glassDots: SkPath;
  traces: SkPath;
  pads: SkPath;
  meshRing: SkPath;
  discRings: SkPath;
  shield: SkPath;
  keyhole: SkPath;
  hatch: SkPath;
  discKeyhole: SkPath;
};

export function makeStatics(): Statics {
  const outlines = LAYERS.map((l) => roundedSquare(l.side * UNIT, l.radius * UNIT, 9));
  const frostDots = Skia.Path.Make();
  {
    const l = LAYERS[3];
    const n = 22;
    const half = (l.side * UNIT) / 2 - 8;
    const r = (HOLE_SIDE * UNIT) / 2 + 7;
    for (let i = 0; i <= n; i++) {
      for (let j = 0; j <= n; j++) {
        const x = -half + (2 * half * i) / n;
        const y = -half + (2 * half * j) / n;
        if (x * x + y * y < r * r) continue;
        if (fract(Math.sin((i * 31 + j * 17) * 12.9898) * 43758.5453) > 0.42) continue;
        frostDots.addOval(Skia.XYWHRect(x - 0.9, y - 0.9, 1.8, 1.8));
      }
    }
  }
  const a = (LAYERS[4].side * UNIT) / 2;
  const routes = [
    [-0.8, -0.55, -0.35, -0.55, -0.2, -0.35],
    [0.8, -0.3, 0.45, -0.3, 0.3, -0.15],
    [-0.55, 0.8, -0.55, 0.4, -0.3, 0.22],
    [0.3, 0.8, 0.3, 0.45, 0.12, 0.3],
    [-0.8, 0.1, -0.42, 0.1],
    [0.62, 0.62, 0.42, 0.42],
  ];
  const traces = Skia.Path.Make();
  const pads = Skia.Path.Make();
  for (const r of routes) {
    traces.moveTo(r[0] * a, r[1] * a);
    for (let k = 2; k < r.length; k += 2) traces.lineTo(r[k] * a, r[k + 1] * a);
    pads.addOval(Skia.XYWHRect(r[0] * a - 2.2, r[1] * a - 2.2, 4.4, 4.4));
    pads.addOval(Skia.XYWHRect(r[r.length - 2] * a - 2.2, r[r.length - 1] * a - 2.2, 4.4, 4.4));
  }
  const glassDots = Skia.Path.Make();
  {
    const n = 18;
    for (let i = 0; i <= n; i++) {
      for (let j = 0; j <= n; j++) {
        const x = -a + 10 + ((2 * a - 20) * i) / n;
        const y = -a + 10 + ((2 * a - 20) * j) / n;
        const h = fract(Math.sin((i * 13 + j * 57) * 78.233) * 43758.5453);
        if (h > 0.34 || x * x + y * y < a * 0.34 * (a * 0.34)) continue;
        glassDots.addOval(Skia.XYWHRect(x - 0.8, y - 0.8, 1.6, 1.6));
      }
    }
  }
  const meshRing = Skia.Path.Make();
  meshRing.addOval(Skia.XYWHRect(-a * 0.28, -a * 0.28, a * 0.56, a * 0.56));
  const discRings = Skia.Path.Make();
  const dd = DISC_SIDE * UNIT;
  for (const r of [0.82, 0.64, 0.46]) discRings.addOval(Skia.XYWHRect((-dd * r) / 2, (-dd * r) / 2, dd * r, dd * r));
  const sw = 0.4 * UNIT;
  const sh = 0.47 * UNIT;
  const hatch = Skia.Path.Make();
  for (let v = -sw; v < sw; v += 2.6) {
    hatch.moveTo(v, -sh / 2);
    hatch.lineTo(v + sh, sh / 2);
  }
  return {
    outlines,
    trayInner: roundedSquare(TRAY_HOLE * UNIT, 0.11 * UNIT, 9),
    coreInset: roundedSquare(LAYERS[2].side * UNIT * 0.86, LAYERS[2].radius * UNIT * 0.7, 9),
    disc: circleOutline(DISC_SIDE * UNIT, 48),
    hole: circleOutline(HOLE_SIDE * UNIT, 48),
    glassInner: roundedSquare(LAYERS[4].side * UNIT - 3, LAYERS[4].radius * UNIT - 1.5, 9),
    lidFrame: roundedSquare(0.7 * UNIT, 0.15 * UNIT, 9),
    foot: roundedSquare(UNIT, 0.22 * UNIT, 9),
    halo114: roundedSquare(1.14 * UNIT, 0.3 * UNIT, 9),
    halo120: roundedSquare(1.2 * UNIT, 0.3 * UNIT, 9),
    knurl: denseOutline(roundedSquare(UNIT, 0.22 * UNIT, 14), 2.4),
    frostDots,
    glassDots,
    traces,
    pads,
    meshRing,
    discRings,
    shield: shieldPath(sw, sh),
    keyhole: keyholePath(sw, sh),
    hatch,
    discKeyhole: keyholePath(DISC_SIDE * UNIT * 0.34, DISC_SIDE * UNIT * 0.4),
  };
}

// Geometry helpers

function project(pr: Proj, o: Outline, z: number, k: number): SkPoint[] {
  'worklet';
  const out: SkPoint[] = [];
  for (let i = 0; i < o.pts.length; i += 2) out.push(pt(pr, o.pts[i] * k, o.pts[i + 1] * k, z));
  return out;
}

function cross(o: SkPoint, a: SkPoint, b: SkPoint) {
  'worklet';
  return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
}

/** Convex hull (monotone chain). */
function hull(input: SkPoint[]): SkPoint[] {
  'worklet';
  const p = input.slice().sort((u, v) => (u.x === v.x ? u.y - v.y : u.x - v.x));
  if (p.length <= 2) return p;
  const lower: SkPoint[] = [];
  const upper: SkPoint[] = [];
  for (let i = 0; i < p.length; i++) {
    const q = p[i];
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], q) <= 0) lower.pop();
    lower.push(q);
  }
  for (let i = p.length - 1; i >= 0; i--) {
    const q = p[i];
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], q) <= 0) upper.pop();
    upper.push(q);
  }
  lower.pop();
  upper.pop();
  return lower.concat(upper);
}

function poly(pts: SkPoint[]): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(pts[0].x, pts[0].y);
  for (let i = 1; i < pts.length; i++) p.lineTo(pts[i].x, pts[i].y);
  p.close();
  return p;
}

function bounds(pts: SkPoint[]): SkRect {
  'worklet';
  let l = Infinity;
  let t = Infinity;
  let r = -Infinity;
  let b = -Infinity;
  for (let i = 0; i < pts.length; i++) {
    l = Math.min(l, pts[i].x);
    t = Math.min(t, pts[i].y);
    r = Math.max(r, pts[i].x);
    b = Math.max(b, pts[i].y);
  }
  return Skia.XYWHRect(l, t, r - l, b - t);
}

function evenOdd(a: SkPath, b: SkPath): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addPath(a);
  p.addPath(b);
  p.setFillType(FillType.EvenOdd);
  return p;
}

/** Where the light band sits along a face's diagonal; it travels with the view angle. */
function specular(yaw: number) {
  'worklet';
  return 0.42 + ((yaw - REST_YAW) / ((10 * Math.PI) / 180)) * 0.32;
}

function band(c: SkCanvas, clip: SkPath, box: SkRect, u: number, alpha: number, width: number) {
  'worklet';
  const stops = [
    0, 0xffffff, 0,
    Math.max(0, u - width), 0xffffff, 0,
    Math.min(Math.max(u, 0), 1), 0xffffff, alpha,
    Math.min(1, u + width), 0xffffff, 0,
    1, 0xffffff, 0,
  ];
  // Keep the stops in order when the band sits near an end.
  for (let i = 3; i < stops.length; i += 3) if (stops[i] < stops[i - 3]) stops[i] = stops[i - 3];
  c.save();
  c.clipPath(clip, ClipOp.Intersect, true);
  c.drawPath(clip, diagonal(stops, box));
  c.restore();
}

// Materials

const CHROME_SIDE = [0, 0x868c94, 1, 0.22, 0xeef0f2, 1, 0.48, 0x9aa0a8, 1, 0.7, 0xf5f6f7, 1, 1, 0x6a7078, 1];
const DARK_CHROME_SIDE = [0, 0x1e2227, 1, 0.3, 0x9097a0, 1, 0.55, 0x2a2e34, 1, 0.8, 0xc8cdd2, 1, 1, 0x2b3036, 1];
const LID_TOP = [0, 0xf7f8f9, 1, 0.55, 0xd9dde1, 1, 1, 0xbcc1c8, 1];
const BASE_TOP = [0, 0xd6dadf, 1, 0.5, 0xa9afb7, 1, 1, 0xc9cdd3, 1];
const TRAY_TOP = [0, 0x3a3f46, 1, 0.3, 0xe3e6ea, 1, 0.55, 0x4e545c, 1, 0.82, 0xd0d4d9, 1, 1, 0x30353b, 1];

type Slab = { top: SkPath; box: SkRect };

/** Fills the side faces (hull) and returns the top face for the caller to finish. */
function slab(c: SkCanvas, pr: Proj, o: Outline, z: number, thick: number, side: Stops, edge: number): Slab {
  'worklet';
  const top = project(pr, o, z + thick, 1);
  const bottom = project(pr, o, z, 1);
  const h = hull(top.concat(bottom));
  const hp = poly(h);
  c.drawPath(hp, across(side, bounds(h)));
  if (edge > 0) c.drawPath(hp, stroke(0x1a1d21, edge, 0.6));
  return { top: poly(top), box: bounds(top) };
}

/** Fine brushed lines across a face, clipped to it. */
function brushed(c: SkCanvas, pr: Proj, side: number, z: number, clip: SkPath, light: number, dark: number) {
  'worklet';
  const lightP = Skia.Path.Make();
  const darkP = Skia.Path.Make();
  const n = 34;
  const h = side / 2;
  for (let j = 1; j < n; j++) {
    const y = -h + (side * j) / n;
    const v = Math.sin(j * 12.9898) * 43758.5453;
    const a = pt(pr, -h, y, z);
    const b = pt(pr, h, y, z);
    const p = v - Math.floor(v) < 0.55 ? lightP : darkP;
    p.moveTo(a.x, a.y);
    p.lineTo(b.x, b.y);
  }
  c.save();
  c.clipPath(clip, ClipOp.Intersect, true);
  c.drawPath(lightP, stroke(0xffffff, light, 0.7));
  c.drawPath(darkP, stroke(0x2a2f35, dark, 0.6));
  c.restore();
}

// Layers

function drawBase(c: SkCanvas, pr: Proj, f: Frame, st: Statics) {
  'worklet';
  const l = LAYERS[0];
  const s = slab(c, pr, st.outlines[0], 0, l.thick, DARK_CHROME_SIDE, 0.14);
  c.drawPath(s.top, diagonal(BASE_TOP, s.box));
  brushed(c, pr, l.side * UNIT, l.thick, s.top, 0.3, 0.07);
  band(c, s.top, s.box, specular(f.yaw) + 0.1, 0.35, 0.18);
  c.drawPath(s.top, stroke(0xffffff, 0.55, 0.8));
}

function drawTray(c: SkCanvas, pr: Proj, z: number, st: Statics) {
  'worklet';
  const l = LAYERS[1];
  const s = slab(c, pr, st.outlines[1], z, l.thick, DARK_CHROME_SIDE, 0.14);
  const innerTopPts = project(pr, st.trayInner, z + l.thick, 1);
  const innerTop = poly(innerTopPts);
  const innerBottom = poly(project(pr, st.trayInner, z, 1));
  c.drawPath(evenOdd(s.top, innerTop), across(TRAY_TOP, s.box));
  // The far inner wall, seen through the hole.
  c.save();
  c.clipPath(innerTop, ClipOp.Intersect, true);
  c.drawPath(evenOdd(innerTop, innerBottom), across([0, 0x5a6068, 1, 0.5, 0x1f2328, 1, 1, 0x8d939b, 1], bounds(innerTopPts)));
  c.restore();
  c.drawPath(s.top, stroke(0xffffff, 0.7, 0.8));
  c.drawPath(innerTop, stroke(0xffffff, 0.5, 0.7));
}

function drawCore(c: SkCanvas, pr: Proj, z: number, f: Frame, st: Statics) {
  'worklet';
  const l = LAYERS[2];
  const o = st.outlines[2];
  // Teal glow under the glass.
  if (f.open > 0.005) c.drawPath(poly(project(pr, o, z, 1.05)), blurred(solid(GLOW, 0.55 * f.open), 16));

  const s = slab(c, pr, o, z, l.thick, [0, 0x1c8577, 1, 0.45, 0x5bd0be, 1, 1, 0x1f8c7d, 1], 0.1);
  c.drawPath(s.top, diagonal([0, 0x9bebdd, 1, 0.5, 0x5cc9b8, 1, 1, 0x2fa594, 1], s.box));
  // Inner bevel and a fine grid.
  const inset = poly(project(pr, st.coreInset, z + l.thick, 1));
  c.drawPath(inset, stroke(0xffffff, 0.35, 0.8));
  const grid = Skia.Path.Make();
  const h = l.side * UNIT * 0.43;
  for (let j = 1; j < 8; j++) {
    const v = -h + (2 * h * j) / 8;
    const a = pt(pr, v, -h, z + l.thick);
    const b = pt(pr, v, h, z + l.thick);
    const d = pt(pr, -h, v, z + l.thick);
    const e = pt(pr, h, v, z + l.thick);
    grid.moveTo(a.x, a.y);
    grid.lineTo(b.x, b.y);
    grid.moveTo(d.x, d.y);
    grid.lineTo(e.x, e.y);
  }
  c.save();
  c.clipPath(inset, ClipOp.Intersect, true);
  c.drawPath(grid, stroke(0xffffff, 0.16, 0.6));
  c.restore();
  c.drawPath(s.top, stroke(0xd9fff8, 0.9, 0.8));

  // The raised metal disc.
  const dz = z + l.thick;
  const ds = slab(c, pr, st.disc, dz, DISC_THICK, CHROME_SIDE, 0.18);
  const centre = pt(pr, 0, 0, dz + DISC_THICK);
  const rp = Skia.Paint();
  rp.setAntiAlias(true);
  rp.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: centre.x - 8, y: centre.y - 4 },
      DISC_SIDE * UNIT * 0.6,
      [hex(0xfbfcfc, 1), hex(0xd5d9de, 1), hex(0xafb5bc, 1)],
      [0, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(ds.top, rp);
  c.drawPath(moved(st.discRings, pr, dz + DISC_THICK, 0), stroke(0x5a6068, 0.16, 0.7));
  band(c, ds.top, ds.box, specular(f.yaw), 0.5, 0.16);
  c.drawPath(moved(st.discKeyhole, pr, dz + DISC_THICK, -Math.PI / 2), solid(0x3b4148, 0.8));
  c.drawPath(ds.top, stroke(0xffffff, 0.8, 0.8));
}

function drawFrosted(c: SkCanvas, pr: Proj, z: number, f: Frame, st: Statics) {
  'worklet';
  const l = LAYERS[3];
  const s = slab(c, pr, st.outlines[3], z, l.thick, [0, 0xe4efed, 0.8, 0.5, 0xffffff, 0.92, 1, 0xcfe2de, 0.8], 0.06);
  const holeTop = poly(project(pr, st.hole, z + l.thick, 1));
  const holeBottom = poly(project(pr, st.hole, z, 1));
  c.drawPath(evenOdd(s.top, holeTop), diagonal([0, 0xffffff, 0.9, 0.6, 0xf1f7f6, 0.82, 1, 0xddebe8, 0.8], s.box));
  // Frosted dots, as in a print on the glass.
  c.save();
  c.clipPath(s.top, ClipOp.Intersect, true);
  c.drawPath(moved(st.frostDots, pr, z + l.thick, 0), solid(0x8fa6a2, 0.5));
  c.restore();
  // The hole's far wall and its teal rim.
  c.save();
  c.clipPath(holeTop, ClipOp.Intersect, true);
  c.drawPath(evenOdd(holeTop, holeBottom), solid(0xd7eeea, 0.75));
  c.restore();
  c.drawPath(holeTop, stroke(0x4fc7b5, 0.35 + 0.45 * f.open, 1.1));
  c.drawPath(s.top, stroke(0xffffff, 1, 1.2));
  band(c, s.top, s.box, specular(f.yaw) - 0.05, 0.45, 0.12);
}

function drawGlass(c: SkCanvas, pr: Proj, z: number, f: Frame, st: Statics) {
  'worklet';
  const l = LAYERS[4];
  const s = slab(c, pr, st.outlines[4], z, l.thick, [0, 0xa9e3da, 0.55, 0.5, 0xf2fcfa, 0.75, 1, 0x9cddd2, 0.55], 0);
  c.drawPath(s.top, diagonal([0, 0xffffff, 0.42, 0.6, 0xf2faf9, 0.22, 1, 0xddf3ef, 0.2], s.box));
  const tz = z + l.thick;
  // Circuit: traces with round pads, and a field of dots.
  c.drawPath(moved(st.glassDots, pr, tz, 0), solid(0x7d9894, 0.55));
  c.drawPath(moved(st.traces, pr, tz, 0), stroke(0x3fb5a3, 0.75, 1));
  c.drawPath(moved(st.pads, pr, tz, 0), solid(0x3fb5a3, 0.85));
  // Centre ring of the mesh.
  const dash = stroke(0x3fb5a3, 0.6, 1);
  dash.setPathEffect(Skia.PathEffect.MakeDash([2, 2.5], 0));
  c.drawPath(moved(st.meshRing, pr, tz, 0), dash);
  c.drawPath(s.top, stroke(0x5cc6b5, 0.9, 1));
  c.drawPath(poly(project(pr, st.glassInner, tz, 1)), stroke(0xffffff, 0.9, 0.8));
  band(c, s.top, s.box, specular(f.yaw) + 0.05, 0.35, 0.1);
}

function drawLid(c: SkCanvas, pr: Proj, z: number, f: Frame, st: Statics) {
  'worklet';
  const l = LAYERS[5];
  const s = slab(c, pr, st.outlines[5], z, l.thick, CHROME_SIDE, 0.14);
  // Knurled edge: fine ticks on the sides that face the viewer.
  const ticks = Skia.Path.Make();
  const k = st.knurl;
  for (let i = 0; i < k.pts.length; i += 2) {
    if (!facing(pr, k.nrm[i], k.nrm[i + 1])) continue;
    const a = pt(pr, k.pts[i], k.pts[i + 1], z + 1.5);
    const b = pt(pr, k.pts[i], k.pts[i + 1], z + l.thick - 1.5);
    ticks.moveTo(a.x, a.y);
    ticks.lineTo(b.x, b.y);
  }
  c.drawPath(ticks, stroke(0x3a4047, 0.28, 0.6));

  const tz = z + l.thick;
  c.drawPath(s.top, diagonal(LID_TOP, s.box));
  brushed(c, pr, l.side * UNIT, tz, s.top, 0.32, 0.04);
  band(c, s.top, s.box, specular(f.yaw), 0.6, 0.16);

  // Engraved inner frame.
  const frame = poly(project(pr, st.lidFrame, tz, 1));
  c.save();
  c.translate(0, -0.6);
  c.drawPath(frame, stroke(0x5a6068, 0.3, 1.1));
  c.restore();
  c.save();
  c.translate(0, 0.7);
  c.drawPath(frame, stroke(0xffffff, 0.95, 1));
  c.restore();

  // Raised shield with a keyhole; the mark's "up" runs along the lid's edge.
  const sp = moved(st.shield, pr, tz + 1.5, -Math.PI / 2);
  c.save();
  c.translate(0.6, 2.4);
  c.drawPath(sp, blurred(solid(0x2a2f35, 0.3), 1.6));
  c.restore();
  const sb = sp.getBounds();
  c.drawPath(sp, diagonal([0, 0xfdfdfe, 1, 0.5, 0xdce0e4, 1, 1, 0xb4bac1, 1], sb));
  c.save();
  c.clipPath(sp, ClipOp.Intersect, true);
  c.drawPath(moved(st.hatch, pr, tz + 1.5, -Math.PI / 2), stroke(0x4a5057, 0.1, 0.6));
  band(c, sp, sb, specular(f.yaw) + 0.08, 0.55, 0.12);
  c.restore();
  c.drawPath(sp, stroke(0xffffff, 0.95, 0.9));
  c.save();
  c.clipPath(sp, ClipOp.Intersect, true);
  c.translate(0, 1);
  c.drawPath(sp, stroke(0x5a6068, 0.35, 1));
  c.restore();
  const kh = moved(st.keyhole, pr, tz + 1.5, -Math.PI / 2);
  c.drawPath(kh, diagonal([0, 0x6a7078, 1, 1, 0x3a3f46, 1], kh.getBounds()));
  c.save();
  c.translate(0, 0.7);
  c.drawPath(kh, stroke(0xffffff, 0.8, 0.7));
  c.restore();
  c.drawPath(s.top, stroke(0xffffff, 0.9, 0.9));
}

/** Thin glass halo around a layer. */
function drawHalo(c: SkCanvas, pr: Proj, o: Outline, z: number, alpha: number) {
  'worklet';
  if (alpha <= 0.005) return;
  c.drawPath(poly(project(pr, o, z, 1)), stroke(0x6fd3c3, alpha, 1));
}

// Stack

export type Geo = { w: number; h: number; top: number; textTop: number };

export function proj(g: Geo, f: Frame): Proj {
  'worklet';
  return {
    cx: g.w / 2 - SHIFT * f.open,
    cy: g.textTop - CLOSED_LIFT + OPEN_DROP * f.open + f.dip,
    yaw: f.yaw,
    scale: 1 + CLOSED_ZOOM * (1 - Math.min(f.open, 1)),
  };
}

export function drawBackground(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const r = Skia.XYWHRect(0, 0, g.w, g.h);
  c.drawRect(r, shaded([0, BG_TOP, 1, 1, BG_BOTTOM, 1], { x: 0, y: 0 }, { x: 0.1, y: g.h }));
  if (f.open > 0.01) {
    const p = pt(proj(g, f), 0, 0, layerZ(2, f));
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient(p, 170, [hex(GLOW, 0.2 * f.open), hex(GLOW, 0)], null, TileMode.Clamp));
    c.drawRect(r, glow);
  }
}

export function drawStack(c: SkCanvas, g: Geo, f: Frame, st: Statics) {
  'worklet';
  const pr = proj(g, f);
  // Contact and ambient shadows under the base.
  const foot = poly(project(pr, st.foot, -1, 1));
  c.save();
  c.translate(0, 16);
  c.drawPath(foot, blurred(solid(0x1a2026, 0.13), 18));
  c.restore();
  c.save();
  c.translate(0, 4);
  c.drawPath(foot, blurred(solid(0x1a2026, 0.22), 4));
  c.restore();

  drawBase(c, pr, f, st);
  drawHalo(c, pr, st.halo114, layerZ(1, f) + 3, 0.45 * f.open);
  drawTray(c, pr, layerZ(1, f), st);
  drawCore(c, pr, layerZ(2, f), f, st);
  drawFrosted(c, pr, layerZ(3, f), f, st);
  drawHalo(c, pr, st.halo120, layerZ(4, f) + 1, 0.5 * f.open);
  drawGlass(c, pr, layerZ(4, f), f, st);
  drawLid(c, pr, layerZ(5, f), f, st);
}

/** Where callout k's line starts: a plane point on its layer's top face. */
export function calloutPoint(g: Geo, f: Frame, k: number): SkPoint {
  'worklet';
  const i = CALLOUTS[k].layer;
  return pt(proj(g, f), 0.26 * UNIT, -0.26 * UNIT, layerZ(i, f) + LAYERS[i].thick);
}

export function calloutX(g: Geo, widest: number) {
  'worklet';
  return Math.min(g.w / 2 - SHIFT + 0.707 * UNIT + 16, g.w - 18 - widest);
}

/** Leader lines and anchor dots (the labels are native text views). */
export function drawCallouts(c: SkCanvas, g: Geo, f: Frame, widest: number) {
  'worklet';
  const lx = calloutX(g, widest);
  for (let k = 0; k < 3; k++) {
    const v = f.callout[k];
    if (v <= 0.002) continue;
    const p = calloutPoint(g, f, k);
    const x1 = p.x + (lx - 8 - p.x) * v;
    c.drawLine(p.x, p.y, x1, p.y, stroke(LINE, 0.95, 1));
    const r = 3.2 * Math.min(1, v * 1.6);
    c.drawCircle(p.x, p.y, r, solid(0xffffff, 1));
    c.drawCircle(p.x, p.y, r, stroke(TEAL, Math.min(1, v * 1.6), 1.4));
  }
}
