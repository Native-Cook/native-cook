/*
 Prism Beam Folder — geometry, the session model and the autoPlay script (a port of
 PrismBeamFolderMotion.swift, same numbers). Every function is a worklet: the frame callback and
 the gesture run them on the UI thread. Helpers are declared above their callers.

 A session is one press of the folder: τ = seconds since the press. The folder squashes (0 – 0.2),
 a thin prism beam shoots up out of it (0.2 – 0.45) and widens into a cone (0.4 – 1.05). While the
 finger is down an item spawns at the top of the beam every 0.26 s (τᵢ = 0.65 + 0.26 i) and floats
 down through it for 2.3 s into the folder; each landing bumps the folder and ticks the badge.
 After the release and the last landing the beam narrows to a line (cs … cs + 0.45), the line falls
 into the folder (cs + 0.4 … cs + 0.65), the front closes, and the badge clears at
 cs + 1.05 … cs + 1.35 (cs = max(release, 1.05, last landing) + 0.05).

 autoPlay: LOOP = 8 s, one session pressed at t = 0.8 and held to τ = 2.8 (nine items):
   0.0 – 0.8 rest · 0.8 – 1.85 press, shoot, widen · 1.45 – 5.83 nine items pour in ·
   5.88 – 6.68 narrow, fall, close · 6.93 – 7.23 badge clears · 7.23 – 8.0 rest (frame 8 = frame 0).
*/

export const LOOP = 8;
export const REDUCED_AT = 3.8;
const AUTO_PRESS = 0.8;
const AUTO_HOLD = 2.8;
const IDLE_BEFORE_BACK = 2;
const SPAWN0 = 0.65;
const GAP = 0.26;
const FLIGHT = 2.3;
const WIDEN_END = 1.05;
export const HB_FULL = 36;
export const HT_FULL = 232;

/** Item kinds: 0 peach, 1 cherries, 2 aubergine, 3 chili. */
const KINDS = [0, 2, 1, 3, 0, 1, 2, 0, 3];
const LANES = [-0.5, 0.42, -0.08, 0.6, -0.62, 0.2, -0.3, 0.5, 0.02];
const TURNS = [-20, 35, 10, -40, 15, -10, 50, -25, 20];
const SIZES = [55, 58, 53, 53, 51, 53, 55, 53, 51];

export type Geo = {
  w: number;
  h: number;
  top: number;
  bottom: number;
  cx: number;
  folderCY: number;
  /** The foot of the beam, and its full length (to y = -40). */
  originY: number;
  length: number;
  itemStartY: number;
  itemEndY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const folderCY = Math.round(h * 0.64);
  const originY = folderCY - 34;
  return {
    w,
    h,
    top,
    bottom,
    cx: w / 2,
    folderCY,
    originY,
    length: originY + 40,
    itemStartY: top + 110,
    itemEndY: folderCY - 16,
  };
}

export type Item = { kind: number; x: number; y: number; size: number; scale: number; angle: number; alpha: number };

export type Frame = {
  hb: number;
  ht: number;
  tip: number;
  beamAlpha: number;
  glow: number;
  phase: number;
  folderSX: number;
  folderSY: number;
  open: number;
  rippleR: number;
  rippleA: number;
  items: Item[];
  count: number;
  badge: number;
  saved: number;
  header: number;
  landed: number;
  opened: boolean;
  restAt: number;
  closeStart: number;
};

/** One hold of the finger, in session seconds; `end` is Infinity while it is down. */
type Hold = { start: number; end: number };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** 0 before a, 1 after b (safe when a and b are infinite: a close that has not been scheduled). */
function seg(t: number, a: number, b: number) {
  'worklet';
  if (t <= a) return 0;
  if (t >= b) return 1;
  return (t - a) / (b - a);
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeIn(x: number) {
  'worklet';
  return x * x * x;
}

function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Ease-out with a small overshoot (about 2 %). */
function easeOutBack(x: number) {
  'worklet';
  const c1 = 0.8;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

export function halfWidth(g: Geo, y: number) {
  'worklet';
  const d = Math.max(0, g.originY - y);
  return HB_FULL + ((HT_FULL - HB_FULL) * d) / g.length;
}

export function onFolder(g: Geo, x: number, y: number) {
  'worklet';
  return Math.abs(x - g.cx) < 52 + 30 && y > g.folderCY - 50 - 30 && y < g.folderCY + 42 + 30;
}

function held(holds: Hold[], s: number) {
  'worklet';
  for (let k = 0; k < holds.length; k++) {
    if (s >= holds[k].start && s <= holds[k].end) return true;
  }
  return false;
}

function closeStart(holds: Hold[]) {
  'worklet';
  if (holds.length === 0) return Infinity;
  const last = holds[holds.length - 1];
  if (!isFinite(last.end)) return Infinity;
  let lastLand = -Infinity;
  for (let i = 0; ; i++) {
    const s = SPAWN0 + GAP * i;
    if (s > last.end) break;
    if (held(holds, s)) lastLand = s + FLIGHT;
  }
  return Math.max(last.end, WIDEN_END, lastLand) + 0.05;
}

function makeItem(i: number, p: number, g: Geo): Item {
  'worklet';
  const j = i % KINDS.length;
  const e = 0.55 * p + 0.45 * p * p * p;
  const y = g.itemStartY + (g.itemEndY - g.itemStartY) * e;
  const wob = 5 * Math.sin(2 * Math.PI * (1.1 * p + 0.37 * i));
  const x = g.cx + LANES[j] * halfWidth(g, y) * 0.88 * (1 - 0.5 * p * p * p) + wob;
  const scale = (0.6 + 0.4 * easeOut(seg(p, 0, 0.12))) * (1 - 0.5 * seg(p, 0.8, 1));
  const spin = i % 2 === 0 ? 90 : -90;
  const angle = TURNS[j] + 18 * Math.sin(2 * Math.PI * (0.6 * p + 0.21 * i)) + spin * p * p * p;
  return { kind: KINDS[j], x, y, size: SIZES[j], scale, angle, alpha: seg(p, 0, 0.1) };
}

function restFrame(phase: number): Frame {
  'worklet';
  return {
    hb: 0,
    ht: 0,
    tip: 0,
    beamAlpha: 0,
    glow: 0,
    phase,
    folderSX: 1,
    folderSY: 1,
    open: 0,
    rippleR: 0,
    rippleA: 0,
    items: [],
    count: 0,
    badge: 0,
    saved: 0,
    header: 1,
    landed: 0,
    opened: false,
    restAt: Infinity,
    closeStart: Infinity,
  };
}

/** The frame at τ seconds into a session. Pure: the same inputs give the same frame. */
export function sessionFrame(tau: number, holds: Hold[], base: number, phase: number, g: Geo): Frame {
  'worklet';
  const f = restFrame(phase);
  if (tau < 0 || holds.length === 0) return f;
  const cs = closeStart(holds);
  f.closeStart = cs;
  f.restAt = cs + 1.35;

  const shoot = easeOut(seg(tau, 0.2, 0.45));
  const widen = easeOutBack(seg(tau, 0.4, WIDEN_END));
  const narrow = easeInOut(seg(tau, cs, cs + 0.45));
  const retract = easeIn(seg(tau, cs + 0.4, cs + 0.65));
  f.hb = (4 + (HB_FULL - 4) * widen) * (1 - 0.94 * narrow);
  f.ht = (12 + (HT_FULL - 12) * widen) * (1 - 0.985 * narrow);
  f.tip = g.length * shoot * (1 - retract);
  f.beamAlpha = seg(tau, 0.2, 0.3) * (1 - seg(tau, cs + 0.5, cs + 0.65));
  f.glow = clamp01(widen) * (1 - narrow);
  f.header = 1 - 0.55 * f.glow;
  f.open = easeOut(seg(tau, 0.2, 0.5)) * (1 - easeInOut(seg(tau, cs + 0.45, cs + 0.8)));
  f.opened = tau >= 0.2;

  let hs = 0;
  for (let k = holds.length - 1; k >= 0; k--) {
    if (holds[k].start <= tau) {
      hs = holds[k].start;
      break;
    }
  }
  const x = tau - hs;
  let a = 0;
  if (x < 0.2) {
    a = easeOut(seg(x, 0, 0.2));
  } else if (x < 1.2) {
    const k = x - 0.2;
    a = Math.exp(-6 * k) * Math.cos(14 * k);
  }
  if (x < 0.5) {
    const r = x / 0.5;
    f.rippleR = 18 + 28 * easeOut(r);
    f.rippleA = 0.5 * (1 - r);
  }

  let landed = 0;
  let firstLand = -1;
  let lastLand = -1;
  let bump = 0;
  for (let i = 0; ; i++) {
    const s = SPAWN0 + GAP * i;
    if (s > tau) break;
    if (!held(holds, s)) continue;
    const land = s + FLIGHT;
    if (tau >= land) {
      landed++;
      if (firstLand < 0) firstLand = land;
      lastLand = land;
      const k = tau - land;
      if (k < 0.6) bump += 0.03 * Math.exp(-9 * k) * Math.cos(16 * k);
      continue;
    }
    f.items.push(makeItem(i, (tau - s) / FLIGHT, g));
  }
  f.landed = landed;
  f.folderSX = 1 + 0.04 * a + 0.5 * bump;
  f.folderSY = 1 - 0.07 * a - bump;

  f.count = base + landed;
  const clear = easeIn(seg(tau, cs + 1.05, cs + 1.35));
  let pop = 0;
  let shown = 0;
  if (base > 0) {
    pop = 1;
    shown = 1;
  } else if (firstLand >= 0) {
    const k = tau - firstLand;
    pop = 1 - Math.exp(-8 * k) * Math.cos(12 * k);
    shown = easeOut(seg(tau, firstLand, firstLand + 0.25));
  }
  let pulse = 0;
  if (lastLand >= 0 && (firstLand !== lastLand || base > 0)) {
    const k = tau - lastLand;
    if (k < 0.6) pulse = 0.25 * Math.exp(-9 * k) * Math.sin(14 * k);
  }
  f.badge = f.count > 0 ? Math.max(0, (pop + pulse) * (1 - clear)) : 0;
  f.saved = shown * (1 - easeOut(seg(tau, cs + 1.05, cs + 1.3)));
  return f;
}

/** The autoPlay frame at loop time t. */
export function scriptFrame(t: number, g: Geo): Frame {
  'worklet';
  return sessionFrame(t - AUTO_PRESS, [{ start: 0, end: AUTO_HOLD }], 0, t, g);
}

/** Clock state behind the frames (seconds): the autoPlay clock and the session a finger started. */
export type Sim = {
  epoch: number;
  start: number;
  holds: Hold[];
  base: number;
  holding: boolean;
  pending: boolean;
  released: number;
  lastLanded: number;
  wasOpen: boolean;
  /** Bumped on each moment that deserves a haptic (the beam opening, a landing). */
  feel: number;
};

export function makeSim(): Sim {
  return {
    epoch: NaN,
    start: NaN,
    holds: [],
    base: 0,
    holding: false,
    pending: false,
    released: NaN,
    lastLanded: 0,
    wasOpen: false,
    feel: 0,
  };
}

function begin(sim: Sim, now: number, base: number) {
  'worklet';
  sim.start = now;
  sim.holds = [{ start: 0, end: Infinity }];
  sim.base = base;
  sim.lastLanded = 0;
  sim.wasOpen = false;
}

export function simFrame(sim: Sim, now: number, g: Geo, auto: boolean): Frame {
  'worklet';
  if (isNaN(sim.epoch)) sim.epoch = now;
  if (isNaN(sim.start)) {
    const t = auto ? (now - sim.epoch) % LOOP : 0;
    return scriptFrame(t, g);
  }
  let tau = now - sim.start;
  let f = sessionFrame(tau, sim.holds, sim.base, now - sim.epoch, g);
  if (sim.pending && tau >= f.closeStart + 0.65) {
    sim.pending = false;
    begin(sim, now, 0);
    tau = 0;
    f = sessionFrame(0, sim.holds, sim.base, now - sim.epoch, g);
  }
  if (f.opened && !sim.wasOpen) sim.feel++;
  if (f.landed > sim.lastLanded) sim.feel++;
  sim.wasOpen = f.opened;
  sim.lastLanded = f.landed;
  if (!sim.holding && !sim.pending && !isNaN(sim.released) && now - sim.released >= IDLE_BEFORE_BACK && tau >= f.restAt) {
    sim.start = NaN;
    sim.epoch = now;
    return scriptFrame(0, g);
  }
  return f;
}

export function simPress(sim: Sim, now: number, g: Geo, auto: boolean) {
  'worklet';
  sim.holding = true;
  if (isNaN(sim.start)) {
    const t = auto && !isNaN(sim.epoch) ? (now - sim.epoch) % LOOP : -1;
    const tau = t - AUTO_PRESS;
    const restAt = closeStart([{ start: 0, end: AUTO_HOLD }]) + 1.35;
    if (tau >= 0 && tau < restAt) {
      // The running loop becomes the session: its virtual finger lifts now.
      sim.start = now - tau;
      sim.holds = [{ start: 0, end: Math.min(AUTO_HOLD, tau) }];
      sim.base = 0;
      const f = sessionFrame(tau, sim.holds, 0, 0, g);
      sim.lastLanded = f.landed;
      sim.wasOpen = f.opened;
    } else {
      begin(sim, now, 0);
      return;
    }
  }
  const tau = now - sim.start;
  const cs = closeStart(sim.holds);
  if (tau < cs) {
    sim.holds.push({ start: tau, end: Infinity });
  } else if (tau < cs + 0.65) {
    sim.pending = true;
  } else {
    const f = sessionFrame(tau, sim.holds, sim.base, 0, g);
    begin(sim, now, tau < cs + 1.05 ? f.count : 0);
  }
}

export function simRelease(sim: Sim, now: number) {
  'worklet';
  sim.holding = false;
  sim.pending = false;
  sim.released = now;
  const last = sim.holds[sim.holds.length - 1];
  if (last && !isNaN(sim.start) && !isFinite(last.end)) last.end = now - sim.start;
}
