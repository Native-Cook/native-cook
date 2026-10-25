/*
 Ink Drop Bloom — geometry, the autoPlay script and the touch simulation. Plain arithmetic on
 plain objects, all worklets, so the component, the offline renderer and the ports share it.

 Units: points and seconds. LOOP = 9 s:
   0.0 – 1.4   rest: clear water, the swatches are empty outlines.
   1.15 – 1.4  drop 1 (indigo) falls into the glass; it blooms 1.4 – 4.
   2.55 – 2.8  drop 2 (orchid) falls; 3.95 – 4.2 drop 3 (lagoon).
   5.6 – 6.6   peak: the plumes fill the glass, every swatch is full.
   6.35 – 6.65 the press's mesh disc comes down to the water; 6.65 – 8.05 it pushes the ink to
               the floor and the water above it is clear (ease-in-out); 8.05 – 8.55 it lifts out.
   7.0 – 8.2   the swatches drain back to outlines.
   8.55 – 9.0  rest (the frame at 9 is the frame at 0).
*/

export const LOOP = 9;
/** The poster under reduced motion: the palette at its fullest. */
export const REDUCED_AT = 6.1;
/** After the last touch the screen waits this long, then clears the water and restarts. */
export const IDLE_BEFORE_BACK = 2;
/** How long clearing back to rest takes after a touch (s). */
export const BACK_FOR = 0.9;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

/** The three inks (sRGB 0–1, at full strength), and the five swatches the palette shows. */
export const INKS: number[][] = [
  [0.17, 0.2, 0.62],
  [0.74, 0.12, 0.42],
  [0.04, 0.5, 0.52],
];
export const SWATCHES = [
  { name: 'Indigo', hex: '2D3489', color: '#2D3489', deep: '#1D2266' },
  { name: 'Orchid', hex: 'B0306E', color: '#B0306E', deep: '#7E1B4E' },
  { name: 'Lagoon', hex: '0F7C80', color: '#0F7C80', deep: '#08585C' },
  { name: 'Plum', hex: '4E2763', color: '#4E2763', deep: '#331745' },
  { name: 'Deep Sea', hex: '1C4A6B', color: '#1C4A6B', deep: '#11324B' },
];
export const PHASES = [
  'Clear water · ready to mix',
  'Dropping indigo…',
  'Dropping orchid…',
  'Dropping lagoon…',
  'Palette ready · 5 colours',
  'Filtering the glass…',
  'Clear water · ready to mix',
];

const DROP_AT = [1.4, 2.8, 4.2];
/** Where each scripted drop enters, as a share of the water's half width. */
const DROP_X = [-0.32, 0.3, 0.02];
const FALL_FOR = 0.25;
const FALL_FROM = 92;
const PRESS_IN = [6.35, 6.65];
const PRESS_PUSH = [6.65, 8.05];
const PRESS_OUT = [8.05, 8.55];
const LABEL_AT = [1.25, 2.65, 4.05, 5.5, 6.45, 8.3];
const LABEL_FOR = 0.42;

// Layout: header, glass, palette card and button, top to bottom.
export const HEADER_H = 116;
export const PALETTE_H = 140;
export const BUTTON_H = 52;

export type InkGeo = {
  w: number;
  h: number;
  cx: number;
  top: number;
  bottom: number;
  hwTop: number;
  hwBot: number;
  wall: number;
  surface: number;
  floor: number;
  paletteTop: number;
  buttonTop: number;
};

export function makeInkGeo(w: number, h: number, insetTop: number, insetBottom: number): InkGeo {
  'worklet';
  const buttonTop = h - Math.max(insetBottom, 12) - 12 - BUTTON_H;
  const paletteTop = buttonTop - 16 - PALETTE_H;
  const top = insetTop + HEADER_H + 24;
  const bottom = paletteTop - 30;
  const tall = bottom - top;
  const hwTop = Math.min(w * 0.29, 116, tall * 0.31);
  const hwBot = hwTop * 0.88;
  const wall = 6;
  return {
    w,
    h,
    cx: w / 2,
    top,
    bottom,
    hwTop,
    hwBot,
    wall,
    surface: top + Math.max(30, tall * 0.11),
    floor: bottom - 22,
    paletteTop,
    buttonTop,
  };
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
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** Everything one frame needs, in the order the shader and the views read it. */
export type InkFrame = {
  /** 4 drops × (x, y, age, seed); age < 0 = none. */
  drops: number[];
  /** Ink index of each drop slot. */
  inks: number[];
  falling: number[];
  fallInk: number;
  /** Disc y (< 0 none), rod alpha, squeeze, ink amount. */
  press: number[];
  stir: number[];
  swatches: number[];
  label: number;
  bar: number;
};

export function emptyFrame(): InkFrame {
  'worklet';
  return {
    drops: [0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0],
    inks: [0, 1, 2, 0],
    falling: [0, 0, 0, 0],
    fallInk: 0,
    press: [-1, 0, 0, 1],
    stir: [0, 0, 0, 60],
    swatches: [0, 0, 0, 0, 0],
    label: 0,
    bar: 0,
  };
}

/** The autoPlay frame at loop time t: a pure function, so frame LOOP = frame 0. */
export function scriptFrame(t: number, g: InkGeo, out: InkFrame) {
  'worklet';
  const ihw = g.hwTop - g.wall;
  const done = t >= PRESS_PUSH[1];
  for (let i = 0; i < 3; i += 1) {
    const age = t - DROP_AT[i];
    out.drops[i * 4] = g.cx + DROP_X[i] * ihw;
    out.drops[i * 4 + 1] = g.surface;
    out.drops[i * 4 + 2] = age > 0 && !done ? age : -1;
    out.drops[i * 4 + 3] = i * 0.37 + 0.11;
    out.inks[i] = i;
  }
  out.drops[14] = -1;
  // The drop in the air.
  out.falling[3] = 0;
  for (let i = 0; i < 3; i += 1) {
    const s = seg(t, DROP_AT[i] - FALL_FOR, DROP_AT[i]);
    if (s > 0 && s < 1) {
      const y0 = g.top - FALL_FROM;
      out.falling[0] = g.cx + DROP_X[i] * ihw;
      // Falling is gravity: the drop speeds up (y ∝ s²).
      out.falling[1] = y0 + (g.surface - y0) * s * s;
      out.falling[2] = 5.2;
      out.falling[3] = clamp01(s / 0.25);
      out.fallInk = i;
    }
  }
  // The press.
  let disc = -1;
  let rod = 0;
  const above = g.top - 26;
  if (t >= PRESS_IN[0] && t < PRESS_IN[1]) {
    disc = above + (g.surface - above) * easeOut(seg(t, PRESS_IN[0], PRESS_IN[1]));
    rod = easeOut(seg(t, PRESS_IN[0], PRESS_IN[0] + 0.18));
  } else if (t >= PRESS_PUSH[0] && t < PRESS_PUSH[1]) {
    disc = g.surface + (g.floor - 4 - g.surface) * easeInOut(seg(t, PRESS_PUSH[0], PRESS_PUSH[1]));
    rod = 1;
  } else if (t >= PRESS_OUT[0] && t < PRESS_OUT[1]) {
    disc = g.floor - 4 + (above - (g.floor - 4)) * easeInOut(seg(t, PRESS_OUT[0], PRESS_OUT[1]));
    rod = 1 - seg(t, PRESS_OUT[1] - 0.2, PRESS_OUT[1]);
  }
  out.press[0] = disc;
  out.press[1] = rod;
  out.press[2] = seg(t, PRESS_PUSH[0], PRESS_PUSH[1]);
  out.press[3] = 1;
  out.stir[2] = 0;
  // Swatches fill as their colour blooms, the two mixes once the colours meet; all drain together.
  const drain = 1 - easeInOut(seg(t, 7.0, 8.2));
  for (let i = 0; i < 3; i += 1) out.swatches[i] = easeOut(seg(t, DROP_AT[i] + 0.25, DROP_AT[i] + 1.35)) * drain;
  out.swatches[3] = easeOut(seg(t, 4.9, 5.8)) * drain;
  out.swatches[4] = easeOut(seg(t, 5.15, 6.05)) * drain;
  let label = 0;
  for (let i = 0; i < LABEL_AT.length; i += 1) label += easeInOut(seg(t, LABEL_AT[i], LABEL_AT[i] + LABEL_FOR));
  out.label = label;
  out.bar = easeInOut(seg(t, 1.3, 6.0)) * (1 - easeInOut(seg(t, 6.65, 8.05)));
}

/** The touch simulation (UI thread). Times in seconds of the frame clock. */
export type InkSim = {
  mode: number;
  /** Loop time (auto), the frame clock, the last touch, the back progress. */
  t: number;
  now: number;
  last: number;
  back: number;
  flow: number;
  frame: InkFrame;
  /** Values at the start of "back", eased toward rest from there. */
  from: InkFrame;
  slot: number;
  nextInk: number;
  touching: boolean;
  lastX: number;
  lastY: number;
};

export function makeSim(): InkSim {
  'worklet';
  return {
    mode: MODE_AUTO,
    t: 0,
    now: 0,
    last: 0,
    back: 0,
    flow: 0,
    frame: emptyFrame(),
    from: emptyFrame(),
    slot: 3,
    nextInk: 0,
    touching: false,
    lastX: 0,
    lastY: 0,
  };
}

function copyFrame(a: InkFrame, b: InkFrame) {
  'worklet';
  for (let i = 0; i < 16; i += 1) b.drops[i] = a.drops[i];
  for (let i = 0; i < 4; i += 1) {
    b.inks[i] = a.inks[i];
    b.press[i] = a.press[i];
    b.stir[i] = a.stir[i];
    b.falling[i] = a.falling[i];
  }
  for (let i = 0; i < 5; i += 1) b.swatches[i] = a.swatches[i];
  b.fallInk = a.fallInk;
  b.label = a.label;
  b.bar = a.bar;
}

/** A finger lands: the script stops where it is and the water is the finger's. */
export function simTouchDown(s: InkSim, x: number, y: number) {
  'worklet';
  if (s.mode !== MODE_LIVE) {
    const f = s.frame;
    // A press under way: what it already cleared stays clear, the disc goes away.
    if (f.press[0] > 0) f.press[3] = f.press[3] * (1 - f.press[2] * f.press[2]);
    f.press[0] = -1;
    f.press[1] = 0;
    f.falling[3] = 0;
    f.label = Math.round(f.label);
    if (f.label >= 5) f.label = 0;
    s.mode = MODE_LIVE;
  }
  s.touching = true;
  s.last = s.now;
  s.lastX = x;
  s.lastY = y;
  s.frame.stir[0] = x;
  s.frame.stir[1] = y;
}

/** Whether a point is inside the water (where a tap drops ink). */
export function inWater(g: InkGeo, x: number, y: number) {
  'worklet';
  return y > g.surface + 4 && y < g.floor - 4 && Math.abs(x - g.cx) < g.hwBot - g.wall - 4;
}

/** Drops ink where the finger tapped; returns the ink index used. */
export function simDrop(s: InkSim, x: number, y: number) {
  'worklet';
  const f = s.frame;
  s.slot = (s.slot + 1) % 4;
  const ink = s.nextInk;
  s.nextInk = (s.nextInk + 1) % 3;
  f.drops[s.slot * 4] = x;
  f.drops[s.slot * 4 + 1] = y;
  f.drops[s.slot * 4 + 2] = 0.001;
  f.drops[s.slot * 4 + 3] = (s.now * 0.731) % 1;
  f.inks[s.slot] = ink;
  if (f.press[3] < 0.999) {
    // Older ink that was half filtered fades with the rest: keep it, but lift the amount.
    f.press[3] = 1;
  }
  f.label = 1 + ink;
  return ink;
}

/** A drag stirs: the twist follows the finger and grows with how far it moves sideways. */
export function simStir(s: InkSim, x: number, y: number) {
  'worklet';
  const f = s.frame;
  const dx = Math.max(-28, Math.min(28, x - s.lastX));
  f.stir[0] += (x - f.stir[0]) * 0.35;
  f.stir[1] += (y - f.stir[1]) * 0.35;
  f.stir[2] = Math.max(-5, Math.min(5, f.stir[2] + dx * 0.0042));
  f.stir[3] = 74;
  s.lastX = x;
  s.lastY = y;
  s.last = s.now;
}

export function simTouchUp(s: InkSim) {
  'worklet';
  s.touching = false;
  s.last = s.now;
}

/** One frame of the simulation; returns true when the loop should start over from t = 0. */
export function simStep(s: InkSim, dt: number, g: InkGeo, auto: boolean, reduced: boolean) {
  'worklet';
  s.now += dt;
  const f = s.frame;
  if (s.mode === MODE_AUTO) {
    if (auto) s.t = (s.t + dt) % LOOP;
    s.flow = reduced ? REDUCED_AT : s.t;
    scriptFrame(reduced ? REDUCED_AT : s.t, g, f);
    return false;
  }
  s.flow += dt;
  if (s.mode === MODE_LIVE) {
    for (let i = 0; i < 4; i += 1) {
      const a = f.drops[i * 4 + 2];
      if (a >= 0) f.drops[i * 4 + 2] = Math.min(a + dt, 9);
    }
    // Swatches of the inks in the water fill; the mixes when two meet.
    for (let i = 0; i < 4; i += 1) {
      if (f.drops[i * 4 + 2] > 0.3) {
        const k = f.inks[i];
        f.swatches[k] = Math.min(1, f.swatches[k] + dt * 1.2);
      }
    }
    if (f.swatches[0] > 0.6 && f.swatches[1] > 0.6) f.swatches[3] = Math.min(1, f.swatches[3] + dt);
    if (f.swatches[1] > 0.6 && f.swatches[2] > 0.6) f.swatches[4] = Math.min(1, f.swatches[4] + dt);
    let full = 0;
    for (let i = 0; i < 5; i += 1) full += f.swatches[i] / 5;
    f.bar += (full - f.bar) * Math.min(1, dt * 4);
    if (!s.touching) f.stir[2] *= Math.exp(-dt / 3);
    if (auto && !s.touching && s.now - s.last > IDLE_BEFORE_BACK) {
      s.mode = MODE_BACK;
      s.back = 0;
      copyFrame(f, s.from);
    }
    return false;
  }
  // Back to rest: the ink fades out, the twist unwinds, the swatches drain, the label rolls home.
  s.back = Math.min(1, s.back + dt / BACK_FOR);
  const k = easeInOut(s.back);
  const a = s.from;
  f.press[3] = a.press[3] * (1 - k);
  f.stir[2] = a.stir[2] * (1 - k);
  for (let i = 0; i < 5; i += 1) f.swatches[i] = a.swatches[i] * (1 - k);
  f.bar = a.bar * (1 - k);
  const home = a.label <= 3 ? 0 : 6;
  f.label = a.label + (home - a.label) * k;
  if (s.back >= 1) {
    s.mode = MODE_AUTO;
    s.t = 0;
    s.flow = 0;
    scriptFrame(0, g, f);
    return true;
  }
  return false;
}

/** The light the water throws on the table takes the mean colour of the ink in it. */
export function inkTint(f: InkFrame) {
  'worklet';
  let r = 0;
  let gg = 0;
  let b = 0;
  let w = 0;
  for (let i = 0; i < 4; i += 1) {
    const a = f.drops[i * 4 + 2];
    if (a <= 0) continue;
    const k = Math.min(1, a / 1.5) * f.press[3] * (1 - f.press[2]);
    const c = INKS[f.inks[i]];
    r += c[0] * k;
    gg += c[1] * k;
    b += c[2] * k;
    w += k;
  }
  if (w <= 0.0001) return [1, 1, 1];
  const m = Math.min(1, w);
  return [1 - m + (r / w) * m, 1 - m + (gg / w) * m, 1 - m + (b / w) * m];
}

/** The shader's uniforms for a frame. */
export function inkUniforms(f: InkFrame, g: InkGeo, flow: number) {
  'worklet';
  const c = (i: number) => INKS[f.inks[i]];
  return {
    size: [Math.max(1, g.w), Math.max(1, g.h)],
    glass: [g.cx, g.top, g.bottom, g.hwTop],
    water: [g.surface, g.floor, g.hwBot, g.wall],
    d0: [f.drops[0], f.drops[1], f.drops[2], f.drops[3]],
    d1: [f.drops[4], f.drops[5], f.drops[6], f.drops[7]],
    d2: [f.drops[8], f.drops[9], f.drops[10], f.drops[11]],
    d3: [f.drops[12], f.drops[13], f.drops[14], f.drops[15]],
    c0: c(0),
    c1: c(1),
    c2: c(2),
    c3: c(3),
    falling: f.falling.slice(),
    fallCol: INKS[f.fallInk],
    press: f.press.slice(),
    stir: f.stir.slice(),
    flow,
    tint: inkTint(f),
  };
}
