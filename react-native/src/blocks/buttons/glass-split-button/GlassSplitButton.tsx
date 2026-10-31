import { BackdropBlur, BlurMask, Canvas, Circle, Fill, Group, LinearGradient, Path, RoundedRect, Shadow, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The capture button of an invented notes app, Drift: a frosted glass disc floats over soft
// drifting colour. Pressed, it first gathers itself into a small pill, then springs open into a
// wide glass bar whose three actions draw themselves in — a ring for Photo, a plus for Note, a
// ring for Voice — each with its label. A moment later it folds back the same way into the disc.
// Tap the glass to open or close it.
//
// The glass is a Skia BackdropBlur clipped to the morphing rounded rectangle, with a white wash,
// a gradient rim and a warm glint along its lower edge.
// Layout (393 x 852 pt): 34 pt top row; title 44 pt heavy; glass centred at 50 % of the height:
// disc 132 pt -> pill 104 x 52 -> bar 272 x 92 (radius h / 2); labels 28 pt under the bar.
//
// autoPlay timeline (LOOP = 7 s):
//   0.0-1.0  rest: the disc
//   1.0-1.4  the disc gathers into the small pill (ease-in-out cubic)
//   1.4-2.1  the pill springs open into the bar (ease-out back)
//   1.7-2.3  the icons draw in (path trim, ease-out cubic); labels fade up
//   3.3-3.7  a press on Note (scale 0.95 and back)
//   4.8-5.15 the icons erase (ease-in cubic)
//   5.0-5.45 the bar folds into the small pill, 5.5-6.2 springs back into the disc
//   6.2-7.0  rest, identical to t = 0
// Tapping the glass opens or closes it (light haptic); 2 s after it opened by touch it folds
// back and the loop goes on.

/** Seconds. */
export const LOOP = 7;
const DISC = 132;
const SMALL = { w: 104, h: 52 };
const BAR = { w: 272, h: 92 };
const HOLD = 2;

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
function easeOutBack(x: number) {
  'worklet';
  const k = 1.5;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** Width, height, icon progress and press scale `t` s into the loop. */
function glassAt(t: number) {
  'worklet';
  let w = DISC;
  let h = DISC;
  if (t < 1.4) {
    const a = easeInOutCubic(seg(t, 1.0, 1.4));
    w = DISC + (SMALL.w - DISC) * a;
    h = DISC + (SMALL.h - DISC) * a;
  } else if (t < 5.0) {
    const b = easeOutBack(seg(t, 1.4, 2.1));
    w = SMALL.w + (BAR.w - SMALL.w) * b;
    h = SMALL.h + (BAR.h - SMALL.h) * b;
  } else if (t < 5.5) {
    const c = easeInOutCubic(seg(t, 5.0, 5.45));
    w = BAR.w + (SMALL.w - BAR.w) * c;
    h = BAR.h + (SMALL.h - BAR.h) * c;
  } else {
    const d = easeOutBack(seg(t, 5.5, 6.2));
    w = SMALL.w + (DISC - SMALL.w) * d;
    h = SMALL.h + (DISC - SMALL.h) * d;
  }
  const icons = easeOutCubic(seg(t, 1.7, 2.3)) * (1 - Math.pow(seg(t, 4.8, 5.15), 3));
  const press = 1 - 0.05 * Math.sin(Math.PI * seg(t, 3.3, 3.7));
  return { w, h, icons, press };
}

type Props = {
  brand?: string;
  title?: string;
  /** Opens and closes by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function GlassSplitButton({ brand = 'Drift', title = 'Capture\nanything', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.5;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  // The loop time the glass is drawn at; touch drives it through the same curves.
  const shown = useSharedValue(reduced ? 3 : 0);
  const manual = useSharedValue(false);
  const tapped = useSharedValue(-10);
  const opening = useSharedValue(true);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    clock.set(t);
    if (manual.get()) {
      const since = now - tapped.get();
      if (opening.get()) {
        shown.set(Math.min(3.0, 1.0 + since));
        if (since > 2.0 + HOLD) {
          manual.set(false);
          origin.set(now - 3.0);
        }
      } else {
        shown.set(Math.min(6.2, 4.8 + since));
        if (since > 1.4 + HOLD) {
          manual.set(false);
          origin.set(now - 6.2);
        }
      }
      return;
    }
    shown.set(autoPlay ? t : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const glass = useDerivedValue(() => glassAt(shown.get()));
  const rect = useDerivedValue(() => {
    const g = glass.get();
    const w = g.w * g.press;
    const h = g.h * g.press;
    return Skia.RRectXY(Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h), h / 2, h / 2);
  });
  const rx = useDerivedValue(() => rect.get().rect.x);
  const ry = useDerivedValue(() => rect.get().rect.y);
  const rw = useDerivedValue(() => rect.get().rect.width);
  const rh = useDerivedValue(() => rect.get().rect.height);
  const rr = useDerivedValue(() => rect.get().rect.height / 2);

  // Icons: two rings and a plus, spaced along the bar, trimmed in by `icons`.
  const ringL = useDerivedValue(() => {
    const g = glass.get();
    const p = Skia.Path.Make();
    p.addCircle(cx - g.w * 0.31, cy, 16);
    return p;
  });
  const ringR = useDerivedValue(() => {
    const g = glass.get();
    const p = Skia.Path.Make();
    p.addCircle(cx + g.w * 0.31, cy, 16);
    return p;
  });
  const plus = useDerivedValue(() => {
    const p = Skia.Path.Make();
    p.moveTo(cx, cy - 15);
    p.lineTo(cx, cy + 15);
    p.moveTo(cx - 15, cy);
    p.lineTo(cx + 15, cy);
    return p;
  });
  const iconEnd = useDerivedValue(() => glass.get().icons);
  const plusOpacity = useDerivedValue(() => 0.28 + 0.72 * glass.get().icons);

  // Colour drifting behind the glass, periodic in the loop.
  const blobA = useDerivedValue(() => cx - 90 + 40 * Math.sin((clock.get() / LOOP) * Math.PI * 2));
  const blobB = useDerivedValue(() => cx + 100 + 36 * Math.cos((clock.get() / LOOP) * Math.PI * 2));
  const blobC = useDerivedValue(() => cy - 110 + 30 * Math.sin((clock.get() / LOOP) * Math.PI * 4));

  const labelsStyle = useAnimatedStyle(() => {
    const k = glass.get().icons;
    return { opacity: k, transform: [{ translateY: 8 * (1 - k) }] };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      const open = glass.get().w > DISC + 20;
      opening.set(!open);
      manual.set(true);
      tapped.set(elapsed.get());
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Fill>
          <LinearGradient start={vec(0, 0)} end={vec(width * 0.4, height)} colors={['#E9ECF1', '#B7BFCC', '#7D879A']} />
        </Fill>
        <Group opacity={0.9}>
          <BlurMask blur={34} style="normal" />
          <Circle cx={blobA} cy={cy - 40} r={84} color="#FFA97A" />
          <Circle cx={blobB} cy={cy + 10} r={78} color="#9D8CFF" />
          <Circle cx={cx} cy={blobC} r={62} color="#6FD8C8" />
        </Group>
        <RoundedRect x={rx} y={ry} width={rw} height={rh} r={rr} color="rgba(255,255,255,0.01)">
          <Shadow dx={0} dy={18} blur={24} color="rgba(40,48,70,0.28)" shadowOnly />
        </RoundedRect>
        <BackdropBlur blur={18} clip={rect}>
          <Fill color="rgba(236,240,246,0.42)" />
        </BackdropBlur>
        <RoundedRect x={rx} y={ry} width={rw} height={rh} r={rr} style="stroke" strokeWidth={1.5}>
          <LinearGradient start={vec(0, cy - 66)} end={vec(0, cy + 66)} colors={['rgba(255,255,255,0.95)', 'rgba(255,255,255,0.25)', 'rgba(255,210,170,0.9)']} />
        </RoundedRect>
        <Path path={ringL} style="stroke" strokeWidth={3.5} strokeCap="round" color="#15171C" start={0} end={iconEnd} />
        <Path path={ringR} style="stroke" strokeWidth={3.5} strokeCap="round" color="#15171C" start={0} end={iconEnd} />
        <Path path={plus} style="stroke" strokeWidth={3.5} strokeCap="round" color="#15171C" opacity={plusOpacity} />
      </Canvas>

      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.meta}>12 notes today</Text>
        </View>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.sub}>A photo, a thought or a voice memo — one tap away.</Text>
      </View>

      <GestureDetector gesture={tap}>
        <View style={[styles.hit, { left: cx - BAR.w / 2, top: cy - DISC / 2, width: BAR.w, height: DISC }]} accessible accessibilityRole="button" accessibilityLabel="New capture" />
      </GestureDetector>

      <Animated.View style={[styles.labels, { top: cy + BAR.h / 2 + 22, left: cx - BAR.w / 2, width: BAR.w }, labelsStyle]} pointerEvents="none">
        <Text style={styles.label}>Photo</Text>
        <Text style={styles.label}>Note</Text>
        <Text style={styles.label}>Voice</Text>
      </Animated.View>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
        <Text style={styles.foot}>Last capture · 9:12 · “Pick up film rolls”</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#B7BFCC' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 18, fontWeight: '800', color: '#15171C' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(21,23,28,0.55)' },
  title: { marginTop: 16, fontSize: 48, lineHeight: 50, fontWeight: '900', color: '#15171C', letterSpacing: -1.8 },
  sub: { marginTop: 12, fontSize: 17, lineHeight: 23, color: 'rgba(21,23,28,0.65)', maxWidth: 290 },
  hit: { position: 'absolute' },
  labels: { position: 'absolute', flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 22 },
  label: { width: 64, textAlign: 'center', fontSize: 14, fontWeight: '700', color: '#15171C' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  foot: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.85)' },
});
