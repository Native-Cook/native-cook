import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  ImageShader,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { bakeEnvelope, buildSealMask, crackPath, flapPath, type Layout, makeLayout, bakeCard, SEAL_PAD, wedgePath } from './waxSealArt';
import {
  CRACKS,
  LOOP,
  loopPose,
  makeCracks,
  openPose,
  pieceAngle,
  type Pose,
  PULL_LIMIT,
  restPose,
  rubber,
} from './waxSealMotion';
import { CHIPS, Inbox, RsvpOverlay, styles } from './waxSealParts';
import { OFF, Piece, sealBroken, sealEffect, type WaxUniforms } from './WaxSealPiece';

/*
 Wax Seal Invite — an invitation that arrives as a modal over the inbox: a cream envelope closed
 with an oxblood wax seal stamped "L&T". Pull the seal down (or hold it) and it cracks into seven
 pieces that fall away; the flap swings open about its top edge on its red liner, and the card
 rises out of the envelope, comes forward and settles with "Attending" / "Can't make it". The
 loop (waxSealMotion.ts, LOOP = 10 s) shows the same and closes it all again in reverse.

 Touch: drag the seal down; it follows on a rubber band (at most 30 pt) while hairline cracks
 start; past 60 pt of pull, or after a 0.7 s hold, it breaks and the envelope opens (3.6 s).
 Releasing earlier springs it back and heals the cracks. The close button re-seals it (2.4 s).
 2 s after the last touch an open invitation closes itself in 0.9 s and the loop restarts.
 Reduced motion: the invitation shown open and still; the buttons still answer.
 */

const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const LINEAR = Easing.linear;
// The user's timeline runs the loop's own script: the break starts it at BREAK_AT (cracks a
// third grown, as the pull left them), it opens until OPEN_AT and closes from CLOSE_AT to LOOP.
const BREAK_AT = 2.48;
const OPEN_AT = 6.0;
const CLOSE_AT = 7.6;
// The seal breaks past BREAK_PULL pt of pull or after BREAK_HOLD s held.
const BREAK_PULL = 60;
const BREAK_HOLD = 0.7;
const IDLE = 2;
const RESET = 0.9;

type Props = {
  /** Breaks the seal, opens the envelope, shows the card and closes it again, on a loop of LOOP s. */
  autoPlay?: boolean;
};

function haptic(kind: 'light' | 'select' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function WaxSealInvite({ autoPlay = false }: Props) {
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
      <Inbox top={insets.top} />
      <BlurView intensity={18} tint="dark" style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, styles.dim]} />
      {layout ? <Scene layout={layout} autoPlay={autoPlay && !reduced} reduced={reduced} /> : null}
    </View>
  );
}

function Scene({ layout, autoPlay, reduced }: { layout: Layout; autoPlay: boolean; reduced: boolean }) {
  const scale = Math.min(PixelRatio.get(), 3);
  const { env, seal, card } = layout;
  const R = seal.r;
  const envelope = useMemo(() => bakeEnvelope(layout, Math.min(scale, 2)), [layout, scale]);
  const cardArt = useMemo(() => bakeCard(card.w, card.h, Math.min(scale, 3)), [card.w, card.h, scale]);
  const mask = useMemo(() => buildSealMask(R, scale), [R, scale]);
  const maskSide = Math.ceil((R + SEAL_PAD) * 2 * scale);
  const side = (R + SEAL_PAD) * 2;
  const cracks = useMemo(() => makeCracks(R), [R]);
  const wedges = useMemo(() => cracks.map((_c, i) => wedgePath(cracks, i, R)), [cracks, R]);
  const mids = useMemo(() => cracks.map((_c, i) => pieceAngle(cracks, i)), [cracks]);
  const flap = useMemo(() => flapPath(env.w, env.flap, env.x, env.y), [env.w, env.flap, env.x, env.y]);

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const user = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const resetting = useSharedValue(0);
  const pressing = useSharedValue(0);
  const pressStart = useSharedValue(0);
  const openedAt = useSharedValue(-1);
  // The finger's pose: whole (pressed, pulled, hairline cracks) or broken (a time on the script).
  const uBroken = useSharedValue(0);
  const uT = useSharedValue(0);
  const uPress = useSharedValue(0);
  const uPull = useSharedValue(0);
  const uCrack = useSharedValue(0);
  const [open, setOpen] = useState(reduced);
  const [answer, setAnswer] = useState<'yes' | 'no' | null>(null);
  const onOpenChange = (v: boolean) => {
    setOpen(v);
    if (!v) setAnswer(null);
  };

  function finishClose() {
    'worklet';
    uBroken.set(0);
    uPress.set(0);
    uPull.set(0);
    uCrack.set(0);
    if (resetting.get() === 1) {
      resetting.set(0);
      clock.set(0);
      user.set(0);
    }
  }

  /** Runs the script from where it is to `to`, at real speed or squeezed into `ms`. */
  function play(to: number, ms?: number) {
    'worklet';
    const from = uT.get();
    const duration = ms ?? Math.max(0, (to - from) * 1000);
    const done = to >= LOOP;
    uT.set(
      withTiming(to, { duration, easing: ms ? EASE_IN_OUT : LINEAR }, (finished) => {
        if (finished && done) finishClose();
      }),
    );
  }

  /** Takes over from the loop where it is; a moving envelope finishes the move it was making. */
  function touched() {
    'worklet';
    lastTouch.set(now.get());
    if (resetting.get() === 1) return;
    if (user.get() === 1) return;
    user.set(1);
    const t = clock.get();
    if (t < 2.4) {
      const p = loopPose(t);
      uBroken.set(0);
      uPress.set(p.press);
      uPull.set(p.pull);
      uCrack.set(p.crack);
      uPress.set(withTiming(0, { duration: 250 }));
      uPull.set(withSpring(0, { duration: 450, dampingRatio: 0.6 }));
      uCrack.set(withTiming(0, { duration: 300 }));
      return;
    }
    uBroken.set(1);
    uT.set(t);
    if (t < OPEN_AT) play(OPEN_AT);
    else if (t >= CLOSE_AT) play(LOOP);
  }

  function breakSeal() {
    'worklet';
    pressing.set(0);
    uBroken.set(1);
    uT.set(BREAK_AT);
    play(OPEN_AT);
    scheduleOnRN(haptic, 'light');
  }

  function closeEnvelope() {
    'worklet';
    touched();
    if (uBroken.get() === 0 || uT.get() < OPEN_AT - 0.01) return;
    if (uT.get() < CLOSE_AT) uT.set(CLOSE_AT);
    play(LOOP);
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
    if (pressing.get() === 1 && uBroken.get() === 0) {
      const held = t - pressStart.get();
      uCrack.set(Math.max(uCrack.get(), Math.min(0.3, (held / BREAK_HOLD) * 0.3)));
      if (held >= BREAK_HOLD) breakSeal();
      return;
    }
    const isOpen = uBroken.get() === 1 && uT.get() > OPEN_AT - 0.001 && uT.get() < CLOSE_AT;
    if (!isOpen) openedAt.set(-1);
    else if (openedAt.get() < 0) openedAt.set(t);
    if (resetting.get() === 1 || t - Math.max(lastTouch.get(), openedAt.get()) < IDLE) return;
    if (uBroken.get() === 0) {
      user.set(0);
      clock.set(0);
    } else if (isOpen) {
      // An open invitation closes itself, squeezed into RESET s.
      resetting.set(1);
      uT.set(CLOSE_AT);
      play(LOOP, RESET * 1000);
    }
  });

  const sealGesture = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      touched();
      if (uBroken.get() === 1) return;
      pressing.set(1);
      pressStart.set(now.get());
      uPress.set(withTiming(1, { duration: 120 }));
    })
    .onUpdate((e) => {
      if (uBroken.get() === 1 || pressing.get() === 0) return;
      lastTouch.set(now.get());
      const raw = Math.max(0, e.translationY);
      uPull.set(rubber(raw, PULL_LIMIT));
      uCrack.set(Math.max(uCrack.get(), Math.min(0.3, (raw / BREAK_PULL) * 0.3)));
      if (raw > BREAK_PULL) breakSeal();
    })
    .onFinalize(() => {
      lastTouch.set(now.get());
      if (pressing.get() === 0 || uBroken.get() === 1) return;
      pressing.set(0);
      uPress.set(withTiming(0, { duration: 200 }));
      uPull.set(withSpring(0, { duration: 450, dampingRatio: 0.55 }));
      uCrack.set(withTiming(0, { duration: 350 }));
    });

  const pose = useDerivedValue<Pose>(() => {
    if (reduced) return openPose();
    if (user.get() === 0) return loopPose(clock.get());
    if (uBroken.get() === 1) return loopPose(uT.get());
    return { ...restPose(), press: uPress.get(), pull: uPull.get(), crack: uCrack.get() };
  });

  useAnimatedReaction(
    () => pose.get().card > 1.9 && pose.get().buttons > 0.5,
    (isOpen, before) => {
      if (isOpen === before) return;
      scheduleOnRN(onOpenChange, isOpen);
      if (isOpen && user.get() === 1 && !reduced) scheduleOnRN(haptic, 'select');
    },
  );

  // The flap: outside while it is less than upright, its lined inside once past.
  const flapOutT = useDerivedValue(() => {
    const a = pose.get().flap;
    return a <= Math.PI / 2 ? [{ perspective: 900 }, { rotateX: a }] : OFF;
  });
  const flapInT = useDerivedValue(() => {
    const a = pose.get().flap;
    return a > Math.PI / 2 ? [{ perspective: 900 }, { rotateX: a }] : OFF;
  });
  const flapOutShade = useDerivedValue(() => (pose.get().flap / (Math.PI / 2)) * 0.45);
  const flapInShade = useDerivedValue(() => ((Math.PI - pose.get().flap) / (Math.PI / 2)) * 0.5);
  const flapShadow = useDerivedValue(() => 0.22 * Math.max(0, Math.cos(pose.get().flap)));

  // The card: inside, rising clear, then forward over the envelope.
  const cardPlace = useDerivedValue(() => {
    const c = pose.get().card;
    const top = c <= 1 ? card.insideTop + (card.clearTop - card.insideTop) * c : card.clearTop + (card.finalTop - card.clearTop) * (c - 1);
    return { top, s: c <= 1 ? 1 : 1 + 0.1 * (c - 1), front: c > 1 };
  });
  // Where the card is drawn: not at all while it is inside the envelope, behind the envelope's
  // front (and clipped to above its mouth) while it goes in or out, in front once it has come out.
  const [cardLayer, setCardLayer] = useState<'inside' | 'behind' | 'front'>(reduced ? 'front' : 'inside');
  useAnimatedReaction(
    () => {
      const c = pose.get().card;
      return c <= 0.001 ? 'inside' : c > 1 ? 'front' : 'behind';
    },
    (layer, before) => {
      if (layer !== before) scheduleOnRN(setCardLayer, layer);
    },
  );
  const cardT = useDerivedValue(() => {
    const k = cardPlace.get();
    return [
      { translateX: card.x + card.w / 2 },
      { translateY: k.top + card.h / 2 },
      { scale: k.s },
      { translateX: -card.w / 2 },
      { translateY: -card.h / 2 },
    ];
  });
  const mouth = useMemo(() => Skia.XYWHRect(0, 0, layout.width, env.y + env.h * 0.5), [layout.width, env.y, env.h]);

  // The seal, in its own coordinates (centre at 0, 0).
  const sealT = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: seal.x }, { translateY: seal.y + p.pull }, { scale: 1 - 0.05 * p.press }];
  });
  // One set of uniforms for the whole seal and its pieces; which one shows is decided by
  // moving the other off screen, so the whole seal is always drawn while it is intact.
  const waxUniforms = useDerivedValue<WaxUniforms>(() => ({ origin: [0, 0], scale, R, pad: SEAL_PAD, gleam: pose.get().gleam }));
  const wholeT = useDerivedValue(() => (sealBroken(pose.get()) ? OFF : [{ translateX: 0 }]));
  const crackLines = useDerivedValue(() => crackPath(cracks, pose.get().crack, 0, 0));
  const crackOpacity = useDerivedValue(() => Math.max(0, 1 - pose.get().fall * 6));
  const sealShadow = useDerivedValue(() => 0.4 * Math.max(0, 1 - pose.get().fall * 5));

  // On short screens the card rises past the header: the header steps aside while it does.
  const headerFades = card.clearTop < layout.headerTop + 86;
  const headerStyle = useAnimatedStyle(() => {
    if (!headerFades) return { opacity: 1 };
    const c = pose.get().card;
    return { opacity: 1 - 0.9 * Math.min(1, Math.max(0, (c - 0.4) / 0.4)) * Math.min(1, Math.max(0, (1.9 - c) / 0.4)) };
  });
  const hintStyle = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - pose.get().crack * 3 - pose.get().card) }));

  const answerWith = (a: 'yes' | 'no') => {
    scheduleOnUI(touched);
    setAnswer(a);
    haptic(a === 'yes' ? 'success' : 'light');
  };

  return (
    <>
      <Animated.View pointerEvents="none" style={[styles.header, { top: layout.headerTop }, headerStyle]}>
        <Text style={styles.kicker}>{"YOU'RE INVITED"}</Text>
        <Text style={styles.title}>A letter from Lena & Theo</Text>
        <Text style={styles.sub}>Delivered yesterday by courier</Text>
      </Animated.View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {/* The envelope's two shadows, then its back with the liner. */}
        <RoundedRect x={env.x + 8} y={env.y + 18} width={env.w - 16} height={env.h} r={12} color="rgba(8,2,0,0.55)">
          <BlurMask blur={22} style="normal" />
        </RoundedRect>
        <RoundedRect x={env.x + 1} y={env.y + 2} width={env.w - 2} height={env.h} r={10} color="rgba(8,2,0,0.45)">
          <BlurMask blur={2.5} style="normal" />
        </RoundedRect>
        {envelope.back ? <Image image={envelope.back} x={env.x} y={env.y} width={env.w} height={env.h} fit="fill" /> : null}

        <Group transform={flapInT} origin={vec(layout.width / 2, env.y)}>
          {envelope.flapIn ? <Image image={envelope.flapIn} x={env.x} y={env.y} width={env.w} height={env.flap + 4} fit="fill" /> : null}
          <Path path={flap} color="#1a0606" opacity={flapInShade} />
        </Group>

        {cardLayer === 'behind' && cardArt ? (
          <Group clip={mouth}>
            <Group transform={cardT}>
              <Image image={cardArt} x={0} y={0} width={card.w} height={card.h} fit="fill" />
            </Group>
          </Group>
        ) : null}

        {envelope.front ? <Image image={envelope.front} x={env.x} y={env.y} width={env.w} height={env.h} fit="fill" /> : null}

        <Group transform={[{ translateY: 3 }]}>
          <Path path={flap} color="#462810" opacity={flapShadow}>
            <BlurMask blur={3} style="normal" />
          </Path>
        </Group>
        <Group transform={flapOutT} origin={vec(layout.width / 2, env.y)}>
          {envelope.flapOut ? <Image image={envelope.flapOut} x={env.x} y={env.y} width={env.w} height={env.flap + 4} fit="fill" /> : null}
          <Path path={flap} color="#2a1a0c" opacity={flapOutShade} />
        </Group>

        {mask && sealEffect ? (
          <Group transform={sealT}>
            <Circle cx={1} cy={3.5} r={R * 0.98} color="#3c0a0a" opacity={sealShadow}>
              <BlurMask blur={4} style="normal" />
            </Circle>
            <Group transform={wholeT}>
              <Rect x={-side / 2} y={-side / 2} width={side} height={side}>
                <Shader source={sealEffect} uniforms={waxUniforms}>
                  <ImageShader image={mask} fit="none" rect={{ x: 0, y: 0, width: maskSide, height: maskSide }} />
                </Shader>
              </Rect>
            </Group>
            {Array.from({ length: CRACKS }, (_v, i) => (
              <Piece
                key={i}
                index={i}
                mid={mids[i]}
                wedge={wedges[i]}
                pose={pose}
                R={R}
                side={side}
                uniforms={waxUniforms}
                mask={mask}
                maskSide={maskSide}
              />
            ))}
            <Path path={crackLines} style="stroke" strokeWidth={1.2} strokeCap="round" strokeJoin="round" color="#2a0507" opacity={crackOpacity} />
            <Group transform={[{ translateX: 0.6 }, { translateY: 0.8 }]}>
              <Path path={crackLines} style="stroke" strokeWidth={0.6} strokeCap="round" color="#d4646a" opacity={crackOpacity} />
            </Group>
          </Group>
        ) : null}

        {cardLayer === 'front' && cardArt ? (
          <Group transform={cardT}>
            <RoundedRect x={6} y={18} width={card.w - 12} height={card.h - 4} r={10} color="rgba(30,8,4,0.4)">
              <BlurMask blur={16} style="normal" />
            </RoundedRect>
            <RoundedRect x={0.5} y={1.5} width={card.w - 1} height={card.h} r={8} color="rgba(30,8,4,0.3)">
              <BlurMask blur={2} style="normal" />
            </RoundedRect>
            <Image image={cardArt} x={0} y={0} width={card.w} height={card.h} fit="fill" />
          </Group>
        ) : null}
      </Canvas>

      <GestureDetector gesture={sealGesture}>
        <View
          style={[styles.sealHit, { left: seal.x - R - 22, top: seal.y - R - 22, width: R * 2 + 44, height: R * 2 + 44 }]}
          pointerEvents={open ? 'none' : 'auto'}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Wax seal. Pull down or hold to open the invitation."
        />
      </GestureDetector>

      <RsvpOverlay
        open={open}
        closable={open && !reduced}
        card={card}
        closeTop={layout.headerTop - 4}
        pose={pose}
        place={cardPlace}
        answer={answer}
        onAnswer={answerWith}
        onClose={() => {
          setAnswer(null);
          scheduleOnUI(closeEnvelope);
        }}
      />

      <View pointerEvents="none" style={[styles.chips, { top: layout.chipsTop }]}>
        {CHIPS.map((c) => (
          <View key={c} style={styles.chip}>
            <Text style={styles.chipText}>{c}</Text>
          </View>
        ))}
      </View>

      <Animated.Text pointerEvents="none" style={[styles.hint, { top: layout.hintTop }, hintStyle]}>
        Pull the seal down to open
      </Animated.Text>
    </>
  );
}
