/**
 * City Event Hero — the timeline. In autoPlay every value on screen is a pure function of the loop
 * time `t` (seconds, 0 ≤ t < LOOP); t = 0 is the pin's still and t = LOOP equals t = 0.
 *
 * Round 3: one evening at the tower, told by four effects that hand over to each other.
 *   0.00–0.70  rest (the pin)
 *   0.70–2.50  SUNDIAL: the sun breaks through and swings round the tower, 9:00 AM → 7:00 PM. Its light
 *              grades the sky, glints across the tower's glass, catches the sun-side edges of the
 *              buildings, and the tower's long shadow sweeps over the far city. The hour reads as a big
 *              clock in the sky BEHIND the tower (the photo is split into depth layers: sky, city,
 *              tower, near towers), while the camera slides left and the layers part (spatial photo).
 *   2.50–3.40  night falls: the city switches on outward, the tower floor by floor (round 2).
 *   3.20–3.45  the View event pill is pressed (the loop shows the gesture it stands for).
 *   3.45–6.40  LED SHOW (the hero): the façade becomes a screen — a scan climbs floor by floor, SEE
 *              YOU THERE lights letter by letter up the tower, the date counts down 31 → 17 JAN, then
 *              the viewer's portrait climbs the spire, shrinking to a point of light; the camera
 *              dollies in and tilts up with it. The façade's light spills on the city and the haze.
 *   6.40–8.70  the tip ignites: willow and two shells. Each burst relights the scene in its colour
 *              (sky, glass, buildings by depth), the title, the guests' rings and the glass; smoke drifts.
 *   8.00–9.00  night lifts back to the pin; the camera returns.   9.00–10.0  rest.
 *
 * Touch: drag the sky (left or down = later) to scrub the hour and the camera follows the finger
 * (release settles on the event's start, 9:00 AM, or its end, 11:45 PM); tap the sky to toggle;
 * tap View event for the LED show (night falls first if needed). Tilting the phone moves the camera.
 *
 * Positions are ref px (1206 px = 402 pt) unless they say photo px (`assets/city.jpg`, 850 × 1850).
 * Springs are given as SwiftUI/Reanimated `{ duration, dampingRatio }` and evaluated in closed form
 * (ω = 2π / duration) so the native ports land on the same values frame for frame.
 */
import { layout } from './theme';

export const LOOP = 10;
/** After the last touch, autoPlay waits this long, glides back to rest, then restarts at t = 0. */
export const RESUME_AFTER = 2000;
/** The glide back to rest before the loop restarts (ms). */
export const RETURN_MS = 800;

export type Spring = { duration: number; dampingRatio: number };

export const SPRINGS = {
  /** Guests travelling between stack and fan: a little overshoot, they were thrown. */
  fan: { duration: 0.55, dampingRatio: 0.72 },
  /** A guest rising into / dropping out of the spotlight. */
  spot: { duration: 0.4, dampingRatio: 0.62 },
  /** Press release, settles without overshoot to speak of. */
  ui: { duration: 0.4, dampingRatio: 0.8 },
  /** The heart's pop back from its dip. */
  pop: { duration: 0.32, dampingRatio: 0.3 },
  /** The chevron returning from its nudge. */
  nudge: { duration: 0.45, dampingRatio: 0.55 },
} as const satisfies Record<string, Spring>;

/** Seconds between neighbours: out, the outermost pair leaves first; back, the innermost. */
export const FAN_STAGGER = 0.045;

/** The fanned row: no overlap, centred on the stack, on an arc that drops at the sides. */
export const FAN = {
  gap: 18, // ref px between rings (6 pt)
  arcR: 2400, // ref px; the outer pair sits ~49 px lower than the centre
  centreRise: 8, // the centre guest moves up this much when the row opens
  tilt: 0.6, // share of the arc's tangent angle the portraits rotate by
  flightLift: 14, // ref px up at mid-flight
  flightScale: 0.03,
} as const;

export const SPOT = { lift: 18, scale: 0.07 } as const;

/** Rolling lines: the guest line and the like count. */
export const ROLL = { name: 0.32, nameRise: 26, count: 0.3, countRise: 22 } as const;

export const LIKE = {
  dip: 0.07, // s, the heart squeezes before it pops
  dipScale: 0.14,
  fill: 0.12, // s, the filled heart fades in from the dip
  ring: 0.5, // s
  ringFrom: 0.9, // × heart radius
  ringTo: 1.9,
  ringStroke: 3.5, // ref px at start, thins to 0
  unfill: 0.2, // s
} as const;

export const VIEW = {
  pressIn: 0.12,
  hold: 0.15,
  pressScale: 0.03, // 1 → 0.97
  nudge: 10, // ref px the chevron moves forward
  nudgeOut: 0.12,
  sweep: 0.5,
  /** Band widths the sweep starts left of the pill: just enough to begin off the glass. */
  bandLead: 1.05,
  band: 110, // ref px, width of the highlight band
  bandSkew: -20, // degrees
} as const;

export const AMBIENT = {
  mist: 0.15, // peak alpha of the haze layer (one slow breath per loop)
  drift: 22, // ref px per second the haze drifts to the left
} as const;

/**
 * Spatial photo: the photo is four baked layers (assets made once from a depth map: `near.png`,
 * `tower.png`, the plate behind them `fill.jpg`, `backdepth.png`). A camera offset `cam` (photo px)
 * moves each layer by `cam · p`, p its parallax share, and a dolly `z` scales it by `1 + z · p` about
 * the focus. The back plate's p grows with its depth: sky barely moves, the far city more.
 */
export const SPACE = {
  focusX: 428, // photo px: on the tower, two thirds of the way up
  focusY: 650,
  far: 0.08, // p of the sky
  perDepth: 0.32, // p added per unit of depth (depth.png × DEPTH_SCALE)
  tower: 0.5,
  near: 0.78,
  ampX: 32, // photo px at camX = 1
  ampY: 24,
  dolly: 0.1, // scale at camZ = 1 (× p)
  /** Extra zoom per photo px of offset, so no layer ever shows past the photo's edge. */
  coverX: 1 / 400,
  coverY: 1 / 600,
  /** The hour title floats between the sky and the tower. */
  title: 0.3,
  /** Touch: photo px of camera per dp of drag, and the most it goes (× amp) before it rubber-bands. */
  follow: 0.12,
  reach: 1.4,
  /** Tilt: photo px per unit of gravity change, and how fast the view re-centres on the hand's pose (s). */
  tilt: 30,
  tiltTau: 1.2,
} as const;

/** Depth maps store depth / DEPTH_SCALE (the depth model's relative units). */
export const DEPTH_SCALE = 5.2;

/** Camera spring after a drag. */
export const CAM_SPRING = { duration: 0.7, dampingRatio: 0.82 } as const;

/**
 * Sundial: `day` 0 → 1 is 9:00 AM → 7:00 PM. The sun's azimuth is measured clockwise from the view
 * direction (0 = straight ahead, 180 = behind the camera); it rises behind-right, passes behind the
 * viewer at noon and sets ahead-left, into the haze left of the tower.
 */
export const DAY = {
  from: 0.7,
  to: 2.5,
  hourFrom: 9,
  hourDusk: 19,
  hourEnd: 23.75, // the event ends 11:45 PM
  azFrom: 110, // degrees
  azTo: 345,
  elPeak: 50,
  rise: 7.2, // hours
  set: 19.1,
  /** The sun breaks through the overcast over the first 14 % of the day and fades from 93 %. */
  breakIn: 0.14,
  fadeFrom: 0.93,
} as const;

/** The camera (pinhole) the sun and shadow are projected with, photo px. */
export const VIEW_CAM = { f: 1000, cx: 425, horizon: 930 } as const;

/** Night falls: one progress `night` 0 → 1 drives the grade and the lights (shader). */
export const NIGHT = {
  fall: 2.5,
  fallEnd: 3.4,
  lift: 8.0,
  liftEnd: 9.0,
  /** Share of `night` over which the sky grades (the rest of the way the lights finish climbing). */
  grade: 0.7,
  /** `night` at which the lights start; the front then sweeps the light key 0 → 1. */
  frontFrom: 0.15,
  /** Light key band a window takes to switch on, and the width of the bright leading edge. */
  soft: 0.035,
  edge: 0.03,
  /** Drags: a horizontal drag over this share of the card's width scrubs a whole day, a vertical one
   *  over this share of its height a whole night. */
  scrubX: 0.6,
  scrub: 0.4,
  /** Flick speed (hours of `tod` per second) that commits a drag whatever its distance. */
  flick: 0.8,
  /** A tap on the sky runs the whole evening (or back) over this long (s). */
  toggle: 2.2,
  /** Fireworks fade with the night below this (a drag back to dusk mid-show). */
  fireFade: 0.6,
} as const;

/** The hour title in the sky. */
export const TITLE = {
  in: 0.75,
  inEnd: 1.0,
  out: 3.05,
  outEnd: 3.4,
  /** After a drag, it stays this long, then fades over `fade` (s). */
  hold: 0.6,
  fade: 0.35,
  /** Minutes the clock steps by. */
  step: 5,
  /** Baseline (ref px) and cap size (pt at 402). */
  base: 640,
  size: 92,
  suffix: 26,
  /** pt from the tower's axis to the hours' right edge and the minutes' left edge. */
  sep: 8,
} as const;

/** The View event pill pressed by the loop (s). */
export const CTA = {
  press: 3.2,
  release: 3.4,
  /** Touch, by day: the evening falls this fast before the show starts (s). */
  dusk: 1.1,
} as const;

/** Spring that finishes a released drag. */
export const NIGHT_SPRING = { duration: 0.6, dampingRatio: 1 } as const satisfies Spring;

/** The spire tip in photo px of `assets/city.jpg` (850 × 1850), and the tower's foot. */
export const TOWER = { tipX: 428, tipY: 293, baseY: 1320 } as const;

/**
 * The LED façade. The tower is read as a cylinder: across it, s = (x − mid) / halfWidth(y) and the
 * column is the angle asin(s) (so columns crowd at the edges, as they do on a round tower); along it,
 * two LED rows per floor (3 photo px each), the floor lines sagging away from the eye level like rings
 * seen in perspective. The message is shown a word at a time, upright letters stacked top to bottom
 * (a vertical sign), each glyph pixel 3 columns × 5 rows; every word climbs in from below, holds,
 * and runs up off the spire as the next one arrives. Times are seconds after the show starts.
 */
export const LED = {
  at: 3.45, // autoplay
  cols: 29,
  row: 3, // photo px per LED row
  mid: 427.5,
  eye: 930,
  sag: 0.035,
  rows: 342, // foot to tip
  /** Boot: a scan climbs the tower. */
  boot: 0.35,
  /** The words, then the date ("dd JAN", the day counting down `dateFrom` → `dateTo`). */
  words: ['SEE', 'YOU', 'THERE'],
  pages: [0.3, 0.72, 1.14, 1.62], // each page arrives; the last leaves at `pagesEnd`
  pagesEnd: 2.22,
  enter: 0.16, // s to climb into place
  leave: 0.1, // s to run up and off
  climb: 300, // rows it runs up by as it leaves
  drift: 20, // rows per second while it holds
  /** Centre row of a page at rest (floor 100, the tower's wide middle, clear of the guests). */
  centre: 200,
  slot: 40, // rows per letter (35 lit + 5 gap)
  dateGap: 8, // extra rows between the day and "JAN"
  colsFrom: 7, // first column of a glyph (glyph = 15 of 29 columns)
  glyphCols: 3,
  glyphRows: 5,
  stagger: 0.04, // letters of a page light top to bottom
  letterFlash: 0.18,
  dateFrom: 31,
  dateTo: 17,
  roll: 0.4, // s the day takes to count down after the date page arrives
  /** The viewer's portrait: appears on the tower's middle, then climbs to the tip, shrinking with it. */
  avatarIn: 2.2,
  avatarInEnd: 2.36,
  avatarRow: 150,
  climbEnd: 2.95,
  /** Portrait radius as a share of the tower's half width where it is (> 1: it wraps round the sides). */
  avatarR: 1.5,
  /** The tip ignites when the portrait reaches it; the façade dims as the fireworks take over. */
  fire: 2.95,
  out: 2.95,
  outEnd: 3.4,
  /** The façade's own colour field: one wave per this many rows, travelling up. */
  fieldWave: 120,
  fieldSpeed: 180,
} as const;

/** Half width of the tower (photo px) every 16 rows from its tip (y 293) to its foot; traced once. */
export const TOWER_HW = {
  y0: 293,
  step: 16,
  hw: [
    0, 4.2, 8, 11.1, 13.9, 16.6, 18.2, 19, 20, 21, 21.8, 22.1, 23, 24, 25, 25.8, 26.1, 27, 28, 28.9, 29.1, 30, 30, 30.5, 32, 32, 32.8, 34, 34, 34.9, 36, 36,
    37, 37.9, 38, 39, 39.5, 40, 40, 40.3, 41, 41, 42, 42, 42, 42, 42, 42, 42.5, 43, 43, 43.5, 43.5, 43.6, 44.4, 44.5, 44.5, 45, 45.5, 46, 46, 46, 46, 46.1, 46,
  ],
} as const;

export type Burst = {
  at: number; // s after ignition
  dx: number; // ref px from the spire tip
  dy: number;
  /** Shell launched from (dx + launchDx, dy + launchDy); 0 = bursts in place. */
  launchDx: number;
  launchDy: number;
  count: number;
  speed: number; // ref px/s
  /** 0 = a peony: sparks on a sphere seen from the side (fills a disc). Else a fountain: sparks
   *  fan upward within ± spread radians of straight up. */
  spread: number;
  /** Fountain only: share of speed taken off the sparks nearest straight up (keeps it under the top bar). */
  upSlow: number;
  drag: number; // 1/s
  gravity: number; // ref px/s²
  life: number; // s
  trail: number; // s of path behind each spark
  /** Last share of life a spark crackles (flickers) before it dies. */
  crackle: number;
  hot: string; // colour at birth, at its burst colour by 25 % of life, ember at the end
  color: string;
  ember: string;
  flash: number; // light it throws on the clouds, 0..1
};

export const FIRE = {
  /** Ignition, in autoplay: the moment the portrait reaches the tip. */
  at: LED.at + LED.fire,
  /** Total length of the fireworks (s after ignition); everything is dark again by then. */
  end: 2.3,
  /** The tip ignites: a flare that swells and fades. */
  flareIn: 0.15,
  flareOut: 0.55,
  /** Flash on the clouds: attack and decay (s). */
  flashIn: 0.05,
  flashTau: 0.32,
  /** Light on the clouds at full flash (× the sky's own brightness). */
  flashGain: 1.1,
  /** Radius of the light a burst throws, photo px. */
  flashR: 260,
  /** Shells rise this long before they burst (s). */
  rise: 0.45,
  /** Smoke a burst leaves: it grows to `smokeR` dp, drifts up and fades (s). */
  smokeIn: 0.25,
  smokeTau: 1.1,
  smokeR: 70,
  smokeRise: 18,
  /** Light falls off with depth too: dp per unit of depth. */
  depthDp: 70,
  bursts: [
    // Gold willow fountain from the spire tip: up and out, then raining down beside the tower.
    { at: 0.12, dx: 0, dy: 0, launchDx: 0, launchDy: 0, count: 96, speed: 520, spread: 1.95, upSlow: 0.5, drag: 1.8, gravity: 360, life: 1.9, trail: 0.26, crackle: 0.15, hot: '#FFF7E6', color: '#FFC266', ember: '#E8642A', flash: 1 },
    // Silver crackle, shell from the rooftops left of the tower.
    { at: 0.55, dx: -270, dy: 330, launchDx: 25, launchDy: 600, count: 64, speed: 540, spread: 0, upSlow: 0, drag: 2.6, gravity: 300, life: 1.5, trail: 0.12, crackle: 0.45, hot: '#FFFFFF', color: '#E4ECFF', ember: '#9DB4FF', flash: 0.7 },
    // Red peony, shell from the rooftops right of the tower.
    { at: 0.72, dx: 260, dy: 260, launchDx: -20, launchDy: 640, count: 64, speed: 560, spread: 0, upSlow: 0, drag: 2.6, gravity: 300, life: 1.4, trail: 0.13, crackle: 0.25, hot: '#FFF1E8', color: '#FF6B57', ember: '#C2311F', flash: 0.75 },
  ] as readonly Burst[],
} as const;

/**
 * How a spark is drawn (ref px). Buckets by remaining life: bright (> 50 %), fading, crackling
 * (the last `burst.crackle` of life, where each spark flickers at `flicker` Hz). Core = the newest
 * 40 % of the trail, thin and solid; glow = the whole trail, wide and blurred.
 */
export const SPARK = {
  coreWidth: [3.4, 2.8, 2.2],
  coreAlpha: [1, 0.8, 0.6],
  glowWidth: [12, 9, 7],
  glowAlpha: [0.38, 0.26, 0.18],
  glowBlur: 5,
  coreShare: 0.4,
  flicker: 30,
  flickerKeep: 0.55,
  /** Per-spark spread: speed ×(0.8 + 0.3 h), life ×(0.75 + 0.35 h), angle jitter ±0.3 slot. */
  speedBase: 0.8,
  speedSpread: 0.3,
  lifeBase: 0.75,
  lifeSpread: 0.35,
  angleJitter: 0.6,
  /** A shell's own trail while it rises (s) and its width. */
  /** A shell: a bright head (the last `shellCore` s of its path) and a soft trail (`shellTrail` s). */
  shellCore: 0.02,
  shellTrail: 0.1,
  shellWidth: 3.2,
  /** A burst's glow fades in over this long, so its first frames are a point of light, not a blob. */
  glowIn: 0.15,
  /** …starting this long after the break, once the stars have left its centre. */
  glowDelay: 0.08,
  /** Cores fade in from 0 over this long after the break, so no frame shows a filled disc. */
  coreIn: 0.08,
  /** Radius (ref px) of the shell's break the stars leave from. */
  breakRadius: 30,
} as const;

/** Reduced motion: no loop, state changes are ≤ 200 ms fades. */
export const REDUCED = { fade: 0.2 } as const;

/* ───────────────────────────── curves ───────────────────────────── */

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

/** Solve the curve's x before sampling y; shared by all ports. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const m = (lo + hi) / 2;
    const u = 1 - m;
    const at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m;
    if (at < x) lo = m;
    else hi = m;
  }
  const m = (lo + hi) / 2;
  const u = 1 - m;
  return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m;
}

/** cubic-bezier(0.23, 1, 0.32, 1) */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** cubic-bezier(0.77, 0, 0.175, 1) */
export function easeInOut(p: number) {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
/** A tap on the sky: answers at once (no slow start), still lets the night take its time to settle. */
export const EASE_NIGHT_TAP = [0.3, 0.55, 0.3, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

/** A damped spring from 0 to 1, `tau` seconds after it starts (0 before). */
export function spring(tau: number, s: Spring) {
  'worklet';
  if (tau <= 0) return 0;
  const omega = (2 * Math.PI) / s.duration;
  const zeta = s.dampingRatio;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  if (env < 1e-4) return 1;
  return 1 - env * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
}

/* ───────────────────────────── geometry ───────────────────────────── */

export type Guest = { cx: number; cy: number; d: number; img: number };

/** The guests left → right (`layout.guests` is in drawing order, outermost first). */
export const SLOTS: readonly Guest[] = [...layout.guests].sort((a, b) => a.cx - b.cx);
export const CENTRE_SLOT = 3;

/** Distance in slots from the centre: 0 for the top guest, 3 for the outer pair. */
export function rank(slot: number) {
  'worklet';
  return Math.abs(slot - CENTRE_SLOT);
}

/** Where each slot sits in the fanned row, and its tilt (degrees, clockwise). */
export const FANNED: readonly { cx: number; cy: number; rot: number }[] = (() => {
  const total = SLOTS.reduce((s, g) => s + g.d, 0) + FAN.gap * (SLOTS.length - 1);
  const centre = SLOTS[CENTRE_SLOT];
  let x = centre.cx - total / 2;
  return SLOTS.map((g) => {
    const cx = x + g.d / 2;
    x += g.d + FAN.gap;
    const dx = cx - centre.cx;
    const drop = FAN.arcR - Math.sqrt(FAN.arcR * FAN.arcR - dx * dx);
    const rot = ((Math.asin(dx / FAN.arcR) * 180) / Math.PI) * FAN.tilt;
    return { cx, cy: centre.cy - FAN.centreRise + drop, rot };
  });
})();

/** Lift while travelling: 0 at both ends, 1 half way. */
export function flight(fan: number) {
  'worklet';
  const f = clamp01(fan);
  return 4 * f * (1 - f);
}

/* ───────────────────────────── frames ───────────────────────────── */

/** Every animated scalar of the screen. 0 everywhere (pop 1) is the pin. */
export type Frame = {
  fan: number[]; // per slot, 0 stack → 1 fanned (springs pass 1 a little)
  spot: number[]; // per slot, 0 → 1 risen into the spotlight
  nameFrom: number; // guest image index of the outgoing line
  nameTo: number;
  nameP: number; // 0 = nameFrom shown, 1 = nameTo shown
  like: number; // filled heart
  pop: number; // heart scale
  ring: number; // burst progress, drawn only strictly between 0 and 1
  count: number; // 0 = "1", 1 = "2"
  press: number; // View event pill pressed
  chev: number; // chevron nudge 0..1
  sweep: number; // highlight across the pill, 0 and 1 are off the pill
  mist: number; // haze 0..1
  drift: number; // haze offset, seconds of drift
  day: number; // sundial: 0 = 9:00 AM (the pin), 1 = 7:00 PM, the sun set
  night: number; // 0 = the pin's light, 1 = full night with every light on
  show: number; // seconds since the LED show started; < 0 = no show (fireworks are part of it)
  camX: number; // spatial camera, × SPACE.ampX / ampY; camZ = dolly 0..1
  camY: number;
  camZ: number;
  title: number; // the hour title in the sky, 0..1
};

export const SLOT_COUNT = SLOTS.length;
/** The rest line names this guest (Amira, image 0). */
export const REST_NAME = 0;

export function restFrame(): Frame {
  'worklet';
  const zeros: number[] = [];
  for (let i = 0; i < SLOT_COUNT; i++) zeros.push(0);
  return {
    fan: zeros,
    spot: zeros.slice(),
    nameFrom: REST_NAME,
    nameTo: REST_NAME,
    nameP: 0,
    like: 0,
    pop: 1,
    ring: 0,
    count: 0,
    press: 0,
    chev: 0,
    sweep: 0,
    mist: 0,
    drift: 0,
    day: 0,
    night: 0,
    show: -1,
    camX: 0,
    camY: 0,
    camZ: 0,
    title: 0,
  };
}

/* ───────────────────────────── night and fireworks ───────────────────────────── */

/** Sine in-out: a dusk does not snap, the grade needs the whole stretch to read. */
export function easeSine(p: number) {
  'worklet';
  return (1 - Math.cos(Math.PI * clamp01(p))) / 2;
}

export function nightAt(t: number) {
  'worklet';
  if (t < NIGHT.fall || t >= NIGHT.liftEnd) return 0;
  if (t < NIGHT.fallEnd) return easeSine(seg(t, NIGHT.fall, NIGHT.fallEnd));
  if (t < NIGHT.lift) return 1;
  return 1 - easeSine(seg(t, NIGHT.lift, NIGHT.liftEnd));
}

/** Seeded 0..1 noise for spark `i` of burst `b`, channel `k`; the same in every port. */
export function hash3(b: number, i: number, k: number) {
  'worklet';
  const v = Math.sin(b * 127.1 + i * 311.7 + k * 74.7) * 43758.5453;
  return v - Math.floor(v);
}

/** Light a burst throws `tau` seconds after it bursts (0..flash): fast attack, exponential decay. */
export function flashAt(burst: Burst, tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const attack = tau < FIRE.flashIn ? tau / FIRE.flashIn : 1;
  return burst.flash * attack * Math.exp(-(tau - Math.min(tau, FIRE.flashIn)) / FIRE.flashTau);
}

/** The spire tip's flare `fire` seconds after ignition, 0..1. */
export function flareAt(fire: number) {
  'worklet';
  if (fire < 0) return 0;
  if (fire < FIRE.flareIn) return easeOut(fire / FIRE.flareIn);
  return 1 - easeOut(seg(fire, FIRE.flareIn, FIRE.flareIn + FIRE.flareOut));
}

/** How much the fireworks light the scene (headline glow, guests' rims), 0..1. */
export function fireLight(fire: number) {
  'worklet';
  if (fire < 0 || fire >= FIRE.end) return 0;
  let sum = 0.6 * flareAt(fire);
  for (let b = 0; b < FIRE.bursts.length; b++) sum += flashAt(FIRE.bursts[b], fire - FIRE.bursts[b].at);
  return clamp01(sum);
}

/** Displacement (ref px) of a spark launched at `speed` along `angle`, `tau` s later: linear drag `k` + gravity `g`. */
export function sparkOffset(speed: number, angle: number, tau: number, k: number, g: number, out: { x: number; y: number }) {
  'worklet';
  const e = 1 - Math.exp(-k * tau);
  const r = (speed * e) / k;
  out.x = Math.cos(angle) * r;
  out.y = Math.sin(angle) * r + g * (tau / k - e / (k * k));
}

/** Launch angle and speed of spark `i` of burst `b` (seeded, the same in every port). */
export function sparkLaunch(b: number, i: number, out: { angle: number; speed: number }) {
  'worklet';
  const B = FIRE.bursts[b];
  const u = (i + SPARK.angleJitter * (hash3(b, i, 0) - 0.5)) / B.count;
  const s = SPARK.speedBase + SPARK.speedSpread * hash3(b, i, 2);
  if (B.spread > 0) {
    const side = 2 * u - 1;
    out.angle = -Math.PI / 2 + B.spread * side;
    out.speed = B.speed * s * (1 - B.upSlow * (1 - Math.abs(side)));
  } else {
    // A sphere of sparks seen from the side: the depth component shortens the screen speed.
    const z = 2 * hash3(b, i, 3) - 1;
    out.angle = 2 * Math.PI * u;
    out.speed = B.speed * s * Math.sqrt(1 - z * z);
  }
}

/** Where a shell is `tau` s into its rise (ease-out), as a share of the launch offset still to go. */
export function shellLeft(tau: number) {
  'worklet';
  return 1 - easeOut(clamp01(tau / FIRE.rise));
}

/* ───────────────────────────── round 3: sundial, camera, LED ───────────────────────────── */

/** Length of the whole show: the LED part, then the fireworks. */
export const SHOW_END = LED.fire + FIRE.end;

/** A reduced-motion tap shows the façade frozen here (the message fully lit). */
export const LED_STILL = 2.0; // the date page, counted down

/** Seconds since ignition during a show, or -1. */
export function fireOf(show: number) {
  'worklet';
  if (show < LED.fire || show >= SHOW_END) return -1;
  return show - LED.fire;
}

/** The hour shown for a day/night pair (24 h clock, fractional). */
export function hourOf(day: number, night: number) {
  'worklet';
  if (night > 0) return DAY.hourDusk + (DAY.hourEnd - DAY.hourDusk) * night;
  return DAY.hourFrom + (DAY.hourDusk - DAY.hourFrom) * day;
}

/** Hour and minutes as the clock shows them (12 h, minutes stepped). */
export function clockOf(hour: number, out: { h: number; m: number; pm: boolean }) {
  'worklet';
  const total = Math.floor((hour * 60) / TITLE.step + 1e-6) * TITLE.step;
  const h24 = Math.floor(total / 60) % 24;
  out.m = total - Math.floor(total / 60) * 60;
  out.pm = h24 >= 12;
  const h12 = h24 % 12;
  out.h = h12 === 0 ? 12 : h12;
}

/** The sun at `day`: azimuth and elevation (radians) and how much of it breaks through (0..1). */
export function sunOf(day: number, out: { az: number; el: number; amt: number }) {
  'worklet';
  const d = clamp01(day);
  const h = DAY.hourFrom + (DAY.hourDusk - DAY.hourFrom) * d;
  out.az = ((DAY.azFrom + (DAY.azTo - DAY.azFrom) * d) * Math.PI) / 180;
  const arc = clamp01((h - DAY.rise) / (DAY.set - DAY.rise));
  out.el = ((DAY.elPeak * Math.sin(Math.PI * arc)) * Math.PI) / 180;
  const brk = d / DAY.breakIn;
  const inn = brk >= 1 ? 1 : brk * brk * (3 - 2 * brk);
  const f = clamp01((d - DAY.fadeFrom) / (1 - DAY.fadeFrom));
  out.amt = inn * (1 - f * f * (3 - 2 * f));
}

/** Half width of the tower (photo px) at photo row `y`; 0 above the tip. */
export function towerHalfWidth(y: number) {
  'worklet';
  const k = (y - TOWER_HW.y0) / TOWER_HW.step;
  if (k <= 0) return 0;
  const n = TOWER_HW.hw.length - 1;
  if (k >= n) return TOWER_HW.hw[n];
  const i = Math.floor(k);
  return TOWER_HW.hw[i] + (TOWER_HW.hw[i + 1] - TOWER_HW.hw[i]) * (k - i);
}

function cube(p: number) {
  'worklet';
  const c = clamp01(p);
  return c * c * c;
}

/** Letters on page `p` (the words, then the date's five: two digits and J A N). */
export function pageLength(p: number) {
  'worklet';
  return p < LED.words.length ? LED.words[p].length : 5;
}

/** Height of page `p` in LED rows. */
export function pageRows(p: number) {
  'worklet';
  return pageLength(p) * LED.slot - (LED.slot - LED.glyphRows * 7) + (p >= LED.words.length ? LED.dateGap : 0);
}

/**
 * The LED show's uniforms `lt` seconds after it starts, written into `o` (16 numbers):
 *   0 on (panel lit 0..1), 1 scan front (rows; huge = all on, < 0 = not started), 2 page (−1 none),
 *   3 page's bottom row, 4 letters lit (fractional, from the top), 5 the day number, 6 portrait centre
 *   (photo px y), 7 its radius (photo px), 8 portrait visible, 9 white flash, 10 colour-field phase
 *   (rows), 11 light spilled on the city 0..1, 12 dissolve 0..1.
 */
export function ledOf(lt: number, o: number[]) {
  'worklet';
  for (let i = 0; i < 16; i++) o[i] = 0;
  o[2] = -1;
  if (lt < 0 || lt >= SHOW_END) {
    o[1] = -1;
    return;
  }
  const dissolve = easeSine(seg(lt, LED.out, LED.outEnd));
  o[0] = clamp01(lt / 0.12) * (1 - dissolve);
  o[1] = lt < LED.boot ? easeInOut(lt / LED.boot) * (LED.rows + 20) : 1e4;
  const n = LED.pages.length;
  for (let p = 0; p < n; p++) {
    const from = LED.pages[p];
    const to = p + 1 < n ? LED.pages[p + 1] : LED.pagesEnd;
    if (lt < from || lt >= to) continue;
    const h = pageRows(p);
    const rest = LED.centre - h / 2;
    const enter = easeOut(seg(lt, from, from + LED.enter));
    const leave = seg(lt, to - LED.leave, to);
    o[2] = p;
    o[3] = -h + (rest + h) * enter + LED.drift * (lt - from) + LED.climb * leave * leave * leave;
    o[4] = (lt - from) / LED.stagger;
  }
  const rp = LED.pages[n - 1];
  o[5] = Math.round(LED.dateFrom - (LED.dateFrom - LED.dateTo) * easeOut(seg(lt, rp + LED.enter * 0.5, rp + LED.enter * 0.5 + LED.roll)));
  const climb = cube(seg(lt, LED.avatarInEnd, LED.climbEnd));
  const rowAt = LED.avatarRow + (LED.rows - 6 - LED.avatarRow) * climb;
  const y = TOWER.baseY - rowAt * LED.row;
  o[6] = y;
  o[7] = LED.avatarR * towerHalfWidth(y) * easeOut(seg(lt, LED.avatarIn, LED.avatarInEnd));
  o[8] = lt >= LED.avatarIn && lt < LED.climbEnd + 0.06 ? 1 : 0;
  // The façade flashes white when the boot scan tops out and when the tip ignites.
  const b = lt - LED.boot;
  const f = lt - LED.fire;
  o[9] = (b > 0 ? 0.55 * Math.exp(-b / 0.12) : 0) + (f > 0 ? Math.exp(-f / 0.2) : 0);
  o[10] = lt * LED.fieldSpeed;
  o[11] = o[0] * (0.55 + 0.45 * Math.max(o[2] >= 0 ? 1 : 0, o[8])) + 0.4 * o[9];
  o[12] = dissolve;
}

/** Spatial camera at loop time `t` (autoplay). */
export function camOf(t: number, out: { x: number; y: number; z: number }) {
  'worklet';
  out.x = t < DAY.to ? -easeSine(seg(t, DAY.from, DAY.to)) : -(1 - easeSine(seg(t, DAY.to, LED.at)));
  const back = 1 - easeSine(seg(t, NIGHT.lift, NIGHT.liftEnd));
  out.z = t < NIGHT.lift ? easeSine(seg(t, LED.at, LED.at + 2.55)) : back;
  out.y = t < NIGHT.lift ? easeSine(seg(t, LED.at + LED.avatarIn - 0.05, LED.at + LED.climbEnd)) : back;
}

/** The sundial's progress at loop time `t`; it drops to 0 under full night, where it no longer shows. */
export function dayAt(t: number) {
  'worklet';
  if (t >= NIGHT.fallEnd) return 0;
  return easeSine(seg(t, DAY.from, DAY.to));
}

/** The View event pill pressed by the loop: press, chevron nudge, highlight sweep. */
export function ctaOf(t: number, out: { press: number; chev: number; sweep: number }) {
  'worklet';
  const r = CTA.release;
  out.press = t < r ? easeOut(seg(t, CTA.press, CTA.press + VIEW.pressIn)) : 1 - spring(t - r, SPRINGS.ui);
  out.chev = t < r ? 0 : t < r + VIEW.nudgeOut ? easeOut(seg(t, r, r + VIEW.nudgeOut)) : 1 - spring(t - r - VIEW.nudgeOut, SPRINGS.nudge);
  const sw = seg(t, r, r + VIEW.sweep);
  out.sweep = sw > 0 && sw < 1 ? easeInOut(sw) : 0;
}

export function frameAt(t: number): Frame {
  'worklet';
  const f = restFrame();
  f.day = dayAt(t);
  f.night = nightAt(t);
  f.show = t >= LED.at && t < LED.at + SHOW_END ? t - LED.at : -1;
  const c = { x: 0, y: 0, z: 0 };
  camOf(t, c);
  f.camX = c.x;
  f.camY = c.y;
  f.camZ = c.z;
  f.title = easeOut(seg(t, TITLE.in, TITLE.inEnd)) * (1 - easeSine(seg(t, TITLE.out, TITLE.outEnd)));
  const p = { press: 0, chev: 0, sweep: 0 };
  ctaOf(t, p);
  f.press = p.press;
  f.chev = p.chev;
  f.sweep = p.sweep;
  // Ambient: the haze breathes once per loop, zero velocity at both ends.
  f.mist = (1 - Math.cos((2 * Math.PI * t) / LOOP)) / 2;
  f.drift = t;
  return f;
}
