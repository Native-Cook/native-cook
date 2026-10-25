import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { FRAME_H, MAP_MIN, drawMoving, makeRailGeo, recordStatic } from './railwayArt';
import { GOLD, INK, MUTED, PAGES } from './RailwayPages';
import { COUNT, LOOP, type RailPose, SECTIONS, TOUCH_PULL, TOUCH_RUN, TRIPS, loopPose, loopTrip, tripLength, tripPose } from './railwayMotion';

/*
 Railway Switch Nav — "Northline", a commuter app whose bottom navigation is a little signal box:
 four painted levers with steel handles (Home, Trips, Tickets, Account) under an enamel track
 diagram. Pulling a lever sets a route: the two switch blades throw, the section's signal and
 lever lamp turn from red to green, the route lights up, a two-car train runs from the current
 section's platform to the new one, and the page slides in from the side the train went. All the
 machinery is Skia drawing in one Canvas (railwayArt.ts); the pages are views.

 autoPlay (LOOP = 9 s, railwayMotion.ts; a "trip" is one lever pull):
   0.0 – 1.2   rest at Home.
   1.2 – 2.0   pull Tickets (4 % anticipation, 5 % overshoot into the notch); Home's lever returns;
               the switches throw (0.3 s), the signal turns green, the route lights.
   2.0 – 3.4   the train runs Home → Tickets (ease-in-out); the Tickets page slides in a full page
               width from the right 3.0 – 3.6 while Home's slides fully out to the left (both
               clipped to the page area); the route fades on arrival.
   4.4 – 6.4   pull Trips (0.6 s), the train runs back left 5.0 – 6.2, the page comes from the left.
   6.8 – 8.4   pull Home, the train returns 7.4 – 8.4, Home's page from the left; rest to 9.0.

 Touch: drag a lever down (it follows the finger, rubber-banding past its stop) or tap it; past
 40 % it commits the trip (a light haptic, another when the train arrives), short of that it
 springs back. A pull is taken once the last train has arrived. 2 s after the last touch the
 train goes home on a quick trip (0.9 s) and the loop starts again.
 Reduced motion: no loop; a lever pull changes the page and lamps with short fades, no train run.
 */

const IDLE_S = 2;
/** The pages' height when the screen has room; on a short screen they get what is left. */
const PAGE_H = 320;
const HOME_PULL = 0.3;
const HOME_RUN = 0.55;

function light() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tickHaptic() {
  Haptics.selectionAsync().catch(() => {});
}

function Page({ k, pose, width, room }: { k: number; pose: SharedValue<RailPose>; width: number; room: number }) {
  const Content = PAGES[k];
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const dir = p.to > p.from ? 1 : -1;
    if (p.from === p.to) return { opacity: k === p.to ? 1 : 0, transform: [{ translateX: 0 }] };
    // The old page slides fully out (clipped by the page area) as the new one slides in beside it.
    const span = width + 24;
    if (k === p.to) return { opacity: 1, transform: [{ translateX: dir * span * (1 - p.page) }] };
    if (k === p.from) return { opacity: p.page < 1 ? 1 : 0, transform: [{ translateX: -dir * span * p.page }] };
    return { opacity: 0, transform: [{ translateX: 0 }] };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]} pointerEvents="none">
      <Content room={room} />
    </Animated.View>
  );
}

function LeverLabel({ k, x, top, pose }: { k: number; x: number; top: number; pose: SharedValue<RailPose> }) {
  const on = useAnimatedStyle(() => ({ opacity: pose.get().signals[k] }));
  return (
    <View style={[styles.plate, { left: x - 36, top }]}>
      <Text style={styles.plateText}>{SECTIONS[k].toUpperCase()}</Text>
      <Animated.View style={[StyleSheet.absoluteFill, styles.plateCenter, on]}>
        <Text style={[styles.plateText, styles.plateOn]}>{SECTIONS[k].toUpperCase()}</Text>
      </Animated.View>
    </View>
  );
}

export function RailwaySwitchNav({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const { width, height } = useWindowDimensions();
  // The pages take up to PAGE_H; the track diagram (the hero) grows into the rest, 150 – 240 pt.
  const pageTop = insets.top + 64;
  const mapBottom = height - insets.bottom - FRAME_H - 12;
  const mapH = Math.max(MAP_MIN, Math.min(240, mapBottom - pageTop - 16 - PAGE_H));
  const mapTop = mapBottom - mapH;
  const pageH = mapTop - 16 - pageTop;
  const g = useMemo(() => makeRailGeo(width, height, mapTop, mapH), [width, height, mapTop, mapH]);
  const still = useMemo(() => recordStatic(g), [g]);

  const t = useSharedValue(0);
  const manual = useSharedValue(auto ? 0 : 1);
  const clock = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  // The finger's trip: [start (clock s), pull, run, from, to, pullFrom].
  const trip = useSharedValue<number[]>([-100, 0.6, 1.0, 1, 0, 0]);
  const drag = useSharedValue(-1);
  const dragAmount = useSharedValue(0);
  const dragStart = useSharedValue<number[]>([0, 0, 0]);
  const crossed = useSharedValue(0);

  function startTrip(to: number, pull: number, run: number, pullFrom: number) {
    'worklet';
    const tr = trip.get();
    trip.set([clock.get(), pull, run, tr[4], to, pullFrom]);
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    clock.set(clock.get() + dt);
    if (manual.get() === 0) {
      t.set((t.get() + dt) % LOOP);
      return;
    }
    const at = resumeAt.get();
    if (at > 0 && clock.get() >= at && drag.get() < 0) {
      const tr = trip.get();
      if (tr[4] !== 0) {
        startTrip(0, HOME_PULL, HOME_RUN, 0);
        resumeAt.set(clock.get() + tripLength(HOME_PULL, HOME_RUN));
      } else {
        resumeAt.set(-1);
        t.set(0);
        manual.set(0);
      }
    }
  });

  const pose = useDerivedValue<RailPose>(() => {
    if (manual.get() === 0) return loopPose(t.get());
    const tr = trip.get();
    const tau = reduced ? 100 : clock.get() - tr[0];
    const p = tripPose(tau, tr[1], tr[2], tr[3] === tr[4] ? (tr[4] === 0 ? 1 : 0) : tr[3], tr[4], tr[5]);
    const d = drag.get();
    if (d >= 0) {
      const levers = p.levers.slice();
      levers[d] = dragAmount.get();
      return { levers, blades: p.blades, signals: p.signals, from: p.from, to: p.to, route: p.route, run: p.run, page: p.page };
    }
    return p;
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawMoving(canvas, g, pose.get());
    return recorder.finishRecordingAsPicture();
  });

  // A light haptic when a finger's train arrives.
  useAnimatedReaction(
    () => (manual.get() === 1 && pose.get().run > 0.995 ? 1 : 0),
    (now, before) => {
      if (now === 1 && before === 0 && resumeAt.get() > 0 && trip.get()[4] !== 0) scheduleOnRN(light);
    },
  );

  // Touch ----------------------------------------------------------------------------------------

  function takeOver() {
    'worklet';
    resumeAt.set(-1);
    if (manual.get() === 0) {
      const lt = loopTrip(t.get());
      const tr = TRIPS[lt[0]];
      trip.set([clock.get() - lt[1], tr[1], tr[2], tr[3], tr[4], 0]);
      manual.set(1);
    }
  }

  function leverAt(x: number) {
    'worklet';
    let best = 0;
    for (let k = 1; k < COUNT; k++) if (Math.abs(x - g.xs[k]) < Math.abs(x - g.xs[best])) best = k;
    return best;
  }

  function settled() {
    'worklet';
    const tr = trip.get();
    return clock.get() - tr[0] >= tr[1] + tr[2];
  }

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          takeOver();
          const k = leverAt(e.x);
          dragStart.set([e.x, e.y, clock.get()]);
          crossed.set(0);
          if (k !== trip.get()[4] && settled()) {
            drag.set(k);
            dragAmount.set(0);
          } else {
            drag.set(-1);
          }
        })
        .onUpdate((e) => {
          if (drag.get() < 0) return;
          const raw = (e.y - dragStart.get()[1]) / 70;
          const over = Math.max(0, raw - 1);
          const amount = Math.max(-0.06, Math.min(raw, 1) + (0.5 * over) / (1 + over));
          dragAmount.set(amount);
          if (amount > 0.4 && crossed.get() === 0) {
            crossed.set(1);
            scheduleOnRN(tickHaptic);
          }
        })
        .onFinalize((e) => {
          const k = drag.get();
          const s = dragStart.get();
          if (k >= 0) {
            const tap = Math.hypot(e.x - s[0], e.y - s[1]) < 10 && clock.get() - s[2] < 0.35;
            const amount = dragAmount.get();
            if (amount > 0.4 || tap) {
              startTrip(k, TOUCH_PULL, TOUCH_RUN, tap ? 0 : Math.min(1, amount));
              drag.set(-1);
              scheduleOnRN(light);
            } else {
              dragAmount.set(
                withSpring(0, { duration: 380, dampingRatio: 0.7 }, (finished) => {
                  if (finished) drag.set(-1);
                }),
              );
            }
          }
          if (auto) resumeAt.set(clock.get() + IDLE_S + 0.01);
        }),
    // The shared values are stable; the gesture depends on the geometry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [g, auto, reduced],
  );


  return (
    <View style={styles.screen}>
      <LinearGradient colors={['#18251F', '#131D19', '#0E1512']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      <View style={[styles.header, { top: insets.top + 12 }]}>
        <View style={styles.brand}>
          <View style={styles.logo}>
            <View style={styles.logoRail} />
            <View style={[styles.logoRail, { transform: [{ rotate: '-35deg' }], top: 9 }]} />
          </View>
          <Text style={styles.brandText}>Northline</Text>
        </View>
        <View style={styles.live}>
          <View style={styles.liveDot} />
          <Text style={styles.liveText}>Live · Elm Cross</Text>
        </View>
      </View>

      <View style={[styles.pages, { top: pageTop, height: Math.max(160, pageH) }]}>
        {PAGES.map((_, k) => (
          <Page key={k} k={k} pose={pose} width={width - 48} room={pageH} />
        ))}
      </View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={still} />
        <Picture picture={picture} />
      </Canvas>

      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Text style={[styles.boxLabel, { left: g.px + 24, top: g.py + g.ph - 22 }]}>BOX 4 · ELM CROSS JUNCTION</Text>
        {SECTIONS.map((s, k) => (
          <Text key={s} style={[styles.station, { left: g.xs[k] - 82, top: g.top + g.plat + 6 }]}>
            {s.toUpperCase()}
          </Text>
        ))}
        {SECTIONS.map((s, k) => (
          <LeverLabel key={s} k={k} x={g.xs[k]} top={g.pivot + 12} pose={pose} />
        ))}
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.levers, { top: g.frame }]}
          accessible
          accessibilityRole="tablist"
          accessibilityLabel="Sections: Home, Trips, Tickets, Account. Pull a lever to go there."
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#131D19', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  logo: { width: 28, height: 28, borderRadius: 9, borderCurve: 'continuous', backgroundColor: GOLD, overflow: 'hidden' },
  logoRail: { position: 'absolute', left: 4, right: 4, top: 13, height: 3, borderRadius: 1.5, backgroundColor: '#1A2420' },
  brandText: { color: INK, fontSize: 20, fontWeight: '700', letterSpacing: -0.3 },
  live: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: 'rgba(236, 244, 236, 0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(236, 244, 236, 0.16)',
  },
  liveDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: '#7BE39A' },
  liveText: { color: MUTED, fontSize: 12, fontWeight: '600' },
  pages: { position: 'absolute', left: 24, right: 24, overflow: 'hidden' },
  boxLabel: { position: 'absolute', color: 'rgba(232, 224, 200, 0.45)', fontSize: 9, fontWeight: '700', letterSpacing: 1.6 },
  station: { position: 'absolute', width: 56, textAlign: 'right', color: 'rgba(232, 224, 200, 0.6)', fontSize: 9, fontWeight: '700', letterSpacing: 1.1 },
  plate: { position: 'absolute', width: 72, height: 24, alignItems: 'center', justifyContent: 'center' },
  plateCenter: { alignItems: 'center', justifyContent: 'center' },
  plateText: { color: 'rgba(60, 40, 10, 0.5)', fontSize: 10, fontWeight: '800', letterSpacing: 1.2 },
  plateOn: { color: '#2B1C05' },
  levers: { position: 'absolute', left: 0, right: 0, bottom: 0 },
});
