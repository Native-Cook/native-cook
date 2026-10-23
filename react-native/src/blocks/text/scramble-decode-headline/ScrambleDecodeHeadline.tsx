import { Canvas, Picture, Skia, TileMode, type SkFont, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Scramble Decode Headline — a release-notes hero whose headline keeps decoding itself. A port of
 the SwiftUI block (ScrambleDecodeHeadline.swift); the timeline is the same:

 Letters are numbered left to right, line by line, spaces left out (k = 0…26 for the default copy
 "Always / shipping / something / new."). A window of letters scrambles: letter j of the window
 turns into violet glyphs 0.014·j s after the window starts, flickers to a new seeded glyph every
 0.05 s, and settles back into its white letter `hold + settle·j` s after the start, so the
 decode sweeps left to right. A settled letter cools from violet to white over 0.15 s. The violet
 glow behind the headline follows the share of letters that are scrambled. A scrambled glyph
 wider than 1.5 times its letter's slot is scaled down towards that width, to 72 % at most.

 autoPlay timeline (LOOP = 8 s):
   0.00         clean headline (also the first look).
   0.50         "Always" (k 0–5)            hold 0.32 s, settle 0.055 s per letter.
   1.60         "ething" (k 17–22)          same.
   2.70         "ng" + "new." (k 21–26)     same, across a line break.
   3.60, 3.75   "ipping" (k 8–13), then "som" (k 14–16): two windows overlapping.
   5.00         the whole headline (k 0–26) hold 0.35 s, settle 0.045 s per letter → clean at 6.52.
   6.52 … 8.00  clean, identical to t = 0.

 Touch: a tap scrambles the whole headline at once and decodes it (hold 0.35 s, settle 0.045 s).
 Dragging over the headline scrambles the letters within 36 pt of the finger; each settles 0.35 s
 after the finger leaves it. 2 s after the last touch the loop starts again from its clean frame.
 Reduced motion: no loop and no scrambling; the headline stays clean.
 */

export type ScrambleDecodeHeadlineWindow = {
  /** Seconds into the loop. */
  start: number;
  /** First and last letter, counted without spaces. */
  first: number;
  last: number;
  hold?: number;
  settle?: number;
};

const DEFAULT_WINDOWS: ScrambleDecodeHeadlineWindow[] = [
  { start: 0.5, first: 0, last: 5 },
  { start: 1.6, first: 17, last: 22 },
  { start: 2.7, first: 21, last: 26 },
  { start: 3.6, first: 8, last: 13 },
  { start: 3.75, first: 14, last: 16 },
  { start: 5.0, first: 0, last: 26, hold: 0.35, settle: 0.045 },
];

const LOOP = 8;
const FLICKER = 0.05;
const COOL = 0.15;
const ONSET = 0.014;
const HOLD = 0.32;
const SETTLE = 0.055;
const SCRUB_RADIUS = 36;
const SCRUB_LINGER = 0.35;
const MAX_SIZE = 76;
const TRACKING = -0.03; // em
const LEADING = 1.0; // line height in em
const MEASURE = 100; // the Skia font's size; drawing scales it to the layout's size
const CHARSET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&!?*+=<>/';
const FONT_NAME = 'ScrambleDecodeHeadline-SpaceGrotesk-Medium';
const FONT_FILE = require('./assets/SpaceGrotesk-Medium.ttf');

const BACKGROUND = '#101114';
const INK = '#F2F2F2';
const VIOLET = '#7C5CFF';
const INK_RGB = [0xf2 / 255, 0xf2 / 255, 0xf2 / 255];
const VIOLET_RGB = [0x7c / 255, 0x5c / 255, 0xff / 255];

type Slot = { char: string; cx: number; cy: number; width: number };
type Layout = { fontSize: number; slots: Slot[]; top: number; bottom: number; centerY: number; widths: Record<string, number> };

/** Where every letter of the headline sits: the font size that fits the longest line, and each letter's slot. */
function buildLayout(lines: string[], font: SkFont, width: number, height: number): Layout {
  // Advances of each character at 1 pt, measured one by one (no kerning, as in every stack).
  const advance = (ch: string) => font.getGlyphWidths(font.getGlyphIDs(ch))[0] / MEASURE;
  const advances = lines.map((line) => Array.from(line, advance));
  const unitWidths = advances.map((a) => a.reduce((x, y) => x + y, 0) + TRACKING * Math.max(0, a.length - 1));
  const size = Math.min(MAX_SIZE, (width - 48) / Math.max(...unitWidths));
  const lineHeight = size * LEADING;
  const blockHeight = lineHeight * lines.length;
  const centerY = height * 0.43;
  const top = centerY - blockHeight / 2;
  const slots: Slot[] = [];
  lines.forEach((line, li) => {
    let x = 24;
    const y = top + lineHeight * (li + 0.5);
    Array.from(line).forEach((ch, ci) => {
      const w = advances[li][ci] * size;
      if (ch !== ' ') slots.push({ char: ch, cx: x + w / 2, cy: y, width: w });
      x += w + TRACKING * size;
    });
  });
  const widths: Record<string, number> = {};
  for (const ch of new Set([...CHARSET, ...lines.join('')])) widths[ch] = advance(ch);
  return { fontSize: size, slots, top, bottom: top + blockHeight, centerY, widths };
}

/** A seeded number in 0..<1 (the same 32-bit integer hash in every stack). */
function hash(i: number, salt: number) {
  'worklet';
  let x = (Math.imul(i, 374761393) + Math.imul(salt, 668265263)) >>> 0;
  x = Math.imul(x ^ (x >>> 13), 1274126177) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return x / 4294967296;
}

/** Letter k under a set of windows, `e` seconds into their timeline: [scrambled, settledAgo]. */
function windowState(k: number, windows: ScrambleDecodeHeadlineWindow[], e: number): [boolean, number] {
  'worklet';
  let settledAgo = Infinity;
  for (const w of windows) {
    if (k < w.first || k > w.last) continue;
    const j = k - w.first;
    const on = w.start + j * ONSET;
    const off = w.start + (w.hold ?? HOLD) + j * (w.settle ?? SETTLE);
    if (e >= on && e < off) return [true, 0];
    if (e >= off) settledAgo = Math.min(settledAgo, e - off);
  }
  return [false, settledAgo];
}

const TAP_WINDOW: ScrambleDecodeHeadlineWindow[] = [{ start: 0, first: 0, last: 1e9, hold: 0.35, settle: 0.045 }];

type Motion = {
  clock: SharedValue<number>;
  /** The loop's t = 0 on the frame clock, or -1. */
  autoStart: SharedValue<number>;
  /** A tap's whole-headline decode, or -1. */
  tapAt: SharedValue<number>;
  /** When each letter was last under the finger (-1: never). */
  touched: SharedValue<number[]>;
  fingerX: SharedValue<number>;
  fingerY: SharedValue<number>;
  /** 1 while a drag is on the headline. */
  scrubbing: SharedValue<number>;
  moved: SharedValue<number>;
  resumeAt: SharedValue<number>;
};

function tactile(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = {
  lines?: string[];
  badge?: string;
  subtitle?: string;
  action?: string;
  windows?: ScrambleDecodeHeadlineWindow[];
  autoPlay?: boolean;
  onAction?: () => void;
};

export function ScrambleDecodeHeadline({
  lines = ['Always', 'shipping', 'something', 'new.'],
  badge = 'Release notes · v2.4',
  subtitle = 'Small, careful improvements every Friday, to the tools your team opens every morning.',
  action = "Read what's new",
  windows = DEFAULT_WINDOWS,
  autoPlay = false,
  onAction,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const font = useFont(FONT_FILE, MEASURE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const layout = useMemo(
    () => (font && size.width > 0 ? buildLayout(lines, font, size.width, size.height) : null),
    [font, size.width, size.height, lines],
  );

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    tapAt: useSharedValue(-1),
    touched: useSharedValue<number[]>([]),
    fingerX: useSharedValue(0),
    fingerY: useSharedValue(0),
    scrubbing: useSharedValue(0),
    moved: useSharedValue(0),
    resumeAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      m.tapAt.set(-1);
      m.touched.set([]);
      m.autoStart.set(now);
    }
  }, !reduced);

  const slots = layout?.slots;
  const gesture = useMemo(() => {
    const list = slots ?? [];
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .enabled(!reduced)
      .onBegin(() => {
        m.resumeAt.set(-1);
        m.autoStart.set(-1);
        m.moved.set(0);
      })
      .onUpdate((e) => {
        if (m.moved.get() === 0 && Math.hypot(e.translationX, e.translationY) > 6) {
          m.moved.set(1);
          scheduleOnRN(tactile, false);
        }
        if (m.moved.get() === 0) return;
        m.scrubbing.set(1);
        m.fingerX.set(e.x);
        m.fingerY.set(e.y);
        const now = m.clock.get();
        const touched = m.touched.get().slice();
        for (let k = 0; k < list.length; k++) {
          if (Math.hypot(list[k].cx - e.x, list[k].cy - e.y) < SCRUB_RADIUS) touched[k] = now;
        }
        m.touched.set(touched);
      })
      .onFinalize(() => {
        const now = m.clock.get();
        if (m.moved.get() === 0) {
          m.tapAt.set(now);
          scheduleOnRN(tactile, true);
        }
        m.scrubbing.set(0);
        if (autoPlay) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slots, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!layout || !font) return recorder.finishRecordingAsPicture();
    const now = m.clock.get();
    const auto = m.autoStart.get();
    const autoActive = !reduced && auto >= 0 && now >= auto;
    const e = autoActive ? (now - auto) % LOOP : 0;
    const step = autoActive ? Math.floor(e / FLICKER) : Math.floor(now / FLICKER);
    const tapAt = m.tapAt.get();
    const touched = m.touched.get();
    const scrubbing = m.scrubbing.get() === 1;
    const fx = m.fingerX.get();
    const fy = m.fingerY.get();

    // Each letter's state: scrambled or not, and how long ago it settled.
    const n = layout.slots.length;
    const scrambled: boolean[] = [];
    const settled: number[] = [];
    let count = 0;
    for (let k = 0; k < n; k++) {
      let s: [boolean, number] = [false, Infinity];
      if (reduced) s = [false, Infinity];
      else if (autoActive) s = windowState(k, windows, e);
      else {
        if (tapAt >= 0) s = windowState(k, TAP_WINDOW, now - tapAt);
        if (!s[0]) {
          const slot = layout.slots[k];
          if (scrubbing && Math.hypot(slot.cx - fx, slot.cy - fy) < SCRUB_RADIUS) s = [true, 0];
          else if (touched[k] !== undefined && touched[k] >= 0) {
            const ago = now - touched[k] - SCRUB_LINGER;
            s = ago < 0 ? [true, 0] : [false, Math.min(s[1], ago)];
          }
        }
      }
      scrambled.push(s[0]);
      settled.push(s[1]);
      if (s[0]) count++;
    }
    const activity = count / Math.max(1, n);

    // The glow behind the headline breathes with how much of it is scrambled.
    const glow = Skia.Paint();
    glow.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: size.width * 0.3, y: layout.centerY },
        size.width * 0.95,
        [Float32Array.of(VIOLET_RGB[0], VIOLET_RGB[1], VIOLET_RGB[2], 0.1 + 0.22 * activity), Float32Array.of(VIOLET_RGB[0], VIOLET_RGB[1], VIOLET_RGB[2], 0)],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, size.width, size.height), glow);

    const paint = Skia.Paint();
    const scale = layout.fontSize / MEASURE;
    // The text box's centre sits 0.346 em above the baseline (ascent 0.984, descent 0.292 em).
    const drop = 0.346 * layout.fontSize;
    for (let k = 0; k < n; k++) {
      const slot = layout.slots[k];
      let ch = slot.char;
      if (scrambled[k]) {
        ch = CHARSET[Math.floor(hash(k, step) * CHARSET.length)];
        paint.setColor(Float32Array.of(VIOLET_RGB[0], VIOLET_RGB[1], VIOLET_RGB[2], 1));
      } else {
        const t = Math.min(1, settled[k] / COOL);
        paint.setColor(
          Float32Array.of(
            VIOLET_RGB[0] + (INK_RGB[0] - VIOLET_RGB[0]) * t,
            VIOLET_RGB[1] + (INK_RGB[1] - VIOLET_RGB[1]) * t,
            VIOLET_RGB[2] + (INK_RGB[2] - VIOLET_RGB[2]) * t,
            1,
          ),
        );
      }
      const w = (layout.widths[ch] ?? 0) * layout.fontSize;
      // A glyph much wider than its letter's slot is shrunk towards 1.5 slot widths (to 72 % at most), so it hardly runs into its neighbours.
      const fit = scrambled[k] ? Math.max(0.72, Math.min(1, (slot.width * 1.5) / Math.max(1, w))) : 1;
      canvas.save();
      canvas.translate(slot.cx, slot.cy);
      canvas.scale(fit, fit);
      canvas.translate(-w / 2, drop);
      canvas.scale(scale, scale);
      canvas.drawText(ch, 0, 0, paint, font);
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {fontsLoaded && layout ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="header" accessibilityLabel={lines.join(' ')}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View style={[styles.badge, { top: layout.top - 28 - 24 }]} pointerEvents="none">
            <View style={styles.dot} />
            <Text style={styles.badgeText} numberOfLines={1}>
              {badge}
            </Text>
          </View>

          <Text style={[styles.subtitle, { top: layout.bottom + 24, width: Math.min(size.width - 48, 320) }]} pointerEvents="none">
            {subtitle}
          </Text>

          <Pressable
            onPress={onAction}
            accessibilityRole="button"
            style={[styles.button, { top: size.height - Math.max(insets.bottom, 20) - 16 - 56, width: size.width - 48 }]}
          >
            <Text style={styles.buttonText}>{action}</Text>
            {/* A right arrow, 15 pt, like the SF Symbol on iOS. */}
            <Svg width={15} height={15} viewBox="0 0 15 15">
              <Path d="M1.5 7.5H13.5M8.5 2.5L13.5 7.5L8.5 12.5" stroke={BACKGROUND} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </Svg>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  badge: {
    position: 'absolute',
    left: 24,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: 'rgba(242,242,242,0.06)',
    borderWidth: 1,
    borderColor: 'rgba(242,242,242,0.10)',
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: VIOLET },
  badgeText: { fontFamily: FONT_NAME, fontSize: 13, color: 'rgba(242,242,242,0.75)' },
  subtitle: { position: 'absolute', left: 24, fontFamily: FONT_NAME, fontSize: 17, lineHeight: 25.7, color: 'rgba(242,242,242,0.55)' },
  button: {
    position: 'absolute',
    left: 24,
    height: 56,
    borderRadius: 28,
    backgroundColor: INK,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  buttonText: { fontFamily: FONT_NAME, fontSize: 17, color: BACKGROUND },
});
