import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
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
import { scheduleOnRN } from 'react-native-worklets';

import {
  BLOBS,
  CAP_END,
  GLASS_END,
  LOOP,
  type Sim,
  TRACKS_ALL,
  TRACKS_FROM,
  blendPose,
  easeInOut,
  loopPose,
  makeLayout,
  makeSim,
  stepSim,
  toLiquidX,
  tracksAt,
} from './lavaLampMotion';
import { LAVA_SKSL } from './lavaLampShader';

/*
 Lava Lamp Loader — "Glowhaus", a music-sync screen whose loader is a lava lamp on a walnut shelf.
 Wax heats on the pool at the bottom, swells, necks off and rises; it merges with the blob under
 the cap, splits, and the cooled half sinks back. The tracks counter clicks up as it syncs.

 Drawing: one Canvas, one RuntimeEffect (lavaLampShader.ts) for the wall, shelf, chrome and the
 metaball wax; the shader's blob uniforms come from one pose array computed per frame on the UI
 thread (lavaLampMotion.ts). Text and the card are views reading shared values: nothing in React
 re-renders while the loop runs or under a finger.

 Touch: a finger on the glass warms the wax there: a blob grows under it (about 1 s) and follows
 it; the other blobs are pushed away from the finger and dragged along its motion (soft springs,
 the wax is slow). Let go and the warmed blob rises to the cap. 2 s after the last touch the wax
 eases back to its resting pose (0.9 s, the warmed blob melts away) and the loop starts again.
 Reduced motion: the resting lamp, no loop; the counter shows its value.
*/

type Props = {
  /** Plays the 9 s rise-merge-sink loop of the wax. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
/** Seconds after the last touch before the lamp eases back, and how long that takes. */
const IDLE = 2;
const RETURN = 0.9;
const POSE_LEN = BLOBS * 4;

const INK = '#F6E7DA';
const INK_SOFT = 'rgba(246,231,218,0.62)';
const INK_FAINT = 'rgba(246,231,218,0.4)';
const AMBER = '#FF9A4A';
const effect = Skia.RuntimeEffect.Make(LAVA_SKSL);

function emptyPose() {
  const p: number[] = [];
  for (let i = 0; i < POSE_LEN; i += 1) p.push(0);
  return p;
}

/** A zeroed pose to write the rest frame into (UI thread). */
function emptyRest(n: number) {
  'worklet';
  const r: number[] = [];
  for (let i = 0; i < n; i += 1) r.push(0);
  return r;
}

function feel() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function LavaLampLoader({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const L = useMemo(() => makeLayout(size.width, size.height, insets.top, insets.bottom), [size.width, size.height, insets.top, insets.bottom]);

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const pose = useSharedValue<number[]>(emptyPose());
  const from = useSharedValue<number[]>(emptyPose());
  const sim = useSharedValue<Sim | null>(null);
  const tracks = useSharedValue(TRACKS_FROM);
  const tracksFrom = useSharedValue(TRACKS_FROM);
  const warm = useSharedValue(0);
  const spin = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min((f.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const out = pose.get().slice();
    const m = mode.get();
    if (m === MODE_AUTO) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      loopPose(t, L, out);
      tracks.set(auto ? tracksAt(t) : TRACKS_FROM);
      warm.set(warm.get() * Math.exp(-dt * 3));
    } else if (m === MODE_USER) {
      const s = sim.get();
      if (s) {
        stepSim(s, dt, L);
        if (s.topped === 1) {
          s.topped = 2;
          scheduleOnRN(feel);
        }
        for (let i = 0; i < POSE_LEN; i += 1) out[i] = s.pos[i];
        warm.set(warm.get() + ((s.down ? 1 : 0) - warm.get()) * Math.min(1, dt * 3));
        if (!s.down && at - lastTouch.get() > IDLE) {
          from.set(out.slice());
          tracksFrom.set(tracks.get());
          returnAt.set(at);
          mode.set(MODE_RETURN);
        }
      }
    } else {
      const e = easeInOut((at - returnAt.get()) / RETURN);
      const rest = emptyRest(out.length);
      loopPose(0, L, rest);
      blendPose(from.get(), rest, e, out);
      tracks.set(tracksFrom.get() + (TRACKS_FROM - tracksFrom.get()) * e);
      warm.set(warm.get() * Math.exp(-dt * 3));
      if (e >= 1) {
        mode.set(MODE_AUTO);
        clock.set(0);
      }
    }
    pose.set(out);
    if (!reduced) spin.set((spin.get() + dt * 2.4) % (Math.PI * 2));
  });

  /** A screen point to the liquid's coordinates, or null outside the glass. */
  function liquidPoint(x: number, y: number) {
    'worklet';
    const ly = y - L.top;
    if (ly < L.H * CAP_END - 20 || ly > L.H * GLASS_END + 30) return null;
    return [toLiquidX(x - L.cx, Math.max(L.H * CAP_END, Math.min(L.H * GLASS_END, ly)), L.H, L.k), ly];
  }

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced)
    .onBegin((e) => {
      const p = liquidPoint(e.x, e.y);
      if (!p) return;
      lastTouch.set(now.get());
      const s = sim.get();
      if (mode.get() === MODE_USER && s) {
        s.down = 1;
        s.fx = p[0];
        s.fy = p[1];
        // The warmed blob picks up again under the new touch.
        s.pos[20] = p[0];
        s.pos[21] = p[1];
      } else {
        sim.set(makeSim(pose.get(), p[0], p[1]));
        mode.set(MODE_USER);
      }
      scheduleOnRN(feel);
    })
    .onUpdate((e) => {
      const s = sim.get();
      if (!s || mode.get() !== MODE_USER || !s.down) return;
      const p = liquidPoint(e.x, Math.max(L.top + L.H * CAP_END, Math.min(L.top + L.H * GLASS_END, e.y)));
      if (!p) return;
      s.fx = p[0];
      s.fy = p[1];
      s.fvx = e.velocityX;
      s.fvy = e.velocityY;
      lastTouch.set(now.get());
    })
    .onFinalize(() => {
      const s = sim.get();
      if (!s || mode.get() !== MODE_USER) return;
      s.down = 0;
      s.fvx = 0;
      s.fvy = 0;
      s.topped = 0;
      lastTouch.set(now.get());
    });

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    return {
      size: [size.width, size.height],
      lamp: [L.cx, L.top, L.H, L.k],
      b0: [p[0], p[1], p[2], p[3]],
      b1: [p[4], p[5], p[6], p[7]],
      b2: [p[8], p[9], p[10], p[11]],
      b3: [p[12], p[13], p[14], p[15]],
      b4: [p[16], p[17], p[18], p[19]],
      bf: [p[20], p[21], p[22], p[23]],
      glow: 1 + 0.18 * warm.get(),
      phase: (2 * Math.PI * clock.get()) / LOOP,
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A lava lamp. Touch the glass to warm the wax; drag to push the blobs.">
          {effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <Text style={styles.kicker}>GLOWHAUS</Text>
        <Text style={styles.title}>Syncing your library</Text>
        <TrackCount tracks={tracks} />
        <Progress tracks={tracks} />
      </View>

      <Text pointerEvents="none" style={[styles.hint, { top: L.shelf + 26 }]}>
        Touch the glass to warm the wax
      </Text>

      <View pointerEvents="none" style={[styles.card, { bottom: insets.bottom + 16 }]}>
        <LinearGradient colors={['#FFB36B', '#E0482F', '#6E1838']} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.art}>
          <View style={styles.artSun} />
        </LinearGradient>
        <View style={styles.cardText}>
          <Text style={styles.cardTitle} numberOfLines={1}>Low Tide Hymns</Text>
          <Text style={styles.cardSub} numberOfLines={1}>Marlow Fenn · 14 of 22 tracks</Text>
        </View>
        <SyncIcon spin={spin} />
      </View>
    </View>
  );
}

const DIGIT_H = 22;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

/** One column of the odometer: the ones turn with the value, higher places as the one below passes 9. */
function Column({ place, tracks }: { place: number; tracks: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = Math.max(0, tracks.get());
    const m = v / place;
    const base = Math.floor(m);
    const pos = place === 1 ? m : base + Math.max(0, (m - base) * 10 - 9);
    return { transform: [{ translateY: -(pos % 10) * DIGIT_H }] };
  });
  return (
    <View style={styles.column}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>{d}</Text>
        ))}
      </Animated.View>
    </View>
  );
}

function TrackCount({ tracks }: { tracks: SharedValue<number> }) {
  return (
    <View style={styles.countRow}>
      <Column place={1000} tracks={tracks} />
      <Text style={styles.digitStatic}>,</Text>
      <Column place={100} tracks={tracks} />
      <Column place={10} tracks={tracks} />
      <Column place={1} tracks={tracks} />
      <Text style={styles.countRest}>{` / ${TRACKS_ALL.toLocaleString('en-US')} tracks`}</Text>
    </View>
  );
}

function Progress({ tracks }: { tracks: SharedValue<number> }) {
  const fill = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.min(1, tracks.get() / TRACKS_ALL) }] }));
  return (
    <View style={styles.track}>
      <Animated.View style={[styles.fill, fill]} />
    </View>
  );
}

function SyncIcon({ spin }: { spin: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ transform: [{ rotate: `${spin.get()}rad` }] }));
  return (
    <View style={styles.syncBox}>
      <Animated.View style={style}>
        <SymbolView
          name={{ ios: 'arrow.triangle.2.circlepath', android: 'sync', web: 'sync' }}
          size={18}
          weight="semibold"
          tintColor={AMBER}
          fallback={<View style={styles.syncFallback} />}
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#1A0D10' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: AMBER, fontSize: 11, fontWeight: '800', letterSpacing: 2.2 },
  title: { color: INK, fontSize: 30, fontWeight: '800', letterSpacing: -0.6 },
  countRow: { flexDirection: 'row', alignItems: 'center', height: DIGIT_H, marginTop: 2 },
  column: { height: DIGIT_H, width: 10, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: INK, fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'], textAlign: 'center' },
  digitStatic: { color: INK, fontSize: 16, fontWeight: '600', lineHeight: DIGIT_H },
  countRest: { color: INK_SOFT, fontSize: 16, fontWeight: '500', lineHeight: DIGIT_H, fontVariant: ['tabular-nums'] },
  track: { height: 4, borderRadius: 2, marginTop: 12, backgroundColor: 'rgba(246,231,218,0.12)', overflow: 'hidden' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, width: '100%', borderRadius: 2, backgroundColor: AMBER, transformOrigin: 'left' },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: INK_FAINT, fontSize: 12, fontWeight: '600', letterSpacing: 0.3 },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 76,
    borderRadius: 22,
    borderCurve: 'continuous',
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: 'rgba(38,20,22,0.92)',
    borderWidth: 1,
    borderColor: 'rgba(255,200,170,0.08)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 16px 36px rgba(0,0,0,0.42), inset 0 1px 0 rgba(255,230,210,0.08)',
  },
  art: { width: 48, height: 48, borderRadius: 12, borderCurve: 'continuous', overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  artSun: { width: 22, height: 22, borderRadius: 11, backgroundColor: 'rgba(255,236,200,0.55)', marginTop: 10 },
  cardText: { flex: 1, gap: 3 },
  cardTitle: { color: INK, fontSize: 16, fontWeight: '700' },
  cardSub: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  syncBox: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,154,74,0.12)' },
  syncFallback: { width: 16, height: 16, borderRadius: 8, borderWidth: 2, borderColor: AMBER, borderTopColor: 'transparent' },
});
