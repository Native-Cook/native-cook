import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  interpolateColor,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, Path, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  CLOSE_MS,
  EASE_IN_OUT,
  easeOut,
  FLOAT,
  PARALLAX,
  ROLL_DEG,
  ORBIT_SPRING,
  SWELL,
  RING_DEG,
  HANDOVER,
  MAX_STEP_MS,
  GLOW,
  LOOP_MS,
  OPEN_MS,
  PRESS_IN_MS,
  PRESS_OUT_MS,
  PRESS_SCALE,
  PROJECT_S,
  REDUCED_FADE_MS,
  RESUME_AFTER_MS,
  RISE,
  ROCKET_FROM,
  RUBBER,
  SCRIPT,
  SNAP,
  WINDOWS,
} from './motion';
import { FRIENDS, ME, PLANETS, ROCKET, START_INDEX, VIDEO } from './planets';
import { COLORS, DECOR, DETAIL, FONT, LIST } from './theme';
import { useReduceTransparency } from './use-reduce-transparency';
import { useResume } from './use-resume';

const inOut = Easing.bezierFn(...EASE_IN_OUT);
const outFn = Easing.bezierFn(0.23, 1, 0.32, 1);

function win(v: number, w: readonly [number, number]) {
  'worklet';
  const t = (v - w[0]) / (w[1] - w[0]);
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

/** Autoplay: carousel position, detail progress and pressed arrow/back as pure functions of the loop clock. */
function sample(tIn: number) {
  'worklet';
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  const a = inOut(win(t, SCRIPT.toVenus));
  const b = inOut(win(t, SCRIPT.toEarth));
  const pos = START_INDEX + a - b;
  let detail = 0;
  if (t >= SCRIPT.open && t < SCRIPT.close) detail = win(t, [SCRIPT.open, SCRIPT.open + OPEN_MS]);
  else if (t >= SCRIPT.close) detail = 1 - win(t, [SCRIPT.close, SCRIPT.close + CLOSE_MS]);
  const pressArrow = t >= SCRIPT.press && t < SCRIPT.press + 140 ? 1 : 0;
  const pressBack = t >= SCRIPT.back && t < SCRIPT.back + 140 ? 1 : 0;
  return { pos, detail, pressArrow, pressBack };
}

export function PlanetExplorerCarousel({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  'use no memo';

  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const glow = !useReduceTransparency();
  const [loaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
  });

  const pos = useSharedValue<number>(START_INDEX);
  const detail = useSharedValue(0);
  const sel = useSharedValue<number>(START_INDEX);
  const arrowPress = useSharedValue(1);
  const backPress = useSharedValue(1);
  const clock = useSharedValue(0);
  const canAutoPlay = autoPlay && !reduced && freezeAt === undefined;
  const mode = useSharedValue(canAutoPlay ? 0 : 1);
  const loopStart = useSharedValue(-1);
  const dragStart = useSharedValue(0);
  // the planets' own position: a spring that follows `pos` (see motion.ts)
  const orbit = useSharedValue<number>(START_INDEX);
  const orbitVel = useSharedValue(0);
  const orbitT = useSharedValue(-1);

  /** Advance the planets' spring to the carousel position `target` at time `now` (fixed small steps). */
  const stepOrbit = useCallback((target: number, now: number) => {
    'worklet';
    if (reduced) {
      orbit.set(target);
      orbitVel.set(0);
      orbitT.set(now);
      return;
    }
    let last = orbitT.get();
    if (last < 0 || now < last || now - last > 1000) {
      orbit.set(target);
      orbitVel.set(0);
      orbitT.set(now);
      return;
    }
    let x = orbit.get();
    let v = orbitVel.get();
    while (last < now) {
      const dt = Math.min(MAX_STEP_MS, now - last) / 1000;
      const a = ORBIT_SPRING.stiffness * (target - x) - ORBIT_SPRING.damping * v;
      v += a * dt;
      x += v * dt;
      last += dt * 1000;
    }
    orbit.set(x);
    orbitVel.set(v);
    orbitT.set(now);
  }, [orbit, orbitT, orbitVel, reduced]);

  const frameDriver = useFrameCallback(useCallback((info: { timestamp: number }) => {
    'worklet';
    clock.set(info.timestamp);
    if (mode.get() !== 0) {
      stepOrbit(pos.get(), info.timestamp);
      return;
    }
    if (loopStart.get() < 0) loopStart.set(info.timestamp);
    const s = sample(info.timestamp - loopStart.get());
    // the loop restarts at rest, where the spring is at rest too
    if (info.timestamp - loopStart.get() < 32) orbitT.set(-1);
    pos.set(s.pos);
    detail.set(s.detail);
    sel.set(START_INDEX);
    arrowPress.set(s.pressArrow ? PRESS_SCALE : 1);
    backPress.set(s.pressBack ? PRESS_SCALE : 1);
    stepOrbit(s.pos, info.timestamp - loopStart.get());
  }, [arrowPress, backPress, clock, detail, loopStart, mode, orbitT, pos, sel, stepOrbit]), false);

  useEffect(() => {
    frameDriver.setActive(freezeAt === undefined && !reduced);
    scheduleOnUI(() => {
      'worklet';
      for (const value of [pos, detail, arrowPress, backPress]) cancelAnimation(value);
      loopStart.set(-1);
      orbitT.set(-1);
      mode.set(canAutoPlay ? 0 : 1);
      if (freezeAt !== undefined) {
        // Sample the deterministic spring once, with no frozen frame driver.
        const step = 1000 / 120;
        for (let t = 0; t <= freezeAt; t += step) stepOrbit(sample(t).pos, t);
        const pose = sample(freezeAt);
        clock.set(freezeAt);
        pos.set(pose.pos);
        detail.set(pose.detail);
        sel.set(START_INDEX);
        arrowPress.set(pose.pressArrow ? PRESS_SCALE : 1);
        backPress.set(pose.pressBack ? PRESS_SCALE : 1);
      } else if (reduced) {
        const selected = Math.max(0, Math.min(PLANETS.length - 1, Math.round(pos.get())));
        pos.set(selected);
        sel.set(selected);
        detail.set(detail.get() >= 0.5 ? 1 : 0);
        orbit.set(selected);
        orbitVel.set(0);
        clock.set(0);
        arrowPress.set(1);
        backPress.set(1);
      }
    });
    return () => frameDriver.setActive(false);
  }, [arrowPress, backPress, canAutoPlay, clock, detail, frameDriver, freezeAt, loopStart, mode, orbit, orbitT, orbitVel, pos, reduced, sel, stepOrbit]);

  useAnimatedReaction(
    () => reduced && freezeAt === undefined ? pos.get() : null,
    (position) => {
      if (position === null) return;
      orbit.set(position);
      orbitVel.set(0);
    },
  );

  const [selIndex, setSelIndex] = useState(START_INDEX);
  const [inDetail, setInDetail] = useState(false);
  useAnimatedReaction(
    () => sel.get(),
    (v, was) => {
      if (v !== was) scheduleOnRN(setSelIndex, v);
    },
  );
  useAnimatedReaction(
    () => detail.get() > 0.5,
    (on, was) => {
      if (on !== was) scheduleOnRN(setInDetail, on);
    },
  );

  const toRest = useCallback(() => {
    if (!canAutoPlay) return;
    detail.set(withTiming(0, { duration: CLOSE_MS }));
    pos.set(
      withTiming(START_INDEX, { duration: 500, easing: easeOut }, (done) => {
        if (!done) return;
        loopStart.set(-1);
        mode.set(0);
      }),
    );
  }, [canAutoPlay, detail, loopStart, mode, pos]);
  const { hold, release } = useResume(canAutoPlay, RESUME_AFTER_MS, toRest);
  const grab = useCallback(() => {
    mode.set(1);
    hold();
  }, [hold, mode]);
  const light = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, []);

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .enabled(freezeAt === undefined)
        .maxPointers(1)
        .activeOffsetX([-10, 10])
        .onBegin(() => {
          scheduleOnRN(grab);
          cancelAnimation(pos);
          dragStart.set(pos.get());
        })
        .onUpdate((e) => {
          const max = PLANETS.length - 1;
          let p = dragStart.get() - e.translationX / LIST.card.spacing;
          if (p < 0) p *= RUBBER;
          if (p > max) p = max + (p - max) * RUBBER;
          pos.set(p);
        })
        .onEnd((e) => {
          const max = PLANETS.length - 1;
          const projected = pos.get() - (e.velocityX * PROJECT_S) / LIST.card.spacing;
          const target = Math.max(0, Math.min(max, Math.round(projected)));
          pos.set(reduced ? target : withSpring(target, { ...SNAP, velocity: -e.velocityX / LIST.card.spacing }));
          if (target !== Math.round(dragStart.get())) scheduleOnRN(light);
        })
        .onFinalize(() => scheduleOnRN(release)),
    [dragStart, freezeAt, grab, light, pos, reduced, release],
  );

  const press = (sv: SharedValue<number>, down: boolean) => {
    sv.set(reduced ? 1 : withTiming(down ? PRESS_SCALE : 1, { duration: down ? PRESS_IN_MS : PRESS_OUT_MS, easing: easeOut }));
  };
  const openDetail = () => {
    grab();
    const i = Math.max(0, Math.min(PLANETS.length - 1, Math.round(pos.get())));
    sel.set(i);
    pos.set(reduced ? i : withSpring(i, SNAP));
    detail.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : OPEN_MS * (1 - detail.get()) }));
    light();
    release();
  };
  const closeDetail = () => {
    grab();
    detail.set(withTiming(0, { duration: reduced ? REDUCED_FADE_MS : CLOSE_MS * detail.get() }));
    release();
  };

  const listStyle = useAnimatedStyle(() => {
    const v = win(detail.get(), WINDOWS.list);
    return { opacity: 1 - v, transform: [{ translateY: reduced ? 0 : RISE * v }] };
  });
  const backdropStyle = useAnimatedStyle(() => ({ opacity: win(detail.get(), WINDOWS.backdrop) }));
  const arrowStyle = useAnimatedStyle(() => ({ transform: [{ scale: arrowPress.get() }] }));
  const backStyle = useAnimatedStyle(() => ({ transform: [{ scale: backPress.get() }] }));

  // The selected planet travels from its card to the top of the detail view.
  const cardPlanetY = LIST.planet.top + LIST.planet.size / 2;
  const morphStyle = useAnimatedStyle(() => {
    const d = detail.get();
    if (d <= 0) return { opacity: 0 };
    if (reduced) return { opacity: win(d, [0.3, 0.7]), transform: [{ translateX: DETAIL.earth.cx }, { translateY: DETAIL.earth.cy }, { scale: DETAIL.earth.size / LIST.planet.size }] };
    const e = inOut(win(d, WINDOWS.earth));
    const float = Math.sin((clock.get() / FLOAT.periodMs) * Math.PI * 2) * FLOAT.earth * win(d, [0.9, 1]);
    return {
      opacity: 1,
      transform: [
        { translateX: interpolate(e, [0, 1], [width / 2, DETAIL.earth.cx]) },
        { translateY: interpolate(e, [0, 1], [cardPlanetY, DETAIL.earth.cy]) + float },
        { scale: interpolate(e, [0, 1], [1, DETAIL.earth.size / LIST.planet.size]) },
      ],
    };
  });

  const backIn = useStagger(detail, WINDOWS.back, reduced);
  const layer = (k: number) => () => {
    'worklet';
    return { transform: [{ translateX: reduced ? 0 : -(pos.get() - START_INDEX) * k }] };
  };
  const starStyle = useAnimatedStyle(layer(PARALLAX.stars));
  // the dashed orbit turns about its own centre as the planets travel along it
  const ringStyle = useAnimatedStyle(() => {
    const deg = reduced ? 0 : (orbit.get() - START_INDEX) * RING_DEG;
    return {
      transform: [
        { translateX: LIST.orbit.cx - width / 2 },
        { translateY: LIST.orbit.cy - height / 2 },
        { rotate: `${deg}deg` },
        { translateX: width / 2 - LIST.orbit.cx },
        { translateY: height / 2 - LIST.orbit.cy },
      ],
    };
  });
  const dotStyle = useAnimatedStyle(layer(PARALLAX.dots));

  if (!loaded) return <View style={[styles.screen, { backgroundColor: COLORS.skyMid }]} />;
  const planet = PLANETS[selIndex];

  return (
    <View style={styles.screen}>
      <StatusBar style={inDetail ? 'dark' : 'light'} />

      {/* ---- list ---- */}
      <Animated.View style={[StyleSheet.absoluteFill, listStyle]} pointerEvents={inDetail ? 'none' : 'auto'}>
        <LinearGradient
          colors={[COLORS.skyTop, COLORS.skyMid, COLORS.skyLow, COLORS.skyBottom]}
          locations={[0.12, 0.35, 0.8, 1]}
          style={StyleSheet.absoluteFill}
        />
        <Animated.View style={[StyleSheet.absoluteFill, ringStyle]} pointerEvents="none">
          <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
            <Circle cx={LIST.orbit.cx} cy={LIST.orbit.cy} r={LIST.orbit.r} stroke={COLORS.orbit} strokeWidth={1} strokeDasharray="3 6" fill="none" />
          </Svg>
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, starStyle]} pointerEvents="none">
          <Svg width={width + PARALLAX.pad * 2} height={height} style={{ marginLeft: -PARALLAX.pad }}>
            {DECOR.stars.map(([x, y]) => (
              <Circle key={`${x}-${y}`} cx={x + PARALLAX.pad} cy={y} r={1.4} fill="rgba(255,255,255,0.75)" />
            ))}
          </Svg>
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, dotStyle]} pointerEvents="none">
          <Svg width={width + PARALLAX.pad * 2} height={height} style={{ marginLeft: -PARALLAX.pad }}>
            {DECOR.dots.map((d) => (
              <Circle key={`${d.x}-${d.y}`} cx={d.x + PARALLAX.pad} cy={d.y} r={d.r} fill={d.c} />
            ))}
          </Svg>
        </Animated.View>
        <View style={styles.menu} accessibilityRole="button" accessibilityLabel="Menu">
          <View style={[styles.menuLine, { width: 30 }]} />
          <View style={[styles.menuLine, { width: 22 }]} />
          <View style={[styles.menuLine, { width: 30 }]} />
        </View>
        <Image source={ME} style={styles.me} accessibilityLabel="Your profile" />
        <View style={styles.title} accessible accessibilityRole="header" accessibilityLabel="Space Explorer">
          <Text style={styles.titleSpace}>Space</Text>
          <Text style={styles.titleExplorer}>Explorer</Text>
        </View>

        <GestureDetector gesture={pan}>
          <View style={styles.carousel}>
            {PLANETS.map((p, i) => (
              <PlanetCard key={p.name} index={i} pos={pos} orbit={orbit} detail={detail} sel={sel} width={width} glow={glow} />
            ))}
          </View>
        </GestureDetector>

        <Pressable
          style={styles.arrowHit}
          accessibilityRole="button"
          accessibilityLabel="Explore this planet"
          onPressIn={() => press(arrowPress, true)}
          onPressOut={() => press(arrowPress, false)}
          onPress={openDetail}
        >
          <Animated.View style={[styles.ring, arrowStyle]}>
            <LinearGradient colors={[COLORS.yellow, COLORS.orange]} style={styles.button}>
              <Svg width={24} height={24} viewBox="0 0 24 24">
                <Path d="M4 12h15M13 5.5 19.5 12 13 18.5" stroke={COLORS.white} strokeWidth={2.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
              </Svg>
            </LinearGradient>
          </Animated.View>
        </Pressable>

        <View style={styles.friends}>
          {FRIENDS.map((f, i) => (
            <Image key={i} source={f} style={[styles.friend, { marginLeft: i === 0 ? 0 : -LIST.friends.overlap, zIndex: 4 - i }]} />
          ))}
        </View>
        <Text style={styles.caption}>Joey, Linn and 8 friends are online</Text>
      </Animated.View>

      {/* ---- detail ---- */}
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]} pointerEvents="none">
        <LinearGradient colors={[COLORS.detailTop, COLORS.detailBottom]} style={StyleSheet.absoluteFill} />
        <LinearGradient
          colors={['#A29FB3', '#C1BECE', '#E3E2E8', '#FFFFFF', '#FFFFFF']}
          locations={[0, 0.1, 0.2, 0.3, 1]}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
          style={StyleSheet.absoluteFill}
        />
        <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
          <Circle cx={DETAIL.earth.cx + 10} cy={DETAIL.earth.cy + 180} r={250} stroke={COLORS.orbitDark} strokeWidth={1} strokeDasharray="3 6" fill="none" />
        </Svg>
      </Animated.View>
      <Detail detail={detail} reduced={reduced} planet={planet} clock={clock} width={width} />

      <Animated.View style={[styles.morph, morphStyle]} pointerEvents="none">
        <Atmosphere color={planet.glow} glow={glow} />
        <Image source={planet.image} style={styles.morphImage} />
      </Animated.View>
      <DetailFront detail={detail} reduced={reduced} clock={clock} />

      <Animated.View style={[styles.back, backIn]} pointerEvents={inDetail ? 'auto' : 'none'}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back"
          hitSlop={12}
          onPressIn={() => press(backPress, true)}
          onPressOut={() => press(backPress, false)}
          onPress={closeDetail}
        >
          <Animated.View style={backStyle}>
            <Svg width={26} height={26} viewBox="0 0 26 26">
              <Path d="M22 13H4.5M11 6 4 13l7 7" stroke={COLORS.navy} strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
          </Animated.View>
        </Pressable>
      </Animated.View>
    </View>
  );
}

/** Atmospheric glow: a soft radial halo in the planet's own colour, drawn behind it. */
function Atmosphere({ color, glow }: { color: string; glow: boolean }) {
  if (!glow) return null;
  const s = LIST.planet.size * GLOW.scale;
  return (
    <Svg width={s} height={s} style={[styles.atmosphere, { left: (LIST.planet.size - s) / 2, top: (LIST.planet.size - s) / 2 }]}>
      <Defs>
        <RadialGradient id={`atm-${color.slice(1)}`} cx="50%" cy="50%" r="50%">
          <Stop offset={GLOW.inner} stopColor={color} stopOpacity={GLOW.opacity} />
          <Stop offset="1" stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Circle cx={s / 2} cy={s / 2} r={s / 2} fill={`url(#atm-${color.slice(1)})`} />
    </Svg>
  );
}

function useStagger(detail: SharedValue<number>, w: readonly [number, number], reduced: boolean) {
  return useAnimatedStyle(() => {
    const v = outFn(win(detail.get(), w));
    return { opacity: v, transform: [{ translateY: reduced ? 0 : RISE * (1 - v) }] };
  });
}

function PlanetCard({
  index,
  pos,
  orbit,
  detail,
  sel,
  width,
  glow,
}: {
  glow: boolean;
  index: number;
  pos: SharedValue<number>;
  orbit: SharedValue<number>;
  detail: SharedValue<number>;
  sel: SharedValue<number>;
  width: number;
}) {
  const p = PLANETS[index];
  const mid = width / 2;
  const amount = (v: number) => {
    'worklet';
    return Math.max(0, 1 - Math.abs(index - v));
  };
  /**
   * Handover depth: while the carousel travels, the card ahead of the motion (incoming) is +1 and passes in front,
   * the card it covers (outgoing) is -1 and recedes. `bell` peaks where the two cards meet. Both are 0 at rest.
   */
  const depth = () => {
    'worklet';
    const p = pos.get();
    const travel = Math.max(-1, Math.min(1, (p - orbit.get()) * HANDOVER.dirGain));
    const rel = index - p;
    const ahead = rel === 0 ? 0 : (rel > 0 ? 1 : -1) * travel;
    const a = amount(p);
    const bell = 4 * a * (1 - a);
    return { ahead, bell, a };
  };
  const frame = useAnimatedStyle(() => {
    const d = depth();
    const out = Math.max(0, -d.ahead) * d.bell;
    const inc = Math.max(0, d.ahead) * d.bell;
    return {
      zIndex: Math.round(d.a * 10 + d.ahead * HANDOVER.zLift),
      transform: [
        { translateX: mid + (index - pos.get()) * LIST.card.spacing - LIST.card.w / 2 },
        { scale: 1 - (1 - HANDOVER.recede) * out + HANDOVER.lift * inc },
      ],
    };
  });
  const shade = useAnimatedStyle(() => {
    const d = depth();
    return {
      top: interpolate(d.a, [0, 1], [LIST.side.top, LIST.card.top]) - LIST.card.top - LIST.card.backgroundLift,
      height: interpolate(d.a, [0, 1], [LIST.side.h, LIST.card.h]) + LIST.card.backgroundLift,
      backgroundColor: COLORS.skyLow,
      opacity: HANDOVER.shade * Math.max(0, -d.ahead) * d.bell,
    };
  });
  const bg = useAnimatedStyle(() => {
    const a = amount(pos.get());
    return {
      top: interpolate(a, [0, 1], [LIST.side.top, LIST.card.top]) - LIST.card.top - LIST.card.backgroundLift,
      height: interpolate(a, [0, 1], [LIST.side.h, LIST.card.h]) + LIST.card.backgroundLift,
      backgroundColor: interpolateColor(a, [0, 1], [COLORS.side, COLORS.white]),
    };
  });
  const planet = useAnimatedStyle(() => {
    // the planet rides its spring: it lags its card, overshoots a little and settles
    const o = orbit.get();
    const a = amount(o);
    const side = index - o > 0 ? -1 : 1;
    const lag = (pos.get() - o) * LIST.card.spacing;
    // the incoming planet leads its card (parallax), the outgoing one drifts further behind
    const d = depth();
    const k = d.ahead > 0 ? -HANDOVER.lead * d.ahead : 1 + HANDOVER.trail * Math.max(0, -d.ahead);
    const size = interpolate(a, [0, 1], [LIST.sidePlanet.size, LIST.planet.size]);
    const hidden = detail.get() > 0 && sel.get() === index;
    return {
      opacity: hidden ? 0 : 1,
      transform: [
        { translateX: LIST.card.w / 2 + side * 46 * (1 - a) + lag * k },
        { translateY: interpolate(a, [0, 1], [LIST.side.top + LIST.sidePlanet.dy, LIST.planet.top + LIST.planet.size / 2]) - LIST.card.top },
        { scale: size / LIST.planet.size },
        // planets roll as they travel, like globes on a track
        { rotate: `${ROLL_DEG * (index - o)}deg` },
      ],
    };
  });
  // the atmosphere swells while the planet is pulled along and relaxes as it settles
  const swell = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + Math.min(SWELL.max, Math.abs(pos.get() - orbit.get()) * SWELL.gain * SWELL.max) }],
  }));
  // the outgoing card's copy is gone before the incoming card covers it
  const coverFade = () => {
    'worklet';
    const d = depth();
    const c = Math.max(0, -d.ahead) * d.bell;
    return 1 - Math.max(0, Math.min(1, (c - HANDOVER.fadeFrom) / HANDOVER.fadeSpan));
  };
  const text = useAnimatedStyle(() => ({ opacity: coverFade(), transform: [{ translateY: 20 * (1 - amount(pos.get())) }] }));
  const nameStyle = useAnimatedStyle(() => ({ color: interpolateColor(amount(pos.get()), [0, 1], [COLORS.sideText, COLORS.navy]) }));
  const descStyle = useAnimatedStyle(() => ({ color: interpolateColor(amount(pos.get()), [0, 1], [COLORS.sideBody, COLORS.body]) }));
  const numStyle = useAnimatedStyle(() => ({ opacity: amount(pos.get()) * coverFade() }));

  return (
    <Animated.View style={[styles.cardFrame, frame]} accessibilityLabel={`${p.name}, planet ${p.order}`}>
      <Animated.View style={[styles.cardBg, bg]} />
      <Animated.View style={[styles.cardBg, shade]} pointerEvents="none" />
      <Animated.Text style={[styles.numeral, numStyle]}>{p.order}</Animated.Text>
      <Animated.View style={[styles.cardText, text]}>
        <Animated.Text style={[styles.name, nameStyle]}>{p.name}</Animated.Text>
        <Animated.Text style={[styles.desc, descStyle]} numberOfLines={3}>
          {p.short}
        </Animated.Text>
      </Animated.View>
      <Animated.View style={[styles.cardPlanet, planet]} pointerEvents="none">
        <Animated.View style={[StyleSheet.absoluteFill, swell]}>
          <Atmosphere color={p.glow} glow={glow} />
        </Animated.View>
        <Image source={p.image} style={styles.morphImage} />
      </Animated.View>
    </Animated.View>
  );
}

function Detail({
  detail,
  reduced,
  planet,
  width,
}: {
  detail: SharedValue<number>;
  reduced: boolean;
  planet: (typeof PLANETS)[number];
  clock: SharedValue<number>;
  width: number;
}) {
  const numeral = useStagger(detail, WINDOWS.numeral, reduced);
  const name = useStagger(detail, WINDOWS.name, reduced);
  const desc = useStagger(detail, WINDOWS.desc, reduced);
  const video = useStagger(detail, WINDOWS.video, reduced);
  const [before, after] = planet.long.split(planet.accent);
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <Animated.View style={[styles.block, video]} />
      <Animated.Text style={[styles.bigNumeral, numeral]}>{planet.order}</Animated.Text>
      <Animated.View style={[StyleSheet.absoluteFill, desc]}>
        <Svg width={width} height={DETAIL.video.top} style={StyleSheet.absoluteFill}>
          <Circle cx={DETAIL.dots.green.x} cy={DETAIL.dots.green.y} r={DETAIL.dots.green.r} fill={COLORS.green} />
          <Circle cx={DETAIL.dots.yellow.x} cy={DETAIL.dots.yellow.y} r={DETAIL.dots.yellow.r} fill={COLORS.yellow} />
          <Circle cx={DETAIL.dots.blue.x} cy={DETAIL.dots.blue.y} r={DETAIL.dots.blue.r} fill={COLORS.dotBlue} />
        </Svg>
      </Animated.View>
      <Animated.Text style={[styles.detailName, name]}>{planet.name}</Animated.Text>
      <Animated.Text style={[styles.detailDesc, desc]}>
        {before}
        <Text style={styles.accent}>{planet.accent}</Text>
        {after}
      </Animated.Text>
      <Animated.View style={[styles.video, video]}>
        <Image source={VIDEO} style={StyleSheet.absoluteFill} contentFit="cover" />
        <View style={styles.play}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <Path d="M6.5 4.5v11L15.5 10z" fill={COLORS.white} />
          </Svg>
        </View>
      </Animated.View>
    </View>
  );
}

/** The rocket flies in over the planet once it has landed, then idles. */
function DetailFront({ detail, reduced, clock }: { detail: SharedValue<number>; reduced: boolean; clock: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = outFn(win(detail.get(), WINDOWS.rocket));
    if (reduced) return { opacity: v };
    const bob = Math.sin((clock.get() / FLOAT.periodMs) * Math.PI * 2 + 1) * FLOAT.rocket * win(detail.get(), [0.95, 1]);
    return {
      opacity: v,
      transform: [
        { translateX: ROCKET_FROM.x * (1 - v) },
        { translateY: ROCKET_FROM.y * (1 - v) + bob },
        { rotate: `${ROCKET_FROM.rotate * (1 - v)}deg` },
      ],
    };
  });
  return (
    <Animated.View style={[styles.rocket, style]} pointerEvents="none">
      <Image source={ROCKET} style={styles.morphImage} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.detailTop, overflow: 'hidden' },
  menu: { position: 'absolute', top: LIST.menu.top, left: LIST.menu.left, gap: 6, padding: 4, margin: -4 },
  menuLine: { height: 2.6, borderRadius: 1.3, backgroundColor: COLORS.white },
  me: {
    position: 'absolute',
    top: LIST.avatar.top,
    right: LIST.avatar.right,
    width: LIST.avatar.size,
    height: LIST.avatar.size,
    borderRadius: LIST.avatar.size / 2,
  },
  title: {
    position: 'absolute',
    top: 90,
    left: LIST.title.left,
  },
  titleSpace: {
    position: 'absolute',
    top: 9,
    left: 1,
    fontFamily: FONT.semibold,
    fontSize: 60,
    lineHeight: 64,
    letterSpacing: -0.5,
    color: COLORS.white,
  },
  titleExplorer: {
    position: 'absolute',
    top: 63,
    left: 7,
    fontFamily: FONT.semibold,
    fontSize: 50,
    lineHeight: LIST.title.lineHeight,
    letterSpacing: -0.5,
    color: COLORS.white,
    transform: [{ scaleX: 1.067 }],
  },
  carousel: { position: 'absolute', top: LIST.planet.top, left: 0, right: 0, height: LIST.card.top + LIST.card.h - LIST.planet.top },
  cardFrame: { position: 'absolute', top: LIST.card.top - LIST.planet.top, left: 0, width: LIST.card.w, height: LIST.card.h },
  cardBg: { position: 'absolute', left: 0, right: 0, borderRadius: LIST.card.radius, borderCurve: 'continuous' },
  numeral: {
    position: 'absolute',
    top: LIST.numeral.top,
    right: LIST.numeral.right,
    fontFamily: FONT.semibold,
    fontSize: LIST.numeral.size,
    lineHeight: LIST.numeral.size * 1.1,
    color: COLORS.numeral,
  },
  cardText: { position: 'absolute', top: LIST.name.top, left: LIST.name.left, right: 22 },
  name: { fontFamily: FONT.semibold, fontSize: LIST.name.size, lineHeight: LIST.name.size * 1.3 },
  desc: { marginTop: LIST.desc.top - LIST.name.top - LIST.name.size * 1.3, fontFamily: FONT.regular, fontSize: LIST.desc.size, lineHeight: LIST.desc.lineHeight },
  cardPlanet: { position: 'absolute', top: -LIST.planet.size / 2, left: -LIST.planet.size / 2, width: LIST.planet.size, height: LIST.planet.size },
  arrowHit: { position: 'absolute', top: LIST.button.cy - LIST.button.ring / 2, alignSelf: 'center' },
  ring: {
    width: LIST.button.ring,
    height: LIST.button.ring,
    borderRadius: LIST.button.ring / 2,
    backgroundColor: COLORS.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: { width: LIST.button.size, height: LIST.button.size, borderRadius: LIST.button.size / 2, alignItems: 'center', justifyContent: 'center' },
  friends: { position: 'absolute', top: LIST.friends.cy - LIST.friends.size / 2, alignSelf: 'center', flexDirection: 'row' },
  friend: { width: LIST.friends.size, height: LIST.friends.size, borderRadius: LIST.friends.size / 2 },
  caption: { position: 'absolute', top: LIST.caption.top, left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: LIST.caption.size, color: COLORS.caption },
  morph: { position: 'absolute', top: -LIST.planet.size / 2, left: -LIST.planet.size / 2, width: LIST.planet.size, height: LIST.planet.size },
  morphImage: { width: '100%', height: '100%' },
  atmosphere: { position: 'absolute' },
  back: { position: 'absolute', top: DETAIL.back.top, left: DETAIL.back.left },
  bigNumeral: {
    position: 'absolute',
    top: DETAIL.numeral.top,
    left: DETAIL.numeral.left,
    fontFamily: FONT.semibold,
    fontSize: DETAIL.numeral.size,
    lineHeight: DETAIL.numeral.size * 1.1,
    color: COLORS.numeral,
  },
  detailName: { position: 'absolute', top: DETAIL.name.top, left: DETAIL.name.left, fontFamily: FONT.semibold, fontSize: DETAIL.name.size, lineHeight: DETAIL.name.size * 1.3, color: COLORS.navy },
  detailDesc: {
    position: 'absolute',
    top: DETAIL.desc.top,
    left: DETAIL.desc.left,
    right: DETAIL.desc.right,
    fontFamily: FONT.regular,
    fontSize: DETAIL.desc.size,
    lineHeight: DETAIL.desc.lineHeight,
    color: COLORS.body,
  },
  accent: { fontFamily: FONT.semibold, color: COLORS.sun },
  video: {
    position: 'absolute',
    top: DETAIL.video.top,
    left: DETAIL.video.left,
    right: DETAIL.video.right,
    height: DETAIL.video.h,
    borderRadius: DETAIL.video.radius,
    borderCurve: 'continuous',
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  play: {
    width: DETAIL.video.play,
    height: DETAIL.video.play,
    borderRadius: DETAIL.video.play / 2,
    backgroundColor: 'rgba(40,40,40,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  block: { position: 'absolute', top: DETAIL.block.top, left: 0, width: DETAIL.block.w, bottom: 0, backgroundColor: COLORS.block },
  rocket: { position: 'absolute', top: DETAIL.rocket.top, left: DETAIL.rocket.left, width: DETAIL.rocket.size, height: DETAIL.rocket.size },
});
