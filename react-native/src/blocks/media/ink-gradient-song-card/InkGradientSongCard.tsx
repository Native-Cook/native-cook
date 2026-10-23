import { BlurStyle, Canvas, type SkCanvas, PaintStyle, Picture, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { INK_EFFECT } from './ink-shader';

/*
 Ink Gradient Song Card — a "make your first song" sheet whose background is ink being stirred in
 water, over a dimmed music app. A port of the SwiftUI block (InkGradientSongCard.swift); the
 numbers are the same.

 Look: behind, a dark music app ("Explore" in Instrument Serif 32 pt, a search button, a banner,
 four release cards drawn in code, a tab bar with a glowing red centre tab), dimmed by black 60 %.
 In front, a card (84 % of the width up to 340 pt, 62 % of the height up to 540 pt, corner 26,
 centred at 53 % of the height) filled with a Skia RuntimeEffect (ink-shader.ts, the same code as
 InkGradientSongCard.metal): three clouds — burgundy #6B1020, signal red #E0251F, lilac #C98BE8 —
 from two warps of four-octave value noise, with thresholds nudged per 0.5 pt grain cell so every
 edge is sprayed and stippled, and a film grain on top. On it: a round close button, "Make your /
 first song" (Instrument Serif 40 pt, white, two 42 pt lines from 46 pt down), a two-line subtitle
 (14 pt, white 90 %; both texts carry a soft black shadow), three tilted prints (100 × 126, a 3 pt
 white border, corner 12, art drawn in code: a studio microphone under a lamp, a record on teal, a
 sun over water) and a pale pink footer (#F8D8DE, 60 pt) reading "Let's make music".

 Motion (LOOP = 8 s), all of it periodic in the loop:
   • the ink drifts on circles of the loop's phase (once and twice a loop);
   • it is stirred by a point travelling a figure eight across the card (x: 28 % of the width,
     cos(2πt/8); y: 20 % of the height, sin(4πt/8), around the middle), swirling the ink around it
     by up to 1.1 rad, fading over about a third of the card;
   • the red and lilac thresholds breathe once a loop (±0.05, ±0.04), so the colours trade places;
   • each print floats ±3 pt and ±1.2° on its own phase.
 Touch: drag on the card to stir the ink yourself: the stirring point follows the finger (eased in
 over 0.25 s) and swirls 2.4 rad, and the prints lean towards the finger by depth (0.03, 0.05, 0.07
 of its distance from the card's centre). 2 s after release the stir eases back to its figure eight
 over 0.8 s. The footer button dips to 97 % while pressed. Light haptic when a stir starts and on
 the button.
 Reduced motion: the ink, the stir and the prints are still; a drag does not stir.
 */

const LOOP = 8;
const FONT_NAME = 'InkGradientSongCard-InstrumentSerif-Regular';
const FONT_FILE = require('./assets/InstrumentSerif-Regular.ttf');
const APP_BACKGROUND = '#141114';

/** Back, left, right: offset from the stack's centre, tilt, float phase, depth for the lean. */
const PRINT_SPOTS = [
  { dx: 6, dy: -26, rot: 4, phase: 0, depth: 0.03 },
  { dx: -64, dy: 10, rot: -12, phase: 2.1, depth: 0.05 },
  { dx: 60, dy: 20, rot: 10, phase: 4.2, depth: 0.07 },
];

const RELEASES = [
  { title: 'Neon Harbor', artist: 'Ivy Lane', from: '#FF8A3D', to: '#7B2CBF' },
  { title: 'Slow Bloom', artist: 'The Quiet Hours', from: '#2EC4B6', to: '#1B4DB1' },
  { title: 'Paper Moon', artist: 'Ada Rook', from: '#F15BB5', to: '#3A0CA3' },
  { title: 'Low Tide', artist: 'Marlow', from: '#FEE440', to: '#F3722C' },
];

function easeOut(x: number) {
  'worklet';
  const t = Math.min(Math.max(x, 0), 1);
  return 1 - Math.pow(1 - t, 3);
}

function easeInOut(x: number) {
  'worklet';
  const t = Math.min(Math.max(x, 0), 1);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function tap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Stirring = { x: number; y: number; strength: number; finger: number };

export function InkGradientSongCard(_props: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const [size, setSize] = useState({ width: 0, height: 0 });
  const w = size.width;
  const h = size.height;
  const cw = Math.min(w * 0.84, 340);
  const ch = Math.min(h * 0.62, 540);
  const cx0 = (w - cw) / 2;
  const cy0 = h * 0.53 - ch / 2;

  const now = useSharedValue(-1);
  const appear = useSharedValue(-1);
  /** A finger stirring the ink: [x, y (card points), when], or []; the release: [x, y, when, amount], or []. */
  const stir = useSharedValue<number[]>([]);
  const released = useSharedValue<number[]>([]);
  const buttonDown = useSharedValue(0);

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    if (appear.get() < 0) appear.set(t);
    now.set(t);
  });

  /** Loop time in seconds (0 under reduced motion). */
  const loopTime = useDerivedValue(() => {
    if (reduced) return 0;
    const c = now.get() - appear.get();
    return c - Math.floor(c / LOOP) * LOOP;
  });

  const stirring = useDerivedValue((): Stirring => {
    const t = loopTime.get();
    const a = (2 * Math.PI * t) / LOOP;
    // The figure eight the ink is stirred along when no finger is on it.
    const ax = cw * (0.5 + 0.28 * Math.cos(a));
    const ay = ch * (0.45 + 0.2 * Math.sin(2 * a));
    const at = now.get();
    let weight = 0;
    let px = ax;
    let py = ay;
    const s = stir.get();
    const r = released.get();
    if (s.length === 3) {
      weight = easeOut((at - s[2]) / 0.25);
      px = s[0];
      py = s[1];
    } else if (r.length === 4) {
      // Held where the finger left for 2 s, then back to the figure eight over 0.8 s.
      weight = r[3] * (1 - easeInOut((at - r[2] - 2) / 0.8));
      px = r[0];
      py = r[1];
    }
    return { x: ax + (px - ax) * weight, y: ay + (py - ay) * weight, strength: 1.1 + 1.3 * weight, finger: weight };
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          if (e.y > ch - 60) {
            buttonDown.set(1);
            scheduleOnRN(tap);
            return;
          }
          if (reduced) return;
          stir.set([e.x, e.y, now.get()]);
          released.set([]);
          scheduleOnRN(tap);
        })
        .onUpdate((e) => {
          const s = stir.get();
          if (s.length === 3) stir.set([e.x, e.y, s[2]]);
        })
        .onFinalize((e) => {
          buttonDown.set(0);
          const s = stir.get();
          if (s.length !== 3) return;
          const at = now.get();
          released.set([e.x, e.y, at, easeOut((at - s[2]) / 0.25)]);
          stir.set([]);
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, ch],
  );

  const app = useMemo(() => (w > 0 ? drawApp(w, h, insets.top, insets.bottom) : null), [w, h, insets.top, insets.bottom]);
  const cardShadow = useMemo(() => (w > 0 ? drawCardShadow(w, h, cx0, cy0, cw, ch) : null), [w, h, cx0, cy0, cw, ch]);

  const ink = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, cw, ch));
    if (cw > 0 && INK_EFFECT) {
      const st = stirring.get();
      const tau = (2 * Math.PI * loopTime.get()) / LOOP;
      const paint = Skia.Paint();
      paint.setShader(INK_EFFECT.makeShader([cw, ch, 0.5, tau, st.x, st.y, st.strength]));
      canvas.drawRect(Skia.XYWHRect(0, 0, cw, ch), paint);
    }
    return recorder.finishRecordingAsPicture();
  });

  const buttonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: withTiming(buttonDown.get() === 1 ? 0.97 : 1, { duration: 120 }) }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const colW = (w - 52) / 2;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {fontsLoaded && app && cardShadow ? (
        <>
          {/* The app behind, dimmed, with the card's shadow. */}
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={app} />
          </Canvas>
          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            <Text style={[styles.explore, { top: insets.top + 8 }]}>Explore</Text>
            <View style={[styles.banner, { top: insets.top + 64, width: w - 72 }]}>
              <Text style={styles.bannerText} numberOfLines={1}>
                New: remix any track with your voice
              </Text>
            </View>
            {RELEASES.map((r, i) => (
              <View key={r.title} style={{ position: 'absolute', left: 20 + (i % 2) * (colW + 12), top: insets.top + 128 + Math.floor(i / 2) * 292 + 232 }}>
                <Text style={styles.releaseTitle} numberOfLines={1}>
                  {r.title}
                </Text>
                <Text style={styles.releaseArtist} numberOfLines={1}>
                  {r.artist}
                </Text>
              </View>
            ))}
            <View style={styles.dim} />
          </View>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={cardShadow} />
          </Canvas>

          <GestureDetector gesture={gesture}>
            <View style={[styles.card, { left: cx0, top: cy0, width: cw, height: ch }]}>
              <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
                <Picture picture={ink} />
              </Canvas>
              <View style={[styles.close, { left: cw - 45 }]} accessible accessibilityRole="button" accessibilityLabel="Close">
                <View style={[styles.closeBar, { transform: [{ rotate: '45deg' }] }]} />
                <View style={[styles.closeBar, { transform: [{ rotate: '-45deg' }] }]} />
              </View>
              <View style={styles.headline} pointerEvents="none">
                <Text style={styles.headlineLine}>Make your</Text>
                <Text style={styles.headlineLine}>first song</Text>
              </View>
              <View style={[styles.subtitleBox, { width: cw - 64 }]} pointerEvents="none">
                <Text style={styles.subtitle} numberOfLines={2}>
                  Hum a tune, describe a mood, or add a photo to start a song.
                </Text>
              </View>
              {PRINT_SPOTS.map((spot, i) => (
                <Print key={i} kind={i} spot={spot} card={{ w: cw, h: ch }} loopTime={loopTime} stirring={stirring} reduced={reduced} />
              ))}
              <Animated.View style={[styles.footer, { top: ch - 60, width: cw }, buttonStyle]} accessible accessibilityRole="button">
                <Text style={styles.footerText}>{"Let's make music"}</Text>
              </Animated.View>
            </View>
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

/** A print in a 130 × 170 view (the print itself 100 × 126 at 15, 22), so its centre is the view's and its shadow fits. */
function Print({
  kind,
  spot,
  card,
  loopTime,
  stirring,
  reduced,
}: {
  kind: number;
  spot: (typeof PRINT_SPOTS)[number];
  card: { w: number; h: number };
  loopTime: SharedValue<number>;
  stirring: SharedValue<Stirring>;
  reduced: boolean;
}) {
  const picture = useMemo(() => drawPrint(kind), [kind]);
  const style = useAnimatedStyle(() => {
    const t = loopTime.get();
    const float = reduced ? 0 : Math.sin((2 * Math.PI * t) / LOOP + spot.phase);
    const st = stirring.get();
    const leanX = (st.x - card.w / 2) * spot.depth * st.finger;
    const leanY = (st.y - card.h / 2) * spot.depth * st.finger;
    return {
      transform: [{ translateX: leanX }, { translateY: 3 * float + leanY }, { rotate: `${spot.rot + 1.2 * float + leanX * 0.15}deg` }],
    };
  });
  const cx = card.w / 2 + spot.dx;
  const cy = card.h - 60 - 128 + spot.dy;
  return (
    <Animated.View style={[styles.print, { left: cx - 65, top: cy - 85 }, style]} pointerEvents="none">
      <Canvas style={StyleSheet.absoluteFill}>
        <Picture picture={picture} />
      </Canvas>
    </Animated.View>
  );
}

function linear(canvas: SkCanvas, x: number, y: number, w: number, h: number, from: string, to: string, x1: number, y1: number, x2: number, y2: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x1, y: y1 }, { x: x2, y: y2 }, [Skia.Color(from), Skia.Color(to)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(x, y, w, h), p);
}

function drawPrint(kind: number) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 130, 170));
  canvas.translate(15, 22);
  // A soft shadow (SwiftUI's radius 10 is about a Gaussian sigma of 5), then the white border.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0, 0, 0, 0.3));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 6, 100, 126), 12, 12), shadow);
  const white = Skia.Paint();
  white.setAntiAlias(true);
  white.setColor(Skia.Color('#FFFFFF'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, 100, 126), 12, 12), white);
  const w = 94;
  const h = 120;
  canvas.save();
  canvas.translate(3, 3);
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 9, 9), 1, true);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  if (kind === 0) {
    // A studio microphone under a warm lamp.
    linear(canvas, 0, 0, w, h, '#F4C27A', '#B4562E', 0, 0, 0, h);
    const lamp = Skia.Paint();
    lamp.setShader(
      Skia.Shader.MakeRadialGradient({ x: 0.72 * w, y: 0.24 * h }, 0.4 * w, [Float32Array.of(1, 1, 1, 0.6), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), lamp);
    p.setColor(Skia.Color('#2A1A14'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.38 * w, 0.28 * h, 0.18 * w, 0.3 * h), 0.09 * w, 0.09 * w), p);
    canvas.drawOval(Skia.XYWHRect(0.3 * w, 0.87 * h, 0.34 * w, 0.07 * h), p);
    const stand = p.copy();
    stand.setStyle(PaintStyle.Stroke);
    stand.setStrokeWidth(3);
    canvas.drawLine(0.47 * w, 0.58 * h, 0.47 * w, 0.9 * h, stand);
  } else if (kind === 1) {
    // A record on teal.
    linear(canvas, 0, 0, w, h, '#2E6F73', '#1D3F4A', 0, 0, w, h);
    const cx = 0.5 * w;
    const cy = 0.55 * h;
    p.setColor(Skia.Color('#141414'));
    canvas.drawCircle(cx, cy, 0.4 * w, p);
    const groove = Skia.Paint();
    groove.setAntiAlias(true);
    groove.setStyle(PaintStyle.Stroke);
    groove.setStrokeWidth(1);
    groove.setColor(Float32Array.of(1, 1, 1, 0.1));
    for (const k of [0.33, 0.27, 0.21]) canvas.drawCircle(cx, cy, k * w, groove);
    p.setColor(Skia.Color('#E0251F'));
    canvas.drawCircle(cx, cy, 0.13 * w, p);
    p.setColor(Skia.Color('#1D3F4A'));
    canvas.drawCircle(cx, cy, 0.025 * w, p);
  } else {
    // A sun over water.
    linear(canvas, 0, 0, w, h, '#FF9A8B', '#6B2C5C', 0, 0, 0, 0.64 * h);
    p.setColor(Skia.Color('#FFE1A8'));
    canvas.drawCircle(0.5 * w, 0.34 * h + 0.2 * w, 0.2 * w, p);
    p.setColor(Skia.Color('#2A0F22'));
    canvas.drawRect(Skia.XYWHRect(0, 0.64 * h, w, 0.36 * h), p);
    p.setColor(Float32Array.of(1, 0.882, 0.659, 0.7));
    [0.7, 0.77, 0.84].forEach((y, k) => {
      const half = (0.16 - 0.04 * k) * w;
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.5 * w - half, y * h, 2 * half, 2), 1, 1), p);
    });
  }
  canvas.restore();
  return recorder.finishRecordingAsPicture();
}

/** The card's shadow over the dimmed app (SwiftUI's radius 30 is about a Gaussian sigma of 15). */
function drawCardShadow(w: number, h: number, x: number, y: number, cw: number, ch: number) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(0, 0, 0, 0.45));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 15, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 16, cw, ch), 26, 26), p);
  return recorder.finishRecordingAsPicture();
}

/** The music app behind the sheet; it is dimmed and never moves. */
function drawApp(w: number, h: number, top: number, bottom: number) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(APP_BACKGROUND));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), p);
  // Search button.
  p.setColor(Float32Array.of(1, 1, 1, 0.08));
  canvas.drawCircle(w - 40, top + 28, 18, p);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(1.8);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setColor(Float32Array.of(1, 1, 1, 0.85));
  canvas.drawCircle(w - 41.5, top + 25.5, 5.5, stroke);
  canvas.drawLine(w - 37.5, top + 30.5, w - 33, top + 35, stroke);
  // Banner.
  p.setColor(Skia.Color('#2A2328'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(20, top + 64, w - 40, 48), 14, 14), p);
  // Releases.
  const colW = (w - 52) / 2;
  RELEASES.forEach((r, i) => {
    const x = 20 + (i % 2) * (colW + 12);
    const y = top + 128 + Math.floor(i / 2) * 292;
    const g = Skia.Paint();
    g.setAntiAlias(true);
    g.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x: x + colW, y: y + 220 }, [Skia.Color(r.from), Skia.Color(r.to)], null, TileMode.Clamp));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, colW, 220), 18, 18), g);
  });
  // Tab bar with a glowing red centre tab.
  const barY = h - Math.max(bottom, 12) - 56;
  p.setColor(Skia.Color('#0E0C0E'));
  canvas.drawRect(Skia.XYWHRect(0, barY, w, h - barY), p);
  p.setColor(Float32Array.of(1, 1, 1, 0.35));
  for (let k = 0; k < 5; k++) {
    if (k === 2) continue;
    const x = w * (0.1 + 0.2 * k);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 9, barY + 19, 18, 18), 5, 5), p);
  }
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Float32Array.of(0.878, 0.145, 0.122, 0.8));
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  canvas.drawCircle(w / 2, barY + 28, 26, glow);
  p.setColor(Skia.Color('#E0251F'));
  canvas.drawCircle(w / 2, barY + 28, 22, p);
  return recorder.finishRecordingAsPicture();
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: APP_BACKGROUND },
  explore: { position: 'absolute', left: 20, height: 40, lineHeight: 40, fontFamily: FONT_NAME, fontSize: 32, color: '#FFFFFF' },
  banner: { position: 'absolute', left: 36, height: 48, justifyContent: 'center' },
  bannerText: { fontSize: 14, fontWeight: '500', color: 'rgba(255,255,255,0.8)' },
  releaseTitle: { fontSize: 15, fontWeight: '600', color: '#FFFFFF' },
  releaseArtist: { fontSize: 13, color: 'rgba(255,255,255,0.55)' },
  dim: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.6)' },
  card: {
    position: 'absolute',
    borderRadius: 26,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  close: {
    position: 'absolute',
    top: 15,
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBar: { position: 'absolute', width: 14.1, height: 1.8, borderRadius: 0.9, backgroundColor: '#FFFFFF' },
  // 46 pt in SwiftUI; a 42 pt lineHeight sets Instrument Serif 5 pt higher than SwiftUI's 42 pt frame.
  headline: { position: 'absolute', top: 51, left: 0, right: 0, alignItems: 'center' },
  headlineLine: {
    height: 42,
    lineHeight: 42,
    fontFamily: FONT_NAME,
    fontSize: 40,
    color: '#FFFFFF',
    textShadowColor: 'rgba(0,0,0,0.25)',
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 8,
  },
  subtitleBox: { position: 'absolute', left: 32, top: 142, height: 40, justifyContent: 'center' },
  subtitle: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.9)',
    textAlign: 'center',
    textShadowColor: 'rgba(0,0,0,0.25)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
  print: { position: 'absolute', width: 130, height: 170 },
  footer: { position: 'absolute', left: 0, height: 60, backgroundColor: '#F8D8DE', alignItems: 'center', justifyContent: 'center' },
  footerText: { fontSize: 15, fontWeight: '600', color: '#3A1A22' },
});
