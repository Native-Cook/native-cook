import { BlurMask, Canvas, Circle, DashPathEffect, Group, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextInputProps, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8;
const RED = '#F5222F';
function clamp(x: number) { 'worklet'; return Math.min(1, Math.max(0, x)); }
function smooth(x: number) { 'worklet'; const v = clamp(x); return v * v * (3 - 2 * v); }
function arrive(q: number) { 'worklet'; const x = clamp(q); return smooth(x) + 2.4 * (x*x*x - x*x); }
function catchOffset(dt: number, velocity = 1.045*2.4/1.78) { 'worklet'; const x = Math.max(0, dt); return velocity / 17 * Math.exp(-7.5*x) * Math.sin(17*x) * (1 - smooth((x - 0.5) / 0.3)); }
function rubber(v: number) { 'worklet'; return v < 0 ? -0.08*(1-Math.exp(v/0.08)) : v > 1 ? 1+0.08*(1-Math.exp(-(v-1)/0.08)) : v; }
function phase(time: number) {
  'worklet'; const t = time % LOOP;
  if (t < 1.1) return 0;
  if (t < 1.32) return -0.045 * smooth((t - 1.1) / 0.22);
  if (t < 3.1) return -0.045 + 1.045 * arrive((t - 1.32) / 1.78);
  if (t < 3.9) return 1 + catchOffset(t - 3.1);
  if (t < 5.2) return 1;
  if (t < 6.8) return 1 - smooth((t - 5.2) / 1.6);
  return 0;
}
function arc(a: number, b: number, cy: number, radius = 226) {
  'worklet'; const p = Skia.Path.Make();
  p.addArc({ x: 196.5 - radius, y: cy - radius, width: radius * 2, height: radius * 2 }, a, Math.max(0.001, b - a)); return p;
}
const LiveInput = Animated.createAnimatedComponent(TextInput);
function LiveText({ value, style }: { value: SharedValue<string>; style: object }) {
  const props = useAnimatedProps(() => ({ text: value.get(), defaultValue: value.get() }) as TextInputProps);
  return <LiveInput animatedProps={props} editable={false} pointerEvents="none" underlineColorAndroid="transparent" style={[styles.live, style]} />;
}
function tapHaptic() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

export function RetirementGapDial({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions(); const insets = useSafeAreaInsets();
  const scale = width / 393; const h = height / scale; const cy = h - 220; const top = insets.top / scale;
  const reduced = useReducedMotion();
  const [awake, setAwake] = useState(false);
  const [amountWidth, setAmountWidth] = useState(80);
  const clock = useSharedValue(0); const epoch = useSharedValue(-1); const touched = useSharedValue(-100);
  const manual = useSharedValue(0); const isTouching = useSharedValue(false); const catchFired = useSharedValue(false); const resolveAt = useSharedValue(-100); const resolveFrom = useSharedValue(0);
  const releaseFrom = useSharedValue(0); const releaseVelocity = useSharedValue(0); const sampleAt = useSharedValue(0); const sampleValue = useSharedValue(0); const dragAngle = useSharedValue(0); const dragStart = useSharedValue(0);
  const sleeping = useSharedValue(!autoPlay);
  const frame = useFrameCallback(({ timestamp }) => { if (epoch.get() < 0) epoch.set(timestamp - clock.get() * 1000); clock.set((timestamp - epoch.get()) / 1000);
    if (!autoPlay && !isTouching.get() && touched.get() >= 0 && clock.get() - touched.get() >= 2.6 && !sleeping.get()) { sleeping.set(true); epoch.set(-1); scheduleOnRN(setAwake, false); } }, false);
  useEffect(() => { frame.setActive(previewTime === undefined && !reduced && (autoPlay || awake)); return () => frame.setActive(false); }, [frame, previewTime, reduced, autoPlay, awake]);
  const value = useDerivedValue(() => {
    if (previewTime !== undefined) return phase(previewTime);
    if (isTouching.get()) return manual.get();
    if (reduced) return touched.get() < 0 ? 0 : manual.get();
    const t = clock.get(); const dt = t - touched.get();
    const rd = t - resolveAt.get();
    if (rd < 0.7) return resolveFrom.get() + (1 - resolveFrom.get()) * arrive(rd / 0.7);
    if (rd < 1.5) return 1 + catchOffset(rd - 0.7, (1 - resolveFrom.get()) * 2.4 / 0.7);
    if (dt < 0.65) { const d = releaseFrom.get() - manual.get(); return manual.get() + (d + (releaseVelocity.get() + 16*d) * dt) * Math.exp(-16*dt) * (1 - smooth((dt - 0.45) / 0.2)); }
    if (dt < 2) return manual.get();
    if (dt < 2.6) return manual.get() * (1 - smooth((dt - 2) / 0.6));
    return autoPlay ? phase(touched.get() < 0 ? t : dt - 2.6) : 0;
  });
  const done = useDerivedValue(() => smooth((clamp(value.get()) - 0.90) / 0.10));
  const impact = useDerivedValue(() => Math.min(1, Math.max(0, value.get() - 1) / 0.05));
  const goalCover = useDerivedValue(() => smooth((clamp(value.get()) - 0.86) / 0.10));
  const gapOpacity = useDerivedValue(() => 1 - goalCover.get());
  const catchRadius = useDerivedValue(() => 20 + impact.get() * 5);
  const catchAlpha = useDerivedValue(() => done.get() * impact.get() * 0.6);
  const angle = useDerivedValue(() => -109.5 + 19.5 * value.get());
  const white = useDerivedValue(() => arc(-171, angle.get(), cy));
  const whiteEdge = useDerivedValue(() => arc(-171, angle.get() - 0.5, cy, 250.5));
  const gap = useDerivedValue(() => arc(Math.min(angle.get(), -90.01), -90, cy));
  const tx = useDerivedValue(() => 196.5 + 226 * Math.cos(angle.get() * Math.PI / 180));
  const ty = useDerivedValue(() => cy + 226 * Math.sin(angle.get() * Math.PI / 180));
  const innerTransform = useDerivedValue(() => [{ translateX: tx.get() }, { translateY: ty.get() }, { scaleX: 1 + impact.get() * 0.18 }, { scaleY: 1 - impact.get() * 0.14 }]);
  const goalGuideOpacity = useDerivedValue(() => 0.055 + 0.04 * clamp(value.get()));
  const glowOpacity = useDerivedValue(() => 0.33 * (1 - clamp(value.get())));
  const ray = useDerivedValue(() => { const a = angle.get() * Math.PI / 180; const p = Skia.Path.Make(); p.moveTo(196.5 + 282 * Math.cos(a), cy + 282 * Math.sin(a)); p.lineTo(196.5 + 88 * Math.cos(a), cy + 88 * Math.sin(a)); return p; });
  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: tx.get() - 25.5 }, { translateY: ty.get() - 25.5 }, { rotate: `${angle.get() + 109.5}deg` }] }));
  const offStyle = useAnimatedStyle(() => ({ opacity: 1 - done.get(), transform: [{ translateY: -4 * done.get() }] }));
  const doneStyle = useAnimatedStyle(() => ({ opacity: done.get(), transform: [{ translateY: 4 * (1 - done.get()) }] }));
  const current = useDerivedValue(() => clamp(value.get()) < 0.005 ? 'Current ₹ 89 L' : `Current ₹ ${(89 + 1.17 * clamp(value.get())).toFixed(2)} L`);
  const missing = useDerivedValue(() => `₹ ${(1.17 * (1 - clamp(value.get()))).toFixed(2)} L`);
  const pan = Gesture.Pan().minDistance(4).onStart(e => {
    if (!autoPlay && sleeping.get()) { sleeping.set(false); epoch.set(-1); scheduleOnRN(setAwake, true); }
    manual.set(value.get()); dragStart.set(value.get()); dragAngle.set(Math.atan2(e.y / scale - cy, e.x / scale - 196.5) * 180 / Math.PI);
    isTouching.set(true); touched.set(clock.get()); catchFired.set(false); resolveAt.set(-100); releaseVelocity.set(0); sampleAt.set(clock.get()); sampleValue.set(value.get());
  }).onUpdate(e => {
    const degrees = Math.atan2(e.y / scale - cy, e.x / scale - 196.5) * 180 / Math.PI;
    const next = rubber(dragStart.get() + (degrees - dragAngle.get()) / 19.5); const dt = clock.get() - sampleAt.get();
    if (dt > 0.002) releaseVelocity.set(0.35 * releaseVelocity.get() + 0.65 * Math.min(3, Math.max(-3, (next - sampleValue.get()) / dt)));
    manual.set(next); sampleValue.set(next); sampleAt.set(clock.get()); touched.set(clock.get());
    if (manual.get() >= 0.99 && !catchFired.get()) { catchFired.set(true); scheduleOnRN(tapHaptic); }
  }).onFinalize(() => {
    if (!isTouching.get()) return;
    releaseFrom.set(manual.get()); manual.set(clamp(manual.get() + Math.min(0.08, Math.max(-0.08, releaseVelocity.get() * 0.12))));
    isTouching.set(false); touched.set(clock.get());
  });
  const resolve = () => { if (!autoPlay && sleeping.get()) { sleeping.set(false); epoch.set(-1); setAwake(true); } resolveFrom.set(value.get()); resolveAt.set(clock.get()); touched.set(clock.get()); manual.set(1); releaseFrom.set(1); releaseVelocity.set(0); tapHaptic(); };
  return <GestureDetector gesture={pan}><View style={styles.screen}>
    <StatusBar style="dark" />
    <View style={{ width: 393, height: h, transform: [{ scale }], transformOrigin: 'top left' }}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Path path={`M31 ${top + 29} L22 ${top + 38} L31 ${top + 47}`} color="#404040" style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" />
        <Path path={arc(-171, -9, cy)} color="#C7C7C7" style="stroke" strokeWidth={51} strokeCap="round" />
        <Path path={gap} color={RED} opacity={glowOpacity} style="stroke" strokeWidth={69} strokeCap="round"><BlurMask blur={23} style="normal" /></Path>
        <Path path={ray} color="rgba(0,0,0,.1)" style="stroke" strokeWidth={1.4}><DashPathEffect intervals={[6, 6]} /></Path>
        <Path path={`M196.5 ${cy - 275} L196.5 ${cy - 77}`} color="black" opacity={goalGuideOpacity} style="stroke" strokeWidth={1} />
        <Path path={gap} color={RED} opacity={gapOpacity} style="stroke" strokeWidth={51} strokeCap="round" />
        <Circle cx={196.5} cy={cy - 226} r={17} color="#B91824" opacity={gapOpacity} />
        <Path path={white} color="#F5F5F5" style="stroke" strokeWidth={51} strokeCap="round" />
        <Path path={whiteEdge} color="rgba(255,255,255,.7)" style="stroke" strokeWidth={0.7} strokeCap="round" />
        <Circle cx={196.5} cy={cy - 226} r={17} color="#F5F5F5" opacity={goalCover} />
        <Circle cx={196.5} cy={cy - 226} r={catchRadius} color="white" opacity={catchAlpha} style="stroke" strokeWidth={1.5} />
        <Group transform={innerTransform}><Circle cx={0} cy={0} r={17} color="#DDDDDD" /></Group>
      </Canvas>
      <Text style={[styles.title, { top: top + 98 }]}>Retirement</Text>
      <LiveText value={current} style={{ position: 'absolute', left: 24, top: top + 143, fontSize: 17, color: '#4F4F4F', width: 320 }} />
      <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="Retirement goal marker" accessibilityHint="Drag along the dial or adjust to close the gap" accessibilityActions={[{ name: 'increment', label: 'Close 20 percent of the gap' }, { name: 'decrement', label: 'Reopen 20 percent of the gap' }]} onAccessibilityAction={e => { if (!autoPlay && sleeping.get()) { sleeping.set(false); epoch.set(-1); setAwake(true); } manual.set(clamp(value.get() + (e.nativeEvent.actionName === 'increment' ? 0.2 : -0.2))); releaseFrom.set(manual.get()); releaseVelocity.set(0); touched.set(clock.get()); resolveAt.set(-100); tapHaptic(); }} style={[styles.arrow, thumbStyle]}><Text style={styles.arrowText}>↗</Text></Animated.View>
      <Text accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none" style={styles.summaryMeasure} onTextLayout={e => { const measured = e.nativeEvent.lines[0]?.width; if (measured) setAmountWidth(Math.ceil(measured)); }}>₹ 1.17 L</Text>
      <Animated.View style={[styles.summary, { top: cy - 47 }, offStyle]}>
        <Text style={styles.summaryText}>Off-Track by</Text><LiveText value={missing} style={{ fontSize: 18, fontWeight: '600', color: RED, width: amountWidth, marginLeft: 6 }} />
      </Animated.View>
      <Animated.Text style={[styles.done, { top: cy - 46 }, doneStyle]}>Back on track</Animated.Text>
      <Text style={[styles.target, { top: cy - 12 }]}>Target ₹ 90.17 L</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Resolve retirement gap" onPress={resolve} style={[styles.resolve, { top: cy + 42 }]}><Text style={styles.resolveText}>Resolve</Text></Pressable>
      <Text style={[styles.hint, { bottom: Math.max(insets.bottom / scale, 16) + 15 }]}>Drag the marker to close the gap</Text>
    </View>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#DEDEDE', overflow: 'hidden' }, live: { padding: 0, margin: 0, fontVariant: ['tabular-nums'], height: 30 },
  title: { position: 'absolute', left: 24, fontSize: 29, fontWeight: '600', color: '#141414' },
  arrow: { position: 'absolute', left: 0, top: 0, width: 51, height: 51, alignItems: 'center', justifyContent: 'center' }, arrowText: { fontSize: 25, color: '#292929', fontWeight: '600' },
  summaryMeasure: { position: 'absolute', opacity: 0, fontSize: 18, fontWeight: '600', fontVariant: ['tabular-nums'] },
  summary: { position: 'absolute', width: 393, height: 30, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }, summaryText: { fontSize: 18, color: '#141414', fontWeight: '600' },
  done: { position: 'absolute', width: 393, textAlign: 'center', fontSize: 18, fontWeight: '600', color: '#141414' }, target: { position: 'absolute', width: 393, textAlign: 'center', fontSize: 16, color: '#525252' },
  resolve: { position: 'absolute', left: 147.5, width: 98, height: 44, borderRadius: 22, backgroundColor: '#D0D0D0', alignItems: 'center', justifyContent: 'center' }, resolveText: { fontSize: 17, fontWeight: '600', color: '#268BF4' },
  hint: { position: 'absolute', width: 393, textAlign: 'center', fontSize: 11, color: '#00000073' },
});
