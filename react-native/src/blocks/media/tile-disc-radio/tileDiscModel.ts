/*
 Tile Disc Radio — stations, tile layouts, the change timeline and the driver (a port of
 TileDiscRadioModel.swift; no drawing here). Every function is a worklet: the driver lives in a shared
 value and is stepped on the UI thread.

 A station is a palette and a 4 × 4 tile layout. A cell code is kind × 16 + mask: kind 0 empty, 1 full,
 2 top + bottom halves, 3 left + right halves, 4…7 one half only (top, right, bottom, left); the mask
 holds the tile's big ("petal") corners, TL 1, TR 2, BR 4, BL 8, clockwise.

 Layouts are written as they look on screen. The disc keeps turning a quarter turn per change, so after
 k quarter turns its local cell (c, r) shows screen cell rot^k(c, r), with its kind and corners turned
 too: a station's resting disc looks the same whatever k is, and the loop closes after three changes.
*/

export type Station = {
  name: string;
  call: string;
  freq: number;
  show: string;
  vol: string;
  listening: string;
  frame: number;
  bgTile: number;
  line: number;
  tile: number;
};

/** One tile of a cell, in cell units (0…1), with its big corners. */
export type Tile = { x: number; y: number; w: number; h: number; mask: number };

/** A station change (or, with dir 0, the disc springing back to rest after a short drag). */
export type Change = {
  from: number;
  to: number;
  /** Quarter turns of the disc before the change. */
  kFrom: number;
  /** +1 next (clockwise), −1 previous, 0 none. */
  dir: number;
  t0: number;
  /** Disc angle in degrees when the change starts. */
  a0: number;
  /** Leans back 4° before turning (a tap); a change thrown by a drag starts at once. */
  antic: boolean;
  /** The transport glyph that was pressed: −1 previous, +1 next, 0 none. */
  press: number;
};

export type Frame = {
  ch: Change;
  /** Seconds since the change started (large once settled). */
  ct: number;
  /** Disc angle in degrees. */
  angle: number;
  playing: boolean;
  /** A clock for the live dot (loop time in autoPlay). */
  clock: number;
  reduced: boolean;
};

export const LOOP = 9;
/** Loop times at which autoPlay presses "next". */
const SCRIPT = [1.5, 4.3, 7.1];
/** A change has settled after this long; rows are still sliding until BUSY. */
const DONE = 1.8;
const BUSY = 1.0;
const IDLE_BEFORE_BACK = 2;
const NONE = -1e9;

export const STATIONS: Station[] = [
  {
    name: 'Lumen Sounds',
    call: 'KVRA',
    freq: 91.7,
    show: 'Night Shift with Ora Lind',
    vol: 'VOL.13',
    listening: '2.4k',
    frame: 0x8a08fd,
    bgTile: 0xfc57e1,
    line: 0xfc1e53,
    tile: 0xfdb400,
  },
  {
    name: 'Field Notes',
    call: 'KOAL',
    freq: 98.3,
    show: 'Low Tide with Remi Okafor',
    vol: 'VOL.14',
    listening: '1.8k',
    frame: 0x007a42,
    bgTile: 0x8cf21e,
    line: 0x253df2,
    tile: 0x1cc8fd,
  },
  {
    name: 'Ember Hours',
    call: 'KWEN',
    freq: 104.5,
    show: 'Morning Drift with Ines Varga',
    vol: 'VOL.15',
    listening: '3.1k',
    frame: 0xf2511b,
    bgTile: 0xffa98a,
    line: 0x2a1fd1,
    tile: 0xfff0d6,
  },
];

/** Disc layouts, row by row as seen on screen. */
const DISC_LAYOUTS = [
  [0x00, 0x18, 0x13, 0x12, 0x00, 0x19, 0x10, 0x36, 0x00, 0x18, 0x14, 0x29, 0x12, 0x2c, 0x16, 0x18],
  [0x18, 0x14, 0x13, 0x12, 0x11, 0x10, 0x10, 0x16, 0x6c, 0x68, 0x26, 0x6c, 0x19, 0x10, 0x33, 0x14],
  [0x12, 0x19, 0x16, 0x18, 0x36, 0x00, 0x13, 0x10, 0x10, 0x56, 0x1c, 0x29, 0x13, 0x18, 0x10, 0x16],
];

/** The background behind the disc (the same for every station; only its colour changes). */
export const BG_LAYOUT = [0x19, 0x13, 0x13, 0x16, 0x18, 0x10, 0x10, 0x34, 0x29, 0x10, 0x10, 0x16, 0x19, 0x1c, 0x1c, 0x14];

// Tiles

export function tiles(code: number): Tile[] {
  'worklet';
  const m = code & 15;
  switch (code >> 4) {
    case 1:
      return [{ x: 0, y: 0, w: 1, h: 1, mask: m }];
    case 2:
      return [
        { x: 0, y: 0, w: 1, h: 0.5, mask: m & 3 },
        { x: 0, y: 0.5, w: 1, h: 0.5, mask: m & 12 },
      ];
    case 3:
      return [
        { x: 0, y: 0, w: 0.5, h: 1, mask: m & 9 },
        { x: 0.5, y: 0, w: 0.5, h: 1, mask: m & 6 },
      ];
    case 4:
      return [{ x: 0, y: 0, w: 1, h: 0.5, mask: m }];
    case 5:
      return [{ x: 0.5, y: 0, w: 0.5, h: 1, mask: m }];
    case 6:
      return [{ x: 0, y: 0.5, w: 1, h: 0.5, mask: m }];
    case 7:
      return [{ x: 0, y: 0, w: 0.5, h: 1, mask: m }];
    default:
      return [];
  }
}

/** The code of local cell (c, r) of a station's disc after k quarter turns clockwise. */
export function localCode(station: number, k: number, c: number, r: number) {
  'worklet';
  const q = ((k % 4) + 4) % 4;
  let sc = c;
  let sr = r;
  for (let n = 0; n < q; n++) {
    const nc = 3 - sr;
    sr = sc;
    sc = nc;
  }
  const code = DISC_LAYOUTS[station][sr * 4 + sc];
  let kind = code >> 4;
  const m = code & 15;
  let mask = 0;
  for (let b = 0; b < 4; b++) if ((m & (1 << ((b + q) % 4))) !== 0) mask |= 1 << b;
  if (kind === 2 || kind === 3) {
    if (q % 2 === 1) kind = kind === 2 ? 3 : 2;
  } else if (kind >= 4) {
    // A half seen on screen at side s sits at local side s − q.
    kind = 4 + ((((kind - 4 - q) % 4) + 4) % 4);
  }
  return (kind << 4) | mask;
}

// Curves

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOut(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

/** A spring's remaining distance (1 → 0) from rest, released with no speed. */
export function springDecay(t: number, response: number, damping: number) {
  'worklet';
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return Math.exp(-zw * t) * (Math.cos(wd * t) + (zw / wd) * Math.sin(wd * t));
}

// Timeline of a change (ct = seconds since it started)

export function angleOf(ch: Change, ct: number) {
  'worklet';
  const a1 = (ch.kFrom + ch.dir) * 90;
  if (ch.dir === 0) {
    if (ct >= DONE) return a1;
    return a1 + (ch.a0 - a1) * springDecay(ct, 0.45, 0.8);
  }
  if (ch.antic) {
    const pre = ch.a0 - 4 * ch.dir;
    if (ct < 0.12) return lerp(ch.a0, pre, easeOutCubic(Math.max(0, ct) / 0.12));
    const t = ct - 0.12;
    if (t >= DONE - 0.12) return a1;
    return a1 + (pre - a1) * springDecay(t, 0.8, 0.7);
  }
  if (ct >= DONE) return a1;
  return a1 + (ch.a0 - a1) * springDecay(Math.max(0, ct), 0.8, 0.7);
}

/** How far local row r has run its conveyor (0 → 1). */
export function rowP(ct: number, r: number) {
  'worklet';
  const s = 0.1 + 0.06 * r;
  return easeInOutCubic(seg(ct, s, s + 0.72));
}

export function gapMix(ct: number) {
  'worklet';
  return easeInOut(seg(ct, 0.3, 0.7));
}

export function pageMix(ct: number) {
  'worklet';
  return easeInOut(seg(ct, 0.2, 0.8));
}

export function freqMix(ct: number) {
  'worklet';
  return easeInOutCubic(seg(ct, 0.1, 1.0));
}

export function bgMix(ct: number, i: number, j: number, dir: number) {
  'worklet';
  const s = 0.15 + 0.045 * ((dir >= 0 ? i : 3 - i) + j);
  return easeInOut(seg(ct, s, s + 0.3));
}

export function wordsOut(ct: number) {
  'worklet';
  return 1 - easeInCubic(seg(ct, 0.05, 0.2));
}

export function wordIn(ct: number, n: number) {
  'worklet';
  const s = 0.7 + 0.05 * n;
  return easeOutCubic(seg(ct, s, s + 0.3));
}

export function titleOut(ct: number) {
  'worklet';
  return easeInCubic(seg(ct, 0, 0.25));
}

export function titleIn(ct: number) {
  'worklet';
  return easeOutCubic(seg(ct, 0.45, 0.85));
}

/** Scale of the pressed transport glyph. */
export function pressScale(ct: number) {
  'worklet';
  if (ct < 0 || ct >= 0.4) return 1;
  if (ct < 0.08) return 1 - 0.14 * easeOutCubic(ct / 0.08);
  return 0.86 + 0.14 * easeOutCubic((ct - 0.08) / 0.32);
}

/** Reduced motion: one 0.3 s crossfade for everything. */
export function fade(ct: number) {
  'worklet';
  return easeInOut(seg(ct, 0, 0.3));
}

// autoPlay

function makeChange(from: number, to: number, kFrom: number, dir: number, t0: number, a0: number, antic: boolean, press: number): Change {
  'worklet';
  return { from, to, kFrom, dir, t0, a0, antic, press };
}

export function restChange(): Change {
  'worklet';
  return makeChange(0, 0, 0, 0, NONE, 0, false, 0);
}

/** The scripted change in effect at loop time tau, and the time since it started. */
function autoChange(tau: number): { ch: Change; ct: number } {
  'worklet';
  let i = -1;
  for (let n = 0; n < SCRIPT.length; n++) if (tau >= SCRIPT[n]) i = n;
  if (i < 0) return { ch: restChange(), ct: 99 };
  return { ch: makeChange(i, (i + 1) % 3, i, 1, SCRIPT[i], i * 90, true, 1), ct: tau - SCRIPT[i] };
}

function makeFrame(ch: Change, ct: number, clock: number, playing: boolean, reduced: boolean): Frame {
  'worklet';
  return {
    ch,
    ct: Math.min(ct, 99),
    angle: reduced ? (ch.kFrom + ch.dir) * 90 : angleOf(ch, ct),
    playing,
    clock,
    reduced,
  };
}

export function emptyFrame(): Frame {
  'worklet';
  return makeFrame(restChange(), 99, 0, true, false);
}

// The driver: plays the script, takes over on touch and goes home 2 s after the last one.

export type Driver = {
  autoStart: number;
  manual: boolean;
  ch: Change;
  dragging: boolean;
  dragBase: number;
  dragAngle: number;
  armed: number;
  lastTouch: number;
  returning: boolean;
  playing: boolean;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    autoStart: NONE,
    manual: false,
    ch: restChange(),
    dragging: false,
    dragBase: 0,
    dragAngle: 0,
    armed: 0,
    lastTouch: NONE,
    returning: false,
    playing: true,
  };
}

function kTo(ch: Change) {
  'worklet';
  return ch.kFrom + ch.dir;
}

function start(d: Driver, dir: number, now: number, a0: number, antic: boolean, press: number) {
  'worklet';
  const c = d.ch;
  d.ch = makeChange(c.to, (c.to + dir + 3) % 3, kTo(c), dir, now, a0, antic, press);
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (auto && d.manual && !d.dragging) {
    const ct = now - d.ch.t0;
    if (d.returning) {
      if (ct >= DONE) {
        d.manual = false;
        d.returning = false;
        d.autoStart = now;
      }
    } else if (now >= d.lastTouch + IDLE_BEFORE_BACK && ct >= DONE) {
      d.playing = true;
      if (d.ch.to === 0) {
        d.manual = false;
        d.autoStart = now;
      } else {
        start(d, d.ch.to === 1 ? -1 : 1, now, angleOf(d.ch, ct), true, 0);
        d.returning = true;
      }
    }
  }
  if (auto && !d.manual) {
    if (d.autoStart === NONE) d.autoStart = now;
    const tau = (now - d.autoStart) % LOOP;
    const a = autoChange(tau);
    return makeFrame(a.ch, a.ct, tau, true, reduced);
  }
  if (d.dragging) {
    const rest = makeChange(d.ch.to, d.ch.to, kTo(d.ch), 0, NONE, d.dragAngle, false, 0);
    const f = makeFrame(rest, 99, now, d.playing, reduced);
    f.angle = d.dragAngle;
    return f;
  }
  return makeFrame(d.ch, now - d.ch.t0, now, d.playing, reduced);
}

/** True once nothing moves after a touch. */
export function driverSettled(d: Driver, now: number) {
  'worklet';
  return !d.dragging && now - d.ch.t0 >= DONE + 0.1 && now - d.lastTouch >= 0.5;
}

/** Takes over from the loop where it is. */
function capture(d: Driver, now: number, auto: boolean) {
  'worklet';
  d.lastTouch = now;
  d.returning = false;
  if (d.manual) return;
  d.manual = true;
  if (auto && d.autoStart !== NONE) {
    const tau = (now - d.autoStart) % LOOP;
    const a = autoChange(tau);
    a.ch.t0 = now - a.ct;
    d.ch = a.ch;
  }
}

function isBusy(d: Driver, now: number) {
  'worklet';
  return d.ch.dir !== 0 && now - d.ch.t0 < BUSY;
}

/** −1 previous, +1 next, 0 play / pause. False when the disc is still busy. */
export function driverTap(d: Driver, button: number, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  capture(d, now, auto);
  if (button === 0) {
    d.playing = !d.playing;
    return true;
  }
  if (isBusy(d, now)) return false;
  start(d, button, now, angleOf(d.ch, now - d.ch.t0), !reduced, button);
  return true;
}

export function driverBeginDrag(d: Driver, now: number, auto: boolean) {
  'worklet';
  capture(d, now, auto);
  if (isBusy(d, now)) return false;
  d.dragging = true;
  d.dragBase = angleOf(d.ch, now - d.ch.t0);
  // The disc is now at rest on ch.to; fold the change into a settled one.
  d.ch = makeChange(d.ch.to, d.ch.to, kTo(d.ch), 0, NONE, d.dragBase, false, 0);
  d.dragAngle = d.dragBase;
  d.armed = 0;
  return true;
}

/** Turns the disc with the finger; returns true when it crosses the change threshold (a detent). */
export function driverDrag(d: Driver, dx: number, radius: number, now: number) {
  'worklet';
  d.lastTouch = now;
  let deg = ((-dx / radius) * 180) / Math.PI;
  const a = Math.abs(deg);
  if (a > 60) deg = (60 + (a - 60) * 0.35) * (deg < 0 ? -1 : 1);
  d.dragAngle = d.dragBase + deg;
  const off = d.dragAngle - kTo(d.ch) * 90;
  const side = off > 22 ? 1 : off < -22 ? -1 : 0;
  const crossed = side !== 0 && side !== d.armed;
  d.armed = side;
  return crossed;
}

/** Releases the disc: a change if it was turned or thrown far enough, else it springs back. */
export function driverEndDrag(d: Driver, vx: number, now: number) {
  'worklet';
  if (!d.dragging) return false;
  d.dragging = false;
  d.lastTouch = now;
  const off = d.dragAngle - kTo(d.ch) * 90;
  let dir = 0;
  if (off > 22 || (vx < -600 && off > 0)) dir = 1;
  else if (off < -22 || (vx > 600 && off < 0)) dir = -1;
  if (dir !== 0) {
    start(d, dir, now, d.dragAngle, false, 0);
    return true;
  }
  d.ch = makeChange(d.ch.to, d.ch.to, kTo(d.ch), 0, now, d.dragAngle, false, 0);
  return false;
}
