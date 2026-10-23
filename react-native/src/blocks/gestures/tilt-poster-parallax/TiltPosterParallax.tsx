/**
 * A film's page in a streaming app whose layered poster tilts with the phone and with a finger. The poster is drawn
 * in code at mount (poster-art.ts): five depths that slide against each other, an embossed foil title over them; the
 * same poster, blurred, is the page's backdrop. Hold the card and it rises; tap it and it turns over to its credits.
 */
import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, Group, Image } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PixelRatio, Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  interpolate, interpolateColor, ReduceMotion, useAnimatedStyle, useDerivedValue, useReducedMotion, useSharedValue, withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import { PARALLAX, SPRING } from './motion';
import { makePosterArt } from './poster-art';
import { PosterCard } from './PosterCard';
import { COLOR, COPY, FONT, LAYOUT, SPACE, TYPE } from './theme';
import { usePosterTilt } from './use-poster-tilt';

export type TiltPosterParallaxProps = { autoPlay?: boolean };

// Room round the card for its turn, its shadow and its growth.
const MARGIN = 72;

export function TiltPosterParallax({ autoPlay = false }: TiltPosterParallaxProps) {
  const [loaded, failed] = useFonts({
    [FONT.serif]: require('./assets/InstrumentSerif-Regular.ttf'),
    [FONT.regular]: require('./assets/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/Inter-SemiBold.ttf'),
  });
  if (!loaded && !failed) return <View style={styles.root} />;
  return <Screen autoPlay={autoPlay} />;
}

function Screen({ autoPlay }: { autoPlay: boolean }) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [saved, setSaved] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((p) => (p.width === width && p.height === height ? p : { width, height }));
  }, []);
  const cardW = Math.round(size.width * LAYOUT.cardWidthRatio);
  const cardH = Math.round(cardW * LAYOUT.cardAspect);
  const art = useMemo(() => (cardW > 0 ? makePosterArt(cardW, cardH, PixelRatio.get()) : null), [cardW, cardH]);
  const ready = size.width > 0 && art !== null;
  const [pills, setPills] = useState({ x: 0, y: 0, width: 0 });
  const onPillsLayout = useCallback((e: LayoutChangeEvent) => {
    const { x, y, width } = e.nativeEvent.layout;
    setPills((p) => (p.x === x && p.y === y && p.width === width ? p : { x, y, width }));
  }, []);
  // The Watchlist button's centre in the card's coordinates, for the scripted finger.
  const watchPoint = useMemo(() => ({
    x: LAYOUT.gutter + pills.x + (pills.width - LAYOUT.pillGap) / 4 - (size.width - cardW) / 2,
    y: cardH + LAYOUT.titleGap + pills.y + LAYOUT.pillHeight / 2,
  }), [cardH, cardW, pills, size.width]);
  const scriptWatch = useCallback((on: boolean) => setSaved(on), []);
  const onResume = useCallback(() => setSaved(false), []);
  const { tiltX, tiltY, lift, flip, clock, finger, gesture, toggleFlip } = usePosterTilt({
    half: { x: cardW / 2, y: cardH / 2 }, ready, autoPlay: autoPlay && !reduceMotion, reduceMotion, watchPoint, onScriptWatch: scriptWatch, onResume,
  });

  // Watchlist: the pill fills with the poster's gold, the plus turns into a tick and the label says so.
  const savedV = useSharedValue(0);
  const press = useSharedValue(1);
  useEffect(() => {
    savedV.set(withSpring(saved ? 1 : 0, { ...SPRING.morph, reduceMotion: ReduceMotion.System }));
  }, [saved, savedV]);
  const pillStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(savedV.get(), [0, 1], [COLOR.glass, COLOR.pillSaved]),
    borderColor: interpolateColor(savedV.get(), [0, 1], [COLOR.glassBorder, COLOR.pillSavedBorder]),
    transform: [{ scale: press.get() * (1 + Math.sin(Math.min(1, Math.max(0, savedV.get())) * Math.PI) * 0.04) }],
  }));
  const plusStyle = useAnimatedStyle(() => ({
    opacity: 1 - savedV.get(),
    transform: [{ rotate: `${savedV.get() * 90}deg` }, { scale: interpolate(savedV.get(), [0, 1], [1, 0.5]) }],
  }));
  const tickStyle = useAnimatedStyle(() => ({
    opacity: savedV.get(),
    transform: [{ rotate: `${(savedV.get() - 1) * 90}deg` }, { scale: interpolate(savedV.get(), [0, 1], [0.5, 1]) }],
  }));
  const pressIn = useCallback(() => { press.set(withSpring(0.97, { ...SPRING.press, reduceMotion: ReduceMotion.System })); }, [press]);
  const pressOut = useCallback(() => { press.set(withSpring(1, { ...SPRING.press, reduceMotion: ReduceMotion.System })); }, [press]);
  const onPosterAction = useCallback(() => { scheduleOnUI(toggleFlip); }, [toggleFlip]);

  const backdropShift = useDerivedValue(() => [
    { translateX: tiltX.get() * PARALLAX.backdrop },
    { translateY: tiltY.get() * PARALLAX.backdrop },
  ]);
  const fingerX = useDerivedValue(() => finger.get().x + (size.width - cardW) / 2);
  const fingerY = useDerivedValue(() => finger.get().y + insets.top + LAYOUT.cardTop);
  const fingerAlpha = useDerivedValue(() => finger.get().alpha);
  const toggleSaved = useCallback(() => {
    if (saved) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    setSaved((s) => !s);
  }, [saved]);
  const bleed = 60;
  const backH = size.height * LAYOUT.backdropShare;

  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style="light" />
    <View style={styles.root} onLayout={onLayout}>
      {ready && art && <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group transform={backdropShift}>
          {/* The poster itself, blurred, behind the top of the page: its dusk is the page's light. */}
          <Image image={art.backdrop} x={-bleed} y={-bleed} width={size.width + bleed * 2} height={backH + bleed * 2} fit="cover" opacity={0.85} />
        </Group>
      </Canvas>}
      <LinearGradient colors={[...COLOR.backdropFade]} locations={[0, 0.35, LAYOUT.backdropShare]} style={StyleSheet.absoluteFill} pointerEvents="none" />
      <View style={[styles.topBar, { top: insets.top + LAYOUT.topBar }]}>
        <Pressable style={styles.round} accessibilityRole="button" accessibilityLabel="Close" hitSlop={6}>
          <BlurView intensity={30} tint="dark" style={StyleSheet.absoluteFill} />
          <Ionicons name="close" size={20} color={COLOR.ink} />
        </Pressable>
        <View style={styles.award} accessible accessibilityLabel="3 festival awards">
          <BlurView intensity={30} tint="dark" style={StyleSheet.absoluteFill} />
          <Ionicons name="trophy-outline" size={14} color={COLOR.trophy} />
          <Text style={TYPE.award}><Text style={TYPE.awardStrong}>{COPY.award[0]}</Text>{COPY.award[1]}</Text>
        </View>
        <Pressable style={styles.round} accessibilityRole="button" accessibilityLabel="Share" hitSlop={6}>
          <BlurView intensity={30} tint="dark" style={StyleSheet.absoluteFill} />
          <Ionicons name="share-outline" size={19} color={COLOR.ink} />
        </Pressable>
      </View>
      {ready && art && <View style={[styles.cardSlot, { top: insets.top + LAYOUT.cardTop - MARGIN, left: (size.width - cardW) / 2 - MARGIN }]}>
        <PosterCard width={cardW} height={cardH} layers={art.layers} tiltX={tiltX} tiltY={tiltY} lift={lift} flip={flip} clock={clock} reduceMotion={reduceMotion} margin={MARGIN} />
        <GestureDetector gesture={gesture}>
          <View style={[styles.cardHit, { left: MARGIN, top: MARGIN, width: cardW, height: cardH }]} accessible accessibilityRole="image"
            accessibilityLabel={COPY.posterLabel} accessibilityHint="Tilt your phone or drag the poster to look around it. Double-tap to turn it over."
            accessibilityActions={[{ name: 'activate', label: 'Turn over' }]} onAccessibilityAction={onPosterAction} />
        </GestureDetector>
      </View>}
      {/* Fixed under the poster's slot: tilting, lifting or turning the poster never moves the page's content. */}
      <View style={[styles.info, { top: insets.top + LAYOUT.cardTop + cardH + LAYOUT.titleGap }]}>
        <Text style={TYPE.title} accessibilityRole="header">{COPY.title}</Text>
        <Text style={TYPE.meta}>{COPY.meta}</Text>
        <View style={styles.pills} onLayout={onPillsLayout}>
          <Pressable onPress={toggleSaved} onPressIn={pressIn} onPressOut={pressOut} style={styles.pillSlot} accessibilityRole="button"
            accessibilityLabel={saved ? 'In your watchlist' : 'Add to watchlist'} accessibilityState={{ selected: saved }}>
            <Animated.View style={[styles.pill, pillStyle]}>
              <View style={styles.icon}>
                <Animated.View style={[styles.iconLayer, plusStyle]}><Ionicons name="add" size={20} color={COLOR.ink} /></Animated.View>
                <Animated.View style={[styles.iconLayer, tickStyle]}><Ionicons name="checkmark" size={20} color={COLOR.goldInk} /></Animated.View>
              </View>
              <Text style={[TYPE.pill, saved && { color: COLOR.goldInk }]}>{saved ? COPY.added : COPY.watchlist}</Text>
            </Animated.View>
          </Pressable>
          <Pressable style={({ pressed }) => [styles.pill, styles.play, pressed && styles.pressed]} accessibilityRole="button" accessibilityLabel={COPY.play}>
            <Ionicons name="play" size={16} color={COLOR.playInk} />
            <Text style={[TYPE.pill, { color: COLOR.playInk }]}>{COPY.play}</Text>
          </Pressable>
        </View>
        <Text style={TYPE.body} numberOfLines={expanded ? undefined : 3}>{COPY.body}</Text>
        <Pressable onPress={() => setExpanded((e) => !e)} style={styles.more} accessibilityRole="button" hitSlop={8}
          accessibilityLabel={expanded ? COPY.less : COPY.more}>
          <Text style={TYPE.more}>{expanded ? COPY.less : COPY.more}</Text>
          <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={14} color={COLOR.ink} />
        </Pressable>
        {/* How it can be watched: small outlined chips, as a streaming page lists its formats. */}
        <View style={styles.formats} accessible accessibilityLabel="Available in 4K, HDR, Dolby Atmos, with audio description">
          {COPY.formats.map((f) => <View key={f} style={styles.chip}><Text style={TYPE.chip}>{f}</Text></View>)}
        </View>
      </View>
      {autoPlay && ready && <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group opacity={fingerAlpha}>
          <Circle cx={fingerX} cy={fingerY} r={LAYOUT.fingerRadius} color={COLOR.finger} />
          <Circle cx={fingerX} cy={fingerY} r={LAYOUT.fingerRadius - 0.5} color={COLOR.fingerRing} style="stroke" strokeWidth={1} />
        </Group>
      </Canvas>}
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bg },
  topBar: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, height: LAYOUT.close, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  round: {
    width: LAYOUT.close, height: LAYOUT.close, borderRadius: LAYOUT.close / 2, overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.glassBorder, alignItems: 'center', justifyContent: 'center',
  },
  award: {
    height: LAYOUT.awardHeight, paddingHorizontal: SPACE.m, borderRadius: LAYOUT.awardHeight / 2, overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.glassBorder, flexDirection: 'row', alignItems: 'center', gap: 6,
  },
  cardSlot: { position: 'absolute' },
  cardHit: { position: 'absolute' },
  info: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, alignItems: 'center', gap: SPACE.xs },
  pills: { alignSelf: 'stretch', flexDirection: 'row', gap: LAYOUT.pillGap, marginTop: SPACE.l, marginBottom: SPACE.l },
  pill: {
    flex: 1, justifyContent: 'center', height: LAYOUT.pillHeight, paddingHorizontal: SPACE.l, borderRadius: LAYOUT.pillHeight / 2,
    backgroundColor: COLOR.glass, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.glassBorder, flexDirection: 'row', alignItems: 'center', gap: SPACE.s,
  },
  pillSlot: { flex: 1 },
  play: { backgroundColor: COLOR.play, borderColor: 'transparent' },
  icon: { width: 20, height: 20 },
  iconLayer: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.8 },
  more: { flexDirection: 'row', alignItems: 'center', gap: SPACE.xs, alignSelf: 'flex-start', marginTop: SPACE.xs },
  formats: { alignSelf: 'stretch', flexDirection: 'row', gap: SPACE.s, marginTop: SPACE.l, paddingTop: SPACE.l, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: COLOR.glassBorder },
  chip: { height: 24, paddingHorizontal: SPACE.s, borderRadius: 6, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.28)', justifyContent: 'center' },
});
