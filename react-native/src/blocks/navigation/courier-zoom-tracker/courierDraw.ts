// The per-frame geometry of the 3D layer: the buildings in view, raised by the camera's tilt, and the van among them,
// in painter's order. It only computes polygons ([argb colour, point count, x0, y0, x1, y1, …] one after another), so
// the Skia layer draws them and a test can draw the same list as SVG. Pure functions of the camera; no allocation per
// building beyond the output list.

import { BLOCK_START, BUILDINGS, BX, BY, DOOR_X, I0, I1, J0, J1, NJT } from "./courierCity";
import { EXTRUDE_X, heightScale, L_H, L_PANEL, L_W, smooth01 } from "./courierMotion";

/** Most buildings drawn in one frame (the nearest first is not needed: the view is small whenever they are tall). */
const MAX_BUILDINGS = 240;
/** Below this many points per metre of height the flat footprints in the map picture are enough. */
const MIN_HEIGHT_SCALE = 0.22;
const FLOOR = 3.2;

export function argb(r: number, g: number, b: number, a: number): number {
  "worklet";
  const ia = Math.round(Math.min(Math.max(a, 0), 1) * 255);
  return ((ia << 24) | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b)) >>> 0;
}

/** Appends one polygon. */
function poly(out: number[], color: number, pts: number[], n: number): void {
  "worklet";
  out.push(color, n);
  for (let k = 0; k < n * 2; k++) out.push(pts[k]);
}

/** A box: four screen corners of its footprint (clockwise on screen) in c[o .. o + 7], raised by (ex, ey) per metre
 * from z0 to z1 metres. Draws the walls that face the viewer, shaded by a light from the upper left, then the top.
 * `wall` and `top` are [r, g, b]. With `unit` > 0 (screen points per metre on the ground) the walls get windows, one
 * per 3.2 m of wall on each 3.2 m floor. */
function box(out: number[], c: number[], o: number, ex: number, ey: number, z0: number, z1: number, wall: number[], top: number[], a: number, unit: number): void {
  "worklet";
  const q = [0, 0, 0, 0, 0, 0, 0, 0];
  for (let e = 0; e < 4; e++) {
    const ax = c[o + e * 2];
    const ay = c[o + e * 2 + 1];
    const bx = c[o + ((e + 1) % 4) * 2];
    const by = c[o + ((e + 1) % 4) * 2 + 1];
    const dx = bx - ax;
    const dy = by - ay;
    const dl = Math.hypot(dx, dy) || 1;
    // outward normal of a clockwise edge on a y-down screen
    const nx = dy / dl;
    const ny = -dx / dl;
    if (nx * EXTRUDE_X - ny >= 0) continue; // faces away from the viewer
    const lit = 0.92 + 0.08 * (nx * -0.6 + ny * -0.8);
    q[0] = ax + ex * z0;
    q[1] = ay + ey * z0;
    q[2] = bx + ex * z0;
    q[3] = by + ey * z0;
    q[4] = bx + ex * z1;
    q[5] = by + ey * z1;
    q[6] = ax + ex * z1;
    q[7] = ay + ey * z1;
    poly(out, argb(wall[0] * lit, wall[1] * lit, wall[2] * lit, a), q, 4);
    if (unit > 0) {
      // the wall's length in metres (the squash only shortens walls that run up the screen; close enough for windows)
      const lenM = dl / unit;
      const cols = Math.min(Math.floor(lenM / 3.2), 10);
      const floors = Math.min(Math.floor((z1 - z0) / FLOOR), 6);
      if (cols < 1 || floors < 1) continue;
      const g = argb(wall[0] * lit * 0.58 + 40, wall[1] * lit * 0.62 + 46, wall[2] * lit * 0.68 + 58, a);
      const hw = 0.62 / lenM;
      for (let f = 0; f < floors; f++) {
        const za = z0 + f * FLOOR + 1.0;
        const zb = za + 1.45;
        for (let w = 0; w < cols; w++) {
          const m = (w + 0.5) / cols;
          const u0 = m - hw;
          const u1 = m + hw;
          q[0] = ax + dx * u0 + ex * za;
          q[1] = ay + dy * u0 + ey * za;
          q[2] = ax + dx * u1 + ex * za;
          q[3] = ay + dy * u1 + ey * za;
          q[4] = ax + dx * u1 + ex * zb;
          q[5] = ay + dy * u1 + ey * zb;
          q[6] = ax + dx * u0 + ex * zb;
          q[7] = ay + dy * u0 + ey * zb;
          poly(out, g, q, 4);
        }
      }
    }
  }
  for (let k = 0; k < 4; k++) {
    q[k * 2] = c[o + k * 2] + ex * z1;
    q[k * 2 + 1] = c[o + k * 2 + 1] + ey * z1;
  }
  poly(out, argb(top[0], top[1], top[2], a), q, 4);
}

/** Screen corners of a world rectangle (x0, y0)–(x1, y1), clockwise. */
function rectCorners(x0: number, y0: number, x1: number, y1: number, cam: number[], c: number[], o: number): void {
  "worklet";
  const xs = [x0, x1, x1, x0];
  const ys = [y0, y0, y1, y1];
  for (let k = 0; k < 4; k++) {
    const dx = (xs[k] - cam[0]) * cam[2];
    const dy = (ys[k] - cam[1]) * cam[2];
    c[o + k * 2] = cam[5] + dx * cam[3] - dy * cam[4];
    c[o + k * 2 + 1] = cam[6] + (dx * cam[4] + dy * cam[3]) * cam[7];
  }
}

/** Screen corners of a rectangle along a heading: from `back` to `front` metres along (hx, hy) and ±half across. */
function orientedCorners(x: number, y: number, hx: number, hy: number, back: number, front: number, half: number, cam: number[], c: number[], o: number): void {
  "worklet";
  // right of travel on a y-down map
  const rx = -hy;
  const ry = hx;
  const al = [back, front, front, back];
  const ac = [-half, -half, half, half];
  for (let k = 0; k < 4; k++) {
    const wx = x + hx * al[k] + rx * ac[k];
    const wy = y + hy * al[k] + ry * ac[k];
    const dx = (wx - cam[0]) * cam[2];
    const dy = (wy - cam[1]) * cam[2];
    c[o + k * 2] = cam[5] + dx * cam[3] - dy * cam[4];
    c[o + k * 2 + 1] = cam[6] + (dx * cam[4] + dy * cam[3]) * cam[7];
  }
  // keep the corners clockwise on screen whatever the heading
  const cross = (c[o + 2] - c[o]) * (c[o + 5] - c[o + 1]) - (c[o + 3] - c[o + 1]) * (c[o + 4] - c[o]);
  if (cross < 0) {
    for (let k = 0; k < 2; k++) {
      const t = c[o + 2 + k];
      c[o + 2 + k] = c[o + 6 + k];
      c[o + 6 + k] = t;
    }
  }
}

const VAN_BODY = [64, 118, 255];
const VAN_SIDE = [38, 88, 222];
const VAN_GLASS = [28, 36, 52];
const VAN_STRIPE = [255, 255, 255];

/** The van: a box body, a lower cab with its windscreen, a blue stripe; at (x, y) heading (hx, hy). */
function van(out: number[], x: number, y: number, hx: number, hy: number, cam: number[], ex: number, ey: number, a: number): void {
  "worklet";
  const c = [0, 0, 0, 0, 0, 0, 0, 0];
  const cab = [0, 0, 0, 0, 0, 0, 0, 0];
  orientedCorners(x, y, hx, hy, -3.1, 1.3, 1.1, cam, c, 0);
  orientedCorners(x, y, hx, hy, 1.3, 3.0, 1.05, cam, cab, 0);
  // which part is nearer the viewer: the one further along (EXTRUDE_X, -1) is behind
  const kb = (c[0] + c[4]) * EXTRUDE_X - (c[1] + c[5]);
  const kc = (cab[0] + cab[4]) * EXTRUDE_X - (cab[1] + cab[5]);
  const pts = [0, 0, 0, 0, 0, 0, 0, 0];
  if (kc > kb) {
    box(out, cab, 0, ex, ey, 0.35, 2.05, VAN_SIDE, VAN_BODY, a, -1);
    box(out, c, 0, ex, ey, 0.35, 2.7, VAN_SIDE, VAN_BODY, a, -1);
  } else {
    box(out, c, 0, ex, ey, 0.35, 2.7, VAN_SIDE, VAN_BODY, a, -1);
    box(out, cab, 0, ex, ey, 0.35, 2.05, VAN_SIDE, VAN_BODY, a, -1);
  }
  // the windscreen: the front 70 % of the cab's top, tinted
  orientedCorners(x, y, hx, hy, 1.75, 2.85, 0.92, cam, pts, 0);
  for (let k = 0; k < 4; k++) {
    pts[k * 2] += ex * 2.06;
    pts[k * 2 + 1] += ey * 2.06;
  }
  poly(out, argb(VAN_GLASS[0], VAN_GLASS[1], VAN_GLASS[2], a), pts, 4);
  // a white stripe along the roof
  orientedCorners(x, y, hx, hy, -2.8, 1.0, 0.22, cam, pts, 0);
  for (let k = 0; k < 4; k++) {
    pts[k * 2] += ex * 2.71;
    pts[k * 2 + 1] += ey * 2.71;
  }
  poly(out, argb(VAN_STRIPE[0], VAN_STRIPE[1], VAN_STRIPE[2], a), pts, 4);
}

/** Inverts a screen point to the ground (metres): [x, y]. */
export function toWorld(sx: number, sy: number, cam: number[], out: number[]): void {
  "worklet";
  const x = sx - cam[5];
  const y = (sy - cam[6]) / cam[7];
  out[0] = cam[0] + (x * cam[3] + y * cam[4]) / cam[2];
  out[1] = cam[1] + (-x * cam[4] + y * cam[3]) / cam[2];
}

/** The 3D layer for one frame into `out`. `vanState` is [x, y, hx, hy, visibility, model 0..1]; the van joins the
 * painter's order only as a model (as a dot it is drawn on top by the caller). Returns the number of buildings. */
export function cityFrame(cam: number[], lay: number[], homeIndex: number, vanState: number[], out: number[]): number {
  "worklet";
  // buildings rise out of their footprints as the camera leans in (from nothing, so they never pop up)
  const h0 = heightScale(cam);
  const hs = h0 * smooth01((h0 - MIN_HEIGHT_SCALE) / 0.35);
  const showVan = vanState[4] * vanState[5] > 0.003;
  if (h0 < MIN_HEIGHT_SCALE && !showVan) return 0;
  const ex = EXTRUDE_X * hs;
  const ey = -hs;
  const W = lay[L_W];
  const bottom = Math.min(lay[L_PANEL] + 60, lay[L_H]);
  // the ground under the visible screen, padded for roofs that rise into view from below
  const w = [0, 0];
  let x0 = 1e9;
  let y0 = 1e9;
  let x1 = -1e9;
  let y1 = -1e9;
  const sxs = [-30, W + 30, W + 30, -30];
  const sys = [-30, -30, bottom + 50 * hs, bottom + 50 * hs];
  for (let k = 0; k < 4; k++) {
    toWorld(sxs[k], sys[k], cam, w);
    if (w[0] < x0) x0 = w[0];
    if (w[0] > x1) x1 = w[0];
    if (w[1] < y0) y0 = w[1];
    if (w[1] > y1) y1 = w[1];
  }
  const ia = Math.max(I0, Math.floor(x0 / BX));
  const ib = Math.min(I1 - 1, Math.floor(x1 / BX));
  const ja = Math.max(J0, Math.floor(y0 / BY));
  const jb = Math.min(J1 - 1, Math.floor(y1 / BY));
  // collect the buildings in view with their screen corners and depth keys
  const idx: number[] = [];
  const key: number[] = [];
  const cor: number[] = [];
  const corner = [0, 0, 0, 0, 0, 0, 0, 0];
  if (h0 > MIN_HEIGHT_SCALE) {
    for (let i = ia; i <= ib && idx.length < MAX_BUILDINGS; i++) {
      for (let j = ja; j <= jb && idx.length < MAX_BUILDINGS; j++) {
        const bi = (i - I0) * NJT + (j - J0);
        const s0 = BLOCK_START[bi];
        const s1 = BLOCK_START[bi + 1];
        for (let b = s0; b < s1 && idx.length < MAX_BUILDINGS; b++) {
          rectCorners(BUILDINGS[b * 5], BUILDINGS[b * 5 + 1], BUILDINGS[b * 5 + 2], BUILDINGS[b * 5 + 3], cam, corner, 0);
          const h = BUILDINGS[b * 5 + 4];
          let mnx = 1e9;
          let mxx = -1e9;
          let mny = 1e9;
          let mxy = -1e9;
          for (let k = 0; k < 4; k++) {
            const px = corner[k * 2];
            const py = corner[k * 2 + 1];
            if (px < mnx) mnx = px;
            if (px > mxx) mxx = px;
            if (py < mny) mny = py;
            if (py > mxy) mxy = py;
          }
          // the roof is up and a little right of the base
          if (mxx + ex * h < -2 || mnx > W + 2 || mxy < -2 || mny + ey * h > bottom) continue;
          idx.push(b);
          key.push(((corner[0] + corner[4]) / 2) * EXTRUDE_X - (corner[1] + corner[5]) / 2);
          for (let k = 0; k < 8; k++) cor.push(corner[k]);
        }
      }
    }
  }
  const n = idx.length;
  // the van takes part in the order as building number -1
  if (showVan) {
    const vc = [0, 0, 0, 0, 0, 0, 0, 0];
    orientedCorners(vanState[0], vanState[1], vanState[2], vanState[3], -3.1, 3.0, 1.1, cam, vc, 0);
    idx.push(-1);
    key.push(((vc[0] + vc[4]) / 2) * EXTRUDE_X - (vc[1] + vc[5]) / 2);
    for (let k = 0; k < 8; k++) cor.push(vc[k]);
  }
  // painter's order: the furthest along the raise direction first (insertion sort of a permutation)
  const order: number[] = [];
  for (let k = 0; k < idx.length; k++) {
    let p = order.length;
    while (p > 0 && key[order[p - 1]] < key[k]) p--;
    order.splice(p, 0, k);
  }
  // windows only close in, where a handful of buildings are in view
  const unit = hs > 1.9 && n <= 28 ? cam[2] : -1;
  const tallWall = [208, 214, 224];
  const tallTop = [234, 238, 243];
  const homeWall = [238, 196, 160];
  const homeTop = [250, 222, 196];
  const wall = [0, 0, 0];
  const top = [0, 0, 0];
  for (let o = 0; o < order.length; o++) {
    const k = order[o];
    const b = idx[k];
    if (b < 0) {
      van(out, vanState[0], vanState[1], vanState[2], vanState[3], cam, ex, ey, vanState[4] * vanState[5]);
      continue;
    }
    const h = BUILDINGS[b * 5 + 4];
    const v = ((b * 7919) % 97) / 97;
    if (b === homeIndex) {
      for (let c = 0; c < 3; c++) {
        wall[c] = homeWall[c];
        top[c] = homeTop[c];
      }
    } else if (h > 24) {
      for (let c = 0; c < 3; c++) {
        wall[c] = tallWall[c] - 6 * v;
        top[c] = tallTop[c] - 4 * v;
      }
    } else if (h <= 12 && BUILDINGS[b * 5 + 2] - BUILDINGS[b * 5] < 14) {
      // a townhouse: one of four painted fronts
      const f = (b * 3) % 4;
      const fr = f === 0 ? 240 : f === 1 ? 232 : f === 2 ? 220 : 236;
      const fg = f === 0 ? 232 : f === 1 ? 218 : f === 2 ? 228 : 226;
      const fb = f === 0 ? 220 : f === 1 ? 204 : f === 2 ? 232 : 218;
      wall[0] = fr;
      wall[1] = fg;
      wall[2] = fb;
      top[0] = 246 - 3 * v;
      top[1] = 243 - 3 * v;
      top[2] = 238 - 2 * v;
    } else {
      wall[0] = 229 - 6 * v;
      wall[1] = 223 - 6 * v;
      wall[2] = 213 - 3 * v;
      top[0] = 250 - 4 * v;
      top[1] = 248 - 4 * v;
      top[2] = 243 - 2 * v;
    }
    box(out, cor, k * 8, ex, ey, 0, h, wall, top, 1, unit);
    if (b === homeIndex) {
      // the front door, on the south wall, with its step
      const dx0 = BUILDINGS[b * 5];
      const dx1 = BUILDINGS[b * 5 + 2];
      const fa = (DOOR_X - 0.75 - dx0) / (dx1 - dx0);
      const fb = (DOOR_X + 0.75 - dx0) / (dx1 - dx0);
      // south wall runs from corner 2 (x1, y1) to corner 3 (x0, y1)
      const o8 = k * 8;
      const sx = cor[o8 + 6] - cor[o8 + 4];
      const sy = cor[o8 + 7] - cor[o8 + 5];
      const q = [0, 0, 0, 0, 0, 0, 0, 0];
      const ba = 1 - fa;
      const bb = 1 - fb;
      q[0] = cor[o8 + 4] + sx * bb;
      q[1] = cor[o8 + 5] + sy * bb;
      q[2] = cor[o8 + 4] + sx * ba;
      q[3] = cor[o8 + 5] + sy * ba;
      q[4] = q[2] + ex * 2.3;
      q[5] = q[3] + ey * 2.3;
      q[6] = q[0] + ex * 2.3;
      q[7] = q[1] + ey * 2.3;
      poly(out, argb(44, 78, 74, 1), q, 4);
    }
  }
  return n;
}
