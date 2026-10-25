import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, ActionSheetIOS, AppState, Image, Modal, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { interpolateColor, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type FrameCallback, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { BOTTOMS, FLAVORS, TOPS, clamp, initialMotion, interact, panel, rounded, selection, tabShift, tabWidth, tabX, wrap, type Motion } from './motion';
export { LOOP } from './motion';

const ART = [require('./assets/strawberry.png'), require('./assets/blueberry.png'), require('./assets/orange.png'), require('./assets/kiwi.png'), require('./assets/apricot.png')];
const COPIES = [-1, 0, 1, 2, 3, 4, 5, 6], TAB_COPIES = [-1, 0, 1, 2, 3, 4, 5, 6, 7];
type Props = { autoPlay?: boolean; previewTime?: number };
function haptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function Drink({ copy, q }: { copy: number; q: SharedValue<number> }) {
  const index = wrap(copy);
  const groupStyle = useAnimatedStyle(() => { const p = panel(copy, q.get()); return { zIndex: Math.round(p.z * 1000), opacity: p.opacity }; });
  const panelStyle = useAnimatedStyle(() => { const p = panel(copy, q.get()); return { left: p.x, top: p.y, width: p.width, height: p.height, borderRadius: p.radius }; });
  const edgeStyle = useAnimatedStyle(() => ({ borderRadius: panel(copy, q.get()).radius }));
  const imageStyle = useAnimatedStyle(() => { const p = panel(copy, q.get()); return { left: p.artX - 168, top: p.artY - 252, transform: [{ scale: p.artScale }] }; });
  return <Animated.View style={[StyleSheet.absoluteFill, groupStyle]} collapsable={false} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[styles.panel, panelStyle]}>
      <LinearGradient colors={[TOPS[index], BOTTOMS[index]]} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 1 }} style={StyleSheet.absoluteFill} />
      <Animated.View style={[styles.panelEdge, edgeStyle]} />
    </Animated.View>
    <Animated.View style={[styles.art, imageStyle]}><Image source={ART[index]} resizeMode="contain" fadeDuration={0} style={{ width: 336, height: 504 }} accessible={false} /></Animated.View>
  </Animated.View>;
}

function FlavorTab({ copy, q, activeCopy, onChoose }: { copy: number; q: SharedValue<number>; activeCopy: number; onChoose: (index: number) => void }) {
  const index = wrap(copy), width = tabWidth(copy), visibleX = tabX(copy) - tabShift(activeCopy), hidden = visibleX < -110 || visibleX > 393;
  const movement = useAnimatedStyle(() => ({ left: tabX(copy) - tabShift(q.get()) }));
  const color = useAnimatedStyle(() => ({ color: interpolateColor(Math.max(0, 1 - Math.abs(copy - q.get())), [0, 1], ['#B5B2B5', '#2D2D30'], 'RGB', { gamma: 1 }) }));
  return <Animated.View style={[styles.tab, { width }, movement]} accessibilityElementsHidden={hidden} importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}>
    <Pressable onPress={() => onChoose(index)} style={styles.tabHit} accessibilityRole="button" accessibilityLabel={FLAVORS[index]} accessibilityState={{ selected: copy === activeCopy }}>
      <Animated.Text allowFontScaling={false} numberOfLines={1} style={[styles.tabText, color]}>{FLAVORS[index]}</Animated.Text>
    </Pressable>
  </Animated.View>;
}

export default function FruitSmoothieCarousel({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions(), scale = width / 393, baseY = Math.max(0, (height - 852 * scale) / 2);
  const initialReduced = useReducedMotion(), [reduced, setReduced] = useState(initialReduced), [chooser, setChooser] = useState(false), [activeCopy, setActiveCopy] = useState(0);
  const motion = useSharedValue<Motion>(initialMotion()), active = useSharedValue(true), choosing = useSharedValue(false);
  const driverRef = useRef<FrameCallback | null>(null), timer = useRef<ReturnType<typeof setTimeout> | null>(null), nativeChooser = useRef(false);
  const setDriverRunning = useCallback((running: boolean) => driverRef.current?.setActive(running), []);
  const cancelTimer = useCallback(() => { if (timer.current !== null) clearTimeout(timer.current); timer.current = null; }, []);
  const q = useDerivedValue(() => selection(motion.get(), autoPlay, reduced, previewTime), [autoPlay, reduced, previewTime]);
  const driver = useFrameCallback(info => {
    'worklet'; if (!active.get() || choosing.get()) return;
    const m = motion.get(); if (m.held) return;
    const now = info.timestamp / 1000; motion.set({ ...m, now, epoch: m.epoch === 0 ? now : m.epoch });
  }, autoPlay && !reduced && previewTime === undefined);
  useEffect(() => { driverRef.current = driver; return () => { driverRef.current = null; }; }, [driver]);
  useAnimatedReaction(() => rounded(q.get()), (value, old) => { if (value !== old) scheduleOnRN(setActiveCopy, value); });

  const armReturn = useCallback((id: number, duration: number) => {
    cancelTimer();
    if (reduced) return;
    timer.current = setTimeout(() => {
      timer.current = null;
      scheduleOnUI((ticket: number) => {
        'worklet'; const m = motion.get(); if (m.id !== ticket || m.held || m.from === null || !active.get() || choosing.get()) return;
        if (autoPlay) motion.set(initialMotion(performance.now() / 1000, m.id + 1));
        else motion.set({ ...m, moving: false });
        scheduleOnRN(setDriverRunning, autoPlay && !reduced && previewTime === undefined);
      }, id);
    }, (autoPlay ? 2.6 : Math.max(0.02, duration + 0.02)) * 1000);
  }, [cancelTimer, motion, active, choosing, autoPlay, reduced, previewTime, setDriverRunning]);
  const reset = useCallback(() => {
    if (nativeChooser.current) { nativeChooser.current = false; ActionSheetIOS.dismissActionSheet(); }
    cancelTimer(); setChooser(false); choosing.set(false);
    scheduleOnUI(() => { 'worklet'; motion.set(initialMotion(0, motion.get().id + 1)); });
    setDriverRunning(autoPlay && !reduced && previewTime === undefined && AppState.currentState === 'active');
  }, [cancelTimer, choosing, motion, setDriverRunning, autoPlay, reduced, previewTime]);
  useEffect(() => { const task = setTimeout(reset, 0); return () => clearTimeout(task); }, [reset]);
  useEffect(() => {
    let mounted = true, revision = 0;
    const update = (value: boolean) => { revision++; setReduced(value); };
    const listener = AccessibilityInfo.addEventListener('reduceMotionChanged', update), initialRevision = revision;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted && revision === initialRevision) update(value); }).catch(() => {});
    return () => { mounted = false; listener.remove(); };
  }, []);
  useEffect(() => {
    const listener = AppState.addEventListener('change', state => { active.set(state === 'active'); if (state === 'active') reset(); else { cancelTimer(); setDriverRunning(false); } });
    return () => { listener.remove(); cancelTimer(); setDriverRunning(false); if (nativeChooser.current) { nativeChooser.current = false; ActionSheetIOS.dismissActionSheet(); } };
  }, [active, reset, cancelTimer, setDriverRunning]);

  const choose = useCallback((index: number) => {
    if (previewTime !== undefined) return;
    cancelTimer(); setChooser(false);
    scheduleOnUI((item: number) => {
      'worklet'; const now = performance.now() / 1000, m = motion.get(), current = selection({ ...m, now }, autoPlay, reduced, previewTime);
      const flavor = wrap(item), target = flavor === 0 && current > 2.5 ? 5 : flavor, changed = wrap(rounded(current)) !== flavor;
      const next = interact(m, target, now, autoPlay, reduced, previewTime);
      motion.set(next); choosing.set(false); scheduleOnRN(armReturn, next.id, next.duration);
      scheduleOnRN(setDriverRunning, !reduced); if (changed && !reduced) scheduleOnRN(haptic);
    }, index);
  }, [previewTime, cancelTimer, motion, autoPlay, reduced, choosing, armReturn, setDriverRunning]);
  const cancelChooser = useCallback(() => {
    setChooser(false);
    scheduleOnUI(() => {
      'worklet'; const now = performance.now() / 1000, m = motion.get(), current = selection({ ...m, now }, autoPlay, reduced, previewTime);
      const next = interact(m, current, now, autoPlay, reduced, previewTime, 0);
      motion.set(next); choosing.set(false); scheduleOnRN(armReturn, next.id, 0); scheduleOnRN(setDriverRunning, autoPlay && !reduced);
    });
  }, [motion, autoPlay, reduced, previewTime, choosing, armReturn, setDriverRunning]);
  const presentChooser = useCallback(() => {
    if (previewTime !== undefined) return;
    cancelTimer(); setChooser(true); setDriverRunning(false);
    scheduleOnUI(() => {
      'worklet'; const now = performance.now() / 1000, m = motion.get(), current = selection({ ...m, now }, autoPlay, reduced, previewTime);
      motion.set({ ...m, now, from: current, target: current, held: true, moving: false, id: m.id + 1 }); choosing.set(true);
    });
    if (Platform.OS === 'ios') {
      nativeChooser.current = true;
      ActionSheetIOS.showActionSheetWithOptions({ title: 'Choose a flavor', options: [...FLAVORS, 'Cancel'], cancelButtonIndex: 5 }, index => {
        if (!nativeChooser.current) return; nativeChooser.current = false;
        if (index === 5) cancelChooser(); else choose(index);
      });
    }
  }, [previewTime, cancelTimer, setDriverRunning, motion, autoPlay, reduced, choosing, cancelChooser, choose]);
  const chooseAdjacent = useCallback((direction: number) => {
    scheduleOnUI((delta: number) => {
      'worklet'; const current = selection({ ...motion.get(), now: performance.now() / 1000 }, autoPlay, reduced, previewTime); scheduleOnRN(choose, wrap(rounded(current) + delta));
    }, direction);
  }, [motion, autoPlay, reduced, previewTime, choose]);

  /* eslint-disable react-hooks/refs, react-hooks/purity -- Gesture callbacks execute later on the UI thread. */
  const updatePan = (translation: number) => {
    'worklet'; const now = performance.now() / 1000, old = motion.get(); let m = old;
    if (!m.held) {
      const current = selection({ ...m, now }, autoPlay, reduced, previewTime), value = current >= 5 - 0.00000001 ? current - 5 : current;
      m = { ...m, from: value, target: value, panOrigin: value, held: true, moving: false, id: m.id + 1 };
      scheduleOnRN(cancelTimer); scheduleOnRN(setDriverRunning, false);
    }
    const proposed = m.panOrigin - translation / scale / 304, edge = clamp(proposed, 0, 5), over = (proposed - edge) * 304;
    motion.set({ ...m, now, target: edge + over / (1 + Math.abs(over) / 120) / 304, duration: 0, lastTouch: now });
  };
  const finishPan = (velocity: number, shouldHaptic: boolean) => {
    'worklet'; const m = motion.get(); if (!m.held || choosing.get()) return;
    const projected = m.target - clamp(velocity, -1200, 1200) * 0.12 / 304, target = clamp(rounded(projected), 0, 5);
    const changed = wrap(target) !== wrap(rounded(m.panOrigin));
    const next = interact(m, target, performance.now() / 1000, autoPlay, reduced, previewTime, 0.42, true);
    motion.set(next); scheduleOnRN(armReturn, next.id, 0.42); scheduleOnRN(setDriverRunning, !reduced);
    if (shouldHaptic && changed && !reduced) scheduleOnRN(haptic);
  };
  const pan = Gesture.Pan().minDistance(6).enabled(!reduced && previewTime === undefined && !chooser)
    .onStart(event => { updatePan(event.translationX); })
    .onUpdate(event => { updatePan(event.translationX); })
    .onEnd(event => { finishPan(event.velocityX / scale, true); })
    .onFinalize(() => { finishPan(0, false); });
  /* eslint-enable react-hooks/refs, react-hooks/purity */
  const underline = useAnimatedStyle(() => {
    const value = q.get(), current = Math.floor(value), fraction = value - current;
    const width = tabWidth(current) * (1 - fraction) + tabWidth(current + 1) * fraction;
    return { left: 63 + width / 2 - 25 };
  });

  return <View style={styles.screen}>
    <StatusBar style="dark" />
    <GestureDetector gesture={pan}>
      <View style={[styles.stage, { top: baseY, transform: [{ scale }], transformOrigin: [0, 0, 0] }]} pointerEvents={previewTime === undefined ? 'auto' : 'none'}>
        <View style={styles.heading} pointerEvents="none"><Text allowFontScaling={false} style={styles.seasonal}>Seasonal</Text><Text allowFontScaling={false} style={styles.smoothie}>Smoothie</Text></View>
        <View style={styles.cards} collapsable={false} accessible accessibilityRole="adjustable" accessibilityLabel={`${FLAVORS[wrap(activeCopy)]} smoothie`} accessibilityHint="Swipe horizontally to choose a flavor" accessibilityActions={[{ name: 'increment', label: 'Next flavor' }, { name: 'decrement', label: 'Previous flavor' }]} onAccessibilityAction={event => chooseAdjacent(event.nativeEvent.actionName === 'decrement' ? -1 : 1)}>
          {COPIES.map(copy => <Drink key={copy} copy={copy} q={q} />)}
        </View>
        <View style={styles.tabs} collapsable={false} pointerEvents="box-none">
          {TAB_COPIES.map(copy => <FlavorTab key={copy} copy={copy} q={q} activeCopy={activeCopy} onChoose={choose} />)}
          <Animated.View pointerEvents="none" style={[styles.underline, underline]} />
        </View>
        <View style={styles.navigation} collapsable={false} pointerEvents="box-none">
          <Pressable style={styles.menuHit} onPress={presentChooser} accessibilityRole="button" accessibilityLabel="Choose a flavor"><View pointerEvents="none" style={styles.menuLines}>{[0, 1, 2].map(line => <View key={line} style={styles.menuLine} />)}</View></Pressable>
          <Pressable style={styles.searchHit} onPress={presentChooser} accessibilityRole="button" accessibilityLabel="Find a flavor">
            {Platform.OS === 'ios' ? <SymbolView name="magnifyingglass" size={20} weight="regular" tintColor="#AEAAAF" style={styles.searchSymbol} /> : <Feather name="search" size={20} color="#AEAAAF" />}
          </Pressable>
        </View>
      </View>
    </GestureDetector>
    <Modal visible={chooser && Platform.OS !== 'ios'} transparent animationType={reduced ? 'none' : 'fade'} onRequestClose={cancelChooser}>
      <View style={styles.modalBackdrop}><View style={styles.dialog}><Text style={styles.dialogTitle}>Choose a flavor</Text>{FLAVORS.map((flavor, index) => <Pressable key={flavor} style={styles.dialogAction} onPress={() => choose(index)} accessibilityRole="button"><Text style={styles.dialogText}>{flavor}</Text></Pressable>)}<Pressable style={styles.dialogAction} onPress={cancelChooser} accessibilityRole="button"><Text style={styles.dialogText}>Cancel</Text></Pressable></View></View>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FCFAFC' }, stage: { position: 'absolute', left: 0, width: 393, height: 852, backgroundColor: '#FCFAFC', overflow: 'hidden' },
  heading: { position: 'absolute', left: 63, top: 161, width: 260, height: 54 },
  seasonal: { color: '#242426', fontSize: 22, fontWeight: '600', lineHeight: 27, includeFontPadding: false }, smoothie: { color: '#242426', fontSize: 22, fontWeight: '700', lineHeight: 27, includeFontPadding: false },
  cards: { ...StyleSheet.absoluteFill, zIndex: 1 }, panel: { position: 'absolute', overflow: 'hidden' }, panelEdge: { ...StyleSheet.absoluteFill, borderColor: 'rgba(255,255,255,0.1)', borderWidth: 0.7, borderRadius: 40 },
  art: { position: 'absolute', width: 336, height: 504 }, tabs: { ...StyleSheet.absoluteFill, zIndex: 2 },
  tab: { position: 'absolute', top: 732, height: 44 }, tabHit: { flex: 1, justifyContent: 'center' }, tabText: { fontSize: 18, fontWeight: '400', lineHeight: 22, includeFontPadding: false },
  underline: { position: 'absolute', top: 776, width: 50, height: 3, borderRadius: 1.5, backgroundColor: '#C66A8F' }, navigation: { ...StyleSheet.absoluteFill, zIndex: 3 },
  menuHit: { position: 'absolute', left: 54, top: 62.5, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }, menuLines: { gap: 9.5 }, menuLine: { width: 26, height: 2, borderRadius: 1, backgroundColor: '#D5A6B4' },
  searchHit: { position: 'absolute', left: 290, top: 65, width: 44, height: 43, borderRadius: 11, backgroundColor: '#F0EDF0', alignItems: 'center', justifyContent: 'center' }, searchSymbol: { width: 20, height: 20 },
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', padding: 12, backgroundColor: 'rgba(0,0,0,0.25)' }, dialog: { borderRadius: 18, backgroundColor: '#FCFAFC', paddingVertical: 8 }, dialogTitle: { fontSize: 15, color: '#777477', textAlign: 'center', padding: 12 },
  dialogAction: { minHeight: 48, justifyContent: 'center', alignItems: 'center' }, dialogText: { color: '#2D2D30', fontSize: 18 },
});
