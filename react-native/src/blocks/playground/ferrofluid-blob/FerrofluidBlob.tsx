import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { FERRO_SKSL } from './FerrofluidBlobShader';
import {
  createFerro,
  EVENT_PULSE,
  EVENT_RISE,
  type FerroSim,
  ferroCancelScripted,
  ferroScript,
  ferroStep,
  ferroTouchDown,
  ferroTouchMove,
  ferroTouchUp,
  LOOP,
  MAX_FRAME,
} from './FerrofluidBlobSim';

type Props = {
  /** Sweeps a magnet over the dish, pulses it, brings in a second one, and lets the fluid settle. */
  autoPlay?: boolean;
};

// The camera looks down at the table from CAMERA_ELEVATION degrees above the horizon, so the round
// dish reads as an ellipse and the spikes stand up off the screen plane.
const CAMERA_ELEVATION = 38;
const SIN_E = Math.sin((CAMERA_ELEVATION * Math.PI) / 180);
const COS_E = Math.cos((CAMERA_ELEVATION * Math.PI) / 180);
// The dish: its radius on the table (a share of the width, at most DISH_MAX pt), and where its
// centre sits on the screen (a share of the height).
const DISH_SHARE = 0.47;
const DISH_MAX = 215;
const DISH_AT = 0.56;
// The fluid's resting level in pt, used to land a finger on its surface.
const LEVEL = 6;
// After a touch the loop waits this long, then starts over from rest.
const USER_HOLD = 3.5;

const INK = '#E9E4DA';
const INK_SOFT = 'rgba(233,228,218,0.5)';
const BACKGROUND = '#0B0B0C';

export function FerrofluidBlob({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(FERRO_SKSL), []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const cx = width / 2;
  const cy = height * DISH_AT;
  const dishR = Math.min(width * DISH_SHARE, DISH_MAX);

  const sim = useSharedValue<FerroSim | null>(null);
  const tick = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const feel = (events: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (events & EVENT_PULSE) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else if (events & EVENT_RISE) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  useFrameCallback((frame) => {
    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_FRAME);
    if (dishR <= 0) return;
    let s = sim.get();
    if (!s) {
      s = createFerro(reducedMotion);
      sim.set(s);
    }
    // A finger resting on the dish keeps the script away.
    for (let i = 0; i < s.m.length; i += 1) if (s.m[i].touch !== -1 && !s.m[i].scripted) hold.set(USER_HOLD);
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const before = clock.get();
      const now = (before + dt) % LOOP;
      clock.set(now);
      ferroScript(s, before, now, dishR);
    }
    ferroStep(s, dt);
    if (s.events !== 0) {
      if (s.eventsByUser) scheduleOnRN(feel, s.events);
      s.events = 0;
      s.eventsByUser = false;
    }
    tick.set(tick.get() + 1);
  });

  // Named worklet functions (not inline arrows), so the React Compiler cannot hoist them out of
  // the gesture callbacks.

  /** A screen point, landed on the fluid's surface, in ground coordinates (dish centre = 0). */
  function toGround(x: number, y: number) {
    'worklet';
    return [x - cx, (y - cy + LEVEL * COS_E) / SIN_E];
  }

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
    const s = sim.get();
    if (s) ferroCancelScripted(s);
  }

  /** Lets go of every magnet a real finger still holds (a gesture that ended without its touches). */
  function releaseFingers() {
    'worklet';
    const s = sim.get();
    if (!s) return;
    for (let i = 0; i < s.m.length; i += 1) if (s.m[i].touch !== -1 && !s.m[i].scripted) s.m[i].touch = -1;
  }

  // One manual gesture sees every finger from the moment it lands, so the field rises at once
  // and a second finger raises a second crown.
  const touch = Gesture.Manual()
    .onTouchesDown((event, manager) => {
      manager.activate();
      touched();
      const s = sim.get();
      if (!s) return;
      for (const t of event.changedTouches) {
        const [gx, gy] = toGround(t.x, t.y);
        ferroTouchDown(s, t.id, gx, gy, false, 1);
      }
    })
    .onTouchesMove((event) => {
      const s = sim.get();
      if (!s) return;
      hold.set(USER_HOLD);
      for (const t of event.changedTouches) {
        const [gx, gy] = toGround(t.x, t.y);
        ferroTouchMove(s, t.id, gx, gy, 1);
      }
    })
    .onTouchesUp((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) ferroTouchUp(s, t.id);
      let held = 0;
      if (s) for (let i = 0; i < s.m.length; i += 1) if (s.m[i].touch !== -1 && !s.m[i].scripted) held += 1;
      if (held === 0) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) ferroTouchUp(s, t.id);
      manager.end();
    })
    // However the gesture ends, no magnet stays held by a finger that is gone (it would keep its
    // crown up and the script away for good).
    .onFinalize(() => {
      releaseFingers();
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const s = sim.get();
    const a = s?.m[0];
    const b = s?.m[1];
    return {
      center: [cx, cy],
      size: [Math.max(1, width), Math.max(1, height)],
      dishR,
      cam: [SIN_E, COS_E],
      magA: a ? [a.x, a.y, a.field, a.spike] : [0, 0, 0, 0],
      magB: b ? [b.x, b.y, b.field, b.spike] : [0, 0, 0, 0],
      lean: [a?.leanX ?? 0, a?.leanY ?? 0, b?.leanX ?? 0, b?.leanY ?? 0],
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <View style={[styles.root, { backgroundColor: BACKGROUND }]} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A dish of ferrofluid. Touch to raise spikes, use two fingers for two crowns, tap to pulse."
        >
          {size && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 22 }]}>
        <Text style={styles.kicker}>FERROFLUID</Text>
        <Text style={styles.title}>Magnetic study in a porcelain dish</Text>
      </View>
      <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 26 }]}>
        Hold to raise · Two fingers, two crowns · Tap to pulse
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 5 },
  kicker: { color: INK, fontSize: 11, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  hint: {
    position: 'absolute',
    left: 24,
    right: 24,
    textAlign: 'center',
    color: INK_SOFT,
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
});
