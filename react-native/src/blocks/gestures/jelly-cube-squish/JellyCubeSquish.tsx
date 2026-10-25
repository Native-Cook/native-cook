import { Canvas, FilterMode, MipmapMode, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawBackdrop, drawFinger, drawJelly, drawName, makeGeo } from './jellyArt';
import { type Deform, HALF, REST, RIGHT, TO_EYE, UP, clamp, faceIndices, project, restPoints } from './jellyMath';
import { FLAVOURS, LOOP, autoColour, autoDeform, autoFinger, easeInOut, seg } from './jellyMotion';
import { bake } from './jellyPaint';

/*
 Jelly Cube Squish — a dessert-bar order screen ("Wobble — Build your jelly") with a translucent
 jelly cube on a plate: lit per vertex (diffuse, a Fresnel sheen, see-through far faces, an inner
 glow, suspended bubbles, additive gloss and a reflected window) and deformed as a soft body. Poke
 it and it dents and wobbles; drag it and the part under the finger stretches, then springs back
 wobbling. Pick a flavour and the new colour rises through it from the plate in a gooey front.

 One Skia canvas: the baked backdrop and plate, then the cube's meshes, the demo finger and the
 flavour's name. Deformation and camera: jellyMath.ts; timeline: jellyMotion.ts; drawing:
 jellyArt.ts. The flavour chips are views with one sliding highlight.

 Touch: springs on the UI thread — squash and lean ω 13 rad/s ζ 0.12, dent ω 20 ζ 0.25, stretch
 ω 12 ζ 0.15. 2 s after the last touch the cube eases to rest (0.9 s), the colour fills back to
 strawberry if needed, and the loop restarts. Reduced motion: no loop, no wobble (the shape follows
 the finger directly); a new flavour fades in at once.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const IDLE = 2;
const SETTLE = 0.9;
const BACKGROUND = '#F1DCD6';

type Props = {
  /** Pokes the cube, switches to lime, stretches it, and back to strawberry, on an 8 s loop. */
  autoPlay?: boolean;
};

/** Spring state: values, velocities and targets of the deformation, the touch point, colour. */
type Soft = {
  sq: number[];
  lx: number[];
  ly: number[];
  dent: number[];
  sx: number[];
  sy: number[];
  sz: number[];
  p: number[];
  colour: number[];
};

const SOFT0: Soft = {
  sq: [0, 0, 0],
  lx: [0, 0, 0],
  ly: [0, 0, 0],
  dent: [0, 0, 0],
  sx: [0, 0, 0],
  sy: [0, 0, 0],
  sz: [0, 0, 0],
  p: [0, -HALF, HALF, 0, 1, 0],
  colour: [0, 0, -10],
};

function buzz(kind: 'poke' | 'release' | 'pick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'pick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'poke' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One spring [value, velocity, target] stepped by dt (or snapped under reduced motion). */
function step(s: number[], omega: number, zeta: number, dt: number, reduced: boolean) {
  'worklet';
  if (reduced) return [s[2], 0, s[2]];
  let x = s[0];
  let v = s[1];
  const n = Math.max(1, Math.ceil(dt * 240));
  const h = dt / n;
  for (let i = 0; i < n; i += 1) {
    v += (-omega * omega * (x - s[2]) - 2 * zeta * omega * v) * h;
    x += v * h;
  }
  return [x, v, s[2]];
}

function soften(s: Soft, k: number): Deform {
  'worklet';
  return {
    sq: s.sq[0] * k,
    lx: s.lx[0] * k,
    ly: s.ly[0] * k,
    dent: s.dent[0] * k,
    px: s.p[0],
    py: s.p[1],
    pz: s.p[2],
    nx: s.p[3],
    ny: s.p[4],
    nz: s.p[5],
    sx: s.sx[0] * k,
    sy: s.sy[0] * k,
    sz: s.sz[0] * k,
  };
}

/** The colour state [from, to, fill, roll] of a touch-mode morph started at `start`. */
function morph(c: number[], now: number, reduced: boolean) {
  'worklet';
  if (reduced) return [c[1], c[1], 0, 0];
  return [c[0], c[1], easeInOut(seg(now, c[2], c[2] + 1)), easeInOut(seg(now, c[2], c[2] + 0.3))];
}

export function JellyCubeSquish({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo(
    () => ({
      name: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '700' }),
      price: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '600' }),
      sub: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '500' }),
    }),
    [],
  );
  const mesh = useMemo(() => ({ rest: restPoints(), idx: faceIndices() }), []);
  const backdrop = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(PixelRatio.get(), 3);
    const image = bake(g.w, g.h, scale, (c) => drawBackdrop(c, g));
    return image ? { image, scale } : null;
  }, [g]);

  // mode: 0 loop / rest, 1 a finger is in charge, 2 settling back to rest.
  const mode = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  const now = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const touching = useSharedValue(0);
  const soft = useSharedValue<Soft>(SOFT0);
  const settleAt = useSharedValue(-1);
  /** What the frame draws: deformation, colour [from, to, fill, roll], finger, loop time. */
  const scene = useSharedValue<{ d: Deform; colour: number[]; finger: number[]; time: number }>({ d: REST, colour: [0, 0, 0, 0], finger: [0, 0, 0, 0], time: 0 });

  useFrameCallback((frame) => {
    const t1 = frame.timestamp / 1000;
    const first = now.get() < 0;
    const dt = first ? 0 : Math.min(t1 - now.get(), 0.05);
    now.set(t1);
    if (first && auto) autoStart.set(t1);
    if (mode.get() === 0 && autoStart.get() >= 0) {
      const t = (t1 - autoStart.get()) % LOOP;
      const d = autoDeform(t);
      scene.set({ d, colour: autoColour(t), finger: autoFinger(t, d), time: t });
      return;
    }
    const s = soft.get();
    const n: Soft = {
      sq: step(s.sq, 13, 0.12, dt, reduced),
      lx: step(s.lx, 13, 0.12, dt, reduced),
      ly: step(s.ly, 13, 0.12, dt, reduced),
      dent: step(s.dent, 20, 0.25, dt, reduced),
      sx: step(s.sx, 12, 0.15, dt, reduced),
      sy: step(s.sy, 12, 0.15, dt, reduced),
      sz: step(s.sz, 12, 0.15, dt, reduced),
      p: s.p,
      colour: s.colour,
    };
    soft.set(n);
    let k = 1;
    if (mode.get() === 2) {
      k = 1 - easeInOut(seg(t1, settleAt.get(), settleAt.get() + SETTLE));
      const c = morph(n.colour, t1, reduced);
      if (k <= 0 && ((c[1] === 0 && c[2] >= 1) || (c[0] === c[1] && c[1] === 0))) {
        soft.set(SOFT0);
        mode.set(0);
        autoStart.set(t1);
      }
    } else if (mode.get() === 1 && auto && touching.get() === 0 && t1 - lastTouch.get() > IDLE) {
      mode.set(2);
      settleAt.set(t1);
      const c = morph(n.colour, t1, reduced);
      const shown = c[2] >= 0.5 ? c[1] : c[0];
      if (shown !== 0) soft.set({ ...n, colour: [shown, 0, t1] });
    }
    scene.set({ d: soften(soft.get(), k), colour: morph(soft.get().colour, t1, reduced), finger: [0, 0, 0, 0], time: (t1 % LOOP + LOOP) % LOOP });
  });

  /** Hands the cube to the finger, keeping the shape and colour the loop had. */
  function takeOver() {
    'worklet';
    if (mode.get() !== 1) {
      const sc = scene.get();
      const d = sc.d;
      const c = sc.colour;
      const shown = c[0] === c[1] ? c[0] : c[2] >= 0.5 ? c[1] : c[0];
      soft.set({
        sq: [d.sq, 0, 0],
        lx: [d.lx, 0, 0],
        ly: [d.ly, 0, 0],
        dent: [d.dent, 0, 0],
        sx: [d.sx, 0, 0],
        sy: [d.sy, 0, 0],
        sz: [d.sz, 0, 0],
        p: [d.px, d.py, d.pz, d.nx, d.ny, d.nz],
        colour: [shown, shown, -10],
      });
    }
    mode.set(1);
    autoStart.set(-1);
    lastTouch.set(now.get());
  }

  const gesture = useMemo(() => {
    const geo = g;
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!geo || e.y > geo.nameBase - 34 || e.y < insets.top + 100) {
          touching.set(-1);
          return;
        }
        takeOver();
        touching.set(1);
        // Where the finger meets the cube: on the plane facing the camera through its centre,
        // moved toward the eye and clamped onto the box; the dent points into the face it hit.
        const c = project(geo.cx, geo.cy, [0, 0, HALF]);
        const dx = (e.x - c[0]) / c[2];
        const dy = (e.y - c[1]) / c[2];
        const P = [RIGHT[0] * dx - UP[0] * dy + TO_EYE[0] * HALF * 1.3, RIGHT[1] * dx - UP[1] * dy + TO_EYE[1] * HALF * 1.3, HALF - UP[2] * dy + TO_EYE[2] * HALF * 1.3];
        const q = [clamp(P[0], -HALF, HALF), clamp(P[1], -HALF, HALF), clamp(P[2], 0, 2 * HALF)];
        const out = [Math.abs(q[0]) / HALF, Math.abs(q[1]) / HALF, Math.abs(q[2] - HALF) / HALF];
        let axis = 0;
        if (out[1] > out[axis]) axis = 1;
        if (out[2] > out[axis]) axis = 2;
        const nrm = [0, 0, 0];
        nrm[axis] = axis === 2 ? (q[2] > HALF ? -1 : 1) : q[axis] > 0 ? -1 : 1;
        const s = soft.get();
        soft.set({ ...s, p: [q[0], q[1], q[2], nrm[0], nrm[1], nrm[2]], dent: [s.dent[0], s.dent[1], 26], sq: [s.sq[0], s.sq[1], 0.06] });
        scheduleOnRN(buzz, 'poke');
      })
      .onUpdate((e) => {
        if (!geo || touching.get() < 0) return;
        lastTouch.set(now.get());
        const c = project(geo.cx, geo.cy, [0, 0, HALF]);
        const tx = e.translationX / c[2];
        const ty = e.translationY / c[2];
        const len = Math.hypot(tx, ty);
        if (len < 8) return;
        // Pull: the drag in the camera's plane, rubber-banded to 64 pt.
        const k = (64 * Math.tanh(len / 64)) / len;
        const s = soft.get();
        soft.set({
          ...s,
          dent: [s.dent[0], s.dent[1], 0],
          sq: [s.sq[0], s.sq[1], -0.03],
          sx: [s.sx[0], s.sx[1], (RIGHT[0] * tx - UP[0] * ty) * k],
          sy: [s.sy[0], s.sy[1], (RIGHT[1] * tx - UP[1] * ty) * k],
          sz: [s.sz[0], s.sz[1], -UP[2] * ty * k],
        });
      })
      .onFinalize(() => {
        if (touching.get() < 0) return;
        touching.set(0);
        lastTouch.set(now.get());
        const s = soft.get();
        const pull = Math.hypot(s.sx[0], s.sy[0], s.sz[0]);
        // Let go: everything springs home; the pull kicks the wobble modes.
        soft.set({
          ...s,
          dent: [s.dent[0], s.dent[1], 0],
          sq: [s.sq[0], s.sq[1] + 0.03 * pull, 0],
          lx: [s.lx[0], s.lx[1] - s.sx[0] * 3, 0],
          ly: [s.ly[0], s.ly[1] - s.sy[0] * 3 + (pull < 4 ? 40 * s.p[4] : 0), 0],
          sx: [s.sx[0], s.sx[1], 0],
          sy: [s.sy[0], s.sy[1], 0],
          sz: [s.sz[0], s.sz[1], 0],
        });
        scheduleOnRN(buzz, 'release');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, insets.top]);

  const pick = (i: number) => {
    buzz('pick');
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      const s = soft.get();
      const c = morph(s.colour, now.get(), reduced);
      const shown = c[0] === c[1] ? c[0] : c[2] >= 0.5 ? c[1] : c[0];
      if (shown === i) return;
      soft.set({ ...s, colour: [shown, i, now.get()], sq: [s.sq[0], s.sq[1] + 1.2, s.sq[2]] });
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && backdrop) {
      const sc = scene.get();
      const k = backdrop.scale;
      canvas.drawImageRectOptions(backdrop.image, Skia.XYWHRect(0, 0, g.w * k, g.h * k), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, null);
      drawJelly(canvas, g, mesh, sc.d, sc.colour, sc.time);
      drawFinger(canvas, g, sc.finger);
      drawName(canvas, g, fonts, sc.colour);
    }
    return recorder.finishRecordingAsPicture();
  });

  // The chip highlight slides with the colour's name.
  const chipW = g ? (g.w - 32 - 16) / 3 : 0;
  const pill = useAnimatedStyle(() => {
    const c = scene.get().colour;
    return { transform: [{ translateX: (c[0] + (c[1] - c[0]) * c[3]) * (chipW + 8) }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && backdrop ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A jelly cube on a plate. Poke it or drag it to make it wobble.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
            <Text style={styles.kicker}>WOBBLE · DESSERT BAR</Text>
            <Text style={styles.title}>Build your jelly</Text>
            <Text style={styles.subtitle}>Step 2 of 3 · Pick a flavour</Text>
          </View>
          <View style={[styles.chips, { top: g.chipsTop }]}>
            <Animated.View pointerEvents="none" style={[styles.pill, { width: chipW }, pill]} />
            {FLAVOURS.map((f, i) => (
              <Pressable key={f.name} onPress={() => pick(i)} accessibilityRole="button" accessibilityLabel={f.name} style={[styles.chip, { width: chipW }]}>
                <View style={[styles.dot, { backgroundColor: `rgb(${f.rgb.map((v) => Math.round(v * 255)).join(',')})` }]} />
                <Text style={styles.chipText} numberOfLines={1}>
                  {f.name}
                </Text>
              </Pressable>
            ))}
          </View>
          <Pressable accessibilityRole="button" style={({ pressed }) => [styles.cta, { top: g.ctaTop }, pressed && styles.pressed]}>
            <Text style={styles.ctaText}>Add to order</Text>
            <Text style={styles.ctaPrice}>$6.50</Text>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const INK = '#3A1F22';

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 6 },
  kicker: { fontSize: 11, fontWeight: '700', letterSpacing: 1.8, color: 'rgba(58,31,34,0.5)' },
  title: { fontSize: 30, fontWeight: '700', color: INK, letterSpacing: -0.4 },
  subtitle: { fontSize: 15, color: 'rgba(58,31,34,0.6)' },
  chips: { position: 'absolute', left: 16, right: 16, height: 44, flexDirection: 'row', gap: 8 },
  pill: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#FFFDFB',
    boxShadow: '0 1px 2px rgba(58,31,34,0.16), 0 6px 16px rgba(58,31,34,0.12)',
  },
  chip: { height: 44, borderRadius: 22, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: 6 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  chipText: { fontSize: 13, fontWeight: '600', color: INK, flexShrink: 1 },
  cta: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 56,
    borderRadius: 28,
    paddingHorizontal: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: INK,
    boxShadow: '0 1px 2px rgba(58,31,34,0.3), 0 10px 24px rgba(58,31,34,0.22)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#FFF4F1' },
  ctaPrice: { fontSize: 17, fontWeight: '600', color: 'rgba(255,244,241,0.7)', fontVariant: ['tabular-nums'] },
  pressed: { transform: [{ scale: 0.97 }] },
});
