// Lava Lamp Loader — the loop's script and the touch simulation, all pure worklets.
//
// A pose is a flat array of six blobs × (x, y, r, stretch): five wax blobs (A, B, C, D, E) and the
// one a finger warms (F). x is pt from the lamp's axis in the liquid's own coordinates (the glass
// magnifies it on screen), y is pt from the top of the lamp, stretch > 1 makes a blob tall.
//
// LOOP = 9 s:
//   0.0 – 1.6  rest: A and B sit on the warm pool, C floats under the cap, D and E drift.
//   1.6 – 2.4  A swells and stretches, dips 3 pt first (anticipation), and necks off the pool.
//   2.4 – 4.6  A rises (ease-in-out) with a sideways sway, tall and thin while it climbs.
//   4.6 – 5.4  A meets C under the cap and they merge.
//   5.4 – 7.6  they split: C, cooled and stretched, sinks to A's old seat (a small squash on
//              landing, B nudged by it) while A shrinks into C's old place.
//   7.6 – 9.0  rest. A and C have traded places with each other's size, so the frame at 9 s is the
//              frame at 0 s.
// The tracks counter clicks up one track at a time from 1,204 to 1,238 during 1.6 – 7.4 and winds
// back during 7.6 – 8.3 while the wax rests.

export const LOOP = 9;
export const BLOBS = 6;
export const CAP_END = 0.13;
export const GLASS_END = 0.7;
export const TRACKS_FROM = 1204;
export const TRACKS_TO = 1238;
export const TRACKS_ALL = 2310;

export type Layout = { cx: number; top: number; H: number; k: number; shelf: number };

export function makeLayout(width: number, height: number, insetTop: number, insetBottom: number): Layout {
  const top = insetTop + 136;
  const shelf = height - insetBottom - 150;
  const H = Math.max(240, shelf - top);
  const k = Math.min(H / 480, (width / 2 - 20) / 116);
  return { cx: width / 2, top, H, k, shelf: top + H };
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return 1 - Math.pow(1 - c, 3);
}

function mix(a: number, b: number, e: number) {
  'worklet';
  return a + (b - a) * e;
}

/** Half-width of the glass at lamp-local y (the same profile the shader draws). */
export function glassHalfWidth(y: number, H: number, k: number) {
  'worklet';
  const capEnd = H * CAP_END;
  const glassEnd = H * GLASS_END;
  const s = clamp01((y - capEnd) / (glassEnd - capEnd));
  return (33 + 45 * Math.pow(s, 1.15)) * k;
}

/** Screen x (from the axis) to the liquid's x at height y: the inverse of the glass's lens. */
export function toLiquidX(x: number, y: number, H: number, k: number) {
  'worklet';
  const hw = glassHalfWidth(y, H, k);
  const u = Math.max(-1, Math.min(1, x / hw));
  return hw * u * (0.74 + 0.26 * u * u);
}

function put(out: number[], i: number, x: number, y: number, r: number, w: number) {
  'worklet';
  out[i * 4] = x;
  out[i * 4 + 1] = y;
  out[i * 4 + 2] = r;
  out[i * 4 + 3] = w;
}

/** The pose at loop time t, written into out (length BLOBS × 4). */
export function loopPose(t: number, L: Layout, out: number[]) {
  'worklet';
  // The wax is drawn 1.2 × the lamp's scale.
  const k = L.k * 1.2;
  const capEnd = L.H * CAP_END;
  const glassEnd = L.H * GLASS_END;
  const ph = (2 * Math.PI * t) / LOOP;
  // Seats: A low left, B low right, C high.
  const ax0 = -16 * k;
  const ay0 = glassEnd - 34 * k;
  const cx0 = 7 * k;
  const cy0 = capEnd + 58 * k;

  // A: swell, rise, merge, settle into C's seat.
  let ax = ax0;
  let ay = ay0;
  let ar = 25 * k;
  let aw = 0.85;
  const sw = seg(t, 1.6, 2.4);
  const dip = Math.sin(Math.PI * seg(t, 1.6, 1.85)) * 3 * k;
  ar = mix(25 * k, 27 * k, easeOut(sw));
  aw = mix(0.85, 1.15, easeInOut(sw));
  ay = ay0 + dip - 12 * k * easeInOut(seg(t, 1.8, 2.4));
  const rise = seg(t, 2.4, 4.6);
  if (rise > 0) {
    const e = easeInOut(rise);
    ay = mix(ay0 - 12 * k, capEnd + 84 * k, e);
    ax = mix(ax0, -2 * k, e) + 7 * k * Math.sin(Math.PI * e);
    aw = 1.12 + 0.12 * Math.sin(Math.PI * e) - 0.07 * e;
  }
  const merge = easeInOut(seg(t, 4.6, 5.4));
  if (t >= 4.6) {
    ax = mix(-2 * k, 3 * k, merge);
    ay = mix(capEnd + 84 * k, capEnd + 74 * k, merge);
    aw = 1.05;
  }
  const split = easeInOut(seg(t, 5.4, 7.6));
  if (t >= 5.4) {
    ax = mix(3 * k, cx0, split);
    ay = mix(capEnd + 74 * k, cy0, split);
    ar = mix(27 * k, 23 * k, split);
    aw = mix(1.05, 1.0, split);
  }

  // C: waits under the cap, drifts down to meet A, then sinks to A's seat.
  let cx = cx0 + 1.5 * k * Math.sin(ph);
  let cy = cy0 + 2 * k * Math.sin(ph + 1);
  let cr = 23 * k;
  let cw = 1.0;
  if (t >= 4.6) {
    cx = mix(cx0, 5 * k, merge);
    cy = mix(cy0, capEnd + 66 * k, merge);
  }
  if (t >= 5.4) {
    const fall = seg(t, 5.4, 7.6);
    const e = easeInOut(fall);
    cx = mix(5 * k, ax0, e) - 6 * k * Math.sin(Math.PI * e);
    cy = mix(capEnd + 66 * k, ay0, e);
    cr = mix(23 * k, 25 * k, e);
    // Tall while it sinks, a small squash when it lands, then its resting shape.
    const land = seg(t, 7.0, 7.6);
    cw = mix(1.0, 1.25, Math.sin(Math.PI * Math.min(1, fall * 1.15))) * (1 - land) + land * (0.85 - 0.08 * Math.sin(Math.PI * land));
  }

  // B rests, and is nudged when C lands next to it.
  const nudge = Math.sin(Math.PI * seg(t, 7.1, 7.9));
  const bx = 21 * k + 2.5 * k * nudge;
  const by = glassEnd - 24 * k + 0.8 * k * Math.sin(2 * ph);
  // D and E: small drifting drops.
  const dx = -9 * k + 3 * k * Math.sin(ph + 0.6);
  const dy = L.H * 0.42 + 6 * k * Math.sin(ph);
  const ex = 15 * k + 2 * k * Math.sin(2 * ph + 2);
  const ey = capEnd + 128 * k + 5 * k * Math.sin(2 * ph + 0.4);

  put(out, 0, ax, ay, ar, aw);
  put(out, 1, bx, by, 21 * k, 0.85);
  put(out, 2, cx, cy, cr, cw);
  put(out, 3, dx, dy, 11 * k, 1.05);
  put(out, 4, ex, ey, 9 * k, 1.0);
  put(out, 5, 0, glassEnd, 0, 1);
}

/** The tracks counter at loop time t: whole tracks, each one clicking in over 0.12 s. */
export function tracksAt(t: number) {
  'worklet';
  const n = TRACKS_TO - TRACKS_FROM;
  if (t < 7.6) {
    const x = seg(t, 1.6, 7.4) * n;
    const whole = Math.floor(x);
    const f = clamp01((x - whole) / 0.7);
    return TRACKS_FROM + whole + easeOut(f);
  }
  return mix(TRACKS_TO, TRACKS_FROM, easeInOut(seg(t, 7.6, 8.3)));
}

/** A touch's state: each blob's position and velocity (x, y, vx, vy), its anchor, and the finger. */
export type Sim = {
  pos: number[];
  vel: number[];
  anchor: number[];
  /** Finger in liquid coordinates, its velocity, and whether it is down. */
  fx: number;
  fy: number;
  fvx: number;
  fvy: number;
  down: number;
  /** Set once the warmed blob reaches the cap (one haptic). */
  topped: number;
};

export function makeSim(pose: number[], fx: number, fy: number): Sim {
  'worklet';
  const pos = pose.slice();
  const vel: number[] = [];
  for (let i = 0; i < BLOBS * 2; i += 1) vel.push(0);
  // The finger's blob starts where the finger lands, with no wax yet.
  pos[20] = fx;
  pos[21] = fy;
  pos[22] = 0;
  pos[23] = 1;
  return { pos, vel, anchor: pose.slice(), fx, fy, fvx: 0, fvy: 0, down: 1, topped: 0 };
}

/** One step of the hand-driven wax: springs home, pushed away from the finger and along its drag. */
export function stepSim(s: Sim, dt: number, L: Layout) {
  'worklet';
  const k = L.k;
  const capEnd = L.H * CAP_END;
  const glassEnd = L.H * GLASS_END;
  const sigma = 60 * k;
  for (let i = 0; i < 5; i += 1) {
    const x = s.pos[i * 4];
    const y = s.pos[i * 4 + 1];
    let vx = s.vel[i * 2];
    let vy = s.vel[i * 2 + 1];
    let ax = 5 * (s.anchor[i * 4] - x) - 3.2 * vx;
    let ay = 5 * (s.anchor[i * 4 + 1] - y) - 3.2 * vy;
    if (s.down) {
      const dx = x - s.fx;
      const dy = y - s.fy;
      const d = Math.sqrt(dx * dx + dy * dy) + 1;
      const fall = Math.exp(-(d * d) / (2 * sigma * sigma));
      ax += (dx / d) * 1500 * k * fall + (s.fvx - vx) * 5 * fall;
      ay += (dy / d) * 1500 * k * fall + (s.fvy - vy) * 5 * fall;
    }
    vx += ax * dt;
    vy += ay * dt;
    let nx = x + vx * dt;
    let ny = y + vy * dt;
    const r = s.pos[i * 4 + 2];
    ny = Math.max(capEnd + 22 * k + r * 0.4, Math.min(glassEnd - 8 * k, ny));
    const lim = Math.max(0, glassHalfWidth(ny, L.H, k) * 0.74 - r * 0.45);
    nx = Math.max(-lim, Math.min(lim, nx));
    s.pos[i * 4] = nx;
    s.pos[i * 4 + 1] = ny;
    s.vel[i * 2] = vx;
    s.vel[i * 2 + 1] = vy;
    // A pushed blob stretches along its speed a little.
    s.pos[i * 4 + 3] = s.anchor[i * 4 + 3] * (1 + Math.min(0.25, Math.abs(vy) / (400 * k)));
  }
  // The warmed blob: grows under a held finger and follows it; let go, it rises to the cap.
  const R = 27 * k;
  let fx = s.pos[20];
  let fy = s.pos[21];
  let vx = s.vel[10];
  let vy = s.vel[11];
  if (s.down) {
    const nx = fx + (s.fx - fx) * Math.min(1, dt * 10);
    const ny = fy + (s.fy - fy) * Math.min(1, dt * 10);
    vx = (nx - fx) / Math.max(dt, 1e-3);
    vy = (ny - fy) / Math.max(dt, 1e-3);
    fx = nx;
    fy = ny;
    s.pos[22] += (R - s.pos[22]) * Math.min(1, dt * 2.6);
  } else {
    vy += -130 * k * dt;
    vy *= Math.exp(-1.6 * dt);
    vx *= Math.exp(-2.5 * dt);
    vx += -fx * 0.8 * dt;
    fx += vx * dt;
    fy += vy * dt;
    const ceil = capEnd + 18 * k + s.pos[22] * 0.7;
    if (fy < ceil) {
      fy = ceil;
      vy = 0;
      if (!s.topped) s.topped = 1;
    }
  }
  const lim = Math.max(0, glassHalfWidth(fy, L.H, k) * 0.74 - s.pos[22] * 0.4);
  s.pos[20] = Math.max(-lim, Math.min(lim, fx));
  s.pos[21] = Math.max(capEnd + 14 * k, Math.min(glassEnd + 10 * k, fy));
  s.vel[10] = vx;
  s.vel[11] = vy;
  s.pos[23] = 1 + Math.min(0.4, Math.max(0, -vy) / (260 * k));
}

/** The pose blended from `from` to `to` by e (the warmed blob melts away). */
export function blendPose(from: number[], to: number[], e: number, out: number[]) {
  'worklet';
  for (let i = 0; i < BLOBS * 4; i += 1) out[i] = from[i] + (to[i] - from[i]) * e;
}
