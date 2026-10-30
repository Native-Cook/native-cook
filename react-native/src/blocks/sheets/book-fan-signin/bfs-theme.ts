// Book Fan Sign-in — layout, colour and copy, measured on the pin's two phones (402 × 874 pt each,
// the home page on the left and the sign-in sheet on the right).

export const REF_W = 402;
export const REF_H = 874;

export const C = {
  page: '#FFFFFF',
  ink: '#0E0E10',
  grey: '#8A8A8E',
  greyLight: '#9A9A9F',
  field: '#F5F5F6',
  chip: '#F3F3F4',
  chipInk: '#6E6E73',
  pink: '#E0457B',
  pinkSoft: '#FCE7EF',
  black: '#121212',
  dim: 'rgba(0, 0, 0, 0.42)',
};

export const COPY = {
  logo: 'retold',
  signIn: 'Sign in',
  tagline: 'Read less, remember more',
  title: ['Which book is', 'on your mind?'],
  sub: ['Turn the best ideas from great books into lessons', 'you can actually use.'],
  search: 'Search books or authors',
  chips: ['For you', 'Most popular', 'Top picks', 'Best sellers'],
  recommended: 'Recommended for you',
  topPicks: 'Top picks',
  books: [
    { cover: 3, title: ['Small Steps,', 'Long Roads'], author: 'Mira Okafor' },
    { cover: 7, title: ['The Lighthouse', 'Habit'], author: 'Jonas Feld' },
    { cover: 0, title: ['Before the Sun', 'Gets Up'], author: 'Ines Varga' },
  ],
  tag: 'Your Books, Put Into Practice',
  sheetTitle: ["Books Don't Change You,", 'Practice Does.'],
  sheetSub: ['Turn any non-fiction book into interactive exercises', 'you actually do. Stop reading. Start applying.', 'Make knowledge stick forever.'],
  cta: 'Continue with Email',
};

export const A = {
  logo: { x: 22, y: 90, size: 30 },
  signIn: { x: 290, y: 72, w: 87, h: 36 },
  tagline: { x: 22, y: 309, size: 15.2 },
  title: { x: 22, y: [344, 383], size: 35 },
  sub: { x: 22, y: [420, 443], size: 15 },
  search: { x: 22, y: 476, w: 356, h: 42 },
  chips: { y: 538, h: 37, x: [22, 115, 240, 348], w: [80, 113, 91, 104] },
  recommended: { x: 22, y: 615 },
  books: { y: 636, w: 87, h: 116, x: [31, 159, 285], titleY: 770, authorY: 788 },
  topPicks: { y: 828 },
  nextRow: { y: 851 },
};

/** The tilted wall of covers behind the header: centre, turn, cover size and pitch. */
export const WALL = { cx: 214, cy: 176, angle: -27, w: 60, h: 86, gapX: 9, gapY: 10, cols: 7, rows: 6, fadeIn: 46, fadeOut: 150 };

export const B = {
  sheet: { x: 12, y: 418, w: 378, h: 442, r: 42 },
  strip: { y: 446, angle: -17, w: 62, h: 88, pitch: 76, x0: 52 },
  stripFade: { from: 448, to: 500 },
  icon: { x: 170, y: 452, s: 62, r: 15 },
  tag: { x: 113, y: 557, w: 176, h: 24 },
  title: { y: [601, 636], size: 30 },
  sub: { y: [672, 691, 709], size: 14.2 },
  cta: { x: 36, y: 755, w: 330, h: 57 },
};

/** Ten invented covers in a 5 × 2 atlas of 300 × 450 px. */
export const COVERS = { n: 10, cols: 5, w: 300, h: 450 };
