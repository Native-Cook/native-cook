import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  useAnimatedReaction,
  withTiming,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  backFrame,
  beginBack,
  DETACH_AT,
  dropletRadius,
  IDLE_BEFORE_BACK,
  liveFrame,
  LOOP,
  makeMetal,
  type Metal,
  MODE_AUTO,
  MODE_BACK,
  MODE_LIVE,
  scriptFrame,
  TETHER_K,
} from './liquidMetalMotion';
import { LIQUID_METAL_SKSL } from './liquidMetalShader';

/*
 Liquid Metal Morph — "Forge", a mode switch whose icon is a blob of liquid chrome.

 Look: a graphite backdrop (#1e2026 → #121316, 4 % grain, vignette). In the middle a mercury blob
 drawn by one runtime shader (liquidMetalShader.ts): a signed distance field shaded as chrome that
 reflects a studio gradient (#0b0c10, #9aa3b5, #f2f4f8, #3a3f4a), with a Fresnel rim, a key
 highlight and a faint chromatic split. It morphs drop → four-point star → ring → drop; during each
 morph four satellite droplets pinch off and merge back. Under it the state crossfades
 ("Melting" / "Casting" / "Polishing") with three progress dots; the bottom holds a segmented
 control (Drop, Star, Ring) whose pill slides with the morph.

 autoPlay (LOOP = 9 s, liquidMetalMotion.ts): 0–1.6 rest (drop, slow wobble), 1.6–3.2 drop → star,
 3.4–4.6 hold, 4.6–6.2 star → ring, 6.2–7.0 hold, 7.0–8.4 ring → drop, a 0.6 s jiggle after each
 morph, 8.4–9 rest. Everything is a function of t.

 Touch: drag from inside the blob to pull a droplet out on a smooth-min tether (it thins with
 distance; a light haptic at 120 pt); let go and it flies home on a spring with the finger's
 velocity. Tap a segment to morph on a playful spring. 2 s after the last touch the blob eases back
 to the drop (0.85 s) and the loop starts again from t = 0. Reduced motion: no loop, no wobble;
 the droplet and the segments still answer the finger.
 */

type Props = {
  /** Plays the 9 s morph loop until touched. */
  autoPlay?: boolean;
};

const BACKGROUND = '#16171b';
const INK = '#e9ebf0';
const MUTE = 'rgba(233,235,240,0.56)';
const FAINT = 'rgba(233,235,240,0.32)';

const STATES = [
  { title: 'Melting', line: 'Drop · alloy at 356 °C' },
  { title: 'Casting', line: 'Star · cooling to 210 °C' },
  { title: 'Polishing', line: 'Ring · buffing to a mirror' },
];
const SEGMENTS = ['Drop', 'Star', 'Ring'];
const READINGS = [
  ['356 °C', 'alloy'],
  ['1.5 cP', 'viscosity'],
  ['0.8 s', 'per cast'],
];
const SEGMENT_W = 96;
/** The state label's roll (ms). */
const LABEL_ROLL = 260;
const SEG_IDLE = '#9095a1';
const SEG_ON = '#17181d';

export function LiquidMetalMorph({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(LIQUID_METAL_SKSL), []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const cx = width / 2;
  const cy = height * 0.41;
  const R = Math.min(width * 0.23, 96, height * 0.11);
  // The readings row sits above the footer, where there is room for it.
  const statusBottom = cy + R * 2.05 + 80;
  const footerTop = height - insets.bottom - 24 - 84;
  const showReadings = footerTop - statusBottom > 76;

  const metal = useSharedValue<Metal>(makeMetal(auto, reduced));
  const tick = useSharedValue(0);

  useEffect(() => {
    metal.set(makeMetal(auto, reduced));
  }, [auto, reduced, metal]);

  const buzz = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((frame) => {
    const m = metal.get();
    if (R <= 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    m.now += dt;
    if (m.mode === MODE_AUTO) {
      if (auto) m.t = (m.t + dt) % LOOP;
      scriptFrame(m, m.t, R);
    } else if (m.mode === MODE_LIVE) {
      liveFrame(m, dt);
      if (auto && !m.held && m.now - m.lastTouch > IDLE_BEFORE_BACK) beginBack(m);
    } else if (m.mode === MODE_BACK) {
      if (backFrame(m, dt)) {
        m.mode = MODE_AUTO;
        m.t = 0;
      }
    }
    if (m.buzz) {
      m.buzz = false;
      scheduleOnRN(buzz);
    }
    tick.set(tick.get() + 1);
  });

  // Named worklets (not inline arrows) so they can be called from the gesture callbacks.
  function touched() {
    'worklet';
    const m = metal.get();
    m.lastTouch = m.now;
    if (m.mode !== MODE_LIVE) {
      m.mode = MODE_LIVE;
      m.v = [0, 0, 0];
      // The chip the morph is closest to becomes the target, so the blob stays where it is.
      let best = 0;
      for (let i = 1; i < 3; i += 1) if (m.w[i] > m.w[best]) best = i;
      m.target = best;
    }
  }

  function choose(index: number) {
    'worklet';
    touched();
    metal.get().target = index;
  }

  const pull = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touched();
      const m = metal.get();
      const inside = Math.hypot(e.x - cx, e.y - cy) < R * 1.25;
      if (!inside) return;
      m.held = true;
      m.shown = true;
      m.detached = false;
      m.hx = e.x;
      m.hy = e.y;
      m.fx = e.x;
      m.fy = e.y;
      m.dx = e.x;
      m.dy = e.y;
      m.dvx = 0;
      m.dvy = 0;
    })
    .onUpdate((e) => {
      const m = metal.get();
      m.lastTouch = m.now;
      if (!m.held) return;
      m.fx = e.x;
      m.fy = e.y;
      const dist = Math.hypot(e.x - m.hx, e.y - m.hy);
      if (!m.detached && dist > DETACH_AT) {
        m.detached = true;
        m.buzz = true;
      } else if (m.detached && dist < DETACH_AT * 0.7) {
        m.detached = false;
      }
    })
    .onEnd((e) => {
      const m = metal.get();
      if (!m.held) return;
      m.dvx = e.velocityX;
      m.dvy = e.velocityY;
    })
    .onFinalize(() => {
      const m = metal.get();
      m.held = false;
      m.lastTouch = m.now;
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const m = metal.get();
    const r = m.shown ? dropletRadius(m, R) : 0;
    return {
      size: [Math.max(1, width), Math.max(1, height)],
      center: [cx, cy],
      R: Math.max(R, 1),
      w: [m.w[0], m.w[1], m.w[2]],
      wob: [m.p1, m.p2, m.amp, m.jiggle],
      sats: m.sats.slice(),
      finger: [m.dx, m.dy, r, TETHER_K],
    };
  });

  // The shape weights as their own derived value, so every style below depends on it directly.
  const weights = useDerivedValue(() => {
    tick.get();
    const w = metal.get().w;
    return [w[0], w[1], w[2]];
  });
  const fade = (w: number) => {
    'worklet';
    return Math.min(Math.max((w - 0.35) / 0.4, 0), 1);
  };
  // The state label rolls: the old one goes up and fades, the new one comes in from below
  // (ease-out, 260 ms), whenever the dominant shape changes.
  const active = useDerivedValue(() => {
    const w = weights.get();
    let best = 0;
    for (let i = 1; i < 3; i += 1) if (w[i] > w[best]) best = i;
    return best;
  });
  const shown0 = useSharedValue(1);
  const shown1 = useSharedValue(0);
  const shown2 = useSharedValue(0);
  useAnimatedReaction(
    () => active.get(),
    (now, before) => {
      if (before === null || now === before) return;
      const roll = { duration: LABEL_ROLL, easing: Easing.bezier(0.23, 1, 0.32, 1) };
      // Entering labels start below (0 → 1), leaving ones run on past 1 to above (1 → 2).
      const all = [shown0, shown1, shown2];
      for (let i = 0; i < 3; i += 1) {
        if (i === now) {
          all[i].set(0);
          all[i].set(withTiming(1, roll));
        } else if (i === before) {
          all[i].set(withTiming(2, roll));
        } else {
          all[i].set(0);
        }
      }
    },
  );
  const rollStyle = (v: number) => {
    'worklet';
    // 0 = waiting below, 1 = shown, 2 = gone above.
    return { opacity: v <= 1 ? v : 2 - v, transform: [{ translateY: (1 - v) * 14 }] };
  };
  const label0 = useAnimatedStyle(() => rollStyle(shown0.get()));
  const label1 = useAnimatedStyle(() => rollStyle(shown1.get()));
  const label2 = useAnimatedStyle(() => rollStyle(shown2.get()));
  const labelStyles = [label0, label1, label2];
  const dot0 = useAnimatedStyle(() => ({ opacity: 0.3 + 0.7 * fade(weights.get()[0]), transform: [{ scaleX: 1 + 1.6 * fade(weights.get()[0]) }] }));
  const dot1 = useAnimatedStyle(() => ({ opacity: 0.3 + 0.7 * fade(weights.get()[1]), transform: [{ scaleX: 1 + 1.6 * fade(weights.get()[1]) }] }));
  const dot2 = useAnimatedStyle(() => ({ opacity: 0.3 + 0.7 * fade(weights.get()[2]), transform: [{ scaleX: 1 + 1.6 * fade(weights.get()[2]) }] }));
  const dotStyles = [dot0, dot1, dot2];
  const ink0 = useAnimatedStyle(() => ({ color: interpolateColor(fade(weights.get()[0]), [0, 1], [SEG_IDLE, SEG_ON]) }));
  const ink1 = useAnimatedStyle(() => ({ color: interpolateColor(fade(weights.get()[1]), [0, 1], [SEG_IDLE, SEG_ON]) }));
  const ink2 = useAnimatedStyle(() => ({ color: interpolateColor(fade(weights.get()[2]), [0, 1], [SEG_IDLE, SEG_ON]) }));
  const inkStyles = [ink0, ink1, ink2];
  const pill = useAnimatedStyle(() => {
    const w = weights.get();
    return { transform: [{ translateX: (w[1] + 2 * w[2]) * SEGMENT_W }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pull}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A blob of liquid metal. Drag from it to pull out a droplet."
        >
          {size && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 20 }]}>
        <Text style={styles.kicker}>FORGE</Text>
        <Text style={styles.title}>Casting your workspace</Text>
        <Text style={styles.subtitle}>Liquid alloy preset · 3 modes</Text>
      </View>

      {size ? (
        <View pointerEvents="none" style={[styles.status, { top: cy + R * 2.05 }]}>
          <View style={styles.labels}>
            {STATES.map((s, i) => (
              <Animated.View key={s.title} style={[styles.label, labelStyles[i]]}>
                <Text style={styles.state}>{s.title}</Text>
                <Text style={styles.stateLine}>{s.line}</Text>
              </Animated.View>
            ))}
          </View>
          <View style={styles.dots}>
            {dotStyles.map((d, i) => (
              <Animated.View key={i} style={[styles.dot, d]} />
            ))}
          </View>
        </View>
      ) : null}

      {size && showReadings ? (
        <View pointerEvents="none" style={[styles.readings, { top: footerTop - 24 - 56 }]}>
          {READINGS.map(([value, label]) => (
            <View key={label} style={styles.reading}>
              <Text style={styles.readingValue}>{value}</Text>
              <Text style={styles.readingLabel}>{label}</Text>
            </View>
          ))}
        </View>
      ) : null}

      <View style={[styles.footer, { bottom: insets.bottom + 24 }]}>
        <View style={styles.segmented}>
          <Animated.View pointerEvents="none" style={[styles.pill, pill]} />
          {SEGMENTS.map((label, i) => (
            <Pressable
              key={label}
              accessibilityRole="button"
              accessibilityLabel={`${label} shape`}
              onPress={() => scheduleOnUI(choose, i)}
              style={styles.segment}
            >
              <Animated.Text style={[styles.segmentText, inkStyles[i]]}>{label}</Animated.Text>
            </Pressable>
          ))}
        </View>
        <Text style={styles.hint}>Pull a droplet out of the metal</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { color: FAINT, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  status: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 16 },
  labels: { height: 56, alignSelf: 'stretch' },
  label: { position: 'absolute', left: 0, right: 0, top: 0, alignItems: 'center', gap: 4 },
  state: { color: INK, fontSize: 24, fontWeight: '600', letterSpacing: -0.2 },
  stateLine: { color: MUTE, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  dots: { flexDirection: 'row', gap: 12 },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: INK },
  footer: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 16 },
  segmented: {
    flexDirection: 'row',
    padding: 4,
    borderRadius: 24,
    backgroundColor: 'rgba(40,42,49,0.92)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.1)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 12px 32px rgba(0,0,0,0.28)',
  },
  pill: {
    position: 'absolute',
    left: 4,
    top: 4,
    width: SEGMENT_W,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#d9dde5',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.7), 0 1px 3px rgba(0,0,0,0.35)',
  },
  segment: { width: SEGMENT_W, height: 40, alignItems: 'center', justifyContent: 'center' },
  segmentText: { color: SEG_IDLE, fontSize: 15, fontWeight: '600' },
  hint: { color: FAINT, fontSize: 13, fontWeight: '500' },
  readings: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 56,
    flexDirection: 'row',
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.04)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.09)',
    alignItems: 'center',
  },
  reading: { flex: 1, alignItems: 'center', gap: 2 },
  readingValue: { color: INK, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  readingLabel: { color: FAINT, fontSize: 11, fontWeight: '600', letterSpacing: 0.4 },
});
