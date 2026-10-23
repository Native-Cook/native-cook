import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, StyleSheet, Text, View, type TextStyle, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withSpring } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { accessibleBrush, begin, captureControl, choose, COLORS, complete, confirmation, controlPressed, currentFields, drive, end, initial, move, NAMES, needsClock, reset, save, tintAt, touchHit, type Motion, type Tint } from './driver';
import { type Frame, type Point } from './motion';
import { draw, prepareBindings, thumbnails, useVelvetResources, type Bindings } from './resources';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number; onBack?: () => void };
type Pack = { motion: Motion; bindings: Bindings | null };
type UI = { selected: number; yards: number; liked: boolean; added: boolean };
type Action = 'back' | 'bag' | 'color' | 'yards' | 'favorite' | 'minus' | 'plus' | 'save' | 'brushUp' | 'brushDown' | number;
type Pose = { frame: Frame; tint: Tint };
const INK = '#171C16', MUTED = '#787E72';
function impact() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }
function addedFeedback() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {}); AccessibilityInfo.announceForAccessibility('Sample added'); }
function hex(color: number) { return `#${color.toString(16).padStart(6, '0')}`; }

/** Raw Down is independent of press recognition and remains stable while the canvas ticks. */
function Control({ label, width, height, style, selected, onPressed, action, kind, children }: {
  label: string; width: number; height: number; style: ViewStyle; selected?: boolean;
  onPressed: (pressed: boolean) => void; action: (kind: Action) => void; kind: Action; children: ReactNode;
}) {
  const owner = useSharedValue({ id: -1, inside: false });
  const touch = useMemo(() => Gesture.Manual().shouldCancelWhenOutside(false)
    .onTouchesDown((event, manager) => {
      if (owner.get().id !== -1) return;
      const point = event.changedTouches[0]; if (!point) return;
      owner.set({ id: point.id, inside: true }); onPressed(true); manager.activate();
    })
    .onTouchesMove(event => {
      const current = owner.get(); if (current.id === -1) return;
      for (const point of event.changedTouches) if (point.id === current.id) {
        const inside = point.x >= 0 && point.x <= width && point.y >= 0 && point.y <= height;
        if (inside !== current.inside) { owner.set({ ...current, inside }); onPressed(inside); }
      }
    })
    .onTouchesUp((event, manager) => {
      const current = owner.get();
      for (const point of event.changedTouches) if (point.id === current.id) {
        const inside = point.x >= 0 && point.x <= width && point.y >= 0 && point.y <= height;
        owner.set({ id: -1, inside: false }); if (current.inside) onPressed(false); if (inside) action(kind);
      }
      if (event.numberOfTouches === 0) manager.end();
    })
    .onTouchesCancelled((_event, manager) => { const current = owner.get(); owner.set({ id: -1, inside: false }); if (current.inside) onPressed(false); manager.end(); })
    .onFinalize(() => { const current = owner.get(); owner.set({ id: -1, inside: false }); if (current.inside) onPressed(false); }), [action, height, kind, onPressed, owner, width]);
  return <GestureDetector gesture={touch}><View collapsable={false} accessible accessibilityRole="button" accessibilityLabel={label} accessibilityState={selected === undefined ? undefined : { selected }}
    onAccessibilityTap={() => scheduleOnUI(action, kind)} accessibilityActions={[{ name: 'activate' }]}
    onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') scheduleOnUI(action, kind); }} style={[style, styles.center]}>
    <View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.center}>{children}</View>
  </View></GestureDetector>;
}
function Icon({ name, size, color = INK, fill = false }: { name: string; size: number; color?: string; fill?: boolean }) {
  const paths: Record<string, string> = {
    back: 'M15 4L7 12L15 20', bag: 'M5 7H19L20 21H4L5 7ZM8 8V6C8 0 16 0 16 6V8',
    heart: 'M12 21C9 18 2 13 2 7.5C2 1.5 9 1 12 6C15 1 22 1.5 22 7.5C22 13 15 18 12 21Z',
    down: 'M5 8L12 15L19 8', plus: 'M4 12H20M12 4V20', minus: 'M4 12H20', check: 'M4 12L9 17L20 5',
    star: 'M12 2L15 8L22 9L17 14L18 21L12 18L6 21L7 14L2 9L9 8Z',
    leaf: 'M4 20C0 8 11 1 21 3C22 13 15 22 4 20ZM4 20L16 8',
    sparkle: 'M12 1L15 9L23 12L15 15L12 23L9 15L1 12L9 9Z',
    grid: 'M3 3H9V9H3ZM15 3H21V9H15ZM3 15H9V21H3ZM15 15H21V21H15Z',
    scissors: 'M9 9L21 21M9 15L21 3M9 7A4 4 0 1 1 1 7A4 4 0 1 1 9 7ZM9 17A4 4 0 1 1 1 17A4 4 0 1 1 9 17Z',
  };
  return <Svg width={size} height={size} viewBox="0 0 24 24"><Path d={paths[name] ?? paths.bag} stroke={color} strokeWidth={name === 'bag' ? 1.55 : 1.7} strokeLinecap="round" strokeLinejoin="round" fill={fill ? color : 'none'} /></Svg>;
}
class VelvetNapStudioTimers {
  pending: ReturnType<typeof setTimeout>[] = [];
  clear() { for (const timer of this.pending) clearTimeout(timer); this.pending = []; }
  schedule(stamp: number, held: boolean, addedAt: number, reduced: boolean, wake: () => void) {
    this.clear(); const dates: number[] = [];
    if (stamp >= 0 && !held) dates.push(stamp + 2, stamp + (reduced ? 2 : 2.6));
    if (addedAt >= 0) dates.push(addedAt + 1.2);
    for (const date of dates) if (date >= Date.now() / 1000 - .01) this.pending.push(setTimeout(wake, Math.max(0, (date - Date.now() / 1000) * 1000) + 1));
  }
}

/** A fixed folded swatch changes its directional pile beneath the finger. */
export function VelvetNapStudio({ autoPlay = false, previewTime, onBack }: Props) {
  const art = useVelvetResources(), ready = art !== null, initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced), [active, setActive] = useState(AppState.currentState === 'active');
  const [size, setSize] = useState({ width: 0, height: 0 }), [moving, setMoving] = useState(false);
  const [ui, setUI] = useState<UI>({ selected: 3, yards: 2, liked: false, added: false });
  const [timers] = useState(() => new VelvetNapStudioTimers());
  const pack = useSharedValue<Pack>({ motion: initial(), bindings: null }), ring = useSharedValue(3);
  const scale = Math.min(size.width / 393, size.height / 852), dx = (size.width - 393 * scale) / 2, dy = (size.height - 852 * scale) / 2;
  const pose = useDerivedValue<Pose>(() => { const m = pack.get().motion; return { frame: drive(m, ready && autoPlay, reduced, ready ? previewTime : undefined), tint: tintAt(m) }; });
  const install = useCallback((motion: Motion) => {
    'worklet'; const old = pack.get(), bindings = art ? prepareBindings(old.bindings ?? art.empty, currentFields(motion)) : null; pack.set({ motion, bindings });
  }, [art, pack]);
  const wake = useCallback(() => { 'worklet'; install(complete(pack.get().motion, Date.now() / 1000, reduced)); }, [install, pack, reduced]);
  const scheduleDeadlines = useCallback((stamp: number, held: boolean, addedAt: number) => timers.schedule(stamp, held, addedAt, reduced, () => scheduleOnUI(wake)), [reduced, timers, wake]);
  const resetState = useCallback((visible: boolean) => {
    'worklet'; const old = pack.get(); pack.set({ motion: reset(old.motion, Date.now() / 1000, visible), bindings: art?.empty ?? null });
  }, [art, pack]);
  useEffect(() => {
    let mounted = true; AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduced(value); });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced); return () => { mounted = false; sub.remove(); };
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', state => { const visible = state === 'active'; setActive(visible); timers.clear(); scheduleOnUI(resetState, visible); });
    return () => sub.remove();
  }, [resetState, timers]);
  useEffect(() => { timers.clear(); scheduleOnUI(resetState, AppState.currentState === 'active'); }, [autoPlay, previewTime, ready, reduced, resetState, timers]);
  useEffect(() => { if (reduced) scheduleOnUI(() => { 'worklet'; ring.set(pack.get().motion.selected); }); }, [pack, reduced, ring]);
  useEffect(() => () => { timers.clear(); scheduleOnUI(resetState, false); }, [resetState, timers]);
  const ticker = useFrameCallback(wake, false);
  useAnimatedReaction(() => needsClock(pack.get().motion, ready && autoPlay, reduced, ready ? previewTime : undefined), (next, previous) => { if (next !== previous) scheduleOnRN(setMoving, next); });
  useEffect(() => { ticker.setActive(active && ready && scale > 0 && moving); return () => ticker.setActive(false); }, [active, moving, ready, scale, ticker]);
  useAnimatedReaction(() => {
    const m = pack.get().motion; return [m.control?.stamp ?? m.event?.stamp ?? -1, m.controlHeld ? 1 : 0, m.addedAt ?? -1];
  }, (next, previous) => { if (!previous || next.some((n, i) => n !== previous[i])) scheduleOnRN(scheduleDeadlines, next[0], next[1] === 1, next[2]); });
  useAnimatedReaction(() => {
    const m = pack.get().motion; return [m.selected, m.yards, m.liked ? 1 : 0, confirmation(m) ? 1 : 0];
  }, (next, previous) => {
    if (!previous || next.some((n, i) => n !== previous[i])) scheduleOnRN(setUI, { selected: next[0], yards: next[1], liked: next[2] === 1, added: next[3] === 1 });
    if (!previous || next[0] !== previous[0]) ring.set(reduced ? next[0] : withSpring(next[0], { mass: 1, stiffness: (2 * Math.PI / .22) ** 2, damping: 2 * .85 * 2 * Math.PI / .22 }));
  });
  const ringStyle = useAnimatedStyle(() => ({ transform: [{ translateX: 43 * scale * ring.get() }] }));
  const picture = useDerivedValue(() => { const p = pose.get(); return draw(p.frame, p.tint, art, pack.get().bindings ?? art?.empty ?? null); });
  const thumbPicture = useMemo(() => thumbnails(art), [art]);
  const pressed = useCallback((isPressed: boolean) => {
    'worklet'; const old = pack.get().motion; if (!ready || previewTime !== undefined || !old.visible) return;
    const visible = pose.get(); install(controlPressed(old, isPressed, Date.now() / 1000, visible.frame, visible.tint));
  }, [install, pack, pose, previewTime, ready]);
  const action = useCallback((kind: Action) => {
    'worklet'; const old = pack.get().motion; if (!ready || previewTime !== undefined || !old.visible) return;
    const now = Date.now() / 1000, visible = pose.get(); let next: Motion;
    if (typeof kind === 'number' || kind === 'color') { const index = typeof kind === 'number' ? kind : (old.selected + 1) % 8; next = choose(old, index, now, visible.frame, visible.tint); if (index !== old.selected) scheduleOnRN(impact); }
    else if (kind === 'save' || kind === 'bag') { next = save(old, now, visible.frame, visible.tint); scheduleOnRN(addedFeedback); }
    else if (kind === 'brushUp' || kind === 'brushDown') next = accessibleBrush(old, kind === 'brushUp', now, visible.frame, visible.tint);
    else { next = captureControl(old, now, visible.frame, visible.tint); if (kind === 'favorite') next = { ...next, liked: !next.liked }; if (kind === 'yards') next = { ...next, yards: next.yards === 8 ? 1 : next.yards + 1 }; if (kind === 'minus') next = { ...next, yards: Math.max(1, next.yards - 1) }; if (kind === 'plus') next = { ...next, yards: Math.min(8, next.yards + 1) }; if (kind === 'back' && onBack) scheduleOnRN(onBack); }
    install(next);
  }, [install, onBack, pack, pose, previewTime, ready]);
  const toPoint = useCallback((x: number, y: number): Point => { 'worklet'; return { x: (x - dx) / scale - 43, y: (y - dy) / scale - 121 }; }, [dx, dy, scale]);
  const finish = useCallback((id: number, point: Point | null, cancelled: boolean) => {
    'worklet'; const visible = pose.get(); install(end(pack.get().motion, id, point, Date.now() / 1000, reduced, cancelled, visible.frame, visible.tint));
  }, [install, pack, pose, reduced]);
  const beginTouch = useCallback((id: number, point: Point) => {
    'worklet'; const old = pack.get().motion; if (!old.session && !touchHit(point)) return false;
    const visible = pose.get(); install(begin(old, id, point, Date.now() / 1000, visible.frame, visible.tint)); return true;
  }, [install, pack, pose]);
  const moveTouch = useCallback((id: number, point: Point) => {
    'worklet'; const old = pack.get().motion, next = move(old, id, point, Date.now() / 1000, reduced); if (next !== old) install(next);
  }, [install, pack, reduced]);
  const fabric = useMemo(() => Gesture.Manual().enabled(ready && active && scale > 0 && previewTime === undefined).shouldCancelWhenOutside(false)
    .onTouchesDown((event, manager) => {
      for (const touch of event.changedTouches) if (beginTouch(touch.id, toPoint(touch.absoluteX, touch.absoluteY))) manager.activate();
    })
    .onTouchesMove(event => { for (const touch of event.changedTouches) moveTouch(touch.id, toPoint(touch.absoluteX, touch.absoluteY)); })
    .onTouchesUp((event, manager) => { for (const touch of event.changedTouches) finish(touch.id, toPoint(touch.absoluteX, touch.absoluteY), false); if (event.numberOfTouches === 0) manager.end(); })
    .onTouchesCancelled((event, manager) => { for (const touch of event.changedTouches) finish(touch.id, null, true); manager.end(); })
    .onFinalize(() => { const session = pack.get().motion.session; if (session) finish(session.id, null, true); }), [active, beginTouch, finish, moveTouch, pack, previewTime, ready, scale, toPoint]);
  const onLayout = useCallback((event: LayoutChangeEvent) => setSize(event.nativeEvent.layout), []);
  const box = (x: number, y: number, width: number, height: number): { position: 'absolute'; left: number; top: number; width: number; height: number } => ({ position: 'absolute', left: dx + x * scale, top: dy + y * scale, width: width * scale, height: height * scale });
  const copy = (x: number, y: number, width: number, height: number, font: number, weight: '400' | '500' | '600' = '400', color = INK): TextStyle => ({ ...box(x, y, width, height), color, fontSize: font * scale, lineHeight: height * scale, fontWeight: weight });
  const control = (kind: Action, label: string, x: number, y: number, width: number, height: number, children: ReactNode, selected?: boolean) => <Control key={String(kind)} kind={kind} action={action} label={label} selected={selected} width={width * scale} height={height * scale} style={box(x, y, width, height)} onPressed={pressed}>{children}</Control>;
  return <View onLayout={onLayout} style={styles.screen}><StatusBar style="dark" />{scale > 0 && <>
    <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
      <Group transform={[{ translateX: dx + 43 * scale }, { translateY: dy + 121 * scale }, { scale }]}><Picture picture={picture} /></Group>
      <Group transform={[{ translateX: dx }, { translateY: dy }, { scale }]}><Picture picture={thumbPicture} /></Group>
    </Canvas>
    {control('back', 'Back', 16, 55, 44, 44, <Icon name="back" size={18 * scale} />)}
    <Text allowFontScaling={false} style={[styles.copy, copy(86.5, 69, 220, 20, 13, '500'), styles.centerText]}>Material studio</Text>
    {control('bag', 'Sample bag', 333, 55, 44, 44, <Icon name="bag" size={18 * scale} />)}
    <GestureDetector gesture={fabric}><View collapsable={false} accessible accessibilityRole="adjustable" accessibilityLabel="Folded crushed velvet fabric" accessibilityValue={{ text: `Directional pile, ${NAMES[ui.selected]}` }} accessibilityHint="Brush the fabric in either direction to change the nap." accessibilityActions={[{ name: 'increment', label: 'Brush up' }, { name: 'decrement', label: 'Brush down' }]} onAccessibilityAction={event => scheduleOnUI(action, event.nativeEvent.actionName === 'increment' ? 'brushUp' : 'brushDown')} style={box(31, 109, 331, 298)} /></GestureDetector>
    <Animated.View pointerEvents="none" accessible={false} style={[box(25, 402, 40, 40), { borderRadius: 20 * scale, borderWidth: scale, borderColor: '#BDC8AB' }, ringStyle]} />
    {NAMES.map((name, index) => control(index, `${name} fabric`, 23 + 43 * index, 400, 44, 44, null, index === ui.selected))}
    <View accessible accessibilityLabel={`Crushed velvet, ${NAMES[ui.selected]} No. ${String(ui.selected + 1).padStart(2, '0')}`} style={box(24, 463, 303, 44)}>
      <Text accessible={false} allowFontScaling={false} style={[styles.copy, { fontSize: 18 * scale, lineHeight: 22 * scale, fontWeight: '600' }]}>Crushed velvet</Text>
      <Text accessible={false} allowFontScaling={false} style={[styles.copy, { fontSize: 18 * scale, lineHeight: 22 * scale, fontWeight: '600' }]}>{NAMES[ui.selected]} No. {String(ui.selected + 1).padStart(2, '0')}</Text>
    </View>
    {control('favorite', ui.liked ? 'Remove favorite' : 'Favorite fabric', 329, 457, 44, 44, <Icon name="heart" size={18 * scale} fill={ui.liked} />)}
    <Text allowFontScaling={false} style={[styles.copy, copy(24, 516, 190, 26, 18, '600'), { fontVariant: ['tabular-nums'] }]}>$8.40 / yd</Text>
    <View style={[box(252, 521, 117, 20), styles.row, { justifyContent: 'flex-end', gap: 3 * scale }]}><Icon name="star" size={8 * scale} fill /><Text allowFontScaling={false} style={[styles.copy, { fontSize: 10 * scale }]}>4.8 (126 reviews)</Text></View>
    {control('color', `Color, ${NAMES[ui.selected]}`, 24, 556, 167, 44, <View style={[styles.row, { width: 167 * scale, height: 38 * scale, borderRadius: 19 * scale, backgroundColor: '#F0F2EB', paddingHorizontal: 17 * scale }]}><Text allowFontScaling={false} style={[styles.copy, { fontSize: 10 * scale, marginRight: 15 * scale }]}>Color</Text><View style={{ width: 32 * scale, height: 14 * scale, borderRadius: 16 * scale, backgroundColor: hex(COLORS[ui.selected]) }} /><View style={styles.spacer} /><Icon name="down" size={8 * scale} /></View>)}
    {control('yards', `Yards, ${ui.yards}`, 202, 556, 167, 44, <View style={[styles.row, { width: 167 * scale, height: 38 * scale, borderRadius: 19 * scale, backgroundColor: '#F0F2EB', paddingHorizontal: 17 * scale }]}><Text allowFontScaling={false} style={[styles.copy, { fontSize: 11 * scale }]}>Yards</Text><View style={styles.spacer} /><Text allowFontScaling={false} style={[styles.copy, { fontSize: 12 * scale, marginRight: 10 * scale, fontVariant: ['tabular-nums'] }]}>{ui.yards}</Text><Icon name="down" size={8 * scale} /></View>)}
    <Text allowFontScaling={false} style={[styles.copy, copy(24, 617, 345, 20, 13, '600')]}>Description</Text>
    <View style={box(24, 647, 345, 34)}><Text allowFontScaling={false} style={[styles.copy, { color: MUTED, fontSize: 12 * scale, lineHeight: 16 * scale }]}>A soft crushed pile with a quiet sheen.</Text><Text allowFontScaling={false} style={[styles.copy, { color: MUTED, fontSize: 12 * scale, lineHeight: 16 * scale }]}>Brush both ways to feel the difference.</Text></View>
    {['leaf', 'sparkle', 'grid', 'scissors'].map((name, i) => <View key={name} pointerEvents="none" accessible={false} style={[box(19.5 + 14 * i, 695.5, 23, 23), styles.center, { borderRadius: 11.5 * scale, backgroundColor: ['#D6D8CA', '#BFC4B1', '#E0DECC', '#C9CAB9'][i] }]}><Icon name={name} size={9 * scale} color="#626A58" /></View>)}
    <Text allowFontScaling={false} style={[styles.copy, copy(96, 697, 230, 20, 11, '400', MUTED)]}>Made for slow projects</Text>
    <View pointerEvents="none" style={[box(24, 755, 99, 52), { backgroundColor: '#F0F2EB', borderRadius: 26 * scale }]} />
    {control('minus', 'Decrease yards', 24, 755, 44, 52, <Icon name="minus" size={12 * scale} />)}
    <Text accessible={false} allowFontScaling={false} style={[styles.copy, copy(60, 755, 27, 52, 12, '500'), styles.centerText]}>{ui.yards}</Text>
    {control('plus', 'Increase yards', 79, 755, 44, 52, <Icon name="plus" size={12 * scale} />)}
    <View pointerEvents="none" style={[box(135, 755, 234, 52), { backgroundColor: '#AFCC4F', borderRadius: 26 * scale }]} />
    {control('save', ui.added ? 'Sample added' : 'Add to samples', 135, 755, 234, 52, <View style={[styles.row, { gap: 9 * scale }]}><Icon name={ui.added ? 'check' : 'bag'} size={16 * scale} color="#25310F" /><Text allowFontScaling={false} style={[styles.copy, { color: '#25310F', fontSize: 14 * scale, fontWeight: '600' }]}>{ui.added ? 'Sample added' : 'Add to samples'}</Text></View>)}
  </>}</View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, overflow: 'hidden', backgroundColor: '#FAFBF7' }, center: { alignItems: 'center', justifyContent: 'center' }, centerText: { textAlign: 'center' }, row: { flexDirection: 'row', alignItems: 'center' }, spacer: { flex: 1 }, copy: { color: INK, padding: 0, margin: 0, includeFontPadding: false, textAlignVertical: 'center' } });
