// Every motion value of the block. Seconds unless the name ends in _MS; points and radians unless noted.

export const STEP = 1 / 120;
export const MAX_STEPS = 30; // per frame: the sim keeps wall-clock time through dropped frames, so the loop stays 12 s

export const PILE = {
  settleSteps: 900, // the pile is dropped and settled once, before the first frame; that pose is home
  settleGravity: 1400,
  home: 40, // spring back to the slot
  damping: 5, // per second
  passes: 3,
  kick: 260, // speed given to the lower balls at each detent
  tremble: 0.35, // share of the kick for the upper balls
};

export const CRANK = {
  grab: 70, // touches within this of the knob turn it
  detent: Math.PI / 2,
  turn: Math.PI * 2,
  back: 0.1, // ratchet: rubber limit backwards (6°)
  overshoot: { stiffness: 300, damping: 14, kick: 1.6 }, // knob wobble after each click (radians per second)
  press: 1.06,
};

// Capsule timeline after the turn completes.
export const DISPENSE = {
  drop: 0.34,
  bounce: 0.18,
  rollStart: 0.56,
  roll: 0.5,
  wobbleStart: 1.1,
  wobble: 0.3,
  split: 1.4,
  splitLength: 0.5,
  cardRise: 0.45,
  done: 1.9,
};

export const COLLECT = { duration: 0.6, bagBounce: { stiffness: 380, damping: 12, kick: -3 } };

export const HAPTIC = { grab: 1, detent: 2, ratchet: 3, dispense: 4, open: 5, collect: 6 } as const;

export const REDUCED = { fade: 0.2 };

// Autoplay: 12 s.
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const SCRIPT = {
  turn: [1.0, 2.7] as const, // one circular drag of 370° around the knob
  turnAngle: Math.PI * 2 + 0.18,
  turnRadius: 52,
  collectAt: 7.8,
};

// Replica loop (the source ad, 10 s): rest, a half turn of the knob with a jostle, the pot comes out of the door and
// grows toward the viewer, holds, and goes back in.
export const REPLICA_LOOP_MS = 10_000;
export const REPLICA_T = { turn: [2.8, 3.6] as const, kick: 3.0, out: [3.8, 5.0] as const, back: [9.0, 9.6] as const };
// The source's camera pushes in while the pot comes out.
export const REPLICA_CAMERA = {
  // [scale, tx, ty] in screen px, fitted with ECC on the machine's base and kept at the knob (similarity).
  a: [1.0946, -15.3, 13.9],
  b: [1.117, -49.3, -18.1],
  t: [3.8, 4.3, 5.5] as const, // push in to a by 4.3 s, on to b by 5.5 s; back out with the pot
};
export const REPLICA_HOLD = -1; // QA only: pins the clock (seconds) for reference screenshots

// Upgrade (built on the accepted replica).
export const KNOB = {
  grab: 80, // touches within this of the knob's centre turn it
  friction: 2.2, // per second: a released knob coasts with its angular velocity
  catchSpin: 0.8, // below this (rad/s) the ratchet's pawl catches and the knob stops
  commitSpin: 3, // released past half a turn faster than this: the knob spins on to finish (velocity OR distance)
  finish: 0.35, // s to finish such a turn
  maxSpin: 9, // rad/s handed over on release
  press: 1.06,
  backMax: 0.3, // backwards strain (rad)
};
export const UP_T = {
  squash: 0.18, // machine anticipation
  out: [0.18, 1.18] as const, // tub out of the door (easeOutBack)
  confetti: 0.35,
  morph: [1.7, 2.25] as const, // label panel → reward card
  done: 2.4,
  collect: 0.8, // coins fly from the coupon into the pill (8 coins, 40 ms stagger, 0.45 s each)
  coinFly: 0.45,
  coinStagger: 0.04,
  coins: 8,
  couponOut: [0.45, 0.8] as const, // the coupon drops and fades after its coins have left
  back: 0.5, // tub back into the door
  sweep: 0.9, // glass specular sweep
  idleSweep: 4, // s between idle glints
};
export const CONFETTI = { count: 24, speed: 560, spread: 1.2, gravity: 1100, life: 1.6, size: [8, 16] as const, spin: 7 };
// The pill rests at 1,290; under the scrim it quietly drops to 1,240 and the collect counts it back up, so the
// loop's first and last frames match.
export const COINS = { from: 1290, add: 50, countSteps: 5, countStep: 70 };
export const UP_LOOP_MS = 12_000;
export const UP_SCRIPT = { turn: [1.0, 2.5] as const, angle: Math.PI * 2 + 0.2, radius: 48, collectAt: 7.8 };
export const QA_SPEED = 1; // QA only: slows the simulation for slow-motion captures
// Parallax against the camera fit, in screen px at pose a and pose b (measured per element on the reference
// frames): the globe sits behind the machine's front, the table's fruit in front of it. c, r: the patch (px).
export const REPLICA_PARALLAX = {
  globe: { a: [-9, 15] as const, b: [-7, -8] as const },
  fruitR: { a: [-24, -43] as const, b: [-8, -32] as const, c: [1110, 2060] as const, r: 420, blur: 3.5 },
  fruitL: { a: [0, -20] as const, b: [0, -6] as const, c: [100, 2200] as const, r: 300, blur: 2 },
  // blur (px): the table's fruit is softer than the plate shows it.
};
