/**
 * The screen in one Skia picture, drawn from the model. What never moves is drawn once at mount into two images (the
 * wall, the table, the machine's body and the inside of the globe behind the pile; the glass's reflections, the lid and
 * the collar in front of it), and each gumball colour into a sprite, so a frame is a few image draws and the moving
 * parts: the handle, the chute's flap, the capsule, the card, the coins and the wallet.
 *
 * Layers, back to front: back image, gumballs, front image, handle, flap, capsule, scrim, card, coins, wallet, hint,
 * the demo's finger.
 */
import {
  BlendMode, BlurStyle, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode,
  type SkCanvas, type SkFont, type SkImage, type SkPaint, type SkTypeface,
} from '@shopify/react-native-skia';

import { BUTTON, CARD, GLOBE, PHASE, PLATE, WALLET_COIN, capsulePose, easeOut, gripAt, span, type Model } from './model';
import { COLLECT, CRANK } from './motion';
import { BALLS, CAPSULE, COLOR, COPY, LAYOUT, STAGE, TYPE, type Layout, type TypeRole } from './theme';

export type Faces = { serif: SkTypeface; regular: SkTypeface; medium: SkTypeface; semibold: SkTypeface };
export type Fonts = Record<TypeRole, SkFont>;
export type SceneCtx = {
  lay: Layout;
  fonts: Fonts;
  back: SkImage;
  front: SkImage;
  balls: SkImage[];
  px: number; // pixels per stage point in the images
};

const SPRITE = 48; // a ball sprite's radius in stage points, drawn at px scale

// ——— Painting helpers (JS thread and worklets) ———

function paintOf(color: string, alpha = 1): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(p.getAlphaf() * Math.max(alpha, 0));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function blurred(p: SkPaint, sigma: number): SkPaint {
  'worklet';
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function advance(font: SkFont, s: string): number {
  'worklet';
  if (!s) return 0;
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, alpha = 1, align = 0) {
  'worklet';
  if (alpha <= 0.004 || !s) return;
  const w = advance(font, s);
  const left = align === 0 ? x : align === 1 ? x - w / 2 : x - w;
  c.drawText(s, left, y, paintOf(color, alpha), font);
}

/** Letter-spaced capitals, centred on x. */
function spaced(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, track: number, alpha = 1) {
  'worklet';
  let w = 0;
  for (let i = 0; i < s.length; i++) w += advance(font, s[i]) + (i < s.length - 1 ? track : 0);
  let cx = x - w / 2;
  for (let i = 0; i < s.length; i++) {
    text(c, s[i], cx, y, font, color, alpha);
    cx += advance(font, s[i]) + track;
  }
}

/** "1,290". */
function grouped(n: number): string {
  'worklet';
  const v = Math.round(n);
  const s = String(v);
  return s.length > 3 ? `${s.slice(0, s.length - 3)},${s.slice(s.length - 3)}` : s;
}

/** A chrome cylinder seen from the front: bands of a bright room reflected across it, lit from the left. */
function chromeAcross(x0: number, x1: number): SkPaint {
  'worklet';
  return linear(x0, 0, x1, 0,
    [COLOR.chromeLow, COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeDeep, COLOR.chromeMid, COLOR.chromeHi, COLOR.chromeLow],
    [0, 0.16, 0.34, 0.52, 0.72, 0.88, 1]);
}

/** A coin, face on, radius r. */
function coin(c: SkCanvas, x: number, y: number, r: number, alpha = 1) {
  'worklet';
  if (alpha <= 0.01) return;
  const body = radial(x - r * 0.35, y - r * 0.4, r * 1.5, [COLOR.coinLight, COLOR.coin, COLOR.coinDeep], [0, 0.55, 1]);
  body.setAlphaf(alpha);
  c.drawCircle(x, y, r, body);
  const rim = paintOf(COLOR.coinDeep, 0.7 * alpha);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(Math.max(1, r * 0.12));
  c.drawCircle(x, y, r * 0.72, rim);
  c.drawCircle(x - r * 0.3, y - r * 0.32, r * 0.18, paintOf('#FFFFFF', 0.7 * alpha));
}

// ——— Images made once (JS thread) ———

function surface(w: number, h: number) {
  return Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
}

function snapshot(s: NonNullable<ReturnType<typeof surface>>): SkImage {
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

function bodyPath() {
  const b = LAYOUT.body;
  const cx = STAGE.w / 2;
  const t = b.topW / 2;
  const bo = b.bottomW / 2;
  return Skia.PathBuilder.Make()
    .moveTo(cx - t, b.top)
    .lineTo(cx + t, b.top)
    .cubicTo(cx + t + 6, b.top + 80, cx + bo, b.bottom - 70, cx + bo, b.bottom - 12)
    .quadTo(cx + bo, b.bottom, cx + bo - 12, b.bottom)
    .lineTo(cx - bo + 12, b.bottom)
    .quadTo(cx - bo, b.bottom, cx - bo, b.bottom - 12)
    .cubicTo(cx - bo, b.bottom - 70, cx - t - 6, b.top + 80, cx - t, b.top)
    .close()
    .build();
}

function drawRoom(c: SkCanvas) {
  const W = STAGE.w;
  const H = 1100; // past the stage, so a tall screen never shows an edge
  c.drawRect(Skia.XYWHRect(-200, -200, W + 400, H + 400), linear(0, 0, 0, LAYOUT.table, [COLOR.wallTop, COLOR.wallBottom], null));
  // The window's light falls on the wall high on the left.
  c.drawRect(Skia.XYWHRect(-200, -200, W + 400, LAYOUT.table + 200), radial(60, 120, 420, ['rgba(255,250,238,0.75)', 'rgba(255,250,238,0)'], null));
  // The table: oak seen from above, lighter where it meets the wall, a little darker toward you, with its grain.
  c.drawRect(Skia.XYWHRect(-200, LAYOUT.table, W + 400, H), linear(0, LAYOUT.table, 0, STAGE.h, [COLOR.tableEdge, COLOR.tableTop, COLOR.tableFront], [0, 0.18, 1]));
  const grain = paintOf(COLOR.tableDeep, 0.1);
  grain.setStyle(PaintStyle.Stroke);
  grain.setStrokeWidth(0.8);
  for (let k = 0; k < 26; k++) {
    const y0 = LAYOUT.table + 8 + k * 6.3 + Math.sin(k * 1.7) * 2;
    const g = Skia.PathBuilder.Make().moveTo(-20, y0);
    for (let x = 0; x <= W + 40; x += 40) g.quadTo(x + 20, y0 + Math.sin(k * 0.9 + x * 0.013) * 2.4, x + 40, y0 + Math.sin(k * 0.6 + x * 0.02) * 1.2);
    grain.setAlphaf(0.05 + 0.06 * ((k * 37) % 5) / 5);
    c.drawPath(g.build(), grain);
  }
  // Where the wall meets the table: a soft line of shade.
  c.drawRect(Skia.XYWHRect(-200, LAYOUT.table - 2, W + 400, 10), blurred(paintOf('#5B3A1C', 0.22), 3));
  c.drawRect(Skia.XYWHRect(-200, LAYOUT.table, W + 400, 1), paintOf('#FFF4E2', 0.5));
  // The machine's shadow on the table, cast away from the light.
  const cx = STAGE.w / 2;
  c.drawOval(Skia.XYWHRect(cx - 132, LAYOUT.foot.y + 4, 290, 30), blurred(paintOf('#3D2410', 0.38), 9));
  c.drawOval(Skia.XYWHRect(cx - 118, LAYOUT.foot.y + 6, 236, 14), blurred(paintOf('#2A170A', 0.45), 3));
}

function drawGlobeBack(c: SkCanvas) {
  const { x, y } = GLOBE;
  const R = LAYOUT.globe.r;
  // The far side of the glass: the wall seen through it, a shade darker and cooler, darker toward the rim.
  c.drawCircle(x, y, R, radial(x - 20, y - 30, R * 1.05, ['rgba(255,255,255,0.10)', 'rgba(120,100,80,0.10)', 'rgba(70,50,35,0.28)'], [0, 0.75, 1]));
  // The wall's light, reflected on the inside of the far glass, low on the right.
  c.drawOval(Skia.XYWHRect(x + R * 0.18, y + R * 0.38, R * 0.62, R * 0.32), blurred(paintOf('#FFF3DF', 0.22), 10));
}

function drawBody(c: SkCanvas) {
  const b = LAYOUT.body;
  const cx = STAGE.w / 2;
  const path = bodyPath();
  const x0 = cx - b.bottomW / 2;
  const x1 = cx + b.bottomW / 2;
  c.drawPath(path, linear(x0, 0, x1, 0,
    [COLOR.enamelDeep, COLOR.enamel, COLOR.enamelLight, COLOR.enamel, '#9E1C25', COLOR.enamelDeep], [0, 0.1, 0.27, 0.48, 0.8, 1]));
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  // The enamel catches the window as a soft vertical streak, and darkens into the collar's shadow and toward the foot.
  c.drawRect(Skia.XYWHRect(cx - 62, b.top - 10, 22, b.bottom - b.top + 20), blurred(paintOf('#FFFFFF', 0.36), 7));
  c.drawRect(Skia.XYWHRect(cx - 54, b.top, 5, b.bottom - b.top), blurred(paintOf('#FFFFFF', 0.4), 1.5));
  c.drawRect(Skia.XYWHRect(x0, b.top, b.bottomW, 26), linear(0, b.top, 0, b.top + 26, ['rgba(40,0,4,0.45)', 'rgba(40,0,4,0)'], null));
  c.drawRect(Skia.XYWHRect(x0, b.bottom - 40, b.bottomW, 40), linear(0, b.bottom - 40, 0, b.bottom, ['rgba(40,0,4,0)', 'rgba(40,0,4,0.35)'], null));
  // Cast metal is not perfectly smooth: a fine orange peel in the paint.
  for (let k = 0; k < 220; k++) {
    const px = x0 + ((k * 73) % 228);
    const py = b.top + ((k * 151) % (b.bottom - b.top));
    c.drawCircle(px, py, 0.6, paintOf(k % 2 ? '#FFFFFF' : '#300006', 0.05));
  }
  c.restore();

  // The plate: a chrome escutcheon with a coin slot, around the handle's hub.
  const { x: px, y: py } = PLATE;
  const r = LAYOUT.plate.r;
  c.drawCircle(px, py + 3, r + 1, blurred(paintOf('#2A0004', 0.45), 4));
  c.drawCircle(px, py, r, radial(px - r * 0.4, py - r * 0.5, r * 1.8, [COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeLow, COLOR.chromeDeep], [0, 0.35, 0.75, 1]));
  const bevel = Skia.Paint();
  bevel.setAntiAlias(true);
  bevel.setStyle(PaintStyle.Stroke);
  bevel.setStrokeWidth(3);
  bevel.setShader(Skia.Shader.MakeLinearGradient({ x: px - r, y: py - r }, { x: px + r, y: py + r },
    [Skia.Color('#FFFFFF'), Skia.Color(COLOR.chromeMid), Skia.Color(COLOR.chromeDeep)], [0, 0.5, 1], TileMode.Clamp));
  c.drawCircle(px, py, r - 3, bevel);
  c.drawRRect(rrect(px - 3, py - r + 10, 6, 18, 3), paintOf(COLOR.slot));
  c.drawRRect(rrect(px - 3, py - r + 10, 6, 2, 1), paintOf('#000000', 0.6));

  // The chute's door: a dark opening in a chrome frame.
  const d = LAYOUT.door;
  const dx = cx - d.w / 2;
  c.drawRRect(rrect(dx - 5, d.top - 5, d.w + 10, d.h + 10, d.r + 5), linear(dx, d.top, dx, d.top + d.h, [COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeDeep], [0, 0.45, 1]));
  c.drawRRect(rrect(dx, d.top, d.w, d.h, d.r), linear(0, d.top, 0, d.top + d.h, ['#140A08', '#2B1612', '#3A1E17'], [0, 0.6, 1]));

  // The foot: a chrome ring on the table.
  const f = LAYOUT.foot;
  c.drawRRect(rrect(cx - f.w / 2, f.y, f.w, f.h, 6), chromeAcross(cx - f.w / 2, cx + f.w / 2));
  c.drawRect(Skia.XYWHRect(cx - f.w / 2 + 6, f.y + 1, f.w - 12, 1), paintOf('#FFFFFF', 0.7));
}

function drawGlassFront(c: SkCanvas) {
  const { x, y } = GLOBE;
  const R = LAYOUT.globe.r;
  // Fresnel: the glass brightens toward its rim.
  c.drawCircle(x, y, R, radial(x, y, R, ['rgba(255,255,255,0)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0.16)'], [0, 0.78, 1]));
  // The window, reflected as a curved pane on the upper left, and a hard specular where the light is.
  const pane = Skia.PathBuilder.Make()
    .addArc(Skia.XYWHRect(x - R + 14, y - R + 14, 2 * R - 28, 2 * R - 28), 196, 62)
    .build();
  const band = paintOf('#FFFFFF', 0.5);
  band.setStyle(PaintStyle.Stroke);
  band.setStrokeWidth(16);
  band.setStrokeCap(StrokeCap.Round);
  c.drawPath(pane, blurred(band, 5));
  const thin = paintOf('#FFFFFF', 0.85);
  thin.setStyle(PaintStyle.Stroke);
  thin.setStrokeWidth(3);
  thin.setStrokeCap(StrokeCap.Round);
  const pane2 = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(x - R + 10, y - R + 10, 2 * R - 20, 2 * R - 20), 202, 40).build();
  c.drawPath(pane2, blurred(thin, 0.8));
  c.drawOval(Skia.XYWHRect(x - R * 0.5, y - R * 0.62, 20, 12), blurred(paintOf('#FFFFFF', 0.95), 2));
  // The table's warmth reflected low on the right.
  const warm = paintOf('#FFD9A8', 0.4);
  warm.setStyle(PaintStyle.Stroke);
  warm.setStrokeWidth(8);
  warm.setStrokeCap(StrokeCap.Round);
  c.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(x - R + 8, y - R + 8, 2 * R - 16, 2 * R - 16), 22, 48).build(), blurred(warm, 4));
  // The rim: a hairline of light, brightest toward the window.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.6);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: x - R, y: y - R }, { x: x + R, y: y + R },
    [Skia.Color('rgba(255,255,255,0.9)'), Skia.Color('rgba(255,255,255,0.25)'), Skia.Color('rgba(255,255,255,0.55)')], [0, 0.6, 1], TileMode.Clamp));
  c.drawCircle(x, y, R - 0.8, rim);

  // The collar the globe sits in, and the lid on top: chrome.
  const co = LAYOUT.collar;
  const cx = STAGE.w / 2;
  c.drawRRect(rrect(cx - co.w / 2, co.y + 3, co.w, co.h, 8), blurred(paintOf('#2A0004', 0.4), 4));
  c.drawRRect(rrect(cx - co.w / 2, co.y, co.w, co.h, 8), chromeAcross(cx - co.w / 2, cx + co.w / 2));
  c.drawRect(Skia.XYWHRect(cx - co.w / 2 + 8, co.y + 1.5, co.w - 16, 1.2), paintOf('#FFFFFF', 0.75));
  c.drawRect(Skia.XYWHRect(cx - co.w / 2 + 4, co.y + co.h - 3, co.w - 8, 2), paintOf(COLOR.chromeDeep, 0.6));

  const l = LAYOUT.lid;
  const top = y - R - l.h + 12;
  const lid = Skia.PathBuilder.Make()
    .moveTo(cx - l.w / 2, top + l.h)
    .cubicTo(cx - l.w / 2, top + 4, cx - l.w / 4, top, cx, top)
    .cubicTo(cx + l.w / 4, top, cx + l.w / 2, top + 4, cx + l.w / 2, top + l.h)
    .close()
    .build();
  c.drawPath(lid, chromeAcross(cx - l.w / 2, cx + l.w / 2));
  c.drawPath(lid, linear(0, top, 0, top + l.h, ['rgba(255,255,255,0.35)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.18)'], [0, 0.5, 1]));
  c.drawRRect(rrect(cx - l.w / 2 - 4, top + l.h - 6, l.w + 8, 9, 4.5), chromeAcross(cx - l.w / 2 - 4, cx + l.w / 2 + 4));
  // The finial.
  const fy = top - l.knob + 3;
  c.drawCircle(cx, fy, l.knob, radial(cx - 4, fy - 5, l.knob * 1.6, [COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeDeep], [0, 0.4, 1]));
  c.drawCircle(cx - 4, fy - 4, 3, paintOf('#FFFFFF', 0.9));
}

function drawHeader(c: SkCanvas, fonts: Fonts) {
  text(c, COPY.title, LAYOUT.edge, LAYOUT.titleBase, fonts.title, COLOR.ink);
  text(c, COPY.sub, LAYOUT.edge, LAYOUT.subBase, fonts.sub, COLOR.mute);
}

/** A gumball colour as a sprite: lit from the upper left, a hard highlight, darker where it turns away. */
function ballSprite(color: string, px: number): SkImage {
  const size = Math.ceil(SPRITE * 2 * px);
  const s = surface(size, size)!;
  const c = s.getCanvas();
  c.scale(px, px);
  const r = SPRITE;
  c.drawCircle(r, r, r - 0.5, radial(r - r * 0.38, r - r * 0.45, r * 1.75, ['#FFFFFF', color, color, '#000000'], [0, 0.22, 0.62, 1.25]));
  c.drawCircle(r, r, r - 0.5, radial(r, r, r, ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(20,8,0,0.28)'], [0, 0.72, 1]));
  // The wall's light bounced up into its lower right.
  c.drawOval(Skia.XYWHRect(r + r * 0.1, r + r * 0.45, r * 0.6, r * 0.3), blurred(paintOf('#FFF1DC', 0.22), r * 0.08));
  c.drawOval(Skia.XYWHRect(r - r * 0.55, r - r * 0.62, r * 0.36, r * 0.24), blurred(paintOf('#FFFFFF', 0.9), r * 0.05));
  return snapshot(s);
}

export function makeScene(lay: Layout, faces: Faces, px: number): SceneCtx | null {
  const fonts = {} as Fonts;
  for (const role of Object.keys(TYPE) as TypeRole[]) fonts[role] = Skia.Font(faces[TYPE[role].font as keyof Faces], TYPE[role].size);
  const W = Math.ceil(STAGE.w * px);
  const H = Math.ceil(STAGE.h * px);
  const sb = surface(W, H);
  const sf = surface(W, H);
  if (!sb || !sf) return null;
  const cb = sb.getCanvas();
  cb.scale(px, px);
  drawRoom(cb);
  drawBody(cb);
  drawGlobeBack(cb);
  drawHeader(cb, fonts);
  const cf = sf.getCanvas();
  cf.scale(px, px);
  drawGlassFront(cf);
  const balls = BALLS.map((b) => ballSprite(b, px));
  return { lay, fonts, back: snapshot(sb), front: snapshot(sf), balls, px };
}

// ——— The frame ———

function image(c: SkCanvas, img: SkImage, px: number) {
  'worklet';
  c.save();
  c.scale(1 / px, 1 / px);
  c.drawImage(img, 0, 0);
  c.restore();
}

function drawBalls(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const src = Skia.XYWHRect(0, 0, ctx.balls[0].width(), ctx.balls[0].height());
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // The lowest balls first, so the ones on top sit over them.
  for (let i = 0; i < m.bx.length; i++) {
    const r = m.br[i];
    c.drawImageRectOptions(ctx.balls[m.bc[i]], src, Skia.XYWHRect(m.bx[i] - r, m.by[i] - r, 2 * r, 2 * r), FilterMode.Linear, MipmapMode.None, p);
  }
}

function drawHandle(c: SkCanvas, m: Model) {
  'worklet';
  const a = m.angle + m.wob * 0.06;
  const { x, y } = PLATE;
  const [gx, gy] = gripAt(a);
  // The arm's shadow on the plate, toward the lower right.
  const sh = paintOf('#1A0A06', 0.32);
  sh.setStyle(PaintStyle.Stroke);
  sh.setStrokeWidth(9);
  sh.setStrokeCap(StrokeCap.Round);
  c.drawLine(x + 3, y + 4, gx + 3, gy + 4, blurred(sh, 2.5));
  // The arm: a chrome bar, its highlight along the side facing the light.
  const arm = paintOf(COLOR.chromeMid);
  arm.setStyle(PaintStyle.Stroke);
  arm.setStrokeWidth(9);
  arm.setStrokeCap(StrokeCap.Round);
  c.drawLine(x, y, gx, gy, arm);
  const hi = paintOf('#FFFFFF', 0.85);
  hi.setStyle(PaintStyle.Stroke);
  hi.setStrokeWidth(2.2);
  hi.setStrokeCap(StrokeCap.Round);
  const nx = -Math.cos(a) * 2.2;
  const ny = -Math.sin(a) * 2.2;
  c.drawLine(x + nx, y + ny, gx + nx, gy + ny, hi);
  // The hub.
  const H = LAYOUT.hub;
  c.drawCircle(x, y, H, radial(x - 4, y - 5, H * 1.8, [COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeDeep], [0, 0.4, 1]));
  c.drawCircle(x, y, 3.5, paintOf(COLOR.chromeDeep));
  // The grip: a cherry bakelite knob that matches the body.
  const G = LAYOUT.grip;
  c.drawCircle(gx + 2, gy + 3, G, blurred(paintOf('#1A0A06', 0.35), 2.5));
  c.drawCircle(gx, gy, G, radial(gx - G * 0.4, gy - G * 0.45, G * 1.7, ['#FF8A84', COLOR.enamelLight, COLOR.enamel, COLOR.enamelDeep], [0, 0.2, 0.55, 1]));
  c.drawCircle(gx - G * 0.36, gy - G * 0.4, G * 0.22, paintOf('#FFFFFF', 0.85));
}

/** The chute's flap: closed, a chrome plate over the door; it swings out and up as the capsule pushes through. */
function drawFlap(c: SkCanvas, m: Model) {
  'worklet';
  let open = 0;
  if (m.phase === PHASE.dispensing) {
    const t = m.t;
    open = t < 0.5 ? easeOut(span(t, 0.22, 0.5)) : 1 - easeOut(span(t, 0.75, 1.15));
    if (m.reduced) open = 0;
  }
  const d = LAYOUT.door;
  const cx = STAGE.w / 2;
  const h = d.h * Math.cos(open * 1.25);
  const top = d.top;
  const p = linear(0, top, 0, top + Math.max(h, 1),
    open > 0.5 ? [COLOR.chromeLow, COLOR.chromeHi] : [COLOR.chromeHi, COLOR.chromeMid, COLOR.chromeLow], open > 0.5 ? null : [0, 0.5, 1]);
  c.drawRRect(rrect(cx - d.w / 2 + 3, top + 2, d.w - 6, Math.max(h - 4, 2), Math.min(d.r - 3, Math.max(h - 4, 2) / 2)), p);
  if (h > 10) {
    // The flap's pull: a small ridge near its lower edge.
    c.drawRRect(rrect(cx - 18, top + h - 14, 36, 4, 2), paintOf(COLOR.chromeDeep, 0.6));
    c.drawRect(Skia.XYWHRect(cx - 16, top + h - 14, 32, 1), paintOf('#FFFFFF', 0.7));
  }
}

function drawCapsule(c: SkCanvas, m: Model) {
  'worklet';
  const p = capsulePose(m);
  if (p.a <= 0.01) return;
  const r = CAPSULE.r * p.s;
  // Its shadow on the table, softer the further it is from the light.
  const onTable = p.y > LAYOUT.foot.y;
  if (onTable) c.drawOval(Skia.XYWHRect(p.x - r * 1.1 + 4, p.y + r * 0.62, r * 2.4, r * 0.55), blurred(paintOf('#3D2410', 0.4 * p.a), 4));
  // Opening: the clear top pops off, flips over and lands upside down on the table to the left.
  const liftX = -p.split * 62 * p.s;
  const liftY = (-Math.sin(p.split * Math.PI) * 34 + p.split * 4) * p.s;
  const tilt = -p.split * 2.75;
  c.save();
  c.translate(p.x, p.y);
  c.rotate((p.rot * 180) / Math.PI, 0, 0);
  // The bottom: mint plastic, the seam a darker ring.
  const bottom = Skia.PathBuilder.Make()
    .moveTo(-r, 0)
    .arcToOval(Skia.XYWHRect(-r, -r, 2 * r, 2 * r), 180, -180, false)
    .close()
    .build();
  const bp = radial(-r * 0.4, r * 0.1, r * 1.8, [CAPSULE.mintLight, CAPSULE.mint, CAPSULE.mintDeep], [0, 0.45, 1]);
  bp.setAlphaf(p.a);
  c.drawPath(bottom, bp);
  c.drawRect(Skia.XYWHRect(-r, -1, 2 * r, 2.4), paintOf(CAPSULE.mintDeep, 0.8 * p.a));
  // Inside: the coin, seen once the top comes off (and through the clear top before).
  coin(c, 0, -r * 0.08, r * 0.42, p.a);
  // The top: clear plastic, lifted and tipped back as the capsule opens.
  c.save();
  c.translate(liftX, liftY);
  c.rotate((tilt * 180) / Math.PI, 0, 0);
  const top = Skia.PathBuilder.Make()
    .moveTo(-r, 0)
    .arcToOval(Skia.XYWHRect(-r, -r, 2 * r, 2 * r), 180, 180, false)
    .close()
    .build();
  // Clear plastic: the table shows through tinted and a shade darker toward its curved edge, lit along its rim.
  const clear = radial(-r * 0.2, -r * 0.35, r * 1.2, ['rgba(255,255,255,0.18)', 'rgba(255,255,255,0.1)', 'rgba(90,60,40,0.28)'], [0, 0.6, 1]);
  clear.setAlphaf(p.a);
  c.drawPath(top, clear);
  const edge = paintOf('#FFFFFF', 0.8 * p.a);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.2);
  c.drawPath(top, edge);
  c.drawRect(Skia.XYWHRect(-r, -2.2, 2 * r, 2.2), paintOf('#FFFFFF', 0.55 * p.a));
  const glint = paintOf('#FFFFFF', 0.85 * p.a);
  glint.setStyle(PaintStyle.Stroke);
  glint.setStrokeWidth(2.4);
  glint.setStrokeCap(StrokeCap.Round);
  c.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-r + 5, -r + 5, 2 * r - 10, 2 * r - 10), 200, 50).build(), blurred(glint, 0.6));
  c.restore();
  c.restore();
}

function drawCard(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { fonts } = ctx;
  let k = m.card;
  let alpha = Math.min(1, k * 1.6);
  let drop = 0;
  if (m.phase === PHASE.collecting) {
    const u = span(m.t, COLLECT.card[0], COLLECT.card[1]);
    alpha = 1 - u;
    drop = m.reduced ? 0 : 50 * easeOut(u);
    k = 1;
  }
  if (alpha <= 0.01) return;
  const rise = m.reduced ? 0 : (1 - k) * 120;
  const s = m.reduced ? 1 : 0.72 + 0.28 * k;
  const cx = CARD.x + CARD.w / 2;
  const by = CARD.y + CARD.h;
  c.save();
  c.translate(cx, by + rise + drop);
  c.scale(s, s);
  c.translate(-cx, -by);
  const card = rrect(CARD.x, CARD.y, CARD.w, CARD.h, LAYOUT.card.r);
  c.drawRRect(rrect(CARD.x + 4, CARD.y + 18, CARD.w - 8, CARD.h - 8, LAYOUT.card.r), blurred(paintOf('#1A0C04', 0.35 * alpha), 18));
  const paper = linear(0, CARD.y, 0, CARD.y + CARD.h, [COLOR.card, '#FBF4E8'], null);
  paper.setAlphaf(alpha);
  c.drawRRect(card, paper);
  const edge = paintOf('#FFFFFF', alpha);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  c.drawRRect(rrect(CARD.x + 0.5, CARD.y + 0.5, CARD.w - 1, CARD.h - 1, LAYOUT.card.r), edge);
  spaced(c, COPY.eyebrow, cx, CARD.y + 34, fonts.eyebrow, COLOR.mute, 1.6, alpha);
  // "+50 coins": the figure in the serif, a coin before it, the word smaller after it, as one line centred.
  const pw = advance(fonts.prize, COPY.prize);
  const uw = advance(fonts.prizeUnit, COPY.unit);
  const total = 30 + 10 + pw + 8 + uw;
  const x0 = cx - total / 2;
  const base = CARD.y + 104;
  // Each coin that has left the card is gone from it: the icon dims as they fly.
  let left = 0;
  if (m.phase === PHASE.collecting) left = span(m.t, 0, COLLECT.stagger * COLLECT.coins);
  coin(c, x0 + 15, base - 20, 15, alpha * (1 - 0.6 * left));
  text(c, COPY.prize, x0 + 40, base, fonts.prize, COLOR.ink, alpha);
  text(c, COPY.unit, x0 + 40 + pw + 8, base, fonts.prizeUnit, COLOR.ink, alpha);
  text(c, COPY.cardSub, cx, CARD.y + 134, fonts.cardSub, COLOR.mute, alpha, 1);
  // Collect: a dark pill, pressed in by a finger.
  const press = m.press;
  const bs = 1 - 0.03 * press;
  const bcx = BUTTON.x + BUTTON.w / 2;
  const bcy = BUTTON.y + BUTTON.h / 2;
  c.save();
  c.translate(bcx, bcy);
  c.scale(bs, bs);
  c.translate(-bcx, -bcy);
  c.drawRRect(rrect(BUTTON.x, BUTTON.y, BUTTON.w, BUTTON.h, BUTTON.h / 2), paintOf(COLOR.button, alpha * (1 - 0.12 * press)));
  text(c, COPY.collect, bcx, bcy + 6, fonts.button, COLOR.buttonInk, alpha, 1);
  c.restore();
  c.restore();
}

/** The coins on their way from the card to the wallet: an arc up and over, each a little after the last. */
function drawCoins(c: SkCanvas, m: Model) {
  'worklet';
  if (m.phase !== PHASE.collecting || m.reduced) return;
  const sx = CARD.x + CARD.w / 2 - 70;
  const sy = CARD.y + 84;
  for (let i = 0; i < COLLECT.coins; i++) {
    const u = span(m.t, i * COLLECT.stagger, i * COLLECT.stagger + COLLECT.fly);
    if (u <= 0 || u >= 1) continue;
    const e = easeOut(u);
    const x = sx + (WALLET_COIN.x - sx) * e + Math.sin(u * Math.PI) * (i % 2 ? 18 : -10);
    const y = sy + (WALLET_COIN.y - sy) * e - Math.sin(u * Math.PI) * (70 + i * 6);
    coin(c, x, y, 13 - 4 * e, 1);
  }
}

function drawWallet(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { fonts } = ctx;
  const w = LAYOUT.wallet;
  const W = 92;
  const x = STAGE.w - LAYOUT.edge - W;
  const s = 1 + 0.06 * m.walletBump;
  const cx = x + W / 2;
  const cy = w.y + w.h / 2;
  c.save();
  c.translate(cx, cy);
  c.scale(s, s);
  c.translate(-cx, -cy);
  c.drawRRect(rrect(x, w.y + 3, W, w.h, w.h / 2), blurred(paintOf('#5B3A1C', 0.18), 5));
  c.drawRRect(rrect(x, w.y, W, w.h, w.h / 2), paintOf(COLOR.pill));
  const edge = paintOf('#FFFFFF');
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  c.drawRRect(rrect(x + 0.5, w.y + 0.5, W - 1, w.h - 1, w.h / 2), edge);
  coin(c, x + w.pad + w.coin, cy, w.coin, 1);
  // The balance: the old figure rolls up and out as the new one rolls in.
  const tx = x + W - w.pad;
  const mix = m.walletMix;
  const roll = m.reduced ? 0 : 10;
  c.save();
  c.clipRect(Skia.XYWHRect(x, w.y, W, w.h), ClipOp.Intersect, true);
  if (mix < 1) text(c, grouped(m.walletPrev), tx, cy + 5 - roll * mix, fonts.wallet, COLOR.ink, 1 - mix, 2);
  text(c, grouped(m.wallet), tx, cy + 5 + roll * (1 - mix), fonts.wallet, COLOR.ink, mix, 2);
  c.restore();
  c.restore();
}

/** The line under the machine, and four dots that fill as the handle clicks round. */
function drawHint(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { fonts } = ctx;
  const copy = [COPY.hint, COPY.hintBusy, COPY.hintDone];
  const y = LAYOUT.hintBase;
  const k = m.hintT;
  const under = 1 - m.scrim; // the card has the stage: the line steps back
  if (k < 1) text(c, copy[m.hintFrom], STAGE.w / 2, y, fonts.hint, COLOR.ink, (1 - k) * 0.85 * under, 1);
  text(c, copy[m.hint], STAGE.w / 2, y, fonts.hint, COLOR.ink, k * 0.85 * under, 1);
  const show = m.hint === 0 ? k : m.hintFrom === 0 ? 1 - k : 0;
  if (show <= 0.01) return;
  const filled = m.phase === PHASE.dispensing ? CRANK.detents : m.detent;
  for (let i = 0; i < CRANK.detents; i++) {
    const dx = STAGE.w / 2 + (i - 1.5) * 14;
    const on = i < filled;
    c.drawCircle(dx, y - 30, 3.5, paintOf(on ? COLOR.enamel : COLOR.ink, (on ? 1 : 0.2) * show));
  }
}

export function drawScene(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { lay } = ctx;
  c.drawColor(Skia.Color(COLOR.wallTop));
  c.save();
  c.translate(lay.ox, lay.oy);
  c.scale(lay.s, lay.s);
  image(c, ctx.back, ctx.px);
  // A tall screen: the table goes on below the stage.
  c.drawRect(Skia.XYWHRect(-lay.ox / lay.s, STAGE.h - 1, lay.width / lay.s, lay.height / lay.s), paintOf(COLOR.tableFront));
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(GLOBE.x, GLOBE.y, LAYOUT.globe.r - 1).build(), ClipOp.Intersect, true);
  drawBalls(c, ctx, m);
  c.restore();
  image(c, ctx.front, ctx.px);
  drawHandle(c, m);
  drawCapsule(c, m);
  drawFlap(c, m);
  if (m.scrim > 0.005) {
    const sp = paintOf(COLOR.scrim, m.scrim);
    sp.setBlendMode(BlendMode.SrcOver);
    c.drawRect(Skia.XYWHRect(-lay.ox / lay.s, -lay.oy / lay.s, lay.width / lay.s, lay.height / lay.s), sp);
    // The capsule stays above the scrim: the card comes out of it.
    drawCapsule(c, m);
  }
  drawCard(c, ctx, m);
  drawCoins(c, m);
  drawWallet(c, ctx, m);
  drawHint(c, ctx, m);
  if (m.ghostO > 0.01) {
    const r = (LAYOUT.finger / 2) * (1 - 0.12 * m.ghostP);
    c.drawCircle(m.ghostX, m.ghostY, r, paintOf(COLOR.finger, m.ghostO));
    const e = paintOf(COLOR.fingerEdge, m.ghostO);
    e.setStyle(PaintStyle.Stroke);
    e.setStrokeWidth(1);
    c.drawCircle(m.ghostX, m.ghostY, r - 0.5, e);
  }
  c.restore();
}
