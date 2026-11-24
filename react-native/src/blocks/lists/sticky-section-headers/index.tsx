import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { Extrapolation, interpolate, useAnimatedStyle, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BG, HAIR, INCOME, INK, MUTE, RAIL_W, SIDE } from './LedgerRows';
import { MONTHS, MONTH_NAMES, SECTIONS, daySpent, formatMoney } from './ledgerData';
import { StickySectionList } from './StickySectionList';

const TOP_BAR_H = 44;
const LIST_TOP_H = 212;

/** November's spend so far, and seeded bar heights for the summary (no randomness). */
const NOV_SPENT = SECTIONS.filter((s) => s.month === 0).reduce((a, s) => a + daySpent(s), 0);
const BARS = Array.from({ length: 18 }, (_, i) => 0.18 + 0.82 * Math.abs(Math.sin(i * 1.71 + 0.4) * Math.cos(i * 0.53)));

function Summary({ width }: { width: number }) {
  return (
    <View style={styles.top}>
      <Text style={styles.large}>Activity</Text>
      <View style={[styles.card, { width: width - SIDE * 2 }]}>
        <LinearGradient colors={['#fffefb', '#fbfaf6', '#f6f4ef']} locations={[0, 0.6, 1]} style={[StyleSheet.absoluteFill, { borderRadius: 20 }]} />
        <View style={styles.cardEdge} />
        <View style={{ flex: 1 }}>
          <Text style={styles.cardLabel}>Spent in November</Text>
          <Text style={styles.cardValue}>{formatMoney(NOV_SPENT, false)}</Text>
          <Text style={styles.cardNote}>
            <Text style={{ color: INCOME, fontWeight: '600' }}>18% less</Text> than October
          </Text>
        </View>
        <View style={styles.bars}>
          {BARS.map((h, i) => (
            <View key={i} style={[styles.bar, { height: 8 + h * 46, backgroundColor: i === BARS.length - 1 ? INK : 'rgba(28,27,25,0.16)' }]} />
          ))}
        </View>
      </View>
    </View>
  );
}

function Footer() {
  return (
    <View style={styles.footer}>
      <Text style={styles.footTitle}>That’s everything since June</Text>
      <Text style={styles.footSub}>Older activity lives in Statements.</Text>
    </View>
  );
}

/** Ledger's activity history: day sections whose headers stick, get pushed, and shrink into a pill with a running total. */
export default function StickySectionHeadersDemo() {
  const insets = useSafeAreaInsets();
  const { width: W } = useWindowDimensions();
  const reduced = useReducedMotion();
  const scrollY = useSharedValue(0);

  const titleStyle = useAnimatedStyle(() => ({
    opacity: interpolate(scrollY.get(), [36, 64], [0, 1], Extrapolation.CLAMP),
    transform: [{ translateY: interpolate(scrollY.get(), [36, 64], [6, 0], Extrapolation.CLAMP) }],
  }));

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <View style={[styles.topBar, { paddingTop: insets.top, height: insets.top + TOP_BAR_H }]}>
        <Text style={styles.back}>Accounts</Text>
        <Animated.Text style={[styles.barTitle, titleStyle]}>Activity</Animated.Text>
        <Text style={styles.action}>Export</Text>
      </View>
      <StickySectionList
        sections={SECTIONS}
        months={MONTHS}
        monthNames={MONTH_NAMES}
        listTop={<Summary width={W} />}
        listTopHeight={LIST_TOP_H}
        footer={<Footer />}
        width={W}
        autoPlay
        reducedMotion={reduced}
        bottomInset={insets.bottom}
        scrollY={scrollY}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BG },
  topBar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: SIDE, backgroundColor: BG, zIndex: 2 },
  back: { flex: 1, fontSize: 17, color: INK, fontWeight: '500' },
  barTitle: { fontSize: 17, fontWeight: '600', color: INK },
  action: { flex: 1, textAlign: 'right', fontSize: 17, color: INK, fontWeight: '500' },
  top: { height: LIST_TOP_H, paddingTop: 2 },
  large: { marginLeft: SIDE, fontSize: 34, lineHeight: 41, fontWeight: '800', color: INK, letterSpacing: -0.6 },
  card: {
    marginLeft: SIDE,
    marginTop: 12,
    height: 136,
    borderRadius: 20,
    padding: 18,
    flexDirection: 'row',
    alignItems: 'flex-end',
    backgroundColor: '#fbfaf6',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIR,
    shadowColor: '#2a2218',
    shadowOpacity: 0.1,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 10 },
    elevation: 4,
  },
  cardEdge: { position: 'absolute', left: 16, right: 16, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.95)' },
  cardLabel: { fontSize: 13, color: MUTE, fontWeight: '500' },
  cardValue: { fontSize: 32, lineHeight: 38, fontWeight: '700', color: INK, letterSpacing: -0.6, marginTop: 4, fontVariant: ['tabular-nums'] },
  cardNote: { fontSize: 13, color: MUTE, marginTop: 6 },
  bars: { flexDirection: 'row', alignItems: 'flex-end', gap: 3, height: 56, marginLeft: 12 },
  bar: { width: 4, borderRadius: 2 },
  footer: { paddingTop: 36, alignItems: 'center', paddingRight: RAIL_W - SIDE },
  footTitle: { fontSize: 15, fontWeight: '600', color: INK },
  footSub: { fontSize: 13, color: MUTE, marginTop: 4 },
});
