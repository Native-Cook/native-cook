import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CAMERAS, STAGGER, easeInOutBack, loopP, smoothstep, springAt } from './camera-model';
import { type Shown, cameraFaces, drawFace, drawScene, m3mul, m3x, m3y } from './render';

/*
 Camera Carousel Fan — a camera shelf: a glossy 3D camera floats over black with eight instant
 photos orbiting it on a tilted ring; picking another camera tumbles the camera end over end and
 the photos flip over to that camera's film look. A port of the SwiftUI block
 (CameraCarouselFan.swift); the numbers are the same.

 The camera is drawn, not loaded: each model is a handful of extruded rounded rectangles and
 circles (camera-model.ts), rotated and projected in perspective (focal 760 pt) and painted back
 to front with smooth Lambert + Blinn shading; lenses get a glass gradient (render.ts).

 The selection is one number p (0 … 4, the camera index). Everything follows from it:
   tumble    φ = (p − round p) · 180°, about the x axis, with 0.35 φ about y; the model swaps at
             the edge-on moment (|p − round p| = ½). Seen from 12° above and 10° to the left, idle
             yaw ±7° (period 4 s) and a 4 pt bob (period 4 s).
   ring      radius 172, tilted 15°, turns by −135° per camera (+ ±6° idle sway, period 8 s); each
             photo faces the viewer with a yaw of 0.55·sin(angle) and its own tilt.
   photos    each photo flips about its own vertical axis by (p − round p) · 180°, 0.06 s after the
             one before it, and shows the round(p) camera's film look.
   labels    name and spec fade and rise 6 pt between cameras; the thumbnail strip slides 72 pt per
             camera under a fixed outline, the nearest thumbnail scaled to 1.15.

 autoPlay timeline (LOOP = 8 s), every move easeInOutBack (≈4 % wind-up and overshoot):
   0.0 – 1.2 rest on OKA 35 · 1.2 – 2.4 flip to RF-2 · 2.4 – 3.6 rest · 3.6 – 4.8 flip to PIP ·
   4.8 – 5.9 rest · 5.9 – 7.4 spin back through RF-2 to OKA 35 · 7.4 – 8.0 rest (= 0.0).

 Touch: dragging sideways turns the camera with the finger (a screen's 80 % per camera, rubber band
 past the ends); release springs (period 0.55 s, damping 0.72) to the nearest camera, thrown 0.18 s
 ahead. Tapping the camera goes to the next one; tapping a thumbnail goes to it. A light haptic on
 each camera passed and on landing. 2 s after the last touch the shelf eases back to OKA 35 and the
 loop starts over.
 Reduced motion: no loop, no tumble, no orbit and no flips; changing camera cross-fades.
 */

const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });
const BG = '#080809';

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  idleOrigin: SharedValue<number>;
  takeoverAt: SharedValue<number>;
  manualP: SharedValue<number>;
  /** [when, from, velocity, to], or []. */
  spring: SharedValue<number[]>;
  /** [when, from, duration], or []. */
  returning: SharedValue<number[]>;
  dragging: SharedValue<number>;
  dragStart: SharedValue<number>;
  moved: SharedValue<number>;
  last: SharedValue<number>;
  resumeAt: SharedValue<number>;
};

function indexOf(p: number) {
  'worklet';
  return Math.min(4, Math.max(0, Math.round(p)));
}

function shownAt(m: Motion, at: number, reduced: boolean, autoPlay: boolean): Shown {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start && !reduced) {
    const t = at - start;
    const photoP: number[] = [];
    for (let k = 0; k < 8; k++) photoP.push(loopP(t - k * STAGGER));
    return { p: loopP(t), clock: t, idle: 1, photoP };
  }
  const r = m.returning.get();
  if (r.length === 3 && start >= 0) {
    const u = (at - r[0]) / r[2];
    const p = r[1] + (0 - r[1]) * easeInOutBack(u);
    return { p, clock: at - start, idle: smoothstep(0, 1, u), photoP: new Array(8).fill(p) };
  }
  let p = m.manualP.get();
  const sp = m.spring.get();
  if (sp.length === 4 && m.dragging.get() === 0) p = springAt(at - sp[0], sp[1], sp[2], sp[3]);
  const take = m.takeoverAt.get();
  const idle = take >= 0 ? 1 - smoothstep(0, 0.4, at - take) : autoPlay ? 0 : 1;
  return { p, clock: at - m.idleOrigin.get(), idle: reduced ? 0 : idle, photoP: new Array(8).fill(p) };
}

function tactile(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function CameraCarouselFan({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [index, setIndex] = useState(0);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    idleOrigin: useSharedValue(0),
    takeoverAt: useSharedValue(-1),
    manualP: useSharedValue(0),
    spring: useSharedValue<number[]>([]),
    returning: useSharedValue<number[]>([]),
    dragging: useSharedValue(0),
    dragStart: useSharedValue(0),
    moved: useSharedValue(0),
    last: useSharedValue(0),
    resumeAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      m.idleOrigin.set(now);
      if (autoPlay && !reduced) m.autoStart.set(now);
    }
    m.clock.set(now);
    // 2 s after the last touch: ease back to the first camera, then the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      const from = shownAt(m, now, reduced, autoPlay).p;
      const dur = 0.5 + 0.3 * Math.abs(from);
      m.manualP.set(from);
      m.spring.set([]);
      m.returning.set([now, from, dur]);
      m.autoStart.set(now + dur);
      m.takeoverAt.set(-1);
    }
  });

  const shown = useDerivedValue(() => shownAt(m, m.clock.get(), reduced, autoPlay));
  const p = useDerivedValue(() => shown.get().p);

  useAnimatedReaction(
    () => indexOf(p.get()),
    (i, previous) => {
      if (i !== previous) scheduleOnRN(setIndex, i);
    },
  );

  const takeOver = (now: number) => {
    'worklet';
    m.resumeAt.set(-1);
    const s = shownAt(m, now, reduced, autoPlay);
    m.manualP.set(s.p);
    const start = m.autoStart.get();
    if (start >= 0) m.idleOrigin.set(start);
    if (start >= 0 || m.returning.get().length > 0 || m.takeoverAt.get() < 0) m.takeoverAt.set(now);
    m.autoStart.set(-1);
    m.returning.set([]);
    m.spring.set([]);
  };

  const settle = (now: number, target: number, velocity: number) => {
    'worklet';
    m.last.set(target);
    if (reduced) {
      m.manualP.set(target);
      m.spring.set([]);
    } else {
      m.spring.set([now, m.manualP.get(), velocity, target]);
    }
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  const width = size.width;
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin(() => {
          takeOver(m.clock.get());
          m.dragging.set(1);
          m.dragStart.set(m.manualP.get());
          m.moved.set(0);
          m.last.set(indexOf(m.manualP.get()));
        })
        .onUpdate((e) => {
          if (m.moved.get() === 0 && Math.abs(e.translationX) > 6) m.moved.set(1);
          if (m.moved.get() === 0 || width <= 0) return;
          let next = m.dragStart.get() - e.translationX / (width * 0.8);
          // Rubber band past the first and last camera.
          if (next < 0) next = -0.35 * (1 - Math.exp(next / 0.35));
          if (next > 4) next = 4 + 0.35 * (1 - Math.exp(-(next - 4) / 0.35));
          m.manualP.set(next);
          const i = indexOf(next);
          if (i !== m.last.get()) {
            m.last.set(i);
            scheduleOnRN(tactile, false);
          }
        })
        .onFinalize((e) => {
          const now = m.clock.get();
          m.dragging.set(0);
          const current = m.manualP.get();
          scheduleOnRN(tactile, true);
          if (m.moved.get() === 1 && width > 0) {
            const v = -e.velocityX / (width * 0.8);
            const near = Math.round(current);
            const target = Math.min(4, Math.max(0, Math.min(near + 1, Math.max(near - 1, Math.round(current + v * 0.18)))));
            settle(now, target, v);
          } else {
            settle(now, (indexOf(current) + 1) % 5, 0);
          }
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [width, reduced, autoPlay],
  );

  const go = (k: number) => {
    tactile(true);
    const now = m.clock.get();
    takeOver(now);
    settle(now, k, 0);
  };

  const scene = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const w = size.width;
    const h = size.height;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (w > 0) drawScene(canvas, w, h, shown.get(), reduced);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: lw, height: lh } = e.nativeEvent.layout;
    if (lw !== size.width || lh !== size.height) setSize({ width: lw, height: lh });
  };

  const labelStyle = useAnimatedStyle(() => {
    const v = p.get();
    const a = 1 - smoothstep(0.08, 0.42, Math.abs(v - Math.round(v)));
    return { opacity: a, transform: [{ translateY: 6 * (1 - a) }] };
  });

  const cam = CAMERAS[index];
  const stripY = size.height * 0.755;
  const footerY = size.height - Math.max(insets.bottom, 16) - 12 - 44;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size.width > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel={`Camera ${cam.name}`}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={scene} />
              </Canvas>
            </View>
          </GestureDetector>

          <View style={[styles.header, { top: insets.top + 14 }]} pointerEvents="none">
            <Text style={[styles.headerText, styles.tracked]}>CAMERA SHELF</Text>
            <Text style={styles.headerText}>{`${String(index + 1).padStart(2, '0')} / 05`}</Text>
          </View>

          <Animated.View style={[styles.labels, { top: size.height * 0.63 }, labelStyle]} pointerEvents="none">
            <Text style={styles.name} numberOfLines={1}>
              {cam.name}
            </Text>
            <Text style={styles.spec} numberOfLines={1}>
              {cam.spec}
            </Text>
          </Animated.View>

          {/* The thumbnail strip: thumbnails slide 72 pt per camera under a fixed outline. */}
          {CAMERAS.map((c, k) => (
            <Thumb key={c.name} k={k} p={p} left={size.width / 2 - 32} top={stripY - 22} onPress={() => go(k)} />
          ))}
          <View style={[styles.outline, { left: size.width / 2 - 35, top: stripY - 29 }]} pointerEvents="none" />
          <LinearGradient
            colors={[BG, BG, `${BG}00`]}
            locations={[0, 0.09, 1]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={[styles.edge, { left: 0, top: stripY - 40, width: size.width * 0.22 }]}
            pointerEvents="none"
          />
          <LinearGradient
            colors={[`${BG}00`, BG, BG]}
            locations={[0, 0.91, 1]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={[styles.edge, { right: 0, top: stripY - 40, width: size.width * 0.22 }]}
            pointerEvents="none"
          />

          <View style={[styles.footer, { top: footerY }]}>
            <Text style={styles.hint}>Swipe to change camera</Text>
            <Pressable
              style={styles.close}
              onPress={() => tactile(false)}
              accessibilityRole="button"
              accessibilityLabel="Close"
            >
              <Svg width={14} height={8} viewBox="0 0 14 8">
                <SvgPath d="M1.1 1 L7 7 L12.9 1" stroke="#000" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </Svg>
            </Pressable>
          </View>
        </>
      ) : null}
    </View>
  );
}

/** One thumbnail: the camera drawn once, slid and scaled on the UI thread. */
function Thumb({ k, p, left, top, onPress }: { k: number; p: SharedValue<number>; left: number; top: number; onPress: () => void }) {
  const picture = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 56, 44));
    const m = m3mul(m3y(-0.45), m3x(0.22));
    cameraFaces(CAMERAS[k], m, 0.26, 0, 28, 24, 1)
      .sort((a, b) => a.z - b.z)
      .forEach((f) => drawFace(canvas, f));
    return recorder.finishRecordingAsPicture();
  }, [k]);
  const itemStyle = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(k - p.get()));
    return { opacity: 0.4 + 0.6 * near, transform: [{ translateX: (k - p.get()) * 72 }] };
  });
  const thumbStyle = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(k - p.get()));
    return { transform: [{ scale: 1 + 0.15 * near }] };
  });
  const labelStyle = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(k - p.get()));
    return { opacity: 0.35 + 0.65 * near };
  });
  return (
    <Animated.View style={[styles.thumb, { left, top }, itemStyle]}>
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={CAMERAS[k].name} style={styles.thumbPress}>
        <Animated.View style={thumbStyle}>
          <Canvas style={styles.thumbCanvas}>
            <Picture picture={picture} />
          </Canvas>
        </Animated.View>
        <Animated.Text style={[styles.thumbLabel, labelStyle]} numberOfLines={1}>
          {CAMERAS[k].name}
        </Animated.Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  header: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between' },
  headerText: { fontFamily: MONO, fontSize: 11, fontWeight: '600', color: 'rgba(255,255,255,0.45)' },
  tracked: { letterSpacing: 2 },
  labels: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 8 },
  name: { fontFamily: MONO, fontSize: 26, fontWeight: '700', color: '#FFFFFF' },
  spec: { fontFamily: MONO, fontSize: 11, fontWeight: '500', letterSpacing: 0.5, color: 'rgba(255,255,255,0.5)' },
  thumb: { position: 'absolute', width: 64, alignItems: 'center' },
  thumbPress: { width: 64, alignItems: 'center', gap: 10 },
  thumbCanvas: { width: 56, height: 44 },
  thumbLabel: { fontFamily: MONO, fontSize: 9, fontWeight: '600', color: '#FFFFFF' },
  outline: { position: 'absolute', width: 70, height: 58, borderRadius: 14, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.9)' },
  edge: { position: 'absolute', height: 100 },
  footer: { position: 'absolute', left: 24, right: 24, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  hint: { fontSize: 13, fontWeight: '500', color: 'rgba(255,255,255,0.4)' },
  close: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
});

