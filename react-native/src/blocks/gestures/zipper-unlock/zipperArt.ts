import { BlurStyle, ClipOp, FillType, PaintStyle, type SkCanvas, type SkFont, type SkPath, type SkPicture, Skia, StrokeCap } from '@shopify/react-native-skia';

import { TAB_LEN, TOOTH_H, TOOTH_PITCH, TOOTH_W, type Zip, gapAt } from './zipperMotion';

/*
 Zipper Unlock — the metal and leather, recorded once at the origin (the slider, its pull tab,
 the bottom stop, the leather patch), and the teeth, rebuilt as three batched paths per frame.
*/

const BRASS = ['#F6E2A6', '#D3A453', '#8C6326', '#E2BE72'];

function brass(canvas: SkCanvas, path: SkPath, x0: number, y0: number, x1: number, y1: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, BRASS.map((c) => Skia.Color(c)), [0, 0.38, 0.72, 1], 0));
  canvas.drawPath(path, p);
}

function edgeLines(canvas: SkCanvas, path: SkPath) {
  const s = Skia.Paint();
  s.setAntiAlias(true);
  s.setStyle(PaintStyle.Stroke);
  s.setStrokeWidth(0.8);
  s.setColor(Skia.Color('rgba(70,44,12,0.65)'));
  canvas.drawPath(path, s);
}

/** The slider, centred on (0, 0): 32 pt wide at the top, 21 at the bottom, 36 tall. */
export function recordSlider(): SkPicture {
  const r = Skia.PictureRecorder();
  const c = r.beginRecording(Skia.XYWHRect(-40, -40, 80, 90));
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(5,10,24,0.55)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-15, -14, 32, 38), 9, 9), shadow);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
  shadow.setColor(Skia.Color('rgba(5,10,24,0.6)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-15.5, -17, 31, 35), 8, 8), shadow);

  const body = Skia.PathBuilder.Make();
  body.moveTo(-16, -12);
  body.cubicTo(-16, -17, -13, -18, -9, -18);
  body.lineTo(9, -18);
  body.cubicTo(13, -18, 16, -17, 16, -12);
  body.lineTo(10.5, 13);
  body.cubicTo(9.5, 17, 7, 18, 4, 18);
  body.lineTo(-4, 18);
  body.cubicTo(-7, 18, -9.5, 17, -10.5, 13);
  body.close();
  brass(c, body.build(), -16, -18, 14, 18);
  edgeLines(c, body.build());
  // Raised centre ridge and the bridge the tab hangs from.
  const ridge = Skia.PathBuilder.Make();
  ridge.addRRect(Skia.RRectXY(Skia.XYWHRect(-3.2, -15, 6.4, 24), 3, 3));
  brass(c, ridge.build(), 3, -15, -3, 9);
  edgeLines(c, ridge.build());
  const bridge = Skia.PathBuilder.Make();
  bridge.addRRect(Skia.RRectXY(Skia.XYWHRect(-5.5, 8, 11, 7), 3.5, 3.5));
  brass(c, bridge.build(), -5, 8, 5, 15);
  edgeLines(c, bridge.build());
  // Top edge highlight.
  const hi = Skia.Paint();
  hi.setAntiAlias(true);
  hi.setStyle(PaintStyle.Stroke);
  hi.setStrokeWidth(1);
  hi.setStrokeCap(StrokeCap.Round);
  hi.setColor(Skia.Color('rgba(255,248,222,0.85)'));
  c.drawLine(-11, -16.6, 11, -16.6, hi);
  return r.finishRecordingAsPicture();
}

/** The tab's outline, hanging from its pivot at (0, 0) down +y, with the ring hole cut out. */
export function tabPath(): SkPath {
  const p = Skia.PathBuilder.Make();
  p.moveTo(-7, -4);
  p.cubicTo(-7, -7, 7, -7, 7, -4);
  p.lineTo(10, TAB_LEN - 12);
  p.cubicTo(10, TAB_LEN - 4, 6, TAB_LEN, 0, TAB_LEN);
  p.cubicTo(-6, TAB_LEN, -10, TAB_LEN - 4, -10, TAB_LEN - 12);
  p.close();
  p.addRRect(Skia.RRectXY(Skia.XYWHRect(-4.5, TAB_LEN - 15, 9, 8), 3.5, 3.5));
  p.addRRect(Skia.RRectXY(Skia.XYWHRect(-3, -2.5, 6, 4), 2, 2));
  p.setFillType(FillType.EvenOdd);
  return p.build();
}

/** The pull tab with its split ring, pivot at (0, 0). */
export function recordTab(): SkPicture {
  const r = Skia.PictureRecorder();
  const c = r.beginRecording(Skia.XYWHRect(-30, -12, 60, TAB_LEN + 40));
  const tab = tabPath();
  brass(c, tab, -10, 0, 12, TAB_LEN);
  edgeLines(c, tab);
  // A pressed border on the tab's face.
  const inner = Skia.Paint();
  inner.setAntiAlias(true);
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(1);
  inner.setColor(Skia.Color('rgba(120,80,24,0.55)'));
  const face = Skia.PathBuilder.Make();
  face.moveTo(-4.5, 3);
  face.lineTo(-7, TAB_LEN - 18);
  face.moveTo(4.5, 3);
  face.lineTo(7, TAB_LEN - 18);
  c.drawPath(face.build(), inner);
  inner.setColor(Skia.Color('rgba(255,245,210,0.6)'));
  c.drawLine(-6, -3.5, 6, -3.5, inner);
  // The ring through the hole.
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(3.2);
  ring.setShader(Skia.Shader.MakeLinearGradient({ x: -10, y: TAB_LEN - 4 }, { x: 10, y: TAB_LEN + 18 }, BRASS.map((x) => Skia.Color(x)), [0, 0.35, 0.7, 1], 0));
  c.drawCircle(0, TAB_LEN + 4, 9.5, ring);
  ring.setShader(null);
  ring.setStrokeWidth(0.8);
  ring.setColor(Skia.Color('rgba(255,248,222,0.7)'));
  c.drawArc(Skia.XYWHRect(-9.5, TAB_LEN - 5.5, 19, 19), 200, 80, false, ring);
  return r.finishRecordingAsPicture();
}

/** The bottom stop: a small box, centred on (0, 0). */
export function recordStop(): SkPicture {
  const r = Skia.PictureRecorder();
  const c = r.beginRecording(Skia.XYWHRect(-20, -20, 40, 40));
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Skia.Color('rgba(5,10,24,0.5)'));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-8, -5, 17, 13), 3, 3), sh);
  const box = Skia.PathBuilder.Make();
  box.addRRect(Skia.RRectXY(Skia.XYWHRect(-8.5, -6, 17, 12), 2.5, 2.5));
  brass(c, box.build(), -8, -6, 8, 6);
  edgeLines(c, box.build());
  return r.finishRecordingAsPicture();
}

function spaced(c: SkCanvas, text: string, font: SkFont, x: number, y: number, spacing: number, paint: ReturnType<typeof Skia.Paint>) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (const w of widths) total += w;
  total += spacing * (text.length - 1);
  let cx = x - total / 2;
  for (let i = 0; i < text.length; i += 1) {
    c.drawText(text[i], cx, y, paint, font);
    cx += widths[i] + spacing;
  }
}

/** The leather patch, 136 × 80 pt, centred on (0, 0). */
export function recordPatch(title: SkFont, small: SkFont): SkPicture {
  const W = 136;
  const H = 80;
  const r = Skia.PictureRecorder();
  const c = r.beginRecording(Skia.XYWHRect(-W, -H, W * 2, H * 2));
  const rect = Skia.XYWHRect(-W / 2, -H / 2, W, H);
  const rr = Skia.RRectXY(rect, 10, 10);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // Two shadows: tight contact and soft ambient.
  p.setColor(Skia.Color('rgba(4,8,20,0.45)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-W / 2 + 2, -H / 2 + 8, W, H), 10, 10), p);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
  p.setColor(Skia.Color('rgba(4,8,20,0.5)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-W / 2, -H / 2 + 1.5, W, H), 10, 10), p);
  p.setMaskFilter(null);
  p.setShader(
    Skia.Shader.MakeLinearGradient({ x: -W / 2, y: -H / 2 }, { x: W / 2, y: H / 2 }, ['#C48D5E', '#9C6438', '#7A4826', '#93603A'].map((x) => Skia.Color(x)), [0, 0.4, 0.8, 1], 0),
  );
  c.drawRRect(rr, p);
  p.setShader(null);
  // Leather pores: seeded specks, darker and lighter.
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  let seed = 7;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  for (let i = 0; i < 220; i += 1) {
    const x = -W / 2 + rnd() * W;
    const y = -H / 2 + rnd() * H;
    p.setColor(Skia.Color(rnd() > 0.5 ? 'rgba(60,30,12,0.18)' : 'rgba(255,220,180,0.10)'));
    c.drawCircle(x, y, 0.5 + rnd() * 0.9, p);
  }
  // Worn, lighter rim.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(5);
  rim.setColor(Skia.Color('rgba(255,214,170,0.10)'));
  rim.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, false));
  c.drawRRect(rr, rim);
  c.restore();
  // Stitched border.
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(1.4);
  st.setStrokeCap(StrokeCap.Round);
  st.setColor(Skia.Color('#E9C98E'));
  st.setPathEffect(Skia.PathEffect.MakeDash([4, 3], 0));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-W / 2 + 6, -H / 2 + 6, W - 12, H - 12), 6, 6), st);
  // Debossed lettering: a light lower edge under a dark fill.
  const lightInk = Skia.Paint();
  lightInk.setAntiAlias(true);
  lightInk.setColor(Skia.Color('rgba(255,222,184,0.35)'));
  const darkInk = Skia.Paint();
  darkInk.setAntiAlias(true);
  darkInk.setColor(Skia.Color('#5A3216'));
  spaced(c, 'POCKET', title, 0, 7.8, 3, lightInk);
  spaced(c, 'POCKET', title, 0, 6.6, 3, darkInk);
  spaced(c, 'NOTES FOR LATER', small, 0, 25, 1.6, lightInk);
  spaced(c, 'NOTES FOR LATER', small, 0, 24, 1.6, darkInk);
  const rule = Skia.Paint();
  rule.setAntiAlias(true);
  rule.setColor(Skia.Color('rgba(70,36,14,0.6)'));
  c.drawRect(Skia.XYWHRect(-30, 12.5, 60, 1), rule);
  return r.finishRecordingAsPicture();
}

/** Appends a rotated rectangle (centre, half sizes, angle) to a builder. */
function quad(b: ReturnType<typeof Skia.PathBuilder.Make>, x: number, y: number, hw: number, hh: number, ca: number, sa: number) {
  'worklet';
  b.moveTo(x + -hw * ca - -hh * sa, y + -hw * sa + -hh * ca);
  b.lineTo(x + hw * ca - -hh * sa, y + hw * sa + -hh * ca);
  b.lineTo(x + hw * ca - hh * sa, y + hw * sa + hh * ca);
  b.lineTo(x + -hw * ca - hh * sa, y + -hw * sa + hh * ca);
  b.close();
}

/**
 * Every tooth of both tapes for this frame: [shadow, body, highlight]. Each tooth sits across its
 * tape's edge (cx ∓ gap), turned square to the edge; closed teeth interlock across the centre.
 */
export function buildTeeth(z: Zip, ys: number, o: number) {
  'worklet';
  const shadow = Skia.PathBuilder.Make();
  const body = Skia.PathBuilder.Make();
  const light = Skia.PathBuilder.Make();
  const hw = TOOTH_W / 2;
  const hh = TOOTH_H / 2;
  for (let s = -1; s <= 1; s += 2) {
    let y = z.yTop + 2 + (s > 0 ? TOOTH_PITCH / 2 : 0);
    while (y < z.yEnd - 6) {
      const g = gapAt(z, y, ys, o);
      const x = z.cx + s * g - s * 0.3;
      if (x > -12 && x < z.width + 12) {
        const slope = (gapAt(z, y + 1.5, ys, o) - gapAt(z, y - 1.5, ys, o)) / 3;
        const a = -Math.atan(s * slope);
        const ca = Math.cos(a);
        const sa = Math.sin(a);
        quad(shadow, x + 0.6, y + 1.4, hw, hh, ca, sa);
        quad(body, x, y, hw, hh, ca, sa);
        // The lit upper face of the scoop.
        quad(light, x + sa * hh * 0.45, y - ca * hh * 0.45, hw - 1, hh * 0.32, ca, sa);
      }
      y += TOOTH_PITCH;
    }
  }
  return [shadow.build(), body.build(), light.build()];
}
