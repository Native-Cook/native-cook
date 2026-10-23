import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  type SharedValue,
  interpolateColor,
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

import { MAP_RATIO, RouteMap, SuccessArt } from './CheckoutArt';

type FeatherName = ComponentProps<typeof Feather>['name'];

export type CheckoutOption = {
  label: string;
  icon: FeatherName;
  /** The headings of the two sections this option unlocks. */
  timeLabel: string;
  placeLabel: string;
  /** The places "Change" goes through; the first one is shown. */
  places: string[];
};

export type CheckoutChoice = { option: string; hour: string; place: string };

type Props = {
  title?: string;
  /** The names of the second and third step, shown next to their dot while they are current. */
  detailsStep?: string;
  confirmationStep?: string;
  orderLabel?: string;
  total?: string;
  placedLabel?: string;
  optionLabel?: string;
  options?: CheckoutOption[];
  /** The hours of the time strip, in order, and the one it starts on. */
  hours?: string[];
  suggestedHour?: number;
  changeLabel?: string;
  confirmLabel?: string;
  doneLabel?: string;
  successTitle?: string;
  returnLabel?: string;
  /** Makes the three choices and presses Done by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the green Confirm is pressed, and when Done is. Nothing is ordered from here. */
  onConfirm?: (choice: CheckoutChoice) => void;
  onDone?: (choice: CheckoutChoice) => void;
  onReturn?: () => void;
  onClose?: () => void;
};

const OPTIONS: CheckoutOption[] = [
  { label: 'Delivery', icon: 'truck', timeLabel: 'DELIVERY BY', placeLabel: 'DELIVERY TO', places: ['Home', 'Office', 'Studio'] },
  { label: 'Pickup', icon: 'shopping-bag', timeLabel: 'PICKUP BY', placeLabel: 'PICKUP AT', places: ['Mill St counter', 'Harbour kiosk'] },
];
const HOURS = ['7AM', '8AM', '9AM', '10AM', '11AM', '12PM', '1PM', '2PM', '3PM', '4PM', '5PM'];

const PAPER = '#ffffff';
const INK = '#111111';
const GREY = '#8d8d8d';
const FAINT = '#a8a8a8';
const HAIRLINE = '#ececec';
const GREEN = '#34d58a';
const TEAL = '#2a9db3';
const LOCKED = '#858585';
const DRAWER_FILL = '#f4f4f4';
const WELL = '#eaeaea';
// A pill before its choice is made: a suggestion, not yet a choice.
const SUGGESTED = '#dcdcdc';
const RETURN_FILL = '#2c2c2c';

const SIDE = 22;
const HEADER = 48;
const DOT = 26;
const STEP_LINE = 46;
const CARD = 160;
const CARD_PAD = 28;
const CONFIRM = { width: 168, height: 78 };
// The grey drawer under the card is narrower than the card by this much on each side.
const DRAWER_INSET = 12;
const DRAWER_PAD = 22;
// The three sections of the drawer, and where the control of each starts inside it.
const SECTION = [118, 138, 136];
const CONTROL_TOP = [42, 46, 44];
const OPTION = { width: 122, height: 58, gap: 6 };
const STRIP = 72;
const ARROW = 36;
const HOUR = { width: 82, height: 58 };
// In the summary the two picks sit at the card's right edge: the option as plain words, the hour as a pill.
const PICK_RIGHT = 26;
const OPTION_LANDS = { width: 86, height: 24, top: 32 };
const HOUR_LANDS = { width: 82, height: 48, top: 68 };
const PLACE_ROW = 40;
const DONE = 62;
const DONE_SIDE = 30;
const DONE_GAP = 22;
const ART = 280;

const NEVER = ReduceMotion.Never;
// A section unfolds, and the picks fly up, on springs with a little overshoot.
const UNFOLD_SPRING = { damping: 17, stiffness: 150, mass: 1, reduceMotion: NEVER };
const UNFOLD_AFTER = 180;
const FLY_SPRING = { damping: 18, stiffness: 130, mass: 1, reduceMotion: NEVER };
const SLIDE_SPRING = { damping: 17, stiffness: 240, mass: 1, reduceMotion: NEVER };
// A pill that is chosen dips to PRESSED and bounces back.
const PRESSED = 0.9;
const BOUNCE_SPRING = { damping: 8, stiffness: 320, mass: 0.8, reduceMotion: NEVER };
const FILL_MS = 180;
const READY_MS = 380;
const ROUTE_MS = 700;
const FLOOD_MS = 520;
const WON_SPRING = { damping: 13, stiffness: 150, mass: 1, reduceMotion: NEVER };
const LEAVE_MS = 360;
const PRESS_MS = 150;

/** autoPlay: one checkout every LOOP seconds; the moments below are seconds into it. */
export const LOOP = 10;
const OPTION_AT = 0.8;
const HOUR_AT = 2;
const CONFIRM_AT = 3.6;
const DONE_AT = 5.6;
const RETURN_AT = 8.2;
/** After a touch autoPlay waits this long before it starts over. */
const USER_HOLD = 3;

type Step = 'details' | 'summary' | 'success';
type Held = 'option' | 'hour' | 'confirm' | 'done' | 'return' | null;

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, (t - from) / (to - from)));
  return u * u * (3 - 2 * u);
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Check() {
  return (
    <View style={[styles.dot, styles.dotDone]}>
      <Feather name="check" size={13} color={PAPER} />
    </View>
  );
}

/**
 * The name of a step: it takes room only while its step is the current one. `summary` is 0 in the
 * second step and 1 in the third; `last` says which of the two this name belongs to.
 */
function StepName({ text, summary, last }: { text: string; summary: SharedValue<number>; last: boolean }) {
  const [width, setWidth] = useState(0);
  const style = useAnimatedStyle(() => {
    const there = last ? smooth(summary.get(), 0.4, 1) : 1 - smooth(summary.get(), 0, 0.6);
    return { width: (width + 8) * there, opacity: there };
  });
  return (
    <Animated.View style={[styles.stepName, style]}>
      {/* The text lies in a row wider than the name ever is, so it is measured at its full width. */}
      <View style={styles.stepRuler}>
        <Text style={styles.stepLabel} numberOfLines={1} onLayout={(event: LayoutChangeEvent) => setWidth(event.nativeEvent.layout.width)}>
          {text}
        </Text>
      </View>
    </Animated.View>
  );
}

/**
 * The confirming steps of a checkout. Under the order's card a drawer asks one thing at a time:
 * delivery or pickup, the hour, the address; each answer unfolds the next section, and with all
 * three the card's button turns green. Confirm folds the drawer away while the picks fly up into
 * the card, which grows into a summary with a map; Done floods the screen green. Nothing is
 * ordered: `onConfirm` and `onDone` are where the requests go.
 */
export function UnlockingCheckoutSteps({
  title = 'Confirm',
  detailsStep = 'DELIVERY DETAILS',
  confirmationStep = 'CONFIRMATION',
  orderLabel = 'Order 12329',
  total = '$19.11',
  placedLabel = 'Placed on\n3 June 2022',
  optionLabel = 'SELECT AN OPTION',
  options = OPTIONS,
  hours = HOURS,
  suggestedHour = 2,
  changeLabel = 'Change',
  confirmLabel = 'Confirm',
  doneLabel = 'Done',
  successTitle = 'Checkout successful!',
  returnLabel = 'Return to Dashboard',
  autoPlay = false,
  onConfirm,
  onDone,
  onReturn,
  onClose,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // Where the card starts, from the top of the screen.
  const [stageTop, setStageTop] = useState(insets.top + 190);
  const firstHour = Math.min(hours.length - 1, Math.max(0, suggestedHour));

  const [step, setStep] = useState<Step>('details');
  // The option chosen, or null while the first one is only suggested.
  const [option, setOption] = useState<number | null>(null);
  // The hour in the middle of the strip, and whether it has been chosen.
  const [hour, setHour] = useState(firstHour);
  const [hourChosen, setHourChosen] = useState(false);
  const [place, setPlace] = useState(0);
  // What the autoPlay loop holds down.
  const [held, setHeld] = useState<Held>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // How far the second and third sections are unfolded.
  const second = useSharedValue(0);
  const third = useSharedValue(0);
  // 0 while a pill is a suggestion, 1 once it is a choice; its place and its bounce.
  const optionOn = useSharedValue(0);
  const optionAt = useSharedValue(0);
  const optionScale = useSharedValue(1);
  const hourOn = useSharedValue(0);
  const hourScale = useSharedValue(1);
  // The card's button: 0 grey with its arrow down, 1 green with its arrow to the right.
  const ready = useSharedValue(0);
  // 0 with the drawer open, 1 in the summary: the picks fly along it.
  const summary = useSharedValue(0);
  const route = useSharedValue(0);
  // The green sheet growing out of Done, what stands on it, and its fading away at the end.
  const flood = useSharedValue(0);
  const won = useSharedValue(0);
  const checked = useSharedValue(0);
  const staying = useSharedValue(1);

  const shown = options[option ?? 0];
  const places = shown.places;
  const complete = option !== null && hourChosen;
  const choice: CheckoutChoice = { option: shown.label, hour: hours[hour], place: places[place % places.length] };

  const cardWidth = size.width - SIDE * 2;
  const mapWidth = cardWidth - CARD_PAD * 2 + 4;
  const cardSummary = CARD + PLACE_ROW + mapWidth * MAP_RATIO + 16;
  const drawerOpen = SECTION[0] + SECTION[1] + SECTION[2];
  // On a short screen everything under the steps is drawn smaller rather than cut off.
  const fit = Math.min(1, (size.height - stageTop - Math.max(insets.bottom, 12)) / (CARD + drawerOpen + 8));
  const doneTop = stageTop + (cardSummary + DONE_GAP) * fit;

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const unfold = (section: SharedValue<number>) => {
    section.set(withDelay(UNFOLD_AFTER, reducedMotion ? withTiming(1, { duration: 160, reduceMotion: NEVER }) : withSpring(1, UNFOLD_SPRING)));
  };

  const bounce = (scale: SharedValue<number>) => {
    if (reducedMotion) return;
    scale.set(withSequence(withTiming(PRESSED, { duration: 90, reduceMotion: NEVER }), withSpring(1, BOUNCE_SPRING)));
  };

  const pickOption = (index: number) => {
    if (step !== 'details') return;
    setOption(index);
    if (index !== option) setPlace(0);
    optionAt.set(withSpring(index, SLIDE_SPRING));
    optionOn.set(withTiming(1, { duration: FILL_MS, reduceMotion: NEVER }));
    bounce(optionScale);
    unfold(second);
  };

  /** Brings the hour `by` places along into the middle of the strip; a tap on an hour also chooses it. */
  const moveHour = (by: number, choose: boolean) => {
    if (step !== 'details') return;
    const next = Math.min(hours.length - 1, Math.max(0, hour + by));
    setHour(next);
    if (!choose) return;
    setHourChosen(true);
    hourOn.set(withTiming(1, { duration: FILL_MS, reduceMotion: NEVER }));
    bounce(hourScale);
    // The address comes filled in, so the last section completes the form as it unfolds.
    unfold(third);
    ready.set(withDelay(UNFOLD_AFTER, withTiming(1, { duration: READY_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER })));
  };

  const goTo = (next: 'details' | 'summary') => {
    setStep(next);
    const to = next === 'summary' ? 1 : 0;
    summary.set(reducedMotion ? withTiming(to, { duration: 200, reduceMotion: NEVER }) : withSpring(to, FLY_SPRING));
    route.set(to ? withDelay(350, withTiming(1, { duration: ROUTE_MS, easing: Easing.inOut(Easing.cubic), reduceMotion: NEVER })) : 0);
  };

  const confirm = () => {
    if (!complete || step !== 'details') return;
    onConfirm?.(choice);
    goTo('summary');
  };

  const done = () => {
    if (step !== 'summary') return;
    onDone?.(choice);
    setStep('success');
    flood.set(withTiming(1, { duration: reducedMotion ? 200 : FLOOD_MS, easing: Easing.inOut(Easing.cubic), reduceMotion: NEVER }));
    won.set(
      withDelay(
        reducedMotion ? 120 : FLOOD_MS * 0.7,
        reducedMotion ? withTiming(1, { duration: 200, reduceMotion: NEVER }) : withSpring(1, WON_SPRING),
      ),
    );
    checked.set(withDelay(FLOOD_MS + 200, withTiming(1, { duration: 320, easing: Easing.out(Easing.cubic), reduceMotion: NEVER })));
  };

  /** The form as it started. With `covered` it changes at once, behind the green screen. */
  const reset = (covered: boolean) => {
    const back = (value: SharedValue<number>, to = 0) => value.set(covered ? to : withTiming(to, { duration: 300, reduceMotion: NEVER }));
    setStep('details');
    setOption(null);
    setHour(firstHour);
    setHourChosen(false);
    setPlace(0);
    [second, third, optionOn, optionAt, hourOn, ready, summary, route].forEach((value) => back(value));
  };

  const clear = () => {
    flood.set(0);
    won.set(0);
    checked.set(0);
    staying.set(1);
  };

  /** Leaves the success screen: the form is put back behind it, then it fades away. */
  const finish = () => {
    if (step !== 'success') return;
    onReturn?.();
    reset(true);
    staying.set(
      withTiming(0, { duration: LEAVE_MS, reduceMotion: NEVER }, (finished) => {
        if (finished) scheduleOnRN(clear);
      }),
    );
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ pickOption, moveHour, confirm, done, finish, reset, step });
  useEffect(() => {
    latest.current = { pickOption, moveHour, confirm, done, finish, reset, step };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      // A press shows for a moment before it acts, as a finger's would.
      const press = (seconds: number, what: Held, run: () => void) => {
        after(origin + seconds * 1000 - Date.now(), () => setHeld(what));
        after(origin + seconds * 1000 + PRESS_MS - Date.now(), () => {
          setHeld(null);
          run();
        });
      };
      press(OPTION_AT, 'option', () => latest.current.pickOption(0));
      press(HOUR_AT, 'hour', () => latest.current.moveHour(0, true));
      press(CONFIRM_AT, 'confirm', () => latest.current.confirm());
      press(DONE_AT, 'done', () => latest.current.done());
      press(RETURN_AT, 'return', () => latest.current.finish());
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      // The loop starts over from the form as it was given.
      after(USER_HOLD * 1000, () => (latest.current.step === 'success' ? latest.current.finish() : latest.current.reset(false)));
      after(USER_HOLD * 1000 + 900, () => cycle(Date.now()));
    }
    return () => {
      timers.forEach(clearTimeout);
      setHeld(null);
    };
  }, [autoPlay, touches]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const drawerLeft = SIDE + DRAWER_INSET + DRAWER_PAD;
  const pickRight = size.width - SIDE - PICK_RIGHT;

  const cardStyle = useAnimatedStyle(() => ({ height: CARD + (cardSummary - CARD) * Math.max(0, summary.get()) }));
  const totalStyle = useAnimatedStyle(() => ({ color: interpolateColor(summary.get(), [0, 1], [INK, FAINT]) }));
  const leavingStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(summary.get(), 0, 0.4) }));
  const confirmStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(ready.get(), [0, 1], [LOCKED, GREEN]) }));
  const arrowStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${90 * (1 - ready.get())}deg` }] }));
  const arrowGrey = useAnimatedStyle(() => ({ opacity: 1 - ready.get() }));
  const arrowGreen = useAnimatedStyle(() => ({ opacity: ready.get() }));
  const extrasStyle = useAnimatedStyle(() => ({ opacity: smooth(summary.get(), 0.45, 1) }));

  const drawerStyle = useAnimatedStyle(() => {
    const open = SECTION[0] + SECTION[1] * Math.max(0, second.get()) + SECTION[2] * Math.max(0, third.get());
    return { height: open * (1 - Math.min(1, Math.max(0, summary.get()))), opacity: 1 - smooth(summary.get(), 0.2, 0.7) };
  });
  const secondStyle = useAnimatedStyle(() => ({ opacity: smooth(second.get(), 0.2, 0.9) }));
  const thirdStyle = useAnimatedStyle(() => ({ opacity: smooth(third.get(), 0.2, 0.9) }));

  // The two pills live above the card and the drawer, so that they can cross from one to the other.
  const optionPill = useAnimatedStyle(() => {
    const s = summary.get();
    const left = drawerLeft + optionAt.get() * (OPTION.width + OPTION.gap);
    const top = CARD + CONTROL_TOP[0];
    const width = OPTION.width + (OPTION_LANDS.width - OPTION.width) * s;
    return {
      left: left + (pickRight - OPTION_LANDS.width - left) * s,
      top: top + (OPTION_LANDS.top - top) * s,
      width,
      height: OPTION.height + (OPTION_LANDS.height - OPTION.height) * s,
      borderRadius: OPTION.height / 2,
      // Grey as a suggestion, black as a choice, and no pill at all once it has landed in the card.
      backgroundColor: interpolateColor(s, [0, 0.8], [interpolateColor(optionOn.get(), [0, 1], [SUGGESTED, INK]), 'rgba(17,17,17,0)']),
      transform: [{ scale: optionScale.get() }],
    };
  });
  const optionInk = useAnimatedStyle(() => ({ color: interpolateColor(optionOn.get() * (1 - smooth(summary.get(), 0.2, 0.8)), [0, 1], [INK, PAPER]) }));
  const optionLight = useAnimatedStyle(() => ({ opacity: optionOn.get() * (1 - smooth(summary.get(), 0.2, 0.8)) }));
  const optionDark = useAnimatedStyle(() => ({ opacity: 1 - optionOn.get() * (1 - smooth(summary.get(), 0.2, 0.8)) }));

  const hourPill = useAnimatedStyle(() => {
    const s = summary.get();
    const left = (size.width - HOUR.width) / 2;
    const top = CARD + SECTION[0] + CONTROL_TOP[1] + (STRIP - HOUR.height) / 2;
    return {
      left: left + (pickRight - HOUR_LANDS.width - left) * s,
      top: top + (HOUR_LANDS.top - top) * s,
      height: HOUR.height + (HOUR_LANDS.height - HOUR.height) * s,
      opacity: smooth(second.get(), 0.3, 0.9),
      backgroundColor: interpolateColor(hourOn.get(), [0, 1], [SUGGESTED, INK]),
      transform: [{ scale: hourScale.get() }],
    };
  });
  const hourInk = useAnimatedStyle(() => ({ color: interpolateColor(hourOn.get(), [0, 1], [INK, PAPER]) }));

  const doneStyle = useAnimatedStyle(() => ({
    opacity: smooth(summary.get(), 0.6, 1),
    transform: [{ translateY: reducedMotion ? 0 : (1 - Math.min(1, summary.get())) * 18 }],
  }));

  // The green sheet starts as the Done button: it takes the screen's width first, then its height.
  const floodStyle = useAnimatedStyle(() => {
    const f = flood.get();
    const wide = smooth(f, 0, 0.4);
    const tall = smooth(f, 0.08, 1);
    return {
      opacity: f > 0 ? staying.get() : 0,
      left: DONE_SIDE * (1 - wide),
      right: DONE_SIDE * (1 - wide),
      top: doneTop * (1 - tall),
      height: DONE * fit + (size.height - DONE * fit) * tall,
    };
  });
  const ghostStyle = useAnimatedStyle(() => {
    const f = flood.get();
    return { top: doneTop, opacity: f > 0 ? 1 - smooth(f, 0.15, 0.7) : 0, transform: [{ scale: 1 + 1.6 * f }] };
  });
  const wonStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, won.get() * 2.5) * staying.get(),
    transform: [{ scale: reducedMotion ? 1 : 0.7 + 0.3 * won.get() }],
  }));
  const twoStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(summary.get(), 0.3, 0.7) }));
  const twoDoneStyle = useAnimatedStyle(() => ({ opacity: smooth(summary.get(), 0.3, 0.7) }));
  const threeStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(summary.get(), [0.3, 0.8], ['#c9c9c9', INK]) }));

  const inDetails = step === 'details';
  const before = hour > 0 ? hours[hour - 1] : '';
  const next = hour < hours.length - 1 ? hours[hour + 1] : '';

  return (
    <View style={[styles.screen, { paddingTop: insets.top + 14 }]} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style={step === 'success' ? 'light' : 'dark'} />

      <View style={styles.header}>
        <Text style={styles.title} accessibilityRole="header" numberOfLines={1}>
          {title}
        </Text>
        <Pressable style={styles.close} hitSlop={8} accessibilityRole="button" accessibilityLabel="Close" onPress={onClose}>
          <Feather name="x" size={17} color={PAPER} />
        </Pressable>
      </View>

      <View style={styles.steps}>
        <Check />
        <View style={styles.stepLine} />
        <Pressable disabled={step !== 'summary'} accessibilityRole="button" accessibilityLabel={detailsStep} onPress={() => goTo('details')}>
          <Animated.View style={[styles.dot, styles.dotNow, twoStyle]}>
            <Text style={styles.dotNumber}>2</Text>
          </Animated.View>
          <Animated.View style={[styles.dotOver, twoDoneStyle]}>
            <Check />
          </Animated.View>
        </Pressable>
        <StepName text={detailsStep} summary={summary} last={false} />
        <View style={styles.stepLine} />
        <Animated.View style={[styles.dot, threeStyle]}>
          <Text style={styles.dotNumber}>3</Text>
        </Animated.View>
        <StepName text={confirmationStep} summary={summary} last />
      </View>

      <View
        style={[styles.stage, { transform: [{ scale: fit }] }]}
        onLayout={(event) => setStageTop(event.nativeEvent.layout.y)}
        pointerEvents={step === 'success' ? 'none' : 'auto'}
      >
        <Animated.View style={[styles.card, cardStyle]}>
          <Text style={styles.orderLabel}>{orderLabel}</Text>
          <Animated.Text style={[styles.total, totalStyle]}>{total}</Animated.Text>
          <Text style={styles.placed}>{placedLabel}</Text>
          <Animated.View style={[styles.cardRule, leavingStyle]} />

          <Animated.View style={[styles.confirmSlot, leavingStyle]} pointerEvents={inDetails ? 'auto' : 'none'}>
            <Pressable
              disabled={!complete}
              accessibilityRole="button"
              accessibilityLabel={confirmLabel}
              accessibilityState={{ disabled: !complete }}
              onPress={() => {
                tap();
                confirm();
              }}
            >
              {({ pressed }) => (
                <Animated.View
                  style={[
                    styles.confirm,
                    confirmStyle,
                    { transform: [{ scale: pressed || held === 'confirm' ? 0.96 : 1 }], transitionProperty: 'transform', transitionDuration: 110 },
                  ]}
                >
                  <Text style={styles.confirmLabel}>{confirmLabel}</Text>
                  <Animated.View style={[styles.confirmDisc, arrowStyle]}>
                    <Animated.View style={[styles.confirmArrow, arrowGrey]}>
                      <Feather name="arrow-right" size={14} color={LOCKED} />
                    </Animated.View>
                    <Animated.View style={[styles.confirmArrow, arrowGreen]}>
                      <Feather name="arrow-right" size={14} color={GREEN} />
                    </Animated.View>
                  </Animated.View>
                </Animated.View>
              )}
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.extras, extrasStyle]} pointerEvents="none" accessibilityElementsHidden={step !== 'summary'}>
            <View style={styles.placeRow}>
              <Text style={styles.placeHeading}>{shown.placeLabel}</Text>
              <Text style={styles.placeValue}>{choice.place}</Text>
            </View>
            <RouteMap width={mapWidth} drawn={route} />
          </Animated.View>
        </Animated.View>

        <Animated.View
          style={[styles.drawer, drawerStyle]}
          pointerEvents={inDetails ? 'auto' : 'none'}
          accessibilityElementsHidden={!inDetails}
          importantForAccessibility={inDetails ? 'auto' : 'no-hide-descendants'}
        >
          <View style={{ height: SECTION[0] }}>
            <Text style={styles.heading}>{optionLabel}</Text>
            <View style={styles.options} accessibilityRole="radiogroup">
              {options.map((item, index) => (
                <Pressable
                  key={item.label}
                  style={styles.option}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: option === index }}
                  onPress={() => {
                    tap();
                    pickOption(index);
                  }}
                >
                  <Feather name={item.icon} size={16} color={GREY} />
                  <Text style={styles.optionLabel}>{item.label}</Text>
                </Pressable>
              ))}
            </View>
          </View>

          <Animated.View style={[{ height: SECTION[1] }, secondStyle]} pointerEvents={option === null ? 'none' : 'auto'}>
            <View style={styles.rule} />
            <Text style={styles.heading}>{shown.timeLabel}</Text>
            <View style={styles.strip}>
              <Pressable style={styles.arrow} accessibilityRole="button" accessibilityLabel="Earlier" onPress={() => moveHour(-1, false)}>
                <Feather name="chevron-left" size={15} color={hour > 0 ? GREY : SUGGESTED} />
              </Pressable>
              {[before, hours[hour], next].map((label, slot) => (
                <Pressable
                  key={slot}
                  style={styles.hour}
                  disabled={!label}
                  accessibilityRole="button"
                  accessibilityLabel={label}
                  accessibilityState={{ selected: slot === 1 && hourChosen }}
                  onPress={() => {
                    tap();
                    moveHour(slot - 1, true);
                  }}
                >
                  <Animated.Text key={label} style={styles.hourLabel} entering={FadeIn.duration(180)} exiting={FadeOut.duration(120)}>
                    {label}
                  </Animated.Text>
                </Pressable>
              ))}
              <Pressable style={styles.arrow} accessibilityRole="button" accessibilityLabel="Later" onPress={() => moveHour(1, false)}>
                <Feather name="chevron-right" size={15} color={hour < hours.length - 1 ? GREY : SUGGESTED} />
              </Pressable>
            </View>
          </Animated.View>

          <Animated.View style={[{ height: SECTION[2] }, thirdStyle]} pointerEvents={hourChosen ? 'auto' : 'none'}>
            <View style={styles.rule} />
            <Text style={styles.heading}>{shown.placeLabel}</Text>
            <View style={styles.place}>
              <Animated.Text key={choice.place} style={styles.placeName} entering={FadeIn.duration(180)} exiting={FadeOut.duration(120)}>
                {choice.place}
              </Animated.Text>
              <Pressable
                style={styles.change}
                hitSlop={12}
                accessibilityRole="button"
                onPress={() => {
                  tap();
                  setPlace((index) => (index + 1) % places.length);
                }}
              >
                <Text style={styles.changeLabel}>{changeLabel}</Text>
              </Pressable>
            </View>
          </Animated.View>
        </Animated.View>

        <Animated.View style={[styles.doneSlot, doneStyle]} pointerEvents={step === 'summary' ? 'auto' : 'none'}>
          <Pressable
            accessibilityRole="button"
            onPress={() => {
              tap();
              done();
            }}
          >
            {({ pressed }) => (
              <View style={[styles.done, (pressed || held === 'done') && styles.donePressed]}>
                <Text style={styles.doneLabel}>{doneLabel}</Text>
              </View>
            )}
          </Pressable>
        </Animated.View>

        <Animated.View style={[styles.pill, optionPill]} pointerEvents="none">
          <View>
            <Animated.View style={optionDark}>
              <Feather name={shown.icon} size={16} color={INK} />
            </Animated.View>
            <Animated.View style={[styles.iconOver, optionLight]}>
              <Feather name={shown.icon} size={16} color={PAPER} />
            </Animated.View>
          </View>
          <Animated.Text style={[styles.pillLabel, optionInk]} numberOfLines={1}>
            {shown.label}
          </Animated.Text>
        </Animated.View>
        <Animated.View style={[styles.pill, styles.hourPill, hourPill, held === 'hour' && styles.pillHeld]} pointerEvents="none">
          <Animated.Text key={hours[hour]} style={[styles.hourPillLabel, hourInk]} entering={FadeIn.duration(180)}>
            {hours[hour]}
          </Animated.Text>
        </Animated.View>
        {held === 'option' && <View style={[styles.pressRing, { left: drawerLeft, top: CARD + CONTROL_TOP[0] }]} pointerEvents="none" />}
      </View>

      <View style={styles.fill} pointerEvents={step === 'success' ? 'auto' : 'none'}>
        <Animated.View style={[styles.flood, floodStyle]} />
        <Animated.Text style={[styles.ghost, ghostStyle]}>{doneLabel}</Animated.Text>
        <Animated.View
          style={[styles.fill, styles.won, { paddingTop: insets.top }, wonStyle]}
          accessibilityElementsHidden={step !== 'success'}
          importantForAccessibility={step === 'success' ? 'auto' : 'no-hide-descendants'}
        >
          <SuccessArt size={ART} checked={checked} />
          <Text style={styles.wonTitle} accessibilityRole="header" accessibilityLiveRegion="polite">
            {successTitle}
          </Text>
          <Pressable
            style={styles.returnSlot}
            accessibilityRole="button"
            onPress={() => {
              tap();
              finish();
            }}
          >
            {({ pressed }) => (
              <View style={[styles.return, (pressed || held === 'return') && styles.returnPressed]}>
                <Text style={styles.returnLabel}>{returnLabel}</Text>
              </View>
            )}
          </Pressable>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAPER,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  header: {
    height: HEADER,
    paddingLeft: SIDE + 4,
    paddingRight: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    color: INK,
    fontSize: 32,
    lineHeight: 40,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  close: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#c6c6c6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  steps: {
    marginTop: 26,
    height: DOT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dot: {
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotDone: {
    backgroundColor: GREEN,
    borderWidth: 2.5,
    borderColor: '#c6f3df',
  },
  dotNow: {
    backgroundColor: INK,
  },
  dotOver: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  dotNumber: {
    color: PAPER,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '800',
  },
  stepLine: {
    width: STEP_LINE,
    height: 1.5,
    marginHorizontal: 8,
    backgroundColor: '#e4e4e4',
  },
  stepName: {
    height: 14,
    overflow: 'hidden',
  },
  stepRuler: {
    position: 'absolute',
    left: 8,
    top: 0,
    width: 240,
    flexDirection: 'row',
  },
  stepLabel: {
    color: INK,
    fontSize: 9,
    lineHeight: 14,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  stage: {
    marginTop: 24,
    transformOrigin: 'top',
  },
  card: {
    marginHorizontal: SIDE,
    borderRadius: 3,
    borderWidth: 1,
    borderColor: '#efefef',
    backgroundColor: PAPER,
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 22, color: 'rgba(0,0,0,0.08)' }],
    // The drawer slides out from under the card.
    zIndex: 1,
  },
  orderLabel: {
    position: 'absolute',
    left: CARD_PAD,
    top: 30,
    color: FAINT,
    fontSize: 9,
    lineHeight: 12,
  },
  total: {
    position: 'absolute',
    left: CARD_PAD,
    top: 50,
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '800',
    letterSpacing: -0.4,
    fontVariant: ['tabular-nums'],
  },
  placed: {
    position: 'absolute',
    left: CARD_PAD,
    top: 90,
    color: FAINT,
    fontSize: 10.5,
    lineHeight: 15,
  },
  cardRule: {
    position: 'absolute',
    left: 138,
    top: 22,
    width: 1,
    height: CARD - 44,
    backgroundColor: '#f2f2f2',
  },
  confirmSlot: {
    position: 'absolute',
    right: 14,
    top: (CARD - CONFIRM.height) / 2,
  },
  confirm: {
    width: CONFIRM.width,
    height: CONFIRM.height,
    borderRadius: 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 26,
  },
  confirmLabel: {
    color: PAPER,
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '700',
  },
  confirmDisc: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: PAPER,
  },
  confirmArrow: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  extras: {
    position: 'absolute',
    left: CARD_PAD - 2,
    right: CARD_PAD - 2,
    top: CARD - 2,
    borderTopWidth: 1,
    borderTopColor: HAIRLINE,
  },
  placeRow: {
    height: PLACE_ROW,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  placeHeading: {
    color: FAINT,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  placeValue: {
    color: INK,
    fontSize: 10.5,
    lineHeight: 14,
    fontWeight: '800',
  },
  drawer: {
    marginHorizontal: SIDE + DRAWER_INSET,
    paddingHorizontal: DRAWER_PAD,
    backgroundColor: DRAWER_FILL,
    overflow: 'hidden',
  },
  heading: {
    position: 'absolute',
    left: 0,
    top: 17,
    color: INK,
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  rule: {
    height: 1,
    backgroundColor: '#e6e6e6',
  },
  options: {
    position: 'absolute',
    left: 0,
    top: CONTROL_TOP[0],
    flexDirection: 'row',
    gap: OPTION.gap,
  },
  option: {
    width: OPTION.width,
    height: OPTION.height,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  optionLabel: {
    color: GREY,
    fontSize: 11.5,
    lineHeight: 15,
    fontWeight: '700',
  },
  strip: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: CONTROL_TOP[1],
    height: STRIP,
    backgroundColor: WELL,
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  arrow: {
    width: ARROW,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hour: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hourLabel: {
    position: 'absolute',
    color: GREY,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '600',
  },
  place: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: CONTROL_TOP[2],
    height: STRIP,
    backgroundColor: WELL,
    justifyContent: 'center',
  },
  placeName: {
    position: 'absolute',
    left: 24,
    color: '#5a5a5a',
    fontSize: 12.5,
    lineHeight: 17,
    fontWeight: '500',
  },
  change: {
    position: 'absolute',
    right: 14,
  },
  changeLabel: {
    color: TEAL,
    fontSize: 10.5,
    lineHeight: 14,
    fontWeight: '800',
  },
  doneSlot: {
    marginTop: DONE_GAP,
    marginHorizontal: DONE_SIDE,
  },
  done: {
    height: DONE,
    borderRadius: 2,
    backgroundColor: GREEN,
    alignItems: 'center',
    justifyContent: 'center',
  },
  donePressed: {
    backgroundColor: '#4fe3a0',
  },
  doneLabel: {
    color: PAPER,
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '700',
  },
  pill: {
    position: 'absolute',
    zIndex: 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  iconOver: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  pillLabel: {
    fontSize: 11.5,
    lineHeight: 15,
    fontWeight: '700',
  },
  hourPill: {
    width: HOUR.width,
    borderRadius: HOUR.height / 2,
  },
  hourPillLabel: {
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '700',
  },
  pillHeld: {
    opacity: 0.8,
  },
  // What autoPlay's press on the suggested option looks like: a ring round the pill.
  pressRing: {
    position: 'absolute',
    zIndex: 3,
    width: OPTION.width,
    height: OPTION.height,
    borderRadius: OPTION.height / 2,
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  flood: {
    position: 'absolute',
    backgroundColor: GREEN,
  },
  ghost: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: DONE,
    lineHeight: DONE,
    color: PAPER,
    fontSize: 13.5,
    fontWeight: '700',
    textAlign: 'center',
  },
  won: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  wonTitle: {
    marginTop: 30,
    color: PAPER,
    fontSize: 21,
    lineHeight: 26,
    fontWeight: '800',
    letterSpacing: -0.2,
  },
  returnSlot: {
    alignSelf: 'stretch',
    marginTop: 34,
    marginHorizontal: DONE_SIDE,
  },
  return: {
    height: DONE,
    borderRadius: 2,
    backgroundColor: RETURN_FILL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  returnPressed: {
    backgroundColor: '#444444',
  },
  returnLabel: {
    color: PAPER,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '700',
  },
});
