import { BlurStyle, Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
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
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP, SPEAK_LENGTH, VOICES, auto, blend, breathing, clamp, easeInOut, easeOut, smoothstep, speak, squash } from './model';
import { ORB_EFFECT } from './orb-shader';

/*
 Watercolor Voice Orbs — a voice picker whose orb is a living watercolour that repaints itself for
 each voice. A port of the SwiftUI block (WatercolorVoiceOrbs.swift); the numbers are the same.

 Look: a black screen. "Choose a voice" (24 pt semibold, white) centred at 17 % of the height; the
 orb (radius 25 % of the width) centred at 43 %, with a soft glow of its mid colour behind it
 (radius 1.25 R, blur 34, 35 %). Under it the voice's name (18 pt semibold), its line (14 pt, white
 50 %) and a pager of four 6 pt dots (14 pt apart, the current one white, stretching between dots).
 A white "Continue" pill (52 pt tall, 24 pt from the sides) at the bottom.
 The orb is a Skia RuntimeEffect (orb-shader.ts, the same code as WatercolorVoiceOrbs.metal):
 domain-warped value noise (two fbm warps of five octaves) painted deep → mid → light, with a bright
 side facing each voice's direction, a darker rim and a soft light upper left. The noise drifts on
 circles of the loop's phase, so the texture flows and comes back to itself every loop. Voices:
 Lagoon (blue, bright at the top), Dune (terracotta, bright lower right), Moss (green, bright at the
 left), Dusk (violet, bright below); each also sits elsewhere in the noise and twists its middle by
 its own amount, and between two voices every parameter is blended, so a swipe makes the clouds flow
 and turn into the next ones while the next palette seeps in through them.

 autoPlay timeline (LOOP = 10 s); v is the voice position, 0…3:
   0.0 – 1.4   Lagoon at rest; the orb breathes 1 → 1.02 (period 2.5 s, four per loop).
   1.4, 3.6, 5.8   a swipe to the next voice: v eases over 0.85 s (cubic in-out), the orb squashes
               1 − 0.11·e^(−2.4u)·sin(7.5u) (to ~0.94, then ~1.02), the names slide 24 pt per
               voice and cross-fade, the pager dot travels and stretches.
   +0.85       the voice lands and speaks a 1.1 s preview: the orb swells up to 3 % and its glow
               brightens with four syllables.
   7.8 – 8.8   a glide back through Moss and Dune to Lagoon (1.0 s), which speaks again.
   9.9 – 10.0  rest; the frame at 10.0 is the one at 0.0.

 Touch: drag sideways to move between voices continuously (a voice per 55 % of the width, a
 rubber band past the ends, a light tick at each voice); release settles on the nearest voice (or
 the next one in the direction of a quick flick) in 0.45 s, squashes and speaks. A tap makes the orb
 give and speak. 2 s after the last touch the picker glides back to Lagoon (0.9 s) and the loop
 starts again from its rest.
 Reduced motion: no flow, breathing, squash or speech; the picker shows Lagoon, and a drag or a tap
 changes voice with a 0.25 s cross-fade.
 */

const TOP = VOICES.length - 1;

function tactile(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

type Shown = { v: number; scale: number; glow: number; tau: number };

export function WatercolorVoiceOrbs({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const w = size.width;
  const h = size.height;
  const radius = 0.25 * w;
  const cy = h * 0.43;
  const nameY = cy + radius + 34;
  const pagerY = nameY + 62;

  const now = useSharedValue(-1);
  const appear = useSharedValue(-1);
  /** autoPlay drives everything while it is on (1), from this time on (rejoinAt). */
  const autoOn = useSharedValue(0);
  const rejoinAt = useSharedValue(-1);
  /** Hand-made state: v glides [from, to, at, duration]; the last squash and the last landing. */
  const glide = useSharedValue([0, 0, -1e9, 0]);
  const squashAt = useSharedValue(-1e9);
  const landedAt = useSharedValue(-1e9);
  /** A drag: [the v it started from, the v now], or []. */
  const drag = useSharedValue<number[]>([]);
  const lastIndex = useSharedValue(0);
  const resumeAt = useSharedValue(-1);

  const manualV = (at: number) => {
    'worklet';
    const d = drag.get();
    if (d.length === 2) return d[1];
    const g = glide.get();
    const u = g[3] > 0 ? (at - g[2]) / g[3] : 1;
    return g[0] + (g[1] - g[0]) * (g[3] > 0.6 ? easeInOut(u) : easeOut(u));
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    if (appear.get() < 0) {
      appear.set(t);
      autoOn.set(autoPlay && !reduced ? 1 : 0);
    }
    now.set(t);
    // 2 s after the last touch: glide back to Lagoon (0.9 s), let it speak, then rejoin the loop
    // inside its rest (loop time 9.9 … 1.4).
    const r = resumeAt.get();
    if (r >= 0 && t >= r) {
      resumeAt.set(-1);
      const v = manualV(t);
      const moving = Math.abs(v) > 0.001;
      if (moving) {
        glide.set([v, 0, t, 0.9]);
        squashAt.set(t);
        landedAt.set(t + 0.9);
      }
      const ready = t + (moving ? 0.9 + SPEAK_LENGTH : 0);
      const er = (ready - appear.get()) % LOOP;
      rejoinAt.set(ready + (er >= 9.9 || er < 1.4 ? 0 : 9.9 - er));
    }
    const j = rejoinAt.get();
    if (j >= 0 && t >= j) {
      rejoinAt.set(-1);
      autoOn.set(1);
    }
  });

  const shown = useDerivedValue((): Shown => {
    const at = now.get();
    const clock = at - appear.get();
    const e = clock - Math.floor(clock / LOOP) * LOOP;
    const tau = (2 * Math.PI * e) / LOOP;
    if (reduced) {
      const g = glide.get();
      return { v: g[0] + (g[1] - g[0]) * clamp((at - g[2]) / 0.25), scale: 1, glow: 0.35, tau: 0 };
    }
    const breath = breathing(clock);
    if (autoOn.get() === 1) {
      const a = auto(e);
      const s = speak(a.sinceLanding);
      return { v: a.v, scale: breath * squash(a.sinceSwipe) + 0.03 * s, glow: 0.35 + 0.25 * s, tau };
    }
    const s = speak(at - landedAt.get());
    return { v: manualV(at), scale: breath * squash(at - squashAt.get()) + 0.03 * s, glow: 0.35 + 0.25 * s, tau };
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin(() => {
          const at = now.get();
          resumeAt.set(-1);
          rejoinAt.set(-1);
          // Freeze what autoPlay shows into the hand-made state.
          if (autoOn.get() === 1) {
            const clock = at - appear.get();
            const a = auto(clock - Math.floor(clock / LOOP) * LOOP);
            glide.set([a.v, a.v, at, 0]);
            squashAt.set(at - a.sinceSwipe);
            landedAt.set(at - a.sinceLanding);
            autoOn.set(0);
          }
          const v = manualV(at);
          drag.set([v, v]);
          lastIndex.set(Math.round(v));
        })
        .onUpdate((e) => {
          const d = drag.get();
          if (d.length !== 2 || Math.abs(e.translationX) <= 4) return;
          let v = d[0] - e.translationX / (0.55 * w);
          if (v < 0) v *= 0.3;
          else if (v > TOP) v = TOP + (v - TOP) * 0.3;
          drag.set([d[0], v]);
          const i = Math.round(clamp(v, 0, TOP));
          if (i !== lastIndex.get()) {
            lastIndex.set(i);
            scheduleOnRN(tick);
          }
        })
        .onFinalize((e) => {
          const d = drag.get();
          if (d.length !== 2) return;
          const at = now.get();
          drag.set([]);
          const current = d[1];
          const moved = Math.abs(e.translationX) > 4;
          let target = Math.round(current);
          if (moved) {
            // A flick: the finger's speed carried for 0.2 s goes past 60 pt.
            const flick = e.velocityX * 0.2;
            if (Math.abs(flick) > 60) target = flick < 0 ? Math.floor(current) + 1 : Math.ceil(current) - 1;
          }
          target = clamp(target, 0, TOP);
          glide.set([current, target, at, reduced ? 0.25 : 0.45]);
          if (!reduced) {
            squashAt.set(at);
            landedAt.set(at + (moved ? 0.45 : 0.1));
          }
          scheduleOnRN(tactile, moved);
          if (autoPlay && !reduced) resumeAt.set(at + 2);
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay, w],
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (w <= 0) return recorder.finishRecordingAsPicture();
    const s = shown.get();
    const b = blend(s.v);
    const cx = w / 2;
    // The glow of the voice's mid colour.
    const glow = Skia.Paint();
    glow.setAntiAlias(true);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 34, true));
    glow.setColor(Float32Array.of(b.mid[0], b.mid[1], b.mid[2], clamp(s.glow)));
    canvas.drawCircle(cx, cy, radius * 1.25 * s.scale, glow);
    // The orb.
    const r = radius * s.scale;
    if (ORB_EFFECT) {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setShader(
        ORB_EFFECT.makeShader([
          cx,
          cy,
          r,
          s.tau,
          ...b.a.deep,
          ...b.a.mid,
          ...b.a.light,
          ...b.b.deep,
          ...b.b.mid,
          ...b.b.light,
          b.f,
          ...b.band,
          ...b.offset,
          b.swirl,
        ]),
      );
      canvas.drawCircle(cx, cy, r + 1, paint);
    }
    // Pager: the current dot travels and stretches between dots.
    const n = VOICES.length;
    const x0 = cx - (n - 1) * 7;
    const dots = Skia.Paint();
    dots.setAntiAlias(true);
    dots.setColor(Float32Array.of(1, 1, 1, 0.28));
    for (let i = 0; i < n; i++) canvas.drawCircle(x0 + i * 14, pagerY + 3, 3, dots);
    const v = clamp(s.v, -0.3, n - 1 + 0.3);
    const f = v - Math.floor(v);
    const stretch = 10 * (1 - Math.abs(2 * f - 1));
    const px = x0 + v * 14;
    dots.setColor(Float32Array.of(1, 1, 1, 1));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(px - 3 - stretch / 2, pagerY, 6 + stretch, 6), 3, 3), dots);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {w > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Voice picker">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <Text style={[styles.title, { top: h * 0.17 - 15 }]} numberOfLines={1} pointerEvents="none">
            Choose a voice
          </Text>
          {VOICES.map((voice, i) => (
            <VoiceName key={voice.name} index={i} shown={shown} top={nameY} name={voice.name} line={voice.line} />
          ))}
          <View style={[styles.button, { top: h - Math.max(insets.bottom, 16) - 12 - 52, width: w - 48 }]} pointerEvents="none">
            <Text style={styles.buttonLabel}>Continue</Text>
          </View>
        </>
      ) : null}
    </View>
  );
}

function VoiceName({ index, shown, top, name, line }: { index: number; shown: SharedValue<Shown>; top: number; name: string; line: string }) {
  const style = useAnimatedStyle(() => {
    const d = index - shown.get().v;
    return { opacity: 1 - smoothstep(0, 0.55, Math.abs(d)), transform: [{ translateX: 24 * d }] };
  });
  return (
    <Animated.View style={[styles.names, { top }, style]} pointerEvents="none">
      <Text style={styles.name} numberOfLines={1}>
        {name}
      </Text>
      <Text style={styles.line} numberOfLines={1}>
        {line}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  title: { position: 'absolute', left: 0, right: 0, fontSize: 24, fontWeight: '600', color: '#FFFFFF', textAlign: 'center' },
  names: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  name: { fontSize: 18, fontWeight: '600', color: '#FFFFFF' },
  line: { marginTop: 5, fontSize: 14, color: 'rgba(255,255,255,0.5)' },
  button: {
    position: 'absolute',
    left: 24,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: { fontSize: 17, fontWeight: '600', color: '#000000' },
});
