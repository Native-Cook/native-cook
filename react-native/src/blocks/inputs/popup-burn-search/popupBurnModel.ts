/*
 Timeline, geometry and touch logic of Popup Burn Search (a port of PopupBurnSearchModel.swift).
 Every function is a worklet: the frame is computed on the UI thread from the loop time.
 Helpers are declared above their callers (a worklet cannot call one declared further down).
*/

export const LOOP = 9.0;
export const NEVER = 1e9;

export const QUERY = 'WHAT SHOULD I COOK TONIGHT?';
export const ANSWERS: string[][] = [
  ['order', 'noodles'],
  ['just toast'],
  ['cereal', 'again'],
  ['call mom'],
  ['eggs,', 'somehow'],
  ['raid the', 'fridge'],
];
const OFFSET_X = [-62, 64, -70, 70, -62, 58];
const OFFSET_Y = [-140, -88, -22, 32, 96, 150];
export const STATUSES = [
  'TYPE A QUESTION, THEN SEARCH',
  '6 ANSWERS FOUND',
  '1 ANSWER LEFT',
  '2 ANSWERS LEFT',
  '3 ANSWERS LEFT',
  '4 ANSWERS LEFT',
  '5 ANSWERS LEFT',
  'NOTHING LEFT. ASK AGAIN?',
];

export const WIN_W = 214;
export const WIN_H = 118;
export const TITLE_H = 18;
export const BURN_LENGTH = 1.8;

const TYPE_START = 0.9;
const BURST_AT = 2.5;
const PRESS_AT = 3.05;
const SPAWN_AT = 3.25;
const SPAWN_GAP = 0.16;
const BURN_AT = 4.9;
const BURN_GAP = 0.18;
const SELECT_AT = 7.65;
const BURST_OFF = 7.7;

export type Pt = { x: number; y: number };
export type Box = { x: number; y: number; w: number; h: number };
export type Particle = { x: number; y: number; size: number; angle: number; color: number; alpha: number };

// Layout

export function barY(h: number) {
  'worklet';
  return h * 0.29;
}

export function buttonX(w: number) {
  'worklet';
  return w / 2 + 122;
}

export function windowRect(i: number, w: number, h: number): Box {
  'worklet';
  const cx = w / 2 + OFFSET_X[i];
  const cy = barY(h) + 260 + OFFSET_Y[i];
  return { x: cx - WIN_W / 2, y: cy - WIN_H / 2, w: WIN_W, h: WIN_H };
}

export function buttonHit(x: number, y: number, w: number, h: number) {
  'worklet';
  return Math.abs(x - buttonX(w)) < 44 && Math.abs(y - barY(h)) < 36;
}

// Math

export function hash(x: number) {
  'worklet';
  const v = Math.sin(x) * 43758.5453;
  return v - Math.floor(v);
}

function h3(a: number, b: number, c: number) {
  'worklet';
  return hash(a * 12.7 + b * 57.3 + c * 3.71 + 0.5);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

export function easeOutBack(x: number) {
  'worklet';
  const c1 = 1.2;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

function easeInBack(x: number) {
  'worklet';
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return c3 * x * x * x - c1 * x * x;
}

function spring(t: number, response: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = (2 * Math.PI) / response;
  const wd = w0 * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-damping * w0 * t) * (Math.cos(wd * t) + ((damping * w0) / wd) * Math.sin(wd * t));
}

function charTime(i: number) {
  'worklet';
  return 0.9 + 0.056 * i + (hash(i + 1) - 0.5) * 0.03;
}

// Burn geometry (age a since a window ignites)

export function burnFront(a: number, r: Box) {
  'worklet';
  const u = easeInOutSine(seg(a, 0.15, 1.6));
  return r.y + r.h + 4 - u * (WIN_H + 26);
}

function flameDie(a: number) {
  'worklet';
  return 1 - easeInCubic(seg(a, 1.25, 1.8));
}

export function flameGrow(a: number) {
  'worklet';
  return easeOutBack(seg(a, 0, 0.28)) * flameDie(a);
}

/** The jagged burn edge, 24 points from left − 2 to right + 14, clamped to the window's top. */
export function edgePoints(i: number, a: number, r: Box): Pt[] {
  'worklet';
  const front = burnFront(a, r);
  const q = Math.floor(a * 12);
  const pts: Pt[] = [];
  for (let k = 0; k < 24; k++) {
    const x = r.x - 2 + k * 10;
    const j = (hash(k * 3.1 + i * 17.9) - 0.5) * 12 + 4 * Math.sin(k * 1.7 + q * 0.5);
    pts.push({ x, y: Math.max(front + j, r.y - 2) });
  }
  return pts;
}

const FLAME_H = [1, 1, 0.7, 0.45];
const FLAME_W = [1, 1, 0.8, 0.6];
const FLAME_D = [26, 26, 18, 10];

/** One layer (0 shadow, 1 red, 2 orange, 3 yellow) of a window's flame crown, as a closed polygon. */
export function flameContour(i: number, l: number, a: number, r: Box): Pt[] {
  'worklet';
  const g = flameGrow(a);
  const front = burnFront(a, r);
  const left = r.x - 8;
  const right = r.x + r.w + 8;
  const mid = (left + right) / 2;
  const n = 6;
  const step = (right - left) / n;
  const q = Math.floor(a * 12);
  const ws = FLAME_W[l] * (0.3 + 0.7 * flameDie(a));
  const hs = FLAME_H[l];
  const valley = 10 * g * hs;
  const pts: Pt[] = [
    { x: mid + (left - mid) * ws, y: front + 2 },
    { x: mid + (left - mid) * ws, y: front - valley },
  ];
  for (let j = 0; j < n; j++) {
    const base = left + (j + 0.5) * step;
    const tall = 70 + 50 * h3(j, i, 1);
    const alt = j % 2 === 1 ? 0.55 : j === 0 || j === n - 1 ? 0.8 : 1;
    const flick = 0.85 + 0.25 * h3(j, i, q + 50);
    const hgt = tall * alt * flick * g * hs;
    const tipX = base + (base - mid) * 0.45 + (h3(j, i, q + 90) - 0.5) * 16;
    pts.push({ x: mid + (tipX - mid) * ws, y: front - hgt });
    pts.push({ x: mid + (left + (j + 1) * step - mid) * ws, y: front - valley });
  }
  pts.push({ x: mid + (right - mid) * ws, y: front + 2 });
  const depth = FLAME_D[l] * g;
  for (let k = 1; k < 8; k++) {
    const f = k / 8;
    pts.push({ x: mid + (right - (right - left) * f - mid) * ws, y: front + 2 + depth * 4 * f * (1 - f) });
  }
  return pts;
}

export function embers(i: number, a: number, r: Box): Particle[] {
  'worklet';
  const out: Particle[] = [];
  for (let k = 0; k < 12; k++) {
    const b = 0.3 + 0.09 * k;
    const life = 0.8 + 0.3 * h3(k, i, 3);
    const s = (a - b) / life;
    if (s < 0 || s >= 1) continue;
    const x0 = r.x + (0.08 + 0.84 * h3(k, i, 4)) * WIN_W;
    const y0 = burnFront(b, r);
    const x = x0 + 12 * Math.sin(2 * Math.PI * (s * 1.2 + h3(k, i, 6)));
    const y = y0 - s * (70 + 70 * h3(k, i, 5));
    const color = s < 0.35 ? 0xffe03d : s < 0.7 ? 0xff8c1a : 0xe8203a;
    const alpha = s < 0.6 ? 1 : 1 - (s - 0.6) / 0.4;
    out.push({ x, y, size: 6 - 4 * s, angle: 0, color, alpha });
  }
  for (let k = 0; k < 5; k++) {
    const b = 0.5 + 0.2 * k;
    const s = (a - b) / 1.1;
    if (s < 0 || s >= 1) continue;
    const x0 = r.x + (0.1 + 0.8 * h3(k, i, 7)) * WIN_W;
    const y0 = burnFront(b, r);
    const x = x0 + (h3(k, i, 8) - 0.5) * 32 * Math.sin(Math.PI * s);
    const y = y0 - s * (50 + 40 * h3(k, i, 9));
    out.push({ x, y, size: 4 + 2 * h3(k, i, 10), angle: 3 * s, color: 0x1c120c, alpha: 0.85 * (1 - s) });
  }
  return out;
}

/** Starburst polygon around (0, 0), 13 spikes; q is the 8 fps boil frame. */
export function burstPoints(q: number): Pt[] {
  'worklet';
  const n = 13;
  const pts: Pt[] = [];
  for (let j = 0; j < n; j++) {
    const base = (j / n) * 2 * Math.PI - Math.PI / 2;
    const boil = 1 + (hash(j * 7.7 + q * 1.3) - 0.5) * 0.1;
    const outer = (52 + 30 * hash(j * 4.3 + 11)) * boil;
    const inner = 27 + 6 * hash(j * 2.9 + 23);
    const ao = base + (hash(j * 5.9 + 31) - 0.5) * 0.24;
    const ai = base + Math.PI / n;
    pts.push({ x: outer * Math.cos(ao), y: outer * Math.sin(ao) });
    pts.push({ x: inner * Math.cos(ai), y: inner * Math.sin(ai) });
  }
  return pts;
}

// Story: what happens when, in one time frame (loop time while playing, takeover time after a touch).

export type Story = {
  typeStart: number;
  selectAt: number;
  burstAt: number;
  burstOff: number;
  pressAt: number;
  spawn: number[];
  burn: number[];
  cursorUntil: number;
};

export function emptyStory(): Story {
  'worklet';
  return {
    typeStart: NEVER,
    selectAt: NEVER,
    burstAt: NEVER,
    burstOff: NEVER,
    pressAt: -NEVER,
    spawn: [NEVER, NEVER, NEVER, NEVER, NEVER, NEVER],
    burn: [NEVER, NEVER, NEVER, NEVER, NEVER, NEVER],
    cursorUntil: NEVER,
  };
}

export function baseStory(): Story {
  'worklet';
  const s = emptyStory();
  s.typeStart = TYPE_START;
  s.selectAt = SELECT_AT;
  s.burstAt = BURST_AT;
  s.burstOff = BURST_OFF;
  s.pressAt = PRESS_AT;
  for (let i = 0; i < 6; i++) {
    s.spawn[i] = SPAWN_AT + SPAWN_GAP * i;
    s.burn[i] = BURN_AT + BURN_GAP * (5 - i);
  }
  return s;
}

/** The base story with every event after t dropped (a touch took over at loop time t). */
function cutStory(t: number): Story {
  'worklet';
  const s = baseStory();
  if (s.typeStart > t) s.typeStart = NEVER;
  if (s.selectAt > t) s.selectAt = NEVER;
  if (s.burstAt > t) s.burstAt = NEVER;
  if (s.burstOff > t) s.burstOff = NEVER;
  if (s.pressAt > t) {
    s.pressAt = -NEVER;
    for (let i = 0; i < 6; i++) s.spawn[i] = NEVER;
  }
  for (let i = 0; i < 6; i++) if (s.burn[i] > t) s.burn[i] = NEVER;
  s.cursorUntil = t;
  return s;
}

function shownStory(t: number): Story {
  'worklet';
  const s = emptyStory();
  s.typeStart = t - 10;
  s.burstAt = t - 10;
  for (let i = 0; i < 6; i++) s.spawn[i] = t - 10;
  s.cursorUntil = -NEVER;
  return s;
}

function typedAt(s: Story, t: number) {
  'worklet';
  if (t >= s.selectAt + 0.25) return 0;
  let n = 0;
  for (let i = 0; i < QUERY.length; i++) if (t >= s.typeStart + charTime(i) - TYPE_START) n++;
  return n;
}

function standing(s: Story, i: number) {
  'worklet';
  return s.spawn[i] < NEVER && s.burn[i] >= NEVER;
}

function gone(s: Story, i: number, t: number) {
  'worklet';
  return s.spawn[i] >= NEVER || (s.burn[i] < NEVER && t >= s.burn[i] + BURN_LENGTH);
}

// Frame

export type Frame = {
  t: number;
  typed: number;
  selected: boolean;
  caret: boolean;
  burstScale: number;
  burstAngle: number;
  burstQ: number;
  press: number;
  cursorX: number;
  cursorY: number;
  cursorAlpha: number;
  cursorSquish: number;
  status: number;
  spawnAge: number[];
  burnAge: number[];
  reduced: boolean;
};

export function makeFrame(s: Story, t: number, reduced: boolean): Frame {
  'worklet';
  const typed = typedAt(s, t);
  const selected = t >= s.selectAt && t < s.selectAt + 0.25;
  const typing = typed > 0 && typed < QUERY.length;
  const f: Frame = {
    t,
    typed,
    selected,
    caret: !selected && (reduced || typing || Math.floor(t / 0.5) % 2 === 0),
    burstScale: 0,
    burstAngle: 0,
    burstQ: reduced ? 0 : Math.floor(t * 8),
    press: 0,
    cursorX: 0,
    cursorY: 0,
    cursorAlpha: 0,
    cursorSquish: 1,
    status: 0,
    spawnAge: [-1, -1, -1, -1, -1, -1],
    burnAge: [-1, -1, -1, -1, -1, -1],
    reduced,
  };

  if (t >= s.burstAt) {
    const a = t - s.burstAt;
    const sp = reduced ? 1 : spring(a, 0.5, 0.55);
    let scale = sp;
    const p = t - s.pressAt;
    if (p >= 0 && p < 0.3 && !reduced) scale *= 1 + 0.12 * Math.sin((Math.PI * p) / 0.3);
    scale *= 1 - easeInBack(seg(t, s.burstOff, s.burstOff + 0.25));
    f.burstScale = Math.max(0, scale);
    f.burstAngle = reduced ? 0 : -25 * (1 - sp);
  }

  const p = t - s.pressAt;
  if (p >= 0 && p < 0.2) f.press = p < 0.06 ? easeOutCubic(p / 0.06) : 1 - easeOutCubic((p - 0.06) / 0.14);

  if (!reduced) {
    const c = Math.min(t, s.cursorUntil);
    const mv = easeInOutCubic(seg(c, 2.3, 2.95));
    let x = lerp(-82, 2, mv) + 30 * Math.sin(Math.PI * mv);
    let y = lerp(190, 6, mv);
    const d = easeInOutCubic(seg(c, 3.6, 4.2));
    x = lerp(x, 34, d);
    y = lerp(y, 74, d);
    f.cursorX = x;
    f.cursorY = y;
    f.cursorAlpha = seg(c, 2.3, 2.45) * (1 - seg(c, 3.9, 4.2)) * (1 - seg(t, s.cursorUntil, s.cursorUntil + 0.2));
    const q = c - PRESS_AT;
    if (q >= 0 && q < 0.06) f.cursorSquish = 1 - (0.2 * q) / 0.06;
    else if (q >= 0.06 && q < 0.2) f.cursorSquish = 0.8 + 0.2 * easeOutCubic((q - 0.06) / 0.14);
  }

  let anySpawned = false;
  let left = 0;
  for (let i = 0; i < 6; i++) {
    if (t >= s.spawn[i]) {
      f.spawnAge[i] = t - s.spawn[i];
      anySpawned = true;
    }
    if (t >= s.burn[i]) f.burnAge[i] = t - s.burn[i];
    if (s.spawn[i] < NEVER && f.burnAge[i] < 1.0) left++;
  }
  if (!anySpawned || t >= s.selectAt + 0.25) f.status = 0;
  else if (left === 0) f.status = 7;
  else if (left === 6) f.status = 1;
  else f.status = left + 1;
  return f;
}

// Driver: plays the loop, takes over on touch, and eases back to the loop 2 s after the last touch.

export type Driver = {
  manual: boolean;
  loopStart: number;
  hasLoop: boolean;
  epoch: number;
  story: Story;
  lastTouch: number;
  returning: boolean;
  settleAt: number;
  hasSettle: boolean;
  reducedReady: boolean;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    manual: false,
    loopStart: 0,
    hasLoop: false,
    epoch: 0,
    story: emptyStory(),
    lastTouch: -NEVER,
    returning: false,
    settleAt: 0,
    hasSettle: false,
    reducedReady: false,
  };
}

function igniteAll(d: Driver, t: number, gap: number) {
  'worklet';
  let rank = 0;
  for (let i = 5; i >= 0; i--) {
    if (!standing(d.story, i)) continue;
    d.story.burn[i] = Math.max(t + rank * gap, d.story.spawn[i] + 0.3);
    rank++;
  }
}

function settle(d: Driver, now: number, t: number, auto: boolean) {
  'worklet';
  if (now - d.lastTouch < 2) return;
  if (!d.returning) {
    d.returning = true;
    igniteAll(d, t, BURN_GAP);
  }
  for (let i = 0; i < 6; i++) if (!gone(d.story, i, t)) return;
  const s = d.story;
  if (!d.hasSettle) {
    let end = t;
    if (typedAt(s, t) > 0 && s.selectAt >= NEVER) s.selectAt = t + 0.1;
    if (s.selectAt < NEVER) end = Math.max(end, s.selectAt + 0.25);
    if (t >= s.burstAt && s.burstOff >= NEVER) s.burstOff = t + 0.15;
    if (s.burstAt < NEVER && s.burstOff < NEVER) end = Math.max(end, s.burstOff + 0.25);
    d.settleAt = end + 0.3;
    d.hasSettle = true;
  }
  if (t >= d.settleAt) {
    d.manual = false;
    d.returning = false;
    d.hasSettle = false;
    d.story = emptyStory();
    if (auto) {
      d.loopStart = now;
      d.hasLoop = true;
    } else {
      d.epoch = now;
    }
  }
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (reduced) {
    if (!d.reducedReady) {
      d.reducedReady = true;
      d.manual = true;
      d.epoch = now;
      d.story = shownStory(0);
    }
    return makeFrame(d.story, now - d.epoch, true);
  }
  if (!d.manual) {
    if (auto) {
      if (!d.hasLoop) {
        d.hasLoop = true;
        d.loopStart = now;
      }
      const tau = (now - d.loopStart) % LOOP;
      return makeFrame(baseStory(), tau, false);
    }
    return makeFrame(d.story, now - d.epoch, false);
  }
  settle(d, now, now - d.epoch, auto);
  return makeFrame(d.story, now - d.epoch, false);
}

/** A finger went down: the story becomes ours from here. */
export function takeOver(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (!d.manual) {
    if (auto && d.hasLoop) {
      const tau = (now - d.loopStart) % LOOP;
      d.epoch = now - tau;
      d.story = cutStory(tau);
    } else {
      d.epoch = now;
      d.story = emptyStory();
    }
    d.manual = true;
  }
  d.lastTouch = now;
  d.returning = false;
  d.hasSettle = false;
}

/** Tap on the search button. */
export function search(d: Driver, now: number) {
  'worklet';
  const t = now - d.epoch;
  const s = d.story;
  s.pressAt = t;
  if (typedAt(s, t) < QUERY.length || s.selectAt < NEVER) {
    s.typeStart = t - 2;
    s.selectAt = NEVER;
  }
  if (!(t >= s.burstAt && t < s.burstOff)) {
    s.burstAt = t;
    s.burstOff = NEVER;
  }
  let k = 0;
  for (let i = 0; i < 6; i++) {
    if (!gone(s, i, t)) continue;
    s.spawn[i] = t + 0.2 + SPAWN_GAP * k;
    s.burn[i] = NEVER;
    k++;
  }
  return true;
}

/** Tap on a window: the topmost standing one under the finger ignites. */
export function burnAt(d: Driver, x: number, y: number, now: number, w: number, h: number) {
  'worklet';
  const t = now - d.epoch;
  for (let i = 5; i >= 0; i--) {
    if (!standing(d.story, i) || t < d.story.spawn[i]) continue;
    const r = windowRect(i, w, h);
    if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
      d.story.burn[i] = t;
      return true;
    }
  }
  return false;
}

/** Long press: everything standing ignites, bottom first. */
export function burnAll(d: Driver, now: number) {
  'worklet';
  const t = now - d.epoch;
  let any = false;
  for (let i = 0; i < 6; i++) if (standing(d.story, i)) any = true;
  igniteAll(d, t, 0.12);
  return any;
}
