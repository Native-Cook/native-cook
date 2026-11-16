/** Measurements refer to .work/gel-cube-unbox/design.md; units are reference points. */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34, sourceH: 804 } as const;
export const ref = {
  color: {
    tint: '#2F63D8',
    page: '#3C5180',
    floor: '#8FA6DA',
    ink: '#F7F9FD',
    mute: '#D6DEEF',
    litInk: '#24385D',
    titleRest: '#E8EEFA',
    flowGlass: 'rgba(244,250,255,0.14)',
    litMute: '#283D62',
    edge: '#7FB2FF',
    core: '#F4FAFF',
    fan: '#DCEBFF',
    underside: '#9CC4FF',
  },
  type: {
    display: { family: 'GcuInter', size: 25, lineHeight: 31, tracking: -0.5 },
    body: { family: 'GcuInter', size: 17, lineHeight: 24, tracking: -0.3 },
    action: { size: 21 },
    chip: { size: 15 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { pill: 29, sheet: 48, chip: 17 },
  elevation: { contact: [2, 4, 0.3], ambient: [18, 40, 0.18] },
  light: { az: 290, el: 25, kelvin: 6500, intensity: 1 },
  material: { round: 3, bevel: 1.5, lid: 0.14, refraction: 6, grain: 0.03, glassFill: 0.1, glassStroke: 0.35 },
} as const;
export const sys = {
  color: ref.color,
  type: ref.type,
  space: ref.space,
  radius: ref.radius,
  material: ref.material,
  surface: { page: ref.color.page },
  ink: { primary: ref.color.ink, secondary: ref.color.mute },
  hero: { glow: ref.color.core, rim: ref.color.edge, light: ref.light },
} as const;
export const comp = {
  cube: { x: 86, y: 129, w: 220, h: 211, size: 215, cx: 196, cy: 235 }, // cube
  close: { x: 340, y: 44, w: 44, h: 44 }, // close
  title: { x: 24, y: 380, w: 354, h: 62 }, // title
  subtitle: { x: 20, y: 451, w: 362, h: 26 }, // subtitle
  pill: { x: 16, y: 497, w: 370, h: 58 }, // pill
  sheet: { x: 0, y: 596, w: 402, h: 278 }, // sheet
  chip: { x: 149, y: 629, w: 104, h: 34 }, // chip
  sheetTitle: { x: 16, y: 680, w: 370, h: 36 }, // sheetTitle
  sheetBody: { x: 24, y: 720, w: 354, h: 52 }, // sheetBody
  arrow: { x: 76, y: 789, w: 8, h: 7 },
  touch: { x: 86, y: 96, w: 220, h: 252 },
  flow: {
    padding: 16,
    gap: 16,
    title: 25,
    titleLine: 32,
    body: 17,
    bodyLine: 25,
    chip: 15,
    chipLine: 24,
    action: 21,
    actionHeight: 58,
    actionRadius: 29,
    alpha: 0.96,
  },
  titleShadow: { radius: 6, y: 3, alpha: 0.45, rgb: '20,35,71' },
  compactHero: 280,
  largeTypeThreshold: 1.1,
  press: 0.97,
  minHit: 44,
} as const;
export const COPY = {
  title: 'Congratulations!\nYou got a Lumen Box',
  subtitle: 'Deals are waiting for you inside',
  sealed: {
    action: 'Unbox',
    chip: '54 days',
    sheetTitle: 'Next one in $38.56',
    sheetBody: 'Save up the required amount before\nyour Lumen Box turns into a pumpkin',
  },
  opened: {
    action: 'See deals',
    chip: '7 days', // the chip carries a clock: it shows how long the unlocked deals last (was '3 deals')
    sheetTitle: 'Unlocked today',
    sheetBody: '$8 off your next order · Free delivery\nA little extra with your next coffee',
  },
} as const;

export function makeLayout(W: number, H: number, top: number, bottom: number) {
  const S = Math.min(W / REF.w, (H - top - bottom) / (REF.sourceH - REF.safeTop));
  const ox = (W - REF.w * S) / 2;
  const oy = top - REF.safeTop * S;
  return { W, H, S, ox, oy, x: (x: number) => ox + x * S, y: (y: number) => oy + y * S };
}
export type GcuPalette = {
  tint: number[];
  page: number[];
  floor: number[];
  edge: number[];
  core: number[];
  fan: number[];
  underside: number[];
};
const linear = (x: number) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4);
const encoded = (x: number) => Math.max(0, Math.min(1, x <= 0.0031308 ? 12.92 * x : 1.055 * Math.max(0, x) ** (1 / 2.4) - 0.055));
function rgb(hex: string) {
  const n = parseInt(hex.replace('#', ''), 16);
  return [(n >> 16) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
function lab(c: number[]) {
  const [r, g, b] = c.map(linear);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}
function fromLab([L, a, b]: number[]) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ].map(encoded);
}
/** Change hue/chroma in OKLCH, keeping the measured lightness ladder of the source. No runtime shader hue hacks. */
export function paletteOf(tint: string = ref.color.tint): GcuPalette {
  if (!/^#[0-9a-f]{6}$/i.test(tint)) tint = ref.color.tint;
  const original = lab(rgb(ref.color.tint));
  const chosen = lab(rgb(tint));
  const delta = Math.atan2(chosen[2], chosen[1]) - Math.atan2(original[2], original[1]);
  const ratio = Math.min(1.4, Math.hypot(chosen[1], chosen[2]) / Math.max(0.001, Math.hypot(original[1], original[2])));
  const recolor = (hex: string) => {
    if (tint.toLowerCase() === ref.color.tint.toLowerCase()) return rgb(hex);
    const [l, a, b] = lab(rgb(hex));
    const c = Math.hypot(a, b) * ratio;
    const h = Math.atan2(b, a) + delta;
    return fromLab([l, c * Math.cos(h), c * Math.sin(h)]);
  };
  return {
    tint: rgb(tint),
    page: recolor(ref.color.page),
    floor: recolor(ref.color.floor),
    edge: recolor(ref.color.edge),
    core: recolor(ref.color.core),
    fan: recolor(ref.color.fan),
    underside: recolor(ref.color.underside),
  };
}
