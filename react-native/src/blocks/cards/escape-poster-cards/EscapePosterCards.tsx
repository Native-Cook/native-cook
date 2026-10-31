import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Image, StyleSheet, Text, View, useWindowDimensions, type ImageSourcePropType } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The deals page of an invented travel agency, Wander Post. Each trip is a full-bleed photograph
// with a cream poster card standing on it: the same view framed small at the top, the trip's name
// set big in green, and its date, departure, nights and board. To change trip the card turns
// round its vertical axis — it is the next trip when it comes back — while the next photograph
// wipes across the screen from the right behind it. Swipe sideways to turn the cards yourself.
//
// Photos: assets/photo-*.jpg (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): photo full bleed; 34 pt top row in white; card 264 x 360 pt (radius
// 10) centred at 47 % of the height: 244 x 156 pt window, title Archivo Expanded Black 34 pt,
// four detail lines; page dots and a 58 pt button at the bottom over a dark gradient.
//
// autoPlay timeline (LOOP = 8.8 s): four trips of 2.2 s; each switch runs 1.4-2.2 s of its stop
// (ease-in-out cubic): the card turns 0 -> 90 deg (shrinking to 0.94) and comes back from -90 ->
// 0 deg showing the next trip; the next photo wipes in from the right edge over the same 0.8 s
// while the current one drifts 60 pt left.
// Dragging: 300 pt per trip with a selection haptic; release snaps over 0.45 s, it stays 2 s,
// then the loop carries on from that trip.

/** Seconds. */
export const LOOP = 8.8;
const STOP = 2.2;
const SWITCH = 1.4;
const N = 4;
const CARD_W = 264;
const CARD_H = 360;
const PER = 300;
const HOLD = 2;
const SNAP = 0.45;

type Trip = { title: string; date: string; from: string; nights: string; board: string; photo: ImageSourcePropType };
const TRIPS: Trip[] = [
  { title: 'Villa\nEscapes', date: '03 May 2026', from: 'Harbour City', nights: '4 nights', board: 'Self-catering', photo: require('./assets/photo-villa.jpg') },
  { title: 'Winter\nBreaks', date: '11 Jan 2026', from: 'Harbour City', nights: '6 nights', board: 'Half board', photo: require('./assets/photo-snow.jpg') },
  { title: 'Lake\nWeekends', date: '19 Jun 2026', from: 'North Gate', nights: '2 nights', board: 'Breakfast', photo: require('./assets/photo-forest.jpg') },
  { title: 'Night Food\nTrails', date: '07 Oct 2026', from: 'Harbour City', nights: '5 nights', board: 'Room only', photo: require('./assets/photo-street.jpg') },
];

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const BLACK = 'EscapePosterCards-ArchivoExpandedBlack';
const GREEN = '#1E4D2B';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function wrapN(i: number) {
  'worklet';
  return ((i % N) + N) % N;
}
/** Fractional trip index `t` seconds into the loop. */
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  return k + easeInOutCubic(clamp01((u - SWITCH) / (STOP - SWITCH)));
}

/** Photo k: the current one drifts left, the next wipes in from the right. */
function Photo({ k, index, width, height }: { k: number; index: SharedValue<number>; width: number; height: number }) {
  const clipStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    const f = i - base;
    if (wrapN(base) === k) return { left: 0, zIndex: 1, opacity: 1 };
    if (wrapN(base + 1) === k) return { left: width * (1 - f), zIndex: 2, opacity: 1 };
    return { left: 0, zIndex: 0, opacity: 0 };
  });
  const imageStyle = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    const f = i - base;
    if (wrapN(base) === k) return { transform: [{ translateX: -60 * f }] };
    if (wrapN(base + 1) === k) return { transform: [{ translateX: -width * (1 - f) + 60 * (1 - f) }] };
    return { transform: [{ translateX: 0 }] };
  });
  return (
    <Animated.View style={[styles.photoClip, { width, height }, clipStyle]}>
      <Animated.View style={[{ width, height }, imageStyle]}>
        <Image source={TRIPS[k].photo} style={{ width: width + 60, height }} resizeMode="cover" />
      </Animated.View>
    </Animated.View>
  );
}

/** The card's face for trip k; shown while the card faces that trip. */
function Face({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const i = index.get();
    const base = Math.floor(i);
    const f = i - base;
    const showing = wrapN(f < 0.5 ? base : base + 1);
    return { opacity: showing === k ? 1 : 0 };
  });
  const trip = TRIPS[k];
  const rows: [string, string][] = [
    ['DATE', trip.date],
    ['FROM', trip.from],
    ['FOR', trip.nights],
    ['BOARD', trip.board],
  ];
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.face, style]}>
      <Image source={trip.photo} style={styles.window} resizeMode="cover" />
      <Text style={styles.cardTitle}>{trip.title}</Text>
      <View style={styles.rows}>
        {rows.map(([label, value]) => (
          <Text key={label} style={styles.rowText}>
            <Text style={styles.rowLabel}>{label} </Text>
            {value}
          </Text>
        ))}
      </View>
      <Text style={styles.price}>from $640</Text>
    </Animated.View>
  );
}

function Dot({ k, index }: { k: number; index: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const i = index.get();
    let d = Math.abs(((((i - k) % N) + N + N / 2) % N) - N / 2);
    d = Math.min(1, d);
    return { width: 8 + 18 * (1 - d), opacity: 0.45 + 0.55 * (1 - d) };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

type Props = {
  brand?: string;
  action?: string;
  /** Turns to the next trip by itself, every STOP seconds. */
  autoPlay?: boolean;
};

export function EscapePosterCards({ brand = 'Wander Post', action = 'See this trip', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO });
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

  const cardStyle = useAnimatedStyle(() => {
    const i = index.get();
    const f = i - Math.floor(i);
    const angle = f < 0.5 ? f * 180 : (f - 1) * 180;
    const scale = 1 - 0.06 * Math.sin(Math.PI * f);
    return { transform: [{ perspective: 900 }, { rotateY: `${angle}deg` }, { scale }] };
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

  if (!fontsLoaded) return <View style={styles.screen} />;

  const cardTop = height * 0.47 - CARD_H / 2;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {/* Not flattened, so the photos' zIndex stays inside this layer. */}
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            {TRIPS.map((t, k) => (
              <Photo key={t.date} k={k} index={index} width={width} height={height} />
            ))}
          </View>
          <LinearGradient colors={['rgba(0,0,0,0.35)', 'rgba(0,0,0,0)']} style={[styles.shade, { top: 0, height: insets.top + 90 }]} pointerEvents="none" />
          <LinearGradient colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.55)']} style={[styles.shade, { bottom: 0, height: 220 }]} pointerEvents="none" />
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.meta}>Deals · Spring</Text>
          </View>
          <Animated.View style={[styles.card, { top: cardTop, left: (width - CARD_W) / 2 }, cardStyle]} accessible accessibilityLabel="Trip poster. Swipe for the next trip.">
            {TRIPS.map((t, k) => (
              <Face key={t.date} k={k} index={index} />
            ))}
          </Animated.View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.dots} pointerEvents="none">
          {TRIPS.map((t, k) => (
            <Dot key={t.date} k={k} index={index} />
          ))}
        </View>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1B2A22' },
  photoClip: { position: 'absolute', top: 0, overflow: 'hidden' },
  shade: { position: 'absolute', left: 0, right: 0 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 17, color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
  card: { position: 'absolute', width: CARD_W, height: CARD_H, borderRadius: 10, backgroundColor: '#F6F2E7', shadowColor: '#000', shadowOpacity: 0.3, shadowRadius: 24, shadowOffset: { width: 0, height: 14 }, elevation: 12 },
  face: { padding: 10 },
  window: { width: CARD_W - 20, height: 156, borderRadius: 3 },
  cardTitle: { marginTop: 14, marginHorizontal: 4, fontFamily: BLACK, fontSize: 30, lineHeight: 32, color: GREEN, letterSpacing: -0.8 },
  rows: { position: 'absolute', left: 14, bottom: 14 },
  rowText: { fontSize: 11, lineHeight: 15, color: GREEN },
  rowLabel: { fontWeight: '800' },
  price: { position: 'absolute', right: 14, bottom: 14, fontFamily: BLACK, fontSize: 13, color: GREEN },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  dots: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 8, marginBottom: 18 },
  dot: { height: 8, borderRadius: 4, backgroundColor: '#FFFFFF' },
  button: { height: 58, borderRadius: 29, backgroundColor: '#F6F2E7', alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BLACK, fontSize: 15, color: GREEN },
});
