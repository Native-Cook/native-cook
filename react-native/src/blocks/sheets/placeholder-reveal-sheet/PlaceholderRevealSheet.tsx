import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  interpolateColor,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

import { useAutoPlay } from './useAutoPlay';

type Props = {
  pageTitle?: string;
  generateLabel?: string;
  title?: string;
  subtitle?: string;
  doneTitle?: string;
  doneCaption?: string;
  /** The title of the front card of the result. */
  cardTitle?: string;
  stopLabel?: string;
  doneLabel?: string;
  /** Seconds the placeholder wobbles before the real cards replace it. */
  preparing?: number;
  /** Presses "Generate cards" and "Done" by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the generation starts, and again with `true` when the cards are shown. */
  onGenerate?: () => void;
  onReady?: () => void;
  /** Called when the sheet is dismissed, by Stop, Done or the loop. */
  onClose?: (finished: boolean) => void;
};

/** Seconds for one pass of the autoPlay loop. */
export const LOOP = 10;
// The timeline, in seconds from the start of a loop.
const PRESS_AT = 0.8;
const OPEN_AT = 0.95;
const PRESS_DONE_AT = 8.5;
const CLOSE_AT = 8.8;

const PAGE = '#121214';
const PAGE_CARD = '#232326';
const PAGE_INK = '#f4f4f5';
const PAGE_MUTE = '#8e8e93';
const RUNNING = '#3ddc84';
const PAUSED = '#ff9f0a';
const BLUE = '#41aff3';
const BLUE_LIP = '#2a8fd3';
const BLUE_PRESSED = '#2f9ee4';
const WHITE = '#ffffff';
const WHITE_LIP = '#dfe4ea';
const TEAL = '#4fd6d0';
const TEAL_DEEP = '#2fbab4';
const TEAL_BACK = '#38bfb9';
const TEAL_BACK_DEEP = '#249e99';
const CAPTION = '#7fc3ee';

const MARGIN = 16;
// The sheet floats inside the screen's corners with a darker lip under its bottom edge.
const SHEET_INSET = 8;
const SHEET_RADIUS = 44;
const SHEET_HEIGHT = 488;
const LIP = 5;
const PLACEHOLDER_WIDTH = 160;
const PLACEHOLDER_HEIGHT = 210;
const PLACEHOLDER_TOP = 132;
const PLACEHOLDER_RADIUS = 22;
const DASH = 9;
const GAP = 7;
const STROKE = 2.5;
const CARD_WIDTH = 156;
const CARD_HEIGHT = 204;
const CARD_TOP = 142;
const CARD_RADIUS = 18;
const STOP_TOP = 372;
const BUTTON_HEIGHT = 52;
const PAGE_BUTTON_HEIGHT = 52;
const BLOB = 120;

const SLIDE = 550;
const WOBBLE = 1600;
// The wobble and the float are one cycle of this clock.
const WOBBLE_DEGREES = 3;
const FLOAT = 4;
const POP_SPRING = { damping: 8, stiffness: 150, mass: 1, reduceMotion: ReduceMotion.Never };
const FAN_SPRING = { damping: 11, stiffness: 150, mass: 1, reduceMotion: ReduceMotion.Never };
const POP_AFTER = 250;
const STOP_AFTER = 1000;
const STOP_IN = 300;
const BLOB_IN = 450;
const RECOLOR = 350;
const FAN_AFTER = 110;
const FAN_ANGLE = 9;
const FAN_SPREAD = 26;
const FADE = 180;
const REST_FADE = 350;
// How dark the page gets under the sheet.
const DIM = 0.55;

type Automation = { title: string; text: string; time: string; every: string; running: boolean };
const AUTOMATIONS: Automation[] = [
  {
    title: 'Mental Models Daily',
    text: 'Short lessons on the thinking tools careful investors lean on, one model at a time.',
    time: '9:00AM',
    every: 'Every 2 days',
    running: true,
  },
  {
    title: 'Stoic Mornings',
    text: 'A passage and one question to start the day calmly, with a short reflection.',
    time: '7:30AM',
    every: 'Every day',
    running: true,
  },
  {
    title: 'Founder Playbook',
    text: 'Hard-won lessons on building a company, from the first idea to the first hire.',
    time: '6:00PM',
    every: 'Weekly',
    running: false,
  },
];

const BULLETS = ['Talk to ten users before writing code', 'Ship something small every week', 'Hire for slope, not intercept'];

const AnimatedRect = Animated.createAnimatedComponent(Rect);

const timing = (ms: number, easing = Easing.out(Easing.cubic)) => ({ duration: ms, easing, reduceMotion: ReduceMotion.Never });

/**
 * A page with a "Generate cards" button. The sheet that rises is blue and holds a dashed
 * placeholder that wobbles while something is made; then a white blob swells inside it, the sheet
 * turns white and three real cards fan out of the placeholder with a Done button. Nothing is
 * generated here: `preparing` seconds stand in for the work.
 */
export function PlaceholderRevealSheet({
  pageTitle = 'Automations',
  generateLabel = 'Generate cards',
  title = 'Preparing',
  subtitle = 'Generating card preview',
  doneTitle = 'ALL SET',
  doneCaption = 'This is a preview. The full cards will be delivered later.',
  cardTitle = 'Founder Playbook',
  stopLabel = 'STOP',
  doneLabel = 'DONE',
  preparing = 5.15,
  autoPlay = false,
  onGenerate,
  onReady,
  onClose,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [phase, setPhase] = useState<'closed' | 'preparing' | 'done'>('closed');
  const [autoPressed, setAutoPressed] = useState<'' | 'generate' | 'done'>('');

  const sheet = useSharedValue(0);
  const pop = useSharedValue(0);
  const clock = useSharedValue(0);
  const stop = useSharedValue(0);
  const blob = useSharedValue(0);
  const reveal = useSharedValue(0);
  const fan = useSharedValue(0);
  const ready = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sheetHeight = Math.min(SHEET_HEIGHT, height - insets.top - 160);
  const sheetWidth = width - 2 * SHEET_INSET;
  const bottom = Math.max(insets.bottom, 16);

  const clear = () => {
    if (ready.current) clearTimeout(ready.current);
    ready.current = null;
  };

  const show = () => {
    for (const value of [pop, stop, blob, reveal, fan]) value.set(0);
    clock.set(0);
    sheet.set(withTiming(1, timing(reducedMotion ? FADE : SLIDE)));
    if (reducedMotion) {
      pop.set(withTiming(1, timing(FADE)));
      stop.set(withTiming(1, timing(FADE)));
      return;
    }
    pop.set(withDelay(POP_AFTER, withSpring(1, POP_SPRING)));
    stop.set(withDelay(STOP_AFTER, withTiming(1, timing(STOP_IN))));
    clock.set(withRepeat(withTiming(1, { duration: WOBBLE, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1));
  };

  const generate = () => {
    if (phase !== 'closed') return;
    setAutoPressed('');
    setPhase('preparing');
    show();
    onGenerate?.();
    ready.current = setTimeout(showCards, preparing * 1000);
  };

  const showCards = () => {
    ready.current = null;
    setPhase('done');
    blob.set(withTiming(1, timing(reducedMotion ? 0 : BLOB_IN)));
    reveal.set(withDelay(reducedMotion ? 0 : BLOB_IN, withTiming(1, timing(reducedMotion ? FADE : RECOLOR, Easing.inOut(Easing.quad)))));
    fan.set(
      reducedMotion
        ? withTiming(1, timing(FADE))
        : withDelay(BLOB_IN + FAN_AFTER, withSpring(1, FAN_SPRING)),
    );
    onReady?.();
  };

  const dismiss = (finished: boolean, ms: number) => {
    clear();
    setAutoPressed('');
    setPhase('closed');
    sheet.set(
      withTiming(0, timing(ms, Easing.inOut(Easing.cubic)), (done) => {
        if (!done) return;
        cancelAnimation(clock);
        for (const value of [pop, stop, blob, reveal, fan]) value.set(0);
      }),
    );
    onClose?.(finished);
  };

  useEffect(() => clear, []);

  const touched = useAutoPlay(
    autoPlay,
    LOOP,
    [
      [PRESS_AT, () => setAutoPressed('generate')],
      [OPEN_AT, generate],
      [PRESS_DONE_AT, () => setAutoPressed('done')],
      [CLOSE_AT, () => dismiss(true, reducedMotion ? FADE : SLIDE)],
    ],
    () => {
      if (phase !== 'closed') dismiss(false, REST_FADE);
      setAutoPressed('');
    },
  );

  const slideStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - sheet.get()) * (sheetHeight + SHEET_INSET + 24) }],
    backgroundColor: interpolateColor(reveal.get(), [0, 1], [BLUE_LIP, WHITE_LIP], 'RGB', { gamma: 1 }),
  }));
  const dimStyle = useAnimatedStyle(() => ({ opacity: DIM * sheet.get() }));
  const whiteStyle = useAnimatedStyle(() => ({ opacity: reveal.get() }));
  const preparingStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, reveal.get() * 2.5) }));
  const headingStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(1, reveal.get() * 2),
    transform: [{ translateY: -8 * reveal.get() }],
  }));
  const doneHeadingStyle = useAnimatedStyle(() => {
    const t = Math.min(1, Math.max(0, reveal.get() * 2 - 0.6));
    return { opacity: t, transform: [{ translateY: 8 * (1 - t) }] };
  });
  const placeholderStyle = useAnimatedStyle(() => {
    const c = clock.get();
    const wave = Math.sin(2 * Math.PI * c);
    const p = pop.get();
    return {
      opacity: Math.min(1, p * 3),
      transform: [
        { translateY: FLOAT * Math.sin(2 * Math.PI * c + 1.2) },
        { rotate: `${WOBBLE_DEGREES * wave}deg` },
        { scale: (0.6 + 0.4 * p) * (1 + 0.1 * blob.get()) },
      ],
    };
  });
  const dashProps = useAnimatedProps(() => ({ strokeDashoffset: -clock.get() * (DASH + GAP) }));
  const blobStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, blob.get() * 3) * (1 - Math.min(1, reveal.get() * 2)),
    transform: [{ scale: 1.4 * blob.get() }],
  }));
  const stopStyle = useAnimatedStyle(() => ({ opacity: stop.get() * (1 - Math.min(1, reveal.get() * 4)) }));
  const doneButtonStyle = useAnimatedStyle(() => {
    const t = Math.min(1, Math.max(0, (reveal.get() - 0.3) / 0.7));
    return { opacity: t, transform: [{ translateY: (1 - t) * 90 }] };
  });
  const leftStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, fan.get() * 3),
    transform: [
      { translateX: -FAN_SPREAD * fan.get() },
      { rotate: `${-FAN_ANGLE * fan.get()}deg` },
      { scale: 0.6 + 0.4 * fan.get() },
    ],
  }));
  const rightStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, fan.get() * 3),
    transform: [
      { translateX: FAN_SPREAD * fan.get() },
      { rotate: `${FAN_ANGLE * fan.get()}deg` },
      { scale: 0.6 + 0.4 * fan.get() },
    ],
  }));
  const frontStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, fan.get() * 3),
    transform: [{ scale: 0.6 + 0.4 * fan.get() }],
  }));

  const cardLeft = (sheetWidth - CARD_WIDTH) / 2;

  return (
    <View style={styles.page} onTouchStart={touched}>
      <StatusBar style="light" />
      <View style={[styles.pageContent, { paddingTop: insets.top + 12 }]}>
        <View style={styles.pageHeader}>
          <View style={styles.pageHeaderText}>
            <Text style={styles.pageTitle}>{pageTitle}</Text>
            <Text style={styles.pageCaption}>2 running · next at 7:30AM</Text>
          </View>
          <View style={styles.pageAvatar}>
            <Ionicons name="add" size={22} color={PAGE_INK} />
          </View>
        </View>
        {AUTOMATIONS.map((item) => (
          <View key={item.title} style={styles.pageCard}>
            <Text style={styles.pageCardTitle} numberOfLines={1}>
              {item.title}
            </Text>
            <Text style={styles.pageCardText} numberOfLines={2}>
              {item.text}
            </Text>
            <View style={styles.pageCardFoot}>
              <View>
                <Text style={styles.pageCardTime}>{item.time}</Text>
                <Text style={styles.pageCardEvery}>{item.every}</Text>
              </View>
              <View style={styles.status}>
                <View style={[styles.statusDot, { backgroundColor: item.running ? RUNNING : PAUSED }]} />
                <Text style={[styles.statusLabel, { color: item.running ? RUNNING : PAUSED }]}>{item.running ? 'RUNNING' : 'PAUSED'}</Text>
              </View>
            </View>
          </View>
        ))}
      </View>

      <View style={[styles.pageButtonSlot, { bottom }]}>
        <Pressable onPress={generate} accessibilityRole="button" accessibilityLabel={generateLabel}>
          {({ pressed }) => (
            <View style={[styles.pageButton, { backgroundColor: pressed || autoPressed === 'generate' ? BLUE_PRESSED : BLUE }]}>
              <Ionicons name="sparkles" size={18} color={WHITE} />
              <Text style={styles.pageButtonLabel}>{generateLabel}</Text>
            </View>
          )}
        </Pressable>
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dimStyle]} pointerEvents="none" />

      <Animated.View
        style={[styles.sheetLip, { height: sheetHeight + LIP, bottom: SHEET_INSET }, slideStyle]}
        pointerEvents={phase === 'closed' ? 'none' : 'auto'}
      >
        <View style={[styles.sheet, { height: sheetHeight }]}>
          <View style={[StyleSheet.absoluteFill, styles.blue]} />
          <Animated.View style={[StyleSheet.absoluteFill, styles.white, whiteStyle]} />

          <Animated.View style={[StyleSheet.absoluteFill, preparingStyle]} pointerEvents="none">
            <Animated.View style={[styles.headings, headingStyle]}>
              <Text style={styles.heading}>{title}</Text>
              <Text style={styles.subheading}>{subtitle}</Text>
            </Animated.View>
          </Animated.View>

          <Animated.View style={[styles.headings, styles.doneHeadings, doneHeadingStyle]} pointerEvents="none">
            <Text style={[styles.heading, styles.doneHeading]}>{doneTitle}</Text>
            <Text style={styles.doneCaption}>{doneCaption}</Text>
          </Animated.View>

          <Animated.View style={[styles.placeholder, { left: (sheetWidth - PLACEHOLDER_WIDTH) / 2 }, placeholderStyle]} pointerEvents="none">
            <View style={styles.placeholderFill} />
            <Svg width={PLACEHOLDER_WIDTH} height={PLACEHOLDER_HEIGHT} style={StyleSheet.absoluteFill}>
              <AnimatedRect
                x={STROKE / 2}
                y={STROKE / 2}
                width={PLACEHOLDER_WIDTH - STROKE}
                height={PLACEHOLDER_HEIGHT - STROKE}
                rx={PLACEHOLDER_RADIUS}
                fill="none"
                stroke="rgba(255,255,255,0.62)"
                strokeWidth={STROKE}
                strokeLinecap="round"
                strokeDasharray={[DASH, GAP]}
                animatedProps={dashProps}
              />
            </Svg>
            <View style={styles.blobSlot}>
              <Animated.View style={[styles.blob, blobStyle]} />
            </View>
          </Animated.View>

          <Animated.View style={[styles.stopSlot, { top: STOP_TOP }, stopStyle]} pointerEvents={phase === 'preparing' ? 'auto' : 'none'}>
            <Pressable onPress={() => dismiss(false, reducedMotion ? FADE : SLIDE)} accessibilityRole="button" accessibilityLabel={stopLabel} hitSlop={10}>
              {({ pressed }) => (
                <View style={[styles.stop, pressed && styles.stopPressed]}>
                  <Ionicons name="stop-circle-outline" size={20} color={WHITE} />
                  <Text style={styles.stopLabel}>{stopLabel}</Text>
                </View>
              )}
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.card, { left: cardLeft }, leftStyle]} pointerEvents="none">
            <CardFace id="left" top={TEAL_BACK} bottom={TEAL_BACK_DEEP} />
          </Animated.View>
          <Animated.View style={[styles.card, { left: cardLeft }, rightStyle]} pointerEvents="none">
            <CardFace id="right" top={TEAL_BACK} bottom={TEAL_BACK_DEEP} />
          </Animated.View>
          <Animated.View style={[styles.card, styles.cardFront, { left: cardLeft }, frontStyle]} pointerEvents="none">
            <CardFace id="front" top={TEAL} bottom={TEAL_DEEP} />
            <Text style={styles.cardTitle} numberOfLines={2}>
              {cardTitle}
            </Text>
            <Text style={styles.cardSubtitle} numberOfLines={2}>
              Hard-won lessons on building from zero
            </Text>
            <View style={styles.cardRule} />
            {BULLETS.map((line) => (
              <View key={line} style={styles.bulletRow}>
                <View style={styles.bulletDot} />
                <Text style={styles.bulletText} numberOfLines={2}>
                  {line}
                </Text>
              </View>
            ))}
          </Animated.View>

          <Animated.View style={[styles.doneSlot, doneButtonStyle]} pointerEvents={phase === 'done' ? 'auto' : 'none'}>
            <Pressable onPress={() => dismiss(true, reducedMotion ? FADE : SLIDE)} accessibilityRole="button" accessibilityLabel={doneLabel}>
              {({ pressed }) => (
                <View style={[styles.doneButton, { backgroundColor: pressed || autoPressed === 'done' ? BLUE_PRESSED : BLUE }]}>
                  <Text style={styles.doneLabel}>{doneLabel}</Text>
                </View>
              )}
            </Pressable>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

/** A card's face: a vertical teal gradient with a soft light along its top edge. */
function CardFace({ id, top, bottom }: { id: string; top: string; bottom: string }) {
  return (
    <Svg width={CARD_WIDTH} height={CARD_HEIGHT} style={StyleSheet.absoluteFill}>
      <Defs>
        <LinearGradient id={`face-${id}`} x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={top} />
          <Stop offset="1" stopColor={bottom} />
        </LinearGradient>
        <LinearGradient id={`shine-${id}`} x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#ffffff" stopOpacity={0.28} />
          <Stop offset="1" stopColor="#ffffff" stopOpacity={0} />
        </LinearGradient>
      </Defs>
      <Rect width={CARD_WIDTH} height={CARD_HEIGHT} rx={CARD_RADIUS} fill={`url(#face-${id})`} />
      <Rect width={CARD_WIDTH} height={CARD_HEIGHT * 0.4} rx={CARD_RADIUS} fill={`url(#shine-${id})`} />
      <Rect x={0.5} y={0.5} width={CARD_WIDTH - 1} height={CARD_HEIGHT - 1} rx={CARD_RADIUS - 0.5} fill="none" stroke="#ffffff" strokeOpacity={0.3} />
    </Svg>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: PAGE },
  pageContent: { flex: 1, paddingHorizontal: MARGIN, gap: 12 },
  pageHeader: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 4, marginBottom: 8 },
  pageHeaderText: { flex: 1, gap: 2 },
  pageTitle: { color: PAGE_INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -0.8 },
  pageCaption: { color: PAGE_MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  pageAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: PAGE_CARD, alignItems: 'center', justifyContent: 'center' },
  pageCard: {
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: PAGE_CARD,
    paddingHorizontal: 22,
    paddingTop: 20,
    paddingBottom: 18,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.06)',
  },
  pageCardTitle: { color: PAGE_INK, fontSize: 24, lineHeight: 30, fontWeight: '800', letterSpacing: -0.5 },
  pageCardText: { marginTop: 4, color: PAGE_MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  pageCardFoot: { marginTop: 18, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  pageCardTime: { color: PAGE_INK, fontSize: 17, lineHeight: 22, fontWeight: '800', letterSpacing: 0.2 },
  pageCardEvery: { color: PAGE_MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  status: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingBottom: 2 },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
  statusLabel: { fontSize: 13, lineHeight: 18, fontWeight: '800', letterSpacing: 0.8 },
  pageButtonSlot: { position: 'absolute', left: MARGIN, right: MARGIN },
  pageButton: {
    height: PAGE_BUTTON_HEIGHT,
    borderRadius: PAGE_BUTTON_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(65,175,243,0.35)' }],
  },
  pageButtonLabel: { color: WHITE, fontSize: 17, lineHeight: 22, fontWeight: '700' },
  dim: { backgroundColor: '#000000' },
  sheetLip: {
    position: 'absolute',
    left: SHEET_INSET,
    right: SHEET_INSET,
    borderRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    boxShadow: [{ offsetX: 0, offsetY: 18, blurRadius: 40, color: 'rgba(0,0,0,0.45)' }],
  },
  sheet: { borderRadius: SHEET_RADIUS, borderCurve: 'continuous', overflow: 'hidden' },
  blue: { backgroundColor: BLUE },
  white: { backgroundColor: WHITE },
  headings: { position: 'absolute', left: 0, right: 0, top: 36, alignItems: 'center', gap: 4 },
  doneHeadings: { top: 30, paddingHorizontal: 44, gap: 6 },
  heading: { color: WHITE, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -0.6 },
  doneHeading: { color: BLUE, fontSize: 32, lineHeight: 38, fontWeight: '900', letterSpacing: 0.2 },
  subheading: { color: 'rgba(255,255,255,0.62)', fontSize: 17, lineHeight: 22, fontWeight: '600' },
  doneCaption: { color: CAPTION, fontSize: 15, lineHeight: 20, fontWeight: '600', textAlign: 'center' },
  placeholder: { position: 'absolute', top: PLACEHOLDER_TOP, width: PLACEHOLDER_WIDTH, height: PLACEHOLDER_HEIGHT },
  placeholderFill: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: PLACEHOLDER_RADIUS, borderCurve: 'continuous', backgroundColor: 'rgba(255,255,255,0.16)' },
  blobSlot: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  blob: {
    width: BLOB,
    height: BLOB,
    borderRadius: BLOB / 2,
    backgroundColor: 'rgba(255,255,255,0.94)',
    boxShadow: [
      { offsetX: 0, offsetY: 0, blurRadius: 18, color: 'rgba(255,255,255,0.95)' },
      { offsetX: 0, offsetY: 0, blurRadius: 48, spreadDistance: 8, color: 'rgba(220,245,255,0.7)' },
    ],
  },
  stopSlot: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  stop: { height: 36, paddingHorizontal: 14, borderRadius: 18, flexDirection: 'row', alignItems: 'center', gap: 6 },
  stopPressed: { backgroundColor: 'rgba(255,255,255,0.2)' },
  stopLabel: { color: WHITE, fontSize: 17, lineHeight: 22, fontWeight: '700', letterSpacing: 0.4 },
  card: {
    position: 'absolute',
    top: CARD_TOP,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    boxShadow: [
      { offsetX: 0, offsetY: 14, blurRadius: 28, color: 'rgba(15,110,106,0.28)' },
      { offsetX: 0, offsetY: 2, blurRadius: 4, color: 'rgba(15,110,106,0.2)' },
    ],
  },
  cardFront: { paddingHorizontal: 14, paddingTop: 16 },
  cardTitle: { color: WHITE, fontSize: 16, lineHeight: 20, fontWeight: '800', textAlign: 'center', letterSpacing: -0.2 },
  cardSubtitle: { marginTop: 3, color: 'rgba(255,255,255,0.82)', fontSize: 10.5, lineHeight: 13, fontWeight: '600', textAlign: 'center' },
  cardRule: { marginVertical: 10, height: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(255,255,255,0.4)' },
  bulletRow: { flexDirection: 'row', gap: 6, marginBottom: 5 },
  bulletDot: { width: 4, height: 4, borderRadius: 2, marginTop: 5, backgroundColor: WHITE },
  bulletText: { flex: 1, color: WHITE, fontSize: 10, lineHeight: 13, fontWeight: '600' },
  doneSlot: { position: 'absolute', left: 20, right: 20, bottom: 24 },
  doneButton: {
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 20, color: 'rgba(65,175,243,0.35)' }],
  },
  doneLabel: { color: WHITE, fontSize: 17, lineHeight: 22, fontWeight: '800', letterSpacing: 1 },
});
