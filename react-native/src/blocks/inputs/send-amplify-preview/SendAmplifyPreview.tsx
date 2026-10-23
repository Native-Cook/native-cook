import {
  Blur, BlurMask, BlurStyle, Canvas, Circle, FillType, FilterMode, Group, Image as SkiaImage,
  LinearGradient, MipmapMode, Oval, Paint, PaintStyle, Picture, Rect, Skia, TileMode,
  type SkImage, type SkPath, type SkPicture, matchFont, rect, rrect,
} from '@shopify/react-native-skia';
import { BlurTargetView, BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, type RefObject, useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue,
  useFrameCallback, useReducedMotion, useSharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = { autoPlay?: boolean; previewTime?: number };
type Pose = { scale: number; right: number; bodyY: number; focus: number; railFocus: number; selection: number; rotation: number; stretch: number; ink: number; impact: number; impactProgress: number };
type Frame = [number, number, number, number, number, number];
type Manual = { captured: Pose; eventStarted: number; lastTouch: number; row: number; releaseFocus: number; dragStartFocus: number; directRail: boolean; dragging: boolean; activated: boolean; live: boolean; hasSelection: boolean };
export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const NAMES = ['Amplify', 'Lift', 'Whisper', 'Drift'];
const REST: Pose = { scale: 0.16, right: 324, bodyY: 461, focus: 3, railFocus: 3, selection: 0, rotation: 0, stretch: 1, ink: 0, impact: 0, impactProgress: 0 };
const INITIAL_MANUAL: Manual = { captured: REST, eventStarted: -1, lastTouch: -1, row: 3, releaseFocus: 3, dragStartFocus: 3, directRail: false, dragging: false, activated: false, live: false, hasSelection: false };
const FONT = Platform.OS === 'ios' ? '.AppleSystemUIFont' : 'sans-serif';
const WHISPER: Frame[] = [[0, .5, .4, 416, 332, 0], [.05, 5.8, 4.4, 415.6, 331.9, 0], [.25, 34.9, 24.7, 370.4, 331.9, 0], [.55, 50.9, 37.8, 362.4, 329, 0], [1.15, 50.9, 37.8, 362.4, 329, 0]];
const LIFT: Frame[] = [[0, .5, .4, 375, 334.8, 0], [.0667, 11.6, 8.7, 366.8, 334.8, 0], [.30, 74.2, 55.3, 299.8, 327.5, 0], [.5667, 93.2, 68.4, 290.4, 324.6, 0], [.7333, 93.2, 68.4, 290.4, 324.6, 0], [1.2833, 40.8, 30.6, 315.1, 330.4, 0], [1.45, 40.8, 30.6, 315.1, 330.4, 0]];
const AMPLIFY: Frame[] = [[0, 40.8, 30.6, 315.1, 330.4, 0], [.0167, 141.2, 116.4, 333.3, 314.4, 12.1], [.05, 189.2, 155.7, 334.1, 309, 11.5], [.10, 240.2, 195, 329, 302, 9.5], [.1667, 285.3, 225.6, 312.2, 295.5, 5], [.20, 260.5, 203.8, 299.8, 299.8, 3], [.25, 206.7, 157.2, 283.8, 307, 1.36], [.30, 133.9, 101.9, 273.6, 320, -1.1], [.3333, 64, 48, 267.1, 329, 0], [.35, 37.8, 27.7, 262.7, 331.9, 0], [.45, 40.8, 30.6, 264.2, 330.4, 0], [1.10, 40.8, 30.6, 264.2, 330.4, 0]];

function seconds() { 'worklet'; return Date.now() / 1000; }
function clamp(value: number, low = 0, high = 1) { 'worklet'; return Math.min(high, Math.max(low, value)); }
function ease(raw: number) { 'worklet'; const u = clamp(raw); return u * u * (3 - 2 * u); }
function selected(pose: Pose) { 'worklet'; return clamp(Math.round(pose.focus), 0, 3); }
function lerp(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
function mixPose(a: Pose, b: Pose, p: number): Pose {
  'worklet';
  return { scale: lerp(a.scale, b.scale, p), right: lerp(a.right, b.right, p), bodyY: lerp(a.bodyY, b.bodyY, p), focus: lerp(a.focus, b.focus, p), railFocus: lerp(a.railFocus, b.railFocus, p), selection: lerp(a.selection, b.selection, p), rotation: lerp(a.rotation, b.rotation, p), stretch: lerp(a.stretch, b.stretch, p), ink: lerp(a.ink, b.ink, p), impact: lerp(a.impact, b.impact, p), impactProgress: lerp(a.impactProgress, b.impactProgress, p) };
}
/** Clamp each cubic Hermite segment to its measured endpoint range. */
function sampleValue(frames: Frame[], index: number, key: number, u: number): number {
  'worklet';
  const a = frames[index]; const b = frames[index + 1]; const x = a[key]; const y = b[key];
  if (x === y) return x;
  const before = frames[Math.max(0, index - 1)]; const after = frames[Math.min(frames.length - 1, index + 2)];
  const duration = b[0] - a[0];
  const m0 = index === 0 ? 0 : (y - before[key]) / (b[0] - before[0]);
  const m1 = index + 2 === frames.length ? 0 : (after[key] - x) / (after[0] - a[0]);
  const value = (2 * u ** 3 - 3 * u * u + 1) * x + (u ** 3 - 2 * u * u + u) * m0 * duration + (-2 * u ** 3 + 3 * u * u) * y + (u ** 3 - u * u) * m1 * duration;
  return clamp(value, Math.min(x, y), Math.max(x, y));
}
function sample(frames: Frame[], time: number): Frame {
  'worklet';
  for (let index = 0; index < frames.length - 1; index++) {
    if (time >= frames[index + 1][0]) continue;
    const u = clamp((time - frames[index][0]) / (frames[index + 1][0] - frames[index][0]));
    return [time, sampleValue(frames, index, 1, u), sampleValue(frames, index, 2, u), sampleValue(frames, index, 3, u), sampleValue(frames, index, 4, u), sampleValue(frames, index, 5, u)];
  }
  return frames[frames.length - 1];
}
/** Row selection starts an independent event, never a continuous strength slider. */
function eventPose(row: number, time: number): Pose {
  'worklet';
  const t = Math.max(0, time); const pose = { ...REST, focus: row, railFocus: row, selection: 1 };
  if (row === 3) return { ...pose, scale: .14, bodyY: 409.7, right: 334.5, ink: ease(t / .16) };
  const values = sample(row === 2 ? WHISPER : row === 1 ? LIFT : AMPLIFY, t);
  const angle = Math.abs(values[5]) * Math.PI / 180; const c = Math.cos(angle); const sn = Math.sin(angle); const determinant = Math.max(.1, c * c - sn * sn);
  const sx = Math.max(.001, (values[1] * c - values[2] * sn) / (270 * determinant));
  const sy = Math.max(.001, (values[2] * c - values[1] * sn) / (200 * determinant));
  return { ...pose, scale: sx, stretch: sy / sx, right: values[4], bodyY: values[3], rotation: values[5], railFocus: row === 0 ? 1 - ease(t / .17) : row, impact: row === 0 ? ease((t - .35) / .10) * (1 - ease((t - .48) / .27)) : 0, impactProgress: row === 0 ? ease((t - .35) / .23) : 0 };
}
function loopPose(raw: number): Pose {
  'worklet';
  const t = (raw % LOOP + LOOP) % LOOP;
  if (t < 1.1 || t >= 7) return REST;
  if (t < 1.35) return mixPose(REST, eventPose(3, .25), ease((t - 1.1) / .25));
  if (t < 2.95) return eventPose(3, t - 1.1);
  if (t < 4.1) return eventPose(2, t - 2.95);
  if (t < 5.55) return eventPose(1, t - 4.1);
  if (t < 6.65) return eventPose(0, t - 5.55);
  return mixPose(eventPose(0, 1.1), REST, ease((t - 6.65) / .35));
}
function currentPose(now: number, manual: Manual, epoch: number, reduced: boolean, autoPlay: boolean, previewTime?: number): Pose {
  'worklet';
  if (reduced) return { ...REST, focus: manual.row, railFocus: manual.row, selection: manual.hasSelection ? 1 : 0 };
  if (previewTime !== undefined) return loopPose(previewTime);
  if (manual.live) {
    if (manual.dragging && !manual.activated) return manual.captured;
    const age = Math.max(0, now - manual.eventStarted);
    const shown = mixPose(manual.captured, eventPose(manual.row, age), ease(age / .1));
    if (manual.dragging) return shown;
    const elapsed = now - manual.lastTouch;
    if (elapsed < 2.6) return mixPose(shown, REST, ease((elapsed - 2) / .6));
  }
  if (!autoPlay) return REST;
  return loopPose(manual.lastTouch < 0 ? now - epoch : now - manual.lastTouch - 2.6);
}
function haptic() { if (Platform.OS !== 'web') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
function hash(index: number) { const value = Math.sin(index * 12.9898) * 43758.5453; return value - Math.floor(value); }

/** Cache the explicit even-odd 60 silhouette, seven-layer bevel and cloth cap at 3×. */
function makeNumeral(): SkImage | null {
  const recorder = Skia.PictureRecorder(); const canvas = recorder.beginRecording(rect(0, 0, 296, 300));
  const glyph = Skia.Path.MakeFromSVGString('M72 0L112 0L57 77C65 73 74 71 83 72C115 75 134 99 134 132C135 172 108 200 67 200C26 200 0 176 0 141C0 122 7 104 17 88L72 0Z')!;
  glyph.addOval(rect(36, 111, 61, 58));
  glyph.addOval(rect(142, 0, 128, 200));
  glyph.addOval(rect(179, 35, 54, 130));
  glyph.setFillType(FillType.EvenOdd);
  glyph.transform([1, 0, 4, 0, 1, 83, 0, 0, 1]);
  const paint = Skia.Paint(); paint.setAntiAlias(true);
  const fill = (color: string) => { paint.setStyle(PaintStyle.Fill); paint.setShader(null); paint.setMaskFilter(null); paint.setColor(Skia.Color(color)); };
  const gradient = (path: SkPath, colors: string[], positions: number[], start: { x: number; y: number }, end: { x: number; y: number }) => {
    fill('white'); paint.setShader(Skia.Shader.MakeLinearGradient(start, end, colors.map((color) => Skia.Color(color)), positions, TileMode.Clamp)); canvas.drawPath(path, paint); paint.setShader(null);
  };
  const shadow = (path: SkPath, x: number, y: number, blur: number, color: string) => {
    canvas.save(); canvas.translate(x, y); fill(color); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur / 2, true)); canvas.drawPath(path, paint); canvas.restore(); paint.setMaskFilter(null);
  };
  shadow(glyph, 2, 3, 3, 'rgba(21,19,25,0.22)');
  for (let depth = 7; depth >= 1; depth--) { canvas.save(); canvas.translate(depth * 0.65, depth * 0.5); fill('#17151a'); canvas.drawPath(glyph, paint); canvas.restore(); }
  canvas.save(); canvas.translate(-1.5, -1.4); fill('#6d686c'); canvas.drawPath(glyph, paint); canvas.restore();
  gradient(glyph, ['#474347', '#19171b', '#0d0c10', '#211e23'], [0, 0.25, 0.73, 1], { x: 0, y: 83 }, { x: 35, y: 283 });
  const cloth = Skia.Path.MakeFromSVGString('M164 76C181 43 203 1 219 -3C231 -7 242 4 249 15C265 38 273 65 272 91C272 98 269 103 264 102C230 88 192 73 164 76Z')!;
  shadow(cloth, 0, 2, 2, 'rgba(0,0,0,0.18)');
  gradient(cloth, ['#7e0712', '#e42c32', '#ba111d', '#7f0711'], [0, 0.36, 0.71, 1], { x: 163, y: 29 }, { x: 274, y: 89 });
  canvas.save(); canvas.clipPath(cloth, 1, true);
  const fold = Skia.Path.MakeFromSVGString('M219 -2C234 29 241 57 247 84')!;
  fill('rgba(87,0,11,0.12)'); paint.setStyle(PaintStyle.Stroke); paint.setStrokeWidth(2); canvas.drawPath(fold, paint); canvas.restore();
  const cuff = Skia.Path.MakeFromSVGString('M151 66C186 64 245 81 274 96C277 108 273 129 266 139C227 122 173 116 138 108C138 94 144 76 151 66Z')!;
  shadow(cuff, 0, 5, 4, 'rgba(0,0,0,0.30)');
  gradient(cuff, ['#fffdf8', '#e8e7ed', '#9b9ba6'], [0, .5, 1], { x: 188, y: 72 }, { x: 208, y: 129 });
  canvas.save(); canvas.clipPath(cuff, 1, true);
  for (let i = 0; i < 180; i++) {
    fill(i % 3 === 0 ? 'rgba(143,140,153,0.05)' : 'rgba(255,255,252,0.16)');
    canvas.drawOval(rect(138 + hash(i + 11) * 138, 64 + hash(i + 97) * 79, .5 + hash(i + 201), 2 + hash(i + 33) * 3), paint);
  }
  canvas.restore();
  // Fine unequal fibers soften the lower cloth edge without a separate pom ball.
  for (let i = 0; i < 96; i++) {
    const u = i / 95; const x = 138 + 128 * u; const y = 108 + 31 * u - 8 * Math.sin(Math.PI * u); const length = 2 + 8 * hash(i + 317);
    fill(i % 4 === 0 ? 'rgba(185,182,194,0.68)' : 'rgba(234,232,239,0.68)');
    canvas.drawOval(rect(x, y - 2, .7 + hash(i + 99), length), paint);
  }
  const picture = recorder.finishRecordingAsPicture();
  const surface = Skia.Surface.MakeOffscreen(888, 900); if (!surface) return null;
  const raster = surface.getCanvas(); raster.clear(Skia.Color('transparent')); raster.scale(3, 3); raster.drawPicture(picture); surface.flush();
  // A CPU-backed image can cross from this JS renderer to the UI renderer safely.
  return surface.makeImageSnapshot().makeNonTextureImage();
}

/** Cached opaque cover and soft radial impact, shared by every deterministic frame. */
function makeInk(): SkImage | null {
  const recorder = Skia.PictureRecorder(); const canvas = recorder.beginRecording(rect(0, 0, 52, 54));
  const paint = Skia.Paint(); paint.setAntiAlias(true);
  const shape = Skia.Path.RRect(rrect(rect(3, 3, 44, 46), 11, 11));
  paint.setColor(Skia.Color('rgba(143,135,146,0.35)')); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, .6, true)); canvas.drawPath(shape, paint);
  paint.setMaskFilter(null); paint.setColor(Skia.Color('#938488')); canvas.drawPath(shape, paint);
  const tail = Skia.Path.MakeFromSVGString('M41 41Q42 47 50 50Q41 50 36 46Z')!; canvas.drawPath(tail, paint);
  for (let i = 0; i < 320; i++) {
    paint.setColor(Skia.Color(i % 2 === 0 ? 'rgba(230,222,232,0.10)' : 'rgba(101,91,104,0.10)'));
    canvas.drawOval(rect(6 + hash(i + 39) * 37, 6 + hash(i + 477) * 39, .65, .65), paint);
  }
  const picture = recorder.finishRecordingAsPicture(); const surface = Skia.Surface.MakeOffscreen(156, 162); if (!surface) return null;
  const raster = surface.getCanvas(); raster.clear(Skia.Color('transparent')); raster.scale(3, 3); raster.drawPicture(picture); surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}
function makeImpact(): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(264, 264); if (!surface) return null;
  const canvas = surface.getCanvas(); canvas.clear(Skia.Color('transparent')); canvas.scale(2, 2);
  const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(Skia.Color('white'));
  paint.setShader(Skia.Shader.MakeRadialGradient({ x: 66, y: 66 }, 62, ['rgba(40,33,44,0.34)', 'rgba(48,40,55,0.24)', 'rgba(48,40,55,0)'].map(color => Skia.Color(color)), [0, .35, 1], TileMode.Clamp));
  canvas.drawRect(rect(0, 0, 132, 132), paint); surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}

function makeConversation(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(rect(0, 0, WIDTH, HEIGHT));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const fill = (color: string) => paint.setColor(Skia.Color(color));
  const text = (copy: string, size: number, weight: '400' | '500' | '600', color: string, x: number, y: number, centered = true) => {
    const font = matchFont({ fontFamily: FONT, fontSize: size, fontWeight: weight });
    const ids = font.getGlyphIDs(copy);
    const width = font.getGlyphWidths(ids).reduce((sum, value) => sum + value, 0);
    const metrics = font.getMetrics();
    fill(color);
    canvas.drawText(copy, centered ? x - width / 2 : x, y - (metrics.ascent + metrics.descent) / 2, paint, font);
  };
  fill('#bbc3c9');
  canvas.drawCircle(196.5, 88, 17, paint);
  text('NV', 10, '600', '#636e76', 196.5, 88);
  text('Nori Vale', 12, '500', '#555c61', 196.5, 117);
  fill('#66737c');
  const chevron = Skia.Path.MakeFromSVGString('M29 91 23 97 29 103');
  if (chevron) {
    const pen = Skia.Paint();
    pen.setColor(Skia.Color('#66737c'));
    pen.setStyle(1);
    pen.setStrokeWidth(1.8);
    pen.setAntiAlias(true);
    canvas.drawPath(chevron, pen);
  }
  fill('#f9fafa');
  canvas.drawRRect(rrect(rect(26, 246, 194, 61), 18, 18), paint);
  text('A number for the', 14, '400', '#637078', 40, 267, false);
  text('good days.', 14, '400', '#637078', 40, 284, false);
  fill('#aabfcb');
  canvas.drawRRect(rrect(rect(239, 568, 128, 48), 20, 20), paint);
  text('Saved that one.', 13, '400', '#637887', 303, 592);
  fill('rgba(168,182,189,0.65)');
  canvas.drawRRect(rrect(rect(41, 329, 57, 5), 2.5, 2.5), paint);
  fill('rgba(183,193,196,0.65)');
  canvas.drawRRect(rrect(rect(40, 343, 105, 4), 2, 2), paint);
  return recorder.finishRecordingAsPicture();
}

function CenteredText({ children, x, y, width = 180, size, color, weight = '400', spacing = 0 }: {
  children: ReactNode; x: number; y: number; width?: number; size: number; color: string;
  weight?: '400' | '500' | '600'; spacing?: number;
}) {
  return <Text style={{ position: 'absolute', left: x - width / 2, top: y - size * 0.6, width, lineHeight: size * 1.2, textAlign: 'center', fontSize: size, fontWeight: weight, color, letterSpacing: spacing, includeFontPadding: false }}>{children}</Text>;
}

function FrostedPill({ x, y, width, height, white, border, shadow, strokeWidth = 1, blurTarget }: {
  x: number; y: number; width: number; height: number; white: number; border: number; shadow: string; strokeWidth?: number; blurTarget: RefObject<View | null>;
}) {
  return <View pointerEvents="none" style={{ position: 'absolute', left: x, top: y, width, height, borderRadius: height / 2, boxShadow: shadow }}>
    <View style={{ flex: 1, borderRadius: height / 2, overflow: 'hidden', borderWidth: strokeWidth, borderColor: `rgba(255,255,255,${border})` }}>
      <BlurView tint="systemUltraThinMaterialLight" intensity={100} blurTarget={Platform.OS === 'android' ? blurTarget : undefined} blurMethod={Platform.OS === 'android' ? 'dimezisBlurViewSdk31Plus' : undefined} style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, { backgroundColor: `rgba(255,255,255,${white})` }]} />
    </View>
  </View>;
}


function RailLabel({ index, pose }: { index: number; pose: SharedValue<Pose> }) {
  const style = useAnimatedStyle(() => ({ opacity: selected(pose.get()) !== index || pose.get().selection < 0.5 ? 0.78 : 0 }));
  return <Animated.Text accessible={false} importantForAccessibility="no" style={[styles.railLabel, { top: 243 + 52 * index }, style]}>{NAMES[index].toUpperCase()}</Animated.Text>;
}
function SelectedPhrase({ index, pose }: { index: number; pose: SharedValue<Pose> }) {
  const style = useAnimatedStyle(() => { const p = pose.get(); return { opacity: selected(p) === index ? p.selection * 0.65 : 0, transform: [{ translateY: 52 * p.focus }] }; });
  return <Animated.Text accessible={false} importantForAccessibility="no" style={[styles.selectedPhrase, style]}>{'SEND WITH ' + NAMES[index].toUpperCase()}</Animated.Text>;
}

/** Four independent message events with opaque ink, measured bursts and a soft impact. */
export function SendAmplifyPreview({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions(); const [size, setSize] = useState({ width: window.width, height: window.height });
  const [start] = useState(seconds); const [manualLive, setManualLive] = useState(false); const [resetAt, setResetAt] = useState(-1); const [accessibilityIndex, setAccessibilityIndex] = useState(3);
  const reduced = useReducedMotion(); const clock = useSharedValue(start); const epoch = useSharedValue(start); const manual = useSharedValue<Manual>(INITIAL_MANUAL);
  const lastHaptic = useSharedValue(-1); const hapticIndex = useSharedValue(3);
  const conversation = useMemo(() => makeConversation(), []); const numeral = useMemo(() => makeNumeral(), []); const ink = useMemo(() => makeInk(), []); const impact = useMemo(() => makeImpact(), []);
  const blurTarget = useRef<View | null>(null);
  const pose = useDerivedValue(() => currentPose(clock.get(), manual.get(), epoch.get(), reduced, autoPlay, previewTime));
  const frames = useFrameCallback(() => clock.set(seconds()), false);
  useEffect(() => { frames.setActive(!reduced && previewTime === undefined && (autoPlay || manualLive)); return () => frames.setActive(false); }, [autoPlay, reduced, previewTime, manualLive, frames]);
  useAnimatedReaction(() => manual.get().row, (next, previous) => { if (next !== previous) scheduleOnRN(setAccessibilityIndex, next); });
  useEffect(() => {
    if (resetAt < 0) return;
    const timer = setTimeout(() => scheduleOnUI(() => {
      'worklet';
      const state = manual.get();
      if (!state.dragging && state.lastTouch === resetAt) { manual.set({ ...state, live: false, activated: false }); scheduleOnRN(setManualLive, false); }
    }), 2650);
    return () => clearTimeout(timer);
  }, [resetAt, manual]);
  const startInteraction = () => { setManualLive(true); setResetAt(-1); };
  const finishInteraction = (time: number) => { setManualLive(true); setResetAt(time); };
  const tick = (index: number, now: number) => {
    'worklet'; if (index !== hapticIndex.get() && now - lastHaptic.get() > .12) { hapticIndex.set(index); lastHaptic.set(now); scheduleOnRN(haptic); }
  };
  const beginEvent = (row: number, now: number) => {
    'worklet';
    const state = manual.get(); const shown = currentPose(now, state, epoch.get(), reduced, autoPlay, previewTime);
    manual.set({ ...state, captured: shown, row, releaseFocus: row, hasSelection: true, eventStarted: now, activated: true, live: true }); tick(row, now);
  };
  const pan = Gesture.Pan().minDistance(0)
    .onBegin((event) => {
      const now = seconds(); const state = manual.get(); const shown = currentPose(now, state, epoch.get(), reduced, autoPlay, previewTime); const directRail = event.x + 12 > 330;
      const releaseFocus = Math.round(clamp(directRail ? (event.y + 184 - 251) / 52 : shown.focus, 0, 3));
      manual.set({ ...state, captured: shown, dragStartFocus: shown.focus, releaseFocus, directRail, activated: false, dragging: true, live: true, lastTouch: now }); clock.set(now); scheduleOnRN(startInteraction);
    })
    .onUpdate((event) => {
      const now = seconds(); const state = manual.get(); const target = state.directRail ? (event.y + 184 - 251) / 52 : state.dragStartFocus + event.translationY / 52;
      const row = Math.round(clamp(target, 0, 3));
      manual.set({ ...state, releaseFocus: row, lastTouch: now }); clock.set(now);
      if (Math.max(Math.abs(event.translationX), Math.abs(event.translationY)) > 6 && (!state.activated || state.row !== row)) beginEvent(row, now);
    })
    .onFinalize(() => {
      const state = manual.get(); if (!state.dragging) return;
      const now = seconds(); if (!state.activated) beginEvent(state.releaseFocus, now);
      manual.set({ ...manual.get(), dragging: false, lastTouch: now }); clock.set(now); scheduleOnRN(finishInteraction, now);
    });
  const selectEffect = (focus: number) => {
    'worklet'; const now = seconds(); beginEvent(Math.round(focus), now); manual.set({ ...manual.get(), lastTouch: now }); clock.set(now); scheduleOnRN(finishInteraction, now);
  };
  const reset = () => {
    'worklet'; const now = seconds(); manual.set(INITIAL_MANUAL); epoch.set(now); clock.set(now); scheduleOnRN(setManualLive, false); scheduleOnRN(setResetAt, -1); scheduleOnRN(haptic);
  };
  const numeralTransform = useDerivedValue(() => {
    const p = reduced ? REST : pose.get(); const angle = p.rotation * Math.PI / 180; const sy = p.scale * p.stretch;
    const width = 270 * p.scale * Math.cos(angle) + 200 * sy * Math.abs(Math.sin(angle));
    const x = p.right - width / 2 + 9 * p.scale * Math.cos(angle) + 33 * sy * Math.sin(angle);
    const y = p.bodyY + 9 * p.scale * Math.sin(angle) - 33 * sy * Math.cos(angle);
    return [{ translateX: x }, { translateY: y }, { rotate: angle }, { scaleX: p.scale }, { scaleY: sy }];
  });
  const numeralOpacity = useDerivedValue(() => 1 - (reduced ? REST : pose.get()).ink);
  const inkOpacity = useDerivedValue(() => (reduced ? REST : pose.get()).ink);
  const inkX = useDerivedValue(() => (reduced ? REST : pose.get()).right - 49);
  const inkY = useDerivedValue(() => (reduced ? REST : pose.get()).bodyY - 27);
  const impactOpacity = useDerivedValue(() => (reduced ? REST : pose.get()).impact);
  const impactTransform = useDerivedValue(() => { const p = reduced ? REST : pose.get(); return [{ translateX: 302 - 21 * p.impactProgress }, { translateY: 259 + 5 * p.impactProgress }, { scaleX: (63 + 47 * p.impactProgress) / 132 }, { scaleY: (76 + 74 * p.impactProgress) / 132 }]; });
  const arrowStyle = useAnimatedStyle(() => ({ opacity: pose.get().selection, transform: [{ translateY: 52 * pose.get().railFocus }] }));
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT);
  // Keep gestures and the animated numeral outside the shared Android blur target.
  const background = <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Rect dither x={0} y={0} width={WIDTH} height={HEIGHT}><LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: HEIGHT }} colors={['#c3bfc5', '#e9ecf4', '#ded9d7', '#d0c1ba', '#d6d6e2']} positions={[0, 0.35, 0.54, 0.76, 1]} /></Rect>
        <Oval dither x={0} y={79} width={300} height={170} color="rgba(139,133,128,0.23)"><BlurMask blur={40} style="normal" /></Oval>
        <Oval dither x={242} y={120} width={160} height={180} color="rgba(203,220,242,0.33)"><BlurMask blur={48} style="normal" /></Oval>
        <Oval dither x={-4} y={257.5} width={310} height={185} color="rgba(247,246,250,0.45)"><BlurMask blur={42} style="normal" /></Oval>
        <Oval dither x={70} y={565} width={310} height={150} color="rgba(191,174,155,0.23)"><BlurMask blur={40} style="normal" /></Oval>
        <Group layer={<Paint opacity={0.13}><Blur blur={20} /></Paint>}><Picture picture={conversation} /></Group>
      </Canvas>;
  return <View style={styles.screen} onLayout={(event) => { const { width, height } = event.nativeEvent.layout; setSize((before) => before.width === width && before.height === height ? before : { width, height }); }}>
    <StatusBar style="dark" />
    <View style={[styles.design, { left: (size.width - WIDTH * scale) / 2, top: (size.height - HEIGHT * scale) / 2, transform: [{ scale }] }]}>
      {Platform.OS === 'android' ? <BlurTargetView ref={blurTarget} pointerEvents="none" style={StyleSheet.absoluteFill}>{background}</BlurTargetView> : background}
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
        <CenteredText x={196.5} y={93} size={17} color="#777478" width={260}>Send with feeling</CenteredText>
        <View style={styles.segment} /><View style={styles.segmentSelected} />
        <CenteredText x={142} y={132.5} size={11} color="#343339" weight="600" width={106}>Bubble</CenteredText><CenteredText x={252} y={132.5} size={11} color="#343339" weight="500" width={106}>Scene</CenteredText>
        <FrostedPill blurTarget={blurTarget} x={24} y={714} width={345} height={50} white={0.21} border={0.52} shadow="0 5px 10px rgba(90,101,112,0.06)" />
        <View style={styles.plusCircle} /><Svg style={{ position: 'absolute', left: 39.5, top: 731.5 }} width={15} height={15} viewBox="0 0 16 16"><Path d="M8 2v12M2 8h12" stroke="#78848c" strokeWidth={0.9} /></Svg>
        <CenteredText x={84} y={739} size={16} color="#4f5a62" weight="500" width={40}>60</CenteredText><CenteredText x={142} y={739} size={12} color="#9aa4ab" width={80}>Message</CenteredText>
        <View style={styles.sendCircle} /><Svg style={{ position: 'absolute', left: 333.75, top: 728.75 }} width={18.5} height={20.5} viewBox="0 0 16 16" preserveAspectRatio="none"><Path d="M8 13V3M3.5 7.5 8 3l4.5 4.5" stroke="white" strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" /></Svg>
        <CenteredText x={196.5} y={789} size={10} color="#8a9095" width={280}>Preview only · no message is sent</CenteredText>
        <FrostedPill blurTarget={blurTarget} x={338} y={230} width={42} height={198} white={0.69} border={0.72} strokeWidth={0.7} shadow="0 2px 4px rgba(0,0,0,0.035)" />
        {NAMES.map((_, index) => <RailLabel key={index} index={index} pose={pose} />)}
        <Canvas style={StyleSheet.absoluteFill}>{NAMES.map((_, index) => <Circle key={index} cx={359} cy={251 + 52 * index} r={4.2} color="#aba8b4" />)}</Canvas>
        <Animated.View style={[styles.selectedArrow, arrowStyle]}><Svg width={16} height={16} viewBox="0 0 16 16"><Path d="M8 13V3M3.5 7.5 8 3l4.5 4.5" stroke="white" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" /></Svg></Animated.View>
        <View style={styles.closeDisc}><Svg width={10} height={10} viewBox="0 0 12 12"><Path d="m2 2 8 8M10 2l-8 8" stroke="white" strokeWidth={1.6} strokeLinecap="round" /></Svg></View>
      </View>
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Group transform={impactTransform} opacity={impactOpacity}><SkiaImage image={impact} x={-66} y={-66} width={132} height={132} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Group>
      </Canvas>
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
        {NAMES.map((_, index) => <SelectedPhrase key={index} index={index} pose={pose} />)}
      </View>
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Group transform={numeralTransform} opacity={numeralOpacity}><SkiaImage image={numeral} x={-148} y={-150} width={296} height={300} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Group>
        <SkiaImage image={ink} x={inkX} y={inkY} width={52} height={54} opacity={inkOpacity} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      </Canvas>
      <GestureDetector gesture={pan}><View style={styles.previewTouch} accessible accessibilityRole="adjustable" accessibilityLabel="Message effect" accessibilityValue={{ text: NAMES[accessibilityIndex] }} accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={(event) => scheduleOnUI(selectEffect, clamp(manual.get().row + (event.nativeEvent.actionName === 'increment' ? 1 : -1), 0, 3))} /></GestureDetector>
      <Pressable style={styles.sendTouch} accessibilityRole="button" accessibilityLabel="Preview amplify effect" onPress={() => scheduleOnUI(selectEffect, 0)} /><Pressable style={styles.resetTouch} accessibilityRole="button" accessibilityLabel="Reset effect preview" onPress={() => scheduleOnUI(reset)} />
    </View>
  </View>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#d6d6e2', overflow: 'hidden' }, design: { position: 'absolute', width: WIDTH, height: HEIGHT, transformOrigin: 'top left', overflow: 'hidden' },
  segment: { position: 'absolute', left: 87, top: 118, width: 220, height: 29, borderRadius: 7, backgroundColor: 'rgba(158,160,169,0.28)' }, segmentSelected: { position: 'absolute', left: 89, top: 120, width: 106, height: 25, borderRadius: 6, backgroundColor: 'rgba(255,255,255,0.96)', boxShadow: '0 1px 1.5px rgba(0,0,0,0.06)' },
  plusCircle: { position: 'absolute', left: 35, top: 727, width: 24, height: 24, borderRadius: 12, backgroundColor: 'rgba(124,138,148,0.09)' }, sendCircle: { position: 'absolute', left: 327, top: 723, width: 32, height: 32, borderRadius: 16, backgroundColor: '#287ae0', boxShadow: '0 2px 5px rgba(57,131,216,0.18)' },
  railLabel: { position: 'absolute', left: 212, width: 110, height: 16, fontSize: 8.2, lineHeight: 16, fontWeight: '600', color: '#89838f', textAlign: 'right', includeFontPadding: false }, selectedPhrase: { position: 'absolute', left: 183, top: 217, width: 136, height: 12, fontSize: 8.2, lineHeight: 12, fontWeight: '600', letterSpacing: 0.25, color: '#68646e', textAlign: 'right', includeFontPadding: false },
  selectedArrow: { position: 'absolute', left: 347, top: 239, width: 24, height: 24, borderRadius: 12, backgroundColor: '#087cfa', alignItems: 'center', justifyContent: 'center' }, closeDisc: { position: 'absolute', left: 348, top: 452, width: 22, height: 22, borderRadius: 11, backgroundColor: '#64626a', alignItems: 'center', justifyContent: 'center' },
  previewTouch: { position: 'absolute', left: 12, top: 184, width: 369, height: 330 }, sendTouch: { position: 'absolute', left: 321, top: 717, width: 44, height: 44 }, resetTouch: { position: 'absolute', left: 337, top: 441, width: 44, height: 44 },
});
