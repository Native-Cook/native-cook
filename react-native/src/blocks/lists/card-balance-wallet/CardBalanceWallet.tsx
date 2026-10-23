import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation, type FrameInfo, type SharedValue, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Card } from './Card';
import { PROFILE, SPENT } from './data';
import { ArrowGlyph, BellGlyph, ChartGlyph, ChatGlyph, GridGlyph, PlusGlyph, ScanGlyph, WalletGlyph } from './glyphs';
import { EASE_OUT, LIFT, LIGHT, PICKUP, pose, PRESS, RELEASE, RESUME_AFTER_MS, TILT } from './motion';
import { ACTIONS, CARD, COLOR, DOTS, FAMILY, FONT, HEADER, SCREEN, SPENT as SPENT_UI, TABBAR, TILES, topOffset } from './theme';

const FONTS = {
  [FAMILY.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
  [FAMILY.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
  [FAMILY.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
  [FAMILY.display]: require('./assets/fonts/InterDisplay-Bold.ttf'),
};
const RETAIN = { top: 16, bottom: 16, left: 16, right: 16 };

export type CardBalanceWalletProps = {
  autoPlay?: boolean;
  /** Host-owned actions for the decorative controls; no wallet backend is implied. */
  onAction?: (action: CardBalanceWalletAction) => void;
  /** Dev only: show the screen still, as the pin (any value), for pixel comparisons. */
  freezeAt?: number;
};

export type CardBalanceWalletAction =
  | 'notifications' | 'addMoney' | 'transfer' | 'scanToPay' | 'addCard'
  | 'overview' | 'insights' | 'messages';

function PressScale({ children, style, label, action, onAction }: {
  children: React.ReactNode; style: object; label: string;
  action: CardBalanceWalletAction; onAction?: (action: CardBalanceWalletAction) => void;
}) {
  const enabled = onAction !== undefined;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: !enabled }}
      disabled={!enabled} onPress={onAction ? () => onAction(action) : undefined}
      pressRetentionOffset={RETAIN} hitSlop={4}
      style={({ pressed }) => [style, { transform: [{ scale: pressed && enabled ? PRESS.scale : 1 }] }]}>
      {children}
    </Pressable>
  );
}

/** The card's shadow where it falls on the white buttons below it: darker and lower the higher the card. */
function CardShadow({ top, width, lift }: { top: number; width: number; lift: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const k = Math.min(1, Math.max(0, lift.get()));
    return { opacity: LIGHT.shadow * k, transform: [{ translateY: LIGHT.shadowDrop * k }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.cardShadow, { top, width }, style]}>
      <LinearGradient colors={['rgba(0,0,0,0.55)', 'rgba(0,0,0,0)']} style={StyleSheet.absoluteFill} />
    </Animated.View>
  );
}

/** The lit page dot stretches toward the side the card leans, the pin's own swipe hint. */
function ActiveDot({ ry }: { ry: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const extra = LIGHT.dotStretch * Math.min(1, Math.abs(ry.get()) / TILT.max);
    return { width: DOTS.active + extra, left: ry.get() < 0 ? -extra : 0 };
  });
  return <Animated.View style={[styles.dot, styles.dotLit, style]} />;
}

export function CardBalanceWallet({ autoPlay = false, freezeAt, onAction }: CardBalanceWalletProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const oy = topOffset(insets.top);
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts(FONTS);
  const cardW = width - SCREEN.inset * 2;
  const playScript = autoPlay && freezeAt === undefined;
  const canAutoPlay = playScript && !reduced;

  const rx = useSharedValue(0);
  const ry = useSharedValue(0);
  const lift = useSharedValue(0); // the card's height off the screen: -0.6 pressed, 1 held up
  const glow = useSharedValue(0); // Reduced Motion's in-place gleam
  // The loop's one clock (ms). Every animated value of the autoplay is pose(clock).
  const clock = useSharedValue(freezeAt ?? 0);
  const playing = useSharedValue(canAutoPlay);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearTimers = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
  }, []);

  const apply = useCallback((t: number) => {
    'worklet';
    const p = pose(t, reduced);
    rx.set(p.rx);
    ry.set(p.ry);
    lift.set(p.lift);
    glow.set(p.glow);
  }, [glow, lift, reduced, rx, ry]);

  const frameDriver = useFrameCallback(useCallback((frame: FrameInfo) => {
    'worklet';
    if (!playing.get()) return;
    clock.set(clock.get() + (frame.timeSincePreviousFrame ?? 0));
    apply(clock.get());
  }, [apply, clock, playing]), false);

  useEffect(() => {
    // The same screen can be reopened with or without a freeze: restart the clock from rest either way.
    clearTimers();
    frameDriver.setActive(canAutoPlay);
    clock.set(freezeAt ?? 0);
    playing.set(canAutoPlay);
    if (reduced) {
      for (const value of [rx, ry, lift, glow]) cancelAnimation(value);
    }
    apply(freezeAt ?? 0);
    return () => frameDriver.setActive(false);
  }, [apply, canAutoPlay, clearTimers, clock, frameDriver, freezeAt, glow, lift, playing, reduced, rx, ry]);

  useEffect(() => clearTimers, [clearTimers]);

  const onTouchStart = useCallback(() => {
    playing.set(false);
    glow.set(0);
    clearTimers();
  }, [clearTimers, glow, playing]);
  const onTouchEnd = useCallback(() => {
    if (!canAutoPlay) return;
    clearTimers();
    timers.current.push(setTimeout(() => {
      clock.set(0);
      playing.set(true);
    }, RESUME_AFTER_MS));
  }, [canAutoPlay, clearTimers, clock, playing]);

  // The card follows the finger 1:1 in tilt while it rests on it, and settles on a spring when it lifts.
  /* eslint-disable react-hooks/refs */
  const cardGesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin(e => {
      scheduleOnRN(onTouchStart);
      if (reduced) return;
      const nx = Math.min(1, Math.max(0, e.x / cardW)) - 0.5;
      const ny = Math.min(1, Math.max(0, e.y / CARD.height)) - 0.5;
      ry.set(withTiming(nx * 2 * TILT.max, { duration: TILT.followIn, easing: EASE_OUT }));
      rx.set(withTiming(-ny * 2 * TILT.max, { duration: TILT.followIn, easing: EASE_OUT }));
      // Pressed in, then picked up.
      lift.set(withSequence(
        withTiming(LIFT.press, { duration: LIFT.pressMs, easing: EASE_OUT }),
        withSpring(1, { duration: PICKUP.response * 1000, dampingRatio: PICKUP.damping }),
      ));
    })
    .onUpdate(e => {
      if (reduced) return;
      ry.set((Math.min(1, Math.max(0, e.x / cardW)) - 0.5) * 2 * TILT.max);
      rx.set(-(Math.min(1, Math.max(0, e.y / CARD.height)) - 0.5) * 2 * TILT.max);
    })
    .onFinalize(e => {
      if (reduced) {
        rx.set(0);
        ry.set(0);
        lift.set(0);
        scheduleOnRN(onTouchEnd);
        return;
      }
      const settle = { duration: RELEASE.response * 1000, dampingRatio: RELEASE.damping };
      rx.set(withSpring(0, { ...settle, velocity: -e.velocityY / CARD.height * 2 * TILT.max }));
      ry.set(withSpring(0, { ...settle, velocity: e.velocityX / cardW * 2 * TILT.max }));
      lift.set(withSpring(0, settle));
      scheduleOnRN(onTouchEnd);
    }), [cardW, lift, onTouchEnd, onTouchStart, reduced, rx, ry]);

  const watcher = useMemo(() => Gesture.Manual()
    .onTouchesDown(() => scheduleOnRN(onTouchStart))
    .onTouchesUp(e => {
      if (e.numberOfTouches === 0) scheduleOnRN(onTouchEnd);
    }), [onTouchEnd, onTouchStart]);
  /* eslint-enable react-hooks/refs */

  if (!fontsLoaded) return <View style={styles.screen} />;
  const tilesTop = oy + TILES.top;
  const spentW = width - SCREEN.inset * 2;
  const barW = spentW - CARD.padX * 2;
  const room = barW - SPENT_UI.barGap * 3;
  const barXs = SPENT.split.map((_, k) => SPENT.split.slice(0, k).reduce((sum, v) => sum + v * room + SPENT_UI.barGap, 0));

  return (
    <GestureHandlerRootView style={styles.screen}>
      <GestureDetector gesture={watcher}>
        <View style={styles.screen}>
          <StatusBar style="light" />
          <View style={[styles.header, { top: oy + HEADER.y - HEADER.avatar / 2, width }]}>
            <Image source={PROFILE.photo} style={styles.avatar} contentFit="cover" accessibilityLabel={PROFILE.name} />
            <Text style={styles.name}>{PROFILE.name}</Text>
            <PressScale label="Notifications, 1 new" action="notifications" onAction={onAction} style={styles.bell}>
              <BellGlyph color={COLOR.white} size={HEADER.bell + 2} />
              <View style={styles.bellDot} />
            </PressScale>
          </View>
          <Card width={width} oy={oy} gesture={cardGesture} rx={rx} ry={ry} lift={lift} glow={glow} reduced={reduced} />
          <View style={[styles.actions, { top: oy + ACTIONS.top, width: spentW }]}>
            <PressScale label="Add money" action="addMoney" onAction={onAction} style={styles.action}>
              <Text style={styles.actionText}>add money</Text>
              <PlusGlyph color={COLOR.ink} size={24} />
            </PressScale>
            <PressScale label="Transfer" action="transfer" onAction={onAction} style={styles.action}>
              <Text style={styles.actionText}>transfer</Text>
              <ArrowGlyph color={COLOR.ink} size={24} />
            </PressScale>
          </View>
          <CardShadow top={oy + ACTIONS.top} width={spentW} lift={lift} />
          <View style={[styles.dots, { top: oy + DOTS.y - DOTS.height / 2, left: DOTS.x }]}
            accessible accessibilityLabel="Card 1 of 3">
            <ActiveDot ry={ry} />
            <View style={[styles.dot, { left: DOTS.active + DOTS.gap, width: DOTS.dot }]} />
            <View style={[styles.dot, { left: DOTS.active + DOTS.dot + DOTS.gap * 2, width: DOTS.dot }]} />
          </View>
          <PressScale label="Scan to pay" action="scanToPay" onAction={onAction} style={[styles.tile, { top: tilesTop, left: SCREEN.inset }]}>
            <ScanGlyph color={COLOR.white} size={28} />
          </PressScale>
          <PressScale label="Add card" action="addCard" onAction={onAction} style={[styles.tile, { top: tilesTop, left: SCREEN.inset + TILES.size + TILES.gap }]}>
            <PlusGlyph color={COLOR.white} size={24} />
          </PressScale>
          <View style={[styles.spent, { top: oy + SPENT_UI.top, width: spentW }]}>
            <Text style={styles.spentTitle}>
              <Text style={styles.spentMuted}>Spent in </Text>{SPENT.month}
            </Text>
            <Text style={styles.spentAmount}>{SPENT.amount}</Text>
            <View style={[styles.bar, { width: barW }]}>
              {SPENT.split.map((share, k) => (
                <View key={k} style={[styles.segment, { left: barXs[k], width: share * room, backgroundColor: SPENT.colors[k] }]} />
              ))}
            </View>
          </View>
          <View style={[styles.tabBand, { top: oy + TABBAR.band, height: height - (oy + TABBAR.band), width }]} pointerEvents="box-none">
            <LinearGradient colors={['rgba(30,29,29,0.0)', 'rgba(30,29,29,0.92)']} locations={[0, 0.12]} style={StyleSheet.absoluteFill} />
            <View style={[styles.tabTrack, { width: width - TABBAR.trackX * 2 }]}>
              <View style={styles.tabPill} accessible accessibilityRole="tab" accessibilityState={{ selected: true }} accessibilityLabel="All cards">
                <WalletGlyph color={COLOR.accent} size={26} />
                <Text style={styles.tabText}>All cards</Text>
              </View>
              {[GridGlyph, ChartGlyph, ChatGlyph].map((G, i) => (
                <PressScale key={i} label={['Overview', 'Insights', 'Messages'][i]}
                  action={(['overview', 'insights', 'messages'] as const)[i]} onAction={onAction} style={[styles.tabCircle, { left: TABBAR.xs[i] - TABBAR.trackX - TABBAR.circle / 2 }]}>
                  <G color={COLOR.tabIcon} size={26} />
                </PressScale>
              ))}
            </View>
          </View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.screen },
  header: { position: 'absolute', left: 0, height: HEADER.avatar, flexDirection: 'row', alignItems: 'center', paddingHorizontal: SCREEN.inset },
  avatar: { width: HEADER.avatar, height: HEADER.avatar, borderRadius: HEADER.avatar / 2, backgroundColor: '#DEDEDE' },
  name: { marginLeft: HEADER.nameX - SCREEN.inset - HEADER.avatar, fontFamily: FAMILY.medium, fontSize: FONT.name, lineHeight: 20, color: COLOR.white },
  bell: { marginLeft: 'auto', width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginRight: -9 },
  bellDot: { position: 'absolute', top: 7, right: 9, width: 8, height: 8, borderRadius: 4, backgroundColor: COLOR.accent },
  actions: { position: 'absolute', left: SCREEN.inset, height: ACTIONS.height, flexDirection: 'row', gap: ACTIONS.gap },
  action: {
    flex: 1, borderRadius: ACTIONS.radius, backgroundColor: COLOR.white, flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between', paddingHorizontal: ACTIONS.padX, borderCurve: 'continuous',
  },
  actionText: { fontFamily: FAMILY.medium, fontSize: FONT.action, lineHeight: 22, color: COLOR.ink, letterSpacing: -0.1, marginTop: -6 },
  dots: { position: 'absolute', height: DOTS.height, width: 60 },
  cardShadow: {
    position: 'absolute', left: SCREEN.inset, height: 34, borderTopLeftRadius: ACTIONS.radius, borderTopRightRadius: ACTIONS.radius,
    overflow: 'hidden',
  },
  dot: { position: 'absolute', left: 0, top: 0, height: DOTS.height, borderRadius: DOTS.height / 2, backgroundColor: COLOR.dot },
  dotLit: { backgroundColor: COLOR.white },
  tile: {
    position: 'absolute', width: TILES.size, height: TILES.size, borderRadius: TILES.radius, backgroundColor: COLOR.tile,
    alignItems: 'center', justifyContent: 'center', borderCurve: 'continuous',
  },
  spent: { position: 'absolute', left: SCREEN.inset, height: 140, borderRadius: SPENT_UI.radius, backgroundColor: COLOR.tile, borderCurve: 'continuous' },
  spentTitle: { position: 'absolute', left: CARD.padX, top: SPENT_UI.titleY - 12, fontFamily: FAMILY.medium, fontSize: FONT.spentTitle, lineHeight: 24, color: COLOR.white },
  spentMuted: { color: COLOR.muted },
  spentAmount: {
    position: 'absolute', right: CARD.padX, top: SPENT_UI.titleY - 15, fontFamily: FAMILY.semibold, fontSize: FONT.spentAmount,
    lineHeight: 30, color: COLOR.white, letterSpacing: -0.3,
  },
  bar: { position: 'absolute', left: CARD.padX, top: SPENT_UI.barY - SPENT_UI.barHeight / 2, height: SPENT_UI.barHeight },
  segment: { position: 'absolute', top: 0, height: SPENT_UI.barHeight, borderRadius: SPENT_UI.barHeight / 2 },
  tabBand: { position: 'absolute', left: 0 },
  tabTrack: {
    position: 'absolute', left: TABBAR.trackX, top: TABBAR.trackTop - TABBAR.band, height: TABBAR.trackHeight,
    borderRadius: TABBAR.trackHeight / 2, backgroundColor: COLOR.tabTrack,
  },
  tabPill: {
    position: 'absolute', left: 5, top: (TABBAR.trackHeight - TABBAR.pillH) / 2, width: TABBAR.pillW, height: TABBAR.pillH,
    borderRadius: TABBAR.pillH / 2, backgroundColor: COLOR.white, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6,
  },
  tabText: { fontFamily: FAMILY.semibold, fontSize: FONT.tab, lineHeight: 20, color: COLOR.accent },
  tabCircle: {
    position: 'absolute', top: (TABBAR.trackHeight - TABBAR.circle) / 2, width: TABBAR.circle, height: TABBAR.circle,
    borderRadius: TABBAR.circle / 2, backgroundColor: COLOR.tabCircle, alignItems: 'center', justifyContent: 'center',
  },
});
