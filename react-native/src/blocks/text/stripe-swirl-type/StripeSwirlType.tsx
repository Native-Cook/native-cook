import { BlurMask, Canvas, Circle, Group, Paint, Path, Rect, RuntimeShader, Skia, useClock, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { PixelRatio, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { fieldEffect } from './stripeSwirlShader';

// A magazine's new-issue screen whose cover line, "MAKE / SOME / WAVES", is drawn only as
// horizontal stripes. A swirl field stirs them: around its centre the stripes twist into rings
// and arcs that break into coloured capsules, then the swirl unwinds (past straight and back) and
// the words read again. Dragging through the type stirs it with the finger. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-1.0   rest: straight stripes, legible words
//   0.8-1.15  a ghost finger ring fades in on the swirl's centre
//   1.0-4.6   the centre circles the words clockwise (1.25 turns, ease-in-out cubic)
//   1.0-3.3   the twist builds 0 -> 7.5 rad, the radius 60 -> 170 pt (ease-in-out cubic)
//   2.4-4.6   an extra spin of the core (2.2 rad, sin² bump)
//   3.3-4.4   peak: rings and colour
//   4.4-4.85  the ring fades out
//   4.4-6.8   the twist unwinds: 7.5 (1 - u)³ cos(1.8 π u), u = smoothstep over the phase, so it
//             dips about 1 rad the other way and lands at 0 with no speed; the radius shrinks back
//   6.8-8.0   rest, identical to t = 0

/** Seconds. */
export const LOOP = 8;
/** Twist at the peak, radians at the centre. */
const PEAK = 7.5;
const MIN_RADIUS = 60;
const MAX_RADIUS = 170;
const SPIN = 2.2;
/** The centre's path, as fractions of the screen width and of the type block's height. */
const ORBIT_X = 0.2;
const ORBIT_Y = 0.16;
const USER_HOLD = 2;
const UNWIND = 1.6;
const MARGIN = 24;
const GAP = 18;
const REST_PHI = -0.35 * Math.PI;

const C = {
  background: '#100F0E',
  ink: '#F1ECE3',
  red: '#E8432E',
  blue: '#2D63D8',
  green: '#2C9F5B',
  yellow: '#F2B42C',
  sky: '#49B3EA',
};

const ANTON = require('./assets/Anton-Regular.ttf');
const SERIF = require('./assets/DMSerifDisplay-Regular.ttf');
const ANTON_FAMILY = 'StripeSwirlType-Anton';
const SERIF_FAMILY = 'StripeSwirlType-DMSerifDisplay';

const rgba = (hex: string) => {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, 1];
};
const COLORS = {
  p3: Platform.OS === 'ios' ? 1 : 0,
  inkColor: rgba(C.ink),
  backColor: rgba(C.background),
  c0: rgba(C.red),
  c1: rgba(C.blue),
  c2: rgba(C.green),
  c3: rgba(C.yellow),
  c4: rgba(C.sky),
};

type Stir = { ox: number; oy: number; twist: number; radius: number; spin: number; ring: number; ringScale: number };

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}
function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}
function easeInCubic(t: number) {
  'worklet';
  return t * t * t;
}
/** From 1 to 0, dipping about 0.14 below zero on the way and landing with no speed. */
function unwound(x: number) {
  'worklet';
  const u = x * x * (3 - 2 * x);
  return Math.pow(1 - u, 3) * Math.cos(1.8 * Math.PI * u);
}
function radiusForTwist(twist: number) {
  'worklet';
  return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * Math.min(1, Math.abs(twist) / PEAK);
}
function restStir(): Stir {
  'worklet';
  return { ox: Math.cos(REST_PHI), oy: Math.sin(REST_PHI), twist: 0, radius: MIN_RADIUS, spin: 0, ring: 0, ringScale: 0.8 };
}

/** The stir `t` seconds into the loop. */
function stirAt(t: number): Stir {
  'worklet';
  const build = easeInOutCubic(seg(t, 1.0, 3.3));
  const x = seg(t, 4.4, 6.8);
  const twist = t < 4.4 ? PEAK * build : PEAK * unwound(x);
  const env = t < 4.4 ? build : 1 - easeInOutCubic(x);
  const swing = Math.sin(Math.PI * seg(t, 2.4, 4.6));
  const phi = REST_PHI + 2.5 * Math.PI * easeInOutCubic(seg(t, 1.0, 4.6));
  const ringIn = easeOutCubic(seg(t, 0.8, 1.15));
  const ringOut = easeInCubic(seg(t, 4.4, 4.85));
  return {
    ox: Math.cos(phi),
    oy: Math.sin(phi),
    twist,
    radius: MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * env,
    spin: SPIN * swing * swing,
    ring: ringIn * (1 - ringOut),
    ringScale: 0.8 + 0.2 * ringIn - 0.15 * ringOut,
  };
}

// The ease-out (0, 0, 0.58, 1) the Swift block's Tween uses.
function easeOut(t: number) {
  'worklet';
  let s = t;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - s) * s * s * 0.58 + s * s * s - t;
    const dx = 6 * (1 - s) * s * 0.58 - 3 * s * s * 0.58 + 3 * s * s;
    if (Math.abs(dx) < 1e-6) break;
    s = Math.min(1, Math.max(0, s - x / dx));
  }
  return 3 * (1 - s) * s * s + s * s * s;
}

/** A number animating towards a target, sampled against the clock (ms). */
type Tween = { from: number; to: number; start: number; duration: number };
function still(v: number): Tween {
  'worklet';
  return { from: v, to: v, start: 0, duration: 0 };
}
function tweenValue(tw: Tween, at: number) {
  'worklet';
  if (tw.duration <= 0) return tw.to;
  const t = Math.min(1, Math.max(0, (at - tw.start) / 1000 / tw.duration));
  return tw.from + (tw.to - tw.from) * easeOut(t);
}
function retarget(tw: Tween, target: number, at: number, duration: number): Tween {
  'worklet';
  return { from: tweenValue(tw, at), to: target, start: at, duration };
}

type Props = {
  wordmark?: string;
  issue?: string;
  hint?: string;
  headline?: string;
  subline?: string;
  action?: string;
  /** Stirs the words by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function haptic(soft = false) {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(soft ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function StripeSwirlType({
  wordmark = 'UNDERTOW',
  issue = 'Issue 07 · Autumn',
  hint = 'Drag through the type to stir it',
  headline = 'Issue 07 is out.',
  subline = 'Twelve stories about change, told slowly.',
  action = 'Start reading',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const pd = PixelRatio.get();
  const [fontsLoaded] = useFonts({ [ANTON_FAMILY]: ANTON, [SERIF_FAMILY]: SERIF });
  const antonProbe = useFont(ANTON, 100);
  const serifProbe = useFont(SERIF, 100);

  // Bottom copy, from the bottom up: 18 + button 54 + 22 + subline 20 + 10 + headline 32 + 22 + hint 16.
  const hintTop = height - insets.bottom - 194;
  const regionTop = insets.top + 70;
  const regionBottom = hintTop - 24;

  // The three words, each fitted to the width less the margins and centred, stacked with GAP
  // between them and centred in their region (scaled down if they do not fit).
  const words = useMemo(() => {
    if (!antonProbe || !serifProbe) return null;
    const lines = [
      { word: 'MAKE', font: antonProbe },
      { word: 'SOME', font: serifProbe },
      { word: 'WAVES', font: antonProbe },
    ];
    const target = width - 2 * MARGIN;
    const shapes = lines.map((line) => {
      const path = Skia.Path.MakeFromText(line.word, 0, 0, line.font) ?? Skia.Path.Make();
      const box = path.computeTightBounds();
      const fit = target / Math.max(box.width, 1);
      return { path, box, fit };
    });
    const total = shapes.reduce((sum, s) => sum + s.box.height * s.fit, 0) + GAP * (shapes.length - 1);
    const k = Math.min(1, (regionBottom - regionTop) / total);
    let y = regionTop + (regionBottom - regionTop - total * k) / 2;
    const top = y;
    const builder = Skia.PathBuilder.Make();
    for (const s of shapes) {
      const scale = s.fit * k;
      const m = Skia.Matrix();
      m.translate((width - s.box.width * scale) / 2, y);
      m.scale(scale, scale);
      m.translate(-s.box.x, -s.box.y);
      builder.addPath(s.path, m);
      y += s.box.height * scale + GAP * k;
    }
    return { path: builder.build(), top, height: y - GAP * k - top };
  }, [antonProbe, serifProbe, width, regionTop, regionBottom]);

  const blockMid = words ? words.top + words.height / 2 : height / 2;
  const blockHeight = words ? words.height : 0;

  const clock = useClock();
  const origin = useSharedValue(0);
  // True from a touch until the swirl has unwound and the loop has started over.
  const manual = useSharedValue(false);
  const dragging = useSharedValue(false);
  const cx = useSharedValue<Tween>(still(0));
  const cy = useSharedValue<Tween>(still(0));
  const tw = useSharedValue<Tween>(still(0));
  // When the finger's swirl started to unwind (-1: not unwinding), and from which twist.
  const unwindStart = useSharedValue(-1);
  const unwindFrom = useSharedValue(0);
  const sign = useSharedValue(1);
  const peaked = useSharedValue(false);
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The screen shows once its faces are loaded, and the loop starts then.
  const ready = words != null && fontsLoaded;
  useEffect(() => {
    if (ready) origin.set(clock.get());
  }, [ready, origin, clock]);

  // The swirl, in points: its centre, twist, radius and spin, plus the ghost ring.
  const field = useDerivedValue(() => {
    const now = clock.get();
    if (manual.get()) {
      let twist = tweenValue(tw.get(), now);
      const start = unwindStart.get();
      if (start >= 0) twist = unwindFrom.get() * unwound(Math.min(1, Math.max(0, (now - start) / 1000 / UNWIND)));
      return { cx: tweenValue(cx.get(), now), cy: tweenValue(cy.get(), now), twist, radius: radiusForTwist(twist), spin: 0, ring: 0, ringScale: 1 };
    }
    const stir = autoPlay && !reduced ? stirAt(Math.max(0, ((now - origin.get()) / 1000) % LOOP)) : restStir();
    return {
      cx: width / 2 + stir.ox * ORBIT_X * width,
      cy: blockMid + stir.oy * ORBIT_Y * blockHeight,
      twist: stir.twist,
      radius: stir.radius,
      spin: stir.spin,
      ring: stir.ring,
      ringScale: stir.ringScale,
    };
  });

  const uniforms = useDerivedValue(() => {
    const f = field.get();
    return {
      scale: pd,
      size: [width, height],
      centre: [f.cx, f.cy],
      twist: f.twist,
      radius: f.radius,
      spin: f.spin,
      ...COLORS,
    };
  });
  const ringCx = useDerivedValue(() => field.get().cx);
  const ringCy = useDerivedValue(() => field.get().cy);
  const ringR = useDerivedValue(() => 22 * field.get().ringScale);
  const ringOpacity = useDerivedValue(() => field.get().ring);

  // 2 s after the last touch the swirl unwinds, and the loop starts over from rest.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        const now = clock.get();
        unwindFrom.set(tweenValue(tw.get(), now));
        unwindStart.set(now);
        list.push(
          setTimeout(() => {
            if (dragging.get()) return;
            unwindStart.set(-1);
            tw.set(still(0));
            origin.set(clock.get());
            manual.set(false);
          }, UNWIND * 1000),
        );
      }, USER_HOLD * 1000),
    );
    return () => {
      list.forEach(clearTimeout);
      timers.current = [];
    };
  }, [touches, clock, dragging, tw, unwindFrom, unwindStart, origin, manual]);

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      const now = clock.get();
      // Pick the swirl up where it is, so the touch never jumps; it moves to the finger at once.
      const current = field.get();
      manual.set(true);
      dragging.set(true);
      peaked.set(false);
      unwindStart.set(-1);
      cx.set(retarget(still(current.cx), e.x, now, 0.1));
      cy.set(retarget(still(current.cy), e.y, now, 0.1));
      tw.set(retarget(still(current.twist), sign.get() * PEAK * 0.35, now, 0.35));
      scheduleOnRN(touched);
      scheduleOnRN(haptic, false);
    })
    .onUpdate((e) => {
      const now = clock.get();
      cx.set(retarget(cx.get(), e.x, now, 0.1));
      cy.set(retarget(cy.get(), e.y, now, 0.1));
      const speed = Math.hypot(e.velocityX, e.velocityY);
      // Stirring clockwise around the words twists one way, anticlockwise the other.
      const cross = (e.x - width / 2) * e.velocityY - (e.y - blockMid) * e.velocityX;
      if (speed > 40) sign.set(cross >= 0 ? 1 : -1);
      const target = sign.get() * PEAK * Math.min(1, Math.max(0, 0.35 + speed / 900));
      tw.set(retarget(tw.get(), target, now, 0.35));
      if (!peaked.get() && Math.abs(tweenValue(tw.get(), now)) > 7) {
        peaked.set(true);
        scheduleOnRN(haptic, true);
      }
    })
    .onFinalize(() => {
      dragging.set(false);
      scheduleOnRN(touched);
    });

  if (!ready || !words) {
    return (
      <View style={styles.screen}>
        <StatusBar style="light" />
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          {/* The words: a mask (black, the words white and blurred) that the shader turns into stripes. */}
          <Canvas style={StyleSheet.absoluteFill} accessibilityLabel="MAKE SOME WAVES" accessible>
            <Group transform={[{ scale: 1 / pd }]}>
              <Group
                layer={
                  <Paint>
                    <RuntimeShader source={fieldEffect} uniforms={uniforms} />
                  </Paint>
                }
              >
                <Group transform={[{ scale: pd }]}>
                  <Rect x={0} y={0} width={width} height={height} color="#000000" />
                  <Path path={words.path} color="#FFFFFF">
                    <BlurMask blur={1.6} style="normal" />
                  </Path>
                </Group>
              </Group>
            </Group>
            {/* The ghost finger that stirs the words in the loop. */}
            <Group opacity={ringOpacity}>
              <Circle cx={ringCx} cy={ringCy} r={ringR} color="rgba(241,236,227,0.1)" />
              <Circle cx={ringCx} cy={ringCy} r={ringR} color="rgba(241,236,227,0.7)" style="stroke" strokeWidth={1.5} />
            </Group>
          </Canvas>

          <View style={[styles.copy, { paddingTop: insets.top + 10, paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
            <View style={styles.top}>
              <Text style={styles.wordmark}>{wordmark}</Text>
              <Text style={styles.issue}>{issue}</Text>
            </View>
            <View style={styles.rule} />

            <View style={{ flex: 1 }} />

            <Text style={styles.hint}>{hint}</Text>
            <Text style={styles.headline}>{headline}</Text>
            <Text style={styles.subline} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
              {subline}
            </Text>
            <View style={styles.button} accessible accessibilityRole="button">
              <Text style={styles.action}>{action}</Text>
            </View>
          </View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  copy: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, paddingHorizontal: MARGIN },
  top: { height: 30, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  wordmark: { fontFamily: ANTON_FAMILY, fontSize: 20, letterSpacing: 2, color: C.ink },
  issue: { fontSize: 13, fontWeight: '500', color: C.ink, opacity: 0.55 },
  rule: { marginTop: 10, height: 1, backgroundColor: 'rgba(241,236,227,0.14)' },
  hint: { height: 16, fontSize: 13, lineHeight: 16, fontWeight: '500', color: C.ink, opacity: 0.45 },
  headline: { marginTop: 22, height: 32, fontFamily: SERIF_FAMILY, fontSize: 26, lineHeight: 32, color: C.ink },
  subline: { marginTop: 10, height: 20, fontSize: 15, lineHeight: 20, color: C.ink, opacity: 0.62 },
  button: { marginTop: 22, height: 54, borderRadius: 27, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '600', color: C.background },
});
