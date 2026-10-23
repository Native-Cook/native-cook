/**
 * Strolls, a walking-routes app, and its first screen. Cream paper, forest-green ink and one orange for the route.
 * The next screen (the map) lies under the first one and is only seen through a glass dome at the bottom edge, which
 * magnifies it like a lens; pull the dome up and it becomes a magnifier you can move over the map, let go and it opens
 * out into the map screen. Inter only. Sizes on a 4 pt grid, 24 pt margins on the first screen, 16 pt on the map.
 */
export const COLOR = {
  page: '#F4EFE6',
  ink: '#1D2B22',
  mute: '#5E6A61', // 5.4:1 on the page
  brand: '#2F5D45',
  // The map.
  land: '#EFE9DD',
  block: '#E6DFD0',
  park: '#CFE0BE',
  parkEdge: '#B9D1A4',
  water: '#B9D6E3',
  waterEdge: '#A3C7D8',
  street: '#FFFFFF',
  streetCase: '#D9D0BE',
  major: '#F6DDA8',
  majorCase: '#E2C384',
  label: '#7A7466',
  route: '#E8642E',
  routeCase: '#FFFFFF',
  pin: '#1D2B22',
  card: '#FFFFFF',
  cardEdge: 'rgba(29,43,34,0.08)',
  glassFill: 'rgba(255,255,255,0.72)',
  shadow: 'rgba(29,43,34,0.16)',
  go: '#2F5D45',
  onGo: '#FFFFFF',
  finger: 'rgba(29,43,34,0.12)',
  fingerEdge: 'rgba(29,43,34,0.3)',
} as const;

export const TYPE = {
  word: { face: 'semibold', size: 20 },
  headline: { face: 'semibold', size: 34, line: 40 },
  body: { face: 'regular', size: 16, line: 22 },
  hint: { face: 'medium', size: 13 },
  street: { face: 'medium', size: 10 },
  hello: { face: 'regular', size: 14 },
  title: { face: 'semibold', size: 24 },
  search: { face: 'regular', size: 15 },
  cardTitle: { face: 'semibold', size: 18 },
  cardMeta: { face: 'regular', size: 13 },
  chip: { face: 'medium', size: 12 },
  button: { face: 'semibold', size: 15 },
} as const;
export type TypeRole = keyof typeof TYPE;

export const LAYOUT = {
  margin: 24,
  edge: 16,
  /** The first screen, below the safe area: the wordmark, then the headline and its line under it. */
  brandY: 20,
  headlineY: 108,
  /** The dome: a disc of this radius whose centre sits this far below the bottom edge. */
  domeR: 172,
  domeSink: 52,
  /** Pulled up, the dome becomes a magnifier this size. */
  lensR: 116,
  /** The lens enlarges the map this much at rest, less as it opens out. */
  mag: 1.45,
  /** The map screen's chrome. */
  searchY: 12,
  searchH: 48,
  cardH: 164,
  back: 44,
  finger: 44,
} as const;

export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const domeY = height + LAYOUT.domeSink;
  // Opening out covers the whole screen from wherever the lens is.
  const cover = Math.hypot(width, height);
  return {
    width, height, top, bottom,
    domeX: width / 2,
    domeY,
    /** The lens's resting place once pulled up: where the demo carries it. */
    lensY: height * 0.5,
    cover,
    cardY: height - Math.max(bottom, LAYOUT.edge) - 8 - LAYOUT.cardH,
  };
}
export type Layout = ReturnType<typeof makeLayout>;
