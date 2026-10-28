/*
 Ingot Coin Switch — the timeline and the 3D ring (a port of IngotCoinSwitchModel.swift).

 Ten pieces stand on a ring of radius 120 around a vertical axis and turn with it (piece i and i + 5
 share a style, so half a turn brings the ring back to itself). Each piece is a convex body made of
 four rings of K outline points: its outline goes from a rounded rectangle (a superellipse, the ingot)
 to a circle (the coin) with the morph m, its rings from a tapered ingot (small top face, big base) to
 a thin coin with a chamfer. The body is turned by its own yaw (a quarter turn with the morph, so a
 coin stands radially like a page of a book), leaned outward from its foot (the crown of the pin) and
 placed on the ring; then it is projected with perspective and shaded face by face.

 Every function here is a worklet: the frame and the pieces are computed on the UI thread.
 Helpers are declared above their callers.
*/

/** One loop of the self-playing demo: rest, switch to coins, hold, switch back, rest. */
export const LOOP = 10;
export const COUNT = 10;
export const STYLES = 5;
const RING_R = 120;
/** The ring's size on the screen. */
const VIEW_SCALE = 0.9;
const CAM_D = 900;
const FOCAL = 900;
/** Outline points per ring. */
const K = 48;

/** The loop's taps on the switch, and the morph that follows each. */
const SWITCH_ON = 2.4;
const SWITCH_OFF = 6.4;
const MORPH_LAG = 0.1;
const MORPH_DUR = 1.1;
/** The morph runs as a wave from the front piece to the sides. */
const WAVE_DELAY = 0.32;
const IDLE_BEFORE_BACK = 2;
/** The ring's slow turn: half a turn per loop. */
const OMEGA = Math.PI / LOOP;
/** Rotating the ring by this angle brings it back to itself. */
const HOME = Math.PI;

export const INGOT_HEX = [0xd8b25a, 0xd9967f, 0xb8bcc7, 0x50525c, 0xc7a47e];
export const COIN_HEX = [0xa9a3b2, 0xe4b9ae, 0xecdcd6, 0x86a1c9, 0xc78e86];

export const METALS = ['Gold', 'Copper', 'Silver', 'Platinum', 'Palladium'];
export const METAL_PRICES = ['$78.42 /g', '$9.36 /kg', '$1.04 /g', '$33.90 /g', '$35.12 /g'];
export const METAL_MOVES = ['+1.2%', '+0.8%', '+2.1%', '+0.4%', '+1.7%'];
export const TICKERS = ['HLDN', 'NWG', 'ORF', 'VLA', 'QRY'];
export const MARKS = ['H', 'N', 'O', 'V', 'Q'];
export const STOCK_PRICES = ['$42.18', '$118.60', '$27.35', '$64.02', '$211.40'];
export const STOCK_MOVES = ['+2.4%', '+0.9%', '+1.6%', '+3.1%', '+0.6%'];

/** Ingot: half width and height of the outline; coin: radius. */
const INGOT_A = 40;
const INGOT_B = 108;
const COIN_R = 78;
/** The four rings of a body, (z, scale x, scale y), for the ingot and the coin. */
const INGOT_RINGS = [
  [22, 0.8, 0.91],
  [-17, 1, 1],
  [-21, 1, 1],
  [-22, 0.97, 0.98],
];
const COIN_RINGS = [
  [7.5, 0.94, 0.94],
  [5, 1, 1],
  [-5, 1, 1],
  [-7.5, 0.94, 0.94],
];

/** Locations of the colours across a face. */
export const CAP_STOPS = [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1];

const SEL_RESPONSE = 0.42;
const SEL_DAMPING = 0.78;

export type Rgb = { r: number; g: number; b: number };
type V3 = { x: number; y: number; z: number };

/** One frame of the screen, whatever drives it (the loop, the finger or the way back to rest). */
export type Frame = {
  /** The ring's angle, radians. */
  phi: number;
  /** The morph of each piece: 0 ingot, 1 coin. */
  m: number[];
  /** The switch's dark capsule: 0 on "Commodities", 1 on "Stocks" (a spring, it overshoots). */
  sel: number;
  /** The copy crossfades from one mode (0 commodities, 1 stocks) to the other, copyU s after the tap. */
  copyFrom: number;
  copyTo: number;
  copyU: number;
  reduced: boolean;
};

/** A strip of a piece's side: xy pairs on the screen. */
export type Quad = { points: number[]; color: Rgb };

/** A flat face with its sliding highlight and its mark. */
export type Cap = {
  points: number[];
  /** Colours of the gradient across the face, at CAP_STOPS. */
  colors: Rgb[];
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  /** Top and bottom of the face, for the sheen. */
  topX: number;
  topY: number;
  bottomX: number;
  bottomY: number;
  /** Maps the mark's picture (centred on 0, y down, in the face's units) onto the screen: a, b, c, d, tx, ty. */
  transform: number[];
  ingotAlpha: number;
  coinAlpha: number;
};

export type Piece = {
  index: number;
  depth: number;
  opacity: number;
  silhouette: number[];
  silhouetteColor: Rgb;
  quads: Quad[];
  caps: Cap[];
};

// Curves

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

/** An angle wrapped into −π…π. */
function wrap(a: number) {
  'worklet';
  let x = a % (2 * Math.PI);
  if (x > Math.PI) x -= 2 * Math.PI;
  if (x < -Math.PI) x += 2 * Math.PI;
  return x;
}

/** A damped spring from x0 (velocity v0) to `target`, tau seconds in. */
function spring(tau: number, x0: number, v0: number, target: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return x0;
  const z = Math.min(damping, 0.999);
  const w0 = (2 * Math.PI) / response;
  const wd = w0 * Math.sqrt(1 - z * z);
  const a = x0 - target;
  const b = (v0 + z * w0 * a) / wd;
  return target + Math.exp(-z * w0 * tau) * (a * Math.cos(wd * tau) + b * Math.sin(wd * tau));
}

function theta(i: number, phi: number) {
  'worklet';
  return phi + (2 * Math.PI * i) / COUNT;
}

/** The delay of piece i in the morph wave, from where it stands on the ring. */
function delayOf(i: number, phi: number) {
  'worklet';
  return (WAVE_DELAY * Math.abs(wrap(theta(i, phi)))) / Math.PI;
}

/** The morph of a piece from m0 to `target`, `u` s after its wave started. */
function morph(u: number, delay: number, m0: number, target: number, duration: number) {
  'worklet';
  return m0 + (target - m0) * easeInOut(seg(u, delay, delay + duration));
}

function emptyM() {
  'worklet';
  const m: number[] = [];
  for (let i = 0; i < COUNT; i++) m.push(0);
  return m;
}

export function emptyFrame(): Frame {
  'worklet';
  return { phi: 0, m: emptyM(), sel: 0, copyFrom: 0, copyTo: 0, copyU: 99, reduced: false };
}

// autoPlay

export function autoFrame(t: number): Frame {
  'worklet';
  const f = emptyFrame();
  f.phi = OMEGA * t;
  const on = SWITCH_ON + MORPH_LAG;
  const off = SWITCH_OFF + MORPH_LAG;
  for (let i = 0; i < COUNT; i++) {
    const up = morph(t - on, delayOf(i, OMEGA * on), 0, 1, MORPH_DUR);
    const down = morph(t - off, delayOf(i, OMEGA * off), 0, 1, MORPH_DUR);
    f.m[i] = up - down;
  }
  if (t < SWITCH_ON) {
    f.sel = 0;
  } else if (t < SWITCH_OFF) {
    f.sel = spring(t - SWITCH_ON, 0, 0, 1, SEL_RESPONSE, SEL_DAMPING);
    f.copyFrom = 0;
    f.copyTo = 1;
    f.copyU = t - SWITCH_ON;
  } else {
    const x0 = spring(SWITCH_OFF - SWITCH_ON, 0, 0, 1, SEL_RESPONSE, SEL_DAMPING);
    f.sel = spring(t - SWITCH_OFF, x0, 0, 0, SEL_RESPONSE, SEL_DAMPING);
    f.copyFrom = 1;
    f.copyTo = 0;
    f.copyU = t - SWITCH_OFF;
  }
  return f;
}

/** Opacity of the outgoing and incoming copy, and the rise of the incoming one. */
export function copyFade(u: number) {
  'worklet';
  const o = 1 - easeOut(seg(u, 0, 0.15));
  const e = easeOut(seg(u, 0.12, 0.35));
  return { out: o, inn: e, rise: 6 * (1 - e) };
}

/** The piece nearest the front. */
export function frontIndex(f: Frame) {
  'worklet';
  const x = -f.phi / ((2 * Math.PI) / COUNT);
  let i = Math.round(x) % COUNT;
  if (i < 0) i += COUNT;
  return i;
}

/** How much the ticker label shows (it fades at each change of the front piece). */
export function frontAlpha(f: Frame) {
  'worklet';
  const y = -f.phi / ((2 * Math.PI) / COUNT) + 0.5;
  const frac = y - Math.floor(y);
  const edge = clamp01(Math.min(frac, 1 - frac) / 0.07);
  const swap = clamp01(Math.abs(f.m[frontIndex(f)] - 0.5) * 5);
  return f.reduced ? swap : Math.min(edge, swap);
}

// Material

function v3(x: number, y: number, z: number): V3 {
  'worklet';
  return { x, y, z };
}

function norm(a: V3): V3 {
  'worklet';
  const l = Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
  return l > 1e-9 ? v3(a.x / l, a.y / l, a.z / l) : a;
}

function dot(a: V3, b: V3) {
  'worklet';
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

export function rgb(hex: number): Rgb {
  'worklet';
  return { r: ((hex >> 16) & 0xff) / 255, g: ((hex >> 8) & 0xff) / 255, b: (hex & 0xff) / 255 };
}

export function mix(a: Rgb, b: Rgb, t: number): Rgb {
  'worklet';
  return { r: a.r + (b.r - a.r) * t, g: a.g + (b.g - a.g) * t, b: a.b + (b.b - a.b) * t };
}

export function scaleRgb(a: Rgb, k: number): Rgb {
  'worklet';
  return { r: a.r * k, g: a.g * k, b: a.b * k };
}

function clampRgb(a: Rgb): Rgb {
  'worklet';
  return { r: clamp01(a.r), g: clamp01(a.g), b: clamp01(a.b) };
}

const KEY_L = { x: -0.45, y: 0.6, z: 0.66 };
const RIM_L = { x: 0.85, y: 0.15, z: 0.5 };

/** Metal (or frosted coin metal, `frost` 1) under a key light and a rim light. */
function shade(base: Rgb, n: V3, frost: number): Rgb {
  'worklet';
  const keyL = norm(KEY_L);
  const rimL = norm(RIM_L);
  const keyH = norm(v3(keyL.x, keyL.y, keyL.z + 1));
  const rimH = norm(v3(rimL.x, rimL.y, rimL.z + 1));
  const nv = Math.max(0, n.z);
  const deep = scaleRgb({ r: base.r * base.r, g: base.g * base.g, b: base.b * base.b }, 0.62);
  let c = mix(deep, base, smooth(0, 0.95, nv));
  c = scaleRgb(c, 0.6 + 0.42 * Math.max(0, dot(n, keyL)));
  const spec = 0.95 * Math.pow(Math.max(0, dot(n, keyH)), lerp(36, 22, frost)) + 0.4 * Math.pow(Math.max(0, dot(n, rimH)), 10);
  const hi = mix(base, { r: 1, g: 1, b: 1 }, lerp(0.62, 0.5, frost));
  const k = spec * lerp(1, 0.75, frost);
  return clampRgb({ r: c.r + hi.r * k, g: c.g + hi.g * k, b: c.b + hi.b * k });
}

/** The colours across a face: its flat shade with a highlight band at `p` (0…1 across the face). */
function capColors(base: Rgb, n: V3, p: number, frost: number, k: number): Rgb[] {
  'worklet';
  const flat = shade(base, n, frost);
  const hi = mix(flat, { r: 1, g: 1, b: 1 }, lerp(0.6, 0.4, frost));
  const out: Rgb[] = [];
  for (let s = 0; s < CAP_STOPS.length; s++) {
    const x = CAP_STOPS[s];
    const edge = 0.86 + 0.14 * (1 - Math.pow(2 * x - 1, 2)) - 0.06 * x;
    const band = Math.exp(-Math.pow((x - p) / 0.17, 2));
    // Polished metal also mirrors the dark room: a darker band on each side of the highlight.
    const dark = Math.exp(-Math.pow((x - p - 0.42) / 0.16, 2)) + Math.exp(-Math.pow((x - p + 0.42) / 0.16, 2));
    out.push(scaleRgb(clampRgb(mix(scaleRgb(flat, edge * (1 - 0.3 * dark)), hi, band * 0.9)), k));
  }
  return out;
}

// Geometry

function hullCross(xs: number[], ys: number[], o: number, a: number, b: number) {
  'worklet';
  return (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o]);
}

/** The convex hull of xy points (monotone chain), as xy pairs. */
function hull(xs: number[], ys: number[]): number[] {
  'worklet';
  const idx: number[] = [];
  for (let i = 0; i < xs.length; i++) idx.push(i);
  idx.sort((p, q) => (xs[p] === xs[q] ? ys[p] - ys[q] : xs[p] - xs[q]));
  const lower: number[] = [];
  for (let n = 0; n < idx.length; n++) {
    const q = idx[n];
    while (lower.length >= 2 && hullCross(xs, ys, lower[lower.length - 2], lower[lower.length - 1], q) <= 0) lower.pop();
    lower.push(q);
  }
  const upper: number[] = [];
  for (let n = idx.length - 1; n >= 0; n--) {
    const q = idx[n];
    while (upper.length >= 2 && hullCross(xs, ys, upper[upper.length - 2], upper[upper.length - 1], q) <= 0) upper.pop();
    upper.push(q);
  }
  lower.pop();
  upper.pop();
  const out: number[] = [];
  for (let n = 0; n < lower.length; n++) out.push(xs[lower[n]], ys[lower[n]]);
  for (let n = 0; n < upper.length; n++) out.push(xs[upper[n]], ys[upper[n]]);
  return out;
}

/** A point of the outline: the ingot's superellipse (m 0) to the coin's circle (m 1). */
function outlineX(j: number, m: number) {
  'worklet';
  const c = Math.cos((2 * Math.PI * j) / K);
  const ix = INGOT_A * (c < 0 ? -1 : 1) * Math.pow(Math.abs(c), 1 / 3);
  return lerp(ix, COIN_R * c, m);
}

function outlineY(j: number, m: number) {
  'worklet';
  const s = Math.sin((2 * Math.PI * j) / K);
  const iy = INGOT_B * (s < 0 ? -1 : 1) * Math.pow(Math.abs(s), 1 / 3);
  return lerp(iy, COIN_R * s, m);
}

/** The piece's transform: own yaw, lean from its foot, place on the ring. */
type Pose = { pop: number; half: number; cyaw: number; syaw: number; cl: number; sl: number; ct: number; st: number };

function world(q: Pose, x0: number, y0: number, z0: number): V3 {
  'worklet';
  let x = x0 * q.pop;
  let y = y0 * q.pop;
  let z = z0 * q.pop;
  const x1 = x * q.cyaw + z * q.syaw;
  z = -x * q.syaw + z * q.cyaw;
  x = x1;
  const yy = y + q.half;
  y = -q.half + yy * q.cl - z * q.sl;
  z = yy * q.sl + z * q.cl;
  const x2 = x * q.ct + z * q.st;
  z = -x * q.st + z * q.ct;
  return v3(x2 + RING_R * q.st, y, z + RING_R * q.ct);
}

function projX(p: V3, cx: number) {
  'worklet';
  return cx + (p.x * VIEW_SCALE * FOCAL) / (CAM_D - p.z);
}

function projY(p: V3, cy: number) {
  'worklet';
  return cy - (p.y * VIEW_SCALE * FOCAL) / (CAM_D - p.z);
}

function piece(i: number, f: Frame, cx: number, cy: number): Piece {
  'worklet';
  const m = f.m[i];
  const th = theta(i, f.phi);
  const style = i % STYLES;
  const yaw = (Math.PI / 2) * m;
  const lean = 0.2 * (1 - 0.75 * m);
  const bump = f.reduced ? 0 : Math.sin(Math.PI * m);
  const q: Pose = {
    pop: 1 + 0.05 * bump,
    half: lerp(INGOT_B, COIN_R, m),
    cyaw: Math.cos(yaw),
    syaw: Math.sin(yaw),
    cl: Math.cos(lean),
    sl: Math.sin(lean),
    ct: Math.cos(th),
    st: Math.sin(th),
  };

  const rz: number[] = [];
  const rsx: number[] = [];
  const rsy: number[] = [];
  for (let r = 0; r < 4; r++) {
    rz.push(lerp(INGOT_RINGS[r][0], COIN_RINGS[r][0], m));
    rsx.push(lerp(INGOT_RINGS[r][1], COIN_RINGS[r][1], m));
    rsy.push(lerp(INGOT_RINGS[r][2], COIN_RINGS[r][2], m));
  }
  const ox: number[] = [];
  const oy: number[] = [];
  for (let j = 0; j < K; j++) {
    ox.push(outlineX(j, m));
    oy.push(outlineY(j, m));
  }
  // pts3[r * K + j], and their projections.
  const pts3: V3[] = [];
  const px: number[] = [];
  const py: number[] = [];
  for (let r = 0; r < 4; r++) {
    for (let j = 0; j < K; j++) {
      const p = world(q, ox[j] * rsx[r], oy[j] * rsy[r], rz[r]);
      pts3.push(p);
      px.push(projX(p, cx));
      py.push(projY(p, cy));
    }
  }

  const center = world(q, 0, 0, 0);
  const frost = smooth(0.25, 0.75, m);
  const base = mix(rgb(INGOT_HEX[style]), rgb(COIN_HEX[style]), frost);
  // A coin's edge is brighter, plain metal, whatever its face's tint.
  const rim = mix(base, rgb(0xd9d8dc), 0.4 * frost);
  // Pieces at the back of the ring sit in the ring's shade.
  const shadeK = lerp(0.72, 1, smooth(-RING_R, RING_R * 0.4, center.z));

  // Side quads facing the camera; neighbours of (almost) the same colour merge into one strip.
  const quads: Quad[] = [];
  let sr = 0;
  let sg = 0;
  let sb = 0;
  let seen = 0;
  for (let r = 0; r < 3; r++) {
    let j0 = -1;
    let j1 = -1;
    let runColor: Rgb = { r: 0, g: 0, b: 0 };
    for (let j = 0; j <= K; j++) {
      let visible = false;
      let col: Rgb = runColor;
      if (j < K) {
        const jn = (j + 1) % K;
        const a = pts3[r * K + j];
        const b = pts3[r * K + jn];
        const c = pts3[(r + 1) * K + jn];
        const d = pts3[(r + 1) * K + j];
        let ux = b.x - a.x;
        let uy = b.y - a.y;
        let uz = b.z - a.z;
        let wx = d.x - a.x;
        let wy = d.y - a.y;
        let wz = d.z - a.z;
        let n = v3(uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx);
        if (Math.sqrt(dot(n, n)) < 1e-6) {
          ux = c.x - b.x;
          uy = c.y - b.y;
          uz = c.z - b.z;
          wx = d.x - b.x;
          wy = d.y - b.y;
          wz = d.z - b.z;
          n = v3(uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx);
        }
        const mid = v3((a.x + b.x + c.x + d.x) / 4, (a.y + b.y + c.y + d.y) / 4, (a.z + b.z + c.z + d.z) / 4);
        if (Math.sqrt(dot(n, n)) >= 1e-6) {
          if (dot(n, v3(mid.x - center.x, mid.y - center.y, mid.z - center.z)) < 0) n = v3(-n.x, -n.y, -n.z);
          n = norm(n);
          if (dot(n, v3(-mid.x, -mid.y, CAM_D - mid.z)) > 0) {
            visible = true;
            col = scaleRgb(shade(rim, n, frost), shadeK);
          }
        }
      }
      let close = !visible;
      if (visible && j0 >= 0) {
        const dc = Math.max(Math.abs(col.r - runColor.r), Math.abs(col.g - runColor.g), Math.abs(col.b - runColor.b));
        if (dc > 0.012) close = true;
      }
      if (close && j0 >= 0) {
        const pts: number[] = [];
        for (let jj = j0; jj <= j1 + 1; jj++) pts.push(px[r * K + (jj % K)], py[r * K + (jj % K)]);
        for (let jj = j1 + 1; jj >= j0; jj--) pts.push(px[(r + 1) * K + (jj % K)], py[(r + 1) * K + (jj % K)]);
        quads.push({ points: pts, color: runColor });
        j0 = -1;
      }
      if (visible) {
        sr += col.r;
        sg += col.g;
        sb += col.b;
        seen += 1;
        if (j0 < 0) {
          runColor = col;
          j0 = j;
        }
        j1 = j;
      }
    }
  }

  // The silhouette, filled first so no background shows through the seams between faces.
  const silhouette = hull(px, py);
  const silhouetteColor = seen > 0 ? { r: sr / seen, g: sg / seen, b: sb / seen } : scaleRgb(base, shadeK);

  const caps: Cap[] = [];
  const o0 = world(q, 0, 0, 0);
  const o1 = world(q, 0, 0, 1);
  const axisZ = norm(v3(o1.x - o0.x, o1.y - o0.y, o1.z - o0.z));
  for (let side = 0; side < 2; side++) {
    const r = side === 0 ? 0 : 3;
    const sign = side === 0 ? 1 : -1;
    const zc = rz[r];
    const cc = world(q, 0, 0, zc);
    const n = v3(axisZ.x * sign, axisZ.y * sign, axisZ.z * sign);
    const facing = dot(n, norm(v3(-cc.x, -cc.y, CAM_D - cc.z)));
    if (facing <= 0) continue;
    const hw = lerp(INGOT_A, COIN_R, m) * rsx[r];
    const hh = q.half * rsy[r];
    // Across the face, left to right as seen from its front.
    const left = world(q, -hw * sign, 0, zc);
    const right = world(q, hw * sign, 0, zc);
    const p = 0.5 - 0.85 * Math.sin(Math.atan2(n.x, n.z));
    const cxo = projX(cc, cx);
    const cyo = projY(cc, cy);
    const ax = world(q, 40 * sign * rsx[r], 0, zc);
    const ay = world(q, 0, 40 * rsy[r], zc);
    const ux = (projX(ax, cx) - cxo) / 40;
    const uy = (projY(ax, cy) - cyo) / 40;
    const vx = (projX(ay, cx) - cxo) / 40;
    const vy = (projY(ay, cy) - cyo) / 40;
    const show = smooth(0.08, 0.4, facing);
    const pts: number[] = [];
    for (let j = 0; j < K; j++) pts.push(px[r * K + j], py[r * K + j]);
    const top = world(q, 0, hh, zc);
    const bottom = world(q, 0, -hh, zc);
    caps.push({
      points: pts,
      colors: capColors(base, n, p, frost, shadeK),
      fromX: projX(left, cx),
      fromY: projY(left, cy),
      toX: projX(right, cx),
      toY: projY(right, cy),
      topX: projX(top, cx),
      topY: projY(top, cy),
      bottomX: projX(bottom, cx),
      bottomY: projY(bottom, cy),
      transform: [ux, uy, -vx, -vy, cxo, cyo],
      ingotAlpha: r === 0 ? show * smooth(0.5, 0.2, m) : 0,
      coinAlpha: show * smooth(0.5, 0.8, m),
    });
  }

  const opacity = f.reduced ? 1 : 1 - 0.45 * bump;
  return { index: i, depth: center.z, opacity, silhouette, silhouetteColor, quads, caps };
}

/** The pieces of the ring, sorted far to near, projected around (cx, cy). */
export function pieces(f: Frame, cx: number, cy: number): Piece[] {
  'worklet';
  const out: Piece[] = [];
  for (let i = 0; i < COUNT; i++) out.push(piece(i, f, cx, cy));
  out.sort((a, b) => a.depth - b.depth);
  return out;
}

// Driver: turns the clock and the finger into frames.

/** "Not set" for the driver's times. */
const NONE = -1e9;
const DECAY = 0.31;
const RETURN_DUR = 1.4;
const POINTS_PER_RADIAN = 150;

export type Driver = {
  autoStart: number;
  manual: boolean;
  dragging: boolean;
  lastTouch: number;
  // The ring: an anchor angle, the release speed (decays) and the slow turn.
  phiAnchor: number;
  anchorAt: number;
  release: number;
  dragPhi0: number;
  // The morph towards `target` (0 ingots, 1 coins).
  target: number;
  switchAt: number;
  m0: number[];
  delays: number[];
  // The switch's capsule and the copy.
  selAt: number;
  sel0: number;
  selV0: number;
  copyFrom: number;
  copyAt: number;
  // The way home after the last touch.
  returnAt: number;
  retPhi0: number;
  retV0: number;
  retPhi1: number;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    autoStart: NONE,
    manual: false,
    dragging: false,
    lastTouch: NONE,
    phiAnchor: 0,
    anchorAt: 0,
    release: 0,
    dragPhi0: 0,
    target: 0,
    switchAt: NONE,
    m0: emptyM(),
    delays: emptyM(),
    selAt: NONE,
    sel0: 0,
    selV0: 0,
    copyFrom: 0,
    copyAt: NONE,
    returnAt: NONE,
    retPhi0: 0,
    retV0: 0,
    retPhi1: 0,
  };
}

function omegaOf(auto: boolean) {
  'worklet';
  return auto ? OMEGA : 0;
}

function driverPhi(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.returnAt !== NONE) {
    // Cubic Hermite from the current angle and speed to home, arriving at the loop's speed.
    const T = RETURN_DUR;
    const u = clamp01((now - d.returnAt) / T);
    const u2 = u * u;
    const u3 = u2 * u;
    return (
      (2 * u3 - 3 * u2 + 1) * d.retPhi0 +
      (u3 - 2 * u2 + u) * T * d.retV0 +
      (-2 * u3 + 3 * u2) * d.retPhi1 +
      (u3 - u2) * T * OMEGA
    );
  }
  if (d.dragging) return d.phiAnchor;
  const dt = Math.max(0, now - d.anchorAt);
  return d.phiAnchor + d.release * DECAY * (1 - Math.exp(-dt / DECAY)) + omegaOf(auto) * dt;
}

function driverPhiVelocity(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.dragging) return 0;
  const dt = Math.max(0, now - d.anchorAt);
  return d.release * Math.exp(-dt / DECAY) + omegaOf(auto);
}

function manualFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  const f = emptyFrame();
  f.phi = driverPhi(d, now, auto);
  const dur = reduced ? 0.3 : MORPH_DUR;
  for (let i = 0; i < COUNT; i++) {
    f.m[i] = morph(now - d.switchAt, reduced ? 0 : d.delays[i], d.m0[i], d.target, dur);
  }
  f.sel = spring(now - d.selAt, d.sel0, d.selV0, d.target, SEL_RESPONSE, SEL_DAMPING);
  f.copyFrom = d.copyFrom;
  f.copyTo = d.target;
  f.copyU = now - d.copyAt;
  return f;
}

function switchTo(d: Driver, mode: number, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  const cur = manualFrame(d, now, auto, reduced);
  const ahead = manualFrame(d, now + 0.01, auto, reduced);
  d.m0 = cur.m.slice();
  d.selV0 = (ahead.sel - cur.sel) / 0.01;
  d.sel0 = cur.sel;
  d.selAt = now;
  d.copyFrom = d.target;
  d.copyAt = now;
  d.target = mode;
  d.switchAt = now + (reduced ? 0 : MORPH_LAG);
  const delays: number[] = [];
  for (let i = 0; i < COUNT; i++) delays.push(delayOf(i, cur.phi));
  d.delays = delays;
}

function goHome(d: Driver, now: number) {
  'worklet';
  d.retPhi0 = driverPhi(d, now, true);
  d.retV0 = driverPhiVelocity(d, now, true);
  const guess = d.retPhi0 + OMEGA * RETURN_DUR + (d.retV0 - OMEGA) * DECAY;
  d.retPhi1 = Math.round(guess / HOME) * HOME;
  if (d.target !== 0) switchTo(d, 0, now, true, false);
  d.returnAt = now;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (auto && d.manual && !d.dragging) {
    if (d.returnAt === NONE && now >= d.lastTouch + IDLE_BEFORE_BACK) goHome(d, now);
    if (d.returnAt !== NONE && now >= d.returnAt + RETURN_DUR) {
      d.manual = false;
      d.returnAt = NONE;
      d.autoStart = now;
    }
  }
  let f: Frame;
  if (auto && !d.manual) {
    if (d.autoStart === NONE) d.autoStart = now;
    f = autoFrame((now - d.autoStart) % LOOP);
  } else {
    f = manualFrame(d, now, auto, reduced);
  }
  f.reduced = reduced;
  if (reduced) f.phi = 0;
  return f;
}

/** True once nothing moves after a touch (to pause the frame callback without autoPlay). */
export function driverSettled(d: Driver, now: number) {
  'worklet';
  return !d.dragging && now - d.lastTouch > 3;
}

/** Takes over from the loop at its current frame. */
function capture(d: Driver, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  if (d.manual) return;
  const f = driverFrame(d, now, auto, reduced);
  d.manual = true;
  d.phiAnchor = f.phi % HOME;
  d.anchorAt = now;
  d.release = 0;
  d.target = f.copyTo;
  d.m0 = f.m.slice();
  d.switchAt = now;
  d.delays = emptyM();
  d.sel0 = f.sel;
  d.selV0 = 0;
  d.selAt = now;
  d.copyFrom = f.copyFrom;
  d.copyAt = now - f.copyU;
}

/** Stops the way home where it is, keeping the angle. */
function cancelReturn(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.returnAt === NONE) return;
  const p = driverPhi(d, now, auto);
  d.returnAt = NONE;
  d.phiAnchor = p;
  d.anchorAt = now;
  d.release = 0;
}

/** A tap on a pill. Returns true when it changed the mode. */
export function driverTap(d: Driver, mode: number, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  capture(d, now, auto, reduced);
  cancelReturn(d, now, auto);
  d.lastTouch = now;
  if (mode === d.target) return false;
  switchTo(d, mode, now, auto, reduced);
  return true;
}

export function driverBeginDrag(d: Driver, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  capture(d, now, auto, reduced);
  cancelReturn(d, now, auto);
  d.dragPhi0 = driverPhi(d, now, auto);
  d.phiAnchor = d.dragPhi0;
  d.anchorAt = now;
  d.release = 0;
  d.dragging = true;
  d.lastTouch = now;
}

export function driverDrag(d: Driver, dx: number, now: number) {
  'worklet';
  if (!d.dragging) return;
  d.phiAnchor = d.dragPhi0 + dx / POINTS_PER_RADIAN;
  d.anchorAt = now;
  d.lastTouch = now;
}

export function driverEndDrag(d: Driver, velocity: number, now: number) {
  'worklet';
  if (!d.dragging) return;
  d.dragging = false;
  d.anchorAt = now;
  d.release = Math.min(Math.max(velocity / POINTS_PER_RADIAN, -7), 7);
  d.lastTouch = now;
}
