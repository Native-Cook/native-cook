// Layout, the crayon's scripted path, and the rubbed-field grid of the Crayon Rubbing Card. The
// grid is GX × GY cells over the paper, 0 (untouched) … 1 (fully rubbed); the shader reads it as a
// smooth image. Everything reached from the UI thread is a named worklet declared above its callers.

/** Loop period in seconds. */
export const LOOP = 9;

/** The design box (393 × 759); the screen scales it to fit. */
export const DESIGN_W = 393;
export const DESIGN_H = 759;

/** The hero canvas (design points) and, inside it, the bronze plaque and the paper over it. */
export const CANVAS = { x: 0, y: 104, w: DESIGN_W, h: 500 };
export const PLAQUE = { x: 24, y: 8, w: 345, h: 432 };
export const PAPER = { x: 34, y: 16, w: 325, h: 416 };
/** Where the crayon lies when nobody rubs (canvas points: its tip, and its angle in degrees). */
export const CRAYON_REST = { x: 176, y: 474, angle: -8 };
/** Cell size of the rubbed field, in points. */
export const CELL = 8;
export const GX = Math.ceil(PAPER.w / CELL);
export const GY = Math.ceil(PAPER.h / CELL);
/** The crayon's side is this wide on the paper (radius of what one stroke covers, in points). */
export const REACH = 17;
/** Strokes of the demo's zig-zag from the top of the sheet to the bottom. */
const STROKES = 14;
/** Up to this many passes of the demo's crayon are remembered per cell. */
const PASSES = 4;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/**
 * The demo's scrubbing hand, in paper points, at a time inside the scrub (0.35–4.0 s): a zig-zag of
 * 14 strokes from top to bottom (each stroke slows at its turn, like a hand), then a quick small
 * circling over the heron to deepen it. Returns [x, y].
 */
export function scrubAt(t: number) {
  'worklet';
  if (t < 3.4) {
    const s = seg(t, 0.35, 3.4);
    const y = 22 + (PAPER.h - 44) * s;
    const x = PAPER.w / 2 + (PAPER.w / 2 - 24) * Math.sin(Math.PI * STROKES * s - Math.PI / 2);
    return [x, y + 6 * Math.sin(Math.PI * 2 * STROKES * s)];
  }
  // From the bottom-left back up over the heron, circling, 3.4–4.0 s.
  const u = seg(t, 3.4, 4.0);
  const cx = 176 - 30 * (1 - u);
  const cy = 190 + 150 * Math.pow(1 - u, 2);
  return [cx + 34 * Math.cos(Math.PI * 6 * u + Math.PI), cy + 22 * Math.sin(Math.PI * 6 * u)];
}

/**
 * The crayon's tip and angle at loop time t, in canvas points: it rests beside the plaque, is
 * picked up 0–0.35 s (0.25 s after the fresh sheet has landed), scrubs to
 * 4.0 s, and is laid back down 4.0–4.55 s. Returns [x, y, angle,
 * contact] where contact is 1 while it presses on the paper.
 */
export function crayonAt(t: number) {
  'worklet';
  const start = scrubAt(0.35);
  const end = scrubAt(4.0);
  const sx = PAPER.x + start[0];
  const sy = PAPER.y + start[1];
  const ex = PAPER.x + end[0];
  const ey = PAPER.y + end[1];
  if (t < 0.35) {
    // Picked up from rest, easing in, so the loop's seam falls on a still crayon.
    const u = easeInOutCubic(seg(t, 0, 0.35));
    const lift = Math.sin(Math.PI * u) * 26;
    return [CRAYON_REST.x + (sx - CRAYON_REST.x) * u, CRAYON_REST.y + (sy - CRAYON_REST.y) * u - lift, CRAYON_REST.angle + (-34 - CRAYON_REST.angle) * u, 0];
  }
  if (t < 4.0) {
    const p = scrubAt(t);
    // The hand turns the crayon a little with each stroke.
    const sway = 6 * Math.sin(Math.PI * STROKES * seg(t, 0.35, 3.4));
    return [PAPER.x + p[0], PAPER.y + p[1], -34 + sway, 1];
  }
  const u = easeInOutCubic(seg(t, 4.0, 4.55));
  const lift = Math.sin(Math.PI * u) * 26;
  return [ex + (CRAYON_REST.x - ex) * u, ey + (CRAYON_REST.y - ey) * u - lift, -34 + (CRAYON_REST.angle + 34) * u, 0];
}

/** The rubbed paper peels off the plaque 7.3–8.75 s, leaving a fresh sheet. */
export function autoPeel(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, 7.3, 8.75));
}

/** The "Collected" badge: in 4.0–4.6 s (a spring-like pop), out as the sheet peels. */
export function autoBadge(t: number) {
  'worklet';
  const u = seg(t, 4.0, 4.6);
  const pop = u >= 1 ? 1 : 1 - Math.exp(-u * 6) * Math.cos(u * 9);
  return pop * (1 - seg(t, 7.3, 7.7));
}

/**
 * When the demo's crayon passes over each cell, worked out once on the JS thread by following the
 * path at 240 Hz: per cell, up to PASSES start times (-1 when unused) and how hard each pass pressed.
 */
function buildPasses() {
  const times = new Array(GX * GY * PASSES).fill(-1);
  const amounts = new Array(GX * GY * PASSES).fill(0);
  const last = new Array(GX * GY).fill(-10);
  const r = Math.ceil(REACH / CELL);
  for (let t = 0.35; t <= 4.0; t += 1 / 240) {
    const p = scrubAt(t);
    const cx = p[0] / CELL;
    const cy = p[1] / CELL;
    for (let gy = Math.floor(cy - r); gy <= Math.ceil(cy + r); gy += 1) {
      for (let gx = Math.floor(cx - r); gx <= Math.ceil(cx + r); gx += 1) {
        if (gx < 0 || gy < 0 || gx >= GX || gy >= GY) continue;
        const d = Math.hypot((gx + 0.5) * CELL - p[0], (gy + 0.5) * CELL - p[1]);
        if (d > REACH) continue;
        const i = gy * GX + gx;
        if (t - last[i] > 0.12) {
          for (let k = 0; k < PASSES; k += 1) {
            if (times[i * PASSES + k] < 0) {
              times[i * PASSES + k] = t;
              // Softer at the stroke's edge, a little uneven from stroke to stroke.
              amounts[i * PASSES + k] = (0.74 + 0.16 * Math.sin(i * 1.7 + k * 2.3)) * (1 - 0.35 * (d / REACH));
              break;
            }
          }
        }
        last[i] = t;
      }
    }
  }
  return { times, amounts };
}

const BUILT = buildPasses();
export const PASS_TIMES = BUILT.times;
export const PASS_AMOUNTS = BUILT.amounts;
export const PASS_COUNT = PASSES;

/** Fills `grid` with the demo's rubbed field at loop time t (all clear once the sheet is gone). */
export function autoGrid(t: number, grid: number[]) {
  'worklet';
  const fresh = t >= 8.75;
  for (let i = 0; i < GX * GY; i += 1) {
    let v = 0;
    if (!fresh) {
      for (let k = 0; k < PASS_COUNT; k += 1) {
        const at = PASS_TIMES[i * PASS_COUNT + k];
        if (at < 0) break;
        v += PASS_AMOUNTS[i * PASS_COUNT + k] * clamp01((t - at) / 0.09);
      }
    }
    grid[i] = v > 1 ? 1 : v;
  }
}

/** Rubs along a finger's segment (paper points): cells under the crayon's side darken with travel. */
export function rubSegment(grid: number[], x0: number, y0: number, x1: number, y1: number) {
  'worklet';
  const len = Math.hypot(x1 - x0, y1 - y0);
  const steps = Math.max(1, Math.ceil(len / 4));
  const r = Math.ceil(REACH / CELL);
  for (let s = 1; s <= steps; s += 1) {
    const px = x0 + ((x1 - x0) * s) / steps;
    const py = y0 + ((y1 - y0) * s) / steps;
    const cx = px / CELL;
    const cy = py / CELL;
    for (let gy = Math.floor(cy - r); gy <= Math.ceil(cy + r); gy += 1) {
      for (let gx = Math.floor(cx - r); gx <= Math.ceil(cx + r); gx += 1) {
        if (gx < 0 || gy < 0 || gx >= GX || gy >= GY) continue;
        const d = Math.hypot((gx + 0.5) * CELL - px, (gy + 0.5) * CELL - py);
        if (d > REACH) continue;
        const i = gy * GX + gx;
        const add = 0.05 * (1 - 0.35 * (d / REACH));
        grid[i] = Math.min(1, grid[i] + add);
      }
    }
  }
}

/** Share of the relief revealed: the rubbed field weighted by how much relief each cell holds. */
export function revealed(grid: number[], weights: number[], total: number) {
  'worklet';
  if (total <= 0) return 0;
  let sum = 0;
  for (let i = 0; i < GX * GY; i += 1) sum += grid[i] * weights[i];
  return sum / total;
}

/** Packs the grid into RGBA bytes (the field in every channel) for the shader's image. */
export function packGrid(grid: number[], bytes: Uint8Array) {
  'worklet';
  for (let i = 0; i < GX * GY; i += 1) {
    const v = Math.round(clamp01(grid[i]) * 255);
    bytes[i * 4] = v;
    bytes[i * 4 + 1] = v;
    bytes[i * 4 + 2] = v;
    bytes[i * 4 + 3] = 255;
  }
}
