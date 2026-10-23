/**
 * Drawn layer: the page colour, the envelope (back, folding flap, card inside, front pocket, seal),
 * the gift card on its way out and in place, and the confetti.
 */
import {
  Blur, Canvas, Circle, Group, Image, LinearGradient, RadialGradient, Path, Rect, RoundedRect, Skia, Text, matchFont, useImage, vec,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { SensorType, interpolateColor, useAnimatedSensor, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { AMBIENT, OPEN, REDUCED_MS, RESET, REVEAL } from './motion';
import { COLORS, GIFT, LAYOUT, type ScreenLayout } from './theme';
import { finishMs, type EnvelopeValues } from './use-envelope';

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
const amountFont = matchFont({ fontFamily: FAMILY, fontSize: 30, fontWeight: 'bold' });
// The seal carries the shop's initials in a bold wordmark, as the source's seal does.
const sealFont = matchFont({ fontFamily: FAMILY, fontSize: 22, fontWeight: 'bold' });
const SEAL_TEXT = 'PT';
const ART = require('./assets/gift-card-art.jpg');

const EW = LAYOUT.envW;
const EH = LAYOUT.envH;
const CW = LAYOUT.cardW;
const CH = LAYOUT.cardH;
// Petals in the card's palette: magenta, pink, blush, lime, white, yellow.
const CONFETTI_COLOURS = ['#E0359A', '#F06BB5', '#FFC1DF', '#C9F23A', '#FFFFFF', '#FFE45C'];

const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};
const easeOut = (t: number) => {
  'worklet';
  return 1 - Math.pow(1 - clamp01(t), 3);
};
const easeInOut = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
};

/** Everything the scene needs for one frame, from the clock. */
export function frameOf(v: EnvelopeValues, reduced: boolean) {
  'worklet';
  const now = v.clock.get();
  const c = v.commit.get();
  let p = v.p.get();
  let fly = 0;
  let after = -1;
  if (c.at >= 0) {
    const fin = finishMs(c.p0, reduced);
    p = c.p0 + (1 - c.p0) * easeOut((now - c.at) / fin);
    after = now - c.at - fin;
    fly = reduced ? (after >= 0 ? 1 : 0) : REVEAL.flyEase(clamp01(after / REVEAL.flyMs));
  }
  const r = v.resetAt.get();
  const reset = r < 0 ? 0 : clamp01((now - r) / (reduced ? REDUCED_MS : RESET.fadeMs));
  const page = after < 0 ? 0 : clamp01((after + 100) / (reduced ? REDUCED_MS : REVEAL.pageMs));
  const drop = after < 0 || reduced ? (after >= 0 ? 1 : 0) : clamp01((after - 60) / REVEAL.envelopeDropMs);
  const b = v.breath.get();
  const breath = c.at < 0 && !reduced && b < 700 ? Math.sin((Math.PI * b) / 700) * OPEN.breathDeg : 0;
  return { now, p, fly, after, reset, page, drop, breath, tilt0: c.at >= 0 ? c.tilt0 : v.tilt.get() };
}

function flapPath() {
  const b = Skia.PathBuilder.Make();
  const tip = EH * LAYOUT.flapTip;
  b.moveTo(0, 0).lineTo(EW, 0).lineTo(EW / 2 + 22, tip - 10).quadTo(EW / 2, tip + 4, EW / 2 - 22, tip - 10).close();
  return b.build();
}
const FLAP = flapPath();
// Side panels run from the top corners down to the pocket's top corners (source frame 010).
const LEFT = Skia.PathBuilder.Make().moveTo(0, 0).lineTo(EW * 0.25, EH * 0.38).lineTo(EW * 0.25, EH).lineTo(0, EH).close().build();
const RIGHT = Skia.PathBuilder.Make().moveTo(EW, 0).lineTo(EW * 0.75, EH * 0.38).lineTo(EW * 0.75, EH).lineTo(EW, EH).close().build();
// As measured on the source: the bottom flap's edges rise steeply from the bottom corners and pass under
// the top flap (its apex is hidden above), leaving narrow side panels.
/** The front pocket: a trapezoid whose flat top sits at `top` (source frames 001 and 010). */
const bottomPath = (top: number) => {
  'worklet';
  return Skia.PathBuilder.Make().moveTo(0, EH).lineTo(EW * 0.25, top).lineTo(EW * 0.75, top).lineTo(EW, EH).close().build();
};
const AMOUNT_Y = CH / 2 - 22;

function GiftCard({ sweep, amount }: { sweep: SharedValue<number>; amount: SharedValue<string> }) {
  const art = useImage(ART);
  // A band of light crosses the glossy art: on arrival, then it rests where device tilt puts it.
  const bandT = useDerivedValue(() => [{ translateX: (sweep.get() - 0.5) * CW * 1.6 }]);
  return (
    <Group>
      <RoundedRect x={-CW / 2 + 10} y={-CH / 2 + 18} width={CW - 20} height={CH - 8} r={LAYOUT.cardR} color="rgba(120,30,60,0.35)">
        <Blur blur={16} />
      </RoundedRect>
      <Group clip={{ rect: { x: -CW / 2, y: -CH / 2, width: CW, height: CH }, rx: LAYOUT.cardR, ry: LAYOUT.cardR }}>
        <Rect x={-CW / 2} y={-CH / 2} width={CW} height={CH} color={COLORS.cardPink} />
        {art && <Image image={art} x={-CW / 2} y={-CH / 2} width={CW} height={CH} fit="cover" />}
        <Rect x={-CW / 2} y={-CH / 2} width={CW} height={CH}>
          <LinearGradient start={vec(-CW / 2, -CH / 2)} end={vec(CW / 2, CH / 2)}
            colors={['rgba(255,255,255,0.18)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0)']} />
        </Rect>
        <Group transform={bandT}>
          <Rect x={-CW * 0.7} y={-CH / 2} width={CW * 1.4} height={CH} blendMode="softLight">
            <LinearGradient start={vec(-CW * 0.2, -CH / 2)} end={vec(CW * 0.15, CH / 2)}
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.95)', 'rgba(255,255,255,0)']} />
          </Rect>
        </Group>
      </Group>
      <Text text={amount} x={-CW / 2 + 20} y={AMOUNT_Y} font={amountFont} color="white" />
    </Group>
  );
}

/** The wax seal: as the flap starts to lift it swells, then cracks into two halves along the flap's line. */
function Seal({ split }: { split: { get(): number } }) {
  const r = LAYOUT.sealD / 2;
  const face = (
    <>
      <Circle cx={0} cy={0} r={r} color={COLORS.ink} />
      <Text text={SEAL_TEXT} x={-sealFont.getTextWidth(SEAL_TEXT) / 2 - 1} y={8} font={sealFont} color={COLORS.sealMark} />
    </>
  );
  const pop = useDerivedValue(() => [{ scale: 1 + 0.12 * Math.sin(Math.PI * clamp01(split.get() * 2.5)) }]);
  const leftT = useDerivedValue(() => [{ translateX: -14 * split.get() }, { translateY: 10 * split.get() * split.get() }, { rotate: -0.35 * split.get() }]);
  const rightT = useDerivedValue(() => [{ translateX: 14 * split.get() }, { translateY: 12 * split.get() * split.get() }, { rotate: 0.4 * split.get() }]);
  const halves = useDerivedValue(() => 1 - clamp01((split.get() - 0.55) / 0.45));
  return (
    <Group transform={[{ translateX: EW / 2 }, { translateY: EH * LAYOUT.sealY }]}>
      <Circle cx={0} cy={3} r={r} color="rgba(60,80,0,0.25)" opacity={halves}><Blur blur={4} /></Circle>
      <Group transform={pop} opacity={halves}>
        <Group transform={leftT} clip={Skia.XYWHRect(-r - 2, -r - 2, r + 2.5, r * 2 + 4)}>{face}</Group>
        <Group transform={rightT} clip={Skia.XYWHRect(0, -r - 2, r + 2, r * 2 + 4)}>{face}</Group>
      </Group>
    </Group>
  );
}

function ConfettiLayer({ v, which }: { v: EnvelopeValues; which: number }) {
  const path = useDerivedValue(() => {
    const c = v.confetti.get();
    const b = Skia.PathBuilder.Make();
    for (let o = 0; o < c.length; o += 8) {
      if (c[o + 6] <= 0 || c[o + 7] !== which) continue;
      const a = c[o + 4];
      const f = Math.min(1, c[o + 6] / 0.5);
      // Petals: a lens shape whose width follows its spin, so each one flutters as it falls.
      const hw = 7 * f;
      const hh = 3.6 * f * (0.25 + 0.75 * Math.abs(Math.cos(a * 1.3)));
      const ca = Math.cos(a);
      const sa = Math.sin(a);
      const x = c[o];
      const y = c[o + 1];
      b.moveTo(x - ca * hw, y - sa * hw);
      b.quadTo(x - sa * hh * 2, y + ca * hh * 2, x + ca * hw, y + sa * hw);
      b.quadTo(x + sa * hh * 2, y - ca * hh * 2, x - ca * hw, y - sa * hw);
      b.close();
    }
    return b.build();
  });
  return <Path path={path} color={CONFETTI_COLOURS[which]} />;
}

export function EnvelopeScene({ v, width, height, reduced, lay }: { v: EnvelopeValues; width: number; height: number; reduced: boolean; lay: ScreenLayout }) {
  const envTop = lay.envTop;
  const finalTop = lay.cardTop;
  const ex = (width - EW) / 2;
  const frame = useDerivedValue(() => frameOf(v, reduced));
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 16 });
  const roll = useDerivedValue(() => (reduced ? 0 : Math.max(-0.35, Math.min(0.35, rotation.sensor.get().roll))));
  const page = useDerivedValue(() => {
    const f = frame.get();
    return interpolateColor(f.page * (1 - f.reset), [0, 1], [COLORS.lime, COLORS.white]);
  });
  // Ambient: the sealed envelope floats ±4 pt; it settles while it is being opened.
  const float = useDerivedValue(() => {
    const f = frame.get();
    if (reduced) return 0;
    const still = 1 - clamp01(f.p / 0.05);
    return AMBIENT.floatPt * Math.sin((2 * Math.PI * f.now) / AMBIENT.floatMs) * still;
  });
  const envTransform = useDerivedValue(() => {
    const f = frame.get();
    const d = f.reset > 0 ? 0 : f.drop;
    // As the flap opens the envelope settles down a little (source frames 001 → 010).
    const settle = f.reset > 0 ? 0 : OPEN.settlePt * clamp01(f.p / OPEN.flapShare);
    return [{ translateX: ex }, { translateY: envTop + settle + d * d * 460 + float.get() }, { scale: f.reset > 0 ? 0.96 + 0.04 * f.reset : 1 }];
  });
  // A slow highlight drifts across the lime page.
  const glowC = useDerivedValue(() => {
    const t = frame.get().now / AMBIENT.glowMs;
    return vec(width * (0.5 + 0.35 * Math.sin(2 * Math.PI * t)), height * (0.35 + 0.12 * Math.cos(2 * Math.PI * t * 0.7)));
  });
  // Kept very faint so the page colour at rest matches the source (pixel gate); it reads only as slow light.
  const glowOpacity = useDerivedValue(() => (reduced ? 0 : AMBIENT.glowAmount * (1 - frame.get().page)));
  const sweep = useDerivedValue(() => {
    const f = frame.get();
    const drift = reduced ? 0 : 0.12 * Math.sin((2 * Math.PI * f.now) / 6000);
    const resting = 0.5 + 0.8 * roll.get() + drift;
    if (f.after < 0 || reduced) return resting;
    const u = clamp01((f.after - REVEAL.flyMs * 0.7) / REVEAL.sweepMs);
    return u < 1 ? -0.4 + 1.8 * u + (resting - 1.4) * u * u : resting;
  });
  const amount = useDerivedValue(() => {
    const f = frame.get();
    if (f.after < 0 || reduced) return GIFT.amount;
    const u = clamp01((f.after - REVEAL.flyMs * 0.8) / REVEAL.countMs);
    const eased = 1 - Math.pow(1 - u, 3);
    return `$${Math.round(GIFT.value * eased)}`;
  });
  const envOpacity = useDerivedValue(() => {
    const f = frame.get();
    return f.reset > 0 ? f.reset : 1 - f.drop;
  });
  const angle = useDerivedValue(() => {
    const f = frame.get();
    if (f.reset > 0) return 0;
    return clamp01(f.p / OPEN.flapShare) * 180 + f.breath;
  });
  // The seal cracks only when the finger lifts the flap (not on the idle breath), so the rest state stays the source's.
  const split = useDerivedValue(() => {
    const f = frame.get();
    const lifted = f.reset > 0 ? 0 : clamp01(f.p / OPEN.flapShare) * 180;
    return clamp01(lifted / (reduced ? 30 : OPEN.sealSplitDeg));
  });
  // The flap is paper: its lower part lags the fold, so it curls as it lifts.
  const fold = EH * LAYOUT.flapTip * 0.55;
  const tipT = useDerivedValue(() => {
    const lag = reduced ? 0 : OPEN.curlDeg * Math.sin((Math.PI * Math.min(180, angle.get())) / 180);
    return [{ translateY: fold }, { perspective: 700 }, { rotateX: (lag * Math.PI) / 180 }, { translateY: -fold }];
  });
  // The front pocket's V: hidden under the closed flap, it opens down to 0.4 of the height as the flap lifts (source).
  // Constant: it read the frame only to re-run, which rebuilt the path every frame.
  const bottom = useDerivedValue(() => bottomPath(OPEN.pocketApex * EH));
  const flapTransform = useDerivedValue(() => [{ perspective: 900 }, { rotateX: (-angle.get() * Math.PI) / 180 }]);
  const flapFront = useDerivedValue(() => (angle.get() < 90 ? 1 : 0));
  // Past 90° the flap folds away behind the envelope (source): it fades as it goes over.
  const flapBack = useDerivedValue(() => (angle.get() >= 90 ? 1 - clamp01((angle.get() - 90) / 25) : 0));
  const cardPose = useDerivedValue(() => {
    const f = frame.get();
    const out = f.reset > 0 ? 0 : clamp01((f.p - OPEN.flapShare) / (1 - OPEN.flapShare));
    const insideY = envTop + OPEN.settlePt * clamp01(f.p / OPEN.flapShare) * (f.reset > 0 ? 0 : 1) + 33 + (CH * LAYOUT.cardInScale) / 2 - out * OPEN.cardRise;
    const finalY = finalTop + CH / 2;
    const k = f.fly;
    // A small extra lift mid-flight so the card arcs instead of sliding.
    const arc = -36 * Math.sin(Math.PI * k);
    return {
      y: insideY + (finalY - insideY) * k + arc,
      s: LAYOUT.cardInScale + (1 - LAYOUT.cardInScale) * easeInOut(k),
      rot: f.tilt0 * (1 - k),
      front: f.after >= 0 && f.reset === 0,
    };
  });
  const cardTransform = useDerivedValue(() => [
    { translateX: width / 2 }, { translateY: cardPose.get().y }, { rotate: (cardPose.get().rot * Math.PI) / 180 }, { scale: cardPose.get().s },
  ]);
  const insideCardTransform = useDerivedValue(() => [
    { translateX: EW / 2 }, { translateY: cardPose.get().y - envTop }, { rotate: (cardPose.get().rot * Math.PI) / 180 }, { scale: cardPose.get().s },
  ]);
  const insideOpacity = useDerivedValue(() => (cardPose.get().front ? 0 : 1));
  const frontOpacity = useDerivedValue(() => (cardPose.get().front ? 1 - frame.get().reset : 0));

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height} color={page} />
      <Rect x={0} y={0} width={width} height={height} opacity={glowOpacity}>
        <RadialGradient c={glowC} r={width * 0.8} colors={['rgba(255,255,255,0.32)', 'rgba(255,255,255,0)']} />
      </Rect>
      <Group transform={envTransform} opacity={envOpacity}>
        <RoundedRect x={14} y={30} width={EW - 28} height={EH - 10} r={14} color={COLORS.shadow}><Blur blur={26} /></RoundedRect>
        <RoundedRect x={0} y={0} width={EW} height={EH} r={10}>
          <LinearGradient start={vec(0, 0)} end={vec(0, EH)} colors={[COLORS.sideHigh, COLORS.bottomLow]} />
        </RoundedRect>
        <Group opacity={flapBack} transform={flapTransform}>
          <Path path={FLAP}>
            <LinearGradient start={vec(0, 0)} end={vec(0, EH * LAYOUT.flapTip)} colors={[COLORS.limeInside, COLORS.limeLow]} />
          </Path>
        </Group>
        <Group opacity={insideOpacity} transform={insideCardTransform}><GiftCard sweep={sweep} amount={amount} /></Group>
        <Group clip={{ rect: { x: 0, y: 0, width: EW, height: EH }, rx: 10, ry: 10 }}>
          <Path path={LEFT}>
            <LinearGradient start={vec(0, EH)} end={vec(EW * 0.3, EH * 0.4)} colors={[COLORS.sideLow, COLORS.sideHigh]} />
          </Path>
          <Path path={RIGHT} color={COLORS.sideRight} />
          <Path path={bottom}>
            <LinearGradient start={vec(0, EH)} end={vec(0, 0)} colors={[COLORS.bottomLow, COLORS.bottomHigh]} />
          </Path>
          {/* Key light from the top left catches the pocket's upper-left area, as in the source. */}
          <Path path={bottom}>
            <RadialGradient c={vec(EW * 0.28, EH * 0.5)} r={EW * 0.42} colors={['rgba(246,255,150,0.55)', 'rgba(246,255,150,0)']} />
          </Path>
          {/* Soft creases where the paper folds, not drawn outlines. */}
          <Path path={bottom} color={COLORS.crease} style="stroke" strokeWidth={1.5}><Blur blur={1.2} /></Path>
        </Group>
        <Group opacity={flapFront} transform={flapTransform}>
          <Path path={FLAP} color="rgba(80,100,0,0.18)" transform={[{ translateY: 4 }]}><Blur blur={5} /></Path>
          <Group clip={Skia.XYWHRect(0, 0, EW, fold)}>
            <Path path={FLAP}>
              <LinearGradient start={vec(0, 0)} end={vec(EW, EH * LAYOUT.flapTip)} colors={[COLORS.flapHigh, COLORS.flapLow]} />
            </Path>
            <Path path={FLAP}>
              <RadialGradient c={vec(EW * 0.24, EH * 0.14)} r={EW * 0.38} colors={['rgba(246,255,140,0.6)', 'rgba(246,255,140,0)']} />
            </Path>
          </Group>
          <Group transform={tipT}>
            <Group clip={Skia.XYWHRect(0, fold, EW, EH)}>
              <Path path={FLAP}>
                <LinearGradient start={vec(0, 0)} end={vec(EW, EH * LAYOUT.flapTip)} colors={[COLORS.flapHigh, COLORS.flapLow]} />
              </Path>
              <Path path={FLAP}>
                <RadialGradient c={vec(EW * 0.24, EH * 0.14)} r={EW * 0.38} colors={['rgba(246,255,140,0.6)', 'rgba(246,255,140,0)']} />
              </Path>
            </Group>
            <Seal split={split} />
          </Group>
        </Group>
      </Group>
      <Group opacity={frontOpacity} transform={cardTransform}><GiftCard sweep={sweep} amount={amount} /></Group>
      {CONFETTI_COLOURS.map((_, i) => <ConfettiLayer key={i} v={v} which={i} />)}
    </Canvas>
  );
}
