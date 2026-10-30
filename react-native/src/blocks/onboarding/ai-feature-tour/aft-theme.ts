// AI Feature Tour — layout, colour and copy, measured on the pin (a phone screen 720 px across).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 720;
export const PIN_W = 720;

export const C = {
  bgTop: '#D3C9FD',
  bgMid: '#EEEBFC',
  bg: '#F8F8FA',
  glow: '#ECE8F8',
  title: '#1D1D22',
  rowTitle: '#1A1A1F',
  sub: '#8A8A90',
  tile: '#FFFFFF',
  tileEdge: '#E4E6EA',
  tileShade: '#B9BCC6',
  calTop: '#DCE2E6',
  calRing: '#A9B2BA',
  dotBlue: '#4D96F4',
  dotGrey: '#D9DDE2',
  green: '#43B97A',
  greenLine: '#6BCB93',
  grid: '#EEF1F3',
  check: '#2FC06A',
  checkGrey: '#C6CBD1',
  line: '#E3E6EA',
  runner: '#3D7BEA',
  avoSkin: '#3E6B2A',
  avoFlesh: '#D9E66A',
  avoInner: '#F2F49A',
  pit: '#A35A26',
  button: '#1F2023',
  sparkle: '#C9B8FF',
};

export const COPY = {
  title: 'Halden Intelligence can:',
  rows: [
    { title: 'Create check ins', sub: ['Reminders that follow up and', 'keep you on track.'] },
    { title: 'Understand your trends', sub: ["See what's moving your metrics", "and where they're heading."] },
    { title: 'Create workout templates', sub: ['Strength templates built around', 'your goals and style.'] },
    { title: 'Log food', sub: ['Snap a photo or describe it and', 'let Halden handle the rest.'] },
    { title: 'Create a training plan', sub: ['A personalized plan based on', 'your goals and activity.'] },
    { title: 'Explain your records', sub: ['Upload records to see your', 'biomarkers explained.'] },
  ],
  button: 'Continue',
};

export const UI = {
  title: { x: 360, y: 235 },
  rows: { iconX: 147, textX: 264, y0: 355, pitch: 220, sub: [42, 84], tile: 114 },
  button: { x: 45, y: 1402, w: 630, h: 95 },
};
