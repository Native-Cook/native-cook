/**
 * The upgrade, built on the accepted replica (same images, positions and colours): crank the knob to dispense,
 * the tub comes out of the door, its label panel morphs into the reward card, Collect flies it into the counter.
 */
import {
  Blur, BlurMask, Canvas, Circle, ColorMatrix, Group, Image, LinearGradient, Oval, Paint, Path, PathOp, Rect, RoundedRect, Skia, useImage,
  vec, type SkImage, type SkPath, type Transforms3d,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Image as RNImage, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  FadeInDown, FadeOutUp, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type DerivedValue, type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PHASE, initialCrank, input, makeGeo, stepCrank, type Input } from './crank-sim';
import { COINS, CONFETTI, HAPTIC, KNOB, QA_SPEED, REPLICA_CAMERA, UP_T } from './motion';
import { COPY, REPLICA_ART as A, SPACE, UP } from './theme';

const PRESS_CURVE = cubicBezier(0.23, 1, 0.32, 1);

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.detent) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
  else if (kind === HAPTIC.ratchet) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  else if (kind === HAPTIC.dispense) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.open) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  else if (kind === HAPTIC.collect) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function clamp(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
function easeOutBack(u: number) {
  'worklet';
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}
/** Ease-in-out for on-screen movement, the bezier(0.77, 0, 0.175, 1) family. */
function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 8 * u * u * u * u : 1 - Math.pow(-2 * u + 2, 4) / 2;
}
function easeOutCubic(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

export function GumballUpgrade({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const s = width / 402;
  const g = useMemo(() => makeGeo(width), [width]);
  const room = useImage(require('./assets/room.jpg'));
  const glass = useImage(require('./assets/globe-glass.png'));
  const fill = useImage(require('./assets/globe-fill.png'));
  const knobImg = useImage(require('./assets/knob.png'));
  const pot = useImage(require('./assets/pot.png'));
  const coupon = useImage(require('./assets/coupon.png'));
  const coin = useImage(require('./assets/coin.png'));
  const sliceA = useImage(require('./assets/slice-peach.png'));
  const sliceB = useImage(require('./assets/slice-passion.png'));
  const peel = useImage(require('./assets/peel.png'));

  const sim = useSharedValue(initialCrank(reduced, autoPlay));
  const angle = useSharedValue(0);
  const press = useSharedValue(0);
  const jx = useSharedValue(0);
  const jy = useSharedValue(0);
  const phase = useSharedValue<number>(PHASE.ready);
  const phaseT = useSharedValue(0);
  const clock = useSharedValue(0);
  const conf = useSharedValue<number[]>([]);
  const bump = useSharedValue(0);
  const phaseNow = useSharedValue<number>(PHASE.ready);
  const coinsNow = useSharedValue(0);
  const [ui, setUi] = useState({ phase: PHASE.ready as number, coins: COINS.from });
  // The pill counts up in a few odometer steps when the balance rises (a handful of renders, not per frame).
  const [shown, setShown] = useState(COINS.from);
  useEffect(() => {
    if (ui.coins === shown) return undefined;
    const up = ui.coins > shown;
    const step = Math.ceil(COINS.add / COINS.countSteps);
    // A drop (hidden under the scrim) applies at once; a rise rolls up in steps.
    const id = setTimeout(() => setShown((v) => (up ? Math.min(ui.coins, v + step) : ui.coins)), up ? COINS.countStep : 0);
    return () => clearTimeout(id);
  }, [ui.coins, shown]);

  useFrameCallback((f) => {
    'worklet';
    if (f.timeSincePreviousFrame === null) return;
    const c = sim.get();
    c.reduced = reduced;
    stepCrank(c, g, (f.timeSincePreviousFrame / 1000) * QA_SPEED, autoPlay);
    sim.set(c);
    angle.set(c.angle + (reduced ? 0 : c.wob * 0.06 + c.back * 0.35));
    press.set(c.press);
    jx.set(c.jx);
    jy.set(c.jy);
    phase.set(c.phase);
    phaseT.set(c.phaseT);
    clock.set(c.clock);
    bump.set(c.bump);
    const out: number[] = [];
    for (let i = 0; i < c.px.length; i++) out.push(c.px[i], c.py[i], c.prot[i], c.pcol[i]);
    conf.set(out);
    if (c.phase !== phaseNow.get() || c.coins !== coinsNow.get()) {
      phaseNow.set(c.phase);
      coinsNow.set(c.coins);
      scheduleOnRN(setUi, { phase: c.phase, coins: c.coins });
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
    .manualActivation(true)
    .maxPointers(1)
    .onTouchesDown((e, m) => {
      const t = e.changedTouches[0];
      if (Math.hypot(t.x - g.kx, t.y - g.ky) < KNOB.grab * g.s && phaseNow.get() === PHASE.ready) {
        act({ type: 'down', x: t.x, y: t.y });
        m.activate();
      } else m.fail();
    })
    .onTouchesMove((e) => {
      const t = e.changedTouches[0];
      act({ type: 'move', x: t.x, y: t.y });
    })
    .onFinalize(() => act({ type: 'up' })), [act, g, phaseNow]);
  const command = useCallback((a: 'turn' | 'collect') => scheduleOnUI(act, { type: 'command', action: a }), [act]);

  // ---- Skia values -------------------------------------------------------------------------------------------
  const knob = useDerivedValue<Transforms3d>(() => [
    { translateX: g.kx }, { translateY: g.ky }, { rotate: A.knob.tilt + angle.value }, { scale: 1 + (KNOB.press - 1) * press.value },
  ]);
  // The knob's affordance ring breathes while the machine waits for a turn.
  const ringO = useDerivedValue(() => (phase.value === PHASE.ready && !reduced ? 0.35 + 0.25 * Math.sin(clock.value * 2.4) : 0));
  const fillT = useDerivedValue<Transforms3d>(() => [{ translateX: jx.value * 0.05 * s }, { translateY: jy.value * 0.05 * s }]);
  // Anticipation: the machine squashes 2 % before the tub comes out.
  const machine = useDerivedValue<Transforms3d>(() => {
    const t = phaseT.value;
    const q = phase.value === PHASE.dispensing && t < UP_T.squash && !reduced ? Math.sin((Math.PI * t) / UP_T.squash) * 0.02 : 0;
    const by = 645 * s;
    return [{ translateY: by }, { scaleY: 1 - q }, { scaleX: 1 + q * 0.5 }, { translateY: -by }];
  });
  // Tub out of the door (the source's beat), held while the coupon peels off, back in after the collect.
  const out = useDerivedValue(() => {
    const t = phaseT.value;
    if (phase.value === PHASE.dispensing) return reduced ? (t > UP_T.out[0] ? 1 : 0) : easeOutBack(clamp((t - UP_T.out[0]) / (UP_T.out[1] - UP_T.out[0])));
    if (phase.value === PHASE.reward) return 1;
    if (phase.value === PHASE.collecting) return t < UP_T.collect ? 1 : 1 - easeOutCubic(clamp((t - UP_T.collect) / UP_T.back));
    return 0;
  });
  // Hero: the label panel peels off into the coupon; the stage behind dims, blurs, and the tub steps back.
  const morph = useDerivedValue(() => {
    const t = phaseT.value;
    if (phase.value === PHASE.dispensing) return reduced ? 0 : easeOutCubic(clamp((t - UP_T.morph[0]) / (UP_T.morph[1] - UP_T.morph[0])));
    if (phase.value === PHASE.reward) return 1;
    if (phase.value === PHASE.collecting) return 1 - easeInOut(clamp((t - UP_T.couponOut[0]) / (UP_T.couponOut[1] - UP_T.couponOut[0])));
    return 0;
  });
  const potT = useDerivedValue<Transforms3d>(() => {
    const u = out.value;
    const x = A.exit.x * s + (A.pot.cx - A.exit.x) * s * u;
    const y = A.exit.y * s + (A.pot.cy - A.exit.y) * s * u;
    const back = 1 - (1 - UP.tubBack) * morph.value;
    // Never from scale(0): the tub starts at 0.3 and fades in over the first 20 % (animate-expo never-ship).
    return [{ translateX: x }, { translateY: y }, { scale: (0.3 + 0.7 * u) * back }];
  });
  const potO = useDerivedValue(() => clamp(out.value / 0.2));
  // The source's camera push-in while the tub comes out (as in the replica), to state b's framing at full out.
  const camera = useDerivedValue<Transforms3d>(() => {
    const u = reduced ? 0 : clamp(out.value);
    const e = u * u * (3 - 2 * u);
    const B = REPLICA_CAMERA.b;
    const k = width / 1206;
    return [{ translateX: B[1] * e * k }, { translateY: B[2] * e * k }, { scale: 1 + (B[0] - 1) * e }];
  });
  const shadowO = useDerivedValue(() => 0.25 * clamp(out.value));
  const shadowT = useDerivedValue<Transforms3d>(() => [{ translateX: A.pot.cx * s }, { translateY: UP.shadowY * s }, { scale: 0.3 + 0.7 * clamp(out.value) }]);
  const stageO = useDerivedValue(() => morph.value); // the pre-blurred copy of the scene, crossfaded in
  const scrimO = useDerivedValue(() => UP.scrim * morph.value);
  // The coupon: from the panel's rect on the tub (tilted as it lifts off) to its place on the stage.
  const P = UP.panel;
  const C = UP.card;
  const couponT = useDerivedValue<Transforms3d>(() => {
    const m = morph.value;
    const cx = (P.x + P.w / 2 + (C.x + C.w / 2 - P.x - P.w / 2) * m) * s;
    const cy = (P.y + P.h / 2 + (C.y + C.h / 2 - P.y - P.h / 2) * m) * s;
    const drop = phase.value === PHASE.collecting ? (1 - m) * 40 * s : 0;
    return [{ translateX: cx }, { translateY: cy + drop }, { rotate: UP.peel * Math.sin(Math.PI * m) }];
  });
  const couponSize = useDerivedValue(() => {
    const m = phase.value === PHASE.collecting ? 1 : morph.value;
    const w = (P.w + (C.w - P.w) * m) * s;
    const h = (P.h + (C.h - P.h) * m) * s;
    return { rect: { x: -w / 2, y: -h / 2, width: w, height: h }, rx: C.r * s, ry: C.r * s };
  });
  const couponRect = useDerivedValue(() => couponSize.value.rect);
  const panelO = useDerivedValue(() => (morph.value > 0.001 ? 1 - clamp(morph.value / 0.5) : 0));
  const stubCoinO = useDerivedValue(() => (phase.value === PHASE.collecting ? 0 : clamp((morph.value - 0.6) / 0.4)));
  const couponO = useDerivedValue(() => (phase.value === PHASE.collecting ? morph.value : clamp((morph.value - 0.2) / 0.4)));
  // Specular sweep across the globe on dispense, and an idle glint every few seconds.
  const sweepX = useDerivedValue(() => {
    const t = phase.value === PHASE.dispensing ? phaseT.value / UP_T.sweep : (clock.value % UP_T.idleSweep) / UP_T.sweep;
    return t < 1 ? (A.globe.x - A.globe.r - 40 + (A.globe.r * 2 + 80) * t) * s : -999;
  });
  const sweepT = useDerivedValue<Transforms3d>(() => [{ translateX: sweepX.value }, { translateY: (A.globe.y - A.globe.r) * s }, { skewX: -0.4 }]);
  const confO = useDerivedValue(() => (conf.value.length ? clamp(1.8 - phaseT.value * 0.8) : 0));

  const globeClip = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.addCircle(A.globe.x * s, A.globe.y * s, (A.globe.r - 4) * s);
    const floor = Skia.PathBuilder.Make();
    floor.addRect({ x: 0, y: 0, width, height: A.globe.floor * s });
    return Skia.Path.MakeFromOp(b.build(), floor.build(), PathOp.Intersect) ?? b.build();
  }, [s, width]);
  const glarePath = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const r = A.glare.r * s;
    b.addArc({ x: A.globe.x * s - r, y: A.globe.y * s - r, width: r * 2, height: r * 2 }, A.glare.from, A.glare.sweep);
    return b.build();
  }, [s]);
  const scene = { width, height, s, g, room, fill, glass, knobImg, globeClip, glarePath, machine, fillT, sweepT, ringO, knob };

  // Coins: from the coupon's stub to the pill on a curved path, staggered.
  const pillX = UP.counter.left + 22;
  const pillY = insets.top / s + UP.counter.top + 18;
  const stubX = C.x + C.w * UP.stub.x;
  const stubY = C.y + C.h * UP.stub.y;

  // ---- RN overlay ---------------------------------------------------------------------------------------------
  const cardText = useAnimatedStyle(() => ({ opacity: phase.value === PHASE.collecting ? morph.value : clamp((morph.value - 0.75) / 0.25) }));
  const counterStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.18 * bump.value }], opacity: 1 - 0.7 * clamp(scrimO.value / UP.scrim) * (phase.value === PHASE.collecting ? 0 : 1) }));
  const showCard = ui.phase === PHASE.reward;
  const sprites = [sliceA, sliceB, peel];

  return (
    <View style={StyleSheet.absoluteFill}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={camera}>
              <Scene {...scene} />
            </Group>
            <Group transform={shadowT} opacity={shadowO}>
              <Oval x={-120 * s} y={-14 * s} width={240 * s} height={28 * s} color={UP.shadow}>
                <BlurMask blur={14 * s} style="normal" />
              </Oval>
            </Group>
            <Group transform={potT} opacity={potO}>
              <Image image={pot} x={(-A.pot.w / 2) * s} y={(-A.pot.h / 2) * s} width={A.pot.w * s} height={A.pot.h * s} fit="fill" />
            </Group>
            {/* The stage: a blurred copy of everything above (fixed radius), crossfaded in, then a dim. */}
            <Group opacity={stageO} layer={<Paint><Blur blur={UP.blur * s} /></Paint>}>
              <Group transform={camera}>
                <Scene {...scene} />
              </Group>
              <Group transform={potT}>
                <Image image={pot} x={(-A.pot.w / 2) * s} y={(-A.pot.h / 2) * s} width={A.pot.w * s} height={A.pot.h * s} fit="fill" />
              </Group>
            </Group>
            <Rect x={0} y={0} width={width} height={height} color={UP.scrimColor} opacity={scrimO} />
            {CONFETTI_IDX.map((i) => <Confetti key={i} index={i} conf={conf} sprites={sprites} s={s} opacity={confO} />)}
            <Group transform={couponT}>
              <Group opacity={panelO}>
                <RoundedRect rect={couponSize} color={UP.navy} />
              </Group>
              <Group opacity={couponO}>
                <Image image={coupon} rect={couponRect} fit="fill" />
                {/* The stub carries the coin the collect will send to the pill. */}
                <Group opacity={stubCoinO}>
                  <Image image={coin} x={(C.w * (UP.stub.x - 0.5) - 20) * s} y={-20 * s} width={40 * s} height={40 * s} fit="fill" />
                </Group>
              </Group>
            </Group>
          </Canvas>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={COPY.knob}
            accessibilityHint={COPY.knobHint}
            onPress={() => command('turn')}
            style={[styles.knob, { left: g.kx - 36 * s, top: g.ky - 36 * s, width: 72 * s, height: 72 * s }]}
          />
        </View>
      </GestureDetector>

      <Animated.View style={[styles.counter, { top: insets.top + UP.counter.top, left: UP.counter.left }, counterStyle]} accessible accessibilityLabel={`${ui.coins} coins`}>
        <RNImage source={require('./assets/coin.png')} style={styles.coin} />
        <View style={styles.countBox}>
          <Animated.Text key={shown} entering={reduced ? undefined : FadeInDown.duration(140)} exiting={reduced ? undefined : FadeOutUp.duration(120)} style={styles.count}>
            {shown.toLocaleString('en-US')}
          </Animated.Text>
        </View>
        <Text style={styles.coinsWord}>{COPY.coinsWord}</Text>
      </Animated.View>

      <Animated.View
        pointerEvents={showCard ? 'auto' : 'none'}
        style={[styles.cardText, { left: (C.x + C.w * UP.couponText.x) * s, top: (C.y + C.h * UP.couponText.y) * s, width: C.w * UP.couponText.w * s }, cardText]}
      >
        <Text style={styles.eyebrow}>{COPY.prize.toUpperCase()}</Text>
        <Text style={styles.amount}>{COPY.amount}</Text>
        <Text style={styles.meta}>{COPY.cardMeta}</Text>
      </Animated.View>
      <Animated.View pointerEvents={showCard ? 'auto' : 'none'} style={[styles.collectWrap, { top: (C.y + C.h + SPACE.lg) * s }, cardText]}>
        <Pressable accessibilityRole="button" accessibilityLabel={COPY.collect} onPress={() => command('collect')} pressRetentionOffset={16}>
          {/* Press feedback on press-in: 0.97 in 100 ms, back in 160 ms; a transition, so a quick tap retargets. */}
          {({ pressed }) => <Animated.View style={[styles.button, {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform', transitionDuration: pressed ? 100 : 160, transitionTimingFunction: PRESS_CURVE,
          }]}>
            <RNImage source={require('./assets/coin.png')} style={styles.buttonCoin} />
            <Text style={styles.buttonText}>{COPY.collect}</Text>
          </Animated.View>}
        </Pressable>
      </Animated.View>

      {/* Coins fly above everything, into the pill. */}
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
        {COIN_IDX.map((i) => (
          <FlyingCoin key={i} index={i} image={coin} phase={phase} phaseT={phaseT} s={s} from={[stubX, stubY]} to={[pillX, pillY]} />
        ))}
      </Canvas>
    </View>
  );
}

const CONFETTI_IDX = Array.from({ length: CONFETTI.count }, (_, i) => i);
const COIN_IDX = Array.from({ length: UP_T.coins }, (_, i) => i);

type SceneProps = {
  width: number; height: number; s: number; g: { kx: number; ky: number };
  room: SkImage | null; fill: SkImage | null; glass: SkImage | null; knobImg: SkImage | null;
  globeClip: SkPath; glarePath: SkPath;
  machine: DerivedValue<Transforms3d>; fillT: DerivedValue<Transforms3d>; sweepT: DerivedValue<Transforms3d>;
  ringO: DerivedValue<number>; knob: DerivedValue<Transforms3d>;
};

/** The replica's scene (room, door, globe, glass, knob) with the upgrade's sweep, jostle and ring. */
function Scene({ width, height, s, g, room, fill, glass, knobImg, globeClip, glarePath, machine, fillT, sweepT, ringO, knob }: SceneProps) {
  return (
    <Group transform={machine}>
      <Image image={room} x={0} y={0} width={width} height={height} fit="fill" />
      <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s}>
        <LinearGradient start={vec(0, A.door.y * s)} end={vec(0, (A.door.y + A.door.h) * s)} colors={[A.door.top, A.door.bottom]} />
      </RoundedRect>
      <RoundedRect x={A.door.x * s} y={A.door.y * s} width={A.door.w * s} height={A.door.h * s} r={A.door.r * s} style="stroke" strokeWidth={6 * s} color={A.door.shade}>
        <BlurMask blur={5 * s} style="inner" />
      </RoundedRect>
      <Group clip={globeClip}>
        <Group transform={fillT}>
          <Image image={fill} x={0} y={0} width={width} height={height} fit="fill">
            <Blur blur={A.fillBlur * s} />
          </Image>
        </Group>
        <Rect x={0} y={0} width={width} height={height} color={A.haze} />
        <Group transform={sweepT}>
          <Rect x={0} y={0} width={UP.sweepW * s} height={A.globe.r * 2 * s} color={UP.sweep}>
            <BlurMask blur={10 * s} style="normal" />
          </Rect>
        </Group>
      </Group>
      <Image image={glass} x={0} y={0} width={width} height={height} fit="fill" />
      <Path path={glarePath} style="stroke" strokeWidth={A.glare.width * s} strokeCap="round" color={A.glare.color}>
        <BlurMask blur={A.glare.blur * s} style="normal" />
      </Path>
      <Circle cx={A.globe.x * s} cy={A.globe.y * s} r={(A.globe.r - 2) * s} style="stroke" strokeWidth={A.rim.width * s} color={A.rim.color} />
      <Circle cx={g.kx} cy={g.ky} r={UP.ring * s} style="stroke" strokeWidth={2 * s} color={UP.ringColor} opacity={ringO}>
        <BlurMask blur={2} style="normal" />
      </Circle>
      <Group transform={knob}>
        <Image image={knobImg} x={(-A.knob.size / 2) * s} y={(-A.knob.size / 2) * s} width={A.knob.size * s} height={A.knob.size * s} fit="fill">
          <ColorMatrix matrix={A.knobMatrix} />
          <Blur blur={A.knobBlur * s} />
        </Image>
      </Group>
    </Group>
  );
}

/** One confetti piece: a fruit slice or peel sprite, seeded size, spinning under gravity. */
function Confetti({ index, conf, sprites, s, opacity }: {
  index: number; conf: SharedValue<number[]>; sprites: (SkImage | null)[]; s: number; opacity: DerivedValue<number>;
}) {
  const size = (CONFETTI.size[0] + ((index * 7) % 9) * ((CONFETTI.size[1] - CONFETTI.size[0]) / 8)) * s * 1.6;
  const t = useDerivedValue<Transforms3d>(() => {
    const p = conf.value;
    const k = index * 4;
    if (k + 3 >= p.length) return [{ translateX: -999 }];
    return [{ translateX: p[k] }, { translateY: p[k + 1] }, { rotate: p[k + 2] }];
  });
  return (
    <Group transform={t} opacity={opacity}>
      <Image image={sprites[index % UP.confettiKinds]} x={-size / 2} y={-size / 2} width={size} height={size} fit="contain" />
    </Group>
  );
}

/** One coin of the collect: stub → pill along a quadratic curve, staggered, shrinking into the pill. */
function FlyingCoin({ index, image, phase, phaseT, s, from, to }: {
  index: number; image: SkImage | null; phase: SharedValue<number>; phaseT: SharedValue<number>; s: number; from: number[]; to: number[];
}) {
  const u = useDerivedValue(() => (phase.value === PHASE.collecting ? clamp((phaseT.value - index * UP_T.coinStagger) / UP_T.coinFly) : 0));
  const t = useDerivedValue<Transforms3d>(() => {
    const e = easeInOut(u.value);
    const cx = (from[0] + to[0]) / 2 + 40 - index * 6;
    const cy = Math.min(from[1], to[1]) - 120 + index * 8;
    const x = (1 - e) * (1 - e) * from[0] + 2 * (1 - e) * e * cx + e * e * to[0];
    const y = (1 - e) * (1 - e) * from[1] + 2 * (1 - e) * e * cy + e * e * to[1];
    return [{ translateX: x * s }, { translateY: y * s }, { scale: 1 - 0.45 * e }];
  });
  const o = useDerivedValue(() => (u.value > 0 && u.value < 1 ? 1 : 0));
  const d = 26 * s;
  return (
    <Group transform={t} opacity={o}>
      <Image image={image} x={-d / 2} y={-d / 2} width={d} height={d} fit="fill" />
    </Group>
  );
}

const styles = StyleSheet.create({
  knob: { position: 'absolute', borderRadius: 36 },
  counter: {
    position: 'absolute', flexDirection: 'row', alignItems: 'center', height: 36, paddingHorizontal: SPACE.md, borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.72)',
  },
  coin: { width: 20, height: 20, marginRight: SPACE.sm },
  coinsWord: { fontSize: 12, color: 'rgba(59,35,80,0.55)', marginLeft: SPACE.xs },
  countBox: { height: 22, overflow: 'hidden' },
  count: { fontSize: 16, lineHeight: 22, fontWeight: '700', color: '#3B2350', fontVariant: ['tabular-nums'], letterSpacing: -0.2 },
  cardText: { position: 'absolute' },
  eyebrow: { color: '#F6C27A', fontSize: 10, letterSpacing: 1.6, fontWeight: '700' },
  amount: { color: '#FFFFFF', fontSize: 44, lineHeight: 48, fontWeight: '800', letterSpacing: -1.2, fontVariant: ['tabular-nums'] },
  meta: { color: 'rgba(255,255,255,0.6)', fontSize: 11, fontVariant: ['tabular-nums'] },
  collectWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  button: {
    flexDirection: 'row', height: 44, paddingHorizontal: SPACE.xl, borderRadius: 22, backgroundColor: '#F2B26A', alignItems: 'center', justifyContent: 'center',
  },
  buttonCoin: { width: 18, height: 18, marginRight: SPACE.sm },
  buttonText: { color: '#293267', fontSize: 16, fontWeight: '700' },
});
