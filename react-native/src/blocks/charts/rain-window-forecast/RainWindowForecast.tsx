import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { cubicBezier, useAnimatedReaction, useAnimatedScrollHandler, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Defs, LinearGradient as SvgGradient, Path as SvgPath, Rect as SvgRect, Stop } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LOOP, MORPH, POINTS, type Weather, weatherBetween } from './rwf-motion';
import { RWF_SKSL, RWF_WET } from './rwf-shader';
import { COLOR, D, FONT, HOURS, makeLayout } from './rwf-theme';
import { type Geo, makeUniforms, makeWetUniforms } from './rwf-uniforms';

/*
 Rain Window Forecast — a weather app's home screen, laid out from the reference pin
 (985232855968876122): the menu and the calendar; the city; a large weather icon; the temperature
 and a line about the day; wind, rain chance and sunshine; "Hourly Forecast" with a row of cards
 that scrolls sideways; the tab bar.

 The icons are glass: a sun (or a crescent moon) behind a cloud of frosted glass that lets its
 warmth through, and glossy blue drops that slide out of the cloud and fade. Tap an hour and the
 large icon turns into that hour's weather — the sun sinks behind the cloud, the moon rises, the
 rain thickens — while the temperature and the line change and the warm glow behind them cools.
 When the hour is a heavy one, rain gathers on the phone's own glass: drops run down over
 everything, leaving trails, until you wipe them away with a finger; they gather again.

 autoPlay: LOOP = 10 s (6pm, the screen rains over, a wipe, 7pm, back to now and the glass dries);
 frame 10 s equals frame 0, the pin's state. A touch takes over; 2 s after the last one the screen
 goes back to now and dry, and the loop comes round again. Reduced motion: no loop, no running drops
 on the glass; an hour's icon changes with a short crossfade.
*/

const page = Skia.RuntimeEffect.Make(RWF_SKSL);
const wetFx = Skia.RuntimeEffect.Make(RWF_WET);
if (!page || !wetFx) throw new Error('Rain Window Forecast: a shader failed to compile.');
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EMPTY = Array.from({ length: POINTS * 3 }, (_, i) => (i % 3 === 2 ? -1 : 0));
const STEP = 16;

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}
function soft() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function RainWindowForecast({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Outfit-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Outfit-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Outfit-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const cardW = D.cards.w * sx;
  const cardGap = D.cards.gap * sx;
  const cardY = L.Y(D.cards.y);
  const geo = useMemo<Geo>(
    () => ({
      w,
      h,
      hero: [D.hero.x * sx, L.Y(D.hero.y), D.hero.s * sx],
      cards: HOURS.map((_, i) => [(D.cards.x + D.cards.w / 2 + i * (D.cards.w + D.cards.gap)) * sx, cardY + D.cards.icon.dy * L.k, D.cards.icon.s * sx] as [number, number, number]),
      tab: [D.tab.x * sx, L.Y(D.tab.y), D.tab.w * sx, D.tab.h * L.k],
    }),
    [w, h, sx, L, cardY],
  );

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  // the rain's own time runs on in every mode, so the drops never jump
  const rainT = useSharedValue(0);
  const prevT = useSharedValue(0);
  const weather = useSharedValue<Weather>(weatherBetween(0, 0, 0));
  const morphFrom = useSharedValue(0);
  const morphTo = useSharedValue(0);
  const morph = useSharedValue(1);
  const wet = useSharedValue(0);
  const wipe = useSharedValue<number[]>(EMPTY);
  const pts = useSharedValue<number[]>(EMPTY);
  const scroll = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [hour, setHour] = useState(0);
  const [selected, setSelected] = useState(-1);

  /* ------------------------------------------------------------ the forecast */
  const pick = (i: number) => {
    'worklet';
    // from whatever the icon shows now
    morphFrom.set(morphTo.get());
    morphTo.set(i);
    morph.set(0);
    morph.set(withTiming(1, { duration: reduced ? 150 : MORPH * 1000 }));
    wet.set(withTiming(!reduced && HOURS[i].rain >= 1 ? 1 : i === 0 ? 0 : wet.get(), { duration: HOURS[i].rain >= 1 ? 1600 : 1600 }));
    scheduleOnRN(setHour, i);
    scheduleOnRN(setSelected, i === 0 ? -1 : i);
    scheduleOnRN(tick);
  };
  const push = (x: number, y: number, stamp: number) => {
    'worklet';
    const a = pts.get().slice(3);
    a.push(x, y, stamp);
    pts.set(a);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // the loop's moment becomes the real state: its hour, its rain, its wipe
      const s = frameAt(rainT.get(), w, h);
      morphFrom.set(s.hour);
      morphTo.set(s.hour);
      morph.set(1);
      weather.set(s.weather);
      wet.set(s.wet);
      const a: number[] = [];
      for (let i = 0; i < POINTS; i++) a.push(s.wipe[i * 3], s.wipe[i * 3 + 1], s.wipe[i * 3 + 2] < 0 ? -1 : clock.get() - s.wipe[i * 3 + 2]);
      pts.set(a);
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    prevT.set(rainT.get());
    if (pin.get() >= 0) rainT.set(pin.get());
    else if (!reduced) rainT.set((rainT.get() + dt) % LOOP);
    if (mode.get() === 0) {
      const s = frameAt(rainT.get(), w, h);
      weather.set(s.weather);
      wet.set(s.wet);
      wipe.set(s.wipe);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerCard >= 0) {
        fingerX.set(geo.cards[s.fingerCard][0] - scroll.get());
        fingerY.set(cardY + (D.cards.h * L.k) / 2);
      } else {
        fingerX.set(s.fingerX);
        fingerY.set(s.fingerY);
      }
      return;
    }
    weather.set(weatherBetween(morphFrom.get(), morphTo.get(), morph.get()));
    const p = pts.get();
    const out: number[] = [];
    for (let i = 0; i < POINTS; i++) {
      const stamp = p[i * 3 + 2];
      out.push(p[i * 3], p[i * 3 + 1], stamp < 0 ? -1 : clock.get() - stamp);
    }
    wipe.set(out);
    if (!auto || clock.get() - lastTouch.get() < 2) return;
    // back to now and dry, then the loop when the rain's clock comes round
    if (morphTo.get() !== 0) {
      pick(0);
      wet.set(withTiming(0, { duration: 1500 }));
      return;
    }
    if (morph.get() >= 1 && wet.get() < 0.001 && rainT.get() < prevT.get()) {
      pts.set(EMPTY);
      mode.set(0);
    }
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    if (!auto)
      scheduleOnUI(() => {
        'worklet';
        finger.set(0);
        fingerPress.set(0);
      });
  }, [auto, mode, finger, fingerPress]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__rainWindow = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), t: +rainT.get().toFixed(2), wet: +wet.get().toFixed(2), sun: +weather.get().sun.toFixed(2) }),
    };
  }, [pin, clock, mode, rainT, wet, weather]);

  // the readings follow the loop's hour too
  useAnimatedReaction(
    () => (mode.get() === 0 ? frameAt(rainT.get(), w, h) : null),
    (s, before) => {
      if (!s) return;
      if (!before || s.hour !== before.hour) scheduleOnRN(setHour, s.hour);
      if (!before || s.selected !== before.selected) scheduleOnRN(setSelected, s.selected);
    },
  );

  /* ------------------------------------------------------------ gestures */
  const wipeGesture = Gesture.Pan()
    .minDistance(6)
    .onStart((e) => {
      takeOver();
      push(0, 0, -1);
      push(e.x, e.y, clock.get());
      if (wet.get() > 0.2) scheduleOnRN(soft);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const p = pts.get();
      const n = p.length;
      if (Math.hypot(e.x - p[n - 3], e.y - p[n - 2]) >= STEP) push(e.x, e.y, clock.get());
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
    });
  const onScroll = useAnimatedScrollHandler((e) => {
    scroll.set(e.contentOffset.x);
  });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => makeUniforms(geo, { weather: weather.get(), t: rainT.get(), loop: LOOP, scroll: scroll.get() }, 0));
  const iconUniforms = useDerivedValue(() => makeUniforms(geo, { weather: weather.get(), t: rainT.get(), loop: LOOP, scroll: scroll.get() }, 1));
  const wetUniforms = useDerivedValue(() => makeWetUniforms(geo, { t: rainT.get(), loop: LOOP, wet: wet.get(), wipe: wipe.get() }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.14 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.5 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const H0 = HOURS[hour];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={wipeGesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${H0.label === 'Now' ? 'Now' : H0.label}, ${H0.temp} degrees, ${H0.line}`}>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Rect x={0} y={0} width={w} height={h}>
              <Shader source={page!} uniforms={uniforms} />
            </Rect>
          </Canvas>
        </View>
      </GestureDetector>

      {fontsLoaded && (
        <>
          {/* the header */}
          <View style={[styles.abs, { left: D.menu.x * sx, top: L.Y(D.menu.y) - 9 }]} pointerEvents="none">
            <Svg width={24} height={18} viewBox="0 0 24 18">
              <Defs>
                <SvgGradient id="m" x1="0" y1="0" x2="1" y2="0">
                  <Stop offset="0" stopColor="#9FD8FF" />
                  <Stop offset="1" stopColor="#E9F6FF" />
                </SvgGradient>
              </Defs>
              <SvgRect x={0} y={2} width={14} height={2.6} rx={1.3} fill="url(#m)" />
              <SvgRect x={0} y={9.8} width={22} height={2.6} rx={1.3} fill="url(#m)" />
            </Svg>
          </View>
          <View style={[styles.abs, { left: D.calendar.x * sx, top: L.Y(D.calendar.y) }]} pointerEvents="none">
            <Svg width={22} height={24} viewBox="0 0 22 24">
              <SvgRect x={1.2} y={3.6} width={19.6} height={19.2} rx={5} stroke="#FFFFFF" strokeWidth={1.8} fill="none" />
              <SvgPath d="M6.5 1 V6 M15.5 1 V6 M1.5 9.5 H20.5" stroke="#FFFFFF" strokeWidth={1.8} strokeLinecap="round" />
              {[0, 1, 2].map((c) => [0, 1].map((r) => <SvgCircle key={`${c}${r}`} cx={7 + c * 4} cy={14 + r * 4} r={1.15} fill="#FFFFFF" />))}
            </Svg>
          </View>
          <Text style={[styles.city, { top: L.Y(D.city.y) - 13 }]} allowFontScaling={false} pointerEvents="none">
            Mumbai,<Text style={styles.country}> India</Text>
          </Text>

          {/* the temperature and the line */}
          <View style={[styles.tempRow, { top: L.Y(D.temp.y) - 28 }]} pointerEvents="none">
            <Animated.Text key={`t${H0.temp}`} style={styles.temp} allowFontScaling={false}>
              {H0.temp}
            </Animated.Text>
            <View style={styles.degree} />
            <Text style={styles.tempUnit} allowFontScaling={false}>
              C
            </Text>
          </View>
          <Animated.Text style={[styles.line, { top: L.Y(D.line.y) - 11, opacity: 1, transitionProperty: 'opacity', transitionDuration: 200, transitionTimingFunction: CSS_EASE_OUT }]} allowFontScaling={false} pointerEvents="none">
            {H0.line}
          </Animated.Text>

          {/* wind, rain chance, sunshine */}
          <View style={[styles.statsRow, { top: L.Y(D.stats.y) - 12 }]} pointerEvents="none">
            <Stat x={D.stats.xs[0] * sx} icon="wind" text="11km/hr" />
            <Stat x={D.stats.xs[1] * sx} icon="drop" text="02%" />
            <Stat x={D.stats.xs[2] * sx} icon="sun" text="8hr" />
          </View>

          {/* the hourly forecast */}
          <View style={[styles.hourlyHead, { left: D.hourly.x * sx, top: L.Y(D.hourly.y) - 12 }]} pointerEvents="none">
            <Svg width={16} height={16} viewBox="0 0 16 16">
              <SvgCircle cx={8} cy={8} r={6.8} stroke="#FFFFFF" strokeWidth={1.7} fill="none" />
              <SvgPath d="M8 4.4 V8 L10.4 9.6" stroke="#FFFFFF" strokeWidth={1.7} fill="none" strokeLinecap="round" />
            </Svg>
            <Text style={styles.hourlyTitle} allowFontScaling={false}>
              Hourly Forecast
            </Text>
          </View>
          <Animated.ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            onScroll={onScroll}
            scrollEventThrottle={16}
            style={[styles.cards, { top: cardY, height: D.cards.h * L.k }]}
            contentContainerStyle={{ paddingHorizontal: D.cards.x * sx, gap: cardGap }}
          >
            {HOURS.map((H, i) => (
              <Pressable
                key={H.label}
                accessibilityRole="button"
                accessibilityState={{ selected: i === selected }}
                accessibilityLabel={`${H.label}, ${H.temp} degrees`}
                onPress={() =>
                  scheduleOnUI(() => {
                    'worklet';
                    takeOver();
                    pick(i);
                  })
                }
              >
                <LinearGradient colors={i === selected ? ['#3A3D5A', '#2A2D49'] : ['#2A2C40', '#1E2037']} style={[styles.card, { width: cardW, height: D.cards.h * L.k }]}>
                  <Text style={[styles.cardLabel, { top: (D.cards.label - 11) * L.k }]} allowFontScaling={false}>
                    {H.label}
                  </Text>
                  <View style={[styles.cardTempRow, { top: (D.cards.temp - 14) * L.k }]}>
                    <Text style={styles.cardTemp} allowFontScaling={false}>
                      {H.temp}
                    </Text>
                    <View style={styles.cardDegree} />
                  </View>
                </LinearGradient>
              </Pressable>
            ))}
          </Animated.ScrollView>
        </>
      )}
      {/* the card icons, over the cards */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={cardY} width={w} height={D.cards.h * L.k * 0.6}>
          <Shader source={page!} uniforms={iconUniforms} />
        </Rect>
      </Canvas>

      {/* the tab bar's icons (its slab is in the page shader) */}
      <View style={[styles.abs, { top: L.Y(D.tab.y), left: 0, width: w, height: D.tab.h * L.k }]} pointerEvents="box-none">
        {D.tab.icons.map((x, i) => (
          <View key={i} style={[styles.abs, { left: x * sx - 11, top: (D.tab.h * L.k) / 2 - 11 }]} accessible accessibilityRole="tab" accessibilityLabel={['Home', 'Search', 'Alerts', 'Map'][i]} accessibilityState={{ selected: i === 0 }}>
            <TabIcon kind={i} />
          </View>
        ))}
      </View>

      {/* rain on the phone's own glass, over everything */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={wetFx!} uniforms={wetUniforms} />
        </Rect>
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

function Stat({ x, icon, text }: { x: number; icon: 'wind' | 'drop' | 'sun'; text: string }) {
  return (
    <View style={[styles.stat, { left: x }]}>
      <Svg width={20} height={20} viewBox="0 0 20 20">
        {icon === 'wind' && <SvgPath d="M2 7 H12 a2.6 2.6 0 1 0 -2.6 -2.6 M2 11 H16 a2.6 2.6 0 1 1 -2.6 2.6 M2 15 H9" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinecap="round" />}
        {icon === 'drop' && <SvgPath d="M10 2.5 C10 2.5 4.5 8.6 4.5 12.2 a5.5 5.5 0 0 0 11 0 C15.5 8.6 10 2.5 10 2.5 Z M7.5 13.6 a2.6 2.6 0 0 0 5 0" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinejoin="round" strokeLinecap="round" />}
        {icon === 'sun' && <SvgPath d="M10 6 a4 4 0 1 0 0.01 0 M10 1.2 V2.6 M10 17.4 V18.8 M1.2 10 H2.6 M17.4 10 H18.8 M3.8 3.8 L4.8 4.8 M15.2 15.2 L16.2 16.2 M3.8 16.2 L4.8 15.2 M15.2 4.8 L16.2 3.8" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinecap="round" />}
      </Svg>
      <Text style={styles.statText} allowFontScaling={false}>
        {text}
      </Text>
    </View>
  );
}

function TabIcon({ kind }: { kind: number }) {
  if (kind === 0)
    return (
      <Svg width={22} height={22} viewBox="0 0 22 22">
        <Defs>
          <SvgGradient id="h" x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor="#F2FBFF" />
            <Stop offset="1" stopColor="#A9DDF8" />
          </SvgGradient>
        </Defs>
        <SvgPath d="M3 9.6 L11 2.6 L19 9.6 V18 a2 2 0 0 1 -2 2 H14 V14.4 a1.6 1.6 0 0 0 -1.6 -1.6 H9.6 A1.6 1.6 0 0 0 8 14.4 V20 H5 a2 2 0 0 1 -2 -2 Z" fill="url(#h)" />
      </Svg>
    );
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      {kind === 1 && <SvgPath d="M10 2.6 a7.4 7.4 0 1 0 0.01 0 M15.4 15.4 L19.6 19.6" stroke={COLOR.tabIcon} strokeWidth={1.8} fill="none" strokeLinecap="round" />}
      {kind === 2 && <SvgPath d="M11 2.4 C7.4 2.4 5.6 5 5.6 8.4 V12.6 L3.8 15.6 H18.2 L16.4 12.6 V8.4 C16.4 5 14.6 2.4 11 2.4 Z M8.8 18.2 a2.2 2.2 0 0 0 4.4 0" stroke={COLOR.tabIcon} strokeWidth={1.8} fill="none" strokeLinejoin="round" />}
      {kind === 3 && <SvgPath d="M2.6 5.2 L8 3 L14 5.6 L19.4 3.4 V16.8 L14 19 L8 16.4 L2.6 18.6 Z M8 3 V16.4 M14 5.6 V19" stroke={COLOR.tabIcon} strokeWidth={1.8} fill="none" strokeLinejoin="round" />}
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  city: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.semibold, fontSize: 18, lineHeight: 26, color: COLOR.ink },
  country: { fontFamily: FONT.regular, fontSize: 16, color: COLOR.mute },
  tempRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'flex-start', paddingRight: 4 },
  temp: { fontFamily: FONT.semibold, fontSize: 46, lineHeight: 56, color: COLOR.ink, letterSpacing: -0.5 },
  degree: { width: 13, height: 13, borderRadius: 6.5, borderWidth: 2.6, borderColor: COLOR.ink, marginLeft: 3, marginTop: 1 },
  tempUnit: { fontFamily: FONT.semibold, fontSize: 24, lineHeight: 30, color: COLOR.ink, marginLeft: 4, marginTop: 22 },
  line: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: 16, lineHeight: 22, color: '#ECECEE' },
  statsRow: { position: 'absolute', left: 0, right: 0, height: 24 },
  stat: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 9 },
  statText: { fontFamily: FONT.medium, fontSize: 17, lineHeight: 22, color: COLOR.text },
  hourlyHead: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 9 },
  hourlyTitle: { fontFamily: FONT.semibold, fontSize: 19, lineHeight: 24, color: COLOR.ink },
  cards: { position: 'absolute', left: 0, right: 0 },
  card: { borderRadius: D.cards.r, overflow: 'hidden' },
  cardLabel: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: 17, lineHeight: 22, color: '#EDEDF0' },
  cardTempRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'flex-start', paddingLeft: 6 },
  cardTemp: { fontFamily: FONT.medium, fontSize: 22, lineHeight: 28, color: COLOR.ink },
  cardDegree: { width: 7, height: 7, borderRadius: 3.5, borderWidth: 1.6, borderColor: COLOR.ink, marginLeft: 2, marginTop: 2 },
});
