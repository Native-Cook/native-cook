import { Canvas, Circle, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** The button has no text, so this is what screen readers announce. */
  label?: string;
  onPress?: () => void;
  /**
   * The pulse is ambient and runs either way; the flag only exists so every gallery button shares
   * the same API.
   */
  autoPlay?: boolean;
};

const SIZE = 56;
// The canvas overflows the button by this much on every side to fit the rings.
const BLEED = 24;
const PULSE_MS = 2000;
// The ring is gone after this share of the cycle; the rest is a pause.
const PULSE_ACTIVE = 0.7;
// How far the ambient ring and the tap ring travel beyond the button's edge.
const PULSE_SPREAD = 10;
const BURST_SPREAD = 18;
const BURST_MS = 650;

const YELLOW = '#ffbd0b';
const PLUS = 'M-11.5 0H11.5M0 -11.5V11.5';

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function Surface({
  clock,
  burst,
  still,
}: {
  clock: SharedValue<number>;
  burst: SharedValue<number>;
  still: boolean;
}) {
  const c = BLEED + SIZE / 2;
  const r = SIZE / 2;

  const pulse = useDerivedValue(() => Math.min(clock.get() / PULSE_ACTIVE, 1));
  // The ring leaves quickly and slows down, staying strong until it is most of the way out.
  // Reduced motion swaps it for a faint resting halo.
  const pulseRadius = useDerivedValue(() => {
    const u = pulse.get();
    return r + PULSE_SPREAD * (still ? 0.6 : 1 - (1 - u) * (1 - u));
  });
  const pulseOpacity = useDerivedValue(() => (still ? 0.28 : 0.5 * (1 - pulse.get() * pulse.get())));
  const burstRadius = useDerivedValue(() => r + BURST_SPREAD * (still ? 0.6 : burst.get()));
  const burstOpacity = useDerivedValue(() => {
    const b = burst.get();
    return b <= 0 ? 0 : 0.55 * (1 - b);
  });

  return (
    <Canvas style={{ width: SIZE + BLEED * 2, height: SIZE + BLEED * 2 }}>
      <Circle cx={c} cy={c} r={burstRadius} color={YELLOW} opacity={burstOpacity} />
      <Circle cx={c} cy={c} r={pulseRadius} color={YELLOW} opacity={pulseOpacity} />
      <Circle cx={c} cy={c} r={r} color={YELLOW} />
      <Path
        path={PLUS}
        transform={[{ translateX: c }, { translateY: c }]}
        style="stroke"
        strokeWidth={2.4}
        strokeCap="round"
        color="#ffffff"
      />
    </Canvas>
  );
}

/**
 * Round yellow "+" action button with a ring that keeps pulsing outward from it. A tap sends out
 * one larger ring of its own.
 */
export function PulseFabButton({ label = 'Add', onPress }: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);

  // Position in the pulse cycle, 0…1.
  const clock = useSharedValue(0);
  const burst = useSharedValue(0);

  useEffect(() => {
    if (reducedMotion) return;
    clock.set(0);
    clock.set(withRepeat(withTiming(1, { duration: PULSE_MS, easing: Easing.linear }), -1, false));
  }, [reducedMotion, clock]);

  const fireBurst = () => {
    // Under reduced motion the ring only fades, which must still run.
    const never = { reduceMotion: ReduceMotion.Never };
    burst.set(
      withSequence(
        withTiming(0.001, { duration: 0, ...never }),
        withTiming(1, { duration: BURST_MS, easing: Easing.out(Easing.cubic), ...never }),
        withTiming(0, { duration: 0, ...never }),
      ),
    );
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => {
        onPress?.();
        fireBurst();
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface clock={clock} burst={burst} still={reducedMotion} />
        </View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: SIZE,
    height: SIZE,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
});
