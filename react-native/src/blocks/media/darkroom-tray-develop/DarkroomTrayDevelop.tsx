import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { DEV_SECONDS, type Frame, LATENT, LOOP, easeOut, loopFrame, makeFrame, makeLayout, stampScale } from './darkroomMotion';
import { DARKROOM_SKSL } from './darkroomShader';

/*
 Darkroom Tray Develop — "Safelight", a film app's print step under a red safelight. A blank
 fibre print lies in the developer; rocking the tray sends a wave across it and the image comes
 up, shadows first, unevenly where the liquid front reaches it last. Then the stop bath, a FIXED
 stamp, the room light flickers on to show the print in white light, and back to red: the print
 slides out and a fresh sheet slides in.

 Drawing: one Canvas with one RuntimeEffect (darkroomShader.ts) that takes the photo as a child
 shader (one bundled 560 × 700 JPEG) and draws bench, tray, liquid, tongs and the two sheets.
 Uniforms come from one frame object written on the UI thread (darkroomMotion.ts). The header,
 timer, stamp and stage pills are views reading shared values; React does not re-render per frame.

 Touch: drag sideways to rock the tray: the tilt follows the finger (rubber-banded past a full
 tip), springs back on release, and the faster it rocks the faster the print develops. A fully
 developed print hands over to the loop's stop-fix-lights sequence; otherwise, 2 s after the
 finger lifts, the half-developed print slides out and a fresh sheet comes in (the loop's last
 phase) and the loop starts again. Reduced motion: no loop and no wave; the screen shows the
 finished print in white light, and dragging does nothing.
*/

type Props = {
  /** Plays the 9 s rock-develop-fix-lights-next-sheet loop. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const IDLE = 2;
/** Where the loop picks up after a touch: the stop bath for a finished print, the exchange otherwise. */
const RESUME_FIXED = 5.0;
const RESUME_EXCHANGE = 6.6;
const photoSource = require('./assets/print.jpg');
const effect = Skia.RuntimeEffect.Make(DARKROOM_SKSL);

const INK_RED = '#F2B9AD';
const INK_WHITE = '#F3F0EA';
const SOFT_RED = 'rgba(242,185,173,0.62)';
const SOFT_WHITE = 'rgba(243,240,234,0.66)';
const STAGES = [
  { name: 'Develop', time: '1:30' },
  { name: 'Stop', time: '0:30' },
  { name: 'Fix', time: '2:00' },
];

function feel(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

/** A finger's push past a full tip gives less and less (rubber band). */
function rubber(x: number) {
  'worklet';
  const a = Math.abs(x);
  if (a <= 1) return x;
  const over = a - 1;
  return Math.sign(x) * (1 + over / (1 + over * 2.2));
}

export function DarkroomTrayDevelop({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const L = useMemo(() => makeLayout(size.width, size.height, insets.top, insets.bottom), [size.width, size.height, insets.top, insets.bottom]);
  const photo = useImage(photoSource);

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const holding = useSharedValue(0);
  const frame = useSharedValue<Frame>(makeFrame());
  const leave = useSharedValue(1);
  // The hand-driven tray.
  const target = useSharedValue(0);
  const rock = useSharedValue(0);
  const rockV = useSharedValue(0);
  const dev = useSharedValue(0);
  const wave = useSharedValue(0);
  const wphase = useSharedValue(0);
  // What the loop adds back after a touch, fading out: [rock, wave, secs, at].
  const offset = useSharedValue<number[]>([0, 0, 0, -10]);
  const stagePos = useSharedValue(0);

  useFrameCallback((fc) => {
    const dt = Math.min((fc.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const f = makeFrame();
    if (reduced) {
      loopFrame(6.0, L.tray[2], 1, f);
      f.wave = 0;
      f.rock = 0;
    } else if (mode.get() === MODE_AUTO) {
      const before = clock.get();
      const t = auto ? (before + dt) % LOOP : 0;
      if (t < before) leave.set(1);
      clock.set(t);
      loopFrame(t, L.tray[2], leave.get(), f);
      const o = offset.get();
      const fade = Math.exp(-(at - o[3]) * 5);
      f.rock += o[0] * fade;
      f.wave += o[1] * fade;
      f.secs += o[2] * Math.exp(-(at - o[3]) * 3);
    } else {
      // Rocking by hand: a spring toward the finger's tilt, the wave from the speed.
      const k = 90;
      const c = 2 * 0.62 * Math.sqrt(k);
      let v = rockV.get() + (k * (target.get() - rock.get()) - c * rockV.get()) * dt;
      const r = rock.get() + v * dt;
      if (Math.abs(v) < 1e-4) v = 0;
      rockV.set(v);
      rock.set(r);
      const speed = Math.min(1, Math.abs(v) / 3);
      wave.set(wave.get() + (Math.min(1, 0.12 + speed) - wave.get()) * Math.min(1, dt * 4));
      wphase.set((wphase.get() + dt * ((2 * Math.PI * 7) / LOOP) * (1 + speed * 1.5)) % (Math.PI * 200));
      dev.set(Math.min(1, dev.get() + dt * (0.012 + 0.42 * speed)));
      loopFrame(1.2, L.tray[2], 1, f);
      f.rock = r;
      f.wave = wave.get();
      f.wphase = wphase.get();
      f.dev = dev.get();
      f.secs = dev.get() * DEV_SECONDS;
      const done = dev.get() >= 1;
      if (done || (!holding.get() && at - lastTouch.get() > IDLE)) {
        // Hand back to the loop where it fits, carrying the tilt, the wave and the timer.
        const t0 = done ? RESUME_FIXED : RESUME_EXCHANGE;
        const g = makeFrame();
        loopFrame(t0, L.tray[2], dev.get(), g);
        offset.set([r - g.rock, wave.get() - g.wave, f.secs - g.secs, at]);
        leave.set(dev.get());
        clock.set(t0);
        mode.set(MODE_AUTO);
        if (done) scheduleOnRN(feel, 1);
      }
    }
    frame.set(f);
    stagePos.set(stagePos.get() + (f.stage - stagePos.get()) * Math.min(1, dt * 10));
  });

  function takeOver() {
    'worklet';
    if (mode.get() !== MODE_AUTO) return;
    const f = frame.get();
    const t = clock.get();
    // During the exchange the fresh sheet takes the touch, with its latent image.
    rock.set(f.rock);
    rockV.set(0);
    wave.set(f.wave);
    wphase.set(f.wphase);
    dev.set(t >= RESUME_EXCHANGE ? Math.max(LATENT, f.devB) : Math.min(0.99, f.dev));
    mode.set(MODE_USER);
  }

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      holding.set(1);
      lastTouch.set(now.get());
    })
    .onStart(() => {
      takeOver();
      scheduleOnRN(feel, 0);
    })
    .onUpdate((e) => {
      if (mode.get() !== MODE_USER) return;
      target.set(rubber(e.translationX / 110));
      lastTouch.set(now.get());
    })
    .onFinalize(() => {
      holding.set(0);
      target.set(0);
      lastTouch.set(now.get());
    });

  const uniforms = useDerivedValue(() => {
    const f = frame.get();
    return {
      imgSize: [560, 700],
      size: [size.width, size.height],
      tray: L.tray,
      sheet: L.sheet,
      devA: f.dev,
      devB: f.devB,
      slide: [f.slideA, f.slideB],
      rock: [f.rock, f.wave],
      wphase: f.wphase,
      light: f.light,
      stopBath: f.stopBath,
    };
  });
  const light = useDerivedValue(() => frame.get().light);
  const secs = useDerivedValue(() => {
    const s = Math.max(0, frame.get().secs);
    const whole = Math.floor(s);
    return whole + easeOut((s - whole) / 0.3);
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  const ink = useAnimatedStyle(() => ({ color: interpolateColor(light.get(), [0, 1], [INK_RED, INK_WHITE]) }));
  const soft = useAnimatedStyle(() => ({ color: interpolateColor(light.get(), [0, 1], [SOFT_RED, SOFT_WHITE]) }));
  const stamp = useAnimatedStyle(() => {
    const f = frame.get();
    const show = f.stamp > 0 ? Math.min(1, f.stamp * 3) * (1 - f.stampOut) : 0;
    return { opacity: show, transform: [{ translateY: -8 * f.stampOut }, { rotate: '-9deg' }, { scale: stampScale(f.stamp) }] };
  });
  const sheetLeft = L.sheet[0] - L.sheet[2] / 2;
  const sheetTop = L.sheet[1] - L.sheet[3] / 2;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A developing tray with a print. Drag sideways to rock the tray and develop the print.">
          {photo && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms}>
                  <ImageShader image={photo} fit="fill" rect={{ x: 0, y: 0, width: 560, height: 700 }} />
                </Shader>
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <View style={styles.headLeft}>
          <Animated.Text style={[styles.kicker, ink]}>SAFELIGHT</Animated.Text>
          <Animated.Text style={[styles.title, ink]}>Print 3 of 12</Animated.Text>
          <Animated.Text style={[styles.sub, soft]}>Fibre paper · 8 × 10 in · 20 °C</Animated.Text>
        </View>
        <View style={styles.timer}>
          <Animated.Text style={[styles.timerLabel, soft]}>DEV</Animated.Text>
          <Timer secs={secs} light={light} />
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.stamp, { left: sheetLeft + 14, top: sheetTop + 22 }, stamp]}>
        <View style={styles.stampInner}>
          <Text style={styles.stampText}>FIXED</Text>
          <Text style={styles.stampSub}>24 OCT · 2:00</Text>
        </View>
      </Animated.View>

      <View pointerEvents="none" style={[styles.panel, { bottom: insets.bottom + 16 }]}>
        <View style={styles.stages}>
          {STAGES.map((s, i) => (
            <StagePill key={s.name} i={i} name={s.name} time={s.time} pos={stagePos} light={light} />
          ))}
        </View>
        <Animated.Text style={[styles.hint, soft]}>Drag sideways to rock the tray</Animated.Text>
      </View>
    </View>
  );
}

const DIGIT_H = 40;
const TEN = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const SIX = ['0', '1', '2', '3', '4', '5', '0'];

/** A wheel of `digits` for the minutes (0), the tens of seconds (1) or the seconds (2). */
function Wheel({ digits, secs, light, which }: { digits: string[]; secs: SharedValue<number>; light: SharedValue<number>; which: 0 | 1 | 2 }) {
  const n = digits.length - 1;
  const style = useAnimatedStyle(() => {
    const s = secs.get();
    let pos = 0;
    if (which === 2) pos = s % 10;
    else if (which === 1) pos = Math.floor((s % 60) / 10) + Math.max(0, (s % 10) - 9);
    else pos = Math.floor(s / 60) + Math.max(0, (s % 60) - 59);
    return { transform: [{ translateY: -(pos % n) * DIGIT_H }] };
  });
  const color = useAnimatedStyle(() => ({ color: interpolateColor(light.get(), [0, 1], [INK_RED, INK_WHITE]) }));
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {digits.map((d, i) => (
          <Animated.Text key={i} style={[styles.digit, color]}>{d}</Animated.Text>
        ))}
      </Animated.View>
    </View>
  );
}

function Timer({ secs, light }: { secs: SharedValue<number>; light: SharedValue<number> }) {
  const color = useAnimatedStyle(() => ({ color: interpolateColor(light.get(), [0, 1], [INK_RED, INK_WHITE]) }));
  return (
    <View style={styles.timerRow}>
      <Wheel digits={TEN} secs={secs} light={light} which={0} />
      <Animated.Text style={[styles.colon, color]}>:</Animated.Text>
      <Wheel digits={SIX} secs={secs} light={light} which={1} />
      <Wheel digits={TEN} secs={secs} light={light} which={2} />
    </View>
  );
}

function StagePill({ i, name, time, pos, light }: { i: number; name: string; time: string; pos: SharedValue<number>; light: SharedValue<number> }) {
  const box = useAnimatedStyle(() => {
    const on = Math.max(0, 1 - Math.abs(pos.get() - i));
    const base = interpolateColor(light.get(), [0, 1], ['rgba(242,120,100,0.08)', 'rgba(243,240,234,0.08)']);
    const lit = interpolateColor(light.get(), [0, 1], ['rgba(242,120,100,0.3)', 'rgba(243,240,234,0.22)']);
    return { backgroundColor: interpolateColor(on, [0, 1], [base, lit]), transform: [{ scale: 1 + 0.03 * on }] };
  });
  const text = useAnimatedStyle(() => {
    const on = Math.max(0, 1 - Math.abs(pos.get() - i));
    return { opacity: 0.55 + 0.45 * on, color: interpolateColor(light.get(), [0, 1], [INK_RED, INK_WHITE]) };
  });
  return (
    <Animated.View style={[styles.pill, box]}>
      <Animated.Text style={[styles.pillName, text]}>{name}</Animated.Text>
      <Animated.Text style={[styles.pillTime, text]}>{time}</Animated.Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#0E0606' },
  header: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  headLeft: { gap: 4, flexShrink: 1 },
  kicker: { fontSize: 11, fontWeight: '800', letterSpacing: 2.2 },
  title: { fontSize: 30, fontWeight: '800', letterSpacing: -0.6 },
  sub: { fontSize: 14, fontWeight: '500' },
  timer: { alignItems: 'flex-end', gap: 2, paddingTop: 2 },
  timerLabel: { fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  timerRow: { flexDirection: 'row', alignItems: 'center', height: DIGIT_H },
  wheel: { height: DIGIT_H, width: 21, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, fontSize: 34, fontWeight: '300', fontVariant: ['tabular-nums'], textAlign: 'center' },
  colon: { fontSize: 32, fontWeight: '300', lineHeight: DIGIT_H, marginHorizontal: 1 },
  stamp: { position: 'absolute', padding: 3, borderRadius: 10, borderWidth: 2, borderColor: 'rgba(250,236,222,0.85)' },
  stampInner: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 7, borderWidth: 1, borderColor: 'rgba(250,236,222,0.7)', alignItems: 'center' },
  stampText: { color: 'rgba(250,236,222,0.92)', fontSize: 22, fontWeight: '900', letterSpacing: 5 },
  stampSub: { color: 'rgba(250,236,222,0.8)', fontSize: 9, fontWeight: '700', letterSpacing: 1.6, fontVariant: ['tabular-nums'] },
  panel: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 128,
    padding: 14,
    gap: 14,
    justifyContent: 'center',
    borderRadius: 26,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(24,14,14,0.88)',
    borderWidth: 1,
    borderColor: 'rgba(255,220,210,0.07)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 18px 40px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,230,220,0.06)',
  },
  stages: { flexDirection: 'row', gap: 8 },
  pill: { flex: 1, height: 58, borderRadius: 18, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center', gap: 2 },
  pillName: { fontSize: 15, fontWeight: '700' },
  pillTime: { fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  hint: { fontSize: 12, fontWeight: '600', textAlign: 'center' },
});
