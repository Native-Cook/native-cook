import { BlurStyle, PaintStyle, type SkCanvas, Skia, StrokeCap, TileMode, vec } from '@shopify/react-native-skia';

// The dish, drawn from vectors in units of the plate's diameter, centred on 0, 0.
const PLATE_LIGHT = '#F6F2EA';
const PLATE_DARK = '#E1D9CB';
const PLATE_RIM = '#D3CABA';
const RICE = '#FBF8F1';
const SALMON_LIGHT = '#E2703F';
const SALMON_DARK = '#A1401C';
const GLAZE = '#7A2E14';
const SEED = '#FFF8EA';
const CUCUMBER = '#D6E8B0';
const CUCUMBER_RIND = '#6F9A3E';

// Edamame and herbs: x, y, radius, and which of the two greens.
const GREENS: [number, number, number, number][] = [
  [-0.27, -0.12, 0.045, 0],
  [-0.2, 0.17, 0.04, 1],
  [-0.06, 0.27, 0.045, 0],
  [0.14, 0.24, 0.038, 1],
  [0.27, 0.08, 0.045, 0],
  [0.24, -0.17, 0.04, 1],
  [0.05, -0.28, 0.042, 0],
  [-0.14, -0.25, 0.036, 1],
  [-0.3, 0.05, 0.034, 1],
];
const GREEN_COLORS = ['#8DB255', '#5F8F34'];
// Cucumber slices: x, y, radius.
const SLICES: [number, number, number][] = [
  [0.2, 0.15, 0.07],
  [-0.22, -0.02, 0.065],
];
// Sesame seeds on the fillet, in the fillet's own turned frame: x, y, angle in degrees.
const SEEDS: [number, number, number][] = [
  [-0.13, -0.04, 20],
  [-0.07, 0.03, -30],
  [-0.02, -0.05, 60],
  [0.04, 0.04, 10],
  [0.09, -0.03, -50],
  [0.14, 0.02, 35],
  [-0.1, 0.06, 80],
  [0.01, 0.0, -10],
];
const FILLET_TURN = -32;

function color(hex: string, alpha: number) {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, Math.max(0, Math.min(1, alpha)));
}

/** The plate of salmon, rice and greens, `d` points across, centred on `cx`, `cy`. */
export function drawPlate(canvas: SkCanvas, cx: number, cy: number, d: number, alpha: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(0.035 * d, 0.5), true));
  shadow.setColor(color('#000000', 0.14 * alpha));
  canvas.drawCircle(cx, cy + 0.04 * d, 0.48 * d, shadow);

  paint.setShader(Skia.Shader.MakeRadialGradient(vec(cx - 0.12 * d, cy - 0.14 * d), 0.62 * d, [color(PLATE_LIGHT, alpha), color(PLATE_DARK, alpha)], null, TileMode.Clamp));
  canvas.drawCircle(cx, cy, 0.5 * d, paint);
  paint.setShader(null);

  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(Math.max(0.012 * d, 0.5));
  rim.setColor(color(PLATE_RIM, 0.8 * alpha));
  canvas.drawCircle(cx, cy, 0.42 * d, rim);

  paint.setColor(color(RICE, alpha));
  canvas.drawCircle(cx, cy, 0.36 * d, paint);

  for (let i = 0; i < SLICES.length; i++) {
    const [x, y, r] = SLICES[i];
    paint.setColor(color(CUCUMBER_RIND, alpha));
    canvas.drawCircle(cx + x * d, cy + y * d, r * d, paint);
    paint.setColor(color(CUCUMBER, alpha));
    canvas.drawCircle(cx + x * d, cy + y * d, r * d * 0.8, paint);
  }
  for (let i = 0; i < GREENS.length; i++) {
    const [x, y, r, k] = GREENS[i];
    paint.setColor(color(GREEN_COLORS[k], alpha));
    canvas.drawCircle(cx + x * d, cy + y * d, r * d, paint);
  }

  // The fillet, turned, with a glaze and sesame seeds.
  canvas.save();
  canvas.translate(cx + 0.02 * d, cy - 0.02 * d);
  canvas.rotate(FILLET_TURN, 0, 0);
  const w = 0.44 * d;
  const h = 0.2 * d;
  paint.setShader(Skia.Shader.MakeLinearGradient(vec(0, -h / 2), vec(0, h / 2), [color(SALMON_LIGHT, alpha), color(SALMON_DARK, alpha)], null, TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), 0.09 * d, 0.09 * d), paint);
  paint.setShader(null);
  const glaze = Skia.Paint();
  glaze.setAntiAlias(true);
  glaze.setStyle(PaintStyle.Stroke);
  glaze.setStrokeCap(StrokeCap.Round);
  glaze.setStrokeWidth(Math.max(0.012 * d, 0.4));
  glaze.setColor(color(GLAZE, 0.45 * alpha));
  for (let k = -2; k <= 2; k++) {
    const x = k * 0.07 * d;
    canvas.drawLine(x - 0.03 * d, -h * 0.38, x + 0.03 * d, h * 0.38, glaze);
  }
  paint.setColor(color(SEED, alpha));
  for (let i = 0; i < SEEDS.length; i++) {
    const [x, y, a] = SEEDS[i];
    canvas.save();
    canvas.translate(x * d, y * d);
    canvas.rotate(a, 0, 0);
    canvas.drawOval(Skia.XYWHRect(-0.014 * d, -0.007 * d, 0.028 * d, 0.014 * d), paint);
    canvas.restore();
  }
  canvas.restore();
}
