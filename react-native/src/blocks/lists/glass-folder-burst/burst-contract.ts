/**
 * Numbers every file of the block agrees on: the folder's size, where its mouth is,
 * the palette and the type sizes. Positions are points on the screen.
 */
import type { SharedValue } from 'react-native-reanimated';

/** The folder at scale 1, measured from its centre. y grows downward. */
export const FOLDER = {
  w: 132,
  h: 104,
  radius: 16,
  /** The top edge of the open front panel: rows rise from behind it, this far above the centre. */
  mouthY: -22,
  /** Half the width of the opening rows pass through. */
  mouthHalf: 44,
  /** Where a stored row sits inside, relative to the centre (hidden behind the front panel). */
  storeY: 10,
  /** The glowing disc that lives inside. */
  orbR: 24,
  /** Its centre while stored, relative to the folder's centre. */
  orbStoredY: 2,
  /** Scale while it waits alone in the middle of the screen. */
  restScale: 1.5,
} as const;

export const ROW = { h: 52, radius: 16, gap: 8, margin: 24, art: 36, artRadius: 8, pad: 8 } as const;

export const PALETTE = {
  page: '#000000',
  green: '#28E07C',
  greenBright: '#8CFFC0',
  greenDeep: '#0A7A43',
  fog: '#0E5C38',
  glassTint: '#0F3D29',
  ink: '#F2FFF7',
  inkMute: 'rgba(242,255,247,0.58)',
  inkOnGreen: 'rgba(2,24,13,0.72)',
  rowDark: '#101814',
  rowRim: 'rgba(255,255,255,0.10)',
} as const;

export const TYPE = {
  /** Archivo Expanded Black. */
  title: 20,
  /** Archivo Expanded Regular from here on. */
  sub: 12,
  rowTitle: 14,
  rowArtist: 11,
  rowTime: 11,
} as const;

/** What the screen tells the folder on every frame. All are shared values read on the UI thread. */
export type FolderDrive = {
  /** Centre of the folder on the canvas. */
  cx: SharedValue<number>;
  cy: SharedValue<number>;
  /** 1 is 132 x 104 pt. */
  scale: SharedValue<number>;
  /** 0 shut, 1 with the front leaning fully open; springs may carry it to -0.06 or 1.1. */
  open: SharedValue<number>;
  /** 0 to 1 while a finger is down on it. */
  press: SharedValue<number>;
  /** 0 to 1: how much light pours out of the mouth (rows passing through it). */
  flow: SharedValue<number>;
  /** -1 to 1: recoil. Positive squashes it flatter and wider, negative stretches it. */
  squash: SharedValue<number>;
};

/** The glowing disc: stored inside the folder, or flown up to the header. */
export type OrbDrive = {
  x: SharedValue<number>;
  y: SharedValue<number>;
  /** Radius in points. */
  r: SharedValue<number>;
  /** 0 to 1 brightness of its bloom. */
  glow: SharedValue<number>;
  /** 0 to 1: how lively the equaliser bars of its mark are. */
  beat: SharedValue<number>;
  /** Opacity of the invented mark; zero behind the frosted front. */
  mark: SharedValue<number>;
};
