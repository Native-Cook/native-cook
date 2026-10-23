import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { KEYBOARD_HEIGHT, Keyboard } from './Keyboard';
import {
  HEADING_HEIGHT,
  MODE_GAP,
  MODE_HEIGHT,
  MODEL_GAP,
  MODEL_HEIGHT,
  type Mode,
  ModeRow,
  type Model,
  ModelRow,
  SwitchRow,
} from './SheetRows';
import { type Step, useAutoPlay } from './use-auto-play';

type Props = {
  /** The rows of the compact sheet; the first one shows the chosen model at its right. */
  modes?: Mode[];
  /** The rows of the tall sheet. */
  models?: Model[];
  /** Plays the whole interaction by itself, over and over, and gives way to a touch. */
  autoPlay?: boolean;
  wordmark?: string;
  placeholder?: string;
  compactTitle?: string;
  tallTitle?: string;
  incognitoLabel?: string;
  incognitoCaption?: string;
  onModeChange?: (mode: Mode) => void;
  onModelChange?: (model: Model) => void;
  onIncognitoChange?: (on: boolean) => void;
};

const MODES: Mode[] = [
  { label: 'Search', caption: 'Fast answers to everyday questions', icon: 'search-outline', pro: true },
  { label: 'Research', caption: 'Deep research on any topic', icon: 'albums-outline', pro: true },
  { label: 'Labs', caption: 'Create projects from scratch', icon: 'flask-outline', pro: true },
];

const MODELS: Model[] = [
  { name: 'Best', caption: 'Picks the right model for each question' },
  { name: 'Pulse', caption: 'Our fastest model for quick lookups' },
  { name: 'Orbit', caption: 'Careful, balanced and clear' },
  { name: 'Vega', caption: 'The newest model from Vega Labs', tag: 'New' },
  { name: 'Atlas', caption: 'Broad knowledge, plain writing' },
  { name: 'Vega Thinking', caption: 'Vega with extended reasoning', tag: 'New', heading: 'Reasoning' },
  { name: 'Atlas Thinking', caption: 'Atlas working through hard problems' },
  { name: 'Nova Max', caption: 'The most capable reasoning model', tag: 'Max' },
  { name: 'Pulse Thinking', caption: 'Pulse with a short reasoning pass' },
];

/** The autoPlay loop, in seconds from its start. */
export const LOOP = 9;
const OPEN_AT = 0.8;
const VALUE_TAP_AT = 1.8;
const TAP_FOR = 0.12;
const PICK_AT = 3;
const COLLAPSE_AT = 4;
const RESEARCH_AT = 5.2;
const INCOGNITO_ON_AT = 6;
const INCOGNITO_OFF_AT = 6.8;
const CLOSE_AT = 7.4;
const REST_AT = 8.2;
// The model the loop picks, by place in the list.
const PICKED = 2;
// Seconds a touch keeps the loop away.
const USER_HOLD = 3;

const AVATAR = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80';

const IDEAS: [keyof typeof Ionicons.glyphMap, string][] = [
  ['bulb-outline', 'Explain simply'],
  ['airplane-outline', 'Plan a weekend'],
  ['git-compare-outline', 'Compare'],
];

const PAGE = '#fbfbfa';
const INK = '#16181a';
const MUTE = '#94969b';
const OUTLINE = '#e7e7e5';
const SHEET = '#ffffff';
const GRABBER = '#d4d4d4';
const CLOSE_FILL = '#ececec';
const TEAL = '#20b8cd';
const TEAL_DEEP = '#1e8f95';
const ORANGE = '#f26a1b';
const DIM = 0.08;

const PAGE_MARGIN = 20;
const SIDE = 8;
const HEADER_HEIGHT = 60;
const COMPACT_HEIGHT = HEADER_HEIGHT + MODE_HEIGHT * 4 + MODE_GAP * 3 + 14;
const ROWS_TOP = HEADER_HEIGHT;
const SHEET_RADIUS = 28;
const BLUR_INTENSITY = 50;
const SNAP_VELOCITY = 500;
const DISMISS_DRAG = 90;

const RISE_SPRING = { duration: 500, dampingRatio: 0.85 };
const MORPH_SPRING = { duration: 450, dampingRatio: 0.88 };
const DROP_MS = 400;
const DROP_EASING = Easing.bezier(0.5, 0, 1, 1);
const FADE_MS = 160;
const KEYS_DOWN_MS = 260;
const KEYS_UP_MS = 380;
const KEYS_UP_AFTER = 120;
const PRESS_SCALE = 0.88;

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function ramp(value: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (value - from) / (to - from)));
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A prompt screen with a sheet over it. The sheet opens as a compact list of modes; touching the
 * model at the right of the first row grows the same sheet upward into a list of models, while
 * the two lists cross-fade inside it, and a pick shrinks it back. The page behind blurs and dims
 * while the sheet is up. The grabber drags the sheet between its two heights.
 */
export function MorphingModeSheet({
  modes = MODES,
  models = MODELS,
  autoPlay = false,
  wordmark = 'Briar',
  placeholder = 'Ask anything...',
  compactTitle = 'Choose a mode',
  tallTitle = 'Models',
  incognitoLabel = 'Incognito mode',
  incognitoCaption = "Activity won't be saved",
  onModeChange,
  onModelChange,
  onIncognitoChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const sheetWidth = width - SIDE * 2;
  const bottom = Math.max(insets.bottom - 14, SIDE);
  const listHeight = models.length * (MODEL_HEIGHT + MODEL_GAP) + models.filter((m) => m.heading).length * HEADING_HEIGHT;
  // As tall as the list needs, and no taller than the screen allows.
  const tallHeight = Math.min(HEADER_HEIGHT + listHeight + 16, height - insets.top - bottom - 24);
  const reach = tallHeight - COMPACT_HEIGHT;

  // 0 below the screen, 1 up; and 0 compact, 1 tall.
  const show = useSharedValue(0);
  const grow = useSharedValue(0);
  const grabbed = useSharedValue(0);
  const valueScale = useSharedValue(1);
  // 1 while the drawn keyboard is up, as it is on the page at rest.
  const keys = useSharedValue(1);

  const [mode, setMode] = useState(0);
  const [model, setModel] = useState(0);
  const [incognito, setIncognito] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [isTall, setIsTall] = useState(false);
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  // Whether the sheet is up, for the timers, which would read a stale state.
  const up = useSharedValue(false);

  const settle = (to: number) => (reducedMotion ? withTiming(to, { duration: FADE_MS }) : withSpring(to, MORPH_SPRING));

  const open = () => {
    if (up.get()) return;
    up.set(true);
    setIsOpen(true);
    show.set(reducedMotion ? withTiming(1, { duration: FADE_MS }) : withSpring(1, RISE_SPRING));
    keys.set(withTiming(0, { duration: reducedMotion ? FADE_MS : KEYS_DOWN_MS, easing: Easing.in(Easing.quad) }));
  };
  const close = () => {
    up.set(false);
    setIsOpen(false);
    show.set(withTiming(0, { duration: reducedMotion ? FADE_MS : DROP_MS, easing: DROP_EASING }));
    keys.set(withDelay(reducedMotion ? 0 : KEYS_UP_AFTER, withTiming(1, { duration: reducedMotion ? FADE_MS : KEYS_UP_MS, easing: Easing.out(Easing.cubic) })));
    setIsTall(false);
    grow.set(settle(0));
  };
  const expand = () => {
    setIsTall(true);
    grow.set(settle(1));
  };
  const collapse = () => {
    setIsTall(false);
    grow.set(settle(0));
  };
  const choose = (index: number) => {
    setModel(index);
    onModelChange?.(models[index]);
  };
  const pickMode = (index: number) => {
    setMode(index);
    onModeChange?.(modes[index]);
  };
  const toggleIncognito = (on: boolean) => {
    setIncognito(on);
    onIncognitoChange?.(on);
  };
  const tapValue = (down: boolean) => valueScale.set(withTiming(down ? PRESS_SCALE : 1, { duration: down ? 80 : 140 }));

  /** Everything back as it was at the start, with no animation. */
  const rest = () => {
    for (const value of [show, grow, grabbed]) {
      cancelAnimation(value);
      value.set(0);
    }
    valueScale.set(1);
    cancelAnimation(keys);
    keys.set(1);
    up.set(false);
    setIsOpen(false);
    setIsTall(false);
    setMode(0);
    setModel(0);
    setIncognito(false);
  };

  const steps: Step[] = [
    { at: OPEN_AT, run: open },
    { at: VALUE_TAP_AT, run: () => tapValue(true) },
    {
      at: VALUE_TAP_AT + TAP_FOR,
      run: () => {
        tapValue(false);
        expand();
      },
    },
    { at: PICK_AT, run: () => setModel(PICKED) },
    { at: COLLAPSE_AT, run: collapse },
    { at: RESEARCH_AT, run: () => setMode(1) },
    { at: INCOGNITO_ON_AT, run: () => setIncognito(true) },
    { at: INCOGNITO_OFF_AT, run: () => setIncognito(false) },
    { at: CLOSE_AT, run: close },
    { at: REST_AT, run: rest },
  ];
  useAutoPlay({ autoPlay, loop: LOOP, steps, rest, touches, hold: USER_HOLD });

  const snap = (wantTall: boolean) => {
    if (wantTall) expand();
    else collapse();
  };

  const pan = Gesture.Pan()
    .onStart(() => {
      grabbed.set(grow.get());
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      grow.set(Math.min(1, Math.max(0, grabbed.get() - event.translationY / reach)));
    })
    .onEnd((event) => {
      if (grabbed.get() < 0.5 && (event.translationY > DISMISS_DRAG || event.velocityY > SNAP_VELOCITY * 2)) {
        scheduleOnRN(close);
        return;
      }
      const up = event.velocityY < -SNAP_VELOCITY || (Math.abs(event.velocityY) <= SNAP_VELOCITY && grow.get() > 0.5);
      scheduleOnRN(snap, up);
    });

  const blurProps = useAnimatedProps(() => ({ intensity: BLUR_INTENSITY * ramp(show.get(), 0, 1) }));
  const dimStyle = useAnimatedStyle(() => ({ opacity: DIM * ramp(show.get(), 0, 1) }));
  const sheetStyle = useAnimatedStyle(() => {
    const tall = lerp(COMPACT_HEIGHT, tallHeight, grow.get());
    return { height: tall, transform: [{ translateY: (1 - show.get()) * (tall + bottom + 24) }] };
  });
  const compactStyle = useAnimatedStyle(() => ({ opacity: 1 - ramp(grow.get(), 0, 0.4) }));
  const tallStyle = useAnimatedStyle(() => ({ opacity: ramp(grow.get(), 0.3, 0.7) }));
  const compactTitleStyle = useAnimatedStyle(() => ({ opacity: 1 - ramp(grow.get(), 0.1, 0.5) }));
  const tallTitleStyle = useAnimatedStyle(() => ({ opacity: ramp(grow.get(), 0.4, 0.8) }));
  const valueStyle = useAnimatedStyle(() => ({ transform: [{ scale: valueScale.get() }] }));
  const keysStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - keys.get()) * (KEYBOARD_HEIGHT + insets.bottom + 12) }] }));
  const caretStyle = useAnimatedStyle(() => ({ opacity: keys.get() }));

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />

      <View style={styles.screen}>
        <View style={[styles.topBar, { top: insets.top + 6 }]}>
          <Image source={AVATAR} style={styles.avatar} contentFit="cover" transition={0} />
          <View style={styles.topEnd}>
            <View style={styles.iconButton}>
              <Ionicons name="time-outline" size={19} color={INK} />
            </View>
            <View style={styles.iconButton}>
              <Ionicons name="create-outline" size={19} color={INK} />
            </View>
          </View>
        </View>
        <View style={[styles.brand, { top: insets.top + 118 }]}>
          <View style={styles.wordmark}>
            <LinearGradient colors={['#ff8a3d', ORANGE, '#d9480f']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.mark}>
              <Ionicons name="sparkles" size={17} color="#ffffff" />
            </LinearGradient>
            <Text style={styles.wordmarkText}>{wordmark}</Text>
            <View style={styles.proPill}>
              <Text style={styles.proPillText}>PRO</Text>
            </View>
          </View>
          <Text style={styles.tagline}>Where good questions begin</Text>
        </View>
        <Pressable
          style={[styles.input, { top: insets.top + 226 }]}
          accessibilityRole="button"
          onPress={() => {
            touched();
            open();
          }}
        >
          <View style={styles.field}>
            <Animated.View style={[styles.caret, caretStyle]} />
            <Text style={styles.placeholder}>{placeholder}</Text>
          </View>
          <View style={styles.tools}>
            <View style={styles.toolsStart}>
              <View style={styles.toolChip}>
                <Ionicons name="add" size={20} color={INK} />
              </View>
              <View style={[styles.toolChip, styles.toolChipWide]}>
                <Ionicons name="globe-outline" size={15} color={TEAL_DEEP} />
                <Text style={styles.toolChipText}>Web</Text>
              </View>
            </View>
            <View style={styles.toolsEnd}>
              <Ionicons name="mic-outline" size={21} color={INK} />
              <View style={styles.send}>
                <Ionicons name="pulse" size={19} color="#ffffff" />
              </View>
            </View>
          </View>
        </Pressable>
        <View style={[styles.ideas, { top: insets.top + 356 }]}>
          {IDEAS.map(([icon, label]) => (
            <View key={label} style={styles.idea}>
              <Ionicons name={icon} size={15} color={MUTE} />
              <Text style={styles.ideaText}>{label}</Text>
            </View>
          ))}
        </View>
        <Animated.View style={[styles.keyboard, keysStyle]}>
          <Keyboard width={width} bottomInset={insets.bottom} />
        </Animated.View>
      </View>

      <AnimatedBlurView pointerEvents="none" tint="systemUltraThinMaterialLight" style={StyleSheet.absoluteFill} animatedProps={blurProps} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.dim, dimStyle]} />
      {isOpen ? (
        <Pressable
          style={StyleSheet.absoluteFill}
          accessibilityLabel="Close"
          onPress={() => {
            touched();
            close();
          }}
        />
      ) : null}

      <Animated.View style={[styles.sheet, { left: SIDE, width: sheetWidth, bottom }, sheetStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
        <View style={styles.sheetClip}>
        <Animated.View style={[styles.layer, { height: COMPACT_HEIGHT }, compactStyle]} pointerEvents={isTall ? 'none' : 'auto'}>
          <View style={[styles.rows, { top: ROWS_TOP }]}>
            {modes.map((item, index) => (
              <ModeRow
                key={item.label}
                mode={item}
                selected={index === mode}
                onPress={() => {
                  touched();
                  pickMode(index);
                }}
                trailing={
                  index === 0 ? (
                    <Pressable
                      hitSlop={8}
                      accessibilityRole="button"
                      accessibilityLabel="Choose a model"
                      onPress={() => {
                        touched();
                        expand();
                      }}
                    >
                      <Animated.View style={[styles.value, valueStyle]}>
                        <Text style={styles.valueText}>{models[model].name}</Text>
                        <Ionicons name="chevron-forward" size={13} color={MUTE} />
                      </Animated.View>
                    </Pressable>
                  ) : null
                }
              />
            ))}
            <SwitchRow
              label={incognitoLabel}
              caption={incognitoCaption}
              on={incognito}
              onPress={() => {
                touched();
                toggleIncognito(!incognito);
              }}
            />
          </View>
        </Animated.View>

        <Animated.View style={[styles.layer, { height: tallHeight }, tallStyle]} pointerEvents={isTall ? 'auto' : 'none'}>
          <ScrollView style={{ marginTop: ROWS_TOP }} contentContainerStyle={styles.list} showsVerticalScrollIndicator={false}>
            {models.map((item, index) => (
              <ModelRow
                key={item.name}
                model={item}
                index={index}
                selected={index === model}
                grow={grow}
                onPress={() => {
                  touched();
                  choose(index);
                  collapse();
                }}
              />
            ))}
          </ScrollView>
        </Animated.View>

        <GestureDetector gesture={pan}>
          <View style={styles.header}>
            <View style={styles.grabber} />
            <Animated.Text style={[styles.title, compactTitleStyle]}>{compactTitle}</Animated.Text>
            <Animated.Text style={[styles.title, tallTitleStyle]}>{tallTitle}</Animated.Text>
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
              <Ionicons name="close" size={16} color="#6b6d72" />
            </Pressable>
          </View>
        </GestureDetector>
        </View>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  topBar: { position: 'absolute', left: 16, right: 16, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  avatar: { width: 36, height: 36, borderRadius: 18, borderWidth: 2, borderColor: '#ffffff' },
  topEnd: { flexDirection: 'row', gap: 8 },
  iconButton: { width: 38, height: 38, borderRadius: 19, backgroundColor: '#ffffff', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: OUTLINE, alignItems: 'center', justifyContent: 'center' },
  brand: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  wordmark: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  mark: { width: 34, height: 34, borderRadius: 11, alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '-8deg' }] },
  wordmarkText: { fontSize: 36, lineHeight: 42, fontWeight: '800', letterSpacing: -1.2, color: INK },
  proPill: { height: 20, paddingHorizontal: 7, borderRadius: 6, borderWidth: 1.5, borderColor: TEAL, alignItems: 'center', justifyContent: 'center', marginTop: 4 },
  proPillText: { fontSize: 10, lineHeight: 12, fontWeight: '800', letterSpacing: 0.8, color: TEAL_DEEP },
  tagline: { marginTop: 6, fontSize: 15, lineHeight: 20, fontWeight: '500', color: MUTE },
  input: {
    position: 'absolute',
    left: PAGE_MARGIN - 4,
    right: PAGE_MARGIN - 4,
    height: 112,
    borderRadius: 26,
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: OUTLINE,
    backgroundColor: '#ffffff',
    paddingHorizontal: 16,
    paddingTop: 18,
    justifyContent: 'space-between',
    paddingBottom: 12,
    shadowColor: '#1a1a1a',
    shadowOpacity: 0.08,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 10 },
  },
  field: { flexDirection: 'row', alignItems: 'center' },
  caret: { width: 2, height: 22, borderRadius: 1, marginRight: 2, backgroundColor: TEAL },
  placeholder: { fontSize: 17, lineHeight: 22, color: MUTE },
  tools: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  toolsStart: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  toolChip: { height: 36, minWidth: 36, borderRadius: 18, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: OUTLINE, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 5 },
  toolChipWide: { paddingHorizontal: 12, backgroundColor: '#eef7f7', borderColor: '#cfe9ea' },
  toolChipText: { fontSize: 14, lineHeight: 18, fontWeight: '600', color: TEAL_DEEP },
  toolsEnd: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  send: { width: 38, height: 38, borderRadius: 19, backgroundColor: TEAL, alignItems: 'center', justifyContent: 'center', shadowColor: TEAL, shadowOpacity: 0.4, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
  keyboard: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  ideas: { position: 'absolute', left: PAGE_MARGIN - 4, right: 0, flexDirection: 'row', gap: 8, overflow: 'hidden' },
  idea: { height: 36, paddingHorizontal: 14, borderRadius: 18, backgroundColor: '#ffffff', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: OUTLINE, flexDirection: 'row', alignItems: 'center', gap: 6 },
  ideaText: { fontSize: 14, lineHeight: 18, fontWeight: '500', color: '#4a4d52' },
  dim: { backgroundColor: '#000000' },
  // The shadow sits on the outer view; the inner one clips the two lists to the sheet's shape.
  sheet: { position: 'absolute', borderRadius: SHEET_RADIUS, backgroundColor: SHEET, shadowColor: '#000000', shadowOpacity: 0.16, shadowRadius: 32, shadowOffset: { width: 0, height: 12 } },
  sheetClip: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: SHEET_RADIUS, overflow: 'hidden' },
  layer: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  rows: { position: 'absolute', left: 12, right: 12, gap: MODE_GAP },
  list: { paddingBottom: 16 },
  header: { position: 'absolute', left: 0, right: 0, top: 0, height: HEADER_HEIGHT, justifyContent: 'center', paddingHorizontal: 16 },
  grabber: { position: 'absolute', top: 8, alignSelf: 'center', width: 36, height: 5, borderRadius: 2.5, backgroundColor: GRABBER },
  title: { position: 'absolute', left: 18, top: 22, fontSize: 18, lineHeight: 24, fontWeight: '700', letterSpacing: -0.3, color: INK },
  close: { position: 'absolute', right: 14, top: 20, width: 28, height: 28, borderRadius: 14, backgroundColor: CLOSE_FILL, alignItems: 'center', justifyContent: 'center' },
  value: { flexDirection: 'row', alignItems: 'center', gap: 2, height: 26, paddingLeft: 10, paddingRight: 6, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.8)' },
  valueText: { fontSize: 13, lineHeight: 16, fontWeight: '600', color: '#5d6066' },
});
