/** Source measurements and Pro-layer overrides: .work/token-fusion-splash/design.md. */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    page: '#232325',
    ring: '#2C2C2F',
    core: '#9B7BEF',
    shade: '#6A5CE6',
    hot: '#EDE4FF',
    ink: '#FDFBFF',
    orange: '#F7931A',
    blue: '#3E9BE9',
    green: '#26A17B',
    navy: '#1B2A8F',
  },
  type: { wordmark: { family: 'TFSOutfit', size: 30, lineHeight: 40, tracking: -0.02, weight: '400' } },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { token: 22, core: 55, poster: 120 },
  light: { az: 315, el: 42, kelvin: 5600, intensity: 1 },
  material: { roughness: 0.78, grain: 0.025, aa: 1, glow: 14, heatLag: 2 / 60, dyeSeconds: 2.4 },
} as const;
export const sys = {
  surface: { page: ref.color.page },
  ink: { primary: ref.color.ink },
  accent: { base: ref.color.core },
  token: { orange: '#D67C16', blue: '#3B97E3' }, // Contrast correction: white glyphs >=3:1 (source flats retained in ref).
  hero: { hot: ref.color.hot, shade: ref.color.shade, light: ref.light },
  stroke: { ring: ref.color.ring },
} as const;
export const comp = {
  core: { x: 201, y: 400, r: ref.radius.core, posterR: ref.radius.poster, mark: 60, smallMark: 40 },
  orbit: { wideX: 176, wideY: 150, tightX: 150, tightY: 118, tilt: -18, angles: [-125, -35, 65, 155] },
  rings: [162, 240, 320, 406],
  wordmark: { y: 470, rise: 8, maxWidth: 338 },
  touch: { target: 48, coreLimit: 90, edge: 12, backInset: 20 },
  atlas: { cell: 128, cells: 7, spread: 16, padding: 16 },
} as const;
export const COPY = { wordmark: 'korra', skip: 'Skip account linking', core: 'Korra core. Drag to collect tokens.' } as const;
// Original vectors, coordinates 0…100; paths can be replaced with any SkPath or SVG path.
export const MARK = 'M12 14 L32 14 L32 41 L62 14 L89 14 L51 48 L90 86 L62 86 L32 56 L32 86 L12 86 Z';
export const DEFAULT_TOKENS = [
  {
    id: 'ember',
    label: 'Ember token',
    color: sys.token.orange,
    glyph: 'M50 12 A38 38 0 1 1 49.99 12 Z M50 24 A26 26 0 1 0 50.01 24 Z M50 39 A11 11 0 1 1 49.99 39 Z',
  },
  { id: 'prism', label: 'Prism token', color: sys.token.blue, glyph: 'M50 9 L80 50 L50 91 L20 50 Z M50 29 L35 50 L50 70 L65 50 Z' },
  {
    id: 'tide',
    label: 'Tide token',
    color: ref.color.green,
    glyph:
      'M15 25 Q32 13 50 25 T85 25 L85 38 Q67 50 50 38 T15 38 Z M15 48 Q32 36 50 48 T85 48 L85 61 Q67 73 50 61 T15 61 Z M15 71 Q32 59 50 71 T85 71 L85 84 Q67 96 50 84 T15 84 Z',
  },
  {
    id: 'arc',
    label: 'Arc token',
    color: ref.color.navy,
    glyph: 'M50 8 A42 42 0 1 1 49.99 8 Z M50 17 A33 33 0 1 0 50.01 17 Z M29 44 L41 44 L41 57 L67 30 L76 39 L41 76 L29 65 Z',
  },
] as const;
export function makeLayout(width: number, height: number, top: number = REF.safeTop, bottom: number = REF.safeBottom) {
  const scale = Math.min(width / REF.w, (height - top - bottom) / 550);
  const cy = Math.max(top + 200 * scale, Math.min((height * comp.core.y) / REF.h, height - bottom - 200 * scale));
  return { width, height, scale, cx: width / 2, cy, offsetX: width / 2 - comp.core.x * scale, offsetY: cy - comp.core.y * scale };
}
export function rgb(hex: string): number[] {
  const h = hex.replace('#', '');
  const full =
    h.length === 3
      ? h
          .split('')
          .map((c) => c + c)
          .join('')
      : h;
  const n = Number.parseInt(full, 16);
  return Number.isFinite(n) ? [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255] : [0.6, 0.48, 0.94];
}
