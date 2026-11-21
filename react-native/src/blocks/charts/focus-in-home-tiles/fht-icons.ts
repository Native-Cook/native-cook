// Focus-In Home Tiles: the glyphs and the temperature figures, as paths from the delivered SVG masters
// (assets/*.svg). Icons are 24 pt boxes drawn with a 2 pt round stroke; figures are 48 × 76 boxes with a 3.4 pt
// stroke (cap line y 10, baseline y 65).

import { Skia, type SkPath } from '@shopify/react-native-skia';

const D = {
  power: 'M12 3 V11 M7.4 5.6 A8 8 0 1 0 16.6 5.6',
  panel: 'M4.5 3.5 H19.5 V20.5 H4.5 Z M11.5 3.5 V20.5 M11.5 13 H19.5',
  auto: 'M3.5 2.5 H20.5 V21.5 H3.5 Z M7.5 17 L12 7 L16.5 17 M9 14 H15',
  timer: 'M9 2 H15 M12 2 V5 M18 5 L19.5 6.5 M12 6 A8 8 0 1 1 11.99 6 M12 14 L16 10',
  lock: 'M7 10 V7 A5 5 0 0 1 17 7 V10 M5 10 H19 V21 H5 Z M12 14 V17',
  calculator: 'M5 2.5 H19 V21.5 H5 Z M8 6 H16 M8 10 V11 M12 10 V11 M16 10 V11 M8 14 V15 M12 14 V15 M16 14 V18 M8 18 H12',
  headset: 'M4 13 V11 A8 8 0 0 1 20 11 V16 M4 11 H7 V16 H4 Z M17 11 H20 V16 H17 Z M5 17 C6 20 8 21 10 21 M10 21 H12',
  hourglass: 'M5 3 H19 M5 21 H19 M6 3 C6 8 8 9 12 12 C16 15 18 16 18 21 M18 3 C18 8 16 9 12 12 C8 15 6 16 6 21',
  ecoLeaf: 'M12 3 C6 7 3.5 11 5 15 C6 18 9 20 12 21 C15 20 18 18 19 15 C20.5 11 18 7 12 3 Z M12 8 V21 M8.5 12 L12 15.5 L15.5 12',
  frost:
    'M12 2 V22 M3.34 7 L20.66 17 M3.34 17 L20.66 7 M8.5 3.5 L12 7 L15.5 3.5 M8.5 20.5 L12 17 L15.5 20.5 M3 11 L7.5 9.4 L7.4 5 M16.6 19 L16.5 14.6 L21 13 M3 13 L7.5 14.6 L7.4 19 M16.6 5 L16.5 9.4 L21 11',
  bell: 'M4 18 H20 L18 15 V10 A6 6 0 0 0 6 10 V15 Z M10 21 H14 M12 2 V4',
  chevronUp: 'M5 15.5 L12 8.5 L19 15.5',
  chevronDown: 'M5 8.5 L12 15.5 L19 8.5',
  boltSmall: 'M10 3 H17 L14 9 H20 L9 21 L11 13 H5 Z',
  hourglassSmall: 'M5 3 H19 M5 21 H19 M6 3 C6 8 9 10 12 12 C15 14 18 16 18 21 M18 3 C18 8 15 10 12 12 C9 14 6 16 6 21',
} as const;

export type IconName = keyof typeof D;

const path = (d: string): SkPath => {
  const p = Skia.Path.MakeFromSVGString(d);
  if (!p) throw new Error(`Focus-In Home Tiles: bad path ${d.slice(0, 24)}`);
  return p;
};

export const ICON = Object.fromEntries(Object.entries(D).map(([k, d]) => [k, path(d)])) as Record<IconName, SkPath>;

/** Figures 0–9 (48 × 76 box) and, at index 10, the optical degree ring (32 × 76 box): SVG strings, so a worklet can pick one. */
export const DIGIT_D: string[] = [
  'M24 10 C9 10 4 20 4 37.5 C4 55 9 65 24 65 C39 65 44 55 44 37.5 C44 20 39 10 24 10 Z',
  'M11 22 L27 10 V65 M11 65 H43',
  'M4 21 C5 13 13 10 24 10 C37 10 44 14 44 23 C44 30 40 34 32 41 L4 65 H44',
  'M5 14 C12 10 19 10 25 10 C38 10 44 15 44 23 C44 31 36 36 25 36 M25 36 C38 36 44 42 44 50 C44 60 37 65 24 65 C16 65 9 64 4 60',
  'M34 65 V10 L4 49 H44',
  'M43 10 H8 L5 36 C11 32 17 31 24 31 C38 31 44 38 44 48 C44 60 36 65 24 65 C16 65 9 63 4 59',
  'M41 14 C36 11 31 10 25 10 C10 10 4 21 4 39 C4 57 10 65 25 65 C37 65 44 58 44 47 C44 37 37 31 25 31 C15 31 9 34 4 40',
  'M4 10 H44 L17 65',
  'M24 10 C11 10 5 15 5 23 C5 31 12 36 24 36 C37 36 44 42 44 51 C44 60 37 65 24 65 C11 65 4 60 4 51 C4 42 11 36 24 36 C36 36 43 31 43 23 C43 15 37 10 24 10 Z',
  'M7 61 C12 64 17 65 23 65 C38 65 44 54 44 36 C44 18 38 10 23 10 C11 10 4 17 4 28 C4 38 11 44 23 44 C33 44 39 41 44 35',
  'M25.1 23 A11.1 11.1 0 1 1 2.9 23 A11.1 11.1 0 1 1 25.1 23',
];
