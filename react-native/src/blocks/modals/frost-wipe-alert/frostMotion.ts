// The wipe trail and the droplets: a plain object on the UI thread, read by the shader.
//
// Points are kept in time order (oldest first), at most TRAIL of them. Each has a position, a
// brush radius (negative = first point of a stroke), the frame-clock time it was laid down, and
// when and how fast it refreezes (regrowStart, regrowSpan). Its regrow progress q goes from -0.05
// (clear) to 1.2 (frosted over) across the span. In autoPlay the whole trail is rebuilt each frame
// from the loop time; a touch keeps the trail as it is and adds to it.

export const LOOP = 9;
export const TRAIL = 32;
export const DROPS = 8;
/** Brush radius (pt), and the travel between two laid points (pt). */
export const BRUSH = 44;
export const STEP = 18;
// A finger's wipe stays clear HOLD s, then refreezes over SPAN s.
const HOLD = 1.2;
const SPAN = 6;
// The script: a zig-zag across the alert 1.4–4.0, clear until 5.4, refrozen by 8.4.
const WIPE = [1.4, 4.0];
const SCRIPT_REGROW = 5.4;
const SCRIPT_SPAN = 3.0;
const ZIGS = 3.5;
// Droplets (s): they hang SAG s, run down, fade out over the last FADE s of LIFE.
const DROP_LIFE = 2.2;
const DROP_SAG = 0.3;
const DROP_FADE = 0.4;
const SCRIPT_DROPS = 6;
const SCRIPT_DROP_AT = 4.2;
const SCRIPT_DROP_LIFE = 1.8;
export const IDLE_BEFORE_BACK = 2;
const BACK_FOR = 0.9;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

export type Frost = {
  mode: number;
  t: number;
  now: number;
  lastTouch: number;
  /** The trail, oldest first. */
  px: number[];
  py: number[];
  pr: number[];
  pt: number[];
  rs: number[];
  sp: number[];
  /** Droplets: start x, y, birth time, life, size. */
  dx: number[];
  dy: number[];
  db: number[];
  dl: number[];
  ds: number[];
  dropNext: number;
  /** A finger wiping: the next point starts a new stroke when `pen` is up. */
  wiping: boolean;
  pen: boolean;
  laid: number;
  /** Easing back: when it began. */
  backAt: number;
  backK: number;
};

export function makeFrost(auto: boolean): Frost {
  return {
    mode: auto ? MODE_AUTO : MODE_LIVE,
    t: 0,
    now: 0,
    lastTouch: -100,
    px: [],
    py: [],
    pr: [],
    pt: [],
    rs: [],
    sp: [],
    dx: new Array(DROPS).fill(0),
    dy: new Array(DROPS).fill(0),
    db: new Array(DROPS).fill(-100),
    dl: new Array(DROPS).fill(DROP_LIFE),
    ds: new Array(DROPS).fill(0),
    dropNext: 0,
    wiping: false,
    pen: false,
    laid: 0,
    backAt: 0,
    backK: 0,
  };
}

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/** The scripted zig-zag over the alert card (box = x, y, w, h), at progress u (0…1). */
function zigzag(u: number, box: number[]): [number, number] {
  'worklet';
  const inset = 26;
  const left = box[0] + inset;
  const right = box[0] + box[2] - inset;
  const x = (left + right) / 2 - ((right - left) / 2) * Math.cos(Math.PI * ZIGS * u);
  const y = box[1] + 40 + (box[3] - 80) * u;
  return [x, y];
}

/** Rebuilds the trail and droplets for loop time t (frame-clock times, so a touch can continue). */
export function scriptFrame(f: Frost, t: number, box: number[]) {
  'worklet';
  const start = f.now - t;
  f.px.length = 0;
  f.py.length = 0;
  f.pr.length = 0;
  f.pt.length = 0;
  f.rs.length = 0;
  f.sp.length = 0;
  const step = (WIPE[1] - WIPE[0]) / (TRAIL - 1);
  for (let k = 0; k < TRAIL; k += 1) {
    const te = WIPE[0] + k * step;
    if (te > t) break;
    // The cosine slows the hand into each turn of the zig-zag.
    const [x, y] = zigzag(seg(te, WIPE[0], WIPE[1]), box);
    const grow = Math.min((t - te) / 0.12, 1);
    f.px.push(x);
    f.py.push(y);
    f.pr.push((k === 0 ? -1 : 1) * BRUSH * (0.4 + 0.6 * grow));
    f.pt.push(start + te);
    f.rs.push(start + SCRIPT_REGROW);
    f.sp.push(SCRIPT_SPAN);
  }
  for (let i = 0; i < DROPS; i += 1) {
    f.db[i] = -100;
    if (i >= SCRIPT_DROPS) continue;
    const k = Math.floor(hash(i * 3 + 1) * (TRAIL - 4)) + 2;
    const [x, y] = zigzag(k / (TRAIL - 1), box);
    f.dx[i] = x + (hash(i * 5 + 2) - 0.5) * BRUSH;
    f.dy[i] = y + BRUSH * 0.62;
    f.db[i] = start + SCRIPT_DROP_AT + i * 0.11;
    f.dl[i] = SCRIPT_DROP_LIFE - i * 0.11;
    f.ds[i] = 5.5 + 2.5 * hash(i * 7 + 3);
  }
}

/** Regrow progress of point i at frame time now, raised to at least `floor` while easing back. */
export function regrow(f: Frost, i: number, floor: number) {
  'worklet';
  const q = ((f.now - f.rs[i]) / f.sp[i]) * 1.25 - 0.05;
  return Math.max(q, floor);
}

/** A droplet's head (x, y), trail length and radius at frame time now; radius 0 when gone. */
export function dropAt(f: Frost, i: number, fade: number): [number, number, number, number] {
  'worklet';
  const age = f.now - f.db[i];
  const life = f.dl[i];
  if (age < 0 || age >= life) return [0, 0, 0, 0];
  const run = Math.max(age - DROP_SAG, 0);
  const fall = 95 * Math.pow(run, 1.6);
  const x = f.dx[i] + 1.6 * Math.sin(run * 6 + i);
  const size = f.ds[i] * Math.min(age / 0.15, 1) * (1 - seg(age, life - DROP_FADE, life)) * fade;
  return [x, f.dy[i] + fall, fall, size];
}

/** Lays a point under the finger. */
export function lay(f: Frost, x: number, y: number) {
  'worklet';
  if (f.px.length >= TRAIL) {
    f.px.shift();
    f.py.shift();
    f.pr.shift();
    f.pt.shift();
    f.rs.shift();
    f.sp.shift();
    // The new oldest point no longer has the one before it.
    if (f.pr.length > 0) f.pr[0] = -Math.abs(f.pr[0]);
  }
  f.px.push(x);
  f.py.push(y);
  f.pr.push((f.pen ? 1 : -1) * BRUSH);
  f.pt.push(f.now);
  f.rs.push(f.now + HOLD);
  f.sp.push(SPAN);
  f.pen = true;
  f.laid += 1;
  // Now and then a drop gathers at the lower edge of the wipe.
  if (hash(f.laid * 1.37) < 0.22) {
    const i = f.dropNext;
    f.dropNext = (i + 1) % DROPS;
    f.dx[i] = x + (hash(f.laid * 2.1) - 0.5) * BRUSH * 0.8;
    f.dy[i] = y + BRUSH * 0.62;
    f.db[i] = f.now + 0.25;
    f.dl[i] = DROP_LIFE;
    f.ds[i] = 5.5 + 2.5 * hash(f.laid * 3.3);
  }
}

export function beginBack(f: Frost) {
  'worklet';
  f.mode = MODE_BACK;
  f.backAt = f.now;
  f.backK = 0;
  f.wiping = false;
}

/** Easing back to the fully frosted rest; true when done. */
export function backFrame(f: Frost) {
  'worklet';
  f.backK = easeInOut(Math.min((f.now - f.backAt) / BACK_FOR, 1));
  if (f.backK >= 1) {
    f.px.length = 0;
    f.py.length = 0;
    f.pr.length = 0;
    f.pt.length = 0;
    f.rs.length = 0;
    f.sp.length = 0;
    for (let i = 0; i < DROPS; i += 1) f.db[i] = -100;
    f.backK = 0;
    return true;
  }
  return false;
}

/** How clear the pane is at (x, y), as the shader sees it (without the noise). */
export function clearAt(f: Frost, x: number, y: number) {
  'worklet';
  let best = 0;
  for (let i = 0; i < f.px.length; i += 1) {
    const r = Math.abs(f.pr[i]);
    let d = Math.hypot(x - f.px[i], y - f.py[i]);
    if (i > 0 && f.pr[i] > 0) {
      const ax = f.px[i - 1];
      const ay = f.py[i - 1];
      const bx = f.px[i] - ax;
      const by = f.py[i] - ay;
      const h = Math.min(Math.max(((x - ax) * bx + (y - ay) * by) / Math.max(bx * bx + by * by, 0.0001), 0), 1);
      d = Math.min(d, Math.hypot(x - ax - bx * h, y - ay - by * h));
    }
    const cover = 1 - Math.min(Math.max((d - r * 0.5) / (r * 0.5), 0), 1);
    if (cover <= 0) continue;
    const q = regrow(f, i, f.mode === MODE_BACK ? f.backK * 1.25 : -1);
    if (cover * 0.9 > q) best = Math.max(best, cover);
  }
  return best;
}
