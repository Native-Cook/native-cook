import type { Face, Plan } from './planeGeometry';

/*
 Paper Plane Steps — the autoPlay script and the per-frame mesh.

 LOOP = 10 s:
   0.0 – 1.6   rest on sheet 1.
   1.6 – 3.4   sheet 1 folds into a plane: four folds of 0.45 s (ease-in-out), each flap turning
               about its crease in 3D with perspective, its shading following its angle.
   3.25 – 3.5  the plane tucks its wings (width × 0.62) and lifts (scale 1 → 0.86).
   3.4 – 4.6   it flies off along a curve (a small dip, then up and out at the top right), banking
               into the turn, shrinking with distance, a dashed trail behind it.
   4.4 – 5.2   sheet 2 slides up from below (ease-out back, 3 % overshoot, 4° → 0° tilt).
   5.2 – 6.4   hold.
   6.4 – 7.6   sheet 2 folds (four folds of 0.3 s); 7.45 – 7.7 tuck; 7.6 – 8.6 flight.
   8.6 – 9.4   sheet 1 slides back up, so the loop rests where it began; 9.4 – 10 rest.

 State, as flat numbers: [sheet, fold 0..4, tuck 0..1, flight 0..1 (−1 none), incoming sheet,
 incoming progress 0..1 (−1 none), dot position].
*/

export const LOOP = 10;
export const PERSPECTIVE = 800;
export const IDLE_BEFORE_RETURN = 2;
export const STEPS = 3;

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOutBack(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  const s = 0.9;
  return 1 + (s + 1) * Math.pow(c - 1, 3) + s * Math.pow(c - 1, 2);
}

function folds(t: number, start: number, each: number) {
  'worklet';
  return clamp((t - start) / each, 0, 4);
}

export function script(t: number): number[] {
  'worklet';
  const dot = easeInOut(seg(t, 4.5, 4.9)) - easeInOut(seg(t, 8.6, 9.0));
  if (t < 4.4) return [0, folds(t, 1.6, 0.45), easeInOut(seg(t, 3.25, 3.5)), t < 3.4 ? -1 : seg(t, 3.4, 4.6), 1, -1, dot];
  if (t < 5.2) return [0, 4, 1, seg(t, 3.4, 4.6), 1, seg(t, 4.4, 5.2), dot];
  if (t < 8.6) return [1, folds(t, 6.4, 0.3), easeInOut(seg(t, 7.45, 7.7)), t < 7.6 ? -1 : seg(t, 7.6, 8.6), 0, -1, dot];
  if (t < 9.4) return [1, 4, 1, 1, 0, seg(t, 8.6, 9.4), dot];
  return [0, 0, 0, -1, 1, -1, dot];
}

/** The flight path from the sheet's centre (sx, sy): [x, y, heading (rad, 0 = nose up), scale, bank]. */
export function flight(f: number, sx: number, sy: number, w: number): [number, number, number, number, number] {
  'worklet';
  const u = easeInOut(clamp(f, 0, 1) * 0.92 + 0.08 * clamp(f, 0, 1) * clamp(f, 0, 1));
  const p0x = sx;
  const p0y = sy;
  const p1x = sx - 70;
  const p1y = sy + 80;
  const p2x = sx + w * 0.6;
  const p2y = sy - 120;
  const p3x = w + 140;
  const p3y = -220;
  const a = 1 - u;
  const x = a * a * a * p0x + 3 * a * a * u * p1x + 3 * a * u * u * p2x + u * u * u * p3x;
  const y = a * a * a * p0y + 3 * a * a * u * p1y + 3 * a * u * u * p2y + u * u * u * p3y;
  const dx = 3 * a * a * (p1x - p0x) + 6 * a * u * (p2x - p1x) + 3 * u * u * (p3x - p2x);
  const dy = 3 * a * a * (p1y - p0y) + 6 * a * u * (p2y - p1y) + 3 * u * u * (p3y - p2y);
  // Nose along the path once it moves; it starts pointing up the screen.
  const heading = u < 0.001 ? 0 : Math.atan2(dy, dx) + Math.PI / 2;
  const blend = clamp(u * 6, 0, 1);
  return [x, y, heading * blend, 0.86 - 0.62 * u, 0.55 * Math.sin(Math.PI * u)];
}

/**
 * The paper this frame as triangles with texture points (in atlas points; the image is drawn as
 * is): the faces lying flat, and the flap turning over (drawn after them). Shading is not baked in:
 * the flap's brightness and the outlines it is laid over are returned for an overlay.
 */
export type Out = {
  sv: { x: number; y: number }[];
  st: { x: number; y: number }[];
  mv: { x: number; y: number }[];
  mt: { x: number; y: number }[];
  /** Screen outlines (x, y pairs, NaN between faces): the flap's, and every face's. */
  mpoly: number[];
  all: number[];
  /** The flap's brightness (1 = untouched). */
  flap: number;
  /** Outlines flattened onto the table, for the shadow. */
  sh: number[];
};

export type Xf = { ox: number; oy: number; cx: number; cy: number; sx: number; sy: number; rot: number; scale: number; bank: number };

/** A sheet point (with height z toward the viewer, negative) on the screen. */
export function screenOf(xf: Xf, x: number, y: number, z: number): [number, number] {
  'worklet';
  const lx = (x - xf.cx) * xf.sx;
  const ly = (y - xf.cy) * xf.sy;
  const s = PERSPECTIVE / (PERSPECTIVE + z);
  const px = lx * s * xf.scale;
  const py = ly * s * xf.scale;
  const ca = Math.cos(xf.rot);
  const sa = Math.sin(xf.rot);
  return [xf.ox + px * ca - py * sa, xf.oy + px * sa + py * ca];
}

function addFace(o: Out, xf: Xf, f: Face, sw: number, step: number, moving: boolean, line: number[] | null, theta: number, flipped: boolean) {
  'worklet';
  const n = f.pts.length / 2;
  const sx: number[] = [];
  const sy: number[] = [];
  for (let i = 0; i < n; i += 1) {
    let x = f.pts[i * 2];
    let y = f.pts[i * 2 + 1];
    let z = 0;
    if (line) {
      // Turn the point about the crease by theta: its offset across the line shrinks by cos θ and
      // rises toward the viewer by sin θ.
      const ux = line[2] - line[0];
      const uy = line[3] - line[1];
      const l = Math.hypot(ux, uy);
      const dx = ux / l;
      const dy = uy / l;
      const along = (x - line[0]) * dx + (y - line[1]) * dy;
      const fx = line[0] + dx * along;
      const fy = line[1] + dy * along;
      const px = x - fx;
      const py = y - fy;
      const c = Math.cos(theta);
      x = fx + px * c;
      y = fy + py * c;
      z = -Math.hypot(px, py) * Math.sin(theta);
    }
    const p = screenOf(xf, x, y, z);
    sx.push(p[0]);
    sy.push(p[1]);
    o.all.push(p[0], p[1]);
    if (moving) o.mpoly.push(p[0], p[1]);
    // The shadow falls on the table: the outline flattened, the lift kept as an offset.
    const sp = screenOf(xf, x, y, 0);
    o.sh.push(sp[0] - z * 0.05, sp[1] - z * 0.09);
  }
  o.all.push(NaN, NaN);
  if (moving) o.mpoly.push(NaN, NaN);
  o.sh.push(NaN, NaN);
  const back = (f.back === 1) !== flipped;
  const vs = moving ? o.mv : o.sv;
  const ts = moving ? o.mt : o.st;
  const ox = back ? 3 * sw : step * sw;
  for (let i = 1; i < n - 1; i += 1) {
    const ids = [0, i, i + 1];
    for (const k of ids) {
      vs.push({ x: sx[k], y: sy[k] });
      ts.push({ x: ox + f.uv[k * 2], y: f.uv[k * 2 + 1] });
    }
  }
}

/**
 * The paper this frame: static faces, then the turning flap (its layers reversed once it passes
 * upright). `fold` is 0..4. The flap's brightness follows its angle, with a glint as it catches the
 * light on the way over.
 */
export function paperMesh(plan: Plan, fold: number, step: number, xf: Xf): Out {
  'worklet';
  const o: Out = { sv: [], st: [], mv: [], mt: [], mpoly: [], all: [], flap: 1, sh: [] };
  if (fold >= 4) {
    for (const f of plan.final) addFace(o, xf, f, plan.sw, step, false, null, 0, false);
    return o;
  }
  const k = Math.floor(fold);
  const fd = plan.folds[k];
  const theta = Math.PI * easeInOut(fold - k);
  for (const f of fd.statics) addFace(o, xf, f, plan.sw, step, false, null, 0, false);
  const flipped = theta > Math.PI / 2;
  const glint = 0.12 * Math.exp(-Math.pow((theta - 1.05) / 0.28, 2));
  o.flap = (flipped ? 0.8 - 0.14 * Math.cos(theta) : 0.8 + 0.2 * Math.cos(theta)) + glint;
  const line = [fd.ax, fd.ay, fd.bx, fd.by];
  const n = fd.movers.length;
  for (let i = 0; i < n; i += 1) {
    const f = fd.movers[flipped ? n - 1 - i : i];
    addFace(o, xf, f, plan.sw, step, true, line, theta, flipped);
  }
  return o;
}

/** The tint laid over a face for a brightness b (1 = untouched): cool shadow below 1, light above. */
export function tintOf(b: number) {
  'worklet';
  if (b >= 1) return `rgba(255,250,236,${clamp(b - 1, 0, 0.3).toFixed(3)})`;
  return `rgba(22,20,38,${clamp(1 - b, 0, 0.7).toFixed(3)})`;
}
