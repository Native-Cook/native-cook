import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { draw, thumbnail } from './art';
import { completeEvent, drive, hasCollection, heldFrame, initialMotion, resetMotion, settleDuration, type Event, type Motion, type Session } from './driver';
import { clamp, dragged, inverseHit, meanViewB, releaseVelocity, retarget, smooth, targetView, zeroSupport, type Frame, type Point } from './motion';
import { useLenticularResources, type Resources } from './resources';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number; onBack?: () => void };
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
function impact() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
function fullView(frame: Frame) { 'worklet'; return frame.pose.yaw <= -7.5 ? 0 : frame.pose.yaw >= 7.5 ? 1 : -1; }
function withoutID(ids: number[], id: number) { 'worklet'; const remaining: number[] = []; for (const value of ids) if (value !== id) remaining.push(value); return remaining; }
function moveSession(session: Session, point: Point, now: number): Session {
  'worklet';
  if (session.lastPoint.x === point.x && session.lastPoint.y === point.y) return session;
  const dt = Math.max(1 / 240, now - session.lastDate), pointer = { x: (point.x - session.lastPoint.x) / dt, y: (point.y - session.lastPoint.y) / dt };
  const age = Math.max(0, now - session.supportDate);
  return { ...session, lastPoint: point, lastDate: now, supportDate: now, pointer, translation: { x: point.x - session.anchor.x, y: point.y - session.anchor.y },
    yawSupport: retarget(session.yawSupport, pointer.x, age), pitchSupport: retarget(session.pitchSupport, -pointer.y, age), supportActive: true, changed: true, terminal: null };
}
class LenticularEditionPassTimers {
  pending: ReturnType<typeof setTimeout>[] = [];
  clear() { for (const timer of this.pending) clearTimeout(timer); this.pending = []; }
  schedule(event: Event, finish: (id: number, phase: 1 | 2 | 3) => void) {
    this.clear();
    const times: [number, 1 | 2 | 3][] = [[settleDuration(event), 1]];
    if (event.kind !== 'reduced' || hasCollection(event)) times.push([2, 2], [event.kind === 'reduced' ? 2.18 : 2.65, 3]);
    for (const [age, phase] of times) this.pending.push(setTimeout(() => finish(event.id, phase), Math.max(0, (event.stamp + age - Date.now() / 1000) * 1000)));
  }
}
function PrintView({ art, index, pose, s, onChoose }: { art: Resources | null; index: number; pose: SharedValue<Frame>; s: number; onChoose: (view: number) => void }) {
  const picture = useMemo(() => art ? thumbnail(art, index) : null, [art, index]);
  const border = useAnimatedStyle(() => { const selected = (meanViewB(pose.get()) > 0.5) === (index === 1); return { borderColor: selected ? '#192C35' : '#CDD4D0', borderWidth: (selected ? 1.2 : 0.6) * s }; });
  const props = useAnimatedProps<ViewProps>(() => ({ accessibilityState: { selected: (meanViewB(pose.get()) > 0.5) === (index === 1) } }));
  return <AnimatedPressable animatedProps={props} onPress={() => onChoose(index)} accessibilityRole="button" accessibilityLabel={index === 0 ? 'View one' : 'View two'} style={{ position: 'absolute', left: (24 + index * 70) * s, top: 647.5 * s, width: 58 * s, height: 44 * s, justifyContent: 'center' }}>
    <View pointerEvents="none" style={{ width: 58 * s, height: 37 * s, borderRadius: 3 * s, overflow: 'hidden' }}>
      {picture && <Canvas pointerEvents="none" style={{ width: 58 * s, height: 37 * s }}><Group transform={[{ scale: s }]}><Picture picture={picture} /></Group></Canvas>}
      <Animated.View style={[StyleSheet.absoluteFill, { borderRadius: 3 * s }, border]} />
    </View>
  </AnimatedPressable>;
}

/** A bowed laminated edition reveals alternate original prints through fixed material ridges. */
export function LenticularEditionPass({ autoPlay = false, previewTime, onBack }: Props) {
  const art = useLenticularResources(), ready = art !== null, initialReduced = useReducedMotion(), safe = useSafeAreaInsets();
  const [reduced, setReduced] = useState(initialReduced), [active, setActive] = useState(true), [size, setSize] = useState({ width: 0, height: 0 });
  const [flags, setFlags] = useState(0), [timers] = useState(() => new LenticularEditionPassTimers());
  const s = size.width / 393, height = s > 0 ? Math.max(852, size.height / s) : 852, bottom = s > 0 ? Math.max(0, safe.bottom / s - 34) : 0;
  const m = useSharedValue<Motion>(initialMotion());
  const pose = useDerivedValue(() => drive(m.get(), ready && autoPlay, reduced, ready ? previewTime : undefined));
  const clearTimers = useCallback(() => timers.clear(), [timers]);
  const finishEvent = useCallback((id: number, phase: 1 | 2 | 3) => { 'worklet'; const old = m.get(), next = completeEvent(old, id, phase, Date.now() / 1000); if (next !== old) m.set(next); }, [m]);
  const scheduleEvent = useCallback((event: Event) => timers.schedule(event, (id, phase) => scheduleOnUI(finishEvent, id, phase)), [finishEvent, timers]);
  const reset = useCallback((visible: boolean, resetView: boolean) => { 'worklet'; m.set(resetMotion(m.get(), Date.now() / 1000, visible, reduced, resetView)); }, [m, reduced]);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduced(value); });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => { mounted = false; sub.remove(); };
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', state => { const visible = state === 'active'; setActive(visible); clearTimers(); scheduleOnUI(reset, visible, false); });
    return () => sub.remove();
  }, [clearTimers, reset]);
  useEffect(() => { clearTimers(); scheduleOnUI(reset, AppState.currentState === 'active', true); }, [clearTimers, ready, reduced, reset]);
  useEffect(() => { clearTimers(); scheduleOnUI(reset, AppState.currentState === 'active', false); }, [autoPlay, clearTimers, previewTime, reset]);
  useEffect(() => () => { clearTimers(); scheduleOnUI(reset, false, false); }, [clearTimers, reset]);
  const ticker = useFrameCallback(() => {
    const old = m.get(), now = Date.now() / 1000, session = old.session;
    if (session?.supportActive && now - session.supportDate >= 0.60) {
      m.set({ ...old, now, session: { ...session, supportActive: false, terminal: dragged(session.base, session.translation, session.yawSupport, session.pitchSupport, 0.60) } });
    } else m.set({ ...old, now });
  }, false);
  useAnimatedReaction(() => {
    const state = m.get(); return state.session ? (state.session.supportActive ? 3 : 1) : state.event ? (state.event.phase === 1 ? 4 : 8) : 0;
  }, (next, previous) => { if (next !== previous) scheduleOnRN(setFlags, next); });
  useEffect(() => {
    const moving = flags === 3 || flags === 8 || (flags === 0 && autoPlay && !reduced);
    ticker.setActive(active && ready && s > 0 && (reduced || previewTime === undefined) && moving);
    return () => ticker.setActive(false);
  }, [active, autoPlay, flags, previewTime, ready, reduced, s, ticker]);
  useAnimatedReaction(() => fullView(pose.get()), (next, previous) => {
    if (next === previous) return;
    const old = m.get(), session = old.session; if (!session) return;
    let hapticArmed = session.hapticArmed, hapticView = session.hapticView;
    if (next === -1) hapticArmed = true;
    else if (next !== hapticView && hapticArmed) { hapticView = next; hapticArmed = false; scheduleOnRN(impact); }
    if (hapticArmed !== session.hapticArmed || hapticView !== session.hapticView) m.set({ ...old, session: { ...session, hapticArmed, hapticView } });
  });
  const picture = useDerivedValue(() => draw(pose.get(), art, height));
  const cardProps = useAnimatedProps<ViewProps>(() => ({ accessibilityValue: { text: meanViewB(pose.get()) > 0.5 ? 'View two of two' : 'View one of two' } }));
  const ctaProps = useAnimatedProps<ViewProps>(() => ({ accessibilityLabel: pose.get().collectionMix >= 0.5 ? 'Collected' : 'Add to collection' }));
  const ordinaryCopy = useAnimatedStyle(() => ({ opacity: 1 - pose.get().collectionMix }));
  const collectedCopy = useAnimatedStyle(() => ({ opacity: pose.get().collectionMix }));
  const begin = useCallback((id: number, point: Point) => {
    'worklet'; const old = m.get();
    if (!old.visible || !ready || reduced || previewTime !== undefined || old.ignored.includes(id)) return false;
    if (old.session) { m.set({ ...old, ignored: [...old.ignored, id] }); return false; }
    const captured = pose.get();
    if (!inverseHit(point, captured.pose)) { m.set({ ...old, ignored: [...old.ignored, id] }); return false; }
    const now = Date.now() / 1000, generation = old.generation + 1;
    m.set({ ...old, now, generation, event: null, session: { id, generation, base: captured, anchor: point, lastPoint: point, lastDate: now, supportDate: now, pointer: { x: 0, y: 0 }, translation: { x: 0, y: 0 }, yawSupport: zeroSupport(), pitchSupport: zeroSupport(), supportActive: false, changed: false, terminal: null, hapticView: meanViewB(captured) > 0.5 ? 1 : 0, hapticArmed: true } });
    scheduleOnRN(clearTimers); return true;
  }, [clearTimers, m, pose, previewTime, ready, reduced]);
  const move = useCallback((id: number, point: Point) => {
    'worklet'; const old = m.get(), current = old.session; if (!current || current.id !== id) return;
    const now = Date.now() / 1000, next = moveSession(current, point, now); if (next !== current) m.set({ ...old, now, session: next });
  }, [m]);
  const end = useCallback((id: number, point: Point | null, cancelled: boolean) => {
    'worklet'; const old = m.get(), current = old.session;
    if (!current || current.id !== id) { if (old.ignored.includes(id)) m.set({ ...old, ignored: withoutID(old.ignored, id) }); return; }
    const now = Date.now() / 1000, session = !cancelled && point ? moveSession(current, point, now) : current;
    const age = Math.max(0, now - session.lastDate), supportAge = Math.max(0, now - session.supportDate);
    const captured = { ...heldFrame(session, now), angularVelocity: releaseVelocity(session.base, session.translation, session.pointer, age, session.changed ? null : session.base.angularVelocity, session.yawSupport, session.pitchSupport, supportAge) };
    const aged = 1 - smooth(age / 0.12), pointer = { x: clamp(session.pointer.x, -1200, 1200) * aged, y: clamp(session.pointer.y, -1200, 1200) * aged };
    const event: Event = { id: old.generation + 1, stamp: now, from: captured, kind: cancelled ? 'cancel' : 'release', view: targetView(captured, pointer), base: session.base, collection: false, phase: 0 };
    m.set({ ...old, now, generation: event.id, session: null, event, ignored: withoutID(old.ignored, id) }); scheduleOnRN(scheduleEvent, event);
  }, [m, scheduleEvent]);
  // Raw touch callbacks deliver true Down without a drag recognizer activation segment.
  const touch = useMemo(() => Gesture.Manual().enabled(ready && active && !reduced && previewTime === undefined).shouldCancelWhenOutside(false)
    .onTouchesDown((event, manager) => {
      for (const point of event.changedTouches) if (s > 0 && begin(point.id, { x: point.absoluteX / s, y: point.absoluteY / s })) manager.activate();
    })
    .onTouchesMove(event => { for (const point of event.changedTouches) if (s > 0) move(point.id, { x: point.absoluteX / s, y: point.absoluteY / s }); })
    .onTouchesUp((event, manager) => {
      for (const point of event.changedTouches) if (s > 0) end(point.id, { x: point.absoluteX / s, y: point.absoluteY / s }, false);
      if (event.numberOfTouches === 0) manager.end();
    })
    .onTouchesCancelled((event, manager) => { for (const point of event.changedTouches) end(point.id, null, true); manager.end(); })
    .onFinalize(() => { const old = m.get(); if (old.session) end(old.session.id, null, true); }), [active, begin, end, m, move, previewTime, ready, reduced, s]);
  const choose = useCallback((view: number) => {
    'worklet'; const old = m.get(); if (!old.visible || !ready || previewTime !== undefined || old.session) return;
    const now = Date.now() / 1000, captured = pose.get();
    const event: Event = { id: old.generation + 1, stamp: now, from: captured, kind: reduced ? 'reduced' : 'release', view, base: captured, collection: false, phase: 0 };
    m.set({ ...old, now, generation: event.id, reducedTargetView: reduced ? view : old.reducedTargetView, event }); scheduleOnRN(scheduleEvent, event); scheduleOnRN(impact);
  }, [m, pose, previewTime, ready, reduced, scheduleEvent]);
  const collect = useCallback(() => {
    'worklet'; const old = m.get(); if (!old.visible || !ready || previewTime !== undefined || old.session) return;
    const now = Date.now() / 1000, captured = pose.get();
    const event: Event = { id: old.generation + 1, stamp: now, from: captured, kind: reduced ? 'reduced' : 'collect', view: old.reducedTargetView, base: captured, collection: true, phase: 0 };
    m.set({ ...old, now, generation: event.id, event }); scheduleOnRN(scheduleEvent, event); scheduleOnRN(impact);
  }, [m, pose, previewTime, ready, reduced, scheduleEvent]);
  const back = useCallback(() => {
    'worklet'; const old = m.get(); if (!old.visible || previewTime !== undefined || old.session) return; m.set(resetMotion(old, Date.now() / 1000, true, reduced)); scheduleOnRN(clearTimers); if (onBack) scheduleOnRN(onBack);
  }, [clearTimers, m, onBack, previewTime, reduced]);
  const onLayout = useCallback((event: LayoutChangeEvent) => setSize(event.nativeEvent.layout), []);
  const box = (x: number, y: number, width: number, h: number) => ({ position: 'absolute' as const, left: x * s, top: y * s, width: width * s, height: h * s });
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="dark" />
    {s > 0 && <>
      <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}><Group transform={[{ scale: s }]}><Picture picture={picture} /></Group></Canvas>
      <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={() => scheduleOnUI(back)} style={[box(14, 70, 44, 44), styles.center]}><Svg width={24 * s} height={24 * s} viewBox="0 0 24 24"><Path d="M20 12H4M4 12L11 5M4 12L11 19" stroke="#192C35" strokeWidth={1.6} fill="none" strokeLinecap="round" strokeLinejoin="round" /></Svg></Pressable>
      <Text allowFontScaling={false} style={[styles.copy, box(62, 81, 220, 20), { fontSize: 10.5 * s, fontWeight: '500', letterSpacing: 1.6 * s, lineHeight: 20 * s }]}>EDITION / 07</Text>
      <View accessible accessibilityLabel="Between two views" style={box(24, 121, 345, 76)}>
        <Text accessible={false} allowFontScaling={false} style={[styles.copy, { fontSize: 32 * s, letterSpacing: -0.8 * s, lineHeight: 35 * s }]}>Between</Text>
        <Text accessible={false} allowFontScaling={false} style={[styles.copy, { fontSize: 32 * s, letterSpacing: -0.8 * s, lineHeight: 35 * s }]}>two views</Text>
      </View>
      <Text allowFontScaling={false} style={[styles.copy, box(275, 210, 94, 25), { textAlign: 'center', fontSize: 10 * s, lineHeight: 25 * s, fontWeight: '500', letterSpacing: s, borderRadius: 12.5 * s, borderWidth: 0.8 * s, borderColor: '#CDD4D0' }]}>02 VIEWS</Text>
      <GestureDetector gesture={touch}><Animated.View collapsable={false} animatedProps={cardProps} accessible accessibilityRole="adjustable" accessibilityLabel="Quiet Orbit, edition 7 of 128" accessibilityHint="Drag to turn the card, or adjust to choose a view." accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => { const name = event.nativeEvent.actionName; if (name === 'increment' || name === 'decrement') scheduleOnUI(choose, name === 'increment' ? 1 : 0); }} style={box(21.5, 242.5, 350, 265)} /></GestureDetector>
      <Text allowFontScaling={false} style={[styles.copy, box(24, 549, 345, 31), { fontSize: 23 * s, fontWeight: '500', lineHeight: 31 * s }]}>Quiet Orbit</Text>
      <Text allowFontScaling={false} style={[styles.copy, styles.secondary, box(24, 586, 345, 19), { fontSize: 12.5 * s, lineHeight: 19 * s }]}>No. 07 of 128</Text>
      <Text allowFontScaling={false} style={[styles.copy, styles.secondary, box(24, 608, 345, 19), { fontSize: 12.5 * s, lineHeight: 19 * s }]}>An angle changes the story.</Text>
      <PrintView art={art} index={0} pose={pose} s={s} onChoose={view => scheduleOnUI(choose, view)} />
      <PrintView art={art} index={1} pose={pose} s={s} onChoose={view => scheduleOnUI(choose, view)} />
      <Text allowFontScaling={false} style={[styles.copy, styles.secondary, box(24, 711, 345, 19), { fontSize: 12 * s, lineHeight: 19 * s }]}>Turn it in your hands.</Text>
      <AnimatedPressable animatedProps={ctaProps} accessibilityRole="button" accessibilityLabel="Add to collection" onPress={() => scheduleOnUI(collect)} style={[box(24, 755 - bottom, 345, 54), styles.center, { borderRadius: 27 * s, backgroundColor: '#20343E' }]}>
        <Animated.Text accessible={false} allowFontScaling={false} style={[styles.cta, { fontSize: 15.5 * s }, ordinaryCopy]}>Add to collection</Animated.Text>
        <Animated.View accessible={false} pointerEvents="none" style={[styles.collected, collectedCopy]}><Svg width={13 * s} height={13 * s} viewBox="0 0 16 16"><Path d="M2 8L6 12L14 3" fill="none" stroke="#F2F2E9" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" /></Svg><Text accessible={false} allowFontScaling={false} style={[styles.cta, { position: 'relative', marginLeft: 8 * s, fontSize: 15.5 * s }]}>Collected</Text></Animated.View>
      </AnimatedPressable>
    </>}
  </View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, overflow: 'hidden', backgroundColor: '#EDEFE9' }, center: { justifyContent: 'center', alignItems: 'center' }, copy: { color: '#192C35', padding: 0, margin: 0, includeFontPadding: false, textAlignVertical: 'center' }, secondary: { color: '#78868B' }, cta: { position: 'absolute', color: '#F2F2E9', fontWeight: '600', includeFontPadding: false }, collected: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' } });
