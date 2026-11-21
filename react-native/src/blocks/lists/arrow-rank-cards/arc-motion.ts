import { DECK, headingFor } from './arc-theme';

export const LOOP = 9;
export const clamp = (v: number, a = 0, b = 1) => { 'worklet'; return Math.max(a, Math.min(b, v)); };
export const seg = (t: number, a: number, b: number) => { 'worklet'; return clamp((t - a) / (b - a)); };
export const mix = (a: number, b: number, p: number) => { 'worklet'; return a + (b - a) * p; };
export const smooth = (p: number) => { 'worklet'; return p * p * (3 - 2 * p); };
export const wrap = (i: number) => { 'worklet'; return ((i % 3) + 3) % 3; };

/** Normalized closed-form pop, exact endpoints, 6% peak overshoot. */
export const springAt = (p: number) => {
  'worklet';
  const u = clamp(p);
  if (u === 0 || u === 1) return u;
  const c = 1.25;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
};
/** The cascade front is the finger's position along the arrow; five slats share the turn, so two show both faces. */
export const fingerSlats = (p: number) => {
  'worklet';
  const front = -2 + 13 * p;
  return Array.from({ length: 9 }, (_, i) => clamp(0.5 + (front - i - 0.5) / 5));
};
export type ArcFrame = {
  index: number; next: number; p: number; q: number[]; drum: number;
  head: number; tail: number; stretch: number; beckon: number;
  ticks: number; shake: number; twitch: number; axis: number; reverse: number; flat: number; blur: number;
};
export const restFrame = (index = 0): ArcFrame => {
  'worklet';
  const i = wrap(index);
  const angle = headingFor(DECK[i].direction);
  return { index: i, next: wrap(i + 1), p: 0, q: [0, 0, 0, 0, 0, 0, 0, 0, 0], drum: 0, head: angle, tail: angle, stretch: 0, beckon: 0, ticks: 0, shake: 0, twitch: 0, axis: angle === 90 || angle === 270 ? 1 : 0, reverse: angle === 180 || angle === 270 ? 1 : 0, flat: 0, blur: 0 };
};
export const shortestTurn = (from: number, to: number) => {
  'worklet';
  const d = ((to - from + 540) % 360) - 180;
  return from + (d === -180 ? 180 : d);
};
/** Two +5 pt beckons along the heading, 280 ms each, starting at `from`; zero outside the window. */
export const beckonAt = (t: number, from = 0) => {
  'worklet';
  const u = t - from;
  return u > 0 && u < 0.56 ? 5 * Math.pow(Math.sin(Math.PI * ((u % 0.28) / 0.28)), 2) : 0;
};

/** Release starts with the held slat angles; the remaining prisms fall at 28 ms intervals. */
export const releaseAt = (held: ArcFrame, elapsed: number, commit: boolean, velocity = 0): ArcFrame => {
  'worklet';
  // The stagger starts at the cascade front, not at slat 0: turned slats never wait for finished ones.
  let first = 0, last = 8;
  while (first < 8 && held.q[first] >= 1) first++;
  while (last > 0 && held.q[last] <= 0) last--;
  const q = held.q.map((v, i) => {
    const order = Math.max(0, commit ? i - first : last - i);
    const k = springAt(seg(elapsed, order * 0.028, order * 0.028 + 0.26));
    return mix(v, commit ? 1 : 0, k);
  });
  const roll = seg(elapsed, 0.015, 0.3);
  const land = smooth(roll);
  const angle = shortestTurn(held.tail, headingFor(DECK[held.next].direction));
  const handoff = clamp(velocity, -3, 3) * elapsed * Math.pow(1 - seg(elapsed, 0, 0.3), 3);
  return { ...held, q, p: mix(held.p, commit ? 1 : 0, land) + handoff,
    drum: mix(held.drum, commit ? 1 : 0, land), stretch: held.stretch * (1 - springAt(seg(elapsed, 0, 0.26))),
    head: commit ? mix(held.head, angle, springAt(seg(elapsed, 0.06, 0.56))) : held.head,
    tail: commit ? mix(held.tail, angle, springAt(seg(elapsed, 0.26, 0.56))) : held.tail,
    ticks: held.ticks - (commit ? 12 * land : 0), beckon: 0, shake: 0, twitch: 0,
    blur: commit ? 24 * roll * (1 - roll) : 0 };
};

const turnFrame = (t: number, index: number, start: number, half: number, release: number, angle: number, nextAngle: number): ArcFrame => {
  'worklet';
  let p = 0;
  if (t < half) p = 0.55 * smooth(seg(t, start, half));
  else if (t < release) p = mix(0.55, 0.62, seg(t, half, release));
  const held = { ...restFrame(index), p, q: fingerSlats(p), head: angle, tail: angle,
    stretch: 36 * clamp(p * 2), drum: 0, ticks: index === 0 ? 0 : -index * 12 };
  if (t < start) return { ...held, beckon: beckonAt(t, start - 0.62) };
  if (t < release) return held;
  const from = { ...held, p: 0.62, q: fingerSlats(0.62), stretch: 36 };
  const f = releaseAt(from, t - release, true);
  f.head = mix(angle, nextAngle, springAt(seg(t, release + 0.06, release + 0.56)));
  f.tail = mix(angle, nextAngle, springAt(seg(t, release + 0.26, release + 0.56)));
  return f;
};

/** Seconds. Addendum replaces the throws and the long end hold with trivision and a six-tick riffle. */
export const frameAt = (time: number): ArcFrame => {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 2.3) return turnFrame(t, 0, 0.7, 1.25, 1.5, 0, 90);
  if (t < 4.85) return turnFrame(t, 1, 3, 3.7, 4.05, 90, 180);
  if (t < 6.9) return turnFrame(t, 2, 5.6, 6.1, 6.3, 180, 360);
  if (t < 8.3) {
    const r = (t - 6.9) / (1.4 / 6);
    const tick = Math.min(5, Math.floor(r));
    const k = r - tick;
    const f = restFrame(tick);
    const roll = seg(k, 0, 0.84);
    const p = smooth(roll);
    return { ...f, p, blur: 24 * roll * (1 - roll), q: Array.from({ length: 9 }, (_, i) => springAt(seg(k, i * 0.022, 0.64 + i * 0.022))),
      drum: p, head: 360 + 720 * smooth(seg(t, 6.9, 8.3)), tail: 360 + 720 * smooth(seg(t - 0.07, 6.9, 8.23)),
      stretch: 10 * Math.sin(Math.PI * p), ticks: -36 - 12 * r };
  }
  return { ...restFrame(), beckon: beckonAt(t, 8.3) };
};
