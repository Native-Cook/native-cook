import { StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { PAY } from './motion';
import { COLORS } from './theme';

const AnimatedPath = Animated.createAnimatedComponent(Path);
const AnimatedCircle = Animated.createAnimatedComponent(Circle);

function ramp(t: number, at: number, ms: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - at) / ms));
}

const FACE = [
  'M6 16V10a4 4 0 0 1 4-4h6', 'M38 6h6a4 4 0 0 1 4 4v6', 'M48 38v6a4 4 0 0 1-4 4h-6', 'M16 48h-6a4 4 0 0 1-4-4v-6',
  'M19 20v4', 'M35 20v4', 'M27 20v10h-3', 'M20 36c4 3.4 10 3.4 14 0',
];
const DASH = 40;

function FacePart({ d, i, clock, reduced }: { d: string; i: number; clock: SharedValue<number>; reduced: boolean }) {
  const props = useAnimatedProps(() => ({
    strokeDashoffset: reduced ? 0 : DASH * (1 - ramp(clock.get(), PAY.faceAt + i * 30, PAY.faceMs * 0.6)),
  }));
  return <AnimatedPath animatedProps={props} d={d} stroke={COLORS.paper} strokeWidth={2.6} fill="none"
    strokeLinecap="round" strokeLinejoin="round" strokeDasharray={[DASH, DASH]} />;
}

/** Face ID, a spinner and a tick on black, as the pin plays after PAY. */
export function PayOverlay({ clock, width, height, active, reduced }: { clock: SharedValue<number>; width: number; height: number; active: boolean; reduced: boolean }) {
  const black = useAnimatedStyle(() => ({ opacity: ramp(clock.get(), PAY.blackAt, PAY.blackMs) }));
  const face = useAnimatedStyle(() => {
    const t = clock.get();
    return { opacity: reduced ? 0 : ramp(t, PAY.faceAt, 120) * (1 - ramp(t, PAY.spinAt - 80, 120)) };
  });
  const spin = useAnimatedStyle(() => {
    const t = clock.get();
    return {
      opacity: reduced ? 0 : ramp(t, PAY.spinAt - 60, 120) * (1 - ramp(t, PAY.checkAt - 40, 100)),
      transform: [{ rotate: reduced ? '0deg' : `${(t - PAY.spinAt) * 0.9}deg` }],
    };
  });
  const ring = useAnimatedProps(() => ({ strokeDashoffset: reduced ? 0 : 120 * (1 - ramp(clock.get(), PAY.checkAt, PAY.checkMs * 0.6)) }));
  const tick = useAnimatedProps(() => ({ strokeDashoffset: reduced ? 0 : 30 * (1 - ramp(clock.get(), PAY.checkAt + PAY.checkMs * 0.35, PAY.checkMs * 0.65)) }));
  const check = useAnimatedStyle(() => ({ opacity: ramp(clock.get(), PAY.checkAt - 40, 80) }));
  const cx = width / 2 - 27;
  const cy = height / 2 - 27;
  return (
    <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.night }, black]} pointerEvents="none"
      accessible={active} accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}
      accessibilityRole="progressbar" accessibilityLabel="Payment processing">
      <Animated.View style={[styles.mark, { left: cx, top: cy }, face]}>
        <Svg width={54} height={54} viewBox="0 0 54 54">
          {FACE.map((d, i) => <FacePart key={d} d={d} i={i} clock={clock} reduced={reduced} />)}
        </Svg>
      </Animated.View>
      <Animated.View style={[styles.mark, { left: cx, top: cy }, spin]}>
        <Svg width={54} height={54} viewBox="0 0 54 54">
          <Path d="M27 9a18 18 0 1 1-18 18" stroke={COLORS.paper} strokeWidth={2.6} fill="none" strokeLinecap="round" />
        </Svg>
      </Animated.View>
      <Animated.View style={[styles.mark, { left: cx, top: cy }, check]}>
        <Svg width={54} height={54} viewBox="0 0 54 54">
          <AnimatedCircle animatedProps={ring} cx={27} cy={27} r={18} stroke={COLORS.paper} strokeWidth={2.6} fill="none"
            strokeDasharray={[120, 120]} transform="rotate(-90 27 27)" />
          <AnimatedPath animatedProps={tick} d="M20 27.5l5 5 9-10" stroke={COLORS.paper} strokeWidth={2.6} fill="none"
            strokeLinecap="round" strokeLinejoin="round" strokeDasharray={[30, 30]} />
        </Svg>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({ mark: { position: 'absolute', width: 54, height: 54 } });
