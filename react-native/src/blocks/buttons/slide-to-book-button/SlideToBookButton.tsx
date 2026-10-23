import { Canvas, Circle, Group, LinearGradient, Path, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  /** Shown in the tag above the button once the call is booked. */
  doneLabel?: string;
  /** Called when the booking is confirmed, by a tap or by sliding the knob across. */
  onPress?: () => void;
  /** Replays the booking by itself with a pause between runs. */
  autoPlay?: boolean;
};

const WIDTH = 240;
const HEIGHT = 64;
// The canvas overflows the button by this much on every side so its edge is not clipped.
const BLEED = 4;
const INSET = 10;
const KNOB_W = 72;
const KNOB_H = HEIGHT - INSET * 2;
const FULL_W = WIDTH - INSET * 2;
const TRAVEL = FULL_W - KNOB_W;
// How far the resting knob stretches while it "breathes" to hint at the slide.
const NUDGE = 8;
// The label sits in the space to the right of the resting knob.
const LABEL_LEFT = INSET + KNOB_W + 4;
const LABEL_W = WIDTH - 14 - LABEL_LEFT;
const HOLD_MS = 1700;
const AUTOPLAY_PAUSE_MS = 1700;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

const X = BLEED;
const Y = BLEED;
const CY = Y + HEIGHT / 2;

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

/** Width of the lime knob for a stretch `p` (0 resting, 1 filling the pill) at time `t`. */
function knobWidth(p: number, t: number) {
  'worklet';
  const breathe = 0.5 - 0.5 * Math.cos((TAU * t) / 1.8);
  return KNOB_W + TRAVEL * p + NUDGE * breathe * (1 - seg(p, 0, 0.2));
}

// Dotted arrow: the head stays lit, the three tail dots light up one after the other.
const DOT = 3.2;
const DOT_R = 1.05;
const ARROW_HEAD = [
  [2, 0],
  [1, 0],
  [1, -1],
  [0, -2],
  [1, 1],
  [0, 2],
];
const ARROW_TAIL = [-2, -1, 0];

function polyline(points: number[], close: boolean) {
  let d = '';
  for (let i = 0; i < points.length; i += 2) d += `${i === 0 ? 'M' : 'L'}${points[i]} ${points[i + 1]}`;
  return close ? `${d}Z` : d;
}

// Phone handset outline and its two signal arcs, in points around the icon's centre.
const HANDSET = [
  4.41, 6.18, 3.6, 6.11, 2.79, 5.98, 1.99, 5.79, 1.21, 5.54, 0.44, 5.23, -0.29, 4.87, -1.0, 4.46, -1.68, 3.99, -2.32,
  3.48, -2.92, 2.92, -3.48, 2.32, -3.99, 1.68, -4.46, 1.0, -4.87, 0.29, -5.23, -0.44, -5.54, -1.21, -5.79, -1.99, -5.98,
  -2.79, -6.11, -3.6, -6.18, -4.41, -1.29, -4.67, -1.25, -4.21, -1.18, -3.75, -1.07, -3.31, -0.93, -2.87, -1.42, -2.14,
  -2.32, -1.08, -2.43, -0.28, -2.06, 0.25, -1.66, 0.75, -1.22, 1.22, -0.75, 1.66, -0.25, 2.06, 0.28, 2.43, 1.08, 2.32,
  2.14, 1.42, 2.87, 0.93, 3.31, 1.07, 3.75, 1.18, 4.21, 1.25, 4.67, 1.29,
];
const SIGNAL_NEAR = [
  2.07, -4.97, 2.61, -4.85, 3.12, -4.64, 3.59, -4.36, 4.0, -4.0, 4.36, -3.59, 4.64, -3.12, 4.85, -2.61, 4.97, -2.07,
];
const SIGNAL_FAR = [
  2.49, -7.94, 3.5, -7.71, 4.46, -7.33, 5.34, -6.79, 6.13, -6.13, 6.79, -5.34, 7.33, -4.46, 7.71, -3.5, 7.94, -2.49,
];
const PHONE = polyline(HANDSET, true) + polyline(SIGNAL_NEAR, false) + polyline(SIGNAL_FAR, false);
const ARROW_HEAD_PATH = ARROW_HEAD.map(
  ([x, y]) =>
    `M${x * DOT - DOT_R} ${y * DOT}a${DOT_R} ${DOT_R} 0 1 0 ${DOT_R * 2} 0a${DOT_R} ${DOT_R} 0 1 0 ${-DOT_R * 2} 0Z`,
).join('');

function TailDot({ index, clock }: { index: number; clock: SharedValue<number> }) {
  const opacity = useDerivedValue(() => 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(TAU * (clock.get() / 0.9 - index * 0.22))));
  return <Circle cx={ARROW_TAIL[index] * DOT} cy={0} r={DOT_R} color="#0c0c0d" opacity={opacity} />;
}

function Surface({ p, clock }: { p: SharedValue<number>; clock: SharedValue<number> }) {
  const pill = useMemo(() => rrect(rect(X, Y, WIDTH, HEIGHT), HEIGHT / 2, HEIGHT / 2), []);
  const knobW = useDerivedValue(() => knobWidth(p.get(), clock.get()));
  const knobEnd = useDerivedValue(() => vec(X + INSET + knobW.get(), 0));
  const atKnob = useDerivedValue(() => [{ translateX: X + INSET + knobW.get() / 2 }, { translateY: CY }]);
  const arrowOpacity = useDerivedValue(() => 1 - seg(p.get(), 0.05, 0.5));
  const phoneOpacity = useDerivedValue(() => seg(p.get(), 0.4, 0.85));
  const phoneScale = useDerivedValue(() => [
    { translateX: -1 },
    { translateY: 1 },
    { scale: 1.1 * (0.7 + 0.3 * seg(p.get(), 0.4, 1)) },
  ]);

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <RoundedRect x={X} y={Y} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#0c0c0d" />
      {/* Glossy edge: a thin light line following the top of the pill. */}
      <Group clip={pill}>
        <RoundedRect x={X + 3.5} y={Y + 3.5} width={WIDTH - 7} height={HEIGHT - 7} r={HEIGHT / 2 - 3.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(0, Y)}
            end={vec(0, Y + HEIGHT * 0.4)}
            colors={['rgba(255,255,255,0.8)', 'rgba(255,255,255,0.35)', 'rgba(255,255,255,0)']}
            positions={[0, 0.35, 1]}
          />
        </RoundedRect>
      </Group>

      <RoundedRect x={X + INSET} y={Y + INSET} width={knobW} height={KNOB_H} r={KNOB_H / 2}>
        <LinearGradient start={vec(X + INSET, 0)} end={knobEnd} colors={['#7dfe3f', '#a2ff24', '#a8ff20']} positions={[0, 0.6, 1]} />
      </RoundedRect>

      <Group transform={atKnob}>
        <Group opacity={arrowOpacity}>
          <Path path={ARROW_HEAD_PATH} color="#0c0c0d" />
          {ARROW_TAIL.map((_, i) => (
            <TailDot key={i} index={i} clock={clock} />
          ))}
        </Group>
        <Group transform={phoneScale} opacity={phoneOpacity}>
          <Path path={PHONE} style="stroke" strokeWidth={1.35} strokeJoin="round" strokeCap="round" color="#0c0c0d" />
        </Group>
      </Group>
    </Canvas>
  );
}

/** A band of brighter text that sweeps across the label; three of them stack into a soft edge. */
function Shine({ label, width, clock }: { label: string; width: number; clock: SharedValue<number> }) {
  const position = (t: number) => {
    'worklet';
    return -40 + (LABEL_W + 80) * seg(t % 2.6, 0.3, 1.3) - width / 2;
  };
  const band = useAnimatedStyle(() => ({ transform: [{ translateX: position(clock.get()) }] }));
  const text = useAnimatedStyle(() => ({ transform: [{ translateX: -position(clock.get()) }] }));
  return (
    <Animated.View style={[styles.shine, { width }, band]}>
      <Animated.View style={[styles.labelBox, text]}>
        <Text style={[styles.label, styles.labelLit]} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Black "Book a call" pill with a lime knob. Tap it, or slide the knob to the right: the knob
 * stretches across the whole pill, a phone icon replaces the arrow and a small "Call booked" tag
 * pops up above before everything slides back.
 */
export function SlideToBookButton({
  label = 'Book a call',
  doneLabel = 'Call booked',
  onPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);

  const p = useSharedValue(0);
  const tag = useSharedValue(0);
  const clock = useSharedValue(0);
  const busy = useRef(false);
  const confirmed = useRef(onPress);
  useEffect(() => {
    confirmed.current = onPress;
  }, [onPress]);

  // The knob breathes, the arrow marches and the label shines all the time; reduced motion stops them.
  const idle = useFrameCallback((info) => {
    clock.set(clock.get() + (info.timeSincePreviousFrame ?? 0) / 1000);
  }, false);
  useEffect(() => {
    idle.setActive(!reducedMotion);
    return () => idle.setActive(false);
  }, [idle, reducedMotion]);

  const confirm = useCallback(
    async (announce: boolean) => {
      if (busy.current) return;
      busy.current = true;
      if (announce) confirmed.current?.();
      // The stretch is the button's state change, so it stays (short) with reduced motion.
      const keep = { reduceMotion: ReduceMotion.Never };
      p.set(withTiming(1, { duration: 420, easing: EASE_OUT, ...keep }));
      await sleep(220);
      tag.set(withTiming(1, { duration: 260, easing: EASE_OUT, ...keep }));
      await sleep(HOLD_MS);
      tag.set(withTiming(0, { duration: 200, ...keep }));
      await sleep(150);
      p.set(withTiming(0, { duration: 480, easing: Easing.inOut(Easing.cubic), ...keep }));
      await sleep(560);
      busy.current = false;
    },
    [p, tag],
  );

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) return;
        await confirm(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [autoPlay, confirm]);

  // Plain responder handlers: a tap confirms, a horizontal slide drags the knob.
  const touch = useRef({ x: 0, y: 0, moved: false });
  const release = (dx: number) => {
    setPressed(false);
    if (busy.current) return;
    // A slide confirms once the knob is past half-way, otherwise it springs back.
    if (!touch.current.moved || dx / TRAVEL > 0.5) confirm(true);
    else p.set(withTiming(0, { duration: 320, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  };
  const onGrant = (e: GestureResponderEvent) => {
    touch.current = { x: e.nativeEvent.pageX, y: e.nativeEvent.pageY, moved: false };
    setPressed(true);
    if (process.env.EXPO_OS !== 'web') {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    }
  };
  const onMove = (e: GestureResponderEvent) => {
    if (busy.current) return;
    const dx = e.nativeEvent.pageX - touch.current.x;
    const dy = e.nativeEvent.pageY - touch.current.y;
    if (!touch.current.moved && Math.abs(dx) > 6 && Math.abs(dx) > Math.abs(dy)) {
      touch.current.moved = true;
      setPressed(false);
    }
    if (touch.current.moved) p.set(Math.min(1, Math.max(0, dx / TRAVEL)));
  };

  // The stretching knob wipes the label away from the left.
  const cover = (pv: number, t: number) => {
    'worklet';
    return Math.max(0, INSET + knobWidth(pv, t) + 2 - LABEL_LEFT);
  };
  const clip = useAnimatedStyle(() => ({ transform: [{ translateX: cover(p.get(), clock.get()) }] }));
  const unclip = useAnimatedStyle(() => ({ transform: [{ translateX: -cover(p.get(), clock.get()) }] }));
  const tagStyle = useAnimatedStyle(() => ({
    opacity: tag.get(),
    transform: [{ translateY: (1 - tag.get()) * 6 }, { scale: 0.9 + 0.1 * tag.get() }],
  }));

  return (
    <View
      onStartShouldSetResponder={() => true}
      // Hold on to a horizontal slide so the list underneath does not take it.
      onResponderTerminationRequest={() => !touch.current.moved}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={(e) => release(e.nativeEvent.pageX - touch.current.x)}
      onResponderTerminate={() => {
        touch.current.moved = true;
        release(0);
      }}
      accessible
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint="Double tap or slide right to confirm"
      onAccessibilityTap={() => confirm(true)}
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
          <Surface p={p} clock={clock} />
        </View>
        <Animated.View pointerEvents="none" style={[styles.labelClip, clip]}>
          <Animated.View style={[styles.labelBox, unclip]}>
            <Text style={styles.label} numberOfLines={1}>
              {label}
            </Text>
            {!reducedMotion && (
              <>
                <Shine label={label} width={64} clock={clock} />
                <Shine label={label} width={42} clock={clock} />
                <Shine label={label} width={20} clock={clock} />
              </>
            )}
          </Animated.View>
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.tag, tagStyle]}>
          <View style={styles.tagTail} />
          <View style={styles.tagBody}>
            <Text style={styles.tagText} numberOfLines={1}>
              {doneLabel}
            </Text>
          </View>
        </Animated.View>
      </Animated.View>
    </View>
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
  labelClip: {
    position: 'absolute',
    left: LABEL_LEFT,
    top: 0,
    width: LABEL_W,
    height: HEIGHT,
    overflow: 'hidden',
  },
  labelBox: {
    width: LABEL_W,
    height: HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#8c8c8c',
    fontSize: 19,
    fontWeight: '400',
    letterSpacing: 0.1,
  },
  labelLit: {
    color: '#ffffff',
    opacity: 0.34,
  },
  shine: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: HEIGHT,
    overflow: 'hidden',
  },
  tag: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: HEIGHT + 5,
    alignItems: 'center',
  },
  tagBody: {
    height: 21,
    paddingHorizontal: 9,
    borderRadius: 8,
    justifyContent: 'center',
    backgroundColor: '#dcfbd3',
  },
  tagTail: {
    position: 'absolute',
    bottom: -3,
    width: 8,
    height: 8,
    borderRadius: 1.5,
    backgroundColor: '#dcfbd3',
    transform: [{ rotate: '45deg' }],
  },
  tagText: {
    color: '#3f6b35',
    fontSize: 10,
    fontWeight: '500',
  },
});
