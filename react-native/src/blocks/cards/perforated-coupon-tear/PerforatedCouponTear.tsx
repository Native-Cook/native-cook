import {
  BlurMask,
  Canvas,
  Fill,
  Glyphs,
  Group,
  LinearGradient,
  Path,
  PathOp,
  RoundedRect,
  Shader,
  Skia,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CREAM, CouponBody, type Fonts, INK, MONO, PAPER, ROAST, SANS, StampRow, StubFace, Wallet, labelWidth, tracked } from './CouponParts';
import {
  EDGE,
  GRAIN_SKSL,
  INK_SKSL,
  beansPath,
  bodyPath,
  fibrePath,
  holesPath,
  stubPath,
} from './couponArt';
import {
  CARD_H,
  FLY_START,
  type Fly,
  type Geom,
  LOOP,
  POCKET_SCALE,
  STAMP_AT,
  STUB_W,
  attachedPose,
  holeCount,
  scriptPose,
  userPose,
} from './couponMotion';

type Props = {
  /** Tears the stub, files it in the wallet, stamps the coupon and brings a fresh one, every 9 s. */
  autoPlay?: boolean;
};

/** Seconds after the last touch before the loop starts again. */
const IDLE = 2;
/** Finger travel (pt, down and away) that rips the whole perforation. */
const TEAR_TRAVEL = 210;

export function PerforatedCouponTear({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const looping = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const W = size.width;
  const H = size.height;

  // Layout (pt). The coupon is the hero; the wallet below it fills the rest of the screen.
  const cardX = 16;
  const cardY = insets.top + 186;
  const cw = Math.max(200, W - 32);
  const px = cw - STUB_W;
  const pocketTop = cardY + CARD_H + 78;
  const pocketBottom = Math.max(pocketTop + 96, H - insets.bottom - 96);
  const frontTop = pocketTop + 30;
  const pocketCx = W / 2;
  const geom: Geom = useMemo(
    () => ({ cw, px, gx: px + 64, gy: 30, pocketX: pocketCx + 43 - cardX, pocketY: frontTop - 22 - cardY, travel: W + 24, front: frontTop - cardY }),
    [cw, px, pocketCx, frontTop, cardY, W],
  );

  const fonts = useMemo<Fonts>(
    () => ({
      free: matchFont({ fontFamily: SANS, fontSize: 60, fontWeight: '800' }),
      small: matchFont({ fontFamily: SANS, fontSize: 14, fontWeight: '500' }),
      label: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
      mono: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '400' }),
      stamp: matchFont({ fontFamily: SANS, fontSize: 21, fontWeight: '800' }),
      tiny: matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '700' }),
    }),
    [],
  );
  const grain = useMemo(() => Skia.RuntimeEffect.Make(GRAIN_SKSL), []);
  const inkFx = useMemo(() => Skia.RuntimeEffect.Make(INK_SKSL), []);

  // Clock and finger state. `user` = 1 while the finger owns the stub (or it springs back).
  const clock = useSharedValue(0);
  const running = useSharedValue(looping);
  const user = useSharedValue(0);
  const idle = useSharedValue(0);
  const userRun = useSharedValue(0);
  const fly = useSharedValue<Fly>({ x: 0, y: 0, phi: 0, on: 0 });
  const uTear = useSharedValue(0);
  const uFree = useSharedValue(0);
  const uX = useSharedValue(0);
  const uY = useSharedValue(0);
  const uPhi = useSharedValue(0);
  const uPhiV = useSharedValue(0);
  const uVx = useSharedValue(0);
  const grabbing = useSharedValue(0);
  const startTear = useSharedValue(0);
  const offX = useSharedValue(0);
  const offY = useSharedValue(0);
  const lastHole = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (user.get() === 1) {
      if (uFree.get() > 0) {
        // The free stub hangs from the finger like a pendulum; a sideways flick swings it.
        const target = -0.09 - Math.max(-0.5, Math.min(0.5, uVx.get() * 0.0006));
        const a = -70 * (uPhi.get() - target) - 6 * uPhiV.get();
        uPhiV.set(uPhiV.get() + a * dt);
        uPhi.set(uPhi.get() + uPhiV.get() * dt);
        uVx.set(uVx.get() * 0.9);
      }
      if (grabbing.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE && Math.abs(uTear.get()) < 0.002) {
          user.set(0);
          clock.set(0);
          running.set(looping);
        }
      }
      return;
    }
    if (!running.get()) return;
    let t = clock.get() + dt;
    if (t >= LOOP) {
      t -= LOOP;
      fly.set({ x: 0, y: 0, phi: 0, on: 0 });
      userRun.set(0);
      if (!looping) {
        t = 0;
        running.set(false);
      }
    }
    clock.set(t);
  });

  const pose = useDerivedValue(() => {
    if (user.get() === 1) return userPose(geom, uTear.get(), uFree.get(), uX.get(), uY.get(), uPhi.get());
    return scriptPose(geom, clock.get(), fly.get());
  });

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  useAnimatedReaction(
    () => (userRun.get() === 1 && user.get() === 0 && clock.get() >= STAMP_AT ? 1 : 0),
    (now, before) => {
      if (now === 1 && before === 0) scheduleOnRN(feel, 1);
    },
  );

  const ys = EDGE.ys;
  const js = EDGE.js;
  const body = useDerivedValue(() => bodyPath(px, pose.get().front, ys, js));
  const stub = useDerivedValue(() => stubPath(px, cw, pose.get().front, ys, js));
  const bodyFibres = useDerivedValue(() => fibrePath(px, pose.get().front, ys, js, 1));
  const stubFibres = useDerivedValue(() => fibrePath(px, pose.get().front, ys, js, -1));
  // A filed stub has its half holes really cut out (it lies on dark leather, not on the paper).
  const tornStub = useMemo(() => {
    const shape = stubPath(px, cw, CARD_H, ys, js);
    return Skia.Path.MakeFromOp(shape, holesPath(px), PathOp.Difference) ?? shape;
  }, [px, cw, ys, js]);
  const tornFibres = useMemo(() => fibrePath(px, CARD_H, ys, js, -1), [px, ys, js]);

  const couponT = useDerivedValue(() => [{ translateX: pose.get().slide }]);
  const freshT = useDerivedValue(() => [{ translateX: pose.get().fresh }]);
  const freshOpacity = useDerivedValue(() => pose.get().freshOn);
  const freshBody = useMemo(() => bodyPath(px, 0, ys, js), [px, ys, js]);
  const freshStub = useMemo(() => stubPath(px, cw, 0, ys, js), [px, cw, ys, js]);
  const noFibres = useMemo(() => Skia.PathBuilder.Make().build(), []);
  const stubT = useDerivedValue(() => {
    const p = pose.get();
    return [
      { translateX: p.sx },
      { translateY: p.sy },
      { rotate: p.phi },
      { scale: p.scale },
      { translateX: -geom.gx },
      { translateY: -geom.gy },
    ];
  });
  const liveOpacity = useDerivedValue(() => (pose.get().filed > 0 ? 0 : 1));
  const filedOpacity = useDerivedValue(() => (pose.get().filed > 0 ? 1 : 0));
  const shadowBlur = useDerivedValue(() => 3 + 9 * pose.get().lift);
  const shadowT = useDerivedValue(() => {
    const l = pose.get().lift;
    return [{ translateX: 1 + 5 * l }, { translateY: 2 + 12 * l }];
  });
  const shadowOpacity = useDerivedValue(() => 0.18 + 0.16 * pose.get().lift);
  const stampT = useDerivedValue(() => {
    const s = Math.max(0.0001, pose.get().stamp);
    return [{ translateX: px * 0.56 }, { translateY: 104 }, { rotate: -0.21 }, { scale: s }];
  });
  const stampOpacity = useDerivedValue(() => {
    const s = pose.get().stamp;
    return s <= 0 ? 0 : Math.min(1, Math.max(0.15, (1.36 - s) / 0.2));
  });
  const inkUniforms = { ink: INK };

  // Static art, built once per layout.
  const beans = useMemo(() => beansPath(px, CARD_H), [px]);
  const holes = useMemo(() => holesPath(px), [px]);
  const glyphs = useMemo(
    () => ({
      reward: tracked(fonts.label, 'REWARD', 22, 34, 1.8),
      exp: tracked(fonts.label, 'EXP 11/30', px - 20, 34, 1.4, 1),
      free: tracked(fonts.free, 'FREE', 19, 102, -1),
      any: tracked(fonts.small, 'Any drink, any size, any day', 22, 126, 0),
      code: tracked(fonts.tiny, 'CODE', 22, 176, 1.6),
      codeNo: tracked(fonts.mono, 'BC-4471-0925', 22 + labelWidth(fonts.tiny, 'CODE', 1.6) + 8, 177, 0.6),
      redeemed: tracked(fonts.stamp, 'REDEEMED', 0, 7.5, 1.2, 0.5),
    }),
    [fonts, px],
  );
  const oldStubs = useMemo(
    () =>
      [
        { dx: -96, tilt: -0.1 },
        { dx: -30, tilt: 0.05 },
      ].map(({ dx, tilt }) => [
        { translateX: pocketCx + dx + 43 - cardX },
        { translateY: frontTop - 22 - cardY + 6 },
        { rotate: tilt },
        { scale: POCKET_SCALE },
        { translateX: -geom.gx },
        { translateY: -geom.gy },
      ]),
    [pocketCx, frontTop, cardY, geom],
  );
  const pocketClip = useMemo(() => Skia.XYWHRect(-cardX, -cardY, W, pocketBottom - 2), [W, pocketBottom, cardY]);
  // The finger: grab the stub, pull it down and away to rip it, let go to file it.
  function canGrab(x: number, y: number) {
    'worklet';
    const p = pose.get();
    if (p.slide !== 0 || p.filed > 0 || p.scale < 1) return false;
    const lx = x - cardX;
    const ly = y - cardY;
    if (p.tear >= 1) return Math.hypot(lx - p.sx, ly - p.sy) < 120;
    return lx > px - 8 && lx < cw + 12 && ly > -12 && ly < CARD_H + 12;
  }

  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart((e) => {
      if (!canGrab(e.x, e.y)) return;
      const p = pose.get();
      grabbing.set(1);
      idle.set(0);
      user.set(1);
      running.set(false);
      if (p.tear >= 1) {
        uFree.set(1);
        uX.set(p.sx);
        uY.set(p.sy);
        uPhi.set(p.phi);
        uPhiV.set(0);
        offX.set(e.x - cardX - p.sx);
        offY.set(e.y - cardY - p.sy);
      } else {
        uFree.set(0);
        uTear.set(p.tear);
        startTear.set(p.tear);
        lastHole.set(Math.floor(p.tear * holeCount()));
      }
    })
    .onUpdate((e) => {
      if (grabbing.get() === 0) return;
      idle.set(0);
      if (uFree.get() > 0) {
        uX.set(e.x - cardX - offX.get());
        uY.set(e.y - cardY - offY.get());
        uVx.set(e.velocityX);
        return;
      }
      const raw = startTear.get() + (e.translationY * 0.85 + e.translationX * 0.55) / TEAR_TRAVEL;
      // Pushing the stub back in only gives a little (rubber band).
      const tear = raw < 0 ? -(-raw / (1 + -raw * 8)) : raw;
      if (tear >= 1) {
        const a = attachedPose(geom, 1, 0);
        uFree.set(1);
        uTear.set(1);
        uX.set(a.sx);
        uY.set(a.sy);
        uPhi.set(a.phi);
        uPhiV.set(0);
        offX.set(e.x - cardX - a.sx);
        offY.set(e.y - cardY - a.sy);
        scheduleOnRN(feel, 1);
        return;
      }
      uTear.set(tear);
      const hole = Math.floor(Math.max(0, tear) * holeCount());
      if (Math.abs(hole - lastHole.get()) >= 2) {
        lastHole.set(hole);
        scheduleOnRN(feel, 0);
      }
    })
    .onEnd((e) => {
      if (grabbing.get() === 0) return;
      if (uFree.get() > 0) {
        // Let go of a free stub: it flies into the wallet and the script finishes the story.
        fly.set({ x: uX.get(), y: uY.get(), phi: uPhi.get(), on: 1 });
        uFree.set(0);
        uTear.set(0);
        user.set(0);
        userRun.set(1);
        clock.set(FLY_START);
        running.set(true);
        return;
      }
      const v = (e.velocityY * 0.85 + e.velocityX * 0.55) / TEAR_TRAVEL;
      uTear.set(withSpring(0, { duration: 420, dampingRatio: 0.78, velocity: v }));
    })
    .onFinalize(() => {
      grabbing.set(0);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const ready = W > 0 && grain && inkFx;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Free coffee coupon. Drag the stub down to tear it off and file it in your wallet.">
          {ready ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <LinearGradient start={vec(0, 0)} end={vec(W * 0.4, H)} colors={PAPER} />
              </Fill>
              <Fill blendMode="softLight">
                <Shader source={grain} uniforms={{ amount: 0.5 }} />
              </Fill>
              <StampRow x={cardX} width={cw} y={insets.top + 150} />

              <Group transform={[{ translateX: cardX }, { translateY: cardY }]}>
                {/* The wallet: a leather pocket whose back stands up behind the filed stubs. */}
                <Wallet left={24 - cardX} top={pocketTop - cardY} width={W - 48} bottom={pocketBottom - cardY} front={frontTop - cardY} grain={grain} font={fonts.tiny}>
                  <Group clip={pocketClip}>
                    {oldStubs.map((tr, i) => (
                      <Group key={i} transform={tr}>
                        <StubFace path={tornStub} fibres={tornFibres} px={px} cw={cw} fonts={fonts} filed />
                      </Group>
                    ))}
                    <Group transform={stubT} opacity={filedOpacity}>
                      <StubFace path={tornStub} fibres={tornFibres} px={px} cw={cw} fonts={fonts} filed />
                    </Group>
                  </Group>
                </Wallet>

                {/* The next coupon, sliding in while the redeemed one is still on its way out. */}
                <Group transform={freshT} opacity={freshOpacity}>
                  <StubFace path={freshStub} fibres={noFibres} px={px} cw={cw} fonts={fonts} />
                  <CouponBody body={freshBody} fibres={noFibres} px={px} fonts={fonts} glyphs={glyphs} beans={beans} holes={holes} grain={grain} />
                </Group>
                <Group transform={couponT}>
                  {/* The live stub sits under the body, so its still-joined part tucks beneath it as it turns. */}
                  <Group transform={stubT} opacity={liveOpacity}>
                    <Group transform={shadowT} opacity={shadowOpacity}>
                      <Path path={stub} color="#2A160C">
                        <BlurMask blur={shadowBlur} style="normal" />
                      </Path>
                    </Group>
                    <StubFace path={stub} fibres={stubFibres} px={px} cw={cw} fonts={fonts} />
                  </Group>
                  <CouponBody body={body} fibres={bodyFibres} px={px} fonts={fonts} glyphs={glyphs} beans={beans} holes={holes} grain={grain} />
                  <Group transform={stampT} opacity={stampOpacity}>
                    <RoundedRect x={-84} y={-24} width={168} height={48} r={8} style="stroke" strokeWidth={3}>
                      <Shader source={inkFx} uniforms={inkUniforms} />
                    </RoundedRect>
                    <RoundedRect x={-78} y={-18} width={156} height={36} r={5} style="stroke" strokeWidth={1.2}>
                      <Shader source={inkFx} uniforms={inkUniforms} />
                    </RoundedRect>
                    <Glyphs font={fonts.stamp} glyphs={glyphs.redeemed}>
                      <Shader source={inkFx} uniforms={inkUniforms} />
                    </Glyphs>
                  </Group>
                </Group>
              </Group>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <View style={styles.brand}>
          <View style={styles.logo}>
            <Text style={styles.logoText}>BC</Text>
          </View>
          <Text style={styles.brandName}>Bean Counter</Text>
        </View>
        <View style={styles.chip}>
          <Text style={styles.chipText}>Larch St.</Text>
        </View>
      </View>
      <View pointerEvents="none" style={[styles.titles, { top: insets.top + 66 }]}>
        <Text style={styles.title}>Your free coffee</Text>
        <Text style={styles.sub}>10 of 10 stamps · tear the stub at the counter</Text>
      </View>
      <View pointerEvents="none" style={[styles.walletHead, { top: cardY + CARD_H + 30 }]}>
        <Text style={styles.walletTitle}>Wallet</Text>
        <Text style={styles.walletSub}>
          <Text style={styles.num}>2</Text> rewards redeemed this month
        </Text>
      </View>
      <View style={[styles.footer, { bottom: insets.bottom + 24 }]}>
        <Pressable style={({ pressed }) => [styles.button, pressed && styles.pressed]} accessibilityRole="button">
          <Text style={styles.buttonText}>Show code at counter</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: PAPER[1] },
  header: { position: 'absolute', left: 20, right: 20, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  logo: { width: 32, height: 32, borderRadius: 16, backgroundColor: ROAST, alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 2px rgba(46,26,14,0.3)' },
  logoText: { color: CREAM, fontSize: 12, fontWeight: '800', letterSpacing: 0.5 },
  brandName: { color: ROAST, fontSize: 16, fontWeight: '700' },
  chip: { height: 28, paddingHorizontal: 12, borderRadius: 14, borderCurve: 'continuous', backgroundColor: 'rgba(58,36,24,0.08)', justifyContent: 'center' },
  chipText: { color: 'rgba(58,36,24,0.75)', fontSize: 13, fontWeight: '600' },
  titles: { position: 'absolute', left: 20, right: 20, gap: 4 },
  title: { color: ROAST, fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: 'rgba(58,36,24,0.62)', fontSize: 15, fontWeight: '500' },
  walletHead: { position: 'absolute', left: 20, right: 20, gap: 2 },
  walletTitle: { color: ROAST, fontSize: 17, fontWeight: '700' },
  walletSub: { color: 'rgba(58,36,24,0.6)', fontSize: 13, fontWeight: '500' },
  num: { fontVariant: ['tabular-nums'], fontWeight: '700', color: 'rgba(58,36,24,0.8)' },
  footer: { position: 'absolute', left: 20, right: 20 },
  button: { height: 52, borderRadius: 26, backgroundColor: ROAST, alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 2px rgba(46,26,14,0.35), 0 10px 24px rgba(46,26,14,0.22)' },
  pressed: { transform: [{ scale: 0.97 }] },
  buttonText: { color: CREAM, fontSize: 17, fontWeight: '700' },
});
