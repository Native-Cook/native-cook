// The pin is a 736 x 552 landscape composition. It is kept whole and scaled uniformly so the
// player bar (its widest element, 644 px) fits the 402 pt screen: S = 0.615 pt per pin pixel,
// centred on the composition's centre (368, 282).
export const S = 0.615;
export const PIN_CX = 368;
export const PIN_CY = 282;

export const TRACKS = [
  { id: 'braids', title: 'Dusk Signal', artist: 'Gianni Rowe', cover: require('./assets/covers/braids.jpg'), glow: '#1E3A3C', wash: ['#2E5A5D', '#1E3A3C', '#142829'] },
  { id: 'velvet', title: 'Velvet Hours', artist: 'Ritva Lane', cover: require('./assets/covers/velvet.jpg'), glow: '#6E6A66', wash: ['#8E8984', '#6E6A66', '#55514E'] },
  { id: 'ember', title: 'Ember Lights', artist: 'Thea Vance', cover: require('./assets/covers/ember.jpg'), glow: '#F2A63C', wash: ['#E39631', '#C47516', '#AE5F08'] },
  { id: 'satin', title: 'Opera 1 Theme', artist: 'Quinn Tyler', cover: require('./assets/covers/satin.jpg'), glow: '#8A8580', wash: ['#A39E98', '#8A8580', '#6C6864'] },
  { id: 'drive', title: 'Night Drive (Chennai)', artist: 'Arun, Isha & Dev', cover: require('./assets/covers/drive.jpg'), glow: '#4A4A50', wash: ['#626269', '#4A4A50', '#36363B'] },
] as const;
export const START = 2;

// Pin pixels.
export const PIN = {
  card: { w: 215, h: 269, radius: 17, inset: 5, cover: 205, coverRadius: 12, titleY: 228, artistY: 252, titleSize: 17.5, artistSize: 16.5 },
  cardCY: 222, // centre of the centre card
  halo: { x: 301, y: 160, rx: 185, ry: 140, alpha: 1 }, // the dark shadow behind the stack's upper left
  drop: { x: 375, y: 385, rx: 180, ry: 45, alpha: 0.95 }, // the centre card's shadow on the room, just under its caption
  shade: { x: 250, y: 410, rx: 190, ry: 95, alpha: 0.92 }, // the dark band between the flow and the bar's left half
  cardCX: 360.5, // the flow is centred 7.5 px left of the composition's centre
  slots: [0, 108, 176],
  slotsRight: [0, 113, 184], // the right-hand cards sit wider than the left in the pin (measured edges 323 / 361.5 pt) // centre x offset of cards 0, 1 and 2 away (measured: edge + scaled half width)
  scales: [1, 0.86, 0.78],
  turns: [0, 12, 20], // degrees of turn: the outer edge recedes, as the pin's top edges slope
  player: { x: 46, y: 409, w: 644, h: 68 },
  prevX: 92.4, pauseX: 151.2, nextX: 211.1, iconY: 443, icon: 22,
  capsule: { x: 248, y: 418, w: 240, h: 50, radius: 12 },
  mini: { x: 254, y: 424, size: 38, radius: 7 },
  miniTitle: { x: 300, y: 436, size: 12.5 },
  miniArtist: { y: 452, size: 10.5 },
  progress: { x: 254, y: 464.2, w: 106 },
  waveX: 442.8, moreX: 470,
  lyricsX: 531.9, listX: 587.2, volumeX: 643.1,
};

export const GLASS = {
  card: 'rgba(120,110,104,0.42)',
  cardEdge: 'rgba(255,255,255,0.32)',
  bar: 'rgba(150,92,30,0.2)', // the pin's bar keeps the room's saturation
  barEdge: 'rgba(255,255,255,0.38)',
  capsule: 'rgba(16,14,13,0.78)',
  ink: '#FFFFFF',
  inkSoft: 'rgba(255,255,255,0.72)',
  track: 'rgba(255,255,255,0.84)', // the pin's track reads as one light line (lum 224 on the dark capsule)
};

// Ink boxes of the bar's glyphs on the pin, in pt (measured at 3x): width, height.
export const GLYPH = {
  rewind: [15.3, 8.7], pause: [8.7, 11.7], forward: [15.6, 8.7],
  lyrics: [13, 12], list: [13, 9.3], volume: [13, 9], wave: [10, 5],
} as const;
