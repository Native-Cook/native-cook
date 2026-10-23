/** The card face: brand, rolling balance, number, three actions. Pure view; the parent moves it. */
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { SEND } from './motion';
import { COLORS, LAYOUT, TYPE } from './theme';

export function formatMoney(cents: number) {
  const sign = cents < 0 ? '−' : '';
  const v = Math.abs(cents) / 100;
  return `${sign}$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const DIGIT_H = 38;

function Digit({ value, index }: { value: number; index: number }) {
  const pos = useSharedValue(value);
  useEffect(() => {
    // Columns further right start a little later, like a mechanical counter settling.
    pos.set(withDelay(index * 40, withTiming(value, { duration: SEND.rollMs, easing: Easing.out(Easing.cubic) })));
  }, [index, pos, value]);
  const style = useAnimatedStyle(() => ({ transform: [{ translateY: -pos.get() * DIGIT_H }] }));
  return (
    <View style={styles.digitWindow}>
      <Animated.View style={style}>
        {Array.from({ length: 10 }, (_, d) => <Text key={d} style={styles.balance}>{d}</Text>)}
      </Animated.View>
    </View>
  );
}

export function Odometer({ cents }: { cents: number }) {
  const text = formatMoney(cents);
  let digit = 0;
  return (
    <View style={styles.odometer} accessible accessibilityLabel={`Balance ${text}`}>
      {text.split('').map((ch, i) => (/\d/.test(ch)
        ? <Digit key={`d${i}`} value={Number(ch)} index={digit++} />
        : <Text key={`c${i}`} style={styles.balance}>{ch === '$' ? '$ ' : ch}</Text>))}
    </View>
  );
}

export function ArrowIcon({ kind, color, size = 18 }: { kind: 'out' | 'in' | 'add' | 'open' | 'rowOut' | 'rowIn'; color: string; size?: number }) {
  // The transaction rows' glyphs stand on a base line, as in the source.
  const d = kind === 'rowOut' ? 'M14 12 L5 3 M5 9.5 V3 H11.5 M4 16.5 H16'
    : kind === 'rowIn' ? 'M14 3 L5 12 M5 5.5 V12 H11.5 M4 16.5 H16'
    : kind === 'out' ? 'M15 15 L5 5 M5 12 V5 H12'
    : kind === 'in' ? 'M14 4 L5 13 M5 6 V13 H12'
      : kind === 'open' ? 'M5 15 L15 5 M8 5 H15 V12' : 'M10 3 V17 M3 10 H17';
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20">
      <Path d={d} stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

const ACTIONS = [
  { kind: 'out', label: 'Send to' },
  { kind: 'in', label: 'Receive' },
  { kind: 'add', label: 'Add' },
] as const;

export function WalletCard({ width, cents, children }: { width: number; cents: number; children?: React.ReactNode }) {
  return (
    <View style={[styles.card, { width }]}>
      <LinearGradient colors={[COLORS.cardHigh, COLORS.card, COLORS.card]} locations={[0, 0.45, 1]}
        start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={styles.row}>
        <Text style={styles.brand}>AERO</Text>
        <Odometer cents={cents} />
      </View>
      <View style={[styles.row, styles.rowChip]}>
        <View style={styles.chip} />
        <Text style={styles.faint}>VALID THRU</Text>
      </View>
      <View style={[styles.row, styles.rowNumber]}>
        <Text style={styles.number}>••••  ••••  ••••  1234</Text>
        <Text style={styles.number}>12/35</Text>
      </View>
      <Text style={styles.holder}>JULES MORROW</Text>
      <View style={styles.actions}>
        {ACTIONS.map((a) => (
          <View key={a.kind} style={styles.action}>
            <View style={styles.actionButton}><ArrowIcon kind={a.kind} color={COLORS.cardInk} /></View>
            <Text style={styles.actionLabel}>{a.label}</Text>
          </View>
        ))}
      </View>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    height: LAYOUT.cardHeight, borderRadius: LAYOUT.cardRadius, overflow: 'hidden',
    padding: LAYOUT.cardPad, backgroundColor: COLORS.card, borderCurve: 'continuous',
  },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rowChip: { marginTop: 20 },
  rowNumber: { marginTop: 8 },
  brand: { color: COLORS.cardInk, fontSize: TYPE.brand, fontWeight: '900', fontStyle: 'italic', letterSpacing: 1 },
  odometer: { flexDirection: 'row', height: DIGIT_H, overflow: 'hidden', transform: [{ translateY: 3 }] },
  digitWindow: { height: DIGIT_H, overflow: 'hidden' },
  balance: {
    color: COLORS.cardInk, fontSize: TYPE.balance, lineHeight: DIGIT_H, height: DIGIT_H, fontWeight: '700',
    fontVariant: ['tabular-nums'], letterSpacing: -0.5,
  },
  chip: { width: 30, height: 22, borderRadius: 5, backgroundColor: COLORS.chip },
  faint: { color: COLORS.cardFaint, fontSize: 9, fontWeight: '600', letterSpacing: 0.8 },
  number: { color: COLORS.cardInk, fontSize: TYPE.cardSmall + 1, fontWeight: '600', fontVariant: ['tabular-nums'] },
  holder: { marginTop: 8, color: COLORS.cardFaint, fontSize: 10, fontWeight: '600', letterSpacing: 1 },
  actions: {
    position: 'absolute', left: 0, right: 0, bottom: 24.5, flexDirection: 'row', justifyContent: 'space-between',
    paddingHorizontal: LAYOUT.cardPad - 8,
  },
  action: { width: 64, alignItems: 'center' },
  actionButton: {
    width: LAYOUT.actionSize, height: LAYOUT.actionSize, borderRadius: LAYOUT.actionSize / 2,
    backgroundColor: COLORS.cardButton, alignItems: 'center', justifyContent: 'center',
  },
  actionLabel: { marginTop: 8, color: COLORS.cardInk, fontSize: TYPE.cardLabel, fontWeight: '600' },
});
