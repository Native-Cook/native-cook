import type { SharedValue } from 'react-native-reanimated';

/**
 * Everything the drawing needs, published once per frame by the simulation (`use-supercharge.ts`).
 * All positions are in design points (see `supercharge-layout.ts`). The scene never animates
 * anything by itself except with `clock`; every level below is the simulation's.
 */
export type SuperchargeValues = {
  /** Seconds since mount, for shimmer, the meniscus and drifting motes. */
  clock: SharedValue<number>;
  /** [dx, dy, rotation (rad), scale] of the whole object about PIVOT: shake, press, kick. */
  card: SharedValue<number[]>;
  /** How far the glass and the bolt glyph are pushed down into the sleeve, in points (0 to 8). */
  sink: SharedValue<number>;
  /** 0..1: how high the gold light stands in the glass, from the sleeve line (0) to its top edge (1). */
  energy: SharedValue<number>;
  /** 0..1: the charged look. 0 is the dark resting glass, 1 the reference still (glass full of yellow light, warm air around it). */
  glow: SharedValue<number>;
  /** Emission of the bolt glyph: 0.12 resting ember, 1 the reference still, up to 1.8 when struck. Flicker is already in it. */
  glyph: SharedValue<number>;
  /** Scale of the bolt glyph about BOLT_CENTRE (1 at rest, pops on the strike). */
  glyphScale: SharedValue<number>;
  /** 0..1: the ring as a gauge: an arc swept clockwise from 12 o'clock. */
  ring: SharedValue<number>;
  /** 0..1.6: the whole ring lit (1 is the reference still). */
  ringLit: SharedValue<number>;
  /**
   * 1 normally. Multiplies every light in the scene (glass, glyph, ring, air, what they throw on the
   * paper). The breath before the strike pulls it to 0.35 and the return stroke snaps it back.
   */
  dim: SharedValue<number>;
  /** 0..1: white over everything. One impact frame, then gone. */
  flash: SharedValue<number>;
  /** 0..1: how far the discharge has grown through the glass (compared with the arrival tile). */
  tree: SharedValue<number>;
  /** 0..1.6: brightness of the grown figure (1.6 as it forms, about 0.3 once it has cooled). */
  treeGlow: SharedValue<number>;
  /** -1 when off, else 0..1: a pulse of light travelling out along the figure. */
  pulse: SharedValue<number>;
  /** 0 when off, else 0..1: a shock ring spreading from the ring light. */
  shock: SharedValue<number>;
  /** [x, intensity 0..1.5]: light from the sky channel falling on the air, the glass edge and the paper. */
  shaft: SharedValue<number[]>;
  /** [x, y, strength 0..1]: the finger on the glass. */
  touch: SharedValue<number[]>;
  /** 0..1: the percentage the readout shows, and how many dots are lit. */
  meter: SharedValue<number>;
  /** 0..1: the "d" of "Supercharged" and the charged body copy. */
  charged: SharedValue<number>;
  /**
   * Which caption shows: 0 "hold to charge", 1 "release", 2 "tap to drain", -1 none.
   * Between two values the old one leaves before the new one arrives; they are never both visible.
   */
  caption: SharedValue<number>;
  /** Lightning to draw this frame. See `LightningFrame`. */
  lightning: SharedValue<LightningFrame>;
  /** Sparks to draw this frame: flat [x, y, vx, vy, life 0..1] × n. */
  sparks: SharedValue<number[]>;
};

/**
 * Polylines for this frame, already cut to what is visible. Three weights, heaviest first.
 * Each is a flat list of segments [x0, y0, x1, y1, ...] in design points.
 * `level` multiplies the brightness of all of it (the strike's flicker); 0 draws nothing.
 */
export type LightningFrame = {
  main: number[];
  branch: number[];
  twig: number[];
  level: number;
  /** Absolute brightness for branch/twig lists; the first stroke keeps forks lit through 160 ms. */
  forkLevel?: number;
  /** Ground trunk in branch weight, following level rather than forkLevel; absent during the leader. */
  ground?: number[];
  /** Widen the sky core to 2.8 pt (22 / 8 / 2.8 passes) during the first return pulse. */
  firstPulse?: boolean;
};

export type SuperchargePhase =
  | 'rest'
  | 'charging'
  | 'draining'
  | 'primed'
  | 'strike'
  | 'charged'
  | 'discharging';
