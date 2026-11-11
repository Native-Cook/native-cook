import { Canvas, Image, LinearGradient, Picture, Rect, Skia, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { drawGlassVoiceCapsule, drawGlassVoiceCapsuleDroplet } from './art';
import {
  advance, clamp, type Frame, frameAt, geometry, initialMotion, retarget, takeOver, toggle,
} from './motion';

export { LOOP } from './motion';

type Layout = { width: number; height: number };
type GlassGeometry = ReturnType<typeof geometry>;

function haptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

/** Native rounded type uses tabular figures. React updates only when a whole second changes. */
function GlassVoiceCapsuleTimer({ frame, location }: { frame: SharedValue<Frame>; location: SharedValue<GlassGeometry> }) {
  const [second, setSecond] = useState(0);
  useAnimatedReaction(() => Math.floor(frame.get().seconds), (value, previous) => {
    if (value !== previous) scheduleOnRN(setSecond, value);
  }, [frame]);
  const container = useAnimatedStyle(() => {
    const f = frame.get(), g = location.get();
    return { left: g.cx - 100, top: g.cy - 24, opacity: f.content,
      transform: [{ rotate: `${g.angle}deg` }, { scale: f.scale * f.timerScale }] };
  });
  const current = useAnimatedStyle(() => {
    const f = frame.get();
    const swap = f.digitRise && Math.floor(f.seconds) > 0 ? clamp(f.digitAge / 0.18) : 1;
    return { opacity: swap, transform: [{ translateY: 4 * (1 - swap) }] };
  });
  const previous = useAnimatedStyle(() => {
    const f = frame.get();
    const swap = f.digitRise && Math.floor(f.seconds) > 0 ? clamp(f.digitAge / 0.18) : 1;
    return { opacity: 1 - swap, transform: [{ translateY: -4 * swap }] };
  });
  const format = (value: number) => `${Math.floor(value / 60).toString().padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}`;
  return (
    <Animated.View pointerEvents="none" style={[styles.timer, container]}>
      <Animated.Text style={[styles.digits, previous]}>{format(Math.max(0, second - 1))}</Animated.Text>
      <Animated.Text style={[styles.digits, current]}>{format(second)}</Animated.Text>
    </Animated.View>
  );
}

/** Thick glass over one bundled image. Every automatic frame comes from the nine-second clock. */
export function GlassVoiceCapsule({ autoPlay = false }: { autoPlay?: boolean }) {
  const [layout, setLayout] = useState<Layout>({ width: 393, height: 852 });
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setLayout(old => old.width === width && old.height === height ? old : { width, height });
  }, []);
  const { width, height } = layout;
  const reduced = useReducedMotionPreference();
  const image = useImage(require('./assets/glassvoicecapsule_spheres.jpg'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  const clock = useSharedValue(0), startedAt = useSharedValue(-1), clockBase = useSharedValue(0);
  const motion = useSharedValue(initialMotion()), asleep = useSharedValue(true);
  const playing = autoPlay && !reduced;

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => {
      setActive(state === 'active');
      if (state !== 'active') { motion.set(initialMotion(clock.get())); setAwake(false); asleep.set(true); }
    });
    return () => sub.remove();
  }, [motion, clock, asleep]);
  useEffect(() => {
    motion.set(initialMotion(clock.get())); asleep.set(false);
  }, [reduced, motion, clock, asleep]);

  const tick = useFrameCallback(info => {
    if (startedAt.get() < 0) { startedAt.set(info.timestamp); clockBase.set(clock.get()); }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const old = motion.get(), next = advance(old, now, reduced);
    if (next !== old) motion.set(next);
    if (!playing && !next.manual && !next.touching && !asleep.get()) {
      asleep.set(true); scheduleOnRN(sleep);
    }
  }, false);
  const running = active && !!image && (playing || awake);
  useEffect(() => {
    startedAt.set(-1); tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const frame = useDerivedValue(() => frameAt(motion.get(), clock.get(), playing, reduced));
  const location = useDerivedValue(() => geometry(frame.get(), motion.get().drag, clock.get(), width, height, reduced));
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (image) drawGlassVoiceCapsule(canvas, image, width, height, frame.get(), location.get());
    return recorder.finishRecordingAsPicture();
  });
  const dropletPicture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (image) drawGlassVoiceCapsuleDroplet(canvas, image, width, height, frame.get(), location.get());
    return recorder.finishRecordingAsPicture();
  });
  const restingCaption = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - frame.get().listening - frame.get().saved) }));
  const listeningCaption = useAnimatedStyle(() => ({ opacity: frame.get().listening }));
  const savedCaption = useAnimatedStyle(() => ({ opacity: frame.get().saved }));

  const gesture = useMemo(() => Gesture.Pan().minDistance(0)
    .onBegin(e => {
      const now = clock.get(), s = motion.get(), g = location.get();
      if (Math.abs(e.x - g.cx) >= g.width / 2 + 12 || Math.abs(e.y - g.cy) >= g.height / 2 + 12) return;
      motion.set({ ...takeOver(s, now, playing, reduced), touching: true, startX: e.x, startY: e.y });
      asleep.set(false); scheduleOnRN(wake);
    })
    .onUpdate(e => {
      const s = motion.get(); if (!s.touching) return;
      motion.set({ ...s, drag: retarget(s.drag, clock.get(), e.translationX, e.translationY, false) });
    })
    .onEnd(e => {
      const now = clock.get(); let s = motion.get(); if (!s.touching) return;
      const f = frameAt(s, now, playing, reduced), g = geometry(f, s.drag, now, width, height, reduced);
      if (Math.hypot(e.translationX, e.translationY) < 8) {
        const angle = g.angle * Math.PI / 180, dx = s.startX - g.cx, dy = s.startY - g.cy;
        const x = (dx * Math.cos(angle) + dy * Math.sin(angle)) / f.scale;
        const y = (-dx * Math.sin(angle) + dy * Math.cos(angle)) / f.scale;
        if (!f.recording || Math.hypot(x - (g.width / 2 - 50), y) < 34 * f.droplet + 10) {
          s = toggle(s, now, playing, reduced); scheduleOnRN(haptic);
        }
      }
      motion.set({ ...s, touching: false, drag: retarget(s.drag, now, 0, 0, true), idleAt: now + 2 });
    })
    .onFinalize(() => {
      const s = motion.get(); if (!s.touching) return;
      const now = clock.get();
      motion.set({ ...s, touching: false, drag: retarget(s.drag, now, 0, 0, true), idleAt: now + 2 });
    }), [clock, motion, location, playing, reduced, asleep, wake, width, height]);

  const accessibleToggle = useCallback(() => {
    motion.set(toggle(motion.get(), clock.get(), playing, reduced));
    asleep.set(false); setAwake(true); haptic();
  }, [motion, clock, playing, reduced, asleep]);

  return (
    <View style={styles.root} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button"
          accessibilityLabel="Say it out loud. Voice note recorder."
          accessibilityHint="Activate to start or stop a note. Drag the glass to move it."
          onAccessibilityTap={accessibleToggle}>
          <Canvas style={StyleSheet.absoluteFill}>
            {image && <Image image={image} x={0} y={0} width={width} height={height} fit="cover" />}
            <Rect x={0} y={0} width={width} height={220 * height / 852}>
              <LinearGradient start={vec(0, 0)} end={vec(0, 220 * height / 852)} colors={['rgba(32,50,79,0.28)', 'rgba(32,50,79,0)']} />
            </Rect>
            <Picture picture={picture} />
          </Canvas>
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Text style={[styles.title, { top: 92 * height / 852 - 18 }]}>Say it out loud</Text>
            <View style={[styles.captions, { top: 132 * height / 852 - 11 }]}>
              <Animated.Text style={[styles.caption, restingCaption]}>Tap the mic to start a note</Animated.Text>
              <Animated.Text style={[styles.caption, listeningCaption]}>Listening…</Animated.Text>
              <Animated.Text style={[styles.caption, savedCaption]}>Saved to Drafts</Animated.Text>
            </View>
            <GlassVoiceCapsuleTimer frame={frame} location={location} />
            <Canvas style={StyleSheet.absoluteFill}><Picture picture={dropletPicture} /></Canvas>
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#6E8DB3' },
  title: { position: 'absolute', left: 0, right: 0, color: '#FFFFFF', fontSize: 28, fontWeight: '600',
    fontFamily: Platform.OS === 'ios' ? 'ui-rounded' : undefined, lineHeight: 36, textAlign: 'center', includeFontPadding: false },
  captions: { position: 'absolute', left: 0, right: 0, height: 22 },
  caption: { position: 'absolute', left: 0, right: 0, color: 'rgba(255,255,255,0.72)', fontSize: 15, lineHeight: 22, textAlign: 'center', includeFontPadding: false },
  timer: { position: 'absolute', width: 200, height: 48 },
  digits: { position: 'absolute', left: 4, width: 200, top: 0, height: 48, color: '#FFFFFF', fontSize: 32,
    fontFamily: Platform.OS === 'ios' ? 'ui-rounded' : undefined, fontWeight: '600', fontVariant: ['tabular-nums'],
    lineHeight: 48, textAlign: 'center', includeFontPadding: false },
});
