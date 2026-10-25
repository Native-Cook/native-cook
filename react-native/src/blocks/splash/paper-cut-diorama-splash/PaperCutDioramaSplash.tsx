import { Canvas, Circle, Group, Image, RadialGradient, Skia, type SkImage, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';

import { bakeBacking, bakeDiorama, DEPTH, type LayerArt, LAYERS, layoutDiorama } from './dioramaArt';
import { LOOP, REBUILD_AT, REST_SCENE, rubber, type ScenePose, scenePose } from './dioramaMotion';

/*
 Paper Cut Diorama Splash — the launch screen of a storytelling app ("Fable"). Six layers of cut
 paper (dusk sky with a paper moon, far hills, pines, a meadow with a river, a grass edge, and a
 cream front frame with an arched window) form a diorama lit from behind the logo and wordmark cut
 out of the frame; the scene sways in parallax, the camera flies through the logo's little door
 into the home screen, and the home screen recedes while the diorama rises back from below as one
 piece. Every layer is baked once into an image (dioramaArt.ts); per frame only their transforms
 change. Timeline in dioramaMotion.ts (LOOP = 9 s).

 Touch: drag anywhere to tilt the diorama: each layer slides by its depth (the front frame and
 the sky in opposite directions, ±18 pt at full tilt, rubber-banded) and springs back level on
 release (0.6 s, damping ratio 0.55, carrying the finger's speed). A touch during any other phase
 brings the scene to the lit diorama first (0.7 s, ease-in-out: from home it flies back out
 through the door, during the rebuild it finishes rising). 2 s after the last touch the tilt
 levels (0.4 s) and the loop restarts from its rest pose.
 Reduced motion: the lit diorama, still; tilting still works.
 */

type Props = {
  /** Plays the launch: layers rise, the logo lights, the scene sways, the camera flies into home. */
  autoPlay?: boolean;
};

const HOLD_AFTER_TOUCH = 2;
const TILT = 18;
const INK = '#2A2230';
const MUTE = 'rgba(42,34,48,0.58)';

/** One paper layer under its own transform. */
function Layer({ i, art, pose, tilt, ex, ey }: { i: number; art: LayerArt; pose: SharedValue<ScenePose>; tilt: SharedValue<number[]>; ex: number; ey: number }) {
  const transform = useDerivedValue(() => {
    const l = pose.get().layers;
    const tl = tilt.get();
    const k = (DEPTH[i] - 0.66) * 2.2;
    return [
      { translateX: ex + l[i * 4 + 1] + tl[0] * TILT * k },
      { translateY: ey + l[i * 4] + l[i * 4 + 2] + tl[1] * TILT * 0.5 * k },
      { scale: l[i * 4 + 3] },
      { translateX: -ex },
      { translateY: -ey },
    ];
  });
  return (
    <Group transform={transform}>
      <Image image={art.image} x={art.x} y={art.y} width={art.w} height={art.h} fit="fill" />
    </Group>
  );
}

const SHELF = [
  { title: 'The Moth Who Kept Time', meta: '8 min · Chapter 2 of 5', color: '#5B4F86' },
  { title: 'Seven Paper Boats', meta: '10 min · New tonight', color: '#3F6470' },
  { title: "Grandpa's Kite", meta: '6 min · Finished twice', color: '#F0AE88' },
  { title: 'Under the Willow Bridge', meta: '14 min · Saved for Sunday', color: '#7E9C68' },
];

const TABS = [
  { label: 'Tonight', d: 'M12 3a7 7 0 1 0 7.5 9.5A6 6 0 0 1 12 3z' },
  { label: 'Library', d: 'M4 4h4v16H4zM10 4h4v16h-4zM16.5 4.6l3.6-.9 3.2 15.5-3.6.9z' },
  { label: 'Profile', d: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 8a7 7 0 0 1 14 0z' },
];

function Home({ top, bottom }: { top: number; bottom: number }) {
  return (
    <View style={[styles.home, { paddingTop: top + 16 }]}>
      <Text style={styles.homeKicker}>FABLE</Text>
      <Text style={styles.homeTitle}>Good evening, Mira</Text>
      <Text style={styles.homeSub}>Tonight&apos;s story is ready · 12 min</Text>
      <LinearGradient colors={['#26315A', '#7A6496', '#F0AE88']} locations={[0, 0.55, 1]} style={styles.card}>
        <Svg style={StyleSheet.absoluteFill} viewBox="0 0 345 220" preserveAspectRatio="none">
          <SvgPath d="M0 150 C60 120 110 140 170 128 C230 116 290 136 345 120 L345 220 L0 220 Z" fill="#7F74A8" opacity={0.9} />
          <SvgPath d="M0 175 C70 160 120 178 190 166 C250 156 300 172 345 160 L345 220 L0 220 Z" fill="#34545F" />
          <SvgPath d="M0 200 C80 188 150 204 220 194 C280 186 320 198 345 192 L345 220 L0 220 Z" fill="#2F5137" />
        </Svg>
        <View style={styles.moon} />
        <View style={styles.cardText}>
          <Text style={styles.cardTitle}>The Lantern Fox</Text>
          <Text style={styles.cardMeta}>Chapter 3 · read by Juno Vale</Text>
        </View>
        <View style={styles.cardPill}>
          <Text style={styles.cardPillText}>Continue</Text>
        </View>
      </LinearGradient>
      <Text style={styles.section}>Your shelf</Text>
      {SHELF.map((s) => (
        <View key={s.title} style={styles.row}>
          <View style={[styles.tile, { backgroundColor: s.color }]} />
          <View style={styles.rowText}>
            <Text style={styles.rowTitle}>{s.title}</Text>
            <Text style={styles.rowMeta}>{s.meta}</Text>
          </View>
        </View>
      ))}
      <View style={[styles.tabs, { paddingBottom: bottom + 6 }]}>
        {TABS.map((t, k) => (
          <View key={t.label} style={styles.tab}>
            <Svg width={22} height={22} viewBox="0 0 24 24">
              <SvgPath d={t.d} fill={k === 0 ? '#B0663E' : 'rgba(42,34,48,0.45)'} />
            </Svg>
            <Text style={[styles.tabLabel, k === 0 && { color: '#B0663E' }]}>{t.label}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

export function PaperCutDioramaSplash({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const L = useMemo(() => (size ? layoutDiorama(size.w, size.h, insets.top) : null), [size, insets.top]);
  const scale = Math.min(2, PixelRatio.get());
  const art = useMemo(() => (L ? bakeDiorama(L, scale) : null), [L, scale]);
  const backing = useMemo<SkImage | null>(() => (L ? bakeBacking(L, scale) : null), [L, scale]);

  const ex = L ? L.emblem.x + L.emblem.w / 2 : 0;
  const ey = L ? L.emblem.y + L.emblem.h * 0.62 : 0;
  const riseDist = (L?.H ?? 800) + 60;
  // The lamp sits between the door and the wordmark, and lights only the frame below the window.
  const gy = L ? (ey + L.wordY) / 2 : 0;
  const glowClip = useMemo(() => (L ? Skia.XYWHRect(-40, L.win.bottom + 2, L.W + 80, L.H) : Skia.XYWHRect(0, 0, 0, 0)), [L]);

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  /** 0 = autoPlay, 1 = the finger's, 2 = fading back to paper. */
  const mode = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const sceneT = useSharedValue(0);
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
    } else if (m === 1 && touching.get() === 0 && now.get() - lastTouch.get() > HOLD_AFTER_TOUCH) {
      // The scene is already at rest; level the tilt and hand back to the loop at t = 0.
      mode.set(2);
      tiltY.set(withTiming(0, { duration: 400 }));
      tiltX.set(
        withTiming(0, { duration: 400 }, (done) => {
          if (done) {
            clock.set(0);
            mode.set(0);
          }
        }),
      );
    }
  });

  const pose = useDerivedValue(() => scenePose(mode.get() === 0 ? (reduced ? REST_SCENE : clock.get()) : sceneT.get(), riseDist));
  const tilt = useDerivedValue(() => [tiltX.get(), tiltY.get()]);
  const frameXf = useDerivedValue(() => {
    const l = pose.get().layers;
    const i = LAYERS - 1;
    const k = (DEPTH[i] - 0.66) * 2.2;
    return [
      { translateX: ex + l[i * 4 + 1] + tiltX.get() * TILT * k },
      { translateY: ey + l[i * 4] + l[i * 4 + 2] + tiltY.get() * TILT * 0.5 * k },
      { scale: l[i * 4 + 3] },
      { translateX: -ex },
      { translateY: -ey },
    ];
  });
  const visible = useDerivedValue(() => pose.get().visible);
  const light = useDerivedValue(() => Math.min(1, pose.get().light));
  const glowR = useDerivedValue(() => 130 + 60 * Math.min(1, Math.max(0, pose.get().light - 1)));

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      touching.set(1);
      lastTouch.set(now.get());
      if (mode.get() !== 1) {
        const from = mode.get() === 0 ? clock.get() : sceneT.get();
        // Play the scene to the lit diorama: back out through the door from home, or on through
        // the rebuild, whichever way the rest pose is nearer in the story.
        sceneT.set(from);
        mode.set(1);
        sceneT.set(withTiming(from >= REBUILD_AT ? LOOP : REST_SCENE, { duration: 700, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
      }
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      tiltX.set(rubber(e.translationX / 130));
      tiltY.set(rubber(e.translationY / 130));
    })
    .onFinalize((e) => {
      touching.set(0);
      lastTouch.set(now.get());
      tiltX.set(withSpring(0, { duration: 600, dampingRatio: 0.55, velocity: e.velocityX / 130 }));
      tiltY.set(withSpring(0, { duration: 600, dampingRatio: 0.55, velocity: e.velocityY / 130 }));
    });

  const homeStyle = useAnimatedStyle(() => ({ opacity: pose.get().home, transform: [{ scale: pose.get().homeScale }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Fable. A paper-cut diorama of a river valley at dusk. Drag to tilt it.">
          {L && art && backing ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Image image={backing} x={0} y={0} width={L.W} height={L.H} />
              <Group opacity={visible}>
                {art.layers.slice(0, LAYERS - 1).map((a, i) => (
                  <Layer key={i} i={i} art={a} pose={pose} tilt={tilt} ex={ex} ey={ey} />
                ))}
                {/* The lamp behind the front frame: seen only through its cut-outs. */}
                <Group transform={frameXf} opacity={light}>
                  <Group clip={glowClip}>
                    <Circle cx={ex} cy={gy} r={glowR}>
                      <RadialGradient c={vec(ex, gy)} r={glowR} colors={['#FFF1D2', '#FFC274', '#F08A3E', 'rgba(232,120,60,0)']} positions={[0, 0.22, 0.6, 1]} />
                    </Circle>
                  </Group>
                </Group>
                <Layer i={LAYERS - 1} art={art.layers[LAYERS - 1]} pose={pose} tilt={tilt} ex={ex} ey={ey} />
                <Group transform={frameXf} opacity={light}>
                  <Image image={art.bloom.image} x={art.bloom.x} y={art.bloom.y} width={art.bloom.w} height={art.bloom.h} fit="fill" blendMode="screen" />
                </Group>
              </Group>
            </Canvas>
          ) : null}
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, homeStyle]}>
            <Home top={insets.top} bottom={insets.bottom} />
          </Animated.View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#EFE6D6' },
  home: { flex: 1, backgroundColor: '#FBF2E2', paddingHorizontal: 24 },
  homeKicker: { color: '#B0663E', fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  homeTitle: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  homeSub: { color: MUTE, fontSize: 15, fontWeight: '500', marginTop: 2 },
  card: { height: 220, borderRadius: 24, borderCurve: 'continuous', marginTop: 20, overflow: 'hidden', boxShadow: '0 2px 4px rgba(40,30,50,0.18), 0 16px 32px rgba(40,30,50,0.18)' },
  moon: { position: 'absolute', right: 56, top: 40, width: 34, height: 34, borderRadius: 17, backgroundColor: '#FCE9C6', boxShadow: '0 0 18px rgba(255,230,190,0.7)' },
  cardText: { position: 'absolute', left: 20, top: 22, right: 120 },
  cardTitle: { color: '#FFF6E8', fontSize: 24, fontWeight: '700', letterSpacing: -0.4 },
  cardMeta: { color: 'rgba(255,246,232,0.75)', fontSize: 14, fontWeight: '500', marginTop: 4 },
  cardPill: { position: 'absolute', left: 20, bottom: 18, height: 36, paddingHorizontal: 16, borderRadius: 18, backgroundColor: '#FBF2E2', justifyContent: 'center' },
  cardPillText: { color: INK, fontSize: 15, fontWeight: '700' },
  section: { color: INK, fontSize: 20, fontWeight: '700', marginTop: 28, marginBottom: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 10 },
  tile: { width: 52, height: 52, borderRadius: 14, borderCurve: 'continuous', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25)' },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  rowMeta: { color: MUTE, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  tabs: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: 10,
    backgroundColor: '#F6EBD9',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(42,34,48,0.14)',
  },
  tab: { alignItems: 'center', gap: 3, minWidth: 64 },
  tabLabel: { color: 'rgba(42,34,48,0.5)', fontSize: 11, fontWeight: '600' },
});
