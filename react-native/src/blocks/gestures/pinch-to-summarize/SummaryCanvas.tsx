/**
 * The two Skia layers: the gradient field under the page, and the light above it (the AI rim glued to the
 * page's edge, the scan band, the "Summarizing" shimmer and the autoplay's finger marks).
 */
import {
  Blur, BlurMask, Canvas, Circle, Group, Image, LinearGradient, matchFont, Picture, Rect, RoundedRect, Skia, SweepGradient,
  useImage,
  Text, vec,
} from '@shopify/react-native-skia';
import { Platform, StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { breathing, cardTop, clamp01, pageFrame, type Stage } from './geometry';
import { QA_HOLD, RIM, TIMING } from './motion';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { SummaryValues } from './use-summary-motion';

// QA (pixel gate) shows the replica only: the upgrade's light layers are off while a state is held.
const QA_REPLICA = QA_HOLD !== null;
const fieldSource = require('./assets/field.png');
const fieldBSource = require('./assets/field-b.png');

type Props = { stage: Stage; values: SummaryValues };

export function SummaryField({ stage, values }: Props) {
  // The source's gradient field, baked from its frame (a smooth image, so no shader is needed for the replica);
  // it breathes a little (scale ±1.5 %) so the field is never dead.
  const field = useImage(fieldSource);
  const fieldB = useImage(fieldBSource);
  const fieldTransform = useDerivedValue(() => {
    const k = 1 + TIMING.fieldBreath * Math.sin((values.clock.get() / TIMING.fieldPeriod) * Math.PI * 2);
    return [{ scale: k }];
  });
  // A lighter ghost card peeks out above the card's top edge (the source shows the page stacked on another).
  const ghost = useDerivedValue(() => {
    const f = pageFrame(stage, values.p.get(), values.lift.get(), values.driftX.get(), values.driftY.get());
    return Skia.RRectXY(Skia.XYWHRect(f.x + LAYOUT.ghostInset, f.y - LAYOUT.ghostPeek, f.width - LAYOUT.ghostInset * 2, f.height), f.radius, f.radius);
  });
  const ghostOpacity = useDerivedValue(() => clamp01((values.p.get() - 0.6) / 0.4));
  // The page's shadow answers the lift: it appears and lengthens as the page leaves the screen plane.
  const pageRect = useDerivedValue(() => {
    const f = pageFrame(stage, values.p.get(), values.lift.get(), values.driftX.get(), values.driftY.get());
    return Skia.RRectXY(Skia.XYWHRect(f.x, f.y, f.width, f.height), f.radius, f.radius);
  });
  const shadowOpacity = useDerivedValue(() => clamp01(values.p.get()) * LAYOUT.shadowAlpha);
  const glowRect = useDerivedValue(() => {
    const f = pageFrame(stage, values.p.get(), values.lift.get(), values.driftX.get(), values.driftY.get());
    const g = LAYOUT.glowSpread;
    return Skia.RRectXY(Skia.XYWHRect(f.x - g, f.y - g * 0.5, f.width + g * 2, f.height + g * 1.6), f.radius + g, f.radius + g);
  });
  // The glow swells while the card thinks and settles to a calm level with the summary.
  const glowOpacity = useDerivedValue(() => (QA_REPLICA ? 0 : clamp01((values.p.get() - 0.5) / 0.5) * (LAYOUT.glowRest + LAYOUT.glowThink * values.think.get())));
  const shadowShift = useDerivedValue(() => [{ translateY: clamp01(values.p.get()) * LAYOUT.shadowDrop }]);
  // Slow motes rising through the field (seeded, so every loop is identical).
  const motes = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, stage.width, stage.height));
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(COLOR.mote));
    const t = values.clock.get() / 1000;
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
      {fieldB && <Image image={fieldB} x={0} y={0} width={stage.width} height={stage.height} fit="fill" opacity={values.lift} />}
    </Group>
    <Picture picture={motes} />
    {/* A hue glow under the card (pre-blurred, never animated blur): the field lights up around it. */}
    <Group opacity={glowOpacity}>
      <RoundedRect rect={glowRect} color={COLOR.cardGlow}>
        <Blur blur={LAYOUT.glowBlur} />
      </RoundedRect>
    </Group>
    <Group opacity={shadowOpacity} transform={shadowShift}>
      <RoundedRect rect={pageRect} color={COLOR.pageShadow}>
        <Blur blur={LAYOUT.shadowBlur} />
      </RoundedRect>
    </Group>
    <RoundedRect rect={ghost} color={COLOR.ghost} opacity={ghostOpacity} />
  </Canvas>;
}

const font = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: TYPE.thinking, fontWeight: '600' });

export function SummaryLight({ stage, values }: Props) {
  const { p, lift, driftX, driftY, rim, think, clock, fingerAX, fingerAY, fingerBX, fingerBY, fingerAlpha } = values;
  const inset = LAYOUT.rimWidth / 2;
  const frame = useDerivedValue(() => pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get(), breathing(think.get(), clock.get(), TIMING.breathe, TIMING.breathePeriod)));
  const rimRect = useDerivedValue(() => {
    const f = frame.get();
    const r = Math.max(0, f.radius - inset);
    return Skia.RRectXY(Skia.XYWHRect(f.x + inset, f.y + inset, f.width - inset * 2, f.height - inset * 2), r, r);
  });
  const center = useDerivedValue(() => {
    const f = frame.get();
    return vec(f.x + f.width / 2, f.y + f.height / 2);
  });
  const spin = useDerivedValue(() => [{ rotate: (clock.get() / TIMING.rimTurnMs) * Math.PI * 2 }]);
  const rimOpacity = useDerivedValue(() => (QA_REPLICA ? 0 : clamp01(rim.get())));
  // The scan band: a soft light passing down the card while it is being read.
  const scanOpacity = useDerivedValue(() => (QA_REPLICA ? 0 : think.get() * (1 - lift.get()) * RIM.scan));
  const scanY = useDerivedValue(() => {
    const f = frame.get();
    const u = (clock.get() % TIMING.scanMs) / TIMING.scanMs;
    return f.y - LAYOUT.scanHeight + u * (f.height + LAYOUT.scanHeight);
  });
  const scanStart = useDerivedValue(() => vec(0, scanY.get()));
  const scanEnd = useDerivedValue(() => vec(0, scanY.get() + LAYOUT.scanHeight));
  // "Summarizing for you…" sits under the card while it thinks, with a light running through the letters.
  const labelWidth = font.measureText(COPY.thinking).width;
  const labelX = (stage.width - labelWidth) / 2;
  const labelY = cardTop(0) + LAYOUT.thinkHeight + LAYOUT.thinkingGap;
  const labelOpacity = useDerivedValue(() => think.get() * (1 - lift.get()));
  const shimmerX = useDerivedValue(() => labelX - 80 + ((clock.get() % TIMING.shimmerMs) / TIMING.shimmerMs) * (labelWidth + 160));
  const shimmerStart = useDerivedValue(() => vec(shimmerX.get(), 0));
  const shimmerEnd = useDerivedValue(() => vec(shimmerX.get() + 80, 0));
  const fingerOpacity = useDerivedValue(() => fingerAlpha.get());
  // Payoff: when the summary lands, a ring of light leaves the card and fades (ease-out).
  const bloomU = useDerivedValue(() => {
    const at = values.bloomAt.get();
    return at < 0 ? 1 : clamp01((clock.get() - at) / TIMING.bloomMs);
  });
  const bloomRect = useDerivedValue(() => {
    const f = frame.get();
    const e = 1 - Math.pow(1 - bloomU.get(), 3);
    const grow = e * TIMING.bloomReach;
    return Skia.RRectXY(Skia.XYWHRect(f.x - grow, f.y - grow, f.width + grow * 2, f.height + grow * 2), f.radius + grow, f.radius + grow);
  });
  const bloomOpacity = useDerivedValue(() => (bloomU.get() >= 1 ? 0 : 1 - bloomU.get()));

  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
    <Group opacity={scanOpacity} clip={rimRect}>
      <Rect x={0} y={scanY} width={stage.width} height={LAYOUT.scanHeight}>
        <LinearGradient start={scanStart} end={scanEnd} colors={['rgba(255,255,255,0)', 'rgba(200,215,255,0.38)', 'rgba(255,255,255,0)']} />
      </Rect>
    </Group>
    <Group opacity={rimOpacity}>
      <RoundedRect rect={rimRect} style="stroke" strokeWidth={LAYOUT.rimGlow} opacity={0.75}>
        <SweepGradient c={center} colors={[...COLOR.rim]} transform={spin} origin={center} />
        <BlurMask blur={LAYOUT.rimGlow} style="normal" />
      </RoundedRect>
      <RoundedRect rect={rimRect} style="stroke" strokeWidth={LAYOUT.rimWidth}>
        <SweepGradient c={center} colors={[...COLOR.rim]} transform={spin} origin={center} />
      </RoundedRect>
    </Group>
    <Group opacity={labelOpacity}>
      <Text x={labelX} y={labelY} text={COPY.thinking} font={font}>
        <LinearGradient start={shimmerStart} end={shimmerEnd} colors={['rgba(255,255,255,0.55)', '#FFFFFF', 'rgba(255,255,255,0.55)']} />
      </Text>
    </Group>
    <Group opacity={bloomOpacity}>
      <RoundedRect rect={bloomRect} style="stroke" strokeWidth={LAYOUT.rimWidth * 2} color={COLOR.bloom}>
        <BlurMask blur={LAYOUT.rimGlow} style="normal" />
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

