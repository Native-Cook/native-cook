/**
 * Envelope Gift Reveal: swipe up on the envelope (or press Open) — the flap folds back, the card
 * follows the finger out, flies into place and the page turns from lime to white under confetti.
 */
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { Easing, ReduceMotion, cubicBezier, useAnimatedStyle, useReducedMotion, withDelay, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import { EnvelopeScene, frameOf } from './EnvelopeScene';
import { HEADLINE, POLISH, REDUCED_MS, RESET, REVEAL } from './motion';
import { COLORS, DISPLAY_FONT, GIFT, LAYOUT, screenLayout } from './theme';
import { PHASE, useEnvelope, type EnvelopeValues, type Haptic } from './use-envelope';

export type EnvelopeGiftRevealProps = { autoPlay?: boolean; onShop?: () => void };

const [e1, e2, e3, e4] = POLISH.easeOut;
const TEXT_EASE = Easing.bezier(e1, e2, e3, e4);
const PRESS_CURVE = cubicBezier(e1, e2, e3, e4);
/** Feedback on press-in: scale 0.96 in 100 ms, back in 160 ms; a transition, so a quick tap retargets. */
const pressStyle = (pressed: boolean) => ({
  transform: [{ scale: pressed ? POLISH.pressScale : 1 }],
  transitionProperty: 'transform' as const, transitionDuration: pressed ? POLISH.pressInMs : POLISH.pressOutMs, transitionTimingFunction: PRESS_CURVE,
});

const HAPTIC: Record<Haptic, () => void> = {
  flap: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); },
  crack: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid); },
  commit: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); },
  success: () => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
  light: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft); },
};

const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};

export function EnvelopeGiftReveal({ autoPlay = false, onShop }: EnvelopeGiftRevealProps) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  return <Screen key={`${width}x${height}-${reduced}`} width={width} height={height} reduced={reduced} autoPlay={autoPlay} onShop={onShop} />;
}

/** Headline letters leave one after another, sliding down as the flap starts to lift (wide type, staggered). */
function Letter({ ch, order, v, reduced }: { ch: string; order: number; v: EnvelopeValues; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const f = frameOf(v, reduced);
    if (f.reset > 0) return { opacity: f.reset, transform: [{ translateY: 0 }] };
    const u = reduced ? clamp01(f.p / 0.08) : clamp01((f.p - order * HEADLINE.stagger) / HEADLINE.span);
    const e = u * u * (3 - 2 * u);
    return { opacity: 1 - e, transform: [{ translateY: HEADLINE.drop * e }] };
  });
  return <Animated.Text style={[styles.headline, style]}>{ch}</Animated.Text>;
}

function Screen({ width, height, reduced, autoPlay, onShop }: { width: number; height: number; reduced: boolean; autoPlay: boolean; onShop?: () => void }) {
  const insets = useSafeAreaInsets();
  // The headlines wait for the wide face, so they never flash in the fallback font.
  const [fontReady] = useFonts({ [DISPLAY_FONT]: require('./assets/ArchivoExpanded-Black.ttf') });
  const [phase, setPhase] = useState<number>(PHASE.sealed);
  const onHaptic = useCallback((k: Haptic) => HAPTIC[k](), []);
  const lay = screenLayout(height, insets.top, insets.bottom);
  const finalCardY = lay.cardTop;
  const { values: v, input } = useEnvelope(width, finalCardY + LAYOUT.cardH / 2, lay.envTop, autoPlay, reduced, { onPhase: setPhase, onHaptic });

  const pan = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((e) => { input.takeOver(); input.down(e.absoluteX, e.absoluteY, false); })
    .onUpdate((e) => { input.move(e.absoluteX, e.absoluteY); })
    .onFinalize((e) => { input.up(e.velocityY); input.letGo(); }), [input]);
  const ui = useCallback((fn: () => void) => scheduleOnUI(() => {
    'worklet';
    input.takeOver();
    fn();
    input.letGo();
  }), [input]);

  const buttonStyle = useAnimatedStyle(() => {
    const f = frameOf(v, reduced);
    const leaving = f.reset > 0 ? 1 - f.reset : clamp01(f.p / 0.06);
    return { opacity: 1 - leaving, transform: [{ scale: 1 - 0.08 * leaving }] };
  });
  // The final screen fades in on the phase change (a clock-driven style here stopped updating after a re-render).
  const showFinal = phase === PHASE.revealing || phase === PHASE.revealed;
  const finalStyle = (k: number) => {
    'worklet';
    const delay = showFinal && !reduced ? 400 + REVEAL.textDelay + k * REVEAL.stagger : 0;
    const timing = { duration: showFinal ? 320 : reduced ? REDUCED_MS : RESET.fadeMs, easing: TEXT_EASE, reduceMotion: ReduceMotion.Never };
    return {
      opacity: withDelay(delay, withTiming(showFinal ? 1 : 0, timing)),
      transform: [{ translateY: withDelay(delay, withTiming(showFinal || reduced ? 0 : POLISH.textRise, timing)) }],
    };
  };
  const final0 = useAnimatedStyle(() => finalStyle(0), [showFinal]);
  const final1 = useAnimatedStyle(() => finalStyle(1), [showFinal]);
  const final2 = useAnimatedStyle(() => finalStyle(2), [showFinal]);
  const sealed = phase === PHASE.sealed || phase === PHASE.dragging;
  const revealed = phase === PHASE.revealed;

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={pan}>
        <View style={styles.root} collapsable={false}>
          <EnvelopeScene v={v} width={width} height={height} reduced={reduced} lay={lay} />
          <View pointerEvents="none" accessible={sealed} accessibilityRole="button" accessibilityLabel="Gift envelope"
            accessibilityHint="Swipe up to open it, or double tap" onAccessibilityTap={() => ui(() => { 'worklet'; input.open(); })}
            style={[styles.envA11y, { left: (width - LAYOUT.envW) / 2, top: lay.envTop }]} />
        </View>
      </GestureDetector>

      {fontReady && (
        <View pointerEvents="none" style={[styles.headlineBox, { top: lay.headlineTop }]} accessible accessibilityRole="header"
          accessibilityLabel={GIFT.headline.replace('\n', ' ')}>
          {GIFT.headline.split('\n').map((line, row) => (
            <View key={row} style={styles.headlineLine}>
              {line.split('').map((ch, i) => <Letter key={i} ch={ch} order={row * 8 + i} v={v} reduced={reduced} />)}
            </View>
          ))}
        </View>
      )}
      <Animated.View style={[styles.openWrap, { top: lay.buttonY - LAYOUT.buttonD / 2 }, buttonStyle]} pointerEvents={sealed ? 'box-none' : 'none'}>
        <Pressable onPress={() => ui(() => { 'worklet'; input.open(); })} pressRetentionOffset={16}
          accessibilityRole="button" accessibilityLabel="Open the gift">
          {({ pressed }) => <Animated.View style={[styles.open, pressStyle(pressed)]}>
            <Text style={styles.openText}>OPEN</Text>
          </Animated.View>}
        </Pressable>
      </Animated.View>

      {fontReady && <Animated.Text pointerEvents="none" style={[styles.title, { top: lay.titleTop }, final0]} accessibilityRole="header">
        {GIFT.title}
      </Animated.Text>}
      <Animated.View pointerEvents="none" style={[styles.message, { top: lay.messageTop }, final1]}
        accessible={revealed} accessibilityLabel={`Gift card, ${GIFT.amount}. ${GIFT.message}`}>
        <Text style={styles.messageText}>{GIFT.message}</Text>
      </Animated.View>
      <Animated.View style={[styles.ctaWrap, { top: lay.ctaTop }, final2]} pointerEvents={revealed ? 'box-none' : 'none'}>
        <Pressable onPress={() => (onShop ? onShop() : ui(() => { 'worklet'; input.reset(); }))} pressRetentionOffset={16}
          accessibilityRole="button" accessibilityLabel={GIFT.cta}>
          {({ pressed }) => <Animated.View style={[styles.cta, pressStyle(pressed)]}>
            <Text style={styles.ctaText}>{GIFT.cta.toUpperCase()}</Text>
          </Animated.View>}
        </Pressable>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.lime },
  envA11y: { position: 'absolute', width: LAYOUT.envW, height: LAYOUT.envH },
  headlineBox: { position: 'absolute', left: LAYOUT.finalSide, right: 8 },
  headlineLine: { flexDirection: 'row' },
  headline: {
    color: COLORS.ink, fontSize: LAYOUT.headline, lineHeight: LAYOUT.headlineLine, fontFamily: DISPLAY_FONT, letterSpacing: -1,
  },
  openWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  open: {
    width: LAYOUT.buttonD, height: LAYOUT.buttonD, borderRadius: LAYOUT.buttonD / 2, backgroundColor: COLORS.ink,
    alignItems: 'center', justifyContent: 'center', shadowColor: '#3A4A00', shadowOpacity: 0.35, shadowRadius: 22, shadowOffset: { width: 0, height: 14 },
  },
  openText: { color: COLORS.white, fontSize: 13, fontWeight: '700', letterSpacing: 1.8 },
  title: {
    position: 'absolute', left: LAYOUT.finalSide, right: 8, color: COLORS.ink, fontSize: LAYOUT.title,
    lineHeight: LAYOUT.titleLine, fontFamily: DISPLAY_FONT, letterSpacing: -0.5,
  },
  message: {
    position: 'absolute', left: LAYOUT.finalSide, right: LAYOUT.finalSide, height: LAYOUT.messageH, borderRadius: 4,
    backgroundColor: COLORS.message, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20,
    shadowColor: '#000', shadowOpacity: 0.08, shadowRadius: 18, shadowOffset: { width: 0, height: 8 }, elevation: 3,
  },
  messageText: { color: COLORS.messageInk, fontSize: 16, textAlign: 'center' },
  ctaWrap: { position: 'absolute', left: LAYOUT.finalSide, right: LAYOUT.finalSide },
  cta: { height: LAYOUT.buttonH, backgroundColor: COLORS.ink, alignItems: 'center', justifyContent: 'center' },
  ctaText: { color: COLORS.white, fontSize: 13, fontWeight: '700', letterSpacing: 1.2 },
});
