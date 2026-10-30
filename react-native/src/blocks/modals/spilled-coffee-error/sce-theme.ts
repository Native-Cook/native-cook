// Spilled Coffee Error — layout, colour and copy, measured on the pin (a 658 × 1170 screen).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels, and the pin's y = 0
// sits DY points down (the wall goes on above it, the floor below).

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 658;
export const DY = 64;
export const PIN_W = 658;

export const C = {
  wallTop: '#F9F9FB',
  wallLow: '#E3E6EB',
  floor: '#F0F1F5',
  floorFar: '#F3F4F7',
  shadow: '#C9CFD8',
  pot: '#F4F5F8',
  potShade: '#DCE0E6',
  leafA: '#7BAD70',
  leafB: '#A5D299',
  leafC: '#5E9A5A',
  book: '#E3E7EC',
  bookTop: '#FAFBFC',
  bookSide: '#C9D0D8',
  paper: '#FFFFFF',
  cup: '#FBFBFB',
  cupShade: '#DADDE2',
  cupInside: '#E2E6EA',
  sleeve: '#C7DAAC',
  sleeveShade: '#A9C38A',
  bean: '#EEF3E6',
  coffee: '#9C6352',
  coffeeLight: '#BB8374',
  coffeeDeep: '#7A4636',
  lid: '#F8F9FC',
  lidShade: '#DCE0E8',
  title: '#2E2B4A',
  body: '#6E6C84',
  button: '#FFFFFF',
  buttonText: '#423F66',
  steam: '#B9BECB',
};

export const COPY = {
  title: 'Error!',
  body: ['Something went wrong,', 'Please try again later'],
  retrying: 'Trying again…',
  retryingBody: ['Brewing a fresh connection,', 'just a moment'],
  button: 'RETRY',
};

/** The scene's floor and the cup (a tapered paper cup, in px of the pin). */
export const SCENE = {
  floorY: 382, // where the wall meets the floor
  elev: (15 * Math.PI) / 180, // the camera looks down this much
  yaw: (28 * Math.PI) / 180, // the lying cup's mouth points left and a little toward us
  cup: { base: { x: 503, y: 545 }, L: 215, rb: 55, rm: 75, sleeve: [0.4, 0.74] as [number, number] },
  lid: { x: 502, y: 620, r: 62 },
  paper: [
    { x: 52, y: 576 },
    { x: 322, y: 552 },
    { x: 432, y: 590 },
    { x: 156, y: 626 },
  ],
};

export const UI = {
  title: 728,
  body: [798, 840],
  button: { x: 222, y: 925, w: 214, h: 76 },
};
