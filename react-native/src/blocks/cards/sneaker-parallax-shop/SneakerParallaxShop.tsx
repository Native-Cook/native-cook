import { Feather } from '@expo/vector-icons';
import { BlurMask, Canvas, Group, Oval, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo, AppState, Image, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions,
  type TextStyle,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback,
  useReducedMotion, useSharedValue, type FrameCallback, type SharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

export const LOOP = 7.5;
type Props = { autoPlay?: boolean; previewTime?: number; onBack?: () => void };
type Frame = { q: number; e: number; detail: number };
type Motion = {
  now: number; epoch: number; manual: boolean; from: Frame; target: Frame;
  eventTime: number; duration: number; lastTouch: number; held: boolean; origin: Frame;
};
const REST: Frame = { q: 0, e: 0, detail: 2 };
const INK = '#202A33';
const MUTED = '#A5A09D';
const BACKGROUND = '#F7F0EE';
const NAMES = ['Crimson Lift', 'Dune Runner', 'Prism Court'];
const PRICES = ['₹8,895', '₹11,897', '₹12,995'];
const COLORS = ['#E24C4C', '#FAB960', '#4D80F4'];
const CATALOG_SIZES = [254.06, 256.54, 293.08];
const CATALOG_CENTERS = [{ x: 223.8, y: 403 }, { x: 218.4, y: 410.1 }, { x: 197.8, y: 391.8 }];
const CATALOG_ANGLES = [8.4, 3.5, -31];
const DETAIL_CENTERS = [{ x: 187.66, y: 179.67 }, { x: 185.09, y: 169.01 }, { x: 175.2, y: 172.3 }];
const DETAIL_ANGLES = [29.8, 24.9, -9.6];
const IMAGE_SIZE = 294.57;
const ART = [
  require('./assets/sneaker-red.png'), require('./assets/sneaker-yellow.png'),
  require('./assets/sneaker-gray.png'), require('./assets/sneaker-blue.png'),
  require('./assets/sneaker-olive.png'), require('./assets/sneaker-black.png'),
];
const AnimatedPath = Animated.createAnimatedComponent(Path);

function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}
function smooth(value: number) {
  'worklet';
  const u = clamp(value);
  return u * u * (3 - 2 * u);
}
function spring(progress: number) {
  'worklet';
  if (progress >= 1) return 1;
  const u = Math.max(0, progress), a = 5.15, b = 3.59;
  const end = 1 - Math.exp(-a) * (Math.cos(b) + a / b * Math.sin(b));
  return (1 - Math.exp(-a * u) * (Math.cos(b * u) + a / b * Math.sin(b * u))) / end;
}
function automatic(time: number): Frame {
  'worklet';
  const t = Math.max(0, time) % LOOP;
  return {
    q: smooth((t - 0.67) / 0.48) + smooth((t - 1.82) / 0.53) - 2 * smooth((t - 5.271933) / 1.2),
    e: clamp((t - 3) / 0.5) * (1 - clamp((t - 4.5) / 0.5)),
    detail: 2,
  };
}
function initialMotion(now = 0): Motion {
  'worklet';
  return { now, epoch: now, manual: false, from: REST, target: REST, eventTime: now, duration: 0, lastTouch: now, held: false, origin: REST };
}
function sample(m: Motion, autoPlay: boolean, reduced: boolean, reducedFrame: Frame, previewTime?: number): Frame {
  'worklet';
  if (reduced) return reducedFrame;
  if (!m.manual) return automatic(previewTime ?? (autoPlay ? Math.max(0, m.now - m.epoch) : 0));
  const u = m.duration === 0 ? 1 : spring((m.now - m.eventTime) / m.duration);
  const recovery = m.held || !autoPlay ? 0 : smooth((m.now - m.lastTouch - 2) / 0.55);
  return {
    q: (m.from.q + (m.target.q - m.from.q) * u) * (1 - recovery),
    e: (m.from.e + (m.target.e - m.from.e) * u) * (1 - recovery),
    detail: m.target.detail,
  };
}
function surfacePath(x: number, e: number) {
  'worklet';
  const fast = clamp(2.29 * e), px = x * (1 - fast), y = 205 - 473.9 * e;
  const width = 282 + 111 * fast, radius = 19 * (1 - fast);
  const right = 530 * (1 - e) + 240 * e, left = 530 * (1 - e) + 125 * e;
  const c1x = (px + width * 0.72) * (1 - e) + 289 * e;
  const c2x = (px + width * 0.25) * (1 - e) + 103 * e;
  return `M${px + radius},${y} L${px + width - radius},${y} Q${px + width},${y} ${px + width},${y + radius} L${px + width},${right - radius} Q${px + width},${right} ${px + width - radius},${right} C${c1x},${530 * (1 - e) + 300 * e} ${c2x},${530 * (1 - e) + 287 * e} ${px + radius},${left} Q${px},${left} ${px},${left - radius} L${px},${y + radius} Q${px},${y} ${px + radius},${y} Z`;
}
function lightHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function ArtImage({ index, style }: { index: number; style?: object }) {
  return <Image source={ART[index]} resizeMode="contain" fadeDuration={0} style={[styles.image, style]} accessible={false} />;
}

function NativeIcon({ name, light, filled = false }: { name: 'back' | 'search' | 'heart'; light: boolean; filled?: boolean }) {
  const size = name === 'back' ? 21 : 20;
  const tint = light ? '#FFFFFF' : INK;
  if (Platform.OS === 'ios') {
    const symbol = name === 'back' ? 'arrow.left' : name === 'search' ? 'magnifyingglass' : filled ? 'heart.fill' : 'heart';
    return <SymbolView name={symbol} size={size} weight="light" tintColor={tint} style={{ width: size, height: size }} />;
  }
  return <Feather name={name === 'back' ? 'arrow-left' : name} size={size} color={tint} />;
}

function ProductCard({ index, pose, colorway, selected, enabled, onChoose }: {
  index: number; pose: SharedValue<Frame>; colorway: number; selected: number; enabled: boolean; onChoose: (index: number) => void;
}) {
  const pathProps = useAnimatedProps(() => {
    const f = pose.get(), expanded = index === f.detail ? f.e : 0;
    return { d: surfacePath(18 + 349 * (index - f.q), expanded) };
  });
  const opacity = useAnimatedStyle(() => ({ opacity: index === pose.get().detail ? 1 : 1 - pose.get().e }));
  const labelStyle = useAnimatedStyle(() => {
    const f = pose.get(), expanded = index === f.detail ? f.e : 0;
    return { opacity: 1 - expanded, transform: [{ translateX: 349 * (index - f.q) }, { translateY: -191 * expanded }] };
  });
  const imageStyle = useAnimatedStyle(() => {
    const f = pose.get(), expanded = index === f.detail ? f.e : 0;
    const base = CATALOG_CENTERS[index], target = DETAIL_CENTERS[index];
    const x = (base.x + 349 * (index - f.q) * 0.84) * (1 - expanded) + target.x * expanded;
    const y = base.y * (1 - expanded) + target.y * expanded;
    const size = CATALOG_SIZES[index] + (IMAGE_SIZE - CATALOG_SIZES[index]) * expanded;
    const angle = (CATALOG_ANGLES[index] + 11 * Math.sin(Math.PI * f.q)) * (1 - expanded) + DETAIL_ANGLES[index] * expanded;
    return { transform: [{ translateX: x - IMAGE_SIZE / 2 }, { translateY: y - IMAGE_SIZE / 2 }, { rotate: `${angle}deg` }, { scale: size / IMAGE_SIZE }] };
  });
  const baseAlpha = useAnimatedStyle(() => ({ opacity: index === pose.get().detail && colorway > 0 ? 1 - pose.get().e : 1 }));
  const alternateAlpha = useAnimatedStyle(() => ({ opacity: index === pose.get().detail ? pose.get().e : 0 }));
  const ambient = useDerivedValue(() => {
    const f = pose.get(), e = index === f.detail ? f.e : 0, shoeX = 72 + 349 * (index - f.q) * 0.84;
    const x = (shoeX + 163) * (1 - e) + 183 * e, y = 504 * (1 - e) + 246 * e;
    return Skia.XYWHRect(x - 94, y - 12, 188, 24);
  });
  const contact = useDerivedValue(() => {
    const f = pose.get(), e = index === f.detail ? f.e : 0, shoeX = 72 + 349 * (index - f.q) * 0.84;
    const x = (shoeX + 163) * (1 - e) + 183 * e, y = 504 * (1 - e) + 246 * e;
    return Skia.XYWHRect(x - 74, y - 4, 148, 8);
  });
  const hitStyle = useAnimatedStyle(() => ({ transform: [{ translateX: 349 * (index - pose.get().q) }] }));
  return (
    <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFill, opacity, { zIndex: index === selected ? 3 : index }]}>
      <Svg width={393} height={852} style={StyleSheet.absoluteFill} pointerEvents="none">
        <AnimatedPath animatedProps={pathProps} fill={COLORS[index]} />
      </Svg>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, labelStyle]}>
        <Text style={[styles.productName, { left: 45, top: 233, color: '#FFFFFF' }]}>{NAMES[index]}</Text>
        <Text style={styles.productPrice}>{PRICES[index]}</Text>
        <View style={styles.hairline} />
      </Animated.View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group>
          <Oval rect={ambient} color="rgba(60,51,48,0.15)"><BlurMask blur={12} style="normal" /></Oval>
          <Oval rect={contact} color="rgba(60,51,48,0.16)"><BlurMask blur={3} style="normal" /></Oval>
        </Group>
      </Canvas>
      <Animated.View pointerEvents="none" style={[styles.heroImage, imageStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, baseAlpha]}><ArtImage index={index} /></Animated.View>
        {index === selected && colorway > 0 ? <Animated.View style={[StyleSheet.absoluteFill, alternateAlpha]}><ArtImage index={colorway + 3} /></Animated.View> : null}
      </Animated.View>
      <Animated.View style={[styles.cardHit, hitStyle]} pointerEvents={enabled ? 'auto' : 'none'} accessibilityElementsHidden={!enabled} importantForAccessibility={enabled ? 'auto' : 'no-hide-descendants'}>
        <Pressable style={styles.fill} onPress={() => onChoose(index)} accessibilityRole="button" accessibilityLabel={`${NAMES[index]}, ${PRICES[index]}. Open details`} />
      </Animated.View>
    </Animated.View>
  );
}

export default function SneakerParallaxShop({ autoPlay = false, previewTime, onBack }: Props) {
  const { width, height } = useWindowDimensions();
  const startupReduced = useReducedMotion();
  const [reduced, setReduced] = useState(startupReduced);
  const [favorite, setFavorite] = useState(false), [sizeIndex, setSizeIndex] = useState(3);
  const [filter, setFilter] = useState(0), [added, setAdded] = useState(false), [more, setMore] = useState(false);
  const [colorway, setColorway] = useState(0), [selected, setSelected] = useState(2);
  const [visibility, setVisibility] = useState(1);
  const motion = useSharedValue<Motion>(initialMotion());
  const reducedFrame = useSharedValue<Frame>({ q: 2, e: 1, detail: 2 });
  const reductionEnabled = useSharedValue(startupReduced);
  const active = useSharedValue(true);
  const driverRef = useRef<FrameCallback | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scale = width / 393;
  const cancelTimer = useCallback(() => { if (timer.current !== null) clearTimeout(timer.current); timer.current = null; }, []);
  const setDriverRunning = useCallback((running: boolean) => { driverRef.current?.setActive(running); }, []);
  const resetControls = useCallback(() => {
    setFavorite(false); setSizeIndex(3); setFilter(0); setAdded(false); setMore(false); setColorway(0); setSelected(2);
  }, []);
  const pose = useDerivedValue(() => sample(motion.get(), autoPlay, reduced, reducedFrame.get(), previewTime), [autoPlay, reduced, previewTime]);
  const driver = useFrameCallback(frame => {
    'worklet';
    if (!active.get()) return;
    const m = motion.get(), now = frame.timestamp / 1000;
    motion.set({ ...m, now, epoch: m.epoch === 0 ? now : m.epoch });
  }, !reduced && previewTime === undefined && autoPlay);
  useEffect(() => {
    driverRef.current = driver;
    return () => { driverRef.current = null; };
  }, [driver]);

  const resetScene = useCallback(() => {
    cancelTimer(); resetControls(); reducedFrame.set({ q: 2, e: 1, detail: 2 });
    scheduleOnUI(() => { 'worklet'; motion.set(initialMotion()); });
    setDriverRunning(!reduced && previewTime === undefined && autoPlay && AppState.currentState === 'active');
  }, [cancelTimer, resetControls, reducedFrame, motion, setDriverRunning, reduced, previewTime, autoPlay]);

  const scheduleAfterTouch = useCallback((duration: number, stamp: number) => {
    const current = motion.get();
    if (!current.manual || current.held || current.lastTouch !== stamp) return;
    cancelTimer();
    const delay = autoPlay ? 2.55 : Math.max(0.02, duration + 0.02);
    timer.current = setTimeout(() => {
      timer.current = null;
      scheduleOnUI((expectedStamp: number) => {
        'worklet';
        const m = motion.get();
        if (!m.manual || m.held || m.lastTouch !== expectedStamp) return;
        const now = performance.now() / 1000;
        if (autoPlay) {
          motion.set(initialMotion(now));
          reducedFrame.set({ q: 2, e: 1, detail: 2 });
          scheduleOnRN(resetControls);
          scheduleOnRN(setDriverRunning, !reduced && previewTime === undefined);
        } else {
          motion.set({ ...m, now });
          scheduleOnRN(setDriverRunning, false);
        }
      }, stamp);
    }, delay * 1000);
  }, [cancelTimer, autoPlay, resetControls, reducedFrame, motion, setDriverRunning, reduced, previewTime]);

  useEffect(() => {
    let mounted = true, revision = 0;
    const update = (value: boolean) => { revision++; reductionEnabled.set(value); setReduced(value); };
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', update);
    const initialRevision = revision;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted && revision === initialRevision) update(value); }).catch(() => {});
    return () => { mounted = false; subscription.remove(); };
  }, [reductionEnabled]);
  useEffect(() => {
    // Reinitialize selections after the external preview/playback/motion preference changes.
    const pending = setTimeout(resetScene, 0);
    return () => clearTimeout(pending);
  }, [resetScene]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      active.set(state === 'active'); resetScene();
      if (state !== 'active') setDriverRunning(false);
    });
    return () => { subscription.remove(); cancelTimer(); setDriverRunning(false); };
  }, [active, resetScene, setDriverRunning, cancelTimer]);
  useAnimatedReaction(() => {
    const f = pose.get();
    return (f.e < 0.05 ? 1 : 0) | (f.e > 0.96 ? 2 : 0) | (f.e > 0.5 ? 4 : 0);
  }, (value, previous) => { if (value !== previous) scheduleOnRN(setVisibility, value); });

  const currentFrame = useCallback(() => sample(motion.get(), autoPlay, reduced, reducedFrame.get(), previewTime), [motion, autoPlay, reduced, reducedFrame, previewTime]);
  const interact = useCallback((target: Frame, duration = 0.42) => {
    if (previewTime !== undefined) return;
    if (reduced) {
      const detail = clamp(target.e > 0.5 ? target.detail : Math.round(target.q), 0, 2);
      reducedFrame.set({ q: detail, e: target.e > 0.5 ? 1 : 0, detail });
      setSelected(detail);
    }
    scheduleOnUI((next: Frame, seconds: number) => {
      'worklet';
      const now = performance.now() / 1000, m = motion.get();
      const from = sample({ ...m, now }, autoPlay, reduced, reducedFrame.get(), previewTime);
      motion.set({ ...m, now, manual: true, from, target: next, eventTime: now, duration: seconds, lastTouch: now, held: false });
      scheduleOnRN(scheduleAfterTouch, seconds, now);
    }, target, duration);
    setDriverRunning(!reduced && previewTime === undefined);
  }, [previewTime, reduced, reducedFrame, motion, autoPlay, setDriverRunning, scheduleAfterTouch]);
  const touchControl = useCallback(() => interact(currentFrame(), 0), [interact, currentFrame]);
  const open = useCallback((index: number) => {
    const item = clamp(index, 0, 2);
    if (item !== currentFrame().detail) { setColorway(0); setFavorite(false); setMore(false); setAdded(false); }
    setSelected(item);
    interact({ q: item, e: 1, detail: item }, 0.55);
    if (!reduced) lightHaptic();
  }, [currentFrame, interact, reduced]);
  const choose = useCallback((index: number) => {
    const f = currentFrame();
    if (Math.abs(f.q - index) < 0.05) open(index);
    else interact({ q: index, e: 0, detail: 2 });
  }, [currentFrame, open, interact]);
  const chooseAdjacent = useCallback((direction: number) => {
    const f = currentFrame(), index = (Math.round(f.q) + direction + 3) % 3;
    setSelected(index); interact({ q: index, e: f.e, detail: index });
  }, [currentFrame, interact]);
  const back = useCallback(() => {
    const f = currentFrame();
    if (f.e > 0.5) interact({ q: f.detail, e: 0, detail: f.detail });
    else onBack?.();
  }, [currentFrame, interact, onBack]);

  /* eslint-disable react-hooks/refs, react-hooks/purity -- RNGH registers these callbacks during render; they execute later on the UI thread. */
  const pan = Gesture.Pan().minDistance(6).enabled(!reduced && previewTime === undefined)
    .onStart(() => {
      scheduleOnRN(cancelTimer); scheduleOnRN(setDriverRunning, true);
      const now = performance.now() / 1000, m = motion.get();
      const f = sample({ ...m, now }, autoPlay, reduced, reducedFrame.get(), previewTime);
      motion.set({ ...m, now, manual: true, from: f, target: f, origin: f, held: true, duration: 0, lastTouch: now });
    })
    .onUpdate(event => {
      const now = performance.now() / 1000, m = motion.get(), f = { ...m.origin };
      const dx = event.translationX / scale, dy = event.translationY / scale;
      if (m.origin.e > 0.5 && Math.abs(dy) > Math.abs(dx)) f.e = clamp(m.origin.e - dy / 280);
      else if (m.origin.e < 0.5) {
        const proposed = m.origin.q - dx / 349, edge = clamp(proposed, 0, 2), over = (proposed - edge) * 349;
        f.q = edge + over / (1 + Math.abs(over) / 120) / 349;
      }
      motion.set({ ...m, now, target: f, duration: 0, eventTime: now, lastTouch: now });
    })
    .onEnd(event => {
      const now = performance.now() / 1000, m = motion.get();
      if (!m.held) return;
      const target = { ...m.target };
      if (m.origin.e > 0.5) { target.e = event.translationY / scale > 80 ? 0 : 1; target.q = target.detail; }
      else {
        // Swift predicts the remaining drag, then takes 18% of that projection.
        const projected = event.velocityX / scale * 0.2 * 0.18;
        target.q = clamp(Math.round(target.q - projected / 349), 0, 2);
      }
      const changed = Math.round(target.q) !== Math.round(m.origin.q) || (target.e > 0.5) !== (m.origin.e > 0.5);
      const from = sample({ ...m, now }, autoPlay, reduced, reducedFrame.get(), previewTime);
      motion.set({ ...m, now, held: false, from, target, duration: 0.42, eventTime: now, lastTouch: now });
      scheduleOnRN(scheduleAfterTouch, 0.42, now);
      if (changed && !reductionEnabled.get()) scheduleOnRN(lightHaptic);
    })
    .onFinalize(() => {
      const m = motion.get();
      if (!m.held) return;
      const now = performance.now() / 1000, target = { ...m.target };
      if (m.origin.e > 0.5) { target.e = 1; target.q = target.detail; }
      else target.q = clamp(Math.round(target.q), 0, 2);
      const from = sample({ ...m, now }, autoPlay, reduced, reducedFrame.get(), previewTime);
      motion.set({ ...m, now, held: false, from, target, duration: 0.42, eventTime: now, lastTouch: now });
      scheduleOnRN(scheduleAfterTouch, 0.42, now);
    });
  /* eslint-enable react-hooks/refs, react-hooks/purity */

  const catalogStyle = useAnimatedStyle(() => ({ opacity: 1 - pose.get().e }));
  const detailStyle = useAnimatedStyle(() => {
    const alpha = smooth((pose.get().e - 0.35) / 0.65);
    return { opacity: alpha, transform: [{ translateY: 12 * (1 - alpha) }] };
  });
  const catalogEnabled = (visibility & 1) !== 0, detailEnabled = (visibility & 2) !== 0, light = (visibility & 4) !== 0;
  const inactive = previewTime !== undefined;
  let chipX = 15;
  const chipWidths = [61, 82, 70, 78, 74];

  return (
    <View style={styles.screen}>
      <StatusBar style={light ? 'light' : 'dark'} />
      <GestureDetector gesture={pan}>
        <Animated.View
          style={[styles.stage, { top: Math.max(0, (height - 852 * scale) / 2), transform: [{ scale }], transformOrigin: [0, 0, 0] }]}
          pointerEvents={inactive ? 'none' : 'auto'}
          accessibilityActions={[{ name: 'increment', label: 'Next shoe' }, { name: 'decrement', label: 'Previous shoe' }, { name: 'activate', label: 'Open product details' }]}
          onAccessibilityAction={event => {
            if (event.nativeEvent.actionName === 'activate') open(Math.round(currentFrame().q));
            else chooseAdjacent(event.nativeEvent.actionName === 'decrement' ? -1 : 1);
          }}
        >
          <Animated.View style={[StyleSheet.absoluteFill, styles.catalogLayer, catalogStyle]} pointerEvents={catalogEnabled ? 'box-none' : 'none'} accessibilityElementsHidden={!catalogEnabled} importantForAccessibility={catalogEnabled ? 'auto' : 'no-hide-descendants'}>
            <Text style={styles.heading}>Shoes</Text>
            {['All', 'Court', 'Run', 'Trail', 'Daily'].map((name, index) => {
              const x = chipX; chipX += chipWidths[index] + 9;
              return <Pressable key={name} style={[styles.chipHit, { left: x, width: chipWidths[index] }]} onPress={() => { setFilter(index); interact({ q: index % 3, e: 0, detail: 2 }); }} accessibilityRole="button" accessibilityState={{ selected: filter === index }}>
                <View style={[styles.chip, { backgroundColor: filter === index ? INK : 'transparent', borderWidth: filter === index ? 0 : 1 }]}><Text style={[styles.chipText, { color: filter === index ? '#FFFFFF' : MUTED }]}>{name}</Text></View>
              </Pressable>;
            })}
            <Text style={styles.options}>243 OPTIONS</Text>
            <View style={styles.rowImageOne}><ArtImage index={2} /></View>
            <View style={styles.rowImageTwo}><ArtImage index={3} /></View>
            <Text style={styles.rowTitleOne}>{'Cinder Pace\nLow Runner'}</Text>
            <Text style={styles.rowPriceOne}>₹12,797</Text>
            <Text style={styles.rowTitleTwo}>Lowtide Mesh</Text>
            <Text style={styles.rowPriceTwo}>₹9,995</Text>
          </Animated.View>
          <View style={[StyleSheet.absoluteFill, styles.heroLayer]} pointerEvents="box-none" collapsable={false}>
            {[0, 1, 2].map(index => <ProductCard key={index} index={index} pose={pose} colorway={colorway} selected={selected} enabled={catalogEnabled && !inactive} onChoose={choose} />)}
          </View>
          <Animated.View style={[StyleSheet.absoluteFill, styles.detailLayer, detailStyle]} pointerEvents={detailEnabled ? 'box-none' : 'none'} accessibilityElementsHidden={!detailEnabled} importantForAccessibility={detailEnabled ? 'auto' : 'no-hide-descendants'}>
            <Text style={styles.detailTitle}>{NAMES[selected]}</Text>
            <Text style={styles.detailPrice}>{PRICES[selected]}</Text>
            <Text style={styles.body}>{'Built for the moments that move you most.\nLight on your feet, ready for every turn.\nSoft landings. A little more possibility.'}</Text>
            <Pressable style={styles.moreHit} onPress={() => { setMore(value => !value); touchControl(); }} accessibilityRole="button" accessibilityLabel="More product details"><Text style={styles.moreText}>{more ? 'LESS' : 'MORE'}</Text></Pressable>
            {more ? <Text style={styles.extra}>Choose your pace. Make it yours.</Text> : null}
            {[0, 1, 2].map(slot => <Pressable key={slot} style={[styles.thumbnail, { left: 22 + slot * 99 }]} onPress={() => { setColorway(slot); touchControl(); }} accessibilityRole="button" accessibilityLabel={`${['Original', 'Moss', 'Night'][slot]} colorway`} accessibilityState={{ selected: colorway === slot }}>
              <ArtImage index={slot === 0 ? selected : slot + 3} style={slot === 0 ? { transform: [{ rotate: `${DETAIL_ANGLES[selected]}deg` }] } : undefined} />
              <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: 18, borderColor: INK, borderWidth: colorway === slot ? 1.6 : 0 }]} />
            </Pressable>)}
            <Text style={styles.sizeLabel}>Select Size</Text>
            {Array.from({ length: 8 }, (_, index) => {
              const disabled = index === 5 || index === 7;
              return <Pressable key={index} disabled={disabled} style={[styles.sizeHit, { left: 27 + index % 4 * 90.5, top: 627 + Math.floor(index / 4) * 62 }]} onPress={() => { setSizeIndex(index); touchControl(); }} accessibilityRole="button" accessibilityLabel={`Size UK ${index + 6}`} accessibilityState={{ disabled, selected: sizeIndex === index }}>
                <View style={[styles.sizeTile, { borderWidth: sizeIndex === index ? 1.5 : 0 }]}><Text style={[styles.sizeText, { opacity: disabled ? 0.3 : 1 }]}>{`UK ${index + 6}`}</Text></View>
              </Pressable>;
            })}
            <Pressable style={styles.bag} onPress={() => { setAdded(true); touchControl(); if (!reduced) lightHaptic(); }} accessibilityRole="button"><Text style={styles.bagText}>{added ? 'Added to Bag' : 'Add to Bag'}</Text></Pressable>
          </Animated.View>
          <Pressable style={[styles.navHit, { left: 4 }]} onPress={back} accessibilityRole="button" accessibilityLabel={light ? 'Back to shoes' : 'Back'}><NativeIcon name="back" light={light} /></Pressable>
          <Pressable style={[styles.navHit, { left: 338 }]} onPress={() => {
            if (currentFrame().e > 0.5) { setFavorite(value => !value); touchControl(); }
            else { const next = (filter + 1) % 5; setFilter(next); interact({ q: next % 3, e: 0, detail: 2 }); }
          }} accessibilityRole="button" accessibilityLabel={light ? 'Favorite product' : 'Browse shoes'}><NativeIcon name={light ? 'heart' : 'search'} light={light} filled={favorite} /></Pressable>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const typography: TextStyle = { color: INK, includeFontPadding: false };
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  stage: { position: 'absolute', left: 0, width: 393, height: 852, backgroundColor: BACKGROUND, overflow: 'hidden' },
  catalogLayer: { zIndex: 0 },
  heroLayer: { zIndex: 1 },
  detailLayer: { zIndex: 2 },
  fill: { flex: 1 },
  image: { width: '100%', height: '100%' },
  heading: { ...typography, position: 'absolute', left: 14, top: 96, width: 360, height: 34, fontSize: 27, lineHeight: 34, fontWeight: '600' },
  chipHit: { position: 'absolute', top: 140, height: 44, justifyContent: 'center' },
  chip: { height: 34, borderRadius: 17, borderColor: '#E5DFDC', alignItems: 'center', justifyContent: 'center' },
  chipText: { ...typography, fontSize: 14, fontWeight: '500' },
  options: { ...typography, position: 'absolute', left: 22, top: 582, width: 112, height: 16, fontSize: 12, lineHeight: 16, letterSpacing: 1, color: MUTED },
  rowImageOne: { position: 'absolute', left: 20, top: 620, width: 131, height: 90 },
  rowImageTwo: { position: 'absolute', left: 20, top: 735, width: 131, height: 90 },
  rowTitleOne: { ...typography, position: 'absolute', left: 182, top: 636, width: 191, height: 44, fontSize: 17, lineHeight: 21.3, fontWeight: '500' },
  rowPriceOne: { ...typography, position: 'absolute', left: 182, top: 683, width: 191, height: 19, fontSize: 14, lineHeight: 19, color: MUTED },
  rowTitleTwo: { ...typography, position: 'absolute', left: 182, top: 751, width: 191, height: 24, fontSize: 17, lineHeight: 24, fontWeight: '500' },
  rowPriceTwo: { ...typography, position: 'absolute', left: 182, top: 778, width: 191, height: 19, fontSize: 14, lineHeight: 19, color: MUTED },
  productName: { ...typography, position: 'absolute', width: 240, height: 31, fontSize: 25, lineHeight: 31, fontWeight: '600' },
  productPrice: { ...typography, position: 'absolute', left: 45, top: 269, width: 200, height: 25, fontSize: 19, lineHeight: 25, color: 'rgba(255,255,255,0.64)' },
  hairline: { position: 'absolute', left: 45, top: 301, width: 0.7, height: 179, backgroundColor: 'rgba(255,255,255,0.22)' },
  heroImage: { position: 'absolute', left: 0, top: 0, width: IMAGE_SIZE, height: IMAGE_SIZE },
  cardHit: { position: 'absolute', left: 18, top: 205, width: 282, height: 325, borderRadius: 19 },
  detailTitle: { ...typography, position: 'absolute', left: 27, top: 325, width: 235, height: 31, fontSize: 24, lineHeight: 31, fontWeight: '700' },
  detailPrice: { ...typography, position: 'absolute', left: 292, top: 329, width: 79, height: 25, fontSize: 18, lineHeight: 25, fontWeight: '600', textAlign: 'right' },
  body: { ...typography, position: 'absolute', left: 27, top: 372, width: 340, height: 76, fontSize: 16.5, lineHeight: 23.2, color: MUTED },
  moreHit: { position: 'absolute', left: 321, top: 409, width: 50, height: 44, alignItems: 'center', justifyContent: 'center' },
  moreText: { ...typography, fontSize: 11, fontWeight: '600' },
  extra: { ...typography, position: 'absolute', left: 30, top: 443, width: 260, height: 18, fontSize: 13, lineHeight: 18, textAlign: 'center', color: MUTED },
  thumbnail: { position: 'absolute', top: 469, width: 88, height: 77, borderRadius: 18, borderColor: INK },
  sizeLabel: { ...typography, position: 'absolute', left: 27, top: 590, width: 200, height: 24, fontSize: 16, lineHeight: 24, fontWeight: '600' },
  sizeHit: { position: 'absolute', width: 73, height: 44, justifyContent: 'center' },
  sizeTile: { width: 73, height: 42, borderRadius: 9, borderColor: INK, backgroundColor: '#FFFCFA', alignItems: 'center', justifyContent: 'center' },
  sizeText: { ...typography, fontSize: 14 },
  bag: { position: 'absolute', left: 27, top: 780, width: 344, height: 49, borderRadius: 13, backgroundColor: INK, alignItems: 'center', justifyContent: 'center' },
  bagText: { ...typography, fontSize: 16, fontWeight: '500', color: '#FFFFFF' },
  navHit: { position: 'absolute', top: 54, width: 44, height: 44, alignItems: 'center', justifyContent: 'center', zIndex: 4 },
});
