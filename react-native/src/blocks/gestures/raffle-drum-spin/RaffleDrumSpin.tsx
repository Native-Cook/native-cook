import { Canvas, Picture, Skia, type SkFont, type SkTypeface, useFont, useImage } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, withTiming, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { RdsDrumGpu } from './gpu/RdsDrumGpu';
import { type RdsCue, useRaffleAudio } from './rds-audio';
import { drawScene, drawTop, type Geo, type Label, makeSlips, recordBase, type Res } from './rds-draw';
import {
  cageAngle,
  canTilt,
  type Driver,
  focusAt,
  grabTicket,
  releaseTicket,
  tiltTicket,
  TICKET,
  frameAt,
  grabStrip,
  makeDriver,
  pressIn,
  pressOut,
  releaseStrip,
  rubber,
  pressPlay,
  selectCard,
  stepDriver,
  storyOf,
  stripAt,
  T,
  touched,
} from './rds-motion';
import { BALL_SKSL, BG_SKSL, FOIL_SKSL, GLASS_SKSL, LIGHT_SKSL, LIQUID_SKSL, MORPH_SKSL, RAYS_SKSL } from './rds-shaders';
import { COPY, L, REF_H, REF_W } from './rds-theme';

/*
 Raffle Drum Spin — the entry screen of an invented raffle game.

 Look (from a still the owner supplied): a violet page lit from behind a chrome raffle drum with
 floating confetti, "Raffle Rumble", three ticket cards (one, two or three baseballs) and Play.
 Motion (ours): choosing a card flies its baseballs up into the drum through the cage; Play spins
 the drum with the crank, the balls tumble, one is drawn out to the front, glowing, in a burst of
 3D foil confetti, then the balls fly home. The drum, balls and foil are real 3D in WebGPU (TypeGPU
 data layouts, WGSL chrome and baseball shaders); the page is Skia.

 autoPlay: LOOP = 10 s (timeline in rds-motion.ts); frame 10 s equals frame 0.
 Touch: tap a card to choose it; drag on the drum to turn its crank (momentum carries on); Play
 spins and draws, Claim ticket sends the stub home. A drawn ticket waits for its Claim; anywhere
 else, 2 s after the last touch the loop resumes.
 Reduced motion: no loop; the first frame shows still.
*/

const bgEffect = Skia.RuntimeEffect.Make(BG_SKSL);
const ballEffect = Skia.RuntimeEffect.Make(BALL_SKSL);
const raysEffect = Skia.RuntimeEffect.Make(RAYS_SKSL);
const morphEffect = Skia.RuntimeEffect.Make(MORPH_SKSL);
const foilEffect = Skia.RuntimeEffect.Make(FOIL_SKSL);
const lightEffect = Skia.RuntimeEffect.Make(LIGHT_SKSL);
const glassEffect = Skia.RuntimeEffect.Make(GLASS_SKSL);
const liquidEffect = Skia.RuntimeEffect.Make(LIQUID_SKSL);

// A thin four-point sparkle, 20 units across.
const STAR = 'M0 -10 Q0.9 -0.9 10 0 Q0.9 0.9 0 10 Q-0.9 0.9 -10 0 Q-0.9 -0.9 0 -10 Z';

type Props = { autoPlay?: boolean };

function widthOf(font: SkFont, str: string) {
  return font.getGlyphWidths(font.getGlyphIDs(str)).reduce((a, b) => a + b, 0);
}

function frameOf(t: number, d: Driver, geo: Geo) {
  'worklet';
  return frameAt(t, geo.sx, geo.sy, storyOf(d), d.spin, Math.min(1, Math.abs(d.spinV) / 14), d.idle);
}

function fit(face: SkTypeface, str: string, target: number): Label {
  const probe = Skia.Font(face, 100);
  const font = Skia.Font(face, (100 * target) / widthOf(probe, str));
  return { font, width: widthOf(font, str) };
}

export function RaffleDrumSpin({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [gpu, setGpu] = useState(false);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const bold = useFont(require('./assets/Inter-Bold.ttf'), 100);
  const semi = useFont(require('./assets/Inter-SemiBold.ttf'), 100);
  const medium = useFont(require('./assets/Inter-Medium.ttf'), 100);
  const regular = useFont(require('./assets/Inter-Regular.ttf'), 100);
  // Display and print: Archivo ExtraBold for the title, Source Serif 4 Semibold for the ticket's
  // engraved capitals and its tabular entry number (chosen with Codex: .work/research/assets/fonts).
  const display = useFont(require('./assets/Archivo-ExtraBold.ttf'), 100);
  const serif = useFont(require('./assets/SourceSerif4-Semibold.ttf'), 100);
  const ticket = useImage(require('./assets/golden-ticket.webp'));
  const back = useImage(require('./assets/golden-ticket-back.webp'));

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => ({ w, h, sx: w / REF_W, sy: h / REF_H, top: insets.top }), [w, h, insets.top]);

  const res = useMemo<Res | null>(() => {
    if (!bold || !semi || !medium || !regular || !display || !serif || !ticket || !back || !foilEffect || !lightEffect || !glassEffect || !liquidEffect || !bgEffect || !ballEffect || !raysEffect || !morphEffect) return null;
    const [fb, fs, fm, fr] = [bold, semi, medium, regular].map((f) => f.getTypeface());
    const fd = display.getTypeface();
    const fz = serif.getTypeface();
    if (!fb || !fs || !fm || !fr || !fd || !fz) return null;
    const star = Skia.Path.MakeFromSVGString(STAR);
    if (!star) return null;
    const s = geo.sx;
    const R: Res = {
      bg: bgEffect,
      ball: ballEffect,
      rays: raysEffect,
      morph: morphEffect,
      foil: foilEffect,
      light: lightEffect,
      glass: glassEffect,
      liquid: liquidEffect,
      ticket,
      back,
      star,
      slips: makeSlips(s),
      base: null,
      text: {
        title: fit(fd, COPY.title, L.titleW * s),
        sub: Skia.Font(fr, L.subSize * s),
        chip: Skia.Font(fm, 11 * s),
        fav: Skia.Font(fs, 11 * s),
        card: Skia.Font(fr, L.textSize * s),
        play: Skia.Font(fs, 17 * s),
        caption: Skia.Font(fz, 10.5 * s),
        entry: Skia.Font(fz, 30 * s),
        stub: Skia.Font(fs, 8 * s),
        stubBig: Skia.Font(fz, 22 * s),
      },
    };
    R.base = recordBase(R, geo, frameAt(0, geo.sx, geo.sy));
    return R;
  }, [bold, semi, medium, regular, display, serif, ticket, back, geo]);

  const driver = useSharedValue<Driver>(makeDriver());
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver());
    clock.set(0);
    time.set(0);
  }, [auto, reduced, ready, driver, clock, time]);

  // Sound (expo-audio, synthesised cues in assets/sfx) plays only for a story a finger started:
  // the looping autoPlay stays silent.
  const play = useRaffleAudio(true);
  const sfx = (cue: RdsCue) => play(cue);

  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 3) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // Debug (development builds only): `globalThis.__raffleDrum.at(5.8)` pins the story time.
  const debugT = useSharedValue(-1);
  const perf = useSharedValue([0, 0, 0, 0, 0, 0]);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __raffleDrum?: unknown };
    g.__raffleDrum = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      // The driver's mode and story time, e.g. "2 7.00" while a drawn ticket waits for Claim.
      state: () => `${driver.get().mode} ${driver.get().t.toFixed(2)}`,
      // Frame report since the last call: UI frames, average and worst interval, hitches (> 20 ms),
      // and the average time to record the two pictures.
      stats: () => {
        const p = perf.get();
        perf.set([0, 0, 0, 0, 0, 0]);
        return JSON.stringify({
          frames: p[0],
          avgMs: +(p[1] / Math.max(1, p[0])).toFixed(2),
          worstMs: +p[2].toFixed(1),
          hitches: p[3],
          pageMs: +(p[4] / Math.max(1, p[0])).toFixed(2),
          topMs: +(p[5] / Math.max(1, p[0])).toFixed(2),
          gpu: (globalThis as { __rdsGpu?: unknown }).__rdsGpu,
        });
      },
    };
    return () => {
      delete g.__raffleDrum;
    };
  }, [debugT, perf, driver]);

  const lastTick = useSharedValue(0);
  const frameCb = useFrameCallback((info) => {
    if (__DEV__) {
      const ms = info.timeSincePreviousFrame ?? 16;
      perf.modify((p) => {
        'worklet';
        p[0] += 1;
        p[1] += ms;
        p[2] = Math.max(p[2], ms);
        if (ms > 20) p[3] += 1;
        return p;
      });
    }
    if (debugT.get() >= 0) {
      time.set(debugT.get());
      return;
    }
    const dt = (info.timeSincePreviousFrame ?? 16) / 1000;
    const now = clock.get() + Math.min(dt, 0.05);
    clock.set(now);
    const d = driver.get();
    const before = d.t;
    const tickBefore = Math.floor((cageAngle(d.t) + d.spin) / ((Math.PI * 2) / 7));
    stepDriver(d, dt, now, auto);
    // The crank's ratchet: one click per seventh of a turn, while a finger is involved.
    const tickNow = Math.floor((cageAngle(d.t) + d.spin) / ((Math.PI * 2) / 7));
    if (d.mode !== 0 && tickNow !== tickBefore && now - lastTick.get() > 0.06) {
      lastTick.set(now);
      scheduleOnRN(buzz, 3);
      scheduleOnRN(sfx, 'ratchet');
    }
    driver.set({ ...d });
    time.set(d.t);
    if (d.mode !== 0) {
      const t = d.t;
      const at = (m: number) => before < m && t >= m;
      for (let k = 0; k <= d.to; k++) {
        if (at(T.fly[1] + k * T.flyStagger)) {
          scheduleOnRN(buzz, 0);
          scheduleOnRN(sfx, 'land');
        }
      }
      // Play's own haptic fires on the tap (onPlay); here only what the story itself causes.
      if (at(T.spin[0])) scheduleOnRN(sfx, 'spin');
      if (at(T.eject[0])) scheduleOnRN(sfx, 'pop');
      if (at(T.stitches[0])) scheduleOnRN(sfx, 'stitches');
      if (at(T.morph[0])) scheduleOnRN(sfx, 'morph');
      if (at(T.burst)) {
        scheduleOnRN(buzz, 2);
        scheduleOnRN(sfx, 'burst');
      }
      // The counter's ticks slow down as its digits settle.
      for (let i = 0; i < 12; i++) {
        const k = i / 12;
        if (at(T.number[0] + (T.number[1] - T.number[0]) * (1 - (1 - k) * (1 - k)))) scheduleOnRN(sfx, 'tick');
      }
      if (at(T.tear[0])) scheduleOnRN(sfx, 'tear');
      if (at(T.burn[0] + 0.08)) scheduleOnRN(sfx, 'dust');
      if (at(T.tear[1])) {
        scheduleOnRN(buzz, 0);
        scheduleOnRN(sfx, 'landStub');
      }
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  // The button's label for VoiceOver and TalkBack follows what it shows ("Claim ticket" while the
  // ticket waits); React hears of it only when it changes.
  const [claimable, setClaimable] = useState(false);
  useAnimatedReaction(
    () => time.get() >= T.claim[0] + 0.12 && time.get() < T.tear[0],
    (now, before) => {
      if (now !== before) scheduleOnRN(setClaimable, now);
    },
  );

  // Reduced motion: a tap cross-fades from the last still state to the next (200 ms, ease-out).
  const fade = useSharedValue(1);
  const prev = useSharedValue<{ t: number; d: Driver }>({ t: 0, d: makeDriver() });

  const picture = useDerivedValue(() => {
    const t0 = __DEV__ ? performance.now() : 0;
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) {
      const k = fade.get();
      if (k < 1) {
        const p = prev.get();
        drawScene(c, res, geo, frameOf(p.t, p.d, geo), gpu);
        const lp = Skia.Paint();
        lp.setAlphaf(k);
        c.saveLayer(lp);
      }
      drawScene(c, res, geo, frameOf(time.get(), driver.get(), geo), gpu);
      if (k < 1) c.restore();
    } else c.drawColor(Skia.Color('#14053a'));
    const pic = recorder.finishRecordingAsPicture();
    if (__DEV__) {
      const ms = performance.now() - t0;
      perf.modify((p) => {
        'worklet';
        p[4] += ms;
        return p;
      });
    }
    return pic;
  }, [res, geo, gpu]);

  // The top layer: the drawn ball, its morph and the golden ticket, above the rack-focus blur.
  const top = useDerivedValue(() => {
    const t0 = __DEV__ ? performance.now() : 0;
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) {
      const k = fade.get();
      if (k < 1) {
        const p = prev.get();
        const lp = Skia.Paint();
        lp.setAlphaf(1 - k);
        c.saveLayer(lp);
        drawTop(c, res, geo, frameOf(p.t, p.d, geo));
        c.restore();
        const ln = Skia.Paint();
        ln.setAlphaf(k);
        c.saveLayer(ln);
      }
      drawTop(c, res, geo, frameOf(time.get(), driver.get(), geo));
      if (k < 1) c.restore();
    }
    const pic = recorder.finishRecordingAsPicture();
    if (__DEV__) {
      const ms = performance.now() - t0;
      perf.modify((p) => {
        'worklet';
        p[5] += ms;
        return p;
      });
    }
    return pic;
  }, [res, geo]);
  // Rack focus: one static blur faded in and out (animating a blur's intensity re-renders it every frame).
  const blurStyle = useAnimatedStyle(() => {
    const k = fade.get();
    return { opacity: k < 1 ? focusAt(prev.get().t) * (1 - k) + focusAt(time.get()) * k : focusAt(time.get()) };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // The crank: a vertical drag on the drum turns it (down = forwards), and it coasts on release.
  const lastY = useSharedValue(0);
  const crank = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(2)
    .onBegin((e) => {
      const d = driver.get();
      touched(d, clock.get());
      d.spinV = 0;
      driver.set({ ...d });
      lastY.set(e.y);
    })
    .onUpdate((e) => {
      const d = driver.get();
      const dy = e.y - lastY.get();
      lastY.set(e.y);
      d.spin += (dy / (L.drumR * geo.sx)) * 1.2;
      d.last = clock.get();
      driver.set({ ...d });
    })
    .onEnd((e) => {
      const d = driver.get();
      d.spinV = Math.max(-24, Math.min(24, (e.velocityY / (L.drumR * geo.sx)) * 1.2));
      d.last = clock.get();
      driver.set({ ...d });
      if (Math.abs(d.spinV) > 3) scheduleOnRN(buzz, 3);
    });

  // Reduced motion: no flight and no spin; a tap swaps the still states (chosen card, the drawn
  // ticket, the entered card).
  const still = (fn: (d: Driver) => void) => {
    scheduleOnUI(() => {
      const d = driver.get();
      prev.set({ t: time.get(), d: { ...d } });
      fade.set(0);
      fade.set(withTiming(1, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      fn(d);
      d.mode = 2;
      d.last = clock.get();
      driver.set({ ...d });
      time.set(d.t);
    });
  };
  // The golden ticket can be taken by a finger while it waits and tilted in the light; let go, it
  // wobbles back (rds-motion: the tilt spring).
  const tiltOn = useSharedValue(false);
  const tiltPan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(1)
    .onStart(() => {
      if (!canTilt(time.get())) return;
      const d = driver.get();
      grabTicket(d, clock.get());
      driver.set({ ...d });
      tiltOn.set(true);
      scheduleOnRN(buzz, 3);
    })
    .onUpdate((e) => {
      if (!tiltOn.get()) return;
      const d = driver.get();
      tiltTicket(d, e.translationX / geo.sx, e.translationY / geo.sy, clock.get());
      driver.set({ ...d });
    })
    .onFinalize((e) => {
      if (!tiltOn.get()) return;
      tiltOn.set(false);
      const d = driver.get();
      releaseTicket(d, e.velocityX / geo.sx, e.velocityY / geo.sy, clock.get());
      driver.set({ ...d });
    });

  const onCard = (c: number) => {
    if (reduced) {
      still((d) => {
        d.from = c;
        d.to = c;
        d.t = 0;
      });
      return;
    }
    scheduleOnUI(() => {
      const d = driver.get();
      if (selectCard(d, c, clock.get())) {
        driver.set({ ...d });
        scheduleOnRN(buzz, 3);
        scheduleOnRN(sfx, 'select');
      }
    });
  };
  const onPressIn = (on: number, x = NaN) => {
    if (reduced) return;
    scheduleOnUI(() => {
      const d = driver.get();
      pressIn(d, on, x / geo.sx);
      driver.set({ ...d });
    });
  };
  const onPressOut = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      pressOut(d);
      driver.set({ ...d });
    });
  };
  // The card buttons slide with the strip, so a tap lands on the card under the finger.
  const stripStyle = useAnimatedStyle(() => {
    const st = storyOf(driver.get());
    return { transform: [{ translateX: (st.strip !== undefined ? st.strip : stripAt(time.get(), st)) * geo.sx }] };
  });

  // The cards scroll sideways under the finger: 1:1 while held, rubber-banded past the ends, then
  // they coast and spring back (rds-motion: stepStrip). A tap still chooses a card.
  const grabAt = useSharedValue(0);
  const swipe = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-14, 14])
    .onStart(() => {
      const d = driver.get();
      grabStrip(d, clock.get());
      grabAt.set(d.strip);
      driver.set({ ...d });
    })
    .onUpdate((e) => {
      const d = driver.get();
      d.strip = rubber(grabAt.get() + e.translationX / geo.sx);
      d.last = clock.get();
      driver.set({ ...d });
    })
    .onFinalize((e) => {
      const d = driver.get();
      if (!d.drag) return;
      releaseStrip(d, e.velocityX / geo.sx, clock.get());
      driver.set({ ...d });
    });
  const onPlay = () => {
    if (reduced) {
      still((d) => {
        d.t = d.t >= T.hold - 0.01 && d.t < T.claimPress[0] ? T.reselect[0] - 0.05 : T.hold;
      });
      return;
    }
    scheduleOnUI(() => {
      const d = driver.get();
      if (pressPlay(d, clock.get())) {
        driver.set({ ...d });
        scheduleOnRN(buzz, 1);
      }
    });
  };

  // Development builds: drive the same handlers a finger would (`__raffleTap.play(60)` presses
  // Play 60 pt from its left edge), for checking touch-driven stories without touch injection.
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __raffleTap?: unknown };
    g.__raffleTap = {
      card: onCard,
      play: (x?: number) => {
        onPressIn(3, x === undefined ? NaN : x * geo.sx);
        onPressOut();
        onPlay();
      },
    };
    return () => {
      delete g.__raffleTap;
    };
  });

  const sx = geo.sx;
  const sy = geo.sy;
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      {size ? (
        <RdsDrumGpu
          time={time}
          driver={driver}
          width={geo.w}
          height={geo.h}
          onAvailable={setGpu}
          disabled={reduced}
          under={
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, blurStyle]}>
              <BlurView tint="dark" intensity={26} style={StyleSheet.absoluteFill} />
            </Animated.View>
          }
        >
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={top} />
          </Canvas>
        </RdsDrumGpu>
      ) : null}
      <GestureDetector gesture={crank}>
        <View
          style={[styles.abs, { left: 100 * sx, top: 90 * sy, width: 250 * sx, height: 220 * sy }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Raffle drum"
          accessibilityHint="Drag up or down to turn the crank."
        />
      </GestureDetector>
      <GestureDetector gesture={tiltPan}>
        <View
          style={[styles.abs, { left: (TICKET.x - TICKET.w / 2) * sx, top: (TICKET.y - TICKET.h / 2) * sy, width: TICKET.w * sx, height: TICKET.h * sy }]}
          accessibilityLabel="Golden ticket"
          accessibilityHint="Drag to tilt it in the light."
        />
      </GestureDetector>
      <GestureDetector gesture={swipe}>
        <View style={[styles.abs, { left: 0, right: 0, top: L.cardY * sy, height: L.cardH * sy }]} accessibilityRole="radiogroup">
          <Animated.View style={[StyleSheet.absoluteFill, stripStyle]} pointerEvents="box-none">
            {[0, 1, 2].map((c) => (
              <Pressable
                key={c}
                onPressIn={() => onPressIn(c)}
                onPressOut={onPressOut}
                onPress={() => onCard(c)}
                style={[
                  styles.abs,
                  {
                    left: (L.cardX0 + c * (L.cardW + L.cardGap)) * sx,
                    top: 0,
                    width: L.cardW * sx,
                    height: L.cardH * sy,
                  },
                ]}
                accessibilityRole="radio"
                accessibilityLabel={COPY.cards[c]}
              />
            ))}
          </Animated.View>
        </View>
      </GestureDetector>
      <Pressable
        onPressIn={(e) => onPressIn(3, e.nativeEvent.locationX)}
        onPressOut={onPressOut}
        onPress={onPlay}
        style={[
          styles.abs,
          {
            left: L.playX * sx,
            top: L.playY * sy,
            width: L.playW * sx,
            height: L.playH * sy,
          },
        ]}
        accessibilityRole="button"
        accessibilityLabel={claimable ? COPY.claim : COPY.play}
        accessibilityHint={claimable ? 'Tears off the stub and enters it with your card.' : 'Spins the drum and draws a winning ball.'}
      />
      <Pressable
        onPress={() => buzz(0)}
        style={[
          styles.abs,
          {
            left: (L.closeX - 26) * sx,
            top: (L.closeY - 26) * sy,
            width: 52 * sx,
            height: 52 * sy,
          },
        ]}
        accessibilityRole="button"
        accessibilityLabel="Close"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#14053a' },
  abs: { position: 'absolute' },
});
