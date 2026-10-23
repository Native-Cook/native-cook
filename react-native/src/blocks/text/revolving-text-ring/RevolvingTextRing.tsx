import { Canvas, Picture, Skia, type SkFont, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Revolving Text Ring — an open-air festival poster whose name runs around a turning 3D ring. A
 port of the SwiftUI block (RevolvingTextRing.swift); the numbers are the same:

 Two bands of capitals are wrapped around a vertical cylinder of radius R (39 % of the screen
 width, at most 170 pt), seen from 22° above with a 1000 pt focal length: a big band
 "OPEN AIR • OPEN AIR • " and, under it, a small tracked band with the dates and place. Each
 band's font size is chosen so that its text goes round exactly once. Every glyph is a flat tile
 tangent to the cylinder, drawn with the affine map of the projection at its centre, so the
 letters on the far side read mirrored through the open ring. Glyphs are drawn back to front;
 a glyph's opacity runs from 0.3 (facing away) to 1 (facing the viewer).

 autoPlay timeline (LOOP = 8 s):
   rotation   constant, one turn every 4 s (two per loop), the front moving left like a marquee.
   0.0 … 3.2  light: paper (#EEEDE8) with near-black ink (#0E0E10).
   3.2 … 4.0  colours invert (ease-in-out): dark paper, light ink, the whole screen together.
   4.0 … 7.2  dark.
   7.2 … 8.0  back to light; t = 8 is the same frame as t = 0 (two whole turns, light).

 Touch: dragging sideways turns the ring under the finger (1 pt = 1/R rad), with a selection
 haptic every 30°; on release it keeps the finger's speed and slows with a 0.5 s time constant.
 A tap inverts the colours (0.6 s). 2 s after the last touch the ring eases to its starting
 angle and the light colours over 1 s, and the loop starts again.
 Reduced motion: the ring stands still in the light colours; dragging still turns it (no glide),
 and a tap cross-fades the colours.
 */

const LOOP = 8;
const TURN = 4;
const TILT = (22 * Math.PI) / 180;
const FOCAL = 1000;
const GLIDE = 0.5;
const TICK = Math.PI / 6;
const SMALL_TRACKING = 0.32; // em
const CAP_HEIGHT = 0.688; // em, Archivo Black
/** The text box's centre sits this far above the baseline (ascent 0.878, descent 0.21 em), as in SwiftUI. */
const BOX_CENTER = 0.334;
const MEASURE = 100; // the Skia font's size; tiles scale it to the band's size
const FONT_NAME = 'RevolvingTextRing-ArchivoBlack';
const FONT_FILE = require('./assets/ArchivoBlack-Regular.ttf');
/** Colours mix straight in sRGB as in the SwiftUI version (Reanimated's default mixes with a 2.2 gamma). */
const SRGB_MIX = { gamma: 1 };
const PAPER = '#EEEDE8';
const NIGHT = '#0E0E10';
const PAPER_RGB = [0xee / 255, 0xed / 255, 0xe8 / 255];
const NIGHT_RGB = [0x0e / 255, 0x0e / 255, 0x10 / 255];

type Glyph = { char: string; big: boolean; phi: number; y: number };
type Ring = { radius: number; bigSize: number; smallSize: number; glyphs: Glyph[]; advance: Record<string, number> };

/** The ring's bands: every glyph's angle on the cylinder and its height. */
function buildRing(big: string, small: string, width: number, font: SkFont): Ring {
  const advance: Record<string, number> = {};
  const adv = (ch: string) => {
    if (advance[ch] === undefined) advance[ch] = font.getGlyphWidths(font.getGlyphIDs(ch))[0] / MEASURE;
    return advance[ch];
  };
  const radius = Math.min(width * 0.39, 170);
  const circumference = 2 * Math.PI * radius;
  const bigChars = Array.from(big);
  const smallChars = Array.from(small);
  // Each band's size makes its text go round exactly once.
  const bigSize = circumference / bigChars.reduce((s, ch) => s + adv(ch), 0);
  const smallSize = circumference / (smallChars.reduce((s, ch) => s + adv(ch), 0) + SMALL_TRACKING * smallChars.length);
  const hBig = CAP_HEIGHT * bigSize;
  const hSmall = CAP_HEIGHT * smallSize;
  const gap = 0.45 * hSmall;
  const yBig = -(hSmall + gap) / 2;
  const ySmall = (hBig + gap) / 2;
  const glyphs: Glyph[] = [];
  let s = 0;
  for (const ch of bigChars) {
    const w = adv(ch) * bigSize;
    if (ch !== ' ') glyphs.push({ char: ch, big: true, phi: (s + w / 2) / radius, y: yBig });
    s += w;
  }
  s = 0;
  for (const ch of smallChars) {
    const w = adv(ch) * smallSize;
    if (ch !== ' ') glyphs.push({ char: ch, big: false, phi: (s + w / 2) / radius, y: ySmall });
    s += w + SMALL_TRACKING * smallSize;
  }
  return { radius, bigSize, smallSize, glyphs, advance };
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/** Projects a point on the ring (x right, y down, z towards the viewer) seen from above: [x, y, depth]. */
function project(x: number, y: number, z: number, cx: number, cy: number): [number, number, number] {
  'worklet';
  const y2 = y * Math.cos(TILT) + z * Math.sin(TILT);
  const z2 = -y * Math.sin(TILT) + z * Math.cos(TILT);
  const p = FOCAL / (FOCAL - z2);
  return [cx + x * p, cy + y2 * p, z2];
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  theta: SharedValue<number>;
  releaseAt: SharedValue<number>;
  releaseSpeed: SharedValue<number>;
  darkFrom: SharedValue<number>;
  darkTo: SharedValue<number>;
  darkSince: SharedValue<number>;
  /** The ease back to the start: [when, from, to, dark], or [] when not returning. */
  returning: SharedValue<number[]>;
  resumeAt: SharedValue<number>;
  dragStart: SharedValue<number>;
  lastTick: SharedValue<number>;
};

function angleAt(m: Motion, at: number, reduced: boolean) {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start && !reduced) return (2 * Math.PI * ((at - start) % LOOP)) / TURN;
  const r = m.returning.get();
  if (r.length === 4) {
    const u = easeInOut(Math.min(1, Math.max(0, (at - r[0]) / 1.0)));
    return r[1] + (r[2] - r[1]) * u;
  }
  const rel = m.releaseAt.get();
  if (rel >= 0 && !reduced) {
    const t = Math.max(0, at - rel);
    return m.theta.get() + m.releaseSpeed.get() * GLIDE * (1 - Math.exp(-t / GLIDE));
  }
  return m.theta.get();
}

function darkAt(m: Motion, at: number, reduced: boolean) {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start && !reduced) {
    const e = (at - start) % LOOP;
    if (e < 3.2) return 0;
    if (e < 4.0) return easeInOut((e - 3.2) / 0.8);
    if (e < 7.2) return 1;
    return 1 - easeInOut((e - 7.2) / 0.8);
  }
  const r = m.returning.get();
  if (r.length === 4) {
    const u = easeInOut(Math.min(1, Math.max(0, (at - r[0]) / 1.0)));
    return r[3] * (1 - u);
  }
  const u = easeInOut(Math.min(1, Math.max(0, (at - m.darkSince.get()) / 0.6)));
  return m.darkFrom.get() + (m.darkTo.get() - m.darkFrom.get()) * u;
}

function selection() {
  Haptics.selectionAsync().catch(() => {});
}

function tapFeedback() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  bigBand?: string;
  smallBand?: string;
  header?: string;
  dates?: string;
  title?: string;
  subtitle?: string;
  action?: string;
  autoPlay?: boolean;
  onAction?: () => void;
};

export function RevolvingTextRing({
  bigBand = 'OPEN AIR • OPEN AIR • ',
  smallBand = 'FRIDAY TO SUNDAY · RIVERSIDE PARK · GATES AT 4 PM · ',
  header = 'OPEN AIR 26',
  dates = 'JUN 26–28',
  title = 'Three days by the river.',
  subtitle = 'Forty artists, two stages and one long weekend outdoors.',
  action = 'Get passes',
  autoPlay = false,
  onAction,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const font = useFont(FONT_FILE, MEASURE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [lightBars, setLightBars] = useState(true);
  const ring = useMemo(() => (font && size.width > 0 ? buildRing(bigBand, smallBand, size.width, font) : null), [font, size.width, bigBand, smallBand]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    theta: useSharedValue(0),
    releaseAt: useSharedValue(-1),
    releaseSpeed: useSharedValue(0),
    darkFrom: useSharedValue(0),
    darkTo: useSharedValue(0),
    darkSince: useSharedValue(-10),
    returning: useSharedValue<number[]>([]),
    resumeAt: useSharedValue(-1),
    dragStart: useSharedValue(NaN),
    lastTick: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    // 2 s after the last touch: ease back to the start for 1 s, then the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      const a = angleAt(m, now, reduced);
      m.returning.set([now, a, Math.round(a / (2 * Math.PI)) * 2 * Math.PI, darkAt(m, now, reduced)]);
      m.autoStart.set(now + 1.0);
    }
  });

  const dark = useDerivedValue(() => darkAt(m, m.clock.get(), reduced));
  // The status bar's icons follow the colours: dark on paper, light on night.
  useAnimatedReaction(
    () => dark.get() < 0.5,
    (light, previous) => {
      if (light !== previous) scheduleOnRN(setLightBars, light);
    },
  );

  const radius = ring?.radius ?? 1;
  const gesture = useMemo(() => {
    const takeOver = (now: number) => {
      'worklet';
      m.resumeAt.set(-1);
      const a = angleAt(m, now, reduced);
      const d = darkAt(m, now, reduced);
      m.theta.set(a);
      m.releaseAt.set(-1);
      m.returning.set([]);
      m.autoStart.set(-1);
      m.darkFrom.set(d);
      m.darkTo.set(d);
      m.darkSince.set(now);
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin(() => {
        takeOver(m.clock.get());
        m.dragStart.set(m.theta.get());
        m.lastTick.set(Math.floor(m.theta.get() / TICK));
      })
      .onUpdate((e) => {
        if (Math.abs(e.translationX) <= 2 && Math.abs(e.translationY) <= 6) return;
        m.theta.set(m.dragStart.get() - e.translationX / radius);
        const t = Math.floor(m.theta.get() / TICK);
        if (t !== m.lastTick.get()) {
          m.lastTick.set(t);
          scheduleOnRN(selection);
        }
      })
      .onFinalize((e) => {
        const now = m.clock.get();
        const moved = Math.hypot(e.translationX, e.translationY) > 6;
        if (moved) {
          if (!reduced) {
            m.releaseSpeed.set(Math.max(-12, Math.min(12, -e.velocityX / radius)));
            m.releaseAt.set(now);
          }
        } else {
          const d = darkAt(m, now, reduced);
          m.darkFrom.set(d);
          m.darkTo.set(d > 0.5 ? 0 : 1);
          m.darkSince.set(now);
          scheduleOnRN(tapFeedback);
        }
        if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [radius, reduced, autoPlay]);

  const centerY = size.height * 0.43;
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!ring || !font) return recorder.finishRecordingAsPicture();
    const now = m.clock.get();
    const angle = angleAt(m, now, reduced);
    const d = dark.get();
    const cx = size.width / 2;
    const n = ring.glyphs.length;
    const tiles: { i: number; depth: number; m: number[]; opacity: number }[] = [];
    for (let i = 0; i < n; i++) {
      const g = ring.glyphs[i];
      const phi = g.phi - angle;
      const sp = Math.sin(phi);
      const cp = Math.cos(phi);
      const x = ring.radius * sp;
      const z = ring.radius * cp;
      const p = project(x, g.y, z, cx, centerY);
      // The tile's axes: along the ring (tangent) and down the cylinder, by finite differences.
      const pt = project(x + cp, g.y, z - sp, cx, centerY);
      const pu = project(x, g.y + 1, z, cx, centerY);
      const facing = cp * Math.cos(TILT);
      tiles.push({
        i,
        depth: p[2],
        m: [pt[0] - p[0], pu[0] - p[0], p[0], pt[1] - p[1], pu[1] - p[1], p[1], 0, 0, 1],
        opacity: 0.3 + 0.7 * smoothstep(-0.15, 0.35, facing),
      });
    }
    tiles.sort((a, b) => a.depth - b.depth);
    const paint = Skia.Paint();
    const r = NIGHT_RGB[0] + (PAPER_RGB[0] - NIGHT_RGB[0]) * d;
    const gg = NIGHT_RGB[1] + (PAPER_RGB[1] - NIGHT_RGB[1]) * d;
    const b = NIGHT_RGB[2] + (PAPER_RGB[2] - NIGHT_RGB[2]) * d;
    for (const t of tiles) {
      const g = ring.glyphs[t.i];
      const fs = g.big ? ring.bigSize : ring.smallSize;
      const w = (ring.advance[g.char] ?? 0.6) * fs;
      paint.setColor(Float32Array.of(r, gg, b, t.opacity));
      canvas.save();
      canvas.concat(Skia.Matrix(t.m));
      canvas.translate(-w / 2, BOX_CENTER * fs);
      canvas.scale(fs / MEASURE, fs / MEASURE);
      canvas.drawText(g.char, 0, 0, paint, font);
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  const bgStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(dark.get(), [0, 1], [PAPER, NIGHT], 'RGB', SRGB_MIX) }));
  const inkText = useAnimatedStyle(() => ({ color: interpolateColor(dark.get(), [0, 1], [NIGHT, PAPER], 'RGB', SRGB_MIX) }));
  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(dark.get(), [0, 1], [NIGHT, PAPER], 'RGB', SRGB_MIX) }));
  const buttonText = useAnimatedStyle(() => ({ color: interpolateColor(dark.get(), [0, 1], [PAPER, NIGHT], 'RGB', SRGB_MIX) }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const bottom = Math.max(insets.bottom, 20);

  return (
    <Animated.View style={[styles.screen, bgStyle]} onLayout={onLayout}>
      <StatusBar style={lightBars ? 'dark' : 'light'} />
      {fontsLoaded && ring ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${bigBand} ${smallBand}`}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View style={[styles.header, { top: insets.top + 16 }]} pointerEvents="none">
            <Animated.Text style={[styles.headerText, inkText]} numberOfLines={1}>
              {header}
            </Animated.Text>
            <Animated.Text style={[styles.headerText, inkText]} numberOfLines={1}>
              {dates}
            </Animated.Text>
          </View>

          <View style={[styles.copy, { top: size.height - bottom - 16 - 56 - 32 - 112, width: size.width - 48 }]} pointerEvents="none">
            <Animated.Text style={[styles.title, inkText]} numberOfLines={2} adjustsFontSizeToFit minimumFontScale={0.8}>
              {title}
            </Animated.Text>
            {/* Lines break as SwiftUI breaks them, keeping a lone word off the last line. */}
            <Animated.Text style={[styles.subtitle, inkText]} lineBreakStrategyIOS="standard">
              {subtitle}
            </Animated.Text>
          </View>

          <Pressable
            onPress={onAction}
            accessibilityRole="button"
            style={[styles.buttonBox, { top: size.height - bottom - 16 - 56, width: size.width - 48 }]}
          >
            <Animated.View style={[styles.button, buttonStyle]}>
              <Animated.Text style={[styles.buttonText, buttonText]}>{action}</Animated.Text>
            </Animated.View>
          </Pressable>
        </>
      ) : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  header: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between' },
  headerText: { fontFamily: FONT_NAME, fontSize: 13, letterSpacing: 1.5 },
  copy: { position: 'absolute', left: 24, gap: 10 },
  title: { fontFamily: FONT_NAME, fontSize: 26 },
  subtitle: { fontSize: 16, lineHeight: 22, opacity: 0.6 },
  buttonBox: { position: 'absolute', left: 24, height: 56 },
  button: { flex: 1, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontFamily: FONT_NAME, fontSize: 16, letterSpacing: 0.5 },
});
