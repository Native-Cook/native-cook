/**
 * The whole screen drawn from the model in one Skia picture: the page (an image made at mount), the header with the
 * focus total, the week strip, the hour labels and now, the blocks as glass with their shadows and capsules on the
 * rail, the edge's time, the bottom bar, the demo's finger. The phone records it every frame something moves.
 *
 * Layers, back to front: page, header, week, labels, resting shadows, blocks, the held block's shadow and the held
 * block (its shadow falls on the blocks below it), the edge's pill, the bar, the finger.
 */
import {
  ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkFont, type SkImage,
  type SkRuntimeEffect, type SkShader, type SkTypeface,
} from '@shopify/react-native-skia';

import type { Backdrop } from './backdrop';
import { DAY_DATE, DAY_TITLE, KIND, PLAN, TODAY, WEEK, clock, length } from './day';
import { GLASS_EFFECT } from './glass-shader';
import { layout, type Model } from './model';
import { LIFT, RESIZE } from './motion';
import { SHADOW_EFFECT, SHADOW_SLOTS } from './shadow-shader';
import { COLOR, DAY, LAYOUT, SHADOW_RGB, TINT, TYPE, yOf, type Layout, type TypeRole } from './theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semibold: SkTypeface; mono: SkTypeface; monoBold: SkTypeface };
export type Fonts = Record<TypeRole, SkFont>;
export type SceneCtx = {
  lay: Layout;
  fonts: Fonts;
  shadow: SkRuntimeEffect;
  glass: SkRuntimeEffect;
  frost: SkShader;
  page: SkImage;
  pageScale: number;
  digit: number;
};

/** SkFonts per role and the frosted page as a shader in points, made once on the JS thread. */
export function makeScene(lay: Layout, faces: Faces, back: Backdrop): SceneCtx {
  const fonts = {} as Fonts;
  for (const role of Object.keys(TYPE) as TypeRole[]) fonts[role] = Skia.Font(faces[TYPE[role].face], TYPE[role].size);
  let digit = 0;
  for (let d = 0; d < 10; d++) digit = Math.max(digit, advance(fonts.total, String(d)));
  const m = Skia.Matrix();
  m.scale(1 / back.frostScale, 1 / back.frostScale);
  const frost = back.frosted.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, m);
  return { lay, fonts, shadow: SHADOW_EFFECT, glass: GLASS_EFFECT, frost, page: back.sharp, pageScale: back.sharpScale, digit };
}

// ——— Small helpers (declared before everything that calls them) ———

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

function smooth(e0: number, e1: number, x: number): number {
  'worklet';
  const t = Math.min(Math.max((x - e0) / (e1 - e0), 0), 1);
  return t * t * (3 - 2 * t);
}

function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, alpha = 1, align = 0) {
  'worklet';
  if (alpha <= 0.004 || !s) return;
  const w = advance(font, s);
  const left = align === 0 ? x : align === 1 ? x - w / 2 : x - w;
  c.drawText(s, left, y, paintOf(color, alpha), font);
}

/** The string cut to `w` with an ellipsis. */
function fit(font: SkFont, s: string, w: number): string {
  'worklet';
  if (advance(font, s) <= w) return s;
  let k = s.length;
  while (k > 1 && advance(font, `${s.slice(0, k).trimEnd()}…`) > w) k--;
  return `${s.slice(0, k).trimEnd()}…`;
}

/**
 * A piece of glass: the frosted page refracted through a rounded box, tinted [r, g, b, strength], `thick` 0..1 (how
 * far its rim bends the page), lit from inside at the finger by `glow`.
 */
function glass(
  c: SkCanvas, ctx: SceneCtx, x: number, y: number, w: number, h: number, r: number,
  tint: readonly number[], thick = 0, glow = 0, gx = 0, gy = 0, alpha = 1,
) {
  'worklet';
  if (alpha <= 0.004 || w <= 0 || h <= 0) return;
  const p = Skia.Paint();
  p.setShader(ctx.glass.makeShaderWithChildren(
    [x, y, w, h, r, tint[0], tint[1], tint[2], tint[3], thick, gx, gy, glow, alpha], [ctx.frost],
  ));
  c.drawRect(Skia.XYWHRect(x - 1, y - 1, w + 2, h + 2), p);
}

function kindColor(kind: number): string {
  'worklet';
  return kind === KIND.focus ? COLOR.focus : kind === KIND.task ? COLOR.task : COLOR.meeting;
}

// ——— Header ———

/**
 * A number of minutes as "2h 00m", right-aligned on `right`, its digits rolling a quarter hour at a time. Each number
 * sits 1.5 pt from its unit and 5 pt from the next number, so it reads as two tight pairs.
 */
function drawRoll(c: SkCanvas, ctx: SceneCtx, value: number, right: number, base: number, color: string, reduced: boolean) {
  'worklet';
  const { fonts, digit } = ctx;
  const v = Math.max(value, 0);
  const a = Math.floor((v + 0.01) / RESIZE.quarter) * RESIZE.quarter;
  const f = Math.min(Math.max((v - a) / RESIZE.quarter, 0), 1);
  const b = a + RESIZE.quarter;
  const roll = reduced ? 0 : 16;
  const parts = (x: number): [string, string] => {
    const mm = Math.round(x) % 60;
    return [String(Math.floor(Math.round(x) / 60)), `${mm < 10 ? '0' : ''}${mm}`];
  };
  const [ha, ma] = parts(a);
  const [hb, mb] = parts(b);
  const tight = 1.5;
  const loose = 5;
  // One cell per digit; a cell whose digit changes rolls the old one up and the new one in. Minute digits are centred
  // in equal cells; an hour digit keeps its right edge on the "h" (k = 1), so a wider figure rolling in never nudges it.
  const cell = (da: string, db: string, cx: number, w: number, k = 0.5) => {
    const at = (d: string) => cx + (w - advance(fonts.total, d)) * k;
    if (f < 0.002 || da === db) {
      text(c, da, at(da), base, fonts.total, color);
      return;
    }
    c.save();
    c.clipRect(Skia.XYWHRect(cx - 2, base - 15, w + 4, 19), ClipOp.Intersect, true);
    text(c, da, at(da), base - roll * f, fonts.total, color, 1 - f);
    text(c, db, at(db), base + roll * (1 - f), fonts.total, color, f);
    c.restore();
  };
  let x = right - advance(fonts.totalUnit, 'm');
  text(c, 'm', x, base, fonts.totalUnit, color);
  x -= tight + 2 * digit;
  cell(ma[0], mb[0], x, digit);
  cell(ma[1], mb[1], x + digit, digit);
  x -= loose + advance(fonts.totalUnit, 'h');
  text(c, 'h', x, base, fonts.totalUnit, color);
  const cells = Math.max(ha.length, hb.length);
  for (let k = cells - 1; k >= 0; k--) {
    const da = ha.padStart(cells, ' ')[k].trim();
    const db = hb.padStart(cells, ' ')[k].trim();
    const w = Math.max(advance(fonts.total, da), advance(fonts.total, db));
    x -= (k === cells - 1 ? tight : 0) + w;
    cell(da, db, x, w, 1);
  }
}

function drawHeader(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { lay, fonts } = ctx;
  text(c, DAY_TITLE, LAYOUT.edge, lay.top + LAYOUT.line1, fonts.title, COLOR.ink);
  text(c, `${DAY_DATE} · ${PLAN.length} plans`, LAYOUT.edge, lay.top + LAYOUT.line2, fonts.date, COLOR.mute);
  // The focus total in a glass chip: a dot and the label over the rolling figure.
  const ch = LAYOUT.chip;
  const x = lay.right - ch.w;
  const y = lay.top + ch.y;
  glass(c, ctx, x, y, ch.w, ch.h, ch.h / 2, TINT.chrome);
  const pad = 18;
  c.drawCircle(x + pad + 3, y + 17, 3, paintOf(COLOR.focus));
  text(c, 'Focus today', x + pad + 11, y + 21, fonts.chipLabel, COLOR.mute);
  drawRoll(c, ctx, m.focus, x + ch.w - pad, y + 39, COLOR.focusInk, m.reduced);
}

/** Monday to Sunday in a glass strip; today sits in a brighter lens of its own. */
function drawWeek(c: SkCanvas, ctx: SceneCtx) {
  'worklet';
  const { lay, fonts } = ctx;
  const wk = LAYOUT.week;
  const x0 = LAYOUT.edge;
  const w = lay.right - x0;
  const y = lay.top + wk.y;
  glass(c, ctx, x0, y, w, wk.h, wk.radius, TINT.chrome);
  const step = (w - 8) / 7;
  for (let i = 0; i < WEEK.length; i++) {
    const d = WEEK[i];
    const cx = x0 + 4 + step * (i + 0.5);
    const today = i === TODAY;
    if (today) glass(c, ctx, cx - wk.cell / 2, y + 4, wk.cell, wk.h - 8, (wk.h - 8) / 2, TINT.selected, 1);
    text(c, d.day, cx, y + 21, fonts.weekday, today ? COLOR.ink : COLOR.faint, 1, 1);
    text(c, String(d.date), cx, y + 40, fonts.weekdate, today || i > TODAY ? COLOR.ink : COLOR.mute, today || i > TODAY ? 1 : 0.9, 1);
    const n = d.dots.length;
    for (let k = 0; k < n; k++) c.drawCircle(cx + (k - (n - 1) / 2) * 6, y + 48, 1.75, paintOf(kindColor(d.dots[k]), today ? 1 : 0.7));
  }
}

// ——— Labels ———

/** The y of the edge being resized (or settling), or -1. */
function edgeY(ctx: SceneCtx, m: Model): number {
  'worklet';
  const i = m.shown;
  if (i < 0 || m.lift <= 0.001) return -1;
  return yOf(ctx.lay, m.edge === 1 ? m.top[i] + m.len[i] : m.top[i]);
}

/**
 * The edge's pill and the hour labels take turns instead of crossfading over each other: the pill is drawn while the
 * lift is above one half, a label near it only once the lift is under one fifth.
 */
function pillShow(m: Model): number {
  'worklet';
  return smooth(0.5, 1, m.lift);
}

function labelMakesWay(m: Model, d: number): number {
  'worklet';
  return smooth(0.2, 0.5, m.lift) * (1 - smooth(22, 30, d));
}

/** A capsule with a time in it, its right edge on the labels' right edge plus `pillPad`. */
function timePill(c: SkCanvas, ctx: SceneCtx, cy: number, label: string, fill: string, ink: string, alpha: number) {
  'worklet';
  if (alpha <= 0.004) return;
  const { fonts } = ctx;
  const h = LAYOUT.pillH;
  const w = advance(fonts.pill, label) + 2 * LAYOUT.pillPad;
  const x = LAYOUT.labelRight + LAYOUT.pillPad - w;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, cy - h / 2, w, h), h / 2, h / 2), paintOf(fill, alpha));
  text(c, label, LAYOUT.labelRight, cy + 4, fonts.pill, ink, alpha, 2);
}

/** Hour labels in the left column, in mono so the times line up; now is a red pill on its own line. */
function drawLabels(c: SkCanvas, ctx: SceneCtx, m: Model, ey: number) {
  'worklet';
  const { lay, fonts } = ctx;
  const ny = yOf(lay, DAY.now);
  for (let h = DAY.start / 60; h <= DAY.end / 60; h++) {
    const y = yOf(lay, h * 60);
    let a = 1;
    if (ey >= 0) a *= 1 - labelMakesWay(m, Math.abs(y - ey));
    a *= smooth(12, 20, Math.abs(y - ny));
    text(c, clock(h * 60), LAYOUT.labelRight, y + 4, fonts.hour, COLOR.faint, a, 2);
  }
  let na = 1;
  if (ey >= 0) na *= 1 - labelMakesWay(m, Math.abs(ny - ey));
  timePill(c, ctx, ny, clock(DAY.now), COLOR.now, COLOR.ink, na);
}

// ——— Blocks ———

type Box = { x: number; y: number; w: number; h: number; s: number; cx: number; cy: number };

/** Block i's box on screen: squashed blocks bulge a little, the held block rises and grows by a hair. */
function boxOf(ctx: SceneCtx, m: Model, i: number, lifted: boolean): Box {
  'worklet';
  const { lay } = ctx;
  const planned = m.end[i] - m.start[i];
  const squash = planned > 0 ? Math.max(0, 1 - m.len[i] / planned) : 0;
  const bulge = m.kind[i] === KIND.meeting || i === m.active ? 0 : Math.min(RESIZE.bulgeMax, RESIZE.squashBulge * squash);
  const up = lifted && !m.reduced ? m.lift : 0; // Reduce Motion: the block stays in place, only its shadow deepens
  // A meeting the chain runs into gives by a point at its top, then comes back when the chain lets go.
  const give = m.kind[i] === KIND.meeting && !m.reduced ? 1.5 * m.lock[i] : 0;
  const y = yOf(lay, m.top[i]) + LAYOUT.cardInset - LIFT.rise * up + give;
  const h = Math.max(m.len[i] * lay.ppm - 2 * LAYOUT.cardInset - give, 4);
  const x = lay.cardX - bulge;
  const w = lay.cardW + 2 * bulge;
  const s = 1 + LIFT.scale * up;
  return { x, y, w, h, s, cx: x + w / 2, cy: y + h / 2 };
}

function drawShadows(c: SkCanvas, ctx: SceneCtx, m: Model, only: number, skip: number) {
  'worklet';
  const { lay } = ctx;
  const rects: number[] = [];
  const params: number[] = [];
  let count = 0;
  for (let i = 0; i < m.n && count < SHADOW_SLOTS; i++) {
    if (i === skip || (only >= 0 && i !== only)) continue;
    const b = boxOf(ctx, m, i, i === only);
    const w = b.w * b.s;
    const h = b.h * b.s;
    rects.push(b.cx - w / 2, b.cy - h / 2, w, h);
    params.push(m.elev[i] * (m.kind[i] === KIND.meeting ? 0.6 : 1), 2.2, Math.min(LAYOUT.radius, h / 2) * b.s, 0);
    count++;
  }
  if (count === 0) return;
  while (rects.length < SHADOW_SLOTS * 4) rects.push(0, 0, 0, 0);
  while (params.length < SHADOW_SLOTS * 4) params.push(0, 0, 0, 0);
  const p = Skia.Paint();
  p.setShader(ctx.shadow.makeShader([...rects, ...params, count, SHADOW_RGB[0], SHADOW_RGB[1], SHADOW_RGB[2]]));
  c.drawRect(Skia.XYWHRect(0, lay.gridTop - 24, lay.width, lay.barTop - lay.gridTop + 24), p);
}

/** A small padlock centred on (x, y). */
function drawLock(c: SkCanvas, x: number, y: number, a: number, reduced: boolean) {
  'worklet';
  if (a <= 0.01) return;
  const k = reduced ? 1 : 0.85 + 0.15 * a; // it grows into place as it fades in (Reduce Motion: it only fades)
  const shackle = Skia.PathBuilder.Make()
    .moveTo(x - 2.5 * k, y - 0.5 * k)
    .lineTo(x - 2.5 * k, y - 2.5 * k)
    .cubicTo(x - 2.5 * k, y - 6 * k, x + 2.5 * k, y - 6 * k, x + 2.5 * k, y - 2.5 * k)
    .lineTo(x + 2.5 * k, y - 0.5 * k)
    .build();
  c.drawPath(shackle, strokeOf(COLOR.ink, 1.25, 0.7 * a));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 4.5 * k, y - 1 * k, 9 * k, 7 * k), 1.75, 1.75), paintOf(COLOR.ink, 0.7 * a));
}

/**
 * Added time fills the held block with a warm light from where its edge was; either way the block says what changed
 * ("+45 min", "−1 h") just inside the edge being dragged. It fades with the lift after the release.
 */
function drawDelta(c: SkCanvas, ctx: SceneCtx, m: Model, b: Box, rr: ReturnType<typeof Skia.RRectXY>) {
  'worklet';
  const { fonts } = ctx;
  const a = pillShow(m);
  if (a < 0.01) return;
  const lift = b.y - (yOf(ctx.lay, m.top[m.shown]) + LAYOUT.cardInset);
  const g = yOf(ctx.lay, m.grabEdge) + lift;
  const e = yOf(ctx.lay, m.edge === 1 ? m.top[m.shown] + m.len[m.shown] : m.top[m.shown]) + lift;
  const grow = m.edge === 1 ? e - g : g - e;
  if (grow > 1) {
    const y0 = Math.min(g, e);
    const y1 = Math.max(g, e);
    const from = m.edge === 1 ? y0 : y1;
    const to = m.edge === 1 ? y1 : y0;
    const c0 = Skia.Color(COLOR.focus);
    c0[3] = 0;
    const c1 = Skia.Color(COLOR.focus);
    c1[3] = 0.2 * a;
    const band = Skia.Paint();
    band.setAntiAlias(true);
    band.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: from }, { x: 0, y: to }, [c0, c1], null, TileMode.Clamp));
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(b.x, y0, b.w, y1 - y0), band);
    c.restore();
  }
  if (m.delta > 0.004) {
    // Fades in at the first quarter and out on its last value (never "+0 min").
    const last = m.deltaLast;
    const label = `${last >= 0 ? '+' : '−'}${length(Math.abs(last))}`;
    const y = m.edge === 1 ? b.y + b.h - LAYOUT.grip.inset - 1 : b.y + LAYOUT.grip.inset + 9;
    if (b.h >= 40) text(c, label, b.x + b.w - LAYOUT.padX, y, fonts.delta, COLOR.focusInk, a * m.delta, 2);
  }
}

function drawBlock(c: SkCanvas, ctx: SceneCtx, m: Model, i: number, lifted: boolean, live: [number[], number[]]) {
  'worklet';
  const { fonts } = ctx;
  const b = boxOf(ctx, m, i, lifted);
  const kind = m.kind[i];
  const item = PLAN[i];
  c.save();
  if (b.s !== 1) {
    c.translate(b.cx, b.cy);
    c.scale(b.s, b.s);
    c.translate(-b.cx, -b.cy);
  }
  const r = Math.min(LAYOUT.radius, b.h / 2);
  const rr = Skia.RRectXY(Skia.XYWHRect(b.x, b.y, b.w, b.h), r, r);
  // The press lights the glass from the finger while the hold fills; held, the glass thickens and stays lit.
  const press = i === m.pressShown ? m.press : 0;
  const up = i === m.shown ? m.lift : 0;
  const lit = m.reduced ? 0 : Math.max(press, 0.75 * up);
  const base = kind === KIND.focus ? TINT.focus : kind === KIND.task ? TINT.task : TINT.meeting;
  // A meeting the chain runs into dims a shade.
  const tint = kind === KIND.meeting
    ? [base[0] * (1 - 0.45 * m.lock[i]), base[1] * (1 - 0.45 * m.lock[i]), base[2] * (1 - 0.4 * m.lock[i]), base[3] + 0.08 * m.lock[i]]
    : [base[0], base[1], base[2], base[3] + 0.06 * Math.max(press, up)];
  glass(c, ctx, b.x, b.y, b.w, b.h, r, tint, up, lit, m.fx, m.fy);

  if (lifted) drawDelta(c, ctx, m, b, rr);

  // Content, clipped to the block (a squashed task loses its bottom, not its shape).
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  const s = kind === KIND.meeting ? m.start[i] : live[0][i];
  const e = kind === KIND.meeting ? m.end[i] : live[0][i] + live[1][i];
  const sub = kind === KIND.focus ? COLOR.focusInk : COLOR.mute;
  let x = b.x + LAYOUT.padX;
  const right = b.x + b.w - LAYOUT.padX;
  const twoLines = b.h >= 44;
  const titleY = twoLines ? b.y + LAYOUT.padY + 14 : b.y + b.h / 2 + 5;
  if (kind === KIND.task) {
    c.drawCircle(x + LAYOUT.check / 2, titleY - 5, LAYOUT.check / 2 - 0.75, strokeOf(COLOR.task, 1.25, 0.85));
    x += LAYOUT.check + 8;
  }
  // Room for the lock opens as it fades in, so the note slides over instead of jumping.
  const lockW = kind === KIND.meeting ? 16 * smooth(0, 1, m.lock[i]) : 0;
  if (twoLines) {
    text(c, fit(fonts.event, item.title, right - x - lockW), x, titleY, fonts.event, COLOR.ink);
    // The times in mono, where it is in Inter.
    const range = kind === KIND.focus ? `${clock(s)}–${clock(e)} · ${length(e - s)}` : `${clock(s)}–${clock(e)}`;
    text(c, range, x, titleY + 18, fonts.eventSub, sub);
    if (kind !== KIND.focus && item.note) {
      const nx = x + advance(fonts.eventSub, range);
      text(c, fit(fonts.eventNote, ` · ${item.note}`, right - nx), nx, titleY + 18, fonts.eventNote, sub);
    }
  } else {
    const side = kind === KIND.meeting ? item.note : clock(s);
    const sideFont = kind === KIND.meeting ? fonts.eventNote : fonts.eventSub;
    let sideW = advance(sideFont, side);
    // A pushed task shows where it was, struck through, then where it is now in ink, 8 pt apart.
    const pushed = kind === KIND.task && i !== m.active && s > m.start[i] + 0.5;
    text(c, side, right - lockW, titleY, pushed ? fonts.eventNow : sideFont, pushed ? COLOR.ink : sub, 1, 2);
    if (pushed) sideW = advance(fonts.eventNow, side);
    const wasA = kind === KIND.task ? m.push[i] : 0;
    if (wasA > 0.004) {
      const was = clock(m.was[i]);
      const ww = advance(fonts.eventSub, was);
      const wx = right - sideW - 8 - ww;
      text(c, was, wx, titleY, fonts.eventSub, COLOR.faint, wasA);
      c.drawRect(Skia.XYWHRect(wx - 1, titleY - 4, ww + 2, 1), paintOf(COLOR.faint, wasA));
      if (pushed) sideW += 8 + ww;
    }
    text(c, fit(fonts.event, item.title, right - lockW - sideW - 12 - x), x, titleY, fonts.event, COLOR.ink);
  }
  c.restore();
  if (kind === KIND.meeting) drawLock(c, right - 4.5, titleY - 5, m.lock[i], m.reduced);

  // The grip on the edge being resized.
  if (lifted && m.lift > 0.01) {
    const g = LAYOUT.grip;
    const gy = m.edge === 1 ? b.y + b.h - g.inset - g.h : b.y + g.inset;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.cx - g.w / 2, gy, g.w, g.h), g.h / 2, g.h / 2), paintOf(COLOR.ink, 0.7 * pillShow(m)));
  }
  c.restore();
}

/** Each block's capsule on the rail, as tall as the block, in its kind's colour. */
function drawCapsules(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  for (let i = 0; i < m.n; i++) {
    const b = boxOf(ctx, m, i, i === m.shown);
    const h = b.h * b.s;
    const w = LAYOUT.capsule;
    const y = b.cy - h / 2;
    const col = m.kind[i] === KIND.meeting ? '#6E727B' : kindColor(m.kind[i]);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(LAYOUT.railX - w / 2, y, w, h), w / 2, w / 2), paintOf(col));
  }
}

// ——— Bar ———

/** The bottom bar: the day's open time (rolling as blocks grow), and a glass "+" beside it. */
function drawBar(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { lay, fonts } = ctx;
  const bar = LAYOUT.bar;
  const y = lay.barTop;
  const plusX = lay.right - bar.plus;
  const w = plusX - 8 - LAYOUT.edge;
  glass(c, ctx, LAYOUT.edge, y, w, bar.h, bar.h / 2, TINT.chrome);
  glass(c, ctx, plusX, y, bar.plus, bar.h, bar.h / 2, TINT.chrome);
  const base = y + bar.h / 2 + 5;
  // "2h 15m open until 18:00": the figure rolls; its width is measured from where its right edge sits.
  const rest = ' open until 18:00';
  const rollRight = LAYOUT.edge + 20 + 64;
  drawRoll(c, ctx, m.open, rollRight, base + 1, COLOR.ink, m.reduced);
  text(c, rest, rollRight, base, fonts.barMute, COLOR.mute);
  const cx = plusX + bar.plus / 2;
  const cy = y + bar.h / 2;
  const pen = strokeOf(COLOR.ink, 2);
  c.drawLine(cx - 8, cy, cx + 8, cy, pen);
  c.drawLine(cx, cy - 8, cx, cy + 8, pen);
}

// ——— Screen ———

export function drawScene(c: SkCanvas, ctx: SceneCtx, m: Model) {
  'worklet';
  const { lay } = ctx;
  c.save();
  c.scale(1 / ctx.pageScale, 1 / ctx.pageScale);
  c.drawImage(ctx.page, 0, 0);
  c.restore();
  drawHeader(c, ctx, m);
  drawWeek(c, ctx);
  const ey = edgeY(ctx, m);
  drawLabels(c, ctx, m, ey);

  const live = layout(m, false);
  const top = m.shown >= 0 && m.lift > 0.001 ? m.shown : -1;
  drawShadows(c, ctx, m, -1, top);
  drawCapsules(c, ctx, m);
  for (let i = 0; i < m.n; i++) if (i !== top) drawBlock(c, ctx, m, i, false, live);
  if (top >= 0) {
    drawShadows(c, ctx, m, top, -1);
    drawBlock(c, ctx, m, top, true, live);
    const t = m.edge === 1 ? live[0][top] + live[1][top] : live[0][top];
    // The pill sits on the edge as drawn (lifted, scaled), not on the time line 2 pt beyond it.
    const b = boxOf(ctx, m, top, true);
    const py = m.edge === 1 ? b.cy + (b.h * b.s) / 2 : b.cy - (b.h * b.s) / 2;
    if (ey >= 0) timePill(c, ctx, Math.min(Math.max(py, lay.gridTop), yOf(lay, DAY.end)), clock(t), COLOR.focus, COLOR.onAccent, pillShow(m));
  }

  drawBar(c, ctx, m);

  // The demo's finger.
  if (m.ghostO > 0.01) {
    const r = (LAYOUT.finger / 2) * (1 - 0.12 * m.ghostP);
    c.drawCircle(m.ghostX, m.ghostY, r, paintOf(COLOR.finger, m.ghostO));
    c.drawCircle(m.ghostX, m.ghostY, r - 0.5, strokeOf(COLOR.fingerEdge, 1, m.ghostO));
  }
}
