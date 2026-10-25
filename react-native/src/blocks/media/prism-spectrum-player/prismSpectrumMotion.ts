/*
 Prism Spectrum Player — timing and geometry. Every value the screen shows is a pure function of
 the loop time t (seconds, 0 ≤ t < LOOP), so the frame at t = LOOP equals the frame at t = 0.

 autoPlay timeline (LOOP = 8 s):
   0.0 – 1.2   rest: paused, a faint beam, short dim bars (the poster).
   1.08 – 1.32 the play button is pressed (scale 1 → 0.92 → 1).
   1.2 – 1.8   play: the beam brightens, the fan opens, the bars rise (ease-out cubic);
               the icon morphs play → pause over 1.2 – 1.45.
   1.8 – 6.4   playing; the elapsed time runs 1:12 → 1:17.
   3.0 – 5.6   the prism turns 0 → +18° → 0 → −18° → 0 (sin of an ease-in-out sweep); the fan
               follows 90 ms behind and swings 1.7× as far, opening wider as it turns.
   6.28 – 6.52 pause pressed; 6.4 – 7.0 the beam dims, bars fall (ease-in-out cubic).
   6.95 – 7.25 "back 5 s" pressed; 7.0 – 7.7 the scrubber glides back to 1:12.
   7.0 – 8.0   rest.
 */

export const LOOP = 8;
/** Beam strength while paused. */
export const REST_BEAM = 0.4;
/** Elapsed seconds at rest (1:12) and the track's length (3:42). */
export const REST_ELAPSED = 72;
export const TRACK_LENGTH = 222;
/** How far the prism may turn (radians, ±25°) and how far the script turns it (±18°). */
export const ROT_LIMIT = (25 * Math.PI) / 180;
const SWEEP = (18 * Math.PI) / 180;
/** The fan's lag behind the prism in the script (s), and how much more it swings. */
const FAN_LAG = 0.09;
export const FAN_GAIN = 1.7;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before a, 1 after b, linear between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

/** 0 → 1 → 0 over [a, b]. */
export function bump(t: number, a: number, b: number) {
  'worklet';
  return Math.sin(Math.PI * seg(t, a, b));
}

function sweep(t: number) {
  'worklet';
  return SWEEP * Math.sin(2 * Math.PI * easeInOutSine(seg(t, 3.0, 5.6)));
}

export type Pose = {
  /** Prism angle and the fan's (lagging) angle, radians. */
  rot: number;
  fan: number;
  /** 0 paused … 1 playing (bars), the beam's strength, and the play/pause icon morph. */
  play: number;
  beam: number;
  icon: number;
  /** Elapsed seconds of the track. */
  elapsed: number;
  /** Press amounts of the play and back-5 buttons (0 … 1). */
  pressPlay: number;
  pressBack: number;
};

export function loopPose(t: number): Pose {
  'worklet';
  const play = easeOutCubic(seg(t, 1.2, 1.8)) * (1 - easeInOutCubic(seg(t, 6.4, 7.0)));
  const icon = easeInOutCubic(seg(t, 1.2, 1.45)) * (1 - easeInOutCubic(seg(t, 6.4, 6.65)));
  const played = seg(t, 1.35, 6.55) * 5.2;
  const back = easeInOutCubic(seg(t, 7.0, 7.7));
  return {
    rot: sweep(t),
    fan: sweep(t - FAN_LAG),
    play,
    beam: REST_BEAM + (1 - REST_BEAM) * play,
    icon,
    elapsed: REST_ELAPSED + played * (1 - back),
    pressPlay: bump(t, 1.08, 1.32) + bump(t, 6.28, 6.52),
    pressBack: bump(t, 6.95, 7.25),
  };
}

export function restPose(): Pose {
  'worklet';
  return loopPose(0);
}

/** Past ±limit the prism resists: limit + a falling-off share of the overshoot. */
export function rubberBand(x: number, limit: number) {
  'worklet';
  const a = Math.abs(x);
  if (a <= limit) return x;
  const over = a - limit;
  return Math.sign(x) * (limit + (over * 0.55) / (1 + over / 0.35));
}

/** Screen layout, in points, from the canvas size and the safe area. */
export type Geo = {
  w: number;
  h: number;
  u: number;
  /** Prism centroid and circumradius. */
  cx: number;
  cy: number;
  r: number;
  /** Longest and shortest bar. */
  lmin: number;
  lmax: number;
  heroBottom: number;
  titleTop: number;
  scrubY: number;
  controlsY: number;
  side: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const heroTop = top + 56;
  const titleTop = Math.round(h * 0.585);
  const heroBottom = titleTop - 12;
  const u = Math.min(w / 393, (heroBottom - heroTop) / 371);
  const side = 204 * u;
  const r = side / Math.sqrt(3);
  const cx = w * 0.32;
  const cy = heroTop + (heroBottom - heroTop) * 0.5;
  // The exit point at rest sits at cx + 0.433 r; the bars must stop short of the right edge.
  const exitX = cx + 0.433 * r;
  const scrubY = titleTop + 88;
  const controlsY = Math.min(scrubY + 86, h - bottom - 128);
  return {
    w,
    h,
    u,
    cx,
    cy,
    r,
    lmin: 48 * u,
    lmax: Math.min(w - exitX - 18, 214 * u),
    heroBottom,
    titleTop,
    scrubY,
    controlsY,
    side,
  };
}

/** Where the beam enters (middle of the left face) and leaves (middle of the right face). */
export function prismPorts(g: Geo, rot: number) {
  'worklet';
  const c = Math.cos(rot);
  const s = Math.sin(rot);
  // Local points (apex up, y down): the midpoints of the two upper faces.
  const lx = -0.433 * g.r;
  const ly = -0.25 * g.r;
  const ex = 0.433 * g.r;
  return {
    inX: g.cx + c * lx - s * ly,
    inY: g.cy + s * lx + c * ly,
    outX: g.cx + c * ex - s * ly,
    outY: g.cy + s * ex + c * ly,
  };
}

/** The fan's centre direction and total spread (radians) for a fan angle and an opening. */
export function fanShape(fan: number, open: number) {
  'worklet';
  const dir = 0.4 + FAN_GAIN * fan;
  const spread = 0.48 * (1 + (0.9 * Math.abs(fan)) / ROT_LIMIT) * (0.78 + 0.22 * open);
  return { dir, spread };
}

export function formatTime(seconds: number) {
  'worklet';
  const s = Math.max(0, Math.floor(seconds));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${r < 10 ? '0' : ''}${r}`;
}
