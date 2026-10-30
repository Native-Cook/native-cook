// Gold Ticket Reward — drawing. The page under the sheet is recorded once (drawPage); everything
// else is drawn every frame from a Frame (drawScene), in the 402 × 874 reference space: the page
// hangs from the top of the screen and the sheet stands on its bottom edge.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
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
  type SkRuntimeEffect,
  type SkShader,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  processTransform3d,
} from '@shopify/react-native-skia';

import { COIN_FRAMES, type Frame, LOOP, mix, T } from './gtr-motion';
import { A, B, BENEFIT_B_SCALE, C, COPY, PAGE, REF_H, REF_W, WIDTH } from './gtr-theme';

// ------------------------------------------------------------------ labels

/** A line of text laid out once: glyphs and their x positions (tracking included). */
export type Label = { font: SkFont; glyphs: number[]; pos: SkPoint[]; width: number; size: number };

function glyphWidths(font: SkFont, str: string) {
  const ids = font.getGlyphIDs(str);
  return { ids, widths: font.getGlyphWidths(ids) };
}

/** `str` in `face`, sized so it is `target` points wide with `track` em of letter spacing. */
export function fitLabel(face: SkTypeface, str: string, target: number, track = 0): Label {
  const probe = Skia.Font(face, 100);
  const w100 = glyphWidths(probe, str).widths.reduce((a, b) => a + b, 0);
  const n = str.length;
  const size = target / (w100 / 100 + track * (n - 1));
  return makeLabel(face, str, size, track);
}

export function makeLabel(face: SkTypeface, str: string, size: number, track = 0): Label {
  const font = Skia.Font(face, size);
  const { ids, widths } = glyphWidths(font, str);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + (i < ids.length - 1 ? track * size : 0);
  }
  return { font, glyphs: ids, pos, width: x, size };
}

/** Draws `l` with its visual centre (half the cap height) on `cy`; `ax` 0 left, 0.5 centre, 1 right. */
export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.35, l.font, paint);
}

// ------------------------------------------------------------------ resources

export type Labels = {
  title: Label;
  help: Label;
  status: Label;
  expert: Label;
  verified: Label;
  progress: Label;
  section: Label;
  pros: Label;
  prosSub: Label;
  water: Label;
  thanks: Label;
  callback: Label;
  winA: Label;
  prizeA: Label;
  winB: Label;
  prizeB: Label;
  benefits: Label[];
  cta: Label;
};

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };

export function makeLabels(f: Faces): Labels {
  const expertSize = fitLabel(f.semi, COPY.expertRef, WIDTH.expert).size;
  const benefitSize = fitLabel(f.semi, COPY.benefits[0], WIDTH.benefitA).size;
  return {
    title: fitLabel(f.semi, COPY.title, WIDTH.title),
    help: fitLabel(f.medium, COPY.help, WIDTH.help),
    status: fitLabel(f.semi, COPY.status, WIDTH.status),
    expert: makeLabel(f.semi, COPY.expert, expertSize),
    verified: fitLabel(f.regular, COPY.verified, WIDTH.verified),
    progress: fitLabel(f.medium, COPY.progress, WIDTH.progress),
    section: fitLabel(f.semi, COPY.section, WIDTH.section, 0.06),
    pros: fitLabel(f.semi, COPY.pros, WIDTH.pros),
    prosSub: fitLabel(f.regular, COPY.prosSub, WIDTH.prosSub),
    water: fitLabel(f.medium, COPY.water, WIDTH.water),
    thanks: fitLabel(f.bold, COPY.thanks, WIDTH.thanks, 0.11),
    callback: fitLabel(f.regular, COPY.callback, WIDTH.callback),
    winA: fitLabel(f.semi, COPY.win, WIDTH.winA),
    prizeA: fitLabel(f.bold, COPY.prize, WIDTH.prizeA),
    winB: fitLabel(f.medium, COPY.win, WIDTH.winB),
    prizeB: fitLabel(f.bold, COPY.prize, WIDTH.prizeB),
    benefits: COPY.benefits.map((b) => makeLabel(f.semi, b, benefitSize)),
    cta: fitLabel(f.medium, COPY.cta, WIDTH.ctaA),
  };
}

export type Res = {
  page: SkImage;
  labels: Labels;
  cars: SkImage;
  carsShader: SkShader;
  coin: SkImage;
  foil: SkRuntimeEffect;
  dissolve: SkRuntimeEffect;
  stage: SkPath;
  stageShader: SkShader;
  glowShader: SkShader;
  glowSpot: SkShader;
  purple: SkShader;
  purpleCore: SkShader;
  prizeShader: SkShader;
  star: SkPath;
  blurA: SkMaskFilter;
  blurB: SkMaskFilter;
  blurCoin: SkMaskFilter;
  blurStar: SkMaskFilter;
  dust: Dust[];
};

const PAGE_BLUR = 1.6; // pt
const STAR = 'M0 -10 Q1.6 -1.6 10 0 Q1.6 1.6 0 10 Q-1.6 1.6 -10 0 Q-1.6 -1.6 0 -10 Z';
export const CARS_BOX = (() => {
  // the car row keeps its aspect: as wide as the pin's, standing on the stage
  const w = A.cars.w;
  const h = (w * 280) / 900;
  return { x: A.cars.x, y: A.cars.y + A.cars.h - h, w, h };
})();

function col(hex: string, a = 1) {
  const c = Skia.Color(hex);
  c[3] = a;
  return c;
}

export function makeStatics(cars: SkImage) {
  const stage = Skia.PathBuilder.Make()
    .moveTo(0, A.stageEdge)
    .lineTo(A.stageInset, A.stageTop)
    .lineTo(REF_W - A.stageInset, A.stageTop)
    .lineTo(REF_W, A.stageEdge)
    .lineTo(REF_W, REF_H)
    .lineTo(0, REF_H)
    .close()
    .build();
  const stageShader = Skia.Shader.MakeLinearGradient(
    { x: 0, y: A.stageTop },
    { x: 0, y: A.band.y },
    [col(C.stageTop), col(C.stageMid), col(C.sheet)],
    [0, 0.42, 1],
    TileMode.Clamp,
  );
  const glowShader = Skia.Shader.MakeLinearGradient(
    { x: 0, y: A.glowTop },
    { x: 0, y: A.stageEdge },
    [col(C.glow, 0), col(C.glow, 0.12), col(C.glow, 0.29), col(C.glow, 0.48), col(C.glow, 0.6), col(C.glowHi, 0.9), col(C.glowHi, 1)],
    [0, 0.41, 0.57, 0.75, 0.83, 0.93, 1],
    TileMode.Clamp,
  );
  const glowSpot = Skia.Shader.MakeRadialGradient(
    { x: CARS_BOX.x + CARS_BOX.w / 2, y: A.stageTop - 6 },
    CARS_BOX.w * 0.62,
    [col('#f0cf7a', 0.32), col('#f0cf7a', 0)],
    [0, 1],
    TileMode.Clamp,
  );
  const purple = Skia.Shader.MakeLinearGradient(
    { x: 0, y: B.fadeTop },
    { x: 0, y: REF_H },
    [
      Skia.Color(`rgba(${C.purpleFade[0]},${C.purpleFade[1]},${C.purpleFade[2]},0)`),
      Skia.Color(`rgba(${C.purpleFade[0]},${C.purpleFade[1]},${C.purpleFade[2]},0.3)`),
      Skia.Color(`rgba(${C.purpleFade[0]},${C.purpleFade[1]},${C.purpleFade[2]},0.66)`),
      col(C.purpleMid),
      col(C.purpleLow),
      col(C.purpleBottom),
    ],
    [0, 48 / 229, 88 / 229, (B.full - B.fadeTop) / (REF_H - B.fadeTop), 188 / 229, 1],
    TileMode.Clamp,
  );
  const purpleCore = Skia.Shader.MakeRadialGradient(
    { x: REF_W / 2, y: B.benefits[0].y - 4 },
    REF_W * 0.55,
    [col('#6a4fd6', 0.32), col('#6a4fd6', 0)],
    [0, 1],
    TileMode.Clamp,
  );
  const prizeShader = Skia.Shader.MakeLinearGradient(
    { x: -WIDTH.prizeA / 2, y: 0 },
    { x: WIDTH.prizeA / 2, y: 0 },
    C.prizeA.map((h) => col(h)),
    [0, 0.5, 1],
    TileMode.Clamp,
  );
  const m = Skia.Matrix();
  m.translate(CARS_BOX.x, CARS_BOX.y);
  m.scale(CARS_BOX.w / cars.width(), CARS_BOX.h / cars.height());
  const carsShader = cars.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.None, m);
  const star = Skia.Path.MakeFromSVGString(STAR)!;
  return {
    stage,
    stageShader,
    glowShader,
    glowSpot,
    purple,
    purpleCore,
    prizeShader,
    carsShader,
    star,
    blurA: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true),
    blurB: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 13, true),
    blurCoin: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true),
    blurStar: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true),
  };
}

// ------------------------------------------------------------------ gold dust (built once)

/**
 * One mote of the gold the cars burn into: it leaves a point of the car row when the dissolve
 * front reaches it, rises like an ember, then swoops onto the ticket where the pour is passing.
 * Every value is fixed here from a seeded hash, so any frame is a function of the loop time.
 */
export type Dust = {
  x0: number; y0: number; c1x: number; c1y: number; c2x: number; c2y: number; tx: number; ty: number;
  rel: number; arr: number; back0: number; size: number; warm: number;
};

const DUST_COUNT = 150;

function hash(n: number) {
  const s = Math.sin(n * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

export function makeDust(cars: SkImage): Dust[] {
  const w = cars.width();
  const h = cars.height();
  const px = cars.readPixels(0, 0, { width: w, height: h, colorType: 4, alphaType: 3 });
  const spots: { u: number; v: number }[] = [];
  if (px) {
    for (let y = 4; y < h - 4; y += 9) {
      for (let x = 4; x < w - 4; x += 9) {
        if (px[(y * w + x) * 4 + 3] > 200) spots.push({ u: x / w, v: y / h });
      }
    }
  }
  if (spots.length === 0) for (let i = 0; i < 400; i++) spots.push({ u: hash(i + 0.5), v: 0.3 + 0.6 * hash(i + 7.7) });
  const out: Dust[] = [];
  const goldSpan = T.gold[1] - T.gold[0];
  for (let i = 0; i < DUST_COUNT; i++) {
    const s = spots[Math.floor(hash(i + 1.23) * spots.length) % spots.length];
    const x0 = CARS_BOX.x + s.u * CARS_BOX.w;
    const y0 = CARS_BOX.y + s.v * CARS_BOX.h;
    // released when the bottom-up dissolve front gets here (its noise averages 0.19)
    const front = ((1 - s.v) * 0.62 + 0.19 + 0.08) / 1.16;
    const rel = T.carsOut[0] + Math.min(1, Math.max(0, front)) * (T.carsOut[1] - T.carsOut[0]) + hash(i + 3.1) * 0.05;
    const u = hash(i + 4.4);
    const v = hash(i + 5.5);
    const tx = B.ticket.x + 14 + u * (B.ticket.w - 28);
    const ty = B.ticket.y + 10 + v * (B.ticket.h - 20);
    // the pour's edge reaches this point of the ticket at about this time
    const reach = (u + (v - 0.5) * 0.35 + 0.4) / 1.8;
    const arr = Math.max(rel + 0.34, T.gold[0] + reach * goldSpan + 0.02);
    out.push({
      x0, y0, tx, ty, rel, arr,
      c1x: x0 + (hash(i + 6.6) - 0.5) * 60,
      c1y: y0 - 34 - hash(i + 7.7) * 56,
      c2x: tx + (hash(i + 8.8) - 0.5) * 90,
      c2y: ty - 80 - hash(i + 9.9) * 70,
      // on the way home they leave the ticket where the gold drains first (the right end)
      back0: T.goldOut[0] + (1 - u) * 0.3 + hash(i + 2.2) * 0.06,
      size: 1.1 + hash(i + 11.1) * 1.4,
      warm: hash(i + 12.2),
    });
  }
  return out;
}

// ------------------------------------------------------------------ the page (recorded once)

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

export function drawPage(l: Labels): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, REF_W, REF_H));
  c.drawColor(Skia.Color(C.page));
  const ink = fill(C.ink);
  const mute = fill(C.mute);
  const violet = fill(C.violet);

  // nav: back arrow, title, help
  const bx0 = PAGE.back.x - 7;
  const by0 = PAGE.back.y;
  const arrow = Skia.PathBuilder.Make()
    .moveTo(bx0 + 7, by0 - 6)
    .lineTo(bx0, by0)
    .lineTo(bx0 + 7, by0 + 6)
    .moveTo(bx0, by0)
    .lineTo(bx0 + 15, by0)
    .build();
  c.drawPath(arrow, stroke(C.ink, 1.8));
  drawLabel(c, l.title, PAGE.title.x, PAGE.title.y, 0, ink);
  const hs = PAGE.help;
  const set = Skia.PathBuilder.Make()
    .addArc(Skia.XYWHRect(hs.icon - 6.5, hs.y - 7, 13, 13), 180, 180)
    .moveTo(hs.icon - 6.5, hs.y)
    .lineTo(hs.icon - 6.5, hs.y + 3.5)
    .moveTo(hs.icon + 6.5, hs.y)
    .lineTo(hs.icon + 6.5, hs.y + 3.5)
    .build();
  c.drawPath(set, stroke(C.ink, 1.6));
  drawLabel(c, l.help, hs.x, hs.y, 1, ink);

  // status: a violet ring with a tick
  const ck = PAGE.check;
  c.drawCircle(ck.x, ck.y, ck.r, stroke(C.violet, ck.stroke));
  const tick = Skia.PathBuilder.Make().moveTo(ck.x - 8, ck.y + 0.5).lineTo(ck.x - 2.5, ck.y + 6).lineTo(ck.x + 8.5, ck.y - 6).build();
  c.drawPath(tick, stroke(C.violet, ck.stroke));
  drawLabel(c, l.status, PAGE.status.x, PAGE.status.y, 0.5, ink);

  // the expert card
  const card = PAGE.card;
  const r1 = Skia.RRectXY(Skia.XYWHRect(card.x, card.y, card.w, card.h), 12, 12);
  c.drawRRect(r1, fill(C.card));
  c.drawRRect(r1, stroke(C.cardLine, 1));
  const av = PAGE.avatar;
  c.drawCircle(av.x, av.y, av.r + 3, fill('#ececf3'));
  c.drawCircle(av.x, av.y - 2.6, 3.4, fill('#9a99ab'));
  const sh = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(av.x - 6, av.y + 1.5, 12, 10), 180, 180).build();
  c.drawPath(sh, fill('#9a99ab'));
  drawLabel(c, l.expert, PAGE.expert.x, PAGE.expert.y, 0, ink);
  const bd = PAGE.badge;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bd.x, bd.y, bd.w, bd.h), 3, 3), fill('#f2c94c'));
  drawLabel(c, l.verified, PAGE.verified.x, PAGE.verified.y, 0, mute);
  const ph = PAGE.phone;
  const handset = Skia.Path.MakeFromSVGString(
    'M-5.5 -7 Q-7 -7 -7 -5 Q-6 3 3 7 Q5 7.5 6 6 L7 4 Q7.2 3 6 2.4 L3.6 1.4 Q2.6 1 2 1.9 L1.3 2.8 Q-2.8 1 -4.4 -2.8 L-3.4 -3.5 Q-2.4 -4.1 -2.8 -5.2 L-3.8 -7 Q-4.3 -7.6 -5.5 -7 Z',
  )!;
  c.save();
  c.translate(ph.x, ph.y);
  c.drawPath(handset, violet);
  c.restore();
  c.drawLine(card.x + 12, PAGE.divider, card.x + card.w - 12, PAGE.divider, stroke(C.cardLine, 1));
  const ri = PAGE.rowIcon;
  c.drawCircle(ri.x, ri.y, 5.5, stroke(C.mute, 1.4));
  c.drawLine(ri.x, ri.y - 2.8, ri.x, ri.y, stroke(C.mute, 1.4));
  c.drawLine(ri.x, ri.y, ri.x + 2.2, ri.y + 1.4, stroke(C.mute, 1.4));
  drawLabel(c, l.progress, PAGE.progress.x, PAGE.progress.y, 0, ink);
  const cv = PAGE.chevron;
  const chev = Skia.PathBuilder.Make().moveTo(cv.x - 2.5, cv.y - 5).lineTo(cv.x + 2.5, cv.y).lineTo(cv.x - 2.5, cv.y + 5).build();
  c.drawPath(chev, stroke(C.mute, 1.6));

  // "you can make a difference"
  drawLabel(c, l.section, PAGE.section.x, PAGE.section.y, 0, mute);
  const c2 = PAGE.card2;
  const r2 = Skia.RRectXY(Skia.XYWHRect(c2.x, c2.y, c2.w, c2.h), 12, 12);
  c.drawRRect(r2, fill(C.card));
  c.drawRRect(r2, stroke(C.cardLine, 1));
  drawLabel(c, l.pros, PAGE.pros.x, PAGE.pros.y, 0, ink);
  drawLabel(c, l.prosSub, PAGE.prosSub.x, PAGE.prosSub.y, 0, mute);
  const wi = PAGE.waterIcon;
  const glass = Skia.PathBuilder.Make().moveTo(wi.x - 4, wi.y - 5).lineTo(wi.x - 3, wi.y + 5).lineTo(wi.x + 3, wi.y + 5).lineTo(wi.x + 4, wi.y - 5).build();
  c.drawPath(glass, stroke(C.violet, 1.4));
  drawLabel(c, l.water, PAGE.water.x + 6, PAGE.water.y, 0, violet);
  return rec.finishRecordingAsPicture();
}

/** The page as the sheet sees it: rasterised once at 3× and softly blurred, like the pin's. */
export function blurPage(page: SkPicture): SkImage | null {
  const k = 3;
  const surface = Skia.Surface.MakeOffscreen(REF_W * k, REF_H * k) ?? Skia.Surface.Make(REF_W * k, REF_H * k);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(k, k);
  const p = Skia.Paint();
  p.setImageFilter(Skia.ImageFilter.MakeBlur(PAGE_BLUR, PAGE_BLUR, TileMode.Clamp, null));
  c.saveLayer(p);
  c.drawPicture(page);
  c.restore();
  surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}

// ------------------------------------------------------------------ the scene (every frame)

export type Geo = { w: number; h: number; s: number; dy: number };
/** Touch on top of the timeline: tilt (−1 … 1) and a press on the CTA (0 … 1). */
export type Live = { tiltX: number; tiltY: number; cta: number };

function smooth01(x: number) {
  'worklet';
  const p = x < 0 ? 0 : x > 1 ? 1 : x;
  return p * p * (3 - 2 * p);
}
function rgba(r: number, g: number, b: number, a: number) {
  'worklet';
  return Skia.Color(`rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${a})`);
}
function hexMix(a: string, b: string, p: number, alpha = 1) {
  'worklet';
  const ca = Skia.Color(a);
  const cb = Skia.Color(b);
  return Float32Array.of(mix(ca[0], cb[0], p), mix(ca[1], cb[1], p), mix(ca[2], cb[2], p), alpha);
}

const CARS = CARS_BOX;
const INK = { below: 700, r0: 640, grow: 1060, feather: 150 };

function fillW(hex: string, a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(hex));
  p.setAlphaf(a);
  return p;
}

function drawStar(c: SkCanvas, r: Res, x: number, y: number, half: number, rot: number, a: number, gold: boolean) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate((rot * 180) / Math.PI, 0, 0);
  c.scale(half / 10, half / 10);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Skia.Color(gold ? '#ffcf6a' : '#ffffff'));
  glow.setAlphaf(a * 0.55);
  glow.setMaskFilter(r.blurStar);
  c.drawPath(r.star, glow);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(gold ? C.starGold : C.starWhite));
  p.setAlphaf(a);
  c.drawPath(r.star, p);
  if (gold) {
    c.scale(0.45, 0.45);
    const core = Skia.Paint();
    core.setAntiAlias(true);
    core.setColor(Skia.Color('#fff4cf'));
    core.setAlphaf(a);
    c.drawPath(r.star, core);
  }
  c.restore();
}

function drawScaled(c: SkCanvas, l: Label, cx: number, cy: number, k: number, p: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.save();
  c.translate(cx, cy);
  c.scale(k, k);
  drawLabel(c, l, 0, 0, 0.5, p);
  c.restore();
}

function drawCoin(c: SkCanvas, r: Res, f: Frame, bx: { x: number; y: number; w: number; h: number }, live: Live) {
  'worklet';
  // the coin sits above the ticket: it slides a little further than the ticket when tilted
  const k = { ...f.coin, x: f.coin.x + live.tiltX * 3 * f.bAmount, y: f.coin.y + live.tiltY * 2 * f.bAmount };
  // its shadow on the ticket: tight when it rests, wide and faint high in the air
  const sh = Skia.Paint();
  sh.setColor(Skia.Color(f.lift > 0.5 ? '#5b2a00' : '#6b5a33'));
  sh.setAlphaf(0.34 * (1 - k.shadow * 0.75));
  sh.setMaskFilter(r.blurCoin);
  const sw = k.r * (0.9 + k.shadow * 0.7);
  const sy = Math.max(bx.y + 6, k.y + k.r * 0.55 + k.shadow * 30);
  c.drawOval(Skia.XYWHRect(k.x - sw, sy - k.r * 0.28, sw * 2, k.r * 0.56), sh);

  const i = ((Math.round(k.frame) % COIN_FRAMES) + COIN_FRAMES) % COIN_FRAMES;
  const src = Skia.XYWHRect((i % 6) * 256, Math.floor(i / 6) * 256, 256, 256);
  const half = k.r * 1.1; // the render frames the 25 mm coin in 27.5 mm
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.drawImageRect(r.coin, src, Skia.XYWHRect(k.x - half, k.y - half, half * 2, half * 2), p);

  if (f.glint > 0 && f.glint < 1) {
    c.save();
    const disc = Skia.PathBuilder.Make().addCircle(k.x, k.y, k.r * 0.97).build();
    c.clipPath(disc, ClipOp.Intersect, true);
    const gx = k.x + mix(-1.6, 1.6, f.glint) * k.r;
    const gp = Skia.Paint();
    gp.setBlendMode(BlendMode.Screen);
    gp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: gx - k.r * 0.5, y: k.y - k.r },
        { x: gx + k.r * 0.5, y: k.y + k.r },
        [Skia.Color('rgba(255,248,220,0)'), Skia.Color('rgba(255,248,220,0.85)'), Skia.Color('rgba(255,248,220,0)')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    c.drawRect(Skia.XYWHRect(k.x - k.r, k.y - k.r, k.r * 2, k.r * 2), gp);
    c.restore();
  }
  if (f.ring.a > 0.01) {
    const p2 = Skia.Paint();
    p2.setAntiAlias(true);
    p2.setStyle(PaintStyle.Stroke);
    p2.setStrokeWidth(mix(0.8, 2.6, f.ring.a / 0.85)); // the ring thins as it spends itself
    p2.setColor(Skia.Color('#ffe7a8'));
    p2.setAlphaf(f.ring.a);
    c.drawCircle(B.coin.x, B.coin.y, f.ring.r, p2);
  }
  if (f.flash > 0.01) {
    const p3 = Skia.Paint();
    p3.setBlendMode(BlendMode.Plus);
    p3.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: B.coin.x, y: B.coin.y },
        B.coin.r * 3.4,
        [Skia.Color(`rgba(255,226,160,${0.75 * f.flash})`), Skia.Color('rgba(255,226,160,0)')],
        [0, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(B.coin.x, B.coin.y, B.coin.r * 3.4, p3);
  }
}

function bez(a: number, b: number, c: number, e: number, q: number) {
  'worklet';
  const u = 1 - q;
  return u * u * u * a + 3 * u * u * q * b + 3 * u * q * q * c + q * q * q * e;
}

/** The gold dust: cars → ticket on the way out, ticket → cars on the way home. */
function drawDust(c: SkCanvas, r: Res, t: number) {
  'worklet';
  const out = t >= T.carsOut[0] && t < T.gold[1] + 0.2;
  const home = t >= T.goldOut[0] && t < T.carsIn[1];
  if (!out && !home) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setBlendMode(BlendMode.Plus);
  for (let i = 0; i < r.dust.length; i++) {
    const m = r.dust[i];
    let k = 0;
    if (out) {
      if (t < m.rel || t > m.arr + 0.06) continue;
      k = Math.min(1, (t - m.rel) / (m.arr - m.rel));
    } else {
      const span = 0.5;
      if (t < m.back0 || t > m.back0 + span) continue;
      k = 1 - (t - m.back0) / span;
    }
    const q = k * k * (3 - 2 * k);
    const fadeIn = Math.min(1, (out ? k : 1 - k) / 0.12);
    const a = fadeIn * (k > 0.86 && out ? 1 - (k - 0.86) / 0.14 : 1) * 0.95;
    for (let j = 0; j < 2; j++) {
      const qq = j === 0 ? q : Math.max(0, q - (out ? 0.05 : -0.05));
      const x = bez(m.x0, m.c1x, m.c2x, m.tx, qq);
      const y = bez(m.y0, m.c1y, m.c2y, m.ty, qq);
      p.setColor(Skia.Color(m.warm > 0.5 ? '#ffd774' : '#fff0b8'));
      p.setAlphaf(a * (j === 0 ? 1 : 0.4));
      c.drawCircle(x, y, m.size * (1 - 0.35 * q) * (j === 0 ? 1 : 0.75), p);
    }
  }
}

/** Gold sparks kicked off the ticket's top edge by the coin's stamp, falling under gravity. */
function drawSparks(c: SkCanvas, t: number) {
  'worklet';
  const tau0 = t - T.land;
  if (tau0 <= 0 || tau0 > 0.75) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  p.setBlendMode(BlendMode.Plus);
  const n = 18;
  for (let i = 0; i < n; i++) {
    const h1 = Math.sin((i + 1) * 78.233) * 43758.5453;
    const r1 = h1 - Math.floor(h1);
    const h2 = Math.sin((i + 1) * 39.425) * 24634.6345;
    const r2 = h2 - Math.floor(h2);
    const side = i % 2 === 0 ? -1 : 1;
    const ang = -Math.PI / 2 + side * (0.35 + r1 * 1.05);
    const sp = 150 + r2 * 190;
    const life = 0.38 + r1 * 0.3;
    if (tau0 > life) continue;
    const vx = Math.cos(ang) * sp;
    const vy = Math.sin(ang) * sp;
    const ox = B.coin.x + side * B.coin.r * (0.45 + 0.4 * r2);
    const oy = B.ticket.y + 1;
    const x = ox + vx * tau0;
    const y = oy + vy * tau0 + 0.5 * 900 * tau0 * tau0;
    const cvy = vy + 900 * tau0;
    const fade = 1 - tau0 / life;
    p.setStrokeWidth(0.7 + 1.3 * fade);
    p.setColor(Skia.Color('#ffe7a0'));
    p.setAlphaf(fade);
    c.drawLine(x, y, x - vx * 0.02, y - cvy * 0.02, p);
  }
}

/** Warm light behind the ticket (the one glow on screen). */
function drawAura(c: SkCanvas, f: Frame) {
  'worklet';
  if (f.aura <= 0.005) return;
  const bx = f.ticket;
  const cx = bx.x + bx.w / 2;
  const cy = bx.y + bx.h / 2;
  const m = Skia.Matrix();
  m.translate(cx, cy);
  m.scale(1, 0.62);
  m.translate(-cx, -cy);
  const p = Skia.Paint();
  p.setBlendMode(BlendMode.Screen);
  p.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy },
      bx.w * 0.85,
      [Skia.Color(`rgba(255,176,72,${Math.min(0.75, f.aura)})`), Skia.Color('rgba(255,176,72,0)')],
      [0.25, 1],
      TileMode.Clamp,
      m,
    ),
  );
  c.drawRect(Skia.XYWHRect(cx - bx.w, cy - bx.w * 0.7, bx.w * 2, bx.w * 1.4), p);
}

function drawTicket(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const L = r.labels;
  const bx = f.ticket;
  const mp = (bx.w - A.ticket.w) / (B.ticket.w - A.ticket.w); // the morph's progress, overshoot included
  const cx = bx.x + bx.w / 2;
  const cy = bx.y + bx.h / 2;
  const rad = A.radius;
  const rr = Skia.RRectXY(Skia.XYWHRect(bx.x, bx.y, bx.w, bx.h), rad, rad);

  c.save();
  // squash, press and the finger's tilt, about the ticket's centre
  c.translate(cx, cy + f.tDy);
  if (live.tiltX !== 0 || live.tiltY !== 0) {
    c.concat(processTransform3d([{ perspective: 520 }, { rotateX: -live.tiltY * 0.2 }, { rotateY: live.tiltX * 0.24 }]));
  }
  c.scale(f.tScaleX, f.tScaleY);
  c.translate(-cx, -cy);

  // shadows: A's grey one under the white card and its coin cradle, B's deep violet one
  const sA = 1 - f.lift;
  if (sA > 0.01) {
    const p = Skia.Paint();
    p.setColor(Skia.Color('rgba(90,70,30,0.16)'));
    p.setAlphaf(0.16 * sA);
    p.setMaskFilter(r.blurA);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx.x, bx.y + 3, bx.w, bx.h), rad, rad), p);
    if (f.halo > 0.01) c.drawCircle(A.coin.x, A.coin.y + 2, A.halo * f.halo, p);
  }
  if (f.lift > 0.01) {
    const p = Skia.Paint();
    p.setColor(Skia.Color('#1a0656'));
    p.setAlphaf(0.55 * f.lift);
    p.setMaskFilter(r.blurB);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx.x + 6, bx.y + 12, bx.w - 12, bx.h), rad, rad), p);
    const warm = Skia.Paint();
    warm.setColor(Skia.Color('#ffb347'));
    warm.setAlphaf(0.2 * f.lift);
    warm.setMaskFilter(r.blurB);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx.x - 4, bx.y - 2, bx.w + 8, bx.h + 4), rad + 4, rad + 4), warm);
  }

  // the white body, with its notches cut out (the sheet or the purple shows through)
  const notches = Skia.PathBuilder.Make().addCircle(bx.x, cy, f.notch).addCircle(bx.x + bx.w, cy, f.notch).build();
  c.save();
  c.clipPath(notches, ClipOp.Difference, true);
  const white = fillW('#ffffff', 1);
  if (f.halo > 0.01) c.drawCircle(A.coin.x, A.coin.y, A.halo * f.halo, white);
  c.drawRRect(rr, white);

  // the gold pour
  const goldOn = f.gold > 0.001;
  if (goldOn) {
    const p = Skia.Paint();
    p.setShader(
      r.foil.makeShader([bx.x, bx.y, bx.w, bx.h, f.gold, f.sweep, live.tiltX, live.tiltY]),
    );
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(bx.x, bx.y, bx.w, bx.h), p);
    c.restore();
  }

  // copy: A's dark lines where the ticket is still white, B's light lines where it is gold
  const winC = { x: mix(A.win.x, B.win.x, mp), y: mix(A.win.y, B.win.y, mp) };
  const prizeC = { x: mix(A.prize.x, B.prize.x, mp), y: mix(A.prize.y, B.prize.y, mp) };
  const winW = mix(L.winA.width, L.winB.width, mp);
  const prizeW = mix(L.prizeA.width, L.prizeB.width, mp);
  const edge = mix(-0.4, 1.4, f.gold);
  // the pour's edge in screen space: x = bx.x + bx.w · (edge − (v − 0.5) · 0.35), v down the ticket
  const xAt = (v: number) => bx.x + bx.w * (edge - (v - 0.5) * 0.35);
  const goldSide = Skia.PathBuilder.Make()
    .moveTo(bx.x - 400, bx.y - 200)
    .lineTo(xAt(-200 / bx.h), bx.y - 200)
    .lineTo(xAt(1 + 200 / bx.h), bx.y + bx.h + 200)
    .lineTo(bx.x - 400, bx.y + bx.h + 200)
    .close()
    .build();
  if (f.gold < 0.999) {
    c.save();
    if (goldOn) c.clipPath(goldSide, ClipOp.Difference, true);
    drawScaled(c, L.winA, winC.x, winC.y, winW / L.winA.width, fillW(C.winA, 1));
    const pz = Skia.Paint();
    pz.setAntiAlias(true);
    pz.setShader(r.prizeShader);
    drawScaled(c, L.prizeA, prizeC.x, prizeC.y, prizeW / L.prizeA.width, pz);
    c.restore();
  }
  if (goldOn) {
    c.save();
    c.clipPath(goldSide, ClipOp.Intersect, true);
    const sh = fillW('#7a3f05', 0.35);
    drawScaled(c, L.prizeB, prizeC.x, prizeC.y + 1.2, prizeW / L.prizeB.width, sh);
    drawScaled(c, L.winB, winC.x, winC.y, winW / L.winB.width, fillW(C.winB, 0.95));
    drawScaled(c, L.prizeB, prizeC.x, prizeC.y, prizeW / L.prizeB.width, fillW(C.prizeB, 1));
    c.restore();
  }
  c.restore(); // notches

  drawCoin(c, r, f, bx, live);
  c.restore();
}



function drawBenefits(c: SkCanvas, L: Labels, f: Frame) {
  'worklet';
  const m = f.benefits;
  const color = hexMix(C.bandInk, '#ffffff', m, 1);
  for (let i = 0; i < 2; i++) {
    const mi = i === 0 ? f.benefits : f.benefits2;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(hexMix(C.bandInk, '#ffffff', mi, 1));
    p.setAlphaf(mix(1, 0.92, mi));
    const x = mix(A.benefits[i].x, B.benefits[i].x, mi);
    const y = mix(A.benefits[i].y, B.benefits[i].y, mi);
    drawScaled(c, L.benefits[i], x, y, mix(1, BENEFIT_B_SCALE, mi), p);
  }
  // separators
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setColor(color);
  sp.setAlphaf(mix(0.45, 0.6, m));
  const sx = mix(A.seps[0], B.sep, m);
  const sh = mix(A.sepH, B.sepH, m);
  const sy = mix(A.benefits[0].y, B.benefits[0].y, m);
  c.drawRect(Skia.XYWHRect(sx - 0.6, sy - sh / 2, 1.2, sh), sp);
  if (f.third > 0.01) {
    const p = fillW(C.bandInk, f.third);
    drawScaled(c, L.benefits[2], A.benefits[2].x, A.benefits[2].y, 1, p);
    const s2 = fillW(C.bandInk, 0.45 * f.third);
    c.drawRect(Skia.XYWHRect(A.seps[1] - 0.6, A.benefits[0].y - A.sepH / 2, 1.2, A.sepH), s2);
  }
}

function drawButton(c: SkCanvas, L: Labels, f: Frame, live: Live) {
  'worklet';
  const m = f.button;
  const bx = {
    x: mix(A.button.x, B.button.x, m),
    y: mix(A.button.y, B.button.y, m),
    w: mix(A.button.w, B.button.w, m),
    h: mix(A.button.h, B.button.h, m),
  };
  const press = 1 - 0.04 * live.cta;
  const k = mix(1, WIDTH.ctaB / WIDTH.ctaA, m);
  c.save();
  c.translate(bx.x + bx.w / 2, bx.y + bx.h / 2);
  c.scale(press, press);
  if (f.ctaGlow > 0.01) {
    // one soft breath of light round the white button: the next thing to do
    const g = Skia.Paint();
    g.setColor(Skia.Color('#ffffff'));
    g.setAlphaf(0.42 * f.ctaGlow);
    g.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 + 8 * f.ctaGlow, true));
    const grow = 3 + 5 * f.ctaGlow;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-bx.w / 2 - grow, -bx.h / 2 - grow, bx.w + grow * 2, bx.h + grow * 2), A.radius + 2 + grow, A.radius + 2 + grow), g);
  }
  const rr = Skia.RRectXY(Skia.XYWHRect(-bx.w / 2, -bx.h / 2, bx.w, bx.h), A.radius + 2, A.radius + 2);
  // violet → white as a wipe from the left, leaning like the gold pour on the ticket
  const edge = -bx.w / 2 - 30 + (bx.w + 60) * m;
  const white = Skia.PathBuilder.Make()
    .moveTo(-bx.w, -bx.h)
    .lineTo(edge + 12, -bx.h)
    .lineTo(edge - 12, bx.h)
    .lineTo(-bx.w, bx.h)
    .close()
    .build();
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  if (m < 0.999) {
    c.drawRRect(rr, fillW(C.ctaA, 1));
    c.save();
    if (m > 0.001) c.clipPath(white, ClipOp.Difference, true);
    drawScaled(c, L.cta, 0, 0, k, fillW(C.ctaAText, 1));
    c.restore();
  }
  if (m > 0.001) {
    c.save();
    if (m < 0.999) c.clipPath(white, ClipOp.Intersect, true);
    c.drawRRect(rr, fillW(C.ctaB, 1));
    drawScaled(c, L.cta, 0, 0, k, fillW(C.ctaBText, 1));
    c.restore();
  }
  c.restore();
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  const L = r.labels;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  // the page, hanging from the top
  c.drawImageRect(r.page, Skia.XYWHRect(0, 0, r.page.width(), r.page.height()), Skia.XYWHRect(0, 0, REF_W * g.s, REF_H * g.s), paint);

  // the dim over it
  const da = C.dimA;
  const db = C.dimB;
  paint.setColor(rgba(mix(da[0], db[0], f.dim), mix(da[1], db[1], f.dim), mix(da[2], db[2], f.dim), mix(da[3], db[3], f.dim)));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), paint);

  // the sheet, standing on the bottom edge
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);

  // A's golden light behind the cars
  if (f.glow > 0.003) {
    const p = Skia.Paint();
    p.setShader(r.glowShader);
    p.setAlphaf(f.glow);
    c.drawRect(Skia.XYWHRect(0, A.glowTop, REF_W, A.stageEdge - A.glowTop), p);
    const q = Skia.Paint();
    q.setShader(r.glowSpot);
    q.setAlphaf(f.glow);
    c.drawRect(Skia.XYWHRect(0, A.glowTop, REF_W, A.stageEdge - A.glowTop), q);
  }

  // the purple rises from below the bottom edge like a dawn: the edge of a huge circle whose
  // centre sits far under the screen, so its front is a shallow arc, highest under the ticket
  const inkR = INK.r0 + f.ink * INK.grow;
  const inkShader = () =>
    Skia.Shader.MakeRadialGradient(
      { x: f.inkX, y: REF_H + INK.below },
      inkR,
      [Skia.Color('black'), Skia.Color('transparent')],
      [(inkR - INK.feather) / inkR, 1],
      TileMode.Clamp,
    );

  // A's sheet: the cream stage, eaten by the ink
  if (f.ink < 0.999) {
    // the cream sheet sinks a little and fades while the dawn eats it from below, so no empty
    // stage is left hanging over the page
    const sink = f.ink * f.ink * 56;
    const fade = Skia.Paint();
    fade.setAlphaf(1 - smooth01((f.ink - 0.08) / 0.5));
    c.saveLayer(fade);
    c.translate(0, sink);
    const p = Skia.Paint();
    p.setShader(r.stageShader);
    c.drawPath(r.stage, p);
    if (f.benefits < 0.999) {
      const band = Skia.Paint();
      band.setColor(Skia.Color(C.band));
      band.setAlphaf(1 - f.benefits);
      c.drawRect(Skia.XYWHRect(A.band.x, A.band.y, A.band.w, A.band.h), band);
    }
    if (f.ink > 0.001) {
      const e = Skia.Paint();
      e.setShader(inkShader());
      e.setBlendMode(BlendMode.DstOut);
      c.translate(0, -sink);
      c.drawRect(Skia.XYWHRect(0, A.glowTop, REF_W, REF_H - A.glowTop), e);
    }
    c.restore();
  }
  // B's purple, revealed by the same ink
  if (f.ink > 0.001) {
    c.saveLayer();
    const p = Skia.Paint();
    p.setShader(r.purple);
    c.drawRect(Skia.XYWHRect(0, B.fadeTop, REF_W, REF_H - B.fadeTop), p);
    const q = Skia.Paint();
    q.setShader(r.purpleCore);
    c.drawRect(Skia.XYWHRect(0, B.fadeTop, REF_W, REF_H - B.fadeTop), q);
    if (f.ink < 0.999) {
      const m = Skia.Paint();
      m.setShader(inkShader());
      m.setBlendMode(BlendMode.DstIn);
      c.drawRect(Skia.XYWHRect(0, 0, REF_W, REF_H), m);
    }
    c.restore();
    // the dawn's front glows violet while it climbs
    const rim = Math.sin(Math.PI * Math.min(1, f.ink * 1.15));
    if (rim > 0.01 && f.ink < 0.999) {
      const p = Skia.Paint();
      p.setBlendMode(BlendMode.Screen);
      p.setShader(
        Skia.Shader.MakeRadialGradient(
          { x: f.inkX, y: REF_H + INK.below },
          inkR,
          [Skia.Color('rgba(139,108,255,0)'), Skia.Color(`rgba(150,120,255,${0.55 * rim})`), Skia.Color('rgba(139,108,255,0)')],
          [(inkR - INK.feather * 1.3) / inkR, (inkR - INK.feather * 0.5) / inkR, 1],
          TileMode.Clamp,
        ),
      );
      c.drawRect(Skia.XYWHRect(0, 0, REF_W, REF_H), p);
    }
  }

  // A's stars, the cars, and the copy above the ticket
  const starA = f.starsA * f.glow;
  if (starA > 0.01) {
    for (let i = 0; i < A.sparkles.length; i++) {
      const s = A.sparkles[i];
      const tw = 0.75 + 0.25 * (0.5 + 0.5 * Math.sin(2 * Math.PI * ((s.k * f.t) / LOOP + s.ph)));
      drawStar(c, r, s.x, s.y, s.s * tw, 0, starA, false);
    }
  }
  if (f.cars < 0.999) {
    const p = Skia.Paint();
    if (f.cars > 0.001 || f.carsGlint >= 0) {
      p.setShader(r.dissolve.makeShaderWithChildren([CARS.x, CARS.y, CARS.w, CARS.h, f.cars, f.carsGlint], [r.carsShader]));
    } else p.setShader(r.carsShader);
    c.drawRect(Skia.XYWHRect(CARS.x, CARS.y - 4, CARS.w, CARS.h + 8), p);
  }
  if (f.thanks.a > 0.01) {
    const p = fillW(C.ink, f.thanks.a);
    drawLabel(c, L.thanks, A.thanks.x, A.thanks.y + f.thanks.dy, 0.5, p);
  }
  if (f.callback.a > 0.01) {
    const p = fillW('#55545f', f.callback.a);
    drawLabel(c, L.callback, A.callback.x, A.callback.y + f.callback.dy, 0.5, p);
  }
  if (f.divider > 0.01) {
    const p = Skia.Paint();
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: A.divider.x0, y: 0 },
        { x: A.divider.x1, y: 0 },
        [Skia.Color('rgba(150,140,110,0)'), Skia.Color('rgba(150,140,110,0.35)'), Skia.Color('rgba(150,140,110,0)')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    p.setAlphaf(f.divider);
    c.drawRect(Skia.XYWHRect(A.divider.x0, A.divider.y - 0.5, A.divider.x1 - A.divider.x0, 1), p);
  }

  drawAura(c, f);
  drawTicket(c, r, f, live);
  drawDust(c, r, f.t);
  drawSparks(c, f.t);

  // B's stars, a layer behind the ticket: they slide the other way when it tilts
  const px = -live.tiltX * 7 * f.bAmount;
  const py = -live.tiltY * 5 * f.bAmount;
  for (let i = 0; i < f.stars.length; i++) {
    const s = f.stars[i];
    if (s.a > 0.01) drawStar(c, r, s.x + px, s.y + py, s.s, s.rot, s.a, B.sparkles[i].gold);
  }

  drawBenefits(c, L, f);
  drawButton(c, L, f, live);

  if (f.close.a > 0.01) {
    const cl = B.close;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(2);
    p.setStrokeCap(StrokeCap.Round);
    p.setColor(Skia.Color('white'));
    p.setAlphaf(f.close.a);
    c.save();
    c.translate(cl.x, cl.y);
    c.rotate((f.close.rot * 180) / Math.PI, 0, 0);
    c.scale(f.close.s, f.close.s);
    const h = cl.s / 2;
    c.drawLine(-h, -h, h, h, p);
    c.drawLine(-h, h, h, -h, p);
    c.restore();
  }
  c.restore();
}

