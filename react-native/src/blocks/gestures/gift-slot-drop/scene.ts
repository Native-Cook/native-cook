/**
 * The whole screen as one Skia picture, drawn from the slot's shared values: the page (an image made at mount), the
 * header's glass, the title (out-then-in), the carousel in perspective, the glass shelf with its slot, the card going
 * in, the activated gift rising out and turning over to its code, the steps and the button, the demo's finger.
 *
 * Back to front: page, header, title, neighbours, shelf, centre card (clipped at the slot), slot lip, gift, details.
 */
import {
  BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode, processTransform3d,
  type SkCanvas, type SkFont, type SkImage, type SkPath, type SkRuntimeEffect, type SkShader, type SkTypeface,
} from '@shopify/react-native-skia';

import type { Backdrop } from './backdrop';
import { GLASS_EFFECT } from './glass-shader';
import { CLOSE, DRAG, EASE_IN_OUT, EASE_OUT, REDUCED_MS, SEQ, TITLE } from './motion';
import { COLOR, GIFTS, LAYOUT, TINT, TYPE, type Gift, type Layout, type TypeRole } from './theme';
import { PHASE, milestones } from './use-gift-slot';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semibold: SkTypeface; mono: SkTypeface };
export type Fonts = Record<TypeRole, SkFont>;
export type SceneCtx = {
  lay: Layout;
  fonts: Fonts;
  glass: SkRuntimeEffect;
  frost: SkShader;
  page: SkImage;
  pageScale: number;
  marks: Record<Gift['mark'], SkPath>;
};

/** The emblems on a 16 × 16 box: a bowl, a pair of rails, a leaf, a coffee bean. */
const MARK_SVG: Record<Gift['mark'], string> = {
  bowl: 'M1 6.5H15C15 10.9 11.9 14 8 14S1 10.9 1 6.5ZM3.2 4.2H12.8',
  rails: 'M4 15L7.5 1M8.5 15L12 1M3.9 11.5H10.9M4.8 8H11.8M5.7 4.5H12.7',
  leaf: 'M8 1C13.2 4.2 14 10.2 8 15C2 10.2 2.8 4.2 8 1ZM8 4V15',
  bean: 'M8 1.2C12.2 1.2 14.4 4.6 14.4 8S12.2 14.8 8 14.8 1.6 11.4 1.6 8 3.8 1.2 8 1.2ZM9.6 2.4C6.2 5.2 9.8 10.8 6.4 13.6',
};

export function makeScene(lay: Layout, faces: Faces, back: Backdrop): SceneCtx {
  const fonts = {} as Fonts;
  for (const role of Object.keys(TYPE) as TypeRole[]) fonts[role] = Skia.Font(faces[TYPE[role].face], TYPE[role].size);
  const m = Skia.Matrix();
  m.scale(1 / back.frostScale, 1 / back.frostScale);
  const frost = back.frosted.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, m);
  const marks = {} as Record<Gift['mark'], SkPath>;
  for (const k of Object.keys(MARK_SVG) as Gift['mark'][]) marks[k] = Skia.Path.MakeFromSVGString(MARK_SVG[k])!;
  return { lay, fonts, glass: GLASS_EFFECT, frost, page: back.sharp, pageScale: back.sharpScale, marks };
}

// ——— Small helpers (declared before everything that calls them) ———

function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** CSS cubic-bezier(x1, y1, x2, y2) at u (Newton on x). */
function bezier(x1: number, y1: number, x2: number, y2: number, u: number): number {
  'worklet';
  const k = clamp01(u);
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let t = k;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * t + bx) * t + cx) * t - k;
    const dx = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    t -= x / dx;
  }
  t = clamp01(t);
  return ((ay * t + by) * t + cy) * t;
}

function easeOut(u: number): number {
  'worklet';
  return bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3], u);
}

function easeInOut(u: number): number {
  'worklet';
  return bezier(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3], u);
}

/** A light spring's settle (0 → 1 with a small overshoot), as a closed form of time in ms. */
function settle(ms: number, duration: number): number {
  'worklet';
  if (ms <= 0) return 0;
  const t = ms / duration;
  if (t >= 1.6) return 1;
  return 1 - Math.exp(-5.2 * t) * Math.cos(4.4 * t);
}

function advance(font: SkFont, s: string): number {
  'worklet';
  if (!s) return 0;
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

function paintOf(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(p.getAlphaf() * Math.max(alpha, 0));
  return p;
}

function strokeOf(color: string, width: number, alpha = 1) {
  'worklet';
  const p = paintOf(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

/** Text at x (align 0 left, 1 centre, 2 right), with extra tracking per character when `track` is set. */
function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, alpha = 1, align = 0, track = 0) {
  'worklet';
  if (alpha <= 0.004 || !s) return;
  const w = advance(font, s) + track * (s.length - 1);
  let left = align === 0 ? x : align === 1 ? x - w / 2 : x - w;
  const p = paintOf(color, alpha);
  if (track === 0) {
    c.drawText(s, left, y, p, font);
    return;
  }
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    c.drawText(ch, left, y, p, font);
    left += advance(font, ch) + track;
  }
}

/** A piece of glass over the frosted page. */
function glass(c: SkCanvas, ctx: SceneCtx, x: number, y: number, w: number, h: number, r: number, tint: readonly number[], thick = 0, alpha = 1) {
  'worklet';
  if (alpha <= 0.004) return;
  const p = Skia.Paint();
  p.setShader(ctx.glass.makeShaderWithChildren([x, y, w, h, r, tint[0], tint[1], tint[2], tint[3], thick, 0, 0, 0, alpha], [ctx.frost]));
  c.drawRect(Skia.XYWHRect(x - 1, y - 1, w + 2, h + 2), p);
}

function mark(c: SkCanvas, ctx: SceneCtx, g: Gift, x: number, y: number, size: number, color: string, alpha: number, width = 1.6) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(size / 16, size / 16);
  c.drawPath(ctx.marks[g.mark], strokeOf(color, (width * 16) / size, alpha));
  c.restore();
}

// ——— A gift card ———

/** The front of a gift card, centred on the origin, `w` × `h`. Everything is drawn in points of the small card. */
function cardFront(c: SkCanvas, ctx: SceneCtx, g: Gift, a: number) {
  'worklet';
  const { fonts } = ctx;
  const W = LAYOUT.cardW;
  const H = LAYOUT.cardH;
  const x0 = -W / 2;
  const y0 = -H / 2;
  const rr = Skia.RRectXY(Skia.XYWHRect(x0, y0, W, H), LAYOUT.cardR, LAYOUT.cardR);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x0 + W, y: y0 + H }, [Skia.Color(g.bg), Skia.Color(g.bg2)], null, TileMode.Clamp));
  fill.setAlphaf(a);
  c.drawRRect(rr, fill);
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  // The emblem again, large and quiet, running off the lower right corner.
  mark(c, ctx, g, x0 + W - 128, y0 + H - 124, 168, g.ink, 0.07 * a, 2.2);
  // A sheen across the upper left, as light falls on a printed card.
  const sheen = Skia.Paint();
  const s0 = Skia.Color('#FFFFFF');
  s0[3] = 0;
  const s1 = Skia.Color('#FFFFFF');
  s1[3] = 0.09 * a;
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x0 + W * 0.7, y: y0 + H * 0.55 }, [s1, s0], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(x0, y0, W, H), sheen);
  c.restore();
  // Rim: a hairline brighter along the top.
  const rim = strokeOf('#FFFFFF', 1, 0.16 * a);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 0.5, y0 + 0.5, W - 1, H - 1), LAYOUT.cardR - 0.5, LAYOUT.cardR - 0.5), rim);

  const L = x0 + 20;
  mark(c, ctx, g, L, y0 + 22, 16, g.ink, a);
  text(c, g.shop, L + 24, y0 + 36, fonts.mark, g.ink, a);
  // The tag, top right: a thin capsule.
  const tw = advance(fonts.tag, 'GIFT') + 3 * 1.2 + 14;
  const tr = Skia.RRectXY(Skia.XYWHRect(x0 + W - 20 - tw, y0 + 22, tw, 18), 9, 9);
  c.drawRRect(tr, strokeOf(g.ink, 1, 0.4 * a));
  text(c, 'GIFT', x0 + W - 20 - tw / 2, y0 + 35, fonts.tag, g.ink, 0.85 * a, 1, 1.2);

  text(c, g.offer, L - 2, y0 + 150, fonts.offer, g.ink, a);
  text(c, g.line, L, y0 + 176, fonts.offerLine, g.ink, a);
  text(c, g.cap, L, y0 + 196, fonts.cap, g.sub, a);

  c.drawRect(Skia.XYWHRect(L, y0 + H - 46, W - 40, 1), paintOf(g.ink, 0.16 * a));
  text(c, 'WELCOME GIFT', L, y0 + H - 22, fonts.tag, g.sub, a, 0, 1.2);
  text(c, 'ORRA PAY', x0 + W - 20, y0 + H - 22, fonts.tag, g.sub, a, 2, 1.2);
}

/** The back of the activated gift: the code, ready to use. Same size as the front. */
function cardBack(c: SkCanvas, ctx: SceneCtx, g: Gift, a: number) {
  'worklet';
  const { fonts } = ctx;
  const W = LAYOUT.cardW;
  const H = LAYOUT.cardH;
  const x0 = -W / 2;
  const y0 = -H / 2;
  const rr = Skia.RRectXY(Skia.XYWHRect(x0, y0, W, H), LAYOUT.cardR, LAYOUT.cardR);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x0, y: y0 + H }, [Skia.Color(g.bg2), Skia.Color(g.bg)], null, TileMode.Clamp));
  fill.setAlphaf(a);
  c.drawRRect(rr, fill);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 0.5, y0 + 0.5, W - 1, H - 1), LAYOUT.cardR - 0.5, LAYOUT.cardR - 0.5), strokeOf('#FFFFFF', 1, 0.16 * a));
  const L = x0 + 20;
  mark(c, ctx, g, L, y0 + 22, 16, g.ink, a);
  text(c, g.shop, L + 24, y0 + 36, fonts.mark, g.ink, a);
  // A check in a ring, top right: activated.
  const cx = x0 + W - 29;
  const cy = y0 + 31;
  c.drawCircle(cx, cy, 9, strokeOf(g.ink, 1.2, 0.5 * a));
  const tick = Skia.PathBuilder.Make().moveTo(cx - 4, cy).lineTo(cx - 1.2, cy + 3).lineTo(cx + 4.2, cy - 3).build();
  c.drawPath(tick, strokeOf(g.ink, 1.6, a));

  text(c, 'YOUR CODE', L, y0 + 104, fonts.codeLabel, g.sub, a, 0, 1.4);
  // The code in an inset well, so it reads as something you could lift off the card.
  const well = Skia.RRectXY(Skia.XYWHRect(L, y0 + 116, W - 40, 52), 12, 12);
  c.drawRRect(well, paintOf('#000000', 0.18 * a));
  c.drawRRect(well, strokeOf(g.ink, 1, 0.14 * a));
  const codeFont = fonts.code;
  // Fit the code to the well: mono at 22 pt, smaller if it would touch the sides.
  const cw = advance(codeFont, g.code);
  const room = W - 40 - 28;
  c.save();
  const k = cw > room ? room / cw : 1;
  c.translate(0, y0 + 150);
  c.scale(k, k);
  text(c, g.code, 0, 0, codeFont, g.ink, a, 1);
  c.restore();
  // The offer in one line, narrowed to the card if it would run past it ("Free flat white for a week").
  const offer = `${g.offer} ${g.line}`;
  const ow = advance(fonts.offerLine, offer);
  c.save();
  c.translate(L, y0 + 202);
  if (ow > W - 40) c.scale((W - 40) / ow, (W - 40) / ow);
  text(c, offer, 0, 0, fonts.offerLine, g.ink, a);
  c.restore();
  text(c, g.cap, L, y0 + 222, fonts.cap, g.sub, a);
  c.drawRect(Skia.XYWHRect(L, y0 + H - 46, W - 40, 1), paintOf(g.ink, 0.16 * a));
  text(c, 'VALID TO 27 NOV', L, y0 + H - 22, fonts.tag, g.sub, a, 0, 1.2);
  text(c, 'ACTIVE', x0 + W - 20, y0 + H - 22, fonts.tag, g.ink, a, 2, 1.2);
}

/** Card g at (x, y) seen in perspective: turned `turnY` about its vertical axis, leaned back `leanX`, scaled. */
function drawCard(c: SkCanvas, ctx: SceneCtx, g: Gift, x: number, y: number, scale: number, turnY: number, leanX: number, a: number, backSide = false, dim = 0) {
  'worklet';
  if (a <= 0.004) return;
  c.save();
  c.concat(processTransform3d([
    { translateX: x }, { translateY: y }, { perspective: 900 }, { rotateY: turnY }, { rotateX: leanX }, { scale },
  ]));
  if (backSide) cardBack(c, ctx, g, a);
  else cardFront(c, ctx, g, a);
  // Neighbours sit in the dark, not behind frosted plastic: dimmed, never see-through.
  if (dim > 0.004) {
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-LAYOUT.cardW / 2, -LAYOUT.cardH / 2, LAYOUT.cardW, LAYOUT.cardH), LAYOUT.cardR, LAYOUT.cardR), paintOf(COLOR.page, dim * a));
  }
  c.restore();
}

/** A soft shadow under a card, on the page. */
function cardShadow(c: SkCanvas, x: number, y: number, w: number, h: number, a: number) {
  'worklet';
  if (a <= 0.01) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color('#000000'));
  p.setAlphaf(0.5 * a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 18, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - w / 2 + 10, y - h / 2 + 22, w - 20, h - 10), 24, 24), p);
}

// ——— Screen ———

export type Frame = {
  now: number;
  offset: number;
  pullX: number;
  pullY: number;
  lift: number;
  phase: number;
  armed: boolean;
  titleAt: number;
  titleWas: number;
  releasedAt: number;
  releasedFrom: { x: number; y: number };
  closeAt: number;
  selected: number;
  ghost: { x: number; y: number; o: number };
  reduced: boolean;
};

const TITLES: [string, string][] = [
  ['Choose your', 'welcome gift'],
  ['Let go to', 'activate it'],
  ['Activating', 'your gift…'],
  ['Your gift', 'is ready'],
];

function titleOf(ph: number, armed: boolean): number {
  'worklet';
  if (ph === PHASE.inserting || ph === PHASE.reading) return 2;
  if (ph === PHASE.offer) return 3;
  return armed ? 1 : 0;
}

function drawTitle(c: SkCanvas, ctx: SceneCtx, f: Frame) {
  'worklet';
  const { lay, fonts } = ctx;
  const cur = titleOf(f.phase, f.armed);
  const e = f.reduced ? 1e6 : f.now - f.titleAt;
  const outU = clamp01(e / TITLE.out);
  const inU = easeOut(clamp01((e - TITLE.out) / TITLE.in));
  const y1 = lay.top + LAYOUT.titleY;
  const draw = (k: number, a: number, dy: number) => {
    const col = k === 1 ? COLOR.amberInk : COLOR.ink;
    text(c, TITLES[k][0], lay.cx, y1 + dy, fonts.title, col, a, 1);
    text(c, TITLES[k][1], lay.cx, y1 + LAYOUT.titleLine + dy, fonts.title, col, a, 1);
  };
  if (outU < 1 && f.titleWas !== cur) draw(f.titleWas, 1 - outU, -TITLE.rise * outU);
  draw(cur, outU < 1 && f.titleWas !== cur ? 0 : inU, TITLE.rise * (1 - inU));
}

/** Header: a glass back button (a close button over the gift), the wallet's name, a glass Terms pill. */
function drawHeader(c: SkCanvas, ctx: SceneCtx, offerA: number) {
  'worklet';
  const { lay, fonts } = ctx;
  const y = lay.top;
  glass(c, ctx, LAYOUT.edge, y, 44, 44, 22, TINT.chrome);
  const bx = LAYOUT.edge + 22;
  const by = y + 22;
  const chev = Skia.PathBuilder.Make().moveTo(bx + 3, by - 7).lineTo(bx - 4, by).lineTo(bx + 3, by + 7).build();
  c.drawPath(chev, strokeOf(COLOR.ink, 2, 1 - offerA));
  const cross = Skia.PathBuilder.Make().moveTo(bx - 6, by - 6).lineTo(bx + 6, by + 6).moveTo(bx + 6, by - 6).lineTo(bx - 6, by + 6).build();
  c.drawPath(cross, strokeOf(COLOR.ink, 2, offerA));
  const tw = advance(fonts.nav, 'Terms') + 32;
  glass(c, ctx, lay.width - LAYOUT.edge - tw, y + 4, tw, 36, 18, TINT.chrome);
  text(c, 'Terms', lay.width - LAYOUT.edge - tw / 2, y + 27, fonts.nav, COLOR.ink, 1, 1);
  text(c, 'ORRA PAY  ·  NEW MEMBER', lay.cx, y + LAYOUT.eyebrowY, fonts.eyebrow, COLOR.faint, 1, 1, 1.4);
}

/** The glass shelf and the dark slot in it, lit from inside by `glow` (0..1). */
function drawShelf(c: SkCanvas, ctx: SceneCtx, glow: number, a: number, dy: number) {
  'worklet';
  if (a <= 0.004) return;
  const { lay } = ctx;
  const sy = lay.slotY + dy;
  glass(c, ctx, lay.cx - LAYOUT.shelfW / 2, sy - LAYOUT.shelfH / 2, LAYOUT.shelfW, LAYOUT.shelfH, LAYOUT.shelfH / 2, TINT.shelf, 0.4, a);
  // Light out of the slot: a warm bloom above it, stronger as the slot works.
  const bloom = Skia.Paint();
  const c0 = Skia.Color(COLOR.amber);
  c0[3] = (0.22 + 0.4 * glow) * a;
  const c1 = Skia.Color(COLOR.amber);
  c1[3] = 0;
  const m = Skia.Matrix();
  m.translate(lay.cx, sy);
  m.scale(1, 0.32);
  m.translate(-lay.cx, -sy);
  bloom.setShader(Skia.Shader.MakeRadialGradient({ x: lay.cx, y: sy }, LAYOUT.slotW * 0.7, [c0, c1], null, TileMode.Clamp, m));
  bloom.setBlendMode(BlendMode.Plus);
  c.drawRect(Skia.XYWHRect(lay.cx - LAYOUT.slotW, sy - 60, LAYOUT.slotW * 2, 120), bloom);
  // The opening: a deep recess with light at its far wall.
  const sw = LAYOUT.slotW;
  const sh = LAYOUT.slotH;
  const r = Skia.RRectXY(Skia.XYWHRect(lay.cx - sw / 2, sy - sh / 2, sw, sh), sh / 2, sh / 2);
  const deep = Skia.Paint();
  deep.setAntiAlias(true);
  const inner = Skia.Color(COLOR.amber);
  inner[3] = (0.35 + 0.65 * glow) * a;
  const dark = Skia.Color(COLOR.slotDeep);
  dark[3] = a;
  deep.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: sy - sh / 2 }, { x: 0, y: sy + sh / 2 }, [dark, dark, inner], [0, 0.45, 1], TileMode.Clamp));
  c.drawRRect(r, deep);
}

/** The slot's near lip, over whatever is going in: a hairline of light on the shelf's edge. */
function drawLip(c: SkCanvas, ctx: SceneCtx, a: number, dy: number) {
  'worklet';
  if (a <= 0.004) return;
  const { lay } = ctx;
  const sy = lay.slotY + dy;
  const sw = LAYOUT.slotW;
  const sh = LAYOUT.slotH;
  // The shelf in front of the opening hides what has gone in.
  glass(c, ctx, lay.cx - sw / 2 - 6, sy, sw + 12, sh / 2 + 3, 2, TINT.shelf, 0, a);
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  const l0 = Skia.Color(COLOR.lip);
  l0[3] = 0;
  const l1 = Skia.Color(COLOR.lip);
  l1[3] = 0.9 * a;
  lip.setShader(Skia.Shader.MakeLinearGradient({ x: lay.cx - sw / 2, y: 0 }, { x: lay.cx + sw / 2, y: 0 }, [l0, l1, l1, l0], [0, 0.2, 0.8, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(lay.cx - sw / 2, sy - 0.5, sw, 1.2), lip);
}

export function drawScene(c: SkCanvas, ctx: SceneCtx, f: Frame) {
  'worklet';
  const { lay, fonts } = ctx;
  const R = f.reduced;
  c.save();
  c.scale(1 / ctx.pageScale, 1 / ctx.pageScale);
  c.drawImage(ctx.page, 0, 0);
  c.restore();

  const m = milestones(R);
  const since = f.releasedAt >= 0 ? f.now - f.releasedAt : -1;
  const closing = f.closeAt >= 0 ? clamp01((f.now - f.closeAt) / (R ? REDUCED_MS : CLOSE.out)) : 0;
  const backIn = f.closeAt >= 0 ? clamp01((f.now - f.closeAt - (R ? 0 : CLOSE.out)) / (R ? REDUCED_MS : CLOSE.back)) : 0;
  const ph = f.phase;
  const inSlot = ph === PHASE.inserting || ph === PHASE.reading || ph === PHASE.offer || ph === PHASE.closing;

  // The gift's presence (0 before it rises, 1 once up, out again on close) and the carousel's.
  const offerA = ph === PHASE.offer ? 1 : ph === PHASE.closing ? 1 - closing : 0;
  let carouselA = 1;
  if (ph === PHASE.inserting) carouselA = R ? 0 : 1 - clamp01(since / 300);
  else if (ph === PHASE.reading || ph === PHASE.offer) carouselA = 0;
  else if (ph === PHASE.closing) carouselA = R ? backIn : easeOut(backIn);
  const shelfOut = ph === PHASE.offer ? clamp01((since - m.risen) / 260) : ph === PHASE.closing ? 1 - backIn : 0;

  drawHeader(c, ctx, offerA);
  drawTitle(c, ctx, f);

  // Neighbours first (and the centre card when it is not being pulled), back to front by distance.
  const centre = f.selected;
  const back = R ? 0 : 12 * (1 - carouselA);
  for (let pass = 2; pass >= 0; pass--) {
    for (let i = 0; i < GIFTS.length; i++) {
      const rel = i - f.offset;
      const d = Math.abs(rel);
      if (d > 1.7 || Math.round(Math.min(d, 2)) !== pass) continue;
      if (i === centre && (ph === PHASE.pulling || inSlot) && ph !== PHASE.closing) continue;
      const k = Math.min(d, 1);
      const x = lay.cx + rel * lay.spacing;
      const s = (1 - (1 - LAYOUT.sideScale) * k) * (i === centre ? f.lift : 1);
      const turn = (R ? 0 : -Math.max(-1, Math.min(1, rel)) * LAYOUT.sideTurn) * (Math.PI / 180);
      const a = i === centre && ph === PHASE.closing ? carouselA : i === centre ? 1 : carouselA;
      const y = lay.cardY + (i === centre && ph === PHASE.closing ? back : 0);
      if (k < 0.5) cardShadow(c, x, y, LAYOUT.cardW * s, LAYOUT.cardH * s, a * (1 - k * 2));
      drawCard(c, ctx, GIFTS[i], x, y, s, turn, 0, a, false, 0.5 * k);
    }
  }

  // Hint between the cards and the shelf, with a chevron that drifts down.
  const hintA = (ph === PHASE.choosing || ph === PHASE.swiping ? 1 : 0) * (ph === PHASE.closing ? 0 : 1);
  if (hintA > 0) {
    text(c, 'Drag a gift into the slot', lay.cx, lay.hintY, fonts.hint, COLOR.mute, hintA, 1);
  }
  // Which of the four, and the fine print: with the cards, gone while a gift is out.
  const dotsA = carouselA * (ph === PHASE.inserting ? 0 : 1);
  // The selected dot stretches into a short bar; the row stays centred as it does.
  let total = 0;
  for (let i = 0; i < GIFTS.length; i++) total += 6 + 12 * clamp01(1 - Math.abs(i - f.offset)) + (i > 0 ? 6 : 0);
  let dx = lay.cx - total / 2;
  for (let i = 0; i < GIFTS.length; i++) {
    const on = clamp01(1 - Math.abs(i - f.offset));
    const w = 6 + 12 * on;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(dx, lay.dotsY - 3, w, 6), 3, 3), paintOf(COLOR.ink, (0.22 + 0.68 * on) * dotsA));
    dx += w + 6;
  }
  text(c, 'One welcome gift per member  ·  valid 30 days', lay.cx, lay.printY, fonts.cap, COLOR.faint, dotsA, 1);

  // The shelf, the card going in (clipped at the slot's line), the near lip over it.
  let glow = 0;
  if (ph === PHASE.pulling) glow = clamp01(f.pullY / DRAG.commit) * 0.6 + (f.armed ? 0.25 : 0);
  if (ph === PHASE.inserting) glow = 0.85;
  if (ph === PHASE.reading) glow = R ? 0.8 : 0.6 + 0.4 * Math.sin(((since - m.sunk) / SEQ.read) * Math.PI * 2 - Math.PI / 2) * 0.5 + 0.2;
  if (ph === PHASE.offer) glow = 1 - clamp01((since - m.read) / 600) * 0.6;
  const shelfA = 1 - shelfOut;
  const shelfDy = R ? 0 : 16 * shelfOut;
  drawShelf(c, ctx, glow, shelfA, shelfDy);

  const g = GIFTS[centre];
  if (ph === PHASE.pulling || ph === PHASE.inserting) {
    let py = f.pullY;
    let px = f.pullX;
    if (ph === PHASE.inserting) {
      const u = R ? 1 : easeInOut(clamp01(since / SEQ.sink));
      const target = lay.slotY - lay.cardY + LAYOUT.cardH * 0.7;
      py = f.releasedFrom.y + (target - f.releasedFrom.y) * u;
      px = f.releasedFrom.x * (1 - u);
    }
    const p = clamp01(py / DRAG.full);
    const s = (1 - (1 - DRAG.minScale) * p) * f.lift;
    const lean = (R ? 0 : DRAG.leanDeg * p) * (Math.PI / 180);
    c.save();
    c.clipRect(Skia.XYWHRect(0, 0, lay.width, lay.slotY), ClipOp.Intersect, true);
    cardShadow(c, lay.cx + px, lay.cardY + py, LAYOUT.cardW * s, LAYOUT.cardH * s, 1 - p);
    drawCard(c, ctx, g, lay.cx + px, lay.cardY + py, s, 0, lean, 1);
    c.restore();
  }
  drawLip(c, ctx, shelfA, shelfDy);

  // The activated gift: up out of the slot, then over to its code.
  if (ph === PHASE.offer || (ph === PHASE.closing && closing < 1)) {
    const riseU = R ? 1 : clamp01((since - m.read) / SEQ.rise);
    const rise = R ? 1 : settle(since - m.read, SEQ.rise);
    const gs = LAYOUT.giftW / LAYOUT.cardW;
    const startY = lay.slotY + LAYOUT.giftH * 0.55;
    const y = startY + (lay.giftY - startY) * rise;
    const flipU = R ? 1 : easeInOut(clamp01((since - m.risen - SEQ.pause) / SEQ.flip));
    const angle = Math.PI * flipU;
    const backSide = flipU > 0.5;
    const a = R ? (ph === PHASE.closing ? 1 - closing : clamp01((since - m.read) / REDUCED_MS)) : 1 - closing;
    const scale = gs * (0.9 + 0.1 * easeOut(riseU)) * (ph === PHASE.closing && !R ? 1 - 0.04 * closing : 1);
    c.save();
    if (riseU < 1) c.clipRect(Skia.XYWHRect(0, 0, lay.width, lay.slotY + shelfDy), ClipOp.Intersect, true);
    cardShadow(c, lay.cx, y + (R ? 0 : 10 * closing), LAYOUT.giftW, LAYOUT.giftH, a * clamp01(riseU * 2 - 1));
    drawCard(c, ctx, g, lay.cx, y + (R ? 0 : 10 * closing), scale, backSide ? angle - Math.PI : angle, 0, a, backSide);
    c.restore();
    if (riseU < 1) drawLip(c, ctx, shelfA, shelfDy);

    // How to use it, and the button.
    const shown = (k: number) => {
      const u = R ? clamp01((since - m.turned) / REDUCED_MS) : easeOut(clamp01((since - m.turned - k * SEQ.stagger) / SEQ.detailsIn));
      return u * (1 - closing);
    };
    const sy = lay.stepsY;
    for (let k = 0; k < 2; k++) {
      const u = shown(k);
      const dy = R ? 0 : 8 * (1 - u);
      text(c, `0${k + 1}`, LAYOUT.edge + 24, sy + k * 32 + dy, fonts.stepNum, COLOR.amberInk, u);
      text(c, g.steps[k], LAYOUT.edge + 56, sy + k * 32 + dy, fonts.step, COLOR.ink, u);
    }
    const bu = shown(2);
    if (bu > 0.004) {
      const bw = lay.width - 2 * LAYOUT.edge;
      const by = lay.buttonY + (R ? 0 : 10 * (1 - bu));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(LAYOUT.edge, by, bw, LAYOUT.buttonH), LAYOUT.buttonH / 2, LAYOUT.buttonH / 2), paintOf(COLOR.button, bu));
      text(c, g.action, lay.cx, by + LAYOUT.buttonH / 2 + 6, fonts.button, COLOR.buttonInk, bu, 1);
    }
  }

  // The demo's finger.
  if (f.ghost.o > 0.01) {
    c.drawCircle(f.ghost.x, f.ghost.y, 20, paintOf('#FFFFFF', 0.14 * f.ghost.o));
    c.drawCircle(f.ghost.x, f.ghost.y, 19.5, strokeOf('#FFFFFF', 1, 0.4 * f.ghost.o));
  }
}
