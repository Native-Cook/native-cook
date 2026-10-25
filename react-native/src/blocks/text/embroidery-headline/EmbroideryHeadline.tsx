import { Canvas, Picture, type SkImage, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Geo, type Satin, drawKnot, drawNeedle, drawSatin, makeGeo, satinFront } from './embroideryArt';
import { bakeScene, buildSatin } from './embroideryBake';
import { LOOP, easeInOut, glyphPresent, knotState, needleOut, pop, seg, subheading } from './embroideryMotion';

/*
 Embroidery Headline — the cover of "Thread & Needle", issue 9. The headline "Slow made." is
 satin-stitched on linen in a wooden hoop: madder red above, indigo below, every letter filled with
 parallel threads laid edge to edge at its own angle, so each catches the light differently; the
 full stop is a French knot. A needle with its thread lays the satin; the subheading follows.

 Touch: drag across the hoop to sew — moving right lays threads into the next unfinished letter
 (1.6 threads per pt), moving left unpicks the last ones; the needle rides the stitching front and
 leans with the drag; a light tick as each letter is finished, a tap when the knot pops. 2 s after
 the finger lifts the headline finishes itself (≤ 0.9 s, ease-in-out) and the loop starts again from
 its first frame. Reduced motion: the finished cover, still; dragging still sews.

 One Canvas: the table, hoop and linen are baked once at 2×; per frame the laid threads (a few
 paths per letter), the knot and the needle are recorded into one picture on the UI thread.
 */

const AUTO = 0;
const USER = 1;
const RETURN = 2;
const STILL = 3;
const PER_PT = 1.6;
const KNOT_POP = 0.4;
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

const CREAM = '#EFE6D4';
const MUTED = '#A7B2A4';
const ACCENT = '#D9A56B';

function sum(a: number[]) {
  'worklet';
  let s = 0;
  for (let i = 0; i < a.length; i += 1) s += a[i];
  return s;
}

/** Adds d threads in stitching order (d > 0) or takes them out from the end (d < 0). */
function sew(p: number[], counts: number[], d: number): number[] {
  'worklet';
  const out = p.slice();
  let left = d;
  if (left > 0) {
    for (let i = 0; i < out.length && left > 0; i += 1) {
      const take = Math.min(counts[i] - out[i], left);
      out[i] += take;
      left -= take;
    }
  } else {
    for (let i = out.length - 1; i >= 0 && left < 0; i -= 1) {
      const take = Math.min(out[i], -left);
      out[i] -= take;
      left += take;
    }
  }
  return out;
}

/** The letter the needle is working: the first unfinished one, or the last one when all are done. */
function activeGlyph(p: number[], counts: number[]) {
  'worklet';
  for (let i = 0; i < p.length; i += 1) if (p[i] < counts[i] - 0.001) return i;
  return p.length - 1;
}

function finishedCount(p: number[], counts: number[]) {
  'worklet';
  let n = 0;
  for (let i = 0; i < p.length; i += 1) if (p[i] >= counts[i] - 0.001) n += 1;
  return n;
}

function blendToFull(p: number[], counts: number[], k: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < p.length; i += 1) out.push(p[i] + (counts[i] - p[i]) * k);
  return out;
}

/** The needle's spot in the loop: [x, y, fromX, fromY, out 0..1]. */
function loopNeedle(t: number, s: Satin, g: Geo, park: number[]): number[] {
  'worklet';
  const p = glyphPresent(t, s.counts);
  // Which letter is being worked: while unpicking, the one being unpicked; then the stitching front.
  let gi = 0;
  if (t < 1.75) {
    for (let i = 0; i < p.length; i += 1) if (p[i] < s.counts[i] - 0.001) gi = i;
    for (let i = 0; i < p.length; i += 1) {
      if (p[i] > 0.001 && p[i] < s.counts[i] - 0.001) {
        gi = i;
        break;
      }
    }
  } else gi = activeGlyph(p, s.counts);
  const f = satinFront(s.glyphs[gi], Math.min(s.counts[gi] - 0.001, p[gi]));
  const out = needleOut(t);
  return [park[0] + (f[0] - park[0]) * out, park[1] + (f[1] - park[1]) * out, out > 0.5 ? f[2] : park[2], out > 0.5 ? f[3] : park[3], out];
}

export function EmbroideryHeadline({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hero, setHero] = useState({ x: 0, y: 0, width: 0, height: 0 });

  const geo = useMemo(() => (size.width > 0 && hero.width > 0 ? makeGeo(size.width, size.height, hero) : null), [size, hero]);
  const satin = useMemo(
    () => (geo ? buildSatin(geo, (s) => matchFont({ fontFamily: SERIF, fontSize: s, fontWeight: '700' }), ['Slow', 'made']) : null),
    [geo],
  );
  // Where the needle rests between passes, stuck in the linen at the lower right, and the hole
  // its thread runs to (just under the knot).
  const park = useMemo(
    () => (geo && satin ? [geo.cx + geo.fabricR * 0.5, geo.cy + geo.fabricR * 0.52, satin.knot[0] - 2, satin.knot[1] + satin.knot[2] + 2] : [0, 0, 0, 0]),
    [geo, satin],
  );
  const scene = useSharedValue<SkImage | null>(null);
  useEffect(() => {
    if (!geo) return undefined;
    const image = bakeScene(geo, 2);
    scene.set(image);
    return () => {
      scene.set(null);
      image?.dispose();
    };
  }, [geo, scene]);

  const now = useSharedValue(0);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(auto ? AUTO : STILL);
  const modeAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const touching = useSharedValue(0);
  const userP = useSharedValue<number[]>([]);
  const returnFrom = useSharedValue<number[]>([]);
  const knotAt = useSharedValue(-100);
  const lean = useSharedValue(0);
  const needle = useSharedValue<number[]>([0, 0, 0, 0, 0]);

  useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 16) / 1000);
    const t = now.get() + dt;
    now.set(t);
    const m = mode.get();
    if (m === AUTO) loopT.set((loopT.get() + dt) % LOOP);
    if (!satin || !geo) return;
    if (m === USER && auto && touching.get() === 0 && t - lastTouch.get() > 2) {
      const from = userP.get();
      returnFrom.set(from);
      mode.set(RETURN);
      modeAt.set(t);
    }
    if (m === RETURN) {
      const from = returnFrom.get();
      const missing = 1 - sum(from) / sum(satin.counts);
      const length = 0.3 + 0.6 * missing;
      const k = easeInOut(seg(t, modeAt.get(), modeAt.get() + length));
      userP.set(blendToFull(from, satin.counts, k));
      if (k >= 1) {
        // The knot pops last (unless it is already there), then the loop takes over at t = 0.
        const ka = knotAt.get();
        if (ka === -100) knotAt.set(t);
        else if (ka === -50 || t - ka > KNOT_POP) {
          mode.set(AUTO);
          loopT.set(0);
        }
      }
    }
    if (m === USER || m === RETURN) {
      // The needle chases the stitching front while sewing and goes back to rest when idle.
      const p = userP.get();
      const busy = touching.get() === 1;
      const gi = activeGlyph(p, satin.counts);
      const f = satinFront(satin.glyphs[gi], Math.min(satin.counts[gi] - 0.001, p[gi]));
      const target = busy ? f : park;
      const n = needle.get();
      const k = 1 - Math.exp(-dt * (busy ? 22 : 7));
      const out = n[4] + ((busy ? 1 : 0) - n[4]) * (1 - Math.exp(-dt * 8));
      needle.set([n[0] + (target[0] - n[0]) * k, n[1] + (target[1] - n[1]) * k, busy ? f[2] : park[2], busy ? f[3] : park[3], out]);
      lean.set(lean.get() * Math.exp(-dt * 6));
    }
  });

  const tick = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      if (!satin || !geo) return;
      touching.set(1);
      lastTouch.set(now.get());
      const m = mode.get();
      if (m === AUTO || m === STILL) {
        const p = m === AUTO ? glyphPresent(loopT.get(), satin.counts) : satin.counts.slice();
        userP.set(p);
        const n = m === AUTO ? loopNeedle(loopT.get(), satin, geo, park) : [park[0], park[1], park[2], park[3], 0];
        needle.set(n);
        const ks = m === AUTO ? knotState(loopT.get()) : [1, 1];
        knotAt.set(ks[1] >= 0.99 ? -50 : -100);
      }
      mode.set(USER);
    })
    .onChange((e) => {
      if (!satin) return;
      const before = userP.get();
      const after = sew(before, satin.counts, e.changeX * PER_PT);
      userP.set(after);
      lean.set(Math.max(-0.35, Math.min(0.35, lean.get() + e.changeX * 0.004)));
      lastTouch.set(now.get());
      const total = sum(satin.counts);
      if (finishedCount(after, satin.counts) !== finishedCount(before, satin.counts)) scheduleOnRN(tick, 0);
      if (sum(after) >= total - 0.001 && sum(before) < total - 0.001) {
        knotAt.set(now.get());
        scheduleOnRN(tick, 1);
      }
      if (sum(after) < total - 0.001) knotAt.set(-100);
    })
    .onFinalize(() => {
      touching.set(0);
      lastTouch.set(now.get());
    });

  /** What is on the cloth: per letter threads laid, knot [scale, alpha], subheading [alpha, rise]. */
  const state = useDerivedValue(() => {
    if (!satin) return { p: [] as number[], knot: [0, 0], sub: [1, 0], needle: [0, 0, 0, 0, 0] };
    const m = mode.get();
    if (m === AUTO) {
      const t = loopT.get();
      return { p: glyphPresent(t, satin.counts), knot: knotState(t), sub: subheading(t), needle: geo ? loopNeedle(t, satin, geo, park) : [0, 0, 0, 0, 0] };
    }
    if (m === STILL) return { p: satin.counts, knot: [1, 1], sub: [1, 0], needle: [park[0], park[1], park[2], park[3], 0] };
    const p = userP.get();
    const total = sum(satin.counts);
    const ka = knotAt.get();
    const knot = ka === -50 ? [1, 1] : ka < 0 ? [0.55, 0] : [pop(seg(now.get(), ka, ka + KNOT_POP)), Math.min(1, (now.get() - ka) * 10)];
    const a = Math.min(1, Math.max(0, (sum(p) - total + 40) / 40));
    return { p, knot, sub: [a, (1 - a) * 10], needle: needle.get() };
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const image = scene.get();
    const s = state.get();
    if (!geo || !satin || !image) return recorder.finishRecordingAsPicture();
    canvas.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, geo.w, geo.h), Skia.Paint());
    drawSatin(canvas, satin, s.p, 1);
    drawKnot(canvas, satin, s.knot[0], s.knot[1]);
    const n = s.needle;
    const gi = activeGlyph(s.p, satin.counts);
    const rgb = n[4] > 0.5 ? satin.glyphs[gi].rgb : satin.knotRGB;
    const bob = mode.get() === AUTO && !reduced ? 0.22 * n[4] * (1 + Math.sin((loopT.get() / LOOP) * 2 * Math.PI * 54)) : 0;
    drawNeedle(canvas, n[0], n[1], -0.92 + lean.get(), n[4] * 0.55 + bob, n[2], n[3], rgb, 1);
    return recorder.finishRecordingAsPicture();
  });

  const subStyle = useAnimatedStyle(() => {
    const s = state.get().sub;
    return { opacity: s[0], transform: [{ translateY: s[1] }] };
  });

  const onRoot = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };
  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (width !== hero.width || height !== hero.height || y !== hero.y) setHero({ x, y, width, height });
  };

  return (
    <View style={styles.root} onLayout={onRoot}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.column, { paddingTop: insets.top + 8, paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.masthead}>
          <Text style={styles.brand}>THREAD &amp; NEEDLE</Text>
          <Text style={styles.issue}>No. 9 · Autumn</Text>
        </View>
        <View style={styles.rule} />
        <View style={styles.kickerRow}>
          <Text style={styles.kicker}>COVER STORY</Text>
          <Text style={styles.kickerRight}>Hand embroidery</Text>
        </View>

        <GestureDetector gesture={pan}>
          <View style={styles.hero} onLayout={onHero} accessible accessibilityLabel="Headline: Slow made. Drag across the hoop to stitch or unpick it." />
        </GestureDetector>

        <Animated.View style={[styles.sub, subStyle]}>
          <Text style={styles.subTitle}>Twelve makers on the quiet patience of the needle</Text>
          <Text style={styles.meta}>Words by Ines Rook · 18 min read</Text>
        </Animated.View>

        <View style={styles.actions}>
          <Pressable style={({ pressed }) => [styles.cta, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
            <Text style={styles.ctaText}>Read the story</Text>
          </Pressable>
          <Pressable style={({ pressed }) => [styles.save, { transform: [{ scale: pressed ? 0.94 : 1 }] }]} accessibilityRole="button" accessibilityLabel="Save the story">
            <SymbolView name="bookmark" size={19} weight="medium" tintColor={CREAM} fallback={<View style={styles.bookmark} />} />
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#1E2822', overflow: 'hidden' },
  column: { flex: 1, paddingHorizontal: 24 },
  masthead: { height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: SERIF, fontSize: 17, fontWeight: '700', color: CREAM, letterSpacing: 2.6 },
  issue: { fontSize: 13, fontWeight: '500', color: MUTED, fontVariant: ['tabular-nums'] },
  rule: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(239,230,212,0.28)', marginTop: 8 },
  kickerRow: { marginTop: 12, flexDirection: 'row', justifyContent: 'space-between' },
  kicker: { fontSize: 11, fontWeight: '700', color: ACCENT, letterSpacing: 2 },
  kickerRight: { fontSize: 12, fontWeight: '500', color: MUTED },
  hero: { flex: 1, minHeight: 260, marginHorizontal: -12 },
  sub: { gap: 8, marginBottom: 20 },
  subTitle: { fontFamily: SERIF, fontStyle: 'italic', fontSize: 23, lineHeight: 29, color: CREAM },
  meta: { fontSize: 13, color: MUTED },
  actions: { flexDirection: 'row', gap: 12 },
  cta: {
    flex: 1,
    height: 52,
    borderRadius: 26,
    backgroundColor: CREAM,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 10px 24px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.7)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#1F2A23' },
  save: {
    width: 52,
    height: 52,
    borderRadius: 26,
    borderWidth: 1,
    borderColor: 'rgba(239,230,212,0.3)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bookmark: { width: 13, height: 17, borderWidth: 1.6, borderColor: CREAM, borderBottomWidth: 0, borderRadius: 2 },
});
