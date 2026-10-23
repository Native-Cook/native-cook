// Daily Spin — one measured stage, one Canvas, readiness-gated UI and gesture-driven prize physics.
import { Canvas, Circle, Group, useFont, useImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, Image, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue,
  useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PHASE, PRIZES } from './wheel-contract';
import { useWheelMotion } from './use-wheel-motion';
import { printedPrizeOpacity, tone, wheelLayout, WHEEL_FONT } from './wheel-theme';
import { WheelCopy } from './WheelCopy';
import { WheelFace } from './WheelFace';
import { WheelParticles } from './WheelParticles';
import { WheelPointer } from './WheelPointer';
import { WheelRays, WheelReveal } from './WheelReveal';
import { WheelSky } from './WheelSky';

export function ChromePrizeWheel({ autoPlay = false }: { autoPlay?: boolean }) {
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => wheelLayout(size.width || 402, size.height || 874, safe.top, safe.bottom), [size, safe.top, safe.bottom]);
  const measure = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize(old => old.width === width && old.height === height ? old : { width, height });
  }, []);
  const [fontReady] = useFonts({ [WHEEL_FONT]: require('./assets/ArchivoExpanded-Black.ttf') });
  const font = useFont(require('./assets/ArchivoExpanded-Black.ttf'), l.numeralSize);
  const stars = useImage(require('./assets/wheel-particles.png'));
  const gem = useImage(require('./assets/wheel-pointer.png'));
  const shadow = useImage(require('./assets/wheel-pointer-shadow.png'));
  const badges = useImage(require('./assets/wheel-badges.png'));
  const plate = useImage(require('./assets/prize-plate.png'));
  const chest = useImage(require('./assets/prize-chest.png'));
  const tray = useImage(require('./assets/prize-tray.png'));
  const box = useImage(require('./assets/prize-box.png'));
  const coffee = useImage(require('./assets/prize-coffee.png'));
  const stack = useImage(require('./assets/prize-stack.png'));
  const [glyphReady, setGlyphReady] = useState(false);
  const [backdropReady, setBackdropReady] = useState(false);
  const [wheelReady, setWheelReady] = useState(false);
  const [canCollect, setCanCollect] = useState(false);
  const reportCollect = useCallback((enabled: boolean) => setCanCollect(enabled), []);
  const ready = !!(size.width > 0 && wheelReady && backdropReady && fontReady && font && stars && gem && shadow
    && badges && plate && chest && tray && box && coffee && stack && glyphReady);
  const api = useWheelMotion({ layout: size.width > 0 ? l : null,
    buttonCentre: { x: l.buttonX, y: l.buttonY }, heroCentre: { x: l.heroX, y: l.heroY }, autoPlay, ready });
  const { motion, wheelGesture, heroGesture, status, collect: collectHandlers, spinFromHub } = api;
  const { wheelScale, wheelOpacity, touchX, touchY, touchOn, phase, hero, heroX, heroY, button, heroSquash, winnerPrize } = motion;
  const stage = useSharedValue(0);
  const clock = useSharedValue(0);
  useEffect(() => { stage.set(withTiming(ready ? 1 : 0, { duration: ready ? 160 : 0, reduceMotion: ReduceMotion.Never })); }, [ready, stage]);
  useFrameCallback(frame => {
    if (ready && !reduced && frame.timeSincePreviousFrame !== null) clock.set(clock.get() + frame.timeSincePreviousFrame / 1000);
  });
  useAnimatedReaction(() => ready && phase.get() === PHASE.reveal && hero.get() > 0.9 && button.get() > 0.9,
    (next, old) => { if (next !== old) scheduleOnRN(reportCollect, next); });
  useEffect(() => {
    if (status.state !== 'won' || status.winner < 0) return;
    const prize = PRIZES[status.winner];
    AccessibilityInfo.announceForAccessibility(`You won ${prize.count}. ${prize.label}.`);
  }, [status.state, status.winner]);
  const visible = useAnimatedStyle(() => ({ opacity: stage.get() }));
  const wheelTransform = useDerivedValue(() => [{ scale: wheelScale.get() }]);
  const printedPrize = useDerivedValue(() => printedPrizeOpacity(phase.get(), heroSquash.get(), winnerPrize.get()));
  const heroHit = useAnimatedStyle(() => ({ transform: [{ translateX: heroX.get() }, { translateY: heroY.get() }] }));
  return <GestureHandlerRootView style={styles.root} onLayout={measure}>
    <GestureDetector gesture={wheelGesture}>
      <Animated.View collapsable={false} style={[styles.fill, visible]} pointerEvents={ready ? 'auto' : 'none'}
        accessibilityElementsHidden={!ready} importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'}>
        <Image source={require('./assets/wheel-backdrop.jpg')} resizeMode="cover" onLoad={() => setBackdropReady(true)}
          accessible={false} style={{ position: 'absolute', left: l.backdrop.x, top: l.backdrop.y,
            width: l.backdrop.width, height: l.backdrop.height }} />
        <Canvas style={styles.fill} pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          {stars && <WheelSky layout={l} motion={motion} clock={clock} image={stars} reduced={reduced} />}
          <Group origin={{ x: l.cx, y: l.cy }} transform={wheelTransform} opacity={wheelOpacity}>
            <WheelFace cx={l.cx} cy={l.cy} radius={l.radius} {...motion} winnerPrize={printedPrize} onReady={setWheelReady} />
            {gem && shadow && <WheelPointer layout={l} motion={motion} clock={clock} gem={gem} shadow={shadow} reduced={reduced} />}
          </Group>
          <WheelRays layout={l} motion={motion} clock={clock} reduced={reduced} />
          {stars && <WheelParticles layout={l} motion={motion} image={stars} near={false} reduced={reduced} />}
          {font && badges && plate && chest && tray && box && coffee && stack &&
            <WheelReveal layout={l} motion={motion} clock={clock} reduced={reduced}
              prizes={[plate, chest, tray, box, coffee, stack]} badges={badges} font={font} />}
          {stars && <WheelParticles layout={l} motion={motion} image={stars} near reduced={reduced} />}
          <Group opacity={touchOn}>
            <Circle cx={touchX} cy={touchY} r={20} color="#FFFFFF24" />
            <Circle cx={touchX} cy={touchY} r={20} color="#FFFFFF73" style="stroke" strokeWidth={1} />
          </Group>
        </Canvas>
        <View accessible={ready && status.state !== 'won'} accessibilityRole="button" accessibilityLabel="Prize wheel" accessibilityHint="Swipe to spin. Double tap to spin."
          accessibilityState={{ disabled: !ready || status.state === 'won' }} onAccessibilityTap={spinFromHub}
          pointerEvents="none" style={{ position: 'absolute', left: l.cx - l.radius, top: l.cy - l.radius,
            width: l.radius * 2, height: l.radius * 2 }} />
        <WheelCopy layout={l} motion={motion} clock={clock} reduced={reduced} status={status} collect={collectHandlers}
          canCollect={canCollect} onGlyphReady={setGlyphReady} />
        {canCollect && <GestureDetector gesture={heroGesture}>
          <Animated.View collapsable={false} accessible={false} style={[{ position: 'absolute', left: l.heroX - l.heroSize / 2,
            top: l.heroY - l.heroSize / 2, width: l.heroSize, height: l.heroSize }, heroHit]} />
        </GestureDetector>}
      </Animated.View>
    </GestureDetector>
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: tone.page, overflow: 'hidden' }, fill: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 } });
