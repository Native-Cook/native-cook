/**
 * The feed: six places, in grid order (left column top to bottom, then the right). Credits are in the block's notes.
 * The collection "Slow coast" already holds three of them; the lake is the one saved in the demo.
 */
export type Photo = { id: string; title: string; place: string; src: number };

export const PHOTOS: Photo[] = [
  { id: 'boardwalk', title: 'Boardwalk to the dunes', place: 'Sylt', src: require('./assets/boardwalk.jpg') },
  { id: 'village', title: 'Last light on the cliff', place: 'Oia', src: require('./assets/village.jpg') },
  { id: 'road', title: 'Road into the valley', place: 'Utah', src: require('./assets/road.jpg') },
  { id: 'pool', title: 'Pool under the palms', place: 'Bali', src: require('./assets/pool.jpg') },
  { id: 'lake', title: 'Still water, early', place: 'Alberta', src: require('./assets/lake.jpg') },
  { id: 'shore', title: 'Shoreline from above', place: 'Maldives', src: require('./assets/shore.jpg') },
];

export const COLLECTION = {
  name: 'Slow coast',
  /** What it held before the save, newest first: shore, village, pool. */
  before: [5, 1, 3],
} as const;

/** Small line glyphs on a 24 pt grid, stroked. */
export const GLYPH = {
  home: 'M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z',
  search: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13ZM15.2 15.2 20 20',
  inbox: 'M4 13.5 6.2 6A1.5 1.5 0 0 1 7.6 5h8.8a1.5 1.5 0 0 1 1.4 1l2.2 7.5V18a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 13.5h4.5l1.2 2h4.6l1.2-2H20',
  bookmark: 'M7 4.5h10a1 1 0 0 1 1 1V20l-6-4.2L6 20V5.5a1 1 0 0 1 1-1Z',
  close: 'M7 7l10 10M17 7 7 17',
} as const;
