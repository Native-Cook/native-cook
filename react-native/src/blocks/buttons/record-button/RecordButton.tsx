import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
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
  /** Controlled state. Leave undefined to let the button start and stop by itself on tap. */
  recording?: boolean;
  onRecordingChange?: (recording: boolean) => void;
  onPress?: () => void;
  /** Records for a few seconds, stops, rests, and starts over. */
  autoPlay?: boolean;
  minutesLabel?: string;
  secondsLabel?: string;
  accessibilityLabel?: string;
};

type Phase = 'idle' | 'recording' | 'done';

// Diameter of the glass sphere at rest and while recording, and the pill that opens behind it.
const SPHERE = 72;
const SPHERE_RECORDING = 76;
const PILL_WIDTH = 208;
const PILL_HEIGHT = 66;
// Distance from the centre to the middle of each timer column.
const COLUMN = 68;
// The canvas overflows the sphere by this much on every side to fit the pill, the glow and the reflection.
const BLEED = 104;
const REFLECTION_DROP = 84;
// How long the green "saved" flash stays before the pill closes.
const DONE_MS = 800;
const AUTOPLAY_REST_MS = 1500;
const AUTOPLAY_RECORD_MS = 3400;
const PULSE_HZ = 1.1;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const CANVAS = SPHERE + BLEED * 2;
const C = CANVAS / 2;

// Microphone drawn around the origin: capsule body, cradle, stem and foot.
const MIC_BODY = { halfWidth: 9, top: -21.6, bottom: 10.8 };
const MIC_LINES = 'M-14 1.8A14 14 0 0 0 14 1.8M0 15.8L0 20.7M-10 20.7L10 20.7';

// Flattens the circular gradients of the reflection into the ellipse they fill.
const REFLECTION_SQUASH = [{ scaleY: 20 / 26 }];

function ellipse(cx: number, cy: number, rx: number, ry: number) {
  return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${rx * 2} 0a${rx} ${ry} 0 1 0 ${-rx * 2} 0Z`;
}

type Motion = {
  /** 0 = sphere only, 1 = pill fully open. */
  expand: SharedValue<number>;
  red: SharedValue<number>;
  green: SharedValue<number>;
  /** 1 = microphone, 0 = record dot. */
  mic: SharedValue<number>;
  time: SharedValue<number>;
};

function Surface({ expand, red, green, mic, time }: Motion) {
  const pulse = useDerivedValue(() => 0.5 + 0.5 * Math.sin(2 * Math.PI * PULSE_HZ * time.get()));
  const radius = useDerivedValue(() => (SPHERE + (SPHERE_RECORDING - SPHERE) * expand.get()) / 2);

  const pillWidth = useDerivedValue(() => SPHERE + (PILL_WIDTH - SPHERE) * expand.get());
  const pillX = useDerivedValue(() => C - pillWidth.get() / 2);
  const pillOpacity = useDerivedValue(() => Math.min(1, 4 * expand.get()));
  const pillTint = useDerivedValue(() => red.get() * expand.get());

  const redGlow = useDerivedValue(() => red.get() * (0.7 + 0.3 * pulse.get()));
  const glowRadius = useDerivedValue(() => radius.get() + 4 + 3 * pulse.get() * red.get());
  const reflectionScale = useDerivedValue(() => [{ scaleX: 1 + 1.6 * expand.get() }]);

  const dot = useDerivedValue(() => 1 - mic.get());
  const dotRadius = useDerivedValue(() => 8 + 1.2 * pulse.get());
  const micScale = useDerivedValue(() => [{ scale: 0.5 + 0.5 * mic.get() }]);

  const reflection = ellipse(C, C + REFLECTION_DROP, 26, 20);
  const reflectionCenter = vec(C, C + REFLECTION_DROP);

  return (
    <Canvas style={{ width: CANVAS, height: CANVAS }}>
      {/* Faint reflection on the floor below, tinted by whatever the sphere is doing. */}
      <Group transform={reflectionScale} origin={reflectionCenter}>
        <Path path={reflection}>
          <RadialGradient
            c={reflectionCenter}
            r={26}
            transform={REFLECTION_SQUASH}
            origin={reflectionCenter}
            colors={['rgba(255,255,255,0.1)', 'rgba(255,255,255,0.03)', 'rgba(255,255,255,0)']}
            positions={[0, 0.5, 1]}
          />
        </Path>
        <Path path={reflection} opacity={red}>
          <RadialGradient
            c={reflectionCenter}
            r={26}
            transform={REFLECTION_SQUASH}
            origin={reflectionCenter}
            colors={['rgba(255,40,44,0.2)', 'rgba(255,40,44,0.07)', 'rgba(255,40,44,0)']}
            positions={[0, 0.5, 1]}
          />
        </Path>
        <Path path={reflection} opacity={green}>
          <RadialGradient
            c={reflectionCenter}
            r={26}
            transform={REFLECTION_SQUASH}
            origin={reflectionCenter}
            colors={['rgba(170,235,60,0.16)', 'rgba(170,235,60,0.05)', 'rgba(170,235,60,0)']}
            positions={[0, 0.5, 1]}
          />
        </Path>
      </Group>

      {/* The pill grows out from behind the sphere. */}
      <Group opacity={pillOpacity}>
        <RoundedRect x={pillX} y={C - PILL_HEIGHT / 2} width={pillWidth} height={PILL_HEIGHT} r={PILL_HEIGHT / 2} color="#161415" />
        <RoundedRect
          x={pillX}
          y={C - PILL_HEIGHT / 2}
          width={pillWidth}
          height={PILL_HEIGHT}
          r={PILL_HEIGHT / 2}
          opacity={pillTint}
        >
          <RadialGradient c={vec(C, C)} r={92} colors={['rgba(255,48,52,0.3)', 'rgba(255,48,52,0)']} />
        </RoundedRect>
        <RoundedRect
          x={pillX}
          y={C - PILL_HEIGHT / 2}
          width={pillWidth}
          height={PILL_HEIGHT}
          r={PILL_HEIGHT / 2}
          style="stroke"
          strokeWidth={1}
        >
          <LinearGradient
            start={vec(C, C - PILL_HEIGHT / 2)}
            end={vec(C, C + PILL_HEIGHT / 2)}
            colors={['rgba(255,255,255,0.14)', 'rgba(255,255,255,0.03)']}
          />
        </RoundedRect>
      </Group>

      <Circle cx={C} cy={C} r={glowRadius} color="rgba(255,44,48,0.75)" opacity={redGlow}>
        <BlurMask blur={14} style="normal" />
      </Circle>
      <Circle cx={C} cy={C} r={glowRadius} color="rgba(170,235,60,0.6)" opacity={green}>
        <BlurMask blur={14} style="normal" />
      </Circle>

      {/* Glass sphere: grey at rest, with a red and a green version faded over it. */}
      <Circle cx={C} cy={C} r={radius}>
        <RadialGradient c={vec(C, C - 4)} r={SPHERE_RECORDING * 0.55} colors={['#625e5f', '#3b3838', '#222020']} positions={[0, 0.6, 1]} />
      </Circle>
      <Circle cx={C} cy={C} r={radius} opacity={red}>
        <RadialGradient c={vec(C, C - 6)} r={SPHERE_RECORDING * 0.6} colors={['#7f3136', '#a8272c', '#f42c30']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={C} cy={C} r={radius} opacity={green}>
        <RadialGradient c={vec(C, C - 6)} r={SPHERE_RECORDING * 0.6} colors={['#56663a', '#7c9a30', '#b9e23c']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={C} cy={C} r={radius} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(C, C - SPHERE / 2)}
          end={vec(C, C + SPHERE / 2)}
          colors={['rgba(255,255,255,0.3)', 'rgba(255,255,255,0.04)']}
        />
      </Circle>
      <Circle cx={C} cy={C} r={radius} style="stroke" strokeWidth={1} color="rgba(255,176,176,0.9)" opacity={red} />
      <Circle cx={C} cy={C} r={radius} style="stroke" strokeWidth={1} color="rgba(228,255,170,0.9)" opacity={green} />

      {/* Record dot. */}
      <Group opacity={dot}>
        <Circle cx={C} cy={C} r={15} color="rgba(255,70,74,0.85)">
          <BlurMask blur={7} style="normal" />
        </Circle>
        <Circle cx={C} cy={C} r={dotRadius} color="#ff5357" />
      </Group>

      {/* Microphone. */}
      <Group opacity={mic}>
        <Circle cx={C} cy={C} r={18} color="rgba(255,255,255,0.22)">
          <BlurMask blur={10} style="normal" />
        </Circle>
        <Group transform={[{ translateX: C }, { translateY: C }]}>
          <Group transform={micScale} origin={vec(0, 0)}>
            <RoundedRect
              x={-MIC_BODY.halfWidth}
              y={MIC_BODY.top}
              width={MIC_BODY.halfWidth * 2}
              height={MIC_BODY.bottom - MIC_BODY.top}
              r={MIC_BODY.halfWidth}
              color="#f4f4f4"
            />
            <Path path={MIC_LINES} style="stroke" strokeWidth={2.5} strokeCap="round" color="#f4f4f4" />
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

const pad = (n: number) => String(n).padStart(2, '0');

/**
 * Round glass microphone button. A tap opens it into a recording pill with a pulsing red dot and
 * a running timer; a second tap flashes it green and closes it again.
 */
export function RecordButton({
  recording,
  onRecordingChange,
  onPress,
  autoPlay = false,
  minutesLabel = 'MIN.',
  secondsLabel = 'SEC.',
  accessibilityLabel = 'Record',
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [phase, setPhase] = useState<Phase>(recording ? 'recording' : 'idle');
  const [seconds, setSeconds] = useState(0);

  // Follow the controlled prop without an effect.
  const [seen, setSeen] = useState(recording);
  if (recording !== seen) {
    setSeen(recording);
    if (recording === true && phase !== 'recording') {
      setSeconds(0);
      setPhase('recording');
    } else if (recording === false && phase === 'recording') {
      setPhase('done');
    }
  }

  const expand = useSharedValue(phase === 'idle' ? 0 : 1);
  const red = useSharedValue(phase === 'recording' ? 1 : 0);
  const green = useSharedValue(0);
  const mic = useSharedValue(phase === 'recording' ? 0 : 1);
  const time = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
  }, phase === 'recording' && !reducedMotion);

  useEffect(() => {
    // These are the button's states, so they still play (as short fades) under reduced motion.
    const to = (value: SharedValue<number>, target: number, duration: number) =>
      value.set(withTiming(target, { duration, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    if (phase === 'recording') {
      to(expand, 1, 520);
      to(red, 1, 320);
      to(green, 0, 200);
      to(mic, 0, 260);
    } else if (phase === 'done') {
      to(red, 0, 260);
      to(green, 1, 260);
      to(mic, 1, 300);
    } else {
      to(expand, 0, 480);
      to(red, 0, 300);
      to(green, 0, 500);
      to(mic, 1, 300);
    }
  }, [phase, expand, red, green, mic]);

  useEffect(() => {
    if (phase === 'recording') {
      const tick = setInterval(() => setSeconds((s) => s + 1), 1000);
      const stop = autoPlay ? setTimeout(() => setPhase('done'), AUTOPLAY_RECORD_MS) : null;
      return () => {
        clearInterval(tick);
        if (stop) clearTimeout(stop);
      };
    }
    const next =
      phase === 'done'
        ? setTimeout(() => setPhase('idle'), DONE_MS)
        : autoPlay
          ? setTimeout(() => {
              setSeconds(0);
              setPhase('recording');
            }, AUTOPLAY_REST_MS)
          : null;
    return () => {
      if (next) clearTimeout(next);
    };
  }, [phase, autoPlay]);

  const toggle = () => {
    onPress?.();
    if (phase === 'done') return;
    const next = phase !== 'recording';
    onRecordingChange?.(next);
    if (recording !== undefined) return;
    if (next) setSeconds(0);
    setPhase(next ? 'recording' : 'done');
  };

  // The timer columns slide out from behind the sphere once the pill is mostly open.
  const leftStyle = useAnimatedStyle(() => {
    const e = expand.get();
    return { opacity: Math.min(1, Math.max(0, (e - 0.45) / 0.55)), transform: [{ translateX: 22 * (1 - e) }] };
  });
  const rightStyle = useAnimatedStyle(() => {
    const e = expand.get();
    return { opacity: Math.min(1, Math.max(0, (e - 0.45) / 0.55)), transform: [{ translateX: -22 * (1 - e) }] };
  });

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ selected: phase === 'recording' }}
      accessibilityValue={phase === 'idle' ? undefined : { text: `${pad(Math.floor(seconds / 60))}:${pad(seconds % 60)}` }}
      onPress={toggle}
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
          <Surface expand={expand} red={red} green={green} mic={mic} time={time} />
        </View>
        <Animated.View pointerEvents="none" style={[styles.column, styles.left, leftStyle]}>
          <Text style={styles.digits}>{pad(Math.floor(seconds / 60))}</Text>
          <Text style={styles.unit}>{minutesLabel}</Text>
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.column, styles.right, rightStyle]}>
          <Text style={styles.digits}>{pad(seconds % 60)}</Text>
          <Text style={styles.unit}>{secondsLabel}</Text>
        </Animated.View>
      </Animated.View>
    </Pressable>
  );
}

const COLUMN_WIDTH = 56;

const styles = StyleSheet.create({
  button: {
    width: SPHERE,
    height: SPHERE,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  column: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: COLUMN_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  left: {
    left: SPHERE / 2 - COLUMN - COLUMN_WIDTH / 2,
  },
  right: {
    left: SPHERE / 2 + COLUMN - COLUMN_WIDTH / 2,
  },
  digits: {
    color: '#f2f2f2',
    fontSize: 23,
    lineHeight: 25,
    fontWeight: '200',
    fontVariant: ['tabular-nums'],
  },
  unit: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: 8,
    lineHeight: 10,
    fontWeight: '500',
    letterSpacing: 0.6,
    marginTop: 1,
  },
});
