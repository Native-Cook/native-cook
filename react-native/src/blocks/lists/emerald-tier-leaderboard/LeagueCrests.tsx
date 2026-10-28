import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useCallback, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation, ReduceMotion, type SharedValue, useAnimatedReaction, useAnimatedStyle,
  useDerivedValue, useSharedValue, withDelay, withSpring,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { EmeraldGlass } from './EmeraldGlass';

export type League = {
  key: string;
  name: string;
  detail: string;
  state: 'done' | 'current' | 'locked';
  chip: string;
  tint: readonly [string, string, string];
};
/** Leagues in climbing order; Emerald is the one this person is playing. */
export const LEAGUES: readonly League[] = [
  { key: 'amber', name: 'Amber League', detail: 'Completed · you finished 2nd of 8', state: 'done', chip: 'Promoted Sep 28', tint: ['#E39A4E', '#9A5422', '#3A1E0C'] },
  { key: 'copper', name: 'Copper League', detail: 'Completed · you finished 1st of 8', state: 'done', chip: 'Promoted Oct 12', tint: ['#EA7A3C', '#A4401A', '#3A170B'] },
  { key: 'emerald', name: 'Emerald League', detail: 'Top 40% advance to the next league', state: 'current', chip: '3d 23h 15m left', tint: ['#00C88E', '#008C67', '#064B37'] },
  { key: 'sapphire', name: 'Sapphire League', detail: 'Finish in Emerald’s top 3 to unlock', state: 'locked', chip: 'Locked', tint: ['#4E86E8', '#22508F', '#0B1F3B'] },
  { key: 'diamond', name: 'Diamond League', detail: 'Unlocks after Sapphire', state: 'locked', chip: 'Locked', tint: ['#A9C4D6', '#5C7787', '#18242C'] },
];
export const CURRENT_LEAGUE = 2;
/** Slot poses of the original composition by distance from focus (left and right differ in the design). */
const LEFT = { x: [0, 104.5, 180.5], y: [0, 4, 18], scale: [1, 0.643, 0.571], rotate: [0, 12, 17], ghost: [1, 0.29, 0.24] };
const RIGHT = { x: [0, 95, 168], y: [0, 7, 18.5], scale: [1, 0.625, 0.545], rotate: [0, 12, 17], ghost: [1, 0.29, 0.24] };
const SPACING = 95;
const CENTER = 198;
const SPRING = { duration: 520, dampingRatio: 0.86, reduceMotion: ReduceMotion.System } as const;

function slotPose(distance: number) {
  'worklet';
  const d = Math.abs(distance);
  const sign = distance < 0 ? -1 : 1;
  const side = distance < 0 ? LEFT : RIGHT;
  const at = (values: number[]) => {
    if (d >= 2) return values[2];
    const i = Math.floor(d);
    return values[i] + (values[i + 1] - values[i]) * (d - i);
  };
  const beyond = Math.max(0, d - 2);
  return {
    x: sign * (at(side.x) + beyond * 70),
    y: at(side.y) + beyond * 10,
    scale: at(side.scale) - beyond * 0.08,
    rotate: sign * at(side.rotate),
    ghost: at(side.ghost),
    opacity: 1 - Math.min(1, Math.max(0, d - 2.2) / 0.6),
  };
}

/** Demo: after the recast, peek at the next (locked) league and come back. */
function demoFocus(seconds: number) {
  'worklet';
  const ease = (x: number) => { const t = Math.max(0, Math.min(1, x)); return t * t * (3 - 2 * t); };
  return CURRENT_LEAGUE + ease((seconds - 4.4) / 0.6) - ease((seconds - 6.2) / 0.6);
}

export function useLeagueFocus({ demoTime, demoRunning, enabled }: { demoTime: SharedValue<number>; demoRunning: SharedValue<boolean>; enabled: boolean }) {
  const manualFocus = useSharedValue(CURRENT_LEAGUE);
  const manual = useSharedValue(false);
  const dragStart = useSharedValue(CURRENT_LEAGUE);
  const focus = useDerivedValue(() => manual.get() || !demoRunning.get() ? manualFocus.get() : demoFocus(demoTime.get()));
  const tick = useCallback(() => { void Haptics.selectionAsync().catch(() => {}); }, []);
  useAnimatedReaction(() => Math.round(focus.get()), (next, previous) => {
    if (previous != null && next !== previous && manual.get()) scheduleOnRN(tick);
  });
  const settle = useCallback((target: number, velocity = 0) => {
    'worklet';
    const index = Math.max(0, Math.min(LEAGUES.length - 1, Math.round(target)));
    manualFocus.set(withSpring(index, { ...SPRING, velocity }, finished => {
      if (!finished || index === CURRENT_LEAGUE) return;
      // A browsed league always hands the screen back to the one being played.
      manualFocus.set(withDelay(3200, withSpring(CURRENT_LEAGUE, SPRING, done => { if (done) manual.set(false); })));
    }));
  }, [manual, manualFocus]);
  const gesture = useMemo(() => Gesture.Pan().enabled(enabled).activeOffsetX([-10, 10]).failOffsetY([-14, 14])
    .onStart(() => {
      const current = focus.get();
      cancelAnimation(manualFocus);
      manualFocus.set(current); dragStart.set(current); manual.set(true);
    })
    .onUpdate(event => {
      const raw = dragStart.get() - event.translationX / SPACING;
      const edge = Math.max(0, Math.min(LEAGUES.length - 1, raw));
      const over = raw - edge;
      manualFocus.set(edge + over / (1 + Math.abs(over) * 2.5));
    })
    .onEnd(event => {
      const velocity = -event.velocityX / SPACING;
      settle(manualFocus.get() + Math.max(-1, Math.min(1, velocity * 0.18)), velocity);
    }), [dragStart, enabled, focus, manual, manualFocus, settle]);
  const select = useCallback((index: number) => {
    'worklet';
    cancelAnimation(manualFocus);
    manualFocus.set(focus.get()); manual.set(true);
    settle(index);
  }, [focus, manual, manualFocus, settle]);
  return { focus, gesture, select };
}

function CrestSlot({ index, focus, children }: { index: number; focus: SharedValue<number>; children: ReactNode }) {
  const state = LEAGUES[index].state;
  const style = useAnimatedStyle(() => {
    const d = index - focus.get();
    const pose = slotPose(d);
    const near = Math.max(0, 1 - Math.abs(d));
    // A locked crest is a ghost at the side and a frosted, readable object when focused.
    const material = state === 'locked' ? pose.ghost * (1 - near) + 0.78 * near : 1;
    // Focusing a locked league nudges its lock once, like trying a closed door.
    const shake = state === 'locked' ? Math.sin(Math.max(0, near - 0.75) * 4 * Math.PI * 3) * near * 3 : 0;
    return {
      opacity: pose.opacity * material,
      zIndex: Math.round(10 - Math.abs(d) * 2),
      transform: [{ translateX: CENTER - 50 + pose.x }, { translateY: pose.y }, { rotate: `${pose.rotate + shake}deg` }, { scale: pose.scale }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.slot, style]}>{children}</Animated.View>;
}

export function LeagueCrestRow({ focus, gesture, select, renderCrest }: {
  focus: SharedValue<number>; gesture: ReturnType<typeof Gesture.Pan>; select: (index: number) => void;
  renderCrest: (league: League, index: number) => ReactNode;
}) {
  return <GestureDetector gesture={gesture}>
    <View style={styles.row} accessible accessibilityRole="adjustable" accessibilityLabel="Leagues"
      accessibilityValue={{ text: LEAGUES[CURRENT_LEAGUE].name }} accessibilityHint="Swipe to browse other leagues"
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={event => select(Math.round(focus.get()) + (event.nativeEvent.actionName === 'increment' ? 1 : -1))}>
      {LEAGUES.map((league, index) => <CrestSlot key={league.key} index={index} focus={focus}>{renderCrest(league, index)}</CrestSlot>)}
    </View>
  </GestureDetector>;
}

function HeadlineLayer({ index, focus, solid }: { index: number; focus: SharedValue<number>; solid: boolean }) {
  const league = LEAGUES[index];
  const style = useAnimatedStyle(() => {
    const d = index - focus.get();
    const near = Math.max(0, 1 - Math.abs(d) * 1.8);
    return { opacity: near, transform: [{ translateX: d * -22 }] };
  });
  const icon = league.state === 'current' ? 'time-outline' : league.state === 'done' ? 'checkmark-circle' : 'lock-closed';
  const tone = league.state === 'locked' ? '#B9C9DA' : league.state === 'done' ? '#FFD9A8' : '#72F598';
  return <Animated.View pointerEvents="none" style={[styles.headline, style]} accessibilityElementsHidden={index !== CURRENT_LEAGUE} importantForAccessibility={index === CURRENT_LEAGUE ? 'auto' : 'no-hide-descendants'}>
    <Text style={styles.title}>{league.name}</Text>
    <Text style={styles.subtitle}>{league.detail}</Text>
    <View style={styles.chip}>
      <EmeraldGlass kind="green" radius={20} solid={solid} />
      <Ionicons name={icon} size={league.state === 'current' ? 18 : 15} color={tone} />
      <Text style={[styles.chipText, { color: tone }]}>{league.chip}</Text>
    </View>
  </Animated.View>;
}

/** Title, subtitle and countdown of the focused league, cross-sliding with the crests. */
export function LeagueHeadline({ focus, solid, challenges }: { focus: SharedValue<number>; solid: boolean; challenges: boolean }) {
  if (challenges) return <View pointerEvents="none" style={styles.headline}>
    <Text style={styles.title}>Emerald Challenges</Text>
    <Text style={styles.subtitle}>Earn XP to climb the league</Text>
    <View style={styles.chip}><EmeraldGlass kind="green" radius={20} solid={solid} /><Ionicons name="time-outline" size={18} color="#72F598" /><Text style={styles.chipText}>3d 23h 15m left</Text></View>
  </View>;
  return <>{LEAGUES.map((league, index) => <HeadlineLayer key={league.key} index={index} focus={focus} solid={solid} />)}</>;
}

function TintLayer({ index, focus }: { index: number; focus: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(index - focus.get())) }));
  const league = LEAGUES[index];
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
    <LinearGradient colors={[league.tint[0], league.tint[1], league.tint[2], '#071e14', '#080a09']} locations={[0, 0.23, 0.40, 0.58, 0.82]} style={StyleSheet.absoluteFill} />
  </Animated.View>;
}

/** The page's light takes the colour of the league in focus. Emerald is the base gradient itself. */
export function LeagueTint({ focus }: { focus: SharedValue<number> }) {
  return <>{LEAGUES.map((league, index) => index === CURRENT_LEAGUE ? null : <TintLayer key={league.key} index={index} focus={focus} />)}</>;
}

/** The standings belong to Emerald; they recede while another league is in focus. */
export function useLeagueRecede(focus: SharedValue<number>) {
  return useAnimatedStyle(() => {
    const away = Math.min(1, Math.abs(focus.get() - CURRENT_LEAGUE));
    return { opacity: 1 - away * 0.62, transform: [{ scale: 1 - away * 0.02 }] };
  });
}

const styles = StyleSheet.create({
  row: { position: 'absolute', left: 0, top: 57, width: 393, height: 118, zIndex: 3 },
  slot: { position: 'absolute', left: 0, top: 0, width: 100, height: 112, alignItems: 'center', justifyContent: 'center' },
  headline: { position: 'absolute', left: 0, top: 168, width: 393, height: 92, alignItems: 'center' },
  title: { width: 393, textAlign: 'center', fontSize: 23, fontWeight: '700', color: '#fff', letterSpacing: -0.65, lineHeight: 28 },
  subtitle: { width: 393, textAlign: 'center', color: '#a6c7b9', fontSize: 13, marginTop: 4, lineHeight: 18 },
  chip: { marginTop: 10, minWidth: 161, height: 32, paddingHorizontal: 16, borderRadius: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  chipText: { fontSize: 12, fontWeight: '700', color: '#72f598', fontVariant: ['tabular-nums'] },
});
