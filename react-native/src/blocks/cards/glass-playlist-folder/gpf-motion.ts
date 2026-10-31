// Glass Playlist Folder — timeline and springs. Helpers above callers.

export const LOOP = 8;

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
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};

/** Marks (s): the cursor comes in and clicks, the folder opens, a cover is hovered, it closes. */
export const T = {
  approach: [1.1, 2.0] as const,
  click: 2.0,
  hover: [3.4, 4.7] as const,
  leave: [5.0, 5.7] as const,
  close: 5.35,
  home: [5.7, 6.8] as const,
} as const;

/** Is the folder meant to be open at loop time t (autoPlay)? */
export const openAt = (t: number) => {
  'worklet';
  return t >= T.click && t < T.close ? 1 : 0;
};

export const COVERS = 4;

export type Springs = {
  open: number; // flap 0 shut … 1 open
  ov: number;
  covers: number[]; // each cover's rise
  cv: number[];
  target: number;
  changedAt: number; // clock when the target last changed
  lift: number; // the hovered cover comes forward
  lv: number;
  liftTarget: number;
  hovered: number; // which cover (0…3)
};

export const makeSprings = (open: number): Springs => {
  'worklet';
  return {
    open,
    ov: 0,
    covers: [open, open, open, open],
    cv: [0, 0, 0, 0],
    target: open,
    changedAt: -10,
    lift: 0,
    lv: 0,
    liftTarget: 0,
    hovered: 1,
  };
};

export const setTarget = (s: Springs, v: number, now: number) => {
  'worklet';
  if (s.target === v) return;
  s.target = v;
  s.changedAt = now;
};

/** Step all springs: the flap (response ~0.45 s, damping 0.7), the covers on a 60 ms stagger with
 * a bouncier spring (0.5 s, damping 0.55); closing runs the stagger the other way. */
export const stepSprings = (s: Springs, dt: number, now: number) => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let k = 0; k < n; k++) {
    s.ov += ((s.target - s.open) * 190 - s.ov * 19) * h;
    s.open += s.ov * h;
    for (let i = 0; i < COVERS; i++) {
      const order = s.target === 1 ? i : COVERS - 1 - i;
      const live = now - s.changedAt > 0.05 + order * 0.06 ? s.target : 1 - s.target;
      s.cv[i] += ((live - s.covers[i]) * 150 - s.cv[i] * 13.5) * h;
      s.covers[i] += s.cv[i] * h;
    }
    s.lv += ((s.liftTarget - s.lift) * 230 - s.lv * 20) * h;
    s.lift += s.lv * h;
  }
};
