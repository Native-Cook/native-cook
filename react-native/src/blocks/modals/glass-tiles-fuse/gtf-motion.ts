// Glass Tiles Fuse — tokens, layout and motion. Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function springTo(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

export const COLOR = {
  page: '#111111',
  sheet: '#141414',
  grabber: '#3A3A3C',
  rowName: '#E8E8E8',
  rowSub: '#8E8E93',
  pill: '#232323',
  pillInk: '#D6D6D6',
  title: '#F2F2F2',
  body: '#999999',
  button: '#F2F2F2',
  buttonInk: '#111111',
  dim: 'rgba(0, 0, 0, 0.42)',
} as const;

export const FONT = { regular: 'GtfInter-Regular', semibold: 'GtfInter-SemiBold', bold: 'GtfInter-Bold' } as const;

export type Layout = {
  w: number;
  h: number;
  titleTop: number;
  rowsTop: number;
  rowH: number;
  sheetTop: number;
  tilesY: number;
  stageH: number;
  white: number;
  glass: number;
  textTop: number;
  buttonTop: number;
  side: number;
};

// The tiles are the picture: 1.35 times the reference's, as large as fits a pull to the screen's edge.
export const SCALE = 1.35;

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const buttonTop = h - Math.max(bottom, 16) - 12 - 52;
  const textTop = buttonTop - 24 - 76;
  const tilesY = textTop - 40 - 0.5 * 145;
  const sheetTop = Math.max(top + 200, tilesY - 0.5 * 145 - 56);
  return {
    w,
    h,
    titleTop: top + 24,
    rowsTop: top + 126,
    rowH: 63,
    sheetTop,
    tilesY,
    stageH: textTop - 12 - sheetTop,
    white: Math.round(107 * SCALE),
    glass: Math.round(102 * SCALE),
    textTop,
    buttonTop,
    side: 32,
  };
}

/** Poses of the two tiles: [x, y, angle] for the white one, then the glass one. */
export type Poses = { wx: number; wy: number; wa: number; gx: number; gy: number; ga: number };

export function rest(L: Layout, t: number, loop: number): Poses {
  'worklet';
  const bob = Math.sin((t / loop) * Math.PI * 4);
  const sway = Math.sin((t / loop) * Math.PI * 2);
  return {
    wx: L.w / 2 - 22 * SCALE,
    wy: L.tilesY + 12 * SCALE,
    wa: 0.14,
    gx: L.w / 2 + 36 * SCALE + sway * 4,
    gy: L.tilesY - 16 * SCALE + bob * 5,
    ga: -0.12 + sway * 0.03,
  };
}

/** Half the centre distance of the two tiles side by side, connected. */
export const GAP = 86;
/** Where the tiles sit melted together (left of centre, so the glass can be pulled out to the right). */
export const FUSED_W = 70;
export const FUSED_G = 30;

/**
 * The connection as one number: 0 apart and resting … 1 connected side by side.
 * approach 0–0.3, fuse 0.18–0.42 (melted together from 0.42 to 0.56), seam 0.36–0.58,
 * part 0.56–0.9 (the neck stretches and snaps), chip 0.78–1.
 */
export function linked(L: Layout, r: Poses, k: number) {
  'worklet';
  const a = easeInOut(seg(k, 0, 0.3));
  const fuseIn = easeInOut(seg(k, 0.18, 0.42));
  const part = easeInOut(seg(k, 0.56, 0.9));
  const fuseOut = easeInOut(seg(k, 0.56, 0.84));
  const cx = L.w / 2;
  const cy = L.tilesY;
  const wobble = Math.sin(seg(k, 0.82, 1) * Math.PI * 3) * (1 - seg(k, 0.82, 1)) * 6;
  return {
    wx: mix(mix(r.wx, cx - FUSED_W, a), cx - GAP - wobble, part),
    wy: mix(mix(r.wy, cy, a), cy, part),
    wa: mix(r.wa, 0, a),
    gx: mix(mix(r.gx, cx + FUSED_G, a), cx + GAP + wobble, part),
    gy: mix(mix(r.gy, cy, a), cy, part),
    ga: mix(r.ga, 0, a),
    size: 1 - 0.1 * part,
    fuse: fuseIn * (1 - fuseOut),
    seam: -1 + 2 * seg(k, 0.36, 0.58),
    check: easeOut(seg(k, 0.78, 1)),
  };
}

/* ---------------------------------------------------------------- the pull */
// While the tiles are melted together a finger can pull the glass out: the white tile is dragged a
// little way after it, the liquid neck between them thins, and past SNAP points it breaks — the
// neck runs back into both tiles in 0.14 s and the white one recoils.
/** The neck breaks when the centres are this far apart (the tiles' edges 44 pt apart). */
export const SNAP_DIST = 185;
export const TENSION = 0.15;
const SNAP = (SNAP_DIST - FUSED_W - FUSED_G) / (1 - TENSION);

/* ---------------------------------------------------------------- loop */

export const LOOP = 9.0;
export const T = {
  press: 1.0, // Connect
  connect0: 1.2,
  fused: 2.8, // link 0.5: one liquid tile (the fuse itself takes 1.2 s of this)
  grab: 3.1, // the scripted finger takes the glass and pulls it right
  snap: 4.2,
  settled: 5.0,
  done: 6.5,
  back0: 6.75,
  back1: 8.2,
} as const;
const PULL_X = SNAP + 4;

export function linkAt(t: number) {
  'worklet';
  if (t < T.connect0) return 0;
  if (t < T.snap) return 0.5 * easeInOut(seg(t, T.connect0, T.fused));
  if (t < T.back0) return 0.5 + 0.5 * easeOut(seg(t, T.snap, T.settled));
  return 1 - easeInOut(seg(t, T.back0, T.back1));
}
/** The scripted finger's pull on the glass (pt to the right), and the neck's hold (1 stuck … 0 broken). */
export function pullAt(t: number) {
  'worklet';
  if (t < T.grab) return 0;
  if (t < T.snap) return PULL_X * easeInOut(seg(t, T.grab, T.snap));
  // released at the snap: the glass carries on into its slot while the pull eases off
  return PULL_X * (1 - easeOut(seg(t, T.snap, T.settled)));
}
export function stickAt(t: number) {
  'worklet';
  if (t < T.snap) return 1;
  if (t < T.back0) return 1 - easeOut(seg(t, T.snap, T.snap + 0.14));
  return 1;
}
/** The white tile's recoil after the snap: dragged after the glass, then let go with a wobble. */
export function recoilAt(t: number) {
  'worklet';
  if (t < T.grab) return 0;
  if (t < T.snap) return TENSION * pullAt(t);
  const x = (t - T.snap) / 0.6;
  if (x >= 1) return 0;
  return TENSION * PULL_X * Math.exp(-4 * x) * Math.cos(9 * x) * (1 - x);
}
export function pressAt(t: number, at: number) {
  'worklet';
  return easeOut(seg(t, at, at + 0.1)) * (1 - easeOut(seg(t, at + 0.16, at + 0.36)));
}

/** The scripted finger: presence, and where (0 the button, 1 on the glass). */
export function fingerAt(t: number) {
  'worklet';
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.25)));
  const onButton = near(T.press, T.press + 0.25) + near(T.done, T.done + 0.25);
  const onGlass = near(T.grab, T.snap);
  return { finger: clamp01(onButton + onGlass), at: onGlass > 0.001 ? 1 : 0 };
}
