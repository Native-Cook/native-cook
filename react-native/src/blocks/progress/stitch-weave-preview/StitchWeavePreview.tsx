import { Canvas, ClipOp, FilterMode, Group, ImageShader, MipmapMode, PaintStyle, Picture, Rect, Shader, Skia, TileMode, type SkColor } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { base, canonicalTime, clamp, columnRows, drive, freezeMaterial, hash, materialMix, moved, resetAmount, rotationMatrix, solidMaterial, YARNS, type Motion } from './motion';
import { makeResources, project, type Resources } from './resources';
import { MATERIAL_EFFECT } from './shader';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number };
let retainedResources: Resources | null = null;
function resources() { if (!retainedResources) retainedResources = makeResources(); return retainedResources; }
function color(hex: number, alpha = 1): SkColor { 'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha); }
function tactile(impact = false) { if (impact) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); else Haptics.selectionAsync().catch(() => {}); }
const AnimatedInput = Animated.createAnimatedComponent(TextInput);

/** Frozen Swift reference: yarn formation atlas, material arc lookup and full-pose manipulation. */
export function StitchWeavePreview({ autoPlay = false, previewTime }: Props) {
  const art = useMemo(() => resources(), []), initialReduced = useReducedMotion(), safe = useSafeAreaInsets();
  const [reduced, setReduced] = useState(initialReduced), [awake, setAwake] = useState(false), [size, setSize] = useState({ width: 0, height: 0 });
  const [controls, setControls] = useState({ pattern: 0, yarn: 0, saved: false });
  const s = size.width / 393, height = s > 0 ? Math.max(852, size.height / s) : 852, bottom = s > 0 ? Math.max(0, safe.bottom / s - 34) : 0;
  const m = useSharedValue<Motion>({ now: 0, epoch: 0, dragging: false, captured: base(0), finger: base(0), action: null, pattern: 0, yarn: 0, material: solidMaterial(0, 0), materialStarted: -1, materialRows: 3, saved: false, saveStarted: -1, lastDetent: 0, lastHaptic: -10 });
  useEffect(() => { const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', value => { setReduced(value); setAwake(false); }); return () => sub.remove(); }, []);
  useEffect(() => { const now = Date.now() / 1000; m.set(old => ({ ...old, now, epoch: now, dragging: false, action: null, materialStarted: -1, material: solidMaterial(old.pattern, old.yarn), saveStarted: -1 })); }, [m, reduced]);
  useEffect(() => { if (autoPlay) { const now = Date.now() / 1000; m.set(old => old.dragging || old.action ? old : { ...old, now, epoch: now }); } }, [autoPlay, m]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = m.get(); let next = { ...old, now };
    if (old.materialStarted >= 0 && now - old.materialStarted >= (reduced ? .18 : .38)) next = { ...next, material: solidMaterial(old.pattern, old.yarn), materialStarted: -1 };
    if (old.saveStarted >= 0 && !old.dragging && now - old.saveStarted >= .48) next = { ...next, saved: true, saveStarted: -1 };
    if (old.action && !old.dragging && now - old.action.stamp >= 2.65) {
      next = { ...next, epoch: old.action.stamp + 2.65, action: null, pattern: 0, yarn: 0, material: solidMaterial(0, 0), materialStarted: -1, saved: false, saveStarted: -1 }; scheduleOnRN(setAwake, false);
    } else if (reduced && next.materialStarted < 0) scheduleOnRN(setAwake, false);
    m.set(next);
  }, false);
  useEffect(() => { ticker.setActive(s > 0 && ((!reduced && previewTime === undefined && autoPlay) || awake)); return () => ticker.setActive(false); }, [autoPlay, awake, previewTime, reduced, s, ticker]);
  const pose = useDerivedValue(() => drive(m.get(), autoPlay, reduced, previewTime));
  useAnimatedReaction(() => ({ pattern: m.get().pattern, yarn: m.get().yarn, saved: m.get().saved }), (next, old) => { if (old && (next.pattern !== old.pattern || next.yarn !== old.yarn || next.saved !== old.saved)) scheduleOnRN(setControls, next); });
  const uniforms = useDerivedValue(() => {
    const p = pose.get(), state = m.get(), a = rotationMatrix(p.rotation), b = rotationMatrix(p.lagRotation);
    const age = state.materialStarted >= 0 ? Math.max(0, state.now - state.materialStarted) : -1, reset = resetAmount(state, reduced, previewTime), styles: number[] = [];
    for (let row = 0; row < 48; row++) {
      const wave = materialMix(row, age, state.materialRows, reduced), w: number[] = [];
      for (let channel = 0; channel < 6; channel++) { const target = channel === state.pattern || channel === state.yarn + 3 ? 1 : 0, value = state.material[row * 6 + channel] * (1 - wave) + target * wave; w.push(value * (1 - reset) + (channel === 0 || channel === 3 ? reset : 0)); }
      styles.push((222*w[3]+136*w[4]+125*w[5])/255,(220*w[3]+154*w[4]+140*w[5])/255,(211*w[3]+140*w[4]+146*w[5])/255,Math.round(clamp(w[0])*4095)+4096*Math.round(clamp(w[1])*4095));
    }
    return { phase: [p.p, canonicalTime(p.p), p.crownElapsed, p.topologySpeed], shape: [p.lift, p.hem, p.anticipation, p.lightPosition], light: [p.light, state.materialStarted >= 0 ? Math.max(0, state.now - state.materialStarted) : -1, resetAmount(state, reduced, previewTime), reduced ? 1 : 0], selection: [state.pattern, state.yarn, state.materialRows, reduced ? .18 : .38], matrix: [...a, ...a.map((v, i) => b[i] - v)], styles };
  });
  const background = useMemo(() => {
    const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, height)), p = Skia.Paint(); p.setAntiAlias(true);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 196.5, y: 0 }, { x: 196.5, y: height }, [Skia.Color('#929C9C'), Skia.Color('#4E595A'), Skia.Color('#20292B')], [0, .4, 1], TileMode.Clamp)); c.drawRect(Skia.XYWHRect(0, 0, 393, height), p);
    p.setShader(Skia.Shader.MakeRadialGradient({ x: 196.5, y: height * .43 }, 290, [color(0xD9E4DD, .1), color(0xD9E4DD, 0)], [20 / 290, 1], TileMode.Clamp, undefined, 1)); c.drawRect(Skia.XYWHRect(0, 0, 393, height), p);
    const gp = Skia.Paint(); gp.setAlphaf(.04); for (let y = 0; y < height; y += 128) for (let x = 0; x < 393; x += 128) c.drawImage(art.grain, x, y, gp);
    return recorder.finishRecordingAsPicture();
  }, [art, height]);
  const threads = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, height)), p = pose.get(); c.clipRect(Skia.XYWHRect(22, 184, 349, 411), ClipOp.Intersect, true);
    const hem = project(art.arcs, .5, 1, p), shade = Skia.Paint(); shade.setAlphaf(.16); c.drawImageRectOptions(art.shadow, Skia.XYWHRect(0, 0, 256, 96), Skia.XYWHRect(hem.x - 174, hem.y - 13, 348, 78), FilterMode.Linear, MipmapMode.None, shade);
    const canonical = canonicalTime(p.p);
    for (let i = 0; i < 52; i++) {
      const formed = columnRows(p.p, canonical, i); let last = Math.min(47, Math.max(2, Math.ceil(formed) - 1)), formation = last < 3 ? 1 : clamp((formed - last - 1 + .45) / .45); if (formation <= 0 && last > 2) { last--; formation = 1; }
      const crownY = 572 - (last + 1) * 6.8 + .5 - 1.8 * (1 - formation), end = project(art.arcs, (i + .5) / 52, (crownY - 245.6) / 326.4, p), sx = 38.4 + i * 6.2;
      const path = Skia.PathBuilder.Make().moveTo(sx, 184).quadTo(sx + (end.x - sx) * .35, 184 + (end.y - 184) * .56, end.x, end.y).detach(), paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(color(0xE6E9E0, .18 + .24 * hash(i + 91))); paint.setStyle(PaintStyle.Stroke); paint.setStrokeWidth(.42 + .28 * hash(i + 7)); paint.setStrokeCap(1); c.drawPath(path, paint);
    }
    return recorder.finishRecordingAsPicture();
  });
  const rowsProps = useAnimatedProps(() => { const text = `Row ${String(Math.min(48, Math.floor(3 + 45 * pose.get().p + .000001))).padStart(2, '0')} of 48`; return { text, defaultValue: text }; });
  const percentProps = useAnimatedProps(() => { const text = `${Math.floor((3 + 45 * pose.get().p) / 48 * 100 + .000001)}%`; return { text, defaultValue: text }; });
  const barStyle = useAnimatedStyle(() => ({ width: 345 * (3 + 45 * pose.get().p) / 48 * s }));
  const hitStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (Math.min(559, Math.max(220, project(art.arcs, .5, 1 - (3 + 45 * pose.get().p) / 48, pose.get()).y)) - 36) * s }] }));
  const hitProps = useAnimatedProps(() => ({ accessibilityValue: { text: `${Math.floor((3 + 45 * pose.get().p) / 48 * 100)} percent` } }));
  const startAction = useCallback((target: number, velocity = 0, kick = 0, saving = false) => {
    'worklet'; const old = m.get(), now = Date.now() / 1000, from = pose.get();
    m.set({ ...old, now, dragging: false, material: freezeMaterial({ ...old, now }, reduced, previewTime), materialStarted: -1, action: { stamp: now, from, target, velocity, kick, saving }, saved: saving ? old.saved : false, saveStarted: saving ? now : -1 }); scheduleOnRN(setAwake, true);
  }, [m, pose, previewTime, reduced]);
  const begin = useCallback(() => {
    'worklet'; if (reduced || previewTime !== undefined) return; const old = m.get(), now = Date.now() / 1000, from = pose.get();
    m.set({ ...old, now, dragging: true, captured: from, finger: from, action: null, material: freezeMaterial({ ...old, now }, reduced, previewTime), materialStarted: -1, saved: false, saveStarted: -1, lastDetent: Math.floor((3 + 45 * from.p) / 6) }); scheduleOnRN(setAwake, true);
  }, [m, pose, previewTime, reduced]);
  const update = useCallback((dx: number, dy: number) => {
    'worklet'; if (reduced || previewTime !== undefined || s <= 0) return; const old = m.get(); if (!old.dragging) return;
    const raw = old.captured.p - dy / (290 * s), p = clamp(raw), next = moved(old.captured, p), over = (raw - p) * 290, lean = clamp(dx / (18 * s), -6, 6); next.hem -= clamp(over * .45 / (1 + Math.abs(over) / 75), -35, 35); next.rotation[1] += lean; next.lagRotation[1] += lean;
    const now = Date.now() / 1000, detent = Math.floor((3 + 45 * p) / 6), tick = detent !== old.lastDetent && now - old.lastHaptic >= .125;
    m.set({ ...old, now, finger: next, lastDetent: tick ? detent : old.lastDetent, lastHaptic: tick ? now : old.lastHaptic }); if (tick) scheduleOnRN(tactile, p >= 1);
  }, [m, previewTime, reduced, s]);
  const end = useCallback((vy: number, cancelled: boolean) => {
    'worklet'; if (!m.get().dragging) return; const old = m.get(), velocity = cancelled ? 0 : -clamp(vy / s, -900, 900) / 290, projected = clamp(old.finger.p + velocity * .12), snapped = clamp((Math.round(3 + 45 * projected) - 3) / 45), now = Date.now() / 1000;
    m.set({ ...old, now, dragging: false, action: { stamp: now, from: old.finger, target: snapped, velocity, kick: cancelled ? 0 : clamp(velocity * 4.5, -14, 14), saving: false }, saved: false, saveStarted: -1 }); scheduleOnRN(setAwake, true);
  }, [m, s]);
  const pan = Gesture.Pan().minDistance(0).onBegin(begin).onUpdate(e => update(e.translationX, e.translationY)).onEnd(e => end(e.velocityY, false)).onFinalize((_e, success) => { if (!success) end(0, true); });
  const choose = useCallback((kind: number, index: number) => {
    'worklet'; const old = m.get(), now = Date.now() / 1000, current = pose.get(), pattern = kind === 0 ? index : old.pattern, yarn = kind === 1 ? index : old.yarn;
    m.set({ ...old, now, material: freezeMaterial({ ...old, now }, reduced, previewTime), materialStarted: now, materialRows: 3 + 45 * current.p, pattern, yarn, saved: false, saveStarted: -1, dragging: false, action: reduced ? null : { stamp: now, from: current, target: current.p, velocity: 0, kick: 0, saving: false } }); scheduleOnRN(setAwake, true); scheduleOnRN(tactile);
  }, [m, pose, previewTime, reduced]);
  const save = useCallback(() => { 'worklet'; if (reduced) { const now = Date.now() / 1000; m.set(old => ({ ...old, now, material: freezeMaterial({ ...old, now }, reduced, previewTime), materialStarted: -1, saved: true })); } else startAction(1, 0, 0, true); scheduleOnRN(tactile, true); }, [m, previewTime, reduced, startAction]);
  const adjust = useCallback((increment: boolean) => { 'worklet'; if (!reduced && previewTime === undefined) { startAction(clamp(pose.get().p + (increment ? 5 / 45 : -5 / 45))); scheduleOnRN(tactile); } }, [pose, previewTime, reduced, startAction]);
  const onLayout = useCallback((e: LayoutChangeEvent) => setSize(e.nativeEvent.layout), []);
  const box = (x: number, y: number, w: number, h: number) => ({ position: 'absolute' as const, left: x * s, top: y * s, width: w * s, height: h * s });
  const text = (fontSize: number, fontWeight: '400' | '500' | '600' = '400', tint = '#F2F1E7') => ({ color: tint, fontSize: fontSize * s, fontWeight, includeFontPadding: false });
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="light" />
    {s > 0 && <>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Group transform={[{ scale: s }]}><Picture picture={background} /><Picture picture={threads} /><Rect x={0} y={0} width={393} height={852}><Shader source={MATERIAL_EFFECT!} uniforms={uniforms}><ImageShader image={art.atlas} fit="none" tx="clamp" ty="clamp" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Shader></Rect></Group></Canvas>
      <Text allowFontScaling={false} style={[box(24, 76, 345, 17), text(11, '600'), { letterSpacing: 2 * s }]}>THREAD / STUDIO</Text>
      <View style={[box(24, 106, 345, 36), styles.centerY]}><Text allowFontScaling={false} style={[text(29, '600'), { letterSpacing: -.6 * s }]}>Make something soft</Text></View>
      <View style={[box(24, 150, 345, 19), styles.centerY]}><Text allowFontScaling={false} style={text(13, '400', '#C4CBC7')}>A small pattern, one row at a time.</Text></View>
      <AnimatedInput animatedProps={rowsProps} defaultValue="Row 03 of 48" editable={false} caretHidden contextMenuHidden accessible={false} style={[box(24, 610, 220, 20), styles.readout, text(12, '500')]} />
      <AnimatedInput animatedProps={percentProps} defaultValue="6%" editable={false} caretHidden contextMenuHidden accessible={false} style={[box(269, 610, 100, 20), styles.readout, text(12, '500'), { textAlign: 'right' }]} />
      <View style={[box(24, 640, 345, 3), { borderRadius: 1.5 * s, backgroundColor: 'rgba(196,203,199,0.2)' }]}><Animated.View style={[{ height: 3 * s, borderRadius: 1.5 * s, backgroundColor: '#EAE8DC' }, barStyle]} /></View>
      <GestureDetector gesture={pan}><Animated.View style={[box(22, 0, 349, 72), hitStyle]} animatedProps={hitProps} accessible accessibilityRole="adjustable" accessibilityLabel={reduced ? 'Pattern preview' : 'Knit progress'} accessibilityHint={reduced ? 'A completed knitted pattern.' : 'Drag upward to knit, downward to unknit.'} accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={e => scheduleOnUI(adjust, e.nativeEvent.actionName === 'increment')} /></GestureDetector>
      {['Sprig', 'Ripple', 'Plain'].map((title, i) => <Pressable key={title} accessibilityRole="button" accessibilityState={{ selected: controls.pattern === i }} onPress={() => scheduleOnUI(choose, 0, i)} style={[box(24 + i * 112, 662.5, 104, 44), styles.center]}><View style={[styles.center, { width: 104 * s, height: 35 * s, borderRadius: 17.5 * s, backgroundColor: controls.pattern === i ? '#EAE8DC' : 'rgba(38,50,53,0.7)' }]}><Text allowFontScaling={false} style={text(12.5, '500', controls.pattern === i ? '#263235' : '#F2F1E7')}>{title}</Text></View></Pressable>)}
      <View style={[box(24, 722 - bottom, 100, 22), styles.centerY]}><Text allowFontScaling={false} style={[text(10, '600'), { letterSpacing: 1.5 * s }]}>YARN</Text></View>
      {YARNS.map((tint, i) => <Pressable key={tint} accessibilityRole="button" accessibilityLabel={`${['Cream', 'Moss', 'Slate'][i]} yarn`} accessibilityState={{ selected: controls.yarn === i }} onPress={() => scheduleOnUI(choose, 1, i)} style={[box(236 + i * 44, 711 - bottom, 44, 44), styles.center]}><View style={[styles.center, { width: 30 * s, height: 30 * s, borderRadius: 15 * s, borderWidth: (controls.yarn === i ? 1.5 : .8) * s, borderColor: controls.yarn === i ? 'rgba(242,241,231,.85)' : 'rgba(242,241,231,.12)' }]}><View style={{ width: 22 * s, height: 22 * s, borderRadius: 11 * s, backgroundColor: tint }} /></View></Pressable>)}
      <Pressable accessibilityRole="button" accessibilityLabel={controls.saved ? 'Pattern saved' : 'Save pattern'} onPress={() => scheduleOnUI(save)} style={[box(24, 755 - bottom, 345, 54), styles.center, { borderRadius: 27 * s, backgroundColor: '#EAE8DC' }]}><Text allowFontScaling={false} style={text(15.5, '600', '#263234')}>{controls.saved ? '✓  Pattern saved' : 'Save pattern'}</Text></Pressable>
    </>}
  </View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#20292B', overflow: 'hidden' }, center: { alignItems: 'center', justifyContent: 'center' }, centerY: { justifyContent: 'center' }, readout: { padding: 0, margin: 0, fontVariant: ['tabular-nums'] } });
