import { Canvas, Circle, Group, Image as SkiaImage, Line, LinearGradient, Path, Skia, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { POSES } from './poses';
export const LOOP = 8;
function smooth(v: number) { 'worklet'; const x = Math.min(1, Math.max(0, v)); return x * x * (3 - 2 * x); }
function flap(t: number) {
  'worklet'; if (t < 0.8 || t >= 7.2) return 0;
  const u = t - 0.8; const x = u < 0.4 ? u / 0.4 : (6.4 - u) / 0.4;
  const integral = x * x * x - 0.5 * x * x * x * x;
  return (u < 0.4 ? 0.2 * integral : u > 6 ? 3 - 0.2 * integral : 0.5 * (u - 0.2)) % 1;
}
function haptic() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
function Cloud({ i, time, recovery, dy, image }: { i: number; time: SharedValue<number>; recovery: SharedValue<number>; dy: number; image: ReturnType<typeof useImage> }) {
  const w = 170 + (i % 3) * 38;
  const x = useDerivedValue(() => {
    const start = i * 0.173; const u = (time.get() / LOOP * (i % 2 === 0 ? 2 : 1) + start) % 1;
    const delta = start - u - Math.round(start - u);
    return 443 - (493 + w) * ((u + delta * recovery.get() + 1) % 1);
  });
  const y = useDerivedValue(() => dy + 120 + i * 92 + 22 * ((1 - recovery.get()) * Math.sin(2 * Math.PI * time.get() / LOOP + i) + recovery.get() * Math.sin(i)));
  return <SkiaImage image={image} x={x} y={y} width={w} height={w * 0.375} opacity={i % 2 === 0 ? 0.44 : 0.24} fit="fill" />;
}
export function PixelWingFlight({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions(); const insets = useSafeAreaInsets(); const scale = width / 393; const h = height / scale; const dy = (h - 1280 * 393 / 720) / 2;
  const [isPaused, setIsPaused] = useState(false);
  const [awake, setAwake] = useState(false);
  const top = insets.top / scale; const bottom = Math.max(insets.bottom / scale, 20); const reduced = useReducedMotion();
  const cloud = useImage(require('./assets/cloud.png')); const grain = useImage(require('./assets/grain.png'));
  const poses = useMemo(() => POSES.map(code => {
    const p = Skia.Path.Make();
    for (let index = 0; index < code.length; index++) {
      const bits = parseInt(code[index], 16);
      for (let bit = 0; bit < 4; bit++) if (bits & (1 << (3 - bit))) {
        const x = ((index % 12) * 4 + bit) * 10 + 140; const y = Math.floor(index / 12) * 10 + 250;
        p.addRect({ x: x * 393 / 720 - 1.25, y: y * 393 / 720 - 1.25, width: 2.5, height: 2.5 });
      }
    } return p;
  }), []);
  const bounds = useMemo(() => poses.map(p => p.getBounds()), [poses]);
  const clock = useSharedValue(0); const epoch = useSharedValue(-1); const touched = useSharedValue(-100); const manual = useSharedValue(0); const dragStart = useSharedValue(0); const isTouching = useSharedValue(false); const paused = useSharedValue(false); const held = useSharedValue(0); const coast = useSharedValue(0); const heldReturn = useSharedValue(0); const manualWingOffset = useSharedValue(0); const heldWingOffset = useSharedValue(0);
  const sleeping = useSharedValue(!autoPlay);
  const frame = useFrameCallback(({ timestamp }) => { if (epoch.get() < 0) epoch.set(timestamp - clock.get() * 1000); clock.set((timestamp - epoch.get()) / 1000);
    if (!autoPlay && !isTouching.get() && touched.get() >= 0 && clock.get() - touched.get() >= 2.6 && !sleeping.get()) { sleeping.set(true); epoch.set(-1); scheduleOnRN(setAwake, false); } }, false);
  useEffect(() => { frame.setActive(previewTime === undefined && !reduced && !isPaused && (autoPlay || awake)); return () => frame.setActive(false); }, [frame, previewTime, reduced, autoPlay, awake, isPaused]);
  const time = useDerivedValue(() => {
    if (previewTime !== undefined) return previewTime % LOOP;
    if (reduced) return 0;
    if (isTouching.get()) return manual.get();
    if (paused.get()) return held.get();
    const dt = clock.get() - touched.get();
    if (dt < 2.6) return (manual.get() + coast.get() * (1 - Math.exp(-dt / 0.1)) + LOOP) % LOOP;
    return autoPlay ? (touched.get() < 0 ? clock.get() : dt - 2.6) % LOOP : 0;
  });
  const recovery = useDerivedValue(() => {
    if (previewTime !== undefined || reduced || isTouching.get()) return 0;
    if (paused.get()) return heldReturn.get();
    const dt = clock.get() - touched.get(); return dt < 2.6 ? smooth((dt - 2) / 0.6) : 0;
  });
  const wingOffset = useDerivedValue(() => {
    if (previewTime !== undefined || reduced) return 0;
    if (paused.get()) return heldWingOffset.get();
    return isTouching.get() || clock.get() - touched.get() < 2.6 ? manualWingOffset.get() : 0;
  });
  const position = useDerivedValue(() => flap((time.get() + wingOffset.get() + LOOP) % LOOP) * poses.length);
  const pose = useDerivedValue(() => Math.floor(position.get()) % poses.length);
  const next = useDerivedValue(() => (pose.get() + 1) % poses.length);
  const mix = useDerivedValue(() => smooth(position.get() - pose.get()));
  const path = useDerivedValue(() => poses[pose.get()]);
  const second = useDerivedValue(() => poses[next.get()]);
  const firstAlpha = useDerivedValue(() => (1 - mix.get()) * (1 - recovery.get()));
  const secondAlpha = useDerivedValue(() => mix.get() * (1 - recovery.get()));
  const y1 = useDerivedValue(() => ((bounds[pose.get()].y * (1 - mix.get()) + bounds[next.get()].y * mix.get()) * (1 - recovery.get()) + bounds[0].y * recovery.get()) + dy - 9);
  const y2 = useDerivedValue(() => (((bounds[pose.get()].y + bounds[pose.get()].height) * (1 - mix.get()) + (bounds[next.get()].y + bounds[next.get()].height) * mix.get()) * (1 - recovery.get()) + (bounds[0].y + bounds[0].height) * recovery.get()) + dy + 9);
  const topLeft = useDerivedValue(() => vec(0, y1.get())); const topRight = useDerivedValue(() => vec(393, y1.get()));
  const lowLeft = useDerivedValue(() => vec(0, y2.get())); const lowRight = useDerivedValue(() => vec(393, y2.get()));
  const captionStyle = useAnimatedStyle(() => ({ top: (y1.get() + y2.get()) / 2 + 18.5 }));
  const pan = Gesture.Pan().minDistance(4).onStart(() => { if (!autoPlay && sleeping.get()) { sleeping.set(false); epoch.set(-1); scheduleOnRN(setAwake, true); } manualWingOffset.set(3 + 2 * flap((time.get() + wingOffset.get() + LOOP) % LOOP) - time.get()); dragStart.set(time.get()); manual.set(time.get()); isTouching.set(true); touched.set(clock.get()); paused.set(false); scheduleOnRN(setIsPaused, false); coast.set(0); }).onUpdate(e => {
    manual.set(((dragStart.get() + e.translationX / width * 2) % LOOP + LOOP) % LOOP); touched.set(clock.get());
  }).onEnd(e => { coast.set(Math.min(0.36, Math.max(-0.36, e.velocityX / width * 2 * 0.12))); }).onFinalize(() => { if (!isTouching.get()) return; isTouching.set(false); touched.set(clock.get()); });
  const pause = () => {
    if (paused.get()) {
      epoch.set(-1); paused.set(false); setIsPaused(false);
    } else { held.set(time.get()); heldReturn.set(recovery.get()); heldWingOffset.set(wingOffset.get()); epoch.set(-1); paused.set(true); setIsPaused(true); }
    haptic();
  };
  return <GestureDetector gesture={pan}><View style={styles.screen}><StatusBar style="light" />
    <View style={{ width: 393, height: h, transform: [{ scale }], transformOrigin: 'top left' }}>
      <Canvas accessible accessibilityLabel="Flight canvas" accessibilityHint="Drag horizontally to steer the wings" style={StyleSheet.absoluteFill}>
        <Path path={`M0 0H393V${h}H0Z`}><LinearGradient start={vec(0, 0)} end={vec(393, h)} colors={['#3836B8', '#333BC7', '#3033AD']} /></Path>
        <SkiaImage image={grain} x={0} y={0} width={393} height={h} opacity={0.7} fit="fill" />
        <Group transform={[{ translateY: dy }]} layer>
          <Path path={path} color="#F7F7F7" opacity={firstAlpha} blendMode="plus" />
          <Path path={second} color="#F7F7F7" opacity={secondAlpha} blendMode="plus" />
          <Path path={poses[0]} color="#F7F7F7" opacity={recovery} blendMode="plus" />
        </Group>
        <Line p1={vec(86, 0)} p2={vec(86, h)} color="rgba(255,255,255,.48)" strokeWidth={0.65} />
        <Line p1={vec(306, 0)} p2={vec(306, h)} color="rgba(255,255,255,.48)" strokeWidth={0.65} />
        <Line p1={topLeft} p2={topRight} color="rgba(255,255,255,.48)" strokeWidth={0.65} />
        <Line p1={lowLeft} p2={lowRight} color="rgba(255,255,255,.48)" strokeWidth={0.65} />
        {Array.from({ length: 14 }, (_, i) => <Circle key={i} cx={(i * 97 + 29) % 393} cy={(i * 173 + 137) % 720 + dy} r={i % 3 === 0 ? 1 : 0.5} color={['white', '#53D56D', '#F6A144', '#66D6F0'][i % 4]} opacity={0.7} />)}
        {Array.from({ length: 6 }, (_, i) => <Cloud key={i} i={i} time={time} recovery={recovery} dy={dy} image={cloud} />)}
      </Canvas>
      <View style={[styles.top, { top: top + 22 }]}><Text style={styles.eyebrow}>AER / FIELD NOTES</Text><Text style={styles.number}>01 / FLIGHT</Text></View>
      <Animated.Text numberOfLines={1} style={[styles.caption, captionStyle]}>Grow wings and fly</Animated.Text>
      <Text style={[styles.title, { bottom: bottom + 96 }]}>A little more sky.</Text>
      <Text style={[styles.subtitle, { bottom: bottom + 74 }]}>A quiet moment, above it all.</Text>
      <Text style={[styles.hint, { bottom: bottom + 24 }]}>DRAG TO FLY</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Pause or resume flight" onPress={pause} style={[styles.pause, { bottom: bottom + 7 }]}><Canvas style={{ width: 16, height: 16 }}><Path path={isPaused ? 'M4 2 L13 8 L4 14 Z' : 'M3.5 2 H6.5 V14 H3.5 Z M9.5 2 H12.5 V14 H9.5 Z'} color="#FFFFFF" /></Canvas></Pressable>
    </View>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden', backgroundColor: '#3636B9' }, top: { position: 'absolute', left: 24, width: 345, flexDirection: 'row', justifyContent: 'space-between' }, eyebrow: { fontSize: 10, fontWeight: '500', letterSpacing: 2, color: '#FFFFFFCC' }, number: { fontSize: 10, fontWeight: '500', letterSpacing: 1, color: '#FFFFFFB3' },
  caption: { position: 'absolute', left: 247, width: 140, fontSize: 11, lineHeight: 13, textAlign: 'center', fontStyle: 'italic', fontWeight: '500', color: '#FFFFFF', transform: [{ rotate: '-90deg' }] },
  title: { position: 'absolute', left: 24, fontSize: 25, fontWeight: '500', color: '#FFFFFF' }, subtitle: { position: 'absolute', left: 24, fontSize: 12, color: '#FFFFFFAD' }, hint: { position: 'absolute', left: 24, fontSize: 9, fontWeight: '500', letterSpacing: 1.8, color: '#FFFFFFA6' },
  pause: { position: 'absolute', right: 24, width: 48, height: 48, borderRadius: 24, borderWidth: 1, borderColor: '#FFFFFF73', alignItems: 'center', justifyContent: 'center' },
});
