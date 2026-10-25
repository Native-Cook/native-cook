import { BlendMode, BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

/** Noise is coloured; this makes it grey so grain reads as texture, not as confetti. */
const GRAY = [0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0];

// Static art for the Card Catalog List, recorded once into pictures on the JS thread: the desk
// and the drawer's inside (behind the cards), and the drawer's front panel (over the cards' far
// ends). Nothing here moves, so neither picture sits under an animated transform.

/** The design box every coordinate below is in; the screen scales it to fit. */
export const DESIGN_W = 393;
export const DESIGN_H = 759;

export const CARD_W = 329;
export const CARD_H = 232;
export const CARD_X = (DESIGN_W - CARD_W) / 2;
/** The reading line: the bottom edge every card turns about. */
export const PIVOT_Y = 424;
export const DRAWER = { x: 14, y: 104, w: DESIGN_W - 28, bottom: PIVOT_Y + 66 };
export const FRONT = { x: 6, y: 476, w: DESIGN_W - 12, h: 98 };

function color(hex: string) {
  return Skia.Color(hex);
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(color), pos ?? null, TileMode.Clamp);
}

/** Wood: a three-stop base, long stretched grain noise and a few darker figure lines. */
function wood(c: SkCanvas, x: number, y: number, w: number, h: number, base: string[], grainAlpha: number, seed: number) {
  const r = Skia.XYWHRect(x, y, w, h);
  const p = Skia.Paint();
  p.setShader(linear(x, y, x, y + h, base, [0, 0.55, 1]));
  c.drawRect(r, p);
  // Grain runs left to right: noise stretched sixty times along x.
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeFractalNoise(0.004, 0.24, 3, seed, 0, 0));
  g.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  g.setBlendMode(BlendMode.Overlay);
  g.setAlphaf(grainAlpha);
  c.drawRect(r, g);
  const fine = Skia.Paint();
  fine.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed + 7, 0, 0));
  fine.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  fine.setBlendMode(BlendMode.SoftLight);
  fine.setAlphaf(0.18);
  c.drawRect(r, fine);
}

/** The desk around the drawer and the drawer's dark inside, where the cards stand. */
export function recordBackdrop(w: number, h: number, ox: number, oy: number, k: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
  wood(c, 0, 0, w, h, ['#3A281C', '#2B1D14', '#1C130D'], 0.55, 3);
  c.save();
  c.translate(ox, oy);
  c.scale(k, k);

  // The drawer's inside: a deep box, darkest at the back, its walls lit from the top left.
  const d = DRAWER;
  const inside = Skia.RRectXY(Skia.XYWHRect(d.x, d.y, d.w, d.bottom - d.y), 10, 10);
  const shadow = Skia.Paint();
  shadow.setColor(color('#0A0604'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  shadow.setAlphaf(0.7);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(d.x - 4, d.y + 6, d.w + 8, d.bottom - d.y), 14, 14), shadow);
  const floor = Skia.Paint();
  floor.setShader(linear(0, d.y, 0, d.bottom, ['#140C07', '#22150D', '#2E1D12'], [0, 0.6, 1]));
  c.drawRRect(inside, floor);
  c.save();
  c.clipRRect(inside, ClipOp.Intersect, true);
  // Side walls: two strips of lighter wood, as seen from above the drawer.
  const wall = 12;
  c.save();
  c.clipRect(Skia.XYWHRect(d.x, d.y, wall, d.bottom - d.y), ClipOp.Intersect, true);
  wood(c, d.x, d.y, wall, d.bottom - d.y, ['#5A3F2B', '#4A3323', '#3B281B'], 0.5, 11);
  c.restore();
  c.save();
  c.clipRect(Skia.XYWHRect(d.x + d.w - wall, d.y, wall, d.bottom - d.y), ClipOp.Intersect, true);
  wood(c, d.x + d.w - wall, d.y, wall, d.bottom - d.y, ['#4A3323', '#3B281B', '#2E1F15'], 0.5, 13);
  c.restore();
  // The back wall's shadow and a soft inner shade along the walls.
  const back = Skia.Paint();
  back.setShader(linear(0, d.y, 0, d.y + 90, ['rgba(4,2,1,0.85)', 'rgba(4,2,1,0.35)', 'rgba(4,2,1,0)']));
  c.drawRect(Skia.XYWHRect(d.x, d.y, d.w, 90), back);
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(10);
  rim.setColor(color('#000000'));
  rim.setAlphaf(0.35);
  rim.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawRRect(inside, rim);
  c.restore();
  // The top edge of the walls catches the light.
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(color('#C89A6A'));
  edge.setAlphaf(0.35);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(d.x + 0.5, d.y + 0.5, d.w - 1, d.bottom - d.y - 1), 10, 10), edge);

  c.restore();
  // Vignette over the whole desk.
  const v = Skia.Paint();
  v.setShader(
    Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.42 }, h * 0.78, [color('rgba(0,0,0,0)'), color('rgba(0,0,0,0)'), color('rgba(8,4,2,0.55)')], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), v);
  return rec.finishRecordingAsPicture();
}

/** The drawer's front: a bevelled oak panel, a brass label holder and a brass rod knob. */
export function recordFront(): SkPicture {
  const f = FRONT;
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, f.w + 12, f.h + 24));
  const x = 0;
  const y = 0;
  const panel = Skia.RRectXY(Skia.XYWHRect(x, y, f.w, f.h), 10, 10);
  // Two shadows: a soft one under the panel and a tight contact one.
  const soft = Skia.Paint();
  soft.setColor(color('#000000'));
  soft.setAlphaf(0.45);
  soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 4, y + 12, f.w - 8, f.h), 12, 12), soft);
  const tight = Skia.Paint();
  tight.setColor(color('#000000'));
  tight.setAlphaf(0.5);
  tight.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 1, y + 2, f.w - 2, f.h), 10, 10), tight);
  c.save();
  c.clipRRect(panel, ClipOp.Intersect, true);
  wood(c, x, y, f.w, f.h, ['#8A5E3B', '#6E4A2E', '#55381F'], 0.6, 21);
  // A raised field inside the panel.
  const lit = Skia.Paint();
  lit.setStyle(PaintStyle.Stroke);
  lit.setStrokeWidth(1.5);
  lit.setColor(color('#E2B07C'));
  lit.setAlphaf(0.35);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 14.5, y + 12.5, f.w - 29, f.h - 25), 6, 6), lit);
  const dark = Skia.Paint();
  dark.setStyle(PaintStyle.Stroke);
  dark.setStrokeWidth(1.5);
  dark.setColor(color('#2A180C'));
  dark.setAlphaf(0.55);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 13.5, y + 13.5, f.w - 27, f.h - 27), 6, 6), dark);
  // The top edge, lit; the bottom edge, in shade.
  const top = Skia.Paint();
  top.setShader(linear(0, y, 0, y + 6, ['rgba(255,214,170,0.45)', 'rgba(255,214,170,0)']));
  c.drawRect(Skia.XYWHRect(x, y, f.w, 6), top);
  const bottom = Skia.Paint();
  bottom.setShader(linear(0, y + f.h - 8, 0, y + f.h, ['rgba(20,10,4,0)', 'rgba(20,10,4,0.5)']));
  c.drawRect(Skia.XYWHRect(x, y + f.h - 8, f.w, 8), bottom);
  c.restore();

  // Brass label holder: a frame with a slot, its card shows through (the text is a view on top).
  const lw = 172;
  const lh = 46;
  const lx = x + (f.w - lw) / 2;
  const ly = y + 18;
  brass(c, lx, ly, lw, lh, 5);
  const label = Skia.Paint();
  label.setShader(linear(0, ly + 6, 0, ly + lh - 6, ['#F4ECD8', '#EADFC4', '#DCCDAA']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lx + 6, ly + 6, lw - 12, lh - 12), 2, 2), label);
  const inset = Skia.Paint();
  inset.setStyle(PaintStyle.Stroke);
  inset.setStrokeWidth(1);
  inset.setColor(color('#5C4320'));
  inset.setAlphaf(0.6);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lx + 6, ly + 6, lw - 12, lh - 12), 2, 2), inset);
  // Rivets either side of the holder.
  for (const rx of [lx - 10, lx + lw + 10]) rivet(c, rx, ly + lh / 2);
  // The brass rod's knob below the holder, and a half-moon pull.
  const pullY = ly + lh + 12;
  const pull = Skia.PathBuilder.Make()
    .moveTo(f.w / 2 - 34, pullY)
    .cubicTo(f.w / 2 - 30, pullY + 22, f.w / 2 + 30, pullY + 22, f.w / 2 + 34, pullY)
    .close()
    .build();
  const pullShadow = Skia.Paint();
  pullShadow.setColor(color('#000000'));
  pullShadow.setAlphaf(0.45);
  pullShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.save();
  c.translate(0, 3);
  c.drawPath(pull, pullShadow);
  c.restore();
  const pullPaint = Skia.Paint();
  pullPaint.setShader(linear(f.w / 2 - 34, pullY, f.w / 2 + 34, pullY + 18, ['#F3D58C', '#B98B3A', '#7A5520', '#C79B4B'], [0, 0.45, 0.75, 1]));
  c.drawPath(pull, pullPaint);
  const knobX = x + f.w - 34;
  const knobY = y + f.h / 2;
  const knob = Skia.Paint();
  knob.setShader(Skia.Shader.MakeRadialGradient({ x: knobX - 3, y: knobY - 4 }, 12, [color('#FBE7B0'), color('#C3933F'), color('#6E4A1A')], [0, 0.5, 1], TileMode.Clamp));
  const knobShadow = Skia.Paint();
  knobShadow.setColor(color('#000000'));
  knobShadow.setAlphaf(0.5);
  knobShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.drawCircle(knobX + 1, knobY + 3, 9, knobShadow);
  c.drawCircle(knobX, knobY, 9, knob);
  const hole = Skia.Paint();
  hole.setColor(color('#3A2610'));
  c.drawCircle(knobX, knobY, 2.5, hole);
  return rec.finishRecordingAsPicture();
}

function brass(c: SkCanvas, x: number, y: number, w: number, h: number, r: number) {
  const s = Skia.Paint();
  s.setColor(color('#000000'));
  s.setAlphaf(0.45);
  s.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 2, w, h), r, r), s);
  const p = Skia.Paint();
  p.setShader(linear(x, y, x + w * 0.4, y + h, ['#F6DC98', '#C89A45', '#8C6326', '#D7AE5C'], [0, 0.35, 0.7, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), p);
  const hi = Skia.Paint();
  hi.setStyle(PaintStyle.Stroke);
  hi.setStrokeWidth(1);
  hi.setColor(color('#FFF2C8'));
  hi.setAlphaf(0.6);
  c.drawLine(x + r, y + 0.8, x + w - r, y + 0.8, hi);
}

function rivet(c: SkCanvas, x: number, y: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x: x - 1, y: y - 1.5 }, 4.5, [color('#FFF0C0'), color('#B88A3C'), color('#5E3F14')], [0, 0.55, 1], TileMode.Clamp));
  c.drawCircle(x, y, 3.5, p);
}
