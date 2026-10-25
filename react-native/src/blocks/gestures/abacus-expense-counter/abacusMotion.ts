/*
 The abacus: geometry, the scripted loop, and the bead physics under the finger.

 Five rods (Food, Transit, Fun, Home, Other), ten beads each. A bead counts when it sits on the
 right ("logged") side; each rod's bead has a price, so the rod's amount is count × price and the
 header total is their sum ($386.40 at rest).

 autoPlay (LOOP = 8 s), every bead a pure function of t:
   0.0 – 1.3   rest.
   1.3 – 5.0   six flicks: a fingertip lands on the left side of a bead, pushes it 3 pt (80 ms), and
               flicks: the bead glides right with friction (an exponential profile, 0.3 s), still
               moving when it hits the stack, and bounces back off it (4.5 pt, decaying in ~0.3 s).
               Food ×3 (1.40, 1.95, 2.50), Fun ×2 (3.15, 3.70), Other ×1 (4.35): $386.40 → $469.00.
   5.0 – 6.2   hold on the totals.
   6.0 – 6.25  "Undo today" is pressed.
   6.2 – 7.5   the logged beads glide back (ease-in-out, 0.6 s, rods 120 ms apart) and bounce
               off the left stack.
   7.5 – 8.0   rest.

 Touch: beads slide on their rod with friction (4/s), collide (restitution 0.5, momentum swaps
 between equal masses), bounce off the frame (0.35), and below 45 pt/s a spring settles each to
 the nearer of its two slots. A grabbed bead follows the finger and pushes the beads in front.
 */

export const LOOP = 8;
export const RODS = 5;
export const BEADS = 10;
export const CATEGORIES = ['Food', 'Transit', 'Fun', 'Home', 'Other'];
/** Cents per bead, beads logged at rest, and each category's weekly budget in cents. */
export const PRICES = [1600, 640, 1500, 4500, 460];
export const REST_COUNTS = [6, 5, 4, 4, 4];
export const BUDGETS = [16000, 6000, 12000, 20000, 4000];
export const WEEK_BUDGET = 52000;

export type Geo = {
  w: number;
  h: number;
  boardX: number;
  boardY: number;
  boardW: number;
  boardH: number;
  frame: number;
  band: number;
  /** Rod y (bead centres), and the inner ends of the rods. */
  rodY: number[];
  innerL: number;
  innerR: number;
  bw: number;
  bh: number;
  /** Bead travel between its two slots. */
  travel: number;
};

export function makeGeo(w: number, h: number, boardY: number, reserveBelow: number): Geo {
  const frame = 16;
  const boardX = 16;
  const boardW = w - 32;
  const band = Math.min(64, Math.max(52, (h - boardY - 2 * frame - reserveBelow) / RODS));
  const innerL = boardX + frame + 4;
  const innerR = boardX + boardW - frame - 4;
  const bw = Math.min(26, (innerR - innerL - 66) / BEADS);
  const bh = band >= 60 ? 30 : 27;
  const rodY: number[] = [];
  for (let r = 0; r < RODS; r++) rodY.push(boardY + frame + r * band + band - bh / 2 - 6);
  return { w, h, boardX, boardY, boardW, boardH: 2 * frame + RODS * band, frame, band, rodY, innerL, innerR, bw, bh, travel: innerR - innerL - BEADS * bw };
}

export function slotL(g: Geo, j: number) {
  'worklet';
  return g.innerL + j * g.bw + g.bw / 2;
}

export function slotR(g: Geo, j: number) {
  'worklet';
  return g.innerR - (BEADS - 1 - j) * g.bw - g.bw / 2;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** Rest positions of every bead, rod by rod. */
export function restBeads(g: Geo, out: number[]) {
  'worklet';
  for (let r = 0; r < RODS; r++) {
    const first = BEADS - REST_COUNTS[r];
    for (let j = 0; j < BEADS; j++) out[r * BEADS + j] = j >= first ? slotR(g, j) : slotL(g, j);
  }
  return out;
}

/** [rod, bead, flick time, undo time] */
const FLICKS: number[][] = [
  [0, 3, 1.4, 6.2],
  [0, 2, 1.95, 6.2],
  [0, 1, 2.5, 6.2],
  [2, 5, 3.15, 6.32],
  [2, 4, 3.7, 6.32],
  [4, 5, 4.35, 6.44],
];
const PUSH = 3;
const PUSH_FOR = 0.08;
const GLIDE_FOR = 0.3;
const GLIDE_K = 2.2;
const UNDO_FOR = 0.6;

/** A bounce off a stack: |sin| arcs decaying, 0 → peak → 0, positive. */
function bounce(tau: number, size: number) {
  'worklet';
  if (tau <= 0 || tau > 0.6) return 0;
  return size * Math.exp(-tau / 0.07) * Math.abs(Math.sin((Math.PI * tau) / 0.11));
}

/** Every bead's x at loop time t. */
export function loopBeads(g: Geo, t: number, out: number[]) {
  'worklet';
  restBeads(g, out);
  for (let k = 0; k < FLICKS.length; k++) {
    const [r, j, tf, tu] = FLICKS[k];
    const L = slotL(g, j);
    const R = slotR(g, j);
    let x = L;
    if (t >= tf && t < tf + PUSH_FOR) x = L + PUSH * easeOut(seg(t, tf, tf + PUSH_FOR));
    else if (t >= tf + PUSH_FOR) {
      const u = seg(t, tf + PUSH_FOR, tf + PUSH_FOR + GLIDE_FOR);
      const f = (1 - Math.exp(-GLIDE_K * u)) / (1 - Math.exp(-GLIDE_K));
      x = L + PUSH + (R - L - PUSH) * f;
      if (u >= 1) x = R - bounce(t - (tf + PUSH_FOR + GLIDE_FOR), 4.5);
    }
    if (t >= tu) x = R - (R - L) * easeInOut(seg(t, tu, tu + UNDO_FOR)) + bounce(t - tu - UNDO_FOR, 3);
    out[r * BEADS + j] = x;
  }
  return out;
}

/** The scripted fingertip: [x, y, alpha]. */
export function loopFinger(g: Geo, t: number) {
  'worklet';
  for (let k = 0; k < FLICKS.length; k++) {
    const [r, j, tf] = FLICKS[k];
    if (t < tf - 0.26 || t > tf + 0.3) continue;
    const contactX = slotL(g, j) - g.bw / 2 - 8;
    const y = g.rodY[r];
    const inn = easeOut(seg(t, tf - 0.26, tf));
    let x = contactX - 14 * (1 - inn);
    let yy = y - 18 * (1 - inn);
    if (t >= tf) x += PUSH * easeOut(seg(t, tf, tf + PUSH_FOR));
    // The flick: the finger carries on 30 pt to the right and lifts away.
    const out = easeOut(seg(t, tf + PUSH_FOR, tf + 0.3));
    x += 30 * out;
    yy -= 6 * out;
    return [x, yy, inn * (1 - out)];
  }
  return [0, 0, 0];
}

/** How pressed the "Undo today" chip looks in the loop (0–1). */
export function loopUndoPress(t: number) {
  'worklet';
  return easeOut(seg(t, 6.0, 6.08)) * (1 - easeInOut(seg(t, 6.14, 6.3)));
}

/** Logged beads per rod for positions x. */
export function countBeads(g: Geo, x: number[], out: number[]) {
  'worklet';
  for (let r = 0; r < RODS; r++) {
    let n = 0;
    for (let j = 0; j < BEADS; j++) if (x[r * BEADS + j] > (slotL(g, j) + slotR(g, j)) / 2) n++;
    out[r] = n;
  }
  return out;
}

const FRICTION = 4;
const SETTLE_BELOW = 45;
const SETTLE_K = 260;
const RESTITUTION = 0.5;
const WALL = 0.35;
const HIT = 60;

/**
 One step of the beads under touch. `x`, `v` are flat arrays (rod × bead); `grab` is
 [rod, bead, target x] or []. Returns the strongest collision speed of the step (0 = none).
 */
export function stepBeads(g: Geo, x: number[], v: number[], grab: number[], dt: number) {
  'worklet';
  let hit = 0;
  const sub = Math.max(1, Math.ceil(dt * 240));
  const h = dt / sub;
  const c = 2 * 0.8 * Math.sqrt(SETTLE_K);
  for (let s = 0; s < sub; s++) {
    for (let r = 0; r < RODS; r++) {
      const base = r * BEADS;
      const held = grab.length === 3 && grab[0] === r ? grab[1] : -1;
      for (let j = 0; j < BEADS; j++) {
        const i = base + j;
        if (j === held) {
          // Spread the finger's move over the frame's substeps, so the bead's speed is real.
          const nx = x[i] + (grab[2] - x[i]) / (sub - s);
          v[i] = (nx - x[i]) / h;
          v[i] = Math.max(-2500, Math.min(2500, v[i]));
          x[i] = nx;
          continue;
        }
        v[i] *= Math.exp(-FRICTION * h);
        if (Math.abs(v[i]) < SETTLE_BELOW) {
          const L = slotL(g, j);
          const R = slotR(g, j);
          const slot = Math.abs(x[i] - L) < Math.abs(x[i] - R) ? L : R;
          v[i] += (SETTLE_K * (slot - x[i]) - c * v[i]) * h;
        }
        x[i] += v[i] * h;
      }
      for (let pass = 0; pass < 3; pass++) {
        for (let j = 0; j < BEADS - 1; j++) {
          const a = base + j;
          const b = a + 1;
          const overlap = g.bw - (x[b] - x[a]);
          if (overlap <= 0) continue;
          if (j === held) {
            x[b] += overlap;
            if (v[b] < v[a]) v[b] = v[a];
          } else if (j + 1 === held) {
            x[a] -= overlap;
            if (v[a] > v[b]) v[a] = v[b];
          } else {
            x[a] -= overlap / 2;
            x[b] += overlap / 2;
            const rel = v[a] - v[b];
            if (rel > 0) {
              const mean = (v[a] + v[b]) / 2;
              v[a] = mean - (RESTITUTION * rel) / 2;
              v[b] = mean + (RESTITUTION * rel) / 2;
              if (rel > HIT && rel > hit) hit = rel;
            }
          }
        }
      }
      // Each bead stays where the frame leaves room for its neighbours; then the chain is
      // straightened both ways, so nothing overlaps and nothing passes through the frame.
      for (let j = 0; j < BEADS; j++) {
        const i = base + j;
        const lo = slotL(g, j);
        const hi = slotR(g, j);
        if (x[i] < lo) {
          x[i] = lo;
          if (v[i] < 0) {
            if (j === 0 && -v[i] > HIT && -v[i] > hit) hit = -v[i];
            v[i] = j === 0 ? -v[i] * WALL : 0;
          }
        } else if (x[i] > hi) {
          x[i] = hi;
          if (v[i] > 0) {
            if (j === BEADS - 1 && v[i] > HIT && v[i] > hit) hit = v[i];
            v[i] = j === BEADS - 1 ? -v[i] * WALL : 0;
          }
        }
      }
      for (let j = 1; j < BEADS; j++) if (x[base + j] - x[base + j - 1] < g.bw) x[base + j] = x[base + j - 1] + g.bw;
      for (let j = BEADS - 2; j >= 0; j--) if (x[base + j + 1] - x[base + j] < g.bw) x[base + j] = x[base + j + 1] - g.bw;
    }
  }
  return hit;
}

/** Whether every bead has come to rest in a slot. */
export function settled(g: Geo, x: number[], v: number[]) {
  'worklet';
  for (let r = 0; r < RODS; r++)
    for (let j = 0; j < BEADS; j++) {
      const i = r * BEADS + j;
      if (Math.abs(v[i]) > 1) return false;
      const d = Math.min(Math.abs(x[i] - slotL(g, j)), Math.abs(x[i] - slotR(g, j)));
      if (d > 0.3) return false;
    }
  return true;
}
