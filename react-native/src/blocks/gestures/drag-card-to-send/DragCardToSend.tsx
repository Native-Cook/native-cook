/**
 * Drag Card to Send: pull the card down and the island swells into your contacts; drop the card on a person.
 * Layers, back to front: wallet home, the black island shape, the sheet content, the pill, the card.
 */
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  FadeInUp, FadeOutUp, LinearTransition, useAnimatedStyle, useReducedMotion,
} from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';

import { bodyAt, clamp01 } from './island-path';
import { IslandPill, IslandShape } from './Island';
import { AMBIENT, FADE, PULL } from './motion';
import { SendSheet } from './SendSheet';
import { AMOUNTS, COLORS, CONTACTS, DEFAULT_AMOUNT_INDEX, FAVOURITES, LAYOUT, SPACE, START_BALANCE, TRANSACTIONS, TYPE } from './theme';
import { PHASE, makeGeometry, useSendMotion, type Haptic } from './use-send-motion';
import { ArrowIcon, WalletCard, formatMoney } from './WalletCard';

export type DragCardToSendProps = { autoPlay?: boolean };

const PEOPLE = [...FAVOURITES, ...CONTACTS];
type Row = { id: string; name: string; date: string; amount: number; kind: 'in' | 'out' };

const HAPTIC: Record<Haptic, () => void> = {
  light: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); },
  medium: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); },
  select: () => { void Haptics.selectionAsync(); },
  success: () => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
};

export function DragCardToSend({ autoPlay = false }: DragCardToSendProps) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  // Geometry is fixed per size; remount on a size or Reduce Motion change so the worklets see the new values.
  return <Wallet key={`${width}x${height}-${reduced}`} width={width} height={height} reduced={reduced} autoPlay={autoPlay} />;
}

function Wallet({ width, height, reduced, autoPlay }: { width: number; height: number; reduced: boolean; autoPlay: boolean }) {
  const geo = useMemo(() => makeGeometry(width, height), [width, height]);
  const [phase, setPhase] = useState<number>(PHASE.rest);
  const [balance, setBalance] = useState(START_BALANCE);
  // Quick-send amount; the source's sheet has no amount picker, so it stays the default here.
  const [amountIndex] = useState(DEFAULT_AMOUNT_INDEX);
  const [rows, setRows] = useState<Row[]>(TRANSACTIONS.map((t) => ({ ...t })));
  const [last, setLast] = useState<{ target: number; cents: number } | null>(null);

  // The worklets keep the first callbacks they were given, so the amount is read through a ref.
  const amountRef = useRef(amountIndex);
  useEffect(() => { amountRef.current = amountIndex; }, [amountIndex]);
  const onSent = useCallback((target: number) => {
    const cents = AMOUNTS[amountRef.current] * 100;
    setLast({ target, cents });
    setBalance((b) => b - cents);
    setRows((r) => [{ id: `s${Date.now()}`, name: PEOPLE[target].name, date: 'Just now', amount: -cents, kind: 'out' }, ...r]);
  }, []);
  const onUndo = useCallback(() => {
    setLast((l) => {
      if (l) {
        setBalance((b) => b + l.cents);
        setRows((r) => r.slice(1));
      }
      return null;
    });
  }, []);
  const onHaptic = useCallback((kind: Haptic) => HAPTIC[kind](), []);

  const { values: v, input } = useSendMotion(geo, autoPlay, reduced, { onPhase: setPhase, onSent, onUndo, onHaptic });

  const pan = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((e) => { input.takeOver(); input.down(e.absoluteX, e.absoluteY, false); })
    .onUpdate((e) => { input.move(e.absoluteX, e.absoluteY, e.velocityX); })
    .onFinalize((e) => { input.up(e.velocityX, e.velocityY); input.letGo(); }), [input]);

  // Any tap on a sheet control also stops the script, like a drag does.
  const ui = useCallback((fn: () => void) => scheduleOnUI(() => {
    'worklet';
    input.takeOver();
    fn();
    input.letGo();
  }), [input]);

  const sheenStyle = useAnimatedStyle(() => {
    const drift = reduced ? 0 : Math.sin((2 * Math.PI * v.clock.get()) / AMBIENT.sheenMs) * 0.35;
    const p = drift + v.tilt.get() * 0.9 + v.sway.get() / 20;
    return { transform: [{ translateX: p * geo.cardW }, { rotate: '18deg' }] };
  });
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: v.phase.get() === PHASE.pulling ? clamp01(v.cardY.get() / 60) * (1 - clamp01((v.g.get() - 0.95) / 0.2)) : 0,
  }));
  // The header buttons step aside for the growing drop (source frame a: 29 pt each at g 0.66).
  const spread = (g: number) => {
    'worklet';
    const u = clamp01(g / 0.66);
    return reduced ? 0 : LAYOUT.headerSpread * u * u * (3 - 2 * u);
  };
  const spreadL = useAnimatedStyle(() => ({ transform: [{ translateX: -spread(v.g.get()) }] }));
  const spreadR = useAnimatedStyle(() => ({ transform: [{ translateX: spread(v.g.get()) }] }));
  // The drop shows a white "send" arrow while it grows (source frame a), gone once it becomes the sheet.
  const dropArrowStyle = useAnimatedStyle(() => {
    const g = v.g.get();
    const b = bodyAt(Math.min(1, g), width, height);
    return {
      opacity: reduced ? 0 : PULL.arrow * clamp01(g / 0.4) * (1 - clamp01((g - 0.9) / 0.2)),
      transform: [{ translateY: b.y + b.h * 0.42 - 12 }],
    };
  });
  const deepStyle = useAnimatedStyle(() => ({ opacity: PULL.deep * clamp01(v.cardY.get() / 263) }));
  // The pulled card pushes the transactions down with it.
  // The pushed list also sinks back a little (scale), as the source's does under the pulled card.
  const listStyle = useAnimatedStyle(() => {
    const y = v.follow.get() ? 0 : Math.max(0, v.cardY.get());
    return { transform: [{ translateY: y }, { scale: 1 - PULL.listSink * clamp01(y / 263) }] };
  });
  const homeStyle = useAnimatedStyle(() => {
    const open = reduced ? v.veil.get() : clamp01((v.g.get() - 1) / 0.6);
    return { opacity: 1 - (1 - FADE.homeDim) * open };
  });
  const cardStyle = useAnimatedStyle(() => {
    const follow = v.follow.get();
    const s = v.cardScale.get();
    const tx = follow ? v.fx.get() - v.ox.get() * s - geo.homeCx + v.magX.get() : v.cardX.get();
    const ty = follow ? v.fy.get() - v.oy.get() * s - geo.homeCy + v.magY.get() : v.cardY.get();
    const lift = clamp01((1 - s) / (1 - 0.42));
    return {
      opacity: v.cardOpacity.get() * (1 - PULL.fade * v.tilt.get()),
      shadowOpacity: 0.18 + 0.22 * lift,
      shadowRadius: 18 + 14 * lift,
      transform: [
        // Pulling shrinks and fades the card a little (no 3D tilt: iOS would sort half of it behind the page).
        { translateX: tx }, { translateY: ty }, { rotateZ: `${v.sway.get()}deg` },
        { scale: s * v.press.get() * (1 - PULL.shrink * v.tilt.get()) },
      ],
    };
  });

  const open = phase === PHASE.docked || phase === PHASE.holding;
  const person = last ? PEOPLE[last.target] : null;
  const receipt = last ? formatMoney(last.cents).replace('.00', '') : '';
  const cardLabel = `AERO card, balance ${formatMoney(balance)}`;

  return (
    <GestureHandlerRootView style={styles.root}>
      <Animated.View style={[styles.fill, homeStyle]} importantForAccessibility={open ? 'no-hide-descendants' : 'auto'}
        accessibilityElementsHidden={open}>
        <View style={[styles.header, { top: LAYOUT.headerCenter - LAYOUT.headerButton / 2, left: LAYOUT.side, right: LAYOUT.side }]}>
          <Animated.View style={[styles.headerButton, spreadL]} accessible={false}>
            <View style={styles.grid}>
              {Array.from({ length: 9 }, (_, i) => <View key={i} style={styles.dot} />)}
            </View>
          </Animated.View>
          <View />
          <Animated.View style={[styles.headerButton, spreadR]} accessible={false}><ArrowIcon kind="add" color={COLORS.ink} /></Animated.View>
        </View>
        <Animated.View style={[StyleSheet.absoluteFill, listStyle]} pointerEvents="box-none">
        <View style={[styles.listTitle, { top: LAYOUT.listTitleTop, left: LAYOUT.side, right: LAYOUT.side }]}>
          <View>
            <Text style={styles.titleLight}>Last</Text>
            <Text style={styles.titleBold} accessibilityRole="header">Transactions</Text>
          </View>
          <View style={[styles.headerButton, styles.titleButton]} accessible={false}><ArrowIcon kind="open" color={COLORS.ink} /></View>
        </View>
        <View style={{ position: 'absolute', top: LAYOUT.rowTop, left: LAYOUT.side, right: LAYOUT.side, gap: LAYOUT.rowGap }}>
          {rows.slice(0, 5).map((r) => (
            <Animated.View key={r.id} style={styles.row} entering={FadeInUp.duration(320)} exiting={FadeOutUp.duration(200)}
              layout={LinearTransition.springify().damping(24)} accessible
              accessibilityLabel={`${r.name}, ${r.date}, ${formatMoney(r.amount)}`}>
              <View style={styles.rowIcon}><ArrowIcon kind={r.kind === 'out' ? 'rowOut' : 'rowIn'} color={COLORS.ink} size={22} /></View>
              <View style={styles.rowText}>
                <Text style={styles.rowName} numberOfLines={1}>{r.name}</Text>
                <Text style={styles.rowDate}>{r.date}</Text>
              </View>
              <Text style={[styles.rowAmount, r.amount > 0 && { color: COLORS.positive }]}>
                {r.amount > 0 ? '+' : ''}{formatMoney(r.amount)}
              </Text>
            </Animated.View>
          ))}
        </View>
        </Animated.View>
      </Animated.View>

      {/* While pulling, "Drag to send" appears where the card was (source frame a); the list below is pushed down. */}
      <Animated.Text pointerEvents="none" style={[styles.ghostLabel, { top: LAYOUT.dragLabelTop }, ghostStyle]}>Drag to send</Animated.Text>
      <IslandShape width={width} height={height} v={v} reduced={reduced} />
      <Animated.View pointerEvents="none" style={[styles.dropArrow, { left: width / 2 - SPACE.xxl / 2 }, dropArrowStyle]}>
        <ArrowIcon kind="out" color={COLORS.sheetInk} size={SPACE.xxl} />
      </Animated.View>
      <SendSheet geo={geo} v={v} reduced={reduced} interactive={open}
        onTarget={(i) => ui(() => { 'worklet'; input.select(i); })}
        onNext={() => ui(() => { 'worklet'; const i = v.hoverIdx.get(); if (i >= 0) input.tapTarget(i); })}
        onClose={() => ui(() => { 'worklet'; input.close(); })} />
      <IslandPill width={width} v={v} receipt={receipt} canUndo={phase === PHASE.sent}
        receiptLabel={person ? `Sent ${formatMoney(last?.cents ?? 0)} to ${person.name}` : ''}
        onUndo={() => ui(() => { 'worklet'; input.tapUndo(false); })} />

      <GestureDetector gesture={pan}>
        <Animated.View pointerEvents={phase === PHASE.docked ? 'none' : 'auto'}
          style={[styles.card, { top: LAYOUT.cardTop, left: LAYOUT.side, width: geo.cardW }, cardStyle]}
          accessible accessibilityRole="button" accessibilityLabel={cardLabel}
          accessibilityHint={open ? undefined : 'Pull down to choose who to send money to'}
          accessibilityActions={[{ name: 'activate', label: 'Send money' }]}
          onAccessibilityAction={() => ui(() => { 'worklet'; input.openSheet(); })}>
          <WalletCard width={geo.cardW} cents={balance}>
            {/* Lifted off the page, the card falls out of its light and reads deeper blue (source frame a). */}
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.deep, deepStyle]} />
            {/* Specular sheen: drifts slowly at rest, follows the pull and the token's sway. */}
            <Animated.View pointerEvents="none" style={[styles.sheen, sheenStyle]}>
              <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0)']}
                start={{ x: 0, y: 0.2 }} end={{ x: 1, y: 0.8 }} style={StyleSheet.absoluteFill} />
            </Animated.View>
          </WalletCard>
        </Animated.View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.page },
  fill: { ...StyleSheet.absoluteFill },
  header: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerButton: {
    width: LAYOUT.headerButton, height: LAYOUT.headerButton, borderRadius: LAYOUT.headerButton / 2,
    backgroundColor: COLORS.headerButton, alignItems: 'center', justifyContent: 'center',
  },
  grid: { width: 14, flexDirection: 'row', flexWrap: 'wrap', gap: 2 },
  dot: { width: 3.3, height: 3.3, borderRadius: 1, backgroundColor: COLORS.ink },
  sheen: { position: 'absolute', top: -60, bottom: -60, left: '20%', width: '45%' },
  deep: { backgroundColor: COLORS.cardDeep },
  dropArrow: { position: 'absolute', top: 0, width: SPACE.xxl, height: SPACE.xxl },
  ghost: { position: 'absolute', height: LAYOUT.cardHeight, borderRadius: LAYOUT.cardRadius, overflow: 'hidden', opacity: 0 },
  frost: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(220,229,236,0.88)' },
  ghostLabel: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: COLORS.mute, fontSize: TYPE.hint, fontWeight: '500' },
  listTitle: { position: 'absolute', paddingLeft: 2, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  titleLight: { color: COLORS.ink, fontSize: TYPE.title, lineHeight: 26, fontWeight: '400' },
  titleBold: { color: COLORS.ink, fontSize: TYPE.title, lineHeight: 26, fontWeight: '700', letterSpacing: -0.3 },
  titleButton: { width: 40, height: 40, borderRadius: 20 },
  row: {
    height: LAYOUT.rowHeight, borderRadius: LAYOUT.rowHeight / 2, backgroundColor: COLORS.rowFill,
    flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10,
  },
  rowIcon: {
    width: LAYOUT.rowIcon, height: LAYOUT.rowIcon, borderRadius: LAYOUT.rowIcon / 2, backgroundColor: COLORS.rowIcon,
    alignItems: 'center', justifyContent: 'center',
  },
  rowText: { flex: 1, marginLeft: 18 },
  rowName: { color: COLORS.ink, fontSize: TYPE.rowName - 1, fontWeight: '600' },
  rowDate: { marginTop: 2, color: COLORS.mute, fontSize: TYPE.rowMeta },
  rowAmount: { marginRight: 0, color: COLORS.ink, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  card: {
    position: 'absolute', height: LAYOUT.cardHeight, borderRadius: LAYOUT.cardRadius,
    shadowColor: COLORS.shadow, shadowOffset: { width: 0, height: 14 }, elevation: 12,
  },
});
