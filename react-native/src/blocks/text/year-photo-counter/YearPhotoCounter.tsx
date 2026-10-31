import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Image, StyleSheet, Text, View, useWindowDimensions, type ImageSourcePropType } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The exhibition guide of an invented museum show, "Carry On — a century of things in our
// pockets". A huge year sits at the top like a counter; to move on, its four digits roll like a
// slot machine, one after another, to the next year, while the last object's print is flicked
// away to the left and the next one drops onto the page, turning as it lands; the caption types
// itself over, the page takes the era's colour and the timeline's marker slides along.
// 1905 box camera, 1957 scooter, 1984 tape player, 2021 earbuds. Swipe to move along the years.
//
// Photos: assets/year-*.jpg (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): 34 pt top row; year Archivo Expanded Black 92 pt in four 80 pt columns
// (110 pt rows) at 13 % of the height; print 252 x 312 pt (10 pt border) centred at 50 %; caption
// in JetBrains Mono under it; timeline and labels at the bottom.
//
// autoPlay timeline (LOOP = 8.8 s): four stops of 2.2 s; each change runs 1.4-2.2 s of its stop:
// digit column i rolls forward from the old digit to the new one (0.5 s, starting 0.08 i s later,
// ease-in-out cubic); the old print flies 420 pt left turning -24 deg (ease-in cubic, 0.45 s), the
// new one drops 160 pt from 1.15x (ease-out back, 1.65-2.2 s); colour and caption cross-fade.
// Swiping: 240 pt per stop with a selection haptic; release snaps over 0.4 s, holds 2 s, then the
// loop carries on.

/** Seconds. */
export const LOOP = 8.8;
const STOP = 2.2;
const CHANGE = 1.4;
const COL = 80;
const ROW = 110;
const PER = 240;
const HOLD = 2;
const SNAP = 0.4;

type Era = { year: number; title: string; text: string; bg: string; tilt: number; photo: ImageSourcePropType };
const ERAS: Era[] = [
  { year: 1905, title: 'The box camera', text: 'Folded wood and leather. One roll, eight pictures, a week to develop.', bg: '#ECE5D8', tilt: -5, photo: require('./assets/year-1905.jpg') },
  { year: 1957, title: 'The city scooter', text: 'Mint paint, chrome trim and a spare wheel strapped to the back.', bg: '#E1ECE3', tilt: 4, photo: require('./assets/year-1957.jpg') },
  { year: 1984, title: 'The pocket tape player', text: 'Ninety minutes of music, and a pencil to wind the tape back.', bg: '#EFE0E4', tilt: -3, photo: require('./assets/year-1984.jpg') },
  { year: 2021, title: 'The wireless earbuds', text: 'Two grams each. Lost behind the sofa within a month.', bg: '#DFE4EE', tilt: 6, photo: require('./assets/year-2021.jpg') },
];
const N = ERAS.length;

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const MONO = require('./assets/JetBrainsMono-Medium.ttf');
const BLACK = 'YearPhotoCounter-ArchivoExpandedBlack';
const MONO_FAMILY = 'YearPhotoCounter-Mono';
const INK = '#1E1A16';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.4;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function wrapN(i: number) {
  'worklet';
  return ((i % N) + N) % N;
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
/** Fractional stop index (0..1 per change, linear in the change's 0.8 s) `t` s into the loop. */
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  return k + clamp01((u - CHANGE) / (STOP - CHANGE));
}
function digitOf(year: number, i: number) {
  'worklet';
  return Math.floor(year / Math.pow(10, 3 - i)) % 10;
}

/** Digit column i: rolls forward from the old year's digit to the new one's. */
function DigitColumn({ i, index }: { i: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const x = index.get();
    const base = Math.floor(x);
    const f = x - base;
    const a = digitOf(ERAS[wrapN(base)].year, i);
    const b = digitOf(ERAS[wrapN(base + 1)].year, i);
    const steps = b >= a ? b - a : b + 10 - a;
    // The change is 0.8 s long; column i rolls during [0.08 i, 0.08 i + 0.5] s of it.
    const local = clamp01((f * 0.8 - 0.08 * i) / 0.5);
    const pos = a + steps * easeInOutCubic(local);
    return { transform: [{ translateY: -ROW * pos }] };
  });
  return (
    <View style={styles.column}>
      <Animated.View style={style}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((d, k) => (
          <Text key={k} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** Print k: lands when it becomes current, flies off when the next one comes. */
function Print({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const x = index.get();
    const base = Math.floor(x);
    const f = x - base;
    const tilt = ERAS[k].tilt;
    if (wrapN(base) === k) {
      const out = Math.pow(clamp01((f * 0.8) / 0.45), 3);
      return { opacity: 1 - clamp01((out - 0.7) / 0.3), zIndex: 1, transform: [{ translateX: -420 * out }, { rotate: `${tilt - 24 * out}deg` }, { scale: 1 }] };
    }
    if (wrapN(base + 1) === k) {
      const inn = clamp01((f * 0.8 - 0.25) / 0.55);
      const e = easeOutBack(inn);
      return { opacity: clamp01(inn * 4), zIndex: 2, transform: [{ translateY: -160 * (1 - e) }, { rotate: `${tilt + 14 * (1 - e)}deg` }, { scale: 1.15 - 0.15 * e }] };
    }
    return { opacity: 0, zIndex: 0, transform: [{ translateX: 0 }, { rotate: '0deg' }, { scale: 1 }] };
  });
  return (
    <Animated.View style={[styles.print, style]}>
      <Image source={ERAS[k].photo} style={styles.photo} resizeMode="cover" />
      <Text style={styles.printCaption}>{`No. ${String(k + 1).padStart(2, '0')} · ${ERAS[k].year}`}</Text>
    </Animated.View>
  );
}

function Caption({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    let d = Math.abs(index.get() - k) % N;
    d = Math.min(d, N - d);
    const o = 1 - clamp01(d / 0.5);
    return { opacity: o, transform: [{ translateY: 8 * (1 - o) }] };
  });
  return (
    <Animated.View style={[styles.caption, style]}>
      <Text style={styles.capTitle}>{ERAS[k].title}</Text>
      <Text style={styles.capText}>{ERAS[k].text}</Text>
    </Animated.View>
  );
}

type Props = {
  /** Walks through the years by itself, every STOP seconds. */
  autoPlay?: boolean;
};

export function YearPhotoCounter({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO, [MONO_FAMILY]: MONO });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
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
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      index.set(since < SNAP ? from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP) : snapTo.get());
      if (since > SNAP + HOLD) {
        manual.set(false);
        origin.set(now - wrapN(snapTo.get()) * STOP);
      }
      return;
    }
    if (!autoPlay) return;
    index.set(indexAt((now - origin.get()) % LOOP));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const bgStyle = useAnimatedStyle(() => {
    const x = index.get();
    const base = Math.floor(x);
    return { backgroundColor: mixColor(ERAS[wrapN(base)].bg, ERAS[wrapN(base + 1)].bg, easeInOutCubic(x - base)) };
  });
  const markerStyle = useAnimatedStyle(() => {
    const x = index.get();
    const base = Math.floor(x);
    const f = easeInOutCubic(x - base);
    const a = wrapN(base);
    const b = wrapN(base + 1);
    const pos = a + (b - a) * f;
    return { left: `${(pos / (N - 1)) * 100}%` };
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

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: ERAS[0].bg }]} />;

  return (
    <Animated.View style={[styles.screen, bgStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>Carry On</Text>
            <Text style={styles.meta}>Room 3 · 1905 – 2021</Text>
          </View>
          <View style={[styles.year, { top: height * 0.13 }]} pointerEvents="none">
            {[0, 1, 2, 3].map((i) => (
              <DigitColumn key={i} i={i} index={index} />
            ))}
          </View>
          <View style={[styles.stage, { top: height * 0.5 - 165, left: (width - 252) / 2 }]} pointerEvents="none">
            {ERAS.map((e, k) => (
              <Print key={e.year} k={k} index={index} />
            ))}
          </View>
          <View style={[styles.captions, { top: height * 0.5 + 168 }]} pointerEvents="none">
            {ERAS.map((e, k) => (
              <Caption key={e.year} k={k} index={index} />
            ))}
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
        <View style={styles.track}>
          <View style={styles.rule} />
          {ERAS.map((e, k) => (
            <View key={e.year} style={[styles.tick, { left: `${(k / (N - 1)) * 100}%` }]} />
          ))}
          <Animated.View style={[styles.marker, markerStyle]} />
        </View>
        <View style={styles.labels}>
          {ERAS.map((e) => (
            <Text key={e.year} style={styles.label}>
              {e.year}
            </Text>
          ))}
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 16, color: INK },
  meta: { fontFamily: MONO_FAMILY, fontSize: 12, color: 'rgba(30,26,22,0.6)' },
  year: { position: 'absolute', left: 0, right: 0, height: ROW, flexDirection: 'row', justifyContent: 'center' },
  column: { width: COL, height: ROW, overflow: 'hidden' },
  digit: { width: COL, height: ROW, lineHeight: ROW, textAlign: 'center', fontFamily: BLACK, fontSize: 92, color: INK },
  stage: { position: 'absolute', width: 252, height: 312 },
  print: { position: 'absolute', left: 0, top: 0, width: 252, height: 312, padding: 10, paddingBottom: 30, backgroundColor: '#FBF8F1', shadowColor: '#000', shadowOpacity: 0.22, shadowRadius: 14, shadowOffset: { width: 0, height: 8 }, elevation: 8 },
  photo: { flex: 1, width: '100%' },
  printCaption: { position: 'absolute', left: 12, bottom: 9, fontFamily: MONO_FAMILY, fontSize: 10, color: 'rgba(30,26,22,0.6)' },
  captions: { position: 'absolute', left: 32, right: 32, height: 90 },
  caption: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  capTitle: { fontFamily: BLACK, fontSize: 18, color: INK },
  capText: { marginTop: 6, fontFamily: MONO_FAMILY, fontSize: 13, lineHeight: 19, color: 'rgba(30,26,22,0.72)', textAlign: 'center' },
  footer: { position: 'absolute', left: 32, right: 32, bottom: 0 },
  track: { height: 18, justifyContent: 'center' },
  rule: { height: 2, backgroundColor: 'rgba(30,26,22,0.25)' },
  tick: { position: 'absolute', width: 2, height: 10, marginLeft: -1, backgroundColor: 'rgba(30,26,22,0.45)' },
  marker: { position: 'absolute', width: 14, height: 14, marginLeft: -7, borderRadius: 7, backgroundColor: INK },
  labels: { marginTop: 8, flexDirection: 'row', justifyContent: 'space-between', marginHorizontal: -14 },
  label: { fontFamily: MONO_FAMILY, fontSize: 12, color: 'rgba(30,26,22,0.7)' },
});
