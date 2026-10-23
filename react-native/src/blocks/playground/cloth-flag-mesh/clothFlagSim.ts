/**
 * The banner: a verlet cloth of MESH_COLS × MESH_ROWS points in 3D (x right, y down, z towards
 * the viewer, all in pt), its first column pinned to the pole. Each tick adds gravity and wind,
 * moves every free point by its own velocity (verlet: position minus last position), then pulls
 * the links back to length. Wind acts twice: as drag along the cloth towards the wind's velocity,
 * and as pressure on each triangle across its face, which is what makes a flag fly and flap.
 *
 * It is built to stay cheap on the UI thread: a modest grid, the straight links solved a few times
 * and the diagonal and bending links once per step, and a tether from each point to the pole that
 * stops the cloth stretching however few passes it gets. Plain arithmetic on typed arrays with a
 * fixed step, so it runs the same, step for step, on any platform.
 */

export const MESH_COLS = 20;
export const MESH_ROWS = 14;
// Rest distance between neighbouring points, in pt: the banner is 285 × 195 pt.
export const SPACING = 15;
export const FLAG_W = (MESH_COLS - 1) * SPACING;
export const FLAG_H = (MESH_ROWS - 1) * SPACING;

// pt/s². A little lighter than the real thing, so the fabric floats.
const GRAVITY = 1400;
// Wind at full strength, and what a gust of 1 adds to it, calm or not (pt/s along +x).
const WIND_SPEED = 1300;
const GUST_SPEED = 950;
// Swirl across the cloth (pt/s along z): waves that travel along the flag at WAVE_SPEED.
const SWIRL = 600;
const WAVE_LENGTH = 180;
const WAVE_SPEED = 240;
// The readout calls full wind 22 knots.
const KNOTS_PER_SPEED = 22 / WIND_SPEED;
// Drag along the cloth towards the air's velocity (1/s) — also what slows a limp flag in still air.
const SKIN = 2.2;
// Pressure across a triangle's face, per pt² and pt/s of wind into it.
const PRESSURE = 0.03;
// Velocity kept per step.
const DAMPING = 0.994;
// Passes per step: the straight links, then the diagonals and the bending links.
const STRETCH_PASSES = 3;
const SOFT_PASSES = 1;
// How strongly each kind of link holds: straight neighbours, diagonals, and two apart (bending).
const STRETCH = 1;
const SHEAR = 0.5;
const BEND = 0.3;
// No point may be further from its neighbour toward the pole than the spacing times this.
const TETHER = 1.03;
// A grabbed point follows the finger by this share of the gap each step, and loses depth.
const GRAB_FOLLOW = 0.55;
const GRAB_FLATTEN = 0.85;

export type Cloth = {
  x: Float64Array;
  y: Float64Array;
  z: Float64Array;
  px: Float64Array;
  py: Float64Array;
  pz: Float64Array;
  /** Where every point was a step ago, so a frame between steps can be drawn in between. */
  ox: Float64Array;
  oy: Float64Array;
  oz: Float64Array;
  ax: Float64Array;
  ay: Float64Array;
  az: Float64Array;
  /** The air's velocity at each point this step. */
  wx: Float64Array;
  wy: Float64Array;
  wz: Float64Array;
  /** 0 for a pinned point, 1 otherwise. */
  free: Uint8Array;
  /** Links as pairs of point indices, their rest lengths and stiffness: the straight ones first. */
  linkA: Int32Array;
  linkB: Int32Array;
  rest: Float64Array;
  stiff: Float64Array;
  straight: number;
  links: number;
  /** Where the pole holds the first column (pt). */
  originX: number;
  originY: number;
  /** Seconds of wind so far, for the travelling swirl. */
  time: number;
};

export type Air = {
  /** 0 calm … 1 full wind. */
  level: number;
  /** A gust on top of the wind (it blows even in calm air), 0…~1.5. */
  gust: number;
};

function addLink(cloth: Cloth, a: number, b: number, stiff: number) {
  'worklet';
  const k = cloth.links;
  cloth.linkA[k] = a;
  cloth.linkB[k] = b;
  cloth.rest[k] = Math.hypot(cloth.x[b] - cloth.x[a], cloth.y[b] - cloth.y[a]);
  cloth.stiff[k] = stiff;
  cloth.links = k + 1;
}

/** Pulls point `i` back to within `max` of point `j`, if it is further. */
function tetherTo(x: Float64Array, y: Float64Array, z: Float64Array, i: number, j: number, max: number) {
  'worklet';
  const dx = x[i] - x[j];
  const dy = y[i] - y[j];
  const dz = z[i] - z[j];
  const d2 = dx * dx + dy * dy + dz * dz;
  if (d2 <= max * max) return;
  const k = max / Math.sqrt(d2);
  x[i] = x[j] + dx * k;
  y[i] = y[j] + dy * k;
  z[i] = z[j] + dz * k;
}

/** A flat banner held out from the pole at (originX, originY), its hoist edge pinned. */
export function createCloth(originX: number, originY: number): Cloth {
  'worklet';
  const n = MESH_COLS * MESH_ROWS;
  const maxLinks = n * 6;
  const cloth: Cloth = {
    x: new Float64Array(n),
    y: new Float64Array(n),
    z: new Float64Array(n),
    px: new Float64Array(n),
    py: new Float64Array(n),
    pz: new Float64Array(n),
    ox: new Float64Array(n),
    oy: new Float64Array(n),
    oz: new Float64Array(n),
    ax: new Float64Array(n),
    ay: new Float64Array(n),
    az: new Float64Array(n),
    wx: new Float64Array(n),
    wy: new Float64Array(n),
    wz: new Float64Array(n),
    free: new Uint8Array(n),
    linkA: new Int32Array(maxLinks),
    linkB: new Int32Array(maxLinks),
    rest: new Float64Array(maxLinks),
    stiff: new Float64Array(maxLinks),
    straight: 0,
    links: 0,
    originX,
    originY,
    time: 0,
  };
  for (let r = 0; r < MESH_ROWS; r += 1) {
    for (let c = 0; c < MESH_COLS; c += 1) {
      const i = r * MESH_COLS + c;
      cloth.x[i] = originX + c * SPACING;
      cloth.y[i] = originY + r * SPACING;
      // A faint, fixed ripple across the face: a perfectly flat sheet could never buckle into folds.
      cloth.z[i] = c === 0 ? 0 : 1.5 * Math.sin(c * 1.7 + r * 0.9) * Math.sin(r * 2.3 + 0.4);
      cloth.pz[i] = cloth.z[i];
      cloth.px[i] = cloth.x[i];
      cloth.py[i] = cloth.y[i];
      cloth.free[i] = c === 0 ? 0 : 1;
    }
  }
  cloth.ox.set(cloth.x);
  cloth.oy.set(cloth.y);
  cloth.oz.set(cloth.z);
  // Straight neighbours first, row by row from the pole outwards, then the softer links.
  for (let r = 0; r < MESH_ROWS; r += 1) {
    for (let c = 0; c < MESH_COLS; c += 1) {
      const i = r * MESH_COLS + c;
      if (c + 1 < MESH_COLS) addLink(cloth, i, i + 1, STRETCH);
      if (r + 1 < MESH_ROWS) addLink(cloth, i, i + MESH_COLS, STRETCH);
    }
  }
  cloth.straight = cloth.links;
  for (let r = 0; r < MESH_ROWS; r += 1) {
    for (let c = 0; c < MESH_COLS; c += 1) {
      const i = r * MESH_COLS + c;
      if (c + 1 < MESH_COLS && r + 1 < MESH_ROWS) {
        addLink(cloth, i, i + MESH_COLS + 1, SHEAR);
        addLink(cloth, i + 1, i + MESH_COLS, SHEAR);
      }
      if (c + 2 < MESH_COLS) addLink(cloth, i, i + 2, BEND);
      if (r + 2 < MESH_ROWS) addLink(cloth, i, i + 2 * MESH_COLS, BEND);
    }
  }
  return cloth;
}

/** Copies every point (position, velocity and last drawn position) of `from` into `to`. */
export function copyCloth(from: Cloth, to: Cloth) {
  'worklet';
  to.x.set(from.x);
  to.y.set(from.y);
  to.z.set(from.z);
  to.px.set(from.px);
  to.py.set(from.py);
  to.pz.set(from.pz);
  to.ox.set(from.ox);
  to.oy.set(from.oy);
  to.oz.set(from.oz);
  to.time = from.time;
}

/**
 * Eases every point a share `k` (0…1) of the way to `to`'s, velocity included. Used to bring a
 * cloth that has nearly come to rest exactly onto a known resting state.
 */
export function blendCloth(cloth: Cloth, to: Cloth, k: number) {
  'worklet';
  const n = MESH_COLS * MESH_ROWS;
  for (let i = 0; i < n; i += 1) {
    cloth.x[i] += (to.x[i] - cloth.x[i]) * k;
    cloth.y[i] += (to.y[i] - cloth.y[i]) * k;
    cloth.z[i] += (to.z[i] - cloth.z[i]) * k;
    cloth.px[i] += (to.px[i] - cloth.px[i]) * k;
    cloth.py[i] += (to.py[i] - cloth.py[i]) * k;
    cloth.pz[i] += (to.pz[i] - cloth.pz[i]) * k;
  }
}

/** Stops every point where it is. */
export function stillCloth(cloth: Cloth) {
  'worklet';
  cloth.px.set(cloth.x);
  cloth.py.set(cloth.y);
  cloth.pz.set(cloth.z);
  cloth.ox.set(cloth.x);
  cloth.oy.set(cloth.y);
  cloth.oz.set(cloth.z);
}

/**
 * One step of `dt` seconds. A point being dragged (`grab` ≥ 0) follows (grabX, grabY) and is held
 * there; on release it keeps the velocity the finger gave it.
 */
export function stepCloth(cloth: Cloth, air: Air, dt: number, grab: number, grabX: number, grabY: number) {
  'worklet';
  const n = MESH_COLS * MESH_ROWS;
  const x = cloth.x;
  const y = cloth.y;
  const z = cloth.z;
  const px = cloth.px;
  const py = cloth.py;
  const pz = cloth.pz;
  const ax = cloth.ax;
  const ay = cloth.ay;
  const az = cloth.az;
  const wx = cloth.wx;
  const wy = cloth.wy;
  const wz = cloth.wz;
  cloth.ox.set(x);
  cloth.oy.set(y);
  cloth.oz.set(z);
  cloth.time += dt;
  const t = cloth.time;

  // The air: a steady push along the flag and two travelling waves across it, at odd angles so the
  // ripples never line up into stripes; then gravity, and drag towards the air's velocity.
  const along = air.level * WIND_SPEED + GUST_SPEED * air.gust;
  const swirlAmp = SWIRL * (air.level + 0.6 * air.gust);
  const k1 = (2 * Math.PI) / WAVE_LENGTH;
  const lift = along * 0.03;
  for (let i = 0; i < n; i += 1) {
    const u = x[i] - cloth.originX;
    const v = y[i] - cloth.originY;
    let swirl = 0;
    if (swirlAmp > 0) {
      swirl =
        Math.sin(k1 * u - k1 * WAVE_SPEED * t + 0.6 * Math.sin(0.013 * v + 0.9 * t)) +
        0.45 * Math.sin(1.7 * k1 * u + 0.021 * v - 1.6 * k1 * WAVE_SPEED * t + 1.3);
    }
    wx[i] = along;
    wy[i] = lift === 0 ? 0 : lift * Math.sin(0.8 * t + 0.01 * u);
    wz[i] = swirlAmp * swirl;
    ax[i] = SKIN * (wx[i] - (x[i] - px[i]) / dt);
    ay[i] = GRAVITY + SKIN * (wy[i] - (y[i] - py[i]) / dt);
    az[i] = SKIN * (wz[i] - (z[i] - pz[i]) / dt);
  }

  // Pressure on each triangle, across its face, shared by its three corners.
  if (along > 1) {
    for (let r = 0; r < MESH_ROWS - 1; r += 1) {
      for (let c = 0; c < MESH_COLS - 1; c += 1) {
        const i = r * MESH_COLS + c;
        for (let half = 0; half < 2; half += 1) {
          const a = half === 0 ? i : i + 1;
          const b = half === 0 ? i + 1 : i + MESH_COLS + 1;
          const d = i + MESH_COLS;
          const e1x = x[b] - x[a];
          const e1y = y[b] - y[a];
          const e1z = z[b] - z[a];
          const e2x = x[d] - x[a];
          const e2y = y[d] - y[a];
          const e2z = z[d] - z[a];
          // Normal scaled by twice the area.
          const cx = e1y * e2z - e1z * e2y;
          const cy = e1z * e2x - e1x * e2z;
          const cz = e1x * e2y - e1y * e2x;
          const len = Math.sqrt(cx * cx + cy * cy + cz * cz);
          if (len < 1e-6) continue;
          // The air against the triangle: its corners' wind less their velocity, averaged.
          const rvx = (wx[a] + wx[b] + wx[d] - (x[a] - px[a] + x[b] - px[b] + x[d] - px[d]) / dt) / 3;
          const rvy = (wy[a] + wy[b] + wy[d] - (y[a] - py[a] + y[b] - py[b] + y[d] - py[d]) / dt) / 3;
          const rvz = (wz[a] + wz[b] + wz[d] - (z[a] - pz[a] + z[b] - pz[b] + z[d] - pz[d]) / dt) / 3;
          // (n̂ · w) n̂ · area / 3 per corner = (n · w) n / (6 |n|).
          const push = (PRESSURE * (cx * rvx + cy * rvy + cz * rvz)) / (6 * len);
          const fx = push * cx;
          const fy = push * cy;
          const fz = push * cz;
          ax[a] += fx;
          ay[a] += fy;
          az[a] += fz;
          ax[b] += fx;
          ay[b] += fy;
          az[b] += fz;
          ax[d] += fx;
          ay[d] += fy;
          az[d] += fz;
        }
      }
    }
  }

  // Verlet: carry on at the last step's velocity, plus this step's push.
  const dt2 = dt * dt;
  for (let i = 0; i < n; i += 1) {
    if (cloth.free[i] === 0) continue;
    const nx = x[i] + (x[i] - px[i]) * DAMPING + ax[i] * dt2;
    const ny = y[i] + (y[i] - py[i]) * DAMPING + ay[i] * dt2;
    const nz = z[i] + (z[i] - pz[i]) * DAMPING + az[i] * dt2;
    px[i] = x[i];
    py[i] = y[i];
    pz[i] = z[i];
    x[i] = nx;
    y[i] = ny;
    z[i] = nz;
  }

  // The grabbed point eases to the finger; its last position stays, so it carries the finger's speed.
  const held = grab >= 0 && grab < n && cloth.free[grab] === 1 ? grab : -1;
  if (held >= 0) {
    px[held] = x[held];
    py[held] = y[held];
    pz[held] = z[held];
    x[held] += (grabX - x[held]) * GRAB_FOLLOW;
    y[held] += (grabY - y[held]) * GRAB_FOLLOW;
    z[held] *= GRAB_FLATTEN;
  }

  // Pull the links back towards their length; a pinned or held end does not move.
  const linkA = cloth.linkA;
  const linkB = cloth.linkB;
  const rest = cloth.rest;
  const stiff = cloth.stiff;
  const free = cloth.free;
  const total = STRETCH_PASSES + SOFT_PASSES;
  for (let pass = 0; pass < total; pass += 1) {
    // The straight links every pass; the soft ones on the last pass only.
    const end = pass < total - SOFT_PASSES ? cloth.straight : cloth.links;
    for (let k = 0; k < end; k += 1) {
      const a = linkA[k];
      const b = linkB[k];
      const wa = a === held ? 0 : free[a];
      const wb = b === held ? 0 : free[b];
      const sum = wa + wb;
      if (sum === 0) continue;
      const dx = x[b] - x[a];
      const dy = y[b] - y[a];
      const dz = z[b] - z[a];
      const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (d < 1e-6) continue;
      const diff = ((d - rest[k]) / d) * stiff[k];
      const sa = (diff * wa) / sum;
      const sb = (diff * wb) / sum;
      x[a] += dx * sa;
      y[a] += dy * sa;
      z[a] += dz * sa;
      x[b] -= dx * sb;
      y[b] -= dy * sb;
      z[b] -= dz * sb;
    }
  }

  // A held point drags the cloth with it, as a pinch does: no point may be further from it than
  // they lie apart on the flat banner, so the whole cloth drapes from the finger in broad folds
  // instead of one point being drawn out into a spike.
  if (held >= 0) {
    const hc = held % MESH_COLS;
    const hr = Math.floor(held / MESH_COLS);
    for (let i = 0; i < n; i += 1) {
      const c = i % MESH_COLS;
      if (c === 0 || i === held) continue;
      const reach = SPACING * TETHER * Math.hypot(c - hc, Math.floor(i / MESH_COLS) - hr);
      tetherTo(x, y, z, i, held, reach);
    }
  }

  // Rows can not stretch: walking out from the pole, each point is kept within TETHER of the
  // one before it, and down each column within TETHER of the one above. Cheap, and it holds the
  // weight that a few passes of the links alone would not. The held point is kept too, so a
  // finger pulling past the cloth's reach draws it taut rather than tearing a point out of it.
  const max = SPACING * TETHER;
  for (let i = 0; i < n; i += 1) {
    const c = i % MESH_COLS;
    if (c === 0) continue;
    tetherTo(x, y, z, i, i - 1, max);
    if (i >= MESH_COLS) tetherTo(x, y, z, i, i - MESH_COLS, max);
  }
}

/**
 * Unit normals at every point of a mesh given as flat x, y, z arrays, from central differences
 * across the grid, turned to face the viewer (+z): the back of the cloth is lit like the front.
 */
export function computeNormals(
  x: Float64Array,
  y: Float64Array,
  z: Float64Array,
  nx: Float64Array,
  ny: Float64Array,
  nz: Float64Array,
) {
  'worklet';
  for (let r = 0; r < MESH_ROWS; r += 1) {
    for (let c = 0; c < MESH_COLS; c += 1) {
      const i = r * MESH_COLS + c;
      const l = c > 0 ? i - 1 : i;
      const rt = c < MESH_COLS - 1 ? i + 1 : i;
      const u = r > 0 ? i - MESH_COLS : i;
      const d = r < MESH_ROWS - 1 ? i + MESH_COLS : i;
      const tx = x[rt] - x[l];
      const ty = y[rt] - y[l];
      const tz = z[rt] - z[l];
      const bx = x[d] - x[u];
      const by = y[d] - y[u];
      const bz = z[d] - z[u];
      let cx = ty * bz - tz * by;
      let cy = tz * bx - tx * bz;
      let cz = tx * by - ty * bx;
      const len = Math.sqrt(cx * cx + cy * cy + cz * cz);
      if (len < 1e-9) {
        nx[i] = 0;
        ny[i] = 0;
        nz[i] = 1;
        continue;
      }
      const s = cz < 0 ? -1 / len : 1 / len;
      cx *= s;
      cy *= s;
      cz *= s;
      nx[i] = cx;
      ny[i] = cy;
      nz[i] = cz;
    }
  }
}

/** Wind speed for the readout, in knots: its strength along the flag, with a little swirl. */
export function windKnots(air: Air, time: number) {
  'worklet';
  const ptPerSecond = air.level * WIND_SPEED + GUST_SPEED * air.gust;
  const knots = ptPerSecond * KNOTS_PER_SPEED;
  return knots * (1 + 0.05 * Math.sin(time * 1.7) + 0.03 * Math.sin(time * 4.1));
}

/**
 * The banner's cloth: a plain weave of fine threads under an invented device — a gold sun of
 * sixteen rays in a ring over three waves — on an oxblood field with a gold border and an ivory
 * hoist. It is drawn in the flat banner's own coordinates (pt), which the mesh maps onto the
 * folds; every thread's crossing is shaded so the weave catches the light.
 */
export const WEAVE_SKSL = `
uniform float2 size;

const half3 FIELD = half3(0.47, 0.09, 0.13);
const half3 FIELD_DEEP = half3(0.36, 0.06, 0.10);
const half3 GOLD = half3(0.93, 0.74, 0.38);
const half3 IVORY = half3(0.94, 0.90, 0.81);

float fill(float d) {
  return 1.0 - smoothstep(-0.6, 0.6, d);
}

float hash(float n) {
  return fract(sin(n * 91.345) * 47453.5453);
}

half4 main(float2 p) {
  float hoist = 50.0;
  half3 col = mix(FIELD, FIELD_DEEP, half(smoothstep(0.0, size.y, p.y) * 0.6));

  // Gold border inside the field.
  float inset = 9.0;
  float2 q = p - float2(hoist, 0.0);
  float2 box = float2(size.x - hoist, size.y);
  float edge = min(min(q.y, box.y - q.y), box.x - q.x);
  float border = fill(abs(edge - inset) - 1.6);
  col = mix(col, GOLD, half(border));

  // The device: a sun in a ring over three waves.
  float2 c = float2(hoist + (size.x - hoist) * 0.5, size.y * 0.42);
  float2 d = p - c;
  float r = length(d);
  float a = atan(d.y, d.x);
  float disc = fill(r - 19.0);
  float ring = fill(abs(r - 27.0) - 1.4);
  float sector = 6.2831853 / 16.0;
  float k = floor(a / sector + 0.5);
  float local = a - k * sector;
  float longRay = mod(k, 2.0) == 0.0 ? 1.0 : 0.0;
  float tip = 34.0 + mix(9.0, 17.0, longRay);
  float t = clamp((r - 33.0) / (tip - 33.0), 0.0, 1.0);
  float halfWidth = mix(3.2, 0.3, t);
  float rays = fill(abs(local) * r - halfWidth) * fill(33.0 - r) * fill(r - tip);
  float waves = 0.0;
  for (int i = 0; i < 3; i++) {
    float y0 = c.y + 58.0 + float(i) * 11.0;
    float wy = y0 + 3.2 * sin((p.x - c.x) * 0.16 + float(i) * 1.1);
    float span = fill(abs(p.x - c.x) - (60.0 - float(i) * 12.0));
    waves = max(waves, fill(abs(p.y - wy) - 1.7) * span);
  }
  float device = max(max(disc, ring), max(rays, waves));
  col = mix(col, GOLD, half(device));

  // The hoist: ivory with two oxblood stripes, and a gold seam where it meets the field.
  float sleeve = fill(p.x - hoist);
  half3 hoistCol = IVORY;
  float stripes = max(fill(abs(p.x - 14.0) - 2.0), fill(abs(p.x - 36.0) - 2.0));
  hoistCol = mix(hoistCol, FIELD, half(stripes));
  col = mix(col, hoistCol, half(sleeve));
  col = mix(col, GOLD, half(fill(abs(p.x - hoist) - 1.2)));

  // Plain weave: threads 2 pt apart, warp and weft over and under in turn.
  float pitch = 2.0;
  float2 g = p / pitch;
  float2 cell = floor(g);
  float2 f = fract(g) - 0.5;
  bool warpUp = mod(cell.x + cell.y, 2.0) < 1.0;
  float across = warpUp ? f.x : f.y;
  float along = warpUp ? f.y : f.x;
  float rounded = 1.0 - 4.0 * across * across;
  float bend = 1.0 - 1.6 * along * along;
  float slub = warpUp ? hash(cell.x) : hash(cell.y + 31.0);
  float shade = 0.72 + 0.3 * rounded * bend + 0.06 * (slub - 0.5);
  // The warp is a touch darker than the weft, like shot silk.
  shade *= warpUp ? 0.95 : 1.03;
  col *= half(shade);
  return half4(col, 1.0);
}
`;
