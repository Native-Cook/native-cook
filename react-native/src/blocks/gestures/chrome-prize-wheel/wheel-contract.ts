// Chrome Prize Wheel — the contract between the assets, the wheel shader, the simulation and the screen.
//
// Angles are radians, measured CLOCKWISE on screen from the +x axis (screen y points down), so the
// angle of a screen offset (x, y) from the wheel's centre is atan2(y, x). 0 is 3 o'clock, PI/2 is
// 6 o'clock, PI is 9 o'clock. The wheel's rotation is `theta`: a point fixed to the wheel at
// wheel-angle `a` shows at screen angle `a + theta`. Positive theta and omega turn the wheel clockwise.
//
// Lengths in this file are in wheel radii unless a name ends in `Pt` (points at 402 x 874) or `Px`.
import type { SharedValue } from 'react-native-reanimated';

// ---------------------------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------------------------

/** The design screen. Everything scales by k = min(width / 402, height / 874). */
export const DESIGN = { width: 402, height: 874, safeTop: 62, safeBottom: 34 } as const;

/** Outer radius of the wheel (the edge of its dark outer band) at k = 1, and its centre. */
export const WHEEL_RADIUS_PT = 164;
export const WHEEL_CENTRE_PT = { x: 201, y: 424 } as const;

// ---------------------------------------------------------------------------------------------
// The wheel's profile (radii, in wheel radii). The viewer looks straight down the axle.
// ---------------------------------------------------------------------------------------------

export const PROFILE = {
  /** 1.000 → 0.955: dark blue-chrome outer band, bevelled down toward the outside. */
  bandInner: 0.955,
  /** 0.955 → 0.855: the bright chrome ring, a half-round (torus) section. The pegs stand on its crest. */
  ringInner: 0.855,
  /** 0.855 → 0.825: a shadowed wall stepping down to the recessed face. */
  faceOuter: 0.825,
  /** Wedges span radius wedgeInner → faceOuter, with rounded corners. */
  wedgeInner: 0.3,
  /** Width of the dark channel between two wedges (so each wedge is inset by half of it). */
  channel: 0.036,
  /** Corner radius of a wedge. */
  wedgeCorner: 0.045,
  /** The hub: a chrome bevel ring from hubDisc → hub, and a brushed chrome disc inside hubDisc. */
  hub: 0.215,
  hubDisc: 0.168,
} as const;

// ---------------------------------------------------------------------------------------------
// Segments and prizes
// ---------------------------------------------------------------------------------------------

export const SEGMENTS = 6;
export const SEGMENT_ANGLE = (Math.PI * 2) / SEGMENTS;

/**
 * Wedge i is centred on wheel-angle i * 60deg and spans +-30deg. The dividers are at 30deg + i * 60deg.
 * At theta = 0 the wheel shows exactly as in the reference still: the box (10) under the pointer.
 */
export const PRIZES = [
  { key: 'plate', count: 5, label: 'A plate of donuts' }, //   0deg — 3 o'clock
  { key: 'chest', count: 50, label: 'The treasure chest' }, //  60deg — 5 o'clock (the jackpot)
  { key: 'tray', count: 15, label: "A baker's tray" }, //      120deg — 7 o'clock
  { key: 'box', count: 10, label: 'A box of ten' }, //         180deg — 9 o'clock, under the pointer
  { key: 'coffee', count: 1, label: 'A coffee on us' }, //     240deg — 11 o'clock
  { key: 'stack', count: 3, label: 'A triple stack' }, //      300deg — 1 o'clock
] as const;

export const JACKPOT_INDEX = 1;

/** Where a prize sits in its wedge (radius of the sprite's centre) and the side of its square tile. */
export const PRIZE_RADIUS = 0.575;
export const PRIZE_TILE = 0.46;

/**
 * Prizes ride the wheel like gondolas on a Ferris wheel: they stay upright on screen and rock when the
 * wheel jolts. Two damped oscillators (`swayA`, `swayB`, radians) are mixed per prize with these weights:
 * tilt_i = swayA * PRIZE_SWAY[i][0] + swayB * PRIZE_SWAY[i][1]. Positive tilt is clockwise.
 */
export const PRIZE_SWAY = [
  [1.0, 0.25],
  [0.7, -0.6],
  [0.85, 0.5],
  [1.05, -0.2],
  [0.6, 0.75],
  [0.9, -0.45],
] as const;

// ---------------------------------------------------------------------------------------------
// Pegs and pointer (the physical parts; the drawing must match these numbers exactly)
// ---------------------------------------------------------------------------------------------

/** 18 chrome pegs on the crest of the ring, at wheel-angles 10deg + n * 20deg (every third one on a divider). */
export const PEGS = {
  count: 18,
  phase: Math.PI / 18,
  spacing: Math.PI / 9,
  radius: 0.905,
  size: 0.026,
} as const;

/**
 * The pointer is a rigid flapper: a gem-tipped isosceles triangle hinged on a stud outside the rim at
 * 9 o'clock, its tip pointing at the wheel's centre. Its `swing` is in radians, 0 at rest, positive
 * clockwise about the pivot, which moves the tip DOWN the screen. A wheel turning clockwise (omega > 0)
 * carries its pegs UP past the pointer, so it kicks the tip up: negative swing.
 *
 * Collision shape: the triangle with its base centred on the pivot (half-width `halfBase`, perpendicular
 * to the axis) and its apex `length` away along the axis. The pegs are circles of radius PEGS.size.
 */
export const POINTER = {
  /** Screen angle of the pointer (9 o'clock). */
  angle: Math.PI,
  /** Distance of the pivot from the wheel's centre. The pivot is at centre + (-pivot, 0). */
  pivot: 1.12,
  /** Pivot → tip. The tip rests at radius pivot - length = 0.87, inside the peg circle's reach. */
  length: 0.25,
  halfBase: 0.095,
} as const;

/** The wheel-angle under the pointer, and the wedge it lies in. */
export function wedgeUnderPointer(theta: number): number {
  'worklet';
  const turn = Math.PI * 2;
  let a = (POINTER.angle - theta) % turn;
  if (a < 0) a += turn;
  return Math.floor((a + SEGMENT_ANGLE / 2) / SEGMENT_ANGLE) % SEGMENTS;
}

// ---------------------------------------------------------------------------------------------
// Assets (all in ./assets). Sizes in pixels.
// ---------------------------------------------------------------------------------------------

export const ASSETS = {
  /**
   * wheel-env.png — 1024 x 512, colour, opaque. Equirectangular environment the chrome mirrors.
   * A direction d = (x right, y up, z toward the viewer) maps to
   *   u = 0.5 + atan2(d.x, d.z) / (2 PI),  v = 0.5 - asin(d.y) / PI.
   * So the picture's centre is what a mirror facing the viewer shows (the room behind the viewer),
   * its top is overhead and its left and right edges are behind the wheel.
   */
  env: { width: 1024, height: 512 },
  /** wheel-env-soft.png — 256 x 128: the same environment, blurred (sigma about 6 px at this size), for brushed and enamel surfaces. */
  envSoft: { width: 256, height: 128 },
  /**
   * wheel-prizes.png — 1152 x 768, colour with alpha. Six 384 px tiles, 3 columns x 2 rows, row-major in
   * PRIZES order. Each tile: the prize upright with its soft contact shadow, inside the central 80%,
   * and its count badge baked in, a disc centred on (0.74, 0.76) of the tile with diameter 0.27 of it.
   * At least 8 px of fully transparent margin on every side of every tile.
   */
  prizes: { width: 1152, height: 768, tile: 384, columns: 3, badge: { x: 0.74, y: 0.76, size: 0.27 } },
  /**
   * prize-<key>.png — 768 x 768 each, colour with alpha: the same six prizes, same framing as their tile
   * (so the tile is this picture at half size plus the badge), WITHOUT the badge and without the shadow.
   */
  hero: { size: 768 },
  /** wheel-badges.png — 768 x 128: the six badges alone (128 px tiles in PRIZES order), disc filling 92% of a tile. */
  badges: { width: 768, height: 128, tile: 128 },
  /**
   * wheel-pointer.png — 264 x 216 (88 x 72 pt at k = 1). The gem pointer drawn pointing RIGHT at swing 0.
   * The pivot is at pixel (60, 108) and the tip at (183, 108); the two long edges of the body run straight
   * from (60, 108 -+ 46.7) to the tip, which is the collision triangle above (164 pt * 3 px per radius).
   * wheel-pointer-shadow.png — same size and registration: its blurred shadow (black, alpha only).
   */
  pointer: { width: 264, height: 216, pivotX: 60, pivotY: 108, pxPerRadius: 492 },
  /**
   * wheel-particles.png — 768 x 192: four 192 px tiles in a row, colour with alpha.
   * 0 glossy magenta star, 1 glossy violet star, 2 white four-point sparkle, 3 soft round bokeh dot.
   */
  particles: { width: 768, height: 192, tile: 192 },
  /** wheel-backdrop.jpg — 804 x 1748: the night-sky page behind everything (no stars, no wheel). */
  backdrop: { width: 804, height: 1748 },
  /** wheel-swipe.png — 96 x 96 (32 pt): the chrome curved-arrow glyph of the hint pill. */
  swipe: { size: 96 },
} as const;

// ---------------------------------------------------------------------------------------------
// The wheel drawing (wheel-shader.ts + WheelFace.tsx) — what it is given
// ---------------------------------------------------------------------------------------------

export type WheelFaceProps = {
  /** Centre and outer radius of the wheel on the canvas, in points. */
  cx: number;
  cy: number;
  radius: number;
  /** Rotation, radians (unbounded; the drawing wraps it itself). */
  theta: SharedValue<number>;
  /** Angular velocity, rad/s. Drives the rotational motion blur of everything fixed to the wheel. */
  omega: SharedValue<number>;
  /**
   * Tilt of the wheel's plane, radians, small (|tilt| < 0.12). tiltX > 0 leans the right edge away from
   * the viewer; tiltY > 0 leans the bottom edge away. It moves every reflection and highlight.
   */
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  /** Turns the environment about the view axis, radians (a slow idle drift of the lights). */
  envTurn: SharedValue<number>;
  /** The two prize oscillators, radians (see PRIZE_SWAY). */
  swayA: SharedValue<number>;
  swayB: SharedValue<number>;
  /** The winning wedge, 0..5, or -1 when there is none. */
  winner: SharedValue<number>;
  /** 0: the winning wedge lies in the face. 1: lifted toward the viewer (may overshoot to about 1.1). */
  lift: SharedValue<number>;
  /** 0..1: the winning wedge's enamel lights up from inside. */
  glow: SharedValue<number>;
  /** 0..1: opacity of the prize printed in the winning wedge (0 while the hero copy is in the air). */
  winnerPrize: SharedValue<number>;
  /** Scale of that prize about its own centre (the restock pop; 1 at rest). */
  winnerPrizeScale: SharedValue<number>;
  /** 0..1: everything except the lifted wedge sinks into the dark (the hero's focus pull). */
  dim: SharedValue<number>;
  /**
   * 0..1: two bright glints leave the pointer and run round the chrome ring in opposite directions,
   * meeting at 3 o'clock. 0 and 1 draw nothing.
   */
  comet: SharedValue<number>;
  /** 0..1: the hub is pressed in. */
  hubPress: SharedValue<number>;
  /** Called with true once the shader and both textures are ready to draw, and false if they are lost. */
  onReady?: (ready: boolean) => void;
};

// ---------------------------------------------------------------------------------------------
// The motion (use-wheel-motion.ts) — what the screen is given
// ---------------------------------------------------------------------------------------------

export const PHASE = {
  /** At rest, waiting for a finger. */
  idle: 0,
  /** A finger holds the wheel; theta follows it. */
  held: 1,
  /** Released; the simulation runs until the wheel and the pointer are still. */
  spinning: 2,
  /** Stopped on a wedge; the short still beat before the reveal. */
  landed: 3,
  /** The prize is out in front of the wheel, until Collect is pressed. */
  reveal: 4,
  /** The prize flies into the button and the wheel restocks. */
  collecting: 5,
} as const;

export type WheelLayout = {
  /** Wheel centre and radius, points. */
  cx: number;
  cy: number;
  radius: number;
};

export type WheelMotion = {
  /** Everything WheelFace needs (same meaning as in WheelFaceProps). */
  theta: SharedValue<number>;
  omega: SharedValue<number>;
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  envTurn: SharedValue<number>;
  swayA: SharedValue<number>;
  swayB: SharedValue<number>;
  winner: SharedValue<number>;
  lift: SharedValue<number>;
  glow: SharedValue<number>;
  winnerPrize: SharedValue<number>;
  winnerPrizeScale: SharedValue<number>;
  dim: SharedValue<number>;
  comet: SharedValue<number>;
  hubPress: SharedValue<number>;

  /** One of PHASE. */
  phase: SharedValue<number>;
  /** The pointer's swing, radians (see POINTER). */
  swing: SharedValue<number>;
  /** Jumps to 1 on the frame a peg strikes the pointer and decays to 0 (time constant 90 ms). */
  tick: SharedValue<number>;
  /** Number of strikes so far (only ever grows). */
  strikes: SharedValue<number>;
  /** Scale of the whole wheel and pointer about the wheel's centre: 1, 0.985 while held, 0.94 behind the hero. */
  wheelScale: SharedValue<number>;
  /** 1 normally. Under Reduce Motion it dips to 0 and back while theta is changed out of sight. */
  wheelOpacity: SharedValue<number>;
  /** A lagging angle, radians: the night sky's stars are dragged round the wheel by it and let go. */
  swirl: SharedValue<number>;

  /** 0: the prize sits in its wedge. 1: the hero pose in front of the wheel. A spring; it overshoots. */
  hero: SharedValue<number>;
  /** Squash and stretch of the hero prize: -1 fully squashed (wide and low) … 0 … 1 stretched (tall and thin). */
  heroSquash: SharedValue<number>;
  /** The finger's pull on the hero prize, points, and the tilt it gives it, radians. */
  heroX: SharedValue<number>;
  heroY: SharedValue<number>;
  heroTilt: SharedValue<number>;
  /** 0..1: the fan of light behind the hero prize. */
  rays: SharedValue<number>;
  /** Seconds since the star burst left, or -1 when there is none. Particles are a pure function of it. */
  burst: SharedValue<number>;
  /** 0.5..1: how big the burst is (1 for the jackpot). */
  burstPower: SharedValue<number>;
  /** 0: the count badge sits on the prize. 1: it has flown to the caption and opened into the numeral. */
  badge: SharedValue<number>;
  /** 0..1: presence of the caption (numeral and label) under the wheel. */
  caption: SharedValue<number>;
  /** The number the caption shows while it counts up (ends on the prize's count). */
  count: SharedValue<number>;
  /** 0: the hint pill. 1: the Collect button. In between it morphs (size, material, label). */
  button: SharedValue<number>;
  /** -1..1: 1 pressed in (scale 0.97); negative is the swallow when the prize lands in it (scale up to 1.04). */
  buttonPress: SharedValue<number>;
  /** 0: the hero prize in its pose. 1: inside the button. */
  collect: SharedValue<number>;
  /** 0: the idle subtitle. 1: the winning subtitle. */
  copySwap: SharedValue<number>;
  /** 1 at rest. Opacity of the hint pill's content; it sinks to 0.35 while the wheel spins. */
  hint: SharedValue<number>;
  /** -1..1: a sideways nudge of the hint pill after a throw too weak to count. */
  hintNudge: SharedValue<number>;

  /** The self-playing loop's finger: position in points and presence 0..1. */
  touchX: SharedValue<number>;
  touchY: SharedValue<number>;
  touchOn: SharedValue<number>;
};
