import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { drawStory, makeArt } from './mascot-art';
import { ANCHORS, BODIES, pages, shownTime, TITLES, type Motion } from './mascot-motion';

export { LOOP } from './mascot-motion';
export type MascotSocialTourProps = { autoPlay?: boolean; renderTime?: number; onLogin?: () => void };
function tactile() { if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

function Copy({ index, time }: { index: number; time: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const p = pages(time.get());
    const current = p.to === index, departing = p.from === index && p.from !== p.to;
    return {
      opacity: current ? 1 : departing ? 1 - p.p * 0.2 : 0,
      transform: [{ translateX: current ? (p.from === p.to ? 0 : 393 * (1 - p.p)) : departing ? -393 * p.p : 786 }],
    };
  });
  const accessibility = useAnimatedProps(() => ({ accessibilityElementsHidden: pages(time.get()).active !== index, importantForAccessibility: (pages(time.get()).active === index ? 'auto' : 'no-hide-descendants') as 'auto' | 'no-hide-descendants' }));
  return <Animated.View pointerEvents="none" animatedProps={accessibility} style={[styles.copy, style]}>
    <View style={styles.titleBox}><Text allowFontScaling={false} style={styles.title}>{TITLES[index]}</Text></View>
    <View style={styles.bodyBox}><Text allowFontScaling={false} style={styles.body}>{BODIES[index]}</Text></View>
  </Animated.View>;
}
function Dot({ index, time }: { index: number; time: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const active = pages(time.get()).active === index;
    return { width: active ? 16 : 5.8, backgroundColor: active ? '#2084F8' : '#E8EAEE' };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

/** A faithful port of the four-page Swift illustration. All frame drawing stays on the UI thread. */
export function MascotSocialTour({ autoPlay = false, renderTime, onLogin }: MascotSocialTourProps) {
  const window = useWindowDimensions(), initialReduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [reduced, setReduced] = useState(initialReduced), [manualClock, setManualClock] = useState(false);
  const [fontsLoaded] = useFonts({ MascotTitanOne: require('./assets/TitanOne-Regular.ttf') });
  const art = useMemo(() => makeArt(), []), width = size?.width ?? window.width, height = size?.height ?? window.height;
  const sx = width / 393, sy = height / 852;
  const m = useSharedValue<Motion>({ now: 0, epoch: 0, manual: -1, dragging: false, origin: 0, released: -1, from: 0, to: 0 });
  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => subscription.remove();
  }, []);
  useEffect(() => {
    if (!fontsLoaded) return;
    const now = Date.now() / 1000; m.set(old => ({ ...old, now, epoch: now }));
  }, [fontsLoaded, m]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = m.get();
    if (old.manual >= 0 && !old.dragging && now - old.released >= 2.55) {
      m.set({ ...old, now, epoch: old.released + 2.55, manual: -1 }); scheduleOnRN(setManualClock, false);
    } else m.set({ ...old, now });
  }, false);
  useEffect(() => {
    ticker.setActive(fontsLoaded && !reduced && renderTime === undefined && (autoPlay || manualClock));
    return () => ticker.setActive(false);
  }, [autoPlay, fontsLoaded, manualClock, reduced, renderTime, ticker]);
  const time = useDerivedValue(() => { const motion = m.get(); return shownTime(motion, motion.now, autoPlay, reduced, renderTime); });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.scale(sx, sy); drawStory(canvas, art, time.get());
    return recorder.finishRecordingAsPicture();
  });
  const begin = useCallback(() => {
    'worklet'; if (renderTime !== undefined) return;
    const now = Date.now() / 1000, old = m.get(), current = shownTime(old, now, autoPlay, reduced);
    m.set({ ...old, now, origin: current, manual: current, dragging: true }); scheduleOnRN(setManualClock, true);
  }, [autoPlay, m, reduced, renderTime]);
  const release = useCallback((velocityX: number, translationX: number) => {
    'worklet'; if (renderTime !== undefined) return;
    const old = m.get(), now = Date.now() / 1000;
    // Swift's predicted travel uses about 0.35 s; its 18% contribution is 0.063 × velocity.
    const projected = Math.min(9.4, Math.max(0, old.origin - (translationX + velocityX * 0.063) / (80 * sx)));
    let target = ANCHORS[0];
    for (let i = 1; i < ANCHORS.length; i++) if (Math.abs(ANCHORS[i] - projected) < Math.abs(target - projected)) target = ANCHORS[i];
    m.set({ ...old, now, dragging: false, manual: reduced ? target : old.manual, released: now, from: old.manual, to: target });
    scheduleOnRN(tactile);
  }, [m, reduced, renderTime, sx]);
  const pan = Gesture.Pan().minDistance(6).onStart(begin)
    .onUpdate(event => {
      if (renderTime !== undefined) return;
      m.set(old => ({ ...old, now: Date.now() / 1000, manual: Math.min(9.4, Math.max(0, old.origin - event.translationX / (80 * sx))) }));
    })
    .onEnd(event => release(event.velocityX, event.translationX))
    .onFinalize((_event, success) => { if (!success && m.get().dragging) release(0, (m.get().origin - m.get().manual) * 80 * sx); });
  const login = () => {
    const old = m.get(), now = Date.now() / 1000, current = shownTime(old, now, autoPlay, reduced, renderTime);
    m.set({ ...old, now, manual: current, dragging: false, released: now, from: current, to: current });
    setManualClock(true); tactile(); onLogin?.();
  };
  const measure = useCallback((event: LayoutChangeEvent) => {
    const next = event.nativeEvent.layout;
    setSize(old => old?.width === next.width && old?.height === next.height ? old : { width: next.width, height: next.height });
  }, []);
  return <View onLayout={measure} style={styles.screen}>
    <StatusBar style="dark" />
    {fontsLoaded && <GestureDetector gesture={pan}>
      <View collapsable={false} style={StyleSheet.absoluteFill}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Picture picture={picture} /></Canvas>
        <View pointerEvents="box-none" style={[styles.design, { transform: [{ scaleX: sx }, { scaleY: sy }] }]}>
          {[0, 1, 2, 3].map(index => <Copy key={index} index={index} time={time} />)}
          <View pointerEvents="none" style={styles.pager}>{[0, 1, 2, 3, 4].map(index => <Dot key={index} index={index} time={time} />)}</View>
          <Pressable onPress={login} accessibilityRole="button" accessibilityLabel="Login" style={styles.loginTarget}><View style={styles.login}><Text allowFontScaling={false} style={styles.loginText}>Login</Text></View></Pressable>
          <View pointerEvents="none" style={styles.legalBox}><Text allowFontScaling={false} style={styles.legal}>{"By joining Nookly you acknowledge that you have read and\nagree to Nookly's Terms & Conditions and Privacy Policy."}</Text></View>
        </View>
      </View>
    </GestureDetector>}
  </View>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden', backgroundColor: '#FFFFFF' },
  design: { position: 'absolute', left: 0, top: 0, width: 393, height: 852, transformOrigin: 'top left' },
  copy: { position: 'absolute', left: 0, top: 0, width: 393, height: 852 },
  titleBox: { position: 'absolute', left: 10, top: 579, width: 373, height: 95, alignItems: 'center', justifyContent: 'center' },
  title: { fontFamily: 'MascotTitanOne', fontSize: 37, lineHeight: 44, textAlign: 'center', color: '#080D28', includeFontPadding: false, transform: [{ scaleX: 1.15 }] },
  bodyBox: { position: 'absolute', left: 9, top: 689, width: 375, height: 54, alignItems: 'center', justifyContent: 'center' },
  body: { fontSize: 16, lineHeight: 25.1, textAlign: 'center', color: '#66676B', includeFontPadding: false },
  pager: { position: 'absolute', left: 160.5, top: 67, width: 72, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5 },
  dot: { height: 5.8, borderRadius: 2.9 },
  loginTarget: { position: 'absolute', left: 293.5, top: 67, width: 79, height: 44, justifyContent: 'center', alignItems: 'center' },
  login: { width: 73, height: 35, borderRadius: 18, backgroundColor: '#F0F5FF', justifyContent: 'center', alignItems: 'center' },
  loginText: { fontSize: 15, fontWeight: '600', color: '#2384F7', includeFontPadding: false },
  legalBox: { position: 'absolute', left: 14, top: 772.5, width: 365, height: 45, justifyContent: 'center', alignItems: 'center' },
  legal: { fontSize: 11, lineHeight: 19, color: '#88898E', textAlign: 'center', includeFontPadding: false },
});
