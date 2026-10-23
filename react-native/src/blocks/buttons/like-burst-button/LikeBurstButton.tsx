import { BlurMask, Canvas, Circle, Group, Path, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Controlled state. Leave it out to let the button keep its own. */
  value?: boolean;
  defaultValue?: boolean;
  onValueChange?: (liked: boolean) => void;
  /** Accessibility label; the button has no visible text. */
  label?: string;
  /** Likes and unlikes by itself with a pause in between. */
  autoPlay?: boolean;
};

const SIZE = 64;
const R = SIZE / 2;
// The canvas overflows the button by this much on every side to fit the shadow and the burst.
const BLEED = 28;
const C = BLEED + R;
const UNLIKE_MS = 260;
const AUTO_LIKED_MS = 2200;
const AUTO_UNLIKED_MS = 1300;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Liking is one clock in seconds; every part below is a window on it.
const T_OUTLINE_SHRINK = [0, 0.09];
const T_OUTLINE_OUT = [0.05, 0.09];
const T_SQUASH = [0, 0.3];
const T_FILL_IN = [0.06, 0.1];
const T_DISC = [0.07, 0.3];
const T_DISC_SOLID = [0.1, 0.22];
const T_HOLE = [0.22, 0.42];
const T_POP = [0.22, 0.55];
const T_TINT_OUT = [0.22, 0.3];
const T_DOTS_FLY = [0.36, 0.62];
const T_DOTS_DRIFT = [0.62, 0.95];
const T_DOTS_IN = [0.36, 0.42];
const T_DOTS_OUT = [0.58, 0.92];
const TOTAL = 1;

const RED = '#f0301f';
const HEART =
  'M0 11.5C-1.7 10.1 -13.3 2.2 -13.3 -5C-13.3 -9.7 -9.8 -12.6 -6.2 -12.6C-3.6 -12.6 -1.3 -11.2 0 -9.1' +
  'C1.3 -11.2 3.6 -12.6 6.2 -12.6C9.8 -12.6 13.3 -9.7 13.3 -5C13.3 2.2 1.7 10.1 0 11.5Z';
// The stroked heart is drawn a little smaller so its outer edge matches the filled one.
const OUTLINE_SCALE = 0.925;

// The burst: seven dots around the rim, clockwise from the top, in degrees.
const DOTS = [
  { angle: -90, color: '#f2d94e' },
  { angle: -35, color: '#c8375a' },
  { angle: 0, color: '#4466e8' },
  { angle: 45, color: '#c63be0' },
  { angle: 135, color: '#5a3de0' },
  { angle: 180, color: '#9bcf63' },
  { angle: -145, color: '#d9763a' },
];

function seg(t: number, window: number[]) {
  'worklet';
  return Math.min(1, Math.max(0, (t - window[0]) / (window[1] - window[0])));
}
function easeOut(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x) * (1 - x);
}
function easeOutBack(x: number) {
  'worklet';
  const c = 2.2;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

function Surface({ burst, fade, still }: { burst: SharedValue<number>; fade: SharedValue<number>; still: boolean }) {
  // `fade` undoes the like: the filled heart dips and gives way to the outline again.
  const emptied = useDerivedValue(() => seg(fade.get(), [0.2, 0.6]));
  const dip = useDerivedValue(() => (still ? 1 : 1 - 0.22 * Math.sin(Math.PI * fade.get())));

  const face = useDerivedValue(() => R * (1 - 0.06 * Math.sin(Math.PI * seg(burst.get(), T_SQUASH))));

  // A red disc swells behind the heart, then hollows out from the centre into a thinning ring.
  const outer = useDerivedValue(() => R * (0.3 + 0.48 * easeOut(seg(burst.get(), T_DISC))));
  const inner = useDerivedValue(() => outer.get() * easeOut(seg(burst.get(), T_HOLE)));
  const ringRadius = useDerivedValue(() => (outer.get() + inner.get()) / 2);
  const ringWidth = useDerivedValue(() => outer.get() - inner.get());
  const ringOpacity = useDerivedValue(() => {
    const t = burst.get();
    return t > T_DISC[0] && t < T_HOLE[1] ? 0.55 + 0.45 * seg(t, T_DISC_SOLID) : 0;
  });

  const outline = useDerivedValue(() => {
    const t = burst.get();
    const shrink = seg(t, T_OUTLINE_SHRINK);
    return [{ scale: OUTLINE_SCALE * Math.max(1 - shrink * shrink, 0.6 + 0.4 * emptied.get()) * dip.get() }];
  });
  const outlineOpacity = useDerivedValue(() => Math.max(1 - seg(burst.get(), T_OUTLINE_OUT), emptied.get()));
  const filled = useDerivedValue(() => [{ scale: (0.42 + 0.58 * easeOutBack(seg(burst.get(), T_POP))) * dip.get() }]);
  const filledOpacity = useDerivedValue(() => seg(burst.get(), T_FILL_IN) * (1 - emptied.get()));
  // On the solid disc the heart reads as a lighter red.
  const tint = useDerivedValue(() => {
    const t = burst.get();
    return seg(t, T_DISC_SOLID) * (1 - seg(t, T_TINT_OUT));
  });

  const dotX = useDerivedValue(() => {
    const t = burst.get();
    return C + R * (0.9 + 0.42 * easeOut(seg(t, T_DOTS_FLY)) + 0.1 * seg(t, T_DOTS_DRIFT));
  });
  const dotR = useDerivedValue(() => {
    const t = burst.get();
    const out = seg(t, T_DOTS_OUT);
    return 2.7 * easeOut(seg(t, T_DOTS_IN)) * (1 - out * out);
  });

  return (
    <Canvas style={styles.canvas}>
      <Circle cx={C} cy={C + 9} r={R - 4} color="rgba(206,120,150,0.42)">
        <BlurMask blur={9} style="normal" />
      </Circle>
      <Circle cx={C} cy={C} r={face} color="#ffffff" />
      <Circle cx={C} cy={C} r={ringRadius} style="stroke" strokeWidth={ringWidth} color={RED} opacity={ringOpacity} />
      <Group transform={[{ translateX: C }, { translateY: C }]}>
        <Group transform={outline} origin={vec(0, 0)}>
          <Path path={HEART} style="stroke" strokeWidth={2} strokeJoin="round" color={RED} opacity={outlineOpacity} />
        </Group>
        <Group transform={filled} origin={vec(0, 0)}>
          <Path path={HEART} color={RED} opacity={filledOpacity} />
          <Path path={HEART} color="#ff8a7d" opacity={tint} />
        </Group>
      </Group>
      {DOTS.map((dot) => (
        <Group key={dot.angle} origin={vec(C, C)} transform={[{ rotate: (dot.angle * Math.PI) / 180 }]}>
          <Circle cx={dotX} cy={C} r={dotR} color={dot.color} />
        </Group>
      ))}
    </Canvas>
  );
}

/**
 * Round white like button. Liking squashes it, swells a red disc behind the heart that opens into
 * a ring, pops the filled heart out of it and throws off a ring of coloured dots; unliking is a
 * quick dip back to the outline.
 */
export function LikeBurstButton({ value, defaultValue = false, onValueChange, label = 'Like', autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [own, setOwn] = useState(defaultValue);
  const liked = value ?? own;
  const [pressed, setPressed] = useState(false);

  // Resting unliked is burst 0 / fade 0; resting liked is burst TOTAL / fade 0. Unliking only runs
  // `fade` to 1, which looks exactly like burst 0 / fade 0, so the next like can rewind both unseen.
  const burst = useSharedValue(liked ? TOTAL : 0);
  const fade = useSharedValue(0);
  const shown = useRef(liked);

  useEffect(() => {
    if (shown.current === liked) return;
    shown.current = liked;
    // Reanimated drops animations under reduced motion; these opt back in so the heart still
    // changes, as a plain cross-fade without the burst.
    const never = { reduceMotion: ReduceMotion.Never };
    if (reducedMotion) {
      burst.set(TOTAL);
      fade.set(withTiming(liked ? 0 : 1, { duration: 160, ...never }));
    } else if (liked) {
      fade.set(0);
      burst.set(0);
      burst.set(withTiming(TOTAL, { duration: TOTAL * 1000, easing: Easing.linear, ...never }));
    } else {
      fade.set(withTiming(1, { duration: UNLIKE_MS, easing: Easing.linear, ...never }));
    }
  }, [liked, reducedMotion, burst, fade]);

  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed) return;
    const timer = setTimeout(
      () => {
        setOwn(!liked);
        onValueChange?.(!liked);
      },
      liked ? AUTO_LIKED_MS : AUTO_UNLIKED_MS,
    );
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, pressed, liked, onValueChange]);

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityLabel={label}
      accessibilityState={{ checked: liked }}
      onPress={() => {
        setOwn(!liked);
        onValueChange?.(!liked);
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
          <Surface burst={burst} fade={fade} still={reducedMotion} />
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
  canvas: {
    width: SIZE + BLEED * 2,
    height: SIZE + BLEED * 2,
  },
});
