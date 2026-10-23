/**
 * The upgrade on the accepted replica: the Blender layers (body, shackle, key) become a vault you unlock by hand —
 * carry the key to the keyhole, it turns into the lock, turn it through three detents, the shackle pops and a
 * Liquid Glass vault sheet rises. Glass surfaces use a static blur intensity; only opacity and transforms move.
 */
import { Canvas, Circle, Group, Image, LinearGradient, RadialGradient, Rect, useImage, vec, type Transforms3d } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming, Easing, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { M, clamp, initialGSim, input, makeGeo, stepG, type Input } from './glass-sim';
import { G_HAPTIC, G_QA_SPEED, GM } from './motion';
import { COPY, REPLICA_BG, REPLICA_FIT as F, SPACE, UPG } from './theme';

function playHaptic(kind: number) {
  if (kind === G_HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === G_HAPTIC.seat) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else if (kind === G_HAPTIC.detent) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
  else if (kind === G_HAPTIC.open) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  else if (kind === G_HAPTIC.wrong) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
}

function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

type Fit = readonly [number, number, number, number, number] | readonly [number, number, number, number, number, number];

/** A layer's fitted pose to the source (as in the replica): screen transforms, k = pt per source px. */
function fitT(q: Fit, c: readonly [number, number], k: number): Transforms3d {
  'worklet';
  return [
    { translateX: (c[0] + q[3]) * k },
    { translateY: (c[1] + q[4]) * k },
    { rotate: (-q[2] * Math.PI) / 180 },
    { skewX: -Math.atan((q.length > 5 ? (q as readonly number[])[5] : 0) / q[1]) },
    { scaleX: q[0] },
    { scaleY: q[1] },
    { translateX: -c[0] * k },
    { translateY: -c[1] * k },
  ];
}
function mixFit(a: Fit, b: Fit, u: number): Fit {
  'worklet';
  const a5 = a.length > 5 ? (a as readonly number[])[5] : 0;
  const b5 = b.length > 5 ? (b as readonly number[])[5] : 0;
  return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u, a[4] + (b[4] - a[4]) * u, a5 + (b5 - a5) * u];
}

export function GlassUpgrade({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const s = width / 402;
  const g = useMemo(() => makeGeo(width), [width]);
  const body = useImage(require('./assets/padlock-body.png'));
  const closed = useImage(require('./assets/shackle-closed.png'));
  const openImg = useImage(require('./assets/shackle-open.png'));
  const keyIn = useImage(require('./assets/key-in.png'));
  const keyInA = useImage(require('./assets/key-in-a.png'));
  const keyFront = useImage(require('./assets/key-front.png'));
  const shadow = useImage(require('./assets/shadow.png'));

  const sim = useSharedValue(initialGSim(g, reduced, autoPlay));
  const kx = useSharedValue(g.restX);
  const ky = useSharedValue(g.restY);
  const mode = useSharedValue<number>(M.idle);
  const modeT = useSharedValue(0);
  const th = useSharedValue(0);
  const shackle = useSharedValue(0);
  const sheet = useSharedValue(0);
  const glint = useSharedValue(-1);
  const shake = useSharedValue(-1);
  const lift = useSharedValue(0);
  const show = useSharedValue(0);
  const tick = useSharedValue(-1);
  const clock = useSharedValue(0);
  const modeNow = useSharedValue<number>(M.idle);
  const [ui, setUi] = useState<number>(M.idle);

  useFrameCallback((f) => {
    'worklet';
    if (f.timeSincePreviousFrame === null) return;
    const c = sim.get();
    c.reduced = reduced;
    stepG(c, g, (f.timeSincePreviousFrame / 1000) * G_QA_SPEED, autoPlay);
    sim.set(c);
    kx.set(c.kx);
    ky.set(c.ky);
    mode.set(c.mode);
    modeT.set(c.modeT);
    th.set(c.th);
    shackle.set(c.shackle);
    sheet.set(c.sheet);
    glint.set(c.glint);
    shake.set(c.shake);
    lift.set(c.lift);
    show.set(c.show);
    tick.set(c.tick);
    clock.set(c.clock);
    if (c.mode !== modeNow.get()) {
      modeNow.set(c.mode);
      scheduleOnRN(setUi, c.mode);
    }
    for (let i = 0; i < c.events.length; i++) scheduleOnRN(playHaptic, c.events[i]);
    c.events = [];
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const c = sim.get();
    input(c, g, ev);
    sim.set(c);
  }, [sim, g]);
  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => act({ type: 'down', x: e.x, y: e.y }))
    .onUpdate((e) => act({ type: 'move', x: e.x, y: e.y }))
    .onFinalize((e) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY })), [act]);
  const command = useCallback((a: 'unlock' | 'lock') => scheduleOnUI(act, { type: 'command', action: a }), [act]);

  // ---- Skia ------------------------------------------------------------------------------------------------
  const full = { x: 0, y: 0, width, height, fit: 'fill' as const };
  const C = UPG.lockCentre;
  // The whole lock rises and shrinks to make room for the vault sheet; a wrong turn shakes it.
  const rig = useDerivedValue<Transforms3d>(() => {
    const k = sheet.value;
    const sh = shake.value < 0 ? 0 : GM.shake.amp * (1 - shake.value / GM.shake.duration) * Math.sin(2 * Math.PI * GM.shake.cycles * (shake.value / GM.shake.duration));
    // The shackle springing free kicks the body down; it settles like a mass on a spring (no keyframes: it decays).
    const g0 = glint.value;
    const recoil = g0 < 0 || reduced ? 0 : GM.recoil.amp * Math.exp(-GM.recoil.decay * g0) * Math.sin(2 * Math.PI * GM.recoil.hz * g0);
    return [
      { translateX: C.x * s + sh * s }, { translateY: C.y * s - GM.rise * s * k + recoil * s }, { scale: 1 - (1 - GM.shrink) * k },
      { translateX: -C.x * s }, { translateY: -C.y * s },
    ];
  });
  // The replica's registration of every layer to the source (so the upgrade at rest is the source's frame).
  const k = width / 1206;
  const lockFit = fitT(F.lock, F.lockCentre, k);
  const frontFit = fitT(F.front, F.frontCentre, k);
  // Shackle: the open/closed layers cross-fade while it travels between their positions, with the spring's overshoot.
  const openO = useDerivedValue(() => clamp(shackle.value));
  const closedO = useDerivedValue(() => 1 - clamp(shackle.value));
  const closedT = useDerivedValue<Transforms3d>(() => [
    { translateX: (F.shackleClosed[0] + 23 * shackle.value) * k }, { translateY: (F.shackleClosed[1] - 90 * shackle.value) * k },
  ]);
  const openT = useDerivedValue<Transforms3d>(() => [
    { translateX: (F.shackleOpen[0] - 23 * (1 - shackle.value)) * k }, { translateY: (F.shackleOpen[1] + 90 * (1 - shackle.value)) * k },
  ]);
  // Key: loose (key-front, where the source shows it; it turns about its long axis at rest, as filmed, and follows the
  // finger) → turning into the lock → seated (key-in-a), then turned (key-in) as it turns, on the source's two poses.
  const insertU = useDerivedValue(() => {
    if (mode.value === M.inserting) return reduced ? 1 : easeInOut(clamp(modeT.value / GM.insert));
    return mode.value === M.seated || mode.value === M.open || mode.value === M.closing ? 1 : 0;
  });
  const looseT = useDerivedValue<Transforms3d>(() => {
    const u = insertU.value;
    const resting = mode.value === M.idle && lift.value < 0.01 && !reduced;
    const spinU = resting ? clamp((clock.value % GM.spin.every) / GM.spin.turn) : 0;
    const spin = Math.max(0.06, Math.abs(Math.cos(Math.PI * spinU)));
    const x = kx.value + (UPG.plug.x * s - kx.value) * u;
    const y = ky.value + (UPG.plug.y * s - ky.value) * u;
    return [
      { translateX: x }, { translateY: y }, { rotate: UPG.insertAngle * u }, { scale: 1 + 0.05 * lift.value }, { scaleX: spin },
      { translateX: -UPG.keyFront.x * s }, { translateY: -UPG.keyFront.y * s },
    ];
  });
  const looseO = useDerivedValue(() => 1 - clamp((insertU.value - 0.6) / 0.4));
  const seatedO = useDerivedValue(() => clamp((insertU.value - 0.6) / 0.4));
  const turnU = useDerivedValue(() => clamp(th.value));
  const keyAO = useDerivedValue(() => seatedO.value * (1 - turnU.value));
  const keyBO = useDerivedValue(() => seatedO.value * turnU.value);
  const keySeatT = useDerivedValue<Transforms3d>(() => fitT(mixFit(F.keyClosed, F.keyOpen, clamp(th.value, -0.15, 1)), F.keyCentre, k));
  const lockO = useDerivedValue(() => Math.max(show.value, seatedO.value));
  // The lock materialises around the keyhole from 0.96, not from nothing (the source's cross-fade, with depth).
  const appearT = useDerivedValue<Transforms3d>(() => {
    const k2 = reduced ? 1 : GM.appear + (1 - GM.appear) * lockO.value;
    return [{ translateX: UPG.plug.x * s }, { translateY: UPG.plug.y * s }, { scale: k2 }, { translateX: -UPG.plug.x * s }, { translateY: -UPG.plug.y * s }];
  });
  // A detent's flash of light where the key turns.
  const tickO = useDerivedValue(() => (tick.value < 0 ? 0 : GM.tick.alpha * (1 - tick.value / GM.tick.duration) ** 2));
  // Glint across the glass when it opens.
  const glintX = useDerivedValue(() => (glint.value < 0 ? -999 : (C.x - 120 + 240 * (glint.value / GM.glint)) * s));
  const glintO = useDerivedValue(() => (glint.value < 0 ? 0 : Math.sin(Math.PI * (glint.value / GM.glint)) * 0.8));
  const glintT = useDerivedValue<Transforms3d>(() => [{ translateX: glintX.value }, { translateY: (C.y - 140) * s }, { skewX: -0.35 }]);
  const glowO = useDerivedValue(() => lockO.value * (0.6 + 0.4 * sheet.value));

  // ---- RN overlay ------------------------------------------------------------------------------------------
  const sheetH = height * 0.5;
  const sheetStyle = useAnimatedStyle(() => (reduced
    ? { opacity: sheet.value }
    : { opacity: 1, transform: [{ translateY: (1 - sheet.value) * (sheetH + 40) }] }));
  const headStyle = useAnimatedStyle(() => ({ opacity: 1 - sheet.value * 0.4 }));
  const lockedO = useAnimatedStyle(() => ({ opacity: 1 - clamp((shackle.value - 0.15) / 0.7), transform: [{ translateY: -8 * clamp(shackle.value) }] }));
  const openedO = useAnimatedStyle(() => ({ opacity: clamp((shackle.value - 0.15) / 0.7), transform: [{ translateY: 8 * (1 - clamp(shackle.value)) }] }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(sheet.value * 3) }));
  const isOpen = ui === M.open;

  return (
    <View style={[StyleSheet.absoluteFill, { backgroundColor: REPLICA_BG }]}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={width} height={height} color={REPLICA_BG} />
            {/* Liquid Glass hue field: the lock's blue glows into the room (a gradient, never a live blur). */}
            <Group opacity={glowO}>
              <Circle cx={C.x * s} cy={C.y * s} r={UPG.glow.r * s}>
                <RadialGradient c={vec(C.x * s, C.y * s)} r={UPG.glow.r * s} colors={[UPG.glow.color, 'rgba(80,96,255,0)']} />
              </Circle>
            </Group>
            <Group transform={rig}>
              <Group opacity={lockO} transform={appearT}>
                <Group transform={lockFit}>
                  <Image image={shadow} {...full} />
                  <Group transform={closedT} opacity={closedO}>
                    <Image image={closed} {...full} />
                  </Group>
                  <Group transform={openT} opacity={openO}>
                    <Image image={openImg} {...full} />
                  </Group>
                  {/* The glint sweeps across the glass only (kept inside the body's own alpha), soft at both edges. */}
                  <Group layer>
                    <Image image={body} {...full} />
                    <Group opacity={glintO} transform={glintT}>
                      <Rect x={0} y={0} width={36 * s} height={280 * s} blendMode="srcATop">
                        <LinearGradient start={vec(0, 0)} end={vec(36 * s, 0)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.75)', 'rgba(255,255,255,0)']} />
                      </Rect>
                    </Group>
                  </Group>
                </Group>
              </Group>
              <Circle cx={UPG.plug.x * s} cy={UPG.plug.y * s} r={40 * s} opacity={tickO}>
                <RadialGradient c={vec(UPG.plug.x * s, UPG.plug.y * s)} r={40 * s} colors={['#FFFFFF', 'rgba(255,255,255,0)']} />
              </Circle>
              <Group transform={keySeatT}>
                <Group opacity={keyAO}>
                  <Image image={keyInA} {...full} />
                </Group>
                <Group opacity={keyBO}>
                  <Image image={keyIn} {...full} />
                </Group>
              </Group>
            </Group>
            <Group transform={looseT} opacity={looseO}>
              <Group transform={frontFit}>
                <Image image={keyFront} {...full} />
              </Group>
            </Group>
          </Canvas>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={isOpen ? COPY.lock : COPY.unlock}
            accessibilityHint={COPY.keyHint}
            onPress={() => command(isOpen ? 'lock' : 'unlock')}
            style={[styles.keyTarget, { left: g.restX - 44, top: g.restY - 70 * s, width: 88, height: 140 * s }]}
          />
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.head, { top: insets.top + SPACE.xl }, headStyle]}>
        <Text style={styles.eyebrow}>{COPY.eyebrow}</Text>
        <View style={styles.titleBox}>
          <Animated.Text style={[styles.title, lockedO]}>{COPY.locked}</Animated.Text>
          <Animated.Text style={[styles.title, styles.overlay, openedO]}>{COPY.unlocked}</Animated.Text>
        </View>
        <Text style={styles.meta}>{COPY.meta}</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + SPACE.xxl }, hintStyle]}>
        <Text style={styles.hintText}>{COPY.hint}</Text>
      </Animated.View>

      {/* Liquid Glass vault sheet: static blur, light tint, bright top edge; grouped rows with inset hairlines. */}
      <Animated.View pointerEvents={isOpen ? 'auto' : 'none'} style={[styles.sheet, { height: sheetH, paddingBottom: insets.bottom + SPACE.lg }, sheetStyle]}>
        <BlurView intensity={60} tint="light" style={StyleSheet.absoluteFill} />
        <View style={styles.sheetTint} />
        <View style={styles.sheetEdge} />
        <View style={styles.sheetHead}>
          <Text style={styles.sheetTitle}>{COPY.head}</Text>
          <Text style={styles.sheetMeta}>{COPY.sheetMeta}</Text>
        </View>
        <View style={styles.group}>
          {COPY.rows.map((r, i) => (
            <SheetRow key={r.title} index={i} sheet={sheet} reduced={reduced}>
              <View style={styles.rowIcon}>
                <SymbolView name={r.symbol as never} size={17} tintColor="#3D46E0" />
              </View>
              <View style={styles.rowText}>
                <Text style={styles.rowTitle}>{r.title}</Text>
                <Text style={styles.rowSub} numberOfLines={1}>{r.sub}</Text>
              </View>
              <SymbolView name={'chevron.right' as never} size={12} tintColor="rgba(22,24,46,0.3)" />
              {i < COPY.rows.length - 1 ? <View style={styles.hairline} /> : null}
            </SheetRow>
          ))}
        </View>
        <PressScale accessibilityLabel={COPY.lock} onPress={() => command('lock')} style={styles.lockButton} reduced={reduced}>
          <SymbolView name={'lock.fill' as never} size={15} tintColor="#FFFFFF" />
          <Text style={styles.lockText}>{COPY.lock}</Text>
        </PressScale>
      </Animated.View>
    </View>
  );
}

/** Press feedback: 0.97 in 120 ms on press, back in 160 ms (ease-out both ways; the release is the slower, deliberate half). */
function PressScale({ accessibilityLabel, onPress, style, reduced, children }: {
  accessibilityLabel: string; onPress: () => void; style: object; reduced: boolean; children: React.ReactNode;
}) {
  const k = useSharedValue(1);
  const a = useAnimatedStyle(() => ({ transform: [{ scale: k.value }] }));
  const out = Easing.bezier(0.23, 1, 0.32, 1);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={onPress}
      onPressIn={() => { if (!reduced) k.set(withTiming(0.97, { duration: 120, easing: out })); }}
      onPressOut={() => k.set(withTiming(1, { duration: 160, easing: out }))}
    >
      <Animated.View style={[style, a]}>{children}</Animated.View>
    </Pressable>
  );
}

/** A vault row: it rises 8 pt into place behind the sheet, each a beat after the one above (opacity only if reduced). */
function SheetRow({ index, sheet, reduced, children }: { index: number; sheet: SharedValue<number>; reduced: boolean; children: React.ReactNode }) {
  const style = useAnimatedStyle(() => {
    const u = clamp((sheet.value - 0.1 - index * GM.rows.stagger) / GM.rows.span);
    const e = 1 - (1 - u) * (1 - u) * (1 - u); // ease-out
    return reduced ? { opacity: e } : { opacity: e, transform: [{ translateY: GM.rows.rise * (1 - e) }] };
  });
  return <Animated.View style={[styles.row, style]}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  keyTarget: { position: 'absolute' },
  head: { position: 'absolute', left: SPACE.xl, right: SPACE.xl },
  eyebrow: { color: 'rgba(22,24,46,0.5)', fontSize: 12, letterSpacing: 1.8, fontWeight: '700' },
  titleBox: { height: 44, marginTop: SPACE.sm, overflow: 'hidden' },
  title: { color: '#16182E', fontSize: 36, lineHeight: 44, fontWeight: '700', letterSpacing: -0.8 },
  overlay: { position: 'absolute', left: 0, top: 0 },
  meta: { marginTop: SPACE.xs, color: 'rgba(22,24,46,0.5)', fontSize: 13 },
  hint: { position: 'absolute', left: SPACE.xl, right: SPACE.xl, alignItems: 'center' },
  hintText: { color: 'rgba(22,24,46,0.55)', fontSize: 14 },
  sheet: {
    position: 'absolute', left: SPACE.sm, right: SPACE.sm, bottom: SPACE.sm, borderRadius: SPACE.xxl, overflow: 'hidden', paddingHorizontal: SPACE.lg, paddingTop: SPACE.xl,
  },
  sheetTint: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(255,255,255,0.45)' },
  sheetEdge: { position: 'absolute', left: 0, right: 0, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.95)' },
  sheetHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', paddingHorizontal: SPACE.xs, marginBottom: SPACE.md },
  sheetTitle: { color: '#16182E', fontSize: 20, fontWeight: '700', letterSpacing: -0.4 },
  sheetMeta: { color: 'rgba(22,24,46,0.5)', fontSize: 12, fontVariant: ['tabular-nums'] },
  group: { backgroundColor: 'rgba(255,255,255,0.7)', borderRadius: SPACE.xxl - SPACE.lg, overflow: 'hidden' }, // nested in the sheet's 32
  row: { height: 60, flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACE.lg },
  rowIcon: { width: 32, height: 32, borderRadius: SPACE.sm, backgroundColor: 'rgba(61,70,224,0.1)', alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, marginLeft: SPACE.md },
  rowTitle: { color: '#16182E', fontSize: 15, fontWeight: '600', letterSpacing: -0.2 },
  rowSub: { color: 'rgba(22,24,46,0.5)', fontSize: 12, marginTop: SPACE.xs / 2 },
  hairline: { position: 'absolute', left: SPACE.lg + 32 + SPACE.md, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(22,24,46,0.12)' },
  lockButton: {
    marginTop: SPACE.lg, height: 48, borderRadius: 24, backgroundColor: '#16182E', flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
  },
  lockText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600', marginLeft: SPACE.sm },
});
