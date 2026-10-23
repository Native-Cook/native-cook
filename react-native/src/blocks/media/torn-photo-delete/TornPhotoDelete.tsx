import {
  BlurStyle,
  Canvas,
  ClipOp,
  PaintStyle,
  PathOp,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
  type SkFont,
  type SkImage,
  TileMode,
  matchFont,
  useImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
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

import {
  type CardBox,
  LOOP,
  type Scene,
  type Tear,
  TOAST_OUT,
  UNDO_PRESS,
  autoScene,
  coreOf,
  cutOf,
  dir,
  easeOutBack,
  heal,
  opening,
  regionOf,
  restOf,
  restScene,
  smoothstep,
  spanOf,
  transformOf,
} from './tear-model';

/*
 Torn Photo Delete — a day's instant photo you delete by tearing it in two. A port of the SwiftUI block
 (TornPhotoDelete.swift); the numbers are the same.

 Look: a pale page (#EFEFF1, a soft pink glow behind the camera button at the lower right). Header: a trash
 glyph at the left, "Selected Day" over "Thursday, 2 July" in the middle. The print: a cream card
 (#F7EEDD → #EFE3CC, corner 3 pt, soft shadow) 85 % of the width (at most 340 pt), a 5:6 photo inset
 16 pt with a faint glare, and "02/07/2026" in a 13 pt mono in its 72 pt bottom margin; centred at 47 %
 of the height. A hint under it, "Drag across the print to tear it". When the print is gone, a dark toast
 "Photo deleted · Undo" rises above the camera button and "Nothing kept for this day" shows where it was.

 The tear (geometry in tear-model.ts): a jagged cut runs from the edge the finger started on towards the
 finger; the two flaps shear apart into a wedge, showing the white paper core along the cut. Released
 under 70 % of the way across, the flaps close (critically damped, ω = 16 /s); past it, the cut finishes
 and the halves fall away, spinning outwards.

 autoPlay: LOOP = 9 s, timeline in tear-model.ts. A small grey disc shows the scripted finger.
 Touch: drag from anywhere on the print (the cut starts on the nearest edge); a light tick every 28 pt of
 tearing, a heavy one when it gives. Undo on the toast brings the print back. 2 s after the last touch the
 print comes back if it was torn and the loop starts again.
 Reduced motion: no loop. The cut still follows the finger; a finished tear fades the print out instead
 of dropping it, and it fades back in.
 */

const PHOTO = require('./assets/road.jpg');
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

type Layout = {
  width: number;
  height: number;
  card: CardBox;
  photoH: number;
  toastX: number;
  toastY: number;
  undoX: number;
  fabX: number;
  fabY: number;
  bottom: number;
};

function layoutFor(width: number, height: number, bottomInset: number): Layout {
  const w = Math.min(width * 0.85, 340);
  const photoH = (w - 32) * 1.2;
  const h = 16 + photoH + 72;
  const bottom = Math.max(bottomInset, 20);
  const toastX = (width - 264) / 2;
  return {
    width,
    height,
    card: { x: (width - w) / 2, y: height * 0.47 - h / 2, w, h },
    photoH,
    toastX,
    toastY: height - bottom - 128,
    undoX: toastX + 264 - 84,
    fabX: width - 24 - 28,
    fabY: height - bottom - 44,
    bottom,
  };
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  quietUntil: SharedValue<number>;
  once: SharedValue<boolean>;
  tear: SharedValue<Tear | null>;
  span: SharedValue<number>;
  edge: SharedValue<number[]>;
  healing: SharedValue<{ tear: Tear; from: number } | null>;
  committed: SharedValue<{ tear: Tear; at: number } | null>;
  /** 0 none, 1 tear, 2 undo, -1 not started. */
  gesture: SharedValue<number>;
  ticks: SharedValue<number>;
  active: SharedValue<boolean>;
  resumeAt: SharedValue<number>;
  idleAt: SharedValue<number>;
};

function sceneAt(m: Motion, at: number, l: Layout, reduced: boolean): Scene {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    let t = at - start;
    const once = m.once.get();
    if (once) t = Math.min(t, LOOP);
    const e = t % LOOP;
    const quiet = at < m.quietUntil.get();
    const s = autoScene(once && t >= LOOP ? LOOP : e, l.card, l.undoX + 42, l.toastY + 24, quiet);
    if (reduced) {
      s.cardScale = 1;
      s.cardLift = 0;
    }
    return s;
  }
  const s = restScene();
  const c = m.committed.get();
  if (c) {
    const f = at - c.at;
    const tr = { ...c.tear };
    if (reduced) {
      s.cardAlpha = 1 - smoothstep(0, 0.3, f);
      tr.fall = -1;
    } else {
      tr.fall = f;
      if (f >= 1.2) s.cardAlpha = 0;
    }
    if (s.cardAlpha > 0) s.tear = tr;
    s.toast = reduced ? smoothstep(0.35, 0.65, f) : easeOutBack((f - 0.35) / 0.4);
    s.empty = smoothstep(0.55, 0.95, f);
    s.hint = 1 - smoothstep(0, 0.3, f);
    return s;
  }
  const t = m.tear.get();
  const h = m.healing.get();
  if (t && t.length > 0) {
    s.tear = t;
  } else if (h) {
    const open = reduced ? 0 : heal(h.tear.open, at - h.from);
    if (open > 0) s.tear = { ...h.tear, open };
  }
  return s;
}

function polygon(points: number[]) {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
  p.close();
  return p;
}

function drawCard(canvas: SkCanvas, l: Layout, image: SkImage, font: SkFont) {
  'worklet';
  const w = l.card.w;
  const h = l.card.h;
  const paper = Skia.Paint();
  paper.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w, y: h }, [Skia.Color('#F7EEDD'), Skia.Color('#EFE3CC')], null, TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 3, 3), paper);
  // The photo, filled into its 5:6 window.
  const p = Skia.XYWHRect(16, 16, w - 32, l.photoH);
  canvas.save();
  canvas.clipRect(p, ClipOp.Intersect, true);
  const iw = image.width();
  const ih = image.height();
  const scale = Math.max(p.width / iw, p.height / ih);
  const dw = iw * scale;
  const dh = ih * scale;
  const paint = Skia.Paint();
  canvas.drawImageRect(image, Skia.XYWHRect(0, 0, iw, ih), Skia.XYWHRect(p.x + p.width / 2 - dw / 2, p.y + p.height / 2 - dh / 2, dw, dh), paint);
  // A faint glare across the gloss.
  const glare = Skia.Paint();
  glare.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: p.x, y: p.y },
      { x: p.x + p.width, y: p.y + p.height },
      [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.13), Float32Array.of(1, 1, 1, 0)],
      [0.25, 0.42, 0.6],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(p, glare);
  canvas.restore();
  const line = Skia.Paint();
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(0.5);
  line.setColor(Float32Array.of(0, 0, 0, 0.12));
  canvas.drawRect(Skia.XYWHRect(p.x + 0.25, p.y + 0.25, p.width - 0.5, p.height - 0.5), line);
  const ink = Skia.Paint();
  ink.setColor(Skia.Color('#292624'));
  const label = '02/07/2026';
  const tw = font.measureText(label).width;
  const m = font.getMetrics();
  canvas.drawText(label, w / 2 - tw / 2, p.y + p.height + 36 - (m.ascent + m.descent) / 2, ink, font);
}

function tactile(kind: 'light' | 'heavy' | 'select') {
  if (kind === 'heavy') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function TornPhotoDelete({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const image = useImage(PHOTO);
  const font = useMemo(() => matchFont({ fontFamily: MONO, fontSize: 13, fontWeight: '500' }), []);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => layoutFor(size.width, size.height, insets.bottom), [size.width, size.height, insets.bottom]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    quietUntil: useSharedValue(-1),
    once: useSharedValue(false),
    tear: useSharedValue<Tear | null>(null),
    span: useSharedValue(0),
    edge: useSharedValue<number[]>([]),
    healing: useSharedValue<{ tear: Tear; from: number } | null>(null),
    committed: useSharedValue<{ tear: Tear; at: number } | null>(null),
    gesture: useSharedValue(-1),
    ticks: useSharedValue(0),
    active: useSharedValue(false),
    resumeAt: useSharedValue(-1),
    idleAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      if (autoPlay && !reduced) m.autoStart.set(now);
      m.clock.set(now);
    }
    // 2 s after the last touch: bring the print back if it was torn, and start the loop again.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      if (m.committed.get()) {
        m.committed.set(null);
        m.autoStart.set(now - TOAST_OUT);
        m.quietUntil.set(now + LOOP);
        m.once.set(!autoPlay || reduced);
      } else if (autoPlay && !reduced) {
        m.tear.set(null);
        m.healing.set(null);
        m.autoStart.set(now);
      }
      m.idleAt.set(now + 2);
    }
    const idleAt = m.idleAt.get();
    if (idleAt >= 0 && now >= idleAt) {
      m.idleAt.set(-1);
      m.active.set(false);
    }
    // The clock only moves while something can move, so an idle screen draws nothing new.
    if ((m.autoStart.get() >= 0 && !reduced) || m.active.get()) m.clock.set(now);
  });

  const scene = useDerivedValue(() => (size.width > 0 ? sceneAt(m, m.clock.get(), l, reduced) : restScene()));

  const card = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
    const s = scene.get();
    if (!image || size.width === 0 || s.cardAlpha <= 0) return recorder.finishRecordingAsPicture();
    const w = l.card.w;
    const h = l.card.h;
    const layer = Skia.Paint();
    layer.setAlphaf(s.cardAlpha);
    canvas.saveLayer(layer);
    canvas.translate(l.card.x + w / 2, l.card.y + h / 2 - s.cardLift);
    canvas.scale(s.cardScale, s.cardScale);
    canvas.translate(-w / 2, -h / 2);

    const round = Skia.Path.Make();
    round.addRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 3, 3));
    const shadowPaint = Skia.Paint();
    shadowPaint.setColor(Float32Array.of(0, 0, 0, 0.16));
    shadowPaint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
    // Shadows of every piece first, then the pieces, so no piece's shadow falls on its neighbour.
    const shadow = (matrix: number[] | null, clip: number[] | null) => {
      const shape = clip ? Skia.Path.MakeFromOp(polygon(clip), round, PathOp.Intersect) : round;
      if (!shape) return;
      canvas.save();
      if (matrix) canvas.concat(matrix);
      canvas.translate(0, 8);
      canvas.drawPath(shape, shadowPaint);
      canvas.restore();
    };
    const corePaint = Skia.Paint();
    const piece = (matrix: number[] | null, clip: number[] | null, core: number[] | null, coreAlpha: number) => {
      canvas.save();
      if (matrix) canvas.concat(matrix);
      if (clip) canvas.clipPath(polygon(clip), ClipOp.Intersect, true);
      drawCard(canvas, l, image, font);
      if (core) {
        canvas.clipPath(round, ClipOp.Intersect, true);
        corePaint.setColor(Float32Array.of(0.995, 0.985, 0.965, coreAlpha));
        canvas.drawPath(polygon(core), corePaint);
      }
      canvas.restore();
    };

    const t = s.tear;
    if (!t || t.length <= 0) {
      shadow(null, null);
      piece(null, null, null, 1);
    } else {
      const cut = cutOf(t);
      const falling = t.fall >= 0;
      const coreAlpha = falling ? 1 : smoothstep(0, 0.02, t.open);
      if (!falling) shadow(null, restOf(t));
      shadow(transformOf(t, 1, w, h), regionOf(t, 1, cut));
      shadow(transformOf(t, -1, w, h), regionOf(t, -1, cut));
      if (!falling) piece(null, restOf(t), null, 1);
      for (const side of [1, -1]) {
        piece(transformOf(t, side, w, h), regionOf(t, side, cut), coreAlpha > 0.01 ? coreOf(t, side, cut) : null, coreAlpha);
      }
    }
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(() => {
    const inUndo = (x: number, y: number) => {
      'worklet';
      return x >= l.undoX - 8 && x <= l.toastX + 264 + 8 && y >= l.toastY - 8 && y <= l.toastY + 48 + 8;
    };
    const commit = (now: number) => {
      'worklet';
      const t = m.tear.get();
      if (!t || m.gesture.get() !== 1) return;
      m.gesture.set(0);
      const len = m.span.get() * 1.06;
      m.committed.set({ tear: { ...t, length: len, open: opening(len) }, at: now });
      m.tear.set(null);
      m.healing.set(null);
      scheduleOnRN(tactile, 'heavy');
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = m.clock.get();
        m.resumeAt.set(-1);
        m.idleAt.set(-1);
        m.active.set(true);
        const current = sceneAt(m, now, l, reduced);
        m.gesture.set(0);
        if (current.toast > 0.5 && inUndo(e.x, e.y)) {
          m.gesture.set(2);
          return;
        }
        const c = l.card;
        const resting = current.cardAlpha >= 1 && current.cardScale >= 0.999 && (!current.tear || current.tear.fall < 0) && !m.committed.get();
        const inCard = e.x >= c.x - 30 && e.x <= c.x + c.w + 30 && e.y >= c.y - 30 && e.y <= c.y + c.h + 30;
        if (!resting || !inCard) return;
        m.gesture.set(1);
        m.autoStart.set(-1);
        m.quietUntil.set(-1);
        m.once.set(false);
        m.healing.set(current.tear ? { tear: current.tear, from: now } : null);
        m.tear.set(null);
        m.ticks.set(0);
        // The nearest point on the card's edge.
        const px = Math.min(Math.max(e.x, c.x), c.x + c.w) - c.x;
        const py = Math.min(Math.max(e.y, c.y), c.y + c.h) - c.y;
        const d = [px, c.w - px, py, c.h - py];
        const i = d.indexOf(Math.min(d[0], d[1], d[2], d[3]));
        const edges = [[0, py], [c.w, py], [px, 0], [px, c.h]];
        m.edge.set(edges[i]);
      })
      .onUpdate((e) => {
        if (m.gesture.get() !== 1) return;
        const edge = m.edge.get();
        const px = e.x - l.card.x;
        const py = e.y - l.card.y;
        let t = m.tear.get();
        if (!t) {
          // Wait until the finger has moved into the card before choosing the direction.
          const dx = px - edge[0];
          const dy = py - edge[1];
          if (Math.hypot(dx, dy) <= 10) return;
          const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
          const s = spanOf(edge[0], edge[1], angle, l.card.w, l.card.h);
          if (s <= 40) return;
          m.span.set(s);
          t = { sx: edge[0], sy: edge[1], angle, length: 0, open: 0, seed: 101 + (Math.floor(m.clock.get()) % 97), fall: -1 };
        }
        const { ux, uy } = dir(t);
        const span = m.span.get();
        const len = Math.min(span * 1.06, Math.max(0, (px - edge[0]) * ux + (py - edge[1]) * uy));
        const k = Math.floor(len / 28);
        if (k > m.ticks.get()) scheduleOnRN(tactile, 'light');
        m.ticks.set(k);
        m.tear.set({ ...t, length: len, open: opening(len) });
        if (len >= span) commit(m.clock.get());
      })
      .onFinalize((e) => {
        const now = m.clock.get();
        const g = m.gesture.get();
        m.gesture.set(-1);
        if (g === 2 && inUndo(e.x, e.y)) {
          scheduleOnRN(tactile, 'select');
          m.committed.set(null);
          m.tear.set(null);
          m.healing.set(null);
          // Rejoin the loop where its own Undo is pressed; without autoPlay, play that part once.
          m.autoStart.set(now - UNDO_PRESS);
          m.quietUntil.set(now + LOOP);
          m.once.set(!autoPlay || reduced);
          m.idleAt.set(now + 2.5);
          return;
        }
        if (g === 1) {
          const t = m.tear.get();
          if (t && t.length > 0) {
            if (t.length >= m.span.get() * 0.7) {
              m.gesture.set(1);
              commit(now);
              m.gesture.set(-1);
            } else {
              m.healing.set({ tear: t, from: now });
              m.tear.set(null);
            }
          }
        }
        m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [l, reduced, autoPlay]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const emptyStyle = useAnimatedStyle(() => ({ opacity: scene.get().empty }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, scene.get().hint)) }));
  const toastStyle = useAnimatedStyle(() => {
    const t = scene.get().toast;
    return { opacity: Math.min(1, Math.max(0, t * 1.5)), transform: [{ translateY: reduced ? 0 : (1 - t) * 90 }] };
  });
  const undoStyle = useAnimatedStyle(() => {
    const p = scene.get().undoPress;
    return { backgroundColor: `rgba(255,255,255,${0.14 * p})`, transform: [{ scale: 1 - 0.06 * p }] };
  });
  const dotStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return {
      opacity: s.dotAlpha,
      transform: [{ translateX: s.dotX - 20 }, { translateY: s.dotY - 20 }, { scale: 1.25 - 0.25 * s.dotPress }],
    };
  });

  const ready = size.width > 0 && !!image;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {ready ? (
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill}>
            <Backdrop l={l} />
            <View style={[styles.header, { top: insets.top + 6 }]} pointerEvents="none">
              <View style={styles.headerTrash}>
                <Glyph d={TRASH} color="#8C8C94" width={1.6} size={22} />
              </View>
              <View style={styles.titles}>
                <Text style={styles.title}>Selected Day</Text>
                <Text style={styles.subtitle}>Thursday, 2 July</Text>
              </View>
            </View>
            <Animated.View style={[styles.empty, { top: l.card.y + l.card.h / 2 - 50 }, emptyStyle]} pointerEvents="none">
              <Glyph d={PHOTO_GLYPH} color="#B3B3BA" width={1.6} size={30} />
              <Text style={styles.emptyTitle}>Nothing kept for this day</Text>
              <Text style={styles.emptyBody}>Take today&apos;s photo with the camera.</Text>
            </Animated.View>
            <Animated.Text style={[styles.hint, { top: l.card.y + l.card.h + 22 }, hintStyle]}>Drag across the print to tear it</Animated.Text>
            <Canvas
              style={StyleSheet.absoluteFill}
              pointerEvents="none"
              accessible
              accessibilityLabel="Photo of 2 July 2026: a road through red rocks"
            >
              <Picture picture={card} />
            </Canvas>
            <Animated.View style={[styles.toastWrap, { left: l.toastX, top: l.toastY }, toastStyle]} pointerEvents="none">
              <View style={styles.toast}>
                <View style={styles.toastTrash}>
                  <Glyph d={TRASH} color="rgba(255,255,255,0.7)" width={1.5} size={16} />
                </View>
                <Text style={styles.toastText}>Photo deleted</Text>
                <View style={styles.toastSpacer} />
                <View style={styles.toastDivider} />
                <Animated.View style={[styles.undo, undoStyle]}>
                  <Text style={styles.undoText}>Undo</Text>
                </Animated.View>
              </View>
            </Animated.View>
            <Animated.View style={[styles.dot, dotStyle]} pointerEvents="none" />
          </View>
        </GestureDetector>
      ) : null}
    </View>
  );
}

/** The page: a pale ground, the pink glow and the camera button, the handle. */
function Backdrop({ l }: { l: Layout }) {
  const picture = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const w = l.width;
    const h = l.height;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const ground = Skia.Paint();
    ground.setColor(Skia.Color('#EFEFF1'));
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), ground);
    const glow = (x: number, y: number, r: number, rgb: number[], a: number) => {
      const p = Skia.Paint();
      p.setShader(
        Skia.Shader.MakeRadialGradient({ x, y }, r, [Float32Array.of(rgb[0], rgb[1], rgb[2], a), Float32Array.of(rgb[0], rgb[1], rgb[2], 0)], null, TileMode.Clamp),
      );
      canvas.drawRect(Skia.XYWHRect(0, 0, w, h), p);
    };
    glow(w / 2, h * 0.42, w * 0.8, [1, 1, 1], 0.7);
    glow(l.fabX, l.fabY, 120, [1, 0.55, 0.66], 0.42);
    // The handle.
    const hy = h - l.bottom - 14;
    const chevron = Skia.Path.Make();
    chevron.moveTo(w / 2 - 9, hy - 3);
    chevron.lineTo(w / 2, hy + 2);
    chevron.lineTo(w / 2 + 9, hy - 3);
    const stroke = Skia.Paint();
    stroke.setStyle(PaintStyle.Stroke);
    stroke.setStrokeWidth(2);
    stroke.setStrokeCap(StrokeCap.Round);
    stroke.setStrokeJoin(StrokeJoin.Round);
    stroke.setColor(Skia.Color('#B3B3BA'));
    canvas.drawPath(chevron, stroke);
    return recorder.finishRecordingAsPicture();
  }, [l]);
  return (
    <>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.fab, { left: l.fabX - 28, top: l.fabY - 28 }]} pointerEvents="none">
        <LinearGradient colors={['#FF8FA8', '#FF5C82']} style={styles.fabFill}>
          <Glyph d={CAMERA} color="#FFFFFF" width={1.8} size={24} />
        </LinearGradient>
      </View>
    </>
  );
}

/** Line glyphs on a 24 × 24 grid. */
const TRASH = 'M4 6.5H20 M9.5 6.5V4H14.5V6.5 M6 6.5L7 20H17L18 6.5 M10.2 10L10.5 16.5 M13.8 10L13.5 16.5';
const CAMERA = 'M3 8.5V18.5H21V8.5H16.5L15 5.5H9L7.5 8.5Z M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0';
const PHOTO_GLYPH =
  'M5.5 4H18.5A2.5 2.5 0 0 1 21 6.5V17.5A2.5 2.5 0 0 1 18.5 20H5.5A2.5 2.5 0 0 1 3 17.5V6.5A2.5 2.5 0 0 1 5.5 4Z M3.5 17L9 11.5L13.5 16L16 13.5L20.5 18 M14 8.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0';

function Glyph({ d, color, width, size }: { d: string; color: string; width: number; size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={(width * 24) / size} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EFEFF1' },
  header: { position: 'absolute', left: 0, right: 0, height: 44, alignItems: 'center', justifyContent: 'center' },
  headerTrash: { position: 'absolute', left: 24, top: 11 },
  titles: { alignItems: 'center', gap: 2 },
  title: { fontSize: 15, fontWeight: '600', color: '#54545C' },
  subtitle: { fontSize: 12, color: '#8C8C94' },
  empty: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 10 },
  emptyTitle: { fontSize: 15, fontWeight: '500', color: '#73737A' },
  emptyBody: { fontSize: 13, color: '#999AA1' },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 13, color: '#8F8F96' },
  fab: {
    position: 'absolute',
    width: 56,
    height: 56,
    borderRadius: 28,
    shadowColor: '#FF5C82',
    shadowOpacity: 0.35,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  fabFill: { flex: 1, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  toastWrap: {
    position: 'absolute',
    width: 264,
    height: 48,
    borderRadius: 24,
    shadowColor: '#000000',
    shadowOpacity: 0.18,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 10,
  },
  toast: { flex: 1, flexDirection: 'row', alignItems: 'center', borderRadius: 24, overflow: 'hidden', backgroundColor: '#1C1C1F' },
  toastTrash: { marginLeft: 18 },
  toastText: { marginLeft: 10, fontSize: 15, fontWeight: '500', color: 'rgba(255,255,255,0.92)' },
  toastSpacer: { flex: 1, minWidth: 8 },
  toastDivider: { width: 1, height: 22, backgroundColor: 'rgba(255,255,255,0.14)' },
  undo: { width: 83, height: 48, alignItems: 'center', justifyContent: 'center' },
  undoText: { fontSize: 15, fontWeight: '600', color: '#FF9EB3' },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(89,89,97,0.28)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.85)',
  },
});
