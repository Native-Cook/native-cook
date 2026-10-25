/*
 Paint Mixer Picker — layout, the paint model and the autoPlay script.

 Paint is mixed subtractively, as a weighted average of absorbance (−ln of each sRGB channel): a
 base of white with weight 1 and each drop with weight K × amount. The bowl's shader uses the same
 weights per point (each drop is a blob that a swirl winds into a spiral and stirring spreads), and
 `mixColor` uses them with every blob spread evenly: the colour the paint will be once mixed. Yellow
 and teal over white give a soft fern green.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.4   rest: a bowl of white paint, five pots around it.
   1.4 – 1.6   the yellow pot lifts (scale 1 → 1.06, shadow grows).
   1.5 – 2.0   a yellow drop flies to the bowl (ease-in-out, arcing towards the camera), lands at
               2.0: ripple, splash, the paint swirls (1.6π, ease-out cubic over 1.4 s).
   2.6 – 2.8   the teal pot lifts; 2.75 – 3.25 its drop flies; lands at 3.25, swirl 1.2π over 1.15 s.
   3.9 – 5.4   a stirring stick comes in, circles twice (4.0 – 5.2, ease-in-out sine), leaves;
               the paint winds 3.2π and mixes to green.
   5.3 – 6.6   hold: "Morning Fern" and its code; the wall card behind is that colour.
   6.6 – 8.3   white paint washes back in from the centre in a reverse spiral; the drops fade.
   8.3 – 9.0   rest.
 */

export const LOOP = 9;
/** Weight of one full drop against the white base. */
export const K = 1.2;
/** Swirl (radians of winding) at which the paint is about 63 % mixed. */
const MIX_TURN = 3.4 * Math.PI;

export type Pot = { name: string; color: string; rgb: [number, number, number] };

function hexRgb(hex: string): [number, number, number] {
  return [parseInt(hex.slice(1, 3), 16) / 255, parseInt(hex.slice(3, 5), 16) / 255, parseInt(hex.slice(5, 7), 16) / 255];
}

const POT_HEX = [
  ['Teal', '#24879A'],
  ['Magenta', '#B8336A'],
  ['Yellow', '#E6B23C'],
  ['White', '#F7F4EE'],
  ['Umber', '#6B4A33'],
];
export const POTS: Pot[] = POT_HEX.map(([name, color]) => ({ name, color, rgb: hexRgb(color) }));
export const BASE_HEX = '#F2EEE6';
/** Pot angles around the bowl (degrees, 0 = right, y down). */
export const POT_ANGLES = [200, 152, 90, 28, -20];
export const YELLOW = 2;
export const TEAL = 0;

export function absorb(rgb: [number, number, number]): [number, number, number] {
  'worklet';
  return [-Math.log(Math.max(rgb[0], 0.02)), -Math.log(Math.max(rgb[1], 0.02)), -Math.log(Math.max(rgb[2], 0.02))];
}

export const BASE_A = absorb(hexRgb(BASE_HEX));
export const POT_A = POTS.map((p) => absorb(p.rgb));

/** Names for colours the bowl can make; the nearest one is shown. */
const NAMES: [string, string][] = [
  ['Chalk White', '#F2EEE6'],
  ['Lemon Sorbet', '#EED9A2'],
  ['Saffron', '#E3B04A'],
  ['Pale Lagoon', '#A9CDD2'],
  ['Deep Teal', '#3E8B95'],
  ['Sea Glass', '#94C3B5'],
  ['Morning Fern', '#7AAE7C'],
  ['Moss', '#6F8A55'],
  ['Olive Grove', '#8E8A4E'],
  ['Rose Quartz', '#E5B4C2'],
  ['Raspberry', '#B54A72'],
  ['Terracotta', '#C0704F'],
  ['Dusk Plum', '#6E4A73'],
  ['Driftwood', '#A8927C'],
  ['Cocoa', '#7A5A44'],
];
export const NAME_LIST = NAMES.map((n) => n[0]);
const NAME_RGB = NAMES.map((n) => hexRgb(n[1]));

export function nearestName(rgb: number[]) {
  'worklet';
  let best = 0;
  let bestD = 1e9;
  for (let i = 0; i < NAME_RGB.length; i += 1) {
    const c = NAME_RGB[i];
    const d = (c[0] - rgb[0]) ** 2 * 0.9 + (c[1] - rgb[1]) ** 2 * 1.2 + (c[2] - rgb[2]) ** 2 * 0.8;
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  }
  return best;
}

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

/** A drop in the bowl, ready for the shader: position, amount, mixed share, winding, absorbance. */
export type Drop = { x: number; y: number; amt: number; m: number; tw: number; a: [number, number, number] };
/** A drop in the air (h = height towards the camera, 0 … 1) or one held under the finger. */
export type Flight = { x: number; y: number; h: number; pot: number };
export type Ripple = { x: number; y: number; age: number; str: number };
export type Splash = { x: number; y: number; age: number; pot: number };

/** Everything one frame needs. */
export type Mix = {
  baseA: [number, number, number];
  baseW: number;
  drops: Drop[];
  ripples: Ripple[];
  splashes: Splash[];
  flights: Flight[];
  /** Wash-back front radius (−1: none) and its reverse twist. */
  wash: [number, number];
  /** How much of the drops' paint is left (the wash fades it). */
  fade: number;
  stick: [number, number, number];
  lift: number[];
};

export type Geo = {
  w: number;
  h: number;
  u: number;
  top: number;
  cardY: number;
  cardH: number;
  cx: number;
  cy: number;
  /** Outer rim radius and the paint's radius. */
  rim: number;
  paint: number;
  potR: number;
  potRing: number;
  panelY: number;
  buttonY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const u = Math.min(w / 393, (h - top - bottom) / 759);
  const cardY = top + 112;
  const cardH = 150 * u;
  const rim = 104 * u;
  const potR = 25 * u;
  const potRing = rim + 54 * u;
  const cy = cardY + cardH + 112 * u;
  const buttonY = h - bottom - 16 - 52;
  return {
    w,
    h,
    u,
    top,
    cardY,
    cardH,
    cx: w / 2,
    cy,
    rim,
    paint: rim * 0.8,
    potR,
    potRing,
    panelY: cy + potRing + potR + 30 * u,
    buttonY,
  };
}

export function potCenter(g: Geo, i: number): [number, number] {
  'worklet';
  const a = (POT_ANGLES[i] * Math.PI) / 180;
  return [g.cx + Math.cos(a) * g.potRing, g.cy + Math.sin(a) * g.potRing];
}

/** The colour the bowl mixes to (sRGB 0 … 1). */
export function mixColor(m: Mix): [number, number, number] {
  'worklet';
  let r = m.baseA[0] * m.baseW;
  let gg = m.baseA[1] * m.baseW;
  let b = m.baseA[2] * m.baseW;
  let wsum = m.baseW;
  for (let i = 0; i < m.drops.length; i += 1) {
    const d = m.drops[i];
    const wgt = K * d.amt * m.fade;
    r += wgt * d.a[0];
    gg += wgt * d.a[1];
    b += wgt * d.a[2];
    wsum += wgt;
  }
  return [Math.exp(-r / wsum), Math.exp(-gg / wsum), Math.exp(-b / wsum)];
}

export function mixedShare(winding: number) {
  'worklet';
  return Math.min(1, 1.04 * (1 - Math.exp(-Math.max(0, winding) / MIX_TURN)));
}

// The script's pieces.
export const LAND_Y = 2.0;
export const LAND_T = 3.25;

export function windingAt(t: number) {
  'worklet';
  return (
    1.6 * Math.PI * easeOutCubic(seg(t, LAND_Y, LAND_Y + 1.4)) +
    1.2 * Math.PI * easeOutCubic(seg(t, LAND_T, LAND_T + 1.15)) +
    3.2 * Math.PI * easeInOutSine(seg(t, 4.0, 5.3))
  );
}

export function landing(g: Geo, which: number): [number, number] {
  'worklet';
  return which === YELLOW ? [g.cx - 20 * g.u, g.cy + 18 * g.u] : [g.cx + 22 * g.u, g.cy - 16 * g.u];
}

function flightAt(g: Geo, pot: number, s: number): Flight {
  'worklet';
  const [px, py] = potCenter(g, pot);
  const [lx, ly] = landing(g, pot);
  const e = easeInOutSine(s);
  return { x: px + (lx - px) * e, y: py + (ly - py) * e, h: Math.sin(Math.PI * s), pot };
}

/** The scripted bowl at loop time t (pure). */
export function autoMix(g: Geo, t: number): Mix {
  'worklet';
  const wind = windingAt(t);
  const back = easeInOutSine(seg(t, 6.6, 8.4));
  const twistNow = wind * (1 - back);
  const fade = 1 - easeInOutCubic(seg(t, 6.6, 8.2));
  const drops: Drop[] = [];
  const ripples: Ripple[] = [];
  const splashes: Splash[] = [];
  const flights: Flight[] = [];
  const events: [number, number][] = [
    [YELLOW, LAND_Y],
    [TEAL, LAND_T],
  ];
  for (const [pot, at] of events) {
    if (t >= at) {
      const [x, y] = landing(g, pot);
      const landWind = windingAt(at);
      drops.push({
        x,
        y,
        amt: easeOutCubic(seg(t, at, at + 0.4)),
        m: mixedShare(wind - landWind),
        tw: twistNow - landWind,
        a: POT_A[pot],
      });
      if (t < at + 1.6) ripples.push({ x, y, age: t - at, str: 1 });
      if (t < at + 0.45) splashes.push({ x, y, age: t - at, pot });
    } else if (t >= at - 0.5) {
      flights.push(flightAt(g, pot, seg(t, at - 0.5, at)));
    }
  }
  const lift = [0, 0, 0, 0, 0];
  lift[YELLOW] = Math.sin(Math.PI * seg(t, 1.4, 1.75)) * 0.6 + easeOutCubic(seg(t, 1.4, 1.55)) * (1 - seg(t, 1.55, 1.85)) * 0.4;
  lift[TEAL] = Math.sin(Math.PI * seg(t, 2.6, 2.95)) * 0.6 + easeOutCubic(seg(t, 2.6, 2.75)) * (1 - seg(t, 2.75, 3.05)) * 0.4;
  // The stick: in over 3.9 – 4.1, two turns, out over 5.2 – 5.4.
  const stickA = 4 * Math.PI * easeInOutSine(seg(t, 4.0, 5.2)) - Math.PI * 0.75;
  const stickIn = easeOutCubic(seg(t, 3.85, 4.1)) * (1 - easeInOutCubic(seg(t, 5.15, 5.45)));
  const sr = g.paint * 0.46;
  const stick: [number, number, number] = [g.cx + Math.cos(stickA) * sr, g.cy + Math.sin(stickA) * sr, stickIn];
  if (stickIn > 0.5) ripples.push({ x: stick[0], y: stick[1], age: 0.25, str: 0.35 * stickIn });
  const front = t > 6.6 && t < 8.4 ? (g.paint + 46 * g.u) * easeInOutSine(seg(t, 6.6, 8.2)) : -1;
  return {
    baseA: BASE_A,
    baseW: 1,
    drops,
    ripples: ripples.slice(0, 3),
    splashes,
    flights,
    wash: [front, -2.4 * Math.PI * back],
    fade,
    stick,
    lift,
  };
}

/** The colour shown on the wall card and the label, a quarter second behind the bowl. */
export function shownColor(g: Geo, t: number) {
  'worklet';
  return mixColor(autoMix(g, Math.max(0, t - 0.25)));
}

export function toHex(rgb: number[]) {
  'worklet';
  let s = '#';
  for (let i = 0; i < 3; i += 1) {
    const v = Math.round(clamp01(rgb[i]) * 255);
    s += (v < 16 ? '0' : '') + v.toString(16).toUpperCase();
  }
  return s;
}

/** Light reflectance value (0 … 100), as paint charts quote it. */
export function lrv(rgb: number[]) {
  'worklet';
  const lin = (c: number) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
  return Math.round(100 * (0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])));
}
