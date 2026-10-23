import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { draw } from './art';
import { begin, clamp, drag, drive, frozen, handles, makeRelease, metrics, nearestDetent, pinch, staticFrame, type Frame, type Motion, type Owner, type ReleaseVelocity, type Session } from './motion';
import { useAuxeticResources } from './resources';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number };
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
function tactile(impact = false) { if (impact) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); else Haptics.selectionAsync().catch(() => {}); }
function ownerBit(owner: Owner) { 'worklet'; return owner === 'left' ? 1 : owner === 'right' ? 2 : 4; }
function movingSession(s: Session, coordinate: number, velocity: number, now: number): Session {
  'worklet'; if (coordinate === s.coordinate) return s;
  const input = s.owner === 'pinch' ? pinch(s.capture, coordinate / s.anchor, velocity / s.anchor) : drag(s.capture, s.owner, coordinate - s.anchor, velocity);
  return { ...s, coordinate, input, lastMovement: now, movementInput: input };
}
function withTick(old: Motion, angle: number, now: number) {
  'worklet'; const zone = nearestDetent(angle, 0, old.hapticZone); if (zone === old.hapticZone) return { state: old, tick: false }; const tick = now - old.lastTick >= .20; return { state: { ...old, hapticZone: zone, lastTick: tick ? now : old.lastTick }, tick };
}
class AuxeticFitLabSaveTimer {
  pending: ReturnType<typeof setTimeout> | null = null;
  clear() { if (this.pending !== null) clearTimeout(this.pending); this.pending = null; }
  schedule(stamp: number, finish: () => void) { this.clear(); this.pending = setTimeout(finish, Math.max(0, (stamp + 2 - Date.now() / 1000) * 1000)); }
}
function MetricReadout({ pose, extension, s, x }: { pose: SharedValue<Frame>; extension: boolean; s: number; x: number }) {
  const value = useAnimatedProps(() => { const m = metrics(pose.get().angle), text = `${(extension ? m.extensionPercent : m.openAreaPercent).toFixed(1)}%`; return { text, defaultValue: text }; });
  return <AnimatedInput animatedProps={value} defaultValue={extension ? '0.0%' : '12.2%'} editable={false} caretHidden contextMenuHidden accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" allowFontScaling={false} style={[styles.readout, { position: 'absolute', left: x * s, top: 611 * s, width: 156 * s, height: 40 * s, fontSize: 30 * s }]} />;
}

/** A single rotating-square linkage; input owns its complete captured pose until release. */
export function AuxeticFitLab({ autoPlay = false, previewTime }: Props) {
  const art = useAuxeticResources(), ready = art !== null, initialReduced = useReducedMotion(), safe = useSafeAreaInsets();
  const [reduced, setReduced] = useState(initialReduced), [active, setActive] = useState(true), [size, setSize] = useState({ width: 0, height: 0 }), [flags, setFlags] = useState(0), [timer] = useState(() => new AuxeticFitLabSaveTimer());
  const s = size.width / 393, height = s > 0 ? Math.max(852, size.height / s) : 852, bottom = s > 0 ? Math.max(0, safe.bottom / s - 34) : 0, held = (flags & 1) !== 0, hasEvent = (flags & 2) !== 0, saved = (flags & 4) !== 0;
  const m = useSharedValue<Motion>({ now: 0, epoch: 0, visible: true, generation: 0, session: null, blocked: 0, tokens: { left: null, right: null, pinch: null }, event: null, saved: false, reducedAngle: 42, quietSave: null, previousDetent: 4, hapticZone: 4, lastTick: -10 });
  const pose = useDerivedValue(() => drive(m.get(), ready && autoPlay, reduced, ready ? previewTime : undefined));
  const clearQuiet = useCallback(() => timer.clear(), [timer]);
  const finishQuiet = useCallback((stamp: number) => { 'worklet'; const old = m.get(); if (old.quietSave === stamp) m.set({ ...old, saved: false, quietSave: null }); }, [m]);
  const scheduleQuiet = useCallback((stamp: number) => timer.schedule(stamp, () => scheduleOnUI(finishQuiet, stamp)), [finishQuiet, timer]);
  const reset = useCallback((visible: boolean, resetAngle = false) => {
    'worklet'; const old = m.get(), now = Date.now() / 1000; m.set({ ...old, now, epoch: now, visible, generation: old.generation + 1, session: null, blocked: 0, tokens: { left: null, right: null, pinch: null }, event: null, saved: false, reducedAngle: resetAngle ? 42 : old.reducedAngle, quietSave: null, previousDetent: 4, hapticZone: 4 });
  }, [m]);
  useEffect(() => { const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced); return () => sub.remove(); }, []);
  useEffect(() => { const sub = AppState.addEventListener('change', state => { const visible = state === 'active'; setActive(visible); clearQuiet(); scheduleOnUI(reset, visible); }); return () => sub.remove(); }, [clearQuiet, reset]);
  useEffect(() => { clearQuiet(); scheduleOnUI(reset, AppState.currentState === 'active', true); }, [clearQuiet, ready, reduced, reset]);
  const restartAutoplay = useCallback(() => { 'worklet'; const old = m.get(); if (!old.session && !old.event) { const now = Date.now() / 1000; m.set({ ...old, now, epoch: now }); } }, [m]);
  useEffect(() => { if (autoPlay) scheduleOnUI(restartAutoplay); }, [autoPlay, restartAutoplay]);
  useEffect(() => () => { clearQuiet(); }, [clearQuiet]);
  const ticker = useFrameCallback(() => {
    const old = m.get(), now = Date.now() / 1000;
    if (!old.session && old.event && now - old.event.stamp >= 2.60) m.set({ ...old, now, epoch: old.event.stamp + 2.60, event: null, saved: false, previousDetent: 4 });
    else m.set({ ...old, now });
  }, false);
  useEffect(() => { ticker.setActive(active && ready && s > 0 && !reduced && previewTime === undefined && !held && (autoPlay || hasEvent)); return () => ticker.setActive(false); }, [active, autoPlay, hasEvent, held, previewTime, ready, reduced, s, ticker]);
  useAnimatedReaction(() => { const state = m.get(); return (state.session ? 1 : 0) | (state.event ? 2 : 0) | (state.saved ? 4 : 0); }, (next, before) => { if (next !== before) scheduleOnRN(setFlags, next); });
  const picture = useDerivedValue(() => draw(pose.get(), art, height));
  const leftStyle = useAnimatedStyle(() => ({ left: handles(pose.get()).leftHit.x * s })), rightStyle = useAnimatedStyle(() => ({ left: handles(pose.get()).rightHit.x * s }));
  const pinchStyle = useAnimatedStyle(() => { const r = handles(pose.get()).pinchHit; return { left: r.x * s, top: (r.y - 250) * s, width: r.width * s, height: r.height * s }; });
  const latticeProps = useAnimatedProps<ViewProps>(() => { const v = metrics(pose.get().angle); return { accessibilityValue: { text: `Extension ${v.extensionPercent.toFixed(1)} percent, open area ${v.openAreaPercent.toFixed(1)} percent` } }; });
  const start = useCallback((owner: Owner, anchor: number) => {
    'worklet'; const old = m.get(); if (!old.visible || !ready || reduced || previewTime !== undefined || (old.blocked & ownerBit(owner)) !== 0) return;
    if (old.session) { if (old.session.owner !== owner) m.set({ ...old, blocked: old.blocked | ownerBit(owner), tokens: { ...old.tokens, [owner]: null } }); return; }
    const now = Date.now() / 1000, current = drive({ ...old, now }, autoPlay, reduced), visible = pose.get(), capture: Frame = { ...visible, angularVelocity: current.angularVelocity, left: { ...visible.left, offsetVelocityX: current.left.offsetVelocityX, scaleVelocity: current.left.scaleVelocity }, right: { ...visible.right, offsetVelocityX: current.right.offsetVelocityX, scaleVelocity: current.right.scaleVelocity }, arrowGainVelocity: current.arrowGainVelocity }, generation = old.generation + 1;
    m.set({ ...old, now, generation, session: { owner, generation, stamp: now, capture, anchor, coordinate: anchor, input: begin(capture), lastMovement: null, movementInput: null }, tokens: { ...old.tokens, [owner]: generation }, event: null, quietSave: null, hapticZone: nearestDetent(capture.angle, 0, old.previousDetent) }); scheduleOnRN(clearQuiet);
  }, [autoPlay, clearQuiet, m, pose, previewTime, ready, reduced]);
  const update = useCallback((owner: Owner, coordinate: number, velocity: number) => {
    'worklet'; const old = m.get(), current = old.session; if (!current || current.owner !== owner || old.tokens[owner] !== current.generation || coordinate === current.coordinate) return;
    const now = Date.now() / 1000, next = movingSession(current, coordinate, velocity, now), h = withTick(old, next.input.frame.angle, now); m.set({ ...h.state, now, session: next }); if (h.tick) scheduleOnRN(tactile);
  }, [m]);
  const end = useCallback((owner: Owner, coordinate: number, velocity: number, cancelled: boolean) => {
    'worklet'; const old = m.get(), current = old.session, tokens = { ...old.tokens, [owner]: null }, blocked = old.blocked & ~ownerBit(owner);
    if (!current || current.owner !== owner || old.tokens[owner] !== current.generation) { m.set({ ...old, blocked, tokens }); return; }
    const now = Date.now() / 1000, session = cancelled ? current : movingSession(current, coordinate, velocity, now), input = session.movementInput;
    const releaseVelocity: ReleaseVelocity = session.lastMovement !== null && input ? { kind: 'user', width: input.requestedWidthVelocity, angular: input.actualAngularVelocity, sampleAge: Math.max(0, now - session.lastMovement) } : { kind: 'inherited', frame: session.capture, heldElapsed: Math.max(0, now - session.stamp) };
    const release = makeRelease(session.input, releaseVelocity, old.previousDetent, cancelled), h = !cancelled && session.coordinate !== current.coordinate ? withTick(old, session.input.frame.angle, now) : { state: old, tick: false };
    m.set({ ...h.state, now, session: null, tokens, blocked, previousDetent: release.target ?? old.previousDetent, event: { stamp: now, held: frozen(session.input.frame), release } }); if (h.tick) scheduleOnRN(tactile);
  }, [m]);
  const pinchGesture = Gesture.Pinch().onStart(e => { if (s <= 0) return; const r = handles(pose.get()).pinchHit, x = e.focalX / s, y = e.focalY / s + 250; if (x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height) start('pinch', e.scale); }).onUpdate(e => update('pinch', e.scale, e.velocity)).onEnd(e => end('pinch', e.scale, e.velocity, false)).onFinalize((_e, success) => { if (!success) end('pinch', 1, 0, true); });
  const leftPan = Gesture.Pan().minDistance(0).activateAfterLongPress(1).simultaneousWithExternalGesture(pinchGesture).onBegin(e => { if (s > 0) start('left', e.translationX / s); }).onUpdate(e => { if (s > 0) update('left', e.translationX / s, e.velocityX / s); }).onEnd(e => { if (s > 0) end('left', e.translationX / s, e.velocityX / s, false); }).onFinalize((_e, success) => { if (!success) end('left', 0, 0, true); });
  const rightPan = Gesture.Pan().minDistance(0).activateAfterLongPress(1).simultaneousWithExternalGesture(pinchGesture).onBegin(e => { if (s > 0) start('right', e.translationX / s); }).onUpdate(e => { if (s > 0) update('right', e.translationX / s, e.velocityX / s); }).onEnd(e => { if (s > 0) end('right', e.translationX / s, e.velocityX / s, false); }).onFinalize((_e, success) => { if (!success) end('right', 0, 0, true); });
  pinchGesture.simultaneousWithExternalGesture(leftPan, rightPan);
  const save = useCallback(() => {
    'worklet'; const old = m.get(); if (old.session || !ready) return; const now = Date.now() / 1000; m.set({ ...old, now, saved: true, quietSave: reduced ? now : old.quietSave, event: reduced ? old.event : { stamp: now, held: frozen(pose.get()), release: null } }); if (reduced) scheduleOnRN(scheduleQuiet, now); scheduleOnRN(tactile, true);
  }, [m, pose, ready, reduced, scheduleQuiet]);
  const adjust = useCallback((increment: boolean) => {
    'worklet'; const old = m.get(); if (old.session || previewTime !== undefined) return; const current = pose.get(), angle = clamp(current.angle + (increment ? 5 : -5), 4, 42); if (angle === current.angle) return; const now = Date.now() / 1000, h = withTick(old, angle, now); m.set({ ...h.state, now, reducedAngle: reduced ? angle : old.reducedAngle, event: reduced ? old.event : { stamp: now, held: staticFrame(angle), release: null } }); if (h.tick) scheduleOnRN(tactile);
  }, [m, pose, previewTime, reduced]);
  const onLayout = useCallback((e: LayoutChangeEvent) => setSize(e.nativeEvent.layout), []);
  const box = (x: number, y: number, w: number, h: number) => ({ position: 'absolute' as const, left: x * s, top: y * s, width: w * s, height: h * s });
  const text = (fontSize: number, fontWeight: '400' | '500' | '600' = '400', color = '#213C3B') => ({ fontSize: fontSize * s, fontWeight, color, includeFontPadding: false });
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="dark" />
    {s > 0 && <>
      <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}><Group transform={[{ scale: s }]}><Picture picture={picture} /></Group></Canvas>
      <View style={[box(24, 76, 345, 17), styles.centerY]}><Text allowFontScaling={false} style={[text(10.5, '600'), { letterSpacing: 1.8 * s }]}>HINGE / MATERIAL LAB</Text></View>
      <View style={[box(24, 105, 345, 38), styles.centerY]}><Text allowFontScaling={false} style={[text(30, '600'), { letterSpacing: -.6 * s }]}>Room to move</Text></View>
      <View style={[box(24, 151, 345, 20), styles.centerY]}><Text allowFontScaling={false} style={text(13, '400', '#798885')}>A rigid pattern with a softer fit.</Text></View>
      <View style={[box(24, 232, 145, 18), styles.centerY]}><Text allowFontScaling={false} style={[text(10.5, '500', '#798885'), { fontFamily: 'Menlo' }]}>SAMPLE 01</Text></View>
      <View style={[box(199, 232, 170, 18), styles.centerY]}><Text allowFontScaling={false} style={[text(12), { textAlign: 'right' }]}>Rotating squares</Text></View>
      <View pointerEvents="none" accessible={false} style={[box(24, 565.7, 345, .6), { backgroundColor: 'rgba(121,136,133,.28)' }]} />
      <View pointerEvents="none" accessible={false} style={[box(196.2, 586, .6, 67), { backgroundColor: 'rgba(121,136,133,.22)' }]} />
      <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[box(24, 586, 156, 18), styles.centerY]}><Text allowFontScaling={false} style={text(11.5, '400', '#798885')}>Extension</Text></View>
      <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[box(213, 586, 156, 18), styles.centerY]}><Text allowFontScaling={false} style={text(11.5, '400', '#798885')}>Open area</Text></View>
      <MetricReadout pose={pose} extension s={s} x={24} /><MetricReadout pose={pose} extension={false} s={s} x={213} />
      <View style={[box(24, 687, 345, 20), styles.centerY]}><Text allowFontScaling={false} style={text(12.5, '400', '#798885')}>Pull a handle. Both directions grow.</Text></View>
      <Pressable accessibilityRole="button" accessibilityLabel={saved ? 'Fit saved' : 'Save fit'} onPress={() => scheduleOnUI(save)} style={[box(24, 755 - bottom, 345, 54), styles.center, { borderRadius: 27 * s, backgroundColor: '#213C3B' }]}><Text allowFontScaling={false} style={text(15.5, '600', '#F6F8F4')}>{saved ? '✓  Fit saved' : 'Save fit'}</Text></Pressable>
      <GestureDetector gesture={pinchGesture}><View collapsable={false} pointerEvents="box-none" style={box(0, 250, 393, 310)}>
        <Animated.View animatedProps={latticeProps} style={[styles.absolute, pinchStyle]} accessible accessibilityRole="adjustable" accessibilityLabel="Auxetic lattice" accessibilityHint={reduced ? 'Adjust the sample angle.' : 'Pull a handle or pinch to expand the pattern.'} accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={e => { if (e.nativeEvent.actionName === 'increment' || e.nativeEvent.actionName === 'decrement') scheduleOnUI(adjust, e.nativeEvent.actionName === 'increment'); }} />
        {!reduced && <>
          <GestureDetector gesture={leftPan}><Animated.View accessible accessibilityLabel="Left expansion handle" accessibilityHint="Drag horizontally to expand both dimensions." style={[box(14, 119, 44, 60), leftStyle]} /></GestureDetector>
          <GestureDetector gesture={rightPan}><Animated.View accessible accessibilityLabel="Right expansion handle" accessibilityHint="Drag horizontally to expand both dimensions." style={[box(335, 119, 44, 60), rightStyle]} /></GestureDetector>
        </>}
      </View></GestureDetector>
    </>}
  </View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#F6F8F4', overflow: 'hidden' }, absolute: { position: 'absolute' }, center: { alignItems: 'center', justifyContent: 'center' }, centerY: { justifyContent: 'center' }, readout: { padding: 0, margin: 0, color: '#213C3B', fontWeight: '600', fontVariant: ['tabular-nums'], textAlignVertical: 'center', includeFontPadding: false } });
