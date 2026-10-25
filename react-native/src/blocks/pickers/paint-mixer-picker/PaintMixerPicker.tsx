import { Canvas, Picture, Rect, RoundedRect, Shader, Skia, matchFont } from '@shopify/react-native-skia';
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
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, type Label, cardRect, drawDynamic, drawRoom, drawTable } from './paintMixerArt';
import {
  BASE_HEX,
  K,
  LOOP,
  type Mix,
  autoMix,
  makeGeo,
  mixColor,
  nearestName,
  shownColor,
} from './paintMixerMotion';
import { PAINT_SKSL } from './paintMixerShader';
import {
  EVENT_GRAB,
  EVENT_LAND,
  type Sim,
  createSim,
  simBusy,
  simDown,
  simFromAuto,
  simMix,
  simMove,
  simStep,
  simUp,
  simWash,
} from './paintMixerSim';

/*
 Paint Mixer Picker — "Hue Room": pick a wall colour by mixing paint. A glazed bowl seen from
 above, five tins around it; drops swirl into the paint and stirring blends them; the colour's
 name and code update and the room card behind takes the colour.

 Touch: drag from a tin to lift a drop (it hangs above the fingertip) and let go over the paint to
 drop it in (light haptic on landing); let go elsewhere and it flies back. Drag inside the bowl to
 stir: the swirl follows the finger's angular speed and mixing follows how far it has turned.
 2 s after the last touch the bowl washes back to white (0.85 s) and the loop restarts. Reduced
 motion: no loop, no swirl; drops land at once and the colour fades in evenly.
 */

type Props = { autoPlay?: boolean };

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const P3 = Platform.OS === 'ios' ? 1 : 0;
const WASH_HOLD = 0.9;
/** The name/code roll: 260 ms per line, the new one 60 ms behind. */
const LABEL_ROLL = 0.32;
const BASE_RGB = [parseInt(BASE_HEX.slice(1, 3), 16) / 255, parseInt(BASE_HEX.slice(3, 5), 16) / 255, parseInt(BASE_HEX.slice(5, 7), 16) / 255];

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind & EVENT_LAND) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else if (kind & EVENT_GRAB) Haptics.selectionAsync().catch(() => {});
}

function applied() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

export function PaintMixerPicker({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const effect = useMemo(() => Skia.RuntimeEffect.Make(PAINT_SKSL), []);
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts>(
    () => ({
      label: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '600' }),
      name: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '700' }),
      code: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '500' }),
      chip: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '600' }),
    }),
    [],
  );
  const backdrop = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawTable(canvas, g);
    return recorder.finishRecordingAsPicture();
  }, [g, size]);
  const room = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawRoom(canvas, g, fonts.chip);
    return recorder.finishRecordingAsPicture();
  }, [g, size, fonts]);

  const now = useSharedValue(-1);
  const start = useSharedValue(-1);
  const resumeAt = useSharedValue(-1);
  const sim = useSharedValue<Sim | null>(null);
  const frameMix = useSharedValue<Mix | null>(null);
  const shown = useSharedValue<number[]>(BASE_RGB);
  const label = useSharedValue<Label>({ cur: 0, prev: 0, since: 1, prevRgb: BASE_RGB });
  const labelAt = useSharedValue(-10);
  const pressed = useSharedValue(0);

  /** The script's label at t: the current name and, within the roll after a change, the one before. */
  function autoLabel(t: number): Label {
    'worklet';
    const cur = nearestName(shownColor(g!, t));
    for (let k = 1; k <= 7; k += 1) {
      const back = (((t - k * 0.05) % LOOP) + LOOP) % LOOP;
      const rgb = shownColor(g!, back);
      const idx = nearestName(rgb);
      if (idx !== cur) return { cur, prev: idx, since: (k - 0.5) * 0.05, prevRgb: rgb };
    }
    return { cur, prev: cur, since: 1, prevRgb: BASE_RGB };
  }

  useFrameCallback((frame) => {
    const n = frame.timestamp / 1000;
    const prev = now.get();
    if (prev < 0 && auto) start.set(n);
    now.set(n);
    if (!g) return;
    const dt = prev < 0 ? 0 : Math.min(n - prev, 1 / 30);
    let s = sim.get();
    if (!s) {
      s = createSim(reduced);
      sim.set(s);
    }
    const st = start.get();
    if (st >= 0 && n >= st) {
      const t = (n - st) % LOOP;
      frameMix.set(autoMix(g, t));
      shown.set(shownColor(g, t));
      label.set(autoLabel(t));
      return;
    }
    simStep(s, g, n, dt);
    if (s.events !== 0) {
      scheduleOnRN(haptic, s.events);
      s.events = 0;
    }
    const labelMoving = n - labelAt.get() < LABEL_ROLL + 0.05;
    const target = mixColor(simMix(s, g, n));
    const cur = shown.get();
    const settling = Math.abs(target[0] - cur[0]) + Math.abs(target[1] - cur[1]) + Math.abs(target[2] - cur[2]) > 0.002;
    if (simBusy(s) || settling || labelMoving || frameMix.get() === null) {
      frameMix.set(simMix(s, g, n));
      const k = 1 - Math.exp(-dt / 0.18);
      const next = [cur[0] + (target[0] - cur[0]) * k, cur[1] + (target[1] - cur[1]) * k, cur[2] + (target[2] - cur[2]) * k];
      shown.set(next);
      const idx = nearestName(next);
      const l = label.get();
      if (idx !== l.cur) {
        labelAt.set(n);
        label.set({ cur: idx, prev: l.cur, since: 0, prevRgb: cur });
      } else if (l.since < LABEL_ROLL) {
        label.set({ cur: l.cur, prev: l.prev, since: n - labelAt.get(), prevRgb: l.prevRgb });
      }
    }
    const r = resumeAt.get();
    if (r >= 0 && n >= r && !s.held && s.flights.length === 0) {
      resumeAt.set(-1);
      simWash(s, n);
      start.set(n + WASH_HOLD);
    }
  });

  function takeOver() {
    'worklet';
    const s = sim.get();
    if (!s || !g) return;
    const n = now.get();
    const st = start.get();
    if (st >= 0 && n >= st) simFromAuto(s, g, (n - st) % LOOP, n);
    start.set(-1);
    resumeAt.set(-1);
  }

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      const s = sim.get();
      if (s && g) simDown(s, g, e.x, e.y);
    })
    .onUpdate((e) => {
      const s = sim.get();
      if (s && g) simMove(s, g, e.x, e.y, e.velocityX, e.velocityY);
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s && g) simUp(s, g, now.get());
      if (auto) resumeAt.set(now.get() + 2);
    });

  const uniforms = useDerivedValue(() => {
    const m = frameMix.get();
    const dA: number[][] = [];
    const dP: number[][] = [];
    for (let i = 0; i < 4; i += 1) {
      const d = m ? m.drops[i] : undefined;
      dA.push(d ? [d.a[0], d.a[1], d.a[2], K * d.amt * (m ? m.fade : 1)] : [0, 0, 0, 0]);
      dP.push(d ? [d.x, d.y, d.m, d.tw] : [0, 0, 0, 0]);
    }
    const rp: number[][] = [];
    for (let i = 0; i < 3; i += 1) {
      const r = m ? m.ripples[i] : undefined;
      rp.push(r ? [r.x, r.y, r.age, r.str] : [0, 0, 0, 0]);
    }
    const base = m ? m.baseA : [0, 0, 0];
    return {
      center: [g?.cx ?? 0, g?.cy ?? 0],
      rim: g?.rim ?? 1,
      paintR: g?.paint ?? 1,
      unit: g?.u ?? 1,
      p3: P3,
      baseA: [base[0], base[1], base[2], m ? m.baseW : 1],
      dA0: dA[0],
      dA1: dA[1],
      dA2: dA[2],
      dA3: dA[3],
      dP0: dP[0],
      dP1: dP[1],
      dP2: dP[2],
      dP3: dP[3],
      rip0: rp[0],
      rip1: rp[1],
      rip2: rp[2],
      wash: m ? m.wash : [-1, 0],
    };
  });

  const wall = useDerivedValue(() => {
    const c = shown.get();
    return `rgb(${Math.round(c[0] * 255)},${Math.round(c[1] * 255)},${Math.round(c[2] * 255)})`;
  });

  const dynamic = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const m = frameMix.get();
    if (g && m) drawDynamic(canvas, g, m, shown.get(), label.get(), fonts);
    return recorder.finishRecordingAsPicture();
  });

  const card = useMemo(() => (g ? cardRect(g) : null), [g]);
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * pressed.get() }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && effect && card ? (
        <>
          <GestureDetector gesture={touch}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Paint mixing bowl. Drag a tin's drop into the bowl, drag in the bowl to stir.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={backdrop} />
                <RoundedRect rect={card} color={wall} />
                <Picture picture={room} />
                <Rect x={g.cx - g.rim - 40} y={g.cy - g.rim - 30} width={2 * g.rim + 80} height={2 * g.rim + 80}>
                  <Shader source={effect} uniforms={uniforms} />
                </Rect>
                <Picture picture={dynamic} />
              </Canvas>
            </View>
          </GestureDetector>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 8 }]}>
            <Text style={styles.kicker}>HUE ROOM</Text>
            <Text style={styles.title}>Mix your wall colour</Text>
            <Text style={styles.subtitle}>Drop paint into the bowl, then stir.</Text>
          </View>

          <Pressable
            accessibilityRole="button"
            onPress={applied}
            onPressIn={() => pressed.set(withTiming(1, { duration: 110 }))}
            onPressOut={() => pressed.set(withSpring(0, { duration: 350, dampingRatio: 0.7 }))}
            style={[styles.buttonWrap, { top: g.buttonY }]}
          >
            <Animated.View style={[styles.button, buttonStyle]}>
              <Text style={styles.buttonText}>Apply to wall</Text>
            </Animated.View>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EDE6DA', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#8A7F72', fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: '#2B2620', fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: '#7A7064', fontSize: 15, fontWeight: '400' },
  buttonWrap: { position: 'absolute', left: 24, right: 24, height: 52 },
  button: {
    flex: 1,
    borderRadius: 26,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#2B2620',
    boxShadow: '0 1px 2px rgba(43,38,32,0.3), 0 10px 24px rgba(43,38,32,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  buttonText: { color: '#F5F0E8', fontSize: 17, fontWeight: '600' },
});
