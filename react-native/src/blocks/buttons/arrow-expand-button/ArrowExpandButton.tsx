import { Canvas, Group, Path, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Expands and contracts by itself, as if a pointer kept hovering over it. */
  autoPlay?: boolean;
  /** How long it stays expanded after the finger lifts, so it isn't hidden under the thumb. */
  lingerMs?: number;
};

const HEIGHT = 48;
// The label starts to the right of the circle; the circle grows over it.
const LABEL_LEFT = 65;
const PAD_RIGHT = 31;
const EXPAND_MS = 450;
const AUTOPLAY_REST_MS = 1300;
const AUTOPLAY_HOLD_MS = 1700;

const INK = '#22242f';
// Tip of the chevron at rest, how far the arrow travels, and the length of the tail it grows.
const TIP_X = 28.5;
const ARROW_SHIFT = 15;
const TAIL = 18;
const HEAD = 6.5;

// Slow start, hard brake: the circle seems to gather itself before it sweeps across.
const EASE_SWEEP = Easing.bezier(0.65, 0, 0.076, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const CY = HEIGHT / 2;
const CHEVRON = `M${TIP_X - HEAD} ${CY - HEAD}L${TIP_X} ${CY}L${TIP_X - HEAD} ${CY + HEAD}`;
const TAIL_PATH = `M${TIP_X - TAIL} ${CY}L${TIP_X - 1} ${CY}`;

function Surface({ w, expand }: { w: number; expand: SharedValue<number> }) {
  const width = useDerivedValue(() => HEIGHT + (w - HEIGHT) * expand.get());
  const slide = useDerivedValue(() => [{ translateX: ARROW_SHIFT * expand.get() }]);

  return (
    <Canvas style={{ width: w, height: HEIGHT }}>
      <RoundedRect x={0} y={0} width={width} height={HEIGHT} r={HEIGHT / 2} color={INK} />
      <Group transform={slide}>
        <Path path={TAIL_PATH} style="stroke" strokeWidth={2} strokeCap="round" color="#ffffff" opacity={expand} />
        <Path path={CHEVRON} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#ffffff" />
      </Group>
    </Canvas>
  );
}

/**
 * "LEARN MORE" with a dark circle holding a chevron. While pressed (or by itself with `autoPlay`)
 * the circle sweeps across into a full pill, the chevron slides right and grows a tail, and the
 * label turns white; then it all contracts again.
 */
export function ArrowExpandButton({ label = 'LEARN MORE', onPress, autoPlay = false, lingerMs = 900 }: Props) {
  const reducedMotion = useReducedMotion();
  const [width, setWidth] = useState<number | null>(null);
  const [pressed, setPressed] = useState(false);
  const [lingering, setLingering] = useState(false);
  const [auto, setAuto] = useState(false);
  const lingerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const expanded = pressed || lingering || auto;
  const expand = useSharedValue(0);

  useEffect(() => {
    expand.set(withTiming(expanded ? 1 : 0, { duration: EXPAND_MS, easing: EASE_SWEEP }));
  }, [expanded, expand]);

  useEffect(() => {
    // The loop is pure motion, so it is dropped when the system asks for reduced motion.
    if (!autoPlay || reducedMotion) return;
    const timer = setTimeout(() => setAuto(!auto), auto ? AUTOPLAY_HOLD_MS : AUTOPLAY_REST_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, auto]);

  useEffect(() => () => {
    if (lingerTimer.current) clearTimeout(lingerTimer.current);
  }, []);

  const labelStyle = useAnimatedStyle(() => ({
    color: interpolateColor(expand.get(), [0, 1], [INK, '#ffffff']),
  }));

  const onLayout = (e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        if (lingerTimer.current) clearTimeout(lingerTimer.current);
        setPressed(true);
        setLingering(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => {
        setPressed(false);
        lingerTimer.current = setTimeout(() => setLingering(false), lingerMs);
      }}
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
        {width != null && (
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Surface w={width} expand={expand} />
          </View>
        )}
        <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: HEIGHT,
    paddingLeft: LABEL_LEFT,
    paddingRight: PAD_RIGHT,
    justifyContent: 'center',
  },
  label: {
    fontSize: 15,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
});
