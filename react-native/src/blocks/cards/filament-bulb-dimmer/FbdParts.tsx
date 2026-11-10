// Filament Bulb Dimmer — the readout that rolls and the switch that stretches.

import { StyleSheet, Text, View } from 'react-native';
import Animated, { interpolateColor, type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';

import { COLOR, FONT } from './fbd-theme';

const AnimatedPath = Animated.createAnimatedComponent(SvgPath);
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const LH = 30;
const DW = 15.4;

/** one column of an odometer: its digit rolls with the value, and carries as the column below
 * passes 9 → 0 */
function Column({ v, place }: { v: SharedValue<number>; place: number }) {
  const roll = useAnimatedStyle(() => {
    const x = Math.max(0, v.get());
    let pos;
    if (place === 1) pos = x % 10;
    else pos = (Math.floor(x / place) % 10) + Math.max(0, (x % place) - (place - 1));
    return { transform: [{ translateY: -pos * LH }] };
  });
  // leading columns open as the value reaches them
  const room = useAnimatedStyle(() => {
    const x = v.get();
    const k = Math.max(0, Math.min(1, x - (place - 1)));
    return { width: place === 1 ? DW : DW * k, opacity: place === 1 ? 1 : k };
  });
  return (
    <Animated.View style={[styles.column, room]}>
      <Animated.View style={roll}>
        {DIGITS.map((n, i) => (
          <Text key={i} style={styles.digit} allowFontScaling={false}>
            {n}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** "78%" whose digits roll like an odometer while the dial turns */
export function RollingPercent({ value }: { value: SharedValue<number> }) {
  return (
    <View style={styles.row} accessible={false} importantForAccessibility="no-hide-descendants">
      <Column v={value} place={100} />
      <Column v={value} place={10} />
      <Column v={value} place={1} />
      <Text style={[styles.digit, styles.pct]} allowFontScaling={false}>
        %
      </Text>
    </View>
  );
}

/** the On switch: the knob stretches as it travels, the pill takes the light's colour, and the bulb
 * in the knob lights with the filament */
export function LampSwitch({ tog, glow, accent, w, h }: { tog: SharedValue<number>; glow: SharedValue<number>; accent: SharedValue<string>; w: number; h: number }) {
  const knobD = h - 6;
  const pill = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(tog.get(), [0, 1], [COLOR.toggleOff, accent.get()]) }));
  const knob = useAnimatedStyle(() => {
    const x = tog.get();
    const stretch = 4 * x * (1 - x) * 12;
    return { width: knobD + stretch, transform: [{ translateX: 3 + x * (w - knobD - 6) - stretch / 2 }] };
  });
  const onText = useAnimatedStyle(() => ({ opacity: tog.get(), transform: [{ translateX: (1 - tog.get()) * -6 }] }));
  const offText = useAnimatedStyle(() => ({ opacity: 1 - tog.get(), transform: [{ translateX: tog.get() * 6 }] }));
  const icon = useAnimatedProps(() => {
    const c = interpolateColor(Math.min(1, glow.get() * 1.3), [0, 1], ['#8B858D', accent.get()]);
    return { fill: c, stroke: c };
  });
  return (
    <Animated.View style={[styles.pill, { width: w, height: h, borderRadius: h / 2 }, pill]}>
      <Animated.Text style={[styles.label, { left: 16 }, onText]} allowFontScaling={false}>
        On
      </Animated.Text>
      <Animated.Text style={[styles.label, { right: 14 }, offText]} allowFontScaling={false}>
        Off
      </Animated.Text>
      <Animated.View style={[styles.knob, { height: knobD, borderRadius: knobD / 2 }, knob]}>
        <Svg width={14} height={16} viewBox="0 0 14 16">
          <AnimatedPath d="M7 1.5 a5 5 0 0 0 -3 9 V12 h6 V10.5 a5 5 0 0 0 -3 -9 Z M5 14 h4" strokeWidth={1.2} strokeLinejoin="round" animatedProps={icon} />
        </Svg>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', height: LH, overflow: 'hidden' },
  column: { height: LH, overflow: 'hidden', alignItems: 'center' },
  digit: { width: DW, height: LH, textAlign: 'center', fontFamily: FONT.bold, fontSize: 25, lineHeight: LH, color: COLOR.dialValue, fontVariant: ['tabular-nums'] },
  pct: { width: 22 },
  pill: { position: 'absolute', justifyContent: 'center' },
  label: { position: 'absolute', fontFamily: FONT.medium, fontSize: 15, color: COLOR.ink },
  knob: { position: 'absolute', left: 0, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
});
