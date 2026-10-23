// Reversible mechanical carry: fixed digit strips move entirely on the UI thread.
import { StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue,
} from 'react-native-reanimated';

import { TYPE, type RollingNumberProps } from './gem-contract';

// Measured once with CanvasKit from the bundled Inter Bold, at its 2048-unit em.
// Each wheel occupies exactly one tabular advance, without inter-column gutters.
// No glyph measurement or layout changes are needed while the number rolls.
const ZERO_ADVANCE = 1353 / 2048;
const COMMA_ADVANCE = 582 / 2048;
const COLUMN_ADVANCE = ZERO_ADVANCE;

export function rollingNumberWidth(digits: number, size: number): number {
  return size * (digits * COLUMN_ADVANCE + Math.floor((digits - 1) / 3) * COMMA_ADVANCE);
}

/** Unwrapped wheel position: a higher wheel moves only in the final unit before a carry. */
export function rollingColumnPosition(value: number, place: number): number {
  'worklet';
  const safe = Math.max(0, value);
  const whole = Math.floor(safe / place);
  return whole + Math.max(0, safe - whole * place - (place - 1));
}

/** Opacity replaces an unavailable gradient mask, using distance from the window centre. */
export function rollingDigitOpacity(row: number, position: number, line: number): number {
  'worklet';
  const gap = Math.abs(row - position);
  const fadeStart = Math.max(0, 0.5 - 4 / Math.max(line, 1));
  const t = Math.max(0, Math.min(1, (gap - fadeStart) / (1 - fadeStart)));
  return 1 - t * t * (3 - 2 * t);
}

type DigitProps = Pick<RollingNumberProps, 'size' | 'line' | 'color'> & {
  row: number;
  position: SharedValue<number>;
  blur: SharedValue<number>;
};

function Digit({ row, position, blur, size, line, color }: DigitProps) {
  const animated = useAnimatedStyle(() => ({
    opacity: rollingDigitOpacity(row, position.get(), line) * (1 - blur.get() * 0.45),
    transform: [{ scaleY: 1 + blur.get() * 0.16 }],
  }));
  return (
    <Animated.Text accessible={false} allowFontScaling={false}
      style={[styles.digit, { fontSize: size, lineHeight: line, height: line, color }, animated]}>
      {row % 10}
    </Animated.Text>
  );
}

type ColumnProps = Pick<RollingNumberProps, 'value' | 'size' | 'line' | 'color'> & {
  place: number;
  index: number;
  speeds: SharedValue<number[]>;
};

function Column({ value, place, index, speeds, size, line, color }: ColumnProps) {
  const position = useDerivedValue(() => rollingColumnPosition(value.get(), place) % 10);
  const blur = useDerivedValue(() => Math.max(0, Math.min(1, (speeds.get()[index] - 12) / 24)));
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -position.get() * line }] }));
  const rows = [];
  for (let row = 0; row <= 10; row++) {
    rows.push(<Digit key={row} row={row} position={position} blur={blur} size={size} line={line} color={color} />);
  }
  return (
    <View accessible={false} style={[styles.window, { width: size * COLUMN_ADVANCE, height: line }]}>
      <Animated.View style={strip}>{rows}</Animated.View>
    </View>
  );
}

export function RollingNumber({ value, digits, size, line, color }: RollingNumberProps) {
  const previous = useSharedValue(-1);
  const speeds = useSharedValue<number[]>(Array(digits).fill(0));
  useFrameCallback((frame) => {
    const current = value.get();
    const last = previous.get();
    const dt = (frame.timeSincePreviousFrame ?? 16.667) / 1000;
    if (last === current) {
      // Reset once after the last moving frame, without scheduling work on RN.
      const old = speeds.get();
      let moving = false;
      for (let i = 0; i < digits; i++) if (old[i] !== 0) moving = true;
      if (!moving) return;
    }
    const next: number[] = [];
    for (let i = 0; i < digits; i++) {
      const place = Math.pow(10, digits - i - 1);
      next.push(last < 0 || dt <= 0 ? 0
        : Math.abs(rollingColumnPosition(current, place) - rollingColumnPosition(last, place)) / dt);
    }
    speeds.set(next);
    previous.set(current);
  });

  const columns = [];
  for (let i = 0; i < digits; i++) {
    if (i > 0 && (digits - i) % 3 === 0) {
      columns.push(
        <Text key={`comma-${i}`} accessible={false} allowFontScaling={false}
          style={[styles.digit, { width: size * COMMA_ADVANCE, height: line, fontSize: size, lineHeight: line, color }]}>,</Text>,
      );
    }
    columns.push(<Column key={i} index={i} place={Math.pow(10, digits - i - 1)}
      value={value} speeds={speeds} size={size} line={line} color={color} />);
  }
  return (
    <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
      pointerEvents="none" style={[styles.row, { width: rollingNumberWidth(digits, size), height: line }]}>
      {columns}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  window: { overflow: 'hidden' },
  digit: {
    fontFamily: TYPE.number.family,
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
    includeFontPadding: false,
  },
});
