import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

export type ShareTarget = 'message' | 'mail' | 'copy' | 'more';

type Props = {
  label?: string;
  /** Controlled state. Leave undefined to let the button keep its own. */
  expanded?: boolean;
  defaultExpanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  /** One of the four share targets was tapped; the cover slides back afterwards. */
  onShare?: (target: ShareTarget) => void;
  /** Slide open and shut by itself with a pause in each state. Tapping still works. */
  autoPlay?: boolean;
};

const WIDTH = 208;
const HEIGHT = 64;
// Bevelled rim of the indigo tray the white cover sits in.
const BORDER = 2.5;
const INNER_W = WIDTH - BORDER * 2;
const INNER_H = HEIGHT - BORDER * 2;
// How much of the cover stays in view when open: a tab to pull it back by.
const SLIVER = 16;
const TRAVEL = INNER_W - SLIVER;
// The icons glide in from this far to the right as the cover leaves.
const ICON_DRIFT = 40;
const ICON_X = [59, 99, 139, 175];
const TAB_HIT = 38;
const ICON_HIT = 40;
// The canvas overflows the button by this much on every side to fit the glow.
const BLEED = 48;
const AUTO_OPEN_MS = 2400;
const AUTO_CLOSED_MS = 1700;

// Slightly under-damped: the cover overshoots by a few points and settles.
const SPRING = { mass: 1, stiffness: 320, damping: 27 };
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const TARGETS: ShareTarget[] = ['message', 'mail', 'copy', 'more'];
const TARGET_NAMES: Record<ShareTarget, string> = {
  message: 'Message',
  mail: 'Mail',
  copy: 'Copy link',
  more: 'More',
};

// Generic line icons on a 20pt box centred on the origin.
const ICONS: Record<ShareTarget, string> = {
  message: 'M-6 -8L6 -8Q9 -8 9 -5L9 2Q9 5 6 5L-0.5 5L-5 9L-5 5L-6 5Q-9 5 -9 2L-9 -5Q-9 -8 -6 -8Z',
  mail:
    'M-6.5 -6.5L6.5 -6.5Q9 -6.5 9 -4L9 4Q9 6.5 6.5 6.5L-6.5 6.5Q-9 6.5 -9 4L-9 -4Q-9 -6.5 -6.5 -6.5Z' +
    'M-8 -4.8L0 1.2L8 -4.8',
  copy:
    'M-0.5 -3L6 -3Q8.5 -3 8.5 -0.5L8.5 6Q8.5 8.5 6 8.5L-0.5 8.5Q-3 8.5 -3 6L-3 -0.5Q-3 -3 -0.5 -3Z' +
    'M-6 3Q-8.5 3 -8.5 0.5L-8.5 -6Q-8.5 -8.5 -6 -8.5L0.5 -8.5Q3 -8.5 3 -6',
  more: 'M-7 0.5L-7 6Q-7 8.5 -4.5 8.5L4.5 8.5Q7 8.5 7 6L7 0.5M0 3L0 -8.5M-4 -4.5L0 -8.5L4 -4.5',
};

function Surface({ p, still }: { p: SharedValue<number>; still: boolean }) {
  const x = BLEED;
  const y = BLEED;
  const ix = x + BORDER;
  const iy = y + BORDER;
  const cy = y + HEIGHT / 2;
  const clip = useMemo(() => rrect(rect(ix, iy, INNER_W, INNER_H), INNER_H / 2, INNER_H / 2), [ix, iy]);

  const glow = useDerivedValue(() => Math.min(1, Math.max(0, p.get())));
  const icons = useDerivedValue(() => [{ translateX: still ? 0 : ICON_DRIFT * (1 - p.get()) }]);
  const cover = useDerivedValue(() => [{ translateX: still ? 0 : -TRAVEL * p.get() }]);
  const coverOpacity = useDerivedValue(() => (still ? Math.min(1, Math.max(0, 1 - p.get())) : 1));

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <Group opacity={glow}>
        <RoundedRect x={x + 6} y={y + 14} width={WIDTH - 12} height={HEIGHT} r={HEIGHT / 2} color="rgba(74,62,240,0.6)">
          <BlurMask blur={16} style="normal" />
        </RoundedRect>
      </Group>

      {/* Tray: a rim lit from above, then the indigo floor. */}
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={HEIGHT / 2}>
        <LinearGradient start={vec(0, y)} end={vec(0, y + HEIGHT)} colors={['#9499f3', '#5a56dc', '#2a2694']} positions={[0, 0.5, 1]} />
      </RoundedRect>
      <RoundedRect x={ix} y={iy} width={INNER_W} height={INNER_H} r={INNER_H / 2}>
        <LinearGradient start={vec(ix, iy)} end={vec(ix + INNER_W, iy + INNER_H)} colors={['#4b46de', '#352fb6']} />
      </RoundedRect>

      <Group clip={clip}>
        <Group transform={icons}>
          {TARGETS.map((target, i) => (
            <Group key={target} transform={[{ translateX: x + ICON_X[i] }, { translateY: cy }]}>
              <Path
                path={ICONS[target]}
                style="stroke"
                strokeWidth={1.7}
                strokeCap="round"
                strokeJoin="round"
                color="#ffffff"
              />
            </Group>
          ))}
        </Group>

        <Group transform={cover} opacity={coverOpacity}>
          <RoundedRect x={ix + 3} y={iy} width={INNER_W} height={INNER_H} r={INNER_H / 2} color="rgba(16,12,84,0.45)">
            <BlurMask blur={5} style="normal" />
          </RoundedRect>
          <RoundedRect x={ix} y={iy} width={INNER_W} height={INNER_H} r={INNER_H / 2}>
            <LinearGradient start={vec(0, iy)} end={vec(0, iy + INNER_H)} colors={['#fbfbfa', '#f1f1ee']} />
          </RoundedRect>
        </Group>
      </Group>
    </Canvas>
  );
}

export function ShareRevealButton({
  label = 'SHARE',
  expanded,
  defaultExpanded = false,
  onExpandedChange,
  onShare,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [inner, setInner] = useState(defaultExpanded);
  const [pressed, setPressed] = useState(false);
  const open = expanded ?? inner;

  // 0 = covered, 1 = open. A spring, so it runs a little past either end before settling.
  const p = useSharedValue(open ? 1 : 0);

  useEffect(() => {
    // Reanimated skips animations outright under reduced motion; the short fade opts back in.
    p.set(
      reducedMotion
        ? withTiming(open ? 1 : 0, { duration: 200, reduceMotion: ReduceMotion.Never })
        : withSpring(open ? 1 : 0, SPRING),
    );
  }, [open, reducedMotion, p]);

  // Re-armed after every change, so a tap simply restarts the pause.
  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed) return;
    const timer = setTimeout(() => {
      if (expanded === undefined) setInner(!open);
      onExpandedChange?.(!open);
    }, open ? AUTO_OPEN_MS : AUTO_CLOSED_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, pressed, open, expanded, onExpandedChange]);

  const setOpen = (next: boolean) => {
    // The touch target that was just tapped is about to be swapped out.
    setPressed(false);
    if (expanded === undefined) setInner(next);
    onExpandedChange?.(next);
  };

  const press = {
    onPressIn: () => {
      setPressed(true);
      if (process.env.EXPO_OS !== 'web') {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }
    },
    onPressOut: () => setPressed(false),
    pressRetentionOffset: 16,
  };

  const coverStyle = useAnimatedStyle(() =>
    reducedMotion
      ? { opacity: Math.min(1, Math.max(0, 1 - p.get())) }
      : { transform: [{ translateX: -TRAVEL * p.get() }] },
  );

  return (
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
        <Surface p={p} still={reducedMotion} />
      </View>
      {/* The label rides on the cover, clipped by the tray like the cover itself. */}
      <View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={styles.tray}
      >
        <Animated.View style={[styles.cover, coverStyle]}>
          <Text style={styles.label} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
      </View>

      {open ? (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Close"
            onPress={() => setOpen(false)}
            style={[styles.hit, { left: 0, width: TAB_HIT }]}
            {...press}
          />
          {TARGETS.map((target, i) => (
            <Pressable
              key={target}
              accessibilityRole="button"
              accessibilityLabel={TARGET_NAMES[target]}
              onPress={() => {
                onShare?.(target);
                setOpen(false);
              }}
              style={[styles.hit, { left: ICON_X[i] - ICON_HIT / 2, width: ICON_HIT }]}
              {...press}
            />
          ))}
        </>
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={label}
          accessibilityState={{ expanded: false }}
          onPress={() => setOpen(true)}
          style={[styles.hit, { left: 0, width: WIDTH }]}
          {...press}
        />
      )}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  tray: {
    position: 'absolute',
    left: BORDER,
    top: BORDER,
    width: INNER_W,
    height: INNER_H,
    borderRadius: INNER_H / 2,
    overflow: 'hidden',
  },
  cover: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hit: {
    position: 'absolute',
    top: 0,
    height: HEIGHT,
  },
  label: {
    color: '#3d37c6',
    fontSize: 17,
    fontWeight: '800',
    letterSpacing: 0.6,
  },
});
