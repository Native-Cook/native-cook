/** Reference: Dribbble d27719748, three fixed tiles at 5.2s. Coordinates in pt. */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    page: '#BEB1B1',
    ink: '#F8F5EB',
    inkDark: '#172326',
    marker: '#F4EBC1',
    route: '#E6E9DF',
    cool: ['#0E2A8A', '#5B8CFF', '#BFE9E6'],
    steady: ['#14232A', '#347F88', '#C8B5B5', '#E58D64'],
    hot: ['#101820', '#1C8C8A', '#F46A2E', '#FFD9A0', '#FFF4E0'],
  },
  type: { display: { family: 'TGT-UrbanistLight', size: 40, tracking: -0.9 }, small: 28, label: 13, note: 11, ui: 'TGT-Urbanist' },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { large: 72, circle: 71, small: 40 },
  light: { az: 315, el: 40, kelvin: 5600, color: '#F8F5EB', intensity: 1 },
  material: { frost: 0.3, labelFrost: 0.46, feather: 12, refraction: 14, dispersion: 1.5, lens: 0.06, dome: 22, rim: 1, shadow: 2 },
  field: {
    grain: 0.04,
    wake: 20,
    peakWake: 60,
    fingerprintRadius: 46,
    fingerprintHeat: 0.12,
    runnerHeight: 0.7,
    // The runner is drawn 1.17x about y 250 so it fills the portrait frame: head just under the status bar, feet on the warm
    // ground band above the home indicator (it used to stop at y 700 and leave the lower third an empty gradient).
    figure: { x: 201, y: 250, k: 1.173 },
  },
} as const;
export const sys = {
  surface: { page: ref.color.page },
  ink: { primary: ref.color.ink, dark: ref.color.inkDark },
  accent: { base: ref.color.marker },
  stroke: { hairline: ref.color.route },
  hero: { light: ref.light, material: ref.material, field: ref.field },
  font: ref.type,
} as const;
export const comp = {
  large: { x: 51, y: 196, w: 300, h: 300, r: 72 },
  contact: { x: 51, y: 512, w: 142, h: 142, r: 71 },
  vertical: { x: 209, y: 512, w: 142, h: 142, r: 40 },
  route: { x: 130, y: 240, w: 135, h: 107, stroke: 0.8, grab: 28 },
  labelWells: { large: [51, 350, 300, 180] as const, contact: [73, 526, 98, 80] as const, vertical: [222, 525, 116, 114] as const },
  text: {
    run: [79, 408],
    distance: [79, 453],
    unit: [181, 453],
    stats: [323, 404, 427, 450],
    contactLabel: [122, 541],
    contactValue: [91, 599],
    contactUnit: [154, 598],
    verticalLabel: [280, 541],
    verticalValue: [230, 632],
    verticalUnit: [298, 632],
  },
  gauge: { contactX: 122, contactY: 629, contactR: 52, verticalX: 280, verticalY: 625, verticalR: 54, stroke: 1, dot: 1.7 },
} as const;
export const COPY = {
  run: 'Easy Run',
  contact: 'Contact time',
  vertical: 'Vertical oscillation',
  km: 'KM',
  mi: 'MI',
  ms: 'MS',
  cm: 'CM',
} as const;
export type TileRect = { x: number; y: number; w: number; h: number; r: number };
/** The three-tile cluster sits on the frame's optical centre (y 447 of 874, between the safe areas); safe area constrains it on short frames. */
export function makeLayout(w: number, h: number, top = 62, bottom = 34) {
  const s = Math.min(w / REF.w, Math.max(0.1, (h - top - bottom - 24) / 458));
  const ox = (w - REF.w * s) / 2;
  const ideal = h * (447 / REF.h) - 425 * s;
  const oy = Math.max(top + 12 - comp.large.y * s, Math.min(ideal, h - bottom - 12 - 654 * s));
  return { w, h, s, ox, oy, x: (v: number) => ox + v * s, y: (v: number) => oy + v * s };
}

export type Metric =
  'run' | 'distance' | 'unit' | 'elapsed' | 'bpm' | 'pace' | 'contactLabel' | 'contact' | 'ms' | 'verticalLabel' | 'oscillation' | 'cm';
export const TEXT_ITEMS: { id: Metric; x: number; y: number; size: number; light?: boolean; align?: 'center' | 'right'; width: number }[] =
  [
    { id: 'run', x: 79, y: 408, size: 13, width: 130 },
    { id: 'distance', x: 79, y: 453, size: 40, light: true, width: 142 },
    { id: 'unit', x: 194, y: 453, size: 11, width: 24 },
    { id: 'elapsed', x: 323, y: 404, size: 11, align: 'right', width: 75 },
    { id: 'bpm', x: 323, y: 427, size: 11, align: 'right', width: 75 },
    { id: 'pace', x: 323, y: 450, size: 11, align: 'right', width: 75 },
    { id: 'contactLabel', x: 122, y: 541, size: 11, align: 'center', width: 120 },
    { id: 'contact', x: 89, y: 599, size: 28, light: true, width: 89 },
    { id: 'ms', x: 160, y: 598, size: 11, width: 21 },
    { id: 'verticalLabel', x: 280, y: 541, size: 11, align: 'center', width: 122 },
    { id: 'oscillation', x: 230, y: 632, size: 28, light: true, width: 95 },
    { id: 'cm', x: 315, y: 632, size: 11, width: 20 },
  ];
