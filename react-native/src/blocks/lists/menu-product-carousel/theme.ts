// The pin is a 720 x 1280 promo video; it is fitted to the 402 x 874 pt screen by height
// (1 px = 0.6828 pt, 44.8 pt cropped off each side), the way ref.py's `fit` places it.
export const PX = 0.6828;
export const CROP = 44.8;
export const px = (v: number) => v * PX; // a pin length in pt
export const pxX = (v: number) => v * PX - CROP; // a pin x in pt
export const pxY = (v: number) => v * PX; // a pin y in pt

export const colors = {
  bg: '#FED5BC',
  card: '#7F4B27',
  brand: '#6E3C1E',
  ink: '#111111',
  title: '#FFFFFF',
  bite: '#7A4524',
};

export const fonts = {
  title: 'MenuLilita',
  word: 'MenuAnton',
  script: 'MenuKaushan',
  ui: 'MenuMontserratSemiBold',
};

// the centre card at rest (pin: 360 x 477 px from y 325); neighbours are 0.778 of it, same centre line
export const card = {
  width: px(360),
  height: px(477),
  centerY: pxY(563.5),
  radius: px(22),
  sideScale: 0.778,
  pitch: 266, // drag distance per card
  shadow: '8px 10px 16px rgba(74, 38, 16, 0.38)',
};

// top logo (pin box 280-437 x 62-176 px); the end card shows it 1.56x larger, its corner at (235, 494) px
export const logo = {
  left: pxX(280),
  top: pxY(62),
  endLeft: pxX(235),
  endTop: pxY(494),
  endScale: 1.56,
};

// outro type, from the pin's boxes
export const outro = {
  dont: { top: pxY(424) - 8, size: 52 },
  take: { right: pxX(589), top: pxY(535), size: 92 },
  bite: { right: pxX(588), top: pxY(668), size: 92 },
};
