// Harbor, a stay-booking form: the screen and the autoPlay script around the reusable DateChipCalendar.
import { Host, Toggle } from '@expo/ui/swift-ui';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Switch, Text, useWindowDimensions, View, type GestureResponderEvent } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Rect, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { formatRange, makeMonths, serial } from './calendar';
import {
  CARD,
  CHIP_HEIGHT,
  DateChipCalendar,
  DAY_ROW,
  GRID_PAD,
  GRID_TOP,
  INK,
  MUTE,
  OPEN_HEIGHT,
  TIDE,
  useDateChipMotion,
  type DateChipMotion,
  type DateRange,
} from './DateChipCalendar';
import {
  CAP_SPRING,
  clamp01,
  easeInOutCubic,
  FOLD_SPRING,
  LOOP,
  mix,
  OPEN_SPRING,
  RESUME_AFTER,
  REVEAL_DELAY,
  REVEAL_IN,
  REVEAL_OUT,
  seg,
  springAt,
  springConfig,
  T,
} from './motion';

const IOS = Platform.OS === 'ios';

const PAPER = '#EEF2EF';
const HAIRLINE = 'rgba(22,38,46,0.09)';
const FILL = '#E6ECE9';

// Layout, in points.
const SIDE = 16;
const HEADER = 72;
const ART_MIN = 64;
const ART_MAX = 220;
const GAP = 12;
const OPTION = 58;
const FACTS = 76;
const NOTE = 20;
const BAR = 76;
const GHOST = 44;
const RATE = 179;
const BREAKFAST = 18;
const LINE = 22;

const FACT_ROWS = [
  { value: '2', label: 'Bedrooms' },
  { value: '4.9', label: 'Guest rating' },
  { value: '40 m', label: 'To the water' },
];

const MONTHS = makeMonths(2026, 10, 7);
const DEC = 1;
const TODAY = serial(2026, 10, 24);
const BLOCKED = [serial(2026, 11, 3), serial(2026, 11, 4), serial(2026, 11, 5), serial(2026, 11, 26), serial(2026, 11, 27)];
const SCRIPT_START = serial(2026, 11, 9);
const SCRIPT_END = serial(2026, 11, 21);
const SCRIPT_LABEL = formatRange(SCRIPT_START, SCRIPT_END);
const DEC_FIRST = MONTHS[DEC].first;
const DEC_OFFSET = MONTHS[DEC].offset;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

const RETURN_CONFIG = springConfig(FOLD_SPRING);
const TAPS = [T.open, T.pick, T.done, T.clear];

function totalLabel(nights: number, breakfast: boolean) {
  const total = nights * (RATE + (breakfast ? BREAKFAST : 0));
  return `$${total.toLocaleString('en-US')} · ${nights} ${nights === 1 ? 'night' : 'nights'}`;
}

function ghostPress(t: number) {
  'worklet';
  let press = 0;
  for (let i = 0; i < TAPS.length; i += 1) {
    const d = t - TAPS[i];
    if (d > -0.1 && d < 0.12) press = Math.max(press, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12);
  }
  // The finger stays down through the drag.
  if (t >= T.dragStart - 0.08 && t <= T.dragStart + T.dragSpan) press = 1;
  return press;
}

function ghostOpacity(t: number) {
  'worklet';
  const a = seg(t, T.ghostIn, 0.2) * (1 - seg(t, T.open + 0.26, 0.2));
  const b = seg(t, T.pick - 0.42, 0.2) * (1 - seg(t, T.done + 0.16, 0.2));
  const c = seg(t, T.clear - 0.45, 0.2) * (1 - seg(t, T.clear + 0.18, 0.2));
  return Math.max(a, b, c);
}

function dayCentre(day: number, gridX: number, gridY: number, cell: number) {
  'worklet';
  const index = day - DEC_FIRST + DEC_OFFSET;
  let row = Math.floor(index / 7);
  let col = index - row * 7;
  if (col > 6.5) {
    row += 1;
    col = 0;
  } else if (col > 6) {
    col = 6;
  }
  return { x: gridX + (col + 0.5) * cell, y: gridY + (row + 0.5) * DAY_ROW };
}

/** Every animated value of the calendar at loop time t. */
/** Writes only real changes, so nothing downstream runs while the pose holds still. */
function put(value: SharedValue<number>, next: number) {
  'worklet';
  if (value.get() !== next) value.set(next);
}

function applyPose(m: DateChipMotion, t: number) {
  'worklet';
  const up = springAt(t - T.open, OPEN_SPRING.response, OPEN_SPRING.ratio);
  const down = springAt(t - T.fold, FOLD_SPRING.response, FOLD_SPRING.ratio);
  put(m.open, t < T.fold ? up : Math.max(0, 1 - down));
  put(m.reveal, seg(t, T.open + REVEAL_DELAY, REVEAL_IN) * (1 - seg(t, T.fold, REVEAL_OUT)));

  const picked = t >= T.pick && t < T.clear;
  put(m.start, picked ? SCRIPT_START : -1);
  put(m.head, picked ? mix(easeInOutCubic(seg(t, T.dragStart, T.dragSpan)), SCRIPT_START, SCRIPT_END) : -1);
  put(m.capStart, picked ? Math.max(0, springAt(t - T.pick, CAP_SPRING.response, CAP_SPRING.ratio)) : 0);
  put(m.capEnd, picked ? Math.max(0, springAt(t - T.dragStart, CAP_SPRING.response, CAP_SPRING.ratio)) : 0);
  put(m.complete, t >= T.commit && t < T.clear ? 1 : 0);

  const rollIn = easeInOutCubic(seg(t, T.commit, T.rollSpan));
  const rollOut = easeInOutCubic(seg(t, T.clear, T.rollSpan));
  put(m.roll, rollIn * (1 - rollOut));
  put(m.hasValue, seg(t, T.commit, 0.16) * (1 - seg(t, T.clear, 0.16)));
  put(m.press, t > T.open - 0.1 && t < T.open + 0.12 ? ghostPress(t) : 0);
}

function HarbourArt({ height }: { height: number }) {
  return (
    <Svg width="100%" height={height} viewBox="0 0 360 132" preserveAspectRatio="xMinYMid slice">
      <Defs>
        <SvgGradient id="harbor-sky" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#23566A" />
          <Stop offset="0.55" stopColor="#E0A77E" />
          <Stop offset="1" stopColor="#F4D7A8" />
        </SvgGradient>
        <SvgGradient id="harbor-sea" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#3E7E86" />
          <Stop offset="0.5" stopColor="#1F5766" />
          <Stop offset="1" stopColor="#143F4C" />
        </SvgGradient>
      </Defs>
      <Rect width="360" height="132" fill="url(#harbor-sky)" />
      <Circle cx="176" cy="70" r="20" fill="#FBE9C6" opacity={0.92} />
      <Path d="M0 82 C 40 66, 86 70, 130 80 C 170 88, 196 76, 232 78 L 232 92 L 0 92 Z" fill="#2C5A63" opacity={0.75} />
      <Rect y="86" width="360" height="46" fill="url(#harbor-sea)" />
      <Path d="M136 96 h 80 M 150 106 h 52 M 164 116 h 28" stroke="#F8DDB1" strokeWidth={2} strokeLinecap="round" opacity={0.5} />
      <Path d="M58 92 V 70 L 76 58 L 94 70 V 92 Z" fill="#F2EADB" />
      <Path d="M54 72 L 76 55 L 98 72" fill="none" stroke="#7C4A38" strokeWidth={4} strokeLinecap="round" strokeLinejoin="round" />
      <Rect x="71" y="78" width="10" height="14" rx="1.5" fill="#34565F" />
      <Path d="M108 98 h 36 l -6 8 h -24 Z" fill="#F2EADB" />
      <Path d="M126 98 V 76 L 140 94 Z" fill="#FBF3E4" />
    </Svg>
  );
}

export type HarborBookingProps = {
  /** Plays the 10 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  onReserve?: (range: DateRange) => void;
};

export function HarborBooking({ autoPlay = true, onReserve }: HarborBookingProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const playing = autoPlay && !reduced;

  const motion = useDateChipMotion(DEC);
  const [range, setRange] = useState<DateRange | null>(null);
  // The total keeps the last range's nights while it rolls out, so a cleared range never flashes the script's.
  const [shownNights, setShownNights] = useState(SCRIPT_END - SCRIPT_START);
  const [guests, setGuests] = useState(2);
  const [breakfast, setBreakfast] = useState(false);

  const clock = useSharedValue(0);
  const mode = useSharedValue(playing ? MODE_AUTO : MODE_MANUAL);
  const touches = useSharedValue(0);
  const seen = useSharedValue(0);
  const idle = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostS = useSharedValue(0);

  const cardWidth = width - SIDE * 2;
  const barHeight = BAR + Math.max(insets.bottom, 12);
  // The picture takes what is left, as long as the open calendar still clears the bar.
  const room = height - insets.top - HEADER - GAP - barHeight;
  const fitsOpen = room - OPEN_HEIGHT - 8;
  const fillsClosed = room - CHIP_HEIGHT - GAP - (OPTION * 2 + FACTS + NOTE + GAP * 3) - 16;
  const artHeight = Math.max(ART_MIN, Math.min(ART_MAX, fitsOpen, fillsClosed));
  const chipTop = insets.top + HEADER + artHeight + GAP;
  const cell = (cardWidth - GRID_PAD * 2) / 7;
  const gridX = SIDE + GRID_PAD;
  const gridY = chipTop + CHIP_HEIGHT + GRID_TOP;
  const chipX = SIDE + cardWidth / 2;
  const chipY = chipTop + CHIP_HEIGHT / 2;
  const doneX = SIDE + cardWidth - 42;
  const clearX = SIDE + cardWidth - 27;

  const held = useSharedValue(0);
  const bump = useCallback(() => {
    touches.set(touches.get() + 1);
  }, [touches]);
  const onTouch = useCallback((event: GestureResponderEvent) => {
    held.set(event.nativeEvent.touches.length);
    touches.set(touches.get() + 1);
  }, [held, touches]);

  const resetForLoop = useCallback(() => {
    setRange(null);
    setShownNights(SCRIPT_END - SCRIPT_START);
  }, []);

  const takeOver = useCallback(() => {
    'worklet';
    const m = motion;
    ghostO.set(withTiming(0, { duration: 120 }));
    m.press.set(0);
    const head = m.head.get();
    if (head >= 0) m.head.set(withTiming(Math.round(head), { duration: 100 }));
    if (m.start.get() >= 0 && Math.abs(head - m.start.get()) > 0.5) {
      m.complete.set(1);
      m.capEnd.set(withTiming(1, { duration: 100 }));
      m.hasValue.set(withTiming(1, { duration: 160 }));
      m.roll.set(withTiming(1, { duration: 200 }));
    } else {
      m.hasValue.set(withTiming(0, { duration: 160 }));
      m.roll.set(withTiming(0, { duration: 200 }));
    }
    if (m.open.get() > 0.5) {
      m.open.set(withSpring(1, springConfig(OPEN_SPRING)));
      m.reveal.set(withTiming(1, { duration: 160 }));
    } else {
      m.open.set(withSpring(0, RETURN_CONFIG));
      m.reveal.set(withTiming(0, { duration: 160 }));
    }
    m.adoptTick.set(m.adoptTick.get() + 1);
  }, [ghostO, motion]);

  const settle = useCallback(() => {
    'worklet';
    const m = motion;
    m.open.set(withSpring(0, RETURN_CONFIG));
    m.reveal.set(withTiming(0, { duration: REVEAL_OUT * 1000 }));
    m.roll.set(withTiming(0, { duration: T.rollSpan * 1000 }));
    m.hasValue.set(withTiming(0, { duration: 160 }));
    m.page.set(withSpring(DEC, RETURN_CONFIG));
  }, [motion]);

  const frameLoop = useFrameCallback(frame => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        if (mode.get() === MODE_AUTO) takeOver();
        mode.set(MODE_MANUAL);
      }
    }
    if (mode.get() === MODE_MANUAL) {
      if (held.get() > 0) {
        idle.set(0);
        return;
      }
      idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        settle();
        mode.set(MODE_RETURN);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      if (Math.abs(motion.open.get()) < 0.01 && motion.roll.get() < 0.01 && Math.abs(motion.page.get() - DEC) < 0.01) {
        motion.open.set(0);
        motion.reveal.set(0);
        motion.roll.set(0);
        motion.hasValue.set(0);
        motion.page.set(DEC);
        motion.start.set(-1);
        motion.head.set(-1);
        motion.complete.set(0);
        motion.capStart.set(0);
        motion.capEnd.set(0);
        motion.resetTick.set(motion.resetTick.get() + 1);
        scheduleOnRN(resetForLoop);
        clock.set(0);
        mode.set(MODE_AUTO);
      }
      return;
    }

    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    applyPose(motion, t);

    // The ghost finger: chip, first day, along the range, Done, clear.
    const first = dayCentre(SCRIPT_START, gridX, gridY, cell);
    let x = chipX;
    let y = chipY;
    if (t >= T.open + 0.3 && t < T.dragStart) {
      const u = easeInOutCubic(seg(t, T.pick - 0.42, 0.34));
      x = mix(u, chipX, first.x);
      y = mix(u, chipY, first.y);
    } else if (t >= T.dragStart && t < T.commit) {
      const h = dayCentre(motion.head.get(), gridX, gridY, cell);
      x = h.x;
      y = h.y;
    } else if (t >= T.commit && t < T.fold + 0.2) {
      const end = dayCentre(SCRIPT_END, gridX, gridY, cell);
      const u = easeInOutCubic(seg(t, T.commit + 0.3, 0.44));
      x = mix(u, end.x, doneX);
      y = mix(u, end.y, chipY);
    } else if (t >= T.clear - 0.5) {
      x = clearX;
      y = chipY;
    }
    ghostX.set(x);
    ghostY.set(y);
    ghostO.set(ghostOpacity(t));
    ghostS.set(ghostPress(t));
  }, playing);

  useEffect(() => {
    frameLoop.setActive(playing);
    if (playing || mode.get() !== MODE_AUTO) return;
    // Reduced motion or autoPlay off: the resting form, nothing scripted.
    mode.set(MODE_MANUAL);
    ghostO.set(0);
    motion.open.set(0);
    motion.reveal.set(0);
    motion.roll.set(0);
    motion.hasValue.set(0);
    motion.start.set(-1);
    motion.head.set(-1);
    motion.complete.set(0);
    motion.capStart.set(0);
    motion.capEnd.set(0);
    motion.press.set(0);
    motion.resetTick.set(motion.resetTick.get() + 1);
  }, [frameLoop, ghostO, mode, motion, playing]);

  const onChange = useCallback((next: DateRange | null) => {
    setRange(next);
    if (next) setShownNights(next.end - next.start);
  }, []);

  const pushStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: motion.open.get() * (OPEN_HEIGHT - CHIP_HEIGHT) }],
  }));
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: mode.get() === MODE_AUTO ? ghostO.get() * 0.9 : 0,
    transform: [
      { translateX: ghostX.get() - GHOST / 2 },
      { translateY: ghostY.get() - GHOST / 2 },
      { scale: 1 - 0.16 * ghostS.get() },
    ],
  }));
  const totalStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -clamp01(motion.roll.get()) * LINE }] }));
  const reserveStyle = useAnimatedStyle(() => ({ opacity: 0.42 + 0.58 * motion.hasValue.get() }));

  const nights = shownNights;
  const total = useMemo(() => totalLabel(nights, breakfast), [breakfast, nights]);

  return (
    <View style={styles.screen} onTouchStart={onTouch} onTouchEnd={onTouch} onTouchCancel={onTouch}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Text style={styles.brand}>Harbor</Text>
        <Text style={styles.title}>Book your stay</Text>
      </View>

      <View style={[styles.art, { top: insets.top + HEADER, height: artHeight }]}>
        <HarbourArt height={artHeight} />
        <View style={styles.artCaption}>
          <Text style={styles.artTitle}>Saltline Cottage</Text>
          <Text style={styles.artMeta}>{`Port Ellis · $${RATE} a night`}</Text>
        </View>
      </View>

      <Animated.View style={[styles.below, { top: chipTop + CHIP_HEIGHT + GAP }, pushStyle]}>
        <View style={styles.option}>
          <View style={styles.optionText}>
            <Text style={styles.optionTitle}>Guests</Text>
            <Text style={styles.optionMeta}>Sleeps up to 4</Text>
          </View>
          <View style={styles.stepper}>
            <Pressable
              onPress={() => setGuests(value => Math.max(1, value - 1))}
              style={styles.step}
              hitSlop={6}
              accessibilityRole="button"
              accessibilityLabel="Fewer guests"
            >
              <View style={styles.stepBar} />
            </Pressable>
            <Text style={styles.stepValue}>{guests}</Text>
            <Pressable
              onPress={() => setGuests(value => Math.min(4, value + 1))}
              style={styles.step}
              hitSlop={6}
              accessibilityRole="button"
              accessibilityLabel="More guests"
            >
              <View style={styles.stepBar} />
              <View style={[styles.stepBar, styles.stepBarTurn]} />
            </Pressable>
          </View>
        </View>
        <View style={styles.option}>
          <View style={styles.optionText}>
            <Text style={styles.optionTitle}>Breakfast</Text>
            <Text style={styles.optionMeta}>{`$${BREAKFAST} a night`}</Text>
          </View>
          {IOS ? (
            <Host matchContents colorScheme="light">
              <Toggle isOn={breakfast} onIsOnChange={setBreakfast} />
            </Host>
          ) : (
            <Switch value={breakfast} onValueChange={setBreakfast} trackColor={{ true: TIDE }} />
          )}
        </View>
        <View style={styles.facts}>
          {FACT_ROWS.map(fact => (
            <View key={fact.label} style={styles.fact}>
              <Text style={styles.factValue}>{fact.value}</Text>
              <Text style={styles.factLabel}>{fact.label}</Text>
            </View>
          ))}
        </View>
        <View style={styles.note}>
          <Text style={styles.noteText}>Free cancellation until 7 days before check-in.</Text>
        </View>
      </Animated.View>

      <View style={[styles.chipSlot, { top: chipTop }]}>
        <DateChipCalendar
          motion={motion}
          months={MONTHS}
          initialPage={DEC}
          today={TODAY}
          blocked={BLOCKED}
          width={cardWidth}
          scriptLabel={SCRIPT_LABEL}
          reducedMotion={reduced}
          onChange={onChange}
          onActivity={bump}
        />
      </View>

      <View style={[styles.bar, { height: barHeight, paddingBottom: Math.max(insets.bottom, 12) }]}>
        <View style={styles.totalWrap}>
          <Text style={styles.totalCaption}>Total</Text>
          <View style={styles.rollClip}>
            <Animated.View style={totalStyle}>
              <View style={styles.rollLine}><Text style={styles.totalMuted}>Select dates</Text></View>
              <View style={styles.rollLine}><Text style={styles.totalValue} numberOfLines={1}>{total}</Text></View>
            </Animated.View>
          </View>
        </View>
        <Animated.View style={reserveStyle}>
          <Pressable
            disabled={!range}
            onPress={() => {
              if (range) onReserve?.(range);
            }}
            style={styles.reserve}
            accessibilityRole="button"
            accessibilityLabel="Reserve"
            accessibilityState={{ disabled: !range }}
          >
            <Text style={styles.reserveText}>Reserve</Text>
          </Pressable>
        </Animated.View>
      </View>

      <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  header: { paddingHorizontal: SIDE + 4 },
  brand: { fontSize: 15, fontWeight: '700', color: TIDE },
  title: { marginTop: 1, fontSize: 28, lineHeight: 34, fontWeight: '700', color: INK },
  art: { position: 'absolute', left: SIDE, right: SIDE, borderRadius: 20, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: '#23566A' },
  artCaption: { position: 'absolute', left: 14, bottom: 10 },
  artTitle: { fontSize: 17, fontWeight: '700', color: '#FFF9EE' },
  artMeta: { fontSize: 13, fontWeight: '500', color: 'rgba(255,249,238,0.86)' },
  chipSlot: { position: 'absolute', left: SIDE },
  below: { position: 'absolute', left: SIDE, right: SIDE, gap: GAP },
  option: {
    height: OPTION,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    boxShadow: '0 1px 2px rgba(16,40,48,0.07), 0 6px 14px rgba(16,40,48,0.05)',
  },
  optionText: { flex: 1 },
  optionTitle: { fontSize: 16, fontWeight: '600', color: INK },
  optionMeta: { fontSize: 13, color: MUTE },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  step: { width: 32, height: 32, borderRadius: 16, backgroundColor: FILL, alignItems: 'center', justifyContent: 'center' },
  stepBar: { position: 'absolute', width: 12, height: 2, borderRadius: 1, backgroundColor: INK },
  stepBarTurn: { transform: [{ rotate: '90deg' }] },
  stepValue: { minWidth: 16, textAlign: 'center', fontSize: 16, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  facts: { height: FACTS, flexDirection: 'row', gap: 8 },
  fact: {
    flex: 1,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: FILL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  factValue: { fontSize: 18, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'] },
  factLabel: { marginTop: 1, fontSize: 12, color: MUTE },
  note: { height: NOTE, justifyContent: 'center', paddingHorizontal: 4 },
  noteText: { fontSize: 13, color: MUTE },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SIDE + 4,
    backgroundColor: CARD,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: HAIRLINE,
    boxShadow: '0 -6px 18px rgba(16,40,48,0.06)',
  },
  totalWrap: { flex: 1, marginRight: 12 },
  totalCaption: { fontSize: 13, fontWeight: '500', color: MUTE },
  rollClip: { height: LINE, overflow: 'hidden' },
  rollLine: { height: LINE, justifyContent: 'center' },
  totalMuted: { fontSize: 17, fontWeight: '600', color: MUTE },
  totalValue: { fontSize: 17, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'] },
  reserve: { height: 48, paddingHorizontal: 28, borderRadius: 24, backgroundColor: TIDE, alignItems: 'center', justifyContent: 'center' },
  reserveText: { fontSize: 17, fontWeight: '600', color: '#F7FBFA' },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(90,110,116,0.34)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.72)',
  },
});
