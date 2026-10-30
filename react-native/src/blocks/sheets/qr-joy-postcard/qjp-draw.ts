// QR Joy Postcard — drawing. The page under the sheet and the hero's backdrop are recorded once;
// everything else is drawn every frame from a Frame, in the 402 × 874 reference space: the page
// hangs from the top of the screen and the sheet stands on its bottom edge.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FillType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkMaskFilter,
  type SkPath,
  type SkPicture,
  type SkPoint,
  type SkShader,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  processTransform3d,
} from '@shopify/react-native-skia';

import { BACK_DONE, type Frame, LIFT, LOOP, mix, modulePose, moduleU, smooth, SWIRL_DONE, T, TURN_LIFTED } from './qjp-motion';
import {
  BEAR,
  BUBBLE,
  C,
  CARD,
  COPY,
  FINDERS,
  GIFT,
  MODULES,
  PAGE,
  QR,
  QR_N,
  REF_H,
  REF_W,
  SHEET,
  SIZE,
  SPARKLES,
  STICKER,
} from './qjp-theme';

// ------------------------------------------------------------------ labels

/** A line of text laid out once: glyphs and their x positions. */
export type Label = { font: SkFont; glyphs: number[]; pos: SkPoint[]; width: number; size: number };

export function makeLabel(face: SkTypeface, str: string, size: number): Label {
  const font = Skia.Font(face, size);
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i];
  }
  return { font, glyphs: ids, pos, width: x, size };
}

/** Draws `l` with the middle of its cap height on `cy`; `ax` 0 left, 0.5 centre, 1 right. */
export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.36, l.font, paint);
}

export type Faces = { regular: SkTypeface; semi: SkTypeface; bold: SkTypeface; hand: SkTypeface };

export type Labels = {
  orders: Label;
  purchases: Label;
  promo: Label;
  pick1: Label;
  pick2: Label;
  point: Label;
  title: Label;
  sub: Label;
  steps: Label[][];
  stepNo: Label[];
  ctaA: Label;
  ctaB: Label;
};

export function makeLabels(f: Faces): Labels {
  return {
    orders: makeLabel(f.semi, COPY.orders, SIZE.orders),
    purchases: makeLabel(f.semi, COPY.purchases, SIZE.row),
    promo: makeLabel(f.semi, COPY.promo, SIZE.promo),
    pick1: makeLabel(f.bold, COPY.pick1, SIZE.pick),
    pick2: makeLabel(f.bold, COPY.pick2, SIZE.pick),
    point: makeLabel(f.regular, COPY.point, SIZE.point),
    title: makeLabel(f.bold, COPY.title, SIZE.title),
    sub: makeLabel(f.regular, COPY.sub, SIZE.body),
    steps: COPY.steps.map((lines) => lines.map((s) => makeLabel(f.regular, s, SIZE.body))),
    stepNo: ['1', '2'].map((s) => makeLabel(f.semi, s, SIZE.stepNo)),
    ctaA: makeLabel(f.semi, COPY.ctaA, SIZE.cta),
    ctaB: makeLabel(f.semi, COPY.ctaB, SIZE.cta),
  };
}

// ------------------------------------------------------------------ colours

type RGB = [number, number, number];
function rgb(hex: string): RGB {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
const K = {
  ink: rgb(C.qrInk),
  petal: rgb(C.petal),
  dotA: rgb(C.dotA),
  dotB: rgb(C.dotB),
  finder: rgb(C.finder),
  core: rgb(C.finderCore),
  white: rgb('#FFFFFF'),
  chip: rgb(C.stepChip),
  cta: rgb(C.cta),
  ctaPress: rgb(C.ctaPress),
};

function col(a: RGB, alpha = 1) {
  'worklet';
  return Float32Array.of(a[0], a[1], a[2], alpha);
}
function colMix(a: RGB, b: RGB, p: number, alpha = 1) {
  'worklet';
  return Float32Array.of(a[0] + (b[0] - a[0]) * p, a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p, alpha);
}

function fill(hex: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(hex));
  return p;
}
function stroke(hex: string, width: number) {
  const p = fill(hex);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

// ------------------------------------------------------------------ resources

const STAR = 'M0 -10 Q1.5 -1.5 10 0 Q1.5 1.5 0 10 Q-1.5 1.5 -10 0 Q-1.5 -1.5 0 -10 Z';
// a heart 20 wide, its middle at the origin
const HEART =
  'M0 7.4 C-1.1 6.4 -10 0.6 -10 -3.6 C-10 -7.2 -6.9 -8.8 -4.9 -8.8 C-2.8 -8.8 -1.2 -7.6 0 -5.6 C1.2 -7.6 2.8 -8.8 4.9 -8.8 C6.9 -8.8 10 -7.2 10 -3.6 C10 0.6 1.1 6.4 0 7.4 Z';
const TICK = 'M-4.6 0.4 L-1.4 3.6 L4.8 -3.4';

export type Res = {
  labels: Labels;
  page: SkPicture;
  hero: SkPicture;
  gift: SkImage;
  sticker: SkImage;
  stickerShadow: SkImage;
  bear: SkImage;
  cushion: SkImage;
  star: SkPath;
  heart: SkPath;
  tick: SkPath;
  bubbleText: SkPath;
  bubbleTextW: number;
  postShader: SkShader;
  petals: SkImage; // white module shapes: PETAL.round × PETAL.stretch sprites
  petalSrc: ReturnType<typeof Skia.XYWHRect>[];
  dotShader: SkShader; // magenta (top left) to violet (bottom right), for the dots at rest
  squares: SkPath; // the plain code's modules, all at once
  dots: SkPath; // the postcard's dots, all at once
  blurShadow: SkMaskFilter;
  blurSoft: SkMaskFilter;
  blurStar: SkMaskFilter;
};

/** Rasterise once, at 3×, into an image the size of `w × h` pt. */
function bake(w: number, h: number, draw: (c: SkCanvas) => void): SkImage | null {
  const k = 3;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(w * k), Math.ceil(h * k)) ?? Skia.Surface.Make(Math.ceil(w * k), Math.ceil(h * k));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(k, k);
  draw(c);
  surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}

function drawPage(l: Labels, tulips: SkImage): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, REF_W, REF_H));
  c.drawColor(Skia.Color(C.page));
  const white = fill(C.card);
  const ink = fill(C.ink);
  const violet = fill(C.violet);
  // the header bar, its bottom corners rounded
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, -40, REF_W, PAGE.header.y1 + 40), PAGE.header.r, PAGE.header.r), white);
  const back = Skia.Path.Make();
  back.moveTo(PAGE.back.x + 4.5, PAGE.back.y - 9);
  back.lineTo(PAGE.back.x - 4.5, PAGE.back.y);
  back.lineTo(PAGE.back.x + 4.5, PAGE.back.y + 9);
  c.drawPath(back, stroke('#8F2BE0', 2.4));
  drawLabel(c, l.orders, REF_W / 2, PAGE.ordersY, 0.5, ink);
  // "Purchases"
  const row = PAGE.row;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(row.x, row.y, row.w, row.h), row.r, row.r), white);
  const bag = Skia.Path.Make();
  const bx = PAGE.bag.x;
  const by = PAGE.bag.y;
  bag.addRRect(Skia.RRectXY(Skia.XYWHRect(bx - 9.5, by - 3.5, 19, 15), 3.2, 3.2));
  bag.moveTo(bx - 4.6, by - 3.5);
  bag.cubicTo(bx - 4.6, by - 12, bx + 4.6, by - 12, bx + 4.6, by - 3.5);
  c.drawPath(bag, stroke(C.ink, 2));
  drawLabel(c, l.purchases, PAGE.purchasesX, PAGE.bag.y + 1, 0, ink);
  const chev = Skia.Path.Make();
  chev.moveTo(PAGE.chevron.x - 3, PAGE.chevron.y - 6.5);
  chev.lineTo(PAGE.chevron.x + 3, PAGE.chevron.y);
  chev.lineTo(PAGE.chevron.x - 3, PAGE.chevron.y + 6.5);
  c.drawPath(chev, stroke(C.ink, 2.2));
  // the promo strip with its tulips, half behind the order card
  const pr = PAGE.promo;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(pr.x, pr.y, pr.w, pr.h), pr.r, pr.r), fill(C.promo));
  const tb = PAGE.tulips;
  const ip = Skia.Paint();
  ip.setAntiAlias(true);
  c.drawImageRectOptions(tulips, Skia.XYWHRect(0, 0, tulips.width(), tulips.height()), Skia.XYWHRect(tb.x, tb.y, tb.w, tb.h), FilterMode.Linear, MipmapMode.Linear, ip);
  drawLabel(c, l.promo, PAGE.promoText.x, PAGE.promoText.y, 0, violet);
  const pc = Skia.Path.Make();
  pc.moveTo(PAGE.promoChevron.x - 2.4, PAGE.promoChevron.y - 5);
  pc.lineTo(PAGE.promoChevron.x + 2.4, PAGE.promoChevron.y);
  pc.lineTo(PAGE.promoChevron.x - 2.4, PAGE.promoChevron.y + 5);
  c.drawPath(pc, stroke(C.violet, 2));
  // the order card
  const od = PAGE.order;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(od.x, od.y, od.w, REF_H), od.r, od.r), white);
  drawLabel(c, l.pick1, 16.4, PAGE.pick1Y, 0, ink);
  drawLabel(c, l.pick2, 16.4, PAGE.pick2Y, 0, ink);
  const qb = PAGE.qrButton;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(qb.x, qb.y, qb.w, qb.h), qb.r, qb.r), fill(C.chip));
  // the little QR glyph: three rounded rings and a square of dots
  const gx = qb.x + qb.w / 2;
  const gy = qb.y + qb.h / 2;
  const ring = stroke(C.violet, 2.3);
  for (const [dx, dy] of [
    [-1, -1],
    [1, -1],
    [-1, 1],
  ]) {
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(gx + dx * 5.6 - 4.2, gy + dy * 5.6 - 4.2, 8.4, 8.4), 2.6, 2.6), ring);
  }
  for (const [dx, dy] of [
    [2.6, 2.6],
    [8.4, 2.6],
    [2.6, 8.4],
    [8.4, 8.4],
  ]) {
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(gx + dx - 1.6, gy + dy - 1.6, 3.2, 3.2), 0.9, 0.9), violet);
  }
  drawLabel(c, l.point, 16.4, PAGE.pointY, 0, fill(C.grey));
  return rec.finishRecordingAsPicture();
}

function drawHero(): SkPicture {
  const rec = Skia.PictureRecorder();
  const top = SHEET.top;
  const bottom = SHEET.heroBottom;
  const c = rec.beginRecording(Skia.XYWHRect(0, top, REF_W, bottom - top + 40));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: REF_W * 0.3, y: bottom }, [Skia.Color(C.heroTop), Skia.Color(C.heroMid)], null, TileMode.Clamp),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, top, REF_W, bottom - top + 40), SHEET.r, SHEET.r), p);
  // soft pools of lighter pink where the pin has them (left middle, bottom right)
  const pools: [number, number, number, number][] = [
    [12, 455, 95, 0.9],
    [392, 528, 120, 0.9],
    [210, 525, 90, 0.45],
  ];
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, top, REF_W, bottom - top + 40), SHEET.r, SHEET.r), ClipOp.Intersect, true);
  for (const [x, y, r, a] of pools) {
    const q = Skia.Paint();
    q.setShader(
      Skia.Shader.MakeRadialGradient({ x, y }, r, [Skia.Color(`rgba(255, 228, 252, ${a})`), Skia.Color('rgba(255, 228, 252, 0)')], null, TileMode.Clamp),
    );
    c.drawCircle(x, y, r, q);
  }
  // a faint diagonal shaft of light from the top left, as in the pin
  const shaft = Skia.Paint();
  shaft.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 20, y: top },
      { x: 120, y: top + 60 },
      [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.32)'), Skia.Color('rgba(255,255,255,0)')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  shaft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  const sp = Skia.Path.Make();
  sp.moveTo(40, top - 10);
  sp.lineTo(95, top - 10);
  sp.lineTo(180, bottom);
  sp.lineTo(110, bottom);
  sp.close();
  c.drawPath(sp, shaft);
  c.restore();
  return rec.finishRecordingAsPicture();
}

export type Images = { bear: SkImage; cushion: SkImage; gift: SkImage; tulips: SkImage };

/** The module sprites: a square that rounds into a dot (columns) and stretches into a petal (rows). */
const PETAL = { cell: 48, shape: 34, round: [0, 0.34, 0.67, 1], stretch: [1, 1.45, 1.95, 2.5] };

function makePetals() {
  const n = PETAL.round.length;
  const rows = PETAL.stretch.length;
  const k = PETAL.cell;
  const surface = Skia.Surface.MakeOffscreen(n * k, rows * k) ?? Skia.Surface.Make(n * k, rows * k);
  if (!surface) return null;
  const c = surface.getCanvas();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color('#FFFFFF'));
  const srcs: ReturnType<typeof Skia.XYWHRect>[] = [];
  for (let row = 0; row < rows; row++) {
    for (let col = 0; col < n; col++) {
      const st = PETAL.stretch[row];
      const w = Math.min(k - 4, PETAL.shape * Math.sqrt(st));
      const h = PETAL.shape / Math.sqrt(st);
      const rad = (Math.min(w, h) / 2) * PETAL.round[col];
      const cx = col * k + k / 2;
      const cy = row * k + k / 2;
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h), rad, rad), p);
      srcs.push(Skia.XYWHRect(col * k, row * k, k, k));
    }
  }
  surface.flush();
  const image = surface.makeImageSnapshot().makeNonTextureImage();
  return image ? { image, srcs } : null;
}

export function makeRes(f: Faces, im: Images): Res | null {
  const labels = makeLabels(f);
  // the gift is far behind: blurred once
  const gift = bake(GIFT.w + 40, GIFT.h + 40, (c) => {
    const p = Skia.Paint();
    p.setImageFilter(Skia.ImageFilter.MakeBlur(GIFT.blur, GIFT.blur, TileMode.Decal, null));
    c.saveLayer(p);
    c.drawImageRectOptions(im.gift, Skia.XYWHRect(0, 0, im.gift.width(), im.gift.height()), Skia.XYWHRect(20, 20, GIFT.w, GIFT.h), FilterMode.Linear, MipmapMode.Linear, null);
    c.restore();
  });
  // the tulip sticker: a white die-cut border round the bouquet, and its shadow
  const b = STICKER.border;
  const sw = STICKER.w + b * 4;
  const sh = STICKER.h + b * 4;
  const silhouette = (color: string, blur: number) => (c: SkCanvas) => {
    const p = Skia.Paint();
    const dilate = Skia.ImageFilter.MakeDilate(b, b, null);
    const tint = Skia.ImageFilter.MakeColorFilter(Skia.ColorFilter.MakeBlend(Skia.Color(color), BlendMode.SrcIn), dilate);
    p.setImageFilter(blur > 0 ? Skia.ImageFilter.MakeBlur(blur, blur, TileMode.Decal, tint) : tint);
    c.saveLayer(p);
    c.drawImageRectOptions(im.tulips, Skia.XYWHRect(0, 0, im.tulips.width(), im.tulips.height()), Skia.XYWHRect(b * 2, b * 2, STICKER.w, STICKER.h), FilterMode.Linear, MipmapMode.Linear, null);
    c.restore();
  };
  const sticker = bake(sw, sh, (c) => {
    silhouette('#FFFFFF', 0)(c);
    c.drawImageRectOptions(im.tulips, Skia.XYWHRect(0, 0, im.tulips.width(), im.tulips.height()), Skia.XYWHRect(b * 2, b * 2, STICKER.w, STICKER.h), FilterMode.Linear, MipmapMode.Linear, null);
  });
  const stickerShadow = bake(sw, sh, silhouette('#5B0A55', 3));
  const petals = makePetals();
  if (!gift || !sticker || !stickerShadow || !petals) return null;

  const bubbleFont = Skia.Font(f.hand, SIZE.bubble);
  const bubbleText = Skia.Path.MakeFromText(COPY.bubble, 0, 0, bubbleFont) ?? Skia.Path.Make();
  const bubbleTextW = bubbleFont.getGlyphWidths(bubbleFont.getGlyphIDs(COPY.bubble)).reduce((a, w) => a + w, 0);

  const squares = Skia.Path.Make();
  const dots = Skia.Path.Make();
  for (const md of MODULES) {
    squares.addRect(Skia.XYWHRect(QR.x + md.c * QR.m - 0.02, QR.y + md.r * QR.m - 0.02, QR.m + 0.04, QR.m + 0.04));
    if (!md.heart) dots.addCircle(QR.x + (md.c + 0.5) * QR.m, QR.y + (md.r + 0.5) * QR.m, QR.m * 0.42);
  }

  return {
    labels,
    petals: petals.image,
    petalSrc: petals.srcs,
    squares,
    dots,
    dotShader: Skia.Shader.MakeLinearGradient(
      { x: QR.x, y: QR.y },
      { x: QR.x + QR.size, y: QR.y + QR.size },
      [Skia.Color(C.dotA), Skia.Color(C.dotB)],
      null,
      TileMode.Clamp,
    ),
    page: drawPage(labels, im.tulips),
    hero: drawHero(),
    gift,
    sticker,
    stickerShadow,
    bear: im.bear,
    cushion: im.cushion,
    star: Skia.Path.MakeFromSVGString(STAR)!,
    heart: Skia.Path.MakeFromSVGString(HEART)!,
    tick: Skia.Path.MakeFromSVGString(TICK)!,
    bubbleText,
    bubbleTextW,
    postShader: Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: CARD.w * 0.5, y: CARD.h },
      [Skia.Color(C.postTop), Skia.Color(C.postBottom)],
      null,
      TileMode.Clamp,
    ),
    blurShadow: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true),
    blurSoft: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true),
    blurStar: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2, true),
  };
}

// ------------------------------------------------------------------ the scene (every frame)

export type Geo = { w: number; h: number; s: number; dy: number };
/** Touch and ambient extras on top of the timeline. */
export type Live = {
  amb: number; // ambient clock, 0 … LOOP
  tiltX: number;
  tiltY: number;
  lean: number;
  cta: number; // finger on the button 0 … 1
  hop: number; // extra bear hop / squash / wave from a tap on it
  squash: number;
  wave: number;
  jiggle: number; // "Send postcard" shake
};

const TAU = Math.PI * 2;

function drawStar(c: SkCanvas, r: Res, x: number, y: number, half: number, rot: number, a: number) {
  'worklet';
  if (a <= 0.01 || half <= 0.2) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(half / 10, half / 10);
  // a soft glow, then the crisp star
  p.setColor(Float32Array.of(1, 1, 1, a * 0.55));
  p.setMaskFilter(r.blurStar);
  c.drawPath(r.star, p);
  p.setMaskFilter(null);
  p.setColor(Float32Array.of(1, 1, 1, a));
  c.drawPath(r.star, p);
  c.restore();
}

function drawSparkles(c: SkCanvas, r: Res, live: Live, boost: number) {
  'worklet';
  for (let i = 0; i < SPARKLES.length; i++) {
    const s = SPARKLES[i];
    // two or three twinkles per loop each, so the loop closes
    const cycles = i % 2 === 0 ? 2 : 3;
    const w = 0.5 + 0.5 * Math.sin(TAU * (cycles * live.amb / LOOP + s.ph));
    const k = (0.72 + 0.28 * w) * (1 + 0.25 * boost);
    drawStar(c, r, s.x - live.tiltX * 3, s.y - live.tiltY * 2, s.s * k, 6 * Math.sin(TAU * (live.amb / LOOP + s.ph)), 0.75 + 0.25 * w);
  }
}

function drawGift(c: SkCanvas, r: Res, live: Live) {
  'worklet';
  const bob = 2.2 * Math.sin(TAU * (live.amb / LOOP));
  const rot = GIFT.rot + 1.2 * Math.sin(TAU * (live.amb / LOOP) + 1.1);
  c.save();
  c.translate(GIFT.x - live.tiltX * 6, GIFT.y + bob - live.tiltY * 3);
  c.rotate(rot, 0, 0);
  const iw = GIFT.w + 40;
  const ih = GIFT.h + 40;
  c.drawImageRectOptions(r.gift, Skia.XYWHRect(0, 0, r.gift.width(), r.gift.height()), Skia.XYWHRect(-iw / 2, -ih / 2, iw, ih), FilterMode.Linear, MipmapMode.None, null);
  c.restore();
}

/** Card transform: the card's own space, origin at its top-left corner. */
function enterCard(c: SkCanvas, f: Frame, live: Live) {
  'worklet';
  const l = f.lift;
  const ang = mix(CARD.angle, TURN_LIFTED, Math.min(1, Math.max(0, l))) + 0.045 * live.jiggle;
  c.translate(CARD.cx, CARD.cy - LIFT * l);
  c.rotate((ang * 180) / Math.PI, 0, 0);
  if (Math.abs(live.tiltX) > 0.001 || Math.abs(live.tiltY) > 0.001) {
    c.concat(processTransform3d([{ perspective: 620 }, { rotateY: live.tiltX * 0.24 }, { rotateX: -live.tiltY * 0.18 }]));
  }
  const k = 1 + 0.012 * f.landSquash;
  c.scale(1 / k, k);
  c.translate(-CARD.w / 2, -CARD.h / 2);
}

function cardRRect() {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(0, 0, CARD.w, CARD.h), CARD.r, CARD.r);
}

function drawCardFace(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // the shadow it casts on the backdrop, deeper when it is raised
  p.setColor(Float32Array.of(0.55, 0.05, 0.5, 0.16 + 0.08 * Math.min(1, f.lift)));
  p.setMaskFilter(r.blurShadow);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(4, 8 + 8 * f.lift, CARD.w - 8, CARD.h), CARD.r, CARD.r), p);
  p.setMaskFilter(null);
  p.setColor(Float32Array.of(1, 1, 1, 1));
  c.drawRRect(cardRRect(), p);
  if (f.face <= 0.001) return;
  // the postcard spreads from the middle of the code, with a soft edge and a glowing front
  const mx = QR.x + QR.size / 2;
  const my = QR.y + QR.size / 2;
  const R = 12 + f.face * 190;
  const post = Skia.Paint();
  post.setAntiAlias(true);
  post.setShader(r.postShader);
  c.save();
  c.clipRRect(cardRRect(), ClipOp.Intersect, true);
  if (f.face < 0.999) {
    c.saveLayer();
    c.drawRect(Skia.XYWHRect(0, 0, CARD.w, CARD.h), post);
    const mask = Skia.Paint();
    mask.setBlendMode(BlendMode.DstIn);
    mask.setShader(
      Skia.Shader.MakeRadialGradient({ x: mx, y: my }, R, [Float32Array.of(0, 0, 0, 1), Float32Array.of(0, 0, 0, 1), Float32Array.of(0, 0, 0, 0)], [0, Math.max(0, (R - 16) / R), 1], TileMode.Clamp),
    );
    c.drawRect(Skia.XYWHRect(0, 0, CARD.w, CARD.h), mask);
    c.restore();
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(5);
    ring.setColor(Float32Array.of(1, 0.43, 0.85, 0.35 * Math.sin(Math.PI * f.face)));
    ring.setMaskFilter(r.blurSoft);
    c.drawCircle(mx, my, R - 8, ring);
  } else {
    c.drawRect(Skia.XYWHRect(0, 0, CARD.w, CARD.h), post);
  }
  // the postcard's dashed edge
  const dash = Skia.Paint();
  dash.setAntiAlias(true);
  dash.setStyle(PaintStyle.Stroke);
  dash.setStrokeWidth(1.3);
  dash.setColor(Float32Array.of(1, 1, 1, 0.95 * smooth((f.face - 0.6) / 0.4)));
  dash.setPathEffect(Skia.PathEffect.MakeDash([5, 4], 0));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(7, 7, CARD.w - 14, CARD.h - 14), CARD.r - 6, CARD.r - 6), dash);
  c.restore();
}

function drawFinders(c: SkCanvas, f: Frame) {
  'worklet';
  const m = QR.m;
  const k = f.finders;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  for (let i = 0; i < FINDERS.length; i++) {
    const fd = FINDERS[i];
    const x = QR.x + fd.c * m;
    const y = QR.y + fd.r * m;
    c.save();
    // each corner square gives a small playful turn as it rounds off
    c.rotate(10 * Math.sin(Math.PI * k) * (i === 1 ? -1 : 1), x + 3.5 * m, y + 3.5 * m);
    const ring = Skia.Path.Make();
    ring.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, 7 * m, 7 * m), 2.2 * m * k, 2.2 * m * k));
    ring.addRRect(Skia.RRectXY(Skia.XYWHRect(x + m, y + m, 5 * m, 5 * m), 1.5 * m * k, 1.5 * m * k));
    ring.setFillType(FillType.EvenOdd); // the hole shows the card
    p.setColor(colMix(K.ink, K.finder, k));
    c.drawPath(ring, p);
    p.setColor(colMix(K.ink, K.core, k));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 2 * m, y + 2 * m, 3 * m, 3 * m), 1.5 * m * k, 1.5 * m * k), p);
    c.restore();
  }
}

/** The modules at rest (flying false: squares on the plain card, dots on the postcard) or in flight. */
function drawModules(c: SkCanvas, r: Res, t: number, flying: boolean) {
  'worklet';
  const m = QR.m;
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(col(K.ink));
  const dotPaint = Skia.Paint();
  dotPaint.setAntiAlias(true);
  dotPaint.setShader(r.dotShader);
  // most of the loop nothing is moving: the whole code is one path
  const calm = t < T.swirlAt || (t >= SWIRL_DONE && t < T.backSwirlAt) || t >= BACK_DONE;
  if (calm) {
    if (flying) return;
    if (t < T.swirlAt || t >= BACK_DONE) c.drawPath(r.squares, ink);
    else c.drawPath(r.dots, dotPaint);
    return;
  }
  // while modules are moving, every one of them (squares, petals, dots) is one sprite of one atlas
  // draw: a white shape (roundness × stretch) tinted per instance, so 300+ modules cost one call
  const srcs: ReturnType<typeof Skia.XYWHRect>[] = [];
  const xf: ReturnType<typeof Skia.RSXform>[] = [];
  const cols: Float32Array[] = [];
  const half = PETAL.cell / 2;
  const put = (sprite: number, size: number, rad: number, x: number, y: number, color: Float32Array) => {
    'worklet';
    srcs.push(r.petalSrc[sprite]);
    xf.push(Skia.RSXformFromRadians(size / PETAL.shape, rad, x, y, half, half));
    cols.push(color);
  };
  const last = PETAL.round.length - 1;
  for (let i = 0; i < MODULES.length; i++) {
    const md = MODULES[i];
    const u = moduleU(md, t);
    const settled = u <= 0.0005 || u >= 0.9995;
    if (settled === flying) continue;
    const g = (md.r + md.c) / (2 * QR_N);
    const dot = [mix(K.dotA[0], K.dotB[0], g), mix(K.dotA[1], K.dotB[1], g), mix(K.dotA[2], K.dotB[2], g)] as RGB;
    if (u <= 0.0005) {
      put(0, m + 0.08, 0, QR.x + (md.c + 0.5) * m, QR.y + (md.r + 0.5) * m, col(K.ink));
      continue;
    }
    if (u >= 0.9995) {
      if (!md.heart) put(last, m * 0.84, 0, QR.x + (md.c + 0.5) * m, QR.y + (md.r + 0.5) * m, col(dot));
      continue;
    }
    const pose = modulePose(md, u);
    const round = smooth((u - 0.12) / 0.5);
    const size = m * mix(1, 0.84, smooth(u)) * (1 + 0.55 * pose.z);
    const alpha = md.heart ? 1 - smooth((u - 0.55) / 0.4) : 1;
    // a faint trail behind the fast ones (behind = where it came from)
    if (pose.z > 0.3) {
      const back = modulePose(md, Math.max(0, Math.min(1, t < T.back ? u - 0.07 : u + 0.07)));
      put(last, size * 0.84, 0, back.x, back.y, colMix(K.ink, K.petal, Math.min(1, u * 2), 0.22 * pose.z * alpha));
    }
    // a petal: stretched along its path while it flies fast, round when it lands
    const ri = Math.round(round * last);
    const si = Math.round(pose.z * (PETAL.stretch.length - 1));
    const colr = u < 0.5 ? colMix(K.ink, K.petal, u * 2, alpha) : colMix(K.petal, dot, (u - 0.5) * 2, alpha);
    put(si * PETAL.round.length + ri, size * (1 + 0.2 * pose.z), pose.spin, pose.x, pose.y, colr);
  }
  if (srcs.length === 0) return;
  const ap = Skia.Paint();
  ap.setAntiAlias(true);
  c.drawAtlas(r.petals, srcs, xf, ap, BlendMode.Modulate, cols, { filter: FilterMode.Linear, mipmap: MipmapMode.Linear });
}

function drawBear(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const sx0 = (live.wave !== 0 ? BEAR.rest + live.wave : f.wave) + 0.5;
  const frame = Math.max(0, Math.min(BEAR.frames - 1, Math.floor(sx0)));
  const col_ = frame % BEAR.cols;
  const row = Math.floor(frame / BEAR.cols);
  const breathe = 0.012 * Math.sin(TAU * 3 * (live.amb / LOOP));
  const squash = f.squash + live.squash;
  const hop = f.hop + live.hop;
  const ip = Skia.Paint();
  ip.setAntiAlias(true);
  const dst = Skia.XYWHRect(-BEAR.w / 2, -BEAR.h, BEAR.w, BEAR.h);
  // the cushion stays put and gives a little when the bear lands on it
  c.save();
  c.translate(BEAR.x, BEAR.y);
  c.scale(1 + Math.max(0, squash) * 0.4, 1 - Math.max(0, squash) * 0.3);
  c.drawImageRectOptions(r.cushion, Skia.XYWHRect(0, 0, r.cushion.width(), r.cushion.height()), dst, FilterMode.Linear, MipmapMode.None, ip);
  c.restore();
  // its shadow on the cushion shrinks as it hops
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.12, 0.0, 0.3, 0.28 * (1 - Math.min(1, hop / 18))));
  sh.setMaskFilter(r.blurSoft);
  c.drawOval(Skia.XYWHRect(BEAR.x - 34 + hop * 0.6, BEAR.y - 26, 68 - hop * 1.2, 12), sh);
  c.save();
  c.translate(BEAR.x, BEAR.y - 16 - hop);
  c.rotate(-live.lean * 7, 0, 0);
  c.scale(1 + squash, (1 - squash) * (1 + breathe));
  c.translate(0, 16);
  c.drawImageRectOptions(
    r.bear,
    Skia.XYWHRect(col_ * BEAR.fw, row * BEAR.fh, BEAR.fw, BEAR.fh),
    dst,
    FilterMode.Linear,
    MipmapMode.None,
    ip,
  );
  c.restore();
}

function drawHeart(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  if (f.heart <= 0.01) return;
  const m = QR.m;
  const mx = QR.x + QR.size / 2;
  const my = QR.y + QR.size / 2;
  const k = Math.max(0, f.heart);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.save();
  c.translate(mx, my);
  c.scale(0.55 + 0.45 * k, 0.55 + 0.45 * k);
  const a = Math.min(1, k * 2.5);
  p.setColor(Float32Array.of(0.6, 0.05, 0.45, 0.18 * a));
  p.setMaskFilter(r.blurSoft);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-3.3 * m, -3.1 * m, 6.6 * m, 6.6 * m), 1.8 * m, 1.8 * m), p);
  p.setMaskFilter(null);
  p.setColor(Float32Array.of(1, 1, 1, a));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-3.3 * m, -3.3 * m, 6.6 * m, 6.6 * m), 1.8 * m, 1.8 * m), p);
  p.setColor(Float32Array.of(0.94, 0.13, 0.62, a));
  c.scale(0.95, 0.95);
  c.translate(0, 0.8);
  c.drawPath(r.heart, p);
  c.restore();
}

function drawSticker(c: SkCanvas, r: Res, f: Frame, t: number, live: Live) {
  'worklet';
  if (t < 3.15 || f.stickerPeel >= 0.999) return;
  const e = smooth(f.sticker);
  const drop = 1 - e * e; // it falls faster as it lands
  const sq = f.stickerSquash;
  const peel = f.stickerPeel;
  const scale = (1 + 0.38 * drop + 0.12 * peel) * 1;
  const rot = STICKER.rot + 22 * drop + 26 * peel;
  const x = STICKER.x + 18 * drop + 70 * peel + live.tiltX * 3;
  const y = STICKER.y - 46 * drop - 95 * peel + live.tiltY * 2;
  const alpha = Math.min(1, f.sticker * 5) * (1 - peel);
  const sw = STICKER.w + STICKER.border * 4;
  const sh = STICKER.h + STICKER.border * 4;
  const ip = Skia.Paint();
  ip.setAntiAlias(true);
  // shadow: far and soft while it falls, tight once stuck
  ip.setAlphaf(alpha * (0.32 - 0.16 * drop));
  c.save();
  c.translate(x + 2 + 10 * drop, y + 3 + 14 * drop);
  c.rotate(rot, 0, 0);
  c.scale(scale, scale);
  c.drawImageRectOptions(r.stickerShadow, Skia.XYWHRect(0, 0, r.stickerShadow.width(), r.stickerShadow.height()), Skia.XYWHRect(-sw / 2, -sh / 2, sw, sh), FilterMode.Linear, MipmapMode.None, ip);
  c.restore();
  ip.setAlphaf(alpha);
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(scale * (1 + 0.07 * sq), scale * (1 - 0.07 * sq));
  c.drawImageRectOptions(r.sticker, Skia.XYWHRect(0, 0, r.sticker.width(), r.sticker.height()), Skia.XYWHRect(-sw / 2, -sh / 2, sw, sh), FilterMode.Linear, MipmapMode.None, ip);
  c.restore();
}

function drawSheen(c: SkCanvas, f: Frame) {
  'worklet';
  if (f.sheen < 0) return;
  const x = mix(-90, CARD.w + 90, f.sheen);
  const p = Skia.Paint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: x - 40, y: 0 },
      { x: x + 40, y: 30 },
      [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.42), Float32Array.of(1, 1, 1, 0)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  p.setBlendMode(BlendMode.Plus);
  c.save();
  c.clipRRect(cardRRect(), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, 0, CARD.w, CARD.h), p);
  c.restore();
}

function drawBurst(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  if (f.burst < 0) return;
  const mx = QR.x + QR.size / 2;
  const my = QR.y + QR.size / 2;
  const n = 9;
  for (let i = 0; i < n; i++) {
    const a = (TAU * i) / n + 0.35;
    const h = 0.5 + 0.5 * Math.sin(i * 7.31);
    const d = (52 + 46 * h) * (1 - Math.pow(1 - f.burst, 3));
    const life = Math.sin(Math.PI * Math.min(1, f.burst * 1.15));
    drawStar(c, r, mx + Math.cos(a) * d, my + Math.sin(a) * d * 0.9, (4 + 5 * h) * life, 40 * f.burst + i * 20, life);
  }
}

function drawBubble(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  if (f.bubble <= 0.01) return;
  const k = Math.max(0, f.bubble);
  const tx = BUBBLE.tail.x;
  const ty = BUBBLE.tail.y;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.save();
  // it grows out of its tail, which points at the bear
  c.translate(tx, ty);
  c.scale(0.5 + 0.5 * k, 0.5 + 0.5 * k);
  c.translate(-tx, -ty);
  const a = Math.min(1, k * 2);
  const bx = BUBBLE.x;
  const by = BUBBLE.y;
  const path = Skia.Path.Make();
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(bx - BUBBLE.w / 2, by - BUBBLE.h / 2, BUBBLE.w, BUBBLE.h), BUBBLE.r, BUBBLE.r));
  const tail = Skia.Path.Make();
  tail.moveTo(bx - 22, by + 10);
  tail.quadTo(bx - 26, by + 22, tx, ty);
  tail.quadTo(bx - 10, by + 20, bx - 8, by + 14);
  tail.close();
  path.addPath(tail);
  p.setColor(Float32Array.of(0.62, 0.0, 0.5, 0.16 * a));
  p.setMaskFilter(r.blurSoft);
  c.save();
  c.translate(0, 3);
  c.drawPath(path, p);
  c.restore();
  p.setMaskFilter(null);
  p.setColor(Float32Array.of(1, 1, 1, a));
  c.drawPath(path, p);
  // "for you" writes itself left to right; a heart follows the last letter
  const heartW = 15;
  const total = r.bubbleTextW + 4 + heartW;
  const x0 = bx - total / 2;
  const yb = by + SIZE.bubble * 0.3;
  if (f.write > 0) {
    c.saveLayer();
    c.save();
    c.translate(x0, yb);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Float32Array.of(0.48, 0.12, 0.66, 1));
    c.drawPath(r.bubbleText, ink);
    c.restore();
    const pen = Skia.Paint();
    pen.setAntiAlias(true);
    pen.setBlendMode(BlendMode.DstIn);
    const reveal = Math.min(1, f.write / 0.82) * (r.bubbleTextW + 10);
    pen.setShader(
      Skia.Shader.MakeLinearGradient({ x: x0 + reveal - 8, y: 0 }, { x: x0 + reveal, y: 0 }, [Float32Array.of(0, 0, 0, 1), Float32Array.of(0, 0, 0, 0)], null, TileMode.Clamp),
    );
    c.drawRect(Skia.XYWHRect(x0 - 4, by - BUBBLE.h / 2, total + 8, BUBBLE.h), pen);
    c.restore();
  }
  const hp = smooth((f.write - 0.8) / 0.2);
  if (hp > 0) {
    c.save();
    c.translate(x0 + r.bubbleTextW + 4 + heartW / 2, by - 1);
    const hs = (0.62 * (0.6 + 0.4 * hp)) * (1 + 0.15 * Math.sin(Math.PI * hp));
    c.scale(hs, hs);
    const hpnt = Skia.Paint();
    hpnt.setAntiAlias(true);
    hpnt.setColor(Float32Array.of(0.94, 0.13, 0.62, a * hp));
    c.drawPath(r.heart, hpnt);
    c.restore();
  }
  c.restore();
}

function drawClose(c: SkCanvas) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(1, 1, 1, 1));
  const cl = SHEET.close;
  c.drawCircle(cl.x, cl.y, cl.r, p);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1.9);
  p.setStrokeCap(StrokeCap.Round);
  p.setColor(Float32Array.of(1, 0.68, 0.95, 1));
  const d = 4.4;
  c.drawLine(cl.x - d, cl.y - d, cl.x + d, cl.y + d, p);
  c.drawLine(cl.x + d, cl.y - d, cl.x - d, cl.y + d, p);
}

function drawText(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const L = r.labels;
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Float32Array.of(0.145, 0.145, 0.153, 1));
  drawLabel(c, L.title, SHEET.titleX, SHEET.titleY, 0, ink);
  drawLabel(c, L.sub, SHEET.titleX, SHEET.subY, 0, ink);
  const chip = Skia.Paint();
  chip.setAntiAlias(true);
  for (let s = 0; s < 2; s++) {
    for (let i = 0; i < 3; i++) drawLabel(c, L.steps[s][i], SHEET.stepTextX, SHEET.stepLineY[s][i], 0, ink);
    const cy = SHEET.stepChipY[s];
    const k = s === 0 ? f.check : 0;
    chip.setColor(colMix(K.chip, K.cta, k));
    // the ticked chip gives a little pop
    const pop = 1 + 0.12 * Math.sin(Math.PI * k);
    c.drawCircle(SHEET.stepChip.x, cy, SHEET.stepChip.r * pop, chip);
    if (k < 0.999) {
      const n = Skia.Paint();
      n.setAntiAlias(true);
      n.setColor(Float32Array.of(0.145, 0.145, 0.153, 1 - k));
      c.save();
      c.translate(SHEET.stepChip.x, cy);
      c.scale(1 - 0.4 * k, 1 - 0.4 * k);
      drawLabel(c, L.stepNo[s], 0, 0, 0.5, n);
      c.restore();
    }
    if (k > 0.001) {
      const tick = r.tick.copy();
      tick.trim(0, Math.min(1, k * 1.15), false);
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setStyle(PaintStyle.Stroke);
      tp.setStrokeWidth(2.3);
      tp.setStrokeCap(StrokeCap.Round);
      tp.setStrokeJoin(StrokeJoin.Round);
      tp.setColor(Float32Array.of(1, 1, 1, 1));
      c.save();
      c.translate(SHEET.stepChip.x, cy);
      c.drawPath(tick, tp);
      c.restore();
    }
  }
  // the button: presses in, and its label morphs from "Decorate QR code" to "Send postcard"
  const b = SHEET.cta;
  const press = Math.max(f.press, live.cta);
  const bs = 1 - 0.04 * press;
  c.save();
  c.translate(b.x + b.w / 2, b.y + b.h / 2);
  c.scale(bs, bs);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(colMix(K.cta, K.ctaPress, press));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), b.r, b.r), bp);
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  const k = f.cta;
  if (k < 0.999) {
    lp.setColor(Float32Array.of(1, 1, 1, 1 - k));
    if (k > 0.001) lp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2 * Math.sin(Math.PI * k), true));
    drawLabel(c, L.ctaA, 0, -9 * k, 0.5, lp);
  }
  if (k > 0.001) {
    lp.setColor(Float32Array.of(1, 1, 1, k));
    lp.setMaskFilter(k < 0.999 ? Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2 * Math.sin(Math.PI * k), true) : null);
    drawLabel(c, L.ctaB, 0, 9 * (1 - k), 0.5, lp);
  }
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  // the page, hanging from the top, under the dim
  c.drawColor(Float32Array.of(0.965, 0.965, 0.98, 1));
  c.save();
  c.scale(g.s, g.s);
  c.drawPicture(r.page);
  c.restore();
  paint.setColor(Float32Array.of(0, 0, 0, 0.5));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), paint);
  c.save();
  c.scale(g.s, g.s);
  const gb = PAGE.grabber;
  paint.setColor(Float32Array.of(1, 1, 1, 0.62));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(gb.x, gb.y, gb.w, gb.h), gb.h / 2, gb.h / 2), paint);
  c.restore();

  // the sheet, standing on the bottom edge
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  c.drawPicture(r.hero);
  c.save();
  c.clipRect(Skia.XYWHRect(0, SHEET.top, REF_W, SHEET.heroBottom - SHEET.top), ClipOp.Intersect, true);
  drawGift(c, r, live);
  drawSparkles(c, r, live, f.burst >= 0 ? Math.sin(Math.PI * f.burst) : 0);
  c.restore();

  // the card and everything on it (not clipped: the bear may hop above the sheet's edge)
  c.save();
  enterCard(c, f, live);
  drawCardFace(c, r, f);
  drawFinders(c, f);
  drawModules(c, r, f.t, false);
  drawHeart(c, r, f);
  drawSheen(c, f);
  drawBear(c, r, f, live);
  drawModules(c, r, f.t, true); // the ones in flight swirl over the bear
  drawSticker(c, r, f, f.t, live);
  drawBurst(c, r, f);
  c.restore();

  // the white half of the sheet hides the card's lower part
  paint.setColor(Float32Array.of(1, 1, 1, 1));
  c.drawRect(Skia.XYWHRect(0, SHEET.heroBottom, REF_W, REF_H - SHEET.heroBottom + 200), paint);
  drawBubble(c, r, f);
  drawClose(c);
  drawText(c, r, f, live);
  c.restore();
}
