import { BlurStyle, Canvas, Picture, Skia, TileMode, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The feed of an invented friends' photo app, Pinboard: every friend's latest photo is a round
// pin badge, and the badges are threaded on one vertical column like a stack of coins seen in
// perspective. The badge in the middle faces you, large; the ones above and below it are tipped
// nearly edge-on so they pile up thin, showing their rims, darker the further they are. The column
// rolls on badge by badge: the next one tips up to face you as the last one tips away. The
// friend's name and caption below change with it. Drag up or down to roll the column.
//
// Photos: assets/*.jpg (generated, prompts in assets/PROMPTS.md), 18 slots repeating the six.
// Layout (393 x 852 pt): 34 pt top row; column centred at x = 50 %, y = 44 % of the height: the
// middle badge 180 pt, its neighbours 132 pt; the first neighbour 118 pt away, then 21 pt per
// badge; tip = 80 deg x min(|d|, 1); name 30 pt heavy, caption and a reply bar at the bottom.
//
// autoPlay timeline (LOOP = 7.2 s): six badges of 1.2 s; each roll runs 0.75-1.2 s of its stop
// (ease-in-out cubic); after six the frame repeats exactly because the photos repeat every six
// slots. The middle badge breathes 1 + 0.015 sin.
// Dragging: 90 pt per badge with a selection haptic; release snaps over 0.35 s, it stays 2 s,
// then the loop carries on from there.

/** Seconds. */
export const LOOP = 7.2;
const STOP = 1.2;
const ROLL = 0.75;
const SLOTS = 18;
const BIG = 214;
const SMALL = 150;
const FIRST_GAP = 136;
const GAP = 23;
const TIP = 80;
const PER = 90;
const HOLD = 2;
const SNAP = 0.35;

type Friend = { name: string; caption: string; when: string };
const FRIENDS: Friend[] = [
  { name: 'Mika', caption: 'Biscuit learned to sit. Took a week.', when: '2 min' },
  { name: 'Theo', caption: 'Saturday tax: one sunbeam, all of it.', when: '18 min' },
  { name: 'Ana', caption: 'Found the noodle bar under the bridge.', when: '1 h' },
  { name: 'Jules', caption: 'New leaf. I am unreasonably proud.', when: '3 h' },
  { name: 'Rui', caption: 'Out of office until further notice.', when: '5 h' },
  { name: 'Noor', caption: 'Painted the bike. Then the wall. Oops.', when: '8 h' },
];
const N = FRIENDS.length;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
/** Slot j's signed distance from the middle for column position `index`, in [-SLOTS/2, SLOTS/2). */
function distOf(j: number, index: number) {
  'worklet';
  let d = (j - index) % SLOTS;
  if (d < -SLOTS / 2) d += SLOTS;
  if (d >= SLOTS / 2) d -= SLOTS;
  return d;
}
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  return k + easeInOutCubic(clamp01((u - ROLL) / (STOP - ROLL)));
}
/** Distance to friend k's nearest slot from the middle. */
function friendDist(k: number, index: number) {
  'worklet';
  let d = (((k - index) % N) + N) % N;
  if (d > N / 2) d -= N;
  return Math.abs(d);
}

function drawColumn(c: SkCanvas, index: number, t: number, cx: number, cy: number, images: (SkImage | null)[]) {
  'worklet';
  const order: number[] = [];
  for (let j = 0; j < SLOTS; j++) order.push(j);
  order.sort((a, b) => Math.abs(distOf(b, index)) - Math.abs(distOf(a, index)));
  for (let n = 0; n < order.length; n++) {
    const j = order[n];
    const d = distOf(j, index);
    const a = Math.abs(d);
    if (a > 8.5) continue;
    const near = Math.min(a, 1);
    const y = cy + Math.sign(d) * (FIRST_GAP * near + GAP * Math.max(0, a - 1));
    const size = (BIG - (BIG - SMALL) * near) * (a < 0.5 ? 1 + 0.015 * Math.sin((t / LOOP) * Math.PI * 12) : 1);
    const tip = (TIP * near * Math.PI) / 180;
    const sy = Math.max(0.12, Math.cos(tip));
    const w = size;
    const h = size * sy;
    // Rim thickness shows on the side facing the middle.
    const thick = 9 * Math.sin(tip);
    const toward = -Math.sign(d);
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setColor(Skia.Color('#D9D4CC'));
    c.drawOval(Skia.XYWHRect(cx - w / 2, y - h / 2 + toward * thick, w, h), rim);
    const shade = Skia.Paint();
    shade.setAntiAlias(true);
    shade.setColor(Skia.Color('rgba(0,0,0,0.35)'));
    c.drawOval(Skia.XYWHRect(cx - w / 2, y - h / 2 + toward * thick, w, h), shade);
    if (a < 0.6) {
      const glow = Skia.Paint();
      glow.setAntiAlias(true);
      glow.setColor(Skia.Color('rgba(0,0,0,0.5)'));
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
      c.drawOval(Skia.XYWHRect(cx - w / 2 + 6, y - h / 2 + 16, w - 12, h), glow);
    }
    // White rim, then the photo inset in it.
    const face = Skia.Paint();
    face.setAntiAlias(true);
    face.setColor(Skia.Color('#F5F2EC'));
    c.drawOval(Skia.XYWHRect(cx - w / 2, y - h / 2, w, h), face);
    const img = images[j % N];
    const inset = 0.06;
    if (img) {
      c.save();
      const clip = Skia.Path.Make();
      clip.addOval(Skia.XYWHRect(cx - w / 2 + w * inset, y - h / 2 + h * inset, w * (1 - 2 * inset), h * (1 - 2 * inset)));
      c.clipPath(clip, 1, true);
      c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(cx - w / 2 + w * inset, y - h / 2 + h * inset, w * (1 - 2 * inset), h * (1 - 2 * inset)), Skia.Paint());
      c.restore();
    }
    // Gloss and distance dimming.
    const gloss = Skia.Paint();
    gloss.setAntiAlias(true);
    gloss.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: y - h / 2 }, { x: 0, y: y + h / 2 }, [Skia.Color('rgba(255,255,255,0.28)'), Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(0,0,0,0.12)')], [0, 0.45, 1], TileMode.Clamp));
    c.drawOval(Skia.XYWHRect(cx - w / 2, y - h / 2, w, h), gloss);
    const dim = Skia.Paint();
    dim.setAntiAlias(true);
    dim.setColor(Skia.Color('#0E0D10'));
    dim.setAlphaf(clamp01((a - 0.3) / 7) * 0.8);
    c.drawOval(Skia.XYWHRect(cx - w / 2, y - h / 2, w, h), dim);
  }
}

function Info({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const o = 1 - clamp01(friendDist(k, index.get()) / 0.45);
    return { opacity: o, transform: [{ translateY: 10 * (1 - o) }] };
  });
  const f = FRIENDS[k];
  return (
    <Animated.View style={[styles.info, style]}>
      <View style={styles.infoRow}>
        <Text style={styles.name}>{f.name}</Text>
        <Text style={styles.when}>{f.when}</Text>
      </View>
      <Text style={styles.caption}>{f.caption}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  /** Rolls the column by itself, one badge every STOP seconds. */
  autoPlay?: boolean;
};

export function OrbitBubbleFriends({ brand = 'Pinboard', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const i0 = useImage(require('./assets/pet-dog.jpg'));
  const i1 = useImage(require('./assets/pet-cat.jpg'));
  const i2 = useImage(require('./assets/food-ramen.jpg'));
  const i3 = useImage(require('./assets/plant.jpg'));
  const i4 = useImage(require('./assets/beach.jpg'));
  const i5 = useImage(require('./assets/bike.jpg'));
  const images = useMemo(() => [i0, i1, i2, i3, i4, i5], [i0, i1, i2, i3, i4, i5]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.44;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const index = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const grabbed = useSharedValue(0);
  const from = useSharedValue(0);
  const snapTo = useSharedValue(0);
  const lastStep = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    clock.set(t);
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      index.set(since < SNAP ? from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP) : snapTo.get());
      if (since > SNAP + HOLD) {
        manual.set(false);
        origin.set(now - ((((snapTo.get() % N) + N) % N) * STOP));
      }
      return;
    }
    if (!autoPlay) return;
    index.set(indexAt(t));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawColumn(canvas, index.get(), clock.get(), cx, cy, images);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, images]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabbed.set(index.get());
      lastStep.set(Math.round(index.get()));
    })
    .onUpdate((e) => {
      const i = grabbed.get() - e.translationY / PER;
      index.set(i);
      if (Math.round(i) !== lastStep.get()) {
        lastStep.set(Math.round(i));
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      from.set(index.get());
      snapTo.set(Math.round(index.get()));
      released.set(elapsed.get());
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Friends' photo badges. Drag up or down to roll them.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.meta}>6 new</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.infos} pointerEvents="none">
          {FRIENDS.map((f, k) => (
            <Info key={f.name} k={k} index={index} />
          ))}
        </View>
        <View style={styles.reply}>
          <Text style={styles.replyText}>Send a reaction…</Text>
          <View style={styles.send}>
            <Text style={styles.sendText}>♥</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#141216' },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '800', color: '#F5F2EC', letterSpacing: -0.4 },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(245,242,236,0.55)' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  infos: { height: 76 },
  info: { position: 'absolute', left: 0, right: 0 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  name: { fontSize: 30, fontWeight: '800', color: '#F5F2EC', letterSpacing: -0.8 },
  when: { fontSize: 14, fontWeight: '600', color: 'rgba(245,242,236,0.5)' },
  caption: { marginTop: 4, fontSize: 16, lineHeight: 22, color: 'rgba(245,242,236,0.7)' },
  reply: { marginTop: 12, height: 56, borderRadius: 28, backgroundColor: 'rgba(245,242,236,0.08)', flexDirection: 'row', alignItems: 'center', paddingLeft: 20, paddingRight: 6 },
  replyText: { flex: 1, fontSize: 16, color: 'rgba(245,242,236,0.5)' },
  send: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#F5F2EC', alignItems: 'center', justifyContent: 'center' },
  sendText: { fontSize: 18, color: '#141216' },
});
