// Raffle Drum Spin — the 2D page, drawn with Skia as UI-thread worklets: the violet page and its
// confetti, title, chips, ticket cards with their ball icons, Play and the winner's caption. The 3D
// drum, balls and foil are RdsDrumGpu's; drawFallbackDrum() stands in for them without WebGPU.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  PointMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  type SkPicture,
  type SkPoint,
  type SkRRect,
  type SkRuntimeEffect,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';

import { BALL_R, easeInOut, easeOut, type Frame, LOOP, mix, project, rand, seg, T, TICKET } from './rds-motion';
import { C, COPY, L, rgba } from './rds-theme';

export type Geo = { w: number; h: number; sx: number; sy: number; top: number };
export type Label = { font: SkFont; width: number };

export type Res = {
  bg: SkRuntimeEffect;
  ball: SkRuntimeEffect;
  rays: SkRuntimeEffect;
  morph: SkRuntimeEffect;
  foil: SkRuntimeEffect;
  light: SkRuntimeEffect;
  glass: SkRuntimeEffect;
  liquid: SkRuntimeEffect;
  ticket: SkImage;
  back: SkImage;
  /** The page's confetti slips, built once (paint with its gradient and blur, and its shape). */
  slips: { paint: SkPaint; rect: SkRRect }[];
  /** Everything on the page that never moves, recorded once (see recordBase). */
  base: SkPicture | null;
  star: SkPath;
  text: {
    title: Label;
    sub: SkFont;
    chip: SkFont;
    fav: SkFont;
    card: SkFont;
    play: SkFont;
    caption: SkFont;
    entry: SkFont;
    stub: SkFont;
    stubBig: SkFont;
  };
};

/** Each card's copy, broken into the lines of the reference. */
export const CARD_LINES = [
  ['A single chance to', 'enter the Raffle and', 'compete for prizes'],
  ['Double your chances', 'to win a prize with', 'two Raffle Entries'],
  ['Triple your odds', 'with three Raffle', 'Entries in the drum'],
];

function paint(color: number, a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(rgba(color, a)));
  return p;
}

function widthOf(font: SkFont, str: string) {
  'worklet';
  const ids = font.getGlyphIDs(str);
  const ws = font.getGlyphWidths(ids);
  let s = 0;
  for (let i = 0; i < ws.length; i++) s += ws[i];
  return s;
}

function centred(c: SkCanvas, font: SkFont, str: string, cx: number, base: number, p: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawText(str, cx - widthOf(font, str) / 2, base, p, font);
}

/** Centred text with tracking (em of the font's size) between glyphs, never after the last. */
function centredTracked(c: SkCanvas, font: SkFont, str: string, cx: number, base: number, p: SkPaint, em: number) {
  'worklet';
  const ids = font.getGlyphIDs(str);
  const ws = font.getGlyphWidths(ids);
  const gap = em * font.getSize();
  let total = gap * (ws.length - 1);
  for (let i = 0; i < ws.length; i++) total += ws[i];
  let x = cx - total / 2;
  for (let i = 0; i < str.length; i++) {
    c.drawText(str[i], x, base, p, font);
    x += ws[i] + gap;
  }
}

function sparkle(c: SkCanvas, R: Res, x: number, y: number, size: number, a: number) {
  'worklet';
  if (a <= 0.01) return;
  c.save();
  c.translate(x, y);
  c.scale(size / 20, size / 20);
  const glow = paint(0xb9a6ff, 0.5 * a);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawPath(R.star, glow);
  c.drawPath(R.star, paint(0xf3efff, a));
  c.restore();
}

// ── page ──────────────────────────────────────────────────────────────────────────────────────

function drawBackground(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const p = Skia.Paint();
  p.setShader(R.bg.makeShader([g.w, g.h, L.drumX * g.sx, (L.drumY - 30) * g.sy, f.glow, f.t]));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), p);
}

/** The reference's confetti: flat violet slips, softly drifting (the burst's foil is 3D, on the GPU). */
const SLIPS: [number, number, number, number, number][] = [
  // x, y, angle, length, blur
  [112, 137, -62, 24, 0],
  [219, 156, -32, 18, 0],
  [92, 240, 12, 26, 0],
  [318, 222, 28, 26, 0],
  [20, 365, -58, 46, 6],
  [370, 330, 35, 34, 5],
  [87, 459, 22, 30, 1],
  [298, 323, -64, 14, 0],
  [175, 283, -58, 10, 0],
  [345, 448, 70, 16, 0],
  [394, 640, 10, 38, 7],
  [362, 236, 22, 20, 0],
];

function drawConfetti(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const k = (f.t / LOOP) * Math.PI * 2;
  for (let i = 0; i < SLIPS.length; i++) {
    const [x, y, ang] = SLIPS[i];
    // The slips float on their own: the drum's spin only stirs the air round them (they drift
    // a little away from it and flutter faster); nothing orbits with the cage.
    const ox = x - L.drumX;
    const oy = y - L.drumY;
    const dist = Math.max(40, Math.hypot(ox, oy));
    const gust = f.speed * (60 / dist) * 9;
    const dx = 5 * Math.sin(k * 2 + i) + (ox / dist) * gust + 3 * f.speed * Math.sin(f.t * 11 + i * 1.7);
    const dy = 4 * Math.sin(k * 3 + i * 2.3) + (oy / dist) * gust;
    const turn = 8 * Math.sin(k * 2 + i * 0.7) + 14 * f.speed * Math.sin(f.t * 7 + i);
    const flip = 0.55 + 0.45 * Math.cos(k * (1 + (i % 3)) + i + f.speed * Math.sin(f.t * 13 + i));
    c.save();
    c.translate((x + dx) * g.sx, (y + dy) * g.sy);
    c.rotate(ang + turn, 0, 0);
    c.scale(1, flip);
    const s = R.slips[i];
    c.drawRRect(s.rect, s.paint);
    c.restore();
  }
}

function drawShadow(c: SkCanvas, g: Geo) {
  'worklet';
  const p = paint(0x05010f, 0.55);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14 * g.sx, true));
  c.drawOval(Skia.XYWHRect(116 * g.sx, 288 * g.sy, 184 * g.sx, 22 * g.sy), p);
  // Where the base actually meets the page: a tight, darker core.
  const core = paint(0x020008, 0.6);
  core.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4 * g.sx, true));
  c.drawOval(Skia.XYWHRect(136 * g.sx, 295 * g.sy, 144 * g.sx, 8 * g.sy), core);
}

function drawTitle(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = 1 - f.dim;
  const t = R.text.title;
  const x = 201 * g.sx - t.width / 2;
  const base = L.titleBase * g.sy;
  const size = t.font.getSize();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: base - size * 0.75 },
      { x: 0, y: base },
      [Skia.Color(rgba(0xffffff, a)), Skia.Color(rgba(0xe7e3ef, a)), Skia.Color(rgba(0xb9b3c6, a))],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  const glow = paint(0xb9a2ff, 0.28 * a);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10 * g.sx, true));
  c.drawText(COPY.title, x, base, glow, t.font);
  c.drawText(COPY.title, x, base, p, t.font);
  const sub = paint(0x9b93ad, a);
  centred(c, R.text.sub, COPY.sub1, 201 * g.sx, L.sub1Y * g.sy, sub);
  centred(c, R.text.sub, COPY.sub2, 201 * g.sx, L.sub2Y * g.sy, sub);
}

/** The title's two sparkles twinkle, so they are drawn every frame. */
function drawSparkles(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = 1 - f.dim;
  const tw = (k: number) => 0.7 + 0.3 * Math.sin((f.t / LOOP) * Math.PI * 4 + k);
  sparkle(c, R, L.sparkL[0] * g.sx, L.sparkL[1] * g.sy, 22 * g.sx * tw(0), 0.85 * a);
  sparkle(c, R, L.sparkR[0] * g.sx, L.sparkR[1] * g.sy, 16 * g.sx * tw(2), 0.7 * a);
}

/** A pill round `str`, its left edge at x (px); returns its width. */
function chip(c: SkCanvas, R: Res, g: Geo, str: string, x: number, a: number) {
  'worklet';
  const y = L.chipsY * g.sy;
  const h = 20 * g.sx;
  const w = widthOf(R.text.chip, str) + 16 * g.sx;
  const line = paint(C.chipLine, 0.9 * a);
  line.setStyle(1);
  line.setStrokeWidth(1 * g.sx);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5 * g.sx, y - h / 2, w - g.sx, h), h / 2, h / 2), line);
  c.drawText(str, x + 8 * g.sx, y + 4 * g.sx, paint(0xb4adc4, a), R.text.chip);
  return w;
}

/** One line, centred: 15+ · $1+ ✦ Favorite Games ✦ unlimited (11 pt, so it reads). */
function drawChips(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = 1 - f.dim;
  const y = L.chipsY * g.sy;
  const gap = 6 * g.sx;
  const star = 30 * g.sx; // a sparkle with a short line each side
  const pill = (str: string) => widthOf(R.text.chip, str) + 16 * g.sx;
  const fav = widthOf(R.text.fav, COPY.fav);
  const total = pill(COPY.chips[0]) + gap + pill(COPY.chips[1]) + star + fav + star + pill(COPY.chips[2]);
  let x = 201 * g.sx - total / 2;
  x += chip(c, R, g, COPY.chips[0], x, a) + gap;
  x += chip(c, R, g, COPY.chips[1], x, a);
  const s1 = x + star / 2;
  x += star;
  c.drawText(COPY.fav, x, y + 4 * g.sx, paint(0xd9d3e6, a), R.text.fav);
  x += fav;
  const s2 = x + star / 2;
  x += star;
  chip(c, R, g, COPY.chips[2], x, a);
  for (const sx of [s1, s2]) {
    const line = paint(0x8a83a0, 0.7 * a);
    line.setStrokeWidth(0.9 * g.sx);
    c.drawLine(sx - 10 * g.sx, y, sx + 10 * g.sx, y, line);
    sparkle(c, R, sx, y, 10 * g.sx, 0.85 * a);
  }
}

function ticketPath(x: number, y: number, w: number, h: number, r: number, nw: number, nd: number) {
  'worklet';
  const cx = x + w / 2;
  const b = Skia.PathBuilder.Make();
  b.moveTo(x + r, y);
  b.lineTo(cx - nw / 2 - nd, y);
  b.cubicTo(cx - nw / 2 + nd * 0.6, y, cx - nw / 2 + nd * 0.4, y + nd, cx - nw / 2 + nd * 2, y + nd);
  b.lineTo(cx + nw / 2 - nd * 2, y + nd);
  b.cubicTo(cx + nw / 2 - nd * 0.4, y + nd, cx + nw / 2 - nd * 0.6, y, cx + nw / 2 + nd, y);
  b.lineTo(x + w - r, y);
  b.quadTo(x + w, y, x + w, y + r);
  b.lineTo(x + w, y + h - r);
  b.quadTo(x + w, y + h, x + w - r, y + h);
  b.lineTo(cx + nw / 2 + nd, y + h);
  b.cubicTo(cx + nw / 2 - nd * 0.6, y + h, cx + nw / 2 - nd * 0.4, y + h - nd, cx + nw / 2 - nd * 2, y + h - nd);
  b.lineTo(cx - nw / 2 + nd * 2, y + h - nd);
  b.cubicTo(cx - nw / 2 + nd * 0.4, y + h - nd, cx - nw / 2 + nd * 0.6, y + h, cx - nw / 2 - nd, y + h);
  b.lineTo(x + r, y + h);
  b.quadTo(x, y + h, x, y + h - r);
  b.lineTo(x, y + r);
  b.quadTo(x, y, x + r, y);
  b.close();
  return b.build();
}

export function ballDisc(c: SkCanvas, R: Res, x: number, y: number, r: number, rot: [number, number, number], a: number, blurX = 0) {
  'worklet';
  if (a <= 0.01 || r < 0.5) return;
  const p = Skia.Paint();
  p.setShader(R.ball.makeShader([x, y, r, rot[0], rot[1], rot[2], a, blurX]));
  const ex = r * (1 + blurX) + 2;
  c.drawRect(Skia.XYWHRect(x - ex, y - r - 2, ex * 2, r * 2 + 4), p);
}

function selAmount(f: Frame, card: number) {
  'worklet';
  if (card === f.to && card === f.from) return 1;
  if (card === f.to) return f.selK;
  if (card === f.from) return 1 - f.selK;
  return 0;
}

function drawCard(c: SkCanvas, R: Res, g: Geo, f: Frame, card: number) {
  'worklet';
  const sel = selAmount(f, card);
  const a = 1 - f.dim * 0.85;
  const x = (L.cardX0 + card * (L.cardW + L.cardGap) + f.strip) * g.sx;
  const y = (L.cardY - 2 * sel) * g.sy;
  const w = L.cardW * g.sx;
  const h = L.cardH * g.sy;
  if (x > g.w + 4 || x + w < -4) return;
  const path = ticketPath(x, y, w, h, L.cardR * g.sx, L.notchW * g.sx, L.notchD * g.sx);
  c.save();
  const pk = 1 - 0.04 * f.cardPress[card];
  if (pk !== 1) {
    c.translate(x + w / 2, y + h / 2);
    c.scale(pk, pk);
    c.translate(-(x + w / 2), -(y + h / 2));
  }
  // The ticket's gold dust arriving: the chosen card's outline lights up gold.
  if (card === f.to && f.absorb > 0.01) {
    const gl = paint(C.gold, 0.85 * f.absorb);
    gl.setStyle(1);
    gl.setStrokeWidth(2 * g.sx);
    gl.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Solid, 6 * g.sx, true));
    c.drawPath(path, gl);
  }
  // Selection glow outside the card.
  if (sel > 0.01) {
    const glow = paint(C.violet, 0.55 * sel * a);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Outer, 12 * g.sx, true));
    c.drawPath(path, glow);
  }
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y },
      { x, y: y + h },
      [Skia.Color(rgba(0x2a1d4a, 0.92 * a)), Skia.Color(rgba(0x150e26, 0.92 * a)), Skia.Color(rgba(0x0b0814, 0.95 * a))],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(path, fill);
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  // Light behind the balls: violet rays on the chosen card, a soft bloom on the others.
  const by = y + L.ballDY * g.sy;
  const bx = x + w / 2;
  const rp = Skia.Paint();
  rp.setShader(R.rays.makeShader([bx, by, (0.25 + 0.75 * sel) * a, f.t]));
  c.drawRect(Skia.XYWHRect(x, y, w, h * 0.62), rp);
  const balls =
    card === 0
      ? [[84, 28]]
      : card === 1
        ? [
            [55, 24],
            [107, 24],
          ]
        : [
            [42, 20],
            [83, 20],
            [124, 20],
          ];
  for (let k = 0; k < balls.length; k++) {
    const [dx, r] = balls[k];
    const bloom = paint(0x8b62ff, 0.45 * a);
    bloom.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12 * g.sx, true));
    c.drawCircle(x + dx * g.sx, by, r * g.sx * 1.05, bloom);
  }
  // Glass sheen on the top half.
  const sheen = Skia.Paint();
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x: x + w * 0.4, y: y + h * 0.5 }, [Skia.Color(rgba(0xffffff, 0.07 * a)), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(x, y, w, h), sheen);
  c.restore();
  // The balls themselves (hidden while they are in play on the GPU).
  for (let k = 0; k < balls.length; k++) {
    const [dx, r] = balls[k];
    const spinK = (f.t / LOOP) * Math.PI * 2;
    const ia = f.iconA[card][k];
    const isc = mix(0.92, 1, ia);
    ballDisc(c, R, x + dx * g.sx, by, r * g.sx * isc, [0.4 + k * 1.3, 0.6 + 0.15 * Math.sin(spinK + k), 0.3 * k], ia * a);
  }
  // Edge: thin when idle, a 2 pt violet line when chosen, and a light running round it as it is chosen.
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(1);
  edge.setStrokeWidth(mix(1, 2.2, sel) * g.sx);
  edge.setColor(Skia.Color(rgba(sel > 0.5 ? C.violet : C.cardEdge, mix(0.9, 1, sel) * a)));
  if (sel > 0.02 && sel < 0.98) {
    const e0 = paint(C.cardEdge, (1 - sel) * a);
    e0.setStyle(1);
    e0.setStrokeWidth(1 * g.sx);
    c.drawPath(path, e0);
    edge.setColor(Skia.Color(rgba(C.violet, sel * a)));
  }
  c.drawPath(path, edge);
  if (card === f.to && f.sweep > 0 && f.sweep < 1) {
    const it = Skia.ContourMeasureIter(path, false, 1);
    const m = it.next();
    if (m) {
      const len = m.length();
      const s0 = (f.sweep * 1.0 + 0.62) * len;
      const seg2 = (from: number, to: number) => {
        const p0 = ((from % len) + len) % len;
        const p1 = ((to % len) + len) % len;
        if (p1 > p0) return m.getSegment(p0, p1, true);
        const b = Skia.PathBuilder.Make();
        b.addPath(m.getSegment(p0, len, true));
        b.addPath(m.getSegment(0, p1, true));
        return b.build();
      };
      const glint = seg2(s0 - 90 * g.sx, s0);
      const gp = paint(0xd9ccff, Math.sin(Math.PI * f.sweep) * a);
      gp.setStyle(1);
      gp.setStrokeWidth(2.6 * g.sx);
      gp.setStrokeCap(1);
      gp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Solid, 3 * g.sx, true));
      c.drawPath(glint, gp);
    }
  }
  // Check mark.
  const kx = x + L.checkDX * g.sx;
  const ky = y + L.checkDY * g.sy;
  const kr = (L.checkD / 2) * g.sx;
  const ring = paint(0x3a3150, (1 - sel) * a);
  ring.setStyle(1);
  ring.setStrokeWidth(1.2 * g.sx);
  c.drawCircle(kx, ky, kr, paint(0x1d1730, 0.9 * a));
  c.drawCircle(kx, ky, kr, ring);
  const pop = card === f.to ? Math.max(0, f.check) * sel : sel;
  const gold = f.entered[card];
  if (pop > 0.01 || gold > 0.01) {
    c.save();
    c.translate(kx, ky);
    const ps = mix(0.6, 1, Math.min(1, Math.max(pop, gold))) * (pop > 1 ? pop : 1);
    c.scale(ps, ps);
    c.drawCircle(0, 0, kr, paint(C.violet, a * Math.min(1, Math.max(pop, gold))));
    if (gold > 0.01) {
      // The torn stub has landed: the check is gold now.
      const gl = paint(C.gold, 0.6 * gold * a);
      gl.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 * g.sx, true));
      c.drawCircle(0, 0, kr * 1.1, gl);
      const gp = Skia.Paint();
      gp.setAntiAlias(true);
      gp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -kr }, { x: 0, y: kr }, [Skia.Color(rgba(0xfff0c2, gold * a)), Skia.Color(rgba(0xe0a83e, gold * a))], null, TileMode.Clamp));
      c.drawCircle(0, 0, kr, gp);
    }
    const tick = paint(gold > 0.5 ? 0x3a2206 : 0xffffff, a * Math.min(1, Math.max(pop, gold)));
    tick.setStyle(1);
    tick.setStrokeWidth(2.2 * g.sx);
    tick.setStrokeCap(1);
    tick.setStrokeJoin(1);
    const t = Skia.PathBuilder.Make()
      .moveTo(-5 * g.sx, 0.5 * g.sx)
      .lineTo(-1.5 * g.sx, 4 * g.sx)
      .lineTo(5.5 * g.sx, -4 * g.sx)
      .build();
    c.drawPath(t, tick);
    c.restore();
  }
  // Copy.
  const lines = CARD_LINES[card];
  const tp = paint(mix(0, 1, sel) > 0.5 ? 0xc7c0d6 : 0xa9a2ba, a);
  for (let i = 0; i < lines.length; i++) c.drawText(lines[i], x + L.textDX * g.sx, y + (L.textDY + 16 + i * L.textLine) * g.sy, tp, R.text.card);
  c.restore();
}

function drawPlay(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const x = L.playX * g.sx;
  const y = L.playY * g.sy;
  const w = L.playW * g.sx;
  const h = L.playH * g.sy;
  const k = 1 - 0.04 * Math.max(f.press, f.claimPress);
  c.save();
  c.translate(x + w / 2, y + h / 2);
  c.scale(k, k);
  c.translate(-(x + w / 2), -(y + h / 2));
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), h / 2, h / 2);
  const glow = paint(0x6a1cd6, 0.4 + 0.3 * f.speed);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16 * g.sx, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 20, y + 10, w - 40, h - 6), h / 2, h / 2), glow);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + h }, [Skia.Color(rgba(0x7426e6, 1)), Skia.Color(rgba(C.playTop, 1)), Skia.Color(rgba(C.playLow, 1))], [0, 0.4, 1], TileMode.Clamp),
  );
  c.drawRRect(rr, fill);
  if (f.fill > 0.001 && f.drain < 0.999) {
    // "Claim ticket": liquid gold rises in the button with a sloshing meniscus; on Claim it drains
    // away from the finger's touch point.
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    const lq = Skia.Paint();
    lq.setShader(R.liquid.makeShader([x, y, w, h, f.fill, f.drain, x + f.drainAt * g.sx, y + h / 2, f.t]));
    c.drawRect(Skia.XYWHRect(x, y, w, h), lq);
    // While it waits, a glint runs across it now and then.
    if (f.claimGlint > 0 && f.claimGlint < 1 && f.drain === 0) {
      const gx = mix(x - 60 * g.sx, x + w + 60 * g.sx, f.claimGlint);
      const sp = Skia.Paint();
      sp.setBlendMode(BlendMode.Plus);
      sp.setShader(Skia.Shader.MakeLinearGradient({ x: gx - 40 * g.sx, y }, { x: gx + 40 * g.sx, y: y + h }, [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,250,235,0.55)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
      c.drawRRect(rr, sp);
    }
    c.restore();
  }
  const rim = paint(0xb79cff, 0.55);
  rim.setStyle(1);
  rim.setStrokeWidth(1 * g.sx);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, w - 1, h - 1), h / 2, h / 2), rim);
  if (f.shimmer > 0 && f.shimmer < 1) {
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    const sx = mix(x - 80 * g.sx, x + w + 80 * g.sx, f.shimmer);
    const sp = Skia.Paint();
    sp.setBlendMode(BlendMode.Plus);
    sp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: sx - 60 * g.sx, y },
        { x: sx + 60 * g.sx, y: y + h },
        [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(220,205,255,0.5)'), Skia.Color('rgba(255,255,255,0)')],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawRect(Skia.XYWHRect(x, y, w, h), sp);
    c.restore();
  }
  // Labels swap with a 2 pt blur and a 5 pt lift.
  const label = (str: string, a: number, color: number, lift: number) => {
    if (a <= 0.01) return;
    const lp = paint(color, a);
    const blur = 2 * (1 - a) * g.sx;
    if (blur > 0.2) lp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    centred(c, R.text.play, str, x + w / 2, y + h / 2 + 6.2 * g.sx + lift, lp);
  };
  // One label leaves before the other arrives, so two words never sit on top of each other.
  const out = seg(f.claimK, 0, 0.45);
  const into = seg(f.claimK, 0.55, 1);
  label(COPY.play, 1 - out, 0xffffff, -5 * out * g.sx);
  label(COPY.claim, into, 0x3b2205, 5 * (1 - into) * g.sx);
  c.restore();
}

function drawClose(c: SkCanvas, g: Geo) {
  'worklet';
  const x = L.closeX * g.sx;
  const y = L.closeY * g.sy;
  const r = (L.closeD / 2) * g.sx;
  c.drawCircle(x, y, r, paint(0x120a22, 0.75));
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1.1 * g.sx);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x, y: y - r }, { x, y: y + r }, [Skia.Color('rgba(200,185,240,0.55)'), Skia.Color('rgba(120,100,170,0.15)')], null, TileMode.Clamp));
  c.drawCircle(x, y, r - 0.5, rim);
  const xp = paint(0xf4f1fb, 1);
  xp.setStyle(1);
  xp.setStrokeWidth(2.4 * g.sx);
  xp.setStrokeCap(1);
  const s = 8 * g.sx;
  c.drawLine(x - s, y - s, x + s, y + s, xp);
  c.drawLine(x + s, y - s, x - s, y + s, xp);
}

function drawDim(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.dim <= 0.004) return;
  const p = Skia.Paint();
  p.setShader(
    Skia.Shader.MakeRadialGradient({ x: L.revealX * g.sx, y: L.revealY * g.sy }, g.h * 0.75, [Skia.Color(rgba(0x0a0418, 0.2 * f.dim)), Skia.Color(rgba(0x04010b, 0.85 * f.dim))], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), p);
}

/** Without WebGPU: the drum as projected chrome strokes and the balls as Skia discs. */
function drawFallbackDrum(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const sx = g.sx;
  const stroke = (pts: { x: number; y: number }[], w: number, color: number, a: number) => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < pts.length; i++) b.lineTo(pts[i].x, pts[i].y);
    const p = paint(color, a);
    p.setStyle(1);
    p.setStrokeWidth(w);
    p.setStrokeCap(1);
    c.drawPath(b.build(), p);
  };
  const rot = (x: number, y: number, z: number) => {
    const s = Math.sin(f.theta);
    const k = Math.cos(f.theta);
    return [x, k * y - s * z, s * y + k * z] as const;
  };
  // Stand and base.
  for (const s of [-1, 1]) {
    const pts = [];
    for (let i = 0; i <= 16; i++) {
      const t = i / 16;
      const u = 1 - t;
      const x = s * (1.18 * u * u * u + 1.36 * 3 * u * u * t + 1.34 * 3 * u * t * t + 1.02 * t * t * t);
      const y = -0.42 * 3 * u * u * t - 1.02 * 3 * u * t * t - 1.3 * t * t * t;
      pts.push(project(x, y, 0, sx));
    }
    stroke(pts, 7 * sx, 0x8d8a99, 1);
    stroke(pts, 2.5 * sx, 0xe9e7f0, 0.9);
  }
  const corners = [project(-1.15, -1.3, 0.72, sx), project(1.15, -1.3, 0.72, sx), project(1.15, -1.3, -0.72, sx), project(-1.15, -1.3, -0.72, sx)];
  const top = Skia.PathBuilder.Make().moveTo(corners[0].x, corners[0].y).lineTo(corners[1].x, corners[1].y).lineTo(corners[2].x, corners[2].y).lineTo(corners[3].x, corners[3].y).close().build();
  c.drawPath(top, paint(0x3b3846, 1));
  // Rings: back halves first, darker; balls; front halves.
  const halves = (front: boolean) => {
    for (let k = 0; k < 7; k++) {
      const a = (k / 7) * Math.PI;
      let run: { x: number; y: number }[] = [];
      for (let i = 0; i <= 72; i++) {
        const u = (i / 72) * Math.PI * 2;
        const [x, y, z] = rot(Math.cos(u), Math.sin(u) * Math.cos(a), Math.sin(u) * Math.sin(a));
        const p = project(x, y, z, sx);
        const isFront = p.z < 6;
        if (isFront === front) run.push(p);
        else if (run.length > 1) {
          stroke(run, 3.2 * sx, front ? 0x9c99aa : 0x4a4660, 1);
          stroke(run, 1.1 * sx, front ? 0xffffff : 0x8d88a6, 0.85);
          run = [];
        } else run = [];
      }
      if (run.length > 1) {
        stroke(run, 3.2 * sx, front ? 0x9c99aa : 0x4a4660, 1);
        stroke(run, 1.1 * sx, front ? 0xffffff : 0x8d88a6, 0.85);
      }
    }
  };
  halves(false);
  const axle = [project(-1.24, 0, 0, sx), project(1.36, 0, 0, sx)];
  stroke(axle, 4 * sx, 0xb9b6c4, 1);
  for (const b of f.balls) if (b.a > 0 && b.z > 3.5) ballDisc(c, R, b.x, b.y, b.r, [b.rx, b.ry, b.rz], b.a);
  halves(true);
  const [hx, hy, hz] = rot(1.36, 0.3, 0);
  const h0 = project(hx, hy, hz, sx);
  const h1 = project(1.66, hy, hz, sx);
  stroke([project(1.36, 0, 0, sx), h0], 3.5 * sx, 0xc9c6d4, 1);
  stroke([h0, h1], 8 * sx, 0x151418, 1);
  for (const b of f.balls) if (b.a > 0 && b.z <= 3.5) ballDisc(c, R, b.x, b.y, b.r, [b.rx, b.ry, b.rz], b.a);
  void BALL_R;
}

/** The slips' paints and shapes: made once on the JS thread, never per frame. */
export function makeSlips(sx: number) {
  return SLIPS.map(([, , , len, blur]) => {
    const w = len * sx;
    const h = w * 0.42;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -h }, { x: 0, y: h }, [Skia.Color(rgba(C.confettiHi, 0.95)), Skia.Color(rgba(C.confetti, 0.9))], null, TileMode.Clamp));
    if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur * sx, true));
    return { paint: p, rect: Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), 2 * sx, 2 * sx) };
  });
}

/**
 * What never moves (the background shader, the drum's shadow, the title and copy, the chips, the
 * close button), recorded once on the JS thread. The frame then replays it as one picture instead
 * of running a full-screen shader and a dozen text and blur draws every frame. The dim and the
 * rack focus darken it from above, so it needs no alpha of its own.
 */
export function recordBase(R: Res, g: Geo, still: Frame) {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const f = { ...still, glow: 0, dim: 0, t: 0 };
  drawBackground(c, R, g, f);
  drawShadow(c, g);
  drawTitle(c, R, g, f);
  drawChips(c, R, g, f);
  drawClose(c, g);
  return recorder.finishRecordingAsPicture();
}

/** The light behind the drum pulses while it spins: an additive radial glow over the base. */
function drawGlow(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const k = f.glow * 0.5;
  if (k <= 0.004) return;
  const p = Skia.Paint();
  p.setBlendMode(BlendMode.Plus);
  const gx = L.drumX * g.sx;
  const gy = (L.drumY - 30) * g.sy;
  const gr = g.w * 0.62;
  p.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: gx, y: gy },
      gr,
      [Skia.Color(rgba(0x331f6b, k)), Skia.Color(rgba(0x331f6b, k * 0.55)), Skia.Color(rgba(0x331f6b, k * 0.16)), Skia.Color(rgba(0x331f6b, 0))],
      [0, 0.35, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(Math.max(0, gx - gr), Math.max(0, gy - gr), Math.min(g.w, 2 * gr), 2 * gr), p);
}

export function drawScene(c: SkCanvas, R: Res, g: Geo, f: Frame, gpu: boolean) {
  'worklet';
  if (R.base) c.drawPicture(R.base);
  else {
    drawBackground(c, R, g, f);
    drawShadow(c, g);
    drawTitle(c, R, g, f);
    drawChips(c, R, g, f);
    drawClose(c, g);
  }
  drawGlow(c, g, f);
  drawConfetti(c, R, g, f);
  if (!gpu) drawFallbackDrum(c, R, g, f);
  drawSparkles(c, R, g, f);
  const lk = f.lens[4];
  if (lk > 0.01) {
    // The cards go through the liquid-glass slab while the choice flows between them.
    const b = Skia.RuntimeShaderBuilder(R.glass);
    b.setUniform('lens', [f.lens[0] * g.sx, f.lens[1] * g.sy, f.lens[2] * g.sx, f.lens[3] * g.sy]);
    b.setUniform('radius', [L.cardR * g.sx]);
    b.setUniform('k', [lk]);
    const lp = Skia.Paint();
    lp.setImageFilter(Skia.ImageFilter.MakeRuntimeShader(b, 'image', null));
    c.saveLayer(lp, Skia.XYWHRect(0, (L.cardY - 30) * g.sy, g.w, (L.cardH + 60) * g.sy));
  }
  for (let k = 0; k < 3; k++) drawCard(c, R, g, f, k);
  if (lk > 0.01) c.restore();
  drawDim(c, g, f);
  // Play lives on the page unless the rack focus is on (then the top layer keeps it sharp), so
  // the top canvas is empty, and costs nothing, most of the loop.
  if (f.focus <= 0.001) drawPlay(c, R, g, f);
}

// ── the top layer: the drawn ball, its morph, the golden ticket and its stub ──────────────────
// Drawn above the rack-focus blur, so it stays sharp while the page goes soft behind it.

function seamPoint(t: number) {
  'worklet';
  const A = 0.78;
  const B = 0.22;
  return [A * Math.cos(t) + B * Math.cos(3 * t), A * Math.sin(t) - B * Math.sin(3 * t), 2 * Math.sqrt(A * B) * Math.sin(2 * t)];
}

/** Object point → view normal: the inverse of the shaders' ry(rx(n, rx), ry). */
function toView(o: number[], rx: number, ry: number) {
  'worklet';
  const cy = Math.cos(-ry);
  const sy = Math.sin(-ry);
  const x1 = cy * o[0] + sy * o[2];
  const z1 = -sy * o[0] + cy * o[2];
  const cx = Math.cos(-rx);
  const sx = Math.sin(-rx);
  return [x1, cx * o[1] - sx * z1, sx * o[1] + cx * z1];
}

function ticketRect(g: Geo) {
  'worklet';
  const w = TICKET.w * g.sx;
  const h = TICKET.h * g.sx;
  return { x: TICKET.x * g.sx - w / 2, y: TICKET.y * g.sy - h / 2, w, h };
}

function drawMorph(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const b = f.hero;
  const tr = ticketRect(g);
  const cx = b ? b.x : TICKET.x * g.sx;
  const cy = b ? b.y : TICKET.y * g.sy;
  const r = b ? b.r : 56 * g.sx;
  const rot = b ? [b.rx, b.ry] : [4 + (T.morph[0] - T.eject[1]) * 1.2, 0.5];
  const p = Skia.Paint();
  const shader = R.morph.makeShaderWithChildren(
    [cx, cy, r, rot[0], rot[1], 0, f.stitches, f.morph, TICKET.x * g.sx, TICKET.y * g.sy, tr.w, tr.h, TICKET.body, R.ticket.width(), R.ticket.height(), 12 * g.sx, b ? b.glow : 1, 1],
    [R.ticket.makeShaderOptions(TileMode.Decal, TileMode.Decal, 1, 1)],
  );
  p.setShader(shader);
  const sq = b ? b.sq : 1;
  c.save();
  if (sq !== 1) {
    c.translate(cx, cy + r);
    c.scale(1 / Math.sqrt(sq), sq);
    c.translate(-cx, -(cy + r));
  }
  // Only the pixels it can touch: the ball and its halo, and the ticket's rectangle once it morphs.
  const pad = 1.95 * r;
  const morphing = f.morph > 0;
  const x0 = morphing ? Math.min(cx - pad, tr.x - 8) : cx - pad;
  const y0 = morphing ? Math.min(cy - pad, tr.y - 8) : cy - pad;
  const x1 = morphing ? Math.max(cx + pad, tr.x + tr.w + 8) : cx + pad;
  const y1 = morphing ? Math.max(cy + pad, tr.y + tr.h + 8) : cy + pad;
  c.drawRect(Skia.XYWHRect(x0, y0, x1 - x0, y1 - y0), p);
  c.restore();
  // Each stitch that pops lets go of its thread: a short red thread whips off the seam, curls,
  // falls and fades, with a pin-prick of light where it broke.
  if (f.stitchT >= 0 && b) {
    const dur = T.stitches[1] - T.stitches[0];
    const thread = paint(0xb3242a, 1);
    thread.setStyle(1);
    thread.setStrokeCap(1);
    thread.setStrokeWidth(1.5 * g.sx);
    const pin = paint(0xffe6b0, 1);
    for (let i = 0; i < 24; i++) {
      const t = (i / 24) * Math.PI * 2;
      const order = (((t / (Math.PI * 2) + 0.25) % 1) + 1) % 1;
      const age = f.stitchT - order * dur;
      if (age < 0 || age > 0.6) continue;
      const n = toView(seamPoint(t), rot[0], rot[1]);
      if (n[2] < 0) continue;
      const l = Math.hypot(n[0], n[1]) || 1;
      const dx = n[0] / l;
      const dy = -n[1] / l;
      const k = age / 0.6;
      const dist = ((150 * (1 - Math.exp(-age * 5))) / 5) * g.sx;
      const ox = cx + n[0] * r + dx * dist;
      const oy = cy - n[1] * r + dy * dist + 130 * age * age * g.sy;
      const spin = (rand(i, 41) > 0.5 ? 1 : -1) * (2 + 3 * rand(i, 42));
      let ang = Math.atan2(dy, dx) + spin * age * 3;
      const bend = Math.sin(age * 26 + i) * 0.9 * (1 - k) + 0.5;
      const len = (3.6 + 1.6 * rand(i, 43)) * g.sx;
      thread.setAlphaf(1 - k * k);
      let x = ox;
      let y = oy;
      for (let j = 0; j < 3; j++) {
        const x2 = x + Math.cos(ang) * len;
        const y2 = y + Math.sin(ang) * len;
        c.drawLine(x, y, x2, y2, thread);
        x = x2;
        y = y2;
        ang += bend;
      }
      if (age < 0.09) {
        pin.setAlphaf(1 - age / 0.09);
        c.drawCircle(cx + n[0] * r, cy - n[1] * r, (1.2 + 2.4 * (age / 0.09)) * g.sx, pin);
      }
    }
  }
}

/** The ticket's printed text: "WINNING ENTRY", the rolling entry number and the stub's count. */
function drawTicketText(c: SkCanvas, R: Res, g: Geo, f: Frame, a: number, stub: boolean, body: boolean) {
  'worklet';
  const tr = ticketRect(g);
  const bodyW = tr.w * TICKET.body;
  const ink = 0x4a2c07;
  if (body) {
    const bx = tr.x + bodyW / 2;
    // Letterpress: each line has a pale highlight just under it, as if pressed into the foil.
    centredTracked(c, R.text.caption, COPY.winner, bx, tr.y + tr.h * 0.36 + 0.7 * g.sx, paint(0xfff1c4, 0.5 * a), 0.12);
    centredTracked(c, R.text.caption, COPY.winner, bx, tr.y + tr.h * 0.36, paint(ink, 0.85 * a), 0.12);
    // The number rolls in digit by digit, each blurred while it moves.
    const digits = COPY.entry;
    const font = R.text.entry;
    const dw = widthOf(font, '0');
    const total = dw * (digits.length + 1);
    const base = tr.y + tr.h * 0.72;
    const size = font.getSize();
    c.save();
    c.clipRect(Skia.XYWHRect(bx - total / 2 - 4, base - size * 0.95, total + 8, size * 1.15), ClipOp.Intersect, true);
    c.drawText('#', bx - total / 2, base + 0.8 * g.sx, paint(0xfff1c4, 0.45 * a), font);
    c.drawText('#', bx - total / 2, base, paint(ink, a), font);
    for (let i = 0; i < digits.length; i++) {
      const k = easeOut(seg(f.number, i * 0.12, 0.55 + i * 0.12));
      const target = digits.charCodeAt(i) - 48;
      const spins = 2 + i;
      const pos = (1 - k) * spins * 10;
      const lp = paint(ink, a);
      const blur = Math.min(3, (1 - k) * 6) * g.sx;
      if (blur > 0.2) lp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
      const x = bx - total / 2 + dw * (i + 1);
      const hl = paint(0xfff1c4, 0.45 * a * k);
      for (let j = -1; j <= 1; j++) {
        const step = Math.floor(pos) + j;
        const frac = pos - Math.floor(pos);
        const digit = (((target - step) % 10) + 10) % 10;
        const y = base + (frac - j) * size * 1.0 * -1;
        if (k > 0.6) c.drawText(String(digit), x, y + 0.8 * g.sx, hl, font);
        c.drawText(String(digit), x, y, lp, font);
      }
    }
    c.restore();
  }
  if (stub) {
    const sx = tr.x + bodyW + (tr.w - bodyW) / 2;
    centredTracked(c, R.text.stub, COPY.stubTop, sx, tr.y + tr.h * 0.4, paint(ink, 0.8 * a), 0.08);
    centred(c, R.text.stubBig, `×${f.to + 1}`, sx, tr.y + tr.h * 0.7 + 0.8 * g.sx, paint(0xfff1c4, 0.45 * a));
    centred(c, R.text.stubBig, `×${f.to + 1}`, sx, tr.y + tr.h * 0.7, paint(ink, a));
  }
}

/** The foil shader for a ticket drawn with its full rectangle at (ox, oy) in local coordinates. */
function foilPaint(R: Res, g: Geo, f: Frame, ox: number, oy: number, mode: number, sweep: number, lit: number, tilt: number[]) {
  'worklet';
  const tr = ticketRect(g);
  const p = Skia.Paint();
  p.setShader(
    R.foil.makeShaderWithChildren(
      [ox, oy, tr.w, tr.h, R.ticket.width(), R.ticket.height(), tilt[0], tilt[1], lit, sweep, mode, f.burn, TICKET.body],
      [R.ticket.makeShaderOptions(TileMode.Decal, TileMode.Decal, 1, 1)],
    ),
  );
  return p;
}

/** A perspective tilt (rad) about the vertical axis (x) and the horizontal one (y), at the origin. */
function perspective(g: Geo, ax: number, ay: number) {
  'worklet';
  const d = 700 * g.sx;
  return Skia.Matrix([Math.cos(ax), 0, 0, 0, Math.cos(ay), 0, Math.sin(ax) / d, -Math.sin(ay) / d, 1]);
}

/** The burnt body's gold dust: each mote leaves the burning edge and flies into the chosen card. */
function drawDust(c: SkCanvas, g: Geo, f: Frame, bob: number) {
  'worklet';
  const t = f.t;
  if (t < T.burn[0] || t > T.burn[1] + T.dust) return;
  const tr = ticketRect(g);
  const card = f.to;
  const cardX = (L.cardX0 + card * (L.cardW + L.cardGap) + f.strip + L.cardW / 2) * g.sx;
  const cardY = (L.cardY + 62) * g.sy;
  const dur = T.burn[1] - T.burn[0];
  const big: SkPoint[] = [];
  const small: SkPoint[] = [];
  for (let i = 0; i < 120; i++) {
    const u = rand(i, 61) * TICKET.body;
    const v = 0.12 + 0.76 * rand(i, 62);
    const at = (TICKET.body + 0.09 - u + (rand(i, 63) - 0.5) * 0.14) / (TICKET.body + 0.27);
    const age = t - (T.burn[0] + at * dur);
    if (age < 0 || age > T.dust) continue;
    const k = easeInOut(age / T.dust);
    const x0 = tr.x + u * tr.w;
    const y0 = tr.y + v * tr.h + bob;
    // Up first, like ash off a flame, then down and in, swirling.
    const qx = x0 + (rand(i, 64) - 0.5) * 90 * g.sx;
    const qy = y0 - (30 + 50 * rand(i, 65)) * g.sy;
    const x1 = cardX + (rand(i, 66) - 0.5) * 70 * g.sx;
    const y1 = cardY + (rand(i, 67) - 0.5) * 40 * g.sy;
    const w = 1 - k;
    const swirl = Math.sin(age * 14 + i) * 5 * g.sx * w;
    const pt = { x: w * w * x0 + 2 * w * k * qx + k * k * x1 + swirl, y: w * w * y0 + 2 * w * k * qy + k * k * y1 };
    if (rand(i, 68) > 0.6) big.push(pt);
    else small.push(pt);
  }
  const fadeIn = Math.min(1, (t - T.burn[0]) / 0.08);
  const halo = paint(C.gold, 0.55 * fadeIn);
  halo.setStrokeCap(1);
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 * g.sx, true));
  const core = paint(0xfff6dc, fadeIn);
  core.setStrokeCap(1);
  halo.setStrokeWidth(7 * g.sx);
  core.setStrokeWidth(2.6 * g.sx);
  if (big.length) {
    c.drawPoints(PointMode.Points, big, halo);
    c.drawPoints(PointMode.Points, big, core);
  }
  halo.setStrokeWidth(4.5 * g.sx);
  core.setStrokeWidth(1.6 * g.sx);
  if (small.length) {
    c.drawPoints(PointMode.Points, small, halo);
    c.drawPoints(PointMode.Points, small, core);
  }
}

/** Where the stub lands in the check: a gold ring opening out and a few short rays. */
function drawLanding(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.land < 0) return;
  const card = f.to;
  const x = (L.cardX0 + card * (L.cardW + L.cardGap) + f.strip + L.checkDX) * g.sx;
  const y = (L.cardY - 2 + L.checkDY) * g.sy;
  const k = easeOut(Math.min(1, f.land / 0.5));
  const ring = paint(0xffdf8a, 1 - k);
  ring.setStyle(1);
  ring.setStrokeWidth(mix(2.6, 0.6, k) * g.sx);
  c.drawCircle(x, y, mix(12, 34, k) * g.sx, ring);
  if (f.land < 0.3) {
    const q = f.land / 0.3;
    const ray = paint(0xfff3c8, 1 - q);
    ray.setStyle(1);
    ray.setStrokeCap(1);
    ray.setStrokeWidth(1.6 * g.sx);
    for (let i = 0; i < 8; i++) {
      const ang = (i / 8) * Math.PI * 2 + 0.3;
      const r0 = mix(14, 22, easeOut(q)) * g.sx;
      const r1 = r0 + mix(6, 2, q) * g.sx;
      c.drawLine(x + Math.cos(ang) * r0, y + Math.sin(ang) * r0, x + Math.cos(ang) * r1, y + Math.sin(ang) * r1, ray);
    }
  }
}

function drawTicket(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const tr = ticketRect(g);
  const split = TICKET.body;
  const textA = seg(f.morph, 0.82, 1);
  const held = f.t - T.morph[1];
  const bob = held > 0 ? Math.sin(held * 2.1) * 3 * g.sy * (1 - f.burn) : 0;
  const cx = TICKET.x * g.sx;
  const cy = TICKET.y * g.sy + bob;
  const tear = f.tear;
  // Body.
  if (f.burn < 1) {
    // The glow it casts on the page.
    const glow = paint(C.gold, 0.32 * (1 - f.burn));
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 28 * g.sx, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tr.x + 12, tr.y + bob + 16, tr.w - 24, tr.h - 8), 20, 20), glow);
    c.save();
    c.translate(cx, cy);
    c.concat(perspective(g, f.tilt[0], f.tilt[1]));
    c.translate(-cx, -(cy - bob));
    // A layer only while it burns (the burn mask needs one); otherwise the foil draws straight in.
    const burning = f.burn > 0;
    if (burning) c.saveLayer(undefined, Skia.XYWHRect(tr.x - 2, tr.y - 2, tr.w + 4, tr.h + 4));
    const lit = foilPaint(R, g, f, tr.x, tr.y, 0, f.foil, f.lit, f.tilt);
    c.drawRect(Skia.XYWHRect(tr.x, tr.y, tr.w * (tear === 0 ? 1 : split), tr.h), lit);
    drawTicketText(c, R, g, f, textA, tear === 0, true);
    if (f.burn > 0) {
      const mask = foilPaint(R, g, f, tr.x, tr.y, 1, 0, 0, f.tilt);
      mask.setBlendMode(BlendMode.DstIn);
      c.drawRect(Skia.XYWHRect(tr.x, tr.y, tr.w * split, tr.h), mask);
    }
    if (burning) c.restore();
    if (burning) {
      const edge = foilPaint(R, g, f, tr.x, tr.y, 2, 0, 0, f.tilt);
      edge.setBlendMode(BlendMode.Plus);
      c.drawRect(Skia.XYWHRect(tr.x, tr.y, tr.w * split, tr.h), edge);
    }
    c.restore();
  }
  drawDust(c, g, f, bob);
  // Stub: tears along the perforation, then flies into the chosen card's check, turning over once
  // on the way (its violet back shows half the time).
  if (tear > 0) {
    const sw = tr.w * (1 - split);
    const sx0 = tr.x + tr.w * split + sw / 2;
    const sy0 = TICKET.y * g.sy;
    const card = f.to;
    const kx = (L.cardX0 + card * (L.cardW + L.cardGap) + f.strip + L.checkDX) * g.sx;
    const ky = (L.cardY - 2 + L.checkDY) * g.sy;
    const ripK = seg(tear, 0, 0.25);
    const flyK = easeInOut(seg(tear, 0.25, 1));
    const v = 1 - flyK;
    const px = v * v * (sx0 + 8 * g.sx * ripK) + 2 * v * flyK * mix(sx0, kx, 0.5) + flyK * flyK * kx;
    const py = v * v * sy0 + 2 * v * flyK * (Math.min(sy0, ky) - 120 * g.sy) + flyK * flyK * ky;
    const rot = -9 * Math.sin(Math.PI * Math.min(1, ripK * 1.4)) * (1 - flyK) + 140 * flyK * (1 - flyK);
    const sc = mix(1, (L.checkD * g.sx) / tr.h, flyK);
    const flip = Math.cos(flyK * Math.PI * 2);
    const a = 1 - seg(tear, 0.9, 1);
    if (a > 0.01) {
      c.save();
      c.translate(px, py);
      c.rotate(rot, 0, 0);
      c.scale(sc * Math.max(0.04, Math.abs(flip)), sc);
      const rect = Skia.XYWHRect(-sw / 2, -tr.h / 2, sw, tr.h);
      // A layer only for the fade at the end and the edge-on shading.
      const layered = a < 0.999 || Math.abs(flip) < 0.995;
      if (layered) {
        const lp = Skia.Paint();
        lp.setAlphaf(a);
        c.saveLayer(lp, rect);
      }
      if (flip >= 0) {
        c.drawRect(rect, foilPaint(R, g, f, -sw / 2 - tr.w * split, -tr.h / 2, 0, 0, 1, [flyK * Math.PI * 2, 0]));
        c.save();
        c.translate(-sx0, -sy0);
        drawTicketText(c, R, g, f, 1, true, false);
        c.restore();
      } else {
        const bw = R.back.width();
        const bh = R.back.height();
        c.drawImageRectOptions(R.back, Skia.XYWHRect(bw * split, 0, bw * (1 - split), bh), rect, 1, 1, Skia.Paint());
      }
      // Edge-on it darkens, face-on it catches the light.
      if (layered) {
        const shade = paint(0x000000, 0.45 * (1 - Math.abs(flip)));
        shade.setBlendMode(BlendMode.SrcATop);
        c.drawRect(rect, shade);
        c.restore();
      }
      c.restore();
    }
    // Paper fibres at the perforation as it rips.
    if (ripK > 0 && ripK < 1) {
      const fx = tr.x + tr.w * split;
      const fib = paint(0xfff1c8, 1);
      for (let i = 0; i < 10; i++) {
        const fy = tr.y + (i + 0.5) * (tr.h / 10);
        const life = Math.sin(Math.PI * seg(ripK, i * 0.06, i * 0.06 + 0.4));
        if (life <= 0) continue;
        fib.setAlphaf(life);
        c.drawCircle(fx + (rand(i, 81) - 0.3) * 10 * g.sx * ripK, fy, 1.4 * g.sx, fib);
      }
    }
  }
}

/** The draw's light (LIGHT_SKSL), over the page above the blur; only while a part of it plays. */
function drawLight(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.streak <= 0.001 && f.rays <= 0.001 && f.ring <= 0.001 && f.aurora <= 0.001) return;
  const p = Skia.Paint();
  const tx = TICKET.x * g.sx;
  const ty = TICKET.y * g.sy;
  p.setShader(
    R.light.makeShader([g.w, g.h, f.streakAt[0], f.streakAt[1], f.streak, tx, ty, f.rays, f.t * 0.22, tx, ty, f.ringR, f.ring, tx, ty, f.aurora, f.t + f.idleT, g.sx]),
  );
  // Silk alone stays near the ticket; the rest may cross the screen.
  if (f.streak <= 0.001 && f.rays <= 0.001 && f.ring <= 0.001) c.drawRect(Skia.XYWHRect(0, ty - 240 * g.sy, g.w, 480 * g.sy), p);
  else c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), p);
}

/** Once a loop, while the page rests, a glint runs over the crank's grip: a hint that it turns. */
function drawCrankHint(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.focus > 0.001) return;
  const u = ((f.t + 0.45) % LOOP) / 0.9;
  if (u >= 1) return;
  const a = Math.sin(Math.PI * u);
  // The grip's middle on the cage's axle frame, turned with the cage (as the GPU turns it).
  const cs = Math.cos(f.theta);
  const sn = Math.sin(f.theta);
  const p = project(1.58, -0.2 * cs, -0.2 * sn, g.sx);
  const glow = paint(0xfff1dc, 0.55 * a);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 * g.sx, true));
  c.drawCircle(p.x, p.y, 8 * g.sx, glow);
  const spark = paint(0xffffff, 0.9 * a);
  c.drawCircle(p.x + mix(-10, 10, u) * g.sx, p.y - 1.5 * g.sx, 1.6 * g.sx, spark);
}

export function drawTop(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  // Play / Claim stays sharp above the rack focus: it is what the visitor acts on.
  if (f.focus > 0.001) drawPlay(c, R, g, f);
  drawLight(c, R, g, f);
  if (f.hero || (f.morph > 0 && f.morph < 1)) {
    drawMorph(c, R, g, f);
    // The print comes up in the foil as the shape settles, so the ticket is never a blank plate.
    // (Called from here: a worklet only sees the functions declared above it.)
    const ink = seg(f.morph, 0.78, 1);
    if (ink > 0) drawTicketText(c, R, g, f, ink, true, true);
  }
  else if (f.morph >= 1 && (f.burn < 1 || f.tear > 0 || f.t < T.burn[1] + T.dust)) drawTicket(c, R, g, f);
  drawLanding(c, g, f);
  drawCrankHint(c, g, f);
}
