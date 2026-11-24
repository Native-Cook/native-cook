import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { type Item, type Txn, formatMoney } from './ledgerData';
import { HEADER_H, ROW_H } from './stickyMotion';

/** Ledger palette: a cool paper, warm ink, one green for money in. */
export const BG = '#f3f2ee';
export const INK = '#1c1b19';
export const MUTE = '#77736b';
export const INCOME = '#2c8650';
export const HAIR = 'rgba(40,34,26,0.12)';

/** Room on the right for the month index. */
export const RAIL_W = 40;
export const SIDE = 20;

/** Header text positions, shared by the in-list header and the sticky copy so they line up to the point. */
export const LBL_Y = 8;
export const LBL_LH = 22;
export const LBL_FS = 17;
export const SUB_Y = 31;
export const SUB_LH = 17;
export const TOT_Y = 18;
export const TOT_LH = 20;

export const SectionHeaderRow = memo(function SectionHeaderRow({ label, count, spent }: { label: string; count: number; spent: number }) {
  return (
    <View style={styles.header}>
      <Text style={[styles.label, { top: LBL_Y }]} numberOfLines={1}>
        {label}
      </Text>
      <Text style={[styles.sub, { top: SUB_Y }]}>{count === 1 ? '1 payment' : `${count} payments`}</Text>
      <Text style={[styles.total, { top: TOT_Y }]}>{formatMoney(-spent, true)}</Text>
    </View>
  );
});

export const TransactionRow = memo(function TransactionRow({ txn, last }: { txn: Txn; last: boolean }) {
  const income = txn.cents > 0;
  return (
    <View style={styles.row}>
      <View style={styles.disc}>
        <LinearGradient colors={[txn.tint[0], txn.tint[1], txn.tint[1]]} locations={[0, 0.7, 1]} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={StyleSheet.absoluteFill} />
        <View style={styles.discEdge} />
        <Text style={styles.initial}>{txn.initial}</Text>
      </View>
      <View style={styles.rowText}>
        <Text style={styles.name} numberOfLines={1}>
          {txn.name}
        </Text>
        <Text style={styles.meta} numberOfLines={1}>
          {txn.meta}
        </Text>
      </View>
      <Text style={[styles.amount, income && { color: INCOME }]}>{formatMoney(txn.cents, true)}</Text>
      {last ? null : <View style={styles.sep} />}
    </View>
  );
});

/** FlashList callbacks at module scope: stable, so recycled rows never re-render for a new function. */
export function renderLedgerItem({ item }: { item: Item }) {
  if (item.type === 'header') return <SectionHeaderRow label={item.label} count={item.count} spent={item.spent} />;
  return <TransactionRow txn={item.txn} last={item.last} />;
}

export function ledgerKey(item: Item) {
  return item.id;
}

export function ledgerType(item: Item) {
  return item.type;
}

const styles = StyleSheet.create({
  header: { height: HEADER_H, backgroundColor: BG },
  label: { position: 'absolute', left: SIDE, right: RAIL_W + 110, fontSize: LBL_FS, lineHeight: LBL_LH, fontWeight: '700', color: INK, letterSpacing: -0.2 },
  sub: { position: 'absolute', left: SIDE, fontSize: 13, lineHeight: SUB_LH, color: MUTE, fontVariant: ['tabular-nums'] },
  total: { position: 'absolute', right: RAIL_W + 4, fontSize: 15, lineHeight: TOT_LH, fontWeight: '600', color: MUTE, fontVariant: ['tabular-nums'] },
  row: { height: ROW_H, flexDirection: 'row', alignItems: 'center', paddingLeft: SIDE, paddingRight: RAIL_W + 4 },
  disc: { width: 40, height: 40, borderRadius: 20, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  discEdge: { position: 'absolute', left: 6, right: 6, top: 1, height: 1, backgroundColor: 'rgba(255,255,255,0.45)', borderRadius: 1 },
  initial: { color: '#fffdf8', fontSize: 16, fontWeight: '700' },
  rowText: { flex: 1, marginLeft: 12, marginRight: 8 },
  name: { fontSize: 16, fontWeight: '600', color: INK, letterSpacing: -0.1 },
  meta: { fontSize: 13, color: MUTE, marginTop: 2 },
  amount: { fontSize: 16, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  sep: { position: 'absolute', left: SIDE + 52, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIR },
});
