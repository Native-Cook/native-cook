/**
 * Strolls' first screen: "Walks worth taking, five minutes from your door." and a glass dome at the bottom edge, through
 * which the map under the screen shows enlarged. Pull the dome up and it becomes a magnifier you move over the map;
 * let go past the threshold and it opens out into the map screen, where today's route draws itself. Back gathers the
 * map into a magnifier and drops it into the dome.
 *
 * Layers, back to front: the first screen (an image), the lens over it sampling the map image, the route, the hint,
 * the back button's touch target, the demo's finger.
 */
import { Canvas, Image, ImageShader, Path, Rect, Shader, useTypeface } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { PixelRatio, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { LENS_EFFECT } from './lens-shader';
import { makePages, routePath, type Faces } from './pages';
import { COLOR, LAYOUT, makeLayout, type Layout } from './theme';
import { useLens } from './use-lens';

export type GlassDiscOnboardingProps = { autoPlay?: boolean };

export function GlassDiscOnboarding({ autoPlay = false }: GlassDiscOnboardingProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  const [fontsLoaded, fontsFailed] = useFonts({ 'GDO-Inter-SemiBold': require('./assets/Inter-SemiBold.ttf') });
  if (!regular || !medium || !semibold || (!fontsLoaded && !fontsFailed)) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  // Geometry and Reduce Motion are fixed per mount; under Reduce Motion there is no demo, a finger still opens it.
  return (
    <Screen key={`${width}x${height}-${insets.top}-${reduced}`} lay={lay} faces={{ regular, medium, semibold }}
      reduced={reduced} autoPlay={autoPlay && !reduced} />
  );
}

function Screen({ lay, faces, reduced, autoPlay }: { lay: Layout; faces: Faces; reduced: boolean; autoPlay: boolean }) {
  const pages = useMemo(() => makePages(lay, faces, PixelRatio.get()), [lay, faces]);
  const route = useMemo(() => routePath(lay), [lay]);
  const { open, gesture, touchStart, touchEnd, onBack, values: v } = useLens({ lay, autoPlay, reduced });
  const uniforms = useDerivedValue(() => ({
    center: [v.cx.get(), v.cy.get()],
    radius: v.r.get(),
    mag: v.mag.get(),
    rim: v.rim.get(),
    lift: 1,
    // Low on the screen the dome would only show the card under it; it looks up at the park and the canal instead,
    // and looks straight down once it has risen to the middle (or opened out).
    look: [0, Math.min(0, (lay.lensY - v.cy.get()) * 0.9) * v.rim.get()],
  }));
  const routeO = useDerivedValue(() => (v.route.get() > 0.001 ? 1 : 0));
  const hint = useAnimatedStyle(() => ({ opacity: v.hint.get(), transform: [{ translateY: 8 * (1 - v.hint.get()) }] }));
  const ghost = useAnimatedStyle(() => ({
    opacity: v.ghostO.get(),
    transform: [
      { translateX: v.ghostX.get() - LAYOUT.finger / 2 },
      { translateY: v.ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.12 * v.ghostP.get() },
    ],
  }));
  const back = { left: LAYOUT.edge, top: lay.top + LAYOUT.searchY, size: LAYOUT.searchH };
  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="dark" />
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              {pages && (
                <>
                  <Image image={pages.intro} x={0} y={0} width={lay.width} height={lay.height} fit="fill" />
                  <Rect x={0} y={0} width={lay.width} height={lay.height}>
                    <Shader source={LENS_EFFECT} uniforms={uniforms}>
                      <ImageShader image={pages.map} x={0} y={0} width={lay.width} height={lay.height} fit="fill" />
                    </Shader>
                  </Rect>
                  <Path path={route} style="stroke" strokeWidth={9} strokeCap="round" strokeJoin="round" color={COLOR.routeCase}
                    start={0} end={v.route} opacity={routeO} />
                  <Path path={route} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={COLOR.route}
                    start={0} end={v.route} opacity={routeO} />
                </>
              )}
            </Canvas>
            <View accessible accessibilityRole="header" accessibilityLabel="Strolls. Walks worth taking, five minutes from your door."
              style={[styles.a11y, { top: lay.top, left: LAYOUT.margin, right: LAYOUT.margin, height: 240 }]} />
          </View>
        </GestureDetector>
        <Animated.View pointerEvents="none" style={[styles.hint, { bottom: lay.height - (lay.domeY - LAYOUT.domeR) + 16 }, hint]}>
          <View style={styles.chevron} />
          <Text style={styles.hintText}>Swipe up to explore</Text>
        </Animated.View>
        {open && (
          <Pressable onPress={onBack} accessibilityRole="button" accessibilityLabel="Back"
            style={[styles.back, { left: back.left, top: back.top, width: back.size, height: back.size }]} />
        )}
        {autoPlay && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  a11y: { position: 'absolute' },
  hint: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  chevron: {
    width: 10, height: 10, borderLeftWidth: 2, borderTopWidth: 2, borderColor: COLOR.ink, transform: [{ rotate: '45deg' }],
    marginBottom: 6, opacity: 0.7,
  },
  hintText: { fontFamily: 'GDO-Inter-SemiBold', fontSize: 13, lineHeight: 16, color: COLOR.ink, letterSpacing: 0.1 },
  back: { position: 'absolute', borderRadius: 24 },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
