import { BlurStyle, Canvas, PaintStyle, Picture, Skia, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The "rendering" screen of an invented image app, Lumen: twelve glass discs stand in a ring like
// petals, each free to turn on the line from the centre. A wave runs round the ring — each disc in
// turn swings face-on, catching the light, and turns edge-on again — while the whole ring drifts
// round once a loop. Under it the step names change and a thin bar fills. Drag round the ring to
// spin it faster; tap to make every petal flip at once.
//
// Drawn in code: each disc is an ellipse (radius 34 pt, squeezed across the ring by |cos| of its
// turn) filled with a blue-to-violet glass gradient, a light rim and a glint that brightens as it
// faces you, over a soft violet halo.
// Layout (393 x 852 pt): 34 pt top row; ring radius 118 pt centred at 44 % of the height; the
// percentage 40 pt heavy in the middle; step line, progress bar and a cancel button below.
//
// autoPlay timeline (LOOP = 6 s): the wave goes round three times (turn of disc i = 2 pi (t / 2 -
// i / 12)); the ring turns once (linear); the percentage counts 0 -> 100 over 0.2-5.4 s (ease-in-out
// cubic) and fades back to 0 over 5.6-6.0; the step names change at 0, 2 and 4 s.
// Dragging round the ring adds spin that decays (e^-2.5 t); a tap flips all petals together over
// 0.6 s (light haptic).

/** Seconds. */
export const LOOP = 6;
const N = 12;
const RING = 118;
const DISC = 34;
const WAVE = 2;

const STEPS = ['Mixing the light', 'Adding depth', 'Polishing the glass'];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function drawRing(c: SkCanvas, t: number, spin: number, flip: number, cx: number, cy: number) {
  'worklet';
  const halo = Skia.Paint();
  halo.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, RING * 2, [Skia.Color('rgba(110,70,255,0.28)'), Skia.Color('rgba(110,70,255,0)')], null, TileMode.Clamp));
  c.drawCircle(cx, cy, RING * 2, halo);
  const drift = (t / LOOP) * Math.PI * 2 + spin;
  for (let i = 0; i < N; i++) {
    const a = drift + (i * Math.PI * 2) / N - Math.PI / 2;
    const x = cx + Math.cos(a) * RING;
    const y = cy + Math.sin(a) * RING;
    const turn = Math.PI * 2 * (t / WAVE - i / N) + flip * Math.PI;
    const face = Math.cos(turn);
    const squeeze = Math.max(0.08, Math.abs(face));
    c.save();
    c.translate(x, y);
    // The disc turns on the radial line, so it squeezes across the ring (along the tangent).
    c.rotate((a * 180) / Math.PI, 0, 0);
    c.scale(1, squeeze);
    const lit = Math.max(0, face);
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: -DISC, y: -DISC },
        { x: DISC, y: DISC },
        [Skia.Color(`rgba(${Math.round(70 + 90 * lit)}, ${Math.round(90 + 60 * lit)}, 255, ${0.35 + 0.4 * lit})`), Skia.Color('rgba(120,40,220,0.32)'), Skia.Color('rgba(20,10,80,0.5)')],
        [0, 0.55, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(0, 0, DISC, body);
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(2 / squeeze);
    rim.setColor(Skia.Color('#B9A8FF'));
    rim.setAlphaf(0.55 + 0.45 * lit);
    c.drawCircle(0, 0, DISC - 1, rim);
    if (lit > 0.2) {
      const glint = Skia.Paint();
      glint.setAntiAlias(true);
      glint.setColor(Skia.Color('#FFFFFF'));
      glint.setAlphaf(0.5 * (lit - 0.2));
      glint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      c.drawCircle(-DISC * 0.3, -DISC * 0.35, DISC * 0.35, glint);
    }
    c.restore();
  }
}

function Step({ k, clock }: { k: number; clock: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const start = k * 2;
    const o = seg(t, start, start + 0.3) * (1 - seg(t, start + 1.7, start + 2.0));
    // The first name is also showing as the loop wraps.
    const wrap = k === 0 ? seg(t, 5.7, 6.0) : 0;
    const v = Math.max(o, wrap);
    return { opacity: v, transform: [{ translateY: 8 * (1 - v) }] };
  });
  return <Animated.Text style={[styles.step, style]}>{STEPS[k]}</Animated.Text>;
}

/** Digit d of the percentage (right-aligned in three places), as a rolling column. */
function PercentDigit({ d, percent }: { d: number; percent: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const s = String(Math.round(percent.get() * 100)).padStart(3, ' ');
    const ch = s[d];
    return { transform: [{ translateY: ch === ' ' ? 48 : -48 * Number(ch) }] };
  });
  return (
    <View style={styles.digitWindow}>
      <Animated.View style={style}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
          <Text key={n} style={styles.digit}>
            {n}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

type Props = {
  /** Spins by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function PetalLoaderSpin({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.44;

  const elapsed = useSharedValue(0);
  const clock = useSharedValue(0);
  const spin = useSharedValue(0);
  const spinVel = useSharedValue(0);
  const flipAt = useSharedValue(-10);
  const flip = useSharedValue(0);
  const lastAngle = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (autoPlay) clock.set(now % LOOP);
    spin.set(spin.get() + spinVel.get() * dt);
    spinVel.set(spinVel.get() * Math.exp(-2.5 * dt));
    flip.set(easeInOutCubic(seg(now - flipAt.get(), 0, 0.6)) * (now - flipAt.get() < 0.6 ? 1 : 0));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawRing(canvas, clock.get(), spin.get(), flip.get(), cx, cy);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy]);

  const percent = useDerivedValue(() => {
    const t = clock.get();
    return easeInOutCubic(seg(t, 0.2, 5.4)) * (1 - seg(t, 5.6, 6.0));
  });
  const barStyle = useAnimatedStyle(() => ({ width: `${percent.get() * 100}%` }));
  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin((e) => lastAngle.set(Math.atan2(e.y - cy, e.x - cx)))
    .onUpdate((e) => {
      const a = Math.atan2(e.y - cy, e.x - cx);
      let d = a - lastAngle.get();
      if (d > Math.PI) d -= Math.PI * 2;
      if (d < -Math.PI) d += Math.PI * 2;
      spin.set(spin.get() + d);
      spinVel.set(d * 60);
      lastAngle.set(a);
    });
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      flipAt.set(elapsed.get());
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A ring of glass petals, rendering. Drag round to spin, tap to flip.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>Lumen</Text>
            <Text style={styles.meta}>Scene 4 of 6</Text>
          </View>
          <View style={[styles.centre, { top: cy - 30 }]} pointerEvents="none">
            {[0, 1, 2].map((d) => (
              <PercentDigit key={d} d={d} percent={percent} />
            ))}
            <Text style={styles.pct}>%</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="box-none">
        <Text style={styles.title}>Rendering “Glass Garden”</Text>
        <View style={styles.steps}>
          {STEPS.map((s, k) => (
            <Step key={s} k={k} clock={clock} />
          ))}
        </View>
        <View style={styles.track}>
          <Animated.View style={[styles.bar, barStyle]} />
        </View>
        <View style={styles.button}>
          <Text style={styles.action}>Run in background</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#04030A' },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.4 },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.5)' },
  centre: { position: 'absolute', left: 0, right: 0, height: 60, flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  digitWindow: { width: 28, height: 48, overflow: 'hidden' },
  digit: { width: 28, height: 48, lineHeight: 48, textAlign: 'center', fontSize: 40, fontWeight: '800', color: '#FFFFFF', fontVariant: ['tabular-nums'] },
  pct: { marginLeft: 2, fontSize: 22, fontWeight: '700', color: 'rgba(255,255,255,0.6)' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  title: { fontSize: 24, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.5 },
  steps: { height: 26, marginTop: 6 },
  step: { position: 'absolute', fontSize: 16, color: '#B9A8FF' },
  track: { marginTop: 14, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.1)', overflow: 'hidden' },
  bar: { height: 4, borderRadius: 2, backgroundColor: '#8F7BFF' },
  button: { marginTop: 22, height: 58, borderRadius: 29, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.25)', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
