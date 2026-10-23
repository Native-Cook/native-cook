import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CARD_HEIGHT, TaskCard, type Task } from './TaskCard';
import { type Step, useAutoPlay } from './use-auto-play';

type Stage = 'idle' | 'open' | 'closing' | 'added';

type Props = {
  /** The task the sheet hands to the list. */
  task?: Task;
  /** The task already in the list. */
  existing?: Task;
  /** Later tasks under it, which make room too. */
  later?: Task[];
  /** Plays the whole interaction by itself, over and over, and gives way to a touch. */
  autoPlay?: boolean;
  newLabel?: string;
  title?: string;
  caption?: string;
  doneLabel?: string;
  /** Called when the card has arrived in the list. */
  onAdd?: (task: Task) => void;
};

const TASK: Task = {
  name: 'Founder Playbook',
  caption: 'Short lessons on building from scratch, read every morning before work.',
  time: '7:30AM',
  color: '#4dd0cb',
};
const EXISTING: Task = {
  name: 'Investor Mindset',
  caption: 'Mental models for calm decisions, with one worked case study a day.',
  time: '9:00AM',
  color: '#f58a4b',
  progress: 0.6,
};
const LATER: Task[] = [
  {
    name: 'Deep Work Sprint',
    caption: 'Ninety focused minutes on the one thing that moves the week forward.',
    time: '1:30PM',
    color: '#8c7cf0',
    status: 'later',
    progress: 0,
  },
  {
    name: 'Evening Reflection',
    caption: 'Three questions to close the day, and one line to remember it by.',
    time: '9:00PM',
    color: '#f2b63d',
    status: 'later',
    progress: 0,
  },
];
const FRONT_LINES = [
  ['Ship the smallest thing that teaches you something.', 'Day 1, on first versions'],
  ['Talk to ten users before you write a line of code.', 'Day 2, on listening'],
  ['Say no to a hundred good ideas.', 'Day 3, on focus'],
];

/** One timeline for the autoPlay loop, in seconds from its start. */
export const LOOP = 9;
const TAP_NEW_AT = 0.9;
const TAP_FOR = 0.15;
const TAP_DONE_AT = 3.9;
const CLEAR_AT = 7.3;
const REST_AT = 8.2;
// Seconds a touch keeps the loop away.
const USER_HOLD = 3;

const PAGE = '#ffffff';
const INK = '#17181a';
const MUTE = '#8d8f94';
const OUTLINE = '#e3e4e6';
const WELL = '#f5f5f6';
const WELL_INK = '#a9abb0';
const BLUE = '#4ab8f0';
const BLUE_SOFT = '#8ccbf0';
const SCRIM = 0.55;
const BADGE = '#f2364c';

const MARGIN = 20;
const PILL_HEIGHT = 28;
const TITLE_TOP = 48;
const NEW_TOP = 132;
const NEW_HEIGHT = 52;
const LIST_TOP = NEW_TOP + NEW_HEIGHT + 16;
const SLOT = CARD_HEIGHT + 16;
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
const SHEET_HEIGHT = 432;
const SHEET_OVERSHOOT = 48;
const SHEET_RADIUS = 28;
const BUTTON_HEIGHT = 56;
const FAN_TOP = 132;
const FAN_WIDTH = 150;
const FAN_HEIGHT = 186;
const FAN_TILT = 7;
const FAN_SPREAD = 22;
const TAB_HEIGHT = 60;
const FAN_STAGGER = 0.08;
const FAN_DURATION = 0.5;
const FAN_AT = 700;
const PRESS_SCALE = 0.95;
const DISMISS_DRAG = 90;
const DISMISS_VELOCITY = 800;

const SHEET_SPRING = { damping: 17, stiffness: 190, mass: 1 };
const FLIGHT_SPRING = { duration: 450, dampingRatio: 0.8 };
const CLOSE_MS = 350;
const CLOSE_EASING = Easing.bezier(0.5, 0, 1, 1);
const FADE_MS = 250;
// The list starts to move this long after the card leaves the sheet.
const LIST_AFTER = 250;
const BADGE_AFTER = 1200;
const CLEAR_MS = 600;

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function ramp(value: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (value - from) / (to - from)));
}

/** Soft start and end of a 0 → 1 run. */
function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/**
 * A task list with a bottom sheet over it. The sheet shows a fan of cards and a Done button; Done
 * drops the sheet and the front card flies out of it into the first slot of the list, growing from
 * 120 points to the width of the list. The card is drawn in an overlay above both, since it belongs
 * to neither, and its small and large layouts cross-fade so that text is never scaled.
 */
export function SheetCardHandoff({
  task = TASK,
  existing = EXISTING,
  later = LATER,
  autoPlay = false,
  newLabel = '+ NEW TASK',
  title = 'ALL SET',
  caption = 'This is a preview. The full cards will be delivered later.',
  doneLabel = 'DONE',
  onAdd,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const sheetHeight = SHEET_HEIGHT + insets.bottom;
  const cardWidth = width - MARGIN * 2;
  const listTop = insets.top + LIST_TOP;

  // 0 hidden, 1 resting; the fan, the flight and the list each have a run of their own.
  const sheet = useSharedValue(0);
  const drag = useSharedValue(0);
  const fan = useSharedValue(0);
  const fly = useSharedValue(0);
  const flying = useSharedValue(0);
  const flyFrom = useSharedValue(0);
  const shift = useSharedValue(0);
  const landed = useSharedValue(0);
  const badge = useSharedValue(0);
  const newScale = useSharedValue(1);
  const doneScale = useSharedValue(1);

  const [added, setAdded] = useState(false);
  const [touches, setTouches] = useState(0);
  // idle → open → closing → added, and back to idle.
  const stage = useSharedValue<Stage>('idle');

  const open = () => {
    if (stage.get() !== 'idle') return;
    stage.set('open');
    if (reducedMotion) {
      sheet.set(withTiming(1, { duration: FADE_MS }));
      fan.set(1);
      return;
    }
    sheet.set(withSpring(1, SHEET_SPRING));
    fan.set(withDelay(FAN_AT, withTiming(1, { duration: (FAN_DURATION + FAN_STAGGER * 2) * 1000, easing: Easing.linear })));
  };

  const arrived = () => {
    stage.set('added');
    setAdded(true);
    onAdd?.(task);
  };

  const close = () => {
    if (stage.get() !== 'open') return;
    stage.set('closing');
    if (reducedMotion) {
      sheet.set(withTiming(0, { duration: FADE_MS }));
      landed.set(withTiming(1, { duration: FADE_MS }));
      shift.set(withTiming(1, { duration: FADE_MS }));
      badge.set(withTiming(1, { duration: FADE_MS }));
      arrived();
      return;
    }
    sheet.set(withTiming(0, { duration: CLOSE_MS, easing: CLOSE_EASING }));
    flying.set(1);
    fly.set(
      withSpring(1, FLIGHT_SPRING, (finished) => {
        if (!finished) return;
        // The overlay and the list's own card swap in the same frame.
        flying.set(0);
        landed.set(1);
        scheduleOnRN(arrived);
        badge.set(withDelay(BADGE_AFTER, withSequence(withTiming(1.15, { duration: 160 }), withTiming(1, { duration: 120 }))));
      }),
    );
    shift.set(withDelay(LIST_AFTER, withSpring(1, FLIGHT_SPRING)));
  };

  const clear = () => {
    if (stage.get() !== 'added') return;
    stage.set('idle');
    landed.set(withTiming(0, { duration: CLEAR_MS * 0.7 }));
    badge.set(withTiming(0, { duration: CLEAR_MS * 0.5 }));
    shift.set(withTiming(0, { duration: CLEAR_MS, easing: Easing.inOut(Easing.cubic) }));
    setAdded(false);
  };

  /** Everything back as it was at the start, with no animation. */
  const rest = () => {
    for (const value of [sheet, drag, fan, fly, flying, flyFrom, shift, landed, badge]) {
      cancelAnimation(value);
      value.set(0);
    }
    newScale.set(1);
    doneScale.set(1);
    stage.set('idle');
    setAdded(false);
  };

  const press = (scale: typeof newScale, down: boolean) => {
    scale.set(withTiming(down ? PRESS_SCALE : 1, { duration: down ? 80 : 140 }));
  };

  const steps: Step[] = [
    { at: TAP_NEW_AT, run: () => press(newScale, true) },
    {
      at: TAP_NEW_AT + TAP_FOR,
      run: () => {
        press(newScale, false);
        open();
      },
    },
    { at: TAP_DONE_AT, run: () => press(doneScale, true) },
    {
      at: TAP_DONE_AT + TAP_FOR,
      run: () => {
        press(doneScale, false);
        close();
      },
    },
    { at: CLEAR_AT, run: clear },
    { at: REST_AT, run: rest },
  ];
  useAutoPlay({ autoPlay, loop: LOOP, steps, rest, touches, hold: USER_HOLD });

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .onStart(() => {
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      drag.set(Math.max(0, event.translationY));
    })
    .onEnd((event) => {
      const far = drag.get() > DISMISS_DRAG || event.velocityY > DISMISS_VELOCITY;
      if (far) {
        // From here the sheet is where the finger left it, and the card starts from there too.
        flyFrom.set(drag.get());
        sheet.set(1 - drag.get() / sheetHeight);
        drag.set(0);
        scheduleOnRN(close);
      } else drag.set(withSpring(0, SHEET_SPRING));
    });

  const scrimStyle = useAnimatedStyle(() => ({
    opacity: SCRIM * Math.min(1, sheet.get()) * (1 - ramp(drag.get(), 0, sheetHeight)),
  }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - sheet.get()) * sheetHeight + drag.get() }],
  }));
  const fanLocal = (index: number) => {
    'worklet';
    return smooth(ramp(fan.get(), (index * FAN_STAGGER) / (FAN_DURATION + FAN_STAGGER * 2), (index * FAN_STAGGER + FAN_DURATION) / (FAN_DURATION + FAN_STAGGER * 2)));
  };
  const backLeft = useAnimatedStyle(() => {
    const t = fanLocal(0);
    return { opacity: t, transform: [{ translateX: -FAN_SPREAD * t }, { translateY: 8 * t }, { rotate: `${-FAN_TILT * t}deg` }] };
  });
  const backRight = useAnimatedStyle(() => {
    const t = fanLocal(1);
    return { opacity: t, transform: [{ translateX: FAN_SPREAD * t }, { translateY: 8 * t }, { rotate: `${FAN_TILT * t}deg` }] };
  });
  const frontStyle = useAnimatedStyle(() => ({ opacity: 1 - flying.get() }));
  const titleStyle = useAnimatedStyle(() => {
    const t = smooth(ramp(fan.get(), 0, 0.5));
    return { opacity: t, transform: [{ scale: lerp(0.8, 1, t) }] };
  });
  const flierStyle = useAnimatedStyle(() => {
    const p = fly.get();
    return {
      opacity: flying.get(),
      left: lerp((width - FAN_WIDTH) / 2, MARGIN, p),
      top: lerp(height - sheetHeight + FAN_TOP + flyFrom.get(), listTop, p),
      width: lerp(FAN_WIDTH, cardWidth, p),
      height: lerp(FAN_HEIGHT, CARD_HEIGHT, p),
      borderRadius: lerp(16, 20, p),
    };
  });
  const clipStyle = useAnimatedStyle(() => ({ borderRadius: lerp(16, 20, fly.get()) }));
  const smallStyle = useAnimatedStyle(() => ({ opacity: 1 - ramp(fly.get(), 0.15, 0.55) }));
  const largeStyle = useAnimatedStyle(() => ({ opacity: ramp(fly.get(), 0.15, 0.55) }));
  const listCardStyle = useAnimatedStyle(() => ({ opacity: landed.get() }));
  const existingStyle = useAnimatedStyle(() => ({ transform: [{ translateY: shift.get() * SLOT }] }));
  const badgeStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, badge.get() * 4), transform: [{ scale: badge.get() }] }));
  const newButtonStyle = useAnimatedStyle(() => ({ transform: [{ scale: newScale.get() }] }));
  const doneButtonStyle = useAnimatedStyle(() => ({ transform: [{ scale: doneScale.get() }] }));

  const count = 2 + later.length + (added ? 1 : 0);

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />

      <View style={[styles.header, { top: insets.top + 8 }]}>
        <View style={styles.pill}>
          <View style={styles.dot} />
          <Text style={styles.pillText}>{`${count} ${count === 1 ? 'TASK' : 'TASKS'}`}</Text>
        </View>
        <View style={styles.headerEnd}>
          <View style={styles.pill}>
            <Ionicons name="calendar-clear-outline" size={12} color={INK} />
            <Text style={styles.pillText}>27 Jan</Text>
          </View>
          <View style={styles.gear}>
            <Ionicons name="settings-outline" size={15} color={INK} />
          </View>
        </View>
      </View>

      <View style={[styles.heading, { top: insets.top + TITLE_TOP }]}>
        <Text style={styles.headingTitle}>Today</Text>
        <Text style={styles.headingCaption}>Monday · Your focus plan</Text>
      </View>

      <Animated.View style={[styles.newSlot, { top: insets.top + NEW_TOP }, newButtonStyle]}>
        <Pressable
          style={styles.newButton}
          accessibilityRole="button"
          accessibilityLabel="New task"
          onPressIn={() => {
            touched();
            press(newScale, true);
          }}
          onPressOut={() => press(newScale, false)}
          onPress={() => {
            clear();
            open();
          }}
        >
          <Text style={styles.newText}>{newLabel}</Text>
        </Pressable>
      </Animated.View>

      <Animated.View style={[styles.slot, styles.lift, { top: listTop, width: cardWidth, shadowColor: task.color }, listCardStyle]}>
        <TaskCard task={task} width={cardWidth} />
        <Animated.View style={[styles.badge, badgeStyle]}>
          <Text style={styles.badgeText}>NEW</Text>
        </Animated.View>
      </Animated.View>
      <Animated.View style={[styles.slot, styles.column, { top: listTop, width: cardWidth }, existingStyle]}>
        {[existing, ...later].map((item) => (
          <View key={item.name} style={[styles.lift, { shadowColor: item.color }]}>
            <TaskCard task={item} width={cardWidth} />
          </View>
        ))}
      </Animated.View>

      <LinearGradient
        pointerEvents="none"
        colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.92)', PAGE]}
        locations={[0, 0.55, 1]}
        style={[styles.fade, { height: TAB_HEIGHT + insets.bottom + 72 }]}
      />
      <View style={[styles.tabs, { bottom: Math.max(insets.bottom, 12) + 4 }]}>
        <View style={[styles.tab, styles.tabActive]}>
          <Ionicons name="checkmark-done" size={20} color="#ffffff" />
          <Text style={styles.tabActiveText}>Tasks</Text>
        </View>
        <View style={styles.tab}>
          <Ionicons name="albums-outline" size={21} color={MUTE} />
        </View>
        <View style={styles.tab}>
          <Ionicons name="stats-chart-outline" size={20} color={MUTE} />
        </View>
        <View style={styles.tab}>
          <Ionicons name="person-circle-outline" size={23} color={MUTE} />
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} />

      <GestureDetector gesture={pan}>
        <Animated.View style={[styles.sheet, { height: sheetHeight + SHEET_OVERSHOOT, paddingBottom: SHEET_OVERSHOOT }, sheetStyle]}>
          <View style={styles.grabber} />
          <Animated.View style={[styles.titleBlock, titleStyle]}>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.caption} lineBreakStrategyIOS="standard">
              {caption}
            </Text>
          </Animated.View>

          <View style={styles.fan}>
            <Animated.View style={[styles.fanCard, styles.backLeft, backLeft]}>
              <BackFace />
            </Animated.View>
            <Animated.View style={[styles.fanCard, styles.backRight, backRight]}>
              <BackFace />
            </Animated.View>
            <Animated.View style={[styles.fanCard, styles.fanFront, frontStyle]}>
              <FanFace task={task} />
            </Animated.View>
          </View>

          <Animated.View style={[styles.doneSlot, { bottom: SHEET_OVERSHOOT + Math.max(insets.bottom, 0) + 20 }, doneButtonStyle]}>
            <Pressable
              style={styles.doneButton}
              accessibilityRole="button"
              onPressIn={() => {
                touched();
                press(doneScale, true);
              }}
              onPressOut={() => press(doneScale, false)}
              onPress={close}
            >
              <Text style={styles.doneText}>{doneLabel}</Text>
            </Pressable>
          </Animated.View>
        </Animated.View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.flier, { backgroundColor: task.color, shadowColor: task.color }, flierStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.clip, clipStyle]}>
          <Animated.View style={[styles.layer, smallStyle]}>
            <FanFace task={task} />
          </Animated.View>
          <Animated.View style={[styles.layer, largeStyle]}>
            <TaskCard task={task} width={cardWidth} />
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

/** The front card of the fan: a small printed card with a title, a lede and three dated lines. */
function FanFace({ task }: { task: Task }) {
  return (
    <View style={styles.face}>
      <LinearGradient
        pointerEvents="none"
        colors={['rgba(255,255,255,0.24)', 'rgba(255,255,255,0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.7, y: 0.8 }}
        style={StyleSheet.absoluteFill}
      />
      <Text style={styles.faceTitle} numberOfLines={2} lineBreakStrategyIOS="standard">
        {task.name}
      </Text>
      <Text style={styles.faceLede} numberOfLines={2} lineBreakStrategyIOS="standard">
        Lessons on building from scratch
      </Text>
      <View style={styles.faceRule} />
      {FRONT_LINES.map(([line, note]) => (
        <View key={line} style={styles.faceLine}>
          <Text style={styles.faceQuote} numberOfLines={2}>
            {`– ${line}`}
          </Text>
          <Text style={styles.faceNote} numberOfLines={1}>
            {note}
          </Text>
        </View>
      ))}
    </View>
  );
}

/** A card behind the front one: only its top edge shows, so a ruled title is enough. */
function BackFace() {
  return (
    <View style={styles.backFace}>
      <View style={[styles.backBar, { width: '70%' }]} />
      <View style={[styles.backBar, { width: '48%', opacity: 0.6 }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  header: { position: 'absolute', left: MARGIN, right: MARGIN, height: PILL_HEIGHT, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerEnd: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  pill: { height: PILL_HEIGHT, paddingHorizontal: 12, borderRadius: PILL_HEIGHT / 2, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: OUTLINE, flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: PAGE },
  pillText: { fontSize: 12, lineHeight: 15, fontWeight: '700', color: INK, letterSpacing: 0.3 },
  dot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: '#34c759' },
  gear: { width: PILL_HEIGHT, height: PILL_HEIGHT, borderRadius: PILL_HEIGHT / 2, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: OUTLINE, alignItems: 'center', justifyContent: 'center' },
  heading: { position: 'absolute', left: MARGIN, right: MARGIN },
  headingTitle: { fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, color: INK },
  headingCaption: { marginTop: 2, fontSize: 15, lineHeight: 20, fontWeight: '500', color: MUTE },
  newSlot: { position: 'absolute', left: MARGIN, right: MARGIN, height: NEW_HEIGHT },
  newButton: { flex: 1, borderRadius: 18, backgroundColor: WELL, borderWidth: 1.5, borderStyle: 'dashed', borderColor: '#d6d7da', alignItems: 'center', justifyContent: 'center' },
  newText: { fontSize: 13, lineHeight: 16, fontWeight: '800', color: WELL_INK, letterSpacing: 1 },
  slot: { position: 'absolute', left: MARGIN },
  column: { gap: 16 },
  // A coloured glow under each card, large and soft, plus the card's own edge.
  lift: { borderRadius: 20, shadowOpacity: 0.28, shadowRadius: 14, shadowOffset: { width: 0, height: 8 } },
  badge: { position: 'absolute', top: 16, right: 16, paddingHorizontal: 8, height: 20, borderRadius: 10, backgroundColor: BADGE, alignItems: 'center', justifyContent: 'center', shadowColor: BADGE, shadowOpacity: 0.4, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  badgeText: { fontSize: 10, lineHeight: 12, fontWeight: '900', color: '#ffffff', letterSpacing: 0.6 },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  tabs: { position: 'absolute', alignSelf: 'center', height: TAB_HEIGHT, paddingHorizontal: 8, borderRadius: TAB_HEIGHT / 2, backgroundColor: '#ffffff', flexDirection: 'row', alignItems: 'center', gap: 4, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(0,0,0,0.06)', shadowColor: '#000000', shadowOpacity: 0.12, shadowRadius: 20, shadowOffset: { width: 0, height: 10 } },
  tab: { height: 44, minWidth: 52, borderRadius: 22, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 6 },
  tabActive: { backgroundColor: INK, paddingHorizontal: 16 },
  tabActiveText: { fontSize: 14, lineHeight: 18, fontWeight: '700', color: '#ffffff' },
  scrim: { backgroundColor: '#000000' },
  sheet: { position: 'absolute', left: 0, right: 0, bottom: -SHEET_OVERSHOOT, backgroundColor: '#ffffff', borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS, alignItems: 'center', shadowColor: '#000000', shadowOpacity: 0.18, shadowRadius: 30, shadowOffset: { width: 0, height: -8 } },
  grabber: { marginTop: 8, width: 36, height: 5, borderRadius: 2.5, backgroundColor: '#e2e3e6' },
  titleBlock: { marginTop: 18, alignItems: 'center', paddingHorizontal: 40 },
  title: { fontSize: 34, lineHeight: 40, fontWeight: '900', letterSpacing: -0.5, color: BLUE },
  caption: { marginTop: 6, fontSize: 15, lineHeight: 20, fontWeight: '500', color: BLUE_SOFT, textAlign: 'center' },
  fan: { position: 'absolute', top: FAN_TOP, width: FAN_WIDTH, height: FAN_HEIGHT },
  fanCard: { position: 'absolute', width: FAN_WIDTH, height: FAN_HEIGHT, borderRadius: 16, overflow: 'hidden' },
  backLeft: { backgroundColor: '#3fc2bc' },
  backRight: { backgroundColor: '#38b5af' },
  fanFront: { backgroundColor: '#4dd0cb', overflow: 'visible', shadowColor: '#0b6f6b', shadowOpacity: 0.3, shadowRadius: 16, shadowOffset: { width: 0, height: 10 } },
  face: { width: FAN_WIDTH, height: FAN_HEIGHT, paddingHorizontal: 12, paddingTop: 14, borderRadius: 16, overflow: 'hidden' },
  faceTitle: { fontSize: 14, lineHeight: 17, fontWeight: '800', letterSpacing: -0.2, color: '#ffffff', textAlign: 'center' },
  faceLede: { marginTop: 3, fontSize: 9, lineHeight: 11, fontWeight: '600', color: 'rgba(255,255,255,0.85)', textAlign: 'center' },
  faceRule: { marginTop: 9, marginBottom: 7, marginHorizontal: -12, height: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(255,255,255,0.35)' },
  faceLine: { marginBottom: 5 },
  faceQuote: { fontSize: 8, lineHeight: 10, fontWeight: '700', color: '#ffffff' },
  faceNote: { fontSize: 7, lineHeight: 9, fontWeight: '500', color: 'rgba(255,255,255,0.72)' },
  backFace: { paddingTop: 14, alignItems: 'center', gap: 5 },
  backBar: { height: 5, borderRadius: 2.5, backgroundColor: 'rgba(255,255,255,0.4)' },
  doneSlot: { position: 'absolute', left: MARGIN, right: MARGIN, height: BUTTON_HEIGHT },
  doneButton: { flex: 1, borderRadius: BUTTON_HEIGHT / 2, backgroundColor: BLUE, alignItems: 'center', justifyContent: 'center', shadowColor: '#1d8fd0', shadowOpacity: 0.35, shadowRadius: 12, shadowOffset: { width: 0, height: 6 } },
  doneText: { fontSize: 16, lineHeight: 20, fontWeight: '900', color: '#ffffff', letterSpacing: 1 },
  flier: { position: 'absolute', shadowOpacity: 0.3, shadowRadius: 14, shadowOffset: { width: 0, height: 8 } },
  clip: { overflow: 'hidden' },
  layer: { position: 'absolute', left: 0, top: 0 },
});
