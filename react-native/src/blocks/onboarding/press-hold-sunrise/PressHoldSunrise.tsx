import { Canvas, Circle, Group, LinearGradient, Path, Rect, Shadow, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  ACTION_AFTER,
  ACTION_GAP,
  ACTION_IN,
  ACTION_RISE,
  AT_B,
  BACK_AT,
  BLOB,
  BOLT,
  BUTTON_RISE,
  CALM_FADE,
  DISC,
  DISC_AFTER,
  DISC_IN,
  END,
  HEADING_AFTER,
  HEADING_IN,
  HEADING_SLIDE,
  HOLD,
  LOOP,
  MARK_IN,
  PRESS_AT,
  REACH,
  RELEASE,
  ROUND,
  SCALLOP,
  SQUARE,
  SQUIRCLE,
  USER_HOLD,
  arrived,
  buttonRadius,
  buttonRise,
  discScale,
  dropped,
  dusk,
  grown,
  labelAlpha,
  night,
  pageAlpha,
  pagerDrawn,
  releaseEase,
  shrunk,
  smooth,
  textAlpha,
} from './timeline';

type Props = {
  /** The app's name under its mark. */
  name?: string;
  tagline?: string;
  /** Under the bolt, inside the button once it has grown. */
  buttonLabel?: string;
  /** The line at the bottom of the welcome. */
  hint?: string;
  /** The first page's heading. */
  heading?: string;
  primaryLabel?: string;
  secondaryLabel?: string;
  /** The button and the orange shapes, top to bottom. */
  accent?: string[];
  /** Number of pages the pager's arc stands for; the first is the one shown. */
  pages?: number;
  /** Holds the button by itself every LOOP seconds, and comes back to the welcome. */
  autoPlay?: boolean;
  /** Called when the hold completes and the tunnel starts. */
  onDone?: () => void;
  onPrimary?: () => void;
  onSecondary?: () => void;
  /** Called when the mark on the first page is tapped and the welcome returns. */
  onBack?: () => void;
};

const CREAM = '#FBF8F1';
const INK = '#251C21';
const BODY = '#2B2B2B';
const DOT = '#EFE9DC';
const DISC_FILL = '#FFFDFF';
const ACCENT = ['#FBD28A', '#F5A742'];
const NIGHT = '#221F23';
// The tunnel's dark shapes are a little warmer than the page they end in.
const SHADE = '#2B2529';
const NIGHT_DOT = '#2C292D';
const NIGHT_INK = '#F4EFE6';
const NIGHT_DISC = '#343134';
const BALL = ['#FECA78', '#F9A53A'];
const TRACK = '#3D3A3D';
const TRACK_ON = '#F4D58A';
const SECONDARY = '#3B383B';

// Where things are, in points from the top safe area, the screen's bottom or a disc's centre.
const MARK_Y = 60;
const NAME_Y = 128;
const TAGLINE_Y = 178;
const HEADING_Y = 111;
/** The welcome's disc and the first page's, as fractions of the screen's height. */
const DISC_AT = 0.61;
const NIGHT_DISC_AT = 0.506;
const NIGHT_DISC_SIZE = 219;
const LABEL_BELOW = 39;
const HINT_ABOVE = 30;
const PAGER_BELOW = 159;
const PAGER_WIDTH = 110;
const PAGER_SAG = 6;
const ACTION = 44;
const ACTION_STEP = 53;
const ACTION_MARGIN = 24;
/** The finger does not have to hit the small button: this much round the disc's centre takes the hold. */
const HOLD_TARGET = 132;
// The halftone field: 2 pt dots on a 10 pt grid, from above the disc to below it.
const GRID = 10;
const FIELD_ABOVE = 230;
const FIELD_BELOW = 130;
const FIELD_FADE = 70;
/** The three balls: x and y from the disc's centre, and the diameter. The last is in front. */
const BALLS = [
  [-19, -54, 83],
  [56, -33, 70],
  [0, 44, 116],
];
/** Turn of each tunnel shape when it is born, in degrees. */
const TURNS = [45, 0, 10, 0, 0];

const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

/**
 * The finger goes down on the button: the scene runs to the first page unless it is let go in
 * time. `late` is how long ago the press was due, so that a timer that fires late does not shift it.
 */
function pressIn(scene: SharedValue<number>, calm: boolean, late: number) {
  'worklet';
  const s = scene.get();
  if (s >= HOLD) return;
  if (!calm) return scene.set(withTiming(AT_B, { duration: (AT_B - s - late) * 1000, ...LINEAR }));
  scene.set(
    withTiming(HOLD + CALM_FADE, { duration: (HOLD + CALM_FADE - s - late) * 1000, ...LINEAR }, (finished) => {
      if (finished) scene.set(AT_B);
    }),
  );
}

/** Let go before the hold was complete: the button springs back. */
function letGo(scene: SharedValue<number>) {
  'worklet';
  const s = scene.get();
  if (s <= 0 || s >= HOLD) return;
  scene.set(withTiming(0, { duration: RELEASE * 1000, easing: releaseEase, reduceMotion: ReduceMotion.Never }));
}

function goBack(scene: SharedValue<number>, calm: boolean, late: number) {
  'worklet';
  if (scene.get() !== AT_B) return;
  const end = calm ? AT_B + CALM_FADE : END;
  scene.set(
    withTiming(end, { duration: (end - AT_B - late) * 1000, ...LINEAR }, (finished) => {
      // The end of the way back is the welcome at rest.
      if (finished) scene.set(0);
    }),
  );
}

function halftone(width: number, top: number, bottom: number) {
  const builder = Skia.PathBuilder.Make();
  for (let y = top + GRID / 2; y < bottom; y += GRID) {
    for (let x = (width / 2) % GRID; x < width; x += GRID) {
      // The dots get smaller towards the field's edges, which reads as fading out.
      const size = smooth(Math.min(x, width - x), 0, FIELD_FADE) * smooth(Math.min(y - top, bottom - y), 0, FIELD_FADE);
      if (size > 0.08) builder.addCircle(x, y, size);
    }
  }
  return builder.build();
}

type Place = { scene: SharedValue<number>; cx: number; cy: number; reach: number; calm: boolean };

/** One shape of the tunnel, or of the way back when `back` is set. */
function Shape({ scene, cx, cy, reach, calm, index, back, path, color, gradient, turn = 0 }: Place & {
  index: number;
  back?: boolean;
  path: SkPath;
  color?: string;
  gradient?: string[];
  turn?: number;
}) {
  const transform = useDerivedValue(() => {
    const now = back ? shrunk(scene.get(), index, reach) : grown(scene.get(), index, reach);
    return [{ translateX: cx }, { translateY: cy }, { rotate: ((turn + now.turn) * Math.PI) / 180 }, { scale: now.radius }];
  });
  const opacity = useDerivedValue(() => {
    if (calm) return 0;
    return (back ? shrunk(scene.get(), index, reach) : grown(scene.get(), index, reach)).alpha;
  });
  return (
    <Group transform={transform}>
      <Path path={path} color={color} opacity={opacity}>
        {gradient && <LinearGradient start={vec(0, -1)} end={vec(0, 1)} colors={gradient} />}
      </Path>
    </Group>
  );
}

function Ball({ scene, calm, index, x, y, opacity }: { scene: SharedValue<number>; calm: boolean; index: number; x: number; y: number; opacity: SharedValue<number> }) {
  const radius = BALLS[index][2] / 2;
  const transform = useDerivedValue(() => [{ translateY: -dropped(scene.get(), index, calm).lift }]);
  const alpha = useDerivedValue(() => opacity.get() * dropped(scene.get(), index, calm).alpha);
  return (
    <Group transform={transform}>
      <Circle cx={x} cy={y} r={radius} opacity={alpha}>
        <LinearGradient start={vec(x, y - radius)} end={vec(x, y + radius)} colors={BALL} />
      </Circle>
    </Group>
  );
}

function Mark({ fill, dot }: { fill: string; dot: string }) {
  return (
    <View style={[styles.mark, { backgroundColor: fill }]}>
      <View style={[styles.markDot, { backgroundColor: dot }]} />
    </View>
  );
}

function Action({ scene, calm, index, top, label, fill, ink, onPress }: {
  scene: SharedValue<number>;
  calm: boolean;
  index: number;
  top: number;
  label: string;
  fill: string;
  ink: string;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const s = scene.get();
    const shown = arrived(s, ACTION_AFTER + index * ACTION_GAP, ACTION_IN, calm);
    return { opacity: pageAlpha(s, calm) * shown, transform: [{ translateY: ACTION_RISE * (1 - shown) }] };
  });
  return (
    <Animated.View style={[styles.actionSlot, { top }, style]}>
      <Pressable onPress={onPress} accessibilityRole="button">
        {({ pressed }) => (
          <Animated.View
            style={[styles.action, { backgroundColor: fill, opacity: pressed ? 0.75 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}
          >
            <Text style={[styles.actionLabel, { color: ink }]} numberOfLines={1}>
              {label}
            </Text>
          </Animated.View>
        )}
      </Pressable>
    </Animated.View>
  );
}

/**
 * A welcome screen whose one button has to be held: it grows until it fills its disc, then
 * bursts over the screen and a tunnel of shapes, dark and orange in turn, carries the app from
 * the light welcome into its dark first page. Tapping the mark there goes back the same way.
 */
export function PressHoldSunrise({
  name = 'Motes.',
  tagline = 'Tiny changes,\nremarkable results.',
  buttonLabel = 'Get Started',
  hint = 'PRESS AND HOLD TO GET STARTED',
  heading = 'Create a few impactful habits\nand adjust them over time.',
  primaryLabel = 'Create first habit',
  secondaryLabel = 'Continue with an account',
  accent = ACCENT,
  pages = 5,
  autoPlay = false,
  onDone,
  onPrimary,
  onSecondary,
  onBack,
}: Props) {
  const calm = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  // Where the scene is: 0 on the welcome at rest, 1 on the first page at rest, 2 on the way.
  const [place, setPlace] = useState(0);
  const [dark, setDark] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The hold in progress: whose it is, for the haptic at its end and for a touch that interrupts.
  const hold = useRef<'hand' | 'auto' | null>(null);

  const scene = useSharedValue(0);

  const cx = width / 2;
  const cy = Math.round(height * DISC_AT);
  const nightY = Math.round(height * NIGHT_DISC_AT);
  // The tunnel's shapes grow until they reach the corner furthest from the disc.
  const reach = Math.hypot(cx, Math.max(cy, height - cy)) * REACH;
  const bottom = Math.max(insets.bottom + 6, 20);
  const where = { scene, cx, cy, reach, calm };

  const dots = useMemo(() => halftone(width, cy - FIELD_ABOVE, cy + FIELD_BELOW), [width, cy]);
  const pager = useMemo(() => {
    const y = nightY + PAGER_BELOW;
    return Skia.PathBuilder.Make()
      .moveTo(cx - PAGER_WIDTH / 2, y)
      .quadTo(cx, y + PAGER_SAG * 2, cx + PAGER_WIDTH / 2, y)
      .build();
  }, [cx, nightY]);

  const completed = () => {
    if (hold.current === 'hand' && process.env.EXPO_OS !== 'web') {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    }
    hold.current = null;
    onDone?.();
  };

  useAnimatedReaction(
    () => scene.get() >= HOLD,
    (now, old) => {
      if (now && old === false) scheduleOnRN(completed);
    },
  );
  useAnimatedReaction(
    () => (scene.get() === 0 ? 0 : scene.get() === AT_B ? 1 : 2),
    (now, old) => {
      if (now !== old) scheduleOnRN(setPlace, now);
    },
  );
  useAnimatedReaction(
    () => night(scene.get(), calm),
    (now, old) => {
      if (now !== old) scheduleOnRN(setDark, now);
    },
  );

  useEffect(() => () => cancelAnimation(scene), [scene]);

  const press = (by: 'hand' | 'auto', late = 0) => {
    hold.current = by;
    scheduleOnUI(pressIn, scene, calm, late);
  };
  const release = () => {
    hold.current = null;
    scheduleOnUI(letGo, scene);
  };
  const back = (late = 0) => scheduleOnUI(goBack, scene, calm, late);

  /** A touch takes over from the autoPlay loop, which starts again from the welcome a while later. */
  const touched = () => {
    if (hold.current === 'auto') release();
    setTouches((n) => n + 1);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ press, back, place });
  useEffect(() => {
    latest.current = { press, back, place };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // Every step is due at a fixed time after the loop began, and is told how late it runs.
    const cycle = (origin: number) => {
      const at = (seconds: number, run: (late: number) => void) => {
        const due = origin + seconds * 1000;
        after(due - Date.now(), () => run(Math.max(0, Date.now() - due) / 1000));
      };
      at(PRESS_AT, (late) => latest.current.press('auto', late));
      at(BACK_AT, (late) => latest.current.back(late));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch: USER_HOLD seconds of rest, counted from the end of what the touch set off,
    // then back to the welcome if need be, and the loop starts over from there.
    const rest = (quiet: number, returning: boolean) => {
      const { place, back: leave } = latest.current;
      if (returning) return place === 0 ? cycle(Date.now()) : after(100, () => rest(0, true));
      if (place === 2) return after(100, () => rest(0, false));
      if (quiet < USER_HOLD * 1000) return after(100, () => rest(quiet + 100, false));
      if (place === 0) return cycle(Date.now());
      leave();
      after(100, () => rest(0, true));
    };
    if (touches === 0) cycle(Date.now());
    else after(100, () => rest(100, false));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const discTransform = useDerivedValue(() => [{ scale: discScale(scene.get()) }]);
  const buttonTransform = useDerivedValue(() => {
    const s = scene.get();
    return [{ translateX: cx }, { translateY: cy - buttonRise(s) }, { scale: buttonRadius(s, reach, calm) }];
  });
  const duskOpacity = useDerivedValue(() => (calm ? dusk(scene.get()) : 0));
  const boltOpacity = useDerivedValue(() => labelAlpha(scene.get(), calm));
  const nightOpacity = useDerivedValue(() => pageAlpha(scene.get(), calm) * arrived(scene.get(), DISC_AFTER, DISC_IN, calm));
  const nightDiscTransform = useDerivedValue(() => [{ scale: 0.8 + 0.2 * arrived(scene.get(), DISC_AFTER, DISC_IN, calm) }]);
  const pageOpacity = useDerivedValue(() => pageAlpha(scene.get(), calm));
  const pagerOpacity = useDerivedValue(() => (pagerDrawn(scene.get(), calm) > 0 ? pageAlpha(scene.get(), calm) : 0));
  const pagerEnd = useDerivedValue(() => pagerDrawn(scene.get(), calm));
  const pagerOnEnd = useDerivedValue(() => Math.min(pagerDrawn(scene.get(), calm), 1 / Math.max(1, pages)));

  const textStyle = useAnimatedStyle(() => ({ opacity: textAlpha(scene.get(), calm) }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: labelAlpha(scene.get(), calm) }));
  const nightMarkStyle = useAnimatedStyle(() => ({ opacity: pageAlpha(scene.get(), calm) * arrived(scene.get(), 0, MARK_IN, calm) }));
  const headingStyle = useAnimatedStyle(() => {
    const shown = arrived(scene.get(), HEADING_AFTER, HEADING_IN, calm);
    return { opacity: pageAlpha(scene.get(), calm) * shown, transform: [{ translateX: HEADING_SLIDE * (1 - shown) }] };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const tap = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style={dark ? 'light' : 'dark'} />

      <View pointerEvents="none" style={styles.fill}>
        <Canvas style={styles.fill}>
          <Path path={dots} color={DOT} />
          <Group transform={discTransform} origin={vec(cx, cy)}>
            <Circle cx={cx} cy={cy} r={DISC / 2} color={DISC_FILL}>
              <Shadow dx={0} dy={8} blur={15} color="rgba(0,0,0,0.06)" />
            </Circle>
          </Group>
        </Canvas>
      </View>

      <Animated.View pointerEvents="none" style={[styles.fill, textStyle]}>
        <View style={[styles.markSlot, { top: insets.top + MARK_Y - 15 }]}>
          <Mark fill={INK} dot={CREAM} />
        </View>
        <Text style={[styles.name, { top: insets.top + NAME_Y - 17 }]} numberOfLines={1}>
          {name}
        </Text>
        <Text style={[styles.tagline, { top: insets.top + TAGLINE_Y - 9.5 }]}>{tagline}</Text>
        <Text style={[styles.hint, { top: height - Math.max(insets.bottom, 12) - HINT_ABOVE - 7 }]} numberOfLines={1}>
          {hint}
        </Text>
      </Animated.View>

      <View pointerEvents="none" style={styles.fill}>
        <Canvas style={styles.fill}>
          <Group transform={buttonTransform}>
            <Circle cx={0} cy={0} r={1}>
              <LinearGradient start={vec(0, -1)} end={vec(0, 1)} colors={accent} />
            </Circle>
          </Group>

          <Shape {...where} index={0} path={SQUARE} color={SHADE} turn={TURNS[0]} />
          <Shape {...where} index={1} path={SCALLOP} gradient={accent} turn={TURNS[1]} />
          <Shape {...where} index={2} path={SQUIRCLE} color={SHADE} turn={TURNS[2]} />
          <Shape {...where} index={3} path={BLOB} gradient={accent} turn={TURNS[3]} />
          <Shape {...where} index={4} path={ROUND} color={NIGHT} turn={TURNS[4]} />

          {/* The way back, the last to shrink first: it lies under the others. */}
          <Shape {...where} back index={2} path={SQUARE} color={SHADE} turn={TURNS[0]} />
          <Shape {...where} back index={1} path={SCALLOP} gradient={accent} turn={TURNS[1]} />
          <Shape {...where} back index={0} path={ROUND} color={NIGHT} />

          <Rect x={0} y={0} width={width} height={height} color={NIGHT} opacity={duskOpacity} />

          <Group transform={[{ translateX: cx - 8 }, { translateY: cy - BUTTON_RISE - 8 }]}>
            <Path path={BOLT} color="#000000" opacity={boltOpacity} />
          </Group>

          <Path path={dots} color={NIGHT_DOT} opacity={nightOpacity} />
          <Group transform={nightDiscTransform} origin={vec(cx, nightY)}>
            <Circle cx={cx} cy={nightY} r={NIGHT_DISC_SIZE / 2} color={NIGHT_DISC} opacity={nightOpacity} />
          </Group>
          {BALLS.map(([x, y], index) => (
            <Ball key={index} scene={scene} calm={calm} index={index} x={cx + x} y={nightY + y} opacity={pageOpacity} />
          ))}
          <Path path={pager} style="stroke" strokeWidth={2} strokeCap="round" color={TRACK} opacity={pagerOpacity} end={pagerEnd} />
          <Path path={pager} style="stroke" strokeWidth={2} strokeCap="round" color={TRACK_ON} opacity={pagerOpacity} end={pagerOnEnd} />
        </Canvas>
      </View>

      <Animated.Text pointerEvents="none" style={[styles.buttonLabel, { top: cy + LABEL_BELOW - 10 }, labelStyle]} numberOfLines={1}>
        {buttonLabel}
      </Animated.Text>

      <Pressable
        disabled={place === 1}
        style={[styles.holdTarget, { left: cx - HOLD_TARGET / 2, top: cy - HOLD_TARGET / 2 }]}
        accessibilityRole="button"
        accessibilityLabel={`${buttonLabel}. ${hint}`}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={() => press('hand')}
        onPressIn={() => {
          tap();
          press('hand');
        }}
        onPressOut={release}
      />

      <View pointerEvents={place === 1 ? 'auto' : 'none'} accessibilityElementsHidden={place !== 1} style={styles.fill}>
        <Animated.View style={[styles.markSlot, { top: insets.top + MARK_Y - 15 }, nightMarkStyle]}>
          <Pressable
            hitSlop={14}
            accessibilityRole="button"
            accessibilityLabel="Back to the welcome"
            onPress={() => {
              tap();
              back();
              onBack?.();
            }}
          >
            <Mark fill="#FFFFFF" dot={NIGHT} />
          </Pressable>
        </Animated.View>
        <Animated.Text style={[styles.heading, { top: insets.top + HEADING_Y - 13.5 }, headingStyle]}>{heading}</Animated.Text>
        <Action
          scene={scene}
          calm={calm}
          index={0}
          top={height - bottom - ACTION - ACTION_STEP}
          label={primaryLabel}
          fill="#FFFFFF"
          ink={NIGHT}
          onPress={() => {
            tap();
            onPrimary?.();
          }}
        />
        <Action
          scene={scene}
          calm={calm}
          index={1}
          top={height - bottom - ACTION}
          label={secondaryLabel}
          fill={SECONDARY}
          ink="#FFFFFF"
          onPress={() => {
            tap();
            onSecondary?.();
          }}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: CREAM,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  markSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  mark: {
    width: 26,
    height: 30,
    borderRadius: 9,
    borderCurve: 'continuous',
  },
  markDot: {
    position: 'absolute',
    left: 9,
    top: 15,
    width: 9,
    height: 9,
    borderRadius: 4.5,
  },
  name: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontFamily: SERIF,
    fontSize: 28,
    lineHeight: 34,
    includeFontPadding: false,
  },
  tagline: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: BODY,
    fontSize: 15,
    lineHeight: 19,
    includeFontPadding: false,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontSize: 10,
    lineHeight: 14,
    fontWeight: '500',
    letterSpacing: 0.6,
    includeFontPadding: false,
  },
  buttonLabel: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontFamily: SERIF,
    fontSize: 16,
    lineHeight: 20,
    includeFontPadding: false,
  },
  holdTarget: {
    position: 'absolute',
    width: HOLD_TARGET,
    height: HOLD_TARGET,
    borderRadius: HOLD_TARGET / 2,
  },
  heading: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: NIGHT_INK,
    fontFamily: SERIF,
    fontSize: 22,
    lineHeight: 27,
    includeFontPadding: false,
  },
  actionSlot: {
    position: 'absolute',
    left: ACTION_MARGIN,
    right: ACTION_MARGIN,
    height: ACTION,
  },
  action: {
    height: ACTION,
    borderRadius: ACTION / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
