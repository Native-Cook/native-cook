import { Canvas, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { LockScene, type LockValues } from './LockScene';
import { makeLockPaths } from './lock-paths';
import { MODE, clamp, initialSim, input, lockLift, makeGeometry, shakeOffset, stepSim, type Input } from './lock-sim';
import { CARRY, GLINT, HAPTIC, SHEET_MOTION, TURN } from './motion';
import { COLORS, COPY, KEY, LOCK, SHEET, TYPE } from './theme';

type Props = { autoPlay?: boolean };

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.seat) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else if (kind === HAPTIC.detent) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
  else if (kind === HAPTIC.open) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  else if (kind === HAPTIC.wrong) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
}

export function GlassLockKey({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const paths = useMemo(() => makeLockPaths(), []);
  const [open, setOpen] = useState(false);
  const [wrong, setWrong] = useState(false);
  const [debug, setDebug] = useState('');

  const sim = useSharedValue(initialSim(g, reduced, autoPlay));
  const kx = useSharedValue(g.restX);
  const ky = useSharedValue(g.restY);
  const tilt = useSharedValue(0);
  const lift = useSharedValue(0);
  const inserted = useSharedValue(0);
  const turnCos = useSharedValue(1);
  const turnSin = useSharedValue(0);
  const shackle = useSharedValue(0);
  const bounce = useSharedValue(0);
  const shake = useSharedValue(0);
  const sheet = useSharedValue(0);
  const holeGlow = useSharedValue(0);
  const glint = useSharedValue(0);
  const bob = useSharedValue(0);
  const openNow = useSharedValue(false);
  const wrongNow = useSharedValue(false);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    kx.set(s.kx);
    ky.set(s.ky);
    tilt.set(clamp(s.kvx * 0.0003, -0.12, 0.12) * (s.mode === MODE.carrying ? 1 : 0));
    lift.set(s.lift);
    inserted.set(s.mode === MODE.inserting || s.mode === MODE.seated || s.mode === MODE.open || s.mode === MODE.closing ? 1 : 0);
    turnCos.set(Math.cos(s.th * TURN.visual));
    turnSin.set(Math.sin(s.th * TURN.visual));
    shackle.set(s.shackle);
    bounce.set(s.bounce);
    shake.set(shakeOffset(s));
    sheet.set(s.sheet);
    glint.set(s.glintT < 0 ? 0 : s.glintT / GLINT.duration);
    const near = s.mode === MODE.carrying ? clamp(1 - Math.hypot(s.kx - g.plugX, s.ky - g.plugY) / (CARRY.magnet * 2 * g.s)) : 0;
    holeGlow.set(s.mode === MODE.seated ? 0.45 + 0.35 * clamp(s.th / 1.57) : near * 0.8);
    // Idle float of the key at rest, so it reads as something to pick up.
    bob.set(s.mode === MODE.idle && !s.reduced ? Math.sin((2 * Math.PI * s.clock) / CARRY.bobPeriod) * CARRY.bob * g.s : 0);
    const isOpen = s.mode === MODE.open;
    if (isOpen !== openNow.get()) {
      openNow.set(isOpen);
      scheduleOnRN(setOpen, isOpen);
    }
    const isWrong = s.wrongHint === 1;
    if (isWrong !== wrongNow.get()) {
      wrongNow.set(isWrong);
      scheduleOnRN(setWrong, isWrong);
    }
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(playHaptic, s.events[i]);
    s.events = [];
    if (Math.floor(s.clock * 4) !== Math.floor((s.clock - 0.02) * 4)) scheduleOnRN(setDebug, `m=${s.mode} demo=${s.demoOn} q=${s.quiet.toFixed(1)} g=${s.grab} au=${s.autoUnlock.toFixed(1)} t=${s.demoT.toFixed(1)}`);
  }, [sim, kx, ky, tilt, lift, inserted, turnCos, turnSin, shackle, bounce, shake, sheet, glint, holeGlow, bob, openNow, wrongNow, g]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    stepSim(s, g, frame.timeSincePreviousFrame / 1000, autoPlay);
    sim.set(s);
    publish();
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => act({ type: 'down', x: e.x, y: e.y }))
    .onUpdate((e) => act({ type: 'move', x: e.x, y: e.y, vx: e.velocityX, vy: e.velocityY }))
    .onFinalize((e) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY })), [act]);

  const command = useCallback((action: 'unlock' | 'lock') => scheduleOnUI(act, { type: 'command', action }), [act]);

  // Skia transforms. The rig lifts and shrinks the lock (and the key in it) while the sheet is up.
  const cx = g.w / 2;
  const cy = g.lockTop + (LOCK.height * g.s) / 2;
  const rig = useDerivedValue<Transforms3d>(() => {
    const l = lockLift(sheet.value, g);
    return [{ translateX: cx }, { translateY: cy + l.dy }, { scale: l.scale }, { translateX: -cx }, { translateY: -cy }];
  });
  const lock = useDerivedValue<Transforms3d>(() => [
    { translateX: g.faceX + shake.value * g.s }, { translateY: g.lockTop + bounce.value * g.s }, { scale: g.s },
  ]);
  const shackleT = useDerivedValue<Transforms3d>(() => {
    const hingeX = LOCK.face / 2 + LOCK.opening / 2 + LOCK.shackle / 2;
    return [
      { translateY: -LOCK.openLift * shackle.value },
      { translateX: hingeX }, { translateY: LOCK.longLeg }, { rotate: LOCK.openTilt * shackle.value },
      { translateX: -hingeX }, { translateY: -LOCK.longLeg },
    ];
  });
  const key = useDerivedValue<Transforms3d>(() => [
    { translateX: kx.value + shake.value * g.s * inserted.value }, { translateY: ky.value + bob.value }, { rotate: tilt.value },
    { scale: g.s * (1 + (CARRY.lift - 1) * lift.value) },
  ]);
  const keyShadow = useDerivedValue<Transforms3d>(() => [
    { translateX: kx.value }, { translateY: ky.value + (KEY.blade + KEY.ring + 18) * g.s }, { scale: g.s * (1 - 0.2 * lift.value) },
  ]);
  const keyFront = useDerivedValue(() => 1 - inserted.value);
  const values: LockValues = { rig, lock, shackle: shackleT, key, turnCos, turnSin, keyFront, keyBehind: inserted, holeGlow, glint, keyShadow };

  // RN layers: title, hint, vault sheet.
  const sheetH = height * SHEET.heightRatio;
  const sheetStyle = useAnimatedStyle(() => (reduced
    ? { opacity: sheet.value, transform: [{ translateY: 0 }] }
    : { opacity: 1, transform: [{ translateY: (1 - sheet.value) * (sheetH + 40) }] }));
  // The title follows the shackle but ignores its overshoot, so neither word lingers at rest.
  const lockedTitle = useAnimatedStyle(() => ({ opacity: 1 - clamp((shackle.value - 0.15) / 0.7) }));
  const openTitle = useAnimatedStyle(() => ({ opacity: clamp((shackle.value - 0.15) / 0.7) }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(sheet.value * 3) }));

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      {debug ? <Text style={{ position: 'absolute', top: 50, left: 10, color: 'red', fontSize: 10, zIndex: 9 }}>{debug}</Text> : null}
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <LockScene g={g} paths={paths} v={values} />
          </Canvas>
          <View
            accessible
            accessibilityRole="button"
            accessibilityLabel={open ? COPY.lock : COPY.unlock}
            accessibilityHint={COPY.keyHint}
            onAccessibilityTap={() => command(open ? 'lock' : 'unlock')}
            style={[styles.keyTarget, { left: g.restX - 44, top: g.restY, width: 88, height: (KEY.blade + KEY.ring) * g.s }]}
          />
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.head, { top: insets.top + TYPE.top, left: TYPE.side, right: TYPE.side }]}>
        <Text style={styles.eyebrow}>{COPY.eyebrow}</Text>
        <View style={styles.titleBox} accessible accessibilityRole="header" accessibilityLabel={open ? COPY.unlocked : COPY.locked}>
          <Animated.Text style={[styles.title, lockedTitle]}>{COPY.locked}</Animated.Text>
          <Animated.Text style={[styles.title, styles.overlay, openTitle]}>{COPY.unlocked}</Animated.Text>
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.hintBox, { bottom: insets.bottom + TYPE.bottom }, hintStyle]}>
        <Text style={[styles.hint, wrong && styles.hintWrong]}>{wrong ? COPY.wrongWay : COPY.hint}</Text>
      </Animated.View>

      <Animated.View
        pointerEvents={open ? 'auto' : 'none'}
        accessibilityElementsHidden={!open}
        importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}
        style={[styles.sheet, { height: sheetH, paddingBottom: insets.bottom + SHEET.pad }, sheetStyle]}
      >
        <Text style={styles.sheetHead} accessibilityRole="header">{COPY.head}</Text>
        <Text style={styles.sheetSub}>{`${COPY.rows.length} items`}</Text>
        <View style={styles.rows}>
          {COPY.rows.map((row, i) => (
            <SheetRow key={row.title} index={i} sheet={sheet} title={row.title} sub={row.sub} icon={row.icon} last={i === COPY.rows.length - 1} reduced={reduced} />
          ))}
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={COPY.lock} onPress={() => command('lock')} style={styles.button}>
          <Text style={styles.buttonText}>{COPY.lock}</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

type RowProps = {
  index: number; sheet: SharedValue<number>; title: string; sub: string; icon: string; last: boolean; reduced: boolean;
};

/** One vault row; rows arrive a little after one another as the sheet rises. */
function SheetRow({ index, sheet, title, sub, icon, last, reduced }: RowProps) {
  const style = useAnimatedStyle(() => {
    const u = clamp((sheet.value - 0.35 - index * SHEET_MOTION.stagger * 2) / 0.45);
    return reduced ? { opacity: sheet.value } : { opacity: u, transform: [{ translateY: (1 - u) * 16 }] };
  });
  return (
    <Animated.View style={[styles.row, !last && styles.rowLine, style]}>
      <View style={styles.rowIcon}>
        <Text style={styles.rowIconText}>{icon}</Text>
      </View>
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>{title}</Text>
        <Text style={styles.rowSub}>{sub}</Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.bgTop },
  keyTarget: { position: 'absolute' },
  head: { position: 'absolute' },
  eyebrow: { color: COLORS.mute, fontSize: TYPE.eyebrowSize, letterSpacing: TYPE.eyebrowTracking, fontWeight: '600' },
  titleBox: { marginTop: TYPE.gap, height: TYPE.titleLine },
  title: { color: COLORS.ink, fontFamily: TYPE.titleFamily, fontSize: TYPE.titleSize, lineHeight: TYPE.titleLine },
  overlay: { position: 'absolute', left: 0, top: 0 },
  hintBox: { position: 'absolute', left: TYPE.side, right: TYPE.side, alignItems: 'center' },
  hint: { color: COLORS.mute, fontSize: TYPE.hintSize, lineHeight: TYPE.hintLine },
  hintWrong: { color: COLORS.error },
  sheet: {
    position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLORS.sheet,
    borderTopLeftRadius: SHEET.radius, borderTopRightRadius: SHEET.radius, paddingHorizontal: SHEET.pad, paddingTop: SHEET.pad,
  },
  sheetHead: { color: COLORS.ink, fontSize: SHEET.headSize, fontWeight: '600' },
  sheetSub: { color: COLORS.mute, fontSize: SHEET.subSize, marginTop: 4 },
  rows: { marginTop: 16, flex: 1 },
  row: { height: SHEET.row, flexDirection: 'row', alignItems: 'center' },
  rowLine: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: COLORS.sheetLine },
  rowIcon: {
    width: SHEET.icon, height: SHEET.icon, borderRadius: SHEET.iconRadius, backgroundColor: COLORS.rowIcon,
    alignItems: 'center', justifyContent: 'center',
  },
  rowIconText: { color: COLORS.rowIconInk, fontSize: 16, fontWeight: '700' },
  rowText: { marginLeft: 12, flex: 1 },
  rowTitle: { color: COLORS.ink, fontSize: SHEET.titleSize, fontWeight: '500' },
  rowSub: { color: COLORS.mute, fontSize: SHEET.subSize, marginTop: 2 },
  button: {
    height: SHEET.button, borderRadius: SHEET.button / 2, backgroundColor: COLORS.button, alignItems: 'center', justifyContent: 'center',
  },
  buttonText: { color: COLORS.buttonInk, fontSize: 17, fontWeight: '600' },
});
