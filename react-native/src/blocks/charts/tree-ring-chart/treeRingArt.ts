import { BlendMode, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas } from '@shopify/react-native-skia';

import { RING_COUNT, clamp01 } from './treeRingMotion';

/** Hero geometry in screen points: the hero's rect (ox, oy, w, h) and the slice inside it. */
export type TreeGeo = { ox: number; oy: number; w: number; h: number; cx: number; cy: number; slice: number; bark: number; pith: number; wood: number; unit: number };

export function makeTreeGeo(ox: number, oy: number, w: number, h: number): TreeGeo {
  const slice = Math.max(60, Math.min(w * 0.41, h / 2 - 30));
  const bark = slice * 0.068;
  return { ox, oy, w, h, cx: ox + w / 2, cy: oy + h / 2 - 10, slice, bark, pith: Math.max(3, slice * 0.025), wood: slice - bark, unit: slice / 161 };
}

/**
 * The chart's guides behind the slice: one faint dashed circle per year at its full-grown radius
 * (visible while the rings have shrunk, so the hero is never empty), and a small year tag on the
 * outermost one. Static, recorded once.
 */
export function drawGuides(canvas: SkCanvas, g: TreeGeo, full: number[], width: number, height: number) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1);
  paint.setColor(Skia.Color('rgba(240, 220, 190, 0.2)'));
  paint.setPathEffect(Skia.PathEffect.MakeDash([2, 5], 0));
  for (let i = 0; i < RING_COUNT; i++) canvas.drawCircle(g.cx, g.cy, full[i] + (i === RING_COUNT - 1 ? g.bark : 0), paint);
  // A soft warm pool of light on the table under the slice.
  const pool = Skia.Paint();
  pool.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: g.cx, y: g.cy + g.slice * 0.08 },
      g.slice * 1.45,
      [Skia.Color('rgba(150, 96, 52, 0.22)'), Skia.Color('rgba(110, 66, 36, 0.08)'), Skia.Color('rgba(60, 36, 22, 0)')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), pool);
}

/** Middle radius of the (continuous) selected ring, from the pose's ring radii. */
export function selectedMid(radii: number[], pith: number, sel: number) {
  'worklet';
  const i0 = Math.max(0, Math.min(RING_COUNT - 1, Math.floor(sel)));
  const i1 = Math.min(RING_COUNT - 1, i0 + 1);
  const f = clamp01(sel - i0);
  const m0 = ((i0 === 0 ? pith : radii[i0 - 1]) + radii[i0]) / 2;
  const m1 = ((i1 === 0 ? pith : radii[i1 - 1]) + radii[i1]) / 2;
  return m0 + (m1 - m0) * f;
}

/**
 * The annotation: a ringed dot on the selected year, straight below the pith, and a thin leader
 * that drops from it to the label under the slice.
 */
export function drawLeader(canvas: SkCanvas, g: TreeGeo, mid: number, glow: number) {
  'worklet';
  const x = g.cx;
  const y = g.cy + mid;
  const end = g.oy + g.h - 4;
  const a = 0.35 + 0.65 * glow;
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1.25);
  line.setStrokeCap(StrokeCap.Round);
  line.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y },
      { x, y: end },
      [Skia.Color(`rgba(255, 214, 150, ${0.95 * a})`), Skia.Color(`rgba(255, 196, 120, ${0.55 * a})`), Skia.Color('rgba(255, 190, 110, 0.15)')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawLine(x, y + 6, x, end, line);
  const halo = Skia.Paint();
  halo.setBlendMode(BlendMode.Plus);
  halo.setShader(
    Skia.Shader.MakeRadialGradient({ x, y }, 18, [Skia.Color(`rgba(255, 180, 90, ${0.45 * a})`), Skia.Color('rgba(255, 150, 60, 0)')], [0, 1], TileMode.Clamp),
  );
  canvas.drawCircle(x, y, 18, halo);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1.5);
  ring.setColor(Skia.Color(`rgba(255, 232, 196, ${a})`));
  canvas.drawCircle(x, y, 5.5, ring);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(Skia.Color(`rgba(255, 246, 228, ${a})`));
  canvas.drawCircle(x, y, 2.6, dot);
}
