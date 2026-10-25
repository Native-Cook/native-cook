import { BlendMode, ClipOp, FilterMode, MipmapMode, Skia, TileMode, type SkCanvas, type SkImage, type SkPaint } from '@shopify/react-native-skia';

import { clamp, mix, rotated, smoother, softClamp, type Frame } from './motion';

function paint(): SkPaint { 'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); return p; }

export function poseWeights(grip: number): [number, number, number] {
  'worklet'; const closure = clamp(grip), first = smoother(2 * closure), second = smoother(2 * closure - 1);
  return [1 - first, first - second, second];
}

/** Pure shared geometry keeps the visible pose and its alpha shadow registered. */
export function handAppearance(f: Frame) {
  'worklet'; const closure = clamp(f.grip), bx = f.handBX, by = f.handBY;
  const hover = softClamp((194 - by) / 10);
  const lift = smoother((180 - by) / 140) * smoother((closure - 0.65) / 0.35);
  return {
    closure, bx, by, hover, lift,
    castX: mix(4 * (2 * softClamp((f.handAngle / 24 + 1) / 2) - 1), 3, closure),
    castY: mix(12 + 32 * hover, 18, closure) + 9 * lift,
    castBlur: mix(6 + 6 * hover, 7, closure) + 4 * lift,
    castAlpha: mix(0.20 + 0.02 * hover, 0.26, closure) * (1 - 0.28 * lift),
    contactAlpha: (0.10 + 0.06 * closure) * (1 - lift),
    contactWidth: (54 - 14 * closure) * (1 - 0.25 * lift),
    contactHeight: (18 - 6 * closure) * (1 - 0.35 * lift),
    contactBlur: 5 - 2 * closure + 1.5 * lift,
    contactX: bx, contactY: by + 5 - closure,
    weights: poseWeights(closure),
  };
}

type Point = [number, number];
type ArmTriangle = { points: [Point, Point, Point]; matrix: number[] };

/** Keep the iOS inlet in the window's reserved cutout area; Android enters at the top edge. */
export function handOrigin(topInset: number, baseY: number, scale: number, isIOS: boolean) {
  'worklet'; const clipTop = isIOS && topInset >= 44 ? Math.max(0, (topInset - 14 - baseY) / scale) : 0;
  return { clipTop, inlet: [196.5, clipTop - 8] as Point };
}

function armTriangle(p0: Point, p1: Point, p2: Point, second: boolean): ArmTriangle {
  'worklet';
  if (!second) return {
    points: [p0, p1, p2],
    matrix: [(p1[0] - p0[0]) / 250, (p2[0] - p0[0]) / 2, p0[0],
      (p1[1] - p0[1]) / 250, (p2[1] - p0[1]) / 2, p0[1], 0, 0, 1],
  };
  const ax = (p1[0] - p2[0]) / 250, ay = (p1[1] - p2[1]) / 250;
  return {
    points: [p0, p1, p2],
    matrix: [ax, (p1[0] - p0[0]) / 2, p0[0] - ax * 250,
      ay, (p1[1] - p0[1]) / 2, p0[1] - ay * 250, 0, 0, 1],
  };
}

/** The exact Swift eight-quad cubic ribbon, including its C2 inlet fade. */
export function armRibbonGeometry(a: Point, w: Point, angle: number) {
  'worklet'; const alpha = smoother((w[1] - a[1]) / 16), triangles: ArmTriangle[] = [];
  if (w[1] <= a[1]) return { alpha, triangles };
  const radians = angle * Math.PI / 180, tangent: Point = [-Math.sin(radians), Math.cos(radians)];
  const reach = Math.hypot(w[0] - a[0], w[1] - a[1]) * 0.34;
  const c1: Point = [a[0], a[1] + reach], c2: Point = [w[0] - tangent[0] * reach, w[1] - tangent[1] * reach];
  const edge = (t: number, side: number): Point => {
    'worklet'; const u = 1 - t;
    const x = u * u * u * a[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * w[0];
    const y = u * u * u * a[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * w[1];
    const dx = 3 * u * u * (c1[0] - a[0]) + 6 * u * t * (c2[0] - c1[0]) + 3 * t * t * (w[0] - c2[0]);
    const dy = 3 * u * u * (c1[1] - a[1]) + 6 * u * t * (c2[1] - c1[1]) + 3 * t * t * (w[1] - c2[1]);
    const length = Math.max(0.001, Math.hypot(dx, dy));
    return [x + side * 58.75 * dy / length, y - side * 58.75 * dx / length];
  };
  for (let i = 0; i < 8; i++) {
    const t0 = i / 8, t1 = (i + 1) / 8;
    const l0 = edge(t0, -1), r0 = edge(t0, 1), l1 = edge(t1, -1), r1 = edge(t1, 1);
    triangles.push(armTriangle(l0, r0, l1, false), armTriangle(r0, r1, l1, true));
  }
  return { alpha, triangles };
}

function drawArmRibbon(canvas: SkCanvas, image: SkImage, a: Point, w: Point, angle: number, width: number, height: number) {
  'worklet'; const geometry = armRibbonGeometry(a, w, angle); if (geometry.alpha <= 0) return;
  const opacity = paint(); opacity.setAlphaf(geometry.alpha);
  canvas.saveLayer(opacity, Skia.XYWHRect(0, 0, width, height));
  const ink = paint();
  for (const triangle of geometry.triangles) {
    const path = Skia.Path.Make(); path.moveTo(triangle.points[0][0], triangle.points[0][1]);
    path.lineTo(triangle.points[1][0], triangle.points[1][1]); path.lineTo(triangle.points[2][0], triangle.points[2][1]); path.close();
    canvas.save(); canvas.clipPath(path, ClipOp.Intersect, false); canvas.concat(triangle.matrix);
    // Extend the constant row across triangle edges to match Swift's filtered seam coverage.
    canvas.drawImageRectOptions(image, Skia.XYWHRect(0, 200, image.width(), 1), Skia.XYWHRect(0, -1, 250, 4), FilterMode.Linear, MipmapMode.None, ink);
    canvas.restore();
  }
  canvas.restore();
}

/** Assemble each anchored arm and hand pose before weighted premultiplied addition. */
export function drawHandPoses(canvas: SkCanvas, f: Frame, images: SkImage[], bx: number, by: number, inlet: Point, width = 393, height = 852) {
  'worklet'; const weights = poseWeights(f.grip), anchors = [[137, 363], [138, 344], [132, 344]], shaftCenters = [138.5, 140, 133];
  const bounds = Skia.XYWHRect(0, 0, width, height);
  canvas.saveLayer(paint(), bounds);
  for (let i = 0; i < 3; i++) {
    if (weights[i] <= 0) continue;
    const image = images[i], center = rotated(0.47 * (125 - anchors[i][0]), 0.47 * (200 - anchors[i][1]), f.handAngle);
    const wrist = rotated(0.47 * (125 - anchors[i][0]), 0.47 * (202 - anchors[i][1]), f.handAngle);
    const weighted = paint(); weighted.setAlphaf(weights[i]); weighted.setBlendMode(BlendMode.Plus);
    canvas.saveLayer(weighted, bounds);
    const poseInlet: Point = [inlet[0] - 0.47 * (shaftCenters[i] - 125), inlet[1]];
    drawArmRibbon(canvas, image, poseInlet, [bx + wrist[0], by + wrist[1]], f.handAngle, width, height);
    canvas.save(); canvas.translate(bx + center[0], by + center[1]); canvas.rotate(f.handAngle, 0, 0); canvas.translate(-58.75, -94);
    canvas.clipRect(Skia.XYWHRect(0, 199 * 0.47, 117.5, 166 * 0.47), ClipOp.Intersect, true);
    canvas.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, 117.5, 188), FilterMode.Linear, MipmapMode.None, paint());
    canvas.restore(); canvas.restore();
  }
  canvas.restore();
}

/** The anchored color poses and world-aligned cast silhouette share one final top clip. */
export function drawHand(canvas: SkCanvas, f: Frame, images: SkImage[], inlet: Point = [196.5, -8], clipTop = 0) {
  'worklet'; const alpha = clamp(f.handAlpha); if (alpha <= 0.00001) return;
  const a = handAppearance(f), bounds = Skia.XYWHRect(0, 0, 393, 852);
  const overall = paint(); overall.setAlphaf(alpha);
  canvas.save(); canvas.clipRect(Skia.XYWHRect(0, clipTop, 393, 852 - clipTop), ClipOp.Intersect, true);
  canvas.saveLayer(overall, bounds);

  const shadow = paint();
  shadow.setColorFilter(Skia.ColorFilter.MakeBlend(Skia.Color('black'), BlendMode.SrcIn));
  shadow.setImageFilter(Skia.ImageFilter.MakeBlur(a.castBlur, a.castBlur, TileMode.Decal));
  shadow.setAlphaf(a.castAlpha);
  // Clip the same curved arm/hand silhouette before Gaussian blur, then offset in screen space.
  const caster = Skia.XYWHRect(0, 0, 393, 480);
  canvas.save(); canvas.translate(a.castX, a.castY); canvas.saveLayer(shadow, caster);
  canvas.save(); canvas.clipRect(caster, ClipOp.Intersect, true);
  drawHandPoses(canvas, f, images, a.bx, a.by, inlet, 393, 480);
  canvas.restore(); canvas.restore(); canvas.restore();

  const contact = paint(); contact.setColor(Skia.Color(`rgba(0,0,0,${a.contactAlpha})`));
  contact.setImageFilter(Skia.ImageFilter.MakeBlur(a.contactBlur, a.contactBlur, TileMode.Decal));
  canvas.drawOval(Skia.XYWHRect(a.contactX - a.contactWidth / 2, a.contactY - a.contactHeight / 2, a.contactWidth, a.contactHeight), contact);
  drawHandPoses(canvas, f, images, a.bx, a.by, inlet);
  canvas.restore(); canvas.restore();
}
