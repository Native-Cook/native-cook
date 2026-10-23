/** The bubbles, the hold ring, the sparks and the quiet-hours dial: one Skia picture per frame from the sim. */
import {
  BlendMode, BlurStyle, Canvas, ClipOp, Image, LinearGradient, matchFont, PaintStyle, Picture, Rect, Skia, StrokeCap, TileMode, useImage, vec,
  type SkCanvas,
} from '@shopify/react-native-skia';
import { Platform, StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { COUNT, ENTITIES, QUIET_INDEX, type Sim } from './bubble-sim';
import { ATLAS, spriteIndex } from './glass-atlas';
import { BURST, LIQUID } from './motion';
import { COLOR, GLASS, HUE, LAYOUT, QUIET, QUIET_GLASS, TYPE } from './theme';

const atlasSource = require('./assets/glass-atlas.jpg');
const speckleSource = require('./assets/speckle.jpg');
const SPRITE_INDEX = ENTITIES.map((e) => spriteIndex(e.id));

type Props = { sim: SharedValue<Sim>; finger: SharedValue<{ x: number; y: number; alpha: number }>; originX: number; originY: number; width: number; height: number; personal: boolean };

const quietFont = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: TYPE.quiet, fontWeight: '700' });

function color(hex: string, alpha = 1) {
  'worklet';
  const c = Skia.Color(hex);
  return Skia.Color([c[0], c[1], c[2], c[3] * alpha]);
}

function paint(hex: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color(hex, alpha));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], stops: number[], alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const cs = [];
  for (let i = 0; i < colors.length; i++) cs.push(color(colors[i], alpha));
  p.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, cs, stops, TileMode.Clamp));
  return p;
}

/** Radial gradient with a separate alpha per stop. */
function radialA(x: number, y: number, r: number, colors: string[], alphas: number[], stops: number[]) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const cs = [];
  for (let i = 0; i < colors.length; i++) cs.push(color(colors[i], alphas[i]));
  p.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, cs, stops, TileMode.Clamp));
  return p;
}

/** Text set along a circle, one glyph at a time, centred on `mid` (radians, 0 = right, clockwise). */
function arcText(canvas: SkCanvas, text: string, cx: number, cy: number, r: number, mid: number, inward: boolean, p: ReturnType<typeof paint>) {
  'worklet';
  const widths: number[] = [];
  let total = 0;
  for (let i = 0; i < text.length; i++) {
    const w = quietFont.measureText(text[i]).width + 1.2;
    widths.push(w);
    total += w;
  }
  const dir = inward ? 1 : -1;
  let a = mid - (dir * total / r) / 2;
  for (let i = 0; i < text.length; i++) {
    const step = widths[i] / r;
    const at = a + dir * step / 2;
    canvas.save();
    canvas.translate(cx + Math.cos(at) * r, cy + Math.sin(at) * r);
    canvas.rotate(((at + (inward ? Math.PI / 2 : -Math.PI / 2)) * 180) / Math.PI, 0, 0);
    canvas.drawText(text[i], -widths[i] / 2 + 0.6, inward ? 0 : quietFont.getSize() * 0.7, p, quietFont);
    canvas.restore();
    a += dir * step;
  }
}

function drawQuiet(canvas: SkCanvas, x: number, y: number, r: number) {
  'worklet';
  if (r < 1) return;
  const k = r / QUIET.r;
  canvas.drawCircle(x, y, r * 1.25, radial(x, y, r * 1.25, [COLOR.quiet, COLOR.quiet, COLOR.bg], [0, 0.55, 1], 0.12));
  // A dim, translucent dark-red disc, as in the source.
  canvas.drawCircle(x, y, r, radial(x, y, r, [COLOR.quietCore, COLOR.quietCore, COLOR.quiet], [0, 0.72, 1], QUIET_GLASS.body));
  // The window of quiet hours as a lit wedge.
  const wedge = Skia.PathBuilder.Make().moveTo(x, y).arcToOval(Skia.XYWHRect(x - r, y - r, r * 2, r * 2), 150, 140, false).close().build();
  canvas.drawPath(wedge, paint(COLOR.quiet, QUIET_GLASS.wedge));
  const ink = paint(COLOR.ink, 0.85);
  arcText(canvas, QUIET.title, x, y, r * 0.68, -Math.PI / 2 + 0.25, true, ink);
  arcText(canvas, QUIET.hours, x, y, r * 0.68 + 7 * k, Math.PI / 2 - 0.35, false, ink);
  canvas.drawCircle(x, y, 3.5 * k, paint(COLOR.ink));
  canvas.drawCircle(x, y, 11 * k, (() => { const p = paint(COLOR.ink, 0.25); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(1); return p; })());
}

export function BubbleCanvas({ sim, finger, originX, originY, width, height, personal }: Props) {
  const atlas = useImage(atlasSource);
  const speckle = useImage(speckleSource);
  const picture = useDerivedValue(() => {
    const s = sim.get();
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.save();
    canvas.translate(originX, originY);
    // Glass sprites sampled from the source's own bubbles (one per habit, scaled with the bubble's radius), drawn
    // with a 'lighten' blend so each glow adds to the dark field and to its neighbours' glow, as light does.
    const glass = atlas !== null;
    if (glass) {
      const lighten = Skia.Paint();
      lighten.setAntiAlias(true);
      lighten.setBlendMode(BlendMode.Lighten);
      for (let i = 0; i < COUNT; i++) {
        const r = s.r[i];
        if (r < 1 || i === QUIET_INDEX) continue;
        const k = SPRITE_INDEX[i];
        const half = r * ATLAS.reach;
        canvas.drawImageRect(atlas, Skia.XYWHRect((k % ATLAS.cols) * ATLAS.size, Math.floor(k / ATLAS.cols) * ATLAS.size, ATLAS.size, ATLAS.size),
          Skia.XYWHRect(s.x[i] - half, s.y[i] - half, half * 2, half * 2), lighten);
      }
    }
    // Halos first, so a neighbour's glow never sits on top of a bubble.
    for (let i = 0; i < (glass ? 0 : COUNT); i++) {
      const r = s.r[i];
      if (r < 1 || i === QUIET_INDEX) continue;
      const h = HUE[ENTITIES[i].hue];
      canvas.drawCircle(s.x[i], s.y[i], r * 1.45, radialA(s.x[i], s.y[i], r * 1.45, [h.halo, h.halo, h.halo, h.halo], [0, 0, GLASS.halo, 0], [0, 0.68, 0.76, 1]));
    }
    drawQuiet(canvas, s.x[QUIET_INDEX], s.y[QUIET_INDEX], s.r[QUIET_INDEX]);
    for (let i = 0; i < COUNT; i++) {
      const r = s.r[i];
      if (r < 1 || i === QUIET_INDEX) continue;
      const h = HUE[ENTITIES[i].hue];
      const x = s.x[i];
      const y = s.y[i];
      const d = s.done[i];
      const charge = s.ring === i ? s.charge : 0;
      // Glass, as in the source: a see-through interior faintly tinted by the hue (~20 %), the colour gathering
      // into a luminous band at 0.9 r, a little falloff at the very edge; charging lifts the interior.
      // Measured on the source: small bubbles are filled with their glow, large ones stay dark inside.
      const base = ENTITIES[i].r;
      const fill = Math.min(GLASS.interiorSmall, Math.max(GLASS.interiorLarge, GLASS.interiorSmall - (base - GLASS.interiorFrom) * GLASS.interiorSlope));
      const band = base > GLASS.bigRadius ? GLASS.bandLarge : GLASS.bandSmall;
      const tint = fill + GLASS.chargeLift * charge;
      // Key light from the lower left, as in the source: the gradient's centre sits up and to the right, so the lit
      // band runs wider along the lower-left rim and thins out at the top right.
      if (glass) {
        // Hold to complete: liquid light rises inside the glass with a moving meniscus (two sines on the sim clock).
        if (charge > 0) {
          const level = y + r - 2 * r * charge;
          const path = Skia.PathBuilder.Make();
          const steps = 24;
          for (let k = 0; k <= steps; k++) {
            const px = x - r + (2 * r * k) / steps;
            const wave = Math.sin(s.clock / LIQUID.period1 + k * 0.55) * LIQUID.amp1 + Math.sin(s.clock / LIQUID.period2 - k * 0.9) * LIQUID.amp2;
            const py = level + wave * Math.sin(Math.PI * charge);
            if (k === 0) path.moveTo(px, py);
            else path.lineTo(px, py);
          }
          path.lineTo(x + r, y + r).lineTo(x - r, y + r).close();
          const liquid = path.build();
          canvas.save();
          canvas.clipPath(Skia.PathBuilder.Make().addCircle(x, y, r - 1).build(), ClipOp.Intersect, true);
          const fill = Skia.Paint();
          fill.setAntiAlias(true);
          fill.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(x, level - LIQUID.amp1), Skia.Point(x, y + r),
            [color(h.light, LIQUID.top), color(h.rim, LIQUID.bottom)], [0, 1], TileMode.Clamp));
          canvas.drawPath(liquid, fill);
          canvas.restore();
        }
      } else {
        canvas.drawCircle(x, y, r, radialA(x + r * GLASS.lightX, y + r * GLASS.lightY, r * GLASS.lightR, [h.inner, h.inner, h.rim, h.light, h.rim], [tint, tint, band, GLASS.rimLight, GLASS.edge], [0, 0.5, 0.86, 0.96, 1]));
        // A soft specular bloom top-left.
        canvas.drawCircle(x - r * 0.32, y - r * 0.38, r * 0.62, radialA(x - r * 0.32, y - r * 0.38, r * 0.62, ['#FFFFFF', '#FFFFFF'], [GLASS.highlight, 0], [0, 1]));
      }
      if (d > 0.01) canvas.drawCircle(x, y, r, radial(x - r * 0.2, y - r * 0.25, r * 1.2, ['#FFFFFF', h.rim, h.rim], [0, 0.45, 1], d * 0.95));
      {
        // A crisp edge on the glass (the sampled sprite is soft at its rim).
        const rim = paint(glass ? h.light : h.rim, glass ? (ENTITIES[i].hue === 'glass' ? GLASS.spriteEdgeGlass : GLASS.spriteEdge) : 0.35);
        rim.setStyle(PaintStyle.Stroke);
        rim.setStrokeWidth(1.5);
        canvas.drawCircle(x, y, r - 0.75, rim);
      }
      // (The source has no hard specular arc; its highlight is the soft bloom above.)
      const f = s.flash[i];
      if (f >= 0) {
        const u = f / BURST.flashMs;
        const flash = paint('#FFFFFF', 1 - u);
        flash.setStyle(PaintStyle.Stroke);
        flash.setStrokeWidth(3 * (1 - u) + 0.5);
        canvas.drawCircle(x, y, r + 4 * u, flash);
      }
    }
    // The hold ring, drawn round the bubble as it charges (and draining when let go early).
    if (s.ring >= 0 && s.charge > 0.001) {
      const i = s.ring;
      const h = HUE[ENTITIES[i].hue];
      const rr = s.r[i] + LAYOUT.ringGap;
      const arc = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(s.x[i] - rr, s.y[i] - rr, rr * 2, rr * 2), -90, 360 * s.charge).build();
      const glow = paint(h.rim, 0.8);
      glow.setStyle(PaintStyle.Stroke);
      glow.setStrokeWidth(LAYOUT.ring + 4);
      glow.setStrokeCap(StrokeCap.Round);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
      canvas.drawPath(arc, glow);
      const ring = paint('#FFFFFF', 0.95);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(LAYOUT.ring);
      ring.setStrokeCap(StrokeCap.Round);
      canvas.drawPath(arc, ring);
    }
    if (s.burst >= 0) {
      const i = s.burst;
      const u = s.burstMs / BURST.sparkMs;
      const out = 1 - Math.pow(1 - u, 3);
      const h = HUE[ENTITIES[i].hue];
      for (let k = 0; k < LAYOUT.sparks; k++) {
        const a = (k / LAYOUT.sparks) * Math.PI * 2 + i;
        const far = k % 2 === 0 ? BURST.sparkFar : BURST.sparkNear;
        const dist = s.r[i] + BURST.sparkFrom + far * out;
        canvas.drawCircle(s.x[i] + Math.cos(a) * dist, s.y[i] + Math.sin(a) * dist, (k % 2 === 0 ? 2.6 : 1.8) * (1 - u * 0.6), paint(k % 3 === 0 ? COLOR.spark : h.rim, 1 - u));
      }
    }
    const fg = finger.get();
    if (fg.alpha > 0.01) {
      canvas.drawCircle(fg.x, fg.y, LAYOUT.fingerRadius, paint(COLOR.finger, fg.alpha));
      const ring = paint(COLOR.fingerRing, fg.alpha);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(1.5);
      canvas.drawCircle(fg.x, fg.y, LAYOUT.fingerRadius, ring);
    }
    canvas.restore();
    return rec.finishRecordingAsPicture();
  });
  // The background lives in this canvas so the glass sprites' 'lighten' blend has the real field beneath it.
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    {speckle && <Image image={speckle} x={0} y={0} width={width} height={height} fit="cover" />}
    {personal && <Rect x={0} y={0} width={width} height={height}>
      <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[COLOR.personalTop, COLOR.personalBottom]} />
    </Rect>}
    <Picture picture={picture} />
  </Canvas>;
}
