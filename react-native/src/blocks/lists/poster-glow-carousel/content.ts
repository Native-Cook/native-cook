import type { ImageSourcePropType } from 'react-native';

// Invented catalogue. Every title stands in for a real film the pin showed.
import type { PosterFace } from './theme';

export type TitleStyle = PosterFace;
export type Poster = { title: string; image: ImageSourcePropType; style: TitleStyle; tint?: string; top?: boolean; credit: string };
export type Still = { title: string; meta: string; image: ImageSourcePropType; progress: number };
export type Tall = { word: string; image: ImageSourcePropType; style: TitleStyle; top?: boolean };

export type Section =
  | { kind: 'posters'; label: string; gap: number; items: Poster[]; labelX?: number; toContent?: number }
  | { kind: 'wide'; label: string; gap: number; items: Still[]; labelX?: number; toContent?: number }
  | { kind: 'tall'; label: string; gap: number; items: Tall[]; labelX?: number; toContent?: number };

export const APP_NAME = 'FilmFlow';

export const PROFILES = [
  { name: 'Jarvis3d', image: require('./assets/avatar-jonas.jpg') },
  { name: 'Isaiah', image: require('./assets/avatar-isaiah.jpg') },
  { name: 'Mia', image: require('./assets/avatar-mia.jpg') },
] as const;

export const HERO = {
  image: require('./assets/hero.jpg'),
  badge: 'NOVA STUDIOS',
  subtitle: 'THE IRON QUEEN',
  lockup: 'KINGDOM OF TIDE',
  title: 'Kingdom of Tide',
  genres: ['Action', 'Adventure'],
};

// A one-sheet: plate, title in its own face, and a billing line in condensed type.
const p = (title: string, image: ImageSourcePropType, style: TitleStyle, tint: string, credit: string, top = false): Poster =>
  ({ title, image, style, tint, credit, top });

export const SECTIONS: Section[] = [
  { kind: 'posters', label: 'Now Playing', gap: 0, items: [
    p('Into the Rift', require('./assets/posters/np-1.jpg'), 'bebas', '#FFD23F', 'A FILM BY TOMÁS REYES'),
    p('Velocity', require('./assets/posters/np-2.jpg'), 'archivo', '#FFFFFF', 'IN CINEMAS JUNE 14'),
    p('Iron Colossi', require('./assets/posters/np-3.jpg'), 'marcellus', '#EDE6D6', 'FROM THE STUDIO OF DUSTLINE'),
    p('Nebula Misfits 3', require('./assets/posters/np-4.jpg'), 'bebas', '#FFE3F6', 'THE ROAD GETS WEIRDER'),
  ] },
  { kind: 'wide', label: 'Continue Watching', gap: 29, items: [
    { title: 'Clash III', meta: '56 minutes remaining', image: require('./assets/stills/cw-1.jpg'), progress: 0.615 },
    { title: 'Kaiju Rising', meta: '24 minutes remaining', image: require('./assets/stills/cw-2.jpg'), progress: 0.8 },
  ] },
  { kind: 'tall', label: 'Set The Mood', gap: 24, items: [
    { word: 'Action', image: require('./assets/mood/action.jpg'), style: 'anton' },
    { word: 'Comedy', image: require('./assets/mood/comedy.jpg'), style: 'anton' },
    { word: 'Drama', image: require('./assets/mood/drama.jpg'), style: 'anton' },
  ] },
  { kind: 'posters', label: 'Trending', gap: 34, items: [
    p('Exfil 2', require('./assets/posters/tr-1.jpg'), 'anton', '#F2F6FA', 'NO WAY BACK · DECEMBER'),
    p('Round Twelve', require('./assets/posters/tr-2.jpg'), 'archivo', '#16181C', 'A FILM BY ADA OKAFOR'),
    p('The Pact', require('./assets/posters/tr-3.jpg'), 'marcellus', '#F1E6C8', 'BASED ON A TRUE STORY', true),
    p('Ember Blade', require('./assets/posters/tr-4.jpg'), 'dmserif', '#F0B27A', 'ONLY IN CINEMAS'),
  ] },
  { kind: 'posters', label: 'For You', gap: 30, items: [
    p('Glitch Hero', require('./assets/posters/fy-1.jpg'), 'abril', '#FFFFFF', 'THIS SUMMER', true),
    p('Red Kick', require('./assets/posters/fy-2.jpg'), 'bebas', '#FFFFFF', 'ONE DOJO · ONE FIGHT'),
    p('Two Friends', require('./assets/posters/fy-3.jpg'), 'dmserif', '#FFFFFF', 'A STORY OF SECOND CHANCES'),
    p('Blue Dash', require('./assets/posters/fy-4.jpg'), 'abril', '#FFFFFF', 'A FAMILY ADVENTURE', true),
  ] },
  { kind: 'posters', label: `New On ${APP_NAME}`, gap: 30, labelX: 11.4, items: [
    p('Snow Beast', require('./assets/posters/new-1.jpg'), 'anton', '#E2231A', 'SOMETHING IS OUT THERE'),
    p('Questfall', require('./assets/posters/new-2.jpg'), 'marcellus', '#FFE7B0', 'EVERY LEGEND BEGINS SOMEWHERE', true),
    p('Tidewater', require('./assets/posters/new-3.jpg'), 'italiana', '#DDF6FF', 'FROM THE DIRECTOR OF HOLLOW'),
    p('Elementa', require('./assets/posters/new-4.jpg'), 'dmserif', '#FFFFFF', 'FALL IN LOVE AFTER DARK'),
  ] },
  { kind: 'tall', label: 'Games Based On Movies', gap: 30, labelX: 11.4, toContent: 37, items: [
    { word: 'Rooftop Run 2', image: require('./assets/games/game-1.jpg'), style: 'bebas', top: true },
    { word: 'Ravenhold', image: require('./assets/games/game-2.jpg'), style: 'marcellus' },
    { word: 'Dune Patrol', image: require('./assets/games/game-3.jpg'), style: 'archivo' },
  ] },
  { kind: 'posters', label: 'Documentary', gap: 32.2, labelX: 11.4, items: [
    p('Golden Boy', require('./assets/posters/doc-1.jpg'), 'bebas', '#FFFFFF', 'A DOCUMENTARY SERIES'),
    p('The Final Run', require('./assets/posters/doc-2.jpg'), 'archivo', '#1B1B1B', 'THE STORY OF A DYNASTY', true),
    p('Wild Planet', require('./assets/posters/doc-3.jpg'), 'italiana', '#FFFFFF', 'NARRATED BY HUGH ALDEN'),
    p('Ice Line', require('./assets/posters/doc-4.jpg'), 'bebas', '#0E2A33', 'A FILM ABOUT WHAT REMAINS', true),
  ] },
  { kind: 'posters', label: 'Coming Soon', gap: 30, labelX: 11.4, items: [
    p('The Hunter', require('./assets/posters/cs-1.jpg'), 'anton', '#EDE6D6', 'FEBRUARY 2025'),
    p('Mercenaries 4', require('./assets/posters/cs-2.jpg'), 'archivo', '#FFFFFF', 'THE LAST JOB'),
    p('Azure Shell', require('./assets/posters/cs-3.jpg'), 'bebas', '#D6E9FF', 'COMING SOON'),
    p('Ashen Rider', require('./assets/posters/cs-4.jpg'), 'marcellus', '#FFD2A6', 'RIDE OR BURN', true),
  ] },
  { kind: 'posters', label: 'Award Winners', gap: 35.4, labelX: 11.4, items: [
    p('Neon Tide', require('./assets/posters/aw-1.jpg'), 'italiana', '#A9F0FF', 'WINNER · BEST PICTURE'),
    p('Lower Floor', require('./assets/posters/aw-2.jpg'), 'dmserif', '#FFFFFF', 'GRAND JURY PRIZE', true),
    p('Open Road', require('./assets/posters/aw-3.jpg'), 'dmserif', '#3D3029', 'THREE FESTIVAL AWARDS', true),
    p('Painted Smile', require('./assets/posters/aw-4.jpg'), 'anton', '#FFE15A', 'BEST DIRECTOR · LIDO'),
  ] },
  { kind: 'posters', label: 'Movie Night', gap: 30.8, labelX: 11.4, items: [
    p('Bramble Bear', require('./assets/posters/mn-1.jpg'), 'abril', '#FFD34E', 'A FAMILY FILM'),
    p('Spark', require('./assets/posters/mn-2.jpg'), 'archivo', '#FFFFFF', 'GOOD BOY. BIG DAY.', true),
    p('The Grey Halls', require('./assets/posters/mn-3.jpg'), 'marcellus', '#E8E2D0', 'PART ONE'),
    p('Meadow Kite', require('./assets/posters/mn-4.jpg'), 'abril', '#2E2A10', 'FROM THE MAKERS OF SPARK', true),
  ] },
  { kind: 'posters', label: 'Staff Recommend', gap: 34.2, labelX: 11.4, items: [
    p('Rain City', require('./assets/posters/sr-1.jpg'), 'italiana', '#FFFFFF', 'A NOIR IN THREE NIGHTS'),
    p('Gold Orbit', require('./assets/posters/sr-2.jpg'), 'bebas', '#FFF1D6', 'STAFF PICK'),
    p('Last Waltz', require('./assets/posters/sr-3.jpg'), 'dmserif', '#F6E7CF', 'ONE MORE DANCE'),
    p('Aurora Wolf', require('./assets/posters/sr-4.jpg'), 'anton', '#E9FFF4', 'HUNT THE LIGHT', true),
  ] },
];
