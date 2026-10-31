import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolate,
  interpolateColor,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LOOP, smooth } from './cct-motion';
import { CCT_SKSL } from './cct-shader';
import { C, CATEGORIES, FONT, GHOST_S, HOP, ICON_S, ICON_Y, LABEL_Y, type Listing, makeLayout, SEARCH_H, STAGE_H } from './cct-theme';

/*
 Clay Category Tabs — the top of a travel app, where the category tabs are clay and one icon is alive.

 The screen: a search pill, three tabs (Homes, Experiences, Services) and, under them, a page of
 listings for the chosen one.

 Signature: the chosen tab's icon is one lit clay object — a house with smoke from its chimney, a
 striped hot-air balloon, a brass concierge bell — and the other tabs hold small grey clay ghosts of
 theirs. Change tab and the object does not swap: it hops along the row to the new tab and becomes the
 next category on the way (the house melts into the balloon, the balloon into the bell), landing with
 a squash; the bell rings when it lands. Tap a tab, or drag the listings sideways: the page drag
 scrubs the hop and the melt 1:1 and holds wherever the finger stops; let go and it settles on the
 nearest tab at the finger's speed.

 autoPlay: LOOP = 9 s (tap Experiences; drag the page to Services, holding half-way; tap Homes);
 frame 9 s equals frame 0. A touch takes over; 2 s after the last one the tabs go back to Homes and
 the loop starts again.
 Reduced motion: no loop; a tab change is a short cross-melt without the hop, squash or ring.
*/

const effect = Skia.RuntimeEffect.Make(CCT_SKSL);
if (!effect) throw new Error('Clay Category Tabs: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const LAST = CATEGORIES.length - 1;

function buzz(kind: 'tick' | 'land') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function ClayCategoryTabs({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [width, setWidth] = useState(402);
  const L = useMemo(() => makeLayout(width, insets.top), [width, insets.top]);
  const [selected, setSelected] = useState(0); // for VoiceOver

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const time = useSharedValue(0); // the smoke's clock: the loop's own time in the loop, then running on
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const r = useSharedValue(0); // the category, 0 … 2, continuous between tabs
  const squash = useSharedValue(0);
  const knob = useSharedValue(0);
  const wobble = useSharedValue(0);
  const finger = useSharedValue(0);
  const onPage = useSharedValue(0);
  const fingerTab = useSharedValue(0);
  const dragX = useSharedValue(0);
  const press = useSharedValue(0);
  const dragging = useSharedValue(0);
  const moving = useSharedValue(0);
  const r0 = useSharedValue(0);

  /* ------------------------------------------------------------ landing and moving (declared before the clock: worklets capture what is above them) */
  const onLand = useCallback((k: number) => {
    buzz('land');
    setSelected(k);
  }, []);
  const onTick = useCallback(() => buzz('tick'), []);
  useAnimatedReaction(
    () => Math.round(Math.max(0, Math.min(LAST, r.get()))),
    (now, before) => {
      if (dragging.get() === 1 && before !== null && now !== before) scheduleOnRN(onTick);
    },
  );

  function land(k: number) {
    'worklet';
    moving.set(0);
    if (!reduced) {
      squash.set(withSequence(withTiming(0.55, { duration: 90, easing: EASE_OUT }), withSpring(0, { duration: 650, dampingRatio: 0.35 })));
      if (k === 2) {
        knob.set(withSequence(withTiming(1, { duration: 120, easing: EASE_OUT }), withTiming(0, { duration: 180, easing: EASE_OUT })));
        wobble.set(withSequence(withTiming(0.14, { duration: 70 }), withSpring(0, { duration: 1000, dampingRatio: 0.2 })));
      }
    }
    scheduleOnRN(onLand, k);
  }

  function goTo(k: number) {
    'worklet';
    moving.set(1);
    const dist = Math.abs(k - r.get());
    const ms = reduced ? 260 : 700 + 400 * dist;
    r.set(
      withTiming(k, { duration: ms, easing: EASE_IN_OUT, reduceMotion: NEVER }, (done) => {
        if (done) land(k);
      }),
    );
  }

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    press.set(withTiming(0, { duration: 150 }));
    squash.set(withTiming(0, { duration: 200 }));
    knob.set(withTiming(0, { duration: 150 }));
    wobble.set(withTiming(0, { duration: 200 }));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      time.set(t);
      r.set(s.r);
      squash.set(s.squash);
      knob.set(s.knob);
      wobble.set(s.wobble);
      finger.set(s.finger);
      onPage.set(s.onPage);
      fingerTab.set(s.tab);
      dragX.set(s.dragX);
      press.set(s.press);
      return;
    }
    if (!reduced) time.set(time.get() + dt);
    if (dragging.get() === 1 || moving.get() === 1) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      // the loop starts on Homes: go home first
      if (r.get() > 0.001) goTo(0);
    } else if (moving.get() === 0 && r.get() < 0.001 && Math.abs(squash.get()) < 0.002 && Math.abs(wobble.get()) < 0.002) {
      resumeAsked.set(0);
      r.set(0);
      squash.set(0);
      time.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__cct = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), r: +r.get().toFixed(3), sq: +squash.get().toFixed(3) }),
    };
  }, [pin, stay, mode, r, squash]);

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const tap = (k: number) => {
    takeOver();
    if (Math.abs(r.get() - k) < 0.001 && moving.get() === 0) return;
    goTo(k);
  };

  // the listings: drag them sideways to scrub the hop; let go and they settle on the nearest page
  const page = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-12, 12])
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      dragging.set(1);
      moving.set(0);
      r.set(r.get()); // catch it where it is
      r0.set(r.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let v = r0.get() - e.translationX / L.w;
      if (v < 0) v = -0.12 * (1 - Math.exp(v / 0.12));
      if (v > LAST) v = LAST + 0.12 * (1 - Math.exp(-(v - LAST) / 0.12));
      r.set(v);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      dragging.set(0);
      const v = -e.velocityX / L.w;
      const k = Math.max(0, Math.min(LAST, Math.round(r.get() + 0.22 * v)));
      moving.set(1);
      r.set(
        reduced
          ? withTiming(k, { duration: 260, easing: EASE_OUT, reduceMotion: NEVER }, (done) => {
              if (done) land(k);
            })
          : withSpring(k, { duration: 650, dampingRatio: 0.9, velocity: v }, (done) => {
              if (done) land(k);
            }),
      );
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = r.get();
    const kc = Math.max(0, Math.min(LAST, k));
    const frac = kc - Math.floor(kc);
    const hop = reduced ? 0 : HOP * Math.sin(Math.PI * frac);
    const x = interpolate(k, [0, 1, 2], L.xs, 'extend');
    const ghost = (i: number) => smooth(0.15, 0.85, Math.abs(k - i));
    return {
      uObj: [x, ICON_Y - hop, ICON_S * (1 - 0.08 * Math.sin(Math.PI * frac)), squash.get()],
      uMorph: [k, 0.35 + (reduced ? 0 : 0.5 * Math.sin(Math.PI * frac)), wobble.get(), knob.get()],
      uGhostX: [L.xs[0], L.xs[1], L.xs[2], ICON_Y],
      uGhostA: [ghost(0), ghost(1), ghost(2), GHOST_S],
      uLive: [time.get(), pixelRatio, 0, 0],
    };
  });

  const pagesStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -r.get() * L.w }] }));
  const barStyle = useAnimatedStyle(() => {
    const k = r.get();
    return { transform: [{ translateX: interpolate(k, [0, 1, 2], L.xs, 'extend') - 20 }, { scaleX: 1 + 0.9 * Math.sin(Math.PI * Math.max(0, Math.min(1, k - Math.floor(k)))) }] };
  });
  const fingerStyle = useAnimatedStyle(() => {
    const p = onPage.get() === 1;
    const x = p ? L.w * (0.78 - 0.42 * dragX.get()) : L.xs[fingerTab.get()];
    const y = p ? L.pagerTop + 150 : L.stageTop + LABEL_Y + 8;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    setWidth((v) => (v === w ? v : w));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />

        {/* search */}
        <View style={[styles.search, { top: L.searchTop }]}>
          <View style={styles.searchPill} accessible accessibilityRole="search" accessibilityLabel="Where to? Anywhere, any week, add guests">
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <Circle cx={7.5} cy={7.5} r={5.5} stroke={C.ink} strokeWidth={2} fill="none" />
              <SvgPath d="M11.6 11.6L16 16" stroke={C.ink} strokeWidth={2} strokeLinecap="round" />
            </Svg>
            <View>
              <Text style={styles.searchTitle}>Where to?</Text>
              <Text style={styles.searchSub}>Anywhere · Any week · Add guests</Text>
            </View>
          </View>
          <View style={styles.filter} accessible accessibilityRole="button" accessibilityLabel="Filters">
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <SvgPath d="M2 5h9M15 5h1M2 13h1M7 13h9" stroke={C.ink} strokeWidth={1.8} strokeLinecap="round" />
              <Circle cx={13} cy={5} r={2} stroke={C.ink} strokeWidth={1.8} fill="none" />
              <Circle cx={5} cy={13} r={2} stroke={C.ink} strokeWidth={1.8} fill="none" />
            </Svg>
          </View>
        </View>

        {/* the tabs */}
        <View style={[styles.stage, { top: L.stageTop, width: L.w }]} accessibilityRole="tablist">
          {active ? (
            <Canvas style={{ position: 'absolute', left: 0, top: 0, width: L.w, height: STAGE_H }} pointerEvents="none">
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
          {CATEGORIES.map((c, i) => (
            <Pressable
              key={c.name}
              onPress={() => tap(i)}
              style={[styles.tab, { left: L.xs[i] - L.slot / 2, width: L.slot }]}
              accessibilityRole="tab"
              accessibilityLabel={c.name}
              accessibilityState={{ selected: selected === i }}
            >
              <TabLabel name={c.name} index={i} r={r} />
            </Pressable>
          ))}
          <Animated.View style={[styles.bar, barStyle]} />
        </View>

        {/* the listings */}
        <GestureDetector gesture={page}>
          <View style={[styles.pager, { top: L.pagerTop }]}>
            <Animated.View style={[styles.pages, { width: L.w * CATEGORIES.length }, pagesStyle]}>
              {CATEGORIES.map((c, i) => (
                <View key={c.name} style={{ width: L.w, paddingHorizontal: 16 }} accessibilityElementsHidden={selected !== i} importantForAccessibility={selected === i ? 'auto' : 'no-hide-descendants'}>
                  <Text style={styles.heading} accessibilityRole="header">{c.heading}</Text>
                  {c.listings.map((l) => (
                    <ListingCard key={l.title} item={l} />
                  ))}
                </View>
              ))}
            </Animated.View>
          </View>
        </GestureDetector>

        {/* the map button floats over the listings */}
        <View style={[styles.map, { bottom: Math.max(insets.bottom, 16) + 8 }]} accessible accessibilityRole="button" accessibilityLabel="Show map">
          <Text style={styles.mapText}>Map</Text>
          <Svg width={16} height={16} viewBox="0 0 16 16">
            <SvgPath d="M1.5 3.5l4-1.5 5 2 4-1.5v10l-4 1.5-5-2-4 1.5z M5.5 2v10 M10.5 4v10" stroke={C.page} strokeWidth={1.5} strokeLinejoin="round" fill="none" />
          </Svg>
        </View>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

function TabLabel({ name, index, r }: { name: string; index: number; r: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(r.get() - index));
    return { color: interpolateColor(near, [0, 1], [C.mute, C.ink]) };
  });
  return <Animated.Text style={[styles.tabText, style]}>{name}</Animated.Text>;
}

function ListingCard({ item }: { item: Listing }) {
  return (
    <View style={styles.card} accessible accessibilityLabel={`${item.title}, ${item.meta}, ${item.when}, ${item.price} per ${item.unit}, rated ${item.rating}`}>
      <View style={styles.cardMain}>
        <Text style={styles.cardTitle} numberOfLines={1}>{item.title}</Text>
        <Text style={styles.cardMeta} numberOfLines={1}>{item.meta}</Text>
        <View style={styles.cardFoot}>
          <Text style={styles.cardWhen}>{item.when}</Text>
          <Text style={styles.cardDot}>·</Text>
          <Svg width={12} height={12} viewBox="0 0 12 12">
            <SvgPath d="M6 .8l1.6 3.3 3.6.5-2.6 2.5.6 3.6L6 9l-3.2 1.7.6-3.6L.8 4.6l3.6-.5z" fill={C.star} />
          </Svg>
          <Text style={styles.cardWhen}>{item.rating}</Text>
        </View>
      </View>
      <View style={styles.cardPrice}>
        <Text style={styles.price}>{item.price}</Text>
        <Text style={styles.unit}>{item.unit}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  search: { position: 'absolute', left: 16, right: 16, height: SEARCH_H, flexDirection: 'row', alignItems: 'center', gap: 12 },
  searchPill: { flex: 1, height: SEARCH_H, borderRadius: SEARCH_H / 2, backgroundColor: C.card, borderWidth: 1, borderColor: C.line, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, gap: 14 },
  searchTitle: { fontFamily: FONT.semibold, fontSize: 15, lineHeight: 20, color: C.ink },
  searchSub: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16, color: C.mute },
  filter: { width: 44, height: 44, borderRadius: 22, borderWidth: 1, borderColor: C.line, alignItems: 'center', justifyContent: 'center' },
  stage: { position: 'absolute', left: 0, height: STAGE_H, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: C.line },
  tab: { position: 'absolute', top: 0, height: STAGE_H, alignItems: 'center', paddingTop: LABEL_Y - 2 },
  tabText: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 20 },
  bar: { position: 'absolute', left: 0, top: STAGE_H - 3, width: 40, height: 3, borderRadius: 1.5, backgroundColor: C.ink },
  pager: { position: 'absolute', left: 0, right: 0, bottom: 0, overflow: 'hidden' },
  pages: { flexDirection: 'row', paddingTop: 20 },
  heading: { fontFamily: FONT.semibold, fontSize: 20, lineHeight: 26, color: C.ink, marginBottom: 12 },
  card: { flexDirection: 'row', alignItems: 'center', backgroundColor: C.card, borderRadius: 20, borderCurve: 'continuous', paddingHorizontal: 16, paddingVertical: 14, marginBottom: 12, gap: 12 },
  cardMain: { flex: 1, gap: 2 },
  cardTitle: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 22, color: C.ink },
  cardMeta: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 18, color: C.mute },
  cardFoot: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 4 },
  cardWhen: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, color: C.ink },
  cardDot: { fontFamily: FONT.regular, fontSize: 13, color: C.mute },
  cardPrice: { alignItems: 'flex-end' },
  price: { fontFamily: FONT.semibold, fontSize: 18, lineHeight: 24, color: C.ink, fontVariant: ['tabular-nums'] },
  unit: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16, color: C.mute },
  map: { position: 'absolute', alignSelf: 'center', height: 48, paddingHorizontal: 20, borderRadius: 24, backgroundColor: C.ink, flexDirection: 'row', alignItems: 'center', gap: 8, boxShadow: '0 6px 20px rgba(0,0,0,0.45)' },
  mapText: { fontFamily: FONT.semibold, fontSize: 15, color: C.page },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.4)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)', boxShadow: '0 1px 6px rgba(0,0,0,0.2)' },
});
