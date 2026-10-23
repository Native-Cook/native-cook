import Ionicons from '@expo/vector-icons/Ionicons';
import { Canvas, PaintStyle, Picture, Skia, StrokeCap } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useEffectEvent, useMemo, useState } from 'react';
import { type AccessibilityActionEvent, type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CARD_HEIGHT, CARD_WIDTH, Sky } from './Sky';
import {
  AUTO_SWIPES,
  FLICK,
  FLICK_MAX,
  type Focus,
  LEAD,
  LESSON_SHIFT,
  LESSON_STAGGER,
  LOOP,
  PITCH,
  type Pick,
  type Pull,
  QUIET,
  ROW_SHIFT,
  ROW_STAGGER,
  STEPS,
  type Slide,
  USER_HOLD,
  clamp01,
  enter,
  liftAt,
  offsetOf,
  panelAt,
  parallaxAt,
  previewAt,
  pulseAt,
  rowAt,
  slideAt,
  smooth,
  stepOf,
  tickAt,
} from './motion';

type IconName = keyof typeof Ionicons.glyphMap;

export type SkyGlassStep = {
  /** Its lines are broken by hand with "\n". */
  title: string;
  subtitle: string;
};

/** A row of the first step's preview. */
export type SkyGlassGoal = { label: string; icon: IconName; color: string };

/** A card of the second step's preview, from the back of the stack to the front. */
export type SkyGlassLesson = { category: string; icon: IconName; title: string };

/** What made the focus change: a finger, the loop that plays by itself, or the return to rest. */
type How = 'swipe' | 'auto' | 'rest';

/** Something the caller hears about: a change of focus, a goal picked, a square checked or cleared. */
type Told = { step: number; how: How } | { goal: number } | { day: number; checked: boolean };

type Props = {
  /** Its lines are broken by hand with "\n". */
  headline?: string;
  subline?: string;
  /** The word on each card's chip; the step's number follows it. */
  stepLabel?: string;
  /** The three cards: a short test, a stack of lessons, a routine. */
  steps?: SkyGlassStep[];
  /** Four rows for the first card. */
  goals?: SkyGlassGoal[];
  /** Three lesson cards for the second card, back to front. */
  lessons?: SkyGlassLesson[];
  routineTitle?: string;
  routineBadge?: string;
  /** Seven column heads for the third card's grid. */
  days?: string[];
  /** The squares of the grid that tick by themselves, counted along the rows from 0 to 27. */
  checkedDays?: number[];
  /** The sky from top to bottom. */
  skyColors?: string[];
  /** What a pane of glass adds over the blurred sky. */
  glassColor?: string;
  /** The checked squares. */
  accentColor?: string;
  /** The text on the light parts of the previews. */
  inkColor?: string;
  /** Goes through the steps by itself every LOOP seconds. */
  autoPlay?: boolean;
  onStepChange?: (step: number) => void;
  onGoalSelect?: (index: number) => void;
  onDayToggle?: (index: number, checked: boolean) => void;
};

const HEADLINE = 'It took a lot of\nsteps to become\nwho you are';
const SUBLINE = 'It will take 3 steps to start\ntransformation';
const SAMPLE_STEPS: SkyGlassStep[] = [
  { title: 'Take a short test', subtitle: 'To get your personal plan' },
  { title: 'Understand your\nwell-being state', subtitle: 'To spark your natural drive' },
  { title: 'Recalibrate', subtitle: 'Train your mind to focus on practical\noutcomes in any state' },
];
const GOALS: SkyGlassGoal[] = [
  { label: 'Overcome trauma', icon: 'diamond', color: '#3b82f6' },
  { label: 'Get things done', icon: 'flower', color: '#ec4899' },
  { label: 'Improve well-being', icon: 'leaf', color: '#65a30d' },
  { label: 'Sharpen focus', icon: 'radio-button-on', color: '#f97316' },
];
const LESSONS: SkyGlassLesson[] = [
  { category: 'Daily tasks', icon: 'checkbox', title: 'Small step\nstep' },
  { category: 'Practice', icon: 'flash', title: 'Define your\nhabits' },
  { category: 'Learning', icon: 'book', title: 'Procrastination\ncycle' },
];
const DAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
const CHECKED = [1, 4, 5, 8, 11];

const SKY = ['#2c57a4', '#4a78bd', '#7fa3d6'];
const GLASS = 'rgba(58,74,102,0.35)';
const ACCENT = '#5ba80f';
const INK = '#1f2937';
const MUTE = '#6b7280';
const LESSON_BAR = '#b5d98b';
const TEXT_SHADOW = 'rgba(16,36,84,0.28)';
const LESSON_SHADOW = 'rgba(16,28,60,0.2)';

// The screen is laid out for at least this height and scaled down where it is shorter.
const MIN_HEIGHT = 720;
// The cards stand this far above the bottom edge, or clear of the home indicator.
const CARD_BOTTOM = 44;
const CARD_CLEAR = 8;
// A finger has to travel this far sideways before it takes hold of the carousel.
const SLOP = 8;

// Inside a card, from its top left corner.
const CHIP_TOP = 33;
const CHIP_WIDTH = 56;
const CHIP_HEIGHT = 24;
const TITLE_TOP = 72;
const TEXT_MARGIN = 20;
const PREVIEW_LEFT = 34;
const PREVIEW_TOP = 172;
const PREVIEW_WIDTH = 230;
const PREVIEW_HEIGHT = 244;

const ROW_HEIGHT = 55;
const ROW_GAP = 8;
const ROW_RADIUS = 14;
const TILE = 36;

// A lesson card is laid out at the front card's size and scaled about the middle of its top edge.
const LESSON_HEIGHT = 88;
const LESSON_TOPS = [18, 82, 156];
const LESSON_SCALES = [0.78, 0.9, 1];
const LESSON_FILLS = ['rgba(255,255,255,0.3)', 'rgba(255,255,255,0.5)', '#ffffff'];

// The grid: seven columns and four rows of squares, placed by their middles.
const COLUMNS = 7;
const SQUARES = 28;
const SQUARE = 20;
const SQUARE_RADIUS = 5.5;
const SQUARE_X = 28;
const SQUARE_Y = 98;
const SQUARE_PITCH_X = 29;
const SQUARE_PITCH_Y = 39;
const DAYS_Y = 62;
const HEADER_Y = 26;
const PANEL_MARGIN = 18;
// The lower rows fade out.
const ROW_FADES = [1, 0.8, 0.45, 0.25];
const EMPTY_ALPHA = 0.6;
// The check is two strokes, from A by B to C; the short one takes this share of the drawing.
const CHECK = [-4.5, 0.4, -1.3, 3.4, 4.5, -3];
const CHECK_SHORT = 0.38;
const CHECK_AFTER = 0.35;
const NO_TOGGLES: number[] = Array.from({ length: SQUARES }, () => 0);

function tick(style: 'select' | 'tap') {
  if (process.env.EXPO_OS === 'web') return;
  if (style === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type CardProps = {
  index: number;
  step: SkyGlassStep;
  chip: string;
  left: number;
  top: number;
  position: SharedValue<number>;
  children: ReactNode;
};

/** What stands on a pane of glass: a chip, a title, a line under it and the step's preview. */
function Card({ index, step, chip, left, top, position, children }: CardProps) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: offsetOf(index, position.get()) * PITCH }] }));
  return (
    <Animated.View style={[styles.card, { left, top }, style]}>
      <View style={styles.chip}>
        <Text style={styles.chipLabel} numberOfLines={1}>
          {chip}
        </Text>
      </View>
      <View style={styles.cardText}>
        <Text style={styles.title} numberOfLines={step.title.split('\n').length} adjustsFontSizeToFit minimumFontScale={0.8}>
          {step.title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={step.subtitle.split('\n').length} adjustsFontSizeToFit minimumFontScale={0.8}>
          {step.subtitle}
        </Text>
      </View>
      <View style={styles.preview}>{children}</View>
    </Animated.View>
  );
}

type PreviewProps = {
  focus: SharedValue<Focus>;
  clock: SharedValue<number>;
  still: boolean;
  ink: string;
};

function GoalRow({ index, goal, focus, clock, still, ink }: PreviewProps & { index: number; goal: SkyGlassGoal }) {
  const style = useAnimatedStyle(() => {
    const { e, shown, warm } = previewAt(0, focus.get(), clock.get());
    const pose = enter(e, index * ROW_STAGGER, ROW_SHIFT, warm, still);
    return { opacity: pose.opacity * shown, transform: [{ translateY: pose.y }] };
  });
  return (
    <Animated.View style={[styles.row, { top: index * (ROW_HEIGHT + ROW_GAP) }, style]}>
      <View style={styles.tile}>
        <Ionicons name={goal.icon} size={20} color={goal.color} />
      </View>
      <Text style={[styles.rowLabel, { color: ink }]} numberOfLines={1}>
        {goal.label}
      </Text>
      <Ionicons name="chevron-forward" size={14} color={ink} style={styles.chevron} />
    </Animated.View>
  );
}

/** Step 1: four goals; a white fill marks one and steps down the list. */
function Goals({ goals, pick, focus, clock, still, ink }: PreviewProps & { goals: SkyGlassGoal[]; pick: SharedValue<Pick> }) {
  const fill = useAnimatedStyle(() => {
    const t = clock.get();
    const here = focus.get();
    const { e, shown, warm } = previewAt(0, here, t);
    const row = rowAt(e, warm ? here.row : 0, pick.get(), t, still);
    // The fill comes in with the row it is on.
    const pose = enter(e, Math.round(row) * ROW_STAGGER, ROW_SHIFT, warm, still);
    return { opacity: pose.opacity * shown, transform: [{ translateY: row * (ROW_HEIGHT + ROW_GAP) + pose.y }] };
  });
  return (
    <>
      <Animated.View style={[styles.rowFill, fill]} />
      {goals.slice(0, 4).map((goal, index) => (
        <GoalRow key={index} index={index} goal={goal} focus={focus} clock={clock} still={still} ink={ink} />
      ))}
    </>
  );
}

function Lesson({ index, lesson, focus, clock, still, ink }: PreviewProps & { index: number; lesson: SkyGlassLesson }) {
  const style = useAnimatedStyle(() => {
    const { e, shown, warm } = previewAt(1, focus.get(), clock.get());
    const pose = enter(e, index * LESSON_STAGGER, LESSON_SHIFT, warm, still);
    const lift = index === 2 ? liftAt(e, still) : 0;
    return { opacity: pose.opacity * shown, transform: [{ translateY: pose.y - lift }, { scale: LESSON_SCALES[index] }] };
  });
  return (
    <Animated.View style={[styles.lesson, index === 2 && styles.lessonFront, { top: LESSON_TOPS[index], backgroundColor: LESSON_FILLS[index] }, style]}>
      <View style={styles.lessonBar} />
      <View style={styles.lessonCategory}>
        <Ionicons name={lesson.icon} size={11} color={MUTE} />
        <Text style={styles.lessonCategoryLabel} numberOfLines={1}>
          {lesson.category}
        </Text>
      </View>
      <Text style={[styles.lessonTitle, { color: ink }]} numberOfLines={2}>
        {lesson.title}
      </Text>
    </Animated.View>
  );
}

type RoutineProps = PreviewProps & {
  title: string;
  badge: string;
  days: string[];
  /** For each square its place among those that tick by themselves, or -1. */
  order: number[];
  /** How many tick by themselves. */
  ticks: number;
  toggles: SharedValue<number[]>;
  accent: string;
};

/** Step 3: a month of a routine; squares tick green one by one, and a finger can change any of them. */
function Routine({ title, badge, days, order, ticks, toggles, accent, focus, clock, still, ink }: RoutineProps) {
  const panel = useAnimatedStyle(() => {
    const { e, shown, warm } = previewAt(2, focus.get(), clock.get());
    const pose = panelAt(e, warm, still);
    return { opacity: pose.opacity * shown, transform: [{ scale: pose.scale }] };
  });
  const pulse = useAnimatedStyle(() => ({ transform: [{ scale: pulseAt(previewAt(2, focus.get(), clock.get()).e, ticks, still) }] }));
  const squares = useDerivedValue(() => {
    const t = clock.get();
    const { e } = previewAt(2, focus.get(), t);
    const list = toggles.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT));
    const box = Skia.RRectXY(Skia.XYWHRect(-SQUARE / 2, -SQUARE / 2, SQUARE, SQUARE), SQUARE_RADIUS, SQUARE_RADIUS);
    const empty = Skia.Paint();
    const full = Skia.Paint();
    const mark = Skia.Paint();
    mark.setStyle(PaintStyle.Stroke);
    mark.setStrokeWidth(2);
    mark.setStrokeCap(StrokeCap.Round);
    const [ax, ay, bx, by, cx, cy] = CHECK;
    for (let i = 0; i < SQUARES; i++) {
      const row = Math.floor(i / COLUMNS);
      const fade = ROW_FADES[row];
      const { q, pop } = tickAt(e, order[i], list[i], t, still);
      canvas.save();
      canvas.translate(SQUARE_X + (i % COLUMNS) * SQUARE_PITCH_X, SQUARE_Y + row * SQUARE_PITCH_Y);
      canvas.scale(pop, pop);
      empty.setColor(Skia.Color('#ffffff'));
      empty.setAlphaf(EMPTY_ALPHA * fade);
      canvas.drawRRect(box, empty);
      if (q > 0) {
        full.setColor(Skia.Color(accent));
        full.setAlphaf(smooth(q, 0, 0.6) * fade);
        canvas.drawRRect(box, full);
        // With motion the check is drawn stroke by stroke; without, it fades in whole.
        const drawn = still ? 1 : clamp01((q - CHECK_AFTER) / (1 - CHECK_AFTER));
        mark.setColor(Skia.Color('#ffffff'));
        mark.setAlphaf((still ? q : 1) * fade);
        if (drawn > 0) {
          const short = Math.min(1, drawn / CHECK_SHORT);
          canvas.drawLine(ax, ay, ax + (bx - ax) * short, ay + (by - ay) * short, mark);
        }
        if (drawn > CHECK_SHORT) {
          const long = (drawn - CHECK_SHORT) / (1 - CHECK_SHORT);
          canvas.drawLine(bx, by, bx + (cx - bx) * long, by + (cy - by) * long, mark);
        }
      }
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <Animated.View style={[styles.panel, panel]}>
      <Text style={[styles.panelTitle, { color: ink }]} numberOfLines={1}>
        {title}
      </Text>
      <Animated.View style={[styles.badge, pulse]}>
        <Ionicons name="checkmark-circle" size={13} color={MUTE} />
        <Text style={styles.badgeLabel} numberOfLines={1}>
          {badge}
        </Text>
      </Animated.View>
      {days.slice(0, COLUMNS).map((day, index) => (
        <Text key={index} style={[styles.day, { left: SQUARE_X + index * SQUARE_PITCH_X - SQUARE_PITCH_X / 2 }]} numberOfLines={1}>
          {day}
        </Text>
      ))}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={squares} />
      </Canvas>
    </Animated.View>
  );
}

/**
 * An introduction in three steps under a drifting sky. A headline floats on the sky, and the
 * steps are panes of frosted glass in a carousel without end; each pane shows a small preview of
 * its step that plays when the card comes to the middle. Swipe to move on, tap a row or a square
 * to change it.
 */
export function SkyGlassSteps({
  headline = HEADLINE,
  subline = SUBLINE,
  stepLabel = 'Step',
  steps = SAMPLE_STEPS,
  goals = GOALS,
  lessons = LESSONS,
  routineTitle = 'New routine',
  routineBadge = 'Mastered',
  days = DAYS,
  checkedDays = CHECKED,
  skyColors = SKY,
  glassColor = GLASS,
  accentColor = ACCENT,
  inkColor = INK,
  autoPlay = false,
  onStepChange,
  onGoalSelect,
  onDayToggle,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const fit = Math.min(1, size.height / MIN_HEIGHT);
  const width = size.width / fit;
  const height = size.height / fit;
  const cardTop = height - Math.max(CARD_BOTTOM, insets.bottom / fit + CARD_CLEAR) - CARD_HEIGHT;
  const cardLeft = (width - CARD_WIDTH) / 2;

  // The step in focus, for screen readers; the pictures follow `focus`.
  const [shown, setShown] = useState(0);
  // The last thing to tell the caller; it leaves the UI thread through here.
  const [told, setTold] = useState<Told | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause; it waits while a finger holds.
  const [touches, setTouches] = useState(0);
  const [holding, setHolding] = useState(false);

  // Seconds since the first frame: `now` always, `clock` only while something moves.
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const settled = useSharedValue(QUIET);
  // The screen opens as it is LEAD after the last card was left: the first step in, and still.
  const slide = useSharedValue<Slide>({ page: 0, from: -1, velocity: 0, at: -LEAD });
  const focus = useSharedValue<Focus>({ page: 0, was: 2, held: QUIET, at: -LEAD, warm: false, row: 0 });
  // A finger on the carousel: where it has it, where it took it, the card it started from, and
  // how far it had already moved when it took hold.
  const drag = useSharedValue({ on: false, position: 0, from: 0, origin: 0, grab: 0 });
  const pull = useSharedValue<Pull>({ x: 0, released: 0 });
  const pick = useSharedValue<Pick>({ row: -1, from: 0, at: 0 });
  // When a finger last changed each square: positive for checked, negative for cleared.
  const toggles = useSharedValue(NO_TOGGLES);

  const order = useMemo(() => {
    const sorted = [...checkedDays].sort((a, b) => a - b);
    return Array.from({ length: SQUARES }, (_, index) => sorted.indexOf(index));
  }, [checkedDays]);

  // The timestamp of the first frame. Every render registers the frame callback anew, which
  // starts its `timeSinceFirstFrame` over, so the time is counted from here instead.
  const born = useSharedValue(-1);

  useFrameCallback((frame) => {
    if (born.get() < 0) born.set(frame.timestamp);
    const t = (frame.timestamp - born.get()) / 1000;
    now.set(t);
    // The sky never rests; with reduced motion it is still, and the clock stops once a change has played out.
    const moving = still ? Math.min(t, settled.get()) : t;
    if (moving !== clock.get()) clock.set(moving);
  });

  const position = useDerivedValue(() => (drag.get().on ? drag.get().position : slideAt(slide.get(), clock.get(), still)));

  const report = useEffectEvent((news: Told | null) => {
    if (news === null) return;
    if ('step' in news) {
      if (news.how === 'swipe') tick('select');
      onStepChange?.(news.step);
    } else if ('goal' in news) {
      tick('tap');
      onGoalSelect?.(news.goal);
    } else {
      tick('tap');
      onDayToggle?.(news.day, news.checked);
    }
  });
  useEffect(() => report(told), [told]);

  const actions = useMemo(() => {
    const touched = () => setTouches((n) => n + 1);
    const held = () => setHolding(true);
    const released = () => {
      setHolding(false);
      setTouches((n) => n + 1);
    };
    /** Where the carousel is right now. */
    const where = () => {
      'worklet';
      const finger = drag.get();
      return finger.on ? finger.position : slideAt(slide.get(), now.get(), still);
    };
    /** Gives a page the focus: its preview starts from the beginning, and the one before fades. */
    const focusOn = (page: number, how: How) => {
      'worklet';
      const t = now.get();
      const old = focus.get();
      const step = stepOf(page);
      const same = step === stepOf(old.page);
      const e = t - old.at;
      focus.set({
        page,
        was: same ? old.was : stepOf(old.page),
        // A card that had started over was already whole.
        held: same ? old.held : old.warm ? Math.max(e, 1) : e,
        at: t,
        warm: same,
        row: same && step === 0 ? rowAt(e, old.warm ? old.row : 0, pick.get(), t, still) : 0,
      });
      // What a finger changed on a card is forgotten when the card comes round again.
      if (step === 0) pick.set({ row: -1, from: 0, at: 0 });
      if (step === 2) toggles.set(NO_TOGGLES);
      settled.set(t + QUIET);
      scheduleOnRN(setShown, step);
      scheduleOnRN(setTold, { step, how });
    };
    /** Sends the carousel to a page. Taps beside the card, the loop that plays by itself and the return to rest all come through here. */
    const goTo = (page: number, how: How) => {
      'worklet';
      const t = now.get();
      slide.set({ page, from: where(), velocity: 0, at: t });
      drag.set({ on: false, position: 0, from: 0, origin: 0, grab: 0 });
      settled.set(t + QUIET);
      if (page !== focus.get().page || how === 'rest') focusOn(page, how);
    };
    /** One card on or back. */
    const move = (by: number, how: How) => {
      'worklet';
      goTo(slide.get().page + by, how);
    };
    /** Back to the first step, the short way round. */
    const rest = () => {
      'worklet';
      goTo(Math.round(where() / STEPS) * STEPS, 'rest');
    };
    /** Step 1: moves the white fill to a row. */
    const choose = (row: number) => {
      'worklet';
      const t = now.get();
      const here = focus.get();
      pick.set({ row, from: rowAt(t - here.at, here.warm ? here.row : 0, pick.get(), t, still), at: t });
      settled.set(t + QUIET);
      scheduleOnRN(setTold, { goal: row });
    };
    /** Step 3: checks or clears a square. */
    const toggle = (index: number) => {
      'worklet';
      // Zero stands for "never touched", so a touch is never stamped with it.
      const t = Math.max(now.get(), 0.001);
      const list = toggles.get();
      const checked = list[index] !== 0 ? list[index] > 0 : order[index] >= 0;
      toggles.set(list.map((at, i) => (i === index ? (checked ? -t : t) : at)));
      settled.set(t + QUIET);
      scheduleOnRN(setTold, { day: index, checked: !checked });
    };
    return { touched, held, released, where, focusOn, goTo, move, rest, choose, toggle };
  }, [now, slide, focus, drag, pick, toggles, settled, order, still]);

  const gesture = useMemo(() => {
    const { touched, held, released, where, focusOn, goTo, choose, toggle } = actions;
    const pan = Gesture.Pan()
      .activeOffsetX([-SLOP, SLOP])
      .onStart((event) => {
        const at = where();
        drag.set({ on: true, position: at, from: at, origin: Math.round(at), grab: event.translationX });
        pull.set({ x: 0, released: -1 });
        scheduleOnRN(held);
      })
      .onChange((event) => {
        const finger = drag.get();
        if (!finger.on) return;
        const moved = (event.translationX - finger.grab) / fit;
        const at = finger.from - moved / PITCH;
        drag.set({ on: true, position: at, from: finger.from, origin: finger.origin, grab: finger.grab });
        pull.set({ x: moved, released: -1 });
        // The card nearest the middle has the focus, also under a finger.
        if (Math.round(at) !== focus.get().page) focusOn(Math.round(at), 'swipe');
      })
      .onEnd((event) => {
        const finger = drag.get();
        if (!finger.on) return;
        const t = now.get();
        const velocity = Math.max(-FLICK_MAX, Math.min(FLICK_MAX, -event.velocityX / fit / PITCH));
        // A flick carries one card further at most.
        const page = Math.max(finger.origin - 1, Math.min(finger.origin + 1, Math.round(finger.position + velocity * FLICK)));
        drag.set({ on: false, position: finger.position, from: finger.from, origin: finger.origin, grab: finger.grab });
        slide.set({ page, from: finger.position, velocity, at: t });
        pull.set({ x: pull.get().x, released: t });
        settled.set(t + QUIET);
        if (page !== focus.get().page) focusOn(page, 'swipe');
        scheduleOnRN(released);
      });
    const tap = Gesture.Tap()
      .maxDuration(1000)
      .onEnd((event, success) => {
        const y = event.y / fit - cardTop;
        if (!success || y < 0 || y > CARD_HEIGHT) return;
        scheduleOnRN(touched);
        const page = slide.get().page;
        const x = event.x / fit - cardLeft;
        // The slivers of the cards on either side bring those cards in.
        if (x < 0 || x > CARD_WIDTH) {
          goTo(page + (x < 0 ? -1 : 1), 'swipe');
          return;
        }
        const px = x - PREVIEW_LEFT;
        const py = y - PREVIEW_TOP;
        if (px < 0 || px > PREVIEW_WIDTH || py < 0 || py > PREVIEW_HEIGHT) return;
        if (stepOf(page) === 0) {
          const row = Math.floor(py / (ROW_HEIGHT + ROW_GAP));
          if (row < 4 && py - row * (ROW_HEIGHT + ROW_GAP) <= ROW_HEIGHT) choose(row);
        } else if (stepOf(page) === 2) {
          const column = Math.round((px - SQUARE_X) / SQUARE_PITCH_X);
          const row = Math.round((py - SQUARE_Y) / SQUARE_PITCH_Y);
          const near = Math.abs(px - SQUARE_X - column * SQUARE_PITCH_X) <= SQUARE_PITCH_X / 2 && Math.abs(py - SQUARE_Y - row * SQUARE_PITCH_Y) <= SQUARE_PITCH_Y / 2;
          if (near && column >= 0 && column < COLUMNS && row >= 0 && row < SQUARES / COLUMNS) toggle(row * COLUMNS + column);
        }
      });
    return Gesture.Exclusive(pan, tap);
  }, [actions, now, slide, focus, drag, pull, settled, fit, cardTop, cardLeft]);

  useEffect(() => {
    if (!autoPlay || holding) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const at of AUTO_SWIPES) after(origin + at * 1000 - Date.now(), () => scheduleOnUI(actions.move, 1, 'auto'));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // A loop starts LEAD after the first step took the focus.
        scheduleOnUI(actions.rest);
        cycle(Date.now() + LEAD * 1000);
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, holding, actions]);

  const headStyle = useAnimatedStyle(() => ({ transform: [{ translateX: parallaxAt(pull.get(), clock.get(), still) }] }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const onAccessibilityAction = (event: AccessibilityActionEvent) => {
    const by = event.nativeEvent.actionName === 'increment' ? 1 : event.nativeEvent.actionName === 'decrement' ? -1 : 0;
    if (by === 0) return;
    actions.touched();
    scheduleOnUI(actions.move, by, 'swipe');
  };

  const current = steps[shown];
  const previews = [
    <Goals key="goals" goals={goals} pick={pick} focus={focus} clock={clock} still={still} ink={inkColor} />,
    lessons.slice(0, 3).map((lesson, index) => <Lesson key={index} index={index} lesson={lesson} focus={focus} clock={clock} still={still} ink={inkColor} />),
    <Routine
      key="routine"
      title={routineTitle}
      badge={routineBadge}
      days={days}
      order={order}
      ticks={checkedDays.length}
      toggles={toggles}
      accent={accentColor}
      focus={focus}
      clock={clock}
      still={still}
      ink={inkColor}
    />,
  ];

  return (
    <GestureDetector gesture={gesture}>
      <View style={[styles.screen, { backgroundColor: skyColors[0] }]} onLayout={onLayout}>
        <StatusBar style="light" />
        <Sky clock={clock} position={position} still={still} width={width} height={height} fit={fit} cardTop={cardTop} colors={skyColors} glassColor={glassColor} />

        <View style={[styles.scene, { width, height, transform: [{ scale: fit }] }]}>
          <Animated.View style={[styles.head, { top: insets.top / fit, height: cardTop - insets.top / fit }, headStyle]} pointerEvents="none">
            {/* The lines are the headline's own, so it shrinks before it would wrap. */}
            <Text style={styles.headline} accessibilityRole="header" numberOfLines={headline.split('\n').length} adjustsFontSizeToFit minimumFontScale={0.7}>
              {headline}
            </Text>
            <Text style={styles.subline} numberOfLines={subline.split('\n').length} adjustsFontSizeToFit minimumFontScale={0.7}>
              {subline}
            </Text>
          </Animated.View>

          <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            {steps.slice(0, STEPS).map((step, index) => (
              <Card key={index} index={index} step={step} chip={`${stepLabel} ${index + 1}`} left={cardLeft} top={cardTop} position={position}>
                {previews[index]}
              </Card>
            ))}
          </View>

          <View
            style={[styles.carousel, { top: cardTop }]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`${stepLabel} ${shown + 1}`}
            accessibilityValue={{ text: `${current.title.replace('\n', ' ')}. ${current.subtitle.replace('\n', ' ')}` }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={onAccessibilityAction}
          />
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  scene: {
    position: 'absolute',
    left: 0,
    top: 0,
    transformOrigin: 'left top',
  },
  head: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  headline: {
    fontSize: 34,
    lineHeight: 40,
    fontWeight: '600',
    letterSpacing: -0.5,
    textAlign: 'center',
    color: '#ffffff',
    textShadowColor: TEXT_SHADOW,
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 12,
    includeFontPadding: false,
  },
  subline: {
    marginTop: 14,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.85)',
    textShadowColor: TEXT_SHADOW,
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 8,
    includeFontPadding: false,
  },
  carousel: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: CARD_HEIGHT,
  },
  card: {
    position: 'absolute',
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  chip: {
    position: 'absolute',
    left: (CARD_WIDTH - CHIP_WIDTH) / 2,
    top: CHIP_TOP,
    width: CHIP_WIDTH,
    height: CHIP_HEIGHT,
    borderRadius: CHIP_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.14)',
  },
  chipLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    color: '#ffffff',
    includeFontPadding: false,
  },
  cardText: {
    position: 'absolute',
    left: TEXT_MARGIN,
    right: TEXT_MARGIN,
    top: TITLE_TOP,
    alignItems: 'center',
  },
  title: {
    fontSize: 21,
    lineHeight: 27,
    fontWeight: '600',
    letterSpacing: -0.2,
    textAlign: 'center',
    color: '#ffffff',
    includeFontPadding: false,
  },
  subtitle: {
    marginTop: 5,
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.7)',
    includeFontPadding: false,
  },
  preview: {
    position: 'absolute',
    left: PREVIEW_LEFT,
    top: PREVIEW_TOP,
    width: PREVIEW_WIDTH,
    height: PREVIEW_HEIGHT,
  },
  rowFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PREVIEW_WIDTH,
    height: ROW_HEIGHT,
    borderRadius: ROW_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.86)',
  },
  row: {
    position: 'absolute',
    left: 0,
    width: PREVIEW_WIDTH,
    height: ROW_HEIGHT,
    borderRadius: ROW_RADIUS,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 10,
    paddingRight: 13,
    backgroundColor: 'rgba(255,255,255,0.26)',
  },
  tile: {
    width: TILE,
    height: TILE,
    borderRadius: 10,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ffffff',
  },
  rowLabel: {
    flex: 1,
    marginLeft: 10,
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  chevron: {
    opacity: 0.7,
  },
  lesson: {
    position: 'absolute',
    left: 0,
    width: PREVIEW_WIDTH,
    height: LESSON_HEIGHT,
    borderRadius: 14,
    borderCurve: 'continuous',
    transformOrigin: 'center top',
  },
  lessonFront: {
    boxShadow: [{ offsetX: 0, offsetY: 6, blurRadius: 16, color: LESSON_SHADOW }],
  },
  lessonBar: {
    position: 'absolute',
    left: 12,
    top: 11.5,
    width: 6,
    height: 65,
    borderRadius: 3,
    backgroundColor: LESSON_BAR,
  },
  lessonCategory: {
    position: 'absolute',
    left: 28,
    top: 11,
    height: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  lessonCategoryLabel: {
    marginLeft: 5,
    fontSize: 11,
    lineHeight: 16,
    color: MUTE,
    includeFontPadding: false,
  },
  lessonTitle: {
    position: 'absolute',
    left: 28,
    right: 12,
    top: 30,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '600',
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  panel: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PREVIEW_WIDTH,
    height: PREVIEW_HEIGHT,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.45)',
  },
  panelTitle: {
    position: 'absolute',
    left: PANEL_MARGIN,
    top: HEADER_Y - 9,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  badge: {
    position: 'absolute',
    right: PANEL_MARGIN,
    top: HEADER_Y - 9,
    height: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  badgeLabel: {
    marginLeft: 4,
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
    color: MUTE,
    includeFontPadding: false,
  },
  day: {
    position: 'absolute',
    top: DAYS_Y - 8,
    width: SQUARE_PITCH_X,
    fontSize: 11,
    lineHeight: 16,
    textAlign: 'center',
    color: '#374151',
    includeFontPadding: false,
  },
});
