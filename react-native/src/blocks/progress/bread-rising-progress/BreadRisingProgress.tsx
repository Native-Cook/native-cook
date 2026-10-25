import {
  BlurMask,
  Canvas,
  Circle,
  Fill,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { ACCENT, INK, MUTE, Progress, Stages, Status, Timer } from './BreadControls';
import { CHECK, GRAIN_SKSL, bowlBack, clothFolds, clothPath, bowlFoot, bowlFront, doughPath, earPath, glassStreaks, rimFront, scorePath, speckles, steamPath } from './breadArt';
import {
  BASE_Y,
  BOULE_CY,
  BOWL_RX,
  BOWL_RY,
  BUBBLES,
  BAKE_END,
  HALF,
  LOOP,
  PROOF_START,
  STOPS,
  breadAt,
  bubbleAt,
  doughPoints,
  doughTop,
  mixHex,
} from './breadMotion';

type Props = {
  /** Proofs, shapes and bakes the loaf, then starts over with raw dough, every 9 s. */
  autoPlay?: boolean;
};

/** Seconds after the last touch before the loop starts again. */
const IDLE = 2;
/** Finger travel (pt) that scrubs from raw dough to the baked loaf. */
const SCRUB = 320;
const RAW = ['#F8E6C8', '#EDCFA2', '#D6AE7C'];
const BAKED = ['#D99A52', '#B06A2E', '#7A431B'];

export function BreadRisingProgress({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const looping = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const W = size.width;
  const H = size.height;

  // Layout (pt). The hero sits between the timer and the stage control, on the counter.
  const heroTop = insets.top + 236;
  const controlsTop = H - insets.bottom - 150;
  const avail = controlsTop - heroTop - 12;
  const heroScale = Math.min(1.08, Math.max(0.6, avail / 330), (W - 24) / 350);
  const heroY = heroTop + 168 * heroScale;
  const counterY = heroY + (BOWL_RY + 8) * heroScale;
  const trackW = Math.max(0, W - 40);

  // The timeline. Auto-play runs it; a finger scrubs it.
  const tl = useSharedValue(0);
  const user = useSharedValue(0);
  const grabbing = useSharedValue(0);
  const returning = useSharedValue(0);
  const idle = useSharedValue(0);
  const startTl = useSharedValue(0);
  const lastStop = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (user.get() === 1) {
      if (grabbing.get() === 0 && returning.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE) {
          // Back to raw dough (the scrub runs backwards), then the loop restarts from t = 0.
          returning.set(1);
          tl.set(
            withTiming(PROOF_START, { duration: 900, easing: Easing.bezier(0.77, 0, 0.175, 1) }, (done) => {
              if (done) {
                tl.set(0);
                user.set(0);
                returning.set(0);
              }
            }),
          );
        }
      }
      return;
    }
    if (!looping) return;
    tl.set((tl.get() + dt) % LOOP);
  });

  const pose = useDerivedValue(() => breadAt(tl.get()));
  const prevTimer = useDerivedValue(() => breadAt(Math.max(0, tl.get() - 1 / 60)).timer);

  const dough = useDerivedValue(() => {
    const p = pose.get();
    return doughPath(doughPoints(p.rise, p.shape, new Array(HALF * 4)));
  });
  const top = useDerivedValue(() => doughTop(pose.get().rise, pose.get().shape));
  const doughColors = useDerivedValue(() => {
    const b = pose.get().bake;
    return [mixHex(RAW[0], BAKED[0], b), mixHex(RAW[1], BAKED[1], b), mixHex(RAW[2], BAKED[2], b)];
  });
  const doughStart = useDerivedValue(() => vec(0, top.get()));
  const doughEnd = useDerivedValue(() => vec(0, BOULE_CY + 24));
  const sheenC = useDerivedValue(() => vec(-38, top.get() + 30));
  const flourA = useDerivedValue(() => 0.55 * (1 - pose.get().bake));
  const blisterA = useDerivedValue(() => 0.5 * pose.get().bake);

  const bubbles = useDerivedValue(() => {
    const p = pose.get();
    const fill = Skia.PathBuilder.Make();
    const ring = Skia.PathBuilder.Make();
    for (let i = 0; i < BUBBLES; i += 1) {
      const b = bubbleAt(i, p.t, p.rise);
      if (b.r > 0.2) fill.addCircle(b.x, b.y, b.r);
      if (b.ringA > 0.02) ring.addCircle(b.x, b.y, b.ring);
    }
    return { fill: fill.build(), ring: ring.build() };
  });
  const bubbleFill = useDerivedValue(() => bubbles.get().fill);
  const bubbleRing = useDerivedValue(() => bubbles.get().ring);

  const scoreA = useDerivedValue(() => Math.min(1, Math.max(0, (pose.get().shape - 0.9) * 10)) * pose.get().stone);
  const score = useDerivedValue(() => scorePath(top.get(), pose.get().bake));
  const ear = useDerivedValue(() => earPath(top.get(), pose.get().bake));
  const steam = useDerivedValue(() => steamPath(pose.get().t, top.get()));
  const steamA = useDerivedValue(() => pose.get().steam);
  const bowlT = useDerivedValue(() => [{ translateY: pose.get().bowlY }]);
  const bowlA = useDerivedValue(() => pose.get().bowlA);
  const stoneA = useDerivedValue(() => pose.get().stone);
  const ovenA = useDerivedValue(() => pose.get().oven);
  const badgeT = useDerivedValue(() => [{ translateX: 104 }, { translateY: BOULE_CY - 100 }, { scale: Math.max(0.001, pose.get().ready) }]);
  const badgeA = useDerivedValue(() => Math.min(1, pose.get().ready * 2));
  const heroT = useMemo(() => [{ translateX: W / 2 }, { translateY: heroY }, { scale: heroScale }], [W, heroY, heroScale]);

  const art = useMemo(
    () => ({
      back: bowlBack(),
      front: bowlFront(),
      rim: rimFront(),
      streaks: glassStreaks(),
      foot: bowlFoot(),
      cloth: clothPath(),
      folds: clothFolds(),
      flour: speckles(3, 70, 0.6, 1.4),
      blisters: speckles(41, 90, 0.8, 2.2),
    }),
    [],
  );
  const grain = useMemo(() => Skia.RuntimeEffect.Make(GRAIN_SKSL), []);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  function stopIndex(t: number) {
    'worklet';
    let k = 0;
    for (let i = 0; i < STOPS.length; i += 1) if (t >= STOPS[i] - 0.01) k = i;
    return k;
  }

  // Drag up on the bowl to proof, shape and bake; drag down to go back. Let go: it settles on a stage.
  const pan = Gesture.Pan()
    .minDistance(4)
    .onStart(() => {
      if (returning.get() === 1) return;
      user.set(1);
      grabbing.set(1);
      idle.set(0);
      let t = tl.get();
      if (t > BAKE_END + 0.6 || t < PROOF_START) t = PROOF_START;
      if (t > BAKE_END) t = BAKE_END;
      tl.set(t);
      startTl.set(t);
      lastStop.set(stopIndex(t));
    })
    .onUpdate((e) => {
      if (grabbing.get() === 0) return;
      idle.set(0);
      const span = BAKE_END - PROOF_START;
      let t = startTl.get() - (e.translationY / SCRUB) * span;
      // Rubber band past raw dough and past the baked loaf.
      if (t < PROOF_START) t = PROOF_START - (PROOF_START - t) / (1 + (PROOF_START - t) * 4);
      if (t > BAKE_END) t = BAKE_END + (t - BAKE_END) / (1 + (t - BAKE_END) * 4);
      tl.set(Math.min(t, BAKE_END + 0.25));
      const k = stopIndex(t);
      if (k !== lastStop.get()) {
        lastStop.set(k);
        scheduleOnRN(feel);
      }
    })
    .onEnd((e) => {
      if (grabbing.get() === 0) return;
      const span = BAKE_END - PROOF_START;
      const v = (-e.velocityY / SCRUB) * span;
      const aim = tl.get() + v * 0.18;
      let best = STOPS[0];
      for (let i = 1; i < STOPS.length; i += 1) if (Math.abs(STOPS[i] - aim) < Math.abs(best - aim)) best = STOPS[i];
      tl.set(withSpring(best, { duration: 500, dampingRatio: 0.85, velocity: v }));
    })
    .onFinalize(() => {
      grabbing.set(0);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Proofing dough. Drag up to proof, shape and bake the loaf.">
          {W > 0 && grain ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <LinearGradient start={vec(0, 0)} end={vec(W * 0.35, H)} colors={['#F7F0E5', '#F0E5D3', '#E7D7BE']} />
              </Fill>
              <Rect x={0} y={counterY} width={W} height={H - counterY}>
                <LinearGradient start={vec(0, counterY)} end={vec(0, H)} colors={['#DCC8A8', '#D2BC98', '#C4AB84']} />
              </Rect>
              <Rect x={0} y={counterY} width={W} height={1.5} color="rgba(255,250,240,0.7)" />
              <Fill blendMode="softLight">
                <Shader source={grain} uniforms={{ amount: 0.45 }} />
              </Fill>

              <Group transform={heroT}>
                {/* The oven: a dark window with glowing elements, behind the loaf while it bakes. */}
                <Group opacity={ovenA}>
                  <RoundedRect x={-172} y={-170} width={344} height={BASE_Y + 196} r={30}>
                    <LinearGradient start={vec(0, -170)} end={vec(0, BASE_Y + 26)} colors={['#2B1810', '#3A2015', '#24140D']} />
                    <Shadow dx={0} dy={14} blur={30} color="rgba(60,30,10,0.35)" />
                  </RoundedRect>
                  <Circle cx={0} cy={-10} r={170}>
                    <RadialGradient c={vec(0, -10)} r={170} colors={['rgba(255,150,70,0.42)', 'rgba(255,110,40,0.14)', 'rgba(255,90,30,0)']} />
                  </Circle>
                  {[-150, -139].map((y) => (
                    <Group key={y}>
                      <RoundedRect x={-136} y={y} width={272} height={4} r={2} color="#FF7A2E">
                        <BlurMask blur={7} style="solid" />
                      </RoundedRect>
                      <RoundedRect x={-136} y={y + 1} width={272} height={2} r={1} color="#FFD3A0" />
                    </Group>
                  ))}
                  <RoundedRect x={-171} y={-169} width={342} height={BASE_Y + 194} r={29} style="stroke" strokeWidth={2} color="rgba(255,220,180,0.16)" />
                </Group>

                <Group opacity={bowlA}>
                  {/* A linen cloth under the bowl, its hem hanging over the counter's edge. */}
                  <Path path={art.cloth}>
                    <LinearGradient start={vec(0, BOWL_RY - 14)} end={vec(0, BOWL_RY + 62)} colors={['#F4EFE6', '#E8E0D2', '#D6CBB8']} />
                    <Shadow dx={0} dy={6} blur={10} color="rgba(80,55,25,0.25)" />
                  </Path>
                  <Group clip={art.cloth}>
                    <Rect x={-200} y={BOWL_RY + 38} width={400} height={4} color="rgba(84,118,148,0.6)" />
                    <Rect x={-200} y={BOWL_RY + 46} width={400} height={2} color="rgba(84,118,148,0.5)" />
                    <Path path={art.folds} style="stroke" strokeWidth={10} color="rgba(120,95,60,0.14)">
                      <BlurMask blur={6} style="normal" />
                    </Path>
                    <Fill blendMode="softLight">
                      <Shader source={grain} uniforms={{ amount: 0.28 }} />
                    </Fill>
                  </Group>
                  <Oval x={-112} y={BOWL_RY - 6} width={224} height={22} color="rgba(80,50,20,0.26)">
                    <BlurMask blur={12} style="normal" />
                  </Oval>
                  <Oval x={-58} y={BOWL_RY - 5} width={116} height={11} color="rgba(60,38,16,0.42)">
                    <BlurMask blur={4} style="normal" />
                  </Oval>
                </Group>
                <Group opacity={stoneA}>
                  <Oval x={-150} y={BASE_Y - 6} width={300} height={34}>
                    <LinearGradient start={vec(0, BASE_Y - 6)} end={vec(0, BASE_Y + 28)} colors={['#57504B', '#3B3532', '#2A2522']} />
                    <Shadow dx={0} dy={8} blur={16} color="rgba(40,24,10,0.35)" />
                  </Oval>
                  <Oval x={-120} y={BASE_Y + 2} width={240} height={16} color="rgba(20,10,4,0.45)">
                    <BlurMask blur={6} style="normal" />
                  </Oval>
                </Group>

                <Group transform={bowlT} opacity={bowlA}>
                  {/* The far wall of the glass, a touch tinted and darker, so the bowl has a body. */}
                  <Path path={art.front}>
                    <LinearGradient start={vec(0, 0)} end={vec(0, BOWL_RY)} colors={['rgba(96,140,132,0.22)', 'rgba(120,160,152,0.14)', 'rgba(70,110,104,0.26)']} />
                  </Path>
                  <Path path={art.back} style="stroke" strokeWidth={9} color="rgba(50,85,80,0.18)">
                    <BlurMask blur={5} style="normal" />
                  </Path>
                  <Path path={art.back} style="stroke" strokeWidth={2.4} color="rgba(255,255,255,0.7)" />
                  <Path path={art.back} style="stroke" strokeWidth={1} color="rgba(80,125,118,0.5)" />
                </Group>

                <Path path={dough}>
                  <LinearGradient start={doughStart} end={doughEnd} colors={doughColors} />
                </Path>
                <Group clip={dough}>
                  <Circle c={sheenC} r={96}>
                    <RadialGradient c={sheenC} r={96} colors={['rgba(255,250,238,0.75)', 'rgba(255,246,228,0.25)', 'rgba(255,246,228,0)']} />
                  </Circle>
                  <Path path={art.flour} color="#FFFDF8" opacity={flourA} />
                  <Path path={art.blisters} color="#4A250C" opacity={blisterA} />
                  <Path path={dough} style="stroke" strokeWidth={14} color="rgba(130,74,28,0.2)">
                    <BlurMask blur={8} style="normal" />
                  </Path>
                </Group>
                <Path path={bubbleFill} color="rgba(255,250,238,0.85)">
                  <Shadow dx={0} dy={0.6} blur={0.8} color="rgba(150,110,60,0.6)" />
                </Path>
                <Path path={bubbleRing} style="stroke" strokeWidth={1} color="rgba(170,130,80,0.55)" />
                <Group opacity={scoreA}>
                  <Path path={score}>
                    <LinearGradient start={vec(0, BOULE_CY - 90)} end={vec(0, BOULE_CY - 60)} colors={['#F6E2BC', '#E9C890', '#D8AE70']} />
                  </Path>
                  <Path path={ear} style="stroke" strokeWidth={3} strokeCap="round" color="rgba(255,230,180,0.75)">
                    <Shadow dx={0} dy={2} blur={2} color="rgba(70,30,8,0.45)" />
                  </Path>
                </Group>

                <Group transform={bowlT} opacity={bowlA}>
                  <Path path={art.front}>
                    <LinearGradient
                      start={vec(-BOWL_RX, 0)}
                      end={vec(BOWL_RX, 0)}
                      colors={['rgba(120,170,160,0.5)', 'rgba(210,236,230,0.16)', 'rgba(255,255,255,0.04)', 'rgba(200,230,224,0.14)', 'rgba(110,160,150,0.46)']}
                      positions={[0, 0.16, 0.5, 0.84, 1]}
                    />
                  </Path>
                  <Path path={art.streaks} style="stroke" strokeWidth={5} strokeCap="round" color="rgba(255,255,255,0.55)">
                    <BlurMask blur={2} style="normal" />
                  </Path>
                  <Path path={art.front} style="stroke" strokeWidth={2.2} color="rgba(70,115,108,0.55)" />
                  <Path path={art.front} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.75)" />
                  <Path path={art.rim} style="stroke" strokeWidth={6} color="rgba(150,195,186,0.45)" />
                  <Path path={art.rim} style="stroke" strokeWidth={3} color="rgba(255,255,255,0.9)" />
                  <Path path={art.rim} style="stroke" strokeWidth={1} color="rgba(60,105,98,0.55)" transform={[{ translateY: 3 }]} />
                  <Path path={art.foot} style="stroke" strokeWidth={2} color="rgba(255,255,255,0.6)" />
                </Group>

                <Group opacity={steamA}>
                  <Path path={steam} style="stroke" strokeWidth={12} strokeCap="round" strokeJoin="round" color="rgba(255,250,244,0.22)">
                    <BlurMask blur={9} style="normal" />
                  </Path>
                  <Path path={steam} style="stroke" strokeWidth={3} strokeCap="round" strokeJoin="round" color="rgba(255,250,244,0.18)">
                    <BlurMask blur={3} style="normal" />
                  </Path>
                </Group>

                <Group transform={badgeT} opacity={badgeA}>
                  <Circle cx={0} cy={0} r={20}>
                    <LinearGradient start={vec(0, -20)} end={vec(0, 20)} colors={['#6FAE6E', '#4E8F52', '#3D7442']} />
                    <Shadow dx={0} dy={4} blur={8} color="rgba(30,60,30,0.35)" />
                  </Circle>
                  <Path path={CHECK} style="stroke" strokeWidth={3} strokeCap="round" strokeJoin="round" color="#F4FBF2" />
                </Group>
              </Group>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <View style={styles.brand}>
          <View style={styles.mark} />
          <Text style={styles.brandName}>Crumb</Text>
        </View>
        <Text style={styles.batch}>Loaf 2 of 3</Text>
      </View>
      <View pointerEvents="none" style={[styles.titles, { top: insets.top + 56 }]}>
        <Text style={styles.title}>Country Sourdough</Text>
        <Text style={styles.sub}>78% hydration · 900 g · Dutch oven</Text>
      </View>
      <View pointerEvents="none" style={[styles.timerBlock, { top: insets.top + 124 }]}>
        <Timer pose={pose} prev={prevTimer} />
        <Status pose={pose} />
      </View>

      <View pointerEvents="none" style={[styles.controls, { top: controlsTop }]}>
        <Stages pose={pose} width={trackW} />
        <Progress pose={pose} width={trackW} />
        <View style={styles.captions}>
          <Text style={styles.caption}>Started 6:10 PM</Text>
          <Text style={styles.caption}>Out of the oven 8:55 PM</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#F0E5D3' },
  header: { position: 'absolute', left: 20, right: 20, height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  mark: { width: 18, height: 18, borderRadius: 9, backgroundColor: ACCENT, boxShadow: 'inset 0 -3px 4px rgba(80,30,5,0.35), 0 1px 2px rgba(80,40,10,0.3)' },
  brandName: { color: INK, fontSize: 17, fontWeight: '800', letterSpacing: -0.2 },
  batch: { color: MUTE, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  titles: { position: 'absolute', left: 20, right: 20, gap: 2 },
  title: { color: INK, fontSize: 30, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: MUTE, fontSize: 15, fontWeight: '500' },
  timerBlock: { position: 'absolute', left: 20, right: 20, gap: 2 },
  controls: { position: 'absolute', left: 20, right: 20, gap: 16 },
  captions: { flexDirection: 'row', justifyContent: 'space-between', marginTop: -6 },
  caption: { color: MUTE, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
