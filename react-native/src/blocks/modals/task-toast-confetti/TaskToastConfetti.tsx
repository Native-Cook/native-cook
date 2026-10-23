import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, Ellipse, Path, RadialGradient, Stop } from 'react-native-svg';

import { Confetti } from './Confetti';
import { easeIn, easeOut, seg, spring, useLoopClock } from './motion';

type IconName = ComponentProps<typeof Ionicons>['name'];

type Props = {
  /** The task that gets ticked. */
  task?: string;
  /** The tasks under it, which stay as they are. */
  otherTasks?: string[];
  toastLabel?: string;
  /** The small chip at the left of the toast. */
  reward?: string;
  hintLabel?: string;
  nextTitle?: string;
  nextLabel?: string;
  /** Ticks the task by itself every LOOP seconds and unticks it again. */
  autoPlay?: boolean;
  /** Called when the task is ticked by a touch. */
  onComplete?: () => void;
};

// The timeline, in seconds from the start of the loop.
export const LOOP = 9;
// A touch starts here, a little before the tick.
const TAP_AT = 0.95;
const TICK_AT = 1.1;
const TICK_IN = 0.3;
const STRIKE_IN = 0.28;
const HINT_OUT_AT = 1.1;
const HINT_OUT_IN = 0.25;
const NEXT_AT = 1.5;
const TOAST_AT = 2.0;
// The confetti bursts out of the toast as it lands.
const CONFETTI_AT = 2.12;
const TOAST_AWAY_AT = 5.8;
const TOAST_AWAY_IN = 0.8;
const NEXT_AWAY_AT = 5.8;
const NEXT_AWAY_IN = 0.6;
const UNTICK_AT = 6.6;
const UNTICK_IN = 0.5;
const HINT_BACK_AT = 7.0;
const HINT_BACK_IN = 0.45;
// The first hint bobs by this much, once every period; LOOP holds a whole number of periods.
const BOB = 3;
const BOB_PERIOD = 0.9;
// The toast drops on a spring; the next hint and the mascot pop on a softer one.
const TOAST_STIFFNESS = 300;
const TOAST_DAMPING = 18;
const POP_STIFFNESS = 260;
const POP_DAMPING = 15;
const REDUCED_FADE = 0.3;

const BLUE = '#1a5cff';
const BLUE_DEEP = '#0b45e0';
const PAGE = '#fdfdff';
const ROW = '#f2f3f7';
const INK = '#14161f';
const MUTE = '#a3a7b5';
const SCRIBBLE = '#b9bdc9';
const DONE = '#a9aebb';
const ORANGE = '#f39a10';
const TOAST = '#f04e1d';
const MIC = '#ffe9cf';

const MARGIN = 16;
// Below the status bar: the gift icon's row, then the title's row.
const HEADER_BAR = 112;
const BANNER = 56;
const ROW_HEIGHT = 60;
const ROW_GAP = 12;
const TOAST_HEIGHT = 52;
const HINT_HEIGHT = 62;
const NEXT_WIDTH = 262;
const NEXT_HEIGHT = 128;
const MASCOT = 64;
const TAB_BAR = 66;
// A tall, condensed heavy face for the title and the bubbles, as the design has.
const DISPLAY = Platform.select({
  ios: { fontFamily: 'AvenirNextCondensed-Heavy' },
  default: { fontFamily: 'sans-serif-condensed', fontWeight: '900' as const },
});

const TABS: { label: string; icon: IconName }[] = [
  { label: 'Home', icon: 'home' },
  { label: 'Teams', icon: 'people-outline' },
  { label: 'Progress', icon: 'stats-chart-outline' },
  { label: 'Discover', icon: 'compass-outline' },
];

/** A round tomato-red creature with a sprout, looking right at the bubble. */
function Mascot() {
  return (
    <Svg width={MASCOT} height={MASCOT} viewBox="0 0 64 64">
      <Defs>
        <RadialGradient id="mascotBody" cx="38%" cy="34%" r="70%">
          <Stop offset="0" stopColor="#ff7b6e" />
          <Stop offset="0.55" stopColor="#ef3340" />
          <Stop offset="1" stopColor="#b8172a" />
        </RadialGradient>
      </Defs>
      <Ellipse cx="32" cy="60" rx="17" ry="3" fill="#000000" opacity={0.12} />
      <Circle cx="32" cy="35" r="24" fill="url(#mascotBody)" />
      <Ellipse cx="22" cy="23" rx="7" ry="4.5" fill="#ffffff" opacity={0.35} transform="rotate(-30 22 23)" />
      <Path d="M32 13 C26 4 18 6 17 9 C22 9 27 11 32 15 Z" fill="#2ea95a" />
      <Path d="M32 13 C37 3 46 5 47 8 C42 8 37 11 32 15 Z" fill="#3cc46a" />
      <Path d="M31 15 C30 9 31 5 33 2" stroke="#24874a" strokeWidth={2.4} strokeLinecap="round" fill="none" />
      <Ellipse cx="27" cy="33" rx="6.2" ry="7.4" fill="#ffffff" />
      <Ellipse cx="41" cy="33" rx="6.2" ry="7.4" fill="#ffffff" />
      <Circle cx="29.5" cy="34" r="3.4" fill="#15131c" />
      <Circle cx="43.5" cy="34" r="3.4" fill="#15131c" />
      <Circle cx="30.6" cy="32.6" r="1.1" fill="#ffffff" />
      <Circle cx="44.6" cy="32.6" r="1.1" fill="#ffffff" />
      <Path d="M20 24.5 L30 27" stroke="#7a0f1d" strokeWidth={2.6} strokeLinecap="round" />
      <Path d="M48 24.5 L38 27" stroke="#7a0f1d" strokeWidth={2.6} strokeLinecap="round" />
      <Path d="M30 45 Q35 49 40 45" stroke="#7a0f1d" strokeWidth={2.4} strokeLinecap="round" fill="none" />
      <Ellipse cx="20" cy="43" rx="3.4" ry="2" fill="#ff9aa2" opacity={0.7} />
      <Ellipse cx="47" cy="43" rx="3.4" ry="2" fill="#ff9aa2" opacity={0.7} />
    </Svg>
  );
}

/** The profile picture: a fuzzy blue face with big eyes. */
function Avatar() {
  return (
    <Svg width={44} height={44} viewBox="0 0 44 44">
      <Defs>
        <RadialGradient id="avatarBody" cx="40%" cy="35%" r="70%">
          <Stop offset="0" stopColor="#3f7bff" />
          <Stop offset="1" stopColor="#0a2a8f" />
        </RadialGradient>
      </Defs>
      <Circle cx="22" cy="22" r="21" fill="url(#avatarBody)" stroke="#5d8fff" strokeWidth={1.5} />
      <Ellipse cx="16" cy="21" rx="6" ry="7" fill="#ffffff" />
      <Ellipse cx="28" cy="20" rx="6" ry="7" fill="#ffffff" />
      <Circle cx="17.5" cy="22" r="3" fill="#0d0f1a" />
      <Circle cx="26.5" cy="21.5" r="3" fill="#0d0f1a" />
      <Path d="M18 31 Q22 34 26 31" stroke="#0d0f1a" strokeWidth={2.2} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

/** The app's mark: a white drop with a dot. */
function Logo() {
  return (
    <Svg width={40} height={46} viewBox="0 0 40 46">
      <Path d="M20 4 C27 14 36 21 36 30 C36 39 29 45 20 45 C11 45 4 39 4 30 C4 21 13 14 20 4 Z" fill="#ffffff" />
      <Circle cx="27" cy="5" r="4" fill="#ffffff" />
      <Path d="M20 26 L20 36" stroke={BLUE} strokeWidth={5} strokeLinecap="round" />
      <Circle cx="20" cy="20" r="3" fill={BLUE} />
    </Svg>
  );
}

/** The scribbled arrow that points from the inspiration note to the tab bar. */
function ScribbleArrow() {
  return (
    <Svg width={30} height={52} viewBox="0 0 30 52">
      <Path d="M13 3 C10 15 17 27 14 47" stroke={SCRIBBLE} strokeWidth={2.4} strokeLinecap="round" fill="none" />
      <Path d="M5 37 L14 48 L23 38" stroke={SCRIBBLE} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/**
 * A task list for today. Ticking the first task strikes the text through, drops a springy toast
 * from the top while confetti bursts out of it, and a speech bubble pops with the next hint beside
 * a mascot. Everything on screen is a function of one clock, so a touch and the autoPlay loop run
 * the same motion.
 */
export function TaskToastConfetti({
  task = 'Begin my first habit',
  otherTasks = ['Drink a glass of water', 'Read ten pages'],
  toastLabel = 'Task completed!',
  reward = '+5 ✦',
  hintLabel = 'Tap to complete',
  nextTitle = 'Have something in mind?',
  nextLabel = 'NEXT',
  autoPlay = false,
  onComplete,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const { clock, play } = useLoopClock(LOOP, TAP_AT, autoPlay);
  // The width of the task's text, which the strike-through draws across.
  const [textWidth, setTextWidth] = useState(0);

  const header = insets.top + HEADER_BAR;
  const firstRow = header + BANNER;
  const toastTop = Math.max(insets.top - 6, 14);
  const toastWidth = Math.min(width - 2 * 28, 340);
  const tabs = TAB_BAR + insets.bottom;
  const nextBottom = tabs + 22;

  const complete = () => {
    if (!play()) return;
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onComplete?.();
  };

  // 1 while the task is ticked: in with the tick, out as it is unticked.
  const ticked = (t: number) => {
    'worklet';
    return seg(t, TICK_AT, TICK_AT + TICK_IN) * (1 - seg(t, UNTICK_AT, UNTICK_AT + UNTICK_IN));
  };

  const boxStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const on = ticked(t);
    return {
      backgroundColor: interpolateColor(on, [0, 1], ['#ffffff', DONE], 'RGB', { gamma: 1 }),
      borderColor: interpolateColor(on, [0, 1], ['#c3c7d2', DONE], 'RGB', { gamma: 1 }),
    };
  });
  const tickStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const into = seg(t, TICK_AT, TICK_AT + TICK_IN);
    const out = seg(t, UNTICK_AT, UNTICK_AT + UNTICK_IN);
    // The tick overshoots on its way in: 0.6 -> 1.2 -> 1.
    const scale = into < 0.6 ? 0.6 + 0.6 * easeOut(into / 0.6) : 1.2 - 0.2 * easeOut((into - 0.6) / 0.4);
    return { opacity: into * (1 - out), transform: [{ scale: reduced ? 1 : scale }] };
  });
  const strikeStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const draw = seg(t, TICK_AT + 0.1, TICK_AT + 0.1 + STRIKE_IN) * (1 - seg(t, UNTICK_AT, UNTICK_AT + UNTICK_IN));
    return { width: textWidth * draw };
  });
  const taskStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.5 * ticked(clock.get()) }));

  const hintStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const gone = seg(t, HINT_OUT_AT, HINT_OUT_AT + HINT_OUT_IN);
    const back = seg(t, HINT_BACK_AT, HINT_BACK_AT + HINT_BACK_IN);
    const present = t < HINT_BACK_AT ? 1 - gone : back;
    const scale = reduced ? 1 : t < HINT_BACK_AT ? 1 - easeIn(gone) : spring(t - HINT_BACK_AT, POP_STIFFNESS, POP_DAMPING);
    const bob = reduced || present < 1 ? 0 : BOB * Math.sin((t / BOB_PERIOD) * Math.PI * 2);
    return { opacity: reduced ? present : Math.min(1, present * 4), transform: [{ translateY: bob }, { scale: Math.max(scale, 0) }] };
  });

  const nextStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const away = seg(t, NEXT_AWAY_AT, NEXT_AWAY_AT + NEXT_AWAY_IN);
    const pop = t < NEXT_AWAY_AT ? spring(t - NEXT_AT - 0.08, POP_STIFFNESS, POP_DAMPING) : 1 - easeIn(away);
    const visible = t >= NEXT_AT && t < NEXT_AWAY_AT + NEXT_AWAY_IN;
    if (reduced) return { opacity: visible ? seg(t, NEXT_AT, NEXT_AT + REDUCED_FADE) * (1 - away) : 0 };
    return { opacity: visible ? 1 : 0, transform: [{ scale: Math.max(pop, 0) }] };
  });
  const mascotStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const away = seg(t, NEXT_AWAY_AT, NEXT_AWAY_AT + NEXT_AWAY_IN);
    const pop = t < NEXT_AWAY_AT ? spring(t - NEXT_AT, 220, 9) : 1 - easeIn(away);
    const visible = t >= NEXT_AT && t < NEXT_AWAY_AT + NEXT_AWAY_IN;
    if (reduced) return { opacity: visible ? seg(t, NEXT_AT, NEXT_AT + REDUCED_FADE) * (1 - away) : 0 };
    // It hops once more as the confetti lands, then sits still.
    const hop = t < NEXT_AWAY_AT ? -10 * Math.max(0, Math.sin(Math.min(1, seg(t, CONFETTI_AT, CONFETTI_AT + 0.45)) * Math.PI)) : 0;
    return {
      opacity: visible ? 1 : 0,
      transform: [{ translateY: (1 - Math.max(pop, 0)) * 28 + hop }, { scale: Math.max(pop, 0) }, { rotate: `${(1 - Math.min(pop, 1)) * -18}deg` }],
    };
  });

  const toastStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const away = seg(t, TOAST_AWAY_AT, TOAST_AWAY_AT + TOAST_AWAY_IN);
    const visible = t >= TOAST_AT && t < TOAST_AWAY_AT + TOAST_AWAY_IN;
    if (reduced) return { opacity: visible ? seg(t, TOAST_AT, TOAST_AT + REDUCED_FADE) * (1 - seg(t, TOAST_AWAY_AT, TOAST_AWAY_AT + REDUCED_FADE)) : 0 };
    // From above the screen to its place, past it and back; away, it goes up and out again.
    const drop = spring(t - TOAST_AT, TOAST_STIFFNESS, TOAST_DAMPING);
    const rise = easeIn(away);
    const offscreen = toastTop + TOAST_HEIGHT + 16;
    return {
      opacity: visible ? 1 : 0,
      transform: [{ translateY: -offscreen * (1 - drop) - offscreen * rise }, { scale: (0.8 + 0.2 * Math.min(drop, 1)) * (1 - 0.2 * rise) }],
    };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />

      <LinearGradient colors={[BLUE, BLUE, BLUE_DEEP]} locations={[0, 0.7, 1]} style={[styles.header, { height: header, paddingTop: insets.top }]}>
        <View style={styles.giftRow}>
          <Ionicons name="gift-outline" size={26} color="#ffffff" />
        </View>
        <View style={styles.titleBar}>
          <Logo />
          <View style={styles.titleRow}>
            <Text style={styles.title}>Today</Text>
            <Ionicons name="chevron-down" size={22} color="#ffffff" style={styles.chevron} />
          </View>
          <Avatar />
        </View>
      </LinearGradient>

      <View style={styles.banner}>
        <Ionicons name="notifications-outline" size={21} color={BLUE} />
        <Text style={styles.bannerText}>Get notified about tasks</Text>
        <Ionicons name="close" size={20} color={MUTE} />
      </View>

      <Pressable style={styles.row} onPress={complete} accessibilityRole="checkbox" accessibilityLabel={task}>
        <Animated.View style={[styles.box, boxStyle]}>
          <Animated.View style={tickStyle}>
            <Ionicons name="checkmark" size={18} color="#ffffff" />
          </Animated.View>
        </Animated.View>
        <View>
          <Animated.Text style={[styles.task, taskStyle]} onTextLayout={(e) => setTextWidth(e.nativeEvent.lines[0]?.width ?? 0)}>
            {task}
          </Animated.Text>
          <Animated.View pointerEvents="none" style={[styles.strike, strikeStyle]} />
        </View>
      </Pressable>
      {otherTasks.map((label) => (
        <View key={label} style={[styles.row, styles.rowNext]}>
          <View style={[styles.box, styles.boxIdle]} />
          <Text style={styles.task}>{label}</Text>
        </View>
      ))}

      <Animated.View pointerEvents="none" style={[styles.hint, { top: firstRow + ROW_HEIGHT + 12, transformOrigin: '36px 0px' }, hintStyle]}>
        <View style={styles.hintTail} />
        <Text style={styles.hintText}>{hintLabel}</Text>
      </Animated.View>

      <View style={[styles.footer, { bottom: tabs + 14 }]} pointerEvents="none">
        <View style={styles.scribbleBlock}>
          <Text style={styles.scribble}>Looking 4{'\n'}inspiration?</Text>
          <ScribbleArrow />
        </View>
        <View style={styles.fabs}>
          <View style={styles.mic}>
            <Ionicons name="mic" size={26} color={BLUE} />
          </View>
          <View style={styles.plus}>
            <Ionicons name="add" size={40} color="#ffffff" />
          </View>
        </View>
      </View>

      <View style={[styles.tabs, { height: tabs, paddingBottom: insets.bottom }]}>
        {TABS.map((tab, i) => (
          <View key={tab.label} style={styles.tab}>
            <Ionicons name={tab.icon} size={27} color={i === 0 ? BLUE : MUTE} />
            <Text style={[styles.tabLabel, { color: i === 0 ? BLUE : MUTE }]}>{tab.label}</Text>
          </View>
        ))}
      </View>

      <Animated.View pointerEvents="none" style={[styles.next, { bottom: nextBottom, transformOrigin: 'bottom left' }, nextStyle]}>
        <View style={styles.nextTail} />
        <Text style={styles.nextTitle}>{nextTitle}</Text>
        <Text style={styles.nextLabel}>{nextLabel} {'›'}</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.mascotSlot, { bottom: nextBottom - 10, left: Math.max(6, width - MARGIN - NEXT_WIDTH - MASCOT - 2) }, mascotStyle]}>
        <Mascot />
      </Animated.View>

      {!reduced && <Confetti clock={clock} at={CONFETTI_AT} originX={width / 2} originY={toastTop + TOAST_HEIGHT / 2} width={width} height={height} />}

      <Animated.View pointerEvents="none" style={[styles.toastSlot, { top: toastTop, left: (width - toastWidth) / 2, width: toastWidth }, toastStyle]}>
        <LinearGradient colors={['#ff6a35', TOAST]} style={styles.toast}>
          <View style={styles.chip}>
            <Text style={styles.chipText}>{reward}</Text>
          </View>
          <Text style={styles.toastText}>{toastLabel}</Text>
        </LinearGradient>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  header: { paddingHorizontal: MARGIN },
  giftRow: { height: 48, alignItems: 'flex-end', justifyContent: 'center', paddingRight: 4 },
  titleBar: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingBottom: 8 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  title: { ...DISPLAY, color: '#ffffff', fontSize: 38, lineHeight: 44, letterSpacing: -0.6 },
  chevron: { marginTop: 6 },
  banner: { height: BANNER, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', gap: 12 },
  bannerText: { flex: 1, color: BLUE, fontSize: 17, lineHeight: 22, fontWeight: '800', letterSpacing: -0.2 },
  row: { marginHorizontal: MARGIN, height: ROW_HEIGHT, borderRadius: 18, backgroundColor: ROW, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 14 },
  rowNext: { marginTop: ROW_GAP },
  box: { width: 28, height: 28, borderRadius: 9, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  boxIdle: { borderColor: '#c3c7d2', backgroundColor: '#ffffff' },
  task: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.2 },
  strike: { position: 'absolute', left: 0, top: 10.5, height: 2, borderRadius: 1, backgroundColor: INK },
  hint: {
    position: 'absolute',
    left: MARGIN + 2,
    height: HINT_HEIGHT,
    paddingHorizontal: 28,
    borderRadius: HINT_HEIGHT / 2,
    backgroundColor: ORANGE,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 10px 24px rgba(214, 120, 0, 0.30), 0 2px 4px rgba(150, 80, 0, 0.18)',
  },
  hintTail: { position: 'absolute', left: 26, top: -7, width: 22, height: 22, backgroundColor: ORANGE, transform: [{ rotate: '45deg' }], borderRadius: 4 },
  hintText: { ...DISPLAY, color: '#ffffff', fontSize: 28, lineHeight: 34, letterSpacing: -0.3 },
  footer: { position: 'absolute', left: MARGIN, right: MARGIN, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  scribbleBlock: { marginLeft: 56, alignItems: 'center' },
  scribble: { ...DISPLAY, color: SCRIBBLE, fontSize: 25, lineHeight: 27, textAlign: 'center', transform: [{ rotate: '-3deg' }] },
  fabs: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  mic: { width: 62, height: 62, borderRadius: 31, backgroundColor: MIC, alignItems: 'center', justifyContent: 'center' },
  plus: {
    width: 74,
    height: 74,
    borderRadius: 37,
    backgroundColor: BLUE,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 10px 22px rgba(26, 92, 255, 0.35), 0 2px 5px rgba(10, 40, 140, 0.25)',
  },
  tabs: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', backgroundColor: PAGE, paddingTop: 8 },
  tab: { flex: 1, alignItems: 'center', gap: 4 },
  tabLabel: { fontSize: 13, lineHeight: 16, fontWeight: '600' },
  next: {
    position: 'absolute',
    right: MARGIN,
    width: NEXT_WIDTH,
    height: NEXT_HEIGHT,
    borderRadius: 40,
    backgroundColor: ORANGE,
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 18,
    justifyContent: 'space-between',
    boxShadow: '0 18px 36px rgba(214, 120, 0, 0.32), 0 3px 8px rgba(150, 80, 0, 0.2)',
  },
  nextTail: { position: 'absolute', left: -7, bottom: 20, width: 26, height: 26, backgroundColor: ORANGE, transform: [{ rotate: '45deg' }], borderRadius: 5 },
  nextTitle: { ...DISPLAY, color: '#ffffff', fontSize: 30, lineHeight: 32, letterSpacing: -0.3 },
  nextLabel: { alignSelf: 'flex-end', color: '#ffffff', fontSize: 15, lineHeight: 18, fontWeight: '900', letterSpacing: 0.4 },
  mascotSlot: { position: 'absolute', width: MASCOT, height: MASCOT },
  toastSlot: { position: 'absolute', height: TOAST_HEIGHT, borderRadius: TOAST_HEIGHT / 2, boxShadow: '0 14px 28px rgba(190, 50, 10, 0.35), 0 3px 6px rgba(120, 30, 0, 0.25)' },
  toast: { flex: 1, borderRadius: TOAST_HEIGHT / 2, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, gap: 12 },
  chip: { height: 28, paddingHorizontal: 10, borderRadius: 14, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  chipText: { color: TOAST, fontSize: 14, lineHeight: 18, fontWeight: '900' },
  toastText: { color: '#ffffff', fontSize: 18, lineHeight: 22, fontWeight: '800', letterSpacing: -0.2 },
});
