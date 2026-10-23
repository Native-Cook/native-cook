// Chrome Zero — a measured, standalone onboarding stage with one shared lighting timeline.
import { useImage } from '@shopify/react-native-skia';
import { useCallback, useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useZeroMotion } from './use-zero-motion';
import { SHOW_TOUCH_MARKER, type ZeroLayout, zeroLayout, ZERO_TONE } from './zero-theme';
import type { ZeroFrame } from './zero-timeline';
import { ZeroBackdrop } from './ZeroBackdrop';
import { ZeroCanvas } from './ZeroCanvas';
import { ZeroCard } from './ZeroCard';
import { ZeroCopy } from './ZeroCopy';
import { ZeroDust, ZeroSparkles } from './ZeroSparkles';

function TouchMarker({ layout, frame }: { layout: ZeroLayout; frame: SharedValue<ZeroFrame> }) {
  const { k, offsetX, button } = layout;
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    // The scripted 150 pt drag uses the same physical-point gain as a real finger.
    const x = f.markerButton ? button.x + button.width / 2
      : offsetX + f.markerX * k + f.markerDrag * (1 - k);
    const y = f.markerButton ? button.y + button.height / 2 : f.markerY * k;
    return { opacity: f.markerOpacity, transform: [{ translateX: x - 20 }, { translateY: y - 20 },
      { scale: 1 - 0.14 * f.markerPress }] };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[styles.marker, style]} />;
}

function ZeroStage({ layout, autoPlay }: { layout: ZeroLayout; autoPlay: boolean }) {
  const reduced = useReducedMotion();
  const [canvasReady, setCanvasReady] = useState(false);
  // Load once here: the gate and the drawing layers use the very same decoded images.
  const cardImage = useImage(require('./assets/zero-card.png'));
  const sparkleImage = useImage(require('./assets/zero-spark.png'));
  const ready = canvasReady && cardImage !== null && sparkleImage !== null;
  const motion = useZeroMotion({ autoPlay, reduced, centreX: layout.centreX, centreY: layout.centreY, unit: layout.unit });
  // Never capture `motion` in a worklet: it also owns native gesture objects.
  const { frame, visible, onReady, yaw, pitch, scale, build, drain, fuse, ripple, rippleX, rippleY,
    sweep, percent, flash } = motion;
  useEffect(() => { onReady(ready); }, [onReady, ready]);
  const fade = useAnimatedStyle(() => ({ opacity: visible.get() }));
  return <>
    <ZeroBackdrop width={layout.width} height={layout.height} frame={frame} visible={visible} />
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fade]}>
      <ZeroDust layout={layout} frame={frame} reduced={reduced} />
    </Animated.View>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fade]}>
      <ZeroCard layout={layout} frame={frame} reduced={reduced} image={cardImage} />
    </Animated.View>
    <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
      style={[{ position: 'absolute', top: 0, left: 0 }, fade]}>
      {/* Full height: on the exit the beads fall out of the bottom of the screen, not into a seam. */}
      <ZeroCanvas width={layout.width} height={layout.height} centreX={layout.centreX}
        centreY={layout.centreY} unit={layout.unit} yaw={yaw} pitch={pitch} scale={scale}
        build={build} drain={drain} fuse={fuse} ripple={ripple} rippleX={rippleX} rippleY={rippleY}
        sweep={sweep} percent={percent} flash={flash} onReady={setCanvasReady} />
    </Animated.View>
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fade]}>
      <ZeroSparkles layout={layout} frame={frame} reduced={reduced} image={sparkleImage} />
      <ZeroDust layout={layout} frame={frame} reduced={reduced} near />
    </Animated.View>
    <GestureDetector gesture={motion.gesture}>
      <View collapsable={false} pointerEvents={ready ? 'auto' : 'none'} accessible={ready}
        accessibilityRole="image" accessibilityLabel="Chrome zero percent numeral"
        accessibilityHint="Drag to turn. Hold to loosen it into beads."
        accessibilityActions={[{ name: 'activate' }]} onAccessibilityTap={motion.activateHero}
        onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') motion.activateHero(); }}
        style={{ position: 'absolute', width: layout.width, height: layout.heroHeight }} />
    </GestureDetector>
    <Animated.View pointerEvents={ready ? 'box-none' : 'none'} accessibilityElementsHidden={!ready}
      importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'} style={[StyleSheet.absoluteFill, fade]}>
      <ZeroCopy layout={layout} frame={frame} reduced={reduced} buttonGesture={motion.buttonGesture}
        activateButton={motion.activateButton} />
    </Animated.View>
    {ready && autoPlay && !reduced && SHOW_TOUCH_MARKER && <TouchMarker layout={layout} frame={frame} />}
  </>;
}

/** Container measurement also permits a smaller embedded preview; no window dimensions are used. */
export function ChromeZeroNumeral({ autoPlay = false }: { autoPlay?: boolean }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const safe = useSafeAreaInsets();
  const measure = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize((previous) => previous.width === width && previous.height === height ? previous : { width, height });
  }, []);
  const layout = zeroLayout(size.width, size.height, safe.top, safe.bottom);
  return <GestureHandlerRootView style={styles.root} onLayout={measure}>
    {size.width > 0 && size.height > 0 && <ZeroStage layout={layout} autoPlay={autoPlay} />}
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: ZERO_TONE.page, overflow: 'hidden' },
  marker: { position: 'absolute', left: 0, top: 0, width: 40, height: 40, borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.14)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.45)' },
});
