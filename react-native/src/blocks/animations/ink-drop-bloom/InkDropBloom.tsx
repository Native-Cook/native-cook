import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
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
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BUTTON_H,
  HEADER_H,
  type InkGeo,
  type InkSim,
  inkUniforms,
  inWater,
  makeInkGeo,
  makeSim,
  PALETTE_H,
  PHASES,
  simDrop,
  simStep,
  simStir,
  simTouchDown,
  simTouchUp,
  SWATCHES,
} from './inkDropMotion';
import { INK_SKSL } from './inkDropShader';

/*
 Ink Drop Bloom — "Studio Tint", a palette generator whose loader is a glass of water.

 Look: a warm studio wall and table, a tall glass tumbler of clear water in the middle (all drawn
 by one full-screen shader, inkDropShader.ts). Drops of ink fall in one by one — indigo, orchid,
 lagoon — and bloom into billowing plumes that sink, break into smaller rings and mix where they
 meet; the palette card below fills each swatch as its colour blooms (the two mixes once the
 colours meet). Then a mesh press pushes the ink down and the water is clear again.

 autoPlay: LOOP = 9 s, see inkDropMotion.ts for the script.
 Touch: tap in the water to drop ink at that point (indigo, orchid, lagoon in turn; light haptic);
 drag to stir (the plumes twist around the finger). 2 s after the last touch the ink fades out
 (0.9 s) and the loop restarts from t = 0. Reduced motion: the full palette, still, no loop.
*/

type Props = {
  /** Plays the 9 s drop-bloom-filter loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(INK_SKSL);
const PHASE_LINE = 22;
const INK_TEXT = '#2A2330';
const MUTED = '#776C61';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

export function InkDropBloom({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<InkGeo | null>(
    () => (size ? makeInkGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );

  const sim = useSharedValue<InkSim>(makeSim());
  const tick = useSharedValue(0);

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, sim]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((frame) => {
    if (!geo) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    simStep(sim.get(), dt, geo, auto, reduced);
    tick.set(tick.get() + 1);
  });

  const touch = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      if (!geo) return;
      simTouchDown(sim.get(), e.x, e.y);
    })
    .onUpdate((e) => {
      simStir(sim.get(), e.x, e.y);
    })
    .onEnd((e) => {
      if (!geo) return;
      const s = sim.get();
      // A short touch that barely moved is a tap: ink goes in where it landed.
      if (Math.hypot(e.translationX, e.translationY) < 10 && inWater(geo, e.x, e.y)) {
        simDrop(s, e.x, e.y);
        scheduleOnRN(feel);
      }
    })
    .onFinalize(() => {
      simTouchUp(sim.get());
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const s = sim.get();
    if (!geo) return inkUniforms(s.frame, makeInkGeo(1, 1, 0, 0), 0);
    return inkUniforms(s.frame, geo, s.flow);
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A glass of water. Tap the water to drop ink, drag to stir it."
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

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12, height: HEADER_H }]}>
        <View style={styles.kickerRow}>
          <Text style={styles.kicker}>STUDIO TINT</Text>
          <View style={styles.batch}>
            <Text style={styles.batchText}>Batch 0427</Text>
          </View>
        </View>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
          Generating your palette
        </Text>
        <View style={styles.phaseClip}>
          {PHASES.map((label, i) => (
            <PhaseLine key={`${label}-${i}`} index={i} label={label} sim={sim} tick={tick} />
          ))}
        </View>
      </View>

      {geo ? (
        <>
          <View style={[styles.palette, { top: geo.paletteTop, height: PALETTE_H }]}>
            <View style={styles.paletteHead}>
              <Text style={styles.paletteTitle}>Palette</Text>
              <View style={styles.barTrack}>
                <Bar sim={sim} tick={tick} />
              </View>
              <Text style={styles.paletteCount}>5 colours</Text>
            </View>
            <View style={styles.swatchRow}>
              {SWATCHES.map((s, i) => (
                <Swatch key={s.name} index={i} sim={sim} tick={tick} />
              ))}
            </View>
          </View>
          <View style={[styles.actions, { top: geo.buttonTop }]}>
            <SaveButton sim={sim} tick={tick} />
            <Pressable accessibilityRole="button" accessibilityLabel="Regenerate" style={styles.round}>
              <Svg width={22} height={22} viewBox="0 0 22 22">
                <Path
                  d="M17.6 8.2 A7 7 0 1 0 18 13.2 M17.8 3.6 V8.4 H13"
                  stroke={INK_TEXT}
                  strokeWidth={1.9}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  fill="none"
                />
              </Svg>
            </Pressable>
          </View>
        </>
      ) : null}
    </View>
  );
}

type Live = { sim: SharedValue<InkSim>; tick: SharedValue<number> };

/** One line of the phase label: lines roll up through a one-line window as the phase changes. */
function PhaseLine({ index, label, sim, tick }: Live & { index: number; label: string }) {
  const style = useAnimatedStyle(() => {
    tick.get();
    const d = index - sim.get().frame.label;
    return {
      opacity: Math.max(0, 1 - Math.abs(d) * 1.4),
      transform: [{ translateY: d * PHASE_LINE }],
    };
  });
  return (
    <Animated.Text style={[styles.phase, style]} numberOfLines={1}>
      {label}
    </Animated.Text>
  );
}

function Bar({ sim, tick }: Live) {
  const style = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ scaleX: Math.max(0.001, sim.get().frame.bar) }] };
  });
  return <Animated.View style={[styles.barFill, style]} />;
}

/** A swatch: a dashed outline that fills from the bottom like a little vial of its colour. */
function Swatch({ index, sim, tick }: Live & { index: number }) {
  const s = SWATCHES[index];
  const fill = useAnimatedStyle(() => {
    tick.get();
    const k = sim.get().frame.swatches[index];
    return { transform: [{ translateY: (1 - k) * 52 }] };
  });
  const text = useAnimatedStyle(() => {
    tick.get();
    const k = sim.get().frame.swatches[index];
    return { opacity: 0.35 + 0.65 * Math.min(1, k * 1.5) };
  });
  return (
    <View style={styles.swatch}>
      <View style={styles.vial}>
        <Animated.View style={[styles.vialFill, { backgroundColor: s.color }, fill]}>
          <View style={[styles.vialDeep, { backgroundColor: s.deep }]} />
          <View style={styles.vialShine} />
        </Animated.View>
      </View>
      <Animated.View style={[styles.swatchText, text]}>
        <Text style={styles.swatchName} numberOfLines={1}>
          {s.name}
        </Text>
        <Text style={styles.swatchHex} numberOfLines={1}>
          {s.hex}
        </Text>
      </Animated.View>
    </View>
  );
}

function SaveButton({ sim, tick }: Live) {
  const style = useAnimatedStyle(() => {
    tick.get();
    return { opacity: 0.5 + 0.5 * sim.get().frame.bar };
  });
  return (
    <Animated.View style={[styles.save, style]}>
      <Pressable accessibilityRole="button" style={styles.savePress}>
        <Text style={styles.saveText}>Save palette</Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#E6DED2', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kickerRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  kicker: { color: '#8A5A3C', fontSize: 12, fontWeight: '700', letterSpacing: 2.2 },
  batch: {
    paddingHorizontal: 8,
    height: 20,
    borderRadius: 10,
    justifyContent: 'center',
    backgroundColor: 'rgba(138,90,60,0.1)',
  },
  batchText: { color: '#8A5A3C', fontSize: 11, fontWeight: '600', fontVariant: ['tabular-nums'] },
  title: { color: INK_TEXT, fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 10 },
  phaseClip: { height: PHASE_LINE, marginTop: 8, overflow: 'hidden' },
  phase: { position: 'absolute', left: 0, right: 0, height: PHASE_LINE, color: MUTED, fontSize: 16, fontWeight: '500' },
  palette: {
    position: 'absolute',
    left: 20,
    right: 20,
    paddingHorizontal: 16,
    paddingTop: 16,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(252,249,244,0.86)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(120,100,80,0.18)',
    boxShadow: '0 1px 3px rgba(70,52,36,0.14), 0 18px 36px rgba(70,52,36,0.12), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  paletteHead: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  paletteTitle: { color: INK_TEXT, fontSize: 15, fontWeight: '700' },
  barTrack: { flex: 1, height: 4, borderRadius: 2, backgroundColor: 'rgba(42,35,48,0.08)', overflow: 'hidden' },
  barFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: '100%',
    borderRadius: 2,
    backgroundColor: '#4A3F86',
    transformOrigin: 'left',
  },
  paletteCount: { color: MUTED, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  swatchRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 14 },
  swatch: { width: 56, alignItems: 'center' },
  vial: {
    width: 48,
    height: 48,
    borderRadius: 14,
    borderCurve: 'continuous',
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: 'rgba(42,35,48,0.22)',
    overflow: 'hidden',
    backgroundColor: 'rgba(42,35,48,0.03)',
  },
  vialFill: { position: 'absolute', left: -2, right: -2, top: -2, bottom: -2 },
  vialDeep: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '45%', opacity: 0.55 },
  vialShine: {
    position: 'absolute',
    left: 6,
    top: 6,
    width: 14,
    height: 5,
    borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  swatchText: { alignItems: 'center', marginTop: 6 },
  swatchName: { color: INK_TEXT, fontSize: 12, fontWeight: '600' },
  swatchHex: { color: MUTED, fontSize: 10, fontFamily: MONO, marginTop: 1 },
  actions: { position: 'absolute', left: 20, right: 20, height: BUTTON_H, flexDirection: 'row', gap: 12 },
  save: {
    flex: 1,
    borderRadius: BUTTON_H / 2,
    backgroundColor: '#2B2433',
    boxShadow: '0 1px 2px rgba(30,20,40,0.3), 0 10px 24px rgba(30,20,40,0.22), inset 0 1px 0 rgba(255,255,255,0.14)',
  },
  savePress: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  saveText: { color: '#F6F1EA', fontSize: 17, fontWeight: '600' },
  round: {
    width: BUTTON_H,
    height: BUTTON_H,
    borderRadius: BUTTON_H / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(252,249,244,0.9)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(120,100,80,0.22)',
    boxShadow: '0 1px 2px rgba(70,52,36,0.14), 0 8px 18px rgba(70,52,36,0.1)',
  },
});
