import { Canvas, FilterMode, MipmapMode, Picture, Skia, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import Svg, { Ellipse, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 10;
type Props = { autoPlay?: boolean; renderTime?: number };
type Sphere = { x: number; y: number; z: number; radius: number; material: number };
type Pose = { medium: number; dense: number; sparse: number; scale: number };
type Motion = { now: number; epoch: number; dragging: boolean; captured: number; time: number; yaw: number; velocity: number; released: number };
// Generated directly from the approved Swift point tables, with the same seeded contour sampler.
const SHAPES = require('./assets/donation-spheres.json') as { medium: Sphere[]; dense: Sphere[]; sparse: Sphere[] };
const OPTIONS = ['Feeling generous? Send a one-time donation', 'Prefer a bit more planning? Set it up monthly', 'Give a little, but often? Chip in your change'];
function smooth(x: number) { 'worklet'; const p = Math.min(1, Math.max(0, x)); return p * p * (3 - 2 * p); }
function mix(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
const POSE_KEYS = [
  [0,1,0,0,1],
  [0.15,1,0,0,1],
  [1.3,1,0,0,0.82],
  [1.5,0,1,0,1.1],
  [3.08,0,1,0,0.82],
  [3.28,0,0,1,1],
  [4.56,0,0,1,0.76],
  [4.8,1,0,0,1.02],
  [6.26,1,0,0,0.82],
  [6.48,0,1,0,1.1],
  [7.95,0,1,0,0.82],
  [8.18,0,0,1,1],
  [9.17,0,0,1,0.85],
  [9.75,1,0,0,1],
  [10,1,0,0,1],
];
function pose(time: number): Pose {
  'worklet'; const t = Math.max(0, time) % LOOP;
  for (let i = 1; i < POSE_KEYS.length; i++) {
    if (t > POSE_KEYS[i][0]) continue;
    const a = POSE_KEYS[i - 1], b = POSE_KEYS[i], f = smooth((t - a[0]) / (b[0] - a[0]));
    return { medium: mix(a[1], b[1], f), dense: mix(a[2], b[2], f), sparse: mix(a[3], b[3], f), scale: mix(a[4], b[4], f) };
  }
  return { medium: 1, dense: 0, sparse: 0, scale: 1 };
}
function drive(m: Motion, now: number, autoPlay: boolean, reduced: boolean, renderTime?: number) {
  'worklet';
  if (reduced) return { time: 0, yaw: 0 };
  if (renderTime !== undefined) return { time: renderTime, yaw: 0 };
  if (m.dragging) return { time: m.time, yaw: m.yaw };
  if (m.released >= 0) {
    const age = Math.max(0, now - m.released), carried = m.time + m.velocity * 0.14 * (1 - Math.exp(-age / 0.14));
    if (age < 2) return { time: carried, yaw: m.yaw * Math.exp(-5 * age) };
    if (age < 2.65) return { time: carried * (1 - smooth((age - 2) / 0.65)), yaw: 0 };
    return { time: autoPlay ? age - 2.65 : 0, yaw: 0 };
  }
  return { time: autoPlay ? Math.max(0, now - m.epoch) : 0, yaw: 0 };
}
function drawHeart(c: SkCanvas, time: number, yaw: number, centerY: number, atlas: SkImage) {
  'worklet';
  const t = Math.max(0, time) % LOOP, p = pose(t);
  const envelope = smooth(t / 0.5) * (1 - smooth((t - 9.17) / 0.58));
  const turn = 0.13 * Math.sin(2 * Math.PI * t / 4.8) * envelope + yaw;
  const roll = 0.028 * Math.sin(4 * Math.PI * t / 4.8) * envelope;
  const spheres: Sphere[] = [];
  for (let i = 0; i < 120; i++) {
    const m = SHAPES.medium[i % 48], d = SHAPES.dense[i], s = SHAPES.sparse[i % 12];
    const radius = ((i < 48 ? m.radius : 0) * p.medium + d.radius * p.dense + (i < 12 ? s.radius : 0) * p.sparse) * p.scale;
    if (radius <= 0.25) continue;
    const x = (m.x * p.medium + d.x * p.dense + s.x * p.sparse) * p.scale;
    const y = (m.y * p.medium + d.y * p.dense + s.y * p.sparse) * p.scale;
    const z = m.z * p.medium + d.z * p.dense + s.z * p.sparse;
    const rx = x * Math.cos(turn) + z * Math.sin(turn), rz = z * Math.cos(turn) - x * Math.sin(turn), depth = 760 / (760 - rz);
    spheres.push({ x: (rx * Math.cos(roll) - y * Math.sin(roll)) * depth, y: (rx * Math.sin(roll) + y * Math.cos(roll)) * depth,
      z: rz, radius: radius * depth, material: i < 48 ? m.material : d.material });
  }
  const paint = Skia.Paint(); paint.setAntiAlias(true);
  spheres.sort((a, b) => a.z - b.z);
  for (const sphere of spheres) {
    const extent = sphere.radius * 256 / 112;
    c.drawImageRectOptions(atlas, Skia.XYWHRect(sphere.material * 256, 0, 256, 256),
      Skia.XYWHRect(196.5 + sphere.x - extent / 2, centerY + sphere.y - extent / 2, extent, extent), FilterMode.Linear, MipmapMode.None, paint);
  }
}
function tactile() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

/** The approved Swift screen, using the same generated sphere atlas and exact point tables. */
export function DonationSphereHeart({ autoPlay = false, renderTime }: Props) {
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced), [manualClock, setManualClock] = useState(false);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const atlas = useImage(require('./assets/donation-spheres.png'));
  const scale = size.width / 393, lowerShift = scale > 0 ? Math.max(0, size.height / scale - 852) : 0;
  const motion = useSharedValue<Motion>({ now: 0, epoch: 0, dragging: false, captured: 0, time: 0, yaw: 0, velocity: 0, released: -1 });
  useEffect(() => {
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', value => { setReduced(value); setManualClock(false); });
    return () => sub.remove();
  }, []);
  useEffect(() => {
    const now = Date.now() / 1000;
    motion.set({ now, epoch: now, dragging: false, captured: 0, time: 0, yaw: 0, velocity: 0, released: -1 });
  }, [atlas, motion, reduced]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = motion.get();
    if (!old.dragging && old.released >= 0 && now - old.released >= 2.65) {
      motion.set({ ...old, now, epoch: old.released + 2.65, released: -1, time: 0, yaw: 0 });
      scheduleOnRN(setManualClock, false);
    } else motion.set({ ...old, now });
  }, false);
  useEffect(() => {
    ticker.setActive(!!atlas && scale > 0 && !reduced && renderTime === undefined && (autoPlay || manualClock));
    return () => ticker.setActive(false);
  }, [atlas, autoPlay, manualClock, reduced, renderTime, scale, ticker]);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (atlas && scale > 0) {
      const m = motion.get(), current = drive(m, m.now, autoPlay, reduced, renderTime);
      canvas.scale(scale, scale); drawHeart(canvas, current.time, current.yaw, 440 + lowerShift * 0.34, atlas);
    }
    return recorder.finishRecordingAsPicture();
  });
  const demonstrate = useCallback(() => {
    if (renderTime !== undefined) return;
    tactile();
    if (reduced) return;
    const now = Date.now() / 1000;
    motion.set(old => ({ ...old, now, dragging: false, time: 1.55, yaw: 0, velocity: 0, released: now })); setManualClock(true);
  }, [motion, reduced, renderTime]);
  const reset = useCallback(() => {
    const now = Date.now() / 1000;
    motion.set({ now, epoch: now, dragging: false, captured: 0, time: 0, yaw: 0, velocity: 0, released: -1 }); setManualClock(false);
  }, [motion]);
  const begin = useCallback(() => {
    'worklet';
    if (reduced || renderTime !== undefined) return;
    const now = Date.now() / 1000, old = motion.get(), current = drive(old, now, autoPlay, reduced);
    motion.set({ ...old, now, dragging: true, captured: current.time % LOOP, time: current.time % LOOP, yaw: 0, released: -1 });
    scheduleOnRN(setManualClock, true);
  }, [autoPlay, motion, reduced, renderTime]);
  const end = useCallback((translationX: number, translationY: number, velocityX: number, velocityY: number) => {
    'worklet';
    if (reduced || renderTime !== undefined || scale <= 0) return;
    const now = Date.now() / 1000, extra = Math.hypot(velocityX, velocityY) * 0.2 / scale;
    motion.set(old => ({ ...old, now, dragging: false, time: Math.hypot(translationX, translationY) < 8 ? 1.55 : old.time,
      velocity: Math.min(3, extra / 43), released: now }));
    scheduleOnRN(tactile);
  }, [motion, reduced, renderTime, scale]);
  const cancel = useCallback(() => {
    'worklet';
    if (motion.get().dragging) motion.set(old => ({ ...old, now: Date.now() / 1000, dragging: false, velocity: 0, released: Date.now() / 1000 }));
  }, [motion]);
  const pan = Gesture.Pan().minDistance(0).onBegin(begin).onUpdate(event => {
    if (reduced || renderTime !== undefined || scale <= 0) return;
    const distance = Math.hypot(event.translationX / scale, event.translationY / scale);
    motion.set(old => ({ ...old, time: old.captured + Math.min(3.3, distance / 43), yaw: Math.max(-0.55, Math.min(0.55, event.translationX / (300 * scale))) }));
  }).onEnd(event => end(event.translationX, event.translationY, event.velocityX, event.velocityY)).onFinalize((_event, success) => {
    if (!success) cancel();
  });
  const onLayout = useCallback((event: LayoutChangeEvent) => setSize(event.nativeEvent.layout), []);
  const box = (x: number, y: number, width: number, height: number) => ({ position: 'absolute' as const, left: x * scale, top: y * scale, width: width * scale, height: height * scale });
  return <View style={styles.screen} onLayout={onLayout}>
    <StatusBar style="dark" />
    {scale > 0 && <>
      <View pointerEvents="none" style={[box(14, 94, 365, 510 + lowerShift * 0.34), { backgroundColor: '#FFFFFF', borderRadius: 16 * scale }]} />
      <View pointerEvents="none" style={[box(14, 620 + lowerShift, 365, 204), { backgroundColor: '#FFFFFF', borderRadius: 16 * scale }]} />
      <Pressable onPress={reset} accessibilityRole="button" accessibilityLabel="Reset donation heart" style={[box(8, 53, 40, 40), styles.center]}>
        <Svg width={13 * scale} height={13 * scale} viewBox="0 0 16 16" stroke="#0E0E0F" strokeWidth={1.4}><Path d="m3 3 10 10M13 3 3 13" /></Svg>
      </Pressable>
      <Text allowFontScaling={false} style={[box(26, 116, 341, 84), styles.heading, { fontSize: 35 * scale, lineHeight: 40 * scale, letterSpacing: -0.8 * scale }]}>DONATE WITH A{'\n'}TAP</Text>
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Picture picture={picture} /></Canvas>
      <GestureDetector gesture={pan}><View style={box(15, 260 + lowerShift * 0.34, 363, 362)} accessible accessibilityRole="button" accessibilityLabel="Glass sphere heart" accessibilityHint="Drag to gather the spheres or spread them apart." onAccessibilityTap={demonstrate} /></GestureDetector>
      {OPTIONS.map((title, i) => <View key={title} style={[box(26, 631 + i * 51 + lowerShift, 341, 28), styles.option, { gap: 16 * scale }]}>
        <OptionIcon index={i} scale={scale} />
        <Text allowFontScaling={false} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.9} style={{ color: '#0E0E0F', fontSize: 13.2 * scale, includeFontPadding: false, flexShrink: 1 }}>{title}</Text>
      </View>)}
      <Pressable onPress={demonstrate} accessibilityRole="button" accessibilityLabel="Donate now" accessibilityHint="Play the sphere heart animation." style={[box(16, 759 + lowerShift, 361, 48), styles.center, { borderRadius: 24 * scale, backgroundColor: '#181B1B', boxShadow: [{ color: 'rgba(0,0,0,0.15)', blurRadius: 6 * scale, offsetX: 0, offsetY: 2 * scale }, { color: 'rgba(0,0,0,0.10)', blurRadius: 26 * scale, offsetX: 0, offsetY: 9 * scale }] }]}>
        <Text allowFontScaling={false} style={{ color: '#FFFFFF', fontSize: 13.5 * scale, fontWeight: '500', includeFontPadding: false }}>Donate now</Text>
      </Pressable>
    </>}
  </View>;
}
function OptionIcon({ index, scale }: { index: number; scale: number }) {
  return <Svg width={17 * scale} height={17 * scale} viewBox="0 0 18 18" accessibilityElementsHidden>
    {index === 0 ? <Path d="M2 2.6 16.1 8 2 15.4l2.4-5.6L11.3 8 4.4 6.3Z" fill="#0E0E0F" />
      : index === 1 ? <><Rect x={2.2} y={3.5} width={13.6} height={12.3} rx={1.5} fill="none" stroke="#0E0E0F" strokeWidth={1.4} /><Path d="M2.3 7h13.2M5.3 1.8v3.4m7.4-3.4v3.4" stroke="#0E0E0F" strokeWidth={1.4} /></>
        : <>{[0, 1].flatMap(column => [0, 1, 2].map(row => <Ellipse key={`${column}-${row}`} cx={5 + column * 8} cy={3.5 + row * 4 + column * 4} rx={3} ry={1.5} fill="#0E0E0F" />))}</>}
  </Svg>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F8F6FA', overflow: 'hidden' },
  center: { alignItems: 'center', justifyContent: 'center' },
  heading: { color: '#0E0E0F', fontWeight: '900', includeFontPadding: false },
  option: { flexDirection: 'row', alignItems: 'center' },
});
