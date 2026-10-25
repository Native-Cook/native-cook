import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
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
import Svg, { Path, Rect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  backFrame,
  beginBack,
  BLOW_AT,
  blowOut,
  type Candle,
  dripLength,
  easeInOut,
  IDLE_BEFORE_BACK,
  liveFrame,
  LOOP,
  makeCandle,
  MODE_AUTO,
  MODE_BACK,
  MODE_LIVE,
  relight,
  resetCandle,
  scriptFrame,
  SESSION,
  smokeAt,
  STUB,
} from './candleMotion';
import { CANDLE_SKSL } from './candleShader';

/*
 Candle Burn Timer — "Ember", a focus timer whose progress is a pillar candle burning down.

 Look: a dark plaster wall and a walnut table lit warm by the flame, an ivory pillar candle on a
 brass dish with a molten pool, a wick, wax drips, a flickering teardrop flame (blue base, white
 core, amber body) with a glow, and smoke when it goes out, all in one runtime shader
 (candleShader.ts). The wax height is the time left. Under it the time rolls like an odometer
 (25:00), a status line, and Pause / Reset.

 autoPlay (LOOP = 9 s, compressed time, candleMotion.ts): 0–1.4 rest (25:00, full candle, lit),
 1.4–6.0 burn 25:00 → 00:00 at a constant rate (the wax shrinks to a 24 % stub; drips run down at
 2.4 and 4.2 over 1.2 s), 6.0–6.35 the flame goes out and smoke curls up from 6.05, 6.6–7.4
 "Session done", 7.4–8.4 a fresh candle rises (ease in-out) as the time rolls back to 25:00,
 8.4 it catches (overshoot, settled by 9.0).

 Touch: drag sideways for wind: the flame leans with the drag's speed on a wobbly spring (closer
 to the candle = stronger); a flick faster than 1500 pt/s blows it out (smoke, timer paused, light
 haptic); tap the candle to relight. Pause / Resume and Reset (the wax rises back over 0.9 s).
 The timer runs in real time. 2 s after the last touch the candle resets and relights (0.9 s)
 and the loop starts again from t = 0. Reduced motion: no loop, a still full candle at 25:00.
 */

type Props = {
  /** Plays the 9 s compressed burn loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(CANDLE_SKSL);
const INK = '#f4e9dc';
const MUTE = 'rgba(244,233,220,0.6)';
const FAINT = 'rgba(244,233,220,0.36)';
const EMBER = '#f0a25a';
const CANDLE_W = 108;
const DIGIT_H = 76;
const STATUSES = ['Focus · ends at 10:25', 'Paused', 'Blown out · tap the wick to relight', 'Session done · take five'];
/** Odometer columns of mm:ss: place (s) and base. */
const COLUMNS = [
  { place: 600, base: 10 },
  { place: 60, base: 10 },
  { place: 10, base: 6 },
  { place: 1, base: 10 },
];

/** One rolling digit column, driven by the seconds left on the UI thread. */
function DigitColumn({ seconds, place, base }: { seconds: SharedValue<number>; place: number; base: number }) {
  const style = useAnimatedStyle(() => {
    const s = Math.max(seconds.get(), 0);
    const whole = Math.floor(s / place);
    const digit = whole % base;
    // It rolls during the last second before its value changes (the units roll every second).
    const r = s - whole * place;
    const roll = place === 1 ? r : Math.max(0, r - (place - 1));
    return { transform: [{ translateY: -(digit + easeInOut(roll)) * DIGIT_H }] };
  });
  const rows = [];
  for (let i = 0; i <= base; i += 1) rows.push(i % base);
  return (
    <View style={styles.digitWindow}>
      <Animated.View style={style}>
        {rows.map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

export function CandleBurnTimer({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;
  const cx = W / 2;
  const baseY = Math.round(H * 0.62);
  const full = Math.max(80, Math.min(250, baseY - insets.top - 236));

  const state = useSharedValue<Candle>(makeCandle(autoPlay));
  const tick = useSharedValue(0);
  const seconds = useSharedValue(SESSION);

  useEffect(() => {
    state.set(makeCandle(autoPlay));
  }, [autoPlay, state]);

  const buzz = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((frame) => {
    const c = state.get();
    if (H <= 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    c.now += dt;
    if (c.mode === MODE_AUTO) {
      if (auto) c.t = (c.t + dt) % LOOP;
      scriptFrame(c, c.t);
      if (!auto) {
        c.flick = 0;
        c.bend = 0;
      }
    } else if (c.mode === MODE_LIVE) {
      liveFrame(c, dt);
      if (auto && c.now - c.lastTouch > IDLE_BEFORE_BACK) beginBack(c);
    } else if (c.mode === MODE_BACK) {
      if (backFrame(c, dt)) {
        c.mode = MODE_AUTO;
        c.t = 0;
      }
    }
    if (c.buzz) {
      c.buzz = false;
      scheduleOnRN(buzz);
    }
    seconds.set(c.remain * SESSION);
    tick.set(tick.get() + 1);
  });

  function touched() {
    'worklet';
    const c = state.get();
    c.lastTouch = c.now;
    if (c.mode !== MODE_LIVE) c.mode = MODE_LIVE;
  }

  function togglePause() {
    'worklet';
    touched();
    const c = state.get();
    c.running = !c.running;
  }

  function reset() {
    'worklet';
    touched();
    resetCandle(state.get());
  }

  const wind = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      touched();
    })
    .onUpdate((e) => {
      const c = state.get();
      c.lastTouch = c.now;
      // Closer to the flame, stronger wind.
      const near = Math.exp(-Math.max(0, Math.abs(e.x - cx) - 60) / 140);
      c.wind = e.velocityX * near;
      if (Math.abs(c.wind) > BLOW_AT && c.lit > 0.5 && blowOut(c)) c.buzz = true;
    })
    .onEnd((e) => {
      const c = state.get();
      c.lastTouch = c.now;
      const tap = Math.hypot(e.translationX, e.translationY) < 10;
      const topY = baseY - full * (STUB + (1 - STUB) * c.remain);
      const onCandle = Math.abs(e.x - cx) < CANDLE_W * 0.9 && e.y > topY - 110 && e.y < baseY + 20;
      if (tap && onCandle && relight(c)) c.buzz = true;
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const c = state.get();
    const height = full * (STUB + (1 - STUB) * c.remain);
    const drips: number[] = [];
    for (let i = 0; i < 2; i += 1) {
      // A drip hangs from the rim and moves down with it as the wax burns.
      drips.push(c.dx[i] * CANDLE_W * 0.5, baseY - height, dripLength(c, i), c.db[i] > -50 ? c.dripAlpha : 0);
    }
    const [smokeAge, smokeStrength] = smokeAt(c);
    return {
      size: [Math.max(W, 1), Math.max(H, 1)],
      base: [cx, baseY],
      candle: [CANDLE_W, height],
      flame: [c.lit, c.bend, c.flick, Math.max(c.lit, 0.25 * smokeStrength)],
      turb: [Math.cos(c.phase), Math.sin(c.phase), Math.cos(c.phase * 1.7), Math.sin(c.phase * 1.7)],
      smoke: [smokeAge, smokeStrength],
      drips,
    };
  });

  const statusStyle = (i: number) => {
    'worklet';
    tick.get();
    const w = state.get().statusW[i];
    return { opacity: w, transform: [{ translateY: (1 - w) * 4 }] };
  };
  const s0 = useAnimatedStyle(() => statusStyle(0));
  const s1 = useAnimatedStyle(() => statusStyle(1));
  const s2 = useAnimatedStyle(() => statusStyle(2));
  const s3 = useAnimatedStyle(() => statusStyle(3));
  const statusStyles = [s0, s1, s2, s3];
  const pauseLabel = useAnimatedStyle(() => {
    tick.get();
    return { opacity: state.get().running ? 1 : 0 };
  });
  const resumeLabel = useAnimatedStyle(() => {
    tick.get();
    return { opacity: state.get().running ? 0 : 1 };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={wind}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A burning candle that shows the time left. Drag sideways to blow on the flame; tap the candle to relight it."
        >
          {size && EFFECT ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
        <Text style={styles.kicker}>EMBER</Text>
        <Text style={styles.title}>Deep work</Text>
        <Text style={styles.subtitle}>Draft the launch notes · session 3 of 4</Text>
      </View>

      {size ? (
        <View pointerEvents="none" style={[styles.timer, { top: baseY + 40 }]}>
          <View style={styles.digits} accessible accessibilityLabel="Time left">
            <DigitColumn seconds={seconds} place={COLUMNS[0].place} base={COLUMNS[0].base} />
            <DigitColumn seconds={seconds} place={COLUMNS[1].place} base={COLUMNS[1].base} />
            <Text style={styles.colon}>:</Text>
            <DigitColumn seconds={seconds} place={COLUMNS[2].place} base={COLUMNS[2].base} />
            <DigitColumn seconds={seconds} place={COLUMNS[3].place} base={COLUMNS[3].base} />
          </View>
          <View style={styles.statusBox}>
            {STATUSES.map((label, i) => (
              <Animated.Text key={label} style={[styles.status, statusStyles[i]]}>
                {label}
              </Animated.Text>
            ))}
          </View>
        </View>
      ) : null}

      <View style={[styles.controls, { bottom: insets.bottom + 24 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Pause or resume" onPress={() => scheduleOnUI(togglePause)} style={styles.button}>
          <View style={styles.buttonInner}>
            <Animated.View style={[styles.buttonLabel, pauseLabel]}>
              <Svg width={12} height={14} viewBox="0 0 12 14">
                <Rect x={1} y={1} width={3.4} height={12} rx={1.2} fill={INK} />
                <Rect x={7.6} y={1} width={3.4} height={12} rx={1.2} fill={INK} />
              </Svg>
              <Text style={styles.buttonText}>Pause</Text>
            </Animated.View>
            <Animated.View style={[styles.buttonLabel, styles.overlay, resumeLabel]}>
              <Svg width={12} height={14} viewBox="0 0 12 14">
                <Path d="M2 1.5 L11 7 L2 12.5 Z" fill={INK} />
              </Svg>
              <Text style={styles.buttonText}>Resume</Text>
            </Animated.View>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Reset the session" onPress={() => scheduleOnUI(reset)} style={[styles.button, styles.primary]}>
          <Text style={styles.primaryText}>Reset</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#140e0b', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { color: EMBER, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  timer: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 6 },
  digits: { flexDirection: 'row', alignItems: 'center' },
  digitWindow: { height: DIGIT_H, width: 42, overflow: 'hidden' },
  digit: {
    height: DIGIT_H,
    lineHeight: DIGIT_H,
    textAlign: 'center',
    color: INK,
    fontSize: 64,
    fontWeight: '200',
    fontVariant: ['tabular-nums'],
  },
  colon: { color: INK, fontSize: 56, fontWeight: '200', marginTop: -8, marginHorizontal: 2 },
  statusBox: { height: 20, alignSelf: 'stretch' },
  status: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: FAINT,
    fontSize: 14,
    fontWeight: '500',
  },
  controls: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', gap: 12 },
  button: {
    flex: 1,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(244,233,220,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(244,233,220,0.16)',
  },
  buttonInner: { height: 24, width: 120, alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  overlay: { position: 'absolute' },
  buttonText: { color: INK, fontSize: 16, fontWeight: '600' },
  primary: {
    backgroundColor: '#f1dcc0',
    borderColor: '#f1dcc0',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.6), 0 1px 2px rgba(0,0,0,0.4), 0 10px 24px rgba(240,162,90,0.18)',
  },
  primaryText: { color: '#2a1a10', fontSize: 16, fontWeight: '700' },
});
