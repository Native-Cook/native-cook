// Penny's home screen under the tour: header, the month card, recent spends, the add button and the tab bar.
// Everything sits at positions from pennyLayout(), so the tour's cutouts land exactly on the real elements.
import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { BUDGET, CATEGORIES, RECENT, TABS, type Spend } from './data';
import type { Box } from './motion';

export const PAPER = ['#F8F5F0', '#F3EFE8', '#ECE7DE'] as const;
export const CARD = '#FFFDF9';
export const INK = '#1E2420';
export const MUTE = '#6E746F';
export const GREEN = '#2F7D5B';

const SIDE = 16;
const BRAND_H = 28;
const CARD_H = 164;
const ROW_H = 58;
const TAB_H = 50;
const FAB = 58;

export type PennyLayout = {
  top: number;
  card: Box;
  fab: Box;
  /** The Insights tab item (icon and label). */
  tab: Box;
  tabTop: number;
  tabBottom: number;
  recentTop: number;
  rows: number;
};

export function pennyLayout(width: number, height: number, insetTop: number, insetBottom: number): PennyLayout {
  const top = insetTop + 8;
  const card = { x: SIDE, y: top + 146, w: width - SIDE * 2, h: CARD_H, r: 22 };
  const tabBottom = Math.max(insetBottom, 8);
  const tabTop = height - tabBottom - TAB_H;
  const fab = { x: width - 20 - FAB, y: tabTop - 18 - FAB, w: FAB, h: FAB, r: FAB / 2 };
  const tabW = width / TABS.length;
  const tab = { x: tabW * 2 + (tabW - 80) / 2, y: tabTop + 3, w: 80, h: 44, r: 14 };
  const recentTop = card.y + card.h + 22;
  const rows = Math.max(2, Math.min(RECENT.length, Math.floor((tabTop - recentTop - 30) / ROW_H)));
  return { top, card, fab, tab, tabTop, tabBottom, recentTop, rows };
}

function money(n: number) {
  return `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function PennyMark() {
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24">
      <Circle cx={12} cy={12} r={11} fill={GREEN} />
      <Circle cx={12} cy={12} r={7.6} fill="none" stroke="#9ED2B6" strokeWidth={1.2} />
      <Path d="M10 16.5 V7.8 H13.1 A2.6 2.6 0 0 1 13.1 13 H10" fill="none" stroke="#F4FBF7" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function SpendGlyph({ kind, color }: { kind: Spend['glyph']; color: string }) {
  const s = { stroke: color, strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      {kind === 'cart' ? (
        <>
          <Path d="M2.5 3.5 H4.6 L6.4 12.6 H15.3 L16.9 6.2 H5.3" {...s} />
          <Circle cx={7.4} cy={16} r={1.2} {...s} />
          <Circle cx={14.2} cy={16} r={1.2} {...s} />
        </>
      ) : null}
      {kind === 'cup' ? <Path d="M4 7 H14 V11.5 A4 4 0 0 1 10 15.5 H8 A4 4 0 0 1 4 11.5 Z M14 8.4 H15.4 A2 2 0 0 1 15.4 12.4 H14 M6.5 2.8 V4.6 M9.5 2.8 V4.6" {...s} /> : null}
      {kind === 'train' ? (
        <>
          <Rect x={4.5} y={2.8} width={11} height={11.5} rx={3} {...s} />
          <Path d="M4.8 9.2 H15.2 M7 17.2 L8.2 14.4 M13 17.2 L11.8 14.4" {...s} />
        </>
      ) : null}
      {kind === 'leaf' ? <Path d="M4 16 C4 9 8.5 4.5 16 4 C15.6 11.5 11 16 4 16 Z M4 16 L10.5 9.5" {...s} /> : null}
    </Svg>
  );
}

type TabKind = (typeof TABS)[number];

function TabGlyph({ kind, color }: { kind: TabKind; color: string }) {
  const s = { stroke: color, strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={26} height={24} viewBox="0 0 26 24">
      {kind === 'Home' ? <Path d="M4.5 11 L13 4 L21.5 11 V19 A1.5 1.5 0 0 1 20 20.5 H15.5 V14.5 H10.5 V20.5 H6 A1.5 1.5 0 0 1 4.5 19 Z" {...s} /> : null}
      {kind === 'Budgets' ? (
        <>
          <Rect x={4} y={5} width={18} height={14.5} rx={3.2} {...s} />
          <Path d="M4 9.5 H22 M16.5 14.5 H18.5" {...s} />
        </>
      ) : null}
      {kind === 'Insights' ? <Path d="M5 20 V13 M10.3 20 V8.5 M15.6 20 V11.5 M20.9 20 V4.5" {...s} /> : null}
      {kind === 'Profile' ? (
        <>
          <Circle cx={13} cy={8.5} r={4.2} {...s} />
          <Path d="M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5" {...s} />
        </>
      ) : null}
    </Svg>
  );
}

function MonthCard({ box }: { box: Box }) {
  const spent = CATEGORIES.reduce((sum, c) => sum + c.spent, 0);
  const share = Math.round((spent / BUDGET) * 100);
  const barW = box.w - 32 - (CATEGORIES.length - 1) * 2;
  return (
    <View style={[styles.card, { left: box.x, top: box.y, width: box.w, height: box.h }]} accessible accessibilityLabel={`Spent this month ${money(spent)}, ${share} percent of budget`}>
      <View style={styles.cardTop}>
        <Text style={styles.cardLabel}>Spent in November</Text>
        <View style={styles.share}>
          <Text style={styles.shareText}>{`${share}%`}</Text>
        </View>
      </View>
      <Text style={styles.cardAmount}>{money(spent)}</Text>
      <View style={styles.bar}>
        {CATEGORIES.map((c, i) => (
          <View
            key={c.name}
            style={[styles.barPart, { width: (barW * c.spent) / BUDGET, backgroundColor: c.color, marginLeft: i === 0 ? 0 : 2 }]}
          />
        ))}
        <View style={styles.barRest} />
      </View>
      <View style={styles.legend}>
        {CATEGORIES.map(c => (
          <View key={c.name} style={styles.legendItem}>
            <View style={[styles.dot, { backgroundColor: c.color }]} />
            <Text style={styles.legendName} numberOfLines={1}>{c.name}</Text>
            <Text style={styles.legendAmount}>{money(c.spent)}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function SpendRow({ spend }: { spend: Spend }) {
  return (
    <View style={styles.row} accessible accessibilityLabel={`${spend.place}, ${money(spend.amount)}, ${spend.time}`}>
      <View style={[styles.rowIcon, { backgroundColor: `${spend.color}1F` }]}>
        <SpendGlyph kind={spend.glyph} color={spend.color} />
      </View>
      <View style={styles.rowText}>
        <Text style={styles.rowPlace} numberOfLines={1}>{spend.place}</Text>
        <Text style={styles.rowTime} numberOfLines={1}>{`${spend.category} · ${spend.time}`}</Text>
      </View>
      <Text style={styles.rowAmount}>{`−${money(spend.amount)}`}</Text>
    </View>
  );
}

export type PennyHomeProps = {
  layout: PennyLayout;
  width: number;
  onAdd?: () => void;
};

/** The app screen. Static: the tour moves over it. */
export const PennyHome = memo(function PennyHome({ layout, width, onAdd }: PennyHomeProps) {
  const left = BUDGET - CATEGORIES.reduce((sum, c) => sum + c.spent, 0);
  const { top, card, fab, tabTop, tabBottom, recentTop, rows } = layout;
  return (
    <View style={StyleSheet.absoluteFill}>
      <LinearGradient colors={PAPER} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />

      <View style={[styles.header, { top, width: width - SIDE * 2 }]}>
        <View style={styles.brandRow}>
          <PennyMark />
          <Text style={styles.brand}>Penny</Text>
          <View style={styles.month}>
            <Text style={styles.monthText}>November</Text>
            <Svg width={10} height={10} viewBox="0 0 10 10">
              <Path d="M2 3.8 L5 6.8 L8 3.8" fill="none" stroke={INK} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
          </View>
        </View>
        <Text style={styles.greeting}>Good evening, Sam</Text>
        <View style={styles.leftRow}>
          <Text style={styles.leftAmount}>{money(left)}</Text>
          <Text style={styles.leftLabel}>left</Text>
        </View>
        <Text style={styles.sub}>{`of ${money(BUDGET)} · 6 days to payday`}</Text>
      </View>

      <MonthCard box={card} />

      <Text style={[styles.section, { top: recentTop, left: SIDE }]}>Recent</Text>
      <View style={[styles.rows, { top: recentTop + 30, width: width - SIDE * 2 }]}>
        {RECENT.slice(0, rows).map(spend => (
          <SpendRow key={spend.place} spend={spend} />
        ))}
      </View>

      <Pressable
        onPress={onAdd}
        style={[styles.fabWrap, { left: fab.x, top: fab.y }]}
        accessibilityRole="button"
        accessibilityLabel="Add a spend"
      >
        <LinearGradient colors={['#4FA27C', GREEN, '#22604A']} locations={[0, 0.5, 1]} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={styles.fab}>
          <Svg width={24} height={24} viewBox="0 0 24 24">
            <Path d="M12 5 V19 M5 12 H19" stroke="#F4FBF7" strokeWidth={2.4} strokeLinecap="round" />
          </Svg>
        </LinearGradient>
      </Pressable>

      <View style={[styles.tabBar, { top: tabTop, height: TAB_H + tabBottom, paddingBottom: tabBottom }]}>
        {TABS.map((tab, i) => (
          <View key={tab} style={styles.tab} accessible accessibilityRole="tab" accessibilityState={{ selected: i === 0 }}>
            <TabGlyph kind={tab} color={i === 0 ? GREEN : '#8C918C'} />
            <Text style={[styles.tabLabel, i === 0 && styles.tabOn]}>{tab}</Text>
          </View>
        ))}
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  header: { position: 'absolute', left: SIDE },
  brandRow: { height: BRAND_H, flexDirection: 'row', alignItems: 'center' },
  brand: { marginLeft: 8, fontSize: 17, fontWeight: '700', color: INK },
  month: {
    marginLeft: 'auto',
    height: 30,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 12,
    borderRadius: 15,
    backgroundColor: 'rgba(30,36,32,0.06)',
  },
  monthText: { fontSize: 14, fontWeight: '600', color: INK },
  greeting: { marginTop: 12, height: 20, fontSize: 15, lineHeight: 20, color: MUTE },
  leftRow: { marginTop: 2, height: 48, flexDirection: 'row', alignItems: 'baseline' },
  leftAmount: { fontSize: 40, lineHeight: 48, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'], letterSpacing: -0.5 },
  leftLabel: { marginLeft: 8, fontSize: 20, fontWeight: '500', color: MUTE },
  sub: { marginTop: 2, height: 18, fontSize: 14, lineHeight: 18, color: MUTE },
  card: {
    position: 'absolute',
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    padding: 16,
    boxShadow: '0 1px 2px rgba(52,44,30,0.08), 0 12px 28px rgba(52,44,30,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  cardTop: { height: 18, flexDirection: 'row', alignItems: 'center' },
  cardLabel: { fontSize: 14, fontWeight: '500', color: MUTE },
  share: { marginLeft: 'auto', height: 22, paddingHorizontal: 8, borderRadius: 11, backgroundColor: 'rgba(47,125,91,0.12)', justifyContent: 'center' },
  shareText: { fontSize: 12.5, fontWeight: '700', color: GREEN, fontVariant: ['tabular-nums'] },
  cardAmount: { marginTop: 2, height: 34, fontSize: 28, lineHeight: 34, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'] },
  bar: { marginTop: 12, height: 10, flexDirection: 'row', borderRadius: 5, overflow: 'hidden', backgroundColor: 'rgba(30,36,32,0.07)' },
  barPart: { height: 10 },
  barRest: { flex: 1 },
  legend: { marginTop: 14, flexDirection: 'row', flexWrap: 'wrap', rowGap: 4 },
  legendItem: { width: '50%', height: 20, flexDirection: 'row', alignItems: 'center', paddingRight: 10 },
  dot: { width: 8, height: 8, borderRadius: 4, marginRight: 7 },
  legendName: { flex: 1, fontSize: 13, color: INK },
  legendAmount: { fontSize: 13, color: MUTE, fontVariant: ['tabular-nums'] },
  section: { position: 'absolute', height: 20, fontSize: 17, lineHeight: 20, fontWeight: '700', color: INK },
  rows: { position: 'absolute', left: SIDE },
  row: { height: ROW_H, flexDirection: 'row', alignItems: 'center' },
  rowIcon: { width: 40, height: 40, borderRadius: 12, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, marginLeft: 12, minWidth: 0 },
  rowPlace: { fontSize: 16, fontWeight: '600', color: INK },
  rowTime: { marginTop: 1, fontSize: 13, color: MUTE },
  rowAmount: { marginLeft: 10, fontSize: 16, fontWeight: '600', color: INK, fontVariant: ['tabular-nums'] },
  fabWrap: {
    position: 'absolute',
    width: FAB,
    height: FAB,
    borderRadius: FAB / 2,
    boxShadow: '0 2px 4px rgba(20,60,42,0.22), 0 12px 26px rgba(20,60,42,0.2)',
  },
  fab: {
    flex: 1,
    borderRadius: FAB / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.3)',
  },
  tabBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    backgroundColor: 'rgba(251,249,245,0.96)',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(30,36,32,0.14)',
  },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 6 },
  tabLabel: { marginTop: 2, fontSize: 10.5, fontWeight: '500', color: '#8C918C' },
  tabOn: { color: GREEN, fontWeight: '600' },
});
