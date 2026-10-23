import {
  Blur,
  ClipOp,
  Canvas,
  Fill,
  Group,
  Paint,
  Picture,
  Shader,
  Skia,
  createPicture,
  useClock,
  useFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A festival poster on hot pink whose huge black number shreds into a comb of vertical strips,
// melts into a grainy blur, comes back out through the strips and settles sharp again, strip by
// strip from left to right. A port of the SwiftUI block, number for number. Swift samples the
// number through a Metal layer shader; here each of the 36 strips draws the number clipped to the
// strip through the same mapping (it is affine per strip), into a Skia picture made on the UI
// thread every frame. The blur is a Skia blur layer and the grain an SkSL shader over the screen.
//
// autoPlay timeline (LOOP = 6 s). `amount` 0 is the clean number and 1 every strip shredded;
// strips start in turn over the left-to-right order (`lead` 0) or right-to-left (`lead` 1).
// `blur` 0-1 is 0-22 pt of Gaussian blur, a 12 % zoom towards the 9 and the grain (0.08 at rest,
// so the poster always has a faint living grain, up to 0.45).
//   0.0-1.2   rest: the clean number
//   1.2-2.1   shred in, left strips first (amount 0 -> 1, ease-in-out cubic)
//   2.1-2.6   the comb ripples (phase-driven)
//   2.5-3.1   blur in (blur 0 -> 1, ease-in-out quad); under it the strips fall back to 0.3 (2.8-3.2)
//   3.1-3.4   the grainy blob
//   3.4-3.9   blur out (ease-out cubic) while the strips come back (amount 0.3 -> 1, 3.4-3.8)
//   3.9       lead flips to right-to-left (every strip is at 1, so nothing moves)
//   3.9-4.4   the comb ripples
//   4.4-5.4   resolve, right strips last (amount 1 -> 0, ease-out cubic)
//   5.4-6.0   rest
// The ripple's phase runs once around per loop, so one period repeats exactly.
//
// Touch: a finger shreds the strips nearest to it first (amount 0.45, + 1 per 200 pt dragged
// sideways), the strips slide with it, and dragging up or down blurs (1 per 240 pt). 2 s after
// the finger lifts everything eases back over 0.8 s and the loop starts over.

/** Seconds. */
export const LOOP = 6;
/** Points of blur at `blur` 1. */
const MAX_BLUR = 22;
const ZOOM = 0.12;
/** The grain's strength at rest and at full blur. */
const GRAIN = 0.08;
const BLUR_GRAIN = 0.45;
const USER_HOLD = 2;
const SETTLE = 0.8;
const STRIPS = 36;
/** Points above and below the glyphs for the stretched strips. */
const PAD = 72;
/** Extra canvas around the number so its blur is not cut off (SwiftUI's blur is not clipped). */
const BLEED = 72;
const FONT = 'ShredBlurNumber-ArchivoBlack';

const C = {
  background: '#F063D4',
  ink: '#14090F',
};

type State = { amount: number; lead: number; blur: number };
const REST: State = { amount: 0, lead: 0, blur: 0 };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function easeInOutQuad(t: number) {
  'worklet';
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}
function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}
function span(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

/** The screen `t` seconds into the loop. */
function stateAt(t: number): State {
  'worklet';
  const blur = t < 3.4 ? easeInOutQuad(span(t, 2.5, 3.1)) : 1 - easeOutCubic(span(t, 3.4, 3.9));
  let amount: number;
  if (t < 2.8) amount = easeInOutCubic(span(t, 1.2, 2.1));
  else if (t < 3.4) amount = 1 - 0.7 * easeInOutCubic(span(t, 2.8, 3.2));
  else if (t < 4.4) amount = 0.3 + 0.7 * easeInOutCubic(span(t, 3.4, 3.8));
  else amount = 1 - easeOutCubic(span(t, 4.4, 5.4));
  return { amount, lead: t < 3.9 ? 0 : 1, blur };
}

function fract(x: number) {
  'worklet';
  return x - Math.floor(x);
}
/** The Metal hash. */
function hash1(n: number) {
  'worklet';
  let x = fract(n * 0.1031);
  let y = fract(n * 1.37 * 0.1031);
  let z = fract(n * 0.73 * 0.1031);
  const d = x * (y + 33.33) + y * (z + 33.33) + z * (x + 33.33);
  x += d;
  y += d;
  z += d;
  return fract((x + y) * z);
}

/**
 * The strip `i` of a canvas `w` × `h` (points); the same numbers as the Metal shader. The strip
 * shows the layer point (cx + (x - c) * span, h/2 + (y - h/2 - yoff) / stretch).
 */
function strip(i: number, w: number, h: number, amount: number, lead: number, focusX: number, focusMix: number, phase: number, drift: number) {
  'worklet';
  const n = STRIPS;
  const sw = w / n;
  const c = (i + 0.5) * sw;
  let order = i / (n - 1) + (1 - (2 * i) / (n - 1)) * lead;
  order += (clamp01(Math.abs(c - focusX) / (w * 0.6)) - order) * focusMix;
  let a = clamp01(amount * 2.2 - 1.2 * order);
  a = a * a * (3 - 2 * a);
  if (a <= 0) return { a: 0, c, cx: c, span: 1, stretch: 1, yoff: 0 };
  const h1 = hash1(i + 1);
  const h2 = hash1(i + 31);
  const h3 = hash1(i + 71);
  const s1 = 0.5 + 0.5 * Math.sin(i * 0.35 + phase + 1);
  const s2 = 0.5 + 0.5 * Math.sin(i * 0.22 - 2 * phase + 2);
  return {
    a,
    c,
    cx: c + a * ((s2 - 0.5) * 2 * sw + (h2 - 0.5) * 1.6 * sw + drift * (0.5 + h3)),
    span: 1 + (1.4 + 2.2 * s1 + 0.8 * h1 - 1) * a,
    stretch: 1 + a * (0.3 + 1.6 * s2 + 0.6 * h2),
    yoff: a * h * 0.1 * (Math.sin(i * 0.4 + 2 * phase + 1.3) + 0.8 * (h1 - 0.5)),
  };
}

// The two curves the Swift block's Tween uses: ease-out (0, 0, 0.58, 1) and ease-in-out (0.42, 0, 0.58, 1).
function bezier(x1: number, x2: number, t: number) {
  'worklet';
  let s = t;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s - t;
    const dx = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    s = Math.min(1, Math.max(0, s - x / dx));
  }
  return 3 * (1 - s) * s * s + s * s * s;
}

/** A number animating towards a target, sampled against the clock (ms). */
type Tween = { from: number; to: number; start: number; duration: number; inOut: boolean };
function still(v: number): Tween {
  'worklet';
  return { from: v, to: v, start: 0, duration: 0, inOut: false };
}
function tweenValue(tw: Tween, at: number) {
  'worklet';
  if (tw.duration <= 0) return tw.to;
  const t = clamp01((at - tw.start) / 1000 / tw.duration);
  return tw.from + (tw.to - tw.from) * (tw.inOut ? bezier(0.42, 0.58, t) : bezier(0, 0.58, t));
}
function retarget(tw: Tween, target: number, at: number, duration: number, inOut: boolean): Tween {
  'worklet';
  return { from: tweenValue(tw, at), to: target, start: at, duration, inOut };
}

// Film grain over the whole screen, the Metal function in SkSL (local coordinates are points):
// cells of 1.25 pt, a new pattern `frame` by `frame`, white and black specks up to `amount` × 0.5
// opacity.
const GRAIN_SKSL = `
uniform float amount;
uniform float frame;

float hash2(float2 c) {
  float3 p3 = fract(float3(c.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 p) {
  float v = hash2(floor(p / 1.25) + float2(frame * 17.0, frame * 31.0)) - 0.5;
  float a = amount * abs(v);
  return v > 0.0 ? half4(half(a), half(a), half(a), half(a)) : half4(0.0, 0.0, 0.0, half(a));
}
`;
const grainEffect = Skia.RuntimeEffect.Make(GRAIN_SKSL)!;

type Props = {
  number?: string;
  brand?: string;
  since?: string;
  kicker?: string;
  headline?: string;
  dates?: string;
  venue?: string;
  action?: string;
  hint?: string;
  /** Shreds, blurs and resolves the number by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function haptic(strength: number) {
  if (process.env.EXPO_OS === 'web') return;
  const style = strength >= 1 ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Soft;
  Haptics.impactAsync(style).catch(() => {});
}

export function ShredBlurNumber({
  number = '19',
  brand = 'LATE FORMS',
  since = 'SINCE 2007',
  kicker = 'EDITION NINETEEN',
  headline = 'Nineteen years\nof loud letters.',
  dates = 'Oct 17 – 19',
  venue = 'Pier Hall, Dock 4',
  action = 'Get passes',
  hint = 'Drag across the number',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FONT]: require('./assets/ArchivoBlack-Regular.ttf') });
  const probeFont = useFont(require('./assets/ArchivoBlack-Regular.ttf'), 100);

  // The number's outline, sized to span the screen less 24 pt a side and stand at most 30 % of
  // its height, with its ink box's top-left corner at the origin.
  const glyphs = useMemo(() => {
    if (!probeFont) return null;
    const path = Skia.Path.MakeFromText(number, 0, 0, probeFont);
    if (!path) return null;
    const box = path.computeTightBounds();
    const size = Math.floor(Math.min((100 * (width - 48)) / Math.max(box.width, 1), (100 * (height * 0.3)) / Math.max(box.height, 1)));
    const k = size / 100;
    const m = Skia.Matrix();
    m.scale(k, k);
    m.translate(-box.x, -box.y);
    path.transform(m);
    return { path, width: box.width * k, height: box.height * k };
  }, [probeFont, number, width, height]);
  const numberHeight = glyphs ? glyphs.height + 2 * PAD : 0;

  const clock = useClock();
  const origin = useSharedValue(0);
  const manual = useSharedValue(false);
  const dragging = useSharedValue(false);
  const amount = useSharedValue<Tween>(still(0));
  const blur = useSharedValue<Tween>(still(0));
  const drift = useSharedValue<Tween>(still(0));
  const focusMix = useSharedValue<Tween>(still(0));
  const lead = useSharedValue(0);
  const focusX = useSharedValue(0);
  const atFull = useSharedValue(false);
  const atBlur = useSharedValue(false);
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The loop starts when the screen appears.
  useEffect(() => {
    origin.set(clock.get());
  }, [origin, clock]);

  const state = useDerivedValue<State>(() => {
    const now = clock.get();
    if (manual.get()) return { amount: tweenValue(amount.get(), now), lead: lead.get(), blur: tweenValue(blur.get(), now) };
    if (!autoPlay || reduced) return REST;
    return stateAt(Math.max(0, ((now - origin.get()) / 1000) % LOOP));
  });
  /** Seconds into the loop (0 under reduced motion). */
  const loopTime = useDerivedValue(() => (reduced ? 0 : Math.max(0, ((clock.get() - origin.get()) / 1000) % LOOP)));

  const picture = useDerivedValue(() => {
    const s = state.get();
    const now = clock.get();
    const phase = (2 * Math.PI * loopTime.get()) / LOOP;
    const mix = manual.get() ? tweenValue(focusMix.get(), now) : 0;
    const shift = manual.get() ? tweenValue(drift.get(), now) : 0;
    const fx = focusX.get();
    return createPicture((canvas) => {
      if (!glyphs) return;
      const w = width;
      const h = numberHeight;
      const bg = Skia.Paint();
      bg.setColor(Skia.Color(C.background));
      const ink = Skia.Paint();
      ink.setAntiAlias(true);
      ink.setColor(Skia.Color(C.ink));
      canvas.translate(0, BLEED);
      canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
      const left = (w - glyphs.width) / 2;
      if (s.amount <= 0.0005) {
        canvas.save();
        canvas.translate(left, PAD);
        canvas.drawPath(glyphs.path, ink);
        canvas.restore();
        return;
      }
      const sw = w / STRIPS;
      for (let i = 0; i < STRIPS; i++) {
        const st = strip(i, w, h, s.amount, s.lead, fx, mix, phase, shift);
        canvas.save();
        canvas.clipRect(Skia.XYWHRect(i * sw, 0, sw, h), ClipOp.Intersect, true);
        // The inverse of the strip's mapping: x = c + (xs - cx) / span, y = h/2 + yoff + (ys - h/2) * stretch.
        canvas.translate(st.c, h / 2 + st.yoff);
        canvas.scale(1 / st.span, st.stretch);
        canvas.translate(-st.cx + left, -h / 2 + PAD);
        canvas.drawPath(glyphs.path, ink);
        canvas.restore();
      }
    });
  });

  // Zoomed about (68 %, 50 %) of the number's canvas, then blurred (as SwiftUI does it).
  const zoom = useDerivedValue(() => {
    const z = 1 + ZOOM * state.get().blur;
    const ax = width * 0.68;
    const ay = BLEED + numberHeight / 2;
    return [{ translateX: ax }, { translateY: ay }, { scale: z }, { translateX: -ax }, { translateY: -ay }];
  });
  const blurSigma = useDerivedValue(() => MAX_BLUR * state.get().blur);
  const grainUniforms = useDerivedValue(() => ({
    amount: GRAIN + (BLUR_GRAIN - GRAIN) * state.get().blur,
    frame: Math.floor(loopTime.get() * 24),
  }));

  // 2 s after the last touch the number eases back to rest, and the loop starts over.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        const now = clock.get();
        amount.set(retarget(amount.get(), 0, now, SETTLE, true));
        blur.set(retarget(blur.get(), 0, now, SETTLE, true));
        drift.set(retarget(drift.get(), 0, now, SETTLE, true));
        list.push(
          setTimeout(() => {
            if (dragging.get()) return;
            origin.set(clock.get());
            manual.set(false);
          }, SETTLE * 1000),
        );
      }, USER_HOLD * 1000),
    );
    return () => {
      list.forEach(clearTimeout);
      timers.current = [];
    };
  }, [touches, clock, dragging, amount, blur, drift, origin, manual]);

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      const now = clock.get();
      const current = state.get();
      dragging.set(true);
      manual.set(true);
      amount.set(still(current.amount));
      blur.set(still(current.blur));
      lead.set(current.lead);
      drift.set(still(0));
      focusMix.set(retarget(still(0), 1, now, 0.3, false));
      focusX.set(e.x);
      atFull.set(false);
      atBlur.set(false);
      scheduleOnRN(touched);
      scheduleOnRN(haptic, 1);
    })
    .onUpdate((e) => {
      const now = clock.get();
      focusX.set(e.x);
      const a = Math.min(1, 0.45 + Math.abs(e.translationX) / 200);
      const b = Math.min(1, Math.abs(e.translationY) / 240);
      amount.set(retarget(amount.get(), a, now, 0.18, false));
      blur.set(retarget(blur.get(), b, now, 0.18, false));
      drift.set(retarget(drift.get(), e.translationX * 0.35, now, 0.18, false));
      // A tap when the strips are all out, and when the blur is full.
      if (a >= 1 && !atFull.get()) scheduleOnRN(haptic, 0.7);
      if (b >= 1 && !atBlur.get()) scheduleOnRN(haptic, 0.5);
      atFull.set(a >= 1);
      atBlur.set(b >= 1);
    })
    .onFinalize(() => {
      dragging.set(false);
      scheduleOnRN(touched);
    });

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
            <View style={styles.row}>
              <Text style={styles.mono}>{brand}</Text>
              <Text style={styles.mono}>{since}</Text>
            </View>
            <View style={[styles.rule, { marginTop: 12 }]} />
          </View>

          <View style={{ flex: 1 }} />

          <View style={{ height: numberHeight }} accessible accessibilityLabel={number}>
            <Canvas pointerEvents="none" style={{ width, height: numberHeight + 2 * BLEED, marginTop: -BLEED }}>
              <Group
                layer={
                  <Paint>
                    <Blur blur={blurSigma} mode="decal" />
                  </Paint>
                }
              >
                <Group transform={zoom}>
                  <Picture picture={picture} />
                </Group>
              </Group>
            </Canvas>
          </View>

          <View style={{ flex: 1 }} />

          <View style={[styles.footer, { paddingBottom: insets.bottom + 16 }]}>
            <Text style={[styles.mono, styles.kicker]}>{kicker}</Text>
            {fontsLoaded ? <Text style={styles.headline}>{headline}</Text> : null}
            <View style={[styles.rule, { marginTop: 20 }]} />
            <View style={styles.actions}>
              <View style={styles.when}>
                <Text style={styles.dates}>{dates}</Text>
                <Text style={styles.venue}>{venue}</Text>
              </View>
              <View style={styles.button} accessible accessibilityRole="button">
                <Text style={styles.buttonLabel}>{action}</Text>
              </View>
            </View>
            <Text style={styles.hint}>{hint}</Text>
          </View>

          {/* Film grain: faint all the time, strong while the number is blurred. */}
          <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={grainEffect} uniforms={grainUniforms} />
            </Fill>
          </Canvas>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  header: { paddingHorizontal: 24 },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  mono: { fontFamily: MONO, fontSize: 12, fontWeight: '600', letterSpacing: 1.4, color: C.ink },
  rule: { height: 1.5, backgroundColor: C.ink },
  footer: { paddingHorizontal: 24 },
  kicker: { opacity: 0.7 },
  // Archivo Black's line is 1.088 em, 33 pt apart on the Swift screen (its lineSpacing(-2) takes nothing off).
  headline: { marginTop: 10, fontFamily: FONT, fontSize: 30, lineHeight: 33, letterSpacing: -0.6, color: C.ink },
  actions: { marginTop: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  when: { flex: 1, gap: 2 },
  dates: { fontSize: 17, fontWeight: '700', color: C.ink },
  venue: { fontSize: 15, fontWeight: '500', color: C.ink, opacity: 0.7 },
  button: { height: 48, paddingHorizontal: 24, borderRadius: 24, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { fontSize: 16, fontWeight: '700', color: C.background },
  hint: { marginTop: 20, fontSize: 13, fontWeight: '500', color: C.ink, opacity: 0.55, textAlign: 'center' },
});
