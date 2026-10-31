import { Canvas, Picture, Skia, useFont, type SkCanvas, type SkFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The line-up page of an invented music festival, Orbit Fest: twelve gig posters stand on a
// tilted ring that turns slowly in 3D under a huge "ORBIT" — each a bold colour field with a
// condensed initial, the act's name and its day — seen in true perspective, the far side of the
// ring dimmer and showing the posters' backs. The act on the poster turning to the front is
// named below. Drag sideways to spin the ring; it keeps turning and settles.
//
// Geometry: posters 92 x 128 pt on a circle of radius 150 pt in the x-z plane, tilted -16 deg (seen from above)
// about x and rolled 8 deg, centred at 50 % of the height, focal length 820 pt. Each poster is
// drawn with the homography that maps its rectangle onto its projected corners; the ones facing
// away are drawn as their dark backs; all are sorted back to front.
//
// autoPlay timeline (LOOP = 9 s): the ring turns once per loop (linear) and its tilt breathes
// +-3 deg twice per loop; the caption follows the poster nearest the front (fades over a twelfth
// of a turn). Dragging turns the ring with the finger and keeps spinning after release (decay
// 2.5 / s); 2 s later it eases to the nearest poster over 0.6 s and the loop goes on from there.

/** Seconds. */
export const LOOP = 9;
const N = 12;
const RING = 150;
const PW = 92;
const PH = 128;
const FOCAL = 820;
const TILT = (-16 * Math.PI) / 180;
const ROLL = (8 * Math.PI) / 180;
const HOLD = 2;
const SETTLE = 0.6;

type Act = { name: string; day: string; bg: string; ink: string; mark: string };
const ACTS: Act[] = [
  { name: 'LUMEN DUO', day: 'FRI', bg: '#E3241B', ink: '#FFFFFF', mark: 'L' },
  { name: 'AMA VALE', day: 'FRI', bg: '#111111', ink: '#F2EFE8', mark: 'A' },
  { name: 'THE ORCHARDS', day: 'SAT', bg: '#F2D13B', ink: '#111111', mark: 'O' },
  { name: 'KIYO', day: 'SAT', bg: '#1F3BD6', ink: '#FFFFFF', mark: 'K' },
  { name: 'RAFA & SOL', day: 'SUN', bg: '#F2EFE8', ink: '#111111', mark: 'R' },
  { name: 'MONO BLOOM', day: 'SUN', bg: '#FF6A2B', ink: '#111111', mark: 'M' },
];

const ANTON = require('./assets/Anton-Regular.ttf');
const FAMILY = 'PosterRingBand-Anton';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Rotate (x, y, z) by the ring's tilt (about x) and roll (about z), then project. */
function project(x: number, y: number, z: number, tilt: number, cx: number, cy: number) {
  'worklet';
  const ct = Math.cos(tilt);
  const st = Math.sin(tilt);
  const y1 = y * ct - z * st;
  const z1 = y * st + z * ct;
  const cr = Math.cos(ROLL);
  const sr = Math.sin(ROLL);
  const x2 = x * cr - y1 * sr;
  const y2 = x * sr + y1 * cr;
  const s = FOCAL / (FOCAL - z1);
  return { x: cx + x2 * s, y: cy + y2 * s, z: z1 };
}

/** The 3x3 matrix (row-major) mapping the w x h rectangle onto the quad p0 p1 p2 p3 (TL TR BR BL). */
function quadMatrix(w: number, h: number, p: number[]) {
  'worklet';
  const x0 = p[0];
  const y0 = p[1];
  const x1 = p[2];
  const y1 = p[3];
  const x2 = p[4];
  const y2 = p[5];
  const x3 = p[6];
  const y3 = p[7];
  const dx1 = x1 - x2;
  const dx2 = x3 - x2;
  const dx3 = x0 - x1 + x2 - x3;
  const dy1 = y1 - y2;
  const dy2 = y3 - y2;
  const dy3 = y0 - y1 + y2 - y3;
  const den = dx1 * dy2 - dx2 * dy1;
  const g = den === 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den;
  const k = den === 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den;
  const a = x1 - x0 + g * x1;
  const b = x3 - x0 + k * x3;
  const d = y1 - y0 + g * y1;
  const e = y3 - y0 + k * y3;
  return [a / w, b / h, x0, d / w, e / h, y0, g / w, k / h, 1];
}

function fill(color: string, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

/** Anton text at `size` pt with its left baseline at (x, y). */
function word(c: SkCanvas, font: SkFont, text: string, x: number, y: number, size: number, color: string) {
  'worklet';
  const k = size / 100;
  c.save();
  c.translate(x, y);
  c.scale(k, k);
  c.drawText(text, 0, 0, fill(color, 1), font);
  c.restore();
}

function drawPoster(c: SkCanvas, act: Act, font: SkFont) {
  'worklet';
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, PW, PH), 4, 4), fill(act.bg, 1));
  word(c, font, act.mark, 7, 92, 92, act.ink);
  c.drawRect(Skia.XYWHRect(7, 99, PW - 14, 1.5), fill(act.ink, 0.6));
  word(c, font, act.name, 7, 113, 11, act.ink);
  word(c, font, act.day, PW - 25, 123, 9, act.ink);
  word(c, font, 'ORBIT 26', 7, 123, 8, act.ink);
}

function drawRing(c: SkCanvas, turn: number, tilt: number, cx: number, cy: number, font: SkFont) {
  'worklet';
  const items: { j: number; z: number; quad: number[]; facing: boolean }[] = [];
  for (let j = 0; j < N; j++) {
    const th = turn + (j * Math.PI * 2) / N;
    const sx = Math.sin(th);
    const cz = Math.cos(th);
    // Tangent along the ring, from the poster's left edge to its right edge.
    const tx = cz;
    const tz = -sx;
    const px = RING * sx;
    const pz = RING * cz;
    const corners = [
      [px - (tx * PW) / 2, -PH / 2, pz - (tz * PW) / 2],
      [px + (tx * PW) / 2, -PH / 2, pz + (tz * PW) / 2],
      [px + (tx * PW) / 2, PH / 2, pz + (tz * PW) / 2],
      [px - (tx * PW) / 2, PH / 2, pz - (tz * PW) / 2],
    ];
    const quad: number[] = [];
    let z = 0;
    for (let q = 0; q < 4; q++) {
      const pr = project(corners[q][0], corners[q][1], corners[q][2], tilt, cx, cy);
      quad.push(pr.x, pr.y);
      z += pr.z / 4;
    }
    // Facing the viewer when the projected corners run clockwise.
    const cross = (quad[2] - quad[0]) * (quad[7] - quad[1]) - (quad[3] - quad[1]) * (quad[6] - quad[0]);
    items.push({ j, z, quad, facing: cross > 0 });
  }
  items.sort((a, b) => a.z - b.z);
  for (let n = 0; n < items.length; n++) {
    const it = items[n];
    const depth = clamp01((it.z + RING) / (2 * RING));
    c.save();
    c.concat(quadMatrix(PW, PH, it.quad));
    if (it.facing) {
      drawPoster(c, ACTS[it.j % ACTS.length], font);
    } else {
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, PW, PH), 4, 4), fill('#2A2A2A', 1));
      c.drawRect(Skia.XYWHRect(10, 10, PW - 20, PH - 20), fill('#3A3A3A', 1));
    }
    // Far posters sit in shade.
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, PW, PH), 4, 4), fill('#EDEBE6', 0.55 * (1 - depth)));
    c.restore();
  }
}

/** The act nearest the front for a ring turned by `turn`. */
function frontOf(turn: number) {
  'worklet';
  const step = (Math.PI * 2) / N;
  const j = Math.round(-turn / step);
  return ((j % N) + N) % N;
}

function Caption({ k, turn }: { k: number; turn: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const step = (Math.PI * 2) / N;
    const pos = -turn.get() / step;
    let d = Math.abs(((((pos - k) % N) + N + N / 2) % N) - N / 2);
    d = Math.min(d, N - d);
    const o = 1 - clamp01(d / 0.5);
    return { opacity: o, transform: [{ translateY: 10 * (1 - o) }] };
  });
  const act = ACTS[k % ACTS.length];
  return (
    <Animated.View style={[styles.caption, style]}>
      <Text style={styles.act}>{act.name}</Text>
      <Text style={styles.slot}>{`${act.day} · Main stage · ${['20:00', '21:30', '23:00'][k % 3]}`}</Text>
    </Animated.View>
  );
}

type Props = {
  title?: string;
  dates?: string;
  action?: string;
  /** Turns the ring by itself, once every LOOP seconds. */
  autoPlay?: boolean;
};

export function PosterRingBand({ title = 'ORBIT', dates = 'Fest 26 · 12–14 Jun · Porto Vale', action = 'Get festival passes', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FAMILY]: ANTON });
  const font = useFont(ANTON, 100);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.5;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const turn = useSharedValue(0);
  const spin = useSharedValue(0);
  const velocity = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const settleFrom = useSharedValue(0);
  const lastX = useSharedValue(0);
  const lastFront = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    clock.set(t);
    if (touching.get()) {
      turn.set(spin.get());
      return;
    }
    if (manual.get()) {
      const since = now - released.get();
      if (since < HOLD) {
        spin.set(spin.get() + velocity.get() * dt);
        velocity.set(velocity.get() * Math.exp(-2.5 * dt));
        settleFrom.set(spin.get());
      } else {
        const step = (Math.PI * 2) / N;
        const target = Math.round(settleFrom.get() / step) * step;
        spin.set(settleFrom.get() + (target - settleFrom.get()) * easeInOutCubic(clamp01((since - HOLD) / SETTLE)));
        if (since > HOLD + SETTLE) {
          manual.set(false);
          // Carry on turning from this angle: the loop clock is set so that its turn matches.
          const frac = ((((-target / (Math.PI * 2)) % 1) + 1) % 1);
          origin.set(now - (1 - frac) * LOOP);
        }
      }
      turn.set(spin.get());
      return;
    }
    turn.set(autoPlay ? -(Math.PI * 2 * t) / LOOP : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (font) drawRing(canvas, turn.get(), TILT + ((3 * Math.PI) / 180) * Math.sin((clock.get() / LOOP) * Math.PI * 4), cx, cy, font);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, font]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin((e) => {
      touching.set(true);
      manual.set(true);
      spin.set(turn.get());
      velocity.set(0);
      lastX.set(e.x);
    })
    .onUpdate((e) => {
      spin.set(spin.get() + (e.x - lastX.get()) / RING);
      lastX.set(e.x);
      velocity.set(e.velocityX / RING);
      const f = frontOf(spin.get());
      if (f !== lastFront.get()) {
        lastFront.set(f);
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 6 }]} pointerEvents="none">
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.dates}>{dates}</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Festival line-up ring. Drag to spin.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.captions, { top: cy + 150 }]} pointerEvents="none">
            {Array.from({ length: N }, (_, k) => (
              <Caption key={k} k={k} turn={turn} />
            ))}
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EDEBE6' },
  head: { position: 'absolute', left: 20, right: 20 },
  title: { fontFamily: FAMILY, fontSize: 132, lineHeight: 150, color: '#111111', letterSpacing: -2 },
  dates: { marginTop: -10, fontSize: 15, fontWeight: '700', color: '#111111', letterSpacing: 0.3 },
  captions: { position: 'absolute', left: 20, right: 20, height: 64 },
  caption: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  act: { fontFamily: FAMILY, fontSize: 36, lineHeight: 44, color: '#111111' },
  slot: { fontSize: 14, fontWeight: '600', color: '#6E6B64' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  button: { height: 58, borderRadius: 4, backgroundColor: '#111111', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 16, fontWeight: '700', color: '#EDEBE6', letterSpacing: 0.3 },
});
