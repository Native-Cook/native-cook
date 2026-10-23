import { Blur, Canvas, Circle, Group, Paint, Paragraph, Path, RadialGradient, Skia, rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type WheelIcon = 'card' | 'bars' | 'shield' | 'bolt' | 'globe';

export type WheelItem = { word: string; icon: WheelIcon };

type Props = {
  /** The words of the wheel, in the order they come into focus; the first one starts there. */
  items?: WheelItem[];
  headline?: string;
  caption?: string;
  primaryLabel?: string;
  secondaryLabel?: string;
  /** Behind the wheel. */
  background?: string;
  /** The words. */
  ink?: string;
  /** The light at the bottom, from its pale edge down to the deepest blue. */
  glow?: string[];
  /** Turns the wheel by one word every STEP seconds; a touch holds it for USER_HOLD seconds. */
  autoPlay?: boolean;
  /** Called with the place in `items` of the word that comes into focus. */
  onChange?: (index: number) => void;
  onPrimary?: () => void;
  onSecondary?: () => void;
};

const ITEMS: WheelItem[] = [
  { word: 'Spend', icon: 'card' },
  { word: 'Earn', icon: 'bars' },
  { word: 'Save', icon: 'shield' },
  { word: 'Pay', icon: 'bolt' },
  { word: 'Invest', icon: 'globe' },
];

const BACKGROUND = '#FFFFFF';
const INK = '#1C1C1E';
const GLOW = ['#CDEEFF', '#09BFFF', '#0093FB', '#166AFF'];
const BUTTON_INK = '#050505';

/** autoPlay: one word every STEP seconds, so the five words come round once a LOOP. */
export const STEP = 1.6;
export const LOOP = 8;
/** After a touch the wheel waits this long, then turns back to the first word and starts over. */
const USER_HOLD = 3;

// The roll: it overshoots the row by about two points and has settled in 0.45 s.
const SPRING = { mass: 1, stiffness: 180, damping: 20, reduceMotion: ReduceMotion.Never };
// A flick carries the wheel on by this many seconds of its speed.
const COAST = 0.18;

// The design is 393 points wide; the light's widths scale with the screen.
const DESIGN_WIDTH = 393;
const LEFT = 56;
const ROW = 72;
const ICON = 28;
const ICON_GAP = 12;
const WORD = 34;
const WORD_LINE = 42;
// Skia draws the system font in its text cut at every size, which is wider than the display cut
// SwiftUI uses at this size; this much less between the letters makes a word as long as there.
const WORD_TIGHTEN = 1.5;
// Two and a half rows show on each side of the focus line.
const WHEEL = ROW * 5;
// A word one row from the focus line, two rows from it, and how soft each row away makes it.
const DIM_NEAR = 0.12;
const DIM_FAR = 0.05;
const SOFTEN = 1.6;
// The icon of the word that comes into focus grows from this size, past its own, and settles.
const POP_FROM = 0.6;
const POP_BACK = 3;

// The light is one large ellipse whose centre lies below the screen, so its upper edge is a dome
// that stands higher in the middle than at the sides.
const DOME_RX = 553;
const DOME_RY = 576;
const DOME_BELOW = 119;
// Where the colours sit from the centre out to the edge: deepest, deepest, blue, cyan, pale, background.
const DOME_STOPS = [0, 0.4, 0.644, 0.852, 0.94, 1];
// The dome rises and falls, and a brighter patch in it drifts from side to side.
const DOME_RISE = 10;
const DOME_PERIOD = 4;
const SPOT_RX = 260;
const SPOT_RY = 130;
const SPOT_ABOVE = 332;
const SPOT_DRIFT = 20;
const SPOT_PERIOD = 8;
const SPOT_ALPHA = 0.55;
// cos², so the patch has no rim.
const SPOT_AT = [0, 0.2, 0.4, 0.6, 0.8, 1];
const SPOT_FALLOFF = [1, 0.905, 0.655, 0.345, 0.095, 0];
// The dome's top, and the focus line above it.
const GLOW_TOP = DOME_RY - DOME_BELOW;
const FOCUS_ABOVE = 138;

const TILE = 40;
// Half of what the headline's 34-point line adds to its font's own 31, and the caption's 17 to 15.
const HEADLINE_LEAD = 1.5;
const CAPTION_LEAD = 1;
const BUTTON = 50;
const BUTTON_SIDE = 40;
const PRESS_SCALE = 0.97;
const PRESS_MS = 130;

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

function box(x: number, y: number, w: number, h: number, r: number) {
  return `M${x + r} ${y}h${w - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}v${h - 2 * r}a${r} ${r} 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a${r} ${r} 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a${r} ${r} 0 0 1 ${r} ${-r}Z`;
}

function oval(cx: number, cy: number, rx: number, ry: number) {
  return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0Z`;
}

type Part = { d: string; color: string; stroke?: number };

// The five icons, each in a 28 × 28 box.
const GLYPHS: Record<WheelIcon, Part[]> = {
  card: [
    { d: box(3, 6, 22, 16, 4), color: '#7B5CFF' },
    { d: 'M3 10.5h22v3.5h-22Z', color: '#CFC4FF' },
    { d: box(6.5, 16.5, 6, 2.5, 1.25), color: '#CFC4FF' },
  ],
  bars: [
    { d: box(3, 14, 6, 11, 2), color: '#FFB286' },
    { d: box(11, 9, 6, 16, 2), color: '#FF9E62' },
    { d: box(19, 4, 6, 21, 2), color: '#FF8A3D' },
  ],
  shield: [
    { d: 'M14 2.5L24 6V13.2C24 19.4 19.9 23.7 14 25.8C8.1 23.7 4 19.4 4 13.2V6Z', color: '#35C8F5' },
    { d: 'M14 2.5L4 6V13.2C4 19.4 8.1 23.7 14 25.8Z', color: '#7FDDFA' },
  ],
  bolt: [{ d: 'M16.5 2L5.5 15.5H12.5L11 26L22.5 12H15.3Z', color: '#FF4D4D' }],
  globe: [
    { d: oval(14, 14, 11.5, 11.5), color: '#6C7BFF' },
    { d: oval(14, 14, 4.8, 11.5), color: '#DDE1FF', stroke: 1.6 },
    { d: 'M2.5 14H25.5', color: '#DDE1FF', stroke: 1.6 },
  ],
};

// The app's mark on its tile: two joined dots and two loose ones, in a 40 × 40 box.
const MARK_LINK = 'M15 15V25';
const MARK_DOTS = [
  [15, 15, 3],
  [15, 25, 3],
  [25, 15, 3],
  [25, 25, 2],
];

/** How far row `index` is from the focus line, in rows; the wheel repeats every `count` rows. */
function offsetOf(index: number, position: number, count: number) {
  'worklet';
  const d = index - position;
  return d - count * Math.round(d / count);
}

function dimOf(d: number) {
  'worklet';
  const a = Math.abs(d);
  if (a <= 1) return 1 - (1 - DIM_NEAR) * a;
  if (a <= 2) return DIM_NEAR - (DIM_NEAR - DIM_FAR) * (a - 1);
  return Math.max(0, DIM_FAR * (3 - a));
}

/** The icon's size: it pops as its word arrives from below, and shrinks as the word leaves upwards. */
function popOf(d: number) {
  'worklet';
  const u = Math.min(1, Math.abs(d));
  if (d < 0) return 1 - (1 - POP_FROM) * u;
  const back = 1 + (POP_BACK + 1) * Math.pow(-u, 3) + POP_BACK * u * u;
  return POP_FROM + (1 - POP_FROM) * back;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function wordParagraph(word: string, color: string, width: number) {
  const builder = Skia.ParagraphBuilder.Make({ maxLines: 1 });
  builder.pushStyle({
    color: Skia.Color(color),
    fontFamilies: [FONT],
    fontSize: WORD,
    fontStyle: { weight: 500 },
    letterSpacing: -0.5 - WORD_TIGHTEN,
    heightMultiplier: WORD_LINE / WORD,
    halfLeading: true,
  });
  builder.addText(word);
  const built = builder.build();
  built.layout(width);
  return built;
}

type RowProps = { index: number; count: number; item: WheelItem; position: SharedValue<number>; ink: string; width: number };

/** One row of the wheel. A row keeps its word; it is drawn at the nearest of its places on the endless wheel. */
function Row({ index, count, item, position, ink, width }: RowProps) {
  const paragraph = useMemo(() => wordParagraph(item.word, ink, width), [item.word, ink, width]);
  const d = useDerivedValue(() => offsetOf(index, position.get(), count));
  const place = useDerivedValue(() => [{ translateY: WHEEL / 2 + d.get() * ROW }]);
  const opacity = useDerivedValue(() => dimOf(d.get()));
  const soft = useDerivedValue(() => Math.min(2, Math.abs(d.get())) * SOFTEN);
  const pop = useDerivedValue(() => [{ scale: popOf(d.get()) }]);
  return (
    <Group transform={place}>
      <Group
        clip={rect(0, -ROW / 2, width, ROW)}
        layer={
          <Paint opacity={opacity}>
            <Blur blur={soft} />
          </Paint>
        }
      >
        <Group transform={pop} origin={vec(LEFT + ICON / 2, 0)}>
          <Group transform={[{ translateX: LEFT }, { translateY: -ICON / 2 }]}>
            {GLYPHS[item.icon].map((part, i) => (
              <Path key={i} path={part.d} color={part.color} style={part.stroke ? 'stroke' : 'fill'} strokeWidth={part.stroke ?? 0} strokeCap="round" />
            ))}
          </Group>
        </Group>
        <Paragraph paragraph={paragraph} x={LEFT + ICON + ICON_GAP} y={-WORD_LINE / 2} width={width} />
      </Group>
    </Group>
  );
}

type GlowProps = { clock: SharedValue<number>; width: number; height: number; background: string; colors: string[] };

/** The light at the bottom of the screen: a dome that breathes, and a brighter patch drifting in it. */
function Glow({ clock, width, height, background, colors }: GlowProps) {
  const k = width / DESIGN_WIDTH;
  const dome = useDerivedValue(() => [
    { translateX: width / 2 },
    { translateY: height + DOME_BELOW + DOME_RISE * Math.sin((clock.get() / DOME_PERIOD) * 2 * Math.PI) },
    { scaleX: DOME_RX * k },
    { scaleY: DOME_RY },
  ]);
  const spot = useDerivedValue(() => [
    { translateX: width / 2 + SPOT_DRIFT * Math.sin((clock.get() / SPOT_PERIOD) * 2 * Math.PI) },
    { translateY: height - SPOT_ABOVE },
    { scaleX: SPOT_RX * k },
    { scaleY: SPOT_RY },
  ]);
  const [pale, cyan, blue, deep] = colors;
  const spotColors = useMemo(() => {
    const [r, g, b] = Skia.Color(cyan);
    return SPOT_FALLOFF.map((f) => Skia.Color(`rgba(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)},${SPOT_ALPHA * f})`));
  }, [cyan]);
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Group transform={dome}>
        <Circle cx={0} cy={0} r={1} dither>
          <RadialGradient c={vec(0, 0)} r={1} colors={[deep, deep, blue, cyan, pale, background]} positions={DOME_STOPS} />
        </Circle>
      </Group>
      <Group transform={spot}>
        <Circle cx={0} cy={0} r={1} dither>
          <RadialGradient c={vec(0, 0)} r={1} colors={spotColors} positions={SPOT_AT} />
        </Circle>
      </Group>
    </Canvas>
  );
}

type PillProps = { label: string; filled: boolean; onPress: () => void };

function Pill({ label, filled, onPress }: PillProps) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button">
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.pill,
            filled ? styles.pillFilled : styles.pillGlass,
            { transform: [{ scale: pressed ? PRESS_SCALE : 1 }], transitionProperty: 'transform', transitionDuration: PRESS_MS },
          ]}
        >
          <Text style={[styles.pillLabel, filled ? styles.pillLabelFilled : styles.pillLabelGlass]} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A sign-up entry screen. In the white upper half a wheel of single words turns one row at a
 * time: the word on the focus line is sharp and dark with its icon, the rows around it are faint
 * and soft, and the list never ends. Below, a blue light breathes behind the headline and the two
 * buttons. The wheel can be dragged, and a faint word can be tapped into focus.
 */
export function ValueWordWheel({
  items = ITEMS,
  headline = 'Your money,\nupgraded',
  caption = 'Save, earn and invest\nwith one account for everything.',
  primaryLabel = 'Get started',
  secondaryLabel = 'I already have an account',
  background = BACKGROUND,
  ink = INK,
  glow = GLOW,
  autoPlay = false,
  onChange,
  onPrimary,
  onSecondary,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const count = items.length;
  // Enough rows to fill the wheel however few words there are, and a whole number of rounds.
  const rows = count * Math.ceil(8 / count);

  // The wheel's place in rows: row n is on the focus line when it is n. It only ever grows or
  // shrinks; the words repeat every `count` rows.
  const position = useSharedValue(0);
  const grab = useSharedValue(0);
  const grabbed = useSharedValue(false);
  // Seconds, round and round, for the light.
  const clock = useSharedValue(0);
  // The row the wheel is on, or on its way to.
  const target = useSharedValue(0);
  // Its word, for the screen reader.
  const [focus, setFocus] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // A finger is on the wheel: the loop waits.
  const [holding, setHolding] = useState(false);

  useEffect(() => {
    if (still) return;
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false, undefined, ReduceMotion.Never));
    return () => cancelAnimation(clock);
  }, [clock, still]);

  useEffect(() => () => cancelAnimation(position), [position]);

  /** Turns the wheel to row `to`, carrying on at `speed` rows a second. */
  const roll = (to: number, speed = 0) => {
    target.set(to);
    // Reduced motion: the word changes in place.
    position.set(still ? to : withSpring(to, { ...SPRING, velocity: speed }));
    const index = ((to % count) + count) % count;
    setFocus(index);
    onChange?.(index);
  };

  /** A touch takes over from the autoPlay loop, which starts again from the first word a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ roll, count });
  useEffect(() => {
    latest.current = { roll, count };
  });

  useEffect(() => {
    if (!autoPlay || holding) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // Step n happens n × STEP after the origin, however late the one before it fired.
    const step = (origin: number, n: number) =>
      after(origin + n * STEP * 1000 - Date.now(), () => {
        latest.current.roll(target.get() + 1);
        step(origin, n + 1);
      });
    if (touches === 0) step(Date.now(), 1);
    else
      after(USER_HOLD * 1000, () => {
        const round = latest.current.count;
        latest.current.roll(Math.round(target.get() / round) * round);
        step(Date.now(), 1);
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, holding, target]);

  const settle = (to: number, speed: number) => {
    setHolding(false);
    roll(to, speed);
    touched();
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-6, 6])
    .onStart((event) => {
      cancelAnimation(position);
      // Where the wheel would be with the finger where the touch began.
      grab.set(position.get() + event.translationY / ROW);
      grabbed.set(true);
      scheduleOnRN(setHolding, true);
    })
    .onUpdate((event) => {
      const to = grab.get() - event.translationY / ROW;
      // One tick for each row that crosses the focus line.
      if (Math.round(to) !== Math.round(position.get())) scheduleOnRN(tick);
      position.set(to);
    })
    .onFinalize((event) => {
      // Also when the system takes the touch away: the wheel never stays between two rows.
      if (!grabbed.get()) return;
      grabbed.set(false);
      const speed = -event.velocityY / ROW;
      scheduleOnRN(settle, Math.round(position.get() + speed * COAST), speed);
    });

  const pick = (rowsAway: number) => {
    if (rowsAway !== 0) tick();
    settle(target.get() + rowsAway, 0);
  };

  const press = Gesture.Tap().onEnd((event) => {
    scheduleOnRN(pick, Math.round((event.y - WHEEL / 2) / ROW));
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const focusY = Math.max(size.height - GLOW_TOP - FOCUS_ABOVE, insets.top + 100);
  const bottom = Math.max(insets.bottom + 6, 24);

  return (
    <View style={[styles.screen, { backgroundColor: background }]} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Glow clock={clock} width={size.width} height={size.height} background={background} colors={glow} />

      <GestureDetector gesture={Gesture.Exclusive(pan, press)}>
        <View
          style={[styles.wheel, { top: focusY - WHEEL / 2 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityValue={{ text: items[focus].word }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => pick(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            {Array.from({ length: rows }, (_, index) => (
              <Row key={index} index={index} count={rows} item={items[index % count]} position={position} ink={ink} width={size.width} />
            ))}
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.foot, { paddingBottom: bottom }]} pointerEvents="box-none">
        <View style={styles.tile}>
          <Canvas style={styles.mark}>
            <Path path={MARK_LINK} style="stroke" strokeWidth={2.4} strokeCap="round" color={INK} />
            {MARK_DOTS.map(([cx, cy, r], i) => (
              <Circle key={i} cx={cx} cy={cy} r={r} color={INK} />
            ))}
          </Canvas>
        </View>
        <Text style={styles.headline}>{headline}</Text>
        <Text style={styles.caption}>{caption}</Text>
        <View style={styles.buttons}>
          <Pill
            label={primaryLabel}
            filled
            onPress={() => {
              tap();
              onPrimary?.();
            }}
          />
          <Pill
            label={secondaryLabel}
            filled={false}
            onPress={() => {
              tap();
              onSecondary?.();
            }}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  wheel: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: WHEEL,
  },
  foot: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  tile: {
    width: TILE,
    height: TILE,
    marginLeft: LEFT,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: '#FFFFFF',
  },
  mark: {
    width: TILE,
    height: TILE,
  },
  // A line box is taller than the font's own line and the text sits in its middle, so the first and
  // the last line each carry half the difference; the margins take it back.
  headline: {
    marginTop: 18 - HEADLINE_LEAD,
    marginBottom: -HEADLINE_LEAD,
    marginHorizontal: LEFT,
    color: '#FFFFFF',
    fontSize: 26,
    lineHeight: 34,
    fontWeight: '500',
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  caption: {
    marginTop: 14 - CAPTION_LEAD,
    marginBottom: -CAPTION_LEAD,
    marginHorizontal: LEFT,
    color: 'rgba(255,255,255,0.6)',
    fontSize: 12.5,
    lineHeight: 17,
    includeFontPadding: false,
  },
  buttons: {
    marginTop: 40,
    marginHorizontal: BUTTON_SIDE,
    gap: 12,
  },
  pill: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillFilled: {
    backgroundColor: '#FFFFFF',
  },
  pillGlass: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  pillLabel: {
    fontWeight: '600',
    includeFontPadding: false,
  },
  pillLabelFilled: {
    color: BUTTON_INK,
    fontSize: 15,
    lineHeight: 20,
  },
  pillLabelGlass: {
    color: '#FFFFFF',
    fontSize: 14,
    lineHeight: 19,
  },
});
