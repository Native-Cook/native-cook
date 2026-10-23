/**
 * Every number the motion uses. Springs are (duration ms, damping ratio), stepped by the model each frame so the
 * picture, the haptics and VoiceOver read the same values. Ease-out enters, ease-in-out moves across; nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export type Spring = { duration: number; dampingRatio: number };

export const SPRINGS = {
  /** The handle settling into a detent after its pawl clicks: a stiff, slightly underdamped metal part. */
  detent: { duration: 180, dampingRatio: 0.45 },
  /** Gumballs pulled back to their place in the pile. */
  pile: { duration: 520, dampingRatio: 0.55 },
  /** The prize card rising out of the capsule: a light object settling, no bounce past its place. */
  card: { duration: 480, dampingRatio: 0.82 },
  /** The wallet pill catching the coins. */
  wallet: { duration: 260, dampingRatio: 0.5 },
  reduced: { duration: 200, dampingRatio: 1 },
} as const;

export const CRANK = {
  /** A full turn in four detents; the pawl stops the handle going back. */
  detents: 4,
  /** How far the handle strains backwards against the pawl (rad). */
  back: 0.22,
  /** Touches within this of the plate's centre turn the handle (pt). */
  grab: 70,
  /** Speed each gumball is kicked with when a detent clicks (pt/s), more for the ones low in the globe. */
  kick: 220,
  gravity: 900,
};

/** After the turn: seconds from the last detent. */
export const DISPENSE = {
  drop: 0.25, // the capsule falls inside the body to the chute
  flap: [0.22, 0.5] as const, // the chute's flap swings open
  roll: [0.3, 0.95] as const, // it rolls out of the door and toward you, onto the table
  wobble: [0.95, 1.3] as const,
  split: [1.32, 1.7] as const, // the halves part
  card: 1.45, // the card starts rising out of it
  ready: 1.95,
};

/** Collect: seconds from the press. */
export const COLLECT = {
  coins: 8,
  stagger: 0.05,
  fly: 0.5,
  card: [0.35, 0.75] as const, // the card drops and fades after its coins leave
  capsule: [0.45, 0.85] as const, // the halves fade off the table
  done: 0.95,
};

export const TIMING = {
  fingerIn: 0.15,
  fingerOut: 0.12,
  press: 0.12, // Collect pressed
  scrimIn: 0.3,
  scrimOut: 0.25,
  walletBack: 0.5, // autoplay only: the wallet returns to its starting balance before the loop ends
};

export const HAPTIC = { tick: 1, drop: 2, open: 3, collect: 4, strain: 5 } as const;

/** Autoplay, seconds from rest; the screen is back at rest before the loop ends. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  down: 0.5,
  turn: [0.7, 2.5] as const, // one circular drag of the handle, a quarter turn at a time
  up: 2.65,
  tap: 5.7, // Collect
  walletBack: 9.0,
};
