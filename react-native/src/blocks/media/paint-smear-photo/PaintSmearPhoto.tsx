import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  BRUSHES,
  IDLE_BEFORE_BACK,
  lay,
  LOOP,
  makePaint,
  MODE_AUTO,
  MODE_LIVE,
  type Paint,
  resetFrame,
  scriptFrame,
  startReset,
  STEP,
  takeOver,
  TRAIL,
} from './paintSmearMotion';
import { PAINT_SMEAR_SKSL } from './paintSmearShader';

/*
 Paint Smear Photo — "Atelier", a photo editor's oil mode: the finger drags the photo like wet
 paint.

 Look: a warm dark studio screen, a still life photo (a teal enamel ewer, orange halves, dried
 hydrangea on black) on a rounded card with two shadows. One runtime shader (paintSmearShader.ts)
 displaces the photo by a drag field made of up to 24 trail points, pulls colour along the stroke
 (three taps), scores bristle lines across it and lights the ridges of the moved paint (impasto)
 from the upper left. Under the card: brush size chips (S/M/L), a Wet–Dry slider (drier paint =
 harsher bristles, shorter drag, less sheen) and Reset.

 autoPlay (LOOP = 9 s, paintSmearMotion.ts): 0–1.4 rest (the clean photo), three curved strokes
 1.4–2.4, 2.6–3.6 and 3.8–4.8 (8 points each, the brush easing in and out, each point's drag
 building over 0.25 s), 4.8–6.2 hold, 6.2–8.2 the paint flows back to the clean photo, 8.2–9 rest.

 Touch: drag on the photo to smear it (a point every 16 pt, drag = travel × 1.1, at most 34 pt;
 when the trail is full the two oldest points merge). Double-tap or Reset: the paint flows back
 (0.85 s). 2 s after the last touch it flows back the same way and the loop starts again from
 t = 0. Reduced motion: no loop, the clean photo; smearing still works.
 */

type Props = {
  /** Plays the 9 s paint-and-flow-back loop until touched. */
  autoPlay?: boolean;
};

const PHOTO = require('./assets/still-life.jpg');
const EFFECT = Skia.RuntimeEffect.Make(PAINT_SMEAR_SKSL);
const INK = '#f3ece2';
const MUTE = 'rgba(243,236,226,0.6)';
const FAINT = 'rgba(243,236,226,0.36)';
const ACCENT = '#e9a24b';
const SLIDER_W = 168;
const THUMB = 26;
/** Two tool rows of 40 pt with 16 pt between. */
const TOOLS_H = 96;

export function PaintSmearPhoto({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const photo = useImage(PHOTO);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [brush, setBrush] = useState(1);

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;
  const cardW = Math.max(0, W - 32);
  const cardTop = insets.top + 68;
  // The tools sit at the bottom above the hint; the photo fills everything between.
  const hintBottom = insets.bottom + 12;
  const toolsBottom = hintBottom + 18 + 16;
  const cardH = Math.max(0, Math.round(H - cardTop - toolsBottom - TOOLS_H - 20));

  const state = useSharedValue<Paint>(makePaint(auto));
  const tick = useSharedValue(0);
  const sigma = useSharedValue(BRUSHES[1]);
  const dry = useSharedValue(0.35);
  const spin = useSharedValue(0);

  useEffect(() => {
    state.set(makePaint(auto));
  }, [auto, state]);

  useEffect(() => {
    sigma.set(withTiming(BRUSHES[brush], { duration: 180, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [brush, sigma]);

  useFrameCallback((frame) => {
    const s = state.get();
    if (cardH <= 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    s.now += dt;
    if (s.mode === MODE_AUTO) {
      if (auto) {
        s.t = (s.t + dt) % LOOP;
        scriptFrame(s, s.t, cardW, cardH);
      }
    } else {
      if (resetFrame(s) && s.resume) {
        s.resume = false;
        s.mode = MODE_AUTO;
        s.t = 0;
      }
      if (auto && !s.painting && s.resetAt < 0 && s.now - s.lastTouch > IDLE_BEFORE_BACK) {
        if (s.x.length > 0) startReset(s, true);
        else {
          s.mode = MODE_AUTO;
          s.t = 0;
        }
      }
    }
    tick.set(tick.get() + 1);
  });

  const light = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  function touched() {
    'worklet';
    const s = state.get();
    s.lastTouch = s.now;
    if (s.mode !== MODE_LIVE) takeOver(s);
  }

  function reset() {
    'worklet';
    touched();
    startReset(state.get(), false);
    spin.set(withTiming(spin.get() - 360, { duration: 700, easing: Easing.bezier(0.77, 0, 0.175, 1) }));
  }

  const smear = Gesture.Pan()
    .minDistance(4)
    .onBegin((e) => {
      touched();
      const s = state.get();
      s.lx = e.x;
      s.ly = e.y;
    })
    .onStart(() => {
      const s = state.get();
      s.painting = true;
      // A stroke stops a flow-back that was under way where it is.
      if (s.resetAt >= 0) takeOver(s);
    })
    .onUpdate((e) => {
      const s = state.get();
      s.lastTouch = s.now;
      if (Math.hypot(e.x - s.lx, e.y - s.ly) >= STEP) lay(s, e.x, e.y);
    })
    .onFinalize(() => {
      const s = state.get();
      s.painting = false;
      s.lastTouch = s.now;
    });
  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      reset();
      scheduleOnRN(light);
    });
  const photoGesture = Gesture.Simultaneous(smear, doubleTap);

  const slide = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touched();
      dry.set(Math.min(Math.max((e.x - THUMB / 2) / (SLIDER_W - THUMB), 0), 1));
    })
    .onUpdate((e) => {
      touched();
      dry.set(Math.min(Math.max((e.x - THUMB / 2) / (SLIDER_W - THUMB), 0), 1));
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const s = state.get();
    const trail: number[] = [];
    for (let i = 0; i < TRAIL; i += 1) {
      if (i < s.x.length) trail.push(s.x[i], s.y[i], s.ax[i], s.ay[i]);
      else trail.push(0, 0, 0, 0);
    }
    return {
      size: [Math.max(cardW, 1), Math.max(cardH, 1)],
      trail,
      sigma: sigma.get(),
      dry: dry.get(),
      flow: s.flow,
      radius: 22,
    };
  });

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: dry.get() * (SLIDER_W - THUMB) }] }));
  const fillStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: -(SLIDER_W / 2) * (1 - dry.get()) }, { scaleX: Math.max(dry.get(), 0.001) }],
  }));
  const spinStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${spin.get()}deg` }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const rect = useMemo(() => ({ x: 0, y: 0, width: cardW, height: cardH }), [cardW, cardH]);

  return (
    <View style={styles.root} onLayout={onLayout}>
      <LinearGradient colors={['#231d18', '#1a1613', '#120f0d']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />

      <View style={[styles.bar, { top: insets.top + 12 }]}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>Atelier</Text>
          <View style={styles.mode}>
            <Text style={styles.modeText}>Oil mode</Text>
          </View>
        </View>
        <Pressable accessibilityRole="button" style={styles.export}>
          <Text style={styles.exportText}>Export</Text>
        </Pressable>
      </View>

      {size && cardH > 0 ? (
        <GestureDetector gesture={photoGesture}>
          <View
            style={[styles.card, { top: cardTop, width: cardW, height: cardH }]}
            accessible
            accessibilityLabel="Still life photo. Drag to smear it like wet oil paint; double-tap to reset."
          >
            {photo && EFFECT ? (
              <Canvas style={StyleSheet.absoluteFill}>
                <Fill>
                  <Shader source={EFFECT} uniforms={uniforms}>
                    <ImageShader image={photo} rect={rect} fit="cover" />
                  </Shader>
                </Fill>
              </Canvas>
            ) : null}
          </View>
        </GestureDetector>
      ) : null}

      {size && cardH > 0 ? (
        <View style={[styles.tools, { bottom: toolsBottom }]}>
          <View style={styles.toolRow}>
            <Text style={styles.toolLabel}>Brush</Text>
            <View style={styles.chips}>
              {['S', 'M', 'L'].map((label, i) => (
                <Pressable
                  key={label}
                  accessibilityRole="button"
                  accessibilityLabel={`Brush size ${label}`}
                  accessibilityState={{ selected: brush === i }}
                  onPress={() => {
                    setBrush(i);
                    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
                  }}
                  style={[styles.chip, brush === i && styles.chipOn]}
                >
                  <View style={[styles.chipDot, { width: 6 + i * 4, height: 6 + i * 4, borderRadius: 3 + i * 2 }, brush === i && styles.chipDotOn]} />
                  <Text style={[styles.chipText, brush === i && styles.chipTextOn]}>{label}</Text>
                </Pressable>
              ))}
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Reset the photo"
              onPress={() => {
                scheduleOnUI(reset);
                light();
              }}
              style={styles.reset}
            >
              <Animated.View style={spinStyle}>
                <Svg width={16} height={16} viewBox="0 0 16 16">
                  <Path d="M13 8 A5 5 0 1 1 11.2 4.2" stroke={INK} strokeWidth={1.8} strokeLinecap="round" fill="none" />
                  <Path d="M11.6 1.6 L11.4 4.6 L8.4 4.3" stroke={INK} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                </Svg>
              </Animated.View>
              <Text style={styles.resetText}>Reset</Text>
            </Pressable>
          </View>
          <View style={styles.toolRow}>
            <Text style={styles.toolLabel}>Paint</Text>
            <View style={styles.sliderRow}>
              <Text style={styles.sliderEnd}>Wet</Text>
              <GestureDetector gesture={slide}>
                <View style={styles.slider} accessible accessibilityRole="adjustable" accessibilityLabel="Paint wetness">
                  <View style={styles.track} />
                  <Animated.View style={[styles.trackFill, fillStyle]} />
                  <Animated.View style={[styles.thumb, thumbStyle]} />
                </View>
              </GestureDetector>
              <Text style={styles.sliderEnd}>Dry</Text>
            </View>
          </View>
        </View>
      ) : null}

      <Text pointerEvents="none" style={[styles.hint, { bottom: hintBottom }]}>
        Drag to smear · double-tap to reset
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#1a1613', overflow: 'hidden' },
  bar: { position: 'absolute', left: 16, right: 16, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  mode: { height: 26, paddingHorizontal: 10, borderRadius: 13, justifyContent: 'center', backgroundColor: 'rgba(233,162,75,0.16)' },
  modeText: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 0.4 },
  export: {
    height: 36,
    paddingHorizontal: 16,
    borderRadius: 18,
    justifyContent: 'center',
    backgroundColor: '#f1e7d8',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.7), 0 1px 2px rgba(0,0,0,0.35)',
  },
  exportText: { color: '#2b2118', fontSize: 15, fontWeight: '600' },
  card: {
    position: 'absolute',
    left: 16,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#0f0d0c',
    boxShadow: '0 2px 3px rgba(0,0,0,0.4), 0 22px 44px rgba(0,0,0,0.45)',
  },
  tools: { position: 'absolute', left: 16, right: 16, gap: 16 },
  toolRow: { flexDirection: 'row', alignItems: 'center', gap: 12, height: 40 },
  toolLabel: { color: FAINT, fontSize: 13, fontWeight: '600', width: 44 },
  chips: { flexDirection: 'row', gap: 8, flex: 1 },
  chip: {
    height: 36,
    minWidth: 52,
    paddingHorizontal: 12,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: 'rgba(243,236,226,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,236,226,0.12)',
  },
  chipOn: { backgroundColor: '#f1e7d8', borderColor: '#f1e7d8' },
  chipDot: { backgroundColor: MUTE },
  chipDotOn: { backgroundColor: '#2b2118' },
  chipText: { color: MUTE, fontSize: 14, fontWeight: '600' },
  chipTextOn: { color: '#2b2118' },
  reset: {
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: 'rgba(243,236,226,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,236,226,0.12)',
  },
  resetText: { color: INK, fontSize: 14, fontWeight: '600' },
  sliderRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  sliderEnd: { color: MUTE, fontSize: 13, fontWeight: '500' },
  slider: { width: SLIDER_W, height: 40, justifyContent: 'center' },
  track: { position: 'absolute', left: 0, right: 0, height: 4, borderRadius: 2, backgroundColor: 'rgba(243,236,226,0.14)' },
  trackFill: { position: 'absolute', left: 0, width: SLIDER_W, height: 4, borderRadius: 2, backgroundColor: ACCENT },
  thumb: {
    position: 'absolute',
    left: 0,
    width: THUMB,
    height: THUMB,
    borderRadius: THUMB / 2,
    backgroundColor: '#f6eee2',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.9), 0 1px 2px rgba(0,0,0,0.35), 0 6px 14px rgba(0,0,0,0.3)',
  },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: FAINT, fontSize: 13, fontWeight: '500' },
});
