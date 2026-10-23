import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, RadialGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FLAME_SIZE, Flame, WINGS_HEIGHT, WINGS_WIDTH, Wings } from './Flame';
import { easeInOut, easeOut, seg, spring, useLoopClock } from './motion';

type Props = {
  /** The streak before today; the count rolls up to the next number. */
  days?: number;
  /** How many days of the week the bar fills, 0 to 7. */
  weekDays?: number;
  streakLabel?: string;
  /** The heading inside the week card. */
  weekLabel?: string;
  shareLabel?: string;
  continueLabel?: string;
  /** Plays by itself every LOOP seconds and ends on the coin again. */
  autoPlay?: boolean;
  onShare?: () => void;
  /** Called when Continue is pressed by a touch. */
  onContinue?: () => void;
};

// The timeline, in seconds from the start of the loop.
export const LOOP = 9;
const TAP_AT = 0.5;
// The coin spins with this period; LOOP holds a whole number of them.
const COIN_PERIOD = 0.9;
const POP_AT = 0.9;
const COIN_OUT_IN = 0.12;
const SPARKS_FOR = 0.5;
const WINGS_FOR = 0.35;
const ROLL_AT = 1.7;
const ROLL_FOR = 0.3;
const BRIGHTEN_AT = 1.6;
const BRIGHTEN_FOR = 0.5;
const LABEL_AT = 1.8;
const LABEL_IN = 0.4;
const LIFT_AT = 2.4;
const LIFT_IN = 0.5;
const CARD_AT = 2.6;
const CARD_IN = 0.5;
const FILL_AT = 3.2;
const FILL_FOR = 0.6;
const BUTTONS_AT = 4.0;
const BUTTON_STAGGER = 0.08;
const BUTTON_IN = 0.45;
const GLINT_AT = 5.2;
const GLINT_FOR = 0.6;
const BREATHE_PERIOD = 1.6;
const OUT_AT = 7.6;
const OUT_IN = 0.8;
const BACK_AT = 8.4;
const BACK_IN = 0.4;
const REDUCED_FADE = 0.3;
// The flame pops on an under-damped spring (ratio 0.5: 2 * 0.5 * sqrt(stiffness)).
const POP_STIFFNESS = 240;
const POP_DAMPING = 15.5;
const POP_FROM = 0.6;
const BREATHE = 0.04;
const LIFT = 50;
const OUT_SLIDE = 24;
const CARD_SLIDE = 30;
const BUTTON_SLIDE = 60;

const PAGE = '#0b0d10';
const GOLD = '#ffd84a';
const GHOST = 'rgba(255,255,255,0.1)';
const CARD = '#14171c';
const CARD_LINE = '#2a2e36';
const DAY = '#5c6270';
const ORANGE = '#ff7a18';
const BLUE = '#1cb0f6';

const COIN_WIDTH = 84;
const COIN_HEIGHT = 58;
const COUNT_SIZE = 88;
const COUNT_LINE = 98;
const MARGIN = 20;
const CARD_HEIGHT = 112;
const TRACK = 12;
const BUTTON = 56;
const GLOW = 460;
// The system's rounded face for the count and the labels.
const ROUNDED = Platform.select({ ios: { fontFamily: 'ui-rounded' }, default: {} });
const DAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

// Fourteen sparks leave the flame in a ring, 80 to 130 points out.
const SPARKS = Array.from({ length: 14 }, (_, i) => {
  const angle = (i / 14) * Math.PI * 2 + 0.3;
  const reach = 80 + ((i * 37) % 50);
  return { dx: Math.cos(angle) * reach, dy: Math.sin(angle) * reach, size: 5 + (i % 3) * 3, color: i % 3 === 1 ? '#ff7a18' : '#ffd23a' };
});

function Spark({ index, clock, flameY, width }: { index: number; clock: SharedValue<number>; flameY: number; width: number }) {
  const spark = SPARKS[index];
  const style = useAnimatedStyle(() => {
    const p = seg(clock.get(), POP_AT + 0.1, POP_AT + 0.1 + SPARKS_FOR);
    return {
      opacity: p === 0 || p === 1 ? 0 : 1 - p * p,
      transform: [{ translateX: width / 2 - spark.size / 2 + spark.dx * easeOut(p) }, { translateY: flameY - spark.size / 2 + spark.dy * easeOut(p) }, { scale: 1 - 0.6 * p }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.spark, { width: spark.size, height: spark.size, borderRadius: spark.size / 2, backgroundColor: spark.color }, style]} />;
}

/**
 * A streak screen: a gold coin spins, pops into a flame mascot, and the day count rolls up by one.
 * The flame lifts, a week bar fills and the buttons slide up; after a pause everything slides away
 * and the coin comes back. One clock drives all of it, so a touch and autoPlay run the same motion.
 */
export function StreakFlameTakeover({
  days = 1824,
  weekDays = 2,
  streakLabel = 'day streak',
  weekLabel = 'This week',
  shareLabel = 'SHARE MILESTONE',
  continueLabel = 'CONTINUE',
  autoPlay = false,
  onShare,
  onContinue,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const { clock, play } = useLoopClock(LOOP, TAP_AT, autoPlay);

  const flameY = Math.max(insets.top + 150, height * 0.33);
  const countTop = flameY + FLAME_SIZE / 2 + 6;
  const labelTop = countTop + COUNT_LINE - 6;
  const cardTop = labelTop + 52 - LIFT;
  const cardWidth = width - MARGIN * 2;
  const trackWidth = cardWidth - 32;
  const bottom = insets.bottom + 8;

  const head = String(days + 1).slice(0, -1);
  const from = String(days).slice(-1);
  const to = String((days + 1) % 10);

  const dismiss = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    play(OUT_AT, true);
    onContinue?.();
  };

  const glint = useDerivedValue(() => seg(clock.get(), GLINT_AT, GLINT_AT + GLINT_FOR));
  // The sparkle by the shades flashes with each glint pass.
  const sparkle = useDerivedValue(() => {
    const t = clock.get();
    return Math.max(Math.sin(Math.PI * seg(t, POP_AT + 0.3, POP_AT + 0.9)), Math.sin(Math.PI * seg(t, GLINT_AT + 0.2, GLINT_AT + 0.8)));
  });

  /** How far the flame, count and label have lifted, and how far they have slid away at the end. */
  const lift = (t: number) => {
    'worklet';
    if (t >= BACK_AT) return 0;
    const up = reduced ? 0 : easeInOut(seg(t, LIFT_AT, LIFT_AT + LIFT_IN)) * -LIFT;
    return up + (reduced ? 0 : easeInOut(seg(t, OUT_AT, OUT_AT + OUT_IN)) * (OUT_SLIDE + LIFT));
  };
  /** 1 while the screen is up, fading to 0 at the end and back to 1 for the resting count. */
  const present = (t: number) => {
    'worklet';
    return t >= BACK_AT ? 0 : 1 - seg(t, OUT_AT, OUT_AT + OUT_IN);
  };

  const coinStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const gone = seg(t, POP_AT, POP_AT + COIN_OUT_IN);
    const back = seg(t, BACK_AT, BACK_AT + BACK_IN);
    const spin = reduced ? 1 : Math.max(0.08, Math.abs(Math.cos((t / COIN_PERIOD) * Math.PI * 2)));
    return {
      opacity: t < POP_AT ? 1 : t >= BACK_AT ? back : 1 - gone,
      transform: [{ scaleX: spin * (1 - 0.4 * gone) }, { scaleY: 1 - 0.4 * gone }],
    };
  });
  const flameStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const gone = present(t);
    if (t < POP_AT || gone === 0) return { opacity: 0 };
    if (reduced) return { opacity: Math.min(seg(t, POP_AT, POP_AT + REDUCED_FADE), gone) };
    const pop = POP_FROM + (1 - POP_FROM) * spring(t - POP_AT, POP_STIFFNESS, POP_DAMPING);
    const breathe = (BREATHE * (1 - Math.cos((t / BREATHE_PERIOD) * Math.PI * 2))) / 2 * seg(t, LIFT_AT, LIFT_AT + LIFT_IN);
    return { opacity: gone, transform: [{ translateY: lift(t) }, { scale: pop * (1 + breathe) }] };
  });
  // A warm glow comes up behind the flame as it pops, and follows it up.
  const haloStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const p = seg(t, POP_AT, POP_AT + 0.5);
    return { opacity: p * present(t), transform: [{ translateY: lift(t) }, { scale: reduced ? 1 : 0.6 + 0.4 * easeOut(p) }] };
  });
  const wingsStyle = useAnimatedStyle(() => {
    const p = seg(clock.get(), POP_AT + 0.05, POP_AT + 0.05 + WINGS_FOR);
    const flap = Math.sin(p * Math.PI * 3);
    return { opacity: reduced || p === 0 || p === 1 ? 0 : 1 - p * 0.3, transform: [{ scaleX: 0.7 + 0.3 * easeOut(p) }, { scaleY: 0.8 + 0.2 * flap }] };
  });

  // Only the colour changes here: the row it sits in carries the lift and the fade.
  const countStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const bright = t >= BACK_AT ? 0 : seg(t, BRIGHTEN_AT, BRIGHTEN_AT + BRIGHTEN_FOR);
    return { color: interpolateColor(bright, [0, 1], [GHOST, GOLD], 'RGB', { gamma: 1 }) };
  });
  const digitStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const roll = t >= BACK_AT ? 0 : reduced ? seg(t, ROLL_AT, ROLL_AT + REDUCED_FADE) : easeInOut(seg(t, ROLL_AT, ROLL_AT + ROLL_FOR));
    return { transform: [{ translateY: -COUNT_LINE * roll }] };
  });
  const countWrapStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const opacity = t >= BACK_AT ? seg(t, BACK_AT, BACK_AT + BACK_IN) : present(t);
    return { opacity, transform: [{ translateY: lift(t) }] };
  });
  const labelStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const p = seg(t, LABEL_AT, LABEL_AT + LABEL_IN);
    return { opacity: p * present(t), transform: [{ translateY: lift(t) }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const p = easeOut(seg(t, CARD_AT, CARD_AT + CARD_IN));
    const out = easeInOut(seg(t, OUT_AT, OUT_AT + OUT_IN));
    return { opacity: p * present(t), transform: [{ translateY: reduced ? 0 : (1 - p) * CARD_SLIDE + (t >= BACK_AT ? 0 : out * OUT_SLIDE) }] };
  });
  const fillStyle = useAnimatedStyle(() => {
    const p = easeInOut(seg(clock.get(), FILL_AT, FILL_AT + FILL_FOR));
    return { width: trackWidth * (weekDays / 7) * p };
  });
  const glowStyle = useAnimatedStyle(() => {
    const p = seg(clock.get(), FILL_AT, FILL_AT + FILL_FOR + 0.4);
    return { opacity: 0.9 * Math.sin(Math.PI * p), transform: [{ translateX: trackWidth * (weekDays / 7) * easeInOut(Math.min(1, p * 1.7)) }] };
  });
  // The clock is read in each hook and passed in: a shared value read inside a helper is not tracked.
  const buttonStyle = (t: number, index: number) => {
    'worklet';
    const at = BUTTONS_AT + index * BUTTON_STAGGER;
    const p = easeOut(seg(t, at, at + BUTTON_IN));
    return { opacity: p * present(t), transform: [{ translateY: reduced ? 0 : (1 - p) * BUTTON_SLIDE + (t >= BACK_AT ? 0 : easeInOut(seg(t, OUT_AT, OUT_AT + OUT_IN)) * OUT_SLIDE) }] };
  };
  const shareStyle = useAnimatedStyle(() => buttonStyle(clock.get(), 0));
  const continueStyle = useAnimatedStyle(() => buttonStyle(clock.get(), 1));

  return (
    <Pressable style={styles.screen} onPress={() => play()} accessibilityLabel={`${days + 1} ${streakLabel}`}>
      <StatusBar style="light" />

      <Animated.View pointerEvents="none" style={[styles.coinSlot, { left: width / 2 - COIN_WIDTH / 2, top: flameY - COIN_HEIGHT / 2 }]}>
        <View style={styles.coinShadow} />
        <Animated.View style={[styles.coin, coinStyle]}>
          <LinearGradient colors={['#ffe066', '#ffbf00', '#e08a00']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
          <View style={styles.coinFace}>
            <Ionicons name="flame" size={24} color="rgba(214,120,0,0.9)" />
          </View>
        </Animated.View>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.halo, { left: width / 2 - GLOW / 2, top: flameY - GLOW / 2 }, haloStyle]}>
        <Canvas style={styles.haloCanvas}>
          <Circle cx={GLOW / 2} cy={GLOW / 2} r={GLOW / 2}>
            <RadialGradient c={vec(GLOW / 2, GLOW / 2)} r={GLOW / 2} colors={['rgba(255,170,0,0.34)', 'rgba(255,120,0,0.1)', 'rgba(255,120,0,0)']} positions={[0, 0.45, 1]} />
          </Circle>
        </Canvas>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.wings, { top: flameY - WINGS_HEIGHT / 2 - 20, left: width / 2 - WINGS_WIDTH / 2 }, wingsStyle]}>
        <Wings />
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.flame, { left: width / 2 - FLAME_SIZE / 2, top: flameY - FLAME_SIZE / 2 }, flameStyle]}>
        <Flame glint={glint} sparkle={sparkle} />
      </Animated.View>
      {SPARKS.map((_, i) => (
        <Spark key={i} index={i} clock={clock} flameY={flameY} width={width} />
      ))}

      <Animated.View pointerEvents="none" style={[styles.countRow, { top: countTop }, countWrapStyle]}>
        <Animated.Text style={[styles.count, countStyle]}>{head}</Animated.Text>
        <View style={styles.digitWindow}>
          <Animated.View style={digitStyle}>
            <Animated.Text style={[styles.count, countStyle]}>{from}</Animated.Text>
            <Animated.Text style={[styles.count, countStyle]}>{to}</Animated.Text>
          </Animated.View>
        </View>
      </Animated.View>
      <Animated.Text style={[styles.label, { top: labelTop }, labelStyle]}>{streakLabel}</Animated.Text>

      <Animated.View style={[styles.card, { top: cardTop, left: MARGIN, width: cardWidth }, cardStyle]} pointerEvents="none">
        <View style={styles.cardHead}>
          <Text style={styles.cardTitle}>{weekLabel}</Text>
          <View style={styles.cardCount}>
            <Ionicons name="flame" size={14} color={ORANGE} />
            <Text style={styles.cardCountText}>
              {weekDays} of 7
            </Text>
          </View>
        </View>
        <View style={styles.days}>
          {DAYS.map((day, i) => (
            <Text key={day} style={[styles.day, i === weekDays - 1 && styles.dayOn]}>
              {day}
            </Text>
          ))}
        </View>
        <View style={[styles.track, { width: trackWidth }]}>
          <Animated.View style={[styles.fill, fillStyle]}>
            <LinearGradient colors={[ORANGE, '#ffc800']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
          <Animated.View style={[styles.glow, glowStyle]} />
        </View>
      </Animated.View>

      <Animated.View style={[styles.share, { bottom: bottom + 52 }, shareStyle]}>
        <Pressable style={styles.shareButton} onPress={onShare} accessibilityRole="button">
          <Ionicons name="share-outline" size={20} color="#ffffff" />
          <Text style={styles.shareLabel}>{shareLabel}</Text>
        </Pressable>
      </Animated.View>
      <Animated.View style={[styles.continue, { bottom }, continueStyle]}>
        <Pressable style={styles.continueButton} onPress={dismiss} accessibilityRole="button">
          <Text style={styles.continueLabel}>{continueLabel}</Text>
        </Pressable>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  coinSlot: { position: 'absolute', width: COIN_WIDTH, height: COIN_HEIGHT, alignItems: 'center' },
  coinShadow: { position: 'absolute', top: COIN_HEIGHT - 6, width: COIN_WIDTH + 20, height: 18, borderRadius: 9, backgroundColor: 'rgba(255,200,0,0.1)' },
  coin: { width: COIN_WIDTH, height: COIN_HEIGHT, borderRadius: COIN_HEIGHT / 2, overflow: 'hidden', alignItems: 'center', justifyContent: 'center', borderWidth: 3, borderColor: '#f2a100' },
  coinFace: { width: 46, height: 32, borderRadius: 16, backgroundColor: 'rgba(255,244,190,0.55)', alignItems: 'center', justifyContent: 'center' },
  halo: { position: 'absolute', width: GLOW, height: GLOW },
  haloCanvas: { width: GLOW, height: GLOW },
  wings: { position: 'absolute', width: WINGS_WIDTH, height: WINGS_HEIGHT },
  flame: { position: 'absolute', width: FLAME_SIZE, height: FLAME_SIZE },
  spark: { position: 'absolute', left: 0, top: 0 },
  countRow: { position: 'absolute', left: 0, right: 0, height: COUNT_LINE, flexDirection: 'row', justifyContent: 'center' },
  count: {
    ...ROUNDED,
    color: GOLD,
    fontSize: COUNT_SIZE,
    lineHeight: COUNT_LINE,
    fontWeight: '900',
    letterSpacing: -2,
    fontVariant: ['tabular-nums'],
    textShadowColor: 'rgba(255, 150, 0, 0.45)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 18,
  },
  digitWindow: { height: COUNT_LINE, overflow: 'hidden' },
  label: { ...ROUNDED, position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#ffffff', fontSize: 22, lineHeight: 28, fontWeight: '800' },
  card: {
    position: 'absolute',
    height: CARD_HEIGHT,
    borderRadius: 22,
    backgroundColor: CARD,
    borderWidth: 1,
    borderColor: CARD_LINE,
    paddingHorizontal: 16,
    justifyContent: 'center',
    gap: 12,
    boxShadow: '0 18px 40px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.05)',
  },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardTitle: { ...ROUNDED, color: '#ffffff', fontSize: 16, lineHeight: 20, fontWeight: '800' },
  cardCount: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  cardCountText: { ...ROUNDED, color: '#9aa1ae', fontSize: 14, lineHeight: 18, fontWeight: '700' },
  days: { flexDirection: 'row', justifyContent: 'space-between' },
  day: { ...ROUNDED, width: 30, textAlign: 'center', color: DAY, fontSize: 13, lineHeight: 16, fontWeight: '800' },
  dayOn: { color: ORANGE },
  track: { height: TRACK, borderRadius: TRACK / 2, backgroundColor: '#1f232a' },
  fill: { height: TRACK, borderRadius: TRACK / 2, overflow: 'hidden' },
  glow: { position: 'absolute', left: -9, top: -5, width: 22, height: 22, borderRadius: 11, backgroundColor: '#ffc23a', boxShadow: '0 0 16px 4px rgba(255, 170, 0, 0.8)' },
  share: { position: 'absolute', left: MARGIN, right: MARGIN, height: BUTTON },
  shareButton: {
    flex: 1,
    borderRadius: 16,
    backgroundColor: BLUE,
    borderBottomWidth: 4,
    borderBottomColor: '#1593d0',
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 10px 24px rgba(28, 176, 246, 0.25)',
  },
  shareLabel: { ...ROUNDED, color: '#ffffff', fontSize: 16, lineHeight: 20, fontWeight: '900', letterSpacing: 0.8 },
  continue: { position: 'absolute', left: MARGIN, right: MARGIN, height: 44 },
  continueButton: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  continueLabel: { ...ROUNDED, color: BLUE, fontSize: 16, lineHeight: 20, fontWeight: '900', letterSpacing: 0.8 },
});
