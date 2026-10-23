import { Canvas, Circle, Group, Rect, rect, rrect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Fills and empties by itself, as if a pointer kept hovering in and out. */
  autoPlay?: boolean;
};

const HEIGHT = 56;
const PAD_X = 34;
const RADIUS = 2;
// Extra room between letters; each one is its own view so it can hop by itself.
const SPACING = 2.6;

// The colours sweep in with a round leading edge this big, relative to the button's height.
const EDGE_RADIUS = 0.6;
const SWEEP_MS = 190;
const SWEEP_GAP_MS = 110;
// Each letter swaps for a fresh copy over LETTER_MS, starting STAGGER_MS after the one before.
const LETTER_MS = 220;
const STAGGER_MS = 100;
const RISE = 12;
const AUTOPLAY_PAUSE_MS = 1200;
const AUTOPLAY_HOLD_MS = 900;

const ORANGE = '#e57e32';
const NAVY = '#1b1f65';

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT_QUAD = Easing.out(Easing.quad);

function Surface({
  w,
  h,
  orange,
  navy,
  still,
}: {
  w: number;
  h: number;
  orange: SharedValue<number>;
  navy: SharedValue<number>;
  still: boolean;
}) {
  const r = h * EDGE_RADIUS;
  // The circle's centre travels from fully outside to where its arc has cleared the far corners.
  const travel = w + r - Math.sqrt(r * r - (h * h) / 4) + 1;
  const orangeX = useDerivedValue(() => -r + travel * orange.get());
  const navyX = useDerivedValue(() => (still ? w : -r + travel * navy.get()));
  // Reduced motion: no sweep, the navy simply fades in.
  const navyOpacity = useDerivedValue(() => (still ? navy.get() : 1));
  const clip = rrect(rect(0, 0, w, h), RADIUS, RADIUS);

  return (
    <Canvas style={{ width: w, height: h }}>
      <Group clip={clip}>
        <Rect x={0} y={0} width={w} height={h} color="#000000" />
        {!still && (
          <Group color={ORANGE}>
            <Rect x={0} y={0} width={orangeX} height={h} />
            <Circle cx={orangeX} cy={h / 2} r={r} />
          </Group>
        )}
        <Group color={NAVY} opacity={navyOpacity}>
          <Rect x={0} y={0} width={navyX} height={h} />
          <Circle cx={navyX} cy={h / 2} r={r} />
        </Group>
      </Group>
    </Canvas>
  );
}

/**
 * One letter as two stacked copies: as the wave passes, the first leaves in the wave's direction
 * while the second arrives from the opposite side.
 */
function Letter({
  char,
  index,
  total,
  wave,
  direction,
}: {
  char: string;
  index: number;
  total: number;
  wave: SharedValue<number>;
  direction: SharedValue<number>;
}) {
  const leaving = useAnimatedStyle(() => {
    const t = Math.min(Math.max((wave.get() * total - index * STAGGER_MS) / LETTER_MS, 0), 1);
    const eased = t * t * (3 - 2 * t);
    return { opacity: 1 - t, transform: [{ translateY: -direction.get() * RISE * eased }] };
  });
  const arriving = useAnimatedStyle(() => {
    const t = Math.min(Math.max((wave.get() * total - index * STAGGER_MS) / LETTER_MS, 0), 1);
    const eased = t * t * (3 - 2 * t);
    return { opacity: t, transform: [{ translateY: direction.get() * RISE * (1 - eased) }] };
  });
  return (
    <View style={styles.cell}>
      <Animated.Text style={[styles.letter, leaving]}>{char}</Animated.Text>
      <Animated.Text style={[styles.letter, styles.arriving, arriving]}>{char}</Animated.Text>
    </View>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Black "SWAGGER" slab. While pressed (or by itself with `autoPlay`) orange and then navy sweep
 * across it with a round leading edge while the letters hop one after another; on release the
 * colours pull back and the letters ripple the other way.
 */
export function LetterWaveButton({ label = 'SWAGGER', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const letters = useMemo(() => Array.from(label), [label]);
  const waveMs = LETTER_MS + STAGGER_MS * Math.max(letters.length - 1, 0);

  const orange = useSharedValue(0);
  const navy = useSharedValue(0);
  // 0 → 1 as the hop travels through the label; it rests at 0.
  const wave = useSharedValue(0);
  // 1: letters leave upwards (filling), -1: downwards (emptying).
  const direction = useSharedValue(1);

  const held = useRef(false);
  const auto = useRef(false);
  const shown = useRef(false);
  const busy = useRef(false);

  /** Plays whole waves until the button shows what is wanted, so letters are never cut off mid-hop. */
  const sync = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    while (shown.current !== (held.current || auto.current)) {
      const on = !shown.current;
      const sweep = { duration: SWEEP_MS, easing: EASE_OUT_QUAD, reduceMotion: ReduceMotion.Never };
      if (on) {
        orange.set(withTiming(1, sweep));
        navy.set(withDelay(SWEEP_GAP_MS, withTiming(1, sweep), ReduceMotion.Never));
      } else {
        navy.set(withTiming(0, sweep));
        orange.set(withDelay(SWEEP_GAP_MS, withTiming(0, sweep), ReduceMotion.Never));
      }
      // Reduced motion keeps the colour change as a fade (see Surface) and leaves the letters still.
      if (!reducedMotion) {
        direction.set(on ? 1 : -1);
        wave.set(withSequence(withTiming(1, { duration: waveMs, easing: Easing.linear }), withTiming(0, { duration: 0 })));
      }
      await sleep(waveMs + 40);
      shown.current = on;
    }
    busy.current = false;
  }, [reducedMotion, waveMs, orange, navy, wave, direction]);

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) break;
        auto.current = true;
        sync();
        await sleep(waveMs + AUTOPLAY_HOLD_MS);
        auto.current = false;
        sync();
        await sleep(waveMs);
      }
    })();
    return () => {
      cancelled = true;
      auto.current = false;
      sync();
    };
  }, [autoPlay, sync, waveMs]);

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
            <Surface w={size.w} h={size.h} orange={orange} navy={navy} still={reducedMotion} />
          </View>
        )}
        <View style={styles.row} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          {letters.map((char, index) => (
            <Letter key={index} char={char} index={index} total={waveMs} wave={wave} direction={direction} />
          ))}
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
  row: {
    flexDirection: 'row',
  },
  cell: {
    paddingHorizontal: SPACING / 2,
  },
  letter: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '600',
  },
  arriving: {
    position: 'absolute',
    left: SPACING / 2,
    top: 0,
  },
});
