import {
  BlurMask,
  Canvas,
  Circle,
  Fill,
  Glyphs,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Rect,
  Shader,
  Shadow,
  Skia,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { GREEN, INK, Leaf, MUTE, PAGES, PageCopy, Petal, ButtonLabel, WateringCan } from './SeedParts';
import {
  GRAIN_SKSL,
  SOIL,
  budPath,
  canBody,
  canHandle,
  canRose,
  canSpout,
  leafPath,
  leafVeins,
  petalPath,
  potBody,
  potRim,
  seedHalf,
  soilCrumbs,
  stamens,
  stemPath,
  windowLight,
} from './seedArt';
import {
  DROPS,
  LEAVES,
  LOOP,
  PARK,
  PETALS,
  type Scene,
  TILT,
  clamp01,
  dropAt,
  easeOut,
  mixHex,
  pageOf,
  plantOf,
  sceneAt,
  spout,
  stemPoint,
} from './seedMotion';

type Props = {
  /** Waters the seed, grows it into a sprout and a flower, and lets it fall back, every 10 s. */
  autoPlay?: boolean;
};

/** Seconds after the last touch before the loop starts again. */
const IDLE = 2;
/** Growth per second of full pouring under the finger. */
const GROW_RATE = 0.32;
export function SeedSproutWelcome({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const looping = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const W = size.width;
  const H = size.height;

  // Layout (pt): the pot stands on a sill; the copy, dots and button sit under it.
  const shelfY = H - insets.bottom - 236;
  const s = Math.max(0.6, Math.min(1.1, (shelfY - insets.top - 64) / 400, (W - 32) / 330));
  const ox = W / 2 - 22 * s;
  const oy = shelfY - 112 * s;

  const clock = useSharedValue(0);
  const wt = useSharedValue(0);
  const user = useSharedValue(0);
  const grabbing = useSharedValue(0);
  const returning = useSharedValue(0);
  const idle = useSharedValue(0);
  const uG = useSharedValue(0);
  const uX = useSharedValue(PARK.x);
  const uY = useSharedValue(PARK.y);
  const uTilt = useSharedValue(0);
  const uTiltV = useSharedValue(0);
  const uWet = useSharedValue(0);
  const offX = useSharedValue(0);
  const offY = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    wt.set(wt.get() + dt);
    if (user.get() === 1) {
      // Over the pot the can tips and pours; the water grows the plant.
      const tip = spout(uX.get(), uY.get(), 0);
      const over = grabbing.get() === 1 && Math.abs(tip.x + 10) < 80 && tip.y < -40 && tip.y > -330;
      const target = over ? TILT : 0;
      const a = -140 * (uTilt.get() - target) - 16 * uTiltV.get();
      uTiltV.set(uTiltV.get() + a * dt);
      uTilt.set(uTilt.get() + uTiltV.get() * dt);
      const pour = clamp01((uTilt.get() / TILT - 0.6) / 0.4);
      if (returning.get() === 0) uG.set(Math.min(1, uG.get() + pour * GROW_RATE * dt));
      uWet.set(Math.max(pour, uWet.get() - dt * 0.25));
      if (grabbing.get() === 0 && returning.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE) {
          returning.set(1);
          uG.set(
            withTiming(0, { duration: 900, easing: Easing.bezier(0.77, 0, 0.175, 1) }, (done) => {
              if (done) {
                clock.set(0);
                user.set(0);
                returning.set(0);
              }
            }),
          );
        }
      }
      return;
    }
    if (looping) clock.set((clock.get() + dt) % LOOP);
  });

  const scene = useDerivedValue<Scene>(() => {
    if (user.get() === 0) return sceneAt(clock.get());
    const pour = clamp01((uTilt.get() / TILT - 0.6) / 0.4);
    return { g: uG.get(), cx: uX.get(), cy: uY.get(), tilt: uTilt.get(), pour, wet: uWet.get(), fall: 0, page: pageOf(uG.get()), t: wt.get() };
  });

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  // A tick when the finger's watering turns the page.
  useAnimatedReaction(
    () => (user.get() === 1 ? Math.round(scene.get().page) : -1),
    (now, before) => {
      if (before !== null && before >= 0 && now > before) scheduleOnRN(feel, 0);
    },
  );

  const sway = useDerivedValue(() => {
    const sc = scene.get();
    return 7 * Math.sin((2 * Math.PI * 2 * sc.t) / LOOP) * sc.g;
  });
  const plant = useDerivedValue(() => plantOf(scene.get().g));
  const stemUp = useDerivedValue(() => plant.get().stem);
  const bloom = useDerivedValue(() => plant.get().bloom);
  const stem = useDerivedValue(() => stemPath(Math.max(0.001, plant.get().stem), sway.get()));
  const stemA = useDerivedValue(() => (plant.get().stem > 0.004 ? 1 : 0));
  const seedT = useDerivedValue(() => {
    const open = clamp01(plant.get().stem * 8);
    return { l: [{ translateX: -2 - 5 * open }, { translateY: -3 }, { rotate: -0.25 - 0.7 * open }], r: [{ translateX: -2 + 5 * open }, { translateY: -3 }, { rotate: -0.25 + 0.5 * open }], a: 1 - clamp01((plant.get().stem - 0.1) * 6) };
  });
  const seedL = useDerivedValue(() => seedT.get().l);
  const seedR = useDerivedValue(() => seedT.get().r);
  const seedA = useDerivedValue(() => seedT.get().a);

  // The flower at the stem's tip: a bud that opens into seven petals, which fall at the end.
  const head = useDerivedValue(() => {
    const p = stemPoint(1, sway.get());
    return [{ translateX: p.x }, { translateY: p.y }];
  });
  const budT = useDerivedValue(() => {
    const st = plant.get().stem;
    const b = plant.get().bloom;
    const grow = easeOut(clamp01((st - 0.82) / 0.18));
    return [{ scale: Math.max(0.001, grow * (1 - 0.2 * b)) }];
  });
  const budA = useDerivedValue(() => 1 - clamp01((plant.get().bloom - 0.25) / 0.3));
  // Falling petals keep falling from where the flower was, not from the shrinking stem.
  const headFall = useDerivedValue(() => {
    if (scene.get().fall <= 0) return head.get();
    const p = stemPoint(1, 0);
    return [{ translateX: p.x }, { translateY: p.y }];
  });
  const discT = useDerivedValue(() => [{ scale: Math.max(0.001, easeOut(plant.get().bloom)) }]);
  const discA = useDerivedValue(() => 1 - clamp01(scene.get().fall * 3));

  const drops = useDerivedValue(() => {
    const sc = scene.get();
    const b = Skia.PathBuilder.Make();
    const tip = spout(sc.cx, sc.cy, sc.tilt);
    if (sc.pour > 0 || user.get() === 0) {
      for (let i = 0; i < DROPS; i += 1) {
        const d = dropAt(i, sc.t, tip.x, tip.y, sc.tilt);
        if (!d) continue;
        // In the loop a drop shows only if the can was pouring when it left the rose.
        const on = user.get() === 0 ? sceneAt(Math.max(0, sc.t - d.life)).pour : sc.pour;
        if (on > 0.05) b.addCircle(d.x, d.y, d.r * Math.min(1, on + 0.3));
      }
    }
    return b.build();
  });
  const canT = useDerivedValue(() => {
    const sc = scene.get();
    return [{ translateX: sc.cx }, { translateY: sc.cy }, { rotate: sc.tilt }];
  });
  const soilColors = useDerivedValue(() => {
    const w = scene.get().wet;
    return [mixHex('#6A4630', '#432817', w), mixHex('#4E321F', '#2F1C0E', w), mixHex('#3A2415', '#22140A', w)];
  });
  const wetA = useDerivedValue(() => scene.get().wet * 0.55);

  const art = useMemo(
    () => ({
      body: potBody(),
      rim: potRim(),
      crumbs: soilCrumbs(),
      seedTop: seedHalf(-1),
      seedBottom: seedHalf(1),
      leaf: leafPath(),
      veins: leafVeins(),
      petal: petalPath(),
      bud: budPath(),
      stamens: stamens(),
      canBody: canBody(),
      spout: canSpout(),
      rose: canRose(),
      handle: canHandle(),
      marker: Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(-17, -58, 34, 30), 4, 4)).build(),
    }),
    [],
  );
  const light = useMemo(() => windowLight(W, H), [W, H]);
  const markerFont = useMemo(() => matchFont({ fontFamily: process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif', fontSize: 10, fontStyle: 'italic', fontWeight: '700' }), []);
  const markerGlyphs = useMemo(() => {
    const ids = markerFont.getGlyphIDs('Cosmos');
    const ws = markerFont.getGlyphWidths(ids);
    const glyphs: { id: number; pos: ReturnType<typeof vec> }[] = [];
    let x = -ws.reduce((a, b) => a + b, 0) / 2;
    for (let k = 0; k < ids.length; k += 1) {
      glyphs.push({ id: ids[k], pos: vec(x, -40) });
      x += ws[k];
    }
    return glyphs;
  }, [markerFont]);
  const grain = useMemo(() => Skia.RuntimeEffect.Make(GRAIN_SKSL), []);
  const heroT = useMemo(() => [{ translateX: ox }, { translateY: oy }, { scale: s }], [ox, oy, s]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onStart((e) => {
      const sc = scene.get();
      const lx = (e.x - ox) / s;
      const ly = (e.y - oy) / s;
      if (returning.get() === 1 || Math.hypot(lx - sc.cx + 20, ly - sc.cy) > 90) return;
      if (user.get() === 0) {
        uG.set(sc.g);
        uWet.set(sc.wet);
        uTilt.set(sc.tilt);
        uTiltV.set(0);
      }
      uX.set(sc.cx);
      uY.set(sc.cy);
      offX.set(lx - sc.cx);
      offY.set(ly - sc.cy);
      user.set(1);
      grabbing.set(1);
      idle.set(0);
      scheduleOnRN(feel, 1);
    })
    .onUpdate((e) => {
      if (grabbing.get() === 0) return;
      idle.set(0);
      uX.set((e.x - ox) / s - offX.get());
      uY.set(Math.min(-90, (e.y - oy) / s - offY.get()));
    })
    .onEnd((e) => {
      if (grabbing.get() === 0) return;
      uX.set(withSpring(PARK.x, { duration: 500, dampingRatio: 0.8, velocity: e.velocityX / s }));
      uY.set(withSpring(PARK.y, { duration: 500, dampingRatio: 0.8, velocity: e.velocityY / s }));
    })
    .onFinalize(() => {
      grabbing.set(0);
    });

  const dot = useAnimatedStyle(() => ({ transform: [{ translateX: scene.get().page * 16 }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A seed in a pot. Drag the watering can over it to make it grow.">
          {W > 0 && grain ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <LinearGradient start={vec(0, 0)} end={vec(W * 0.4, H)} colors={['#EEF2E8', '#E0E8D8', '#CFDCC6']} />
              </Fill>
              <Path path={light} color="rgba(255,253,240,0.42)">
                <BlurMask blur={26} style="normal" />
              </Path>
              <Fill blendMode="softLight">
                <Shader source={grain} uniforms={{ amount: 0.42 }} />
              </Fill>
              <Rect x={0} y={shelfY} width={W} height={20}>
                <LinearGradient start={vec(0, shelfY)} end={vec(0, shelfY + 20)} colors={['#D9B48A', '#C79C6E', '#A97E52']} />
                <Shadow dx={0} dy={10} blur={18} color="rgba(60,50,30,0.22)" />
              </Rect>
              <Rect x={0} y={shelfY} width={W} height={1.5} color="rgba(255,244,224,0.8)" />

              <Group transform={heroT}>
                <Oval x={-86} y={102} width={172} height={20} color="rgba(50,40,20,0.3)">
                  <BlurMask blur={8} style="normal" />
                </Oval>
                <Path path={art.body}>
                  <LinearGradient start={vec(-70, 0)} end={vec(70, 0)} colors={['#9C4527', '#D07A4C', '#B45A33', '#8E3D21']} positions={[0, 0.32, 0.62, 1]} />
                </Path>
                <Path path={art.body} blendMode="softLight">
                  <Shader source={grain} uniforms={{ amount: 0.32 }} />
                </Path>
                <Rect x={-70} y={18} width={140} height={10} color="rgba(70,25,10,0.25)">
                  <BlurMask blur={4} style="normal" />
                </Rect>
                <Path path={art.rim}>
                  <LinearGradient start={vec(-80, 0)} end={vec(80, 0)} colors={['#A84E2B', '#DC8A5A', '#BE6439', '#953F22']} positions={[0, 0.3, 0.62, 1]} />
                  <Shadow dx={0} dy={2} blur={3} color="rgba(80,30,10,0.35)" />
                </Path>
                <Rect x={-76} y={-7} width={152} height={1.5} color="rgba(255,214,180,0.6)" />
                <Oval rect={SOIL}>
                  <LinearGradient start={vec(0, -9)} end={vec(0, 9)} colors={soilColors} />
                </Oval>
                <Group clip={SOIL}>
                  <Path path={art.crumbs} color="#7B5638" />
                  <Oval x={-50} y={-7} width={100} height={12} color="rgba(255,255,255,0.12)" opacity={wetA}>
                    <BlurMask blur={4} style="normal" />
                  </Oval>
                </Group>

                {/* A wooden plant marker, so even the bare pot reads as a promise. */}
                <Group transform={[{ translateX: -44 }, { translateY: -4 }, { rotate: -0.12 }]}>
                  <Rect x={-2.5} y={-30} width={5} height={36} color="#C9A574" />
                  <Path path={art.marker}>
                    <LinearGradient start={vec(-16, -58)} end={vec(16, -30)} colors={['#EBD3A8', '#D9BA86', '#C49F68']} />
                    <Shadow dx={0} dy={2} blur={3} color="rgba(70,45,20,0.3)" />
                  </Path>
                  <Glyphs font={markerFont} glyphs={markerGlyphs} color="#5B4325" />
                </Group>
                <Group opacity={seedA} transform={[{ scale: 1.6 }]}>
                  <Group transform={seedL}>
                    <Path path={art.seedTop} color="#7A5530" />
                  </Group>
                  <Group transform={seedR}>
                    <Path path={art.seedBottom} color="#5E3E20" />
                  </Group>
                </Group>

                <Group opacity={stemA}>
                  <Path path={stem} style="stroke" strokeWidth={6} strokeCap="round" strokeJoin="round" color="#4E7F3A" />
                  <Path path={stem} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color="rgba(190,230,150,0.55)" />
                </Group>
                {LEAVES.map((_, k) => (
                  <Leaf key={k} k={k} scene={scene} stemUp={stemUp} sway={sway} leaf={art.leaf} veins={art.veins} />
                ))}

                <Group transform={head}>
                  <Group transform={budT} opacity={budA}>
                    <Path path={art.bud}>
                      <LinearGradient start={vec(0, -26)} end={vec(0, 2)} colors={['#F08A72', '#B9785A', '#5C8A44']} />
                    </Path>
                  </Group>
                </Group>
                <Group transform={headFall}>
                  {Array.from({ length: PETALS }, (_, i) => (
                    <Petal key={i} i={i} scene={scene} bloom={bloom} petal={art.petal} />
                  ))}
                </Group>
                <Group transform={head} opacity={discA}>
                  <Group transform={discT}>
                    <Circle cx={0} cy={0} r={12}>
                      <RadialGradient c={vec(-2, -3)} r={14} colors={['#FFE08A', '#F2B33D', '#C9801C']} />
                    </Circle>
                    <Path path={art.stamens} color="#8A4F12" />
                  </Group>
                </Group>

                <Path path={drops} color="rgba(150,205,236,0.95)">
                  <Shadow dx={0} dy={0.5} blur={0.5} color="rgba(40,90,120,0.35)" />
                </Path>

                <Group transform={canT}>
                  <WateringCan body={art.canBody} spout={art.spout} rose={art.rose} handle={art.handle} />
                </Group>
              </Group>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <View style={styles.brand}>
          <View style={styles.mark} />
          <Text style={styles.brandName}>Greenroom</Text>
        </View>
        <Text style={styles.skip}>Skip</Text>
      </View>
      <View pointerEvents="none" style={[styles.copy, { top: shelfY + 36 }]}>
        {PAGES.map((_, i) => (
          <PageCopy key={i} scene={scene} i={i} />
        ))}
      </View>
      <View style={[styles.footer, { bottom: insets.bottom + 16 }]}>
        <View style={styles.dots}>
          {PAGES.map((_, i) => (
            <View key={i} style={styles.dot} />
          ))}
          <Animated.View style={[styles.dotOn, dot]} />
        </View>
        <Pressable style={({ pressed }) => [styles.button, pressed && styles.pressed]} accessibilityRole="button">
          <ButtonLabel scene={scene} last={false} label="Continue" />
          <ButtonLabel scene={scene} last label="Get started" />
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#E0E8D8' },
  header: { position: 'absolute', left: 24, right: 24, height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  mark: { width: 16, height: 16, borderTopLeftRadius: 16, borderBottomRightRadius: 16, backgroundColor: GREEN, transform: [{ rotate: '-45deg' }] },
  brandName: { color: INK, fontSize: 17, fontWeight: '800', letterSpacing: -0.2 },
  skip: { color: MUTE, fontSize: 15, fontWeight: '600' },
  copy: { position: 'absolute', left: 24, right: 24, height: 96 },
  footer: { position: 'absolute', left: 24, right: 24, gap: 20 },
  dots: { flexDirection: 'row', gap: 8, height: 8 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(35,51,31,0.18)' },
  dotOn: { position: 'absolute', left: 0, top: 0, width: 8, height: 8, borderRadius: 4, backgroundColor: GREEN },
  button: { height: 52, borderRadius: 26, backgroundColor: INK, alignItems: 'center', justifyContent: 'center', overflow: 'hidden', boxShadow: '0 1px 2px rgba(20,30,15,0.35), 0 10px 22px rgba(20,30,15,0.2)' },
  pressed: { transform: [{ scale: 0.97 }] },
});
