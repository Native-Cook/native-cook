import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { PressScale } from '@/shared/PressScale';
import { SwapText } from '@/shared/SwapText';
import Animated, { Easing, FadeIn, FadeInDown, FadeOut, LinearTransition, ZoomIn, cancelAnimation, interpolate, interpolateColor, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Defs, RadialGradient, Circle, Path, Stop } from 'react-native-svg';

export const LOOP = 10;
const OUT = Easing.bezier(0.23, 1, 0.32, 1);
const STAR_ART = require('./assets/four-point-star.webp');
const HISTORY = [
  ['2:11 PM', 'Plan a weekend by the coast'],
  ['2:41 PM', 'Make time for a long walk'],
  ['3:27 PM', 'Finish the first chapter'],
];
const TODAY = ['Reply to 10 messages', 'Evening run with the river club', 'Record the podcast intro', 'Dentist appointment @ 2:00', 'Call Mira', 'Water the plants', 'Notes for Theo + 30 min review'];

// A laurel sprig drawn as five leaves on a curved stem; mirrored for the right side.
function Laurel({ flip = false }: { flip?: boolean }) {
  const leaves = [[13, 30, -38], [10.5, 23, -22], [9.5, 16, -6], [10.5, 9.5, 12], [13.5, 4, 30]];
  return <Svg width={22} height={36} viewBox="0 0 22 36" style={flip && { transform: [{ scaleX: -1 }] }} accessible={false}>
    <Path d="M16 34C9 28 7 18 13 3" stroke="#B9BCB1" strokeWidth={1.4} fill="none" strokeLinecap="round" />
    {leaves.map(([x, y, a], i) => <Path key={i} d={`M${x} ${y}c-5-1-7-4-7-7 4 0 7 2 7 7Z`} fill="#BFC2B7" transform={`rotate(${a} ${x} ${y})`} />)}
  </Svg>;
}

function HistoryEntry({ stamp, title, index, time, reduced }: { stamp: string; title: string; index: number; time: SharedValue<number>; reduced: boolean }) {
  const motion = useAnimatedStyle(() => ({
    opacity: reduced ? 1 : interpolate(time.get(), [0.72 + index * 0.18, 1.12 + index * 0.18, 9.6, 9.95], [0, 1, 1, 0], 'clamp'),
    transform: [{ translateY: reduced ? 0 : interpolate(time.get(), [0.72 + index * 0.18, 1.12 + index * 0.18], [10, 0], 'clamp') }],
  }));
  return <Animated.View style={[styles.historyRow, motion]}>{index < HISTORY.length - 1 && <View style={styles.rail} />}<View style={styles.stampRow}><View style={styles.dot} /><Text style={styles.stamp}>{stamp}</Text></View><Text maxFontSizeMultiplier={1.8} style={styles.historyTitle}>{title}</Text></Animated.View>;
}

function Star({ size, index, time, reduced }: { size: number; index: number; time: SharedValue<number>; reduced: boolean }) {
  const motion = useAnimatedStyle(() => {
    const t = time.get();
    // Source frames36–90: edge-on → face → edge-on → settled face.
    // Stagger changes the phase of the turn, not just the alpha of a static star.
    const offset = index === 1 ? 0 : index === 0 ? 0.12 : 0.2;
    const local = t - offset;
    const emerge = reduced ? 1 : interpolate(local, [0.48, 0.6, 0.82, 1.4], [0, 0.25, 1, 1], 'clamp');
    const turn = reduced ? 0 : interpolate(local, [0.48, 0.6, 0.82, 1.02, 1.19, 1.43, 1.65], [90, 86, 20, -30, -88, -180, -180], 'clamp');
    const roll = reduced ? 0 : interpolate(local, [0.48, 0.8, 1.03, 1.2, 1.45, 1.65], [18, -28, 28, 12, -4, 0], 'clamp');
    return { opacity: reduced ? 1 : Math.min(1, emerge * 3) * (1 - interpolate(t, [9.6, 9.95], [0, 1], 'clamp')), transform: [{ rotateZ: `${roll}deg` }, { scaleX: reduced ? 1 : Math.max(0.025, Math.abs(Math.cos(turn * Math.PI / 180))) }, { scale: emerge }, { translateY: reduced ? 0 : (1 - emerge) * 12 }] };
  });
  return <Animated.View style={[{ width: size, height: size }, motion]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Image source={STAR_ART} resizeMode="stretch" style={{ position: 'absolute', width: size * 1.29, height: size * 1.06, left: -size * 0.145, top: -size * 0.03 }} />
  </Animated.View>;
}

// The tick lands with a small overshoot; unticking simply lets the ring fade back.
function Check({ checked, reduced }: { checked: boolean; reduced: boolean }) {
  return <View style={styles.check}>
    <View style={[styles.checkRing, checked && { borderColor: '#E96739' }]} />
    {checked && <Animated.View entering={reduced ? FadeIn.duration(120) : ZoomIn.springify().damping(13).stiffness(260)} exiting={FadeOut.duration(120)} style={styles.checkFill}><Ionicons name="checkmark" size={15} color="#FFFFFF" /></Animated.View>}
  </View>;
}

export function EmberDayReview({ autoPlay = false, previewTime, onCommit }: { autoPlay?: boolean; previewTime?: number; onCommit?: (completed: number) => void }) {
  const insets = useSafeAreaInsets();
  const { width, height, fontScale } = useWindowDimensions();
  const reduced = useMotionPreference();
  const [day, setDay] = useState<'yesterday' | 'today'>('yesterday');
  const [review, setReview] = useState(false);
  const [completed, setCompleted] = useState<number[]>([]);
  const [tasks, setTasks] = useState(TODAY);
  const [draft, setDraft] = useState('');
  const addTask = () => {
    const title = draft.trim();
    if (!title) return;
    setTasks(current => current.includes(title) ? current : [...current, title]);
    setDraft(''); setCommitted(false);
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };
  const [committed, setCommitted] = useState(false);
  const [paused, setPaused] = useState(false);
  const [foreground, setForeground] = useState(true);
  const [autoStage, setAutoStage] = useState(0);
  const holding = useRef(false);
  // While the user is typing an action the demo never takes the screen back.
  const editing = useRef(false);
  const reviewTarget = useRef(false);
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const normalizedPreview = previewTime === undefined || !Number.isFinite(previewTime) ? undefined : previewTime >= 0 ? previewTime % LOOP : ((previewTime % LOOP) + LOOP) % LOOP;
  const time = useSharedValue(normalizedPreview ?? (reduced || !autoPlay ? 2 : 0));
  const page = useSharedValue(0);
  const reviewLift = useSharedValue(0);
  const automatic = previewTime !== undefined || (autoPlay && !paused && foreground && !reduced && !review && !committed);
  const visibleDay = automatic ? (autoStage === 2 ? 'today' : 'yesterday') : day;
  const reviewVisible = automatic ? autoStage === 1 : review;
  const pageProgress = useDerivedValue(() => automatic ? interpolate(time.get(), [7, 7.5, 9.25, 9.85], [0, 1, 1, 0], 'clamp') : page.get());
  const reviewProgress = useDerivedValue(() => automatic ? interpolate(time.get(), [4.55, 4.6, 4.7, 4.8, 4.95, 6.03, 6.1, 6.2, 6.35], [0, 0.05, 0.32, 0.8, 1, 1, 0.85, 0.1, 0], 'clamp') : reviewLift.get());
  useAnimatedReaction(() => automatic ? (reviewProgress.get() > 0.5 ? 1 : pageProgress.get() > 0.5 ? 2 : 0) : 0, (next, previous) => { if (next !== previous) scheduleOnRN(setAutoStage, next); });
  const compact = height < 740 || fontScale > 1.35;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => setForeground(state === 'active'));
    return () => subscription.remove();
  }, []);
  useEffect(() => {
    if (normalizedPreview !== undefined) { cancelAnimation(time); time.set(normalizedPreview); return; }
    if (!foreground) { cancelAnimation(time); return; }
    if (reduced || !autoPlay) { cancelAnimation(time); time.set(2); }
    else if (automatic) {
      time.set(0);
      time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear }), -1));
    }
  }, [autoPlay, automatic, foreground, normalizedPreview, reduced, time]);
  useEffect(() => () => { cancelAnimation(time); if (resume.current) clearTimeout(resume.current); }, [time]);

  const touch = useCallback(() => {
    if (resume.current) clearTimeout(resume.current);
    cancelAnimation(time);
    page.set(pageProgress.get());
    reviewLift.set(reviewProgress.get());
    setPaused(true);
    setDay(visibleDay);
    reviewTarget.current = reviewVisible;
    setReview(reviewVisible);
    if (!holding.current) resume.current = setTimeout(() => { if (!editing.current) setPaused(false); }, 2000);
  }, [page, pageProgress, reviewLift, reviewProgress, reviewVisible, time, visibleDay]);
  const touchStart = () => { holding.current = true; touch(); };
  const touchEnd = () => { holding.current = false; const reviewEnd = reviewTarget.current ? 1 : 0; reviewLift.set(reduced ? reviewEnd : withTiming(reviewEnd, { duration: reviewEnd ? 280 : 240, easing: OUT })); page.set(reduced ? (day === 'today' ? 1 : 0) : withSpring(day === 'today' ? 1 : 0, { duration: 400, dampingRatio: 1 })); if (resume.current) clearTimeout(resume.current); resume.current = setTimeout(() => { if (!editing.current) setPaused(false); }, 2000); };
  const showReview = (open: boolean) => {
    touch();
    reviewTarget.current = open;
    setReview(open);
    reviewLift.set(reduced ? (open ? 1 : 0) : withTiming(open ? 1 : 0, { duration: open ? 280 : 240, easing: OUT }));
  };
  const select = (next: 'today' | 'yesterday') => {
    touch();
    setDay(next);
    page.set(reduced ? (next === 'today' ? 1 : 0) : withSpring(next === 'today' ? 1 : 0, { duration: 400, dampingRatio: 1 }));
    if (next === 'yesterday') time.set(reduced ? 2 : withTiming(2, { duration: 650, easing: OUT }));
    void Haptics.selectionAsync();
  };
  const yesterdayStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 - pageProgress.get() : 1, transform: [{ translateX: reduced ? 0 : -pageProgress.get() * width }] }));
  const todayStyle = useAnimatedStyle(() => ({ opacity: reduced ? pageProgress.get() : 1, transform: [{ translateX: reduced ? 0 : (1 - pageProgress.get()) * width }] }));
  const daySurfaceStyle = useAnimatedStyle(() => ({ borderRadius: reviewProgress.get() > 0 ? 30 : 0, opacity: reduced ? 1 - reviewProgress.get() : 1, transform: [{ translateY: reduced ? 0 : reviewProgress.get() * height * 0.752 }, { scale: reduced ? 1 : 1 - 0.08 * reviewProgress.get() }], transformOrigin: 'top center' }));
  const summaryStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 : interpolate(time.get(), [0.85, 1.4], [0, 1], 'clamp') * (1 - interpolate(time.get(), [9.6, 9.95], [0, 1], 'clamp')), transform: [{ translateY: reduced ? 0 : interpolate(time.get(), [0.85, 1.4], [10, 0], 'clamp') }] }));

  const heroStyle = useAnimatedStyle(() => ({ height: (compact ? 200 : 262) * (reduced ? 1 : interpolate(time.get(), [0, 0.25, 0.4, 0.6, 0.8, 9.6, 10], [0, 0, 0.62, 0.94, 1, 1, 0], 'clamp')) }));
  const followingStyle = useAnimatedStyle(() => ({ transform: [{ translateY: reduced ? 0 : (compact ? 200 : 262) * (interpolate(time.get(), [0, 0.25, 0.4, 0.6, 0.8, 9.6, 10], [0, 0, 0.62, 0.94, 1, 1, 0], 'clamp') - 1) }] }));
  const haloStyle = useAnimatedStyle(() => ({ opacity: reduced ? 0 : interpolate(time.get(), [0.3, 0.5, 0.8, 1.45], [0, 0.6, 0.35, 0], 'clamp'), transform: [{ scale: interpolate(time.get(), [0.3, 0.7, 1.4], [0.2, 0.85, 1.2], 'clamp') }] }));
  const dayContentStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 : interpolate(reviewProgress.get(), [0, 0.4, 0.85, 1], [1, 1, 0.15, 0], 'clamp') }));
  const topTintStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(reviewProgress.get(), [0.65, 1], ['#FFFFFF00', '#FFFFFF']) }));
  const glowStyle = useAnimatedStyle(() => ({ opacity: (reduced ? 1 : interpolate(time.get(), [0.4, 0.9, 9.6, 10], [0, 1, 1, 0], 'clamp')) * (1 - pageProgress.get()) * (1 - reviewProgress.get()), transform: [{ translateX: reduced ? 0 : -pageProgress.get() * width }, { scale: reduced ? 1 : interpolate(time.get(), [0.25, 0.8], [0.6, 1], 'clamp') }] }));
  const headerContentStyle = useAnimatedStyle(() => ({ opacity: 1 - interpolate(reviewProgress.get(), [0.65, 0.9], [0, 1], 'clamp') }));
  const compactDayStyle = useAnimatedStyle(() => ({ opacity: reduced ? 0 : interpolate(reviewProgress.get(), [0.75, 1], [0, 1], 'clamp') }));

  return <View key={`type-${fontScale}`} style={styles.screen}>
    <StatusBar style="dark" />
    <View style={[styles.reviewScreen, StyleSheet.absoluteFill, { paddingTop: insets.top + 4, paddingBottom: insets.bottom + 210 }]} accessibilityElementsHidden={!reviewVisible} importantForAccessibility={reviewVisible ? 'auto' : 'no-hide-descendants'} pointerEvents={reviewVisible ? 'auto' : 'none'}><View style={styles.reviewHeader}><Pressable accessibilityRole="button" onPress={() => showReview(false)} style={styles.iconButton}><Text style={styles.primaryText}>Done</Text></Pressable></View><Text accessibilityRole="header" maxFontSizeMultiplier={1.6} style={styles.reviewTitle}>Review</Text><View style={styles.reviewEmpty}><Text maxFontSizeMultiplier={1.6} style={styles.reviewEmptyTitle}>No unfinished actions{'\n'}for selected day.</Text></View></View>
    <Animated.View style={[styles.screen, { overflow: 'hidden' }, daySurfaceStyle]} pointerEvents={reviewVisible ? 'none' : 'auto'} accessibilityElementsHidden={reviewVisible} importantForAccessibility={reviewVisible ? 'no-hide-descendants' : 'auto'}>
    <Animated.View pointerEvents="none" style={[styles.glow, { top: insets.top + 52 + (compact ? 100 : 131) - 230, left: width / 2 - 230 }, glowStyle]}><Svg width={460} height={460} viewBox="0 0 460 460"><Defs><RadialGradient id="ember-glow"><Stop offset="0" stopColor="#FFD9C2" stopOpacity={0.95} /><Stop offset="0.55" stopColor="#FFE6D7" stopOpacity={0.7} /><Stop offset="1" stopColor="#FFF4EE" stopOpacity={0} /></RadialGradient></Defs><Circle cx={230} cy={230} r={230} fill="url(#ember-glow)" /></Svg></Animated.View>
    <Animated.View style={[styles.topBar, { paddingTop: insets.top - 4 }, topTintStyle]}>
      <Animated.View style={[{ flex: 1, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, headerContentStyle]}>
      <View style={styles.iconButton} />
      <View style={[styles.badge, visibleDay === 'today' && styles.badgeToday]}>{visibleDay === 'today' && <Text maxFontSizeMultiplier={1.4} style={styles.badgeText}>3 in Review</Text>}</View>
      <Pressable accessibilityRole="button" accessibilityLabel="Review unfinished actions" onPress={() => showReview(true)} hitSlop={8} style={styles.iconButton}><View style={styles.moreDisc}><Ionicons name="ellipsis-horizontal" size={15} color="#E8693A" /></View></Pressable>
      </Animated.View>
    </Animated.View>
    <Animated.View style={[styles.viewport, dayContentStyle]}>
      <Animated.View style={[styles.panel, yesterdayStyle]} pointerEvents={visibleDay === 'yesterday' ? 'auto' : 'none'} accessibilityElementsHidden={visibleDay !== 'yesterday'} importantForAccessibility={visibleDay === 'yesterday' ? 'auto' : 'no-hide-descendants'}>
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={visibleDay === 'yesterday' && !reviewVisible}><ScrollView contentContainerStyle={{ paddingBottom: insets.bottom + 96 }} showsVerticalScrollIndicator={false} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd} onScrollBeginDrag={touchStart} onScrollEndDrag={touchEnd} onMomentumScrollBegin={touchStart} onMomentumScrollEnd={touchEnd}>
          <View style={{ height: compact ? 200 : 262 }}>
          <Animated.View style={[styles.hero, { position: 'absolute', top: 0, left: 0, right: 0 }, heroStyle]}>
            <Animated.View pointerEvents="none" style={[styles.halo, haloStyle]}><Svg width={300} height={300} viewBox="0 0 300 300"><Defs><RadialGradient id="ember-halo"><Stop offset="0.5" stopColor="#FF9C4D" stopOpacity={0} /><Stop offset="0.73" stopColor="#FF9C4D" stopOpacity={0.5} /><Stop offset="1" stopColor="#FF9C4D" stopOpacity={0} /></RadialGradient></Defs><Circle cx={150} cy={150} r={150} fill="url(#ember-halo)" /></Svg></Animated.View>
            <View style={styles.starSmall}><Star size={46} index={0} time={time} reduced={reduced} /></View>
            <View style={styles.starMain}><Star size={138} index={1} time={time} reduced={reduced} /></View>
            <View style={styles.starRight}><Star size={80} index={2} time={time} reduced={reduced} /></View>
          </Animated.View>
          </View>
          <Animated.View style={followingStyle}>
          <View style={[styles.dateRow, fontScale > 1.4 ? { flexWrap: 'wrap', gap: 8 } : { justifyContent: 'center', paddingHorizontal: 0, overflow: 'hidden' }]}>
            {fontScale <= 1.4 && <Text maxFontSizeMultiplier={1.5} style={[styles.dateHint, { position: 'absolute', right: width * 0.835, fontSize: 24 }]}>Oct 24</Text>}
            <Pressable accessibilityRole="tab" accessibilityState={{ selected: true }} onPress={() => select('yesterday')} style={[styles.dateButton, fontScale > 1.4 && { flexBasis: '100%' }]}><Text maxFontSizeMultiplier={1.5} style={[styles.dateActive, fontScale <= 1.4 && { fontSize: 35 }]}>Yesterday</Text></Pressable>
            <Pressable accessibilityRole="tab" accessibilityState={{ selected: false }} onPress={() => select('today')} style={[styles.dateButton, fontScale <= 1.4 && { position: 'absolute', left: width * 0.83 }]}><Text maxFontSizeMultiplier={1.5} style={[styles.dateInactive, fontScale <= 1.4 && { fontSize: 28 }]}>Today</Text></Pressable>
          </View>
          <Animated.View style={[styles.summary, fontScale > 1.4 && { flexDirection: 'column', gap: 12 }, summaryStyle]}>
            <View style={styles.summarySide}><Text style={styles.smallValue}>42 min</Text><Text style={styles.smallLabel}>well spent</Text></View>
            <View style={styles.summaryMiddle}><Laurel /><View><Text maxFontSizeMultiplier={1.6} style={styles.total}>✓ 3</Text><Text style={styles.smallLabel}>completed</Text></View><Laurel flip /></View>
            <Pressable style={styles.summarySide} accessibilityRole="button" accessibilityLabel="Review zero unfinished actions" onPress={() => showReview(true)}><Text style={styles.smallValue}>0 open</Text><Text style={styles.smallLabel}>to review ›</Text></Pressable>
          </Animated.View>
          <View style={styles.history}>{HISTORY.map(([stamp, title], index) => <HistoryEntry key={title} stamp={stamp} title={title} index={index} time={time} reduced={reduced} />)}</View>
          </Animated.View>
        </ScrollView></ProgressiveBlurViewport>
      </Animated.View>
      <Animated.View style={[styles.panel, todayStyle]} pointerEvents={visibleDay === 'today' ? 'auto' : 'none'} accessibilityElementsHidden={visibleDay !== 'today'} importantForAccessibility={visibleDay === 'today' ? 'auto' : 'no-hide-descendants'}>
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={visibleDay === 'today' && !reviewVisible}><ScrollView automaticallyAdjustKeyboardInsets keyboardShouldPersistTaps="handled" contentContainerStyle={{ paddingHorizontal: 24, paddingBottom: 170 + insets.bottom }} showsVerticalScrollIndicator={false} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd} onScrollBeginDrag={touchStart} onScrollEndDrag={touchEnd} onMomentumScrollBegin={touchStart} onMomentumScrollEnd={touchEnd}>
          <View style={[styles.dateRow, styles.todayTabs, fontScale > 1.4 && { flexWrap: 'wrap' }]}>{fontScale <= 1.4 && <Pressable accessibilityRole="tab" accessibilityLabel="Yesterday" onPress={() => select('yesterday')} style={[styles.dateButton, styles.peekLeft]}><Text numberOfLines={1} maxFontSizeMultiplier={1.5} style={styles.todayTabMuted}>Yesterday</Text></Pressable>}{fontScale > 1.4 && <Pressable accessibilityRole="tab" accessibilityLabel="Yesterday" onPress={() => select('yesterday')} style={styles.dateButton}><Text maxFontSizeMultiplier={1.5} style={styles.todayTabMuted}>Yesterday</Text></Pressable>}<Text accessibilityRole="tab" accessibilityState={{ selected: true }} maxFontSizeMultiplier={1.5} style={styles.todayTab}>Today</Text><Text numberOfLines={1} maxFontSizeMultiplier={1.5} style={styles.todayTabMuted}>Tomorrow</Text><Text numberOfLines={1} maxFontSizeMultiplier={1.5} style={styles.todayTabMuted}>Wednesday</Text></View>
          {tasks.map((title, index) => <Animated.View key={title} entering={index >= TODAY.length && !reduced ? FadeInDown.duration(320).easing(OUT).withInitialValues({ opacity: 0, transform: [{ translateY: 10 }] }) : undefined} layout={reduced ? undefined : LinearTransition.duration(240).easing(OUT)}><PressScale scale={0.98} reduced={reduced} accessibilityRole="checkbox" accessibilityLabel={title} accessibilityState={{ checked: completed.includes(index) }} onPress={() => { touch(); setCommitted(false); setCompleted(current => current.includes(index) ? current.filter(value => value !== index) : [...current, index]); void Haptics.selectionAsync(); }} style={styles.task}><Check checked={completed.includes(index)} reduced={reduced} /><Text style={[styles.taskText, completed.includes(index) && styles.taskDone]}>{title}</Text></PressScale></Animated.View>)}
          {committed && <Text accessibilityLiveRegion="polite" style={styles.saved}>Committed. Small steps count.</Text>}
        </ScrollView></ProgressiveBlurViewport>
      </Animated.View>
    </Animated.View>
    <Animated.View pointerEvents="none" style={[styles.collapsedHandle, compactDayStyle]} />
    <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.collapsedDay, compactDayStyle]}><Text style={[styles.reviewNoticeText, { fontSize: 16 }]}>Showing unfinished for selected day</Text><Text style={styles.collapsedDayText}>Yesterday ›</Text></Animated.View>
    <LinearGradient pointerEvents="box-none" colors={visibleDay === 'today' ? ['#FFFFFF00', '#FFF1EA', '#FFD9C7'] : ['#FFFFFF00', '#FFFFFF', '#FFFFFF']} style={[styles.footer, { paddingBottom: insets.bottom + 12 }, visibleDay === 'today' && { paddingTop: 44 }]}>
      {visibleDay === 'today' && <View style={styles.addAction}><Ionicons name="add" size={20} color="#C9C2BE" /><TextInput value={draft} onChangeText={setDraft} onFocus={() => { editing.current = true; touch(); }} onBlur={() => { editing.current = false; touch(); }} placeholder="Add Action" maxFontSizeMultiplier={1.6} placeholderTextColor="#C9C2BE" returnKeyType="done" blurOnSubmit={false} onSubmitEditing={addTask} accessibilityLabel="Add an action for today" style={styles.addInput} /><View style={{ width: 20 }} /></View>}
      <Pressable accessibilityRole="button" accessibilityLabel={visibleDay === 'yesterday' ? 'Today' : committed ? 'Committed' : 'Commit today'} onPress={() => { if (visibleDay === 'yesterday') select('today'); else { touch(); setCommitted(true); onCommit?.(completed.length); void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); } }} style={({ pressed }) => [styles.primary, { opacity: pressed ? 0.6 : 1 }]}>{visibleDay === 'yesterday' ? <View style={styles.dayBadge}><Text style={styles.dayBadgeText}>25</Text></View> : <Ionicons name={committed ? 'lock-closed' : 'lock-open'} size={17} color="#E8693A" />}<SwapText maxFontSizeMultiplier={1.6} style={styles.primaryText} reduced={reduced} text={visibleDay === 'yesterday' ? 'Today' : committed ? 'Committed' : 'Commit'} />{visibleDay === 'yesterday' && <Ionicons name="arrow-forward" size={17} color="#DC6539" />}</Pressable>
    </LinearGradient>
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' }, topBar: { paddingHorizontal: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, glow: { position: 'absolute', width: 460, height: 460 }, badge: { minWidth: 36, height: 6, borderRadius: 3, backgroundColor: '#E9E9EC', alignItems: 'center', justifyContent: 'center' }, badgeToday: { height: undefined, minHeight: 20, paddingVertical: 2, borderRadius: 10, paddingHorizontal: 9, backgroundColor: '#FFEDE5' }, badgeText: { fontSize: 10, fontWeight: '600', color: '#E8693A' }, moreDisc: { width: 28, height: 28, borderRadius: 14, backgroundColor: '#F2F2F4', alignItems: 'center', justifyContent: 'center' }, dayBadge: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#E8693A', alignItems: 'center', justifyContent: 'center' }, dayBadgeText: { color: '#FFFFFF', fontSize: 9, fontWeight: '700', fontVariant: ['tabular-nums'] }, todayTabs: { paddingHorizontal: 0, gap: 16, marginTop: -8, overflow: 'visible' }, todayTab: { fontSize: 34, fontWeight: '700', color: '#1F201E', letterSpacing: -0.8 }, todayTabMuted: { fontSize: 34, fontWeight: '700', color: '#BDBDBB', letterSpacing: -0.8 }, peekLeft: { position: 'absolute', right: '100%', marginRight: 14 }, iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' }, viewport: { flex: 1, overflow: 'hidden' }, panel: { ...StyleSheet.absoluteFill }, hero: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }, halo: { position: 'absolute', width: 300, height: 300 }, collapsedHandle: { position: 'absolute', top: 12, alignSelf: 'center', width: 32, height: 3, borderRadius: 2, backgroundColor: '#D9D8D4' }, collapsedDay: { position: 'absolute', top: 30, alignSelf: 'center', alignItems: 'center', gap: 6 }, collapsedDayText: { color: '#E8693A', fontSize: 15, fontWeight: '600' }, starMain: { transform: [{ translateX: 0 }, { translateY: -3 }] }, starSmall: { position: 'absolute', left: '23%', top: '25%' }, starRight: { position: 'absolute', right: '17%', top: '47%' }, dateRow: { flexDirection: 'row', alignItems: 'center', gap: 20, paddingHorizontal: 24, minHeight: 64 }, dateButton: { minHeight: 48, justifyContent: 'center' }, dateHint: { color: '#77746F', fontSize: 17 }, dateActive: { fontSize: 24, fontWeight: '700', color: '#292A27', letterSpacing: -0.8 }, dateInactive: { color: '#74796F', fontSize: 21, fontWeight: '600' }, summary: { marginHorizontal: 16, borderRadius: 20, paddingVertical: 26, paddingHorizontal: 8, flexDirection: 'row', alignItems: 'center', backgroundColor: '#FFF', boxShadow: '0px 4px 20px rgba(97,77,58,0.08)' }, summarySide: { flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 48 }, summaryMiddle: { flex: 1.3, flexDirection: 'row', justifyContent: 'center', gap: 7, alignItems: 'center' }, smallValue: { fontSize: 12, color: '#70766A', fontWeight: '500' }, smallLabel: { fontSize: 10, color: '#73796E', textAlign: 'center', marginTop: 4 }, total: { fontSize: 24, fontWeight: '600', color: '#4A4D45', textAlign: 'center' },  history: { paddingHorizontal: 28, paddingTop: 14, gap: 20 }, historyRow: { gap: 5 }, historyTitleIndent: {}, stampRow: { flexDirection: 'row', alignItems: 'center', gap: 5 }, stamp: { fontSize: 11, color: '#8C8C88', letterSpacing: 0.5, marginLeft: 5, fontVariant: ['tabular-nums'] }, dot: { width: 5, height: 5, borderRadius: 3, backgroundColor: '#F07951' }, rail: { position: 'absolute', left: 2, top: 9, bottom: -27, width: 1, backgroundColor: '#F4B59B' }, historyTitle: { fontSize: 16, lineHeight: 22, color: '#2E2F2C', marginLeft: 10 }, footer: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingTop: 28, alignItems: 'center' }, primary: { minHeight: 48, paddingHorizontal: 24, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center' }, primaryText: { fontSize: 14, color: '#D66235', fontWeight: '600' }, eyebrow: { fontSize: 10, fontWeight: '600', letterSpacing: 1.5, color: '#AD8A75', marginTop: 24 }, todayIntro: { fontSize: 14, color: '#72786D', marginBottom: 20 }, task: { minHeight: 50, flexDirection: 'row', alignItems: 'center', gap: 12, borderRadius: 14, padding: 8 }, check: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center' }, checkRing: { position: 'absolute', width: 28, height: 28, borderRadius: 14, borderWidth: 1.5, borderColor: '#E3E3E6', backgroundColor: '#EFEFF1' }, checkFill: { width: 28, height: 28, borderRadius: 14, backgroundColor: '#E96739', alignItems: 'center', justifyContent: 'center' }, addAction: { alignSelf: 'stretch', marginHorizontal: 12, marginBottom: 6, minHeight: 54, borderRadius: 27, borderWidth: 2, borderColor: '#FFC4A8', backgroundColor: '#FFFFFF', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 8, boxShadow: '0px 0px 14px rgba(255,140,90,0.28)' }, addInput: { flex: 1, fontSize: 15, lineHeight: 20, color: '#4A4F46', paddingVertical: 0, height: 50, textAlign: 'center' }, taskText: { flex: 1, fontSize: 17, lineHeight: 23, color: '#2E2F2C' }, taskDone: { textDecorationLine: 'line-through', color: '#A2A79C' }, saved: { paddingTop: 24, fontSize: 14, lineHeight: 21, color: '#C75B30' }, reviewScreen: { flex: 1, backgroundColor: '#F4F3F5', paddingHorizontal: 24 }, reviewHeader: { flexDirection: 'row', justifyContent: 'flex-end', marginRight: -12 }, reviewTitle: { fontSize: 30, lineHeight: 36, fontWeight: '700', color: '#1F201E' }, reviewEmpty: { flex: 1, justifyContent: 'center', alignItems: 'center' }, reviewEmptyTitle: { fontSize: 25, lineHeight: 38, color: '#C4C4C8', textAlign: 'center' }, reviewCopy: { color: '#70756B', fontSize: 14, lineHeight: 21, textAlign: 'center', maxWidth: 260 }, reviewNoticeText: { fontSize: 13, lineHeight: 18, color: '#72776D', textAlign: 'center' }, reviewPill: { paddingVertical: 16, paddingHorizontal: 20, borderRadius: 24, backgroundColor: '#FFFFFF', alignItems: 'center' },
});
