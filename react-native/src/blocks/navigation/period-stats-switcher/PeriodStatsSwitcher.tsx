import { MaterialIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, type TextStyle, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeInDown,
  FadeOut,
  ReduceMotion,
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** A day or a month to remember: its figure and when it was. */
export type Mark = { value: number; when: string; caption: string };

/** Everything the screen shows for one period. */
export type Stats = {
  /** The switch's word for it. */
  name: string;
  /** What the row under the switch calls this period, then the ones before it. */
  labels: string[];
  total: number;
  distance: number;
  floors: number;
  calories: number;
  /** The chart's top value. */
  scale: number;
  /** Bar heights as fractions of the chart, one to a slot; 0 leaves the slot empty. */
  bars: number[];
  /** Slots whose goal was not met, drawn grey. */
  missed: number[];
  /** Labels under the chart: the slot each one stands under. */
  axis: { at: number; text: string; strong?: boolean }[];
  average: number;
  averageCaption: string;
  /** This period against the last one: the difference and its percentage. */
  change: number;
  percent: number;
  changeCaption: string;
  best?: Mark;
  worst?: Mark;
};

type Props = {
  /** The periods of the switch, shortest first; the first one shows the goal disc. */
  periods?: Stats[];
  /** The first period's goal, written in its disc. */
  goal?: number;
  unit?: string;
  totalCaption?: string;
  /** Goes from period to period by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the period's place in `periods` when the switch moves. */
  onSelect?: (index: number) => void;
  onClose?: () => void;
};

const MONTH_BARS = [
  0.52, 0.68, 0.61, 0.8, 0.47, 0.74, 0.9, 0.63, 0.95, 0.3, 0.58, 0.86, 0.71, 0.66, 0.92, 0.55, 0.78, 0.84, 0.6, 0.49, 0.73, 0.88, 0.64, 0.57,
  0.7, 0.81, 0.62, 0.76, 0, 0,
];

const PERIODS: Stats[] = [
  {
    name: 'Week',
    labels: ['Today, 28 Aug', '19 – 25 Aug', '12 – 18 Aug', '5 – 11 Aug'],
    total: 5052,
    distance: 3.4,
    floors: 19,
    calories: 278,
    scale: 5984,
    bars: [0.85, 0.75, 0.73, 0, 0, 0, 0],
    missed: [],
    axis: ['26\nMon', '27\nTue', '28\nWed', '29\nThu', '30\nFri', '31\nSat', '1\nSun'].map((text, at) => ({ at, text, strong: at === 2 })),
    average: 5408,
    averageCaption: 'Daily avg.',
    change: -391,
    percent: -7,
    changeCaption: 'vs last week avg.',
  },
  {
    name: 'Month',
    labels: ['Aug 2024', 'Jul 2024', 'Jun 2024', 'May 2024'],
    total: 162779,
    distance: 110.5,
    floors: 332,
    calories: 3262,
    scale: 9525,
    bars: MONTH_BARS,
    missed: [8, 9],
    axis: [0, 4, 8, 12, 16, 20, 24, 28].map((at) => ({ at, text: String(at + 1) })),
    average: 5813,
    averageCaption: 'Daily avg.',
    change: -583,
    percent: -10,
    changeCaption: 'vs last month avg.',
    best: { value: 9525, when: '9 Aug', caption: 'Best day' },
    worst: { value: 1441, when: '10 Aug', caption: 'Worst day' },
  },
  {
    name: 'Year',
    labels: ['2024', '2023', '2022', '2021'],
    total: 1172691,
    distance: 811.3,
    floors: 2382,
    calories: 13075,
    scale: 198312,
    bars: [0.6, 0.64, 0.66, 0.74, 0.8, 0.86, 0.95, 0.7, 0, 0, 0, 0],
    missed: [],
    axis: ['Jan', 'Mar', 'May', 'Jul', 'Sep', 'Nov'].map((text, index) => ({ at: index * 2, text })),
    average: 146586,
    averageCaption: 'Monthly avg.',
    change: 17766,
    percent: 12,
    changeCaption: 'vs last year avg.',
    best: { value: 198312, when: 'Jul', caption: 'Best month' },
    worst: { value: 110127, when: 'Jan', caption: 'Worst month' },
  },
];

// Earlier periods are made from the given one: every figure times one of these, the bars turned round.
const EARLIER = [1, 0.86, 1.12, 0.93];
const TURN = 3;

const PAGE = '#F1F0F7';
const NAVY = '#212557';
const INK = '#111111';
const MUTE = '#8D8CA0';
const FAINT = '#C8C7D4';
const TRACK = '#E4E3EC';
const SEGMENT_INK = '#55546A';
const GREEN = '#26CE47';
const PANEL = '#DFEDE8';
const PANEL_INK = '#7FA08F';
const MISSED = '#C3D2CB';
const RED = '#E5484D';
const GOLD = '#F2B022';
const STRIP = '#E8E7EF';

const MARGIN = 24;
// From the safe area's top.
const SWITCH_TOP = 20;
const SWITCH = 30;
const SWITCH_LEFT = 60;
const SWITCH_INSET = 2;
const PERIOD_TOP = 67;
const DISC_TOP = 116;
const DISC = 226;
const RING = 12;
const FIGURE_TOP = 100;
const FIGURE = 46;
const FIGURE_LINE = 52;
// Everything from the totals down: where it starts under the disc, and how far it rises without it.
const BODY_TOP = 360;
const BODY_RISE = 170;
// Inside that body.
const CHART_TOP = 62;
const CHART = 160;
const BAR_WIDEST = 30;
const BAR_SHARE = 0.62;
const AXIS_TOP = CHART_TOP + CHART + 8;
const LEGEND_TOP = CHART_TOP + CHART + 44;
const CARDS_TOP = LEGEND_TOP + 48;
const CARD = 92;
const CARD_TALL = 106;
const CARD_GAP = 12;

// The switch's capsule and the layout follow the period on a quick spring and a 350 ms ease.
const CAPSULE_SPRING = { mass: 1, stiffness: 420, damping: 34 };
const SHIFT_FOR = 350;
// The disc comes back as a ring that fills, then turns solid.
const RING_AFTER = 150;
const RING_FOR = 800;
const SOLID_FROM = 0.85;
// A digit column turns to its new digit; columns start one after another, left to right.
const ROLL_FOR = 650;
const ROLL_STEP = 40;
const ROLL_EASE = Easing.bezier(0.23, 1, 0.32, 1);
// The chart's clock, in seconds: the old bars sink, then the new ones grow one after another on a
// spring that passes their height by about 6%.
const SINK = 0.15;
const BAR_STEP = 0.012;
const BAR_OMEGA = 20;
const BAR_ZETA = 0.667;
const BAR_REST = 0.9;
const CHART_FOR = SINK + 30 * BAR_STEP + BAR_REST;
const CROSS = 0.25;
const SWAP = 250;
// The second row of cards follows the first.
const ROW_AFTER = 60;
const ROW_RISE = 10;
const SWIPE = 50;

/** autoPlay: Week, Month, Year and back to Week. */
export const LOOP = 10;
const STEPS = [
  { at: 2.4, to: 1 },
  { at: 4.8, to: 2 },
  { at: 7.4, to: 0 },
];
/** After a touch autoPlay waits this long before it goes back to the first period and starts over. */
const USER_HOLD = 3;

function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** How much of its height a bar has `t` seconds after it started to grow. */
function grownAt(t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= BAR_REST) return 1;
  const damped = BAR_OMEGA * Math.sqrt(1 - BAR_ZETA * BAR_ZETA);
  return 1 - Math.exp(-BAR_ZETA * BAR_OMEGA * t) * (Math.cos(damped * t) + ((BAR_ZETA * BAR_OMEGA) / damped) * Math.sin(damped * t));
}

function grouped(value: number) {
  return String(Math.round(Math.abs(value))).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** The period as it was `back` steps ago. */
function earlier(stats: Stats, back: number): Stats {
  if (back === 0) return stats;
  const by = EARLIER[back % EARLIER.length];
  const count = stats.bars.length;
  return {
    ...stats,
    total: stats.total * by,
    distance: stats.distance * by,
    floors: stats.floors * by,
    calories: stats.calories * by,
    average: stats.average * by,
    change: -stats.change * by,
    percent: -stats.percent,
    bars: stats.bars.map((_, slot) => stats.bars[(slot + back * TURN) % count]),
    missed: stats.missed.map((slot) => (slot + count - ((back * TURN) % count)) % count),
  };
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One digit of a rolling figure: a strip of the ten digits behind a one-line window. */
function Digit({ digit, order, line, style }: { digit: number; order: number; line: number; style: TextStyle }) {
  const at = useSharedValue(digit);

  useEffect(() => {
    at.set(withDelay(order * ROLL_STEP, withTiming(digit, { duration: ROLL_FOR, easing: ROLL_EASE })));
  }, [digit, order, at]);

  const stripStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -at.get() * line }] }));

  return (
    // Wider than the digit by the padding, so that an italic is not cut at its sides.
    <View style={[styles.window, { height: line }]}>
      <Animated.View style={stripStyle}>
        {DIGITS.map((text) => (
          <Text key={text} style={[style, { height: line, lineHeight: line }]}>
            {text}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

/** A figure whose digits roll to a new value, the leftmost first. */
function Roll({ text, line, style }: { text: string; line: number; style: TextStyle }) {
  const characters = text.split('');
  return (
    <View style={styles.roll} accessible accessibilityLabel={text}>
      {characters.map((character, index) =>
        // A column keeps its place from the right, so that a longer figure grows at the left.
        character >= '0' && character <= '9' ? (
          <Digit key={characters.length - index} digit={Number(character)} order={index} line={line} style={style} />
        ) : (
          <Text key={`${characters.length - index}${character}`} style={[style, { height: line, lineHeight: line }]}>
            {character}
          </Text>
        ),
      )}
    </View>
  );
}

/**
 * A bar of the chart. Its set came in at the clock's whole number `born`: it grows while the clock
 * runs to the next one and sinks when the clock goes on from there.
 */
function Bar({
  clock,
  born,
  still,
  order,
  left,
  width,
  height,
  color,
}: {
  clock: SharedValue<number>;
  born: number;
  still: boolean;
  order: number;
  left: number;
  width: number;
  height: number;
  color: string;
}) {
  const barStyle = useAnimatedStyle(() => {
    // Seconds since this bar's set came in, and since the set after it did.
    const t = (clock.get() - born) * CHART_FOR;
    const gone = t - CHART_FOR;
    // Reduced motion: the two sets cross-fade at full height.
    if (still) return { opacity: clamp01(t / CROSS) * (1 - clamp01(gone / CROSS)), transform: [{ scaleY: 1 }] };
    return { opacity: 1, transform: [{ scaleY: grownAt(t - SINK - order * BAR_STEP) * (1 - clamp01(gone / SINK)) }] };
  });
  return <Animated.View style={[styles.bar, { left, width, height, backgroundColor: color }, barStyle]} />;
}

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/**
 * A statistics screen under a three-way switch. Changing the period rebuilds what is under it:
 * the goal disc gives way to a large rolling total and the page closes up, every figure rolls to
 * its new digits, the old bars sink and a new set grows from the baseline, left to right.
 */
export function PeriodStatsSwitcher({
  periods = PERIODS,
  goal = 3000,
  unit = 'steps',
  totalCaption = 'Total Walk',
  autoPlay = false,
  onSelect,
  onClose,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width } = laidOut ?? window;
  const [period, setPeriod] = useState(0);
  // How many periods back the arrows have gone.
  const [back, setBack] = useState(0);
  // The bars that are leaving the chart.
  const [left, setLeft] = useState<Stats | null>(null);
  // How many times the chart has changed its bars.
  const [turn, setTurn] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The capsule's place in the switch, counted in segments.
  const capsule = useSharedValue(0);
  // 1 while the first period, with its disc, is shown.
  const disc = useSharedValue(1);
  const ring = useSharedValue(1);
  // The chart's clock: one whole number for every set of bars, which grows while the clock runs
  // from its number to the next. Each bar reads its own stretch of it, so the picture is right
  // whether the clock or the render with the new set comes first.
  const clock = useSharedValue(1);

  const stats = earlier(periods[period], back);
  const first = period === 0;
  const segment = (width - SWITCH_LEFT - MARGIN - SWITCH_INSET * 2) / periods.length;
  const circumference = Math.PI * (DISC - RING);

  /** Shows a period, `steps` back: the figures roll by themselves, the chart's clock starts over. */
  const show = (index: number, steps: number) => {
    if (index === period && steps === back) return;
    setLeft(stats);
    setPeriod(index);
    setBack(steps);
    setTurn(turn + 1);
    clock.set(turn + 1);
    clock.set(withTiming(turn + 2, { duration: CHART_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    if (index === period) return;
    capsule.set(withSpring(index, CAPSULE_SPRING));
    disc.set(withTiming(index === 0 ? 1 : 0, { duration: SHIFT_FOR }));
    if (index === 0) {
      ring.set(0);
      ring.set(withDelay(RING_AFTER, withTiming(1, { duration: RING_FOR, easing: Easing.out(Easing.quad) })));
    }
    onSelect?.(index);
  };

  const select = (index: number) => {
    if (index === period) return;
    tick();
    show(index, 0);
  };

  const step = (by: number) => {
    const next = back + by;
    if (next < 0 || next >= periods[period].labels.length) return;
    tick();
    show(period, next);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ show });
  useEffect(() => {
    latest.current = { show };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const { at, to } of STEPS) after(origin + at * 1000 - Date.now(), () => latest.current.show(to, 0));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      // The loop starts over from the first period, once it has settled there.
      after(USER_HOLD * 1000, () => {
        latest.current.show(0, 0);
        after((RING_AFTER + RING_FOR) * 2, () => cycle(Date.now()));
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const swiped = (by: number) => {
    const next = period + by;
    if (next >= 0 && next < periods.length) select(next);
  };

  const swipe = Gesture.Pan()
    .activeOffsetX([-20, 20])
    .failOffsetY([-12, 12])
    .onEnd((event) => {
      if (Math.abs(event.translationX) > SWIPE) scheduleOnRN(swiped, event.translationX < 0 ? 1 : -1);
    });

  const capsuleStyle = useAnimatedStyle(() => ({ transform: [{ translateX: capsule.get() * segment }] }));
  const discStyle = useAnimatedStyle(() => {
    const shown = disc.get();
    return {
      opacity: shown * shown,
      transform: [{ translateX: -(1 - shown) * (width / 2 - MARGIN - DISC * 0.1) }, { scale: mix(0.2, 1, shown) }],
    };
  });
  const totalStyle = useAnimatedStyle(() => ({ opacity: clamp01(1 - disc.get() * 2) }));
  const bodyStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -BODY_RISE * (1 - disc.get()) }] }));
  const solidStyle = useAnimatedStyle(() => ({ opacity: clamp01((ring.get() - SOLID_FROM) / (1 - SOLID_FROM)) }));
  const ringProps = useAnimatedProps(() => ({ strokeDashoffset: circumference * (1 - ring.get()) }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const slot = (width - MARGIN * 2) / stats.bars.length;
  const cardWidth = (width - MARGIN * 2 - CARD_GAP) / 2;
  const up = stats.change >= 0;
  const week = earlier(periods[0], first ? back : 0);
  // Away from the first period the disc holds zeros, so that its figure counts up when it returns.
  const discFigure = first ? grouped(week.total) : grouped(week.total).replace(/\d/g, '0');
  const totals = [
    { figure: stats.distance.toFixed(1), unit: 'KM', caption: 'Total distance' },
    { figure: grouped(stats.floors), unit: '', caption: 'Total floors' },
    { figure: grouped(stats.calories), unit: 'kcal', caption: 'Total calories' },
  ];

  const bars = (set: Stats, born: number) => {
    const each = (width - MARGIN * 2) / set.bars.length;
    const wide = Math.min(BAR_WIDEST, each * BAR_SHARE);
    return set.bars.map((share, index) =>
      share > 0 ? (
        <Bar
          key={`${born}.${index}`}
          clock={clock}
          born={born}
          still={reducedMotion}
          order={index}
          left={MARGIN + each * index + (each - wide) / 2}
          width={wide}
          height={CHART * share}
          color={set.missed.includes(index) ? MISSED : GREEN}
        />
      ) : null,
    );
  };

  return (
    <GestureDetector gesture={swipe}>
      <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
        <StatusBar style="dark" />

        <Animated.View style={[styles.body, { top: insets.top + BODY_TOP }, bodyStyle]}>
          <View style={styles.totals}>
            {totals.map((item) => (
              <View key={item.caption} style={styles.totalsColumn}>
                <View style={styles.totalsFigure}>
                  <Roll text={item.figure} line={28} style={styles.totalFigure} />
                  <Text style={styles.totalUnit}>{item.unit}</Text>
                </View>
                <Text style={styles.caption}>{item.caption}</Text>
              </View>
            ))}
          </View>

          <View style={styles.chart}>
            <Animated.Text key={stats.scale} style={styles.scale} entering={FadeIn.duration(SWAP)} exiting={FadeOut.duration(SWAP)}>
              {grouped(stats.scale)}
            </Animated.Text>
            {/* One list, keyed by set: the bars that leave stay the ones they were while they sink. */}
            {[...(left !== null ? bars(left, turn - 1) : []), ...bars(stats, turn)]}
          </View>
          <Animated.View key={`axis${period}`} style={styles.axis} entering={FadeIn.duration(SWAP)} exiting={FadeOut.duration(SWAP)}>
            {stats.axis.map((label) => (
              <Text key={label.at} style={[styles.axisLabel, label.strong && styles.axisStrong, { left: MARGIN + slot * label.at + slot / 2 - 30 }]}>
                {label.text}
              </Text>
            ))}
          </Animated.View>
          <View style={styles.legend}>
            <View style={styles.legendLine} />
            <Text style={styles.legendLabel}>Goal line</Text>
            <View style={[styles.legendMark, { backgroundColor: GREEN }]} />
            <Text style={styles.legendLabel}>Goal meet</Text>
            <View style={[styles.legendMark, { backgroundColor: MISSED }]} />
            <Text style={styles.legendLabel}>Goal not meet</Text>
          </View>

          <View style={styles.cards}>
            <View style={[styles.card, { width: cardWidth }]}>
              <MaterialIcons name="directions-run" size={22} color={GREEN} />
              <Roll text={grouped(stats.average)} line={28} style={styles.cardFigure} />
              <Text style={styles.caption}>{stats.averageCaption}</Text>
            </View>
            <View style={[styles.card, { width: cardWidth }]}>
              <View style={[styles.badge, { backgroundColor: up ? GREEN : RED }]}>
                <MaterialIcons name={up ? 'north-east' : 'south-west'} size={14} color="#FFFFFF" />
              </View>
              <Text style={[styles.percent, { color: up ? GREEN : RED }]}>
                {up ? '+' : '-'}
                {Math.abs(stats.percent)}%
              </Text>
              <View style={styles.signed}>
                <Text style={[styles.cardFigure, styles.sign, { color: up ? GREEN : RED }]}>{up ? '+' : '-'}</Text>
                <Roll text={grouped(stats.change)} line={28} style={{ ...styles.cardFigure, color: up ? GREEN : RED }} />
              </View>
              <Text style={styles.caption}>{stats.changeCaption}</Text>
            </View>
          </View>

          {/* The second row belongs to the longer periods; it follows the first in and out. */}
          {stats.best && stats.worst && (
            <Animated.View
              key={`row${period}`}
              style={[styles.cards, styles.cardsBelow]}
              entering={FadeInDown.duration(SWAP).delay(ROW_AFTER).withInitialValues({ transform: [{ translateY: ROW_RISE }] })}
              exiting={FadeOut.duration(SINK * 1000)}
            >
              {[stats.best, stats.worst].map((mark, index) => (
                <View key={mark.caption} style={[styles.card, styles.cardTall, { width: cardWidth }]}>
                  <MaterialIcons name={index === 0 ? 'emoji-events' : 'sentiment-dissatisfied'} size={22} color={index === 0 ? GOLD : MUTE} />
                  <Roll text={grouped(mark.value)} line={28} style={styles.cardFigure} />
                  <Text style={styles.when}>{mark.when}</Text>
                  <Text style={styles.caption}>{mark.caption}</Text>
                </View>
              ))}
            </Animated.View>
          )}
        </Animated.View>

        {/* The headline: a goal disc for the first period, a large total for the others. */}
        <View style={[styles.headline, { top: insets.top, height: BODY_TOP }]} pointerEvents="none">
          <Animated.View style={[styles.total, totalStyle]}>
            <Roll text={first ? grouped(week.total) : grouped(stats.total)} line={FIGURE_LINE} style={styles.totalBig} />
            <Text style={styles.totalCaption}>{totalCaption}</Text>
          </Animated.View>

          <Animated.View style={[styles.disc, { left: (width - DISC) / 2 }, discStyle]}>
            <Svg width={DISC} height={DISC} style={styles.fill}>
              <AnimatedCircle
                cx={DISC / 2}
                cy={DISC / 2}
                r={(DISC - RING) / 2}
                stroke={GREEN}
                strokeWidth={RING}
                strokeLinecap="round"
                strokeDasharray={[circumference, circumference]}
                animatedProps={ringProps}
                fill="none"
                rotation={-90}
                origin={`${DISC / 2}, ${DISC / 2}`}
              />
            </Svg>
            <MaterialIcons name="directions-walk" size={30} color={NAVY} />
            <Roll text={discFigure} line={52} style={styles.discFigure} />
            <Text style={[styles.discCaption, { color: MUTE }]}>
              of {grouped(goal)} {unit}
            </Text>
            <Animated.View style={[styles.fill, styles.solid, solidStyle]}>
              <Svg width={DISC} height={DISC} style={styles.fill}>
                <Circle
                  cx={DISC / 2}
                  cy={DISC / 2}
                  r={DISC / 2 - 16}
                  stroke="#FFFFFF"
                  strokeWidth={3}
                  strokeLinecap="round"
                  strokeDasharray={[0.01, (Math.PI * (DISC - 32)) / 48 - 0.01]}
                  fill="none"
                />
              </Svg>
              <MaterialIcons name="directions-walk" size={30} color="#FFFFFF" />
              <Roll text={discFigure} line={52} style={{ ...styles.discFigure, color: '#FFFFFF' }} />
              <Text style={styles.discCaption}>
                of {grouped(goal)} {unit}
              </Text>
            </Animated.View>
          </Animated.View>
        </View>

        <View style={[styles.top, { top: insets.top + SWITCH_TOP }]}>
          <Pressable style={styles.close} hitSlop={10} accessibilityRole="button" accessibilityLabel="Close" onPress={onClose}>
            {({ pressed }) => <MaterialIcons name="close" size={22} color={INK} style={pressed && styles.pressed} />}
          </Pressable>
          <View style={styles.switch} accessibilityRole="tablist">
            <Animated.View style={[styles.capsule, { width: segment }, capsuleStyle]} />
            {periods.map((item, index) => (
              <Pressable
                key={item.name}
                style={styles.segment}
                accessibilityRole="tab"
                accessibilityState={{ selected: index === period }}
                onPress={() => select(index)}
              >
                <Text style={[styles.segmentLabel, index === period && styles.segmentOn]}>{item.name}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={[styles.period, { top: insets.top + PERIOD_TOP }]}>
          <Pressable hitSlop={12} accessibilityRole="button" accessibilityLabel="Earlier" onPress={() => step(1)}>
            <MaterialIcons name="chevron-left" size={22} color={back < stats.labels.length - 1 ? INK : FAINT} />
          </Pressable>
          <View style={styles.periodBox}>
            <Animated.Text
              key={stats.labels[back]}
              style={styles.periodLabel}
              numberOfLines={1}
              entering={FadeIn.duration(SWAP)}
              exiting={FadeOut.duration(SWAP)}
            >
              {stats.labels[back]}
            </Animated.Text>
          </View>
          <Pressable hitSlop={12} accessibilityRole="button" accessibilityLabel="Later" onPress={() => step(-1)}>
            <MaterialIcons name="chevron-right" size={22} color={back > 0 ? INK : FAINT} />
          </Pressable>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  pressed: {
    opacity: 0.5,
  },
  roll: {
    flexDirection: 'row',
  },
  window: {
    overflow: 'hidden',
    paddingHorizontal: 3,
    marginHorizontal: -3,
  },
  top: {
    position: 'absolute',
    left: 0,
    right: MARGIN,
    height: SWITCH,
    flexDirection: 'row',
    alignItems: 'center',
  },
  close: {
    width: SWITCH_LEFT,
    height: SWITCH,
    paddingLeft: MARGIN - 3,
    justifyContent: 'center',
  },
  switch: {
    flex: 1,
    height: SWITCH,
    padding: SWITCH_INSET,
    borderRadius: 8,
    backgroundColor: TRACK,
    flexDirection: 'row',
  },
  capsule: {
    position: 'absolute',
    left: SWITCH_INSET,
    top: SWITCH_INSET,
    bottom: SWITCH_INSET,
    borderRadius: 7,
    backgroundColor: '#FFFFFF',
    boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 3, color: 'rgba(0,0,0,0.1)' }],
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    color: SEGMENT_INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  segmentOn: {
    color: INK,
  },
  period: {
    position: 'absolute',
    left: MARGIN - 7,
    right: MARGIN - 7,
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  periodBox: {
    flex: 1,
    height: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  periodLabel: {
    position: 'absolute',
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    includeFontPadding: false,
  },
  headline: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  total: {
    position: 'absolute',
    left: MARGIN,
    top: FIGURE_TOP,
  },
  totalBig: {
    color: NAVY,
    fontSize: FIGURE,
    fontWeight: '800',
    fontStyle: 'italic',
    letterSpacing: -1,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  totalCaption: {
    marginTop: 4,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  disc: {
    position: 'absolute',
    top: DISC_TOP,
    width: DISC,
    height: DISC,
    borderRadius: DISC / 2,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  solid: {
    borderRadius: DISC / 2,
    backgroundColor: GREEN,
    alignItems: 'center',
    justifyContent: 'center',
  },
  discFigure: {
    color: NAVY,
    fontSize: 46,
    fontWeight: '700',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  discCaption: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 12,
    lineHeight: 16,
    includeFontPadding: false,
  },
  body: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  totals: {
    height: CHART_TOP,
    paddingHorizontal: MARGIN,
    flexDirection: 'row',
  },
  totalsColumn: {
    flex: 1,
  },
  totalsFigure: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  totalFigure: {
    color: NAVY,
    fontSize: 24,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  totalUnit: {
    marginLeft: 2,
    marginBottom: 4,
    color: NAVY,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    includeFontPadding: false,
  },
  caption: {
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
    includeFontPadding: false,
  },
  chart: {
    height: CHART,
    backgroundColor: PANEL,
  },
  scale: {
    position: 'absolute',
    left: MARGIN,
    top: 6,
    color: PANEL_INK,
    fontSize: 10,
    lineHeight: 12,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  bar: {
    position: 'absolute',
    bottom: 0,
    borderTopLeftRadius: 2,
    borderTopRightRadius: 2,
    transformOrigin: 'bottom',
  },
  axis: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: AXIS_TOP,
    height: 28,
  },
  axisLabel: {
    position: 'absolute',
    top: 0,
    width: 60,
    color: MUTE,
    fontSize: 11,
    lineHeight: 13,
    textAlign: 'center',
    includeFontPadding: false,
  },
  axisStrong: {
    color: INK,
    fontWeight: '700',
  },
  legend: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: LEGEND_TOP,
    height: 24,
    borderRadius: 6,
    backgroundColor: STRIP,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  legendLine: {
    width: 10,
    height: 1.5,
    marginRight: 5,
    backgroundColor: MUTE,
  },
  legendMark: {
    width: 7,
    height: 7,
    borderRadius: 1.5,
    marginLeft: 14,
    marginRight: 5,
  },
  legendLabel: {
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
    includeFontPadding: false,
  },
  cards: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: CARDS_TOP,
    flexDirection: 'row',
    gap: CARD_GAP,
  },
  cardsBelow: {
    top: CARDS_TOP + CARD + CARD_GAP,
  },
  card: {
    height: CARD,
    paddingHorizontal: 14,
    paddingTop: 12,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: '#FFFFFF',
  },
  cardTall: {
    height: CARD_TALL,
  },
  cardFigure: {
    color: NAVY,
    fontSize: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  badge: {
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  percent: {
    position: 'absolute',
    right: 14,
    top: 14,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    includeFontPadding: false,
  },
  signed: {
    flexDirection: 'row',
  },
  sign: {
    height: 28,
    lineHeight: 28,
  },
  when: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
