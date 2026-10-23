// Gem Orb Collect — the contract between the crystal shader, the backdrop and orb layers,
// the crystal controls and the screen. Every file of the block imports its shared types,
// sizes, colours and timings from here.
//
// Spaces
// - Canvas: one full-screen Skia canvas, in points, origin top-left, x right, y DOWN.
// - Gem view space: origin at the crystal's centre, x right, y UP, z toward the viewer.
//   One unit is the crystal's girdle circumradius (`radius` points on the canvas).
// - Gem local space: the cut's own axes. The table (the flat top facet) faces +z, the culet -z.
//   view = Rx(pitch) · Ry(yaw) · Rz(roll) · local.
//   Positive yaw turns the table toward the viewer's right (the way a finger dragging right pushes it),
//   positive pitch tips the top of the stone toward the viewer, roll turns it about its own axis.
// - The camera is a weak pinhole on the +z axis, CAMERA units from the crystal's centre.
import type { SharedValue } from 'react-native-reanimated';

export const CAMERA = 7;

/** The attitude the crystal rests in: table almost square to the viewer, a little of the crown's right side showing. */
export const REST = { yaw: -0.16, pitch: 0.1, roll: 0.22 } as const;

// ---------------------------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------------------------
export const PALETTE = {
  pageTop: '#17175E',
  pageMid: '#0A0A30',
  pageBottom: '#000000',
  bloom: '#2A2CE0',
  dust: '#7B5CFF',
  gemBody: '#2A2BD8',
  gemDeep: '#100E6E',
  gemLit: '#B58CFF',
  gemPink: '#E9A9FF',
  rimCyan: '#59E6FF',
  rimGreen: '#5CFFB0',
  rimMagenta: '#FF7AF0',
  starCore: '#FFFFFF',
  starHalo: '#F0B8FF',
  orbCyan: '#9FEFFF',
  orbLilac: '#C9B6FF',
  orbPink: '#FFB3F4',
  title: '#F2F1FF',
  body: 'rgba(214, 212, 255, 0.66)',
  number: '#FFFFFF',
  buttonLabel: '#FFFFFF',
} as const;

// ---------------------------------------------------------------------------------------------
// Layout. Designed on 402 × 874 (safe area 62 top, 34 bottom); `layout()` gives every other size.
// ---------------------------------------------------------------------------------------------
export type Box = { x: number; y: number; width: number; height: number };

export type Layout = {
  width: number;
  height: number;
  /** min(width / 402, height / 874). */
  k: number;
  /** Type scale: max(k, 0.86). */
  type: number;
  /** Crystal centre and girdle circumradius, canvas points. */
  gemX: number;
  gemY: number;
  gemRadius: number;
  /** The running tally ("+120"), centred on this point while orbs arrive. */
  tallyX: number;
  tallyY: number;
  /** Top of the title line and of the one-line body under it. */
  titleTop: number;
  bodyTop: number;
  /** The balance capsule (mini crystal + rolling number) and the mini crystal inside it. */
  capsule: Box;
  miniGemX: number;
  miniGemY: number;
  miniGemRadius: number;
  /** The Collect / Replay bar. */
  button: Box;
};

export const TYPE = {
  title: { size: 28, line: 34, family: 'GemInter-Bold', tracking: -0.4 },
  body: { size: 16, line: 24, family: 'GemInter-Regular', tracking: 0 },
  number: { size: 20, line: 24, family: 'GemInter-Bold', tracking: 0 },
  tally: { size: 22, line: 28, family: 'GemInter-Bold', tracking: 0 },
  button: { size: 17, line: 24, family: 'GemInter-Bold', tracking: 0 },
} as const;

export function layout(width: number, height: number, safeTop: number, safeBottom: number): Layout {
  'worklet';
  const k = Math.min(width / 402, height / 874);
  const type = Math.max(k, 0.86);
  const usable = height - safeTop - safeBottom;
  const gemRadius = Math.round(104 * k);
  const gemX = width / 2;
  const gemY = Math.round(safeTop + usable * 0.296);
  const titleTop = Math.round(safeTop + usable * 0.599);
  const bodyTop = titleTop + Math.round(TYPE.title.line * type) + 8;
  const capsuleHeight = 44;
  const capsuleWidth = 120;
  const capsuleTop = bodyTop + Math.round(TYPE.body.line * type) + 24;
  const buttonHeight = height < 700 ? 48 : 56;
  const buttonTop = height - safeBottom - 24 - buttonHeight;
  return {
    width,
    height,
    k,
    type,
    gemX,
    gemY,
    gemRadius,
    tallyX: width / 2,
    tallyY: gemY + gemRadius + Math.round(54 * k),
    titleTop,
    bodyTop,
    capsule: { x: Math.round((width - capsuleWidth) / 2), y: capsuleTop, width: capsuleWidth, height: capsuleHeight },
    miniGemX: Math.round((width - capsuleWidth) / 2) + 12 + 12,
    miniGemY: capsuleTop + capsuleHeight / 2,
    miniGemRadius: 12,
    button: { x: 24, y: buttonTop, width: width - 48, height: buttonHeight },
  };
}

// ---------------------------------------------------------------------------------------------
// The numbers of the story
// ---------------------------------------------------------------------------------------------
export const ORB_COUNT = 20;
export const ORB_VALUE = 10;
export const BALANCE_BEFORE = 1400;
export const BALANCE_AFTER = BALANCE_BEFORE + ORB_COUNT * ORB_VALUE;

export const COPY = {
  titleBefore: '200 Lumens ready',
  titleAfter: '200 Lumens collected',
  bodyBefore: 'Hold the crystal to draw them in.',
  bodyAfter: 'Keep collecting to unlock new crystals.',
  buttonBefore: 'Collect',
  buttonAfter: 'Replay',
} as const;

// ---------------------------------------------------------------------------------------------
// Timings (seconds on the simulation clock)
// ---------------------------------------------------------------------------------------------
export const TIMING = {
  /** A touch has to stay down, and still (within holdSlop points), this long before the first orb leaves.
   *  A shorter touch pings the crystal; a touch that moves first is a drag and never collects. */
  holdDelay: 0.28,
  holdSlop: 8,
  /** Gap between arrivals: firstGap · gapRatio^n, never below minGap. */
  firstGap: 0.26,
  gapRatio: 0.9,
  minGap: 0.05,
  /** Flight time of the first and of the last orb. */
  flightFirst: 0.95,
  flightLast: 0.55,
  /** An orb's light travels from the facet it entered to the star. */
  absorb: 0.28,
  /** The held breath between the last arrival and the burst. */
  hush: 0.32,
  /** The burst: flare attack, flare decay (time constant), spin, shock ring, beams. */
  flareAttack: 0.09,
  flareDecay: 0.42,
  spin: 1.5,
  shock: 0.9,
  beams: 1.6,
  /** The tally leaves for the capsule this long after the burst, and travels this long. */
  tallyLeaves: 0.25,
  tallyFlight: 0.5,
  /** The balance rolls for this long once the tally lands. */
  roll: 1.1,
  /** Replay: the crystal gives the orbs back. */
  rewind: 0.9,
  /** The self-playing loop. Every idle motion has a period that divides it. */
  loop: 12,
  /** A touch pauses the self-playing loop for this long. */
  resumeAfter: 3.5,
} as const;

// ---------------------------------------------------------------------------------------------
// Lights: the orbs nearest the crystal, as the crystal shader sees them.
// A flat array of LIGHTS × 4 numbers: x, y, z (gem view space, in gem radii) and strength.
//   strength > 0: a light OUTSIDE the stone. Facets turned toward it flare (specular), and where it is
//                 behind the stone it is seen refracted through the facets.
//   strength < 0: a light INSIDE the stone, |strength| bright, at that point. It glows through the facets
//                 like the star does. An orb becomes one of these the moment it enters.
//   strength = 0: unused slot.
// Approaching orbs use about 1, the moment of contact about 3, an absorbed orb starts near -2 and fades to 0.
// ---------------------------------------------------------------------------------------------
export const LIGHTS = 6;

export type GemLayerProps = {
  /** Crystal centre on the canvas, points. */
  centreX: SharedValue<number>;
  centreY: SharedValue<number>;
  /** Points per gem unit (girdle circumradius), press and bounce included. */
  radius: SharedValue<number>;
  /** Radians. */
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
  roll: SharedValue<number>;
  /** Squash and stretch along the screen's vertical: 1 is none, 0.9 is 10% shorter and a little wider. */
  stretch: SharedValue<number>;
  /** 0..1: how much light the crystal holds. 0: the star is a dim ember and the body is deep. 1: the star is bright, the body saturated, fire in every facet. */
  charge: SharedValue<number>;
  /** 0..1: the burst. The star overexposes, dispersion widens, long star rays leave the stone (they may reach 2.2 radii from the centre). */
  flare: SharedValue<number>;
  /** LIGHTS × 4 numbers, see above. */
  lights: SharedValue<number[]>;
  /** Seconds on the simulation clock (slow shimmer only; nothing may jump when it wraps at TIMING.loop). */
  time: SharedValue<number>;
  /** 0..1 over the whole layer. */
  opacity: SharedValue<number>;
};

export type BackdropLayerProps = {
  width: number;
  height: number;
  gemX: SharedValue<number>;
  gemY: SharedValue<number>;
  /** Resting girdle radius in points (not animated). */
  gemRadius: number;
  /** For the caustic flecks the crystal throws on the page and the parallax of the dust. */
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
  charge: SharedValue<number>;
  flare: SharedValue<number>;
  /** 0..1: the held breath. The sky dims, the halo contracts, the dust is drawn toward the crystal. */
  hush: SharedValue<number>;
  /** 0..1: progress of the one shock ring leaving the crystal. 0 and 1 draw nothing. */
  shock: SharedValue<number>;
  /** 0..1: strength of the prismatic beams the spinning crystal sweeps round the page. */
  beams: SharedValue<number>;
  time: SharedValue<number>;
};

// ---------------------------------------------------------------------------------------------
// Sprites: orbs, their comet tails and the sparks they strike. One flat array, SPRITE_STRIDE numbers each.
//   0 x        canvas points
//   1 y        canvas points
//   2 z        depth in gem radii, 0 at the crystal's centre, positive toward the viewer.
//              z < 0 is drawn by the pass behind the crystal, z >= 0 by the pass in front.
//              Far from 0 the sprite is out of focus: softer, and beyond about +1.6 a wide dim bokeh disc.
//   3 size     diameter of the bright core in points (the halo is drawn about four times wider)
//   4 alpha    0..1; 0 means the slot is empty
//   5 tint     0 cyan-white, 1 lilac, 2 pink (whole numbers)
//   6 stretch  1 is a round orb; above 1 it is drawn as a comet this many times longer than wide, head first
//   7 angle    radians, direction of travel on the canvas (0 is +x, positive turns toward +y, i.e. downward)
// ---------------------------------------------------------------------------------------------
export const MAX_SPRITES = 72;
export const SPRITE_STRIDE = 8;

export type OrbsLayerProps = {
  sprites: SharedValue<number[]>;
  pass: 'back' | 'front';
};

// ---------------------------------------------------------------------------------------------
// Crystal controls: the Collect bar and the balance capsule, cut from the same stone.
// ---------------------------------------------------------------------------------------------
export type CrystalPillLayerProps = {
  /** Canvas points. The pill is a stadium (corner radius = height / 2). */
  x: number;
  y: number;
  width: number;
  height: number;
  /** 'bar': the bright violet Collect bar. 'capsule': the smaller, darker smoked-glass balance capsule. */
  tone: 'bar' | 'capsule';
  /** 0..1: pressed. The bar sinks (the screen also scales it to 0.97), its facets darken and the glints slide. */
  press: SharedValue<number>;
  /** 0..1: lit from inside (the capsule when the tally lands, the bar when it becomes available). */
  glow: SharedValue<number>;
  /** -1..2: position of a band of light crossing the pill from left (0) to right (1). Off the pill it shows nothing. */
  sheen: SharedValue<number>;
  /** 0..1 over the whole pill. */
  opacity: SharedValue<number>;
  /** Scale about the pill's centre (press-in 0.97, capsule squash). */
  scale: SharedValue<number>;
  time: SharedValue<number>;
};

export type RollingNumberProps = {
  /** The number shown, continuous: 1400 → 1600. Each column shows its digit of this value; columns turn like an odometer. */
  value: SharedValue<number>;
  /** How many digits to show (4: "1,400"). A thousands comma is drawn. */
  digits: number;
  /** Font size and line height in points; family is TYPE.number.family. */
  size: number;
  line: number;
  color: string;
};
