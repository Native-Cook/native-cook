// Ripple Pond Photo — a travel journal's photo page whose water answers the finger.
//
// The page is a full-bleed photo of a boat on still water with the entry's title over it. The water
// is a real 2D wave simulation on the GPU (gpu/RppGpu.tsx, constants in rpp-motion.ts): a tap drops a
// ring, a drag leaves a wake, rings bounce off the screen's edges and die down, and the photo is seen
// through the surface. The title and the notes are native text above the canvas, so they stay sharp.
//
// autoPlay: LOOP = 10 s. A ghost finger taps twice, drags a wake across, taps once more, then the
// water is damped flat and cleared at t = 0, so frame 10 s equals frame 0. A touch takes over at once
// (the ghost fades); IDLE seconds after the last touch the water is calmed and the loop restarts at
// t = 0. Reduced motion: the still photo, no water and no loop.
//
// Thread model: one useFrameCallback on the UI thread advances the clock, runs the script, collects
// the frame's drops and writes one small array that the canvas reads in the same frame. Nothing here
// touches React state per frame; the only JS calls are the haptic on touch-down and the GPU's
// availability.

import { Image } from 'expo-image';
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
import { RppGpu } from './gpu/RppGpu';
import { RING, RppSkiaFallback } from './RppSkiaFallback';
import { DROP, IDLE, LOOP, RETURN, SIM, T, dampAt, dragX, dragY, ghostAlpha, ghostPress, ghostTap, gridRows, seg, smooth, tapShare } from './rpp-motion';

export { LOOP };

const PHOTO = require('./assets/rpp-cove.jpg') as number;

const COPY = {
  journal: 'Halcyon',
  day: 'Day 6 of 11',
  title: 'Still water at\nCala Brisa',
  date: 'Thursday 12 June · 7:40 am',
  note: 'Anchored off the south beach. Not a breath of wind until nine, and the bottom clear at four metres.',
  facts: 'Water 23° · Wind calm',
  hint: 'Touch the water',
} as const;

const INK = '#F4FBFA';
const DEEP = '#062E33';
const GHOST = 46;
/** taps that can be pressing into the water at once (the other two drop slots carry the wake) */
const TAP_SLOTS = 2;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;

type Props = { autoPlay?: boolean };

/** start a tap at (u, v) on step `at`, in a free slot or over the oldest */
function addTap(taps: SharedValue<number[]>, at: number, u: number, v: number) {
  'worklet';
  const list = taps.get().slice();
  let slot = 0;
  let oldest = Infinity;
  for (let i = 0; i < TAP_SLOTS; i++) {
    const born = list[i * 3];
    if (born < oldest) {
      oldest = born;
      slot = i;
    }
  }
  list[slot * 3] = at;
  list[slot * 3 + 1] = u;
  list[slot * 3 + 2] = v;
  taps.set(list);
}

/** add a ring for the fallback shader, over the oldest */
function addRing(rings: SharedValue<number[]>, cursor: SharedValue<number>, x: number, y: number, born: number, strength: number) {
  'worklet';
  const list = rings.get().slice();
  const slot = cursor.get() % RING.count;
  list[slot * 4] = x;
  list[slot * 4 + 1] = y;
  list[slot * 4 + 2] = born;
  list[slot * 4 + 3] = strength;
  rings.set(list);
  cursor.set(slot + 1);
}

function emptyTaps() {
  'worklet';
  const list: number[] = [];
  for (let i = 0; i < TAP_SLOTS * 3; i++) list.push(i % 3 === 0 ? -1 : 0);
  return list;
}

function emptyRings() {
  'worklet';
  const list: number[] = [];
  for (let i = 0; i < RING.count * 4; i++) list.push(0);
  return list;
}

function buzz() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function RipplePondPhoto({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const rows = gridRows(width, height);
  /** null until the GPU has answered */
  const [gpu, setGpu] = useState<boolean | null>(null);

  const clock = useSharedValue(0);
  const gate = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const prevT = useSharedValue(-1);
  const stepsDone = useSharedValue(-1);
  const epoch = useSharedValue(0);
  const serial = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const down = useSharedValue(0);
  /** the pointer that cuts the wake (the finger, or the ghost during the scripted drag), pt */
  const penX = useSharedValue(0);
  const penY = useSharedValue(0);
  const wakeX = useSharedValue(-1);
  const wakeY = useSharedValue(-1);
  const taps = useSharedValue<number[]>([-1, 0, 0, -1, 0, 0]);
  const frame = useSharedValue<number[]>([]);
  // the ghost finger
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const ghostP = useSharedValue(0);
  const hint = useSharedValue(1);
  // the fallback's rings
  const rings = useSharedValue<number[]>(new Array<number>(RING.count * 4).fill(0));
  const ringCursor = useSharedValue(0);
  const ringGap = useSharedValue(0);
  const level = useSharedValue(1);

  useEffect(() => {
    // the loop waits at t = 0 until the water (or its fallback) is known
    gate.set(gpu === null ? 0 : 1);
  }, [gpu, gate]);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();

    // fixed steps, however long the frame was
    const target = Math.floor(now * SIM.stepHz);
    if (stepsDone.get() < 0 || target - stepsDone.get() > SIM.stepHz / 4) stepsDone.set(target - 1);
    const a = stepsDone.get();
    const steps = Math.min(SIM.maxSteps, target - a);
    const b = a + steps;
    stepsDone.set(b);

    let damp: number = SIM.damp;
    let wake = 0;
    if (mode.get() === MODE_AUTO) {
      if (gate.get() === 0 || !auto) {
        autoStart.set(now);
        prevT.set(-1);
        ghostA.set(0);
      } else {
        const t = (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
        if (t < prevT.get()) {
          // the cut: the water is flat here (see the notes), so clearing it changes nothing on screen
          epoch.set(epoch.get() + 1);
          taps.set(emptyTaps());
          rings.set(emptyRings());
          wakeX.set(-1);
        }
        for (let i = 0; i < T.taps.length; i++) {
          const at = T.taps[i][0];
          if (prevT.get() < at && t >= at) {
            addTap(taps, a, T.taps[i][1], T.taps[i][2]);
            addRing(rings, ringCursor, T.taps[i][1] * width, T.taps[i][2] * height, now, 1);
          }
        }
        const which = ghostTap(t);
        if (which >= 0) {
          ghostX.set(T.taps[which][1] * width);
          ghostY.set(T.taps[which][2] * height);
        } else {
          const u = seg(t, T.drag[0], T.drag[1]);
          ghostX.set(dragX(u) * width);
          ghostY.set(dragY(u) * height);
        }
        ghostA.set(ghostAlpha(t));
        ghostP.set(ghostPress(t));
        if (t >= T.drag[0] && t < T.drag[1]) {
          penX.set(ghostX.get());
          penY.set(ghostY.get());
          wake = 1;
        } else {
          wakeX.set(-1);
        }
        damp = dampAt(t);
        level.set(1 - smooth(seg(t, T.calm[0], T.calm[1])));
        prevT.set(t);
      }
    } else if (mode.get() === MODE_TOUCH) {
      wake = down.get();
      if (down.get() === 0) wakeX.set(-1);
      level.set(1);
      if (auto && down.get() === 0 && now - lastTouch.get() >= IDLE) {
        mode.set(MODE_RETURN);
        returnAt.set(now);
      }
    } else {
      const since = now - returnAt.get();
      const k = smooth(seg(since, 0, RETURN.ease));
      damp = SIM.damp + (SIM.calmDamp - SIM.damp) * k;
      level.set(1 - k);
      if (since >= RETURN.total) {
        epoch.set(epoch.get() + 1);
        taps.set(emptyTaps());
        rings.set(emptyRings());
        wakeX.set(-1);
        autoStart.set(now);
        prevT.set(-1);
        mode.set(MODE_AUTO);
        hint.set(withTiming(1, { duration: 400 }));
      }
    }

    // the frame (FRAME in gpu/RppGpu.tsx): its drops are two tap slots, then the wake's midpoint and head
    const out: number[] = [serial.get() + 1, epoch.get(), steps, SIM.c2, damp];
    serial.set(serial.get() + 1);
    const list = taps.get();
    let ended = false;
    for (let i = 0; i < TAP_SLOTS; i++) {
      const born = list[i * 3];
      let depth = 0;
      if (born >= 0 && steps > 0) {
        depth = DROP.tapDepth * (tapShare((b - born) / DROP.tapSteps) - tapShare((a - born) / DROP.tapSteps));
        if (b - born >= DROP.tapSteps) ended = true;
      }
      out.push(list[i * 3 + 1], list[i * 3 + 2], DROP.tapRadius, depth);
    }
    if (ended) {
      const next = list.slice();
      for (let i = 0; i < TAP_SLOTS; i++) {
        if (next[i * 3] >= 0 && b - next[i * 3] >= DROP.tapSteps) next[i * 3] = -1;
      }
      taps.set(next);
    }
    let cut = 0;
    let fromX = penX.get();
    let fromY = penY.get();
    if (wake > 0 && steps > 0) {
      if (wakeX.get() >= 0) {
        fromX = wakeX.get();
        fromY = wakeY.get();
        const travel = Math.sqrt((penX.get() - fromX) * (penX.get() - fromX) + (penY.get() - fromY) * (penY.get() - fromY));
        // a faster finger cuts deeper
        if (travel > 0.5) cut = Math.max(DROP.wakeMax, DROP.wakePerPt * travel);
        // the fallback gets a small ring every 28 pt of wake
        ringGap.set(ringGap.get() + travel);
        if (ringGap.get() > 28) {
          ringGap.set(0);
          addRing(rings, ringCursor, penX.get(), penY.get(), now, 0.3);
        }
      }
      if (cut !== 0 || wakeX.get() < 0) {
        wakeX.set(penX.get());
        wakeY.set(penY.get());
      }
    }
    out.push((fromX + penX.get()) / 2 / width, (fromY + penY.get()) / 2 / height, DROP.wakeRadius, cut / 2);
    out.push(penX.get() / width, penY.get() / height, DROP.wakeRadius, cut / 2);
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
      mode.set(MODE_TOUCH);
      down.set(1);
      lastTouch.set(clock.get());
      ghostA.set(withTiming(0, { duration: 150 }));
      hint.set(withTiming(0, { duration: 250 }));
      penX.set(e.x);
      penY.set(e.y);
      wakeX.set(-1);
      addTap(taps, Math.max(0, stepsDone.get()), e.x / width, e.y / height);
      addRing(rings, ringCursor, e.x, e.y, clock.get(), 1);
      scheduleOnRN(buzz);
    })
    .onUpdate((e) => {
      penX.set(e.x);
      penY.set(e.y);
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      down.set(0);
      lastTouch.set(clock.get());
    });

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostA.get(),
    transform: [{ translateX: ghostX.get() - GHOST / 2 }, { translateY: ghostY.get() - GHOST / 2 }, { scale: 1 - 0.16 * ghostP.get() }],
  }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() * 0.62 }));

  const narrow = height < 700;
  const titleSize = narrow ? 28 : 32;

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen}>
        <StatusBar style="light" />
        <Image source={PHOTO} style={StyleSheet.absoluteFill} contentFit="cover" />
        {gpu === false && !reduced ? <RppSkiaFallback photoAsset={PHOTO} width={width} height={height} clock={clock} level={level} rings={rings} /> : null}
        <RppGpu photoAsset={PHOTO} frame={frame} width={width} height={height} rows={rows} onAvailable={setGpu} disabled={reduced || gpu === false} />

        {/* the page's text sits above the water, on two soft scrims */}
        <LinearGradient pointerEvents="none" colors={['rgba(6,46,51,0.62)', 'rgba(6,46,51,0.3)', 'rgba(6,46,51,0)']} locations={[0, 0.55, 1]} style={[styles.scrimTop, { height: insets.top + (narrow ? 170 : 200) }]} />
        <LinearGradient pointerEvents="none" colors={['rgba(4,38,44,0)', 'rgba(4,38,44,0.34)', 'rgba(4,38,44,0.7)']} locations={[0, 0.5, 1]} style={[styles.scrimBottom, { height: insets.bottom + (narrow ? 170 : 200) }]} />

        <View pointerEvents="none" style={[styles.head, { top: insets.top + 12 }]}>
          <View style={styles.headRow}>
            <Text style={styles.journal}>{COPY.journal}</Text>
            <Text style={styles.day}>{COPY.day}</Text>
          </View>
          <Text style={[styles.title, { fontSize: titleSize, lineHeight: titleSize + 4 }]}>{COPY.title}</Text>
          <Text style={styles.date}>{COPY.date}</Text>
        </View>

        <View pointerEvents="none" style={[styles.foot, { bottom: Math.max(insets.bottom, 14) + 14 }]}>
          <Text style={styles.note} numberOfLines={3}>
            {COPY.note}
          </Text>
          <View style={styles.footRow}>
            <Text style={styles.facts}>{COPY.facts}</Text>
            <Animated.Text style={[styles.hint, hintStyle]}>{COPY.hint}</Animated.Text>
          </View>
        </View>

        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]}>
          <View style={styles.ghostCore} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DEEP, overflow: 'hidden' },
  scrimTop: { position: 'absolute', left: 0, right: 0, top: 0 },
  scrimBottom: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  head: { position: 'absolute', left: 24, right: 24 },
  headRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  journal: { color: INK, fontSize: 15, fontWeight: '600', letterSpacing: 0.2 },
  day: { color: 'rgba(244,251,250,0.74)', fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  title: { color: INK, fontWeight: '700', letterSpacing: -0.6, marginTop: 22 },
  date: { color: 'rgba(244,251,250,0.8)', fontSize: 15, fontWeight: '500', marginTop: 8, fontVariant: ['tabular-nums'] },
  foot: { position: 'absolute', left: 24, right: 24 },
  note: { color: 'rgba(244,251,250,0.9)', fontSize: 15, lineHeight: 21, fontWeight: '400' },
  footRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 },
  facts: { color: 'rgba(244,251,250,0.74)', fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  hint: { color: INK, fontSize: 13, fontWeight: '500' },
  // a soft white touch: a faint halo, a thin ring and a brighter core, no dark edge
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.7)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 0 18px rgba(255,255,255,0.38), inset 0 0 10px rgba(255,255,255,0.22)',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(255,255,255,0.62)', boxShadow: '0 0 8px rgba(255,255,255,0.6)' },
});
