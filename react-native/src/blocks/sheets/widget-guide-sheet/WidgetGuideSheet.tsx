import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PHONE_HEIGHT, Scene } from './Scene';
import { type Step, useAutoPlay } from './use-auto-play';

type Props = {
  /** What the guide says under the picture, one line of text per slide. */
  captions?: string[];
  /** Plays the whole guide by itself, over and over, and gives way to a touch. */
  autoPlay?: boolean;
  title?: string;
  /** Called when a slide settles, with its place in `captions`. */
  onSlideChange?: (index: number) => void;
  onClose?: () => void;
};

const CAPTIONS = [
  'On your Home Screen, press and hold an empty space.',
  'Tap the plus button in the top left.',
  'Find Daybreak, then choose a widget.',
  'Place your widget(s), then press the Done button in the top right.',
  'Press and hold a widget to customize it.',
];

const LABELS = {
  search: 'Search',
  countdown: 'Daybreak',
  until: 'days until',
  event: "Mia's birthday",
  trip: 'Road trip',
  tomorrow: 'tomorrow',
  firstName: 'Graduation day',
  firstDate: 'in 7 days',
  secondName: "Lena's birthday",
  secondDate: 'in 9 days',
  done: 'Done',
  edit: 'Edit Widget',
};

/** The autoPlay loop, in seconds from its start. */
export const LOOP = 10.5;
const OPEN_AT = 0.8;
const SLIDE_AT = [2.8, 4.4, 6, 7.8];
const CLOSE_AT = 9.3;
const REST_AT = 9.9;
// Seconds a touch keeps the loop away.
const USER_HOLD = 3;

const PAGE = '#f4f4f6';
const INK = '#111111';
const MUTE = '#888888';
const DOT = '#d0d0d0';
const CLOSE_FILL = '#efeff2';
const SCRIM = 0.45;
const ROW_FILL = '#ffffff';

type Event = { name: string; when: string; days: string; color: string; icon: keyof typeof Ionicons.glyphMap };

const EVENTS: Event[] = [
  { name: 'Road trip', when: 'Tomorrow · Big Sur', days: '1', color: '#f4a000', icon: 'car-sport' },
  { name: 'Graduation day', when: 'Mon, 20 Oct · Campus hall', days: '7', color: '#f0476b', icon: 'school' },
  { name: "Lena's birthday", when: 'Wed, 22 Oct', days: '9', color: '#1fb3d6', icon: 'gift' },
  { name: "Mia's birthday", when: 'Mon, 3 Nov', days: '21', color: '#2fcf8a', icon: 'balloon' },
  { name: 'Concert night', when: 'Sun, 16 Nov · The Fillmore', days: '34', color: '#7a55d9', icon: 'musical-notes' },
  { name: 'Winter break', when: 'Fri, 19 Dec', days: '67', color: '#3a6fe0', icon: 'snow' },
];

const MARGIN = 20;
const SHEET_RADIUS = 28;
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
const OVERSHOOT = 40;
const HEADER = 52;
const PHONE_TOP = HEADER;
const CAPTION_HEIGHT = 56;
const NAV = 44;
const NAV_GAP = 24;
const DOT_SIZE = 7;
const SWIPE_DISTANCE = 40;

const RISE_SPRING = { damping: 18, stiffness: 170, mass: 1 };
const DROP_MS = 400;
const DROP_EASING = Easing.bezier(0.5, 0, 1, 1);
const SLIDE_MS = 700;
const SLIDE_EASING = Easing.inOut(Easing.cubic);
const DASH_MS = 1000;
const DASH_PERIOD = 8;
const SHIMMER_AFTER = 500;
const SHIMMER_MS = 700;
const FADE_MS = 160;

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

/**
 * A guide in a sheet: a miniature home screen shows, one slide at a time, how to add a widget. A
 * single number moves between slides and every part of the picture (the empty slot, the plus,
 * the picker that rises inside it, the widgets that drop in, the blurred page and its menu) is a
 * function of it, so the slides cross-fade, step backwards, and follow a swipe or the chevrons.
 */
export function WidgetGuideSheet({ captions = CAPTIONS, autoPlay = false, title = 'Widget Guide', onSlideChange, onClose }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const sheetHeight = height - insets.top;
  const phoneWidth = width - MARGIN * 2;
  const last = captions.length - 1;

  // 0 hidden, 1 up; the slide number; the dashes' march; the picker's shimmer.
  const sheet = useSharedValue(0);
  const step = useSharedValue(0);
  const dash = useSharedValue(0);
  const shimmer = useSharedValue(1);
  const drag = useSharedValue(0);

  const [slide, setSlide] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);

  const go = (index: number) => {
    const next = Math.min(last, Math.max(0, index));
    setSlide(next);
    step.set(withTiming(next, { duration: reducedMotion ? FADE_MS : SLIDE_MS, easing: SLIDE_EASING }));
    if (next === 2 && !reducedMotion) {
      shimmer.set(0);
      shimmer.set(withDelay(SHIMMER_AFTER, withTiming(1, { duration: SHIMMER_MS, easing: Easing.inOut(Easing.quad) })));
    }
    onSlideChange?.(next);
  };

  const open = () => {
    setIsOpen(true);
    sheet.set(reducedMotion ? withTiming(1, { duration: FADE_MS }) : withSpring(1, RISE_SPRING));
    if (reducedMotion) return;
    dash.set(0);
    dash.set(withRepeat(withTiming(DASH_PERIOD, { duration: DASH_MS, easing: Easing.linear }), -1));
  };

  const close = () => {
    setIsOpen(false);
    onClose?.();
    sheet.set(
      withTiming(0, { duration: reducedMotion ? FADE_MS : DROP_MS, easing: DROP_EASING }, (finished) => {
        if (!finished) return;
        // Out of sight now: the next opening starts from the first slide.
        cancelAnimation(dash);
        dash.set(0);
        step.set(0);
        drag.set(0);
        scheduleOnRN(setSlide, 0);
      }),
    );
  };

  /** Everything back as it was at the start, with no animation. */
  const rest = () => {
    for (const value of [sheet, step, dash, drag]) {
      cancelAnimation(value);
      value.set(0);
    }
    shimmer.set(1);
    setIsOpen(false);
    setSlide(0);
  };

  const steps: Step[] = [
    { at: OPEN_AT, run: open },
    ...SLIDE_AT.map((at, index) => ({ at, run: () => go(index + 1) })),
    { at: CLOSE_AT, run: close },
    { at: REST_AT, run: rest },
  ];
  useAutoPlay({ autoPlay, loop: LOOP, steps, rest, touches, hold: USER_HOLD });

  const swipe = Gesture.Pan()
    .activeOffsetX([-15, 15])
    .failOffsetY([-15, 15])
    .onStart(() => {
      scheduleOnRN(touched);
    })
    .onEnd((event) => {
      if (event.translationX < -SWIPE_DISTANCE) scheduleOnRN(go, slide + 1);
      else if (event.translationX > SWIPE_DISTANCE) scheduleOnRN(go, slide - 1);
    });

  const dismiss = Gesture.Pan()
    .activeOffsetY([-12, 12])
    .failOffsetX([-24, 24])
    .onStart(() => {
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      drag.set(Math.max(0, event.translationY));
    })
    .onEnd((event) => {
      if (drag.get() > sheetHeight / 5 || event.velocityY > 900) {
        sheet.set(1 - drag.get() / sheetHeight);
        drag.set(0);
        scheduleOnRN(close);
      } else drag.set(withSpring(0, RISE_SPRING));
    });

  const scrimStyle = useAnimatedStyle(() => ({ opacity: SCRIM * Math.min(1, sheet.get()) * (1 - Math.min(1, drag.get() / sheetHeight)) }));
  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - sheet.get()) * (sheetHeight + OVERSHOOT) + drag.get() }] }));

  const navBottom = Math.max(insets.bottom, 12) + 4;
  // The caption sits halfway between the picture and the buttons, however tall the screen is.
  const captionTop = Math.min(
    sheetHeight - navBottom - NAV - NAV_GAP - CAPTION_HEIGHT,
    PHONE_TOP + PHONE_HEIGHT + (sheetHeight - navBottom - NAV - PHONE_TOP - PHONE_HEIGHT - CAPTION_HEIGHT) / 2,
  );

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />

      <View style={[styles.page, { paddingTop: insets.top + 8 }]}>
        <View style={styles.pageHead}>
          <View>
            <Text style={styles.pageDate}>MONDAY, 13 OCTOBER</Text>
            <Text style={styles.pageTitle}>Up next</Text>
          </View>
          <View style={styles.addButton}>
            <Ionicons name="add" size={24} color="#ffffff" />
          </View>
        </View>
        {EVENTS.map((event) => (
          <View key={event.name} style={styles.row}>
            <View style={[styles.days, { backgroundColor: event.color }]}>
              <Text style={styles.daysNumber}>{event.days}</Text>
              <Text style={styles.daysLabel}>{event.days === '1' ? 'DAY' : 'DAYS'}</Text>
            </View>
            <View style={styles.rowText}>
              <Text style={styles.rowTitle}>{event.name}</Text>
              <Text style={styles.rowWhen}>{event.when}</Text>
            </View>
            <Ionicons name={event.icon} size={20} color={event.color} />
          </View>
        ))}
        {!isOpen ? <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel="Open the guide" onPress={() => { touched(); open(); }} /> : null}
      </View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} />

      <GestureDetector gesture={dismiss}>
        <Animated.View pointerEvents={isOpen ? 'auto' : 'none'} style={[styles.sheet, { top: insets.top, height: sheetHeight + OVERSHOOT }, sheetStyle]}>
          <View style={styles.header}>
            <Text style={styles.title}>{title}</Text>
            <Pressable
              style={styles.close}
              accessibilityRole="button"
              accessibilityLabel="Close"
              hitSlop={8}
              onPress={() => {
                touched();
                close();
              }}
            >
              <Ionicons name="close" size={17} color="#6d6d72" />
            </Pressable>
          </View>

          <GestureDetector gesture={swipe}>
            <View style={[styles.phone, { top: PHONE_TOP }]}>
              <Scene width={phoneWidth} step={step} dash={dash} shimmer={shimmer} labels={LABELS} />
            </View>
          </GestureDetector>

          <View style={[styles.captions, { top: captionTop }]}>
            {captions.map((caption, index) => (
              <Caption key={caption} index={index} step={step} text={caption} />
            ))}
          </View>

          <View style={[styles.nav, { bottom: OVERSHOOT + navBottom }]}>
            <Chevron name="chevron-back" label="Previous" disabled={slide === 0} onPress={() => { touched(); go(slide - 1); }} />
            <View style={styles.dots}>
              {captions.map((caption, index) => (
                <Animated.View
                  key={caption}
                  style={[styles.dot, { width: index === slide ? 20 : DOT_SIZE, backgroundColor: index === slide ? INK : DOT }, { transitionProperty: ['width', 'backgroundColor'], transitionDuration: 250 }]}
                />
              ))}
            </View>
            <Chevron name="chevron-forward" label="Next" disabled={slide === last} onPress={() => { touched(); go(slide + 1); }} />
          </View>
        </Animated.View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}


/** One caption; they sit on top of each other and cross-fade as the slide number passes. */
function Caption({ index, step, text }: { index: number; step: SharedValue<number>; text: string }) {
  const style = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(step.get() - index) * 2.2);
    return { opacity: near, transform: [{ translateY: lerp(6, 0, near) }] };
  });
  return (
    <Animated.Text style={[styles.caption, style]} lineBreakStrategyIOS="standard">
      {text}
    </Animated.Text>
  );
}

function Chevron({ name, label, disabled, onPress }: { name: 'chevron-back' | 'chevron-forward'; label: string; disabled: boolean; onPress: () => void }) {
  return (
    <Pressable style={[styles.chevron, { opacity: disabled ? 0.35 : 1 }]} accessibilityRole="button" accessibilityLabel={label} disabled={disabled} hitSlop={8} onPress={onPress}>
      <Ionicons name={name} size={20} color={INK} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  page: { ...StyleSheet.absoluteFill, backgroundColor: PAGE, paddingHorizontal: MARGIN, gap: 10 },
  pageHead: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', marginBottom: 6 },
  pageDate: { fontSize: 12, lineHeight: 16, fontWeight: '700', letterSpacing: 0.8, color: MUTE },
  pageTitle: { fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, color: INK },
  addButton: { width: 44, height: 44, borderRadius: 22, backgroundColor: INK, alignItems: 'center', justifyContent: 'center', marginBottom: 2 },
  row: { height: 76, borderRadius: 20, backgroundColor: ROW_FILL, paddingHorizontal: 12, paddingRight: 18, flexDirection: 'row', alignItems: 'center', gap: 14, shadowColor: '#000000', shadowOpacity: 0.04, shadowRadius: 8, shadowOffset: { width: 0, height: 2 } },
  days: { width: 52, height: 52, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  daysNumber: { fontSize: 20, lineHeight: 22, fontWeight: '800', color: '#ffffff' },
  daysLabel: { fontSize: 8.5, lineHeight: 10, fontWeight: '800', letterSpacing: 0.6, color: 'rgba(255,255,255,0.85)' },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { fontSize: 17, lineHeight: 22, fontWeight: '700', letterSpacing: -0.3, color: INK },
  rowWhen: { fontSize: 13, lineHeight: 17, fontWeight: '500', color: MUTE },
  scrim: { backgroundColor: '#000000' },
  sheet: { position: 'absolute', left: 0, right: 0, borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS, backgroundColor: '#ffffff', shadowColor: '#000000', shadowOpacity: 0.2, shadowRadius: 30, shadowOffset: { width: 0, height: -6 } },
  header: { height: HEADER, justifyContent: 'center', alignItems: 'center' },
  title: { fontFamily: MONO, fontSize: 13, lineHeight: 16, letterSpacing: 0.4, color: MUTE },
  close: { position: 'absolute', right: 16, top: 11, width: 30, height: 30, borderRadius: 15, backgroundColor: CLOSE_FILL, alignItems: 'center', justifyContent: 'center' },
  phone: { position: 'absolute', left: MARGIN, height: PHONE_HEIGHT, borderRadius: 30, shadowColor: '#1d2a8a', shadowOpacity: 0.25, shadowRadius: 20, shadowOffset: { width: 0, height: 10 } },
  captions: { position: 'absolute', left: 28, right: 28, height: CAPTION_HEIGHT },
  caption: { position: 'absolute', left: 0, right: 0, top: 0, textAlign: 'center', fontSize: 19, lineHeight: 26, fontWeight: '700', letterSpacing: -0.3, color: INK },
  nav: { position: 'absolute', left: MARGIN, right: MARGIN, height: NAV, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  dots: { flexDirection: 'row', gap: 6 },
  dot: { height: DOT_SIZE, borderRadius: DOT_SIZE / 2 },
  chevron: { width: NAV, height: NAV, borderRadius: NAV / 2, backgroundColor: CLOSE_FILL, alignItems: 'center', justifyContent: 'center' },
});
