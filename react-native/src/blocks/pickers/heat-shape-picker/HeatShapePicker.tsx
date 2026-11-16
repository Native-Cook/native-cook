import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback,
  useReducedMotion, useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, LinearGradient as SvgGradient, Path, Stop } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawBody, glyphPath } from './heatShapeArt';
import {
  automatic, CAPTIONS, type Frame, type Layout, makeFrame, makeLayout, makeSpring, MODES, NAMES,
  rampString, retarget, type Spring, springState, type Transition, transitionFrame,
} from './heatShapeModel';

export { LOOP } from './heatShapeModel';

type Driver = {
  epoch: number; transition: Transition | null; held: Frame | null; dragging: boolean; deadline: number;
  x: Spring; y: Spring; grip: Spring; direction: number;
};
type Drawing = { frame: Frame; dx: number; dy: number; grip: number; direction: number };

/** Read only from frame and gesture callbacks, never while rendering a component. */
function nowSeconds() { 'worklet'; return Date.now() / 1000; }

function makeDriver(now: number): Driver {
  'worklet';
  return { epoch: now, transition: null, held: null, dragging: false, deadline: Infinity,
    x: makeSpring(), y: makeSpring(), grip: makeSpring(), direction: 0 };
}
function frameAt(d: Driver, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  if (d.held) return d.held;
  if (d.transition) return transitionFrame(d.transition, now, reduced);
  return automatic(auto ? Math.max(0, now - d.epoch) : 0, reduced);
}
function haptic(kind: 'light' | 'soft' | 'selection') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'selection') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

const GLYPHS = MODES.slice(0, 4).map(glyphPath);
const GLYPH_COLORS = MODES.slice(0, 4).map((p) => [0.20, 0.10, 0, -0.10, -0.20].map((v) => rampString(p.h + v)));

function Chip({ index, drawing, layout, selected, onSelect }: {
  index: number; drawing: SharedValue<Drawing>; layout: Layout; selected: boolean; onSelect: () => void;
}) {
  const outlineStyle = useAnimatedStyle(() => ({ opacity: 1 - drawing.get().frame.chips[index] }));
  const fillStyle = useAnimatedStyle(() => ({ opacity: drawing.get().frame.chips[index] }));
  const nameStyle = useAnimatedStyle(() => ({ opacity: 0.55 + 0.45 * drawing.get().frame.chips[index] }));
  return (
    <Pressable onPress={onSelect} hitSlop={6} accessibilityRole="button" accessibilityLabel={NAMES[index]}
      accessibilityHint={CAPTIONS[index]} accessibilityState={{ selected }}
      style={[styles.chip, { left: (28 + 87 * index) * layout.sx, top: 676 * layout.sy + layout.shift }]}>
      <View pointerEvents="none" style={styles.chipStroke} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, outlineStyle]}>
        <Svg width={76} height={76}>
          <Path d={GLYPHS[index]} fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth={1.8} strokeLinejoin="round" />
        </Svg>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fillStyle]}>
        <Svg width={76} height={76}>
          <Defs>
            <SvgGradient id={`heatGlyph${index}`} gradientUnits="userSpaceOnUse" x1={38} y1={16.75} x2={38} y2={51.25}>
              {GLYPH_COLORS[index].map((color, j) => <Stop key={j} offset={j / 4} stopColor={color} />)}
            </SvgGradient>
          </Defs>
          <Path d={GLYPHS[index]} fill={`url(#heatGlyph${index})`} stroke="#FF2A12" strokeWidth={1.2} strokeLinejoin="round" />
        </Svg>
      </Animated.View>
      <Animated.Text accessible={false} style={[styles.chipName, nameStyle]}>{NAMES[index]}</Animated.Text>
    </Pressable>
  );
}

function Label({ index, drawing, layout }: { index: number; drawing: SharedValue<Drawing>; layout: Layout }) {
  const style = useAnimatedStyle(() => ({ opacity: drawing.get().frame.labels[index], transform: [{ translateY: drawing.get().frame.labelY[index] }] }));
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
      <Text accessible={false} style={[styles.modeName, { top: 548 * layout.sy + layout.shift }]}>{NAMES[index]}</Text>
      <Text accessible={false} style={[styles.caption, { top: 598 * layout.sy + layout.shift }]}>{CAPTIONS[index]}</Text>
    </Animated.View>
  );
}

/**
 * A heater's four modes share one body of light. The 8 s clock deforms one 240-point outline;
 * every blurred rim, fill, volume pass and ember uses that same geometry. Touch freezes the
 * loop, springs from the displayed frame and resumes at the selected mode after 2 s.
 * Reduced motion keeps only scheduled 300 ms cross-fades between resting silhouettes.
 */
export function HeatShapePicker({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 393, height: 852 });
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const [selected, setSelected] = useState(0);
  const layout = useMemo(() => makeLayout(size.width, size.height, insets.top, insets.bottom),
    [size.width, size.height, insets.top, insets.bottom]);
  const driver = useSharedValue<Driver>(makeDriver(0));
  const drawing = useSharedValue<Drawing>({ frame: makeFrame(), dx: 0, dy: 0, grip: 0, direction: 0 });

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => subscription.remove();
  }, []);
  useEffect(() => {
    driver.set(makeDriver(nowSeconds()));
    drawing.set({ frame: automatic(0, reduced), dx: 0, dy: 0, grip: 0, direction: 0 });
  }, [autoPlay, reduced, active, driver, drawing]);

  const clock = useFrameCallback(() => {
    const now = nowSeconds();
    const d = driver.get();
    if (!autoPlay && !d.transition && !d.dragging) { scheduleOnRN(setManualActive, false); return; }
    if (!d.dragging && d.transition && now >= d.deadline) {
      if (autoPlay) { d.epoch = d.deadline - d.transition.target * 2; d.transition = null; }
      d.deadline = Infinity;
      driver.set({ ...d });
      scheduleOnRN(setManualActive, !autoPlay && !reduced);
    }
    drawing.set({ frame: frameAt(d, now, autoPlay, reduced), dx: springState(d.x, now)[0],
      dy: springState(d.y, now)[0], grip: springState(d.grip, now)[0], direction: d.direction });
  }, false);
  useEffect(() => {
    clock.setActive(active && (autoPlay || manualActive));
    return () => clock.setActive(false);
  }, [active, autoPlay, manualActive, clock]);

  // Accessibility state changes only at a mode boundary, never once per frame.
  useAnimatedReaction(() => drawing.get().frame.selected, (value, previous) => {
    if (value !== previous) scheduleOnRN(setSelected, value);
  });

  const choose = (index: number) => {
    'worklet';
    const now = nowSeconds(), d = driver.get();
    const source = frameAt(d, now, autoPlay, reduced);
    d.held = null;
    d.transition = { source, target: index, at: now };
    d.deadline = d.dragging ? Infinity : now + 2;
    driver.set({ ...d });
    scheduleOnRN(haptic, 'light');
  };

  const pan = Gesture.Pan().minDistance(0)
    .onBegin((e) => {
      if (Math.hypot(e.x - layout.cx, e.y - layout.cy) > 1.4 * 110 * layout.sx) return;
      const now = nowSeconds(), d = driver.get();
      d.held = frameAt(d, now, autoPlay, reduced); d.dragging = true; d.deadline = Infinity;
      d.grip = retarget(d.grip, 1, now);
      driver.set({ ...d });
      scheduleOnRN(setManualActive, true);
      scheduleOnRN(haptic, 'soft');
    })
    .onUpdate((e) => {
      const d = driver.get(); if (!d.dragging) return;
      const now = nowSeconds(), x = e.translationX, y = e.translationY;
      const length = Math.hypot(x, y), follow = length > 0 ? Math.min(26, length * 0.22) / length : 0;
      d.x = retarget(d.x, x * follow, now); d.y = retarget(d.y, y * follow, now);
      if (length > 1) d.direction = Math.atan2(x, -y);
      driver.set({ ...d });
    })
    .onEnd((e) => {
      const d = driver.get(); if (!d.dragging) return;
      const now = nowSeconds(), source = frameAt(d, now, autoPlay, reduced);
      const swipe = Math.abs(e.translationX) > 44;
      const target = swipe ? (source.selected + (e.translationX < 0 ? 1 : 3)) % 4 : source.selected;
      d.held = null; d.dragging = false;
      d.transition = { source, target, at: now, settling: !swipe };
      d.grip = retarget(d.grip, 0, now, true); d.x = retarget(d.x, 0, now, true); d.y = retarget(d.y, 0, now, true);
      d.deadline = now + 2;
      driver.set({ ...d });
      if (swipe) scheduleOnRN(haptic, 'selection');
    })
    .onFinalize(() => {
      // A cancelled gesture releases its springs and still resumes after the same delay.
      const d = driver.get(); if (!d.dragging) return;
      const now = nowSeconds(), source = frameAt(d, now, autoPlay, reduced);
      d.held = null; d.dragging = false;
      d.transition = { source, target: source.selected, at: now, settling: true };
      d.grip = retarget(d.grip, 0, now, true); d.x = retarget(d.x, 0, now, true); d.y = retarget(d.y, 0, now, true);
      d.deadline = now + 2;
      driver.set({ ...d });
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, layout.width, layout.height));
    const d = drawing.get();
    drawBody(canvas, d.frame, layout, reduced, d.dx, d.dy, d.grip, d.direction);
    return recorder.finishRecordingAsPicture();
  });
  const ringStyle = useAnimatedStyle(() => ({ transform: [{ translateX: (28 + 87 * drawing.get().frame.ring) * layout.sx }] }));
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((previous) => previous.width === width && previous.height === height ? previous : { width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable"
          accessibilityLabel="Heater mode" accessibilityValue={{ text: NAMES[selected] }}
          accessibilityHint="Drag the light, or swipe horizontally to change modes."
          accessibilityActions={[{ name: 'increment', label: 'Next mode' }, { name: 'decrement', label: 'Previous mode' }]}
          onAccessibilityAction={(e) => {
            setManualActive(true);
            scheduleOnUI(choose, (selected + (e.nativeEvent.actionName === 'increment' ? 1 : 3)) % 4);
          }}>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><Picture picture={picture} /></Canvas>
        </View>
      </GestureDetector>
      <Text pointerEvents="none" style={[styles.overline, { top: 78 * layout.sy + layout.shift }]}>LIVING ROOM · 21°</Text>
      <Text pointerEvents="none" style={[styles.title, { top: 102 * layout.sy + layout.shift }]}>Heater mode</Text>
      {NAMES.map((name, index) => <Label key={name} index={index} drawing={drawing} layout={layout} />)}
      <Animated.View pointerEvents="none" style={[styles.ring, { top: 676 * layout.sy + layout.shift }, ringStyle]}>
        {/* Native blurred shadows approximate the two ends of the gradient glow without another canvas. */}
        <View style={styles.ringGlow} />
        <LinearGradient colors={['#FF2A12', '#5546FF']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.ringBorder}>
          <View style={styles.ringCutout} />
        </LinearGradient>
      </Animated.View>
      {NAMES.map((name, index) => <Chip key={name} index={index} drawing={drawing} layout={layout}
        selected={selected === index} onSelect={() => { setManualActive(true); scheduleOnUI(choose, index); }} />)}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  overline: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.55)',
    fontSize: 12, fontWeight: '600', letterSpacing: 1.8, includeFontPadding: false },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#FFFFFF', fontSize: 30, fontWeight: '600', includeFontPadding: false },
  modeName: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#FFFFFF', fontSize: 36, fontWeight: '700', includeFontPadding: false },
  caption: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.55)', fontSize: 15, includeFontPadding: false },
  chip: { position: 'absolute', width: 76, height: 76, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.05)' },
  chipStroke: { position: 'absolute', left: -0.5, top: -0.5, width: 77, height: 77, borderRadius: 24.5,
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  chipName: { position: 'absolute', top: 56, left: 0, right: 0, textAlign: 'center', color: '#FFFFFF', fontSize: 11, fontWeight: '500', includeFontPadding: false },
  ring: { position: 'absolute', left: 0, width: 76, height: 76 },
  ringGlow: { position: 'absolute', left: -1, top: -1, width: 78, height: 78, borderRadius: 25, backgroundColor: '#000000',
    boxShadow: '-2px -2px 16px 2px rgba(255,42,18,0.36), 2px 2px 16px 2px rgba(85,70,255,0.36)' },
  ringBorder: { position: 'absolute', left: -0.8, top: -0.8, width: 77.6, height: 77.6, borderRadius: 24.8, padding: 1.6 },
  ringCutout: { flex: 1, borderRadius: 23.2, backgroundColor: '#000000' },
});
