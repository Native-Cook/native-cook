// Piñata Smash Reward — layout, colour and copy.
//
// Every position is in points of a 402 × 874 reference screen (iPhone 17 Pro). The pin is a 720 px
// square crop; it maps to the full width (1 pt = 1.79 clip px) with its top at y = 170. The values
// were measured on the pin's frames (.work/pinata-smash-reward/measure.md).

export const REF_W = 402;
export const REF_H = 874;
/** The reference phone's top safe-area inset; the header follows the real one. */
export const REF_TOP_INSET = 62;

export const COPY = {
  label: 'Keep tapping',
  done: 'Come back tomorrow',
  title: 'Daily piñata',
};

export const C = {
  page: '#FFFFFF',
  ink: '#141414',
  label: '#141414',
  bar: '#000000',
  track: '#D6D6D4',
  fill: '#8745DD',
  fillGlow: '#B57CFF',
  done: '#F0EFEB',
  doneInk: '#A3A3A0',
  white: '#FFFFFF',
  ribbon: '#E9D7FF',
  ribbonMid: '#F3E9FF',
  ribbonEdge: '#D9C6F2',
  token: '#8B41BE',
  tokenRim: '#B57BE0',
  tokenDeep: '#6E2C9C',
  chip: '#F3EEFD',
  chipInk: '#6B2FB3',
  ripple: '#DDF5F5',
  hole: '#FFD36B',
  shadow: '#000000',
  confetti: ['#CE38FA', '#59DA8F', '#72A5F1', '#B257EC', '#EFB238', '#E94B91'],
  tiles: ['#689CD9', '#426FC4', '#6B48CC', '#91CFE2', '#B257EC'],
  streamers: ['#59DA8F', '#CE38FA'],
};

/**
 * The stage (ribbon, piñata, pill, ripple, hop, drop-in, loot spawn points) sits 56 pt lower than the
 * pin's crop maps it, so the figure's centre is at y ≈ 415 and the phone is balanced; the header, the
 * floor and the pile stay where they are.
 */
export const STAGE_DY = 56;

/** Hard-coded geometry (pt). */
export const L = {
  // the piñata's figure box on screen at rest (the art's figure box maps onto it)
  figure: { x: 49, y: 227 + STAGE_DY, w: 286, h: 303 },
    ribbonW: 16.2,
  ribbonMidW: 5,
  // the pill: one shape in three states, centred on x = 201
  pill: { cx: 201.3, y: 427.7 + STAGE_DY, h: 45.5, r: 11 },
  labelW: 186,
  barW: 120,
  doneW: 172.5,
  barH: 43.6,
  track: { w: 100, h: 6.7 },
  fillMin: 6,
  font: 15.5,
  bolt: { w: 13, h: 17, gap: 8, stroke: 1.6 },
  token: 17, // radius
  // header (ours)
  title: { x: 24, base: 78, size: 20 },
  chip: { right: 24, y: 58, h: 36, r: 18, coin: 11, size: 15, padL: 8, padR: 14, gap: 7 },
  floor: 836,
  shadow: { y: 838, w: 150, h: 18 },
  // the header band: a white strip under the title and chip, drawn above the stage
  band: { bottom: 106, fade: 24 },
};

/**
 * The art's 1080 × 1140 canvas is registered to the pin: canvas px = 0.835 × (1440-frame px) − (35, 53).
 * In reference points that is x = ox + u·k, y = oy + v·k.
 */
const PIN_PT = 1440 / 402; // 1440-frame px per pt
export const ART = { w: 1080, h: 1140, k: 1 / (0.835 * PIN_PT), ox: 35 / (0.835 * PIN_PT), oy: 170 + STAGE_DY + 53 / (0.835 * PIN_PT) };
