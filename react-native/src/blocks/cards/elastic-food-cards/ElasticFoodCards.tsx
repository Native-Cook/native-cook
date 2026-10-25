import { Feather } from '@expo/vector-icons';
import { Canvas, Picture, Rect, Shader, Skia, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo, AppState, Image, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput,
  View, useWindowDimensions, type TextProps, type TextStyle,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction, useAnimatedScrollHandler, useAnimatedStyle, useDerivedValue, useFrameCallback,
  useReducedMotion, useSharedValue, withTiming, type FrameCallback, type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawHand, handOrigin } from './art';
import { ELASTIC_FOOD_CARDS_SURFACE_SKSL } from './cloth-shader';
import { append, clamp, driveFrame, gridClipTop, initialMotion, mixed, newDrive, open, recoveryDuration, rotated, rubber, sample, smooth, type Frame, type Motion } from './motion';
export { LOOP } from './motion';

const SURFACE = Skia.RuntimeEffect.Make(ELASTIC_FOOD_CARDS_SURFACE_SKSL);
if (!SURFACE) throw new Error('Elastic Food Cards: the surface shader did not compile.');

type Props = { autoPlay?: boolean; previewTime?: number };
const PAGE = '#DFDFDD', CARD = '#E8E8E6', PRIMARY = '#101110', SECONDARY = '#8F9290';
const NAMES = ['Harbor Ramen', 'Sunrise Rolls', 'Salmon Bites', 'Garden Miso'];
const PRICES = ['$ 15.00', '$ 9.50', '$ 7.00', '$ 4.50'];
const RATINGS = ['4.5', '4.8', '5.0', '4.3'];
const ART = {
  ramen: require('./assets/ramen.png'), rolls: require('./assets/rolls.png'), nigiri: require('./assets/nigiri.png'),
  miso: require('./assets/miso.png'), hand: require('./assets/hand.png'), half: require('./assets/hand-half.png'), fist: require('./assets/hand-fist.png'),
  promo: require('./assets/promo.png'), avatar: require('./assets/avatar.png'),
};
const PRODUCTS = [ART.ramen, ART.rolls, ART.nigiri, ART.miso];
const PRODUCT_RECTS = [
  { left: 19, top: 52.35, width: 140, height: 74.3 }, { left: 21.5, top: 54.75, width: 137, height: 68.5 },
  { left: 20, top: 45.25, width: 139, height: 92.5 }, { left: 27.5, top: 45, width: 124, height: 93 },
];

function FoodText(props: TextProps) { return <Text allowFontScaling={false} {...props} />; }
function haptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }
function starPath() {
  let d = '';
  for (let index = 0; index < 10; index++) { const angle = -Math.PI / 2 + index * Math.PI / 5, radius = index % 2 === 0 ? 8.5 : 4; d += `${index === 0 ? 'M' : 'L'}${8.5 + Math.cos(angle) * radius},${8.5 + Math.sin(angle) * radius} `; }
  return `${d}Z`;
}
const STAR = starPath();

function Heart({ saved, reduced }: { saved: boolean; reduced: boolean }) {
  const fill = useSharedValue(saved ? 1 : 0);
  useEffect(() => { fill.set(withTiming(saved ? 1 : 0, { duration: reduced ? 0 : 120 })); }, [fill, saved, reduced]);
  const outlineStyle = useAnimatedStyle(() => ({ opacity: 1 - fill.get() }));
  const fillStyle = useAnimatedStyle(() => ({ opacity: fill.get() }));
  return <View style={styles.heartArt} pointerEvents="none">
    <Animated.View style={[StyleSheet.absoluteFill, outlineStyle]}>
      {Platform.OS === 'ios' ? <SymbolView name="heart" size={19} weight="ultraLight" tintColor={PRIMARY} style={styles.symbol} /> : <Feather name="heart" size={19} color={PRIMARY} />}
    </Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, fillStyle]}>
      {Platform.OS === 'ios' ? <SymbolView name="heart.fill" size={19} weight="ultraLight" tintColor={PRIMARY} style={styles.symbol} /> : <Svg width={19} height={19} viewBox="0 0 24 24"><Path fill={PRIMARY} d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z" /></Svg>}
    </Animated.View>
  </View>;
}

function FoodCard({ index, pose, extra, matches, saved, reduced, onSave }: {
  index: number; pose: SharedValue<Frame>; extra: number; matches: boolean; saved: boolean; reduced: boolean; onSave: (index: number) => void;
}) {
  const movement = useAnimatedStyle(() => ({ transform: [{ translateY: 389 - gridClipTop(pose.get()) + 148 * pose.get().q[Math.floor(index / 2) + 4] }] }));
  const height = 225 + extra;
  return <Animated.View style={[styles.foodCard, { left: index % 2 === 0 ? 16 : 203, top: Math.floor(index / 2) * (238 + extra), height, opacity: matches ? 1 : 0.32 }, movement]} accessible accessibilityLabel={`${NAMES[index]}, ${PRICES[index]}, rated ${RATINGS[index]} out of 5`} accessibilityActions={[{ name: 'toggleSave', label: saved ? 'Unsave food' : 'Save food' }]} onAccessibilityAction={() => onSave(index)}>
    <Image source={PRODUCTS[index]} resizeMode="contain" fadeDuration={0} style={[styles.product, PRODUCT_RECTS[index]]} accessible={false} />
    <View style={[styles.nameBox, { height: 26 + extra }]}><FoodText numberOfLines={extra ? 2 : 1} style={[styles.foodName, { fontSize: extra ? 20 : 17, lineHeight: extra ? 24 : 20.3 }]}>{NAMES[index]}</FoodText></View>
    <FoodText style={[styles.foodPrice, { top: 179 + extra, fontSize: extra ? 19 : 16 }]}>{PRICES[index]}</FoodText>
    <Svg width={17} height={17} style={{ position: 'absolute', left: 114.5, top: 182.5 + extra }} accessible={false}><Path d={STAR} fill="#E8B14B" /></Svg>
    <FoodText style={[styles.rating, { top: 179 + extra, fontSize: extra ? 18 : 15 }]}>{RATINGS[index]}</FoodText>
    <Pressable style={styles.heartHit} onPress={() => onSave(index)} accessibilityRole="button" accessibilityLabel={`${saved ? 'Unsave' : 'Save'} ${NAMES[index]}`} accessibilityValue={{ text: saved ? 'Saved' : 'Not saved' }}>
      <Heart saved={saved} reduced={reduced} />
    </Pressable>
  </Animated.View>;
}

export default function ElasticFoodCards({ autoPlay = false, previewTime }: Props) {
  const { width, height, fontScale } = useWindowDimensions(), insets = useSafeAreaInsets();
  const scale = width / 393, baseY = Math.max(0, (height - 852 * scale) / 2), extra = fontScale >= 1.65 ? 44 : 0;
  const origin = handOrigin(insets.top, baseY, scale, Platform.OS === 'ios');
  const startupReduced = useReducedMotion(), [reduced, setReduced] = useState(startupReduced);
  const [saved, setSaved] = useState<Set<number>>(() => new Set()), [category, setCategory] = useState(0);
  const [searchVisible, setSearchVisible] = useState(false), [query, setQuery] = useState(''), [draftQuery, setDraftQuery] = useState('');
  const [canScroll, setCanScroll] = useState(true);
  const hand = useImage(ART.hand), half = useImage(ART.half), fist = useImage(ART.fist);
  const motion = useSharedValue<Motion>(initialMotion()), active = useSharedValue(true), searching = useSharedValue(false);
  const reductionEnabled = useSharedValue(startupReduced), ready = useSharedValue(false), scrollOffset = useSharedValue(0);
  const driverRef = useRef<FrameCallback | null>(null), timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null), gridRef = useRef<ScrollView | null>(null);
  const setDriverRunning = useCallback((running: boolean) => driverRef.current?.setActive(running), []);
  const cancelTimer = useCallback(() => {
    if (timer.current !== null) clearTimeout(timer.current); timer.current = null;
    if (settleTimer.current !== null) clearTimeout(settleTimer.current); settleTimer.current = null;
  }, []);
  const resetControls = useCallback(() => { setSaved(new Set()); setCategory(0); setQuery(''); setDraftQuery(''); gridRef.current?.scrollTo({ y: 0, animated: false }); }, []);
  const frame = useDerivedValue(() => sample(motion.get(), autoPlay, reduced, searching.get(), previewTime), [autoPlay, reduced, previewTime]);

  const driver = useFrameCallback(info => {
    'worklet'; if (!active.get() || !ready.get() || searching.get()) return;
    const now = info.timestamp / 1000, m = motion.get(); if (m.held && m.inputSettled) return;
    motion.set({ ...m, now, epoch: m.epoch === 0 ? now : m.epoch });
  }, !reduced && previewTime === undefined && autoPlay);
  useEffect(() => { driverRef.current = driver; return () => { driverRef.current = null; }; }, [driver]);
  useEffect(() => { ready.set(Boolean(hand && half && fist)); }, [hand, half, fist, ready]);

  const scheduleReset = useCallback((ticket: number, delay: number) => {
    if (timer.current !== null) clearTimeout(timer.current);
    if (reduced) return;
    timer.current = setTimeout(() => {
      timer.current = null;
      scheduleOnUI((expected: number) => {
        'worklet'; const m = motion.get();
        if (m.id !== expected || m.held || searching.get() || !active.get()) return;
        motion.set(initialMotion(performance.now() / 1000, m.id + 1)); scrollOffset.set(0);
        scheduleOnRN(resetControls); scheduleOnRN(setDriverRunning, autoPlay && !reduced && previewTime === undefined);
      }, ticket);
    }, delay);
  }, [motion, searching, active, scrollOffset, resetControls, setDriverRunning, autoPlay, reduced, previewTime]);
  const scheduleInputSettle = useCallback((ticket: number) => {
    if (settleTimer.current !== null) clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      settleTimer.current = null;
      scheduleOnUI((expected: number) => {
        'worklet'; const m = motion.get(); if (m.id !== expected || !m.held) return;
        motion.set({ ...m, inputSettled: true }); scheduleOnRN(setDriverRunning, false);
      }, ticket);
    }, 2000);
  }, [motion, setDriverRunning]);
  const resetLifecycle = useCallback(() => {
    cancelTimer(); resetControls(); setSearchVisible(false); searching.set(false); scrollOffset.set(0);
    scheduleOnUI(() => { 'worklet'; motion.set(initialMotion(0, motion.get().id + 1)); });
    setDriverRunning(autoPlay && !reduced && previewTime === undefined && AppState.currentState === 'active');
  }, [cancelTimer, resetControls, searching, scrollOffset, motion, setDriverRunning, autoPlay, reduced, previewTime]);
  useEffect(() => { const pending = setTimeout(resetLifecycle, 0); return () => clearTimeout(pending); }, [resetLifecycle]);
  useEffect(() => {
    let mounted = true, revision = 0;
    const update = (value: boolean) => { revision++; reductionEnabled.set(value); setReduced(value); };
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', update), initialRevision = revision;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted && revision === initialRevision) update(value); }).catch(() => {});
    return () => { mounted = false; subscription.remove(); };
  }, [reductionEnabled]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => { active.set(state === 'active'); if (state === 'active') resetLifecycle(); else { cancelTimer(); setDriverRunning(false); scheduleOnUI(() => { 'worklet'; const m = motion.get(); motion.set({ ...m, held: false, id: m.id + 1 }); }); } });
    return () => { subscription.remove(); cancelTimer(); setDriverRunning(false); };
  }, [active, resetLifecycle, cancelTimer, setDriverRunning, motion]);
  useAnimatedReaction(() => frame.get().q[0] <= 0.02 && !(motion.get().held && motion.get().hero), (value, old) => { if (value !== old) scheduleOnRN(setCanScroll, value); });

  const registerAction = useCallback((refresh = false, showSearch = false) => {
    if (previewTime !== undefined) return;
    cancelTimer();
    scheduleOnUI((willRefresh: boolean, willSearch: boolean) => {
      'worklet'; const now = performance.now() / 1000, m = motion.get();
      const current = m.searchSnapshot ?? sample({ ...m, now }, autoPlay, reduced, false, previewTime), id = m.id + 1;
      const drive = append(newDrive(current, now), willRefresh && !reduced ? open(1) : current, now, true);
      motion.set({ ...m, id, now, drive, lastTouch: now, held: false, hero: false, inputSettled: false, searchSnapshot: willSearch ? current : null });
      searching.set(willSearch);
      const origin = driveFrame(drive, now + 2);
      scheduleOnRN(scheduleReset, id, (2 + recoveryDuration(origin)) * 1000);
    }, refresh, showSearch);
    setDriverRunning(!reduced && !showSearch);
  }, [previewTime, cancelTimer, motion, autoPlay, reduced, searching, scheduleReset, setDriverRunning]);
  const saveFood = useCallback((index: number) => { registerAction(); setSaved(current => { const next = new Set(current); if (next.has(index)) next.delete(index); else next.add(index); return next; }); haptic(); }, [registerAction]);
  const openSearch = useCallback(() => { registerAction(false, true); setDraftQuery(query); setSearchVisible(true); }, [registerAction, query]);
  const closeSearch = useCallback((submit: boolean) => { registerAction(); setSearchVisible(false); if (submit) { setCategory(0); setQuery(draftQuery); } }, [registerAction, draftQuery]);
  const showAll = useCallback(() => { registerAction(); setCategory(0); setQuery(''); gridRef.current?.scrollTo({ y: 0, animated: false }); }, [registerAction]);

  const nativeScroll = Gesture.Native();
  /* eslint-disable react-hooks/refs, react-hooks/purity -- RNGH stores these callbacks for later native/UI-thread events. */
  const finishPan = (velocity: number) => {
    'worklet'; const m = motion.get(); if (!m.held) return;
    const now = performance.now() / 1000, current = sample({ ...m, now }, autoPlay, reduced, searching.get(), previewTime);
    const initial = { ...current, qVel: current.qVel.map(v => clamp(v, -700 / 148, 700 / 148)) };
    const q = current.q[0] >= 0.55 || velocity >= 650 ? 1 : 0, target = m.hero ? open(q, current.handAngle) : current;
    const drive = append(newDrive(initial, now), target, now, !m.hero || q >= current.q[0]);
    motion.set({ ...m, now, drive, held: false, hero: false, inputSettled: false, lastTouch: now });
    scheduleOnRN(cancelTimer); scheduleOnRN(setDriverRunning, true);
    scheduleOnRN(scheduleReset, m.id, (2 + recoveryDuration(driveFrame(drive, now + 2))) * 1000);
  };
  const pan = Gesture.Pan().minDistance(6).cancelsTouchesInView(false).enabled(previewTime === undefined && !reduced && !searchVisible).simultaneousWithExternalGesture(nativeScroll)
    .onBegin(event => {
      const now = performance.now() / 1000, m = motion.get(), start = sample({ ...m, now }, autoPlay, reduced, searching.get(), previewTime);
      const hero = scrollOffset.get() <= 0.5 && (event.y - baseY) / scale <= 220 + 148 * Math.max(0, start.q[0]);
      const id = m.id + 1, drive = append(newDrive(start, now), start, now, true);
      motion.set({ ...m, now, id, drive, panStart: start, held: true, hero, inputSettled: false, detent: start.q[0] >= 0.9, lastTouch: now });
      scheduleOnRN(cancelTimer); scheduleOnRN(setDriverRunning, true); scheduleOnRN(scheduleInputSettle, id);
    })
    .onUpdate(event => {
      const now = performance.now() / 1000, m = motion.get(); if (!m.held || !m.drive) return;
      if (!m.hero) { motion.set({ ...m, now, lastTouch: now, inputSettled: false }); scheduleOnRN(scheduleInputSettle, m.id); scheduleOnRN(setDriverRunning, true); return; }
      const distance = event.translationY / scale, start = m.panStart;
      const target = start.q[0] + rubber(start.q[0] + distance / 148) - rubber(start.q[0]), delta = target - start.q[0];
      const blend = smooth(Math.abs(distance) / 60), pose = open(target, start.handAngle + clamp(event.translationX / scale * 0.03, -6, 6));
      pose.handBX += clamp(event.translationX / scale * 0.04, -12, 12);
      const r = rotated(4, 16, pose.handAngle); pose.gatherX = pose.handBX - r[0]; pose.gatherY = pose.handBY - r[1];
      const next = mixed(start, pose, smooth(Math.abs(distance) / 40));
      next.q = start.q.map(value => value + delta + (target - value - delta) * blend); next.q[5] = next.q[4];
      const detent = m.detent || target >= 0.9; if (detent && !m.detent) scheduleOnRN(haptic);
      motion.set({ ...m, now, drive: append(m.drive, next, now, true), lastTouch: now, inputSettled: false, detent });
      scheduleOnRN(scheduleInputSettle, m.id); scheduleOnRN(setDriverRunning, true);
    })
    .onEnd(event => { finishPan(event.velocityY / scale); })
    .onFinalize(() => { finishPan(0); });
  /* eslint-enable react-hooks/refs, react-hooks/purity */

  const headerStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 148 * frame.get().q[0] }] }));
  const titleStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 148 * frame.get().q[1] }] }));
  const categoriesStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 148 * frame.get().q[2] }] }));
  const promoStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 148 * frame.get().q[3] }] }));
  const gridStyle = useAnimatedStyle(() => { const top = gridClipTop(frame.get()); return { top, height: 852 - top }; });
  const contentStyle = useAnimatedStyle(() => ({ height: 463 + 389 - gridClipTop(frame.get()) + extra * 2 + Math.max(0, 148 * frame.get().q[5]) + insets.bottom / scale }));
  const surfaceUniforms = useDerivedValue(() => {
    const f = frame.get(); return { anchor: [f.gatherX, f.gatherY], amplitude: clamp(f.reliefAmplitude, 0, 24), fanWidth: clamp(f.fanWidth, 0, 378), tailY: clamp(f.tailY, 160, 263), contact: clamp(f.grip) };
  });
  const scrollHandler = useAnimatedScrollHandler(event => { scrollOffset.set(event.contentOffset.y); });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 852)), f = frame.get();
    if (hand && half && fist) drawHand(canvas, f, [hand, half, fist], origin.inlet, origin.clipTop);
    return recorder.finishRecordingAsPicture();
  }, [hand, half, fist, origin.inlet, origin.clipTop]);

  return <View style={styles.screen}>
    <StatusBar style="dark" />
    <GestureDetector gesture={pan}>
      <Animated.View style={[styles.stage, { top: baseY, transform: [{ scale }], transformOrigin: [0, 0, 0] }]} pointerEvents={previewTime === undefined ? 'auto' : 'none'} accessibilityActions={[{ name: 'refreshFood', label: 'Refresh food' }]} onAccessibilityAction={() => registerAction(true)}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible={false}><Rect x={0} y={0} width={393} height={300}><Shader source={SURFACE!} uniforms={surfaceUniforms} /></Rect><Picture picture={picture} /></Canvas>
        <Animated.View style={[StyleSheet.absoluteFill, headerStyle]} pointerEvents="box-none">
          <Pressable style={styles.searchHit} onPress={openSearch} accessibilityRole="button" accessibilityLabel="Search food"><Svg width={44} height={44}><Circle cx={20} cy={20} r={11.5} fill="none" stroke="#7B817D" strokeWidth={2} /><Path d="M28 29L37 38" stroke="#7B817D" strokeWidth={2} strokeLinecap="round" /></Svg></Pressable>
          <View style={styles.location} accessible accessibilityLabel="Delivery location, Fable Bay" accessibilityActions={[{ name: 'refreshFood', label: 'Refresh food' }]} onAccessibilityAction={() => registerAction(true)}><FoodText style={styles.locationLabel}>location:</FoodText><FoodText style={styles.locationValue}>Fable Bay</FoodText></View>
          <Image source={ART.avatar} resizeMode="cover" style={styles.avatar} accessible={false} />
          <View style={styles.notification} pointerEvents="none" />
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, titleStyle]} pointerEvents="box-none">
          <FoodText style={styles.heading}>Popular Food</FoodText>
          <Pressable style={styles.allHit} onPress={showAll} accessibilityRole="button" accessibilityLabel="View all food"><FoodText style={styles.allText}>view all</FoodText></Pressable>
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, categoriesStyle]} pointerEvents="box-none">
          {[
            { name: 'Ramen', art: ART.miso, x: 16, width: 117 }, { name: 'Sushi', art: ART.nigiri, x: 139, width: 108 },
            { name: 'Rolls', art: ART.rolls, x: 253, width: 104 }, { name: 'Soup', art: ART.miso, x: 363, width: 104 },
          ].map((item, index) => <Pressable key={item.name} style={[styles.category, { left: item.x, width: item.width }]} onPress={() => { registerAction(); setCategory(index); }} accessibilityRole="button" accessibilityState={{ selected: category === index }}>
            <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: 24, borderColor: 'rgba(16,17,16,0.12)', borderWidth: category === index && index !== 0 ? 0.8 : 0 }]} />
            <Image source={item.art} resizeMode="contain" fadeDuration={0} style={{ position: 'absolute', left: index === 0 ? 14 : 13, top: 12, width: index === 0 ? 22 : 24, height: 20 }} accessible={false} />
            <FoodText numberOfLines={1} style={[styles.categoryText, { width: item.width - 48 }]}>{item.name}</FoodText>
          </Pressable>)}
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, promoStyle]} pointerEvents="none" accessible accessibilityLabel="Discount 50 percent">
          <View style={styles.promoImage}><Image source={ART.promo} resizeMode="cover" fadeDuration={0} style={{ width: 363, height: 121 }} /><LinearGradient colors={['transparent', 'rgba(0,0,0,0.65)', '#000000']} locations={[145 / 363, 182 / 363, 210 / 363]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} /></View>
          <FoodText style={styles.discount}>Discount 50%</FoodText><FoodText style={styles.promoSubcopy}>learn more...</FoodText>
        </Animated.View>
        <GestureDetector gesture={nativeScroll}>
          <Animated.ScrollView ref={gridRef} style={[styles.grid, gridStyle]} scrollEnabled={canScroll} showsVerticalScrollIndicator={false} onScroll={scrollHandler} scrollEventThrottle={16} bounces={false} removeClippedSubviews={false}>
            <Animated.View style={[styles.gridContent, contentStyle]}>
              {[0, 1, 2, 3].map(index => {
                const matches = (!query || NAMES[index].toLowerCase().includes(query.toLowerCase())) && (category === 0 || category === 1 && index === 2 || category === 2 && index === 1 || category === 3 && index === 3);
                return <FoodCard key={index} index={index} pose={frame} extra={extra} matches={matches} saved={saved.has(index)} reduced={reduced} onSave={saveFood} />;
              })}
            </Animated.View>
          </Animated.ScrollView>
        </GestureDetector>
      </Animated.View>
    </GestureDetector>
    <Modal visible={searchVisible} transparent animationType="fade" onRequestClose={() => closeSearch(false)}>
      <View style={styles.modalBackdrop}><View style={styles.searchDialog}>
        <Text style={styles.dialogTitle}>Search food</Text>
        <TextInput value={draftQuery} onChangeText={setDraftQuery} placeholder="Food or ingredients" autoFocus returnKeyType="search" onSubmitEditing={() => closeSearch(true)} style={styles.query} accessibilityLabel="Food or ingredients" />
        <View style={styles.dialogActions}><Pressable onPress={() => closeSearch(false)} style={styles.dialogButton} accessibilityRole="button"><Text>Cancel</Text></Pressable><Pressable onPress={() => closeSearch(true)} style={styles.dialogButton} accessibilityRole="button"><Text style={styles.searchAction}>Search</Text></Pressable></View>
      </View></View>
    </Modal>
  </View>;
}

const type: TextStyle = { color: PRIMARY, includeFontPadding: false };
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE }, stage: { position: 'absolute', left: 0, width: 393, height: 852, backgroundColor: PAGE, overflow: 'hidden' },
  searchHit: { position: 'absolute', left: 22, top: 66, width: 44, height: 44 },
  location: { position: 'absolute', left: 110, top: 69, width: 172, height: 41 },
  locationLabel: { ...type, width: 172, height: 18, fontSize: 13, lineHeight: 18, color: SECONDARY, textAlign: 'center' },
  locationValue: { ...type, marginTop: 1, width: 172, height: 22, fontSize: 17, lineHeight: 22, fontWeight: '500', textAlign: 'center' },
  avatar: { position: 'absolute', left: 333, top: 73, width: 32, height: 32, borderRadius: 16 },
  notification: { position: 'absolute', left: 359.8, top: 70.8, width: 6.4, height: 6.4, borderRadius: 3.2, backgroundColor: '#CA5E67' },
  heading: { ...type, position: 'absolute', left: 16, top: 135, width: 270, height: 36, fontSize: 28, lineHeight: 36, letterSpacing: -0.4 },
  allHit: { position: 'absolute', left: 293, top: 132.5, width: 84, height: 44, justifyContent: 'center' },
  allText: { ...type, width: 84, height: 25, fontSize: 17, lineHeight: 25, textAlign: 'right', color: SECONDARY },
  category: { position: 'absolute', top: 194, height: 45, borderRadius: 24, backgroundColor: '#E5E5E3' },
  categoryText: { ...type, position: 'absolute', left: 47, top: 12, height: 23, fontSize: 18, lineHeight: 23 },
  promoImage: { position: 'absolute', left: 16, top: 254, width: 363, height: 121, backgroundColor: '#060707', borderRadius: 34, overflow: 'hidden' },
  discount: { ...type, position: 'absolute', left: 207, top: 290, width: 162, height: 31, fontSize: 23, lineHeight: 31, letterSpacing: -0.3, color: '#E0E0DE' },
  promoSubcopy: { ...type, position: 'absolute', left: 207, top: 319, width: 155, height: 18, fontSize: 13, lineHeight: 18, color: SECONDARY },
  grid: { position: 'absolute', left: 0, top: 381, width: 393, height: 471, overflow: 'hidden' }, gridContent: { width: 393 },
  foodCard: { position: 'absolute', width: 177, backgroundColor: CARD, borderRadius: 31, boxShadow: [{ offsetX: 0, offsetY: 9, blurRadius: 18, color: 'rgba(0,0,0,0.025)' }, { offsetX: 0, offsetY: 1.5, blurRadius: 3, color: 'rgba(0,0,0,0.04)' }] },
  product: { position: 'absolute' }, nameBox: { position: 'absolute', left: 14, top: 146, width: 151, justifyContent: 'center' },
  foodName: { ...type }, foodPrice: { ...type, position: 'absolute', left: 14, width: 84, height: 25, lineHeight: 25 },
  rating: { ...type, position: 'absolute', left: 136, width: 32, height: 25, lineHeight: 25, color: SECONDARY },
  heartHit: { position: 'absolute', left: 1.5, top: 7.5, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  heartArt: { width: 19, height: 19 }, symbol: { width: 19, height: 19 },
  modalBackdrop: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.28)' },
  searchDialog: { width: 300, padding: 20, borderRadius: 18, backgroundColor: '#F6F6F4' }, dialogTitle: { color: PRIMARY, fontSize: 18, fontWeight: '600', textAlign: 'center' },
  query: { marginTop: 16, height: 40, backgroundColor: '#FFFFFF', borderWidth: StyleSheet.hairlineWidth, borderColor: '#B8BBB8', borderRadius: 7, paddingHorizontal: 10, fontSize: 16, color: PRIMARY },
  dialogActions: { flexDirection: 'row', justifyContent: 'space-around', marginTop: 12 }, dialogButton: { minWidth: 90, minHeight: 44, alignItems: 'center', justifyContent: 'center' }, searchAction: { fontWeight: '600', color: PRIMARY },
});
