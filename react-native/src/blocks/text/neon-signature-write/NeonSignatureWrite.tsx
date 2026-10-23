import {
  BlurStyle,
  Canvas,
  type SkCanvas,
  type SkColor,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  createPicture,
  useFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type NeonWord, SAMPLE_WORDS, type WordShape, layoutWords } from './geometry';

/*
 Neon Signature Write: a script word on a dark wall that writes itself in neon.

 Timeline of the self-playing loop (LOOP = 9.6 s, three segments of 3.2 s):
 segment k opens with word k lit, the resting state.
   0.00–0.80  word k is lit; its glow breathes once (+10 %).
   0.80–1.20  word k switches off: it flickers twice, then the tube cools to black.
   1.20–1.30  dark.
   1.30–2.80  word k + 1 is written (the "write" clock τ runs 0 → 1.5 s). Three tubes trace the outline
              of every letter: mint first, rose 0.18 s later, the word's own colour 0.36 s later, each over
              1.1 s. Within a tube, a contour starts later the further right it sits (up to 50 % of the
              tube's run) and takes 50 % of it. The mint and rose heads burn white-hot and throw sparks.
   2.80–3.20  ignite (τ 1.5 → 1.9): the letters fill with the word's gradient after two flickers while the
              tubes fade; the glow overshoots by 45 % and settles.
 Segment 3 writes word 0 again, so the period ends exactly where it began.

 Touch: dragging across the sign rewrites the shown word with the pen under the finger (left edge of the
 word = nothing, right edge = every tube closed); letting go finishes it and lights it. A tap switches the
 sign off and writes the next word. 2 s after the last touch the sign goes back to the first word, lit,
 and the loop starts over. Reduced motion: the first word is shown lit and still; a tap cross-fades to
 the next one.
*/

/** The self-playing loop, in seconds. */
export const LOOP = 9.6;

const SEGMENT = 3.2;
const HOLD = 0.8;
const OFF = 0.4;
const DARK = 0.1;
const WRITE = 1.5;
const IGNITE = 0.4;
/** τ when the sign is fully lit. */
const LIT = WRITE + IGNITE;
/** The three tubes: when each starts on τ, and how long it runs. */
const LAGS = [0, 0.18, 0.36];
const RUN = 1.1;
const STAGGER = 0.5;
const SPAN = 0.5;
/** τ when the last tube closes: the right edge of a drag. */
const CLOSED = 0.36 + RUN;
const IDLE = 2;
const FADE = 0.25;
const SWITCH_OVER = OFF + DARK + LIT;

const WALL = '#060509';
const MINT = '#3dffc4';
const ROSE = '#ff6fae';
const HEAD = '#d8fff4';
const SPARK = '#9ffcff';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

/** Fill brightness while igniting: two flickers, then on. */
const IGNITE_KEYS = [0, 0, 0.08, 0.85, 0.16, 0.15, 0.26, 0.95, 0.34, 0.4, 0.5, 1, 1, 1];
/** Brightness while switching off: dips and catches twice, then cools. */
const OFF_KEYS = [0, 1, 0.1, 0.25, 0.18, 0.9, 0.28, 0.15, 0.36, 0.7, 0.55, 0.35, 1, 0];

// What a touch is playing.
const NONE = 0;
const SCRUB = 1;
const PLAY = 2;
/** Switch `from` off, then write `to`. */
const NEXT = 3;
/** Reduced motion: cross-fade from one lit word to another. */
const CROSS = 4;

type Manual = { mode: number; word: number; to: number; tau: number; start: number };
/** What the sign shows at one moment. */
type Look = { word: number; tau: number; off: number; breath: number; alpha: number };

function keyed(keys: number[], x: number) {
  'worklet';
  if (x <= keys[0]) return keys[1];
  for (let i = 2; i < keys.length; i += 2) {
    if (x <= keys[i]) {
      const x0 = keys[i - 2];
      const y0 = keys[i - 1];
      return y0 + ((keys[i + 1] - y0) * (x - x0)) / (keys[i] - x0);
    }
  }
  return keys[keys.length - 1];
}

function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

function look(word: number, tau: number, off = 0, breath = 0, alpha = 1): Look {
  'worklet';
  return { word, tau, off, breath, alpha };
}

/** `from` switches off, a beat of dark, then `to` is written and lit; `e` counts from the switch-off. */
function switchOver(from: number, to: number, e: number): Look {
  'worklet';
  if (e < OFF) return look(from, LIT, e / OFF);
  if (e < OFF + DARK) return look(to, 0);
  return look(to, Math.min(e - OFF - DARK, LIT));
}

function lookAt(now: number, m: Manual, loopStart: number, count: number, playing: boolean): Look {
  'worklet';
  const e = now - m.start;
  if (m.mode === SCRUB) return look(m.word, m.tau);
  if (m.mode === PLAY) return look(m.word, Math.min(m.tau + e, LIT));
  if (m.mode === NEXT) return switchOver(m.word, m.to, e);
  if (m.mode === CROSS) return e < FADE ? look(m.word, LIT, 0, 0, 1 - e / FADE) : look(m.to, LIT, 0, 0, Math.min((e - FADE) / FADE, 1));
  if (!playing) return look(0, LIT);
  const t = (((now - loopStart) % LOOP) + LOOP) % LOOP;
  const k = Math.min(Math.floor(t / SEGMENT), 2) % count;
  const l = t - k * SEGMENT;
  if (l < HOLD) return look(k, LIT, 0, Math.sin((Math.PI * l) / HOLD));
  return switchOver(k, (k + 1) % count, l - HOLD);
}

function finished(m: Manual, now: number) {
  'worklet';
  const e = now - m.start;
  if (m.mode === SCRUB) return false;
  if (m.mode === PLAY) return m.tau + e >= LIT;
  if (m.mode === NEXT) return e >= SWITCH_OVER;
  if (m.mode === CROSS) return e >= FADE * 2;
  return true;
}

function rgba(hex: string, alpha: number): SkColor {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, Math.min(Math.max(alpha, 0), 1));
}

/** `hex` moved `amount` of the way to white. */
function whiten(hex: string, amount: number): SkColor {
  'worklet';
  const c = rgba(hex, 1);
  return Float32Array.of(c[0] + (1 - c[0]) * amount, c[1] + (1 - c[1]) * amount, c[2] + (1 - c[2]) * amount, 1);
}

function drawTubes(canvas: SkCanvas, shape: WordShape, colours: NeonWord, tau: number, alpha: number) {
  'worklet';
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const glow = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true);
  const heads = Skia.Path.Make();
  const hot = Skia.Path.Make();
  const sparks: number[][] = [];
  for (let layer = 0; layer < 3; layer++) {
    const run = clamp01((tau - LAGS[layer]) / RUN);
    if (run <= 0) continue;
    const colour = layer === 0 ? MINT : layer === 1 ? ROSE : colours.glow;
    const tube = Skia.Path.Make();
    for (const contour of shape.contours) {
      const p = clamp01((run - STAGGER * contour.delay) / SPAN);
      if (p <= 0) continue;
      tube.addPath(contour.measure.getSegment(0, p * contour.length, true));
      if (layer >= 2) continue;
      if (p < 1) {
        const [head] = contour.measure.getPosTan(p * contour.length);
        heads.addCircle(head.x, head.y, 1.6);
        hot.addPath(contour.measure.getSegment(Math.max(p - 0.07, 0) * contour.length, p * contour.length, true));
      }
      for (const s of contour.sparks) {
        if (s.layer !== layer || p < s.at) continue;
        const age = (p - s.at) * SPAN * RUN;
        if (age >= s.life) continue;
        const x = s.ox + s.vx * age;
        const y = s.oy + s.vy * age + 0.5 * 260 * age * age;
        const vy = s.vy + 260 * age;
        const speed = Math.max(Math.hypot(s.vx, vy), 1);
        sparks.push([x, y, x - (s.vx / speed) * s.length, y - (vy / speed) * s.length, 1 - age / s.life, s.width, s.white ? 1 : 0]);
      }
    }
    stroke.setMaskFilter(glow);
    stroke.setStrokeWidth(3.5);
    stroke.setStrokeCap(StrokeCap.Butt);
    stroke.setStrokeJoin(StrokeJoin.Miter);
    stroke.setColor(rgba(colour, 0.6 * alpha));
    canvas.drawPath(tube, stroke);
    stroke.setMaskFilter(null);
    stroke.setStrokeWidth(1.7);
    stroke.setStrokeCap(StrokeCap.Round);
    stroke.setStrokeJoin(StrokeJoin.Round);
    stroke.setColor(rgba(colour, alpha));
    canvas.drawPath(tube, stroke);
    stroke.setStrokeWidth(0.6);
    stroke.setStrokeCap(StrokeCap.Butt);
    stroke.setStrokeJoin(StrokeJoin.Miter);
    stroke.setColor(rgba('#ffffff', 0.4 * alpha));
    canvas.drawPath(tube, stroke);
  }
  // The pen heads: white-hot ends with a halo, and the sparks they throw.
  stroke.setMaskFilter(glow);
  stroke.setStrokeWidth(4);
  stroke.setColor(rgba('#ffffff', 0.9 * alpha));
  canvas.drawPath(hot, stroke);
  fill.setMaskFilter(glow);
  fill.setColor(rgba(HEAD, alpha));
  canvas.drawPath(heads, fill);
  stroke.setMaskFilter(null);
  fill.setMaskFilter(null);
  stroke.setStrokeWidth(1.8);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setColor(rgba('#ffffff', alpha));
  canvas.drawPath(hot, stroke);
  fill.setColor(rgba('#ffffff', alpha));
  canvas.drawPath(heads, fill);
  for (const [x, y, tx, ty, life, width, white] of sparks) {
    stroke.setStrokeWidth(width);
    stroke.setColor(rgba(white ? '#ffffff' : SPARK, alpha * life));
    canvas.drawLine(tx, ty, x, y, stroke);
  }
}

function drawFill(canvas: SkCanvas, shape: WordShape, colours: NeonWord, level: number, gain: number) {
  'worklet';
  const left = Skia.Point(shape.left.x, shape.left.y);
  const right = Skia.Point(shape.right.x, shape.right.y);
  const tube = Skia.Shader.MakeLinearGradient(left, right, [rgba(colours.start, 1), rgba(colours.end, 1)], null, TileMode.Clamp);
  const core = Skia.Shader.MakeLinearGradient(left, right, [whiten(colours.start, 0.5), whiten(colours.end, 0.45)], null, TileMode.Clamp);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);

  // The halo is one layer (fill and outline blurred together), faded as a whole.
  const layer = Skia.Paint();
  layer.setAlphaf(Math.min(level * 0.55 * gain, 1));
  canvas.saveLayer(layer);
  const wide = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true);
  fill.setMaskFilter(wide);
  fill.setColor(rgba(colours.glow, 1));
  canvas.drawPath(shape.fill, fill);
  stroke.setMaskFilter(wide);
  stroke.setStrokeWidth(6);
  stroke.setColor(rgba(colours.glow, 1));
  canvas.drawPath(shape.fill, stroke);
  canvas.restore();

  stroke.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  stroke.setStrokeWidth(3.2);
  stroke.setShader(tube);
  stroke.setAlphaf(Math.min(level * gain, 1));
  canvas.drawPath(shape.fill, stroke);

  fill.setMaskFilter(null);
  stroke.setMaskFilter(null);
  stroke.setStrokeWidth(1.2);
  stroke.setAlphaf(level);
  canvas.drawPath(shape.fill, stroke);
  fill.setShader(core);
  fill.setAlphaf(level);
  canvas.drawPath(shape.fill, fill);
}

function drawSign(canvas: SkCanvas, width: number, height: number, l: Look, shape: WordShape, colours: NeonWord) {
  'worklet';
  const ignite = clamp01((l.tau - WRITE) / IGNITE);
  const level = (l.tau >= WRITE ? keyed(IGNITE_KEYS, ignite) : 0) * keyed(OFF_KEYS, l.off) * l.alpha;
  const gain = 1 + 0.45 * (ignite > 0.4 && ignite < 1 ? Math.sin((Math.PI * (ignite - 0.4)) / 0.6) : 0) + 0.1 * l.breath;
  const tubes = (1 - ignite) * l.alpha;
  const writing = Math.min(l.tau / WRITE, 1) * tubes;

  // Light thrown on the wall.
  const light = Math.max(level * gain, writing * 0.55);
  if (light > 0.002) {
    const wall = Skia.Paint();
    wall.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(width / 2, height * 0.45),
        width * 0.95,
        [rgba(colours.glow, 0.2), rgba(colours.glow, 0.06), rgba(colours.glow, 0)],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    wall.setAlphaf(Math.min(light, 1));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), wall);
  }
  if (tubes > 0.002 && l.tau > 0) drawTubes(canvas, shape, colours, Math.min(l.tau, WRITE), tubes);
  if (level > 0.002) drawFill(canvas, shape, colours, level, gain);
}

type Props = {
  words?: NeonWord[];
  /** Writes the words one after another by itself, every `LOOP` seconds round. */
  autoPlay?: boolean;
  caption?: string;
  hint?: string;
};

/**
 * A script word on a dark wall that writes itself in neon: three coloured tubes trace the outline of
 * every letter with white-hot, sparking pen heads, then the word flickers on in its own gradient.
 */
export function NeonSignatureWrite({ words = SAMPLE_WORDS, autoPlay = false, caption = 'Open late', hint = 'Drag across the sign to write it' }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const font = useFont(require('./assets/Hurricane-Regular.ttf'), 100);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const texts = words.map((w) => w.text).join('\n');
  const shapes = useMemo(
    () => (font && size.width > 0 ? layoutWords(font, texts.split('\n'), size.width, size.height) : null),
    [font, texts, size.width, size.height],
  );
  const [shown, setShown] = useState({ word: 0, lit: true });

  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const manual = useSharedValue<Manual>({ mode: NONE, word: 0, to: 0, tau: 0, start: 0 });
  const dragging = useSharedValue(false);
  const scrubbing = useSharedValue(false);
  const grabbed = useSharedValue(0);
  /** When the last touch ended, while it still has to be settled; −1 otherwise. */
  const touchEnd = useSharedValue(-1);
  const returning = useSharedValue(false);
  /** A touch started the run on screen: it gets a haptic when the word lights. */
  const touchRun = useSharedValue(false);
  const lastShown = useSharedValue(-1);
  // The sign's outlines take a moment to build; the screen shows, and the loop starts, once they exist.
  const ready = useSharedValue(false);
  useEffect(() => {
    ready.set(shapes != null);
  }, [shapes, ready]);
  const count = words.length;
  const playing = autoPlay && !reduced;

  const impact = (soft: boolean) => {
    Haptics.impactAsync(soft ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    const before = lookAt(clock.get(), manual.get(), loopStart.get(), count, playing);
    clock.set(now);
    if (loopStart.get() < 0 || !ready.get()) loopStart.set(now);
    const m = manual.get();

    // 2 s after the last touch, once what it started has finished, the sign returns to the first word
    // and the loop starts over from its resting frame.
    if (touchEnd.get() >= 0 && !dragging.get() && now - touchEnd.get() >= IDLE && finished(m, now)) {
      const current = lookAt(now, m, loopStart.get(), count, playing).word;
      if (!returning.get() && current !== 0) {
        returning.set(true);
        touchRun.set(false);
        manual.set({ mode: reduced ? CROSS : NEXT, word: current, to: 0, tau: 0, start: now });
      } else {
        returning.set(false);
        touchEnd.set(-1);
        loopStart.set(now);
        manual.set({ mode: NONE, word: 0, to: 0, tau: 0, start: 0 });
      }
    }

    const l = lookAt(now, manual.get(), loopStart.get(), count, playing);
    if (touchRun.get() && before.tau < WRITE && l.tau >= WRITE && l.word === before.word) scheduleOnRN(impact, true);
    const key = l.word * 2 + (l.tau >= WRITE && l.off < 0.5 ? 1 : 0);
    if (key !== lastShown.get()) {
      lastShown.set(key);
      scheduleOnRN(setShown, { word: l.word, lit: key % 2 === 1 });
    }
  });

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const l = lookAt(now, manual.get(), loopStart.get(), count, playing);
    return createPicture((canvas) => {
      if (!shapes || l.word >= shapes.length) return;
      drawSign(canvas, size.width, size.height, l, shapes[l.word], words[l.word]);
    });
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const now = clock.get();
      dragging.set(true);
      scrubbing.set(false);
      returning.set(false);
      touchEnd.set(-1);
      grabbed.set(lookAt(now, manual.get(), loopStart.get(), count, playing).word);
    })
    .onUpdate((e) => {
      if (reduced || !shapes) return;
      const moved = Math.hypot(e.translationX, e.translationY) > 8;
      if (!moved && !scrubbing.get()) return;
      if (!scrubbing.get()) {
        scrubbing.set(true);
        scheduleOnRN(impact, false);
      }
      const box = shapes[grabbed.get()].box;
      const fraction = clamp01((e.x - box.x) / box.width);
      touchRun.set(true);
      manual.set({ mode: SCRUB, word: grabbed.get(), to: 0, tau: fraction * CLOSED, start: clock.get() });
    })
    .onFinalize(() => {
      const now = clock.get();
      const m = manual.get();
      if (reduced) {
        const from = lookAt(now, m, loopStart.get(), count, playing).word;
        manual.set({ mode: CROSS, word: from, to: (from + 1) % count, tau: 0, start: now });
        scheduleOnRN(impact, false);
      } else if (scrubbing.get() && m.mode === SCRUB) {
        manual.set({ mode: PLAY, word: m.word, to: 0, tau: m.tau, start: now });
      } else {
        const from = lookAt(now, m, loopStart.get(), count, playing).word;
        touchRun.set(true);
        manual.set({ mode: NEXT, word: from, to: (from + 1) % count, tau: 0, start: now });
        scheduleOnRN(impact, false);
      }
      dragging.set(false);
      scrubbing.set(false);
      touchEnd.set(now);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const current = words[shown.word] ?? words[0];
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill} accessibilityLabel={current.text} accessibilityRole="image">
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>

      {shapes ? (
        <View pointerEvents="none" style={[styles.chrome, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
          <View style={styles.bar}>
            <View
              style={[
                styles.dot,
                shown.lit
                  ? { backgroundColor: current.glow, shadowColor: current.glow, shadowOpacity: 1, shadowRadius: 5, shadowOffset: { width: 0, height: 0 } }
                  : { backgroundColor: 'rgba(255,255,255,0.25)' },
              ]}
            />
            <Text style={styles.caption} numberOfLines={1}>
              {caption.toUpperCase()}
            </Text>
            <View style={styles.spacer} />
            <Text style={styles.count} numberOfLines={1}>
              {`${String(shown.word + 1).padStart(2, '0')} / ${String(words.length).padStart(2, '0')}`}
            </Text>
          </View>

          <View style={styles.flex} />

          <View style={styles.footer}>
            <Text style={styles.hint} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
              {hint}
            </Text>
            <View style={styles.chips}>
              {words.map((w, index) => {
                const selected = index === shown.word;
                return (
                  <View
                    key={w.text}
                    style={[
                      styles.chip,
                      selected ? { borderColor: withAlpha(w.glow, 0.7), backgroundColor: withAlpha(w.glow, 0.12) } : styles.chipIdle,
                    ]}
                  >
                    <Text style={[styles.chipText, selected && styles.chipSelected]} numberOfLines={1}>
                      {w.text}
                    </Text>
                  </View>
                );
              })}
            </View>
          </View>
        </View>
      ) : null}
    </View>
  );
}

function withAlpha(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: WALL },
  chrome: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
  bar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 24, paddingTop: 16 },
  dot: { width: 7, height: 7, borderRadius: 3.5, marginRight: 10 },
  caption: { color: 'rgba(255,255,255,0.72)', fontFamily: MONO, fontSize: 12, fontWeight: '600', letterSpacing: 2.4 },
  spacer: { flex: 1, minWidth: 12 },
  count: { color: 'rgba(255,255,255,0.42)', fontFamily: MONO, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  flex: { flex: 1 },
  footer: { alignItems: 'center', paddingHorizontal: 16, paddingBottom: 24, gap: 20 },
  hint: { color: 'rgba(255,255,255,0.42)', fontSize: 13 },
  chips: { flexDirection: 'row', gap: 8 },
  chip: { height: 32, borderRadius: 16, borderWidth: 1, paddingHorizontal: 14, justifyContent: 'center' },
  chipIdle: { borderColor: 'rgba(255,255,255,0.1)' },
  chipText: { color: 'rgba(255,255,255,0.36)', fontSize: 14 },
  chipSelected: { color: 'rgba(255,255,255,0.95)', fontWeight: '600' },
});
