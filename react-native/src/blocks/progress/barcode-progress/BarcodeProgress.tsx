import { BlurMask, Canvas, Circle, Group, Path, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled progress, 0…1. Leave undefined to show the reference's 66% (or the autoPlay loop). */
  progress?: number;
  /** Scans the bars from 0 to 66% by itself, holds, rewinds and starts over. */
  autoPlay?: boolean;
  title?: string;
  caption?: string;
  /** Change against the last period, in percent, shown in the chip. */
  change?: number;
  comparison?: string;
  onMorePress?: () => void;
};

const WIDTH = 344;
const HEIGHT = 175;
const RADIUS = 7;
// The canvas overflows the card by this much on every side to fit the scanner's glow.
const BLEED = 16;

const BARS = 33;
const ROW_X = 11;
const ROW_Y = 115;
const ROW_W = 322;
const ROW_H = 49.5;
const GAP = 1.3;
const PITCH = (ROW_W + GAP) / BARS;
const BAR_W = PITCH - GAP;
const ROW_RADIUS = 2.2;

const FIGURE_Y = 84.7;
const CHIP_X = 66.8;
const CHIP_W = 40;
const CHIP_H = 17;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.66;
const START = 0.4;
const FILL = 1.9;
const HOLD = 4.2;
const RESET = 0.7;
const LOOP = START + FILL + HOLD + RESET;
const LANDED = START + FILL;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = LANDED + 1.2;
// The ripple that keeps running through the bars: one pass every RIPPLE_PERIOD seconds.
const RIPPLE_PERIOD = 3.6;
const RIPPLE_PASS = 1.15;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Progress at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < LANDED) return REST * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how fast the scanner is travelling at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  const u = (t - START) / FILL;
  return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
}

/** 0…1: how lit bar `index` is. A bar switches on as the scanner crosses its middle third. */
function barLit(value: number, index: number) {
  'worklet';
  return clamp01((value * BARS - index - 0.5) * 3 + 0.5);
}

/**
 * Height of bar `index`, 0…1 of the row. The bars around the moving scanner duck, so a notch
 * travels with it, and a shallow ripple keeps running through the row while it rests.
 */
function barHeight(value: number, index: number, activity: number, time: number, ambient: number) {
  'worklet';
  const near = clamp01(1 - Math.abs(value * BARS - index - 0.5) / 2.6);
  const notch = 0.42 * activity * near * near * (3 - 2 * near);
  const phase = (time % RIPPLE_PERIOD) / RIPPLE_PASS;
  const at = -4 + (BARS + 8) * phase;
  const wave = phase < 1 ? Math.exp(-Math.pow((index - at) / 2.1, 2)) : 0;
  return 1 - notch - 0.2 * ambient * (1 - activity) * wave;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** Scale of the change chip. */
  chip: SharedValue<number>;
  /** 0 with reduced motion: no ripple. */
  ambient: number;
};

function Surface({ value, time, activity, chip, ambient }: Motion) {
  // All bars at their current heights; `from`/`to` pick the dim row, the lit ones or the one switching on.
  const bars = (from: number, to: number) => {
    'worklet';
    let d = '';
    for (let i = from; i < to; i++) {
      const h = ROW_H * barHeight(value.get(), i, activity.get(), time.get(), ambient);
      const x = ROW_X + i * PITCH;
      d += `M${x.toFixed(2)} ${(ROW_Y + (ROW_H - h) / 2).toFixed(2)}h${BAR_W.toFixed(2)}v${h.toFixed(2)}h${(-BAR_W).toFixed(2)}Z`;
    }
    return d || 'M0 0';
  };
  // Index of the first bar that is not fully lit.
  const edge = useDerivedValue(() => {
    let i = 0;
    while (i < BARS && barLit(value.get(), i) >= 1) i++;
    return i;
  });
  const dim = useDerivedValue(() => bars(0, BARS));
  const lit = useDerivedValue(() => bars(0, edge.get()));
  const turning = useDerivedValue(() => bars(edge.get(), Math.min(BARS, edge.get() + 1)));
  const turningAlpha = useDerivedValue(() => (edge.get() < BARS ? barLit(value.get(), edge.get()) : 0));

  // The scanner: a bright line riding the head of the fill, with a soft bloom, only while it moves.
  const beam = useDerivedValue(() => [{ translateX: ROW_X + clamp01(value.get()) * (ROW_W + GAP) - GAP / 2 }]);
  const chipTransform = useDerivedValue(() => [{ scale: chip.get() }]);

  const chipCx = CHIP_X + CHIP_W / 2;

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT} r={RADIUS} color="#0b0b0b" />
        <RoundedRect x={0.5} y={0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1} color="#1d1d1d" />

        {/* "More" dots and the divider under the caption. */}
        {[0, 1, 2].map((i) => (
          <Circle key={i} cx={323.3 + i * 2.5} cy={18.4} r={0.75} color="#f2f2f2" />
        ))}
        <Rect x={ROW_X} y={59} width={ROW_W} height={0.5} color="#272727" />

        {/* Change chip: outline and arrow; the label is real text scaled with it. */}
        <Group transform={chipTransform} origin={vec(chipCx, FIGURE_Y)}>
          <RoundedRect
            x={CHIP_X + 0.4}
            y={FIGURE_Y - CHIP_H / 2 + 0.4}
            width={CHIP_W - 0.8}
            height={CHIP_H - 0.8}
            r={CHIP_H / 2}
            style="stroke"
            strokeWidth={0.8}
            color="#ededed"
          />
          <Path
            path={`M${CHIP_X + 8.2} ${FIGURE_Y + 2.4}L${CHIP_X + 13} ${FIGURE_Y - 2.4}M${CHIP_X + 9.1} ${FIGURE_Y - 2.5}H${CHIP_X + 13.1}V${FIGURE_Y + 1.5}`}
            style="stroke"
            strokeWidth={0.8}
            strokeCap="round"
            strokeJoin="round"
            color="#ededed"
          />
        </Group>

        <Group clip={{ rect: { x: ROW_X, y: ROW_Y, width: ROW_W, height: ROW_H }, rx: ROW_RADIUS, ry: ROW_RADIUS }}>
          <Path path={dim} color="#474747" />
          <Path path={lit} color="#fafafa" />
          <Path path={turning} color="#fafafa" opacity={turningAlpha} />
        </Group>

        <Group transform={beam} opacity={activity}>
          <Rect x={-9} y={ROW_Y - 2} width={18} height={ROW_H + 4} color="rgba(255,255,255,0.4)">
            <BlurMask blur={7} style="normal" />
          </Rect>
          <RoundedRect x={-0.9} y={ROW_Y - 6} width={1.8} height={ROW_H + 12} r={0.9} color="#ffffff">
            <BlurMask blur={0.6} style="normal" />
          </RoundedRect>
        </Group>
      </Group>
    </Canvas>
  );
}

function MoreButton({ onPress }: { onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="More"
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      hitSlop={10}
      style={styles.more}
    >
      {/* The dots themselves are drawn on the canvas; this dims them while pressed. */}
      <Animated.View
        style={[
          styles.moreVeil,
          { opacity: pressed ? 0.6 : 0, transitionProperty: 'opacity', transitionDuration: 130, transitionTimingFunction: CSS_EASE_OUT },
        ]}
      />
    </Pressable>
  );
}

/**
 * Black "Progress Indicator" card: a caption, the percentage with a change chip, and a wide
 * barcode of 33 bars, white for the completed part. A scanner line with a soft bloom rides the
 * head of the fill and the bars around it duck, so a notch travels along the row and each bar
 * snaps to white as the line crosses it; the chip lands with a springy pop, and while the value
 * rests a shallow ripple keeps running through the bars.
 */
export function BarcodeProgress({
  progress,
  autoPlay = false,
  title = 'Progress Indicator',
  caption = 'You are on track to finish the goal three days early',
  change = 30,
  comparison = 'vs. the last period',
  onMorePress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (ripple), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  // When a controlled value last changed, on the `time` axis.
  const movedAt = useSharedValue(-10);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    movedAt.set(time.get());
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 800, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held, movedAt, time]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    if (!playing.get()) return;
    let t = clock.get() + dt;
    if (looping.get()) {
      if (t >= LOOP) t -= LOOP;
    } else if (t >= REST_T) {
      // A tapped, one-off run stops at the resting frame.
      t = REST_T;
      playing.set(false);
    }
    clock.set(t);
  }, !reducedMotion);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const u = (time.get() - movedAt.get()) / 0.7;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  // The chip shrinks away while the bars rewind and lands with an overshoot once they are filled.
  const chip = useDerivedValue(() => {
    if (reducedMotion || controlled) return 1;
    const t = clock.get();
    if (t >= LOOP - RESET) return 1 - 0.25 * clamp01((t - (LOOP - RESET)) / 0.25);
    const u = (t - LANDED + 0.25) / 0.6;
    if (u <= 0) return 0.75;
    if (u >= 1) return 1;
    return 1 - 0.25 * Math.exp(-6 * u) * Math.cos(11 * u);
  });

  // The figures are real text: re-render only when a whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );
  // In the autoPlay loop the change counts up together with the bars.
  const delta = controlled ? change : Math.round((change * percent) / (REST * 100));

  const chipStyle = useAnimatedStyle(() => ({ transform: [{ scale: chip.get() }] }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} activity={activity} chip={chip} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title}. ${caption}. Up ${delta}% ${comparison}`}
        accessibilityValue={{ min: 0, max: 100, now: percent }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.caption} numberOfLines={1}>
          {caption}
        </Text>
        <Text style={styles.figure}>{percent}%</Text>
        <Animated.View style={[styles.chip, chipStyle]}>
          <Text style={styles.change}>{delta}%</Text>
        </Animated.View>
        <Text style={styles.comparison} numberOfLines={1}>
          {comparison}
        </Text>
      </View>

      <MoreButton onPress={onMorePress} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  title: {
    position: 'absolute',
    left: 11,
    top: 18.8 - 8,
    color: '#f5f5f5',
    fontSize: 11.6,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.1,
  },
  caption: {
    position: 'absolute',
    left: 11,
    right: 11,
    top: 42.7 - 7,
    color: '#9a9a9a',
    fontSize: 10.2,
    lineHeight: 14,
    fontWeight: '400',
    letterSpacing: 0.1,
  },
  figure: {
    position: 'absolute',
    left: 11,
    top: FIGURE_Y - 15,
    color: '#ffffff',
    fontSize: 23.5,
    lineHeight: 30,
    fontWeight: '400',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  chip: {
    position: 'absolute',
    left: CHIP_X,
    top: FIGURE_Y - CHIP_H / 2,
    width: CHIP_W,
    height: CHIP_H,
    justifyContent: 'center',
  },
  change: {
    position: 'absolute',
    left: 17,
    right: 4,
    color: '#ededed',
    fontSize: 8.2,
    lineHeight: 12,
    fontWeight: '500',
    fontFamily: MONO,
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  comparison: {
    position: 'absolute',
    left: 118,
    top: FIGURE_Y - 7,
    color: '#9a9a9a',
    fontSize: 10.2,
    lineHeight: 14,
    fontWeight: '400',
    letterSpacing: 0.1,
  },
  more: {
    position: 'absolute',
    left: 316,
    top: 11,
    width: 20,
    height: 15,
  },
  moreVeil: {
    position: 'absolute',
    left: 4,
    top: 4,
    width: 12,
    height: 7,
    backgroundColor: '#0b0b0b',
  },
});
