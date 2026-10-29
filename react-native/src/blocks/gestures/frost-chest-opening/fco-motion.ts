// Frost Chest Opening — the timeline, the touch driver and the per-frame values.
//
// One timeline time `t` (0 … LOOP) decides every phase; autoPlay advances it in real time, a touch
// drives the charge part of it, and idle motion (snow, rays, the chest's bob) runs on its own clock
// with periods that divide LOOP, so one period cut from a recording loops without a jump.

export const LOOP = 9.6;

// Times are the reference video's, shifted so its press lands at 0.62 s (reference − 5.15 s), and
// every value below was measured on it frame by frame (orb circle fits, liquid level, element
// offsets and fades); see the block's notes for the method.
export const T = {
  press: 0.62, // the finger lands on the pill
  pop: [0.645, 0.665] as const, // the glass orb snaps out of the bead to 80 % of its size...
  popPeak: 0.82, // ...swells on to 106.5 %...
  popSettle: 0.95, // ...and settles (radii fitted to the meniscus)
  meniscus: 0.66, // the first sliver of liquid
  fillKnee: 0.81, // 15 % full; from here it rises at a steady rate
  fillEnd: 2.26, // full
  drift: [0.88, 2.35] as const, // while it fills the orb grows 55 → 67 pt and drifts up-left
  hintOut: [0.62, 0.8] as const, // the hint gives way before the orb reaches it
  charge: [0.8, 1.9] as const, // the chest lights up from inside
  shaft: [1.98, 2.43] as const, // a diagonal light shaft sweeps the chest left to right
  headerOut: [2.35, 2.62] as const, // header and title fade while the orb flies
  pillOut: [2.3, 2.54] as const,
  flyStart: 2.38,
  impact: 2.64, // the orb is swallowed by the chest
  flashIn: [2.632, 2.74] as const, // a white burst grows out of the swallow point to the whole screen
  flashOut: [2.88, 3.02] as const, // held white longer than the recording (0.25 s), then clears
  swap: 2.715, // closed → open, hidden by the flash
  glareChest: [2.74, 2.86] as const, // the open chest's overexposure fades
  pale: [3.8, 4.2] as const, // the open chest pales a little as the light settles
  sparkle: [2.74, 3.4] as const, // stars and ice chips stream out of the chest
  frostIn: [3.02, 3.22] as const, // the rays give way to the frost backdrop
  card: 3.03, // the card appears in the blaze above the chest
  cardFull: 3.27, // it has grown to 108 %, rising from y 314 to its place
  cardShrink: [3.45, 3.64] as const, // and eases down to 100 %
  cardSharp: [3.26, 3.38] as const, // the motion blur clears
  panel: [3.98, 4.12] as const, // the panel's glass fades in...
  others: [4.08, 4.38] as const, // ...then "Other Rewards"
  tileStart: [4.13, 4.2, 4.28] as const, // each tile fades in (exponential, τ below)
  tileTau: [0.065, 0.06, 0.05] as const,
  claim: 4.245, // Claim rises from 60 pt below (τ 0.1 s); it shows pale from 4.17 s and saturates (τ 0.09 s)
  claimShow: [4.17, 4.27] as const,
  settled: 4.7, // everything has arrived: a touch-driven reveal waits here
  // The reveal's light (ours, on top of the recording): light leaks round the chest while the orb
  // flies, explodes at the swallow, pulls back, surges again as the card is born and settles.
  leak: [2.4, 2.632] as const,
  burstAt: 2.632,
  surge: [3.0, 3.1] as const, // the second surge: the card is born white-hot in it
  cardCool: [3.2, 3.45] as const, // the card cools from white to its colours
  motes: [2.74, 4.8] as const, // light dust rising in the beam
  claimPress: [7.7, 7.86] as const,
  exit: [7.86, 8.14] as const, // rewards and button leave (faster than they came), the card sinks
  sink: [7.92, 8.36] as const,
  flash2In: [8.24, 8.38] as const,
  flash2Out: [8.42, 8.86] as const,
  swapBack: 8.4,
  restIn: [8.46, 9.06] as const,
};

/** Orb geometry (pt): the bead it grows from, where it settles, where it drifts to, the flight end. */
export const ORB = {
  // Centred on the pill (x 201.5); it grows in place, the pill fading out under it.
  bead: { x: 201.5, y: 651, r: 6.5 },
  start: { x: 201.5, y: 648, r: 55 },
  end: { x: 201.5, y: 643, r: 66.8 },
  swallow: { x: 203, dy: 205, dr: 36.8, dur: 0.26 }, // flight: rise and shrink over dur (then a little more)
};

/** How fast the timeline runs backwards while an unfinished charge drains after a release. */
export const DRAIN = 4; // a released charge drains in about 0.4 s: the system's answer is fast
/** Seconds after the last touch before autoPlay takes over again. */
export const RESUME = 2;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export type Frame = {
  t: number;
  idle: number; // idle clock, 0 … LOOP
  orb: number; // orb visibility (0 = only the bead)
  orbX: number; // orb centre and radius, pt
  orbY: number;
  orbR: number;
  fill: number; // liquid level, 0 … 1
  charge: number; // how lit the closed chest is
  sky: number; // how lit the sky's rays are (keeps rising until the orb is swallowed)
  shaft: number; // light shaft progress, 0 … 1 (0 when idle)
  fly: number; // the orb's flight into the chest, 0 … 1
  header: number; // back button, balance and title
  pill: number; // "1X RARE DROP"
  hint: number; // "HOLD TO OPEN CHEST!"
  press: number; // the pill pushed in by the finger
  seam: number; // the closed chest burning white as the orb goes in
  flash: number;
  reveal: number; // 0 = the closed chest, 1 = the open chest
  frost: number; // 0 = the sky with rays behind, 1 = the frost backdrop
  glare: number; // the open chest overexposed by its own light, fading after the burst
  pale: number; // 0 = the open chest's burst colours, 1 = its paler settled colours
  mouth: number; // the white glow in the chest's mouth (in front of the lid)
  sparkle: number; // seconds since the sparkle burst began (−1 when idle)
  openDY: number; // the open chest's slow settle, pt
  cardX: number; // card centre, pt
  cardY: number;
  cardS: number; // card scale
  cardA: number;
  spin: number; // card's Y rotation in degrees (720 = front, settled)
  cardBlur: number; // pt
  sink: number; // card falling back into the chest
  panel: number;
  others: number;
  tiles: [number, number, number];
  claim: number; // alpha
  claimInk: number; // 0 = pale, 1 = its full blue
  claimY: number; // offset below its place, pt
  claimDown: number;
  // The light (see LIGHT_SKSL)
  lCore: number;
  lRays: number;
  lReach: number;
  lStreak: number;
  lRingR: number; // pt
  lRingA: number;
  lPillar: number;
  lX: number; // source, pt
  lY: number;
  lBelow: number;
  lBehind: number; // 1 while it leaks from behind the closed chest
  exposure: number; // scene exposure (1 = as is; above 1 blows the highlights out)
  vignette: number;
  dim: number; // 0 = as is, 1 = the scene sunk to a deep blue
  shakeX: number; // pt
  shakeY: number;
  punch: number; // scene scale about the chest
  cardHot: number; // 1 = the card is white-hot, 0 = its colours
  cardHalo: number;
  motes: number; // seconds since the light dust began (−1 when idle)
};

/** The card's resting centre (pt); kept here so the timeline does not import the theme. */
const L_CARD_Y = 226;
const L_CARD_X = 201.5;

/** The card's turn, measured: back until 3.13 s, front 3.23, back 3.42, front again by 3.8 (degrees). */
const SPIN_T = [3.03, 3.13, 3.17, 3.23, 3.31, 3.42, 3.565, 3.68, 3.8];
const SPIN_A = [180, 200, 270, 360, 450, 540, 630, 705, 720];

function spinAt(t: number) {
  'worklet';
  if (t <= SPIN_T[0]) return SPIN_A[0];
  for (let i = 1; i < SPIN_T.length; i++) {
    if (t < SPIN_T[i]) {
      const k = (t - SPIN_T[i - 1]) / (SPIN_T[i] - SPIN_T[i - 1]);
      // The last stretch eases into the front; the others are linear (the turn's own speed).
      const e = i === SPIN_T.length - 1 ? 1 - (1 - k) * (1 - k) : k;
      return mix(SPIN_A[i - 1], SPIN_A[i], e);
    }
  }
  return SPIN_A[SPIN_A.length - 1];
}

/** Liquid level: a sliver after the pop, 15 % at the knee, then a steady rise to full. */
function fillAt(t: number) {
  'worklet';
  if (t < T.meniscus) return 0;
  if (t < T.fillKnee) return mix(0.03, 0.15, seg(t, T.meniscus, T.fillKnee));
  return mix(0.15, 1, seg(t, T.fillKnee, T.fillEnd));
}

/** Every animated value at timeline time `t`, with `idle` the idle clock. */
export function frameAt(t: number, idle: number): Frame {
  'worklet';
  const charging = t >= T.press && t < T.swap;
  // Orb: pop (ease-out with a small overshoot that settles), slow growth and drift, then the flight.
  const popK = seg(t, T.pop[0], T.pop[1]);
  const pop = t < T.pop[1] ? 0.8 * easeOut(popK) : t < T.popPeak ? mix(0.8, 1.065, easeOut(seg(t, T.pop[1], T.popPeak))) : mix(1.065, 1, easeInOut(seg(t, T.popPeak, T.popSettle)));
  const d = seg(t, T.drift[0], T.drift[1]);
  let orbX = mix(ORB.start.x, ORB.end.x, d);
  let orbY = mix(ORB.start.y, ORB.end.y, d);
  let orbR = mix(ORB.start.r, ORB.end.r, d);
  if (t < T.popSettle) {
    const k = seg(t, T.pop[0], T.popSettle);
    orbX = mix(ORB.bead.x, ORB.start.x, k);
    orbY = mix(ORB.bead.y, ORB.start.y, k);
    orbR = mix(ORB.bead.r, ORB.start.r, pop);
  }
  const flyK = seg(t, T.flyStart, T.impact);
  if (t > T.flyStart) {
    // Measured path: slow off the pill, then ever faster up into the chest, shrinking all the way.
    const u = Math.min(t, T.impact) - T.flyStart;
    const k = u / ORB.swallow.dur;
    orbX = mix(ORB.end.x, ORB.swallow.x, Math.min(1, k));
    orbY = ORB.end.y - ORB.swallow.dy * (0.25 * k + 0.75 * Math.pow(k, 2.4));
    orbR = ORB.end.r - ORB.swallow.dr * (0.3 * k + 0.7 * Math.pow(k, 1.8));
  }
  const orb = charging && t < T.impact ? 1 : 0;
  const fill = charging ? fillAt(t) : 0;
  const fly = charging ? flyK : 0;
  const charge = t < T.swap ? easeInOut(seg(t, T.charge[0], T.charge[1])) : 0;
  const sky = t < T.swap ? easeInOut(seg(t, T.charge[0], T.impact)) : 0;
  const shaft = t > T.shaft[0] && t < T.shaft[1] ? seg(t, T.shaft[0], T.shaft[1]) : 0;
  const restBack = easeOut(seg(t, T.restIn[0], T.restIn[1]));
  const header = t < T.swapBack ? 1 - seg(t, T.headerOut[0], T.headerOut[1]) : restBack;
  // The orb takes the pill's place: the pill fades out as the orb pops, and comes back after Claim.
  const pill = t < T.swapBack ? 1 - easeOut(seg(t, T.press, T.popSettle)) : restBack;
  const hint = t < T.swapBack ? 1 - easeOut(seg(t, T.hintOut[0], T.hintOut[1])) : restBack;
  const press = t < T.press ? 0 : t < T.fillEnd ? easeOut(seg(t, T.press, T.press + 0.12)) : 1 - seg(t, T.fillEnd, T.fillEnd + 0.2);
  const seam = t < T.swap ? seg(t, T.impact - 0.04, T.swap) : 0;
  // The burst is opaque from its start; it covers the screen by growing (see drawFlash).
  const flash1 = t < T.flashOut[0] ? seg(t, T.flashIn[0], T.flashIn[0] + 0.014) : 1 - easeOut(seg(t, T.flashOut[0], T.flashOut[1]));
  const flash2 = t < T.flash2Out[0] ? easeIn(seg(t, T.flash2In[0], T.flash2In[1])) : 1 - easeOut(seg(t, T.flash2Out[0], T.flash2Out[1]));
  const flash = t < 6 ? flash1 : flash2 * 0.92;
  const reveal = t >= T.swap && t < T.swapBack ? 1 : 0;
  const frost = t < T.swapBack ? easeInOut(seg(t, T.frostIn[0], T.frostIn[1])) : 0;
  const glare = reveal * (1 - seg(t, T.glareChest[0], T.glareChest[1]));
  const pale = easeInOut(seg(t, T.pale[0], T.pale[1]));
  const mouth = reveal * mix(1, 0.55, seg(t, 3.0, 3.85));
  const sparkle = t >= T.sparkle[0] && t < T.sparkle[1] ? t - T.sparkle[0] : -1;
  const openDY = reveal * (5 * (1 - seg(t, 3.0, 5.5)) + 3 * (1 - easeOut(seg(t, T.swap, 2.85))));
  // Card (measured): appears small in the blaze at y 314, rises and grows to 108 % by 3.27 s while
  // it turns, then eases down to its size; blurred until 3.26 s, sharp by 3.38 s.
  const sink = easeIn(seg(t, T.sink[0], T.sink[1]));
  const cu = Math.max(0, t - T.card) / 0.17;
  const cardY = L_CARD_Y + 88 * Math.exp(-cu * cu);
  const cardX = L_CARD_X - 3 * (1 - seg(t, 3.4, 3.7));
  const sk = seg(t, T.card, T.cardFull);
  const cardS = t < T.cardShrink[0] ? 0.1 + 0.98 * sk * sk * (3 - 2 * sk) : mix(1.08, 1, easeInOut(seg(t, T.cardShrink[0], T.cardShrink[1])));
  const cardA = reveal * seg(t, T.card, T.card + 0.07);
  const spin = spinAt(t);
  const cardBlur = 4.5 * (1 - seg(t, T.cardSharp[0], T.cardSharp[1])) * (t >= T.card ? 1 : 0);
  const exit = easeOut(seg(t, T.exit[0], T.exit[1]));
  const panel = seg(t, T.panel[0], T.panel[1]) * (1 - exit);
  const others = seg(t, T.others[0], T.others[1]) * (1 - exit);
  const tile = (k: number) => (t < T.tileStart[k] ? 0 : 1 - Math.exp(-(t - T.tileStart[k]) / T.tileTau[k])) * (1 - exit);
  const ct = Math.max(0, t - T.claim);
  const claimY = (t < T.claim ? 60 : 60 * Math.exp(-ct / 0.1)) + 40 * exit;
  const claim = seg(t, T.claimShow[0], T.claimShow[1]) * (1 - exit);
  const claimInk = t < T.claim ? 0 : 1 - Math.exp(-ct / 0.09);
  const cd = seg(t, T.claimPress[0], T.claimPress[1]);
  const claimDown = cd <= 0 || cd >= 1 ? 0 : Math.sin(cd * Math.PI);
  // ── The light ──
  const bt = t - T.burstAt;
  const lit = t >= T.burstAt && t < T.swapBack;
  const leak = t < T.burstAt && t >= T.leak[0] ? easeIn(seg(t, T.leak[0], T.leak[1])) : 0;
  const sinkLight = 0.8 * Math.sin(Math.PI * seg(t, T.sink[0], T.sink[1] + 0.05));
  let lCore = 0.18 * leak;
  if (lit) {
    if (bt < 0.03) lCore = mix(0.2, 3.2, bt / 0.03);
    else if (t < 2.9) lCore = mix(3.2, 2.6, seg(t, T.burstAt + 0.03, 2.9));
    else if (t < T.surge[0]) lCore = mix(2.6, 1.3, seg(t, 2.9, T.surge[0]));
    else if (t < T.surge[1]) lCore = mix(1.3, 2.6, easeOut(seg(t, T.surge[0], T.surge[1])));
    else if (t < 3.45) {
      // The light withdraws into the card, which shows through it from about 3.3 s.
      const k = seg(t, T.surge[1] + 0.08, 3.45);
      lCore = mix(2.6, 0.35, 1 - (1 - k) * (1 - k));
    } else lCore = mix(0.35, 0.1, seg(t, 3.4, 4.4));
    lCore = lCore * (1 - seg(t, T.sink[0], T.sink[1])) + sinkLight;
  }
  const lRays = lit ? (bt < 0.6 ? 1.1 : mix(1.05, 0.18, seg(t, 3.3, 3.95))) * (1 - 0.8 * seg(t, T.sink[0], T.sink[1])) : 0.7 * leak;
  const lReach = lit ? 0.2 + 0.9 * easeOut(seg(t, T.burstAt, 2.95)) : 0.18 + 0.1 * leak;
  let lStreak = 0.15 * leak;
  if (lit) {
    if (bt < 0.04) lStreak = mix(0.2, 1.2, bt / 0.04);
    else if (t < T.surge[0]) lStreak = mix(1.2, 0.25, easeOut(seg(t, T.burstAt + 0.04, T.surge[0])));
    else if (t < T.surge[1]) lStreak = mix(0.25, 0.8, seg(t, T.surge[0], T.surge[1]));
    else lStreak = mix(0.8, 0, easeOut(seg(t, T.surge[1], 3.6)));
  }
  let lRingR = 0;
  let lRingA = 0;
  if (lit && t < 3.05) {
    lRingR = 740 * easeOut(seg(t, T.burstAt, 3.05));
    lRingA = 0.9 * (1 - seg(t, T.burstAt, 3.05));
  } else if (t >= T.surge[0] + 0.04 && t < 3.45) {
    // A smaller pulse ring from the card's birth.
    lRingR = 420 * easeOut(seg(t, T.surge[0] + 0.04, 3.45));
    lRingA = 0.45 * (1 - seg(t, T.surge[0] + 0.04, 3.45));
  }
  // The pillar out of the mouth gives way once the card is born: from then on the light sits behind it.
  const onCard = seg(t, T.surge[0], T.surge[0] + 0.08) * (1 - seg(t, 3.5, 4.0));
  const lPillar = lit ? seg(t, 2.85, 2.98) * (1 - onCard) * (1 - seg(t, 3.3, 3.6)) : 0;
  const toMouth = easeInOut(seg(t, T.burstAt, 2.8));
  const lX = lit ? mix(mix(200, 198.5, toMouth), cardX, onCard) : 200;
  const lY = lit ? mix(mix(430, 400, toMouth), cardY, onCard) : 425;
  const lBelow = t < 2.88 ? 1 : mix(1, 0.1, seg(t, 2.88, 3.02));
  const lBehind = lit ? 0 : 1;
  const hump1 = seg(t, T.burstAt, 2.68) * (1 - seg(t, 2.9, 3.05));
  const hump2 = Math.sin(Math.PI * seg(t, T.surge[0], 3.4));
  const exposure = 1 + (lit ? 0.6 * hump1 + 0.35 * hump2 : 0);
  // The world dims to a deep blue as the orb flies in (the inhale before the burst) and stays dim
  // while the light pours out, so the light reads against it; the lights come up as the card lands.
  const dimIn = easeInOut(seg(t, 2.3, 2.6));
  const dimOut = easeInOut(seg(t, 3.25, 3.8));
  const dim = t < T.swapBack ? 0.72 * dimIn * (1 - dimOut) : 0;
  const vignette = 0.38 * dimIn * (1 - dimOut) * (t < T.swapBack ? 1 : 0);
  let shakeX = 0;
  let shakeY = 0;
  if (bt >= 0 && bt < 0.35) {
    const amp = 5 * Math.exp(-bt / 0.07);
    shakeX = amp * Math.sin(bt * 95);
    shakeY = amp * Math.cos(bt * 78 + 0.7);
  } else if (t >= T.surge[0] + 0.02 && t < T.surge[0] + 0.25) {
    const amp = 2 * Math.exp(-(t - T.surge[0] - 0.02) / 0.06);
    shakeX = amp * Math.sin(t * 90);
    shakeY = amp * Math.cos(t * 70);
  }
  const pushIn = t < T.burstAt ? 0.012 * easeIn(seg(t, 1.9, T.burstAt)) : 0.012 * (1 - easeOut(seg(t, T.burstAt, T.burstAt + 0.5)));
  const punch = 1 + pushIn + (bt >= 0 && t < T.swapBack ? 0.03 * Math.exp(-bt / 0.09) : 0);
  const cardHot = t < T.cardCool[0] ? 1 : 1 - easeOut(seg(t, T.cardCool[0], T.cardCool[1]));
  const cardHalo = seg(t, T.card, T.card + 0.07) * (1 - seg(t, 3.35, 3.9));
  const motes = t >= T.motes[0] && t < T.motes[1] ? t - T.motes[0] : -1;

  return {
    lCore, lRays, lReach, lStreak, lRingR, lRingA, lPillar, lX, lY, lBelow, lBehind, exposure, vignette, dim, shakeX, shakeY, punch,
    cardHot, cardHalo, motes,
    t, idle, orb, orbX, orbY, orbR, fill, charge, sky, shaft, fly, header, pill, hint, press, seam, flash, reveal, frost,
    glare, pale, mouth, sparkle, openDY, cardX, cardY, cardS, cardA, spin, cardBlur, sink, panel, others, tiles: [tile(0), tile(1), tile(2)],
    claim, claimInk, claimY, claimDown,
  };
}

// ── Touch driver ────────────────────────────────────────────────────────────────────────────────
// mode 0: autoPlay, 1: finger down on the pill (charging), 2: draining after an early release,
// 3: playing the reveal forward to `stop`, 4: waiting (idle) until RESUME after the last touch.

export type Driver = { mode: number; t: number; stop: number; last: number };

export function makeDriver(): Driver {
  return { mode: 0, t: 0, stop: LOOP, last: -100 };
}

/** Advance the driver by `dt` seconds at clock `now`; returns the timeline time. */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean): number {
  'worklet';
  const step = Math.min(dt, 0.05);
  if (d.mode === 0) {
    d.t = auto ? (d.t + step) % LOOP : 0;
  } else if (d.mode === 1) {
    d.t = Math.max(d.t, T.press) + step;
    if (d.t >= T.fillEnd + 0.08) {
      d.mode = 3;
      d.stop = T.settled;
    }
  } else if (d.mode === 2) {
    d.t -= step * DRAIN;
    if (d.t <= T.press - 0.01) {
      d.t = 0;
      d.mode = 4;
      d.last = now;
    }
  } else if (d.mode === 3) {
    const next = d.t + step;
    if (d.stop >= LOOP && next >= LOOP) {
      d.t = 0;
      d.mode = 4;
      d.last = now;
    } else if (d.stop < LOOP && next >= d.stop) {
      d.t = d.stop;
      d.mode = 4;
      d.last = now;
    } else d.t = next;
  } else if (d.mode === 4) {
    if (auto && now - d.last > RESUME) d.mode = 0;
  }
  return d.t;
}

/** Where a touch lands: 'pill' on the hold button at rest, 'claim' on the claim button, else none. */
export function holdBegin(d: Driver, now: number) {
  'worklet';
  // Only at rest or while the charge drains: the reveal plays out once it started.
  if (d.t < T.flyStart && (d.mode === 0 || d.mode === 2 || d.mode === 4)) {
    if (d.t > T.fillEnd) return false;
    if (d.mode === 0 && d.t > T.press) {
      // autoPlay was mid-charge: keep its level, the finger takes over.
    } else if (d.t < T.press) d.t = T.press - 0.02;
    d.mode = 1;
    d.last = now;
    return true;
  }
  return false;
}

export function holdEnd(d: Driver, now: number) {
  'worklet';
  if (d.mode === 1) {
    d.mode = 2;
    d.last = now;
  }
}

export function canClaim(d: Driver) {
  'worklet';
  return d.t >= T.claim + 0.25 && d.t < T.claimPress[0];
}

export function claimTap(d: Driver, now: number) {
  'worklet';
  if (!canClaim(d)) return false;
  d.t = T.claimPress[0];
  d.mode = 3;
  d.stop = LOOP;
  d.last = now;
  return true;
}

export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 0) {
    // A touch anywhere while the reveal autoplays freezes it where it is.
    if (d.t >= T.flyStart && d.t < T.settled) {
      d.mode = 3;
      d.stop = T.settled;
    } else if (d.t >= T.settled && d.t < T.claimPress[0]) {
      d.mode = 4;
    }
  }
}

// ── Deterministic randomness ────────────────────────────────────────────────────────────────────

export function rand(i: number, salt: number) {
  'worklet';
  const x = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
}
