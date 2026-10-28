import { StatusBar } from 'expo-status-bar';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, {
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { Ellipse, G, Path, Svg } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const LOOP = 8;
const CREAM = '#FFF1EA';
const GOLD = '#FFC66D';
const CIRCUIT = 'M 7.25 82 L 40.6 12 L 69.6 12 L 65.25 43 L 101.5 31 L 111.65 8 L 134.85 24 L 121.8 62 L 81.2 68 L 56.55 90 Z';

const smooth = (value: number) => {
  'worklet';
  const x = Math.max(0, Math.min(1, value));
  return x * x * (3 - 2 * x);
};
const loopPose = (seconds: number) => {
  'worklet';
  return smooth((seconds - 1.4) / 1.6) * (1 - smooth((seconds - 4.8) / 1.6));
};

function resetRelief(pose: SharedValue<number>, loopTime: SharedValue<number>, mode: SharedValue<number>, reduced: boolean, previewTime?: number) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  pose.value = reduced ? 0.7 : previewTime == null ? 0 : loopPose(time);
  loopTime.value = time;
  mode.value = 0;
}

function beginReliefDrag(pose: SharedValue<number>, dragOrigin: SharedValue<number>, mode: SharedValue<number>) {
  'worklet';
  dragOrigin.value = pose.value;
  mode.value = 1;
}

function updateReliefDrag(pose: SharedValue<number>, dragOrigin: SharedValue<number>, translationX: number) {
  'worklet';
  pose.value = Math.max(0, Math.min(1, dragOrigin.value + translationX / 150));
}

function finishReliefDrag(pose: SharedValue<number>, returnFrom: SharedValue<number>, returnStart: SharedValue<number>, mode: SharedValue<number>) {
  'worklet';
  returnFrom.value = pose.value;
  returnStart.value = 0;
  mode.value = 2;
}

function adjustRelief(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet';
  pose.value = value;
  finishReliefDrag(pose, from, elapsed, mode);
}

type Props = {
  autoPlay?: boolean;
  /** A deterministic position in the 8-second loop, for previews and screenshots. */
  previewTime?: number;
};

export function ApexReviewRelief({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  const [rating, setRating] = useState(4);
  const [manualActive, setManualActive] = useState(false);
  const scale = Math.min(width / 393, Math.max(0, height - insets.top - insets.bottom) / 760);
  const pose = useSharedValue(reduced ? 0.7 : previewTime == null ? 0 : loopPose(((previewTime % LOOP) + LOOP) % LOOP));
  const loopTime = useSharedValue(previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP);
  const mode = useSharedValue(0); // 0: loop/rest, 1: dragging, 2: returning
  const dragOrigin = useSharedValue(0);
  const returnStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const finishManual = useCallback(() => setManualActive(false), []);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => setAppActive(state === 'active'));
    return () => subscription.remove();
  }, []);

  const frame = useFrameCallback((info) => {
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (mode.value === 2) {
      returnStart.value += dt;
      if (returnStart.value < 2) {
        pose.value = returnFrom.value;
      } else {
        const progress = (returnStart.value - 2) / 0.45;
        pose.value = returnFrom.value * (1 - smooth(progress));
        if (progress >= 1) {
          pose.value = 0;
          loopTime.value = 0;
          mode.value = 0;
          if (!autoPlay) scheduleOnRN(finishManual);
        }
      }
      return;
    }
    if (mode.value === 0 && !autoPlay) {
      scheduleOnRN(finishManual);
      return;
    }
    if (mode.value === 0 && autoPlay && previewTime == null) {
      loopTime.value = (loopTime.value + dt) % LOOP;
      pose.value = loopPose(loopTime.value);
    }
  }, false);

  useEffect(() => {
    scheduleOnUI(resetRelief, pose, loopTime, mode, reduced, previewTime);
  }, [appActive, autoPlay, loopTime, mode, pose, previewTime, reduced]);

  useEffect(() => {
    frame.setActive(appActive && !reduced && previewTime == null && (autoPlay || manualActive));
  }, [appActive, autoPlay, frame, manualActive, previewTime, reduced]);

  const beginDrag = useCallback(() => {
    setManualActive(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }, []);

  const pan = useMemo(() => Gesture.Pan()
    .minDistance(1)
    .onBegin(() => {
      if (reduced || previewTime != null) return;
      beginReliefDrag(pose, dragOrigin, mode);
      // Starting from the rendered pose means taking over the autoplay never jumps.
      scheduleOnRN(beginDrag);
    })
    .onUpdate((event) => {
      if (reduced || previewTime != null) return;
      updateReliefDrag(pose, dragOrigin, event.translationX);
    })
    .onFinalize(() => {
      if (reduced || previewTime != null || mode.value !== 1) return;
      finishReliefDrag(pose, returnFrom, returnStart, mode);
    }), [beginDrag, dragOrigin, mode, pose, previewTime, reduced, returnFrom, returnStart]);

  const heroStyle = useAnimatedStyle(() => ({
    transform: [
      { perspective: 900 },
      { rotateX: `${pose.value * 14}deg` },
      { rotateY: `${pose.value * -18}deg` },
    ],
  }));
  const backStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: 2 * (8 + pose.value * 9) },
      { translateY: 2 * (11 + pose.value * 8) },
      { rotateZ: `${-12 + pose.value * 7}deg` },
    ],
  }));
  const middleStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: 8 + pose.value * 9 },
      { translateY: 11 + pose.value * 8 },
      { rotateZ: `${-12 + pose.value * 7}deg` },
    ],
  }));
  const faceStyle = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${-12 + pose.value * 7}deg` }] }));
  const artworkStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: -5 }, { translateY: -6 - pose.value * 9 }],
  }));
  const badgeStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: 96 },
      { translateY: -83 - pose.value * 16 },
      { rotateZ: '-16deg' },
      { scale: 1 + pose.value * 0.06 },
    ],
  }));

  const adjust = useCallback((value: number) => {
    if (reduced || previewTime != null) return;
    setManualActive(true);
    scheduleOnUI(adjustRelief, pose, returnFrom, returnStart, mode, value);
    Haptics.selectionAsync();
  }, [mode, pose, previewTime, reduced, returnFrom, returnStart]);

  const rate = useCallback((value: number) => {
    setRating(value);
    setManualActive(true);
    scheduleOnUI(finishReliefDrag, pose, returnFrom, returnStart, mode);
    Haptics.selectionAsync();
  }, [mode, pose, returnFrom, returnStart]);

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <LinearGradient
        colors={['#B91529', '#390911', '#100B10']}
        locations={[0, 0.45, 0.8]}
        start={{ x: 0.5, y: 0 }}
        end={{ x: 0.5, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <View style={[styles.safeArea, { top: insets.top, bottom: insets.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={styles.header}>
            <Text style={styles.flag}>⚑</Text>
            <Text style={styles.headerLabel}>APEX / RACE JOURNAL</Text>
            <Text style={styles.headerNumber}>08</Text>
          </View>
          <Text style={styles.title}>Ember Coast</Text>
          <Text style={styles.subtitle}>ROUND 08  ·  SUNDAY SESSION</Text>

          <GestureDetector gesture={pan}>
            <Animated.View style={[styles.hero, heroStyle]} accessible accessibilityRole="adjustable" accessibilityLabel="Ember Coast circuit relief" accessibilityHint="Drag right to lift the circuit" accessibilityActions={[{ name: 'increment', label: 'Lift circuit' }, { name: 'decrement', label: 'Rest circuit' }]} onAccessibilityAction={event => adjust(event.nativeEvent.actionName === 'increment' ? 1 : 0)}>
              <View style={styles.glow} />
              <Animated.View style={[styles.slab, styles.slabBack, backStyle]}>
                <LinearGradient colors={['#C52739', '#651421']} style={[StyleSheet.absoluteFill, { borderRadius: 44 }]} />
              </Animated.View>
              <Animated.View style={[styles.slab, styles.slabMiddle, middleStyle]}>
                <LinearGradient colors={['#C52739', '#651421']} style={[StyleSheet.absoluteFill, { borderRadius: 44 }]} />
              </Animated.View>
              <Animated.View style={[styles.slab, styles.slabFace, faceStyle]}>
                <LinearGradient colors={['#C52739', '#651421']} style={[StyleSheet.absoluteFill, { borderRadius: 44 }]} />
                <Animated.View style={[styles.emboss, artworkStyle]}>
                  <View style={styles.route}>
                    {Array.from({ length: 9 }, (_, index) => <ReliefCircuitStroke key={index} pose={pose} index={index} />)}
                    <ReliefCircuitStroke pose={pose} index={9} />
                  </View>
                  <Text style={styles.circuitName}>EMBER GP</Text>
                  <Text style={styles.circuitNo}>CIRCUIT NO. 08</Text>
                </Animated.View>
              </Animated.View>
              <Animated.View style={[styles.badge, badgeStyle]}>
                <LinearGradient colors={['#D9394C', '#670F23']} style={[StyleSheet.absoluteFill, { borderRadius: 12 }]} />
                <Text style={styles.badgeStars}>★★★★★</Text>
              </Animated.View>
            </Animated.View>
          </GestureDetector>

          <View style={styles.scoreLine}>
            <Laurel />
            <Text style={styles.score}>4.8</Text>
            <Laurel mirror />
          </View>
          <Text style={styles.tagline}>A weekend worth remembering.</Text>

          <View style={styles.metrics}>
            <Metric value="129" label="RATINGS" />
            <View style={styles.divider} />
            <Metric value="2.4k" label="REVIEWS" />
            <View style={styles.divider} />
            <Metric value="08" label="ROUND" />
          </View>

          <View style={styles.ratingBar} accessibilityLabel="Your rating">
            {[1, 2, 3, 4, 5].map((star) => (
              <RatingStar key={star} star={star} rating={rating} onRate={rate} />
            ))}
          </View>
          <Text style={styles.ratingCaption}>YOUR RATING  /  {rating} OF 5</Text>
          <Text style={styles.footer}>Drag the circuit. Relive the race.</Text>
        </View>
      </View>
    </View>
  );
}

function Laurel({ mirror = false }: { mirror?: boolean }) {
  return <Svg width={16} height={32} viewBox="0 0 16 32" accessibilityElementsHidden>
    <G transform={mirror ? 'translate(16 0) scale(-1 1)' : undefined}>
      <Path d="M14 30 Q1 19 9 2" fill="none" stroke={GOLD} strokeWidth={1.2} />
      {[0, 1, 2, 3, 4].map(i => <G key={i}>
        <Ellipse cx={5 + i * 0.9} cy={6 + i * 4.8} rx={1.8} ry={3.5} fill={GOLD} transform={`rotate(-42 ${5 + i * 0.9} ${6 + i * 4.8})`} />
        <Ellipse cx={10 + i * 0.3} cy={4 + i * 4.8} rx={1.6} ry={3.2} fill={GOLD} transform={`rotate(36 ${10 + i * 0.3} ${4 + i * 4.8})`} />
      </G>)}
    </G>
  </Svg>;
}

function Metric({ value, label }: { value: string; label: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
    </View>
  );
}

function ReliefCircuitStroke({ pose, index }: { pose: SharedValue<number>; index: number }) {
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: index === 9 ? -8.8 - pose.value * 9.6 : -index * (1.1 + pose.value * 1.2) }],
  }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, animatedStyle]}>
      <Svg width={145} height={100} viewBox="0 0 145 100">
        <Path
          d={CIRCUIT}
          fill="none"
          stroke={index === 9 ? 'rgba(255,241,234,0.25)' : index === 8 ? '#F04E59' : '#6A1023'}
          strokeWidth={index === 9 ? 1.5 : 17}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </Svg>
    </Animated.View>
  );
}

function RatingStar({ star, rating, onRate }: { star: number; rating: number; onRate: (value: number) => void }) {
  return (
    <Pressable onPress={() => onRate(star)} accessibilityRole="button" accessibilityLabel={`Rate ${star} stars`} accessibilityState={{ selected: star === rating }} style={styles.starButton}>
      <Text style={[styles.rateStar, { color: star <= rating ? GOLD : 'rgba(255,241,234,0.16)' }]}>★</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#100B10' },
  safeArea: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', left: 30, right: 30, top: 0, height: 44, flexDirection: 'row', alignItems: 'center' },
  flag: { color: CREAM, fontSize: 15, width: 18 },
  headerLabel: { color: CREAM, fontSize: 11, fontWeight: '600', letterSpacing: 2, marginLeft: 3 },
  headerNumber: { color: CREAM, fontSize: 13, fontVariant: ['tabular-nums'], fontFamily: 'monospace', marginLeft: 'auto' },
  title: { position: 'absolute', top: 59, width: 393, textAlign: 'center', color: CREAM, fontSize: 34, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { position: 'absolute', top: 108, width: 393, textAlign: 'center', color: 'rgba(255,241,234,0.65)', fontSize: 11, fontWeight: '500', letterSpacing: 1.7 },
  hero: { position: 'absolute', left: 30, top: 132, width: 333, height: 315, alignItems: 'center', justifyContent: 'center' },
  glow: { position: 'absolute', width: 220, height: 220, borderRadius: 110, backgroundColor: 'rgba(255,76,80,0.25)', opacity: 0.9, shadowColor: '#FF4C50', shadowOpacity: 0.5, shadowRadius: 40 },
  slab: { position: 'absolute', width: 225, height: 225, borderRadius: 44, borderWidth: 1, borderColor: 'rgba(255,241,234,0.07)', shadowColor: '#000', shadowOpacity: 0.25, shadowRadius: 28, shadowOffset: { width: 0, height: 22 }, elevation: 12 },
  slabBack: { borderColor: 'rgba(255,241,234,0.07)' },
  slabMiddle: { borderColor: 'rgba(255,241,234,0.07)' },
  slabFace: { borderColor: 'rgba(255,241,234,0.18)', alignItems: 'center', justifyContent: 'center', overflow: 'visible' },
  emboss: { alignItems: 'center', justifyContent: 'center' },
  route: { width: 145, height: 100, marginBottom: 12, shadowColor: '#000', shadowOpacity: 0.35, shadowRadius: 7, shadowOffset: { width: 0, height: 10 }, elevation: 6 },
  circuitName: { color: '#F2A1A6', fontSize: 22, fontWeight: '900', letterSpacing: 0.2 },
  circuitNo: { color: '#E58A94', marginTop: 12, fontSize: 9, fontWeight: '700', letterSpacing: 2 },
  badge: { position: 'absolute', left: '50%', top: '50%', width: 80, height: 36, marginLeft: -40, marginTop: -18, alignItems: 'center', justifyContent: 'center', borderRadius: 12, borderWidth: 1, borderColor: 'rgba(255,241,234,0.18)', overflow: 'visible', shadowColor: '#000', shadowOpacity: 0.3, shadowRadius: 10, shadowOffset: { width: 0, height: 8 }, elevation: 9 },
  badgeStars: { color: GOLD, fontSize: 12, letterSpacing: 1 },
  scoreLine: { position: 'absolute', top: 452, height: 56, width: 393, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 15 },
  laurel: { color: GOLD, fontSize: 30, transform: [{ scaleX: -1 }] },
  laurelRight: { transform: [{ scaleX: 1 }] },
  score: { color: CREAM, fontSize: 48, fontWeight: '600', letterSpacing: -1 },
  tagline: { position: 'absolute', top: 510, width: 393, color: 'rgba(255,241,234,0.65)', textAlign: 'center', fontSize: 14 },
  metrics: { position: 'absolute', top: 548, left: 25, right: 25, height: 48, flexDirection: 'row', alignItems: 'center' },
  metric: { flex: 1, alignItems: 'center', gap: 5 },
  metricValue: { color: CREAM, fontSize: 24, fontWeight: '500' },
  metricLabel: { color: 'rgba(255,241,234,0.5)', fontSize: 9, fontWeight: '500', letterSpacing: 1.6 },
  divider: { width: 1, height: 32, backgroundColor: 'rgba(255,241,234,0.12)' },
  ratingBar: { position: 'absolute', top: 615, left: 30, width: 333, height: 66, borderRadius: 34, borderWidth: 1, borderColor: 'rgba(255,241,234,0.12)', backgroundColor: 'rgba(0,0,0,0.22)', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 11 },
  starButton: { width: 44, height: 54, alignItems: 'center', justifyContent: 'center' },
  rateStar: { fontSize: 26, includeFontPadding: false },
  ratingCaption: { position: 'absolute', top: 693, width: 393, textAlign: 'center', color: 'rgba(255,241,234,0.5)', fontSize: 10, fontWeight: '500', letterSpacing: 2 },
  footer: { position: 'absolute', bottom: 0, width: 393, height: 25, textAlign: 'center', color: 'rgba(255,241,234,0.5)', fontSize: 12 },
});
