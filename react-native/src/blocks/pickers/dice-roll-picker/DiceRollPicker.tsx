import { Canvas, FilterMode, MipmapMode, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, PLAYERS, drawAvatars, drawDie, drawDieShadow, drawResult, drawRoom, makeGeo, playerFor } from './diceArt';
import {
  EDGE,
  HALF,
  type Mat,
  type Pose,
  TABLE,
  THROW_END,
  type Throw,
  axisAngle,
  clamp,
  faceUp,
  hash,
  makeThrow,
  mul,
  project,
  slidePose,
  snapFlat,
  support,
  throwPose,
  topFace,
  unproject,
} from './diceMath';
import { AUTO_SUM, LOOP, RIM_Y, autoPose, autoResult, restPose, springIn } from './diceMotion';
import { bake } from './dicePaint';

/*
 Dice Roll Picker — a board-game night screen ("Game Table — Who goes first?"). Two ivory dice,
 drawn in real 3D (projected rounded cubes, lit faces, concave pips, shadows that grow and soften
 with height) sit on green felt in a walnut tray. Flick on the table to throw: the dice tumble,
 hop, bounce off the far rim and settle on their faces; their sum picks a player, whose avatar pops
 and wears a "Starts" tag while the line rolls to "11 · Priya starts". Push a die with a finger to
 roll it over by hand; let go and it settles flat on the nearest face.

 One Skia canvas: a baked room and tray, then per frame the shadows, the dice (far one first), the
 avatars and the result line. 3D math and the closed-form throw: diceMath.ts; timeline:
 diceMotion.ts. Touch throws use the same closed-form throw with random faces.

 2 s after the last touch (once the dice are still) they slide home to 3 and 4 (1.6 s) and the
 loop restarts. Reduced motion: no loop; a throw places the dice at once and the result fades in.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const IDLE = 2;
const SLIDE = 1.6;
const FLICK = 650;
const BACKGROUND = '#1A1512';

/** A die's motion: 0 still at `pose`, 1 a throw, 2 a slide from (m0, p0) to (m1, p1). */
type DieState = { kind: number; t0: number; dur: number; pose: Pose; th: Throw | null; m0: Mat; m1: Mat; p0: number[]; p1: number[] };

type Frame = { a: Pose; b: Pose; k: number; pop: number; chosen: number; sum: number };

type Props = {
  /** Picks the dice up, throws them (5 and 6, Priya starts) and slides them home, on an 8 s loop. */
  autoPlay?: boolean;
};

function buzz(kind: 'land' | 'snap') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'snap') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function still(pose: Pose): DieState {
  'worklet';
  return { kind: 0, t0: 0, dur: 0, pose, th: null, m0: pose.m, m1: pose.m, p0: [pose.x, pose.y], p1: [pose.x, pose.y] };
}

/** Where a die is now, and whether its motion is over. */
function evalDie(st: DieState, now: number): [Pose, boolean] {
  'worklet';
  if (st.kind === 1 && st.th) {
    const s = now - st.t0;
    return [throwPose(st.th, clamp(s, 0, THROW_END)), s >= THROW_END];
  }
  if (st.kind === 2) {
    const u = (now - st.t0) / st.dur;
    return [slidePose(st.m0, st.m1, st.p0, st.p1, u), u >= 1];
  }
  return [st.pose, true];
}

export function DiceRollPicker({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      initials: matchFont({ fontFamily: SANS, fontSize: 18, fontWeight: '700' }),
      name: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '600' }),
      range: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '500' }),
      result: matchFont({ fontFamily: SANS, fontSize: 20, fontWeight: '700' }),
      tag: matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '800' }),
    }),
    [],
  );
  const room = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(PixelRatio.get(), 3);
    const image = bake(g.w, g.h, scale, (c) => drawRoom(c, g));
    return image ? { image, scale } : null;
  }, [g]);

  // mode: 0 loop / rest, 1 a finger is in charge, 2 sliding home.
  const mode = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  const now = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const dragging = useSharedValue(-1);
  const dragPrev = useSharedValue<number[]>([0, 0]);
  const flicked = useSharedValue(0);
  const dice = useSharedValue<DieState[]>([still(restPose(0)), still(restPose(1))]);
  /** [result roll, pop, chosen, sum, settled since]. */
  const result = useSharedValue<number[]>([0, 0, 0, 7, -1]);
  const returnAt = useSharedValue(-1);
  const frame = useSharedValue<Frame>({ a: restPose(0), b: restPose(1), k: 0, pop: 0, chosen: 3, sum: 7 });

  useFrameCallback((info) => {
    const t1 = info.timestamp / 1000;
    const first = now.get() < 0;
    const dt = first ? 0 : Math.min(t1 - now.get(), 0.05);
    now.set(t1);
    if (first && auto) autoStart.set(t1);
    if (mode.get() === 0 && autoStart.get() >= 0) {
      const t = (t1 - autoStart.get()) % LOOP;
      const r = autoResult(t);
      frame.set({ a: autoPose(0, t), b: autoPose(1, t), k: r[0], pop: r[1], chosen: 5, sum: AUTO_SUM });
      return;
    }
    const st = dice.get();
    const [a, doneA] = evalDie(st[0], t1);
    const [b, doneB] = evalDie(st[1], t1);
    const settled = mode.get() !== 2 && dragging.get() < 0 && doneA && doneB;
    const r = result.get().slice();
    if (settled) {
      if (r[4] < 0) {
        r[4] = t1;
        r[3] = topFace(a.m) + topFace(b.m);
        r[2] = playerFor(r[3]);
      }
      r[1] = reduced ? 1 : springIn(t1 - r[4]);
    } else {
      r[4] = -1;
      r[1] *= Math.exp(-dt * 8);
    }
    r[0] += ((settled ? 1 : 0) - r[0]) * (1 - Math.exp(-dt * 10));
    result.set(r);
    if (mode.get() === 1 && auto && settled && t1 - lastTouch.get() > IDLE) {
      // Slide home from wherever the dice lie.
      mode.set(2);
      returnAt.set(t1);
      const rest = [restPose(0), restPose(1)];
      const cur = [a, b];
      dice.set(
        [0, 1].map((i) => ({ kind: 2, t0: t1, dur: SLIDE, pose: rest[i], th: null, m0: cur[i].m, m1: rest[i].m, p0: [cur[i].x, cur[i].y], p1: [rest[i].x, rest[i].y] })),
      );
    } else if (mode.get() === 2 && t1 > returnAt.get() + SLIDE + 0.2) {
      mode.set(0);
      dice.set([still(restPose(0)), still(restPose(1))]);
      autoStart.set(t1);
    }
    frame.set({ a, b, k: r[0], pop: r[1], chosen: r[2], sum: r[3] });
  });

  // A light tap as the result lands (finger throws only).
  useAnimatedReaction(
    () => (mode.get() === 1 && frame.get().k > 0.5 ? 1 : 0),
    (v, prev) => {
      if (prev === 0 && v === 1) scheduleOnRN(buzz, 'land');
    },
  );

  /** Hands control to the finger: freezes whatever plays into still dice. */
  function takeOver() {
    'worklet';
    const t1 = now.get();
    if (mode.get() !== 1) {
      const f = frame.get();
      dice.set([still(f.a), still(f.b)]);
    }
    mode.set(1);
    autoStart.set(-1);
    lastTouch.set(t1);
  }

  function throwAll(bias: number) {
    'worklet';
    takeOver();
    const t1 = now.get();
    const st = dice.get();
    const next: DieState[] = [];
    for (let i = 0; i < 2; i += 1) {
      const [cur] = evalDie(st[i], t1);
      // A throw's outcome from the moment it was thrown (seeded, so it needs no random source).
      const seed = Math.floor(t1 * 997) + i * 131;
      const face = 1 + Math.floor(hash(seed) * 6);
      const yaw = (hash(seed + 1) - 0.5) * 1.4;
      const fx = (i === 0 ? -40 : 42) + bias * 30 + (hash(seed + 2) - 0.5) * 24;
      const fy = 18 + hash(seed + 3) * 40;
      const end = faceUp(face, yaw);
      if (reduced) {
        next.push(still({ m: end, x: fx, y: fy, hop: 0 }));
        continue;
      }
      const th = makeThrow(cur.m, end, [cur.x, cur.y], [fx, fy], cur.hop, 2 + Math.floor(hash(seed + 4) * 2), RIM_Y);
      next.push({ kind: 1, t0: t1 + i * 0.05, dur: THROW_END, pose: cur, th, m0: cur.m, m1: end, p0: [cur.x, cur.y], p1: [fx, fy] });
    }
    dice.set(next);
  }

  const gesture = useMemo(() => {
    const geo = g;
    const lim = { x0: TABLE.x0 + HALF + 2, x1: TABLE.x1 - HALF - 2, y0: TABLE.y0 + HALF + 2, y1: TABLE.y1 - HALF - 2 };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!geo || e.y > geo.resultBase - 30) {
          flicked.set(-1);
          return;
        }
        flicked.set(0);
        takeOver();
        // A die under the finger? (projected centre, generous radius)
        const f = frame.get();
        const poses = [f.a, f.b];
        let hit = -1;
        let best = EDGE * 0.85;
        for (let i = 0; i < 2; i += 1) {
          const p = poses[i];
          const s = project(geo.cx, geo.cy, [p.x, p.y, p.hop + support(p.m)]);
          const d = Math.hypot(e.x - s[0], e.y - s[1]);
          if (d < best) {
            best = d;
            hit = i;
          }
        }
        if (hit >= 0 && poses[hit].hop < 4) {
          dragging.set(hit);
          const st = dice.get().slice();
          st[hit] = still(poses[hit]);
          dice.set(st);
          dragPrev.set(unproject(geo.cx, geo.cy, e.x, e.y, HALF));
        }
      })
      .onUpdate((e) => {
        if (!geo || flicked.get() < 0) return;
        lastTouch.set(now.get());
        const i = dragging.get();
        if (i < 0) return;
        const w = unproject(geo.cx, geo.cy, e.x, e.y, HALF);
        const prev = dragPrev.get();
        const st = dice.get().slice();
        const p = st[i].pose;
        const x = clamp(p.x + (w[0] - prev[0]), lim.x0, lim.x1);
        const y = clamp(p.y + (w[1] - prev[1]), lim.y0, lim.y1);
        const dx = x - p.x;
        const dy = y - p.y;
        const dist = Math.hypot(dx, dy);
        let m = p.m;
        // Pushed along the felt it tips over: a quarter turn per edge length, about z × direction.
        if (dist > 0.01) m = mul(axisAngle([-dy / dist, dx / dist, 0], (dist / EDGE) * (Math.PI / 2)), m);
        st[i] = still({ m, x, y, hop: 0 });
        dice.set(st);
        dragPrev.set(w);
      })
      .onFinalize((e) => {
        if (flicked.get() < 0) return;
        lastTouch.set(now.get());
        const i = dragging.get();
        if (i >= 0) {
          dragging.set(-1);
          const st = dice.get().slice();
          const p = st[i].pose;
          const flat = snapFlat(p.m);
          st[i] = reduced ? still({ m: flat, x: p.x, y: p.y, hop: 0 }) : { kind: 2, t0: now.get(), dur: 0.28, pose: p, th: null, m0: p.m, m1: flat, p0: [p.x, p.y], p1: [p.x, p.y] };
          dice.set(st);
          scheduleOnRN(buzz, 'snap');
        } else if (Math.hypot(e.velocityX, e.velocityY) > FLICK) {
          throwAll(clamp(e.velocityX / 2500, -0.6, 0.6));
        }
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced]);

  const roll = () => {
    scheduleOnUI(() => {
      'worklet';
      throwAll(0);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && room) {
      const f = frame.get();
      const s = room.scale;
      canvas.drawImageRectOptions(room.image, Skia.XYWHRect(0, 0, g.w * s, g.h * s), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, null);
      drawDieShadow(canvas, g, f.a);
      drawDieShadow(canvas, g, f.b);
      // The die farther away (larger y) first.
      if (f.a.y > f.b.y) {
        drawDie(canvas, g, f.a);
        drawDie(canvas, g, f.b);
      } else {
        drawDie(canvas, g, f.b);
        drawDie(canvas, g, f.a);
      }
      const pops = [0, 0, 0, 0, 0, 0];
      pops[f.chosen] = f.pop;
      drawAvatars(canvas, g, fonts, pops, f.chosen);
      drawResult(canvas, g, fonts, `${f.sum} · ${PLAYERS[f.chosen].name} starts`, f.k);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && room ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Two dice on a felt table. Flick to throw them, or push a die to turn it by hand.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
            <Text style={styles.kicker}>GAME TABLE · THURSDAY</Text>
            <Text style={styles.title}>Who goes first?</Text>
            <Text style={styles.subtitle}>Harbor Lights · round 2 of 3</Text>
          </View>
          <Pressable accessibilityRole="button" onPress={roll} style={({ pressed }) => [styles.cta, { top: g.ctaTop }, pressed && styles.pressed]}>
            <View style={styles.ctaDie}>
              <View style={[styles.ctaPip, { left: 4, top: 4 }]} />
              <View style={[styles.ctaPip, { left: 8.5, top: 8.5 }]} />
              <View style={[styles.ctaPip, { left: 13, top: 13 }]} />
            </View>
            <Text style={styles.ctaText}>Roll the dice</Text>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { fontSize: 11, fontWeight: '700', letterSpacing: 1.8, color: 'rgba(243,234,219,0.5)' },
  title: { fontSize: 30, fontWeight: '700', color: '#F7EEDF', letterSpacing: -0.4 },
  subtitle: { fontSize: 15, color: 'rgba(243,234,219,0.6)' },
  cta: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 56,
    borderRadius: 28,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    backgroundColor: '#F2C46B',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 10px 28px rgba(242,196,107,0.22)',
  },
  ctaText: { fontSize: 17, fontWeight: '700', color: '#3A2610' },
  ctaDie: { width: 20, height: 20, borderRadius: 5, borderWidth: 1.5, borderColor: '#3A2610' },
  ctaPip: { position: 'absolute', width: 3, height: 3, borderRadius: 1.5, backgroundColor: '#3A2610', marginLeft: -1.5, marginTop: -1.5 },
  pressed: { transform: [{ scale: 0.97 }] },
});
