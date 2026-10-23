import { Ionicons } from '@expo/vector-icons';
import { Blur, Canvas, Circle, DashPathEffect, Line, LinearGradient as SkiaGradient, Oval, Path, RadialGradient, Shadow, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { easeInOut, easeOut, seg, spring, useLoopClock } from './motion';
import { Coins, Confetti } from './Particles';

type Row = { label: string; value: string };

type Props = {
  /** The amount in cents, counted up on the card. */
  amount?: number;
  currency?: string;
  title?: string;
  subtitle?: string;
  /** The receipt's number, shown at its top right. */
  reference?: string;
  /** The fee row above the total. */
  fee?: string;
  rows?: Row[];
  claimLabel?: string;
  /** The reward the bar reveals. */
  reward?: string;
  /** Plays by itself every LOOP seconds and ends on a white screen again. */
  autoPlay?: boolean;
  /** Called when the claim bar is pressed by a touch. */
  onClaim?: () => void;
};

// The timeline, in seconds from the start of the loop.
export const LOOP = 10;
const TAP_AT = 0.5;
const POP_AT = 0.6;
const RIPPLE_FOR = 0.6;
const SHRINK_AT = 1.4;
const SHRINK_IN = 0.55;
const TITLE_AT = 1.85;
const TITLE_IN = 0.35;
const RECEIPT_AT = 2.0;
const ROW_STAGGER = 0.08;
const COUNT_AT = 3.2;
const COUNT_FOR = 0.9;
const SHEEN_AT = 4.4;
const SHEEN_FOR = 1.0;
const PEEL_AT = 5.6;
const PEEL_FOR = 1.0;
const COINS_AT = 5.9;
const FADE_AT = 8.7;
const FADE_IN = 0.8;
const REDUCED_FADE = 0.3;
// Springs: the check pops with overshoot, the receipt settles.
const POP_STIFFNESS = 260;
const POP_DAMPING = 14;
const RECEIPT_STIFFNESS = 150;
const RECEIPT_DAMPING = 20;
// How far the screen rises as it fades out.
const RISE = 80;

const GREEN = '#2fbf71';
const GREEN_LIGHT = '#7beaa9';
const GREEN_DEEP = '#16a05a';
const PAGE = '#f3f4f6';
const PAPER = '#ffffff';
const INK = '#101318';
const VALUE = '#1f2328';
const LABEL = '#8a8f98';
const RULE = '#dfe2e7';
const SILVER_INK = '#5f636b';
const REWARD_INK = '#3d4048';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

const CHECK_SIZE = 148;
const CHECK_SMALL = 72;
const CHECK_PATH = 'M48 76L66 94L102 56';
const MARGIN = 16;
const CARD_RADIUS = 22;
const PAD = 20;
const HEAD_HEIGHT = 48;
const AMOUNT_HEIGHT = 92;
const RULE_HEIGHT = 24;
const ROW_HEIGHT = 38;
const TOTAL_HEIGHT = 48;
const TOOTH = 12;
const TEETH_HEIGHT = 8;
const PUNCH = 10;
const BAR_HEIGHT = 56;
const BAR_GAP = 18;
// Room round the receipt's drawn paper for its shadow.
const SHADOW_ROOM = 28;

const DEFAULT_ROWS: Row[] = [
  { label: 'To', value: 'Mia Carter' },
  { label: 'Email', value: 'hello@example.com' },
  { label: 'Method', value: 'E-Transfer' },
  { label: 'Date', value: 'June 12, 2024 · 9:41' },
];

function money(cents: number) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}

/**
 * The receipt's paper: rounded at the top, torn into teeth at the bottom, with a half-circle punch
 * on each side at `punchY`. Drawn `SHADOW_ROOM` in from the canvas edges.
 */
function paper(width: number, height: number, punchY: number) {
  const o = SHADOW_ROOM;
  const r = CARD_RADIUS;
  const bottom = o + height;
  let d = `M${o} ${o + r}A${r} ${r} 0 0 1 ${o + r} ${o}H${o + width - r}A${r} ${r} 0 0 1 ${o + width} ${o + r}`;
  d += `V${o + punchY - PUNCH}A${PUNCH} ${PUNCH} 0 0 0 ${o + width} ${o + punchY + PUNCH}V${bottom}`;
  const count = Math.round(width / TOOTH);
  const step = width / count;
  for (let i = count; i > 0; i--) d += `L${o + (i - 0.5) * step} ${bottom + TEETH_HEIGHT}L${o + (i - 1) * step} ${bottom}`;
  d += `V${o + punchY + PUNCH}A${PUNCH} ${PUNCH} 0 0 0 ${o} ${o + punchY - PUNCH}Z`;
  return d;
}

/**
 * A payment receipt: a green check pops with confetti, shrinks to the top while the receipt card
 * slides up and its amount counts, then a silver bar peels back to show a reward and coins spill.
 * One clock drives all of it, so a touch and the autoPlay loop run the same motion.
 */
export function PaymentCheckBurst({
  amount = 57862,
  currency = 'CA $',
  title = 'Payment Complete',
  subtitle = 'Your transfer is on its way.',
  reference = 'NC-20481',
  fee = '$0.00',
  rows = DEFAULT_ROWS,
  claimLabel = 'Peel to claim your reward',
  reward = '+400 points',
  autoPlay = false,
  onClaim,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const { clock, play } = useLoopClock(LOOP, TAP_AT, autoPlay);
  const [counted, setCounted] = useState(0);

  const cardWidth = width - MARGIN * 2;
  const centerY = height * 0.42;
  const smallY = insets.top + 76;
  const titleTop = smallY + CHECK_SMALL / 2 + 10;
  const cardTop = titleTop + 72;
  const punchY = HEAD_HEIGHT + AMOUNT_HEIGHT + RULE_HEIGHT / 2;
  const cardHeight = HEAD_HEIGHT + AMOUNT_HEIGHT + RULE_HEIGHT + rows.length * ROW_HEIGHT + RULE_HEIGHT + TOTAL_HEIGHT + 8;
  const barTop = cardTop + cardHeight + TEETH_HEIGHT + BAR_GAP;
  const toCard = height - cardTop;

  useAnimatedReaction(
    () => {
      const t = clock.get();
      return Math.round(amount * (reduced ? seg(t, COUNT_AT, COUNT_AT + REDUCED_FADE) : easeOut(seg(t, COUNT_AT, COUNT_AT + COUNT_FOR))));
    },
    (value, old) => {
      if (value !== old) scheduleOnRN(setCounted, value);
    },
  );

  const claim = () => {
    // Only a bar that is on screen can be peeled; the press goes to the peel itself.
    const t = clock.get();
    if (t < RECEIPT_AT + 0.6 || t >= PEEL_AT) return;
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    play(PEEL_AT, true);
    onClaim?.();
  };

  const checkStyle = useAnimatedStyle(() => {
    const t = clock.get();
    if (reduced) {
      return { opacity: seg(t, POP_AT, POP_AT + REDUCED_FADE), transform: [{ translateY: smallY - centerY }, { scale: CHECK_SMALL / CHECK_SIZE }] };
    }
    const pop = t < POP_AT ? 0 : spring(t - POP_AT, POP_STIFFNESS, POP_DAMPING);
    const move = easeInOut(seg(t, SHRINK_AT, SHRINK_AT + SHRINK_IN));
    const size = 1 - (1 - CHECK_SMALL / CHECK_SIZE) * move;
    return { opacity: t < POP_AT ? 0 : 1, transform: [{ translateY: (smallY - centerY) * move }, { scale: pop * size }] };
  });
  const rippleStyle = useAnimatedStyle(() => {
    const p = seg(clock.get(), POP_AT + 0.1, POP_AT + 0.1 + RIPPLE_FOR);
    return { opacity: reduced || p === 0 || p === 1 ? 0 : 0.5 * (1 - p), transform: [{ scale: 1 + easeOut(p) }] };
  });
  const titleStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), TITLE_AT, TITLE_AT + TITLE_IN));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 8 }] };
  });
  const subtitleStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), TITLE_AT + 0.06, TITLE_AT + 0.06 + TITLE_IN));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 8 }] };
  });
  // The tick draws itself on once the circle has popped.
  const tick = useDerivedValue(() => (reduced ? 1 : easeOut(seg(clock.get(), POP_AT + 0.12, POP_AT + 0.42))));
  const ripple2Style = useAnimatedStyle(() => {
    const p = seg(clock.get(), POP_AT + 0.25, POP_AT + 0.25 + RIPPLE_FOR * 1.3);
    return { opacity: reduced || p === 0 || p === 1 ? 0 : 0.35 * (1 - p), transform: [{ scale: 1 + 1.3 * easeOut(p) }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const t = clock.get();
    if (reduced) return { opacity: seg(t, RECEIPT_AT, RECEIPT_AT + REDUCED_FADE) };
    const into = t < RECEIPT_AT ? 0 : spring(t - RECEIPT_AT, RECEIPT_STIFFNESS, RECEIPT_DAMPING);
    return { opacity: t < RECEIPT_AT ? 0 : 1, transform: [{ translateY: (1 - into) * toCard }] };
  });
  // The whole screen rises and fades at the end.
  const stageStyle = useAnimatedStyle(() => {
    const out = easeInOut(seg(clock.get(), FADE_AT, FADE_AT + FADE_IN));
    return { opacity: 1 - out, transform: [{ translateY: reduced ? 0 : -RISE * out }] };
  });
  const sheenStyle = useAnimatedStyle(() => {
    const p = seg(clock.get(), SHEEN_AT, SHEEN_AT + SHEEN_FOR);
    return { opacity: reduced ? 0 : Math.sin(Math.PI * p), transform: [{ translateX: -90 + (cardWidth + 180) * p }, { skewX: '-22deg' }] };
  });
  const peelStyle = useAnimatedStyle(() => {
    const p = easeInOut(seg(clock.get(), PEEL_AT, PEEL_AT + PEEL_FOR));
    if (reduced) return { opacity: 1 - seg(clock.get(), PEEL_AT, PEEL_AT + REDUCED_FADE) };
    // A flap hinged on its top edge, swinging up and away; it is gone once edge-on.
    return { opacity: p >= 0.98 ? 0 : 1, transform: [{ perspective: 500 }, { rotateX: `${p * 88}deg` }] };
  });
  const shadowStyle = useAnimatedStyle(() => {
    const p = easeInOut(seg(clock.get(), PEEL_AT, PEEL_AT + PEEL_FOR));
    return { opacity: 0.28 * Math.sin(Math.PI * Math.min(1, p * 1.1)) };
  });
  return (
    <Pressable style={styles.screen} onPress={() => play()} accessibilityLabel={title}>
      <StatusBar style="dark" />
      <Animated.View style={[StyleSheet.absoluteFill, stageStyle]} pointerEvents="box-none">
        <View pointerEvents="none" style={[styles.checkSlot, { left: width / 2 - CHECK_SIZE / 2, top: centerY - CHECK_SIZE / 2 }]}>
          <Animated.View style={[styles.ripple, rippleStyle]} />
          <Animated.View style={[styles.ripple, styles.rippleSoft, ripple2Style]} />
          <Animated.View style={checkStyle}>
            <Canvas style={styles.check}>
              <Circle cx={CHECK_SIZE / 2} cy={CHECK_SIZE / 2 + 6} r={CHECK_SIZE / 2 - 14} color={GREEN} opacity={0.45}>
                <Blur blur={12} />
              </Circle>
              <Circle cx={CHECK_SIZE / 2} cy={CHECK_SIZE / 2} r={CHECK_SIZE / 2 - 16}>
                <RadialGradient c={vec(CHECK_SIZE / 2, 30)} r={CHECK_SIZE * 0.75} colors={[GREEN_LIGHT, GREEN, GREEN_DEEP]} positions={[0, 0.55, 1]} />
              </Circle>
              <Circle cx={CHECK_SIZE / 2} cy={CHECK_SIZE / 2} r={CHECK_SIZE / 2 - 16.75} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.35)" />
              <Oval x={CHECK_SIZE / 2 - 34} y={26} width={68} height={30} opacity={0.5}>
                <SkiaGradient start={vec(0, 26)} end={vec(0, 56)} colors={['rgba(255,255,255,0.75)', 'rgba(255,255,255,0)']} />
              </Oval>
              <Path path={CHECK_PATH} style="stroke" strokeWidth={10} strokeCap="round" strokeJoin="round" color="rgba(10,90,45,0.25)" start={0} end={tick} transform={[{ translateY: 2 }]} />
              <Path path={CHECK_PATH} style="stroke" strokeWidth={10} strokeCap="round" strokeJoin="round" color="#ffffff" start={0} end={tick} />
            </Canvas>
          </Animated.View>
        </View>

        <Animated.Text style={[styles.title, { top: titleTop }, titleStyle]}>{title}</Animated.Text>
        <Animated.Text style={[styles.subtitle, { top: titleTop + 38 }, subtitleStyle]}>{subtitle}</Animated.Text>

        <Animated.View style={[styles.card, { top: cardTop, left: MARGIN, width: cardWidth }, cardStyle]}>
          <Canvas pointerEvents="none" style={[styles.paper, { width: cardWidth + SHADOW_ROOM * 2, height: cardHeight + TEETH_HEIGHT + SHADOW_ROOM * 2 }]}>
            <Path path={paper(cardWidth, cardHeight, punchY)} color={PAPER}>
              <Shadow dx={0} dy={16} blur={22} color="rgba(17, 24, 39, 0.10)" />
              <Shadow dx={0} dy={1} blur={3} color="rgba(17, 24, 39, 0.08)" />
            </Path>
            <Line p1={vec(SHADOW_ROOM + PUNCH + 8, SHADOW_ROOM + punchY)} p2={vec(SHADOW_ROOM + cardWidth - PUNCH - 8, SHADOW_ROOM + punchY)} color={RULE} strokeWidth={1.5}>
              <DashPathEffect intervals={[6, 5]} />
            </Line>
          </Canvas>
          <View style={[styles.receipt, { height: cardHeight }]}>
            <View style={styles.head}>
              <View style={styles.headLeft}>
                <View style={styles.brand}>
                  <Ionicons name="checkmark" size={13} color="#ffffff" />
                </View>
                <Text style={styles.kicker}>RECEIPT</Text>
              </View>
              <Text style={styles.reference}>#{reference}</Text>
            </View>
            <View style={styles.amountBox}>
              <Text style={styles.amountLabel}>Amount paid</Text>
              <View style={styles.amountRow}>
                <Text style={styles.currency}>{currency}</Text>
                <Text style={styles.amount}>{money(counted)}</Text>
              </View>
            </View>
            <View style={{ height: RULE_HEIGHT }} />
            {rows.map((row, i) => (
              <ReceiptRow key={row.label} row={row} index={i} clock={clock} reduced={reduced} />
            ))}
            <View style={styles.ruleSlot}>
              <View style={styles.rule} />
            </View>
            <ReceiptRow row={{ label: 'Fee', value: fee }} index={rows.length} clock={clock} reduced={reduced} />
          </View>

          <View style={[styles.bars, { top: cardHeight + TEETH_HEIGHT + BAR_GAP }]}>
            <View style={styles.reward}>
              <LinearGradient colors={['#fbfbfc', '#e4e5e8', '#f5f5f7']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
              <LinearGradient colors={['#ffffff', '#c9cacf', '#8e9096']} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={styles.coinChip}>
                <Ionicons name="star" size={12} color="#ffffff" />
              </LinearGradient>
              <Text style={styles.rewardText}>{reward}</Text>
              <Animated.View style={[styles.flapShadow, shadowStyle]} />
            </View>
            <Animated.View style={[styles.peel, peelStyle, { transformOrigin: 'top' }]}>
              <Pressable style={StyleSheet.absoluteFill} onPress={claim} accessibilityRole="button" accessibilityLabel={claimLabel}>
                <LinearGradient
                  colors={['#f6f6f8', '#cfd0d5', '#eceef1', '#b4b6bc', '#dcdde1']}
                  locations={[0, 0.3, 0.5, 0.78, 1]}
                  start={{ x: 0, y: 0 }}
                  end={{ x: 1, y: 1 }}
                  style={StyleSheet.absoluteFill}
                />
                <View style={styles.claimWrap}>
                  <Ionicons name="gift-outline" size={18} color={SILVER_INK} />
                  <Text style={styles.claimText}>{claimLabel}</Text>
                </View>
                <Animated.View pointerEvents="none" style={[styles.sheen, sheenStyle]} />
              </Pressable>
            </Animated.View>
          </View>
        </Animated.View>

        <Coins clock={clock} at={COINS_AT} x={width / 2} y={barTop + BAR_HEIGHT / 2} />
      </Animated.View>

      <Confetti clock={clock} at={POP_AT} width={width} height={height} />
    </Pressable>
  );
}

function ReceiptRow({ row, index, clock, reduced }: { row: Row; index: number; clock: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const at = RECEIPT_AT + 0.5 + index * ROW_STAGGER;
    const p = easeOut(seg(clock.get(), at, at + 0.3));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 8 }] };
  });
  return (
    <Animated.View style={[styles.row, style]}>
      <Text style={styles.rowLabel}>{row.label}</Text>
      <Text style={styles.rowValue}>{row.value}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  checkSlot: { position: 'absolute', width: CHECK_SIZE, height: CHECK_SIZE },
  check: { width: CHECK_SIZE, height: CHECK_SIZE },
  ripple: { position: 'absolute', left: 16, top: 16, right: 16, bottom: 16, borderRadius: CHECK_SIZE / 2, borderWidth: 3, borderColor: GREEN },
  rippleSoft: { borderWidth: 0, backgroundColor: 'rgba(47,191,113,0.35)' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: INK, fontSize: 28, lineHeight: 34, fontWeight: '800', letterSpacing: -0.6 },
  subtitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: LABEL, fontSize: 16, lineHeight: 22, fontWeight: '500' },
  card: { position: 'absolute' },
  paper: { position: 'absolute', left: -SHADOW_ROOM, top: -SHADOW_ROOM },
  receipt: { paddingHorizontal: PAD },
  head: { height: HEAD_HEIGHT, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  headLeft: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  brand: { width: 22, height: 22, borderRadius: 11, backgroundColor: GREEN, alignItems: 'center', justifyContent: 'center' },
  kicker: { color: VALUE, fontSize: 12, lineHeight: 16, fontWeight: '800', letterSpacing: 1.6 },
  reference: { color: LABEL, fontSize: 12, lineHeight: 16, fontFamily: MONO },
  amountBox: { height: AMOUNT_HEIGHT, alignItems: 'center', justifyContent: 'center' },
  amountLabel: { color: LABEL, fontSize: 13, lineHeight: 18, fontWeight: '600', letterSpacing: 0.2 },
  amountRow: { marginTop: 2, flexDirection: 'row', alignItems: 'flex-start' },
  currency: { marginTop: 7, marginRight: 3, color: LABEL, fontSize: 20, lineHeight: 24, fontWeight: '700' },
  amount: { color: INK, fontSize: 46, lineHeight: 52, fontWeight: '800', letterSpacing: -1.4, fontVariant: ['tabular-nums'] },
  row: { height: ROW_HEIGHT, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rowLabel: { color: LABEL, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  rowValue: { color: VALUE, fontSize: 15, lineHeight: 20, fontWeight: '600', fontVariant: ['tabular-nums'] },
  ruleSlot: { height: RULE_HEIGHT, justifyContent: 'center' },
  rule: { height: 1, backgroundColor: RULE },
  bars: { position: 'absolute', left: 0, right: 0, height: BAR_HEIGHT },
  reward: {
    height: BAR_HEIGHT,
    borderRadius: 16,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: '#dcdde1',
  },
  coinChip: { width: 26, height: 26, borderRadius: 13, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#9fa1a7' },
  rewardText: { color: REWARD_INK, fontSize: 19, lineHeight: 24, fontWeight: '800', letterSpacing: -0.3 },
  flapShadow: { position: 'absolute', left: 0, right: 0, top: 0, height: 18, backgroundColor: '#000000' },
  peel: { position: 'absolute', left: 0, right: 0, top: 0, height: BAR_HEIGHT, borderRadius: 16, overflow: 'hidden', boxShadow: '0 4px 10px rgba(17, 24, 39, 0.12)' },
  claimWrap: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center' },
  claimText: { color: SILVER_INK, fontSize: 16, lineHeight: 20, fontWeight: '600' },
  sheen: { position: 'absolute', top: -10, bottom: -10, width: 54, backgroundColor: 'rgba(255,255,255,0.75)' },
});
