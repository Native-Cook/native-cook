import { BlurStyle, Canvas, ClipOp, Picture, Skia, type SkImage, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
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
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { FORMATS, LOOP, SCATTER, centerAt, critical, pageAt, shapeAt, spring } from './model';

/*
 Morphing Print Layout — a film picker where one white print frame morphs between formats. A port of the
 SwiftUI block (MorphingPrintLayout.swift); the numbers are the same.

 Look: a white top strip (nav bar: back arrow, "Select Film" with a small black "?" badge, a sliders and
 a share glyph; under it a one-line pager of format names in 24 pt medium, the selected one black, the
 others grey, and five 6 pt dots). Below it an orange stage, #F4B043 at the top to #EE4D0F at the
 bottom, with the print frame centred in it (white, corner 3 pt, a warm shadow), its square wells filled
 with six summer photos. Under the frame a caption for the format; at the bottom an export button and a
 white "Choose photos" pill.

 Layouts, morph and autoPlay timeline (LOOP = 7 s): model.ts. The names slide with the page (each centred
 by its measured width, 20 pt apart), their ink blending from black to grey over one page; the caption
 crossfades over half a page each way, one caption at a time.

 Touch: drag sideways anywhere to scrub the page (170 pt a page, rubber-banded at 35 % past the ends); a
 light haptic at each format. On release it springs to the nearest format, thrown a quarter of the way by
 the finger's speed (the travel a 0.4 s glide at that speed would add). Tapping a name goes to it. 2 s
 after the last touch the frame sweeps back to Single and the loop starts again.
 Reduced motion: no loop and no scattering; a drag still scrubs, and release snaps at once.
 */

const PAGE_WIDTH = 170;
const INK = '#121214';
const PHOTOS = [
  require('./assets/palm.jpg'),
  require('./assets/shore.jpg'),
  require('./assets/sunrise.jpg'),
  require('./assets/village.jpg'),
  require('./assets/pool.jpg'),
  require('./assets/boardwalk.jpg'),
];

type Layout = {
  width: number;
  height: number;
  top: number;
  k: number;
  stripBottom: number;
  bottomY: number;
  centerX: number;
  centerY: number;
  captionY: number;
};

function layoutFor(width: number, height: number, top: number, bottomInset: number): Layout {
  const k = Math.min(width, 430) / 393;
  const stripBottom = top + 112;
  const bottomY = height - Math.max(bottomInset, 20) - 34;
  const centerY = (stripBottom + bottomY - 66) / 2;
  return { width, height, top, k, stripBottom, bottomY, centerX: width / 2, centerY, captionY: centerY + 186 * k };
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  dragging: SharedValue<boolean>;
  dragFrom: SharedValue<number>;
  dragOffset: SharedValue<number>;
  /** [date, from, to, sweep (1) or spring (0)] or []. */
  glide: SharedValue<number[]>;
  resting: SharedValue<number>;
  lastTick: SharedValue<number>;
  active: SharedValue<boolean>;
  resumeAt: SharedValue<number>;
  joinAt: SharedValue<number>;
};

function rubber(p: number) {
  'worklet';
  const last = FORMATS.length - 1;
  if (p < 0) return p * 0.35;
  if (p > last) return last + (p - last) * 0.35;
  return p;
}

function pageOf(m: Motion, at: number, reduced: boolean) {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) return pageAt((at - start) % LOOP);
  if (m.dragging.get()) return rubber(m.dragFrom.get() - m.dragOffset.get() / PAGE_WIDTH);
  const g = m.glide.get();
  if (g.length === 4) {
    const u = at - g[0];
    const e = reduced ? 1 : g[3] > 0 ? critical(u) : spring(u);
    return g[1] + (g[2] - g[1]) * e;
  }
  return m.resting.get();
}

function drawFrame(p: number, l: Layout, images: SkImage[], reduced: boolean) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
  const s = shapeAt(p, reduced);
  const k = l.k;
  const w = s.w * k;
  const h = s.h * k;
  const ox = l.centerX - w / 2;
  const oy = l.centerY - h / 2;
  // The big frame, fading while the prints are loose.
  const frameAlpha = 1 - Math.min(1, s.loose * 1.6);
  if (frameAlpha > 0.001) {
    const shadow = Skia.Paint();
    shadow.setColor(Float32Array.of(0.45, 0.12, 0, 0.28 * frameAlpha));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 11, true));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox, oy + 12, w, h), 3, 3), shadow);
    const white = Skia.Paint();
    white.setColor(Float32Array.of(1, 1, 1, frameAlpha));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox, oy, w, h), 3, 3), white);
  }
  // Wells, from the last to the first, so a well that splits off comes out from under its neighbour.
  const paint = Skia.Paint();
  for (let i = s.shown - 1; i >= 0; i--) {
    const wl = s.wells[i];
    const x = ox + wl.x * k;
    const y = oy + wl.y * k;
    const size = wl.s * k;
    canvas.save();
    if (s.loose > 0 && i < 4) {
      const sc = SCATTER[i];
      canvas.translate(sc[0] * k * s.loose, sc[1] * k * s.loose);
      canvas.rotate(sc[2] * s.loose, x + size / 2, y + size / 2);
      // Its own little print border.
      const b = 8 * k * s.loose;
      const border = Skia.RRectXY(Skia.XYWHRect(x - b, y - b, size + 2 * b, size + b + 22 * k * s.loose), 2, 2);
      const bs = Skia.Paint();
      bs.setColor(Float32Array.of(0.45, 0.12, 0, 0.3 * s.loose));
      bs.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
      canvas.save();
      canvas.translate(0, 8);
      canvas.drawRRect(border, bs);
      canvas.restore();
      const bw = Skia.Paint();
      bw.setColor(Float32Array.of(1, 1, 1, 1));
      canvas.drawRRect(border, bw);
    }
    canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x, y, size, size), 2, 2), ClipOp.Intersect, true);
    const img = images[i];
    canvas.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x, y, size, size), paint);
    canvas.restore();
  }
  return recorder.finishRecordingAsPicture();
}

function tick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MorphingPrintLayout({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const i0 = useImage(PHOTOS[0]);
  const i1 = useImage(PHOTOS[1]);
  const i2 = useImage(PHOTOS[2]);
  const i3 = useImage(PHOTOS[3]);
  const i4 = useImage(PHOTOS[4]);
  const i5 = useImage(PHOTOS[5]);
  const images = useMemo(() => (i0 && i1 && i2 && i3 && i4 && i5 ? [i0, i1, i2, i3, i4, i5] : null), [i0, i1, i2, i3, i4, i5]);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => layoutFor(size.width, size.height, insets.top, insets.bottom), [size.width, size.height, insets.top, insets.bottom]);
  // The names' measured widths; their centres sit in a row, 20 pt apart.
  const widths = useSharedValue<number[]>(FORMATS.map(() => 0));
  const [, setMeasured] = useState<number[]>(FORMATS.map(() => 0));

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    dragging: useSharedValue(false),
    dragFrom: useSharedValue(0),
    dragOffset: useSharedValue(0),
    glide: useSharedValue<number[]>([]),
    resting: useSharedValue(0),
    lastTick: useSharedValue(0),
    active: useSharedValue(false),
    resumeAt: useSharedValue(-1),
    joinAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      if (autoPlay && !reduced) m.autoStart.set(now);
      m.clock.set(now);
    }
    // 2 s after the last touch: sweep back to Single (1 s), then join the loop in its final rest.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      if (!autoPlay || reduced) {
        m.active.set(false);
      } else {
        const p = pageOf(m, now, reduced);
        if (p > 0.01) {
          m.glide.set([now, p, 0, 1]);
          m.resting.set(0);
          m.joinAt.set(now + 1);
        } else {
          m.joinAt.set(now);
        }
      }
    }
    const joinAt = m.joinAt.get();
    if (joinAt >= 0 && now >= joinAt) {
      m.joinAt.set(-1);
      m.glide.set([]);
      m.autoStart.set(now - 6.9);
    }
    if ((m.autoStart.get() >= 0 && !reduced) || m.active.get()) m.clock.set(now);
  });

  const page = useDerivedValue(() => pageOf(m, m.clock.get(), reduced));
  const frame = useDerivedValue(() => {
    if (!images || size.width === 0) {
      // An empty picture: finishing a recorder that never began recording crashes the app.
      const recorder = Skia.PictureRecorder();
      recorder.beginRecording(Skia.XYWHRect(0, 0, 1, 1));
      return recorder.finishRecordingAsPicture();
    }
    return drawFrame(page.get(), l, images, reduced);
  });
  const centers = useDerivedValue(() => {
    const ws = widths.get();
    const xs: number[] = [];
    let x = 0;
    for (const w of ws) {
      xs.push(x + w / 2);
      x += w + 20;
    }
    return xs;
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin(() => {
          const now = m.clock.get();
          m.resumeAt.set(-1);
          m.joinAt.set(-1);
          m.active.set(true);
          const p = pageOf(m, now, reduced);
          m.autoStart.set(-1);
          m.glide.set([]);
          m.dragFrom.set(p);
          m.dragOffset.set(0);
          m.dragging.set(true);
          m.lastTick.set(Math.round(p));
        })
        .onUpdate((e) => {
          m.dragOffset.set(e.translationX);
          const p = pageOf(m, m.clock.get(), reduced);
          const k = Math.round(p);
          if (k !== m.lastTick.get() && p >= -0.2 && p <= FORMATS.length - 1 + 0.2) {
            m.lastTick.set(k);
            scheduleOnRN(tick);
          }
        })
        .onFinalize((e) => {
          const now = m.clock.get();
          const p = rubber(m.dragFrom.get() - e.translationX / PAGE_WIDTH);
          m.dragging.set(false);
          const tap = Math.abs(e.translationX) < 6 && Math.abs(e.translationY) < 6;
          let target = Math.round(p);
          if (tap) {
            // A tap: on a name, go to it.
            if (e.y < l.stripBottom && e.y > l.top + 44) {
              const xs = centers.get();
              const ws = widths.get();
              const cp = centerAt(p, xs);
              for (let i = 0; i < xs.length; i++) {
                const cx = l.width / 2 + xs[i] - cp;
                if (Math.abs(e.x - cx) < ws[i] / 2 + 10) target = i;
              }
            }
          } else {
            target = Math.round(p - ((e.velocityX * 0.4) / PAGE_WIDTH) * 0.25);
          }
          target = Math.min(FORMATS.length - 1, Math.max(0, target));
          if (target !== Math.round(p) || tap) scheduleOnRN(tick);
          m.glide.set([now, p, target, 0]);
          m.resting.set(target);
          m.resumeAt.set(now + 2);
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [l, reduced],
  );

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };
  const onName = (i: number) => (e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    setMeasured((prev) => {
      if (Math.abs(prev[i] - w) < 0.5) return prev;
      const next = [...prev];
      next[i] = w;
      widths.set(next);
      return next;
    });
  };

  const ready = size.width > 0 && !!images;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {ready ? (
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill}>
            <LinearGradient colors={['#F4B043', '#EE4D0F']} style={[styles.stage, { top: l.stripBottom }]} pointerEvents="none" />
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible accessibilityLabel="Print layout preview">
              <Picture picture={frame} />
            </Canvas>
            {FORMATS.map((f, i) => (
              <Caption key={f.name} index={i} text={f.caption} page={page} top={l.captionY - 11} />
            ))}
            <View style={[styles.bottom, { top: l.bottomY - 24 }]} pointerEvents="none">
              <View style={styles.export}>
                <Glyph d={EXPORT} color="#FFFFFF" width={1.8} size={22} />
              </View>
              <View style={styles.pill}>
                <Text style={styles.pillText}>Choose photos</Text>
              </View>
            </View>

            <View style={[styles.strip, { height: l.stripBottom }]} pointerEvents="none">
              <View style={[styles.nav, { top: l.top }]}>
                <Glyph d={BACK} color={INK} width={1.8} size={22} />
                <View style={styles.flex} />
                <Glyph d={SLIDERS} color={INK} width={1.6} size={22} />
                <Glyph d={SHARE} color={INK} width={1.6} size={22} />
              </View>
              <View style={[styles.titleRow, { top: l.top }]}>
                <Text style={styles.title}>Select Film</Text>
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>?</Text>
                </View>
              </View>
              {FORMATS.map((f, i) => (
                <Name
                  key={f.name}
                  index={i}
                  text={f.name}
                  page={page}
                  centers={centers}
                  widths={widths}
                  half={l.width / 2}
                  top={l.top + 44 + 26}
                  onLayout={onName(i)}
                />
              ))}
              <View style={[styles.dots, { top: l.top + 44 + 50 }]}>
                {FORMATS.map((f, i) => (
                  <Dot key={f.name} index={i} page={page} />
                ))}
              </View>
            </View>
          </View>
        </GestureDetector>
      ) : null}
    </View>
  );
}

function Name({
  index,
  text,
  page,
  centers,
  widths,
  half,
  top,
  onLayout,
}: {
  index: number;
  text: string;
  page: SharedValue<number>;
  centers: SharedValue<number[]>;
  widths: SharedValue<number[]>;
  half: number;
  top: number;
  onLayout: (e: LayoutChangeEvent) => void;
}) {
  const style = useAnimatedStyle(() => {
    const p = page.get();
    const xs = centers.get();
    const d = Math.min(1, Math.abs(index - p));
    const g = Math.round(255 * (0.07 + (0.77 - 0.07) * d));
    return {
      color: `rgb(${g},${g},${g})`,
      opacity: widths.get()[index] > 0 ? 1 : 0,
      transform: [{ translateX: half + xs[index] - centerAt(p, xs) - widths.get()[index] / 2 }],
    };
  });
  return (
    <Animated.Text onLayout={onLayout} style={[styles.name, { top: top - 15 }, style]}>
      {text}
    </Animated.Text>
  );
}

function Dot({ index, page }: { index: number; page: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const a = Math.max(0, 1 - Math.abs(index - page.get()));
    const g = Math.round(255 * (0.84 - 0.77 * a));
    return { backgroundColor: `rgb(${g},${g},${g})` };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

function Caption({ index, text, page, top }: { index: number; text: string; page: SharedValue<number>; top: number }) {
  // One caption at a time: each is gone half way to the next.
  const style = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - 2 * Math.abs(index - page.get())) }));
  return (
    <Animated.Text style={[styles.caption, { top }, style]} pointerEvents="none">
      {text}
    </Animated.Text>
  );
}

/** Line glyphs on a 24 × 24 grid. */
const BACK = 'M20 12H4 M10 6L4 12L10 18';
const SLIDERS =
  'M3 6H21 M3 12H21 M3 18H21 M13 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M6 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M11 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0';
const SHARE = 'M12 3.5V15 M7.5 8L12 3.5L16.5 8 M8 11H5V21H19V11H16';
const EXPORT = 'M12 4V15 M7.5 10.5L12 15L16.5 10.5 M4 15V20H20V15';

function Glyph({ d, color, width, size }: { d: string; color: string; width: number; size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={(width * 24) / size} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' },
  stage: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  strip: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: '#FFFFFF', overflow: 'hidden' },
  nav: { position: 'absolute', left: 20, right: 20, height: 44, flexDirection: 'row', alignItems: 'center', gap: 18 },
  flex: { flex: 1 },
  titleRow: { position: 'absolute', left: 0, right: 0, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  title: { fontSize: 15, fontWeight: '600', color: INK },
  badge: { width: 15, height: 15, borderRadius: 7.5, backgroundColor: INK, alignItems: 'center', justifyContent: 'center' },
  badgeText: { fontSize: 10, fontWeight: '700', color: '#FFFFFF' },
  name: { position: 'absolute', left: 0, fontSize: 24, lineHeight: 30, fontWeight: '500' },
  dots: { position: 'absolute', left: 0, right: 0, height: 6, flexDirection: 'row', justifyContent: 'center', gap: 8 },
  dot: { width: 6, height: 6, borderRadius: 3 },
  caption: { position: 'absolute', left: 0, right: 0, height: 22, lineHeight: 22, textAlign: 'center', fontSize: 15, fontWeight: '500', color: 'rgba(255,255,255,0.92)' },
  bottom: { position: 'absolute', left: 24, right: 24, height: 48, flexDirection: 'row', alignItems: 'center', gap: 12 },
  export: { width: 48, height: 48, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.22)', alignItems: 'center', justifyContent: 'center' },
  pill: { flex: 1, height: 48, borderRadius: 24, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  pillText: { fontSize: 16, fontWeight: '600', color: INK },
});
