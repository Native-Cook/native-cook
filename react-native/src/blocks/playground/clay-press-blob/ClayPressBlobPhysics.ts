// The clay's soft body: a closed ring of mass points around a pressure volume, stepped in fixed
// substeps on the UI thread. Everything here is plain arithmetic on arrays so it can be ported
// line for line. Units are points and seconds; every point has a mass of 1, so a force is an
// acceleration.

/** Points around the ring. */
export const CLAY_POINTS = 48;
// Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
const SUBSTEP = 1 / 240;
const MAX_FRAME = 1 / 30;

// Neighbours are tied by springs of this stiffness (1/s²), damped along the edge.
const EDGE_K = 2600;
const EDGE_DAMP = 6;
// Every point is pulled toward its place on the rest shape around the centroid. Soft, so the
// clay wobbles at about 1.8 Hz; much stiffer while a pinch is holding it.
const SHAPE_K = 130;
const PINCH_K = 420;
// The trapped volume pushes out along each edge's normal as the area drops below its rest area.
const PRESSURE_K = 26000;
// Bending stiffness along the rim (1/s²).
const BEND_K = 900;
// Damping of each point's motion relative to the body (wobble), and of the body itself (air).
const INNER_DAMP = 3.4;
const AIR_DAMP = 0.6;
// The body drifts back to its home on a slightly underdamped spring.
const HOME_K = 11;
const HOME_DAMP = 4.6;
// The walls are soft: a point that comes within WALL_CUSHION pt of one meets a damped spring that
// grows from nothing across that cushion and stiffens past it (WALL_K × (1 + (depth / WALL_GIVE)²)),
// so a thrown body slows before the wall and flattens against it over a few frames into a
// rounded squash, never a cut. WALL_GRIP drags lightly along the wall. Nothing ever stops dead on
// a line; only a far safety rail, WALL_RAIL pt past the wall, holds a point thrown wildly.
const WALL_K = 2200;
const WALL_DAMP = 40;
const WALL_GRIP = 4;
const WALL_GIVE = 12;
const WALL_CUSHION = 28;
const WALL_RAIL = 80;

// Plasticity. A point pushed more than YIELD pt off its rest radius drags the rest radius after
// it at YIELD_RATE per second, so pokes and pulls leave marks; the marks creep back toward the
// round shape at RECOVER_RATE per second and are smoothed along the ring like soft clay.
const YIELD = 3;
const YIELD_RATE = 5.5;
const RECOVER_RATE = 0.95;
const SMOOTH_RATE = 2.2;
// Pressing in the middle spreads the clay: the rest radius grows by up to SPREAD pt, most on the
// finger's side.
const SPREAD = 7;

// A finger that lands outside the clay is a disc of FINGER_R pt that pushes the rim in on a
// soft contact spring (CONTACT_K, CONTACT_DAMP), never by moving points outright.
const FINGER_R = 24;
const CONTACT_K = 2600;
const CONTACT_DAMP = 30;
// A finger pressed in near the rim notches it: rim points within the dent's reach are pulled in
// by up to NOTCH pt (on NOTCH_K), so a press at the edge dents the edge instead of pushing a lump.
const NOTCH = 11;
const NOTCH_K = 260;
// A press turns into a drag once the finger travels MOVE_SLOP pt; a touch that ends within
// TAP_TIME s without becoming a drag is a tap.
const MOVE_SLOP = 9;
const TAP_TIME = 0.26;
// A drag grabs the ring within GRAB_REACH pt of the finger, or, from inside, the side it pulls
// toward. The grabbed patch is a Gaussian GRAB_SIGMA points wide, held on a spring; its pull is
// rubber-banded so the lump never stretches past PULL_MAX pt.
const GRAB_REACH = 46;
const GRAB_SIGMA = 2.4;
const GRAB_SPAN = 6;
const GRAB_K = 950;
const GRAB_DAMP = 38;
const PULL_MAX = 130;
// A pulled lump keeps WALL_ROOM pt inside the walls, easing into that limit rather than meeting it.
const WALL_ROOM = 26;
// Let go faster than FLICK_SPEED pt/s and the whole body is thrown with FLICK_SHARE of the
// finger's speed, never more than FLICK_MAX.
const FLICK_SPEED = 700;
const FLICK_SHARE = 0.45;
const FLICK_MAX = 850;
const THROW_KEEP = 0.1;
// Pinch: the shape is stretched by the ratio of finger spans along their axis and squeezed
// across it, within these limits.
const PINCH_MIN = 0.6;
const PINCH_MAX = 1.5;
const PINCH_SET = 0.3;

// Dents: they deepen toward 1 at DENT_GROW per second while the finger stays down, hold their
// depth DENT_HOLD s after it lifts, then fill back in over DENT_RECOVER s. Their fingerprint
// ridges fade out over RIDGE_FADE s. Up to DENTS are kept; the shallowest gives way.
export const DENT_RADIUS = 30;
const DENTS = 6;
const DENT_GROW = 6.5;
const DENT_HOLD = 0.9;
const DENT_RECOVER = 2.6;
const RIDGE_FADE = 1.5;

// A thrown body that strikes a wall faster than THUD_SPEED pt/s reports a hit.
const THUD_SPEED = 260;

// Colour change: the new colour spreads from the tap over RECOLOUR_TIME s.
const RECOLOUR_TIME = 0.75;

/** Bits in `events`, cleared by whoever reads them. */
export const EVENT_PRESS = 1;
export const EVENT_RECOLOUR = 2;
export const EVENT_THUD = 4;
export const EVENT_RELEASE = 8;

export type ClayDent = {
  live: boolean;
  /** Centre, relative to the body's centroid. */
  x: number;
  y: number;
  depth: number;
  ridge: number;
  growing: boolean;
  /** Seconds since the finger lifted. */
  since: number;
  /** Seeds the fingerprint's tilt and breaks. */
  seed: number;
};

export type ClayFinger = {
  id: number;
  down: boolean;
  /** A scripted finger from autoPlay: no haptics, and a tap sets `tapColour` instead of cycling. */
  scripted: boolean;
  tapColour: number;
  x: number;
  y: number;
  startX: number;
  startY: number;
  startT: number;
  lastX: number;
  lastY: number;
  lastT: number;
  vx: number;
  vy: number;
  moved: boolean;
  dent: number;
};

export type ClaySim = {
  ready: boolean;
  time: number;
  acc: number;
  r0: number;
  homeX: number;
  homeY: number;
  left: number;
  top: number;
  right: number;
  bottom: number;
  /** The round body's area, and what pressing has spread it by since. */
  baseArea: number;
  spreadArea: number;
  edge0: number;
  x: number[];
  y: number[];
  vx: number[];
  vy: number[];
  rest: number[];
  // Scratch for the forces of one substep.
  ax: number[];
  ay: number[];
  cx: number;
  cy: number;
  cvx: number;
  cvy: number;
  fingers: ClayFinger[];
  /** 0 none, 1 press, 2 drag, 3 pinch. */
  mode: number;
  grab: number;
  grabFromX: number[];
  grabFromY: number[];
  // Scratch: each point's place on the rest shape this substep.
  tx: number[];
  ty: number[];
  pinchFrom: number;
  pinchScale: number;
  pinchUx: number;
  pinchUy: number;
  pressSide: number;
  flying: boolean;
  throwByUser: boolean;
  thudAt: number;
  /** Reduced motion: no throws, and the wobble dies out fast. */
  calm: boolean;
  dents: ClayDent[];
  colourFrom: number;
  colourTo: number;
  recolour: number;
  recolourX: number;
  recolourY: number;
  paletteSize: number;
  events: number;
  /** The user (not autoPlay) caused the events in `events`. */
  eventsByUser: boolean;
};

function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

function wrap(i: number, n: number) {
  'worklet';
  return ((i % n) + n) % n;
}

function newFinger(id: number) {
  'worklet';
  return {
    id,
    down: false,
    scripted: false,
    tapColour: -1,
    x: 0,
    y: 0,
    startX: 0,
    startY: 0,
    startT: 0,
    lastX: 0,
    lastY: 0,
    lastT: 0,
    vx: 0,
    vy: 0,
    moved: false,
    dent: -1,
  } as ClayFinger;
}

/** A fresh, round body at rest in the middle of the stage. */
export function createClay(
  width: number,
  height: number,
  top: number,
  bottom: number,
  paletteSize: number,
  calm: boolean,
): ClaySim {
  'worklet';
  const n = CLAY_POINTS;
  const left = 20;
  const right = width - 20;
  const r0 = Math.min(width * 0.3, (bottom - top) * 0.3, 132);
  const homeX = width / 2;
  const homeY = (top + bottom) / 2;
  const x: number[] = [];
  const y: number[] = [];
  const zero: number[] = [];
  const rest: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const a = (i / n) * Math.PI * 2;
    x.push(homeX + Math.cos(a) * r0);
    y.push(homeY + Math.sin(a) * r0);
    zero.push(0);
    rest.push(r0);
  }
  const dents: ClayDent[] = [];
  for (let i = 0; i < DENTS; i += 1) {
    dents.push({ live: false, x: 0, y: 0, depth: 0, ridge: 0, growing: false, since: 0, seed: 0 });
  }
  return {
    ready: true,
    time: 0,
    acc: 0,
    r0,
    homeX,
    homeY,
    left,
    top,
    right,
    bottom,
    baseArea: 0.5 * n * r0 * r0 * Math.sin((2 * Math.PI) / n),
    spreadArea: 0,
    edge0: 2 * r0 * Math.sin(Math.PI / n),
    x,
    y,
    vx: zero.slice(),
    vy: zero.slice(),
    rest,
    ax: zero.slice(),
    ay: zero.slice(),
    cx: homeX,
    cy: homeY,
    cvx: 0,
    cvy: 0,
    fingers: [newFinger(-1), newFinger(-1)],
    mode: 0,
    grab: -1,
    grabFromX: zero.slice(),
    tx: zero.slice(),
    ty: zero.slice(),
    grabFromY: zero.slice(),
    pinchFrom: 1,
    pinchScale: 1,
    pinchUx: 1,
    pinchUy: 0,
    pressSide: 0,
    flying: false,
    throwByUser: false,
    thudAt: -10,
    calm,
    dents,
    colourFrom: 0,
    colourTo: 0,
    recolour: 1,
    recolourX: homeX,
    recolourY: homeY,
    paletteSize,
    events: 0,
    eventsByUser: false,
  };
}

/** Is (px, py) inside the ring? An even-odd ray cast over the points. */
export function clayContains(s: ClaySim, px: number, py: number) {
  'worklet';
  const n = CLAY_POINTS;
  let inside = false;
  for (let i = 0, j = n - 1; i < n; j = i, i += 1) {
    const yi = s.y[i];
    const yj = s.y[j];
    if (yi > py !== yj > py && px < ((s.x[j] - s.x[i]) * (py - yi)) / (yj - yi) + s.x[i]) inside = !inside;
  }
  return inside;
}

function nearestPoint(s: ClaySim, px: number, py: number) {
  'worklet';
  let best = 0;
  let bestD = Infinity;
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    const d = (s.x[i] - px) * (s.x[i] - px) + (s.y[i] - py) * (s.y[i] - py);
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  }
  return { index: best, distance: Math.sqrt(bestD) };
}

function centroid(s: ClaySim) {
  'worklet';
  let cx = 0;
  let cy = 0;
  let vx = 0;
  let vy = 0;
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    cx += s.x[i];
    cy += s.y[i];
    vx += s.vx[i];
    vy += s.vy[i];
  }
  s.cx = cx / CLAY_POINTS;
  s.cy = cy / CLAY_POINTS;
  s.cvx = vx / CLAY_POINTS;
  s.cvy = vy / CLAY_POINTS;
}

function signedArea(s: ClaySim) {
  'worklet';
  let a = 0;
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    const j = (i + 1) % CLAY_POINTS;
    a += s.x[i] * s.y[j] - s.x[j] * s.y[i];
  }
  return a / 2;
}

/** `v` eased into [lo, hi]: unchanged well inside, approaching an edge without ever passing it. */
function softLimit(v: number, lo: number, hi: number, ease: number) {
  'worklet';
  if (v > hi - ease) return hi - ease + ease * Math.tanh((v - (hi - ease)) / ease);
  if (v < lo + ease) return lo + ease - ease * Math.tanh((lo + ease - v) / ease);
  return v;
}

/** How much a wall holds a point `d` pt into its cushion: 0 at the cushion's edge, 1 at the wall. */
function wallGrip(d: number) {
  'worklet';
  return Math.min(1, d / WALL_CUSHION);
}

/** The wall's stiffness `d` pt into its cushion: growing across it, stiffening past the wall. */
function wallStiffness(d: number) {
  'worklet';
  const past = Math.max(0, d - WALL_CUSHION) / WALL_GIVE;
  return WALL_K * wallGrip(d) * (1 + past * past);
}

/** A rubber band: follows a short pull one to one and never goes past `max`. */
function rubber(d: number, max: number) {
  'worklet';
  return max * (1 - Math.exp(-d / max));
}

function startDent(s: ClaySim, px: number, py: number) {
  'worklet';
  let slot = 0;
  let shallowest = Infinity;
  for (let i = 0; i < DENTS; i += 1) {
    const d = s.dents[i];
    if (!d.live) {
      slot = i;
      shallowest = -1;
      break;
    }
    if (d.depth < shallowest) {
      shallowest = d.depth;
      slot = i;
    }
  }
  const d = s.dents[slot];
  d.live = true;
  d.x = px - s.cx;
  d.y = py - s.cy;
  d.depth = 0;
  d.ridge = 0;
  d.growing = true;
  d.since = 0;
  // A deterministic seed from where it landed.
  d.seed = Math.abs(Math.sin(px * 12.9898 + py * 78.233) * 43758.5453) % 1;
  return slot;
}

function stopDent(s: ClaySim, slot: number) {
  'worklet';
  if (slot < 0) return;
  const d = s.dents[slot];
  d.growing = false;
  d.since = 0;
  // A touch too short to leave a mark leaves none.
  if (d.depth < 0.06) d.live = false;
}

function fingerOf(s: ClaySim, id: number) {
  'worklet';
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down && s.fingers[i].id === id) return s.fingers[i];
  return null;
}

function downCount(s: ClaySim) {
  'worklet';
  let n = 0;
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down) n += 1;
  return n;
}

function beginPinch(s: ClaySim) {
  'worklet';
  const a = s.fingers[0];
  const b = s.fingers[1];
  if (s.mode === 1 || s.mode === 2) {
    for (let i = 0; i < s.fingers.length; i += 1) stopDent(s, s.fingers[i].dent);
  }
  s.mode = 3;
  s.grab = -1;
  s.pinchFrom = Math.max(20, Math.hypot(b.x - a.x, b.y - a.y));
  s.pinchScale = 1;
}

/** A finger lands. `scripted` marks autoPlay's own fingers. */
export function clayTouchDown(s: ClaySim, id: number, px: number, py: number, scripted: boolean, tapColour: number) {
  'worklet';
  let f: ClayFinger | null = null;
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
  f.tapColour = tapColour;
  f.x = px;
  f.y = py;
  f.startX = px;
  f.startY = py;
  f.startT = s.time;
  f.lastX = px;
  f.lastY = py;
  f.lastT = s.time;
  f.vx = 0;
  f.vy = 0;
  f.moved = false;
  f.dent = -1;
  s.flying = false;
  if (downCount(s) === 2) {
    // Re-order so the two fingers of a pinch are 0 and 1.
    beginPinch(s);
    return;
  }
  s.mode = 1;
  s.grab = -1;
  if (clayContains(s, px, py)) {
    f.dent = startDent(s, px, py);
    // The side of the body the finger is on, as an angle, for the spread.
    s.pressSide = Math.atan2(py - s.cy, px - s.cx);
    s.events |= EVENT_PRESS;
    s.eventsByUser = !scripted;
  }
}

/** The ring patch a drag takes hold of, or -1 for none. */
function pickGrab(s: ClaySim, f: ClayFinger) {
  'worklet';
  const near = nearestPoint(s, f.startX, f.startY);
  if (near.distance < GRAB_REACH) return near.index;
  if (!clayContains(s, f.startX, f.startY)) return -1;
  // From inside, the side the finger pulls toward.
  const dx = f.x - f.startX;
  const dy = f.y - f.startY;
  const len = Math.hypot(dx, dy) || 1;
  let best = -1;
  let bestDot = -Infinity;
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    const ox = s.x[i] - s.cx;
    const oy = s.y[i] - s.cy;
    const dot = (ox * dx + oy * dy) / ((Math.hypot(ox, oy) || 1) * len);
    if (dot > bestDot) {
      bestDot = dot;
      best = i;
    }
  }
  return best;
}

export function clayTouchMove(s: ClaySim, id: number, px: number, py: number) {
  'worklet';
  const f = fingerOf(s, id);
  if (!f) return;
  f.x = px;
  f.y = py;
  // The finger's speed, smoothed over the moves that span at least 4 ms.
  const dt = s.time - f.lastT;
  if (dt > 0.004) {
    f.vx = f.vx * 0.35 + ((px - f.lastX) / dt) * 0.65;
    f.vy = f.vy * 0.35 + ((py - f.lastY) / dt) * 0.65;
    f.lastX = px;
    f.lastY = py;
    f.lastT = s.time;
  }
  if (!f.moved && Math.hypot(px - f.startX, py - f.startY) > MOVE_SLOP) {
    f.moved = true;
    if (s.mode === 1) {
      stopDent(s, f.dent);
      s.mode = 2;
      s.grab = pickGrab(s, f);
      for (let i = 0; i < CLAY_POINTS; i += 1) {
        s.grabFromX[i] = s.x[i];
        s.grabFromY[i] = s.y[i];
      }
    }
  }
}

/** Starts the colour change to palette entry `to`, spreading from (px, py). */
export function clayRecolour(s: ClaySim, to: number, px: number, py: number) {
  'worklet';
  if (to === s.colourTo && s.recolour >= 1) return;
  // A change caught half way finishes at once, so the new one starts from a single colour.
  s.colourFrom = s.recolour < 0.5 ? s.colourFrom : s.colourTo;
  s.colourTo = to;
  s.recolour = 0;
  s.recolourX = px - s.cx;
  s.recolourY = py - s.cy;
}

/** A pinch leaves PINCH_SET of its squeeze in the clay, which then creeps back round. */
function setPinch(s: ClaySim) {
  'worklet';
  const sa = s.pinchScale;
  const sb = 1 / Math.sqrt(sa);
  const ux = s.pinchUx;
  const uy = s.pinchUy;
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    const ang = (i / CLAY_POINTS) * Math.PI * 2;
    const ox = Math.cos(ang);
    const oy = Math.sin(ang);
    const along = (ox * ux + oy * uy) * sa;
    const across = (-ox * uy + oy * ux) * sb;
    const squeezed = s.rest[i] * Math.hypot(along, across);
    s.rest[i] += (squeezed - s.rest[i]) * PINCH_SET;
  }
}

/** A finger lifts. Ends a press, a tap, a drag (maybe a throw) or a pinch. */
export function clayTouchUp(s: ClaySim, id: number, cancelled: boolean) {
  'worklet';
  const f = fingerOf(s, id);
  if (!f) return;
  f.down = false;
  stopDent(s, f.dent);
  f.dent = -1;
  // A finger that stopped before it lifted throws nothing.
  const still = s.time - f.lastT > 0.08;
  const vx = still ? 0 : f.vx;
  const vy = still ? 0 : f.vy;
  if (s.mode === 3) {
    // Once a pinch has started, the clay waits for every finger to lift.
    if (downCount(s) === 0) {
      setPinch(s);
      s.mode = 0;
      s.events |= EVENT_RELEASE;
      s.eventsByUser = !f.scripted;
    }
    return;
  }
  if (cancelled) {
    s.mode = 0;
    s.grab = -1;
    return;
  }
  if (s.mode === 1 && !f.moved && s.time - f.startT < TAP_TIME) {
    const next = f.tapColour >= 0 ? f.tapColour : (s.colourTo + 1) % s.paletteSize;
    clayRecolour(s, next, f.x, f.y);
    s.events |= EVENT_RECOLOUR;
    s.eventsByUser = !f.scripted;
  }
  if (s.mode === 2) {
    const speed = Math.hypot(vx, vy);
    if (s.grab >= 0 && speed > FLICK_SPEED && !s.calm) {
      const k = Math.min(FLICK_MAX, speed * FLICK_SHARE) / speed;
      // The pulled lump lets go of most of its own speed, so the body flies as one piece.
      centroid(s);
      for (let i = 0; i < CLAY_POINTS; i += 1) {
        s.vx[i] = s.cvx + (s.vx[i] - s.cvx) * THROW_KEEP + vx * k;
        s.vy[i] = s.cvy + (s.vy[i] - s.cvy) * THROW_KEEP + vy * k;
      }
      s.flying = true;
      s.throwByUser = !f.scripted;
    }
    if (s.grab >= 0) {
      s.events |= EVENT_RELEASE;
      s.eventsByUser = !f.scripted;
    }
  }
  s.mode = 0;
  s.grab = -1;
}

/** How many real (not scripted) fingers are down. */
export function clayUserFingers(s: ClaySim) {
  'worklet';
  let n = 0;
  for (let i = 0; i < s.fingers.length; i += 1) if (s.fingers[i].down && !s.fingers[i].scripted) n += 1;
  return n;
}

/** Lifts every finger without a throw or a tap, e.g. when a real touch takes over from autoPlay. */
export function clayCancelScripted(s: ClaySim) {
  'worklet';
  for (let i = 0; i < s.fingers.length; i += 1) {
    const f = s.fingers[i];
    if (f.down && f.scripted) clayTouchUp(s, f.id, true);
  }
}

/** One fixed substep of the body. */
function substep(s: ClaySim, h: number) {
  'worklet';
  const n = CLAY_POINTS;
  centroid(s);
  const area = signedArea(s);
  const restArea = s.baseArea + s.spreadArea;
  const pressure = PRESSURE_K * (restArea / Math.max(area, restArea * 0.2) - 1);
  const stiff = s.mode === 3 ? PINCH_K : SHAPE_K;

  // The pinch as a stretch along its axis and the inverse squeeze across it.
  let sa = 1;
  let sb = 1;
  let ux = 1;
  let uy = 0;
  if (s.mode === 3) {
    const a = s.fingers[0];
    const b = s.fingers[1];
    const span = Math.hypot(b.x - a.x, b.y - a.y);
    const raw = span / s.pinchFrom;
    // Past its limits the squeeze follows at a quarter of the pull.
    sa = raw < PINCH_MIN ? PINCH_MIN - (PINCH_MIN - raw) * 0.25 : raw > PINCH_MAX ? PINCH_MAX + (raw - PINCH_MAX) * 0.25 : raw;
    sb = 1 / Math.sqrt(sa);
    ux = (b.x - a.x) / (span || 1);
    uy = (b.y - a.y) / (span || 1);
    s.pinchScale = sa;
    s.pinchUx = ux;
    s.pinchUy = uy;
  }

  for (let i = 0; i < n; i += 1) {
    const ang = (i / n) * Math.PI * 2;
    let ox = Math.cos(ang) * s.rest[i];
    let oy = Math.sin(ang) * s.rest[i];
    if (s.mode === 3) {
      const along = ox * ux + oy * uy;
      const across = -ox * uy + oy * ux;
      ox = ux * along * sa - uy * across * sb;
      oy = uy * along * sa + ux * across * sb;
    }
    s.tx[i] = s.cx + ox;
    s.ty[i] = s.cy + oy;
    let ax = (s.cx + ox - s.x[i]) * stiff;
    let ay = (s.cy + oy - s.y[i]) * stiff;
    // Pressure along the outward normal of the two edges that meet here.
    const p = (i + 1) % n;
    const m = (i + n - 1) % n;
    ax += (pressure * (s.y[p] - s.y[m])) / (2 * s.edge0);
    ay += (pressure * -(s.x[p] - s.x[m])) / (2 * s.edge0);
    // Internal and air damping, and the pull home.
    const inner = s.calm ? INNER_DAMP * 3 : INNER_DAMP;
    ax += -(s.vx[i] - s.cvx) * inner - s.vx[i] * AIR_DAMP + (s.homeX - s.cx) * HOME_K - s.cvx * HOME_DAMP;
    ay += -(s.vy[i] - s.cvy) * inner - s.vy[i] * AIR_DAMP + (s.homeY - s.cy) * HOME_K - s.cvy * HOME_DAMP;
    s.ax[i] = ax;
    s.ay[i] = ay;
  }
  // Bending: each point is pulled to keep the rest shape's bend with its neighbours, which irons
  // out short ripples along the rim without rounding off the shape.
  for (let i = 0; i < n; i += 1) {
    const p = (i + 1) % n;
    const m = (i + n - 1) % n;
    s.ax[i] += ((s.x[p] + s.x[m] - 2 * s.x[i]) - (s.tx[p] + s.tx[m] - 2 * s.tx[i])) * BEND_K;
    s.ay[i] += ((s.y[p] + s.y[m] - 2 * s.y[i]) - (s.ty[p] + s.ty[m] - 2 * s.ty[i])) * BEND_K;
  }
  // Edge springs.
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    const dx = s.x[j] - s.x[i];
    const dy = s.y[j] - s.y[i];
    const len = Math.hypot(dx, dy) || 0.0001;
    const restLen = (s.edge0 * (s.rest[i] + s.rest[j])) / (2 * s.r0);
    const ex = dx / len;
    const ey = dy / len;
    const relV = (s.vx[j] - s.vx[i]) * ex + (s.vy[j] - s.vy[i]) * ey;
    const f = (len - restLen) * EDGE_K + relV * EDGE_DAMP;
    s.ax[i] += ex * f;
    s.ay[i] += ey * f;
    s.ax[j] -= ex * f;
    s.ay[j] -= ey * f;
  }
  // A dragged patch rides the finger, rubber-banded.
  if (s.mode === 2 && s.grab >= 0) {
    let f = s.fingers[0].down ? s.fingers[0] : s.fingers[1];
    if (!f.down) f = s.fingers[0];
    const dx = f.x - f.startX;
    const dy = f.y - f.startY;
    const len = Math.hypot(dx, dy);
    const k = len > 0 ? rubber(len, PULL_MAX) / len : 0;
    for (let o = -GRAB_SPAN; o <= GRAB_SPAN; o += 1) {
      const i = wrap(s.grab + o, n);
      const w = Math.exp(-(o * o) / (2 * GRAB_SIGMA * GRAB_SIGMA));
      const tx = softLimit(s.grabFromX[i] + dx * k, s.left + WALL_ROOM, s.right - WALL_ROOM, WALL_ROOM);
      const ty = softLimit(s.grabFromY[i] + dy * k, s.top + WALL_ROOM, s.bottom - WALL_ROOM, WALL_ROOM);
      s.ax[i] += w * ((tx - s.x[i]) * GRAB_K - s.vx[i] * GRAB_DAMP);
      s.ay[i] += w * ((ty - s.y[i]) * GRAB_K - s.vy[i] * GRAB_DAMP);
    }
  }
  // Soft walls: px, py are how deep a point is into a wall's cushion, signed the way it is pushed.
  let hit = 0;
  const near = WALL_CUSHION;
  for (let i = 0; i < n; i += 1) {
    const px = s.x[i] < s.left + near ? s.left + near - s.x[i] : s.x[i] > s.right - near ? s.right - near - s.x[i] : 0;
    const py = s.y[i] < s.top + near ? s.top + near - s.y[i] : s.y[i] > s.bottom - near ? s.bottom - near - s.y[i] : 0;
    if (px !== 0) {
      hit = Math.max(hit, -Math.sign(px) * s.vx[i]);
      const grip = wallGrip(Math.abs(px));
      s.ax[i] += px * wallStiffness(Math.abs(px)) - (Math.sign(px) * s.vx[i] < 0 ? s.vx[i] * WALL_DAMP * grip : 0);
      s.ay[i] -= s.vy[i] * WALL_GRIP * grip;
    }
    if (py !== 0) {
      hit = Math.max(hit, -Math.sign(py) * s.vy[i]);
      const grip = wallGrip(Math.abs(py));
      s.ay[i] += py * wallStiffness(Math.abs(py)) - (Math.sign(py) * s.vy[i] < 0 ? s.vy[i] * WALL_DAMP * grip : 0);
      s.ax[i] -= s.vx[i] * WALL_GRIP * grip;
    }
  }
  // A finger that landed outside the clay pushes the rim in on a soft contact spring.
  if (s.mode === 1) {
    for (let k = 0; k < s.fingers.length; k += 1) {
      const f = s.fingers[k];
      if (!f.down || f.dent >= 0) continue;
      for (let i = 0; i < n; i += 1) {
        const dx = s.x[i] - f.x;
        const dy = s.y[i] - f.y;
        const d = Math.hypot(dx, dy);
        if (d < FINGER_R && d > 0.0001) {
          const vn = (s.vx[i] * dx + s.vy[i] * dy) / d;
          const push = (FINGER_R - d) * CONTACT_K - (vn < 0 ? vn * CONTACT_DAMP : 0);
          s.ax[i] += (push * dx) / d;
          s.ay[i] += (push * dy) / d;
        }
      }
    }
  }
  // A dent near the rim notches it: the rim points it reaches are drawn in toward the centre.
  for (let k = 0; k < DENTS; k += 1) {
    const dent = s.dents[k];
    if (!dent.live || dent.depth < 0.02) continue;
    const reach = Math.hypot(dent.x, dent.y);
    const facing = Math.atan2(dent.y, dent.x);
    const d = dent.depth * dent.depth * (3 - 2 * dent.depth);
    for (let i = 0; i < n; i += 1) {
      const ang = (i / n) * Math.PI * 2;
      let da = ang - facing;
      da -= Math.round(da / (Math.PI * 2)) * Math.PI * 2;
      const along = (da * s.rest[i]) / DENT_RADIUS;
      if (along > 2.6 || along < -2.6) continue;
      const over = clamp((reach + DENT_RADIUS * 0.9 - s.rest[i]) / (DENT_RADIUS * 0.9), 0, 1);
      if (over <= 0) continue;
      const pull = NOTCH * d * over * Math.exp(-along * along) * NOTCH_K;
      s.ax[i] -= Math.cos(ang) * pull;
      s.ay[i] -= Math.sin(ang) * pull;
    }
  }
  // Integrate (semi-implicit Euler).
  for (let i = 0; i < n; i += 1) {
    s.vx[i] += s.ax[i] * h;
    s.vy[i] += s.ay[i] * h;
    s.x[i] += s.vx[i] * h;
    s.y[i] += s.vy[i] * h;
  }
  // The far safety rail, for a point thrown wildly; the soft walls do the real work.
  for (let i = 0; i < n; i += 1) {
    s.x[i] = clamp(s.x[i], s.left - WALL_RAIL, s.right + WALL_RAIL);
    s.y[i] = clamp(s.y[i], s.top - WALL_RAIL, s.bottom + WALL_RAIL);
  }
  if (s.flying && hit > THUD_SPEED && s.time - s.thudAt > 0.4) {
    s.thudAt = s.time;
    s.flying = false;
    s.events |= EVENT_THUD;
    s.eventsByUser = s.throwByUser;
  }

  // Plasticity: rest radii yield to big pushes, creep back round and smooth along the ring.
  const next = s.ax;
  for (let i = 0; i < n; i += 1) {
    const ang = (i / n) * Math.PI * 2;
    const d = (s.x[i] - s.cx) * Math.cos(ang) + (s.y[i] - s.cy) * Math.sin(ang);
    const e = d - s.rest[i];
    let r = s.rest[i];
    // A pinch holds the clay to its own shape; what it leaves behind is set when it lets go.
    if (s.mode === 3) {
      next[i] = r;
      continue;
    }
    if (e > YIELD) r += (e - YIELD) * YIELD_RATE * h;
    else if (e < -YIELD) r += (e + YIELD) * YIELD_RATE * h;
    r += (s.r0 - r) * RECOVER_RATE * h;
    r += (s.rest[(i + 1) % n] + s.rest[(i + n - 1) % n] - 2 * s.rest[i]) * SMOOTH_RATE * h;
    next[i] = r;
  }
  // A press spreads the clay, most on the finger's side, and the volume with it.
  s.spreadArea -= s.spreadArea * RECOVER_RATE * h;
  const wedge = Math.sin((2 * Math.PI) / n);
  if (s.mode === 1) {
    for (let k = 0; k < s.fingers.length; k += 1) {
      const f = s.fingers[k];
      if (!f.down || f.dent < 0) continue;
      const dent = s.dents[f.dent];
      if (!dent.growing) continue;
      const rate = (1 - dent.depth) * DENT_GROW * h;
      const off = Math.hypot(dent.x, dent.y) / s.r0;
      for (let i = 0; i < n; i += 1) {
        const ang = (i / n) * Math.PI * 2;
        const facing = Math.max(0, Math.cos(ang - s.pressSide));
        const grow = SPREAD * rate * (0.35 + 0.65 * facing * facing * off);
        next[i] += grow;
        s.spreadArea += s.rest[i] * grow * wedge;
      }
    }
  }
  for (let i = 0; i < n; i += 1) s.rest[i] = clamp(next[i], s.r0 * 0.55, s.r0 * 1.7);
}

/** Lays the clay back down round and still at home, keeping its colour. */
function heal(s: ClaySim) {
  'worklet';
  for (let i = 0; i < CLAY_POINTS; i += 1) {
    const a = (i / CLAY_POINTS) * Math.PI * 2;
    s.x[i] = s.homeX + Math.cos(a) * s.r0;
    s.y[i] = s.homeY + Math.sin(a) * s.r0;
    s.vx[i] = 0;
    s.vy[i] = 0;
    s.rest[i] = s.r0;
  }
  s.spreadArea = 0;
  s.acc = 0;
  s.mode = 0;
  s.grab = -1;
  s.flying = false;
  for (let i = 0; i < s.fingers.length; i += 1) s.fingers[i].down = false;
  for (let i = 0; i < DENTS; i += 1) s.dents[i].live = false;
  centroid(s);
}

/** Advances dents, the colour change and the body by one frame of `dt` seconds. */
export function clayStep(s: ClaySim, frameDt: number) {
  'worklet';
  const dt = Math.min(frameDt, MAX_FRAME);
  s.time += dt;
  for (let i = 0; i < DENTS; i += 1) {
    const d = s.dents[i];
    if (!d.live) continue;
    if (d.growing) {
      d.depth += (1 - d.depth) * Math.min(1, DENT_GROW * dt);
      d.ridge = d.depth;
    } else {
      d.since += dt;
      d.ridge = Math.max(0, d.ridge - dt / RIDGE_FADE);
      if (d.since > DENT_HOLD) d.depth = Math.max(0, d.depth - dt / DENT_RECOVER);
      if (d.depth <= 0) d.live = false;
    }
  }
  if (s.recolour < 1) {
    s.recolour = Math.min(1, s.recolour + dt / RECOLOUR_TIME);
    if (s.recolour >= 1) s.colourFrom = s.colourTo;
  }
  s.acc += dt;
  let steps = 0;
  while (s.acc >= SUBSTEP && steps < 10) {
    substep(s, SUBSTEP);
    s.acc -= SUBSTEP;
    steps += 1;
  }
  centroid(s);
  // A guard: if anything ever went non-finite, the clay is laid back down round at home.
  if (!Number.isFinite(s.cx) || !Number.isFinite(s.cy) || !Number.isFinite(s.cvx) || !Number.isFinite(s.cvy)) heal(s);
}
