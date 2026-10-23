import { Canvas, Circle, Group, Path, RoundedRect, Skia, rect, rrect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Shape = 'pill' | 'round';
type Tone = 'blue' | 'dark' | 'light';

type Props = {
  label?: string;
  loadingLabel?: string;
  doneLabel?: string;
  /** `pill` shows the label next to the icon, `round` is the icon alone. */
  shape?: Shape;
  tone?: Tone;
  onPress?: () => void;
  /** Uploads by itself, shows the result, resets and starts over. */
  autoPlay?: boolean;
  /** Extra wait before the first automatic run, to stagger a group of buttons. */
  autoPlayDelay?: number;
};

const HEIGHT = 52;
const PILL_WIDTH = 164;
// The icon sits in a circle as tall as the button; the liquid rises inside it.
const R = HEIGHT / 2;
const LABEL_SLIDE = 30;
const AUTO_PAUSE_MS = 1100;
const AUTO_HOLD_MS = 2400;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// One clock in seconds runs the upload (0 → T_DONE) and later the reset (T_DONE → TOTAL).
const T_SWAP_LOADING = [0, 0.35];
const T_FLAT = [0, 0.18];
const T_DROP = [0.05, 0.4];
const T_DASH_OUT = [0.25, 0.45];
const T_RISE = [0.45, 2.15];
const T_SWAP_DONE = [2.35, 2.7];
const T_CHECK = [2.5, 2.85];
const T_DONE = 2.9;
const T_FADE = [2.9, 3.25];
const T_SWAP_IDLE = [2.95, 3.35];
const T_ARROW_BACK = [3.1, 3.45];
const TOTAL = 3.5;

// The liquid's surface bulges in the middle and ripples while it rises.
const DOME = 4.5;
const WAVE = 1.4;
const ARROW_HALF = 6.5;
const HEAD = 5.2;
const TICK = 5.5;

const CHECK = 'M-6.5 0.5L-2 5L7 -4.5';

type Palette = { base: string; well: string; liquid: string; ink: string; doneInk: string };

function palette(shape: Shape, tone: Tone): Palette {
  if (tone === 'light') {
    return { base: '#f2f7ff', well: shape === 'round' ? '#f2f7ff' : '#e4ecfb', liquid: '#3350fc', ink: '#3350fc', doneInk: '#ffffff' };
  }
  if (tone === 'dark') {
    return shape === 'round'
      ? { base: '#1c2030', well: '#1c2030', liquid: '#57cf88', ink: '#ffffff', doneInk: '#ffffff' }
      : { base: '#1b1f2e', well: '#171a27', liquid: '#272c3c', ink: '#ffffff', doneInk: '#ffffff' };
  }
  return shape === 'round'
    ? { base: '#2743e7', well: '#2743e7', liquid: '#4a66ec', ink: '#ffffff', doneInk: '#ffffff' }
    : { base: '#3350fc', well: '#2743e7', liquid: '#4a66ec', ink: '#ffffff', doneInk: '#ffffff' };
}

function seg(t: number, window: number[]) {
  'worklet';
  return Math.min(1, Math.max(0, (t - window[0]) / (window[1] - window[0])));
}
function easeOut(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x) * (1 - x);
}

/** Height of the liquid's surface at `u` (−1…1 across the circle), relative to the circle's centre. */
function surface(u: number, t: number) {
  'worklet';
  const rise = Math.pow(seg(t, T_RISE), 1.7);
  const swell = Math.sin(Math.PI * rise);
  const level = R - rise * (2 * R + DOME + 2);
  return level - DOME * swell * (1 - u * u) + WAVE * swell * Math.sin(u * 2.6 + t * 6);
}

function liquid(cx: number, t: number, still: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  if (still) {
    // Reduced motion: no rising level, the fill simply fades in.
    path.addCircle(cx, R, R);
    return path;
  }
  if (t <= T_RISE[0]) return path;
  path.moveTo(cx - R, HEIGHT);
  for (let i = 0; i <= 12; i++) {
    const u = -1 + i / 6;
    path.lineTo(cx + u * R, R + surface(u, t));
  }
  path.lineTo(cx + R, HEIGHT);
  path.close();
  return path;
}

/** How far the returning arrow still has to rise during the reset. */
function arrowOffset(t: number, still: boolean) {
  'worklet';
  return still ? 0 : 8 * (1 - easeOut(seg(t, T_ARROW_BACK)));
}

/** The arrow's head. It folds flat into a dash at the start of an upload. */
function head(cx: number, t: number, still: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  const resetting = t > T_DONE;
  const fold = resetting || still ? 0 : easeOut(seg(t, T_FLAT));
  const tip = R - ARROW_HALF + 3.5 * fold + (resetting ? arrowOffset(t, still) : 0);
  const wing = HEAD * (1 - fold);
  path.moveTo(cx - HEAD, tip + wing);
  path.lineTo(cx, tip);
  path.lineTo(cx + HEAD, tip + wing);
  return path;
}

/** The arrow's stem. It drops to the bottom of the circle as a short tick and floats up on the liquid. */
function stem(cx: number, t: number, still: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  if (t > T_DONE || still) {
    const dy = t > T_DONE ? arrowOffset(t, still) : 0;
    path.moveTo(cx, R + ARROW_HALF + dy);
    path.lineTo(cx, R - ARROW_HALF + dy);
    return path;
  }
  const drop = seg(t, T_DROP) * seg(t, T_DROP);
  const length = ARROW_HALF * 2 - (ARROW_HALF * 2 - TICK) * drop;
  const floor = Math.min(ARROW_HALF + (R - 1.5 - ARROW_HALF) * drop, surface(0, t));
  path.moveTo(cx, R + floor);
  path.lineTo(cx, R + floor - length);
  return path;
}

function Surface({
  w,
  colors,
  round,
  t,
  still,
}: {
  w: number;
  colors: Palette;
  round: boolean;
  t: SharedValue<number>;
  still: boolean;
}) {
  const cx = w - R;
  const well = useMemo(() => rrect(rect(cx - R, 0, HEIGHT, HEIGHT), R, R), [cx]);

  const fill = useDerivedValue(() => liquid(cx, t.get(), still));
  const fillOpacity = useDerivedValue(() => (still ? seg(t.get(), T_RISE) : 1) * (1 - seg(t.get(), T_FADE)));
  const headPath = useDerivedValue(() => head(cx, t.get(), still));
  const stemPath = useDerivedValue(() => stem(cx, t.get(), still));
  const headOpacity = useDerivedValue(() => {
    const now = t.get();
    return now > T_DONE ? seg(now, T_ARROW_BACK) : 1 - seg(now, T_DASH_OUT);
  });
  const stemOpacity = useDerivedValue(() => {
    const now = t.get();
    if (now > T_DONE) return seg(now, T_ARROW_BACK);
    if (still) return 1 - seg(now, T_DASH_OUT);
    return now >= T_RISE[1] ? 0 : 1;
  });
  const checkEnd = useDerivedValue(() => easeOut(seg(t.get(), T_CHECK)));
  const checkOpacity = useDerivedValue(() => 1 - seg(t.get(), T_FADE));

  return (
    <Canvas style={{ width: w, height: HEIGHT }}>
      <RoundedRect x={0} y={0} width={w} height={HEIGHT} r={R} color={colors.base} />
      {!round && <Circle cx={cx} cy={R} r={R} color={colors.well} />}
      <Group clip={well}>
        <Path path={fill} color={colors.liquid} opacity={fillOpacity} />
        <Path path={stemPath} style="stroke" strokeWidth={2} strokeCap="round" color={colors.ink} opacity={stemOpacity} />
      </Group>
      <Path path={headPath} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={colors.ink} opacity={headOpacity} />
      <Group transform={[{ translateX: cx }, { translateY: R }]}>
        <Path
          path={CHECK}
          style="stroke"
          strokeWidth={2}
          strokeCap="round"
          strokeJoin="round"
          color={colors.doneInk}
          start={0}
          end={checkEnd}
          opacity={checkOpacity}
        />
      </Group>
    </Canvas>
  );
}

type Phase = 'idle' | 'uploading' | 'done' | 'resetting';

/**
 * "Upload" button whose icon circle fills with liquid. A tap folds the arrow into a tick that
 * floats up on the rising, rippling level while the label reads "Uploading"; when the circle is
 * full a check is drawn and the label reads "Uploaded". A second tap (or autoPlay) resets it.
 */
export function LiquidUploadButton({
  label = 'Upload',
  loadingLabel = 'Uploading',
  doneLabel = 'Uploaded',
  shape = 'pill',
  tone = 'blue',
  onPress,
  autoPlay = false,
  autoPlayDelay = 0,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [phase, setPhase] = useState<Phase>('idle');
  const firstRun = useRef(true);
  const t = useSharedValue(0);

  const round = shape === 'round';
  const w = round ? HEIGHT : PILL_WIDTH;
  const colors = useMemo(() => palette(shape, tone), [shape, tone]);

  // The clock: an upload runs it to T_DONE and leaves it there, a reset runs it out and rewinds.
  useEffect(() => {
    if (phase !== 'uploading' && phase !== 'resetting') return;
    // Reanimated drops animations under reduced motion, which would leave the button stuck, so the
    // clock opts back in; the drawing switches to plain fades instead.
    const run = (to: number, seconds: number) =>
      withTiming(to, { duration: seconds * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
    const uploading = phase === 'uploading';
    const seconds = uploading ? T_DONE : TOTAL - T_DONE;
    if (uploading) t.set(0);
    t.set(run(uploading ? T_DONE : TOTAL, seconds));
    const timer = setTimeout(() => {
      // The reset ends looking exactly like the start, so rewinding the clock is invisible.
      if (!uploading) t.set(0);
      setPhase(uploading ? 'done' : 'idle');
    }, seconds * 1000);
    return () => clearTimeout(timer);
  }, [phase, t]);

  // autoPlay starts an upload after a pause and clears the result after a moment; left alone,
  // the result stays up until the next tap.
  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed || (phase !== 'idle' && phase !== 'done')) return;
    const wait = phase === 'done' ? AUTO_HOLD_MS : AUTO_PAUSE_MS + (firstRun.current ? autoPlayDelay : 0);
    const timer = setTimeout(() => {
      firstRun.current = false;
      setPhase(phase === 'done' ? 'resetting' : 'uploading');
    }, wait);
    return () => clearTimeout(timer);
  }, [phase, autoPlay, autoPlayDelay, reducedMotion, pressed]);

  const slide = reducedMotion ? 0 : LABEL_SLIDE;
  // Labels leave through the top and arrive from below; the reset runs the other way.
  const idleStyle = useAnimatedStyle(() => {
    const out = seg(t.get(), T_SWAP_LOADING);
    const back = seg(t.get(), T_SWAP_IDLE);
    return {
      opacity: 1 - out + back,
      transform: [{ translateY: -slide * (back > 0 ? 1 - easeOut(back) : easeOut(out)) }],
    };
  });
  const loadingStyle = useAnimatedStyle(() => {
    const shown = seg(t.get(), T_SWAP_LOADING);
    const out = seg(t.get(), T_SWAP_DONE);
    return {
      opacity: shown - out,
      transform: [{ translateY: slide * (1 - easeOut(shown)) - slide * easeOut(out) }],
    };
  });
  const doneStyle = useAnimatedStyle(() => {
    const shown = seg(t.get(), T_SWAP_DONE);
    const out = seg(t.get(), T_SWAP_IDLE);
    return {
      opacity: shown - out,
      transform: [{ translateY: slide * (1 - easeOut(shown)) + slide * easeOut(out) }],
    };
  });

  const status = phase === 'uploading' ? loadingLabel : phase === 'done' ? doneLabel : label;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={status}
      accessibilityState={{ busy: phase === 'uploading' }}
      onPress={() => {
        if (phase === 'idle') {
          onPress?.();
          setPhase('uploading');
        } else if (phase === 'done') {
          setPhase('resetting');
        }
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
          { width: w, height: HEIGHT },
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Surface w={w} colors={colors} round={round} t={t} still={reducedMotion} />
        </View>
        {!round && (
          <View pointerEvents="none" style={styles.labels}>
            <Animated.Text style={[styles.label, { color: colors.ink }, idleStyle]} numberOfLines={1}>
              {label}
            </Animated.Text>
            <Animated.Text style={[styles.label, { color: colors.ink }, loadingStyle]} numberOfLines={1}>
              {loadingLabel}
            </Animated.Text>
            <Animated.Text style={[styles.label, { color: colors.ink }, doneStyle]} numberOfLines={1}>
              {doneLabel}
            </Animated.Text>
          </View>
        )}
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // The label area is everything left of the icon circle; labels sliding out are clipped by it.
  labels: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PILL_WIDTH - HEIGHT + 8,
    height: HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderTopLeftRadius: R,
    borderBottomLeftRadius: R,
  },
  label: {
    position: 'absolute',
    fontSize: 15,
    fontWeight: '600',
  },
});
