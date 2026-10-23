export type Planet = {
  name: string;
  order: string;
  short: string;
  long: string;
  /** Words of `long` set in green bold, as the pin does for "the Sun". */
  accent: string;
  image: number;
  /** Atmosphere colour (from the planet's own art). */
  glow: string;
};

export const PLANETS: Planet[] = [
  {
    name: 'Mars',
    order: '4',
    short: 'The fourth planet from the Sun, a dusty, cold, desert world..',
    long: 'The fourth planet from the Sun is a dusty, cold desert world with a very thin atmosphere and the tallest volcano we know.',
    accent: 'the Sun',
    image: require('./assets/mars.png'),
    glow: '#FF8A4C',
  },
  {
    name: 'Earth',
    order: '3',
    short: 'The third planet from the Sun and the only astronomical..',
    long: 'The third planet from the Sun and the only astronomical object known to harbor and support life. 70.8% covered with water.',
    accent: 'the Sun',
    image: require('./assets/earth.png'),
    glow: '#5FC6FF',
  },
  {
    name: 'Venus',
    order: '2',
    short: 'The second planet from the Sun and our closest planetary neighbour..',
    long: 'The second planet from the Sun and our closest planetary neighbour, wrapped in thick clouds that trap its heat.',
    accent: 'the Sun',
    image: require('./assets/venus.png'),
    glow: '#CFEAFF',
  },
];

export const START_INDEX = 1;

export const FRIENDS = [
  require('./assets/avatar-2.png'),
  require('./assets/avatar-3.png'),
  require('./assets/avatar-4.png'),
  require('./assets/avatar-5.png'),
];
export const ME = require('./assets/avatar-1.png');
export const ROCKET = require('./assets/rocket.png');
export const VIDEO = require('./assets/earth-video.jpg');
