// The contract between the picture (BeamCanvas, shaders) and the motion (simulation, hook, screen).
// Everything is in design points on a 402 x 874 screen; consumers multiply by `k` and add `ox`/`oy`.
import type { SharedValue } from 'react-native-reanimated';

export const DESIGN_W = 402;
export const DESIGN_H = 874;
export const STEP_COUNT = 5;
export const FIELD_COUNT = 7;

export const C = {
  bg: '#000000',
  core: '#EAF4FF',
  beam: '#5FA8FF',
  electric: '#2F7BFF',
  deep: '#0B3BD6',
  violet: '#7A4DFF',
  amber: '#FF8A3D',
  red: '#FF4D5E',
  redCore: '#FFE3E6',
  warm: '#FFD9A0',
  text: '#F5F8FF',
  textMute: 'rgba(226,236,255,0.56)',
  textFaint: 'rgba(226,236,255,0.36)',
  errorText: '#FF8A94',
  ink: '#06163A',
} as const;

export const FONT = {
  regular: 'BeamInter-Regular',
  bold: 'BeamInter-Bold',
} as const;

// Stepper column: a vertical glass rail through five nodes, each with a glass pill to its right.
export const G = {
  railX: 56,
  nodeY0: 128,
  pitch: 68,
  nodeR: 24, // bezel outer radius
  domeR: 18, // glass dome radius
  tubeW: 6,
  rowX: 32,
  rowW: 338,
  rowH: 44,
  rowRestScale: 0.965, // rows scale about their node centre: rest 0.965, risen 1
  rowIconX: 102, // icon centre
  rowIcon: 20,
  rowLabelX: 122,
  rowLabelSize: 16,
  rowSummaryRight: 354,
  rowSummarySize: 13,
  // The form panel hangs under the stepper: its top edge is fixed and its bottom edge moves.
  panelX: 24,
  panelW: 354,
  panelTop: 446,
  panelR: 28,
  pad: 16,
  innerW: 322,
  titleY: 16,
  titleH: 24,
  titleSize: 17,
  counterSize: 13,
  fieldH: 56,
  fieldR: 16,
  fieldPadX: 16,
  labelSize: 16,
  labelRestY: 18, // top of the 20-pt label line box when it rests as a placeholder
  labelFloatY: 7, // top of the same box when floated (scaled about its left edge)
  labelFloatScale: 0.72,
  valueY: 25, // top of the 24-pt value line box
  valueSize: 16,
  ctaH: 56,
  ctaLensX: 149, // left of the 56-pt processing lens, centred in the panel
  backW: 56,
  backGap: 8,
  errExtra: 24, // extra height while an error message shows
  errSize: 13,
  segX: 16,
  segY: 184,
  segW: 322,
  segH: 48,
  segInset: 4,
  segThumbW: 157,
  segTravel: 161,
  summaryY: 52,
  summaryRowH: 32,
  summarySize: 14,
  bodyY: 48, // confirmation body line
  bodyH: 20,
  bodySize: 14,
} as const;

export const STEPS = [
  { key: 'contact', label: 'Contact', title: 'Your email', cta: 'Continue', icon: 'mail' },
  { key: 'shipping', label: 'Shipping', title: 'Delivery address', cta: 'Continue', icon: 'box' },
  { key: 'payment', label: 'Payment', title: 'Card details', cta: 'Continue', icon: 'card' },
  { key: 'review', label: 'Review', title: 'Review order', cta: 'Pay', icon: 'receipt' },
  { key: 'done', label: 'Confirmed', title: 'Order confirmed', cta: 'Paid', icon: 'seal' },
] as const;

// Field rectangles are panel-local (origin = the panel's top-left corner), height G.fieldH.
export const FIELDS = [
  { id: 'email', step: 0, label: 'Email', x: 16, y: 52, w: 322 },
  { id: 'address', step: 1, label: 'Street address', x: 16, y: 52, w: 322 },
  { id: 'city', step: 1, label: 'City', x: 16, y: 116, w: 198 },
  { id: 'zip', step: 1, label: 'ZIP', x: 222, y: 116, w: 116 },
  { id: 'card', step: 2, label: 'Card number', x: 16, y: 52, w: 322 },
  { id: 'expiry', step: 2, label: 'MM / YY', x: 16, y: 116, w: 157 },
  { id: 'cvc', step: 2, label: 'CVC', x: 181, y: 116, w: 157 },
] as const;
export type FieldId = (typeof FIELDS)[number]['id'];

export const DELIVERY = [
  { key: 'standard', label: 'Standard', price: 'Free', summary: 'Standard · 5–7 days' },
  { key: 'express', label: 'Express', price: '$12', summary: 'Express · 2–3 days' },
] as const;
export const SUBTOTAL = 172;
export const EXPRESS_FEE = 12;
export const ORDER_ID = '#NC-20481';

// Bottom of each step's content, panel-local. The call to action starts 16 below it (plus G.errExtra
// while an error shows) and the panel ends 16 below the call to action.
export const CONTENT_BOTTOM = [108, 232, 172, 180, 68] as const;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function nodeY(i: number) {
  'worklet';
  return 128 + 68 * i;
}
/** Panel height for a step; `err` is 0..1 (error message reveal). Heights: 196, 320, 260, 268, 156. */
export function panelHeight(step: number, err: number) {
  'worklet';
  const bottoms = [108, 232, 172, 180, 68];
  return bottoms[step] + 16 + 24 * err + 56 + 16;
}
/** Panel-local top of the call to action row: always 72 above the panel's bottom edge. */
export function ctaTop(panelH: number) {
  'worklet';
  return panelH - 72;
}
/** Left edge of the call to action for a split (back button out) and lens (processing) amount. */
export function ctaLeft(split: number, lens: number) {
  'worklet';
  const x = 16 + 64 * split;
  return x + (149 - x) * lens;
}
export function ctaWidth(split: number, lens: number) {
  'worklet';
  const w = 322 - 64 * split;
  return w + (56 - w) * lens;
}
/** Row scale about its node centre for a rise amount (may overshoot 1). */
export function rowScale(rise: number) {
  'worklet';
  return 0.965 + 0.035 * rise;
}
/** Opacity of the outgoing step's panel content while PANEL_MIX runs 0 -> 1. */
export function contentOut(mix: number) {
  'worklet';
  return 1 - clamp01(mix / 0.3);
}
/** Opacity of the incoming step's panel content; it also slides up from `contentShift` points below. */
export function contentIn(mix: number) {
  'worklet';
  const u = clamp01((mix - 0.4) / 0.6);
  return u * u * (3 - 2 * u);
}
export function contentShift(mix: number) {
  'worklet';
  return 10 * (1 - contentIn(mix));
}

// One flat array is published per frame (only when something changed). Index map:
export const F = {
  HEAD: 0, // beam head along the rail in node units 0..4; the rail is lit from node 0 to here
  HEAD_GLOW: 1, // 0 at rest, 1 travelling or held by a finger, up to 1.6 on the ignition frame
  HEAD_VEL: 2, // node units per second, positive = downward; the comet tail trails behind it
  TONE: 3, // 0 blue .. 1 red (error) for the head, the rays and the active node
  WARM: 4, // 0 .. 1 success: white-gold rail, warm rays
  BURST: 5, // ray and bloom intensity; 1 at rest, about 2 at an ignition, 2.6 at the finale
  SURGE: 6, // finale return wave: 0 idle, 0 -> 1 runs from node 4 up to node 0
  PRESENT: 7, // 0 .. 1 presence of the whole scene (intro fade)
  PANEL_H: 8, // panel height in points
  PANEL_FROM: 9, // outgoing step index (integer)
  PANEL_TO: 10, // incoming / current step index (integer)
  PANEL_MIX: 11, // 0 .. 1 content cross-fade; 1 at rest
  ERR: 12, // 0 .. 1 error message reveal on the current step
  FOCUS_X: 13, // focus light rectangle, panel-local; one object that glides between fields
  FOCUS_Y: 14,
  FOCUS_W: 15,
  FOCUS_H: 16,
  FOCUS_A: 17, // its presence 0 .. 1
  CTA_PRESS: 18, // 0 .. 1 press-in
  CTA_SPLIT: 19, // 0 .. 1 the back button has split out of the call to action (steps 1 to 3)
  CTA_LENS: 20, // 0 pill .. 1 a 56-pt lens (processing the payment)
  CTA_SPIN: 21, // radians, the lens's orbiting light
  CTA_DONE: 22, // 0 .. 1 success state (tick drawn, bright)
  BACK_PRESS: 23,
  SEG_LEAD: 24, // delivery thumb: it spans min(lead, trail) .. max(lead, trail); 0 Standard, 1 Express
  SEG_TRAIL: 25,
  SEG_PRESS: 26,
  DRAG: 27, // 0 .. 1 a finger holds the beam head
  NODE_ON: 28, // +i: 0 dormant smoked glass .. 1 lit (may overshoot to 1.2); 0.4 = done but ahead of the head
  NODE_DONE: 33, // +i: 0 numeral .. 1 tick drawn
  NODE_ACTIVE: 38, // +i: 0 .. 1 active emphasis (orbit ring, wider bloom)
  NODE_FLASH: 43, // +i: ignition flash, 1 decaying to 0 (shockwave ring, sparks)
  NODE_SHAKE: 48, // +i: x offset in points (error)
  NODE_PRESS: 53, // +i: 0 .. 1
  ROW_ACTIVE: 58, // +i: 0 .. 1 blue lit glass
  ROW_DONE: 63, // +i: 0 .. 1 finished (dim, shows its summary)
  ROW_RISE: 68, // +i: 0 rest .. 1 risen (may overshoot)
  ROW_SWEEP: 73, // +i: 0 .. 1 rim light pouring from the node end to the far end on activation
  ROW_PRESS: 78, // +i: 0 .. 1
  FIELD_FLOAT: 83, // +f: 0 label rests as placeholder .. 1 floated
  FIELD_ERR: 90, // +f: 0 .. 1 red
  SIZE: 97,
} as const;

export type BeamMotion = {
  /** Simulation seconds, always advancing while the scene is on screen (shimmer, plasma flow). */
  clock: SharedValue<number>;
  /** The flat frame described by `F`. */
  frame: SharedValue<number[]>;
};

export type BeamCanvasProps = {
  width: number;
  height: number;
  k: number;
  ox: number;
  oy: number;
  motion: BeamMotion;
  /** Reduced motion: no shimmer, no ray drift; draw the frame as given. */
  still: boolean;
  /** Called once when fonts, images and shaders are loaded and the first frame has been drawn. */
  onReady: () => void;
};

export type RestOptions = {
  /** Furthest step reached (>= step). */
  furthest?: number;
  /** Delivery is Express. */
  express?: boolean;
  /** Per field: has a value. */
  filled?: readonly boolean[];
  /** Index of the field in error, or -1. */
  errorField?: number;
  /** Index of the focused field, or -1. */
  focusField?: number;
};

/** The canonical resting frame of a step. The simulation must settle to exactly this. */
export function frameAtRest(step: number, options: RestOptions): number[] {
  'worklet';
  const furthest = options.furthest === undefined ? step : options.furthest;
  const errorField = options.errorField === undefined ? -1 : options.errorField;
  const focusField = options.focusField === undefined ? -1 : options.focusField;
  const finished = step === 4;
  const frame: number[] = [];
  for (let i = 0; i < 97; i++) frame.push(0);
  frame[0] = step;
  frame[4] = finished ? 1 : 0;
  frame[5] = 1;
  frame[7] = 1;
  frame[8] = panelHeight(step, errorField >= 0 ? 1 : 0);
  frame[9] = step;
  frame[10] = step;
  frame[11] = 1;
  frame[12] = errorField >= 0 ? 1 : 0;
  frame[19] = step >= 1 && step <= 3 ? 1 : 0;
  frame[22] = finished ? 1 : 0;
  frame[24] = options.express ? 1 : 0;
  frame[25] = options.express ? 1 : 0;
  for (let i = 0; i < 5; i++) {
    const done = finished || i < furthest;
    const active = i === step && !finished;
    frame[28 + i] = i <= step ? 1 : done ? 0.4 : 0;
    frame[33 + i] = done && !active ? 1 : 0;
    frame[38 + i] = active ? 1 : 0;
    frame[58 + i] = active ? 1 : 0;
    frame[63 + i] = done && !active ? 1 : 0;
    frame[68 + i] = active || (finished && i === 4) ? 1 : 0;
    frame[73 + i] = active ? 1 : 0;
  }
  const fields = [
    [0, 16, 52, 322],
    [1, 16, 52, 322],
    [1, 16, 116, 198],
    [1, 222, 116, 116],
    [2, 16, 52, 322],
    [2, 16, 116, 157],
    [2, 181, 116, 157],
  ];
  for (let f = 0; f < 7; f++) {
    const filled = options.filled ? options.filled[f] : false;
    frame[83 + f] = filled || f === focusField ? 1 : 0;
    frame[90 + f] = f === errorField ? 1 : 0;
  }
  if (focusField >= 0) {
    frame[13] = fields[focusField][1];
    frame[14] = fields[focusField][2];
    frame[15] = fields[focusField][3];
    frame[16] = 56;
    frame[17] = 1;
  }
  return frame;
}
