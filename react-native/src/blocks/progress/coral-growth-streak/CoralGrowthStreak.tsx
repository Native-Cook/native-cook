// Coral Growth Streak — a habit's week, kept as a reef that grows a ring for every day done.
//
// Tidepool's streak screen: a round window onto a sea bed where a Gray-Scott reaction-diffusion
// simulation on the GPU (gpu/CgsGpu.tsx, constants in cgs-motion.ts) grows brain coral. Each day that
// is completed fills its dot, rolls the count and opens the next ring of the reef with a few seeds;
// the coral fills the ring and stops. The seventh day sends a band of light across it.
//
// autoPlay: LOOP = 10 s. Seven days are completed 0.9 s apart, the bloom crosses, the reef holds,
// then the coral dissolves from the rim inwards while the dots drain; the field is cleared at t = 0,
// so frame 10 s equals frame 0. A touch on the reef takes over at once: the whole window opens and
// coral grows where the finger is. IDLE seconds after the last touch the reef dissolves and the loop
// restarts at t = 0. Reduced motion: the full week, a still reef, no loop.
//
// Thread model: one useFrameCallback on the UI thread advances the clock, decides the frame's steps
// and seeds and writes one small array that the canvas reads in the same frame; the dots and the
// count are animated styles of the loop time. Nothing here touches React state per frame; the only
// JS calls are the haptic on touch-down and the GPU's availability.

import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { CgsSkiaFallback, TOUCHES } from './CgsSkiaFallback';
import { DAY_STEP, DAYS, IDLE, LOOP, RETURN, SEEDS, SIM, T, bloomAt, countAt, dayTime, easeOutBack, levelAt, ringFrac, ringSoftAt, seg, smooth } from './cgs-motion';
import { CgsGpu } from './gpu/CgsGpu';

export { LOOP };

const COPY = {
  app: 'Tidepool',
  week: 'Week 47',
  habit: 'Morning swim',
  line: 'A ring of coral for every day you show up.',
  unit: 'day streak',
  best: 'Best 21',
  days: ['M', 'T', 'W', 'T', 'F', 'S', 'S'],
  stats: [
    ['This month', '18 of 24'],
    ['Average', '34 min'],
    ['Water', '17°'],
  ],
  hint: 'Touch the reef to plant coral',
} as const;

const INK = '#EAF6F4';
const MUTE = 'rgba(234,246,244,0.62)';
const CORAL = '#F4705F';
const DEEP = '#05222B';
/** the count's digit height, pt */
const DIGIT = 56;
const DOT = 36;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
/** the still frame shown under reduced motion: after the bloom, before the fade */
const STILL = 8.3;

type Props = { autoPlay?: boolean };

/** remember a touch for the fallback's shader, over the oldest */
function addTouch(touches: SharedValue<number[]>, cursor: SharedValue<number>, x: number, y: number, born: number) {
  'worklet';
  const list = touches.get().slice();
  const slot = cursor.get() % TOUCHES;
  list[slot * 4] = x;
  list[slot * 4 + 1] = y;
  list[slot * 4 + 2] = born;
  list[slot * 4 + 3] = 1;
  touches.set(list);
  cursor.set(slot + 1);
}

function noTouches() {
  'worklet';
  const list: number[] = [];
  for (let i = 0; i < TOUCHES * 4; i++) list.push(0);
  return list;
}

function buzz() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** one day of the week: its letter and a dot that fills when the day is done */
function DayDot({ index, time, level }: { index: number; time: SharedValue<number>; level: SharedValue<number> }) {
  const fill = useAnimatedStyle(() => {
    const u = seg(time.get(), dayTime(index), dayTime(index) + T.dot);
    const k = level.get();
    return { opacity: Math.min(1, u * 3) * k, transform: [{ scale: Math.max(0.01, easeOutBack(u) * (0.6 + 0.4 * k)) }] };
  });
  const tick = useAnimatedStyle(() => {
    const u = seg(time.get(), dayTime(index) + 0.12, dayTime(index) + T.dot + 0.1);
    return { opacity: u * level.get(), transform: [{ rotate: '45deg' }, { scale: 0.5 + 0.5 * easeOutBack(u) }] };
  });
  return (
    <View style={styles.day}>
      <Text style={styles.dayLetter}>{COPY.days[index]}</Text>
      <View style={styles.dot}>
        <Animated.View style={[styles.dotFill, fill]} />
        <Animated.View style={[styles.tick, tick]} />
      </View>
    </View>
  );
}

export function CoralGrowthStreak({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const compact = height < 740;
  /** the reef window's diameter, pt */
  const reef = Math.round(Math.max(200, Math.min(320, width - 76, height * 0.36)));
  /** null until the GPU has answered */
  const [gpu, setGpu] = useState<boolean | null>(null);

  const clock = useSharedValue(0);
  const gate = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const prevT = useSharedValue(-1);
  /** steps run since the field was last cleared, and the next day to seed */
  const stepsDone = useSharedValue(0);
  const nextDay = useSharedValue(0);
  const epoch = useSharedValue(0);
  const serial = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const down = useSharedValue(0);
  /** the finger on the reef, pt in the window, and where it last planted */
  const penX = useSharedValue(0);
  const penY = useSharedValue(0);
  const plantX = useSharedValue(-100);
  const plantY = useSharedValue(-100);
  const frame = useSharedValue<number[]>([]);
  // what the views and the fallback read
  const time = useSharedValue(reduced ? STILL : 0);
  const level = useSharedValue(1);
  const bloom = useSharedValue(0);
  const phase = useSharedValue(0);
  const ring = useSharedValue(reduced ? ringFrac(DAYS) : 0);
  const hint = useSharedValue(1);
  const touches = useSharedValue<number[]>(new Array<number>(TOUCHES * 4).fill(0));
  const touchCursor = useSharedValue(0);

  useEffect(() => {
    // the loop waits at t = 0 until the reef (or its fallback) is known
    gate.set(gpu === null ? 0 : 1);
  }, [gpu, gate]);

  useEffect(() => {
    if (reduced) {
      time.set(STILL);
      level.set(1);
      bloom.set(0);
      ring.set(ringFrac(DAYS));
    }
  }, [reduced, time, level, bloom, ring]);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    const before = clock.get();
    clock.set(before + dt);
    const now = clock.get();
    phase.set((now / LOOP) % 1);

    let steps = 0;
    let open = ringFrac(DAYS);
    let seedDay = -1;
    let plant = 0;
    if (mode.get() === MODE_AUTO) {
      if (gate.get() === 0 || !auto) {
        autoStart.set(now);
        prevT.set(-1);
        time.set(0);
        level.set(1);
        bloom.set(0);
        ring.set(0);
        open = 0;
      } else {
        const t = (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
        if (t < prevT.get()) {
          // the cut: the view shows the bare sea bed here whatever the field holds, so clearing it changes nothing on screen
          epoch.set(epoch.get() + 1);
          stepsDone.set(0);
          nextDay.set(0);
          touches.set(noTouches());
        }
        // fixed steps, however long the frame was, counted from the cut
        const done = stepsDone.get();
        steps = Math.max(0, Math.min(SIM.maxSteps, Math.floor(t * SIM.stepHz) - done));
        const day = nextDay.get();
        if (day < DAYS && steps > 0) {
          const at = DAY_STEP[day];
          if (done >= at) {
            // the day's seeds go in with this frame's first step, which is step `at` exactly
            seedDay = day;
            nextDay.set(day + 1);
          } else if (done + steps > at) {
            steps = at - done;
          }
        }
        stepsDone.set(done + steps);
        open = ringFrac(nextDay.get());
        time.set(t);
        level.set(levelAt(t));
        bloom.set(bloomAt(t));
        ring.set(ringSoftAt(t));
        prevT.set(t);
      }
    } else {
      steps = Math.min(SIM.maxSteps, Math.floor(now * SIM.stepHz) - Math.floor(before * SIM.stepHz));
      stepsDone.set(stepsDone.get() + steps);
      if (mode.get() === MODE_TOUCH) {
        level.set(Math.min(1, level.get() + dt * 4));
        bloom.set(0);
        ring.set(Math.min(ringFrac(DAYS), ring.get() + dt * 2));
        if (down.get() === 1 && steps > 0) {
          const dx = penX.get() - plantX.get();
          const dy = penY.get() - plantY.get();
          // one seed where the finger lands, then one every 5 pt of travel
          if (dx * dx + dy * dy > 25) {
            plant = 1;
            plantX.set(penX.get());
            plantY.set(penY.get());
            addTouch(touches, touchCursor, penX.get(), penY.get(), now);
          }
        }
        if (auto && down.get() === 0 && now - lastTouch.get() >= IDLE) {
          mode.set(MODE_RETURN);
          returnAt.set(now);
        }
      } else {
        const since = now - returnAt.get();
        level.set(1 - smooth(seg(since, 0, RETURN.ease)));
        if (since >= RETURN.total) {
          epoch.set(epoch.get() + 1);
          stepsDone.set(0);
          nextDay.set(0);
          touches.set(noTouches());
          autoStart.set(now);
          prevT.set(-1);
          time.set(0);
          level.set(1);
          ring.set(0);
          mode.set(MODE_AUTO);
          hint.set(withTiming(1, { duration: 400 }));
          steps = 0;
          open = 0;
        }
      }
    }

    // the frame (FRAME in gpu/CgsGpu.tsx)
    const out: number[] = [serial.get() + 1, epoch.get(), steps, (open * SIM.grid) / 2, level.get(), bloom.get(), phase.get()];
    serial.set(serial.get() + 1);
    const seeds = seedDay >= 0 ? SEEDS[seedDay] : null;
    for (let i = 0; i < SIM.seedSlots; i++) {
      if (seeds && i * 2 < seeds.length) out.push(seeds[i * 2], seeds[i * 2 + 1], 1, 0);
      else if (plant === 1 && i === 0) out.push(penX.get() / reef, penY.get() / reef, 1, 0);
      else out.push(0, 0, 0, 0);
    }
    frame.set(out);
  }, false);

  useEffect(() => {
    ticker.setActive(!reduced);
    return () => ticker.setActive(false);
  }, [ticker, reduced]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .enabled(!reduced)
    .onBegin((e) => {
      const dx = e.x / reef - 0.5;
      const dy = e.y / reef - 0.5;
      if (dx * dx + dy * dy > 0.25) return;
      mode.set(MODE_TOUCH);
      down.set(1);
      lastTouch.set(clock.get());
      hint.set(withTiming(0, { duration: 250 }));
      penX.set(e.x);
      penY.set(e.y);
      plantX.set(-100);
      plantY.set(-100);
      scheduleOnRN(buzz);
    })
    .onUpdate((e) => {
      if (down.get() === 0) return;
      penX.set(e.x);
      penY.set(e.y);
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      if (down.get() === 0) return;
      down.set(0);
      lastTouch.set(clock.get());
    });

  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -DIGIT * countAt(time.get()) * level.get() }] }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() * 0.7 }));
  const swell = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.02 * Math.sin(Math.PI * bloom.get()) }] }));

  const still = reduced || gpu === false;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <LinearGradient colors={['#0B3540', '#072A34', '#041C24']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      <View style={[styles.page, { paddingTop: insets.top + (compact ? 8 : 14), paddingBottom: Math.max(insets.bottom, 12) + (compact ? 6 : 14) }]}>
        <View>
          <View style={styles.headRow}>
            <Text style={styles.app}>{COPY.app}</Text>
            <Text style={styles.week}>{COPY.week}</Text>
          </View>
          <Text style={[styles.habit, compact && styles.habitCompact]}>{COPY.habit}</Text>
          <Text style={styles.line}>{COPY.line}</Text>
        </View>

        <Animated.View style={[styles.bezel, { width: reef + 20, height: reef + 20, borderRadius: (reef + 20) / 2 }, swell]}>
          <GestureDetector gesture={pan}>
            <View style={[styles.reef, { width: reef, height: reef, borderRadius: reef / 2 }]}>
              {still ? <CgsSkiaFallback size={reef} clock={clock} level={level} bloom={bloom} phase={phase} ring={ring} touches={touches} /> : null}
              <CgsGpu frame={frame} size={reef} onAvailable={setGpu} disabled={still} />
              <View pointerEvents="none" style={[styles.glass, { borderRadius: reef / 2 }]} />
            </View>
          </GestureDetector>
        </Animated.View>

        <View style={styles.streakRow}>
          <View style={styles.count}>
            <Animated.View style={strip}>
              {[0, 1, 2, 3, 4, 5, 6, 7].map((n) => (
                <View key={n} style={styles.digit}>
                  <Text style={styles.digitText}>{n}</Text>
                </View>
              ))}
            </Animated.View>
          </View>
          <Text style={styles.unit}>{COPY.unit}</Text>
          <View style={styles.bestPill}>
            <Text style={styles.best}>{COPY.best}</Text>
          </View>
        </View>

        <View style={styles.week7}>
          {[0, 1, 2, 3, 4, 5, 6].map((i) => (
            <DayDot key={i} index={i} time={time} level={level} />
          ))}
        </View>

        <View style={styles.card}>
          {COPY.stats.map(([label, value], i) => (
            <View key={label} style={[styles.stat, i > 0 && styles.statRule]}>
              <Text style={styles.statValue}>{value}</Text>
              <Text style={styles.statLabel}>{label}</Text>
            </View>
          ))}
        </View>

        <Animated.Text style={[styles.hint, hintStyle]}>{COPY.hint}</Animated.Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DEEP, overflow: 'hidden' },
  page: { flex: 1, paddingHorizontal: 24, justifyContent: 'space-between' },
  headRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  app: { color: INK, fontSize: 15, fontWeight: '600', letterSpacing: 0.2 },
  week: { color: MUTE, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  habit: { color: INK, fontSize: 32, lineHeight: 38, fontWeight: '700', letterSpacing: -0.6, marginTop: 14 },
  habitCompact: { fontSize: 26, lineHeight: 30, marginTop: 6 },
  line: { color: MUTE, fontSize: 15, lineHeight: 20, fontWeight: '400', marginTop: 4 },
  // the window's rim: a pale ring with a top edge highlight, a near and a far shadow
  bezel: {
    alignSelf: 'center',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(214,240,236,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(214,240,236,0.18)',
    boxShadow: '0 26px 48px rgba(0,8,12,0.55), 0 4px 10px rgba(0,8,12,0.4), inset 0 1px 0 rgba(255,255,255,0.28)',
  },
  reef: { overflow: 'hidden', backgroundColor: '#0A3440' },
  // the glass over the water: shaded under the rim's upper lip, a faint light along the lower edge
  glass: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, boxShadow: 'inset 0 10px 22px rgba(0,10,14,0.6), inset 0 -2px 5px rgba(214,240,236,0.14)' },
  streakRow: { flexDirection: 'row', alignItems: 'flex-end' },
  count: { height: DIGIT, overflow: 'hidden', minWidth: 34 },
  digit: { height: DIGIT, justifyContent: 'center' },
  digitText: { color: INK, fontSize: 52, lineHeight: DIGIT, fontWeight: '700', letterSpacing: -1, fontVariant: ['tabular-nums'] },
  unit: { color: INK, fontSize: 17, fontWeight: '600', marginLeft: 8, marginBottom: 10, flex: 1 },
  bestPill: { marginBottom: 9, paddingHorizontal: 12, height: 28, borderRadius: 14, justifyContent: 'center', backgroundColor: 'rgba(214,240,236,0.10)', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(214,240,236,0.2)' },
  best: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  week7: { flexDirection: 'row', justifyContent: 'space-between' },
  day: { alignItems: 'center', width: DOT },
  dayLetter: { color: MUTE, fontSize: 12, fontWeight: '600', marginBottom: 8 },
  dot: {
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(214,240,236,0.07)',
    borderWidth: 1,
    borderColor: 'rgba(214,240,236,0.16)',
  },
  dotFill: {
    position: 'absolute',
    left: -1,
    top: -1,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: CORAL,
    boxShadow: '0 4px 10px rgba(244,112,95,0.35), inset 0 1px 0 rgba(255,226,200,0.6), inset 0 -3px 6px rgba(150,40,60,0.35)',
  },
  // a check mark: the right and bottom edges of a small box, turned 45°
  tick: { width: 7, height: 13, marginTop: -3, borderRightWidth: 2.5, borderBottomWidth: 2.5, borderColor: '#4A0F1E', borderBottomRightRadius: 1.5 },
  card: {
    flexDirection: 'row',
    borderRadius: 20,
    borderCurve: 'continuous',
    paddingVertical: 14,
    backgroundColor: 'rgba(214,240,236,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(214,240,236,0.16)',
    boxShadow: '0 12px 24px rgba(0,8,12,0.3), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  stat: { flex: 1, alignItems: 'center' },
  statRule: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: 'rgba(214,240,236,0.18)' },
  statValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  statLabel: { color: MUTE, fontSize: 12, fontWeight: '500', marginTop: 3 },
  hint: { color: INK, fontSize: 13, fontWeight: '500', textAlign: 'center' },
});
