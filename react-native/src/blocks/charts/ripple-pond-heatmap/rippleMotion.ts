/*
 Ripple Pond Heatmap — geometry, the autoPlay script and the touch simulation (worklets).

 LOOP = 9 s:
   0.0 – 1.4   rest: still water, caustics drifting (one cycle per loop).
   1.4         a tap on day 7: a ripple leaves it (140 pt/s), the pads around bob as it passes,
               the detail card rolls to "Wed 7 · 28 min · 2 sessions" and a ring lights the pad.
   3.2         a tap on day 18, the same.
   4.6 – 5.8   a finger drags across week 4 (ease-in-out), leaving a wake: a ripple every 0.2 s
               along its path; the card rolls to the week.
   5.8 – 7.6   the water settles (every ripple fades out by 7.8); 7.4 the card rolls back to
               the month and the ring fades.
   7.6 – 9.0   rest.
*/

import { cellOf, TODAY } from './rippleArt';

export const LOOP = 9;
export const REDUCED_AT = 0;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;
export const SOURCES = 8;

export const HEADER_H = 118;
export const CARD_H = 92;
export const CHIPS_H = 40;

export type PondGeo = { w: number; h: number; grid: number[]; cardTop: number; legendTop: number; chipsTop: number };

export function makePondGeo(w: number, h: number, insetTop: number, insetBottom: number): PondGeo {
  'worklet';
  const left = 16;
  const cellW = (w - left * 2) / 7;
  const gridTop = insetTop + HEADER_H + 34;
  const chipsTop = h - Math.max(insetBottom, 12) - 8 - CHIPS_H;
  const cardTop = chipsTop - 12 - CARD_H;
  // Rows taller than wide (the pond spreads down the screen), short enough to leave the legend
  // room above the card.
  const rowH = Math.min(cellW * 1.45, (cardTop - 56 - gridTop) / 5);
  return { w, h, grid: [left, gridTop, cellW, rowH], cardTop, legendTop: gridTop + rowH * 5 + 14, chipsTop };
}

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function padCenter(g: PondGeo, day: number) {
  'worklet';
  const [c, r] = cellOf(day);
  return [g.grid[0] + (c + 0.5) * g.grid[2], g.grid[1] + (r + 0.5) * g.grid[3]];
}

export type PondFrame = {
  /** 8 sources × (x, y, age, amplitude). */
  src: number[];
  /** Selected pad x, y, glow. */
  sel: number[];
  /** Detail card: -1 month, 1…31 day, 100 + row week. */
  detail: number;
  phase: number;
};

export function emptyFrame(): PondFrame {
  'worklet';
  const src: number[] = [];
  for (let i = 0; i < SOURCES; i += 1) src.push(0, 0, -1, 0);
  return { src, sel: [0, 0, 0], detail: -1, phase: 0 };
}

const TAPS = [
  [1.4, 7],
  [3.2, 18],
];
const WAKE_FROM = 4.6;
const WAKE_TO = 5.8;
const WAKE_ROW = 3;

/** The finger's place along the wake at time t (ease-in-out across the row). */
function wakeAt(g: PondGeo, t: number) {
  'worklet';
  const u = easeInOut(seg(t, WAKE_FROM, WAKE_TO));
  const x0 = g.grid[0] + g.grid[2] * 0.2;
  const x1 = g.grid[0] + g.grid[2] * 6.8;
  const y = g.grid[1] + (WAKE_ROW + 0.5) * g.grid[3] + Math.sin(u * Math.PI) * 6;
  return [x0 + (x1 - x0) * u, y];
}

export function scriptFrame(t: number, g: PondGeo, f: PondFrame) {
  'worklet';
  f.phase = t / LOOP;
  const calm = 1 - easeInOut(seg(t, 6.8, 7.8));
  for (let i = 0; i < 2; i += 1) {
    const c = padCenter(g, TAPS[i][1]);
    f.src[i * 4] = c[0];
    f.src[i * 4 + 1] = c[1];
    f.src[i * 4 + 2] = t >= TAPS[i][0] ? t - TAPS[i][0] : -1;
    f.src[i * 4 + 3] = calm;
  }
  for (let j = 0; j < 6; j += 1) {
    const born = WAKE_FROM + j * 0.2;
    const p = wakeAt(g, born);
    const k = (2 + j) * 4;
    f.src[k] = p[0];
    f.src[k + 1] = p[1];
    f.src[k + 2] = t >= born ? t - born : -1;
    f.src[k + 3] = 0.55 * calm;
  }
  // Card and ring.
  let detail = -1;
  let day = 0;
  if (t >= 1.45 && t < 3.25) {
    detail = 7;
    day = 7;
  } else if (t >= 3.25 && t < 4.7) {
    detail = 18;
    day = 18;
  } else if (t >= 4.7 && t < 7.4) {
    detail = 100 + WAKE_ROW;
  }
  f.detail = detail;
  if (day > 0) {
    const c = padCenter(g, day);
    f.sel[0] = c[0];
    f.sel[1] = c[1];
  }
  const glow7 = easeInOut(seg(t, 1.45, 1.75)) * (1 - easeInOut(seg(t, 3.2, 3.45)));
  const glow18 = easeInOut(seg(t, 3.3, 3.6)) * (1 - easeInOut(seg(t, 4.6, 4.9)));
  f.sel[2] = day === 7 ? glow7 : day === 18 ? glow18 : 0;
}

export type PondSim = {
  mode: number;
  t: number;
  now: number;
  last: number;
  back: number;
  frame: PondFrame;
  next: number;
  dragging: boolean;
  lastX: number;
  lastY: number;
  from: number[];
};

export function makeSim(): PondSim {
  'worklet';
  return {
    mode: MODE_AUTO,
    t: 0,
    now: 0,
    last: 0,
    back: 0,
    frame: emptyFrame(),
    next: 0,
    dragging: false,
    lastX: 0,
    lastY: 0,
    from: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  };
}

function goLive(s: PondSim) {
  'worklet';
  if (s.mode === MODE_LIVE) return;
  s.mode = MODE_LIVE;
  // Ripples already out keep travelling; the oldest slot is reused first.
  let oldest = 0;
  let age = -2;
  for (let i = 0; i < SOURCES; i += 1) {
    const a = s.frame.src[i * 4 + 2];
    const rank = a < 0 ? 1e9 : a;
    if (rank > age) {
      age = rank;
      oldest = i;
    }
  }
  s.next = oldest;
}

function emit(s: PondSim, x: number, y: number, amp: number) {
  'worklet';
  const k = s.next * 4;
  s.frame.src[k] = x;
  s.frame.src[k + 1] = y;
  s.frame.src[k + 2] = 0;
  s.frame.src[k + 3] = amp;
  s.next = (s.next + 1) % SOURCES;
}

/** The day under a point, or 0. */
export function dayAt(g: PondGeo, x: number, y: number) {
  'worklet';
  const c = Math.floor((x - g.grid[0]) / g.grid[2]);
  const r = Math.floor((y - g.grid[1]) / g.grid[3]);
  if (c < 0 || c > 6 || r < 0 || r > 4) return 0;
  const day = r * 7 + c - 3 + 1;
  if (day < 1 || day > 31) return 0;
  const cx = g.grid[0] + (c + 0.5) * g.grid[2];
  const cy = g.grid[1] + (r + 0.5) * g.grid[3];
  return Math.hypot(x - cx, y - cy) < g.grid[2] * 0.48 ? day : 0;
}

export function simDown(s: PondSim, x: number, y: number) {
  'worklet';
  goLive(s);
  s.last = s.now;
  s.lastX = x;
  s.lastY = y;
}

/** A tap drops a ripple; on a pad it selects the day. Returns the day (0 for open water). */
export function simTap(s: PondSim, g: PondGeo, x: number, y: number) {
  'worklet';
  goLive(s);
  s.last = s.now;
  const day = dayAt(g, x, y);
  emit(s, x, y, 1);
  if (day > 0) {
    const c = padCenter(g, day);
    s.frame.sel[0] = c[0];
    s.frame.sel[1] = c[1];
    s.frame.sel[2] = day <= TODAY ? 1 : 0.5;
    s.frame.detail = day;
  }
  return day;
}

/** A drag leaves a wake: a ripple every 20 pt, stronger the faster the finger moves. */
export function simDrag(s: PondSim, x: number, y: number, speed: number) {
  'worklet';
  goLive(s);
  s.dragging = true;
  s.last = s.now;
  if (Math.hypot(x - s.lastX, y - s.lastY) >= 20) {
    emit(s, x, y, Math.min(0.8, Math.max(0.3, speed / 900)));
    s.lastX = x;
    s.lastY = y;
  }
}

export function simDragEnd(s: PondSim, g: PondGeo, y: number) {
  'worklet';
  s.dragging = false;
  s.last = s.now;
  const r = Math.floor((y - g.grid[1]) / g.grid[3]);
  if (r >= 0 && r <= 4) {
    s.frame.detail = 100 + r;
    s.frame.sel[2] = 0;
  }
}

/** One frame; returns true when the loop restarts. */
export function simStep(s: PondSim, dt: number, g: PondGeo, auto: boolean, reduced: boolean) {
  'worklet';
  s.now += dt;
  const f = s.frame;
  if (s.mode === MODE_AUTO) {
    if (auto) s.t = (s.t + dt) % LOOP;
    scriptFrame(reduced ? REDUCED_AT : s.t, g, f);
    return false;
  }
  f.phase += dt / LOOP;
  for (let i = 0; i < SOURCES; i += 1) {
    const a = f.src[i * 4 + 2];
    if (a >= 0) f.src[i * 4 + 2] = a + dt > 6 ? -1 : a + dt;
  }
  if (s.mode === MODE_LIVE) {
    if (auto && !s.dragging && s.now - s.last > IDLE_BEFORE_BACK) {
      s.mode = MODE_BACK;
      s.back = 0;
      for (let i = 0; i < SOURCES; i += 1) s.from[i] = f.src[i * 4 + 3];
      s.from[8] = f.sel[2];
      s.from[9] = f.phase;
      f.detail = -1;
    }
    return false;
  }
  s.back = Math.min(1, s.back + dt / BACK_FOR);
  const k = easeInOut(s.back);
  for (let i = 0; i < SOURCES; i += 1) f.src[i * 4 + 3] = s.from[i] * (1 - k);
  f.sel[2] = s.from[8] * (1 - k);
  // The caustics' clock eases on to the next whole loop, where the script picks it up.
  f.phase = s.from[9] + (Math.ceil(s.from[9]) - s.from[9]) * k;
  if (s.back >= 1) {
    s.mode = MODE_AUTO;
    s.t = 0;
    scriptFrame(0, g, f);
    return true;
  }
  return false;
}

export function pondUniforms(f: PondFrame, g: PondGeo) {
  'worklet';
  const s = f.src;
  return {
    size: [Math.max(1, g.w), Math.max(1, g.h)],
    grid: g.grid.slice(),
    s0: [s[0], s[1], s[2], s[3]],
    s1: [s[4], s[5], s[6], s[7]],
    s2: [s[8], s[9], s[10], s[11]],
    s3: [s[12], s[13], s[14], s[15]],
    s4: [s[16], s[17], s[18], s[19]],
    s5: [s[20], s[21], s[22], s[23]],
    s6: [s[24], s[25], s[26], s[27]],
    s7: [s[28], s[29], s[30], s[31]],
    sel: [f.sel[0], f.sel[1], f.sel[2], 0],
    phase: f.phase - Math.floor(f.phase),
  };
}
