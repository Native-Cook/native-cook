import {
  BlendMode, Canvas, ClipOp, PaintStyle, Picture, Skia, TileMode, matchFont, useImage,
  type SkCanvas, type SkFont, type SkImage, type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type FrameCallback } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { begin, clamp, contains, dot, finish, initialMotion, local, matrix, point, projection, rock, sample, select, type Frame } from './motion';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number };
type Painter = { canvas: SkCanvas; paint: SkPaint };
type BlurAsset = { image: SkImage; padding: number } | null;
type BlurAssets = { ambient: BlurAsset; contact: BlurAsset; rim: BlurAsset };
const DAY = require('./assets/rocker-day.png');
const NIGHT = require('./assets/rocker-night.png');

function mixed(day: number, night: number, value: number, alpha = 1) {
  'worklet';
  const n = clamp(value);
  const r = ((day >> 16) & 255) * (1 - n) + ((night >> 16) & 255) * n;
  const g = ((day >> 8) & 255) * (1 - n) + ((night >> 8) & 255) * n;
  const b = (day & 255) * (1 - n) + (night & 255) * n;
  return Float32Array.of(r / 255, g / 255, b / 255, clamp(alpha));
}
function reset(p: Painter, color = 0xffffff, alpha = 1, stroke = 0) {
  'worklet';
  p.paint.setShader(null); p.paint.setMaskFilter(null); p.paint.setImageFilter(null); p.paint.setColorFilter(null);
  p.paint.setBlendMode(BlendMode.SrcOver); p.paint.setAlphaf(1);
  p.paint.setColor(mixed(color, color, 0, alpha)); p.paint.setAntiAlias(true);
  p.paint.setStyle(stroke > 0 ? PaintStyle.Stroke : PaintStyle.Fill); p.paint.setStrokeWidth(stroke);
}
function projected(p: Painter, f: Frame, depth: number, angle: number) {
  'worklet';
  p.canvas.save(); p.canvas.concat(matrix(projection(f, angle, depth)));
}
function cached(p: Painter, cache: BlurAsset, alpha: number) {
  'worklet';
  if (!cache) return;
  const padding = cache.padding;
  reset(p); p.paint.setAlphaf(clamp(alpha));
  p.canvas.drawImageRect(cache.image, Skia.XYWHRect(0, 0, cache.image.width(), cache.image.height()),
    Skia.XYWHRect(-padding, -padding, 100 + padding * 2, 260 + padding * 2), p.paint);
}
function makeBlurAsset(radius: number, padding: number, color: number, stroke = 0): BlurAsset {
  const width = 100 + padding * 2, height = 260 + padding * 2;
  const surface = Skia.Surface.MakeOffscreen(width * 2, height * 2);
  if (!surface) return null;
  const canvas = surface.getCanvas(), p = { canvas, paint: Skia.Paint() };
  canvas.clear(Skia.Color('transparent')); canvas.scale(2, 2); canvas.translate(padding, padding);
  reset(p, color, 1, stroke);
  p.paint.setImageFilter(Skia.ImageFilter.MakeBlur(radius, radius, TileMode.Decal));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, 100, 260), 50, 50), p.paint);
  surface.flush();
  // CPU-backed snapshots can safely cross from this one-time JS cache to the UI worklet.
  const image = surface.makeImageSnapshot().makeNonTextureImage();
  return image ? { image, padding } : null;
}
function label(p: Painter, font: SkFont, value: string, centerX: number, centerY: number, color: ReturnType<typeof Skia.Color>) {
  'worklet';
  reset(p); p.paint.setColor(color);
  const glyphs = font.getGlyphIDs(value), widths = font.getGlyphWidths(glyphs);
  let width = 0;
  for (let i = 0; i < widths.length; i++) width += widths[i];
  const metrics = font.getMetrics();
  p.canvas.drawText(value, centerX - width / 2, centerY - (metrics.ascent + metrics.descent) / 2, p.paint, font);
}
function groundLight(p: Painter, f: Frame) {
  'worklet';
  const n = clamp(f.n), lifted = 130 * (1 - 2 * n);
  const center = point(projection(f, rock(f), 8), { x: 0, y: lifted });
  const radius = (84 + 11 * f.c) * Math.min(1.7, f.scale / 1.223);
  reset(p);
  p.paint.setShader(Skia.Shader.MakeRadialGradient(center, radius,
    [mixed(0x8c2de5, 0x8c2de5, n, 0.12 + 0.83 * n), mixed(0x8c2de5, 0x8c2de5, n, 0)], [0, 1], TileMode.Clamp, undefined, 1));
  p.paint.setImageFilter(Skia.ImageFilter.MakeBlur(12 + 8 * n, 12 + 8 * n, TileMode.Decal));
  p.canvas.drawOval(Skia.XYWHRect(center.x - radius * 1.2, center.y - radius * 0.9, radius * 2.4, radius * 1.8), p.paint);
  reset(p);
  p.paint.setShader(Skia.Shader.MakeRadialGradient(center, 35,
    [mixed(0xc15cf1, 0xf7caff, n, 0.08 + 0.65 * n), mixed(0xc15cf1, 0xf7caff, n, 0)], [0, 1], TileMode.Clamp, undefined, 1));
  p.paint.setImageFilter(Skia.ImageFilter.MakeBlur(8, 8, TileMode.Decal));
  p.canvas.drawOval(Skia.XYWHRect(center.x - 45, center.y - 35, 90, 70), p.paint);
}
function drawArt(p: Painter, f: Frame, day: SkImage, night: SkImage, font: SkFont, blur: BlurAssets) {
  'worklet';
  const { canvas } = p, n = clamp(f.n), angle = rock(f);
  const capsule = Skia.RRectXY(Skia.XYWHRect(0, 0, 100, 260), 50, 50);
  groundLight(p, f);
  canvas.save(); canvas.translate(0, 10); projected(p, f, 0, 0); cached(p, blur.ambient, 0.12 - 0.04 * n); canvas.restore(); canvas.restore();
  canvas.save(); canvas.translate(0, 1.5); projected(p, f, 0, 0); cached(p, blur.contact, 0.25); canvas.restore(); canvas.restore();
  projected(p, f, 0, 0);
  reset(p, 0xffffff, 1, 0.5); p.paint.setColor(mixed(0x8c8c8c, 0x080809, n)); canvas.drawRRect(capsule, p.paint); canvas.restore();
  for (let index = 0; index < 20; index++) {
    const fraction = index / 19, back = 0.52 + 0.48 * fraction;
    projected(p, f, fraction * 18, angle * fraction);
    reset(p);
    p.paint.setShader(Skia.Shader.MakeLinearGradient({ x: 50, y: 0 }, { x: 50, y: 260 },
      [mixed(0x45138f, 0xf7caff, n, back), mixed(0x8934d4, 0xdb8eff, n, back), mixed(0xc15cf1, 0x45138f, n, back)], [0, 0.5, 1], TileMode.Clamp, undefined, 1));
    canvas.drawRRect(capsule, p.paint); canvas.restore();
  }
  projected(p, f, 18, angle); cached(p, blur.rim, 0.75 * n); canvas.restore();
  projected(p, f, 18, angle); canvas.clipRRect(capsule, ClipOp.Intersect, true);
  // Premultiplied contributions share an isolated layer, preserving opaque face coverage.
  canvas.saveLayer(undefined, Skia.XYWHRect(0, 0, 100, 260));
  reset(p); p.paint.setAlphaf(1 - n);
  canvas.drawImageRect(day, Skia.XYWHRect(0, 0, day.width(), day.height()),
    Skia.XYWHRect(-22 * 100 / 187, -40 * 260 / 526, 231 * 100 / 187, 600 * 260 / 526), p.paint);
  reset(p); p.paint.setAlphaf(n); p.paint.setBlendMode(BlendMode.Plus);
  canvas.drawImageRect(night, Skia.XYWHRect(0, 0, night.width(), night.height()),
    Skia.XYWHRect(-21 * 100 / 189, -40 * 260 / 525, 231 * 100 / 189, 600 * 260 / 525), p.paint);
  canvas.restore(); canvas.restore();
  projected(p, f, 18, angle); reset(p, 0xffffff, 1, 0.6);
  p.paint.setShader(Skia.Shader.MakeLinearGradient({ x: 50, y: 0 }, { x: 50, y: 260 },
    [mixed(0xddd9dd, 0xf7caff, n, 0.28 + 0.62 * n), mixed(0xbd69e6, 0x8934d4, n, 0.18)], [0, 1], TileMode.Clamp, undefined, 1));
  canvas.drawRRect(capsule, p.paint); canvas.restore();
  projected(p, f, 0, 0);
  label(p, font, 'ER', 119, 11, mixed(0x727272, 0xf1f1f1, n));
  label(p, font, 'SÜ', 119, 260, mixed(0x727272, 0xf1f1f1, n));
  for (let index = 0; index < 19; index++) {
    reset(p); p.paint.setColor(mixed(0xa9adb3, 0xf1f1f1, dot(f, index)));
    canvas.drawCircle(119, 33 + index * 197 / 18, 1.2, p.paint);
    reset(p, 0, 0.1 * (1 - n), 0.35); canvas.drawCircle(119, 33 + index * 197 / 18, 1.2, p.paint);
  }
  canvas.restore();
}
function lightHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }
function uiNow() { 'worklet'; return performance.now() / 1000; }

/** A physical emitting rocker with independently captured wide, macro and overhead cameras. */
export default function VioletRockerCard({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const preview = previewTime !== undefined && Number.isFinite(previewTime) ? Math.max(0, previewTime) : undefined;
  const startupReduced = useReducedMotion();
  const [reduced, setReduced] = useState(startupReduced), [nightMode, setNightMode] = useState(startupReduced);
  const day = useImage(DAY), night = useImage(NIGHT);
  const font = useMemo(() => matchFont({ fontFamily: Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: 18, fontWeight: '400' }), []);
  const blur = useMemo(() => ({ ambient: makeBlurAsset(22, 88, 0), contact: makeBlurAsset(3, 12, 0), rim: makeBlurAsset(4, 16, 0xb32eff, 2) }), []);
  const motion = useSharedValue(initialMotion()), active = useSharedValue(AppState.currentState == null || AppState.currentState === 'active');
  const ready = useSharedValue(false), reduction = useSharedValue(startupReduced), accepted = useSharedValue(false);
  const touchStart = useSharedValue({ x: 0, y: 0 });
  const driverRef = useRef<FrameCallback | null>(null);
  const setRunning = useCallback((value: boolean) => { driverRef.current?.setActive(value); }, []);
  const driver = useFrameCallback(tick => {
    'worklet';
    if (!active.get() || !ready.get() || reduction.get()) return;
    const now = tick.timestamp / 1000;
    let m = motion.get();
    if (m.epoch < 0) m = { ...m, epoch: now };
    if (m.manual && !m.held && now - m.lastTouch >= (autoPlay ? 2.6 : 0.66)) {
      m = autoPlay ? { ...initialMotion(now), epoch: now } : { ...m, moving: false };
    }
    motion.set({ ...m, now });
  }, false);
  useEffect(() => { driverRef.current = driver; }, [driver]);
  const frame = useDerivedValue(() => sample(motion.get(), autoPlay, reduction.get(), preview), [autoPlay, preview]);
  useAnimatedReaction(() => {
    const m = motion.get();
    return active.get() && ready.get() && !reduction.get() && preview === undefined && !m.held && (autoPlay || m.moving);
  }, (running, old) => { if (running !== old) scheduleOnRN(setRunning, running); }, [autoPlay, preview, setRunning]);
  useAnimatedReaction(() => frame.get().n >= 0.5, (isNight, old) => { if (isNight !== old) scheduleOnRN(setNightMode, isNight); });
  useEffect(() => {
    let mounted = true, revision = 0;
    const update = (value: boolean) => { revision++; reduction.set(value); setReduced(value); };
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', update);
    const initialRevision = revision;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (mounted && revision === initialRevision) update(value);
    }).catch(() => { /* The synchronous system value remains valid if the query is unavailable. */ });
    return () => { mounted = false; subscription.remove(); };
  }, [reduction]);
  useEffect(() => { ready.set(Boolean(day && night)); }, [day, night, ready]);
  useEffect(() => {
    scheduleOnUI(() => { 'worklet'; motion.set(initialMotion()); accepted.set(false); });
  }, [autoPlay, reduced, preview, motion, accepted]);
  useEffect(() => {
    const listener = AppState.addEventListener('change', state => {
      active.set(state === 'active');
      scheduleOnUI(() => { 'worklet'; motion.set(initialMotion()); accepted.set(false); });
    });
    return () => { listener.remove(); setRunning(false); };
  }, [active, accepted, motion, setRunning]);
  const choose = useCallback((target: number) => {
    if (preview !== undefined) return;
    scheduleOnUI((value: number) => {
      'worklet';
      if (!active.get()) return;
      const now = uiNow(), m = motion.get(), isReduced = reduction.get();
      const before = sample({ ...m, now }, autoPlay, isReduced);
      motion.set(select(m, value, now, autoPlay, isReduced));
      if (!isReduced && (before.n >= 0.5) !== (value >= 0.5)) scheduleOnRN(lightHaptic);
    }, target);
  }, [active, autoPlay, motion, preview, reduction]);
  const scale = width / 393, top = Math.max(0, (height - 852 * scale) / 2);
  const pan = Gesture.Pan().minDistance(0).enabled(preview === undefined)
    .onStart(event => {
      const now = uiNow(), start = { x: event.x / scale, y: (event.y - top) / scale };
      touchStart.set(start); accepted.set(false);
      if (!active.get()) return;
      const m = motion.get(), isReduced = reduction.get();
      if (isReduced) {
        const f = sample({ ...m, now }, autoPlay, true);
        accepted.set(contains(local(projection(f, rock(f), 18), start))); return;
      }
      const next = begin(m, start, now, autoPlay);
      if (next) { accepted.set(true); motion.set(next); }
    })
    .onUpdate(event => {
      if (!accepted.get() || reduction.get()) return;
      const m = motion.get(); if (!m.held) return;
      const now = uiNow();
      const dragged = m.dragged || Math.hypot(event.translationX / scale, event.translationY / scale) > 3;
      let heldNight = m.heldNight;
      if (dragged) {
        const f = sample({ ...m, now }, autoPlay, false);
        const p = local(projection(f, rock(f), 18), { x: event.x / scale, y: (event.y - top) / scale });
        if (p) heldNight = clamp((p.y + 95) / 190);
      }
      motion.set({ ...m, now, dragged, heldNight, lastTouch: now });
    })
    .onEnd(event => {
      if (!accepted.get()) return;
      const now = uiNow(), m = motion.get(), isReduced = reduction.get();
      const f = sample({ ...m, now }, autoPlay, isReduced), p = projection(f, rock(f), 18);
      if (isReduced) {
        const start = local(p, touchStart.get());
        if (contains(start)) motion.set({ ...m, now, reducedNight: start!.y < 0 ? 0 : 1 });
        accepted.set(false); return;
      }
      if (!m.held) return;
      const end = { x: event.x / scale, y: (event.y - top) / scale };
      let target = 0, velocity = 0;
      if (!m.dragged) { target = (local(p, end)?.y ?? 0) < 0 ? 0 : 1; }
      else {
        const predicted = { x: end.x + event.velocityX / scale * 0.1, y: end.y + event.velocityY / scale * 0.1 };
        const mappedPoint = local(p, end), predictedPoint = local(p, predicted);
        const mapped = mappedPoint ? clamp((mappedPoint.y + 95) / 190) : m.heldNight;
        const projectedNight = predictedPoint ? clamp((predictedPoint.y + 95) / 190) : m.heldNight;
        target = projectedNight >= 0.5 ? 1 : 0; velocity = (projectedNight - mapped) / 0.1;
      }
      motion.set(finish(m, target, velocity, now)); accepted.set(false);
      if (m.originalOn !== (target >= 0.5)) scheduleOnRN(lightHaptic);
    })
    .onFinalize(() => {
      if (!accepted.get()) return;
      const m = motion.get();
      if (m.held && !reduction.get()) motion.set(finish(m, m.heldNight >= 0.5 ? 1 : 0, 0, uiNow()));
      accepted.set(false);
    });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const f = frame.get();
    canvas.clear(mixed(0xc2c2c2, 0x111113, f.n));
    if (day && night) {
      canvas.save(); canvas.translate(0, top); canvas.scale(scale, scale);
      canvas.clipRect(Skia.XYWHRect(0, 0, 393, 852), ClipOp.Intersect, true);
      drawArt({ canvas, paint: Skia.Paint() }, f, day, night, font, blur); canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, top, scale, day, night, font, blur]);
  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen} accessible accessibilityRole="switch" accessibilityLabel="Ambient light"
        accessibilityState={{ checked: nightMode }} accessibilityValue={{ text: nightMode ? 'On' : 'Off' }}
        accessibilityHint="Press the upper half for off or the lower half for on. Drag to rock the light."
        accessibilityActions={[{ name: 'activate', label: 'Toggle ambient light' }, { name: 'increment', label: 'Turn on' }, { name: 'decrement', label: 'Turn off' }]}
        onAccessibilityTap={() => choose(nightMode ? 0 : 1)}
        onAccessibilityAction={event => {
          const action = event.nativeEvent.actionName;
          if (action === 'increment') choose(1);
          else if (action === 'decrement') choose(0);
          else if (action === 'activate') choose(nightMode ? 0 : 1);
        }}>
        <StatusBar style={nightMode ? 'light' : 'dark'} />
        <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
      </View>
    </GestureDetector>
  );
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#C2C2C2' } });
