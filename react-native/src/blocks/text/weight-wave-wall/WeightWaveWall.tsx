import { Canvas, type SkCanvas, Picture, Skia, createPicture, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type GestureResponderEvent, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { LEVELS, type Wall, layoutWall } from './face';

/*
 Weight Wave Wall: a wall of one word, every letter on its own weight of a variable font (Epilogue,
 100 hairline to 900 black), with bands of black letters rolling through it like surf.

 Timeline of the self-playing loop (LOOP = 7 s, two wave periods of 3.5 s):
   a letter's weight follows s = ½ + ½·sin(2π·(d·(u + 0.06·row) − t / 3.5) + 0.9·group), where u is the
   letter's place along its row (0 … 1, ten letters), rows go in groups of four whose waves run in
   opposite directions (d = ±1), and s is sharpened with smoothstep(0.18, 0.82). The weight is
   100 + 800·s, taken in 33 steps of 25; the letter's opacity is 0.42 + 0.58·s. Each letter advances by
   its own weight's width, so the black bands push their neighbours along the row. The frame at t = 0 is
   the resting state, and t = 7 s is the same frame.

 Touch: a finger drops a stone into the wall. The letters near it go black (a Gaussian of 80 pt) and
 rings run outward from it (wavelength 140 pt, one ring a second, fading over 220 pt); the touch's
 field fades in over 0.35 s (strong ease-out) and follows the finger. 2 s after the last touch the
 loop starts over from t = 0 while the touch's field fades out over 0.6 s (ease-in-out). Reduced
 motion: the wall holds the first frame and touch does nothing.
*/

/** The self-playing loop, in seconds. */
export const LOOP = 7;

const PERIOD = 3.5;
const GROUP_ROWS = 4;
const ROW_SLANT = 0.06;
const GROUP_PHASE = 0.9;
const TOUCH_RADIUS = 80;
const RING_LENGTH = 140;
const RING_PERIOD = 1;
const RING_FADE = 220;
const BLEND_IN = 0.35;
const BLEND_OUT = 0.6;
const IDLE = 2;
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

/** Strong ease-out (0.23, 1, 0.32, 1) and ease-in-out (0.42, 0, 0.58, 1). */
const CURVES = [
  [0.23, 1, 0.32, 1],
  [0.42, 0, 0.58, 1],
];

function bezier(c: number[], x: number) {
  'worklet';
  // Solve x(t) = x by bisection, then return y(t).
  let lo = 0;
  let hi = 1;
  let t = x;
  for (let i = 0; i < 24; i++) {
    t = (lo + hi) / 2;
    const u = 1 - t;
    const xt = 3 * u * u * t * c[0] + 3 * u * t * t * c[2] + t * t * t;
    if (xt < x) lo = t;
    else hi = t;
  }
  const u = 1 - t;
  return 3 * u * u * t * c[1] + 3 * u * t * t * c[3] + t * t * t;
}

type Tween = { from: number; to: number; start: number; duration: number; curve: number };

function tweenAt(tw: Tween, now: number) {
  'worklet';
  if (tw.duration <= 0) return tw.to;
  const t = Math.min(Math.max((now - tw.start) / tw.duration, 0), 1);
  return tw.from + (tw.to - tw.from) * bezier(CURVES[tw.curve], t);
}

function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

function drawWall(canvas: SkCanvas, wall: Wall, letters: number, height: number, t: number, k: number, fx: number, fy: number, since: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const perRow = letters * 2;
  const rows = Math.ceil(height / wall.pitch) + 1;
  const firstBaseline = (height - (rows - 1) * wall.pitch) / 2 + wall.xHeight / 2;
  for (let row = 0; row < rows; row++) {
    const group = Math.floor(row / GROUP_ROWS);
    const direction = group % 2 === 0 ? 1 : -1;
    const baseline = firstBaseline + row * wall.pitch;
    const middle = baseline - wall.xHeight / 2;
    for (let column = 0; column < 2; column++) {
      let x = wall.margin + column * (wall.column + wall.gap);
      for (let i = 0; i < letters; i++) {
        const u = (column * letters + i + 0.5) / perRow;
        let s = 0.5 + 0.5 * Math.sin(2 * Math.PI * (direction * (u + ROW_SLANT * row) - t / PERIOD) + GROUP_PHASE * group);
        if (k > 0) {
          const cx = wall.margin + column * (wall.column + wall.gap) + ((i + 0.5) * wall.column) / letters;
          const d = Math.hypot(cx - fx, middle - fy);
          const ring = (0.5 + 0.5 * Math.cos(2 * Math.PI * (d / RING_LENGTH - since / RING_PERIOD))) * Math.exp(-d / RING_FADE);
          const r = d / TOUCH_RADIUS;
          const touch = Math.max(Math.exp(-r * r), ring);
          s += (touch - s) * k;
        }
        const level = Math.round(smoothstep(0.18, 0.82, s) * (LEVELS - 1));
        paint.setColor(Float32Array.of(1, 1, 1, 0.42 + (0.58 * level) / (LEVELS - 1)));
        canvas.save();
        canvas.translate(x, baseline);
        canvas.drawPath(wall.paths[level][i], paint);
        canvas.restore();
        x += wall.advances[level][i];
      }
    }
  }
}

type Props = {
  word?: string;
  kicker?: string;
  title?: string;
  subtitle?: string;
  action?: string;
  /** Rolls the waves by itself; the pattern repeats every `LOOP` seconds. */
  autoPlay?: boolean;
  onAction?: () => void;
};

/**
 * A wall of one word on black, every letter on its own weight of a variable font, with bands of black
 * letters rolling through it like surf; a finger drops a stone and rings run out from it.
 */
export function WeightWaveWall({
  word = 'waves',
  kicker = 'Focus sounds — 04',
  title = 'Let it roll in.',
  subtitle = 'Twenty minutes of slow surf for deep work.',
  action = 'Play waves',
  autoPlay = false,
  onAction,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const thin = useFont(require('./assets/Epilogue-100.ttf'), 100);
  const regular = useFont(require('./assets/Epilogue-400.ttf'), 100);
  const black = useFont(require('./assets/Epilogue-900.ttf'), 100);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const wall = useMemo(
    () => (thin && regular && black && size.width > 0 ? layoutWall(thin, regular, black, word, size.width) : null),
    [thin, regular, black, word, size.width],
  );
  const letters = Array.from(word).length;

  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const blend = useSharedValue<Tween>({ from: 0, to: 0, start: 0, duration: 0, curve: 0 });
  const finger = useSharedValue({ x: 0, y: 0 });
  const touchStart = useSharedValue(0);
  const dragging = useSharedValue(false);
  const touchEnd = useSharedValue(-1);

  const haptic = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (loopStart.get() < 0) loopStart.set(now);
    // 2 s after the last touch the loop starts over while the touch's field fades out.
    if (touchEnd.get() >= 0 && !dragging.get() && now - touchEnd.get() >= IDLE) {
      touchEnd.set(-1);
      loopStart.set(now);
      blend.set({ from: tweenAt(blend.get(), now), to: 0, start: now, duration: BLEND_OUT, curve: 1 });
    }
  });

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const t = reduced ? 0 : (((now - loopStart.get()) % LOOP) + LOOP) % LOOP;
    const k = reduced ? 0 : tweenAt(blend.get(), now);
    const f = finger.get();
    const since = now - touchStart.get();
    return createPicture((canvas) => {
      if (!wall) return;
      drawWall(canvas, wall, letters, size.height, t, k, f.x, f.y, since);
    });
  });

  const begin = (x: number, y: number) => {
    'worklet';
    if (reduced) return;
    const now = clock.get();
    finger.set({ x, y });
    dragging.set(true);
    touchEnd.set(-1);
    touchStart.set(now);
    const k = tweenAt(blend.get(), now);
    blend.set({ from: k, to: 1, start: now, duration: BLEND_IN * (1 - k), curve: 0 });
    scheduleOnRN(haptic);
  };
  const end = () => {
    'worklet';
    if (!dragging.get()) return;
    dragging.set(false);
    touchEnd.set(clock.get());
  };

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => begin(e.absoluteX, e.absoluteY))
    .onUpdate((e) => {
      if (dragging.get()) finger.set({ x: e.absoluteX, y: e.absoluteY });
    })
    .onFinalize(() => end());

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>

      {/* The status bar and the copy each sit on a fade to black. */}
      <LinearGradient
        pointerEvents="none"
        colors={['#000000', 'rgba(0,0,0,0.75)', 'rgba(0,0,0,0)']}
        locations={[0, 0.5, 1]}
        style={[styles.top, { height: insets.top + 44 }]}
      />
      <LinearGradient
        pointerEvents="none"
        colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.92)', '#000000']}
        locations={[0, 0.4, 0.55]}
        style={[styles.bottom, { height: 440 + insets.bottom }]}
      />

      <View pointerEvents="box-none" style={[styles.copy, { paddingBottom: insets.bottom + (insets.bottom > 16 ? 8 : 16) }]}>
        <Text style={styles.kicker} numberOfLines={1}>
          {kicker.toUpperCase()}
        </Text>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>
          {title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={2}>
          {subtitle}
        </Text>
        <Pressable
          accessibilityRole="button"
          onPressIn={(e: GestureResponderEvent) => scheduleOnUI(begin, e.nativeEvent.pageX, e.nativeEvent.pageY)}
          onPressOut={() => scheduleOnUI(end)}
          onPress={onAction}
          style={styles.button}
        >
          <Text style={styles.action} numberOfLines={1}>
            {action}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  top: { position: 'absolute', top: 0, left: 0, right: 0 },
  bottom: { position: 'absolute', bottom: 0, left: 0, right: 0 },
  copy: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  kicker: { color: 'rgba(255,255,255,0.55)', fontFamily: MONO, fontSize: 12, fontWeight: '500', letterSpacing: 2 },
  title: { color: '#ffffff', fontSize: 34, fontWeight: '600', letterSpacing: -0.8, marginTop: 12 },
  subtitle: { color: 'rgba(255,255,255,0.6)', fontSize: 15, marginTop: 8 },
  button: { marginTop: 24, height: 56, borderRadius: 28, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  action: { color: '#000000', fontSize: 17, fontWeight: '600' },
});
