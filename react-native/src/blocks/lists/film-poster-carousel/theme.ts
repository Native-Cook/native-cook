// Measured on the pin rectified to 1206 x 2622 (3x); values in pt. The pin sets its text in
// SF Pro (the iOS system face), so the block uses the system font with the pin's weights.

export const FILMS = [
  {
    id: 'skyride', title: 'Skyride', year: '2019', director: 'Rafi Hassan', duration: '2h 10m', rating: 4.5,
    tags: ['#action', '#romantic', '#family'],
    poster: require('./assets/poster-skyride.jpg'),
    panel: '#151A2D', chip: '#393A4E', chevron: '#ADAEC0', meta: '#C6C8D7',
    footer: '#F0365D', footerInk: '#FFEFF6',
  },
  {
    id: 'pride', title: 'The Lion Path', year: '2019', director: 'Joel Mercer', duration: '2h 22m', rating: 5,
    tags: ['#drama', '#adventure', '#family'],
    poster: require('./assets/poster-cub.jpg'),
    panel: '#1F1516', chip: '#413937', chevron: '#B4A9A8', meta: '#D2C9CC',
    footer: '#F3D72E', footerInk: '#140A06',
  },
] as const;

/** Text boxes are given as the pin's ink: left edge, cap top and baseline. */
export const LAYOUT = {
  posterH: 550, // the plate at full width (1024 × 1400), as the pin's art is laid out
  fadeFrom: 0.74,
  panelBottom: 775.5, // the panel's rounded bottom edge, over the footer
  panelRadius: 34,
  // A soft dark band where the panel's rounded bottom lies over the footer (measured: 5 pt, ~60 % at the edge).
  panelShadow: { opacity: 1, radius: 2.2, y: 2 },
  close: { right: 32.3, top: 33.7, size: 42, radius: 8, icon: 15 },
  title: { x: 44.1, baseline: 525.3, size: 50, weight: '700', tracking: 0.2 },
  meta: { x: 43.6, baseline: 556.4, size: 14, weight: '600', tracking: 0.5 },
  duration: { x: 43.4, baseline: 588.7, size: 18, weight: '700', tracking: 1.2 },
  stars: { cx: 50.9, cy: 616.9, size: 16, pitch: 22, inner: 0.5, halo: 0.55 }, // centres measured; a soft dark halo round each
  tags: { x: 42.7, top: 662.3, h: 31.5, padX: 16.5, gap: 13, size: 14.6, weight: '700', tracking: -0.3 },
  chevron: { cx: 201.4, top: 747, w: 26, h: 12, stroke: 2.2 },
  footerLabel: { baseline: 831.7, size: 19.5, weight: '800', tracking: 1.75 },
  starColor: '#EBD46E',
  starOff: '#8A8778', // the empty half reads grey in the pin
  ink: '#FFFFFF',
} as const;
