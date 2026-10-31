// Liquid Art Deck — the deck's state machine, its physics and the autoplay script. Helpers above callers.
import { Skia } from '@shopify/react-native-skia';

export const LOOP = 9;
export const N = 6;
export const CARD = { w: 282, h: 384 } as const;
export const FLY = { like: 0.6, pass: 0.42 } as const;

export const ART = [
  { title: 'Mercury Bloom', by: '@ines.form', price: '$240', seed: 0.1 },
  { title: 'Quiet Chrome', by: '@tomas.k', price: '$180', seed: 0.37 },
  { title: 'Dusk Liquor', by: '@mira.studio', price: '$320', seed: 0.62 },
  { title: 'Soft Alloy', by: '@juno.labs', price: '$95', seed: 0.83 },
  { title: 'Pearl Static', by: '@ade.works', price: '$410', seed: 0.21 },
  { title: 'Night Syrup', by: '@koa.prints', price: '$150', seed: 0.5 },
];

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};

export type Deck = {
  cur: number; // index of the front card
  dx: number; // front card's offset from its place
  dy: number;
  vx: number;
  vy: number;
  held: number;
  sx: number; // the art's smear (pt), a wobbly spring driven by the card's speed
  sy: number;
  svx: number;
  svy: number;
  ds: number[]; // each card's displayed slot (springs to i - cur)
  vds: number[];
  fly: number; // card in flight (-1 none)
  kind: number; // 1 like (into the heart), 2 pass (off to the left)
  back: number; // 1 when the flight runs backwards (undo)
  start: number; // clock at the flight's start
  fx: number; // where the flight left from (offset from the centre)
  fy: number;
  hist: number[]; // kinds of the cards already swiped, newest last
  count: number; // favourites
  beat: number; // clock of the last heart beat
};

export const makeDeck = (): Deck => {
  'worklet';
  const ds: number[] = [];
  const vds: number[] = [];
  for (let i = 0; i < N; i++) {
    ds.push(i);
    vds.push(0);
  }
  return { cur: 0, dx: 0, dy: 0, vx: 0, vy: 0, held: 0, sx: 0, sy: 0, svx: 0, svy: 0, ds, vds, fly: -1, kind: 0, back: 0, start: 0, fx: 0, fy: 0, hist: [], count: 12, beat: -10 };
};

/** Starts a swipe of the front card (kind 1 like, 2 pass) from where it is now. */
export const swipe = (d: Deck, kind: number, now: number) => {
  'worklet';
  if (d.fly >= 0 || d.cur >= N) return false;
  d.fly = d.cur;
  d.kind = kind;
  d.back = 0;
  d.start = now;
  d.fx = d.dx;
  d.fy = d.dy;
  d.hist.push(kind);
  d.cur += 1;
  d.dx = 0;
  d.dy = 0;
  d.vx = 0;
  d.vy = 0;
  return true;
};

/** Brings the last swiped card back along the path it left by. */
export const undo = (d: Deck, now: number) => {
  'worklet';
  if (d.fly >= 0 || d.hist.length === 0) return false;
  const kind = d.hist.pop() as number;
  d.cur -= 1;
  d.fly = d.cur;
  d.kind = kind;
  d.back = 1;
  d.start = now;
  d.fx = 0;
  d.fy = 0;
  if (kind === 1) d.count -= 1;
  return true;
};

/** One physics step. */
export const stepDeck = (d: Deck, dt: number, now: number) => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  const px = d.dx;
  const py = d.dy;
  for (let s = 0; s < n; s++) {
    if (d.held === 0) {
      // the front card springs home, keeping its release velocity
      d.vx += (-230 * d.dx - 2 * Math.sqrt(230) * 0.72 * d.vx) * h;
      d.vy += (-230 * d.dy - 2 * Math.sqrt(230) * 0.72 * d.vy) * h;
      d.dx += d.vx * h;
      d.dy += d.vy * h;
    }
    for (let i = 0; i < N; i++) {
      const tgt = i - d.cur;
      d.vds[i] += (190 * (tgt - d.ds[i]) - 2 * Math.sqrt(190) * 0.86 * d.vds[i]) * h;
      d.ds[i] += d.vds[i] * h;
    }
  }
  // the smear follows the card's speed and wobbles back like jelly
  const vx = dt > 0 ? (d.dx - px) / dt : 0;
  const vy = dt > 0 ? (d.dy - py) / dt : 0;
  const tx = Math.max(-64, Math.min(64, vx * 0.1));
  const ty = Math.max(-64, Math.min(64, vy * 0.1));
  for (let s = 0; s < n; s++) {
    d.svx += (260 * (tx - d.sx) - 2 * Math.sqrt(260) * 0.28 * d.svx) * h;
    d.svy += (260 * (ty - d.sy) - 2 * Math.sqrt(260) * 0.28 * d.svy) * h;
    d.sx += d.svx * h;
    d.sy += d.svy * h;
  }
  // a finished flight
  if (d.fly >= 0) {
    const dur = d.kind === 1 ? FLY.like : FLY.pass;
    if (now - d.start >= dur) {
      if (d.back === 0 && d.kind === 1) {
        d.count += 1;
        d.beat = now;
      }
      if (d.back === 1) d.ds[d.fly] = 0;
      d.fly = -1;
    }
  }
};

/** The autoplay finger: where it holds the front card at loop time t (dx, dy), or null. */
export const scriptFinger = (t: number) => {
  'worklet';
  if (t >= 1.0 && t < 1.6) {
    const k = easeInOut((t - 1.0) / 0.6);
    return { on: 1, dx: 150 * k, dy: -30 * Math.sin(k * Math.PI) - 10 * k };
  }
  if (t >= 3.4 && t < 3.9) {
    const k = easeInOut((t - 3.4) / 0.5);
    return { on: 1, dx: -140 * k, dy: 20 * Math.sin(k * Math.PI) };
  }
  return { on: 0, dx: 0, dy: 0 };
};

// ---------------------------------------------------------------- the art: liquid chrome, smeared by the swipe
export const ARTFX = Skia.RuntimeEffect.Make(`
uniform float2 o;      // card centre
uniform float2 sz;     // art size
uniform float seed;
uniform float2 smear;  // pt
uniform float ph;      // slow drift, radians
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
float3 pal(float t) {
  return 0.5 + 0.5 * cos(6.2831 * (t + float3(0.0, 0.33, 0.67)));
}
half4 main(float2 xy) {
  float2 q = (xy - o) / sz.y;
  // the smear: pixels near the middle are dragged back along the motion, like thick paint
  float2 sm = smear / sz.y;
  float fall = exp(-dot(q, q) * 2.2);
  q -= sm * fall * 2.2;
  q += float2(-sm.y, sm.x) * fall * 0.6 * sin(q.y * 9.0);
  float2 d = float2(cos(ph), sin(ph)) * 0.35;
  float2 w = float2(fbm(q * 2.2 + seed * 10.0 + d), fbm(q * 2.2 + 3.7 - d));
  float f = fbm(q * 2.6 + w * 2.2 + seed * 4.0);
  // chrome: bright bands where the field's slope is steep, tinted by a seed palette
  float band = pow(abs(sin(f * 14.0 + seed * 9.0)), 10.0);
  float rim = pow(abs(sin(f * 7.0 + 1.3 + seed * 5.0)), 3.0);
  float3 base = mix(float3(0.05, 0.05, 0.07), pal(seed + f * 0.28), 0.66);
  float3 col = base * (0.2 + 0.85 * f) * (0.55 + 0.45 * rim) + band * mix(float3(1.0), pal(seed + 0.12 + f * 0.2), 0.3) * 1.05;
  // a soft vignette inside the card
  col *= 1.0 - 0.35 * smoothstep(0.35, 0.75, length(q));
  col += (hash(floor(xy)) - 0.5) * 0.04;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}`);
