/**
 * Contract between the fan renderer and the screen. Both sides import from here.
 * Positions are in points on the screen; (0, 0) is the screen's top-left corner.
 */
import type { SharedValue } from 'react-native-reanimated';

export type BladeHue = 'lime' | 'ember' | 'teal';

export interface BladeCard {
  hue: BladeHue;
  /** 0–99. The number engraved in the blade, and how far its light rises, in percent of its length. */
  value: number;
}

/** The wheel is endless; the card at index i is BLADE_CARDS[((i % 9) + 9) % 9]. */
export const BLADE_PERIOD = 9;
export const BLADE_CARDS: readonly BladeCard[] = [
  { hue: 'lime', value: 60 },
  { hue: 'ember', value: 47 },
  { hue: 'teal', value: 49 },
  { hue: 'lime', value: 71 },
  { hue: 'ember', value: 38 },
  { hue: 'teal', value: 55 },
  { hue: 'lime', value: 62 },
  { hue: 'ember', value: 44 },
  { hue: 'teal', value: 53 },
];

export const BLADE_HUES: Record<BladeHue, string> = {
  lime: '#D8F21A',
  ember: '#E8480C',
  teal: '#0FE0D2',
};
export const CTA_LIME = '#DCFF1F';

export interface FanLayout {
  width: number;
  height: number;
  /** min(width / 402, height / 874): every fan length below is already multiplied by it. */
  scale: number;
  /** Centre the blades radiate from; it lies below the bottom of the screen. */
  pivotX: number;
  pivotY: number;
  /** A blade: a rounded slab of glass. */
  cardW: number;
  cardH: number;
  cardT: number;
  cornerR: number;
  /** Distance from the pivot to the bottom edge of the hero blade at rest, and of a side blade. */
  heroBottomR: number;
  sideBottomR: number;
  /** The lit bar the renderer draws; the screen lays its label and its press target over this rect. */
  ctaX: number;
  ctaY: number;
  ctaW: number;
  ctaH: number;
  ctaR: number;
}

export function computeFanLayout(width: number, height: number, bottomInset: number): FanLayout {
  const scale = Math.min(width / 402, height / 874);
  const ctaH = 56;
  const ctaX = 16;
  const ctaW = width - 32;
  const ctaY = height - Math.max(bottomInset, 8) - 8 - ctaH;
  const cardH = Math.round(320 * scale);
  const heroBottomR = Math.round(388 * scale);
  return {
    width, height, scale,
    pivotX: width / 2,
    pivotY: ctaY - Math.round(48 * scale) + heroBottomR,
    cardW: Math.round(132 * scale),
    cardH,
    cardT: 7 * scale,
    cornerR: 14 * scale,
    heroBottomR,
    sideBottomR: Math.round(306 * scale),
    ctaX, ctaY, ctaW, ctaH,
    ctaR: 16,
  };
}

/** Everything that moves. The screen owns and drives these; the renderer only reads them, on the UI thread. */
export interface BladeMotion {
  /** Wheel position in cards. Card i sits in slot u = pos - i; u = 0 is the hero slot, u < 0 is left of it. Raising pos turns the fan clockwise: the next card arrives from the left. Unbounded. */
  pos: SharedValue<number>;
  /** 0–1.25: how much of its light the blade in the hero slot holds. 1 at rest. A draw empties it and the landing refills it. */
  charge: SharedValue<number>;
  /** 0–1: one-shot bloom when a draw lands (the screen runs it up and back down). */
  flare: SharedValue<number>;
  /** 0–1: the bar is pressed in. */
  press: SharedValue<number>;
  /** 0–1: light held by the bar. 1 at rest; a draw sends it into the fan and it comes back when the blade has landed. */
  ctaEnergy: SharedValue<number>;
  /** 0–1: the fan is open. 0 = blades closed into one stack and dark (before the first frame), 1 = the resting fan. */
  intro: SharedValue<number>;
  /** 1 = no idle breathing (reduced motion). */
  still: SharedValue<number>;
}

export interface BladeCanvasProps {
  layout: FanLayout;
  motion: BladeMotion;
  /** Called once, on the JS thread, when the shader and its images have drawn a first frame. */
  onReady: () => void;
}
