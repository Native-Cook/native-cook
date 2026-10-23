// The pin is a 1080 × 1350 presentation of one folder component in two states. Measurements
// stay in source pixels and are scaled onto the 402 pt capture canvas.
export const S = 0.51;
export const PIN = { cx: 545, cy: 655 }; // centre of the arrangement

export const FONT = { regular: 'Inter-Regular', medium: 'Inter-Medium', semibold: 'Inter-SemiBold' };

export const C = {
  page: '#010101',
  folder: '#24211f',
  ink: '#ffffff',
  mute: '#918f8d',
  frame: '#fffefb',
};

// Folder card (pin px). Origins of the two folders on the pin.
export const CARD = { w: 435, h: 434, radius: 40 };
export const FOLDERS_AT = [
  { x: 327, y: 173 },
  { x: 344, y: 691 },
];

// The pocket: a tab on the left, a slope, then the flat top edge.
export const POCKET = {
  tabTop: 155,
  tabEnd: 150,
  slopeEnd: 213,
  top: 202,
  tabRadius: 18,
  cornerRadius: 22,
};

// Lettering on the pocket (pin px, card-local).
export const TYPE = {
  title: { x: 18, y: 179, size: 20 },
  sub: { x: 18, y: 210, size: 19 },
  count: { x: 25, y: 390, size: 44 },
  unit: { gap: 7, size: 19, dy: -9.5 }, // the pin centres the unit on the figures, not on their baseline
  notes: { right: 51, y: 390, size: 19 },
  label: { right: 38.5, y: 46, size: 19.9, line: 23 }, // the caption on the closed folder's photo
};

// Open contour traced from the pin, in card-local pixels. Each row corresponds to the same
// command in pocketSteps; separate corner controls retain the pin's rounded, asymmetric lip.
export const POCKET_OPEN = [
  [-101, 257],
  [-106, 240, -85, 240],
  [158, 240],
  [183, 240, 185, 274, 226, 274],
  [508, 275],
  [535, 276, 523, 310],
  [439, 418],
  [425, 442, 415, 442],
  [24, 442],
  [6, 442, -8, 420],
  [-33.7, 365.7, -82.1, 311.3, -101, 257],
] as const;

// Open pocket lettering (pin px, offsets from the closed spots), measured on the pin's open folder.
export const OPEN_TYPE = {
  scale: 1.05,
  title: { dx: -88, dy: 94 },
  sub: { dx: -68, dy: 94 },
  count: { dx: -10, dy: 7 },
  notes: { dx: 27, dy: 5 },
};

// Photos: one print size; closed, the front print fills the card top edge to edge.
export const PHOTO = {
  w: 327,
  h: 330,
  radius: 26,
  frame: 8,
  closedScale: CARD.w / 327,
  closed: { x: 217.5, y: 217 }, // centre, card-local
  open: [
    { x: 189.5, y: 178, rot: 0 },
    { x: 23, y: 202, rot: -22.5 },
    { x: 381, y: 202, rot: 22.5 },
  ],
};

export const DRAG = { range: 140, resist: 0.3, commit: 0.5, velocity: 600 }; // pt for a full open, rubber band, progress, pt/s
