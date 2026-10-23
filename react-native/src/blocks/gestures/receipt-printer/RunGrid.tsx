/** The run list: title, the year in three figures, and a grid of runs, each with its route traced. */
import { SymbolView } from 'expo-symbols';
import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { POLISH, PRESS } from './motion';
import { CAP_TOP, CARDS, COLOR, COPY, GRID, FONT, PILLS, TYPE } from './theme';

const HOLD_EASE = Easing.bezier(POLISH.easeOut[0], POLISH.easeOut[1], POLISH.easeOut[2], POLISH.easeOut[3]);

/**
 * The run's route, as a map app would trace it: a loop made of a few harmonics with seeded weights and phases, so each
 * tile's route has its own shape (a long loop by the river, a lopsided one up a hill), fitted to the tile.
 */
function routePath(seed: number, w: number, h: number) {
  const rnd = (k: number) => {
    const v = Math.sin(seed * 91.7 + k * 12.9898) * 43758.5453;
    return v - Math.floor(v);
  };
  const a2 = 0.1 + rnd(1) * 0.18;
  const a3 = rnd(2) * 0.12;
  const b2 = 0.08 + rnd(3) * 0.22;
  const p2 = rnd(4) * 6.28;
  const p3 = rnd(5) * 6.28;
  const q2 = rnd(6) * 6.28;
  const pts: [number, number][] = [];
  for (let k = 0; k < 64; k++) {
    const t = (k / 64) * Math.PI * 2;
    pts.push([Math.cos(t) + a2 * Math.cos(2 * t + p2) + a3 * Math.cos(3 * t + p3), Math.sin(t) + b2 * Math.sin(2 * t + q2) + a3 * 0.6 * Math.sin(3 * t + p3)]);
  }
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
  for (const [x, y] of pts) {
    x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
  }
  const k = Math.min((w - 6) / (x1 - x0), (h - 6) / (y1 - y0));
  const ox = (w - (x1 - x0) * k) / 2 - x0 * k;
  const oy = (h - (y1 - y0) * k) / 2 - y0 * k;
  const at = (i: number): [number, number] => [ox + pts[i][0] * k, oy + pts[i][1] * k];
  const d = pts.map((_, i) => `${i === 0 ? 'M' : 'L'}${at(i)[0].toFixed(1)} ${at(i)[1].toFixed(1)}`).join(' ') + ' Z';
  return { d, start: at(Math.floor(rnd(7) * 64)) };
}

const ROUTE = { w: 78, h: 44 };

/** The route in ink, its start as a dot. */
function Route({ seed }: { seed: number }) {
  const { d, start } = routePath(seed, ROUTE.w, ROUTE.h);
  return <Svg width={ROUTE.w} height={ROUTE.h} viewBox={`0 0 ${ROUTE.w} ${ROUTE.h}`}>
    <Path d={d} fill="none" stroke={COLOR.icon} strokeWidth={1.6} strokeLinejoin="round" strokeLinecap="round" />
    <Circle cx={start[0]} cy={start[1]} r={2.6} fill={COLOR.zone} stroke={COLOR.icon} strokeWidth={1} />
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
      <View style={[styles.icon, { top: GRID.iconTop - 8 }]}><Route seed={c.seed} /></View>
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
  icon: { position: 'absolute', left: (GRID.cardW - ROUTE.w) / 2 },
  km: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.heavy, fontSize: TYPE.cardKm, color: COLOR.ink },
});
