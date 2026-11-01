// Trophy Fan Case — the hand of cards: layout targets, springs and the autoplay script.
// Helpers above callers.

export const LOOP = 8;
export const N = 4;
export const CARD = { w: 150, h: 228 } as const;

/** Autoplay marks (s). */
export const S = {
  fan: 0.45, // the deck fans out
  scrub: [1.5, 2.6] as const, // a finger runs along the hand
  pick: 2.85, // card 1 is lifted out and turned over
  back: 5.25, // it goes back into the hand
  close: 6.45, // the hand closes into a deck
} as const;
export const PICK = 1;

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

export type Pose = { x: number; y: number; r: number; s: number; f: number };
/** ph: 0 in the hand, 1 rising out of it (still behind the cards in front), 2 free above the hand,
 * 3 going back over the top of its slot, 4 sliding down into its slot (behind again). */
export type Body = Pose & { vx: number; vy: number; vr: number; vs: number; vf: number; ph: number };
/** How far above its slot a card rises when it leaves or comes back; the rest of the hand drops 70 pt
 * meanwhile, so 172 pt up its bottom edge clears the tops of the cards in front of it. */
export const CLEAR = 190;
export const PASS = 172;
export const DROP = 70;
export type Hand = {
  cards: Body[];
  spread: number; // 0 deck, 1 fanned (spring)
  vspread: number;
  focus: number; // card index under the finger (fractional), -9 none
  vfocus: number;
  focusOn: number; // 0..1 how much the focus lifts (spring)
  vfocusOn: number;
  sel: number; // selected card, -1 none
  held: number; // card held by a finger, -1 none
  hx: number; // finger position while holding
  hy: number;
};

export const makeHand = (): Hand => {
  'worklet';
  const cards: Body[] = [];
  for (let i = 0; i < N; i++) cards.push({ x: 0, y: 0, r: 0, s: 1, f: 0, vx: 0, vy: 0, vr: 0, vs: 0, vf: 0, ph: 0 });
  return { cards, spread: 0, vspread: 0, focus: -9, vfocus: 0, focusOn: 0, vfocusOn: 0, sel: -1, held: -1, hx: 0, hy: 0 };
};

/** Where card i sits in the hand: a fan about a pivot far below (deck when spread is 0). */
export const handPose = (i: number, spread: number, focus: number, focusOn: number, cx: number, fy: number): Pose => {
  'worklet';
  const c = i - (N - 1) / 2;
  const a = c * 11 * spread; // degrees
  const rad = (a * Math.PI) / 180;
  const R = 520;
  const deckX = c * 5;
  const deckY = Math.abs(c) * 3;
  const lift = focusOn * Math.max(0, 1 - Math.abs(focus - i) * 1.5);
  return {
    x: cx + mix(deckX, R * Math.sin(rad), spread),
    y: fy + mix(deckY, R * (1 - Math.cos(rad)), spread) - 24 * lift,
    r: mix(c * 1.6, a, spread),
    s: mix(0.97, 1, spread) * (1 + 0.04 * lift),
    f: 0,
  };
};

/** Damped spring toward a target, sub-stepped (k stiffness /s², z damping ratio). */
export const springTo = (p: number, v: number, target: number, k: number, z: number, dt: number) => {
  'worklet';
  const c = 2 * Math.sqrt(k) * z;
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  let x = p;
  let vv = v;
  for (let i = 0; i < n; i++) {
    vv += (k * (target - x) - c * vv) * h;
    x += vv * h;
  }
  return [x, vv];
};

/** The autoplay script at loop time t: spread, focus (and how much it lifts), selection. */
export const script = (t: number) => {
  'worklet';
  const spread = t >= S.fan && t < S.close ? 1 : 0;
  const k = seg(t, S.scrub[0], S.scrub[1]);
  const scrubbing = t >= S.scrub[0] - 0.1 && t < S.scrub[1] + 0.25;
  const focus = scrubbing ? mix(-0.4, 3.4, smooth(k)) : -9;
  const sel = t >= S.pick && t < S.back ? PICK : -1;
  return { spread, focus, focusOn: scrubbing ? 1 : 0, sel };
};
