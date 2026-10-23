import { BlurStyle, Canvas, Picture, Skia, createPicture, useClock, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A writing app's welcome screen on near-black. A four-line headline in tall condensed capitals is
// typed in letter by letter: the last four characters behind the moving front are blurred, faint
// and spread out to the right, and sharpen and close up as the front moves on. An orange caret
// rides the front (and pulses while the headline holds) and a thin bar under the headline fills
// with it. After a hold the headline is taken back the same way from its end. Dragging sideways
// scrubs the front. A port of the SwiftUI block, number for number; each letter is drawn on its
// own with a blur mask filter, into a Skia picture made on the UI thread every frame.
//
// The front `f` counts characters over the whole headline (spaces and line breaks included, so a
// line break is a short pause). A character `k` with d = f - k <= 0 is not drawn; with
// 0 < d < 4, u = d / 4: opacity u, blur 0.16 em × (1 - u)², shifted right 0.3 em × (1 - u)²;
// from d >= 4 it is sharp and in place.
//
// autoPlay timeline (LOOP = 8 s), with N characters and full = N + 4:
//   0.0-0.5   empty (the rest state)
//   0.5-3.4   types in: f 0 -> full, linear (about 14 characters a second, as in the reference)
//   3.4-6.4   holds, sharp, while the caret pulses three times (opacity 0.55 + 0.45 cos, 1 s)
//   6.4-7.6   taken back from the end: f full -> 0, linear (about 34 characters a second)
//   7.6-8.5   empty (wrapping round to 0.5), the caret pulsing once at the start of the line
// While the front moves the caret is fully on. Something always moves, so a screen recording
// never sits on a stale frame.
// The caret is shown the whole loop; it sits 0.3 em ahead of the front while letters are blurred
// and closes up to the text at either end (its lead is scaled by min(f, 1) and by the share of
// the edge still to type), so at rest it stands at the start of the line.
//
// Touch: dragging sideways moves the front 1 character per 8 pt from where it was (eased over
// 0.12 s), with a selection tick at each word's end. 2 s after the finger lifts the headline is
// taken back over 0.9 s and the loop starts over.

/** Seconds. */
export const LOOP = 8;
/** Characters behind the front that are still blurred. */
const EDGE = 4;
const TYPE_START = 0.5;
const TYPE_END = 3.4;
const ERASE_START = 6.4;
const ERASE_END = 7.6;
/** The caret pulses every PULSE seconds while the headline holds, and fades in over CARET_FADE when a finger takes over. */
const PULSE = 1;
const CARET_FADE = 0.08;
/** Points of drag per character. */
const DRAG_STEP = 8;
const USER_HOLD = 2;
const SETTLE = 0.9;
/** In ems of the headline size. */
const MAX_BLUR = 0.16;
const MAX_SHIFT = 0.3;
const SIZE_LIMIT = 104;
/** Room around the headline for the blur. */
const PAD = 24;
const FAMILY = 'BlurEdgeTypeIn-BebasNeue';

const C = {
  background: '#070707',
  ink: '#FFFFFF',
  caret: '#FF5B2E',
};

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** The front `t` seconds into the loop, for a headline whose last front is `full`. */
function frontAt(t: number, full: number) {
  'worklet';
  if (t < ERASE_START) return full * clamp01((t - TYPE_START) / (TYPE_END - TYPE_START));
  return full * (1 - clamp01((t - ERASE_START) / (ERASE_END - ERASE_START)));
}

/** The caret's opacity `t` seconds into the loop. */
function caretAt(t: number) {
  'worklet';
  if (t > TYPE_END && t < ERASE_START) return 0.55 + 0.45 * Math.cos((2 * Math.PI * (t - TYPE_END)) / PULSE);
  if (t < TYPE_START || t > ERASE_END) {
    const r = t > ERASE_END ? t - ERASE_END : t + LOOP - ERASE_END;
    return 0.55 + 0.45 * Math.cos((2 * Math.PI * r) / (LOOP - ERASE_END + TYPE_START));
  }
  return 1;
}

/** One character of the headline; an empty one is a line break. */
type Glyph = { character: string; x: number; advance: number; line: number; index: number };

// The ease-out curve the Swift block's Tween uses (0, 0, 0.58, 1), and ease-in-out (0.42, 0, 0.58, 1).
function bezier(x1: number, x2: number, t: number) {
  'worklet';
  let s = t;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s - t;
    const dx = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    s = Math.min(1, Math.max(0, s - x / dx));
  }
  return 3 * (1 - s) * s * s + s * s * s;
}

/** A number animating towards a target, sampled against the clock (ms). `curve`: 0 linear, 1 ease-out, 2 ease-in-out. */
type Tween = { from: number; to: number; start: number; duration: number; curve: number };
function still(v: number): Tween {
  'worklet';
  return { from: v, to: v, start: 0, duration: 0, curve: 1 };
}
function tweenValue(tw: Tween, at: number) {
  'worklet';
  if (tw.duration <= 0) return tw.to;
  const t = clamp01((at - tw.start) / 1000 / tw.duration);
  const e = tw.curve === 0 ? t : tw.curve === 1 ? bezier(0, 0.58, t) : bezier(0.42, 0.58, t);
  return tw.from + (tw.to - tw.from) * e;
}
function retarget(tw: Tween, target: number, at: number, duration: number, curve: number): Tween {
  'worklet';
  return { from: tweenValue(tw, at), to: target, start: at, duration, curve };
}

type Props = {
  lines?: string[];
  label?: string;
  day?: string;
  message?: string;
  action?: string;
  hint?: string;
  /** Types the headline in and takes it back by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

const DEFAULT_LINES = ['ONE WORD', 'AT A TIME.', 'THEN THE', 'NEXT ONE.'];

export function BlurEdgeTypeIn({
  lines = DEFAULT_LINES,
  label = 'Daily pages',
  day = 'Day 12',
  message = 'A quiet editor that shows only the line you are on. Everything else can wait.',
  action = 'Start writing',
  hint = 'Drag sideways to scrub',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const probe = useFont(require('./assets/BebasNeue-Regular.ttf'), 100);

  // The lines at the size that makes the widest span the screen less 24 pt a side (at most 104 pt).
  // A line break between lines is a character too (a pause), placed at the end of its line.
  // Characters are placed by a Skia paragraph, which applies the font's kerning as Core Text does
  // (summed glyph widths would not, and `getTextWidth` comes back rounded).
  const layout = useMemo(() => {
    const typeface = probe?.getTypeface();
    if (!probe || !typeface) return null;
    const provider = Skia.TypefaceFontProvider.Make();
    provider.registerFont(typeface, FAMILY);
    const shape = (text: string, fontSize: number) => {
      const paragraph = Skia.ParagraphBuilder.Make({}, provider)
        .pushStyle({ fontFamilies: [FAMILY], fontSize })
        .addText(text)
        .build();
      paragraph.layout(100000);
      return paragraph;
    };
    const widest = Math.max(...lines.map((l) => shape(l, 100).getLongestLine()));
    const size = Math.min(SIZE_LIMIT, Math.floor((100 * (width - 48)) / Math.max(widest, 1)));
    const font = Skia.Font(typeface, size);
    const glyphs: Glyph[] = [];
    lines.forEach((text, row) => {
      const paragraph = shape(text, size);
      const lineWidth = paragraph.getLongestLine();
      const starts: number[] = [];
      for (let i = 0; i < text.length; i++) starts.push(paragraph.getRectsForRange(i, i + 1)[0]?.x ?? 0);
      for (let i = 0; i < text.length; i++) {
        const next = i + 1 < text.length ? starts[i + 1] : lineWidth;
        glyphs.push({ character: text[i], x: starts[i], advance: next - starts[i], line: row, index: glyphs.length });
      }
      if (row < lines.length - 1) glyphs.push({ character: '', x: lineWidth, advance: 0, line: row, index: glyphs.length });
    });
    return {
      font,
      size,
      glyphs,
      step: size * 0.86,
      cap: size * 0.7,
      height: size * 0.7 + (lines.length - 1) * size * 0.86,
      full: glyphs.length + EDGE,
    };
  }, [probe, lines, width]);
  const full = layout ? layout.full : EDGE;

  const clock = useClock();
  const origin = useSharedValue(0);
  const manual = useSharedValue(false);
  const dragging = useSharedValue(false);
  const front = useSharedValue<Tween>(still(0));
  const caretShown = useSharedValue<Tween>(still(0));
  const dragFrom = useSharedValue(0);
  const lastWord = useSharedValue(0);
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The loop starts when the screen appears.
  useEffect(() => {
    origin.set(clock.get());
  }, [origin, clock]);

  const f = useDerivedValue(() => {
    const now = clock.get();
    if (manual.get()) return tweenValue(front.get(), now);
    if (!autoPlay || reduced) return full;
    return frontAt(Math.max(0, ((now - origin.get()) / 1000) % LOOP), full);
  });
  const caret = useDerivedValue(() => {
    const now = clock.get();
    if (manual.get()) return tweenValue(caretShown.get(), now);
    if (!autoPlay || reduced) return 0;
    return caretAt(Math.max(0, ((now - origin.get()) / 1000) % LOOP));
  });

  const glyphs = layout ? layout.glyphs : [];
  /** How many words are complete with the front at `at`. */
  const words = (at: number) => {
    'worklet';
    let n = 0;
    for (let i = 0; i < glyphs.length; i++) {
      const g = glyphs[i];
      if ((g.character === ' ' || g.character === '' || g.index === glyphs.length - 1) && g.index < at - EDGE + 1) n++;
    }
    return n;
  };

  const picture = useDerivedValue(() => {
    const front = f.get();
    const caretOpacity = caret.get();
    return createPicture((canvas) => {
      if (!layout) return;
      const size = layout.size;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(Skia.Color(C.ink));
      for (let i = 0; i < layout.glyphs.length; i++) {
        const g = layout.glyphs[i];
        if (g.character === '' || g.character === ' ') continue;
        const d = front - g.index;
        if (d <= 0) continue;
        const u = clamp01(d / EDGE);
        const k = (1 - u) * (1 - u);
        const blur = MAX_BLUR * size * k;
        const x = 24 + g.x + MAX_SHIFT * size * k;
        const baseline = PAD + layout.cap + g.line * layout.step;
        paint.setAlphaf(u);
        paint.setMaskFilter(blur > 0.05 ? Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true) : null);
        canvas.drawText(g.character, x, baseline, paint, layout.font);
      }
      // The caret, just ahead of the newest letter.
      if (caretOpacity > 0.001 && layout.glyphs.length > 0) {
        const k = Math.min(layout.glyphs.length - 1, Math.max(0, Math.floor(front)));
        const g = layout.glyphs[k];
        const cx = g.x + clamp01(front - k) * g.advance;
        const lead = clamp01(front) * clamp01((layout.full - front) / EDGE);
        const x = 24 + cx + MAX_SHIFT * size * lead + 4 * clamp01(front);
        const top = PAD + g.line * layout.step;
        const bar = Skia.Paint();
        bar.setAntiAlias(true);
        bar.setColor(Skia.Color(C.caret));
        bar.setAlphaf(caretOpacity);
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, top - 0.04 * size, 4, layout.cap + 0.08 * size), 2, 2), bar);
      }
    });
  });

  const barStyle = useAnimatedStyle(() => ({ width: `${Math.min(1, f.get() / full) * 100}%` }));

  // 2 s after the last touch the headline is taken back, and the loop starts over from empty.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        front.set(retarget(front.get(), 0, clock.get(), SETTLE, 2));
        list.push(
          setTimeout(() => {
            if (dragging.get()) return;
            origin.set(clock.get());
            manual.set(false);
          }, SETTLE * 1000),
        );
      }, USER_HOLD * 1000),
    );
    return () => {
      list.forEach(clearTimeout);
      timers.current = [];
    };
  }, [touches, clock, dragging, front, origin, manual]);

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      const now = clock.get();
      const from = f.get();
      caretShown.set(retarget(still(caret.get()), 1, now, CARET_FADE, 0));
      dragFrom.set(from);
      manual.set(true);
      dragging.set(true);
      front.set(still(from));
      lastWord.set(words(from));
      scheduleOnRN(touched);
      scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      const target = Math.min(full, Math.max(0, dragFrom.get() + e.translationX / DRAG_STEP));
      front.set(retarget(front.get(), target, clock.get(), 0.12, 1));
      const w = words(target);
      if (w !== lastWord.get()) scheduleOnRN(tick);
      lastWord.set(w);
    })
    .onFinalize(() => {
      dragging.set(false);
      scheduleOnRN(touched);
    });

  const headlineHeight = layout ? layout.height : 0;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
            <Text style={styles.label}>{label}</Text>
            <Text style={styles.label}>{day}</Text>
          </View>

          <View style={styles.flex} />

          {/* The headline, with room around it for the blur. */}
          <View style={{ height: headlineHeight }} accessible accessibilityLabel={lines.join(' ')}>
            <Canvas pointerEvents="none" style={{ width, height: headlineHeight + 2 * PAD, marginTop: -PAD }}>
              <Picture picture={picture} />
            </Canvas>
          </View>

          <View style={styles.track}>
            <Animated.View style={[styles.fill, barStyle]} />
          </View>

          <Text style={styles.message} lineBreakStrategyIOS="standard">
            {message}
          </Text>

          {/* Twice the room below as above, so the headline sits high. */}
          <View style={styles.flex} />
          <View style={styles.flex} />

          <View style={styles.button} accessible accessibilityRole="button">
            <Text style={styles.buttonLabel}>{action}</Text>
          </View>

          <Text style={[styles.hint, { paddingBottom: insets.bottom + 12 }]}>{hint}</Text>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  flex: { flex: 1 },
  header: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 24 },
  label: { fontSize: 13, fontWeight: '600', letterSpacing: 0.6, color: C.ink, opacity: 0.55 },
  track: { marginTop: 28, marginHorizontal: 24, height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.14)', overflow: 'hidden' },
  fill: { height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.8)' },
  // SwiftUI's lineSpacing(3) on 17 pt: a 23 pt line.
  message: { marginTop: 24, paddingHorizontal: 24, fontSize: 17, lineHeight: 23, color: C.ink, opacity: 0.62 },
  button: { marginHorizontal: 24, height: 56, borderRadius: 28, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { fontSize: 17, fontWeight: '600', color: C.background },
  hint: { marginTop: 16, fontSize: 13, fontWeight: '500', color: C.ink, opacity: 0.4, textAlign: 'center' },
});
