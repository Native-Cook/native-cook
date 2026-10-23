/**
 * Drag Card to Send: pull the card down and the Dynamic Island lets out a drop of liquid that holds the amount, one
 * detent per step; slide sideways to choose who. Let go: the drop pinches off, falls into that person, and the island
 * becomes the receipt with Undo while the balance rolls down.
 * Layers, back to front: wallet home, the card, the liquid, the people (a drop falls behind their faces), the
 * amount on the drop, the receipt.
 */
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions, type AccessibilityActionEvent } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, withTiming,
} from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';

import { DropAmount, IslandLiquid, Receipt, useLiquid, type Subscribe } from './Island';
import { SEND, easeOut } from './motion';
import { Activity, Avatar, PeopleRow } from './People';
import { COLORS, LAYOUT, PEOPLE, PHOTOS, ROWS, START_BALANCE, STEPS, TYPE, formatMoney, makeLayout, type Row } from './theme';
import { PHASE, useSendMotion, type Haptic } from './use-send-motion';
import { WalletCard } from './WalletCard';

export type DragCardToSendProps = { autoPlay?: boolean };

const HAPTIC: Record<Haptic, () => void> = {
  tick: () => { void Haptics.selectionAsync(); },
  light: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); },
  success: () => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
};

export function DragCardToSend({ autoPlay = false }: DragCardToSendProps) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  // Geometry is fixed per width; remount on a width or Reduce Motion change so the worklets see the new values.
  return <Wallet key={`${width}-${reduced}`} width={width} reduced={reduced} autoPlay={autoPlay} />;
}

const A11Y_DEFAULT_STEP = STEPS.indexOf(20);

/**
 * 0 → 1 once the screen is drawing steadily. The card's Skia canvas paints a few frames after the views do, so on
 * opening the block its white type would sit bare on the light page; the card waits for this and fades in.
 */
function useSteady() {
  const ready = useSharedValue(0);
  const count = useSharedValue({ ms: 0, smooth: 0 });
  useFrameCallback((info) => {
    if (ready.get() > 0) return;
    const dt = info.timeSincePreviousFrame ?? 0;
    const c = count.get();
    const next = { ms: c.ms + dt, smooth: dt > 0 && dt < 50 ? c.smooth + 1 : 0 };
    count.set(next);
    if (next.ms > 200 && next.smooth >= 4) ready.set(withTiming(1, { duration: 180, easing: easeOut, reduceMotion: ReduceMotion.Never }));
  });
  return ready;
}

function Wallet({ width, reduced, autoPlay }: { width: number; reduced: boolean; autoPlay: boolean }) {
  const lay = useMemo(() => makeLayout(width), [width]);
  const [phase, setPhase] = useState<number>(PHASE.rest);
  const [balance, setBalance] = useState(START_BALANCE);
  const [rows, setRows] = useState<Row[]>(ROWS);
  const [last, setLast] = useState<{ person: number; cents: number } | null>(null);
  const [draft, setDraft] = useState({ step: -1, person: -1 });
  const [a11yStep, setA11yStep] = useState(A11Y_DEFAULT_STEP);

  // The drop's amount is its own small component; it listens here instead of re-rendering the whole screen.
  const listeners = useRef(new Set<(s: number, p: number) => void>());
  const subscribe = useCallback<Subscribe>((fn) => {
    listeners.current.add(fn);
    return () => { listeners.current.delete(fn); };
  }, []);
  const onDraft = useCallback((step: number, person: number) => {
    listeners.current.forEach((fn) => fn(step, person));
    // The hint keeps the last full choice while the drop falls (the flight ends with a draft of -1 once sent).
    setDraft((d) => (step < 0 && person >= 0 ? d : { step, person }));
  }, []);
  const onSent = useCallback((person: number, cents: number) => {
    setLast({ person, cents });
    setBalance((b) => b - cents);
    const p = PEOPLE[person];
    setRows((r) => [{ id: `s${Date.now()}`, name: p.full, meta: 'Just now', cents: -cents, person }, ...r]);
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

  const { values: v, input } = useSendMotion(lay, autoPlay, reduced, { onPhase: setPhase, onDraft, onSent, onUndo, onHaptic });
  const look = useLiquid(lay, v);

  const pan = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((e) => { input.takeOver(); input.down(e.absoluteX, e.absoluteY, false); })
    .onUpdate((e) => { input.move(e.absoluteX, e.absoluteY); })
    .onFinalize((e) => { input.up(e.velocityY, e.absoluteY); input.letGo(); }), [input]);

  // Any tap also stops the script, like a drag does.
  const ui = useCallback((fn: () => void) => scheduleOnUI(() => {
    'worklet';
    input.takeOver();
    fn();
    input.letGo();
  }), [input]);

  const steady = useSteady();
  // Reduce Motion keeps the press in the card's light (WalletCard reads `press`) and drops the scale.
  const pressScale = reduced ? 1 : SEND.pressScale;
  const cardStyle = useAnimatedStyle(() => ({
    opacity: steady.get(),
    transform: [{ translateY: v.cardY.get() }, { scale: 1 - (1 - pressScale) * v.press.get() }],
  }));

  const pulling = phase === PHASE.pulling && draft.step >= 0 && draft.person >= 0;
  const falling = phase === PHASE.falling;
  const chosen = `$${STEPS[Math.max(0, draft.step)]} to ${PEOPLE[Math.max(0, draft.person)].name}`;
  const hint = pulling ? `Let go to send ${chosen}` : falling ? `Sending ${chosen}` : 'Pull the card to send';

  const a11yAmount = `$${STEPS[a11yStep]}`;
  const onA11y = (e: AccessibilityActionEvent) => {
    const name = e.nativeEvent.actionName;
    if (name === 'increment') setA11yStep((s) => Math.min(STEPS.length - 1, s + 1));
    else if (name === 'decrement') setA11yStep((s) => Math.max(0, s - 1));
    else {
      const i = PEOPLE.findIndex((p) => p.id === name);
      const s = a11yStep;
      if (i >= 0) ui(() => { 'worklet'; input.sendTo(i, s); });
    }
  };

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={[styles.header, { top: LAYOUT.headerTop }]}>
        <Text style={[TYPE.largeTitle, styles.ink]} accessibilityRole="header">Wallet</Text>
        <View accessible accessibilityLabel="Account, Jules Morrow"><Avatar source={PHOTOS.jules} size={LAYOUT.headerAvatar} /></View>
      </View>

      <View style={[styles.section, { top: lay.sectionTop }]}>
        <Text style={[TYPE.section, styles.ink]} accessibilityRole="header">Send to</Text>
        <Text style={[TYPE.hint, { color: pulling || falling ? COLORS.ink : COLORS.mute }]} accessibilityLiveRegion="polite">{hint}</Text>
      </View>

      <View style={[styles.section, { top: lay.activityTop }]}>
        <Text style={[TYPE.section, styles.ink]} accessibilityRole="header">Activity</Text>
        <Text style={[TYPE.hint, styles.link]} accessibilityRole="link">See all</Text>
      </View>
      <Activity rows={rows} top={lay.rowTop} reduced={reduced} />

      <GestureDetector gesture={pan}>
        <Animated.View style={[styles.card, { top: lay.cardTop, width: lay.cardW, height: lay.cardH }, cardStyle]}
          accessible accessibilityRole="adjustable"
          accessibilityLabel={`Cove card, balance ${formatMoney(balance)}`}
          accessibilityValue={{ text: `Amount ${a11yAmount}` }}
          accessibilityHint="Pull down to set an amount, slide sideways to choose a person, and let go to send"
          accessibilityActions={[
            { name: 'increment' }, { name: 'decrement' },
            ...PEOPLE.map((p) => ({ name: p.id, label: `Send ${a11yAmount} to ${p.name}` })),
          ]}
          onAccessibilityAction={onA11y}>
          <WalletCard width={lay.cardW} height={lay.cardH} cents={balance} shift={v.cardY} press={v.press} />
        </Animated.View>
      </GestureDetector>

      <IslandLiquid lay={lay} look={look} />
      <PeopleRow lay={lay} v={v} reduced={reduced} />
      <DropAmount look={look} subscribe={subscribe} reduced={reduced} />
      <Receipt lay={lay} v={v} last={last} canUndo={phase === PHASE.sent} reduced={reduced}
        onUndo={() => {
          // The haptic on the tap's own frame; the receipt folds on the UI thread.
          HAPTIC.light();
          ui(() => { 'worklet'; input.tapUndo(false); });
        }} />
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.page },
  ink: { color: COLORS.ink },
  link: { color: COLORS.accent, fontWeight: '500' },
  header: { position: 'absolute', left: LAYOUT.edge, right: LAYOUT.edge, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  section: { position: 'absolute', left: LAYOUT.edge, right: LAYOUT.edge, flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  card: { position: 'absolute', left: LAYOUT.edge },
});
