/*
 Rocket Launch Progress — the text around the launch: the rolling status line, the odometer of the
 deploy's progress, the 3-2-1, the "Live" badge and the pipeline's steps. Each piece reads the
 loop's shared values (T = loop time, F = progress %, spin = spinner angle) in animated styles, so
 none of it re-renders while the loop runs.
*/
import { SymbolView } from 'expo-symbols';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { PAL } from './rocketArt';
import { STATUS, countDigit, deployedBadge, stepState, statusLine } from './rocketMotion';

type Clock = { T: SharedValue<number>; F: SharedValue<number>; spin: SharedValue<number> };

function Line({ i, T }: { i: number; T: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const s = statusLine(i, T.get());
    return { opacity: s.o, transform: [{ translateY: s.y }] };
  });
  return <Animated.Text style={[styles.status, style]} numberOfLines={1}>{STATUS[i]}</Animated.Text>;
}

/** One line in, the previous one out upward: never two strings faded over each other in place. */
export function StatusLine({ T }: { T: SharedValue<number> }) {
  return (
    <View style={styles.statusBox}>
      {STATUS.map((_, i) => (
        <Line key={i} i={i} T={T} />
      ))}
    </View>
  );
}

const DIGIT_H = 56;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

/** Column `place` (1, 10, 100): the ones roll with the value, higher columns turn over as the one below passes 9. */
function Column({ place, F }: { place: number; F: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = Math.max(0, F.get());
    const m = v / place;
    const base = Math.floor(m);
    const pos = place === 1 ? m : base + Math.max(0, (m - base) * 10 - 9);
    const shown = place === 1 ? 1 : place === 10 ? Math.min(1, Math.max(0, v - 9)) : Math.min(1, Math.max(0, v - 99));
    return { opacity: shown, transform: [{ translateY: -(pos % 10) * DIGIT_H }] };
  });
  return (
    <View style={styles.column}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>{d}</Text>
        ))}
      </Animated.View>
    </View>
  );
}

export function Odometer({ F, T }: { F: SharedValue<number>; T: SharedValue<number> }) {
  // Right-aligned columns; the readout steps aside while the countdown and the badge have the sky.
  const box = useAnimatedStyle(() => {
    const t = T.get();
    const away = Math.min(1, Math.max(0, (t - 4.3) / 0.2)) * (1 - Math.min(1, Math.max(0, (t - 9.3) / 0.3)));
    return { opacity: 1 - away, transform: [{ translateY: -10 * away }] };
  });
  return (
    <Animated.View style={[styles.odo, box]}>
      <Text style={styles.odoLabel}>DEPLOY PROGRESS</Text>
      <View style={styles.odoRow}>
        <Column place={100} F={F} />
        <Column place={10} F={F} />
        <Column place={1} F={F} />
        <Text style={styles.percent}>%</Text>
      </View>
    </Animated.View>
  );
}

function CountDigit({ k, T }: { k: number; T: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = countDigit(k, T.get());
    return { opacity: d.o, transform: [{ translateY: d.y }, { scale: d.s }] };
  });
  return <Animated.Text style={[styles.count, style]}>{3 - k}</Animated.Text>;
}

export function Countdown({ T }: { T: SharedValue<number> }) {
  return (
    <View pointerEvents="none" style={styles.countBox}>
      {[0, 1, 2].map((k) => (
        <CountDigit key={k} k={k} T={T} />
      ))}
    </View>
  );
}

export function Badge({ T }: { T: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const b = deployedBadge(T.get());
    return { opacity: b.o, transform: [{ scale: b.s }] };
  });
  return (
    <Animated.View style={[styles.badge, style]}>
      <SymbolView name={{ ios: 'checkmark.circle.fill', android: 'check_circle', web: 'check_circle' }} size={20} tintColor={PAL.amber} />
      <Text style={styles.badgeText}>v4.2.0 is live</Text>
    </Animated.View>
  );
}

const STEPS = [
  { name: 'Build', detail: '2,184 modules · 41 s' },
  { name: 'Test', detail: '612 checks passed' },
  { name: 'Upload', detail: '38.4 MB to 3 regions' },
  { name: 'Launch', detail: 'Traffic 0 → 100 %' },
];

function StepRow({ i, clock, h }: { i: number; clock: Clock; h: number }) {
  const { T, F, spin } = clock;
  const wait = useAnimatedStyle(() => ({ opacity: stepState(i, T.get(), F.get()) === 0 ? 1 : 0 }));
  const run = useAnimatedStyle(() => ({
    opacity: stepState(i, T.get(), F.get()) === 1 ? 1 : 0,
    transform: [{ rotate: `${spin.get()}rad` }],
  }));
  const done = useAnimatedStyle(() => {
    const on = stepState(i, T.get(), F.get()) === 2;
    return { opacity: on ? 1 : 0, transform: [{ scale: on ? 1 : 0.85 }] };
  });
  const label = useAnimatedStyle(() => ({ opacity: stepState(i, T.get(), F.get()) === 0 ? 0.5 : 1 }));
  return (
    <View style={[styles.row, { height: h }]}>
      <View style={styles.icon}>
        <Animated.View style={[styles.dot, wait]} />
        <Animated.View style={[styles.ring, run]} />
        <Animated.View style={[styles.check, done]}>
          <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={12} weight="bold" tintColor="#2A1606" />
        </Animated.View>
      </View>
      <Animated.View style={[styles.rowText, label]}>
        <Text style={styles.stepName}>{STEPS[i].name}</Text>
        <Text style={styles.stepDetail} numberOfLines={1}>{STEPS[i].detail}</Text>
      </Animated.View>
    </View>
  );
}

export function StepsCard({ clock, top, height, rowH }: { clock: Clock; top: number; height: number; rowH: number }) {
  return (
    <View style={[styles.card, { top, height }]}>
      <View style={styles.cardHead}>
        <Text style={styles.cardTitle}>Pipeline</Text>
        <Text style={styles.cardHint}>Hold the pad to fuel</Text>
      </View>
      {STEPS.map((_, i) => (
        <StepRow key={i} i={i} clock={clock} h={rowH} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  statusBox: { height: 22, overflow: 'hidden' },
  status: { position: 'absolute', left: 0, right: 0, color: PAL.inkSoft, fontSize: 15, fontWeight: '500' },
  odo: { position: 'absolute', left: 22, gap: 2 },
  odoLabel: { color: PAL.inkFaint, fontSize: 10, fontWeight: '700', letterSpacing: 1.6 },
  odoRow: { flexDirection: 'row', alignItems: 'flex-end' },
  column: { height: DIGIT_H, width: 32, overflow: 'hidden' },
  digit: { height: DIGIT_H, color: PAL.ink, fontSize: 52, lineHeight: DIGIT_H, fontWeight: '700', fontVariant: ['tabular-nums'], textAlign: 'center' },
  percent: { color: PAL.amber, fontSize: 24, fontWeight: '700', marginBottom: 9, marginLeft: 2 },
  countBox: { position: 'absolute', left: 0, right: 0, alignItems: 'center', height: 120, justifyContent: 'center' },
  count: { position: 'absolute', color: PAL.ink, fontSize: 96, fontWeight: '800', fontVariant: ['tabular-nums'], textShadowColor: 'rgba(255,150,80,0.45)', textShadowRadius: 24 },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(30,24,52,0.86)',
    borderWidth: 1,
    borderColor: 'rgba(255,169,77,0.4)',
    boxShadow: '0 10px 30px rgba(0,0,0,0.35)',
  },
  badgeText: { color: PAL.ink, fontSize: 16, fontWeight: '700' },
  card: {
    position: 'absolute',
    left: 14,
    right: 14,
    borderRadius: 26,
    borderCurve: 'continuous',
    paddingHorizontal: 18,
    paddingTop: 16,
    backgroundColor: PAL.card,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 18px 40px rgba(0,0,0,0.4)',
  },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 },
  cardTitle: { color: PAL.ink, fontSize: 17, fontWeight: '700' },
  cardHint: { color: PAL.inkFaint, fontSize: 12, fontWeight: '600' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  icon: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center' },
  dot: { position: 'absolute', width: 10, height: 10, borderRadius: 5, borderWidth: 1.5, borderColor: 'rgba(243,238,248,0.35)' },
  ring: { position: 'absolute', width: 22, height: 22, borderRadius: 11, borderWidth: 2.5, borderColor: 'rgba(255,169,77,0.18)', borderTopColor: PAL.amber },
  check: { position: 'absolute', width: 22, height: 22, borderRadius: 11, alignItems: 'center', justifyContent: 'center', backgroundColor: PAL.amber },
  rowText: { flex: 1, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 10 },
  stepName: { color: PAL.ink, fontSize: 16, fontWeight: '600' },
  stepDetail: { flexShrink: 1, color: PAL.inkSoft, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
});
