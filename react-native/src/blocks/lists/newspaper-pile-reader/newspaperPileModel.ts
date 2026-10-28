/*
 The model of Newspaper Pile Reader (a port of NewspaperPileReaderModel.swift and the driver in
 NewspaperPileReader.swift): the six papers, the geometry, the curves and the frame, all pure worklets
 of the loop time (or of the touch state's times).
*/

export type Paper = {
  masthead: string;
  /** 0 tabloid, 1 caps broadsheet, 2 blackletter photo right, 3 blackletter photo left, 4 sans, 5 serif. */
  style: number;
  headline: string;
  deck: string;
  kicker: string;
  title: string;
  summary: string;
  tone: number;
};

export const LOOP = 9.0;
export const PAPER_W = 340;
export const PAPER_H = 228;
export const PILE_SCALE = 0.86;
export const PAGE_STRIDE = 170 + 14 + 0.9 * 170;
const JX = [-5, 7, -8, 4, -3, 6];
const JY = [0, 4, -3, 5, -2, 0];
const PILE_ROT = [-2.2, 1.6, -3.0, 2.4, -1.4, 2.0];

// Loop beats (s).
const RIFFLE_AT = 1.0;
const OPEN_AT = 2.6;
const CLOSE_AT = 6.0;
const IDLE_BEFORE_BACK = 2.0;
const NONE = -1e9;

export const PAPERS: Paper[] = [
  {
    masthead: 'Evening Lantern',
    style: 0,
    headline: 'SIGNING OFF',
    deck: 'Keeper of Gull Point, 41 years',
    kicker: 'EVENING LANTERN · 4 MIN READ',
    title: 'Lighthouse keeper signs off after 41 years',
    summary: 'Ada Morrow climbed the 212 steps of Gull Point every night since 1985. On Saturday the light goes automatic.',
    tone: 0xf3f1ea,
  },
  {
    masthead: 'THE QUAYSIDE JOURNAL.',
    style: 1,
    headline: 'Port Cities Bet on Electric Ferries',
    deck: 'Spring Fares Fall 12%',
    kicker: 'THE QUAYSIDE JOURNAL · 6 MIN READ',
    title: 'Port cities bet on electric ferries',
    summary: 'Three harbors retire their diesel fleets by 2029. Fares fall 12% this spring as the first quiet crossings begin.',
    tone: 0xefece4,
  },
  {
    masthead: 'The Larkmoor Herald',
    style: 2,
    headline: 'Night Market Returns to the Old Docks',
    deck: '',
    kicker: 'THE LARKMOOR HERALD · 3 MIN READ',
    title: 'Night market returns to the old docks',
    summary: 'Forty stalls, a string of lights and a brass band: the Friday market is back after six years, with late ferries home.',
    tone: 0xf2efe6,
  },
  {
    masthead: 'The Fernhill Post',
    style: 3,
    headline: 'Rail Line Reopens After Storm Repairs',
    deck: '',
    kicker: 'THE FERNHILL POST · 5 MIN READ',
    title: 'Rail line reopens after storm repairs',
    summary: 'Trains run again between Fernhill and Copper Bay after eleven weeks of work on the washed-out embankment.',
    tone: 0xeeede8,
  },
  {
    masthead: 'Saltday',
    style: 4,
    headline: 'Tides of Change on the North Shore',
    deck: '',
    kicker: 'SALTDAY · 7 MIN READ',
    title: 'Tides of change on the north shore',
    summary: 'A new seawall, a wider beach and a boardwalk that floats: how one coastal town plans for the next fifty winters.',
    tone: 0xf1eee5,
  },
  {
    masthead: 'Orrin Bay Gazette',
    style: 5,
    headline: 'Gardens Bloom a Month Early',
    deck: '',
    kicker: 'ORRIN BAY GAZETTE · 2 MIN READ',
    title: 'Gardens bloom a month early',
    summary: 'A warm March has the magnolias out ahead of the festival, so the botanic garden opens its gates this weekend.',
    tone: 0xedeae1,
  },
];
const N = 6;

// Geometry (pt)

export type Geo = {
  w: number;
  h: number;
  top: number;
  cx: number;
  pileTop: number;
  barY: number;
  gap: number;
  openY: number;
  paperBottom: number;
  captionTop: number;
  dashY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const pileTop = top + 96;
  const barY = h - Math.max(bottom, 16) - 30;
  const openY = top + 222;
  const paperBottom = openY + 114;
  const captionTop = paperBottom + 30;
  return {
    w,
    h,
    top,
    cx: w / 2,
    pileTop,
    barY,
    gap: (barY - 4 - pileTop - 98) / 5,
    openY,
    paperBottom,
    captionTop,
    dashY: Math.min(captionTop + 200, barY - 70),
  };
}

export function pileX(g: Geo, i: number) {
  'worklet';
  return g.cx + JX[i];
}

export function pileY(g: Geo, i: number) {
  'worklet';
  return g.pileTop + 98 + i * g.gap + JY[i];
}

// Curves

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

function easeOutCubic(x: number) {
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

/** A damped spring as a curve: ≈ 3.5 % overshoot at x ≈ 0.42, exactly 1 from x = 1. */
export function spr(x: number) {
  'worklet';
  return x >= 1 ? 1 : 1 - Math.exp(-8 * x) * Math.cos(7.5 * x);
}

/** A riffle pulse: out, back, a small overshoot, 0 at both ends. */
function rif(x: number) {
  'worklet';
  return x <= 0 || x >= 1 ? 0 : (Math.sin(1.25 * Math.PI * x) * Math.sqrt(1 - x)) / 0.8;
}

export function hash(n: number) {
  'worklet';
  const v = Math.sin(n * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

// State and frame

/** Everything that drives a frame, as times and values (null = not set). */
export type State = {
  openAt: number | null;
  closeAt: number | null;
  sel: number;
  closeSel: number;
  pageFrom: number;
  pageTo: number;
  pageAt: number;
  pageLive: number | null;
  dragLive: number | null;
  dragFrom: number;
  dragAt: number;
  closeDrag: number;
  riffleOn: boolean;
  riffleDx: number;
  riffleY: number;
  riffleVx: number;
  riffleFromDx: number[];
  riffleFromRot: number[];
  riffleFromLift: number[];
  riffleAt: number;
  autoRiffle: number | null;
  reduced: boolean;
};

export function makeState(): State {
  'worklet';
  return {
    openAt: null,
    closeAt: null,
    sel: 1,
    closeSel: 1,
    pageFrom: 1,
    pageTo: 1,
    pageAt: NONE,
    pageLive: null,
    dragLive: null,
    dragFrom: 0,
    dragAt: NONE,
    closeDrag: 0,
    riffleOn: false,
    riffleDx: 0,
    riffleY: 0,
    riffleVx: 0,
    riffleFromDx: [0, 0, 0, 0, 0, 0],
    riffleFromRot: [0, 0, 0, 0, 0, 0],
    riffleFromLift: [0, 0, 0, 0, 0, 0],
    riffleAt: NONE,
    autoRiffle: null,
    reduced: false,
  };
}

/** Where one paper is drawn: centre, scale (1 = open size 340 × 228), rotation (degrees) and lift. */
export type Sheet = { x: number; y: number; scale: number; rot: number; lift: number };

export type Frame = {
  sheets: Sheet[];
  raised: number;
  raisedAlpha: number;
  papersAlpha: number;
  caption: number;
  rows: number[];
  rise: number[];
  page: number;
  toggle: number;
};

export function emptyFrame(): Frame {
  'worklet';
  return {
    sheets: [],
    raised: -1,
    raisedAlpha: 0,
    papersAlpha: 1,
    caption: 1,
    rows: [0, 0, 0, 0],
    rise: [0, 0, 0, 0],
    page: 1,
    toggle: 0,
  };
}

function autoState(reduced: boolean): State {
  'worklet';
  const s = makeState();
  s.autoRiffle = RIFFLE_AT;
  s.openAt = OPEN_AT;
  s.closeAt = CLOSE_AT;
  s.reduced = reduced;
  return s;
}

function pageAt(s: State, t: number) {
  'worklet';
  if (s.pageLive !== null) return s.pageLive;
  return lerp(s.pageFrom, s.pageTo, spr(seg(t, s.pageAt, s.pageAt + 0.5)));
}

function openSpring(s: State, t: number, i: number) {
  'worklet';
  if (s.openAt === null) return 0;
  const a = s.openAt;
  if (s.reduced) return t >= a + 0.2 ? 1 : 0;
  const lead = i === s.sel;
  const d = lead ? 0 : 0.06 + 0.045 * Math.abs(i - s.sel);
  const start = a + 0.1 + d;
  return spr(seg(t, start, start + (lead ? 0.75 : 0.6)));
}

function closeSpring(s: State, t: number, i: number) {
  'worklet';
  if (s.closeAt === null) return 0;
  const b = s.closeAt;
  if (s.reduced) return t >= b + 0.2 ? 1 : 0;
  const lead = i === s.closeSel;
  const start = b + (lead ? 0.05 : 0.15 + 0.045 * Math.abs(i - s.closeSel));
  return spr(seg(t, start, start + (lead ? 0.75 : 0.65)));
}

/** 0 = in the pile, 1 = in the reader (may overshoot either way). */
function morph(s: State, t: number, i: number) {
  'worklet';
  return openSpring(s, t, i) * (1 - closeSpring(s, t, i));
}

function dragOffset(s: State, t: number) {
  'worklet';
  if (s.dragLive !== null) return s.dragLive;
  if (s.closeAt !== null) return s.closeDrag * (1 - clamp01(closeSpring(s, t, s.closeSel)));
  return s.dragFrom * (1 - spr(seg(t, s.dragAt, s.dragAt + 0.45)));
}

function riffleShift(dx: number) {
  'worklet';
  return (dx * 0.55) / (1 + Math.abs(dx) / 240);
}

type Riffle = { dx: number; rot: number; lift: number };

/** The finger's part of the riffle: papers near the finger follow it most. */
function fingerRiffle(s: State, i: number, g: Geo): Riffle {
  'worklet';
  const w = Math.exp(-Math.pow((pileY(g, i) - s.riffleY) / 110, 2));
  const shift = riffleShift(s.riffleDx);
  return {
    dx: shift * w,
    rot: (shift * 0.1 + Math.min(Math.max(s.riffleVx * 0.0015, -4), 4)) * w,
    lift: 0.35 * Math.min(1, Math.abs(shift) / 22) * w,
  };
}

/** Sideways shift, extra rotation and lift of paper i from the riffle (loop wave, finger or release). */
function riffle(s: State, t: number, i: number, g: Geo): Riffle {
  'worklet';
  let dx = 0;
  let rot = 0;
  let lift = 0;
  if (s.autoRiffle !== null && !s.reduced) {
    const a = s.autoRiffle;
    const r = rif(seg(t, a + 0.07 * i, a + 0.07 * i + 0.6));
    dx += 22 * r;
    rot += 3 * r;
    lift += 0.35 * Math.max(r, 0);
  }
  if (s.riffleOn) {
    const f = fingerRiffle(s, i, g);
    dx += f.dx;
    rot += f.rot;
    lift += f.lift;
  } else {
    const k = 1 - spr(seg(t, s.riffleAt, s.riffleAt + 0.6));
    dx += s.riffleFromDx[i] * k;
    rot += s.riffleFromRot[i] * k;
    lift += s.riffleFromLift[i] * k;
  }
  return { dx, rot, lift };
}

export function makeFrame(s: State, t: number, g: Geo): Frame {
  'worklet';
  const f = emptyFrame();
  const p = pageAt(s, t);
  const rp = Math.min(Math.max(Math.round(p), 0), N - 1);
  const lead = s.closeAt !== null ? s.closeSel : s.sel;
  const drag = dragOffset(s, t);
  f.page = p;
  f.caption = rp;
  for (let i = 0; i < N; i++) {
    const m = morph(s, t, i);
    const mc = clamp01(m);
    const r = riffle(s, t, i, g);
    const di = i - p;
    const d = Math.min(1, Math.abs(di));
    const near = Math.max(0, 1 - Math.abs(di));
    const sgn = di > 0 ? 1 : di < 0 ? -1 : 0;
    const ox = g.cx + di * PAGE_STRIDE;
    const oy = g.openY + 10 * d + drag * near;
    const os = 1 - 0.1 * d - (drag / 1500) * near;
    const orot = -0.6 * (1 - d) + 3 * sgn * d;
    const sh: Sheet = {
      x: lerp(pileX(g, i) + r.dx, ox, m),
      y: lerp(pileY(g, i), oy, m),
      scale: lerp(PILE_SCALE, os, m),
      rot: lerp(PILE_ROT[i] + r.rot, orot, m),
      lift: mc * near + r.lift,
    };
    if (i === lead) {
      sh.scale += 0.04 * Math.sin(Math.PI * mc);
    } else {
      sh.rot += 6 * (i > lead ? 1 : -1) * Math.sin(Math.PI * mc);
    }
    if (i === s.sel && s.openAt !== null && !s.reduced) {
      sh.scale *= 1 - 0.025 * Math.sin(Math.PI * seg(t, s.openAt, s.openAt + 0.14));
    }
    f.sheets.push(sh);
  }
  if (s.openAt !== null) {
    const a = s.openAt;
    const b = s.closeAt;
    if (s.reduced) {
      f.papersAlpha = 1 - Math.sin(Math.PI * seg(t, a, a + 0.4));
      if (b !== null) f.papersAlpha *= 1 - Math.sin(Math.PI * seg(t, b, b + 0.4));
    } else {
      f.raised = b !== null ? s.closeSel : rp;
      f.raisedAlpha = seg(t, a + 0.1, a + 0.2) * (b !== null ? 1 - seg(t, b, b + 0.12) : 1);
    }
    const out = b === null ? 0 : s.reduced ? seg(t, b, b + 0.3) : easeInCubic(seg(t, b, b + 0.2));
    const pageFade = clamp01(1 - 2 * Math.abs(p - rp));
    const dragFade = 1 - Math.min(1, Math.max(0, drag) / 120);
    for (let k = 0; k < 4; k++) {
      let e = 0;
      if (s.reduced) {
        e = seg(t, a + 0.2, a + 0.5);
      } else {
        const a0 = a + 0.45 + 0.06 * k;
        e = easeOutCubic(seg(t, a0, a0 + 0.4));
      }
      f.rows[k] = e * (1 - out) * dragFade * (k === 3 ? 1 : pageFade);
      f.rise[k] = s.reduced ? 0 : 8 * (1 - e);
    }
  }
  f.toggle = easeInOutCubic(clamp01(morph(s, t, lead)));
  return f;
}

/** The front-most paper under a point, or -1. */
function hit(f: Frame, x: number, y: number) {
  'worklet';
  for (let i = f.sheets.length - 1; i >= 0; i--) {
    const sh = f.sheets[i];
    const a = (-sh.rot * Math.PI) / 180;
    const dx = x - sh.x;
    const dy = y - sh.y;
    const lx = (dx * Math.cos(a) - dy * Math.sin(a)) / sh.scale;
    const ly = (dx * Math.sin(a) + dy * Math.cos(a)) / sh.scale;
    if (Math.abs(lx) <= PAPER_W / 2 && Math.abs(ly) <= PAPER_H / 2) return i;
  }
  return -1;
}

// The driver: plays the loop, takes over on touch and goes home 2 s after the last one.

export type Driver = {
  autoStart: number;
  manual: boolean;
  s: State;
  lastTouch: number;
  /** 0 undecided, 1 riffle, 2 page, 3 down, 4 ignored. */
  mode: number;
  touching: boolean;
  pageBase: number;
  last: Frame;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    autoStart: NONE,
    manual: false,
    s: makeState(),
    lastTouch: NONE,
    mode: 0,
    touching: false,
    pageBase: 0,
    last: emptyFrame(),
  };
}

function isOpen(d: Driver) {
  'worklet';
  return d.s.openAt !== null && d.s.closeAt === null;
}

function settled(d: Driver, now: number) {
  'worklet';
  const s = d.s;
  if (s.closeAt !== null && now - s.closeAt < 1.0) return false;
  if (now - s.riffleAt < 0.7 || now - s.pageAt < 0.6 || now - s.dragAt < 0.5) return false;
  if (s.autoRiffle !== null && now - s.autoRiffle < 1.0) return false;
  return true;
}

/** Takes over from the loop where it is, so nothing jumps. */
function capture(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (!auto || d.manual || d.autoStart === NONE) {
    d.manual = true;
    return;
  }
  d.manual = true;
  const tau = (now - d.autoStart) % LOOP;
  const shift = now - tau;
  const a = makeState();
  a.reduced = d.s.reduced;
  a.autoRiffle = RIFFLE_AT + shift;
  if (tau >= OPEN_AT) a.openAt = OPEN_AT + shift;
  if (tau >= CLOSE_AT) a.closeAt = CLOSE_AT + shift;
  d.s = a;
}

function openPaper(d: Driver, i: number, now: number) {
  'worklet';
  const keep = d.s;
  const s = makeState();
  s.reduced = keep.reduced;
  s.riffleFromDx = keep.riffleFromDx;
  s.riffleFromRot = keep.riffleFromRot;
  s.riffleFromLift = keep.riffleFromLift;
  s.riffleAt = keep.riffleAt;
  s.autoRiffle = keep.autoRiffle;
  s.openAt = now;
  s.sel = i;
  s.closeSel = i;
  s.pageFrom = i;
  s.pageTo = i;
  d.s = s;
}

function closePaper(d: Driver, now: number) {
  'worklet';
  const s = d.s;
  const p = pageAt(s, now);
  s.closeDrag = dragOffset(s, now);
  s.dragLive = null;
  s.pageLive = null;
  s.pageFrom = p;
  s.pageTo = p;
  s.closeSel = Math.min(Math.max(Math.round(p), 0), N - 1);
  s.closeAt = now;
}

/** True while something still moves after a touch. */
export function driverLive(d: Driver, now: number) {
  'worklet';
  if (d.mode !== 0) return true;
  if (now - d.lastTouch < IDLE_BEFORE_BACK + 1.5) return true;
  if (d.s.closeAt !== null && now - d.s.closeAt < 1.2) return true;
  return false;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean, g: Geo): Frame {
  'worklet';
  d.s.reduced = reduced;
  if (auto && d.manual && d.mode === 0 && now - d.lastTouch > IDLE_BEFORE_BACK) {
    if (isOpen(d)) {
      closePaper(d, now);
    } else if (settled(d, now)) {
      d.manual = false;
      d.autoStart = now;
    }
  }
  let f: Frame;
  if (auto && !d.manual) {
    if (d.autoStart === NONE) d.autoStart = now;
    const tau = (now - d.autoStart) % LOOP;
    f = makeFrame(autoState(reduced), tau, g);
  } else {
    f = makeFrame(d.s, now, g);
  }
  d.last = f;
  return f;
}

/** A finger moved. */
export function driverChange(
  d: Driver,
  startY: number,
  dx: number,
  dy: number,
  vx: number,
  now: number,
  auto: boolean,
  reduced: boolean,
) {
  'worklet';
  if (!d.touching) {
    d.touching = true;
    capture(d, now, auto);
  }
  d.lastTouch = now;
  const s = d.s;
  if (d.mode === 0) {
    if (Math.hypot(dx, dy) <= 8) return;
    if (isOpen(d)) {
      if (Math.abs(dx) > Math.abs(dy)) {
        d.mode = 2;
        d.pageBase = pageAt(s, now);
      } else if (dy > 0) {
        d.mode = 3;
      } else {
        d.mode = 4;
      }
    } else if (s.closeAt === null || settled(d, now)) {
      if (Math.abs(dx) > Math.abs(dy) && !reduced) {
        d.mode = 1;
        s.riffleOn = true;
        s.riffleY = startY;
      } else {
        d.mode = 4;
      }
    } else {
      d.mode = 4;
    }
  }
  if (d.mode === 1) {
    s.riffleDx = dx;
    s.riffleVx = vx;
  } else if (d.mode === 2) {
    const n = N - 1;
    let p = d.pageBase - dx / PAGE_STRIDE;
    if (p < 0) p = p / (1 - p * 2);
    if (p > n) p = n + (p - n) / (1 + (p - n) * 2);
    s.pageLive = p;
  } else if (d.mode === 3) {
    s.dragLive = dy > 0 ? dy / (1 + dy / 400) : 0;
  }
}

/** The finger lifted. Returns true when a haptic should play. */
export function driverEnd(
  d: Driver,
  x: number,
  y: number,
  dx: number,
  dy: number,
  vx: number,
  vy: number,
  now: number,
  auto: boolean,
  g: Geo,
) {
  'worklet';
  d.lastTouch = now;
  if (!d.touching) capture(d, now, auto);
  const mode = d.mode;
  d.mode = 0;
  d.touching = false;
  const s = d.s;
  if (mode === 1) {
    for (let i = 0; i < N; i++) {
      const r = fingerRiffle(s, i, g);
      s.riffleFromDx[i] = r.dx;
      s.riffleFromRot[i] = r.rot;
      s.riffleFromLift[i] = r.lift;
    }
    s.riffleOn = false;
    s.riffleAt = now;
    return false;
  }
  if (mode === 2) {
    const p = s.pageLive !== null ? s.pageLive : pageAt(s, now);
    const from = Math.round(d.pageBase);
    let target = Math.round(p - (vx / PAGE_STRIDE) * 0.25);
    target = Math.min(Math.max(target, from - 1), from + 1);
    target = Math.min(Math.max(target, 0), N - 1);
    s.pageLive = null;
    s.pageFrom = p;
    s.pageTo = target;
    s.pageAt = now;
    return target !== from;
  }
  if (mode === 3) {
    const dd = s.dragLive !== null ? s.dragLive : 0;
    if (dd > 90 || vy > 700) {
      closePaper(d, now);
      return true;
    }
    s.dragLive = null;
    s.dragFrom = dd;
    s.dragAt = now;
    return false;
  }
  if (mode === 4) return false;
  if (Math.hypot(dx, dy) >= 10) return false;
  if (isOpen(d)) {
    closePaper(d, now);
    return true;
  }
  if (s.closeAt !== null && now - s.closeAt < 0.9) return false;
  const i = hit(d.last, x, y);
  if (i >= 0) {
    openPaper(d, i, now);
    return true;
  }
  return false;
}

/** The accessibility action: opens the second paper, or puts the open one back. */
export function driverToggle(d: Driver, now: number, auto: boolean) {
  'worklet';
  capture(d, now, auto);
  d.lastTouch = now;
  if (isOpen(d)) closePaper(d, now);
  else openPaper(d, 1, now);
}
