import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, interpolateColor, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Blur, Canvas, Circle, Group, interpolateColors, Line, Path, Skia, Text as SkText, useFont, vec } from '@shopify/react-native-skia';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { C, G, LOOP, mix, seg, SPRING, type TrFrame, trFrameAt } from './tr-motion';

type Props = {
  /** Plays the 9 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

const SLOTS = [-4, -3, -2, -1, 0, 1, 2, 3, 4] as const;

/** Look of a numeral k steps from the focus row (k continuous): scale, colour mix, depth blur, alpha. */
const falloff = (k: number) => {
  'worklet';
  const a = Math.abs(k);
  const scale = a < 1 ? mix(1, 0.84, a) : a < 2 ? mix(0.84, 0.74, a - 1) : a < 3 ? mix(0.74, 0.66, a - 2) : 0.66;
  const blur = a < 1 ? 0 : a < 2 ? mix(0, 1.5, a - 1) : a < 3 ? mix(1.5, 4, a - 2) : 4;
  const alpha = a < 3.3 ? 1 : 1 - seg(a, 3.3, 3.9);
  return { scale, blur, alpha, a };
};

const hourLabel = (v: number) => {
  'worklet';
  const n = ((((v - 1) % 12) + 12) % 12) + 1;
  return n < 10 ? `0${n}` : `${n}`;
};
const minLabel = (v: number) => {
  'worklet';
  const n = (((v * 5) % 60) + 60) % 60;
  return n < 10 ? `0${n}` : `${n}`;
};

/**
 * The pinion: four distinct lobes (radius R, 8 pt deep between them at rest), centred on the origin. `lobe` 0 makes
 * it a plain disc, which is what it becomes on the confirm (the sun).
 */
const pinionPathAt = (R: number, lobe: number) => {
  'worklet';
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i <= 120; i++) {
    const t = (i / 120) * Math.PI * 2;
    const r = R * (1 - lobe * 0.26 * (0.5 - 0.5 * Math.cos(4 * t)));
    const x = Math.cos(t) * r;
    const y = Math.sin(t) * r;
    if (i === 0) b.moveTo(x, y);
    else b.lineTo(x, y);
  }
  b.close();
  return b.detach();
};

/** The engraved scale on a band's inner edge: a 6 pt tick every 3.6°, 12 pt at each 18° detent. */
const ticksPath = (() => {
  const b = Skia.PathBuilder.Make();
  const r0 = G.ringR - G.ringW / 2;
  for (let i = 0; i < 100; i++) {
    const a = (i * 3.6 * Math.PI) / 180;
    const len = i % 5 === 0 ? 12 : 6;
    b.moveTo(Math.cos(a) * r0, Math.sin(a) * r0);
    b.lineTo(Math.cos(a) * (r0 + len), Math.sin(a) * (r0 + len));
  }
  return b.detach();
})();

/** A 14 pt arc on a ring's outer rim, centred on angle `deg` (degrees), around the origin. */
const rimArc = (deg: number) => {
  const b = Skia.PathBuilder.Make();
  const r = G.ringR + G.ringW / 2 - 2;
  const half = (7 / r) * (180 / Math.PI);
  for (let i = 0; i <= 12; i++) {
    const a = ((deg - half + (2 * half * i) / 12) * Math.PI) / 180;
    if (i === 0) b.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else b.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  return b.detach();
};
// where each rim meets the pinion (201, 442)
const MESH_L = rimArc((Math.atan2(G.pinionY - G.wheelY, 201 - G.leftCx) * 180) / Math.PI);
const MESH_R = rimArc((Math.atan2(G.pinionY - G.wheelY, 201 - G.rightCx) * 180) / Math.PI);

/**
 * The "When do you usually wake up?" step of a sleep-coach app: two wheels wider than the screen, meshed through a
 * small yellow pinion, carry the hours and minutes past a fixed focus row. Passing the top of the hour on the
 * minutes kicks the hour wheel over (a clock's carry); numerals streak along the arc at speed.
 */
export function TwinRingTimePicker({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({
    'TR-Onest-ExtraBold': require('./assets/fonts/Onest-ExtraBold.ttf'),
    'TR-InterTight-SemiBold': require('./assets/fonts/InterTight-SemiBold.ttf'),
  });
  const font = useFont(require('./assets/fonts/Onest-ExtraBold.ttf'), 56);
  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  // the layout is measured on an 874 pt screen; keep the wheels centred in the space between title and CTA
  const dy = (H / S - 874) / 2;

  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const hLive = useSharedValue(8);
  const mLive = useSharedValue(6);
  const openLive = useSharedValue(0);
  const fillLive = useSharedValue(0);
  const ctaLive = useSharedValue(1);
  const held = useSharedValue(0); // 1 hours, 2 minutes
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const homing = useSharedValue(0);
  const start = useSharedValue(0);
  const hPrev = useSharedValue(8);
  const mPrev = useSharedValue(6);
  const hSpeedLive = useSharedValue(0);
  const mSpeedLive = useSharedValue(0);
  const flashLive = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tr?: unknown };
    g.__tr = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => pinned.set(0),
      rate: (r: number) => rate.set(r),
      state: () => ({ T: T.get(), auto: auto.get(), h: hLive.get(), m: mLive.get() }),
    };
    return () => {
      delete g.__tr;
    };
  }, [T, auto, pinned, rate, hLive, mLive]);

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    // live speeds for the motion blur
    if (dt > 0) {
      hSpeedLive.set(mix(hSpeedLive.get(), Math.abs(hLive.get() - hPrev.get()) / dt, 0.5));
      mSpeedLive.set(mix(mSpeedLive.get(), Math.abs(mLive.get() - mPrev.get()) / dt, 0.5));
    }
    hPrev.set(hLive.get());
    mPrev.set(mLive.get());
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    if (!reduced && autoPlay && touching.get() === 0 && openLive.get() < 0.01 && now.get() - lastTouch.get() > 2) {
      const homed = Math.abs(hLive.get() - 8) < 0.01 && Math.abs(mLive.get() - 6) < 0.01 &&
        hSpeedLive.get() < 0.15 && mSpeedLive.get() < 0.15;
      if (homed && fillLive.get() < 0.01) {
        T.set(0);
        homing.set(0);
        auto.set(1);
      } else if (!homed && homing.get() === 0) {
        homing.set(1);
        hLive.set(withSpring(8, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        mLive.set(withSpring(6, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
      }
    }
  });

  const F = useDerivedValue<TrFrame>(() => {
    if (auto.get() === 1) return trFrameAt(T.get());
    return { h: hLive.get(), m: mLive.get(), open: openLive.get(), fill: fillLive.get(), cta: ctaLive.get(), pm: 0, kick: 0, flash: flashLive.get(), hSpeed: hSpeedLive.get(), mSpeed: mSpeedLive.get() };
  });

  // ---- haptics: a selection tick per detent, rate-limited; the carry is the one heavy cue ----
  const buzz = (k: 'sel' | 'light' | 'rigid' | 'success') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else if (k === 'light') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (k === 'rigid') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };
  const lastTick = useSharedValue(0);
  useAnimatedReaction(
    () => (auto.get() === 1 || homing.get() === 1 ? null : Math.round(mLive.get()) * 1000 + Math.round(hLive.get())),
    (v, prev) => {
      if (v === null || prev === null || prev === undefined || v === prev) return;
      if (now.get() - lastTick.get() < 0.1) return;
      lastTick.set(now.get());
      scheduleOnRN(buzz, 'sel');
    },
  );
  // the carry: minutes crossing 55 ↔ 00 move the hour by one
  useAnimatedReaction(
    () => (auto.get() === 1 || homing.get() === 1 ? null : Math.floor((mLive.get() + 0.5) / 12)),
    (v, prev) => {
      if (v === null || prev === null || prev === undefined || v === prev) return;
      const target = Math.round(hLive.get()) + (v - prev);
      hLive.set(withSpring(target, { damping: SPRING.weighty.d, stiffness: SPRING.weighty.k }));
      flashLive.set(1);
      flashLive.set(withTiming(0, { duration: 120 }));
      scheduleOnRN(buzz, 'rigid');
    },
  );

  // ---- geometry ----
  const leftC = useDerivedValue(() => G.leftCx - G.open * Math.max(F.get().open, 0));
  const rightC = useDerivedValue(() => G.rightCx + G.open * Math.max(F.get().open, 0));
  const ringColorL = useDerivedValue(() => (held.get() === 1 ? C.ringHeld : C.ring));
  const ringColorR = useDerivedValue(() => (held.get() === 2 ? C.ringHeld : C.ring));
  const focusScale = useDerivedValue(() => 1 + 0.28 * Math.min(Math.max(F.get().open, 0), 1));
  const neighbourA = useDerivedValue(() => 1 - Math.min(Math.max(F.get().open, 0), 1));
  // on the confirm the pinion rises to y 400 and rounds into a 64 pt sun
  const openK = useDerivedValue(() => Math.min(Math.max(F.get().open, 0), 1));
  const pinionM = useDerivedValue(() => {
    const f = F.get();
    const o = openK.get();
    const rise = 42 * (1 - Math.pow(1 - o, 3));
    const ang = (90 * (f.m - 6 - (f.h - 8)) * Math.PI) / 180;
    const sc = 1 + 0.25 * f.kick;
    return Skia.Matrix().translate(201, G.pinionY + G.wheelY - 495 - rise - 3 * f.kick).rotate(ang).scale(sc, sc);
  });
  const pinionPath = useDerivedValue(() => pinionPathAt(mix(25, 32, openK.get()), 1 - openK.get()));
  const pinionDotA = useDerivedValue(() => 1 - seg(openK.get(), 0, 0.4));
  const sunGlowA = useDerivedValue(() => 0.35 * seg(openK.get(), 0.3, 1));
  const stemA = useDerivedValue(() => 1 - seg(openK.get(), 0, 0.3));
  const ticksL = useDerivedValue(() => Skia.Matrix().translate(leftC.get(), G.wheelY).rotate((F.get().h * G.step * Math.PI) / 180));
  const ticksR = useDerivedValue(() => Skia.Matrix().translate(rightC.get(), G.wheelY).rotate((-F.get().m * G.step * Math.PI) / 180));
  const meshL = useDerivedValue(() => Skia.Matrix().translate(leftC.get(), G.wheelY));
  const meshR = useDerivedValue(() => Skia.Matrix().translate(rightC.get(), G.wheelY));
  const flashA = useDerivedValue(() => F.get().flash);
  const pinionColor = useDerivedValue(() => interpolateColors(seg(F.get().h, 11.5, 12.5) * (F.get().h < 20 ? 1 : 0), [0, 1], [C.pinion, C.pinionPm]));
  const ampm = useDerivedValue(() => {
    const hr = ((Math.round(F.get().h) % 24) + 24) % 24;
    return hr >= 12 ? 1 : 0;
  });
  const ampmK = useDerivedValue(() => withSpring(ampm.get(), { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (homing.get() === 1) {
      cancelAnimation(hLive);
      cancelAnimation(mLive);
      homing.set(0);
    }
    if (auto.get() === 1) {
      const f = trFrameAt(T.get());
      hLive.set(f.h);
      mLive.set(f.m);
      hPrev.set(f.h);
      mPrev.set(f.m);
      hSpeedLive.set(f.hSpeed);
      mSpeedLive.set(f.mSpeed);
      openLive.set(f.open);
      fillLive.set(f.fill);
      ctaLive.set(f.cta);
      ctaLive.set(withTiming(1, { duration: 140 }));
      flashLive.set(f.flash);
      flashLive.set(withTiming(0, { duration: 120 }));
      auto.set(0);
    }
    pinned.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(0);
    held.set(0);
    lastTouch.set(now.get());
  };
  const closeJaws = () => {
    'worklet';
    openLive.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
    fillLive.set(withTiming(0, { duration: 500 }));
  };

  const pan = Gesture.Pan()
    .onBegin((e) => {
      takeOver();
      if (openLive.get() > 0.05) {
        closeJaws();
        return;
      }
      const left = e.x < W / 2;
      held.set(left ? 1 : 2);
      start.set(left ? hLive.get() : mLive.get());
      // grab: stop where it is
      if (left) cancelAnimation(hLive);
      else cancelAnimation(mLive);
      scheduleOnRN(buzz, 'light');
    })
    .onUpdate((e) => {
      if (held.get() === 0) return;
      const per = held.get() === 1 ? (G.hourR * Math.PI * G.step) / 180 : (G.minR * Math.PI * G.step) / 180;
      const v = start.get() + e.translationY / S / per;
      if (held.get() === 1) hLive.set(v);
      else mLive.set(v);
    })
    .onFinalize((e, success) => {
      const side = held.get();
      release();
      if (side === 0) return;
      const per = ((side === 1 ? G.hourR : G.minR) * Math.PI * G.step) / 180;
      const vel = success ? Math.max(-14, Math.min(14, e.velocityY / S / per)) : 0;
      const wheel = side === 1 ? hLive : mLive;
      const cur = wheel.get();
      const target = Math.round(cur + vel * 0.3);
      const spring = { damping: SPRING.detent.d, stiffness: SPRING.detent.k, velocity: vel };
      wheel.set(withSpring(target, spring));
    });

  const onContinue = () => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      if (openLive.get() > 0.5) {
        closeJaws();
      } else {
        openLive.set(withSpring(1, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        fillLive.set(withTiming(1, { duration: 700 }, (finished) => {
          if (finished) scheduleOnRN(buzz, 'success');
        }));
      }
      release();
    });
  };

  // ---- RN layers ----
  const fillSt = useAnimatedStyle(() => ({ width: mix(G.fill[0], G.fill[1], F.get().fill) * S }));
  const ctaSt = useAnimatedStyle(() => ({ transform: [{ scale: F.get().cta }] }));
  // AM/PM grows with the focus time about the screen's centre (201, 495), so it moves out as the time scales up
  // on the confirm AM/PM leaves the minutes and settles under the colon, so the grown time keeps the screen's width
  // (it fades out beside the minutes, moves while hidden, and fades in under the colon)
  const ampmPos = useDerivedValue(() => (openK.get() > 0.4 ? 1 : 0));
  const ampmA = useDerivedValue(() => (openK.get() < 0.4 ? 1 - seg(openK.get(), 0, 0.15) : seg(openK.get(), 0.55, 0.9)));
  const ampmX = useDerivedValue(() => (201 - 17 - (G.ampmX - 2)) * ampmPos.get() * S);
  const ampmY = useDerivedValue(() => 52 * ampmPos.get() * S);
  const amSt = useAnimatedStyle(() => ({ opacity: (1 - seg(ampmK.get(), 0.3, 0.7)) * ampmA.get(), transform: [{ translateX: ampmX.get() }, { translateY: ampmY.get() + 12 * ampmK.get() * S }] }));
  const pmSt = useAnimatedStyle(() => ({ opacity: seg(ampmK.get(), 0.3, 0.7) * ampmA.get(), transform: [{ translateX: ampmX.get() }, { translateY: ampmY.get() - 12 * (1 - ampmK.get()) * S }] }));
  const pageSt = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(openK.get(), [0, 1], [C.page, C.pageWarm]) }));

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const backTop = Math.max(G.back.y * S, insets.top + 8);
  const titleTop = Math.max((G.title - 6) * S, insets.top + 100 * S);
  const ctaTop = Math.min((G.cta.y + 2 * dy) * S, H - insets.bottom - G.cta.h * S);

  return (
    <Animated.View style={[styles.root, pageSt]} onLayout={onLayout}>
      <StatusBar style="dark" />
      {/* back disc and progress */}
      <View style={[styles.back, { left: G.back.x * S, top: backTop, width: G.back.d * S, height: G.back.d * S, borderRadius: (G.back.d / 2) * S }]}>
        <Svg width={18 * S} height={14 * S} viewBox="0 0 18 14">
          <SvgPath d="M17 7H1.5M7 1L1 7l6 6" stroke={C.ink} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </Svg>
      </View>
      <View style={[styles.track, { left: G.track.x * S, top: backTop + 16 * S, width: G.track.w * S, height: G.track.h * S, borderRadius: 6 * S }]}>
        <Animated.View style={[styles.fill, { height: G.track.h * S, borderRadius: 6 * S }, fillSt]} />
      </View>
      <Text allowFontScaling={false} style={[styles.title, fontsOk && styles.tight, { top: titleTop, fontSize: 29.5 * S, lineHeight: 33 * S }]}>
        {'When do you usually\nwake up?'}
      </Text>

      <GestureDetector gesture={pan}>
        <View style={[StyleSheet.absoluteFill, { top: (255 + dy) * S, height: 480 * S }]} />
      </GestureDetector>

      {size && font && (
        <Canvas style={{ position: 'absolute', left: 0, top: dy * S, width: W, height: 874 * S }} pointerEvents="none">
          <Group transform={[{ scale: S }]}>
            <Circle cx={leftC} cy={G.wheelY} r={G.ringR} style="stroke" strokeWidth={G.ringW} color={ringColorL} />
            <Circle cx={rightC} cy={G.wheelY} r={G.ringR} style="stroke" strokeWidth={G.ringW} color={ringColorR} />
            {/* engraved scales: the wheels read as turning even between numerals */}
            <Path path={ticksPath} matrix={ticksL} style="stroke" strokeWidth={1.5} color={C.tick} />
            <Path path={ticksPath} matrix={ticksR} style="stroke" strokeWidth={1.5} color={C.tick} />
            {/* the carry: both rims flash where they mesh with the pinion */}
            <Path path={MESH_L} matrix={meshL} style="stroke" strokeWidth={4} strokeCap="round" color={C.rimFlash} opacity={flashA} />
            <Path path={MESH_R} matrix={meshR} style="stroke" strokeWidth={4} strokeCap="round" color={C.rimFlash} opacity={flashA} />
            <Line p1={vec(201, G.wheelY - 8)} p2={vec(201, G.wheelY + 8)} color={C.seam} strokeWidth={1} opacity={neighbourA} />
            {SLOTS.map((j) => (
              <Numeral key={`h${j}`} side={0} j={j} F={F} font={font} cx={leftC} focusScale={focusScale} neighbourA={neighbourA} />
            ))}
            {SLOTS.map((j) => (
              <Numeral key={`m${j}`} side={1} j={j} F={F} font={font} cx={rightC} focusScale={focusScale} neighbourA={neighbourA} />
            ))}
            {/* colon */}
            <Group transform={[{ translateX: 201 }, { translateY: G.wheelY }]}>
              <Group transform={[{ scale: 1 }]}>
                <Circle cx={0} cy={G.colonY[0] - 495} r={4.5} color={C.ink} />
                <Circle cx={0} cy={G.colonY[1] - 495} r={4.5} color={C.ink} />
              </Group>
            </Group>
            {/* pinion with its stem */}
            <Line p1={vec(201, 465)} p2={vec(201, 481)} color={C.pinion} strokeWidth={1} opacity={stemA} />
            <Group matrix={pinionM}>
              <Circle cx={0} cy={0} r={46} color={C.pinion} opacity={sunGlowA}>
                <Blur blur={18} />
              </Circle>
              <Path path={pinionPath} color="rgba(138,100,0,0.18)" transform={[{ translateY: 1 }]}>
                <Blur blur={1} />
              </Path>
              <Path path={pinionPath} color={pinionColor} />
              <Path path={pinionPath} style="stroke" strokeWidth={1} color="rgba(255,210,74,0.6)" />
              <Circle cx={19} cy={0} r={1.5} color={C.pinionDot} opacity={pinionDotA} />
            </Group>
          </Group>
        </Canvas>
      )}

      {/* AM / PM */}
      {(
        <View pointerEvents="none" style={{ position: 'absolute', left: (G.ampmX - 2) * S, top: (G.ampmY - 4 + dy) * S }}>
          <Animated.Text allowFontScaling={false} style={[styles.ampm, fontsOk && styles.onest, { fontSize: 23 * S }, amSt]}>
            AM
          </Animated.Text>
          <Animated.Text allowFontScaling={false} style={[styles.ampm, styles.pm, fontsOk && styles.onest, { fontSize: 23 * S }, pmSt]}>
            PM
          </Animated.Text>
        </View>
      )}

      <Animated.View style={[styles.ctaWrap, { left: G.cta.x * S, top: ctaTop, width: G.cta.w * S, height: G.cta.h * S }, ctaSt]}>
        <Pressable onPress={onContinue} style={({ pressed }) => [styles.cta, { borderRadius: G.cta.r * S, backgroundColor: pressed ? C.ctaPressed : C.cta, paddingHorizontal: 18 * S }]}>
          <Text allowFontScaling={false} style={[styles.ctaLabel, fontsOk && styles.tight, { fontSize: 20 * S }]}>
            Continue
          </Text>
          <Svg width={24 * S} height={14 * S} viewBox="0 0 24 14">
            <SvgPath d="M1.5 7H22M16 1.5L22 7l-6 5.5" stroke={C.ctaInk} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

type NumeralProps = {
  side: 0 | 1;
  j: number;
  F: { get: () => TrFrame };
  font: ReturnType<typeof useFont>;
  cx: { get: () => number };
  focusScale: { get: () => number };
  neighbourA: { get: () => number };
};

/** One numeral slot on a wheel: j steps from the value nearest the focus row. */
function Numeral({ side, j, F, font, cx, focusScale, neighbourA }: NumeralProps) {
  const v = useDerivedValue(() => Math.round(side === 0 ? F.get().h : F.get().m) + j);
  const label = useDerivedValue(() => (side === 0 ? hourLabel(v.get()) : minLabel(v.get())));
  const look = useDerivedValue(() => {
    const f = F.get();
    const val = side === 0 ? f.h : f.m;
    const k = v.get() - val; // >0: above the focus row
    const fo = falloff(k);
    const ang = side === 0 ? (-k * G.step * Math.PI) / 180 : Math.PI + (k * G.step * Math.PI) / 180;
    const R = side === 0 ? G.hourR : G.minR;
    const x = cx.get() + Math.cos(ang) * R;
    const y = G.wheelY + Math.sin(ang) * R;
    const rot = side === 0 ? ang : ang - Math.PI;
    const focusK = 1 - Math.min(fo.a, 1);
    const sc = fo.scale * mix(1, focusScale.get(), focusK);
    const alpha = fo.alpha * mix(neighbourA.get(), 1, focusK > 0.5 ? 1 : 0);
    const speed = side === 0 ? f.hSpeed : f.mSpeed;
    const motion = Math.min(14, speed * 2.4);
    return { x, y, rot, sc, alpha, blurX: fo.blur, blurY: fo.blur + motion, a: fo.a };
  });
  const m = useDerivedValue(() => {
    const l = look.get();
    return Skia.Matrix().translate(l.x, l.y).rotate(l.rot).scale(l.sc, l.sc);
  });
  const color = useDerivedValue(() => {
    const a = look.get().a;
    return interpolateColors(Math.min(a, 3), [0, 1, 2, 3], [C.ink, C.k1, C.k2, C.k3]);
  });
  const opacity = useDerivedValue(() => look.get().alpha);
  // Local y follows the tangent of the wheel after the numeral's rotation.
  const blur = useDerivedValue(() => vec(Math.max(look.get().blurX, 0.0001), Math.max(look.get().blurY, 0.0001)));
  // widths measured once on the JS thread (a Skia font cannot be called from the UI thread)
  const widths = useMemo(() => {
    const out: Record<string, number> = {};
    if (!font) return out;
    for (let v = 0; v < 12; v++) {
      for (const l of [hourLabel(v + 1), minLabel(v)]) out[l] = font.getGlyphWidths(font.getGlyphIDs(l)).reduce((s2, g) => s2 + g, 0);
    }
    return out;
  }, [font]);
  const x = useDerivedValue(() => -(widths[label.get()] ?? 60) / 2);
  if (!font) return null;
  return (
    <Group matrix={m} opacity={opacity}>
      <SkText text={label} font={font} x={x} y={20} color={color}>
        <Blur blur={blur} mode="decal" />
      </SkText>
    </Group>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
  back: { position: 'absolute', backgroundColor: C.disc, alignItems: 'center', justifyContent: 'center' },
  track: { position: 'absolute', backgroundColor: C.track, overflow: 'hidden' },
  fill: { backgroundColor: C.ink },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: C.ink, fontWeight: '600', letterSpacing: -1 },
  ampm: { color: C.ink, fontWeight: '800', letterSpacing: -0.5 },
  onest: { fontFamily: 'TR-Onest-ExtraBold', fontWeight: undefined },
  tight: { fontFamily: 'TR-InterTight-SemiBold', fontWeight: undefined },
  pm: { position: 'absolute', left: 0, top: 0 },
  ctaWrap: { position: 'absolute' },
  cta: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  ctaLabel: { color: C.ctaInk, fontWeight: '600' },
});
