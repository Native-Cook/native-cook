import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  vec,
} from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { Platform, Pressable, StyleSheet, Text, TextInput, type TextInputProps, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Controlled signal strength, 0…1. Leave undefined to show the reference's 56.2% (or the autoPlay loop). */
  progress?: number;
  /** Sweeps from 0 to 56.2% by itself, holds, drops and starts over. */
  autoPlay?: boolean;
  section?: string;
  title?: string;
  signalLabel?: string;
  noiseLabel?: string;
  /** Change shown next to the reading. */
  delta?: string;
  channelsLabel?: string;
};

const WIDTH = 340;
const HEIGHT = 218;
const PAD = 18;
// The canvas overflows the panel by this much on every side for the backdrop and the brackets.
const BLEED = 44;

const HEADER_H = 36;
const BAR_X = PAD;
const BAR_Y = 110;
const BAR_W = WIDTH - PAD * 2;
const BAR_H = 7.5;
const TICKS = 60;
const TICK_W = 1.3;
const DIVIDER_Y = 153;
const CHIP_Y = 184;
const CHIP = 16;
const CHIP_PITCH = 19.5;
const SCAN_W = 34;

// Width of a "56.2%" reading and of one of its digits, to keep the neighbours attached to it.
const READING_W = 86;
const DIGIT_W = 18.2;

// The channel chips wake up one after another as the signal passes these levels.
const CHANNEL_AT = [0.1, 0.22, 0.34, 0.46];

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.562;
const START = 0.55;
const FILL = 2.1;
const HOLD = 3.6;
const RESET = 0.6;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + FILL + 1;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });
const YELLOW = '#f6ef4c';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

/** Signal strength at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + FILL) return REST * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how hard the probe is scanning at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  return clamp01((t - START) / 0.2) * clamp01((START + FILL + 0.25 - t) / 0.5);
}

/** 0…1: how far the corner brackets have locked onto the panel at `t` seconds into the loop. */
function loopLock(t: number) {
  'worklet';
  if (t > LOOP - RESET) return 1 - clamp01((t - (LOOP - RESET)) / (RESET * 0.8));
  return easeOutCubic(clamp01((t - 0.05) / 0.45));
}

function percent(v: number) {
  'worklet';
  return `${(Math.round(v * 1000) / 10).toFixed(1)}%`;
}

const TICK_PATH = Array.from({ length: TICKS }, (_, i) => {
  const x = BAR_X + (i + 0.5) * (BAR_W / TICKS) - TICK_W / 2;
  return `M${x.toFixed(2)} ${BAR_Y}h${TICK_W}v${BAR_H}h${-TICK_W}Z`;
}).join('');

const BRACKET = 5;
// One corner bracket per corner, drawn 1.5pt outside the panel: [corner x, corner y, x dir, y dir].
const CORNERS = [
  [0, 0, 1, 1],
  [WIDTH, 0, -1, 1],
  [0, HEIGHT, 1, -1],
  [WIDTH, HEIGHT, -1, -1],
];

// Generic channel glyphs, each drawn around its own centre: linked nodes, a grid, a target, a half disc.
const GLYPH_LINKS = 'M-2.3 -3.3L2.5 0L-2.3 3.3';
const GLYPH_HALF = 'M0 -3.9A3.9 3.9 0 0 1 0 3.9Z';

/** Soft streaks of the out-of-focus backdrop: centre, half length, half thickness, colour, alpha. */
const BACKDROP = [
  { x: 40, y: -10, l: 190, t: 70, rgb: '143,182,201', alpha: 0.95 },
  { x: 170, y: 20, l: 150, t: 40, rgb: '168,201,214', alpha: 0.7 },
  { x: 60, y: 230, l: 220, t: 60, rgb: '24,48,26', alpha: 0.9 },
  { x: 300, y: 250, l: 200, t: 70, rgb: '20,40,24', alpha: 0.9 },
  { x: 330, y: 60, l: 160, t: 46, rgb: '36,70,38', alpha: 0.8 },
  { x: 250, y: 10, l: 170, t: 16, rgb: '176,180,84', alpha: 0.7 },
  { x: 20, y: 120, l: 150, t: 14, rgb: '190,190,96', alpha: 0.6 },
  { x: 120, y: 250, l: 150, t: 12, rgb: '170,176,90', alpha: 0.5 },
  { x: 350, y: 150, l: 150, t: 18, rgb: '110,150,80', alpha: 0.7 },
];
const BACKDROP_TILT = (-58 * Math.PI) / 180;

// Brightness of the static along the ticks: an irregular pattern that jumps about a dozen times a second.
const STATIC = [0, 0.5, 0, 0, 0.9, 0, 0.3, 0, 0, 0.7, 0, 0, 1, 0, 0.4, 0, 0, 0.8, 0, 0].map(
  (a) => `rgba(255,255,255,${(a * 0.3).toFixed(3)})`,
);

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  lock: SharedValue<number>;
  /** 0 with reduced motion: no static, no drifting backdrop. */
  ambient: number;
};

function Bracket({ index, lock }: { index: number; lock: SharedValue<number> }) {
  const [cx, cy, dx, dy] = CORNERS[index];
  const x = cx - dx * 1.5;
  const y = cy - dy * 1.5;
  const path = `M${x + dx * BRACKET} ${y}H${x}V${y + dy * BRACKET}`;
  // They fly in from outside and snap onto the corners.
  const transform = useDerivedValue(() => {
    const away = (1 - lock.get()) * 9;
    return [{ translateX: -dx * away }, { translateY: -dy * away }];
  });
  return <Path path={path} transform={transform} style="stroke" strokeWidth={1} color="#ffffff" opacity={lock} />;
}

function Channel({ index, value }: { index: number; value: SharedValue<number> }) {
  const x = PAD + index * CHIP_PITCH;
  const cx = x + CHIP / 2;
  const cy = CHIP_Y + CHIP / 2;
  const on = useDerivedValue(() => clamp01((value.get() - CHANNEL_AT[index]) / 0.03));
  const opacity = useDerivedValue(() => 0.28 + 0.72 * on.get());
  // A yellow flash as the channel comes online.
  const flash = useDerivedValue(() => 4 * on.get() * (1 - on.get()));
  return (
    <Group>
      <Rect x={x} y={CHIP_Y} width={CHIP} height={CHIP} color="rgba(255,255,255,0.09)" />
      <Rect x={x} y={CHIP_Y} width={CHIP} height={CHIP} color={YELLOW} opacity={flash} />
      <Group transform={[{ translateX: cx }, { translateY: cy }]} opacity={opacity}>
        {index === 0 && (
          <>
            <Path path={GLYPH_LINKS} style="stroke" strokeWidth={1} color="#ffffff" />
            <Circle cx={-2.3} cy={-3.3} r={1.5} color="#ffffff" />
            <Circle cx={2.5} cy={0} r={1.5} color="#ffffff" />
            <Circle cx={-2.3} cy={3.3} r={1.5} color="#ffffff" />
          </>
        )}
        {index === 1 && (
          <>
            <RoundedRect x={-3.9} y={-3.9} width={3.2} height={3.2} r={0.9} color="#ffffff" />
            <RoundedRect x={0.7} y={-3.9} width={3.2} height={3.2} r={0.9} color="#ffffff" />
            <RoundedRect x={-3.9} y={0.7} width={3.2} height={3.2} r={0.9} color="#ffffff" />
            <RoundedRect x={0.7} y={0.7} width={3.2} height={3.2} r={0.9} color="#ffffff" />
          </>
        )}
        {index === 2 && (
          <>
            <Circle cx={0} cy={0} r={3.9} style="stroke" strokeWidth={1.1} color="#ffffff" />
            <Circle cx={0} cy={0} r={1.4} color="#ffffff" />
          </>
        )}
        {index === 3 && (
          <>
            <Circle cx={0} cy={0} r={3.9} style="stroke" strokeWidth={1.1} color="#ffffff" />
            <Path path={GLYPH_HALF} color="#ffffff" />
          </>
        )}
      </Group>
    </Group>
  );
}

function Surface({ value, time, activity, lock, ambient }: Motion) {
  const fill = useDerivedValue(() => BAR_W * clamp01(value.get()));
  const head = useDerivedValue(() => BAR_X + fill.get());
  // Ticks just ahead of the bar light up, as if a probe were scanning them.
  const scan = useDerivedValue(() => ({ x: head.get(), y: BAR_Y - 1, width: SCAN_W, height: BAR_H + 2 }));
  const scanShade = useDerivedValue(() => [{ translateX: head.get() }]);
  const scanAlpha = useDerivedValue(() => 0.25 + 0.75 * activity.get());
  const headX = useDerivedValue(() => head.get() - 0.8);
  const headAlpha = useDerivedValue(() => activity.get() * clamp01(value.get() / 0.01));
  // Static crackles over the empty part of the scale, louder the higher the noise ratio.
  const crackle = useDerivedValue(() => {
    const frame = Math.floor(time.get() * 13);
    return [{ translateX: ((frame * 37.7) % 97) + BAR_X }];
  });
  const crackleAlpha = useDerivedValue(() => ambient * (0.35 + 0.65 * (1 - clamp01(value.get()))));
  const empty = useDerivedValue(() => ({ x: head.get(), y: BAR_Y - 1, width: BAR_W - fill.get(), height: BAR_H + 2 }));
  const sway = useDerivedValue(() => [
    { translateX: ambient * 9 * Math.sin(time.get() * 0.23) * Math.cos(BACKDROP_TILT) },
    { translateY: ambient * 9 * Math.sin(time.get() * 0.23) * Math.sin(BACKDROP_TILT) },
  ]);

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* Out-of-focus backdrop: soft streaks of sky, leaves and light. */}
        <Group transform={sway}>
          {BACKDROP.map((b, i) => (
            <Group key={i} transform={[{ translateX: b.x }, { translateY: b.y }, { rotate: BACKDROP_TILT }, { scaleX: b.l }, { scaleY: b.t }]}>
              <Circle cx={0} cy={0} r={1}>
                <RadialGradient c={vec(0, 0)} r={1} colors={[`rgba(${b.rgb},${b.alpha})`, `rgba(${b.rgb},0)`]} />
              </Circle>
            </Group>
          ))}
        </Group>

        <Rect x={2} y={8} width={WIDTH - 4} height={HEIGHT - 4} color="rgba(0,0,0,0.4)">
          <BlurMask blur={12} style="normal" />
        </Rect>
        <Rect x={0} y={0} width={WIDTH} height={HEIGHT} color="rgba(24,27,24,0.95)" />
        {/* A paler band where the backdrop shows through the glass. */}
        <Group clip={{ x: 0, y: 0, width: WIDTH, height: HEIGHT }}>
          <Group transform={[{ translateX: 96 }, { translateY: 60 }, { rotate: BACKDROP_TILT }, { scaleX: 190 }, { scaleY: 62 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={['rgba(150,150,120,0.17)', 'rgba(150,150,120,0)']} />
            </Circle>
          </Group>
        </Group>
        <Rect x={0.5} y={0.5} width={WIDTH - 1} height={HEIGHT - 1} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.09)" />
        {CORNERS.map((_, i) => (
          <Bracket key={i} index={i} lock={lock} />
        ))}

        <Rect x={0} y={HEADER_H} width={WIDTH} height={1} color="rgba(255,255,255,0.09)" />
        <Rect x={PAD} y={DIVIDER_Y} width={BAR_W} height={1} color="rgba(255,255,255,0.09)" />
        <Rect x={WIDTH - PAD - 40} y={10} width={40} height={16} color="rgba(150,148,50,0.3)" />
        <Rect x={WIDTH - PAD - 54} y={CHIP_Y - 0.5} width={54} height={17} color="rgba(150,148,50,0.34)" />

        {/* Scale: a faint track with ticks, static on its empty part, the lit ticks ahead of the bar. */}
        <Rect x={BAR_X} y={BAR_Y} width={BAR_W} height={BAR_H} color="rgba(255,255,255,0.05)" />
        <Path path={TICK_PATH} color="rgba(255,255,255,0.15)" />
        <Group clip={empty}>
          <Path path={TICK_PATH} opacity={crackleAlpha}>
            <LinearGradient start={vec(0, 0)} end={vec(97, 0)} colors={STATIC} mode="repeat" transform={crackle} />
          </Path>
        </Group>
        <Group clip={scan}>
          <Path path={TICK_PATH} opacity={scanAlpha}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(SCAN_W, 0)}
              colors={['rgba(246,239,76,0.95)', 'rgba(246,239,76,0)']}
              transform={scanShade}
            />
          </Path>
        </Group>

        <Rect x={BAR_X} y={BAR_Y + 1} width={fill} height={BAR_H} color="rgba(246,239,76,0.3)">
          <BlurMask blur={5} style="normal" />
        </Rect>
        <Rect x={BAR_X} y={BAR_Y} width={fill} height={BAR_H} color={YELLOW} />
        {/* The probe: a hot line at the head of the bar while it moves. */}
        <Rect x={headX} y={BAR_Y - 3.5} width={1.6} height={BAR_H + 7} color="rgba(255,252,170,0.9)" opacity={headAlpha}>
          <BlurMask blur={2.4} style="solid" />
        </Rect>

        {CHANNEL_AT.map((_, i) => (
          <Channel key={i} index={i} value={value} />
        ))}
      </Group>
    </Canvas>
  );
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** Text set from the UI thread, so a counting number costs no React render. */
function LiveText({ text, style }: { text: SharedValue<string>; style: TextStyle | TextStyle[] }) {
  const animatedProps = useAnimatedProps(() => ({ text: text.get(), defaultValue: text.get() }) as TextInputProps);
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessible={false}
      underlineColorAndroid="transparent"
      style={[styles.live, style]}
      animatedProps={animatedProps}
    />
  );
}

/**
 * Dark monospace readout over a blurred backdrop: signal strength and noise ratio in large
 * figures, a yellow bar on a ticked 0–100% scale and the active channel chips. The corner
 * brackets fly in and lock onto the panel, a probe scans the ticks ahead of the bar, static
 * crackles over the empty part of the scale, both figures count against each other and the
 * channels come online one by one as the signal climbs.
 */
export function SignalStrengthBar({
  progress,
  autoPlay = false,
  section = 'Threshold',
  title = 'Signal strength',
  signalLabel = 'SIGNAL STRENGTH',
  noiseLabel = 'NOISE RATIO',
  delta = '+32%',
  channelsLabel = 'ACTIVE CHANNELS',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
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
    const since = time.get() - movedAt.get();
    return clamp01(since / 0.2) * clamp01((1.1 - since) / 0.5);
  });
  const lock = useDerivedValue(() => (controlled || reducedMotion ? 1 : loopLock(clock.get())));

  const signal = useDerivedValue(() => percent(clamp01(value.get())));
  // The two readings always add up to 100.0.
  const noise = useDerivedValue(() => percent(1 - Math.round(clamp01(value.get()) * 1000) / 1000));
  const inputs = useDerivedValue(() => {
    let n = 0;
    for (const at of CHANNEL_AT) if (value.get() >= at + 0.015) n += 1;
    return `${n} ${n === 1 ? 'INPUT' : 'INPUTS'}`;
  });

  // The delta follows the end of the reading and the noise label its start, whatever their digit count.
  const deltaStyle = useAnimatedStyle(() => {
    const tenths = Math.round(clamp01(value.get()) * 1000);
    return { transform: [{ translateX: tenths < 100 ? -DIGIT_W : tenths >= 1000 ? DIGIT_W : 0 }] };
  });
  const noiseLabelStyle = useAnimatedStyle(() => {
    const tenths = 1000 - Math.round(clamp01(value.get()) * 1000);
    return { transform: [{ translateX: tenths < 100 ? DIGIT_W : tenths >= 1000 ? -DIGIT_W : 0 }] };
  });

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} activity={activity} lock={lock} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={title}
        accessibilityValue={{ min: 0, max: 100, now: Math.round(target * 100), text: `${(target * 100).toFixed(1)}%` }}
      >
        <Text style={styles.header} numberOfLines={1}>
          <Text style={styles.section}>{section}</Text>
          <Text style={styles.section}>{'  /  '}</Text>
          {title}
        </Text>
        <LiveText text={signal} style={[styles.mono, styles.headerChip]} />

        <Text style={[styles.mono, styles.label, styles.signalLabel]} numberOfLines={1}>
          {signalLabel}
        </Text>
        <Animated.Text style={[styles.mono, styles.label, styles.noiseLabel, noiseLabelStyle]} numberOfLines={1}>
          {noiseLabel}
        </Animated.Text>
        <LiveText text={signal} style={[styles.reading, styles.signal]} />
        <LiveText text={noise} style={[styles.reading, styles.noise]} />
        <Animated.Text style={[styles.mono, styles.delta, deltaStyle]} numberOfLines={1}>
          {delta}
        </Animated.Text>

        <View style={styles.scale}>
          {['0%', '25%', '50%', '75%', '100%'].map((mark) => (
            <Text key={mark} style={[styles.mono, styles.mark]}>
              {mark}
            </Text>
          ))}
        </View>

        <Text style={[styles.mono, styles.label, styles.channels]} numberOfLines={1}>
          {channelsLabel}
        </Text>
        <LiveText text={inputs} style={[styles.mono, styles.inputs]} />
      </View>
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
  live: {
    position: 'absolute',
    padding: 0,
    margin: 0,
  },
  mono: {
    fontFamily: MONO,
    fontSize: 8.6,
    lineHeight: 12,
    fontWeight: '400',
  },
  header: {
    position: 'absolute',
    left: PAD,
    top: HEADER_H / 2 - 8,
    color: '#ffffff',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
  },
  section: {
    color: 'rgba(255,255,255,0.42)',
  },
  headerChip: {
    right: PAD,
    top: 8,
    width: 40,
    height: 16,
    textAlign: 'center',
    color: '#ece65c',
  },
  label: {
    position: 'absolute',
    color: 'rgba(255,255,255,0.74)',
    letterSpacing: 0.5,
  },
  signalLabel: {
    left: PAD,
    top: 49,
  },
  noiseLabel: {
    left: WIDTH - PAD - READING_W,
    top: 49,
  },
  reading: {
    top: 66,
    width: 160,
    height: 38,
    color: '#ffffff',
    fontSize: 30,
    fontWeight: '500',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  signal: {
    left: PAD,
  },
  noise: {
    right: PAD,
    textAlign: 'right',
  },
  delta: {
    position: 'absolute',
    left: PAD + READING_W + 7,
    top: 86.5,
    color: '#65dc63',
  },
  scale: {
    position: 'absolute',
    left: PAD,
    right: PAD,
    top: 127,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  mark: {
    color: 'rgba(255,255,255,0.5)',
  },
  channels: {
    left: PAD,
    top: 166,
  },
  inputs: {
    right: PAD,
    top: CHIP_Y - 2.5,
    width: 54,
    height: 17,
    textAlign: 'center',
    color: '#f0ea58',
    letterSpacing: 0.4,
  },
});
