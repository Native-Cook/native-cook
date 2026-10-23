import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  Keyframe,
  ReduceMotion,
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

import { PILE_HEIGHT, Tomatoes } from './Tomatoes';
import { useAutoPlay } from './useAutoPlay';

type Props = {
  /** The length of a focus session, in seconds. */
  duration?: number;
  startLabel?: string;
  holdLabel?: string;
  sheetTitle?: string;
  sheetBody?: string;
  gotItLabel?: string;
  /** Starts, holds Stop to the end and dismisses the sheet by itself every LOOP seconds. */
  autoPlay?: boolean;
  onStart?: () => void;
  /** Called when the hold completes, with the seconds that were left. */
  onStop?: (secondsLeft: number) => void;
};

/** Seconds for one pass of the autoPlay loop. */
export const LOOP = 10;
// The timeline, in seconds from the start of a loop.
// The pile is on screen from the end of one loop to Start, and again from the stop on.
const PRESS_START_AT = 1.25;
const START_AT = 1.4;
const HOLD_AT = 2.6;
const PRESS_GOT_IT_AT = 7.3;
const DISMISS_AT = 7.5;

const IDLE_TOP = '#fde3d2';
const PEACH_TOP = '#f7ae88';
const PEACH_MID = '#fde3d1';
const INK = '#4a3b35';
const MUTE = '#8c7a71';
const BOOKMARK = '#d9a58b';
const TRACK = 'rgba(74,59,53,0.16)';
const BUTTON = '#4a403b';
const BUTTON_PRESSED = '#2e2724';
const CARD = '#f6ece4';
const CARD_TITLE = '#3e2f28';
const CARD_BODY = '#77675e';
const GOT_IT = '#d0504a';
const GOT_IT_PRESSED = '#ab3c37';

const TIMER_SIZE = 84;
const DIGIT_WIDTH = 52;
const COLON_WIDTH = 24;
const TIMER_HEIGHT = 100;
const START_WIDTH = 156;
const START_HEIGHT = 56;
const HOLD_LINE_WIDTH = 140;
const HOLD_LINE_HEIGHT = 4;
const SHEET_MARGIN = 10;
const SHEET_HEIGHT = 244;
const SHEET_RADIUS = 36;
const GOT_IT_WIDTH = 188;
const GOT_IT_HEIGHT = 52;

// The durations of the pieces, in milliseconds.
const HOLD = 2400;
const BACKGROUND = 500;
const FALL = 650;
const RISE = 1500;
const ROLL = 200;
const ROLL_RISE = 12;
const DIM = 0.45;
const DIM_IN = 300;
const SHEET_AFTER = 700;
const PULSE_AFTER = 700;
const FOOTER_AFTER = 700;
const FADE = 180;
const REST_FADE = 350;
const SHEET_SPRING = { damping: 15, stiffness: 180, mass: 1, reduceMotion: ReduceMotion.Never };
// A light tick while the hold fills, as a long press gives on a device.
const TICK = 500;

const ROLL_IN = new Keyframe({
  0: { opacity: 0, transform: [{ translateY: ROLL_RISE }] },
  100: { opacity: 1, transform: [{ translateY: 0 }] },
}).duration(ROLL);
const ROLL_OUT = new Keyframe({
  0: { opacity: 1, transform: [{ translateY: 0 }] },
  100: { opacity: 0, transform: [{ translateY: -ROLL_RISE }] },
}).duration(ROLL);

const timing = (ms: number, easing = Easing.out(Easing.cubic)) => ({ duration: ms, easing, reduceMotion: ReduceMotion.Never });

/** Milliseconds from now to a time on the clock. */
function until(at: number) {
  return at - Date.now();
}

function clock(seconds: number) {
  const left = Math.max(0, seconds);
  return `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
}

/** One character of the timer: a new one rolls up into place while the old one rolls away. */
function Glyph({ char, reduced }: { char: string; reduced: boolean }) {
  const wide = char === ':';
  return (
    <View style={{ width: wide ? COLON_WIDTH : DIGIT_WIDTH, height: TIMER_HEIGHT }}>
      <Animated.Text
        key={char}
        entering={reduced ? undefined : ROLL_IN}
        exiting={reduced ? undefined : ROLL_OUT}
        style={styles.glyph}
      >
        {char}
      </Animated.Text>
    </View>
  );
}

/**
 * A focus timer. "Start Focus" drops the tomatoes away and the day turns peach; "Hold To Stop
 * Focus" fills a line while it is held, and stopping early resets the timer, drops the tomatoes
 * back with bounces and warns in a sheet. The autoPlay loop presses and holds the same controls.
 */
export function HoldStopSheet({
  duration = 300,
  startLabel = 'Start Focus',
  holdLabel = 'Hold To Stop Focus',
  sheetTitle = 'Focus time is less than 5 minutes',
  sheetBody = 'Pomodoros lasting less than 5 minutes will not be recorded. Keep going! Every focus session will get better and better (^_^)',
  gotItLabel = 'Got it',
  autoPlay = false,
  onStart,
  onStop,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [running, setRunning] = useState(false);
  const [left, setLeft] = useState(duration);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [autoPressed, setAutoPressed] = useState<'' | 'start' | 'gotIt'>('');

  const run = useSharedValue(0);
  const footer = useSharedValue(0);
  const fill = useSharedValue(0);
  const fall = useSharedValue(0);
  const rise = useSharedValue(0);
  const dim = useSharedValue(0);
  const sheet = useSharedValue(0);
  const pulse = useSharedValue(1);

  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const ticks = useRef<ReturnType<typeof setInterval> | null>(null);
  const holding = useRef(false);

  const bottom = Math.max(insets.bottom, 12);

  const later = (ms: number, task: () => void) => timers.current.push(setTimeout(task, ms));
  const clearTimers = () => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    if (ticks.current) clearInterval(ticks.current);
    ticks.current = null;
  };
  useEffect(() => clearTimers, []);

  const haptic = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** One second off the timer, each at its own absolute time from the start. */
  const countdown = (origin: number, second: number) => {
    later(until(origin + second * 1000), () => {
      setLeft(duration - second);
      if (second < duration) countdown(origin, second + 1);
    });
  };

  const start = () => {
    if (running) return;
    setAutoPressed('');
    setRunning(true);
    setLeft(duration);
    rise.set(0);
    fall.set(withTiming(1, timing(reducedMotion ? FADE : FALL, Easing.in(Easing.quad))));
    run.set(withTiming(1, timing(BACKGROUND, Easing.inOut(Easing.quad))));
    footer.set(withDelay(FOOTER_AFTER, withTiming(1, timing(FADE))));
    countdown(Date.now(), 1);
    onStart?.();
  };

  const stop = () => {
    holding.current = false;
    if (ticks.current) clearInterval(ticks.current);
    ticks.current = null;
    clearTimers();
    onStop?.(left);
    setRunning(false);
    setLeft(duration);
    setSheetOpen(true);
    run.set(withTiming(0, timing(BACKGROUND, Easing.inOut(Easing.quad))));
    footer.set(withTiming(0, timing(FADE)));
    fill.set(0);
    rise.set(0);
    rise.set(withTiming(1, { duration: reducedMotion ? FADE : RISE, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    dim.set(withTiming(DIM, timing(DIM_IN)));
    sheet.set(reducedMotion ? withDelay(FADE, withTiming(1, timing(FADE))) : withDelay(SHEET_AFTER, withSpring(1, SHEET_SPRING)));
    pulse.set(withDelay(PULSE_AFTER + SHEET_AFTER, withSequence(withTiming(1.08, timing(150)), withTiming(1, timing(250)))));
  };

  const beginHold = (felt: boolean) => {
    if (!running || holding.current) return;
    holding.current = true;
    fill.set(
      withTiming(1, { duration: HOLD, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(stop);
      }),
    );
    if (felt) {
      haptic();
      ticks.current = setInterval(haptic, TICK);
    }
  };

  const endHold = () => {
    if (!holding.current) return;
    holding.current = false;
    if (ticks.current) clearInterval(ticks.current);
    ticks.current = null;
    fill.set(withTiming(0, timing(250)));
  };

  const dismiss = (ms = reducedMotion ? FADE : 450) => {
    setAutoPressed('');
    setSheetOpen(false);
    dim.set(withTiming(0, timing(ms)));
    sheet.set(withTiming(0, timing(ms, Easing.in(Easing.cubic)), (finished) => {
      // The pile is back in place behind the sheet; only the pieces of the animation reset.
      if (!finished) return;
      fall.set(0);
      rise.set(0);
    }));
  };

  const rest = () => {
    clearTimers();
    holding.current = false;
    setRunning(false);
    setLeft(duration);
    setSheetOpen(false);
    setAutoPressed('');
    for (const value of [run, footer, fill, dim, sheet, fall, rise]) cancelAnimation(value);
    for (const value of [run, footer, fill, dim, sheet]) value.set(withTiming(0, timing(REST_FADE)));
    // A pile that is back in place stays; one that is away rises back from below.
    if (rise.get() > 0.99) fall.set(0);
    else fall.set(withTiming(0, timing(REST_FADE)));
    rise.set(0);
  };

  const touched = useAutoPlay(
    autoPlay,
    LOOP,
    [
      [PRESS_START_AT, () => setAutoPressed('start')],
      [START_AT, start],
      [HOLD_AT, () => beginHold(false)],
      [PRESS_GOT_IT_AT, () => setAutoPressed('gotIt')],
      [DISMISS_AT, () => dismiss()],
    ],
    rest,
  );

  const topGradient = useAnimatedStyle(() => ({ opacity: run.get() }));
  const timerStyle = useAnimatedStyle(() => ({
    top: height * (0.22 + 0.1 * run.get()),
  }));
  const focusStyle = useAnimatedStyle(() => ({ opacity: 1 - run.get() }));
  const footerStyle = useAnimatedStyle(() => ({ opacity: footer.get() * (1 - 0.45 * fill.get()) }));
  const lineStyle = useAnimatedStyle(() => ({ width: HOLD_LINE_WIDTH * fill.get() }));
  const startStyle = useAnimatedStyle(() => {
    const down = Math.min(1, fall.get() * 1.6);
    const back = Math.min(1, Math.max(0, (rise.get() - 0.55) / 0.45));
    return {
      opacity: reducedMotion ? 1 - down * (1 - rise.get()) : 1,
      transform: [{ translateY: reducedMotion ? 0 : rise.get() > 0 ? -height * (1 - Easing.bounce(back)) : 480 * down * down }],
    };
  });
  const dimStyle = useAnimatedStyle(() => ({ opacity: dim.get() }));
  const sheetStyle = useAnimatedStyle(() => ({
    opacity: reducedMotion ? sheet.get() : 1,
    transform: [{ translateY: reducedMotion ? 0 : (1 - sheet.get()) * (SHEET_HEIGHT + bottom + SHEET_MARGIN + 24) }],
  }));
  const pulseStyle = useAnimatedStyle(() => ({ transform: [{ scale: pulse.get() }] }));

  const text = clock(left);

  return (
    <View style={styles.screen} onTouchStart={touched}>
      <StatusBar style="dark" />
      <LinearGradient colors={[IDLE_TOP, '#ffffff']} locations={[0, 0.6]} style={StyleSheet.absoluteFill} />
      <Animated.View style={[StyleSheet.absoluteFill, topGradient]} pointerEvents="none">
        <LinearGradient colors={[PEACH_TOP, PEACH_MID, '#ffffff']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
      </Animated.View>

      <View style={[styles.bookmark, { top: insets.top + 6 }]}>
        <Ionicons name="bookmark" size={20} color={BOOKMARK} />
      </View>

      <Animated.View style={[styles.timer, timerStyle]} pointerEvents="none">
        <View style={styles.glyphs} accessible accessibilityRole="timer" accessibilityLabel={text}>
          {text.split('').map((char, index) => (
            <Glyph key={index} char={char} reduced={reducedMotion} />
          ))}
        </View>
        <Animated.View style={[styles.focusRow, focusStyle]}>
          <Text style={styles.focus}>Focus</Text>
          <Ionicons name="chevron-forward" size={16} color={MUTE} />
        </Animated.View>
      </Animated.View>

      <Tomatoes width={width} drop={height} fall={fall} rise={rise} reduced={reducedMotion} />

      <Animated.View style={[styles.startSlot, { bottom: PILE_HEIGHT * 0.5 }, startStyle]} pointerEvents={running || sheetOpen ? 'none' : 'auto'}>
        <Pressable onPress={start} accessibilityRole="button" accessibilityLabel={startLabel}>
          {({ pressed }) => (
            <View style={[styles.start, { backgroundColor: pressed || autoPressed === 'start' ? BUTTON_PRESSED : BUTTON }]}>
              <Text style={styles.startLabel}>{startLabel}</Text>
            </View>
          )}
        </Pressable>
      </Animated.View>

      <Animated.View style={[styles.footer, { bottom: bottom + 12 }, footerStyle]} pointerEvents={running ? 'auto' : 'none'}>
        <Pressable onPressIn={() => beginHold(true)} onPressOut={endHold} hitSlop={16} accessibilityRole="button" accessibilityLabel={holdLabel}>
          <View style={styles.footerHit}>
            <View style={styles.track}>
              <Animated.View style={[styles.line, lineStyle]} />
            </View>
            <Text style={styles.holdLabel}>{holdLabel}</Text>
          </View>
        </Pressable>
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dimStyle]} pointerEvents={sheetOpen ? 'auto' : 'none'} />

      <Animated.View style={[styles.sheet, { left: SHEET_MARGIN, right: SHEET_MARGIN, bottom: Math.max(SHEET_MARGIN, bottom - 8) }, sheetStyle]} pointerEvents={sheetOpen ? 'auto' : 'none'}>
        <View style={styles.grabber} />
        <Text style={styles.sheetTitle}>{sheetTitle}</Text>
        <Text style={styles.sheetBody}>{sheetBody}</Text>
        <Animated.View style={pulseStyle}>
          <Pressable onPress={() => dismiss()} accessibilityRole="button" accessibilityLabel={gotItLabel}>
            {({ pressed }) => (
              <View style={[styles.gotIt, { backgroundColor: pressed || autoPressed === 'gotIt' ? GOT_IT_PRESSED : GOT_IT }]}>
                <Text style={styles.gotItLabel}>{gotItLabel}</Text>
              </View>
            )}
          </Pressable>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ffffff' },
  bookmark: { position: 'absolute', right: 24 },
  timer: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  glyphs: { flexDirection: 'row', height: TIMER_HEIGHT },
  glyph: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontSize: TIMER_SIZE,
    lineHeight: TIMER_HEIGHT,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
    letterSpacing: -1,
  },
  focusRow: { flexDirection: 'row', alignItems: 'center', gap: 2, marginTop: 4 },
  focus: { color: INK, fontSize: 20, lineHeight: 26, fontWeight: '700' },
  startSlot: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  start: {
    width: START_WIDTH,
    height: START_HEIGHT,
    borderRadius: START_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [
      { offsetX: 0, offsetY: 10, blurRadius: 24, color: 'rgba(74,40,25,0.35)' },
      { offsetX: 0, offsetY: 2, blurRadius: 4, color: 'rgba(74,40,25,0.25)' },
    ],
  },
  startLabel: { color: '#ffffff', fontSize: 18, lineHeight: 22, fontWeight: '700', letterSpacing: -0.2 },
  footer: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  footerHit: { alignItems: 'center', paddingHorizontal: 40, paddingVertical: 8, gap: 12 },
  track: { width: HOLD_LINE_WIDTH, height: HOLD_LINE_HEIGHT, borderRadius: HOLD_LINE_HEIGHT / 2, backgroundColor: TRACK, overflow: 'hidden' },
  line: { height: HOLD_LINE_HEIGHT, borderRadius: HOLD_LINE_HEIGHT / 2, backgroundColor: INK },
  holdLabel: { color: INK, fontSize: 16, lineHeight: 20, fontWeight: '700' },
  dim: { backgroundColor: '#000000' },
  sheet: {
    position: 'absolute',
    height: SHEET_HEIGHT,
    borderRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    paddingHorizontal: 28,
    paddingTop: 10,
    alignItems: 'center',
    boxShadow: [
      { offsetX: 0, offsetY: 20, blurRadius: 48, color: 'rgba(40,20,10,0.35)' },
      { offsetX: 0, offsetY: 2, blurRadius: 6, color: 'rgba(40,20,10,0.15)' },
    ],
  },
  grabber: { width: 40, height: 5, borderRadius: 3, backgroundColor: 'rgba(62,47,40,0.18)', marginBottom: 18 },
  sheetTitle: { color: CARD_TITLE, fontSize: 20, lineHeight: 26, fontWeight: '800', textAlign: 'center', letterSpacing: -0.3 },
  sheetBody: { color: CARD_BODY, fontSize: 14, lineHeight: 20, textAlign: 'center', marginTop: 8, marginBottom: 20 },
  gotIt: {
    width: GOT_IT_WIDTH,
    height: GOT_IT_HEIGHT,
    borderRadius: GOT_IT_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 18, color: 'rgba(208,80,74,0.35)' }],
  },
  gotItLabel: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '800' },
});
