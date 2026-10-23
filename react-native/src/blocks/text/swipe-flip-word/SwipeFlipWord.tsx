import { Canvas, Group, Paint, RuntimeShader, Skia, Text as SkiaText, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Swipe Flip Word — a gesture lesson whose big word "swipe" rolls over like a twisted ribbon. A port
 of the SwiftUI block (SwipeFlipWord.swift); the numbers are the same.

 The word (Archivo Expanded Black, 80 % of the screen's width, hot pink #FF2E6E on violet #430090)
 is printed on all four faces of a square prism lying along it, its half side R = half the word's
 glyph height + 0.04 em, its axis R behind the screen, seen through a camera F = 320 pt in front.
 Every column of the word turns about that axis by its own angle (a Skia runtime shader used as an
 image filter, the same code as the Metal shader): faces that roll up go back and shrink, the next
 face comes up from below, and faces turned from the viewer are shaded down to 55 %. Every face shows
 the same word, so after a quarter turn the word is back at rest.

 A roll: column u (0 at the side the roll starts from, 1 at the other) turns by ±90° · ease(q), where
 q = clamp(a − u·b), and ease is easeInOutBack with overshoot k (a small wind-up and overshoot).
   autoPlay roll: a = T / 0.9 s, b = 0.35 / 0.9, k = 1.0 — each column turns in 0.9 s, the last
   one starting 0.35 s after the first, so a roll takes 1.25 s and the whole word twists at once.

 autoPlay timeline (LOOP = 4.8 s):
   0.00 … 0.60  rest.
   0.60 … 1.85  roll up, travelling left to right (a swipe to the right).
   1.85 … 3.00  rest.
   3.00 … 4.25  roll down, travelling right to left (a swipe to the left).
   4.25 … 4.80  rest; t = 4.8 is the same frame as t = 0.

 Touch: a horizontal drag scrubs a roll in its direction (right = up, left = down): P = |dx| / (55 %
 of the width), q = clamp(P·1.4 − u·0.4), k = 0; each whole roll passed gives a light haptic and the
 drag rolls on. On release the roll finishes (P past 0.5, or a fling over 500 pt/s in its direction)
 or falls back, over 0.4 s (easeOutCubic). A tap plays one roll to the right. 2 s after the last
 touch the loop starts again from rest.
 Reduced motion: the word stays still and the loop is off; a tap dims and restores it (0.5 s).
 */

const LOOP = 4.8;
const ROLLS: [number, number][] = [
  [0.6, 1],
  [3.0, -1],
];
const TURN = 0.9;
const STAGGER = 0.35;
const OVERSHOOT = 1.0;
const SCRUB_WIDTH = 0.4;
const FOCAL = 320;
const VIOLET = '#430090';
const PINK = '#FF2E6E';
const LILAC = '#C7A6FF';
const FONT_NAME = 'SwipeFlipWord-Archivo';
const FONT_FILE = require('./assets/ArchivoExpandedBlack.ttf');

const SHADER = Skia.RuntimeEffect.Make(`
uniform shader image;
uniform float4 geo;   // cx, cy, R, F (pixels)
uniform float4 word;  // left, width, direction, -
uniform float4 roll;  // a, b, sign, overshoot

float ease(float x, float c1) {
  float c2 = c1 * 1.525;
  if (x < 0.5) {
    float t = 2.0 * x;
    return t * t * ((c2 + 1.0) * t - c2) / 2.0;
  }
  float t = 2.0 * x - 2.0;
  return (t * t * ((c2 + 1.0) * t + c2) + 2.0) / 2.0;
}

float angle(float x) {
  float u = clamp((x - word.x) / word.y, 0.0, 1.0);
  if (word.z < 0.0) u = 1.0 - u;
  float q = clamp(roll.x - u * roll.y, 0.0, 1.0);
  return roll.z * 1.5707963 * ease(q, roll.w);
}

half4 main(float2 pos) {
  float cx = geo.x;
  float cy = geo.y;
  float R = geo.z;
  float F = geo.w;
  float d = pos.y - cy;
  half4 result = half4(0.0);
  float nearest = -1e9;
  for (int k = -1; k <= 2; k++) {
    float x = pos.x;
    float v = 0.0;
    float z = 0.0;
    float psi = 0.0;
    for (int i = 0; i < 3; i++) {
      psi = angle(x) - float(k) * 1.5707963;
      float s = sin(psi);
      float c = cos(psi);
      v = -(F * R * s + d * (F + R - R * c)) / (d * s + F * c);
      z = -R + R * c - v * s;
      x = cx + (pos.x - cx) * (F - z) / F;
    }
    float c = cos(psi);
    if (c > 0.001 && abs(v) <= R && z > nearest) {
      nearest = z;
      half4 color = image.eval(float2(x, cy - v));
      half shade = half(0.55 + 0.45 * c);
      result = half4(color.rgb * shade, color.a);
    }
  }
  return result;
}
`)!;

/** What the shader needs from the roll: [a, b, sign, overshoot, direction]. */
type Roll = [number, number, number, number, number];
const REST: Roll = [0, 1, 1, 0, 1];

function autoRoll(elapsed: number, sign: number): Roll {
  'worklet';
  if (elapsed >= TURN + STAGGER) return REST;
  return [elapsed / TURN, STAGGER / TURN, sign, OVERSHOOT, sign];
}

function scrubRoll(sign: number, progress: number): Roll {
  'worklet';
  const p = progress - Math.floor(progress);
  if (p <= 0) return REST;
  return [p * (1 + SCRUB_WIDTH), SCRUB_WIDTH, sign, 0, sign];
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /** A drag: its direction and progress in rolls (progress -1 when none). */
  scrubSign: SharedValue<number>;
  scrubProgress: SharedValue<number>;
  /** A release: [from, to, sign, when], or [] when none. */
  settle: SharedValue<number[]>;
  tapRoll: SharedValue<number>;
  dim: SharedValue<number>;
  resumeAt: SharedValue<number>;
};

function rollAt(m: Motion, now: number, reduced: boolean): Roll {
  'worklet';
  if (reduced) return REST;
  if (m.scrubProgress.get() >= 0) return scrubRoll(m.scrubSign.get(), m.scrubProgress.get());
  const s = m.settle.get();
  if (s.length === 4) {
    const t = Math.min(1, Math.max(0, (now - s[3]) / 0.4));
    const e = 1 - Math.pow(1 - t, 3);
    return scrubRoll(s[2], s[0] + (s[1] - s[0]) * e);
  }
  const tap = m.tapRoll.get();
  if (tap >= 0) return autoRoll(now - tap, 1);
  const start = m.autoStart.get();
  if (start >= 0 && now >= start) {
    const e = (now - start) % LOOP;
    for (let i = ROLLS.length - 1; i >= 0; i--) if (e >= ROLLS[i][0]) return autoRoll(e - ROLLS[i][0], ROLLS[i][1]);
  }
  return REST;
}

function haptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  word?: string;
  lesson?: string;
  topic?: string;
  caption?: string;
  action?: string;
  autoPlay?: boolean;
  onAction?: () => void;
};

export function SwipeFlipWord({
  word = 'swipe',
  lesson = 'LESSON 01',
  topic = 'GESTURES',
  caption = 'One motion to clear a card.\nRight keeps it, left lets it go.',
  action = 'Continue',
  autoPlay = false,
  onAction,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const unitFont = useFont(FONT_FILE, 100);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const pd = PixelRatio.get();

  // The word at the size that makes it 80 % of the width, and the prism around it.
  const metrics = useMemo(() => {
    if (!unitFont || size.width <= 0) return null;
    // The advance character by character (no kerning, as in every stack).
    const advance = (f: typeof unitFont) => Array.from(word).reduce((s, ch) => s + (f.getGlyphWidths(f.getGlyphIDs(ch))[0] ?? 0), 0);
    const fontSize = (size.width * 0.8) / Math.max(advance(unitFont) / 100, 0.1);
    const font = Skia.Font(unitFont.getTypeface() ?? undefined, fontSize);
    // The glyphs' ink box, from their outlines as in Swift (measureText's box is taller).
    const bounds = Skia.Path.MakeFromText(word, 0, 0, font)?.computeTightBounds() ?? font.measureText(word);
    return {
      font,
      width: advance(font),
      // How far the glyphs' vertical centre sits above the baseline.
      glyphMid: -(bounds.y + bounds.height / 2),
      // The prism's half side: half the glyphs' height plus 0.04 em.
      radius: bounds.height / 2 + 0.04 * fontSize,
    };
  }, [unitFont, size.width, word]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    scrubSign: useSharedValue(1),
    scrubProgress: useSharedValue(-1),
    settle: useSharedValue<number[]>([]),
    tapRoll: useSharedValue(-1),
    dim: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    // 2 s after the last touch the loop starts again.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      m.settle.set([]);
      m.tapRoll.set(-1);
      m.autoStart.set(now);
    }
  });

  const width = size.width;
  const gesture = useMemo(() => {
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin(() => {
        m.resumeAt.set(-1);
        m.autoStart.set(-1);
      })
      .onUpdate((e) => {
        if (reduced) return;
        const dx = e.translationX;
        if (Math.abs(dx) <= 4 && m.scrubProgress.get() < 0) return;
        if (m.scrubProgress.get() < 0) {
          // A drag takes over from whatever plays; a roll in progress is simply replaced.
          m.tapRoll.set(-1);
          m.settle.set([]);
        }
        const sign = dx >= 0 ? 1 : -1;
        const progress = Math.abs(dx) / (width * 0.55);
        const old = m.scrubProgress.get();
        if (old >= 0 && m.scrubSign.get() === sign && Math.floor(progress) > Math.floor(old)) scheduleOnRN(haptic);
        m.scrubSign.set(sign);
        m.scrubProgress.set(progress);
      })
      .onFinalize((e) => {
        const now = m.clock.get();
        const progress = m.scrubProgress.get();
        if (progress >= 0) {
          const whole = Math.floor(progress);
          const fling = e.velocityX * m.scrubSign.get() > 500;
          const target = progress - whole > 0.5 || fling ? whole + 1 : whole;
          if (target > whole) scheduleOnRN(haptic);
          m.settle.set([progress, target, m.scrubSign.get(), now]);
          m.scrubProgress.set(-1);
        } else if (Math.abs(e.translationX) <= 4) {
          scheduleOnRN(haptic);
          if (reduced) {
            m.dim.set(now);
          } else {
            m.settle.set([]);
            m.tapRoll.set(now);
          }
        }
        if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [width, reduced, autoPlay]);

  const centerY = size.height * 0.42;
  const radius = metrics?.radius ?? 40;
  const layerHeight = radius * 4 + 40;
  const left = metrics ? (size.width - metrics.width) / 2 : 0;
  const wordWidth = metrics?.width ?? 1;

  // The shader works in pixels: the content is drawn at the pixel ratio inside the filtered layer.
  const uniforms = useDerivedValue(() => {
    const r = rollAt(m, m.clock.get(), reduced);
    return {
      geo: [(size.width / 2) * pd, (layerHeight / 2) * pd, radius * pd, FOCAL * pd],
      word: [left * pd, wordWidth * pd, r[4], 0],
      roll: [r[0], r[1], r[2], r[3]],
    };
  });

  const layerStyle = useAnimatedStyle(() => {
    const since = m.dim.get();
    if (since < 0) return { opacity: 1 };
    const t = (m.clock.get() - since) / 0.5;
    return {
      opacity: t >= 1 ? 1 : 1 - 0.6 * Math.sin(Math.PI * Math.max(0, t)),
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    if (w !== size.width || h !== size.height) setSize({ width: w, height: h });
  };

  const bottom = Math.max(insets.bottom, 20);
  const buttonTop = size.height - bottom - 16 - 56;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {metrics ? (
        <Animated.View
          style={[styles.layer, { top: centerY - layerHeight / 2, height: layerHeight }, layerStyle]}
          pointerEvents="none"
          accessible
          accessibilityLabel={word}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={[{ scale: 1 / pd }]}>
              <Group
                layer={
                  <Paint>
                    <RuntimeShader source={SHADER} uniforms={uniforms} />
                  </Paint>
                }
                transform={[{ scale: pd }]}
              >
                <SkiaText x={left} y={layerHeight / 2 + metrics.glyphMid} text={word} font={metrics.font} color={PINK} />
              </Group>
            </Group>
          </Canvas>
        </Animated.View>
      ) : null}

      {/* The whole screen takes the drag, under the chrome so the button keeps its own touches. */}
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} />
      </GestureDetector>

      {fontsLoaded && metrics ? (
        <>
          <View style={[styles.header, { top: insets.top + 16 }]} pointerEvents="none">
            <Text style={styles.headerText} numberOfLines={1}>
              {lesson}
            </Text>
            <Text style={styles.headerText} numberOfLines={1}>
              {topic}
            </Text>
          </View>

          <Text style={[styles.caption, { top: centerY + radius * 1.6 + 24, width: size.width - 48 }]} pointerEvents="none">
            {caption}
          </Text>

          <View style={[styles.dots, { top: buttonTop - 30 }]} pointerEvents="none">
            <View style={styles.dotActive} />
            <View style={styles.dot} />
            <View style={styles.dot} />
          </View>

          <Pressable onPress={onAction} accessibilityRole="button" style={[styles.button, { top: buttonTop, width: size.width - 48 }]}>
            <Text style={styles.buttonText}>{action}</Text>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: VIOLET },
  layer: { position: 'absolute', left: 0, right: 0 },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  headerText: {
    fontFamily: FONT_NAME,
    fontSize: 11,
    letterSpacing: 1.6,
    color: LILAC,
    opacity: 0.75,
  },
  caption: {
    position: 'absolute',
    left: 24,
    fontSize: 17,
    fontWeight: '500',
    // SwiftUI's lineSpacing(4) on 17 pt.
    lineHeight: 24,
    color: LILAC,
    textAlign: 'center',
  },
  dots: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
  },
  dotActive: { width: 20, height: 6, borderRadius: 3, backgroundColor: PINK },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: LILAC,
    opacity: 0.4,
  },
  button: {
    position: 'absolute',
    left: 24,
    height: 56,
    borderRadius: 28,
    backgroundColor: PINK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    fontFamily: FONT_NAME,
    fontSize: 15,
    letterSpacing: 0.6,
    color: VIOLET,
  },
});
