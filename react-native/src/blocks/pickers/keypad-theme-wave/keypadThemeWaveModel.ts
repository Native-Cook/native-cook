/*
 Keypad Theme Wave — the pure part (a port of KeypadThemeWaveModel.swift): palettes, the keypad, the
 strip's layout, the timing of a theme change, the autoPlay timeline, the tiny calculator and the driver
 that turns the clock and the finger into scenes. Everything here runs on the UI thread as worklets;
 helpers are declared above their callers.
*/

export type Theme = {
  name: string;
  bg: number;
  digit: number;
  digitInk: number;
  fn: number;
  fnInk: number;
  op: number;
  opInk: number;
  display: number;
  ring: number;
};

/** A theme change: when, to which theme, and from which side (+1 the new swatch came from the right). */
export type Change = { at: number; to: number; dir: number };

/** What one frame shows. */
export type Scene = {
  now: number;
  base: number;
  changes: Change[];
  /** The strip's position in swatches (wraps every theme count). */
  scroll: number;
  display: string;
  /** The time each key was last pressed (index row * 5 + column). */
  presses: number[];
  reduced: boolean;
};

export const LOOP = 10;

function theme(
  name: string,
  bg: number,
  digit: number,
  digitInk: number,
  fn: number,
  fnInk: number,
  op: number,
  opInk: number,
  display: number,
  ring: number,
): Theme {
  return { name, bg, digit, digitInk, fn, fnInk, op, opInk, display, ring };
}

export const THEMES: Theme[] = [
  theme('Paper', 0xe8e7ea, 0xbebdc3, 0x2c2c31, 0xbebdc3, 0x2c2c31, 0x8f8e95, 0xffffff, 0x2c2c31, 0x75747b),
  theme('Classic', 0xdcd8d0, 0xa9a49b, 0x26241f, 0xf0532f, 0xffffff, 0x383532, 0xffffff, 0x1f1d1a, 0x5e5a53),
  theme('Garden', 0x23a05a, 0xf5f2e6, 0x1f5f38, 0xf26a2a, 0xffffff, 0xe3392f, 0xffffff, 0xffffff, 0xe6f6ea),
  theme('Marigold', 0xf8b33c, 0xfff8ea, 0x3a2a12, 0xe2432d, 0xffffff, 0x2b6fbe, 0xffffff, 0xd93a22, 0x7a4e0e),
  theme('Lilac', 0xd0c2f9, 0x9b7cf1, 0xffffff, 0x9b7cf1, 0xffffff, 0xcff24b, 0x3d3470, 0x5b46c2, 0x6a55c9),
  theme('Sky', 0x6ccaf4, 0x2f9ad5, 0xf2faff, 0x2f9ad5, 0xf2faff, 0xf26c22, 0xffffff, 0x145c88, 0x1f6f9c),
  theme('Mint', 0x7de8d6, 0x1da388, 0xf2fffb, 0x137e69, 0xffffff, 0x55d3b9, 0x0f5a4b, 0x0f5a4b, 0x167d6a),
  theme('Meadow', 0x68d877, 0x41bc55, 0xf3fff4, 0x2e9c43, 0xf3fff4, 0xa6ec7a, 0x1f5a28, 0x1f5a28, 0x2b8a3c),
  theme('Lemon', 0xfada5e, 0xf6a623, 0xffffff, 0xf6a623, 0xffffff, 0xe8562a, 0xffffff, 0x8a5a00, 0xa8740e),
  theme('Night', 0x1d1c1b, 0x3a3936, 0xf3f1ec, 0x5a5854, 0xf3f1ec, 0x8e8b85, 0x1d1c1b, 0xf3f1ec, 0x8a8782),
];
export const COUNT = 10;
/** The resting theme: Classic. */
export const HOME = 1;

export function fillOf(t: Theme, role: number) {
  'worklet';
  return role === 1 ? t.fn : role === 2 ? t.op : t.digit;
}

export function inkOf(t: Theme, role: number) {
  'worklet';
  return role === 1 ? t.fnInk : role === 2 ? t.opInk : t.digitInk;
}

// Keypad

/** Labels, row by row; "" is the wordmark's cell. */
export const LABELS = [
  ['AC', '7', '8', '9', '÷'],
  ['±', '4', '5', '6', '×'],
  ['%', '1', '2', '3', '−'],
  ['', '0', '.', '=', '+'],
];

/** 0 digit, 1 function (AC ± %), 2 operator (÷ × − + =). */
export function role(r: number, c: number) {
  'worklet';
  if (c === 0) return 1;
  if (c === 4 || (r === 3 && c === 3)) return 2;
  return 0;
}

export function isKey(r: number, c: number) {
  'worklet';
  return !(r === 3 && c === 0);
}

export const KEY_RADIUS = 25;
export const PITCH = 59;

// Curves

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutBack(x: number, s: number) {
  'worklet';
  const y = x - 1;
  return 1 + (s + 1) * y * y * y + s * y * y;
}

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Rounds half away from zero, like Swift's rounded(). */
export function roundHalf(x: number) {
  'worklet';
  return x >= 0 ? Math.floor(x + 0.5) : -Math.floor(-x + 0.5);
}

// Timing of a change

export const KEY_DUR = 0.64;
const KEY_LEAD = 0.08;
const KEY_STAGGER = 0.06;
export const REVEAL_DUR = 0.62;
const SETTLE = 1.3;
export const REDUCED_DUR = 0.25;

/** The diagonal a key sits on, counted from the corner the colour comes from (0 … 7). */
function diagonal(r: number, c: number, dir: number) {
  'worklet';
  return 3 - r + (dir > 0 ? 4 - c : c);
}

function keyDelay(r: number, c: number, dir: number) {
  'worklet';
  return KEY_LEAD + KEY_STAGGER * diagonal(r, c, dir);
}

export type KeyState = { from: number; to: number; u: number };

/** One key's transition: the theme it leaves, the one it goes to, and its progress (1 = done). */
export function keyState(s: Scene, r: number, c: number): KeyState {
  'worklet';
  let from = s.base;
  let to = s.base;
  let u = 1;
  let prev = s.base;
  for (let i = 0; i < s.changes.length; i++) {
    const ch = s.changes[i];
    const start = ch.at + (s.reduced ? 0 : keyDelay(r, c, ch.dir));
    if (s.now >= start) {
      from = prev;
      to = ch.to;
      u = Math.min(1, (s.now - start) / (s.reduced ? REDUCED_DUR : KEY_DUR));
    }
    prev = ch.to;
  }
  return { from, to, u };
}

/** What a key draws at progress u of its change. */
export type Look = {
  oldR: number;
  oldLabel: number;
  ring: number;
  ringLabel: number;
  newR: number;
  newLabel: number;
  scale: number;
  mix: number;
};

export function look(u: number, reduced: boolean): Look {
  'worklet';
  const R = KEY_RADIUS;
  const l: Look = { oldR: 0, oldLabel: 0, ring: 0, ringLabel: 0, newR: R, newLabel: 1, scale: 1, mix: 1 };
  if (reduced) {
    l.mix = easeInOutCubic(u);
    return l;
  }
  if (u >= 1) return l;
  const drain = easeInCubic(seg(u, 0, 0.28));
  l.oldR = R * (1 - drain);
  l.oldLabel = 1 - seg(u, 0, 0.15);
  const v = seg(u, 0.46, 1);
  const grow = easeOutBack(v, 1.9);
  l.newR = R * grow;
  l.ring = seg(u, 0.06, 0.2) * (1 - seg(grow, 0.85, 1));
  const swap = seg(v, 0.25, 0.6);
  l.ringLabel = seg(u, 0.12, 0.26) * (1 - swap);
  l.newLabel = swap;
  const dip = easeOutCubic(seg(u, 0, 0.28));
  l.scale = 1 - 0.08 * dip * (1 - grow);
  return l;
}

/** The press dip of a key tapped tau seconds ago: 0 at rest, 1 fully pressed. */
export function press(tau: number) {
  'worklet';
  if (tau < 0 || tau > 0.5) return 0;
  if (tau < 0.07) return easeOutCubic(tau / 0.07);
  return 1 - easeOutBack(seg(tau, 0.07, 0.4), 1.6);
}

/** The reveal radius of a change tau seconds old, as a fraction of the card's diagonal. */
export function reveal(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const x = Math.min(1, tau / REVEAL_DUR);
  return (1 - Math.cos(Math.PI * x)) / 2;
}

// The strip

export const SWATCH_PITCH = 34;

/** Distance of a swatch from the centre, in swatches, wrapped into [−count/2, count/2). */
export function wrap(d: number) {
  'worklet';
  const n = COUNT;
  let x = (d + n / 2) % n;
  if (x < 0) x += n;
  return x - n / 2;
}

export function mod(i: number) {
  'worklet';
  return ((i % COUNT) + COUNT) % COUNT;
}

/** Horizontal offset of a swatch d swatches from the centre (the centred one is wider and pushes the others). */
export function swatchX(d: number) {
  'worklet';
  const a = Math.min(Math.abs(d), 1);
  return d * SWATCH_PITCH + (d < 0 ? -1 : 1) * a * 8;
}

// Springs (response 0.5 s, damping 0.8)

/** An underdamped spring from x0 with speed v0 towards 0, after tau seconds. */
function spring(x0: number, v0: number, tau: number) {
  'worklet';
  if (tau <= 0) return x0;
  const w = (2 * Math.PI) / 0.5;
  const z = 0.8;
  const wd = w * Math.sqrt(1 - z * z);
  const e = Math.exp(-z * w * tau);
  return e * (x0 * Math.cos(wd * tau) + ((v0 + z * w * x0) / wd) * Math.sin(wd * tau));
}

function springVelocity(x0: number, v0: number, tau: number) {
  'worklet';
  const h = 0.001;
  const a = Math.max(0, tau - h);
  return (spring(x0, v0, tau + h) - spring(x0, v0, a)) / (tau + h - a);
}

// autoPlay

/** The strip's moves: start time, from, to (unwrapped swatch positions). */
const AUTO_MOVES = [
  { at: 1.0, from: 1, to: 3 }, // Classic → Marigold
  { at: 3.2, from: 3, to: 4 }, // → Lilac
  { at: 5.2, from: 4, to: 6 }, // → Mint
  { at: 7.2, from: 6, to: 1 }, // back to Classic
];
const WAVE_AFTER_MOVE = 0.15;

const AUTO_CHANGES: Change[] = AUTO_MOVES.map((m) => ({
  at: m.at + WAVE_AFTER_MOVE,
  to: ((m.to % COUNT) + COUNT) % COUNT,
  dir: m.to > m.from ? 1 : -1,
}));

/** Strip position and speed at loop time t. */
function autoScroll(t: number) {
  'worklet';
  let s = AUTO_MOVES[0].from;
  let v = 0;
  let target = s;
  for (let i = 0; i < AUTO_MOVES.length; i++) {
    const m = AUTO_MOVES[i];
    if (t < m.at) continue;
    const x0 = m.from - m.to;
    s = m.to + spring(x0, 0, t - m.at);
    v = springVelocity(x0, 0, t - m.at);
    target = m.to;
  }
  return { s, v, target };
}

function emptyPresses() {
  'worklet';
  const p: number[] = [];
  for (let i = 0; i < 20; i++) p.push(-1e9);
  return p;
}

export function emptyScene(): Scene {
  'worklet';
  return { now: 0, base: HOME, changes: [], scroll: HOME, display: '0', presses: emptyPresses(), reduced: false };
}

function autoScene(t: number): Scene {
  'worklet';
  const s = emptyScene();
  s.now = t;
  for (let i = 0; i < AUTO_CHANGES.length; i++) {
    if (AUTO_CHANGES[i].at <= t) s.changes.push(AUTO_CHANGES[i]);
  }
  s.scroll = autoScroll(t).s;
  return s;
}

// The tiny calculator behind the preview's keys

export type Calc = { display: string; acc: number; hasAcc: boolean; op: string; fresh: boolean };

const MAX_DIGITS = 9;

function calcClear(c: Calc) {
  'worklet';
  c.display = '0';
  c.acc = 0;
  c.hasAcc = false;
  c.op = '';
  c.fresh = true;
}

function calcValue(c: Calc) {
  'worklet';
  const v = parseFloat(c.display.replace('−', '-'));
  return isNaN(v) ? 0 : v;
}

function calcDigits(c: Calc) {
  'worklet';
  let n = 0;
  for (let i = 0; i < c.display.length; i++) {
    const ch = c.display.charCodeAt(i);
    if (ch >= 48 && ch <= 57) n++;
  }
  return n;
}

function calcApply(op: string, a: number, b: number) {
  'worklet';
  if (op === '÷') return b === 0 ? NaN : a / b;
  if (op === '×') return a * b;
  if (op === '−') return a - b;
  return a + b;
}

/** At most nine characters, the minus sign written as "−". */
export function calcFormat(x: number) {
  'worklet';
  if (!isFinite(x)) return 'Error';
  let s = '';
  if (x === roundHalf(x) && Math.abs(x) < 1e9) {
    s = String(Math.trunc(x));
  } else {
    for (let d = 8; d >= 0; d--) {
      let t = x.toFixed(d);
      if (t.indexOf('.') >= 0) {
        while (t.endsWith('0')) t = t.slice(0, -1);
        if (t.endsWith('.')) t = t.slice(0, -1);
      }
      if (t.length <= 9) {
        s = t;
        break;
      }
    }
  }
  if (s === '') return 'Error';
  if (s === '-0') s = '0';
  return s.replace('-', '−');
}

function calcPress(c: Calc, key: string) {
  'worklet';
  if (c.display === 'Error' && key !== 'AC') calcClear(c);
  if (key === 'AC') {
    calcClear(c);
  } else if (key >= '0' && key <= '9' && key.length === 1) {
    if (c.fresh || c.display === '0') {
      c.display = key;
      c.fresh = false;
    } else if (calcDigits(c) < MAX_DIGITS) {
      c.display += key;
    }
  } else if (key === '.') {
    if (c.fresh) {
      c.display = '0.';
      c.fresh = false;
    } else if (c.display.indexOf('.') < 0 && calcDigits(c) < MAX_DIGITS) {
      c.display += '.';
    }
  } else if (key === '±') {
    if (c.display === '0') return;
    c.display = c.display.startsWith('−') ? c.display.slice(1) : '−' + c.display;
  } else if (key === '%') {
    c.display = calcFormat(calcValue(c) / 100);
  } else if (key === '÷' || key === '×' || key === '−' || key === '+') {
    if (c.op !== '' && c.hasAcc && !c.fresh) c.display = calcFormat(calcApply(c.op, c.acc, calcValue(c)));
    c.acc = calcValue(c);
    c.hasAcc = true;
    c.op = key;
    c.fresh = true;
  } else if (key === '=') {
    if (c.op !== '' && c.hasAcc) {
      c.display = calcFormat(calcApply(c.op, c.acc, calcValue(c)));
      c.hasAcc = false;
      c.op = '';
    }
    c.fresh = true;
  }
}

/** The delete tag: drops the last character. */
function calcBackspace(c: Calc) {
  'worklet';
  if (c.fresh || c.display === 'Error') return;
  c.display = c.display.slice(0, -1);
  if (c.display === '' || c.display === '−') c.display = '0';
}

// Driver

const NONE = -1e9;
const IDLE_BEFORE_HOME = 2;
const HOME_DUR = 1.4;

export type Driver = {
  autoStart: number;
  manual: boolean;
  dragging: boolean;
  lastTouch: number;
  returnAt: number;
  base: number;
  changes: Change[];
  // The strip: a spring from (anchorX, anchorV) at anchorAt towards target, or the finger.
  target: number;
  anchorAt: number;
  anchorX: number;
  anchorV: number;
  dragFrom: number;
  dragDx: number;
  detent: number;
  calc: Calc;
  presses: number[];
};

export function makeDriver(): Driver {
  'worklet';
  return {
    autoStart: NONE,
    manual: false,
    dragging: false,
    lastTouch: NONE,
    returnAt: NONE,
    base: HOME,
    changes: [],
    target: HOME,
    anchorAt: NONE,
    anchorX: 0,
    anchorV: 0,
    dragFrom: 0,
    dragDx: 0,
    detent: 0,
    calc: { display: '0', acc: 0, hasAcc: false, op: '', fresh: true },
    presses: emptyPresses(),
  };
}

export function applied(d: Driver) {
  'worklet';
  return d.changes.length > 0 ? d.changes[d.changes.length - 1].to : d.base;
}

export function driverScroll(d: Driver, now: number, reduced: boolean) {
  'worklet';
  if (d.dragging) return d.dragFrom - d.dragDx / SWATCH_PITCH;
  const tau = now - d.anchorAt;
  if (reduced) return d.target + d.anchorX * (1 - easeInOutCubic(seg(tau, 0, 0.2)));
  return d.target + spring(d.anchorX, d.anchorV, tau);
}

function driverVelocity(d: Driver, now: number, reduced: boolean) {
  'worklet';
  if (d.dragging || reduced) return 0;
  return springVelocity(d.anchorX, d.anchorV, now - d.anchorAt);
}

function send(d: Driver, to: number, dir: number, now: number) {
  'worklet';
  if (to === applied(d)) return false;
  d.changes.push({ at: now, to, dir: dir >= 0 ? 1 : -1 });
  return true;
}

function settleTo(d: Driver, t: number, now: number, reduced: boolean) {
  'worklet';
  const s = driverScroll(d, now, reduced);
  const v = driverVelocity(d, now, reduced);
  d.target = t;
  d.anchorAt = now;
  d.anchorX = s - t;
  d.anchorV = v;
}

/** Clear the display and glide the strip home to Classic. */
function goHome(d: Driver, now: number, reduced: boolean) {
  'worklet';
  d.returnAt = now;
  calcClear(d.calc);
  const s = driverScroll(d, now, reduced);
  const r = roundHalf(s);
  const k = r - mod(r) + HOME;
  let best = k;
  if (Math.abs(k - COUNT - s) < Math.abs(best - s)) best = k - COUNT;
  if (Math.abs(k + COUNT - s) < Math.abs(best - s)) best = k + COUNT;
  settleTo(d, best, now, reduced);
  send(d, HOME, best >= s ? 1 : -1, now);
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Scene {
  'worklet';
  if (auto && d.manual && !d.dragging) {
    if (d.returnAt === NONE && now >= d.lastTouch + IDLE_BEFORE_HOME) goHome(d, now, reduced);
    if (d.returnAt !== NONE && now >= d.returnAt + HOME_DUR) {
      d.manual = false;
      d.returnAt = NONE;
      d.autoStart = now;
      d.base = HOME;
      d.changes = [];
    }
  }
  if (auto && !d.manual) {
    if (d.autoStart === NONE) d.autoStart = now;
    const t = (now - d.autoStart) % LOOP;
    return autoScene(t);
  }
  // Changes that are complete everywhere become the base.
  while (d.changes.length > 0 && now > d.changes[0].at + SETTLE) {
    d.base = d.changes[0].to;
    d.changes.shift();
  }
  return {
    now,
    base: d.base,
    changes: d.changes.slice(),
    scroll: driverScroll(d, now, reduced),
    display: d.calc.display,
    presses: d.presses.slice(),
    reduced,
  };
}

/** The finger takes over from autoPlay: carry on from what the loop shows now. */
export function driverTouch(d: Driver, now: number, auto: boolean) {
  'worklet';
  d.lastTouch = now;
  d.returnAt = NONE;
  if (!auto || d.manual) return;
  d.manual = true;
  const start = d.autoStart === NONE ? now : d.autoStart;
  const loopStart = start + Math.floor((now - start) / LOOP) * LOOP;
  const t = now - loopStart;
  d.base = HOME;
  d.changes = [];
  // A move that has started keeps its wave, even if the wave starts a moment later.
  for (let i = 0; i < AUTO_CHANGES.length; i++) {
    const c = AUTO_CHANGES[i];
    if (c.at - WAVE_AFTER_MOVE <= t) d.changes.push({ at: c.at + loopStart, to: c.to, dir: c.dir });
  }
  const a = autoScroll(t);
  d.target = a.target;
  d.anchorAt = now;
  d.anchorX = a.s - a.target;
  d.anchorV = a.v;
}

export function driverBeginDrag(d: Driver, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  driverTouch(d, now, auto);
  d.dragFrom = driverScroll(d, now, reduced);
  d.dragDx = 0;
  d.detent = roundHalf(d.dragFrom);
  d.dragging = true;
}

/** Follows the finger; true when a swatch crosses the centre. */
export function driverDrag(d: Driver, dx: number, now: number) {
  'worklet';
  d.lastTouch = now;
  d.dragDx = dx;
  const k = roundHalf(d.dragFrom - dx / SWATCH_PITCH);
  if (k !== d.detent) {
    d.detent = k;
    return true;
  }
  return false;
}

export function driverCancelDrag(d: Driver, now: number) {
  'worklet';
  if (!d.dragging) return;
  const s = d.dragFrom - d.dragDx / SWATCH_PITCH;
  d.dragging = false;
  d.target = roundHalf(s);
  d.anchorAt = now;
  d.anchorX = s - d.target;
  d.anchorV = 0;
}

/** Snaps to the swatch the flick lands on; true when that sends a wave. */
export function driverEndDrag(d: Driver, vx: number, now: number, reduced: boolean) {
  'worklet';
  if (!d.dragging) return false;
  d.lastTouch = now;
  const s = d.dragFrom - d.dragDx / SWATCH_PITCH;
  const v = Math.max(-40, Math.min(40, -vx / SWATCH_PITCH));
  d.dragging = false;
  const r = roundHalf(s);
  const t = Math.max(r - 4, Math.min(r + 4, roundHalf(s + v * 0.2)));
  d.target = t;
  d.anchorAt = now;
  d.anchorX = s - t;
  d.anchorV = reduced ? 0 : v;
  const dir = t > d.dragFrom ? 1 : t < d.dragFrom ? -1 : v >= 0 ? 1 : -1;
  return send(d, mod(t), dir, now);
}

/** Picks the swatch `by` steps from the centred one; true when that sends a wave. */
export function driverTapSwatch(d: Driver, by: number, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  driverTouch(d, now, auto);
  const s = driverScroll(d, now, reduced);
  const t = roundHalf(s) + by;
  settleTo(d, t, now, reduced);
  return send(d, mod(t), by >= 0 ? 1 : -1, now);
}

export function driverPressDown(d: Driver, r: number, c: number, now: number, auto: boolean) {
  'worklet';
  driverTouch(d, now, auto);
  d.presses[r * 5 + c] = now;
}

export function driverPressKey(d: Driver, r: number, c: number, now: number) {
  'worklet';
  d.lastTouch = now;
  calcPress(d.calc, LABELS[r][c]);
}

export function driverBackspace(d: Driver, now: number) {
  'worklet';
  d.lastTouch = now;
  calcBackspace(d.calc);
}
