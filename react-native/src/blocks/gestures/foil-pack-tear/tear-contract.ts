/**
 * Foil Pack Tear: the contract between the scene (everything Skia draws) and the
 * motion (gestures, state machine, springs, the self-playing demo).
 *
 * The motion owns one shared value per field of `TearPose`; the scene only reads
 * them. Every length is in stage points: a 402 x 874 design space that the screen
 * scales uniformly (`stageFit`) and centres. Angles are radians.
 */
import type { SharedValue } from 'react-native-reanimated';

export const STAGE = { width: 402, height: 874 } as const;

/** The rarity frame: a card-shaped glowing border holding the pack. */
export const FRAME = { x: 48, y: 128, width: 306, height: 492, radius: 28, band: 12 } as const;

/** The plate on the frame's bottom edge that carries the rarity word. A trapezoid that flares downwards. */
export const TAB = { cx: 201, top: 590, bottom: 626, topWidth: 128, bottomWidth: 172, labelWidth: 120, labelHeight: 14, labelCy: 608 } as const;

/** The sealed pack at rest. `sealTop`/`sealBottom` are the heights of the crimped bands. */
export const PACK = { x: 93, y: 176, width: 216, height: 376, sealTop: 26, sealBottom: 24 } as const;

/**
 * The tear. The line runs left to right across the pack just under the top seal.
 * It is stored as one vertical offset per column (`columns` samples, `step` pt apart,
 * column 0 on the pack's left edge), measured from `y` in pack-local points and kept
 * within +-`wander`. `notch` is the depth of the pre-cut V on the left edge at rest.
 */
export const TEAR = { y: 34, wander: 10, columns: 73, step: 3, notch: 5 } as const;

/** The card. `width`/`height` is its size inside the pack; revealed it is `revealScale` times larger. */
export const CARD = {
  width: 180,
  height: 252,
  radius: 9,
  /** Centre while hidden in the sealed pack. */
  insideX: 201,
  insideY: 384,
  /** Centre after the spring that lifts it 60% clear of the torn mouth (the pack has sunk `PEEK_SINK`). */
  peekY: 201,
  /** Centre and scale once revealed: 250 x 350 pt, 16 pt inside the frame's band. */
  revealY: 364,
  revealScale: 250 / 180,
} as const;

/** How far the opened pack sinks while the card rises. */
export const PEEK_SINK = 16;

/** The mirror line of the floor reflection and the centre line of the hint. */
export const FLOOR_Y = 636;
export const HINT_Y = 806;

/** Bundled assets (all in `./assets`). Pixel sizes are 3x the point sizes above. */
export const ASSETS = {
  /** Greyscale strip, five 648 x 1128 tiles side by side: normal X, normal Y, height, silhouette, cavity. */
  packMaps: { tile: [648, 1128], tiles: 5 },
  /** Colour RGBA, 648 x 1128: the ink printed on the film (alpha = ink coverage). */
  packPrint: [648, 1128],
  /** Colour, 750 x 1050 each, square corners (the scene rounds them). */
  cardFace: [750, 1050],
  cardBack: [750, 1050],
  /** Greyscale strip, three 750 x 1050 tiles: face foil mask, face depth, back foil mask. */
  cardMaps: { tile: [750, 1050], tiles: 3 },
  /** White on transparent, 360 x 42: the word LEGENDARY for the tab. */
  tabLabel: [360, 42],
  /** White on transparent, 96 x 96: a four-point glint with a soft halo. */
  spark: [96, 96],
} as const;

/** Everything the scene needs to draw one frame. See the briefs for what each state looks like. */
export type TearPose = {
  /** Lamp lean, -1..1 on each axis (finger or device tilt). 0,0 is the studio default (key light upper left). */
  lightX: number;
  lightY: number;
  /** Tilt of the whole stage about the frame's centre, for the idle sway. |value| <= 0.07. 0 while touched. */
  swayX: number;
  swayY: number;
  /** 1 while a sealed pack rests untouched (breathing, glint along the tear line); 0 otherwise. Eased by the motion. */
  calm: number;

  /** Pack offset from its rest rectangle, rotation about its centre, scale about its centre, opacity. */
  packX: number;
  packY: number;
  packRot: number;
  packScale: number;
  packOpacity: number;

  /** Tear front in pack-local points, 0 (sealed) .. PACK.width (torn right through). Never decreases within one pack. */
  tearFront: number;
  /** TEAR.columns offsets from TEAR.y, valid for columns the front has passed. */
  tearLine: number[];
  /** The torn strip: 0 while it rides the tear front as a curl, 1 once it is torn off and falling. */
  stripFree: number;
  /** While free: offset from where it detached, rotation and opacity. Ignored while it rides the front. */
  stripX: number;
  stripY: number;
  stripRot: number;
  stripOpacity: number;

  /** Light escaping through the torn gap, 0..1. Follows the tear's progress, stays after release. */
  leak: number;
  /** The flash when the strip comes off, 1 decaying to 0. */
  burst: number;
  /** How far the torn mouth has opened, 0..1. */
  mouth: number;

  /** Card centre (stage points), scale (1 = 180 x 252), rotation about its vertical axis (0 = back to the viewer, pi = face), tilt and opacity. */
  cardX: number;
  cardY: number;
  cardScale: number;
  cardFlip: number;
  cardTiltX: number;
  cardTiltY: number;
  cardOpacity: number;
  /** A band of light crossing the card face: -0.3 (off, before) .. 1.3 (off, after). */
  sheen: number;

  /** Frame colour: 0 = cool cyan-blue-violet (sealed), 1 = legendary white-gold. */
  frameHeat: number;
  /** Light racing round the border from the tab to the top on both sides: 0 = not started, 1 = met at the top and gone. */
  frameRing: number;
  /** Brightness of the tab plate and its word, 0..1 (rest is about 0.45). */
  tabGlow: number;

  /** Seconds since the glitter burst behind the revealed card began; -1 when there is none. */
  sparkleAge: number;
  /** Amount of drifting glitter motes in the light above the opened pack and around the revealed card, 0..1. */
  dust: number;
};

export type TearValues = { [K in keyof TearPose]: SharedValue<TearPose[K]> };

export function flatTearLine(): number[] {
  const line: number[] = [];
  for (let i = 0; i < TEAR.columns; i += 1) line.push(0);
  return line;
}

/** A hand-made looking tear for proofs and for the demo's finger: two slow waves and a little jitter. */
export function sampleTearLine(): number[] {
  const line: number[] = [];
  for (let i = 0; i < TEAR.columns; i += 1) {
    const u = i / (TEAR.columns - 1);
    const slow = 5.5 * Math.sin(u * 4.1 + 0.6) - 3.2 * Math.sin(u * 9.3 + 1.9);
    const jitter = 0.9 * Math.sin(i * 12.9898) * Math.cos(i * 4.1414);
    line.push(Math.max(-TEAR.wander, Math.min(TEAR.wander, slow + jitter)));
  }
  return line;
}

/** A sealed pack at rest: the first and last frame of the loop. */
export const REST_POSE: TearPose = {
  lightX: 0, lightY: 0, swayX: 0, swayY: 0, calm: 1,
  packX: 0, packY: 0, packRot: 0, packScale: 1, packOpacity: 1,
  tearFront: 0, tearLine: flatTearLine(),
  stripFree: 0, stripX: 0, stripY: 0, stripRot: 0, stripOpacity: 1,
  leak: 0, burst: 0, mouth: 0,
  cardX: CARD.insideX, cardY: CARD.insideY, cardScale: 1, cardFlip: 0, cardTiltX: 0, cardTiltY: 0, cardOpacity: 1,
  sheen: -0.3,
  frameHeat: 0, frameRing: 0, tabGlow: 0.45,
  sparkleAge: -1, dust: 0,
};

/** Named still poses: the storyboard both runs prove against. */
export const STORY: Record<'rest' | 'tearing' | 'opened' | 'peek' | 'edge' | 'revealed' | 'tilted', TearPose> = {
  rest: REST_POSE,
  /** Finger at 46% of the way across, pulling: the curl rides the front, light leaks from the gap. */
  tearing: {
    ...REST_POSE, calm: 0, lightX: -0.05, lightY: -0.6, packX: 3, packRot: 0.022,
    tearFront: 0.46 * PACK.width, tearLine: sampleTearLine(), leak: 0.46,
  },
  /** 90 ms after the strip came off: flash, strip falling, card not yet moving. */
  opened: {
    ...REST_POSE, calm: 0, tearFront: PACK.width, tearLine: sampleTearLine(),
    stripFree: 1, stripX: 14, stripY: 22, stripRot: 0.5, stripOpacity: 1,
    leak: 1, burst: 0.85, mouth: 0.4, tabGlow: 0.6,
  },
  /** The card has sprung 60% clear, back to the viewer; the pack has sunk. */
  peek: {
    ...REST_POSE, calm: 0, packY: PEEK_SINK, tearFront: PACK.width, tearLine: sampleTearLine(),
    stripFree: 1, stripOpacity: 0, leak: 0.8, mouth: 1, cardY: CARD.peekY, tabGlow: 0.75, dust: 1,
  },
  /** Mid-reveal: the card is edge-on on its way to the centre, the empty pack is dropping away. */
  edge: {
    ...REST_POSE, calm: 0, packY: 150, packRot: -0.14, packOpacity: 0.45, tearFront: PACK.width, tearLine: sampleTearLine(),
    stripFree: 1, stripOpacity: 0, leak: 0.3, mouth: 1,
    cardY: 300, cardScale: 1.24, cardFlip: Math.PI / 2, tabGlow: 0.9, frameHeat: 0.35, dust: 0.6,
  },
  /** The payoff, 250 ms after the face arrives: the ring has nearly met at the top, glitter is flying. */
  revealed: {
    ...REST_POSE, calm: 0, packOpacity: 0, packY: 260, tearFront: PACK.width, tearLine: sampleTearLine(),
    stripFree: 1, stripOpacity: 0,
    cardY: CARD.revealY, cardScale: CARD.revealScale, cardFlip: Math.PI, sheen: 0.45,
    frameHeat: 1, frameRing: 0.8, tabGlow: 1, sparkleAge: 0.25, dust: 1,
  },
  /** Revealed and held under a finger: tilted, the foil has moved. */
  tilted: {
    ...REST_POSE, calm: 0, lightX: 0.7, lightY: -0.4, packOpacity: 0, packY: 260, tearFront: PACK.width, tearLine: sampleTearLine(),
    stripFree: 1, stripOpacity: 0,
    cardY: CARD.revealY, cardScale: CARD.revealScale, cardFlip: Math.PI, cardTiltX: -0.16, cardTiltY: 0.24, sheen: 1.3,
    frameHeat: 1, frameRing: 1, tabGlow: 0.85, sparkleAge: 3, dust: 1,
  },
};

/** Uniform fit of the 402 x 874 stage into a container, centred. */
export function stageFit(width: number, height: number) {
  'worklet';
  const k = Math.min(width / STAGE.width, height / STAGE.height);
  return { k, x: (width - STAGE.width * k) / 2, y: (height - STAGE.height * k) / 2 };
}
