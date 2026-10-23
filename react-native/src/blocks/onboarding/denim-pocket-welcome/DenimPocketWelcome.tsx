import {
  Canvas, ClipOp, FilterMode, MipmapMode, PaintStyle, Picture, Skia, TileMode, useImage,
  type SkCanvas, type SkColor, type SkImage, type SkPaint, type SkPath, type SkPicture,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8;
type Props = { autoPlay?: boolean; previewTime?: number };
type Motion = {
  now: number; epoch: number; dragging: boolean; captured: number; capturedTab: number; finger: number; tab: number;
  released: number; releaseFrom: number; releaseTarget: number; releaseVelocity: number; ready: boolean;
};
type Pose = { p: number; tab: number };
type Art = { tooth: SkPath[]; bevel: SkPath[]; recess: SkPath[]; body: SkPicture; pull: SkPicture };
function clamp(x: number, low = 0, high = 1) { 'worklet'; return Math.min(high, Math.max(low, x)); }
function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
function progress(time: number) {
  'worklet'; const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 1.3) return 0;
  if (t < 1.63) return 0.10 * smooth((t - 1.3) / 0.33);
  if (t < 1.88) return 0.10 * (1 - smooth((t - 1.63) / 0.25));
  if (t < 3.75) return 0.86 * smooth((t - 1.88) / 1.87);
  if (t < 4.22) return 0.86 + 0.14 * (1 - Math.pow(1 - (t - 3.75) / 0.47, 3));
  if (t < 5.18) return 1;
  return 1 - smooth((t - 5.18) / 1.92);
}
function drive(m: Motion, now: number, autoPlay: boolean, reduced: boolean, previewTime?: number): Pose {
  'worklet';
  if (reduced) return { p: 1, tab: 0 };
  let t = ((previewTime ?? Math.max(0, now - m.epoch)) % LOOP + LOOP) % LOOP;
  if (previewTime === undefined) {
    if (m.dragging) return { p: m.finger, tab: m.tab };
    if (m.released >= 0) {
      const age = Math.max(0, now - m.released);
      if (age < 2) return {
        p: clamp(m.releaseTarget + (m.releaseFrom - m.releaseTarget) * Math.exp(-12 * age) * Math.cos(12 * age)
          + m.releaseVelocity * age * Math.exp(-12 * age)),
        tab: m.tab * Math.exp(-9 * age) * Math.cos(13 * age),
      };
      if (age < 2.6) return { p: m.releaseTarget * (1 - smooth((age - 2) / 0.6)), tab: 0 };
      t = Math.max(0, age - 2.6) % LOOP;
    }
    if (!autoPlay) return { p: 0, tab: 0 };
  }
  const p = progress(t);
  const tab = -14 * smooth((t - 1.15) / 0.15) * (1 - smooth((t - 1.88) / 1.97))
    + 9 * Math.sin((t - 1.88) * 9) * smooth((t - 1.88) / 0.2) * (1 - smooth((t - 2.4) / 1.45));
  return { p, tab: p > 0.98 ? 0 : tab };
}
function join(p: number) { 'worklet'; return 116 + 650 * Math.min(p / 0.86, 1) + 128 * smooth((p - 0.86) / 0.14); }
function edge(y: number, p: number, side: number) {
  'worklet'; const j = join(p), gap = 10 + 175 * smooth(p / 0.86);
  return 196.5 + side * (2 + (y < j ? gap * Math.pow(Math.max(0, (j - y) / j), 0.78) : 0));
}
function color(hex: number, alpha = 1): SkColor {
  'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha);
}
function paint(hex: number, alpha = 1): SkPaint {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(color(hex, alpha)); return p;
}
function rounded(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet'; return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}
function gradient(hex: number[], from: number[], to: number[]) {
  'worklet'; const p = paint(0xffffff);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: from[0], y: from[1] }, { x: to[0], y: to[1] }, hex.map(h => color(h)), null, TileMode.Clamp, undefined, 1)); return p;
}
function stroke(c: SkCanvas, path: SkPath, hex: number, width: number, alpha = 1) {
  'worklet'; const p = paint(hex, alpha); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(width); c.drawPath(path, p);
}
function shadow(c: SkCanvas, path: SkPath, hex: number, alpha: number, radius: number, dx: number, dy: number) {
  'worklet'; const p = paint(hex); p.setImageFilter(Skia.ImageFilter.MakeDropShadowOnly(dx, dy, radius / 2, radius / 2, color(0, alpha))); c.drawPath(path, p);
}
function makeArt(): Art {
  const tooth = [-1, 1].map(side => rounded(side < 0 ? -6 : -1.5, -1.7, 7.5, 3.4, 0.7));
  const bevel = [-1, 1].map(side => Skia.PathBuilder.Make().addRect(Skia.XYWHRect(side < 0 ? -6 : -1.5, -1.7, 7.5, 0.75)).detach());
  const recess = [-1, 1].map(side => Skia.PathBuilder.Make().addRect(Skia.XYWHRect(side < 0 ? -6 : -1.5, 1.1, 7.5, 0.6)).detach());
  let recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(-40, -45, 80, 100));
  const bodyPath = rounded(-15, -20, 30, 43, 6);
  shadow(c, bodyPath, 0x808b8c, 0.32, 2.5, 0, 1.5); shadow(c, bodyPath, 0x808b8c, 0.18, 7, 0, 5);
  c.drawPath(bodyPath, gradient([0xAAB3B3, 0x717C7F, 0x455255], [-15, -15], [15, 20])); stroke(c, bodyPath, 0x394A4E, 1);
  c.drawPath(rounded(-10, -17, 20, 27, 4), paint(0x596568));
  c.drawPath(rounded(-5.5, -19, 11, 31, 4), gradient([0xAFB7B7, 0x6B7679, 0x859092], [-5, 0], [5, 0]));
  const body = recorder.finishRecordingAsPicture();
  recorder = Skia.PictureRecorder(); c = recorder.beginRecording(Skia.XYWHRect(-40, -25, 80, 110));
  const pull = Skia.PathBuilder.Make().addPath(rounded(-12, -4, 24, 64, 9)).addPath(rounded(-4, 43, 8, 10, 3.5)).setFillType(1).detach();
  shadow(c, pull, 0x879395, 0.32, 2.5, 0, 1.5); shadow(c, pull, 0x879395, 0.18, 7, 0, 5);
  c.drawPath(pull, gradient([0xAAB2B2, 0x727D7F, 0x536267], [-12, 0], [12, 55])); stroke(c, pull, 0x3B4A4D, 0.8);
  c.drawPath(rounded(-7, 3, 14, 28, 4), paint(0x5B686C)); stroke(c, rounded(-10.5, -2, 21, 59, 8), 0xffffff, 0.7, 0.14);
  return { tooth, bevel, recess, body, pull: recorder.finishRecordingAsPicture() };
}
function drawCover(c: SkCanvas, p: number, angle: number, denim: SkImage | null, art: Art) {
  'worklet'; if (p >= 1) return;
  const depart = smooth((p - 0.86) / 0.14), src = denim ? Skia.XYWHRect(0, 0, denim.width(), denim.height()) : null;
  for (let sideIndex = 0; sideIndex < 2; sideIndex++) {
    const side = sideIndex === 0 ? -1 : 1;
    c.save(); c.translate(side * 238 * depart, 64 * depart); c.rotate(side * 2.5 * depart, 0, 0);
    const outer = side < 0 ? -240 : 633;
    const b = Skia.PathBuilder.Make().moveTo(outer, -70).lineTo(edge(-70, p, side), -70);
    for (let row = 0; row <= 60; row++) { const y = row * 16; b.lineTo(edge(y, p, side), y); }
    const outline = b.lineTo(outer, 960).close().detach();
    shadow(c, outline, 0x123342, 0.42, 6, -side * 4, 2); c.drawPath(outline, paint(0x123342));
    c.save(); c.clipPath(outline, ClipOp.Intersect, true);
    const imagePaint = paint(0xffffff);
    for (let row = 0; denim && src && row < 28; row++) {
      const y = row * 36 - 72, shift = edge(y + 18, p, side) - (196.5 + side * 2);
      c.save(); c.clipRect(Skia.XYWHRect(-300, y, 1000, 36.2), ClipOp.Intersect, false);
      c.drawImageRectOptions(denim, src, Skia.XYWHRect(-100 + shift * 0.4, -70, 593, 1024), FilterMode.Linear, MipmapMode.None, imagePaint); c.restore();
    }
    c.restore();
    const hemBuilder = Skia.PathBuilder.Make(), foldBuilder = Skia.PathBuilder.Make();
    for (let i = 0; i <= 125; i++) {
      const y = i * 7.2 - 36, x = edge(y, p, side) + side * 5;
      if (i === 0) { hemBuilder.moveTo(x, y); foldBuilder.moveTo(x + side * 10, y); }
      else { hemBuilder.lineTo(x, y); foldBuilder.lineTo(x + side * 10, y); }
    }
    const hem = hemBuilder.detach(), fold = foldBuilder.detach();
    const shade = paint(0x081B29, 0.45); shade.setStyle(PaintStyle.Stroke); shade.setStrokeWidth(5);
    shade.setImageFilter(Skia.ImageFilter.MakeDropShadow(side * 2, 1, 1.5, 1.5, color(0, 0.26)));
    c.drawPath(fold, shade); stroke(c, fold, 0x7396A3, 1.1, 0.22); stroke(c, hem, 0x132127, 21); stroke(c, hem, 0x3A4A4F, 1, 0.8);
    const stitches = Skia.PathBuilder.Make(), teeth = Skia.PathBuilder.Make(), bevel = Skia.PathBuilder.Make(), recess = Skia.PathBuilder.Make();
    for (let i = 0; i < 126; i++) {
      const y = i * 7.2 + (side > 0 ? 3.6 : 0) - 28, x = edge(y, p, side);
      const slope = (edge(y + 1, p, side) - edge(y - 1, p, side)) / 2;
      const a = -Math.atan(slope), co = Math.cos(a), si = Math.sin(a), matrix = Skia.Matrix([co, -si, x, si, co, y, 0, 0, 1]);
      teeth.addPath(art.tooth[sideIndex], matrix); bevel.addPath(art.bevel[sideIndex], matrix); recess.addPath(art.recess[sideIndex], matrix);
      const sx = x + side * 15; stitches.moveTo(sx, y - 1.5).lineTo(sx + slope * 3, y + 1.5);
    }
    const stitchPaint = paint(0x738386, 0.58); stitchPaint.setStyle(PaintStyle.Stroke); stitchPaint.setStrokeWidth(0.6); stitchPaint.setStrokeCap(1);
    c.drawPath(stitches.detach(), stitchPaint); c.drawPath(teeth.detach(), paint(0x8B9698)); c.drawPath(bevel.detach(), paint(0xD5DAD6, 0.75)); c.drawPath(recess.detach(), paint(0x34434A)); c.restore();
  }
  c.save(); c.translate(196.5, join(p) + 64 * depart); c.drawPicture(art.body); c.translate(0, 11); c.rotate(angle, 0, 0); c.drawPicture(art.pull); c.restore();
}
function tactile() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Frozen Swift port: cloth UV strips, native copy and velocity-aware settlement. */
export function DenimPocketWelcome({ autoPlay = false, previewTime }: Props) {
  const initialReduced = useReducedMotion(), safe = useSafeAreaInsets();
  const [reduced, setReduced] = useState(initialReduced), [manualClock, setManualClock] = useState(false);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const denim = useImage(require('./assets/denim.jpg')), art = useMemo(() => makeArt(), []), s = size.width / 393;
  const bottom = s > 0 ? Math.max(0, safe.bottom / s - 34) : 0;
  const m = useSharedValue<Motion>({ now: 0, epoch: 0, dragging: false, captured: 0, capturedTab: 0, finger: 0, tab: 0, released: -1, releaseFrom: 0, releaseTarget: 0, releaseVelocity: 0, ready: false });
  useEffect(() => { const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', value => { setReduced(value); setManualClock(false); }); return () => subscription.remove(); }, []);
  useEffect(() => {
    const now = Date.now() / 1000; m.set(old => ({ ...old, now, epoch: now, dragging: false, released: -1, finger: 0, tab: 0, ready: false }));
  }, [denim, m, reduced]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = m.get();
    if (old.released >= 0 && !old.dragging && now - old.released >= 2.6) {
      m.set({ ...old, now, epoch: old.released + 2.6, released: -1, finger: 0, tab: 0, ready: false }); scheduleOnRN(setManualClock, false);
    } else m.set({ ...old, now });
  }, false);
  useEffect(() => {
    ticker.setActive(!!denim && s > 0 && !reduced && previewTime === undefined && (autoPlay || manualClock)); return () => ticker.setActive(false);
  }, [autoPlay, denim, manualClock, previewTime, reduced, s, ticker]);
  const pose = useDerivedValue(() => drive(m.get(), m.get().now, autoPlay, reduced, previewTime));
  const reveal = useDerivedValue(() => reduced ? 1 : smooth((pose.get().p - 0.72) / 0.28));
  const ready = useDerivedValue(() => { const state = m.get(); return state.ready && (reduced || (state.released >= 0 && state.now - state.released < 2.6)); });
  const setupStyle = useAnimatedStyle(() => ({ opacity: reveal.get() }));
  const logoStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 12 * (1 - reveal.get()) * s }] }));
  const featuresStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 8 * (1 - reveal.get()) * s }] }));
  const hitStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (join(pose.get().p) - 36) * s }] }));
  const hitProps = useAnimatedProps(() => ({ pointerEvents: (!reduced && (pose.get().p < 0.985 || m.get().dragging) ? 'auto' : 'none') as 'auto' | 'none', accessibilityElementsHidden: reduced || (pose.get().p >= 0.985 && !m.get().dragging), accessibilityValue: { text: `${Math.floor(pose.get().p * 100)} percent open` }, importantForAccessibility: (reduced || (pose.get().p >= 0.985 && !m.get().dragging) ? 'no-hide-descendants' : 'yes') as 'yes' | 'no-hide-descendants' }));
  const setupProps = useAnimatedProps(() => ({ accessibilityElementsHidden: !reduced && pose.get().p <= 0.985, importantForAccessibility: (!reduced && pose.get().p <= 0.985 ? 'no-hide-descendants' : 'yes') as 'yes' | 'no-hide-descendants' }));
  const ctaProps = useAnimatedProps(() => ({ pointerEvents: (reduced || pose.get().p > 0.985 ? 'auto' : 'none') as 'auto' | 'none', accessibilityLabel: ready.get() ? 'Journal ready' : 'Start a journal' }));
  const normalStyle = useAnimatedStyle(() => ({ opacity: ready.get() ? 0 : 1 })), readyStyle = useAnimatedStyle(() => ({ opacity: ready.get() ? 1 : 0 }));
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (s > 0) { c.scale(s, s); const p = pose.get(); drawCover(c, p.p, p.tab, denim, art); } return recorder.finishRecordingAsPicture();
  });
  const release = useCallback((from: number, target: number, velocity: number, markReady = false) => {
    'worklet'; const now = Date.now() / 1000;
    m.set(old => ({ ...old, now, dragging: false, releaseFrom: from, releaseTarget: target, releaseVelocity: velocity, released: now, ready: markReady || old.ready })); scheduleOnRN(setManualClock, true); scheduleOnRN(tactile);
  }, [m]);
  const begin = useCallback(() => {
    'worklet'; if (reduced || previewTime !== undefined) return;
    const now = Date.now() / 1000, old = m.get(), current = drive(old, now, autoPlay, reduced);
    m.set({ ...old, now, dragging: true, captured: current.p, capturedTab: current.tab, finger: current.p, tab: current.tab, released: -1, ready: false });
    scheduleOnRN(setManualClock, true);
  }, [autoPlay, m, previewTime, reduced]);
  const update = useCallback((translationX: number, translationY: number) => {
    'worklet'; if (reduced || previewTime !== undefined || s <= 0) return;
    m.set(old => ({ ...old, now: Date.now() / 1000, finger: clamp(old.captured + translationY / (650 * s)), tab: clamp(old.capturedTab + translationX / (3 * s), -18, 18) }));
  }, [m, previewTime, reduced, s]);
  const end = useCallback((velocityY: number) => {
    'worklet'; if (reduced || previewTime !== undefined || s <= 0) return;
    const old = m.get(), vy = velocityY / s;
    release(old.finger, old.finger >= 0.62 || vy >= 720 ? 1 : 0, clamp(vy, -1000, 1000) / 650);
  }, [m, previewTime, reduced, release, s]);
  const pan = Gesture.Pan().minDistance(0).onBegin(begin)
    .onUpdate(e => update(e.translationX, e.translationY)).onEnd(e => end(e.velocityY))
    .onFinalize((_e, success) => { if (!success && m.get().dragging) { const old = m.get(); release(old.finger, old.finger >= 0.62 ? 1 : 0, 0); } });
  const onLayout = useCallback((e: LayoutChangeEvent) => setSize(e.nativeEvent.layout), []);
  const box = (x: number, y: number, width: number, height: number) => ({ position: 'absolute' as const, left: x * s, top: y * s, width: width * s, height: height * s });
  const text = (fontSize: number, fontWeight: '400' | '500' | '600' = '400', opacity = 1) => ({ color: '#F0F1DD', fontSize: fontSize * s, fontWeight, opacity, includeFontPadding: false });
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="light" />
    {s > 0 && <>
      <LinearGradient colors={['#087CFA', '#279BF0', '#72D3F3']} style={box(0, 0, 393, Math.max(852, size.height / s))} />
      <Animated.View animatedProps={setupProps} style={[StyleSheet.absoluteFill, setupStyle]} pointerEvents="box-none">
        <Animated.View style={[StyleSheet.absoluteFill, logoStyle]} pointerEvents="none">
          <View style={[box(148.5, 119, 96, 108), styles.notebook, { borderRadius: 18 * s }]}>
            <View style={[box(9, 22, 78, 73), { backgroundColor: '#158CFA', borderRadius: 8 * s }]} />
            {[20, 66].map(x => <View key={x} style={[box(x, 6, 10, 13), { backgroundColor: '#65716E', borderRadius: 2 * s }]}><View style={[box(1, 1, 2, 8), { backgroundColor: 'rgba(255,255,255,0.3)' }]} /></View>)}
            <View style={[box(33, 101, 30, 2), { backgroundColor: '#C7BF9E', borderRadius: s }]} />
          </View>
          <View style={[box(28, 255, 337, 65), styles.center]}><Text allowFontScaling={false} style={[text(52), { letterSpacing: -1.6 * s }]}>Dayloom</Text></View>
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, featuresStyle]} pointerEvents="none">
          {['Keep a small moment', 'Find the thread', 'Make room for more'].map((title, i) => {
            const y = 437 + i * 75 - bottom;
            return <View key={title}>
              <View style={[box(29, y + 5, 24, 24), styles.center, { backgroundColor: 'rgba(9,93,132,0.65)', borderRadius: 12 * s }]}><Text allowFontScaling={false} style={text(12, '600')}>{i + 1}</Text></View>
              <View style={box(73, y, 284, 60)}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 * s }}><FeatureIcon index={i} scale={s} /><Text allowFontScaling={false} style={text(14.5, '600')}>{title}</Text></View>
                <Text allowFontScaling={false} style={[text(12.5, '400', 0.64), { marginTop: 4 * s, lineHeight: 17.5 * s }]}>{['One photo, one line, one ordinary day.', 'See the days that belong together.', 'Your memories stay close, at your pace.'][i]}</Text>
              </View>
            </View>;
          })}
        </Animated.View>
        <AnimatedPressable animatedProps={ctaProps} accessibilityRole="button" accessibilityLabel="Start a journal" onPress={() => {
          if (!reduced && pose.get().p <= 0.985) return;
          if (reduced) { m.set(old => ({ ...old, ready: true })); tactile(); } else release(1, 1, 0, true);
        }} style={[box(20, 747 - bottom, 353, 60), styles.center, { backgroundColor: '#087FFA', borderRadius: 17 * s, borderWidth: 0.8 * s, borderColor: 'rgba(255,255,255,0.12)' }]}>
          <Animated.Text allowFontScaling={false} style={[text(16, '600'), normalStyle]}>Start a journal</Animated.Text>
          <Animated.View style={[StyleSheet.absoluteFill, styles.center, { flexDirection: 'row', gap: 8 * s }, readyStyle]} pointerEvents="none"><Text allowFontScaling={false} style={text(14, '600')}>✓</Text><Text allowFontScaling={false} style={text(16, '600')}>Journal ready</Text></Animated.View>
        </AnimatedPressable>
      </Animated.View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Picture picture={picture} /></Canvas>
      <GestureDetector gesture={pan}><Animated.View animatedProps={hitProps} style={[box(150.5, 0, 92, 124), hitStyle]} accessible accessibilityRole="button" accessibilityLabel="Open journal cover" accessibilityHint="Drag the zipper downward to reveal your journal." onAccessibilityTap={() => { if (!reduced && previewTime === undefined) release(pose.get().p, 1, 0); }} /></GestureDetector>
    </>}
  </View>;
}
function FeatureIcon({ index, scale }: { index: number; scale: number }) {
  return <Svg width={14 * scale} height={14 * scale} viewBox="0 0 16 16" fill="none" stroke="#F0F1DD" strokeWidth={1.05} accessibilityElementsHidden>
    {index === 0 ? <><Path d="M2.4 4.7h2.2l1-1.6h4.8l1 1.6h2.2v7.1H2.4Z" /><Path d="M10.5 8.3a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0Z" /></>
      : index === 1 ? <>{[2.3, 9.3].flatMap(x => [2.3, 9.3].map(y => <Rect key={`${x}-${y}`} x={x} y={y} width={4.4} height={4.4} rx={0.6} />))}</>
        : <Path d="M5.7 7.2 8.3 2c.5-.5 1.6-.1 1.5.9l-.4 3.4h3.3c.9 0 1.2.5 1 1.3l-1 4.6c-.1.6-.6.9-1.3.9H5.7Zm-3.3.2h3v5.7h-3Z" />}
  </Svg>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#123342', overflow: 'hidden' }, center: { justifyContent: 'center', alignItems: 'center' },
  notebook: { backgroundColor: '#E8E5C9', shadowColor: '#315F92', shadowOpacity: 0.2, shadowRadius: 8, shadowOffset: { width: 0, height: 7 } },
});
