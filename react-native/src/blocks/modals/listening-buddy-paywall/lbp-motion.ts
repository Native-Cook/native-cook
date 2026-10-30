// Listening Buddy Paywall — the loop and the touch driver. The buddy listens to a beat (12 per
// loop); choosing a plan moves the frame, swells the card and changes the buddy's face; Continue
// sends notes up.

export const LOOP = 9.0;
export const RESUME = 2;
export const BEAT = LOOP / 12;
export const REST_PLAN = 1; // Annual, as in the pin

export const T = {
  picks: [
    [1.2, 0],
    [3.0, 2],
    [4.8, 1],
  ] as [number, number][],
  go: 6.5, // Continue
} as const;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
/** 0 → 1 → 0 over `dur` seconds from `at`. */
export function bump(t: number, at: number, dur: number) {
  'worklet';
  const p = (t - at) / dur;
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

/** The buddy's face for each plan: eyes (0 closed happy arcs … 1 open dots … 2 wide), smile, open mouth, blush. */
export const FACES = [
  { eyes: 1, smile: 0.45, open: 0, blush: 0.25 }, // Monthly: content
  { eyes: 0, smile: 1, open: 0, blush: 1 }, // Annual: blissful
  { eyes: 2, smile: 0.3, open: 1, blush: 0.6 }, // Lifetime: wow
];

export type Frame = {
  amb: number;
  plan: number; // selected
  from: number; // the plan before
  selAt: number; // seconds since it changed (large = settled)
  go: number; // seconds since Continue (−1 none)
  poke: number; // seconds since the buddy was poked (−1 none)
  press: number; // the bar pressed in (0…1)
  closeK: number; // the close button pressed in (0…1)
};

export function frameAt(t: number): Frame {
  'worklet';
  let plan = REST_PLAN;
  let from = REST_PLAN;
  let at = -10;
  for (let i = 0; i < T.picks.length; i++) {
    if (t >= T.picks[i][0]) {
      from = plan;
      plan = T.picks[i][1];
      at = T.picks[i][0];
    }
  }
  return {
    amb: t,
    plan,
    from,
    selAt: t - at,
    go: t >= T.go ? t - T.go : -1,
    poke: -1,
    press: bump(t, T.go - 0.02, 0.3),
    closeK: 0,
  };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  plan: number;
  from: number;
  selAt: number;
  goAt: number;
  pokeAt: number;
  pressing: number; // 1 bar, 2 close
  pressAt: number;
  releaseAt: number;
  released: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, plan: REST_PLAN, from: REST_PLAN, selAt: -10, goAt: -10, pokeAt: -10, pressing: 0, pressAt: -10, releaseAt: -10, released: 0 };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  const f = frameAt(d.t);
  d.mode = 2;
  d.plan = f.plan;
  d.from = f.from;
  d.selAt = now - f.selAt;
  d.goAt = f.go >= 0 ? now - f.go : -10;
}

export function select(d: Driver, plan: number, now: number) {
  'worklet';
  if (plan === d.plan) return false;
  d.from = d.plan;
  d.plan = plan;
  d.selAt = now;
  return true;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  if (!auto || d.pressing > 0 || now - d.last < RESUME) return;
  // home: Annual again, then the loop once the notes have gone
  if (d.plan !== REST_PLAN) {
    select(d, REST_PLAN, now);
    d.last = now - RESUME + 0.9;
    return;
  }
  if (now - d.selAt > 1 && now - d.goAt > 2.5 && now - d.pokeAt > 1) {
    d.mode = 0;
    d.t = 0;
  }
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const press = d.pressing === 1 ? smooth((now - d.pressAt) / 0.15) : d.released === 1 ? 1 - smooth((now - d.releaseAt) / 0.15) : 0;
  const closeK = d.pressing === 2 ? smooth((now - d.pressAt) / 0.15) : d.released === 2 ? 1 - smooth((now - d.releaseAt) / 0.15) : 0;
  return {
    amb: d.amb,
    plan: d.plan,
    from: d.from,
    selAt: now - d.selAt,
    go: now - d.goAt < 2.5 ? now - d.goAt : -1,
    poke: now - d.pokeAt < 1 ? now - d.pokeAt : -1,
    press,
    closeK,
  };
}
