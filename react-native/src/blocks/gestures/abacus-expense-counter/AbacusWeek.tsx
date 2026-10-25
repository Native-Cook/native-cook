import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, withSpring } from 'react-native-reanimated';

import { RollingAmount } from './AbacusRolling';

/*
 The week strip under the abacus: one bar per day. Monday to Friday are spent and fixed (they add
 up to the abacus' resting total, $386.40); Saturday is today and grows with whatever the beads
 log beyond that (a spring on scaleY, from the bottom); Sunday is still to come (an outline).
 */

/** Cents spent Monday to Friday; their sum is the abacus' resting total. */
const PAST = [6210, 4830, 9140, 5500, 12960];
const DAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const SCALE = 14000;
const BAR_H = 56;
const INK = '#f4ece0';
const INK_SOFT = '#ad9e8e';

type Props = { total: SharedValue<number>; base: number; color: string; reduced: boolean };

export function AbacusWeek({ total, base, color, reduced }: Props) {
  const today = useDerivedValue(() => Math.max(0, total.get() - base));
  const bar = useAnimatedStyle(() => {
    const v = Math.max(0.02, Math.min(1, today.get() / SCALE));
    return { transform: [{ scaleY: reduced ? v : withSpring(v, { duration: 600, dampingRatio: 0.75 }) }] };
  });
  return (
    <View style={styles.card}>
      <View style={styles.head}>
        <Text style={styles.title}>This week</Text>
        <View style={styles.today}>
          <Text style={styles.todayLabel}>Today</Text>
          <RollingAmount cents={today} initial={0} size={15} weight="600" color={INK} reduced={reduced} />
        </View>
      </View>
      <View style={styles.bars}>
        {DAYS.map((d, i) => (
          <View key={i} style={styles.col}>
            <View style={[styles.slot, i === 6 && styles.future]}>
              {i < 5 ? <View style={[styles.past, { height: Math.round((PAST[i] / SCALE) * BAR_H) }]} /> : null}
              {i === 5 ? <Animated.View style={[styles.now, { backgroundColor: color }, bar]} /> : null}
            </View>
            <Text style={[styles.day, i === 5 && { color }]}>{d}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

export const WEEK_H = 132;

const styles = StyleSheet.create({
  card: {
    height: WEEK_H,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 10,
    gap: 10,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(244,236,224,0.05)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(244,236,224,0.1)',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05), 0 8px 24px rgba(0,0,0,0.25)',
  },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 18 },
  title: { color: INK, fontSize: 14, fontWeight: '600' },
  today: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  todayLabel: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  bars: { flexDirection: 'row', gap: 10, flex: 1 },
  col: { flex: 1, alignItems: 'center', gap: 4 },
  slot: { width: '100%', height: BAR_H, justifyContent: 'flex-end', borderRadius: 6, overflow: 'hidden', backgroundColor: 'rgba(244,236,224,0.04)' },
  future: { borderWidth: 1, borderStyle: 'dashed', borderColor: 'rgba(244,236,224,0.18)', backgroundColor: 'transparent' },
  past: { width: '100%', borderRadius: 6, backgroundColor: 'rgba(244,236,224,0.22)' },
  now: { width: '100%', height: BAR_H, borderRadius: 6, transformOrigin: 'bottom center' },
  day: { color: INK_SOFT, fontSize: 11, fontWeight: '600' },
});
