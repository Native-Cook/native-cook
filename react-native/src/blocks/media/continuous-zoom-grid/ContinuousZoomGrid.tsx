import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
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
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { drawGrid } from './draw';
import {
  COUNT,
  type GridView,
  LEVELS,
  MAX_Z,
  MIN_Z,
  MONTHS,
  REST_ANCHOR,
  centreMonth,
  clamp01,
  easeInOut,
  nearestLevel,
  row,
  viewAt,
} from './model';

/*
 Continuous Zoom Grid — a photo library that zooms continuously from 3 large photos per row to a
 contact sheet of 20 per row and back, the way a pinch does it. A port of the SwiftUI block
 (ContinuousZoomGrid.swift); the numbers are the same.

 The zoom z (photos per row) is continuous; the grid is drawn from the two layouts either side of it
 in [3, 5, 9, 14, 20]. Each layout keeps its photos square (the screen's width / n) and is scaled by
 n / z about the anchor photo (the one under the fingers), whose centre stays at the same height;
 the closer layout fades in over the middle of each step (smoothstep 0.2 – 0.8 of the step in log
 space) while the other fades out. Small photos are their sky over their ground; from 22 pt their
 picture fades in (fully drawn at 44 pt), so the zoom reads like mipmaps cross-fading. Month chips
 sit on each month's first photo below 64 pt (gone by 64, full at 34); video lengths show on photos
 above 60 pt.

 The 3,000 photos are drawn in code from their index (model.ts): one of eight palettes, one of eight
 kinds of picture (sun over the sea, hills in haze, city lights out of focus, dunes, pines in mist,
 petals close up, a building in low sun, a beach) and a few shape parameters, all from an integer
 hash; every picture gets a soft vignette. Photos come in runs that share a kind and a palette, and
 months are 30 to 109 photos long, newest first.

 Chrome: a white screen; a header (round back button, "Library" over the month in the middle of the
 screen, a Select pill) over a white fade; a floating glass control at the bottom (Years · Months ·
 All Photos) whose highlight follows the zoom.

 autoPlay timeline (LOOP = 8 s):
   0.0 – 1.0   rest: 3 per row, photo 901 centred at half height
   1.0 – 2.8   zoom out to 20 per row about it (z = 3 · (20/3)^e, e easeInOut)
   3.4 – 4.4   the grid is dragged down 262 pt, until photo 906 sits where it rests at 3 per row
   4.6 – 6.4   zoom back in to 3 per row about photo 906 (cubic in, out with a 5 % overshoot)
   6.4 – 8.0   rest (the same frame as 0)

 Touch: pinch to zoom about the photo between the fingers (rubber band past 3 and 20 per row; on
 release it settles on the nearest layout in 0.35 s, ease out, with a light haptic); drag to scroll
 (thrown on release, 0.5 s ease out). 2 s after the last touch the grid zooms back to 3 per row
 (0.6 s), then scrolls back to its resting place (0.6 s) and the loop starts over.
 Reduced motion: no loop; a pinch still zooms but lands on the nearest layout at once, and drags
 scroll without the throw.
 */

const FAMILY = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
const INK = '#121214';
const SEGMENTS = ['Years', 'Months', 'All Photos'];

/** A tween that eases out (cubic): [from, to, start time, duration]. */
function tweenAt(t: number[], now: number) {
  'worklet';
  const u = clamp01((now - t[2]) / t[3]);
  return t[0] + (t[1] - t[0]) * (1 - Math.pow(1 - u, 3));
}
function still(v: number) {
  'worklet';
  return [v, v, 0, 1];
}

function tactile(strong: boolean) {
  if (strong) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

export function ContinuousZoomGrid({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [month, setMonth] = useState(0);
  const fonts = useMemo(
    () => ({
      chip: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '600' }),
      length: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '600' }),
    }),
    [],
  );
  const w = size.width;
  const h = size.height;

  const clock = useSharedValue(-1);
  const autoStart = useSharedValue(-1);
  /** The ease back: [when, z, anchor, fy], or []. */
  const returning = useSharedValue<number[]>([]);
  /** The manual zoom, kept as log z so a settle is even across layouts. */
  const zoom = useSharedValue(still(Math.log(3)));
  const anchor = useSharedValue(REST_ANCHOR);
  const fy = useSharedValue(still(0));
  const pinchStart = useSharedValue(-100);
  const dragStart = useSharedValue(-1e9);
  const lastLevel = useSharedValue(0);
  const resumeAt = useSharedValue(-1);

  const viewNow = (now: number): GridView => {
    'worklet';
    const a = autoStart.get();
    if (a >= 0 && now >= a && !reduced) return viewAt(now - a, w, h);
    const r = returning.get();
    if (r.length === 4) {
      const t = now - r[0];
      if (t < 0.6) return { z: r[1] * Math.pow(3 / r[1], easeInOut(t / 0.6)), anchor: r[2], fy: r[3] };
      // At 3 per row, switch to the resting photo (seamless) and scroll it home.
      const from = r[3] + ((row(REST_ANCHOR, 3) - row(r[2], 3)) * w) / 3;
      const u = easeInOut((t - 0.6) / 0.6);
      return { z: 3, anchor: REST_ANCHOR, fy: from + (h * 0.5 - from) * u };
    }
    return { z: Math.exp(tweenAt(zoom.get(), now)), anchor: anchor.get(), fy: tweenAt(fy.get(), now) };
  };

  useFrameCallback((info) => {
    if (h <= 0) return;
    const now = info.timestamp / 1000;
    if (clock.get() < 0) {
      fy.set(still(h * 0.5));
      if (autoPlay && !reduced) autoStart.set(now);
    }
    clock.set(now);
    const r = resumeAt.get();
    if (r >= 0 && now >= r) {
      resumeAt.set(-1);
      const v = viewNow(now);
      returning.set([now, v.z, v.anchor, v.fy]);
      autoStart.set(now + 1.2);
    }
  });

  const shown = useDerivedValue(() => viewNow(clock.get()));

  useAnimatedReaction(
    () => (w > 0 ? centreMonth(w, h, shown.get()) : 0),
    (m, previous) => {
      if (m !== previous) scheduleOnRN(setMonth, m);
    },
  );

  const takeOver = (now: number) => {
    'worklet';
    resumeAt.set(-1);
    const v = viewNow(now);
    zoom.set(still(Math.log(v.z)));
    anchor.set(v.anchor);
    fy.set(still(v.fy));
    autoStart.set(-1);
    returning.set([]);
  };

  const gesture = useMemo(() => {
    /** Re-anchors on the photo under a point (only ever at rest on a layout, so nothing moves). */
    const anchorAt = (px: number, py: number, now: number) => {
      'worklet';
      const v = viewNow(now);
      const n = LEVELS[nearestLevel(v.z)];
      if (Math.abs(v.z - n) > 0.01) return;
      const cell = w / n;
      const ra = row(v.anchor, n);
      const r = ra + Math.floor((py - v.fy) / cell + 0.5);
      const c = Math.min(n - 1, Math.max(0, Math.floor(px / cell)));
      anchor.set(Math.min(COUNT - 1, Math.max(0, r * n + c)));
      fy.set(still(v.fy + (r - ra) * cell));
    };
    const pinch = Gesture.Pinch()
      .onStart((e) => {
        const now = clock.get();
        takeOver(now);
        anchorAt(e.focalX, e.focalY, now);
        pinchStart.set(zoom.get()[1]);
        lastLevel.set(nearestLevel(Math.exp(zoom.get()[1])));
        dragStart.set(-1e9);
      })
      .onUpdate((e) => {
        if (pinchStart.get() < -50) return;
        let lz = pinchStart.get() - Math.log(e.scale);
        // Rubber band past the first and last layout.
        const lo = Math.log(MIN_Z);
        const hi = Math.log(MAX_Z);
        if (lz < lo) lz = lo - 0.25 * (1 - Math.exp((lz - lo) / 0.25));
        if (lz > hi) lz = hi + 0.25 * (1 - Math.exp(-(lz - hi) / 0.25));
        zoom.set(still(lz));
        const level = nearestLevel(Math.exp(lz));
        if (level !== lastLevel.get()) {
          lastLevel.set(level);
          scheduleOnRN(tactile, false);
        }
      })
      .onEnd(() => {
        const now = clock.get();
        pinchStart.set(-100);
        const lz = zoom.get()[1];
        zoom.set([lz, Math.log(LEVELS[nearestLevel(Math.exp(lz))]), now, reduced ? 0.001 : 0.35]);
        scheduleOnRN(tactile, true);
        if (autoPlay && !reduced) resumeAt.set(now + 2);
      });
    const drag = Gesture.Pan()
      .minDistance(8)
      .maxPointers(1)
      .onStart(() => {
        if (pinchStart.get() > -50) return;
        takeOver(clock.get());
        dragStart.set(fy.get()[1]);
      })
      .onUpdate((e) => {
        if (dragStart.get() < -1e8 || pinchStart.get() > -50) return;
        fy.set(still(dragStart.get() + e.translationY));
      })
      .onEnd((e) => {
        if (dragStart.get() < -1e8) return;
        const now = clock.get();
        const end = dragStart.get() + e.translationY;
        dragStart.set(-1e9);
        fy.set(reduced ? still(end) : [end, end + e.velocityY * 0.25, now, 0.5]);
        if (autoPlay && !reduced) resumeAt.set(now + 2);
      });
    return Gesture.Simultaneous(pinch, drag);
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [w, h, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (w > 0) drawGrid(canvas, w, h, insets.top, shown.get(), fonts);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  // The control's highlight: All Photos at 3 per row, Years at 20 (log scale).
  const seg = useDerivedValue(() => 2 - 2 * clamp01(Math.log(shown.get().z / MIN_Z) / Math.log(MAX_Z / MIN_Z)));
  const highlightStyle = useAnimatedStyle(() => ({ transform: [{ translateX: 4 + seg.get() * 86.7 }] }));

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {w > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={`Photo library, ${MONTHS.labels[month]}`}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View style={[styles.header, { top: insets.top + 4 }]} pointerEvents="none">
            <View style={styles.back} accessible accessibilityLabel="Back">
              <Svg width={9} height={16} viewBox="0 0 9 16">
                <SvgPath d="M8.1 1 L0.9 8 L8.1 15" stroke={INK} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </Svg>
            </View>
            <View style={styles.flex} />
            <View style={styles.select}>
              <Text style={styles.selectText}>Select</Text>
            </View>
          </View>
          <View style={[styles.title, { top: insets.top + 6 }]} pointerEvents="none">
            <Text style={styles.titleText}>Library</Text>
            <Text style={styles.subtitle}>{MONTHS.labels[month]}</Text>
          </View>

          <View style={[styles.control, { left: w / 2 - 135, top: h - Math.max(insets.bottom, 16) - 10 - 46 }]} pointerEvents="none">
            <Animated.View style={[styles.highlight, highlightStyle]} />
            <View style={styles.segments}>
              {SEGMENTS.map((label, i) => (
                <Segment key={label} label={label} index={i} seg={seg} />
              ))}
            </View>
          </View>
        </>
      ) : null}
    </View>
  );
}

function Segment({ label, index, seg }: { label: string; index: number; seg: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: 0.42 + 0.58 * Math.max(0, 1 - Math.abs(index - seg.get())) }));
  return (
    <Animated.Text style={[styles.segment, style]} numberOfLines={1}>
      {label}
    </Animated.Text>
  );
}

const shadow = { shadowColor: '#000000', shadowOpacity: 0.1, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 4 };

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FAFAFA' },
  header: { position: 'absolute', left: 16, right: 16, height: 40, flexDirection: 'row', alignItems: 'center' },
  back: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center', ...shadow },
  flex: { flex: 1 },
  select: { height: 36, borderRadius: 18, paddingHorizontal: 16, backgroundColor: '#FFFFFF', justifyContent: 'center', ...shadow },
  selectText: { fontSize: 15, fontWeight: '600', color: INK },
  title: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 1 },
  titleText: { fontSize: 17, fontWeight: '600', color: INK },
  subtitle: { fontSize: 12, fontWeight: '500', color: 'rgba(18,18,20,0.5)', fontVariant: ['tabular-nums'] },
  control: {
    position: 'absolute',
    width: 270,
    height: 46,
    borderRadius: 23,
    backgroundColor: 'rgba(255,255,255,0.88)',
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.06)',
    shadowColor: '#000000',
    shadowOpacity: 0.12,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  highlight: { position: 'absolute', top: 4, left: 0, width: 88, height: 36, borderRadius: 18, backgroundColor: 'rgba(18,18,20,0.08)' },
  segments: { flexDirection: 'row', height: 44, paddingHorizontal: 4, alignItems: 'center' },
  segment: { width: 86.7, textAlign: 'center', fontSize: 14, fontWeight: '600', color: INK },
});
