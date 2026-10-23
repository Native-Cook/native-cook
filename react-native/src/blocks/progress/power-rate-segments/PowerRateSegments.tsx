import {
  Blur,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  vec,
} from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Controlled power rate, 0…1. Leave undefined to show the reference's 24.31% (or the autoPlay loop). */
  progress?: number;
  /** Ramps from 0 to 24.31% by itself, holds, drops and starts over. */
  autoPlay?: boolean;
  label?: string;
  /** Text of the change chip. */
  change?: string;
  selectedRow?: string;
  settingsRow?: string;
};

const WIDTH = 308;
const HEIGHT = 300;
const RADIUS = 16;
// The canvas overflows the menu by this much on every side: the light streaks carry on outside it.
const BLEED = 46;

const INSET = 17.5;
const ROW_Y = 12;
const ROW_H = 48;
const DIVIDER_Y = 82;
const PANEL_Y = 109;
const PANEL_W = WIDTH - INSET * 2;
const PANEL_H = 115;
const PANEL_R = 10;
const SETTINGS_Y = 262;
const ICON_X = 37;

const SEGMENTS = 25;
const SEG_X = 30;
const SEG_Y = 194;
const SEG_W = 6;
const SEG_H = 17.5;
const SEG_PITCH = 9;
// The lit run ends inside a segment, so the leading one shows as a sliver until it is passed.
const SEG_LEAD = 0.75;

const DIGIT_W = 13.4;
const DIGIT_H = 28;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.2431;
const START = 0.4;
const FILL = 2.2;
const HOLD = 3.8;
const RESET = 0.6;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + FILL + 1;
const WAVE_PERIOD = 2.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
// The streaks rise to the right at this angle and fan out from the menu's bottom right corner.
const TILT = (-22 * Math.PI) / 180;

/**
 * Light streaks in the tilted frame: `a` along the streak from the corner (negative = left),
 * `d` above the corner, half length `l`, half thickness `t`, then how far and how fast it slides.
 */
const STREAKS = [
  { a: -120, d: 150, l: 210, t: 5, alpha: 0.3, amp: 30, speed: 0.23, phase: 0.0, mint: false },
  { a: -60, d: 128, l: 190, t: 2.2, alpha: 0.42, amp: 40, speed: 0.31, phase: 1.3, mint: true },
  { a: -150, d: 112, l: 240, t: 7, alpha: 0.34, amp: 26, speed: 0.19, phase: 2.1, mint: false },
  { a: -40, d: 96, l: 170, t: 1.6, alpha: 0.55, amp: 46, speed: 0.37, phase: 0.7, mint: true },
  { a: -100, d: 80, l: 230, t: 6, alpha: 0.42, amp: 30, speed: 0.21, phase: 3.0, mint: false },
  { a: -30, d: 66, l: 180, t: 2.4, alpha: 0.6, amp: 50, speed: 0.33, phase: 4.2, mint: true },
  { a: -140, d: 52, l: 250, t: 8, alpha: 0.45, amp: 24, speed: 0.17, phase: 5.0, mint: false },
  { a: -50, d: 40, l: 200, t: 1.8, alpha: 0.7, amp: 44, speed: 0.41, phase: 2.6, mint: true },
  { a: -90, d: 26, l: 240, t: 7, alpha: 0.5, amp: 28, speed: 0.2, phase: 1.1, mint: false },
  { a: -20, d: 14, l: 190, t: 2.2, alpha: 0.75, amp: 52, speed: 0.36, phase: 5.6, mint: true },
  { a: -120, d: 0, l: 260, t: 9, alpha: 0.5, amp: 22, speed: 0.16, phase: 3.7, mint: false },
  { a: -60, d: -14, l: 220, t: 2, alpha: 0.6, amp: 40, speed: 0.3, phase: 0.3, mint: true },
  { a: -100, d: -30, l: 260, t: 8, alpha: 0.4, amp: 26, speed: 0.18, phase: 4.6, mint: false },
  { a: -180, d: 176, l: 200, t: 3, alpha: 0.2, amp: 30, speed: 0.25, phase: 2.9, mint: true },
];

/** Motes drifting up the light: height above the corner, radius, speed (laps per second), phase. */
const MOTES = [
  { d: 34, r: 1.1, speed: 0.07, phase: 0.1 },
  { d: 58, r: 0.8, speed: 0.05, phase: 0.45 },
  { d: 12, r: 1.3, speed: 0.06, phase: 0.7 },
  { d: 84, r: 0.7, speed: 0.045, phase: 0.25 },
  { d: -8, r: 1, speed: 0.08, phase: 0.9 },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

// Long tail, so the last digits of the counter are seen rolling into place.
function easeOutQuart(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 4);
}

/** Power rate at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + FILL) return REST * easeOutQuart((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** Where digit `place` of the counter sits (0…10), rolling over like a mechanical odometer. */
function digitPosition(value: number, place: number) {
  'worklet';
  // Hundredths of a percent, snapped so 24.31 does not come out as 24.3099….
  const y = Math.round(value * 1e6) / 100 / Math.pow(10, place);
  const base = Math.floor(y);
  const frac = y - base;
  // The last wheel turns freely; the others only move while the wheel below passes from 9 to 0.
  const roll = place === 0 ? frac : Math.max(0, (frac - 0.9) * 10);
  return (base % 10) + roll;
}

const MENU_FILL = `M0 0V${HEIGHT - RADIUS}A${RADIUS} ${RADIUS} 0 0 0 ${RADIUS} ${HEIGHT}H${WIDTH - RADIUS}A${RADIUS} ${RADIUS} 0 0 0 ${WIDTH} ${HEIGHT - RADIUS}V0Z`;
const MENU_EDGE = `M0.5 0V${HEIGHT - RADIUS}A${RADIUS - 0.5} ${RADIUS - 0.5} 0 0 0 ${RADIUS} ${HEIGHT - 0.5}H${WIDTH - RADIUS}A${RADIUS - 0.5} ${RADIUS - 0.5} 0 0 0 ${WIDTH - 0.5} ${HEIGHT - RADIUS}V0`;

const SEGMENT_PATH = Array.from({ length: SEGMENTS }, (_, i) => {
  const x = SEG_X + i * SEG_PITCH;
  const r = 1.6;
  return (
    `M${x + r} ${SEG_Y}H${x + SEG_W - r}Q${x + SEG_W} ${SEG_Y} ${x + SEG_W} ${SEG_Y + r}` +
    `V${SEG_Y + SEG_H - r}Q${x + SEG_W} ${SEG_Y + SEG_H} ${x + SEG_W - r} ${SEG_Y + SEG_H}` +
    `H${x + r}Q${x} ${SEG_Y + SEG_H} ${x} ${SEG_Y + SEG_H - r}V${SEG_Y + r}Q${x} ${SEG_Y} ${x + r} ${SEG_Y}Z`
  );
}).join('');

// Plug in a ring, a cog and a generic chip, each drawn around its own centre.
const PLUG = 'M-2.2 -4.6V-2M2.2 -4.6V-2M-3.7 -2H3.7V0.2A3.7 3.7 0 0 1 -3.7 0.2ZM0 3.9V6.2';
const COG = (() => {
  const teeth = 8;
  let d = '';
  for (let i = 0; i < teeth; i++) {
    const a = (i / teeth) * Math.PI * 2;
    const step = (Math.PI * 2) / teeth;
    const points: [number, number][] = [
      [a - step * 0.14, 8.2],
      [a + step * 0.14, 8.2],
      [a + step * 0.3, 6.2],
      [a + step * 0.7, 6.2],
    ];
    for (const [angle, radius] of points) {
      d += `${d ? 'L' : 'M'}${(radius * Math.cos(angle)).toFixed(2)} ${(radius * Math.sin(angle)).toFixed(2)}`;
    }
  }
  return `${d}Z`;
})();
const CHIP_PINS = 'M-10 -4H-7M-10 0H-7M-10 4H-7M7 -4H10M7 0H10M7 4H10';

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0 with reduced motion: the light stands still. */
  ambient: number;
};

function Streak({ index, time, ambient }: { index: number; time: SharedValue<number>; ambient: number }) {
  const s = STREAKS[index];
  const rgb = s.mint ? '150,255,205' : '26,224,116';
  const transform = useDerivedValue(() => [
    { translateX: s.a + ambient * s.amp * Math.sin(time.get() * s.speed * 2 + s.phase) },
    { translateY: -s.d },
    { scaleX: s.l },
    { scaleY: s.t },
  ]);
  const opacity = useDerivedValue(
    () => Math.min(1, s.alpha * 1.3) * (0.72 + ambient * 0.28 * Math.sin(time.get() * s.speed * 5.3 + s.phase * 1.7)),
  );
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={1} opacity={opacity}>
        <RadialGradient c={vec(0, 0)} r={1} colors={[`rgba(${rgb},1)`, `rgba(${rgb},0)`]} />
      </Circle>
    </Group>
  );
}

function Mote({ index, time }: { index: number; time: SharedValue<number> }) {
  const m = MOTES[index];
  const lap = useDerivedValue(() => (time.get() * m.speed + m.phase) % 1);
  const cx = useDerivedValue(() => -300 + 330 * lap.get());
  const opacity = useDerivedValue(
    () => Math.sin(Math.PI * lap.get()) * (0.55 + 0.45 * Math.sin(time.get() * 3.1 + index * 1.9)),
  );
  return <Circle cx={cx} cy={-m.d} r={m.r} color="#c4ffe4" opacity={opacity} />;
}

function Surface({ value, time, ambient }: Motion) {
  // The light swells with the power rate and breathes a little.
  const gain = useDerivedValue(
    () => 0.5 + 0.42 * clamp01(value.get() / REST) + ambient * 0.08 * Math.sin(time.get() * 1.3),
  );
  const lit = useDerivedValue(() => {
    const width = Math.max(0, clamp01(value.get()) * SEGMENTS - SEG_LEAD) * SEG_PITCH;
    return { x: SEG_X - 8, y: SEG_Y - 8, width: width + 8, height: SEG_H + 16 };
  });
  // Mains flicker on the lit run, and a pulse that runs along it every few seconds.
  const glow = useDerivedValue(
    () => 0.82 + ambient * 0.18 * Math.sin(time.get() * 21) * Math.sin(time.get() * 6.7),
  );
  const wave = useDerivedValue(() => {
    const phase = (time.get() % WAVE_PERIOD) / 0.9;
    return [{ translateX: SEG_X - 30 + (SEGMENTS * SEG_PITCH + 30) * Math.min(phase, 1) }];
  });
  const waveAlpha = useDerivedValue(() => ((time.get() % WAVE_PERIOD) / 0.9 < 1 ? ambient : 0));
  const cursor = useDerivedValue(() => 0.65 + ambient * 0.35 * Math.sin(time.get() * 4.2));

  const px = INSET;
  const py = PANEL_Y;

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* Light fanning out from the bottom right corner. */}
        <Group transform={[{ translateX: WIDTH }, { translateY: HEIGHT }, { rotate: TILT }]} opacity={gain}>
          <Group transform={[{ translateX: -50 }, { translateY: -24 }, { scaleX: 330 }, { scaleY: 118 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={['rgba(22,214,110,0.85)', 'rgba(22,214,110,0.34)', 'rgba(22,214,110,0)']} />
            </Circle>
          </Group>
          <Group transform={[{ translateX: 10 }, { scaleX: 190 }, { scaleY: 74 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={['rgba(44,246,172,0.95)', 'rgba(44,246,172,0.32)', 'rgba(44,246,172,0)']} />
            </Circle>
          </Group>
          {STREAKS.map((_, i) => (
            <Streak key={i} index={i} time={time} ambient={ambient} />
          ))}
          {ambient > 0 && MOTES.map((_, i) => <Mote key={i} index={i} time={time} />)}
        </Group>

        {/* Outside the glass the light is much dimmer. */}
        <Group clip={MENU_FILL} invertClip>
          <Rect x={-BLEED} y={-BLEED} width={WIDTH + BLEED * 2} height={HEIGHT + BLEED * 2} color="rgba(2,5,4,0.74)" />
        </Group>

        {/* Menu glass: open at the top, where the rest of the menu would continue. */}
        <Path path={MENU_FILL}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, HEIGHT)}
            colors={['rgba(13,15,14,0)', 'rgba(13,15,14,0.5)', 'rgba(13,15,14,0.14)']}
            positions={[0, 0.07, 1]}
          />
        </Path>
        <Path path={MENU_EDGE} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(WIDTH, HEIGHT)}
            colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.11)', 'rgba(190,255,225,0.2)', 'rgba(150,255,210,0.75)']}
            positions={[0, 0.12, 0.6, 1]}
          />
        </Path>

        {/* Selected row and divider. */}
        <RoundedRect x={INSET} y={ROW_Y} width={PANEL_W} height={ROW_H} r={11} color="rgba(255,255,255,0.055)" />
        <Rect x={INSET} y={DIVIDER_Y} width={PANEL_W} height={1} color="rgba(255,255,255,0.1)" />
        <Group transform={[{ translateX: ICON_X }, { translateY: ROW_Y + ROW_H / 2 }]}>
          <Circle cx={0} cy={0} r={8.6} style="stroke" strokeWidth={1.5} color="#2fd46d" />
          <Path path={PLUG} style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#2fd46d" />
        </Group>
        <Group transform={[{ translateX: ICON_X }, { translateY: SETTINGS_Y }]}>
          <Path path={COG} style="stroke" strokeWidth={1.5} strokeJoin="round" color="rgba(255,255,255,0.62)" />
          <Circle cx={0} cy={0} r={2.6} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.62)" />
        </Group>

        {/* Power panel: dark glass on the left, lit green on the right. */}
        <RoundedRect x={px} y={py} width={PANEL_W} height={PANEL_H} r={PANEL_R}>
          <LinearGradient
            start={vec(px, py)}
            end={vec(px + PANEL_W, py + PANEL_H * 0.6)}
            colors={['rgba(27,33,31,0.82)', 'rgba(24,52,39,0.55)', 'rgba(40,170,105,0.2)']}
            positions={[0, 0.45, 1]}
          />
        </RoundedRect>
        <RoundedRect x={px + 0.5} y={py + 0.5} width={PANEL_W - 1} height={PANEL_H - 1} r={PANEL_R - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(px, py)}
            end={vec(px + PANEL_W, py + PANEL_H)}
            colors={['rgba(255,255,255,0.14)', 'rgba(170,255,215,0.22)', 'rgba(150,255,205,0.7)']}
            positions={[0, 0.55, 1]}
          />
        </RoundedRect>

        {/* Chip glyph. */}
        <Group transform={[{ translateX: 41 }, { translateY: py + 28.5 }]}>
          <Path path={CHIP_PINS} style="stroke" strokeWidth={1.5} strokeCap="round" color="#2fd46d" />
          <RoundedRect x={-7} y={-7} width={14} height={14} r={3} color="#2fd46d" />
          <RoundedRect x={-2.8} y={-2.8} width={5.6} height={5.6} r={1.2} color="#143524" />
        </Group>

        {/* Change chip with its cursor bar. */}
        <Rect x={213} y={py + 13.5} width={61} height={16} color="rgba(52,214,122,0.16)" />
        <Rect x={276.4} y={py + 13.5} width={1.5} height={16} color="#3fe087" opacity={cursor} />

        {/* Segments: the dim row fades out to the right, the lit run glows. */}
        <Path path={SEGMENT_PATH}>
          <LinearGradient
            start={vec(SEG_X, 0)}
            end={vec(SEG_X + SEGMENTS * SEG_PITCH, 0)}
            colors={['rgba(70,190,120,0.4)', 'rgba(70,190,120,0.2)', 'rgba(70,190,120,0)']}
            positions={[0, 0.5, 0.95]}
          />
        </Path>
        <Group
          layer={
            <Paint opacity={glow}>
              <Blur blur={3.6} />
            </Paint>
          }
        >
          <Group clip={lit}>
            <Path path={SEGMENT_PATH} color="#2ff48a" />
          </Group>
        </Group>
        <Group clip={lit}>
          <Path path={SEGMENT_PATH}>
            <LinearGradient
              start={vec(0, SEG_Y)}
              end={vec(0, SEG_Y + SEG_H)}
              colors={['#7dffb6', '#e2ffef', '#7dffb6']}
            />
          </Path>
          <Path path={SEGMENT_PATH} opacity={waveAlpha}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(30, 0)}
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.95)', 'rgba(255,255,255,0)']}
              transform={wave}
            />
          </Path>
        </Group>
      </Group>
    </Canvas>
  );
}

const WHEEL = Array.from({ length: 11 }, (_, i) => i % 10);

function Digit({ place, value, optional }: { place: number; value: SharedValue<number>; optional?: boolean }) {
  // Hundreds and tens leave the row while they would only show a leading zero.
  const slot = useAnimatedStyle(() => {
    if (!optional) return { width: DIGIT_W, opacity: 1 };
    const shown = clamp01((value.get() * 100 - Math.pow(10, place - 2) * 0.96) / (Math.pow(10, place - 2) * 0.04));
    return { width: DIGIT_W * shown, opacity: shown };
  });
  const wheel = useAnimatedStyle(() => ({
    transform: [{ translateY: -DIGIT_H * digitPosition(clamp01(value.get()), place) }],
  }));
  return (
    <Animated.View style={[styles.slot, slot]}>
      <Animated.View style={wheel}>
        {WHEEL.map((digit, i) => (
          <Text key={i} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/**
 * Dark glass settings menu with a glowing green panel: a power rate counter, a change chip and a
 * row of small segments, over streaks of green light. The digits roll like an odometer, the
 * segments light one after another with a flickering glow and a pulse that runs along them, and
 * the light streaks slide, swell with the rate and carry a few drifting motes.
 */
export function PowerRateSegments({
  progress,
  autoPlay = false,
  label = 'GPU Actual Power Rate',
  change = '-14.83%',
  selectedRow = 'Power Usage',
  settingsRow = 'Settings',
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

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 900, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

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

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Text style={[styles.row, styles.rowSelected, { top: ROW_Y + ROW_H / 2 - 11 }]} numberOfLines={1}>
          {selectedRow}
        </Text>
        <Text style={[styles.row, { top: SETTINGS_Y - 11 }]} numberOfLines={1}>
          {settingsRow}
        </Text>

        <View
          style={styles.counter}
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={label}
          accessibilityValue={{ min: 0, max: 100, now: Math.round(target * 100), text: `${(target * 100).toFixed(2)}%` }}
        >
          <Digit place={4} value={value} optional />
          <Digit place={3} value={value} optional />
          <Digit place={2} value={value} />
          <Text style={styles.digitMark}>.</Text>
          <Digit place={1} value={value} />
          <Digit place={0} value={value} />
          <Text style={styles.digitMark}>%</Text>
        </View>
        <Text style={styles.change} numberOfLines={1}>
          {change}
        </Text>
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
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
  row: {
    position: 'absolute',
    left: 57,
    color: 'rgba(255,255,255,0.56)',
    fontSize: 15.5,
    lineHeight: 22,
    fontWeight: '400',
    letterSpacing: 0.2,
  },
  rowSelected: {
    color: '#ffffff',
    fontWeight: '500',
  },
  counter: {
    position: 'absolute',
    left: 61,
    top: PANEL_Y + 28.5 - DIGIT_H / 2,
    height: DIGIT_H,
    flexDirection: 'row',
  },
  slot: {
    height: DIGIT_H,
    overflow: 'hidden',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 22,
    lineHeight: DIGIT_H,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  digitMark: {
    color: '#ffffff',
    fontSize: 22,
    lineHeight: DIGIT_H,
    fontWeight: '500',
  },
  change: {
    position: 'absolute',
    left: 213,
    top: PANEL_Y + 13.5,
    width: 61,
    textAlign: 'center',
    color: '#3bd583',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '400',
    fontVariant: ['tabular-nums'],
  },
  label: {
    position: 'absolute',
    left: 30.5,
    top: PANEL_Y + 62.5 - 10,
    color: 'rgba(232,244,238,0.5)',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '400',
    letterSpacing: 0.2,
  },
});
