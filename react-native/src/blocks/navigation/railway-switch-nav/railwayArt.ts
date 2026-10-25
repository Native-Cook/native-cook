import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkPicture } from '@shopify/react-native-skia';

import { COUNT, type RailPose, clamp01 } from './railwayMotion';

/** Geometry in screen points: the track diagram panel and the lever frame under it. */
export type RailGeo = {
  w: number;
  h: number;
  /** Diagram panel. */
  px: number;
  py: number;
  pw: number;
  ph: number;
  /** Sidings (one per section, above its lever), the trunk line, platform top and curve radius. */
  xs: number[];
  trunk: number;
  top: number;
  rc: number;
  /** Size of the train, switches and signals (1 on a 150 pt diagram, up to 1.4 on a taller one). */
  k: number;
  /** Platform length beside each siding. */
  plat: number;
  /** Lever frame top, lever pivot and the lever's visible length at rest. */
  frame: number;
  pivot: number;
  lever: number;
};

export const MAP_MIN = 150;
export const FRAME_H = 172;
/** A car's length and the gap between cars, before the diagram's scale k. */
const CAR = 20;
const GAP = 2;

export function makeRailGeo(w: number, h: number, mapTop: number, mapH: number): RailGeo {
  const px = 16;
  const pw = w - 32;
  const xs: number[] = [];
  for (let i = 0; i < COUNT; i++) xs.push(px + (pw * (i + 0.5)) / COUNT);
  const frame = mapTop + mapH + 12;
  const k = Math.max(1, Math.min(1.4, mapH / MAP_MIN));
  const top = mapTop + 26 + 4 * k;
  const trunk = mapTop + mapH - 32;
  const rc = 20 * k;
  return { w, h, px, py: mapTop, pw, ph: mapH, xs, trunk, top, rc, k, plat: (trunk - rc - top) * 0.6, frame, pivot: frame + 124, lever: 92 };
}

// Route geometry ---------------------------------------------------------------------------------

export function routeLength(g: RailGeo, from: number, to: number) {
  'worklet';
  const a = g.trunk - g.rc - g.top;
  return 2 * a + Math.PI * g.rc + Math.abs(g.xs[to] - g.xs[from]) - 2 * g.rc;
}

/** A point on the route from one platform to another: down the first siding, round onto the trunk,
 * along it, round and up the second siding. Returns [x, y]. */
export function routePoint(g: RailGeo, from: number, to: number, s: number): number[] {
  'worklet';
  const dir = to > from ? 1 : -1;
  const a = g.trunk - g.rc - g.top;
  const arc = (Math.PI * g.rc) / 2;
  const xf = g.xs[from];
  const xt = g.xs[to];
  const run = Math.abs(xt - xf) - 2 * g.rc;
  const yc = g.trunk - g.rc;
  let d = Math.max(0, s);
  if (d <= a) return [xf, g.top + d];
  d -= a;
  if (d <= arc) {
    const f = d / g.rc;
    return [xf + dir * g.rc - dir * g.rc * Math.cos(f), yc + g.rc * Math.sin(f)];
  }
  d -= arc;
  if (d <= run) return [xf + dir * (g.rc + d), g.trunk];
  d -= run;
  if (d <= arc) {
    const f = d / g.rc;
    return [xt - dir * g.rc + dir * g.rc * Math.sin(f), yc + g.rc * Math.cos(f)];
  }
  d -= arc;
  return [xt, Math.max(g.top, yc - d)];
}

function routePath(g: RailGeo, from: number, to: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const L = routeLength(g, from, to);
  const n = 48;
  for (let i = 0; i <= n; i++) {
    const p = routePoint(g, from, to, (L * i) / n);
    if (i === 0) b.moveTo(p[0], p[1]);
    else b.lineTo(p[0], p[1]);
  }
  return b.build();
}

// Static art -------------------------------------------------------------------------------------

function screw(canvas: SkCanvas, x: number, y: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: x - 1, y: y - 1 }, 4, [Skia.Color('#D9C38E'), Skia.Color('#8A6B33'), Skia.Color('#4A3816')], [0, 0.6, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, 3.2, p);
  const slot = Skia.Paint();
  slot.setAntiAlias(true);
  slot.setStrokeWidth(0.9);
  slot.setColor(Skia.Color('rgba(40, 28, 10, 0.8)'));
  canvas.drawLine(x - 2, y + 1.2, x + 2, y - 1.2, slot);
}

/** The enamel diagram panel with every track drawn dim, and the wooden lever frame. */
export function recordStatic(g: RailGeo): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));

  // Diagram panel: two shadows, a painted face with a lit top edge, grain.
  const panel = Skia.RRectXY(Skia.XYWHRect(g.px, g.py, g.pw, g.ph), 18, 18);
  const amb = Skia.Paint();
  amb.setColor(Skia.Color('rgba(4, 8, 7, 0.45)'));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.px + 2, g.py + 12, g.pw - 4, g.ph), 18, 18), amb);
  const face = Skia.Paint();
  face.setAntiAlias(true);
  face.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: g.py }, { x: 0, y: g.py + g.ph }, [Skia.Color('#2A3D37'), Skia.Color('#22332E'), Skia.Color('#1B2925')], [0, 0.5, 1], TileMode.Clamp));
  canvas.drawRRect(panel, face);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 5, 0, 0));
  grain.setAlphaf(0.07);
  grain.setBlendMode(BlendMode.Overlay);
  canvas.save();
  canvas.clipRRect(panel, ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(g.px, g.py, g.pw, g.ph), grain);
  canvas.restore();
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1);
  rim.setColor(Skia.Color('rgba(214, 236, 222, 0.16)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.px + 0.5, g.py + 0.5, g.pw - 1, g.ph - 1), 17.5, 17.5), rim);
  screw(canvas, g.px + 12, g.py + 12);
  screw(canvas, g.px + g.pw - 12, g.py + 12);
  screw(canvas, g.px + 12, g.py + g.ph - 12);
  screw(canvas, g.px + g.pw - 12, g.py + g.ph - 12);

  // Tracks: the trunk, each siding and its two curves (a wye), dim cream enamel.
  const track = Skia.PathBuilder.Make();
  track.moveTo(g.px + 24, g.trunk);
  track.lineTo(g.px + g.pw - 24, g.trunk);
  const yc = g.trunk - g.rc;
  for (let i = 0; i < COUNT; i++) {
    const x = g.xs[i];
    track.moveTo(x, g.top);
    track.lineTo(x, yc);
    track.arcToOval(Skia.XYWHRect(x - 2 * g.rc, yc - g.rc, 2 * g.rc, 2 * g.rc), 0, 90, true);
    track.arcToOval(Skia.XYWHRect(x, yc - g.rc, 2 * g.rc, 2 * g.rc), 180, -90, true);
  }
  const tp = Skia.Paint();
  tp.setAntiAlias(true);
  tp.setStyle(PaintStyle.Stroke);
  tp.setStrokeWidth(3 * g.k);
  tp.setStrokeCap(StrokeCap.Round);
  tp.setStrokeJoin(StrokeJoin.Round);
  tp.setColor(Skia.Color('rgba(232, 224, 200, 0.26)'));
  canvas.drawPath(track.build(), tp);
  // Insulated joints between track circuits and buffer stops at the platforms.
  const joint = Skia.Paint();
  joint.setAntiAlias(true);
  joint.setStrokeWidth(1.2);
  joint.setColor(Skia.Color('rgba(20, 30, 27, 0.95)'));
  for (let i = 0; i < COUNT - 1; i++) {
    const x = (g.xs[i] + g.xs[i + 1]) / 2;
    canvas.drawLine(x, g.trunk - 3, x, g.trunk + 3, joint);
  }
  const stop = Skia.Paint();
  stop.setAntiAlias(true);
  stop.setStrokeWidth(3);
  stop.setStrokeCap(StrokeCap.Round);
  stop.setColor(Skia.Color('rgba(232, 224, 200, 0.45)'));
  for (let i = 0; i < COUNT; i++) canvas.drawLine(g.xs[i] - 6, g.top - 4, g.xs[i] + 6, g.top - 4, stop);
  // Platforms beside the sidings.
  const plat = Skia.Paint();
  plat.setAntiAlias(true);
  plat.setColor(Skia.Color('rgba(232, 224, 200, 0.13)'));
  const edgeP = Skia.Paint();
  edgeP.setAntiAlias(true);
  edgeP.setStrokeWidth(1);
  edgeP.setColor(Skia.Color('rgba(255, 214, 120, 0.35)'));
  for (let i = 0; i < COUNT; i++) {
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.xs[i] - 9 - 8 * g.k, g.top + 4, 8 * g.k, g.plat), 2, 2), plat);
    canvas.drawLine(g.xs[i] - 8, g.top + 6, g.xs[i] - 8, g.top + 2 + g.plat, edgeP);
  }

  // Lever frame: a mahogany plinth with a brass rail, one dark slot per lever.
  const frameTop = g.frame;
  const body = Skia.Paint();
  body.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: frameTop }, { x: 0, y: g.h }, [Skia.Color('#5A2E1E'), Skia.Color('#47231A'), Skia.Color('#2C1610')], [0, 0.35, 1], TileMode.Clamp));
  const frameR = Skia.RRectXY(Skia.XYWHRect(0, frameTop, g.w, g.h - frameTop + 30), 26, 26);
  const fs = Skia.Paint();
  fs.setColor(Skia.Color('rgba(0,0,0,0.45)'));
  fs.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, frameTop - 6, g.w, 40), 26, 26), fs);
  canvas.drawRRect(frameR, body);
  canvas.save();
  canvas.clipRRect(frameR, ClipOp.Intersect, true);
  for (let i = 0; i < 14; i++) {
    const y = frameTop + 8 + i * 13;
    const gp = Skia.Paint();
    gp.setAntiAlias(true);
    gp.setStyle(PaintStyle.Stroke);
    gp.setStrokeWidth(0.7);
    gp.setColor(Skia.Color(`rgba(20, 8, 4, ${0.18 + 0.1 * Math.sin(i * 2.1)})`));
    const b = Skia.PathBuilder.Make();
    b.moveTo(0, y);
    b.cubicTo(g.w * 0.3, y + 4 * Math.sin(i), g.w * 0.6, y - 3 * Math.cos(i * 1.3), g.w, y + 2 * Math.sin(i * 0.7));
    canvas.drawPath(b.build(), gp);
  }
  const fgrain = Skia.Paint();
  fgrain.setShader(Skia.Shader.MakeFractalNoise(0.7, 0.05, 2, 9, 0, 0));
  fgrain.setAlphaf(0.08);
  fgrain.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(0, frameTop, g.w, g.h - frameTop), fgrain);
  canvas.restore();
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  lip.setStrokeWidth(1);
  lip.setColor(Skia.Color('rgba(255, 210, 170, 0.28)'));
  canvas.drawLine(24, frameTop + 0.8, g.w - 24, frameTop + 0.8, lip);
  // Brass rail behind the levers.
  const rail = Skia.Paint();
  rail.setAntiAlias(true);
  rail.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: frameTop + 40 }, { x: 0, y: frameTop + 48 }, [Skia.Color('#F0D79A'), Skia.Color('#B08A42'), Skia.Color('#6E5220')], [0, 0.5, 1], TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(16, frameTop + 40, g.w - 32, 8), 4, 4), rail);
  for (let i = 0; i < COUNT; i++) {
    const x = g.xs[i];
    const slot = Skia.Paint();
    slot.setAntiAlias(true);
    slot.setShader(Skia.Shader.MakeLinearGradient({ x: x - 9, y: 0 }, { x: x + 9, y: 0 }, [Skia.Color('#0E0605'), Skia.Color('#1E0F0B'), Skia.Color('#0E0605')], [0, 0.5, 1], TileMode.Clamp));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 9, frameTop + 30, 18, g.pivot - frameTop - 22), 9, 9), slot);
    // The brass name plate under the lever (its text is a view).
    const plate = Skia.Paint();
    plate.setAntiAlias(true);
    plate.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: g.pivot + 12 }, { x: 0, y: g.pivot + 36 }, [Skia.Color('#EAD08F'), Skia.Color('#C29C52'), Skia.Color('#8A6A2E')], [0, 0.45, 1], TileMode.Clamp));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 36, g.pivot + 12, 72, 24), 6, 6), plate);
    const pe = Skia.Paint();
    pe.setAntiAlias(true);
    pe.setStyle(PaintStyle.Stroke);
    pe.setStrokeWidth(1);
    pe.setColor(Skia.Color('rgba(70, 48, 14, 0.7)'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 35.5, g.pivot + 12.5, 71, 23), 5.5, 5.5), pe);
    // Lamp bezel above the slot.
    const bez = Skia.Paint();
    bez.setAntiAlias(true);
    bez.setShader(Skia.Shader.MakeRadialGradient({ x: x - 2, y: frameTop + 16 }, 10, [Skia.Color('#F3DDA2'), Skia.Color('#9C7A38'), Skia.Color('#4E3A14')], [0, 0.65, 1], TileMode.Clamp));
    canvas.drawCircle(x, frameTop + 18, 8.5, bez);
  }
  return recorder.finishRecordingAsPicture();
}

// Moving art -------------------------------------------------------------------------------------

/** Lever colours, as in a real frame: signals red, routes blue, points black, the last yellow. */
const PAINT = ['#B5352B', '#2D5E9E', '#2A2D31', '#C9961F'];
const PAINT_DARK = ['#5E1712', '#132B4D', '#0E0F11', '#6B4C09'];

function lamp(canvas: SkCanvas, x: number, y: number, r: number, green: number) {
  'worklet';
  const gr = clamp01(green);
  const red = 1 - gr;
  const core = Skia.Paint();
  core.setAntiAlias(true);
  const c1 = `rgba(${Math.round(255 * red + 150 * gr)}, ${Math.round(90 * red + 255 * gr)}, ${Math.round(70 * red + 150 * gr)}, 1)`;
  const c2 = `rgba(${Math.round(150 * red + 20 * gr)}, ${Math.round(20 * red + 140 * gr)}, ${Math.round(16 * red + 70 * gr)}, 1)`;
  core.setShader(Skia.Shader.MakeRadialGradient({ x: x - r * 0.3, y: y - r * 0.3 }, r * 1.3, [Skia.Color('#FFFBEA'), Skia.Color(c1), Skia.Color(c2)], [0, 0.35, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, r, core);
  const glow = Skia.Paint();
  glow.setBlendMode(BlendMode.Plus);
  glow.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r * 4, [Skia.Color(red > 0.5 ? 'rgba(255, 80, 50, 0.35)' : 'rgba(110, 255, 150, 0.35)'), Skia.Color('rgba(0,0,0,0)')], [0, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, r * 4, glow);
}

function drawCar(canvas: SkCanvas, g: RailGeo, from: number, to: number, s: number, lead: boolean, tail: boolean) {
  'worklet';
  const a = routePoint(g, from, to, s);
  const b = routePoint(g, from, to, s - (CAR - 2) * g.k);
  const cx = (a[0] + b[0]) / 2;
  const cy = (a[1] + b[1]) / 2;
  const ang = (Math.atan2(a[1] - b[1], a[0] - b[0]) * 180) / Math.PI;
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(ang, 0, 0);
  canvas.scale(g.k, g.k);
  // Along +x is forward. Body, roof stripe, windows, light at the leading/trailing end.
  const sh = Skia.Paint();
  sh.setColor(Skia.Color('rgba(0, 0, 0, 0.45)'));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-CAR / 2 + 1, -4.6 + 1.5, CAR, 9.2), 3.5, 3.5), sh);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -4.6 }, { x: 0, y: 4.6 }, [Skia.Color('#FFF1C9'), Skia.Color('#F2C14E'), Skia.Color('#B9861E')], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-CAR / 2, -4.6, CAR, 9.2), 3.5, 3.5), body);
  const stripe = Skia.Paint();
  stripe.setAntiAlias(true);
  stripe.setColor(Skia.Color('#2B3A36'));
  canvas.drawRect(Skia.XYWHRect(-CAR / 2 + 3, -1.2, CAR - 6, 2.4), stripe);
  const win = Skia.Paint();
  win.setAntiAlias(true);
  win.setColor(Skia.Color('rgba(210, 240, 236, 0.9)'));
  for (let i = 0; i < 4; i++) canvas.drawRect(Skia.XYWHRect(-CAR / 2 + 4 + i * 3.4, -0.7, 2.2, 1.4), win);
  if (lead) {
    const head = Skia.Paint();
    head.setBlendMode(BlendMode.Plus);
    head.setShader(Skia.Shader.MakeRadialGradient({ x: CAR / 2 + 2, y: 0 }, 12, [Skia.Color('rgba(255, 244, 200, 0.55)'), Skia.Color('rgba(0,0,0,0)')], [0, 1], TileMode.Clamp));
    canvas.drawCircle(CAR / 2 + 2, 0, 12, head);
    const lampP = Skia.Paint();
    lampP.setColor(Skia.Color('#FFFDF2'));
    canvas.drawCircle(CAR / 2 - 0.8, -2.6, 0.9, lampP);
    canvas.drawCircle(CAR / 2 - 0.8, 2.6, 0.9, lampP);
  }
  if (tail) {
    const tl = Skia.Paint();
    tl.setColor(Skia.Color('#FF4A36'));
    canvas.drawCircle(-CAR / 2 + 0.8, -2.6, 0.9, tl);
    canvas.drawCircle(-CAR / 2 + 0.8, 2.6, 0.9, tl);
  }
  canvas.restore();
}

function drawLever(canvas: SkCanvas, g: RailGeo, k: number, pull: number) {
  'worklet';
  const x = g.xs[k];
  const p = Math.max(-0.1, Math.min(1.1, pull));
  // Pulled toward the viewer: the visible length shortens and the handle comes closer (larger).
  const len = g.lever * (1 - 0.42 * p);
  const topY = g.pivot - len;
  const sc = 1 + 0.14 * p;
  const sh = Skia.Paint();
  sh.setColor(Skia.Color(`rgba(0, 0, 0, ${0.35 + 0.15 * p})`));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 4 * p, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 4 + 2 + 3 * p, topY + 6 + 4 * p, 8, len), 4, 4), sh);
  // Painted rod, tapering toward the top, lit more as it turns to the light.
  const rod = Skia.PathBuilder.Make();
  rod.moveTo(x - 5, g.pivot);
  rod.lineTo(x - 3.4, topY + 22);
  rod.lineTo(x + 3.4, topY + 22);
  rod.lineTo(x + 5, g.pivot);
  rod.close();
  const rp = Skia.Paint();
  rp.setAntiAlias(true);
  rp.setShader(
    Skia.Shader.MakeLinearGradient({ x: x - 5, y: 0 }, { x: x + 5, y: 0 }, [Skia.Color(PAINT_DARK[k]), Skia.Color(PAINT[k]), Skia.Color(`rgba(255, 245, 230, ${0.35 + 0.25 * p})`), Skia.Color(PAINT[k]), Skia.Color(PAINT_DARK[k])], [0, 0.3, 0.42, 0.6, 1], TileMode.Clamp),
  );
  canvas.drawPath(rod.build(), rp);
  // Polished steel handle and the catch handle behind it.
  canvas.save();
  canvas.translate(x, topY + 22);
  canvas.scale(sc, sc);
  const catchP = Skia.Paint();
  catchP.setAntiAlias(true);
  catchP.setShader(Skia.Shader.MakeLinearGradient({ x: 4, y: 0 }, { x: 8, y: 0 }, [Skia.Color('#6F757C'), Skia.Color('#E8ECEF'), Skia.Color('#5A6066')], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(4.2, -22, 3.4, 18), 1.7, 1.7), catchP);
  canvas.drawRect(Skia.XYWHRect(2, -6, 4, 2.4), catchP);
  const grip = Skia.Paint();
  grip.setAntiAlias(true);
  grip.setShader(
    Skia.Shader.MakeLinearGradient({ x: -4, y: 0 }, { x: 4, y: 0 }, [Skia.Color('#4F555B'), Skia.Color('#C9CFD4'), Skia.Color('#FFFFFF'), Skia.Color('#9BA2A8'), Skia.Color('#3E4348')], [0, 0.3, 0.42, 0.7, 1], TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-4, -26, 8, 28), 4, 4), grip);
  const collar = Skia.Paint();
  collar.setAntiAlias(true);
  collar.setShader(Skia.Shader.MakeLinearGradient({ x: -5, y: 0 }, { x: 5, y: 0 }, [Skia.Color('#6E5220'), Skia.Color('#F0D79A'), Skia.Color('#6E5220')], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-5, 0, 10, 4), 1.5, 1.5), collar);
  canvas.restore();
}

/** Everything that moves: route light, switch blades, signals, the train, lamps and levers. */
export function drawMoving(canvas: SkCanvas, g: RailGeo, p: RailPose) {
  'worklet';
  if (p.route > 0.002 && p.from !== p.to) {
    const path = routePath(g, p.from, p.to);
    const glow = Skia.Paint();
    glow.setAntiAlias(true);
    glow.setStyle(PaintStyle.Stroke);
    glow.setStrokeWidth(9 * g.k);
    glow.setStrokeCap(StrokeCap.Round);
    glow.setStrokeJoin(StrokeJoin.Round);
    glow.setColor(Skia.Color(`rgba(255, 214, 120, ${0.32 * p.route})`));
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, false));
    canvas.drawPath(path, glow);
    const line = Skia.Paint();
    line.setAntiAlias(true);
    line.setStyle(PaintStyle.Stroke);
    line.setStrokeWidth(3 * g.k);
    line.setStrokeCap(StrokeCap.Round);
    line.setStrokeJoin(StrokeJoin.Round);
    line.setColor(Skia.Color(`rgba(255, 236, 184, ${0.95 * p.route})`));
    canvas.drawPath(path, line);
  }
  // Switch blades at the foot of each siding, and the point motor's lamp.
  const yc = g.trunk - g.rc;
  const blade = Skia.Paint();
  blade.setAntiAlias(true);
  blade.setStrokeWidth(2.6 * g.k);
  blade.setStrokeCap(StrokeCap.Round);
  for (let k = 0; k < COUNT; k++) {
    const b = p.blades[k];
    const ang = (b * 34 * Math.PI) / 180;
    const x0 = g.xs[k];
    const y0 = yc - 10 * g.k;
    const set = Math.abs(b);
    blade.setColor(Skia.Color(`rgba(${Math.round(150 + 105 * set)}, ${Math.round(150 + 61 * set)}, ${Math.round(140 - 40 * set)}, ${0.7 + 0.3 * set})`));
    canvas.drawLine(x0, y0, x0 + Math.sin(ang) * 17 * g.k, y0 + Math.cos(ang) * 17 * g.k, blade);
    const pivotP = Skia.Paint();
    pivotP.setAntiAlias(true);
    pivotP.setColor(Skia.Color('#E8E0C8'));
    canvas.drawCircle(x0, y0, 2.2 * g.k, pivotP);
    lamp(canvas, x0 - 14 * g.k, yc - 4, 2.2 * g.k, set > 0.5 ? 1 : 0);
  }
  // Platform signals.
  for (let k = 0; k < COUNT; k++) {
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setColor(Skia.Color('#0F1715'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.xs[k] + 6 + 3 * g.k, g.top - 2, 9 * g.k, 14 * g.k), 4.5 * g.k, 4.5 * g.k), sp);
    lamp(canvas, g.xs[k] + 6 + 7.5 * g.k, g.top - 2 + 7 * g.k, 3 * g.k, p.signals[k]);
  }
  // The train: two cars, headlights ahead, tail lights behind.
  const L = routeLength(g, p.from === p.to ? 0 : p.from, p.from === p.to ? 1 : p.to);
  const car = CAR * g.k;
  const gap = GAP * g.k;
  const span = 2 * car + gap;
  if (p.from === p.to) {
    // At rest on a platform: reuse a route that ends there.
    const other = p.to === 0 ? 1 : 0;
    const Lr = routeLength(g, other, p.to);
    drawCar(canvas, g, other, p.to, Lr, true, false);
    drawCar(canvas, g, other, p.to, Lr - car - gap, false, true);
  } else {
    const lead = span + (L - span) * p.run;
    drawCar(canvas, g, p.from, p.to, lead, true, false);
    drawCar(canvas, g, p.from, p.to, lead - car - gap, false, true);
  }
  // Lever-frame lamps (repeaters) and the levers.
  for (let k = 0; k < COUNT; k++) {
    lamp(canvas, g.xs[k], g.frame + 18, 5, p.signals[k]);
    drawLever(canvas, g, k, p.levers[k]);
  }
}
