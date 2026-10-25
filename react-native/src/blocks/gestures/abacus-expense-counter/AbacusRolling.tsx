import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';

/*
 Rolling amounts and meters for the abacus screen, all on the UI thread: an amount is a row of
 digit wheels (0–9 and a second 0), each rolled by a spring when its digit changes, forward when
 the amount grows and backward when it shrinks, the lower wheels first (25 ms apart). Leading
 zeros fade out and the "$" slides in after them. Nothing here re-renders React.
 */

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const ROLL = { duration: 520, dampingRatio: 0.78 };

type Props = {
  /** The amount in cents, and its value when the screen first appears. */
  cents: SharedValue<number>;
  initial: number;
  size: number;
  color: string;
  weight?: '600' | '700';
  /** Whole-dollar digits shown (leading zeros fade). */
  intDigits?: number;
  reduced?: boolean;
};

function digitAt(cents: number, place: number) {
  'worklet';
  return Math.floor(Math.max(0, Math.round(cents)) / Math.pow(10, place)) % 10;
}

type WheelProps = { cents: SharedValue<number>; initial: number; place: number; order: number; line: number; width: number; style: object; reduced: boolean };

const Wheel = memo(function Wheel({ cents, initial, place, order, line, width, style, reduced }: WheelProps) {
  const pos = useSharedValue(digitAt(initial, place));
  const last = useSharedValue(initial);
  useAnimatedReaction(
    () => Math.round(cents.get()),
    (v) => {
      const before = last.get();
      if (v === before) return;
      last.set(v);
      const from = ((Math.round(pos.get()) % 10) + 10) % 10;
      const to = digitAt(v, place);
      if (from === to) return;
      const delta = v > before ? (to - from + 10) % 10 : -((from - to + 10) % 10);
      const target = Math.round(pos.get()) + delta;
      pos.set(reduced ? target : withDelay(order * 25, withSpring(target, ROLL)));
    },
  );
  const roll = useAnimatedStyle(() => {
    const p = ((pos.get() % 10) + 10) % 10;
    return { transform: [{ translateY: -p * line }] };
  });
  // Leading zeros fade (never the units of dollars).
  const show = useAnimatedStyle(() => {
    if (place <= 2) return { opacity: 1 };
    const visible = Math.round(cents.get()) >= Math.pow(10, place) ? 1 : 0;
    return { opacity: reduced ? visible : withTiming(visible, { duration: 200 }) };
  });
  return (
    <Animated.View style={[{ width, height: line, overflow: 'hidden' }, show]}>
      <Animated.View style={roll}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={[style, { height: line, lineHeight: line, width, textAlign: 'center' }]}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
});

export function RollingAmount({ cents, initial, size, color, weight = '700', intDigits = 3, reduced = false }: Props) {
  const line = Math.round(size * 1.18);
  const width = Math.round(size * 0.6);
  const text = { fontSize: size, fontWeight: weight, color, fontVariant: ['tabular-nums' as const] };
  const places: number[] = [];
  for (let p = intDigits + 1; p >= 2; p--) places.push(p);
  // The "$" moves right by one wheel for every leading zero that is hidden.
  const dollar = useAnimatedStyle(() => {
    let hidden = 0;
    for (let p = intDigits + 1; p > 2; p--) if (Math.round(cents.get()) < Math.pow(10, p)) hidden++;
    const x = hidden * width;
    return { transform: [{ translateX: reduced ? x : withTiming(x, { duration: 220 }) }] };
  });
  return (
    <View style={styles.row} accessible={false}>
      <Animated.Text style={[text, { height: line, lineHeight: line }, dollar]}>$</Animated.Text>
      {places.map((p) => (
        <Wheel key={p} cents={cents} initial={initial} place={p} order={p} line={line} width={width} style={text} reduced={reduced} />
      ))}
      <Text style={[text, { height: line, lineHeight: line, width: Math.round(size * 0.3), textAlign: 'center' }]}>.</Text>
      <Wheel cents={cents} initial={initial} place={1} order={1} line={line} width={width} style={text} reduced={reduced} />
      <Wheel cents={cents} initial={initial} place={0} order={0} line={line} width={width} style={text} reduced={reduced} />
    </View>
  );
}

/** A thin meter whose fill springs to `value` (0–1). */
export function Meter({ value, color, height = 4, reduced = false }: { value: SharedValue<number>; color: string; height?: number; reduced?: boolean }) {
  const fill = useAnimatedStyle(() => {
    const v = Math.max(0.001, Math.min(1, value.get()));
    return { transform: [{ scaleX: reduced ? v : withSpring(v, { duration: 600, dampingRatio: 0.8 }) }] };
  });
  return (
    <View style={[styles.track, { height, borderRadius: height / 2 }]}>
      <Animated.View style={[styles.fill, { backgroundColor: color, borderRadius: height / 2 }, fill]} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start' },
  track: { flex: 1, backgroundColor: 'rgba(244,236,224,0.1)', overflow: 'hidden' },
  fill: { flex: 1, transformOrigin: 'left center' },
});
