import { Blur, BlurMask, Canvas, Group, RoundedRect, SweepGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
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
   * `press` (default): the light runs around the border while pressed/hovered, then finishes
   * its lap and settles back into the top highlight. `always`: it never stops.
   */
  mode?: 'press' | 'always';
};

const HEIGHT = 54;
const PAD_X = 38;
// The canvas overflows the button by this much on every side to fit the bloom and the shadow.
const BLEED = 36;
const LAP_MS = 2200;
// How far (in laps) the light must still travel after release, so the run-out isn't hidden under the thumb.
const MIN_RUNOUT = 0.5;

const TAU = Math.PI * 2;
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
// Starts at 1.25× average speed and lands with zero velocity: paired with a 1.25× duration it
// picks up the linear loop without a speed jump.
const EASE_SETTLE = Easing.bezier(0.4, 0.5, 0.6, 1);

const CLEAR_WHITE = 'rgba(255,255,255,0)';
const CLEAR_BLUE = 'rgba(48,32,255,0)';

// Sweep positions run clockwise from 3 o'clock, so 0.75 is the top of the button.
const HIGHLIGHT_COLORS = [CLEAR_BLUE, CLEAR_BLUE, '#3a26ff', '#b266ff', '#ffffff', '#ffffff', '#b266ff', '#3a26ff', CLEAR_BLUE, CLEAR_BLUE];
const HIGHLIGHT_STOPS = [0, 0.55, 0.585, 0.615, 0.645, 0.855, 0.885, 0.915, 0.95, 1];
const HIGHLIGHT_BLOOM = [CLEAR_WHITE, CLEAR_WHITE, 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0.85)', CLEAR_WHITE, CLEAR_WHITE];
const HIGHLIGHT_BLOOM_STOPS = [0, 0.63, 0.69, 0.81, 0.87, 1];

// The coloured head leads the white highlight by about a third of a lap.
const COMET_OFFSET = 0.75;
const COMET_COLORS = [CLEAR_BLUE, 'rgba(48,32,255,0.6)', '#3a26ff', '#ea3dff', '#ea3dff', '#4a2cff', CLEAR_BLUE, CLEAR_BLUE];
const COMET_STOPS = [0, 0.08, 0.24, 0.3, 0.37, 0.43, 0.5, 1];

function Surface({
  w,
  h,
  angle,
  comet,
}: {
  w: number;
  h: number;
  angle: SharedValue<number>;
  comet: SharedValue<number>;
}) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const c = vec(x + w / 2, y + h / 2);

  const highlightSpin = useDerivedValue(() => [{ rotate: TAU * angle.get() }]);
  const cometSpin = useDerivedValue(() => [{ rotate: TAU * (angle.get() + COMET_OFFSET) }]);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <RoundedRect x={x} y={y + 6} width={w} height={h} r={r} color="rgba(0,0,0,0.55)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#000000" />
      <RoundedRect
        x={x + 0.5}
        y={y + 0.5}
        width={w - 1}
        height={h - 1}
        r={r - 0.5}
        style="stroke"
        strokeWidth={1}
        color="rgba(255,255,255,0.035)"
      />

      <Group opacity={comet}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r} style="stroke" strokeWidth={4}>
          <SweepGradient c={c} origin={c} transform={cometSpin} colors={COMET_COLORS} positions={COMET_STOPS} />
          <Blur blur={4.5} />
        </RoundedRect>
        <RoundedRect x={x + 1} y={y + 1} width={w - 2} height={h - 2} r={r - 1} style="stroke" strokeWidth={2}>
          <SweepGradient c={c} origin={c} transform={cometSpin} colors={COMET_COLORS} positions={COMET_STOPS} />
        </RoundedRect>
      </Group>

      <RoundedRect x={x} y={y} width={w} height={h} r={r} style="stroke" strokeWidth={3}>
        <SweepGradient
          c={c}
          origin={c}
          transform={highlightSpin}
          colors={HIGHLIGHT_BLOOM}
          positions={HIGHLIGHT_BLOOM_STOPS}
        />
        <Blur blur={5} />
      </RoundedRect>
      <RoundedRect x={x + 1} y={y + 1} width={w - 2} height={h - 2} r={r - 1} style="stroke" strokeWidth={2}>
        <SweepGradient
          c={c}
          origin={c}
          transform={highlightSpin}
          colors={HIGHLIGHT_COLORS}
          positions={HIGHLIGHT_STOPS}
        />
      </RoundedRect>
    </Canvas>
  );
}

export function GlowBorderButton({ label = 'Let me glow', onPress, mode = 'press' }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);

  const running = mode === 'always' || pressed || hovered;

  // Measured in laps; whole numbers put the white highlight back at the top.
  const angle = useSharedValue(0);
  const comet = useSharedValue(0);
  const hasRun = useRef(false);

  useEffect(() => {
    if (reducedMotion) {
      comet.set(withTiming(running ? 1 : 0, { duration: 200 }));
      return;
    }
    const from = angle.get();
    if (running) {
      hasRun.current = true;
      comet.set(withTiming(1, { duration: 350 }));
      angle.set(withRepeat(withTiming(from + 1, { duration: LAP_MS, easing: Easing.linear }), -1, false));
    } else if (hasRun.current) {
      // Nothing to settle on mount: without this guard the highlight would do a lap by itself.
      let runout = Math.ceil(from) - from;
      if (runout < MIN_RUNOUT) runout += 1;
      const duration = 1.25 * runout * LAP_MS;
      angle.set(withTiming(from + runout, { duration, easing: EASE_SETTLE }));
      comet.set(withTiming(0, { duration, easing: Easing.inOut(Easing.quad) }));
    }
  }, [running, reducedMotion, angle, comet]);

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
            <Surface w={size.w} h={size.h} angle={angle} comet={comet} />
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
    fontSize: 17,
    fontWeight: '500',
  },
});
