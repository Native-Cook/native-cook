import type { ImageSourcePropType } from 'react-native';

export type Memo = {
  title: string;
  sub: string;
  count: string;
  unit: string;
  notes: string;
  label?: string; // caption over the front photo while the folder is closed
  photos: [ImageSourcePropType, ImageSourcePropType, ImageSourcePropType]; // front, left, right
};

const front = require('./assets/photo-profile.jpg');
const left = require('./assets/photo-shades-v2.jpg');
const right = require('./assets/photo-up-v4.jpg');

export const MEMOS: Memo[] = [
  { title: 'Daily Memo', sub: 'Notes & Journaling', count: '09', unit: 'Doc', notes: '11th Notes', label: 'Daily Task\nCard Design', photos: [front, left, right] },
  { title: 'Daily Memo', sub: 'Notes & Journaling', count: '09', unit: 'Doc', notes: '11th Notes', label: 'Daily Task\nCard Design', photos: [front, left, right] },
];
