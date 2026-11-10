import { BlurMask, Canvas, Circle, Group, Line, LinearGradient, Path, Rect, RoundedRect, Shader, Skia, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { FBD_LIGHT_SKSL } from './fbd-light';
import {
  bulbPoint,
  COOL,
  frameAt,
  GRAB_AT,
  GRAV,
  HEAT_UP,
  LIFT_MAX,
  LOOP,
  lumOf,
  poseOf,
  PULL_AT,
  PULL_CLICK,
  PULL_MAX,
  PULL_W,
  PULL_Z,
  SWING_W,
  SWING_Z,
  TWIST_W,
  TWIST_Z,
  WARM,
  warmUp,
} from './fbd-motion';
import { LampSwitch, RollingPercent } from './FbdParts';
import { FBD_SKSL } from './fbd-shader';
import { A_BAND0, A_BAND1, A_BAND2, A_DOT, A_KNOB, A_TOGGLE, accentAt, arcPoint, BULB, COLOR, DEFAULT_LEVEL, DEFAULT_SWATCH, dotX, FONT, levelAt, lightColor, makeLayout, PIN, SWATCHES } from './fbd-theme';
import { makeScene, makeUniforms } from './fbd-uniforms';
import { FbdGpu } from './gpu/FbdGpu';
import { useFbdAudio } from './use-fbd-audio';

/*
 Filament Bulb Dimmer — the Lamp Details screen of an invented smart-home app, laid out from the
 reference pin (697565429800389600): back and "Lamp Details"; the lamp's card with its name, the On
 switch and its schedule; the energy used and the room's temperature either side of the lamp, an
 "Energy Usage" tab on the left edge; under the lamp the intensity dial with its white readout; the
 light colours as dots along the bottom.

 Where the pin hangs a shade, this block hangs a real Edison bulb: ray-traced in 3D on WebGPU (brass
 socket, threaded cap, glass envelope, a squirrel cage of glowing strands), with HDR bloom, the air's
 glow and 2,600 motes of dust lit by it (a TypeGPU compute pass). Its light falls on the app: the
 page's pool, the card's and the readings' edges, all following the bulb as it swings.

 Touch: take the bulb and swing it (it twists on its cord when held off-centre; lifted, the cord goes
 slack and catches it when let go), or pull it down: past 16 pt the socket's switch clicks. Tap it
 to switch. Drag the dial's knob to dim it: the filament cools from white to an orange ember, and the
 readout rolls. Tap or drag along the dots to change the light's colour: the ring stretches between
 the dots, and the light, the dial, the switch and the room all take the colour. The On switch does
 what the pull does.

 autoPlay: LOOP = 10 s (fbd-motion.ts); frame 10 s equals frame 0, the pin's state. A touch takes
 over; 2 s after the last one the light goes back to on, 78 % and Warm, the bulb settles and the loop
 restarts. Reduced motion: no loop, no swing, no dust; the light switches with a short fade.
*/

const effect = Skia.RuntimeEffect.Make(FBD_SKSL);
if (!effect) throw new Error('Filament Bulb Dimmer: the shader failed to compile.');
const lightEffect = Skia.RuntimeEffect.Make(FBD_LIGHT_SKSL);
if (!lightEffect) throw new Error('Filament Bulb Dimmer: the light shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const RING = 11.5;

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function FilamentBulbDimmer({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  // development: show the Skia scene instead of the WebGPU one
  const [forceSkia, setForceSkia] = useState(false);
  const play = useFbdAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const dialR = PIN.dial.r * sx;
  // positions the UI thread needs, as plain numbers
  const spots = useMemo(() => {
    const toggleX = L.card.x + (PIN.toggle.x - PIN.card.x + PIN.toggle.w / 2) * sx;
    const toggleY = L.card.y + (PIN.toggle.y - PIN.card.y + PIN.toggle.h / 2) * L.k;
    return { toggleX, toggleY, dotsY: L.dotsY, dot0: PIN.dots.xs[0] * sx, dot4: PIN.dots.xs[4] * sx };
  }, [L, sx]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  // development: a slower clock for smooth recordings (1 = real time)
  const rate = useSharedValue(1);

  // the bulb: swing, stretch, twist and their speeds; held by a finger or free
  const th = useSharedValue(0);
  const thV = useSharedValue(0);
  const st = useSharedValue(0);
  const stV = useSharedValue(0);
  const tw = useSharedValue(0);
  const twV = useSharedValue(0);
  const held = useSharedValue(0);
  const holdX = useSharedValue(0);
  const holdY = useSharedValue(0);
  const grabDX = useSharedValue(0);
  const grabDY = useSharedValue(0);
  const grabAcross = useSharedValue(0);
  const clicked = useSharedValue(0);

  // the light
  const on = useSharedValue(1);
  const heat = useSharedValue(DEFAULT_LEVEL);
  const switchedAt = useSharedValue(-100);
  const level = useSharedValue(DEFAULT_LEVEL);
  const lead = useSharedValue(DEFAULT_SWATCH);
  const trail = useSharedValue(DEFAULT_SWATCH);
  const lastStep = useSharedValue(Math.round(DEFAULT_LEVEL * 20));
  const knobPress = useSharedValue(0);
  const tog = useSharedValue(1);
  const shownPct = useSharedValue(Math.round(DEFAULT_LEVEL * 100));
  const scrubbing = useSharedValue(0);

  // the scripted finger
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);

  const scene = useSharedValue<number[]>([]);
  const [lit, setLit] = useState(true);
  const [percent, setPercent] = useState(Math.round(DEFAULT_LEVEL * 100));
  const [dot, setDot] = useState<number>(DEFAULT_SWATCH);

  /* ------------------------------------------------------------ actions */
  const toggle = (touch: boolean) => {
    'worklet';
    on.set(on.get() === 1 ? 0 : 1);
    switchedAt.set(clock.get());
    scheduleOnRN(haptic, 2);
    if (touch) scheduleOnRN(play, 'pull');
  };
  const choose = (i: number) => {
    'worklet';
    const d = reduced ? 0 : 1;
    lead.set(withTiming(i, { duration: 340 * d, easing: EASE_OUT }));
    trail.set(withTiming(i, { duration: 600 * d, easing: EASE_IN_OUT }));
    scheduleOnRN(haptic, 0);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // the loop's switch becomes the real one, as it stands; the filament carries on from its heat
      switchedAt.set(-100);
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const dustT = clock.get() % LOOP;
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      if (dt > 0) {
        thV.set((s.th - th.get()) / dt);
        stV.set((s.s - st.get()) / dt);
        twV.set((s.tw - tw.get()) / dt);
      }
      th.set(s.th);
      st.set(s.s);
      tw.set(s.tw);
      on.set(s.on);
      heat.set(s.heat);
      level.set(s.level);
      lead.set(s.lead);
      trail.set(s.trail);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerAt === 0 || s.fingerAt === 4) {
        const p = poseOf(L, s.th, s.s);
        const at = s.fingerAt === 0 ? GRAB_AT : PULL_AT;
        const q = bulbPoint(L, p, at.across, at.down);
        fingerX.set(q.x);
        fingerY.set(q.y);
      } else if (s.fingerAt === 1) {
        const p = arcPoint(L, s.level);
        fingerX.set(p.x);
        fingerY.set(p.y);
      } else if (s.fingerAt === 2) {
        fingerX.set(dotX(sx, s.fingerDot));
        fingerY.set(spots.dotsY);
      } else {
        fingerX.set(spots.toggleX);
        fingerY.set(spots.toggleY);
      }
      scene.set(makeScene(L, { th: s.th, s: s.s, tw: s.tw, heat: s.heat, on: s.on, colour: (s.lead + s.trail) / 2, t: dustT }));
      return;
    }

    // the bulb's physics, in four steps a frame
    if (!reduced) {
      const n = 4;
      const hdt = dt / n;
      // while the loop waits to resume, the air calms the bulb faster
      const calm = resumeAsked.get() === 1 ? 4 : 1;
      for (let i = 0; i < n; i++) {
        let a;
        let as;
        if (held.get() === 1) {
          const dx = holdX.get() - grabDX.get() - L.anchorX;
          const dy = holdY.get() - grabDY.get() - L.anchorY;
          const thT = Math.max(-1, Math.min(1, Math.atan2(dx, Math.max(1, dy))));
          let sT = Math.sqrt(dx * dx + dy * dy) - L.cord;
          if (sT > PULL_MAX) sT = PULL_MAX + (sT - PULL_MAX) * 0.2;
          sT = Math.max(-LIFT_MAX, Math.min(PULL_MAX + 12, sT));
          const W = 40;
          a = W * W * (thT - th.get()) - 2 * W * thV.get();
          as = W * W * (sT - st.get()) - 2 * W * stV.get();
          // held off-centre, a sideways move turns the bulb on its cord
          twV.set(twV.get() + grabAcross.get() * a * hdt * 0.9);
        } else {
          a = -SWING_W * SWING_W * Math.sin(th.get()) - 2 * SWING_Z * calm * SWING_W * thV.get();
          as = st.get() < -0.5 ? GRAV * Math.cos(th.get()) : -PULL_W * PULL_W * st.get() - 2 * PULL_Z * PULL_W * stV.get();
        }
        const at = -TWIST_W * TWIST_W * tw.get() - 2 * TWIST_Z * calm * TWIST_W * twV.get();
        thV.set(thV.get() + a * hdt);
        th.set(th.get() + thV.get() * hdt);
        stV.set(stV.get() + as * hdt);
        st.set(st.get() + stV.get() * hdt);
        twV.set(twV.get() + at * hdt);
        tw.set(tw.get() + twV.get() * hdt);
      }
      // the pull switch clicks once per pull
      if (held.get() === 1 && clicked.get() === 0 && st.get() > PULL_CLICK) {
        clicked.set(1);
        toggle(true);
      }
      if (clicked.get() === 1 && st.get() < 6) clicked.set(0);
    }
    // the filament heats quickly and cools slowly; a switch on warms up with a flicker
    let target = on.get() === 1 ? level.get() : 0;
    const since = clock.get() - switchedAt.get();
    if (on.get() === 1 && since < WARM && !reduced) target *= warmUp(since / WARM);
    const tau = reduced ? 0.07 : target > heat.get() ? HEAT_UP : COOL;
    heat.set(heat.get() + (target - heat.get()) * (1 - Math.exp(-dt / tau)));
    scene.set(makeScene(L, { th: th.get(), s: st.get(), tw: tw.get(), heat: heat.get(), on: on.get(), colour: (lead.get() + trail.get()) / 2, t: dustT }));

    if (!auto || held.get() === 1 || scrubbing.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (on.get() === 0) {
        on.set(1);
        switchedAt.set(clock.get());
      }
      level.set(withTiming(DEFAULT_LEVEL, { duration: 700, easing: EASE_IN_OUT }));
      lead.set(withTiming(DEFAULT_SWATCH, { duration: 500, easing: EASE_OUT }));
      trail.set(withTiming(DEFAULT_SWATCH, { duration: 750, easing: EASE_IN_OUT }));
      return;
    }
    const still = Math.abs(th.get()) < 0.004 && Math.abs(thV.get()) < 0.02 && Math.abs(st.get()) < 0.4 && Math.abs(stV.get()) < 2 && Math.abs(tw.get()) < 0.02 && Math.abs(twV.get()) < 0.05;
    const settled = Math.abs(heat.get() - DEFAULT_LEVEL) < 0.002 && Math.abs(level.get() - DEFAULT_LEVEL) < 0.001 && Math.abs(trail.get() - DEFAULT_SWATCH) < 0.001;
    if (still && settled) {
      resumeAsked.set(0);
      th.set(0);
      st.set(0);
      tw.set(0);
      autoStart.set(clock.get());
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
      on.set(heat.get() > 0.35 ? 1 : 0);
      heat.set(on.get() === 1 ? level.get() : 0);
      switchedAt.set(-100);
      th.set(0);
      thV.set(0);
      st.set(0);
      stV.set(0);
      tw.set(0);
      twV.set(0);
      lead.set(Math.round(trail.get()));
      trail.set(Math.round(trail.get()));
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__filamentBulb = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      skia: (v: boolean) => {
        setForceSkia(v);
        if (v) setGpuOk(false);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), on: on.get(), heat: +heat.get().toFixed(3), level: +level.get().toFixed(3), colour: +trail.get().toFixed(2), th: +th.get().toFixed(3), s: +st.get().toFixed(2), tw: +tw.get().toFixed(3) }),
    };
  }, [pin, rate, autoStart, clock, mode, on, heat, level, trail, th, st, tw]);

  useAnimatedReaction(
    () => [on.get(), Math.round(level.get() * 100), Math.round(trail.get())] as const,
    (now, before) => {
      if (!before || now[0] !== before[0]) {
        scheduleOnRN(setLit, now[0] === 1);
        tog.set(reduced ? now[0] : withSpring(now[0], { duration: 420, dampingRatio: 0.72 }));
      }
      if (!before || now[1] !== before[1]) {
        scheduleOnRN(setPercent, now[1]);
        shownPct.set(reduced ? now[1] : withSpring(now[1], { duration: 260, dampingRatio: 1 }));
      }
      if (!before || now[2] !== before[2]) scheduleOnRN(setDot, now[2]);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // take the bulb: swing it, lift it, pull it down past the click
  const bulbPan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      const p = poseOf(L, th.get(), st.get());
      grabDX.set(e.absoluteX - p.hx);
      grabDY.set(e.absoluteY - p.hy);
      holdX.set(e.absoluteX);
      holdY.set(e.absoluteY);
      // how far off the axis the finger holds it, in R
      const dx = e.absoluteX - p.hx;
      const dy = e.absoluteY - p.hy;
      grabAcross.set(Math.max(-1, Math.min(1, (dx * Math.cos(p.tilt) - dy * Math.sin(p.tilt)) / L.R)));
      held.set(1);
      scheduleOnRN(play, 'tink');
      scheduleOnRN(haptic, 1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      holdX.set(e.absoluteX);
      holdY.set(e.absoluteY);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      held.set(0);
      if (st.get() > 8) scheduleOnRN(play, 'release');
    });
  // a Pan that never moves does not end with success, so a tap is its own gesture: a tug on the cord
  const bulbTap = Gesture.Tap()
    .maxDistance(6)
    .onEnd(() => {
      takeOver();
      toggle(true);
      if (!reduced) {
        stV.set(stV.get() + 170);
        twV.set(twV.get() + 0.5);
      }
    });
  const bulbTouch = Gesture.Simultaneous(bulbPan, bulbTap);

  const setLevelAt = (x: number, y: number) => {
    'worklet';
    const l = levelAt(L, x, y);
    level.set(l);
    const step = Math.round(l * 20);
    if (step !== lastStep.get()) {
      lastStep.set(step);
      scheduleOnRN(haptic, step === 0 || step === 20 ? 1 : 0);
    }
  };
  const dial = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      knobPress.set(withSpring(1, { duration: 220, dampingRatio: 0.7 }));
      setLevelAt(e.absoluteX, e.absoluteY);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      setLevelAt(e.absoluteX, e.absoluteY);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      knobPress.set(withSpring(0, { duration: 300, dampingRatio: 0.7 }));
    });

  // drag along the dots: the ring's front follows the finger, its tail catches up
  const swatchAt = (x: number) => {
    'worklet';
    return Math.max(0, Math.min(4, ((x - spots.dot0) / (spots.dot4 - spots.dot0)) * 4));
  };
  const dotsPan = Gesture.Pan()
    .minDistance(8)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      scrubbing.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const s = swatchAt(e.absoluteX);
      lead.set(s);
      trail.set(withSpring(s, { duration: 380, dampingRatio: 0.9 }));
      const i = Math.round(s);
      if (i !== Math.round(trail.get())) scheduleOnRN(haptic, 0);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      if (scrubbing.get() === 1) choose(Math.round(lead.get()));
      scrubbing.set(0);
    });
  const dotsTap = Gesture.Tap()
    .maxDistance(8)
    .onEnd((e) => {
      takeOver();
      choose(Math.round(swatchAt(e.absoluteX)));
    });
  const dotsTouch = Gesture.Exclusive(dotsPan, dotsTap);

  /* ------------------------------------------------------------ drawing */
  const colour = useDerivedValue(() => (lead.get() + trail.get()) / 2);
  const uniforms = useDerivedValue(() => makeUniforms(L, { th: th.get(), s: st.get(), tw: tw.get(), heat: heat.get(), on: on.get(), colour: colour.get(), t: 0 }, pixelRatio));
  const bandColors = useDerivedValue(() => [accentAt(colour.get(), A_BAND0), accentAt(colour.get(), A_BAND1), accentAt(colour.get(), A_BAND2)]);
  const toggleColor = useDerivedValue(() => accentAt(colour.get(), A_TOGGLE));
  const knobColor = useDerivedValue(() => accentAt(colour.get(), A_KNOB));
  const ringColor = useDerivedValue(() => accentAt(colour.get(), A_DOT));
  const glow = useDerivedValue(() => lumOf(heat.get()) / lumOf(DEFAULT_LEVEL));
  // the intensity band: up the left leg, over the top, down the right leg
  const band = useMemo(() => {
    const rm = (L.rOut + L.rIn) / 2;
    const b = Skia.PathBuilder.Make();
    b.moveTo(L.arcCx - rm, L.arcCy + L.leg);
    b.lineTo(L.arcCx - rm, L.arcCy);
    b.arcToOval(Skia.XYWHRect(L.arcCx - rm, L.arcCy - rm, rm * 2, rm * 2), 180, 180, false);
    b.lineTo(L.arcCx + rm, L.arcCy + L.leg);
    return b.build();
  }, [L]);
  const bandW = L.rOut - L.rIn;
  const knob = useDerivedValue(() => arcPoint(L, level.get()));
  const knobX = useDerivedValue(() => knob.get().x);
  const knobY = useDerivedValue(() => knob.get().y);
  const knobR = useDerivedValue(() => 17 + 3 * knobPress.get());
  const knobDot = useDerivedValue(() => 7.5 - 1.5 * knobPress.get());
  const needleA = useDerivedValue(() => {
    const k = knob.get();
    const a = Math.atan2(k.y - L.arcCy, k.x - L.arcCx);
    return vec(L.arcCx + Math.cos(a) * (L.rIn - 6), L.arcCy + Math.sin(a) * (L.rIn - 6));
  });
  const needleB = useDerivedValue(() => {
    const k = knob.get();
    const a = Math.atan2(k.y - L.arcCy, k.x - L.arcCx);
    return vec(L.arcCx + Math.cos(a) * (dialR + 6), L.arcCy + Math.sin(a) * (dialR + 6));
  });
  const bandOpacity = useDerivedValue(() => 0.42 + 0.58 * Math.min(1, glow.get()));
  const glowOpacity = useDerivedValue(() => 0.42 * Math.min(1.4, glow.get()));
  // the selection ring: a pill from the tail to the front while the colour changes
  const ringX = useDerivedValue(() => Math.min(dotX(sx, lead.get()), dotX(sx, trail.get())) - RING);
  const ringW = useDerivedValue(() => Math.abs(dotX(sx, lead.get()) - dotX(sx, trail.get())) + RING * 2);
  const ringStroke = useDerivedValue(() => 1.2 - 0.35 * Math.min(1, Math.abs(lead.get() - trail.get())));
  const hillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: dotX(sx, trail.get()) - 70 }] }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.12 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.45 * finger.get()})`);
  // the bulb's light on the card, the readings and the tab
  const lightU = useDerivedValue(() => {
    const p = poseOf(L, th.get(), st.get());
    const lc = lightColor(heat.get(), colour.get());
    const g = lumOf(heat.get()) * 2.2;
    return {
      uL: [p.fx, p.fy, BULB.depth * L.R],
      uCol: [Math.pow(lc[0], 1 / 2.2) * g, Math.pow(lc[1], 1 / 2.2) * g, Math.pow(lc[2], 1 / 2.2) * g],
      uCard: [L.card.x, L.card.y, L.card.w, L.card.h],
      uChipL: [L.chips.l, L.chips.y, L.chips.w, L.chips.h],
      uChipR: [L.chips.r, L.chips.y, L.chips.w, L.chips.h],
      uTab: [PIN.tab.x * sx, L.tabY, PIN.tab.w * sx, L.tabH],
      uRad: [PIN.card.r, 16, PIN.tab.r, 0],
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const toggleW = PIN.toggle.w * sx;
  const toggleH = PIN.toggle.h * L.k;
  const legEnd = L.arcCy + L.leg;
  const rm = (L.rOut + L.rIn) / 2;
  const hillH = h - (L.dotsY - 42);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {!gpuOk && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Rect x={0} y={0} width={w} height={h}>
            <Shader source={effect!} uniforms={uniforms} />
          </Rect>
        </Canvas>
      )}
      {size && <FbdGpu scene={scene} L={L} dialR={dialR} p3={false} disabled={reduced || forceSkia} onAvailable={setGpuOk} />}

      {/* the Energy Usage tab on the left edge and the two readings either side of the lamp */}
      <View style={[styles.tab, { left: PIN.tab.x * sx, top: L.tabY, width: PIN.tab.w * sx, height: L.tabH, borderRadius: PIN.tab.r }]} pointerEvents="none">
        {fontsLoaded && (
          <Text style={[styles.tabText, { left: (32 - PIN.tab.x) * sx - 90, top: L.tabH / 2 - 14 }]} allowFontScaling={false}>
            Energy Usage
          </Text>
        )}
      </View>
      {fontsLoaded && (
        <>
          <View style={[styles.chip, { left: L.chips.l, top: L.chips.y, width: L.chips.w, height: L.chips.h }]} pointerEvents="none">
            <Text style={styles.chipText} allowFontScaling={false}>
              4 kwh
            </Text>
          </View>
          <View style={[styles.chip, { left: L.chips.r, top: L.chips.y, width: L.chips.w, height: L.chips.h }]} pointerEvents="none">
            <Text style={styles.chipText} allowFontScaling={false}>
              18° C
            </Text>
          </View>
        </>
      )}

      {/* the intensity dial and the colours' ring */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group opacity={glowOpacity}>
          <Path path={band} style="stroke" strokeWidth={bandW} strokeCap="round">
            <LinearGradient start={vec(L.arcCx - L.rOut, 0)} end={vec(L.arcCx + L.rOut, 0)} colors={bandColors} />
            <BlurMask blur={12} style="outer" />
          </Path>
        </Group>
        <Group opacity={bandOpacity}>
          <Path path={band} style="stroke" strokeWidth={bandW} strokeCap="round">
            <LinearGradient start={vec(L.arcCx - L.rOut, 0)} end={vec(L.arcCx + L.rOut, 0)} colors={bandColors} />
          </Path>
        </Group>
        <Line p1={needleA} p2={needleB} color="rgba(255,255,255,0.35)" strokeWidth={1.2} />
        <Circle cx={knobX} cy={knobY} r={knobR} color="#FFFFFF">
          <BlurMask blur={4} style="solid" />
        </Circle>
        <Circle cx={knobX} cy={knobY} r={knobDot} color={knobColor} />
      </Canvas>
      <GestureDetector gesture={dial}>
        <View
          style={[styles.abs, { left: L.arcCx - L.rOut - 10, top: L.arcCy - L.rOut - 10, width: L.rOut * 2 + 20, height: L.rOut + L.leg + bandW / 2 + 20 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Intensity"
          accessibilityValue={{ min: 0, max: 100, now: percent }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              level.set(withTiming(Math.max(0, Math.min(1, level.get() + (e.nativeEvent.actionName === 'increment' ? 0.1 : -0.1))), { duration: 200 }));
            })
          }
        />
      </GestureDetector>
      <View style={[styles.dial, { left: L.arcCx - dialR, top: L.arcCy + PIN.dial.dy * L.k - dialR, width: dialR * 2, height: dialR * 2, borderRadius: dialR }]} pointerEvents="none">
        {fontsLoaded && (
          <>
            <Text style={styles.dialLabel} allowFontScaling={false}>
              Intensity
            </Text>
            <RollingPercent value={shownPct} />
          </>
        )}
      </View>
      <BulbIcon x={L.arcCx - rm} y={legEnd - 6} on={false} />
      <BulbIcon x={L.arcCx + rm} y={legEnd - 6} on />

      {/* the colours: a bump glides under the chosen dot, the ring stretches to it */}
      <Animated.View style={[styles.hill, { top: L.dotsY - 42 }, hillStyle]} pointerEvents="none">
        <Svg width={140} height={hillH} viewBox={`0 0 140 ${hillH}`}>
          <SvgPath d={`M0 ${hillH} C 40 ${hillH} 44 0 70 0 C 96 0 100 ${hillH} 140 ${hillH} Z`} fill="#36363E" />
          <SvgPath d={`M70 56 V ${hillH}`} stroke="rgba(255,255,255,0.12)" strokeWidth={1} />
        </Svg>
      </Animated.View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <RoundedRect x={ringX} y={L.dotsY - RING} width={ringW} height={RING * 2} r={RING} color={ringColor} style="stroke" strokeWidth={ringStroke} />
      </Canvas>
      <GestureDetector gesture={dotsTouch}>
        <View style={[styles.abs, { left: spots.dot0 - 26, top: L.dotsY - 24, width: spots.dot4 - spots.dot0 + 52, height: 48 }]}>
          {SWATCHES.map((s, i) => (
            <View
              key={s.name}
              style={[styles.dotHit, { left: PIN.dots.xs[i] * sx - spots.dot0 + 26 - 22, top: 2 }]}
              accessible
              accessibilityRole="radio"
              accessibilityState={{ checked: i === dot }}
              accessibilityLabel={`${s.name} light`}
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityAction={() =>
                scheduleOnUI(() => {
                  'worklet';
                  takeOver();
                  choose(i);
                })
              }
            >
              <View style={[styles.dot, { backgroundColor: s.dot }]} />
            </View>
          ))}
        </View>
      </GestureDetector>

      {/* the bulb: take it, pull it, tap it */}
      <GestureDetector gesture={bulbTouch}>
        <View
          style={[styles.abs, { left: L.anchorX - 130, top: L.restY - 12, width: 260, height: BULB.bottom * L.R + 8 }]}
          accessible
          accessibilityRole="switch"
          accessibilityLabel="Living Room Lamp"
          accessibilityState={{ checked: lit }}
          accessibilityHint="Pull down to switch, or swing it"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              toggle(false);
            })
          }
        />
      </GestureDetector>

      {/* the header and the lamp's card */}
      <PressScale style={[styles.back, { top: L.backY - 22, left: PIN.back.x * sx - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back" hitSlop={4}>
        <Svg width={12} height={20} viewBox="0 0 12 20">
          <SvgPath d="M10 2 L2 10 L10 18" stroke={COLOR.title} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      {fontsLoaded && (
        <Text style={[styles.title, { top: L.backY - 11 }]} allowFontScaling={false} accessibilityRole="header" pointerEvents="none">
          Lamp Details
        </Text>
      )}
      <View style={[styles.card, { left: L.card.x, top: L.card.y, width: L.card.w, height: L.card.h, borderRadius: PIN.card.r }]}>
        {fontsLoaded && (
          <>
            <Text style={[styles.name, { left: (PIN.name.x - PIN.card.x) * sx, top: (PIN.name.y - PIN.card.y) * L.k - 18 }]} allowFontScaling={false}>
              Living Room Lamp
            </Text>
            <Pressable
              style={[styles.abs, { left: (PIN.toggle.x - PIN.card.x) * sx, top: (PIN.toggle.y - PIN.card.y) * L.k, width: toggleW, height: toggleH }]}
              accessibilityRole="switch"
              accessibilityState={{ checked: lit }}
              accessibilityLabel="Lamp power"
              onPress={() =>
                scheduleOnUI(() => {
                  'worklet';
                  takeOver();
                  toggle(true);
                  if (!reduced) twV.set(twV.get() + (on.get() === 1 ? 0.25 : -0.25));
                })
              }
            >
              <LampSwitch tog={tog} glow={glow} accent={toggleColor} w={toggleW} h={toggleH} />
            </Pressable>
            <View style={[styles.clock, { left: (PIN.clock.x - PIN.card.x) * sx, top: (PIN.clock.y - PIN.card.y) * L.k }]} pointerEvents="none">
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <SvgCircle cx={8} cy={8} r={6.5} stroke="#D8D3DA" strokeWidth={1.4} fill="none" />
                <SvgPath d="M8 4.5 V8 L10.5 9.5" stroke="#D8D3DA" strokeWidth={1.4} fill="none" strokeLinecap="round" />
              </Svg>
            </View>
            <Text style={[styles.time, { left: (PIN.clock.x - PIN.card.x + PIN.clock.d + 9) * sx, top: (PIN.clock.y - PIN.card.y) * L.k + PIN.clock.d / 2 - 9 }]} allowFontScaling={false}>
              9:00 pm to 5:00 am
            </Text>
          </>
        )}
      </View>

      {/* the bulb's light on the app, then the scripted finger */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={L.tabY + L.tabH + 4}>
          <Shader source={lightEffect!} uniforms={lightU} />
        </Rect>
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

/** the small bulbs at the ends of the dial's band: dim at the low end, lit at the high end */
function BulbIcon({ x, y, on }: { x: number; y: number; on: boolean }) {
  return (
    <View style={[styles.abs, { left: x - 13, top: y - 17 }]} pointerEvents="none">
      <Svg width={26} height={31} viewBox="0 0 20 24">
        <SvgPath d="M10 3 a6.5 6.5 0 0 0 -3.8 11.8 V17 h7.6 V14.8 A6.5 6.5 0 0 0 10 3 Z M7.2 20 h5.6" fill={on ? 'rgba(255,248,214,0.92)' : 'rgba(255,255,255,0.32)'} stroke={on ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.4)'} strokeWidth={1.2} strokeLinejoin="round" />
        {on && <SvgPath d="M10 0 V1.5 M2 3 L3 4 M18 3 L17 4 M0 10 H1.5 M20 10 H18.5" stroke="rgba(255,255,255,0.9)" strokeWidth={1.2} strokeLinecap="round" />}
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  back: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 18, lineHeight: 22, color: COLOR.title },
  card: { position: 'absolute', backgroundColor: COLOR.card },
  name: { position: 'absolute', fontFamily: FONT.semibold, fontSize: 29, lineHeight: 36, letterSpacing: -0.2, color: COLOR.ink },
  clock: { position: 'absolute', width: PIN.clock.d, height: PIN.clock.d, borderRadius: PIN.clock.d / 2, backgroundColor: COLOR.clockBg, alignItems: 'center', justifyContent: 'center' },
  time: { position: 'absolute', fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, color: COLOR.chipText },
  chip: { position: 'absolute', borderRadius: 16, backgroundColor: COLOR.chipBg, alignItems: 'center', justifyContent: 'center' },
  chipText: { fontFamily: FONT.medium, fontSize: 18, color: COLOR.chipText },
  tab: { position: 'absolute', backgroundColor: COLOR.tab, overflow: 'hidden' },
  tabText: { position: 'absolute', width: 180, textAlign: 'center', fontFamily: FONT.medium, fontSize: 22, color: COLOR.tabText, transform: [{ rotate: '-90deg' }] },
  dial: { position: 'absolute', backgroundColor: COLOR.dial, alignItems: 'center', justifyContent: 'center', paddingTop: 2 },
  dialLabel: { fontFamily: FONT.regular, fontSize: 11, lineHeight: 14, color: COLOR.dialLabel },
  hill: { position: 'absolute', left: 0 },
  dotHit: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  dot: { width: PIN.dots.d, height: PIN.dots.d, borderRadius: PIN.dots.d / 2 },
});
