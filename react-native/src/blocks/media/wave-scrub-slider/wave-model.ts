import { ClipOp, PaintStyle, type SkCanvas, type SkPath, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

/** The self-playing loop, in seconds. */
export const LOOP = 8;
/** How many times a second the wave's crests travel to the right (a whole number per loop). */
export const DRIFT = 1;

/** The scripted finger: (time, value) keys, eased in and out between them. */
const KEYS: [number, number][] = [
  [0, 0],
  [0.6, 0],
  [2.2, 0.62],
  [2.9, 0.62],
  [3.9, 1],
  [5.0, 1],
  [6.6, 0],
  [8.0, 0],
];

export function easeInOut(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return u * u * (3 - 2 * u);
}

export function scripted(t: number) {
  'worklet';
  for (let i = 1; i < KEYS.length; i++) {
    if (t <= KEYS[i][0]) {
      const [t0, v0] = KEYS[i - 1];
      const [t1, v1] = KEYS[i];
      return v0 + (v1 - v0) * easeInOut((t - t0) / (t1 - t0));
    }
  }
  return 0;
}

/** Cycles of the wave from the left end of the track to u (0…1). */
export function cycles(u: number, v: number) {
  'worklet';
  return (0.6 + 0.4 * v) * (2.0 * u + 6.5 * u * u);
}

/** The wave's height above the centre line at u, for a thumb at v and a drift phase (in cycles). */
export function waveHeight(u: number, v: number, phase: number, width: number) {
  'worklet';
  const x = u * width;
  const thumb = v * width;
  const amp = x <= thumb ? 15 * v * smoothstep(0, 0.3, u) : 1.2 + 5 * v;
  const taper = smoothstep(3, 18, Math.abs(x - thumb)) * smoothstep(0, 10, width - x) * smoothstep(0, 6, x);
  return amp * taper * Math.sin(2 * Math.PI * (cycles(u, v) - phase));
}

/** The photo's grade: contrast 1 → 1.45 around mid grey, saturation 1 → 1.3; a 4 × 5 matrix, offsets in 0…1. */
export function grade(v: number) {
  'worklet';
  const c = 1 + 0.45 * v;
  const s = 1 + 0.3 * v;
  const lr = 0.2126 * (1 - s);
  const lg = 0.7152 * (1 - s);
  const lb = 0.0722 * (1 - s);
  const o = 0.5 * (1 - c);
  return [c * (lr + s), c * lg, c * lb, 0, o, c * lr, c * (lg + s), c * lb, 0, o, c * lr, c * lg, c * (lb + s), 0, o, 0, 0, 0, 1, 0];
}

/** The value as the panel reads it: 0, then +1 … +100. */
export function label(v: number) {
  'worklet';
  const n = Math.round(v * 100);
  return n === 0 ? '0' : `+${n}`;
}

/** A colour from 0xRRGGBB and an alpha, as Skia wants it. */
export function rgb(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

const FAR = [
  [0.05, 1.3, 0.4],
  [0.03, 3.1, 1.2],
  [0.012, 7.7, 0.3],
];
const MID = [
  [0.06, 0.9, 2.2],
  [0.025, 2.3, 0.6],
  [0.01, 5.9, 1.9],
];
const CLOUDS = [
  [0.06, 0.47, 0.42, 0.35],
  [0.3, 0.52, 0.5, 0.28],
  [0.62, 0.43, 0.3, 0.22],
  [0.7, 0.58, 0.26, 0.3],
];
const PINES = [
  [0.04, 0.84, 0.3],
  [0.1, 0.845, 0.4],
  [0.165, 0.85, 0.24],
  [0.235, 0.86, 0.33],
  [0.3, 0.875, 0.2],
  [0.885, 0.875, 0.26],
  [0.95, 0.87, 0.34],
];

function fill(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

/** A pine: a stack of five tiers that widen downwards, on a thin trunk. */
function pine(x: number, base: number, height: number) {
  'worklet';
  const p = Skia.Path.Make();
  const top = base - height;
  p.moveTo(x, top);
  const right: [number, number][] = [];
  for (let i = 1; i <= 5; i++) {
    const y = top + (height * 0.86 * i) / 5;
    const wi = (height * 0.2 * i) / 5 + 1.5;
    right.push([x + wi, y]);
    if (i < 5) right.push([x + wi * 0.35, y - height * 0.02]);
  }
  for (const pt of right) p.lineTo(pt[0], pt[1]);
  p.lineTo(x + 1.2, top + height * 0.86);
  p.lineTo(x + 1.2, base);
  p.lineTo(x - 1.2, base);
  p.lineTo(x - 1.2, top + height * 0.86);
  for (let i = right.length - 1; i >= 0; i--) p.lineTo(2 * x - right[i][0], right[i][1]);
  p.close();
  return p;
}

/** Draws the photo being edited (a lake at dusk) into a w × h rectangle at the origin. */
export function drawPhoto(canvas: SkCanvas, w: number, h: number) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, w, h);
  const horizon = 0.74 * h;
  // Sky.
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: h },
      [rgb(0x16213f), rgb(0x4b3a6b), rgb(0xc4607a), rgb(0xf4a261), rgb(0xffd59e)],
      [0, 0.38, 0.56, 0.68, 0.74],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(full, sky);
  // Stars in the upper sky, fixed places (a hash of the index).
  for (let i = 0; i < 28; i++) {
    const fx = (i * 0.618034) % 1;
    const fy = (i * 0.381966 + 0.13 * (i % 3)) % 1;
    const r = i % 5 === 0 ? 1.3 : 0.8;
    const a = Math.min(Math.max(0.35 + (0.4 * (i % 4)) / 3 - 0.3 * fy, 0), 1);
    canvas.drawCircle(fx * w, (0.08 + 0.3 * fy) * h, r, fill(Float32Array.of(1, 1, 1, a)));
  }
  // Sun and its glow.
  const sx = 0.64 * w;
  const sy = 0.665 * h;
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: sx, y: sy }, 0.55 * w, [rgb(0xffc27a, 0.85), rgb(0xffc27a, 0)], null, TileMode.Clamp));
  canvas.drawRect(full, glow);
  canvas.drawCircle(sx, sy, 0.075 * w, fill(rgb(0xfff3d6)));
  // Streaks of cloud.
  for (const c of CLOUDS) canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(c[0] * w, c[1] * h, c[2] * w, 5), 2.5, 2.5), fill(rgb(0xf7b48a, c[3])));
  // Far and middle ridges.
  const ridgePath = (waves: number[][], base: number) => {
    const p = Skia.Path.Make();
    p.moveTo(0, horizon);
    for (let x = 0; x <= w + 4; x += 4) {
      let y = base * h;
      for (const wv of waves) y -= wv[0] * h * Math.sin((x / w) * 2 * Math.PI * wv[1] + wv[2]);
      p.lineTo(x, y);
    }
    p.lineTo(w + 4, horizon);
    p.close();
    return p;
  };
  canvas.drawPath(ridgePath(FAR, 0.66), fill(rgb(0xa45a7c)));
  const midRidge = ridgePath(MID, 0.7);
  canvas.drawPath(midRidge, fill(rgb(0x5d3160)));
  // Lake.
  const lake = Skia.XYWHRect(0, horizon, w, h - horizon);
  const water = Skia.Paint();
  water.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: horizon }, { x: 0, y: h }, [rgb(0xe59a6f), rgb(0x3d2447), rgb(0x1a1022)], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.drawRect(lake, water);
  // The middle ridge mirrored in the water.
  canvas.save();
  canvas.clipRect(lake, ClipOp.Intersect, true);
  canvas.translate(0, 2 * horizon);
  canvas.scale(1, -1);
  canvas.drawPath(midRidge, fill(rgb(0x5d3160, 0.45)));
  canvas.restore();
  // The sun's path on the water.
  let i = 0;
  for (let y = horizon + 5; y < 0.93 * h; y += 7) {
    const k = (y - horizon) / (0.93 * h - horizon);
    const lw = (0.16 - 0.12 * k) * w * (i % 2 === 0 ? 1 : 0.7);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx - lw / 2, y, lw, 2.4), 1.2, 1.2), fill(rgb(0xffe2b0, 0.8 - 0.6 * k)));
    i++;
  }
  // The near shore with its pines.
  const ink = fill(rgb(0x120a18));
  const shore = Skia.Path.Make();
  shore.moveTo(0, 0.83 * h);
  shore.cubicTo(0.22 * w, 0.84 * h, 0.36 * w, 0.9 * h, 0.46 * w, h);
  shore.lineTo(0, h);
  shore.close();
  canvas.drawPath(shore, ink);
  const right = Skia.Path.Make();
  right.moveTo(w, 0.86 * h);
  right.cubicTo(0.9 * w, 0.87 * h, 0.82 * w, 0.93 * h, 0.78 * w, h);
  right.lineTo(w, h);
  right.close();
  canvas.drawPath(right, ink);
  for (const p of PINES) canvas.drawPath(pine(p[0] * w, p[1] * h + 6, p[2] * h), ink);
  // Vignette: clear inside 0.3 h of the centre, 45 % black at 0.85 h.
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: w / 2, y: h * 0.55 },
      0.85 * h,
      [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.45)],
      [0.3 / 0.85, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(full, vignette);
}

export type Icon =
  | 'back'
  | 'undo'
  | 'redo'
  | 'more'
  | 'auto'
  | 'light'
  | 'contrast'
  | 'color'
  | 'vignette'
  | 'grain'
  | 'sharpen'
  | 'crop'
  | 'brush'
  | 'dial'
  | 'layers'
  | 'sky';

/** An icon drawn in a 24 × 24 box: what to stroke (1.6 wide, round) and what to fill. */
function iconPaths(icon: Icon): [SkPath, SkPath] {
  'worklet';
  const s = Skia.Path.Make();
  const f = Skia.Path.Make();
  const line = (pts: number[], close = false) => {
    s.moveTo(pts[0], pts[1]);
    for (let i = 2; i < pts.length; i += 2) s.lineTo(pts[i], pts[i + 1]);
    if (close) s.close();
  };
  const circle = (x: number, y: number, r: number, filled = false) => (filled ? f : s).addOval(Skia.XYWHRect(x - r, y - r, 2 * r, 2 * r));
  const star = (x: number, y: number, r: number) => {
    const k = r * 0.24;
    f.moveTo(x, y - r);
    f.quadTo(x + k, y - k, x + r, y);
    f.quadTo(x + k, y + k, x, y + r);
    f.quadTo(x - k, y + k, x - r, y);
    f.quadTo(x - k, y - k, x, y - r);
    f.close();
  };
  switch (icon) {
    case 'back':
      line([15, 5, 8, 12, 15, 19]);
      break;
    case 'undo':
      line([9, 5, 5, 9, 9, 13]);
      s.moveTo(5, 9);
      s.lineTo(14, 9);
      s.arcToOval(Skia.XYWHRect(9, 9, 10, 10), -90, 180, false);
      s.lineTo(10, 19);
      break;
    case 'redo':
      line([15, 5, 19, 9, 15, 13]);
      s.moveTo(19, 9);
      s.lineTo(10, 9);
      s.arcToOval(Skia.XYWHRect(5, 9, 10, 10), -90, -180, false);
      s.lineTo(14, 19);
      break;
    case 'more':
      circle(12, 12, 9);
      for (const x of [8, 12, 16]) circle(x, 12, 1.3, true);
      break;
    case 'auto':
      star(10.5, 13.5, 7);
      star(18, 5.5, 3);
      break;
    case 'light':
      circle(12, 12, 4);
      for (let k = 0; k < 8; k++) {
        const a = (k * Math.PI) / 4;
        line([12 + 7 * Math.cos(a), 12 + 7 * Math.sin(a), 12 + 9.5 * Math.cos(a), 12 + 9.5 * Math.sin(a)]);
      }
      break;
    case 'contrast':
      circle(12, 12, 8);
      f.moveTo(12, 4);
      f.arcToOval(Skia.XYWHRect(4, 4, 16, 16), -90, -180, false);
      f.close();
      break;
    case 'color':
      circle(9, 10, 5);
      circle(15, 10, 5);
      circle(12, 15, 5);
      break;
    case 'vignette':
      s.addRRect(Skia.RRectXY(Skia.XYWHRect(4, 4, 16, 16), 4, 4));
      s.addOval(Skia.XYWHRect(8, 8.5, 8, 7));
      break;
    case 'grain':
      for (const x of [7, 12, 17]) for (const y of [7, 12, 17]) circle(x, y, 1.4, true);
      break;
    case 'sharpen':
      line([12, 4, 20, 19, 4, 19], true);
      break;
    case 'crop':
      line([7, 3, 7, 17, 21, 17]);
      line([3, 7, 17, 7, 17, 21]);
      break;
    case 'brush':
      line([5, 15, 14, 6, 19, 11, 10, 20], true);
      line([9.5, 10.5, 14.5, 15.5]);
      line([10, 20, 20, 20]);
      break;
    case 'dial':
      circle(12, 13, 8);
      line([12, 13, 16, 9]);
      circle(12, 13, 1.6, true);
      line([12, 2.5, 12, 4]);
      break;
    case 'layers':
      s.addRRect(Skia.RRectXY(Skia.XYWHRect(4, 8, 12, 12), 2, 2));
      s.addRRect(Skia.RRectXY(Skia.XYWHRect(8, 4, 12, 12), 2, 2));
      break;
    case 'sky':
      s.moveTo(7, 15);
      s.arcToOval(Skia.XYWHRect(7, 10, 10, 10), 180, 180, false);
      line([3, 15, 21, 15]);
      line([12, 5, 12, 7]);
      line([5.6, 8.6, 7, 10]);
      line([18.4, 8.6, 17, 10]);
      line([6, 19, 18, 19]);
      break;
  }
  return [s, f];
}

/** Draws an icon centred at a point, `size` pt square. */
export function drawIcon(canvas: SkCanvas, icon: Icon, cx: number, cy: number, color: Float32Array, size = 24) {
  'worklet';
  const [s, f] = iconPaths(icon);
  canvas.save();
  canvas.translate(cx - size / 2, cy - size / 2);
  canvas.scale(size / 24, size / 24);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(1.6);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setStrokeJoin(StrokeJoin.Round);
  stroke.setColor(color);
  canvas.drawPath(s, stroke);
  canvas.drawPath(f, fill(color));
  canvas.restore();
}
