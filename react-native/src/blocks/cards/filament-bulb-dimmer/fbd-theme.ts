// Filament Bulb Dimmer — tokens and layout, measured from the reference (Pinterest pin
// 697565429800389600, "Lamp Details", a 402 pt wide screen). The pin's positions are kept in its own
// points (PIN); makeLayout maps them onto the device: x scaled to the width, y from under the status
// bar down to just above the home indicator.

export const COLOR = {
  page: '#262630',
  card: '#3D383F',
  ink: '#FFFFFF',
  title: '#E9E5EB',
  chipText: '#ECE8EE',
  chipBg: '#2B2A32',
  tab: '#282832',
  tabText: '#3D3C46',
  toggle: '#EE7E5E',
  toggleOff: '#4A444C',
  clockBg: '#4A444C',
  dial: '#FFFFFF',
  dialLabel: '#A3A1A6',
  dialValue: '#141217',
} as const;

export const FONT = {
  regular: 'FbdInter-Regular',
  medium: 'FbdInter-Medium',
  semibold: 'FbdInter-SemiBold',
  bold: 'FbdInter-Bold',
} as const;

/** the light colours of the dots, left to right; the third (warm yellow) is the default */
export const SWATCHES = [
  { name: 'Violet', dot: '#AF3AFA', tint: [0.8, 0.5, 1.0], lin: [0.46, 0.2, 1.0] },
  { name: 'Ice', dot: '#54F7FC', tint: [0.62, 0.96, 1.0], lin: [0.34, 0.86, 1.0] },
  { name: 'Warm', dot: '#F8BC02', tint: [1.0, 0.8, 0.4], lin: [1.0, 0.43, 0.11] },
  { name: 'Amber', dot: '#FF963F', tint: [1.0, 0.6, 0.3], lin: [1.0, 0.3, 0.06] },
  { name: 'Rose', dot: '#FF6B8B', tint: [1.0, 0.55, 0.62], lin: [1.0, 0.24, 0.34] },
] as const;

/** The app's accent follows the light: the dial's band (three stops), the switch and the knob's dot.
 * Warm is the pin's own orange. */
export const ACCENTS = [
  { band: ['#7A52FF', '#9F54FF', '#D884FF'], toggle: '#9B62F4', knob: '#B884FF' },
  { band: ['#2E9BFF', '#37C6F2', '#86F2FF'], toggle: '#33A9E0', knob: '#5CD6F5' },
  { band: ['#FF8344', '#FE923A', '#FFB71C'], toggle: '#EE7E5E', knob: '#FFA13A' },
  { band: ['#FF6A2C', '#FF7F35', '#FFA445'], toggle: '#EF6B44', knob: '#FF8A40' },
  { band: ['#FF4F7C', '#FF6C90', '#FFA0B8'], toggle: '#EE5F82', knob: '#FF7C9C' },
] as const;

/* ---------------------------------------------------------------- the bulb, in its radius R
 Measured down the cord from the socket's top (y down): a brass socket, the threaded cap, the glass
 neck and the globe; inside, an Edison squirrel cage strung between two rings of hooks round a glass
 stem. The WebGPU scene, the Skia fallback and the UI's light all use these. */
export const BULB = {
  socket: 0.5, // socket from 0 to here
  neck: 1.12, // the cap ends and the glass begins
  globe: 2.74, // centre of the globe (radius 1)
  bottom: 3.74,
  filament: 2.55, // centre of the cage, where the light comes from
  depth: 2.4, // how far the bulb hangs in front of the page, in R
} as const;

function hexLin(h: string) {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((c) => {
    const v = c / 255;
    return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  });
}
function linLab(c: number[]) {
  const l = Math.cbrt(0.4122214708 * c[0] + 0.5363325363 * c[1] + 0.0514459929 * c[2]);
  const m = Math.cbrt(0.2119034982 * c[0] + 0.6806995451 * c[1] + 0.1073969566 * c[2]);
  const s = Math.cbrt(0.0883024619 * c[0] + 0.2817188376 * c[1] + 0.6299787005 * c[2]);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}
/** the accents in OKLab, so a colour change glides through vivid hues rather than grey */
export const ACCENT_LAB = ACCENTS.map((a, i) => [...a.band, a.toggle, a.knob, SWATCHES[i].dot].map((c) => linLab(hexLin(c))));
/** which accent colour: the band's three stops, the switch, the knob's dot */
export const A_BAND0 = 0;
export const A_BAND1 = 1;
export const A_BAND2 = 2;
export const A_TOGGLE = 3;
export const A_KNOB = 4;
export const A_DOT = 5;

/** an OKLab colour in linear sRGB */
export function labLin(L: number, a: number, b: number) {
  'worklet';
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  return [
    Math.max(0, 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    Math.max(0, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    Math.max(0, -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
  ];
}
/** the band's middle stop, linear, for a swatch position */
export function accentLin(s: number) {
  'worklet';
  const i = Math.max(0, Math.min(ACCENT_LAB.length - 1.0001, s));
  const a = ACCENT_LAB[Math.floor(i)][1];
  const b = ACCENT_LAB[Math.floor(i) + 1][1];
  const k = i - Math.floor(i);
  return labLin(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k);
}

/** an OKLab colour as an rgb() string */
export function labCss(L: number, a: number, b: number) {
  'worklet';
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  const r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
  const g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
  const bl = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;
  const enc = (v: number) => {
    const c = Math.max(0, Math.min(1, v));
    return Math.round(255 * (c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055));
  };
  return `rgb(${enc(r)},${enc(g)},${enc(bl)})`;
}

/** one accent colour for a swatch position 0 … 4 (continuous while the colour changes) */
export function accentAt(s: number, kind: number) {
  'worklet';
  const i = Math.max(0, Math.min(ACCENT_LAB.length - 1.0001, s));
  const a = ACCENT_LAB[Math.floor(i)][kind];
  const b = ACCENT_LAB[Math.floor(i) + 1][kind];
  const k = i - Math.floor(i);
  return labCss(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k);
}

/** the light's colour (linear, brightest channel 1) for the filament's heat 0 … 1 and a swatch:
 * an incandescent dims towards deep orange and red whatever its colour at full power */
export function lightColor(heat: number, s: number) {
  'worklet';
  const T = (900 + 1800 * Math.pow(Math.max(0, heat), 0.6)) / 100;
  const r = 1;
  const g = Math.max(0, Math.min(1, (99.4708025861 * Math.log(T) - 161.1195681661) / 255));
  const b = T <= 19 ? 0 : Math.max(0, Math.min(1, (138.5177312231 * Math.log(T - 10) - 305.0447927307) / 255));
  const bb = [r, Math.pow(g, 2.2), Math.pow(b, 2.2)];
  const i = Math.max(0, Math.min(SWATCHES.length - 1.0001, s));
  const A = SWATCHES[Math.floor(i)].lin;
  const B = SWATCHES[Math.floor(i) + 1].lin;
  const k = i - Math.floor(i);
  const smart = Math.max(0, Math.min(1, (heat - 0.3) / 0.5));
  const sm = smart * smart * (3 - 2 * smart);
  const out = [0, 0, 0];
  for (let c = 0; c < 3; c++) out[c] = bb[c] + (A[c] + (B[c] - A[c]) * k - bb[c]) * sm;
  const mx = Math.max(out[0], out[1], out[2], 1e-4);
  return [out[0] / mx, out[1] / mx, out[2] / mx];
}
export const DEFAULT_SWATCH = 2;
export const DEFAULT_LEVEL = 0.78;

/** the pin's geometry, in its points */
export const PIN = {
  w: 402,
  top: 43, // the header's top edge
  back: { x: 43, y: 65 },
  title: { y: 65 },
  card: { x: 29, y: 121, w: 344, h: 135, r: 24 },
  name: { x: 54, y: 160 },
  toggle: { x: 54, y: 196, w: 82, h: 38 },
  clock: { x: 168, y: 198, d: 34 },
  chip: { y: 313, h: 50, w: 108, l: 50, r: 244 },
  tab: { x: -26, y: 322, w: 92, h: 250, r: 32 },
  bulb: { top: 364, bottom: 541 },
  arc: { cx: 202, cy: 724, rOut: 130, rIn: 82, leg: 22 },
  dial: { r: 46, dy: -12 },
  dots: { y: 831, xs: [62, 125, 201, 278, 340], d: 12 },
} as const;

export type Layout = {
  w: number;
  h: number;
  sx: number;
  k: number;
  backY: number;
  card: { x: number; y: number; w: number; h: number };
  nameY: number;
  toggleY: number;
  chipY: number;
  chips: { l: number; r: number; y: number; w: number; h: number };
  tabY: number;
  tabH: number;
  bulbTop: number;
  /** where the cord leaves the card and the socket's top at rest */
  anchorX: number;
  anchorY: number;
  restY: number;
  cord: number;
  R: number; // the glass bulb's radius
  arcCx: number;
  arcCy: number;
  rOut: number;
  rIn: number;
  leg: number;
  dotsY: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const sx = w / PIN.w;
  const y0 = top;
  const y1 = h - Math.max(bottom, 16) - 14;
  const k = (y1 - y0) / (PIN.dots.y - PIN.top);
  const Y = (y: number) => y0 + (y - PIN.top) * k;
  const bulbTop = Y(PIN.bulb.top);
  // the bulb is as wide as the pin's shade is, give or take: its glass ends a little below the rim
  const R = (Y(PIN.bulb.bottom) - bulbTop) / 3.0;
  return {
    w,
    h,
    sx,
    k,
    backY: Y(PIN.back.y),
    card: { x: PIN.card.x * sx, y: Y(PIN.card.y), w: PIN.card.w * sx, h: PIN.card.h * k },
    nameY: Y(PIN.name.y),
    toggleY: Y(PIN.toggle.y),
    chipY: Y(PIN.chip.y),
    chips: { l: PIN.chip.l * sx, r: PIN.chip.r * sx, y: Y(PIN.chip.y), w: PIN.chip.w * sx, h: PIN.chip.h * k },
    tabY: Y(PIN.tab.y),
    tabH: PIN.tab.h * k,
    bulbTop,
    anchorX: w / 2,
    anchorY: Y(PIN.card.y) + PIN.card.h * k,
    restY: bulbTop - BULB.socket * R,
    cord: bulbTop - BULB.socket * R - (Y(PIN.card.y) + PIN.card.h * k),
    R,
    arcCx: PIN.arc.cx * sx,
    arcCy: Y(PIN.arc.cy),
    rOut: PIN.arc.rOut * sx,
    rIn: PIN.arc.rIn * sx,
    leg: PIN.arc.leg * k,
    dotsY: Y(PIN.dots.y),
  };
}

/** the x of a swatch position 0 … 4 along the dots (continuous) */
export function dotX(sx: number, s: number) {
  'worklet';
  const i = Math.max(0, Math.min(PIN.dots.xs.length - 1.0001, s));
  const a = Math.floor(i);
  return (PIN.dots.xs[a] + (PIN.dots.xs[a + 1] - PIN.dots.xs[a]) * (i - a)) * sx;
}

/** the knob's travel is the light's output, the readout its perceived intensity: travel = level^2.1
 * (the pin's knob sits at 58 % of the band for "78%") */
export const GAMMA = 2.1;

/** a point on the intensity arc for a level 0 … 1, at the middle of the band: the band runs from the
 * bottom of the left leg, over the top, to the bottom of the right leg */
export function arcPoint(L: Layout, level: number) {
  'worklet';
  const rm = (L.rOut + L.rIn) / 2;
  const legLen = L.leg;
  const half = Math.PI * rm;
  const total = legLen * 2 + half;
  const s = Math.pow(Math.max(0, Math.min(1, level)), GAMMA) * total;
  if (s < legLen) return { x: L.arcCx - rm, y: L.arcCy + legLen - s, a: Math.PI };
  if (s > legLen + half) return { x: L.arcCx + rm, y: L.arcCy + (s - legLen - half), a: 0 };
  const a = Math.PI - ((s - legLen) / half) * Math.PI;
  return { x: L.arcCx + rm * Math.cos(a), y: L.arcCy - rm * Math.sin(a), a };
}

/** the level for a touch point on the arc (nearest point of the band's centre line) */
export function levelAt(L: Layout, x: number, y: number) {
  'worklet';
  const rm = (L.rOut + L.rIn) / 2;
  const legLen = L.leg;
  const half = Math.PI * rm;
  const total = legLen * 2 + half;
  let s;
  if (y > L.arcCy) {
    const down = Math.min(legLen, y - L.arcCy);
    s = x < L.arcCx ? legLen - down : legLen + half + down;
  } else {
    const a = Math.atan2(L.arcCy - y, x - L.arcCx); // 0 right … π left
    s = legLen + ((Math.PI - Math.max(0, Math.min(Math.PI, a))) / Math.PI) * half;
  }
  return Math.pow(Math.max(0, Math.min(1, s / total)), 1 / GAMMA);
}
