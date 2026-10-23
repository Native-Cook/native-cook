import { BlurMask, Canvas, Group, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Replays the confirmation by itself with a pause between runs. */
  autoPlay?: boolean;
};

const HEIGHT = 50;
const PAD_X = 46;
// The canvas overflows the button by this much on every side to fit the glow.
const BLEED = 28;
// Gap that opens between the pill and its glow when it is clicked.
const RING = 3.4;
const HOLD_MS = 2000;
const AUTOPLAY_PAUSE_MS = 1400;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const CHECK = 'M-9.5 0.5L-3.5 6.5L10 -7.5';

type Motion = {
  glow: SharedValue<number>;
  ring: SharedValue<number>;
  done: SharedValue<number>;
  check: SharedValue<number>;
};

function Surface({ w, h, glow, ring, done, check }: { w: number; h: number } & Motion) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const cx = x + w / 2;
  const cy = y + h / 2;

  // The glow sits just outside the pill and is pushed further out by the ring.
  const halo = useDerivedValue(() => {
    const d = 1 + RING * ring.get();
    return { x: x - d, y: y - d, width: w + d * 2, height: h + d * 2, r: r + d };
  });
  const haloX = useDerivedValue(() => halo.get().x);
  const haloY = useDerivedValue(() => halo.get().y);
  const haloW = useDerivedValue(() => halo.get().width);
  const haloH = useDerivedValue(() => halo.get().height);
  const haloR = useDerivedValue(() => halo.get().r);

  const ringWidth = useDerivedValue(() => RING * ring.get());
  const ringX = useDerivedValue(() => x - ringWidth.get() / 2);
  const ringY = useDerivedValue(() => y - ringWidth.get() / 2);
  const ringW = useDerivedValue(() => w + ringWidth.get());
  const ringH = useDerivedValue(() => h + ringWidth.get());
  const ringR = useDerivedValue(() => r + ringWidth.get() / 2);

  const checkScale = useDerivedValue(() => [{ scale: 0.4 + 0.6 * check.get() }]);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <RoundedRect x={haloX} y={haloY} width={haloW} height={haloH} r={haloR} color="rgba(186,238,51,0.8)" opacity={glow}>
        <BlurMask blur={7} style="normal" />
      </RoundedRect>
      <RoundedRect
        x={ringX}
        y={ringY}
        width={ringW}
        height={ringH}
        r={ringR}
        style="stroke"
        strokeWidth={ringWidth}
        color="#f1f4ee"
        opacity={ring}
      />
      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#baee33" />
      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#222522" opacity={done} />
      <Group transform={[{ translateX: cx }, { translateY: cy }]}>
        <Group transform={checkScale} origin={vec(0, 0)}>
          <Path
            path={CHECK}
            style="stroke"
            strokeWidth={2.6}
            strokeCap="round"
            strokeJoin="round"
            color="#ffffff"
            opacity={check}
          />
        </Group>
      </Group>
    </Canvas>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Lime "Apply" pill. A tap lights its glow, opens a pale ring around it, then turns it charcoal
 * with a white check mark before it settles back.
 */
export function ApplyCheckButton({ label = 'Apply', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const glow = useSharedValue(0);
  const ring = useSharedValue(0);
  const done = useSharedValue(0);
  const check = useSharedValue(0);

  const busy = useRef(false);

  /** One confirmation. `hover` first lets the glow sit for a moment, like the reference's cursor arriving. */
  const confirm = useCallback(
    async (hover: boolean) => {
      if (busy.current) return;
      busy.current = true;
      // Reduced motion keeps the colour change and the check as plain fades.
      const quick = reducedMotion ? 0 : 1;

      glow.set(withTiming(quick, { duration: hover ? 400 : 300, easing: EASE_OUT }));
      if (hover) await sleep(900);
      ring.set(withTiming(quick, { duration: 300, easing: EASE_OUT }));
      await sleep(hover ? 420 : 380);

      done.set(withTiming(1, { duration: 220 }));
      check.set(withDelay(80, withTiming(1, { duration: 320, easing: EASE_OUT })));
      glow.set(withTiming(0, { duration: 260 }));
      ring.set(withTiming(0, { duration: 260 }));
      await sleep(HOLD_MS);

      check.set(withTiming(0, { duration: 140 }));
      await sleep(100);
      done.set(withTiming(0, { duration: 320 }));
      await sleep(420);
      busy.current = false;
    },
    [reducedMotion, glow, ring, done, check],
  );

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) return;
        await confirm(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [autoPlay, confirm]);

  // The label makes way for the check slightly ahead of the colour change.
  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, done.get() * 1.6) }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev && prev.w === width && prev.h === height ? prev : { w: width, h: height }));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => {
        onPress?.();
        confirm(false);
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
            <Surface w={size.w} h={size.h} glow={glow} ring={ring} done={done} check={check} />
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
    color: '#1c2118',
    fontSize: 18,
    fontWeight: '600',
  },
});
