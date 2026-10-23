/** The source's white account card. */
import { StyleSheet, Text, View } from 'react-native';
import type { SharedValue } from 'react-native-reanimated';

import { RollingAmount } from './RollingAmount';

import { COLOR, FLAT, LAYOUT, TYPE } from './theme';

export function BalanceCard({ balance, reduceMotion }: { balance: SharedValue<number>; reduceMotion: boolean }) {
  return (
    <View style={styles.card} accessible accessibilityLabel="Bank account balance">
      <View style={styles.badge}>
        <View style={styles.ring} />
      </View>
      <View>
        <Text style={styles.label}>Bank account</Text>
        <View style={styles.amountRow}>
          <RollingAmount value={balance} style={styles.balance} reduceMotion={reduceMotion} />
          <Text style={styles.currency}> $</Text>
        </View>
      </View>
    </View>
  );
}

const RING = 24;

const styles = StyleSheet.create({
  card: {
    height: FLAT.card.height,
    borderRadius: FLAT.card.radius,
    backgroundColor: COLOR.white,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    gap: 16,
    shadowColor: COLOR.slabShadow,
    shadowOpacity: 0.18,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 12 },
  },
  badge: { width: LAYOUT.cardBadge, height: LAYOUT.cardBadge, borderRadius: LAYOUT.cardBadgeRadius, backgroundColor: COLOR.plum, alignItems: 'center', justifyContent: 'center' },
  ring: { width: RING, height: RING, borderRadius: RING / 2, borderWidth: 4, borderColor: COLOR.pink },
  label: { ...TYPE.cardLabel, color: COLOR.cardLabel },
  amountRow: { flexDirection: 'row', alignItems: 'baseline' },
  balance: { ...TYPE.balance, color: COLOR.plumInk, fontVariant: ['tabular-nums'] },
  currency: { ...TYPE.currency, color: COLOR.cardCurrency },
});
