import { Canvas, Picture, Skia, type SkFont, useFont } from '@shopify/react-native-skia';
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
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Dot Cloud Digits — a month's numbers, each drawn by the letters of its own name. A port of the
 SwiftUI block (DotCloudDigits.swift); the timeline is the same:

 autoPlay (LOOP = 9 s, three numbers of 3 s each, k = 0, 1, 2 starting at 3k s):
   3k + 0.0 … 3k + 1.2  number k rests, fully formed (t = 0 is the screen's first look).
   3k + 1.2 … 3k + 3.0  transition k → k+1 (1.8 s). Each letter waits 0…0.3 s (seeded), then
                        travels 1.5 s along a curve through its cloud point (ease-in-out cubic);
                        the cloud turns 0.55 rad as it goes. Halfway it becomes the new name's
                        letter; it shrinks to 70 % and dims to 50 % there. Caption: the old one
                        fades out over the first 35 %, the new one in over the last 40 %.
   9.0                  back at number 0, formed: identical to t = 0.
 Background letters twinkle with whole-number cycles per loop, and a glint (opacity 0.8 → 1) runs
 along the outline's letters once every 3 s.

 Touch: a drag pushes the outline's letters away from the finger (radius 90 pt, up to 46 pt); on
 release they spring back (e^{-6t}·cos 11t). A tap moves to the next number. 2 s after the last
 touch the screen returns to the first number and the loop starts again.
 Reduced motion: no loop, no cloud, no twinkle; a tap cross-fades to the next number (0.4 s).
 */

export type DotCloudDigitsStat = { value: string; word: string; caption: string };

export const DOT_CLOUD_DIGITS_STATS: DotCloudDigitsStat[] = [
  { value: '29', word: 'TWENTY-NINE', caption: 'runs before 8 am' },
  { value: '12', word: 'TWELVE', caption: 'new routes mapped' },
  { value: '64', word: 'SIXTY-FOUR', caption: 'kilometres on trails' },
];

const LOOP = 9;
const SLOT = 3;
const HOLD = 1.2;
const TRANSITION = 1.8;
const MAX_DELAY = 0.3;
const PARTICLES = 150;
const STARS = 56;
const LETTER_SIZE = 10.5;
const MAX_HEIGHT = 300;
const FONT_NAME = 'DotCloudDigits-Poppins-SemiBold';
const FONT_FILE = require('./assets/Poppins-SemiBold.ttf');
const INK = '#F5F2ED';
const BACKGROUND = '#040405';

type Point = { x: number; y: number };
type Star = Point & { scale: number; base: number; cycles: number; phase: number };
type Shapes = {
  targets: Point[][];
  cloud: Point[];
  cloudCenter: Point;
  delay: number[];
  stars: Star[];
  words: string[][];
  /** Each letter's advance at LETTER_SIZE, to centre it. */
  widths: Record<string, number>;
  captionTop: number;
};

/** A seeded number in 0..<1 (the same 32-bit integer hash in every stack). */
function hash(i: number, salt: number) {
  'worklet';
  let x = (Math.imul(i, 374761393) + Math.imul(salt, 668265263)) >>> 0;
  x = Math.imul(x ^ (x >>> 13), 1274126177) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return x / 4294967296;
}

function ease(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** A curve from `a` to `b` that passes the cloud point halfway, the cloud turning as it goes. */
function travel(a: Point, b: Point, cloud: Point, center: Point, s: number): Point {
  'worklet';
  // Turn the cloud point about the cloud's centre in a squashed space, so the band swirls flat.
  const angle = 0.55 * s;
  const dx = cloud.x - center.x;
  const dy = (cloud.y - center.y) * 3;
  const rx = dx * Math.cos(angle) - dy * Math.sin(angle);
  const ry = dx * Math.sin(angle) + dy * Math.cos(angle);
  const cx = center.x + rx;
  const cy = center.y + ry / 3;
  const kx = 2 * cx - (a.x + b.x) / 2;
  const ky = 2 * cy - (a.y + b.y) / 2;
  const u = 1 - s;
  return { x: u * u * a.x + 2 * s * u * kx + s * s * b.x, y: u * u * a.y + 2 * s * u * ky + s * s * b.y };
}

type Outline = { contours: Point[][]; minX: number; minY: number; maxX: number; maxY: number };

/** The figure's outline at 1 pt per em, y down, from the glyphs placed by their advances (no kerning). */
function outline(text: string, font: SkFont): Outline {
  const unit = 1000;
  const typeface = font.getTypeface();
  const big = typeface ? Skia.Font(typeface, unit) : font;
  const contours: Point[][] = [];
  let x = 0;
  for (const ch of text) {
    const path = Skia.Path.MakeFromText(ch, x, 0, big);
    if (path) {
      const iter = Skia.ContourMeasureIter(path, true, 1);
      for (let contour = iter.next(); contour; contour = iter.next()) {
        const length = contour.length();
        if (length < 1) continue;
        const steps = Math.max(2, Math.floor(length / 4));
        const points: Point[] = [];
        for (let k = 0; k <= steps; k++) {
          const [pos] = contour.getPosTan((length * k) / steps);
          points.push({ x: pos.x / unit, y: pos.y / unit });
        }
        contours.push(points);
      }
    }
    x += big.getGlyphWidths(big.getGlyphIDs(ch))[0];
  }
  const all = contours.flat();
  return {
    contours,
    minX: Math.min(...all.map((p) => p.x)),
    minY: Math.min(...all.map((p) => p.y)),
    maxX: Math.max(...all.map((p) => p.x)),
    maxY: Math.max(...all.map((p) => p.y)),
  };
}

function length(c: Point[]) {
  let sum = 0;
  for (let j = 0; j < c.length - 1; j++) sum += Math.hypot(c[j + 1].x - c[j].x, c[j + 1].y - c[j].y);
  return sum;
}

function pointOn(c: Point[], distance: number): Point {
  let walked = 0;
  for (let j = 0; j < c.length - 1; j++) {
    const seg = Math.hypot(c[j + 1].x - c[j].x, c[j + 1].y - c[j].y);
    if (walked + seg >= distance && seg > 0) {
      const t = (distance - walked) / seg;
      return { x: c[j].x + (c[j + 1].x - c[j].x) * t, y: c[j].y + (c[j + 1].y - c[j].y) * t };
    }
    walked += seg;
  }
  return c[c.length - 1];
}

/**
 * `count` points spread evenly along all the contours: each gets its share of the total length
 * (rounded; the longest takes the remainder), and its points sit in the middle of equal steps.
 */
function sample(contours: Point[][], count: number): Point[] {
  const lengths = contours.map(length);
  const total = lengths.reduce((a, b) => a + b, 0);
  const counts = lengths.map((l) => Math.round((l / total) * count));
  let longest = 0;
  lengths.forEach((l, i) => {
    if (l > lengths[longest]) longest = i;
  });
  counts[longest] += count - counts.reduce((a, b) => a + b, 0);
  const points: Point[] = [];
  contours.forEach((c, ci) => {
    if (counts[ci] <= 0) return;
    const step = lengths[ci] / counts[ci];
    for (let k = 0; k < counts[ci]; k++) points.push(pointOn(c, (k + 0.5) * step));
  });
  return points;
}

function buildShapes(stats: DotCloudDigitsStat[], font: SkFont, width: number, height: number): Shapes {
  const outlines = stats.map((s) => outline(s.value, font));
  const maxW = Math.max(...outlines.map((o) => o.maxX - o.minX));
  const maxH = Math.max(...outlines.map((o) => o.maxY - o.minY));
  // The figures share one scale (pt per em) so that "12" is not bigger than "64".
  const scale = Math.min((width - 64) / maxW, MAX_HEIGHT / maxH);
  const centerY = height * 0.45;
  const captionTop = centerY + (maxH * scale) / 2 + 44;
  const targets = outlines.map((o) => {
    const ox = width / 2 - ((o.minX + o.maxX) / 2) * scale;
    const oy = centerY - ((o.minY + o.maxY) / 2) * scale;
    return sample(o.contours, PARTICLES).map((p) => ({ x: ox + p.x * scale, y: oy + p.y * scale }));
  });
  const cloudCenter = { x: width / 2, y: centerY + 70 };
  const cloud: Point[] = [];
  const delay: number[] = [];
  for (let i = 0; i < PARTICLES; i++) {
    const spread = 0.35 + 0.65 * Math.sqrt(hash(i, 1));
    cloud.push({
      x: cloudCenter.x + (hash(i, 2) * 2 - 1) * (width * 0.44) * spread,
      y: cloudCenter.y + (hash(i, 3) * 2 - 1) * 48 * spread,
    });
    delay.push(hash(i, 4) * MAX_DELAY);
  }
  // Background letters below the header and anywhere but on the caption's band.
  const lo = captionTop - 12;
  const hi = captionTop + 64;
  const stars: Star[] = [];
  for (let i = 0; i < STARS; i++) {
    let y = 104 + hash(i, 6) * (height - 164);
    if (y >= lo && y <= hi) y = y - lo < hi - y ? lo - 4 - (y - lo) : hi + 4 + (hi - y);
    stars.push({
      x: 12 + hash(i, 5) * (width - 24),
      y,
      scale: 0.8 + 0.25 * hash(i, 7),
      base: 0.12 + 0.18 * hash(i, 8),
      cycles: 1 + Math.floor(hash(i, 9) * 3),
      phase: hash(i, 10) * 2 * Math.PI,
    });
  }
  const words = stats.map((s) => Array.from(s.word).filter((ch) => /[A-Za-z]/.test(ch)));
  const widths: Record<string, number> = {};
  for (const ch of new Set(words.flat())) widths[ch] = font.getGlyphWidths(font.getGlyphIDs(ch))[0];
  return { targets, cloud, cloudCenter, delay, stars, words, widths, captionTop };
}

/** What is shown at a moment: number a turning into b, tau seconds into the transition (-1: resting on b). */
type Shown = { a: number; b: number; tau: number; fade: number };

/** The block's state on the UI thread; times are in seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  from: SharedValue<number>;
  to: SharedValue<number>;
  since: SharedValue<number>;
  fingerX: SharedValue<number>;
  fingerY: SharedValue<number>;
  pressedAt: SharedValue<number>;
  releasedAt: SharedValue<number>;
  /** When the screen should go back to the first number and restart the loop. */
  resumeAt: SharedValue<number>;
  /** 1 once the current touch has moved past 6 pt (a drag, not a tap). */
  moved: SharedValue<number>;
};

function shownAt(m: Motion, at: number, count: number, reduced: boolean): Shown {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start && !reduced) {
    const e = (((at - start) % LOOP) + LOOP) % LOOP;
    const k = Math.min(count - 1, Math.floor(e / SLOT));
    const within = e - k * SLOT;
    if (within < HOLD) return { a: k, b: k, tau: -1, fade: -1 };
    return { a: k, b: (k + 1) % count, tau: within - HOLD, fade: -1 };
  }
  const since = m.since.get();
  if (since >= 0) {
    const tau = at - since;
    if (reduced) {
      const f = Math.min(1, Math.max(0, tau / 0.4));
      return { a: m.from.get(), b: m.to.get(), tau: -1, fade: f < 1 ? f : -1 };
    }
    if (tau < TRANSITION) return { a: m.from.get(), b: m.to.get(), tau: Math.max(0, tau), fade: -1 };
  }
  return { a: m.to.get(), b: m.to.get(), tau: -1, fade: -1 };
}

/** Caption i's opacity and vertical shift for the shown state. */
function captionLook(s: Shown, i: number): [number, number] {
  'worklet';
  const resting = s.tau < 0 && s.fade < 0;
  if (resting) return [i === s.b ? 1 : 0, 0];
  const p = s.tau >= 0 ? Math.min(1, s.tau / TRANSITION) : s.fade;
  const outT = s.tau >= 0 ? ease(Math.min(1, p / 0.35)) : p;
  const inT = s.tau >= 0 ? ease(Math.max(0, (p - 0.6) / 0.4)) : p;
  if (i === s.b) return [inT, 8 * (1 - inT)];
  if (i === s.a) return [1 - outT, -6 * outT];
  return [0, 0];
}

/** Moves to the next number; false while a change is still playing. */
function advanceTo(m: Motion, now: number, count: number, reduced: boolean) {
  'worklet';
  const since = m.since.get();
  if (since >= 0 && now - since < (reduced ? 0.4 : TRANSITION)) return false;
  m.from.set(m.to.get());
  m.to.set((m.to.get() + 1) % count);
  m.since.set(now);
  return true;
}

function tactile(strong: boolean) {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  if (strong) Haptics.selectionAsync().catch(() => {});
}

type Props = {
  stats?: DotCloudDigitsStat[];
  title?: string;
  autoPlay?: boolean;
};

export function DotCloudDigits({ stats = DOT_CLOUD_DIGITS_STATS, title = 'OCTOBER IN NUMBERS', autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const font = useFont(FONT_FILE, LETTER_SIZE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const shapes = useMemo(
    () => (font && size.width > 0 ? buildShapes(stats, font, size.width, size.height) : null),
    [font, size.width, size.height, stats],
  );
  const count = stats.length;

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    from: useSharedValue(0),
    to: useSharedValue(0),
    since: useSharedValue(-1),
    fingerX: useSharedValue(0),
    fingerY: useSharedValue(0),
    pressedAt: useSharedValue(-1),
    releasedAt: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
    moved: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    if (reduced && m.since.get() >= 0 && now - m.since.get() > 0.45) m.since.set(-1);
    // 2 s after the last touch: finish any change, then go back to the first number and loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      const since = m.since.get();
      if (since >= 0 && now - since < TRANSITION) return;
      m.resumeAt.set(-1);
      if (m.to.get() !== 0) {
        m.from.set(m.to.get());
        m.to.set(0);
        m.since.set(now);
        m.autoStart.set(now + TRANSITION);
      } else {
        m.autoStart.set(now);
      }
    }
  });

  const gesture = useMemo(() => {
    const takeOver = (at: number) => {
      'worklet';
      m.resumeAt.set(-1);
      if (m.autoStart.get() < 0) return;
      const s = shownAt(m, at, count, reduced);
      m.from.set(s.a);
      m.to.set(s.b);
      m.since.set(s.tau >= 0 ? at - s.tau : -1);
      m.autoStart.set(-1);
    };
    const scheduleResume = (at: number) => {
      'worklet';
      if (autoPlay && !reduced) m.resumeAt.set(at + 2);
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        m.moved.set(0);
        takeOver(m.clock.get());
        m.fingerX.set(e.x);
        m.fingerY.set(e.y);
      })
      .onUpdate((e) => {
        if (reduced || Math.hypot(e.translationX, e.translationY) <= 6) return;
        if (m.moved.get() === 0) {
          m.moved.set(1);
          m.pressedAt.set(m.clock.get());
          m.releasedAt.set(-1);
          scheduleOnRN(tactile, false);
        }
        m.fingerX.set(e.x);
        m.fingerY.set(e.y);
      })
      .onFinalize(() => {
        const now = m.clock.get();
        if (m.moved.get() === 1) {
          m.releasedAt.set(now);
          scheduleResume(now);
          return;
        }
        if (advanceTo(m, now, count, reduced)) scheduleOnRN(tactile, true);
        scheduleResume(now);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPlay, reduced, count]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!shapes || !font) return recorder.finishRecordingAsPicture();
    const now = m.clock.get();
    const state = shownAt(m, now, count, reduced);
    const auto = m.autoStart.get();
    const loopT = auto >= 0 ? now - auto : now;
    // How hard the finger pushes, 0…1, springing back after release.
    let strength = 0;
    const pressed = m.pressedAt.get();
    if (!reduced && pressed >= 0) {
      const held = 1 - Math.exp(-12 * Math.max(0, now - pressed));
      const released = m.releasedAt.get();
      if (released < 0) strength = held;
      else {
        const t = now - released;
        strength = t > 1.2 ? 0 : held * Math.exp(-6 * t) * Math.cos(11 * t);
      }
    }
    const fx = m.fingerX.get();
    const fy = m.fingerY.get();
    const paint = Skia.Paint();
    paint.setColor(Skia.Color(INK));
    const words = shapes.words;
    // The capitals' centre sits 0.35 em above the baseline (cap height 0.7 em), as in the Swift symbols.
    const letter = (ch: string, x: number, y: number, scale: number, alpha: number) => {
      paint.setAlphaf(Math.min(1, Math.max(0, alpha)));
      canvas.save();
      canvas.translate(x, y);
      canvas.scale(scale, scale);
      canvas.drawText(ch, -shapes.widths[ch] / 2, 0.35 * LETTER_SIZE, paint, font);
      canvas.restore();
    };

    // Background letters.
    const starFlip = state.tau >= 0 ? ease(Math.min(1, state.tau / 0.9)) : 0;
    for (let i = 0; i < shapes.stars.length; i++) {
      const star = shapes.stars[i];
      const twinkle = reduced ? 0.6 : 0.5 + 0.5 * Math.sin(2 * Math.PI * ((star.cycles * loopT) / LOOP) + star.phase);
      const swap = state.tau < 0 ? 0 : Math.sin(Math.PI * starFlip);
      const word = starFlip < 0.5 ? words[state.a] : words[state.b];
      letter(word[i % word.length], star.x, star.y, star.scale, (star.base + 0.32 * twinkle) * (1 - 0.8 * swap));
    }

    // The figure.
    const a = shapes.targets[state.a];
    const b = shapes.targets[state.b];
    const wa = words[state.a];
    const wb = words[state.b];
    for (let i = 0; i < PARTICLES; i++) {
      if (state.fade >= 0) {
        letter(wa[i % wa.length], a[i].x, a[i].y, 1, 1 - state.fade);
        letter(wb[i % wb.length], b[i].x, b[i].y, 1, state.fade);
        continue;
      }
      let s = 0;
      let p = b[i];
      if (state.tau >= 0) {
        s = easeInOut(Math.min(1, Math.max(0, (state.tau - shapes.delay[i]) / (TRANSITION - MAX_DELAY))));
        p = travel(a[i], b[i], shapes.cloud[i], shapes.cloudCenter, s);
      }
      if (strength !== 0) {
        const dx = p.x - fx;
        const dy = p.y - fy;
        const dist = Math.max(1, Math.hypot(dx, dy));
        if (dist < 90) {
          const k = Math.pow(1 - dist / 90, 2) * 46 * strength;
          p = { x: p.x + (dx / dist) * k, y: p.y + (dy / dist) * k };
        }
      }
      const bump = Math.sin(Math.PI * s);
      const glint = reduced ? 1 : 0.8 + 0.2 * Math.pow(Math.max(0, Math.cos(2 * Math.PI * (i / PARTICLES - loopT / SLOT))), 6);
      const word = s < 0.5 ? wa : wb;
      letter(word[i % word.length], p.x, p.y, 1 - 0.3 * bump, (1 - 0.5 * bump) * glint);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const ready = fontsLoaded && shapes;
  const captionTop = shapes?.captionTop ?? 0;

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${title.charAt(0)}${title.slice(1).toLowerCase()}`}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={() => {
          const now = m.clock.get();
          m.autoStart.set(-1);
          m.resumeAt.set(-1);
          if (advanceTo(m, now, count, reduced)) tactile(true);
          if (autoPlay && !reduced) m.resumeAt.set(now + 2);
        }}
      >
        <StatusBar style="light" />
        {ready ? (
          <>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Picture picture={picture} />
            </Canvas>

            <View style={[styles.header, { top: insets.top + 20 }]} pointerEvents="none">
              <Text style={styles.headerText} numberOfLines={1}>
                {title}
              </Text>
              <View style={styles.counter}>
                {stats.map((_, i) => (
                  <Counter key={i} index={i} count={count} m={m} reduced={reduced} />
                ))}
              </View>
            </View>

            <View style={[styles.caption, { top: captionTop, width: size.width - 48 }]} pointerEvents="none">
              {stats.map((stat, i) => (
                <Caption key={i} index={i} stat={stat} count={count} m={m} reduced={reduced} />
              ))}
            </View>

            <View style={[styles.bar, { top: size.height - Math.max(insets.bottom, 20) - 28 }]} pointerEvents="none">
              {stats.map((_, i) => (
                <Segment key={i} index={i} count={count} m={m} reduced={reduced} />
              ))}
            </View>
          </>
        ) : null}
      </View>
    </GestureDetector>
  );
}

type PartProps = { index: number; count: number; m: Motion; reduced: boolean };

function Counter({ index, count, m, reduced }: PartProps) {
  const style = useAnimatedStyle(() => ({
    opacity: captionLook(shownAt(m, m.clock.get(), count, reduced), index)[0],
  }));
  return (
    <Animated.Text style={[styles.headerText, styles.counterText, style]}>
      {`${String(index + 1).padStart(2, '0')} / ${String(count).padStart(2, '0')}`}
    </Animated.Text>
  );
}

function Caption({ index, stat, count, m, reduced }: PartProps & { stat: DotCloudDigitsStat }) {
  const style = useAnimatedStyle(() => {
    const [opacity, dy] = captionLook(shownAt(m, m.clock.get(), count, reduced), index);
    return { opacity, transform: [{ translateY: dy }] };
  });
  return (
    <Animated.View style={[styles.captionItem, style]}>
      <Text style={styles.captionText} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>
        {stat.caption}
      </Text>
      <Text style={styles.wordText} numberOfLines={1}>
        {stat.word}
      </Text>
    </Animated.View>
  );
}

function Segment({ index, count, m, reduced }: PartProps) {
  const style = useAnimatedStyle(() => {
    const s = shownAt(m, m.clock.get(), count, reduced);
    const resting = s.tau < 0 && s.fade < 0;
    const p = s.tau >= 0 ? Math.min(1, s.tau / TRANSITION) : s.fade >= 0 ? s.fade : 1;
    const bar = easeInOut(p);
    const on = resting ? (index === s.b ? 1 : 0) : index === s.a ? 1 - bar : index === s.b ? bar : 0;
    return { width: 8 + 20 * on, opacity: 0.25 + 0.65 * on };
  });
  return <Animated.View style={[styles.segment, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerText: { fontFamily: FONT_NAME, fontSize: 12, letterSpacing: 2, color: 'rgba(245,242,237,0.55)' },
  counter: { alignItems: 'flex-end', justifyContent: 'center' },
  counterText: { position: 'absolute', right: 0, fontVariant: ['tabular-nums'], minWidth: 72, textAlign: 'right' },
  caption: { position: 'absolute', left: 24 },
  captionItem: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  captionText: { fontFamily: FONT_NAME, fontSize: 22, color: INK, textAlign: 'center' },
  wordText: { fontFamily: FONT_NAME, fontSize: 11, letterSpacing: 3, color: 'rgba(245,242,237,0.45)', marginTop: 10, textAlign: 'center' },
  bar: { position: 'absolute', left: 0, right: 0, height: 3, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  segment: { height: 3, borderRadius: 1.5, backgroundColor: INK },
});
