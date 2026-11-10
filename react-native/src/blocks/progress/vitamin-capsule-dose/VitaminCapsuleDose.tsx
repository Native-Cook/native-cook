import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, FadeInDown, FadeOutUp, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { VcdGpu } from './gpu/VcdGpu';
import { frameAt, HOLD, idle, LOOP, MORPH, POUR_TILT, REFORM, SEP_OFF, T, TAKE, TILT } from './vcd-motion';
import { VCD_SKSL } from './vcd-shader';
import { COLOR, D, DEFAULT_MED, FONT, makeLayout, MEDS } from './vcd-theme';
import { makeFrame, makeGeo, type SpillState } from './vcd-uniforms';
import { useVcdAudio } from './use-vcd-audio';

/*
 Vitamin Capsule Dose — the medication screen of an invented reminder app, laid out from the reference
 pin (660692207860966897, its detail screen): a sheet over a black top with a grey card behind it;
 Back; the medicine itself; its name, kind and dose, an edit button; the daily dosage and how often;
 Schedule with All Logs and three times, each with its check; Export to PDF and Log Medication.

 Where the pin has a rendered capsule, the medicine is one glass object on WebGPU (TypeGPU-resolved
 WGSL, sphere-traced): a capsule of white porcelain and sky-blue glass with vitamin beads inside, a
 mint chalk tablet with its score line, or a drop of the liquid. Swipe across it for the next
 medicine: it morphs into its form, turning once round, and the screen's text follows. Drag it to
 turn it. Press and hold Log Medication and the button fills; at the end the dose is logged: the
 object burns away from a noise field into 900 beads (a compute pass) that stream into the next open
 check, which fills green, and the form grows back. Tap a filled check to undo it.

 autoPlay: LOOP = 10 s (vcd-motion.ts); frame 10 s equals frame 0, the pin's state. A touch takes
 over; 2 s after the last one the medicine goes back to Lumivit, the day's logs to the pin's, and the
 loop restarts. Reduced motion: no loop and no turning; the forms cross-fade and a log checks at once.
*/

const effect = Skia.RuntimeEffect.Make(VCD_SKSL);
if (!effect) throw new Error('Vitamin Capsule Dose: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
/** the day's logs at rest: each medicine's first time is checked, as in the pin */
const BASE = [1, 0, 0, 1, 0, 0, 1, 0, 0];

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function VitaminCapsuleDose({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Poppins-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const play = useVcdAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const g = useMemo(() => makeGeo(L), [L]);
  // the checks' centres, as plain numbers for the UI thread
  const checkX = D.rows.checkX * sx;
  const rowsY = useMemo(() => D.rows.ys.map((y) => L.Y(y)), [L]);
  const logY = L.Y(D.log.y + D.log.h / 2);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const formA = useSharedValue<number>(MEDS[DEFAULT_MED].shape);
  const formB = useSharedValue<number>(MEDS[DEFAULT_MED].shape);
  const morphK = useSharedValue(0);
  const med = useSharedValue<number>(DEFAULT_MED);
  const yaw = useSharedValue(0);
  const yawV = useSharedValue(0);
  const pitch = useSharedValue(0);
  const pitchV = useSharedValue(0);
  const dragging = useSharedValue(0);
  const hold = useSharedValue(0);
  const holding = useSharedValue(0);
  const holdTick = useSharedValue(0);
  const take = useSharedValue(0);
  const reform = useSharedValue(1);
  const targetRow = useSharedValue(1);
  const done = useSharedValue<number[]>(BASE);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const fallback = useSharedValue({ a: 0, b: 0, k: 0, scale: 1 });
  // the capsule opened: the cap pulled off and twisted, the cup tipped, the pearls' physics on the GPU
  const sep = useSharedValue(0);
  const twist = useSharedValue(0);
  const tilt = useSharedValue(TILT);
  const pmode = useSharedValue(0); // 0 none, 2 live, 3 back into the cup, 4 to the check
  const spawnReq = useSharedValue(0);
  const pulling = useSharedValue(0);
  const stirX = useSharedValue(-999);
  const stirY = useSharedValue(-999);
  const stirPX = useSharedValue(-999);
  const stirPY = useSharedValue(-999);
  const stirDown = useSharedValue(0);
  const prevLoopT = useSharedValue(-1);

  const [current, setCurrent] = useState<number>(DEFAULT_MED);
  const [checks, setChecks] = useState<number[]>(BASE);
  const [poured, setPoured] = useState(false);

  /* ------------------------------------------------------------ actions */
  const setDone = (i: number, v: number) => {
    'worklet';
    const next = done.get().slice();
    next[i] = v;
    done.set(next);
  };
  // the check fills once the pearls have reached it
  const markLater = (i: number) => {
    setTimeout(
      () =>
        scheduleOnUI(() => {
          'worklet';
          setDone(i, 1);
        }),
      1000,
    );
  };
  const select = (i: number) => {
    'worklet';
    if (take.get() > 0) return;
    const to = MEDS[i].shape;
    med.set(i);
    const now = morphK.get() > 0.5 ? formB.get() : formA.get();
    formA.set(now);
    formB.set(to);
    morphK.set(0);
    scheduleOnRN(haptic, 0);
    scheduleOnRN(play, 'morph');
    morphK.set(
      withTiming(1, { duration: reduced ? 250 : MORPH * 1000, easing: EASE_IN_OUT }, (fin) => {
        if (!fin) return;
        formA.set(formB.get());
        morphK.set(0);
      }),
    );
  };
  // the cap pops off, the cup tips and pours
  const popOpen = () => {
    'worklet';
    if (pmode.get() !== 0) return;
    sep.set(withTiming(SEP_OFF, { duration: 380, easing: Easing.bezier(0.34, 1.5, 0.64, 1) }));
    twist.set(withTiming(1.1, { duration: 380 }));
    tilt.set(withTiming(POUR_TILT, { duration: 500, easing: EASE_IN_OUT }));
    pmode.set(2);
    spawnReq.set(1);
    scheduleOnRN(haptic, 1);
    scheduleOnRN(play, 'take');
  };
  // the pearls go home (into the cup or to the check), then the capsule closes
  const closeUp = (toCheck: boolean) => {
    'worklet';
    if (pmode.get() === 0) {
      sep.set(withSpring(0, { duration: 500, dampingRatio: 0.7 }));
      twist.set(withTiming(0, { duration: 400 }));
      return;
    }
    pmode.set(toCheck ? 4 : 3);
    tilt.set(withDelay(900, withTiming(TILT, { duration: 450, easing: EASE_IN_OUT })));
    twist.set(withDelay(1100, withTiming(0, { duration: 500 })));
    sep.set(
      withDelay(
        1100,
        withTiming(0, { duration: 500, easing: EASE_IN_OUT }, (fin) => {
          if (fin) pmode.set(0);
        }),
      ),
    );
  };
  const step = (d: number) => {
    'worklet';
    select((med.get() + d + MEDS.length) % MEDS.length);
  };
  const doTake = () => {
    'worklet';
    holding.set(0);
    hold.set(withTiming(0, { duration: 260, easing: EASE_OUT }));
    scheduleOnRN(play, 'fill', true);
    const m = med.get();
    const ds = done.get();
    let r = -1;
    for (let i = 0; i < 3; i++) if (r < 0 && ds[m * 3 + i] === 0) r = i;
    if (r < 0) {
      scheduleOnRN(haptic, 1);
      return;
    }
    targetRow.set(r);
    if (pmode.get() === 2) {
      // the poured pearls are the dose: they stream into the check
      closeUp(true);
      scheduleOnRN(markLater, m * 3 + r);
      return;
    }
    scheduleOnRN(play, 'take');
    if (reduced) {
      setDone(m * 3 + r, 1);
      return;
    }
    take.set(0);
    take.set(
      withTiming(1, { duration: TAKE * 1000, easing: Easing.linear }, (fin) => {
        if (!fin) return;
        setDone(m * 3 + r, 1);
        take.set(0);
        reform.set(0);
        reform.set(withTiming(1, { duration: REFORM * 1000, easing: Easing.bezier(0.34, 1.56, 0.64, 1) }));
      }),
    );
  };
  const undo = (row: number) => {
    'worklet';
    const i = med.get() * 3 + row;
    if (done.get()[i] === 0 || row === 0) return;
    setDone(i, 0);
    scheduleOnRN(haptic, 1);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // finish what the loop was doing, then hand it to the finger
      if (morphK.get() > 0) {
        formA.set(morphK.get() > 0.5 ? formB.get() : formA.get());
        morphK.set(0);
      }
      hold.set(withTiming(0, { duration: 200 }));
      if (take.get() > 0) {
        take.set(0);
        reform.set(withTiming(1, { duration: 300 }));
      }
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const lt = clock.get() % LOOP;
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      formA.set(s.a);
      formB.set(s.b);
      morphK.set(s.k);
      med.set(s.med);
      hold.set(s.hold);
      take.set(s.take);
      reform.set(s.reform);
      targetRow.set(1);
      const want = s.taken > 0.5 ? 1 : 0;
      if (done.get()[1] !== want) setDone(1, want);
      finger.set(s.finger);
      fingerPress.set(s.press);
      if (s.fingerAt === 0) {
        // on the white cap, pulling it up and to the right along the capsule
        const k = 0.45 + 0.55 * s.fingerX;
        fingerX.set(g.cx + g.unit * 0.7 * k);
        fingerY.set(g.cy - g.unit * 0.7 * k);
      } else if (s.fingerAt === 3) {
        fingerX.set(L.w * (0.12 + 0.72 * s.fingerX));
        fingerY.set(g.floor - 6);
      } else if (s.fingerAt === 1) {
        fingerX.set(L.w * 0.5);
        fingerY.set(logY);
      } else {
        fingerX.set(checkX);
        fingerY.set(rowsY[1]);
      }
      // the pearls: spawned on the frame the loop passes the spill
      let mode = s.mode;
      if (prevLoopT.get() < T.spill && t >= T.spill) mode = 1;
      prevLoopT.set(t);
      sep.set(s.sep);
      twist.set(s.twist);
      tilt.set(s.tilt);
      pmode.set(s.mode);
      if (s.fingerAt === 3) {
        stirX.set(L.w * (0.12 + 0.72 * s.fingerX));
        stirY.set(g.floor - 6);
      }
      const spill: SpillState = {
        sep: s.sep,
        twist: s.twist,
        tilt: s.tilt,
        hidden: s.hidden,
        dt: Math.min(1 / 30, dt),
        mode,
        fx: stirX.get(),
        fy: stirY.get(),
        fdx: stirX.get() - stirPX.get(),
        fdy: stirY.get() - stirPY.get(),
        down: s.fingerAt === 3 && s.finger > 0.5 ? 1 : 0,
      };
      stirPX.set(stirX.get());
      stirPY.set(stirY.get());
      frame.set(makeFrame(g, L.w, L.h, pixelRatio, { a: s.a, b: s.b, k: s.k, yaw: s.yaw, pitch: s.pitch, bob: s.bob, take: s.take, reform: s.reform, hold: s.hold, t, dotX: checkX, dotY: rowsY[1] }, spill));
      fallback.set({ a: s.a, b: s.b, k: s.k, scale: s.take > 0 ? 1 - s.take : s.reform });
      return;
    }

    // the object turned by a finger springs back to its pose
    if (dragging.get() === 0 && !reduced) {
      const W = 6;
      yawV.set(yawV.get() + (-W * W * yaw.get() - 2 * 0.45 * W * yawV.get()) * dt);
      pitchV.set(pitchV.get() + (-W * W * pitch.get() - 2 * 0.45 * W * pitchV.get()) * dt);
      yaw.set(yaw.get() + yawV.get() * dt);
      pitch.set(pitch.get() + pitchV.get() * dt);
    }
    // Log Medication fills while the finger stays
    if (holding.get() === 1) {
      const hv = Math.min(1, hold.get() + dt / HOLD);
      hold.set(hv);
      const q = Math.floor(hv * 4);
      if (q > holdTick.get()) {
        holdTick.set(q);
        scheduleOnRN(haptic, 1);
      }
      if (hv >= 1) doTake();
    }
    const id = reduced ? { yaw: 0, pitch: 0, bob: 0 } : idle(lt);
    frame.set(
      makeFrame(g, L.w, L.h, pixelRatio, {
        a: formA.get(),
        b: formB.get(),
        k: morphK.get(),
        yaw: id.yaw + yaw.get(),
        pitch: id.pitch + pitch.get() - hold.get() * 0.06,
        bob: id.bob,
        take: take.get(),
        reform: reform.get(),
        hold: hold.get(),
        t: lt,
        dotX: checkX,
        dotY: rowsY[targetRow.get()],
      },
      {
        sep: sep.get(),
        twist: twist.get(),
        tilt: tilt.get(),
        hidden: pmode.get() !== 0 || spawnReq.get() === 1 ? 1 : 0,
        dt: Math.min(1 / 30, dt),
        mode: spawnReq.get() === 1 ? 1 : pmode.get(),
        fx: stirX.get(),
        fy: stirY.get(),
        fdx: stirDown.get() === 1 ? stirX.get() - stirPX.get() : 0,
        fdy: stirDown.get() === 1 ? stirY.get() - stirPY.get() : 0,
        down: stirDown.get(),
      }),
    );
    spawnReq.set(0);
    stirPX.set(stirX.get());
    stirPY.set(stirY.get());
    fallback.set({ a: formA.get(), b: formB.get(), k: morphK.get(), scale: take.get() > 0 ? 1 - take.get() : reform.get() });

    if (!auto || dragging.get() === 1 || holding.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      done.set(BASE);
      if (pmode.get() !== 0 || sep.get() > 0.01) closeUp(false);
      if (med.get() !== DEFAULT_MED || formA.get() !== MEDS[DEFAULT_MED].shape) select(DEFAULT_MED);
      return;
    }
    const still = pmode.get() === 0 && sep.get() < 0.001 && Math.abs(tilt.get() - TILT) < 0.001 && Math.abs(yaw.get()) < 0.01 && Math.abs(pitch.get()) < 0.01 && Math.abs(yawV.get()) < 0.05 && morphK.get() === 0 && take.get() === 0 && reform.get() > 0.999;
    // the loop starts on a pose without sway: wait for the sway to pass through it
    const id0 = idle(lt);
    if (still && formA.get() === MEDS[DEFAULT_MED].shape && Math.abs(id0.yaw) < 0.02 && Math.abs(id0.bob) < 0.3) {
      resumeAsked.set(0);
      yaw.set(0);
      pitch.set(0);
      autoStart.set(clock.get());
      prevLoopT.set(-1);
      mode.set(0);
    }
  }, false);
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      formA.set(morphK.get() > 0.5 ? formB.get() : formA.get());
      morphK.set(0);
      take.set(0);
      reform.set(1);
      hold.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__vitaminDose = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), a: formA.get(), b: formB.get(), k: +morphK.get().toFixed(2), med: med.get(), hold: +hold.get().toFixed(2), take: +take.get().toFixed(2), done: done.get() }),
    };
  }, [pin, rate, autoStart, clock, mode, formA, formB, morphK, med, hold, take, done]);

  useAnimatedReaction(
    () => pmode.get() === 2,
    (now, before) => {
      if (now !== before) scheduleOnRN(setPoured, now);
    },
  );
  // the screen follows the medicine and the day's logs
  useAnimatedReaction(
    () => med.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setCurrent, now);
    },
  );
  useAnimatedReaction(
    () => done.get().join(''),
    (now, before) => {
      if (now === before) return;
      const next = now.split('').map((c) => (c === '1' ? 1 : 0));
      scheduleOnRN(setChecks, next);
      if (before && mode.get() === 1 && now > before) {
        scheduleOnRN(haptic, 2);
        scheduleOnRN(play, 'done');
      }
    },
  );

  /* ------------------------------------------------------------ gestures */
  // on the closed capsule, a drag up and to the right pulls the white cap off along its axis (past
  // 0.45 of a unit it pops); any other drag turns the medicine, and a swipe changes it. On the open
  // capsule a tap sends the pearls home and closes it
  const AX = Math.SQRT1_2;
  const heroPan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      takeOver();
    })
    .onStart((e) => {
      const closedCapsule = formA.get() === 0 && morphK.get() === 0 && pmode.get() === 0 && sep.get() < 0.05;
      const along = (e.translationX * AX - e.translationY * AX) / Math.max(1, Math.hypot(e.translationX, e.translationY));
      pulling.set(closedCapsule && along > 0.6 && !reduced ? 1 : 0);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (reduced || pmode.get() !== 0) return;
      if (pulling.get() === 1) {
        const proj = (e.translationX * AX - e.translationY * AX) / g.unit;
        const k = Math.max(0, proj);
        sep.set(k < 0.6 ? k : 0.6 + (k - 0.6) * 0.2);
        twist.set(Math.min(1.2, sep.get() * 1.8));
        return;
      }
      dragging.set(1);
      yaw.set(Math.max(-1.4, Math.min(1.4, e.translationX / 70)));
      pitch.set(Math.max(-0.9, Math.min(0.9, -e.translationY / 90)));
      yawV.set(e.velocityX / 70);
      pitchV.set(-e.velocityY / 90);
    })
    .onEnd((e) => {
      if (pulling.get() === 1) {
        if (sep.get() > 0.45) popOpen();
        else {
          // the cap snaps home carrying the finger's speed along its axis
          const along = ((e.velocityX * AX - e.velocityY * AX) / g.unit) * (sep.get() < 0.6 ? 1 : 0.2);
          sep.set(withSpring(0, { duration: 450, dampingRatio: 0.72, velocity: along }));
          twist.set(withSpring(0, { duration: 450, dampingRatio: 0.7, velocity: along * 1.8 }));
        }
        return;
      }
      if (pmode.get() !== 0) return;
      if (Math.abs(e.translationX) > 60 || Math.abs(e.velocityX) > 650) step(e.translationX < 0 ? 1 : -1);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      dragging.set(0);
      pulling.set(0);
    });
  const heroTap = Gesture.Tap()
    .maxDistance(8)
    .onEnd(() => {
      takeOver();
      if (pmode.get() === 2) closeUp(false);
    });
  const heroTouch = Gesture.Exclusive(heroPan, heroTap);
  // run a finger through the poured pearls
  const stir = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      stirX.set(e.absoluteX);
      stirY.set(e.absoluteY);
      stirPX.set(e.absoluteX);
      stirPY.set(e.absoluteY);
      stirDown.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      stirX.set(e.absoluteX);
      stirY.set(e.absoluteY);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      stirDown.set(0);
    });
  // hold Log Medication
  const logPan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      if (take.get() > 0) return;
      holding.set(1);
      holdTick.set(0);
      scheduleOnRN(play, 'fill');
    })
    .onUpdate(() => {
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      if (holding.get() === 1) {
        holding.set(0);
        hold.set(withTiming(0, { duration: 260, easing: EASE_OUT }));
        scheduleOnRN(play, 'fill', true);
      }
    });

  /* ------------------------------------------------------------ drawing */
  const fallbackU = useDerivedValue(() => {
    const f = fallback.get();
    return { uC: [g.cx, g.cy], uU: g.unit, uShape: [f.a, f.b, f.k], uScale: Math.max(0.001, f.scale), uFade: 1 };
  });
  const sideX = D.side.x * sx;
  const sideW = D.side.w * sx;
  const logFill = useAnimatedStyle(() => ({ width: sideW * hold.get(), opacity: hold.get() > 0 ? 1 : 0 }));
  const logScale = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.02 * Math.min(1, hold.get() * 8) }] }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(20,24,60,${0.08 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(20,24,60,${0.35 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const m = MEDS[current];
  const chipW = D.chips.w * sx;
  // a chip holds two lines of text, so it shrinks less than the page on a short screen; its words stay
  // centred in it
  const chipH = D.chips.h * Math.max(L.k, 0.9);
  const chipTop = L.Y(D.chips.y) + (D.chips.h * L.k - chipH) / 2;
  const chipPad = (chipH - D.chips.h) / 2;
  const rowsTop = L.Y(D.rows.y);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {/* the sheet over the screen below it */}
      <View style={[styles.behind, { left: D.behind.x * sx, top: L.Y(D.behind.y), width: D.behind.w * sx, borderRadius: D.behind.r }]} />
      <View style={[styles.sheet, { top: L.Y(D.sheet.y), borderTopLeftRadius: D.sheet.r, borderTopRightRadius: D.sheet.r }]} />

      <PressScale style={[styles.back, { left: D.back.x * sx - 14, top: L.Y(D.back.y) - 20 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back" hitSlop={6}>
        <Svg width={10} height={16} viewBox="0 0 10 16">
          <SvgPath d="M8 2 L2 8 L8 14" stroke={COLOR.pinkText} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
        {fontsLoaded && (
          <Text style={styles.backText} allowFontScaling={false}>
            Back
          </Text>
        )}
      </PressScale>

      {/* the flat medicine, under the WebGPU one until it is up */}
      {!gpuOk && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Rect x={g.cx - g.unit * 1.6} y={g.cy - g.unit * 1.6} width={g.unit * 3.2} height={g.unit * 3.2}>
            <Shader source={effect!} uniforms={fallbackU} />
          </Rect>
        </Canvas>
      )}

      {fontsLoaded && (
        <>
          <Animated.View key={`name${current}`} style={[styles.abs, { left: D.name.x * sx, top: L.Y(D.name.y) - 20 }]} entering={reduced ? undefined : FadeInDown.duration(320)} exiting={reduced ? undefined : FadeOutUp.duration(180)} pointerEvents="none">
            <Text style={styles.name} allowFontScaling={false}>
              {m.name}
            </Text>
            <Text style={styles.sub} allowFontScaling={false}>
              {`${m.kind}  •  ${m.dose}`}
            </Text>
          </Animated.View>
          <PressScale style={[styles.edit, { left: D.edit.x * sx - D.edit.r, top: L.Y(D.edit.y) - D.edit.r, width: D.edit.r * 2, height: D.edit.r * 2, borderRadius: D.edit.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel={`Edit ${m.name}`}>
            <Svg width={22} height={22} viewBox="0 0 22 22">
              <SvgPath d="M4 15.5 L4.8 12 L13.8 3 a2 2 0 0 1 2.8 2.8 L7.6 14.8 Z M12.3 4.6 L15.2 7.5 M3.5 19 H18" stroke={COLOR.pinkText} strokeWidth={1.7} fill="none" strokeLinejoin="round" strokeLinecap="round" />
            </Svg>
          </PressScale>

          {/* the dose and how often */}
          {[0, 1].map((i) => (
            <View key={i} style={[styles.chip, { left: D.chips.xs[i] * sx, top: chipTop, width: chipW, height: chipH, borderRadius: D.chips.r }]} pointerEvents="none">
              <View style={[styles.abs, { left: D.chips.iconDx - 11, top: chipH / 2 - 11 }]}>{i === 0 ? <PillChip /> : <ClockChip />}</View>
              <Animated.View key={`chip${i}${current}`} style={[styles.abs, { left: D.chips.textDx }]} entering={reduced ? undefined : FadeInDown.duration(300).delay(40 * i)} exiting={reduced ? undefined : FadeOutUp.duration(160)}>
                <Text style={[styles.chipValue, { top: D.chips.valueDy - 14 + chipPad }]} allowFontScaling={false}>
                  {i === 0 ? m.daily : m.each}
                </Text>
                <Text style={[styles.chipLabel, { top: D.chips.labelDy - 12 + chipPad }]} allowFontScaling={false}>
                  {i === 0 ? 'Daily Dosage' : 'Each Day'}
                </Text>
              </Animated.View>
            </View>
          ))}

          {/* the schedule */}
          <Text style={[styles.schedule, { left: D.side.x * sx, top: L.Y(D.schedule.y) - 15 }]} allowFontScaling={false}>
            Schedule
          </Text>
          <PressScale style={[styles.logs, { right: L.w - (D.schedule.iconX + 12) * sx, top: L.Y(D.schedule.y) - 16 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="All logs">
            <Text style={styles.logsText} allowFontScaling={false}>
              All Logs
            </Text>
            <Svg width={22} height={22} viewBox="0 0 22 22">
              <SvgPath d="M6 3.5 H16 a2.5 2.5 0 0 1 2.5 2.5 V16 a2.5 2.5 0 0 1 -2.5 2.5 H6 A2.5 2.5 0 0 1 3.5 16 V6 A2.5 2.5 0 0 1 6 3.5 Z M7.5 11 L10 13.5 L14.5 8.5 M7.5 2 V5 M14.5 2 V5" stroke={COLOR.pinkText} strokeWidth={1.6} fill="none" strokeLinejoin="round" strokeLinecap="round" />
            </Svg>
          </PressScale>
          <View style={[styles.rows, { left: D.rows.x * sx, top: rowsTop, width: D.rows.w * sx, height: D.rows.h * L.k, borderRadius: D.rows.r }]} />
          {[0, 1, 2].map((r) => (
            <View key={r} style={[styles.abs, { left: D.rows.textX * sx, top: rowsY[r] - 13 }]} pointerEvents="none">
              <Animated.Text key={`t${current}${r}`} style={styles.time} allowFontScaling={false} entering={reduced ? undefined : FadeInDown.duration(280).delay(30 * r)} exiting={reduced ? undefined : FadeOutUp.duration(150)}>
                {m.times[r]}
              </Animated.Text>
            </View>
          ))}
          {[0, 1, 2].map((r) => (
            <Check
              key={r}
              x={checkX}
              y={rowsY[r]}
              on={checks[current * 3 + r] === 1}
              label={m.times[r]}
              onPress={() =>
                scheduleOnUI(() => {
                  'worklet';
                  takeOver();
                  undo(r);
                })
              }
            />
          ))}

          <PressScale style={[styles.export, { left: sideX, top: L.Y(D.export.y), width: sideW, height: D.export.h * L.k, borderRadius: D.export.r }]} scale={0.98} reduced={reduced} accessibilityRole="button" accessibilityLabel="Export to PDF">
            <Text style={styles.exportText} allowFontScaling={false}>
              Export to PDF
            </Text>
          </PressScale>
        </>
      )}

      {/* Log Medication: hold it */}
      <GestureDetector gesture={logPan}>
        <Animated.View
          style={[styles.log, { left: sideX, top: L.Y(D.log.y), width: sideW, height: D.log.h * L.k, borderRadius: (D.log.h * L.k) / 2 }, logScale]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Log Medication"
          accessibilityHint="Press and hold to log this dose"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              doTake();
            })
          }
        >
          <Animated.View style={[styles.logFill, logFill]} />
          {fontsLoaded && (
            <Text style={styles.logText} allowFontScaling={false}>
              Log Medication
            </Text>
          )}
        </Animated.View>
      </GestureDetector>

      {size && <VcdGpu frame={frame} width={L.w} height={L.h} disabled={reduced} onAvailable={setGpuOk} />}

      {/* the pearls on the sheet take a finger while they are out */}
      <GestureDetector gesture={stir}>
        <View style={[styles.abs, { left: 0, top: g.floor - 70, width: L.w, height: 84 }]} pointerEvents={poured ? 'auto' : 'none'} />
      </GestureDetector>

      {/* the medicine takes the finger */}
      <GestureDetector gesture={heroTouch}>
        <View
          style={[styles.abs, { left: g.cx - g.unit * 1.25, top: g.cy - g.unit * 1.15, width: g.unit * 2.5, height: g.unit * 2.2 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={m.name}
          accessibilityHint="Swipe for the next medicine"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              step(e.nativeEvent.actionName === 'increment' ? 1 : -1);
            })
          }
        />
      </GestureDetector>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

/** a schedule check: an empty ring, or green with a tick that pops in; a filled one undoes */
function Check({ x, y, on, label, onPress }: { x: number; y: number; on: boolean; label: string; onPress: () => void }) {
  const r = D.rows.checkR;
  return (
    <PressScale
      style={[styles.abs, styles.checkHit, { left: x - 22, top: y - 22 }]}
      disabled={!on}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: on }}
      accessibilityLabel={`${label}${on ? ', taken: undo' : ''}`}
      onPress={onPress}
    >
      <View style={[styles.ring, { width: r * 2, height: r * 2, borderRadius: r }]} />
      <Animated.View style={[styles.abs, styles.tick, { width: r * 2 + 1, height: r * 2 + 1, borderRadius: r + 0.5, opacity: on ? 1 : 0, transform: [{ scale: on ? 1 : 0.4 }], transitionProperty: ['opacity', 'transform'], transitionDuration: 280 }]}>
        <Svg width={11} height={9} viewBox="0 0 11 9">
          <SvgPath d="M1.5 4.6 L4.2 7.2 L9.5 1.6" stroke="#FFFFFF" strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </Animated.View>
    </PressScale>
  );
}

function PillChip() {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <SvgPath d="M8.2 17.8 a4.6 4.6 0 0 1 -6.5 -6.5 L6 7 l6.5 6.5 Z" fill={COLOR.pill} />
      <SvgPath d="M6 7 L10.2 2.8 a4.6 4.6 0 0 1 6.5 6.5 L12.5 13.5 Z" fill={COLOR.pill} opacity={0.55} />
    </Svg>
  );
}
function ClockChip() {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <SvgCircle cx={11} cy={11} r={10} fill={COLOR.clock} />
      <SvgPath d="M11 5.5 V11 L14 13" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.top, overflow: 'hidden' },
  abs: { position: 'absolute' },
  behind: { position: 'absolute', height: 120, backgroundColor: COLOR.behind },
  sheet: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLOR.sheet },
  back: { position: 'absolute', height: 40, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, gap: 10 },
  backText: { fontFamily: FONT.semibold, fontSize: D.back.size, lineHeight: 22, color: COLOR.pinkText },
  name: { fontFamily: FONT.bold, fontSize: D.name.size, lineHeight: 42, color: COLOR.ink },
  sub: { marginTop: 4, fontFamily: FONT.regular, fontSize: D.sub.size, lineHeight: 20, color: COLOR.sub },
  edit: { position: 'absolute', backgroundColor: COLOR.white, alignItems: 'center', justifyContent: 'center', boxShadow: '0 6px 18px rgba(30,34,70,0.10)' },
  chip: { position: 'absolute', backgroundColor: COLOR.fill },
  chipValue: { position: 'absolute', left: 0, width: 120, fontFamily: FONT.semibold, fontSize: 18, lineHeight: 26, color: COLOR.ink },
  chipLabel: { position: 'absolute', left: 0, width: 120, fontFamily: FONT.regular, fontSize: 14.4, lineHeight: 22, color: COLOR.sub },
  schedule: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.schedule.size, lineHeight: 30, color: COLOR.ink },
  logs: { position: 'absolute', height: 32, flexDirection: 'row', alignItems: 'center', gap: 8 },
  logsText: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 22, color: COLOR.pinkText },
  rows: { position: 'absolute', backgroundColor: COLOR.fill },
  time: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 26, color: COLOR.pinkText },
  checkHit: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  ring: { borderWidth: 1.6, borderColor: COLOR.ring },
  tick: { backgroundColor: COLOR.green, alignItems: 'center', justifyContent: 'center' },
  export: { position: 'absolute', backgroundColor: COLOR.fill, alignItems: 'center', justifyContent: 'center' },
  exportText: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 22, color: COLOR.ink },
  log: { position: 'absolute', backgroundColor: COLOR.pink, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  logFill: { position: 'absolute', left: 0, top: 0, bottom: 0, backgroundColor: COLOR.pinkDeep },
  logText: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 22, color: COLOR.white },
});
