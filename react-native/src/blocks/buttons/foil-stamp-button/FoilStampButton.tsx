import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  ImageShader,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  Shader,
  Skia,
  SweepGradient,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakePaper, buildSealMask, checkPath, type Layout, makeLayout, MASK_PAD } from './foilStampArt';
import { OrderHeader, OrderRows } from './FoilStampChrome';
import {
  DESCENT,
  easeOut,
  flake,
  FLAKES,
  LIGHT_AZ,
  LIGHT_EL,
  LOOP,
  loopPose,
  type Pose,
  PRESS_SCALE,
  rubber,
  sealedPose,
  SQUASH,
} from './foilStampMotion';
import { SEAL_SKSL } from './foilStampShader';
import { LABEL, styles } from './foilStampStyles';

/*
 Foil Stamp Button — a stationery shop's checkout, "Paper & Press — Order #2047". A cream cotton
 card waits for its monogram; the big round brass stamp at the bottom is the button. Pressing it
 sends the stamp down (scale 1 → 0.94, the shadows tighten); on release it springs back and the
 card shows the seal embossed in the paper, then a sweep lays champagne foil over it, and the
 label becomes a drawn check and "Sealed". The timeline is in foilStampMotion.ts (LOOP = 8 s).

 Touch: hold the stamp; the longer the hold, the deeper the press (full at 0.55 s). A release
 before 0.35 s leaves a faint blind emboss with no foil, a full hold a crisp foil seal. Dragging
 the card sideways swings the lamp, so the emboss and the foil's highlights move (rubber-banded,
 sprung back on release). 2 s after the last touch the seal dissolves (0.8 s) and the loop starts
 again from t = 0. Reduced motion: the sealed card, still; a tap on the stamp fades the seal out
 or in (0.3 s).
 */

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

// Holding the stamp: full depth after HOLD_FULL s; a hold under HOLD_WEAK s is a weak strike.
const HOLD_FULL = 0.55;
const HOLD_WEAK = 0.35;
// After the last touch the screen waits IDLE s, then eases back to rest over RESET s.
const IDLE = 2;
const RESET = 0.8;
// Dragging the card swings the light by at most LIGHT_DRAG radians.
const LIGHT_DRAG = 0.9;

const sealEffect = Skia.RuntimeEffect.Make(SEAL_SKSL);

type Props = {
  /** Strikes, foils and dissolves the seal by itself, on a loop of LOOP s. */
  autoPlay?: boolean;
};

function haptic(kind: 'light' | 'select') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function FoilStampButton({ autoPlay = false }: Props) {
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
  const paper = useMemo(() => bakePaper(layout, scale), [layout, scale]);
  const R = layout.seal.r;
  const mask = useMemo(() => buildSealMask(R, scale), [R, scale]);
  const maskSide = Math.ceil((R + MASK_PAD) * 2 * scale);
  const { seal, button, card } = layout;
  const check = useMemo(() => checkPath(button.x, button.y - 9), [button.x, button.y]);

  // The clock and who owns the screen: the loop (user 0) or the finger (user 1).
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const user = useSharedValue(reduced ? 1 : 0);
  const lastTouch = useSharedValue(0);
  const resetAt = useSharedValue(-1);
  const holding = useSharedValue(0);
  const holdStart = useSharedValue(0);
  const reachedFull = useSharedValue(0);
  const dragging = useSharedValue(0);
  const lightAtGrab = useSharedValue(0);
  // The finger's pose (used while user = 1).
  const uPress = useSharedValue(0);
  const uSquash = useSharedValue(0);
  const uDepth = useSharedValue(reduced ? 1 : 0);
  const uFoil = useSharedValue(reduced ? 1 : 0);
  const uDissolve = useSharedValue(0);
  const uRing = useSharedValue(0);
  const uLight = useSharedValue(0);
  const uCheck = useSharedValue(reduced ? 1 : 0);
  const uFlakeStart = useSharedValue(reduced ? -10 : -1);

  useEffect(() => {
    if (!reduced) return;
    const s = sealedPose();
    user.set(1);
    uDepth.set(s.depth);
    uFoil.set(s.foil);
    uCheck.set(s.check);
    uDissolve.set(0);
  }, [reduced, user, uDepth, uFoil, uCheck, uDissolve]);

  /** Takes over from the loop without a jump: the finger's pose starts as the loop's frame. */
  function touched() {
    'worklet';
    lastTouch.set(now.get());
    resetAt.set(-1);
    if (user.get() === 1) return;
    const p = loopPose(clock.get());
    uPress.set(p.press);
    uSquash.set(0);
    uDepth.set(p.depth);
    uFoil.set(p.foil);
    uDissolve.set(p.dissolve);
    uRing.set(0);
    uLight.set(p.light - LIGHT_AZ);
    uCheck.set(p.check);
    uFlakeStart.set(p.flakeAge >= 0 ? now.get() - p.flakeAge : -1);
    user.set(1);
  }

  function contact() {
    'worklet';
    uSquash.set(withSequence(withTiming(1, { duration: 70 }), withTiming(0, { duration: 110 })));
    uRing.set(withSequence(withTiming(1, { duration: 120 }), withTiming(0, { duration: 340 })));
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
      uPress.set(easeOut(Math.min(1, held / HOLD_FULL)));
      if (held >= HOLD_FULL && reachedFull.get() === 0) {
        reachedFull.set(1);
        contact();
        scheduleOnRN(haptic, 'light');
      }
      return;
    }
    if (dragging.get() === 1) return;
    if (resetAt.get() < 0 && t - lastTouch.get() > IDLE) {
      const timing = { duration: RESET * 1000, easing: EASE_IN_OUT };
      if (uDepth.get() > 0.001) uDissolve.set(withTiming(1, timing));
      uCheck.set(withTiming(0, { duration: 450, easing: EASE_OUT }));
      uPress.set(withTiming(0, timing));
      uLight.set(withTiming(0, timing));
      resetAt.set(t + RESET + 0.05);
    } else if (resetAt.get() > 0 && t >= resetAt.get()) {
      resetAt.set(-1);
      clock.set(0);
      user.set(0);
    }
  });

  const stamp = Gesture.Manual()
    .onTouchesDown((_e, manager) => {
      manager.activate();
      touched();
      if (reduced) {
        // A tap fades the seal out, or back in.
        const on = uDepth.get() > 0.5 && uDissolve.get() < 0.5;
        const fade = { duration: 300, easing: EASE_IN_OUT };
        if (on) {
          uDissolve.set(withTiming(1, fade));
          uCheck.set(withTiming(0, fade));
        } else {
          uDissolve.set(0);
          uDepth.set(withTiming(1, fade));
          uFoil.set(1);
          uCheck.set(withTiming(1, fade));
        }
        return;
      }
      holding.set(1);
      holdStart.set(now.get());
      reachedFull.set(0);
      // The old seal makes way for the new strike.
      uDepth.set(withTiming(0, { duration: 160 }));
      uFoil.set(withTiming(0, { duration: 160 }));
      uCheck.set(withTiming(0, { duration: 180, easing: EASE_OUT }));
    })
    .onTouchesUp((_e, manager) => {
      manager.end();
    })
    .onTouchesCancelled((_e, manager) => {
      manager.end();
    })
    .onFinalize(() => {
      if (holding.get() === 0) return;
      holding.set(0);
      const held = now.get() - holdStart.get();
      lastTouch.set(now.get());
      const strength =
        held < HOLD_WEAK ? 0.3 + (held / HOLD_WEAK) * 0.25 : Math.min(1, 0.55 + ((held - HOLD_WEAK) / (HOLD_FULL - HOLD_WEAK)) * 0.45);
      if (reachedFull.get() === 0) {
        contact();
        scheduleOnRN(haptic, 'light');
      }
      uPress.set(withSpring(0, { duration: 550, dampingRatio: 0.45 }));
      uDissolve.set(0);
      uDepth.set(withTiming(strength, { duration: 300, easing: EASE_OUT }));
      const crisp = strength > 0.6;
      uFlakeStart.set(crisp ? now.get() : -1);
      uFoil.set(0);
      if (crisp) {
        uFoil.set(withDelay(500, withTiming(1, { duration: 1800, easing: EASE_IN_OUT })));
        uCheck.set(withDelay(1100, withTiming(1, { duration: 600, easing: EASE_OUT })));
      }
    });

  const tilt = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onStart(() => {
      touched();
      dragging.set(1);
      lightAtGrab.set(uLight.get());
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      uLight.set(lightAtGrab.get() + (rubber(e.translationX, 140) / 140) * LIGHT_DRAG);
    })
    .onFinalize((e) => {
      dragging.set(0);
      lastTouch.set(now.get());
      uLight.set(withSpring(0, { duration: 700, dampingRatio: 0.7, velocity: (e.velocityX / 140) * LIGHT_DRAG * 0.4 }));
    });

  const pose = useDerivedValue<Pose>(() => {
    if (user.get() === 0) return loopPose(clock.get());
    const start = uFlakeStart.get();
    return {
      lift: DESCENT * uPress.get(),
      press: uPress.get(),
      squash: uSquash.get(),
      depth: uDepth.get(),
      foil: uFoil.get(),
      dissolve: uDissolve.get(),
      ring: uRing.get(),
      light: LIGHT_AZ + uLight.get(),
      check: uCheck.get(),
      flakeAge: start === -1 ? -1 : now.get() - start,
    };
  });

  const sealUniforms = useDerivedValue(() => {
    const p = pose.get();
    return {
      origin: [seal.x, seal.y],
      scale,
      R,
      depth: p.depth,
      foil: p.foil,
      dissolve: p.dissolve,
      ring: p.ring,
      light: p.light,
      elevation: LIGHT_EL,
    };
  });

  const flakes = useDerivedValue(() => {
    const p = pose.get();
    const b = Skia.PathBuilder.Make();
    if (p.flakeAge < 0) return b.build();
    for (let i = 0; i < FLAKES; i += 1) {
      const [x, y, a, s] = flake(i, p.flakeAge, R);
      const cx = seal.x + x;
      const cy = seal.y + y;
      for (let k = 0; k < 4; k += 1) {
        const ang = a + (k * Math.PI) / 2;
        const rr = s * (k % 2 === 0 ? 1 : 0.55);
        const px = cx + Math.cos(ang) * rr;
        const py = cy + Math.sin(ang) * rr;
        if (k === 0) b.moveTo(px, py);
        else b.lineTo(px, py);
      }
      b.close();
    }
    return b.build();
  });
  const flakeOpacity = useDerivedValue(() => {
    const p = pose.get();
    if (p.flakeAge < 0) return 0;
    return Math.min(1, p.flakeAge / 0.08) * (1 - p.dissolve) * Math.min(1, p.depth / 0.6);
  });

  // The stamp: lifted, pressed, squashed.
  const stampTransform = useDerivedValue(() => {
    const p = pose.get();
    const s = 1 - PRESS_SCALE * p.press - SQUASH * p.squash;
    return [{ translateY: p.lift }, { scale: s }];
  });
  const ambientY = useDerivedValue(() => button.y + 12 - 9 * pose.get().press);
  const ambientOpacity = useDerivedValue(() => 0.34 - 0.18 * Math.max(0, pose.get().press));
  const ambientBlur = useDerivedValue(() => 16 - 9 * Math.max(0, pose.get().press));
  const contactOpacity = useDerivedValue(() => 0.28 + 0.14 * Math.max(0, pose.get().press));
  const checkEnd = useDerivedValue(() => pose.get().check);

  const sealLabel = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: 1 - p.check, transform: [{ translateY: p.lift - p.check * 6 }, { scale: 1 - PRESS_SCALE * p.press }] };
  });
  const sealedLabel = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.check, transform: [{ translateY: p.lift + (1 - p.check) * 6 }, { scale: 1 - PRESS_SCALE * p.press }] };
  });

  const side = (R + MASK_PAD) * 2;
  const br = button.r;

  return (
    <>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {paper ? <Image image={paper} x={0} y={0} width={layout.width} height={layout.height} fit="fill" /> : null}
        {mask && sealEffect ? (
          <Rect x={seal.x - side / 2} y={seal.y - side / 2} width={side} height={side}>
            <Shader source={sealEffect} uniforms={sealUniforms}>
              <ImageShader image={mask} fit="none" rect={{ x: 0, y: 0, width: maskSide, height: maskSide }} />
            </Shader>
          </Rect>
        ) : null}
        <Path path={flakes} opacity={flakeOpacity}>
          <LinearGradient
            start={vec(seal.x - R, seal.y - R)}
            end={vec(seal.x + R, seal.y + R)}
            colors={['#8a6a2c', '#f3dc9a', '#b8913f', '#fff0c4', '#9a7635']}
          />
        </Path>

        {/* The stamp's two shadows on the linen. */}
        <Circle cx={button.x} cy={ambientY} r={br - 4} color="#3c2810" opacity={ambientOpacity}>
          <BlurMask blur={ambientBlur} style="normal" />
        </Circle>
        <Circle cx={button.x} cy={button.y + 2} r={br - 1} color="#2a1a08" opacity={contactOpacity}>
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Group transform={stampTransform} origin={vec(button.x, button.y)}>
          {/* Brass ring, a dark groove, and the walnut face. */}
          <Circle cx={button.x} cy={button.y} r={br}>
            <SweepGradient
              c={vec(button.x, button.y)}
              colors={['#7a5523', '#e2c27a', '#fff1c9', '#b48638', '#5e3f18', '#c9a253', '#f4dc9c', '#9a6f2c', '#7a5523']}
            />
          </Circle>
          <Circle cx={button.x} cy={button.y} r={br - 0.75} style="stroke" strokeWidth={1.5}>
            <LinearGradient
              start={vec(button.x, button.y - br)}
              end={vec(button.x, button.y + br)}
              colors={['rgba(255,248,226,0.75)', 'rgba(255,248,226,0)', 'rgba(70,44,12,0.5)']}
            />
          </Circle>
          <Circle cx={button.x} cy={button.y} r={br - 7} color="#3a2711" />
          <Circle cx={button.x} cy={button.y} r={br - 9}>
            <RadialGradient
              c={vec(button.x - 12, button.y - 16)}
              r={br * 1.3}
              colors={['#5d4130', '#3a281b', '#24170f', '#1b110b']}
            />
          </Circle>
          <Circle cx={button.x} cy={button.y} r={br - 9.5} style="stroke" strokeWidth={1}>
            <LinearGradient
              start={vec(button.x, button.y - br)}
              end={vec(button.x, button.y + br)}
              colors={['rgba(255,240,210,0.22)', 'rgba(255,240,210,0)', 'rgba(0,0,0,0.3)']}
            />
          </Circle>
          <Path path={check} style="stroke" strokeWidth={2.6} strokeCap="round" strokeJoin="round" color={LABEL} start={0} end={checkEnd} />
        </Group>
      </Canvas>

      <OrderHeader top={layout.headerTop} />

      {/* The card: drag it sideways to swing the lamp. */}
      <GestureDetector gesture={tilt}>
        <View
          style={[styles.card, { left: card.x, top: card.y, width: card.w, height: card.h }]}
          accessible
          accessibilityLabel="Monogram preview card. Drag sideways to move the light."
        >
          <View style={styles.cardTop} pointerEvents="none">
            <Text style={styles.cardKicker}>MONOGRAM PREVIEW</Text>
            <Text style={styles.cardNumber}>No. 1 / 120</Text>
          </View>
          <Text pointerEvents="none" style={styles.cardFoot}>
            Elena & Vic · 14 June 2027
          </Text>
        </View>
      </GestureDetector>

      <OrderRows top={layout.rowsTop} left={card.x} width={card.w} />

      {/* The stamp's label and hit area. */}
      <GestureDetector gesture={stamp}>
        <View
          style={[styles.hit, { left: button.x - br - 8, top: button.y - br - 8, width: br * 2 + 16, height: br * 2 + 16 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Seal order. Hold to press the stamp."
        >
          <Animated.Text style={[styles.stampLabel, sealLabel]}>Seal order</Animated.Text>
          <Animated.Text style={[styles.stampLabel, styles.sealedLabel, sealedLabel]}>Sealed</Animated.Text>
        </View>
      </GestureDetector>
      <Text pointerEvents="none" style={[styles.hint, { top: layout.hintTop }]}>
        Hold to press · drag the card to move the light
      </Text>
    </>
  );
}
