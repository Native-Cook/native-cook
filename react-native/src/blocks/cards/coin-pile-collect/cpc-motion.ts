// Coin Pile Collect — every motion number of the block, and the pure functions of time built on them.
//
// The pile is a simulation, not a keyframe track: it runs at a fixed step (DT) from the resting heap, so the loop is
// a pure function of loop time by replay (the same steps in the same order always give the same coins). The script
// below is what an invisible thumb does during autoplay; a real touch feeds the same inputs to the same solver.
//
// Loop (LOOP = 8.0 s):
//   0.00 – 0.50  rest, full tile: the exchange coin turns slowly
//   0.50 – 1.55  thumb down on Collect: the valve opens (250 ms), the river forms, nearest coins light cyan (gasp 1.5)
//   1.55 – 1.75  thumb lifts: the valve closes (120 ms), the heap settles
//   1.75 – 2.80  thumb down again and slid away from the pill: the throttle closes to about 3 coins a second
//   2.80 – ~3.5  thumb back on the pill: the rest pours; the last coin stands on its rim, then drops; $0.00
//   ~3.5 – ~4.2  empty beat: pill dims, the exchange coin takes a kick and lands on "$"
//   ~4.2 – ~6.5  rain: every coin falls from above in stacking order, the amount climbs, the pill brightens
//   ~6.5 – 8.00  settle, rest (the last two coins rock and stop)

export const LOOP = 8.0;
export const DT = 1 / 120; // fixed simulation step, s

/** the valve: coins per second at the fully open valve, and its ramps */
export const VALVE = {
  rate: 20,
  attack: 0.25,
  release: 0.12,
  throttleMin: 0.15, // share of the rate left when the thumb is 120 pt or more from the pill
  throttleSlide: 120,
  throttleLag: 0.08,
};

/** what the thumb does during autoplay (t in loop seconds) */
export const SCRIPT = {
  down1: 0.5,
  up1: 1.55,
  down2: 1.75,
  slideOn: [1.75, 1.95] as const,
  slideOff: [2.45, 2.8] as const,
  slideMax: 100, // pt from the pill's centre
  end: 3.9, // thumb lifts (the tile has emptied by then)
};

export const EMPTY = { rainDelay: 0.6, pillAlpha: 0.55 };
export const RAIN = {
  stagger: 0.062, // between two coins leaving the top edge, s
  gravity: 2400, // pt/s² of the fall
  spawnY: 46, // pt above the tile's top edge the lowest coin starts from
  spawnSpread: 80,
  glide: 0.7, // a coin returning to its place after a touch: s
  glideStagger: 0.02,
  bounce: 0.34, // landing bounce length, s
  bounceZ: 9, // pt of rebound height
};
export const RESTORE_IDLE = 2.0; // s after the last touch before the tile eases back to rest and autoplay resumes

/** the exchange coin: a full turn every 4 s (two a loop, so the seam is exact), a kick of two extra turns on empty */
export const XCOIN = {
  turn: 4.0,
  kickDelay: 0.08,
  kickTurns: 2,
  kickLength: 0.6,
  restWindow: 0.7, // easing to the front face after a touch
  tilt: 0.16, // rad, a slight lean toward the viewer so the rim catches the light
};

/** the pill and the amount */
export const PILL = {
  pressScale: 0.97,
  bulge: 0.035, // scaleY per coin passing
  bulgeSpring: { response: 0.2, zeta: 0.5 },
  lineMax: 0.6,
  litTau: 0.14,
  pressTau: 0.045,
  alphaTau: 0.12,
};
export const ROLL = { tau: 0.04 }; // the amount's digits chase the coins' value with this time constant, s

/** gestures */
export const GESTURE = {
  tapMax: 0.18, // s: a press shorter than this latches the valve open
  fingerRadius: 22, // pt, the collider in the pile
  hitSlopPill: 8,
  coinTick: 3, // a light haptic every third coin through the mouth
  hapticGap: 0.12, // s
  impactMin: 200, // pt/s: contacts faster than this click
  clickGap: 0.12,
  tiltG: 0.25, // g at full tilt
};

/** named springs (DESIGN-SYSTEM §8) */
export const SPRING = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  press: { damping: 18, stiffness: 380, mass: 1 },
};

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}
export function smooth(a: number, b: number, v: number) {
  'worklet';
  const k = clamp01((v - a) / (b - a));
  return k * k * (3 - 2 * k);
}
export function easeOut(k: number) {
  'worklet';
  const u = 1 - clamp01(k);
  return 1 - u * u * u;
}
export function easeInOut(k: number) {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** 0 → 1 step response of a damped spring at time t (analytic, so a replay and a live run agree) */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

/** the thumb during autoplay at loop time t, into out[k] (pill held) and out[k + 1] (pt from the pill's centre) */
export function scriptAt(t: number, out: number[], k: number) {
  'worklet';
  const held = (t >= SCRIPT.down1 && t < SCRIPT.up1) || (t >= SCRIPT.down2 && t < SCRIPT.end);
  const on = smooth(SCRIPT.slideOn[0], SCRIPT.slideOn[1], t);
  const off = smooth(SCRIPT.slideOff[0], SCRIPT.slideOff[1], t);
  out[k] = held ? 1 : 0;
  out[k + 1] = SCRIPT.slideMax * on * (1 - off);
}

/** the exchange coin's turn (rad about its vertical axis; 0 = "$" faces the viewer) at free-running clock t */
export function xcoinAngle(t: number, kickAt: number) {
  'worklet';
  const w = (2 * Math.PI) / XCOIN.turn;
  let a = w * t;
  if (kickAt >= 0) {
    const k = (t - kickAt - XCOIN.kickDelay) / XCOIN.kickLength;
    if (k > 0) {
      // two extra turns with a soft landing and a 3 % overshoot that settles (the idle spin carries on after it)
      const e = k >= 1 ? 1 : easeInOut(k) * 1.0;
      const over = k >= 1 ? 0.03 * Math.exp(-(k - 1) * 9) * Math.sin((k - 1) * 22) : 0;
      a += 2 * Math.PI * XCOIN.kickTurns * (e + over);
    }
  }
  return a;
}

/** rotation of the exchange coin easing to the next front face: a cubic Hermite from (th0, speed w) to (th1, 0) */
export function xcoinEase(th0: number, th1: number, w: number, s: number) {
  'worklet';
  const u = clamp01(s);
  const T = XCOIN.restWindow;
  const h00 = 2 * u * u * u - 3 * u * u + 1;
  const h10 = u * u * u - 2 * u * u + u;
  const h01 = -2 * u * u * u + 3 * u * u;
  return h00 * th0 + h10 * T * w + h01 * th1;
}

/** the next multiple of a full turn at least half a window ahead of th0 at speed w */
export function xcoinTarget(th0: number, w: number) {
  'worklet';
  const ahead = th0 + 0.5 * w * XCOIN.restWindow;
  return 2 * Math.PI * Math.ceil(ahead / (2 * Math.PI));
}
