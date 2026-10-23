export const colors = {
  background: '#FFFFFF',
  ink: '#070707',
  detailAmount: '#303030',
  secondary: '#8F8F8F',
  accent: '#C8F77D',
  accentDark: '#99CE4A',
  heart: '#F43D43',
  heartDark: '#B81421',
};

// Measured from the 748 x 462 source frame and normalized to a 402 pt screen.
export const layout = {
  sourceWidth: 748,
  header: { top: 22.3, side: 59, fontSize: 15, counterSize: 12.5, lineHeight: 19 },
  deck: { centerX: 199.3, centerY: 121, card: 134.7, radius: 9, border: 1.2 },
  metadata: { top: 203, nameSize: 9.3, detailSize: 9, nameLine: 11.7, detailLine: 12 },
};

export const fan = {
  x: [0, 64, 87, 94],
  y: [0, 1, 3, 5],
  scale: [1, 0.76, 0.63, 0.52],
  rotate: [0, 7, 9, 11],
};
