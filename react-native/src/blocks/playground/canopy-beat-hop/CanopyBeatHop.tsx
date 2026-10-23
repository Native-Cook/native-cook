import {
  BlurStyle, Canvas, FilterMode, matchFont, MipmapMode, PaintStyle, Picture, Skia, TileMode, useImage,
  type SkCanvas, type SkFont, type SkImage, type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo } from 'react';
import { AppState, Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { cancelAnimation, ReduceMotion, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

const LOOP = 10;
const LANES = [0, 0.45, -0.45, -0.8, 0, 0.8, 0.85, -0.85, 0.85, -0.85, 0.85, -0.85, 0.85, 0, -0.65, 0.65, -1.6, 0, 1.6, 0, -1.6, 0, 0.45, 0];
const FACES = [0x8cffcb, 0x91cbff, 0xf58cf4, 0xffe395, 0x8cffcb];
const NEONS = [0x00ffab, 0x2aaeff, 0xf82aeb, 0xffc22d, 0x00ffab];
const GROUNDS = [0x0fb36b, 0x165fa5, 0xa00e9b, 0xac7417, 0x0fb36b];
type Props = { autoPlay?: boolean; /** Deterministic phase for previews. */ previewTime?: number };
type Fonts = { great: SkFont; perfect: SkFont; combo: SkFont };

function smooth(value: number) { 'worklet'; const f = Math.max(0, Math.min(1, value)); return f * f * (3 - 2 * f); }
function lane(index: number) { 'worklet'; return LANES[(index % 24 + 24) % 24]; }
function travelAt(time: number) {
  'worklet';
  const q = Math.max(0, Math.min(1, (time - 0.75) / 8.5)), r = 0.12 / 8.5;
  const integrated = q < r ? q * q / (2 * r) : q > 1 - r ? 1 - r - (1 - q) ** 2 / (2 * r) : q - r / 2;
  return integrated / (1 - r) * 24;
}
function color(hex: number, opacity = 1) {
  'worklet';
  return Skia.Color(`rgba(${(hex >> 16) & 255},${(hex >> 8) & 255},${hex & 255},${Math.max(0, Math.min(1, opacity))})`);
}
function blend(a: number, b: number, f: number) {
  'worklet';
  const r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
  const g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
  const blue = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
  return (r << 16) | (g << 8) | blue;
}
function tile(x: number, y: number, s: number) {
  'worklet';
  const pts = [[x - 85 * s, y - 60 * s], [x + 85 * s, y - 60 * s], [x + 104 * s, y + 60 * s], [x - 104 * s, y + 60 * s]];
  const path = Skia.PathBuilder.Make();
  for (let i = 0; i < 4; i++) {
    const a = pts[(i + 3) % 4], b = pts[i], c = pts[(i + 1) % 4];
    const ex = b[0] + (a[0] - b[0]) * 0.07, ey = b[1] + (a[1] - b[1]) * 0.07;
    if (i === 0) path.moveTo(ex, ey); else path.lineTo(ex, ey);
    path.quadTo(b[0], b[1], b[0] + (c[0] - b[0]) * 0.07, b[1] + (c[1] - b[1]) * 0.07);
  }
  path.close(); return path.detach();
}
function diamond(x: number, y: number, w: number, h: number) {
  'worklet'; return Skia.PathBuilder.Make().moveTo(x, y - h).lineTo(x + w, y).lineTo(x, y + h).lineTo(x - w, y).close().detach();
}
function reset(paint: SkPaint, hex: number, opacity = 1, width = 0) {
  'worklet';
  paint.setShader(null); paint.setMaskFilter(null); paint.setAlphaf(1); paint.setColor(color(hex, opacity));
  paint.setStyle(width > 0 ? PaintStyle.Stroke : PaintStyle.Fill); paint.setStrokeWidth(width); paint.setAntiAlias(true);
}
function text(canvas: SkCanvas, paint: SkPaint, value: string, x: number, y: number, font: SkFont, hex: number, opacity = 1) {
  'worklet'; reset(paint, hex, opacity);
  const widths = font.getGlyphWidths(font.getGlyphIDs(value)); let width = 0;
  for (const w of widths) width += w;
  const metrics = font.getMetrics(); canvas.drawText(value, x - width / 2, y - (metrics.ascent + metrics.descent) / 2, paint, font);
}
function image(canvas: SkCanvas, paint: SkPaint, img: SkImage, x: number, y: number, w: number, h: number, opacity = 1) {
  'worklet'; reset(paint, 0xffffff, opacity);
  canvas.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.None, paint);
}

function outlinedLabel(canvas: SkCanvas, paint: SkPaint, value: string, x: number, y: number, font: SkFont, ink: number, opacity: number) {
  'worklet';
  text(canvas, paint, value, x + 1.5, y + 3, font, 0, opacity * 0.25);
  for (let j = 0; j < 8; j++) { const a = j * Math.PI / 4; text(canvas, paint, value, x + Math.cos(a) * 1.5, y + Math.sin(a) * 1.5, font, 0xffffff, opacity); }
  text(canvas, paint, value, x, y, font, ink, opacity);
}
function drawTile(canvas: SkCanvas, paint: SkPaint, x: number, y: number, s: number, active: boolean, neon: number, face: number, ground: number, distance: number) {
  'worklet';
  const top = tile(x, y, s);
  const side = Skia.PathBuilder.Make().moveTo(x - 103 * s, y + 57 * s).lineTo(x + 103 * s, y + 57 * s).lineTo(x + 101 * s, y + 102 * s).lineTo(x - 101 * s, y + 102 * s).close().detach();
  reset(paint, 0xffffff); paint.setShader(Skia.Shader.MakeLinearGradient({ x, y: y + 50 * s }, { x, y: y + 105 * s }, [color(active ? face : neon, active ? 0.8 : 0.3), color(neon, 0.28), color(ground, 0)], [0, 0.5, 1], TileMode.Clamp)); canvas.drawPath(side, paint);
  reset(paint, neon, active ? 0.9 : 0.5); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8 * s, true)); canvas.drawPath(top, paint);
  reset(paint, active ? face : ground); canvas.drawPath(top, paint);
  reset(paint, neon, 0.8, 6 * s); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8 * s, true)); canvas.drawPath(top, paint); paint.setMaskFilter(null); canvas.drawPath(top, paint);
  reset(paint, 0xebfffc, 1, 2.8 * s); canvas.drawPath(top, paint);
  if (distance < -0.04) for (let j = 1; j <= 2; j++) {
    reset(paint, neon, Math.max(0, (0.43 + distance * 0.3) / j), 2 * s); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2 * s, true)); canvas.drawPath(tile(x, y, s * (1 + j * 0.26)), paint);
  }
}
function drawCourier(canvas: SkCanvas, paint: SkPaint, courier: SkImage, x: number, jump: number, squash: number, lean: number) {
  'worklet';
  const sw = 55 * (1 - jump * 0.3);
  reset(paint, 0, 0.27 - jump * 0.17); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true)); canvas.drawOval(Skia.XYWHRect(x - sw / 2, 615, sw, 13), paint);
  canvas.save(); canvas.translate(x, 615 - jump * 170); canvas.rotate(lean * 180 / Math.PI, 0, 0);
  const scale = 0.89 + jump * 0.11; canvas.scale(scale * (1 + squash * 0.5), scale * (1 - squash)); image(canvas, paint, courier, -50, -143, 100, 143); canvas.restore();
}
/** Geometry, lane changes and timing match CanopyBeatHop.swift. */
function drawScene(canvas: SkCanvas, time: number, manual: number, steering: number, still: boolean, veil: number, artwork: SkImage[], courier: SkImage, fonts: Fonts) {
  'worklet';
  let theme = 0, mix = 0;
  if (time >= 2.15 && time < 4.15) mix = smooth((time - 2.15) / 0.35);
  else if (time >= 4.15 && time < 6.15) { theme = 1; mix = smooth((time - 4.15) / 0.35); }
  else if (time >= 6.15 && time < 8.15) { theme = 2; mix = smooth((time - 6.15) / 0.35); }
  else if (time >= 8.15) { theme = 3; mix = smooth((time - 8.15) / 0.35); }
  const paint = Skia.Paint(); paint.setAntiAlias(true);
  image(canvas, paint, artwork[theme % 4], 0, 0, 393, 852); image(canvas, paint, artwork[(theme + 1) % 4], 0, 0, 393, 852, mix);
  const neon = blend(NEONS[theme], NEONS[theme + 1], mix), face = blend(FACES[theme], FACES[theme + 1], mix), ground = blend(GROUNDS[theme], GROUNDS[theme + 1], mix);
  const p = manual < 0 ? travelAt(time) : manual, step = Math.floor(p), f = p - Math.floor(p);
  const resting = manual < 0 && (time <= 0.75 || time >= 9.25), jump = still || resting ? 0 : 4 * f * (1 - f);
  const current = lane(step), next = lane(step + 1), playerLane = manual < 0 ? current + (next - current) * smooth(f) : steering;
  const playerX = 196.5 + playerLane * 100;
  const layer = Skia.Paint();
  for (let i = step + 6; i >= step - 2; i--) {
    const d = i - p; if (d < -1.7 || d > 5.5) continue;
    const s = d < 0 ? 1 - 0.8 * d : 1 / (1 + 0.8 * d), y = d < 0 ? 615 - 270 * d : 225 + 390 * s - 15 * d, n = (i % 24 + 24) % 24, selected = lane(i);
    const positions = n >= 5 && n <= 10 ? [selected, selected + (selected > 0 ? -1.9 : 1.9)] : n >= 16 && n <= 21 ? [-1.6, 0, 1.6] : [selected];
    for (const position of positions) {
      const x = 196.5 + position * 100 * s, active = Math.abs(position - selected) < 0.01, hit = d <= 0.02;
      const opacity = 1 - smooth((d - 4.8) / 0.7); layer.setAlphaf(opacity); if (opacity < 1) canvas.saveLayer(layer); else canvas.save();
      drawTile(canvas, paint, x, y, s, active && !hit, neon, face, ground, d);
      if (active && !hit) {
        if (n % 5 === 3 && d > 0.15) {
          const cy = y - 22 * s, w = 7 * s, h = 17 * s;
          reset(paint, 0xffffff); paint.setShader(Skia.Shader.MakeLinearGradient({ x: x - w, y: cy - h }, { x: x + w, y: cy + h }, [color(0xbcffff), color(0x147aff)], [0, 1], TileMode.Clamp)); canvas.drawPath(diamond(x, cy, w, h), paint);
          reset(paint, 0x9beeff, 0.7); canvas.drawPath(Skia.PathBuilder.Make().moveTo(x, cy - h).lineTo(x, cy + h).lineTo(x - w, cy).close().detach(), paint);
        }
        if (n % 4 === 1) { reset(paint, 0xffffff, 0.5); canvas.drawPath(diamond(x, y, 22 * s, 9 * s), paint); }
      }
      canvas.restore();
    }
  }
  const squash = still || resting ? 0 : Math.exp(-f * 22) * Math.cos(f * 22) * 0.06;
  const lean = still || resting ? 0 : manual < 0 ? (next - current) * Math.sin(f * Math.PI) * 0.05 : playerLane * 0.035;
  drawCourier(canvas, paint, courier, playerX, jump, squash, lean);
  if (!still && !resting && step % 5 === 3 && f < 0.75) {
    const life = Math.min(1, f / 0.75), radius = 46 * (1 - (1 - life) ** 3);
    for (let j = 0; j < 12; j++) { const a = j * Math.PI / 6; reset(paint, j % 2 === 0 ? 0xd5ffff : 0x63b9ff, 1 - life); canvas.drawPath(diamond(playerX + Math.cos(a) * radius, 533 - jump * 100 + Math.sin(a) * radius * 0.65 - life * 20, 2.5 * (1 - life), 4 * (1 - life)), paint); }
  }
  const n = (step % 24 + 24) % 24, perfect = !resting && [2, 3, 4, 9, 10, 18].includes(n);
  const combo = n >= 2 && n <= 4 ? n - 1 : n >= 9 && n <= 10 ? n - 8 : 1;
  if (perfect && !still) {
    const flash = 0.85 * Math.exp(-f * 11);
    reset(paint, 0xffffff); paint.setShader(Skia.Shader.MakeRadialGradient({ x: 196.5, y: 86 }, 83, [color(0xffffff, flash), color(0xffffff, flash * 0.5), color(0xffffff, 0)], [0, 0.5, 1], TileMode.Clamp)); canvas.drawOval(Skia.XYWHRect(114, 4, 165, 165), paint);
  }
  const opacity = resting ? 1 : 0.72 + 0.28 * Math.exp(-f * 8), scale = resting ? 1 : 1 + 0.08 * Math.exp(-f * 12);
  canvas.save(); canvas.translate(196.5, 86); canvas.scale(scale, scale);
  outlinedLabel(canvas, paint, perfect ? 'PERFECT' : 'GREAT', 0, 0, perfect ? fonts.perfect : fonts.great, perfect ? 0xf020d9 : 0x159fd0, opacity); canvas.restore();
  if (perfect) outlinedLabel(canvas, paint, `X${combo}`, 196.5, 133, fonts.combo, 0xf43ce4, opacity);
  if (veil > 0) { reset(paint, 0x085e4f, veil); canvas.drawRect(Skia.XYWHRect(0, 0, 393, 852), paint); }
}
function touchHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

export default function CanopyBeatHop({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions(), reduced = useReducedMotion();
  const day = useImage(require('./assets/day.png')), night = useImage(require('./assets/night.png')), violet = useImage(require('./assets/violet.png')), amber = useImage(require('./assets/amber.png')), courier = useImage(require('./assets/courier.png'));
  const latestFrame = useSharedValue(0);
  const clock = useSharedValue(0), epoch = useSharedValue(-1), active = useSharedValue(true), manualStart = useSharedValue(-1), startTravel = useSharedValue(0), steering = useSharedValue(0);
  const lastTouch = useSharedValue(-100), dragging = useSharedValue(false), releaseTime = useSharedValue(0), releaseVelocity = useSharedValue(0), resuming = useSharedValue(false);
  const fonts = useMemo(() => {
    const family = Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif';
    return { great: matchFont({ fontFamily: family, fontSize: 34, fontWeight: '900' }), perfect: matchFont({ fontFamily: family, fontSize: 39, fontWeight: '900' }), combo: matchFont({ fontFamily: family, fontSize: 19, fontWeight: '900' }) };
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', state => { active.set(state === 'active'); if (state === 'active') { epoch.set(-1); manualStart.set(-1); } });
    return () => { sub.remove(); cancelAnimation(manualStart); };
  }, [active, epoch, manualStart]);
  const callback = useFrameCallback(frame => {
    'worklet'; if (!active.get()) return;
    const now = frame.timestamp / 1000; latestFrame.set(now); if (epoch.get() < 0) epoch.set(now);
    if (manualStart.get() >= 0 && !dragging.get() && now - lastTouch.get() >= 2.25) { manualStart.set(-1); epoch.set(now); resuming.set(true); }
    const needsFrame = autoPlay || manualStart.get() >= 0 || resuming.get();
    if (resuming.get() && now - epoch.get() >= 0.25) resuming.set(false);
    if (needsFrame) clock.set(now);
  }, !reduced && previewTime === undefined);
  useEffect(() => { callback.setActive(!reduced && previewTime === undefined); }, [callback, reduced, previewTime]);
  const pan = Gesture.Pan().minDistance(0)
    .onBegin(event => {
      cancelAnimation(manualStart);
      const now = reduced ? 0 : latestFrame.get(); clock.set(now); if (manualStart.get() < 0) { startTravel.set(travelAt(autoPlay && epoch.get() >= 0 ? (now - epoch.get()) % LOOP : 0)); manualStart.set(now); }
      steering.set(Math.max(-1.35, Math.min(1.35, (event.x / width * 393 - 196.5) / 100))); dragging.set(true); lastTouch.set(now); scheduleOnRN(touchHaptic);
    })
    .onUpdate(event => { steering.set(Math.max(-1.35, Math.min(1.35, (event.x / width * 393 - 196.5) / 100))); lastTouch.set(clock.get()); })
    .onFinalize(event => { dragging.set(false); lastTouch.set(clock.get()); releaseTime.set(clock.get()); releaseVelocity.set(Math.max(-3, Math.min(3, event.velocityX / width * 393 / 100))); if (reduced) manualStart.set(withDelay(2250, withTiming(-1, { duration: 0, reduceMotion: ReduceMotion.Never }), ReduceMotion.Never)); });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (day && night && violet && amber && courier) {
      canvas.scale(width / 393, height / 852);
      const elapsed = Math.max(0, clock.get() - Math.max(0, epoch.get())), time = reduced ? 0 : previewTime ?? (autoPlay ? elapsed % LOOP : 0);
      const manual = manualStart.get() < 0 ? -1 : startTravel.get() + (reduced ? 0 : (clock.get() - manualStart.get()) * (24 / 8.38)), age = Math.max(0, clock.get() - releaseTime.get());
      const steer = steering.get() + (dragging.get() || reduced ? 0 : releaseVelocity.get() * age * Math.exp(-10 * age));
      const veil = manual < 0 ? (resuming.get() ? 1 - smooth(elapsed / 0.25) : 0) : dragging.get() ? 0 : smooth((clock.get() - lastTouch.get() - 2) / 0.25);
      drawScene(canvas, time, manual, steer, reduced, veil, [day, night, violet, amber], courier, fonts);
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, day, night, violet, amber, courier, reduced, previewTime, autoPlay, fonts]);
  function accessibleMove(value: number) { const now = reduced ? 0 : latestFrame.get(); clock.set(now); manualStart.set(now); startTravel.set(0); steering.set(value); lastTouch.set(now); touchHaptic(); if (reduced) manualStart.set(withDelay(2250, withTiming(-1, { duration: 0, reduceMotion: ReduceMotion.Never }), ReduceMotion.Never)); }
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel="Canopy Beat Hop game" accessibilityHint="Drag left or right to steer. Tap to hop."
    accessibilityActions={[{ name: 'increment', label: 'Move Right' }, { name: 'decrement', label: 'Move Left' }, { name: 'activate', label: 'Hop' }]}
    onAccessibilityAction={event => accessibleMove(event.nativeEvent.actionName === 'increment' ? 0.75 : event.nativeEvent.actionName === 'decrement' ? -0.75 : 0)}>
    <StatusBar style="light" /><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#075e51' } });
