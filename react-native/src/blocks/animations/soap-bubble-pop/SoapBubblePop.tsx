import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
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
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  type BubbleGeo,
  type BubbleSim,
  bubbleUniforms,
  CARD_H,
  makeBubbleGeo,
  makeSim,
  simStep,
  simTap,
  simTouch,
  STAR_FOR,
} from './bubbleMotion';
import { BUBBLE_SKSL } from './bubbleShader';

/*
 Soap Bubble Pop — "Bubble Bath", the reward screen of a kids' word-learning app.

 Look: a pastel tiled bathroom wall (aqua to lilac), three big soap bubbles hovering over it with
 swirling thin-film colour, a window reflection and a faint rim; a coral bubble wand holding a
 film of soap above the "Up next" card. All of it is one full-screen shader (bubbleShader.ts).
 Tap a bubble and it pops: the film tears open from the finger, droplets fly off its outline and
 fall, a "+10" star floats up and the round's score rolls. New bubbles are blown from the wand.

 autoPlay: LOOP = 8 s (script in bubbleMotion.ts). Touch interrupts it; popped bubbles are blown
 again from the wand one at a time; 2 s after the last touch the bubbles glide home (0.9 s) and the
 loop restarts from t = 0. Reduced motion: the bubbles rest, still; nothing plays or pops.
*/

type Props = {
  /** Plays the 8 s hover-pop-blow loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(BUBBLE_SKSL);
const NAVY = '#1B2F55';
const MUTED = '#3E5276';
const CORAL = '#F0644A';
const DIGIT_H = 22;
const TENS = ['', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

export function SoapBubblePop({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<BubbleGeo | null>(
    () => (size ? makeBubbleGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const sim = useSharedValue<BubbleSim>(makeSim());
  const tick = useSharedValue(0);

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, sim]);

  const pop = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((frame) => {
    if (!geo) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    simStep(sim.get(), dt, geo, auto, reduced);
    tick.set(tick.get() + 1);
  });

  const touch = Gesture.Tap()
    .enabled(!reduced)
    .maxDuration(400)
    .onBegin(() => {
      if (geo) simTouch(sim.get(), geo);
    })
    .onEnd((e) => {
      if (!geo) return;
      if (simTap(sim.get(), geo, e.x, e.y) >= 0) scheduleOnRN(pop);
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    return bubbleUniforms(sim.get().frame, geo ?? makeBubbleGeo(1, 1, 0, 0));
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Soap bubbles. Tap a bubble to pop it.">
          {size && EFFECT ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      {[0, 1, 2].map((i) => (
        <FloatingStar key={i} index={i} sim={sim} tick={tick} />
      ))}

      <View style={[styles.header, { top: insets.top + 8 }]} pointerEvents="box-none">
        <View style={styles.topRow} pointerEvents="box-none">
          <Pressable accessibilityRole="button" accessibilityLabel="Close" style={styles.close}>
            <Svg width={16} height={16} viewBox="0 0 16 16">
              <Path d="M3.5 3.5 L12.5 12.5 M12.5 3.5 L3.5 12.5" stroke={NAVY} strokeWidth={2} strokeLinecap="round" />
            </Svg>
          </Pressable>
          <Text style={styles.lessonOf}>Lesson 4 of 12</Text>
          <View style={styles.scorePill} accessibilityLabel="Round stars">
            <StarIcon size={18} />
            <View style={styles.digits}>
              <ScoreWheel sim={sim} tick={tick} />
              <Text style={styles.digit}>0</Text>
            </View>
          </View>
        </View>
        <Text style={styles.title}>Lesson 4 done!</Text>
        <Text style={styles.subtitle}>Ocean Words · pop the bubbles for stars</Text>
      </View>

      {geo ? (
        <View style={[styles.card, { top: geo.cardTop, height: CARD_H }]}>
          <View style={styles.cardRow}>
            <View style={styles.shell}>
              <Svg width={28} height={28} viewBox="0 0 28 28">
                <Path d="M14 4 C7 4 3.5 10 4 16 L14 24 L24 16 C24.5 10 21 4 14 4 Z" fill="#FFD9C9" stroke={CORAL} strokeWidth={1.6} strokeLinejoin="round" />
                <Path d="M14 6 V22 M9 7.5 L11.5 21 M19 7.5 L16.5 21 M5.5 12 L10 20 M22.5 12 L18 20" stroke={CORAL} strokeWidth={1.2} strokeLinecap="round" />
              </Svg>
            </View>
            <View style={styles.cardText}>
              <Text style={styles.upNext}>UP NEXT · 6 MIN</Text>
              <Text style={styles.nextTitle} numberOfLines={1}>
                Lesson 5 · Shells & Shores
              </Text>
            </View>
          </View>
          <Pressable accessibilityRole="button" style={styles.continue}>
            <Text style={styles.continueText}>Continue</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

type Live = { sim: SharedValue<BubbleSim>; tick: SharedValue<number> };

function StarIcon({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Defs>
        <SvgGradient id="star" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#FFE28A" />
          <Stop offset="0.55" stopColor="#FFC23D" />
          <Stop offset="1" stopColor="#F29A1F" />
        </SvgGradient>
      </Defs>
      <Path
        d="M12 2.6 L14.8 8.6 L21.3 9.3 L16.4 13.7 L17.8 20.2 L12 16.9 L6.2 20.2 L7.6 13.7 L2.7 9.3 L9.2 8.6 Z"
        fill="url(#star)"
        stroke="#D9821A"
        strokeWidth={1}
        strokeLinejoin="round"
      />
      <Circle cx={9.6} cy={9.8} r={1.4} fill="rgba(255,255,255,0.7)" />
    </Svg>
  );
}

/** The tens of the round score: a column of digits that rolls and rests on a whole digit. */
function ScoreWheel({ sim, tick }: Live) {
  const style = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ translateY: -sim.get().frame.score * DIGIT_H }] };
  });
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {TENS.map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** "+10" with a star, floating up from where a bubble popped. */
function FloatingStar({ index, sim, tick }: Live & { index: number }) {
  const style = useAnimatedStyle(() => {
    tick.get();
    const f = sim.get().frame;
    const age = f.stars[index];
    if (age < 0) return { opacity: 0, transform: [{ translateX: -200 }, { translateY: -200 }, { scale: 0.9 }] };
    const u = Math.min(1, age / STAR_FOR);
    const rise = 1 - Math.pow(1 - u, 3);
    const pop = Math.min(1, age / 0.22);
    // A playful overshoot on the way in: 0.9 → 1.06 → 1.
    const scale = age < 0.22 ? 0.9 + 0.16 * Math.sin(pop * Math.PI * 0.5) : 1.06 - 0.06 * Math.min(1, (age - 0.22) / 0.2);
    const opacity = Math.min(1, age / 0.12) * (1 - Math.max(0, (age - 0.7) / 0.4));
    return {
      opacity,
      transform: [{ translateX: f.starX[index] - 44 }, { translateY: f.starY[index] - 20 - 76 * rise }, { scale }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.star, style]}>
      <StarIcon size={26} />
      <Text style={styles.starText}>+10</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#86A9DC', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 44 },
  close: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.55)',
    boxShadow: '0 1px 2px rgba(27,47,85,0.18), inset 0 1px 0 rgba(255,255,255,0.8)',
  },
  lessonOf: { color: MUTED, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  scorePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 40,
    paddingHorizontal: 14,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.72)',
    boxShadow: '0 1px 2px rgba(27,47,85,0.18), 0 8px 18px rgba(27,47,85,0.12), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  digits: { flexDirection: 'row', alignItems: 'center', width: 26, justifyContent: 'flex-end' },
  wheel: { height: DIGIT_H, overflow: 'hidden' },
  digit: { height: DIGIT_H, color: NAVY, fontSize: 18, lineHeight: DIGIT_H, fontWeight: '800', fontVariant: ['tabular-nums'] },
  title: { color: NAVY, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 16 },
  subtitle: { color: MUTED, fontSize: 16, fontWeight: '500', marginTop: 4 },
  star: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 88,
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  starText: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '900',
    textShadowColor: 'rgba(27,47,85,0.45)',
    textShadowOffset: { width: 0, height: 1.5 },
    textShadowRadius: 3,
  },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    padding: 16,
    gap: 12,
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: '#FBF8FF',
    boxShadow: '0 2px 4px rgba(27,30,70,0.18), 0 22px 44px rgba(27,30,70,0.2), inset 0 1px 0 rgba(255,255,255,1)',
  },
  cardRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  shell: {
    width: 44,
    height: 44,
    borderRadius: 14,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFEDE5',
  },
  cardText: { flex: 1, gap: 2 },
  upNext: { color: '#8A7FA6', fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  nextTitle: { color: NAVY, fontSize: 17, fontWeight: '700' },
  continue: {
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: CORAL,
    boxShadow: '0 1px 2px rgba(160,50,30,0.35), 0 8px 18px rgba(240,100,74,0.35), inset 0 1px 0 rgba(255,255,255,0.3)',
  },
  continueText: { color: '#FFF8F4', fontSize: 17, fontWeight: '700' },
});
