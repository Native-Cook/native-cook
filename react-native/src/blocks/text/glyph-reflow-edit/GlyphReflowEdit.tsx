// "Quill": a writing assistant rewrites a paragraph, and the letters both versions share glide to their new places.
// Glyphs are measured once (./layout); one Skia canvas draws them on the UI thread from one morph value.
import { BlendMode, Canvas, matchFont, Picture, Skia, type SkCanvas, type SkFont, type SkPaint, type SkPoint } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { DOCUMENT, ORIGINAL, type ToneVersion, VERSIONS } from './data';
import { buildMorph, layOut, type Morph, statsOf } from './layout';
import { easeInOutCubic, easeOutCubic, GLYPH, ghostOpacityAt, ghostPressAt, LOOP, mix, MORPH_A, progressAt, RESUME_AFTER, rubber, SCRUB_BACK, seg, SPRING, toneAt, toneIndicatorAt } from './motion';

export { LOOP };
export type GlyphReflowVersion = ToneVersion;
export type GlyphReflowEditProps = {
  /** Plays the 9 s loop until a finger takes over; it carries on 2 s after the last touch. */
  autoPlay?: boolean;
  /** The text as written. */
  original?: string;
  /** The rewrites, one chip each. */
  versions?: GlyphReflowVersion[];
  /** Shown in the header. */
  title?: string;
};

const DESK = '#E8EBE4';
const PAPER = '#FBFBF6';
const INK = '#1F2522';
const INK_SOFT = '#6C756F';
const HAIRLINE = 'rgba(31,37,34,0.1)';
const ACCENT = '#2C6E51';
const ACCENT_SOFT = '#D9E7DF';

const SIDE = 16;
const MAX_WIDTH = 560;
const HEADER = 56;
const CARD_PAD = 20;
const GAP = 12;
const CHIPS = 44;
const BAR = 58;
const BAR_LABELS = 20;
const STATS = 68;
const THUMB = 28;
const SIZES = [31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16];
const LEADING = 1.38;
const BUCKETS = 8;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;

const FONT_FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

function tick() {
  if (Platform.OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function drawBuckets(canvas: SkCanvas, font: SkFont, paints: SkPaint[], ids: number[][], points: SkPoint[][]) {
  'worklet';
  for (let b = 0; b < BUCKETS; b += 1) {
    if (ids[b].length > 0) canvas.drawGlyphs(ids[b], points[b], 0, 0, font, paints[b]);
  }
}

function drawMorph(canvas: SkCanvas, morph: Morph, amount: number, font: SkFont, ink: SkPaint, outPaints: SkPaint[], inPaints: SkPaint[]) {
  'worklet';
  const p = Math.min(Math.max(amount, 0), 1);
  const points: SkPoint[] = new Array(morph.ids.length);
  for (let i = 0; i < morph.ids.length; i += 1) {
    const start = morph.delay[i] * GLYPH.spread;
    const k = easeInOutCubic(seg(p, start, start + 1 - GLYPH.spread));
    const lift = 4 * k * (1 - k) * morph.arc[i];
    points[i] = { x: mix(morph.ax[i], morph.bx[i], k), y: mix(morph.ay[i], morph.by[i], k) + lift };
  }
  if (morph.ids.length > 0) canvas.drawGlyphs(morph.ids, points, 0, 0, font, ink);

  if (p < 1) {
    const ids: number[][] = [[], [], [], [], [], [], [], []];
    const at: SkPoint[][] = [[], [], [], [], [], [], [], []];
    for (let i = 0; i < morph.outIds.length; i += 1) {
      const start = morph.outDelay[i] * GLYPH.outStagger;
      const k = seg(p, start, start + GLYPH.outEnd);
      if (k >= 1) continue;
      const b = Math.min(Math.floor((1 - k) * BUCKETS), BUCKETS - 1);
      ids[b].push(morph.outIds[i]);
      at[b].push({ x: morph.outX[i], y: morph.outY[i] + k * k * GLYPH.drop });
    }
    drawBuckets(canvas, font, outPaints, ids, at);
  }
  if (p > GLYPH.inStart) {
    const ids: number[][] = [[], [], [], [], [], [], [], []];
    const at: SkPoint[][] = [[], [], [], [], [], [], [], []];
    for (let i = 0; i < morph.inIds.length; i += 1) {
      const start = GLYPH.inStart + morph.inDelay[i] * GLYPH.inStagger;
      const k = seg(p, start, start + GLYPH.inLength);
      if (k <= 0) continue;
      const b = Math.min(Math.floor(k * BUCKETS), BUCKETS - 1);
      ids[b].push(morph.inIds[i]);
      at[b].push({ x: morph.inX[i], y: morph.inY[i] + (1 - easeOutCubic(k)) * GLYPH.rise });
    }
    drawBuckets(canvas, font, inPaints, ids, at);
  }
}

function StatLayer({ value, index, progress, tone }: { value: string; index: number; progress: SharedValue<number>; tone: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const p = Math.min(Math.max(progress.get(), 0), 1);
    const current = tone.get();
    return { opacity: index === 0 ? 1 - p : current === index - 1 ? p : 0 };
  });
  return (
    <Animated.Text style={[styles.statValue, index > 0 && styles.statLayer, style]} numberOfLines={1}>
      {value}
    </Animated.Text>
  );
}

function Stat({ label, values, progress, tone }: { label: string; values: string[]; progress: SharedValue<number>; tone: SharedValue<number> }) {
  return (
    <View style={styles.stat}>
      <View style={styles.statValues}>
        {values.map((value, index) => (
          <StatLayer key={`${label}-${index}`} value={value} index={index} progress={progress} tone={tone} />
        ))}
      </View>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function ChipLabel({ label, index, indicator }: { label: string; index: number; indicator: SharedValue<number> }) {
  const on = useAnimatedStyle(() => ({ opacity: 1 - Math.min(Math.abs(indicator.get() - index), 1) }));
  return (
    <View style={styles.chipLabel}>
      <Text style={styles.chipText}>{label}</Text>
      <Animated.Text style={[styles.chipText, styles.chipTextOn, on]}>{label}</Animated.Text>
    </View>
  );
}

function BarName({ label, index, indicator }: { label: string; index: number; indicator: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: 1 - Math.min(Math.abs(indicator.get() - index), 1) }));
  return <Animated.Text style={[styles.barLabel, styles.barName, style]}>{label}</Animated.Text>;
}

export function GlyphReflowEdit({ autoPlay = true, original = ORIGINAL, versions = VERSIONS, title = DOCUMENT }: GlyphReflowEditProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const playing = autoPlay && !reducedMotion;

  const column = Math.min(width, MAX_WIDTH);
  const left = (width - column) / 2 + SIDE;
  const inner = column - SIDE * 2;
  const textWidth = inner - CARD_PAD * 2;
  const top = insets.top + 4;
  const bottom = Math.max(insets.bottom, 12);
  const cardHeight = Math.max(height - top - bottom - HEADER - GAP - CHIPS - GAP - BAR - GAP - STATS, 180);
  const textRoom = cardHeight - CARD_PAD * 2;
  const chipsTop = top + HEADER + cardHeight + GAP;
  const barTop = chipsTop + CHIPS + GAP;
  const chipWidth = inner / Math.max(versions.length, 1);
  const trackLeft = left + THUMB / 2;
  const trackWidth = inner - THUMB;
  const trackY = barTop + BAR_LABELS + (BAR - BAR_LABELS) / 2;

  // The largest type at which every version fits the card, measured once per size.
  const page = useMemo(() => {
    let chosen: { font: SkFont; morphs: Morph[] } | null = null;
    for (const size of SIZES) {
      const font = matchFont({ fontFamily: FONT_FAMILY, fontSize: size, fontWeight: '400' });
      const lineHeight = Math.round(size * LEADING);
      const base = layOut(font, original, textWidth, lineHeight);
      const laid = versions.map((version) => layOut(font, version.text, textWidth, lineHeight));
      const tallest = Math.max(base.height, ...laid.map((entry) => entry.height));
      chosen = { font, morphs: versions.map((version, i) => buildMorph(original, version.text, base, laid[i], textWidth)) };
      if (tallest <= textRoom) break;
    }
    return chosen as { font: SkFont; morphs: Morph[] };
  }, [original, versions, textWidth, textRoom]);

  const paints = useMemo(() => {
    const make = (color: string, alpha: number) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setBlendMode(BlendMode.SrcOver);
      paint.setColor(Skia.Color(color));
      paint.setAlphaf(alpha);
      return paint;
    };
    const ink = make(INK, 1);
    const from = Skia.Color(ACCENT);
    const to = Skia.Color(INK);
    const out: SkPaint[] = [];
    const incoming: SkPaint[] = [];
    for (let b = 0; b < BUCKETS; b += 1) {
      const alpha = (b + 1) / BUCKETS;
      out.push(make(INK, alpha));
      // New letters arrive in the accent and settle into the ink.
      const toInk = (b / (BUCKETS - 1)) ** 2;
      const paint = make(INK, alpha);
      paint.setColor(Float32Array.of(mix(from[0], to[0], toInk), mix(from[1], to[1], toInk), mix(from[2], to[2], toInk), alpha));
      incoming.push(paint);
    }
    return { ink, out, incoming };
  }, []);

  const stats = useMemo(() => {
    const base = statsOf(original, original);
    const rows = versions.map((version) => statsOf(original, version.text));
    return {
      words: [base.words, ...rows.map((row) => row.words)],
      read: [base.read, ...rows.map((row) => row.read)],
      change: [base.change, ...rows.map((row) => row.change)],
    };
  }, [original, versions]);

  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const progress = useSharedValue(reducedMotion ? 1 : 0);
  const velocity = useSharedValue(0);
  const target = useSharedValue(0);
  const tone = useSharedValue(0);
  const indicator = useSharedValue(0);
  const pending = useSharedValue(-1);
  const returning = useSharedValue(0);
  const touches = useSharedValue(0);
  const seen = useSharedValue(0);
  const held = useSharedValue(0);
  const idle = useSharedValue(0);
  const side = useSharedValue(0);
  const ghostShown = useSharedValue(0);

  useEffect(() => {
    // Reduced motion: the first rewrite, in place, with the loop stopped.
    clock.set(0);
    mode.set(MODE_AUTO);
    returning.set(0);
    pending.set(-1);
    tone.set(0);
    indicator.set(0);
    velocity.set(0);
    target.set(reducedMotion ? 1 : 0);
    progress.set(reducedMotion ? 1 : 0);
  }, [reducedMotion, clock, mode, returning, pending, tone, indicator, velocity, target, progress]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    if (reducedMotion) {
      // No glide: a chip swaps the text at once, and the bar still scrubs under the finger.
      const next = pending.get();
      if (next >= 0) {
        tone.set(next);
        indicator.set(next);
        progress.set(1);
        target.set(1);
        pending.set(-1);
      } else if (held.get() === 0 && progress.get() !== target.get()) {
        progress.set(target.get());
      }
      return;
    }

    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      returning.set(0);
      if (mode.get() === MODE_AUTO) {
        mode.set(MODE_MANUAL);
        ghostShown.set(0);
        velocity.set(0);
      }
    }

    if (mode.get() === MODE_AUTO) {
      if (!playing) return;
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      clock.set(t);
      const nextProgress = progressAt(t);
      if (nextProgress !== progress.get()) progress.set(nextProgress);
      const nextTone = toneAt(t);
      if (nextTone !== tone.get()) tone.set(nextTone);
      const nextIndicator = toneIndicatorAt(t);
      if (nextIndicator !== indicator.get()) indicator.set(nextIndicator);
      ghostShown.set(ghostOpacityAt(t));
      return;
    }

    let p = progress.get();
    let v = velocity.get();
    if (held.get() === 0) {
      idle.set(idle.get() + dt);
      const next = pending.get();
      if (next >= 0 && Math.abs(p) < 0.015 && Math.abs(v) < 0.6) {
        tone.set(next);
        pending.set(-1);
        target.set(1);
      }
      v += (SPRING.stiffness * (target.get() - p) - SPRING.damping * v) * dt;
      p += v * dt;
      if (Math.abs(target.get() - p) < 0.0008 && Math.abs(v) < 0.01) {
        p = target.get();
        v = 0;
      }
      velocity.set(v);
      if (p !== progress.get()) progress.set(p);

      if (playing && returning.get() === 0 && idle.get() >= RESUME_AFTER) {
        returning.set(1);
        pending.set(-1);
        target.set(0);
      }
    }

    const wanted = returning.get() === 1 && Math.abs(p) < 0.015 ? 0 : tone.get();
    const shown = indicator.get();
    const moved = shown + (wanted - shown) * (1 - Math.exp(-dt * 14));
    const settled = Math.abs(wanted - moved) < 0.002 ? wanted : moved;
    if (settled !== shown) indicator.set(settled);

    if (returning.get() === 1 && p === 0) {
      tone.set(0);
      if (settled === 0) {
        returning.set(0);
        mode.set(MODE_AUTO);
        clock.set(0);
      }
    }
  });

  const onChip = (index: number) => {
    touches.set(touches.get() + 1);
    tick();
    if (tone.get() === index && pending.get() < 0) {
      target.set(progress.get() > 0.5 ? 0 : 1);
    } else {
      pending.set(index);
      target.set(0);
    }
  };

  const scrub = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .hitSlop({ vertical: 10 })
    .onBegin((event) => {
      touches.set(touches.get() + 1);
      held.set(1);
      pending.set(-1);
      velocity.set(0);
      const value = rubber((event.x - THUMB / 2) / trackWidth);
      side.set(value > 0.5 ? 1 : 0);
      progress.set(value);
      target.set(value);
    })
    .onUpdate((event) => {
      const value = rubber((event.x - THUMB / 2) / trackWidth);
      progress.set(value);
      target.set(value);
      velocity.set(event.velocityX / trackWidth);
      const now = value > 0.5 ? 1 : 0;
      if (now !== side.get()) {
        side.set(now);
        scheduleOnRN(tick);
      }
    })
    .onFinalize(() => {
      held.set(0);
      idle.set(0);
      if (reducedMotion) {
        const settled = Math.min(Math.max(progress.get(), 0), 1);
        target.set(settled);
        progress.set(settled);
        return;
      }
      target.set(progress.get() + velocity.get() * 0.16 > 0.5 ? 1 : 0);
    });

  const picture = useDerivedValue(() => {
    const amount = progress.get();
    const current = tone.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, inner, cardHeight));
    canvas.translate(CARD_PAD, CARD_PAD);
    drawMorph(canvas, page.morphs[Math.min(current, page.morphs.length - 1)], amount, page.font, paints.ink, paints.out, paints.incoming);
    return recorder.finishRecordingAsPicture();
  });

  const pill = useAnimatedStyle(() => ({ transform: [{ translateX: indicator.get() * chipWidth }] }));
  const fill = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.min(Math.max(progress.get(), 0), 1) }] }));
  const thumb = useAnimatedStyle(() => {
    const p = progress.get();
    const stretch = Math.min(Math.abs(velocity.get()) * 0.03, 0.16);
    return { transform: [{ translateX: p * trackWidth }, { scaleX: 1 + stretch }, { scaleY: 1 - stretch * 0.6 }, { scale: held.get() === 1 ? 1.08 : 1 }] };
  });
  const originalLabel = useAnimatedStyle(() => ({ opacity: mix(1, 0.45, Math.min(Math.max(progress.get(), 0), 1)) }));
  const ghost = useAnimatedStyle(() => {
    const t = clock.get();
    const opacity = mode.get() === MODE_AUTO && playing ? ghostShown.get() : 0;
    let x = left + chipWidth * 0.5;
    let y = chipsTop + CHIPS / 2;
    if (t >= 4.45) {
      x = left + chipWidth * 1.5;
    } else if (t >= MORPH_A[1]) {
      x = trackLeft + (t < SCRUB_BACK[0] ? 1 : Math.min(Math.max(progress.get(), 0), 1)) * trackWidth;
      y = trackY;
    }
    return { opacity, transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - ghostPressAt(t) * 0.18 }] };
  });

  return (
    <View style={styles.screen} accessibilityLabel="Quill rewrite preview">
      <StatusBar style="dark" />
      <View style={[styles.column, { paddingTop: top, marginLeft: left, width: inner }]}>
        <View style={styles.header}>
          <View style={styles.mark}>
            <Text style={styles.markText}>Q</Text>
          </View>
          <View style={styles.headerText}>
            <Text style={styles.title} numberOfLines={1}>{title}</Text>
            <Text style={styles.meta} numberOfLines={1}>Quill suggestion</Text>
          </View>
          <Text style={styles.saved}>Saved</Text>
        </View>

        <View style={[styles.card, { height: cardHeight }]} accessible accessibilityRole="text" accessibilityLabel={original}>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>
        </View>

        <View style={styles.chips}>
          <Animated.View style={[styles.pill, { width: chipWidth }, pill]} />
          {versions.map((version, index) => (
            <Pressable
              key={version.label}
              style={styles.chip}
              onPress={() => onChip(index)}
              accessibilityRole="button"
              accessibilityLabel={`Rewrite ${version.label.toLowerCase()}`}
            >
              <ChipLabel label={version.label} index={index} indicator={indicator} />
            </Pressable>
          ))}
        </View>

        <View style={styles.bar}>
          <View style={styles.barLabels}>
            <Animated.Text style={[styles.barLabel, originalLabel]}>Original</Animated.Text>
            <View style={styles.barNames}>
              {versions.map((version, index) => (
                <BarName key={version.label} label={version.label} index={index} indicator={indicator} />
              ))}
            </View>
          </View>
          <GestureDetector gesture={scrub}>
            <View style={styles.trackArea} accessibilityLabel="Scrub between the original and the rewrite">
              <View style={[styles.track, { left: THUMB / 2, width: trackWidth }]}>
                <Animated.View style={[styles.trackFill, fill]} />
              </View>
              <Animated.View style={[styles.thumb, thumb]} />
            </View>
          </GestureDetector>
        </View>

        <View style={styles.stats}>
          <Stat label="Words" values={stats.words} progress={progress} tone={tone} />
          <View style={styles.statRule} />
          <Stat label="Read time" values={stats.read} progress={progress} tone={tone} />
          <View style={styles.statRule} />
          <Stat label="Length" values={stats.change} progress={progress} tone={tone} />
        </View>
      </View>
      <Animated.View pointerEvents="none" style={[styles.ghost, ghost]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DESK },
  column: { flex: 1 },
  header: { height: HEADER, flexDirection: 'row', alignItems: 'center', gap: 10 },
  mark: { width: 36, height: 36, borderRadius: 11, backgroundColor: ACCENT, alignItems: 'center', justifyContent: 'center', borderTopWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.4)' },
  markText: { color: '#F5F8F2', fontSize: 19, fontWeight: '700' },
  headerText: { flex: 1, minWidth: 0 },
  title: { color: INK, fontSize: 17, fontWeight: '600' },
  meta: { color: INK_SOFT, fontSize: 13, marginTop: 1 },
  saved: { color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  card: {
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: PAPER,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
    overflow: 'hidden',
    boxShadow: '0 1px 3px rgba(31,37,34,0.14), 0 12px 32px rgba(31,37,34,0.1), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  chips: { height: CHIPS, marginTop: GAP, flexDirection: 'row', borderRadius: CHIPS / 2, backgroundColor: 'rgba(31,37,34,0.07)', overflow: 'hidden' },
  pill: { position: 'absolute', top: 3, bottom: 3, left: 0, borderRadius: (CHIPS - 6) / 2, backgroundColor: INK, boxShadow: '0 1px 2px rgba(31,37,34,0.3)' },
  chip: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  chipLabel: { alignItems: 'center', justifyContent: 'center' },
  chipText: { color: INK, fontSize: 15, fontWeight: '600' },
  chipTextOn: { position: 'absolute', color: '#F5F7F1' },
  bar: { height: BAR, marginTop: GAP },
  barLabels: { height: BAR_LABELS, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  barLabel: { color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  barNames: { minWidth: 72, height: BAR_LABELS, alignItems: 'flex-end', justifyContent: 'center' },
  barName: { position: 'absolute', right: 0, color: ACCENT, fontWeight: '600' },
  trackArea: { height: BAR - BAR_LABELS, justifyContent: 'center' },
  track: { position: 'absolute', height: 4, borderRadius: 2, backgroundColor: 'rgba(31,37,34,0.13)', overflow: 'hidden' },
  trackFill: { width: '100%', height: '100%', backgroundColor: ACCENT, transformOrigin: 'left' },
  thumb: { position: 'absolute', left: 0, width: THUMB, height: THUMB, borderRadius: THUMB / 2, backgroundColor: PAPER, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIRLINE, boxShadow: '0 1px 2px rgba(31,37,34,0.25), 0 4px 12px rgba(31,37,34,0.18)' },
  stats: { height: STATS, marginTop: GAP, flexDirection: 'row', alignItems: 'center', borderRadius: 18, borderCurve: 'continuous', backgroundColor: ACCENT_SOFT },
  stat: { flex: 1, alignItems: 'center' },
  statValues: { height: 26, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center' },
  statValue: { color: INK, fontSize: 20, lineHeight: 26, fontWeight: '700', fontVariant: ['tabular-nums'] },
  statLayer: { position: 'absolute' },
  statLabel: { color: '#4F5D55', fontSize: 12, marginTop: 2 },
  statRule: { width: StyleSheet.hairlineWidth, height: 30, backgroundColor: 'rgba(31,37,34,0.18)' },
  ghost: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(31,37,34,0.2)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.75)' },
});
