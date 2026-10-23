/**
 * Envelope Gift Reveal: a letter from Nora on a linen table. Swipe up on the envelope (or press "Lift the flap"): the
 * wax seal cracks, the flap folds back in perspective, the card follows the finger out, lands, and turns over to her
 * note in her own hand.
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
import { COLORS, FONTS, GIFT, LAYOUT, screenLayout } from './theme';
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

/**
 * Headline letters leave one after another, sliding down as the flap starts to lift (wide type, staggered). On the
 * way back they wait for the revealed screen's title to be gone, then rise in the same order (reverse of the exit).
 */
function Letter({ ch, order, v, reduced }: { ch: string; order: number; v: EnvelopeValues; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const f = frameOf(v, reduced);
    if (f.reset > 0) {
      if (reduced) return { opacity: f.resetIn, transform: [{ translateY: 0 }] };
      const start = RESET.inFrom * RESET.fadeMs + order * RESET.letterStagger;
      const u = clamp01((f.reset * RESET.fadeMs - start) / RESET.letterMs);
      const e = 1 - Math.pow(1 - u, 3);
      return { opacity: e, transform: [{ translateY: HEADLINE.drop * (1 - e) }] };
    }
    const u = reduced ? clamp01(f.p / 0.08) : clamp01((f.p - order * HEADLINE.stagger) / HEADLINE.span);
    const e = u * u * (3 - 2 * u);
    return { opacity: 1 - e, transform: [{ translateY: HEADLINE.drop * e }] };
  });
  return <Animated.Text style={[styles.headline, style]}>{ch}</Animated.Text>;
}

function Screen({ width, height, reduced, autoPlay, onShop }: { width: number; height: number; reduced: boolean; autoPlay: boolean; onShop?: () => void }) {
  const insets = useSafeAreaInsets();
  // The headlines wait for the wide face, so they never flash in the fallback font.
  const [fontReady] = useFonts({
    [FONTS.serif]: require('./assets/InstrumentSerif-Regular.ttf'),
    [FONTS.ui]: require('./assets/Inter-Medium.ttf'),
    [FONTS.uiBold]: require('./assets/Inter-SemiBold.ttf'),
  });
  const [phase, setPhase] = useState<number>(PHASE.sealed);
  const onHaptic = useCallback((k: Haptic) => HAPTIC[k](), []);
  const lay = screenLayout(height, insets.top, insets.bottom);
  const finalCardY = lay.cardTop;
  // Reduce Motion: no demo; the letter waits sealed and a finger still opens it.
  const { values: v, input } = useEnvelope(width, finalCardY + LAYOUT.cardH / 2, lay.envTop, autoPlay && !reduced, reduced, { onPhase: setPhase, onHaptic });

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

  const kickerStyle = useAnimatedStyle(() => {
    const f = frameOf(v, reduced);
    const o = f.reset > 0 ? f.resetIn : 1 - clamp01(f.p / 0.08);
    return { opacity: o };
  });
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
    // Out fast on a reset, so the title is gone before the sealed headline starts to come back.
    const timing = { duration: showFinal ? 320 : reduced ? REDUCED_MS * RESET.outShare : RESET.textOutMs, easing: TEXT_EASE, reduceMotion: ReduceMotion.Never };
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
        <Animated.Text pointerEvents="none" style={[styles.kicker, { top: lay.headlineTop - 24 }, kickerStyle]}>{GIFT.kicker}</Animated.Text>
      )}
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
      <Animated.View style={[styles.openWrap, { top: lay.buttonY - LAYOUT.hintH / 2 }, buttonStyle]} pointerEvents={sealed ? 'box-none' : 'none'}>
        <Pressable onPress={() => ui(() => { 'worklet'; input.open(); })} pressRetentionOffset={16} hitSlop={8}
          accessibilityRole="button" accessibilityLabel="Open the letter">
          {({ pressed }) => <Animated.View style={[styles.open, pressStyle(pressed)]}>
            <View style={styles.chevron} />
            <Text style={styles.openText}>{GIFT.hint}</Text>
          </Animated.View>}
        </Pressable>
      </Animated.View>

      {fontReady && <Animated.Text pointerEvents="none" style={[styles.title, { top: lay.titleTop }, final0]} accessibilityRole="header">
        {GIFT.title}
      </Animated.Text>}
      <Animated.View pointerEvents="none" style={[styles.message, { top: lay.messageTop }, final1]}
        accessible={revealed} accessibilityLabel={`Gift card, ${GIFT.amount} at ${GIFT.shop}. ${GIFT.message}`}>
        <Text style={styles.messageText}>{GIFT.caption}</Text>
        <Text style={styles.messageSub}>{GIFT.captionSub}</Text>
      </Animated.View>
      <Animated.View style={[styles.ctaWrap, { top: lay.ctaTop }, final2]} pointerEvents={revealed ? 'box-none' : 'none'}>
        <Pressable onPress={() => (onShop ? onShop() : ui(() => { 'worklet'; input.reset(); }))} pressRetentionOffset={16}
          accessibilityRole="button" accessibilityLabel={GIFT.cta}>
          {({ pressed }) => <Animated.View style={[styles.cta, pressStyle(pressed)]}>
            <Text style={styles.ctaText}>{GIFT.cta}</Text>
          </Animated.View>}
        </Pressable>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.table },
  envA11y: { position: 'absolute', width: LAYOUT.envW, height: LAYOUT.envH },
  kicker: { position: 'absolute', left: LAYOUT.side, right: LAYOUT.side, fontFamily: FONTS.ui, fontSize: 11, lineHeight: 16, letterSpacing: 1.6, color: COLORS.mute },
  headlineBox: { position: 'absolute', left: LAYOUT.side, right: LAYOUT.side },
  headlineLine: { flexDirection: 'row' },
  // Instrument Serif's caps and ascenders reach above its line box on iOS: the line is set 4 pt taller than the size.
  headline: { color: COLORS.ink, fontSize: LAYOUT.headline, lineHeight: LAYOUT.headlineLine, fontFamily: FONTS.serif, letterSpacing: -0.6 },
  openWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  open: {
    height: LAYOUT.hintH, paddingHorizontal: 24, borderRadius: LAYOUT.hintH / 2, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: COLORS.faint,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, backgroundColor: 'rgba(243,236,223,0.04)',
  },
  chevron: { width: 9, height: 9, borderLeftWidth: 1.75, borderTopWidth: 1.75, borderColor: COLORS.ink, transform: [{ rotate: '45deg' }], marginTop: 4 },
  openText: { color: COLORS.ink, fontFamily: FONTS.ui, fontSize: 15, letterSpacing: -0.1 },
  title: {
    position: 'absolute', left: LAYOUT.side, right: LAYOUT.side, color: COLORS.ink, fontSize: LAYOUT.title,
    lineHeight: LAYOUT.titleLine, fontFamily: FONTS.serif, letterSpacing: -0.4,
  },
  message: { position: 'absolute', left: LAYOUT.side, right: LAYOUT.side, alignItems: 'center' },
  messageText: { color: COLORS.ink, fontFamily: FONTS.ui, fontSize: 15, lineHeight: 20, textAlign: 'center' },
  messageSub: { color: COLORS.mute, fontFamily: FONTS.ui, fontSize: 13, lineHeight: 18, textAlign: 'center', marginTop: 4 },
  ctaWrap: { position: 'absolute', left: LAYOUT.side, right: LAYOUT.side },
  cta: { height: LAYOUT.buttonH, borderRadius: LAYOUT.buttonH / 2, backgroundColor: COLORS.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { color: COLORS.ctaInk, fontFamily: FONTS.uiBold, fontSize: 16, letterSpacing: -0.2 },
});
