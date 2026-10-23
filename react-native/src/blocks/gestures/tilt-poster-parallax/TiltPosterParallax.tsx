/** A film's detail screen whose layered poster tilts with the phone and with a finger. */
import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, Group, Image, RadialGradient, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  interpolate, interpolateColor, ReduceMotion, useAnimatedStyle, useDerivedValue, useReducedMotion, useSharedValue, withSpring,
} from 'react-native-reanimated';
import { scheduleOnUI } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PARALLAX, SPRING } from './motion';
import { PosterCard } from './PosterCard';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import { usePosterTilt } from './use-poster-tilt';

export type TiltPosterParallaxProps = { autoPlay?: boolean };

// Room round the card for its turn, its shadow and its growth (half the extra width each side).
const MARGIN = 72;
const plateSource = require('./assets/poster-plate.jpg');
const backdropSource = require('./assets/backdrop.jpg');
const figureSource = require('./assets/poster-figure.png');
// The block's side faces, rendered in Blender (front edge at the left / top).
const edgeSideSource = require('./assets/edge-side.png');
const edgeTopSource = require('./assets/edge-top.png');

export function TiltPosterParallax({ autoPlay = false }: TiltPosterParallaxProps) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [saved, setSaved] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const plate = useImage(plateSource);
  const backdrop = useImage(backdropSource);
  const figure = useImage(figureSource);
  const edgeSide = useImage(edgeSideSource);
  const edgeTop = useImage(edgeTopSource);
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((p) => (p.width === width && p.height === height ? p : { width, height }));
  }, []);
  const cardW = Math.round(size.width * LAYOUT.cardWidthRatio);
  const cardH = Math.round(cardW * LAYOUT.cardAspect);
  const ready = size.width > 0 && plate !== null && figure !== null && backdrop !== null;
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
  const scriptWatch = useCallback(() => setSaved((s) => !s), []);
  const { tiltX, tiltY, lift, flip, clock, finger, gesture, toggleFlip } = usePosterTilt({
    half: { x: cardW / 2, y: cardH / 2 }, ready, autoPlay, reduceMotion, watchPoint, onScriptWatch: scriptWatch,
  });

  // Watchlist: the pill fills with the poster's gold, the plus turns into a tick and the label says so.
  const savedV = useSharedValue(0);
  const press = useSharedValue(1);
  useEffect(() => {
    savedV.set(withSpring(saved ? 1 : 0, { ...SPRING.morph, reduceMotion: ReduceMotion.System }));
  }, [saved, savedV]);
  const pillStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(savedV.get(), [0, 1], [COLOR.pill, COLOR.pillSaved]),
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

  // The text block rides down as the card grows, as in the source.
  const infoShift = useAnimatedStyle(() => ({ transform: [{ translateY: (lift.get() - 1) * cardH }] }));
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

  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style="light" />
    <View style={styles.root} onLayout={onLayout}>
      {ready && <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group transform={backdropShift}>
          {/* As in the source: a softened, desaturated concert photo behind the top half, fading to black. */}
          <Image image={backdrop} x={-bleed} y={-bleed} width={size.width + bleed * 2} height={size.width * LAYOUT.backdropAspect + bleed} fit="cover" />
        </Group>
        <Circle cx={0} cy={0} r={LAYOUT.stageLight}>
          <RadialGradient c={vec(0, 0)} r={LAYOUT.stageLight} colors={[COLOR.stageLight, 'rgba(140,168,235,0)']} />
        </Circle>
      </Canvas>}
      {/* The source's backdrop falls off to the right, away from the stage light. */}
      <LinearGradient colors={[...COLOR.backdropSide]} start={{ x: 0.2, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} pointerEvents="none" />
      <LinearGradient colors={[...COLOR.backdropFade]} locations={[0, 0.4, 0.62]} style={StyleSheet.absoluteFill} pointerEvents="none" />
      <View style={[styles.topBar, { top: insets.top + LAYOUT.topBar }]}>
        <Pressable style={styles.close} accessibilityRole="button" accessibilityLabel="Close" hitSlop={6}>
          <Ionicons name="close" size={20} color={COLOR.ink} />
        </Pressable>
        <View style={styles.award} accessible accessibilityLabel="1 nomination, 2 wins">
          <Ionicons name="trophy" size={13} color={COLOR.trophy} />
          <Text style={TYPE.award}>
            <Text style={TYPE.awardStrong}>{COPY.award[0]}</Text>{COPY.award[1]}<Text style={TYPE.awardStrong}>{COPY.award[2]}</Text>{COPY.award[3]}
          </Text>
        </View>
        <Pressable style={styles.close} accessibilityRole="button" accessibilityLabel="More options" hitSlop={6}>
          <Ionicons name="ellipsis-horizontal" size={18} color={COLOR.ink} />
        </Pressable>
      </View>
      {ready && <View style={[styles.cardSlot, { top: insets.top + LAYOUT.cardTop - MARGIN, left: (size.width - cardW) / 2 - MARGIN }]}>
        <PosterCard width={cardW} height={cardH} plate={plate} figure={figure} tiltX={tiltX} tiltY={tiltY} lift={lift} flip={flip} clock={clock} reduceMotion={reduceMotion} margin={MARGIN} title={COPY.title} edgeSide={edgeSide} edgeTop={edgeTop} />
        <GestureDetector gesture={gesture}>
          <View style={[styles.cardHit, { left: MARGIN, top: MARGIN, width: cardW, height: cardH }]} accessible accessibilityRole="image"
            accessibilityLabel={COPY.posterLabel} accessibilityHint="Tilt your phone or drag the poster to look around it. Double-tap to turn it over."
            accessibilityActions={[{ name: 'activate', label: 'Turn over' }]} onAccessibilityAction={onPosterAction} />
        </GestureDetector>
      </View>}
      <Animated.View style={[styles.info, { top: insets.top + LAYOUT.cardTop + cardH + LAYOUT.titleGap }, infoShift]}>
        <Text style={TYPE.title} accessibilityRole="header">{COPY.title}</Text>
        <Text style={TYPE.date}>{COPY.date}</Text>
        <View style={styles.pills} onLayout={onPillsLayout}>
          <Pressable onPress={toggleSaved} onPressIn={pressIn} onPressOut={pressOut} style={styles.pillSlot} accessibilityRole="button"
            accessibilityLabel={saved ? 'In your watchlist' : 'Add to watchlist'} accessibilityState={{ selected: saved }}>
            <Animated.View style={[styles.pill, pillStyle]}>
              <View style={styles.icon}>
                <Animated.View style={[styles.iconLayer, plusStyle]}><Ionicons name="add" size={18} color={COLOR.ink} /></Animated.View>
                <Animated.View style={[styles.iconLayer, tickStyle]}><Ionicons name="checkmark" size={18} color={COLOR.goldInk} /></Animated.View>
              </View>
              <Text style={[TYPE.pill, saved && { color: COLOR.goldInk }]}>{saved ? COPY.added : COPY.watchlist}</Text>
            </Animated.View>
          </Pressable>
          <Pressable style={({ pressed }) => [styles.pill, styles.pillGhost, pressed && styles.pressed]} accessibilityRole="button" accessibilityLabel="Mark as watched">
            <Ionicons name="eye-outline" size={18} color={COLOR.inkGhost} />
            <Text style={[TYPE.pill, { color: COLOR.inkGhost }]}>{COPY.watched}</Text>
          </Pressable>
        </View>
        <Text style={TYPE.body} numberOfLines={expanded ? undefined : 3}>{COPY.body}</Text>
        <Pressable onPress={() => setExpanded((e) => !e)} style={styles.more} accessibilityRole="button" hitSlop={8}
          accessibilityLabel={expanded ? COPY.less : COPY.more}>
          <Text style={TYPE.more}>{expanded ? COPY.less : COPY.more}</Text>
          <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={14} color={COLOR.inkFaint} />
        </Pressable>
      </Animated.View>
      {autoPlay && ready && <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group opacity={fingerAlpha}>
          <Circle cx={fingerX} cy={fingerY} r={LAYOUT.fingerRadius} color={COLOR.finger} />
          <Circle cx={fingerX} cy={fingerY} r={LAYOUT.fingerRadius} color={COLOR.fingerRing} style="stroke" strokeWidth={1.5} />
        </Group>
      </Canvas>}
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bg },
  topBar: { position: 'absolute', left: LAYOUT.gutter - 8, right: LAYOUT.gutter - 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  close: { width: LAYOUT.close, height: LAYOUT.close, borderRadius: LAYOUT.close / 2, backgroundColor: COLOR.glass, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.glassBorder, alignItems: 'center', justifyContent: 'center' },
  award: { height: LAYOUT.awardHeight, paddingHorizontal: 12, borderRadius: LAYOUT.awardHeight / 2, flexDirection: 'row', alignItems: 'center', gap: 6 },
  cardSlot: { position: 'absolute' },
  cardHit: { position: 'absolute' },
  info: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, alignItems: 'center', gap: 4 },
  pills: { alignSelf: 'stretch', flexDirection: 'row', gap: LAYOUT.pillGap, marginTop: LAYOUT.bodyGap - 1, marginBottom: LAYOUT.bodyGap + 1 },
  pill: { flex: 1, justifyContent: 'center', height: LAYOUT.pillHeight, paddingHorizontal: 20, borderRadius: LAYOUT.pillHeight / 2, backgroundColor: COLOR.pill, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.glassBorder, flexDirection: 'row', alignItems: 'center', gap: 8 },
  pillSlot: { flex: 1 },
  icon: { width: 18, height: 18 },
  iconLayer: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  pillGhost: { backgroundColor: COLOR.pillGhost, borderColor: 'transparent' },
  pressed: { opacity: 0.7 },
  more: { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', marginTop: 0 },
});
