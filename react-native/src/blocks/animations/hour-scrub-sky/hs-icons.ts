// Hour Scrub Sky: vector glyphs on a 24-unit grid (filled tab icons, 2 pt strokes elsewhere), parsed once.

import { FillType, Skia, type SkPath } from '@shopify/react-native-skia';

const P = (d: string, evenOdd = false): SkPath => {
  const p = Skia.Path.MakeFromSVGString(d);
  if (!p) throw new Error(`Hour Scrub Sky: bad icon path ${d.slice(0, 24)}`);
  return evenOdd ? Skia.PathBuilder.MakeFromPath(p).setFillType(FillType.EvenOdd).detach() : p;
};

/** Location arrow (filled): Nearby tab and the location row. */
export const NAV = P('M20.3 3.2 C20.9 3.0 21.2 3.5 20.9 4.0 L13.9 20.6 C13.6 21.4 12.5 21.3 12.4 20.5 L11.2 13.0 L3.6 11.7 C2.8 11.6 2.7 10.5 3.5 10.2 Z');
/** Folded map (filled, three panels): Cities. */
export const MAP = P(
  'M3.0 6.6 C3.0 6.2 3.2 5.9 3.6 5.8 L7.8 4.0 L7.8 18.1 L3.8 19.8 C3.4 20.0 3.0 19.7 3.0 19.2 Z ' +
    'M9.3 4.0 L14.7 6.1 L14.7 20.0 L9.3 18.0 Z ' +
    'M16.2 6.0 L20.2 4.3 C20.6 4.1 21.0 4.4 21.0 4.9 V17.4 C21.0 17.8 20.8 18.1 20.4 18.2 L16.2 20.0 Z',
);
/** Trophy (filled cup, stem and base; handles as strokes). */
export const TROPHY = P(
  'M6.8 3.2 H17.2 V9.0 C17.2 12.0 14.9 14.3 12.0 14.3 C9.1 14.3 6.8 12.0 6.8 9.0 Z ' +
    'M10.9 14.0 H13.1 V17.0 H10.9 Z ' +
    'M8.2 17.0 H15.8 C16.5 17.0 17.0 17.5 17.0 18.2 V20.8 H7.0 V18.2 C7.0 17.5 7.5 17.0 8.2 17.0 Z',
);
export const TROPHY_HANDLES = P('M7.0 5.2 H4.2 V7.0 C4.2 9.0 5.5 10.6 7.4 10.9 M17.0 5.2 H19.8 V7.0 C19.8 9.0 18.5 10.6 16.6 10.9');
/** Ellipsis in a filled circle (the dots are cut out): More. */
export const MORE = P(
  'M12 2.5 A9.5 9.5 0 1 1 11.99 2.5 Z ' +
    'M7.6 10.6 A1.45 1.45 0 1 0 7.61 10.6 Z M12 10.6 A1.45 1.45 0 1 0 12.01 10.6 Z M16.4 10.6 A1.45 1.45 0 1 0 16.41 10.6 Z',
  true,
);
/** Chip: leaf with a slash (pollen off) and sun behind a cloud. */
export const LEAF = P('M5.2 18.8 C5.0 11.6 9.6 5.6 18.9 5.1 C19.4 14.2 13.8 18.9 6.6 18.9 M5.2 18.8 L11.6 12.4');
export const SLASH = P('M3.8 3.8 L20.2 20.2');
export const SUN_DISC = P('M9.0 5.6 A3.4 3.4 0 1 1 8.99 5.6 Z');
export const SUN_RAYS = P('M9.0 1.8 V3.0 M3.9 3.9 L4.8 4.8 M1.8 9.0 H3.0 M14.1 3.9 L13.2 4.8 M3.9 14.1 L4.8 13.2');
export const CLOUD = P(
  'M8.6 20.0 H17.6 C19.6 20.0 21.2 18.4 21.2 16.4 C21.2 14.5 19.8 13.0 17.9 12.8 C17.3 10.8 15.5 9.4 13.4 9.4 C10.9 9.4 8.9 11.3 8.7 13.7 C7.0 13.9 5.7 15.3 5.7 17.0 C5.7 18.7 7.0 20.0 8.6 20.0 Z',
);
/** Arrow pill: a 16 pt arrow pointing right (pt, centred on 0,0). */
export const ARROW = P('M-8 0 H8 M2.2 -6 L8 0 L2.2 6');
/** Play disc glyphs (pt, centred on 0,0). */
export const PLAY = P('M-3.4 -5.6 C-3.4 -6.3 -2.7 -6.7 -2.1 -6.3 L6.2 -0.9 C6.8 -0.5 6.8 0.4 6.2 0.8 L-2.1 6.2 C-2.7 6.6 -3.4 6.2 -3.4 5.5 Z');
export const PAUSE = P('M-5.2 -5.6 C-5.2 -6.1 -4.8 -6.5 -4.3 -6.5 H-2.3 C-1.8 -6.5 -1.4 -6.1 -1.4 -5.6 V5.6 C-1.4 6.1 -1.8 6.5 -2.3 6.5 H-4.3 C-4.8 6.5 -5.2 6.1 -5.2 5.6 Z M1.4 -5.6 C1.4 -6.1 1.8 -6.5 2.3 -6.5 H4.3 C4.8 -6.5 5.2 -6.1 5.2 -5.6 V5.6 C5.2 6.1 4.8 6.5 4.3 6.5 H2.3 C1.8 6.5 1.4 6.1 1.4 5.6 Z');
export const RESET = P('M-4.6 -3.6 A6 6 0 1 1 -6.0 1.2 M-4.6 -3.6 L-5.0 -7.4 M-4.6 -3.6 L-0.9 -3.4');
