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

// The shop page of an invented nut-milk brand, Oatlier. The flavour's name is set huge in
// Instrument Serif and split in two, with the glass bottle standing in the gap between the halves;
// a handful of its nuts fall from above and bounce on the table. To change flavour the letters
// roll away upwards one after another while the next name rolls up from below, the bottle swings
// out to the left as the next one swings in from the right, the nuts fall off the table and the
// next ones drop in, and the colour of the room follows. Swipe sideways to change flavour.
//
// Artwork: assets/bottle-*.png and almond/hazelnut/walnut.png (generated, prompts in
// assets/PROMPTS.md). Layout (393 x 852 pt): 34 pt top row; word halves Instrument Serif 70 pt
// either side of a 140 pt gap at 40 % of the height; bottle 360 pt square centred at 42 %;
// table line at 66 %; product card and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 7.8 s): three stops of 2.6 s. In each (local u):
//   0.00-0.90  six nuts fall in turn (0.1 s apart) from 260 pt above the table, landing with two
//              decaying bounces (|cos| curve, 0.9 s each)
//   0.90-1.90  hold; the bottle sways +-2 deg
//   1.90-2.60  switch (ease-in-out cubic): letters roll out up / in from below, 30 ms apart; the
//              bottle swings by -360 r pt and -16 r deg; the nuts drop 120 pt and fade; the colour
//              cross-fades
// Dragging: 300 pt per flavour with a selection haptic; release snaps over 0.35 s, it stays 2 s,
// then the loop carries on from that flavour.

/** Seconds. */
export const LOOP = 7.8;
const STOP = 2.6;
const SWITCH = 1.9;
const BOTTLE = 360;
const PER = 300;
const HOLD = 2;
const SNAP = 0.35;

type Flavour = { left: string; right: string; name: string; line: string; price: string; tint: string; deep: string };
const FLAVOURS: Flavour[] = [
  { left: 'Alm', right: 'ond', name: 'Almond milk', line: 'Toasted almonds, a pinch of sea salt. 1 L', price: '$4.20', tint: '#F4EBDD', deep: '#6E4A26' },
  { left: 'Haze', right: 'lnut', name: 'Hazelnut milk', line: 'Roasted hazelnuts, no added sugar. 1 L', price: '$4.60', tint: '#EAD9C6', deep: '#5B3519' },
  { left: 'Wal', right: 'nut', name: 'Walnut milk', line: 'Cold-pressed walnuts and a little date. 1 L', price: '$4.90', tint: '#E3E6D6', deep: '#3F4A2A' },
];

/** Six nuts: x offset from centre (pt), size, landing delay and turn. */
const NUTS = Array.from({ length: 6 }, (_, i) => {
  const h = (n: number) => {
    const x = Math.sin((i + 1) * 19.31 + n * 7.77) * 43758.5453;
    return x - Math.floor(x);
  };
  const side = i % 2 === 0 ? -1 : 1;
  return { dx: side * (70 + 90 * h(1)), size: 40 + 22 * h(2), delay: i * 0.1, turn: h(3) * 360, sink: h(4) * 18 };
});

const SERIF = require('./assets/InstrumentSerif-Regular.ttf');
const FAMILY = 'NutMilkFlavours-InstrumentSerif';

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
function offsetOf(j: number, i: number) {
  'worklet';
  let r = j - wrap3(i);
  if (r > 1.5) r -= 3;
  if (r <= -1.5) r += 3;
  return r;
}
/** Height above the table of a nut dropped `x` seconds ago (0.9 s fall with two bounces). */
function dropHeight(x: number) {
  'worklet';
  if (x <= 0) return 260;
  if (x >= 0.9) return 0;
  if (x < 0.45) {
    const u = x / 0.45;
    return 260 * (1 - u * u);
  }
  if (x < 0.72) return 34 * Math.sin((Math.PI * (x - 0.45)) / 0.27);
  return 9 * Math.sin((Math.PI * (x - 0.72)) / 0.18);
}
/** The flavour index (fractional while switching) and the local time in the stop. */
function stopAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  return { index: k + easeInOutCubic(clamp01((u - SWITCH) / (STOP - SWITCH))), local: u };
}

function drawScene(c: SkCanvas, index: number, local: number, t: number, cx: number, bottleY: number, tableY: number, bottles: (SkImage | null)[], nuts: (SkImage | null)[]) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const switching = index - Math.floor(index);
  // Nuts of the current flavour (the outgoing one while switching): fall in, then drop away.
  const nutsOf = wrap3(Math.floor(index));
  const img = nuts[nutsOf];
  if (img) {
    for (let n = 0; n < NUTS.length; n++) {
      const nut = NUTS[n];
      const h = dropHeight(local - nut.delay);
      const away = switching;
      const y = tableY - nut.size / 2 + nut.sink - h + away * 120;
      paint.setAlphaf(1 - away);
      c.save();
      c.translate(cx + nut.dx, y);
      c.rotate(nut.turn + h * 0.6, 0, 0);
      c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-nut.size / 2, -nut.size / 2, nut.size, nut.size), paint);
      c.restore();
    }
  }
  // Bottles: the current one in the gap, neighbours swinging in and out.
  const sway = 2 * Math.sin((t / LOOP) * Math.PI * 6);
  for (let j = 0; j < 3; j++) {
    const r = offsetOf(j, index);
    const fade = 1 - Math.abs(r);
    if (fade <= 0) continue;
    const b = bottles[j];
    if (!b) continue;
    paint.setAlphaf(fade);
    c.save();
    c.translate(cx + r * 360, bottleY + BOTTLE / 2);
    c.rotate(-16 * r + sway * (1 - Math.abs(r)), 0, 0);
    c.translate(0, -BOTTLE / 2);
    c.drawImageRect(b, Skia.XYWHRect(0, 0, b.width(), b.height()), Skia.XYWHRect(-BOTTLE / 2, -BOTTLE / 2, BOTTLE, BOTTLE), paint);
    c.restore();
  }
}

/** One half of the word: its letters roll up and out, staggered. */
function Half({ k, side, index }: { k: number; side: 'left' | 'right'; index: SharedValue<number> }) {
  const text = FLAVOURS[k][side];
  return (
    <View style={[styles.half, side === 'left' ? styles.halfLeft : styles.halfRight]}>
      {text.split('').map((ch, n) => (
        <Letter key={n} ch={ch} k={k} n={side === 'left' ? n : n + 4} index={index} />
      ))}
    </View>
  );
}
function Letter({ ch, k, n, index }: { ch: string; k: number; n: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const r = offsetOf(k, index.get());
    // r < 0: leaving (rolls up); r > 0: arriving (from below). Stagger by letter.
    const lag = clamp01(Math.abs(r) * 1.25 - n * 0.04);
    const y = r <= 0 ? -lag * 110 : lag * 110;
    return { opacity: 1 - lag, transform: [{ translateY: y }] };
  });
  return <Animated.Text style={[styles.letter, { color: FLAVOURS[k].deep }, style]}>{ch}</Animated.Text>;
}

function Info({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: 1 - smooth(Math.abs(offsetOf(k, index.get())) / 0.45) }));
  const f = FLAVOURS[k];
  return (
    <Animated.View style={[styles.info, style]}>
      <View style={styles.infoRow}>
        <Text style={styles.name}>{f.name}</Text>
        <Text style={[styles.price, { color: f.deep }]}>{f.price}</Text>
      </View>
      <Text style={styles.line}>{f.line}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  action?: string;
  /** Changes flavour by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function NutMilkFlavours({ brand = 'Oatlier', action = 'Add to basket', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FAMILY]: SERIF });
  const b0 = useImage(require('./assets/bottle-almond.png'));
  const b1 = useImage(require('./assets/bottle-hazelnut.png'));
  const b2 = useImage(require('./assets/bottle-walnut.png'));
  const n0 = useImage(require('./assets/almond.png'));
  const n1 = useImage(require('./assets/hazelnut.png'));
  const n2 = useImage(require('./assets/walnut.png'));
  const bottles = useMemo(() => [b0, b1, b2], [b0, b1, b2]);
  const nuts = useMemo(() => [n0, n1, n2], [n0, n1, n2]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const bottleY = height * 0.42;
  const tableY = height * 0.66;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const index = useSharedValue(0);
  const local = useSharedValue(1);
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
        local.set(Math.min(1.2, since - SNAP));
        if (since > SNAP + HOLD) {
          manual.set(false);
          origin.set(now - wrap3(snapTo.get()) * STOP - 1.2);
        }
      }
      return;
    }
    if (!autoPlay) {
      index.set(0);
      local.set(1.2);
      return;
    }
    const s = stopAt(clock.get());
    index.set(s.index);
    local.set(s.local);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, index.get(), local.get(), clock.get(), cx, bottleY, tableY, bottles, nuts);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, bottleY, tableY, bottles, nuts]);

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
      const i = grabbed.get() - e.translationX / PER;
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

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: FLAVOURS[0].tint }]} />;

  return (
    <Animated.View style={[styles.screen, tintStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.menu}>Shop · Story · Basket (1)</Text>
          </View>
          <View style={[styles.wordRow, { top: height * 0.4 - 55 }]} pointerEvents="none">
            {FLAVOURS.map((f, k) => (
              <View key={f.name} style={StyleSheet.absoluteFill}>
                <Half k={k} side="left" index={index} />
                <Half k={k} side="right" index={index} />
              </View>
            ))}
          </View>
          <View style={[styles.table, { top: tableY }]} pointerEvents="none" />
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Nut milk bottle. Swipe to change flavour.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.infos}>
          {FLAVOURS.map((f, k) => (
            <Info key={f.name} k={k} index={index} />
          ))}
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
  brand: { fontFamily: FAMILY, fontSize: 28, color: '#2A1D12' },
  menu: { fontSize: 13, fontWeight: '600', color: 'rgba(42,29,18,0.6)' },
  wordRow: { position: 'absolute', left: 0, right: 0, height: 110 },
  half: { position: 'absolute', top: 0, flexDirection: 'row', overflow: 'hidden', height: 110, alignItems: 'center' },
  halfLeft: { right: '50%', marginRight: 70 },
  halfRight: { left: '50%', marginLeft: 70 },
  letter: { fontFamily: FAMILY, fontSize: 70, lineHeight: 110, letterSpacing: -2 },
  table: { position: 'absolute', left: 24, right: 24, height: 1, backgroundColor: 'rgba(42,29,18,0.18)' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  infos: { height: 70 },
  info: { position: 'absolute', left: 0, right: 0 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  name: { fontFamily: FAMILY, fontSize: 34, color: '#2A1D12' },
  price: { fontSize: 18, fontWeight: '700' },
  line: { marginTop: 2, fontSize: 15, lineHeight: 20, color: 'rgba(42,29,18,0.62)' },
  button: { marginTop: 14, height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
