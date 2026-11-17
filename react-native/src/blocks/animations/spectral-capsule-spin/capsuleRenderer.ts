import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkPaint,
  type SkShader,
} from '@shopify/react-native-skia';

import {
  UX,
  UY,
  endOn,
  hash,
  lightFor,
  makeGeometry,
  nearCap,
  nearRimArc,
  nearSeamFront,
  ring,
  ringBack,
  silhouette,
  smoothstep,
  towardsPole,
} from './capsuleGeometry';

/*
 Draws one frame of the capsule with Skia's canvas, layer for layer like the Swift reference:
 the glass body clipped to the silhouette (fill, frost, fibres, blue inner glow, the lit cap, the
 dispersion band, the far rings), then the rim (warm fringe, white bloom, spectral edge, core).
*/

export const PALETTE = {
  background: 0x040508,
  vignette: 0x0b0e18,
  body: 0x05070d,
  frost: 0xc8d7eb,
  fibre: 0xb9c9e6,
  innerGlow: 0x2d4dff,
  rimCore: 0xf4f7ff,
  warmFringe: 0xffb458,
  coolFringe: 0x8a6bff,
  capBase: 0xece8ff,
  causticCore: 0xffe24a,
  causticMid: 0xffb040,
  causticEdge: 0xff6a3d,
  crescent: 0xff4d5a,
  seam: 0x48e0d6,
  farSeam: 0x9fb6ff,
  halo: 0x1a2c7a,
  rings: [0xfff1d6, 0xe6f27a, 0x5c8dff],
  /** The dispersion band, red round to red. */
  spectrum: [0xff3b5c, 0xff9f1c, 0xffe14d, 0x4be38a, 0x29c6ff, 0x5a5bff, 0xc64bff, 0xff3b5c],
};

export const FIBRE_SIZE = 480;
const FIBRE_COUNT = 420;
const FIBRE_SCALE = 2;

/** A colour from 0xRRGGBB and an alpha. */
export function rgba(hex: number, alpha: number): SkColor {
  'worklet';
  const a = Math.min(Math.max(alpha, 0), 1);
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function pathOf(points: number[], closed: boolean) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
  if (closed) p.close();
  return p.detach();
}

function solid(hex: number, alpha: number, stroke: number, blur: number): SkPaint {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(rgba(hex, alpha));
  if (stroke > 0) {
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(stroke);
    paint.setStrokeCap(StrokeCap.Round);
    paint.setStrokeJoin(StrokeJoin.Round);
  }
  if (blur > 0) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return paint;
}

function shaded(shader: SkShader, alpha: number, stroke: number, blur: number): SkPaint {
  'worklet';
  const paint = solid(0x000000, 1, stroke, blur);
  paint.setShader(shader);
  paint.setAlphaf(Math.min(Math.max(alpha, 0), 1));
  return paint;
}

function spectrumTwice(): SkColor[] {
  'worklet';
  const out: SkColor[] = [];
  const s = PALETTE.spectrum;
  for (let i = 0; i < s.length; i += 1) out.push(rgba(s[i], 1));
  for (let i = 1; i < s.length; i += 1) out.push(rgba(s[i], 1));
  return out;
}

function sweep(cx: number, cy: number, angle: number): SkShader {
  'worklet';
  const m = Skia.Matrix().translate(cx, cy).rotate(angle).translate(-cx, -cy);
  return Skia.Shader.MakeSweepGradient(cx, cy, spectrumTwice(), null, TileMode.Clamp, m);
}

/** Faint frosted fibres inside the glass: seeded strokes drawn once into an image (JS thread). */
export function makeFibres(): SkImage | null {
  const px = FIBRE_SIZE * FIBRE_SCALE;
  const surface = Skia.Surface.Make(px, px);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(FIBRE_SCALE, FIBRE_SCALE);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeCap(StrokeCap.Round);
  paint.setStrokeWidth(0.6);
  for (let n = 0; n < FIBRE_COUNT; n += 1) {
    const x = hash(n, 1) * FIBRE_SIZE;
    const y = hash(n, 2) * FIBRE_SIZE;
    let length = 6 + 20 * hash(n, 3);
    const angle = 2 * Math.PI * hash(n, 4);
    const bend = (hash(n, 5) - 0.5) * length * 0.6;
    let alpha = 0.12 + 0.2 * hash(n, 6);
    if (n % 13 === 0) {
      length *= 2.2;
      alpha = 0.38;
    }
    const dx = (Math.cos(angle) * length) / 2;
    const dy = (Math.sin(angle) * length) / 2;
    paint.setColor(rgba(PALETTE.fibre, alpha));
    const p = Skia.PathBuilder.Make();
    p.moveTo(x - dx, y - dy);
    p.quadTo(x - Math.sin(angle) * bend, y + Math.cos(angle) * bend, x + dx, y + dy);
    canvas.drawPath(p.detach(), paint);
  }
  surface.flush();
  const snapshot = surface.makeImageSnapshot();
  return snapshot.makeNonTextureImage() ?? snapshot;
}

/** The page behind the capsule: a vignette and a blue halo that warms with the caustic. */
export function drawBackdrop(canvas: SkCanvas, width: number, height: number, centreY: number, warm: number) {
  'worklet';
  const vignette = Skia.Shader.MakeRadialGradient(
    { x: width / 2, y: centreY },
    height * 0.62,
    [rgba(PALETTE.vignette, 1), rgba(PALETTE.background, 1)],
    null,
    TileMode.Clamp,
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), shaded(vignette, 1, 0, 0));
  const halo = Skia.Shader.MakeRadialGradient(
    { x: width / 2, y: centreY },
    260,
    [rgba(PALETTE.halo, 0.1 + 0.16 * warm), rgba(PALETTE.halo, 0)],
    null,
    TileMode.Clamp,
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), shaded(halo, 1, 0, 0));
}

/** Two small capsules centred on (x, y): each lights when its end of the big one faces the viewer. */
export function drawIndicator(canvas: SkCanvas, x: number, y: number, phi: number) {
  'worklet';
  const lit = [endOn(phi, Math.PI / 2), endOn(phi, (3 * Math.PI) / 2)];
  for (let i = 0; i < 2; i += 1) {
    const rect = Skia.RRectXY(Skia.XYWHRect(x - 27 + i * 32, y - 4, 22, 8), 4, 4);
    canvas.drawRRect(rect, solid(0x4d7bff, 0.9 * lit[i], 0, 6));
    canvas.drawRRect(rect, solid(0x232836, 1, 0, 0));
    canvas.drawRRect(rect, solid(0xdce6ff, lit[i], 0, 0));
  }
}

/** The capsule for one pose, centred in a box of width × height whose top-left is the origin. */
export function drawCapsule(
  canvas: SkCanvas,
  fibres: SkImage | null,
  width: number,
  height: number,
  phi: number,
  hueShift: number,
  warmGain: number,
  radius: number,
  halfLength: number,
) {
  'worklet';
  const cx = width / 2;
  const cy = height / 2;
  const g = makeGeometry(cx, cy, radius, halfLength, phi);
  const light = lightFor(g.facing);
  const warm = light.warm * warmGain;
  const sil = pathOf(silhouette(g), true);
  const cap = pathOf(nearCap(g), true);
  const nearR = g.nearR;
  const spectralAngle = -Math.PI / 4 + phi + hueShift;

  // The glass body, everything clipped to the silhouette.
  canvas.save();
  canvas.clipPath(sil, ClipOp.Intersect, true);
  canvas.drawPath(sil, solid(PALETTE.body, 0.92, 0, 0));

  const frost = light.frost;
  if (frost > 0.005) {
    const span = radius * 1.1;
    const shader = Skia.Shader.MakeLinearGradient(
      { x: cx - g.acrossX * span, y: cy - g.acrossY * span },
      { x: cx + g.acrossX * span, y: cy + g.acrossY * span },
      [rgba(PALETTE.frost, 0.55 * frost), rgba(PALETTE.frost, 0.16 * frost), rgba(PALETTE.frost, 0.55 * frost)],
      [0, 0.5, 1],
      TileMode.Clamp,
    );
    canvas.drawPath(sil, shaded(shader, 1, 0, 0));
  }

  const s = FIBRE_SIZE;
  if (fibres) {
    const src = Skia.XYWHRect(0, 0, fibres.width(), fibres.height());
    const texture = Skia.Paint();
    texture.setAlphaf(Math.min(1, 0.8 + 0.6 * frost));
    canvas.save();
    canvas.translate(cx, cy);
    canvas.rotate(-45, 0, 0);
    canvas.scale(Math.max(Math.abs(g.cosP), 0.32), 1);
    canvas.drawImageRect(fibres, src, Skia.XYWHRect(-s / 2 + g.sinP * 30, -s / 2, s, s), texture);
    canvas.restore();
  }

  canvas.drawPath(sil, solid(PALETTE.innerGlow, 0.9 * light.rim, 34, 12));

  // The near cap catching the light: a pale lens, a red crescent at the pole, a warm caustic.
  if (warm > 0.005 || light.fibres > 0.005) {
    canvas.save();
    canvas.clipPath(cap, ClipOp.Intersect, true);
    canvas.drawPath(cap, solid(PALETTE.capBase, 0.8 * smoothstep(0, 0.55, warm), 0, 0));
    const red = towardsPole(g, 0.55 + 0.3 * Math.abs(g.cosP));
    const redRadius = 0.75 * nearR;
    const redShader = Skia.Shader.MakeRadialGradient(
      { x: red[0], y: red[1] },
      redRadius,
      [rgba(PALETTE.crescent, 0.85 * warm), rgba(PALETTE.crescent, 0)],
      null,
      TileMode.Clamp,
    );
    canvas.drawCircle(red[0], red[1], redRadius, shaded(redShader, 1, 0, 0));
    const hot = towardsPole(g, 0.12 + 0.35 * Math.abs(g.cosP));
    const hotShader = Skia.Shader.MakeRadialGradient(
      { x: hot[0], y: hot[1] },
      nearR,
      [rgba(PALETTE.causticCore, warm), rgba(PALETTE.causticMid, 0.9 * warm), rgba(PALETTE.causticEdge, 0.5 * warm), rgba(PALETTE.causticEdge, 0)],
      [0, 0.4, 0.7, 1],
      TileMode.Clamp,
    );
    canvas.drawCircle(hot[0], hot[1], nearR, shaded(hotShader, 1, 0, 0));
    if (light.fibres > 0.005 && fibres) {
      const src = Skia.XYWHRect(0, 0, fibres.width(), fibres.height());
      const lit = Skia.Paint();
      lit.setAlphaf(light.fibres);
      canvas.save();
      canvas.translate(g.nearX, g.nearY);
      canvas.rotate(45, 0, 0);
      canvas.drawImageRect(fibres, src, Skia.XYWHRect(-s / 4, -s / 4, s / 2, s / 2), lit);
      canvas.restore();
    }
    canvas.restore();
  }

  // The dispersion band: a crescent of spectrum along the cap's rim, and along the seam.
  const rainbow = light.rainbow;
  const seamBand = Math.max(0.7 * rainbow, 0.9 * warm);
  if (rainbow > 0.005 || seamBand > 0.005) {
    canvas.save();
    canvas.clipPath(cap, ClipOp.Intersect, true);
    const shader = sweep(g.nearX, g.nearY, spectralAngle);
    canvas.drawPath(pathOf(nearRimArc(g, nearR * 0.22), false), shaded(shader, rainbow, nearR * 0.36, 4));
    canvas.drawPath(pathOf(nearSeamFront(g), false), shaded(shader, seamBand, nearR * 0.16, 4));
    canvas.restore();
    canvas.drawPath(pathOf(nearSeamFront(g), false), solid(PALETTE.seam, 0.8 * warm, 2, 0));
  }

  // The far seam and the stacked rings that read through the glass when it points at us.
  const farSeam = 0.3 * g.facing * (1 - light.rings);
  if (farSeam > 0.01) canvas.drawPath(pathOf(ring(g, 0), true), solid(PALETTE.farSeam, farSeam, 1.5, 0));
  if (light.rings > 0.005) {
    const lambdas = [0, 0.35, 0.7];
    for (let i = 0; i < 3; i += 1) {
      canvas.drawPath(pathOf(ringBack(g, lambdas[i]), false), solid(PALETTE.rings[i], light.rings * (0.95 - 0.15 * i), 4.5 - i, 1.2));
    }
    canvas.drawPath(pathOf(ring(g, 1), true), solid(PALETTE.rimCore, 0.8 * light.rings, 2.5, 1.2));
  }
  canvas.restore();

  // The rim: a warm outer fringe (strongest bottom-left), a soft white bloom, a bright core.
  const span = halfLength + radius;
  const fringe = Skia.Shader.MakeLinearGradient(
    { x: cx - UX * span, y: cy - UY * span },
    { x: cx + UX * span, y: cy + UY * span },
    [rgba(PALETTE.warmFringe, 0.95), rgba(PALETTE.warmFringe, 0.55), rgba(PALETTE.coolFringe, 0.5)],
    null,
    TileMode.Clamp,
  );
  canvas.drawPath(sil, shaded(fringe, 1, 16, 6));
  canvas.drawPath(sil, solid(0xffffff, 0.65 * light.rim, 10, 4));

  // A thin spectral fringe on the near cap's rim, always faintly there.
  canvas.save();
  canvas.clipPath(sil, ClipOp.Intersect, true);
  canvas.drawPath(pathOf(nearRimArc(g, 3), false), shaded(sweep(g.nearX, g.nearY, spectralAngle), 0.25 + 0.55 * light.rainbow, 5, 2));
  canvas.restore();

  canvas.drawPath(sil, solid(PALETTE.rimCore, 0.95 * light.rim, 3.4, 0));
}
