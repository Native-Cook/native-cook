/**
 * Answer a call by lifting the receiver: an ivory bakelite handset hangs on its coiled cord and swings, buzzing at each
 * ring. Lift it to answer (it stays up and the call's timer starts), pull it down to decline, or to hang up during the
 * call. The coil bunches when lifted and stretches thin when pulled. Decline and Accept below do the same for a tap or
 * VoiceOver. The cord and the handset are one fixed-step simulation on the UI thread (call-sim.ts).
 */
import { Canvas, Skia, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing, FadeIn, FadeOut, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  PHASE, handsetPoint, handsetRotation, initialSim, input, makeGeometry, preroll, ringState, stepSim, type Input,
} from './call-sim';
import { CallScene, type CallValues } from './CallScene';
import { HAPTIC, PRESS } from './motion';
import { COLORS, CORD, COPY, GLASS, HANDSET, LAYOUT, SPACE } from './theme';

type Props = { autoPlay?: boolean };

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function playHaptic(kind: number) {
  const ignore = () => {};
  if (kind === HAPTIC.grab) void Haptics.selectionAsync().catch(ignore);
  else if (kind === HAPTIC.answer) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore);
  else if (kind === HAPTIC.decline) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === HAPTIC.tick) void Haptics.selectionAsync().catch(ignore);
}

function clock(t: number) {
  const m = Math.floor(t / 60);
  const sec = Math.floor(t % 60);
  return `${m < 10 ? '0' : ''}${m}:${sec < 10 ? '0' : ''}${sec}`;
}

/**
 * The coil as a helix along the chain: a constant number of turns, each with a fixed length of wire, so a stretched
 * link has a longer pitch and a smaller radius. Half-turns turned away from the viewer go in `back`.
 */
function buildCoil(p: number[], scale: number) {
  'worklet';
  const back = Skia.PathBuilder.Make();
  const front = Skia.PathBuilder.Make();
  const n = CORD.links;
  if (p.length < (n + 1) * 2) return { back: back.build(), front: front.build() };
  const perLink = CORD.turns / n;
  const steps = Math.ceil(perLink * 2 * CORD.samples);
  let lastSide = 0;
  for (let i = 0; i < n; i++) {
    const i0 = Math.max(0, i - 1);
    const i3 = Math.min(n, i + 2);
    const x0 = p[i0 * 2]; const y0 = p[i0 * 2 + 1];
    const x1 = p[i * 2]; const y1 = p[i * 2 + 1];
    const x2 = p[i * 2 + 2]; const y2 = p[i * 2 + 3];
    const x3 = p[i3 * 2]; const y3 = p[i3 * 2 + 1];
    // Wire per turn is fixed: the longer the link, the longer the pitch and the smaller the loop.
    const len = Math.hypot(x2 - x1, y2 - y1) / scale;
    const pitch = len / perLink;
    const k = (CORD.wire / (2 * Math.PI)) ** 2 - (pitch / (2 * Math.PI)) ** 2;
    const radius = Math.min(CORD.maxRadius, Math.sqrt(Math.max(k, 1.2))) * scale;
    for (let j = 0; j < steps; j++) {
      if (i > 0 && j === 0) continue;
      const u = j / steps;
      const u2 = u * u;
      const u3 = u2 * u;
      // Catmull-Rom through the chain, and its tangent.
      const cx = 0.5 * (2 * x1 + (-x0 + x2) * u + (2 * x0 - 5 * x1 + 4 * x2 - x3) * u2 + (-x0 + 3 * x1 - 3 * x2 + x3) * u3);
      const cy = 0.5 * (2 * y1 + (-y0 + y2) * u + (2 * y0 - 5 * y1 + 4 * y2 - y3) * u2 + (-y0 + 3 * y1 - 3 * y2 + y3) * u3);
      const tx = 0.5 * ((-x0 + x2) + 2 * (2 * x0 - 5 * x1 + 4 * x2 - x3) * u + 3 * (-x0 + 3 * x1 - 3 * x2 + x3) * u2);
      const ty = 0.5 * ((-y0 + y2) + 2 * (2 * y0 - 5 * y1 + 4 * y2 - y3) * u + 3 * (-y0 + 3 * y1 - 3 * y2 + y3) * u2);
      const tl = Math.hypot(tx, ty) || 1;
      const phase = 2 * Math.PI * perLink * (i + u);
      // The loop seen a little from above: its near half sits lower than its far half.
      const off = radius * Math.sin(phase);
      const dip = radius * 0.32 * Math.cos(phase);
      const x = cx - (ty / tl) * off + (tx / tl) * dip;
      const y = cy + (tx / tl) * off + (ty / tl) * dip;
      const side = Math.cos(phase) >= 0 ? 1 : -1;
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

export function SlideToAnswerCord({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  // Under Reduce Motion there is no demo; a finger still answers.
  const demo = autoPlay && !reduced;
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const [phase, setPhase] = useState<number>(PHASE.ringing);
  const [seconds, setSeconds] = useState(0);
  const [muted, setMuted] = useState(false);

  const sim = useSharedValue(initialSim(g, reduced, demo));
  const ready = useSharedValue(false);
  const chain = useSharedValue<number[]>([]);
  const th = useSharedValue(0);
  const buzz = useSharedValue(0);
  const conn = useSharedValue(0);
  const lightX = useSharedValue(width / 2);
  const lightY = useSharedValue(g.restY);
  const acceptGlow = useSharedValue(0);
  const phaseNow = useSharedValue<number>(PHASE.ringing);
  const secNow = useSharedValue(0);
  const pressRed = useSharedValue(1);
  const pressGreen = useSharedValue(1);
  const pressTo = useCallback((v: SharedValue<number>, down: boolean) => {
    if (reduced && down) return;
    v.set(withTiming(down ? PRESS.scale : 1, { duration: down ? PRESS.in : PRESS.out, easing: EASE_OUT }));
  }, [reduced]);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    const pts: number[] = [];
    for (let i = 0; i <= CORD.links; i++) pts.push(s.px[i], s.py[i]);
    chain.set(pts);
    th.set(s.th);
    buzz.set(ringState(s).buzz);
    conn.set(s.wConn);
    const c = handsetPoint(s, g, HANDSET.comX, HANDSET.comY);
    lightX.set(c.x);
    lightY.set(c.y);
    acceptGlow.set(s.phase === PHASE.ringing && !s.reduced ? ringState(s).pulse : -1);
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
  }, [sim, chain, th, buzz, conn, lightX, lightY, acceptGlow, phaseNow, secNow, g]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    if (!ready.get()) {
      ready.set(true);
      if (demo) preroll(s, g);
    }
    stepSim(s, g, frame.timeSincePreviousFrame / 1000, demo);
    sim.set(s);
    publish();
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  // Only a touch on the receiver becomes a drag; anywhere else (the buttons) is left alone.
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
  const coil = useDerivedValue(() => buildCoil(chain.value, g.s));
  const coilBack = useDerivedValue(() => coil.value.back);
  const coilFront = useDerivedValue(() => coil.value.front);
  const handset = useDerivedValue<Transforms3d>(() => {
    const p = chain.value;
    const n = CORD.links;
    if (p.length === 0) return [];
    return [{ translateX: p[n * 2] }, { translateY: p[n * 2 + 1] }, { rotate: handsetRotation(th.value) + buzz.value }, { scale: g.s }];
  });
  // Its shadow falls on the wall behind, 10 pt right and 22 pt down, a touch larger.
  const shadow = useDerivedValue<Transforms3d>(() => {
    const p = chain.value;
    const n = CORD.links;
    if (p.length === 0) return [];
    return [{ translateX: p[n * 2] + 10 * g.s }, { translateY: p[n * 2 + 1] + 22 * g.s }, { rotate: handsetRotation(th.value) + buzz.value }, { scale: g.s * 1.03 }];
  });
  const values: CallValues = { coilBack, coilFront, handset, shadow, lightX, lightY, conn };

  const buttonsY = height - insets.bottom - LAYOUT.buttonsBottom - LAYOUT.labelSize - LAYOUT.labelGap - LAYOUT.button / 2;
  const pillH = GLASS.pillH;
  const pillTop = buttonsY + GLASS.pillY - pillH / 2;
  const copyTop = height * LAYOUT.copyTopRatio;
  const copyH = LAYOUT.nameLine + LAYOUT.metaGap + LAYOUT.metaLine + LAYOUT.gap + LAYOUT.statusLine;
  const lift = Math.max(0, copyTop + copyH - (pillTop - LAYOUT.copyToPill));
  const copyRise = useAnimatedStyle(() => ({ transform: [{ translateY: -lift * conn.value }] }));
  const controls = useAnimatedStyle(() => ({
    opacity: Math.max(0, (conn.value - 0.5) / 0.5),
    transform: [{ translateY: 12 * (1 - conn.value) }],
  }));
  const acceptLook = useAnimatedStyle(() => ({ opacity: 1 - conn.value, transform: [{ scale: pressGreen.value }] }));
  // Accept breathes with each ring: a soft green halo leaves it.
  const halo = useAnimatedStyle(() => {
    const p = acceptGlow.value;
    return p < 0 ? { opacity: 0 } : { opacity: 0.45 * (1 - p), transform: [{ scale: 1 + 0.5 * p }] };
  });
  const status = phase === PHASE.connected ? clock(seconds) : phase === PHASE.declined ? COPY.declined
    : phase === PHASE.ended ? COPY.ended : COPY.ringing;
  const half = LAYOUT.button / 2;
  const ringing = phase === PHASE.ringing;
  const connected = phase === PHASE.connected;
  // Decline slides to the middle and becomes End as the call connects.
  const redSlide = useAnimatedStyle(() => ({ transform: [{ translateX: LAYOUT.buttonGap * conn.value }] }));
  const redLook = useAnimatedStyle(() => ({ transform: [{ scale: pressRed.value }] }));

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <CallScene g={g} v={values} />
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
            style={[styles.handsetTarget, { left: width / 2 - 75, top: g.restY - 10, width: 150, height: 200 }]}
          />
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.copy, { top: copyTop, height: copyH, left: LAYOUT.side, right: LAYOUT.side }, copyRise]}>
        <Text style={styles.name} accessibilityRole="header">{COPY.name}</Text>
        <Text style={styles.meta}>{COPY.meta}</Text>
        <Animated.Text key={connected ? 'timer' : status} entering={FadeIn.duration(200).delay(120)} exiting={FadeOut.duration(120)} style={styles.status}>
          {status}
        </Animated.Text>
      </Animated.View>

      <Animated.View pointerEvents={connected ? 'box-none' : 'none'} style={[styles.pill, { top: pillTop, height: pillH, left: LAYOUT.side, right: LAYOUT.side }, controls]}>
        {COPY.controls.map((c) => (
          <ControlButton key={c.name} label={c.label} reduced={reduced} selected={c.name === 'mute' ? muted : undefined} on={c.name === 'mute' && muted}
            onPress={() => (c.name === 'mute' ? setMuted((m) => !m) : undefined)}>
            <SymbolView name={c.symbol as never} size={22} tintColor={c.name === 'mute' && muted ? COLORS.controlOnInk : COLORS.glyph} />
          </ControlButton>
        ))}
      </Animated.View>

      <Animated.View style={[styles.button, { left: width / 2 - LAYOUT.buttonGap - half, top: buttonsY - half }, redSlide]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={connected ? 'End call' : 'Decline call'}
          disabled={!ringing && !connected}
          onPress={() => command(connected ? 'end' : 'decline')}
          onPressIn={() => pressTo(pressRed, true)}
          onPressOut={() => pressTo(pressRed, false)}
        >
          <Animated.View style={[styles.disc, { backgroundColor: COLORS.decline, opacity: ringing || connected ? 1 : 0.45 }, redLook]}>
            <SymbolView name={'phone.down.fill' as never} size={LAYOUT.glyph} tintColor={COLORS.glyph} />
          </Animated.View>
        </Pressable>
        <Text style={styles.label}>{connected ? COPY.end : COPY.decline}</Text>
      </Animated.View>

      <Animated.View pointerEvents={ringing ? 'auto' : 'none'} style={[styles.button, { left: width / 2 + LAYOUT.buttonGap - half, top: buttonsY - half }, acceptLook]}>
        <Animated.View pointerEvents="none" style={[styles.halo, halo]} />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Accept call"
          disabled={!ringing}
          onPress={() => command('answer')}
          onPressIn={() => pressTo(pressGreen, true)}
          onPressOut={() => pressTo(pressGreen, false)}
        >
          <View style={[styles.disc, { backgroundColor: COLORS.accept }]}>
            <SymbolView name={'phone.fill' as never} size={LAYOUT.glyph} tintColor={COLORS.glyph} />
          </View>
        </Pressable>
        <Text style={styles.label}>{COPY.accept}</Text>
      </Animated.View>
    </View>
  );
}

/** An in-call control: 0.95 in 120 ms on press, back in 160 ms; it turns white when on. */
function ControlButton({ label, reduced, selected, on, onPress, children }: {
  label: string; reduced: boolean; selected?: boolean; on: boolean; onPress: () => void; children: React.ReactNode;
}) {
  const k = useSharedValue(1);
  const a = useAnimatedStyle(() => ({ transform: [{ scale: k.value }] }));
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={selected === undefined ? undefined : { selected }}
      onPress={onPress}
      onPressIn={() => { if (!reduced) k.set(withTiming(PRESS.scale, { duration: PRESS.in, easing: EASE_OUT })); }}
      onPressOut={() => k.set(withTiming(1, { duration: PRESS.out, easing: EASE_OUT }))}
      style={styles.controlWrap}
    >
      <Animated.View style={[styles.control, on && styles.controlOn, a]}>{children}</Animated.View>
      <Text style={styles.controlLabel}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.page },
  copy: { position: 'absolute', alignItems: 'center' },
  name: { color: COLORS.ink, fontSize: LAYOUT.nameSize, lineHeight: LAYOUT.nameLine, fontWeight: '400', letterSpacing: 0.2 },
  meta: { marginTop: LAYOUT.metaGap, color: COLORS.mute, fontSize: LAYOUT.metaSize, lineHeight: LAYOUT.metaLine },
  status: { marginTop: LAYOUT.gap, color: COLORS.faint, fontSize: LAYOUT.statusSize, lineHeight: LAYOUT.statusLine, fontVariant: ['tabular-nums'] },
  handsetTarget: { position: 'absolute' },
  pill: { position: 'absolute', flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-around' },
  controlWrap: { alignItems: 'center', width: 80 },
  control: {
    width: GLASS.controlH, height: GLASS.controlH, borderRadius: GLASS.controlH / 2, alignItems: 'center', justifyContent: 'center',
    backgroundColor: COLORS.glass, borderWidth: StyleSheet.hairlineWidth, borderColor: COLORS.glassEdge,
  },
  controlOn: { backgroundColor: COLORS.controlOn },
  controlLabel: { marginTop: SPACE.sm, color: COLORS.mute, fontSize: 12, lineHeight: 16, fontWeight: '500' },
  button: { position: 'absolute', width: LAYOUT.button, alignItems: 'center' },
  disc: { width: LAYOUT.button, height: LAYOUT.button, borderRadius: LAYOUT.button / 2, alignItems: 'center', justifyContent: 'center' },
  halo: { position: 'absolute', left: 0, top: 0, width: LAYOUT.button, height: LAYOUT.button, borderRadius: LAYOUT.button / 2, backgroundColor: COLORS.accept },
  label: { marginTop: LAYOUT.labelGap, width: 120, textAlign: 'center', color: COLORS.ink, fontSize: LAYOUT.labelSize, lineHeight: 16, fontWeight: '500' },
});
