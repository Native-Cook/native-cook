import { BlurStyle, Canvas, Picture, Skia, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The menu of an invented doughnut bakery, Glaze & Co. One doughnut sits large in the middle with
// the next ones peeking in at the edges; changing flavour rolls them along like wheels — each turns
// by the distance it travels — growing as they reach the middle and shrinking as they leave, with a
// little squash as the new one lands. The flavour's name in italic serif slides behind it, the
// page takes its colour, and the name, notes and price below cross-fade. Swipe to roll them.
//
// Artwork: assets/donut-*.png (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): 34 pt top row with "Doughnuts" in Instrument Serif Italic 30 pt; the
// middle doughnut 270 pt at 41 % of the height, neighbours 0.5x at +-250 pt; a 150 pt italic word
// behind it at 16 % opacity; name 40 pt, notes, price and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 7.2 s): three stops of 2.4 s; each switch runs 1.6-2.4 s of its stop
// (ease-in-out cubic) and the arriving doughnut squashes on landing (1 + 0.06 e^-7x sin 16x).
// Dragging: 215 pt per doughnut with a selection haptic; release snaps over 0.4 s, it stays 2 s,
// then the loop carries on from that doughnut.

/** Seconds. */
export const LOOP = 7.2;
const STOP = 2.4;
const SWITCH = 1.6;
const SIZE = 290;
const GAP = 215;
const HOLD = 2;
const SNAP = 0.4;

type Flavour = { word: string; name: string; notes: string; price: string; tint: string; deep: string };
const FLAVOURS: Flavour[] = [
  { word: 'Berry', name: 'Strawberry glaze', notes: 'Brioche dough, fresh strawberry icing, white sprinkles.', price: '$3.20', tint: '#FBE1E8', deep: '#B0244F' },
  { word: 'Cocoa', name: 'Dark chocolate', notes: 'Seventy per cent cocoa glaze with gold sugar pearls.', price: '$3.40', tint: '#F0E2D6', deep: '#5A2E16' },
  { word: 'Matcha', name: 'Matcha drizzle', notes: 'Ceremonial matcha icing and a white chocolate drizzle.', price: '$3.60', tint: '#E2EFD8', deep: '#3D6B2A' },
];

const ITALIC = require('./assets/InstrumentSerif-Italic.ttf');
const REGULAR = require('./assets/InstrumentSerif-Regular.ttf');
const SERIF_ITALIC = 'DonutFlavourPicker-SerifItalic';
const SERIF = 'DonutFlavourPicker-Serif';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function wrap3(i: number) {
  'worklet';
  return ((i % 3) + 3) % 3;
}
function offsetOf(j: number, i: number) {
  'worklet';
  let r = j - (((i % 3) + 3) % 3);
  if (r > 1.5) r -= 3;
  if (r <= -1.5) r += 3;
  return r;
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
/** Squash of a doughnut that landed `x` seconds ago. */
function landing(x: number) {
  'worklet';
  if (x <= 0 || x > 0.8) return 0;
  return 0.06 * Math.exp(-7 * x) * Math.sin(16 * x);
}

/** Fractional doughnut index and the seconds since the last landing, `t` s into the loop. */
function carouselAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  const index = k + easeInOutCubic(clamp01((u - SWITCH) / (STOP - SWITCH)));
  // Doughnut k landed at the end of the previous stop (k = 0 landed at the end of the loop).
  return { index, landed: u };
}

function drawDonuts(c: SkCanvas, index: number, landed: number, cx: number, cy: number, images: (SkImage | null)[]) {
  'worklet';
  const order = [0, 1, 2].sort((a, b) => Math.abs(offsetOf(b, index)) - Math.abs(offsetOf(a, index)));
  for (let n = 0; n < 3; n++) {
    const j = order[n];
    const img = images[j];
    if (!img) continue;
    const r = offsetOf(j, index);
    const x = cx + r * GAP;
    const scale = 1 - 0.5 * Math.min(1, Math.abs(r));
    const s = SIZE * scale;
    const squash = Math.abs(r) < 0.02 ? landing(landed) : 0;
    // Rolling: the turn matches the distance travelled along the floor.
    const turn = ((r * GAP) / (s / 2)) * (180 / Math.PI);
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    shadow.setColor(Skia.Color('rgba(60,30,20,0.22)'));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12 * scale, true));
    c.drawOval(Skia.XYWHRect(x - s * 0.36, cy + s * 0.4, s * 0.72, s * 0.11), shadow);
    c.save();
    c.translate(x, cy + (s / 2) * squash);
    c.scale(1 + squash, 1 - squash);
    c.rotate(turn, 0, 0);
    c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-s / 2, -s / 2, s, s), Skia.Paint());
    c.restore();
  }
}

function Word({ k, index, cy }: { k: number; index: SharedValue<number>; cy: number }) {
  const style = useAnimatedStyle(() => {
    const r = offsetOf(k, index.get());
    return { opacity: 0.16 * (1 - clamp01(Math.abs(r) * 1.4)), transform: [{ translateX: r * 140 }] };
  });
  return (
    <Animated.Text style={[styles.word, { top: cy - 100, color: FLAVOURS[k].deep }, style]} numberOfLines={1}>
      {FLAVOURS[k].word}
    </Animated.Text>
  );
}

function Info({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = Math.abs(offsetOf(k, index.get()));
    const o = 1 - smooth(d / 0.5);
    return { opacity: o, transform: [{ translateY: 10 * (1 - o) }] };
  });
  const f = FLAVOURS[k];
  return (
    <Animated.View style={[styles.info, style]}>
      <View style={styles.infoRow}>
        <Text style={styles.name}>{f.name}</Text>
        <Text style={[styles.price, { color: f.deep }]}>{f.price}</Text>
      </View>
      <Text style={styles.notes}>{f.notes}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  action?: string;
  /** Rolls to the next doughnut by itself, every STOP seconds. */
  autoPlay?: boolean;
};

export function DonutFlavourPicker({ brand = 'Glaze & Co.', action = 'Add to box', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [SERIF_ITALIC]: ITALIC, [SERIF]: REGULAR });
  const d0 = useImage(require('./assets/donut-strawberry.png'));
  const d1 = useImage(require('./assets/donut-chocolate.png'));
  const d2 = useImage(require('./assets/donut-matcha.png'));
  const images = useMemo(() => [d0, d1, d2], [d0, d1, d2]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.43;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const index = useSharedValue(0);
  const landed = useSharedValue(1);
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
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      if (since < SNAP) {
        index.set(from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP));
        landed.set(1);
      } else {
        index.set(snapTo.get());
        landed.set(since - SNAP);
        if (since > SNAP + HOLD) {
          manual.set(false);
          origin.set(now - wrap3(snapTo.get()) * STOP - 1.0);
        }
      }
      return;
    }
    if (!autoPlay) return;
    const s = carouselAt((now - origin.get()) % LOOP);
    index.set(s.index);
    landed.set(s.landed);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawDonuts(canvas, index.get(), landed.get(), cx, cy, images);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, images]);

  const tintStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    return { backgroundColor: mixColor(FLAVOURS[wrap3(base)].tint, FLAVOURS[wrap3(base + 1)].tint, i - base) };
  });
  const buttonStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    return { backgroundColor: mixColor(FLAVOURS[wrap3(base)].deep, FLAVOURS[wrap3(base + 1)].deep, i - base) };
  });

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
      const i = grabbed.get() - e.translationX / GAP;
      index.set(i);
      landed.set(1);
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

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: FLAVOURS[0].tint }]} />;

  return (
    <Animated.View style={[styles.screen, tintStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            {FLAVOURS.map((f, k) => (
              <Word key={f.word} k={k} index={index} cy={cy} />
            ))}
          </View>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.title}>Doughnuts</Text>
            <Text style={styles.brand}>{brand}</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Doughnut flavours. Swipe to roll to the next one.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.infos} pointerEvents="none">
          {FLAVOURS.map((f, k) => (
            <Info key={f.name} k={k} index={index} />
          ))}
        </View>
        <View style={styles.actions}>
          <View style={styles.stepper}>
            <Text style={styles.stepText}>−</Text>
            <Text style={styles.count}>2</Text>
            <Text style={styles.stepText}>+</Text>
          </View>
          <Animated.View style={[styles.button, buttonStyle]} accessible accessibilityRole="button">
            <Text style={styles.action}>{action}</Text>
          </Animated.View>
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontFamily: SERIF_ITALIC, fontSize: 30, color: '#2A1A14' },
  brand: { fontSize: 14, fontWeight: '700', color: 'rgba(42,26,20,0.6)' },
  word: { position: 'absolute', left: -60, right: -60, textAlign: 'center', fontFamily: SERIF_ITALIC, fontSize: 168, lineHeight: 200 },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  infos: { height: 96 },
  info: { position: 'absolute', left: 0, right: 0 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  name: { fontFamily: SERIF, fontSize: 38, color: '#2A1A14', letterSpacing: -0.5 },
  price: { fontSize: 20, fontWeight: '800' },
  notes: { marginTop: 4, fontSize: 15, lineHeight: 21, color: 'rgba(42,26,20,0.65)', maxWidth: 300 },
  actions: { marginTop: 12, flexDirection: 'row', gap: 12 },
  stepper: { width: 128, height: 58, borderRadius: 29, backgroundColor: 'rgba(255,255,255,0.7)', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around' },
  stepText: { fontSize: 22, fontWeight: '600', color: '#2A1A14' },
  count: { fontSize: 18, fontWeight: '800', color: '#2A1A14' },
  button: { flex: 1, height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
