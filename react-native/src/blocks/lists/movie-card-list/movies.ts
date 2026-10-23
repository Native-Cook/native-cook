import type { ImageSourcePropType } from 'react-native';

// Three invented films in the pin's order: the hand starts on the middle one.
export type Movie = {
  id: string;
  title: string;
  genres: string[];
  rating: number;
  stars: number;
  director: string;
  intro: string;
  poster: ImageSourcePropType;
  backdrop: ImageSourcePropType;
  cast: { name: string; photo: ImageSourcePropType }[];
  // Title lockups drawn over the art, as the real posters print them.
  lockup: { kicker?: string; title: string[]; tagline?: string; date?: string; tone: 'jester' | 'buds' | 'grift' };
  backdropColor: string;
  // The generated poster's face sits left of centre; the card frames it the way the pin's poster is framed.
  posterZoom?: { scale: number; x: number; y: number };
};

export const MOVIES: Movie[] = [
  {
    id: 'buds', title: 'Best Buds', genres: ['Comedy', 'Family', 'Teen'], rating: 8.1, stars: 4,
    director: 'Director / Sam Whitlow',
    intro: 'Three sixth-graders skip school to replace a drone they broke, and their one-day trip across town turns into the wildest weekend of their lives.',
    poster: require('./assets/poster-buds.jpg'), backdrop: require('./assets/backdrop-buds.jpg'),
    cast: [
      { name: 'Milo Hart', photo: require('./assets/cast-buds-1.jpg') },
      { name: 'Ezra Quinn', photo: require('./assets/cast-buds-2.jpg') },
      { name: 'Dean Porter', photo: require('./assets/cast-buds-3.jpg') },
    ],
    lockup: { title: ['BEST', 'BUDS'], tone: 'buds' }, backdropColor: '#E3221C',
  },
  {
    id: 'jester', title: 'Drift', genres: ['Action', 'Drama', 'History'], rating: 9.0, stars: 4,
    director: 'Director / Nora Kessler',
    intro: 'The film “Drift” follows a night-shift ferry pilot who finds a stranger’s phone on board and is pulled into the city’s underworld. Released by Northlight Pictures this October.',
    poster: require('./assets/poster-jester.jpg'), backdrop: require('./assets/backdrop-jester.jpg'),
    cast: [
      { name: 'Mason Vale', photo: require('./assets/cast-jester-1.jpg') },
      { name: 'Robert Delane', photo: require('./assets/cast-jester-2.jpg') },
      { name: 'Zara Bennett', photo: require('./assets/cast-jester-3.jpg') },
    ],
    lockup: { kicker: 'MASON VALE', title: ['DRIFT'], tagline: 'PUT ON A BRAVE FACE', date: 'OCTOBER 4', tone: 'jester' },
    backdropColor: '#050807',
  },
  {
    id: 'grift', title: 'The Grift', genres: ['Comedy', 'Crime', 'Romance'], rating: 7.6, stars: 4,
    director: 'Director / Lena Ortiz',
    intro: 'Two rival con artists on the Riviera team up to fleece a tech millionaire, and soon discover that the mark is playing them both.',
    poster: require('./assets/poster-grift.jpg'), backdrop: require('./assets/backdrop-grift.jpg'),
    cast: [
      { name: 'Ava Sinclair', photo: require('./assets/cast-grift-1.jpg') },
      { name: 'Rose Hadley', photo: require('./assets/cast-grift-2.jpg') },
      { name: 'Victor Lang', photo: require('./assets/cast-grift-3.jpg') },
    ],
    lockup: { title: ['THE GRIFT'], tone: 'grift' }, backdropColor: '#C9A23A',
  },
];

export const START_PAGE = 1;
export const SCREEN_STILL = require('./assets/screen-jester.jpg');

// Seat map: 10 rows; a 2-seat block, the 8-seat centre block and a 2-seat block.
// 'A' free, 'T' taken. Read from the pin's map.
export const SEAT_ROWS = [
  // Read from the pin rectified on its own UI (homography): two seats on the left run straight into the
  // seven of the middle block, one aisle, then two on the right.
  'TTAAAAAAA TT',
  'TTAAAAAAA TT',
  'TTTTTTTTT TT',
  'TTTTTTTTT TT',
  'TTTTTTTTT TT',
  'TTTTTTTTT TT',
  'TTTTTTTTT TT',
  'TTTTAAAAA TT',
  'TAAAAAAAA TT',
  'TAAAAAAAA TT',
];
export const SEAT_PRICE = 12;
export const DATE_LABEL = 'January 18';
export const TIMES = ['17:30', '18:00', '18:30', '19:00', '20:15'];
