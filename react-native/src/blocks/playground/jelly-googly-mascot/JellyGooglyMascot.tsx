import { BlurStyle, Canvas, PaintStyle, Picture, Skia, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A toy: a glossy jelly blob with two googly eyes. Its outline never quite stops moving, it leans
// and stretches towards your finger, and its pupils follow the finger a beat behind. Poke it and
// it squashes flat, blinks, wobbles back up — and turns into the next flavour: lemon, blueberry,
// grape, each with its own room colour. Drag to be watched; tap to poke.
//
// The blob is drawn in code: a closed curve through 48 points whose radius is a sum of slow
// sines (periodic in the loop), shaded with a radial gradient, a darker rim, a soft specular
// highlight and a floor shadow; the eyes are white ovals with black pupils.
// Layout (393 x 852 pt): 34 pt top row; title 40 pt heavy; blob radius 128 pt centred at 50 %
// of the height; three flavour dots and a line at the bottom.
//
// autoPlay timeline (LOOP = 8 s): a ghost finger circles the blob along a closed curve (one
// lap per loop); the blob leans up to 18 pt towards it and stretches up to 8 %; pupils follow the
// finger as it was 0.12 s before. Pokes at 1.6, 4.3 and 7.0 s: a squash 1 + 0.16 e^-4x sin 13x,
// a 0.18 s blink and a 0.3 s colour cross-fade to the next flavour, so the loop ends on lemon.
// Dragging moves the target; a tap pokes; 2 s after the last touch the ghost finger takes over
// again over 0.6 s.

/** Seconds. */
export const LOOP = 8;
const R = 128;
const POINTS = 48;
const POKES = [1.6, 4.3, 7.0];
const HOLD = 2;
const BLEND = 0.6;

type Flavour = { name: string; light: string; deep: string; room: string; ink: string };
const FLAVOURS: Flavour[] = [
  { name: 'Lemon', light: '#FFE45C', deep: '#F2A100', room: '#FFF3D6', ink: '#4A3500' },
  { name: 'Blueberry', light: '#8FB0FF', deep: '#3553E6', room: '#C3CDFF', ink: '#14205E' },
  { name: 'Grape', light: '#F2B8FF', deep: '#A855E8', room: '#A98BF5', ink: '#2B0F4F' },
];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}
function hexA(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return Skia.Color(`rgb(${r}, ${g}, ${bl})`);
}

/** The ghost finger's point (relative to the blob's centre), `t` s into the loop. */
function ghostAt(t: number) {
  'worklet';
  const a = (t / LOOP) * Math.PI * 2;
  return { x: 150 * Math.sin(a) + 30 * Math.sin(3 * a), y: -40 + 150 * Math.sin(2 * a) * 0.8 + 40 * Math.cos(a) };
}
/** Squash amount, blink (0 open .. 1 shut) and flavour (fractional) `t` s into the loop. */
function pokeAt(t: number) {
  'worklet';
  let squash = 0;
  let blink = 0;
  let flavour = 0;
  for (let i = 0; i < POKES.length; i++) {
    const x = t - POKES[i];
    if (x > 0 && x < 1.6) squash += 0.16 * Math.exp(-4 * x) * Math.sin(13 * x);
    if (x > 0 && x < 0.18) blink = Math.max(blink, Math.sin((Math.PI * x) / 0.18));
    flavour += smooth(x / 0.3);
  }
  // The squash of the last poke runs past the loop's end into its start.
  const wrap = t + LOOP - POKES[2];
  if (wrap > 0 && wrap < 1.6) squash += 0.16 * Math.exp(-4 * wrap) * Math.sin(13 * wrap);
  return { squash, blink, flavour: flavour % 3 };
}

function drawBlob(c: SkCanvas, t: number, cx: number, cy: number, tx: number, ty: number, px: number, py: number, squash: number, blink: number, flavour: number) {
  'worklet';
  const base = Math.floor(flavour);
  const f = flavour - base;
  const A = FLAVOURS[base % 3];
  const B = FLAVOURS[(base + 1) % 3];
  const light = hexA(A.light, B.light, f);
  const deep = hexA(A.deep, B.deep, f);
  // Lean and stretch towards the target.
  const dist = Math.hypot(tx, ty);
  const lean = Math.min(18, dist * 0.09);
  const ux = dist > 0.001 ? tx / dist : 0;
  const uy = dist > 0.001 ? ty / dist : 0;
  const ox = cx + ux * lean;
  const oy = cy + uy * lean;
  const stretch = Math.min(0.08, dist / 2400);
  const w = (Math.PI * 2) / LOOP;

  // Floor shadow.
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Skia.Color('rgba(40,20,60,0.22)'));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
  c.drawOval(Skia.XYWHRect(cx - R * (0.85 + squash), cy + R * 0.92, R * 2 * (0.85 + squash), 34), sh);

  const pts: number[] = [];
  for (let i = 0; i < POINTS; i++) {
    const th = (i / POINTS) * Math.PI * 2;
    const r = R * (1 + 0.045 * Math.sin(3 * th + 2 * w * t) + 0.03 * Math.sin(5 * th - 3 * w * t) + 0.02 * Math.sin(2 * th + w * t));
    let x = Math.cos(th) * r;
    let y = Math.sin(th) * r;
    // Stretch along the lean direction.
    const along = x * ux + y * uy;
    x += ux * along * stretch;
    y += uy * along * stretch;
    // Squash: wider and flatter, sitting on the floor.
    x *= 1 + squash;
    y = y * (1 - squash) + R * squash;
    pts.push(ox + x, oy + y);
  }
  const path = Skia.Path.Make();
  const n = POINTS;
  const mid = (i: number) => {
    'worklet';
    const a = i % n;
    const b = (i + 1) % n;
    return [(pts[a * 2] + pts[b * 2]) / 2, (pts[a * 2 + 1] + pts[b * 2 + 1]) / 2];
  };
  const m0 = mid(0);
  path.moveTo(m0[0], m0[1]);
  for (let i = 1; i <= n; i++) {
    const p = i % n;
    const m = mid(i);
    path.quadTo(pts[p * 2], pts[p * 2 + 1], m[0], m[1]);
  }
  path.close();

  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(Skia.Shader.MakeRadialGradient({ x: ox - R * 0.35, y: oy - R * 0.4 }, R * 1.7, [light, deep], [0.1, 1], TileMode.Clamp));
  c.drawPath(path, body);
  c.save();
  c.clipPath(path, 1, true);
  // A darker rim inside the edge and a bounce light at the bottom.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(26);
  rim.setColor(deep);
  rim.setAlphaf(0.35);
  rim.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawPath(path, rim);
  const bounce = Skia.Paint();
  bounce.setAntiAlias(true);
  bounce.setColor(Skia.Color('rgba(255,255,255,0.35)'));
  bounce.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawOval(Skia.XYWHRect(ox - R * 0.5, oy + R * 0.62, R, R * 0.3), bounce);
  c.restore();
  const spec = Skia.Paint();
  spec.setAntiAlias(true);
  spec.setColor(Skia.Color('rgba(255,255,255,0.8)'));
  spec.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.save();
  c.rotate(-28, ox - R * 0.42, oy - R * 0.5);
  c.drawOval(Skia.XYWHRect(ox - R * 0.62, oy - R * 0.64, R * 0.42, R * 0.22), spec);
  c.restore();

  // Eyes: ride on the body, a little further along the lean; pupils look at (px, py).
  const ex = ox + ux * lean * 0.6;
  const ey = oy - R * 0.12 + uy * lean * 0.6 + R * squash * 0.8;
  for (let s = -1; s <= 1; s += 2) {
    const cxE = ex + s * R * 0.4;
    const rx = R * 0.27 * (1 + squash);
    const ry = R * 0.31 * (1 - squash) * (1 - 0.92 * blink);
    const white = Skia.Paint();
    white.setAntiAlias(true);
    white.setColor(Skia.Color('#FFFFFF'));
    c.drawOval(Skia.XYWHRect(cxE - rx, ey - ry, rx * 2, ry * 2), white);
    const dx = px - (cxE - cx);
    const dy = py - (ey - cy);
    const d = Math.max(1, Math.hypot(dx, dy));
    const reach = Math.min(1, d / 120);
    const qx = cxE + (dx / d) * rx * 0.42 * reach;
    const qy = ey + (dy / d) * ry * 0.42 * reach;
    const pupil = Skia.Paint();
    pupil.setAntiAlias(true);
    pupil.setColor(Skia.Color('#0B0B0F'));
    const pr = R * 0.155;
    c.save();
    c.clipPath((() => {
      const e = Skia.Path.Make();
      e.addOval(Skia.XYWHRect(cxE - rx, ey - ry, rx * 2, ry * 2));
      return e;
    })(), 1, true);
    c.drawOval(Skia.XYWHRect(qx - pr, qy - pr * 1.1, pr * 2, pr * 2.2), pupil);
    const glint = Skia.Paint();
    glint.setAntiAlias(true);
    glint.setColor(Skia.Color('#FFFFFF'));
    c.drawCircle(qx - pr * 0.35, qy - pr * 0.45, pr * 0.22, glint);
    c.restore();
  }
  // A small mouth.
  const mouth = Skia.Paint();
  mouth.setAntiAlias(true);
  mouth.setColor(Skia.Color('#0B0B0F'));
  c.drawOval(Skia.XYWHRect(ex - 6, ey + R * 0.3 - 4 * (1 - squash), 12, 9 * (1 - squash) + 2), mouth);
}

function Name({ k, poke }: { k: number; poke: SharedValue<{ squash: number; blink: number; flavour: number }> }) {
  const style = useAnimatedStyle(() => {
    const fl = poke.get().flavour;
    const b = Math.floor(fl);
    let d = Math.abs(fl - k);
    d = Math.min(d, 3 - d);
    return { opacity: 1 - clamp01(d / 0.5), color: mixColor(FLAVOURS[b % 3].ink, FLAVOURS[(b + 1) % 3].ink, fl - b) };
  });
  return <Animated.Text style={[styles.name, style]}>{FLAVOURS[k].name}</Animated.Text>;
}

type Props = {
  title?: string;
  /** Plays with the blob by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function JellyGooglyMascot({ title = 'Poke the\njelly', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
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
  // Target and pupil target relative to the centre; squash, blink and flavour.
  const target = useSharedValue({ x: 0, y: -40, px: 0, py: -40 });
  const poke = useSharedValue({ squash: 0, blink: 0, flavour: 0 });
  const finger = useSharedValue({ x: 0, y: 0 });
  const touching = useSharedValue(false);
  const lastTouch = useSharedValue(-10);
  const userPoke = useSharedValue(-10);
  const userFlavour = useSharedValue(0);
  const manual = useSharedValue(false);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = autoPlay ? (((now - origin.get()) % LOOP) + LOOP) % LOOP : 0;
    clock.set(t);
    const g = autoPlay ? ghostAt(t) : { x: 0, y: -40 };
    const gp = autoPlay ? ghostAt((t - 0.12 + LOOP) % LOOP) : g;
    const auto = pokeAt(t);
    if (manual.get()) {
      const since = now - lastTouch.get();
      const back = touching.get() ? 0 : smooth((since - HOLD) / BLEND);
      const f = finger.get();
      const cur = target.get();
      // Pupils trail the finger by easing towards it.
      const k = 1 - Math.exp(-14 * dt);
      const px = cur.px + (f.x - cur.px) * k;
      const py = cur.py + (f.y - cur.py) * k;
      target.set({ x: f.x + (g.x - f.x) * back, y: f.y + (g.y - f.y) * back, px: px + (gp.x - px) * back, py: py + (gp.y - py) * back });
      const x = now - userPoke.get();
      const squash = x > 0 && x < 1.6 ? 0.16 * Math.exp(-4 * x) * Math.sin(13 * x) : 0;
      const blink = x > 0 && x < 0.18 ? Math.sin((Math.PI * x) / 0.18) : 0;
      const fl = (userFlavour.get() + smooth(x / 0.3)) % 3;
      poke.set({ squash, blink, flavour: back >= 1 ? auto.flavour : fl });
      if (back >= 1) {
        manual.set(false);
        // Resume at the loop time that shows the flavour now on screen.
        const shown = Math.round(fl) % 3;
        const starts = [0, POKES[0] + 0.4, POKES[1] + 0.4];
        origin.set(now - starts[shown]);
      }
      return;
    }
    target.set({ x: g.x, y: g.y, px: gp.x, py: gp.y });
    poke.set(autoPlay ? auto : { squash: 0, blink: 0, flavour: 0 });
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const tg = target.get();
    const p = poke.get();
    drawBlob(canvas, clock.get(), cx, cy, tg.x, tg.y, tg.px, tg.py, p.squash, p.blink, p.flavour);
    // The ghost finger while the loop plays.
    if (!manual.get() && autoPlay) {
      const dot = Skia.Paint();
      dot.setAntiAlias(true);
      dot.setColor(Skia.Color('rgba(255,255,255,0.55)'));
      canvas.drawCircle(cx + tg.x, cy + tg.y, 16, dot);
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(2);
      ring.setColor(Skia.Color('rgba(255,255,255,0.9)'));
      canvas.drawCircle(cx + tg.x, cy + tg.y, 16, ring);
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, autoPlay]);

  const roomStyle = useAnimatedStyle(() => {
    const fl = poke.get().flavour;
    const b = Math.floor(fl);
    return { backgroundColor: mixColor(FLAVOURS[b % 3].room, FLAVOURS[(b + 1) % 3].room, fl - b) };
  });
  const inkStyle = useAnimatedStyle(() => {
    const fl = poke.get().flavour;
    const b = Math.floor(fl);
    return { color: mixColor(FLAVOURS[b % 3].ink, FLAVOURS[(b + 1) % 3].ink, fl - b) };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const takeOver = () => {
    'worklet';
    if (!manual.get()) {
      manual.set(true);
      userFlavour.set(Math.round(poke.get().flavour) % 3);
      userPoke.set(-10);
    }
    lastTouch.set(elapsed.get());
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      touching.set(true);
      finger.set({ x: e.x - cx, y: e.y - cy });
    })
    .onUpdate((e) => {
      finger.set({ x: e.x - cx, y: e.y - cy });
      lastTouch.set(elapsed.get());
    })
    .onFinalize(() => {
      touching.set(false);
      lastTouch.set(elapsed.get());
    });
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd((e) => {
      takeOver();
      finger.set({ x: e.x - cx, y: e.y - cy });
      // The next flavour starts from the one now showing.
      const now = elapsed.get();
      const x = now - userPoke.get();
      userFlavour.set((userFlavour.get() + (x > 0 && x < 10 ? 1 : 0)) % 3);
      userPoke.set(now);
      scheduleOnRN(haptic);
    });

  return (
    <Animated.View style={[styles.screen, roomStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={Gesture.Simultaneous(pan, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Animated.Text style={[styles.brand, inkStyle]}>blobby</Animated.Text>
              <Animated.Text style={[styles.meta, inkStyle]}>Playground</Animated.Text>
            </View>
            <Animated.Text style={[styles.title, inkStyle]}>{title}</Animated.Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A jelly blob with googly eyes. Drag to be watched, tap to poke.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.footer, { paddingBottom: insets.bottom + 24 }]} pointerEvents="none">
            <View style={styles.names}>
              {FLAVOURS.map((f, k) => (
                <Name key={f.name} k={k} poke={poke} />
              ))}
            </View>
            <Animated.Text style={[styles.hint, inkStyle]}>Drag to be watched · tap to poke</Animated.Text>
          </View>
        </View>
      </GestureDetector>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '900', letterSpacing: -0.5 },
  meta: { fontSize: 14, fontWeight: '600', opacity: 0.6 },
  title: { marginTop: 14, fontSize: 46, lineHeight: 48, fontWeight: '900', letterSpacing: -1.8 },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  names: { height: 40, alignSelf: 'stretch', alignItems: 'center' },
  name: { position: 'absolute', fontSize: 30, fontWeight: '900', letterSpacing: -0.8 },
  hint: { marginTop: 6, fontSize: 15, fontWeight: '600', opacity: 0.6 },
});
