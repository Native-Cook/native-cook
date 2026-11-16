// Coin Pile Collect — everything that is drawn. One window-sized picture per frame: the page, the flat tiles, the type
// (kerned glyph runs baked from HarfBuzz), the pile (one ray-cast shader), the pill, the amount's odometer and the
// exchange coin. The same functions run on the UI thread in the frame's picture and in node on CanvasKit for review.
//
// Every worklet is declared above the ones that call it. Host objects (fonts, effects, shaders, paths) are made on the
// JS thread in makeRes and only read here.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPath,
  type SkPoint,
  type SkRuntimeEffect,
  type SkShader,
  type SkTypeface,
} from '@shopify/react-native-skia';

import { ALIVE, AX, AY, FACE, GLOW, PHI, STRIDE, X, Y, Z } from './cpc-physics';
import { CTL, C_BULGE, C_CENTS, C_FLOW, C_LIT, C_PILLA, C_PRESS } from './cpc-engine';
import { GLYPHS } from './cpc-glyphs';
import { PILL, XCOIN, clamp01, mix, smooth } from './cpc-motion';
import { COPY, PREMIUM, comp, ref } from './cpc-theme';
import { COIN_EMBOSS_CELL, PILE_SKSL, PAGE_SKSL, XCOIN_SKSL } from './cpc-shaders';

export type PreRun = { ids: number[]; pts: SkPoint[]; font: SkFont; w: number };
export type Uni = { fx: SkRuntimeEffect; u: number[]; slot: Record<string, number> };

export type Res = {
  p3: number;
  upx: number;
  fs: number;
  pile: Uni;
  xcoin: Uni;
  page: Uni;
  emb: SkShader;
  pageShader: SkShader;
  col: Record<string, SkColor>;
  tiles: { lime: SkPath; teal: SkPath; magenta: SkPath; navy: SkPath };
  run: Record<string, PreRun>;
  amount: {
    font: SkFont;
    size: number;
    digitIds: number[][];
    dollarIds: number[];
    dotIds: number[];
    adv: number;
    dollarAdv: number;
    dotAdv: number;
    p0: SkPoint[];
    track: number;
  };
  mono: { ids: number[][]; w: number[]; font: SkFont };
  nCoins: number;
};

export type View = { A: number[]; theta: number; press: number; w: number; h: number };

// ---------------------------------------------------------------------------------------------------------------------
// resources, JS thread only

function hexRgb(hex: string) {
  const v = parseInt(hex.slice(1), 16);
  return [((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255];
}

function uniOf(fx: SkRuntimeEffect): Uni {
  const slot: Record<string, number> = {};
  for (let i = 0; i < fx.getUniformCount(); i++) slot[fx.getUniformName(i)] = fx.getUniform(i).slot;
  return { fx, u: new Array<number>(fx.getUniformFloatCount()).fill(0), slot };
}

function setU(u: Uni, name: string, v: number | number[]) {
  const s = u.slot[name];
  if (s === undefined) throw new Error(`uniform ${name} is not in the effect`);
  if (typeof v === 'number') u.u[s] = v;
  else for (let i = 0; i < v.length; i++) u.u[s + i] = v[i];
}

function compile(src: string, what: string) {
  const fx = Skia.RuntimeEffect.Make(src);
  if (!fx) throw new Error(`Coin Pile Collect: the ${what} shader did not compile`);
  return fx;
}

/** a superellipse-cornered rounded rectangle (iOS "continuous" corners), as a path */
export function squirclePath(x: number, y: number, w: number, h: number, r: number) {
  const n = 4.6;
  const e = 2 / n;
  const b = Skia.PathBuilder.Make();
  const seg = 18;
  const corner = (cx: number, cy: number, sx: number, sy: number, from: number, to: number) => {
    for (let i = 0; i <= seg; i++) {
      const t = from + ((to - from) * i) / seg;
      const u = Math.pow(Math.abs(Math.cos(t)), e);
      const v = Math.pow(Math.abs(Math.sin(t)), e);
      b.lineTo(cx + sx * r * u, cy + sy * r * v);
    }
  };
  const q = Math.PI / 2;
  b.moveTo(x + r, y);
  b.lineTo(x + w - r, y);
  corner(x + w - r, y + r, 1, -1, q, 0);
  b.lineTo(x + w, y + h - r);
  corner(x + w - r, y + h - r, 1, 1, 0, q);
  b.lineTo(x + r, y + h);
  corner(x + r, y + h - r, -1, 1, q, 0);
  b.lineTo(x, y + r);
  corner(x + r, y + r, -1, -1, 0, q);
  b.close();
  return b.detach();
}

function preRun(key: keyof typeof GLYPHS, font: SkFont, size: number, track: number): PreRun {
  const g = GLYPHS[key] as unknown as { ids: number[]; xs: number[]; w: number };
  const pts = g.ids.map((_, i) => Skia.Point((g.xs[i] * size) / 1000 + i * track * size, 0));
  return { ids: g.ids, pts, font, w: (g.w * size) / 1000 + (g.ids.length - 1) * track * size };
}

/** part of a baked run (glyphs from..to-1), its pen positions rebased to the first glyph */
function preRunSlice(key: keyof typeof GLYPHS, from: number, to: number, font: SkFont, size: number, track: number): PreRun {
  const g = GLYPHS[key] as unknown as { ids: number[]; xs: number[]; w: number };
  const x0 = g.xs[from];
  const ids = g.ids.slice(from, to);
  const pts = ids.map((_, i) => Skia.Point(((g.xs[from + i] - x0) * size) / 1000 + i * track * size, 0));
  const end = to < g.ids.length ? g.xs[to] : g.w;
  return { ids, pts, font, w: ((end - x0) * size) / 1000 + (ids.length - 1) * track * size };
}

/** the emboss atlas: three cells (the H mark, "$", "€") of COIN_EMBOSS_CELL px, alpha = ridge height */
export function makeAtlas(ebTf: SkTypeface) {
  const C = COIN_EMBOSS_CELL;
  const surf = Skia.Surface.MakeOffscreen(C * 3, C);
  if (!surf) throw new Error('Coin Pile Collect: no offscreen surface for the emboss atlas');
  const c = surf.getCanvas();
  const mk = (w: number) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(w);
    p.setStrokeJoin(StrokeJoin.Round);
    p.setColor(Skia.Color('#FFFFFF'));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.25, true));
    return p;
  };
  // cell 0: the H mark, one outline
  const pts: number[][] = [
    [46, 40],
    [76, 40],
    [76, 82],
    [116, 82],
    [116, 40],
    [146, 40],
    [146, 152],
    [116, 152],
    [116, 110],
    [76, 110],
    [76, 152],
    [46, 152],
  ];
  const pb = Skia.PathBuilder.Make();
  pts.forEach(([x, y], i) => (i ? pb.lineTo(x, y) : pb.moveTo(x, y)));
  pb.close();
  c.drawPath(pb.detach(), mk(5.2));
  const font = Skia.Font(ebTf, 168);
  const m = font.getMetrics();
  const cap = -m.ascent * 0.7;
  [
    [1, '$'],
    [2, '€'],
  ].forEach(([k, ch]) => {
    const ids = font.getGlyphIDs(ch as string);
    const wd = font.getGlyphWidths(ids)[0];
    c.drawText(ch as string, (k as number) * C + (C - wd) / 2, C / 2 + cap / 2, mk(4.6), font);
  });
  surf.flush();
  const snap = surf.makeImageSnapshot();
  // an offscreen surface's snapshot is a GPU texture of that context: a canvas cannot sample it
  return snap.makeNonTextureImage() ?? snap;
}

export function makeRes(opts: {
  ebTf: SkTypeface;
  sbTf: SkTypeface;
  p3: number;
  fs: number;
  upx: number;
  w: number;
  h: number;
  bottom: number;
}): Res {
  const { ebTf, sbTf, p3, fs, upx, w, h, bottom } = opts;
  const C = ref.color;
  const col: Record<string, SkColor> = {};
  (Object.keys(C) as (keyof typeof C)[]).forEach((k) => {
    col[k] = Skia.Color(C[k]);
  });

  const extra: Record<string, string> = {
    white28: 'rgba(255,255,255,0.28)',
    white20: 'rgba(255,255,255,0.20)',
    white22: 'rgba(255,255,255,0.22)',
    white0: 'rgba(255,255,255,0)',
    white: 'rgba(255,255,255,1)',
    edgeMid: 'rgba(255,255,255,0.72)',
    white80: 'rgba(255,255,255,0.8)',
    black18: 'rgba(0,0,0,0.18)',
    sheenBlue: 'rgba(120,140,255,0.16)',
    filmFill: 'rgba(235,245,255,0.07)',
    filmEdge: 'rgba(240,248,255,0.55)',
    gridBlue: 'rgba(150,170,255,0.10)',
    coinShadow: '#3A0C36',
    pillTop: '#F7C943',
    pillBottom: '#F1B92E',
    cyan0: 'rgba(63,233,255,0)',
    cyan1: 'rgba(63,233,255,1)',
    glowIn: 'rgba(63,233,255,0.28)',
    glowOut: 'rgba(63,233,255,0)',
  };
  Object.keys(extra).forEach((k) => {
    col[k] = Skia.Color(extra[k]);
  });

  const atlas = makeAtlas(ebTf);
  const emb = atlas.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.None);

  const R = comp.coin.r;
  const pile = uniOf(compile(PILE_SKSL, 'pile'));
  setU(pile, 'uCell', COIN_EMBOSS_CELL);
  setU(pile, 'uP3', p3);
  setU(pile, 'uPx', upx);
  setU(pile, 'uR', R);
  setU(pile, 'uH', comp.coin.h);
  setU(pile, 'uK', 1);
  setU(pile, 'uAlbA', hexRgb(C.coinFaceA));
  setU(pile, 'uAlbB', hexRgb(C.coinFaceB));
  setU(pile, 'uAlbRim', hexRgb(C.coinRim));
  setU(pile, 'uEnvKey', [0.72, 0.95, 1.0]);
  setU(pile, 'uEnvBounce', [0.14, 0.3, 0.8]);
  setU(pile, 'uEnvRoom', [0.01, 0.04, 0.06]);
  setU(pile, 'uBg', hexRgb(C.teal));
  setU(pile, 'uTile', [comp.teal.w, comp.teal.h]);
  setU(pile, 'uAmp', 1);

  const xc = comp.exchange;
  const xcoin = uniOf(compile(XCOIN_SKSL, 'exchange coin'));
  setU(xcoin, 'uCell', COIN_EMBOSS_CELL);
  setU(xcoin, 'uP3', p3);
  setU(xcoin, 'uPx', upx);
  setU(xcoin, 'uR', xc.r);
  setU(xcoin, 'uH', xc.h);
  setU(xcoin, 'uK', xc.r / R);
  setU(xcoin, 'uAlbA', hexRgb(C.pink));
  setU(xcoin, 'uAlbB', hexRgb(C.copper));
  setU(xcoin, 'uAlbRim', hexRgb(C.pinkRim));
  setU(xcoin, 'uEnvKey', [1.0, 0.86, 0.94]);
  setU(xcoin, 'uEnvBounce', [0.5, 0.2, 0.62]);
  setU(xcoin, 'uEnvRoom', [0.12, 0.04, 0.12]);
  setU(xcoin, 'uShadowOn', 0);

  const page = uniOf(compile(PAGE_SKSL, 'page'));
  setU(page, 'uP3', p3);
  setU(page, 'uSize', [w, h]);
  setU(page, 'uPage', hexRgb(C.page));
  setU(page, 'uGlow', hexRgb(C.pageGlow));
  setU(page, 'uGlow2', hexRgb(C.pageGlow2));
  const pageShader = page.fx.makeShader(page.u);

  const sz = (n: number) => n * fs;
  const ebFont = (n: number) => Skia.Font(ebTf, sz(n));
  const sbFont = (n: number) => Skia.Font(sbTf, sz(n));
  const T = ref.type;
  const fTitle = ebFont(T.title.size);
  const fTile = ebFont(T.tile.size);
  const fPremium = ebFont(PREMIUM.size);
  const fCopy = sbFont(T.copy.size);
  const fPill = sbFont(T.pill.size);
  const fSmall = sbFont(T.small.size);
  const fAmount = ebFont(T.amount.size);
  const fBank = ebFont(15);
  const fBankBig = ebFont(30);
  const fUrl = sbFont(7);
  const run: Record<string, PreRun> = {
    title1: preRun('title1', fTitle, sz(T.title.size), T.title.tracking),
    title2: preRun('title2', fTitle, sz(T.title.size), T.title.tracking),
    protect1: preRun('protect1', fTile, sz(T.tile.size), T.tile.tracking),
    protect2: preRun('protect2', fTile, sz(T.tile.size), T.tile.tracking),
    earned: preRun('earned', fCopy, sz(T.copy.size), T.copy.tracking),
    period: preRun('period', fCopy, sz(T.copy.size), T.copy.tracking),
    exchange1: preRun('exchange1', fSmall, sz(T.small.size), T.small.tracking),
    exchange2: preRun('exchange2', fSmall, sz(T.small.size), T.small.tracking),
    // "Get your / Premium / card" in display type, as the pin sets it under the exchange tile
    premium1: preRun('premium1', fPremium, sz(PREMIUM.size), PREMIUM.tracking),
    premium2: preRunSlice('premium2', 0, 7, fPremium, sz(PREMIUM.size), PREMIUM.tracking),
    premium3: preRunSlice('premium2', 8, 12, fPremium, sz(PREMIUM.size), PREMIUM.tracking),
    collect: preRun('collect', fPill, sz(T.pill.size), T.pill.tracking),
    halden: preRun('halden', fBank, sz(15), -0.01),
    haldenBig: preRun('halden', fBankBig, sz(30), -0.02),
    cardUrl: preRun('cardUrl', fUrl, sz(7), 0.06),
  };

  const dg = GLYPHS.digits;
  const adv = (dg.adv * sz(T.amount.size)) / 1000 + T.amount.tracking * sz(T.amount.size);
  const amount = {
    font: fAmount,
    size: sz(T.amount.size),
    digitIds: dg.ids.map((id) => [id]),
    dollarIds: [GLYPHS.dollar.id],
    dotIds: [GLYPHS.dot.id],
    adv,
    dollarAdv: (GLYPHS.dollar.adv * sz(T.amount.size)) / 1000 + T.amount.tracking * sz(T.amount.size),
    dotAdv: (GLYPHS.dot.adv * sz(T.amount.size)) / 1000 + T.amount.tracking * sz(T.amount.size),
    p0: [Skia.Point(0, 0)],
    track: T.amount.tracking,
  };

  const monoFont = ebFont(16);
  const mono = { ids: [] as number[][], w: [] as number[], font: monoFont };
  COPY.monograms.forEach((ch) => {
    const ids = monoFont.getGlyphIDs(ch);
    mono.ids.push(ids);
    mono.w.push(monoFont.getGlyphWidths(ids)[0]);
  });

  return {
    p3,
    upx,
    fs,
    pile,
    xcoin,
    page,
    emb,
    pageShader,
    col,
    tiles: {
      lime: squirclePath(comp.lime.x, comp.lime.y, comp.lime.w, comp.lime.h, ref.radius.tile),
      teal: squirclePath(0, 0, comp.teal.w, comp.teal.h, ref.radius.teal),
      magenta: squirclePath(comp.magenta.x, comp.magenta.y, comp.magenta.w, comp.magenta.h, ref.radius.tile),
      navy: squirclePath(comp.navy.x, comp.navy.y, comp.navy.w, Math.max(bottom + 48 - comp.navy.y, comp.navy.h), ref.radius.tile),
    },
    run,
    amount,
    mono,
    nCoins: comp.coin.count,
  };
}

// ---------------------------------------------------------------------------------------------------------------------
// drawing (worklets)

function solid(color: SkColor, a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  if (a < 1) p.setAlphaf(a);
  return p;
}

function drawRun(c: SkCanvas, r: PreRun, x: number, y: number, paint: ReturnType<typeof solid>) {
  'worklet';
  c.drawGlyphs(r.ids, r.pts, x, y, r.font, paint);
}

function grad(x0: number, y0: number, x1: number, y1: number, a: SkColor, b: SkColor) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), [a, b], null, TileMode.Clamp));
  return p;
}

function blurred(color: SkColor, a: number, sigma: number) {
  'worklet';
  const p = solid(color, a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function drawAvatars(c: SkCanvas, res: Res) {
  'worklet';
  const a = comp.avatars;
  const cy = a.y + a.r;
  const cols = [res.col.discC, res.col.discB, res.col.discA];
  for (let k = 0; k < 3; k++) {
    // drawn right to left: the left disc lies on top
    const cx = 402 - a.right - a.r - k * a.pitch;
    const col = cols[k];
    const ring = solid(res.col.page, 1);
    c.drawCircle(cx, cy, a.r + 2.5, ring);
    c.drawCircle(cx, cy, a.r, solid(col, 1));
    // a soft light from the upper left across the disc, so it is not a flat sticker
    const g = Skia.Paint();
    g.setAntiAlias(true);
    g.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(cx - a.r, cy - a.r),
        Skia.Point(cx + a.r, cy + a.r),
        [res.col.white28, res.col.white0, res.col.black18],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(cx, cy, a.r, g);
    const ink = solid(k === 0 ? res.col.ink : res.col.discInk, 1);
    const ids = res.mono.ids[2 - k];
    const wd = res.mono.w[2 - k];
    const shift = k === 0 ? 0 : (2 * a.r - a.pitch) / 2;
    c.drawGlyphs(ids, res.amount.p0, cx - wd / 2 + shift, cy + 5.6, res.mono.font, ink);
  }
}

function drawBack(c: SkCanvas, res: Res) {
  'worklet';
  const b = comp.back;
  const p = solid(res.col.ink, 1);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1.6);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  const path = Skia.PathBuilder.Make();
  path.moveTo(b.x + 20, b.y + 12);
  path.lineTo(b.x + 3, b.y + 12);
  path.moveTo(b.x + 10, b.y + 5);
  path.lineTo(b.x + 3, b.y + 12);
  path.lineTo(b.x + 10, b.y + 19);
  c.drawPath(path.detach(), p);
}

function drawTitle(c: SkCanvas, res: Res) {
  'worklet';
  const ink = solid(res.col.ink, 1);
  const cap = ref.type.title.size * 0.708 * res.fs;
  const base = comp.title.capTop + cap;
  drawRun(c, res.run.title1, comp.title.x, base, ink);
  drawRun(c, res.run.title2, comp.title.x, base + ref.type.title.lineHeight * res.fs, ink);
}

function drawLime(c: SkCanvas, res: Res) {
  'worklet';
  const t = comp.lime;
  c.drawPath(res.tiles.lime, solid(res.col.lime, 1));
  c.save();
  c.clipPath(res.tiles.lime, ClipOp.Intersect, true);
  c.translate(t.x, t.y);
  const ink = solid(res.col.limeInk, 1);
  const lh = ref.type.tile.lineHeight * res.fs;
  const first = 18 + ref.type.tile.size * 0.708 * res.fs;
  drawRun(c, res.run.protect1, 18, first, ink);
  drawRun(c, res.run.protect2, 18, first + lh, ink);

  // the card in its vacuum film: a flat picture, the tile's one quiet object
  const cx = 18;
  const cy = 88;
  const cw = 136;
  const ch = 80;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx, cy + 5, cw, ch), 11, 11), blurred(res.col.shadow, 0.42, 5));
  const card = Skia.RRectXY(Skia.XYWHRect(cx, cy, cw, ch), 10, 10);
  c.drawRRect(card, grad(cx, cy, cx + cw, cy + ch, res.col.cardTop, res.col.cardBottom));
  const sheen = Skia.Paint();
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(cx, cy),
      Skia.Point(cx + cw * 0.9, cy + ch),
      [res.col.white20, res.col.white0, res.col.sheenBlue],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(card, sheen);
  drawRun(c, res.run.halden, cx + 12, cy + 26, solid(res.col.ink, 0.82));
  drawRun(c, res.run.cardUrl, cx + cw - 12 - res.run.cardUrl.w, cy + ch - 10, solid(res.col.ink, 0.5));
  // the film: a bright edge, a hazy fill, two creases and a highlight in the upper left
  const film = Skia.RRectXY(Skia.XYWHRect(cx - 5, cy - 5, cw + 10, ch + 10), 15, 15);
  c.drawRRect(film, solid(res.col.filmFill, 1));
  const edge = solid(res.col.filmEdge, 1);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.1);
  c.drawRRect(film, edge);
  const crease = solid(res.col.white22, 1);
  crease.setStyle(PaintStyle.Stroke);
  crease.setStrokeWidth(0.9);
  crease.setStrokeCap(StrokeCap.Round);
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(cx - 3, cy + 14);
  pb.cubicTo(cx + 22, cy + 6, cx + 40, cy + 22, cx + 64, cy - 2);
  pb.moveTo(cx + cw + 3, cy + ch - 18);
  pb.cubicTo(cx + cw - 22, cy + ch - 8, cx + cw - 40, cy + ch - 24, cx + cw - 70, cy + ch + 3);
  pb.moveTo(cx + 6, cy + ch + 3);
  pb.cubicTo(cx + 18, cy + ch - 10, cx + 30, cy + ch - 4, cx + 44, cy + ch + 3);
  c.drawPath(pb.detach(), crease);
  const glint = blurred(res.col.white80, 0.35, 3);
  c.drawOval(Skia.XYWHRect(cx + 6, cy + 4, 34, 10), glint);
  c.restore();
}

function drawNavy(c: SkCanvas, res: Res) {
  'worklet';
  const t = comp.navy;
  c.drawPath(res.tiles.navy, grad(t.x, t.y, t.x + t.w * 0.3, t.y + 260, res.col.navyTop, res.col.navyBottom));
  c.save();
  c.clipPath(res.tiles.navy, ClipOp.Intersect, true);
  c.translate(t.x, t.y);
  // a fine diagonal grid, the card's guilloche, cut off by the tile
  const g = solid(res.col.gridBlue, 1);
  g.setStyle(PaintStyle.Stroke);
  g.setStrokeWidth(0.8);
  const gb = Skia.PathBuilder.Make();
  const gh = 640; // taller than any window: the tile bleeds off the bottom edge
  for (let i = -48; i < 22; i++) {
    gb.moveTo(i * 12, 0);
    gb.lineTo(i * 12 + gh * 0.9, gh);
    gb.moveTo(i * 12 + gh * 0.9, 0);
    gb.lineTo(i * 12, gh);
  }
  c.drawPath(gb.detach(), g);
  const w = solid(res.col.ink, 0.5);
  w.setStyle(PaintStyle.Stroke);
  w.setStrokeWidth(1);
  drawRun(c, res.run.haldenBig, 18, t.h - 22, w);
  c.restore();
}

function drawPremium(c: SkCanvas, res: Res) {
  'worklet';
  const ink = solid(res.col.ink, 1);
  const lh = PREMIUM.lineHeight * res.fs;
  const first = comp.premium.y + 6 + PREMIUM.size * 0.708 * res.fs;
  drawRun(c, res.run.premium1, comp.premium.x, first, ink);
  drawRun(c, res.run.premium2, comp.premium.x, first + lh, ink);
  drawRun(c, res.run.premium3, comp.premium.x, first + 2 * lh, ink);
}

/** the exchange coin's axis and face from its turn theta about the vertical axis, leaning a little toward the viewer */
function xcoinPose(theta: number, out: number[]) {
  'worklet';
  const s = Math.sin(theta);
  const k = Math.cos(theta);
  const lean = XCOIN.tilt;
  // (sinθ, -cosθ·sin lean, cosθ·cos lean), flipped when it faces away so the viewer always sees a cap
  let ax = s;
  let ay = -k * Math.sin(lean);
  const az = k * Math.cos(lean);
  let face = 0;
  if (az < 0) {
    ax = -ax;
    ay = -ay;
    face = 1;
  }
  out[0] = ax;
  out[1] = ay;
  out[2] = face;
}

function drawMagenta(c: SkCanvas, res: Res, theta: number) {
  'worklet';
  const t = comp.magenta;
  c.drawPath(res.tiles.magenta, grad(t.x, t.y, t.x + t.w * 0.25, t.y + t.h, res.col.magentaTop, res.col.magentaBottom));
  c.save();
  c.clipPath(res.tiles.magenta, ClipOp.Intersect, true);
  c.translate(t.x, t.y);
  const xc = comp.exchange;
  const pose = [0, 0, 0];
  xcoinPose(theta, pose);
  // the coin hovers: its soft shadow falls to the lower right and narrows as it turns edge-on
  const wide = Math.abs(Math.cos(theta)) * xc.r + xc.h;
  c.save();
  c.translate(xc.cx + 12, xc.cy + 16);
  c.drawOval(Skia.XYWHRect(-wide, -xc.r, wide * 2, xc.r * 2), blurred(res.col.coinShadow, 0.45, 9));
  c.restore();
  const u = res.xcoin.u.slice();
  const s = res.xcoin.slot;
  u[s.uCoin] = xc.cx;
  u[s.uCoin + 1] = xc.cy;
  u[s.uCoin + 2] = xc.h;
  u[s.uCoin + 3] = 1;
  u[s.uAxis] = pose[0];
  u[s.uAxis + 1] = pose[1];
  u[s.uAxis + 2] = 0;
  u[s.uAxis + 3] = pose[2];
  const p = Skia.Paint();
  p.setShader(res.xcoin.fx.makeShaderWithChildren(u, [res.emb]));
  c.drawRect(Skia.XYWHRect(0, 0, t.w, t.h), p);
  const ink = solid(res.col.ink, 1);
  const lh = ref.type.small.lineHeight * res.fs;
  const base2 = t.h - 12;
  drawRun(c, res.run.exchange1, 16, base2 - lh, ink);
  drawRun(c, res.run.exchange2, 16, base2, ink);
  c.restore();
}

function drawDigit(c: SkCanvas, res: Res, q: number, from: number, x: number, base: number, alpha: number, color: SkColor) {
  'worklet';
  if (alpha < 0.01) return;
  const am = res.amount;
  const fl = Math.floor(q);
  const frac = q - fl;
  // A digit rests, then changes over the end of its unit (from..1; from = 1 is a plain counter). The
  // higher places use 0.97, so they turn exactly while the place below rolls 9 → 0. The old glyph lifts,
  // blurs and fades over the first 60 % of the change, the new one rises in over the last 60 %.
  const roll = from >= 1 ? 0 : smooth(from, 1, frac);
  const d0 = ((fl % 10) + 10) % 10;
  const d1 = (d0 + 1) % 10;
  const H = am.size * 0.3;
  const a0 = alpha * (1 - smooth(0, 0.6, roll));
  const a1 = alpha * smooth(0.4, 1, roll);
  if (a0 > 0.003) {
    const pa = solid(color, a0);
    if (roll > 0.02) pa.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, roll * am.size * 0.05, true));
    c.drawGlyphs(am.digitIds[d0], am.p0, x, base - roll * H, am.font, pa);
  }
  if (a1 > 0.003) {
    const pb = solid(color, a1);
    if (roll < 0.98) pb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, (1 - roll) * am.size * 0.05, true));
    c.drawGlyphs(am.digitIds[d1], am.p0, x, base + (1 - roll) * H, am.font, pb);
  }
}

/** "$220.34" as an odometer: every digit chases its place of the shown value; the decimal point glides as digits appear */
function drawAmount(c: SkCanvas, res: Res, cents: number, x0: number, base: number, color: SkColor) {
  'worklet';
  const am = res.amount;
  const v = Math.max(cents, 0);
  const dollars = v / 100;
  const nd = 1 + smooth(9.5, 10, dollars) + smooth(99.5, 100, dollars) + smooth(999.5, 1000, dollars);
  const xDot = x0 + am.dollarAdv + nd * am.adv;
  const paint = solid(color, 1);
  c.drawGlyphs(am.dollarIds, am.p0, x0, base, am.font, paint);
  c.drawGlyphs(am.dotIds, am.p0, xDot, base, am.font, paint);
  for (let k = 0; k < 6; k++) {
    const place = Math.pow(10, k);
    let x = 0;
    let alpha = 1;
    if (k < 2) x = xDot + am.dotAdv + (1 - k) * am.adv;
    else x = xDot - (k - 1) * am.adv;
    if (k === 3) alpha = smooth(9.5, 10, dollars);
    if (k === 4) alpha = smooth(99.5, 100, dollars);
    if (k === 5) alpha = smooth(999.5, 1000, dollars);
    drawDigit(c, res, v / place, k === 0 ? 1 : k === 1 ? 0.7 : 0.97, x, base, alpha, color);
  }
}

/** the teal tile at the canvas origin: the pile, the copy, the pill */
export function drawHopper(c: SkCanvas, res: Res, A: number[]) {
  'worklet';
  const t = comp.teal;
  const pl = comp.pill;
  c.save();
  c.clipPath(res.tiles.teal, ClipOp.Intersect, true);

  // the pile
  const u = res.pile.u.slice();
  const s = res.pile.slot;
  const n = res.nCoins;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    const b = s.uCoins + 8 * i;
    u[b] = A[o + X];
    u[b + 1] = A[o + Y];
    u[b + 2] = A[o + Z];
    u[b + 3] = A[o + GLOW] + 2 * A[o + FACE];
    u[b + 4] = A[o + AX];
    u[b + 5] = A[o + AY];
    u[b + 6] = A[o + PHI];
    u[b + 7] = A[o + ALIVE];
  }
  u[s.uN] = n;
  const sp = Skia.Paint();
  sp.setShader(res.pile.fx.makeShaderWithChildren(u, [res.emb]));
  c.drawRect(Skia.XYWHRect(0, 0, t.w, t.h), sp);

  // the haze of the lit coins: one soft additive bloom per coin, over the pile (the shader itself adds only the contact light)
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    const g = A[o + GLOW];
    if (g < 0.06 || A[o + ALIVE] < 0.5) continue;
    const gr = comp.coin.r * 2.3;
    const bp = Skia.Paint();
    bp.setShader(
      Skia.Shader.MakeRadialGradient(Skia.Point(A[o + X], A[o + Y]), gr, [res.col.glowIn, res.col.glowOut], null, TileMode.Clamp),
    );
    bp.setBlendMode(BlendMode.Plus);
    bp.setAlphaf(Math.min(g, 1));
    c.drawCircle(A[o + X], A[o + Y], gr, bp);
  }

  const press = A[CTL + C_PRESS];
  const bulge = A[CTL + C_BULGE];
  const pillA = A[CTL + C_PILLA];
  const lit = A[CTL + C_LIT];
  const flow = A[CTL + C_FLOW];
  const cents = A[CTL + C_CENTS];

  // the copy
  const white = solid(res.col.ink, 1);
  drawRun(c, res.run.earned, comp.copy.x, comp.copy.earnedBase, white);
  const kLit = clamp01(lit);
  const ac = res.col.amber;
  const al = res.col.amberLit;
  drawAmount(
    c,
    res,
    cents,
    comp.copy.x,
    comp.copy.amountBase,
    Float32Array.of(mix(ac[0], al[0], kLit), mix(ac[1], al[1], kLit), mix(ac[2], al[2], kLit), 1),
  );
  drawRun(c, res.run.period, comp.copy.x, comp.copy.periodBase, white);

  // the pill: it is pressed in, bulges as each coin goes in, dims when the tile is empty
  const cx = pl.x + pl.w / 2;
  const cy = pl.y + pl.h / 2;
  const sx = 1 - (1 - PILL.pressScale) * press;
  const sy = sx * (1 + bulge);
  c.save();
  c.translate(cx, cy);
  c.scale(sx, sy);
  c.translate(-cx, -cy);
  const rr = Skia.RRectXY(Skia.XYWHRect(pl.x, pl.y, pl.w, pl.h), pl.h / 2, pl.h / 2);
  const sh = Skia.RRectXY(Skia.XYWHRect(pl.x + 2, pl.y + 6, pl.w - 4, pl.h - 2), pl.h / 2, pl.h / 2);
  c.drawRRect(sh, blurred(res.col.shadow, 0.5 * pillA * (1 - 0.6 * press), 7));
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, pl.y),
      Skia.Point(0, pl.y + pl.h),
      [res.col.pillTop, res.col.pillBottom],
      null,
      TileMode.Clamp,
    ),
  );
  body.setAlphaf(pillA);
  c.drawRRect(rr, body);
  // the cyan line on the upper rim while coins pass
  if (flow > 0.01 && lit > 0.01) {
    const lp = Skia.Paint();
    lp.setAntiAlias(true);
    lp.setStyle(PaintStyle.Stroke);
    lp.setStrokeWidth(1);
    lp.setStrokeCap(StrokeCap.Round);
    lp.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(pl.x + 22, 0),
        Skia.Point(pl.x + pl.w - 22, 0),
        [res.col.cyan0, res.col.cyan1, res.col.cyan0],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    lp.setAlphaf(PILL.lineMax * flow * lit);
    c.drawLine(pl.x + 22, pl.y + 0.5, pl.x + pl.w - 22, pl.y + 0.5, lp);
  }
  const lab = res.run.collect;
  const ink = solid(res.col.pillInk, 1);
  ink.setAlphaf(pillA);
  drawRun(c, lab, cx - lab.w / 2, cy + (ref.type.pill.size * 0.708 * res.fs) / 2, ink);
  c.restore();
  c.restore();
}

/** the whole window. L = layout (window size, S, ox, oy); the sim array A and the exchange coin's turn */
export function drawScreen(
  c: SkCanvas,
  res: Res,
  L: { W: number; H: number; S: number; ox: number; oy: number },
  A: number[],
  theta: number,
) {
  'worklet';
  const page = Skia.Paint();
  page.setShader(res.pageShader);
  c.drawRect(Skia.XYWHRect(0, 0, L.W, L.H), page);
  c.save();
  c.translate(L.ox, L.oy);
  c.scale(L.S, L.S);
  drawAvatars(c, res);
  drawBack(c, res);
  drawTitle(c, res);
  drawLime(c, res);
  drawMagenta(c, res, theta);
  drawNavy(c, res);
  drawPremium(c, res);
  c.save();
  c.translate(comp.teal.x, comp.teal.y);
  drawHopper(c, res, A);
  c.restore();
  c.restore();
  // Scroll edge: the page itself (same shader, so no band or colour step) fades in over the last
  // 96 pt, so the bento reads as a list that continues under the home indicator, not a cut. It never
  // starts above the last line of copy (short phones get a shorter fade).
  const copyBottom = L.oy + (comp.premium.y + 6 + PREMIUM.size * 0.708 * res.fs + 2 * PREMIUM.lineHeight * res.fs + 8) * L.S;
  const fadeH = Math.max(16 * L.S, Math.min(96 * L.S, L.H - copyBottom));
  const edge = Skia.Paint();
  edge.setShader(
    Skia.Shader.MakeBlend(
      BlendMode.DstIn,
      res.pageShader,
      Skia.Shader.MakeLinearGradient(
        Skia.Point(0, L.H - fadeH),
        Skia.Point(0, L.H),
        [res.col.white0, res.col.edgeMid, res.col.white],
        [0, 0.55, 1],
        TileMode.Clamp,
      ),
    ),
  );
  c.drawRect(Skia.XYWHRect(0, L.H - fadeH, L.W, fadeH), edge);
}
