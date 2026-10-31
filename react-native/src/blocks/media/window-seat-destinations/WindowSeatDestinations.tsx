import { BlurMask, Canvas, Group, Image, LinearGradient, Oval, Rect, RoundedRect, Skia, useImage, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// An invented flight app, porthole: the whole screen is a cabin wall with one big airplane window.
// Behind its bevelled frame and the glare on the glass the view drifts past as if the plane were
// flying (clouds slide by, the land pans slowly); then the plastic shade slides down, the next
// destination appears behind it and the shade lifts again, while the destination's name, route
// and fare change below. Four destinations: snowy ridges, a coral atoll, a city at dusk, dunes.
// Pull the shade down with your finger to fly on to the next one.
//
// Artwork: assets/view-alps.jpg, view-lagoon.jpg, view-city.jpg, view-dunes.jpg (generated, prompts
// in assets/PROMPTS.md), cover-fitted in the window opening and panned 70 pt across each stop.
//
// Layout (393 x 852 pt): headline "Where to / next?" Instrument Serif 54/56 pt under a 34 pt top
// row; window 300 x 424 pt (radius 140) centred at 49 % of the height, its opening 244 x 368 pt
// (radius 114); destination name Instrument Serif 36 pt with route and fare, four dots, a 58 pt
// button.
//
// autoPlay timeline (LOOP = 8 s): four stops of 2 s. In each (local u = t - 2k):
//   0.00-1.25  the view pans (whole stop: x from +35 to -35 pt, linear), clouds drift
//   1.25-1.60  the shade slides down (ease-in cubic) and closes
//   1.65       the next destination swaps in behind the closed shade
//   1.70-2.00  the shade lifts (ease-out cubic); the caption cross-fades over 1.55-1.85
// Clouds and a 1.2 pt turbulence wobble turn a whole number of times per loop, so the loop is
// seamless. Dragging down on the window pulls the shade; past half it closes and flies on to the
// next stop (light haptic), otherwise it lifts again; 2 s later the loop carries on from there.

/** Seconds. */
export const LOOP = 8;
const STOP = 2;
const FRAME_W = 300;
const FRAME_H = 424;
const OPEN_W = 244;
const OPEN_H = 368;
const PAN = 70;
const HOLD = 2;

const C = {
  wallTop: '#F1F4F7',
  wallBottom: '#C9D3DC',
  ink: '#13243A',
  soft: '#5E6E80',
  shade: '#EEF1F4',
  shadeRib: '#DDE3E9',
};

const STOPS = [
  { name: 'Alpine Ridge', route: 'Geneva → Sion · 55 min', fare: '$129' },
  { name: 'Nalu Atoll', route: 'Malé → Nalu · 1 h 20 min', fare: '$214' },
  { name: 'Velmar Harbour', route: 'Porto → Velmar · 1 h 05 min', fare: '$96' },
  { name: 'Amber Dunes', route: 'Ouarzi → Merz · 1 h 40 min', fare: '$158' },
];

const SERIF = require('./assets/InstrumentSerif-Regular.ttf');
const ITALIC = require('./assets/InstrumentSerif-Italic.ttf');
const SERIF_FAMILY = 'WindowSeatDestinations-InstrumentSerif';
const ITALIC_FAMILY = 'WindowSeatDestinations-InstrumentSerifItalic';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

/** Shade (0 open, 1 closed), which view shows, pan (0-1 across the stop) and caption, at t. */
function stopAt(t: number) {
  'worklet';
  const k = Math.floor(t / STOP);
  const u = t - k * STOP;
  const shade = u < 1.65 ? easeInCubic(seg(u, 1.25, 1.6)) : 1 - easeOutCubic(seg(u, 1.7, 2.0));
  const view = u < 1.65 ? k % 4 : (k + 1) % 4;
  // The caption index runs smoothly from k to k + 1 over 1.55-1.85.
  const caption = k + smooth(seg(u, 1.55, 1.85));
  const pan = u < 1.65 ? (u + 0.35) / STOP : (u - 1.65) / STOP;
  return { shade, view, caption, pan };
}

function Caption({ k, caption }: { k: number; caption: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const c = ((caption.get() % 4) + 4) % 4;
    let d = Math.abs(c - k);
    d = Math.min(d, 4 - d);
    const o = 1 - smooth(d / 0.5);
    return { opacity: o, transform: [{ translateY: 12 * (1 - o) }] };
  });
  const s = STOPS[k];
  return (
    <Animated.View style={[styles.caption, style]}>
      <View style={styles.captionRow}>
        <Text style={styles.name}>{s.name}</Text>
        <Text style={styles.fare}>{s.fare}</Text>
      </View>
      <Text style={styles.route}>{s.route}</Text>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  seat?: string;
  headline?: string;
  action?: string;
  /** Flies from view to view by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function WindowSeatDestinations({ brand = 'porthole', seat = 'Seat 14A', headline = 'Where to\nnext?', action = 'Search flights', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [SERIF_FAMILY]: SERIF, [ITALIC_FAMILY]: ITALIC });
  const alps = useImage(require('./assets/view-alps.jpg'));
  const lagoon = useImage(require('./assets/view-lagoon.jpg'));
  const city = useImage(require('./assets/view-city.jpg'));
  const dunes = useImage(require('./assets/view-dunes.jpg'));
  const views = useMemo(() => [alps, lagoon, city, dunes], [alps, lagoon, city, dunes]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.49;
  const fx = cx - FRAME_W / 2;
  const fy = cy - FRAME_H / 2;
  const ox = cx - OPEN_W / 2;
  const oy = cy - OPEN_H / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const shade = useSharedValue(0);
  const view = useSharedValue(0);
  const caption = useSharedValue(0);
  const pan = useSharedValue(0.175);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const grabShade = useSharedValue(0);
  const fromShade = useSharedValue(0);
  const advance = useSharedValue(false);
  const target = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    clock.set(t);
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      if (advance.get()) {
        // Close, swap to the next view behind the shade, lift.
        if (since < 0.2) shade.set(fromShade.get() + (1 - fromShade.get()) * easeInCubic(since / 0.2));
        else if (since < 0.3) {
          shade.set(1);
          view.set(target.get());
          caption.set(target.get());
        } else shade.set(1 - easeOutCubic(clamp01((since - 0.3) / 0.3)));
      } else {
        shade.set(fromShade.get() * (1 - easeOutCubic(clamp01(since / 0.3))));
      }
      pan.set(Math.min(1, pan.get() + dt / STOP));
      if (since > 0.6 + HOLD) {
        manual.set(false);
        origin.set(now - view.get() * STOP);
      }
      return;
    }
    if (!autoPlay) return;
    const s = stopAt(t);
    shade.set(s.shade);
    view.set(s.view);
    caption.set(s.caption);
    pan.set(s.pan);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  // The window: frame, the view clipped to the opening, clouds, glare and the shade.
  const opening = useMemo(() => Skia.RRectXY(Skia.XYWHRect(ox, oy, OPEN_W, OPEN_H), 114, 114), [ox, oy]);
  const wobble = useDerivedValue(() => [{ translateY: 1.2 * Math.sin((clock.get() / LOOP) * Math.PI * 2 * 5) }]);
  const viewImage = useDerivedValue(() => views[view.get()] ?? null);
  const viewX = useDerivedValue(() => ox - (OPEN_H - OPEN_W) / 2 + PAN / 2 - PAN * pan.get());
  const shadeH = useDerivedValue(() => OPEN_H * shade.get());
  const handleY = useDerivedValue(() => oy + OPEN_H * shade.get() - 12);
  const cloudA = useDerivedValue(() => ox + OPEN_W + 60 - (((clock.get() / LOOP) * 3) % 1) * (OPEN_W + 220));
  const cloudB = useDerivedValue(() => ox + OPEN_W + 60 - (((clock.get() / LOOP) * 2 + 0.55) % 1) * (OPEN_W + 220));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pull = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabShade.set(shade.get());
      caption.set(Math.round(caption.get()) % 4);
      view.set(Math.round(caption.get()) % 4);
    })
    .onUpdate((e) => {
      shade.set(clamp01(grabShade.get() + e.translationY / OPEN_H));
    })
    .onFinalize(() => {
      touching.set(false);
      fromShade.set(shade.get());
      advance.set(shade.get() > 0.5);
      target.set((view.get() + 1) % 4);
      if (shade.get() > 0.5) scheduleOnRN(haptic);
      pan.set(0);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: C.wallTop }]} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pull}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Airplane window. Pull the shade down to fly to the next destination.">
            <Rect x={0} y={0} width={width} height={height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[C.wallTop, C.wallBottom]} />
            </Rect>
            {/* Panel seams of the cabin wall. */}
            <Rect x={cx - FRAME_W / 2 - 34} y={0} width={1} height={height} color="rgba(19,36,58,0.06)" />
            <Rect x={cx + FRAME_W / 2 + 34} y={0} width={1} height={height} color="rgba(19,36,58,0.06)" />
            <Group transform={wobble}>
              {/* The recess and the bevelled frame. */}
              <RoundedRect x={fx - 14} y={fy - 14} width={FRAME_W + 28} height={FRAME_H + 28} r={154} color="rgba(19,36,58,0.10)">
                <BlurMask blur={10} style="normal" />
              </RoundedRect>
              <RoundedRect x={fx} y={fy} width={FRAME_W} height={FRAME_H} r={140}>
                <LinearGradient start={vec(fx, fy)} end={vec(fx + FRAME_W, fy + FRAME_H)} colors={['#FFFFFF', '#E2E8EE', '#BFC9D3']} />
              </RoundedRect>
              <RoundedRect x={ox - 8} y={oy - 8} width={OPEN_W + 16} height={OPEN_H + 16} r={122}>
                <LinearGradient start={vec(ox, oy)} end={vec(ox, oy + OPEN_H)} colors={['#9EABB8', '#E9EEF2']} />
              </RoundedRect>
              <Group clip={opening}>
                <Rect x={ox} y={oy} width={OPEN_W} height={OPEN_H} color="#8FA6BC" />
                <Image image={viewImage} x={viewX} y={oy} width={OPEN_H} height={OPEN_H} fit="cover" />
                <Group opacity={0.75}>
                  <Oval x={cloudA} y={oy + 70} width={170} height={54} color="#FFFFFF">
                    <BlurMask blur={16} style="normal" />
                  </Oval>
                  <Oval x={cloudB} y={oy + 210} width={210} height={64} color="#FFFFFF">
                    <BlurMask blur={18} style="normal" />
                  </Oval>
                </Group>
                {/* Glare on the glass. */}
                <Rect x={ox} y={oy} width={OPEN_W} height={OPEN_H}>
                  <LinearGradient start={vec(ox, oy)} end={vec(ox + OPEN_W, oy + OPEN_H * 0.6)} colors={['rgba(255,255,255,0.35)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0.12)']} positions={[0, 0.45, 1]} />
                </Rect>
                {/* The shade, ribbed, with its pull tab. */}
                <Rect x={ox} y={oy} width={OPEN_W} height={shadeH}>
                  <LinearGradient start={vec(ox, 0)} end={vec(ox + OPEN_W, 0)} colors={[C.shadeRib, C.shade, C.shadeRib]} />
                </Rect>
                <RoundedRect x={cx - 26} y={handleY} width={52} height={10} r={5} color="#C3CCD5" />
              </Group>
              <RoundedRect x={ox} y={oy} width={OPEN_W} height={OPEN_H} r={114} color="rgba(19,36,58,0.25)" style="stroke" strokeWidth={2} />
            </Group>
          </Canvas>

          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>{brand}</Text>
              <View style={styles.seat}>
                <Text style={styles.seatText}>{seat}</Text>
              </View>
            </View>
            <Text style={styles.headline}>{headline}</Text>
          </View>

          <View style={[styles.captions, { top: fy + FRAME_H + 26 }]} pointerEvents="none">
            {STOPS.map((s, k) => (
              <Caption key={s.name} k={k} caption={caption} />
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
  screen: { flex: 1, backgroundColor: C.wallTop },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: ITALIC_FAMILY, fontSize: 28, color: C.ink },
  seat: { height: 28, paddingHorizontal: 12, borderRadius: 14, backgroundColor: 'rgba(19,36,58,0.08)', justifyContent: 'center' },
  seatText: { fontSize: 13, fontWeight: '700', color: C.ink, letterSpacing: 0.3 },
  headline: { marginTop: 6, fontFamily: SERIF_FAMILY, fontSize: 54, lineHeight: 56, color: C.ink, letterSpacing: -1 },
  captions: { position: 'absolute', left: 28, right: 28, height: 70 },
  caption: { position: 'absolute', left: 0, right: 0 },
  captionRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  name: { fontFamily: SERIF_FAMILY, fontSize: 36, lineHeight: 42, color: C.ink },
  fare: { fontSize: 17, fontWeight: '700', color: C.ink },
  route: { marginTop: 2, fontSize: 15, lineHeight: 20, color: C.soft, fontWeight: '500' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  button: { height: 58, borderRadius: 29, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
