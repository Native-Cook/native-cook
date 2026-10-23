import type { SharedValue } from 'react-native-reanimated';

/** Design space is a 402 x 874 pt phone. Every length below is multiplied by `k`. */
export const D = {
  width: 402,
  height: 874,
  /** The cap: closed it is the whole object, open it is the panel's header. */
  cap: { w: 288, h: 60, r: 22 },
  /** The slab hangs under the cap, 2 pt narrower on each side. */
  slab: { inset: 2, r: 12 },
  /** Cap top to slab bottom when open. */
  panelH: 376,
  /** Row centre lines, measured from the cap's top edge when open. */
  rows: [98, 166, 234, 302],
  rowH: 68,
  /** Label left edge from the cap's left edge; Inter Bold caps. */
  label: { x: 26, size: 21, tracking: 2.8 },
  title: { size: 40, tracking: 4.5 },
  /** Switch: left edge from the cap's left edge; knob travels `travel` between its two rests. */
  sw: { x: 218, w: 44, h: 24, knob: 18, inset: 3, travel: 20 },
  /** How far the cap's centre rises when the panel opens: (panelH - cap.h) / 2. */
  rise: 158,
  /** Furthest the halo reaches from the object's outline at full green. */
  haloReach: 150,
} as const;

export const COUNT = 4;
export const TITLE = 'SUCCESS';
export const LABELS = ['DISCIPLINE', 'DEDICATION', 'HARDWORK', 'CONSISTENCY'] as const;

/** Emitted light at 0, 1, 2, 3 and 4 switches on (sRGB 0..1). The cap's unfilled part is the same hue, dimmer. */
export const HUES = [
  [1.0, 0.231, 0.231],
  [0.91, 0.19, 0.55],
  [0.47, 0.33, 1.0],
  [0.15, 0.745, 0.705],
  [0.18, 0.878, 0.416],
] as const;
export const INK = '#F4F3EF';
export const PAGE = '#000000';

export function checklistScale(width: number, height: number) {
  'worklet';
  return Math.min(1, width / D.width, height / 700);
}

export type ChecklistGeometry = {
  /** Cap centre. */
  cx: number; capCy: number;
  /** Cap half width at full presence, half height. */
  capHalfW: number; capHalfH: number;
  /** Slab: left, right, bottom edge. Its top is hidden under the cap. */
  slabLeft: number; slabRight: number; slabBottom: number;
};

/** Where everything is for a given `open` (0 closed pill at the screen centre, 1 open panel). */
export function checklistGeometry(open: number, width: number, height: number, k: number): ChecklistGeometry {
  'worklet';
  const capCy = height / 2 - D.rise * k * open;
  const capHalfW = (D.cap.w * k) / 2;
  const capHalfH = (D.cap.h * k) / 2;
  return {
    cx: width / 2,
    capCy,
    capHalfW,
    capHalfH,
    slabLeft: width / 2 - capHalfW + D.slab.inset * k,
    slabRight: width / 2 + capHalfW - D.slab.inset * k,
    slabBottom: capCy + capHalfH + (D.panelH - D.cap.h) * k * open,
  };
}

/** Row `index` centre line: rows hang from the slab's bottom edge, so they slide out from under the cap. */
export function rowCentreY(index: number, slabBottom: number, k: number) {
  'worklet';
  return slabBottom - (D.panelH - D.rows[index]) * k;
}

/**
 * Everything the picture needs, written by the simulation on the UI thread once per frame.
 * The canvas only reads these.
 */
export type ChecklistMotion = {
  /** Seconds since the object last appeared (simulation time). Drives turbulence and breathing. */
  clock: SharedValue<number>;
  /** 0 nothing on screen .. 1 cap at full width. The cap wipes open from its centre. */
  present: SharedValue<number>;
  /** Intro pop of the seven title letters, each 0..1 (1 at rest). */
  letters: SharedValue<number[]>;
  /** 0 closed pill .. 1 open panel. */
  open: SharedValue<number>;
  /** Cap press depth 0..1 (1 is scale 0.95). */
  press: SharedValue<number>;
  /** Knob ends per switch in travel units: 0 off .. 1 on. The knob is the capsule between them. */
  lead: SharedValue<number[]>;
  trail: SharedValue<number[]>;
  /** Finger on the knob, per switch, 0..1. */
  hold: SharedValue<number[]>;
  /** Cap fill, 0..4: a quick follower of the live sum of knob centres. */
  fill: SharedValue<number>;
  /** Halo colour position, 0..4: a slower follower of the same sum. */
  hue: SharedValue<number>;
  /** Green takeover 0..1, sprung, overshoots to about 1.25 on the surge. */
  tip: SharedValue<number>;
  /** One-shot shock ring: 0 idle, 0 -> 1 while it runs. */
  surge: SharedValue<number>;
  /** Rim flash 0..1, decays by itself. */
  flash: SharedValue<number>;
  /** Glare streak across the cap, -0.3..1.3; nothing shows outside 0..1. */
  glare: SharedValue<number>;
  /** 1 under reduced motion: the still picture (no turbulence, no ring, no glare). */
  still: SharedValue<number>;
};
