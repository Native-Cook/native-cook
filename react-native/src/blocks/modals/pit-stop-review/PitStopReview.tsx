import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, Keyboard, Modal, Platform, Pressable, ScrollView, Share, StatusBar, StyleSheet, Text, TextInput, View, useWindowDimensions, type LayoutChangeEvent, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Svg, { Path } from 'react-native-svg';
import Animated, { cancelAnimation, runOnJS, runOnUI, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { graphemes, limitNote, PitStopReviewController, planQuietPanel, planScene, type Plan } from './controller';
import { M, ctaScale, moving, panel as panelAt, starLanded, starScale } from './motion';
import { PitStopReviewHero } from './PitStopReviewHero';
import { PitStopReviewLights } from './PitStopReviewLights';
import { PitStopReviewTabBar, type TabKey } from './PitStopReviewTabBar';
import { ASCENT, avatar, BAR_CENTER, bento, clubPass, colors, FONT_FILES, fonts, hero, heroText, pager, pageTone, stars as ST } from './theme';
import { Bento, Rail, StatsCard } from './PitStopReviewBento';
import { useFonts } from 'expo-font';

const { ink: INK, muted: MUTED, bg: BG, panel: PANEL, violet: VIOLET, orange: ORANGE, yellow: YELLOW } = colors;
const PROFILE = require('./assets/PitStopReview-profile.jpg');
type Tab = TabKey;
type Destination = 'pass' | 'profile' | 'calendar' | 'reflex' | 'customize' | 'laps' | 'pitcast' | 'results' | 'circuits' | null;
const scaleFor = (width: number) => width / 402;
/** The stats card sits 12 pt under the third tile, as on the pin (and in the sibling Paddock block). */
const statsTop = bento.top + 220;
/** Seconds after the share UI closes during which a tap that dismissed it is not treated as an editor tap. */
const SHARE_TOUCH_GUARD = 0.45;
/** The composer's star path (outline star in the 28 pt box, radius 11, inner ratio 0.46). */
const STAR_D = (() => {
  const pts: string[] = [];
  for (let n = 0; n < 10; n++) { const a = -Math.PI / 2 + (n * Math.PI) / 5, r = n % 2 === 0 ? ST.radius : ST.radius * 0.46; pts.push(`${(14 + Math.cos(a) * r).toFixed(3)},${(14 + Math.sin(a) * r).toFixed(3)}`); }
  return `M ${pts.join(' L ')} Z`;
})();
/** Badge sparkle: a full four-point star (pin: 8.0 × 8.7 pt), drawn in a unit box. */
const SPARKLE_D = 'M 0.5 0 Q 0.59 0.41 1 0.5 Q 0.59 0.59 0.5 1 Q 0.41 0.59 0 0.5 Q 0.41 0.41 0.5 0 Z';
const REST_PLAN: Plan = { mode: 'static', anchor: 0, startedAt: 0, segs: [], s: 0, quiet: false, qFrom: 0, qTo: 0, qStart: -1 };
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

function tickClock(clock: SharedValue<number>, active: SharedValue<boolean>, offset: SharedValue<number>) { 'worklet'; if (active.get()) clock.set(performance.now() / 1000 + offset.get()); }
function updateClock(clock: SharedValue<number>, active: SharedValue<boolean>, plan: SharedValue<Plan>, offset: SharedValue<number>, time: number, playing: boolean, next: Plan) {
  'worklet';
  offset.set(time - performance.now() / 1000); clock.set(time); active.set(playing); plan.set(next);
}

export default function PitStopReview({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions(), insets = useSafeAreaInsets(), s = scaleFor(width), now = () => performance.now() / 1000;
  const [fontsLoaded] = useFonts(FONT_FILES);
  const shareLockUntil = useRef(0);
  const [controller] = useState(() => { const value = new PitStopReviewController(autoPlay, 0); value.setForeground(false, 0); return value; });
  const [, refresh] = useState(0), [wakeToken, setWakeToken] = useState(0), [running, setRunning] = useState(false), [tab, setTab] = useState<Tab>('Home'), [destination, setDestination] = useState<Destination>(null), [rating, setRating] = useState(0), [note, setNote] = useState('');
  const [saved, setSaved] = useState<string[]>([]), [passSaved, setPassSaved] = useState(false), [cards, setCards] = useState([true, true, true]), [laps, setLaps] = useState(3), [circuit, setCircuit] = useState('Harbor'), [name, setName] = useState('Alex Vale');
  const [game, setGame] = useState('Ready'), [reactionAt, setReactionAt] = useState<number | null>(null), [shareBusy, setShareBusy] = useState(false);
  const [feedTop, setFeedTop] = useState(true), [visible, setVisible] = useState(false), [accessibilityReady, setAccessibilityReady] = useState(false), [keyboardHeight, setKeyboardHeight] = useState(0), [quiet, setQuietState] = useState(false);

  const clockOffset = useSharedValue(0), clock = useSharedValue(0), phaseActive = useSharedValue(false), plan = useSharedValue<Plan>(REST_PLAN), ctaPress = useSharedValue(0);
  const scene = useDerivedValue(() => planScene(plan.get(), clock.get()));
  const panelP = useDerivedValue(() => { const p = plan.get(); return p.quiet ? planQuietPanel(p, clock.get()) : panelAt(scene.get()); });

  // Window positions of the five star slots when the composer is open (for the light → star flight).
  const panelBottom = 16 * s + (keyboardHeight || insets.bottom), panelHeight = Math.min(422 * s, height - keyboardHeight - insets.top - 32);
  const [rowY, setRowY] = useState(24 * s + 64 * s);
  const slots = useSharedValue<number[]>([]);
  const slotList = useMemo(() => { const top = height - panelBottom - panelHeight + rowY + ST.height * s / 2; const out: number[] = []; for (let i = 0; i < 5; i++) out.push(width / 2 - (ST.row * s) / 2 + (i + 0.5) * ST.cell * s, top); return out; }, [height, panelBottom, panelHeight, rowY, s, width]);
  useEffect(() => { slots.set(slotList); }, [slots, slotList]);
  const onRowLayout = useCallback((e: LayoutChangeEvent) => setRowY(e.nativeEvent.layout.y), []);

  const onFrame = useCallback(() => { 'worklet'; tickClock(clock, phaseActive, clockOffset); }, [clock, phaseActive, clockOffset]);
  const frameCallback = useFrameCallback(onFrame, false);
  useEffect(() => { frameCallback.setActive(running); return () => frameCallback.setActive(false); }, [frameCallback, running]);
  const wake = useCallback(() => {
    const t = now(), play = controller.cadence(t) === 'animation';
    setVisible(controller.panelVisible(t)); setRating(controller.displayRating(t)); setNote(controller.displayNote(t)); setRunning(play);
    runOnUI(updateClock)(clock, phaseActive, plan, clockOffset, t, play, controller.plan(t)); setWakeToken((x) => x + 1);
  }, [clock, controller, phaseActive, plan, clockOffset]);
  const syncVisibility = useCallback((panelVisible: boolean) => { setVisible(panelVisible || controller.editorOwned); }, [controller]);
  const syncCadence = useCallback(() => wake(), [wake]);
  useAnimatedReaction(() => {
    const p = plan.get(), t = clock.get(), sc = scene.get();
    const active = p.mode === 'path' ? true : p.mode === 'demo' ? moving(sc) : p.quiet ? t < p.qStart + 0.12 : false;
    return { panelVisible: panelP.get() > 0.001, active };
  }, (current, previous) => {
    if (!previous || current.panelVisible !== previous.panelVisible) runOnJS(syncVisibility)(current.panelVisible);
    if (previous?.active && !current.active) runOnJS(syncCadence)();
  }, [syncCadence, syncVisibility]);
  useEffect(() => { const t = now(), delay = controller.nextWakeDelay(t); if (delay === null || delay <= 0) return; const timer = setTimeout(() => { wake(); }, delay * 1000 + 12); return () => clearTimeout(timer); }, [controller, wake, wakeToken]);
  const commit = useCallback(() => { refresh((x) => x + 1); wake(); }, [wake]);
  useEffect(() => { const timer = setTimeout(() => { controller.setForeground(false, now()); wake(); }, 0); return () => { clearTimeout(timer); cancelAnimation(clock); }; }, [clock, controller, wake]);
  useEffect(() => { const timer = setTimeout(() => { controller.setAutoPlay(autoPlay, now()); wake(); }, 0); return () => clearTimeout(timer); }, [autoPlay, controller, wake]);
  useEffect(() => { const sub = AppState.addEventListener('change', (state) => { const active = state === 'active'; controller.setForeground(active && accessibilityReady, now()); if (!active) { setReactionAt(null); setGame('Ready'); } commit(); }); return () => sub.remove(); }, [accessibilityReady, controller, commit]);
  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', (event) => { if (Platform.OS === 'ios') { Keyboard.scheduleLayoutAnimation(event); setKeyboardHeight(event.endCoordinates.height); } controller.setKeyboardPresented(true, now()); commit(); });
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', (event) => { if (Platform.OS === 'ios') Keyboard.scheduleLayoutAnimation(event); setKeyboardHeight(0); controller.setKeyboardPresented(false, now()); commit(); });
    return () => { show.remove(); hide.remove(); };
  }, [controller, commit]);
  const readQuiet = useCallback(async (initial = false) => {
    const [reduce, reader] = await Promise.all([AccessibilityInfo.isReduceMotionEnabled(), AccessibilityInfo.isScreenReaderEnabled()]);
    controller.setQuiet(reduce || reader, now()); setQuietState(reduce || reader);
    if (initial) { setAccessibilityReady(true); controller.setForeground(AppState.currentState !== 'background', now()); }
    commit();
  }, [controller, commit]);
  useEffect(() => { const timer = setTimeout(() => { void readQuiet(true); }, 0); const a = AccessibilityInfo.addEventListener('reduceMotionChanged', () => { void readQuiet(); }); const b = AccessibilityInfo.addEventListener('screenReaderChanged', () => { void readQuiet(); }); return () => { clearTimeout(timer); a.remove(); b.remove(); }; }, [readQuiet]);

  const panelStyle = useAnimatedStyle(() => { const p = panelP.get(), q = plan.get().quiet; return { opacity: p, transform: q ? [] : [{ translateY: (1 - p) * M.panelRise * s }, { scale: M.panelScale + (1 - M.panelScale) * p }] }; });
  const backdropStyle = useAnimatedStyle(() => ({ opacity: panelP.get() }));
  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ scale: ctaScale(scene.get()) * (1 - (1 - M.ctaPressed) * ctaPress.get()) }] }));
  const openEditor = () => { Keyboard.dismiss(); controller.openEditor(now()); setRating(controller.rating); setNote(controller.note); commit(); };
  const closeEditor = () => { Keyboard.dismiss(); controller.closeEditor(now()); commit(); };
  // iOS 26 presents Share.share as a popover whose outside tap also reaches the views below it; the share UI owns that tap, not the editor.
  const closeFromBackdrop = () => { if (controller.sharing || now() < shareLockUntil.current) return; closeEditor(); };
  const updateRating = (value: number) => { const n = Math.max(0, Math.min(5, value)); const changed = !controller.editorOwned || n !== controller.rating; controller.setRating(n, now()); setRating(n); commit(); if (changed) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); };
  const updateNote = (value: string) => { const text = limitNote(value); controller.setNote(text, now()); setNote(text); commit(); };
  const scrollRef = useRef<ScrollView>(null);
  const selectTab = (value: Tab) => {
    scrollRef.current?.scrollTo({ y: 0, animated: false }); Keyboard.dismiss(); setDestination(null); setTab(value); const home = value === 'Home'; if (home) { setFeedTop(true); controller.setFeedAtTop(true, now()); } controller.setHomeSelected(home, now()); commit(); };
  const openDestination = (next: Exclude<Destination, null>) => { Keyboard.dismiss(); controller.setDestinationOwned(true, now()); setDestination(next); if (next === 'reflex') { setGame('Ready'); setReactionAt(null); } commit(); };
  const closeDestination = () => { if (destination === 'reflex') cancelReaction(); setDestination(null); controller.setDestinationOwned(false, now()); commit(); };
  const cancelReaction = () => { setReactionAt(null); setGame('Ready'); };
  const runReaction = () => { if (game.endsWith(' ms')) { setGame('Ready'); return; } if (game === 'Ready' || game === 'Too soon. Try again') { setGame('Get ready…'); } else if (game === 'GO' && reactionAt !== null) { setGame(`${Math.round(performance.now() - reactionAt)} ms`); setReactionAt(null); } else if (game === 'Get ready…') { setGame('Too soon. Try again'); } };
  // Stable handler for the memoised lower half, so a cadence re-render does not re-render the rail and bento.
  const openRef = useRef(openDestination);
  useEffect(() => { openRef.current = openDestination; });
  const openStable = useCallback((d: Exclude<Destination, null>) => openRef.current(d), []);
  const topScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => { const y = e.nativeEvent.contentOffset.y; const top = y <= 4; if (top !== feedTop) { setFeedTop(top); controller.setFeedAtTop(top, now()); commit(); } };
  const share = async () => { if (!controller.beginShare(now())) return; shareLockUntil.current = Infinity; setShareBusy(true); Keyboard.dismiss(); commit(); const trimmed = controller.note.trim(); try { await Share.share({ message: `Turn Nine — ${controller.rating}/5${trimmed ? `\n${trimmed}` : ''}` }); } finally { shareLockUntil.current = now() + SHARE_TOUCH_GUARD; setShareBusy(false); controller.setSharing(false, now()); commit(); } };

  const homeContent = <>
    <LinearGradient colors={pageTone.colors} locations={pageTone.locations} style={{ position: 'absolute', top: 0, width, height: pageTone.height * s }} />
    <View style={[styles.road, { top: hero.top * s, width, height: hero.height * s }]} pointerEvents="none">
      <PitStopReviewHero width={width} scale={s} scene={scene} />
    </View>
    <Pressable accessibilityRole="button" accessibilityLabel="Club Pass" hitSlop={{ top: 8, bottom: 8 }} onPress={() => openDestination('pass')} style={[styles.pass, { left: clubPass.left * s, top: (BAR_CENTER - clubPass.height / 2) * s, height: clubPass.height * s, paddingLeft: clubPass.padLeft * s, paddingRight: clubPass.padRight * s, borderRadius: (clubPass.height * s) / 2, gap: clubPass.gap * s }]}>
      <View style={{ width: clubPass.badge * s, height: clubPass.badge * s, borderRadius: (clubPass.badge * s) / 2, backgroundColor: 'white', alignItems: 'center', justifyContent: 'center' }}>
        <Svg width={clubPass.sparkleW * s} height={clubPass.sparkleH * s} viewBox="0 0 1 1" preserveAspectRatio="none"><Path d={SPARKLE_D} fill={VIOLET} /></Svg>
      </View>
      <Text style={[styles.passText, { fontSize: clubPass.fontSize * s, lineHeight: clubPass.lineHeight * s, letterSpacing: clubPass.letterSpacing * s, fontWeight: clubPass.fontWeight }]}>Club Pass</Text>
    </Pressable>
    <Pressable accessibilityLabel={`${name} profile`} onPress={() => openDestination('profile')} style={[styles.profile, { right: avatar.right * s, top: (BAR_CENTER - avatar.hit / 2) * s, width: avatar.hit * s, height: avatar.hit * s }]}><View style={{ width: avatar.ring * s, height: avatar.ring * s, borderRadius: (avatar.ring * s) / 2, backgroundColor: 'rgba(255,255,255,.16)', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: 'rgba(255,255,255,.22)', alignItems: 'center', justifyContent: 'center' }}><Image source={PROFILE} style={{ width: avatar.photo * s, height: avatar.photo * s, borderRadius: (avatar.photo * s) / 2 }} /></View></Pressable>
    <View pointerEvents="none" style={{ position: 'absolute', left: (heroText.centerX - 201) * s, width, top: (heroText.title.baseline - ASCENT * heroText.title.size) * s }}>
      <Text allowFontScaling={false} style={{ textAlign: 'center', fontFamily: fonts.bold, fontSize: heroText.title.size * s, letterSpacing: heroText.title.tracking * s, color: INK }}>Enjoying Turn Nine?</Text>
    </View>
    <Text allowFontScaling={false} style={{ position: 'absolute', left: (heroText.centerX - heroText.subtitle.width / 2) * s, width: heroText.subtitle.width * s, top: (heroText.subtitle.baseline - (heroText.subtitle.lineHeight - 1.219 * heroText.subtitle.size) / 2 - ASCENT * heroText.subtitle.size) * s, textAlign: 'center', fontFamily: fonts.medium, fontSize: heroText.subtitle.size * s, lineHeight: heroText.subtitle.lineHeight * s, color: INK }}>Share a quick review — help more fans find their next race-day companion.</Text>
    <AnimatedPressable accessibilityRole="button" onPress={openEditor} onPressIn={() => ctaPress.set(withTiming(1, { duration: 90 }))} onPressOut={() => ctaPress.set(withTiming(0, { duration: 160 }))} style={[styles.write, { left: (heroText.centerX - heroText.cta.width / 2) * s, top: heroText.cta.top * s, width: heroText.cta.width * s, height: heroText.cta.height * s, borderRadius: (heroText.cta.height / 2) * s }, ctaStyle]}><Text allowFontScaling={false} style={{ fontFamily: fonts.semibold, fontSize: heroText.cta.size * s, letterSpacing: heroText.cta.tracking * s, color: '#080708' }}>Write a review</Text></AnimatedPressable>
    <View style={[styles.pager, { top: pager.top * s, height: pager.height * s, gap: pager.gap * s }]}><View style={[styles.pagerActive, { width: pager.active * s, height: 4 * s }]} /><View style={[styles.pagerDot, { width: pager.dot * s, height: pager.dot * s }]} /><View style={[styles.pagerDot, { width: pager.dot * s, height: pager.dot * s }]} /></View>
    <Rail s={s} width={width} onOpen={openStable} />
    <Bento s={s} cards={cards} onOpen={openStable} />
    <StatsCard s={s} top={statsTop} onOpen={openStable} />
  </>;
  const tabContent = (screen: Tab) => <View style={{ paddingHorizontal: 24 * s, paddingTop: 28 * s, gap: 18 * s }}><Text style={{ fontSize: 30 * s, fontWeight: '700', color: INK }}>{screen}</Text><Text style={{ fontSize: 15 * s, color: MUTED }}>{({ Calendar: 'Keep a weekend on your list', Results: 'Turn Nine standings', Circuits: 'Explore the circuit guides', Customize: 'Choose which cards appear on Home', Home: '' } as Record<Tab, string>)[screen]}</Text>
    {screen === 'Calendar' ? venues.map((v) => <ToggleRow key={v} label={`${v} · Race weekend`} active={saved.includes(v)} onPress={() => setSaved(saved.includes(v) ? saved.filter((x) => x !== v) : [...saved, v])} />) : null}
    {screen === 'Results' ? racers.map((r, i) => <View key={r[0]} style={styles.resultRow}><Text style={{ color: MUTED }}>{i + 1}</Text><Text style={{ color: INK, flex: 1, marginLeft: 14 }}>{r[0]}</Text><Text style={{ color: MUTED }}>{r[1]} pts</Text></View>) : null}
    {screen === 'Circuits' ? <>{['Harbor', 'Juniper', 'Mesa'].map((c) => <CircuitRow key={c} name={c} selected={circuit === c} onPress={() => setCircuit(c)} />)}<Text style={{ color: MUTED }}>{circuit} Circuit · A short track guide</Text></> : null}
    {screen === 'Customize' ? <>{['Every lap counts', 'Race day reflex test', 'Custom home cards'].map((label, i) => <ToggleRow key={label} label={label} active={cards[i]} onPress={() => setCards(cards.map((x, j) => (j === i ? !x : x)))} />)}<Pressable style={styles.restore} onPress={() => setCards([true, true, true])}><Text style={{ color: INK }}>Restore cards</Text></Pressable></> : null}
  </View>;
  const showHome = tab === 'Home';
  const ratingGesture = Gesture.Pan().minDistance(0).runOnJS(true).onBegin((e) => { const v = Math.max(1, Math.min(5, Math.ceil((e.x / (ST.row * s)) * 5))); updateRating(v); }).onUpdate((e) => { const v = Math.max(1, Math.min(5, Math.ceil((e.x / (ST.row * s)) * 5))); updateRating(v); });
  const isPanel = visible;
  useEffect(() => { if (game !== 'Get ready…' || destination !== 'reflex') return; const timer = setTimeout(() => { if (AppState.currentState === 'active') { setReactionAt(performance.now()); setGame('GO'); } }, 3000); return () => clearTimeout(timer); }, [game, destination]);
  if (!fontsLoaded) return <View style={styles.root} />;
  return <View style={styles.root}><StatusBar barStyle="light-content" /><ScrollView ref={scrollRef} contentInsetAdjustmentBehavior="never" accessibilityElementsHidden={isPanel} importantForAccessibility={isPanel ? 'no-hide-descendants' : 'auto'} onScroll={showHome ? topScroll : undefined} scrollEventThrottle={1000 / 15} showsVerticalScrollIndicator={false} scrollEnabled={!isPanel} contentContainerStyle={{ paddingTop: insets.top, paddingBottom: 120 * s }}>
    {/* Home stays mounted while another tab shows (remounting its canvases on return cost frames). */}
    <View accessibilityElementsHidden={!showHome} importantForAccessibility={showHome ? 'auto' : 'no-hide-descendants'} pointerEvents={showHome ? 'box-none' : 'none'} style={{ width, height: 1030 * s, position: 'relative' }}>{homeContent}</View>
    {/* Every tab screen stays mounted; a switch only toggles which one is displayed. */}
    {/* Home is never hidden with display: none (its canvases would be resized to zero and rebuilt on return): the other
        screens cover it with an opaque page instead, and are themselves only toggled. */}
    {(['Calendar', 'Results', 'Circuits', 'Customize'] as const).map((t) => <View key={t} style={{ position: 'absolute', left: 0, top: insets.top, width, height: 1030 * s + 120 * s, backgroundColor: BG, display: tab === t ? 'flex' : 'none' }}>{tabContent(t)}</View>)}
  </ScrollView>
    <View pointerEvents={isPanel ? 'none' : 'box-none'} accessibilityElementsHidden={isPanel} importantForAccessibility={isPanel ? 'no-hide-descendants' : 'auto'} style={StyleSheet.absoluteFill}><PitStopReviewTabBar active={tab} onSelect={selectTab} scale={s} disabled={isPanel} quiet={quiet} /></View>
    {/* Always mounted (a mount at every opening cost frames); hidden and touch-transparent while closed. */}
    <><Animated.View pointerEvents={isPanel ? 'auto' : 'none'} style={[styles.backdrop, backdropStyle]}><LinearGradient pointerEvents="none" colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.12)', 'rgba(0,0,0,0.62)']} locations={[0, 0.34, 0.6]} style={StyleSheet.absoluteFill} /><Pressable style={{ flex: 1 }} onPress={closeFromBackdrop} /></Animated.View><View pointerEvents={shareBusy || !isPanel ? 'none' : 'box-none'} style={styles.keyboardOverlay}><Animated.View accessibilityViewIsModal={isPanel} accessibilityElementsHidden={!isPanel} importantForAccessibility={isPanel ? 'auto' : 'no-hide-descendants'} style={[styles.panel, { width: 370 * s, height: panelHeight, bottom: panelBottom }, panelStyle]}>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 24 * s, paddingBottom: 28 * s }}><View style={styles.panelHeader}><View style={{ flex: 1 }}><Text style={[styles.panelTitle, { fontSize: 24 * s }]}>Your race-day take</Text><Text style={[styles.panelSub, { fontSize: 15 * s }]}>What has your experience been like?</Text></View><Pressable accessibilityLabel="Close review" onPress={closeEditor} style={styles.close}><MaterialCommunityIcons name="close" color={INK} size={16} /></Pressable></View>
        <GestureDetector gesture={ratingGesture}><View onLayout={onRowLayout} style={[styles.ratingRow, { width: ST.row * s, height: ST.height * s, marginTop: 12 * s }]}>{[1, 2, 3, 4, 5].map((n) => <Pressable key={n} accessibilityRole="button" accessibilityLabel={`${n} ${n === 1 ? 'star' : 'stars'}`} accessibilityState={{ selected: n === rating }} onPress={() => updateRating(n)} style={{ width: ST.cell * s, height: ST.height * s, alignItems: 'center', justifyContent: 'center' }}><StarSlot index={n - 1} shown={rating > 0 || quiet} selected={rating > 0 && n <= rating} scene={scene} s={s} /></Pressable>)}</View></GestureDetector>
        <View style={[styles.noteBox, { height: 110 * s, marginTop: 10 * s }]}>{!note && <Text pointerEvents="none" style={[styles.placeholder, { top: 17 * s, left: 18 * s, fontSize: 15 * s }]}>Add a note (optional)</Text>}<TextInput accessibilityLabel="Review note" value={note} onChangeText={updateNote} multiline textAlignVertical="top" placeholderTextColor={MUTED} style={[styles.input, { fontSize: 15 * s, padding: 14 * s }]} /></View><Text style={{ color: MUTED, fontSize: 11 * s, textAlign: 'right', marginTop: 4 * s }}>{graphemes(note).length}/280</Text>
        <Pressable accessibilityRole="button" disabled={!rating || shareBusy} onPress={share} style={[styles.share, { height: 48 * s, marginTop: 12 * s, opacity: rating ? 1 : 0.55 }]}><Text style={{ color: rating ? '#080708' : MUTED, fontSize: 16 * s, fontWeight: '700' }}>{shareBusy ? 'Sharing…' : 'Share review'}</Text></Pressable><Pressable onPress={() => updateRating(0)} style={{ height: 44 * s, justifyContent: 'center' }}><Text style={{ color: MUTED, fontSize: 14 * s }}>Reset rating</Text></Pressable>
      </ScrollView>
    </Animated.View></View>
    {!quiet && <PitStopReviewLights width={width} height={height} scale={s} top={insets.top + hero.top * s} slots={slots} scene={scene} />}</>
    <DestinationModal destination={destination} close={closeDestination} s={s} saved={saved} setSaved={setSaved} passSaved={passSaved} setPassSaved={setPassSaved} name={name} setName={setName} cards={cards} setCards={setCards} laps={laps} setLaps={setLaps} game={game} runGame={runReaction} circuit={circuit} setCircuit={setCircuit} cancelReaction={cancelReaction} />
  </View>;
}

/** One composer star: hidden until its start light lands in the slot (or always shown once rated / in quiet mode). */
function StarSlot({ index, shown, selected, scene, s }: { index: number; shown: boolean; selected: boolean; scene: SharedValue<number>; s: number }) {
  const style = useAnimatedStyle(() => ({ opacity: shown ? 1 : starLanded(scene.get(), index), transform: [{ scale: shown ? 1 : starScale(scene.get(), index) }] }), [shown, index]);
  return <Animated.View style={style}><Svg width={ST.box * s} height={ST.box * s} viewBox="0 0 28 28" accessible={false}><Path d={STAR_D} fill={selected ? YELLOW : 'none'} stroke={selected ? 'rgba(0,0,0,.35)' : 'white'} strokeWidth={2} strokeLinejoin="round" /></Svg></Animated.View>;
}

const venues=['Harbor Circuit','Juniper Park']; const racers:[string,number][]=[['Mira Chen',42],['Alex Vale',38],['Sam Ives',34],['Noah Reed',29]];
function ToggleRow({label,active,onPress}:{label:string;active:boolean;onPress:()=>void}){return <Pressable accessibilityRole="switch" accessibilityState={{checked:active}} onPress={onPress} style={styles.toggleRow}><Text style={{color:INK,flex:1}}>{label}</Text><MaterialCommunityIcons name={active?'toggle-switch':'toggle-switch-off-outline'} size={32} color={active?YELLOW:MUTED}/></Pressable>}
/** Same three outlines as the Swift circuitOutline canvas (72x44 points; Juniper starts at 0.62 of the height). */
const CIRCUIT_PATHS:Record<string,string>={
 Harbor:'M 6 26 C 8 10 20 5 30 7 C 47 10 62 7 66 20 C 68 35 51 42 39 39 C 22 35 11 39 6 26',
 Juniper:'M 8 27.28 C 4 12 24 4 34 7 C 54 9 69 16 64 30 C 54 43 18 40 8 27.28',
 Mesa:'M 7 32 L 18 8 L 48 8 L 65 22 L 53 39 L 25 34 Z',
};
function CircuitRow({name,selected,onPress}:{name:string;selected:boolean;onPress:()=>void}){return <Pressable accessibilityLabel={`${name} circuit`} accessibilityState={{selected}} onPress={onPress} style={styles.circuitRow}><View style={styles.track}><Svg width={72} height={44} viewBox="0 0 72 44" accessible={false}><Path d={CIRCUIT_PATHS[name]??CIRCUIT_PATHS.Harbor} fill="none" stroke={selected?YELLOW:'rgba(255,255,255,0.55)'} strokeWidth={selected?3:2} strokeLinecap="round" strokeLinejoin="round"/></Svg></View><Text style={{color:INK,flex:1,marginLeft:14}}>{name}</Text>{selected&&<MaterialCommunityIcons name="check-circle" size={20} color={YELLOW}/>}</Pressable>}
function DestinationModal({destination,close,s,saved,setSaved,passSaved,setPassSaved,name,setName,cards,setCards,laps,setLaps,game,runGame,circuit,setCircuit,cancelReaction}:{destination:Destination;close:()=>void;s:number;saved:string[];setSaved:(x:string[])=>void;passSaved:boolean;setPassSaved:(x:boolean)=>void;name:string;setName:(x:string)=>void;cards:boolean[];setCards:(x:boolean[])=>void;laps:number;setLaps:(x:number)=>void;game:string;runGame:()=>void;circuit:string;setCircuit:(x:string)=>void;cancelReaction:()=>void}){
 const title=destination==='pass'?'Club Pass':destination==='profile'?'Profile':destination==='calendar'?'Race weekends':destination==='reflex'?'Reaction test':destination==='customize'?'Home cards':destination==='laps'?'Every lap counts':destination==='pitcast'?'The weekend brief':destination==='results'?'Results':'Circuits';
 const cardLabels=['Every lap counts','Race day reflex test','Custom home cards'];
 return <Modal visible={destination!==null} animationType="slide" presentationStyle="pageSheet" onRequestClose={close} onDismiss={()=>{if(destination!==null)close();cancelReaction();}}><View style={styles.modal}><View style={styles.modalTop}><Text style={styles.modalTitle}>{title}</Text><Pressable onPress={close} style={styles.done}><Text style={{color:INK,fontWeight:'600'}}>Done</Text></Pressable></View><ScrollView contentContainerStyle={{padding:24,gap:12}}>
 {destination==='pass'&&<><Text style={styles.sectionHeading}>Club Pass</Text>{['Weekend notes','Personal home cards','Saved race weekends'].map(x=><Text key={x} style={styles.destinationText}>•  {x}</Text>)}<ToggleRow label={passSaved?'Pass saved':'Save pass'} active={passSaved} onPress={()=>setPassSaved(!passSaved)}/></>}
 {destination==='profile'&&<><TextInput accessibilityLabel="Display name" value={name} onChangeText={setName} placeholder="Display name" placeholderTextColor={MUTED} style={styles.profileInput}/><Text style={styles.destinationText}>Your dashboard display name.</Text></>}
 {destination==='calendar'&&venues.map(v=><ToggleRow key={v} label={`${v} · Race weekend`} active={saved.includes(v)} onPress={()=>setSaved(saved.includes(v)?saved.filter(x=>x!==v):[...saved,v])}/>)}
 {destination==='reflex'&&<View style={styles.game}><Text style={styles.gameText}>{game}</Text><Pressable onPress={runGame} style={styles.primary}><Text style={{color:'white',fontWeight:'700'}}>{game==='GO'?'Tap now':game==='Get ready…'?'Wait for GO':game.endsWith(' ms')?'Try again':game==='Too soon. Try again'?'Start test':'Start test'}</Text></Pressable></View>}
 {destination==='customize'&&<>{cardLabels.map((x,i)=><ToggleRow key={x} label={x} active={cards[i]} onPress={()=>setCards(cards.map((v,j)=>j===i?!v:v))}/>)}<Pressable style={styles.restore} onPress={()=>setCards([true,true,true])}><Text style={{color:INK}}>Restore cards</Text></Pressable></>}
 {destination==='laps'&&<View style={styles.game}><Text style={styles.gameText}>Every lap counts</Text><Text style={styles.destinationText}>This week · 18.4 km</Text><Text style={styles.destinationText}>Today&apos;s logged laps: {laps}</Text><Pressable onPress={()=>setLaps(laps+1)} style={styles.primary}><Text style={{color:'white',fontWeight:'700'}}>Log a lap</Text></Pressable></View>}
 {destination==='pitcast'&&<><Text style={styles.sectionHeading}>The weekend brief · Alex Vale / Sam Ives</Text>{['A cleaner first lap — Leave room before the first turn.','Watch the weather — Coastal cloud can change quickly.','One thing to notice — How the field settles after the restart.'].map(x=><Text key={x} style={styles.destinationText}>{x}</Text>)}</>}
 {destination==='results'&&racers.map((r,i)=><View key={r[0]} style={styles.resultRow}><Text style={{color:MUTED}}>{i+1}</Text><Text style={{color:INK,flex:1,marginLeft:14}}>{r[0]}</Text><Text style={{color:MUTED}}>{r[1]} pts</Text></View>)}
 {destination==='circuits'&&<>{['Harbor','Juniper','Mesa'].map(c=><CircuitRow key={c} name={c} selected={circuit===c} onPress={()=>setCircuit(c)}/>)}<Text style={styles.destinationText}>{circuit} Circuit · A short track guide</Text></>}
 </ScrollView></View></Modal>;
}

const styles:any={root:{flex:1,backgroundColor:BG},keyboardOverlay:{position:'absolute',left:0,right:0,top:0,bottom:0},pass:{position:'absolute',backgroundColor:VIOLET,flexDirection:'row',alignItems:'center'},passText:{color:'white',fontWeight:'700',includeFontPadding:false},profile:{position:'absolute',width:44,height:44,alignItems:'center',justifyContent:'center'},heroTitle:{position:'absolute',alignSelf:'center',width:'100%',textAlign:'center',fontWeight:'700',letterSpacing:-.6,color:INK},heroSub:{position:'absolute',alignSelf:'center',textAlign:'center',color:INK,opacity:.92,lineHeight:20},write:{position:'absolute',backgroundColor:INK,alignItems:'center',justifyContent:'center'},writeText:{fontSize:18,fontWeight:'700',color:'#080708'},pager:{position:'absolute',alignSelf:'center',flexDirection:'row',alignItems:'center'},pagerActive:{borderRadius:4,backgroundColor:'white'},pagerDot:{borderRadius:4,backgroundColor:'rgba(255,255,255,.38)'},road:{position:'absolute',left:0,overflow:'hidden'},shortcut:{position:'absolute',alignSelf:'center',height:60,backgroundColor:'#171416',borderRadius:32,borderWidth:1,borderColor:'rgba(255,255,255,.12)',flexDirection:'row',alignItems:'center',paddingHorizontal:4},shortcutButton:{flex:1,height:'100%',alignItems:'center',justifyContent:'center'},vDivider:{height:27,width:1,backgroundColor:'rgba(255,255,255,.2)'},bento:{position:'absolute',alignSelf:'center',flexDirection:'row',alignItems:'flex-start',gap:11},tile:{borderRadius:34,flexDirection:'row',alignItems:'center'},pitcast:{backgroundColor:'#F2EFE0',borderRadius:30},pitcastTop:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',color:'#211F1B'},mic:{backgroundColor:ORANGE,alignItems:'center',justifyContent:'center'},pitcastTitle:{fontWeight:'900',color:'#1D1B18'},pitcastFoot:{flexDirection:'row',gap:12,marginTop:8,color:'#1D1B18'},weekend:{position:'absolute',borderRadius:28,gap:8},weekendTitle:{color:INK,fontSize:17,fontWeight:'700'},weekendText:{color:MUTED,fontSize:13},backdrop:{position:'absolute',left:0,right:0,top:0,bottom:0},panel:{position:'absolute',alignSelf:'center',backgroundColor:PANEL,borderRadius:28,borderWidth:1,borderColor:'rgba(255,255,255,.12)',overflow:'hidden',shadowColor:'black',shadowOpacity:.5,shadowRadius:30,elevation:20},panelHeader:{flexDirection:'row',alignItems:'flex-start',gap:8},panelTitle:{color:INK,fontWeight:'700'},panelSub:{color:MUTED,marginTop:6},close:{width:44,height:44,marginTop:-4,backgroundColor:'rgba(255,255,255,.08)',borderRadius:24,alignItems:'center',justifyContent:'center'},ratingRow:{alignSelf:'center',flexDirection:'row'},noteBox:{backgroundColor:'rgba(255,255,255,.07)',borderRadius:16,borderWidth:1,borderColor:'rgba(255,255,255,.1)'},placeholder:{position:'absolute',color:MUTED},input:{color:INK,flex:1},share:{borderRadius:28,backgroundColor:INK,alignItems:'center',justifyContent:'center'},toggleRow:{minHeight:64,backgroundColor:'rgba(255,255,255,.07)',borderRadius:18,padding:16,flexDirection:'row',alignItems:'center'},resultRow:{minHeight:56,borderBottomWidth:1,borderBottomColor:'rgba(255,255,255,.12)',flexDirection:'row',alignItems:'center'},circuitRow:{minHeight:64,flexDirection:'row',alignItems:'center',padding:10,backgroundColor:'rgba(255,255,255,.06)',borderRadius:16},track:{width:72,height:44,justifyContent:'center'},restore:{height:48,justifyContent:'center',alignItems:'center',borderColor:'rgba(255,255,255,.22)',borderWidth:1,borderRadius:12},modal:{flex:1,backgroundColor:BG,paddingTop:18},modalTop:{height:48,paddingHorizontal:24,flexDirection:'row',alignItems:'center',justifyContent:'space-between'},modalTitle:{fontSize:18,fontWeight:'700',color:INK},done:{padding:8},sectionHeading:{color:INK,fontWeight:'700',fontSize:16},destinationText:{color:MUTED,fontSize:15,lineHeight:22,paddingVertical:6},profileInput:{height:52,borderRadius:12,paddingHorizontal:14,color:INK,backgroundColor:'rgba(255,255,255,.08)'},game:{flex:1,minHeight:400,alignItems:'center',justifyContent:'center',gap:24},gameText:{fontSize:32,fontWeight:'700',color:INK},primary:{backgroundColor:VIOLET,minHeight:48,paddingHorizontal:24,borderRadius:12,alignItems:'center',justifyContent:'center'}};
