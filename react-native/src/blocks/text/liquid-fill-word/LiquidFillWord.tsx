import {
  Canvas,
  Group,
  Paint,
  Path,
  Rect,
  RuntimeShader,
  Skia,
  Text as SkiaText,
  useClock,
  useFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A big word filled with three flat liquids. The letters are a mask over a field coloured by
// level: lime at the bottom, green above it and salmon on top, each with its own wavy surface,
// plus green blobs (salmon at their cores) swirling in the lime near its surface. The letter edges
// wobble a little, like a meniscus. Dragging up or down pours or drains it. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 8 s), levels from 0 (bottom of the descender) to 1 (top of the
// ascenders); 1.15 is brim-full and -0.08 empty:
//   0.0-2.0   full and resting: lime, with green blobs swirling near the top
//   2.0-4.0   drains (ease-in-out cubic), only the lime showing; the blobs fade out by 2.8
//   4.0-4.5   empty: only the faint glass of the letters
//   4.5-7.1   pours: salmon first (4.5 + 1.6 s), green behind it (4.8 + 1.8 s), lime last
//             (5.1 + 2.0 s), each ease-in-out cubic; the blobs come back 6.4-7.6
//   7.1-8.0   full and resting again
// The surfaces slosh harder while the level moves (slosh 0.35 -> 1, a sine bump over each move).
// The swirl's phase runs once around per loop, so one period repeats exactly.

/** Seconds. */
export const LOOP = 8;
const FULL = 1.15;
const EMPTY = -0.08;
const DRAIN_AT = 2;
const DRAIN_FOR = 2;
const SALMON_POUR: [number, number] = [4.5, 1.6];
const GREEN_POUR: [number, number] = [4.8, 1.8];
const LIME_POUR: [number, number] = [5.1, 2.0];
/** While draining, green and salmon sit this far under the lime, hidden. */
const HIDDEN = 0.25;
/** While a finger sets the level, green and salmon ride this far above the lime. */
const GREEN_BAND = 0.06;
const SALMON_BAND = 0.12;
/** Points of drag for the whole range. */
const DRAG_RANGE = 300;
const USER_HOLD = 2;
const SETTLE = 1.2;
const PAD = 24;
// The readout's line box, as SwiftUI sets 64 pt Paytone One: its ascent (1.113 em) above the
// baseline and its descent (0.283 em) below.
const READOUT_BASELINE = 64 * 1.113;
const READOUT_HEIGHT = 64 * (1.113 + 0.283);

const C = {
  background: '#93B0A1',
  ink: '#1F3329',
  lime: '#CDD98F',
  green: '#1DA07F',
  salmon: '#E8967A',
  /** The empty letters: a shade darker than the backdrop. */
  ghost: '#86A495',
};

type Liquid = { lime: number; green: number; salmon: number; blobs: number; slosh: number };
const REST: Liquid = { lime: FULL, green: FULL, salmon: FULL, blobs: 1, slosh: 0.35 };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
// The two curves the Swift block's Tween uses: ease-out (0, 0, 0.58, 1) and ease-in-out (0.42, 0, 0.58, 1).
function bezier(x1: number, x2: number, t: number) {
  'worklet';
  // y1 = 0, y2 = 1; solve x(s) = t by Newton's method, then return y(s).
  let s = t;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s - t;
    const dx = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    s = Math.min(1, Math.max(0, s - x / dx));
  }
  return 3 * (1 - s) * s * s + s * s * s;
}
function level(t: number, pour: [number, number]) {
  'worklet';
  return EMPTY + (FULL - EMPTY) * easeInOutCubic(clamp01((t - pour[0]) / pour[1]));
}
function bump(t: number, a: number, b: number) {
  'worklet';
  return Math.sin(Math.PI * clamp01((t - a) / (b - a)));
}

/** The liquids `t` seconds into the loop. */
function liquidAt(t: number): Liquid {
  'worklet';
  const slosh = 0.35 + 0.65 * (bump(t, 2.0, 4.3) + bump(t, 4.5, 7.6));
  const blobs = t < 4.25 ? 1 - easeInOutCubic(clamp01((t - DRAIN_AT) / 0.8)) : easeInOutCubic(clamp01((t - 6.4) / 1.2));
  if (t < 4.25) {
    const l = FULL + (EMPTY - FULL) * easeInOutCubic(clamp01((t - DRAIN_AT) / DRAIN_FOR));
    return { lime: l, green: l - HIDDEN, salmon: l - HIDDEN, blobs, slosh };
  }
  return { lime: level(t, LIME_POUR), green: level(t, GREEN_POUR), salmon: level(t, SALMON_POUR), blobs, slosh };
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

// The same function as the Metal one, in SkSL, in pixels (`scale` turns them into points).
const FILL_SKSL = `
uniform shader image;
uniform float scale;
uniform float phase;
uniform float lime;
uniform float green;
uniform float salmon;
uniform float blobs;
uniform float slosh;
uniform float bottomY;
uniform float topY;
uniform vec4 limeColor;
uniform vec4 greenColor;
uniform vec4 salmonColor;
uniform vec4 ghostColor;

half4 main(float2 fragCoord) {
  float2 p = fragCoord / scale;
  float ph = phase;
  float2 w = float2(
    sin(p.y * 0.31 + p.x * 0.07 + 2.0 * ph) + 0.25 * sin(p.y * 0.73 - p.x * 0.19 - 3.0 * ph),
    cos(p.x * 0.27 - p.y * 0.05 + 3.0 * ph) + 0.25 * cos(p.x * 0.61 + p.y * 0.23 + 2.0 * ph)
  ) * 1.0;
  float a = image.eval((p + w) * scale).a;
  float edge = smoothstep(0.35, 0.65, a);
  if (edge <= 0.0) { return half4(0.0); }

  float h = bottomY - topY;
  float u = (bottomY - p.y) / h;
  float aa = 0.9 / h;
  float amp = 0.025 + 0.045 * slosh;
  float wl = amp * (0.6 * sin(p.x * 0.045 + 2.0 * ph) + 0.4 * sin(p.x * 0.11 - 3.0 * ph + 1.3));
  float wg = amp * (0.6 * sin(p.x * 0.05 - 2.0 * ph + 2.1) + 0.4 * sin(p.x * 0.13 + 3.0 * ph + 0.4));
  float ws = amp * (0.6 * sin(p.x * 0.04 + 3.0 * ph + 4.0) + 0.4 * sin(p.x * 0.12 - 2.0 * ph + 2.6));
  float cl = 1.0 - smoothstep(lime + wl - aa, lime + wl + aa, u);
  float cg = 1.0 - smoothstep(green + wg - aa, green + wg + aa, u);
  float cs = 1.0 - smoothstep(salmon + ws - aa, salmon + ws + aa, u);

  float3 col = ghostColor.rgb;
  col = mix(col, salmonColor.rgb, cs);
  col = mix(col, greenColor.rgb, cg);
  col = mix(col, limeColor.rgb, cl);

  float b = sin(p.x * 0.05 + 2.0 * ph) + sin(p.y * 0.07 - ph + p.x * 0.025) + sin((p.x - p.y) * 0.04 + 3.0 * ph);
  float near = smoothstep(lime - 1.05, lime - 0.45, u);
  float tg = 3.1 - 2.4 * blobs * near;
  col = mix(col, greenColor.rgb, smoothstep(tg - 0.04, tg + 0.04, b) * cl);
  float ts = 3.1 - 1.2 * blobs * near;
  col = mix(col, salmonColor.rgb, smoothstep(ts - 0.04, ts + 0.04, b) * cl);

  return half4(half3(col * edge), half(edge));
}
`;
const fillEffect = Skia.RuntimeEffect.Make(FILL_SKSL)!;

const rgba = (hex: string) => {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, 1];
};
const COLORS = { limeColor: rgba(C.lime), greenColor: rgba(C.green), salmonColor: rgba(C.salmon), ghostColor: rgba(C.ghost) };

type Props = {
  word?: string;
  label?: string;
  issue?: string;
  caption?: string;
  levelLabel?: string;
  hint?: string;
  /** Drains and pours the word by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function haptic(light = true) {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(light ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

export function LiquidFillWord({
  word = 'Liquid',
  label = 'Fluid type',
  issue = 'No. 07',
  caption = 'Three liquids, one level.',
  levelLabel = 'Level',
  hint = 'Drag up or down to pour',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const pd = PixelRatio.get();
  const probeFont = useFont(require('./assets/PaytoneOne-Regular.ttf'), 100);
  const numberFont = useFont(require('./assets/PaytoneOne-Regular.ttf'), 64);
  const percentFont = useFont(require('./assets/PaytoneOne-Regular.ttf'), 26);

  // The word's outline, scaled to span the screen less 24 pt a side (at most 110 pt type), with
  // its ink box's top-left corner at the origin.
  const outline = useMemo(() => {
    if (!probeFont) return null;
    const path = Skia.Path.MakeFromText(word, 0, 0, probeFont);
    if (!path) return null;
    const box = path.computeTightBounds();
    const size = Math.min(110, Math.floor((100 * (width - 48)) / Math.max(box.width, 1)));
    const k = size / 100;
    const m = Skia.Matrix();
    m.scale(k, k);
    m.translate(-box.x, -box.y);
    path.transform(m);
    return { path, width: box.width * k, height: box.height * k };
  }, [probeFont, word, width]);

  const clock = useClock();
  const origin = useSharedValue(0);
  const manual = useSharedValue(false);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(FULL);
  const atLimit = useSharedValue(false);
  const lvl = useSharedValue<Tween>(still(FULL));
  const blobs = useSharedValue<Tween>(still(1));
  const slosh = useSharedValue<Tween>(still(0.35));
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The screen shows once its face is loaded (no frame without the word or the readout), and the
  // loop starts then.
  const ready = outline != null && numberFont != null && percentFont != null;
  useEffect(() => {
    if (ready) origin.set(clock.get());
  }, [ready, origin, clock]);

  const liquid = useDerivedValue<Liquid>(() => {
    const now = clock.get();
    if (manual.get()) {
      const l = tweenValue(lvl.get(), now);
      return { lime: l, green: l + GREEN_BAND, salmon: l + SALMON_BAND, blobs: tweenValue(blobs.get(), now), slosh: tweenValue(slosh.get(), now) };
    }
    if (!autoPlay || reduced) return REST;
    const t = Math.max(0, ((now - origin.get()) / 1000) % LOOP);
    return liquidAt(t);
  });

  const uniforms = useDerivedValue(() => {
    const l = liquid.get();
    const phase = reduced ? 0 : (2 * Math.PI * ((clock.get() / 1000) % LOOP)) / LOOP;
    const h = outline ? outline.height : 0;
    return {
      scale: pd,
      phase,
      lime: l.lime,
      green: l.green,
      salmon: l.salmon,
      blobs: l.blobs,
      slosh: reduced ? 0 : l.slosh,
      bottomY: PAD + h,
      topY: PAD,
      ...COLORS,
    };
  });

  // 2 s after the last touch the word fills back up, and the loop starts over from rest.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        const now = clock.get();
        lvl.set(retarget(lvl.get(), FULL, now, SETTLE, true));
        blobs.set(retarget(blobs.get(), 1, now, SETTLE, true));
        slosh.set(retarget(slosh.get(), 0.35, now, SETTLE, true));
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
  }, [touches, clock, dragging, lvl, blobs, slosh, origin, manual]);

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const now = clock.get();
      const from = liquid.get().lime;
      dragFrom.set(from);
      manual.set(true);
      dragging.set(true);
      lvl.set(still(from));
      blobs.set(retarget(blobs.get(), 0.5, now, 0.4, false));
      slosh.set(retarget(slosh.get(), 1, now, 0.4, false));
      scheduleOnRN(touched);
      scheduleOnRN(haptic, true);
    })
    .onUpdate((e) => {
      const target = Math.min(FULL, Math.max(EMPTY, dragFrom.get() - e.translationY / DRAG_RANGE));
      lvl.set(retarget(lvl.get(), target, clock.get(), 0.18, false));
      // A tap at either end of the range.
      const limit = target >= FULL || target <= EMPTY;
      if (limit && !atLimit.get()) scheduleOnRN(haptic, false);
      atLimit.set(limit);
    })
    .onFinalize(() => {
      dragging.set(false);
      scheduleOnRN(touched);
    });

  // The readout.
  const fraction = useDerivedValue(() => clamp01(liquid.get().lime));
  const percentText = useDerivedValue(() => `${Math.round(fraction.get() * 100)}`);
  const percentX = useDerivedValue(() => (numberFont ? numberFont.getTextWidth(percentText.get()) + 2 : 0));
  const barStyle = useAnimatedStyle(() => ({ width: `${fraction.get() * 100}%` }));

  const wordHeight = outline ? outline.height + 2 * PAD : 0;

  if (!ready) {
    return (
      <View style={styles.screen}>
        <StatusBar style="dark" />
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          <View style={[styles.top, { paddingTop: insets.top + 14 }]}>
            <Text style={styles.label}>{label}</Text>
            <Text style={styles.issue}>{issue}</Text>
          </View>

          <View style={{ flex: 1 }} />

          {/* The word: drawn white over a faint veil, coloured by the shader. */}
          <Canvas style={{ width, height: wordHeight }} accessibilityLabel={word} accessible>
            {outline ? (
              <Group transform={[{ scale: 1 / pd }]}>
                <Group
                  layer={
                    <Paint>
                      <RuntimeShader source={fillEffect} uniforms={uniforms} />
                    </Paint>
                  }
                >
                  <Group transform={[{ scale: pd }]}>
                    {/* A veil over the whole canvas, under the shader's edge threshold (0.35), so the shader sees all of it. */}
                    <Rect x={0} y={0} width={width} height={wordHeight} color="rgba(255,255,255,0.2)" />
                    <Group transform={[{ translateX: (width - outline.width) / 2 }, { translateY: PAD }]}>
                      <Path path={outline.path} color="#FFFFFF" />
                    </Group>
                  </Group>
                </Group>
              </Group>
            ) : null}
          </Canvas>

          <Text style={styles.caption}>{caption}</Text>

          <View style={{ flex: 1 }} />

          <View
            style={[styles.meter, { paddingBottom: insets.bottom + 28 }]}
            accessible
            accessibilityLabel={`${levelLabel} level`}
          >
            <Text style={styles.levelLabel}>{levelLabel.toUpperCase()}</Text>
            <Canvas style={styles.readout}>
              {numberFont && percentFont ? (
                <>
                  <SkiaText x={0} y={READOUT_BASELINE} text={percentText} font={numberFont} color={C.ink} />
                  <SkiaText x={percentX} y={READOUT_BASELINE} text="%" font={percentFont} color={C.ink} />
                </>
              ) : null}
            </Canvas>
            <View style={styles.track}>
              <Animated.View style={[styles.bar, barStyle]} />
            </View>
            <Text style={styles.hint}>{hint}</Text>
          </View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  top: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 28 },
  label: { fontSize: 15, fontWeight: '600', color: C.ink },
  issue: { fontSize: 15, fontWeight: '500', color: C.ink, opacity: 0.55, fontVariant: ['tabular-nums'] },
  caption: { marginTop: 6, paddingHorizontal: 28, fontSize: 17, fontWeight: '500', color: C.ink, opacity: 0.7, textAlign: 'center' },
  meter: { paddingHorizontal: 28 },
  levelLabel: { fontSize: 13, fontWeight: '600', letterSpacing: 1.2, color: C.ink, opacity: 0.6 },
  readout: { marginTop: 2, height: READOUT_HEIGHT, width: 220 },
  track: { marginTop: 10, height: 4, borderRadius: 2, backgroundColor: 'rgba(31,51,41,0.12)', overflow: 'hidden' },
  bar: { height: 4, borderRadius: 2, backgroundColor: C.ink },
  hint: { marginTop: 14, fontSize: 13, fontWeight: '500', color: C.ink, opacity: 0.5 },
});
