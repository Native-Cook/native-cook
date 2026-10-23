import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, PaintStyle, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

export const LOOP = 8;
const BG = '#454FDC';
function ease(value: number) { 'worklet'; const u = Math.max(0, Math.min(1, value)); return u * u * (3 - 2 * u); }
function paint(color: string, alpha = 1) {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(Skia.Color(color)); p.setAlphaf(Math.max(0, Math.min(1, alpha))); return p;
}
function fill(canvas: SkCanvas, path: SkPath, color: string, alpha = 1) {
  'worklet'; canvas.drawPath(path, paint(color, alpha));
}
function gradient(canvas: SkCanvas, path: SkPath, colors: string[], start: number[], end: number[], alpha = 1, width = 0) {
  'worklet'; const p = paint('#ffffff', alpha);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: start[0], y: start[1] }, { x: end[0], y: end[1] }, colors.map(c => Skia.Color(c)), null, TileMode.Clamp));
  if (width > 0) { p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(width); }
  canvas.drawPath(path, p);
}
function radial(canvas: SkCanvas, x: number, y: number, radius: number, color: string, alpha = 1, startRadius = 15) {
  'worklet'; const p = paint('#ffffff', alpha);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, radius, [Skia.Color(color), Skia.Color(color), Skia.Color(color + '00')], [0, startRadius / radius, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, radius, p);
}
function stroke(canvas: SkCanvas, path: SkPath, color: string, width: number, alpha = 1) {
  'worklet'; const p = paint(color, alpha); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(width); p.setStrokeCap(StrokeCap.Round); p.setStrokeJoin(StrokeJoin.Round); canvas.drawPath(path, p);
}
function circle(x: number, y: number, radius: number) { 'worklet'; const p = Skia.Path.Make(); p.addCircle(x, y, radius); return p; }
function roundRect(x: number, y: number, width: number, height: number, radius: number) {
  'worklet'; const p = Skia.Path.Make(); p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, width, height), radius, radius)); return p;
}
type Fonts = Record<string, SkFont>;
function label(canvas: SkCanvas, fonts: Fonts, text: string, x: number, y: number, size: number, color: string, bold = false, alpha = 1) {
  'worklet'; const font = fonts[`${size}-${bold}`];
  const width = font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, n) => sum + n, 0);
  const metrics = font.getMetrics();
  canvas.drawText(text, x - width / 2, y - (metrics.ascent + metrics.descent) / 2, paint(color, alpha), font);
}

function draw(c: SkCanvas, fonts: Fonts, t: number) {
  'worklet';
  const reset = 1 - ease((t - 5.8) / 1.6);
  const trace = ease((t - 0.6) / 2) * reset, lift = ease((t - 2.6) / 1) * reset;
  const reveal = ease((t - 3.4) / 0.6) * (1 - ease((t - 5.8) / 0.5));
  const phase = ease((t - 0.6) / 6.8) * Math.PI * 2;
  for (let i = 0; i < 4; i++) {
    radial(c, 196.5 + 145 * Math.sin(phase + i * 1.8), 130 + i * 190 + 90 * Math.cos(phase + i), 260, i % 2 === 0 ? '#E6AAEF' : '#707AFF', 0.85);
  }
  const side = 136 - 24 * lift, cy = 426 - 108 * lift;
  const tile = roundRect(196.5 - side / 2, cy - side / 2, side, side, 32);
  radial(c, 196.5, cy + 30, 90, '#282392', 0.3, 5);
  gradient(c, tile, ['#FFFFFF2B', '#FFFFFF0A'], [140, cy - 60], [250, cy + 60]);
  gradient(c, tile, ['#FFFFFF73', '#FFFFFF14'], [140, cy - 60], [250, cy + 60], 1, 0.8);
  const ribbon = Skia.Path.Make(), count = Math.floor(180 * trace);
  for (let i = 0; i <= count; i++) {
    const a = i / 180 * Math.PI * 2 - Math.PI / 2;
    const x = 196.5 + 35 * Math.sin(a) * (1 - 0.15 * lift), y = cy + 20 * Math.sin(2 * a) * (1 - 0.15 * lift);
    if (i === 0) ribbon.moveTo(x, y); else ribbon.lineTo(x, y);
  }
  stroke(c, ribbon, '#FFFFFF', 3.5);
  const tipAngle = count / 180 * Math.PI * 2 - Math.PI / 2;
  const tipX = 196.5 + 35 * Math.sin(tipAngle) * (1 - 0.15 * lift), tipY = cy + 20 * Math.sin(2 * tipAngle) * (1 - 0.15 * lift);
  radial(c, tipX, tipY, 7, '#FFFFFF', Math.sin(Math.PI * trace) * 0.8, 0);
  fill(c, circle(tipX, tipY, 2.5), '#FFFFFF', Math.sin(Math.PI * trace));
  label(c, fonts, 'velune', 196.5, cy + side / 2 + 35, 24, '#FFFFFF', true, ease((t - 1.8) / 0.6) * reset);
  label(c, fonts, 'A calmer kind', 196.5, 568 + 16 * (1 - reveal), 30, '#FFFFFF', true, reveal);
  label(c, fonts, 'of everyday.', 196.5, 605 + 16 * (1 - reveal), 30, '#FFFFFF', true, reveal);
  label(c, fonts, 'A moment for you, every day.', 196.5, 649, 14, '#FFFFFF', false, reveal * 0.85);
  fill(c, roundRect(28, 700, 337, 54, 27), '#FFFFFF', reveal);
  label(c, fonts, 'Take a breath', 196.5, 727, 15, '#454FDC', true, reveal);
  label(c, fonts, 'Make room for a little calm', 196.5, 783, 11, '#FFFFFF', false, reveal * 0.8);
}


/** Deterministic scene; previewTime is optional for still previews. */
export function MistGlassSplash({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => { const sub = AppState.addEventListener('change', state => setActive(state === 'active')); return () => sub.remove(); }, []);
  const time = useSharedValue(0), elapsed = useSharedValue(0), held = useSharedValue(0);
  const touching = useSharedValue(false), released = useSharedValue(-1);
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? 4.8 : 0); }, [previewTime, reduced, time]);
  const frame = useFrameCallback(info => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    if (touching.get()) { time.set(held.get()); return; }
    if (released.get() >= 0) {
      const age = elapsed.get() - released.get();
      time.set(age < 2 ? held.get() : age < 2.6 ? held.get() * (1 - ease((age - 2) / 0.6)) : autoPlay ? (age - 2.6) % LOOP : 0);
    } else { time.set(autoPlay ? elapsed.get() % LOOP : 0); }
  }, false);
  useEffect(() => { frame.setActive(active && !reduced && previewTime === undefined); return () => frame.setActive(false); }, [active, reduced, previewTime, frame]);
  const fonts = useMemo(() => {
    const result: Fonts = {};
    for (const size of [11, 12, 14, 15, 16, 18, 24, 30, 32, 34, 36, 42, 46]) for (const bold of [false, true]) {
      result[`${size}-${bold}`] = matchFont({ fontFamily: Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: size, fontWeight: bold ? '700' : '400' });
    }
    return result;
  }, []);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.drawColor(Skia.Color(BG));
    const scale = Math.min(width / 393, height / 852);
    canvas.translate((width - 393 * scale) / 2, (height - 852 * scale) / 2); canvas.scale(scale, scale);
    draw(canvas, fonts, time.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, fonts]);
  const haptic = () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); };
  const pan = Gesture.Pan().minDistance(0).enabled(!reduced && previewTime === undefined)
    .onBegin(e => { touching.set(true); held.set(Math.max(0, Math.min(7.4, e.x / width * 7.4))); scheduleOnRN(haptic); })
    .onUpdate(e => { held.set(Math.max(0, Math.min(7.4, e.x / width * 7.4))); })
    .onEnd(e => { if (Math.abs(e.translationX) < 8) held.set(4.8); })
    .onFinalize(() => { touching.set(false); released.set(elapsed.get()); });
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityLabel="Velune. A calmer kind of everyday. Drag to draw the glass mark.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
