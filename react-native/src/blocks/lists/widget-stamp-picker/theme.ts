// Measured from the pin (720 px wide video, 1 px = 0.5583 pt). Layout values are in points
// relative to `top`, the screen line that matches the top edge of the pin's crop.
export const PIN_TO_PT = 402 / 720;

export const colors = {
  bg: '#FFFCFF',
  ink: '#161C33',
  backBg: '#F1F5FB',
  skipBg: '#FFE8FF',
  skipInk: '#E0129E',
  caption: '#4E4B50',
  hint: '#DCDFE7',
  door: '#E3E8F0',
  doorEdge: '#CBD3DF',
  blobs: ['#FFA51A', '#26B36B', '#FF3FC4'] as const,
};

export const layout = {
  // the pin's crop starts this far below the safe area
  cropTopFromSafe: 18,
  sideMargin: 17,
  backSize: 38,
  navCenterY: 4.5,
  skip: { width: 74, height: 39 },
  mascot: { left: 167, top: 89, width: 64, height: 78 },
  // Gasoek One is narrower than the pin's face: widened to the pin's line length (326 pt)
  title: { top: 189.5, fontSize: 31.9, lineHeight: 37.5, stretch: 1.125 },
  stamp: { centerY: 478.5, width: 228, height: 287 },
  hint: { centerY: 667.8, width: 50, height: 28 },
  caption: { top: 704, width: 360, fontSize: 18, lineHeight: 24 },
};

// The stamp, drawn in its own box (points).
export const stampShape = {
  // perforation, traced at 3x: half-round notches r 7.8 pt centred on the stamp's edge, 19.2 pt
  // apart; the stamp's coloured shadow shows through them, so they read as cut paper
  notch: 7.8,
  pitch: 19.2,
  notchesAcross: 11, // first one 21 pt from the corner
  notchesDown: 15, // first one 9.6 pt from the corner
  panel: {
    left: 33,
    top: 30,
    width: 162,
    height: 183,
    radius: 12,
    border: 2,
  },
  labelCenterY: 243,
  labelSize: 27.5,
  labelStretch: 1.09,
  shadowDy: 5,
  haloBlur: 7,
  edgeSoftness: 0.5,
  edgeShadeDy: 3,
  edgeShadeBlur: 4,
  edgeShadeAlpha: 0.3,
  shadowBlur: 7,
};

// shade: the paper's shadow inside the notches, sampled from the pin per stamp
export type StampTone = { tint: string; glow: string; border: string; label: string; shade: string };

// Display: Titan One, the closest open face to the pin's (fitted by overlap with the pin's
// title: u-shaped y, round-bottomed w), widened to the pin's line lengths. Text: Inter.
export const fonts = { display: 'TitanOne', text: 'Inter' };
