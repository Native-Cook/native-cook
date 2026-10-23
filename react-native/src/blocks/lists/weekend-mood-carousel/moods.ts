// Moods of the picker. Measured from the pin (Fluper's "weekend mood" screen); the characters are our own.

export type FaceKind = 'sleepy' | 'foodie' | 'recovery' | 'binge' | 'shopping' | 'scroll' | 'social';

export type MoodCharacter = {
  kind: FaceKind;
  /** Flat colour of the illustration panel behind this character. */
  panel: string;
  /** Selected chip gradient, left to right. */
  chip: readonly [string, string, string];
  /** Centre indicator of the ruler, top to bottom. */
  tick: readonly [string, string];
};

/** Carousel order, left to right, as the pin lays them out. */
export const CHARACTERS: readonly MoodCharacter[] = [
  {
    kind: 'sleepy',
    panel: '#F0E1B0',
    chip: ['#F6AE31', '#EBA36E', '#E09AA3'],
    tick: ['#C2B4D8', '#F4D27E'],
  },
  {
    kind: 'foodie',
    panel: '#BEDB98',
    chip: ['#6BB341', '#ADB443', '#DBAD6E'],
    tick: ['#6FAFDB', '#82CC86'],
  },
  {
    kind: 'recovery',
    panel: '#FDBFB6',
    chip: ['#EB7F5F', '#EB8551', '#F1814F'],
    tick: ['#CFB2E6', '#F0D978'],
  },
  {
    kind: 'binge',
    panel: '#F6D0FB',
    chip: ['#C87CF2', '#C487D0', '#D39B9D'],
    tick: ['#DE6573', '#D6AFEA'],
  },
  {
    kind: 'shopping',
    panel: '#FDD7FC',
    chip: ['#C976EA', '#D376BE', '#E57A75'],
    tick: ['#DAAEEF', '#E4A3CB'],
  },
  {
    kind: 'scroll',
    panel: '#FDA4AB',
    chip: ['#DA735E', '#DB7D4E', '#EC8E18'],
    tick: ['#DAAEEF', '#D66476'],
  },
  {
    kind: 'social',
    panel: '#FDD2BA',
    chip: ['#EC7C66', '#E8854D', '#F69318'],
    tick: ['#CEB0E8', '#6FB1E6'],
  },
];

export const CHAR = {
  sleepy: 0,
  foodie: 1,
  recovery: 2,
  binge: 3,
  shopping: 4,
  scroll: 5,
  social: 6,
} as const;

/** Chip strip, in the pin's order. Each chip shows the character that best fits it. */
export const CHIPS: readonly { label: string; char: number }[] = [
  { label: 'Grateful', char: CHAR.recovery },
  { label: 'Joyful', char: CHAR.binge },
  { label: 'Sleepy', char: CHAR.sleepy },
  { label: 'Recovery Mode', char: CHAR.recovery },
  { label: 'Satisfied', char: CHAR.foodie },
  { label: 'Foodie', char: CHAR.foodie },
  { label: 'Reflective', char: CHAR.recovery },
  { label: 'Thoughtful', char: CHAR.social },
  { label: 'Nostalgic', char: CHAR.social },
  { label: 'Social Butterfly', char: CHAR.social },
  { label: 'Uncertain', char: CHAR.scroll },
  { label: 'Melancholy', char: CHAR.sleepy },
  { label: 'Binge-Watching', char: CHAR.binge },
  { label: 'Disappointed', char: CHAR.scroll },
  { label: 'Lonely', char: CHAR.shopping },
  { label: 'Shopping', char: CHAR.shopping },
  { label: 'Hopeless', char: CHAR.scroll },
  { label: 'Heartbroken', char: CHAR.shopping },
  { label: 'Scroll Mode', char: CHAR.scroll },
  { label: 'Devastated', char: CHAR.sleepy },
];

export const CHIP = {
  sleepy: 2,
  recovery: 3,
  foodie: 5,
  social: 9,
  binge: 12,
  shopping: 15,
  scroll: 18,
} as const;
