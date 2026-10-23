import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { IconStage } from './IconStage';
import { SheetButton, SheetCopy, SheetDots, SheetPage, SheetPuck, SheetTouchMarker } from './SheetChrome';
import { SHEET_COPY, sheetLayout, SHEET_TONE, type SheetLayout } from './sheet-theme';
import { sheetHeight, sheetTravel } from './sheet-timeline';
import { useSheetMotion } from './use-sheet-motion';

function Screen({ layout, autoPlay }: { layout: SheetLayout; autoPlay: boolean }) {
  const reduced = useReducedMotion();
  const [stageReady, setStageReady] = useState(false);
  const motion = useSheetMotion({ layout, autoPlay, reduced, ready: stageReady });
  const { frame, visible, semantics, position, clock, running, bounce, lean, press, ambientGesture, panGesture, activateIcon } = motion;
  const { width, height, sheetShort, sheetTall } = layout;
  const gate = useAnimatedStyle(() => ({ opacity: visible.get() }));
  const sheetStyle = useAnimatedStyle(() => {
    const f = frame.get(); const h = sheetHeight(f.position, sheetShort, sheetTall);
    return { height: h, opacity: reduced ? f.open : 1,
      transform: [{ translateY: sheetTravel(f, layout, reduced) }] };
  });
  const stageStyle = useAnimatedStyle(() => {
    const f = frame.get(); return { opacity: f.contents,
      transform: [{ translateY: reduced ? 0 : 24 * (1 - f.stage) },
        { translateX: reduced ? 0 : -f.edge }, { scale: reduced ? 1 : 0.92 + 0.08 * f.stage }] };
  });
  const closeStyle = useAnimatedStyle(() => ({ opacity: frame.get().contents }));
  // Bottom anchoring cancels height changes; counter-travel pins the fog to the button.
  const fogStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -sheetTravel(frame.get(), layout, reduced) }] }));
  // The recognizer converts this body-local surface to screen-container coordinates for panBegin.
  const bodyStyle = useAnimatedStyle(() => ({ height: sheetHeight(frame.get().position, sheetShort, sheetTall) - 56 }));
  return <GestureDetector gesture={ambientGesture}><Animated.View pointerEvents={semantics.visible ? 'box-none' : 'none'}
    accessibilityElementsHidden={!semantics.visible} importantForAccessibility={semantics.visible ? 'auto' : 'no-hide-descendants'}
    style={[StyleSheet.absoluteFill, gate]}>
    <SheetPage layout={layout} motion={motion} />
    <View pointerEvents="box-none" accessibilityViewIsModal={semantics.open} style={StyleSheet.absoluteFill}>
      <Animated.View pointerEvents={semantics.open ? 'auto' : 'none'} accessibilityElementsHidden={!semantics.open}
        importantForAccessibility={semantics.open ? 'auto' : 'no-hide-descendants'} style={[styles.sheet, { width }, sheetStyle]}>
        <View style={styles.sheetClip}>
          <LinearGradient pointerEvents="none" colors={[SHEET_TONE.sheetTop, SHEET_TONE.sheetBottom]}
            style={styles.sheetGradient} />
          <View pointerEvents="none" style={styles.topHighlight} />
          <SheetCopy index={0} layout={layout} motion={motion} reduced={reduced} />
          <SheetCopy index={1} layout={layout} motion={motion} reduced={reduced} />
          <SheetCopy index={2} layout={layout} motion={motion} reduced={reduced} />
          <SheetCopy index={3} layout={layout} motion={motion} reduced={reduced} />
          <Animated.View pointerEvents="none" accessible accessibilityRole="image" accessibilityLabel={SHEET_COPY[semantics.step].image}
            accessibilityHint="Press and hold to squash the clay icon." accessibilityActions={[{ name: 'activate' }]}
            onAccessibilityTap={activateIcon}
            onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'activate') activateIcon(); }}
            style={[{ position: 'absolute', left: 0, bottom: height - layout.stageTop - layout.stageHeight }, stageStyle]}>
            <IconStage width={width} height={layout.stageHeight} centreX={layout.centreX} centreY={layout.centreY}
              unit={layout.unit} position={position} clock={clock} running={running} bounce={bounce} lean={lean}
              press={press} reducedMotion={reduced} onReady={setStageReady} />
          </Animated.View>
          <SheetDots layout={layout} motion={motion} />
          <GestureDetector gesture={panGesture}>
            <Animated.View collapsable={false} accessible={false} style={[styles.bodyTouch, { width }, bodyStyle]} />
          </GestureDetector>
          <Animated.View style={[styles.close, closeStyle]}><SheetPuck kind={0} motion={motion} /></Animated.View>
          <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
            style={[styles.fog, { height: layout.fog.height }, fogStyle]}>
            <LinearGradient colors={['rgba(36,38,44,0)', 'rgba(36,38,44,1)']}
              style={{ position: 'absolute', left: 0, right: 0, top: layout.fog.fadeStart, height: layout.fog.fadeHeight }} />
            <View style={[styles.fogSolid, { top: layout.fog.fadeStart + layout.fog.fadeHeight }]} />
          </Animated.View>
        </View>
      </Animated.View>
      <SheetButton layout={layout} motion={motion} reduced={reduced} />
    </View>
    {autoPlay && !reduced && <SheetTouchMarker layout={layout} motion={motion} />}
  </Animated.View></GestureDetector>;
}
export function IconMorphSheet({ autoPlay = false }: { autoPlay?: boolean }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const insets = useSafeAreaInsets();
  // Text laid out before its font is registered keeps the fallback face on iOS: mount nothing until then.
  const [fontsLoaded] = useFonts({
    'IconMorphInter-Bold': require('./assets/Inter-Bold.ttf'),
    'IconMorphInter-Regular': require('./assets/Inter-Regular.ttf'),
  });
  const onLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize((previous) => previous.width === width && previous.height === height ? previous : { width, height });
  }, []);
  return <GestureHandlerRootView onLayout={onLayout} style={styles.root}>
    {fontsLoaded && size.width > 0 && size.height > 0 && <Screen layout={sheetLayout(size.width, size.height, insets)} autoPlay={autoPlay} />}
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: SHEET_TONE.page, overflow: 'hidden' },
  sheet: { position: 'absolute', left: 0, bottom: 0, borderTopLeftRadius: 28, borderTopRightRadius: 28,
    shadowColor: '#000000', shadowRadius: 24, shadowOpacity: 0.25, shadowOffset: { width: 0, height: -8 } },
  sheetClip: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, overflow: 'hidden', borderTopLeftRadius: 28, borderTopRightRadius: 28,
    backgroundColor: SHEET_TONE.sheetBottom },
  sheetGradient: { position: 'absolute', left: 0, right: 0, top: 0, height: 240 },
  fog: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  fogSolid: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: SHEET_TONE.sheetBottom },
  topHighlight: { position: 'absolute', top: 0, left: 28, right: 28, height: 1, backgroundColor: 'rgba(255,255,255,0.06)' },
  close: { position: 'absolute', top: 6, right: 10 },
  bodyTouch: { position: 'absolute', left: 0, bottom: 0 },
});
