import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector, RectButton } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, Easing, ReduceMotion, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';
import Svg, { Circle, ClipPath, Defs, Image as SvgImage, LinearGradient as SvgGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';
import { EmeraldRecastScene } from './EmeraldRecastScene';
import { RECAST_SEAT_PHASE } from './recastMotion';
import { CURRENT_LEAGUE, type League, LeagueCrestRow, LeagueHeadline, LeagueTint, useLeagueFocus, useLeagueRecede } from './LeagueCrests';
import { EmeraldGlass, useEmeraldSolidMaterial } from './EmeraldGlass';
import { ArtMaterial } from './EmeraldMaterials';
import { clamp, DEMO_RETURN_START, emeraldDemoPhase, emeraldRankFrame, emeraldRubberPhase, emeraldSnapTarget, emeraldUnrubberPhase, LOOP, TAKEOVER_MS } from './motion';
export { EMERALD_TIER_LEADERBOARD_LOOP_SECONDS, LOOP } from './motion';

const ATLAS = require('./assets/leaderboard-atlas.png');
const THEO_PORTRAIT = require('./assets/theo-portrait.png');
const CREST = require('./assets/emerald-crest.png');
const BUTTON_FEEDBACK = { underlayColor: '#BDFBE0', activeOpacity: .12, rippleColor: '#BDFBE024' } as const;
const CROPS = { ari: [379, 449, 98], milo: [230, 513, 82], juno: [549, 525, 82], theo: [245, 760, 47], noa: [245, 827, 47], kai: [245, 894, 47] } as const;
type AvatarName = keyof typeof CROPS;
export type EmeraldTierLeaderboardProps = { autoPlay?: boolean; previewTime?: number; previewReducedMotion?: boolean; renderer?: 'auto' | 'skia'; onRendererStatus?: (status: string) => void };
function Avatar({ name, size }: { name: AvatarName; size: number }) {
  const [x, y, d] = name === 'theo' ? [0, 0, 1254] : CROPS[name];
  return <View style={{ width: size, height: size, borderRadius: size / 2, overflow: 'hidden', borderWidth: 1.5, borderColor: '#ffffff60', backgroundColor: '#2c5747' }}>
    <Svg width="100%" height="100%" viewBox={`${x} ${y} ${d} ${d}`}>
      <Defs><ClipPath id={`clip-${name}`}><Circle cx={x + d / 2} cy={y + d / 2} r={d / 2} /></ClipPath></Defs>
      <SvgImage href={name === 'theo' ? THEO_PORTRAIT : ATLAS} width={name === 'theo' ? 1254 : 1447} height={name === 'theo' ? 1254 : 1087} clipPath={`url(#clip-${name})`} />
    </Svg>
  </View>;
}
const LOCKED_TONES = {
  emerald: { light: '#a6e1c5', dark: '#297e62', base: '#549f88', inner: '#39957b', edge: '#8ed6bd', lock: '#b7e7d2', hole: '#4d9e80' },
  sapphire: { light: '#b7d2ff', dark: '#2a58a8', base: '#4f74b4', inner: '#3b63aa', edge: '#a3c4ff', lock: '#dce8ff', hole: '#3b63aa' },
  diamond: { light: '#eef6fa', dark: '#71899a', base: '#8aa1b0', inner: '#6f8898', edge: '#dbe9f1', lock: '#f3f8fb', hole: '#6f8898' },
} as const;
function Crest({ locked = false, pale = false, letter = 'E', tone = 'emerald' }: { locked?: boolean; pale?: boolean; letter?: string; tone?: keyof typeof LOCKED_TONES }) {
  const lockTone = LOCKED_TONES[tone];
  const id = locked ? `lock-${tone}` : pale ? 'pale' : 'bronze';
  return <Svg width="100%" height="100%" viewBox="0 0 100 112"><Defs><SvgGradient id={id} x1="0" y1="0" x2="1" y2="1"><Stop stopColor={locked ? lockTone.light : pale ? '#ffe2ac' : '#ffc283'} /><Stop offset="1" stopColor={locked ? lockTone.dark : pale ? '#e4965a' : '#b9410e'} /></SvgGradient></Defs><Path d="M8 30 0 43 13 66 23 96 50 110 78 96 89 67 100 43 91 31Z" fill={locked ? lockTone.base : '#b65a24'} /><Path d="m50 0 41 22-4 61-19 22H32L13 83 9 22Z" fill={`url(#${id})`} /><Path d="m50 14 28 16-3 47-14 15H39L25 77l-3-47Z" fill={locked ? lockTone.inner : '#d66d2e'} stroke={locked ? lockTone.edge : '#ffd49e'} strokeWidth={4} />{locked ? <><Path d="M40 49v-9a10 10 0 0 1 20 0v9" fill="none" stroke={lockTone.lock} strokeWidth={6} /><Rect x={33} y={48} width={34} height={27} rx={5} fill={lockTone.lock} /><Circle cx={50} cy={59} r={4} fill={lockTone.hole} /></> : <SvgText x={50} y={76} fill="#fff0cc" fontSize={51} fontWeight="700" textAnchor="middle">{letter}</SvgText>}</Svg>;
}
/** A locked crest keeps the design's green ghost at rest and takes its own league's tone in focus. */
function LockedCrest({ index, tone, focus }: { index: number; tone: 'sapphire' | 'diamond'; focus: SharedValue<number> }) {
  const toned = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(index - focus.get())) }));
  return <View style={{ width: 100, height: 112 }}>
    <Crest locked />
    <Animated.View style={[StyleSheet.absoluteFill, toned]}><Crest locked tone={tone} /></Animated.View>
  </View>;
}
/** The demo owns a clock; input owns a separate, monotonic rank journey. */
function useEmeraldRankMotion({ autoPlay, previewTime, reduced, width }: {
  autoPlay: boolean; previewTime?: number; reduced: boolean; width: number;
}) {
  const initialTime = clamp(previewTime ?? 0, 0, LOOP);
  const rankPhase = useSharedValue(emeraldDemoPhase(initialTime / LOOP));
  const demoTime = useSharedValue(initialTime);
  const demoAnchor = useSharedValue(-1);
  const lastTick = useSharedValue(-1);
  const manual = useSharedValue(false);
  const animating = useSharedValue(false);
  const dragging = useSharedValue(false);
  const target = useSharedValue<0 | 1>(0);
  const dragStart = useSharedValue(0);
  const dragTranslation = useSharedValue(0);
  const holdRemaining = useSharedValue(2);
  const generation = useSharedValue(0);
  const needsRecovery = useSharedValue(false);
  const responseAllowed = useSharedValue(true);
  const hapticArmed = useSharedValue(false);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const focused = useSharedValue(false);
  const focusRef = useRef(false);
  const haptic = useCallback(() => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    void AccessibilityInfo.isScreenReaderEnabled().then(enabled => {
      if (enabled && focusRef.current && AppState.currentState === 'active') {
        AccessibilityInfo.announceForAccessibility('Theo Park is first with 1050 points. Ari Vale second, Milo Chen third, Juno Reed fourth.');
      }
    }).catch(() => {});
  }, []);
  const frame = useDerivedValue(() => emeraldRankFrame(rankPhase.get(), reduced, responseAllowed.get()));

  const settle = useCallback((destination: 0 | 1, velocity: number, notify: boolean) => {
    'worklet';
    const owner = generation.get() + 1;
    generation.set(owner);
    cancelAnimation(rankPhase);
    manual.set(true);
    animating.set(true);
    dragging.set(false);
    target.set(destination);
    responseAllowed.set(destination === 1);
    hapticArmed.set(notify && destination === 1);
    demoAnchor.set(-1);
    holdRemaining.set(2);
    rankPhase.set(withSpring(destination, {
      duration: 420, dampingRatio: Math.abs(velocity) > .35 ? .88 : 1,
      velocity: clamp(velocity, -4, 4), overshootClamping: true, reduceMotion: ReduceMotion.Never,
    }, finished => {
      if (!finished || generation.get() !== owner) return;
      animating.set(false);
      holdRemaining.set(2);
    }));
  }, [animating, demoAnchor, dragging, generation, hapticArmed, holdRemaining, manual, rankPhase, responseAllowed, target]);

  const takeLead = useCallback((preserveHaptic: boolean) => {
    'worklet';
    const current = rankPhase.get();
    const owner = generation.get() + 1;
    generation.set(owner);
    cancelAnimation(rankPhase);
    manual.set(true);
    animating.set(true);
    dragging.set(false);
    target.set(1);
    responseAllowed.set(true);
    if (!preserveHaptic) hapticArmed.set(true);
    demoAnchor.set(-1);
    holdRemaining.set(2);
    // Constant phase speed feeds the same eased choreography used by the demo.
    rankPhase.set(withTiming(1, { duration: Math.max(120, TAKEOVER_MS * (1 - clamp(current))), easing: Easing.linear, reduceMotion: ReduceMotion.Never }, finished => {
      if (!finished || generation.get() !== owner) return;
      animating.set(false);
      holdRemaining.set(2);
    }));
  }, [animating, demoAnchor, dragging, generation, hapticArmed, holdRemaining, manual, rankPhase, responseAllowed, target]);

  const replayOnUI = useCallback(() => {
    'worklet';
    if (previewTime !== undefined || !focused.get() || !foreground.get()) return;
    if (reduced) {
      generation.set(generation.get() + 1);
      cancelAnimation(rankPhase);
      manual.set(true); animating.set(false); dragging.set(false); target.set(1);
      responseAllowed.set(false); demoAnchor.set(-1); holdRemaining.set(2);
      hapticArmed.set(rankPhase.get() < .5);
      rankPhase.set(1);
      return;
    }
    const continuing = manual.get() && animating.get() && target.get() === 1;
    if (rankPhase.get() < .999 || animating.get()) {
      takeLead(continuing);
      return;
    }
    // A completed replay travels back visibly; another tap can redirect this return instantly.
    const owner = generation.get() + 1;
    generation.set(owner);
    cancelAnimation(rankPhase);
    manual.set(true); animating.set(true); dragging.set(false); target.set(0);
    responseAllowed.set(false); hapticArmed.set(false); demoAnchor.set(-1);
    rankPhase.set(withTiming(0, { duration: 900, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, finished => {
      if (finished && generation.get() === owner) takeLead(false);
    }));
  }, [animating, demoAnchor, dragging, focused, foreground, generation, hapticArmed, holdRemaining, manual, previewTime, rankPhase, reduced, responseAllowed, takeLead, target]);
  const replay = useCallback(() => scheduleOnUI(replayOnUI), [replayOnUI]);

  useAnimatedReaction(() => hapticArmed.get() && !dragging.get() && rankPhase.get() >= RECAST_SEAT_PHASE,
    committed => {
      if (!committed || !focused.get() || !foreground.get()) return;
      hapticArmed.set(false);
      scheduleOnRN(haptic);
    });

  const suspend = useCallback(() => {
    'worklet';
    needsRecovery.set(manual.get() || animating.get() || dragging.get());
    generation.set(generation.get() + 1);
    cancelAnimation(rankPhase);
    animating.set(false); dragging.set(false); hapticArmed.set(false);
    demoAnchor.set(-1); lastTick.set(-1);
  }, [animating, demoAnchor, dragging, generation, hapticArmed, lastTick, manual, needsRecovery, rankPhase]);
  const recover = useCallback(() => {
    'worklet';
    if (previewTime !== undefined || !focused.get() || !foreground.get()) return;
    demoAnchor.set(-1); lastTick.set(-1);
    if (!needsRecovery.get()) return;
    needsRecovery.set(false);
    if (reduced) { rankPhase.set(emeraldSnapTarget(rankPhase.get(), 0)); return; }
    settle(emeraldSnapTarget(rankPhase.get(), 0), 0, false);
  }, [demoAnchor, focused, foreground, lastTick, needsRecovery, previewTime, rankPhase, reduced, settle]);

  const frameCallback = useFrameCallback(info => {
    if (previewTime !== undefined || reduced || !focused.get() || !foreground.get()) return;
    const previous = lastTick.get();
    const elapsed = previous < 0 ? 0 : Math.max(0, (info.timestamp - previous) / 1000);
    lastTick.set(info.timestamp);
    if (dragging.get() || animating.get() || !autoPlay) return;
    if (manual.get()) {
      const remaining = Math.max(0, holdRemaining.get() - elapsed);
      holdRemaining.set(remaining);
      if (remaining > 0) return;
      // Resume at an identical rest pose, then use the demo's guided return.
      demoTime.set(target.get() === 1 ? DEMO_RETURN_START : 0);
      demoAnchor.set(-1);
      manual.set(false);
    }
    if (demoAnchor.get() < 0) demoAnchor.set(info.timestamp - demoTime.get() * 1000);
    const seconds = ((info.timestamp - demoAnchor.get()) / 1000) % LOOP;
    demoTime.set(seconds);
    responseAllowed.set(seconds < DEMO_RETURN_START);
    rankPhase.set(emeraldDemoPhase(seconds / LOOP));
  }, false);

  useFocusEffect(useCallback(() => {
    focusRef.current = true;
    focused.set(true);
    frameCallback.setActive(previewTime === undefined && !reduced && AppState.currentState === 'active');
    scheduleOnUI(recover);
    return () => {
      focusRef.current = false;
      focused.set(false);
      frameCallback.setActive(false);
      scheduleOnUI(suspend);
    };
  }, [focused, frameCallback, previewTime, recover, reduced, suspend]));
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      const active = state === 'active';
      foreground.set(active);
      frameCallback.setActive(active && focusRef.current && previewTime === undefined && !reduced);
      scheduleOnUI(active ? recover : suspend);
    });
    return () => subscription.remove();
  }, [foreground, frameCallback, previewTime, recover, reduced, suspend]);
  useEffect(() => {
    if (previewTime === undefined) return;
    scheduleOnUI(() => {
      generation.set(generation.get() + 1);
      cancelAnimation(rankPhase);
      manual.set(false); animating.set(false); dragging.set(false); hapticArmed.set(false);
      const seconds = clamp(previewTime, 0, LOOP);
      needsRecovery.set(false);
      demoTime.set(seconds); demoAnchor.set(-1);
      responseAllowed.set(seconds < DEMO_RETURN_START);
      rankPhase.set(emeraldDemoPhase(seconds / LOOP));
    });
  }, [animating, demoAnchor, demoTime, dragging, generation, hapticArmed, manual, needsRecovery, previewTime, rankPhase, responseAllowed]);
  useEffect(() => {
    if (!reduced || previewTime !== undefined) return;
    scheduleOnUI(() => {
      generation.set(generation.get() + 1);
      cancelAnimation(rankPhase);
      rankPhase.set(emeraldSnapTarget(rankPhase.get(), 0));
      manual.set(true); animating.set(false); dragging.set(false); hapticArmed.set(false);
      responseAllowed.set(false); target.set(rankPhase.get() >= .5 ? 1 : 0);
      holdRemaining.set(2); demoAnchor.set(-1);
    });
  }, [animating, demoAnchor, dragging, generation, hapticArmed, holdRemaining, manual, previewTime, rankPhase, reduced, responseAllowed, target]);

  const gesture = useMemo(() => Gesture.Pan().enabled(previewTime === undefined && !reduced)
    .activeOffsetY([-8, 8]).failOffsetX([-14, 14]).maxPointers(1)
    .onStart(event => {
      if (!focused.get() || !foreground.get()) return;
      generation.set(generation.get() + 1);
      cancelAnimation(rankPhase);
      manual.set(true); animating.set(false); dragging.set(true); hapticArmed.set(false);
      responseAllowed.set(false); demoAnchor.set(-1);
      dragStart.set(emeraldUnrubberPhase(rankPhase.get()));
      dragTranslation.set(event.translationY);
    })
    .onUpdate(event => {
      if (!dragging.get() || !focused.get() || !foreground.get()) return;
      rankPhase.set(emeraldRubberPhase(dragStart.get() + -(event.translationY - dragTranslation.get()) / width));
    })
    .onEnd((event, success) => {
      if (!success || !dragging.get()) return;
      const velocity = -event.velocityY / width;
      settle(emeraldSnapTarget(rankPhase.get(), velocity), velocity, true);
    })
    .onFinalize(() => {
      if (!dragging.get()) return;
      // Cancellation settles too; it cannot leave the two portraits suspended together.
      settle(emeraldSnapTarget(rankPhase.get(), 0), 0, false);
    }), [animating, demoAnchor, dragStart, dragTranslation, dragging, focused, foreground, generation, hapticArmed, manual, previewTime, rankPhase, reduced, responseAllowed, settle, width]);
  const demoRunning = useDerivedValue(() => !manual.get());
  return { frame, gesture, replay, demoTime, demoRunning };
}

export function EmeraldTierLeaderboard({ autoPlay = true, previewTime, previewReducedMotion, renderer = 'auto', onRendererStatus }: EmeraldTierLeaderboardProps) {
  const insets = useSafeAreaInsets(), systemReduced = useReducedMotionPreference(), reduced = previewReducedMotion ?? systemReduced;
  const solidMaterial = useEmeraldSolidMaterial();
  const [viewport, setViewport] = useState({ width: 393, height: 852 }), [tab, setTab] = useState('Leagues'), [nav, setNav] = useState('Tournaments');
  const onLayout = useCallback((e: LayoutChangeEvent) => { const { width, height } = e.nativeEvent.layout; setViewport(v => v.width === width && v.height === height ? v : { width, height }); }, []);
  const widthScale = viewport.width / 393, usableHeight = viewport.height - insets.top - insets.bottom - 78 * widthScale;
  const scale = Math.min(widthScale, Math.max(.65, usableHeight / 714));
  const { frame, gesture, replay, demoTime, demoRunning } = useEmeraldRankMotion({ autoPlay, previewTime, reduced, width: Math.max(120, 241.5 * scale) });
  const league = useLeagueFocus({ demoTime, demoRunning, enabled: previewTime === undefined && tab === 'Leagues' });
  const recede = useLeagueRecede(league.focus);
  const renderCrest = useCallback((item: League, index: number) => index === CURRENT_LEAGUE
    ? <ArtMaterial asset={CREST} width={100} height={112} frame={frame} />
    : item.state === 'locked' ? <LockedCrest index={index} tone={item.key === 'diamond' ? 'diamond' : 'sapphire'} focus={league.focus} />
    : <View style={{ width: 100, height: 112 }}><Crest pale={item.key === 'amber'} letter={item.name[0]} /></View>, [frame, league.focus]);
  return <View style={s.root} onLayout={onLayout} testID="emerald-tier-leaderboard">
    <StatusBar style="light" />
    <LinearGradient colors={['#00c88e', '#008c67', '#064b37', '#071e14', '#080a09']} locations={[0, .23, .40, .58, .82]} style={StyleSheet.absoluteFill} />
    <LeagueTint focus={league.focus} />
    <View style={{ position: 'absolute', top: insets.top + 8, left: (viewport.width - 393 * scale) / 2, width: 393, height: 714, transformOrigin: 'top left', transform: [{ scale }] }}>
      <View style={s.header}><View style={s.segments}><EmeraldGlass kind="green" radius={25} solid={solidMaterial} />{['Leagues', 'Challenges'].map(label => <RectButton {...BUTTON_FEEDBACK} key={label} accessibilityRole="tab" accessibilityState={{ selected: tab === label }} accessibilityLabel={label} onPress={() => { setTab(label); if (label === 'Leagues') replay(); }} style={[s.segment, tab === label && s.selected]}>{tab === label ? <EmeraldGlass kind="selected" radius={22} solid={solidMaterial} /> : null}<Text style={[s.segmentText, tab === label && s.selectedText]}>{label}</Text></RectButton>)}</View><RectButton {...BUTTON_FEEDBACK} accessibilityRole="button" accessibilityLabel="Replay league update" onPress={replay} style={s.bell}><Ionicons name="notifications" size={27} color="#77f38a" /><View style={s.notificationDot} /></RectButton></View>
      <LeagueCrestRow focus={league.focus} gesture={league.gesture} select={league.select} renderCrest={renderCrest} />
      <LeagueHeadline focus={league.focus} solid={solidMaterial} challenges={tab !== 'Leagues'} />
      <GestureDetector gesture={gesture}><Animated.View style={[s.arenaWrap, recede]} accessibilityLabel="Rank Recast. Pull the fourth-place row upward to preview the new leader; pull downward to reverse."><EmeraldRecastScene frame={frame} solid={solidMaterial} renderer={renderer} onRendererStatus={onRendererStatus} Avatar={Avatar} /><RectButton {...BUTTON_FEEDBACK} accessibilityRole="button" accessibilityLabel="Replay the fourth-place row becoming the leader" onPress={replay} style={{ position: 'absolute', left: 19, top: 288, width: 355, height: 47, borderRadius: 13, zIndex: 15 }} /><RectButton {...BUTTON_FEEDBACK} accessibilityRole="button" accessibilityLabel="Replay Rank Recast" onPress={replay} style={s.replayTarget} /></Animated.View></GestureDetector>
      <Animated.View style={[s.list, recede]}>{[{ n: '05', name: 'Noa Ellis', avatar: 'noa', score: 730 }, { n: '06', name: 'Kai Rowan', avatar: 'kai', score: 710 }].map(row => <View key={row.n} style={s.row}><EmeraldGlass kind="row" radius={13} solid={solidMaterial} /><Text style={s.rowNumber}>{row.n}</Text><Avatar name={row.avatar as AvatarName} size={30} /><Text style={s.rowName}>{row.name}</Text><Text style={s.rowScore}>{row.score}</Text><Text style={s.rowBolt}>ϟ</Text></View>)}</Animated.View>
    </View>
    <View style={[s.nav, { bottom: Math.max(8, insets.bottom), left: 19 * widthScale, right: 19 * widthScale, height: 52 * widthScale }]}><EmeraldGlass kind="smoke" radius={29} solid={solidMaterial} />{[{ label: 'Home', icon: 'home' }, { label: 'Tournaments', icon: 'trophy' }, { label: 'Profile', icon: 'person' }].map(item => <RectButton {...BUTTON_FEEDBACK} key={item.label} accessibilityRole="tab" accessibilityState={{ selected: nav === item.label }} onPress={() => { setNav(item.label); if (item.label === 'Tournaments') replay(); }} style={[s.navItem, nav === item.label && s.navSelected]}>{nav === item.label ? <EmeraldGlass kind="selected" radius={25} solid={solidMaterial} /> : null}<Ionicons name={item.icon as 'home' | 'trophy' | 'person'} size={20 * widthScale} color={nav === item.label ? '#fff' : '#a7ada6'} /><Text style={[s.navLabel, { color: nav === item.label ? '#fff' : '#a7ada6' }]}>{item.label}</Text></RectButton>)}</View>
  </View>;
}
const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#080a09', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, top: 0, right: 16, height: 44, flexDirection: 'row', gap: 10 },
  segments: { width: 304, height: 42, borderRadius: 25, padding: 4, flexDirection: 'row', backgroundColor: 'transparent', borderWidth: 1, borderColor: 'transparent' },
  segment: { flex: 1, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  selected: { backgroundColor: 'transparent' },
  segmentText: { fontSize: 12, color: '#94bbac' },
  selectedText: { color: '#fff', fontWeight: '700' },
  bell: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  notificationDot: { position: 'absolute', right: 7, top: 4, width: 8, height: 8, borderRadius: 5, backgroundColor: '#ff8278' },
  arenaWrap: { position: 'absolute', top: 273, left: 0, width: 393, height: 335 },
  replayTarget: { position: 'absolute', left: 135, top: 169, width: 123, height: 105, zIndex: 15, borderRadius: 5 },
  list: { position: 'absolute', top: 614, left: 19, right: 19, gap: 6 },
  row: { height: 47, borderRadius: 13, backgroundColor: 'transparent', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, gap: 12 },
  rowNumber: { color: '#b5b9b5', fontSize: 12, fontVariant: ['tabular-nums'] },
  rowName: { flex: 1, color: '#fff', fontSize: 12, fontWeight: '500' },
  rowScore: { color: '#fff', fontSize: 12, fontVariant: ['tabular-nums'] },
  rowBolt: { color: '#ffc846', fontSize: 20, marginLeft: -8 },
  nav: { position: 'absolute', borderWidth: 1, borderColor: 'transparent', borderRadius: 29, flexDirection: 'row', backgroundColor: 'transparent', padding: 2 },
  navItem: { flex: 1, borderRadius: 25, alignItems: 'center', justifyContent: 'center', gap: 2 },
  navSelected: { borderWidth: 1, borderColor: 'transparent', backgroundColor: 'transparent' },
  navLabel: { fontSize: 10 },
});
