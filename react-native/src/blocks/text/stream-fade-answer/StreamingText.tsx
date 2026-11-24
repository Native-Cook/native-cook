import {
  BlendMode,
  BlurStyle,
  Canvas,
  Picture,
  Skia,
  TileMode,
  matchFont,
  type SkFont,
  type SkMaskFilter,
  type SkPicture,
  type SkRect,
  type SkTextBlob,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { Platform, type StyleProp, View, type ViewStyle } from 'react-native';
import { type SharedValue, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';

import { BLUR_LEVELS, FEATHER, WORD_BLUR, WORD_DUR, WORD_RATE, WORD_RISE, easeOutCubic, easeOutQuad, wordStarts } from './streamFadeMotion';

/*
 StreamingText — text that arrives word by word, the way an assistant's answer streams in.

 The paragraph is laid out once, in full: every word is measured (glyph advances of the system
 font) and wrapped into lines before anything shows, so a word never moves sideways or pushes
 another when it appears. Only three things animate per word: opacity 0 → 1, a blur clearing
 and a rise of a few points. Whatever the length of the text, one Canvas draws it from one
 derived value: settled lines are single text blobs, and only the dozen or so words still
 arriving are drawn one by one, so there is no animated view per word.

 The newest line also fades toward the front of the stream (a horizontal gradient mask that
 runs ahead with the words), which softens the leading edge without a caret.

 Drive it with a shared clock (`time`, in seconds, plus `start` for this text's place on it) to
 scrub, pause or chain several texts, or leave `time` out and it plays once when it mounts.
*/

type Weight = '400' | '500' | '600' | '700';

export type StreamingTextProps = {
  text: string;
  /** Width to wrap in, pt. The height follows from the text (see measureStreamingText). */
  width: number;
  /** Words per second. The default is one word every 28 ms. */
  rate?: number;
  /** A shared clock in seconds. Without it the text plays by itself once. */
  time?: SharedValue<number>;
  /** When on the clock this text starts. */
  start?: number;
  fontSize?: number;
  fontWeight?: Weight;
  lineHeight?: number;
  color?: string;
  /** Varies the rhythm between texts on one screen. */
  seed?: number;
  /** Show the whole text at once (static text, or reduced motion). */
  settled?: boolean;
  style?: StyleProp<ViewStyle>;
};

type Options = { width: number; rate: number; fontSize: number; fontWeight: Weight; lineHeight: number; color: string; seed: number };

/** Room around the text for the blur and the rise, so neither is clipped by the canvas. */
const PAD = 12;
const FAMILY = Platform.OS === 'ios' ? 'System' : 'sans-serif';

type StreamLayout = {
  width: number;
  height: number;
  lineHeight: number;
  lineCount: number;
  color: string;
  bounds: SkRect;
  /** Where text starts inside the canvas, and the first baseline below a line's top. */
  left: number;
  firstBase: number;
  /** Per word: pen x, baseline y, the line it is on, when it starts (s), its glyphs. */
  xs: number[];
  ys: number[];
  lineOf: number[];
  starts: number[];
  blobs: SkTextBlob[];
  /** Per line: its text as one blob, its baseline and its first word. */
  lineBlobs: SkTextBlob[];
  lineBase: number[];
  lineFirst: number[];
  /** Width of the longest line, pt. */
  widest: number;
  /** How fast the front of the stream runs along a line, pt/s. */
  frontSpeed: number;
  /** When the last word has arrived, seconds after the first starts. */
  duration: number;
  filters: SkMaskFilter[];
  full: SkPicture;
  empty: SkPicture;
};

const fonts = new Map<string, SkFont>();

function fontFor(size: number, weight: Weight) {
  const key = `${size}/${weight}`;
  let font = fonts.get(key);
  if (!font) {
    font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
    fonts.set(key, font);
  }
  return font;
}

function advance(font: SkFont, text: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let total = 0;
  for (let i = 0; i < widths.length; i += 1) total += widths[i];
  return total;
}

let blurFilters: SkMaskFilter[] | null = null;

function filters() {
  if (!blurFilters) {
    blurFilters = [];
    for (let k = 0; k <= BLUR_LEVELS; k += 1) blurFilters.push(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(0.01, (WORD_BLUR * k) / BLUR_LEVELS), true));
  }
  return blurFilters;
}

const layouts = new Map<string, StreamLayout>();

/** Wraps and measures the text once. A line break in the text starts a new line. */
function makeStreamLayout(text: string, o: Options): StreamLayout {
  const key = `${text}|${o.width}|${o.rate}|${o.fontSize}|${o.fontWeight}|${o.lineHeight}|${o.color}|${o.seed}`;
  const cached = layouts.get(key);
  if (cached) return cached;

  const font = fontFor(o.fontSize, o.fontWeight);
  const metrics = font.getMetrics();
  const space = advance(font, ' ');
  // The baseline sits where a native Text of this line height would put it.
  const firstBase = (o.lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;

  const xs: number[] = [];
  const ys: number[] = [];
  const lineOf: number[] = [];
  const blobs: SkTextBlob[] = [];
  const lineText: string[] = [];
  const lineFirst: number[] = [];
  let line = 0;
  let travelled = 0;
  let widest = 0;
  const paragraphs = text.split('\n');
  for (let p = 0; p < paragraphs.length; p += 1) {
    const words = paragraphs[p].split(' ').filter(Boolean);
    let x = 0;
    for (let w = 0; w < words.length; w += 1) {
      const wide = advance(font, words[w]);
      if (x > 0 && x + wide > o.width) {
        line += 1;
        x = 0;
      }
      if (lineText[line] === undefined) {
        lineText[line] = words[w];
        lineFirst[line] = xs.length;
      } else {
        lineText[line] += ` ${words[w]}`;
      }
      xs.push(PAD + x);
      ys.push(PAD + firstBase + line * o.lineHeight);
      lineOf.push(line);
      blobs.push(Skia.TextBlob.MakeFromText(words[w], font));
      widest = Math.max(widest, x + wide);
      x += wide + space;
      travelled += wide + space;
    }
    if (words.length > 0) line += 1;
  }
  const lineCount = Math.max(1, line);
  const lineBlobs: SkTextBlob[] = [];
  const lineBase: number[] = [];
  for (let l = 0; l < lineCount; l += 1) {
    lineBlobs.push(Skia.TextBlob.MakeFromText(lineText[l] ?? ' ', font));
    lineBase.push(PAD + firstBase + l * o.lineHeight);
    if (lineFirst[l] === undefined) lineFirst[l] = xs.length;
  }

  const starts = wordStarts(xs.length, o.rate, o.seed);
  const height = lineCount * o.lineHeight;
  const bounds = Skia.XYWHRect(0, 0, o.width + PAD * 2, height + PAD * 2);

  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(bounds);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(o.color));
  for (let l = 0; l < lineCount; l += 1) canvas.drawTextBlob(lineBlobs[l], PAD, lineBase[l], ink);
  const full = recorder.finishRecordingAsPicture();
  const blank = Skia.PictureRecorder();
  blank.beginRecording(bounds);
  const empty = blank.finishRecordingAsPicture();

  const made: StreamLayout = {
    width: o.width,
    height,
    lineHeight: o.lineHeight,
    lineCount,
    color: o.color,
    bounds,
    left: PAD,
    firstBase,
    xs,
    ys,
    lineOf,
    starts,
    blobs,
    lineBlobs,
    lineBase,
    lineFirst,
    widest,
    frontSpeed: xs.length > 0 ? (travelled / xs.length) * o.rate : 0,
    duration: (xs.length > 0 ? starts[xs.length - 1] : 0) + WORD_DUR,
    filters: filters(),
    full,
    empty,
  };
  layouts.set(key, made);
  return made;
}

function withDefaults(o: Partial<Options> & { width: number }): Options {
  const fontSize = o.fontSize ?? 16;
  return {
    width: o.width,
    rate: o.rate ?? WORD_RATE,
    fontSize,
    fontWeight: o.fontWeight ?? '400',
    lineHeight: o.lineHeight ?? Math.round(fontSize * 1.42),
    color: o.color ?? '#1d1c1a',
    seed: o.seed ?? 0,
  };
}

/**
 The size and timing of a StreamingText before it is rendered: its height (the layout is final
 from the start), how many lines and words, and `duration`, the seconds from its first word
 starting to its last word having arrived, and `widest`, its longest line. `lastStart` is when the last word begins, which is
 where the next text on the same clock can follow.
*/
export function measureStreamingText(text: string, options: Partial<Options> & { width: number }) {
  const L = makeStreamLayout(text, withDefaults(options));
  return { height: L.height, lines: L.lineCount, words: L.xs.length, widest: L.widest, duration: L.duration, lastStart: L.duration - WORD_DUR };
}

/** The text at stream time s (seconds since its first word started). */
function streamPicture(L: StreamLayout, s: number) {
  'worklet';
  const n = L.starts.length;
  if (s <= 0 || n === 0) return L.empty;
  // Past the last word the front still has to run off the line before the mask is gone.
  if (s >= L.duration + 0.12) return L.full;

  // Words [0, a) have arrived, [a, b) are arriving, the rest have not started.
  let a = 0;
  while (a < n && s - L.starts[a] >= WORD_DUR) a += 1;
  let b = a;
  while (b < n && L.starts[b] < s) b += 1;

  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(L.bounds);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(L.color));
  const base = ink.getAlphaf();

  const openLine = a < n ? L.lineOf[a] : L.lineCount;
  for (let l = 0; l < openLine; l += 1) canvas.drawTextBlob(L.lineBlobs[l], L.left, L.lineBase[l], ink);
  if (a < n) {
    for (let w = L.lineFirst[openLine]; w < a; w += 1) canvas.drawTextBlob(L.blobs[w], L.xs[w], L.ys[w], ink);
  }

  if (b > a) {
    canvas.saveLayer();
    for (let w = a; w < b; w += 1) {
      const q = (s - L.starts[w]) / WORD_DUR;
      const e = easeOutCubic(q);
      const level = Math.round((1 - e) * BLUR_LEVELS);
      ink.setAlphaf(base * easeOutQuad(q));
      ink.setMaskFilter(level > 0 ? L.filters[level] : null);
      canvas.drawTextBlob(L.blobs[w], L.xs[w], L.ys[w] + WORD_RISE * (1 - e), ink);
    }
    // The newest line fades toward the front, which keeps running at the pace of the stream.
    const k = b - 1;
    const front = L.xs[k] + (s - L.starts[k]) * L.frontSpeed;
    const mask = Skia.Paint();
    mask.setBlendMode(BlendMode.DstIn);
    mask.setShader(
      Skia.Shader.MakeLinearGradient({ x: front - FEATHER, y: 0 }, { x: front + 8, y: 0 }, [Skia.Color('rgba(0,0,0,1)'), Skia.Color('rgba(0,0,0,0)')], null, TileMode.Clamp),
    );
    // Only this line's box (and the rise under it): the line above keeps its descenders.
    const top = L.lineBase[L.lineOf[k]] - L.firstBase;
    canvas.drawRect(Skia.XYWHRect(0, top, L.bounds.width, L.lineHeight + WORD_RISE + 4), mask);
    canvas.restore();
  }
  return recorder.finishRecordingAsPicture();
}

export function StreamingText(props: StreamingTextProps) {
  const { text, time, start = 0, settled = false, style } = props;
  const reduced = useReducedMotion();
  const o = withDefaults(props);
  const L = useMemo(
    () => makeStreamLayout(text, { width: o.width, rate: o.rate, fontSize: o.fontSize, fontWeight: o.fontWeight, lineHeight: o.lineHeight, color: o.color, seed: o.seed }),
    [text, o.width, o.rate, o.fontSize, o.fontWeight, o.lineHeight, o.color, o.seed],
  );
  const whole = settled || reduced;

  // Its own clock, used only when no shared one is given.
  const own = useSharedValue(0);
  useFrameCallback((f) => {
    if (own.get() > L.duration + 0.2) return;
    own.set(own.get() + Math.min(f.timeSincePreviousFrame ?? 16, 50) / 1000);
  }, time === undefined && !whole);

  const picture = useDerivedValue(() => {
    if (whole) return L.full;
    return streamPicture(L, (time ? time.get() : own.get()) - start);
  });

  return (
    <View accessible accessibilityRole="text" accessibilityLabel={text} style={[{ width: L.width, height: L.height }, style]}>
      <Canvas pointerEvents="none" style={{ position: 'absolute', left: -PAD, top: -PAD, width: L.width + PAD * 2, height: L.height + PAD * 2 }}>
        <Picture picture={picture} />
      </Canvas>
    </View>
  );
}
