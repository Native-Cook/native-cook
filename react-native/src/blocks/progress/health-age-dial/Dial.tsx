import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Line,
  Path,
  Skia,
  SweepGradient,
  vec,
} from '@shopify/react-native-skia';
import { useMemo, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { ROLL, SIGN, TRAIL, clamp01, rollStep, seg } from './motion';
import { colors, layout, ringStops, type } from './theme';

const D = layout.dial;
const OUTER = D.r + D.stroke / 2;
/** The dial canvas: the ring plus room for the trail's glow. */
export const DIAL_SIZE = 2 * (OUTER + D.canvasPad);
const C = DIAL_SIZE / 2;

/** Degrees clockwise from 12 o'clock → Skia's degrees clockwise from 3 o'clock. */
function skiaDeg(a12: number): number {
  'worklet';
  return a12 - 90;
}

function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function mix(a: string, b: string, p: number): string {
  const x = hexToRgb(a);
  const y = hexToRgb(b);
  const c = x.map((v, i) => Math.round(v + (y[i] - v) * p));
  return `rgb(${c[0]},${c[1]},${c[2]})`;
}

/**
 * The ring's sweep gradient. Skia sweeps from 3 o'clock, so the stops are re-ordered from there;
 * 3 o'clock (90° from 12) lies on the grey end of the ring, where neighbouring stops match.
 */
function sweepStops(): { colors: string[]; positions: number[] } {
  const stops = ringStops.map(([a, c]) => ({ p: (((a - 90) % 360) + 360) % 360 / 360, c }));
  stops.sort((s, t) => s.p - t.p);
  // Colour exactly at 3 o'clock (a12 = 90), between the stops on either side of 90°.
  const before = ringStops.filter(([a]) => a <= 90).pop()!;
  const after = ringStops.find(([a]) => a > 90)!;
  const seam = mix(before[1], after[1], (90 - before[0]) / (after[0] - before[0]));
  return {
    colors: [seam, ...stops.map((s) => s.c), seam],
    positions: [0, ...stops.map((s) => s.p), 1],
  };
}

function arcPath(cx: number, cy: number, r: number, fromA12: number, toA12: number) {
  const p = Skia.Path.Make();
  p.addArc({ x: cx - r, y: cy - r, width: 2 * r, height: 2 * r }, skiaDeg(fromA12), toA12 - fromA12);
  return p;
}

/**
 * The dial: gradient ring, actual-age tick, comet trail and the health-age dot. `years` is
 * the dot's offset from the actual age; `trail` its signed trail length in degrees.
 */
export function DialRing({
  years,
  trail,
  lift,
}: {
  years: SharedValue<number>;
  trail: SharedValue<number>;
  lift: SharedValue<number>;
}) {
  const ring = useMemo(() => arcPath(C, C, D.r, D.start, D.end), []);
  // A full circle starting at 3 o'clock; trimmed and rotated it becomes the comet's trail.
  const circle = useMemo(() => arcPath(C, C, D.r, 90, 449.99), []);
  const sweep = useMemo(() => sweepStops(), []);

  const dotAngle = useDerivedValue(() => (skiaDeg(years.get() * D.degPerYear) * Math.PI) / 180);
  const dotX = useDerivedValue(() => C + D.r * Math.cos(dotAngle.get()));
  const dotY = useDerivedValue(() => C + D.r * Math.sin(dotAngle.get()));
  const dotR = useDerivedValue(() => (D.dot / 2) * (1 + 0.14 * lift.get()));
  const liftGlow = useDerivedValue(() => 0.55 * lift.get());

  // The trail lives in a frame rotated so the dot sits at 180°; trim picks the side it trails on.
  const trailFrame = useDerivedValue(() => [{ rotate: dotAngle.get() - Math.PI }]);
  const trailStart = useDerivedValue(() => 0.5 + Math.min(0, trail.get()) / 360);
  const trailEnd = useDerivedValue(() => 0.5 + Math.max(0, trail.get()) / 360);
  const trailOpacity = useDerivedValue(() => clamp01(Math.abs(trail.get()) / (TRAIL.max * TRAIL.fade)));
  const span = TRAIL.max / 360;

  return (
    <Canvas style={{ width: DIAL_SIZE, height: DIAL_SIZE }} pointerEvents="none">
      <Path path={ring} style="stroke" strokeWidth={D.stroke} strokeCap="round">
        <SweepGradient c={vec(C, C)} colors={sweep.colors} positions={sweep.positions} />
        <BlurMask blur={D.edgeSoftness} style="normal" />
      </Path>
      <Line
        p1={vec(C, C - D.r - D.tickLength / 2)}
        p2={vec(C, C - D.r + D.tickLength / 2)}
        color={colors.tick}
        strokeWidth={D.tickWidth}
        strokeCap="round"
      >
        <BlurMask blur={D.edgeSoftness * 0.6} style="normal" />
      </Line>
      <Group transform={trailFrame} origin={vec(C, C)} opacity={trailOpacity} blendMode="screen">
        <Path path={circle} style="stroke" strokeWidth={D.stroke * 0.86} strokeCap="butt" start={trailStart} end={trailEnd}>
          <SweepGradient
            c={vec(C, C)}
            colors={['rgba(255,255,255,0)', 'rgba(214,236,255,0.95)', 'rgba(255,255,255,0)']}
            positions={[0.5 - span, 0.5, 0.5 + span]}
          />
          <BlurMask blur={3} style="normal" />
        </Path>
      </Group>
      <Circle cx={dotX} cy={dotY} r={dotR} color={colors.blue} opacity={liftGlow}>
        <BlurMask blur={12} style="normal" />
      </Circle>
      <Circle cx={dotX} cy={dotY} r={dotR} color={colors.blue} />
      <Circle cx={dotX} cy={dotY} r={dotR} color="rgba(10,100,190,0.35)" style="stroke" strokeWidth={1.2} />
    </Canvas>
  );
}

/** One digit of a rolling column: digit `index % 10`, `index - position` rows below the window. */
function RollDigit({
  index,
  position,
  lineHeight,
  style,
}: {
  index: number;
  position: SharedValue<number>;
  lineHeight: number;
  style: object;
}) {
  const animated = useAnimatedStyle(() => {
    const d = index - position.get();
    return {
      opacity: clamp01(1 - Math.abs(d) * ROLL.falloff),
      transform: [{ translateY: d * lineHeight * ROLL.travel }],
    };
  });
  return (
    <Animated.Text style={[StyleSheet.absoluteFill, style, animated]} allowFontScaling={false}>
      {index % 10}
    </Animated.Text>
  );
}

const DIGITS = Array.from({ length: 11 }, (_, i) => i);

function RollColumn({
  position,
  width,
  lineHeight,
  style,
}: {
  position: SharedValue<number>;
  width: number;
  lineHeight: number;
  style: object;
}) {
  return (
    <View style={{ width, height: lineHeight }}>
      {DIGITS.map((i) => (
        <RollDigit key={i} index={i} position={position} lineHeight={lineHeight} style={style} />
      ))}
    </View>
  );
}

const NUMBER_STYLE = {
  fontFamily: 'ui-rounded',
  fontSize: type.number.size,
  fontWeight: type.number.weight,
  color: type.number.color,
  letterSpacing: type.number.tracking,
  textAlign: 'center' as const,
};
export const NUMBER_LINE = Math.round(type.number.size * 1.2);

/**
 * "-3.9" as an odometer: sign, units, point, tenths. At rest the columns sit exactly where the
 * text "-3.9" lays its glyphs out (measured at runtime), so the still frame is the plain text.
 */
export function YearsOdometer({ years }: { years: SharedValue<number> }) {
  const [w, setW] = useState<number[] | null>(null);
  const parts = ['-', '-3', '-3.', '-3.9'];
  const measured: number[] = useMemo(() => [], []);
  const onMeasure = (i: number) => (e: LayoutChangeEvent) => {
    measured[i] = e.nativeEvent.layout.width;
    if (parts.every((_, k) => measured[k] > 0)) setW([...measured]);
  };

  const tenths = useDerivedValue(() => {
    const a = Math.abs(years.get()) * 10;
    return rollStep(a - Math.floor(a / 10) * 10);
  });
  const units = useDerivedValue(() => {
    const a = Math.abs(years.get()) * 10;
    const t = a - Math.floor(a / 10) * 10;
    return rollStep(Math.floor(a / 10) + Math.max(0, t - 9));
  });
  const minus = useAnimatedStyle(() => ({ opacity: seg(-years.get(), SIGN.from, SIGN.to) }));
  const plus = useAnimatedStyle(() => ({ opacity: seg(years.get(), SIGN.from, SIGN.to) }));

  if (!w) {
    return (
      <View style={styles.measure} pointerEvents="none">
        {parts.map((p, i) => (
          <Text key={p} style={NUMBER_STYLE} onLayout={onMeasure(i)} allowFontScaling={false}>
            {p}
          </Text>
        ))}
      </View>
    );
  }
  const [w0, w1, w2, w3] = w;
  return (
    <View
      style={{ width: w3, height: NUMBER_LINE, flexDirection: 'row' }}
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {/* Signs are right-aligned on the units column, so a wide "+" grows to the left. */}
      <View style={{ width: w0, height: NUMBER_LINE }}>
        <Animated.Text style={[styles.sign, NUMBER_STYLE, minus]} allowFontScaling={false}>
          -
        </Animated.Text>
        <Animated.Text style={[styles.sign, NUMBER_STYLE, plus]} allowFontScaling={false}>
          +
        </Animated.Text>
      </View>
      <RollColumn position={units} width={w1 - w0} lineHeight={NUMBER_LINE} style={NUMBER_STYLE} />
      <Text style={[NUMBER_STYLE, { width: w2 - w1 }]} allowFontScaling={false}>
        .
      </Text>
      <RollColumn position={tenths} width={w3 - w2} lineHeight={NUMBER_LINE} style={NUMBER_STYLE} />
    </View>
  );
}

const styles = StyleSheet.create({
  measure: { position: 'absolute', opacity: 0, flexDirection: 'row' },
  sign: { position: 'absolute', top: 0, right: 0 },
});
