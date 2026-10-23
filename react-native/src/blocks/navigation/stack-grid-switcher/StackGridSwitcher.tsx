import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, LinearGradient, Path, RadialGradient, Rect, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type Figure = 'chevron' | 'disc' | 'rings' | 'stripes' | 'square' | 'dots' | 'wave' | 'triangle';

export type Sleeve = {
  title: string;
  artist: string;
  /** The cover's gradient, top to bottom. */
  colors: [string, string];
  /** The one figure drawn on the cover, and its colour. */
  figure: Figure;
  tint: string;
  /** The sleeve's front edge, where the title is written. */
  edge: string;
  /** The edge is light: its title is written in black. */
  light?: boolean;
};

export type Section = { title: string; items: Sleeve[] };

type Props = {
  /** The two halves of the library; the segmented control chooses one. */
  sections?: Section[];
  /** Starts on the grid instead of the stack. */
  initialGrid?: boolean;
  /** Presses the view toggle by itself, to the grid and back, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with a sleeve or a cover that was tapped. */
  onSelect?: (item: Sleeve) => void;
  /** Called when the toggle changes the view. */
  onViewChange?: (grid: boolean) => void;
  onSort?: () => void;
};

const ALBUMS: Sleeve[] = [
  { title: 'Open Window', artist: 'Lowe', colors: ['#FFFFFF', '#D9DBE0'], figure: 'chevron', tint: '#1F4FD8', edge: '#C9CBD1', light: true },
  { title: 'Night Bus', artist: 'Ada Renn', colors: ['#0B0B0C', '#4A2E1C'], figure: 'disc', tint: '#F28A2E', edge: '#1A1310' },
  { title: 'Quiet Arcs', artist: 'Tomas Vale', colors: ['#E6E6E8', '#CFCFD4'], figure: 'rings', tint: '#111111', edge: '#B9B9BF', light: true },
  { title: 'Paper Weather', artist: 'Jae Min', colors: ['#7B3FE4', '#2A1E7A'], figure: 'stripes', tint: '#FF8CC6', edge: '#4B2FA6' },
  { title: 'Red Room', artist: 'Ostrow', colors: ['#FFFFFF', '#F1F1F1'], figure: 'square', tint: '#D92B2B', edge: '#E4E4E6', light: true },
  { title: 'Gold Standard', artist: 'Mara Wilde', colors: ['#0A0A0A', '#1B1708'], figure: 'dots', tint: '#D8B24A', edge: '#2A2412' },
  { title: 'Blue Hour', artist: 'Dean Laurel', colors: ['#1E8C8C', '#06181C'], figure: 'wave', tint: '#D6F2EE', edge: '#123C40' },
  { title: 'Half Light', artist: 'Noor Sato', colors: ['#2B2B2F', '#121214'], figure: 'rings', tint: '#FFFFFF', edge: '#0E0E10' },
  { title: 'Soft Machines', artist: 'Iri', colors: ['#B9A3F2', '#9C86E0'], figure: 'triangle', tint: '#6B52C8', edge: '#7E66D6' },
  { title: 'Salt Lines', artist: 'Peder Holm', colors: ['#F4E9D8', '#E3CDAA'], figure: 'stripes', tint: '#9C5B2E', edge: '#D4BC96', light: true },
  { title: 'Low Sun', artist: 'Cass Arden', colors: ['#F2A03D', '#C63D2F'], figure: 'disc', tint: '#FFE9B8', edge: '#9E2F25' },
  { title: 'Glass Garden', artist: 'Yun Hale', colors: ['#0E3B2E', '#061712'], figure: 'dots', tint: '#7BE0A8', edge: '#0A241C' },
  { title: 'Field Notes', artist: 'Rowan Pike', colors: ['#DDE7F2', '#B8C8DB'], figure: 'wave', tint: '#28456B', edge: '#A6B7CC', light: true },
  { title: 'Night Shift', artist: 'Odile', colors: ['#15182B', '#05060D'], figure: 'chevron', tint: '#7C8CFF', edge: '#0B0D1A' },
  { title: 'Plain Song', artist: 'The Marrows', colors: ['#EFE6F0', '#D9C6DC'], figure: 'square', tint: '#5A2A62', edge: '#C7B1CB', light: true },
];

const PLAYLISTS: Sleeve[] = [
  { title: 'Morning Commute', artist: '24 songs', colors: ['#FFD9A8', '#F29A6B'], figure: 'disc', tint: '#FFFFFF', edge: '#D9794C' },
  { title: 'Deep Focus', artist: '41 songs', colors: ['#10233F', '#060B17'], figure: 'rings', tint: '#6FA8FF', edge: '#0A1528' },
  { title: 'Sunday Kitchen', artist: '18 songs', colors: ['#F6F1E4', '#E6DCC2'], figure: 'dots', tint: '#C2542B', edge: '#D6CAA9', light: true },
  { title: 'Long Drive', artist: '56 songs', colors: ['#3C2A6B', '#140E2B'], figure: 'wave', tint: '#FFB36B', edge: '#22184A' },
  { title: 'Rain Room', artist: '33 songs', colors: ['#C9D6DC', '#9FB2BC'], figure: 'stripes', tint: '#3D5560', edge: '#8DA1AB', light: true },
  { title: 'After Hours', artist: '27 songs', colors: ['#1A1A1E', '#000000'], figure: 'triangle', tint: '#E0457B', edge: '#101013' },
  { title: 'Fresh Finds', artist: '50 songs', colors: ['#D4F2C4', '#8FD39A'], figure: 'chevron', tint: '#1E5B3A', edge: '#79BD86', light: true },
  { title: 'Slow Dance', artist: '15 songs', colors: ['#F2C4D0', '#D98AA4'], figure: 'square', tint: '#7A2440', edge: '#C4728E' },
  { title: 'Run Club', artist: '38 songs', colors: ['#FFE14D', '#F2A81D'], figure: 'stripes', tint: '#1A1A1E', edge: '#D99114', light: true },
];

const SECTIONS: Section[] = [
  { title: 'Albums', items: ALBUMS },
  { title: 'Playlists', items: PLAYLISTS },
];

const PAGE = '#9198AC';
const PAGE_LIGHT = '#B4B8C6';
const SEGMENT_FILL = 'rgba(255,255,255,0.25)';
const SEGMENT_INK = '#111111';
const SEGMENT_MUTE = 'rgba(255,255,255,0.85)';
const DOCK_FILL = 'rgba(255,255,255,0.55)';
const DOCK_INK = '#1A1A1E';
const DOCK_MUTE = '#7C7C88';

// The top bar, under the safe area's top edge.
const BAR_TOP = 6;
const BAR = 35;
const BAR_SIDE = 22;
const SEGMENT = 81;
const SEGMENT_INSET = 2.5;
const SEGMENT_SLIDE = 250;
const DOCK_WIDTH = 188;
const DOCK_HEIGHT = 55;

// The stack: the middle of the first sleeve's front edge under the safe area's top edge, the
// distance from one sleeve to the next, the edge's height and the room at the sides.
const STACK_TOP = 66;
const PITCH = 86;
const STRIP = 14;
const STACK_SIDE = 36;
// The eye is level with this fraction of the screen's height: sleeves above it show their
// underside, sleeves below it their cover.
const EYE = 0.453;
// A sleeve lies flat. Its far edge is drawn at this scale, and its cover rises (or hangs) by up to
// LIFT_MAX points, reached REACH points from eye level; at eye level only the front edge shows.
const FAR = 0.7;
const LIFT_MAX = 71;
const REACH = 300;
const SHADE = 0.3;
const PRESS_SCALE = 0.05;
const PRESS_MS = 140;

// The grid.
const COLUMNS = 3;
const GRID_TOP = 45;
const GRID_SIDE = 4.5;
const GRID_GAP = 6;
const GRID_CAMERA = 800;

// A switch, in seconds from the tap: the view on screen leaves for LEAVE, the page is empty for a
// beat, and from ARRIVE the other view assembles on springs, one piece after another.
const LEAVE = 0.32;
const ARRIVE = 0.6;
const SPREAD = 120;
const LEAVE_SCALE = 0.8;
const LEAVE_BLUR = 16;
const ARRIVE_BLUR = 12;
const ARRIVE_CLEAR = 0.4;
const SLEEVE_STAGGER = 0.035;
const SLEEVE_RANKS = 8;
const SLEEVE_FROM = 0.6;
const LABEL_AT = 1.05;
const LABEL_IN = 0.3;
const COVER_STAGGER = 0.03;
const COVER_RANKS = 20;
const COVER_TILT = (70 * Math.PI) / 180;
const COVER_SHIFT = 60;
const COVER_LEAVE = 0.22;
const COVER_LEAVE_STAGGER = 0.01;
const PIECE_IN = 0.12;
// The spring of every arriving piece (mass 1, stiffness 220, damping 20), as a function of time:
// 1 - e^(-DECAY t) (cos(RATE t) + DECAY / RATE sin(RATE t)); it is taken as settled after SPRING_FOR.
const SPRING_DECAY = 10;
const SPRING_RATE = 10.954;
const SPRING_FOR = 0.7;
const GLYPH_FROM = 0.25;
const GLYPH_TO = 0.55;
// The clock stops here, and the toggle takes another press.
const SWITCH_END = 1.75;
// With reduced motion the two views cross-fade.
const FADE = 0.25;
// Changing the segment: what is shown fades and blurs out, the other half comes in the same way.
const SWAP = 0.5;
const SWAP_BLUR = 10;

// The loop: the toggle is pressed to go to the grid, and again to come back.
export const LOOP = 9;
const TO_GRID_AT = 1.2;
const TO_STACK_AT = 5;
const USER_HOLD = 3;

// iOS has no blur filter for a view, so a blur view lies over the sleeves and covers; Android blurs
// the two layers themselves, from Android 12 on, and only fades and moves them before that.
const IOS = Platform.OS === 'ios';
const CAN_FILTER = Platform.OS === 'android' && Number(Platform.Version) >= 31;
const BLUR_INTENSITY = 40;

// Glyphs in boxes of 24 points.
const SORT = 'M7 20V4M3 8L7 4M17 4V20M21 16L17 20';
const LINES = 'M5 6H19M5 10H19M5 14H19M5 18H19';
const SQUARES = 'M5 5H10V10H5ZM14 5H19V10H14ZM5 14H10V19H5ZM14 14H19V19H14Z';
const DOCK_SLEEVES = 'M5 4V20M9 4V20';
const DOCK_SLEEVE = 'M12.5 4L19 6V18L12.5 20Z';
const DOCK_FACE = 'M8.5 14C9.4 15.6 10.6 16.4 12 16.4C13.4 16.4 14.6 15.6 15.5 14';
const DOCK_LIST = 'M4 6H16M4 11H13M4 16H10M17.5 16.5V9L20.5 10';

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function smooth(x: number, from: number, to: number) {
  'worklet';
  const t = clamp01((x - from) / (to - from));
  return t * t * (3 - 2 * t);
}

function easeIn(t: number) {
  'worklet';
  const c = clamp01(t);
  return c * c * c;
}

/** Where an arriving piece is, `t` seconds after it set off: 0 to 1 with one small overshoot. */
function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= SPRING_FOR) return 1;
  return 1 - Math.exp(-SPRING_DECAY * t) * (Math.cos(SPRING_RATE * t) + (SPRING_DECAY / SPRING_RATE) * Math.sin(SPRING_RATE * t));
}

type Frame = { width: number; height: number; stackTop: number; eye: number; side: number };

/** Where a sleeve's front edge is, how big and how visible the sleeve is, and its title. */
function sleevePose(index: number, t: number, toGrid: boolean, scrollY: number, frame: Frame, still: boolean) {
  'worklet';
  const base = frame.stackTop + index * PITCH - scrollY;
  if (still) {
    const there = smooth(t, 0, FADE);
    return { y: base, scale: 1, opacity: toGrid ? 1 - there : there, label: 1 };
  }
  if (toGrid) {
    // Leaving: the sleeves above eye level rise and the others sink, shrinking.
    const u = easeIn(t / LEAVE);
    return { y: base + (base < frame.eye ? -SPREAD : SPREAD) * u, scale: 1 - (1 - LEAVE_SCALE) * u, opacity: 1 - smooth(u, 0.4, 1), label: 1 };
  }
  // Arriving: from eye level out to their shelves, the middle ones first.
  const middle = Math.round((frame.eye - frame.stackTop + scrollY) / PITCH);
  const since = t - ARRIVE - Math.min(Math.abs(index - middle), SLEEVE_RANKS) * SLEEVE_STAGGER;
  // A sleeve whose shelf is off the screen is put there without the trip across it.
  const a = base < -PITCH || base > frame.height + PITCH ? 1 : spring(since);
  return {
    y: frame.eye + (base - frame.eye) * a,
    scale: SLEEVE_FROM + (1 - SLEEVE_FROM) * a,
    opacity: smooth(since, 0, PIECE_IN),
    label: smooth(t, LABEL_AT, LABEL_AT + LABEL_IN),
  };
}

/** The turn of a flat sleeve's cover about its front edge, and the camera that draws it, `h` points below eye level. */
function tilt(h: number, size: number) {
  'worklet';
  const near = 1 - Math.min(Math.abs(h) / REACH, 1);
  const lift = LIFT_MAX * (1 - near * near);
  const angle = Math.acos(Math.min(lift / (size * FAR), 1));
  // Past a right angle the cover hangs below the edge and is seen from underneath.
  return { angle: h >= 0 ? angle : Math.PI - angle, camera: (FAR * size * Math.sin(angle)) / (1 - FAR) };
}

/** How a cover of the grid is tipped, moved and shown. */
function coverPose(index: number, count: number, t: number, toGrid: boolean, still: boolean) {
  'worklet';
  if (still) {
    const there = smooth(t, 0, FADE);
    return { tilt: 0, shift: 0, opacity: toGrid ? there : 1 - there };
  }
  if (toGrid) {
    // Arriving in reading order: tipped back and off to the top left, then flat in its cell.
    const since = t - ARRIVE - Math.min(index, COVER_RANKS) * COVER_STAGGER;
    const b = spring(since);
    return { tilt: COVER_TILT * (1 - b), shift: -COVER_SHIFT * (1 - b), opacity: smooth(since, 0, PIECE_IN) };
  }
  // Leaving in reverse order: tipping back.
  const v = easeIn((t - Math.min(count - 1 - index, COVER_RANKS) * COVER_LEAVE_STAGGER) / COVER_LEAVE);
  return { tilt: COVER_TILT * v, shift: -COVER_SHIFT * 0.5 * v, opacity: 1 - smooth(v, 0.3, 1) };
}

/** How far out of focus a view is, against ARRIVE_BLUR: going out as it leaves, coming in as it assembles. */
function viewBlur(t: number, leaving: boolean) {
  'worklet';
  return leaving ? (LEAVE_BLUR / ARRIVE_BLUR) * easeIn(t / LEAVE) : 1 - smooth(t, ARRIVE, ARRIVE + ARRIVE_CLEAR);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** A cover made in code: a gradient and one figure, in a box of 100 units. */
function Cover({ item, id, shaded = false }: { item: Sleeve; id: string; shaded?: boolean }) {
  const { figure, tint } = item;
  return (
    <Svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
      <Defs>
        <LinearGradient id={`${id}-fill`} x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={item.colors[0]} />
          <Stop offset="1" stopColor={item.colors[1]} />
        </LinearGradient>
        <LinearGradient id={`${id}-shade`} x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#000000" stopOpacity={SHADE} />
          <Stop offset="1" stopColor="#000000" stopOpacity={0} />
        </LinearGradient>
      </Defs>
      <Rect width={100} height={100} fill={`url(#${id}-fill)`} />
      {figure === 'chevron' && <Path d="M28 32L50 68L72 32" stroke={tint} strokeWidth={10} strokeLinejoin="round" strokeLinecap="round" fill="none" />}
      {figure === 'disc' && <Circle cx={50} cy={50} r={22} fill={tint} />}
      {figure === 'rings' && [14, 24, 34].map((r) => <Circle key={r} cx={50} cy={50} r={r} stroke={tint} strokeWidth={1.5} fill="none" />)}
      {figure === 'stripes' && [26, 38, 50, 62, 74].map((y) => <Rect key={y} x={22} y={y - 2.5} width={56} height={5} fill={tint} />)}
      {figure === 'square' && <Rect x={34} y={34} width={32} height={32} fill={tint} />}
      {figure === 'dots' && [26, 42, 58, 74].map((y) => [26, 42, 58, 74].map((x) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={3} fill={tint} />))}
      {figure === 'wave' && <Path d="M14 54C26 34 38 34 50 54S74 74 86 54" stroke={tint} strokeWidth={3} strokeLinecap="round" fill="none" />}
      {figure === 'triangle' && <Path d="M50 26L76 72H24Z" fill={tint} />}
      {/* The far half of a sleeve lying flat is in shade. */}
      {shaded && <Rect width={100} height={100} fill={`url(#${id}-shade)`} />}
    </Svg>
  );
}

type Motion = {
  /** Seconds since the toggle was pressed; SWITCH_END at rest. */
  clock: SharedValue<number>;
  /** 1 when the grid is the view shown or arriving, 0 for the stack. */
  toGrid: SharedValue<number>;
  scrollY: SharedValue<number>;
  /** The sleeve under a finger, and how far it has come forward. */
  pressed: SharedValue<number>;
  press: SharedValue<number>;
};

/** One sleeve of the stack: its cover lying flat, and its front edge with the title. */
function SleeveView({ index, item, id, frame, motion, still }: { index: number; item: Sleeve; id: string; frame: Frame; motion: Motion; still: boolean }) {
  const { clock, toGrid, scrollY, pressed, press } = motion;
  const size = frame.width - frame.side * 2;

  const cover = useAnimatedStyle(() => {
    const pose = sleevePose(index, clock.get(), toGrid.get() === 1, scrollY.get(), frame, still);
    const turn = tilt(pose.y - frame.eye, size);
    const forward = pressed.get() === index ? 1 + PRESS_SCALE * press.get() : 1;
    return {
      opacity: pose.opacity,
      transform: [{ translateY: pose.y - size }, { perspective: turn.camera }, { rotateX: `${turn.angle}rad` }, { scale: pose.scale * forward }],
    };
  });
  const strip = useAnimatedStyle(() => {
    const pose = sleevePose(index, clock.get(), toGrid.get() === 1, scrollY.get(), frame, still);
    const forward = pressed.get() === index ? 1 + PRESS_SCALE * press.get() : 1;
    return { opacity: pose.opacity, transform: [{ translateY: pose.y - STRIP / 2 }, { scale: pose.scale * forward }] };
  });
  const label = useAnimatedStyle(() => ({ opacity: sleevePose(index, clock.get(), toGrid.get() === 1, scrollY.get(), frame, still).label }));

  return (
    <>
      <Animated.View style={[styles.sleeveCover, { left: frame.side, width: size, height: size }, cover]}>
        <Cover item={item} id={id} shaded />
      </Animated.View>
      <Animated.View style={[styles.strip, { left: frame.side, width: size, backgroundColor: item.edge }, strip]}>
        <Animated.Text style={[styles.stripText, { color: item.light ? '#000000' : '#ffffff' }, label]} numberOfLines={1}>
          <Text style={styles.stripTitle}>{item.title}</Text> {item.artist}
        </Animated.Text>
      </Animated.View>
    </>
  );
}

/** One cover of the grid. */
function GridCover({
  index,
  count,
  item,
  id,
  size,
  motion,
  still,
  onPress,
}: {
  index: number;
  count: number;
  item: Sleeve;
  id: string;
  size: number;
  motion: Motion;
  still: boolean;
  onPress: () => void;
}) {
  const { clock, toGrid } = motion;
  const placed = useAnimatedStyle(() => {
    const pose = coverPose(index, count, clock.get(), toGrid.get() === 1, still);
    return {
      opacity: pose.opacity,
      transform: [{ translateX: pose.shift }, { translateY: pose.shift }, { perspective: GRID_CAMERA }, { rotateX: `${pose.tilt}rad` }],
    };
  });
  return (
    <Animated.View style={[{ width: size, height: size }, placed]}>
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={`${item.title}, ${item.artist}`}>
        {({ pressed }) => (
          <Animated.View
            style={{ width: size, height: size, transform: [{ scale: pressed ? 0.96 : 1 }], transitionProperty: 'transform', transitionDuration: PRESS_MS }}
          >
            <Cover item={item} id={id} />
          </Animated.View>
        )}
      </Pressable>
    </Animated.View>
  );
}

function Glyph({ path, color, filled = false, size = 24, stroke = 1.8 }: { path: string; color: string; filled?: boolean; size?: number; stroke?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={path} stroke={filled ? 'none' : color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill={filled ? color : 'none'} />
    </Svg>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A music library with two views of the same sleeves. The stack shows them lying flat, one above
 * the other, in perspective: above eye level you look at their underside, below it at their covers,
 * and scrolling moves them through that. The toggle at the top right blows the stack apart and
 * assembles a flat grid of the covers, and back.
 */
export function StackGridSwitcher({ sections = SECTIONS, initialGrid = false, autoPlay = false, onSelect, onViewChange, onSort }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;

  const [grid, setGrid] = useState(initialGrid);
  // The segment chosen, and the one whose sleeves are drawn: it follows half a swap later.
  const [segment, setSegment] = useState(0);
  const [shown, setShown] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const clock = useSharedValue(SWITCH_END);
  const toGrid = useSharedValue(initialGrid ? 1 : 0);
  const scrollY = useSharedValue(0);
  const pressed = useSharedValue(-1);
  const press = useSharedValue(0);
  // 0 to 1 across a change of segment; 0 at rest.
  const swap = useSharedValue(0);
  // How far the toggle is pressed in.
  const push = useSharedValue(0);
  const slide = useSharedValue(0);
  const stackScroll = useAnimatedRef<Animated.ScrollView>();
  const gridScroll = useRef<ScrollView>(null);

  const top = insets.top;
  const bottom = Math.max(insets.bottom, 12);
  const frame: Frame = { width, height, stackTop: top + STACK_TOP, eye: height * EYE, side: STACK_SIDE };
  const motion: Motion = { clock, toGrid, scrollY, pressed, press };
  const items = sections[Math.min(shown, sections.length - 1)].items;
  const cell = (width - GRID_SIDE * 2 - GRID_GAP * (COLUMNS - 1)) / COLUMNS;

  /** The toggle: the view on screen leaves and the other one assembles. */
  const flip = (next: boolean, auto: boolean) => {
    if (clock.get() < SWITCH_END) return;
    if (!auto) tick();
    push.set(withSequence(withTiming(1, { duration: 90, reduceMotion: ReduceMotion.Never }), withTiming(0, { duration: 200, reduceMotion: ReduceMotion.Never })));
    setGrid(next);
    onViewChange?.(next);
    toGrid.set(next ? 1 : 0);
    clock.set(0);
    clock.set(withTiming(SWITCH_END, { duration: SWITCH_END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  const commit = (next: number) => {
    setShown(next);
    stackScroll.current?.scrollTo({ y: 0, animated: false });
    gridScroll.current?.scrollTo({ y: 0, animated: false });
  };

  /** The segmented control: the capsule slides, and what is shown is swapped while it is out of sight. */
  const choose = (next: number, auto = false) => {
    if (next === segment || swap.get() > 0) return;
    if (!auto) tick();
    setSegment(next);
    slide.set(withTiming(next, { duration: SEGMENT_SLIDE, easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: ReduceMotion.Never }));
    swap.set(0);
    swap.set(
      withSequence(
        withTiming(0.5, { duration: SWAP * 500, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
          if (finished) scheduleOnRN(commit, next);
        }),
        withTiming(1, { duration: SWAP * 500, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
        withTiming(0, { duration: 0, reduceMotion: ReduceMotion.Never }),
      ),
    );
  };

  /** The state every loop starts from, set at once. */
  const rest = () => {
    cancelAnimation(clock);
    cancelAnimation(swap);
    clock.set(SWITCH_END);
    toGrid.set(initialGrid ? 1 : 0);
    swap.set(0);
    slide.set(0);
    push.set(0);
    press.set(0);
    setGrid(initialGrid);
    setSegment(0);
    commit(0);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ flip, choose, rest, grid, segment });
  useEffect(() => {
    latest.current = { flip, choose, rest, grid, segment };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      latest.current.rest();
      at(TO_GRID_AT, () => latest.current.flip(!initialGrid, true));
      at(TO_STACK_AT, () => latest.current.flip(initialGrid, true));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    /** After a touch: back to the first view of the first segment, then the loop again. */
    const back = () => {
      if (clock.get() < SWITCH_END || swap.get() > 0) return after(100, back);
      if (latest.current.segment !== 0) {
        latest.current.choose(0, true);
        return after(SWAP * 1000 + 100, back);
      }
      if (latest.current.grid !== initialGrid) {
        latest.current.flip(initialGrid, true);
        return after(SWITCH_END * 1000, back);
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, back);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, initialGrid, clock, swap]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollY.set(event.contentOffset.y);
  });

  const lift = (index: number, down: boolean) => {
    if (down) pressed.set(index);
    press.set(withTiming(down ? 1 : 0, { duration: PRESS_MS, reduceMotion: ReduceMotion.Never }));
  };

  // Both views, through a change of segment.
  const content = useAnimatedStyle(() => {
    const there = smooth(Math.abs(swap.get() - 0.5) * 2, 0, 1);
    const style = { opacity: there, transform: [{ scale: reducedMotion ? 1 : 0.97 + 0.03 * there }] };
    return CAN_FILTER ? { ...style, filter: [{ blur: reducedMotion ? 0 : SWAP_BLUR * (1 - there) }] as unknown as string } : style;
  });

  const stackStyle = useAnimatedStyle(() => {
    if (!CAN_FILTER || reducedMotion) return {};
    return { filter: [{ blur: ARRIVE_BLUR * viewBlur(clock.get(), toGrid.get() === 1) }] as unknown as string };
  });
  const gridStyle = useAnimatedStyle(() => {
    if (!CAN_FILTER || reducedMotion) return {};
    return { filter: [{ blur: ARRIVE_BLUR * viewBlur(clock.get(), toGrid.get() === 0) }] as unknown as string };
  });
  // The blur view covers both views, and by ARRIVE the one that left has faded: until then it is the
  // leaving view that blurs, after that the arriving one.
  const blurProps = useAnimatedProps(() => {
    if (reducedMotion) return { intensity: 0 };
    const t = clock.get();
    const amount = t < ARRIVE ? Math.min(1, (LEAVE_BLUR / ARRIVE_BLUR) * easeIn(t / LEAVE)) * (1 - smooth(t, LEAVE, ARRIVE)) : 1 - smooth(t, ARRIVE, ARRIVE + ARRIVE_CLEAR);
    const there = smooth(Math.abs(swap.get() - 0.5) * 2, 0, 1);
    return { intensity: BLUR_INTENSITY * Math.max(amount, 1 - there) };
  });

  const capsule = useAnimatedStyle(() => ({ transform: [{ translateX: slide.get() * (SEGMENT - SEGMENT_INSET) }] }));
  const pushed = useAnimatedStyle(() => ({ opacity: 1 - 0.35 * push.get(), transform: [{ scale: 1 - 0.14 * push.get() }] }));
  // The toggle shows the view that is on: lines for the stack, squares for the grid.
  const linesStyle = useAnimatedStyle(() => {
    const m = smooth(clock.get(), GLYPH_FROM, GLYPH_TO);
    const on = toGrid.get() === 1 ? 1 - m : m;
    return { opacity: on, transform: [{ scale: 0.7 + 0.3 * on }] };
  });
  const squaresStyle = useAnimatedStyle(() => {
    const m = smooth(clock.get(), GLYPH_FROM, GLYPH_TO);
    const on = toGrid.get() === 1 ? m : 1 - m;
    return { opacity: on, transform: [{ scale: 0.7 + 0.3 * on }] };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const select = (item: Sleeve) => {
    tick();
    onSelect?.(item);
  };

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="light" />
      <Svg style={StyleSheet.absoluteFill} width={width} height={height}>
        <Defs>
          <RadialGradient id="page" cx={width / 2} cy={height * 0.45} r={height * 0.55} gradientUnits="userSpaceOnUse">
            <Stop offset="0" stopColor={PAGE_LIGHT} />
            <Stop offset="1" stopColor={PAGE} />
          </RadialGradient>
        </Defs>
        <Rect width={width} height={height} fill="url(#page)" />
      </Svg>

      <Animated.View style={[StyleSheet.absoluteFill, content]}>
        <Animated.View
          style={[StyleSheet.absoluteFill, stackStyle]}
          pointerEvents={grid ? 'none' : 'auto'}
          accessibilityElementsHidden={grid}
          importantForAccessibility={grid ? 'no-hide-descendants' : 'auto'}
        >
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            {items.map((item, index) => (
              <SleeveView key={`${shown}-${item.title}`} index={index} item={item} id={`s${shown}-${index}`} frame={frame} motion={motion} still={reducedMotion} />
            ))}
          </View>
          {/* The sleeves are drawn from the scroll position; what scrolls is a column of clear rows that take the taps. */}
          <Animated.ScrollView
            ref={stackScroll}
            onScroll={onScroll}
            scrollEventThrottle={16}
            scrollEnabled={!grid}
            showsVerticalScrollIndicator={false}
            contentContainerStyle={{ paddingTop: frame.stackTop - PITCH / 2, paddingBottom: height - frame.eye - PITCH / 2 }}
          >
            {items.map((item, index) => (
              <Pressable
                key={`${shown}-${item.title}`}
                style={styles.row}
                onPressIn={() => lift(index, true)}
                onPressOut={() => lift(index, false)}
                onPress={() => select(item)}
                accessibilityRole="button"
                accessibilityLabel={`${item.title}, ${item.artist}`}
              />
            ))}
          </Animated.ScrollView>
        </Animated.View>

        <Animated.View
          style={[StyleSheet.absoluteFill, gridStyle]}
          pointerEvents={grid ? 'auto' : 'none'}
          accessibilityElementsHidden={!grid}
          importantForAccessibility={grid ? 'auto' : 'no-hide-descendants'}
        >
          <ScrollView
            ref={gridScroll}
            scrollEnabled={grid}
            showsVerticalScrollIndicator={false}
            contentContainerStyle={[styles.grid, { paddingTop: top + GRID_TOP, paddingBottom: bottom + DOCK_HEIGHT + 12 }]}
          >
            {items.map((item, index) => (
              <GridCover
                key={`${shown}-${item.title}`}
                index={index}
                count={items.length}
                item={item}
                id={`g${shown}-${index}`}
                size={cell}
                motion={motion}
                still={reducedMotion}
                onPress={() => select(item)}
              />
            ))}
          </ScrollView>
        </Animated.View>
      </Animated.View>

      {/* A light material whatever the app's appearance: the default one is dark in a dark app and darkens the page. */}
      {IOS && (
        <AnimatedBlurView pointerEvents="none" tint="systemUltraThinMaterialLight" style={StyleSheet.absoluteFill} animatedProps={blurProps} />
      )}

      <View style={[styles.bar, { top: top + BAR_TOP }]} pointerEvents="box-none">
        <Pressable style={styles.barButton} hitSlop={10} onPress={onSort} accessibilityRole="button" accessibilityLabel="Sort">
          <Glyph path={SORT} color="#ffffff" />
        </Pressable>
        <View style={styles.segments} accessibilityRole="tablist">
          <Animated.View style={[styles.capsule, capsule]} />
          {sections.slice(0, 2).map((section, index) => (
            <Pressable
              key={section.title}
              style={styles.segment}
              onPress={() => choose(index)}
              accessibilityRole="tab"
              accessibilityState={{ selected: segment === index }}
            >
              <Animated.Text
                style={[styles.segmentLabel, { color: segment === index ? SEGMENT_INK : SEGMENT_MUTE, transitionProperty: 'color', transitionDuration: SEGMENT_SLIDE }]}
                numberOfLines={1}
              >
                {section.title}
              </Animated.Text>
            </Pressable>
          ))}
        </View>
        <Pressable
          style={styles.barButton}
          hitSlop={10}
          onPress={() => flip(!grid, false)}
          accessibilityRole="button"
          accessibilityLabel={grid ? 'Show as a stack' : 'Show as a grid'}
        >
          <Animated.View style={[styles.barButton, pushed]}>
            <Animated.View style={[StyleSheet.absoluteFill, linesStyle]}>
              <Glyph path={LINES} color="#ffffff" />
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, squaresStyle]}>
              <Glyph path={SQUARES} color="#ffffff" stroke={1.6} />
            </Animated.View>
          </Animated.View>
        </Pressable>
      </View>

      <View style={[styles.dock, { left: (width - DOCK_WIDTH) / 2, bottom }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        {IOS && <BlurView tint="light" intensity={40} style={StyleSheet.absoluteFill} />}
        <View style={styles.dockFill} />
        <Svg width={24} height={24} viewBox="0 0 24 24">
          <Path d={DOCK_SLEEVES} stroke={DOCK_INK} strokeWidth={1.8} strokeLinecap="round" fill="none" />
          <Path d={DOCK_SLEEVE} fill={DOCK_INK} />
        </Svg>
        <Svg width={24} height={24} viewBox="0 0 24 24">
          <Circle cx={12} cy={12} r={8.2} stroke={DOCK_MUTE} strokeWidth={1.6} fill="none" />
          <Circle cx={9.2} cy={10} r={1} fill={DOCK_MUTE} />
          <Circle cx={14.8} cy={10} r={1} fill={DOCK_MUTE} />
          <Path d={DOCK_FACE} stroke={DOCK_MUTE} strokeWidth={1.6} strokeLinecap="round" fill="none" />
        </Svg>
        <Svg width={24} height={24} viewBox="0 0 24 24">
          <Path d={DOCK_LIST} stroke={DOCK_MUTE} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          <Circle cx={15.8} cy={16.8} r={1.9} stroke={DOCK_MUTE} strokeWidth={1.6} fill="none" />
        </Svg>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: PAGE,
  },
  sleeveCover: {
    position: 'absolute',
    top: 0,
    transformOrigin: 'bottom',
  },
  strip: {
    position: 'absolute',
    top: 0,
    height: STRIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stripText: {
    fontSize: 8.5,
    lineHeight: 12,
    includeFontPadding: false,
  },
  stripTitle: {
    fontWeight: '800',
  },
  row: {
    height: PITCH,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: GRID_GAP,
    paddingHorizontal: GRID_SIDE,
  },
  bar: {
    position: 'absolute',
    left: BAR_SIDE,
    right: BAR_SIDE,
    height: BAR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  barButton: {
    width: 24,
    height: 24,
  },
  segments: {
    width: SEGMENT * 2,
    height: BAR,
    borderRadius: BAR / 2,
    backgroundColor: SEGMENT_FILL,
    flexDirection: 'row',
  },
  capsule: {
    position: 'absolute',
    left: SEGMENT_INSET,
    top: SEGMENT_INSET,
    width: SEGMENT - SEGMENT_INSET,
    height: BAR - SEGMENT_INSET * 2,
    borderRadius: (BAR - SEGMENT_INSET * 2) / 2,
    backgroundColor: '#ffffff',
  },
  segment: {
    width: SEGMENT,
    height: BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  dock: {
    position: 'absolute',
    width: DOCK_WIDTH,
    height: DOCK_HEIGHT,
    borderRadius: DOCK_HEIGHT / 2,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-evenly',
  },
  dockFill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: DOCK_FILL,
  },
});
