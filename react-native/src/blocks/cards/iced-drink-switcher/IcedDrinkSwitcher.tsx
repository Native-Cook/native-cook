import { Canvas, Picture, Skia, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The drink page of an invented café app, chill.: a tall iced drink floats in the middle of a
// colour-washed screen with its name set huge behind it, surrounded by its ingredients drifting at
// different depths — tea leaves around the matcha, roasted beans around the coffee, chocolate
// chunks around the cocoa. Every couple of seconds the glass swings away to the left, the
// ingredients scatter, the next drink swings in from the right with its own, and the colour, the
// big word and the order card follow. Swipe sideways to switch drinks yourself.
//
// Artwork: assets/drink-*.png and leaf/bean/cocoa.png (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): 34 pt top row; big word Archivo Expanded Black 64 pt at 27 % of the
// height; glass 340 pt square centred at 40 %; eight ingredients per drink; a white order card
// from 64 % of the height (name, line, S/M/L pills, price and a 56 pt button).
//
// autoPlay timeline (LOOP = 7.5 s): three stops of 2.5 s. In each: 0-1.8 s hold (the glass bobs
// 6 pt three times per loop, the ingredients drift 8 pt and turn +-10 deg twice per loop);
// 1.8-2.5 s switch to the next drink (ease-in-out cubic): for a drink at offset r from the current
// one the glass moves r x 360 pt, turns -14 r deg, scales 1 - 0.25 |r| and fades 1 - |r|; its
// ingredients spread by 1 + 1.2 |r|, shift r x 200 pt and fade 1 - 1.5 |r|.
// Dragging: 300 pt per drink with a selection haptic; release snaps over 0.35 s, it stays 2 s,
// then the loop carries on from that drink.

/** Seconds. */
export const LOOP = 7.5;
const STOP = 2.5;
const SWITCH = 1.8;
const GLASS = 340;
const PER_DRINK = 300;
const HOLD = 2;
const SNAP = 0.35;

type Drink = { word: string; name: string; line: string; price: string; tint: string; deep: string };
const DRINKS: Drink[] = [
  { word: 'MATCHA', name: 'Iced Matcha Latte', line: 'Stone-ground matcha, oat milk, a little honey.', price: '$5.40', tint: '#DDEFD1', deep: '#2F6B2A' },
  { word: 'COFFEE', name: 'Iced Cold Brew', line: 'Steeped for 18 hours, finished with whole milk.', price: '$4.80', tint: '#F1E3D2', deep: '#6A3B1D' },
  { word: 'COCOA', name: 'Iced Cocoa Cream', line: 'Dark cocoa, cold milk and a cloud of cream.', price: '$5.10', tint: '#ECD5C8', deep: '#5B271A' },
];

/** Each drink's ingredients: offset from the glass centre (pt), size (pt), depth (front > 0.5), turn. */
const BITS = Array.from({ length: 8 }, (_, i) => {
  const h = (n: number) => {
    const x = Math.sin((i + 1) * 31.17 + n * 11.73) * 43758.5453;
    return x - Math.floor(x);
  };
  const a = (i / 8) * Math.PI * 2 + (h(1) - 0.5) * 0.6;
  const r = 120 + 60 * h(2);
  return { dx: Math.cos(a) * r, dy: Math.sin(a) * r * 1.15 - 10, size: 44 + 46 * h(3), depth: h(4), turn: h(5) * 360, phase: h(6) * Math.PI * 2 };
});

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const ARCHIVO_SEMI = require('./assets/Archivo-SemiBold.ttf');
const BLACK = 'IcedDrinkSwitcher-ArchivoExpandedBlack';
const SEMI = 'IcedDrinkSwitcher-ArchivoSemiBold';

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
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}
function wrap3(i: number) {
  'worklet';
  return ((i % 3) + 3) % 3;
}
/** Offset of drink j from the fractional index i, in (-1.5, 1.5]. */
function offsetOf(j: number, i: number) {
  'worklet';
  let r = j - wrap3(i);
  if (r > 1.5) r -= 3;
  if (r <= -1.5) r += 3;
  return r;
}
/** The drink index, fractional while switching, `t` seconds into the loop. */
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  return k + easeInOutCubic(clamp01((t - k * STOP - SWITCH) / (STOP - SWITCH)));
}

function drawBits(c: SkCanvas, front: boolean, i: number, t: number, cx: number, cy: number, sprites: (SkImage | null)[]) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  for (let j = 0; j < 3; j++) {
    const r = offsetOf(j, i);
    const fade = 1 - 1.5 * Math.abs(r);
    if (fade <= 0) continue;
    const img = sprites[j];
    if (!img) continue;
    for (let b = 0; b < BITS.length; b++) {
      const bit = BITS[b];
      if ((bit.depth > 0.5) !== front) continue;
      const spread = 1 + 1.2 * Math.abs(r);
      const drift = Math.sin((t / LOOP) * Math.PI * 4 + bit.phase);
      const x = cx + bit.dx * spread + r * 200;
      const y = cy + bit.dy * spread + 8 * drift;
      const size = bit.size * (front ? 1.15 : 0.8);
      paint.setAlphaf(fade * (front ? 1 : 0.85));
      c.save();
      c.translate(x, y);
      c.rotate(bit.turn + 10 * drift, 0, 0);
      c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-size / 2, -size / 2, size, size), paint);
      c.restore();
    }
  }
}

function drawGlasses(c: SkCanvas, i: number, t: number, cx: number, cy: number, glasses: (SkImage | null)[]) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const bob = 6 * Math.sin((t / LOOP) * Math.PI * 6);
  for (let j = 0; j < 3; j++) {
    const r = offsetOf(j, i);
    const fade = 1 - Math.abs(r);
    if (fade <= 0) continue;
    const img = glasses[j];
    if (!img) continue;
    const s = 1 - 0.25 * Math.abs(r);
    paint.setAlphaf(fade);
    c.save();
    c.translate(cx + r * 360, cy + bob);
    c.rotate(-14 * r, 0, 0);
    c.scale(s, s);
    c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-GLASS / 2, -GLASS / 2, GLASS, GLASS), paint);
    c.restore();
  }
}

function Word({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const r = offsetOf(k, index.get());
    return { opacity: 1 - smooth(Math.abs(r) / 0.6), transform: [{ translateX: r * 120 }] };
  });
  return <Animated.Text style={[styles.word, { color: DRINKS[k].deep }, style]}>{DRINKS[k].word}</Animated.Text>;
}

function Info({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const r = offsetOf(k, index.get());
    return { opacity: 1 - smooth(Math.abs(r) / 0.45), transform: [{ translateY: Math.abs(r) * 14 }] };
  });
  const d = DRINKS[k];
  return (
    <Animated.View style={[styles.info, style]}>
      <View style={styles.infoRow}>
        <Text style={styles.name}>{d.name}</Text>
        <Text style={[styles.price, { color: d.deep }]}>{d.price}</Text>
      </View>
      <Text style={styles.line}>{d.line}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  action?: string;
  /** Switches drinks by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function IcedDrinkSwitcher({ brand = 'chill.', action = 'Add to order', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO, [SEMI]: ARCHIVO_SEMI });
  const matcha = useImage(require('./assets/drink-matcha.png'));
  const coffee = useImage(require('./assets/drink-coffee.png'));
  const cocoa = useImage(require('./assets/drink-cocoa.png'));
  const leaf = useImage(require('./assets/leaf.png'));
  const bean = useImage(require('./assets/bean.png'));
  const chunk = useImage(require('./assets/cocoa.png'));
  const glasses = useMemo(() => [matcha, coffee, cocoa], [matcha, coffee, cocoa]);
  const sprites = useMemo(() => [leaf, bean, chunk], [leaf, bean, chunk]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.4;

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
    clock.set((now - origin.get()) % LOOP);
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      if (since < SNAP) index.set(from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP));
      else {
        index.set(snapTo.get());
        if (since > SNAP + HOLD) {
          manual.set(false);
          origin.set(now - wrap3(snapTo.get()) * STOP);
        }
      }
      return;
    }
    index.set(autoPlay ? indexAt(clock.get()) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const back = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawBits(canvas, false, index.get(), clock.get(), cx, cy, sprites);
    drawGlasses(canvas, index.get(), clock.get(), cx, cy, glasses);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, sprites, glasses]);
  const front = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawBits(canvas, true, index.get(), clock.get(), cx, cy, sprites);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, sprites]);

  const tintStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    return { backgroundColor: mixColor(DRINKS[wrap3(base)].tint, DRINKS[wrap3(base + 1)].tint, i - base) };
  });
  const buttonStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    return { backgroundColor: mixColor(DRINKS[wrap3(base)].deep, DRINKS[wrap3(base + 1)].deep, i - base) };
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
      const i = grabbed.get() - e.translationX / PER_DRINK;
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

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: DRINKS[0].tint }]} />;

  const cardTop = height * 0.64;

  return (
    <Animated.View style={[styles.screen, tintStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <View style={styles.bag}>
              <Text style={styles.bagText}>2</Text>
            </View>
          </View>
          <View style={[styles.words, { top: height * 0.27 - 40 }]} pointerEvents="none">
            {DRINKS.map((d, k) => (
              <Word key={d.word} k={k} index={index} />
            ))}
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Drinks. Swipe to change.">
            <Picture picture={back} />
            <Picture picture={front} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.card, { top: cardTop, paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.infos}>
          {DRINKS.map((d, k) => (
            <Info key={d.name} k={k} index={index} />
          ))}
        </View>
        <View style={styles.sizes}>
          {['S', 'M', 'L'].map((s) => (
            <View key={s} style={[styles.size, s === 'M' && styles.sizeOn]}>
              <Text style={[styles.sizeText, s === 'M' && styles.sizeTextOn]}>{s}</Text>
            </View>
          ))}
          <Text style={styles.oz}>16 oz · less ice</Text>
        </View>
        <Animated.View style={[styles.button, buttonStyle]} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 22, color: '#1C1410', letterSpacing: -0.5 },
  bag: { width: 34, height: 34, borderRadius: 17, backgroundColor: '#1C1410', alignItems: 'center', justifyContent: 'center' },
  bagText: { fontFamily: BLACK, fontSize: 13, color: '#FFFFFF' },
  words: { position: 'absolute', left: 0, right: 0, height: 80, alignItems: 'center' },
  word: { position: 'absolute', fontFamily: BLACK, fontSize: 64, lineHeight: 80, letterSpacing: -2 },
  card: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 24, paddingTop: 24, borderTopLeftRadius: 32, borderTopRightRadius: 32, backgroundColor: '#FFFFFF' },
  infos: { height: 76 },
  info: { position: 'absolute', left: 0, right: 0 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  name: { fontFamily: SEMI, fontSize: 24, color: '#1C1410', letterSpacing: -0.4 },
  price: { fontFamily: BLACK, fontSize: 20 },
  line: { marginTop: 6, fontSize: 15, lineHeight: 20, color: '#7A6F68' },
  sizes: { marginTop: 8, flexDirection: 'row', alignItems: 'center', gap: 8 },
  size: { width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: '#E4DDD7', alignItems: 'center', justifyContent: 'center' },
  sizeOn: { backgroundColor: '#1C1410', borderColor: '#1C1410' },
  sizeText: { fontFamily: SEMI, fontSize: 15, color: '#1C1410' },
  sizeTextOn: { color: '#FFFFFF' },
  oz: { marginLeft: 6, fontSize: 14, color: '#7A6F68' },
  button: { marginTop: 18, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: SEMI, fontSize: 17, color: '#FFFFFF' },
});
