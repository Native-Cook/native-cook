import { Canvas, Picture, Skia, TileMode, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { BACKGROUND, drawFace, drawStreaks, framePath, hexA, makeGrain, monoText, white } from './art';
import { LOOP, clamp01, faceMatrix, glitch, loopAngle, loopCaption, printedSide, spring } from './motion';

/*
 Iridescent CD Flip — a compact disc in a sci-fi HUD that turns over to show its rainbow data side.
 A port of the SwiftUI block (IridescentCdFlip.swift); the numbers are the same.

 Look: black with fixed film grain. A white chamfered frame (corners cut at the top right and
 bottom left) holds the disc, 0.78 × the frame across: the printed side is glossy black with
 warm light bands across its lower third, a fan of cold streaks at the right, a gold mono
 "MIRROR INDEX" and red and green hub circles; the data side is mirror grey with two hue cycles
 of diffraction colour that sweep round as it turns. Both have a clear hub, a hole and a rim, and
 a soft band of light that slides across with the turn. Mono captions sit above and below the
 frame and retype themselves (a glitch) at every flip. Under the frame: the jewel case with the
 same art, the track list, a level meter, a laser read-out and a "FLIP DISC" pill.

 The disc turns about its vertical axis, seen from 1000 pt away (the same projection as Core
 Animation's m34 = −1/1000 in the Swift version, applied here as a 3×3 matrix on the canvas);
 near edge-on a chrome edge shows.

 autoPlay timeline (LOOP = 8 s, motion.ts):
   0.0 – 1.2    printed side, swaying ±4° (one sine a loop) so the light slides.
   1.2          flip: 180° on a spring (ω 6.5, ζ 0.6: about 9 % overshoot and a wobble), captions retype.
   1.2 – 4.2    the data side, its rainbow sweeping with the sway.
   4.2          flip on round to 360°: the printed side again, captions retype.
   8.0          360° is 0°: the same frame as 0.0.

 Touch: drag sideways on the disc to turn it with the finger (half a turn per disc width); on
 release it springs to the nearest side, thrown by its speed (a medium haptic when it lands on
 the other side). Tap the disc or the pill to flip it. 2 s after the last touch it turns back to
 the printed side and the loop starts again.
 Reduced motion: no loop and no sway; a flip is a 0.3 s cross-fade between the sides, the disc
 never turns; dragging does nothing.
 */

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const TRACKS = [
  ['01', 'Halation', '3:41'],
  ['02', 'Glass Sea', '4:02'],
  ['03', 'Afterimage', '2:58'],
  ['04', 'Beam of Light', '5:10'],
];
const CYCLES = [3, 5, 4, 7, 6, 3, 5, 4];

type Pose = { angle: number; back: number; printed: boolean; since: number; clock: number };

type Motion = {
  now: SharedValue<number>;
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /** Whole turns added to the loop's angle after a hand-off. */
  angleOffset: SharedValue<number>;
  /** The turn under the spring: [since, from, velocity, to]. */
  turn: SharedValue<number[]>;
  /** The disc under a finger, or NaN. */
  dragAngle: SharedValue<number>;
  dragFrom: SharedValue<number>;
  touching: SharedValue<number>;
  moved: SharedValue<number>;
  busyUntil: SharedValue<number>;
  resumeAt: SharedValue<number>;
  pressed: SharedValue<number>;
};

function tactile(kind: 'light' | 'medium') {
  Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** back is −1 except under reduced motion, where it is how much of the data side shows. */
function poseAt(m: Motion, at: number, reduced: boolean): Pose {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    const t = (at - start) % LOOP;
    const cap = loopCaption(t);
    return { angle: loopAngle(t) + m.angleOffset.get(), back: -1, printed: cap[0], since: cap[1], clock: t };
  }
  const tr = m.turn.get();
  const e = Math.max(0, at - tr[0]);
  if (reduced) {
    const showBack = !printedSide(tr[3]);
    const f = clamp01(e / 0.3);
    return { angle: 0, back: showBack ? f : 1 - f, printed: !showBack, since: e, clock: 0 };
  }
  const drag = m.dragAngle.get();
  const dragging = !Number.isNaN(drag);
  const angle = dragging ? drag : spring(tr[1], tr[2], tr[3], e)[0];
  return { angle, back: -1, printed: printedSide(tr[3]), since: dragging ? 10 : e, clock: at % (LOOP * 100) };
}

function velocityAt(m: Motion, at: number) {
  'worklet';
  const tr = m.turn.get();
  return spring(tr[1], tr[2], tr[3], Math.max(0, at - tr[0]))[1];
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
  const a = loopAngle(t) + m.angleOffset.get();
  m.turn.set([now, a, 0, Math.round(a / 180) * 180]);
}

export function IridescentCdFlip({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [printed, setPrinted] = useState(true);
  const w = size.width;
  const h = size.height;
  const s = w - 48;
  const y0 = insets.top + 40;
  const r = 0.39 * s;
  const cx = w / 2;
  const cy = y0 + s / 2;
  const caseTop = y0 + s + 44;
  const pillTop = h - Math.max(insets.bottom, 16) - 60;

  const fonts = useMemo(
    () =>
      r > 0
        ? {
            hud: matchFont({ fontFamily: MONO, fontSize: 10, fontWeight: '500' }),
            small: matchFont({ fontFamily: MONO, fontSize: 9, fontWeight: '500' }),
            label: matchFont({ fontFamily: MONO, fontSize: Math.max(7, r * 0.075), fontWeight: '600' }),
            tiny: matchFont({ fontFamily: MONO, fontSize: 6, fontWeight: '500' }),
            pill: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '600' }),
          }
        : null,
    [r],
  );
  const grain = useMemo(() => (w > 0 ? makeGrain(w, h) : null), [w, h]);

  const m: Motion = {
    now: useSharedValue(-1),
    clock: useSharedValue(0),
    autoStart: useSharedValue(-1),
    angleOffset: useSharedValue(0),
    turn: useSharedValue<number[]>([-1e9, 0, 0, 0]),
    dragAngle: useSharedValue(NaN),
    dragFrom: useSharedValue(0),
    touching: useSharedValue(0),
    moved: useSharedValue(0),
    busyUntil: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
    pressed: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.now.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.now.set(now);
    if (!reduced || now < m.busyUntil.get() || m.touching.get() === 1) m.clock.set(now);
    // 2 s after the last touch: back to the printed side, then the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt && m.touching.get() === 0) {
      m.resumeAt.set(-1);
      const tr = m.turn.get();
      let target = tr[3];
      if (!printedSide(target)) target += 180;
      if (target !== tr[3]) m.turn.set([now, poseAt(m, now, reduced).angle, velocityAt(m, now), target]);
      // Start once the spring has settled (within a hundredth of a degree after 2.5 s).
      m.autoStart.set(Math.max(now, m.turn.get()[0] + 2.5));
      m.angleOffset.set(target);
    }
  });

  const pose = useDerivedValue(() => poseAt(m, m.clock.get(), reduced));

  useAnimatedReaction(
    () => pose.get().printed,
    (p, previous) => {
      if (p !== previous) scheduleOnRN(setPrinted, p);
    },
  );

  const flip = () => {
    'worklet';
    const now = m.now.get();
    takeOver(m, now);
    const p = poseAt(m, now, reduced);
    m.turn.set([now, p.angle, reduced ? 0 : velocityAt(m, now), m.turn.get()[3] + 180]);
    scheduleOnRN(tactile, 'medium');
    if (reduced) m.busyUntil.set(now + 0.4);
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin(() => {
          const now = m.now.get();
          m.touching.set(1);
          m.moved.set(0);
          takeOver(m, now);
          m.dragFrom.set(poseAt(m, now, reduced).angle);
        })
        .onUpdate((e) => {
          if (reduced) return;
          if (m.moved.get() === 0 && Math.abs(e.translationX) > 6) {
            m.moved.set(1);
            scheduleOnRN(tactile, 'light');
          }
          if (m.moved.get() === 1) m.dragAngle.set(m.dragFrom.get() + (e.translationX / (2 * r)) * 180);
        })
        .onFinalize((e) => {
          m.touching.set(0);
          const a = m.dragAngle.get();
          if (m.moved.get() === 1 && !Number.isNaN(a)) {
            const now = m.now.get();
            const v = (e.velocityX / (2 * r)) * 180;
            const target = Math.round((a + v * 0.15) / 180) * 180;
            if (printedSide(target) !== printedSide(m.dragFrom.get())) scheduleOnRN(tactile, 'medium');
            m.turn.set([now, a, v, target]);
            m.dragAngle.set(NaN);
            if (autoPlay && !reduced) m.resumeAt.set(now + 2);
          } else {
            flip();
          }
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [r, reduced, autoPlay],
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (!fonts || !grain) return recorder.finishRecordingAsPicture();
    const p = pose.get();
    canvas.drawPicture(grain);
    const angle = p.angle;
    const cosA = Math.cos((angle * Math.PI) / 180);
    const reducedBack = p.back >= 0;
    const backAmount = reducedBack ? p.back : cosA < 0 ? 1 : 0;

    // The light the disc throws.
    const glow = backAmount > 0.5 ? 0x8a7cff : 0xff6a2b;
    const strength = reducedBack ? 0.12 + 0.1 * p.back : cosA < 0 ? 0.22 * -cosA : 0.12 * cosA;
    const paint = Skia.Paint();
    paint.setShader(
      Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r * 1.7, [hexA(glow, strength), hexA(glow, strength), hexA(glow, 0)], [0, 0.235, 1], TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
    // The HUD frame and its captions.
    const line = Skia.Paint();
    line.setAntiAlias(true);
    line.setStyle(1);
    line.setStrokeWidth(1);
    line.setColor(white(0.85));
    canvas.drawPath(framePath(24, y0, s, 0.18 * s), line);
    const headline = p.printed ? 'COMPACT DISC (CD) — PRINTED SIDE' : 'DATA SIDE — READ BY LASER 780 NM';
    const footline = p.printed ? 'MIRROR INDEX — HALATION' : 'BE READ WITH A BEAM OF LIGHT';
    monoText(canvas, glitch(headline, p.since, p.printed ? 1 : 2), 24, y0 - 8, fonts.hud, white(0.85), 1.5, false, 2);
    monoText(canvas, glitch(footline, p.since - 0.1, p.printed ? 3 : 4), 24 + s, y0 + s + 8, fonts.hud, white(0.85), 1.5, true, 1);
    // Edge-on, the disc's chrome edge.
    if (!reducedBack && Math.abs(cosA) < 0.4) {
      const ew = 1.5 + 3 * Math.abs(Math.sin((angle * Math.PI) / 180));
      const edge = Skia.Paint();
      edge.setAntiAlias(true);
      edge.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: cx, y: cy - r },
          { x: cx, y: cy + r },
          [hexA(0xff9ad5), white(1), hexA(0x9b9ea6), hexA(0x5bc8ff), white(1)],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - ew / 2, cy - r, ew, 2 * r), ew / 2, ew / 2), edge);
    }
    // The disc.
    if (reducedBack) {
      // Reduced motion: the two sides cross-fade, flat.
      const layer = Skia.Paint();
      for (let side = 0; side < 2; side++) {
        const a = side === 0 ? 1 - p.back : p.back;
        if (a <= 0.001) continue;
        layer.setAlphaf(a);
        canvas.saveLayer(layer);
        canvas.translate(cx - r, cy - r);
        drawFace(canvas, r, 0, side === 1, fonts.label);
        canvas.restore();
      }
    } else {
      canvas.save();
      canvas.concat(Skia.Matrix(faceMatrix(r, angle, cx, cy)));
      drawFace(canvas, r, angle, cosA < 0, fonts.label);
      canvas.restore();
    }

    // The jewel case: the same art, a spine at the left.
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setColor(hexA(0x0b0b0e));
    canvas.drawRect(Skia.XYWHRect(24, caseTop, 150, 134), fill);
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(24 + 14, caseTop, 136, 134), 1, true);
    drawStreaks(canvas, 24 + 14, caseTop - 136 * 0.08, 136);
    monoText(canvas, 'MIRROR INDEX · HALATION', 24 + 22, caseTop + 134 * 0.6, fonts.tiny, white(0.6), 1, false, 0);
    canvas.restore();
    canvas.drawRect(Skia.XYWHRect(24.5, caseTop + 0.5, 149, 133), line);
    fill.setColor(white(0.85));
    canvas.drawRect(Skia.XYWHRect(24 + 10, caseTop, 1, 134), fill);
    fill.setColor(white(0.5));
    canvas.drawRect(Skia.XYWHRect(24 + 13, caseTop, 1, 134), fill);

    // A HUD read-out: the laser's position sweeping over the disc's radius, once there and back a loop.
    const top = caseTop + 134 + 40;
    const span = w - 48;
    const u = reduced ? 0.3 : 0.5 - 0.42 * Math.cos((2 * Math.PI * p.clock) / LOOP);
    for (let k = 0; k <= 40; k++) {
      const x = 24 + (span * k) / 40;
      const tall = k % 5 === 0 ? 8 : 4;
      fill.setColor(white(k % 5 === 0 ? 0.6 : 0.3));
      canvas.drawRect(Skia.XYWHRect(x - 0.5, top + 18 - tall, 1, tall), fill);
    }
    fill.setColor(white(0.35));
    canvas.drawRect(Skia.XYWHRect(24, top + 18, span, 1), fill);
    fill.setColor(hexA(0xff4b3a));
    canvas.drawRect(Skia.XYWHRect(24 + span * u - 1, top + 4, 2, 20), fill);
    canvas.drawCircle(24 + span * u, top + 18.5, 3, fill);
    monoText(canvas, 'LASER 780 NM', 24, top + 32, fonts.small, white(0.55), 1, false, 1);
    monoText(canvas, 'HUB  —  RIM', 24 + span, top + 32, fonts.small, white(0.55), 1, true, 1);

    // The flip pill: a circular arrow and its label.
    const pressed = m.pressed.get();
    const sc = pressed ? 0.96 : 1;
    canvas.save();
    canvas.translate(cx, pillTop + 22);
    canvas.scale(sc, sc);
    canvas.translate(-cx, -(pillTop + 22));
    const pill = Skia.RRectXY(Skia.XYWHRect(cx - 84, pillTop, 168, 44), 22, 22);
    if (pressed) {
      fill.setColor(white(0.14));
      canvas.drawRRect(pill, fill);
    }
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 83.5, pillTop + 0.5, 167, 43), 21.5, 21.5), line);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setStyle(1);
    ink.setStrokeWidth(1.6);
    ink.setStrokeCap(1);
    ink.setColor(white(1));
    let labelWidth = 0;
    for (const gw of fonts.pill.getGlyphWidths(fonts.pill.getGlyphIDs('FLIP DISC'))) labelWidth += gw + 2;
    const ix = cx - (14 + 10 + labelWidth) / 2 + 7;
    const iy = pillTop + 22;
    const rr = 5;
    const arcs = Skia.Path.Make();
    arcs.addArc(Skia.XYWHRect(ix - rr, iy - rr, 2 * rr, 2 * rr), 200, 140);
    arcs.addArc(Skia.XYWHRect(ix - rr, iy - rr, 2 * rr, 2 * rr), 20, 140);
    for (const deg of [340, 160]) {
      const ar = (deg * Math.PI) / 180;
      const tx = ix + rr * Math.cos(ar);
      const ty = iy + rr * Math.sin(ar);
      arcs.moveTo(tx, ty);
      arcs.lineTo(tx + 3.2 * Math.cos(ar + 2.2), ty + 3.2 * Math.sin(ar + 2.2));
      arcs.moveTo(tx, ty);
      arcs.lineTo(tx + 3.2 * Math.cos(ar + 0.6 + Math.PI), ty + 3.2 * Math.sin(ar + 0.6 + Math.PI));
    }
    canvas.drawPath(arcs, ink);
    monoText(canvas, 'FLIP DISC', ix + 7 + 10, iy + 1, fonts.pill, white(1), 2, false, 0);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const setPressed = (on: number) => {
    scheduleOnUI(() => {
      'worklet';
      m.pressed.set(on);
      m.clock.set(m.now.get());
    });
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {fonts && grain ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>

          {/* Touches on the disc's square: drag to turn, tap to flip. */}
          <GestureDetector gesture={gesture}>
            <View
              style={[styles.hit, { top: y0, width: s, height: s }]}
              accessible
              accessibilityRole="button"
              accessibilityLabel={printed ? 'Compact disc, printed side' : 'Compact disc, data side'}
            />
          </GestureDetector>

          {/* The track list and the level meter, beside the case drawn in the canvas. */}
          <View pointerEvents="none" style={[styles.tracks, { top: caseTop, left: 24 + 150 + 20, width: w - 48 - 150 - 20 }]}>
            {TRACKS.map((tr, i) => (
              <View key={tr[0]} style={styles.row}>
                <Text style={[styles.mono, styles.dim]}>{tr[0]}</Text>
                <Text style={[styles.mono, styles.name, { color: i === 0 ? '#FFFFFF' : 'rgba(255,255,255,0.6)' }]} numberOfLines={1}>
                  {tr[1]}
                </Text>
                <Text style={[styles.mono, styles.dim]}>{tr[2]}</Text>
              </View>
            ))}
            <View style={styles.meterRow}>
              <Text style={styles.playing}>NOW PLAYING · 01</Text>
              <View style={styles.meter}>
                {CYCLES.map((cycles, b) => (
                  <Bar key={b} index={b} cycles={cycles} pose={pose} reduced={reduced} />
                ))}
              </View>
            </View>
          </View>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Flip disc"
            onPressIn={() => setPressed(1)}
            onPressOut={() => setPressed(0)}
            onPress={() => scheduleOnUI(flip)}
            style={[styles.pill, { top: pillTop, left: cx - 84 }]}
          />
        </>
      ) : null}
    </View>
  );
}

/** A level-meter bar: it moves a whole number of times a loop. */
function Bar({ index, cycles, pose, reduced }: { index: number; cycles: number; pose: SharedValue<Pose>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const level = reduced ? 0.4 : 0.5 + 0.5 * Math.sin((2 * Math.PI * cycles * pose.get().clock) / LOOP + index * 1.7);
    return { height: 3 + 11 * level };
  });
  return <Animated.View style={[styles.bar, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  hit: { position: 'absolute', left: 24 },
  tracks: { position: 'absolute' },
  row: {
    height: 26,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.12)',
  },
  mono: { fontFamily: MONO, fontSize: 11, fontWeight: '500' },
  dim: { color: 'rgba(255,255,255,0.45)' },
  name: { flex: 1 },
  meterRow: { height: 30, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  playing: { fontFamily: MONO, fontSize: 9, fontWeight: '500', letterSpacing: 1, color: 'rgba(255,255,255,0.55)' },
  meter: { flexDirection: 'row', alignItems: 'center', gap: 3, height: 14 },
  bar: { width: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.8)' },
  pill: { position: 'absolute', width: 168, height: 44, borderRadius: 22 },
});
