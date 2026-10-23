import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, type ReactNode, useEffect, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  interpolate,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { FaceScan } from './FaceScan';
import { useAutoPlay } from './useAutoPlay';

type Props = {
  /** The words of the key, shown in a grid of three columns. */
  words?: string[];
  walletName?: string;
  balance?: string;
  title?: string;
  /** The body in three lines, which rise one after the other. */
  body?: string[];
  /** The three reminders under the body. */
  notes?: string[];
  cancelLabel?: string;
  revealLabel?: string;
  copiedLabel?: string;
  /** Opens the sheet, reveals the key, copies it and closes again by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when Reveal is pressed. */
  onReveal?: () => void;
  /** Called with the key's words when the key is tapped to copy. */
  onCopy?: (words: string[]) => void;
};

/** Seconds for one pass of the autoPlay loop. */
export const LOOP = 9;
// The timeline, in seconds from the start of a loop.
const PRESS_ROW_AT = 0.3;
const OPEN_AT = 0.5;
const PRESS_REVEAL_AT = 2.2;
const REVEAL_AT = 2.5;
const COPY_AT = 5.2;
const HIDE_AT = 6.5;
const CLOSE_AT = 7.5;

const WORDS = ['river', 'amber', 'falcon', 'lunar', 'maple', 'quartz', 'harbor', 'velvet', 'cactus', 'ember', 'orbit', 'willow'];
const BODY = ['Your Private Key is the key used to', 'back up your wallet. Keep it secret', 'and secure at all times.'];
const NOTES: { icon: ComponentProps<typeof Ionicons>['name']; text: string }[] = [
  { icon: 'shield-checkmark-outline', text: 'Keep your Private Key safe' },
  { icon: 'reader-outline', text: "Don't share it with anyone else" },
  { icon: 'ban-outline', text: "If you lose it, we can't recover it" },
];

const PAGE = '#ffffff';
const INK = '#16181b';
const MUTE = '#7d8187';
const NOTE = '#6b7076';
const WELL = '#f4f6f8';
const LINE = '#e9ecef';
const CANCEL = '#f1f3f6';
const CYAN = '#14b8ff';
const CYAN_PRESSED = '#0a9fe0';
const AMBER = ['#f6c04a', '#eaa31c', '#d8870a'] as const;
const DOT = '#e5334b';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const IOS = Platform.OS === 'ios';

const MARGIN = 20;
const PAD = 22;
const CARD_HEIGHT = 208;
const CARD_RADIUS = 24;
const SHEET_RADIUS = 32;
const SHEET_BOTTOM = 16;
// The sheet's height with the warning, and with the key.
const FRONT = 372;
const BACK = 318;
const BUTTON_HEIGHT = 50;
const CANCEL_WIDTH = 128;
const BOX_PAD = 12;
const CHIP_HEIGHT = 36;
const CHIP_GAP = 8;
const BOX_RADIUS = 14;
const BOX_HEIGHT = BOX_PAD * 2 + CHIP_HEIGHT * 4 + CHIP_GAP * 3;
const COLUMNS = 3;
const GLOW_WIDTH = 2;
const ACTIVITY = [
  { title: 'Received', caption: 'From 0x8f3…a21 · 2h ago', amount: '+0.01 ETH', received: true },
  { title: 'Sent', caption: 'To mira.eth · Yesterday', amount: '−0.005 ETH', received: false },
];
const SETTINGS: { icon: ComponentProps<typeof Ionicons>['name']; tint: string; title: string; caption: string }[] = [
  { icon: 'eye-outline', tint: '#14b8ff', title: 'Private Key', caption: 'Back up your wallet' },
  { icon: 'globe-outline', tint: '#7c5cff', title: 'Network', caption: 'Ethereum Mainnet' },
  { icon: 'apps-outline', tint: '#ff8a3d', title: 'Connected apps', caption: '3 apps can see this wallet' },
];

// Response 0.5 s, damping ratio 0.82.
const SPRING = { damping: 20.7, stiffness: 158, mass: 1, reduceMotion: ReduceMotion.Never };
const FADE = 180;
const SCAN = 500;
const SWITCH_AFTER = 560;
const SWITCH = 320;
const UNBLUR_AFTER = 480;
const UNBLUR = 800;
const REBLUR = 350;
const GLOW = 950;
const TOAST_FOR = 1400;
const INTRO_AFTER = 700;
const INTRO = 1000;
const LINE_STAGGER = 80;
const NOTE_STAGGER = 100;
const NOTE_START = 300;
const NOTE_IN = 300;
const NOTE_SLIDE = 12;
const CLOSE_MS = 450;
const REST_FADE = 350;
const SCRIM = 0.32;
// The bright edge that leads the unblur across the key.
const EDGE = 36;

const timing = (ms: number, easing = Easing.out(Easing.cubic)) => ({ duration: ms, easing, reduceMotion: ReduceMotion.Never });

type Value = { get(): number };

function perimeter(width: number, height: number, radius: number) {
  return 2 * (width - 2 * radius) + 2 * (height - 2 * radius) + 2 * Math.PI * radius;
}

function Rise({ progress, start, children }: { progress: Value; start: number; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const t = Math.min(1, Math.max(0, (progress.get() * INTRO - start) / NOTE_IN));
    return { opacity: t, transform: [{ translateY: 8 * (1 - t) }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

function Slide({ progress, start, children }: { progress: Value; start: number; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const t = Math.min(1, Math.max(0, (progress.get() * INTRO - start) / NOTE_IN));
    return { opacity: t, transform: [{ translateX: -NOTE_SLIDE * (1 - t) }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

/**
 * A wallet page and a floating sheet that warns about the private key. Reveal scans a face,
 * swaps the warning for the key, which unblurs from left to right while a light runs once around
 * its box; tapping the key copies it. Nothing real is kept here: `words` is whatever you pass.
 */
export function KeyRevealSheet({
  words = WORDS,
  walletName = 'BFF',
  balance = '0.02 ETH',
  title = 'Private Key',
  body = BODY,
  notes = NOTES.map((note) => note.text),
  cancelLabel = 'Cancel',
  revealLabel = 'Reveal',
  copiedLabel = 'Copied',
  autoPlay = false,
  onReveal,
  onCopy,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [open, setOpen] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const [pressed, setPressed] = useState<'' | 'row' | 'reveal'>('');
  const closing = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sheet = useSharedValue(0);
  const size = useSharedValue(0);
  const intro = useSharedValue(0);
  const mode = useSharedValue(0);
  const scan = useSharedValue(1);
  const keys = useSharedValue(0);
  const glow = useSharedValue(0);
  const toast = useSharedValue(0);
  const tap = useSharedValue(0);
  const pulse = useSharedValue(1);

  const sheetWidth = width - MARGIN * 2;
  const boxWidth = sheetWidth - PAD * 2;
  const around = perimeter(boxWidth, BOX_HEIGHT, BOX_RADIUS);
  const chipWidth = (boxWidth - BOX_PAD * 2 - CHIP_GAP * (COLUMNS - 1)) / COLUMNS;

  const haptic = (kind: 'tap' | 'done') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'tap') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  const show = () => {
    if (open) return;
    if (closing.current) clearTimeout(closing.current);
    setOpen(true);
    setPressed('');
    for (const value of [intro, mode, keys, glow, toast, tap, size]) value.set(0);
    scan.set(1);
    sheet.set(reducedMotion ? withTiming(1, timing(FADE)) : withSpring(1, SPRING));
    intro.set(withDelay(reducedMotion ? 0 : INTRO_AFTER, withTiming(1, { duration: reducedMotion ? FADE : INTRO, easing: Easing.linear, reduceMotion: ReduceMotion.Never })));
    pulse.set(withDelay(INTRO_AFTER + NOTE_START, withSequence(withTiming(1.3, timing(180)), withTiming(1, timing(260)))));
  };

  const reveal = (auto: boolean) => {
    if (revealed) return;
    setPressed('');
    setRevealed(true);
    if (!auto) haptic('tap');
    onReveal?.();
    const wait = reducedMotion ? 0 : SWITCH_AFTER;
    scan.set(0);
    scan.set(withTiming(1, { duration: reducedMotion ? 0 : SCAN, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    mode.set(withDelay(wait, withTiming(1, timing(reducedMotion ? FADE : SWITCH))));
    size.set(withDelay(wait, reducedMotion ? withTiming(1, timing(FADE)) : withSpring(1, SPRING)));
    keys.set(withDelay(wait + (reducedMotion ? 0 : UNBLUR_AFTER), withTiming(1, { duration: reducedMotion ? FADE : UNBLUR, easing: Easing.linear, reduceMotion: ReduceMotion.Never })));
    glow.set(withDelay(wait + (reducedMotion ? 0 : UNBLUR_AFTER), withTiming(reducedMotion ? 0 : 1, { duration: GLOW, easing: Easing.inOut(Easing.quad), reduceMotion: ReduceMotion.Never })));
  };

  const copy = (auto: boolean) => {
    if (!revealed) return;
    if (!auto) haptic('done');
    onCopy?.(words);
    tap.set(withSequence(withTiming(1, timing(90)), withTiming(0, timing(180))));
    toast.set(withSequence(withTiming(1, timing(reducedMotion ? FADE : 260)), withDelay(TOAST_FOR, withTiming(0, timing(FADE)))));
  };

  const hide = () => {
    if (!revealed) return;
    setRevealed(false);
    keys.set(withTiming(0, timing(reducedMotion ? FADE : REBLUR)));
    glow.set(0);
    const wait = reducedMotion ? 0 : REBLUR;
    mode.set(withDelay(wait, withTiming(0, timing(reducedMotion ? FADE : SWITCH))));
    size.set(withDelay(wait, reducedMotion ? withTiming(0, timing(FADE)) : withSpring(0, SPRING)));
  };

  const dismiss = (ms = reducedMotion ? FADE : CLOSE_MS) => {
    setOpen(false);
    setPressed('');
    sheet.set(
      withTiming(0, timing(ms, Easing.in(Easing.cubic)), (finished) => {
        if (!finished) return;
        // Out of sight: the next opening starts from the warning again.
        mode.set(0);
        size.set(0);
        keys.set(0);
        intro.set(0);
      }),
    );
    closing.current = setTimeout(() => setRevealed(false), ms);
  };

  useEffect(
    () => () => {
      if (closing.current) clearTimeout(closing.current);
    },
    [],
  );

  const rest = () => {
    if (closing.current) clearTimeout(closing.current);
    setOpen(false);
    setRevealed(false);
    setPressed('');
    for (const value of [sheet, size, intro, mode, keys, glow, toast, tap]) {
      cancelAnimation(value);
      value.set(withTiming(0, timing(REST_FADE)));
    }
  };

  const touched = useAutoPlay(
    autoPlay,
    LOOP,
    [
      [PRESS_ROW_AT, () => setPressed('row')],
      [OPEN_AT, show],
      [PRESS_REVEAL_AT, () => setPressed('reveal')],
      [REVEAL_AT, () => reveal(true)],
      [COPY_AT, () => copy(true)],
      [HIDE_AT, hide],
      [CLOSE_AT, () => dismiss()],
    ],
    rest,
  );

  const cardStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -8 * sheet.get() }, { scale: 1 - 0.04 * sheet.get() }],
  }));
  const scrimStyle = useAnimatedStyle(() => ({ opacity: SCRIM * sheet.get() }));
  const sheetStyle = useAnimatedStyle(() => ({
    height: interpolate(size.get(), [0, 1], [FRONT, BACK]),
    transform: [{ translateY: (1 - sheet.get()) * (FRONT + SHEET_BOTTOM + insets.bottom + 24) }],
  }));
  const frontStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, mode.get() * 2.2) }));
  const backStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, mode.get() * 2.2 - 1.2)), transform: [{ translateY: 8 * (1 - mode.get()) }] }));
  const pulseStyle = useAnimatedStyle(() => ({ transform: [{ scale: pulse.get() }] }));
  const mask = useAnimatedStyle(() => ({ width: boxWidth * (1 - keys.get()) }));
  const edgeStyle = useAnimatedStyle(() => ({
    opacity: Math.sin(Math.PI * keys.get()),
    transform: [{ translateX: boxWidth * keys.get() - EDGE }],
  }));
  const boxStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.02 * tap.get() }] }));
  const glowStyle = useAnimatedStyle(() => ({ opacity: Math.sin(Math.PI * glow.get()) }));
  const glowProps = useAnimatedProps(() => ({ strokeDashoffset: -glow.get() * around }));
  const toastStyle = useAnimatedStyle(() => ({ opacity: toast.get(), transform: [{ translateY: 8 * (1 - toast.get()) }] }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 1 - toast.get() }));

  const dash = [around * 0.25, around * 0.75];

  return (
    <View style={styles.screen} onTouchStart={touched}>
      <StatusBar style="dark" />

      <View style={[styles.header, { paddingTop: insets.top + 8, height: insets.top + 52 }]}>
        <Ionicons name="close" size={24} color={INK} />
        <View>
          <Ionicons name="gift-outline" size={24} color={INK} />
          <View style={styles.dot} />
        </View>
      </View>

      <Animated.View style={[styles.walletShadow, cardStyle]}>
        <View style={styles.wallet}>
          <LinearGradient colors={AMBER} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
          <LinearGradient colors={['rgba(255,255,255,0.28)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0 }} end={{ x: 0.6, y: 0.7 }} style={StyleSheet.absoluteFill} />
          {/* A large faint heart in the corner, the card's own pattern. */}
          <Ionicons name="heart" size={210} color="rgba(255,255,255,0.10)" style={styles.watermark} />
          <View style={styles.heartBadge}>
            <Ionicons name="heart" size={22} color="#ffffff" />
          </View>
          <View style={styles.copyAddress}>
            <Text style={styles.copyAddressLabel}>Copy Address</Text>
            <Ionicons name="copy-outline" size={15} color="rgba(255,255,255,0.9)" />
          </View>
          <View style={styles.walletBottom}>
            <Text style={styles.walletName}>{walletName}</Text>
            <Text style={styles.balance}>{balance}</Text>
          </View>
          <View style={styles.customize}>
            <Ionicons name="color-palette-outline" size={14} color="#ffffff" />
            <Text style={styles.customizeLabel}>Customize</Text>
          </View>
          <View style={styles.walletEdge} pointerEvents="none" />
        </View>
      </Animated.View>

      <Text style={styles.sectionLabel}>Wallet settings</Text>
      <View style={styles.list}>
        {SETTINGS.map((item, index) => {
          const content = (touching: boolean) => (
            <View style={[styles.item, index > 0 && styles.itemLine, (touching || (index === 0 && pressed === 'row')) && styles.itemPressed]}>
              <View style={[styles.itemIcon, { backgroundColor: `${item.tint}1f` }]}>
                <Ionicons name={item.icon} size={19} color={item.tint} />
              </View>
              <View style={styles.itemText}>
                <Text style={styles.itemTitle}>{index === 0 ? title : item.title}</Text>
                <Text style={styles.itemCaption}>{item.caption}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color="#b4b8bd" />
            </View>
          );
          return index === 0 ? (
            <Pressable key={item.title} onPress={show} accessibilityRole="button" accessibilityLabel={title}>
              {({ pressed: touching }) => content(touching)}
            </Pressable>
          ) : (
            <View key={item.title}>{content(false)}</View>
          );
        })}
      </View>

      <Text style={styles.sectionLabel}>Recent activity</Text>
      <View style={styles.list}>
        {ACTIVITY.map((item, index) => (
          <View key={item.title} style={[styles.item, index > 0 && styles.itemLine]}>
            <View style={[styles.itemIcon, { backgroundColor: item.received ? '#22b07d1f' : '#7d81871f' }]}>
              <Ionicons name={item.received ? 'arrow-down' : 'arrow-up'} size={18} color={item.received ? '#22b07d' : NOTE} />
            </View>
            <View style={styles.itemText}>
              <Text style={styles.itemTitle}>{item.title}</Text>
              <Text style={styles.itemCaption}>{item.caption}</Text>
            </View>
            <Text style={[styles.amount, item.received && styles.amountIn]}>{item.amount}</Text>
          </View>
        ))}
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} pointerEvents={open ? 'auto' : 'none'}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => dismiss()} accessibilityLabel="Close" />
      </Animated.View>

      <Animated.View style={[styles.sheet, { bottom: SHEET_BOTTOM, left: MARGIN, right: MARGIN }, sheetStyle]} pointerEvents={open ? 'auto' : 'none'}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.pane, frontStyle]} pointerEvents={revealed ? 'none' : 'auto'}>
          <View style={styles.top}>
            <KeyIcon />
            <Pressable style={styles.x} onPress={() => dismiss()} accessibilityRole="button" accessibilityLabel="Close">
              <Ionicons name="close" size={16} color={MUTE} />
            </Pressable>
          </View>
          <Text style={[styles.title, styles.frontTitle]}>{title}</Text>
          <View style={styles.body}>
            {body.map((line, index) => (
              <Rise key={line} progress={intro} start={index * LINE_STAGGER}>
                <Text style={styles.bodyLine}>{line}</Text>
              </Rise>
            ))}
          </View>
          <View style={styles.notes}>
            {notes.map((text, index) => (
              <Slide key={text} progress={intro} start={NOTE_START + index * NOTE_STAGGER}>
                <View style={styles.note}>
                  {index === 0 ? (
                    <Animated.View style={pulseStyle}>
                      <Ionicons name={NOTES[0].icon} size={18} color={NOTE} />
                    </Animated.View>
                  ) : (
                    <Ionicons name={NOTES[index % NOTES.length].icon} size={18} color={NOTE} />
                  )}
                  <Text style={styles.noteText}>{text}</Text>
                </View>
              </Slide>
            ))}
          </View>
          <View style={styles.buttons}>
            <Pressable style={styles.cancel} onPress={() => dismiss()} accessibilityRole="button">
              <Text style={styles.cancelLabel}>{cancelLabel}</Text>
            </Pressable>
            <Pressable style={styles.revealSlot} onPress={() => reveal(false)} accessibilityRole="button">
              {({ pressed: touching }) => (
                <View
                  style={[
                    styles.reveal,
                    { backgroundColor: touching || pressed === 'reveal' ? CYAN_PRESSED : CYAN, transform: [{ scale: touching || pressed === 'reveal' ? 0.96 : 1 }] },
                  ]}
                >
                  <FaceScan scan={scan} color="#ffffff" />
                  <Text style={styles.revealLabel}>{revealLabel}</Text>
                </View>
              )}
            </Pressable>
          </View>
        </Animated.View>

        <Animated.View style={[StyleSheet.absoluteFill, styles.pane, backStyle]} pointerEvents={revealed ? 'auto' : 'none'}>
          <View style={styles.backTop}>
            <Text style={styles.title}>{title}</Text>
            <Pressable style={styles.x} onPress={() => dismiss()} accessibilityRole="button" accessibilityLabel="Close">
              <Ionicons name="close" size={16} color={MUTE} />
            </Pressable>
          </View>
          <Pressable onPress={() => copy(false)} accessibilityRole="button" accessibilityLabel="Copy the key">
            <Animated.View style={[styles.box, { width: boxWidth }, boxStyle]}>
              {words.map((word, index) => (
                <View key={word} style={[styles.chip, { width: chipWidth }]}>
                  <Text style={styles.chipIndex}>{index + 1}</Text>
                  <Text style={styles.chipWord} numberOfLines={1}>
                    {word}
                  </Text>
                </View>
              ))}
              <Animated.View style={[styles.edge, edgeStyle]} pointerEvents="none">
                <LinearGradient colors={['rgba(20,184,255,0)', 'rgba(20,184,255,0.22)']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
                <View style={styles.edgeLine} />
              </Animated.View>
              <Animated.View style={[styles.mask, mask]} pointerEvents="none">
                {IOS ? (
                  <BlurView intensity={45} tint="systemUltraThinMaterialLight" style={[styles.blur, { width: boxWidth }]} />
                ) : (
                  <View style={[styles.blur, { width: boxWidth, backgroundColor: 'rgba(244,246,248,0.94)' }]} />
                )}
              </Animated.View>
              <Animated.View style={[StyleSheet.absoluteFill, glowStyle]} pointerEvents="none">
                <Svg width={boxWidth} height={BOX_HEIGHT}>
                  <AnimatedGlow width={boxWidth} dash={dash} animatedProps={glowProps} stroke={6} opacity={0.22} />
                  <AnimatedGlow width={boxWidth} dash={dash} animatedProps={glowProps} stroke={GLOW_WIDTH} opacity={1} />
                </Svg>
              </Animated.View>
            </Animated.View>
          </Pressable>
          <View style={styles.hintSlot}>
            <Animated.Text style={[styles.hint, hintStyle]}>Tap the key to copy it</Animated.Text>
            <Animated.View style={[styles.toast, toastStyle]}>
              <Ionicons name="checkmark-circle" size={16} color={CYAN} />
              <Text style={styles.toastLabel}>{copiedLabel}</Text>
            </Animated.View>
          </View>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const AnimatedRect = Animated.createAnimatedComponent(Rect);

/** A card with two lines of text and an eye over its corner, as the design draws it. */
function KeyIcon() {
  return (
    <Svg width={40} height={34} viewBox="0 0 40 34">
      <Rect x={1.5} y={1.5} width={30} height={22} rx={6} fill="none" stroke={MUTE} strokeWidth={2} />
      <Path d="M8 9.5h10M8 15h6" stroke={MUTE} strokeWidth={2} strokeLinecap="round" />
      <Path d="M18 24.5c2.6-4.4 5.9-6.6 9.8-6.6s7.2 2.2 9.8 6.6c-2.6 4.4-5.9 6.6-9.8 6.6s-7.2-2.2-9.8-6.6Z" fill={PAGE} stroke={MUTE} strokeWidth={2} strokeLinejoin="round" />
      <Circle cx={27.8} cy={24.5} r={2.8} fill="none" stroke={MUTE} strokeWidth={2} />
    </Svg>
  );
}

function AnimatedGlow({ width, dash, stroke, opacity, animatedProps }: { width: number; dash: number[]; stroke: number; opacity: number; animatedProps: object }) {
  return (
    <AnimatedRect
      x={stroke / 2}
      y={stroke / 2}
      width={width - stroke}
      height={BOX_HEIGHT - stroke}
      rx={BOX_RADIUS}
      fill="none"
      stroke={CYAN}
      strokeOpacity={opacity}
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeDasharray={dash}
      animatedProps={animatedProps}
    />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  header: { paddingHorizontal: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  dot: { position: 'absolute', right: -3, top: -3, width: 10, height: 10, borderRadius: 5, backgroundColor: DOT, borderWidth: 2, borderColor: PAGE },
  walletShadow: {
    marginHorizontal: MARGIN,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    boxShadow: [
      { offsetX: 0, offsetY: 16, blurRadius: 32, color: 'rgba(200,120,0,0.32)' },
      { offsetX: 0, offsetY: 2, blurRadius: 6, color: 'rgba(120,70,0,0.18)' },
    ],
  },
  wallet: { flex: 1, borderRadius: CARD_RADIUS, borderCurve: 'continuous', overflow: 'hidden' },
  walletEdge: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: CARD_RADIUS, borderCurve: 'continuous', borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)' },
  watermark: { position: 'absolute', right: -40, bottom: -56, transform: [{ rotate: '-14deg' }] },
  heartBadge: { position: 'absolute', left: 18, top: 18, width: 40, height: 40, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.2)', alignItems: 'center', justifyContent: 'center' },
  copyAddress: { position: 'absolute', right: 20, top: 28, flexDirection: 'row', alignItems: 'center', gap: 6 },
  copyAddressLabel: { color: 'rgba(255,255,255,0.92)', fontSize: 15, lineHeight: 20, fontWeight: '600' },
  walletBottom: { position: 'absolute', left: 20, bottom: 18 },
  walletName: { color: '#ffffff', fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.3 },
  balance: { color: 'rgba(255,255,255,0.78)', fontSize: 15, lineHeight: 20, fontWeight: '600', fontVariant: ['tabular-nums'] },
  customize: { position: 'absolute', right: 18, bottom: 20, height: 32, paddingHorizontal: 12, borderRadius: 16, backgroundColor: 'rgba(255,255,255,0.22)', flexDirection: 'row', alignItems: 'center', gap: 6 },
  customizeLabel: { color: '#ffffff', fontSize: 13, lineHeight: 17, fontWeight: '700' },
  sectionLabel: { marginTop: 24, marginBottom: 8, marginHorizontal: MARGIN + 4, color: MUTE, fontSize: 13, lineHeight: 18, fontWeight: '600', letterSpacing: 0.2 },
  list: { marginHorizontal: MARGIN, borderRadius: 22, borderCurve: 'continuous', backgroundColor: WELL, overflow: 'hidden' },
  item: { height: 68, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 14 },
  itemLine: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#dde1e5', marginLeft: 0 },
  itemPressed: { backgroundColor: '#e8ebef' },
  itemIcon: { width: 38, height: 38, borderRadius: 12, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  itemText: { flex: 1, gap: 1 },
  itemTitle: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  itemCaption: { color: MUTE, fontSize: 14, lineHeight: 18 },
  amount: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  amountIn: { color: '#22b07d' },
  scrim: { backgroundColor: '#000000' },
  sheet: {
    position: 'absolute',
    borderRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    overflow: 'hidden',
    boxShadow: [
      { offsetX: 0, offsetY: 24, blurRadius: 60, color: 'rgba(0,0,0,0.22)' },
      { offsetX: 0, offsetY: 2, blurRadius: 8, color: 'rgba(0,0,0,0.08)' },
    ],
  },
  pane: { paddingHorizontal: PAD, paddingTop: PAD },
  top: { height: 34, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  x: { width: 30, height: 30, borderRadius: 15, backgroundColor: CANCEL, alignItems: 'center', justifyContent: 'center' },
  frontTitle: { marginTop: 10 },
  title: { color: INK, fontSize: 24, lineHeight: 30, fontWeight: '700', letterSpacing: -0.5 },
  body: { marginTop: 6 },
  bodyLine: { color: MUTE, fontSize: 16, lineHeight: 22 },
  notes: { marginTop: 20, gap: 12 },
  note: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  noteText: { color: NOTE, fontSize: 15, lineHeight: 20 },
  buttons: { position: 'absolute', left: PAD, right: PAD, bottom: PAD, height: BUTTON_HEIGHT, flexDirection: 'row', gap: 10 },
  cancel: { width: CANCEL_WIDTH, borderRadius: BUTTON_HEIGHT / 2, backgroundColor: CANCEL, alignItems: 'center', justifyContent: 'center' },
  cancelLabel: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600' },
  revealSlot: { flex: 1 },
  reveal: {
    flex: 1,
    borderRadius: BUTTON_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 18, color: 'rgba(20,184,255,0.35)' }],
  },
  revealLabel: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '700' },
  backTop: { height: 30, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  box: {
    height: BOX_HEIGHT,
    borderRadius: BOX_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    padding: BOX_PAD,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: CHIP_GAP,
    overflow: 'hidden',
  },
  chip: {
    height: CHIP_HEIGHT,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    paddingHorizontal: 9,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(16,24,40,0.06)' }],
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: LINE,
  },
  chipIndex: { width: 14, color: CYAN, fontSize: 11, lineHeight: 14, fontFamily: MONO, fontWeight: '700', textAlign: 'right' },
  chipWord: { flex: 1, color: INK, fontSize: 14, lineHeight: 18, fontFamily: MONO, fontWeight: '600' },
  edge: { position: 'absolute', left: 0, top: 0, bottom: 0, width: EDGE },
  edgeLine: { position: 'absolute', right: 0, top: 0, bottom: 0, width: 2, backgroundColor: CYAN, boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 8, color: 'rgba(20,184,255,0.9)' }] },
  mask: { position: 'absolute', right: 0, top: 0, bottom: 0, overflow: 'hidden' },
  blur: { position: 'absolute', right: 0, top: 0, bottom: 0 },
  hintSlot: { marginTop: 14, height: 28, alignItems: 'center', justifyContent: 'center' },
  hint: { color: MUTE, fontSize: 14, lineHeight: 18 },
  toast: { position: 'absolute', height: 28, paddingHorizontal: 12, borderRadius: 14, backgroundColor: '#e6f7ff', flexDirection: 'row', alignItems: 'center', gap: 6 },
  toastLabel: { color: '#0a7fb8', fontSize: 14, lineHeight: 18, fontWeight: '700' },
});
