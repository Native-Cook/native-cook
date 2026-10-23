// Native accessible copy and one continuous glass-pill-to-chrome-button surface.
import { LinearGradient } from 'expo-linear-gradient';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { PRIZES, type WheelMotion } from './wheel-contract';
import type { WheelMotionApi } from './use-wheel-motion';
import { buttonPose, COPY, tone, WHEEL_FONT, type ScreenLayout } from './wheel-theme';

export function WheelCopy({ layout: l, motion, clock, reduced, status, collect, canCollect, onGlyphReady }: {
  layout: ScreenLayout; motion: WheelMotion; clock: SharedValue<number>; reduced: boolean;
  status: WheelMotionApi['status']; collect: WheelMotionApi['collect']; canCollect: boolean;
  onGlyphReady: (ready: boolean) => void;
}) {
  const { copySwap, caption, button, buttonPress, hint, hintNudge } = motion;
  const idleStyle = useAnimatedStyle(() => ({ opacity: 1 - copySwap.get(), transform: [{ translateY: -8 * copySwap.get() }] }));
  const wonStyle = useAnimatedStyle(() => ({ opacity: copySwap.get(), transform: [{ translateY: 8 * (1 - copySwap.get()) }] }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: caption.get(), transform: [{ translateY: 8 * (1 - caption.get()) }] }));
  const surfaceStyle = useAnimatedStyle(() => {
    const p = buttonPose(l, button.get(), buttonPress.get(), hintNudge.get());
    return { width: p.width, height: p.height, borderRadius: p.height / 2,
      left: l.cx - p.width / 2, top: l.buttonY - p.height / 2,
      transform: [{ translateX: p.x }, { scale: p.scale }] };
  });
  const glassStyle = useAnimatedStyle(() => ({ opacity: 1 - button.get() }));
  const metalStyle = useAnimatedStyle(() => ({ opacity: button.get() }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() * (1 - button.get()), transform: [{ translateY: -6 * button.get() }] }));
  const collectStyle = useAnimatedStyle(() => ({ opacity: button.get(), transform: [{ translateY: 6 * (1 - button.get()) }] }));
  const glyphStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${reduced ? 0 : 0.12 * Math.sin(clock.get() * 2.3)}rad` }] }));
  const textStyle = { fontSize: l.bodySize, lineHeight: l.bodyLine };
  const winner = PRIZES[Math.max(0, status.winner)];
  return <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
    <Text accessibilityRole="header" numberOfLines={1} style={[styles.line, { top: l.titleTop,
      fontFamily: WHEEL_FONT, fontSize: l.titleSize, lineHeight: l.titleLine, letterSpacing: -0.4 }]}>Daily Spin</Text>
    <Animated.Text accessible={status.state !== 'won'} numberOfLines={1}
      style={[styles.line, styles.subtle, textStyle, { top: l.subtitleTop }, idleStyle]}>{COPY.idle}</Animated.Text>
    <Animated.Text accessible={status.state === 'won'} numberOfLines={1}
      style={[styles.line, styles.subtle, textStyle, { top: l.subtitleTop }, wonStyle]}>{status.winner === 1 ? COPY.jackpot : COPY.win}</Animated.Text>
    <Animated.Text accessible={status.state === 'won'} numberOfLines={1}
      style={[styles.line, styles.subtle, textStyle, { top: l.labelTop }, labelStyle]}>{winner.label}</Animated.Text>
    <Animated.View style={[styles.surface, surfaceStyle]} pointerEvents={canCollect ? 'auto' : 'none'}>
      <Animated.View pointerEvents="none" style={[styles.glow, metalStyle]} />
      <View style={styles.clip} pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Animated.View style={[StyleSheet.absoluteFill, styles.glass, glassStyle]} />
        <Animated.View style={[StyleSheet.absoluteFill, metalStyle]}>
          <LinearGradient colors={['#EEF0FF', '#FFFFFF', '#C3CBFF', '#A9B4FF']} locations={[0, 0.35, 0.53, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <View style={styles.edge} />
        <Animated.View style={[styles.content, hintStyle]}>
          <Animated.View style={glyphStyle}><Image source={require('./assets/wheel-swipe.png')} style={styles.glyph} onLoad={() => onGlyphReady(true)} /></Animated.View>
          <Text numberOfLines={1} style={styles.hint}>Swipe to spin</Text>
        </Animated.View>
        <Animated.View style={[styles.content, collectStyle]}><Text numberOfLines={1} style={styles.collect}>Collect</Text></Animated.View>
      </View>
      <Pressable accessible={canCollect} accessibilityElementsHidden={!canCollect}
        importantForAccessibility={canCollect ? 'yes' : 'no-hide-descendants'} accessibilityRole="button"
        accessibilityLabel="Collect" accessibilityState={{ disabled: !canCollect }} disabled={!canCollect}
        onPressIn={collect.onPressIn} onPressOut={collect.onPressOut} onPress={collect.onPress} style={StyleSheet.absoluteFill} />
    </Animated.View>
  </View>;
}
const styles = StyleSheet.create({
  line: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: tone.title },
  subtle: { color: tone.subtitle }, surface: { position: 'absolute' },
  clip: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: 28, overflow: 'hidden' },
  glass: { borderRadius: 28, backgroundColor: 'rgba(18,22,84,0.72)', borderWidth: 1, borderColor: 'rgba(190,198,255,0.28)' },
  glow: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: 28, backgroundColor: '#6F7DFF', shadowColor: '#6F7DFF', shadowOpacity: 0.42, shadowRadius: 18, shadowOffset: { width: 0, height: 6 } },
  edge: { position: 'absolute', top: 0, left: 20, right: 20, height: 1, backgroundColor: 'rgba(244,245,255,0.42)' },
  content: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  glyph: { width: 24, height: 24 }, hint: { fontSize: 15, fontWeight: '600', color: '#E4E6FF' },
  collect: { fontSize: 17, fontWeight: '600', color: '#0B0E3F' },
});
