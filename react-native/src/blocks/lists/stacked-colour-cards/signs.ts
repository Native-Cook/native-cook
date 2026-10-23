// The post, top to bottom, as the pin shows it: every board fitted to two rectified frames of
// the pin (rest and the turned pose at 1.8 s). y = face centre (pt), x = sideways offset from
// the pole, L = length, tail = share of the length behind the pole, h = face height, d = thickness,
// tip = arrow point (0 = square end), heading = where it points (0 right, 180 left).
// named: false = the pin never shows this board's lettering (it is seen end-on or from the side).
// nameDx = where the pin sets the name, off the face's centre (pt).
export type Sign = { name: string; named?: false; nameDx?: number; km?: number; color: string; ink: string; heading: number; y: number; x: number; L: number; tail: number; h: number; d: number; tip: number };

const WHITE = '#ffffff';

export const SIGNS: Sign[] = [
  { name: 'Jason', km: 23, color: '#74b6a0', ink: WHITE, heading: 8, y: 24, x: 0, L: 292, tail: 0.575, h: 34, d: 20, tip: 26 },
  { name: 'Kenji', named: false, color: '#e64d50', ink: WHITE, heading: 64, y: 86, x: -41, L: 138, tail: 0.515, h: 38, d: 18, tip: 20 },
  { name: 'Dylan', nameDx: 12, km: 46, color: '#dc5c2a', ink: '#f6c39d', heading: 3, y: 128, x: 4, L: 270, tail: 0.515, h: 51, d: 12.5, tip: 32 },
  { name: 'Priya', named: false, color: '#dbb66d', ink: '#c9a85a', heading: 8, y: 175.0, x: -26, L: 150, tail: 0.5, h: 71, d: 27.0, tip: 0 },
  { name: 'Logan', named: false, color: '#ed977f', ink: WHITE, heading: 37.0, y: 234.5, x: -24, L: 132, tail: 0.489, h: 55, d: 10.5, tip: 0 },
  { name: 'Mason', named: false, color: '#e16228', ink: WHITE, heading: 47.0, y: 284.5, x: -16, L: 142, tail: 0.402, h: 46, d: 10.5, tip: 20 },
  { name: 'Noah', nameDx: 13, color: '#efa280', ink: '#e8915f', heading: 8, y: 343, x: 5, L: 269, tail: 0.51, h: 57, d: 15.5, tip: 30 },
  { name: 'Tomás', named: false, color: '#1051cd', ink: WHITE, heading: 2.5, y: 390, x: -5, L: 104, tail: 0.577, h: 27, d: 31.5, tip: 0 },
  { name: 'Caleb', nameDx: -25, km: 23, color: '#0a65db', ink: WHITE, heading: 168, y: 434.5, x: 5, L: 196, tail: 0.28, h: 65, d: 10.5, tip: 32 },
  { name: 'Amara', named: false, color: '#b0c9d9', ink: WHITE, heading: 11, y: 482.5, x: -12, L: 102, tail: 0.492, h: 42, d: 34, tip: 0 },
  { name: 'Sam', nameDx: 4, color: '#f0aa88', ink: '#e8955f', heading: 4, y: 540, x: 1, L: 232, tail: 0.48, h: 62, d: 18, tip: 28 },
  { name: 'Lena', named: false, color: '#a4b44e', ink: WHITE, heading: 23, y: 615, x: 8, L: 132, tail: 0.485, h: 93, d: 32, tip: 20 },
  { name: 'Jack', nameDx: 22, color: '#f2918a', ink: '#f7c0b8', heading: 13, y: 699, x: -1, L: 228, tail: 0.48, h: 68, d: 12, tip: 0 },
  { name: 'Owen', nameDx: -2, color: '#f3a986', ink: '#e99a6a', heading: -8, y: 765.5, x: 0, L: 170, tail: 0.5, h: 75, d: 18, tip: 0 },
  { name: 'Ryan', nameDx: 45, km: 21, color: '#ec6647', ink: '#f6c0a0', heading: -3, y: 839.5, x: 0, L: 214, tail: 0.45, h: 72, d: 14, tip: 28 },
];
