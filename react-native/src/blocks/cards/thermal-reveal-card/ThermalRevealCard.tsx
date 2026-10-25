import { Canvas, Fill, ImageShader, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { COVES, makeMapImage } from './thermalArt';
import {
  backFrame,
  beginBack,
  emit,
  HEAT_RADIUS,
  HEAT_SLOTS,
  heatAt,
  heatStrength,
  IDLE_BEFORE_BACK,
  liveFrame,
  LOOP,
  makeThermal,
  MODE_AUTO,
  MODE_BACK,
  MODE_LIVE,
  scriptFrame,
  type Thermal,
  TILT_MAX,
  TILT_PER_PT,
} from './thermalMotion';
import { THERMAL_SKSL } from './thermalShader';

/*
 Thermal Reveal Card — a travel club's membership pass printed in thermochromic ink.

 Look: a dark navy screen ("Hidden Coves of Aruna"), and a portrait pass of dark ink (#1b1f2b) with
 a fine guilloché print, a gloss band and a lit top edge. Warmth turns the ink deep violet →
 magenta → orange → yellow and then clear, showing a map drawn in code underneath (thermalArt.ts):
 islands, a dashed route, three coves marked X, a compass and place names. One runtime shader
 (thermalShader.ts) draws the card; the map is drawn once into an image.

 autoPlay (LOOP = 9 s, thermalMotion.ts): 0–1.5 rest (dark card, gloss drifting, a ±1.6° sway),
 1.5–5.5 a thumb sweeps an S-curve down the card through the three coves (heat points every
 0.061 s, each fading over 1.4 s), 4.6–5.5 the whole card warms, 5.5–6.4 peak (route and Xs
 visible, warm colour fringe at the corners), 6.4–8.2 it cools from the edges in, 8.2–9 rest.

 Touch: drag on the card to warm it (a resting thumb keeps warming the same spot); a light haptic
 when a cove is uncovered. Drag beside the card to tilt it (up to 8°, soft limit), the gloss
 follows; it springs back on release. 2 s after the last touch the warmth fades (0.8 s) and the
 loop restarts from t = 0. Reduced motion: the loop stops on the revealed map.
 */

type Props = {
  /** Plays the 9 s warm-and-cool loop until touched. */
  autoPlay?: boolean;
};

const INK = '#f2ece2';
const MUTE = 'rgba(242,236,226,0.6)';
const FAINT = 'rgba(242,236,226,0.38)';
const AMBER = '#f2b35a';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

export function ThermalRevealCard({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(THERMAL_SKSL), []);

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;
  const cardW = Math.min(W - 48, 345);
  const cardTop = insets.top + 168;
  const cardH = Math.max(0, Math.round(Math.min(cardW * 1.3, H - cardTop - insets.bottom - 196)));
  const cardX = (W - cardW) / 2;
  const map = useMemo(
    () => (cardW > 0 && cardH > 0 ? makeMapImage(cardW, cardH, Math.min(PixelRatio.get(), 3)) : null),
    [cardW, cardH],
  );

  const state = useSharedValue<Thermal>(makeThermal(auto, reduced));
  const tick = useSharedValue(0);
  useEffect(() => {
    state.set(makeThermal(auto, reduced));
  }, [auto, reduced, state]);

  const buzz = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((frame) => {
    const s = state.get();
    if (cardH <= 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    s.now += dt;
    if (s.mode === MODE_AUTO) {
      if (auto) s.t = (s.t + dt) % LOOP;
      if (auto) scriptFrame(s, s.t, cardW, cardH);
    } else if (s.mode === MODE_LIVE) {
      liveFrame(s, dt);
      // One light tick per cove uncovered by a finger.
      for (let i = 0; i < 3; i += 1) {
        const heat = heatAt(s, COVES[i][0] * cardW, COVES[i][1] * cardH, cardW, cardH);
        if (!s.found[i] && heat > 0.85 && s.warming) {
          s.found[i] = true;
          s.buzz = true;
        } else if (s.found[i] && heat < 0.4) {
          s.found[i] = false;
        }
      }
      const idle = !s.warming && !s.tilting && s.now - s.lastTouch > IDLE_BEFORE_BACK;
      if (auto && idle) beginBack(s);
    } else if (s.mode === MODE_BACK) {
      if (backFrame(s, dt)) {
        s.mode = MODE_AUTO;
        s.t = 0;
      }
    }
    if (s.buzz) {
      s.buzz = false;
      scheduleOnRN(buzz);
    }
    tick.set(tick.get() + 1);
  });

  function soft(x: number) {
    'worklet';
    return TILT_MAX * Math.tanh(x / TILT_MAX);
  }

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const s = state.get();
      s.lastTouch = s.now;
      if (s.mode !== MODE_LIVE) {
        s.mode = MODE_LIVE;
        s.fade = 1;
        s.vrx = 0;
        s.vry = 0;
      }
      const x = e.x - cardX;
      const y = e.y - (cardTop - STAGE_PAD);
      if (x >= 0 && x <= cardW && y >= 0 && y <= cardH) {
        s.warming = true;
        s.fx = x;
        s.fy = y;
        emit(s, x, y);
      } else {
        s.tilting = true;
        s.tiltX = s.rx;
        s.tiltY = s.ry;
      }
    })
    .onUpdate((e) => {
      const s = state.get();
      s.lastTouch = s.now;
      if (s.warming) {
        const x = Math.min(Math.max(e.x - cardX, 0), cardW);
        const y = Math.min(Math.max(e.y - (cardTop - STAGE_PAD), 0), cardH);
        // Lay a point every 12 pt of travel as well as every EMIT_EVERY while resting.
        s.fx = x;
        s.fy = y;
        if (Math.hypot(x - s.ex, y - s.ey) > 12) emit(s, x, y);
      } else if (s.tilting) {
        s.tiltY = soft(e.translationX * TILT_PER_PT);
        s.tiltX = soft(-e.translationY * TILT_PER_PT);
      }
    })
    .onEnd((e) => {
      const s = state.get();
      if (s.tilting) {
        s.vry = e.velocityX * TILT_PER_PT * 0.5;
        s.vrx = -e.velocityY * TILT_PER_PT * 0.5;
      }
    })
    .onFinalize(() => {
      const s = state.get();
      s.warming = false;
      s.tilting = false;
      s.lastTouch = s.now;
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const s = state.get();
    const heat: number[] = [];
    for (let j = 0; j < HEAT_SLOTS; j += 1) {
      heat.push(s.hx[j], s.hy[j], heatStrength(s.now - s.ht[j]) * s.fade, HEAT_RADIUS);
    }
    return {
      size: [Math.max(cardW, 1), Math.max(cardH, 1)],
      heat,
      glob: s.glob,
      gloss: [s.gloss, 1],
      radius: 22,
    };
  });

  const tilt = useAnimatedStyle(() => {
    tick.get();
    const s = state.get();
    return {
      transform: [{ perspective: 900 }, { rotateX: `${s.rx}deg` }, { rotateY: `${s.ry}deg` }],
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <LinearGradient colors={['#18202e', '#0f141d', '#0a0d13']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />

      <View style={[styles.nav, { top: insets.top + 6 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back" style={styles.navButton}>
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <Path d="M9 2 L4 7 L9 12" stroke={INK} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </Pressable>
        <Text style={styles.navTitle}>Members</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="More" style={styles.navButton}>
          <Svg width={16} height={4} viewBox="0 0 16 4">
            <Circle cx={2} cy={2} r={1.6} fill={INK} />
            <Circle cx={8} cy={2} r={1.6} fill={INK} />
            <Circle cx={14} cy={2} r={1.6} fill={INK} />
          </Svg>
        </Pressable>
      </View>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 62 }]}>
        <Text style={styles.kicker}>COVE SOCIETY · SEASON 4</Text>
        <Text style={styles.title}>Hidden Coves of Aruna</Text>
        <Text style={styles.subtitle}>Warm the ink with your thumb to read the map</Text>
      </View>

      {size && cardH > 0 ? (
        <GestureDetector gesture={touch}>
          <View style={[styles.stage, { top: cardTop - STAGE_PAD, height: cardH + STAGE_PAD * 2 }]}>
            <Animated.View
              style={[styles.card, { left: cardX, top: STAGE_PAD, width: cardW, height: cardH }, tilt]}
              accessible
              accessibilityLabel="Membership card in thermochromic ink. Drag on it to warm the ink and reveal the map; drag beside it to tilt it."
            >
              {effect && map ? (
                <Canvas style={StyleSheet.absoluteFill}>
                  <Fill>
                    <Shader source={effect} uniforms={uniforms}>
                      <ImageShader image={map} rect={{ x: 0, y: 0, width: cardW, height: cardH }} fit="fill" />
                    </Shader>
                  </Fill>
                </Canvas>
              ) : null}
              <View pointerEvents="none" style={styles.cardTop}>
                <View>
                  <Text style={styles.brand}>ARUNA</Text>
                  <Text style={styles.brandSub}>COVE SOCIETY</Text>
                </View>
                <Text style={styles.number}>No. 0418</Text>
              </View>
              <View pointerEvents="none" style={styles.cardBottom}>
                <View>
                  <Text style={styles.holder}>MARA ELLIS</Text>
                  <Text style={styles.holderSub}>Explorer · since 2023</Text>
                </View>
                <View style={styles.reveal}>
                  <Svg width={8} height={16} viewBox="0 0 8 16">
                    <Path d="M4 1.2 a1.8 1.8 0 0 1 1.8 1.8 v6.6 a3 3 0 1 1 -3.6 0 V3 A1.8 1.8 0 0 1 4 1.2 Z" stroke={INK} strokeWidth={1} fill="none" />
                    <Circle cx={4} cy={12.2} r={1.6} fill={AMBER} />
                  </Svg>
                  <Text style={styles.revealText}>Reveals at 31 °C</Text>
                </View>
              </View>
            </Animated.View>
          </View>
        </GestureDetector>
      ) : null}

      {size && cardH > 0 ? (
        <View pointerEvents="none" style={[styles.stats, { top: cardTop + cardH + 22 }]}>
          {[
            ['6', 'coves'],
            ['14.8 km', 'route'],
            ['06:12', 'low tide'],
          ].map(([value, label]) => (
            <View key={label} style={styles.stat}>
              <Text style={styles.statValue}>{value}</Text>
              <Text style={styles.statLabel}>{label}</Text>
            </View>
          ))}
        </View>
      ) : null}

      <Pressable accessibilityRole="button" style={[styles.cta, { bottom: insets.bottom + 20 }]}>
        <LinearGradient
          colors={['#f7cf7a', '#f2a94e', '#e9853a']}
          locations={[0, 0.55, 1]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.ctaFill}
        >
          <Text style={styles.ctaText}>Start the route</Text>
        </LinearGradient>
      </Pressable>
    </View>
  );
}

/** The touch area reaches this far above and below the card, for tilting beside it. */
const STAGE_PAD = 28;

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0e1219', overflow: 'hidden' },
  nav: { position: 'absolute', left: 16, right: 16, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.12)',
  },
  navTitle: { color: MUTE, fontSize: 15, fontWeight: '600' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { color: AMBER, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  stage: { position: 'absolute', left: 0, right: 0 },
  card: {
    position: 'absolute',
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#1b1f2b',
    boxShadow: '0 2px 3px rgba(0,0,0,0.35), 0 20px 44px rgba(0,0,0,0.42)',
  },
  cardTop: { position: 'absolute', left: 20, right: 20, top: 18, flexDirection: 'row', justifyContent: 'space-between' },
  brand: { color: INK, fontSize: 17, fontWeight: '800', letterSpacing: 5, textShadowColor: 'rgba(0,0,0,0.45)', textShadowRadius: 4 },
  brandSub: { color: MUTE, fontSize: 9, fontWeight: '700', letterSpacing: 2.6, marginTop: 2, textShadowColor: 'rgba(0,0,0,0.45)', textShadowRadius: 3 },
  number: { color: INK, fontFamily: MONO, fontSize: 12, fontVariant: ['tabular-nums'], textShadowColor: 'rgba(0,0,0,0.45)', textShadowRadius: 3 },
  cardBottom: { position: 'absolute', left: 20, right: 20, bottom: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  holder: { color: INK, fontSize: 13, fontWeight: '700', letterSpacing: 1.6, textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 3 },
  holderSub: { color: MUTE, fontSize: 11, fontWeight: '500', marginTop: 2, textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 3 },
  reveal: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  revealText: { color: MUTE, fontSize: 10, fontWeight: '600', textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 3 },
  stats: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between' },
  stat: { flex: 1, alignItems: 'center', gap: 2 },
  statValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  statLabel: { color: FAINT, fontSize: 12, fontWeight: '500' },
  cta: { position: 'absolute', left: 24, right: 24, height: 52 },
  ctaFill: {
    flex: 1,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.45), 0 1px 2px rgba(0,0,0,0.3), 0 10px 24px rgba(233,133,58,0.28)',
  },
  ctaText: { color: '#2a1a0c', fontSize: 17, fontWeight: '700' },
});
