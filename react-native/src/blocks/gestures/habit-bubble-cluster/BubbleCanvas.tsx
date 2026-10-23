/**
 * The bubbles as one Skia picture per frame from the sim: each is a lens of Liquid Glass over a calm page (made once
 * at mount), tinted by its habit's colour. Holding one fills it with liquid, its surface moving, a thin ring of light
 * closing round it; done, it stays full under a check. A tap shows the habit's name on a glass label above it.
 */
import {
  Canvas, ClipOp, FilterMode, MipmapMode, PaintStyle, Picture, Skia, StrokeCap, TileMode, createPicture, useTypeface,
  type SkCanvas, type SkFont, type SkImage, type SkRuntimeEffect, type SkShader,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { PixelRatio, StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { makeBackdrop } from './backdrop';
import { COUNT, ENTITIES, QUIET_INDEX, type Sim } from './bubble-sim';
import { GLASS_EFFECT } from './glass-shader';
import { LABEL, LIQUID, RIPPLE } from './motion';
import { COLOR, HUE, LAYOUT, QUIET, TYPE } from './theme';

type Props = {
  sim: SharedValue<Sim>;
  finger: SharedValue<{ x: number; y: number; alpha: number }>;
  label: SharedValue<{ i: number; at: number }>;
  originX: number;
  originY: number;
  width: number;
  height: number;
  reduced: boolean;
};

type Ctx = {
  page: SkImage;
  pageScale: number;
  frost: SkShader;
  glass: SkRuntimeEffect;
  label: SkFont;
  labelSub: SkFont;
  quiet: SkFont;
};

// ——— Helpers (declared before the worklets that call them) ———

function paint(hex: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(hex));
  if (alpha < 1) p.setAlphaf(p.getAlphaf() * Math.max(alpha, 0));
  return p;
}

function stroke(hex: string, width: number, alpha = 1) {
  'worklet';
  const p = paint(hex, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function advance(font: SkFont, s: string): number {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function easeOut(u: number): number {
  'worklet';
  const c = clamp01(u);
  return 1 - Math.pow(1 - c, 3);
}

/** A disc (or a capsule) of glass at (x, y). */
function glass(c: SkCanvas, ctx: Ctx, x: number, y: number, w: number, h: number, r: number, tint: readonly number[], strength: number, thick: number, glow: number, gx = x + w / 2, gy = y + h / 2) {
  'worklet';
  const p = Skia.Paint();
  p.setShader(ctx.glass.makeShaderWithChildren(
    [x, y, w, h, r, tint[0], tint[1], tint[2], strength, thick, gx, gy, glow, 1], [ctx.frost],
  ));
  c.drawRect(Skia.XYWHRect(x - 1, y - 1, w + 2, h + 2), p);
}

/** Text set along a circle, one glyph at a time, centred on `mid` (radians, 0 = right, clockwise). */
function arcText(c: SkCanvas, font: SkFont, text: string, cx: number, cy: number, r: number, mid: number, inward: boolean, color: string, alpha: number) {
  'worklet';
  const widths: number[] = [];
  let total = 0;
  for (let i = 0; i < text.length; i++) {
    const w = advance(font, text[i]) + 1.2;
    widths.push(w);
    total += w;
  }
  const dir = inward ? 1 : -1;
  let a = mid - (dir * total / r) / 2;
  const p = paint(color, alpha);
  for (let i = 0; i < text.length; i++) {
    const step = widths[i] / r;
    const at = a + (dir * step) / 2;
    c.save();
    c.translate(cx + Math.cos(at) * r, cy + Math.sin(at) * r);
    c.rotate(((at + (inward ? Math.PI / 2 : -Math.PI / 2)) * 180) / Math.PI, 0, 0);
    c.drawText(text[i], -widths[i] / 2 + 0.6, inward ? 0 : font.getSize() * 0.7, p, font);
    c.restore();
    a += dir * step;
  }
}

/** The quiet-hours dial: clear glass, the window of quiet as a darker wedge, its words round the rim. */
function drawQuiet(c: SkCanvas, ctx: Ctx, x: number, y: number, r: number) {
  'worklet';
  if (r < 1) return;
  const k = r / QUIET.r;
  glass(c, ctx, x - r, y - r, r * 2, r * 2, r, HUE.glass.tint, 0.04, 0.2, 0);
  const wedge = Skia.PathBuilder.Make().moveTo(x, y).arcToOval(Skia.XYWHRect(x - r * 0.84, y - r * 0.84, r * 1.68, r * 1.68), 150, 140, false).close().build();
  c.drawPath(wedge, paint('#000000', 0.22));
  arcText(c, ctx.quiet, QUIET.title, x, y, r * 0.68, -Math.PI / 2 + 0.25, true, COLOR.ink, 0.8);
  arcText(c, ctx.quiet, QUIET.hours, x, y, r * 0.68 + 7 * k, Math.PI / 2 - 0.35, false, COLOR.ink, 0.8);
  c.drawCircle(x, y, 3 * k, paint(COLOR.ink, 0.9));
}

/** The liquid inside bubble i, filled to `level` (0..1), its surface moving with two sines on the sim clock. */
function drawLiquid(c: SkCanvas, x: number, y: number, r: number, level: number, top: string, bottom: string, clock: number, calm: number) {
  'worklet';
  if (level <= 0.002) return;
  const surface = y + r - 2 * r * level;
  const path = Skia.PathBuilder.Make();
  const steps = 24;
  for (let k = 0; k <= steps; k++) {
    const px = x - r + (2 * r * k) / steps;
    const wave = Math.sin(clock / LIQUID.period1 + k * 0.55) * LIQUID.amp1 + Math.sin(clock / LIQUID.period2 - k * 0.9) * LIQUID.amp2;
    const py = surface + wave * Math.sin(Math.PI * clamp01(level)) * calm;
    if (k === 0) path.moveTo(px, py);
    else path.lineTo(px, py);
  }
  path.lineTo(x + r, y + r).lineTo(x - r, y + r).close();
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(x, y, r - 1.5).build(), ClipOp.Intersect, true);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x, y: surface - 4 }, { x, y: y + r }, [Skia.Color(top), Skia.Color(bottom)], [0, 1], TileMode.Clamp));
  fill.setAlphaf(0.92);
  c.drawPath(path.build(), fill);
  // The meniscus catches the light: a bright hairline along the surface.
  c.restore();
}

export function BubbleCanvas({ sim, finger, label, originX, originY, width, height, reduced }: Props) {
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  const ctx: Ctx | null = useMemo(() => {
    if (!regular || !semibold) return null;
    const back = makeBackdrop(width, height, PixelRatio.get());
    if (!back) return null;
    // The frost is sampled in cluster coordinates (the picture is drawn translated to the cluster's centre).
    const local = Skia.Matrix();
    local.translate(-originX, -originY);
    local.scale(1 / back.frostScale, 1 / back.frostScale);
    const frost = back.frosted.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, local);
    return {
      page: back.sharp, pageScale: back.sharpScale, frost, glass: GLASS_EFFECT,
      label: Skia.Font(semibold, TYPE.label), labelSub: Skia.Font(regular, TYPE.labelSub), quiet: Skia.Font(semibold, TYPE.quiet),
    };
  }, [height, originX, originY, regular, semibold, width]);

  const picture = useDerivedValue(() => {
    const s = sim.get();
    const fg = finger.get();
    const lb = label.get();
    return createPicture((c) => {
      if (!ctx) return;
      c.save();
      c.scale(1 / ctx.pageScale, 1 / ctx.pageScale);
      c.drawImage(ctx.page, 0, 0);
      c.restore();
      c.save();
      c.translate(originX, originY);
      drawQuiet(c, ctx, s.x[QUIET_INDEX], s.y[QUIET_INDEX], s.r[QUIET_INDEX]);
      for (let i = 0; i < COUNT; i++) {
        const r = s.r[i];
        if (r < 1 || i === QUIET_INDEX) continue;
        const h = HUE[ENTITIES[i].hue];
        const x = s.x[i];
        const y = s.y[i];
        const charge = s.ring === i ? s.charge : 0;
        const done = s.done[i];
        const held = s.held === i ? 1 : 0;
        // The glass: tinted a little more as it fills; a held bubble's glass lights from the finger.
        glass(c, ctx, x - r, y - r, r * 2, r * 2, r, h.tint, 0.17 + 0.1 * Math.max(charge, done), 0.3 + 0.5 * held, reduced ? 0 : 0.8 * held, s.fx, s.fy);
        const level = Math.max(charge, done);
        drawLiquid(c, x, y, r, level, h.top, h.bottom, s.clock, reduced ? 0 : 1 - done);
        // Light caught on the liquid's surface, and the rim over it, so the liquid reads as inside the glass.
        if (level > 0.002) c.drawCircle(x, y, r - 0.75, stroke('#FFFFFF', 1.2, 0.22 + 0.2 * level));
        // Completion: a ring of light leaves the bubble and fades.
        if (s.burst === i && !reduced) {
          const u = clamp01(s.burstMs / RIPPLE.ms);
          c.drawCircle(x, y, r + 4 + RIPPLE.reach * easeOut(u), stroke(h.top, 1.5 * (1 - u) + 0.5, 0.7 * (1 - u)));
        }
      }
      // The hold ring: a thin line of light closing round the bubble as it fills.
      if (s.ring >= 0 && s.charge > 0.001) {
        const i = s.ring;
        const rr = s.r[i] + LAYOUT.ringGap;
        const arc = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(s.x[i] - rr, s.y[i] - rr, rr * 2, rr * 2), -90, 360 * s.charge).build();
        c.drawCircle(s.x[i], s.y[i], rr, stroke('#FFFFFF', LAYOUT.ring, 0.12));
        c.drawPath(arc, stroke('#FFFFFF', LAYOUT.ring, 0.9));
      }
      // A tapped habit's name, on a glass label above it: in, held, out.
      if (lb.i >= 0) {
        const e = s.clock - lb.at;
        const inU = reduced ? 1 : easeOut(e / LABEL.inMs);
        const outU = clamp01((e - LABEL.inMs - LABEL.holdMs) / LABEL.outMs);
        const a = Math.min(clamp01(e / LABEL.inMs), 1 - outU);
        if (a > 0.004) {
          const i = lb.i;
          const name = ENTITIES[i].name;
          const sub = s.doneTarget[i] > 0.5 ? 'Done today · tap to reopen' : 'Hold to mark it done';
          const w = Math.max(advance(ctx.label, name), advance(ctx.labelSub, sub)) + 32;
          const hgt = LAYOUT.labelH;
          let lx = s.x[i] - w / 2;
          lx = Math.max(-originX + LAYOUT.gutter, Math.min(lx, width - originX - LAYOUT.gutter - w));
          const ly = s.y[i] - s.r[i] - LAYOUT.labelGap - hgt + (reduced ? 0 : LABEL.rise * (1 - inU));
          const layer = Skia.Paint();
          layer.setAlphaf(a);
          c.saveLayer(layer, Skia.XYWHRect(lx - 4, ly - 4, w + 8, hgt + 8));
          // Solid, not glass: it sits over other bubbles, and glass would show the page through them.
          const box = Skia.RRectXY(Skia.XYWHRect(lx, ly, w, hgt), 16, 16);
          const shadow = paint('#000000', 0.5);
          shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 10, true));
          c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lx + 4, ly + 8, w - 8, hgt - 4), 16, 16), shadow);
          c.drawRRect(box, paint('#1A1D21'));
          c.drawRRect(box, stroke('#FFFFFF', 1, 0.12));
          c.drawText(name, lx + 16, ly + 22, paint(COLOR.ink), ctx.label);
          c.drawText(sub, lx + 16, ly + 40, paint(COLOR.ink, 0.62), ctx.labelSub);
          c.restore();
        }
      }
      if (fg.alpha > 0.01) {
        c.drawCircle(fg.x, fg.y, LAYOUT.fingerRadius, paint(COLOR.finger, fg.alpha));
        c.drawCircle(fg.x, fg.y, LAYOUT.fingerRadius - 0.5, stroke(COLOR.fingerRing, 1, fg.alpha));
      }
      c.restore();
    });
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
