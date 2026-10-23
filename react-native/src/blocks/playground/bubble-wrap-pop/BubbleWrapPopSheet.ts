// The sheet of bubble wrap as plain state, stepped once per frame on the UI thread: each bubble's
// pressure and pop, the fingers, the tug of the sheet, the shake, and the swap to a fresh sheet.
// Units are points and seconds.

// Bubbles: radius as a share of the column pitch; the rows sit on a hex grid.
export const BUBBLE_SHARE = 0.4;
const COLUMNS = 6;
// The film runs this far past the outer bubbles.
export const FILM_MARGIN = 18;

// A press builds pressure from 0 to 1 over PRESS_TIME s and the bubble pops at 1. Let go early,
// the dome springs back (1/s², 1/s). A sliding finger pops what it crosses: a bubble it reaches
// within SLIDE_REACH of its radius is committed and pops SLIDE_TIME s later.
const PRESS_TIME = 0.46;
const RELAX_K = 420;
const RELAX_C = 16;
const SLIDE_REACH = 0.85;
const SLIDE_TIME = 0.045;
const COMMIT = 0.72;
const MOVE_SLOP = 8;
// The air rings and the collapse play out over this long after a pop.
export const POP_LIFE = 0.7;

// The sheet tugs with the finger, rubber-banded to TUG_MAX pt, and springs home on release.
const TUG_MAX = 46;
const TUG_K = 210;
const TUG_C = 19;
// Each pop knocks the camera with SHAKE_KICK pt/s (half as much on a slide), on a stiff spring.
const SHAKE_KICK = 230;
const SHAKE_K = 1100;
const SHAKE_C = 24;
const SHAKE_MAX = 5;
// Once every bubble is gone the sheet waits SWAP_DELAY s, then slides out as a fresh one slides
// in over SWAP_TIME s.
const SWAP_DELAY = 0.55;
export const SWAP_TIME = 1.05;
// The counter rolls toward the count on a spring.
const COUNT_K = 260;
const COUNT_C = 30;
// Pops closer together than this give one haptic.
const HAPTIC_GAP = 0.05;

export const EVENT_POP = 1;
export const EVENT_SWAP = 2;

export type Finger = {
  id: number;
  down: boolean;
  scripted: boolean;
  x: number;
  y: number;
  x0: number;
  y0: number;
  lastX: number;
  lastY: number;
  /** 0 pressing a bubble, 1 sliding, 2 tugging the sheet. */
  mode: number;
  bubble: number;
  moved: boolean;
  tugFromX: number;
  tugFromY: number;
  vx: number;
  vy: number;
  lastT: number;
};

export type Sheet = {
  ready: boolean;
  time: number;
  width: number;
  /** The sheet's rectangle at rest. */
  left: number;
  top: number;
  right: number;
  bottom: number;
  radius: number;
  count: number;
  /** Bubble centres, relative to the sheet's top left. */
  bx: number[];
  by: number[];
  rowOf: number[];
  pressure: number[];
  pressureV: number[];
  committed: boolean[];
  popped: boolean[];
  /** Seconds since each bubble popped, or -1. */
  popAge: number[];
  /** Which side the finger was on when it popped, for the bulge. */
  leanX: number[];
  leanY: number[];
  pops: number;
  shown: number;
  shownV: number;
  tugX: number;
  tugY: number;
  tugVX: number;
  tugVY: number;
  shakeX: number;
  shakeY: number;
  shakeVX: number;
  shakeVY: number;
  /** Seconds since every bubble went, -1 while some remain. */
  emptyFor: number;
  /** 0 … 1 while a fresh sheet slides in. */
  swap: number;
  fingers: Finger[];
  events: number;
  eventsByUser: boolean;
  lastHaptic: number;
  calm: boolean;
};

function newFinger(): Finger {
  'worklet';
  return {
    id: -1,
    down: false,
    scripted: false,
    x: 0,
    y: 0,
    x0: 0,
    y0: 0,
    lastX: 0,
    lastY: 0,
    mode: 0,
    bubble: -1,
    moved: false,
    tugFromX: 0,
    tugFromY: 0,
    vx: 0,
    vy: 0,
    lastT: 0,
  };
}

/** A full sheet laid out in the given rectangle. */
export function createSheet(width: number, left: number, top: number, right: number, bottom: number, calm: boolean): Sheet {
  'worklet';
  const inner = right - left - FILM_MARGIN * 2;
  const pitch = inner / (COLUMNS + 0.5);
  const radius = pitch * BUBBLE_SHARE;
  const rowPitch = pitch * 0.866;
  const rows = Math.max(1, Math.floor((bottom - top - FILM_MARGIN * 2 - pitch) / rowPitch) + 1);
  // Centre the rows in the height they leave.
  const used = (rows - 1) * rowPitch + pitch;
  const offY = (bottom - top - used) / 2 + pitch / 2;
  const bx: number[] = [];
  const by: number[] = [];
  const rowOf: number[] = [];
  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < COLUMNS; c += 1) {
      bx.push(FILM_MARGIN + pitch / 2 + c * pitch + (r % 2) * (pitch / 2));
      by.push(offY + r * rowPitch);
      rowOf.push(r);
    }
  }
  const n = bx.length;
  const zeros: number[] = [];
  const no: boolean[] = [];
  const minus: number[] = [];
  for (let i = 0; i < n; i += 1) {
    zeros.push(0);
    no.push(false);
    minus.push(-1);
  }
  return {
    ready: true,
    time: 0,
    width,
    left,
    top,
    right,
    bottom,
    radius,
    count: n,
    bx,
    by,
    rowOf,
    pressure: zeros.slice(),
    pressureV: zeros.slice(),
    committed: no.slice(),
    popped: no.slice(),
    popAge: minus,
    leanX: zeros.slice(),
    leanY: zeros.slice(),
    pops: 0,
    shown: 0,
    shownV: 0,
    tugX: 0,
    tugY: 0,
    tugVX: 0,
    tugVY: 0,
    shakeX: 0,
    shakeY: 0,
    shakeVX: 0,
    shakeVY: 0,
    emptyFor: -1,
    swap: 0,
    fingers: [newFinger(), newFinger()],
    events: 0,
    eventsByUser: false,
    lastHaptic: -1,
    calm,
  };
}

/** The intact bubble under (x, y) on screen, or -1. */
export function bubbleAt(s: Sheet, x: number, y: number, reach: number) {
  'worklet';
  const lx = x - s.left - s.tugX;
  const ly = y - s.top - s.tugY;
  let best = -1;
  let bestD = s.radius * reach;
  for (let i = 0; i < s.count; i += 1) {
    if (s.popped[i]) continue;
    const d = Math.hypot(s.bx[i] - lx, s.by[i] - ly);
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  }
  return best;
}

function fingerOf(s: Sheet, id: number) {
  'worklet';
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down && s.fingers[i].id === id) return s.fingers[i];
  return null;
}

function rubber(d: number, max: number) {
  'worklet';
  const a = Math.abs(d);
  return Math.sign(d) * max * (1 - Math.exp(-a / max));
}

export function sheetDown(s: Sheet, id: number, x: number, y: number, scripted: boolean) {
  'worklet';
  if (s.swap > 0) return;
  let f: Finger | null = null;
  for (let i = 0; i < s.fingers.length; i += 1) {
    if (!s.fingers[i].down) {
      f = s.fingers[i];
      break;
    }
  }
  if (!f) return;
  f.id = id;
  f.down = true;
  f.scripted = scripted;
  f.x = x;
  f.y = y;
  f.x0 = x;
  f.y0 = y;
  f.lastX = x;
  f.lastY = y;
  f.lastT = s.time;
  f.vx = 0;
  f.vy = 0;
  f.moved = false;
  // Among the bubbles it presses (or, on a popped one, waits to slide); on the film's edge or
  // off the sheet it tugs the film.
  const b = bubbleAt(s, x, y, 1);
  const lx = x - s.left - s.tugX;
  const ly = y - s.top - s.tugY;
  const amongBubbles =
    lx > FILM_MARGIN && lx < s.right - s.left - FILM_MARGIN && ly > FILM_MARGIN && ly < s.bottom - s.top - FILM_MARGIN;
  f.bubble = b;
  f.mode = b >= 0 || amongBubbles ? 0 : 2;
  f.tugFromX = s.tugX;
  f.tugFromY = s.tugY;
  if (b >= 0) {
    s.leanX[b] = (x - s.left - s.tugX - s.bx[b]) / s.radius;
    s.leanY[b] = (y - s.top - s.tugY - s.by[b]) / s.radius;
  }
}

export function sheetMove(s: Sheet, id: number, x: number, y: number) {
  'worklet';
  const f = fingerOf(s, id);
  if (!f) return;
  const px = f.x;
  const py = f.y;
  f.x = x;
  f.y = y;
  const dt = s.time - f.lastT;
  if (dt > 0.004) {
    f.vx = f.vx * 0.35 + ((x - f.lastX) / dt) * 0.65;
    f.vy = f.vy * 0.35 + ((y - f.lastY) / dt) * 0.65;
    f.lastX = x;
    f.lastY = y;
    f.lastT = s.time;
  }
  if (!f.moved && Math.hypot(x - f.x0, y - f.y0) > MOVE_SLOP) {
    f.moved = true;
    if (f.mode === 0) f.mode = 1;
  }
  if (f.mode === 2) {
    s.tugX = rubber(f.tugFromX + x - f.x0, TUG_MAX);
    s.tugY = rubber(f.tugFromY + y - f.y0, TUG_MAX);
    s.tugVX = 0;
    s.tugVY = 0;
    return;
  }
  if (f.mode === 1) {
    // Every bubble the finger passes over on its way from the last point, a few pt apart.
    const steps = Math.max(1, Math.ceil(Math.hypot(x - px, y - py) / 6));
    for (let k = 1; k <= steps; k += 1) {
      const b = bubbleAt(s, px + ((x - px) * k) / steps, py + ((y - py) * k) / steps, SLIDE_REACH);
      if (b >= 0 && !s.committed[b]) {
        s.committed[b] = true;
        s.pressure[b] = Math.max(s.pressure[b], COMMIT);
        s.leanX[b] = Math.sign(x - px) * 0.6;
        s.leanY[b] = Math.sign(y - py) * 0.6;
      }
    }
    f.bubble = -1;
  }
}

export function sheetUp(s: Sheet, id: number) {
  'worklet';
  const f = fingerOf(s, id);
  if (!f) return;
  f.down = false;
  if (f.mode === 2) {
    const still = s.time - f.lastT > 0.08;
    s.tugVX = still ? 0 : f.vx;
    s.tugVY = still ? 0 : f.vy;
  }
  f.bubble = -1;
}

export function sheetUserFingers(s: Sheet) {
  'worklet';
  let n = 0;
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down && !s.fingers[i].scripted) n += 1;
  return n;
}

export function sheetCancelScripted(s: Sheet) {
  'worklet';
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down && s.fingers[i].scripted) sheetUp(s, s.fingers[i].id);
}

function pop(s: Sheet, b: number, sliding: boolean, byUser: boolean) {
  'worklet';
  s.popped[b] = true;
  s.popAge[b] = 0;
  s.pressure[b] = 1;
  s.pops += 1;
  if (!s.calm) {
    // A knock in a direction set by which bubble it was, so the shake is the same every time.
    const a = (b * 2.399963) % (Math.PI * 2);
    const kick = SHAKE_KICK * (sliding ? 0.5 : 1);
    s.shakeVX += Math.cos(a) * kick;
    s.shakeVY += Math.sin(a) * kick;
  }
  s.events |= EVENT_POP;
  if (byUser && s.time - s.lastHaptic > HAPTIC_GAP) {
    s.eventsByUser = true;
    s.lastHaptic = s.time;
  }
}

/** Advances everything by one frame. */
export function stepSheet(s: Sheet, frameDt: number) {
  'worklet';
  const dt = Math.min(frameDt, 1 / 30);
  s.time += dt;
  // Pressure: a held bubble builds toward its pop; a committed one runs out fast; the rest relax.
  const pressing: number[] = [];
  let pressedByUser = false;
  for (let k = 0; k < s.fingers.length; k += 1) {
    const f = s.fingers[k];
    if (f.down && f.mode === 0 && f.bubble >= 0 && !s.popped[f.bubble]) {
      pressing.push(f.bubble);
      if (!f.scripted) pressedByUser = true;
    }
  }
  let anyByUser = pressedByUser;
  for (let k = 0; k < s.fingers.length; k += 1) if (s.fingers[k].down && !s.fingers[k].scripted) anyByUser = true;
  for (let i = 0; i < s.count; i += 1) {
    if (s.popped[i]) {
      if (s.popAge[i] >= 0) s.popAge[i] += dt;
      continue;
    }
    if (s.committed[i]) {
      s.pressure[i] += dt / SLIDE_TIME * (1 - COMMIT);
      if (s.pressure[i] >= 1) pop(s, i, true, anyByUser);
      continue;
    }
    if (pressing.indexOf(i) >= 0) {
      s.pressure[i] += dt / PRESS_TIME;
      s.pressureV[i] = 0;
      if (s.pressure[i] >= 1) pop(s, i, false, pressedByUser);
      continue;
    }
    if (s.pressure[i] !== 0 || s.pressureV[i] !== 0) {
      s.pressureV[i] += (-s.pressure[i] * RELAX_K - s.pressureV[i] * RELAX_C) * dt;
      s.pressure[i] += s.pressureV[i] * dt;
      if (Math.abs(s.pressure[i]) < 0.001 && Math.abs(s.pressureV[i]) < 0.01) {
        s.pressure[i] = 0;
        s.pressureV[i] = 0;
      }
    }
  }
  // The tug springs home once no finger holds it.
  let tugging = false;
  for (let k = 0; k < s.fingers.length; k += 1) if (s.fingers[k].down && s.fingers[k].mode === 2) tugging = true;
  if (!tugging) {
    s.tugVX += (-s.tugX * TUG_K - s.tugVX * TUG_C) * dt;
    s.tugVY += (-s.tugY * TUG_K - s.tugVY * TUG_C) * dt;
    s.tugX += s.tugVX * dt;
    s.tugY += s.tugVY * dt;
  }
  // Shake, in four substeps: it is stiff.
  for (let k = 0; k < 4; k += 1) {
    const h = dt / 4;
    s.shakeVX += (-s.shakeX * SHAKE_K - s.shakeVX * SHAKE_C) * h;
    s.shakeVY += (-s.shakeY * SHAKE_K - s.shakeVY * SHAKE_C) * h;
    s.shakeX = Math.max(-SHAKE_MAX, Math.min(SHAKE_MAX, s.shakeX + s.shakeVX * h));
    s.shakeY = Math.max(-SHAKE_MAX, Math.min(SHAKE_MAX, s.shakeY + s.shakeVY * h));
  }
  // The counter.
  s.shownV += ((s.pops - s.shown) * COUNT_K - s.shownV * COUNT_C) * dt;
  s.shown += s.shownV * dt;
  // An empty sheet gives way to a fresh one.
  if (s.swap > 0) {
    s.swap += dt / SWAP_TIME;
    if (s.swap >= 1) {
      s.swap = 0;
      for (let i = 0; i < s.count; i += 1) {
        s.popped[i] = false;
        s.committed[i] = false;
        s.pressure[i] = 0;
        s.pressureV[i] = 0;
        s.popAge[i] = -1;
      }
      s.emptyFor = -1;
    }
  } else if (s.pops >= s.count) {
    s.emptyFor = s.emptyFor < 0 ? 0 : s.emptyFor + dt;
    if (s.emptyFor >= SWAP_DELAY) {
      s.swap = 0.0001;
      s.pops = 0;
      s.events |= EVENT_SWAP;
      for (let k = 0; k < s.fingers.length; k += 1) s.fingers[k].down = false;
    }
  }
}
