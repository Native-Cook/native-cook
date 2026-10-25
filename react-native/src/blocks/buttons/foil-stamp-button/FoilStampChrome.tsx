import { Text, View } from 'react-native';

import { styles } from './foilStampStyles';

/** The order's lines (they add up to the total). */
const ROWS: [string, string][] = [
  ['Cotton card, 600 gsm × 120', '$216.00'],
  ['Champagne foil seal', '$132.00'],
  ['Hand-deckled envelopes', '$36.00'],
];

export function OrderHeader({ top }: { top: number }) {
  return (
    <View pointerEvents="none" style={[styles.header, { top }]}>
      <Text style={styles.kicker}>PAPER & PRESS</Text>
      <Text style={styles.title}>Order #2047</Text>
      <Text style={styles.sub}>Wedding suite · ships in 6 days</Text>
    </View>
  );
}

export function OrderRows({ top, left, width }: { top: number; left: number; width: number }) {
  return (
    <View pointerEvents="none" style={[styles.rows, { top, left, width }]}>
      {ROWS.map(([name, price]) => (
        <View key={name} style={styles.row}>
          <Text style={styles.rowName}>{name}</Text>
          <Text style={styles.rowPrice}>{price}</Text>
        </View>
      ))}
      <View style={styles.rule} />
      <View style={styles.row}>
        <Text style={styles.totalName}>Total</Text>
        <Text style={styles.totalPrice}>$384.00</Text>
      </View>
    </View>
  );
}
