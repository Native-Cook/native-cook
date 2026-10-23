/**
 * Paste Places Import — the small paper map of the invented city (Velmora) that the text area unfolds into.
 * Drawn once into an SkPicture (card-local, pt) and shown on each folding panel; the route and the pins'
 * dots and shadows are drawn per frame on top (see `drawPins`).
 */
import { BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, type SkCanvas, type SkPath, type SkPicture } from '@shopify/react-native-skia';

import { box, hairline, MAP, mapColors as C, colors, radius, SPOTS } from './theme';

const W = box.card.w;
const H = box.card.h;

function stroke(color: string, width: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(Skia.Color(color));
  return p;
}

function fill(color: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

const svg = (d: string) => Skia.Path.MakeFromSVGString(d) as SkPath;

/** The map, card-local, `u` pt per ref px. */
export function makeMapPicture(u: number, panels: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas: SkCanvas = rec.beginRecording(Skia.XYWHRect(0, 0, W * u, H * u));
  canvas.scale(u, u);
  const card = Skia.RRectXY(Skia.XYWHRect(0, 0, W, H), radius.card, radius.card);
  canvas.save();
  canvas.clipRRect(card, ClipOp.Intersect, true);

  canvas.drawRect(Skia.XYWHRect(0, 0, W, H), fill(C.land));
  for (const p of MAP.parks) canvas.drawOval(Skia.XYWHRect(p.cx - p.rx, p.cy - p.ry, 2 * p.rx, 2 * p.ry), fill(C.park));

  // Minor streets: a slightly turned grid.
  canvas.save();
  canvas.rotate(MAP.minorAngle, W / 2, H / 2);
  const minor = stroke(C.minor, MAP.minorW);
  for (let x = -W; x < 2 * W; x += MAP.minorGap) canvas.drawLine(x, -H, x, 2 * H, minor);
  for (let y = -H; y < 2 * H; y += MAP.minorGap * 1.3) canvas.drawLine(-W, y, 2 * W, y, minor);
  canvas.restore();

  // River under the avenues, so every avenue crosses it on a bridge.
  const river = svg(MAP.river);
  canvas.drawPath(river, stroke(C.shore, MAP.shoreW));
  canvas.drawPath(river, stroke(C.water, MAP.riverW));

  const casing = stroke(C.casing, MAP.casingW);
  const street = stroke(C.street, MAP.majorW);
  const majors = MAP.major.map(svg);
  for (const m of majors) canvas.drawPath(m, casing);
  for (const m of majors) canvas.drawPath(m, street);

  // Sea over everything it covers.
  const sea = svg(MAP.sea);
  canvas.drawPath(sea, fill(C.water));
  canvas.drawPath(sea, stroke(C.shore, MAP.seaShoreW));

  // Creases where the paper was folded.
  const dark = stroke(C.creaseDark, MAP.creaseW);
  const light = stroke(C.creaseLight, MAP.creaseW);
  dark.setStrokeCap(StrokeCap.Butt);
  light.setStrokeCap(StrokeCap.Butt);
  for (let i = 1; i < panels; i++) {
    const y = (H * i) / panels;
    canvas.drawLine(0, y - MAP.creaseW / 2, W, y - MAP.creaseW / 2, dark);
    canvas.drawLine(0, y + MAP.creaseW / 2, W, y + MAP.creaseW / 2, light);
  }
  canvas.restore();

  // The text area's hairline.
  const border = stroke(colors.cardBorder, hairline);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(hairline / 2, hairline / 2, W - hairline, H - hairline), radius.card - hairline / 2, radius.card - hairline / 2), border);
  return rec.finishRecordingAsPicture();
}

/** The route through the places (Catmull-Rom through the dots), card-local pt. */
export function makeRoute(u: number): SkPath {
  const pts = SPOTS.map(([x, y]) => ({ x: x * u, y: y * u }));
  const p = Skia.PathBuilder.Make();
  p.moveTo(pts[0].x, pts[0].y);
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[Math.min(pts.length - 1, i + 2)];
    p.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y);
  }
  return p.build();
}

export function makePinPaints(k: number, pinTokens: { route: number; routeCasing: number; shadowBlur: number }) {
  const route = stroke(C.route, pinTokens.route * k);
  const routeCasing = stroke(C.routeCasing, pinTokens.routeCasing * k);
  const dot = fill(C.dot);
  const ring = fill(C.dotRing);
  const shadow = fill(C.shadow);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, pinTokens.shadowBlur * k, true));
  return { route, routeCasing, dot, ring, shadow };
}
