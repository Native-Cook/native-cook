/** The run list: title, three month pills and a grid of run cards with a treadmill drawing. */
import { SymbolView } from 'expo-symbols';
import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { POLISH, PRESS } from './motion';
import { CAP_TOP, CARDS, COLOR, COPY, GRID, FONT, PILLS, TYPE } from './theme';

const HOLD_EASE = Easing.bezier(POLISH.easeOut[0], POLISH.easeOut[1], POLISH.easeOut[2], POLISH.easeOut[3]);

/** A bar from (x1, y1) to (x2, y2), w thick, with round ends: the treadmill is built from these. */
function bar(x1: number, y1: number, x2: number, y2: number, w: number) {
  const l = Math.hypot(x2 - x1, y2 - y1);
  const nx = (-(y2 - y1) / l) * (w / 2);
  const ny = ((x2 - x1) / l) * (w / 2);
  const r = w / 2;
  return `M${x1 + nx} ${y1 + ny} L${x2 + nx} ${y2 + ny} A${r} ${r} 0 0 0 ${x2 - nx} ${y2 - ny} L${x1 - nx} ${y1 - ny} A${r} ${r} 0 0 0 ${x1 + nx} ${y1 + ny}Z`;
}

// Measured on the source's card (viewBox = points): deck, motor housing, upright, handlebar, console.
const TREADMILL = [
  bar(2.8, 28.4, 42.6, 25.6, 5.4),
  bar(34.6, 21.8, 43.4, 21.2, 5),
  bar(30.6, 30.4, 28.2, 9.8, 4),
  bar(7.6, 9.8, 39.2, 7.4, 4.2),
  'M33.8 0.6 L38.6 6.2 L30.4 6.8 Z',
];

/** A treadmill in a three-quarter side view, white with an ink outline, as drawn on the source's cards. */
function Treadmill() {
  return <Svg width={GRID.iconW} height={GRID.iconH} viewBox="0 0 46 33">
    <Path d="M6 31.6 a2.1 1 0 1 0 4.2 0 a2.1 1 0 1 0 -4.2 0z" fill={COLOR.icon} />
    {TREADMILL.map((d) => <Path key={d} d={d} fill={COLOR.paper} stroke={COLOR.icon} strokeWidth={1.3} strokeLinejoin="round" />)}
  </Svg>;
}

function cap(size: number, capTop: number) {
  return capTop - size * CAP_TOP;
}

type Props = {
  top: number;
  pressed: SharedValue<number>;
  lift: SharedValue<number>;
  onPress: (i: number) => void;
  onLongPress: (i: number) => void;
};

function Card({ i, top, left, pressed, lift, onPress, onLongPress }: { i: number; top: number; left: number } & Omit<Props, 'top'>) {
  const c = CARDS[i];
  const reduced = useReducedMotion();
  // Press and hold: the tile sinks over the long-press time, then pops up as the menu opens (or springs back).
  const hold = useSharedValue(0);
  const sink = () => { if (!reduced) hold.set(withTiming(1, { duration: PRESS.longPressMs, easing: HOLD_EASE })); };
  const rise = () => { hold.set(withSpring(0, POLISH.releaseSpring)); };
  const anim = useAnimatedStyle(() => {
    const on = pressed.get() === i ? lift.get() : 0;
    return {
      backgroundColor: on > 0.01 ? COLOR.cardPressed : COLOR.card,
      transform: [{ scale: (1 + (PRESS.cardLift - 1) * on) * (1 - (1 - POLISH.holdScale) * hold.get()) }],
      shadowOpacity: 0.12 * on,
    };
  });
  return <Pressable onPress={() => onPress(i)} onLongPress={() => { rise(); onLongPress(i); }} delayLongPress={PRESS.longPressMs}
    onPressIn={sink} onPressOut={rise} pressRetentionOffset={16}
    accessibilityRole="button" accessibilityLabel={`Run ${c.date.join(' ')}, ${c.km}. Opens its receipt.`}
    style={[styles.cardHit, { top, left }]}>
    <Animated.View style={[styles.card, anim]}>
      <Text style={[styles.date, { top: cap(TYPE.cardDate, GRID.dateCap), left: GRID.dateInset }]}>{c.date[0]}</Text>
      <Text style={[styles.date, { top: cap(TYPE.cardDate, GRID.dateCap), right: GRID.dateInset }]}>{c.date[1]}</Text>
      <View style={[styles.icon, { top: GRID.iconTop }]}><Treadmill /></View>
      <Text style={[styles.km, { top: cap(TYPE.cardKm, GRID.cardH - GRID.kmCapFromBottom) }]}>{c.km}</Text>
    </Animated.View>
  </Pressable>;
}

export const RunGrid = memo(function RunGrid({ top, pressed, lift, onPress, onLongPress }: Props) {
  const col = (k: number) => GRID.gutter + k * (GRID.cardW + GRID.gap);
  return <View style={StyleSheet.absoluteFill}>
    <Text style={[styles.title, { top: top + cap(TYPE.title, GRID.titleCap) }]} accessibilityRole="header">{COPY.title}</Text>
    <Text style={[styles.subtitle, { top: top + cap(TYPE.subtitle, GRID.subtitleCap) }]}>{COPY.subtitle}</Text>
    <View style={[styles.info, { top: top + GRID.info.cy - GRID.info.r, left: GRID.info.cx - GRID.info.r }]} accessibilityLabel="About">
      <SymbolView name="info.circle" size={GRID.info.r * 2} tintColor={COLOR.ink} weight="semibold" />
    </View>
    {PILLS.map((p, k) => <View key={p} style={[styles.pill, { top: top + GRID.pillTop, left: col(k) }]}>
      <Text style={styles.pillText}>{p}</Text>
    </View>)}
    {CARDS.map((c, i) => <Card key={`${c.date.join('')}${c.km}`} i={i} top={top + GRID.rowTop + Math.floor(i / 3) * GRID.rowPitch}
      left={col(i % 3)} pressed={pressed} lift={lift} onPress={onPress} onLongPress={onLongPress} />)}
  </View>;
});

const styles = StyleSheet.create({
  title: { position: 'absolute', left: GRID.gutter, fontFamily: FONT.heavy, fontSize: TYPE.title, color: COLOR.ink },
  subtitle: { position: 'absolute', left: GRID.gutter, fontFamily: FONT.regular, fontSize: TYPE.subtitle, color: COLOR.inkSoft },
  info: { position: 'absolute', width: GRID.info.r * 2, height: GRID.info.r * 2 },
  pill: { position: 'absolute', width: GRID.cardW, height: GRID.pillH, borderRadius: GRID.radius, backgroundColor: COLOR.card, alignItems: 'center', justifyContent: 'center' },
  pillText: { fontFamily: FONT.heavy, fontSize: TYPE.pill, color: COLOR.ink },
  cardHit: { position: 'absolute', width: GRID.cardW, height: GRID.cardH },
  card: {
    flex: 1, borderRadius: GRID.radius, backgroundColor: COLOR.card,
    shadowColor: COLOR.shadow, shadowRadius: 14, shadowOffset: { width: 0, height: 6 }, shadowOpacity: 0,
  },
  date: { position: 'absolute', fontFamily: FONT.medium, fontSize: TYPE.cardDate, color: COLOR.date },
  icon: { position: 'absolute', left: (GRID.cardW - GRID.iconW) / 2 },
  km: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.heavy, fontSize: TYPE.cardKm, color: COLOR.ink },
});
