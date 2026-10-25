import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  Image,
  LinearGradient,
  Path,
  Picture,
  RadialGradient,
  RoundedRect,
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
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeBackdrop, cordPaths, type Layout, makeLayout, recordPoster, smokeOpacity, smokePath, sparkPaths } from './fuseBurnArt';
import { fuseAt, LOOP, loopPose, makeFuse, nearestOnFuse, type Pose } from './fuseBurnMotion';
import { AVATARS, Check, COUNT_H, Flame, styles } from './fuseBurnParts';

/*
 Fuse Burn Button — an event's ticket screen, "Launch Night — Rooftop 27, 21:00". A braided fuse
 is wrapped round the "Light it up" capsule. In the loop (fuseBurnMotion.ts, LOOP = 9 s) a match
 flares at the capsule's left end, a spark runs clockwise round it (sputtering once at 40 %),
 the cord behind it turns to crinkled charcoal under rising smoke, and when the spark meets the
 start the capsule bursts into 30 sparks, lights up amber, reads "You're in", and the count of
 people going rolls from 211 to 212; then the cord re-weaves itself and the screen rests.

 Touch: hold the capsule to burn (a full lap takes 4.2 s); let go early and the spark sputters
 and dies where it is, the next hold relights it there. Drag a finger along the cord just ahead
 of the spark to pull the burn along (the finger is the match). 2 s after the last touch the cord
 re-weaves (0.85 s) and the loop starts again. Reduced motion: no spark or smoke; a tap on the
 capsule fades it between "Light it up" and "You're in".
 */

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

// The fuse sits FUSE_OFFSET pt outside the capsule's outline.
const FUSE_OFFSET = 8;
// Burning under a held finger: a whole lap in 1 / BURN_RATE s.
const BURN_RATE = 0.24;
// A drag pulls the burn along when it lands within SCRUB_REACH pt of the cord, ahead of the
// spark by less than SCRUB_AHEAD of a lap.
const SCRUB_REACH = 34;
const SCRUB_AHEAD = 0.12;
const IDLE = 2;
const RESET = 0.85;
// Touch slop round the capsule.
const SLOP = 22;


type Props = {
  /** Lights, burns, bursts and re-weaves the fuse by itself, on a loop of LOOP s. */
  autoPlay?: boolean;
};

function haptic(kind: 'light' | 'success' | 'select') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function FuseBurnButton({ autoPlay = false }: Props) {
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
  const backdrop = useMemo(() => bakeBackdrop(layout, scale), [layout, scale]);
  const posterArt = useMemo(() => recordPoster(layout.poster), [layout.poster]);
  const { capsule } = layout;
  const fuse = useMemo(
    () => makeFuse(capsule.x, capsule.y, capsule.w, capsule.h, FUSE_OFFSET),
    [capsule.x, capsule.y, capsule.w, capsule.h],
  );
  const cx = capsule.x + capsule.w / 2;
  const cy = capsule.y + capsule.h / 2;
  const radius = capsule.h / 2;

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const user = useSharedValue(reduced ? 1 : 0);
  const lastTouch = useSharedValue(0);
  const resetAt = useSharedValue(-1);
  const holding = useSharedValue(0);
  const fired = useSharedValue(0);
  // The finger's pose.
  const uB = useSharedValue(0);
  const uW = useSharedValue(0);
  const uSpark = useSharedValue(0);
  const uFlare = useSharedValue(0);
  const uSpeed = useSharedValue(0);
  const uBurst = useSharedValue(-1);
  const uLit = useSharedValue(0);
  const uLabel = useSharedValue(0);
  const uCount = useSharedValue(0);

  useEffect(() => {
    if (reduced) user.set(1);
  }, [reduced, user]);

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    resetAt.set(-1);
    if (user.get() === 1) return;
    const p = loopPose(clock.get(), fuse.length);
    uB.set(p.b);
    uW.set(p.w);
    uSpark.set(p.spark);
    uFlare.set(p.flare);
    uSpeed.set(0);
    uBurst.set(p.burstAge >= 0 ? now.get() - p.burstAge : -1);
    uLit.set(p.lit);
    uLabel.set(p.label);
    uCount.set(p.count);
    fired.set(p.b >= 0.999 ? 1 : 0);
    user.set(1);
  }

  function burst() {
    'worklet';
    fired.set(1);
    uBurst.set(now.get());
    uLit.set(withTiming(1, { duration: 300, easing: EASE_OUT }));
    uLabel.set(withTiming(1, { duration: 500, easing: EASE_OUT }));
    uCount.set(withTiming(1, { duration: 550, easing: EASE_IN_OUT }));
    scheduleOnRN(haptic, 'success');
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
    if (holding.get() === 1 && fired.get() === 0) {
      // Burning: the spark brightens quickly and runs at a slightly uneven pace.
      const pace = BURN_RATE * (0.85 + 0.3 * Math.sin(t * 3.1) * Math.sin(t * 1.7));
      uB.set(Math.min(1, uB.get() + dt * pace));
      uSpeed.set(pace * fuse.length);
      uSpark.set(uSpark.get() + (1 - uSpark.get()) * Math.min(1, dt * 12));
      if (uB.get() >= 1) burst();
    } else if (uSpark.get() > 0.001) {
      // Let go: the spark sputters and dies where it is.
      uSpeed.set(0);
      uSpark.set(uSpark.get() * Math.exp(-dt * 3.2) * (0.92 + 0.08 * Math.sin(t * 40)));
    }
    if (holding.get() === 1) return;
    if (resetAt.get() < 0 && t - lastTouch.get() > IDLE) {
      const timing = { duration: RESET * 1000, easing: EASE_IN_OUT };
      uW.set(withTiming(1, timing));
      uSpark.set(withTiming(0, { duration: 250 }));
      uLit.set(withTiming(0, timing));
      uLabel.set(withTiming(0, timing));
      uCount.set(withTiming(0, timing));
      resetAt.set(t + RESET + 0.05);
    } else if (resetAt.get() > 0 && t >= resetAt.get()) {
      resetAt.set(-1);
      clock.set(0);
      user.set(0);
    }
  });

  const touch = Gesture.Manual()
    .onTouchesDown((_e, manager) => {
      manager.activate();
      touched();
      if (reduced) {
        const on = uLabel.get() > 0.5;
        const fade = { duration: 300, easing: EASE_IN_OUT };
        uLit.set(withTiming(on ? 0 : 1, fade));
        uLabel.set(withTiming(on ? 0 : 1, fade));
        uCount.set(withTiming(on ? 0 : 1, fade));
        return;
      }
      holding.set(1);
      if (fired.get() === 0 && uSpark.get() < 0.2) {
        // A fresh light: the match flares.
        uFlare.set(withSequence(withTiming(1, { duration: 110 }), withTiming(0, { duration: 320, easing: EASE_OUT })));
        scheduleOnRN(haptic, 'light');
      }
    })
    .onTouchesMove((e) => {
      if (reduced || fired.get() === 1) return;
      lastTouch.set(now.get());
      const touchPoint = e.allTouches[0];
      if (!touchPoint) return;
      // The detector's view starts SLOP pt above and left of the capsule.
      const [u, d] = nearestOnFuse(fuse, touchPoint.x + capsule.x - SLOP, touchPoint.y + capsule.y - SLOP);
      const b = uB.get();
      if (d < SCRUB_REACH && u > b && u - b < SCRUB_AHEAD) {
        uSpeed.set(((u - b) * fuse.length) / 0.016);
        uSpark.set(Math.max(uSpark.get(), 0.85));
        uB.set(u);
        if (u >= 1) burst();
      }
    })
    .onTouchesUp((_e, manager) => {
      manager.end();
    })
    .onTouchesCancelled((_e, manager) => {
      manager.end();
    })
    .onFinalize(() => {
      holding.set(0);
      lastTouch.set(now.get());
    });

  const pose = useDerivedValue<Pose>(() => {
    if (user.get() === 0) return loopPose(clock.get(), fuse.length);
    const start = uBurst.get();
    return {
      b: uB.get(),
      w: uW.get(),
      spark: reduced ? 0 : uSpark.get(),
      flare: uFlare.get(),
      speed: uSpeed.get(),
      burstAge: start < 0 ? -1 : now.get() - start,
      lit: uLit.get(),
      label: uLabel.get(),
      count: uCount.get(),
      time: now.get(),
    };
  });

  const cord = useDerivedValue(() => {
    const p = pose.get();
    return cordPaths(fuse, p.b, p.w);
  });
  const cordPath = useDerivedValue(() => cord.get().cord);
  const ticks = useDerivedValue(() => cord.get().ticks);
  const charcoal = useDerivedValue(() => cord.get().charcoal);
  const ember = useDerivedValue(() => cord.get().ember);
  const charcoalOpacity = useDerivedValue(() => 1 - pose.get().w);
  const emberOpacity = useDerivedValue(() => Math.min(1, pose.get().spark * 1.2) * (1 - pose.get().w));

  const sparks = useDerivedValue(() => sparkPaths(fuse, pose.get(), cx, cy));
  const hot = useDerivedValue(() => sparks.get()[0]);
  const warm = useDerivedValue(() => sparks.get()[1]);
  const smoke = useDerivedValue(() => smokePath(fuse, pose.get()));
  const smokeAlpha = useDerivedValue(() => smokeOpacity(pose.get()));

  const sparkAt = useDerivedValue(() => {
    const [x, y] = fuseAt(fuse, pose.get().b);
    return vec(x, y);
  });
  const sparkX = useDerivedValue(() => sparkAt.get().x);
  const sparkY = useDerivedValue(() => sparkAt.get().y);
  const glowR = useDerivedValue(() => 20 + pose.get().flare * 34);
  const glowAlpha = useDerivedValue(() => Math.min(1, pose.get().spark + pose.get().flare));
  const coreAlpha = useDerivedValue(() => pose.get().spark);
  const insideGlow = useDerivedValue(() => pose.get().spark * 0.45 * (1 - pose.get().lit));
  const lit = useDerivedValue(() => pose.get().lit);
  const burstFlash = useDerivedValue(() => {
    const a = pose.get().burstAge;
    return a < 0 ? 0 : Math.exp(-a * 5) * 0.8;
  });

  const lightLabel = useAnimatedStyle(() => {
    const l = pose.get().label;
    return { opacity: 1 - l, transform: [{ translateY: -l * 8 }, { scale: 1 - l * 0.04 }] };
  });
  const inLabel = useAnimatedStyle(() => {
    const l = pose.get().label;
    return { opacity: l, transform: [{ translateY: (1 - l) * 8 }, { scale: 0.96 + l * 0.04 }] };
  });
  const countRoll = useAnimatedStyle(() => ({ transform: [{ translateY: -pose.get().count * COUNT_H }] }));

  const { poster } = layout;
  return (
    <>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {backdrop ? <Image image={backdrop} x={0} y={0} width={layout.width} height={layout.height} fit="fill" /> : null}
        {/* The poster, with two shadows and a hairline of light on its top edge. */}
        <RoundedRect x={poster.x} y={poster.y + 14} width={poster.w} height={poster.h - 10} r={24} color="rgba(0,0,8,0.55)">
          <BlurMask blur={22} style="normal" />
        </RoundedRect>
        <Picture picture={posterArt} />
        <RoundedRect x={poster.x + 0.5} y={poster.y + 0.5} width={poster.w - 1} height={poster.h - 1} r={23.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(0, poster.y)}
            end={vec(0, poster.y + poster.h)}
            colors={['rgba(255,255,255,0.22)', 'rgba(255,255,255,0.03)', 'rgba(255,255,255,0.06)']}
          />
        </RoundedRect>

        {/* The capsule. */}
        <RoundedRect x={capsule.x + 6} y={capsule.y + 14} width={capsule.w - 12} height={capsule.h} r={radius} color="rgba(0,0,10,0.6)">
          <BlurMask blur={18} style="normal" />
        </RoundedRect>
        <RoundedRect x={capsule.x} y={capsule.y + 1.5} width={capsule.w} height={capsule.h} r={radius} color="rgba(0,0,8,0.7)">
          <BlurMask blur={2.5} style="normal" />
        </RoundedRect>
        <RoundedRect x={capsule.x} y={capsule.y} width={capsule.w} height={capsule.h} r={radius}>
          <LinearGradient start={vec(0, capsule.y)} end={vec(0, capsule.y + capsule.h)} colors={['#2d3768', '#20284d', '#171d3a']} />
        </RoundedRect>
        <RoundedRect x={capsule.x} y={capsule.y} width={capsule.w} height={capsule.h} r={radius} opacity={insideGlow}>
          <RadialGradient c={sparkAt} r={160} colors={['rgba(255,140,60,0.9)', 'rgba(255,110,50,0.25)', 'rgba(255,110,50,0)']} />
        </RoundedRect>
        <RoundedRect x={capsule.x} y={capsule.y} width={capsule.w} height={capsule.h} r={radius} opacity={lit}>
          <LinearGradient
            start={vec(capsule.x, capsule.y)}
            end={vec(capsule.x + capsule.w, capsule.y + capsule.h)}
            colors={['#ffc06a', '#ff9444', '#f2672c', '#ff8c3e']}
          />
        </RoundedRect>
        <RoundedRect x={capsule.x} y={capsule.y} width={capsule.w} height={capsule.h} r={radius} color="#fff4dc" opacity={burstFlash} />
        <RoundedRect x={capsule.x + 0.5} y={capsule.y + 0.5} width={capsule.w - 1} height={capsule.h - 1} r={radius - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(0, capsule.y)}
            end={vec(0, capsule.y + capsule.h)}
            colors={['rgba(255,255,255,0.28)', 'rgba(255,255,255,0.02)', 'rgba(0,0,0,0.25)']}
          />
        </RoundedRect>

        {/* The fuse: a dark core, the braid, its twist highlights; charcoal and embers behind the spark. */}
        <Path path={cordPath} style="stroke" strokeWidth={6.2} strokeCap="round" color="#3b2812" />
        <Path path={cordPath} style="stroke" strokeWidth={4.4} strokeCap="round" color="#b98d55" />
        <Path path={ticks} style="stroke" strokeWidth={1.2} strokeCap="round" color="#f0d7a2" opacity={0.85} />
        <Group opacity={charcoalOpacity}>
          <Path path={charcoal} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color="#0b0a0c" opacity={0.7} />
          <Path path={charcoal} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#3a3230" />
          <Path path={charcoal} style="stroke" strokeWidth={0.8} color="#9a8e84" opacity={0.55}>
            <DashPathEffect intervals={[1.5, 3.5]} />
          </Path>
        </Group>
        <Path path={ember} style="stroke" strokeWidth={3.4} strokeCap="round" color="#ff6a2e" blendMode="plus" opacity={emberOpacity}>
          <BlurMask blur={2} style="solid" />
        </Path>

        {/* Smoke, then the spark: a glow, its core and the thrown sparks (added light). */}
        <Path path={smoke} color="#9aa3c4" opacity={smokeAlpha}>
          <Blur blur={7} />
        </Path>
        <Circle cx={sparkX} cy={sparkY} r={glowR} opacity={glowAlpha} blendMode="plus">
          <RadialGradient c={sparkAt} r={glowR} colors={['#fff6e0', '#ffd27a', 'rgba(255,106,46,0.45)', 'rgba(255,106,46,0)']} positions={[0, 0.18, 0.5, 1]} />
        </Circle>
        <Path path={warm} style="stroke" strokeWidth={1.3} strokeCap="round" color="#ff9a3c" blendMode="plus" />
        <Path path={hot} style="stroke" strokeWidth={1.7} strokeCap="round" color="#fff0c2" blendMode="plus" />
        <Circle cx={sparkX} cy={sparkY} r={2.6} color="#fffaf0" opacity={coreAlpha} />
      </Canvas>

      <View pointerEvents="none" style={[styles.header, { top: layout.headerTop }]}>
        <Text style={styles.kicker}>LAUNCH NIGHT</Text>
        <Text style={styles.title}>Rooftop 27</Text>
        <Text style={styles.sub}>Sat 22 Nov · 21:00 · Pier District</Text>
      </View>

      <View pointerEvents="none" style={[styles.posterInfo, { left: poster.x + 16, top: poster.y + poster.h - 64, width: poster.w - 32 }]}>
        <View style={styles.chip}>
          <Text style={styles.chipText}>LINEUP</Text>
        </View>
        <Text style={styles.lineup} numberOfLines={1}>
          Mara Quell · The Low Tides · Okoro
        </Text>
      </View>

      <View pointerEvents="none" style={[styles.rows, { top: layout.rowsTop, left: capsule.x, width: capsule.w }]}>
        <View style={styles.row}>
          <View style={styles.avatars}>
            {AVATARS.map((a, i) => (
              <View key={a.initials} style={[styles.avatar, { backgroundColor: a.color, marginLeft: i === 0 ? 0 : -8 }]}>
                <Text style={styles.avatarText}>{a.initials}</Text>
              </View>
            ))}
          </View>
          <View style={styles.countClip}>
            <Animated.View style={countRoll}>
              <Text style={styles.count}>211</Text>
              <Text style={styles.count}>212</Text>
            </Animated.View>
          </View>
          <Text style={styles.going}>going · 12 friends</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.ticket}>General admission</Text>
          <Text style={styles.price}>$24.00</Text>
        </View>
      </View>

      <GestureDetector gesture={touch}>
        <View
          style={[
            styles.hit,
            { left: capsule.x - SLOP, top: capsule.y - SLOP, width: capsule.w + SLOP * 2, height: capsule.h + SLOP * 2 },
          ]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Light it up. Hold to burn the fuse."
        >
          <Animated.View style={[styles.label, lightLabel]}>
            <Flame />
            <Text style={styles.labelText}>Light it up</Text>
          </Animated.View>
          <Animated.View style={[styles.label, inLabel]}>
            <Check />
            <Text style={[styles.labelText, styles.inText]}>{"You're in"}</Text>
          </Animated.View>
        </View>
      </GestureDetector>
      <Text pointerEvents="none" style={[styles.hint, { top: layout.hintTop }]}>
        Hold to light · drag along the fuse
      </Text>
    </>
  );
}
