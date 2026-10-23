import { Blur, BlurMask, Canvas, Circle, Group, LinearGradient, Paint, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled share of the timer that is left, 0…1. Leave undefined to show the reference's 13:12 (or the autoPlay loop). */
  progress?: number;
  /** Runs by itself: winds up to the full duration, races down to 13:12, ticks there in real time, and starts over. */
  autoPlay?: boolean;
  /** Length of the timer in seconds. */
  duration?: number;
  /** Time of day the timer goes off. */
  alarm?: string;
  label?: string;
  onPauseChange?: (paused: boolean) => void;
  onCancel?: () => void;
};

const WIDTH = 330;
const HEIGHT = 200;
// The canvas overflows the widget by this much on every side to fit the panel's shadow.
const BLEED = 30;

const PILL_X = 67.3;
const PILL_W = 195.4;
const PILL_H = 35.3;
const RING_X = 86.2;
const RING_Y = PILL_H / 2;
const RING_R = 10.6;

const PANEL_Y = 57.4;
const PANEL_H = HEIGHT - PANEL_Y;
const PANEL_R = 36;
// The progress stroke runs round the panel, leaving a gap at the top centre.
const STROKE = 7;
const EDGE = 10.5;
const LEFT = EDGE;
const RIGHT = WIDTH - EDGE;
const TOP = PANEL_Y + EDGE;
const BOTTOM = HEIGHT - EDGE;
const CORNER = PANEL_R - EDGE;
const GAP_FROM = 107.5;
const GAP_TO = 222.5;
const TOP_RUN = RIGHT - CORNER - GAP_TO;
const SIDE_RUN = BOTTOM - TOP - CORNER * 2;
const BOTTOM_RUN = RIGHT - LEFT - CORNER * 2;
const ARC = (Math.PI / 2) * CORNER;
const LENGTH = TOP_RUN * 2 + SIDE_RUN * 2 + BOTTOM_RUN + ARC * 4;
// Starts right of the gap and runs clockwise back to its left.
const BORDER =
  `M${GAP_TO} ${TOP}H${RIGHT - CORNER}A${CORNER} ${CORNER} 0 0 1 ${RIGHT} ${TOP + CORNER}V${BOTTOM - CORNER}` +
  `A${CORNER} ${CORNER} 0 0 1 ${RIGHT - CORNER} ${BOTTOM}H${LEFT + CORNER}A${CORNER} ${CORNER} 0 0 1 ${LEFT} ${BOTTOM - CORNER}` +
  `V${TOP + CORNER}A${CORNER} ${CORNER} 0 0 1 ${LEFT + CORNER} ${TOP}H${GAP_FROM}`;

const BUTTON_Y = 128.6;
const BUTTON_R = 32.1;
const PAUSE_X = 67.2;
const CANCEL_X = 262.8;
const DIGIT_W = 24.4;
const DIGIT_H = 48;

// The reference's frame: 13:12 left of a 16:15 timer.
const DURATION = 975;
const REST_S = 792;
// The autoPlay loop (seconds): wind up to the full duration, race down to the reference, tick in real time.
const WIND = 0.7;
const RACE = 2.6;
const HOLD = 5.2;
const LOOP = WIND + RACE + HOLD;
const LANDED = WIND + RACE;

const ORANGE = '#ff9f0a';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const BELL =
  'M-4.7 3.1C-3.5 2.1-3.4 0.6-3.4-1C-3.4-3.3-1.9-4.9 0-4.9C1.9-4.9 3.4-3.3 3.4-1C3.4 0.6 3.5 2.1 4.7 3.1Z' +
  'M-1.3 4.1A1.3 1.3 0 0 0 1.3 4.1Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Share of the reference timer left at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < WIND) return (REST_S - HOLD + (DURATION - REST_S + HOLD) * easeInOutCubic(t / WIND)) / DURATION;
  if (t < LANDED) return (DURATION - (DURATION - REST_S) * easeInOutCubic((t - WIND) / RACE)) / DURATION;
  return (REST_S - (t - LANDED)) / DURATION;
}

/** 0…1: how fast the time is being wound at `t` seconds into the loop; 0 while it ticks in real time. */
function loopActivity(t: number) {
  'worklet';
  if (t < WIND) return Math.sin(Math.PI * (t / WIND));
  if (t < LANDED) return Math.sin(Math.PI * ((t - WIND) / RACE));
  return 0;
}

/** Point at `u` (0…1) along the border stroke. */
function borderPoint(u: number) {
  'worklet';
  let s = clamp01(u) * LENGTH;
  const corner = (cx: number, cy: number, from: number, along: number) => {
    const a = from + along / CORNER;
    return [cx + CORNER * Math.cos(a), cy + CORNER * Math.sin(a)];
  };
  if (s <= TOP_RUN) return [GAP_TO + s, TOP];
  s -= TOP_RUN;
  if (s <= ARC) return corner(RIGHT - CORNER, TOP + CORNER, -Math.PI / 2, s);
  s -= ARC;
  if (s <= SIDE_RUN) return [RIGHT, TOP + CORNER + s];
  s -= SIDE_RUN;
  if (s <= ARC) return corner(RIGHT - CORNER, BOTTOM - CORNER, 0, s);
  s -= ARC;
  if (s <= BOTTOM_RUN) return [RIGHT - CORNER - s, BOTTOM];
  s -= BOTTOM_RUN;
  if (s <= ARC) return corner(LEFT + CORNER, BOTTOM - CORNER, Math.PI / 2, s);
  s -= ARC;
  if (s <= SIDE_RUN) return [LEFT, BOTTOM - CORNER - s];
  s -= SIDE_RUN;
  if (s <= ARC) return corner(LEFT + CORNER, TOP + CORNER, Math.PI, s);
  s -= ARC;
  return [LEFT + CORNER + s, TOP];
}

/** A cheap repeatable random number in 0…1. */
function noise(seed: number) {
  'worklet';
  const v = Math.sin(seed * 127.1 + 31.7) * 43758.5453;
  return v - Math.floor(v);
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** 1 while the timer is running (playing and not paused). */
  running: SharedValue<number>;
  paused: SharedValue<number>;
  /** Seconds on the timer; the tip of the stroke beats once per second of it. */
  seconds: SharedValue<number>;
};

function Surface({ value, time, activity, running, paused, seconds }: Motion) {
  const end = useDerivedValue(() => clamp01(value.get()));
  const tip = useDerivedValue(() => borderPoint(value.get()));
  // A paused timer breathes.
  const lit = useDerivedValue(() => 1 - paused.get() * (0.3 + 0.2 * Math.sin(time.get() * 3.4)));
  // The burning tip of the stroke: it beats with every second and flares while the time is being wound.
  const beat = useDerivedValue(() => {
    const since = Math.ceil(seconds.get()) - seconds.get();
    return running.get() * (1 - activity.get()) * Math.exp(-5 * since);
  });
  const tipTransform = useDerivedValue(() => [{ translateX: tip.get()[0] }, { translateY: tip.get()[1] }]);
  const glowRadius = useDerivedValue(() => 5.5 + 3 * beat.get() + 3.5 * activity.get());
  const glowAlpha = useDerivedValue(() => running.get() * (0.55 + 0.3 * beat.get() + 0.15 * activity.get()));
  // A faint bloom along the whole stroke, stronger while the time is being wound.
  const bloomAlpha = useDerivedValue(() => running.get() * (0.22 + 0.5 * activity.get()));
  const coreAlpha = useDerivedValue(() => running.get() * (0.5 + 0.5 * Math.max(beat.get(), activity.get())));
  // Sparks thrown off the tip: a couple while it ticks, a shower while the time is being wound.
  const sparks = useDerivedValue(() => {
    const count = running.get() > 0 ? Math.round(2 + 7 * activity.get()) : 0;
    const [x, y] = tip.get();
    let d = '';
    for (let i = 0; i < count; i++) {
      const cycle = time.get() / 0.75 + i / 9;
      const age = cycle - Math.floor(cycle);
      const seed = Math.floor(cycle) * 9 + i;
      const angle = noise(seed) * Math.PI * 2;
      const reach = (9 + 14 * noise(seed + 0.5)) * (1 - Math.pow(1 - age, 2));
      const r = 1.35 * (1 - age);
      const px = x + Math.cos(angle) * reach;
      const py = y + Math.sin(angle) * reach + 7 * age * age;
      d += `M${(px - r).toFixed(2)} ${py.toFixed(2)}a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(r * 2).toFixed(2)} 0a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(-r * 2).toFixed(2)} 0Z`;
    }
    return d || 'M0 0';
  });
  // The compact pill's ring mirrors the stroke.
  const ring = useDerivedValue(() => {
    const sweep = Math.min(359.9, Math.max(0.01, clamp01(value.get()) * 360));
    const a = ((sweep - 90) * Math.PI) / 180;
    return `M${RING_X} ${RING_Y - RING_R}A${RING_R} ${RING_R} 0 ${sweep > 180 ? 1 : 0} 1 ${(RING_X + RING_R * Math.cos(a)).toFixed(2)} ${(RING_Y + RING_R * Math.sin(a)).toFixed(2)}`;
  });

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* The lighter sheet the expanded panel sits on. */}
        <RoundedRect x={-9} y={PANEL_Y - 11} width={WIDTH + 18} height={PANEL_H + 60} r={46}>
          <LinearGradient start={vec(0, PANEL_Y + PANEL_H - 50)} end={vec(0, PANEL_Y + PANEL_H + 44)} colors={['rgba(246,246,246,1)', 'rgba(246,246,246,0)']} />
        </RoundedRect>
        <RoundedRect x={10} y={PANEL_Y + 16} width={WIDTH - 20} height={PANEL_H - 10} r={PANEL_R} color="rgba(0,0,0,0.3)">
          <BlurMask blur={14} style="normal" />
        </RoundedRect>

        {/* Compact pill */}
        <RoundedRect x={PILL_X} y={0} width={PILL_W} height={PILL_H} r={PILL_H / 2} color="#000000" />
        <Circle cx={RING_X} cy={RING_Y} r={RING_R} style="stroke" strokeWidth={2.2} color="#2c2c2e" />
        <Path path={ring} style="stroke" strokeWidth={2.2} strokeCap="round" color={ORANGE} opacity={lit} />

        {/* Expanded panel */}
        <RoundedRect x={0} y={PANEL_Y} width={WIDTH} height={PANEL_H} r={PANEL_R} color="#000000" />
        <Path path={BORDER} style="stroke" strokeWidth={STROKE} strokeCap="round" color="#2a2a2c" />
        <Group
          layer={
            <Paint opacity={bloomAlpha}>
              <Blur blur={5} />
            </Paint>
          }
        >
          <Path path={BORDER} style="stroke" strokeWidth={STROKE + 2} strokeCap="round" color="rgba(255,159,10,0.35)" start={0} end={end} />
        </Group>
        <Path path={BORDER} style="stroke" strokeWidth={STROKE} strokeCap="round" color={ORANGE} start={0} end={end} opacity={lit} />

        <Group transform={tipTransform}>
          <Circle cx={0} cy={0} r={glowRadius} color="rgba(255,176,46,0.9)" opacity={glowAlpha}>
            <BlurMask blur={5} style="normal" />
          </Circle>
          <Circle cx={0} cy={0} r={2.7} color="#fff3d6" opacity={coreAlpha}>
            <BlurMask blur={1.1} style="normal" />
          </Circle>
        </Group>
        <Path path={sparks} color="#ffd98a" />

        {/* Alarm bell */}
        <Group transform={[{ translateX: 142.9 }, { translateY: 95.4 }]}>
          <Path path={BELL} color="#5a5a5e" />
        </Group>
      </Group>
    </Canvas>
  );
}

/** One digit of the big time. While the timer ticks, a new digit rolls up into place like an odometer. */
function Digit({ char, rolling }: { char: string; rolling: boolean }) {
  return (
    <View style={styles.digit}>
      <Animated.Text
        key={char}
        style={styles.time}
        entering={rolling ? rollIn : undefined}
        exiting={rolling ? rollOut : undefined}
      >
        {char}
      </Animated.Text>
    </View>
  );
}

const ROLL = { duration: 320, easing: EASE_OUT };

function rollIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ translateY: DIGIT_H * 0.55 }] },
    animations: { opacity: withTiming(1, { duration: 200 }), transform: [{ translateY: withTiming(0, ROLL) }] },
  };
}

function rollOut() {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
    animations: { opacity: withTiming(0, { duration: 200 }), transform: [{ translateY: withTiming(-DIGIT_H * 0.55, ROLL) }] },
  };
}

function timecode(seconds: number) {
  const s = Math.max(0, Math.ceil(seconds - 1e-4));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/** The two time read-outs. Only this re-renders while the time counts. */
function Readouts({
  seconds,
  initialSeconds,
  activity,
}: {
  seconds: SharedValue<number>;
  initialSeconds: number;
  activity: SharedValue<number>;
}) {
  const [shown, setShown] = useState(() => ({ seconds: Math.ceil(initialSeconds - 1e-4), rolling: false }));
  useAnimatedReaction(
    () => Math.ceil(seconds.get() - 1e-4) * 2 + (activity.get() > 0 ? 0 : 1),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, { seconds: Math.floor(now / 2), rolling: now % 2 === 1 && before != null && before % 2 === 1 });
    },
  );
  const text = timecode(shown.seconds);
  return (
    <>
      <Text style={styles.compact}>{text}</Text>
      <View style={styles.big}>
        <Digit char={text[0]} rolling={shown.rolling} />
        <Digit char={text[1]} rolling={shown.rolling} />
        <Text style={[styles.time, styles.colon]}>:</Text>
        <Digit char={text[3]} rolling={shown.rolling} />
        <Digit char={text[4]} rolling={shown.rolling} />
      </View>
    </>
  );
}

function RoundButton({
  x,
  color,
  label,
  onPress,
  children,
}: {
  x: number;
  color: string;
  label: string;
  onPress: () => void;
  children: ReactNode;
}) {
  const [pressed, setPressed] = useState(false);
  const position: ViewStyle = { left: x - BUTTON_R, top: BUTTON_Y - BUTTON_R };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      style={[styles.button, position]}
    >
      <Animated.View
        style={[
          styles.buttonFace,
          {
            backgroundColor: color,
            transform: [{ scale: pressed ? 0.93 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 150,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <Canvas style={styles.buttonFace}>{children}</Canvas>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Timer live activity: a compact black pill with a small ring and the time, and an expanded
 * black panel whose border is an orange stroke that shrinks as the time runs out, with pause and
 * cancel buttons around the big time. The tip of the stroke burns: it glows, beats with every
 * second and throws sparks (a shower while the time is being wound), the digits of the big
 * time roll like an odometer on each tick, and a paused timer breathes.
 */
export function TimerBorderProgress({
  progress,
  autoPlay = false,
  duration = DURATION,
  alarm = '14:44',
  label = 'Timer',
  onPauseChange,
  onCancel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST_S / DURATION;
  const [paused, setPaused] = useState(false);

  // Seconds: `time` never stops (sparks, breathing), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : LANDED);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const pausedValue = useSharedValue(0);
  const held = useSharedValue(target);
  // When a controlled value last changed, on the `time` axis.
  const movedAt = useSharedValue(-10);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : LANDED);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    movedAt.set(time.get());
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 800, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held, movedAt, time]);

  useEffect(() => {
    pausedValue.set(withTiming(paused ? 1 : 0, { duration: 200, reduceMotion: ReduceMotion.Never }));
  }, [paused, pausedValue]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    // Pause really pauses: the loop's clock stands still.
    if (!playing.get() || pausedValue.get() > 0) return;
    let t = clock.get() + dt;
    if (looping.get()) {
      if (t >= LOOP) t -= LOOP;
    } else if (t >= LANDED) {
      // A tapped, one-off run stops at the resting frame.
      t = LANDED;
      playing.set(false);
    }
    clock.set(t);
  }, !reducedMotion);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const seconds = useDerivedValue(() => clamp01(value.get()) * (controlled ? duration : DURATION));
  // Plain copy of the starting value: children seed their text from it instead of reading a
  // shared value while rendering.
  const initialSeconds =
    clamp01(controlled ? target : loopValue(autoPlay && !reducedMotion ? 0 : LANDED)) * (controlled ? duration : DURATION);
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return playing.get() ? loopActivity(clock.get()) : 0;
    const u = (time.get() - movedAt.get()) / 0.7;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  const running = useDerivedValue(() => (playing.get() ? 1 - pausedValue.get() : 0));

  // For assistive tech: the time left, refreshed once per second and not at all while it is being wound.
  const [left, setLeft] = useState(Math.ceil(target * duration));
  useAnimatedReaction(
    () => (activity.get() > 0 ? -1 : Math.ceil(seconds.get() - 1e-4)),
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(setLeft, now);
    },
  );

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const pauseGlyph = useDerivedValue(() => 1 - pausedValue.get());

  return (
    <View style={styles.widget}>
      <Pressable
        onPress={restart}
        style={StyleSheet.absoluteFill}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${label}, alarm at ${alarm}${paused ? ', paused' : ''}`}
        accessibilityValue={{ text: `${timecode(left)} left` }}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface value={value} time={time} activity={activity} running={running} paused={pausedValue} seconds={seconds} />
        </View>
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Text style={styles.alarm}>{alarm}</Text>
          <Readouts seconds={seconds} initialSeconds={initialSeconds} activity={activity} />
          <Text style={styles.label}>{label}</Text>
        </View>
      </Pressable>

      <RoundButton
        x={PAUSE_X}
        color="#262628"
        label={paused ? 'Resume' : 'Pause'}
        onPress={() => {
          setPaused(!paused);
          onPauseChange?.(!paused);
        }}
      >
        <Path
          path={`M${BUTTON_R - 7.3} ${BUTTON_R - 8.6}v17.2M${BUTTON_R + 7.3} ${BUTTON_R - 8.6}v17.2`}
          style="stroke"
          strokeWidth={6.6}
          strokeCap="round"
          color="#ffffff"
          opacity={pauseGlyph}
        />
        <Path
          path={`M${BUTTON_R - 6.5} ${BUTTON_R - 10.5}L${BUTTON_R + 11} ${BUTTON_R}L${BUTTON_R - 6.5} ${BUTTON_R + 10.5}Z`}
          style="stroke"
          strokeWidth={5}
          strokeJoin="round"
          color="#ffffff"
          opacity={pausedValue}
        />
        <Path
          path={`M${BUTTON_R - 6.5} ${BUTTON_R - 10.5}L${BUTTON_R + 11} ${BUTTON_R}L${BUTTON_R - 6.5} ${BUTTON_R + 10.5}Z`}
          color="#ffffff"
          opacity={pausedValue}
        />
      </RoundButton>
      <RoundButton
        x={CANCEL_X}
        color="#4a1010"
        label="Cancel"
        onPress={() => {
          setPaused(false);
          restart();
          onCancel?.();
        }}
      >
        <Path
          path={`M${BUTTON_R - 8.3} ${BUTTON_R - 8.3}l16.6 16.6M${BUTTON_R + 8.3} ${BUTTON_R - 8.3}l-16.6 16.6`}
          style="stroke"
          strokeWidth={7}
          strokeCap="round"
          color="#ff3b3b"
        />
      </RoundButton>
    </View>
  );
}

const styles = StyleSheet.create({
  widget: {
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
  compact: {
    position: 'absolute',
    right: WIDTH - 255.6,
    top: RING_Y - 10,
    color: ORANGE,
    fontSize: 14.6,
    lineHeight: 20,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  alarm: {
    position: 'absolute',
    left: 153.4,
    top: 95.4 - 10,
    color: '#5a5a5e',
    fontSize: 14.8,
    lineHeight: 20,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  big: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 128.4 - DIGIT_H / 2,
    height: DIGIT_H,
    flexDirection: 'row',
    justifyContent: 'center',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    alignItems: 'center',
    overflow: 'hidden',
  },
  time: {
    color: '#ffffff',
    fontSize: 40.5,
    lineHeight: DIGIT_H,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  colon: {
    width: 12,
    textAlign: 'center',
    top: -3,
  },
  label: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 159.8 - 10.5,
    textAlign: 'center',
    color: '#5a5a5e',
    fontSize: 15.6,
    lineHeight: 21,
    fontWeight: '500',
  },
  button: {
    position: 'absolute',
    width: BUTTON_R * 2,
    height: BUTTON_R * 2,
  },
  buttonFace: {
    width: BUTTON_R * 2,
    height: BUTTON_R * 2,
    borderRadius: BUTTON_R,
  },
});
