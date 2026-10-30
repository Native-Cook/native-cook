// Raffle Drum Spin — the timeline, the drum's camera and every per-frame value.
//
// One story time `t` (0 … LOOP) decides the choreography (see .work design.md): a ticket card is
// chosen and its baseballs fly into the chrome drum; Play spins it; the winner is drawn out to the
// front, its stitches pop and its two figure-8 leather covers peel off and morph into a golden
// ticket; Claim tears the ticket's stub off into the chosen card. Touches reuse the same timeline
// (see the driver), a crank drag adds its own spin. Every value is a function of time: one period
// loops without a jump.

import { ICON_BALLS, L } from './rds-theme';

export const LOOP = 10;

export const T = {
  select: [0.6, 0.92] as const, // the selection flows to the chosen card (a slab of liquid glass)
  fly: [0.75, 1.35] as const, // its balls fly into the drum (each `flyStagger` after the last)
  flyStagger: 0.1,
  landBounce: 0.3, // after landing, a damped bounce on the drum's floor
  press: [1.6, 1.75] as const, // Play is pressed
  spin: [1.7, 3.6] as const, // the drum winds up and decelerates
  eject: [3.45, 4.0] as const, // the winning ball is drawn out to the front
  handoff: 0.18, // part of the eject after which the winner is drawn on the top layer
  focus: [3.5, 3.9] as const, // rack focus: the page blurs behind the winner
  antic: [4.0, 4.75] as const, // the wind-up: the ball comes closer, spins up and settles, light leaks from its seam, it trembles
  stitches: [4.05, 4.55] as const, // its stitches pop one by one round the seam
  morph: [4.75, 5.7] as const, // the covers crack, peel apart and flatten into the golden ticket
  burst: 5.5, // foil confetti bursts as the ticket takes its shape
  number: [5.6, 6.2] as const, // the entry number rolls in
  sweep: [6.2, 6.9] as const, // then a light sweep across the foil
  claim: [6.35, 6.95] as const, // then Play fills with gold and becomes "Claim ticket"
  hold: 7.0, // a touch-driven draw waits here for Claim
  claimPress: [7.85, 8.0] as const,
  drain: 0.45, // the gold drains from where the finger came down
  tear: [8.0, 8.6] as const, // the stub tears off and flies into the chosen card (flipping over)
  burn: [8.15, 8.6] as const, // once the stub is off, the body burns away into gold dust
  dust: 0.42, // each mote's flight from the burning edge into the chosen card
  focusOut: [8.6, 9.0] as const, // the page comes back into focus once the body is gone
  back: [8.75, 9.25] as const, // then the balls still in the drum fly home
  backStagger: 0.1,
  entered: [8.6, 8.8] as const, // the stub lands: the card's check turns gold
  iconBack: [8.55, 8.85] as const, // the drawn ball's icon comes back on its card
  reselect: [9.4, 9.72] as const, // autoPlay: the selection returns to the first card
};

/** The golden ticket at rest: centre and size (pt), and the body's share of its width. */
export const TICKET = { x: 201, y: 432, w: 300, h: 111, body: 0.729 };

/** Idle spin (rad/s) and the extra angle of the draw, chosen so one loop is exactly 5 turns. */
export const IDLE_RATE = 0.35;
export const TURNS = 3; // whole turns of the cage per loop (idle included), so the loop closes
export const SPIN_EXTRA = Math.PI * 2 * TURNS - IDLE_RATE * LOOP;

/** Drum camera: yaw and pitch (rad), distance; the sphere has radius 1. */
// A camera a little above the drum (negative pitch) and turned so its crank end comes forward.
export const CAM = { yaw: 0.2, pitch: -0.27, dist: 6 };
export const BALL_R = 0.15; // world radius of a ball
const INNER = 1 - 0.03 - BALL_R; // where a ball rests against the cage

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

/** A CSS cubic-bezier(x1, y1, x2, y2) at progress x (Newton steps, then bisection if needed). */
export function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let t = x;
  for (let i = 0; i < 6; i++) {
    const e = ((ax * t + bx) * t + cx) * t - x;
    const d = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(e) < 1e-5) break;
    if (Math.abs(d) < 1e-6) {
      let lo = 0;
      let hi = 1;
      t = x;
      for (let j = 0; j < 20; j++) {
        const v = ((ax * t + bx) * t + cx) * t;
        if (Math.abs(v - x) < 1e-5) break;
        if (v < x) lo = t;
        else hi = t;
        t = (lo + hi) / 2;
      }
      break;
    }
    t -= e / d;
  }
  return ((ay * t + by) * t + cy) * t;
}

/** The strong ease-out for entering UI: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(x: number) {
  'worklet';
  return bezier(x, 0.23, 1, 0.32, 1);
}

/** A gentler ease-in-out for a liquid's level: cubic-bezier(0.45, 0, 0.3, 1), readable from end to end. */
export function easeLiquid(x: number) {
  'worklet';
  return bezier(x, 0.45, 0, 0.3, 1);
}

/** The strong ease-in-out for things moving on screen: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(x: number) {
  'worklet';
  return bezier(x, 0.77, 0, 0.175, 1);
}

export function rand(i: number, salt: number) {
  'worklet';
  const x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** The drum's projection: world point → screen point (pt) and camera depth. */
export function project(x: number, y: number, z: number, sx: number) {
  'worklet';
  const cy = Math.cos(CAM.yaw);
  const sy = Math.sin(CAM.yaw);
  const x1 = cy * x + sy * z;
  const z1 = -sy * x + cy * z;
  const cp = Math.cos(CAM.pitch);
  const sp = Math.sin(CAM.pitch);
  const y2 = cp * y - sp * z1;
  const z2 = sp * y + cp * z1;
  const depth = CAM.dist + z2;
  const f = L.drumR * CAM.dist * sx;
  return { x: L.drumX * sx + (f * x1) / depth, y: L.drumY * sx - (f * y2) / depth, z: depth, f };
}

/** Wind-up over the first 40 % (quadratic), then a long cubic deceleration with matching speed. */
function spinCurve(u: number) {
  'worklet';
  if (u <= 0) return 0;
  if (u >= 1) return 1;
  const k = 0.4;
  const a = 3 / (2 * k + k * k);
  if (u < k) return a * u * u;
  const v = (u - k) / (1 - k);
  return a * k * k + (1 - a * k * k) * (1 - (1 - v) * (1 - v) * (1 - v));
}

export function cageAngle(t: number) {
  'worklet';
  return IDLE_RATE * t + SPIN_EXTRA * spinCurve(seg(t, T.spin[0], T.spin[1]));
}

/** Screen centre (pt) of a card's ball icon. */
export function iconCentre(card: number, k: number, strip: number, sx: number, sy: number) {
  'worklet';
  const [dx, r] = ICON_BALLS[card][k];
  return {
    x: (L.cardX0 + card * (L.cardW + L.cardGap) + dx + strip) * sx,
    y: (L.cardY + L.ballDY) * sy,
    r: r * sx,
  };
}

export type Ball = {
  x: number; // screen centre (layout pt × scale)
  y: number;
  r: number; // screen radius
  z: number; // camera depth (world units)
  rx: number; // rotation (rad) about x, y and z
  ry: number;
  rz: number;
  a: number;
  glow: number;
  sq: number; // squash: vertical scale (1 = round)
};

export type Frame = {
  t: number;
  theta: number; // cage angle
  omega: number; // cage angular speed (rad/s), for the ring ghosts
  speed: number; // 0 … 1, how hard the drum spins
  from: number;
  to: number;
  selK: number; // 0 = the selection is on `from`, 1 = on `to`
  sweep: number; // light running round the chosen card's outline, 0 … 1
  check: number; // the chosen card's check mark (overshoots)
  strip: number; // card strip offset, pt
  iconA: number[][]; // ball icons shown on each card
  balls: Ball[]; // the GPU's balls (the winner leaves them at the handoff)
  hero: Ball | null; // the winner on the top layer, from the handoff to the morph
  press: number;
  cardPress: number[];
  shimmer: number;
  dim: number; // the page dims behind the winner
  focus: number; // rack focus 0 … 1 (page blur)
  stitches: number; // 0 … 1 of the seam's stitches popped
  stitchT: number; // seconds since the first stitch popped (−1 before)
  morph: number; // 0 = the ball, 1 = the golden ticket
  ticketA: number; // the ticket's body
  number: number; // entry number roll 0 … 1
  foil: number; // light sweep across the foil 0 … 1 (0 when idle)
  claimK: number; // 0 = "Play", 1 = "Claim ticket"
  claimGlint: number; // a glint across Claim while it waits, 0 … 1 (0 when idle)
  claimPress: number;
  tear: number; // the stub's tear and flight 0 … 1 (0 before)
  burn: number; // the body burning into dust 0 … 1
  absorb: number; // the chosen card glowing as the dust arrives 0 … 1
  land: number; // seconds since the stub landed in the check (−1 when idle)
  tilt: number[]; // the ticket's tilt (rad): about its vertical axis, about its horizontal one
  lit: number; // the foil's lighting, faded in after the morph
  streak: number; // the anamorphic streak through the drawn ball as it flies out
  streakAt: number[];
  rays: number; // god rays from the parting covers
  ring: number; // the chromatic shockwave of the burst, and its radius (px)
  ringR: number;
  aurora: number; // silk light behind the waiting ticket
  lens: number[]; // the liquid-glass selection slab: x, y, w, h (pt) and strength
  fill: number; // Claim's liquid gold rising 0 … 1, then draining 0 … 1 from `drainAt` (pt from the left)
  drain: number;
  drainAt: number;
  idleT: number; // seconds a touch-driven draw has waited (keeps ambient light moving)
  entered: number[]; // a card's gold check
  burst: number; // seconds since the foil burst (−1 when idle)
  glow: number;
  vortex: number; // confetti swirling round the drum
};

/** `tap` is a press's depth (0 … 1, eased) on card 0…2 or Play (3); `touch` = driven by a finger. */
export type Story = { from: number; to: number; reselect: boolean; tap: number; tapOn: number; touch: boolean; strip?: number; tiltX?: number; tiltY?: number; pressX?: number };

const AUTO: Story = { from: 0, to: 1, reselect: true, tap: 0, tapOn: -1, touch: false };

// ── The balls inside the drum: a real simulation, baked once ─────────────────────────────────
//
// Each ball falls in, rolls on the wire cage, is carried up its wall by friction while the cage
// spins, gets kicked inward when a wire passes under it, flies, bounces off the others and falls
// back: gravity, a spherical wall turning about the axle, restitution, sliding friction and
// ball-to-ball collisions, integrated at 480 Hz. It only depends on story time (the cage's angle is
// a function of it), so it is simulated once per card (one, two or three balls) at import and
// sampled at 120 Hz: every loop plays the same tumble, and the balls never turn with the cage.

const SIM_HZ = 480;
const BAKE_HZ = 120;
const GRAVITY = 62; // drum radii per s² (a 15 cm drum)
const WALL_E = 0.32; // restitution against the cage
const BALL_E = 0.55; // restitution between balls
const GRIP = 12; // how fast a touching ball takes up the wires' speed (1/s)
const WIRES = 6; // meridians: a kick each time one passes under a ball
const KICK = 0.3; // share of the wire's sliding speed a passing wire gives the ball

type Baked = { t0: number; count: number; data: number[] };

function bake(n: number): Baked {
  const t0 = T.fly[1];
  const t1 = T.back[1] + 2 * T.backStagger;
  const count = Math.ceil((t1 - t0) * BAKE_HZ) + 2;
  const data = new Array<number>(count * n * 4).fill(0);
  const p = [] as number[][];
  const v = [] as number[][];
  const roll = [] as number[];
  const spinRate = [] as number[];
  for (let k = 0; k < n; k++) {
    const phi = 0.16 * Math.sin(1.3 * (T.fly[1] + k * T.flyStagger) + 2 * k);
    p.push([(k - (n - 1) / 2) * 0.42, -INNER * Math.cos(phi), INNER * Math.sin(phi)]);
    v.push([0, -1.6, 0]);
    roll.push(k * 1.7);
    spinRate.push(0);
  }
  const live = (k: number, t: number) => {
    if (t < T.fly[1] + k * T.flyStagger) return false; // still flying in
    if (k === 0) return t < T.eject[0]; // drawn out
    return t < T.back[0] + (k - 1) * T.backStagger; // flying home
  };
  const h = 1 / SIM_HZ;
  const steps = SIM_HZ / BAKE_HZ;
  let t = t0;
  const wirePrev = new Array<number>(n).fill(0);
  for (let s = 0; s < count; s++) {
    for (let k = 0; k < n; k++) {
      const o = (s * n + k) * 4;
      data[o] = p[k][0];
      data[o + 1] = p[k][1];
      data[o + 2] = p[k][2];
      data[o + 3] = roll[k];
    }
    for (let j = 0; j < steps; j++) {
      const w = (cageAngle(t + h) - cageAngle(t - h)) / (2 * h);
      const ang = cageAngle(t);
      for (let k = 0; k < n; k++) {
        if (!live(k, t)) continue;
        const q = p[k];
        const u = v[k];
        u[1] -= GRAVITY * h;
        q[0] += u[0] * h;
        q[1] += u[1] * h;
        q[2] += u[2] * h;
        const d = Math.hypot(q[0], q[1], q[2]);
        if (d > INNER) {
          // On the cage: push back inside, bounce the normal speed, drag the rest towards the wires.
          const nx = q[0] / d;
          const ny = q[1] / d;
          const nz = q[2] / d;
          q[0] = nx * INNER;
          q[1] = ny * INNER;
          q[2] = nz * INNER;
          const vn = u[0] * nx + u[1] * ny + u[2] * nz;
          if (vn > 0) {
            u[0] -= (1 + WALL_E) * vn * nx;
            u[1] -= (1 + WALL_E) * vn * ny;
            u[2] -= (1 + WALL_E) * vn * nz;
          }
          // The wire's velocity at the contact: ω × r about the axle (x).
          const wy = -w * q[2];
          const wz = w * q[1];
          const g = Math.min(1, GRIP * h);
          u[1] += (wy - u[1]) * g;
          u[2] += (wz - u[2]) * g;
          u[0] *= 1 - 2.5 * h;
          // A wire passing under the ball throws it inward.
          const phase = Math.floor(((ang - Math.atan2(q[2], -q[1])) * WIRES) / Math.PI);
          if (phase !== wirePrev[k]) {
            // Mostly along the wire's motion (it catches the ball), a little inward (it bumps it off).
            const sy = wy - u[1];
            const sz = wz - u[2];
            const bump = Math.hypot(sy, sz) * 0.35 + Math.abs(w) * INNER * 0.05;
            u[1] += sy * KICK - ny * bump;
            u[2] += sz * KICK - nz * bump;
            u[0] += 0.25 * bump * Math.sin(phase * 2.39 + k * 1.3);
            wirePrev[k] = phase;
          }
          // Rolling on the wires: the roll follows the sliding speed along the wall.
          spinRate[k] = (Math.hypot(u[1] - wy, u[2] - wz) / BALL_R) * Math.sign(w || 1);
        }
        roll[k] += spinRate[k] * h;
        spinRate[k] *= 1 - 0.6 * h;
      }
      // Ball against ball.
      for (let a = 0; a < n; a++) {
        for (let b = a + 1; b < n; b++) {
          if (!live(a, t) || !live(b, t)) continue;
          const dx = p[b][0] - p[a][0];
          const dy = p[b][1] - p[a][1];
          const dz = p[b][2] - p[a][2];
          const dd = Math.hypot(dx, dy, dz);
          if (dd >= 2 * BALL_R || dd < 1e-6) continue;
          const nx = dx / dd;
          const ny = dy / dd;
          const nz = dz / dd;
          const push = (2 * BALL_R - dd) / 2;
          p[a][0] -= nx * push;
          p[a][1] -= ny * push;
          p[a][2] -= nz * push;
          p[b][0] += nx * push;
          p[b][1] += ny * push;
          p[b][2] += nz * push;
          const rel = (v[b][0] - v[a][0]) * nx + (v[b][1] - v[a][1]) * ny + (v[b][2] - v[a][2]) * nz;
          if (rel < 0) {
            const jn = (-(1 + BALL_E) * rel) / 2;
            v[a][0] -= jn * nx;
            v[a][1] -= jn * ny;
            v[a][2] -= jn * nz;
            v[b][0] += jn * nx;
            v[b][1] += jn * ny;
            v[b][2] += jn * nz;
          }
        }
      }
      t += h;
    }
  }
  return { t0, count, data };
}

export const BAKED = [bake(1), bake(2), bake(3)];

/** Ball `k` of `n` inside the drum at story time `t` (world, not turning with the cage). */
function insideBall(k: number, n: number, t: number, manualSpin: number, manualSpeed: number) {
  'worklet';
  const b = BAKED[n - 1];
  const f = Math.max(0, Math.min(b.count - 1.001, (t - b.t0) * BAKE_HZ));
  const i = Math.floor(f);
  const u = f - i;
  const o0 = (i * n + k) * 4;
  const o1 = ((i + 1) * n + k) * 4;
  const d = b.data;
  let x = d[o0] + (d[o1] - d[o0]) * u;
  let y = d[o0 + 1] + (d[o1 + 1] - d[o0 + 1]) * u;
  let z = d[o0 + 2] + (d[o1 + 2] - d[o0 + 2]) * u;
  const roll = d[o0 + 3] + (d[o1 + 3] - d[o0 + 3]) * u;
  if (manualSpeed > 0.001) {
    // A crank turned by hand drags the balls up the wall the way it turns, a little.
    const a = 0.9 * manualSpeed * Math.sign(manualSpin || 1);
    const c = Math.cos(a);
    const s = Math.sin(a);
    const y2 = c * y - s * z;
    z = s * y + c * z;
    y = y2;
    x += 0.04 * manualSpeed * Math.sin(t * 9 + k);
  }
  return { x, y, z, roll: roll + manualSpin * 0.5 };
}

function quad(a: number, b: number, c: number, u: number) {
  'worklet';
  const v = 1 - u;
  return v * v * a + 2 * v * u * b + u * u * c;
}

/** The rack focus alone (for the blur view's animated intensity). */
export function focusAt(t: number) {
  'worklet';
  return easeInOut(seg(t, T.focus[0], T.focus[1])) * (1 - easeInOut(seg(t, T.focusOut[0], T.focusOut[1])));
}

function stripOf(c: number) {
  'worklet';
  return c === 2 ? STRIP_MIN : 0;
}

/** How far the strip can slide left: the third card's far edge then sits 17 pt from the screen's. */
export const STRIP_MIN = -(L.cardX0 * 2 + 3 * L.cardW + 2 * L.cardGap - 402);

/** A finger dragging the strip past either end meets growing resistance (iOS's rubber band). */
export function rubber(x: number) {
  'worklet';
  const over = x > 0 ? x : x < STRIP_MIN ? x - STRIP_MIN : 0;
  if (over === 0) return x;
  const d = 402 * 0.55;
  const r = (1 - 1 / ((Math.abs(over) * 0.55) / d + 1)) * d;
  return (x > 0 ? 0 : STRIP_MIN) + Math.sign(over) * r;
}

/**
 * The strip after a finger lets go: it coasts and slows like a scroll view (deceleration 0.998 per
 * ms), and past an end, or towards a goal (a tapped card brought into view, or autoPlay's own
 * choice), a critically damped spring carries it home with the speed it already had.
 */
function stepStrip(d: Driver, h: number, goal: number) {
  'worklet';
  if (d.drag) return;
  let target = goal;
  if (Number.isNaN(target)) {
    if (d.strip > 0) target = 0;
    else if (d.strip < STRIP_MIN) target = STRIP_MIN;
  }
  if (Number.isNaN(target)) {
    d.strip += d.stripV * h;
    d.stripV *= Math.pow(0.998, h * 1000);
    if (Math.abs(d.stripV) < 4) d.stripV = 0;
    return;
  }
  const w = 16;
  const a = -w * w * (d.strip - target) - 2 * w * d.stripV;
  d.stripV += a * h;
  d.strip += d.stripV * h;
  if (Math.abs(d.strip - target) < 0.2 && Math.abs(d.stripV) < 2) {
    d.strip = target;
    d.stripV = 0;
  }
}

/** How far the card strip is slid left (pt) so the chosen card is in view. */
export function stripAt(t: number, story: Story) {
  'worklet';
  const reselect = story.reselect && t >= T.reselect[0];
  const from = reselect ? story.to : story.from;
  const to = reselect ? story.from : story.to;
  const win = reselect ? T.reselect : T.select;
  return mix(stripOf(from), stripOf(to), easeOut(seg(t, win[0], win[1])));
}

/** Every animated value at story time `t`; `manualSpin` (rad) and `manualSpeed` come from the crank. */
export function frameAt(t: number, sx: number, sy: number, story: Story = AUTO, manualSpin = 0, manualSpeed = 0, idle = 0): Frame {
  'worklet';
  const reselect = story.reselect && t >= T.reselect[0];
  const from = reselect ? story.to : story.from;
  const to = reselect ? story.from : story.to;
  const selWin = reselect ? T.reselect : T.select;
  const selK = easeOut(seg(t, selWin[0], selWin[1]));
  const sweep = seg(t, selWin[0], selWin[0] + 0.5);
  // A tap's answer, seen tens of times: ease-out, no overshoot (scale from 0.6 with opacity).
  const check = easeOut(seg(t, selWin[0] + 0.02, selWin[0] + 0.2));
  const strip = story.strip !== undefined ? story.strip : stripAt(t, story);

  const theta = cageAngle(t) + manualSpin;
  const omega = (cageAngle(t + 0.008) - cageAngle(t - 0.008)) / 0.016;
  const speed = Math.min(1, Math.max(0, (omega - IDLE_RATE) / 14));

  const card = story.to;
  const n = card + 1;
  const iconA = [[1], [1, 1], [1, 1, 1]];
  const balls: Ball[] = [];
  let hero: Ball | null = null;
  const R = { x: TICKET.x * sx, y: TICKET.y * sy, r: 56 * sx };
  for (let k = 0; k < n; k++) {
    const home = iconCentre(card, k, strip, sx, sy);
    const inside = insideBall(k, n, t, manualSpin, manualSpeed);
    const p = project(inside.x, inside.y, inside.z, sx);
    const pr = (p.f * BALL_R) / p.z;
    const fs = T.fly[0] + k * T.flyStagger;
    const fe = T.fly[1] + k * T.flyStagger;
    const winner = k === 0;
    const bs = T.back[0] + (k - 1) * T.backStagger;
    const be = T.back[1] + (k - 1) * T.backStagger;
    let b: Ball = { x: home.x, y: home.y, r: home.r, z: 3.2, rx: inside.roll, ry: 0.6 * Math.sin(t * 1.7 + k), rz: 0.3 * k, a: 0, glow: 0, sq: 1 };
    const inPlay = winner ? t >= fs && t < T.morph[1] : t >= fs && t < be;
    if (inPlay) {
      iconA[card][k] = 0;
      b.a = 1;
      if (t < fe) {
        // In: lifted off the card, up over the page, down through the cage's top.
        const u = easeInOut(seg(t, fs, fe));
        b.x = quad(home.x, mix(home.x, p.x, 0.6), p.x, u);
        b.y = quad(home.y, L.drumY * sy - 150 * sy, p.y, u);
        b.r = mix(home.r, pr, easeOut(u));
        b.z = u < 0.55 ? 3.2 : mix(3.2, p.z, seg(u, 0.55, 1));
      } else {
        b.x = p.x;
        b.y = p.y;
        b.r = pr;
        b.z = p.z;
        const lt = t - fe;
        if (lt < T.landBounce) {
          // The bounce itself is simulated; this is the squash of the impact.
          b.sq = 1 - 0.22 * Math.exp(-lt * 14) * Math.cos(lt * 30);
        }
      }
      if (winner && t >= T.eject[0]) {
        // Drawn: pops up out of the cage from where it was (squash, then stretch), flies to the front.
        const u = seg(t, T.eject[0], T.eject[1]);
        const e = easeInOut(u);
        b.x = quad(p.x, L.drumX * sx, R.x, e);
        b.y = quad(p.y, L.drumY * sy - 170 * sy, R.y, e);
        b.r = mix(pr, R.r, easeOut(u));
        b.z = mix(p.z, 3.1, seg(u, 0, 0.18));
        b.sq = u < 0.12 ? 1 - 0.25 * Math.sin((u / 0.12) * Math.PI) : 1 + 0.1 * Math.sin(seg(u, 0.12, 0.5) * Math.PI);
        b.glow = seg(u, 0.4, 1);
        b.rx += u * 4;
        if (u >= T.handoff) {
          hero = { ...b };
          b.a = 0;
        }
      }
      if (!winner && t >= bs) {
        const u = easeInOut(seg(t, bs, be));
        b.x = quad(p.x, mix(p.x, home.x, 0.5), home.x, u);
        b.y = quad(p.y, Math.min(p.y, home.y) - 110 * sy, home.y, u);
        b.r = mix(pr, home.r, u);
        b.z = mix(p.z, 3.2, seg(u, 0, 0.3));
      }
    }
    if (winner && t >= T.iconBack[0] && t < LOOP) iconA[card][k] = easeOut(seg(t, T.iconBack[0], T.iconBack[1]));
    else if (winner && t >= T.morph[1]) iconA[card][k] = 0;
    balls.push(b);
  }
  // The winner at rest in front, before the morph: a slow turn and a bob.
  if (t >= T.eject[1] && t < T.morph[1] && hero === null) {
    hero = { x: R.x, y: R.y, r: R.r, z: 3.1, rx: 4 + (t - T.eject[1]) * 1.2, ry: 0.5, rz: 0, a: 1, glow: 1, sq: 1 };
  }
  if (hero && t >= T.eject[1]) {
    // The wind-up before it opens: it comes a little closer and its glow swells, it spins up and
    // settles to a stop just as it cracks (a speed bump, 1 − cos), then trembles for 0.18 s.
    const tt = Math.min(t, T.morph[0]);
    const u = seg(tt, T.antic[0], T.morph[0]);
    const near = easeInOut(seg(t, T.antic[0], T.morph[0]));
    hero.r = R.r * (1 + 0.08 * near);
    hero.glow = 1 + 0.5 * near;
    hero.rx = 4 + (tt - T.eject[1]) * 1.2 + 6.5 * (u - Math.sin(2 * Math.PI * u) / (2 * Math.PI));
    hero.ry += 0.35 * near;
    const shake = seg(t, T.morph[0] - 0.18, T.morph[0]) * (1 - seg(t, T.morph[0], T.morph[0] + 0.1));
    hero.x = R.x + Math.sin(t * 97) * 1.6 * sx * shake;
    hero.y = R.y + Math.sin((t - T.eject[1]) * 3) * 3 * sy * (1 - near) + Math.cos(t * 83) * 1.2 * sy * shake;
  }

  // A finger's press shows at once (scale 0.97 in, back out); autoPlay acts its own presses.
  const pulse = story.tap;
  const press = Math.max(story.touch ? 0 : Math.sin(Math.PI * seg(t, T.press[0], T.press[1])), story.tapOn === 3 ? pulse : 0);
  const cardPress = [0, 0, 0];
  if (story.tapOn >= 0 && story.tapOn < 3) cardPress[story.tapOn] = pulse;
  const shimmer = t > T.spin[0] && t < T.spin[0] + 0.9 ? seg(t, T.spin[0], T.spin[0] + 0.9) : 0;
  const focus = easeInOut(seg(t, T.focus[0], T.focus[1])) * (1 - easeInOut(seg(t, T.focusOut[0], T.focusOut[1])));
  const dim = 0.35 * focus;
  const stitches = seg(t, T.stitches[0], T.stitches[1]);
  const stitchT = t >= T.stitches[0] && t < T.morph[1] + 0.4 ? t - T.stitches[0] : -1;
  // Linear: the morph shader eases each of its phases (crack, peel, flatten) itself.
  const morph = seg(t, T.morph[0], T.morph[1]);
  const burn = seg(t, T.burn[0], T.burn[1]);
  const ticketA = t < T.morph[1] ? (t >= T.morph[0] ? 1 : 0) : 1 - burn;
  const absorb = Math.sin(Math.PI * seg(t, T.burn[0] + T.dust * 0.8, T.burn[1] + T.dust + 0.15));
  const land = t >= T.tear[1] && t < T.tear[1] + 0.6 ? t - T.tear[1] : -1;
  const lit = easeOut(seg(t, T.morph[1] - 0.05, T.morph[1] + 0.35));
  // While it waits the ticket turns slowly in the light (a finger can take it, see the driver).
  const sway = seg(t, T.morph[1], T.morph[1] + 0.4) * (1 - seg(t, T.claimPress[0] - 0.25, T.claimPress[0] + 0.1));
  const ht = t - T.morph[1];
  const tilt = [0.26 * Math.sin(ht * 2.2) * sway + (story.tiltX ?? 0), 0.14 * Math.sin(ht * 1.5 + 0.8) * sway + (story.tiltY ?? 0)];
  const number = easeOut(seg(t, T.number[0], T.number[1]));
  // The foil's light sweep, again every 1.6 s while the ticket waits to be claimed.
  // While a touch-driven draw waits for Claim, `idle` keeps these going.
  const since = t + idle - T.sweep[0];
  const sweepLen = T.sweep[1] - T.sweep[0];
  const ph = since >= 0 && t < T.claimPress[0] ? since % 1.6 : -1;
  const foil = ph >= 0 && ph < sweepLen ? ph / sweepLen : 0;
  const gph = t >= T.claim[1] + 0.3 && t < T.claimPress[0] ? (t + idle - T.claim[1] - 0.3) % 1.6 : -1;
  const claimGlint = gph >= 0 && gph < 0.6 ? gph / 0.6 : 0;
  const drainAt0 = T.claimPress[0] + 0.05;
  // The label goes with the gold: "Play" comes back only once most of the button is violet again.
  const claimK = easeOut(seg(t, T.claim[0], T.claim[1])) * (1 - easeLiquid(seg(t, drainAt0, drainAt0 + T.drain)));
  // The gold rises over 0.65 s and drains over 0.45 s on a gentle curve, so the liquid reads; once
  // drained it stays drained until the loop starts again.
  const fill = easeLiquid(seg(t, T.claim[0] - 0.05, T.claim[1] + 0.05));
  const drain = easeLiquid(seg(t, drainAt0, drainAt0 + T.drain));
  const drainAt = story.pressX !== undefined && !Number.isNaN(story.pressX) ? story.pressX : L.playW / 2;
  // The draw's light: a streak as the ball flies out, rays as the covers part, a ring at the burst,
  // silk behind the waiting ticket.
  const wb = hero ?? balls[0];
  const se = seg(t, T.eject[0] + 0.02, T.eject[0] + 0.35);
  const streak = se > 0 && se < 1 ? Math.pow(Math.sin(Math.PI * se), 1.3) : 0;
  const streakAt = [wb ? wb.x : 0, wb ? wb.y : 0];
  const rays = easeOut(seg(t, T.morph[0] + 0.02, T.morph[0] + 0.22)) * (1 - easeInOut(seg(t, T.burst - 0.05, T.burst + 0.35)));
  const rAge = t - T.burst;
  const ring = rAge >= 0 && rAge < 0.5 ? Math.pow(1 - rAge / 0.5, 2) : 0;
  const ringR = mix(40, 300, easeOut(Math.max(0, Math.min(1, rAge / 0.5)))) * sx;
  const aurora = easeInOut(seg(t, T.number[0], T.number[0] + 0.65)) * (1 - easeOut(seg(t, T.burn[0], T.burn[1] - 0.1)));
  // The liquid-glass slab flowing from the old card to the new: the leading edge first, then the
  // trailing one catches up, so it stretches like a drop on its way.
  const lensFrom = L.cardX0 + from * (L.cardW + L.cardGap) + strip;
  const lensTo = L.cardX0 + to * (L.cardW + L.cardGap) + strip;
  const lu = seg(t, selWin[0], selWin[1]);
  const lead = easeOut(Math.min(1, lu / 0.72));
  const trail = easeInOut(seg(lu, 0.18, 1));
  const right = to > from;
  const lx0 = right ? mix(lensFrom, lensTo, trail) : mix(lensFrom, lensTo, lead);
  const lx1 = (right ? mix(lensFrom, lensTo, lead) : mix(lensFrom, lensTo, trail)) + L.cardW;
  const lk = from !== to && lu > 0 && lu < 1 ? Math.pow(Math.sin(Math.PI * lu), 0.55) : 0;
  const squeeze = 1 - 0.06 * Math.sin(Math.PI * lu) * (lx1 - lx0 - L.cardW) / (L.cardW + L.cardGap);
  const lh = L.cardH * squeeze;
  const lens = [lx0, L.cardY + (L.cardH - lh) / 2, lx1 - lx0, lh, lk];
  const claimPress = story.touch ? 0 : Math.sin(Math.PI * seg(t, T.claimPress[0], T.claimPress[1]));
  const tear = t >= T.tear[0] && t < T.reselect[0] ? seg(t, T.tear[0], T.tear[1]) : 0;
  const entered = [0, 0, 0];
  entered[card] = easeOut(seg(t, T.entered[0], T.entered[1])) * (1 - seg(t, T.reselect[0], T.reselect[0] + 0.3));
  // The confetti keeps falling while a touch-driven draw waits (`idle`), never frozen mid-air.
  const bt = t + (t >= T.burst ? idle : 0);
  const burst = bt >= T.burst && bt < T.burst + 2.4 ? bt - T.burst : -1;
  const glow = 0.35 * Math.min(1, speed * 1.4) + 0.4 * focus;
  return {
    t, theta, omega, speed: Math.min(1, speed + manualSpeed), from, to, selK, sweep, check, strip, iconA, balls, hero, press, cardPress, shimmer, dim, focus,
    stitches, stitchT, morph, ticketA, number, foil, claimK, claimGlint, claimPress, tear, burn, absorb, land, tilt, lit, streak, streakAt, rays, ring, ringR, aurora, lens, fill, drain, drainAt, idleT: idle, entered, burst, glow, vortex: speed,
  };
}

// ── Touch driver ────────────────────────────────────────────────────────────────────────────────
// mode 0: autoPlay; 1: playing a touch-started stretch to `stop`; 2: waiting (RESUME after the
// last touch hands back to autoPlay).

export const RESUME = 2; // seconds without a touch before the story plays on back to rest

/**
 * mode 0: autoPlay. 1: playing a touch's part of the story up to `stop`. 2: waiting there (`idle`
 * seconds so far). 3: no touch for RESUME seconds, so the story plays on to its end (handing the
 * choice back to the first card) and autoPlay takes over at the loop's start, without a jump.
 */
export type Driver = {
  mode: number; t: number; stop: number; last: number; from: number; to: number; spin: number; spinV: number; idle: number; tap: number; tapOn: number; held: number;
  /** The card strip's own physics (pt, pt/s): `drag` while a finger holds it, `free` once a finger has moved it. */
  strip: number; stripV: number; drag: boolean; free: boolean; goal: number;
  /** A finger tilting the ticket (rad, rad/s); it settles back with one small wobble when let go. */
  tiltX: number; tiltY: number; tiltVX: number; tiltVY: number; tilting: boolean;
  /** Where the last finger came down on the button (pt from its left edge; NaN: its middle). */
  pressX: number;
};

export function makeDriver(): Driver {
  return { mode: 0, t: 0, stop: LOOP, last: -100, from: 0, to: 1, spin: 0, spinV: 0, idle: 0, tap: 0, tapOn: -1, held: -1, strip: 0, stripV: 0, drag: false, free: false, goal: NaN, tiltX: 0, tiltY: 0, tiltVX: 0, tiltVY: 0, tilting: false, pressX: NaN };
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  const step = Math.min(dt, 0.05);
  // Press feedback follows the finger: in while it is down, out when it lifts (ease-out, ~150 ms).
  // In fast (τ 35 ms, ~110 ms to settle), out a little slower (τ 65 ms, ~200 ms): the press is the deliberate half.
  const down = d.held >= 0;
  d.tap += ((down ? 1 : 0) - d.tap) * (1 - Math.exp(-step / (down ? 0.035 : 0.065)));
  // The crank's own momentum.
  d.spin += d.spinV * step;
  d.spinV *= Math.exp(-step * 1.6);
  if (Math.abs(d.spinV) < 0.01) d.spinV = 0;
  if (d.mode === 0) {
    d.t = auto ? (d.t + step) % LOOP : 0;
    if (auto && d.t < step) {
      d.from = 0;
      d.to = 1;
    }
  } else if (d.mode === 1) {
    const next = d.t + step;
    if (next >= d.stop) {
      d.t = d.stop;
      d.mode = 2;
      d.last = now;
    } else d.t = next;
  } else if (d.mode === 2) {
    d.idle = now - d.last;
    // A drawn ticket waits for its Claim however long it takes: only the visitor sends it home.
    const waitingForClaim = d.t >= T.hold - 0.01 && d.t < T.claimPress[0];
    if (auto && d.idle > RESUME && !waitingForClaim) {
      d.mode = 3;
      d.from = 0;
      d.idle = 0;
    }
  } else if (d.mode === 3) {
    d.t += step;
    if (d.t >= LOOP) {
      d.mode = 0;
      d.t = 0;
      d.from = 0;
      d.to = 1;
    }
  }
  if (d.mode !== 2) d.idle = 0;
  // The strip: autoPlay drives it (after a drag it springs back onto autoPlay's path first);
  // otherwise it coasts, or springs to the card a tap brought into view.
  const autoStrip = d.mode === 0 || d.mode === 3;
  const goal = autoStrip ? stripAt(d.t, { from: d.from, to: d.to, reselect: true, tap: 0, tapOn: -1, touch: false }) : d.goal;
  for (let i = 0; i < 4; i++) stepStrip(d, step / 4, goal);
  if (autoStrip && d.free && !d.drag && d.strip === goal) d.free = false;
  // The tilted ticket springs back, underdamped (ζ 0.6: one small overshoot, carrying the release velocity).
  if (!d.tilting) {
    const w = 11;
    const z = 0.6;
    for (let i = 0; i < 4; i++) {
      const hh = step / 4;
      d.tiltVX += (-w * w * d.tiltX - 2 * z * w * d.tiltVX) * hh;
      d.tiltVY += (-w * w * d.tiltY - 2 * z * w * d.tiltVY) * hh;
      d.tiltX += d.tiltVX * hh;
      d.tiltY += d.tiltVY * hh;
    }
    if (Math.abs(d.tiltX) + Math.abs(d.tiltY) < 1e-4 && Math.abs(d.tiltVX) + Math.abs(d.tiltVY) < 1e-3) {
      d.tiltX = 0;
      d.tiltY = 0;
      d.tiltVX = 0;
      d.tiltVY = 0;
    }
  }
  if (!d.free) d.strip = autoStrip ? goal : d.strip;
}

export function storyOf(d: Driver): Story {
  'worklet';
  return { from: d.from, to: d.to, reselect: d.mode === 0 || d.mode === 3, tap: d.tap, tapOn: d.tapOn, touch: d.mode === 1 || d.mode === 2, strip: d.free ? d.strip : undefined, tiltX: d.tiltX, tiltY: d.tiltY, pressX: d.mode === 1 || d.mode === 2 ? d.pressX : NaN };
}

/** A tap on card `c`: move the selection there and fly its balls into the drum. */
/** A finger down on card 0…2 or Play (3): its press feedback starts at once. */
export function pressIn(d: Driver, on: number, x = NaN) {
  'worklet';
  d.held = on;
  d.tapOn = on;
  if (on === 3) d.pressX = x;
}

export function pressOut(d: Driver) {
  'worklet';
  d.held = -1;
}

export function selectCard(d: Driver, c: number, now: number) {
  'worklet';
  if (d.mode !== 0 && d.t > T.press[0] && d.t < T.back[1]) return false;
  const before = d.free ? d.strip : stripAt(d.t, storyOf(d));
  const current = d.mode === 0 || d.mode === 3 ? (d.t >= T.select[0] && d.t < T.reselect[0] ? d.to : d.from) : d.to;
  if (current === c && d.mode !== 0) return false;
  d.from = current;
  d.to = c;
  d.t = T.select[0];
  d.mode = 1;
  d.stop = T.press[0] - 0.05;
  d.last = now;
  // Bring the chosen card fully into view (12 pt from the edges) if it is not.
  d.free = true;
  d.strip = before;
  const left = L.cardX0 + c * (L.cardW + L.cardGap);
  const lo = 12 - left;
  const hi = 402 - 12 - L.cardW - left;
  d.goal = Math.max(STRIP_MIN, Math.min(0, Math.min(hi, Math.max(lo, d.strip))));
  return true;
}


/** A tap on Play: spin and draw, then wait with the winner out (a second tap sends it home). */
export function pressPlay(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 0 || d.mode === 3) {
    const chosen = d.t >= T.select[0] && d.t < T.reselect[0] ? d.to : d.from;
    d.from = chosen;
    d.to = chosen;
    if (d.t >= T.reselect[0]) d.t = 0;
  }
  if (d.t >= T.hold - 0.01 && d.t < T.claimPress[0]) {
    // Claim: tear the stub off and send it home.
    d.t = T.claimPress[0];
    d.mode = 1;
    d.stop = T.reselect[0] - 0.05;
    return true;
  }
  if (d.t < T.press[0]) {
    // From rest the chosen card's balls fly in first, at once; once they are in, the spin starts.
    if (d.t < T.fly[0]) d.t = T.fly[0] - 0.02;
    else if (d.t > T.fly[1] + 0.35) d.t = Math.max(d.t, T.press[0] - 0.02);
    d.mode = 1;
    d.stop = T.hold;
    return true;
  }
  if (d.t < T.hold) {
    d.mode = 1;
    d.stop = T.hold;
    return true;
  }
  return false;
}

export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 0 || d.mode === 3) {
    // Freeze autoPlay where it is; the loop resumes RESUME s after the last touch.
    d.mode = 2;
    d.to = d.t >= T.select[0] && d.t < T.reselect[0] ? d.to : d.from;
    d.from = d.to;
  }
}

/** A finger takes the strip: autoPlay pauses there and the strip follows the finger. */
export function grabStrip(d: Driver, now: number) {
  'worklet';
  if (!d.free) d.strip = stripAt(d.t, storyOf(d));
  touched(d, now);
  d.free = true;
  d.drag = true;
  d.stripV = 0;
  d.goal = NaN;
}

export function releaseStrip(d: Driver, velocity: number, now: number) {
  'worklet';
  d.drag = false;
  d.stripV = velocity;
  d.last = now;
}

/** While the ticket waits, a finger can take it and tilt it in the light. */
export function canTilt(t: number) {
  'worklet';
  return t >= T.morph[1] && t < T.claimPress[0];
}

export function grabTicket(d: Driver, now: number) {
  'worklet';
  touched(d, now);
  d.tilting = true;
  d.tiltVX = 0;
  d.tiltVY = 0;
}

/** Drag (pt) → tilt, with resistance past about 25°. */
export function tiltTicket(d: Driver, dx: number, dy: number, now: number) {
  'worklet';
  const soft = (v: number) => 0.55 * Math.tanh(v / 0.55);
  d.tiltX = soft(dx / 170);
  d.tiltY = soft(-dy / 140);
  d.last = now;
}

export function releaseTicket(d: Driver, vx: number, vy: number, now: number) {
  'worklet';
  d.tilting = false;
  d.tiltVX = Math.max(-8, Math.min(8, vx / 170));
  d.tiltVY = Math.max(-8, Math.min(8, -vy / 140));
  d.last = now;
}
