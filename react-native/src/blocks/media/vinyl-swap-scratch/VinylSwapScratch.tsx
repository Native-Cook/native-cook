import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
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

import {
  ALBUMS,
  type Fonts,
  type Geo,
  PILLS,
  drawArm,
  drawBackground,
  drawControls,
  drawDisc,
  drawFinger,
  drawScratchMarks,
  drawWaveform,
  makeGeo,
  pillX,
} from './art';
import { LOOP, OMEGA, type Pose, STOP_FOR, SWAP_LENGTH, approach, brake, loopPose, loopState, restPose, swapInto } from './motion';

/*
 Vinyl Swap Scratch — a turntable "now playing" screen you can scratch, and whose whole colour
 changes with the record on the platter. A port of the SwiftUI block (VinylSwapScratch.swift); the
 numbers are the same.

 Look: a black record (diameter 1.12 × the screen width) cropped by the left edge, centred at
 29 % / 35.5 % of the screen, its label drawn in code (two invented albums: "Slow Satellites" by
 The Amber Rooms, a saffron sunburst; "Glass Harbor" by Noa Lind, cream with teal rings). The
 background is the label's colour, blurred and tinted (ochre to near-black for A, teal for B).
 A chrome tonearm pivots at the top right. Under the record a slim waveform strip (bars that
 scroll with the platter's angle, a fixed playhead), then the title, then three dark pills.

 autoPlay timeline (LOOP = 10 s, motion.ts):
   0.0 – 1.5    album A plays (≈ 218 °/s, rounded so the platter turns 5 whole times a loop).
   1.3 – 1.5    a finger lands on the record; 1.5 the platter stops under it in 0.12 s.
   1.62 – 3.16  three baby scratches (back 55°, 75°, 95° and forward again, 0.6 / 0.5 / 0.44 s):
                the waveform scrubs back and forth, bars smear and flare in the accent colour,
                arcs flicker at the rim.
   3.2          release: the motor pulls the platter back up (spring ω 12, ζ 0.45, ~20 % overshoot).
   4.6 – 6.4    "next" is pressed: the arm lifts and swings out (0.4 s), the record slides off to
                the left (0.3 – 0.8 s, ease-in), record B slides in from the right (0.6 s on, spring
                ω 11, ζ 0.62), the colours cross-fade (0.4 – 1.4 s), the title changes (0.45 – 1.05 s),
                the arm swings back and drops (1.3 – 1.8 s). The waveform hushes while the stylus is up.
   7.7 – 9.5    "previous": the same, B back to A.
   10.0         the same frame as 0.0.

 Touch: a finger on the record stops the platter under it (0.12 s), turns it with the finger
 (the angle around the centre) and scrubs the waveform; on release the motor spins it back up.
 Pause stops the platter over 0.8 s, play spins it up; previous/next change the record. 2 s
 after the last touch the screen goes back to album A playing and the loop starts again.
 Reduced motion: nothing spins and nothing plays by itself; changing the record cross-fades in
 0.3 s; the record still turns under a finger.
 */

const FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

/** The block's state on the UI thread; times are seconds on the frame clock, -1 or [] for "not set". */
type Motion = {
  /** The frame clock, and the clock the drawing follows (held still under reduced motion when idle). */
  now: SharedValue<number>;
  clock: SharedValue<number>;
  /** The loop's t = 0, and the angle added to the loop's own (so a hand-off keeps the record still). */
  autoStart: SharedValue<number>;
  angleOffset: SharedValue<number>;
  album: SharedValue<number>;
  /** A record change: [when, from, to] or []. */
  change: SharedValue<number[]>;
  motorOn: SharedValue<number>;
  /** The platter under the hand: [date, angle, brake (1) or motor (0), from speed, brake duration]. */
  spin: SharedValue<number[]>;
  /** A finger on the record: [pointer angle, turned, velocity, last move, last tick] or []. */
  hand: SharedValue<number[]>;
  ignoring: SharedValue<number>;
  /** Reduced motion: the drawing follows the clock until then. */
  busyUntil: SharedValue<number>;
  resumeAt: SharedValue<number>;
  pressed: SharedValue<number[]>;
};

function tactile(kind: 'soft' | 'light' | 'tick') {
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function motorSpeed(m: Motion, reduced: boolean) {
  'worklet';
  return reduced || m.motorOn.get() === 0 ? 0 : OMEGA;
}

/** The hand-driven platter: [angle, speed]. */
function platter(m: Motion, at: number, reduced: boolean): [number, number] {
  'worklet';
  const s = m.spin.get();
  const t = Math.max(0, at - s[0]);
  const r = s[2] === 1 ? brake(s[3], s[4], t) : approach(s[3], motorSpeed(m, reduced), t);
  let a = s[1] + r[0];
  let v = r[1];
  const h = m.hand.get();
  if (h.length === 5) {
    a += h[1];
    v += h[2] * Math.exp(-Math.max(0, at - h[3]) / 0.08);
  }
  return [a, v];
}

function poseAt(m: Motion, at: number, reduced: boolean): Pose {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    const p = loopPose((at - start) % LOOP);
    p.angle += m.angleOffset.get();
    return p;
  }
  const pl = platter(m, at, reduced);
  let p = restPose(m.album.get());
  const c = m.change.get();
  if (c.length === 3) {
    const e = at - c[0];
    if (e < SWAP_LENGTH) swapInto(p, e, c[1], c[2], reduced);
    else p = restPose(c[2]);
  }
  p.angle = pl[0];
  p.speed = pl[1];
  p.clock = at % 1000;
  return p;
}

/** Freezes whatever plays into the hand-driven state. */
function takeOver(m: Motion, now: number, reduced: boolean) {
  'worklet';
  m.resumeAt.set(-1);
  const start = m.autoStart.get();
  if (start >= 0) {
    if (now >= start) {
      const t = (now - start) % LOOP;
      const p = loopPose(t);
      const s = loopState(t);
      m.album.set(s.album);
      m.change.set(s.swap ? [now - (t - s.swap[0]), s.swap[1], s.swap[2]] : []);
      m.motorOn.set(1);
      m.spin.set([now, p.angle + m.angleOffset.get(), 0, p.speed, 0]);
    }
    m.autoStart.set(-1);
  }
  const c = m.change.get();
  if (c.length === 3 && now - c[0] >= SWAP_LENGTH) {
    m.album.set(c[2]);
    m.change.set([]);
  }
  if (reduced) m.busyUntil.set(now + 0.4);
}

export function VinylSwapScratch({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [shown, setShown] = useState(0);
  const [playingLabel, setPlayingLabel] = useState(true);
  const g = useMemo<Geo | null>(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.bottom) : null), [size, insets.bottom]);
  const fonts = useMemo<Fonts | null>(() => {
    if (!g) return null;
    return {
      heavyA: matchFont({ fontFamily: FAMILY, fontSize: Math.max(7, g.labelR * 0.1), fontWeight: '800' }),
      heavyB: matchFont({ fontFamily: FAMILY, fontSize: Math.max(7, g.labelR * 0.11), fontWeight: '800' }),
      mono: matchFont({ fontFamily: MONO, fontSize: Math.max(6, g.labelR * 0.075), fontWeight: '600' }),
      pill: matchFont({ fontFamily: MONO, fontSize: 9, fontWeight: '600' }),
    };
  }, [g]);

  const m: Motion = {
    now: useSharedValue(-1),
    clock: useSharedValue(0),
    autoStart: useSharedValue(-1),
    angleOffset: useSharedValue(0),
    album: useSharedValue(0),
    change: useSharedValue<number[]>([]),
    motorOn: useSharedValue(1),
    spin: useSharedValue<number[]>([0, 0, 0, reduced ? 0 : OMEGA, 0]),
    hand: useSharedValue<number[]>([]),
    ignoring: useSharedValue(0),
    busyUntil: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
    pressed: useSharedValue<number[]>([0, 0, 0]),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.now.get() < 0) {
      m.spin.set([now, 0, 0, reduced ? 0 : OMEGA, 0]);
      if (autoPlay && !reduced) m.autoStart.set(now);
    }
    m.now.set(now);
    if (!reduced || now < m.busyUntil.get() || m.hand.get().length > 0) m.clock.set(now);
    // 2 s after the last touch: back to album A playing, then the loop from its start.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt && m.hand.get().length === 0) {
      m.resumeAt.set(-1);
      takeOver(m, now, reduced);
      let ready = now;
      const c = m.change.get();
      if (c.length === 0 && m.album.get() !== 0) {
        m.change.set([now, m.album.get(), 0]);
        ready = now + SWAP_LENGTH;
      } else if (c.length === 3) {
        ready = c[0] + SWAP_LENGTH;
      }
      if (m.motorOn.get() === 0) {
        const pl = platter(m, now, reduced);
        m.motorOn.set(1);
        m.spin.set([now, pl[0], 0, pl[1], 0]);
      }
      // Let the motor settle (1 s after its last push) so the hand-off keeps the speed.
      ready = Math.max(ready, m.spin.get()[0] + 1);
      m.angleOffset.set(platter(m, ready, reduced)[0]);
      m.autoStart.set(ready);
    }
  });

  const pose = useDerivedValue(() => poseAt(m, m.clock.get(), reduced));

  useAnimatedReaction(
    () => {
      const p = pose.get();
      return (p.titleAlpha[1] > p.titleAlpha[0] ? 1 : 0) * 2 + (m.motorOn.get() === 1 || m.autoStart.get() >= 0 ? 1 : 0);
    },
    (key, previous) => {
      if (key !== previous) {
        scheduleOnRN(setShown, key >> 1);
        scheduleOnRN(setPlayingLabel, (key & 1) === 1);
      }
    },
  );

  const gesture = useMemo(() => {
    if (!g) return Gesture.Pan();
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (Math.hypot(e.x - g.cx, e.y - g.cy) > g.r + 8 || !(e.x < g.w * 0.82 || e.y > g.py + 60)) {
          m.ignoring.set(1);
          return;
        }
        m.ignoring.set(0);
        const now = m.now.get();
        takeOver(m, now, reduced);
        const pl = platter(m, now, reduced);
        m.spin.set([now, pl[0], 1, pl[1], STOP_FOR]);
        m.hand.set([(Math.atan2(e.y - g.cy, e.x - g.cx) * 180) / Math.PI, 0, 0, now, 0]);
        scheduleOnRN(tactile, 'soft');
      })
      .onUpdate((e) => {
        const h = m.hand.get();
        if (m.ignoring.get() === 1 || h.length !== 5) return;
        const now = m.now.get();
        const pointer = (Math.atan2(e.y - g.cy, e.x - g.cx) * 180) / Math.PI;
        let d = pointer - h[0];
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        const dt = Math.max(1 / 120, now - h[3]);
        const velocity = 0.6 * h[2] * Math.exp(-dt / 0.08) + (0.4 * d) / dt;
        const turned = h[1] + d;
        let ticked = h[4];
        if (Math.abs(turned - ticked) >= 15) {
          ticked = turned;
          scheduleOnRN(tactile, 'tick');
        }
        m.hand.set([pointer, turned, velocity, now, ticked]);
      })
      .onFinalize(() => {
        if (m.ignoring.get() === 1 || m.hand.get().length !== 5) {
          m.ignoring.set(0);
          return;
        }
        const now = m.now.get();
        const a = platter(m, now, reduced)[0];
        m.hand.set([]);
        m.spin.set([now, a, 0, 0, 0]);
        scheduleOnRN(tactile, 'light');
        if (reduced) m.busyUntil.set(now + 0.1);
        if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced, autoPlay]);

  const press = (i: number) => {
    tactile('light');
    scheduleOnUI(() => {
      'worklet';
      const now = m.now.get();
      takeOver(m, now, reduced);
      if (i === 0) {
        const pl = platter(m, now, reduced);
        const on = m.motorOn.get() === 1 ? 0 : 1;
        m.motorOn.set(on);
        m.spin.set(on === 1 ? [now, pl[0], 0, pl[1], 0] : [now, pl[0], 1, pl[1], 0.8]);
      } else if (m.change.get().length === 0) {
        m.change.set([now, m.album.get(), 1 - m.album.get()]);
      }
      if (autoPlay && !reduced) m.resumeAt.set(now + 2);
    });
  };

  const setPressed = (i: number, on: number) => {
    scheduleOnUI(() => {
      'worklet';
      const p = m.pressed.get().slice();
      p[i] = on;
      m.pressed.set(p);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!g || !fonts) return recorder.finishRecordingAsPicture();
    const p = pose.get();
    drawBackground(canvas, g, p.mix);
    for (const i of [1, 0]) {
      if (p.discAlpha[i] > 0.001 && Math.abs(p.discX[i]) < 1) drawDisc(canvas, g, i, g.cx + p.discX[i] * g.slide, g.cy, p.angle, p.discAlpha[i], fonts);
    }
    drawScratchMarks(canvas, g, p);
    drawFinger(canvas, g, p);
    drawArm(canvas, g, p.arm, p.lift);
    drawWaveform(canvas, g, p);
    drawControls(canvas, g, p.press, m.pressed.get(), m.motorOn.get() === 1 || m.autoStart.get() >= 0, fonts.pill);
    return recorder.finishRecordingAsPicture();
  });

  const titleA = useAnimatedStyle(() => ({ opacity: pose.get().titleAlpha[0], transform: [{ translateX: pose.get().titleX[0] }] }));
  const titleB = useAnimatedStyle(() => ({ opacity: pose.get().titleAlpha[1], transform: [{ translateX: pose.get().titleX[1] }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {g && fonts ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={`Now playing ${ALBUMS[shown].title} by ${ALBUMS[shown].artist}`}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          {/* Title and artist of both records, cross-fading; the speed tag on the right. */}
          <View style={[styles.titles, { top: g.titleTop, width: g.w - 48 }]} pointerEvents="none">
            {[titleA, titleB].map((style, i) => (
              <Animated.View key={i} style={[styles.titleBlock, style]}>
                <Text style={styles.title} numberOfLines={1}>
                  {ALBUMS[i].title}
                </Text>
                <Text style={styles.artist} numberOfLines={1}>
                  {ALBUMS[i].artist}
                </Text>
              </Animated.View>
            ))}
            <View style={styles.tag}>
              <Text style={styles.tagText}>33⅓ RPM</Text>
            </View>
          </View>

          {/* The pills are drawn in the canvas; these take the touches. */}
          {PILLS.map((pill, i) => (
            <Pressable
              key={pill.label}
              accessibilityRole="button"
              accessibilityLabel={i === 0 ? (playingLabel ? 'Pause' : 'Play') : i === 1 ? 'Previous' : 'Next'}
              onPressIn={() => setPressed(i, 1)}
              onPressOut={() => setPressed(i, 0)}
              onPress={() => press(i)}
              style={[styles.pill, { left: pillX(g, i), top: g.controlsTop, width: pill.width }]}
            />
          ))}
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#15110A' },
  titles: { position: 'absolute', left: 24 },
  titleBlock: { position: 'absolute', left: 0, top: 0, right: 0, gap: 4 },
  title: { fontSize: 24, fontWeight: '600', color: '#FFFFFF' },
  artist: { fontSize: 15, color: 'rgba(255,255,255,0.62)' },
  tag: {
    position: 'absolute',
    right: 0,
    top: 6,
    height: 24,
    paddingHorizontal: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    justifyContent: 'center',
  },
  tagText: { fontSize: 11, fontWeight: '500', color: 'rgba(255,255,255,0.55)', fontFamily: MONO },
  pill: { position: 'absolute', height: 56 },
});
