import { Canvas, Circle, Group, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type Props = {
  /** The titles of the three tabs, in the order of the icons: house, picture, gear. */
  labels?: [string, string, string];
  /** The tab that is selected at first, and the one autoPlay comes back to. */
  initialTab?: number;
  background?: string;
  barColor?: string;
  /** The placeholder cards of the sample pages. */
  cardColor?: string;
  /** The selected icon's stroke, and the titles. */
  ink?: string;
  /** The stroke of an icon that is not selected. */
  mute?: string;
  /** The dot inside the selected icon. */
  accent?: string;
  /** Goes through the tabs by itself every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number) => void;
};

/** The period of the autoPlay loop, in seconds, and the tab it selects at each moment of it. */
export const LOOP = 9;
const STEPS = [
  { at: 1.2, tab: 1 },
  { at: 3.2, tab: 2 },
  { at: 5.0, tab: 1 },
  { at: 6.6, tab: 0 },
];
// After a touch the loop waits this long, returns to the first tab and starts over.
const USER_HOLD = 3;

const BACKGROUND = '#D6D6D6';
const BAR = '#EFEFEF';
const CARD = '#E4E4E4';
const INK = '#0B0B0F';
const MUTE = '#55555A';
const ACCENT = '#5546F2';
// The circle and the line on a settings row.
const DETAIL = '#CFCFCF';

const MARGIN = 24;
// The title's top edge below the status bar, and the gap under it.
const TITLE_TOP = 22;
const TITLE_GAP = 20;
const BAR_WIDTH = 262;
const BAR_HEIGHT = 88;
// The bar's bottom edge above the bottom of the screen, and at least this far above the home indicator.
const BAR_BOTTOM = 40;
const BAR_CLEARANCE = 6;
// Each icon is drawn in a 40 point box; these are the centres from the bar's left edge.
const ICON = 40;
const ICON_X = [52, 131, 210];
const TAP_WIDTH = 79;

const STROKE = 2;
const STROKE_ACTIVE = 3;
const DOT = 14;
// The house's dot starts at this share of its size, under the roof, and lands in the lower middle.
const DOT_SMALL = 0.4;
const HOME_DOT_FROM = 14;
const HOME_DOT_Y = 24;
// The picture's small circle (8 points) and the gear's ring (10 points) grow into the dot.
const PHOTO_DOT = { x: 25, y: 16.5, radius: 4 };
const GEAR_RING = 5;

// Seconds. The stroke and the dot each ease towards their new state; a tab that is deselected
// while it still moves turns round from where it is.
const STROKE_ON = 0.15;
const STROKE_OFF = 0.25;
const FILL_ON = 0.3;
const FILL_OFF = 0.25;
const CONTENT = 0.3;
const CONTENT_SCALE = 0.97;
// How long the clock of a selected icon runs: the longest of the three effects, settled.
const MOTION_FOR = 1;
// The house's dot drops on a spring that overshoots once: mass 1, stiffness 400, damping 14.
const DROP_STIFFNESS = 400;
const DROP_DAMPING = 14;
// The picture rocks: a swing of this period that is over after ROCK_FOR, about -10°, +6°, -2°.
const ROCK_DEGREES = 12;
const ROCK_PERIOD = 0.36;
const ROCK_FOR = 0.6;
const ROCK_FALLOFF = 1.17;
// The gear turns one lobe on, which maps it onto itself.
const SPIN_DEGREES = 60;
const SPIN_FOR = 0.4;

const EASE = Easing.out(Easing.cubic);

function easeOut(x: number) {
  'worklet';
  const rest = 1 - Math.min(1, Math.max(0, x));
  return 1 - rest * rest * rest;
}

/** Where a spring released at 0 towards 1 is after `t` seconds. */
function drop(t: number) {
  'worklet';
  if (t >= MOTION_FOR) return 1;
  const omega = Math.sqrt(DROP_STIFFNESS);
  const zeta = DROP_DAMPING / (2 * omega);
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** The picture's angle `t` seconds after it was selected, in degrees. */
function rock(t: number) {
  'worklet';
  if (t >= ROCK_FOR) return 0;
  return -ROCK_DEGREES * Math.sin((2 * Math.PI * t) / ROCK_PERIOD) * Math.pow(1 - t / ROCK_FOR, ROCK_FALLOFF);
}

/** A closed outline through `points` whose corners are rounded off `cuts` points before and after each. */
function rounded(points: number[][], cuts: number[]) {
  const toward = (from: number[], to: number[], distance: number) => {
    const length = Math.hypot(to[0] - from[0], to[1] - from[1]);
    return `${from[0] + ((to[0] - from[0]) * distance) / length} ${from[1] + ((to[1] - from[1]) * distance) / length}`;
  };
  const corners = points.map((point, i) => {
    const before = points[(i + points.length - 1) % points.length];
    const after = points[(i + 1) % points.length];
    return `${i === 0 ? 'M' : 'L'}${toward(point, before, cuts[i])}Q${point[0]} ${point[1]} ${toward(point, after, cuts[i])}`;
  });
  return `${corners.join('')}Z`;
}

// The house: a roof peak, two shoulders and a flat bottom.
const HOUSE_POINTS = [[20, 4], [35.5, 16.5], [35.5, 36], [4.5, 36], [4.5, 16.5]];
const HOUSE_CUTS = [3.5, 5, 7, 7, 5];
const HOUSE = Skia.Path.MakeFromSVGString(rounded(HOUSE_POINTS, HOUSE_CUTS))!;

// The picture: a rounded square, and two hills that start on its left edge and on the first hill.
const FRAME = { x: 4.5, y: 4.5, size: 31, radius: 11 };
const HILLS = Skia.Path.MakeFromSVGString('M4.5 21Q19 21 23.5 35.5M19.9 28.1Q27 24.5 35.5 25.5')!;

// The gear: a circle whose radius swells six times on the way round, with a lobe at the top.
const GEAR_RADIUS = 15;
const GEAR_WAVE = 1.6;
const GEAR_LOBES = 6;
const GEAR_STEPS = 120;
const GEAR = Skia.Path.MakeFromSVGString(
  `M${Array.from({ length: GEAR_STEPS }, (_, i) => {
    const angle = (i / GEAR_STEPS) * 2 * Math.PI;
    const radius = GEAR_RADIUS + GEAR_WAVE * Math.cos(GEAR_LOBES * angle);
    return `${ICON / 2 + radius * Math.sin(angle)} ${ICON / 2 - radius * Math.cos(angle)}`;
  }).join('L')}Z`,
)!;

const CENTRE = vec(ICON / 2, ICON / 2);

/** What moves one tab: all of it runs on the UI thread. */
type Motion = {
  /** 1 while the tab is selected. */
  active: SharedValue<number>;
  /** The stroke, from thin and grey (0) to thick and dark (1). */
  on: SharedValue<number>;
  /** The dot, from absent (0) to full (1). */
  fill: SharedValue<number>;
  /** The tab's page, from hidden (0) to shown (1). */
  shown: SharedValue<number>;
  /** Seconds since the tab was last selected, up to MOTION_FOR. */
  t: SharedValue<number>;
};

function useMotion(selected: boolean): Motion {
  const state = selected ? 1 : 0;
  const active = useSharedValue(state);
  const on = useSharedValue(state);
  const fill = useSharedValue(state);
  const shown = useSharedValue(state);
  const t = useSharedValue(MOTION_FOR);
  return { active, on, fill, shown, t };
}

function ease(seconds: number) {
  return { duration: seconds * 1000, easing: EASE, reduceMotion: ReduceMotion.Never };
}

function move(motion: Motion, selected: boolean) {
  motion.active.set(selected ? 1 : 0);
  motion.on.set(withTiming(selected ? 1 : 0, ease(selected ? STROKE_ON : STROKE_OFF)));
  motion.fill.set(withTiming(selected ? 1 : 0, ease(selected ? FILL_ON : FILL_OFF)));
  motion.shown.set(withTiming(selected ? 1 : 0, ease(CONTENT)));
  if (!selected) return;
  motion.t.set(0);
  motion.t.set(withTiming(MOTION_FOR, { duration: MOTION_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type IconProps = { motion: Motion; still: boolean; ink: string; mute: string; accent: string };

/** The stroke of an icon: it thickens and darkens as the tab is selected. */
function useStroke(motion: Motion, ink: string, mute: string) {
  const width = useDerivedValue(() => STROKE + (STROKE_ACTIVE - STROKE) * motion.on.get());
  const color = useDerivedValue(() => interpolateColor(motion.on.get(), [0, 1], [mute, ink], 'RGB', { gamma: 1 }));
  return { width, color };
}

/** An outlined circle of radius `small` that fills with the accent and grows into the dot. */
function Morph({ motion, still, small, x, y, stroke, accent }: IconProps & { small: number; x: number; y: number; stroke: ReturnType<typeof useStroke> }) {
  const grown = useDerivedValue(() => small + (DOT / 2 - small) * motion.fill.get());
  // Without motion the two only cross-fade, each at its own size.
  const ring = useDerivedValue(() => (still ? small : grown.get()) - stroke.width.get() / 2);
  const dot = useDerivedValue(() => (still ? DOT / 2 : grown.get()));
  const ringOpacity = useDerivedValue(() => 1 - motion.fill.get());
  return (
    <>
      <Circle cx={x} cy={y} r={ring} style="stroke" strokeWidth={stroke.width} color={stroke.color} opacity={ringOpacity} />
      <Circle cx={x} cy={y} r={dot} color={accent} opacity={motion.fill} />
    </>
  );
}

function HomeIcon(props: IconProps) {
  const { motion, still, ink, mute, accent } = props;
  const stroke = useStroke(motion, ink, mute);
  // Selected, the dot drops from under the roof on the spring; deselected, it rises back as it fades.
  const y = useDerivedValue(() => {
    if (still) return HOME_DOT_Y;
    return HOME_DOT_FROM + (HOME_DOT_Y - HOME_DOT_FROM) * (motion.active.get() ? drop(motion.t.get()) : motion.fill.get());
  });
  const radius = useDerivedValue(() => (DOT / 2) * (still ? 1 : DOT_SMALL + (1 - DOT_SMALL) * motion.fill.get()));
  return (
    <>
      <Path path={HOUSE} style="stroke" strokeWidth={stroke.width} strokeJoin="round" color={stroke.color} />
      <Circle cx={ICON / 2} cy={y} r={radius} color={accent} opacity={motion.fill} />
    </>
  );
}

function PhotosIcon(props: IconProps) {
  const { motion, still, ink, mute } = props;
  const stroke = useStroke(motion, ink, mute);
  const turn = useDerivedValue(() => [{ rotate: still ? 0 : (rock(motion.t.get()) * Math.PI) / 180 }]);
  return (
    <Group origin={CENTRE} transform={turn}>
      <RoundedRect x={FRAME.x} y={FRAME.y} width={FRAME.size} height={FRAME.size} r={FRAME.radius} style="stroke" strokeWidth={stroke.width} color={stroke.color} />
      <Path path={HILLS} style="stroke" strokeWidth={stroke.width} strokeCap="round" color={stroke.color} />
      <Morph {...props} small={PHOTO_DOT.radius} x={PHOTO_DOT.x} y={PHOTO_DOT.y} stroke={stroke} />
    </Group>
  );
}

function SettingsIcon(props: IconProps) {
  const { motion, still, ink, mute } = props;
  const stroke = useStroke(motion, ink, mute);
  const turn = useDerivedValue(() => [{ rotate: still ? 0 : (SPIN_DEGREES * easeOut(motion.t.get() / SPIN_FOR) * Math.PI) / 180 }]);
  return (
    <>
      <Group origin={CENTRE} transform={turn}>
        <Path path={GEAR} style="stroke" strokeWidth={stroke.width} strokeJoin="round" color={stroke.color} />
      </Group>
      <Morph {...props} small={GEAR_RING} x={ICON / 2} y={ICON / 2} stroke={stroke} />
    </>
  );
}

const ICONS = [HomeIcon, PhotosIcon, SettingsIcon];

/** One tab's page: it fades in over the page it replaces, growing the last few hundredths. */
function Page({ motion, still, hidden, top, children }: { motion: Motion; still: boolean; hidden: boolean; top: number; children: ReactNode }) {
  const style = useAnimatedStyle(() => ({
    opacity: motion.shown.get(),
    transform: [{ scale: still ? 1 : CONTENT_SCALE + (1 - CONTENT_SCALE) * motion.shown.get() }],
  }));
  return (
    <Animated.View
      pointerEvents={hidden ? 'none' : 'auto'}
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}
      style={[styles.page, { paddingTop: top }, style]}
    >
      {children}
    </Animated.View>
  );
}

/** Sample pages, one per tab: grey placeholders in three different layouts. */
function HomeCards({ color }: { color: string }) {
  return (
    <View style={styles.stack}>
      <View style={[styles.card, { height: 150, backgroundColor: color }]} />
      <View style={[styles.card, { height: 96, backgroundColor: color }]} />
    </View>
  );
}

function PhotoGrid({ color }: { color: string }) {
  return (
    <View style={styles.grid}>
      {[0, 1, 2].map((row) => (
        <View key={row} style={styles.gridRow}>
          <View style={[styles.photo, { backgroundColor: color }]} />
          <View style={[styles.photo, { backgroundColor: color }]} />
        </View>
      ))}
    </View>
  );
}

function SettingsRows({ color }: { color: string }) {
  return (
    <View style={styles.rows}>
      {[0, 1, 2, 3, 4].map((row) => (
        <View key={row} style={[styles.row, { backgroundColor: color }]}>
          <View style={styles.rowCircle} />
          <View style={styles.rowLine} />
        </View>
      ))}
    </View>
  );
}

const PAGES = [HomeCards, PhotoGrid, SettingsRows];

/**
 * A screen with a floating pill of three outlined icons. Selecting one thickens and darkens its
 * stroke and brings a dot into it, each icon in its own way: the house's drops in and bounces,
 * the picture rocks while its small circle fills, the gear turns while its ring fills. The page
 * above cross-fades to the tab's own.
 */
export function DotIconTabBar({
  labels = ['Home', 'Photos', 'Settings'],
  initialTab = 0,
  background = BACKGROUND,
  barColor = BAR,
  cardColor = CARD,
  ink = INK,
  mute = MUTE,
  accent = ACCENT,
  autoPlay = false,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [selected, setSelected] = useState(initialTab);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  const motions = [useMotion(initialTab === 0), useMotion(initialTab === 1), useMotion(initialTab === 2)];

  const select = (index: number, byHand: boolean) => {
    if (index === selected) return;
    // The loop that plays by itself is silent.
    if (byHand) tick();
    move(motions[selected], false);
    move(motions[index], true);
    setSelected(index);
    onSelect?.(index);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ select, initialTab });
  useEffect(() => {
    latest.current = { select, initialTab };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // Every step is timed from the loop's origin, so a late timer does not push the next one back.
    const cycle = (origin: number) => {
      STEPS.forEach((step) => after(origin + step.at * 1000 - Date.now(), () => latest.current.select(step.tab, false)));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        latest.current.select(latest.current.initialTab, false);
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const bottom = Math.max(BAR_BOTTOM, insets.bottom + BAR_CLEARANCE);

  return (
    <View style={[styles.screen, { backgroundColor: background }]}>
      <StatusBar style="dark" />

      {PAGES.map((Content, i) => (
        <Page key={i} motion={motions[i]} still={reducedMotion} hidden={i !== selected} top={insets.top + TITLE_TOP}>
          <Text style={[styles.title, { color: ink }]} numberOfLines={1} accessibilityRole="header">
            {labels[i]}
          </Text>
          <Content color={cardColor} />
        </Page>
      ))}

      <View pointerEvents="box-none" style={[styles.barSlot, { bottom }]}>
        <View style={[styles.bar, { backgroundColor: barColor }]} accessibilityRole="tablist">
          {ICON_X.map((x, i) => (
            <Pressable
              key={i}
              style={[styles.tab, { left: x - TAP_WIDTH / 2 }]}
              accessibilityRole="tab"
              accessibilityLabel={labels[i]}
              accessibilityState={{ selected: i === selected }}
              onPress={() => {
                setTouches((n) => n + 1);
                select(i, true);
              }}
            />
          ))}
          <Canvas style={styles.icons} pointerEvents="none">
            {ICONS.map((Icon, i) => (
              <Group key={i} transform={[{ translateX: ICON_X[i] - ICON / 2 }, { translateY: (BAR_HEIGHT - ICON) / 2 }]}>
                <Icon motion={motions[i]} still={reducedMotion} ink={ink} mute={mute} accent={accent} />
              </Group>
            ))}
          </Canvas>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  page: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    paddingHorizontal: MARGIN,
  },
  title: {
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    includeFontPadding: false,
    marginBottom: TITLE_GAP,
  },
  stack: {
    gap: 12,
  },
  card: {
    borderRadius: 24,
    borderCurve: 'continuous',
  },
  grid: {
    gap: 13,
  },
  gridRow: {
    flexDirection: 'row',
    gap: 13,
  },
  photo: {
    flex: 1,
    aspectRatio: 1,
    borderRadius: 18,
    borderCurve: 'continuous',
  },
  rows: {
    gap: 8,
  },
  row: {
    height: 56,
    borderRadius: 16,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    gap: 12,
  },
  rowCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: DETAIL,
  },
  rowLine: {
    width: 90,
    height: 10,
    borderRadius: 5,
    backgroundColor: DETAIL,
  },
  barSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  bar: {
    width: BAR_WIDTH,
    height: BAR_HEIGHT,
    borderRadius: BAR_HEIGHT / 2,
    boxShadow: [{ offsetX: 0, offsetY: 4, blurRadius: 14, color: 'rgba(0,0,0,0.06)' }],
  },
  tab: {
    position: 'absolute',
    top: 0,
    width: TAP_WIDTH,
    height: BAR_HEIGHT,
  },
  icons: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BAR_WIDTH,
    height: BAR_HEIGHT,
  },
});
