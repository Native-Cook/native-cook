import { BlurMask, Canvas, Circle, Group, RadialGradient, Rect, Skia, useImage, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  SensorType, interpolateColor, useAnimatedSensor, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { RoomScene, type RoomValues } from './CordScene';
import {
  bulbAngle, glassArm, glassCenter, igniteValue, initialSim, input, makeGeometry, revealReach, stepSim, type Input,
} from './cord-sim';
import { FEEL, HAPTIC, HERO, QA_SPEED, REVEAL, SWITCH } from './motion';
import { CORD, COPY, DARK, FIT, LIGHT, REPLICA_ART, TYPE } from './theme';

type Props = { autoPlay?: boolean };

// A hard but anti-aliased edge for the reveal circle.
const REVEAL_EDGE = ['#FFFFFF', '#FFFFFF', 'rgba(255,255,255,0)'];
const REVEAL_STOPS = [0, 0.994, 1];
const EDGE = 'rgba(255, 200, 130, 0.9)';

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.detent) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.tick) Haptics.selectionAsync();
  else if (kind === HAPTIC.home) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

export function PullCordTheme({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const wall = useImage(require('./assets/wall.jpg'));
  const wallLight = useImage(require('./assets/wall-light.jpg'));
  const lit = useImage(require('./assets/bulb-lit.png'));
  const dim = useImage(require('./assets/bulb-dim.png'));
  const warm = useImage(require('./assets/bulb-warm.png'));
  const lightAdd = useImage(require('./assets/light-add.png'));
  const lightMul = useImage(require('./assets/light-mul.png'));
  const images = useMemo(() => ({ wall, wallLight, lit, dim, warm, lightAdd, lightMul }), [wall, wallLight, lit, dim, warm, lightAdd, lightMul]);
  const [light, setLight] = useState(false);

  // The cord settles under gravity in initialSim: do it once, not on every render.
  const [initial] = useState(() => initialSim(g, reduced, autoPlay));
  const sim = useSharedValue(initial);
  const rope = useSharedValue<number[]>(initialRope(g));
  const angle = useSharedValue(0);
  const cx = useSharedValue(g.pivotX);
  const cy = useSharedValue(g.socketTop + REPLICA_ART.glassCenterSrc * FIT.k * g.s);
  const radius = useSharedValue(0);
  const fade = useSharedValue(0);
  const level = useSharedValue(0);
  const neon = useSharedValue(1);
  const themeNow = useSharedValue(0);
  const frac = useSharedValue(0); // reveal progress 0..1 including the scrub preview
  const commit = useSharedValue(99); // seconds since the last theme commit
  const clock = useSharedValue(0);
  const tilt = useAnimatedSensor(SensorType.ROTATION);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    const pts: number[] = [];
    for (let i = 0; i < CORD.points; i++) pts.push(s.px[i], s.py[i]);
    rope.set(pts);
    // Phone tilt adds to the swing (the simulator reads 0).
    angle.set(bulbAngle(s, g) + (s.reduced ? 0 : tilt.sensor.value.roll * HERO.tilt));
    const c = glassCenter(s, g);
    cx.set(c.x);
    cy.set(c.y);
    // Reduce Motion: the light scene cross-fades instead of growing from the bulb.
    const reach = revealReach(c, g);
    // Scrub preview: while the cord is pulled the other room bleeds in (or the light room shrinks back).
    const p = s.armed && !s.reduced ? Math.min(1, s.e / (SWITCH.detent * g.s)) : 0;
    const f = s.light === 0 ? s.rv + FEEL.preview * p * p : s.rv - FEEL.preview * p * p;
    radius.set(s.reduced ? (s.rv > 0 ? reach : 0) : Math.max(0, f) * reach);
    frac.set(Math.max(0, Math.min(1, f)));
    commit.set(s.reduced ? 99 : s.commitT);
    clock.set(s.clock);
    fade.set(s.reduced ? s.rv : s.rv > 0 ? 1 : 0);
    level.set(s.level);
    neon.set(igniteValue(s));
    if (s.light !== themeNow.get()) {
      themeNow.set(s.light);
      scheduleOnRN(setLight, s.light === 1);
    }
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(playHaptic, s.events[i]);
    s.events = [];
  }, [sim, rope, angle, cx, cy, radius, fade, level, neon, themeNow, g, frac, commit, clock, tilt]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    stepSim(s, g, (frame.timeSincePreviousFrame / 1000) * QA_SPEED, autoPlay);
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
    .onFinalize((e, ok) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY, cancelled: !ok })), [act]);

  const onToggle = useCallback(() => scheduleOnUI(act, { type: 'toggle' }), [act]);

  // Shared Skia values.
  const bulb = useDerivedValue<Transforms3d>(() => [
    { translateX: g.pivotX }, { translateY: g.pivotY }, { rotate: -angle.value },
    { translateY: g.socketTop - g.pivotY },
  ]);
  // Which stile the bulb lights more: −1 when it swings fully left, 1 fully right.
  const maxAngle = (REPLICA_ART.swingSrc * FIT.k * g.s) / glassArm(g);
  const side = useDerivedValue(() => Math.max(-1, Math.min(1, angle.value / maxAngle)));
  const center = useDerivedValue(() => ({ x: cx.value, y: cy.value }));
  const ropePath = useDerivedValue(() => {
    const p = rope.value;
    const b = Skia.PathBuilder.Make();
    b.moveTo(p[0], p[1]);
    for (let i = 1; i < CORD.points - 1; i++) {
      const mx = (p[i * 2] + p[i * 2 + 2]) / 2;
      const my = (p[i * 2 + 1] + p[i * 2 + 3]) / 2;
      b.quadTo(p[i * 2], p[i * 2 + 1], mx, my);
    }
    const n = CORD.points - 1;
    b.lineTo(p[n * 2], p[n * 2 + 1]);
    return b.build();
  });
  const bead = useDerivedValue<Transforms3d>(() => {
    const p = rope.value;
    const n = CORD.points - 1;
    const dx = p[n * 2] - p[n * 2 - 2];
    const dy = p[n * 2 + 1] - p[n * 2 - 1];
    return [{ translateX: p[n * 2] }, { translateY: p[n * 2 + 1] }, { rotate: Math.atan2(-dx, dy) }, { scale: g.s }];
  });
  const revealR = useDerivedValue(() => Math.max(0.5, radius.value));

  const lightSV = useDerivedValue(() => themeNow.value);
  const values: RoomValues = { bulb, center, level, neon, side, rope: ropePath, bead, commit, clock, light: lightSV };

  // Text recolours when the light's front passes it (or cross-fades under Reduce Motion).
  const textTop = height - insets.bottom - TYPE.bottom - TYPE.hintLine - TYPE.gap - TYPE.titleLine - TYPE.gap - TYPE.eyebrowSize;
  // One derived value per text line: the shared values are read here directly, so the styles update with them.
  const mixAt = (y: number, r: number, x: number, yc: number, f: number) => {
    'worklet';
    if (reduced) return f;
    const d = Math.hypot(TYPE.margin + 40 - x, y - yc);
    return Math.min(1, Math.max(0, (r - d + REVEAL.textBand / 2) / REVEAL.textBand));
  };
  const mixEyebrow = useDerivedValue(() => mixAt(textTop, radius.value, cx.value, cy.value, fade.value));
  const mixTitle = useDerivedValue(() => mixAt(textTop + 30, radius.value, cx.value, cy.value, fade.value));
  const mixHint = useDerivedValue(() => mixAt(textTop + 80, radius.value, cx.value, cy.value, fade.value));
  const eyebrowStyle = useAnimatedStyle(() => ({ color: interpolateColor(mixEyebrow.value, [0, 1], [DARK.mute, LIGHT.mute]) }));
  // Dark ↔ Light rolls like a split-flap as the light's edge reaches the label.
  const darkTitle = useAnimatedStyle(() => ({
    opacity: 1 - mixTitle.value,
    transform: [{ translateY: reduced ? 0 : -TYPE.titleLine * mixTitle.value }],
  }));
  const lightTitle = useAnimatedStyle(() => ({
    opacity: mixTitle.value,
    transform: [{ translateY: reduced ? 0 : TYPE.titleLine * (1 - mixTitle.value) }],
  }));
  const hintStyle = useAnimatedStyle(() => ({ color: interpolateColor(mixHint.value, [0, 1], [DARK.mute, LIGHT.mute]) }));
  const lightLayer = useDerivedValue(() => fade.value);
  const edgeO = useDerivedValue(() => (reduced ? 0 : 4 * frac.value * (1 - frac.value)));
  const flashO = useDerivedValue(() => (themeNow.value === 1 && commit.value < HERO.flash ? HERO.flashPeak * (1 - commit.value / HERO.flash) : 0));

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} collapsable={false}>
        <StatusBar style={light ? 'dark' : 'light'} />
        <Canvas style={StyleSheet.absoluteFill}>
          <RoomScene light={false} g={g} v={values} images={images} />
          {/* The light room, cut to a circle growing from the bulb: a radial gradient used as a dstIn mask on a layer
              (an animated clip or Mask element is not redrawn reliably). */}
          <Group opacity={lightLayer} layer>
            <RoomScene light g={g} v={values} images={images} />
            <Rect x={0} y={0} width={width} height={height} blendMode="dstIn">
              <RadialGradient c={center} r={revealR} colors={REVEAL_EDGE} positions={REVEAL_STOPS} />
            </Rect>
          </Group>
          {/* The reveal's warm soft edge, strongest mid-way. */}
          <Circle c={center} r={revealR} style="stroke" strokeWidth={HERO.edge} color={EDGE} opacity={edgeO}>
            <BlurMask blur={HERO.edge / 2} style="normal" />
          </Circle>
          {/* Payoff: a brief soft white flash from the bulb on commit to light. */}
          <Rect x={0} y={0} width={width} height={height} opacity={flashO}>
            <RadialGradient c={center} r={width} colors={['#FFFFFF', 'rgba(255,255,255,0)']} />
          </Rect>
        </Canvas>

        <View pointerEvents="none" style={[styles.copy, { left: TYPE.margin, right: TYPE.margin, bottom: insets.bottom + TYPE.bottom }]}>
          <Animated.Text style={[styles.eyebrow, eyebrowStyle]}>{COPY.eyebrow}</Animated.Text>
          <View style={styles.titleBox} accessible accessibilityRole="header" accessibilityLabel={`${light ? COPY.light : COPY.dark} theme`}>
            <Animated.Text style={[styles.title, { color: DARK.ink }, darkTitle]}>{COPY.dark}</Animated.Text>
            <Animated.Text style={[styles.title, styles.overlay, { color: LIGHT.ink }, lightTitle]}>{COPY.light}</Animated.Text>
          </View>
          <Animated.Text style={[styles.hint, hintStyle]}>{COPY.hint}</Animated.Text>
        </View>

        <View
          accessible
          accessibilityRole="switch"
          accessibilityLabel={COPY.a11yLabel}
          accessibilityHint={COPY.a11yHint}
          accessibilityState={{ checked: light }}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={onToggle}
          style={[styles.cordTarget, {
            left: g.anchorX - (CORD.hit * g.s) / 2,
            top: g.restY - 8 * g.s,
            width: CORD.hit * g.s,
            height: (CORD.beadHeight + 16) * g.s,
          }]}
        />
      </View>
    </GestureDetector>
  );
}

function initialRope(g: ReturnType<typeof makeGeometry>) {
  const pts: number[] = [];
  for (let i = 0; i < CORD.points; i++) pts.push(g.anchorX, g.anchorY + g.seg * i);
  return pts;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: DARK.wallTop },
  copy: { position: 'absolute' },
  eyebrow: { fontSize: TYPE.eyebrowSize, letterSpacing: TYPE.eyebrowTracking, fontWeight: '600' },
  titleBox: { marginTop: TYPE.gap, height: TYPE.titleLine, overflow: 'hidden' },
  title: { fontFamily: TYPE.titleFamily, fontSize: TYPE.titleSize, lineHeight: TYPE.titleLine },
  overlay: { position: 'absolute', left: 0, top: 0 },
  hint: { marginTop: TYPE.gap, fontSize: TYPE.hintSize, lineHeight: TYPE.hintLine },
  cordTarget: { position: 'absolute' },
});
