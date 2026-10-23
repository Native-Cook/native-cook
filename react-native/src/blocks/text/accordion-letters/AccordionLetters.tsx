import { Canvas, type SkCanvas, type SkFont, type SkPath, Picture, Skia, createPicture, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Accordion Letters: four rows of one repeated capital (Anybody Black), each row a fixed width shared by
 its five letters, which squeeze to slivers and stretch to slabs like the bellows of an accordion.

 A row's letter widths are proportional to exp(G·s·(2 − j)) for letters j = 0 … 4, with G = 0.8, so at
 s = +1 the first letter is 24.5 times as wide as the last and at s = −1 the other way round; the widths
 always add up to the row (minus 5 pt gaps).

 Timeline of the self-playing loop (LOOP = 7.2 s, six beats of 1.2 s):
   beat b shows letter A, B, C (b mod 3) in its palette: blue with pale grey, black with yellow,
   orange-red with black; palettes and letters cut hard at the beat's start.
   In beat b every row flips s from σ to −σ (σ = +1 on even beats, −1 on odd), row r starting at
   0.15 + 0.07·r s and taking 0.7 s on an ease-in-out cubic, so the flip ripples down the rows; each
   beat ends where the next begins. Beat 6 is beat 0 again: A, blue, wide on the left — the resting state.

 Touch: dragging across the screen pulls the bulge to the finger: the letter under it grows widest
 (log-width 2.4·exp(−((j − f)/1.1)²), f the finger's place in letters) and each row follows 0.07 s after
 the one above; the drag's shape blends in over 0.25 s. A tap plays the next beat now (cut and flip),
 the drag's shape giving way to it over 0.25 s.
 2 s after the last touch the loop starts over from its first frame and the drag's shape blends out over
 0.5 s. Reduced motion: the letters sit at equal widths and a tap cuts to the next letter and palette.
*/

const BEAT = 1.2;
/** The self-playing loop, in seconds. */
export const LOOP = BEAT * 6;

const FLIP_AT = 0.15;
const FLIP = 0.7;
const ROW_LAG = 0.07;
const ROWS = 4;
const COUNT = 5;
const GAIN = 0.8;
const GAP = 5;
const MARGIN = 16;
const ROW_HEIGHT = 110;
const ROW_GAP = 14;
const BLOCK_HEIGHT = ROWS * ROW_HEIGHT + (ROWS - 1) * ROW_GAP;
const BULGE = 2.4;
const BULGE_WIDTH = 1.1;
const BLEND_IN = 0.25;
const BLEND_OUT = 0.5;
const IDLE = 2;
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

const PALETTES = [
  { back: '#0a47e8', ink: '#dcdcdc' },
  { back: '#0d0d0d', ink: '#fadb0a' },
  { back: '#ff4a1c', ink: '#111111' },
];

/** Strong ease-out (0.23, 1, 0.32, 1) and ease-in-out (0.42, 0, 0.58, 1). */
const CURVES = [
  [0.23, 1, 0.32, 1],
  [0.42, 0, 0.58, 1],
];

function bezier(c: number[], x: number) {
  'worklet';
  // Solve x(t) = x by bisection, then return y(t).
  let lo = 0;
  let hi = 1;
  let t = x;
  for (let i = 0; i < 24; i++) {
    t = (lo + hi) / 2;
    const u = 1 - t;
    const xt = 3 * u * u * t * c[0] + 3 * u * t * t * c[2] + t * t * t;
    if (xt < x) lo = t;
    else hi = t;
  }
  const u = 1 - t;
  return 3 * u * u * t * c[1] + 3 * u * t * t * c[3] + t * t * t;
}

type Tween = { from: number; to: number; start: number; duration: number; curve: number };

function tweenAt(tw: Tween, now: number) {
  'worklet';
  if (tw.duration <= 0) return tw.to;
  const t = Math.min(Math.max((now - tw.start) / tw.duration, 0), 1);
  return tw.from + (tw.to - tw.from) * bezier(CURVES[tw.curve], t);
}

function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Log-widths of a row's letters for beat `b` at `tau`. */
function loopShape(b: number, tau: number, row: number) {
  'worklet';
  const sigma = b % 2 === 0 ? 1 : -1;
  const p = easeInOutCubic(Math.min(Math.max((tau - FLIP_AT - ROW_LAG * row) / FLIP, 0), 1));
  const s = sigma * (1 - 2 * p);
  const centre = (COUNT - 1) / 2;
  const out: number[] = [];
  for (let j = 0; j < COUNT; j++) out.push(GAIN * s * (centre - j));
  return out;
}

/** The finger's place along a row, in letters (0 … 4). */
function fingerIndex(x: number, width: number) {
  'worklet';
  const fraction = (x - MARGIN) / (width - 2 * MARGIN);
  return Math.min(Math.max(fraction, 0), 1) * COUNT - 0.5;
}

/** The place at `at` from samples [time, value] (the oldest before them, the newest after). */
function trailAt(trail: number[][], at: number) {
  'worklet';
  if (trail.length === 0) return 0;
  if (at <= trail[0][0]) return trail[0][1];
  const last = trail[trail.length - 1];
  if (at >= last[0]) return last[1];
  for (let i = 1; i < trail.length; i++) {
    if (trail[i][0] >= at) {
      const span = trail[i][0] - trail[i - 1][0];
      return span > 0 ? trail[i - 1][1] + ((trail[i][1] - trail[i - 1][1]) * (at - trail[i - 1][0])) / span : trail[i][1];
    }
  }
  return last[1];
}

type Glyph = { path: SkPath; x: number; width: number; capHeight: number };

/** Letter outlines of Anybody Black at 100 pt (baseline at y = 0) with their boxes. */
function glyphsFor(font: SkFont, letters: string[]): Glyph[] {
  const capHeight = Skia.Path.MakeFromText('H', 0, 0, font)?.computeTightBounds().height ?? 67.5;
  return letters.map((letter) => {
    const path = Skia.Path.MakeFromText(letter.slice(0, 1), 0, 0, font) ?? Skia.Path.Make();
    const box = path.computeTightBounds();
    return { path, x: box.x, width: Math.max(box.width, 1), capHeight };
  });
}

function drawRows(canvas: SkCanvas, width: number, height: number, top: number, glyph: Glyph, back: string, ink: string, beat: number, tau: number, still: boolean, k: number, trail: number[][], now: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setColor(Skia.Color(back));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(ink));
  const rowWidth = width - 2 * MARGIN - GAP * (COUNT - 1);
  for (let row = 0; row < ROWS; row++) {
    const logs = still ? [0, 0, 0, 0, 0] : loopShape(beat, tau, row);
    if (k > 0) {
      const f = trailAt(trail, now - ROW_LAG * row);
      for (let j = 0; j < COUNT; j++) {
        const d = (j - f) / BULGE_WIDTH;
        logs[j] += (BULGE * Math.exp(-d * d) - logs[j]) * k;
      }
    }
    let total = 0;
    for (let j = 0; j < COUNT; j++) total += Math.exp(logs[j]);
    const baseline = top + row * (ROW_HEIGHT + ROW_GAP) + ROW_HEIGHT;
    let x = MARGIN;
    for (let j = 0; j < COUNT; j++) {
      const w = (Math.exp(logs[j]) / total) * rowWidth;
      const sx = w / glyph.width;
      canvas.save();
      canvas.translate(x - glyph.x * sx, baseline);
      canvas.scale(sx, ROW_HEIGHT / glyph.capHeight);
      canvas.drawPath(glyph.path, paint);
      canvas.restore();
      x += w + GAP;
    }
  }
}

type Props = {
  letters?: string[];
  kicker?: string;
  range?: string;
  title?: string;
  hint?: string;
  /** Plays the accordion by itself, every `LOOP` seconds round. */
  autoPlay?: boolean;
};

/**
 * Four rows of one repeated capital whose letters squeeze to slivers and stretch to slabs like the
 * bellows of an accordion, the letter and its palette cutting hard on every beat.
 */
export function AccordionLetters({
  letters = ['A', 'B', 'C'],
  kicker = 'Width axis',
  range = '12 – 300 %',
  title = 'Every letter,\nevery width.',
  hint = 'Drag across the rows · tap for the next letter',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const font = useFont(require('./assets/Anybody-Black.ttf'), 100);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const key = letters.join('');
  const glyphs = useMemo(() => (font ? glyphsFor(font, key.split('')) : null), [font, key]);
  const [shown, setShown] = useState(0);
  const blockTop = size.height * 0.47 - BLOCK_HEIGHT / 2;

  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  /** −1 while the loop plays; otherwise the beat a tap is playing, from `beatStart`. */
  const tapBeat = useSharedValue(-1);
  const beatStart = useSharedValue(0);
  const blend = useSharedValue<Tween>({ from: 0, to: 0, start: 0, duration: 0, curve: 0 });
  const trail = useSharedValue<number[][]>([]);
  const dragging = useSharedValue(false);
  const dragged = useSharedValue(false);
  /** The finger has moved, so this touch is a drag, not a tap. */
  const swiping = useSharedValue(false);
  /** The beat a drag froze, and how far into it. */
  const frozen = useSharedValue([0, 0]);
  const stillBeat = useSharedValue(0);
  const touchEnd = useSharedValue(-1);
  const tick = useSharedValue(Number.NaN);
  const lastShown = useSharedValue(-1);
  const playing = autoPlay;

  const haptic = (kind: number) => {
    if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  /** The beat on screen and how far into it (0 … 1.2 s). */
  const beatAt = (now: number): [number, number] => {
    'worklet';
    if (reduced) return [stillBeat.get(), BEAT];
    if (dragged.get()) return [frozen.get()[0], frozen.get()[1]];
    if (tapBeat.get() >= 0) return [tapBeat.get(), Math.min(now - beatStart.get(), BEAT)];
    if (!playing) return [0, 0];
    const e = (((now - loopStart.get()) % LOOP) + LOOP) % LOOP;
    const b = Math.min(Math.floor(e / BEAT), 5);
    return [b, e - b * BEAT];
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (loopStart.get() < 0) loopStart.set(now);
    // 2 s after the last touch the loop starts over and a drag's shape blends out.
    if (touchEnd.get() >= 0 && !dragging.get() && now - touchEnd.get() >= IDLE) {
      touchEnd.set(-1);
      loopStart.set(now);
      tapBeat.set(-1);
      stillBeat.set(0);
      if (dragged.get()) {
        dragged.set(false);
        blend.set({ from: tweenAt(blend.get(), now), to: 0, start: now, duration: BLEND_OUT, curve: 1 });
      }
    }
    const b = beatAt(now)[0];
    if (b !== lastShown.get()) {
      lastShown.set(b);
      scheduleOnRN(setShown, b);
    }
  });

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const [b, tau] = beatAt(now);
    const k = reduced ? 0 : tweenAt(blend.get(), now);
    const palette = PALETTES[b % PALETTES.length];
    const samples = trail.get();
    return createPicture((canvas) => {
      if (!glyphs || size.width <= 0) return;
      drawRows(canvas, size.width, size.height, blockTop, glyphs[b % glyphs.length], palette.back, palette.ink, b, tau, reduced, k, samples, now);
    });
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      dragging.set(true);
      touchEnd.set(-1);
      tick.set(Number.NaN);
      swiping.set(false);
    })
    .onUpdate((e) => {
      if (reduced) return;
      const now = clock.get();
      if (Math.hypot(e.translationX, e.translationY) > 8) swiping.set(true);
      if (!swiping.get()) return;
      if (!dragged.get()) {
        frozen.set(beatAt(now));
        dragged.set(true);
        blend.set({ from: tweenAt(blend.get(), now), to: 1, start: now, duration: BLEND_IN, curve: 0 });
        scheduleOnRN(haptic, 0);
      }
      const f = fingerIndex(e.x, size.width);
      const kept = trail.get().filter((s) => now - s[0] <= 1);
      kept.push([now, f]);
      trail.set(kept);
      const notch = Math.round(f);
      if (notch !== tick.get()) {
        if (!Number.isNaN(tick.get())) scheduleOnRN(haptic, 2);
        tick.set(notch);
      }
    })
    .onFinalize(() => {
      const now = clock.get();
      if (reduced) {
        stillBeat.set(stillBeat.get() + 1);
        scheduleOnRN(haptic, 1);
      } else if (!swiping.get()) {
        // A tap: the next beat, now; a drag's shape gives way to it.
        const b = beatAt(now)[0];
        if (dragged.get()) {
          dragged.set(false);
          blend.set({ from: tweenAt(blend.get(), now), to: 0, start: now, duration: BLEND_IN, curve: 0 });
        }
        tapBeat.set(b + 1);
        beatStart.set(now);
        scheduleOnRN(haptic, 1);
      }
      dragging.set(false);
      touchEnd.set(now);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const ink = PALETTES[shown % PALETTES.length].ink;
  const fade = (alpha: number) => {
    const n = parseInt(ink.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
  };
  return (
    <View style={[styles.screen, { backgroundColor: PALETTES[shown % PALETTES.length].back }]} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill} accessibilityLabel={(letters[shown % letters.length] ?? '').repeat(COUNT)} accessibilityRole="image">
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.bar, { top: insets.top + 16 }]}>
        <Text style={[styles.mono, { color: fade(0.75) }]} numberOfLines={1}>
          {kicker.toUpperCase()}
        </Text>
        <View style={styles.spacer} />
        <Text style={[styles.mono, { color: fade(0.75) }]} numberOfLines={1}>
          {range}
        </Text>
      </View>

      <View pointerEvents="none" style={[styles.footer, { bottom: Math.max(insets.bottom, 16) + 12 }]}>
        <View style={styles.titleRow}>
          <Text style={[styles.title, { color: ink }]}>{title}</Text>
          <View style={styles.letters}>
            {letters.map((l, index) => (
              <Text key={`${l}${index}`} style={[styles.letter, { color: fade(index === shown % letters.length ? 1 : 0.35) }]}>
                {l}
              </Text>
            ))}
          </View>
        </View>
        <Text style={[styles.hint, { color: fade(0.6) }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
          {hint.toUpperCase()}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  bar: { position: 'absolute', left: 0, right: 0, height: 20, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center' },
  mono: { fontFamily: MONO, fontSize: 12, fontWeight: '600', letterSpacing: 2 },
  spacer: { flex: 1, minWidth: 12 },
  footer: { position: 'absolute', left: 20, right: 20, gap: 14 },
  titleRow: { flexDirection: 'row', alignItems: 'flex-end' },
  title: { flex: 1, fontSize: 30, lineHeight: 36, fontWeight: '800', letterSpacing: -0.6 },
  letters: { flexDirection: 'row', gap: 10, marginLeft: 12, paddingBottom: 4 },
  letter: { fontFamily: MONO, fontSize: 15, fontWeight: '700' },
  hint: { fontFamily: MONO, fontSize: 10, fontWeight: '500', letterSpacing: 1.4 },
});
