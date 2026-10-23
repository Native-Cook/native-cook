import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { COLORS, COUNT, type Drive, LOOP, dot, easeOut, lead, level, quarterOf } from './motion';

/*
 Particle Listening Ring — a song search that listens: a ring of coloured dots breathes with the
 voice it hears. A port of the SwiftUI block (ParticleListeningRing.swift); the numbers are the same.

 Look: a warm off-white screen (#FBFAF7) with a round back button top left, the caption "Hum,
 sing, or play the tune" (17 pt, #5E6168) centred at 15.5 % of the height, and in the middle
 (centre at 46 % of the height, radius 32 % of the width) a ring of 96 dots plus 16 that fall into
 the middle when it is loud. Four colours: coral #FF6B57, violet #6C4CF5, teal #19B39A, sun #FFB020.
 At the bottom a white mic button (60 pt, soft shadow) with "Hold anywhere to hum along" under it.

 Each dot (seeded, the same numbers in every stack): an angle around the ring with a little
 jitter, a colour, a rest radius 1–2.4 pt and a loud radius 2.6–7.2 pt. With the level a:
   radius on the ring  R·(1 + 0.03j)·(1 + 0.12a) + R·a·(0.16·n(θ, t) + 0.12·j2)
                       (n: sines of 3θ and 5θ drifting 2 and 3 times a loop)
   angle               θ + 0.18·sin(2πt/6) + 0.05·sin(2πkt/6 + φ)
   size                rest·(1 − 0.35a)·twinkle + growth(colour, t)·loud·pulse
 The voice has three swells (1.1 s violet, 2.45 s teal and sun, 3.85 s coral): the leading colour
 grows to full size, the others to a quarter, and a soft wash of the leading colours (16 %, radius
 1.6 R, from a point 0.35 R out in that colour's quarter) glows behind the ring. In the quiet dips
 half of the ring empties (dots fade to 10 % and shrink to 28 %), the empty half turning once a
 loop. The 16 inner dots fall up to 75 % of the way to the centre when the level passes 0.2–0.8.

 autoPlay timeline (LOOP = 6 s): 0–0.5 s a still, thin ring of specks; 0.5–5.4 s the three swells
 with dips between them; 5.4–6 s the thin ring again. Every motion runs a whole number of times per
 loop, so 6.0 is the frame at 0.0.

 Touch: hold anywhere and you are the voice: the level rises to 1 in 0.3 s (falls over 0.6 s after
 release), the colour of the quarter under the finger leads, and the dots near the finger's angle
 are pushed out by up to 22 % of the radius; dragging around moves the push (a light tick each time
 the leading colour changes). The script fades out in 0.25 s at the touch and comes back at the
 first loop start at least 2 s after the release (the ring is at rest there).
 Reduced motion: the ring stands still at the middle swell; touches do not move it.
 */

const BACKGROUND = '#FBFAF7';
const CAPTION = '#5E6168';
const INK = Float32Array.of(0.165, 0.169, 0.188, 1);

function tap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

export function ParticleListeningRing({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });

  const w = size.width;
  const h = size.height;
  const cx = w / 2;
  const cy = h * 0.46;
  const radius = w * 0.32;
  const micY = h - Math.max(insets.bottom, 16) - 78;

  const now = useSharedValue(-1);
  const appear = useSharedValue(-1);
  /** The script plays from this moment on; before it, it is faded out since scriptCut. */
  const scriptFrom = useSharedValue(autoPlay ? -1e12 : 1e12);
  const scriptCut = useSharedValue(-1e12);
  /** A finger: [x, y, when it came down], or []. */
  const press = useSharedValue<number[]>([]);
  /** The release: [angle, when, the level reached], or []. */
  const released = useSharedValue<number[]>([]);
  const lastQuarter = useSharedValue(-1);

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    if (appear.get() < 0) appear.set(t);
    now.set(t);
  });

  const drive = useDerivedValue((): Drive => {
    if (reduced) return { t: 2.45, gain: 1, touch: 0, touchAngle: 0 };
    const at = now.get();
    const e = at - appear.get();
    const t = e - Math.floor(e / LOOP) * LOOP;
    const gain = at >= scriptFrom.get() ? 1 : 1 - easeOut((at - scriptCut.get()) / 0.25);
    let touch = 0;
    let angle = 0;
    const p = press.get();
    const r = released.get();
    if (p.length === 3) {
      touch = easeOut((at - p[2]) / 0.3);
      angle = Math.atan2(p[1] - cy, p[0] - cx);
    } else if (r.length === 3) {
      touch = r[2] * (1 - easeOut((at - r[1]) / 0.6));
      angle = r[0];
    }
    // A finger's voice flutters too.
    touch *= 1 + 0.15 * Math.sin(2 * Math.PI * 3.1 * e) + 0.08 * Math.sin(2 * Math.PI * 5.3 * e + 1);
    return { t, gain, touch, touchAngle: angle };
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          if (reduced) return;
          const at = now.get();
          // The script fades out from what it shows now.
          if (at >= scriptFrom.get()) scriptCut.set(at);
          scriptFrom.set(1e12);
          press.set([e.x, e.y, at]);
          released.set([]);
          lastQuarter.set(quarterOf(Math.atan2(e.y - cy, e.x - cx)));
          scheduleOnRN(tap);
        })
        .onUpdate((e) => {
          if (reduced) return;
          const p = press.get();
          if (p.length === 3) press.set([e.x, e.y, p[2]]);
          const q = quarterOf(Math.atan2(e.y - cy, e.x - cx));
          if (q !== lastQuarter.get()) {
            lastQuarter.set(q);
            scheduleOnRN(tick);
          }
        })
        .onFinalize((e) => {
          if (reduced) return;
          const p = press.get();
          if (p.length !== 3) return;
          const at = now.get();
          released.set([Math.atan2(e.y - cy, e.x - cx), at, easeOut((at - p[2]) / 0.3)]);
          press.set([]);
          if (autoPlay) {
            // The script comes back at the first loop start at least 2 s from now.
            const start = appear.get();
            scriptFrom.set(start + Math.ceil((at - start + 2) / LOOP) * LOOP);
          }
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay, cx, cy],
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const d = drive.get();
    const full = Skia.XYWHRect(0, 0, w, h);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(BACKGROUND));
    canvas.drawRect(full, paint);
    if (w <= 0) return recorder.finishRecordingAsPicture();
    // A soft wash of the leading colours behind the ring, as strong as the voice.
    for (let k = 0; k < 4; k++) {
      const wt = Math.min(1, lead(d, k));
      if (wt < 0.01) continue;
      const a = (k * Math.PI) / 2 + Math.PI / 4;
      const col = COLORS[k];
      const wash = Skia.Paint();
      wash.setShader(
        Skia.Shader.MakeRadialGradient(
          { x: cx + 0.35 * radius * Math.cos(a), y: cy + 0.35 * radius * Math.sin(a) },
          radius * 1.6,
          [Float32Array.of(col[0], col[1], col[2], 0.16 * wt), Float32Array.of(col[0], col[1], col[2], 0)],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawRect(full, wash);
    }
    for (let i = 0; i < COUNT; i++) {
      const p = dot(i, d, cx, cy, radius);
      if (p[2] < 0.15 || p[3] < 0.02) continue;
      const col = COLORS[p[4]];
      paint.setColor(Float32Array.of(col[0], col[1], col[2], p[3]));
      canvas.drawCircle(p[0], p[1], p[2], paint);
    }

    // Back button.
    paint.setColor(Float32Array.of(0.937, 0.929, 0.918, 1));
    canvas.drawCircle(38, insets.top + 26, 18, paint);
    const stroke = Skia.Paint();
    stroke.setAntiAlias(true);
    stroke.setStyle(PaintStyle.Stroke);
    stroke.setStrokeCap(StrokeCap.Round);
    stroke.setStrokeJoin(StrokeJoin.Round);
    stroke.setStrokeWidth(2);
    stroke.setColor(INK);
    const bx = 34;
    const by = insets.top + 19;
    const chevron = Skia.Path.Make();
    chevron.moveTo(bx + 7, by + 1);
    chevron.lineTo(bx + 1, by + 7);
    chevron.lineTo(bx + 7, by + 13);
    canvas.drawPath(chevron, stroke);

    // The mic button, with a soft ring that swells with the level.
    const lv = Math.min(1, d.gain * level(d.t) + d.touch);
    const ring = stroke.copy();
    ring.setColor(Float32Array.of(1.0, 0.42, 0.341, 0.28 * lv));
    canvas.drawCircle(cx, micY, 30 + 14 * lv, ring);
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    // SwiftUI's shadow radius 12 is about a Gaussian sigma of 6.
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    shadow.setColor(Float32Array.of(0, 0, 0, 0.08));
    canvas.drawCircle(cx, micY + 4, 30, shadow);
    paint.setColor(Float32Array.of(1, 1, 1, 1));
    canvas.drawCircle(cx, micY, 30, paint);
    // A microphone in an 18 × 26 box: capsule, cradle and stand.
    const ox = cx - 9;
    const oy = micY - 13;
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox + 5, oy, 8, 15), 4, 4), stroke);
    const mic = Skia.Path.Make();
    mic.moveTo(ox + 1, oy + 11);
    mic.cubicTo(ox + 1, oy + 22, ox + 17, oy + 22, ox + 17, oy + 11);
    mic.moveTo(ox + 9, oy + 19.5);
    mic.lineTo(ox + 9, oy + 25);
    canvas.drawPath(mic, stroke);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {w > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Listening">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <Text style={[styles.caption, { top: h * 0.155 - 11 }]} numberOfLines={1} pointerEvents="none">
            Hum, sing, or play the tune
          </Text>
          <Text style={[styles.hint, { top: micY + 44 }]} numberOfLines={1} pointerEvents="none">
            Hold anywhere to hum along
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  caption: { position: 'absolute', left: 0, right: 0, height: 22, lineHeight: 22, fontSize: 17, color: CAPTION, textAlign: 'center' },
  hint: { position: 'absolute', left: 0, right: 0, fontSize: 13, color: 'rgba(94,97,104,0.8)', textAlign: 'center' },
});
