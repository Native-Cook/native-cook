import { Canvas, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { GumballScene, type GumballValues } from './GumballScene';
import { PHASE, capsulePose, clamp, initialSim, input, makeGeometry, my, stepSim, type Input } from './gumball-sim';
import { COLLECT, CRANK, HAPTIC } from './motion';
import { BAG, CAPSULE, CARD, COLORS, COPY, MACHINE, TYPE } from './theme';

type Props = { autoPlay?: boolean };

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.detent) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
  else if (kind === HAPTIC.ratchet) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  else if (kind === HAPTIC.dispense) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.open) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  else if (kind === HAPTIC.collect) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

export function GumballCrank({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const [phase, setPhase] = useState<number>(PHASE.ready);

  // Settling the pile is a few thousand steps: do it once, not on every render.
  const [initial] = useState(() => initialSim(g, reduced, autoPlay));
  const sim = useSharedValue(initial);
  const radii = initial.br;
  const balls = useSharedValue<number[]>([]);
  const crank = useSharedValue(0);
  const press = useSharedValue(0);
  const cx = useSharedValue(0);
  const cy = useSharedValue(0);
  const cscale = useSharedValue(1);
  const capsuleOpacity = useSharedValue(0);
  const seamRy = useSharedValue(0);
  const flip = useSharedValue(0);
  const inDoor = useSharedValue(0);
  const split = useSharedValue(0);
  const card = useSharedValue(0);
  const collect = useSharedValue(0);
  const bag = useSharedValue(0);
  const phaseNow = useSharedValue<number>(PHASE.ready);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    const pts: number[] = [];
    for (let i = 0; i < s.br.length; i++) pts.push(s.bx[i], s.by[i]);
    balls.set(pts);
    crank.set(s.crank + s.wob * 0.06 + s.back * 0.35);
    press.set(s.press);
    const p = capsulePose(s, g);
    cx.set(p.x);
    cy.set(p.y);
    cscale.set(p.scale * g.s);
    capsuleOpacity.set(p.visible * (1 - (p.split > 0 ? 1 : 0)));
    seamRy.set(CAPSULE.r * Math.sin(p.spin));
    flip.set(Math.cos(p.spin) < 0 ? 1 : 0);
    inDoor.set(p.inDoor);
    split.set(p.split);
    card.set(p.card);
    collect.set(s.phase === PHASE.collecting ? clamp(s.phaseT / COLLECT.duration) : 0);
    bag.set(s.bag);
    if (s.phase !== phaseNow.get()) {
      phaseNow.set(s.phase);
      scheduleOnRN(setPhase, s.phase);
    }
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(playHaptic, s.events[i]);
    s.events = [];
  }, [sim, balls, crank, press, cx, cy, cscale, capsuleOpacity, seamRy, flip, inDoor, split, card, collect, bag, phaseNow, g]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    stepSim(s, g, frame.timeSincePreviousFrame / 1000, autoPlay);
    sim.set(s);
    publish();
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  // Only touches near the knob turn it; the card's button keeps its own touches.
  const gesture = useMemo(() => Gesture.Pan()
    .manualActivation(true)
    .maxPointers(1)
    .onTouchesDown((e, manager) => {
      const t = e.changedTouches[0];
      if (Math.hypot(t.x - g.knobX, t.y - g.knobY) < CRANK.grab * g.s && phaseNow.get() === PHASE.ready) {
        act({ type: 'down', x: t.x, y: t.y });
        manager.activate();
      } else {
        manager.fail();
      }
    })
    .onTouchesMove((e) => {
      const t = e.changedTouches[0];
      act({ type: 'move', x: t.x, y: t.y });
    })
    .onFinalize(() => act({ type: 'up' })), [act, g, phaseNow]);

  const command = useCallback((action: 'turn' | 'collect') => scheduleOnUI(act, { type: 'command', action }), [act]);

  // Skia values.
  const knob = useDerivedValue<Transforms3d>(() => [
    { translateX: g.knobX }, { translateY: g.knobY }, { rotate: crank.value }, { scale: g.s * (1 + (CRANK.press - 1) * press.value) },
  ]);
  const capsule = useDerivedValue<Transforms3d>(() => [{ translateX: cx.value }, { translateY: cy.value }, { scale: cscale.value }]);
  const shellTop = useDerivedValue<Transforms3d>(() => {
    const u = split.value;
    return [{ translateX: cx.value - 18 * u * g.s }, { translateY: cy.value - 90 * u * g.s }, { rotate: -0.7 * u }, { scale: cscale.value }];
  });
  const shellBottom = useDerivedValue<Transforms3d>(() => [{ translateX: cx.value }, { translateY: cy.value + 14 * split.value * g.s }, { scale: cscale.value }]);
  const shellOpacity = useDerivedValue(() => (split.value > 0 ? 1 - split.value : 0));
  const burst = useDerivedValue(() => (split.value > 0 && split.value < 1 ? Math.sin(Math.PI * split.value) * 0.9 : 0));
  const scrim = useDerivedValue(() => card.value * (1 - collect.value));
  const bagX = width - insets.right - BAG.right - BAG.size / 2;
  const bagY = insets.top + TYPE.top + BAG.size / 2;
  const bagT = useDerivedValue<Transforms3d>(() => [{ translateX: bagX }, { translateY: bagY + bag.value * g.s }, { scale: g.s }]);
  const values: GumballValues = {
    balls, knob, capsule, capsuleOpacity, seamRy, flip, inDoor, shellTop, shellBottom, shellOpacity, burst, scrim, bag: bagT,
  };

  // The reward card rises out of the capsule, then flies into the bag on Collect.
  const cardY = my(g, CARD.y);
  const cardStyle = useAnimatedStyle(() => {
    const c = card.value;
    const k = collect.value;
    const e = k * k;
    const rise = reduced ? 1 : c < 1 ? 1 + 2.2 * Math.pow(c - 1, 3) + 1.2 * Math.pow(c - 1, 2) : 1;
    const x = (bagX - width / 2) * e;
    const y = (reduced ? 0 : (1 - c) * 120 * g.s) + (bagY - cardY) * e;
    return {
      opacity: reduced ? c * (1 - k) : Math.min(1, c * 2) * (1 - Math.max(0, k - 0.7) / 0.3),
      transform: [{ translateX: x }, { translateY: y }, { scale: (reduced ? 1 : 0.3 + 0.7 * rise) * (1 - 0.88 * e) }],
    };
  });

  const showCard = phase === PHASE.reward;
  const hint = phase === PHASE.dispensing ? COPY.hintBusy : COPY.hint;

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <GumballScene g={g} radii={radii} v={values} />
          </Canvas>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={COPY.knob}
            accessibilityHint={COPY.knobHint}
            accessibilityState={{ disabled: phase !== PHASE.ready }}
            onPress={() => command('turn')}
            style={[styles.knob, {
              left: g.knobX - (MACHINE.plateSize * g.s) / 2, top: g.knobY - (MACHINE.plateSize * g.s) / 2,
              width: MACHINE.plateSize * g.s, height: MACHINE.plateSize * g.s,
            }]}
          />
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.head, { top: insets.top + TYPE.top, left: TYPE.side, right: TYPE.side + BAG.size + 16 }]}>
        <Text style={styles.title} accessibilityRole="header">{COPY.title}</Text>
        <Text style={styles.sub}>{COPY.sub}</Text>
      </View>
      <View accessible accessibilityLabel={COPY.bag} style={[styles.bag, { left: bagX - BAG.size / 2, top: bagY - BAG.size / 2 }]} />

      <View pointerEvents="none" style={[styles.hintBox, { bottom: insets.bottom + TYPE.bottom }]}>
        <Text style={styles.hint}>{hint}</Text>
      </View>

      <Animated.View
        pointerEvents={showCard ? 'auto' : 'none'}
        accessibilityElementsHidden={!showCard}
        importantForAccessibility={showCard ? 'auto' : 'no-hide-descendants'}
        style={[styles.card, { left: (width - CARD.width) / 2, top: cardY - CARD.height / 2 }, cardStyle]}
      >
        <Text style={styles.cardEyebrow}>{COPY.prize.toUpperCase()}</Text>
        <View style={styles.amountRow}>
          <Text style={styles.amount}>{COPY.amount}</Text>
          <Text style={styles.unit}>{COPY.unit}</Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={COPY.collect} onPress={() => command('collect')} style={styles.button}>
          <Text style={styles.buttonText}>{COPY.collect}</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.wallTop },
  knob: { position: 'absolute', borderRadius: 18 },
  head: { position: 'absolute' },
  title: { color: COLORS.ink, fontFamily: TYPE.titleFamily, fontSize: TYPE.titleSize },
  sub: { color: COLORS.mute, fontSize: TYPE.subSize, marginTop: 4 },
  bag: { position: 'absolute', width: BAG.size, height: BAG.size },
  hintBox: { position: 'absolute', left: TYPE.side, right: TYPE.side, alignItems: 'center' },
  hint: { color: COLORS.mute, fontSize: TYPE.hintSize },
  card: {
    position: 'absolute', width: CARD.width, height: CARD.height, borderRadius: CARD.radius, backgroundColor: COLORS.card,
    padding: CARD.pad, alignItems: 'center', justifyContent: 'space-between',
    shadowColor: '#3B2350', shadowOpacity: 0.18, shadowRadius: 24, shadowOffset: { width: 0, height: 12 },
  },
  cardEyebrow: { color: COLORS.cardAccent, fontSize: 12, letterSpacing: 1.6, fontWeight: '700' },
  amountRow: { flexDirection: 'row', alignItems: 'baseline' },
  amount: { color: COLORS.ink, fontFamily: TYPE.titleFamily, fontSize: TYPE.amountSize },
  unit: { color: COLORS.mute, fontSize: 17, marginLeft: 8 },
  button: {
    alignSelf: 'stretch', height: 52, borderRadius: 26, backgroundColor: COLORS.button, alignItems: 'center', justifyContent: 'center',
  },
  buttonText: { color: COLORS.buttonInk, fontSize: 17, fontWeight: '600' },
});
