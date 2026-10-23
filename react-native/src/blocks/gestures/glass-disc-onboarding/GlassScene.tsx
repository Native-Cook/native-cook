/** The blush backdrop, and over the screen's text the glass lens and the bubble cloud, drawn on the UI thread. */
import {
  Canvas, Circle, Fill, ImageShader, matchFont, PaintStyle, Picture, RadialGradient, Shader, Skia, vec, type SkImage,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { PixelRatio, StyleSheet } from 'react-native';
import { SensorType, useAnimatedSensor, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { backdropEffect, lensEffect, rgb } from './lens-shader';
import { BURST, UPGRADE } from './motion';
import { HIDDEN, type Stickers } from './sticker-sim';
import { ATLAS, COLOR, COPY, FONT, LAYOUT, TYPE } from './theme';
import type { DiscValues } from './use-glass-disc';

// The lens texture is twice the screen's height; 2× is sharp enough under glass and halves its memory.
const RATIO = Math.min(2, PixelRatio.get());

/** Where the second screen's buttons sit (points), shared by the views and the lens texture. */
export function buttonRects(width: number, height: number) {
  const w = width - LAYOUT.gutter * 2;
  const darkY = height - LAYOUT.buttonsBottom - LAYOUT.buttonHeightDark;
  const lightY = darkY - LAYOUT.buttonGap - LAYOUT.buttonHeight;
  return { x: LAYOUT.gutter, w, lightY, darkY };
}

/**
 * The texture the lens looks through. Top half: the second screen's text and buttons where they stand (the bead
 * and the rewind refract them). Bottom half: the headline alone, centred on (W/2, 1.5 H), which the rising lens shows.
 */
function makeTexture(width: number, height: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.round(width * RATIO), Math.round(height * 2 * RATIO));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(RATIO, RATIO);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(COLOR.lensInk));
  const centred = (text: string, font: ReturnType<typeof matchFont>, y: number, paint = ink) => {
    c.drawText(text, (width - font.measureText(text).width) / 2, y, paint, font);
  };
  const head = matchFont({ fontFamily: FONT, fontSize: TYPE.headline, fontWeight: '400' });
  const base = height * LAYOUT.headlineBaseline;
  centred(COPY.headline, head, base);
  COPY.sub.forEach((line, i) => centred(line, head, base + TYPE.headlineLine * (i + 1)));
  const b = buttonRects(width, height);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(Skia.Color(COLOR.buttonLight));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x, b.lightY, b.w, LAYOUT.buttonHeight), LAYOUT.buttonHeight / 2, LAYOUT.buttonHeight / 2), fill);
  fill.setColor(Skia.Color(COLOR.buttonDark));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x, b.darkY, b.w, LAYOUT.buttonHeightDark), LAYOUT.buttonHeightDark / 2, LAYOUT.buttonHeightDark / 2), fill);
  const label = matchFont({ fontFamily: FONT, fontSize: TYPE.button.fontSize, fontWeight: '500' });
  const mid = LAYOUT.buttonHeight / 2 + TYPE.button.fontSize * 0.35;
  centred(COPY.email, label, b.lightY + mid);
  const white = Skia.Paint();
  white.setAntiAlias(true);
  white.setColor(Skia.Color(COLOR.buttonDarkInk));
  centred(COPY.phone, label, b.darkY + LAYOUT.buttonHeightDark / 2 + TYPE.button.fontSize * 0.35, white);
  const lone = matchFont({ fontFamily: FONT, fontSize: TYPE.lens, fontWeight: '500' });
  centred(COPY.lens, lone, height * 1.5);
  return surface.makeImageSnapshot();
}

const BG = { bgTL: rgb(COLOR.bgTL), bgTR: rgb(COLOR.bgTR), bgBL: rgb(COLOR.bgBL), bgBR: rgb(COLOR.bgBR) };
const SHADOW = rgb(COLOR.shadow);
const RING = [rgb(COLOR.ringInner), rgb(COLOR.ringCore), rgb(COLOR.ringOuter), rgb(COLOR.ringBlush)];

/** The blush backdrop and, once the second screen is in, a pre-blurred glow field in the band's hues behind it. */
export function Backdrop({ width, height, values }: { width: number; height: number; values: DiscValues }) {
  const uniforms = useMemo(() => ({ size: [width, height], ...BG }), [height, width]);
  const { headIn, clock } = values;
  const fieldY = height * LAYOUT.beadRestY;
  const fieldC = useDerivedValue(() => vec(width / 2 + UPGRADE.fieldDrift * Math.sin((clock.get() / UPGRADE.fieldPeriodMs) * Math.PI * 2), fieldY));
  const fieldOpacity = useDerivedValue(() => headIn.get());
  if (!backdropEffect) return null;
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Fill>
      <Shader source={backdropEffect} uniforms={uniforms} />
    </Fill>
    <Circle c={fieldC} r={width * 0.8} opacity={fieldOpacity}>
      <RadialGradient c={fieldC} r={width * 0.8} colors={[COLOR.fieldA, COLOR.fieldB, 'rgba(176,92,255,0)']} positions={[0, 0.45, 1]} />
    </Circle>
  </Canvas>;
}

type Props = {
  width: number;
  height: number;
  values: DiscValues;
  stickers: SharedValue<Stickers>;
  finger: SharedValue<{ x: number; y: number; alpha: number }>;
  atlas: SkImage | null;
};

export function GlassLayer({ width, height, values, stickers, finger, atlas }: Props) {
  const texture = useMemo(() => makeTexture(width, height), [height, width]);
  const { discX, discY, discR, ring, reveal, texMode, introAlpha, nudge, stretch, glint, glintAng, spreadBoost, squash, clock, landAt } = values;
  const L = LAYOUT.lens;
  // The highlight follows the phone's tilt; the simulator has none, so it also leans towards the finger.
  const tilt = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const uniforms = useDerivedValue(() => {
    const R = discR.get() * (1 - squash.get());
    const cy = discY.get() - nudge.get();
    const rot = tilt.sensor.get();
    const f = finger.get();
    const lx = Math.max(-1, Math.min(1, rot.roll * 1.5 + (f.alpha > 0.5 ? (f.x - width / 2) / width : 0)));
    const ly = Math.max(-1, Math.min(1, rot.pitch * 1.5));
    // Rising: the lens looks at the lone headline; bead and rewind: at the screen in place.
    const lone = texMode.get() < 0.5;
    const cx = discX.get();
    const origin = lone ? [width / 2 - L.tx * R, height * 1.5 - L.ty * R] : [cx, cy];
    return {
      size: [width, height],
      texScale: RATIO,
      center: [cx, cy],
      radius: R,
      alpha: 1,
      stretch: stretch.get(),
      glint: glint.get(),
      glintAng: glintAng.get(),
      light: [lx, ly],
      glow: UPGRADE.glow,
      spreadBoost: spreadBoost.get(),
      texOrigin: origin,
      reveal: reveal.get(),
      fold: L.fold,
      foldBack: L.foldBack,
      foldShape: L.foldShape,
      kY: L.kY,
      kX: L.kX,
      squeezeX: L.squeezeX,
      spread: L.spread,
      fringeFrom: L.fringeFrom,
      fade: L.fade,
      mirrorFloor: L.mirrorFloor,
      ring: ring.get(),
      ringFrom: LAYOUT.ringFrom,
      ringTo: LAYOUT.ringTo,
      rimLight: LAYOUT.rimLight,
      rimWidth: LAYOUT.rimWidth,
      shadowAt: LAYOUT.shadowAt,
      shadowWidth: LAYOUT.shadowWidth,
      shadowAlpha: LAYOUT.shadowAlpha,
      beadFrom: LAYOUT.bead.from,
      beadSpan: LAYOUT.bead.span,
      beadFrost: LAYOUT.bead.frost,
      domeFrost: LAYOUT.domeFrost * introAlpha.get(),
      ...BG,
      shadowColor: SHADOW,
      ringInner: RING[0],
      ringCore: RING[1],
      ringOuter: RING[2],
      ringBlush: RING[3],
    };
  });
  const texRect = useMemo(() => Skia.XYWHRect(0, 0, width * RATIO, height * 2 * RATIO), [height, width]);

  const cloud = useDerivedValue(() => {
    const s = stickers.get();
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, width, height));
    // Payoff: pressure rings leave the bead with the bubbles.
    const since = clock.get() - landAt.get() - BURST.delayMs;
    if (since > 0 && since < UPGRADE.pulseMs + UPGRADE.pulses[UPGRADE.pulses.length - 1]) {
      const ringPaint = Skia.Paint();
      ringPaint.setAntiAlias(true);
      ringPaint.setStyle(PaintStyle.Stroke);
      ringPaint.setColor(Skia.Color(COLOR.pulse));
      for (let k = 0; k < UPGRADE.pulses.length; k++) {
        const u = (since - UPGRADE.pulses[k]) / UPGRADE.pulseMs;
        if (u <= 0 || u >= 1) continue;
        const e = 1 - Math.pow(1 - u, 3);
        ringPaint.setStrokeWidth(2.2 * (1 - u) + 0.4);
        ringPaint.setAlphaf(0.7 * (1 - u));
        canvas.drawCircle(discX.get(), discY.get(), discR.get() + UPGRADE.pulseGrow * e, ringPaint);
      }
    }
    if (s.phase !== HIDDEN && atlas) {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      for (let i = 0; i < s.x.length; i++) {
        const r = s.r[i] * s.scale[i];
        if (r < 0.5) continue;
        const col = s.cell[i] % ATLAS.columns;
        const row = Math.floor(s.cell[i] / ATLAS.columns) % ATLAS.rows;
        // Soft glass: a bubble stretches along its flight and settles round.
        const sp = Math.hypot(s.vx[i], s.vy[i]);
        const w = Math.min(UPGRADE.wobbleMax, sp * UPGRADE.wobblePerSpeed);
        const a = (Math.atan2(s.vy[i], s.vx[i]) * 180) / Math.PI;
        canvas.save();
        canvas.translate(s.x[i], s.y[i]);
        if (w > 0.005) {
          canvas.rotate(a, 0, 0);
          canvas.scale(1 + w, 1 - w);
          canvas.rotate(-a, 0, 0);
        }
        canvas.drawImageRect(atlas,
          Skia.XYWHRect(col * ATLAS.cell, row * ATLAS.cell, ATLAS.cell, ATLAS.cell),
          Skia.XYWHRect(-r, -r, r * 2, r * 2), paint);
        canvas.restore();
      }
    }
    const f = finger.get();
    if (f.alpha > 0.01) {
      const dot = Skia.Paint();
      dot.setAntiAlias(true);
      dot.setColor(Skia.Color(COLOR.finger));
      dot.setAlphaf(f.alpha * 0.45);
      canvas.drawCircle(f.x, f.y, LAYOUT.fingerRadius, dot);
      const edge = Skia.Paint();
      edge.setAntiAlias(true);
      edge.setStyle(PaintStyle.Stroke);
      edge.setStrokeWidth(1.5);
      edge.setColor(Skia.Color(COLOR.fingerRing));
      edge.setAlphaf(f.alpha * 0.35);
      canvas.drawCircle(f.x, f.y, LAYOUT.fingerRadius, edge);
    }
    return rec.finishRecordingAsPicture();
  });

  if (!lensEffect) return null;
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Fill>
      <Shader source={lensEffect} uniforms={uniforms}>
        <ImageShader image={texture} tx="decal" ty="decal" fit="none" rect={texRect} />
      </Shader>
    </Fill>
    <Picture picture={cloud} />
  </Canvas>;
}
