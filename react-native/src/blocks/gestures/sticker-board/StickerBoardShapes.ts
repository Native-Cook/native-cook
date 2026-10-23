import { PathOp, Skia, StrokeCap, StrokeJoin, type SkPath } from '@shopify/react-native-skia';

export type StickerKind = 'circle' | 'rounded' | 'star' | 'smiley' | 'bubble' | 'heart' | 'badge';

/** A sticker: its shape, its art, where it rests on the board and how it is turned. */
export type Sticker = {
  kind: StickerKind;
  /** Radius for round shapes; width and height for the rounded photo and the bubble (pt). */
  size: [number, number];
  /** Unsplash id for a photo sticker. */
  photo?: string;
  /** Fill of a drawn sticker. */
  color?: string;
  /** Words on the bubble or the badge. */
  text?: string;
  /** Resting place, as shares of the board's width and of its height between the safe areas. */
  home: [number, number];
  /** Resting turn, degrees. */
  turn: number;
};

// The white die-cut border around every sticker (pt).
export const BORDER = 7;
// Points around a sticker's outline, sampled at even angles from its middle, so the outline can be
// crumpled into a paper ball point by point. Every fourth one starts a facet of the ball.
export const RING = 48;
export const FACET_STEP = 4;
// Chords between ring points drawn as creases on the ball.
export const CREASES: [number, number][] = [
  [2, 21],
  [7, 31],
  [13, 40],
  [25, 45],
  [35, 10],
  [18, 29],
];

export const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=400&q=80`;

export const STICKERS: Sticker[] = [
  { kind: 'circle', size: [56, 56], photo: 'photo-1559181567-c3190ca9959b', home: [0.27, 0.17], turn: -6 },
  { kind: 'rounded', size: [108, 132], photo: 'photo-1518020382113-a7e8fc38eac9', home: [0.72, 0.19], turn: 7 },
  { kind: 'star', size: [48, 48], color: '#ffc531', home: [0.17, 0.36], turn: -14 },
  { kind: 'circle', size: [46, 46], photo: 'photo-1629740067905-bd3f515aa739', home: [0.52, 0.37], turn: 4 },
  { kind: 'bubble', size: [124, 60], color: '#ff6f91', text: 'good boy!', home: [0.8, 0.41], turn: 8 },
  { kind: 'rounded', size: [128, 96], photo: 'photo-1609639643505-3c158a56de42', home: [0.33, 0.56], turn: -5 },
  { kind: 'smiley', size: [40, 40], color: '#ffd23f', home: [0.75, 0.58], turn: 10 },
  { kind: 'badge', size: [42, 42], color: '#2f9e64', text: 'FRESH', home: [0.18, 0.75], turn: -9 },
  { kind: 'circle', size: [50, 50], photo: 'photo-1558788353-f76d92427f16', home: [0.5, 0.74], turn: 3 },
  { kind: 'heart', size: [44, 40], color: '#ff4b5c', home: [0.81, 0.75], turn: -8 },
];

// Cork: warm ground, fine granules, darker flecks and a soft vignette.
export const CORK = Skia.RuntimeEffect.Make(`
uniform float2 size;

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
  float fine = noise(xy * 0.9);
  float mid = noise(xy * 0.22);
  float wide = noise(xy * 0.035);
  float3 base = float3(0.73, 0.54, 0.34);
  float3 col = base * (0.8 + 0.22 * mid + 0.14 * wide) * (0.88 + 0.22 * fine);
  float fleck = step(0.9, hash(floor(xy * 0.45))) * smoothstep(0.3, 0.8, noise(xy * 0.7));
  col *= 1.0 - 0.45 * fleck;
  float2 uv = xy / size - 0.5;
  col *= 1.0 - 0.9 * dot(uv, uv);
  return half4(half3(col), 1.0);
}`)!;

/** A five-pointed star, its points rounded by the stroke drawn over it. */
function starPath(r: number) {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < 10; i += 1) {
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    const rr = i % 2 === 0 ? r : r * 0.5;
    if (i === 0) b.moveTo(Math.cos(a) * rr, Math.sin(a) * rr);
    else b.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
  }
  return b.close().build();
}

function heartPath(w: number, h: number) {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, h * 0.95);
  b.cubicTo(-w * 0.2, h * 0.7, -w, h * 0.35, -w, -h * 0.25);
  b.cubicTo(-w, -h * 0.85, -w * 0.25, -h * 1.05, 0, -h * 0.5);
  b.cubicTo(w * 0.25, -h * 1.05, w, -h * 0.85, w, -h * 0.25);
  b.cubicTo(w, h * 0.35, w * 0.2, h * 0.7, 0, h * 0.95);
  return b.close().build();
}

/** A speech bubble: a pill with a tail off its lower left. */
function bubblePath(w: number, h: number) {
  const body = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), h / 2, h / 2)).build();
  const tail = Skia.PathBuilder.Make()
    .moveTo(-w * 0.28, h * 0.3)
    .quadTo(-w * 0.32, h * 0.62, -w * 0.46, h * 0.78)
    .quadTo(-w * 0.18, h * 0.7, -w * 0.06, h * 0.4)
    .close()
    .build();
  return Skia.Path.MakeFromOp(body, tail, PathOp.Union) ?? body;
}

/** A scalloped seal: a circle with sixteen bumps around it. */
function badgePath(r: number) {
  const b = Skia.PathBuilder.Make();
  const bumps = 16;
  for (let i = 0; i <= bumps; i += 1) {
    const a = (i / bumps) * Math.PI * 2;
    const p = [Math.cos(a) * r, Math.sin(a) * r];
    if (i === 0) {
      b.moveTo(p[0], p[1]);
      continue;
    }
    const m = a - Math.PI / bumps;
    b.quadTo(Math.cos(m) * r * 1.16, Math.sin(m) * r * 1.16, p[0], p[1]);
  }
  return b.close().build();
}

/** A repeatable pseudo-random number in [0, 1). */
export function hash01(n: number) {
  'worklet';
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

/** The outermost edge of `outline` along `RING` rays from its middle, as x, y pairs. */
function sampleRing(outline: SkPath, extent: number) {
  const out: number[] = [];
  for (let i = 0; i < RING; i += 1) {
    const a = -Math.PI / 2 + (i / RING) * Math.PI * 2;
    const cx = Math.cos(a);
    const cy = Math.sin(a);
    // March in from outside to the first point inside, then halve the step to the edge.
    let inside = 0;
    for (let r = extent + 2; r > 0; r -= 1) {
      if (outline.contains(cx * r, cy * r)) {
        inside = r;
        break;
      }
    }
    let lo = inside;
    let hi = inside + 1;
    for (let k = 0; k < 8; k += 1) {
      const mid = (lo + hi) / 2;
      if (outline.contains(cx * mid, cy * mid)) lo = mid;
      else hi = mid;
    }
    out.push(cx * lo, cy * lo);
  }
  return out;
}

/** The ring of a crumpled ball of radius 1 on the same rays: lumpy, with a few deep folds. */
function ballRing(seed: number) {
  const raw: number[] = [];
  for (let i = 0; i < RING; i += 1) raw.push(0.8 + 0.3 * hash01(seed * 7.13 + i * 1.91));
  const out: number[] = [];
  for (let i = 0; i < RING; i += 1) {
    const a = -Math.PI / 2 + (i / RING) * Math.PI * 2;
    let f = 0.5 * raw[i] + 0.25 * (raw[(i + RING - 1) % RING] + raw[(i + 1) % RING]);
    // A fold every so often bites into the ball.
    if (i % 7 === 3) f -= 0.13;
    if (i % 7 === 0) f += 0.06;
    out.push(Math.cos(a) * f, Math.sin(a) * f);
  }
  return out;
}

/** How light (+) or dark (−) each facet of the ball is. */
function facetShades(seed: number) {
  const out: number[] = [];
  for (let j = 0; j < RING / FACET_STEP; j += 1) out.push(hash01(seed * 3.7 + j * 5.3) * 2 - 1);
  return out;
}

export type Shape = { art: SkPath; outline: SkPath; extent: number; ring: number[]; ball: number[]; shades: number[] };

/** The sticker's own art shape, its outline with the white border, and the rings it crumples along. */
export function shapes(s: Sticker, index: number): Shape {
  const [a, b] = s.size;
  let art: SkPath;
  switch (s.kind) {
    case 'circle':
    case 'smiley':
      art = Skia.PathBuilder.Make().addCircle(0, 0, a).build();
      break;
    case 'rounded':
      art = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(-a / 2, -b / 2, a, b), 18, 18)).build();
      break;
    case 'star':
      art = starPath(a);
      break;
    case 'heart':
      art = heartPath(a, b);
      break;
    case 'bubble':
      art = bubblePath(a, b);
      break;
    default:
      art = badgePath(a);
  }
  const border = Skia.Path.Stroke(art, { width: BORDER * 2, join: StrokeJoin.Round, cap: StrokeCap.Round });
  const outline = (border && Skia.Path.MakeFromOp(art, border, PathOp.Union)) ?? art;
  const r = outline.getBounds();
  const extent = Math.max(1, Math.hypot(r.x, r.y), Math.hypot(r.x + r.width, r.y + r.height));
  return { art, outline, extent, ring: sampleRing(outline, extent), ball: ballRing(index + 1), shades: facetShades(index + 1) };
}
