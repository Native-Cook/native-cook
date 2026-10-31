import { BlurMask, Canvas, LinearGradient, Path, Picture, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient as Fade } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  SensorType,
  type SharedValue,
  useAnimatedSensor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawScene, type Scene } from './flr-draw';
import {
  chestAt,
  clamp01,
  FADE,
  INTRO,
  introU,
  LOOP,
  mix,
  presence,
  R,
  seg,
  smooth,
  X,
} from './flr-motion';
import { RAYS_SKSL } from './flr-shaders';
import { BADGE, C, COPY } from './flr-theme';
import { type Cue, useFlrAudio } from './use-flr-audio';

/*
 First Loot Reward — the "you earned your first Loot" screen of an invented game, after its first win.

 Look (the reference recording): near-black page with dark purple rays behind a lavender hexagon
 badge holding a treasure chest, sparkles around it, a two-line title, a grey line under it, a
 purple "Announcement" card with a ribbon swirl, and two pill buttons (Keep playing, View loot).
 Motion of the recording (3.5 s): a thin ring appears, the badge grows out of it, sparkles pop
 in, the chest gathers itself and bursts open in a purple bloom, then shuts; the title, the line,
 the card and the buttons fade in one after another.

 Ours: only the touch (the tilt with its sheen and holographic rim, the press-and-hold burst) and
 the fade to black that closes the loop; the autoplay is the recording's reveal, measured at 30 fps.

 autoPlay: LOOP = 4 s, the recording's cycle (timings in flr-motion.ts): the reveal 0–1.8 s, the
 finished screen rests, a fade to black 3.55–3.92 s; frame 4 s equals frame 0.
 Touch: press the badge and the chest gathers (it trembles harder the longer it is held); let go
 and it bursts open; drag it to tilt the badge (a sheen and a holographic film follow, the
 sparkles and rays drift in parallax). Keep playing replays the reveal; View loot bursts the
 chest. 2 s after the last touch the loop resumes.
 Reduced motion: the finished screen, still.
*/

const raysFx = Skia.RuntimeEffect.Make(RAYS_SKSL);
let debugState = '';

type Props = { autoPlay?: boolean; previewTime?: number };

const RESTING = (t: number) => {
  'worklet';
  return t > 1.9 && t < FADE[0];
};

function Word({ i, word, time }: { i: number; word: string; time: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const t = time.get();
    const a = presence(t, X.title, X.dur, R.title, 0.38);
    void i;
    return { opacity: a, transform: [{ translateY: (1 - a) * 6 }] };
  });
  return (
    <Animated.Text style={[styles.title, style]} allowFontScaling={false}>
      {i === 3 || i === 5 ? word : `${word} `}
    </Animated.Text>
  );
}

export function FirstLootReward({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const bx = w / 2;
  const by = insets.top + BADGE.fromTop;
  const titleTop = by + 169;
  const subTop = titleTop + 68 + 15;
  const btnBottom = insets.bottom + 16;
  const cardBottom = btnBottom + 52 + 24;
  // where the loot flies: the middle of View loot
  const lootX = w - 20 - (w - 48) / 4;
  const lootY = h - btnBottom - 26;

  const swirl = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(250, 160);
    b.cubicTo(250, 90, 330, 40, 370, 40);
    b.cubicTo(420, 40, 420, 120, 360, 120);
    b.cubicTo(300, 120, 300, 20, 360, -10);
    return b.build();
  }, []);

  const play = useFlrAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(previewTime ?? 0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10); // clock of the last touch
  const pressAt = useSharedValue(-1); // clock the badge was pressed (-1: not pressed)
  const openAt = useSharedValue(-10); // clock a finger burst the chest
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  const power = useSharedValue(1); // the last finger burst's strength
  // the phone's tilt nudges the badge too, round wherever it is held (a slow baseline)
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const gyro = useSharedValue({ init: 0, pitch: 0, roll: 0, x: 0, y: 0 });
  const debugT = useSharedValue(-1);

  useEffect(() => {
    time.set(previewTime ?? 0);
  }, [previewTime, reduced, time]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __firstLoot?: unknown };
    g.__firstLoot = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete g.__firstLoot;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const r = rotation.sensor.get();
    const gy = gyro.get();
    if (gy.init === 0 && (r.pitch !== 0 || r.roll !== 0)) {
      gy.init = 1;
      gy.pitch = r.pitch;
      gy.roll = r.roll;
    }
    const kb = Math.min(1, dt * 0.6);
    gy.pitch += (r.pitch - gy.pitch) * kb;
    gy.roll += (r.roll - gy.roll) * kb;
    const ks = 1 - Math.exp(-dt * 10);
    gy.x += (Math.max(-1, Math.min(1, (r.roll - gy.roll) / 0.35)) - gy.x) * ks;
    gy.y += (Math.max(-1, Math.min(1, (r.pitch - gy.pitch) / 0.35)) - gy.y) * ks;
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${time.get().toFixed(2)} press=${pressAt.get().toFixed(1)} open=${openAt.get().toFixed(1)} now=${now.toFixed(1)} tilt=${tiltX.get().toFixed(2)}`);
    }
    if (debugT.get() >= 0) {
      time.set(debugT.get());
      return;
    }
    const t = time.get();
    const touched = now - last.get() < 2 || pressAt.get() >= 0;
    if (auto && !(touched && RESTING(t))) {
      let n = t + dt;
      if (n >= LOOP) n -= LOOP;
      time.set(n);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(active && !reduced && size !== null && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [active, reduced, size, previewTime, frameCb]);

  const scene = useDerivedValue<Scene>(() => {
    const t = time.get();
    const now = clock.get();
    const u = introU(t);
    // the reference: a thin ring, then the hexagon fades in while it grows 0.6 → 1.03 → 1
    const ring = 1 - smooth(seg(u, R.ring[0], R.ring[1]));
    const morph = 1;
    const badgeA = seg(u, 0.03, 0.21);
    // measured: 0.6 at 0.067 s, 0.85 at 0.167, 1.0 at 0.233, 1.03 at 0.3, back to 1 by 0.4
    const badgeS =
      u < 0.233
        ? mix(0.5, 1, smooth(seg(u, 0.04, 0.233)))
        : u < 0.3
          ? mix(1, 1.03, 1 - Math.pow(1 - seg(u, 0.233, 0.3), 2))
          : mix(1.03, 1, smooth(seg(u, 0.3, 0.42)));
    const float = 0;
    const sceneA = smooth(seg(u, 0.25, 0.8));
    const sparkA = seg(u, R.sparkle, R.sparkle + 1.1);
    // the chest: a finger's press / burst wins over the timeline
    const pa = pressAt.get();
    const oa = now - openAt.get();
    let a = u - R.gather;
    if (pa >= 0) a = Math.min(now - pa, 0.24);
    else if (oa >= 0 && oa < 1.7) a = 0.25 + oa;
    const chest = chestAt(a);
    // holding charges the burst: the chest trembles harder, the rim and glow brighten
    const charge = pa >= 0 ? clamp01((now - pa - 0.24) / 1.1) : 0;
    if (pa >= 0 && now - pa > 0.24) chest.gather = 1 + Math.sin((now - pa) * 46) * (0.06 + 0.08 * charge);
    const sweep = -1;
    const fingerBurst = pa < 0 && oa >= 0 && oa < 1.7;
    const pw = fingerBurst ? power.get() : 1;
    const waveR = 0;
    const waveA = 0;
    const spinAuto = 0;
    const spinTouch = 0;
    const dust = -1;
    const gx = gyro.get();
    return {
      t,
      bx,
      by: by + float,
      badgeA,
      badgeS,
      morph,
      ring,
      sceneA,
      sparkA,
      chest,
      tiltX: Math.max(-1, Math.min(1, tiltX.get() + gx.x * 0.6)),
      tiltY: Math.max(-1, Math.min(1, tiltY.get() + gx.y * 0.6)),
      press: pa >= 0 ? Math.min((now - pa) / 0.12, 1) : 0,
      sweep,
      power: pw,
      charge,
      spin: spinAuto + spinTouch,
      waveR,
      waveA,
      dust,
      lootX,
      lootY,
      w,
      h,
    };
  }, [w, h, bx, by, lootX, lootY]);

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (raysFx) drawScene(c, raysFx, scene.get());
    else c.drawColor(Skia.Color(C.page));
    return rec.finishRecordingAsPicture();
  }, [w, h]);

  const swirlEnd = useDerivedValue(() => {
    const t = time.get();
    void t;
    return 1;
  });

  // ---------------------------------------------------------------- type and controls
  const subStyle = useAnimatedStyle(() => {
    const a = presence(time.get(), X.sub, X.dur, R.sub, 0.32);
    return { opacity: a, transform: [{ translateY: (1 - a) * 6 }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const a = presence(time.get(), X.card, X.dur, R.card, 0.3);
    return { opacity: a, transform: [{ translateY: (1 - a) * 5 }] };
  });
  const keepIn = useDerivedValue(() => presence(time.get(), X.buttons, X.dur, R.keep, 0.2));
  const viewIn = useDerivedValue(() => presence(time.get(), X.buttons + 0.03, X.dur, R.view, 0.25));
  const shimmer = useDerivedValue(() => {
    const t = time.get();
    return t >= INTRO ? seg(t - INTRO, R.shimmer[0], R.shimmer[1]) : 0;
  });
  const keepPress = useSharedValue(0);
  const viewPress = useSharedValue(0);
  const keepStyle = useAnimatedStyle(() => ({
    opacity: keepIn.get(),
    transform: [{ translateY: (1 - keepIn.get()) * 10 }, { scale: 1 - keepPress.get() * 0.04 }],
  }));
  const viewStyle = useAnimatedStyle(() => ({
    opacity: viewIn.get(),
    transform: [
      { translateY: (1 - viewIn.get()) * 10 },
      { scale: 1 - viewPress.get() * 0.04 },
    ],
  }));
  const shineStyle = useAnimatedStyle(() => {
    const k = shimmer.get();
    return { opacity: k > 0 && k < 1 ? 1 : 0, transform: [{ translateX: mix(-120, 260, k) }, { rotate: '18deg' }] };
  });

  const fadeStyle = useAnimatedStyle(() => ({ opacity: smooth(seg(time.get(), FADE[0], FADE[1])) }));

  // ---------------------------------------------------------------- touch
  const badgeTouch = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced && previewTime === undefined)
    .onBegin(() => {
      const t = time.get();
      if (!RESTING(t)) return;
      last.set(clock.get());
      pressAt.set(clock.get());
      scheduleOnRN(buzz, 0);
    })
    .onUpdate((e) => {
      if (pressAt.get() < 0) return;
      last.set(clock.get());
      tiltX.set(Math.max(-1, Math.min(1, e.translationX / 90)));
      tiltY.set(Math.max(-1, Math.min(1, e.translationY / 90)));
    })
    .onFinalize(() => {
      if (pressAt.get() < 0) return;
      const now = clock.get();
      power.set(1 + 0.6 * clamp01((now - pressAt.get() - 0.21) / 1.1));
      pressAt.set(-1);
      openAt.set(now);
      last.set(now);
      tiltX.set(withSpring(0, { duration: 600, dampingRatio: 0.55 }));
      tiltY.set(withSpring(0, { duration: 600, dampingRatio: 0.55 }));
      scheduleOnRN(cue, 'open');
      scheduleOnRN(buzz, 2);
    });

  const keepTouch = Gesture.Tap()
    .maxDuration(2000)
    .onBegin(() => {
      keepPress.set(withSpring(1, { duration: 160, dampingRatio: 1 }));
    })
    .onFinalize((_e, ok) => {
      keepPress.set(withSpring(0, { duration: 320, dampingRatio: 0.7 }));
      if (!ok) return;
      scheduleOnRN(cue, 'tap');
      scheduleOnRN(buzz, 0);
      // replay the reveal: leave first, from wherever the settled screen is
      if (!reduced && RESTING(time.get())) {
        time.set(FADE[0]);
        last.set(-10);
      }
    });
  const viewTouch = Gesture.Tap()
    .maxDuration(2000)
    .onBegin(() => {
      viewPress.set(withSpring(1, { duration: 160, dampingRatio: 1 }));
    })
    .onFinalize((_e, ok) => {
      viewPress.set(withSpring(0, { duration: 320, dampingRatio: 0.7 }));
      if (!ok) return;
      scheduleOnRN(cue, 'tap');
      if (!reduced && RESTING(time.get())) {
        power.set(1);
        openAt.set(clock.get());
        last.set(clock.get());
        scheduleOnRN(cue, 'open');
        scheduleOnRN(buzz, 2);
      } else scheduleOnRN(buzz, 0);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const cardW = w - 40;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      <GestureDetector gesture={badgeTouch}>
        <View
          style={[styles.badgeHit, { left: bx - 110, top: by - 110 }]}
          accessible
          accessibilityRole="imagebutton"
          accessibilityLabel="Loot chest badge"
          accessibilityHint="Opens the chest."
        />
      </GestureDetector>
      <View style={[styles.titleBox, { top: titleTop }]} pointerEvents="none" accessible accessibilityRole="header">
        <View style={styles.line}>
          {COPY.titleWords.slice(0, 4).map((word, i) => (
            <Word key={word} i={i} word={word} time={time} />
          ))}
        </View>
        <View style={styles.line}>
          {COPY.titleWords.slice(4).map((word, i) => (
            <Word key={word} i={i + 4} word={word} time={time} />
          ))}
        </View>
      </View>
      <Animated.Text style={[styles.sub, { top: subTop }, subStyle]} allowFontScaling={false}>
        {COPY.sub}
      </Animated.Text>
      <Animated.View style={[styles.card, { bottom: cardBottom, width: cardW }, cardStyle]}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <RoundedRect x={0} y={0} width={cardW} height={133} r={20}>
            <LinearGradient start={vec(0, 133)} end={vec(cardW, 0)} colors={[C.cardL, '#6a3fa6', C.cardR]} />
          </RoundedRect>
          <Path path={swirl} style="stroke" strokeWidth={24} strokeCap="round" color={C.swirl} start={0} end={swirlEnd}>
            <BlurMask blur={0.6} style="normal" />
          </Path>
        </Canvas>
        <View style={styles.chip}>
          <Ionicons name="notifications" size={12} color="#ffffff" style={styles.bell} />
          <Text style={styles.chipText} allowFontScaling={false}>
            {COPY.chip}
          </Text>
        </View>
        <Text style={styles.heading} allowFontScaling={false}>
          {COPY.heading}
        </Text>
        <Text style={styles.body} allowFontScaling={false}>
          {COPY.body}
        </Text>
      </Animated.View>
      <View style={[styles.buttons, { bottom: btnBottom }]}>
        <GestureDetector gesture={keepTouch}>
          <Animated.View style={[styles.btn, styles.btnDark, keepStyle]} accessible accessibilityRole="button">
            <Text style={[styles.btnText, { color: '#ffffff' }]} allowFontScaling={false}>
              {COPY.keep}
            </Text>
          </Animated.View>
        </GestureDetector>
        <GestureDetector gesture={viewTouch}>
          <Animated.View style={[styles.btn, styles.btnLight, viewStyle]} accessible accessibilityRole="button">
            <Animated.View style={[styles.shine, shineStyle]} pointerEvents="none">
              <Fade
                colors={['rgba(196,160,255,0)', 'rgba(196,160,255,0.55)', 'rgba(196,160,255,0)']}
                start={{ x: 0, y: 0.5 }}
                end={{ x: 1, y: 0.5 }}
                style={StyleSheet.absoluteFill}
              />
            </Animated.View>
            <Text style={[styles.btnText, { color: '#0b0b0c' }]} allowFontScaling={false}>
              {COPY.view}
            </Text>
          </Animated.View>
        </GestureDetector>
      </View>
      <Animated.View style={[StyleSheet.absoluteFill, styles.fade, fadeStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page },
  badgeHit: { position: 'absolute', width: 220, height: 220 },
  titleBox: { position: 'absolute', left: 20, right: 20, alignItems: 'center' },
  line: { flexDirection: 'row', justifyContent: 'center' },
  title: { color: C.title, fontSize: 29.5, lineHeight: 34, fontWeight: '400', letterSpacing: -0.45 },
  sub: {
    position: 'absolute',
    left: 36,
    right: 36,
    color: C.sub,
    fontSize: 14.3,
    lineHeight: 20,
    textAlign: 'center',
    letterSpacing: -0.1,
  },
  card: { position: 'absolute', left: 20, height: 133, borderRadius: 20, overflow: 'hidden', paddingHorizontal: 16 },
  chip: {
    marginTop: 14,
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    height: 23,
    paddingLeft: 8,
    paddingRight: 9,
    borderRadius: 11.5,
    backgroundColor: C.chip,
  },
  bell: { marginRight: 4 },
  chipText: { color: '#ffffff', fontSize: 12.5, fontWeight: '500' },
  heading: { marginTop: 17, color: '#ffffff', fontSize: 17, lineHeight: 22, letterSpacing: -0.2 },
  body: { marginTop: 5, color: C.cardBody, fontSize: 14, lineHeight: 18, letterSpacing: -0.1 },
  buttons: { position: 'absolute', left: 20, right: 20, height: 52, flexDirection: 'row', gap: 8 },
  btn: { flex: 1, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  btnDark: { backgroundColor: C.btnDark },
  btnLight: { backgroundColor: C.btnLight },
  btnText: { fontSize: 17, fontWeight: '600', letterSpacing: -0.2 },
  shine: { position: 'absolute', top: -20, bottom: -20, left: 0, width: 70 },
  fade: { backgroundColor: C.page },
  loot: { color: '#dcc6ff', textShadowColor: 'rgba(170,120,255,0.55)', textShadowRadius: 10, textShadowOffset: { width: 0, height: 0 } },
  pill: {
    position: 'absolute',
    minWidth: 30,
    height: 22,
    paddingHorizontal: 7,
    borderRadius: 11,
    backgroundColor: '#8e5cf0',
    borderWidth: 2,
    borderColor: '#090909',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillText: { color: '#ffffff', fontSize: 12, fontWeight: '700' },
});

export { LOOP };
