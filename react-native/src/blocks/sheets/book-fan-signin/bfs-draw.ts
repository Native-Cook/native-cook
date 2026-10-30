// Book Fan Sign-in — drawing. The home page's still parts are recorded once (and baked once more,
// blurred, for under the sheet); the wall of covers, the flights and the sheet are drawn every frame.

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
  type SkPath,
  type SkPicture,
  type SkPoint,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { cellAt, coverOf, FLIGHTS, FLYING, type Frame, LOOP, mix, slot, smooth, wallShift } from './bfs-motion';
import { A, B, C, COPY, COVERS, REF_H, REF_W, WALL } from './bfs-theme';

export type Label = { font: SkFont; glyphs: number[]; pos: SkPoint[]; width: number; size: number };

export function makeLabel(face: SkTypeface, str: string, size: number, track = 0): Label {
  const font = Skia.Font(face, size);
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + (i < ids.length - 1 ? track * size : 0);
  }
  return { font, glyphs: ids, pos, width: x, size };
}

export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.36, l.font, paint);
}

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };

type SheetLabels = { signIn: Label; tag: Label; title: Label[]; sub: Label[]; cta: Label };

export type Res = {
  covers: SkImage;
  over: SkPicture; // the home page's text, field, chips and books (over the wall)
  blurred: SkImage; // the whole home page, blurred, for under the sheet
  labels: SheetLabels;
  logo: SkPath; // the app icon's mark
  mail: SkPath;
};

function fill(color: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}
function stroke(color: string, w: number) {
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function coverSrc(n: number) {
  'worklet';
  const col = n % COVERS.cols;
  const row = Math.floor(n / COVERS.cols);
  return Skia.XYWHRect(col * COVERS.w, row * COVERS.h, COVERS.w, COVERS.h);
}

/** A cover at (x, y), turned `deg`, `w` wide, with a soft shadow `sh` (0 … 1). */
function drawCover(c: SkCanvas, img: SkImage, n: number, x: number, y: number, w: number, deg: number, sh: number, alpha = 1, lift = 0) {
  'worklet';
  const h = (w * COVERS.h) / COVERS.w;
  c.save();
  c.translate(x, y - 3 * lift);
  c.rotate(deg, 0, 0);
  c.scale(1 + 0.05 * lift, 1 + 0.05 * lift);
  if (sh > 0.01) {
    const s = Skia.Paint();
    s.setAntiAlias(true);
    s.setColor(Float32Array.of(0.1, 0.08, 0.15, 0.22 * sh * alpha));
    s.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4 + 8 * sh, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2 + 2, -h / 2 + 4 + 8 * sh, w, h), 3, 3), s);
  }
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setAlphaf(alpha);
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), 2.5, 2.5), ClipOp.Intersect, true);
  c.drawImageRectOptions(img, coverSrc(n), Skia.XYWHRect(-w / 2, -h / 2, w, h), FilterMode.Linear, MipmapMode.Linear, p);
  // a lifted cover catches the light across its gloss
  if (lift > 0.02) {
    const gl = Skia.Paint();
    gl.setBlendMode(BlendMode.Plus);
    gl.setShader(Skia.Shader.MakeLinearGradient({ x: -w / 2, y: -h / 2 }, { x: w / 2, y: h / 2 }, [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.22 * lift * alpha), Float32Array.of(1, 1, 1, 0)], [0.2, 0.5, 0.8], TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), gl);
  }
  c.restore();
  c.restore();
}

/** The tilted wall, fading into the page at its edges; `taken` empties the cells whose covers flew. */
function drawWall(c: SkCanvas, covers: SkImage, shift: number, taken: number[], amb: number) {
  'worklet';
  for (let i = 0; i < WALL.cols; i++) {
    for (let j = -1; j <= WALL.rows; j++) {
      let skip = false;
      for (let k = 0; k < FLYING.length; k++) if (FLYING[k].i === i && FLYING[k].j === j && taken[k] > 0.001) skip = true;
      if (skip) continue;
      const p = cellAt(i, j, shift);
      if (p.y < -60 || p.y > 330) continue;
      // a wave of light crosses the shelf twice a loop, lifting each cover a little as it passes
      const front = -180 + ((amb % (LOOP / 2)) / (LOOP / 2)) * 900;
      const d = p.x + p.y * 0.6 - front;
      const lift = amb < 0 ? 0 : Math.exp(-(d * d) / 2600);
      drawCover(c, covers, coverOf(i, j), p.x, p.y, WALL.w, WALL.angle, 0.25 + 0.6 * lift, 0.9, lift);
    }
  }
  // the wall dissolves into white away from its middle, as in the pin
  const fade = Skia.Paint();
  fade.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: WALL.cx, y: WALL.cy },
      WALL.fadeOut,
      [Float32Array.of(1, 1, 1, 0.08), Float32Array.of(1, 1, 1, 0.62), Float32Array.of(1, 1, 1, 1)],
      [WALL.fadeIn / WALL.fadeOut, 0.74, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, -60, REF_W, 420), fade);
  const bottom = Skia.Paint();
  bottom.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 228 }, { x: 0, y: 292 }, [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 1)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 228, REF_W, 140), bottom);
}

function drawHomeOver(c: SkCanvas, l: { [k: string]: Label | Label[] }, covers: SkImage) {
  const ink = fill(C.ink);
  const grey = fill(C.grey);
  const greyL = fill(C.greyLight);
  drawLabel(c, l.logo as Label, A.logo.x, A.logo.y, 0, ink);
  drawLabel(c, l.tagline as Label, A.tagline.x, A.tagline.y, 0, fill(C.pink));
  const title = l.title as Label[];
  drawLabel(c, title[0], A.title.x, A.title.y[0], 0, ink);
  drawLabel(c, title[1], A.title.x, A.title.y[1], 0, ink);
  const sub = l.sub as Label[];
  drawLabel(c, sub[0], A.sub.x, A.sub.y[0], 0, grey);
  drawLabel(c, sub[1], A.sub.x, A.sub.y[1], 0, grey);
  // search
  const s = A.search;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.x, s.y, s.w, s.h), s.h / 2, s.h / 2), fill(C.field));
  c.drawCircle(s.x + 26, s.y + s.h / 2 - 1, 6.5, stroke(C.greyLight, 1.6));
  c.drawLine(s.x + 31, s.y + s.h / 2 + 4, s.x + 34.5, s.y + s.h / 2 + 7.5, stroke(C.greyLight, 1.6));
  drawLabel(c, l.search as Label, s.x + 42, s.y + s.h / 2, 0, greyL);
  // chips
  const ch = A.chips;
  const chips = l.chips as Label[];
  for (let i = 0; i < 4; i++) {
    const on = i === 0;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ch.x[i], ch.y, ch.w[i], ch.h), ch.h / 2, ch.h / 2), fill(on ? C.black : C.chip));
    const ix = ch.x[i] + 16;
    const iy = ch.y + ch.h / 2;
    if (i === 0) {
      // a sparkle
      const sp = Skia.Path.MakeFromSVGString('M0 -6.5 Q0.9 -0.9 6.5 0 Q0.9 0.9 0 6.5 Q-0.9 0.9 -6.5 0 Q-0.9 -0.9 0 -6.5 Z')!;
      sp.offset(ix, iy);
      c.drawPath(sp, fill('#FFFFFF'));
    } else if (i === 1) {
      const fl = Skia.Path.MakeFromSVGString('M0 -6 C3 -2.5 4.6 0 4.2 2.4 C3.8 5 2 6.3 0 6.3 C-2.2 6.3 -4 4.9 -4.2 2.6 C-4.4 0.4 -2.8 -1 -2.2 -2.8 C-1.4 -1.3 -0.6 -0.8 -0.2 -0.9 C-0.6 -2.6 -0.6 -4.4 0 -6 Z')!;
      fl.offset(ix, iy);
      c.drawPath(fl, fill(C.chipInk));
    } else if (i === 2) {
      for (let k = 0; k < 3; k++) c.drawLine(ix - 5, iy - 4 + k * 4, ix + 1 - k * 1.5, iy - 4 + k * 4, stroke(C.chipInk, 1.5));
      c.drawCircle(ix + 4.5, iy + 4, 1.8, fill(C.chipInk));
    } else {
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ix - 5.5, iy - 5, 11, 10), 2, 2), stroke(C.chipInk, 1.5));
    }
    drawLabel(c, chips[i], ch.x[i] + 30, iy, 0, fill(on ? '#FFFFFF' : C.chipInk));
  }
  // recommended
  drawLabel(c, l.recommended as Label, A.recommended.x, A.recommended.y, 0, fill(C.grey));
  const chev = (y: number) => {
    const p = Skia.Path.Make();
    p.moveTo(369, y - 5);
    p.lineTo(374, y);
    p.lineTo(369, y + 5);
    c.drawPath(p, stroke(C.greyLight, 1.8));
  };
  chev(A.recommended.y);
  const bk = A.books;
  const bookTitles = l.bookTitles as Label[];
  const bookAuthors = l.bookAuthors as Label[];
  for (let i = 0; i < 3; i++) {
    const n = COPY.books[i].cover;
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Float32Array.of(0, 0, 0, 0.16));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
    c.drawRect(Skia.XYWHRect(bk.x[i] + 2, bk.y + 5, bk.w, bk.h), sh);
    c.drawImageRectOptions(covers, coverSrc(n), Skia.XYWHRect(bk.x[i], bk.y, bk.w, bk.h), FilterMode.Linear, MipmapMode.Linear, null);
    drawLabel(c, bookTitles[i * 2], bk.x[i] - 4 + (i === 2 ? -10 : 0), bk.titleY, 0, ink);
    drawLabel(c, bookTitles[i * 2 + 1], bk.x[i] - 4 + (i === 2 ? -10 : 0), bk.titleY + 18, 0, ink);
    drawLabel(c, bookAuthors[i], bk.x[i] - 4 + (i === 2 ? -10 : 0), bk.authorY + 18, 0, grey);
  }
  drawLabel(c, l.topPicks as Label, A.recommended.x, A.topPicks.y + 14, 0, fill(C.grey));
  chev(A.topPicks.y + 14);
  for (let i = 0; i < 3; i++) {
    c.drawImageRectOptions(covers, coverSrc((i * 4 + 5) % 10), Skia.XYWHRect(bk.x[i], A.nextRow.y + 20, bk.w, bk.h), FilterMode.Linear, MipmapMode.Linear, null);
  }
}

export function makeRes(f: Faces, covers: SkImage): Res | null {
  const l: { [k: string]: Label | Label[] } = {
    logo: makeLabel(f.bold, COPY.logo, A.logo.size, -0.045),
    tagline: makeLabel(f.medium, COPY.tagline, A.tagline.size),
    title: COPY.title.map((s) => makeLabel(f.bold, s, A.title.size, -0.02)),
    sub: COPY.sub.map((s) => makeLabel(f.regular, s, A.sub.size)),
    search: makeLabel(f.regular, COPY.search, 16),
    chips: COPY.chips.map((s) => makeLabel(f.medium, s, 13.6)),
    recommended: makeLabel(f.medium, COPY.recommended, 15),
    topPicks: makeLabel(f.medium, COPY.topPicks, 15),
    bookTitles: COPY.books.flatMap((b) => b.title.map((s) => makeLabel(f.semi, s, 14.5))),
    bookAuthors: COPY.books.map((b) => makeLabel(f.regular, b.author, 14.5)),
  };
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, REF_W, REF_H + 200));
  drawHomeOver(c, l, covers);
  const over = rec.finishRecordingAsPicture();

  // the whole page, blurred once, for under the sheet (the wall at its loop start)
  const k = 2;
  const surface = Skia.Surface.MakeOffscreen(REF_W * k, REF_H * k) ?? Skia.Surface.Make(REF_W * k, REF_H * k);
  if (!surface) return null;
  const bc = surface.getCanvas();
  bc.scale(k, k);
  const bp = Skia.Paint();
  bp.setImageFilter(Skia.ImageFilter.MakeBlur(13, 13, TileMode.Clamp, null));
  bc.saveLayer(bp);
  bc.drawColor(Skia.Color(C.page));
  drawWall(bc, covers, wallShift(0, 0), [0, 0, 0, 0, 0, 0], -1);
  bc.drawPicture(over);
  bc.restore();
  surface.flush();
  const blurred = surface.makeImageSnapshot().makeNonTextureImage();
  if (!blurred) return null;

  // the icon's mark: one looping ribbon stroke (an "r" that turns into a page curl)
  const logo = Skia.Path.MakeFromSVGString('M-15 14 L-15 -6 C-15 -15 -5 -17 -1 -10 C2 -4 -4 2 3 6 C9 9.5 15 4 14 -4 C13 -11 6 -12 4 -7')!;
  const mail = Skia.Path.Make();
  mail.addRRect(Skia.RRectXY(Skia.XYWHRect(-10, -7.5, 20, 15), 2.5, 2.5));
  mail.moveTo(-9, -6);
  mail.lineTo(0, 1.2);
  mail.lineTo(9, -6);
  return {
    covers,
    over,
    blurred,
    labels: {
      signIn: makeLabel(f.medium, COPY.signIn, 16),
      tag: makeLabel(f.medium, COPY.tag, 14),
      title: COPY.sheetTitle.map((s) => makeLabel(f.bold, s, B.title.size, -0.02)),
      sub: COPY.sheetSub.map((s) => makeLabel(f.regular, s, B.sub.size)),
      cta: makeLabel(f.semi, COPY.cta, 19),
    },
    logo,
    mail,
  };
}

// ------------------------------------------------------------------ the scene

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = { amb: number; scroll: number; drag: number; cta: number; press: number };

function hex(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}

/** Where the sheet stands: down by its height when closed, plus the finger's pull. */
function sheetOffset(f: Frame, live: Live) {
  'worklet';
  return (1 - f.sheet) * (REF_H - B.sheet.y + 40) + Math.max(0, live.drag) * Math.min(1, f.sheet) + Math.min(0, live.drag) * 0.25;
}

function drawSheet(c: SkCanvas, r: Res, f: Frame, live: Live, oy: number) {
  'worklet';
  const s = B.sheet;
  c.save();
  c.translate(0, oy);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0, 0, 0, 0.18 * f.sheet));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.x, s.y + 6, s.w, s.h), s.r, s.r), sh);
  const card = Skia.RRectXY(Skia.XYWHRect(s.x, s.y, s.w, s.h), s.r, s.r);
  const white = Skia.Paint();
  white.setAntiAlias(true);
  white.setColor(Float32Array.of(1, 1, 1, 1));
  c.drawRRect(card, white);
  // the strip of covers that flew in, drifting slowly, fading down into the sheet
  c.save();
  c.clipRRect(card, ClipOp.Intersect, true);
  const drift = 4 * Math.sin((2 * Math.PI * live.amb) / 9);
  for (let k = 0; k < FLIGHTS; k++) {
    const u = f.flights[k];
    if (u < 0.86) continue;
    const sl = slot(k);
    const a = smooth((u - 0.86) / 0.14);
    drawCover(c, r.covers, coverOf(FLYING[k].i, FLYING[k].j), sl.x + drift, sl.y, B.strip.w, sl.angle, 0.4, a);
  }
  const fade = Skia.Paint();
  fade.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: B.stripFade.from }, { x: 0, y: B.stripFade.to }, [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 1)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(s.x, B.stripFade.from, s.w, B.stripFade.to - B.stripFade.from + 1), fade);
  c.restore();

  // the icon pops in and its mark draws itself
  const ic = B.icon;
  const k = Math.max(0, f.icon);
  if (k > 0.01) {
    c.save();
    c.translate(ic.x + ic.s / 2, ic.y + ic.s / 2);
    c.scale(0.7 + 0.3 * k, 0.7 + 0.3 * k);
    const ip = Skia.Paint();
    ip.setAntiAlias(true);
    ip.setColor(Float32Array.of(0.07, 0.07, 0.07, Math.min(1, k * 2)));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-ic.s / 2, -ic.s / 2, ic.s, ic.s), ic.r, ic.r), ip);
    if (f.logo > 0.01) {
      const m = r.logo.copy();
      if (f.logo < 0.999) m.trim(0, f.logo, false);
      const mp = Skia.Paint();
      mp.setAntiAlias(true);
      mp.setStyle(PaintStyle.Stroke);
      mp.setStrokeWidth(5.2);
      mp.setStrokeCap(StrokeCap.Round);
      mp.setStrokeJoin(StrokeJoin.Round);
      mp.setColor(Float32Array.of(1, 1, 1, 1));
      c.drawPath(m, mp);
    }
    c.restore();
  }
  const L = r.labels;
  // the tag pill
  if (f.tag > 0.01) {
    const t = B.tag;
    c.save();
    c.translate(0, 10 * (1 - f.tag));
    const pp = Skia.Paint();
    pp.setAntiAlias(true);
    pp.setColor(hex(C.pinkSoft, f.tag));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(t.x, t.y, t.w, t.h), t.h / 2, t.h / 2), pp);
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(hex(C.pink, f.tag));
    drawLabel(c, L.tag, REF_W / 2, t.y + t.h / 2, 0.5, tp);
    c.restore();
  }
  // the title comes into focus line by line; "Practice Does." gets a light running through its pink
  const lines = [f.title, f.title2];
  for (let i = 0; i < 2; i++) {
    const a = lines[i];
    if (a <= 0.01) continue;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(i === 0 ? hex(C.ink, a) : hex(C.pink, a));
    if (a < 0.98) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 * (1 - a), true));
    drawLabel(c, L.title[i], REF_W / 2, B.title.y[i] + 12 * (1 - a), 0.5, p);
    if (i === 1 && f.shimmer >= 0) {
      const lw = L.title[1].width;
      const x0 = REF_W / 2 - lw / 2;
      const sx = mix(x0 - 60, x0 + lw + 60, f.shimmer);
      const g = Skia.Paint();
      g.setAntiAlias(true);
      g.setShader(
        Skia.Shader.MakeLinearGradient({ x: sx - 40, y: 0 }, { x: sx + 40, y: 0 }, [hex('#FFFFFF', 0), hex('#FFD1E2', 0.95), hex('#FFFFFF', 0)], null, TileMode.Clamp),
      );
      c.save();
      c.clipRect(Skia.XYWHRect(sx - 40, B.title.y[1] - 22, 80, 44), ClipOp.Intersect, true);
      drawLabel(c, L.title[1], REF_W / 2, B.title.y[1], 0.5, g);
      c.restore();
    }
  }
  if (f.sub > 0.01) {
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setColor(hex(C.grey, f.sub));
    for (let i = 0; i < 3; i++) drawLabel(c, L.sub[i], REF_W / 2, B.sub.y[i] + 8 * (1 - f.sub) * (1 + i * 0.4), 0.5, sp);
  }
  if (f.cta > 0.01) {
    const b = B.cta;
    const s2 = 1 - 0.03 * live.cta;
    c.save();
    c.translate(b.x + b.w / 2, b.y + b.h / 2 + 22 * (1 - f.cta));
    c.scale(s2, s2);
    const bp = Skia.Paint();
    bp.setAntiAlias(true);
    bp.setColor(hex(C.black, f.cta));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), b.h / 2, b.h / 2), bp);
    const lp = Skia.Paint();
    lp.setAntiAlias(true);
    lp.setColor(Float32Array.of(1, 1, 1, f.cta));
    const lw = L.cta.width + 32;
    drawLabel(c, L.cta, -lw / 2 + 32, 0, 0, lp);
    const mp = Skia.Paint();
    mp.setAntiAlias(true);
    mp.setStyle(PaintStyle.Stroke);
    mp.setStrokeWidth(1.8);
    mp.setStrokeJoin(StrokeJoin.Round);
    mp.setColor(Float32Array.of(1, 1, 1, f.cta));
    c.save();
    c.translate(-lw / 2 + 10, 0);
    c.drawPath(r.mail, mp);
    c.restore();
    c.restore();
  }
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  c.drawColor(hex(C.page));
  c.save();
  c.translate(0, 0);
  c.scale(g.s, g.s);
  const shift = wallShift(live.amb, live.scroll);
  // the home page (its wall drifting), then the blurred copy sweeping down over it
  drawWall(c, r.covers, shift, f.flights, live.amb);
  // Sign in
  const si = A.signIn;
  const ps = 1 - 0.04 * Math.max(f.press, live.press);
  c.save();
  c.translate(si.x + si.w / 2, si.y + si.h / 2);
  c.scale(ps, ps);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0, 0, 0, 0.08));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-si.w / 2, -si.h / 2 + 3, si.w, si.h), si.h / 2, si.h / 2), sh);
  const wp = Skia.Paint();
  wp.setAntiAlias(true);
  wp.setColor(Float32Array.of(1, 1, 1, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-si.w / 2, -si.h / 2, si.w, si.h), si.h / 2, si.h / 2), wp);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(C.ink));
  drawLabel(c, r.labels.signIn, 0, 0, 0.5, ink);
  c.restore();
  c.drawPicture(r.over);
  if (f.blur > 0.001) {
    const edge = mix(-160, REF_H + 40, f.blur);
    c.saveLayer();
    c.drawImageRectOptions(r.blurred, Skia.XYWHRect(0, 0, r.blurred.width(), r.blurred.height()), Skia.XYWHRect(0, 0, REF_W, REF_H), FilterMode.Linear, MipmapMode.None, null);
    const m = Skia.Paint();
    m.setBlendMode(BlendMode.DstIn);
    m.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: edge }, { x: 0, y: edge + 160 }, [Float32Array.of(0, 0, 0, 1), Float32Array.of(0, 0, 0, 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, REF_W, REF_H), m);
    c.restore();
  }
  if (f.dim > 0.001) {
    const d = Skia.Paint();
    d.setColor(Float32Array.of(0, 0, 0, 0.42 * f.dim));
    c.drawRect(Skia.XYWHRect(0, -50, REF_W, REF_H + 100), d);
  }
  const oy = sheetOffset(f, live) + g.dy / g.s;
  if (f.sheet > 0.001) drawSheet(c, r, f, live, oy);
  // the covers in flight, over everything, landing in the sheet's strip
  for (let k = 0; k < FLIGHTS; k++) {
    const u = f.flights[k];
    if (u <= 0.001 || u >= 0.999) continue;
    const src = cellAt(FLYING[k].i, FLYING[k].j, shift);
    const sl = slot(k);
    const ex = sl.x;
    const ey = sl.y + oy;
    // an arc up and over, the cover lifting toward you and turning to the strip's angle
    const cx = mix(src.x, ex, 0.5) + (k - 2.5) * 22;
    const cy = mix(src.y, ey, 0.45) - 36;
    const q = 1 - u;
    const x = q * q * src.x + 2 * q * u * cx + u * u * ex;
    const y = q * q * src.y + 2 * q * u * cy + u * u * ey;
    const lift = Math.sin(Math.PI * u);
    const w = mix(WALL.w, B.strip.w, u) * (1 + 0.22 * lift);
    const deg = mix(WALL.angle, sl.angle, u) + 14 * lift * (k % 2 === 0 ? 1 : -1);
    const a = u > 0.86 ? 1 - smooth((u - 0.86) / 0.14) : 1;
    drawCover(c, r.covers, coverOf(FLYING[k].i, FLYING[k].j), x, y, w, deg, lift, a);
  }
  c.restore();
}
