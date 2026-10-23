// Categories (front of the deck first) and classes. Names and people are invented.

export type Category = { name: string; color: string; photo: number };

/** Index 0 is the front card of the first screen; index 5 the front card after one swipe. */
export const CATEGORIES: Category[] = [
  { name: 'Yoga', color: '#F2B21C', photo: require('./assets/cat-yoga.jpg') },
  { name: 'Aerobics', color: '#9E1530', photo: require('./assets/cat-aerobics.jpg') },
  { name: 'Cardio', color: '#4FA9D8', photo: require('./assets/cat-cardio.jpg') },
  { name: 'Acrobatics', color: '#1E8C8C', photo: require('./assets/cat-acrobatics.jpg') },
  { name: 'Running', color: '#B5182B', photo: require('./assets/cat-running.jpg') },
  { name: 'Core', color: '#D9A68A', photo: require('./assets/cat-core.jpg') },
  { name: 'HIIT', color: '#F0A81C', photo: require('./assets/cat-hiit.jpg') },
  { name: 'Crossfit', color: '#8E1222', photo: require('./assets/cat-crossfit.jpg') },
  { name: 'Balling', color: '#E2A419', photo: require('./assets/cat-balling.jpg') },
  { name: 'Strength', color: '#144C52', photo: require('./assets/cat-strength.jpg') },
];
export const CROSSFIT = 7;
export const HERO = require('./assets/hero-crossfit.jpg');

export type ClassCard = {
  key: string;
  title: string;
  kind: 'live' | 'quiz' | 'session';
  colors: readonly [string, string];
  photo: number | null;
  coach?: { name: string; photo: number };
  time?: string;
  length?: string;
};

const COACH_1 = require('./assets/coach-1.jpg');
const COACH_2 = require('./assets/coach-2.jpg');

/** Day 8: the cards of the pin. */
export const DAY_8: ClassCard[] = [
  { key: 'live8', title: 'Monday\nMotivation', kind: 'live', colors: ['#F2378F', '#7A5CF0'], photo: null, coach: { name: 'Tomás Herrera', photo: COACH_1 } },
  { key: 'quiz8', title: 'What is an\nAntioxidant', kind: 'quiz', colors: ['#F7B31C', '#F39A14'], photo: require('./assets/class-antioxidant.png'), coach: { name: 'Amara Okafor', photo: COACH_2 }, time: '8:30 am', length: '20 min' },
  { key: 'monday8', title: 'Monday\nMotivation', kind: 'session', colors: ['#EC2B33', '#E02029'], photo: require('./assets/class-monday.png'), length: '35 min.' },
  { key: 'morning8', title: 'Morning\nFlow', kind: 'session', colors: ['#2C9A9B', '#258486'], photo: require('./assets/class-morning.png'), length: '30 min.' },
];

/** Day 9. */
export const DAY_9: ClassCard[] = [
  { key: 'live9', title: 'Break a\nSweat', kind: 'live', colors: ['#2CB6B0', '#1B8FA3'], photo: null, coach: { name: 'Amara Okafor', photo: COACH_2 } },
  { key: 'quiz9', title: 'Sit and\nStretch', kind: 'quiz', colors: ['#D9D4C8', '#BFB8A8'], photo: null, coach: { name: 'Tomás Herrera', photo: COACH_1 }, time: '7:00 am', length: '15 min' },
  { key: 'run9', title: 'Keep on\nRunning', kind: 'session', colors: ['#3E7A4A', '#2F6439'], photo: require('./assets/class-running.png'), length: '40 min.' },
  { key: 'hiit9', title: 'High\nIntensity', kind: 'session', colors: ['#8E1222', '#6E0D1A'], photo: require('./assets/class-hiit.png'), length: '25 min.' },
];

export const SESSION_TIMES = ['4:30 am', '5:00 am', '5:30 am', '6:00 am'];

/** April's grid from the week of the 6th, as the pin shows it. Weekends and past days are dimmed. */
export const WEEKS = [
  [6, 7, 8, 9, 10, 11, 12],
  [13, 14, 15, 16, 17, 18, 19],
  [20, 21, 22, 23, 24, 25, 26],
  [27, 28, 29, 30, 31, 0, 0],
];
export const DIM_DAYS = [6, 11, 13, 18, 20, 22, 24, 25, 27];
/** Class dots under a day: r = red, g = green. */
export const DOTS: Record<number, string> = { 7: 'r', 10: 'rg', 12: 'rg', 14: 'r', 15: 'rg', 16: 'r', 17: 'rg', 19: 'r', 21: 'rg', 23: 'r', 26: 'r', 28: 'rg', 29: 'rg', 30: 'r', 31: 'r' };
