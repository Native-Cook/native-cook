import { Canvas, Fill, Picture, Shader, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, PLAY_R, drawDynamic, drawStatic } from './prismSpectrumArt';
import {
  type Pose,
  LOOP,
  REST_BEAM,
  ROT_LIMIT,
  fanShape,
  loopPose,
  makeGeo,
  prismPorts,
  rubberBand,
} from './prismSpectrumMotion';
import { PRISM_SKSL } from './prismSpectrumShader';

/*
 Prism Spectrum Player — a "now playing" screen where a white beam enters a glass prism and leaves
 as a rainbow fan; each of the fan's twelve spectral bands is an equaliser bar.

 Touch: drag across the hero to turn the prism (±25°, rubber band past it, momentum on release);
 the fan follows on a spring and opens wider as it turns. The play button plays and pauses (the
 beam brightens and the bars rise, or the beam dims and the bars fall). 2 s after the last touch
 the screen eases back to rest (paused, prism level, 1:12) in 0.8 s and the loop starts again.
 Reduced motion: no loop; the prism still turns under a finger, play/pause fades.
 */

type Props = { autoPlay?: boolean };

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const P3 = Platform.OS === 'ios' ? 1 : 0;
/** Radians of prism turn per point of horizontal drag. */
const DRAG_GAIN = 0.0042;
/** The fan's spring (stiffness, damping) under the finger. */
const FAN_K = 420;
const FAN_C = 2 * 0.72 * Math.sqrt(FAN_K);
const RETURN_MS = 800;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

function tap(kind: 'light' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function PrismSpectrumPlayer({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const effect = useMemo(() => Skia.RuntimeEffect.Make(PRISM_SKSL), []);
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts>(
    () => ({
      mono: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '500' }),
      small: matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '700' }),
    }),
    [],
  );

  // The clock: `now` is the frame time; the loop runs from `start` (−1 while a finger is in
  // charge); `origin` drives the ambient motion (bars, motes, glints), which never restarts.
  const now = useSharedValue(-1);
  const start = useSharedValue(-1);
  const origin = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  // The hand-driven state, used whenever the loop is not running.
  const rest = loopPose(0);
  const uRot = useSharedValue(rest.rot);
  const uFan = useSharedValue(rest.fan);
  const uFanV = useSharedValue(0);
  const uPlay = useSharedValue(rest.play);
  const uBeam = useSharedValue(rest.beam);
  const uIcon = useSharedValue(rest.icon);
  const uElapsed = useSharedValue(rest.elapsed);
  const uPress = useSharedValue(0);
  const playing = useSharedValue(0);
  const grab = useSharedValue(0);
  const pastLimit = useSharedValue(0);

  useFrameCallback((frame) => {
    const n = frame.timestamp / 1000;
    const prev = now.get();
    if (prev < 0) {
      origin.set(n);
      if (auto) start.set(n);
    }
    now.set(n);
    const dt = prev < 0 ? 0 : Math.min(n - prev, 1 / 30);
    const s = start.get();
    if (s < 0 || n < s) {
      // The fan follows the prism on a spring; a playing track advances.
      const a = FAN_K * (uRot.get() - uFan.get()) - FAN_C * uFanV.get();
      const v = uFanV.get() + a * dt;
      uFanV.set(v);
      uFan.set(uFan.get() + v * dt);
      if (playing.get() === 1) uElapsed.set(uElapsed.get() + dt);
    }
    // 2 s after the last touch: ease back to rest, then run the loop from t = 0.
    const r = resumeAt.get();
    if (r >= 0 && n >= r) {
      resumeAt.set(-1);
      playing.set(0);
      const back = { duration: RETURN_MS, easing: EASE_IN_OUT };
      uRot.set(withTiming(rest.rot, back));
      uPlay.set(withTiming(rest.play, back));
      uBeam.set(withTiming(rest.beam, back));
      uIcon.set(withTiming(rest.icon, { duration: 300, easing: EASE_IN_OUT }));
      uElapsed.set(withTiming(rest.elapsed, back));
      start.set(n + RETURN_MS / 1000 + 0.05);
    }
  }, !reduced);

  /** Hands the loop's current pose to the hand-driven state. */
  function takeOver() {
    'worklet';
    const n = now.get();
    const s = start.get();
    if (s >= 0 && n >= s) {
      const p = loopPose((n - s) % LOOP);
      uRot.set(p.rot);
      uFan.set(p.fan);
      uFanV.set(0);
      uPlay.set(p.play);
      uBeam.set(p.beam);
      uIcon.set(p.icon);
      uElapsed.set(p.elapsed);
      playing.set(p.play > 0.5 ? 1 : 0);
    } else if (s >= 0) {
      // Mid-return: keep where the easing got to.
      uRot.set(uRot.get());
      uPlay.set(uPlay.get());
      uBeam.set(uBeam.get());
      uElapsed.set(uElapsed.get());
    }
    start.set(-1);
    resumeAt.set(-1);
  }

  function released() {
    'worklet';
    if (auto) resumeAt.set(now.get() + 2);
  }

  const pose = useDerivedValue<Pose>(() => {
    const n = now.get();
    const s = start.get();
    if (s >= 0 && n >= s) return loopPose((n - s) % LOOP);
    return {
      rot: uRot.get(),
      fan: reduced ? uRot.get() : uFan.get(),
      play: uPlay.get(),
      beam: uBeam.get(),
      icon: uIcon.get(),
      elapsed: uElapsed.get(),
      pressPlay: 0,
      pressBack: 0,
    };
  });

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    const w = g?.w ?? 1;
    const h = g?.h ?? 1;
    if (!g) {
      return { size: [w, h], p3: P3, unit: 1, prism: [0, 0, 1, 0], ports: [0, 0, 0, 0], fan: [0, 1, 0, 0], clk: [0, LOOP], lens: [1, 2] };
    }
    const ports = prismPorts(g, p.rot);
    const f = fanShape(p.fan, p.play);
    const n = now.get();
    const ambient = n < 0 ? 0 : (((n - origin.get()) % LOOP) + LOOP) % LOOP;
    return {
      size: [w, h],
      p3: P3,
      unit: g.u,
      prism: [g.cx, g.cy, g.r, p.rot],
      ports: [ports.inX, ports.inY, ports.outX, ports.outY],
      fan: [f.dir, f.spread, p.beam, p.play],
      clk: [ambient, LOOP],
      lens: [g.lmin, g.lmax],
    };
  });

  const staticPicture = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawStatic(canvas, g, insets.top, insets.bottom, fonts);
    return recorder.finishRecordingAsPicture();
  }, [g, size, insets.top, insets.bottom, fonts]);

  const dynamicPicture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) {
      const p = pose.get();
      const n = now.get();
      const s = start.get();
      const press = s >= 0 && n >= s ? p.pressPlay : uPress.get();
      drawDynamic(canvas, g, p, press, fonts);
    }
    return recorder.finishRecordingAsPicture();
  });

  const turn = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      takeOver();
      grab.set(uRot.get());
      pastLimit.set(0);
    })
    .onUpdate((e) => {
      const raw = grab.get() + e.translationX * DRAG_GAIN;
      uRot.set(rubberBand(raw, ROT_LIMIT));
      const over = Math.abs(raw) > ROT_LIMIT ? 1 : 0;
      if (over !== pastLimit.get()) {
        pastLimit.set(over);
        if (over === 1) scheduleOnRN(tap, 'tick');
      }
      if (reduced) uFan.set(uRot.get());
    })
    .onEnd((e) => {
      // Momentum: aim a little past where the finger let go, inside the limits.
      const v = e.velocityX * DRAG_GAIN;
      const target = Math.max(-ROT_LIMIT, Math.min(ROT_LIMIT, uRot.get() + v * 0.12));
      uRot.set(withSpring(target, { duration: 450, dampingRatio: 0.8, velocity: v }));
    })
    .onFinalize(() => {
      released();
    });

  const toggle = () => {
    tap('light');
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      const next = playing.get() === 1 ? 0 : 1;
      playing.set(next);
      const fade = { duration: reduced ? 300 : 600, easing: EASE_IN_OUT };
      uPlay.set(withTiming(next, fade));
      uBeam.set(withTiming(next === 1 ? 1 : REST_BEAM, fade));
      uIcon.set(withTiming(next, { duration: 250, easing: EASE_IN_OUT }));
      released();
    });
  };

  const pressIn = () => {
    scheduleOnUI(() => {
      'worklet';
      uPress.set(withTiming(1, { duration: 110, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
    });
  };
  const pressOut = () => {
    scheduleOnUI(() => {
      'worklet';
      uPress.set(withSpring(0, { duration: 350, dampingRatio: 0.6 }));
    });
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && effect ? (
        <>
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={effect} uniforms={uniforms} />
            </Fill>
            <Picture picture={staticPicture} />
            <Picture picture={dynamicPicture} />
          </Canvas>

          <GestureDetector gesture={turn}>
            <View
              style={[styles.hero, { top: insets.top + 48, height: g.heroBottom - insets.top - 48 }]}
              accessible
              accessibilityLabel="Glass prism splitting the beam into a spectrum. Drag sideways to turn it."
            />
          </GestureDetector>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 10 }]}>
            <Text style={styles.kicker}>PLAYING FROM ALBUM</Text>
            <Text style={styles.album}>Glasshouse</Text>
          </View>

          <View pointerEvents="none" style={[styles.titles, { top: g.titleTop }]}>
            <Text style={styles.title} numberOfLines={1}>
              Low Tide Lanterns
            </Text>
            <Text style={styles.artist} numberOfLines={1}>
              Iva North
            </Text>
          </View>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Play or pause"
            onPressIn={pressIn}
            onPressOut={pressOut}
            onPress={toggle}
            hitSlop={8}
            style={[
              styles.play,
              {
                left: g.w / 2 - PLAY_R,
                top: g.controlsY - PLAY_R,
                width: PLAY_R * 2,
                height: PLAY_R * 2,
              },
            ]}
          />

          <View pointerEvents="none" style={[styles.next, { bottom: insets.bottom + 23 }]}>
            <Text style={styles.nextLabel}>UP NEXT</Text>
            <Text style={styles.nextTitle} numberOfLines={1}>
              Paper Harbour · Iva North
            </Text>
          </View>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0D0F1F', overflow: 'hidden' },
  hero: { position: 'absolute', left: 0, right: 0 },
  header: { position: 'absolute', left: 64, right: 64, alignItems: 'center', gap: 2 },
  kicker: { color: 'rgba(238,234,248,0.55)', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  album: { color: '#EEEAF8', fontSize: 15, fontWeight: '600' },
  titles: { position: 'absolute', left: 24, right: 72, gap: 4 },
  title: { color: '#F3F1FA', fontSize: 28, fontWeight: '600', letterSpacing: -0.4 },
  artist: { color: 'rgba(238,234,248,0.6)', fontSize: 17, fontWeight: '400' },
  play: { position: 'absolute', borderRadius: PLAY_R },
  next: { position: 'absolute', left: 84, right: 72, gap: 2 },
  nextLabel: { color: 'rgba(238,234,248,0.45)', fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  nextTitle: { color: 'rgba(238,234,248,0.8)', fontSize: 14, fontWeight: '500' },
});
