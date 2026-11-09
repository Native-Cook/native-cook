import { Canvas, Circle, Group, Path, PathOp, Skia, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

// MARK: Timing

/** One day in the self-playing loop: Morning → Day → Evening → Night → Morning, 2 s per band. */
export const LOOP = 8;
/** Seconds each band owns in the loop. */
const PHASE = 2;
/** Every phase rests this long with its band open before handing over to the next band. */
const HOLD = 1.35;
/** The spring hand-off to the next band; it has settled to within 0.1 % by then. */
const MOVE = 0.65;
/** The layout spring: SwiftUI .spring(response: 0.45, dampingFraction: 0.78). */
const RESPONSE = 0.45;
const DAMPING = 0.78;
const OMEGA = (2 * Math.PI) / RESPONSE;
const SPRING = { mass: 1, stiffness: OMEGA * OMEGA, damping: 2 * DAMPING * OMEGA };
/** Seconds after the last touch before the loop resumes from the open band. */
const IDLE = 2;
/** Reduced motion: the open band's content fades out, the bands swap, the content fades back in. */
const FADE_OUT_MS = 110;
const FADE_IN_MS = 190;
/** Icon clock shown under reduced motion: a still frame with rain and snow visible. */
const STILL_CLOCK = 0.45;
const TAU = Math.PI * 2;

// MARK: Layout (pt)

const HEADER_H = 64;
const ROW_H = 72;
const MARGIN = 24;
const ICON = 160;
const ICON_LEFT = 14;
const COLUMN_LEFT = 184;
const CAST_H = 14;

// MARK: Content

type Band = {
  label: string;
  spoken: string;
  hours: string;
  temp: string;
  feels: string;
  condition: string;
  note: string;
  stats: [string, string][];
  colors: [string, string, string];
  ink: string;
};

const BANDS: Band[] = [
  {
    label: 'MORNING',
    spoken: 'Morning, minus 1 degree, sunny',
    hours: '6–11 AM',
    temp: '−1°',
    feels: 'Feels −4°',
    condition: 'Sunny',
    note: 'Clear and crisp until noon',
    stats: [['WIND', 'E 7 mph'], ['HUMIDITY', '91%'], ['PRECIP', '0%']],
    colors: ['#EDC897', '#E3BB88', '#D9AB79'],
    ink: '#5A2F1F',
  },
  {
    label: 'DAY',
    spoken: 'Day, plus 3 degrees, partly cloudy',
    hours: '11 AM–5 PM',
    temp: '+3°',
    feels: 'Feels +1°',
    condition: 'Partly Cloudy',
    note: 'Clouds drift in after lunch',
    stats: [['WIND', 'N 5 mph'], ['HUMIDITY', '45%'], ['PRECIP', '10%']],
    colors: ['#E2A472', '#DB9864', '#CE8858'],
    ink: '#47210F',
  },
  {
    label: 'EVENING',
    spoken: 'Evening, plus 1 degree, light rain',
    hours: '5–10 PM',
    temp: '+1°',
    feels: 'Feels −2°',
    condition: 'Light Rain',
    note: 'Showers ease off by nine',
    stats: [['WIND', 'W 12 mph'], ['HUMIDITY', '88%'], ['PRECIP', '70%']],
    colors: ['#BA7465', '#B1695A', '#A15D52'],
    ink: '#FFF2E6',
  },
  {
    label: 'NIGHT',
    spoken: 'Night, minus 2 degrees, light snow',
    hours: '10 PM–6 AM',
    temp: '−2°',
    feels: 'Feels −6°',
    condition: 'Light Snow',
    note: 'Flurries settle before dawn',
    stats: [['WIND', 'NW 9 mph'], ['HUMIDITY', '84%'], ['PRECIP', '60%']],
    colors: ['#6B4D51', '#5F4346', '#4E363B'],
    ink: '#F7E9DD',
  },
];

const HEADER_COLORS = ['#94B19B', '#8BA892', '#83A08A'] as const;
const HEADER_INK = '#FBF6EE';

// Icon palette.
const SUN_MORNING = '#E57A3A';
const SUN_GLOW = 'rgba(255, 243, 222, 0.45)';
const SUN_DAY = '#FFE3B3';
const SUN_DAY_GLOW = 'rgba(255, 236, 200, 0.32)';
const CLOUD_DARK = '#5C3534';
const CLOUD_DARK_RIM = '#704543';
const SUN_DUSK = '#7A3A30';
const CLOUD_CREAM = '#F6D5AE';
const CLOUD_CREAM_RIM = '#FFE8CB';
const RAIN = '#F6D5AE';
const MOON = '#F2A86C';
const MOON_GLOW = 'rgba(242, 168, 108, 0.18)';
const CLOUD_NIGHT = '#EBD0B0';
const CLOUD_NIGHT_RIM = '#F7E3CB';
const SNOW = '#F6DEC4';

// MARK: Pure helpers (declared above every worklet that calls them)

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/** 0 before a, 1 after b, linear between. */
function seg(v: number, a: number, b: number) {
  'worklet';
  return clamp01((v - a) / (b - a));
}

function easeOutCubic(v: number) {
  'worklet';
  const u = 1 - v;
  return 1 - u * u * u;
}

function wrap(t: number) {
  'worklet';
  const r = t % LOOP;
  return r < 0 ? r + LOOP : r;
}

function fract(v: number) {
  'worklet';
  return v - Math.floor(v);
}

/** Seeded per-particle offset in [0, 1). */
function hash(i: number) {
  'worklet';
  return fract(Math.sin(i * 12.9898 + 1.7) * 43758.5453);
}

/** Step response of the layout spring (response 0.45 s, damping ratio 0.78; about 2 % overshoot). */
function springStep(s: number) {
  'worklet';
  const wd = OMEGA * Math.sqrt(1 - DAMPING * DAMPING);
  return 1 - Math.exp(-DAMPING * OMEGA * s) * (Math.cos(wd * s) + ((DAMPING * OMEGA) / wd) * Math.sin(wd * s));
}

/** Hand-off progress 0 → 1 over MOVE seconds on the spring, corrected to land exactly on 1. */
function handoff(s: number) {
  'worklet';
  if (s <= 0) return 0;
  if (s >= MOVE) return 1;
  return springStep(s) + (1 - springStep(MOVE)) * (s / MOVE);
}

/** Openness of each band at loop time t: one band open, or two sharing the hand-off. */
function autoWeights(t: number) {
  'worklet';
  const k = Math.floor(t / PHASE) % 4;
  const p = handoff(t - k * PHASE - HOLD);
  const w = [0, 0, 0, 0];
  w[k] = 1 - p;
  w[(k + 1) % 4] += p;
  return w;
}

/** Openness when a finger holds the day at focus f ∈ [0, 3]: the two nearest bands share it. */
function tentWeights(f: number) {
  'worklet';
  const w = [0, 0, 0, 0];
  for (let i = 0; i < 4; i += 1) w[i] = Math.max(0, 1 - Math.abs(f - i));
  return w;
}

/** Past the first or last band the finger meets a rubber band. */
function rubber(f: number) {
  'worklet';
  if (f < 0) {
    const over = -f;
    return -(over * 0.55) / (1 + over * 1.2);
  }
  if (f > 3) {
    const over = f - 3;
    return 3 + (over * 0.55) / (1 + over * 1.2);
  }
  return f;
}

// MARK: Static artwork

let cloudShape: SkPath | null = null;
/** One puffy cloud centred on (0, 0), about 96 × 60 pt: three puffs over a rounded base. */
function getCloud() {
  if (cloudShape) return cloudShape;
  const b = Skia.PathBuilder.Make();
  b.addRRect(Skia.RRectXY(Skia.XYWHRect(-48, 2, 96, 26), 13, 13));
  b.addCircle(-24, 6, 20);
  b.addCircle(4, -6, 26);
  b.addCircle(30, 8, 17);
  cloudShape = b.detach();
  return cloudShape;
}

let moonShape: SkPath | null = null;
/** A crescent: a 26 pt disc with a 22 pt bite taken from its upper right. */
function getMoon() {
  if (moonShape) return moonShape;
  const disc = Skia.Path.Make();
  disc.addCircle(62, 58, 26);
  const bite = Skia.Path.Make();
  bite.addCircle(75, 47, 22);
  moonShape = Skia.Path.MakeFromOp(disc, bite, PathOp.Difference) ?? disc;
  return moonShape;
}

let dropShape: SkPath | null = null;
/** A rain streak, slanted by the west wind. */
function getDrop() {
  if (dropShape) return dropShape;
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, 0);
  b.lineTo(-2, 8);
  dropShape = b.detach();
  return dropShape;
}

let flakeShape: SkPath | null = null;
/** A six-armed snowflake, 9 pt across. */
function getFlake() {
  if (flakeShape) return flakeShape;
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < 3; i += 1) {
    const a = (i * Math.PI) / 3 + Math.PI / 2;
    b.moveTo(Math.cos(a) * 4.5, Math.sin(a) * 4.5);
    b.lineTo(-Math.cos(a) * 4.5, -Math.sin(a) * 4.5);
  }
  flakeShape = b.detach();
  return flakeShape;
}

let pinShape: SkPath | null = null;
/** The header's location pin, 14 × 18 pt. */
function getPin() {
  if (pinShape) return pinShape;
  const b = Skia.PathBuilder.Make();
  b.moveTo(7, 18);
  b.cubicTo(7, 18, 0, 10.5, 0, 7);
  b.cubicTo(0, 3.1, 3.1, 0, 7, 0);
  b.cubicTo(10.9, 0, 14, 3.1, 14, 7);
  b.cubicTo(14, 10.5, 7, 18, 7, 18);
  b.close();
  b.addCircle(7, 7, 2.6, true);
  pinShape = b.detach();
  return pinShape;
}

/** Rays around (cx, cy): `count` round-capped strokes from r1, each breathing its own length. */
function rayPath(cx: number, cy: number, count: number, r1: number, len: number, wobble: number, turn: number, c: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < count; i += 1) {
    const a = turn + (i * TAU) / count - Math.PI / 2;
    const l = len + wobble * Math.sin((TAU * 2 * c) / LOOP + (i * TAU * 2) / count);
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    b.moveTo(cx + ca * r1, cy + sa * r1);
    b.lineTo(cx + ca * (r1 + l), cy + sa * (r1 + l));
  }
  return b.detach();
}

// MARK: Icons (160 × 160 pt canvases; c is the icon clock, 0 ..< LOOP)

type Clock = SharedValue<number>;

/** A falling particle: `period` s per fall from y0 over `fall` pt; fades in over 15 %, out over the last 40 %. */
function Particle({
  index,
  clock,
  x,
  y0,
  fall,
  period,
  drift,
  sway,
  spin,
  path,
  color,
  width,
}: {
  index: number;
  clock: Clock;
  x: number;
  y0: number;
  fall: number;
  period: number;
  drift: number;
  sway: number;
  spin: number;
  path: SkPath;
  color: string;
  width: number;
}) {
  const transform = useDerivedValue(() => {
    const h = hash(index);
    const q = fract(clock.get() / period + h);
    return [
      { translateX: x + drift * q + sway * Math.sin(TAU * (q + h)) },
      { translateY: y0 + fall * q },
      { rotate: spin * q },
    ];
  });
  const opacity = useDerivedValue(() => {
    const q = fract(clock.get() / period + hash(index));
    return seg(q, 0, 0.15) * (1 - seg(q, 0.6, 1));
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={path} color={color} style="stroke" strokeWidth={width} strokeCap="round" />
    </Group>
  );
}

/** A cloud with a lighter rim along its top edge. */
function Cloud({ transform, body, rim }: { transform: SharedValue<{ translateX: number }[] | object[]>; body: string; rim: string }) {
  const cloud = getCloud();
  return (
    <Group transform={transform as never}>
      <Path path={cloud} color={rim} />
      <Group transform={[{ translateY: 3 }]}>
        <Path path={cloud} color={body} />
      </Group>
    </Group>
  );
}

/** Morning: a sun whose ten rays turn two ray-spacings a loop and breathe in a travelling wave. */
function SunIcon({ clock }: { clock: Clock }) {
  const rays = useDerivedValue(() => {
    const c = clock.get();
    return rayPath(80, 80, 10, 38, 8, 3, (c / LOOP) * ((2 * TAU) / 10), c);
  });
  const glow = useDerivedValue(() => 52 + 3 * Math.sin((TAU * 2 * clock.get()) / LOOP));
  return (
    <Canvas style={styles.icon}>
      <Circle cx={80} cy={80} r={glow} color={SUN_GLOW} />
      <Circle cx={80} cy={80} r={29} color={SUN_MORNING} style="stroke" strokeWidth={4.5} />
      <Circle cx={80} cy={80} r={21} color={SUN_MORNING} />
      <Path path={rays} color={SUN_MORNING} style="stroke" strokeWidth={6} strokeCap="round" />
    </Canvas>
  );
}

/** Day: a pale sun behind a dark cloud that drifts across it and back once a loop. */
function PartlyCloudyIcon({ clock }: { clock: Clock }) {
  const rays = useDerivedValue(() => {
    const c = clock.get();
    return rayPath(60, 56, 8, 26, 6, 2, (c / LOOP) * (TAU / 8), c);
  });
  const cloud = useDerivedValue(() => {
    const c = clock.get();
    return [
      { translateX: 90 + 12 * Math.sin((TAU * c) / LOOP) },
      { translateY: 98 + 2 * Math.sin((TAU * 2 * c) / LOOP) },
    ];
  });
  // The glow brightens as the cloud drifts off the sun.
  const glow = useDerivedValue(() => 0.55 + 0.45 * (0.5 + 0.5 * Math.sin((TAU * clock.get()) / LOOP)));
  return (
    <Canvas style={styles.icon}>
      <Group opacity={glow}>
        <Circle cx={60} cy={56} r={38} color={SUN_DAY_GLOW} />
      </Group>
      <Path path={rays} color={SUN_DAY} style="stroke" strokeWidth={4.5} strokeCap="round" />
      <Circle cx={60} cy={56} r={18} color={SUN_DAY} />
      <Cloud transform={cloud as never} body={CLOUD_DARK} rim={CLOUD_DARK_RIM} />
    </Canvas>
  );
}

const RAIN_X = [58, 73, 88, 103, 118, 133];

/** Evening: a low dusk sun behind a cream cloud, six streaks of rain falling once a second. */
function RainIcon({ clock }: { clock: Clock }) {
  const rays = useDerivedValue(() => {
    const c = clock.get();
    return rayPath(50, 50, 7, 22, 5, 1.5, (c / LOOP) * (TAU / 7), c);
  });
  const cloud = useDerivedValue(() => [
    { translateX: 88 },
    { translateY: 80 + 2 * Math.sin((TAU * 2 * clock.get()) / LOOP) },
  ]);
  const drop = getDrop();
  return (
    <Canvas style={styles.icon}>
      <Path path={rays} color={SUN_DUSK} style="stroke" strokeWidth={4} strokeCap="round" />
      <Circle cx={50} cy={50} r={15} color={SUN_DUSK} />
      {RAIN_X.map((x, i) => (
        <Particle
          key={i}
          index={i}
          clock={clock}
          x={x - 4}
          y0={112}
          fall={38}
          period={1}
          drift={-6}
          sway={0}
          spin={0}
          path={drop}
          color={RAIN}
          width={4}
        />
      ))}
      <Cloud transform={cloud as never} body={CLOUD_CREAM} rim={CLOUD_CREAM_RIM} />
    </Canvas>
  );
}

const SNOW_X = [54, 68, 82, 96, 110, 124, 140];

/** Night: a crescent moon rocking 8°, a small cloud passing and snowflakes drifting down every 2 s. */
function SnowIcon({ clock }: { clock: Clock }) {
  const moon = useDerivedValue(() => [
    { translateX: 62 },
    { translateY: 58 },
    { rotate: (8 * Math.PI) / 180 * Math.sin((TAU * clock.get()) / LOOP) },
    { translateX: -62 },
    { translateY: -58 },
  ]);
  const cloud = useDerivedValue(() => [
    { translateX: 102 + 6 * Math.sin((TAU * clock.get()) / LOOP + Math.PI / 2) },
    { translateY: 88 },
    { scale: 0.78 },
  ]);
  const flake = getFlake();
  return (
    <Canvas style={styles.icon}>
      <Circle cx={62} cy={58} r={40} color={MOON_GLOW} />
      <Group transform={moon}>
        <Path path={getMoon()} color={MOON} />
      </Group>
      {SNOW_X.map((x, i) => (
        <Particle
          key={i}
          index={i + 11}
          clock={clock}
          x={x}
          y0={106}
          fall={46}
          period={2}
          drift={0}
          sway={4}
          spin={Math.PI / 3}
          path={flake}
          color={SNOW}
          width={1.8}
        />
      ))}
      <Cloud transform={cloud as never} body={CLOUD_NIGHT} rim={CLOUD_NIGHT_RIM} />
    </Canvas>
  );
}

function BandIcon({ index, clock }: { index: number; clock: Clock }) {
  if (index === 0) return <SunIcon clock={clock} />;
  if (index === 1) return <PartlyCloudyIcon clock={clock} />;
  if (index === 2) return <RainIcon clock={clock} />;
  return <SnowIcon clock={clock} />;
}

// MARK: Band

/** One reveal step: fades in and rises `rise` pt while the band's openness w goes from a to b. */
function useReveal(w: SharedValue<number>, alpha: SharedValue<number>, a: number, b: number, rise: number, grow = 0) {
  return useAnimatedStyle(() => {
    const e = easeOutCubic(seg(w.get(), a, b));
    const transform =
      grow > 0 ? [{ translateY: rise * (1 - e) }, { scale: 1 - grow + grow * e }] : [{ translateY: rise * (1 - e) }];
    return { opacity: e * alpha.get(), transform };
  });
}

function BandView({
  index,
  band,
  w,
  tops,
  alpha,
  clock,
  width,
  fullHeight,
  openHeight,
  onSelect,
}: {
  index: number;
  band: Band;
  w: SharedValue<number>;
  tops: SharedValue<number[]>;
  alpha: SharedValue<number>;
  clock: Clock;
  width: number;
  fullHeight: number;
  openHeight: number;
  onSelect: (index: number) => void;
}) {
  const place = useAnimatedStyle(() => ({ transform: [{ translateY: tops.get()[index] }] }));
  const icon = useReveal(w, alpha, 0.15, 0.8, 18, 0.28);
  const title = useReveal(w, alpha, 0.35, 0.9, 10);
  const note = useReveal(w, alpha, 0.42, 0.95, 10);
  const stats = useReveal(w, alpha, 0.5, 1, 8);
  const ink = band.ink;
  const soft = { color: ink, opacity: 0.74 };
  const hairTop = Math.max(ROW_H + 200, openHeight - 100);
  // The icon and its words sit centred in the room between the row and the stats.
  const detailTop = ROW_H + Math.max(4, Math.round((hairTop - ROW_H - ICON) / 2));
  const columnWidth = (width - 2 * MARGIN) / 3;

  return (
    <Animated.View
      style={[styles.band, { height: fullHeight, backgroundColor: band.colors[2] }, place]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={band.spoken}
      onAccessibilityTap={() => onSelect(index)}
    >
      {index > 0 ? (
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(46, 20, 14, 0)', 'rgba(46, 20, 14, 0.16)']}
          style={styles.cast}
        />
      ) : null}
      <LinearGradient
        pointerEvents="none"
        colors={band.colors}
        locations={[0, 0.45, 1]}
        style={[styles.fill, { height: Math.min(openHeight, fullHeight) }]}
      />
      {index > 0 ? <View pointerEvents="none" style={styles.edge} /> : null}

      {/* The row every band always shows */}
      <Text style={[styles.label, { color: ink }]}>{band.label}</Text>
      <Text style={[styles.temp, { color: ink }]}>{band.temp}</Text>
      <Text style={[styles.hours, soft]}>{band.hours}</Text>
      <Text style={[styles.feels, { color: ink, opacity: 0.84 }]}>{band.feels}</Text>

      {/* What opening the band reveals */}
      <Animated.View pointerEvents="none" style={[styles.iconBox, { top: detailTop }, icon]}>
        <BandIcon index={index} clock={clock} />
      </Animated.View>
      <Animated.Text style={[styles.condition, { color: ink, top: detailTop + 42, width: width - COLUMN_LEFT - 20 }, title]}>
        {band.condition}
      </Animated.Text>
      <Animated.Text
        numberOfLines={2}
        style={[styles.note, { color: ink, top: detailTop + 78, width: width - COLUMN_LEFT - 20 }, note]}
      >
        {band.note}
      </Animated.Text>
      <Animated.View pointerEvents="none" style={[styles.statsBox, { top: hairTop, width: width - 2 * MARGIN }, stats]}>
        <View style={[styles.hairline, { backgroundColor: ink }]} />
        <View style={styles.statsRow}>
          {band.stats.map(([caption, value]) => (
            <View key={caption} style={{ width: columnWidth }}>
              <Text style={[styles.statCaption, { color: ink }]}>{caption}</Text>
              <Text style={[styles.statValue, { color: ink }]}>{value}</Text>
            </View>
          ))}
        </View>
      </Animated.View>
    </Animated.View>
  );
}

// MARK: Header

function HeaderGlyphs() {
  return (
    <View style={styles.glyphs} pointerEvents="none">
      <View style={styles.grid}>
        {[0, 1, 2, 3].map((i) => (
          <View key={i} style={styles.gridCell} />
        ))}
      </View>
      <View style={styles.list}>
        {[0, 1, 2].map((i) => (
          <View key={i} style={styles.listRow}>
            <View style={styles.listDot} />
            <View style={styles.listBar} />
          </View>
        ))}
      </View>
    </View>
  );
}

function haptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

// MARK: Screen

const PLAYING = 0;
const TOUCHING = 1;

/** The Today screen of an invented weather app: four bands of the day, the open one showing its weather. */
export function DaypartWeatherBands({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const width = size.width;
  const headerBottom = insets.top + HEADER_H;
  const fullHeight = size.height - headerBottom;
  const area = fullHeight - insets.bottom;
  const openHeight = area - 3 * ROW_H;
  const span = Math.max(1, openHeight - ROW_H);

  const w0 = useSharedValue(1);
  const w1 = useSharedValue(0);
  const w2 = useSharedValue(0);
  const w3 = useSharedValue(0);
  /** Icon clock, 0 ..< LOOP; it never resets on touch, so icons keep moving smoothly. */
  const clock = useSharedValue(reduced ? STILL_CLOCK : 0);
  /** Content opacity, dipped only by the reduced-motion cross-fade. */
  const alpha = useSharedValue(1);
  const red = useSharedValue(reduced);
  const auto = useSharedValue(autoPlay);
  const mode = useSharedValue(PLAYING);
  const clockOrigin = useSharedValue(-1);
  const loopOrigin = useSharedValue(-1);
  /** Band to resume the loop from on the next frame, or -1. */
  const resumeBand = useSharedValue(-1);
  const now = useSharedValue(0);
  /** Bumped by every touch and restart: an idle timer whose token is stale does nothing. */
  const generation = useSharedValue(0);
  // Touch tracking.
  const startY = useSharedValue(0);
  const startAt = useSharedValue(0);
  const lastY = useSharedValue(0);
  const lastAt = useSharedValue(0);
  const velocity = useSharedValue(0);
  const dragging = useSharedValue(false);
  const startFocus = useSharedValue(0);
  const startBand = useSharedValue(0);

  const tops = useDerivedValue(() => {
    const ws = [w0.get(), w1.get(), w2.get(), w3.get()];
    const out = [0, 0, 0, 0];
    let y = 0;
    for (let i = 0; i < 4; i += 1) {
      out[i] = y;
      y += ROW_H + (openHeight - ROW_H) * ws[i];
    }
    return out;
  });

  const setWeights = (ws: number[]) => {
    'worklet';
    w0.set(ws[0]);
    w1.set(ws[1]);
    w2.set(ws[2]);
    w3.set(ws[3]);
  };

  const dominant = () => {
    'worklet';
    const ws = [w0.get(), w1.get(), w2.get(), w3.get()];
    let best = 0;
    for (let i = 1; i < 4; i += 1) if (ws[i] > ws[best]) best = i;
    return best;
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    if (clockOrigin.get() < 0) clockOrigin.set(t);
    if (red.get()) return;
    clock.set(wrap(t - clockOrigin.get()));
    if (resumeBand.get() >= 0) {
      loopOrigin.set(t - resumeBand.get() * PHASE);
      resumeBand.set(-1);
      mode.set(PLAYING);
    }
    if (mode.get() !== PLAYING || !auto.get()) return;
    if (loopOrigin.get() < 0) loopOrigin.set(t);
    setWeights(autoWeights(wrap(t - loopOrigin.get())));
  });

  // Restart on mount, on a Reduce Motion or autoPlay change and when the app comes back.
  useEffect(() => {
    const restart = () => {
      generation.set(generation.get() + 1);
      red.set(reduced);
      auto.set(autoPlay);
      dragging.set(false);
      alpha.set(1);
      resumeBand.set(-1);
      clockOrigin.set(-1);
      loopOrigin.set(-1);
      mode.set(PLAYING);
      clock.set(reduced ? STILL_CLOCK : 0);
      w0.set(1);
      w1.set(0);
      w2.set(0);
      w3.set(0);
    };
    restart();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') restart();
    });
    return () => {
      subscription.remove();
      generation.set(generation.get() + 1);
    };
  }, [reduced, autoPlay, generation, red, auto, dragging, alpha, resumeBand, clockOrigin, loopOrigin, mode, clock, w0, w1, w2, w3]);

  // 2 s after the last touch the loop picks up from the band that is open.
  const startIdleTimer = (token: number) => {
    setTimeout(() => {
      if (generation.get() !== token || red.get() || !auto.get()) return;
      resumeBand.set(dominant());
    }, IDLE * 1000);
  };

  const springTo = (target: number) => {
    'worklet';
    w0.set(withSpring(target === 0 ? 1 : 0, SPRING));
    w1.set(withSpring(target === 1 ? 1 : 0, SPRING));
    w2.set(withSpring(target === 2 ? 1 : 0, SPRING));
    w3.set(withSpring(target === 3 ? 1 : 0, SPRING));
  };

  const fadeTo = (target: number) => {
    'worklet';
    alpha.set(withSequence(withTiming(0, { duration: FADE_OUT_MS }), withTiming(1, { duration: FADE_IN_MS })));
    w0.set(withDelay(FADE_OUT_MS, withTiming(target === 0 ? 1 : 0, { duration: 0 })));
    w1.set(withDelay(FADE_OUT_MS, withTiming(target === 1 ? 1 : 0, { duration: 0 })));
    w2.set(withDelay(FADE_OUT_MS, withTiming(target === 2 ? 1 : 0, { duration: 0 })));
    w3.set(withDelay(FADE_OUT_MS, withTiming(target === 3 ? 1 : 0, { duration: 0 })));
  };

  const select = (target: number) => {
    'worklet';
    if (target !== dominant()) scheduleOnRN(haptic);
    if (red.get()) fadeTo(target);
    else springTo(target);
  };

  const onSelect = (index: number) => {
    generation.set(generation.get() + 1);
    mode.set(TOUCHING);
    scheduleOnUI(select, index);
    startIdleTimer(generation.get());
  };

  const bandAt = (y: number) => {
    'worklet';
    const ts = tops.get();
    for (let i = 3; i > 0; i -= 1) if (y >= ts[i]) return i;
    return 0;
  };

  const touch = Gesture.Manual()
    .onTouchesDown((e, manager) => {
      if (e.numberOfTouches > 1) return;
      manager.activate();
      const p = e.allTouches[0];
      // Any touch takes the day over from the loop, where it is.
      generation.set(generation.get() + 1);
      mode.set(TOUCHING);
      dragging.set(false);
      startY.set(p.y);
      startAt.set(now.get());
      lastY.set(p.y);
      lastAt.set(now.get());
      velocity.set(0);
    })
    .onTouchesMove((e) => {
      const p = e.allTouches[0];
      if (!p) return;
      const dy = p.y - startY.get();
      const dt = now.get() - lastAt.get();
      if (dt > 0.001) {
        velocity.set(0.7 * ((p.y - lastY.get()) / dt) + 0.3 * velocity.get());
        lastAt.set(now.get());
      }
      lastY.set(p.y);
      if (!dragging.get()) {
        if (Math.abs(dy) < 10) return;
        dragging.set(true);
        const ws = [w0.get(), w1.get(), w2.get(), w3.get()];
        const d = dominant();
        // Two bands sharing the screen read as their weighted focus; the Night → Morning seam is not adjacent.
        const adjacent = ws[0] < 0.01 || ws[3] < 0.01;
        startFocus.set(adjacent ? ws[1] + 2 * ws[2] + 3 * ws[3] : d);
        startBand.set(d);
      }
      if (red.get()) return;
      setWeights(tentWeights(rubber(startFocus.get() - dy / span)));
    })
    .onTouchesUp((e, manager) => {
      if (e.numberOfTouches > 1) return;
      const p = e.changedTouches[0];
      const y = p ? p.y : lastY.get();
      const dy = y - startY.get();
      if (dragging.get()) {
        if (red.get()) {
          const step = dy < 0 ? 1 : -1;
          select(Math.min(3, Math.max(0, startBand.get() + step)));
        } else {
          const f = startFocus.get() - dy / span;
          const projected = f - (0.18 * velocity.get()) / span;
          const target = Math.min(3, Math.max(0, Math.round(projected)));
          if (target !== startBand.get()) scheduleOnRN(haptic);
          springTo(target);
        }
      } else if (now.get() - startAt.get() < 0.5) {
        select(bandAt(y));
      }
      dragging.set(false);
      manager.end();
      const token = generation.get() + 1;
      generation.set(token);
      scheduleOnRN(startIdleTimer, token);
    })
    .onTouchesCancelled((_e, manager) => {
      if (dragging.get() && !red.get()) springTo(dominant());
      dragging.set(false);
      manager.end();
      const token = generation.get() + 1;
      generation.set(token);
      scheduleOnRN(startIdleTimer, token);
    });

  const weights = [w0, w1, w2, w3];

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    if (w !== size.width || h !== size.height) setSize({ width: w, height: h });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View style={[styles.bands, { top: headerBottom, height: fullHeight }]}>
          {BANDS.map((band, i) => (
            <BandView
              key={band.label}
              index={i}
              band={band}
              w={weights[i]}
              tops={tops}
              alpha={alpha}
              clock={clock}
              width={width}
              fullHeight={fullHeight}
              openHeight={openHeight}
              onSelect={onSelect}
            />
          ))}
        </View>
      </GestureDetector>

      <View style={[styles.header, { height: headerBottom }]} pointerEvents="none">
        <LinearGradient colors={HEADER_COLORS} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
        <View style={[styles.headerRow, { top: insets.top }]}>
          <Canvas style={styles.pin}>
            <Path path={getPin()} color={HEADER_INK} fillType="evenOdd" />
          </Canvas>
          <Text style={styles.city}>Larkhaven</Text>
          <Text style={styles.date}>Tuesday, 10 November</Text>
          <HeaderGlyphs />
        </View>
      </View>
      <LinearGradient
        pointerEvents="none"
        colors={['rgba(40, 34, 26, 0.16)', 'rgba(40, 34, 26, 0)']}
        style={[styles.headerShadow, { top: headerBottom }]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#4E363B', overflow: 'hidden' },
  bands: { position: 'absolute', left: 0, right: 0 },
  band: { position: 'absolute', left: 0, right: 0, top: 0 },
  fill: { position: 'absolute', left: 0, right: 0, top: 0 },
  cast: { position: 'absolute', left: 0, right: 0, top: -CAST_H, height: CAST_H },
  edge: { position: 'absolute', left: 0, right: 0, top: 0, height: 1, backgroundColor: 'rgba(255, 255, 255, 0.14)' },
  label: { position: 'absolute', left: MARGIN, top: 16, fontSize: 11, fontWeight: '700', letterSpacing: 2.2 },
  temp: { position: 'absolute', left: MARGIN - 1, top: 30, fontSize: 30, lineHeight: 36, fontWeight: '300' },
  hours: { position: 'absolute', right: MARGIN, top: 16, fontSize: 12, fontWeight: '600', textAlign: 'right' },
  feels: { position: 'absolute', right: MARGIN, top: 38, fontSize: 13, fontWeight: '400', textAlign: 'right' },
  iconBox: { position: 'absolute', left: ICON_LEFT, width: ICON, height: ICON },
  icon: { width: ICON, height: ICON },
  condition: { position: 'absolute', left: COLUMN_LEFT, fontSize: 24, lineHeight: 30, fontWeight: '400' },
  note: { position: 'absolute', left: COLUMN_LEFT, fontSize: 14, lineHeight: 19, opacity: 0.78 },
  statsBox: { position: 'absolute', left: MARGIN },
  hairline: { height: StyleSheet.hairlineWidth * 2, opacity: 0.2 },
  statsRow: { flexDirection: 'row', marginTop: 16 },
  statCaption: { fontSize: 10.5, fontWeight: '700', letterSpacing: 1.6, opacity: 0.66 },
  statValue: { fontSize: 17, lineHeight: 22, fontWeight: '500', marginTop: 4 },
  header: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  headerRow: { position: 'absolute', left: 0, right: 0, height: HEADER_H },
  headerShadow: { position: 'absolute', left: 0, right: 0, height: 10 },
  pin: { position: 'absolute', left: MARGIN, top: 15, width: 14, height: 18 },
  city: { position: 'absolute', left: MARGIN + 22, top: 11, fontSize: 17, lineHeight: 22, fontWeight: '600', color: HEADER_INK },
  date: { position: 'absolute', left: MARGIN + 22, top: 34, fontSize: 12, lineHeight: 16, fontWeight: '500', color: HEADER_INK, opacity: 0.78 },
  glyphs: { position: 'absolute', right: MARGIN, top: 23, height: 18, flexDirection: 'row', alignItems: 'center', gap: 16 },
  grid: { width: 17, height: 17, flexDirection: 'row', flexWrap: 'wrap', gap: 3 },
  gridCell: { width: 7, height: 7, borderRadius: 1.5, backgroundColor: HEADER_INK, opacity: 0.92 },
  list: { width: 18, height: 15, justifyContent: 'space-between' },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  listDot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: HEADER_INK },
  listBar: { flex: 1, height: 2.5, borderRadius: 1.25, backgroundColor: HEADER_INK, opacity: 0.92 },
});
