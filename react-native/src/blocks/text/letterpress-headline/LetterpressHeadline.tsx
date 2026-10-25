import {
  BlurMask,
  Canvas,
  Group,
  Image,
  ImageShader,
  LinearGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Text as SkiaText,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakePaper, buildMask, headlineFont, type Layout, makeLayout, SECOND, secondFont } from './letterpressArt';
import {
  easeInOut,
  easeOut,
  LIGHT_AZ,
  LIGHT_EL,
  LOOP,
  loopPose,
  MAX_GLYPHS,
  type Pose,
  printedPose,
  rubber,
} from './letterpressMotion';
import { PRESS_SKSL } from './letterpressShader';
import { TypeBlock } from './LetterpressTypeBlock';

/*
 Letterpress Headline — a print studio's new edition, "Press & Paper". On thick cotton paper the
 headline "Made by hand." is printed one letter at a time: a metal type block (its face bearing
 the mirrored letter) comes down, its shadow tightening, presses the letter into the paper in
 vermilion ink and lifts away; then an ink roller sweeps the second line on, and the lamp swings
 so the debossed walls catch the light. The loop is in letterpressMotion.ts (LOOP = 9 s).

 Touch: press and hold the paper and the platen presses every letter at once: the blocks come
 down in 0.25 s, then the impression deepens with the hold (full at about 1.1 s) and stays when
 you let go. Dragging swings the lamp (sideways = round, up and down = higher and lower,
 rubber-banded, sprung back on release). 2 s after the last touch the impression fades (0.9 s)
 and the loop starts again. Reduced motion: the printed page, still.
 */

const INK = '#2b2622';
const MUTE = '#7b6f62';
const VERMILION = '#b4442f';
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const IDLE = 2;
const RESET = 0.9;
// Holding: the blocks reach the paper after BLOCK_DOWN s; the impression is full HOLD_FULL s later.
const BLOCK_DOWN = 0.25;
const HOLD_FULL = 0.9;

const SPECS = [
  ['Paper', '600 gsm cotton, deckled'],
  ['Ink', 'Vermilion 032, oil based'],
  ['Press', 'Albion No. 3, 1904'],
];

const pressEffect = Skia.RuntimeEffect.Make(PRESS_SKSL);

type Props = {
  /** Prints, inks, lights and clears the headline by itself, on a loop of LOOP s. */
  autoPlay?: boolean;
};

function haptic(kind: 'light' | 'select' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function LetterpressHeadline({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  const layout = useMemo(
    () => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  return (
    <View style={styles.root} onLayout={onLayout}>
      {layout ? <Scene layout={layout} autoPlay={autoPlay && !reduced} reduced={reduced} /> : null}
    </View>
  );
}

function Scene({ layout, autoPlay, reduced }: { layout: Layout; autoPlay: boolean; reduced: boolean }) {
  const scale = Math.min(PixelRatio.get(), 3);
  const paper = useMemo(() => bakePaper(layout, Math.min(scale, 2)), [layout, scale]);
  const mask = useMemo(() => buildMask(layout, scale), [layout, scale]);
  const font = useMemo(() => headlineFont(layout.size), [layout.size]);
  const sfont = useMemo(() => secondFont(layout.second.size), [layout.second.size]);
  const { region, glyphs, second } = layout;
  const count = Math.min(glyphs.length, MAX_GLYPHS);
  const boxes = useMemo(() => {
    const b = layout.boxes.map((v) => [...v]);
    while (b.length < MAX_GLYPHS) b.push([-1000, -1000, -999, -999]);
    return b;
  }, [layout.boxes]);
  const maskW = Math.ceil(region.w * scale);
  const maskH = Math.ceil(region.h * scale);

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const user = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const resetAt = useSharedValue(-1);
  const holding = useSharedValue(0);
  const holdStart = useSharedValue(0);
  const contact = useSharedValue(0);
  const grab = useSharedValue([0, 0]);
  // The finger's pose.
  const uBase = useSharedValue<number[]>(new Array(MAX_GLYPHS).fill(0));
  const uFade = useSharedValue(1);
  const uPlaten = useSharedValue(0);
  const uBlock = useSharedValue(0);
  const uInk = useSharedValue(0);
  const uInkAlpha = useSharedValue(1);
  const uAz = useSharedValue(0);
  const uEl = useSharedValue(0);
  const [reserved, setReserved] = useState(false);

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    resetAt.set(-1);
    if (user.get() === 1) return;
    const p = loopPose(clock.get(), count);
    uBase.set(p.depth.slice());
    uFade.set(1);
    uPlaten.set(0);
    uBlock.set(0);
    uInk.set(p.ink);
    uInkAlpha.set(p.inkAlpha);
    uAz.set(p.light - LIGHT_AZ);
    uEl.set(p.elevation - LIGHT_EL);
    user.set(1);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const t = now.get() + dt;
    now.set(t);
    if (user.get() === 0) {
      if (autoPlay) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (reduced) return;
    if (holding.get() === 1) {
      const held = t - holdStart.get();
      uBlock.set(easeInOut(Math.min(1, held / BLOCK_DOWN)));
      if (held >= BLOCK_DOWN) {
        uPlaten.set(Math.max(uPlaten.get(), easeOut((held - BLOCK_DOWN) / HOLD_FULL)));
        if (contact.get() === 0) {
          contact.set(1);
          scheduleOnRN(haptic, 'light');
        } else if (contact.get() === 1 && held >= BLOCK_DOWN + HOLD_FULL) {
          contact.set(2);
          scheduleOnRN(haptic, 'select');
        }
      }
      return;
    }
    if (resetAt.get() < 0 && t - lastTouch.get() > IDLE) {
      const timing = { duration: RESET * 1000, easing: EASE_IN_OUT };
      uFade.set(withTiming(0, timing));
      uPlaten.set(withTiming(0, timing));
      uInkAlpha.set(withTiming(0, timing));
      uAz.set(withTiming(0, timing));
      uEl.set(withTiming(0, timing));
      resetAt.set(t + RESET + 0.05);
    } else if (resetAt.get() > 0 && t >= resetAt.get()) {
      resetAt.set(-1);
      clock.set(0);
      user.set(0);
    }
  });

  const press = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      touched();
      holding.set(1);
      holdStart.set(now.get());
      contact.set(0);
      grab.set([uAz.get(), uEl.get()]);
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      const g = grab.get();
      uAz.set(g[0] + (rubber(e.translationX, 160) / 160) * 1.3);
      uEl.set(Math.max(-0.4, Math.min(0.3, g[1] - rubber(e.translationY, 200) / 400)));
    })
    .onFinalize((e) => {
      holding.set(0);
      lastTouch.set(now.get());
      uBlock.set(withTiming(0, { duration: 260, easing: EASE_OUT }));
      uAz.set(withSpring(0, { duration: 700, dampingRatio: 0.7, velocity: (e.velocityX / 160) * 0.5 }));
      uEl.set(withSpring(0, { duration: 700, dampingRatio: 0.8 }));
    });

  const pose = useDerivedValue<Pose>(() => {
    if (reduced) return printedPose(count);
    if (user.get() === 0) return loopPose(clock.get(), count);
    const base = uBase.get();
    const fade = uFade.get();
    const platen = uPlaten.get();
    const block = uBlock.get();
    const depth: number[] = [];
    const blocks: number[] = [];
    const falling: boolean[] = [];
    for (let i = 0; i < MAX_GLYPHS; i += 1) {
      depth.push(i < count ? Math.max(base[i] * fade, platen) : 0);
      blocks.push(i < count ? block : 0);
      falling.push(holding.get() === 1);
    }
    return {
      depth,
      block: blocks,
      falling,
      roller: 0,
      rollerAlpha: 0,
      ink: uInk.get(),
      inkAlpha: uInkAlpha.get() * fade,
      light: LIGHT_AZ + uAz.get(),
      elevation: LIGHT_EL + uEl.get(),
    };
  });

  useAnimatedReaction(
    () => user.get() === 0,
    (looping, before) => {
      if (looping && before === false) scheduleOnRN(setReserved, false);
    },
  );

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    return {
      origin: [region.x, region.y],
      scale,
      boxes,
      depth: p.depth,
      count,
      split: layout.split,
      light: p.light,
      elevation: p.elevation,
    };
  });

  // The second line, inked up to the roller.
  const rollFrom = second.x - 22;
  const rollTo = second.x + second.width + 22;
  const lineTop = second.baselines[0] - second.size - 10;
  const lineBottom = second.baselines[1] + 10;
  const inkClip = useDerivedValue(() => Skia.XYWHRect(0, lineTop, rollFrom + (rollTo - rollFrom) * pose.get().ink, lineBottom - lineTop));
  const inkAlpha = useDerivedValue(() => pose.get().inkAlpha * 0.92);
  const rollerT = useDerivedValue(() => [{ translateX: rollFrom + (rollTo - rollFrom) * pose.get().roller }]);
  const rollerAlpha = useDerivedValue(() => pose.get().rollerAlpha);
  const rollH = lineBottom - lineTop + 8;

  return (
    <>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {paper ? <Image image={paper} x={0} y={0} width={layout.width} height={layout.height} fit="fill" /> : null}
        {mask && pressEffect ? (
          <Rect x={region.x} y={region.y} width={region.w} height={region.h}>
            <Shader source={pressEffect} uniforms={uniforms}>
              <ImageShader image={mask} fit="none" rect={{ x: 0, y: 0, width: maskW, height: maskH }} />
            </Shader>
          </Rect>
        ) : null}

        <Group clip={inkClip} opacity={inkAlpha}>
          {SECOND.map((line, i) => (
            <SkiaText key={line} x={second.x} y={second.baselines[i]} text={line} font={sfont} color="#2a3140" />
          ))}
        </Group>

        {/* The roller: a rubber cylinder on a steel frame with a wooden grip. */}
        <Group transform={rollerT} opacity={rollerAlpha}>
          <RoundedRect x={-11} y={lineTop + 4} width={26} height={rollH} r={13} color="rgba(30,20,10,0.35)">
            <BlurMask blur={6} style="normal" />
          </RoundedRect>
          <Rect x={-1.5} y={lineTop - 46} width={3} height={50} color="#8c9097" />
          <RoundedRect x={-6} y={lineTop - 86} width={12} height={44} r={6}>
            <LinearGradient start={vec(-6, 0)} end={vec(6, 0)} colors={['#6b4426', '#a8754a', '#5a3a21']} />
          </RoundedRect>
          <RoundedRect x={-13} y={lineTop - 4} width={26} height={rollH} r={13}>
            <LinearGradient start={vec(-13, 0)} end={vec(13, 0)} colors={['#17181c', '#3f434c', '#2a3140', '#121316']} positions={[0, 0.35, 0.6, 1]} />
          </RoundedRect>
          <RoundedRect x={-7} y={lineTop} width={3} height={rollH - 8} r={1.5} color="rgba(255,255,255,0.18)" />
        </Group>

        {glyphs.slice(0, count).map((g, i) => (
          <TypeBlock key={`${g.ch}${i}`} index={i} glyph={g} size={layout.size} font={font} pose={pose} />
        ))}
      </Canvas>

      <GestureDetector gesture={press}>
        <View
          style={[styles.pressArea, { height: layout.detailsTop - 8 }]}
          accessible
          accessibilityLabel="Paper. Press and hold to print the headline; drag to move the light."
        />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.chrome, { top: layout.chromeTop }]}>
        <Text style={styles.brand}>Press & Paper</Text>
        <View style={styles.pill}>
          <Text style={styles.pillText}>Edition 14</Text>
        </View>
      </View>
      <Text pointerEvents="none" style={[styles.kicker, { top: layout.kickerTop }]}>
        NEW EDITION · AUTUMN 2027
      </Text>

      <View pointerEvents="none" style={[styles.specs, { top: layout.specsTop }]}>
        {SPECS.map(([k, v]) => (
          <View key={k} style={styles.spec}>
            <Text style={styles.specKey}>{k}</Text>
            <Text style={styles.specValue}>{v}</Text>
          </View>
        ))}
      </View>

      <View pointerEvents="none" style={[styles.details, { top: layout.detailsTop }]}>
        <Text style={styles.detail}>12 prints · 300 × 400 mm</Text>
        <Text style={styles.price}>$180</Text>
      </View>
      <Pressable
        onPress={() => {
          setReserved(true);
          haptic('success');
        }}
        style={({ pressed }) => [styles.button, { top: layout.buttonTop, transform: [{ scale: pressed ? 0.97 : 1 }] }]}
        accessibilityRole="button"
      >
        <Text style={styles.buttonText}>{reserved ? 'Print no. 7 reserved' : 'Print edition'}</Text>
      </Pressable>
      <Text pointerEvents="none" style={[styles.hint, { top: layout.hintTop }]}>
        Hold the paper to print · drag to move the light
      </Text>
    </>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#efe8dc' },
  pressArea: { position: 'absolute', left: 0, right: 0, top: 0 },
  chrome: { position: 'absolute', left: 28, right: 28, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { color: INK, fontSize: 17, fontWeight: '700', letterSpacing: -0.2 },
  pill: {
    height: 26,
    paddingHorizontal: 10,
    borderRadius: 13,
    justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(43,38,34,0.35)',
  },
  pillText: { color: INK, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  kicker: { position: 'absolute', left: 28, color: VERMILION, fontSize: 11, fontWeight: '700', letterSpacing: 2.2, fontVariant: ['tabular-nums'] },
  specs: { position: 'absolute', left: 28, right: 28, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(43,38,34,0.25)' },
  spec: {
    height: 38,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(43,38,34,0.25)',
  },
  specKey: { color: MUTE, fontSize: 13, fontWeight: '600', letterSpacing: 0.4 },
  specValue: { color: INK, fontSize: 14, fontWeight: '500' },
  details: { position: 'absolute', left: 28, right: 28, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  detail: { color: MUTE, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  price: { color: INK, fontSize: 18, fontWeight: '700', fontVariant: ['tabular-nums'] },
  button: {
    position: 'absolute',
    left: 28,
    right: 28,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#2b2622',
    boxShadow: '0 1px 2px rgba(43,30,20,0.3), 0 10px 24px rgba(43,30,20,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  buttonText: { color: '#f6efe3', fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: MUTE, fontSize: 12, fontWeight: '500', opacity: 0.8 },
});
