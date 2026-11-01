// Holo Cash Card — the card's tilt, the live balance and the autoplay script. Helpers above callers.

export const LOOP = 8;
/** The balance in units of 0.00001 dollar: $5,020.20313. */
export const START = 502020313;
/** Seconds between two accrual ticks of the last digit, at 3 % and when boosted to 8 %. */
export const TICK = { base: 0.3, boost: 0.11 } as const;
export const S = {
  drag: [1.0, 2.4] as const, // a finger tilts the card one way, then the other, and lets go
  boostOn: 3.2,
  boostOff: 6.4,
  reset: [7.25, 7.75] as const, // the accrued digits dip out and come back at 313
} as const;

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return clamp01((t - a) / (b - a));
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};

/** The script's tilt target at loop time t (degrees about x and y) and whether a finger holds it. */
export const scriptTilt = (t: number) => {
  'worklet';
  if (t < S.drag[0] || t >= S.drag[1]) return { on: 0, rx: 0, ry: 0 };
  const k = seg(t, S.drag[0], S.drag[1]);
  // out to the upper left, across to the lower right
  const a = smooth(seg(k, 0, 0.35));
  const b = smooth(seg(k, 0.35, 0.9));
  return { on: 1, rx: mix(mix(0, 9, a), -7, b), ry: mix(mix(0, -15, a), 15, b) };
};

/** Accrual ticks since the start of the loop (boosted between boostOn and boostOff). */
export const ticksAt = (t: number) => {
  'worklet';
  const a = Math.min(t, S.boostOn) / TICK.base;
  const b = Math.max(0, Math.min(t, S.boostOff) - S.boostOn) / TICK.boost;
  const c = Math.max(0, t - S.boostOff) / TICK.base;
  return Math.floor(a + b + c);
};

export type Odo = { disp: number[]; tgt: number[]; vel: number[]; last: number[] };
export const COLS = 9;
export const makeOdo = (v: number): Odo => {
  'worklet';
  const disp: number[] = [];
  const tgt: number[] = [];
  const vel: number[] = [];
  const last: number[] = [];
  for (let k = 0; k < COLS; k++) {
    const d = Math.floor(v / Math.pow(10, COLS - 1 - k)) % 10;
    disp.push(d);
    tgt.push(d);
    vel.push(0);
    last.push(d);
  }
  return { disp, tgt, vel, last };
};

/** Each column rolls forward to its new digit on a spring (a carry rolls the next column too). */
export const stepOdo = (o: Odo, v: number, dt: number) => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let k = 0; k < COLS; k++) {
    const d = Math.floor(v / Math.pow(10, COLS - 1 - k)) % 10;
    if (d !== o.last[k]) {
      o.tgt[k] += (d - o.last[k] + 10) % 10;
      o.last[k] = d;
    }
    for (let i = 0; i < n; i++) {
      o.vel[k] += (420 * (o.tgt[k] - o.disp[k]) - 2 * Math.sqrt(420) * 0.9 * o.vel[k]) * h;
      o.disp[k] += o.vel[k] * h;
    }
    // keep the numbers small
    if (o.tgt[k] > 1000) {
      o.tgt[k] -= 1000;
      o.disp[k] -= 1000;
    }
  }
};
