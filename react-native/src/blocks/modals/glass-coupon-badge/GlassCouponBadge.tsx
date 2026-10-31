import { BlendMode, BlurStyle, Canvas, Picture, Skia, TileMode, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The "you unlocked a coupon" sheet of an invented shop, Parcel. Over the dimmed shop a white sheet
// holds a thick glass coupon with a percent sign: it sways gently in 3D, then spins a full turn
// on its vertical axis — a real slab, its silver-lilac edge showing as it turns side-on — lands
// with a wobble and throws out a ring of sparks; a shine runs across the coupon code below.
// Drag the coupon to turn it in your fingers.
//
// Artwork: assets/badge.png (generated, prompt in assets/PROMPTS.md). The slab is the image drawn
// as a 3D quad (homography, focal 800 pt) eleven times through its 22 pt thickness: the inner
// layers as flat silhouettes tinted from silver to lilac, the face on top.
// Layout (393 x 852 pt): the shop page behind under a 50 % dark veil; sheet from 38 % of the height
// (radius 32); coupon 210 pt centred on the sheet's top edge + 70 pt; heading 28 pt heavy, a line,
// a dashed code chip and a 58 pt button.
//
// autoPlay timeline (LOOP = 6 s):
//   always  sway: yaw 14 sin(2 pi t / 6), pitch 8 sin(4 pi t / 6) deg
//   1.4-3.1 one full turn of yaw (ease-in-out cubic), scale 1 -> 1.08 -> 1
//   3.1-3.9 wobble on landing (yaw 10 e^-5x sin 14x)
//   3.1-3.8 12 sparks fly 40 -> 150 pt out and fade
//   4.2-5.0 a shine sweeps across the code chip
// Dragging turns the coupon (yaw 0.5 deg/pt, pitch -0.4 deg/pt); release springs it back over
// 0.8 s with a wobble; 2 s later the loop starts again.

/** Seconds. */
export const LOOP = 6;
const SIZE = 210;
const THICK = 22;
const LAYERS = 11;
const FOCAL = 800;
const HOLD = 2;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Yaw and pitch (deg), scale and spark progress `t` s into the loop. */
function poseAt(t: number) {
  'worklet';
  const w = (Math.PI * 2 * t) / LOOP;
  const spin = 360 * easeInOutCubic(seg(t, 1.4, 3.1));
  const x = t - 3.1;
  const wobble = x > 0 && x < 0.8 ? 10 * Math.exp(-5 * x) * Math.sin(14 * x) : 0;
  const yaw = 14 * Math.sin(w) + spin + wobble;
  const pitch = 8 * Math.sin(2 * w);
  const scale = 1 + 0.08 * Math.sin(Math.PI * seg(t, 1.4, 3.1));
  const sparks = t >= 3.1 && t < 3.8 ? seg(t, 3.1, 3.8) : 0;
  return { yaw, pitch, scale, sparks };
}

/** The 3x3 matrix (row-major) mapping the w x h rectangle onto the quad p (TL TR BR BL). */
function quadMatrix(w: number, h: number, p: number[]) {
  'worklet';
  const dx1 = p[2] - p[4];
  const dx2 = p[6] - p[4];
  const dx3 = p[0] - p[2] + p[4] - p[6];
  const dy1 = p[3] - p[5];
  const dy2 = p[7] - p[5];
  const dy3 = p[1] - p[3] + p[5] - p[7];
  const den = dx1 * dy2 - dx2 * dy1;
  const g = den === 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den;
  const k = den === 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den;
  const a = p[2] - p[0] + g * p[2];
  const b = p[6] - p[0] + k * p[6];
  const d = p[3] - p[1] + g * p[3];
  const e = p[7] - p[1] + k * p[7];
  return [a / w, b / h, p[0], d / w, e / h, p[1], g / w, k / h, 1];
}

function drawCoupon(c: SkCanvas, img: SkImage, cx: number, cy: number, yawDeg: number, pitchDeg: number, scale: number) {
  'worklet';
  const ay = (yawDeg * Math.PI) / 180;
  const ax = (pitchDeg * Math.PI) / 180;
  const cyw = Math.cos(ay);
  const syw = Math.sin(ay);
  const cp = Math.cos(ax);
  const sp = Math.sin(ax);
  const s = (SIZE * scale) / 2;
  const project = (x: number, y: number, z: number) => {
    'worklet';
    // Yaw about y, then pitch about x.
    const x1 = x * cyw + z * syw;
    const z1 = -x * syw + z * cyw;
    const y2 = y * cp - z1 * sp;
    const z2 = y * sp + z1 * cp;
    const k = FOCAL / (FOCAL - z2);
    return [cx + x1 * k, cy + y2 * k];
  };
  // Which side faces the viewer: the face normal's z after rotation.
  const nz = cyw * cp;
  const front = nz >= 0;
  const W = img.width();
  const H = img.height();
  const src = Skia.XYWHRect(0, 0, W, H);
  for (let n = 0; n < LAYERS; n++) {
    // Back to front as seen by the viewer.
    const i = front ? n : LAYERS - 1 - n;
    const z = -THICK / 2 + (THICK * i) / (LAYERS - 1);
    const corners = [project(-s, -s, z), project(s, -s, z), project(s, s, z), project(-s, s, z)];
    const quad = [corners[0][0], corners[0][1], corners[1][0], corners[1][1], corners[2][0], corners[2][1], corners[3][0], corners[3][1]];
    const outer = n === LAYERS - 1;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    if (!outer) {
      // Edge layers: a flat silhouette from silver (outer) to lilac (middle).
      const mid = 1 - Math.abs((i / (LAYERS - 1)) * 2 - 1);
      const r = Math.round(200 - 60 * mid);
      const g = Math.round(204 - 90 * mid);
      const b = Math.round(232 - 10 * mid);
      p.setColorFilter(Skia.ColorFilter.MakeBlend(Skia.Color(`rgb(${r}, ${g}, ${b})`), BlendMode.SrcIn));
    }
    c.save();
    c.concat(quadMatrix(W, H, quad));
    c.drawImageRect(img, src, src, p);
    c.restore();
  }
}

function drawScene(c: SkCanvas, t: number, yaw: number, pitch: number, scale: number, sparks: number, cx: number, cy: number, img: SkImage | null) {
  'worklet';
  // Glow under the coupon.
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, SIZE * 0.9, [Skia.Color('rgba(124,92,255,0.35)'), Skia.Color('rgba(124,92,255,0)')], null, TileMode.Clamp));
  c.drawCircle(cx, cy, SIZE * 0.9, glow);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Skia.Color('rgba(40,20,90,0.25)'));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  const sw = SIZE * 0.7 * Math.max(0.35, Math.abs(Math.cos((yaw * Math.PI) / 180)));
  c.drawOval(Skia.XYWHRect(cx - sw / 2, cy + SIZE * 0.6, sw, 22), sh);
  if (sparks > 0) {
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2 + 0.2;
      const r = 40 + 110 * (1 - Math.pow(1 - sparks, 2)) + SIZE * 0.35;
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setColor(Skia.Color(i % 3 === 0 ? '#FF8A1F' : i % 3 === 1 ? '#7C5CFF' : '#4FC3FF'));
      p.setAlphaf(1 - sparks);
      c.drawCircle(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 6 * (1 - sparks * 0.6), p);
    }
  }
  if (img) drawCoupon(c, img, cx, cy, yaw, pitch, scale);
  void t;
}

type Props = {
  brand?: string;
  code?: string;
  /** Spins the coupon by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function GlassCouponBadge({ brand = 'Parcel', code = 'SPRING25', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const badge = useImage(require('./assets/badge.png'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const sheetTop = height * 0.38;
  const cx = width / 2;
  const cy = sheetTop + 70 - 40;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const pose = useSharedValue({ yaw: 0, pitch: 0, scale: 1, sparks: 0 });
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const drag = useSharedValue({ yaw: 0, pitch: 0 });

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      pose.set({ yaw: drag.get().yaw, pitch: drag.get().pitch, scale: 1, sparks: 0 });
      return;
    }
    if (manual.get()) {
      const x = now - released.get();
      // Spring back to rest with a wobble, then wait.
      const k = x < 0.8 ? Math.exp(-5 * x) * Math.cos(10 * x) : 0;
      pose.set({ yaw: drag.get().yaw * k, pitch: drag.get().pitch * k, scale: 1, sparks: 0 });
      if (x > 0.8 + HOLD) {
        manual.set(false);
        origin.set(now);
      }
      return;
    }
    if (!autoPlay) return;
    const t = (now - origin.get()) % LOOP;
    clock.set(t);
    pose.set(poseAt(t));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const p = pose.get();
    drawScene(canvas, clock.get(), p.yaw, p.pitch, p.scale, p.sparks, cx, cy, badge);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, badge]);

  const shineStyle = useAnimatedStyle(() => {
    const u = seg(clock.get(), 4.2, 5.0);
    return { opacity: u > 0 && u < 1 ? 1 : 0, transform: [{ translateX: -120 + 420 * u }, { rotate: '18deg' }] };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      const p = pose.get();
      drag.set({ yaw: ((p.yaw % 360) + 540) % 360 - 180, pitch: p.pitch });
      scheduleOnRN(haptic);
    })
    .onChange((e) => {
      const d = drag.get();
      drag.set({ yaw: d.yaw + e.changeX * 0.5, pitch: Math.max(-50, Math.min(50, d.pitch - e.changeY * 0.4)) });
    })
    .onFinalize(() => {
      touching.set(false);
      // Spring back by the shortest way.
      const d = drag.get();
      drag.set({ yaw: ((d.yaw % 360) + 540) % 360 - 180, pitch: d.pitch });
      released.set(elapsed.get());
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      {/* The shop behind the sheet. */}
      <View style={[styles.shop, { paddingTop: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.meta}>Bag (3)</Text>
        </View>
        <View style={styles.tiles}>
          {['#F6C9B8', '#C9D8F6', '#D9F0C9', '#F0E2B8'].map((c) => (
            <View key={c} style={[styles.tile, { backgroundColor: c }]} />
          ))}
        </View>
      </View>
      <View style={styles.veil} pointerEvents="none" />

      <View style={[styles.sheet, { top: sheetTop }]}>
        <View style={styles.grabber} />
        <View style={[styles.sheetBody, { paddingBottom: insets.bottom + 14 }]}>
          <Text style={styles.kicker}>Reward unlocked</Text>
          <Text style={styles.heading}>25% off your next order</Text>
          <Text style={styles.sub}>For hitting five orders this spring. Valid on everything until 31 May.</Text>
          <View style={styles.chip}>
            <Text style={styles.code}>{code}</Text>
            <Text style={styles.copy}>Copy</Text>
            <Animated.View style={[styles.shine, shineStyle]} pointerEvents="none">
              <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
            </Animated.View>
          </View>
          <View style={styles.button}>
            <Text style={styles.action}>Apply coupon</Text>
          </View>
        </View>
      </View>

      <GestureDetector gesture={pan}>
        <View style={[styles.hit, { left: cx - SIZE / 2 - 20, top: cy - SIZE / 2 - 20, width: SIZE + 40, height: SIZE + 40 }]} accessible accessibilityLabel="Coupon. Drag to turn it." />
      </GestureDetector>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F2EF' },
  shop: { position: 'absolute', left: 20, right: 20, top: 0 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '900', color: '#16141A' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(22,20,26,0.6)' },
  tiles: { marginTop: 18, flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tile: { width: '47%', height: 170, borderRadius: 20 },
  veil: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: 'rgba(12,8,24,0.5)' },
  sheet: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: '#FFFFFF', borderTopLeftRadius: 32, borderTopRightRadius: 32 },
  grabber: { alignSelf: 'center', marginTop: 8, width: 40, height: 5, borderRadius: 3, backgroundColor: 'rgba(22,20,26,0.15)' },
  sheetBody: { flex: 1, paddingHorizontal: 24, justifyContent: 'flex-end' },
  kicker: { textAlign: 'center', fontSize: 13, fontWeight: '800', letterSpacing: 1.6, color: '#7C5CFF', textTransform: 'uppercase' },
  heading: { marginTop: 8, textAlign: 'center', fontSize: 28, lineHeight: 32, fontWeight: '900', color: '#16141A', letterSpacing: -0.8 },
  sub: { marginTop: 8, textAlign: 'center', fontSize: 15, lineHeight: 21, color: 'rgba(22,20,26,0.6)' },
  chip: { marginTop: 18, height: 56, borderRadius: 16, borderWidth: 1.5, borderStyle: 'dashed', borderColor: 'rgba(124,92,255,0.5)', backgroundColor: 'rgba(124,92,255,0.07)', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 18, overflow: 'hidden' },
  code: { fontSize: 20, fontWeight: '900', color: '#16141A', letterSpacing: 3 },
  copy: { fontSize: 15, fontWeight: '700', color: '#7C5CFF' },
  shine: { position: 'absolute', top: -20, bottom: -20, width: 60 },
  button: { marginTop: 14, height: 58, borderRadius: 29, backgroundColor: '#16141A', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
  hit: { position: 'absolute', zIndex: 2 },
});
