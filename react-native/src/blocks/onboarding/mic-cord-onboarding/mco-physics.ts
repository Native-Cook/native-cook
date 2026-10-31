// Mic Cord Onboarding — the cable (a Verlet rope), the mic on its end and the notes it puffs out.
// Helpers above callers.

export const N = 18; // rope points
export const SEG = 10; // pt between points
export const GRAVITY = 1500; // pt/s²
export const LOOP = 8;
/** Autoplay: a finger grabs the mic at 1.0 s, pulls it up and to the right, lets go at 1.8 s. */
export const GRAB = { at: 1.0, release: 1.8, dx: 40, dy: -18 } as const;
export const NOTES = 14;

export type Rope = {
  x: number[];
  y: number[];
  px: number[];
  py: number[];
  held: number; // 1 while a finger holds the mic
  hx: number;
  hy: number;
  speed: number; // the mic's speed, pt/s
  notes: number[]; // per note: x, y, vx, vy, age, kind (6 numbers each)
  spawn: number; // seconds since the last note
  burst: number; // notes still to puff out after a release
  was: number; // held on the previous step
  seed: number;
};

export const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 12.9898 + 4.1414) * 43758.5453;
  return s - Math.floor(s);
};

export const makeRope = (ax: number, ay: number): Rope => {
  'worklet';
  const x: number[] = [];
  const y: number[] = [];
  for (let i = 0; i < N; i++) {
    x.push(ax);
    y.push(ay + i * SEG);
  }
  const notes: number[] = [];
  for (let i = 0; i < NOTES * 6; i++) notes.push(i % 6 === 4 ? 99 : 0);
  return { x, y, px: x.slice(), py: y.slice(), held: 0, hx: 0, hy: 0, speed: 0, notes, spawn: 0, seed: 0, burst: 0, was: 0 };
};

/** One fixed step: Verlet integration with damping, the anchor pinned, the end held by a finger. */
export const stepRope = (r: Rope, ax: number, ay: number, h: number, damping: number) => {
  'worklet';
  const ex = r.x[N - 1];
  const ey = r.y[N - 1];
  for (let i = 1; i < N; i++) {
    const vx = (r.x[i] - r.px[i]) * damping;
    const vy = (r.y[i] - r.py[i]) * damping;
    r.px[i] = r.x[i];
    r.py[i] = r.y[i];
    // the mic's weight hangs on the last point
    const g = i === N - 1 ? GRAVITY * 1.6 : GRAVITY;
    r.x[i] += vx;
    r.y[i] += vy + g * h * h;
  }
  r.x[0] = ax;
  r.y[0] = ay;
  r.px[0] = ax;
  r.py[0] = ay;
  for (let it = 0; it < 14; it++) {
    for (let i = 0; i < N - 1; i++) {
      const dx = r.x[i + 1] - r.x[i];
      const dy = r.y[i + 1] - r.y[i];
      const d = Math.max(0.0001, Math.hypot(dx, dy));
      // a little stretch is allowed, like a coiled cable
      const diff = (d - SEG) / d;
      const k = d > SEG ? 0.5 : 0.25;
      const mx = dx * diff * k;
      const my = dy * diff * k;
      if (i > 0) {
        r.x[i] += mx;
        r.y[i] += my;
      }
      r.x[i + 1] -= mx;
      r.y[i + 1] -= my;
    }
    if (r.held === 1) {
      r.x[N - 1] = r.hx;
      r.y[N - 1] = r.hy;
    }
  }
  r.speed = Math.hypot(r.x[N - 1] - ex, r.y[N - 1] - ey) / h;
};

/** Notes: a fast swing puffs one out every 0.11 s; each floats up, drifts and fades over 1.6 s. */
export const stepNotes = (r: Rope, h: number, quiet: boolean) => {
  'worklet';
  r.spawn += h;
  // letting go of the mic puffs out a little burst
  if (r.was === 1 && r.held === 0) r.burst = 4;
  r.was = r.held;
  if (!quiet && r.held === 0 && (r.speed > 300 || r.burst > 0) && r.spawn > 0.08) {
    if (r.burst > 0) r.burst -= 1;
    r.spawn = 0;
    // reuse the oldest note
    let best = 0;
    for (let i = 1; i < NOTES; i++) if (r.notes[i * 6 + 4] > r.notes[best * 6 + 4]) best = i;
    r.seed += 1;
    const o = best * 6;
    // out of the mic's head, about 200 pt down the cable's last direction
    const dx = r.x[N - 1] - r.x[N - 3];
    const dy = r.y[N - 1] - r.y[N - 3];
    const dl = Math.max(0.001, Math.hypot(dx, dy));
    r.notes[o] = r.x[N - 1] + (dx / dl) * 200 + (hash(r.seed + 11) - 0.5) * 40;
    r.notes[o + 1] = r.y[N - 1] + (dy / dl) * 200;
    r.notes[o + 2] = (hash(r.seed) - 0.5) * 160 + (r.x[N - 1] - r.px[N - 1]) / h * 0.15;
    r.notes[o + 3] = -120 - hash(r.seed + 7) * 120;
    r.notes[o + 4] = 0;
    r.notes[o + 5] = Math.floor(hash(r.seed + 3) * 3);
  }
  for (let i = 0; i < NOTES; i++) {
    const o = i * 6;
    if (r.notes[o + 4] > 1.6) continue;
    r.notes[o + 4] += h;
    r.notes[o] += r.notes[o + 2] * h;
    r.notes[o + 1] += r.notes[o + 3] * h;
    r.notes[o + 2] *= 1 - h * 1.5;
    r.notes[o + 3] *= 1 - h * 0.6;
  }
};
