import { BlurStyle, Canvas, Picture, Skia, type SkFont, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
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
 Gleam Line Intro — the opening of a journaling app: short lines of a serif on black, each word
 arriving with a gleam, then fading for the next beat. A port of the SwiftUI block
 (GleamLineIntro.swift); the numbers are the same.

 Text: Newsreader (a static instance at weight 360, optical size 60), 26 pt, left aligned at 32 pt with
 a 1.3 line height, the block centred on 46 % of the height. Settled words are warm grey #D8D2C8;
 italic words are the emphasis. Three thin progress segments at the top fill with the beats; a hint
 ("Tap to continue") sits at the bottom during the first two beats, a white "Begin writing" pill on
 the last one.

 A word arriving, p = (t − its start) / 0.7 s:
   opacity     smoothstep(0, 0.6, p)
   blur        5 · (1 − easeOutCubic(p / 0.8)) (a Gaussian sigma)
   drift       rises 7 pt into place, easeOutCubic over p = 0 … 1
   gleam       g = exp(−((p − 0.42) / 0.2)²): its colour runs to white and a white glow (sigma 8,
               60 % · g) blooms behind it, so with the words staggered a highlight sweeps along the line.
 A word leaving over d seconds, q = (t − its fade) / d: opacity × (1 − smoothstep(q)), blur + 4·q,
 drifting 6 pt up.

 autoPlay timeline (LOOP = 10 s); the loop starts and ends on the last beat, fully shown:
   0.0 … 1.2   rest: "Keep them here, / one line a day." and the Begin pill.
   1.2 … 1.8   that beat and the pill fade out (0.6 s); the progress segments empty.
   1.9 … 4.9   beat 1, "Some days are loud. / Most of them go unwritten.", word by word every 0.16 s;
               it fades out at 4.9 (0.6 s). The hint fades in at 1.6 and out at 8.6 (0.4 s each).
   5.5 … 8.65  beat 2, three lines arriving at 5.5, 6.3 and 7.1 (a word every 0.12 s), dimmed away line
               by line from 7.9, 8.05 and 8.2 (0.45 s each).
   8.7 … 9.94  beat 3 again, a word every 0.09 s; the pill fades in from 9.3 to 9.8. t = 10 = t = 0.
   Segment k fills linearly while its beat plays: 1.9 → 4.9, 5.5 → 8.6, 8.7 → 9.9.

 Touch: a tap skips to the next beat: what is on screen freezes and fades out in 0.35 s, then the
 next beat arrives as in the loop and holds. 2 s after the last tap the last beat is brought back
 the same way (if it is not already showing) and the loop carries on from rest. A light haptic per tap.
 Reduced motion: the last beat stands still and the loop is off; taps still step through the beats,
 the words fading in without blur, drift or gleam.
 */

const LOOP = 10;
const FONT_SIZE = 26;
const WORD_SPAN = 0.7;
const SETTLED = [0xd8 / 255, 0xd2 / 255, 0xc8 / 255];
const REGULAR_FILE = require('./assets/Newsreader.ttf');
const ITALIC_FILE = require('./assets/NewsreaderItalic.ttf');

type Beat = {
  /** When its first word starts, and the latest loop time that still shows it whole. */
  reveal: number;
  hold: number;
  /** When each line starts, and the gap between its words. */
  lineStarts: number[];
  stagger: number;
  /** When each line fades, and over how long; the last beat fades at 1.2 of the next loop. */
  fades: number[];
  fadeSpan: number;
};

const BEATS: Beat[] = [
  { reveal: 1.9, hold: 4.85, lineStarts: [1.9], stagger: 0.16, fades: [4.9], fadeSpan: 0.6 },
  { reveal: 5.5, hold: 7.85, lineStarts: [5.5, 6.3, 7.1], stagger: 0.12, fades: [7.9, 8.05, 8.2], fadeSpan: 0.45 },
  { reveal: 8.7, hold: 9.999, lineStarts: [8.7], stagger: 0.09, fades: [1.2], fadeSpan: 0.6 },
];

function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - Math.min(1, Math.max(0, t)), 3);
}

/** The beat after the one on screen at loop time `e`. */
function nextBeat(e: number) {
  'worklet';
  if (e < 1.2) return 0;
  if (e < 4.9) return 1;
  if (e < 8.7) return 2;
  return 0;
}

type Word = { text: string; italic: boolean; x: number; baseline: number; line: number; index: number; place: number };

/** A word's look at a loop time: [opacity, blur (sigma), vertical drift, gleam]. */
function look(beat: number, w: Word, e: number, reduced: boolean): [number, number, number, number] {
  'worklet';
  const b = BEATS[beat];
  let s: number;
  let f: number;
  // A beat with one start counts its words through the beat; one with a start per line through the line.
  if (b.lineStarts.length === 1) {
    s = b.lineStarts[0] + w.index * b.stagger;
    f = b.fades[0];
  } else {
    s = b.lineStarts[Math.min(w.line, b.lineStarts.length - 1)] + w.place * b.stagger;
    f = b.fades[Math.min(w.line, b.fades.length - 1)];
  }
  if (beat === 2) {
    // The last beat is whole at the start of the loop and fades at 1.2; later it arrives again.
    if (e < 5) s -= LOOP;
    else f += LOOP;
  }
  const p = (e - s) / WORD_SPAN;
  if (p <= 0) return [0, 0, 0, 0];
  let alpha = smoothstep(0, 0.6, p);
  let blur = 0;
  let dy = 0;
  let gleam = 0;
  if (!reduced) {
    blur = 5 * (1 - easeOutCubic(Math.min(1, p / 0.8)));
    dy = 7 * (1 - easeOutCubic(Math.min(1, p)));
    gleam = p < 1.2 ? Math.exp(-Math.pow((p - 0.42) / 0.2, 2)) : 0;
  }
  if (e >= f) {
    const q = (e - f) / b.fadeSpan;
    if (q >= 1) return [0, 0, 0, 0];
    const k = smoothstep(0, 1, q);
    alpha *= 1 - k;
    if (!reduced) {
      blur += 4 * q;
      dy -= 6 * k;
    }
  }
  return [alpha, blur, dy, gleam];
}

/** The chrome at loop time `e`: [fill 1, fill 2, fill 3, hint opacity, pill opacity]. */
function chrome(e: number): number[] {
  'worklet';
  let fills: number[];
  if (e < 1.2) fills = [1, 1, 1];
  else if (e < 1.8) {
    const k = 1 - smoothstep(1.2, 1.8, e);
    fills = [k, k, k];
  } else {
    fills = [
      [1.9, 4.9],
      [5.5, 8.6],
      [8.7, 9.9],
    ].map(([a, b]) => Math.min(1, Math.max(0, (e - a) / (b - a))));
  }
  const hint = smoothstep(1.6, 2.0, e) * (1 - smoothstep(8.6, 9.0, e));
  const pill = e < 5 ? 1 - smoothstep(1.2, 1.6, e) : smoothstep(9.3, 9.8, e);
  return [fills[0], fills[1], fills[2], hint, pill];
}

/** Every word of every beat; *…* marks italics, and lines wrap greedily if one is too long. */
function layoutBeats(texts: string[], width: number, height: number, regular: SkFont, italic: SkFont): Word[][] {
  // Advances character by character (Skia draws without kerning).
  const advance = (text: string, font: SkFont) => font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
  const metrics = regular.getMetrics();
  const ascent = -metrics.ascent;
  const descent = metrics.descent;
  const lineHeight = 1.3 * FONT_SIZE;
  const left = 32;
  const maxWidth = width - 64;
  const space = advance(' ', regular);
  return texts.map((text) => {
    const lines: [string, boolean, number][][] = [];
    for (const raw of text.split('\n')) {
      let line: [string, boolean, number][] = [];
      let w = 0;
      let inItalic = false;
      for (const token of raw.split(' ').filter((t) => t.length > 0)) {
        let word = token;
        if (word.startsWith('*')) {
          inItalic = true;
          word = word.slice(1);
        }
        const isItalic = inItalic;
        if (word.endsWith('*')) {
          inItalic = false;
          word = word.slice(0, -1);
        }
        const a = advance(word, isItalic ? italic : regular);
        if (line.length > 0 && w + space + a > maxWidth) {
          lines.push(line);
          line = [];
          w = 0;
        }
        w += (line.length === 0 ? 0 : space) + a;
        line.push([word, isItalic, a]);
      }
      lines.push(line);
    }
    const top = height * 0.46 - (lines.length * lineHeight) / 2;
    const words: Word[] = [];
    let count = 0;
    lines.forEach((line, li) => {
      const baseline = top + li * lineHeight + (lineHeight - ascent - descent) / 2 + ascent;
      let x = left;
      line.forEach(([word, isItalic, a], wi) => {
        words.push({ text: word, italic: isItalic, x, baseline, line: li, index: count++, place: wi });
        x += a + space;
      });
    });
    return words;
  });
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /** A beat shown by hand: which, and when it was asked for. */
  manualBeat: SharedValue<number>;
  manualAt: SharedValue<number>;
  /** What was on screen when the last change was asked for, frozen at that loop time, and when. */
  outgoingE: SharedValue<number>;
  outgoingAt: SharedValue<number>;
  /** The return to rest after a tap: when its next step runs, and which (1: bring the last beat, 2: loop). */
  resumeAt: SharedValue<number>;
  resumeStep: SharedValue<number>;
};

/** The loop time that shows what is asked for. */
function loopTime(m: Motion, now: number, reduced: boolean) {
  'worklet';
  const beat = m.manualBeat.get();
  if (beat >= 0) {
    const b = BEATS[beat];
    return Math.min(b.reveal + Math.max(0, now - m.manualAt.get() - 0.35) - 0.001, b.hold);
  }
  const start = m.autoStart.get();
  if (start >= 0 && now >= start && !reduced) return (now - start) % LOOP;
  return 0;
}

/** The frozen outgoing time and how much of it still shows, or null. */
function outgoing(m: Motion, now: number): [number, number] | null {
  'worklet';
  const e = m.outgoingE.get();
  if (e < 0) return null;
  const k = smoothstep(0, 0.35, now - m.outgoingAt.get());
  return k < 1 ? [e, 1 - k] : null;
}

function show(m: Motion, beat: number, now: number, reduced: boolean) {
  'worklet';
  m.outgoingE.set(loopTime(m, now, reduced));
  m.outgoingAt.set(now);
  m.autoStart.set(-1);
  m.manualBeat.set(beat);
  m.manualAt.set(now);
}

function chromeAt(m: Motion, now: number, reduced: boolean): number[] {
  'worklet';
  const c = chrome(loopTime(m, now, reduced));
  const out = outgoing(m, now);
  if (!out) return c;
  const was = chrome(out[0]);
  return c.map((v, i) => v * (1 - out[1]) + was[i] * out[1]);
}

function tapFeedback() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  beats?: string[];
  hint?: string;
  action?: string;
  autoPlay?: boolean;
  onAction?: () => void;
};

export function GleamLineIntro({
  beats = [
    'Some days are loud.\nMost of them go *unwritten.*',
    'A thought on the train.\nA name you almost forgot.\nThe light at six o’clock.',
    'Keep them here,\n*one line a day.*',
  ],
  hint = 'Tap to continue',
  action = 'Begin writing',
  autoPlay = false,
  onAction,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const regular = useFont(REGULAR_FILE, FONT_SIZE);
  const italic = useFont(ITALIC_FILE, FONT_SIZE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const words = useMemo(
    () => (regular && italic && size.width > 0 ? layoutBeats(beats, size.width, size.height, regular, italic) : null),
    [regular, italic, size.width, size.height, beats],
  );

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    manualBeat: useSharedValue(-1),
    manualAt: useSharedValue(0),
    outgoingE: useSharedValue(-1),
    outgoingAt: useSharedValue(0),
    resumeAt: useSharedValue(-1),
    resumeStep: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    // 2 s after the last tap: bring the last beat back (if it is not showing), then the loop from rest.
    const at = m.resumeAt.get();
    if (at >= 0 && now >= at) {
      if (m.resumeStep.get() === 1 && m.manualBeat.get() !== 2) {
        show(m, 2, now, reduced);
        m.resumeStep.set(2);
        m.resumeAt.set(now + 1.7);
      } else {
        m.resumeAt.set(-1);
        m.outgoingE.set(-1);
        m.manualBeat.set(-1);
        m.autoStart.set(now);
      }
    }
  });

  const tap = Gesture.Tap().onEnd(() => {
    const now = m.clock.get();
    scheduleOnRN(tapFeedback);
    show(m, nextBeat(loopTime(m, now, reduced)), now, reduced);
    m.resumeAt.set(-1);
    if (autoPlay && !reduced) {
      m.resumeStep.set(1);
      m.resumeAt.set(now + 2);
    }
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!words || !regular || !italic) return recorder.finishRecordingAsPicture();
    const now = m.clock.get();
    const paint = Skia.Paint();
    const glow = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true);
    const draw = (e: number, fade: number) => {
      for (let bi = 0; bi < words.length; bi++) {
        for (const w of words[bi]) {
          const [a, blur, dy, g] = look(bi, w, e, reduced);
          const alpha = a * fade;
          if (alpha < 0.004) continue;
          const font = w.italic ? italic : regular;
          const y = w.baseline + dy;
          if (g > 0.01) {
            paint.setColor(Float32Array.of(1, 1, 1, 0.6 * g * alpha));
            paint.setMaskFilter(glow);
            canvas.drawText(w.text, w.x, y, paint, font);
          }
          paint.setColor(
            Float32Array.of(SETTLED[0] + (1 - SETTLED[0]) * g, SETTLED[1] + (1 - SETTLED[1]) * g, SETTLED[2] + (1 - SETTLED[2]) * g, alpha),
          );
          if (blur > 0.05) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
          else paint.setMaskFilter(null);
          canvas.drawText(w.text, w.x, y, paint, font);
          paint.setMaskFilter(null);
        }
      }
    };
    const out = outgoing(m, now);
    if (out) draw(out[0], out[1]);
    draw(loopTime(m, now, reduced), 1);
    return recorder.finishRecordingAsPicture();
  });

  const chromeNow = useDerivedValue(() => chromeAt(m, m.clock.get(), reduced));
  const fill0 = useAnimatedStyle(() => ({ width: `${chromeNow.get()[0] * 100}%` }));
  const fill1 = useAnimatedStyle(() => ({ width: `${chromeNow.get()[1] * 100}%` }));
  const fill2 = useAnimatedStyle(() => ({ width: `${chromeNow.get()[2] * 100}%` }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: chromeNow.get()[3] }));
  const pillStyle = useAnimatedStyle(() => ({ opacity: chromeNow.get()[4] }));
  const fills = [fill0, fill1, fill2];

  // The pill answers only once it is shown; before that a touch there is a tap on the screen.
  const [pillActive, setPillActive] = useState(false);
  useAnimatedReaction(
    () => chromeNow.get()[4] > 0.9,
    (active, previous) => {
      if (active !== previous) scheduleOnRN(setPillActive, active);
    },
  );

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const bottom = Math.max(insets.bottom, 20);
  const last = beats[beats.length - 1].replace(/\*/g, '').replace(/\n/g, ' ');

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={last}>
          {words ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View style={[styles.segments, { top: insets.top + 14 }]} pointerEvents="none">
        {fills.map((style, i) => (
          <View key={i} style={styles.track}>
            <Animated.View style={[styles.fill, style]} />
          </View>
        ))}
      </View>

      <Animated.Text style={[styles.hint, { top: size.height - bottom - 16 - 28 - 9 }, hintStyle]} pointerEvents="none">
        {hint}
      </Animated.Text>

      <Animated.View
        style={[styles.pillBox, { top: size.height - bottom - 16 - 56, width: size.width - 64 }, pillStyle]}
        pointerEvents={pillActive ? 'auto' : 'none'}
      >
        <Pressable onPress={onAction} accessibilityRole="button" style={styles.pill}>
          <Text style={styles.pillText}>{action}</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000' },
  segments: { position: 'absolute', left: 32, right: 32, flexDirection: 'row', gap: 8 },
  track: { flex: 1, height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.16)', overflow: 'hidden' },
  fill: { height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.85)' },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: 13,
    fontWeight: '500',
    letterSpacing: 0.3,
    color: '#8A857D',
  },
  pillBox: { position: 'absolute', left: 32, height: 56 },
  pill: { flex: 1, borderRadius: 28, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  pillText: { fontSize: 16, fontWeight: '600', color: '#000' },
});
