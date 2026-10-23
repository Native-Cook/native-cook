import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Line, Path, Polyline } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { NOTES_H, NOTES_W, WALLET_H, WALLET_W, drawBack, drawNotes, drawPocket } from './art';
import { LOOP, OUT_AT, SEQUENCE_END, USER_HOLD, heroAt } from './timeline';

type Props = {
  overline?: string;
  title?: string;
  tagline?: string;
  subtitle?: string;
  /** The large lines under the button, cut off by it and by the screen's edge. */
  teaser?: string;
  buttonLabel?: string;
  cardLabel?: string;
  cardNumber?: string;
  cardExpiry?: string;
  /** Plays the hero's sequence by itself every LOOP seconds, from the card back to the card. */
  autoPlay?: boolean;
  onGetStarted?: () => void;
  onBack?: () => void;
};

const INK = '#111111';
const TAGLINE = '#333333';
const RULE = '#888888';
const TEASER = 'rgba(85,85,85,0.6)';
const CARD_TOP = '#232323';
const CARD_BOTTOM = '#0e0e0e';
const CHIP_TOP = '#e8d2a0';
const CHIP_BOTTOM = '#b39459';
const CHIP_LINE = 'rgba(122,98,56,0.7)';
const CARD_INK = '#e5e5e5';
const CARD_MUTE = '#a8a8a8';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

// Layout, in points: the header from the safe area's top, the footer from its bottom, the hero
// a little below the middle.
const BACK_TOP = 40;
const OVERLINE_TOP = 127;
const TITLE_TOP = 144;
const HERO_AT = 0.545;
const TAGLINE_UP = 211;
const RULE_UP = 147;
const RULE_X = 48;
const SUBTITLE_UP = 92;
const BUTTON_UP = 74;
const BUTTON_HEIGHT = 44;
const MARGIN = 24;
const TEASER_UP = 49;
const TEASER_X = 72;
const FADE = 120;
const CARD_W = 200;
const CARD_H = 128;
const CARD_RADIUS = 12;
/** The perspective the card tips back in, in points from the eye. */
const PERSPECTIVE = 700;
const PRESSED = 0.97;

// The contactless mark: three arcs opening to the right, in a 14-point box.
const WAVES = [3, 6, 9].map((r) => {
  const a = Math.PI / 4;
  return `M${2 + r * Math.cos(a)} ${7 - r * Math.sin(a)}A${r} ${r} 0 0 1 ${2 + r * Math.cos(a)} ${7 + r * Math.sin(a)}`;
});

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Card({ label, number, expiry }: { label: string; number: string; expiry: string }) {
  return (
    <LinearGradient colors={[CARD_TOP, CARD_BOTTOM]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.card}>
      <LinearGradient colors={[CHIP_TOP, CHIP_BOTTOM]} style={styles.chip}>
        <View style={[styles.chipLine, { top: 7 }]} />
        <View style={[styles.chipLine, { top: 14 }]} />
        <View style={styles.chipStem} />
      </LinearGradient>
      <Svg width={14} height={14} style={styles.contactless}>
        {WAVES.map((d) => (
          <Path key={d} d={d} stroke={CARD_INK} strokeWidth={1.2} strokeLinecap="round" fill="none" />
        ))}
      </Svg>
      <Text style={styles.cardLabel} numberOfLines={1}>
        {label}
      </Text>
      <Text style={styles.cardNumber} numberOfLines={1}>
        {number}
      </Text>
      <Text style={styles.cardExpiry} numberOfLines={1}>
        {expiry}
      </Text>
    </LinearGradient>
  );
}

/**
 * A welcome screen whose hero changes hands: the black card tips back and drops behind a green
 * card wallet, two notes rise out of the wallet's pocket and slide back in, and the card rises
 * out of it and lands where it started. A tap on the hero plays it.
 */
export function WalletUnpackWelcome({
  overline = 'EXPECT MORE FROM YOUR',
  title = 'WALLET',
  tagline = 'a prepaid wallet designed for the\ncreditworthy',
  subtitle = "A PAYMENT EXPERIENCE THAT'S",
  teaser = 'FAST,\nCONVENIENT &',
  buttonLabel = 'Get started',
  cardLabel = 'CREDIT CARD',
  cardNumber = '1234 5678 9123 XXXX',
  cardExpiry = '12/25',
  autoPlay = false,
  onGetStarted,
  onBack,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [walletShown, setWalletShown] = useState(false);

  const heroY = height * HERO_AT;
  const bottom = height - insets.bottom;

  // Seconds since the first frame; when the loop started (-1 while touches have the screen); when
  // a tap started the sequence (-1 when none is playing).
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  const origin = useSharedValue(autoPlay ? 0 : -1);
  const playAt = useSharedValue(-1);
  const touch = useSharedValue({ down: false, last: 0 });

  /** Seconds into the sequence at `now`: the loop's while it plays, else a tap's. */
  const sequenceAt = (now: number) => {
    'worklet';
    const start = origin.get();
    if (start >= 0 && now >= start) return (now - start) % LOOP;
    const played = playAt.get();
    if (played >= 0 && now - played < SEQUENCE_END) return now - played;
    return 0;
  };

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    clock.set(now);
    const t = touch.get();
    const played = playAt.get();
    // A while after the last touch and the end of the sequence, the loop starts again from rest.
    if (autoPlay && origin.get() < 0 && !t.down && now >= Math.max(t.last, played >= 0 ? played + SEQUENCE_END : 0) + USER_HOLD) {
      origin.set(now);
      playAt.set(-1);
    }
  });

  const hero = useDerivedValue(() => heroAt(sequenceAt(clock.get()), still));

  useAnimatedReaction(
    () => hero.get().wallet > 0.001,
    (shown, before) => {
      if (shown !== before) scheduleOnRN(setWalletShown, shown);
    },
  );

  const back = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    drawBack(recorder.beginRecording(Skia.XYWHRect(0, 0, WALLET_W, WALLET_H)));
    return recorder.finishRecordingAsPicture();
  }, []);
  const pocket = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    drawPocket(recorder.beginRecording(Skia.XYWHRect(0, 0, WALLET_W, WALLET_H)));
    return recorder.finishRecordingAsPicture();
  }, []);
  const notes = useDerivedValue(() => {
    const h = hero.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, NOTES_W, NOTES_H));
    drawNotes(
      canvas,
      { y: h.note1Y, turn: h.note1Turn, wave: h.note1Wave, blur: h.note1Blur },
      { y: h.note2Y, turn: h.note2Turn, wave: h.note2Wave, blur: h.note2Blur },
      h.phase,
    );
    return recorder.finishRecordingAsPicture();
  });

  /** A touch takes the screen from the loop; a sequence it was in the middle of plays on. */
  const takeOver = () => {
    'worklet';
    const now = clock.get();
    touch.set({ down: true, last: now });
    if (origin.get() < 0) return;
    const q = sequenceAt(now);
    playAt.set(q > OUT_AT && q < SEQUENCE_END ? now - q : -1);
    origin.set(-1);
  };
  const letGo = () => {
    'worklet';
    touch.set({ down: false, last: clock.get() });
  };
  const play = () => {
    'worklet';
    const now = clock.get();
    const played = playAt.get();
    if (played >= 0 && now - played < SEQUENCE_END) return;
    playAt.set(now - OUT_AT);
  };

  const walletStyle = useAnimatedStyle(() => ({ opacity: hero.get().wallet, transform: [{ scale: hero.get().walletScale }] }));
  const cardStyle = useAnimatedStyle(() => {
    const h = hero.get();
    return { opacity: h.card, transform: [{ translateY: h.cardY }, { rotate: `${h.turn}deg` }, { scale: h.cardScale }] };
  });
  const tipStyle = useAnimatedStyle(() => ({ transform: [{ perspective: PERSPECTIVE }, { rotateX: `${hero.get().tip}deg` }] }));
  const blankStyle = useAnimatedStyle(() => ({ opacity: 1 - hero.get().face }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const centred = (w: number, h: number) => ({ left: width / 2 - w / 2, top: heroY - h / 2, width: w, height: h });

  return (
    <View
      style={styles.screen}
      onLayout={onLayout}
      onTouchStart={() => scheduleOnUI(takeOver)}
      onTouchEnd={() => scheduleOnUI(letGo)}
      onTouchCancel={() => scheduleOnUI(letGo)}
    >
      <StatusBar style="dark" />
      {/* A soft band of shade across the white, like light on brushed metal. */}
      <LinearGradient
        colors={['#ffffff', '#dadada', '#ffffff', '#e9e9e9']}
        locations={[0, 0.42, 0.68, 1]}
        start={{ x: 0, y: 0.15 }}
        end={{ x: 1, y: 0.85 }}
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient colors={['#a9a9a9', 'rgba(169,169,169,0)']} style={[styles.topShade, { height: insets.top + 30 }]} />

      <Pressable
        style={[styles.back, { top: insets.top + BACK_TOP - 22 }]}
        accessibilityRole="button"
        accessibilityLabel="Back"
        onPress={() => onBack?.()}
      >
        <Svg width={20} height={12}>
          <Line x1={0.75} y1={6} x2={20} y2={6} stroke={INK} strokeWidth={1.5} strokeLinecap="round" />
          <Polyline points="5.5,1 0.75,6 5.5,11" stroke={INK} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </Svg>
      </Pressable>

      <Text style={[styles.overline, { top: insets.top + OVERLINE_TOP }]} numberOfLines={1}>
        {overline}
      </Text>
      <Text style={[styles.title, { top: insets.top + TITLE_TOP }]} numberOfLines={1} accessibilityRole="header">
        {title}
      </Text>

      <Pressable
        style={[styles.hero, centred(WALLET_W + 40, WALLET_H + 40)]}
        accessibilityRole="button"
        accessibilityLabel="Card and wallet"
        accessibilityHint="Plays the animation"
        onPress={() => {
          tap();
          scheduleOnUI(play);
        }}
      />
      <Animated.View pointerEvents="none" style={[styles.layer, centred(WALLET_W, WALLET_H), walletStyle]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={back} />
        </Canvas>
      </Animated.View>
      {walletShown && (
        <Animated.View pointerEvents="none" style={[styles.layer, centred(NOTES_W, NOTES_H), walletStyle]}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={notes} />
          </Canvas>
        </Animated.View>
      )}
      <Animated.View pointerEvents="none" style={[styles.layer, centred(CARD_W, CARD_H), cardStyle]}>
        <Animated.View style={[styles.fill, tipStyle]}>
          <Card label={cardLabel} number={cardNumber} expiry={cardExpiry} />
          {/* Past edge-on the card shows its plain back: the printing goes first. */}
          <Animated.View style={[styles.cardBlank, blankStyle]} />
        </Animated.View>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.layer, centred(WALLET_W, WALLET_H), walletStyle]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={pocket} />
        </Canvas>
      </Animated.View>

      <Text style={[styles.tagline, { top: bottom - TAGLINE_UP }]} lineBreakStrategyIOS="standard">
        {tagline}
      </Text>
      <Svg width={width - RULE_X * 2} height={1} style={[styles.rule, { top: bottom - RULE_UP }]}>
        <Line x1={0} y1={0.5} x2={width - RULE_X * 2} y2={0.5} stroke={RULE} strokeWidth={1} strokeDasharray="1.5,2.5" />
      </Svg>

      <Text style={[styles.teaser, { top: bottom - TEASER_UP }]} numberOfLines={2}>
        {teaser}
      </Text>
      <LinearGradient
        pointerEvents="none"
        colors={['rgba(189,189,189,0)', 'rgba(189,189,189,0.9)']}
        style={[styles.bottomShade, { top: height - FADE }]}
      />
      <Text style={[styles.subtitle, { top: bottom - SUBTITLE_UP }]} numberOfLines={1}>
        {subtitle}
      </Text>
      <Pressable
        style={[styles.buttonSlot, { top: bottom - BUTTON_UP }]}
        accessibilityRole="button"
        onPress={() => {
          tap();
          onGetStarted?.();
        }}
      >
        {({ pressed }) => (
          <Animated.View style={[styles.button, { transform: [{ scale: pressed ? PRESSED : 1 }], transitionProperty: 'transform', transitionDuration: 100 }]}>
            <Text style={styles.buttonLabel} numberOfLines={1}>
              {buttonLabel}
            </Text>
          </Animated.View>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#ffffff',
    overflow: 'hidden',
  },
  fill: {
    flex: 1,
  },
  topShade: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  back: {
    position: 'absolute',
    left: MARGIN - 12,
    width: 44,
    height: 44,
    justifyContent: 'center',
    paddingLeft: 12,
  },
  overline: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    textAlign: 'center',
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 2,
    includeFontPadding: false,
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontSize: 76,
    lineHeight: 84,
    fontWeight: '200',
    letterSpacing: 1,
    includeFontPadding: false,
  },
  hero: {
    position: 'absolute',
  },
  layer: {
    position: 'absolute',
  },
  card: {
    flex: 1,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  cardBlank: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD_BOTTOM,
  },
  chip: {
    position: 'absolute',
    left: 20,
    top: 32,
    width: 28,
    height: 22,
    borderRadius: 4,
  },
  chipLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: CHIP_LINE,
  },
  chipStem: {
    position: 'absolute',
    left: 13.5,
    top: 7,
    width: 1,
    height: 8,
    backgroundColor: CHIP_LINE,
  },
  contactless: {
    position: 'absolute',
    right: 18,
    top: 36,
  },
  cardLabel: {
    position: 'absolute',
    right: 16,
    top: 14,
    color: '#cfcfcf',
    fontSize: 6,
    lineHeight: 8,
    fontWeight: '500',
    letterSpacing: 1.2,
    includeFontPadding: false,
  },
  cardNumber: {
    position: 'absolute',
    left: 20,
    top: 78,
    color: CARD_INK,
    fontFamily: MONO,
    fontSize: 12,
    lineHeight: 16,
    letterSpacing: 1,
    includeFontPadding: false,
  },
  cardExpiry: {
    position: 'absolute',
    left: 20,
    top: 98,
    color: CARD_MUTE,
    fontFamily: MONO,
    fontSize: 7,
    lineHeight: 10,
    includeFontPadding: false,
  },
  tagline: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    textAlign: 'center',
    color: TAGLINE,
    fontSize: 11,
    lineHeight: 20,
    includeFontPadding: false,
  },
  rule: {
    position: 'absolute',
    left: RULE_X,
  },
  teaser: {
    position: 'absolute',
    left: TEASER_X,
    right: 0,
    color: TEASER,
    fontSize: 36,
    lineHeight: 38,
    fontWeight: '200',
    includeFontPadding: false,
  },
  bottomShade: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: FADE,
  },
  subtitle: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    textAlign: 'center',
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 1.5,
    includeFontPadding: false,
  },
  buttonSlot: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: BUTTON_HEIGHT,
  },
  button: {
    height: BUTTON_HEIGHT,
    borderRadius: 2,
    backgroundColor: '#000000',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
