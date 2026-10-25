import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawCup, makePearlSprite } from './bobaCup';
import {
  type Cup,
  LOOP,
  type Scene,
  easeInOutCubic,
  easeOutBack,
  loopScene,
  makeGeo,
  packPearls,
  percent,
  seg,
  shakeEnv,
  shakeRot,
  stepOf,
} from './bobaMotion';
import { type Fonts, drawBackdrop, drawPanel } from './bobaPanel';

/*
 Boba Pearl Progress — "Pearl Lane", an order brewing. A clear cup fills as the order
 progresses: pearls drop in and settle, milk tea pours, the cup is shaken, sealed and pierced by a
 straw; the percentage and a four-step tracker follow.

 Touch: drag the cup sideways to tip it (±12°, rubber band past it): the cup follows on a spring
 (ζ 0.75), the tea's surface stays level and sloshes behind (ζ 0.22), the pearls roll to the low
 side. "Shake it" rocks the cup for 1.4 s. Meanwhile the order keeps brewing. 2 s after the last
 touch the cup is swapped for an empty one (0.9 s) and the loop starts again. Reduced motion: the
 finished cup at 100 %, still; tipping follows the finger without slosh.
 */

type Props = { autoPlay?: boolean };

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
/** Tip per point of drag (radians) and the limit (12°). */
const TIP_GAIN = 0.0028;
const TIP_LIMIT = (12 * Math.PI) / 180;
const CUP_K = 260;
const CUP_C = 2 * 0.75 * Math.sqrt(CUP_K);
const SLOSH_K = 90;
const SLOSH_C = 2 * 0.22 * Math.sqrt(SLOSH_K);
const ROLL_K = 70;
const ROLL_C = 2 * 0.45 * Math.sqrt(ROLL_K);
const SWAP = 0.9;
const READY_TC = 8.2;

function haptic(kind: 'tick' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function rubber(x: number, limit: number) {
  'worklet';
  const a = Math.abs(x);
  if (a <= limit) return x;
  const over = a - limit;
  return Math.sign(x) * (limit + (over * 0.55) / (1 + over / 0.25));
}

export function BobaPearlProgress({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const pearls = useMemo(() => (g ? packPearls(g) : []), [g]);
  const sprite = useMemo(() => makePearlSprite(), []);
  const fonts = useMemo<Fonts>(
    () => ({
      pct: matchFont({ fontFamily: SANS, fontSize: 40, fontWeight: '700' }),
      status: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '600' }),
      eta: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '500' }),
      step: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '600' }),
    }),
    [],
  );
  const backdrop = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawBackdrop(canvas, g);
    return recorder.finishRecordingAsPicture();
  }, [g, size]);

  const now = useSharedValue(-1);
  const start = useSharedValue(-1);
  const origin = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  // The hand-driven cup.
  const tc = useSharedValue(reduced ? READY_TC : 0);
  const rot = useSharedValue(0);
  const rotV = useSharedValue(0);
  const rotTarget = useSharedValue(0);
  const tilt = useSharedValue(0);
  const tiltV = useSharedValue(0);
  const roll = useSharedValue(0);
  const rollV = useSharedValue(0);
  const shakeAt = useSharedValue(-10);
  const swapAt = useSharedValue(-10);
  const swapTc = useSharedValue(0);
  const stepChange = useSharedValue<[number, number, number]>(reduced ? [3, 3, -10] : [0, 0, -10]);
  const pastLimit = useSharedValue(0);
  const pressed = useSharedValue(0);

  useFrameCallback((frame) => {
    const n = frame.timestamp / 1000;
    const prev = now.get();
    if (prev < 0) {
      origin.set(n);
      if (auto) start.set(n);
    }
    now.set(n);
    const dt = prev < 0 ? 0 : Math.min(n - prev, 1 / 30);
    const st = start.get();
    if (st >= 0 && n >= st) return;
    // The order keeps brewing under the finger; the cup, its tea and pearls are springs.
    tc.set(Math.min(READY_TC, tc.get() + dt));
    const shake = shakeRot(n - shakeAt.get());
    const total = rot.get() + shake;
    const a = CUP_K * (rotTarget.get() - rot.get()) - CUP_C * rotV.get();
    rotV.set(rotV.get() + a * dt);
    rot.set(rot.get() + rotV.get() * dt);
    const ts = SLOSH_K * (-total - tilt.get()) - SLOSH_C * tiltV.get();
    tiltV.set(tiltV.get() + ts * dt);
    tilt.set(tilt.get() + tiltV.get() * dt);
    const rs = ROLL_K * (total * 70 - roll.get()) - ROLL_C * rollV.get();
    rollV.set(rollV.get() + rs * dt);
    roll.set(roll.get() + rollV.get() * dt);
    const step = stepOf(tc.get());
    const sc = stepChange.get();
    if (step !== sc[0]) stepChange.set([step, sc[0], n]);
    const r = resumeAt.get();
    if (r >= 0 && n >= r) {
      resumeAt.set(-1);
      rotTarget.set(0);
      if (tc.get() > 1.4) {
        swapAt.set(n);
        swapTc.set(tc.get());
        start.set(n + SWAP + 0.05);
      } else {
        start.set(n + 0.6);
      }
    }
  }, !reduced);

  function takeOver() {
    'worklet';
    const n = now.get();
    const st = start.get();
    if (st >= 0 && n >= st && g) {
      const t = (n - st) % LOOP;
      const sc = loopScene(g, t, t);
      const c = sc.cups[0];
      tc.set(t >= 8.9 ? 0 : Math.min(t, READY_TC));
      rot.set(c ? c.rot : 0);
      tilt.set(c ? c.tilt : 0);
      rotV.set(0);
      tiltV.set(0);
      roll.set(0);
      rollV.set(0);
      shakeAt.set(t > 5.0 && t < 6.4 ? n - (t - 5.0) : -10);
      stepChange.set([stepOf(tc.get()), stepOf(tc.get()), -10]);
    }
    swapAt.set(-10);
    start.set(-1);
    resumeAt.set(-1);
  }

  const scene = useDerivedValue<Scene | null>(() => {
    if (!g) return null;
    const n = now.get();
    const st = start.get();
    const ambient = n < 0 ? 0 : (((n - origin.get()) % LOOP) + LOOP) % LOOP;
    if (st >= 0 && n >= st) return loopScene(g, (n - st) % LOOP, ambient);
    const sh = n - shakeAt.get();
    const user: Cup = {
      dx: 0,
      rot: rot.get() + shakeRot(sh),
      tc: tc.get(),
      tilt: reduced ? 0 : tilt.get(),
      roll: reduced ? 0 : roll.get(),
      shake: shakeEnv(sh),
      phase: sh,
    };
    const sc = stepChange.get();
    const base: Scene = {
      cups: [user],
      percent: percent(user.tc),
      step: sc[0],
      prevStep: sc[1],
      stepFade: Math.min(1, (n - sc[2]) / 0.3),
      pct: [0, 0, 1],
      clock: ambient,
    };
    const sw = n - swapAt.get();
    if (sw >= 0 && sw <= SWAP + 0.1) {
      // The finished cup leaves, an empty one arrives.
      const s = sw / SWAP;
      user.dx = -g.w * 0.8 * easeInOutCubic(seg(s, 0, 0.55));
      user.tc = swapTc.get();
      const fresh: Cup = { dx: g.w * 0.8 * (1 - easeOutBack(seg(s, 0.35, 1))), rot: 0, tc: 0, tilt: 0, roll: 0, shake: 0, phase: 0 };
      base.cups = [user, fresh];
      base.percent = s > 0.5 ? 0 : percent(swapTc.get());
      base.pct = [Math.floor(percent(swapTc.get())), 0, easeInOutCubic(seg(s, 0.4, 0.6))];
      base.step = s > 0.45 ? 0 : stepOf(swapTc.get());
      base.prevStep = stepOf(swapTc.get());
      base.stepFade = s > 0.45 ? Math.min(1, (s - 0.45) / 0.33) : 1;
    }
    return base;
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const sc = scene.get();
    if (g && sc && sprite) {
      for (const cup of sc.cups) drawCup(canvas, g, cup, pearls, sprite, sc.clock);
      drawPanel(canvas, g, sc, fonts);
    }
    return recorder.finishRecordingAsPicture();
  });

  const tip = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      takeOver();
      pastLimit.set(0);
    })
    .onUpdate((e) => {
      const raw = e.translationX * TIP_GAIN;
      rotTarget.set(rubber(raw, TIP_LIMIT));
      if (reduced) rot.set(rotTarget.get());
      const over = Math.abs(raw) > TIP_LIMIT ? 1 : 0;
      if (over !== pastLimit.get()) {
        pastLimit.set(over);
        if (over === 1) scheduleOnRN(haptic, 'tick');
      }
    })
    .onFinalize(() => {
      rotTarget.set(0);
      if (reduced) rot.set(0);
      if (auto) resumeAt.set(now.get() + 2);
    });

  const shakeIt = () => {
    haptic('light');
    if (reduced) return;
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      shakeAt.set(now.get());
      if (auto) resumeAt.set(now.get() + 2);
    });
  };

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * pressed.get() }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && sprite ? (
        <>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={backdrop} />
            <Picture picture={picture} />
          </Canvas>

          <GestureDetector gesture={tip}>
            <View
              style={[styles.cupTouch, { top: g.top - 40, height: g.cupH + 70, left: g.cx - g.rimW - 40, width: g.rimW * 2 + 80 }]}
              accessible
              accessibilityLabel="Your drink. Drag sideways to tip the cup."
            />
          </GestureDetector>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 8 }]}>
            <Text style={styles.kicker}>PEARL LANE</Text>
            <Text style={styles.title}>Order #318</Text>
            <Text style={styles.subtitle}>Brown Sugar Milk Tea · Large · 50% sweet</Text>
          </View>

          <Pressable
            accessibilityRole="button"
            onPress={shakeIt}
            onPressIn={() => pressed.set(withTiming(1, { duration: 110 }))}
            onPressOut={() => pressed.set(withSpring(0, { duration: 350, dampingRatio: 0.7 }))}
            style={[styles.buttonWrap, { top: g.buttonY }]}
          >
            <Animated.View style={[styles.button, buttonStyle]}>
              <Text style={styles.buttonText}>Shake it</Text>
            </Animated.View>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4E2D0', overflow: 'hidden' },
  cupTouch: { position: 'absolute' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#A06A48', fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: '#3B2418', fontSize: 30, fontWeight: '700', letterSpacing: -0.5, fontVariant: ['tabular-nums'] },
  subtitle: { color: '#8C6E5C', fontSize: 15, fontWeight: '400' },
  buttonWrap: { position: 'absolute', left: 24, right: 24, height: 52 },
  button: {
    flex: 1,
    borderRadius: 26,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#3B2418',
    boxShadow: '0 1px 2px rgba(59,36,24,0.3), 0 10px 24px rgba(59,36,24,0.2), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  buttonText: { color: '#FBEBDD', fontSize: 17, fontWeight: '600' },
});
