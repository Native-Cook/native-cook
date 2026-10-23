import { BlendMode, BlurStyle, Canvas, Picture, Skia, TileMode, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
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

import {
  CHORUS,
  GAP,
  type KaraokeLayout,
  type KaraokeWord,
  LOOP,
  REWIND,
  ROW_HEIGHT,
  SIZE,
  SLOT,
  SONG,
  buildLayout,
  easeInOut,
  easeOutBack,
  lineAt,
  lineBlur,
  lineTop,
  offsetOf,
  playheadAt,
  positionOf,
  scrollAt,
  singingLine,
  smoothstep,
  sungAlpha,
  unsungAlpha,
  wordFill,
  wordLift,
} from './karaoke-model';

/*
 Karaoke Lyrics Highlight — a "now playing" lyrics screen that sings its chorus word by word. A
 port of the SwiftUI block (KaraokeLyricsHighlight.swift); the numbers are the same.

 Look: an invented song ("Paper Satellites" by Lumen Fair) on a drifting violet–magenta field.
 Big left-aligned lines in Poppins Bold 32 pt (rows 38 pt, 22 pt between lines) fill the screen;
 the line being sung sits with its top at 30 % of the screen height. A header (art, title,
 artist, a small live waveform) sits at the top and a slim scrubber with times at the bottom;
 the lyrics fade out under both.

 Per line, by its distance d from the focus (negative: already sung):
   sung ink   white × 1 at the focus, down to 0.25 one line above;
   unsung ink white × 0.35 at the focus, 0.25 one line below, 0.15 two below;
   blur       2.5 one line above, 1.5 per line below (up to 3), as a Gaussian sigma.
 A word fills left to right with white: a soft 16 pt edge, and a glow (sigma 5) on the 28 pt
 behind the edge. A word being sung lifts 2 pt and grows 3 % (in 0.12 s, out 0.2 s); the held
 "Ohhh," fills over 0.95 s and its letters wave (2.5 pt, 1.6 Hz, 0.7 rad apart).

 autoPlay timeline (LOOP = 10 s, see karaoke-model.ts for the word times):
   0.0 / 2.5 / 5.0 / 7.5   a chorus line starts; its words fill over the next ~2 s.
   +1.95 … +2.5            the list scrolls one line up (ease-out-back, small overshoot).
   9.5 … 10.0              the playhead glides from 1:21 back to 1:12 (the chorus loops).
   10.0                    one chorus further down: the same frame as t = 0.
 The background blobs drift once per loop and the header waveform's bars move a whole number of
 times per loop.

 Touch: dragging scrolls the lyrics freely; the fill pauses and the lines un-blur while the finger
 is down (0.25 s), and blur again after release (0.4 s). Tapping a line jumps the song to it: the
 loop carries on from that line, the list gliding there (0.55 s, ease-out-back) and its fill
 restarting. 2 s after a drag, the list eases back to the chorus's first line over 0.6 s (the
 fill rewinds with it) and the loop starts again.
 Reduced motion: no loop, no drift, no lift or wave; the first line shows fully sung. Dragging
 still scrolls and a tap moves the focus to a line, sung at once.
 */

const FONT_NAME = 'KaraokeLyricsHighlight-Poppins-Bold';
const FONT_FILE = require('./assets/Poppins-Bold.ttf');

/** What the screen shows at a moment. */
type Shown = {
  scroll: number;
  /** The line being sung and how far into its slot; while returning, blended towards restLine. */
  line: number;
  tau: number;
  restLine: number;
  rest: number;
  playhead: number;
  /** Loop time for the drift and the waveform. */
  clock: number;
  /** 1 normally, 0 while a finger is dragging the lyrics. */
  blurScale: number;
};

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  origin: SharedValue<number>;
  correctionAt: SharedValue<number>;
  correctionLines: SharedValue<number>;
  manualOffset: SharedValue<number>;
  frozenLine: SharedValue<number>;
  frozenTau: SharedValue<number>;
  frozenPlayhead: SharedValue<number>;
  /** The ease back: [when, from scroll, from playhead], or []. */
  returning: SharedValue<number[]>;
  pressedAt: SharedValue<number>;
  releasedAt: SharedValue<number>;
  resumeAt: SharedValue<number>;
  dragStart: SharedValue<number>;
  moved: SharedValue<number>;
};

function shownAt(m: Motion, at: number, layout: KaraokeLayout, reduced: boolean): Shown {
  'worklet';
  let blurScale = 1;
  const pressed = m.pressedAt.get();
  if (pressed >= 0) {
    blurScale = 1 - smoothstep(0, 0.25, at - pressed);
    const released = m.releasedAt.get();
    if (released >= 0) blurScale = Math.max(blurScale, smoothstep(0, 0.4, at - released));
  }
  const start = m.autoStart.get();
  if (start >= 0 && at >= start && !reduced) {
    const t = at - start;
    const cycles = Math.floor(t / LOOP);
    const e = t - cycles * LOOP;
    const base = m.origin.get() + 4 * cycles;
    let s = base + scrollAt(e);
    const c = m.correctionAt.get();
    if (c >= 0) {
      const u = (at - c) / 0.55;
      if (u < 1) s += m.correctionLines.get() * (1 - easeOutBack(u));
    }
    const k = singingLine(e);
    return { scroll: s, line: base + k, tau: e - k * SLOT, restLine: base + k, rest: 0, playhead: playheadAt(e), clock: e, blurScale };
  }
  const r = m.returning.get();
  if (r.length === 3) {
    const u = easeInOut((at - r[0]) / 0.6);
    const origin = m.origin.get();
    return {
      scroll: r[1] + (origin - r[1]) * u,
      line: m.frozenLine.get(),
      tau: m.frozenTau.get(),
      restLine: origin,
      rest: u,
      playhead: r[2] + (SONG.chorusAt - r[2]) * u,
      clock: 0,
      blurScale,
    };
  }
  return {
    scroll: positionOf(layout, m.manualOffset.get()),
    line: m.frozenLine.get(),
    tau: m.frozenTau.get(),
    restLine: m.frozenLine.get(),
    rest: 0,
    playhead: m.frozenPlayhead.get(),
    clock: 0,
    blurScale,
  };
}

/** How much word w of absolute line i is filled. */
function fillOf(i: number, w: KaraokeWord, s: Shown) {
  'worklet';
  const base = (line: number, tau: number) => (i < line ? 1 : i > line ? 0 : wordFill(w, tau));
  const a = base(s.line, s.tau);
  if (s.rest <= 0) return a;
  return a + (base(s.restLine, 0) - a) * s.rest;
}

function time(seconds: number) {
  'worklet';
  const s = Math.floor(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function tactile(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function KaraokeLyricsHighlight({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const font = useFont(FONT_FILE, SIZE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [times, setTimes] = useState([time(SONG.chorusAt), `-${time(SONG.length - SONG.chorusAt)}`]);
  const layout = useMemo(() => (font && size.width > 0 ? buildLayout(size.width, font) : null), [font, size.width]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    origin: useSharedValue(0),
    correctionAt: useSharedValue(-1),
    correctionLines: useSharedValue(0),
    manualOffset: useSharedValue(0),
    frozenLine: useSharedValue(0),
    frozenTau: useSharedValue(reduced ? SLOT : 0),
    frozenPlayhead: useSharedValue(SONG.chorusAt),
    returning: useSharedValue<number[]>([]),
    pressedAt: useSharedValue(-1),
    releasedAt: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
    dragStart: useSharedValue(0),
    moved: useSharedValue(0),
  };

  const headerBottom = insets.top + 12 + 48;
  const scrubTop = size.height - Math.max(insets.bottom, 20) - 44;
  const focus = size.height * 0.3;

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.clock.set(now);
    // 2 s after a drag: ease back to the chorus's first line over 0.6 s, then the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt && layout) {
      m.resumeAt.set(-1);
      const s = positionOf(layout, m.manualOffset.get());
      m.origin.set(Math.round(s / 4) * 4);
      m.returning.set([now, s, m.frozenPlayhead.get()]);
      m.autoStart.set(now + 0.6);
      m.correctionAt.set(-1);
    }
  });

  const shown = useDerivedValue(() => (layout ? shownAt(m, m.clock.get(), layout, reduced) : null));

  // The scrubber's times change once a second; they are text on the JS side.
  // Both are whole seconds rounded down, as SwiftUI's Int(): the time left reads 2:29 at 1:13.4.
  useAnimatedReaction(
    () => {
      const s = shown.get();
      return s ? Math.floor(s.playhead) * 10000 + Math.floor(SONG.length - s.playhead) : -1;
    },
    (key, previous) => {
      if (key >= 0 && key !== previous) scheduleOnRN(setTimes, [time(Math.floor(key / 10000)), `-${time(key % 10000)}`]);
    },
  );

  const gesture = useMemo(() => {
    const takeOver = (now: number) => {
      'worklet';
      if (!layout) return;
      m.resumeAt.set(-1);
      const s = shownAt(m, now, layout, reduced);
      m.manualOffset.set(offsetOf(layout, s.scroll));
      m.frozenLine.set(s.rest >= 1 ? s.restLine : s.line);
      m.frozenTau.set(s.rest >= 1 ? 0 : s.tau);
      m.frozenPlayhead.set(s.playhead);
      m.autoStart.set(-1);
      m.returning.set([]);
      m.correctionAt.set(-1);
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin(() => {
        takeOver(m.clock.get());
        m.dragStart.set(m.manualOffset.get());
        m.moved.set(0);
        m.pressedAt.set(-1);
        m.releasedAt.set(-1);
      })
      .onUpdate((e) => {
        if (m.moved.get() === 0 && Math.hypot(e.translationX, e.translationY) > 6) {
          m.moved.set(1);
          m.pressedAt.set(m.clock.get());
          scheduleOnRN(tactile, false);
        }
        if (m.moved.get() === 1) m.manualOffset.set(m.dragStart.get() - e.translationY);
      })
      .onFinalize((e) => {
        if (!layout) return;
        const now = m.clock.get();
        if (m.moved.get() === 1) {
          m.releasedAt.set(now);
          if (autoPlay && !reduced) m.resumeAt.set(now + 2);
          return;
        }
        // Tap: jump the song to the line under the finger; the loop carries on from it.
        const s = positionOf(layout, m.manualOffset.get());
        const first = Math.floor(s) - 4;
        let hit = -1e9;
        for (let i = first; i <= first + 12; i++) {
          const top = focus + lineTop(layout, i) - offsetOf(layout, s);
          if (e.y >= top - GAP / 2 && e.y < top + lineAt(layout, i).height + GAP / 2) hit = i;
        }
        if (hit === -1e9) {
          if (autoPlay && !reduced) m.resumeAt.set(now + 2);
          return;
        }
        scheduleOnRN(tactile, true);
        const k = ((hit % 4) + 4) % 4;
        if (reduced) {
          m.manualOffset.set(offsetOf(layout, hit));
          m.frozenLine.set(hit);
          m.frozenTau.set(SLOT);
          return;
        }
        m.origin.set(hit - k);
        m.autoStart.set(now - k * SLOT);
        m.correctionLines.set(s - hit);
        m.correctionAt.set(now);
        m.pressedAt.set(-1);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layout, reduced, autoPlay, focus]);

  const background = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const w = size.width;
    const h = size.height;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = shown.get();
    const a = (2 * Math.PI * (s ? s.clock : 0)) / LOOP;
    const k = reduced ? 0 : 1;
    const paint = Skia.Paint();
    paint.setColor(Skia.Color('#12062A'));
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
    const blob = (x: number, y: number, r: number, rgb: number[], alpha: number) => {
      const p = Skia.Paint();
      p.setShader(
        Skia.Shader.MakeRadialGradient(
          { x, y },
          r,
          [Float32Array.of(rgb[0], rgb[1], rgb[2], alpha), Float32Array.of(rgb[0], rgb[1], rgb[2], 0)],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawRect(Skia.XYWHRect(0, 0, w, h), p);
    };
    blob(w * (0.25 + k * 0.08 * Math.sin(a)), h * (0.22 + k * 0.05 * Math.cos(a)), w * 0.95, [0.835, 0.227, 0.616], 0.75);
    blob(w * (0.8 + k * 0.07 * Math.cos(a)), h * (0.7 + k * 0.06 * Math.sin(a)), w * 1.0, [0.353, 0.122, 0.659], 0.9);
    blob(w * (0.92 - k * 0.05 * Math.sin(a)), h * 0.12, w * 0.6, [1.0, 0.478, 0.349], 0.35);
    const shade = Skia.Paint();
    shade.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h }, [Float32Array.of(0, 0, 0, 0.1), Float32Array.of(0, 0, 0, 0.45)], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), shade);
    return recorder.finishRecordingAsPicture();
  });

  const lyrics = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const w = size.width;
    const h = size.height;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = shown.get();
    if (!s || !layout || !font) return recorder.finishRecordingAsPicture();
    canvas.saveLayer();
    const paint = Skia.Paint();
    const mask = Skia.Paint();
    mask.setBlendMode(BlendMode.DstIn);
    const blurOf = (sigma: number) => (sigma < 0.1 ? null : Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
    const first = Math.floor(s.scroll) - 4;
    for (let i = first; i <= first + 12; i++) {
      const line = lineAt(layout, i);
      const top = focus + lineTop(layout, i) - offsetOf(layout, s.scroll);
      if (top > h + 20 || top + line.height < -20) continue;
      const d = i - s.scroll;
      const blur = blurOf(lineBlur(d) * s.blurScale);
      const sungA = sungAlpha(d);
      const unsungA = unsungAlpha(d);
      for (const pw of line.words) {
        const f = fillOf(i, pw.word, s);
        const lift = reduced || s.rest > 0 || i !== s.line ? 0 : wordLift(pw.word, s.tau);
        // The caps' middle sits on the middle of the row.
        const baseline = top + pw.row * ROW_HEIGHT + ROW_HEIGHT / 2 + 0.35 * SIZE - 2 * lift;
        const rect = Skia.XYWHRect(pw.x - 4, baseline - SIZE * 1.1, pw.width + 8, SIZE * 1.5);
        canvas.save();
        // Grow 3 % about the word's centre.
        const grow = 1 + 0.03 * lift;
        const cx = pw.x + pw.width / 2;
        const cy = baseline - 0.35 * SIZE;
        canvas.translate(cx, cy);
        canvas.scale(grow, grow);
        canvas.translate(-cx, -cy);
        const text = (alpha: number, filter: ReturnType<typeof blurOf>) => {
          paint.setColor(Float32Array.of(1, 1, 1, Math.min(1, Math.max(0, alpha))));
          paint.setMaskFilter(filter);
          if (pw.word.held) {
            for (let li = 0; li < pw.letters.length; li++) {
              const wave = 2.5 * Math.sin(2 * Math.PI * 1.6 * s.tau - li * 0.7) * lift;
              canvas.drawText(pw.letters[li].ch, pw.x + pw.letters[li].x, baseline - wave, paint, font);
            }
          } else {
            canvas.drawText(pw.word.text, pw.x, baseline, paint, font);
          }
        };
        if (f < 1) text(unsungA, blur);
        if (f >= 1) {
          text(sungA, blur);
        } else if (f > 0) {
          const edge = pw.x + f * pw.width;
          canvas.saveLayer(undefined, rect);
          text(sungA, blur);
          mask.setShader(
            Skia.Shader.MakeLinearGradient(
              { x: edge - 16, y: 0 },
              { x: edge + 8, y: 0 },
              [Float32Array.of(1, 1, 1, 1), Float32Array.of(1, 1, 1, 1), Float32Array.of(1, 1, 1, 0)],
              [0, 0.5, 1],
              TileMode.Clamp,
            ),
          );
          canvas.drawRect(rect, mask);
          canvas.restore();
          // The glowing leading edge.
          const glowRect = Skia.XYWHRect(rect.x - 10, rect.y - 10, rect.width + 20, rect.height + 20);
          canvas.saveLayer(undefined, glowRect);
          text(0.9 * sungA, blurOf(5));
          mask.setShader(
            Skia.Shader.MakeLinearGradient(
              { x: edge - 28, y: 0 },
              { x: edge + 4, y: 0 },
              [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 1), Float32Array.of(1, 1, 1, 0)],
              [0, 0.85, 1],
              TileMode.Clamp,
            ),
          );
          canvas.drawRect(glowRect, mask);
          canvas.restore();
        }
        canvas.restore();
      }
    }
    // The lyrics fade out under the header and above the scrubber.
    mask.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: 0 },
        { x: 0, y: h },
        [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 1), Float32Array.of(1, 1, 1, 1), Float32Array.of(1, 1, 1, 0)],
        [headerBottom / h, (headerBottom + 56) / h, (scrubTop - 64) / h, (scrubTop - 12) / h],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), mask);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const trackWidth = size.width - 48;
  const a = SONG.chorusAt / SONG.length;
  const b = (SONG.chorusAt + LOOP - REWIND) / SONG.length;
  const progressStyle = useAnimatedStyle(() => ({ width: trackWidth * ((shown.get()?.playhead ?? SONG.chorusAt) / SONG.length) }));
  const knobStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: trackWidth * ((shown.get()?.playhead ?? SONG.chorusAt) / SONG.length) - 6 }],
  }));

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {fontsLoaded && layout ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={background} />
          </Canvas>
          <GestureDetector gesture={gesture}>
            <View
              style={StyleSheet.absoluteFill}
              accessible
              accessibilityLabel={CHORUS.map((l) => l.map((w) => w.text).join(' ')).join(', ')}
            >
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={lyrics} />
              </Canvas>
            </View>
          </GestureDetector>

          <View style={[styles.header, { top: insets.top + 12 }]} pointerEvents="none">
            {/* Album art: a gradient square with a small orbit. */}
            <LinearGradient colors={['#FF7A59', '#D53A9D', '#2B1055']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.art}>
              <View style={styles.orbit} />
              <View style={styles.moon} />
            </LinearGradient>
            <View style={styles.titles}>
              <Text style={styles.title} numberOfLines={1}>
                {SONG.title}
              </Text>
              <Text style={styles.artist} numberOfLines={1}>
                {SONG.artist}
              </Text>
            </View>
            {/* A small live waveform: five bars, each moving a whole number of times per loop. */}
            <View style={styles.wave}>
              {[3, 5, 4, 6, 3].map((cycles, i) => (
                <Bar key={i} index={i} cycles={cycles} shown={shown} reduced={reduced} />
              ))}
            </View>
          </View>

          <View style={[styles.scrubber, { top: scrubTop, width: trackWidth }]} pointerEvents="none">
            <View style={[styles.track, { width: trackWidth }]} />
            <Animated.View style={[styles.progress, progressStyle]} />
            {/* The chorus being looped, marked above the track. */}
            <View style={[styles.loopMark, { left: trackWidth * a, width: trackWidth * (b - a) }]} />
            <Animated.View style={[styles.knob, knobStyle]} />
            <View style={styles.times}>
              <Text style={styles.time}>{times[0]}</Text>
              <Text style={styles.time}>{times[1]}</Text>
            </View>
          </View>
        </>
      ) : null}
    </View>
  );
}

function Bar({ index, cycles, shown, reduced }: { index: number; cycles: number; shown: SharedValue<Shown | null>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const clock = shown.get()?.clock ?? 0;
    return { height: reduced ? 8 : 6 + 12 * (0.5 + 0.5 * Math.sin((2 * Math.PI * cycles * clock) / LOOP + index * 1.3)) };
  });
  return <Animated.View style={[styles.bar, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#12062A' },
  header: { position: 'absolute', left: 24, right: 24, height: 48, flexDirection: 'row', alignItems: 'center', gap: 12 },
  art: { width: 48, height: 48, borderRadius: 12, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  orbit: { width: 26, height: 26, borderRadius: 13, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)' },
  moon: { position: 'absolute', width: 6, height: 6, borderRadius: 3, backgroundColor: '#FFFFFF', transform: [{ translateX: 9 }, { translateY: -9 }] },
  titles: { flex: 1, gap: 2 },
  title: { fontFamily: FONT_NAME, fontSize: 17, color: '#FFFFFF' },
  artist: { fontSize: 14, color: 'rgba(255,255,255,0.6)' },
  wave: { width: 27, height: 18, flexDirection: 'row', alignItems: 'center', gap: 3 },
  bar: { width: 3, borderRadius: 1.5, backgroundColor: 'rgba(255,255,255,0.85)' },
  scrubber: { position: 'absolute', left: 24, height: 4 },
  track: { position: 'absolute', top: 0, left: 0, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.2)' },
  progress: { position: 'absolute', top: 0, left: 0, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.9)' },
  loopMark: { position: 'absolute', top: -6, height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.45)' },
  knob: { position: 'absolute', top: -4, left: 0, width: 12, height: 12, borderRadius: 6, backgroundColor: '#FFFFFF' },
  times: { position: 'absolute', top: 14, left: 0, right: 0, flexDirection: 'row', justifyContent: 'space-between' },
  time: { fontSize: 12, fontWeight: '500', color: 'rgba(255,255,255,0.6)', fontVariant: ['tabular-nums'] },
});
