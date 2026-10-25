/*
 Popcorn Pop Button — the motion. Every frame is a pure function of a small "story" (when each
 kernel popped, when it was tipped into the bowl, when the lid lifted, when the label rolled), so
 the self-playing loop and the finger share one drawing: the loop fills the story from the
 timeline, a finger fills it from the simulation in the component.

 autoPlay (LOOP = 8 s):
   0.0 – 1.2   rest: 22 kernels in the copper pan, glass lid on, "Start the movie".
   1.2 – 2.6   a press (scale 0.97 in 0.1 s): the pan heats (glow, thermometer arc, kernels jitter).
   2.6 – 4.6   popping: one by one, slow, then fast, then slow (rank q -> 2.6 + 2 s * (q + 0.9 sin 2πq / 2π)).
   4.6 – 5.0   the heap lifts the lid; it rides up on the popcorn (ease-out back).
   5.0 – 5.35  the label rolls to "Enjoy the show"; the press lets go at 4.8.
   6.4 – 8.0   the pan tips into the bowl on the movie card (staggered arcs), kernels drop back in,
               the lid settles, the label rolls back. 8.0 is the same frame as 0.0.
*/

export const LOOP = 8;
export const N = 22;
/** Corns needed before a release counts (and before the lid lifts under a finger). */
export const ENOUGH = 15;
export const NEVER = 1e9;

export type Geo = {
  w: number;
  h: number;
  /** The round button. */
  bx: number;
  by: number;
  D: number;
  /** The pan's opening (an ellipse) and its front wall height. */
  px: number;
  py: number;
  rx: number;
  ry: number;
  wall: number;
  /** The lid's dome height. */
  dome: number;
  /** The bowl's mouth on the movie card. */
  bowlX: number;
  bowlY: number;
};

/** Everything a frame is drawn from. Times are on one clock (loop time, or the frame clock). */
export type Story = {
  now: number;
  popAt: number[];
  goneAt: number[];
  emptyFor: number;
  lidAt: number;
  lidBack: number;
  labelAt: number;
  labelOff: number;
  heat: number;
  press: number;
};

export type Pose = {
  heat: number;
  press: number;
  shakeX: number;
  shakeY: number;
  /** Per kernel: x, y, rotation (deg), alpha, variant. */
  kernels: number[];
  /** Per corn in the pan, back to front: x, y, rotation, sx, sy, variant. */
  corns: number[];
  /** Corns on their way to the bowl: x, y, rotation, scale, variant. */
  flying: number[];
  /** Hull flecks: x, y, alpha. */
  flecks: number[];
  lidX: number;
  lidY: number;
  lidRot: number;
  label: number;
  bowl: number;
};

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
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

export function easeInOutCubic(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

export function easeOutBack(x: number) {
  'worklet';
  const k = clamp01(x) - 1;
  const c = 1.9;
  return 1 + (c + 1) * k * k * k + c * k * k;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** The pop order: rank of each kernel (a seeded shuffle), and the kernel at each rank. */
function makeRanks() {
  const idx: number[] = [];
  for (let i = 0; i < N; i += 1) idx.push(i);
  idx.sort((a, b) => hash(a * 3.7 + 1) - hash(b * 3.7 + 1));
  const rank: number[] = new Array(N).fill(0);
  for (let r = 0; r < N; r += 1) rank[idx[r]] = r;
  return rank;
}
export const RANK = makeRanks();

/** Slow, fast, slow: the share of the popping time at which rank q pops. */
export function popCurve(q: number) {
  'worklet';
  return q + (0.9 * Math.sin(2 * Math.PI * q)) / (2 * Math.PI);
}

function makeAutoPops() {
  const out: number[] = [];
  for (let i = 0; i < N; i += 1) out.push(2.6 + 2 * popCurve(RANK[i] / (N - 1)));
  return out;
}
export const AUTO_POP = makeAutoPops();
/** Under a finger: the cooking needed before rank r pops (see the component's simulation). */
export function cookNeeded(r: number) {
  'worklet';
  return 0.05 + 1.25 * popCurve(r / (N - 1));
}

/** Kernel spots on the pan's floor and the corns' places in the heap, in pan units (-1..1). */
function makeSpots() {
  const spots: number[] = [];
  const heap: number[] = [];
  for (let i = 0; i < N; i += 1) {
    const r = 0.74 * Math.sqrt((i + 0.5) / N) * (0.88 + 0.2 * hash(i + 61));
    const a = i * 2.39996 + 0.3 + (hash(i + 67) - 0.5) * 0.5;
    spots.push(r * Math.cos(a), r * Math.sin(a) * 0.92);
  }
  // Heap places by rank: 14 on the floor, then 8 piled on top (lift in pt, scaled by D later).
  for (let r = 0; r < N; r += 1) {
    if (r < 14) {
      const rr = 0.78 * Math.sqrt((r + 0.5) / 14);
      const a = r * 2.39996 + 0.9;
      heap.push(rr * Math.cos(a), rr * Math.sin(a), 0.004 + (1 - rr) * 0.02);
    } else {
      const j = r - 14;
      const rr = 0.5 * Math.sqrt((j + 0.5) / 8);
      const a = j * 2.39996 + 2.1;
      heap.push(rr * Math.cos(a), rr * Math.sin(a) - 0.12, 0.05 + (1 - rr) * 0.035);
    }
  }
  return { spots, heap };
}
const SPOTS = makeSpots();
export const SPOT = SPOTS.spots;
export const HEAP = SPOTS.heap;

/** Corn draw order (by rank): the floor layer back to front, then the pile back to front. */
function makeOrder() {
  const r: number[] = [];
  for (let i = 0; i < N; i += 1) r.push(i);
  r.sort((a, b) => (a < 14 ? 0 : 1000) + HEAP[a * 3 + 1] - ((b < 14 ? 0 : 1000) + HEAP[b * 3 + 1]));
  return r;
}
export const ORDER = makeOrder();
/** Kernel at each rank. */
function makeByRank() {
  const out: number[] = new Array(N).fill(0);
  for (let i = 0; i < N; i += 1) out[RANK[i]] = i;
  return out;
}
export const BY_RANK = makeByRank();

export function autoHeat(t: number) {
  'worklet';
  const up = 0.5 - 0.5 * Math.cos(Math.PI * seg(t, 1.2, 2.6));
  return up * (1 - easeInOutCubic(seg(t, 4.6, 5.6)));
}

export function autoPress(t: number) {
  'worklet';
  return easeOutCubic(seg(t, 1.2, 1.3)) * (1 - easeOutCubic(seg(t, 4.8, 4.95)));
}

/** The loop's story at loop time t. */
export function autoStory(t: number): Story {
  'worklet';
  const gone: number[] = [];
  for (let i = 0; i < N; i += 1) gone.push(6.4);
  return {
    now: t,
    popAt: AUTO_POP,
    goneAt: gone,
    emptyFor: 1.6,
    lidAt: 4.6,
    lidBack: 6.4,
    labelAt: 5.0,
    labelOff: 6.4,
    heat: autoHeat(t),
    press: autoPress(t),
  };
}

/** A corn's resting angle in the heap. */
function heapRot(r: number) {
  'worklet';
  return (hash(r + 101) - 0.5) * 140;
}

/** Where a corn sits in the heap (screen pt). */
function heapAt(g: Geo, r: number, out: number[]) {
  'worklet';
  out[0] = g.px + HEAP[r * 3] * g.rx * 0.86;
  out[1] = g.py + HEAP[r * 3 + 1] * g.ry * 0.8 - HEAP[r * 3 + 2] * g.D - g.D * 0.012;
}

export function makePose(s: Story, g: Geo): Pose {
  'worklet';
  const now = s.now;
  const heat = clamp01(s.heat);
  const D = s.emptyFor;
  const kernels: number[] = [];
  const corns: number[] = [];
  const flying: number[] = [];
  const flecks: number[] = [];
  const hp = [0, 0];
  const jig = heat * heat * g.D * 0.006;
  let bowl = 0;
  for (let i = 0; i < N; i += 1) {
    const r = RANK[i];
    const sx = g.px + SPOT[i * 2] * g.rx;
    const sy = g.py + SPOT[i * 2 + 1] * g.ry;
    const pop = s.popAt[i];
    const gone = s.goneAt[i];
    const goneCorn = pop <= now && gone <= now && pop < gone;
    // The kernel: on the floor until it pops, jittering with the heat; dropped back in after the bowl.
    let showKernel = pop > now;
    let dropY = 0;
    let alpha = 1;
    if (goneCorn) {
      const e = now - gone - D * (0.31 + (0.31 * r) / N);
      const dur = D * 0.22;
      if (e >= 0) {
        showKernel = true;
        const k = clamp01(e / dur);
        dropY = -g.D * 0.2 * (1 - k * k);
        if (k >= 1) dropY = -g.D * 0.012 * Math.max(0, Math.sin(Math.PI * clamp01((e - dur) / 0.12)));
        alpha = clamp01(k / 0.3);
      }
    }
    if (showKernel) {
      const k = 40 + Math.floor(hash(i + 9) * 20);
      const ph = hash(i + 4) * 6.283;
      const jx = jig * Math.sin((2 * Math.PI * k * now) / LOOP + ph);
      const jy = jig * 0.7 * Math.sin((2 * Math.PI * (k + 7) * now) / LOOP + ph * 1.7);
      kernels.push(sx + jx, sy + jy + dropY, hash(i + 2) * 360 + jx * 8, alpha, i % 4);
    }
    if (pop > now) continue;
    const tau = now - pop;
    // Hull flecks thrown out as it bursts.
    if (tau < 0.4) {
      for (let f = 0; f < 3; f += 1) {
        const a = -Math.PI / 2 + (hash(i * 7 + f) - 0.5) * 2.6;
        const v = g.D * (0.35 + 0.4 * hash(i * 5 + f + 2));
        flecks.push(sx + Math.cos(a) * v * tau, sy + Math.sin(a) * v * tau + g.D * 1.4 * tau * tau, 1 - tau / 0.4);
      }
    }
    heapAt(g, r, hp);
    if (goneCorn) {
      // Into the bowl: an arc up and over, shrinking, staggered by rank.
      const e = now - gone - D * ((0.34 * r) / N);
      if (e < 0) {
        corns.push(r, hp[0], hp[1], heapRot(r), 1, 1);
        continue;
      }
      const k = clamp01(e / (D * 0.28));
      if (k >= 1) {
        const late = e - D * 0.28;
        if (late < 0.25) bowl = Math.max(bowl, Math.sin((Math.PI * late) / 0.25));
        continue;
      }
      const u = easeInOutCubic(k);
      const mx = (hp[0] + g.bowlX) / 2;
      const my = Math.min(hp[1], g.bowlY) - g.D * 0.32;
      const x = (1 - u) * (1 - u) * hp[0] + 2 * (1 - u) * u * mx + u * u * g.bowlX;
      const y = (1 - u) * (1 - u) * hp[1] + 2 * (1 - u) * u * my + u * u * g.bowlY;
      flying.push(x, y, heapRot(r) + 220 * u, 1 - 0.5 * u, r % 6);
      continue;
    }
    // In flight from the kernel's spot to its place in the heap, then a little bounce.
    const T = 0.4 + 0.14 * hash(i + 31);
    const lidOn = pop < s.lidAt || s.lidBack > s.lidAt;
    const H = g.D * (lidOn ? 0.07 + 0.04 * hash(i + 17) : 0.16 + 0.08 * hash(i + 17));
    const u = clamp01(tau / T);
    let x = sx + (hp[0] - sx) * u;
    let y = sy + (hp[1] - sy) * u - H * 4 * u * (1 - u);
    let q = 0.2 * Math.exp(-tau / 0.09) * Math.cos((2 * Math.PI * tau) / 0.16);
    if (tau > T) {
      const l = tau - T;
      y -= g.D * 0.02 * Math.abs(Math.sin((Math.PI * l) / 0.16)) * Math.exp(-l / 0.11);
      q += 0.14 * Math.exp(-l / 0.06);
      x = hp[0];
    }
    const grow = 0.35 + 0.65 * easeOutBack(tau / 0.14);
    const spin = (hash(i + 51) > 0.5 ? 1 : -1) * (200 + 220 * hash(i + 57));
    const rot = heapRot(r) + spin * (1 - easeOutCubic(u));
    corns.push(r, x, y, rot, grow * (1 + q), grow * (1 - q));
  }
  // Back to front by heap order (the floor layer first).
  const sorted: number[] = [];
  for (let o = 0; o < N; o += 1) {
    const want = ORDER[o];
    for (let c = 0; c < corns.length; c += 6) {
      if (corns[c] === want) {
        sorted.push(corns[c + 1], corns[c + 2], corns[c + 3], corns[c + 4], corns[c + 5], want % 6);
        break;
      }
    }
  }
  // The lid: rattled by pops underneath, then lifted onto the heap, then lowered back.
  let lidX = 0;
  let lidY = 0;
  let lidRot = 0;
  for (let i = 0; i < N; i += 1) {
    const tau = now - s.popAt[i] - 0.05;
    if (tau >= 0 && tau < 0.12 && s.popAt[i] < s.lidAt) {
      lidY -= g.D * 0.012 * Math.sin((Math.PI * tau) / 0.12);
      lidRot += (hash(i + 77) - 0.5) * 3 * Math.sin((Math.PI * tau) / 0.12);
    }
  }
  const lift = easeOutBack(seg(now, s.lidAt, s.lidAt + 0.45));
  const down = s.lidBack >= s.lidAt ? easeInOutCubic(seg(now, s.lidBack + D * 0.55, s.lidBack + D * 0.83)) : 0;
  const perched = lift * (1 - down);
  lidX += g.D * 0.05 * perched;
  lidY += -g.D * 0.1 * perched;
  lidRot += -9 * perched;
  if (s.lidBack >= s.lidAt) lidY -= g.D * 0.008 * Math.sin(Math.PI * seg(now, s.lidBack + D * 0.83, s.lidBack + D * 0.95));
  const on = easeInOutCubic(seg(now, s.labelAt, s.labelAt + 0.35));
  const off = s.labelOff >= s.labelAt ? easeInOutCubic(seg(now, s.labelOff + D * 0.62, s.labelOff + D * 0.84)) : 0;
  const shake = heat * heat * g.D * 0.0025;
  return {
    heat,
    press: clamp01(s.press),
    shakeX: shake * Math.sin((2 * Math.PI * 47 * now) / LOOP),
    shakeY: shake * 0.6 * Math.sin((2 * Math.PI * 61 * now) / LOOP + 1),
    kernels,
    corns: sorted,
    flying,
    flecks,
    lidX,
    lidY,
    lidRot,
    label: on * (1 - off),
    bowl,
  };
}
