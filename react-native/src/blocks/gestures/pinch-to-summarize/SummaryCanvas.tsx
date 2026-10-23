/**
 * The two Skia layers: the gradient field under the page, and the light above it (the AI rim glued to the
 * page's edge, the bloom when the summary lands, and the autoplay's finger marks). While the summary is made the
 * picked sentences pulse on the page itself (no shimmer, no scan band).
 */
import {
  Blur, BlurMask, Canvas, Circle, Group, Image, Picture, RoundedRect, Skia, SweepGradient, useImage, vec,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { clamp01, easeOut, pageFrame, type PageFrame, type Stage } from './geometry';
import { QA_HOLD, TIMING } from './motion';
import { COLOR, LAYOUT } from './theme';
import type { SummaryValues } from './use-summary-motion';

// QA (pixel gate) shows the replica only: the upgrade's light layers are off while a state is held.
const QA_REPLICA = QA_HOLD !== null;
const fieldSource = require('./assets/field.png');
const fieldBSource = require('./assets/field-b.png');

// Below this progress the opaque page covers the whole field. Nothing of the field may change then: a Skia canvas that
// redraws while it is covered gets no drawable back from the compositor, and after three such frames
// `CAMetalLayer.nextDrawable` blocks the main thread for its full one-second timeout (the freezes at the start of a
// pinch and at the end of every return).
const COVERED_P = 0.002;
const NO_SCALE = [{ scale: 1 }];

type Props = { stage: Stage; values: SummaryValues };
type FieldFrame = PageFrame & { p: number; covered: boolean };

export function SummaryField({ stage, values }: Props) {
  const { p, lift, driftX, driftY, breath, pageAlpha, clock, think } = values;
  // The source's gradient field, baked from its frame (a smooth image, so no shader is needed for the replica);
  // it breathes a little (scale ±1.5 %) so the field is never dead.
  const field = useImage(fieldSource);
  const fieldB = useImage(fieldBSource);
  // The field's one frame of the page: every Skia value below derives from it (or from the gated clock), and while
  // the page covers the field it is one constant object, so the canvas does not redraw at all.
  const rest = useMemo<FieldFrame>(() => ({ ...pageFrame(stage, 0, 0, 0, 0), p: 0, covered: true }), [stage]);
  const frame = useDerivedValue<FieldFrame>(() => {
    const pv = p.get();
    if (pv <= COVERED_P && pageAlpha.get() >= 1) return rest;
    return { ...pageFrame(stage, pv, lift.get(), driftX.get(), driftY.get(), breath.get()), p: pv, covered: false };
  });
  const fieldClock = useDerivedValue(() => (p.get() <= COVERED_P && pageAlpha.get() >= 1 ? -1 : clock.get()));
  const fieldLift = useDerivedValue(() => (p.get() <= COVERED_P && pageAlpha.get() >= 1 ? 0 : lift.get()));
  const fieldTransform = useDerivedValue(() => {
    const t = fieldClock.get();
    if (t < 0) return NO_SCALE;
    return [{ scale: 1 + TIMING.fieldBreath * Math.sin((t / TIMING.fieldPeriod) * Math.PI * 2) }];
  });
  // Depth cue (the source shows the page stacked on another): the same card one step further back, i.e. the
  // front card's exact frame (breathing included) scaled about its centre line, radius scaled with it. It stays
  // hidden behind the page until the card has nearly landed, then slides up from behind its top edge.
  const stackRect = useDerivedValue(() => {
    const f = frame.get();
    const out = clamp01((f.p - LAYOUT.stackFrom) / (1 - LAYOUT.stackFrom));
    const w = f.width * LAYOUT.stackScale;
    const h = f.height * LAYOUT.stackScale;
    const r = f.radius * LAYOUT.stackScale;
    return Skia.RRectXY(Skia.XYWHRect(f.x + (f.width - w) / 2, f.y - LAYOUT.stackPeek * out * out * (3 - 2 * out), w, h), r, r);
  });
  const stackOpacity = useDerivedValue(() => clamp01((frame.get().p - LAYOUT.stackFrom) / LAYOUT.stackFade) * pageAlpha.get());
  // The page's shadow answers the lift: it appears and lengthens as the page leaves the screen plane. It and the glow
  // are the page's own rectangle without the breathing (as before).
  const flat = useDerivedValue<FieldFrame>(() => {
    const f = frame.get();
    return f.covered ? f : { ...pageFrame(stage, f.p, lift.get(), driftX.get(), driftY.get()), p: f.p, covered: false };
  });
  const pageRect = useDerivedValue(() => {
    const f = flat.get();
    return Skia.RRectXY(Skia.XYWHRect(f.x, f.y, f.width, f.height), f.radius, f.radius);
  });
  const shadowOpacity = useDerivedValue(() => clamp01(frame.get().p) * LAYOUT.shadowAlpha);
  const glowRect = useDerivedValue(() => {
    const f = flat.get();
    const g = LAYOUT.glowSpread;
    return Skia.RRectXY(Skia.XYWHRect(f.x - g, f.y - g * 0.5, f.width + g * 2, f.height + g * 1.6), f.radius + g, f.radius + g);
  });
  // The glow swells while the card thinks and settles to a calm level with the summary.
  const glowOpacity = useDerivedValue(() => (QA_REPLICA ? 0 : clamp01((frame.get().p - 0.5) / 0.5) * (LAYOUT.glowRest + LAYOUT.glowThink * think.get())));
  const shadowShift = useDerivedValue(() => [{ translateY: clamp01(frame.get().p) * LAYOUT.shadowDrop }]);
  // Slow motes rising through the field (seeded, so every loop is identical).
  const motes = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, stage.width, stage.height));
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(COLOR.mote));
    const t = Math.max(0, fieldClock.get()) / 1000;
    for (let i = 0; i < (QA_REPLICA ? 0 : TIMING.motes); i++) {
      const h1 = Math.sin(i * 12.9898) * 43758.5453 - Math.floor(Math.sin(i * 12.9898) * 43758.5453);
      const h2 = Math.sin(i * 78.233) * 12543.123 - Math.floor(Math.sin(i * 78.233) * 12543.123);
      const speed = TIMING.moteSpeedMin + (TIMING.moteSpeedMax - TIMING.moteSpeedMin) * h2;
      const y = stage.height - ((h2 * stage.height + t * speed) % (stage.height + 20));
      const x = h1 * stage.width + Math.sin(t * 0.6 + i) * 6;
      paint.setAlphaf(0.08 + 0.14 * h1);
      canvas.drawCircle(x, y, 1 + 1.6 * h2, paint);
    }
    return rec.finishRecordingAsPicture();
  });
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Group transform={fieldTransform} origin={vec(stage.width / 2, stage.height / 2)}>
      {field && <Image image={field} x={0} y={0} width={stage.width} height={stage.height} fit="fill" />}
      {/* The field shifts to the summary's palette as the card lifts (baked from the source's summary frame). */}
      {fieldB && <Image image={fieldB} x={0} y={0} width={stage.width} height={stage.height} fit="fill" opacity={fieldLift} />}
    </Group>
    <Picture picture={motes} />
    {/* A hue glow under the card. A blur mask on a filled rounded rect is drawn analytically on the GPU (the image
        filter it replaces re-blurred an offscreen layer of the whole glow on every frame). */}
    <Group opacity={glowOpacity}>
      <RoundedRect rect={glowRect} color={COLOR.cardGlow}>
        <BlurMask blur={LAYOUT.glowBlur} style="normal" />
      </RoundedRect>
    </Group>
    {/* Behind the page's shadow, so the front card's shadow falls on it as it would on a real card. */}
    <RoundedRect rect={stackRect} color={COLOR.stack} opacity={stackOpacity} />
    <Group opacity={shadowOpacity} transform={shadowShift}>
      <RoundedRect rect={pageRect} color={COLOR.pageShadow}>
        <BlurMask blur={LAYOUT.shadowBlur} style="normal" />
      </RoundedRect>
    </Group>
  </Canvas>;
}

export function SummaryLight({ stage, values }: Props) {
  const { p, lift, driftX, driftY, rim, breath, clock, bloomAt, fingerAX, fingerAY, fingerBX, fingerBY, fingerAlpha } = values;
  const inset = LAYOUT.rimWidth / 2;
  // What an unlit rim or a finished bloom draws: one constant object each, so returning it changes nothing.
  const none = useMemo(() => ({ rect: Skia.RRectXY(Skia.XYWHRect(0, 0, 0, 0), 0, 0), center: vec(0, 0) }), []);
  const frame = useDerivedValue(() => pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get(), breath.get()));
  // The light canvas only redraws while something in it shows: the rim (rim > 0), the bloom, or the scripted fingers.
  // Its clocks are primitives that hold still otherwise, so the summary at rest costs no frames here.
  const rimOn = useDerivedValue(() => !QA_REPLICA && rim.get() > 0.001);
  const rimRect = useDerivedValue(() => {
    if (!rimOn.get()) return none.rect;
    const f = frame.get();
    const r = Math.max(0, f.radius - inset);
    return Skia.RRectXY(Skia.XYWHRect(f.x + inset, f.y + inset, f.width - inset * 2, f.height - inset * 2), r, r);
  });
  const center = useDerivedValue(() => {
    if (!rimOn.get()) return none.center;
    const f = frame.get();
    return vec(f.x + f.width / 2, f.y + f.height / 2);
  });
  const spinT = useDerivedValue(() => (rimOn.get() ? clock.get() : 0));
  const spin = useDerivedValue(() => [{ rotate: (spinT.get() / TIMING.rimTurnMs) * Math.PI * 2 }]);
  const rimOpacity = useDerivedValue(() => (QA_REPLICA ? 0 : clamp01(rim.get())));
  const fingerOpacity = useDerivedValue(() => fingerAlpha.get());
  // Payoff: when the summary lands, a ring of light leaves the card and fades (ease-out).
  const bloomU = useDerivedValue(() => {
    const at = bloomAt.get();
    return at < 0 ? 1 : clamp01((clock.get() - at) / TIMING.bloomMs);
  });
  const bloomRect = useDerivedValue(() => {
    const u = bloomU.get();
    if (u >= 1) return none.rect;
    const f = frame.get();
    const grow = easeOut(u) * TIMING.bloomReach;
    return Skia.RRectXY(Skia.XYWHRect(f.x - grow, f.y - grow, f.width + grow * 2, f.height + grow * 2), f.radius + grow, f.radius + grow);
  });
  const bloomOpacity = useDerivedValue(() => (bloomU.get() >= 1 ? 0 : 1 - bloomU.get()));

  // The rim's glow and the bloom are soft strokes. A blur *mask* on a stroke has no GPU path in Skia: it was rasterised
  // on the CPU, uploaded and blurred on every frame (most of the main thread's Skia time). A blur *image filter* on
  // the same stroke stays on the GPU and looks the same.
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Group opacity={rimOpacity}>
      <RoundedRect rect={rimRect} style="stroke" strokeWidth={LAYOUT.rimGlow} opacity={0.75}>
        <SweepGradient c={center} colors={[...COLOR.rim]} transform={spin} origin={center} />
        <Blur blur={LAYOUT.rimGlow} />
      </RoundedRect>
      <RoundedRect rect={rimRect} style="stroke" strokeWidth={LAYOUT.rimWidth}>
        <SweepGradient c={center} colors={[...COLOR.rim]} transform={spin} origin={center} />
      </RoundedRect>
    </Group>
    <Group opacity={bloomOpacity}>
      <RoundedRect rect={bloomRect} style="stroke" strokeWidth={LAYOUT.rimWidth * 2} color={COLOR.bloom}>
        <Blur blur={LAYOUT.rimGlow} />
      </RoundedRect>
    </Group>
    <Group opacity={fingerOpacity}>
      <Circle cx={fingerAX} cy={fingerAY} r={LAYOUT.fingerRadius} color={COLOR.finger} />
      <Circle cx={fingerAX} cy={fingerAY} r={LAYOUT.fingerRadius} color={COLOR.fingerRing} style="stroke" strokeWidth={1.5} />
      <Circle cx={fingerBX} cy={fingerBY} r={LAYOUT.fingerRadius} color={COLOR.finger} />
      <Circle cx={fingerBX} cy={fingerBY} r={LAYOUT.fingerRadius} color={COLOR.fingerRing} style="stroke" strokeWidth={1.5} />
    </Group>
  </Canvas>;
}
