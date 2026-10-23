import { Canvas, Skia, useImage, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  Easing, FadeIn, FadeOut, interpolateColor, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  PHASE, clamp, dragDistance, handsetPoint, handsetRotation, initialSim, input, makeGeometry, preroll, ringState,
  stepSim, type Input,
} from './call-sim';
import { CallScene, type CallValues } from './CallScene';
import { makeHandsetPaths } from './handset-paths';
import { CHEVRON, DRAG, HAPTIC, PRESS, QA_SPEED, REPLICA_HOLD, REPLICA_SWING, RING, WASH } from './motion';
import { COLORS, CORD, COPY, GLASS, HANDSET, LAYOUT, REPLICA, SPACE } from './theme';

const GLASS_PILL = { y: GLASS.pillY, h: GLASS.pillH };

type Props = { autoPlay?: boolean };

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.answer) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  else if (kind === HAPTIC.decline) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.tick) Haptics.selectionAsync();
}

function clock(t: number) {
  const m = Math.floor(t / 60);
  const sec = Math.floor(t % 60);
  return `${m < 10 ? '0' : ''}${m}:${sec < 10 ? '0' : ''}${sec}`;
}

export function SlideToAnswerCord({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const paths = useMemo(() => makeHandsetPaths(), []);
  const pink = useImage(require('./assets/handset.png'));
  const green = useImage(require('./assets/handset-green.png'));
  const shell = useImage(require('./assets/handset-shell.png'));
  const shellGreen = useImage(require('./assets/handset-shell-green.png'));
  // Replica: the source's handset traced from its frames (light over the backdrop) at the swing's left end, middle and right end.
  const srcA = useImage(require('./assets/handset-src-a.png'));
  const srcRest = useImage(require('./assets/handset-src-rest.png'));
  const srcB = useImage(require('./assets/handset-src-b.png'));
  const images = useMemo(() => ({ pink, green, shell, shellGreen, srcA, srcRest, srcB }), [pink, green, shell, shellGreen, srcA, srcRest, srcB]);
  const [phase, setPhase] = useState<number>(PHASE.ringing);
  const [seconds, setSeconds] = useState(0);

  const sim = useSharedValue(initialSim(g, reduced, autoPlay));
  const ready = useSharedValue(false);
  const chain = useSharedValue<number[]>([]);
  const th = useSharedValue(0);
  const buzz = useSharedValue(0);
  const conn = useSharedValue(0);
  const morph = useSharedValue(0);
  const wash = useSharedValue<number[]>([0, 0, 0]);
  const chev = useSharedValue(-1);
  const [muted, setMuted] = useState(false);
  const off = useSharedValue(0);
  const pulseR = useSharedValue(0);
  const pulseO = useSharedValue(0);
  const pulseX = useSharedValue(0);
  const pulseY = useSharedValue(0);
  const acceptGlow = useSharedValue(0);
  const declineGlow = useSharedValue(0);
  const phaseNow = useSharedValue<number>(PHASE.ringing);
  const secNow = useSharedValue(0);
  const swing = useSharedValue(0); // time within the source's swing period
  const src = useSharedValue(1); // how far the handset is on the source's swing
  const pressRed = useSharedValue(1);
  const pressGreen = useSharedValue(1);
  // Press feedback on the drawn discs: 0.95 in 120 ms, back in 160 ms (strong ease-out both ways).
  const pressTo = useCallback((v: SharedValue<number>, down: boolean) => {
    if (reduced && down) return;
    v.set(withTiming(down ? PRESS.scale : 1, { duration: down ? PRESS.in : PRESS.out, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [reduced]);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    const pts: number[] = [];
    for (let i = 0; i <= CORD.links; i++) pts.push(s.px[i], s.py[i]);
    chain.set(pts);
    const ring = ringState(s);
    th.set(s.th);
    swing.set(s.clock % REPLICA_SWING.period);
    src.set(REPLICA ? 1 : s.look);
    buzz.set(ring.buzz);
    conn.set(s.wConn);
    off.set(s.wOff);
    const c = handsetPoint(s, g, HANDSET.comX, HANDSET.comY);
    // Morph eases with the connected weight (≈ 0.25 s, exponential approach: no ease-in).
    morph.set(s.reduced ? (s.phase === PHASE.connected ? 1 : 0) : s.wConn);
    const fresh = s.phase !== PHASE.ringing && s.phaseT < WASH.duration && !s.reduced;
    wash.set(fresh ? [c.x, c.y, s.phaseT / WASH.duration] : [0, 0, 0]);
    chev.set(s.phase === PHASE.ringing && !s.grab && !s.reduced ? (s.clock % CHEVRON.period) / CHEVRON.period : -1);
    pulseX.set(c.x);
    pulseY.set(c.y);
    pulseR.set(ring.pulse < 0 ? 0 : (RING.pulseFrom + (RING.pulseTo - RING.pulseFrom) * (1 - Math.pow(1 - ring.pulse, 2))) * g.s);
    pulseO.set(ring.pulse < 0 ? 0 : 0.55 * (1 - ring.pulse));
    const d = s.grab ? dragDistance(s, g) : 0;
    acceptGlow.set(s.phase === PHASE.ringing ? clamp(-d / (DRAG.answer * g.s)) : 0);
    declineGlow.set(clamp(d / (DRAG.decline * g.s)));
    if (s.phase !== phaseNow.get()) {
      phaseNow.set(s.phase);
      scheduleOnRN(setPhase, s.phase);
    }
    const sec = Math.floor(s.callT);
    if (sec !== secNow.get()) {
      secNow.set(sec);
      scheduleOnRN(setSeconds, sec);
    }
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(playHaptic, s.events[i]);
    s.events = [];
  }, [sim, chain, th, buzz, conn, off, morph, wash, chev, pulseX, pulseY, pulseR, pulseO, acceptGlow, declineGlow, phaseNow, secNow, g, swing, src]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    if (!ready.get()) {
      ready.set(true);
      if (autoPlay) preroll(s, g);
      // QA only: run to the held time once and freeze there.
      if (REPLICA_HOLD >= 0) stepSim(s, g, REPLICA_HOLD, autoPlay, true);
    }
    if (REPLICA_HOLD < 0) stepSim(s, g, (frame.timeSincePreviousFrame / 1000) * QA_SPEED, autoPlay);
    sim.set(s);
    publish();
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  // Only a touch on the handset becomes a drag; anywhere else (the buttons) is left alone.
  const hits = useCallback((x: number, y: number) => {
    'worklet';
    const p = handsetPoint(sim.get(), g, HANDSET.comX, HANDSET.comY);
    return Math.hypot(x - p.x, y - p.y) < (HANDSET.hit / 2) * g.s;
  }, [sim, g]);
  const gesture = useMemo(() => Gesture.Pan()
    .manualActivation(true)
    .maxPointers(1)
    .onTouchesDown((e, manager) => {
      const t = e.changedTouches[0];
      if (hits(t.x, t.y)) {
        act({ type: 'down', x: t.x, y: t.y });
        manager.activate();
      } else {
        manager.fail();
      }
    })
    .onUpdate((e) => act({ type: 'move', x: e.x, y: e.y, vx: e.velocityX, vy: e.velocityY }))
    .onEnd((e) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY }))
    .onTouchesCancelled(() => act({ type: 'up', vx: 0, vy: 0 })), [act, hits]);

  const command = useCallback((action: 'answer' | 'decline' | 'end') => scheduleOnUI(act, { type: 'command', action }), [act]);

  // Skia values.
  const coil = useDerivedValue(() => buildCoil(chain.value, g.s, swing.value));
  const coilBack = useDerivedValue(() => coil.value.back);
  const coilFront = useDerivedValue(() => coil.value.front);
  const handset = useDerivedValue<Transforms3d>(() => {
    const p = chain.value;
    const n = CORD.links;
    if (p.length === 0) return [];
    return [{ translateX: p[n * 2] }, { translateY: p[n * 2 + 1] }, { rotate: handsetRotation(th.value) + buzz.value }, { scale: g.s }];
  });
  const face = useDerivedValue(() => {
    const lit = interpolateColor(conn.value, [0, 1], [COLORS.ring, COLORS.connected]);
    return interpolateColor(off.value, [0, 1], [lit, COLORS.off]) as string;
  });
  const faceDeep = useDerivedValue(() => {
    const lit = interpolateColor(conn.value, [0, 1], [COLORS.ringDeep, COLORS.connectedDeep]);
    return interpolateColor(off.value, [0, 1], [lit, COLORS.offDeep]) as string;
  });
  const ground = useDerivedValue(() => {
    const lit = interpolateColor(conn.value, [0, 1], [COLORS.ground, COLORS.groundConnected]);
    return interpolateColor(off.value, [0, 1], [lit, COLORS.groundEnded]) as string;
  });
  const glowOn = useDerivedValue(() => 1 - 0.8 * off.value);
  const values: CallValues = {
    coilBack, coilFront, handset, face, faceDeep, ground, pulseR, pulseO, pulseX, pulseY, glowOn,
    acceptGlow, declineGlow, conn, off, morph, wash, chev, swing, src, pressRed, pressGreen,
  };
  // In-call controls appear inside the pill as the morph completes.
  const pillIcons = useAnimatedStyle(() => ({ opacity: Math.max(0, (morph.value - 0.6) / 0.4), transform: [{ scale: 0.95 + 0.05 * morph.value }] }));

  const buttonsY = height - insets.bottom - LAYOUT.buttonsBottom - LAYOUT.labelSize - LAYOUT.labelGap - LAYOUT.button / 2;
  const status = phase === PHASE.connected ? clock(seconds) : phase === PHASE.declined ? COPY.declined
    : phase === PHASE.ended ? COPY.ended : COPY.ringing;
  const half = LAYOUT.button / 2;
  const ringing = phase === PHASE.ringing;
  const connected = phase === PHASE.connected;

  return (
    <View style={styles.root}>
      <StatusBar style="light" hidden={REPLICA} />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <CallScene g={g} paths={paths} v={values} buttonsY={buttonsY} images={images} />
          </Canvas>
          <View
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={COPY.a11yHandset}
            accessibilityHint={COPY.a11yHint}
            accessibilityActions={[{ name: 'increment', label: COPY.accept }, { name: 'decrement', label: COPY.decline }]}
            onAccessibilityAction={(e) => {
              if (e.nativeEvent.actionName === 'increment') command('answer');
              else command(connected ? 'end' : 'decline');
            }}
            style={[styles.handsetTarget, { left: width / 2 - 75, top: g.restY - 10, width: 150, height: 170 }]}
          />
        </View>
      </GestureDetector>

      {REPLICA ? null : (<>
      <View pointerEvents="none" style={[styles.copy, { top: height * LAYOUT.copyTopRatio, left: LAYOUT.side, right: LAYOUT.side }]}>
        {/* Caller identity: the name and real meta (the handset hangs where an avatar would be). */}
        <Text style={styles.name} accessibilityRole="header">{COPY.name}</Text>
        <Text style={styles.meta}>{COPY.meta}</Text>
        <Animated.Text key={phase === PHASE.connected ? 'timer' : status} entering={FadeIn.duration(200)} exiting={FadeOut.duration(120)} style={styles.status}>
          {status}
        </Animated.Text>
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel={connected ? 'End call' : 'Decline call'}
        disabled={!ringing && !connected}
        onPress={() => command(connected ? 'end' : 'decline')}
        onPressIn={() => pressTo(pressRed, true)}
        onPressOut={() => pressTo(pressRed, false)}
        style={[styles.button, {
          left: (connected ? width / 2 : width / 2 - LAYOUT.buttonGap * g.s) - half,
          top: buttonsY - half,
        }]}
      >
        <Text style={[styles.label, { top: LAYOUT.button + LAYOUT.labelGap }]}>{connected ? COPY.end : COPY.decline}</Text>
      </Pressable>
      {/* The pill's controls (the pill itself is the morphed Accept disc in the canvas). */}
      <Animated.View pointerEvents={connected ? 'box-none' : 'none'} style={[styles.pill, { top: buttonsY + GLASS_PILL.y - GLASS_PILL.h / 2, height: GLASS_PILL.h }, pillIcons]}>
        {COPY.controls.map((c) => (
          <ControlButton key={c.name} label={c.label} reduced={reduced} selected={c.name === 'mute' ? muted : undefined} on={c.name === 'mute' && muted}
            onPress={() => (c.name === 'mute' ? setMuted((m) => !m) : undefined)}>
            <SymbolView name={c.symbol as never} size={20} tintColor={c.name === 'mute' && muted ? '#16122E' : '#FFFFFF'} />
            <Text style={[styles.controlLabel, c.name === 'mute' && muted && styles.controlLabelOn]}>{c.label}</Text>
          </ControlButton>
        ))}
      </Animated.View>
      {!connected ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Accept call"
          disabled={!ringing}
          onPress={() => command('answer')}
          onPressIn={() => pressTo(pressGreen, true)}
          onPressOut={() => pressTo(pressGreen, false)}
          style={[styles.button, { left: width / 2 + LAYOUT.buttonGap * g.s - half, top: buttonsY - half }]}
        >
          <Text style={[styles.label, { top: LAYOUT.button + LAYOUT.labelGap }]}>{COPY.accept}</Text>
        </Pressable>
      ) : null}
      </>)}
    </View>
  );
}

/**
 * The coil as a helix along the chain: a constant number of turns, each with a fixed length of wire, so a
 * stretched link has a longer pitch and a smaller radius. Half-turns facing away go in `back`.
 */
function buildCoil(p: number[], scale: number, swingT: number) {
  'worklet';
  // The source's wave rolls a little along the cord as it swings (measured at its three frames).
  let roll = 0;
  {
    const K = CORD.replicaRoll;
    for (let i = 0; i < K.length - 1; i++) {
      if (swingT >= K[i][0] && swingT <= K[i + 1][0]) roll = K[i][1] + ((K[i + 1][1] - K[i][1]) * (swingT - K[i][0])) / (K[i + 1][0] - K[i][0]);
    }
  }
  const back = Skia.PathBuilder.Make();
  const front = Skia.PathBuilder.Make();
  const n = CORD.links;
  if (p.length < (n + 1) * 2) return { back: back.build(), front: front.build() };
  // The source's cord: a flat wave of fixed height (no helix, nothing behind).
  const perLink = CORD.replicaTurns / n;
  const steps = Math.ceil(perLink * 2 * CORD.samples);
  let lastSide = 0;
  for (let i = 0; i < n; i++) {
    const i0 = Math.max(0, i - 1);
    const i3 = Math.min(n, i + 2);
    const x0 = p[i0 * 2]; const y0 = p[i0 * 2 + 1];
    const x1 = p[i * 2]; const y1 = p[i * 2 + 1];
    const x2 = p[i * 2 + 2]; const y2 = p[i * 2 + 3];
    const x3 = p[i3 * 2]; const y3 = p[i3 * 2 + 1];
    const radius = CORD.replicaWave * scale;
    for (let k = 0; k < steps; k++) {
      if (i > 0 && k === 0) continue;
      const u = k / steps;
      const u2 = u * u;
      const u3 = u2 * u;
      // Catmull-Rom through the chain, and its tangent.
      const cx = 0.5 * (2 * x1 + (-x0 + x2) * u + (2 * x0 - 5 * x1 + 4 * x2 - x3) * u2 + (-x0 + 3 * x1 - 3 * x2 + x3) * u3);
      const cy = 0.5 * (2 * y1 + (-y0 + y2) * u + (2 * y0 - 5 * y1 + 4 * y2 - y3) * u2 + (-y0 + 3 * y1 - 3 * y2 + y3) * u3);
      const tx = 0.5 * ((-x0 + x2) + 2 * (2 * x0 - 5 * x1 + 4 * x2 - x3) * u + 3 * (-x0 + 3 * x1 - 3 * x2 + x3) * u2);
      const ty = 0.5 * ((-y0 + y2) + 2 * (2 * y0 - 5 * y1 + 4 * y2 - y3) * u + 3 * (-y0 + 3 * y1 - 3 * y2 + y3) * u2);
      const tl = Math.hypot(tx, ty) || 1;
      const phase = 2 * Math.PI * perLink * (i + u) + CORD.replicaPhase + roll;
      const off = radius * Math.sin(phase);
      const x = cx - (ty / tl) * off;
      const y = cy + (tx / tl) * off;
      const side = 1;
      const path = side > 0 ? front : back;
      if (side !== lastSide) {
        path.moveTo(x, y);
        lastSide = side;
      } else {
        path.lineTo(x, y);
      }
    }
  }
  return { back: back.build(), front: front.build() };
}

/** An in-call control: 0.95 in 120 ms on press, back in 160 ms; its own surface turns white when on. */
function ControlButton({ label, reduced, selected, on, onPress, children }: {
  label: string; reduced: boolean; selected?: boolean; on: boolean; onPress: () => void; children: React.ReactNode;
}) {
  const k = useSharedValue(1);
  const a = useAnimatedStyle(() => ({ transform: [{ scale: k.value }] }));
  const ease = Easing.bezier(0.23, 1, 0.32, 1);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={selected === undefined ? undefined : { selected }}
      onPress={onPress}
      onPressIn={() => { if (!reduced) k.set(withTiming(PRESS.scale, { duration: PRESS.in, easing: ease })); }}
      onPressOut={() => k.set(withTiming(1, { duration: PRESS.out, easing: ease }))}
    >
      <Animated.View style={[styles.control, on && styles.controlOn, a]}>{children}</Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  meta: { marginTop: SPACE.xs, color: 'rgba(244,242,255,0.5)', fontSize: 13 },
  pill: { position: 'absolute', left: SPACE.lg, right: SPACE.lg, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around' },
  control: { alignItems: 'center', justifyContent: 'center', minWidth: 64, height: 52, borderRadius: 26 },
  controlOn: { backgroundColor: 'rgba(255,255,255,0.9)' },
  controlLabel: { marginTop: SPACE.xs, color: 'rgba(255,255,255,0.75)', fontSize: 10, fontWeight: '600' },
  controlLabelOn: { color: '#16122E' },
  root: { flex: 1, backgroundColor: COLORS.skyTop },
  copy: { position: 'absolute', alignItems: 'center' },
  name: { color: COLORS.ink, fontSize: LAYOUT.nameSize, lineHeight: LAYOUT.nameLine, fontWeight: '600', letterSpacing: -0.4 },
  status: { marginTop: LAYOUT.gap, color: COLORS.mute, fontSize: LAYOUT.statusSize, lineHeight: LAYOUT.statusLine, fontVariant: ['tabular-nums'] },
  handsetTarget: { position: 'absolute' },
  button: { position: 'absolute', width: LAYOUT.button, height: LAYOUT.button, borderRadius: LAYOUT.button / 2, alignItems: 'center' },
  label: { position: 'absolute', width: 120, textAlign: 'center', color: COLORS.label, fontSize: LAYOUT.labelSize },
});
