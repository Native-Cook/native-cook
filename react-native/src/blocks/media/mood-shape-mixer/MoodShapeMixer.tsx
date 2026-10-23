import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { drawStage, makeArt } from './mood-art';
import { MOODS, animation, at, clamp, itemWidth, mix, nearest, scripted, still, type Anim, type Shown } from './mood-model';

export { LOOP } from './mood-model';
export type MoodShapeMixerProps = { autoPlay?: boolean; renderTime?: number };
type Run = { now: number; origin: number; autoStart: number; chooser: Anim; panel: Anim; mood: Anim; resumeAt: number; returning: boolean; dragging: boolean; captured: number; lastMood: number; closing: boolean; panelOrigin: number };
function shown(run: Run, now: number, reduced: boolean, renderTime?: number): Shown {
  'worklet'; if (renderTime !== undefined) return scripted(renderTime);
  if (run.autoStart >= 0 && now >= run.autoStart && !reduced) return scripted(now - run.autoStart);
  return { chooser: at(run.chooser, now), panel: at(run.panel, now), m: at(run.mood, now), clock: now - run.origin, closing: run.closing, origin: run.panelOrigin };
}
function captured(run: Run, now: number, reduced: boolean): Run {
  'worklet'; const s = shown(run, now, reduced);
  return { ...run, now, autoStart: -1, chooser: still(s.chooser), panel: still(s.panel), mood: still(s.m), resumeAt: -1, returning: false, closing: s.closing, panelOrigin: s.origin };
}
function tactile() { if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
function selection() { if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {}); }
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Compact source picker. Artwork, copy scrambling and every animation stay on the UI thread. */
export function MoodShapeMixer({ autoPlay = false, renderTime }: MoodShapeMixerProps) {
  const window = useWindowDimensions(), initialReduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [reduced, setReduced] = useState(initialReduced), [manualClock, setManualClock] = useState(false);
  const art = useMemo(() => makeArt(), []), width = size?.width ?? window.width, height = size?.height ?? window.height, sx = width / 393, sy = height / 852;
  const run = useSharedValue<Run>({ now: 0, origin: 0, autoStart: -1, chooser: still(1), panel: still(0), mood: still(5), resumeAt: -1, returning: false, dragging: false, captured: 5, lastMood: 5, closing: false, panelOrigin: 0 });
  useEffect(() => { const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced); return () => subscription.remove(); }, []);
  useEffect(() => {
    const now = Date.now() / 1000;
    run.set({ now, origin: now, autoStart: autoPlay && !reduced ? now : -1, chooser: still(1), panel: still(reduced ? 1 : 0), mood: still(5), resumeAt: -1, returning: false, dragging: false, captured: 5, lastMood: 5, closing: false, panelOrigin: 0 });
  }, [autoPlay, reduced, run]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = run.get(); let next = { ...old, now };
    if (!old.dragging && old.resumeAt >= 0 && now >= old.resumeAt) {
      if (!old.returning) {
        const s = shown(old, now, reduced);
        next = { ...next, chooser: animation(s.chooser, 1, now, 0.5), panel: animation(s.panel, 0, now, 0.5), mood: animation(s.m, 5, now, 0.5), returning: true, resumeAt: now, closing: true, panelOrigin: 0 };
      } else if (now >= old.resumeAt + 0.55) {
        next = { ...next, chooser: still(1), panel: still(0), mood: still(5), autoStart: autoPlay ? now : -1, returning: false, resumeAt: -1, closing: false, panelOrigin: 0 };
        scheduleOnRN(setManualClock, false);
      }
    }
    run.set(next);
  }, false);
  useEffect(() => {
    ticker.setActive(!reduced && renderTime === undefined && (autoPlay || manualClock));
    return () => ticker.setActive(false);
  }, [autoPlay, manualClock, reduced, renderTime, ticker]);
  const pose = useDerivedValue(() => { const r = run.get(); return shown(r, r.now, reduced, renderTime); });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.scale(sx, sy); drawStage(canvas, art, pose.get(), reduced); return recorder.finishRecordingAsPicture();
  });
  const open = () => {
    if (renderTime !== undefined) return;
    const now = Date.now() / 1000, r = captured(run.get(), now, reduced), s = shown(r, now, reduced);
    run.set({ ...r, chooser: animation(s.chooser, 1, now, reduced ? 0 : 0.3), panel: animation(s.panel, 1, now, reduced ? 0 : 0.4, 1), closing: false, panelOrigin: s.panel < 0.1 && s.chooser < 0.5 ? 1 : s.origin, resumeAt: reduced ? -1 : now + 2 });
    setManualClock(true); tactile();
  };
  const close = () => {
    if (renderTime !== undefined) return;
    const now = Date.now() / 1000, r = captured(run.get(), now, reduced), s = shown(r, now, reduced);
    run.set({ ...r, panel: s.panel > 0.1 ? animation(s.panel, 0, now, reduced ? 0 : 0.37) : r.panel, chooser: s.panel > 0.1 ? r.chooser : animation(s.chooser, 0, now, reduced ? 0 : 0.4), closing: s.panel > 0.1 ? true : r.closing, panelOrigin: 0, resumeAt: reduced ? -1 : now + 2 });
    setManualClock(true); tactile();
  };
  const select = (delta: number) => {
    if (renderTime !== undefined) return;
    const now = Date.now() / 1000, r = captured(run.get(), now, reduced), m = at(r.mood, now);
    run.set({ ...r, mood: animation(m, clamp(nearest(m) + delta), now, reduced ? 0 : 0.26, 1), resumeAt: reduced ? -1 : now + 2 });
    setManualClock(true); selection();
  };
  const release = useCallback(() => {
    'worklet'; const now = Date.now() / 1000, r = run.get(); if (!r.dragging) return;
    const m = at(r.mood, now);
    run.set({ ...r, now, dragging: false, mood: animation(m, nearest(m), now, reduced ? 0 : 0.26, 1), resumeAt: reduced ? -1 : now + 2 });
  }, [reduced, run]);
  const begin = useCallback(() => {
    'worklet'; if (renderTime !== undefined) return;
    const now = Date.now() / 1000, r = captured(run.get(), now, reduced), m = at(r.mood, now);
    run.set({ ...r, dragging: true, captured: m, lastMood: nearest(m) }); scheduleOnRN(setManualClock, true);
  }, [reduced, renderTime, run]);
  const update = useCallback((translationX: number) => {
    'worklet'; if (renderTime !== undefined) return;
    const r = run.get(), m = clamp(r.captured + translationX / (47.8 * sx)), selected = nearest(m);
    if (selected !== r.lastMood) scheduleOnRN(selection);
    run.set({ ...r, now: Date.now() / 1000, mood: still(m), lastMood: selected });
  }, [renderTime, run, sx]);
  const pan = Gesture.Pan().minDistance(0).onStart(begin).onUpdate(event => update(event.translationX))
    .onEnd(release).onFinalize((_event, success) => { if (!success) release(); });
  const mainProps = useAnimatedProps(() => {
    const s = pose.get(), visible = s.chooser > 0.5 && s.panel < 0.1;
    return { pointerEvents: (visible ? 'auto' : 'none') as 'auto' | 'none', accessibilityElementsHidden: !visible, accessibilityLabel: `Feeling, ${MOODS[nearest(s.m)].name}` };
  });
  const closeProps = useAnimatedProps(() => {
    const s = pose.get(); return { pointerEvents: (s.chooser > 0.1 ? 'auto' : 'none') as 'auto' | 'none', accessibilityElementsHidden: s.chooser <= 0.1, accessibilityLabel: s.panel > 0.1 ? 'Close feeling picker' : 'Close activities' };
  });
  const roomProps = useAnimatedProps(() => {
    const s = pose.get(); return { pointerEvents: (s.chooser < 0.1 ? 'auto' : 'none') as 'auto' | 'none', accessibilityElementsHidden: s.chooser >= 0.1, accessibilityLabel: `Feeling, ${MOODS[nearest(s.m)].name}` };
  });
  const sliderProps = useAnimatedProps(() => {
    const s = pose.get(); return { pointerEvents: (s.panel > 0.8 ? 'auto' : 'none') as 'auto' | 'none', accessibilityElementsHidden: s.panel <= 0.8, accessibilityValue: { text: MOODS[nearest(s.m)].name } };
  });
  const sliderStyle = useAnimatedStyle(() => {
    const s = pose.get(), p = s.closing ? 1 : s.panel, w = itemWidth(s.m);
    return { left: mix(196.5 - w / 2, 10.2, p) * sx, top: mix(652, 656, p) * sy, width: mix(w, 372.6, p) * sx };
  });
  const mainStyle = useAnimatedStyle(() => { const w = itemWidth(pose.get().m); return { left: (196.5 - w / 2) * sx, width: w * sx }; });
  const measure = useCallback((event: LayoutChangeEvent) => {
    const next = event.nativeEvent.layout; setSize(old => old?.width === next.width && old?.height === next.height ? old : { width: next.width, height: next.height });
  }, []);
  const box = (x: number, y: number, w: number, h: number) => ({ position: 'absolute' as const, left: x * sx, top: y * sy, width: w * sx, height: h * sy });
  return <View style={styles.screen} onLayout={measure}>
    <StatusBar style="dark" />
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Picture picture={picture} /></Canvas>
    <AnimatedPressable animatedProps={mainProps} onPress={open} accessibilityRole="button" style={[box(68.5, 652, 256, 68), mainStyle]} />
    <GestureDetector gesture={pan}><Animated.View animatedProps={sliderProps} style={[box(10.2, 656, 372.6, 68), sliderStyle]} accessible accessibilityRole="adjustable" accessibilityLabel="Feeling slider" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => select(event.nativeEvent.actionName === 'increment' ? 1 : -1)} /></GestureDetector>
    <AnimatedPressable animatedProps={closeProps} onPress={close} accessibilityRole="button" style={box(172.5, 760, 48, 48)} />
    <AnimatedPressable animatedProps={roomProps} onPress={open} accessibilityRole="button" style={box(127.5, 765, 138, 50)} />
  </View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#ECE7DB', overflow: 'hidden' } });
