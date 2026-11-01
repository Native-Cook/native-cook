// Coin Card Onboarding — the scene's keyframes per page, the pager spring and the autoplay script.
// Helpers above callers.

export const LOOP = 9;
export const PAGES = 3;
export const DEG = Math.PI / 180;

/** Autoplay: rest on page 1, swipe to 2 at 1.6 s, to 3 at 4.2 s, back to 1 at 6.8 s. */
export const scriptPage = (t: number) => {
  'worklet';
  if (t >= 6.8) return 0;
  if (t >= 4.2) return 2;
  if (t >= 1.6) return 1;
  return 0;
};

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};

/** Value of a keyframed channel at page position p (0..PAGES-1), eased between pages. */
export const key = (k: readonly number[], p: number) => {
  'worklet';
  const q = Math.max(0, Math.min(PAGES - 1, p));
  const i = Math.min(PAGES - 2, Math.floor(q));
  const base = mix(k[i], k[i + 1], smooth(q - i));
  // past the ends (rubber band) keep moving a little
  if (p < 0) return base + (k[0] - k[1]) * 0.25 * -p;
  if (p > PAGES - 1) return base + (k[PAGES - 1] - k[PAGES - 2]) * 0.25 * (p - PAGES + 1);
  return base;
};

// Keyframes per page, relative to the scene centre (pt and degrees).
export const FRONT = {
  x: [18, 0, 0],
  y: [6, -4, 34],
  rz: [-14, -6, 0],
  ry: [-20, 14, 0],
  rx: [10, 6, 46],
} as const;
export const BACK = {
  x: [-52, -74, 0],
  y: [40, 30, 56],
  rz: [-21, -28, 0],
  ry: [-24, 8, 0],
  rx: [10, 4, 46],
} as const;
/** The euro coin tumbles about its horizontal axis, a full turn per page. */
export const EURO = { x: [64, -86, 74], y: [-74, -96, -34], r: [36, 34, 30], flip: [0, 360, 720] } as const;
/** The dollar coin spins about its vertical axis the other way. */
export const DOLLAR = { x: [22, 92, -70], y: [-18, -18, -26], r: [31, 30, 30], flip: [0, -360, -720] } as const;

export const COPY = [
  {
    title: 'The Card That\nWorks Anywhere',
    body: 'Create your account to trade, store,\nand grow your digital assets securely',
  },
  {
    title: 'Swap Between\nCoins in a Tap',
    body: 'Move between 40 currencies at the\nrate you see, with no hidden spread',
  },
  {
    title: 'Your Keys,\nYour Money',
    body: 'Biometric approvals and cold storage\nkeep every coin in your hands',
  },
];
