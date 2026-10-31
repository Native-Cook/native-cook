// Park Assist Scrub — colours, type, words, the camera and the layout.

export const C = {
  page: '#0E0F12',
  ink: '#F2F3F5',
  mute: '#8B8E98',
  card: '#1A1C21',
  line: '#2A2D33',
  accent: '#FF8A3D',
  green: '#4BDC86',
  amber: '#FFC23D',
  red: '#FF4D45',
} as const;

export const FONT = {
  regular: 'PasInter-Regular',
  medium: 'PasInter-Medium',
  semibold: 'PasInter-SemiBold',
} as const;

export const CAMERA = { x: 0.75, y: 1.9, tilt: 0.62, ppm: 58 } as const;
export const STAGE_H = 560;
export const SPEED = 0.26; // path share per second while the button is held

export function makeLayout(w: number, h: number, top: number, bottom: number) {
  const headTop = top + 8;
  const stageTop = headTop + 64;
  const buttonTop = h - Math.max(bottom, 16) - 8 - 64;
  const stageH = Math.min(STAGE_H, buttonTop - 24 - stageTop);
  return { w, h, headTop, stageTop, stageH, buttonTop };
}
