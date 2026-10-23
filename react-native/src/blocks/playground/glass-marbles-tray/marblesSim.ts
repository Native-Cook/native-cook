/**
 * Glass marbles on a wooden tray. Everything lives in the tray's own frame (u right, v down, w up
 * out of the floor, pt, origin at the tray's centre), so the physics never sees the perspective.
 *
 * Each tick: the tray's tilt and its small slide follow their targets on springs; gravity along
 * the tilted floor, the slide's inertia and the carved cups (shallow bowls that pull a marble to
 * their centre) push the marbles; rolling friction slows them; then a few passes of circle-circle
 * collisions (with restitution and mass) and walls. Marbles can hop off the floor when the tray
 * is shaken, and each one carries a 3D orientation that turns as it rolls, so its swirl rolls too.
 *
 * Plain arithmetic on typed arrays with one fixed tick and seeded randomness: it ports step for step.
 */

export const TICK = 1 / 60;
export const MAX_TICKS = 3;
// Collisions run on SUBSTEPS slices of each tick.
const SUBSTEPS = 2;

// autoPlay period (s).
export const LOOP = 12;

// Gravity along the floor (pt/s²), already scaled for a rolling ball (5/7 of free fall).
const GRAVITY = 4300;
// A hop: gravity on a marble in the air, and how much of its fall speed a landing keeps.
const AIR_GRAVITY = 5200;
const LANDING = 0.32;
// Rolling: speed kept per second, plus a small constant deceleration (pt/s²).
const ROLL_KEEP = 0.7;
const ROLL_FRICTION = 16;
// Cups: radius (pt), depth (pt) and the extra damping (1/s) of a marble rocking in one.
export const CUP_R = 19;
const CUP_DEPTH = 1.4;
const CUP_DAMP = 2.4;
// Restitution of glass on glass, and of glass on the wooden wall.
const BOUNCE = 0.82;
const WALL_BOUNCE = 0.45;
// A collision faster than this (pt/s) clicks: a glint, and a haptic when the user is playing.
export const CLICK_SPEED = 170;
// Tray springs: the tilt (1/s², 1/s) and the slide.
const TILT_K = 150;
const TILT_D = 17;
const SLIDE_K = 260;
const SLIDE_D = 24;
// How much of the slide's acceleration the marbles feel (they stay put while the tray moves).
const INERTIA = 1;
// The guided return at the end of an autoPlay loop: spring (1/s²) and damping (1/s) to home.
const GUIDE_K = 16;
const GUIDE_D = 5;

export const FLASHES = 10;
const FLASH_LIFE = 0.22;

export type Marble = {
  r: number;
  /** Glass tint, swirl colours (rgb 0…255) and how many vanes the swirl has. */
  glass: number[];
  vane: number[];
  vane2: number[];
  vanes: number;
};

export type Tray = {
  /** Inner floor half-size (pt) and the radius of its corners. */
  halfW: number;
  halfH: number;
  corner: number;
  /** Cup centres (pt). */
  cupX: Float64Array;
  cupY: Float64Array;
  /** The cup each marble starts in. */
  home: Int32Array;
};

export type World = {
  n: number;
  x: Float64Array;
  y: Float64Array;
  vx: Float64Array;
  vy: Float64Array;
  /** Height above the floor and its speed: non-zero only in a hop. */
  z: Float64Array;
  vz: Float64Array;
  r: Float64Array;
  mass: Float64Array;
  /** Orientation of each marble: a 3×3 rotation, row-major, 9 numbers each. */
  rot: Float64Array;
  /** The tray: tilt about its x and y axes (rad) and their speeds; its slide (pt) and speeds. */
  tiltX: number;
  tiltY: number;
  tiltVX: number;
  tiltVY: number;
  slideX: number;
  slideY: number;
  slideVX: number;
  slideVY: number;
  /** Glints where marbles clicked: position, age (s) and strength. */
  flashX: Float64Array;
  flashY: Float64Array;
  flashAge: Float64Array;
  flashPower: Float64Array;
  nextFlash: number;
  /** Clicks this tick (for the haptic), and the hardest one. */
  clicks: number;
  hardest: number;
  /** Seed of the generator used by shakes and flicks. */
  seed: number;
};

export type Input = {
  tiltX: number;
  tiltY: number;
  slideX: number;
  slideY: number;
  /** 0…1: how strongly each marble is led back to its home cup. */
  guide: number;
};

/** mulberry32: the same sequence on every platform. */
export function random(w: World) {
  'worklet';
  w.seed = (w.seed + 0x6d2b79f5) >>> 0;
  let t = w.seed;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

/** The cups: a honeycomb of rows of five and four, centred on the floor. */
export function makeTray(halfW: number, halfH: number, marbles: number): Tray {
  'worklet';
  const pitch = Math.max(8, Math.min(62, ((halfW - 28) * 2) / 4));
  const rowGap = pitch * 0.866;
  const rows = Math.max(3, Math.min(9, Math.floor(((halfH - 28) * 2) / rowGap) + 1));
  const xs: number[] = [];
  const ys: number[] = [];
  for (let row = 0; row < rows; row += 1) {
    const count = row % 2 === 0 ? 5 : 4;
    const y = (row - (rows - 1) / 2) * rowGap;
    for (let c = 0; c < count; c += 1) {
      xs.push((c - (count - 1) / 2) * pitch);
      ys.push(y);
    }
  }
  // Homes: spread over the board, every other cup or so, in a fixed order.
  const home = new Int32Array(marbles);
  const step = xs.length / marbles;
  for (let i = 0; i < marbles; i += 1) home[i] = Math.min(xs.length - 1, Math.floor(i * step + step * 0.35));
  return { halfW, halfH, corner: 20, cupX: new Float64Array(xs), cupY: new Float64Array(ys), home };
}

export function createWorld(tray: Tray, set: Marble[]): World {
  'worklet';
  const n = set.length;
  const w: World = {
    n,
    x: new Float64Array(n),
    y: new Float64Array(n),
    vx: new Float64Array(n),
    vy: new Float64Array(n),
    z: new Float64Array(n),
    vz: new Float64Array(n),
    r: new Float64Array(n),
    mass: new Float64Array(n),
    rot: new Float64Array(n * 9),
    tiltX: 0,
    tiltY: 0,
    tiltVX: 0,
    tiltVY: 0,
    slideX: 0,
    slideY: 0,
    slideVX: 0,
    slideVY: 0,
    flashX: new Float64Array(FLASHES),
    flashY: new Float64Array(FLASHES),
    flashAge: new Float64Array(FLASHES).fill(FLASH_LIFE),
    flashPower: new Float64Array(FLASHES),
    nextFlash: 0,
    clicks: 0,
    hardest: 0,
    seed: 7041,
  };
  for (let i = 0; i < n; i += 1) {
    const cup = tray.home[i];
    w.x[i] = tray.cupX[cup];
    w.y[i] = tray.cupY[cup];
    w.r[i] = set[i].r;
    w.mass[i] = set[i].r * set[i].r * set[i].r;
    // A different resting turn for each, so the swirls do not line up.
    const a = i * 2.39996;
    const b = i * 1.1;
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    const cb = Math.cos(b);
    const sb = Math.sin(b);
    // Rz(a)·Rx(b)
    const m = w.rot;
    const o = i * 9;
    m[o] = ca;
    m[o + 1] = -sa * cb;
    m[o + 2] = sa * sb;
    m[o + 3] = sa;
    m[o + 4] = ca * cb;
    m[o + 5] = -ca * sb;
    m[o + 6] = 0;
    m[o + 7] = sb;
    m[o + 8] = cb;
  }
  return w;
}

export function copyWorld(from: World, to: World) {
  'worklet';
  to.x.set(from.x);
  to.y.set(from.y);
  to.vx.set(from.vx);
  to.vy.set(from.vy);
  to.z.set(from.z);
  to.vz.set(from.vz);
  to.rot.set(from.rot);
  to.tiltX = from.tiltX;
  to.tiltY = from.tiltY;
  to.tiltVX = from.tiltVX;
  to.tiltVY = from.tiltVY;
  to.slideX = from.slideX;
  to.slideY = from.slideY;
  to.slideVX = from.slideVX;
  to.slideVY = from.slideVY;
  to.seed = from.seed;
}

/** Re-squares a marble's rotation (Gram–Schmidt on its rows) so rounding never skews it. */
function orthonormalize(m: Float64Array, o: number) {
  'worklet';
  let l = Math.hypot(m[o], m[o + 1], m[o + 2]);
  const l2 = Math.hypot(m[o + 3], m[o + 4], m[o + 5]);
  // A collapsed or non-finite matrix (never expected): start the marble's turn over.
  if (!(l > 1e-9) || !(l2 > 1e-9) || !Number.isFinite(l + l2)) {
    m[o] = 1;
    m[o + 1] = 0;
    m[o + 2] = 0;
    m[o + 3] = 0;
    m[o + 4] = 1;
    m[o + 5] = 0;
    m[o + 6] = 0;
    m[o + 7] = 0;
    m[o + 8] = 1;
    return;
  }
  m[o] /= l;
  m[o + 1] /= l;
  m[o + 2] /= l;
  const d = m[o] * m[o + 3] + m[o + 1] * m[o + 4] + m[o + 2] * m[o + 5];
  m[o + 3] -= d * m[o];
  m[o + 4] -= d * m[o + 1];
  m[o + 5] -= d * m[o + 2];
  l = Math.hypot(m[o + 3], m[o + 4], m[o + 5]);
  m[o + 3] /= l;
  m[o + 4] /= l;
  m[o + 5] /= l;
  m[o + 6] = m[o + 1] * m[o + 5] - m[o + 2] * m[o + 4];
  m[o + 7] = m[o + 2] * m[o + 3] - m[o] * m[o + 5];
  m[o + 8] = m[o] * m[o + 4] - m[o + 1] * m[o + 3];
}

/** Turns marble `i` by `angle` about the unit axis (ax, ay, az): R ← Rot·R. */
function turn(w: World, i: number, ax: number, ay: number, az: number, angle: number) {
  'worklet';
  if (angle === 0) return;
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  const t = 1 - c;
  const r00 = t * ax * ax + c;
  const r01 = t * ax * ay - s * az;
  const r02 = t * ax * az + s * ay;
  const r10 = t * ax * ay + s * az;
  const r11 = t * ay * ay + c;
  const r12 = t * ay * az - s * ax;
  const r20 = t * ax * az - s * ay;
  const r21 = t * ay * az + s * ax;
  const r22 = t * az * az + c;
  const m = w.rot;
  const o = i * 9;
  for (let col = 0; col < 3; col += 1) {
    const a = m[o + col];
    const b = m[o + 3 + col];
    const d = m[o + 6 + col];
    m[o + col] = r00 * a + r01 * b + r02 * d;
    m[o + 3 + col] = r10 * a + r11 * b + r12 * d;
    m[o + 6 + col] = r20 * a + r21 * b + r22 * d;
  }
}

/** Eases every marble's rotation a share `k` towards `from`'s, then squares it again. */
export function blendRotations(to: World, from: World, k: number) {
  'worklet';
  for (let i = 0; i < to.rot.length; i += 1) to.rot[i] += (from.rot[i] - to.rot[i]) * k;
  for (let i = 0; i < to.n; i += 1) orthonormalize(to.rot, i * 9);
}

/** Eases every marble a share `k` towards `from`'s position and speed. */
export function blendPositions(to: World, from: World, k: number) {
  'worklet';
  for (let i = 0; i < to.n; i += 1) {
    to.x[i] += (from.x[i] - to.x[i]) * k;
    to.y[i] += (from.y[i] - to.y[i]) * k;
    to.vx[i] += (from.vx[i] - to.vx[i]) * k;
    to.vy[i] += (from.vy[i] - to.vy[i]) * k;
    to.z[i] += (from.z[i] - to.z[i]) * k;
    to.vz[i] += (from.vz[i] - to.vz[i]) * k;
  }
}

function addFlash(w: World, x: number, y: number, power: number) {
  'worklet';
  const f = w.nextFlash;
  w.flashX[f] = x;
  w.flashY[f] = y;
  w.flashAge[f] = 0;
  w.flashPower[f] = power;
  w.nextFlash = (f + 1) % FLASHES;
}

/** A shake: every marble is knocked in a random direction and hops. */
export function scatter(w: World, strength: number) {
  'worklet';
  for (let i = 0; i < w.n; i += 1) {
    const a = random(w) * Math.PI * 2;
    const speed = strength * (220 + 260 * random(w));
    w.vx[i] += Math.cos(a) * speed;
    w.vy[i] += Math.sin(a) * speed;
    w.vz[i] = Math.max(w.vz[i], strength * (160 + 180 * random(w)));
  }
}

/** A flick of marble `i` along (dx, dy) at `speed` pt/s, with a little hop. */
export function flick(w: World, i: number, dx: number, dy: number, speed: number) {
  'worklet';
  const l = Math.hypot(dx, dy) || 1;
  w.vx[i] += (dx / l) * speed;
  w.vy[i] += (dy / l) * speed;
  w.vz[i] = Math.max(w.vz[i], 90);
}

/** Gravity along the tilted floor, per unit of GRAVITY: (sin ty, −cos ty · sin tx). */
export function floorPull(tiltX: number, tiltY: number) {
  'worklet';
  return { x: Math.sin(tiltY), y: -Math.cos(tiltY) * Math.sin(tiltX) };
}

function walls(w: World, tray: Tray, i: number) {
  'worklet';
  const r = w.r[i];
  const lx = tray.halfW - r;
  const ly = tray.halfH - r;
  // The rounded corners: a marble beyond a corner's centre is held on its arc.
  const cr = Math.max(0, tray.corner - r);
  const cx = lx - cr;
  const cy = ly - cr;
  const ax = Math.abs(w.x[i]);
  const ay = Math.abs(w.y[i]);
  if (ax > cx && ay > cy && cr > 0) {
    const sx = Math.sign(w.x[i]);
    const sy = Math.sign(w.y[i]);
    const dx = ax - cx;
    const dy = ay - cy;
    const d = Math.hypot(dx, dy);
    if (d > cr) {
      const nx = (dx / d) * sx;
      const ny = (dy / d) * sy;
      w.x[i] = sx * cx + nx * cr;
      w.y[i] = sy * cy + ny * cr;
      const vn = w.vx[i] * nx + w.vy[i] * ny;
      if (vn > 0) {
        w.vx[i] -= (1 + WALL_BOUNCE) * vn * nx;
        w.vy[i] -= (1 + WALL_BOUNCE) * vn * ny;
        if (vn > CLICK_SPEED) addFlash(w, w.x[i] + nx * r, w.y[i] + ny * r, Math.min(1, vn / 600));
      }
    }
    return;
  }
  if (w.x[i] > lx || w.x[i] < -lx) {
    const s = Math.sign(w.x[i]);
    w.x[i] = s * lx;
    if (w.vx[i] * s > 0) {
      if (Math.abs(w.vx[i]) > CLICK_SPEED) addFlash(w, s * (lx + r), w.y[i], Math.min(1, Math.abs(w.vx[i]) / 600));
      w.vx[i] = -w.vx[i] * WALL_BOUNCE;
    }
  }
  if (w.y[i] > ly || w.y[i] < -ly) {
    const s = Math.sign(w.y[i]);
    w.y[i] = s * ly;
    if (w.vy[i] * s > 0) {
      if (Math.abs(w.vy[i]) > CLICK_SPEED) addFlash(w, w.x[i], s * (ly + r), Math.min(1, Math.abs(w.vy[i]) / 600));
      w.vy[i] = -w.vy[i] * WALL_BOUNCE;
    }
  }
}

function collide(w: World) {
  'worklet';
  for (let i = 0; i < w.n; i += 1) {
    for (let j = i + 1; j < w.n; j += 1) {
      const dx = w.x[j] - w.x[i];
      const dy = w.y[j] - w.y[i];
      const min = w.r[i] + w.r[j];
      const d2 = dx * dx + dy * dy;
      if (d2 >= min * min || d2 === 0) continue;
      // Marbles at very different heights pass over each other.
      if (Math.abs(w.z[i] - w.z[j]) > min * 0.8) continue;
      const d = Math.sqrt(d2);
      const nx = dx / d;
      const ny = dy / d;
      const mi = w.mass[i];
      const mj = w.mass[j];
      const total = mi + mj;
      // Push apart in proportion to the other's mass.
      const overlap = min - d;
      w.x[i] -= nx * overlap * (mj / total);
      w.y[i] -= ny * overlap * (mj / total);
      w.x[j] += nx * overlap * (mi / total);
      w.y[j] += ny * overlap * (mi / total);
      const vn = (w.vx[j] - w.vx[i]) * nx + (w.vy[j] - w.vy[i]) * ny;
      if (vn >= 0) continue;
      const impulse = (-(1 + BOUNCE) * vn) / (1 / mi + 1 / mj);
      w.vx[i] -= (impulse / mi) * nx;
      w.vy[i] -= (impulse / mi) * ny;
      w.vx[j] += (impulse / mj) * nx;
      w.vy[j] += (impulse / mj) * ny;
      if (-vn > CLICK_SPEED) {
        w.clicks += 1;
        w.hardest = Math.max(w.hardest, -vn);
        addFlash(w, w.x[i] + nx * w.r[i], w.y[i] + ny * w.r[i], Math.min(1, -vn / 600));
      }
    }
  }
}

/** One tick of the tray and its marbles. */
export function stepWorld(w: World, tray: Tray, input: Input, dt: number) {
  'worklet';
  w.clicks = 0;
  w.hardest = 0;
  // The tray follows its targets on springs; the marbles feel how it accelerates.
  const tax = TILT_K * (input.tiltX - w.tiltX) - TILT_D * w.tiltVX;
  const tay = TILT_K * (input.tiltY - w.tiltY) - TILT_D * w.tiltVY;
  w.tiltVX += tax * dt;
  w.tiltVY += tay * dt;
  w.tiltX += w.tiltVX * dt;
  w.tiltY += w.tiltVY * dt;
  const sax = SLIDE_K * (input.slideX - w.slideX) - SLIDE_D * w.slideVX;
  const say = SLIDE_K * (input.slideY - w.slideY) - SLIDE_D * w.slideVY;
  w.slideVX += sax * dt;
  w.slideVY += say * dt;
  w.slideX += w.slideVX * dt;
  w.slideY += w.slideVY * dt;

  const pull = floorPull(w.tiltX, w.tiltY);
  const gx = GRAVITY * pull.x - INERTIA * sax;
  const gy = GRAVITY * pull.y - INERTIA * say;
  const h = dt / SUBSTEPS;
  const keep = Math.pow(ROLL_KEEP, h);
  for (let step = 0; step < SUBSTEPS; step += 1) {
    for (let i = 0; i < w.n; i += 1) {
      let ax = 0;
      let ay = 0;
      const grounded = w.z[i] <= 0;
      if (grounded) {
        ax += gx;
        ay += gy;
        // The nearest cup pulls the marble to its centre; the home cup when it is being led home.
        let best = -1;
        let bestD = CUP_R * CUP_R;
        for (let c = 0; c < tray.cupX.length; c += 1) {
          const dx = tray.cupX[c] - w.x[i];
          const dy = tray.cupY[c] - w.y[i];
          const d2 = dx * dx + dy * dy;
          if (d2 < bestD) {
            bestD = d2;
            best = c;
          }
        }
        if (best >= 0 && input.guide < 0.5) {
          const k = (GRAVITY * 2 * CUP_DEPTH) / (CUP_R * CUP_R);
          ax += k * (tray.cupX[best] - w.x[i]) - CUP_DAMP * w.vx[i];
          ay += k * (tray.cupY[best] - w.y[i]) - CUP_DAMP * w.vy[i];
        }
      } else {
        // In the air: it keeps its speed and falls back.
        w.vz[i] -= AIR_GRAVITY * h;
      }
      if (input.guide > 0) {
        const cup = tray.home[i];
        ax += input.guide * (GUIDE_K * (tray.cupX[cup] - w.x[i]) - GUIDE_D * w.vx[i]);
        ay += input.guide * (GUIDE_K * (tray.cupY[cup] - w.y[i]) - GUIDE_D * w.vy[i]);
      }
      w.vx[i] += ax * h;
      w.vy[i] += ay * h;
      if (grounded) {
        w.vx[i] *= keep;
        w.vy[i] *= keep;
        const speed = Math.hypot(w.vx[i], w.vy[i]);
        if (speed > 0) {
          const slow = Math.max(0, speed - ROLL_FRICTION * h) / speed;
          w.vx[i] *= slow;
          w.vy[i] *= slow;
        }
      }
      const ox = w.x[i];
      const oy = w.y[i];
      w.x[i] += w.vx[i] * h;
      w.y[i] += w.vy[i] * h;
      if (!grounded || w.vz[i] > 0) {
        w.z[i] += w.vz[i] * h;
        if (w.z[i] <= 0) {
          const hit = -w.vz[i];
          w.z[i] = 0;
          w.vz[i] = hit * LANDING > 60 ? hit * LANDING : 0;
          if (hit > CLICK_SPEED * 1.4) {
            w.clicks += 1;
            w.hardest = Math.max(w.hardest, hit * 0.5);
          }
        }
      }
      walls(w, tray, i);
      // Rolling turns the marble about the axis across its path, by distance over radius.
      const mx = w.x[i] - ox;
      const my = w.y[i] - oy;
      const dist = Math.hypot(mx, my);
      if (dist > 1e-6 && w.z[i] <= 0) turn(w, i, -my / dist, mx / dist, 0, dist / w.r[i]);
    }
    collide(w);
  }
  for (let i = 0; i < w.n; i += 1) orthonormalize(w.rot, i * 9);
  for (let f = 0; f < FLASHES; f += 1) if (w.flashAge[f] < FLASH_LIFE) w.flashAge[f] += dt;
}

export function flashLife() {
  'worklet';
  return FLASH_LIFE;
}

// autoPlay script (s): at rest, then the tilt traces a circle (one and a quarter turns), then a
// shake, then the marbles settle and are led home.
export const CIRCLE_FROM = 1.0;
export const CIRCLE_TO = 7.0;
const CIRCLE_TURNS = 1.25;
const CIRCLE_TILT = 0.2;
export const SHAKE_AT = 7.5;
const SHAKE_TIME = 0.7;
const SHAKE_SLIDE = 16;
const SHAKE_RATE = 5;
export const GUIDE_FROM = 8.7;
export const GUIDE_FULL = 10.4;

/** The tray's tilt and slide that the script asks for at loop time `t`. */
export function scriptPose(t: number) {
  'worklet';
  let tiltX = 0;
  let tiltY = 0;
  let slideX = 0;
  if (t > CIRCLE_FROM && t < CIRCLE_TO) {
    const u = (t - CIRCLE_FROM) / (CIRCLE_TO - CIRCLE_FROM);
    const amp = CIRCLE_TILT * Math.pow(Math.sin(Math.PI * u), 2);
    const a = Math.PI * 2 * CIRCLE_TURNS * u - Math.PI / 2;
    tiltY = amp * Math.cos(a);
    tiltX = amp * Math.sin(a);
  }
  if (t > SHAKE_AT && t < SHAKE_AT + SHAKE_TIME) {
    const u = (t - SHAKE_AT) / SHAKE_TIME;
    slideX = SHAKE_SLIDE * Math.sin(Math.PI * 2 * SHAKE_RATE * (t - SHAKE_AT)) * Math.sin(Math.PI * u);
  }
  let guide = 0;
  if (t > GUIDE_FROM) guide = Math.min(1, (t - GUIDE_FROM) / (GUIDE_FULL - GUIDE_FROM));
  return { tiltX, tiltY, slideX, guide };
}
