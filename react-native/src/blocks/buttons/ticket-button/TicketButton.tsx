import { Canvas, Path, Rect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Fills and empties by itself, as if a pointer kept hovering in and out. */
  autoPlay?: boolean;
};

const HEIGHT = 48;
const PAD_X = 62;
// The label rolls inside a window this tall, like a one-line ticker.
const LINE = 24;
const FILL_MS = 550;
const ROLL_MS = 300;
const AUTOPLAY_PAUSE_MS = 1300;
const AUTOPLAY_HOLD_MS = 1500;

const PAPER = '#fffdfe';
const TEAL = '#46c9cb';
const INK = '#141016';

// How far the corners of the moving edge trail behind its middle, as a fraction of the height.
const LAG = 0.45;
// Circle-to-cubic constant for the rounded corners of the edge.
const K = 0.5523;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT_CUBIC = Easing.out(Easing.cubic);

/**
 * `phase` runs 0 → 1 while the teal sheet rises from the bottom and 1 → 2 while the white sheet
 * drops back from the top; 2 looks exactly like 0, so it can snap back unnoticed.
 */
function Surface({ w, h, phase, still }: { w: number; h: number; phase: SharedValue<number>; still: boolean }) {
  const filling = useDerivedValue(() => phase.get() <= 1);
  const baseColor = useDerivedValue(() => (filling.get() ? PAPER : TEAL));
  const sheetColor = useDerivedValue(() => (filling.get() ? TEAL : PAPER));
  const progress = useDerivedValue(() => (filling.get() ? phase.get() : phase.get() - 1));
  // Reduced motion: the sheet does not travel, it fades in over the whole button.
  const sheetOpacity = useDerivedValue(() => (still ? progress.get() : 1));

  const sheet = useDerivedValue(() => {
    const p = still ? 1 : progress.get();
    const lag = h * LAG * (1 - p);
    // A full-width dome at first that flattens into a straight edge with rounded corners.
    const rx = w * (0.06 + 0.44 * (1 - p) ** 3);
    if (filling.get()) {
      const edge = h * (1 - p);
      const side = edge + lag;
      return (
        `M0 ${h}L0 ${side}C0 ${side - K * lag} ${rx - K * rx} ${edge} ${rx} ${edge}` +
        `L${w - rx} ${edge}C${w - rx + K * rx} ${edge} ${w} ${side - K * lag} ${w} ${side}L${w} ${h}Z`
      );
    }
    const edge = h * p;
    const side = edge - lag;
    return (
      `M0 0L0 ${side}C0 ${side + K * lag} ${rx - K * rx} ${edge} ${rx} ${edge}` +
      `L${w - rx} ${edge}C${w - rx + K * rx} ${edge} ${w} ${side + K * lag} ${w} ${side}L${w} 0Z`
    );
  });

  return (
    <Canvas style={{ width: w, height: h }}>
      <Rect x={0} y={0} width={w} height={h} color={baseColor} />
      <Path path={sheet} color={sheetColor} opacity={sheetOpacity} />
    </Canvas>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * White "GET TICKET" slab. While pressed (or by itself with `autoPlay`) a teal sheet with a
 * rounded leading edge rises from the bottom and the label rolls over to white; on release a white
 * sheet drops back down from the top.
 */
export function TicketButton({ label = 'GET TICKET', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const phase = useSharedValue(0);
  // 0 shows the dark label, 1 the white one.
  const roll = useSharedValue(0);

  const held = useRef(false);
  const auto = useRef(false);
  const shown = useRef(false);
  const busy = useRef(false);

  /** Plays whole sweeps until the button shows what is wanted, so a quick tap never cuts one short. */
  const sync = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    while (shown.current !== (held.current || auto.current)) {
      const on = !shown.current;
      // Reduced motion turns the sweep into a fade, which must still run.
      const timing = { duration: FILL_MS, easing: EASE_OUT_CUBIC, reduceMotion: ReduceMotion.Never };
      phase.set(
        on ? withSequence(withTiming(0, { duration: 0, reduceMotion: ReduceMotion.Never }), withTiming(1, timing)) : withTiming(2, timing),
      );
      roll.set(withTiming(on ? 1 : 0, { duration: ROLL_MS, easing: EASE_OUT_CUBIC, reduceMotion: ReduceMotion.Never }));
      await sleep(FILL_MS);
      shown.current = on;
    }
    busy.current = false;
  }, [phase, roll]);

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) break;
        auto.current = true;
        sync();
        await sleep(FILL_MS + AUTOPLAY_HOLD_MS);
        auto.current = false;
        sync();
        await sleep(FILL_MS);
      }
    })();
    return () => {
      cancelled = true;
      auto.current = false;
      sync();
    };
  }, [autoPlay, sync]);

  const inkStyle = useAnimatedStyle(() =>
    reducedMotion ? { opacity: 1 - roll.get() } : { transform: [{ translateY: -LINE * roll.get() }] },
  );
  const paperStyle = useAnimatedStyle(() =>
    reducedMotion ? { opacity: roll.get() } : { transform: [{ translateY: LINE * (1 - roll.get()) }] },
  );

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
        held.current = true;
        sync();
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => {
        setPressed(false);
        held.current = false;
        sync();
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
        {size && (
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Surface w={size.w} h={size.h} phase={phase} still={reducedMotion} />
          </View>
        )}
        <View style={styles.line}>
          <Animated.Text style={[styles.label, styles.ink, inkStyle]} numberOfLines={1}>
            {label}
          </Animated.Text>
          <Animated.Text style={[styles.label, styles.paper, paperStyle]} numberOfLines={1}>
            {label}
          </Animated.Text>
        </View>
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
  line: {
    height: LINE,
    overflow: 'hidden',
  },
  label: {
    fontFamily: Platform.select({ ios: 'ui-monospace', default: 'monospace' }),
    fontSize: 16,
    lineHeight: LINE,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  ink: {
    color: INK,
  },
  paper: {
    position: 'absolute',
    left: 0,
    top: 0,
    color: '#ffffff',
  },
});
