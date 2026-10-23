/**
 * Wallet First Deposit — the timeline. Every value on screen is a pure function of the loop time
 * `t` (seconds, 0 ≤ t < LOOP): no state is accumulated between frames, so t = LOOP equals t = 0.
 *
 *   0.0–1.4  rest = pin 15 (empty wallet)
 *   1.4–2.6  the verification bar fills; 2.4–2.7 the banner copy turns into "Verified…"
 *   2.6–2.75 anticipation: the card dips to 0.96
 *   2.75–3.55 the card flies on an arc into the header and settles on a spring; 3.45–3.95 a sheen sweeps it
 *   2.9–3.4  the banner leaves; 3.25–4.1 the content below glides down to its funded place
 *   3.3–4.3  the balance rolls to $6,790.22
 *   3.6–4.2  Receive / Send pop in; 3.9–4.9 Transactions cascades in
 *   3.4–5.3  three action cards become four payee cards; the orbs pop
 *   4.6–6.2  Analytics: the plot grows, the orange line draws, the forecast fades in
 *   6.2–8.4  hold = pin 14
 *   8.4–10   staged return to rest
 */

export const LOOP = 10;

/** Where a run started by a tap stops: the funded hold, and the end of the return. */
export const FUNDED_AT = 6.2;
export const RETURN_AT = 8.4;
export const FILL_AT = 1.4;
/** The card lands (first crossing of its target): a light haptic when a tap started the run. */
export const LAND_AT = 3.22;
/** After the last touch, autoPlay waits this long before it carries on from where it is. */
export const RESUME_AFTER = 2000;

export const SPRINGS = {
  // Reanimated-style (duration ≈ settle, dampingRatio) expressed as natural frequency for a closed form.
  flight: { omega: 11.4, zeta: 0.72 },
  ui: { omega: 15.7, zeta: 0.8 },
  pop: { omega: 12.6, zeta: 0.55 },
  width: { omega: 13, zeta: 0.7 },
} as const;

export const STAGGER = {
  pills: 0.06,
  tx: 0.05,
  payees: 0.06,
  cards: 0.04,
  digits: 0.03,
} as const;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** Solve the curve's x coordinate before sampling y; shared by all native ports. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const t = (lo + hi) / 2;
    const u = 1 - t;
    const at = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t;
    if (at < x) lo = t;
    else hi = t;
  }
  const t = (lo + hi) / 2;
  const u = 1 - t;
  return 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t;
}

/** Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number) {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

/** A damped spring from 0 to 1, `tau` seconds after it starts. */
export function spring(tau: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return 0;
  const { omega, zeta } = s;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  const v = 1 - env * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
  return env < 1e-4 ? 1 : v;
}

/** Every animated scalar of the screen at loop time t. 0 = pin 15, 1 = pin 14 unless noted. */
export type Frame = {
  fill: number; // verification bar, fraction of the track
  copy: number; // banner copy: 0 original, 1 "Verified…"
  squeeze: number; // card anticipation, 0..0.04
  fly: number; // card along its arc (spring: may pass 1 a little)
  lift: number; // 0 at rest, 1 mid-flight
  tilt: number; // degrees
  sheen: number; // specular sweep 0..1 (0 and 1 = off the card)
  banner: number; // 1 = banner gone
  dot: number; // 1 = red dot gone
  balance: number; // odometer master 0..1
  reflow: number; // Recent payees block from A to B
  reflow2: number; // Analytics block from A to B
  pill0: number;
  pill1: number;
  txHead: number;
  txCard: number;
  txRow0: number;
  txRow1: number;
  green: number;
  actionsOut: number; // 1 = action content gone
  card0: number; // card shape morph A → B
  card1: number;
  card2: number;
  card3: number; // the fourth (new) payee card
  orb0: number;
  orb1: number;
  orb2: number;
  orb3: number;
  names: number; // payee names and See all
  plot: number; // chart geometry A → B
  plotA: number; // 1 = A's flat area gone
  draw: number; // orange line trim
  forecast: number;
  spentValue: number; // $0 → $983 odometer
  label: number; // $312 → $1,92k
};

function forward(t: number): Frame {
  'worklet';
  const flightLinear = seg(t, 2.75, 3.3);
  const pills = (k: number) => spring(t - 3.6 - k * STAGGER.pills, SPRINGS.ui);
  const tx = (k: number) => easeOut(seg(t, 3.9 + k * STAGGER.tx, 4.35 + k * STAGGER.tx));
  const card = (k: number) => easeInOut(seg(t, 3.35 + k * STAGGER.cards, 4.3 + k * STAGGER.cards));
  const orb = (k: number) => spring(t - 4.2 - k * STAGGER.payees, SPRINGS.pop);
  return {
    fill: lerp(0, 1, easeInOut(seg(t, FILL_AT, 2.6))),
    copy: easeInOut(seg(t, 2.4, 2.7)),
    squeeze: 0.04 * easeOut(seg(t, 2.6, 2.75)) * (1 - easeOut(seg(t, 2.75, 2.95))),
    fly: spring(t - 2.75, SPRINGS.flight),
    lift: Math.sin(Math.PI * flightLinear),
    tilt: -8 * Math.sin(Math.PI * Math.pow(flightLinear, 0.75)),
    sheen: seg(t, 3.45, 3.95),
    banner: easeOut(seg(t, 2.9, 3.4)),
    dot: easeOut(seg(t, 3.3, 3.55)),
    balance: seg(t, 3.3, 4.3),
    reflow: easeInOut(seg(t, 3.25, 4.0)),
    reflow2: easeInOut(seg(t, 3.35, 4.1)),
    pill0: pills(0),
    pill1: pills(1),
    txHead: tx(0),
    txCard: tx(1),
    txRow0: tx(2),
    txRow1: tx(3),
    green: easeInOut(seg(t, 4.5, 4.9)),
    actionsOut: easeOut(seg(t, 3.2, 3.45)),
    card0: card(0),
    card1: card(1),
    card2: card(2),
    card3: easeOut(seg(t, 4.1, 4.6)),
    orb0: orb(0),
    orb1: orb(1),
    orb2: orb(2),
    orb3: orb(3),
    names: easeOut(seg(t, 4.35, 4.85)),
    plot: easeInOut(seg(t, 4.6, 5.4)),
    plotA: easeOut(seg(t, 4.6, 5.0)),
    draw: easeInOut(seg(t, 4.85, 6.0)),
    forecast: easeOut(seg(t, 5.3, 6.0)),
    spentValue: seg(t, 4.8, 5.6),
    label: easeInOut(seg(t, 5.3, 5.7)),
  };
}

/** The return, about 3× faster and staged: lists leave, the balance rolls down, the card glides back. */
function back(t: number): Frame {
  'worklet';
  const out = (a: number, b: number) => 1 - easeOut(seg(t, a, b));
  const glide = 1 - easeInOut(seg(t, 9.0, 9.6));
  const bannerIn = easeOut(seg(t, 9.25, 9.7));
  const card = (k: number) => 1 - easeInOut(seg(t, 8.7 + k * 0.03, 9.35 + k * 0.03));
  return {
    // The banner is out of sight until 9.25: its bar and copy reset there.
    fill: t < 9.1 ? 1 : 0,
    copy: t < 9.1 ? 1 : 0,
    squeeze: 0,
    fly: glide,
    lift: 0.45 * Math.sin(Math.PI * seg(t, 9.0, 9.6)),
    tilt: 4 * Math.sin(Math.PI * seg(t, 9.0, 9.6)),
    sheen: 0,
    banner: 1 - bannerIn,
    dot: out(9.6, 9.9),
    balance: 1 - easeInOut(seg(t, 8.5, 9.1)),
    reflow: 1 - easeInOut(seg(t, 8.75, 9.35)),
    reflow2: 1 - easeInOut(seg(t, 8.8, 9.4)),
    pill0: out(8.5, 8.75),
    pill1: out(8.46, 8.71),
    txHead: out(8.55, 8.8),
    txCard: out(8.5, 8.75),
    txRow0: out(8.4, 8.62),
    txRow1: out(8.43, 8.65),
    green: out(8.4, 8.6),
    actionsOut: 1 - easeOut(seg(t, 9.4, 9.85)),
    card0: card(0),
    card1: card(1),
    card2: card(2),
    card3: out(8.45, 8.75),
    orb0: out(8.4, 8.65),
    orb1: out(8.43, 8.68),
    orb2: out(8.46, 8.71),
    orb3: out(8.4, 8.6),
    names: out(8.4, 8.6),
    plot: 1 - easeInOut(seg(t, 8.6, 9.2)),
    plotA: 1 - easeOut(seg(t, 9.0, 9.5)),
    draw: 1 - easeInOut(seg(t, 8.4, 8.8)),
    forecast: out(8.4, 8.7),
    spentValue: 1 - seg(t, 8.5, 9.0),
    label: 1 - easeInOut(seg(t, 8.6, 8.9)),
  };
}

// Declared after forward/back: a worklet captures the functions it calls when it is defined.
export function frameAt(t: number): Frame {
  'worklet';
  const phase = ((t % LOOP) + LOOP) % LOOP;
  if (phase < RETURN_AT) return forward(phase);
  return back(phase);
}

/** The resting frames, for Reduced Motion. */
export const REST_T = 0;
export const FUNDED_T = FUNDED_AT;
