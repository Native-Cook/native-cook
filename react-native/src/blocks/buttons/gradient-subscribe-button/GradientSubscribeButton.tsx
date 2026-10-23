import { Blur, Canvas, Group, LinearGradient, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /**
   * Keep the gradient drifting and the glow lit all the time. When off, the button rests on its
   * magenta face and only drifts while pressed/hovered, then finishes its cycle and settles.
   */
  autoPlay?: boolean;
};

const HEIGHT = 54;
const PAD_X = 40;
// The canvas overflows the button by this much on every side to fit the glow.
const BLEED = 44;
// One full trip through the gradient, which is four button-widths long.
const CYCLE_MS = 3300;
const SPAN = 4;
// How far (in cycles) the gradient must still travel after release, so the run-out isn't hidden under the thumb.
const MIN_RUNOUT = 0.35;
const GLOW_BLUR = 13;
const GLOW_OPACITY = 0.78;
const LETTER_SPACING = 2.6;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
// Starts at 1.25× average speed and lands with zero velocity: paired with a 1.25× duration it
// picks up the linear loop without a speed jump.
const EASE_SETTLE = Easing.bezier(0.4, 0.5, 0.6, 1);

// Magenta → crimson → orange → back to magenta, tiled so the drift never shows a seam.
const COLORS = ['#e830c8', '#d8252f', '#ff9a10', '#e830c8'];
const STOPS = [0, 1 / 3, 2 / 3, 1];

function Surface({
  w,
  h,
  phase,
  glow,
}: {
  w: number;
  h: number;
  phase: SharedValue<number>;
  glow: SharedValue<number>;
}) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const cy = y + h / 2;

  // The gradient slides left, so the colours travel from the right edge to the left one.
  const start = useDerivedValue(() => ({ x: x - (phase.get() % 1) * SPAN * w, y: cy }));
  const end = useDerivedValue(() => ({ x: x + (1 - (phase.get() % 1)) * SPAN * w, y: cy }));
  const glowOpacity = useDerivedValue(() => glow.get() * GLOW_OPACITY);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <Group opacity={glowOpacity}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r}>
          <LinearGradient start={start} end={end} colors={COLORS} positions={STOPS} mode="repeat" />
          <Blur blur={GLOW_BLUR} />
        </RoundedRect>
      </Group>
      <RoundedRect x={x} y={y} width={w} height={h} r={r}>
        <LinearGradient start={start} end={end} colors={COLORS} positions={STOPS} mode="repeat" />
      </RoundedRect>
    </Canvas>
  );
}

export function GradientSubscribeButton({ label = 'SUBSCRIBE', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);

  const running = autoPlay || pressed || hovered;

  // Measured in cycles; whole numbers put the magenta face back under the label.
  const phase = useSharedValue(0);
  const glow = useSharedValue(0);
  const hasRun = useRef(false);

  useEffect(() => {
    if (reducedMotion) {
      // Reanimated skips animations outright under reduced motion; this short fade opts back in.
      glow.set(withTiming(running ? 1 : 0, { duration: 200, reduceMotion: ReduceMotion.Never }));
      return;
    }
    const from = phase.get();
    if (running) {
      hasRun.current = true;
      glow.set(withTiming(1, { duration: 350 }));
      phase.set(withRepeat(withTiming(from + 1, { duration: CYCLE_MS, easing: Easing.linear }), -1, false));
    } else if (hasRun.current) {
      let runout = Math.ceil(from) - from;
      if (runout < MIN_RUNOUT) runout += 1;
      const duration = 1.25 * runout * CYCLE_MS;
      phase.set(withTiming(from + runout, { duration, easing: EASE_SETTLE }));
      glow.set(withTiming(0, { duration, easing: Easing.in(Easing.quad) }));
    }
  }, [running, reducedMotion, phase, glow]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev && prev.w === width && prev.h === height ? prev : { w: width, h: height }));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      onHoverIn={() => setHovered(true)}
      onHoverOut={() => setHovered(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        onLayout={onLayout}
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
        {size && (
          <View pointerEvents="none" style={styles.surface}>
            <Surface w={size.w} h={size.h} phase={phase} glow={glow} />
          </View>
        )}
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: HEIGHT,
    paddingHorizontal: PAD_X,
    alignItems: 'center',
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  label: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: LETTER_SPACING,
    // iOS adds the tracking after the last glyph too; nudge the text back to the optical centre.
    marginLeft: process.env.EXPO_OS === 'ios' ? LETTER_SPACING : 0,
  },
});
