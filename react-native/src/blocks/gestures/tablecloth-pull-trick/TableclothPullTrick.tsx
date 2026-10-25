import { Canvas, FilterMode, MipmapMode, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawCandlePools, drawDishes } from './clothDishes';
import { type HudFonts, drawHud } from './clothHud';
import { CLOTH_LEN, type ClothPose, LOOP, OUT, THROW_BACK, YANK, autoPose, clamp, easeInOut, easeOut, restPose, seg, throwBack, yankPull } from './clothMotion';
import { bake } from './clothPaint';
import { CLOTH_W, TEX_SCALE, drawCloth, drawLinen, drawRoom, drawStreaks, makeGeo, makeMesh, project } from './clothScene';

/*
 Tablecloth Pull Trick — a dinner-party planner ("Supper Club — Table for six") whose hero is the
 set table seen from above the front: a checked linen cloth (a textured mesh) under six place
 settings, wine glasses and two candles. Grab the hem and yank: fast enough (> 1300 pt/s at the
 release) and the cloth whips off in 0.55 s, a wave running through it toward you, ghosts and speed
 streaks behind it, while each dish hops as the cloth leaves from under it and jiggles; too slow
 and the dishes slide along with it (up to 40 pt) and the glasses lean. A clean pull rolls the
 counter up and shows a badge. The cloth is then thrown back over the table and billows down.

 One Skia canvas: a baked room and walnut table, the cloth mesh (baked linen texture + a colour-only
 shading mesh), candlelight pooled on it, the dishes back to front, the counter and the badge.
 Timeline: clothMotion.ts; scene and mesh: clothScene.ts; dishes: clothDishes.ts.

 Touch takes over at once. 2 s after the last touch the cloth is thrown back on (2.4 s, as in the
 loop) or, if it is still on the table, eases back (1 s), the dishes go home, and the loop restarts. Reduced motion: no loop; a pull jumps to its result.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const IDLE = 2;
const RESET = 1;
const FAST = 1300;
const MAX_SLIDE = 40;
const BACKGROUND = '#1E1513';

type Props = {
  /** Yanks the cloth (a clean pull), then slides it back, on a 9 s loop. */
  autoPlay?: boolean;
};

type Feel = 'grab' | 'clean' | 'messy';

function buzz(kind: Feel) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'clean') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'grab' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

/** The finger's state: pull, its velocity, the dishes' drift, glass lean spring, events. */
type User = {
  d: number;
  v: number;
  slide: number;
  tip: number;
  tipV: number;
  wave: number;
  fling: number;
  whipAt: number;
  whipFrom: number;
  jig: number;
  count: number;
  shown: number;
  cleanAt: number;
};

const USER0: User = { d: 0, v: 0, slide: 0, tip: 0, tipV: 0, wave: 0, fling: 0, whipAt: -1, whipFrom: 0, jig: 0, count: 3, shown: 3, cleanAt: -1 };

export function TableclothPullTrick({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<HudFonts>(
    () => ({
      label: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
      count: matchFont({ fontFamily: SANS, fontSize: 32, fontWeight: '700' }),
      badge: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '600' }),
    }),
    [],
  );
  const mesh = useMemo(() => makeMesh(), []);
  const art = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(PixelRatio.get(), 3);
    const room = bake(g.w, g.h, scale, (c) => drawRoom(c, g));
    const linen = bake(CLOTH_W, CLOTH_LEN, TEX_SCALE, (c) => drawLinen(c));
    return room && linen ? { room, linen, scale } : null;
  }, [g]);

  // mode: 0 loop / rest, 1 a finger is in charge, 2 the cloth going back on.
  const mode = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  const now = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const pulling = useSharedValue(0);
  const grab = useSharedValue<number[]>([0, 0]);
  const user = useSharedValue<User>(USER0);
  const back = useSharedValue<number[]>([0, 0, 0, 3, 0]);
  const pose = useSharedValue<ClothPose>(restPose(0, 3));

  useFrameCallback((frame) => {
    const t1 = frame.timestamp / 1000;
    const first = now.get() < 0;
    const dt = first ? 0 : Math.min(t1 - now.get(), 0.05);
    now.set(t1);
    if (first && auto) autoStart.set(t1);
    const loopT = (t1 % LOOP + LOOP) % LOOP;
    if (mode.get() === 0 && autoStart.get() >= 0) {
      pose.set(autoPose((t1 - autoStart.get()) % LOOP));
      return;
    }
    if (mode.get() === 2) {
      // Back to rest: thrown back on if the cloth is gone, else eased back; the dishes go home.
      const b = back.get();
      const r = t1 - b[0];
      const p = restPose(loopT, 3);
      let done: boolean;
      if (b[4] === 1) {
        const tb = throwBack(r);
        p.lift = tb.lift;
        p.back = tb.back;
        p.air = tb.air;
        p.hang = tb.hang;
        p.wave = tb.wave;
        done = r >= THROW_BACK;
      } else {
        p.d = b[1] * (1 - easeOut(seg(r, 0, RESET)));
        done = r >= RESET;
      }
      const e = easeInOut(seg(r, 0.2, b[4] === 1 ? 2.0 : RESET));
      p.slide = b[2] * (1 - e);
      p.count = b[3] + (3 - b[3]) * e;
      pose.set(p);
      if (done) {
        user.set(USER0);
        mode.set(0);
        autoStart.set(t1);
      }
      return;
    }
    const s = { ...user.get() };
    let speed = 0;
    let yankAge = -1;
    if (s.whipAt >= 0) {
      // The yank, as in the loop: u^1.6 over 0.55 s, a wave and a whip down the drape.
      const age = t1 - s.whipAt;
      const y = yankPull(s.whipFrom, age);
      s.d = y[0];
      speed = y[1];
      s.fling = 0.85 * Math.sin(Math.PI * clamp((age - 0.1) / 0.75, 0, 1));
      s.wave = 26 * Math.sin(Math.PI * clamp((age - 0.03) / 0.67, 0, 1));
      yankAge = age;
      if (age >= YANK + 1.7) s.whipAt = -1;
    } else if (pulling.get() === 0 && s.d < OUT - 1 && dt > 0) {
      // Let go slowly: the cloth springs back under the dishes (ω 9, ζ 0.75).
      const steps = Math.ceil(dt * 240);
      const h = dt / steps;
      for (let k = 0; k < steps; k += 1) {
        const acc = -81 * s.d - 13.5 * s.v;
        s.v += acc * h;
        s.d += s.v * h;
      }
      s.wave *= Math.exp(-dt * 4);
      s.fling *= Math.exp(-dt * 6);
    }
    // Glass lean: a spring back to upright (ω 14, ζ 0.18 — glasses wobble).
    if (dt > 0) {
      const steps = Math.ceil(dt * 240);
      const h = dt / steps;
      for (let k = 0; k < steps; k += 1) {
        const acc = -196 * s.tip - 5 * s.tipV;
        s.tipV += acc * h;
        s.tip += s.tipV * h;
      }
    }
    s.shown += (s.count - s.shown) * (1 - Math.exp(-dt * 9));
    user.set(s);
    const badge = s.cleanAt >= 0 ? easeOut(seg(t1, s.cleanAt, s.cleanAt + 0.3)) * (1 - seg(t1, s.cleanAt + 2.2, s.cleanAt + 2.6)) : 0;
    const p = restPose(loopT, s.shown);
    p.d = s.d;
    p.speed = speed;
    p.wave = s.wave;
    p.fling = s.fling;
    p.slide = s.slide;
    p.yankAge = yankAge;
    p.yankFrom = s.whipFrom;
    p.jig = s.jig;
    p.tip = s.tip;
    p.badge = badge;
    pose.set(p);
    const idle = pulling.get() === 0 && s.whipAt < 0 && t1 - lastTouch.get() > IDLE;
    if (mode.get() === 1 && auto && idle) {
      mode.set(2);
      back.set([t1, s.d, s.slide, s.shown, s.d >= OUT - 1 ? 1 : 0]);
    }
  });

  const gesture = useMemo(() => {
    const geo = g;
    const frontY = geo ? project(geo, 0, 0, 0)[1] : 0;
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!geo || e.y < frontY - 50 || e.y > geo.panelTop) {
          grab.set([0, -1]);
          return;
        }
        if (mode.get() !== 1) {
          // Take over from the loop (or the reset) where it is.
          const p = pose.get();
          user.set({ ...USER0, d: p.d >= OUT - 1 ? 0 : p.d, slide: p.slide, count: Math.round(p.count), shown: p.count });
        }
        mode.set(1);
        autoStart.set(-1);
        lastTouch.set(now.get());
        const s = user.get();
        if (s.whipAt >= 0 || s.d >= OUT - 1) {
          grab.set([0, -1]);
          return;
        }
        pulling.set(1);
        grab.set([s.d, 1]);
        scheduleOnRN(buzz, 'grab');
      })
      .onUpdate((e) => {
        const gr = grab.get();
        if (gr[1] < 0) return;
        lastTouch.set(now.get());
        const s = { ...user.get() };
        const raw = gr[0] + e.translationY * 1.15;
        const d = raw < 0 ? raw / (1 - raw / 30) : Math.min(raw, CLOTH_LEN + 40);
        const delta = d - s.d;
        const fast = e.velocityY > FAST;
        if (delta > 0) {
          // Friction drags the dishes along with a slow pull; a fast one leaves them behind.
          s.slide = Math.min(MAX_SLIDE, s.slide + delta * (fast ? 0.02 : 0.85));
          s.tipV += delta * (fast ? 3 : 8);
        }
        s.v = e.velocityY * 1.15;
        s.wave = clamp(Math.abs(e.velocityY) / 120, 0, 12);
        s.fling = clamp(e.velocityY / 3000, 0, 0.6);
        s.d = d;
        user.set(s);
      })
      .onFinalize((e) => {
        const gr = grab.get();
        if (gr[1] < 0) return;
        grab.set([0, -1]);
        pulling.set(0);
        lastTouch.set(now.get());
        const s = { ...user.get() };
        const t1 = now.get();
        if (e.velocityY > FAST && s.d > 16) {
          const clean = s.slide < 12;
          s.whipAt = reduced ? t1 - YANK - 2 : t1;
          s.whipFrom = s.d;
          s.jig = clean ? 1 : 1.5;
          if (clean) {
            s.count += 1;
            s.cleanAt = t1 + 0.25;
          }
          scheduleOnRN(buzz, clean ? 'clean' : 'messy');
        } else {
          s.v = Math.min(s.v, 0);
          s.tipV -= 60;
        }
        user.set(s);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && art) {
      const p = pose.get();
      const sc = art.scale;
      canvas.drawImageRectOptions(art.room, Skia.XYWHRect(0, 0, g.w * sc, g.h * sc), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, null);
      // Motion blur on the yank: two fading ghosts a few hundredths of a second behind.
      if (p.speed > 300) {
        drawCloth(canvas, g, mesh, art.linen, { ...p, d: p.d - p.speed * 0.06 }, 0.16);
        drawCloth(canvas, g, mesh, art.linen, { ...p, d: p.d - p.speed * 0.03 }, 0.3);
      }
      drawCloth(canvas, g, mesh, art.linen, p);
      drawStreaks(canvas, g, p);
      drawCandlePools(canvas, g, p.time);
      drawDishes(canvas, g, p);
      // Thrown back on, the cloth is above the dishes: drawn over them, fading under as it lands.
      if (p.lift > 8) {
        const k = clamp((p.lift - 8) / 52, 0, 1);
        drawCloth(canvas, g, mesh, art.linen, p, k * k * (3 - 2 * k));
      }
      drawHud(canvas, g, fonts, insets.top, p);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A table set for six. Grab the tablecloth's hem and yank it fast to pull it out from under the dishes.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
            <Text style={styles.kicker}>SUPPER CLUB</Text>
            <Text style={styles.title}>Table for six</Text>
            <Text style={styles.subtitle}>Sat 19 Oct · 7:30 pm · 14 Hollis Lane</Text>
          </View>
          <View style={[styles.panel, { top: g.panelTop, paddingBottom: Math.max(insets.bottom, 12) }]}>
            <View style={styles.guestsRow}>
              <View style={styles.faces}>
                {GUESTS.map((x, i) => (
                  <View key={x.i} style={[styles.face, { backgroundColor: x.c, marginLeft: i === 0 ? 0 : -8 }]}>
                    <Text style={styles.faceText}>{x.i}</Text>
                  </View>
                ))}
              </View>
              <View style={styles.guestsText}>
                <Text style={styles.guestsTitle}>6 of 6 coming</Text>
                <Text style={styles.guestsNote} numberOfLines={1}>
                  Yank the hem — fast — before dessert
                </Text>
              </View>
            </View>
            <Pressable accessibilityRole="button" style={({ pressed }) => [styles.cta, pressed && styles.pressed]}>
              <Text style={styles.ctaText}>Send the menu</Text>
            </Pressable>
          </View>
        </>
      ) : null}
    </View>
  );
}

const GUESTS = [
  { i: 'A', c: '#C0603A' },
  { i: 'B', c: '#4F7A3A' },
  { i: 'C', c: '#7A5BA6' },
  { i: 'D', c: '#B8873A' },
  { i: 'E', c: '#3F7F8C' },
  { i: 'F', c: '#A8475E' },
];

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 120, gap: 6 },
  kicker: { fontSize: 11, fontWeight: '700', letterSpacing: 1.8, color: 'rgba(244,231,212,0.5)' },
  title: { fontSize: 30, fontWeight: '700', color: '#FBF0DF', letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: 'rgba(244,231,212,0.6)' },
  panel: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: 20,
    paddingHorizontal: 16,
    gap: 16,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: '#211714',
    boxShadow: '0 -1px 0 rgba(255,236,210,0.08), 0 -16px 32px rgba(0,0,0,0.45)',
  },
  guestsRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 8 },
  faces: { flexDirection: 'row' },
  face: { width: 32, height: 32, borderRadius: 16, borderWidth: 2, borderColor: '#211714', alignItems: 'center', justifyContent: 'center' },
  faceText: { fontSize: 13, fontWeight: '700', color: '#FFF4E4' },
  guestsText: { flex: 1, gap: 2 },
  guestsTitle: { fontSize: 15, fontWeight: '600', color: '#FBF0DF' },
  guestsNote: { fontSize: 13, color: 'rgba(244,231,212,0.55)' },
  cta: { height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center', backgroundColor: '#E9B872', boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 8px 22px rgba(233,184,114,0.18)' },
  ctaText: { fontSize: 17, fontWeight: '700', color: '#3A2412' },
  pressed: { transform: [{ scale: 0.97 }] },
});
