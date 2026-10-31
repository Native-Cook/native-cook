import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, useFont, type SkCanvas, type SkFont, type SkPicture } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The poster wall of an invented design conference, Kiln 26: five bold type posters — "2026
// NOW", "Kiln Next", "The new way to build", "is now", "Make it" — each a field of colour with
// soft 3D blobs or tubes behind huge words. They change like blinds: the card is cut into six
// vertical slats and the next poster's slats slide up one after another, a little apart, so for a
// moment both posters show in stripes. Swipe sideways to change poster.
//
// Posters are drawn in code once (Archivo Expanded Black type, radial-gradient blobs, round
// strokes) into pictures; each frame draws the current poster and the next one's slats over it.
// Layout (393 x 852 pt): 34 pt top row; caption lines; card full width minus 40 pt, 4:5.3, centred
// at 48 % of the height (radius 18); counter and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 8 s): five posters of 1.6 s; each change runs 0.95-1.6 s of its stop:
// slat i (0..5) slides up from the card's bottom over 0.4 s starting 0.05 i s later (ease-out
// cubic); the counter rolls with it.
// Dragging: 260 pt per poster scrubs the slats; release finishes or undoes the change over 0.4 s,
// it stays 2 s, then the loop carries on from there.

/** Seconds. */
export const LOOP = 8;
const STOP = 1.6;
const CHANGE = 0.95;
const SLATS = 6;
const SLAT_DELAY = 0.05;
const SLAT_RUN = 0.4;
const N = 5;
const PER = 260;
const HOLD = 2;
const SNAP = 0.4;

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const BLACK = 'PosterMorphCarousel-ArchivoExpandedBlack';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

// --- Posters, drawn once on the JS thread -------------------------------------------------------

function blob(c: SkCanvas, x: number, y: number, r: number, light: string, deep: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: x - r * 0.35, y: y - r * 0.4 }, r * 1.5, [Skia.Color(light), Skia.Color(deep)], [0, 1], TileMode.Clamp));
  c.drawCircle(x, y, r, p);
}
function soft(c: SkCanvas, x: number, y: number, r: number, color: string, blur: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  c.drawCircle(x, y, r, p);
}
/** Text at `size` pt with its left baseline at (x, y); returns its width. */
function word(c: SkCanvas, font: SkFont, text: string, x: number, y: number, size: number, color: string | null, shader?: ReturnType<typeof Skia.Shader.MakeLinearGradient>) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  if (shader) p.setShader(shader);
  else p.setColor(Skia.Color(color ?? '#FFFFFF'));
  c.save();
  c.translate(x, y);
  c.scale(size / 100, size / 100);
  c.drawText(text, 0, 0, p, font);
  c.restore();
  return font.measureText(text).width * (size / 100);
}
/** The size at which `text` spans `w` pt. */
function fit(font: SkFont, text: string, w: number) {
  return (w / font.measureText(text).width) * 100;
}
function fillBg(c: SkCanvas, W: number, H: number, a: string, b: string) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: W * 0.4, y: H }, [Skia.Color(a), Skia.Color(b)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), p);
}

function posterNow(c: SkCanvas, W: number, H: number, font: SkFont) {
  fillBg(c, W, H, '#2F5BFF', '#62B6FF');
  blob(c, W * 0.3, H * 0.22, W * 0.32, '#FFE07A', '#FF7A00');
  blob(c, W * 0.72, H * 0.12, W * 0.2, '#FFD36B', '#F29100');
  soft(c, W * 0.85, H * 0.45, W * 0.16, 'rgba(255,170,40,0.75)', 18);
  word(c, font, '2026', 20, H * 0.64, 34, '#FFFFFF');
  word(c, font, 'NOW', 18, H * 0.88, fit(font, 'NOW', W - 36), '#FFFFFF');
  word(c, font, 'KILN 26', 20, H - 18, 11, 'rgba(255,255,255,0.85)');
}
function posterNext(c: SkCanvas, W: number, H: number, font: SkFont) {
  fillBg(c, W, H, '#FFFFFF', '#F4F1EC');
  blob(c, W * 0.25, H * 0.15, W * 0.2, '#FFB15C', '#FF6A2B');
  blob(c, W * 0.5, H * 0.2, W * 0.24, '#B48CFF', '#5B2BE8');
  blob(c, W * 0.78, H * 0.12, W * 0.18, '#FF8AC4', '#E8207A');
  blob(c, W * 0.35, H * 0.33, W * 0.12, '#FF9A9A', '#D8264A');
  const bar = Skia.Paint();
  bar.setColor(Skia.Color('#3D7BFF'));
  c.drawRect(Skia.XYWHRect(W * 0.16, H * 0.62, W * 0.1, H * 0.38), bar);
  blob(c, W * 0.7, H * 0.95, W * 0.28, '#FFE36B', '#FF9E1A');
  blob(c, W * 0.42, H * 0.98, W * 0.16, '#FF6B8B', '#D11A4A');
  word(c, font, 'Kiln', 24, H * 0.43, 18, '#5F6368');
  word(c, font, 'Next', 22, H * 0.56, fit(font, 'Next', W * 0.6), '#111111');
}
function posterWay(c: SkCanvas, W: number, H: number, font: SkFont) {
  fillBg(c, W, H, '#FF5FA2', '#FF9E2B');
  blob(c, W * 0.42, H * 0.42, W * 0.3, '#7CFFB8', '#00A86B');
  blob(c, W * 0.7, H * 0.6, W * 0.26, '#64E1FF', '#0072FF');
  blob(c, W * 0.3, H * 0.7, W * 0.24, '#9BFF6B', '#1DB954');
  blob(c, W * 0.65, H * 0.3, W * 0.18, '#5BE7C4', '#008F7A');
  const s = fit(font, 'The new', W - 40);
  word(c, font, 'The new', 20, H * 0.16, s, '#FFFFFF');
  const w2 = font.measureText('way to').width * (s / 100);
  word(c, font, 'way to', W - 20 - w2, H * 0.56, s, '#FFFFFF');
  word(c, font, 'build', 20, H - 24, s, '#FFFFFF');
}
function posterIs(c: SkCanvas, W: number, H: number, font: SkFont) {
  fillBg(c, W, H, '#1C1C1F', '#121214');
  // A green plus, a pink ring and an orange flower.
  const plus = Skia.Paint();
  plus.setAntiAlias(true);
  plus.setStyle(PaintStyle.Stroke);
  plus.setStrokeWidth(16);
  plus.setStrokeCap(StrokeCap.Round);
  plus.setColor(Skia.Color('#7CFF4D'));
  c.drawLine(W * 0.8, H * 0.08, W * 0.8, H * 0.22, plus);
  c.drawLine(W * 0.73, H * 0.15, W * 0.87, H * 0.15, plus);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(13);
  ring.setShader(Skia.Shader.MakeSweepGradient(W * 0.52, H * 0.16, [Skia.Color('#FF5FA2'), Skia.Color('#FFD54D'), Skia.Color('#6BB8FF'), Skia.Color('#FF5FA2')], null, TileMode.Clamp));
  c.drawCircle(W * 0.52, H * 0.16, W * 0.09, ring);
  for (let i = 0; i < 4; i++) {
    const a = (i * Math.PI) / 2 + Math.PI / 4;
    blob(c, W * 0.66 + Math.cos(a) * 16, H * 0.27 + Math.sin(a) * 16, 15, '#FFB84D', '#FF7A00');
  }
  const pixel = Skia.Paint();
  pixel.setColor(Skia.Color('#3D6BFF'));
  for (let i = 0; i < 5; i++) for (let j = 0; j < 5; j++) if ((i + j * 2) % 3 !== 0) c.drawRect(Skia.XYWHRect(W * 0.1 + i * 12, H * 0.1 + j * 12, 10, 10), pixel);
  const s = fit(font, 'now', W - 40);
  const w1 = font.measureText('is').width * (s / 100);
  word(c, font, 'is', (W - w1) / 2, H * 0.68, s, '#FFFFFF');
  word(c, font, 'now', 20, H - 24, s, null, Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: W, y: 0 }, [Skia.Color('#5BE7FF'), Skia.Color('#B48CFF'), Skia.Color('#FFFFFF')], null, TileMode.Clamp));
}
function posterMake(c: SkCanvas, W: number, H: number, font: SkFont) {
  fillBg(c, W, H, '#3B2BFF', '#5E8BFF');
  const band = (pts: number[], color: string) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color(color));
    const path = Skia.Path.Make();
    path.moveTo(pts[0], pts[1]);
    for (let i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
    path.close();
    c.drawPath(path, p);
  };
  band([0, H * 0.15, W * 0.45, 0, W, H * 0.55, W, H * 0.85], '#FFC21A');
  band([0, H * 0.6, W * 0.5, H * 0.45, W * 0.4, H, 0, H], '#FF8AC4');
  const tube = (pts: number[], w: number, a: string, b: string) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(w);
    p.setStrokeCap(StrokeCap.Round);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: pts[1] - w }, { x: 0, y: pts[1] + w }, [Skia.Color(a), Skia.Color(b)], null, TileMode.Mirror));
    const path = Skia.Path.Make();
    path.moveTo(pts[0], pts[1]);
    path.cubicTo(pts[2], pts[3], pts[4], pts[5], pts[6], pts[7]);
    c.drawPath(path, p);
  };
  tube([W * 0.15, H * 0.38, W * 0.4, H * 0.1, W * 0.75, H * 0.2, W * 0.7, H * 0.45], 54, '#FF8A94', '#E81A3A');
  tube([W * 0.7, H * 0.45, W * 0.66, H * 0.65, W * 0.4, H * 0.6, W * 0.35, H * 0.72], 44, '#FFC8E2', '#F06AA8');
  blob(c, W * 0.2, H * 0.4, 40, '#8FB8FF', '#2F5BFF');
  word(c, font, 'Make it', 20, H - 28, fit(font, 'Make it', W - 40), '#FFFFFF');
}

const DRAWERS = [posterNow, posterNext, posterWay, posterIs, posterMake];

function useDrawnPosters(font: SkFont | null, W: number, H: number) {
  return useMemo(() => {
    if (!font) return [] as SkPicture[];
    return DRAWERS.map((draw) => {
      const rec = Skia.PictureRecorder();
      const c = rec.beginRecording(Skia.XYWHRect(0, 0, W, H));
      c.save();
      c.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, W, H), 18, 18), 1, true);
      draw(c, W, H, font);
      c.restore();
      return rec.finishRecordingAsPicture();
    });
  }, [font, W, H]);
}

// --- Animation ------------------------------------------------------------------------------

/** Fractional poster index `t` seconds into the loop (each change spans 0.65 s). */
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  return k + clamp01((u - CHANGE) / (STOP - CHANGE));
}
/** Slat i's progress for a change that is fraction f done. */
function slatAt(i: number, f: number) {
  'worklet';
  const span = SLAT_RUN + SLAT_DELAY * (SLATS - 1);
  return easeOutCubic(clamp01((f * span - SLAT_DELAY * i) / SLAT_RUN));
}

function Counter({ index }: { index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const i = index.get();
    return { transform: [{ translateY: -20 * (((i % N) + N) % N) }] };
  });
  return (
    <View style={styles.counterWindow}>
      <Animated.View style={style}>
        {[1, 2, 3, 4, 5, 1].map((n, k) => (
          <Text key={k} style={styles.counter}>{`0${n}`}</Text>
        ))}
      </Animated.View>
    </View>
  );
}

type Props = {
  brand?: string;
  action?: string;
  /** Changes poster by itself, every STOP seconds. */
  autoPlay?: boolean;
};

export function PosterMorphCarousel({ brand = 'Kiln 26', action = 'Get your ticket', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO });
  const font = useFont(ARCHIVO, 100);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const W = width - 40;
  const H = Math.round(W * 1.325);
  const x0 = 20;
  const y0 = height * 0.48 - H / 2;
  const posters = useDrawnPosters(font, W, H);

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const index = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const grabbed = useSharedValue(0);
  const from = useSharedValue(0);
  const snapTo = useSharedValue(0);
  const lastStep = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      index.set(since < SNAP ? from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP) : snapTo.get());
      if (since > SNAP + HOLD) {
        manual.set(false);
        origin.set(now - ((((snapTo.get() % N) + N) % N) * STOP));
      }
      return;
    }
    if (!autoPlay) return;
    index.set(indexAt((now - origin.get()) % LOOP));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (posters.length === N) {
      const i = index.get();
      const base = Math.floor(i);
      const f = i - base;
      const cur = posters[((base % N) + N) % N];
      const next = posters[(((base + 1) % N) + N) % N];
      const shadow = Skia.Paint();
      shadow.setAntiAlias(true);
      shadow.setColor(Skia.Color('rgba(0,0,0,0.5)'));
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 22, true));
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 10, y0 + 24, W - 20, H), 18, 18), shadow);
      canvas.save();
      canvas.translate(x0, y0);
      canvas.drawPicture(cur);
      if (f > 0) {
        const sw = W / SLATS;
        for (let s = 0; s < SLATS; s++) {
          const p = slatAt(s, f);
          if (p <= 0) continue;
          canvas.save();
          canvas.clipRect(Skia.XYWHRect(s * sw - 0.5, 0, sw + 1, H), 1, true);
          canvas.translate(0, (1 - p) * H);
          canvas.drawPicture(next);
          canvas.restore();
        }
      }
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, posters, x0, y0, W, H]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabbed.set(index.get());
      lastStep.set(Math.round(index.get()));
    })
    .onUpdate((e) => {
      const i = grabbed.get() - e.translationX / PER;
      index.set(i);
      if (Math.round(i) !== lastStep.get()) {
        lastStep.set(Math.round(i));
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      from.set(index.get());
      snapTo.set(Math.round(index.get()));
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.meta}>Poster wall</Text>
          </View>
          <Text style={[styles.caption, { top: y0 - 30 }]}>Five posters for five days of making</Text>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Conference posters. Swipe to change poster.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.under, { top: y0 + H + 14 }]} pointerEvents="none">
            <Text style={styles.caption2}>Lisbon · 12–16 May 2026</Text>
            <View style={styles.counterRow}>
              <Counter index={index} />
              <Text style={styles.counterTotal}> / 05</Text>
            </View>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#141416' },
  top: { position: 'absolute', left: 20, right: 20, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 18, color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.55)' },
  caption: { position: 'absolute', left: 20, right: 20, textAlign: 'center', fontSize: 13, fontWeight: '600', color: 'rgba(255,255,255,0.6)' },
  under: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  caption2: { fontSize: 13, fontWeight: '600', color: 'rgba(255,255,255,0.6)' },
  counterRow: { flexDirection: 'row', alignItems: 'center' },
  counterWindow: { height: 20, overflow: 'hidden' },
  counter: { height: 20, lineHeight: 20, fontFamily: BLACK, fontSize: 14, color: '#FFFFFF' },
  counterTotal: { fontFamily: BLACK, fontSize: 14, color: 'rgba(255,255,255,0.45)' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  button: { height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BLACK, fontSize: 15, color: '#141416' },
});
