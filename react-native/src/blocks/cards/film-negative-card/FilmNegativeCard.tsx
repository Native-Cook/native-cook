import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  ImageShader,
  matchFont,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  SweepGradient,
  Text as SkText,
  useImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeDesk, layoutFilm } from './filmNegativeArt';
import { clamp01, filmPose, LOOP, REST_ON, rubber, smoothstep } from './filmNegativeMotion';
import { FILM_SKSL } from './filmNegativeShader';

/*
 Film Negative Card — a photo-lab screen ("Darkroom Club — Roll 12, frame 7") with a strip of
 35 mm colour negative lying on a dark desk above a light table. Drag the strip onto the light:
 where it overlaps the lit panel the negative inverts into the positive photo (one Skia shader,
 filmNegativeShader.ts), the light's soft edge sweeping across the frame; a loupe drifts over
 the face while it rests there. Timeline in filmNegativeMotion.ts (LOOP = 8 s).

 Touch: drag the strip anywhere (vertical follows the finger, rubber-banded past the desk and the
 table; sideways it rubber-bands back). Release: a spring (0.45 s, damping ratio 0.78, carrying
 the finger's velocity) to the light or back to the desk, whichever the flick points at. Landing
 on the light brings the loupe in. Haptics: a selection tick when the strip passes halfway, a
 light impact when it lands on the light. 2 s after the last touch it eases back to the desk
 (0.9 s) and the loop starts again.
 Reduced motion: the strip rests on the light showing the positive; no loop, no dragging.
 */

type Props = {
  /** Slides the negative onto the light table and back, with the loupe, on a loop. */
  autoPlay?: boolean;
};

const BG = '#16110F';
const INK = '#F4EADB';
const MUTE = 'rgba(244,234,219,0.58)';
const ACCENT = '#F2A65A';
const LIFT_SCALE = 0.018;
const HOLD_AFTER_TOUCH = 2;
const RETURN_MS = 900;
const ROLL_H = 16;

const EDGE_FONT = matchFont({
  fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }),
  fontSize: 7.5,
  fontWeight: '700',
});

function tick(kind: 'select' | 'land') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function FilmNegativeCard({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const photo = useImage(require('./assets/portrait.jpg'));
  const effect = useMemo(() => Skia.RuntimeEffect.Make(FILM_SKSL), []);
  const L = useMemo(() => (size ? layoutFilm(size.w, size.h, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const desk = useMemo(() => (L ? bakeDesk(L, Math.min(3, PixelRatio.get())) : null), [L]);

  // Plain numbers for the worklets (0 until the first layout).
  const restY = L?.restY ?? 0;
  const onY = L?.onY ?? 0;
  /** Where the strip rests: partly over the top of the light table. */
  const homeY = restY + REST_ON * (onY - restY);
  const sx = L?.stripX ?? 0;
  const sw = L?.stripW ?? 1;
  const sh = L?.stripH ?? 1;
  const F = L?.frame ?? { x: 0, y: 0, w: 1, h: 1 };
  const P = L?.panel ?? { x: 0, y: 0, w: 1, h: 1 };
  const perf = L?.perf ?? { pitch: 1, w: 0, h: 0, phase: 0, top: 0, bottom: 0 };
  const gap = L?.gap ?? 0;
  const imgW = photo?.width() ?? 900;
  const imgH = photo?.height() ?? 600;
  const p3 = Platform.OS === 'ios' ? 1 : 0;

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  /** 0 = autoPlay timeline, 1 = the finger's (or its spring's), 2 = easing back to rest. */
  const mode = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const manY = useSharedValue(0);
  const manX = useSharedValue(0);
  const manLift = useSharedValue(0);
  const manLoupe = useSharedValue(0);
  const grabY = useSharedValue(0);
  const grabX = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
    } else if (m === 1 && touching.get() === 0 && now.get() - lastTouch.get() > HOLD_AFTER_TOUCH) {
      mode.set(2);
      const ease = Easing.bezier(0.65, 0, 0.35, 1);
      manX.set(withTiming(0, { duration: RETURN_MS, easing: ease }));
      manLoupe.set(withTiming(0, { duration: 250 }));
      manY.set(
        withTiming(homeY, { duration: RETURN_MS, easing: ease }, (finished) => {
          if (finished) {
            clock.set(0);
            mode.set(0);
          }
        }),
      );
    }
  });

  /** [strip top, x offset, lift, loupe opacity, loupe dx, loupe dy] */
  const view = useDerivedValue(() => {
    if (mode.get() === 0) {
      const p = filmPose(reduced ? 3.02 : clock.get());
      return [restY + p.on * (onY - restY), 0, p.lift, p.loupe, p.loupeDx, p.loupeDy];
    }
    return [manY.get(), manX.get(), manLift.get(), manLoupe.get(), 0, 0];
  });

  /** How far the strip is onto the light, 0…1. */
  const onAmount = useDerivedValue(() => clamp01((view.get()[0] - restY) / Math.max(1, onY - restY)));

  const xf = useDerivedValue(() => {
    const v = view.get();
    const s = 1 + LIFT_SCALE * v[2];
    return [s, sx + v[1] + (sw / 2) * (1 - s), v[0] + (sh / 2) * (1 - s)];
  });

  const stripTransform = useDerivedValue(() => {
    const [s, tx, ty] = xf.get();
    return [{ translateX: tx }, { translateY: ty }, { scale: s }];
  });

  const loupeLocal = useDerivedValue(() => {
    const v = view.get();
    return [F.x + F.w * 0.66 + v[4], F.y + F.h * 0.4 + v[5], F.h * 0.3, v[3]];
  });

  const uniforms = useDerivedValue(() => {
    const [s, tx, ty] = xf.get();
    return {
      imgSize: [imgW, imgH],
      strip: [0, 0, sw, sh],
      frame: [F.x, F.y, F.w, F.h],
      gap,
      light: [(P.x - tx) / s, (P.y - ty) / s, P.w / s, P.h / s],
      loupe: loupeLocal.get(),
      perf: [perf.pitch, perf.w, perf.h, perf.phase],
      rows: [perf.top, perf.bottom],
      sheen: view.get()[0],
      p3,
      roll: 0,
    };
  });

  // The rest of the roll, lying dimmed on the desk behind the strip: same shader, no light.
  const rollUniforms = useMemo(
    () => ({
      imgSize: [imgW, imgH],
      strip: [0, 0, sw, sh],
      frame: [F.x, F.y, F.w, F.h],
      gap,
      light: [-9999, -9999, 1, 1],
      loupe: [0, 0, 1, 0],
      perf: [perf.pitch, perf.w, perf.h, perf.phase],
      rows: [perf.top, perf.bottom],
      sheen: restY - 80,
      p3,
      roll: 1,
    }),
    // Static per layout.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [L, imgW, imgH],
  );
  const rollTransform = useMemo(() => [{ translateX: sx + 6 }, { translateY: restY - 10 }, { rotate: -0.022 }, { scale: 0.95 }], [sx, restY]);

  const litClip = useDerivedValue(() => {
    const [s, tx, ty] = xf.get();
    return Skia.XYWHRect((P.x - tx) / s, (P.y - ty) / s, P.w / s, P.h / s);
  });

  // Shadows on the desk / table, in screen space: tight contact + soft ambient that grows with lift.
  const ambientRect = useDerivedValue(() => {
    const v = view.get();
    const grow = 6 * v[2];
    return Skia.RRectXY(Skia.XYWHRect(sx + v[1] - grow / 2, v[0] + 8 + 14 * v[2], sw + grow, sh), 8, 8);
  });
  const contactRect = useDerivedValue(() => {
    const v = view.get();
    return Skia.RRectXY(Skia.XYWHRect(sx + v[1] + 2, v[0] + 1.5 + 3 * v[2], sw - 4, sh), 5, 5);
  });
  const ambientOpacity = useDerivedValue(() => 0.5 - 0.18 * view.get()[2] - 0.2 * onAmount.get());
  const contactOpacity = useDerivedValue(() => 0.75 * (1 - clamp01(view.get()[2])));

  // The loupe's chrome ring, in screen space.
  const ring = useDerivedValue(() => {
    const [s, tx, ty] = xf.get();
    const l = loupeLocal.get();
    return [tx + l[0] * s, ty + l[1] * s, l[2] * s * (0.94 + 0.06 * l[3]), l[3]];
  });
  const ringC = useDerivedValue(() => ({ x: ring.get()[0], y: ring.get()[1] }));
  const ringCx = useDerivedValue(() => ring.get()[0]);
  const ringCy = useDerivedValue(() => ring.get()[1]);
  const ringShadowCy = useDerivedValue(() => ring.get()[1] + 7);
  const ringR = useDerivedValue(() => ring.get()[2] + 4);
  const ringInnerR = useDerivedValue(() => ring.get()[2] - 0.5);
  const ringOuterR = useDerivedValue(() => ring.get()[2] + 8.4);
  const ringOpacity = useDerivedValue(() => ring.get()[3]);

  // Haptic tick when the strip passes halfway under the finger.
  useAnimatedReaction(
    () => (mode.get() === 1 && touching.get() === 1 ? (onAmount.get() > (1 + REST_ON) / 2 ? 1 : 0) : -1),
    (cur, prev) => {
      if (prev !== null && prev !== -1 && cur !== -1 && cur !== prev) scheduleOnRN(tick, 'select');
    },
  );

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      const v = view.get();
      const hit = e.y > v[0] - 16 && e.y < v[0] + sh + 16;
      if (!hit) return;
      touching.set(1);
      lastTouch.set(now.get());
      cancelAnimation(manY);
      cancelAnimation(manX);
      if (mode.get() !== 1) {
        manY.set(v[0]);
        manX.set(v[1]);
        manLoupe.set(v[3]);
        manLift.set(v[2]);
        mode.set(1);
      }
      grabY.set(manY.get());
      grabX.set(manX.get());
      manLift.set(withSpring(1, { duration: 350, dampingRatio: 0.8 }));
      manLoupe.set(withTiming(0, { duration: 160 }));
    })
    .onUpdate((e) => {
      if (touching.get() === 0) return;
      lastTouch.set(now.get());
      manY.set(rubber(grabY.get() + e.translationY, restY - 4, onY + 4));
      manX.set(rubber(grabX.get() + e.translationX, 0, 0));
    })
    .onFinalize((e) => {
      if (touching.get() === 0) return;
      touching.set(0);
      lastTouch.set(now.get());
      const projected = manY.get() + e.velocityY * 0.12;
      const toLight = projected > (homeY + onY) / 2;
      manY.set(withSpring(toLight ? onY : homeY, { duration: 450, dampingRatio: 0.78, velocity: e.velocityY }));
      manX.set(withSpring(0, { duration: 400, dampingRatio: 0.8, velocity: e.velocityX }));
      manLift.set(withSpring(0, { duration: 450, dampingRatio: 0.85 }));
      if (toLight) {
        manLoupe.set(withDelay(280, withTiming(1, { duration: 320, easing: Easing.bezier(0.23, 1, 0.32, 1) })));
        scheduleOnRN(tick, 'land');
      }
    });

  const rollStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -ROLL_H * smoothstep(0.66, 0.86, onAmount.get()) }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const edgeLabels = useMemo(() => {
    const out: { x: number; y: number; t: string }[] = [];
    if (!L) return out;
    const yTop = perf.top - perf.h / 2 - 3.5;
    const yBot = perf.bottom + perf.h / 2 + 9;
    for (let i = -1; i <= 1; i += 1) {
      const x0 = F.x + i * (F.w + gap);
      out.push({ x: x0 + 6, y: yTop, t: 'LUMA 400' });
      out.push({ x: x0 + F.w * 0.64, y: yTop, t: `${7 + i}` });
      out.push({ x: x0 + F.w * 0.12, y: yBot, t: `${7 + i}` });
      out.push({ x: x0 + F.w * 0.58, y: yBot, t: `${7 + i}A` });
    }
    return out;
    // The labels only depend on the layout.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [L]);

  const rollLabels = useMemo(() => edgeLabels.map((l) => ({ ...l, t: l.t.replace(/^(\d+)/, (d) => `${Number(d) - 4}`) })), [edgeLabels]);

  return (
    <View style={[styles.root, { backgroundColor: BG }]} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A strip of colour negative film. Drag it onto the light table to see it as a positive photo."
        >
          {L && desk && effect && photo ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Image image={desk} x={0} y={0} width={L.W} height={L.H} />
              <Group transform={rollTransform} opacity={0.62}>
                <RoundedRect x={4} y={10} width={sw - 8} height={sh} r={6} color="rgba(0,0,0,0.5)">
                  <BlurMask blur={10} style="normal" />
                </RoundedRect>
                <Rect x={0} y={0} width={sw} height={sh}>
                  <Shader source={effect} uniforms={rollUniforms}>
                    <ImageShader image={photo} tx="clamp" ty="clamp" />
                  </Shader>
                </Rect>
                {rollLabels.map((l, i) => (
                  <SkText key={`r${i}`} x={l.x} y={l.y} text={l.t} font={EDGE_FONT} color="rgba(58,24,8,0.8)" />
                ))}
              </Group>
              <RoundedRect rect={ambientRect} color="black" opacity={ambientOpacity}>
                <BlurMask blur={14} style="normal" />
              </RoundedRect>
              <RoundedRect rect={contactRect} color="black" opacity={contactOpacity}>
                <BlurMask blur={2.5} style="normal" />
              </RoundedRect>
              <Group transform={stripTransform}>
                <Rect x={0} y={0} width={sw} height={sh}>
                  <Shader source={effect} uniforms={uniforms}>
                    <ImageShader image={photo} tx="clamp" ty="clamp" />
                  </Shader>
                </Rect>
                {edgeLabels.map((l, i) => (
                  <SkText key={`d${i}`} x={l.x} y={l.y} text={l.t} font={EDGE_FONT} color="rgba(58,24,8,0.8)" />
                ))}
                <Group clip={litClip}>
                  {edgeLabels.map((l, i) => (
                    <SkText key={`l${i}`} x={l.x} y={l.y} text={l.t} font={EDGE_FONT} color="rgba(255,226,182,0.82)" />
                  ))}
                </Group>
              </Group>
              <Group opacity={ringOpacity}>
                <Circle cx={ringCx} cy={ringShadowCy} r={ringR} color="rgba(0,0,0,0.42)">
                  <BlurMask blur={9} style="normal" />
                </Circle>
                <Circle cx={ringCx} cy={ringCy} r={ringR} style="stroke" strokeWidth={8}>
                  <SweepGradient c={ringC} colors={['#8C8782', '#F3F0EA', '#6A6662', '#D9D4CC', '#7D7873', '#EDE9E2', '#8C8782']} />
                </Circle>
                <Circle cx={ringCx} cy={ringCy} r={ringInnerR} style="stroke" strokeWidth={1.2} color="rgba(20,14,10,0.6)" />
                <Circle cx={ringCx} cy={ringCy} r={ringOuterR} style="stroke" strokeWidth={0.8} color="rgba(255,250,240,0.35)" />
              </Group>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>DARKROOM CLUB</Text>
        <Text style={styles.title}>Roll 12, frame 7</Text>
        <Text style={styles.sub}>LUMA 400 · 36 exposures · developed Oct 14</Text>
        <View style={styles.metaRow}>
          <View style={styles.chip}>
            <View style={styles.dot} />
            <View style={styles.rollClip}>
              <Animated.View style={rollStyle}>
                <Text style={styles.chipText}>NEGATIVE</Text>
                <Text style={styles.chipText}>POSITIVE</Text>
              </Animated.View>
            </View>
          </View>
          <Text style={styles.meta}>f/2.8 · 1/250 s · 50 mm</Text>
        </View>
      </View>

      {L ? (
        <View style={[styles.footer, { top: L.footerY }]}>
          <View style={styles.price}>
            <Text style={styles.priceLabel}>Lustre print, 4 × 6 in</Text>
            <Text style={styles.priceValue}>$0.45 · ready Thursday</Text>
          </View>
          <Pressable
            accessibilityRole="button"
            onPressIn={() => tick('select')}
            style={({ pressed }) => [styles.cta, { transform: [{ scale: pressed ? 0.97 : 1 }] }]}
          >
            <Text style={styles.ctaText}>Order print</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 2.2 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500', marginTop: 2 },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 24,
    paddingHorizontal: 10,
    borderRadius: 12,
    backgroundColor: 'rgba(242,166,90,0.12)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(242,166,90,0.4)',
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: ACCENT },
  rollClip: { height: ROLL_H, overflow: 'hidden' },
  chipText: { height: ROLL_H, lineHeight: ROLL_H, color: INK, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  meta: { color: MUTE, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  footer: { position: 'absolute', left: 24, right: 20, height: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  price: { gap: 2, flexShrink: 1 },
  priceLabel: { color: MUTE, fontSize: 13, fontWeight: '500' },
  priceValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  cta: {
    height: 48,
    paddingHorizontal: 24,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: ACCENT,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 10px 24px rgba(242,166,90,0.22), inset 0 1px 0 rgba(255,255,255,0.35)',
  },
  ctaText: { color: '#2B1608', fontSize: 16, fontWeight: '700' },
});
