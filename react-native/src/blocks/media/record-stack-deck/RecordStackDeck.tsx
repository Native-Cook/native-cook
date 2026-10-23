import { BlurStyle, Canvas, Picture, Skia, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
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
import { scheduleOnRN } from 'react-native-worklets';

import { ALBUMS, drawVinyl, hexA, makeCovers } from './art';
import {
  COUNT,
  DRAG_UNIT,
  FLAT_SCALE,
  FOCUS_Y,
  LOOP,
  OPEN_LENGTH,
  SLEEVE_SHIFT,
  VINYL_RADIUS,
  VINYL_SHIFT,
  clamp01,
  closing,
  easeInOut,
  fadeOf,
  loopOpen,
  loopRing,
  loopScroll,
  offsetOf,
  opening,
  project,
  settleAt,
  sleeveMatrix,
  smoothstep,
  tiltOf,
  topEdge,
  topFade,
  yOf,
} from './motion';

/*
 Record Stack Deck — a crate of ten records shown as a vertical 3D deck; hold one and it rises,
 flattens, and its vinyl slides half out of the sleeve, spinning. A port of the SwiftUI block
 (RecordStackDeck.swift); the numbers are the same.

 Look: black screen lit from above and below by the colour of the record at the focus. The
 sleeves (squares 0.78 × the screen width, covers drawn in code, invented albums) lie stacked at
 42 % of the height: the one at the focus is seen edge-on as a thin strip, the others open up
 with distance (the cosine of the tilt is 0.02 + 0.08 a − 0.0065 a², a = |offset|) and sit
 26 a + 11 a² pt from the focus, each titled in small type above its strip. Each sleeve is drawn
 through a perspective matrix (camera 1400 pt away, as Core Animation's m34 = −1/1400 in the
 Swift version). A white ring marks the focus. Under the deck a large caption over a fade.

 autoPlay timeline (LOOP = 10 s, motion.ts):
   0.0 – 0.4    rest on the first record.
   0.4 – 1.1    one record down (ease-out-back), 1.1 – 1.9 two more (ease-out).
   1.9 – 2.8    the ring fills as if held: sixteen ticks light up around it.
   2.8 – 3.6    the record rises out of the deck and flattens (ease-out-back) while the others
                dim and spread; 3.6 – 4.2 its vinyl slides half out to the right (the sleeve
                moves left), spinning at 200 °/s with the cover as its label; the caption
                changes to the album's title.
   6.6 – 8.0    the vinyl slides back in and the sleeve returns to the deck.
   8.0 – 9.7    a long flick through seven records (ease-out-quart) to the first one again:
                the crate has turned once, so 10.0 is the same frame as 0.0.

 Touch: drag up or down to turn the deck (44 pt per record, a selection tick per record); on
 release it is thrown by its speed and settles on a record (critically damped spring, ω 12).
 Tap a strip to bring it to the focus. Hold the focus (0.8 s, the ring fills) or tap it to open
 the record; tap anywhere to put it back. 2 s after the last touch the record goes back, the
 deck settles on the first record and the loop starts again.
 Reduced motion: no loop and no spin; opening and closing cross-fade in 0.25 s; the deck still
 follows a finger and settles without being thrown.
 */

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

type Pose = { scroll: number; ring: number; open: number; spin: number; fade: number };

/** The block's state on the UI thread; times are seconds on the frame clock, -1 or [] for "not set". */
type Motion = {
  now: SharedValue<number>;
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /** The settling deck: [since, from, speed, to]. */
  settle: SharedValue<number[]>;
  /** The deck under a finger, or NaN. */
  dragScroll: SharedValue<number>;
  dragFrom: SharedValue<number>;
  /** The ring being held: [since, duration] or []. */
  hold: SharedValue<number[]>;
  /** Open the record when the ring is full: 1 while a finger holds it, 2 after a tap. */
  holdOpens: SharedValue<number>;
  /** Opening (1) or closing (0) since a time: [since, opening] or []. */
  openMove: SharedValue<number[]>;
  touching: SharedValue<number>;
  moved: SharedValue<number>;
  lastTick: SharedValue<number>;
  busyUntil: SharedValue<number>;
  resumeAt: SharedValue<number>;
};

function tactile(kind: 'tick' | 'light' | 'medium') {
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function openValue(m: Motion, at: number) {
  'worklet';
  const o = m.openMove.get();
  if (o.length !== 2) return 0;
  const e = Math.max(0, at - o[0]);
  if (e >= OPEN_LENGTH) return o[1] === 1 ? 2 : 0;
  return o[1] === 1 ? opening(e) : closing(e);
}

function poseAt(m: Motion, at: number, reduced: boolean): Pose {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    const t = (at - start) % LOOP;
    return { scroll: loopScroll(t), ring: loopRing(t), open: loopOpen(t), spin: 200 * t, fade: -1 };
  }
  const s = m.settle.get();
  const drag = m.dragScroll.get();
  const scroll = Number.isNaN(drag) ? settleAt(s[1], s[2], s[3], Math.max(0, at - s[0])) : drag;
  const h = m.hold.get();
  const ring = h.length === 2 ? clamp01((at - h[0]) / h[1]) : 0;
  let open = openValue(m, at);
  let fade = -1;
  if (reduced) {
    const o = m.openMove.get();
    if (o.length === 2) {
      const f = clamp01((at - o[0]) / 0.25);
      fade = o[1] === 1 ? f : 1 - f;
    } else {
      fade = 0;
    }
    open = 2;
  }
  return { scroll, ring, open, spin: reduced ? 0 : 200 * (at % 1800), fade };
}

/** Freezes whatever plays into the hand-driven state. */
function takeOver(m: Motion, now: number) {
  'worklet';
  m.resumeAt.set(-1);
  const start = m.autoStart.get();
  if (start < 0) return;
  m.autoStart.set(-1);
  if (now < start) return;
  const t = (now - start) % LOOP;
  const s = loopScroll(t);
  m.settle.set([now, s, 0, Math.round(s)]);
  if (loopOpen(t) > 0) {
    // Carry on the open record; a closing one finishes closing.
    const closingNow = t >= 6.6;
    const e = closingNow ? t - 6.6 : Math.min(t - 2.8, OPEN_LENGTH);
    m.openMove.set([now - e, closingNow ? 0 : 1]);
  } else {
    m.openMove.set([]);
  }
  m.hold.set([]);
}

export function RecordStackDeck({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [focus, setFocus] = useState(0);
  const w = size.width;
  const h = size.height;
  const s = 0.78 * w;
  const cy = h * FOCUS_Y;
  const captionTop = h - Math.max(insets.bottom, 16) - 40 - 92;
  const covers = useMemo(() => (s > 0 ? makeCovers(s) : null), [s]);

  const m: Motion = {
    now: useSharedValue(-1),
    clock: useSharedValue(0),
    autoStart: useSharedValue(-1),
    settle: useSharedValue<number[]>([0, 0, 0, 0]),
    dragScroll: useSharedValue(NaN),
    dragFrom: useSharedValue(0),
    hold: useSharedValue<number[]>([]),
    holdOpens: useSharedValue(0),
    openMove: useSharedValue<number[]>([]),
    touching: useSharedValue(0),
    moved: useSharedValue(0),
    lastTick: useSharedValue(0),
    busyUntil: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
  };

  const openRecord = (now: number) => {
    'worklet';
    m.hold.set([]);
    m.holdOpens.set(0);
    m.openMove.set([now, 1]);
    scheduleOnRN(tactile, 'medium');
    if (reduced) m.busyUntil.set(now + 0.4);
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.now.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.now.set(now);
    if (!reduced || now < m.busyUntil.get() || m.touching.get() === 1) m.clock.set(now);
    // A held or tapped ring that has filled opens the record.
    const hd = m.hold.get();
    if (hd.length === 2 && now - hd[0] >= hd[1] && (m.holdOpens.get() === 2 || (m.holdOpens.get() === 1 && m.touching.get() === 1))) {
      openRecord(now);
    }
    // 2 s after the last touch: put the record back, settle on the first record, start the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt && m.touching.get() === 0) {
      m.resumeAt.set(-1);
      let at = now;
      const o = m.openMove.get();
      if (o.length === 2 && o[1] === 1) {
        // Still opening: let it finish first.
        if (at - o[0] < OPEN_LENGTH) at = o[0] + OPEN_LENGTH;
        m.openMove.set([at, 0]);
        at += OPEN_LENGTH;
      }
      const sc = poseAt(m, at, reduced).scroll;
      m.settle.set([at, sc, 0, Math.round(sc / COUNT) * COUNT]);
      m.autoStart.set(at + 0.8);
    }
  });

  const pose = useDerivedValue(() => poseAt(m, m.clock.get(), reduced));

  useAnimatedReaction(
    () => (((Math.round(pose.get().scroll) % COUNT) + COUNT) % COUNT),
    (k, previous) => {
      if (k !== previous) scheduleOnRN(setFocus, k);
    },
  );

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          const now = m.now.get();
          m.touching.set(1);
          m.moved.set(0);
          takeOver(m, now);
          const sc = poseAt(m, now, reduced).scroll;
          m.dragFrom.set(sc);
          m.lastTick.set(Math.floor(sc + 0.5));
          // Holding the focus fills the ring; when it is full the record opens.
          const o = m.openMove.get();
          if ((o.length === 0 || (o[1] === 0 && openValue(m, now) === 0)) && Math.abs(e.y - cy) < 34) {
            m.openMove.set([]);
            m.hold.set([now, 0.8]);
            m.holdOpens.set(1);
          }
        })
        .onUpdate((e) => {
          const o = m.openMove.get();
          if (o.length === 2 && o[1] === 1) return;
          if (m.moved.get() === 0 && Math.abs(e.translationY) > 8) {
            m.moved.set(1);
            m.hold.set([]);
            m.holdOpens.set(0);
          }
          if (m.moved.get() === 1) {
            const sc = m.dragFrom.get() - e.translationY / DRAG_UNIT;
            m.dragScroll.set(sc);
            const k = Math.floor(sc + 0.5);
            if (k !== m.lastTick.get()) {
              m.lastTick.set(k);
              scheduleOnRN(tactile, 'tick');
            }
          }
        })
        .onFinalize((e) => {
          const now = m.now.get();
          m.touching.set(0);
          const o = m.openMove.get();
          const hd = m.hold.get();
          if (m.moved.get() === 1) {
            const drag = m.dragScroll.get();
            const sc = Number.isNaN(drag) ? m.dragFrom.get() : drag;
            const v = reduced ? 0 : -e.velocityY / DRAG_UNIT;
            m.settle.set([now, sc, v, Math.round(sc + v * 0.25)]);
            m.dragScroll.set(NaN);
          } else if (o.length === 2 && o[1] === 1) {
            // A tap on an open record puts it back.
            if (now - o[0] >= OPEN_LENGTH) {
              m.openMove.set([now, 0]);
              scheduleOnRN(tactile, 'light');
              if (reduced) m.busyUntil.set(now + 0.4);
            }
          } else if (hd.length === 2) {
            // A tap on the focus: the ring finishes quickly, then the record opens.
            const progress = clamp01((now - hd[0]) / hd[1]);
            m.hold.set([now - progress * 0.3, 0.3]);
            m.holdOpens.set(2);
          } else {
            // A tap on another strip brings it to the focus.
            const sc = poseAt(m, now, reduced).scroll;
            let best = 0;
            let bestDistance = Infinity;
            for (let i = 0; i < COUNT; i++) {
              const d = offsetOf(i, sc);
              const dist = Math.abs(cy + yOf(d) - e.y);
              if (dist < bestDistance && fadeOf(d) > 0.3) {
                bestDistance = dist;
                best = d;
              }
            }
            m.settle.set([now, sc, 0, Math.round(sc + best)]);
            scheduleOnRN(tactile, 'tick');
          }
          if (autoPlay && !reduced) m.resumeAt.set(now + 2);
          if (reduced) m.busyUntil.set(now + 0.5);
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cy, reduced, autoPlay],
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (!covers || w === 0) return recorder.finishRecordingAsPicture();
    const p = pose.get();
    const reducedFade = p.fade >= 0;
    const deckAlpha = reducedFade ? 1 - p.fade : 1;
    const lifted = reducedFade ? 0 : Math.min(p.open, 1);
    const out = reducedFade ? 1 : easeInOut(Math.max(0, p.open - 1));
    const focusIndex = ((Math.round(p.scroll) % COUNT) + COUNT) % COUNT;

    // The focused record's colour, lighting the screen.
    const n = COUNT;
    const sc = ((p.scroll % n) + n) % n;
    const a = Math.floor(sc) % n;
    const b = (a + 1) % n;
    const f = sc - Math.floor(sc);
    const ca = hexA(ALBUMS[a].glow);
    const cb = hexA(ALBUMS[b].glow);
    const glow = [ca[0] + (cb[0] - ca[0]) * f, ca[1] + (cb[1] - ca[1]) * f, ca[2] + (cb[2] - ca[2]) * f];
    const g = (alpha: number) => Float32Array.of(glow[0], glow[1], glow[2], alpha);
    const boost = 1 + 0.5 * Math.min(p.open, 1) * (reducedFade ? p.fade : 1);
    const bg = Skia.Paint();
    bg.setShader(
      Skia.Shader.MakeRadialGradient({ x: w / 2, y: cy - h * 0.34 }, w * 0.95, [g(0.5 * boost), g(0.12 * boost), g(0)], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
    bg.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: h + 40 }, w * 0.9, [g(0.3 * boost), g(0)], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);

    const layer = Skia.Paint();
    const sleeve = (i: number, x: number, y: number, k: number, tilt: number, alpha: number, shadow: number) => {
      if (alpha <= 0.002) return;
      if (shadow > 0.001) {
        const half = (s * k) / 2;
        const path = Skia.Path.Make();
        const corners = [
          [-half, -half],
          [half, -half],
          [half, half],
          [-half, half],
        ];
        for (let j = 0; j < 4; j++) {
          const q = project(corners[j][0], corners[j][1], tilt);
          if (j === 0) path.moveTo(x + q[0], y + q[1] + 14);
          else path.lineTo(x + q[0], y + q[1] + 14);
        }
        path.close();
        const sp = Skia.Paint();
        sp.setColor(Float32Array.of(0, 0, 0, 0.55 * shadow));
        sp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 24, true));
        canvas.drawPath(path, sp);
      }
      canvas.save();
      canvas.concat(Skia.Matrix(sleeveMatrix(s, k, tilt, x, y)));
      layer.setAlphaf(alpha);
      canvas.saveLayer(layer);
      canvas.drawPicture(covers[i]);
      canvas.restore();
      canvas.restore();
    };

    // The deck, farthest first; the picked sleeve goes on top of the vinyl.
    const order: number[] = [];
    for (let i = 0; i < COUNT; i++) order.push(i);
    order.sort((x, y) => Math.abs(offsetOf(y, p.scroll)) - Math.abs(offsetOf(x, p.scroll)));
    let picked: number[] | null = null;
    for (const i of order) {
      const d = offsetOf(i, p.scroll);
      const isPicked = i === focusIndex && p.open > 0 && !reducedFade;
      let tilt = tiltOf(d);
      let y = yOf(d);
      let k = 1;
      let x = w / 2;
      let alpha = fadeOf(d) * topFade(cy + yOf(d), insets.top) * deckAlpha;
      if (isPicked) {
        tilt *= 1 - lifted;
        y *= 1 - lifted;
        k = 1 - (1 - FLAT_SCALE) * lifted;
        x += SLEEVE_SHIFT * s * FLAT_SCALE * out;
        picked = [i, x, cy + y, k, tilt, alpha, lifted];
        continue;
      } else if (!reducedFade) {
        const mo = Math.min(p.open, 1);
        y *= 1 + 0.3 * mo;
        alpha *= 1 - 0.85 * mo;
      }
      sleeve(i, x, cy + y, k, tilt, alpha, 0);
    }

    // The vinyl behind the lifted sleeve.
    if (p.open > 1 || reducedFade) {
      const fs = s * FLAT_SCALE;
      const la = reducedFade ? p.fade : 1;
      if (la > 0.001) {
        layer.setAlphaf(la);
        canvas.saveLayer(layer);
        drawVinyl(canvas, covers[focusIndex], s, w / 2 + VINYL_SHIFT * fs * out, cy, VINYL_RADIUS * fs, p.spin);
        canvas.restore();
      }
    }
    if (picked) sleeve(picked[0], picked[1], picked[2], picked[3], picked[4], picked[5], picked[6]);
    // Reduced motion: the open sleeve cross-fades in over the deck.
    if (reducedFade && p.fade > 0) sleeve(focusIndex, w / 2 + SLEEVE_SHIFT * s * FLAT_SCALE, cy, FLAT_SCALE, 0, p.fade, 0);

    // The focus ring, and the ticks of a hold.
    const ringAlpha = deckAlpha * (1 - smoothstep(0, 0.4, reducedFade ? 0 : p.open));
    if (ringAlpha > 0.01) {
      const r = 12 + (p.ring >= 1 ? 2 : 0);
      const ink = Skia.Paint();
      ink.setAntiAlias(true);
      ink.setStyle(1);
      ink.setStrokeWidth(1.5);
      ink.setColor(Float32Array.of(1, 1, 1, 0.9 * ringAlpha));
      canvas.drawCircle(w / 2, cy, r, ink);
      if (p.ring > 0) {
        ink.setStrokeWidth(3);
        ink.setStrokeCap(1);
        ink.setColor(Float32Array.of(1, 1, 1, ringAlpha));
        const arc = Skia.Path.Make();
        arc.addArc(Skia.XYWHRect(w / 2 - r, cy - r, 2 * r, 2 * r), -90, 360 * p.ring);
        canvas.drawPath(arc, ink);
        ink.setStrokeWidth(1.5);
        for (let k = 0; k < 16; k++) {
          const on = smoothstep(k / 16, (k + 1) / 16, p.ring);
          if (on <= 0) continue;
          const an = -Math.PI / 2 + (k / 16) * 2 * Math.PI;
          ink.setColor(Float32Array.of(1, 1, 1, 0.9 * on * ringAlpha));
          canvas.drawLine(
            w / 2 + Math.cos(an) * (r + 6),
            cy + Math.sin(an) * (r + 6),
            w / 2 + Math.cos(an) * (r + 6 + 6 * on),
            cy + Math.sin(an) * (r + 6 + 6 * on),
            ink,
          );
        }
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  // Each strip's title above it, as text views riding the strips.
  const stripAlpha = (pp: Pose) => {
    'worklet';
    const reducedFade = pp.fade >= 0;
    return (reducedFade ? 1 - pp.fade : 1) * (1 - smoothstep(0, 0.4, reducedFade ? 0 : pp.open));
  };

  const deckCaption = useAnimatedStyle(() => {
    const p = pose.get();
    const a = p.fade >= 0 ? 1 - p.fade : 1 - smoothstep(0.2, 0.8, p.open);
    return { opacity: a, transform: [{ translateY: 8 * (1 - a) }] };
  });
  const openCaption = useAnimatedStyle(() => {
    const p = pose.get();
    const a = p.fade >= 0 ? p.fade : smoothstep(1.2, 1.8, p.open);
    return { opacity: a, transform: [{ translateY: 8 * (1 - a) }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const album = ALBUMS[focus];
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {covers ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={`Record crate, ${album.title} by ${album.artist} in focus`}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
              {ALBUMS.map((al, i) => (
                <StripTitle key={al.title} index={i} pose={pose} s={s} w={w} cy={cy} top={insets.top} stripAlpha={stripAlpha} />
              ))}
            </View>
          </GestureDetector>

          {/* A fade under the caption. */}
          <LinearGradient
            pointerEvents="none"
            colors={['rgba(10,10,12,0)', 'rgba(10,10,12,0.92)', 'rgba(10,10,12,0.92)']}
            locations={[0, 0.55, 1]}
            style={[styles.fade, { top: captionTop - 70 }]}
          />

          <View pointerEvents="none" style={[styles.captions, { top: captionTop, width: w - 48 }]}>
            <Animated.View style={[styles.caption, deckCaption]}>
              <Text style={styles.big}>{'Every album,\npressed to vinyl.'}</Text>
              <Text style={styles.sub}>Hold a record to play it.</Text>
            </Animated.View>
            <Animated.View style={[styles.caption, openCaption]}>
              <Text style={styles.big} numberOfLines={2}>{`Now spinning\n${album.title}`}</Text>
              <Text style={styles.sub}>{`${album.artist} · Side A`}</Text>
            </Animated.View>
          </View>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, width: w - 48 }]}>
            <Text style={styles.crate}>Crate</Text>
            <Text style={styles.count}>10 RECORDS</Text>
          </View>
        </>
      ) : null}
    </View>
  );
}

function StripTitle({
  index,
  pose,
  s,
  w,
  cy,
  top,
  stripAlpha,
}: {
  index: number;
  pose: SharedValue<Pose>;
  s: number;
  w: number;
  cy: number;
  top: number;
  stripAlpha: (p: Pose) => number;
}) {
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const d = offsetOf(index, p.scroll);
    const a = stripAlpha(p) * fadeOf(d) * topFade(cy + yOf(d), top) * (1 - smoothstep(2.4, 3.4, Math.abs(d)));
    const edge = cy + yOf(d) + topEdge(s, tiltOf(d));
    // The text's box bottom sits 4 pt above the strip's top edge.
    return { opacity: a, transform: [{ translateY: edge - 4 - 14 }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.strip, { left: (w - s) / 2 + 2, width: s }, style]}>
      <Text style={styles.stripText} numberOfLines={1}>
        <Text style={styles.stripTitle}>{ALBUMS[index].title}</Text>
        {`  ${ALBUMS[index].artist}`}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0A0A0C' },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  captions: { position: 'absolute', left: 24 },
  caption: { position: 'absolute', left: 0, top: 0, right: 0, gap: 10 },
  big: { fontSize: 30, lineHeight: 36, fontWeight: '700', letterSpacing: -0.5, color: '#FFFFFF' },
  sub: { fontSize: 15, color: 'rgba(255,255,255,0.6)' },
  header: { position: 'absolute', left: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  crate: { fontSize: 17, fontWeight: '600', color: '#FFFFFF' },
  count: { fontSize: 11, fontWeight: '400', letterSpacing: 1, color: 'rgba(255,255,255,0.55)', fontFamily: MONO },
  strip: { position: 'absolute', top: 0, height: 14, justifyContent: 'flex-end' },
  stripText: { fontSize: 10, lineHeight: 14, color: 'rgba(255,255,255,0.6)' },
  stripTitle: { fontWeight: '600', color: '#FFFFFF' },
});
