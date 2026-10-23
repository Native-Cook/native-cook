import { Canvas, Group, Path, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
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
  /** Replays the reload by itself with a pause between runs. */
  autoPlay?: boolean;
};

const HEIGHT = 56;
const RADIUS = 7;
const PAD_LEFT = 26;
const ICON = 24;
const GAP = 18;
const PAD_RIGHT = 32;
// Width of the square the button collapses to while it works.
const COLLAPSED = 60;
const SPIN_TURNS = 2;
const SPIN_MS = 1300;
const AUTOPLAY_PAUSE_MS = 1500;
// Label width assumed until the real one has been measured.
const LABEL_WIDTH_GUESS = 80;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_SPIN = Easing.bezier(0.45, 0, 0.55, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

// Two arcs (radius 9) chasing each other clockwise, each ending in an arrowhead. Centred on the origin.
const ARCS = 'M-7.372 -5.162A9 9 0 0 1 8.863 -1.563M7.372 5.162A9 9 0 0 1 -8.863 1.563';
const HEADS = 'M5.8 -1.6H12.2L9 2.4ZM-12.2 1.6H-5.8L-9 -2.4Z';

type Motion = {
  /** 1 = full width with the label, 0 = collapsed to the icon. */
  open: SharedValue<number>;
  /** Icon rotation in turns. */
  spin: SharedValue<number>;
  /** 0 = blue, 1 = green (done). */
  ok: SharedValue<number>;
};

function Surface({ width, open, spin, ok }: { width: number } & Motion) {
  const w = useDerivedValue(() => COLLAPSED + (width - COLLAPSED) * open.get());
  const x = useDerivedValue(() => (width - w.get()) / 2);
  // The icon sits in the leading padding and drifts to the middle of the square.
  const icon = useDerivedValue(() => [
    { translateX: x.get() + COLLAPSED / 2 + (PAD_LEFT + ICON / 2 - COLLAPSED / 2) * open.get() },
    { translateY: HEIGHT / 2 },
    { rotate: TAU * spin.get() },
  ]);

  return (
    <Canvas style={{ width, height: HEIGHT }}>
      <RoundedRect x={x} y={0} width={w} height={HEIGHT} r={RADIUS} color="#c7dff3" />
      <RoundedRect x={x} y={0} width={w} height={HEIGHT} r={RADIUS} color="#cbf8d0" opacity={ok} />
      <Group transform={icon}>
        <Path path={ARCS} style="stroke" strokeWidth={2} strokeCap="round" color="#2e3ccb" />
        <Path path={HEADS} color="#2e3ccb" />
        <Group opacity={ok}>
          <Path path={ARCS} style="stroke" strokeWidth={2} strokeCap="round" color="#42c258" />
          <Path path={HEADS} color="#42c258" />
        </Group>
      </Group>
    </Canvas>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Pale blue "Reload" button. A tap collapses it to a square around its refresh icon, the icon
 * spins while it works, turns green when done, and the button opens back up before returning to blue.
 */
export function ReloadButton({ label = 'Reload', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [labelWidth, setLabelWidth] = useState(LABEL_WIDTH_GUESS);
  const [pressed, setPressed] = useState(false);
  const width = PAD_LEFT + ICON + GAP + Math.ceil(labelWidth) + PAD_RIGHT;

  const open = useSharedValue(1);
  const spin = useSharedValue(0);
  const ok = useSharedValue(0);
  // A small swell before the collapse, like the reference's wind-up.
  const swell = useSharedValue(1);

  const busy = useRef(false);

  const reload = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;

    if (reducedMotion) {
      // No collapse or spin: just the colour change.
      await sleep(500);
      ok.set(withTiming(1, { duration: 300 }));
      await sleep(1600);
      ok.set(withTiming(0, { duration: 450 }));
      await sleep(450);
      busy.current = false;
      return;
    }

    swell.set(withTiming(1.05, { duration: 200, easing: EASE_OUT }));
    await sleep(200);
    swell.set(withTiming(1, { duration: 420, easing: EASE_IN_OUT }));
    open.set(withTiming(0, { duration: 420, easing: EASE_IN_OUT }));
    await sleep(500);

    // Whole turns, so the icon comes to rest exactly as it started.
    spin.set(0);
    spin.set(withTiming(SPIN_TURNS, { duration: SPIN_MS, easing: EASE_SPIN }));
    await sleep(SPIN_MS - 250);
    ok.set(withTiming(1, { duration: 300 }));
    await sleep(950);

    open.set(withTiming(1, { duration: 460, easing: EASE_IN_OUT }));
    await sleep(460 + 1100);
    ok.set(withTiming(0, { duration: 450 }));
    await sleep(450);
    busy.current = false;
  }, [reducedMotion, open, spin, ok, swell]);

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) return;
        await reload();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [autoPlay, reload]);

  const swellStyle = useAnimatedStyle(() => ({ transform: [{ scale: swell.get() }] }));
  // The window the label is seen through: it follows the shrinking face and clips the label.
  const windowStyle = useAnimatedStyle(() => {
    const w = COLLAPSED + (width - COLLAPSED) * open.get();
    return { left: (width - w) / 2, width: w };
  });
  // The label leaves upwards as the button closes and drops back in as it opens.
  const labelStyle = useAnimatedStyle(() => ({
    opacity: open.get() * open.get(),
    transform: [{ translateY: -(1 - open.get()) * HEIGHT * 0.55 }],
    color: interpolateColor(ok.get(), [0, 1], ['#3a63b7', '#46c357']),
  }));

  const onLabelLayout = (e: LayoutChangeEvent) => {
    const measured = e.nativeEvent.layout.width;
    setLabelWidth((prev) => (Math.abs(prev - measured) < 0.5 ? prev : measured));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => {
        onPress?.();
        reload();
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
          { width, height: HEIGHT },
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <Animated.View style={[{ width, height: HEIGHT }, swellStyle]}>
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Surface width={width} open={open} spin={spin} ok={ok} />
          </View>
          <Animated.View pointerEvents="none" style={[styles.window, windowStyle]}>
            <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
              {label}
            </Animated.Text>
          </Animated.View>
          {/* Off-screen copy that only measures the label, so the width never depends on the clipped one. */}
          <Text style={[styles.label, styles.measure]} numberOfLines={1} onLayout={onLabelLayout}>
            {label}
          </Text>
        </Animated.View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  window: {
    position: 'absolute',
    top: 0,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
    justifyContent: 'center',
  },
  label: {
    position: 'absolute',
    left: PAD_LEFT + ICON + GAP,
    fontSize: 24,
    fontWeight: '600',
    color: '#3a63b7',
  },
  measure: {
    opacity: 0,
  },
});
