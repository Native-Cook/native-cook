import { Canvas, LinearGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Sways through the tilt by itself, as if a pointer were circling over it, with rests in between. */
  autoPlay?: boolean;
};

// Fixed size: the tilt is measured against it and the touch target must not move with the pill.
const WIDTH = 190;
const HEIGHT = 58;
const MAX_TILT_DEG = 22;
const PERSPECTIVE = 520;
// The label floats this far above the pill, so it slides away from the raised side.
const LIFT = 26;
// The pill grows a little while it is tilted, as in the reference's hover.
const GROW = 0.06;

const TAU = Math.PI * 2;
// One lap of the imaginary pointer around the pill.
const LAP_MS = 3600;
const AUTOPLAY_PAUSE_MS = 1300;
const AUTOPLAY_RAMP_MS = 600;
// How far from the centre the imaginary pointer circles, 1 being the pill's edge.
const AUTOPLAY_REACH = 0.9;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.inOut(Easing.cubic);

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Violet-to-blue pill that tilts in 3D so the side under the finger rises towards you, with the
 * label floating above it. With `autoPlay` it sways through the same tilt by itself.
 */
export function TiltButton({ label = 'BUTTON', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();

  // Where the finger is, -1…1 on both axes from the pill's centre, and how much it counts.
  const touchX = useSharedValue(0);
  const touchY = useSharedValue(0);
  const press = useSharedValue(0);
  // The imaginary pointer: its angle around the pill and how much it counts.
  const theta = useSharedValue(0);
  const sway = useSharedValue(0);

  useEffect(() => {
    // Reduced motion: no swaying, and a press only dims the pill (see below).
    if (!autoPlay || reducedMotion) return;
    let cancelled = false;
    theta.set(0);
    theta.set(withRepeat(withTiming(TAU, { duration: LAP_MS, easing: Easing.linear }), -1, false));
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) break;
        sway.set(withTiming(1, { duration: AUTOPLAY_RAMP_MS, easing: EASE_IN_OUT }));
        await sleep(AUTOPLAY_RAMP_MS + LAP_MS);
        sway.set(withTiming(0, { duration: AUTOPLAY_RAMP_MS, easing: EASE_IN_OUT }));
        await sleep(AUTOPLAY_RAMP_MS);
      }
    })();
    return () => {
      cancelled = true;
      cancelAnimation(theta);
      sway.set(withTiming(0, { duration: AUTOPLAY_RAMP_MS }));
    };
  }, [autoPlay, reducedMotion, theta, sway]);

  // The pointer the pill reacts to: the finger while pressed, otherwise the imaginary one.
  const pointer = useDerivedValue(() => {
    const s = sway.get() * AUTOPLAY_REACH;
    const p = press.get();
    const ax = Math.cos(theta.get()) * s;
    const ay = Math.sin(theta.get()) * s;
    return {
      x: ax + (touchX.get() - ax) * p,
      y: ay + (touchY.get() - ay) * p,
      amount: Math.max(sway.get(), p),
    };
  });

  const pillStyle = useAnimatedStyle(() => {
    if (reducedMotion) return { opacity: 1 - 0.18 * press.get() };
    const { x, y, amount } = pointer.get();
    return {
      transform: [
        { perspective: PERSPECTIVE },
        { rotateX: `${MAX_TILT_DEG * y}deg` },
        { rotateY: `${-MAX_TILT_DEG * x}deg` },
        { scale: 1 + GROW * amount },
      ],
    };
  });
  const labelStyle = useAnimatedStyle(() => {
    if (reducedMotion) return {};
    const { x, y, amount } = pointer.get();
    const toRad = (MAX_TILT_DEG * Math.PI) / 180;
    return {
      transform: [
        { perspective: PERSPECTIVE },
        // Standing in for translateZ: the lifted label shifts along the pill's tilted normal.
        { translateX: -LIFT * Math.sin(toRad * x) },
        { translateY: -LIFT * Math.sin(toRad * y) },
        { rotateX: `${MAX_TILT_DEG * y}deg` },
        { rotateY: `${-MAX_TILT_DEG * x}deg` },
        { scale: (1 + GROW * amount) * (1 + (LIFT / PERSPECTIVE) * amount) },
      ],
    };
  });

  const track = (e: GestureResponderEvent, duration: number) => {
    const { locationX, locationY } = e.nativeEvent;
    const x = Math.min(Math.max((locationX - WIDTH / 2) / (WIDTH / 2), -1), 1);
    const y = Math.min(Math.max((locationY - HEIGHT / 2) / (HEIGHT / 2), -1), 1);
    touchX.set(withTiming(x, { duration, easing: EASE_OUT }));
    touchY.set(withTiming(y, { duration, easing: EASE_OUT }));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      style={styles.button}
      onPress={onPress}
      onPressIn={(e) => {
        track(e, 0);
        // Under reduced motion this is the dimming fade, which must still run.
        press.set(withTiming(1, { duration: 220, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onTouchMove={(e) => track(e, 90)}
      onPressOut={() => {
        press.set(withTiming(0, { duration: 500, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      }}
      pressRetentionOffset={16}
    >
      {/* Children ignore touches so locations are always measured against the untilted target. */}
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, pillStyle]}>
        <Canvas style={styles.fill}>
          <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT} r={HEIGHT / 2}>
            <LinearGradient start={vec(0, HEIGHT)} end={vec(WIDTH, 0)} colors={['#5d1bee', '#12a4f1']} />
          </RoundedRect>
        </Canvas>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.labelLayer, labelStyle]}>
        <Animated.Text style={styles.label} numberOfLines={1}>
          {label}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
  },
  fill: {
    width: WIDTH,
    height: HEIGHT,
  },
  labelLayer: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#ffffff',
    fontSize: 21,
    fontWeight: '700',
    letterSpacing: 1.5,
  },
});
