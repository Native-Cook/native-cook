import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

export const LOOP = 8;
const BG = '#FFF9F7';
function ease(value: number) { 'worklet'; const u = Math.max(0, Math.min(1, value)); return u * u * (3 - 2 * u); }
function paint(color: string, alpha = 1) {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(Skia.Color(color)); p.setAlphaf(Math.max(0, Math.min(1, alpha))); return p;
}
function fill(canvas: SkCanvas, path: SkPath, color: string, alpha = 1) {
  'worklet'; canvas.drawPath(path, paint(color, alpha));
}
function gradient(canvas: SkCanvas, path: SkPath, colors: string[], start: number[], end: number[], alpha = 1) {
  'worklet'; const p = paint('#ffffff', alpha);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: start[0], y: start[1] }, { x: end[0], y: end[1] }, colors.map(c => Skia.Color(c)), null, TileMode.Clamp));
  canvas.drawPath(path, p);
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
  const bloom = ease((t - 0.6) / 1.4) * reset;
  const open = ease((t - 2) / 1.2) * reset;
  const reveal = ease((t - 3) / 0.6) * (1 - ease((t - 5.8) / 0.5));
  const radius = (22 + 26 * bloom) * (1 + 18 * open);
  const petals = Skia.Path.Make();
  for (let i = 0; i <= 240; i++) {
    const a = i / 240 * Math.PI * 2, r = radius * (1 + 0.24 * Math.cos(6 * a));
    const x = 196.5 + r * Math.cos(a + bloom * Math.PI / 3), y = 426 + r * Math.sin(a + bloom * Math.PI / 3);
    if (i === 0) petals.moveTo(x, y); else petals.lineTo(x, y);
  }
  petals.close(); gradient(c, petals, ['#FF885E', '#FF526B'], [80, 0], [313, 852]);
  for (let i = 0; i < 12; i++) {
    const a = i * Math.PI / 6 + bloom * 0.65, distance = 52 + 54 * bloom;
    fill(c, circle(196.5 + Math.cos(a) * distance, 426 + Math.sin(a) * distance, 1.5 + i % 3), i % 2 === 0 ? '#FF526B' : '#FF885E', bloom * (1 - open));
  }
  const emblem = Skia.Path.Make();
  for (let i = 0; i <= 120; i++) {
    const a = i / 120 * Math.PI * 2, r = 14 * (1 + 0.24 * Math.cos(6 * a));
    const x = 140 + r * Math.cos(a), y = 426 - 20 * reveal + r * Math.sin(a);
    if (i === 0) emblem.moveTo(x, y); else emblem.lineTo(x, y);
  }
  emblem.close(); fill(c, emblem, '#FFFFFF', reveal);
  label(c, fonts, 'flori', 220, 426 - 20 * reveal, 46, '#FFFFFF', true, reveal);
  label(c, fonts, 'Good things start', 196.5, 568 + 16 * (1 - reveal), 30, '#FFFFFF', true, reveal);
  label(c, fonts, 'with hello.', 196.5, 605 + 16 * (1 - reveal), 30, '#FFFFFF', true, reveal);
  label(c, fonts, 'A little spark. A new connection.', 196.5, 649, 14, '#FFFFFF', false, reveal * 0.88);
  fill(c, roundRect(28, 700, 337, 54, 27), '#FFFFFF', reveal);
  label(c, fonts, 'Make a connection', 196.5, 727, 15, '#FF526B', true, reveal);
  label(c, fonts, 'Your next chapter starts here', 196.5, 783, 11, '#FFFFFF', false, reveal * 0.8);
  label(c, fonts, 'FLORI', 196.5, 494, 12, '#FF526B', true, 1 - ease((t - 1.2) / 0.5) * reset);
}


/** Deterministic scene; previewTime is optional for still previews. */
export function PetalBurstSplash({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
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
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityLabel="Flori. Good things start with hello. Drag to open the petals.">
    <StatusBar style="dark" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
