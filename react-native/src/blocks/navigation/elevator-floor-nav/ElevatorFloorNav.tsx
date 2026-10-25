import { Canvas, Picture, type SkImage, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, type Layers, STORE, bakeDoor, bakeFrame, bakeLobby, bakePoster, drawFrame, makeScene } from './elevatorArt';
import {
  BACK_PACE,
  FLOORS,
  IDLE_BEFORE_BACK,
  LOOP,
  type Pose,
  type Trip,
  loopPose,
  mergeBell,
  restAt,
  tripDone,
  tripPose,
} from './elevatorMotion';

/*
 Elevator Floor Nav — "Merrow & Hale", a department store whose section navigation is its lift.

 Look: a green marble lobby; above the doorway an enamel floor dial (1 – 5) on a brass half-moon
 with a sunburst and two hall lanterns; the doorway itself in a brass surround, showing the floor
 the car is on (each a poster drawn in code: "Shoes", "Fashion", "Beauty", "Home", "Café", with
 three arched display windows and prices); two brushed-steel doors with vision panels; below, a
 brass panel of five glass buttons with the sections' names.

 Tap a floor: its button dips and lights amber, the doors close (0.6 s, a small bump as they
 meet), the car rides 0.4 s a floor (smootherstep) while the store slides by behind the vision
 panels, blurred with speed, and the needle follows; on arrival the lantern of the direction
 glows, a ring of light leaves the dial, the button goes out, the doors open on the new section
 and the header rolls to its name. A floor called during a trip is queued. 2 s after the last
 touch the car goes home to floor 1 at a quick pace and the loop starts again from t = 0
 (elevatorMotion.ts, LOOP = 9 s). Reduced motion: no loop; a floor opens at once.
*/

type Props = {
  /** Plays the 9 s loop (to Home and back to Shoes) until touched. */
  autoPlay?: boolean;
};

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const INK = '#F3EAD7';
const BRASS = '#D9B66E';

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;
const LABEL_H = 18;

function buzz(kind: 'press' | 'arrive') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'press') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

/** One floor's name in the header: rolls up out, or in from below, when the car arrives. */
function FloorLabel({ pose, floor }: { pose: SharedValue<Pose>; floor: number }) {
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    let y = LABEL_H;
    let a = 0;
    if (p.labelTo === floor) {
      y = (1 - p.labelK) * LABEL_H;
      a = p.labelK;
    } else if (p.labelFrom === floor && p.labelFrom !== p.labelTo) {
      y = -p.labelK * LABEL_H;
      a = 1 - p.labelK;
    }
    return { opacity: a, transform: [{ translateY: y }] };
  });
  return (
    <Animated.Text style={[styles.floorLabel, style]} numberOfLines={1}>
      Floor {floor} · {STORE[floor - 1].name}
    </Animated.Text>
  );
}

export function ElevatorFloorNav({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const scene = useMemo(() => (size ? makeScene(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: SERIF, fontSize: 50, fontWeight: '400' }),
      caps: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
      body: matchFont({ fontFamily: SANS, fontSize: 14, fontWeight: '400' }),
      item: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '600' }),
      price: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '400' }),
      numeral: matchFont({ fontFamily: SERIF, fontSize: 16, fontWeight: '700' }),
      button: matchFont({ fontFamily: SERIF, fontSize: 19, fontWeight: '700' }),
    }),
    [],
  );
  // What the resting frame needs is baked at once (lobby, doors, frame, floor 1); the other
  // floors follow one per tick after the first paint. 2× is plenty for art under motion and glass.
  const scale = Math.min(2, PixelRatio.get());
  const base = useMemo(() => {
    if (!scene) return null;
    const lobby = bakeLobby(scene, fonts, scale);
    const doorL = bakeDoor(scene, true, scale);
    const doorR = bakeDoor(scene, false, scale);
    const frame = bakeFrame(scene, scale);
    const first = bakePoster(scene, 0, fonts, scale);
    if (!lobby || !doorL || !doorR || !frame || !first) return null;
    return { scene, lobby, doorL, doorR, frame, first };
  }, [scene, fonts, scale]);
  const [later, setLater] = useState<{ scene: unknown; posters: (SkImage | null)[] }>({ scene: null, posters: [] });
  useEffect(() => {
    if (!base) return;
    let cancelled = false;
    // Floor 4 first: the loop rides there.
    const order = [3, 1, 2, 4].filter((f) => f < FLOORS);
    const posters: (SkImage | null)[] = new Array(FLOORS).fill(null);
    let timer: ReturnType<typeof setTimeout> | null = null;
    const next = (k: number) => {
      if (cancelled || k >= order.length) return;
      posters[order[k]] = bakePoster(base.scene, order[k], fonts, scale);
      setLater({ scene: base.scene, posters: posters.slice() });
      timer = setTimeout(() => next(k + 1), 16);
    };
    const frameId = requestAnimationFrame(() => {
      timer = setTimeout(() => next(0), 32);
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(frameId);
      if (timer) clearTimeout(timer);
    };
  }, [base, fonts, scale]);
  const layers = useMemo<Layers | null>(() => {
    if (!base) return null;
    const posters: (SkImage | null)[] = [base.first];
    for (let f = 1; f < FLOORS; f += 1) posters.push(later.scene === base.scene ? (later.posters[f] ?? null) : null);
    return { lobby: base.lobby, posters, doorL: base.doorL, doorR: base.doorR, frame: base.frame };
  }, [base, later]);

  const now = useSharedValue(0);
  /** The frame clock, kept running for gestures even while nothing is drawn. */
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const lastTouch = useSharedValue(0);
  const trip = useSharedValue<Trip>({ from: 1, to: 1, p0: -99, pace: 1 });
  const prev = useSharedValue<Trip>({ from: 1, to: 1, p0: -99, pace: 1 });
  const pending = useSharedValue(0);
  /** Under reduced motion the floor simply changes. */
  const still = useSharedValue(1);

  const pose = useDerivedValue<Pose>(() => {
    const at = now.get();
    if (reduced && mode.get() === AUTO) return restAt(still.get());
    if (mode.get() !== AUTO) return mergeBell(tripPose(trip.get(), at), tripPose(prev.get(), at));
    if (!auto) return restAt(1);
    return loopPose((((at - loopStart.get()) % LOOP) + LOOP) % LOOP);
  });

  function startTrip(to: number, at: number, pace: number) {
    'worklet';
    const cur = trip.get();
    prev.set(cur);
    trip.set({ from: cur.to, to, p0: at, pace });
  }

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    clock.set(at);
    if (loopStart.get() < 0) loopStart.set(at);
    if (!auto && mode.get() === AUTO && now.get() > 0) return;
    now.set(at);
    const m = mode.get();
    if (m === AUTO) return;
    const tr = trip.get();
    if (!tripDone(tr, at)) return;
    if (pending.get() > 0) {
      const to = pending.get();
      pending.set(0);
      if (to !== tr.to) startTrip(to, at, 1);
      return;
    }
    const bell = pose.get().ding;
    if (m === TOUCH && at - lastTouch.get() > IDLE_BEFORE_BACK) {
      mode.set(BACK);
      if (tr.to !== 1) startTrip(1, at, BACK_PACE);
    } else if (m === BACK && Math.max(bell[0], bell[1]) < 0.04) {
      mode.set(AUTO);
      loopStart.set(at);
    }
  });

  // A light tick when a trip the finger called arrives.
  useAnimatedReaction(
    () => (mode.get() === TOUCH ? Math.max(pose.get().ding[0], pose.get().ding[1]) > 0.8 : false),
    (on, was) => {
      if (on && !was) scheduleOnRN(buzz, 'arrive');
    },
  );

  /** Hands the loop's trip to the finger's clock, so nothing jumps. */
  function takeOver(at: number) {
    'worklet';
    if (mode.get() === TOUCH) return;
    if (mode.get() === AUTO) {
      const t = (((at - loopStart.get()) % LOOP) + LOOP) % LOOP;
      if (t >= 1.3 && t < 5.4) {
        trip.set({ from: 1, to: 4, p0: at - (t - 1.3), pace: 1 });
        prev.set({ from: 4, to: 1, p0: at - (t + LOOP - 5.4), pace: 1 });
      } else {
        const back = t >= 5.4 ? t - 5.4 : t + LOOP - 5.4;
        trip.set({ from: 4, to: 1, p0: at - back, pace: 1 });
        prev.set({ from: 1, to: 4, p0: at - back - 4.1, pace: 1 });
      }
    }
    mode.set(TOUCH);
  }

  const call = (floor: number) => {
    buzz('press');
    scheduleOnUI(() => {
      'worklet';
      const at = clock.get();
      lastTouch.set(at);
      if (reduced) {
        still.set(floor);
        return;
      }
      takeOver(at);
      // A floor called while the car is busy waits for this trip to finish.
      if (tripDone(trip.get(), at)) startTrip(floor, at, 1);
      else pending.set(floor);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    if (scene && layers) drawFrame(canvas, scene, layers, pose.get(), fonts);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {scene && layers ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 8 }]}>
        <Text style={styles.wordmark}>MERROW & HALE</Text>
        <View style={styles.labelWindow}>
          {[1, 2, 3, 4, 5].map((f) => (
            <FloorLabel key={f} pose={pose} floor={f} />
          ))}
        </View>
      </View>

      {scene
        ? scene.buttonX.map((x, i) => (
            <Pressable
              key={i}
              accessibilityRole="button"
              accessibilityLabel={`Floor ${i + 1}, ${STORE[i].name}`}
              onPress={() => call(i + 1)}
              hitSlop={6}
              style={[styles.button, { left: x - scene.buttonR - 4, top: scene.buttonY - scene.buttonR - 4, width: scene.buttonR * 2 + 8, height: scene.buttonR * 2 + 8 }]}
            />
          ))
        : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#10221C', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, alignItems: 'center', gap: 4 },
  wordmark: { color: INK, fontFamily: SERIF, fontSize: 17, letterSpacing: 4.5 },
  labelWindow: { height: LABEL_H + 2, alignSelf: 'stretch', overflow: 'hidden' },
  floorLabel: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: BRASS,
    fontSize: 13,
    lineHeight: LABEL_H,
    fontWeight: '600',
    letterSpacing: 0.4,
  },
  button: { position: 'absolute', borderRadius: 40 },
});
