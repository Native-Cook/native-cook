import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP, UX, UY, easeInOutCubic, lightFor, loopTime, phiAt } from './capsuleGeometry';
import { drawBackdrop, drawCapsule, drawIndicator, makeFibres } from './capsuleRenderer';

/*
 Spectral Capsule Spin — a "formulating" waiting screen of a supplement app. A clear glass capsule
 turns end over end on near black, one full turn every LOOP s. When a cap swings towards the viewer
 it throws a band of spectrum, then a warm caustic, then shows as a disc with the body's rings
 stacked behind it.

 Drawing: one Skia Canvas; a Picture recorded per frame on the UI thread from a pure function of
 the turn angle (capsuleRenderer.ts), the outlines from a projected 3D capsule (capsuleGeometry.ts).
 The text is static views; nothing in React re-renders while the loop runs or under a finger.

 Touch: drag along the capsule's diagonal to turn it (90 pt a radian); a light haptic each time a
 cap passes end-on. On release it coasts (time constant 0.35 s); 2 s after the last touch it eases
 (0.9 s) to the nearest flat pose and the loop starts again from there.
 Reduced motion: no turn; the capsule rests at 46° with its warm cap, and only the hues and the
 caustic shimmer slowly over LOOP s.
*/

type Props = {
  brand?: string;
  batch?: string;
  label?: string;
  title?: string;
  detail?: string;
  /** Turns by itself, one full turn every LOOP seconds. */
  autoPlay?: boolean;
};

/** Capsule radius and half the seam-to-seam length, as fractions of the screen width. */
const RADIUS_RATIO = 0.172;
const HALF_LENGTH_RATIO = 0.268;
const CENTRE_RATIO = 0.44;
const CANVAS_HEIGHT = 440;
/** Points of drag along the capsule's diagonal for one radian of turn. */
const DRAG_SCALE = 90;
const COAST_TAU = 0.35;
const HOLD_AFTER_TOUCH = 2;
const SETTLE = 0.9;
const REDUCED_PHI = 0.8;
const MAX_SPIN = 14;

type Touch = {
  dragging: boolean;
  base: number;
  phi: number;
  released: boolean;
  releasePhi: number;
  velocity: number;
  releaseAt: number;
  detent: number;
};

const IDLE_TOUCH: Touch = {
  dragging: false,
  base: 0,
  phi: 0,
  released: false,
  releasePhi: 0,
  velocity: 0,
  releaseAt: 0,
  detent: 0,
};

type Pose = { phi: number; hueShift: number; warmGain: number };

function detentOf(phi: number) {
  'worklet';
  return Math.floor((phi - Math.PI / 2) / Math.PI);
}

/** The turn at `now` seconds: the loop, a finger, or the coast and settle after one. */
function poseAt(now: number, touch: Touch, autoPlay: boolean, reduced: boolean): Pose {
  'worklet';
  if (reduced) {
    const w = (2 * Math.PI * loopTime(now)) / LOOP;
    return { phi: REDUCED_PHI, hueShift: 0.6 * Math.sin(w), warmGain: 0.85 + 0.15 * Math.sin(w) };
  }
  if (touch.dragging) return { phi: touch.phi, hueShift: 0, warmGain: 1 };
  if (touch.released) {
    const e = now - touch.releaseAt;
    const coast = (x: number) => touch.releasePhi + touch.velocity * COAST_TAU * (1 - Math.exp(-x / COAST_TAU));
    if (e < HOLD_AFTER_TOUCH) return { phi: coast(e), hueShift: 0, warmGain: 1 };
    const held = coast(HOLD_AFTER_TOUCH);
    const rest = Math.round(held / Math.PI) * Math.PI;
    const k = (e - HOLD_AFTER_TOUCH) / SETTLE;
    if (k < 1) return { phi: held + (rest - held) * easeInOutCubic(k), hueShift: 0, warmGain: 1 };
    if (!autoPlay) return { phi: rest, hueShift: 0, warmGain: 1 };
    return { phi: rest + phiAt(loopTime(e - HOLD_AFTER_TOUCH - SETTLE)), hueShift: 0, warmGain: 1 };
  }
  if (!autoPlay) return { phi: 0, hueShift: 0, warmGain: 1 };
  return { phi: phiAt(loopTime(now)), hueShift: 0, warmGain: 1 };
}

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function SpectralCapsuleSpin({
  brand = 'ORRIN',
  batch = 'Batch 0427',
  label = 'FORMULATING',
  title = 'Your daily blend',
  detail = 'Sealing four actives into one clear capsule.',
  autoPlay = false,
}: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const fibres = useMemo(() => makeFibres(), []);

  const now = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const touch = useSharedValue<Touch>(IDLE_TOUCH);

  useFrameCallback((info) => {
    if (startedAt.get() < 0) startedAt.set(info.timestamp);
    const t = touch.get();
    // At rest without autoPlay nothing moves, so the picture is not recorded again.
    const settled = !t.dragging && (!t.released || now.get() - t.releaseAt > HOLD_AFTER_TOUCH + SETTLE + 0.1);
    if (!autoPlay && !reduced && settled && now.get() > 0) return;
    now.set((info.timestamp - startedAt.get()) / 1000);
  });

  const centreY = height * CENTRE_RATIO;
  const radius = width * RADIUS_RATIO;
  const halfLength = width * HALF_LENGTH_RATIO;
  const bottom = height - insets.bottom;

  const pose = useDerivedValue(() => poseAt(now.get(), touch.get(), autoPlay, reduced));
  const picture = useDerivedValue(() => {
    const p = pose.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawBackdrop(canvas, width, height, centreY, lightFor(Math.abs(Math.sin(p.phi))).warm * p.warmGain);
    canvas.save();
    canvas.translate(0, centreY - CANVAS_HEIGHT / 2);
    drawCapsule(canvas, fibres, width, CANVAS_HEIGHT, p.phi, p.hueShift, p.warmGain, radius, halfLength);
    canvas.restore();
    drawIndicator(canvas, width / 2, bottom - 40, p.phi);
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(4)
        .enabled(!reduced)
        .onStart(() => {
          const base = pose.get().phi;
          touch.set({ ...IDLE_TOUCH, dragging: true, base, phi: base, detent: detentOf(base) });
        })
        .onUpdate((e) => {
          const t = touch.get();
          if (!t.dragging) return;
          const along = e.translationX * UX + e.translationY * UY;
          const phi = t.base - along / DRAG_SCALE;
          const detent = detentOf(phi);
          if (detent !== t.detent) scheduleOnRN(tick);
          touch.set({ ...t, phi, detent });
        })
        .onEnd((e) => {
          const t = touch.get();
          if (!t.dragging) return;
          const along = e.velocityX * UX + e.velocityY * UY;
          const velocity = Math.min(Math.max(-along / DRAG_SCALE, -MAX_SPIN), MAX_SPIN);
          touch.set({ ...t, dragging: false, released: true, releasePhi: t.phi, velocity, releaseAt: now.get() });
        }),
    [reduced, pose, touch, now],
  );

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="image"
        accessibilityLabel="A clear capsule turning end over end"
        accessibilityHint="Drag to turn it"
      >
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
        <View style={[styles.topRow, { top: insets.top + 24 - 10 }]} pointerEvents="none">
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.batch}>{batch}</Text>
        </View>
        <View style={[styles.line, { top: bottom - 148 - 8, height: 16 }]} pointerEvents="none">
          <Text style={styles.label}>{label}</Text>
        </View>
        <View style={[styles.line, { top: bottom - 114 - 17, height: 34 }]} pointerEvents="none">
          <Text style={styles.title}>{title}</Text>
        </View>
        <View style={[styles.line, { top: bottom - 80 - 10, height: 20 }]} pointerEvents="none">
          <Text style={styles.detail}>{detail}</Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#040508' },
  topRow: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brand: { color: '#C9CFDB', fontSize: 13, lineHeight: 20, fontWeight: '600', letterSpacing: 3 },
  batch: { color: '#6E7587', fontSize: 13, lineHeight: 20, fontWeight: '400', fontVariant: ['tabular-nums'] },
  line: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  label: { color: '#7C8496', fontSize: 12, lineHeight: 16, fontWeight: '600', letterSpacing: 2.4, textAlign: 'center' },
  title: { color: '#F1F3F8', fontSize: 28, lineHeight: 34, fontWeight: '600', textAlign: 'center' },
  detail: { color: '#8C93A4', fontSize: 15, lineHeight: 20, fontWeight: '400', textAlign: 'center', maxWidth: 320 },
});
