/** The source's Overview tab (f005): bank cards side by side, each with a plum badge over its top-left corner. */
import { StyleSheet, Text, View } from 'react-native';
import type { SharedValue } from 'react-native-reanimated';

import { RollingAmount } from './RollingAmount';

import { COLOR, FONT, FLAT, TYPE } from './theme';

// The middle of a carousel: one card to the left, one to the right.
const CARDS = [-1, 0, 1];

export function CardsView({ balance, reduceMotion }: { balance: SharedValue<number>; reduceMotion: boolean }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      {CARDS.map((i) => (
        <View key={i} style={[styles.wrap, { left: FLAT.cards.left + i * FLAT.cards.pitch }]} accessible accessibilityLabel="Bank account balance">
          <View style={styles.card}>
            <Text style={styles.label}>Bank account</Text>
            <View style={styles.amountRow}>
              <RollingAmount value={balance} style={styles.balance} reduceMotion={reduceMotion} />
              <Text style={styles.currency}> $</Text>
            </View>
          </View>
          <View style={styles.badge}><View style={styles.ring} /></View>
        </View>
      ))}
    </View>
  );
}

const C = FLAT.cards;

const styles = StyleSheet.create({
  wrap: { position: 'absolute', top: C.badgeTop, width: C.width, height: C.top + C.height - C.badgeTop },
  card: {
    position: 'absolute', left: 0, top: C.top - C.badgeTop, width: C.width, height: C.height, borderRadius: C.radius,
    backgroundColor: COLOR.white, justifyContent: 'flex-end', paddingLeft: 30, paddingBottom: 31, // measured on the source's Overview (rectified)
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.2, shadowRadius: 20, shadowOffset: { width: 0, height: 12 },
  },
  label: { ...TYPE.cardLabel, fontSize: 15, color: COLOR.cardLabel, fontFamily: FONT.medium },
  amountRow: { flexDirection: 'row', alignItems: 'baseline', marginLeft: 2.4 }, // the source's digits start just inside the label
  balance: { ...TYPE.balance, fontSize: 36, lineHeight: 44, color: COLOR.cardInk },
  currency: { ...TYPE.currency, fontSize: 34, color: COLOR.cardCurrency },
  badge: {
    position: 'absolute', left: C.badgeLeft, top: 0, width: C.badge, height: C.badge, borderRadius: 22,
    backgroundColor: COLOR.plum, alignItems: 'center', justifyContent: 'center',
  },
  ring: { width: 48, height: 48, borderRadius: 24, borderWidth: 5, borderColor: COLOR.pink, borderTopColor: 'rgba(242,98,147,0.35)' },
});
