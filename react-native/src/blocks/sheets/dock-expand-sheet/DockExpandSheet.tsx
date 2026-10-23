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
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { SkyPage } from './SkyPage';
import { type Step, useAutoPlay } from './use-auto-play';

export type Mode = {
  label: string;
  /** A line under the label. */
  caption?: string;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
};

type Props = {
  /** The rows of the list the dock opens into. */
  modes?: Mode[];
  /** The mode that starts out ticked, by place in `modes`. */
  selected?: number;
  /** Plays the whole interaction by itself, over and over, and gives way to a touch. */
  autoPlay?: boolean;
  place?: string;
  title?: string;
  hint?: string;
  travelHint?: string;
  times?: string[][];
  onSelect?: (mode: Mode, index: number) => void;
};

const MODES: Mode[] = [
  { label: 'Classic', caption: 'Sunrise, noon and sunset', icon: 'star', color: '#4b8df8' },
  { label: 'Daily', caption: 'Your day around the light', icon: 'sunny', color: '#f7a23b' },
  { label: 'Photography', caption: 'Golden and blue hours', icon: 'camera', color: '#c057ea' },
  { label: 'Mindfulness', caption: 'Quiet moments to pause', icon: 'leaf', color: '#44d07a' },
  { label: 'Hiking', caption: 'Daylight left on the trail', icon: 'walk', color: '#d6a878' },
  { label: 'Moon Watching', caption: 'Moonrise and phase', icon: 'moon', color: '#8b6cf6' },
];

/** The autoPlay loop, in seconds from its start. */
export const LOOP = 10;
const PRESS_AT = 2.6;
const OPEN_AT = 2.9;
const PICK_AT = 4.6;
const CLOSE_AT = 6.2;
const TRAVEL_AT = 7.2;
const TRAVEL_BACK_AT = 9.1;
const REST_AT = 9.7;
// The mode the loop moves the tick to, by place in the list.
const PICKED = 2;
// Seconds a touch keeps the loop away.
const USER_HOLD = 3;

const NIGHT = '#05060a';
const GLASS = 'rgba(255,255,255,0.1)';
const GLASS_PRESSED = 'rgba(255,255,255,0.25)';
const GLASS_EDGE = 'rgba(255,255,255,0.12)';
const MUTED = 'rgba(255,255,255,0.5)';
const PILL = '#161c2c';
const LIST = ['#101b33', '#0a0d14'] as const;
const INK = '#ffffff';
const SOFT = 'rgba(255,255,255,0.8)';
const YELLOW = '#e9ff70';
const TICK_GREEN = '#b8e04c';
const LOCATION = '#3b9be0';

const MARGIN = 20;
const CONTROL = 48;
const CENTER_WIDTH = 136;
const MERGED_HEIGHT = 52;
const DOCK_LIFT = 32;
const TOOLTIP_HEIGHT = 30;
const TOOLTIP_GAP = 44;
const LINE = 1.5;
const ROW = 68;
const ROWS_TOP = 84;
const HEADER = 52;
const TRAVEL_TIP_WIDTH = 262;
const PRESS_SCALE = 0.92;
const PULSE = 0.03;
const PULSE_MS = 625;
const DISMISS_DRAG = 80;

const MORPH_SPRING = { duration: 550, dampingRatio: 0.88 };
const TICK_MS = 250;
const TRAVEL_MS = 1600;
const FADE_MS = 400;
const REDUCED_MS = 160;

const TIMES = [
  ['Sunrise', '6:08'],
  ['Golden hour', '7:12'],
  ['Solar noon', '12:41'],
  ['Sunset', '19:14'],
];

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function ramp(value: number, from: number, to: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)));
  return t * t * (3 - 2 * t);
}

/**
 * A night-sky screen with a dock of three glass controls. Pressing the sun button merges the dock
 * into one "Modes" pill that grows upward into a full-screen list, in one container whose frame,
 * corner radius and contents move together; closing runs the same motion back. A tooltip tied to
 * the button by a line breathes while the dock is at rest.
 */
export function DockExpandSheet({
  modes = MODES,
  selected = 1,
  autoPlay = false,
  place = 'Lisbon',
  title = 'Modes',
  hint = 'Modes to match your routine',
  travelHint = 'Long press and slide to time travel',
  times = TIMES,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const dockTop = height - insets.bottom - DOCK_LIFT - CONTROL;
  const pillTop = dockTop - (MERGED_HEIGHT - CONTROL) / 2;
  const horizon = insets.top + 250;
  const rulerTop = horizon + 36;

  // 0 dock, 1 full screen.
  const morph = useSharedValue(0);
  const pulse = useSharedValue(0);
  const press = useSharedValue(0);
  // Which tooltip shows: 0 the first, 1 the second.
  const tip = useSharedValue(0);
  const ruler = useSharedValue(0);
  const tick = useSharedValue(selected);

  const [mode, setMode] = useState(selected);
  const [isOpen, setIsOpen] = useState(false);
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  // Whether the list is open, for the timers, which would read a stale state.
  const up = useSharedValue(false);

  const move = (to: number) => (reducedMotion ? withTiming(to, { duration: REDUCED_MS }) : withSpring(to, MORPH_SPRING));

  const startPulse = () => {
    if (reducedMotion) return;
    pulse.set(withRepeat(withSequence(withTiming(1, { duration: PULSE_MS, easing: Easing.inOut(Easing.sin) }), withTiming(0, { duration: PULSE_MS, easing: Easing.inOut(Easing.sin) })), -1));
  };
  const stopPulse = () => {
    cancelAnimation(pulse);
    pulse.set(withTiming(0, { duration: 120 }));
  };

  const open = () => {
    if (up.get()) return;
    up.set(true);
    setIsOpen(true);
    stopPulse();
    morph.set(move(1));
  };
  const close = () => {
    if (!up.get()) return;
    up.set(false);
    setIsOpen(false);
    morph.set(move(0));
  };
  const choose = (index: number) => {
    setMode(index);
    tick.set(withTiming(index, { duration: reducedMotion ? REDUCED_MS : TICK_MS, easing: Easing.out(Easing.cubic) }));
    onSelect?.(modes[index], index);
  };
  const setPress = (down: boolean) => press.set(withTiming(down ? 1 : 0, { duration: down ? 80 : 160 }));
  const travel = (on: boolean) => {
    tip.set(withTiming(on ? 1 : 0, { duration: FADE_MS }));
    ruler.set(on ? withTiming(1, { duration: TRAVEL_MS, easing: Easing.linear }) : withTiming(0, { duration: FADE_MS }));
  };

  /** Everything back as it was at the start, with no animation. */
  const rest = () => {
    for (const value of [morph, pulse, press, tip, ruler]) {
      cancelAnimation(value);
      value.set(0);
    }
    cancelAnimation(tick);
    tick.set(selected);
    up.set(false);
    setIsOpen(false);
    setMode(selected);
  };

  const steps: Step[] = [
    { at: 0, run: startPulse },
    { at: PRESS_AT, run: () => setPress(true) },
    {
      at: OPEN_AT,
      run: () => {
        setPress(false);
        open();
      },
    },
    { at: PICK_AT, run: () => choose(PICKED) },
    { at: CLOSE_AT, run: close },
    { at: TRAVEL_AT, run: () => travel(true) },
    { at: TRAVEL_BACK_AT, run: () => travel(false) },
    { at: REST_AT, run: rest },
  ];
  useAutoPlay({ autoPlay, loop: LOOP, steps, rest, touches, hold: USER_HOLD });

  const swipe = Gesture.Pan()
    .onStart(() => {
      scheduleOnRN(touched);
    })
    .onEnd((event) => {
      if (event.translationY > DISMISS_DRAG) scheduleOnRN(close);
    });

  const pageStyle = useAnimatedStyle(() => {
    const t = ramp(morph.get(), 0.2, 1);
    return { opacity: 1 - t, transform: [{ scale: lerp(1, 0.94, t) }] };
  });
  const tipOneStyle = useAnimatedStyle(() => ({ opacity: 1 - tip.get() }));
  const tipTwoStyle = useAnimatedStyle(() => ({ opacity: tip.get() }));
  const pulseStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + PULSE * pulse.get() }] }));
  const controlsStyle = useAnimatedStyle(() => ({ opacity: 1 - ramp(morph.get(), 0, 0.2) }));
  const centerStyle = useAnimatedStyle(() => ({ transform: [{ scaleX: lerp(1, (width - MARGIN * 2) / CENTER_WIDTH, ramp(morph.get(), 0, 0.3)) }] }));
  const sunStyle = useAnimatedStyle(() => ({
    transform: [{ scale: lerp(1, PRESS_SCALE, press.get()) }],
    backgroundColor: press.get() > 0.5 ? GLASS_PRESSED : GLASS,
  }));
  const frameStyle = useAnimatedStyle(() => {
    const m = morph.get();
    const grow = ramp(m, 0.2, 1);
    return {
      opacity: ramp(m, 0, 0.12),
      left: lerp(MARGIN, 0, grow),
      width: lerp(width - MARGIN * 2, width, grow),
      top: lerp(pillTop, 0, grow),
      height: lerp(MERGED_HEIGHT, height, grow),
      borderRadius: lerp(MERGED_HEIGHT / 2, 0, grow),
    };
  });
  const listFadeStyle = useAnimatedStyle(() => ({ opacity: ramp(morph.get(), 0.3, 0.8) }));
  const headerStyle = useAnimatedStyle(() => ({ top: lerp(0, insets.top + 4, ramp(morph.get(), 0.2, 1)) }));
  const tickStyle = useAnimatedStyle(() => ({ transform: [{ translateY: tick.get() * ROW }] }));

  return (
    <GestureHandlerRootView style={[styles.screen, { backgroundColor: NIGHT }]}>
      <StatusBar style="light" />

      <Animated.View style={[StyleSheet.absoluteFill, pageStyle]}>
        <SkyPage width={width} horizon={horizon} rulerTop={rulerTop} ruler={ruler} times={times} />

        <Animated.View style={[styles.tip, { left: MARGIN, top: dockTop - TOOLTIP_GAP - TOOLTIP_HEIGHT }, tipOneStyle]}>
          <View style={[styles.line, { left: CONTROL / 2 - LINE / 2, top: TOOLTIP_HEIGHT, height: TOOLTIP_GAP }]} />
          <Animated.View style={[styles.tipPill, pulseStyle]}>
            <Text style={styles.tipText}>{hint}</Text>
            <View style={styles.tipClose}>
              <Ionicons name="close" size={10} color={YELLOW} />
            </View>
          </Animated.View>
        </Animated.View>

        <Animated.View style={[styles.tip, { left: (width - TRAVEL_TIP_WIDTH) / 2, top: rulerTop + 92 }, tipTwoStyle]}>
          <View style={[styles.line, { left: TRAVEL_TIP_WIDTH / 2 - LINE / 2, top: -48, height: 48 }]} />
          <View style={[styles.dotEnd, { left: TRAVEL_TIP_WIDTH / 2 - 4, top: -52 }]} />
          <View style={[styles.tipPill, { width: TRAVEL_TIP_WIDTH }]}>
            <Text style={styles.tipText}>{travelHint}</Text>
            <View style={styles.tipClose}>
              <Ionicons name="close" size={10} color={YELLOW} />
            </View>
          </View>
        </Animated.View>
      </Animated.View>

      <Animated.View pointerEvents={isOpen ? 'none' : 'auto'} style={[styles.dock, { top: dockTop }, controlsStyle]}>
        <Animated.View style={[styles.sunWell, sunStyle]}>
          <Pressable
            style={styles.control}
            accessibilityRole="button"
            accessibilityLabel="Modes"
            onPressIn={() => {
              touched();
              setPress(true);
            }}
            onPressOut={() => setPress(false)}
            onPress={open}
          >
            <Ionicons name="sunny" size={21} color={INK} />
          </Pressable>
        </Animated.View>
        <Animated.View style={[styles.center, centerStyle]}>
          <Ionicons name="navigate" size={14} color={LOCATION} />
          <Text style={styles.place}>{place}</Text>
        </Animated.View>
        <View style={[styles.control, styles.glass]}>
          <Ionicons name="grid" size={18} color={SOFT} />
        </View>
      </Animated.View>

      <Animated.View pointerEvents={isOpen ? 'auto' : 'none'} style={[styles.frame, { backgroundColor: PILL }, frameStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, listFadeStyle]}>
          <LinearGradient colors={LIST} style={StyleSheet.absoluteFill} />
        </Animated.View>

        <View style={{ position: 'absolute', left: 0, right: 0, top: insets.top + ROWS_TOP }}>
          {modes.map((item, index) => (
            <ModeRow key={item.label} mode={item} index={index} morph={morph} selected={index === mode} onPress={() => { touched(); choose(index); }} />
          ))}
          <Animated.View style={[styles.tick, tickStyle]} pointerEvents="none">
            <View style={styles.tickBadge}>
              <Ionicons name="checkmark" size={15} color="#0b0f17" />
            </View>
          </Animated.View>
        </View>

        <GestureDetector gesture={swipe}>
          <Animated.View style={[styles.header, headerStyle]}>
            <View style={styles.closeButton}>
              <Ionicons name="swap-vertical" size={16} color={SOFT} />
            </View>
            <Text style={styles.title}>{title}</Text>
            <Pressable
              style={styles.closeButton}
              accessibilityRole="button"
              accessibilityLabel="Close"
              hitSlop={8}
              onPress={() => {
                touched();
                close();
              }}
            >
              <Ionicons name="close" size={17} color={SOFT} />
            </Pressable>
          </Animated.View>
        </GestureDetector>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

/** A row of the list: it fades in and slides up 16 points a little after the row above. */
function ModeRow({
  mode,
  index,
  morph,
  selected,
  onPress,
}: {
  mode: Mode;
  index: number;
  morph: { get: () => number };
  selected: boolean;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const t = ramp(morph.get(), 0.45 + index * 0.04, 0.85 + index * 0.04);
    return { opacity: t, transform: [{ translateY: lerp(16, 0, t) }] };
  });
  return (
    <Animated.View style={style}>
      <Pressable style={styles.row} onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
        <View style={[styles.well, { backgroundColor: `${mode.color}26`, borderColor: `${mode.color}40` }]}>
          <Ionicons name={mode.icon} size={19} color={mode.color} />
        </View>
        <View style={styles.rowText}>
          <Text style={styles.rowLabel}>{mode.label}</Text>
          {mode.caption ? <Text style={styles.rowCaption}>{mode.caption}</Text> : null}
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  tip: { position: 'absolute' },
  tipPill: { height: TOOLTIP_HEIGHT, paddingLeft: 12, paddingRight: 6, borderRadius: TOOLTIP_HEIGHT / 2, backgroundColor: YELLOW, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, transformOrigin: 'left bottom', shadowColor: YELLOW, shadowOpacity: 0.35, shadowRadius: 10, shadowOffset: { width: 0, height: 0 } },
  tipText: { fontSize: 13, lineHeight: 16, fontWeight: '600', letterSpacing: -0.1, color: '#0d0d0d' },
  tipClose: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#14160c', alignItems: 'center', justifyContent: 'center' },
  line: { position: 'absolute', width: LINE, backgroundColor: YELLOW },
  dotEnd: { position: 'absolute', width: 8, height: 8, borderRadius: 4, backgroundColor: YELLOW },
  dock: { position: 'absolute', left: MARGIN, right: MARGIN, height: CONTROL, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  control: { width: CONTROL, height: CONTROL, borderRadius: CONTROL / 2, alignItems: 'center', justifyContent: 'center', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: GLASS_EDGE },
  glass: { backgroundColor: GLASS },
  sunWell: { borderRadius: CONTROL / 2 },
  center: { width: CENTER_WIDTH, height: CONTROL, borderRadius: CONTROL / 2, backgroundColor: GLASS, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: GLASS_EDGE, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  place: { fontSize: 15, lineHeight: 20, fontWeight: '600', color: SOFT },
  frame: { position: 'absolute', overflow: 'hidden' },
  header: { position: 'absolute', left: 0, right: 0, height: HEADER, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: MARGIN },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 17, lineHeight: 22, fontWeight: '700', letterSpacing: -0.2, color: INK },
  closeButton: { width: 34, height: 34, borderRadius: 17, backgroundColor: GLASS, alignItems: 'center', justifyContent: 'center' },
  row: { height: ROW, flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: MARGIN },
  well: { width: 42, height: 42, borderRadius: 13, borderWidth: StyleSheet.hairlineWidth * 2, alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, gap: 2 },
  rowLabel: { fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.3, color: INK },
  rowCaption: { fontSize: 13, lineHeight: 17, fontWeight: '500', color: MUTED },
  tick: { position: 'absolute', right: MARGIN, top: 0, height: ROW, justifyContent: 'center' },
  tickBadge: { width: 24, height: 24, borderRadius: 12, backgroundColor: TICK_GREEN, alignItems: 'center', justifyContent: 'center', shadowColor: TICK_GREEN, shadowOpacity: 0.45, shadowRadius: 8, shadowOffset: { width: 0, height: 0 } },
});
