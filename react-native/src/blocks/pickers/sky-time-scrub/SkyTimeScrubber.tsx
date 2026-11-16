import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AppState, Platform, StatusBar, StyleSheet, Text, TextInput, View, useWindowDimensions, type ViewStyle } from 'react-native';
import { useFonts } from 'expo-font';
import { Canvas, Fill, Shader, Skia, useFont } from '@shopify/react-native-skia';
import { Gesture, GestureDetector, Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolateColor,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { isWorkletFunction, scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import {
  comp,
  COPY,
  makeLayout,
  resolveTheme,
  ROW_ACCENTS,
  sys,
  type StsLayout,
  type StsResolvedTheme,
  type StsThemeProps,
} from './sts-theme';
import {
  archY,
  circularDiff,
  clamp,
  createTimeline,
  DEMO_SOLAR,
  evTargetAt,
  GESTURE,
  HINT,
  hintAt,
  LOOP,
  makeDay,
  minutesFromDayParam,
  minutesPerDay,
  mix,
  nearestDetent,
  resolveDetents,
  ROWS,
  rowTimes,
  sampleTimeline,
  smooth,
  solarAt,
  SPRINGS,
  sunHeight,
  sunSemiHeight,
  targetEV,
  timeLabel,
  wrapMinutes,
  type Day,
  type SolarModel,
} from './sts-motion';
import { solarDay } from './sts-solar';
import { hexString, luminance, rgbString, skyTintAt } from './sts-tint';
import { haptic, type HapticCue } from './sts-haptics';
import { useStsAudio, type StsCue } from './sts-audio';
import { SKY_SKSL } from './sts-shaders';

export { solarPosition, solarDay } from './sts-solar';
export type { SolarModel } from './sts-motion';

const EFFECT = Skia.RuntimeEffect.Make(SKY_SKSL);
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
const AnimatedPath = Animated.createAnimatedComponent(Path);
type StsDevHook = {
  pin: (t: number) => void;
  at: (t: number) => void;
  play: () => void;
  progress: (p: number) => void;
  state: () => object;
};
declare global {
  var __sts: StsDevHook | undefined;
}

export type SkyTint = { zenith: string; horizon: string; sun: string };
export type SkyTimeScrubberProps = {
  /** Minutes since local midnight, written and read on the UI thread. It may run past 0..1439 while a scrub crosses midnight. */
  value?: SharedValue<number>;
  /** Starting minute when uncontrolled; default `now`. */
  defaultValue?: number;
  /** The "Now" minute and the loop's resting minute; default the device clock. */
  now?: number;
  /** A settled selection: after a detent, a coast or a travel, never mid-drag. */
  onChange?: (minutes: number) => void;
  /** Every frame of a user-driven scrub, on the UI thread. Mark it with 'worklet'; a plain function is called from JS at 20 Hz. */
  onScrub?: (minutes: number) => void;
  onScrubStart?: () => void;
  /** The finger lifts; `minutes` is where it lifted, before the coast. */
  onScrubEnd?: (minutes: number) => void;
  /** Drives the solar day from a pure-TypeScript ephemeris. `utcOffset` (minutes) defaults to the device's. */
  location?: { latitude: number; longitude: number; utcOffset?: number };
  /** The calendar day for `location`; default today. */
  date?: Date;
  /** Or pass your API's day (sunrise, noon and sunset in minutes, noon elevation in degrees). Wins over `location`. */
  solar?: SolarModel;
  /** Where the ruler lands. Default `['sunrise', 'noon', 'sunset', 'now']`. */
  detents?: ('sunrise' | 'noon' | 'sunset' | 'now' | number)[];
  /** 2 clear to 8 hazy. */
  turbidity?: number;
  /** The eye's iris: `adapt: false` shows the steady state at once; times in ms. Default 180 / 600. */
  exposure?: { adapt?: boolean; upMs?: number; downMs?: number };
  /** Written by the component every frame (when set): the sky's colours, so you can light your own UI with it. */
  skyTint?: SharedValue<SkyTint>;
  /** Replaces the three solar-time rows that wake while the sky is scrubbed. */
  renderRows?: (info: { minutes: SharedValue<number> }) => ReactNode;
  theme?: StsThemeProps;
  /** The yellow instruction pill; `null` hides it with its needle. */
  hint?: string | null;
  /** Default true. */
  haptics?: boolean;
  /** Default false. */
  sound?: boolean;
  autoPlay?: boolean;
};

type SkyShaderProps = {
  elevation: SharedValue<number>;
  sunX: SharedValue<number>;
  exposure?: SharedValue<number>;
  turbidity?: number;
  style?: ViewStyle;
};

const SKY_DAY: Day = makeDay(DEMO_SOLAR);

/**
 * The sky alone: the exact scattering shader at a third of the resolution, the box's bottom edge being the horizon.
 * `elevation` is the sun's height in degrees (-45..89), `sunX` its position across the box in reference points (0..402;
 * the dotted arch runs 86..316), `exposure` the iris in EV (default: the steady state for that elevation).
 */
export function SkyShader({ elevation, sunX, exposure, turbidity = 2, style }: SkyShaderProps) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const scale = size.width > 0 ? size.width / 402 / comp.sky.rasterScale : 1;
  const horizon = size.height / Math.max(size.width / 402, 1e-3);
  const outputP3 = Platform.OS === 'ios' ? 1 : 0;
  const uniforms = useDerivedValue(() => {
    const e = elevation.get();
    return {
      scale,
      horizon,
      arcCentre: horizon - 7,
      arcAmplitude: 87,
      rulerY: horizon + 120,
      rulerOffset: 0,
      sunX: sunX.get(),
      sunH: sunHeight(e, SKY_DAY, 1),
      elevation: e,
      ev: exposure ? exposure.get() : evTargetAt(e),
      evTarget: evTargetAt(e),
      held: 0,
      press: 0,
      turbidity,
      layer: 0,
      outputP3,
      tickColor: [0.42, 0.51, 0.85],
      groundColor: [0.00784, 0.00784, 0.01176],
    };
  });
  if (!EFFECT) return null;
  return (
    <View style={style} onLayout={(e) => setSize(e.nativeEvent.layout)} pointerEvents="none" accessible={false}>
      {size.width > 0 ? (
        <Canvas
          style={{
            width: size.width / comp.sky.rasterScale,
            height: size.height / comp.sky.rasterScale,
            transformOrigin: 'top left',
            transform: [{ scale: comp.sky.rasterScale }],
          }}
          accessible={false}
        >
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms} />
          </Fill>
        </Canvas>
      ) : null}
    </View>
  );
}
/** Raw shader canvas used by the component for its sky and its sharp instrument layers. */
function ShaderCanvas<T extends Record<string, number | number[]>>({ uniforms, style }: { uniforms: SharedValue<T>; style: object }) {
  return EFFECT ? (
    <Canvas style={style} accessible={false}>
      <Fill>
        <Shader source={EFFECT} uniforms={uniforms} />
      </Fill>
    </Canvas>
  ) : null;
}

const ROW_KEYS = ['noon', 'sunset', 'night'] as const;

export function SkyTimeScrubber({
  autoPlay = false,
  value,
  defaultValue,
  now,
  onChange,
  onScrub,
  onScrubStart,
  onScrubEnd,
  location,
  date,
  solar,
  detents,
  turbidity = 2,
  exposure,
  skyTint,
  renderRows,
  theme,
  hint,
  haptics = true,
  sound = false,
}: SkyTimeScrubberProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotion();
  const [fontsReady, fontError] = useFonts({
    StsInter: require('./assets/fonts/Inter-Regular.ttf'),
    StsMedium: require('./assets/fonts/Inter-Medium.ttf'),
    StsSemibold: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const layout = useMemo(
    () => makeLayout(width, height, insets.top, insets.bottom, fontScale),
    [width, height, insets.top, insets.bottom, fontScale],
  );
  const { s, horizon, rulerY, arcCentre, arcAmplitude, chipY } = layout;
  const projection = arcAmplitude / s / 87;

  // --- the day: any solar model, any "now", any detents, any iris -------------------------------------------------------
  const [deviceNow] = useState(() => {
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  });
  const rest = wrapMinutes(now ?? deviceNow);
  const sunrise = solar?.sunrise;
  const noon = solar?.noon;
  const sunset = solar?.sunset;
  const maxElevation = solar?.maxElevation;
  const latitude = location?.latitude;
  const longitude = location?.longitude;
  const utcOffset = location?.utcOffset;
  const dateTime = date?.getTime();
  const day = useMemo(() => {
    let model: SolarModel | null = null;
    if (sunrise !== undefined && noon !== undefined && sunset !== undefined) {
      model = { sunrise, noon, sunset, maxElevation: maxElevation ?? DEMO_SOLAR.maxElevation };
    } else if (latitude !== undefined && longitude !== undefined) {
      model = solarDay(dateTime !== undefined ? new Date(dateTime) : new Date(), latitude, longitude, utcOffset);
    }
    return makeDay(model ?? DEMO_SOLAR);
  }, [sunrise, noon, sunset, maxElevation, latitude, longitude, dateTime, utcOffset]);
  const detentKey = detents ? detents.join(',') : '';
  const detentList = useMemo(
    () =>
      resolveDetents(
        day,
        rest,
        detentKey
          ? detentKey.split(',').map((v) => (v === 'sunrise' || v === 'noon' || v === 'sunset' || v === 'now' ? v : Number(v)))
          : undefined,
      ),
    [day, rest, detentKey],
  );
  const adapt = exposure?.adapt ?? true;
  const upSeconds = (exposure?.upMs ?? GESTURE.exposureUp * 1000) / 1000;
  const downSeconds = (exposure?.downMs ?? GESTURE.exposureDown * 1000) / 1000;
  const expo = useMemo(() => ({ adapt, up: upSeconds, down: downSeconds }), [adapt, upSeconds, downSeconds]);
  const timeline = useMemo(() => createTimeline(day, rest, expo), [day, rest, expo]);
  const rowT = useMemo(() => rowTimes(day), [day]);
  const th = useMemo(
    () => resolveTheme({ accent: theme?.accent, tick: theme?.tick, chip: theme?.chip, page: theme?.page, font: theme?.font }),
    [theme?.accent, theme?.tick, theme?.chip, theme?.page, theme?.font],
  );
  const hintText = hint === undefined ? COPY.hint : hint;
  const scrubIsWorklet = !!onScrub && isWorkletFunction(onScrub);

  // --- shared state ------------------------------------------------------------------------------------------------------
  const internal = useSharedValue<number>(defaultValue ?? rest);
  const minutes = value ?? internal;
  const ev = useSharedValue(targetEV(rest, day));
  const held = useSharedValue(0);
  const touch = useSharedValue({ down: false, active: false, at: 0, base: 0, x: 0, lastCue: -1, sun: false, offset: 0 });
  // 0 autoplay, 1 touch/idle, 2 coast, 3 snap, 4 returning. Pin is an independent override.
  const mode = useSharedValue(value || !autoPlay ? 1 : 0);
  const clock = useSharedValue(0);
  const loopTime = useSharedValue(0);
  const pinned = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const coast = useSharedValue({ from: 0, velocity: 0, at: 0 });
  const returning = useSharedValue({ from: 0, at: 0 });
  const appActive = useSharedValue(AppState.currentState === 'active');
  const wake = useSharedValue(0);
  const hintOn = useSharedValue(1);
  const tickAt0 = useSharedValue(-100);
  const tickAt1 = useSharedValue(-100);
  const tickAt2 = useSharedValue(-100);
  const prevMinutes = useSharedValue(-1e9);
  const lastScrubJs = useSharedValue(0);
  const [chipVisible, setChipVisible] = useState(false);
  const [hintDismissed, setHintDismissed] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const reveal = useSharedValue(0);
  const chipFont = useFont(require('./assets/fonts/Inter-Medium.ttf'), sys.type.time.size * s * fontScale);
  const ready = fontsReady && !!EFFECT && !!chipFont;
  const [shown, setShown] = useState(false);
  const haze = clamp(turbidity, 2, 8);
  const outputP3 = Platform.OS === 'ios' ? 1 : 0;
  const { play } = useStsAudio(sound);

  const commit = useCallback(
    (m: number) => {
      onChange?.(wrapMinutes(m));
    },
    [onChange],
  );
  const cue = useCallback(
    (kind: HapticCue, sfx: StsCue | '') => {
      if (haptics) haptic(kind);
      if (sound && sfx) play(sfx);
    },
    [haptics, sound, play],
  );
  const sunState = useDerivedValue(() => solarAt(minutes.get(), day));
  const tint = useDerivedValue(() => skyTintAt(sunState.get().elevation));

  // Let the native canvas mount before putting readable UI above it; the loop stays at its poster.
  useEffect(() => {
    if (!ready) return;
    let second = 0;
    const first = requestAnimationFrame(() => {
      second = requestAnimationFrame(() => setShown(true));
    });
    return () => {
      cancelAnimationFrame(first);
      cancelAnimationFrame(second);
    };
  }, [ready]);

  useEffect(() => {
    const listener = AppState.addEventListener('change', (state) => {
      appActive.set(state === 'active');
    });
    return () => listener.remove();
  }, [appActive]);

  useEffect(() => {
    reveal.set(withTiming(scheduleOpen ? 1 : 0, { duration: comp.feedback.duration }));
  }, [scheduleOpen, reveal]);

  useEffect(() => {
    if (!__DEV__) return;
    const pin = (t: number) => {
      cancelAnimation(minutes);
      pinned.set(Math.max(0, t));
    };
    const hook: StsDevHook = {
      pin,
      at: pin,
      play: () => {
        pinned.set(-1);
        mode.set(0);
        loopTime.set(0);
      },
      progress: (p) => {
        pinned.set(-1);
        mode.set(1);
        minutes.set(clamp(p) * 1440);
        held.set(1);
        lastTouch.set(clock.get());
      },
      state: () => ({
        minutes: minutes.get(),
        ev: ev.get(),
        held: held.get(),
        wake: wake.get(),
        loop: loopTime.get(),
        pinned: pinned.get(),
        mode: mode.get(),
        reducedMotion: reduce,
      }),
    };
    globalThis.__sts = hook;
    return () => {
      if (globalThis.__sts === hook) globalThis.__sts = undefined;
    };
  }, [clock, ev, held, lastTouch, loopTime, minutes, mode, pinned, reduce, wake]);

  useEffect(() => () => cancelAnimation(minutes), [minutes]);

  useFrameCallback((frame) => {
    if (!appActive.get() || !ready || !shown) return;
    const nowT = frame.timestamp / 1000;
    clock.set(nowT);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 0) / 1000, GESTURE.maxFrameDelta);
    if (pinned.get() >= 0) {
      const pose = sampleTimeline(reduce ? 0 : pinned.get(), timeline);
      minutes.set(pose.minutes);
      ev.set(pose.ev);
      held.set(pose.held);
      wake.set(pose.wake);
      hintOn.set(reduce ? 1 : hintAt(pinned.get()));
      tickAt0.set(nowT - pose.tick0);
      tickAt1.set(nowT - pose.tick1);
      tickAt2.set(nowT - pose.tick2);
      prevMinutes.set(-1e9);
      return;
    }
    if (mode.get() === 0 && autoPlay && !reduce) {
      loopTime.set((loopTime.get() + dt) % LOOP);
      const pose = sampleTimeline(loopTime.get(), timeline);
      minutes.set(pose.minutes);
      ev.set(pose.ev);
      held.set(pose.held);
      wake.set(pose.wake);
      // Follows the film, but never jumps up: after a touch the loop restarts and the hint unfolds at its own pace.
      hintOn.set(Math.min(hintAt(loopTime.get()), hintOn.get() + dt / HINT.arrive));
      tickAt0.set(nowT - pose.tick0);
      tickAt1.set(nowT - pose.tick1);
      tickAt2.set(nowT - pose.tick2);
      prevMinutes.set(pose.minutes);
      return;
    }
    const t = touch.get();
    if (t.down) {
      if (!t.active && nowT - t.at >= GESTURE.longPress) {
        touch.set({ ...t, active: true, lastCue: nowT });
        if (haptics) scheduleOnRN(haptic, 'onset');
        if (onScrubStart) scheduleOnRN(onScrubStart);
      }
      const target = t.active || nowT - t.at >= GESTURE.longPress ? 1 : 0;
      held.set(reduce ? target : mix(held.get(), target, 1 - Math.exp(-dt / GESTURE.holdRise)));
    } else {
      held.set(reduce ? 0 : mix(held.get(), 0, 1 - Math.exp(-dt / GESTURE.holdFall)));
    }
    if (mode.get() === 2) {
      const c = coast.get(),
        elapsed = Math.max(0, nowT - c.at);
      const speed = c.velocity * Math.exp(-GESTURE.decay * elapsed);
      const m = c.from + (c.velocity / GESTURE.decay) * (1 - Math.exp(-GESTURE.decay * elapsed));
      minutes.set(m);
      const detent = nearestDetent(m, detentList);
      if (Math.abs(speed) < GESTURE.captureSpeed && detent.distance < GESTURE.detentDistance) {
        mode.set(3);
        scheduleOnRN(cue, 'landing', 'clack');
        minutes.set(
          withSpring(detent.value, { ...SPRINGS.detent, velocity: speed }, (finished) => {
            if (finished) {
              mode.set(1);
              scheduleOnRN(commit, detent.value);
            }
          }),
        );
      } else if (Math.abs(speed) < GESTURE.stopSpeed) {
        mode.set(1);
        scheduleOnRN(commit, m);
      }
    }
    if (!t.down && lastTouch.get() >= 0 && nowT - lastTouch.get() >= GESTURE.resumeAfter && mode.get() !== 4) {
      cancelAnimation(minutes);
      returning.set({ from: minutes.get(), at: nowT });
      mode.set(4);
    }
    if (mode.get() === 4) {
      const r = returning.get();
      const p = clamp((nowT - r.at) / GESTURE.returnDuration);
      const target = rest + Math.round((r.from - rest) / 1440) * 1440;
      minutes.set(mix(r.from, target, reduce ? 1 : smooth(p)));
      if (p >= 1 || reduce) {
        minutes.set(rest);
        ev.set(targetEV(rest, day));
        mode.set(autoPlay && !reduce ? 0 : 1);
        loopTime.set(0);
        lastTouch.set(-1);
        scheduleOnRN(commit, rest);
      }
    }
    const cur = minutes.get();
    const target = targetEV(cur, day);
    const tau = target < ev.get() ? expo.up : expo.down;
    ev.set(
      reduce || !expo.adapt ? target : clamp(mix(ev.get(), target, 1 - Math.exp(-dt / Math.max(tau, 0.001))), target - 0.8, target + 1.2),
    );
    // The schedule rows wake while a scrub session lasts and sleep again when the sky returns to now.
    const session = held.get() > 0.5 || (lastTouch.get() >= 0 && mode.get() !== 4);
    wake.set(reduce ? (session ? ROWS.max : 0) : clamp(wake.get() + (session ? dt * ROWS.rise : -dt * ROWS.fall), 0, ROWS.max));
    // The hint stays folded away until the sky is back at now and the touch is over.
    const away = held.get() > 0.02 || lastTouch.get() >= 0;
    hintOn.set(reduce ? (away ? 0 : 1) : clamp(hintOn.get() + (away ? -dt / HINT.leave : dt / HINT.arrive)));
    // A row ticks the instant the scrub crosses its time (any day), in step with the detent cue.
    const prev = prevMinutes.get();
    if (prev > -1e8) {
      for (let r = 0; r < 3; r++) {
        if (Math.floor((prev - rowT[r]) / 1440) !== Math.floor((cur - rowT[r]) / 1440)) {
          if (r === 0) tickAt0.set(nowT);
          else if (r === 1) tickAt1.set(nowT);
          else tickAt2.set(nowT);
          if (touch.get().active && nowT - touch.get().lastCue >= GESTURE.hapticGap) {
            touch.set({ ...touch.get(), lastCue: nowT });
            scheduleOnRN(cue, 'step', '');
          }
        }
      }
    }
    prevMinutes.set(cur);
    if (onScrub && (t.active || mode.get() === 2 || mode.get() === 3)) {
      if (scrubIsWorklet) onScrub(wrapMinutes(cur));
      else if (nowT - lastScrubJs.get() >= 0.05) {
        lastScrubJs.set(nowT);
        scheduleOnRN(onScrub, wrapMinutes(cur));
      }
    }
  });

  // Every 15 minutes a step cue, on the hour a Light tap instead; a tick two semitones lower once the sun is down.
  useAnimatedReaction(
    () => Math.floor(wrapMinutes(minutes.get()) / GESTURE.step),
    (step, previous) => {
      if (previous === null || step === previous || mode.get() === 0 || pinned.get() >= 0) return;
      const driven = touch.get().active || mode.get() === 2;
      if (!driven || clock.get() - touch.get().lastCue < GESTURE.hapticGap) return;
      touch.set({ ...touch.get(), lastCue: clock.get() });
      const night = sunState.get().elevation < 0;
      scheduleOnRN(cue, step % 4 === 0 ? 'hour' : 'step', night ? 'tickNight' : 'tick');
    },
  );

  // The horizon cues use the geometry that is drawn: the lower limb touches the ground, then the upper limb is gone.
  useAnimatedReaction(
    () => {
      const e = sunState.get().elevation;
      const h = sunHeight(e, day, projection);
      const semi = sunSemiHeight(e);
      return h - semi > 0 ? 2 : h + semi < 0 ? 0 : 1;
    },
    (limb, previous) => {
      if (previous === null || limb === previous || mode.get() === 0 || pinned.get() >= 0) return;
      const lower = (limb === 1 && previous === 2) || (limb === 2 && previous === 1);
      if (lower) scheduleOnRN(cue, 'lowerLimb', 'swell');
      else scheduleOnRN(cue, 'upperLimb', '');
    },
  );

  useAnimatedReaction(
    () => held.get() > GESTURE.hiddenThreshold || circularDiff(wrapMinutes(minutes.get()), rest) > 1,
    (visible, previous) => {
      if (visible !== previous) scheduleOnRN(setChipVisible, visible);
    },
  );

  useAnimatedReaction(
    () => (skyTint ? sunState.get().elevation : 0),
    (elevation) => {
      if (!skyTint) return;
      const c = skyTintAt(elevation);
      skyTint.set({ zenith: hexString(c[0], c[1], c[2]), horizon: hexString(c[3], c[4], c[5]), sun: hexString(c[6], c[7], c[8]) });
    },
  );

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((event) => {
          cancelAnimation(minutes);
          pinned.set(-1);
          mode.set(1);
          lastTouch.set(clock.get());
          touch.set({ down: true, active: false, at: clock.get(), base: minutes.get(), x: event.x, lastCue: -1, sun: false, offset: 0 });
        })
        .onUpdate((event) => {
          const t = touch.get();
          const moved = Math.abs(event.translationX / s) > GESTURE.slop;
          if (!t.active && moved) {
            touch.set({ ...t, active: true, lastCue: clock.get() });
            if (haptics) scheduleOnRN(haptic, 'onset');
            if (onScrubStart) scheduleOnRN(onScrubStart);
          }
          if (t.active || moved) minutes.set(t.base - (event.translationX / s) * GESTURE.minutesPerPoint);
          lastTouch.set(clock.get());
        })
        .onEnd((event) => {
          const t = touch.get();
          if (!t.active) return;
          const speed = clamp((-event.velocityX / s) * GESTURE.minutesPerPoint, -GESTURE.maxSpeed, GESTURE.maxSpeed);
          if (onScrubEnd) scheduleOnRN(onScrubEnd, wrapMinutes(minutes.get()));
          if (reduce) {
            const d = nearestDetent(minutes.get(), detentList);
            if (d.distance < GESTURE.detentDistance) minutes.set(d.value);
            scheduleOnRN(commit, minutes.get());
          } else {
            coast.set({ from: minutes.get(), velocity: speed, at: clock.get() });
            mode.set(2);
          }
        })
        .onFinalize(() => {
          touch.set({ ...touch.get(), down: false, active: false });
          lastTouch.set(clock.get());
        }),
    [clock, coast, commit, detentList, haptics, lastTouch, minutes, mode, onScrubEnd, onScrubStart, pinned, reduce, s, touch],
  );

  // Grab the sun: its disc by day, its grey marker by night, dragged along the dotted arch with the finger as the clock.
  const sunPan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((event) => {
          cancelAnimation(minutes);
          pinned.set(-1);
          mode.set(1);
          lastTouch.set(clock.get());
          const sunX = solarAt(minutes.get(), day).x * s;
          // The grab keeps its offset from the sun's centre, so nothing jumps under the finger.
          touch.set({
            down: true,
            active: true,
            at: clock.get(),
            base: minutes.get(),
            x: event.absoluteX,
            lastCue: clock.get(),
            sun: true,
            offset: sunX - event.absoluteX,
          });
          if (haptics) scheduleOnRN(haptic, 'onset');
          if (onScrubStart) scheduleOnRN(onScrubStart);
        })
        .onUpdate((event) => {
          const t = touch.get();
          const d = ((event.absoluteX + t.offset) / s - 86) / 230;
          const base = wrapMinutes(t.base);
          minutes.set(t.base - base + minutesFromDayParam(d, day));
          lastTouch.set(clock.get());
        })
        .onEnd((event) => {
          const cur = minutes.get();
          const d = (solarAt(cur, day).x - 86) / 230;
          const speed = clamp(((event.velocityX / s) * minutesPerDay(d, day)) / 230, -GESTURE.maxSpeed, GESTURE.maxSpeed);
          if (onScrubEnd) scheduleOnRN(onScrubEnd, wrapMinutes(cur));
          if (reduce) {
            const nd = nearestDetent(cur, detentList);
            if (nd.distance < GESTURE.detentDistance) minutes.set(nd.value);
            scheduleOnRN(commit, minutes.get());
          } else {
            coast.set({ from: cur, velocity: speed, at: clock.get() });
            mode.set(2);
          }
        })
        .onFinalize(() => {
          touch.set({ ...touch.get(), down: false, active: false, sun: false });
          lastTouch.set(clock.get());
        }),
    [clock, coast, commit, day, detentList, haptics, lastTouch, minutes, mode, onScrubEnd, onScrubStart, pinned, reduce, s, touch],
  );

  const travelTo = useCallback(
    (rawTarget: number) => {
      const target = rawTarget + Math.round((minutes.get() - rawTarget) / 1440) * 1440;
      cancelAnimation(minutes);
      pinned.set(-1);
      mode.set(3);
      lastTouch.set(clock.get());
      cue('onset', '');
      if (reduce) {
        minutes.set(target);
        commit(target);
        mode.set(1);
        return;
      }
      minutes.set(
        withSpring(target, SPRINGS.glide, (finished) => {
          if (finished) {
            mode.set(1);
            scheduleOnRN(commit, target);
          }
        }),
      );
    },
    [clock, commit, cue, lastTouch, minutes, mode, pinned, reduce],
  );

  const baseUniforms = useDerivedValue(() => {
    const sun = sunState.get();
    return {
      horizon: horizon / s,
      arcCentre: arcCentre / s,
      arcAmplitude: arcAmplitude / s,
      rulerY: rulerY / s,
      rulerOffset: (wrapMinutes(minutes.get()) - rest) / GESTURE.minutesPerPoint,
      sunX: sun.x,
      sunH: sunHeight(sun.elevation, day, projection),
      elevation: sun.elevation,
      ev: ev.get(),
      evTarget: evTargetAt(sun.elevation),
      held: held.get(),
      press: touch.get().down ? 1 : 0,
      turbidity: haze,
      outputP3,
      tickColor: th.tickRgb,
      groundColor: th.pageRgb,
    };
  });
  const skyUniforms = useDerivedValue(() => ({ ...baseUniforms.get(), scale: s / comp.sky.rasterScale, layer: 0 }));
  const uiUniforms = useDerivedValue(() => ({ ...baseUniforms.get(), scale: s, layer: 1 }));
  const chipProps = useAnimatedProps(() => ({
    text: timeLabel(minutes.get(), day, rest),
    defaultValue: timeLabel(minutes.get(), day, rest),
  }));
  const hintLive = hintText !== null && !hintDismissed && !scheduleOpen;
  const chipStyle = useAnimatedStyle(() => {
    const base = circularDiff(wrapMinutes(minutes.get()), rest) > 1 ? 1 : clamp(held.get() * 4);
    // While the hint is out the chip waits; it takes over as the folding hint reaches its size.
    return { opacity: base * (hintLive && !reduce ? 1 - smooth((hintOn.get() - 0.12) / 0.26) : 1) };
  });
  // One line always: the glyph widths + 24 pt of padding + 8 pt of safety for tabular figures and kerning the font
  // object does not see; the chip is tinted a quarter of the way toward the sky's horizon colour.
  const chipMetrics = useAnimatedStyle(() => {
    const label = timeLabel(minutes.get(), day, rest);
    const advances = chipFont ? chipFont.getGlyphWidths(chipFont.getGlyphIDs(label)) : [];
    let w = (comp.chip.padding * 2 + 8) * s;
    for (let i = 0; i < advances.length; i++) w += advances[i];
    if (th.customFont) w += label.length * 0.9 * s;
    const c = tint.get();
    const k = comp.rim.chip;
    return {
      width: clamp(w, comp.chip.minW * s, Math.min(layout.width - 32 * s, comp.chip.maxW * s * Math.max(1, fontScale))),
      backgroundColor: rgbString(mix(th.chipRgb[0], c[3], k), mix(th.chipRgb[1], c[4], k), mix(th.chipRgb[2], c[5], k)),
    };
  });
  const panelBackground = useAnimatedStyle(() => ({ opacity: reveal.get() }));
  const customRowsStyle = useAnimatedStyle(() => ({
    opacity: ROWS.rest + (1 - ROWS.rest) * Math.max(smooth(wake.get() / ROWS.fade), reveal.get()),
  }));
  // The hint is one object that folds: the label fades, the pill narrows and darkens into the time chip, then the
  // stem draws back down into its dot on the ruler. Arrival plays it backwards. Reduce Motion: a plain fade.
  const hintGroup = useAnimatedStyle(() => ({ opacity: hintLive ? (reduce ? hintOn.get() : 1) : withTiming(0, { duration: 160 }) }));
  const hintPill = useAnimatedStyle(() => {
    const h = reduce ? 1 : hintOn.get();
    const open = smooth((h - 0.35) / 0.45);
    const w = mix(comp.chip.minW * s, layout.hintW, open);
    const tall = mix(comp.chip.h * s, layout.hintH, open);
    return {
      left: (layout.width - w) / 2,
      width: w,
      top: layout.hintY + layout.hintH - tall,
      height: tall,
      borderRadius: mix((comp.chip.h * s) / 2, comp.hint.radius * s, open),
      // Stays the accent colour while it folds (no muddy in-between hue); at chip size it hands over to the chip by opacity.
      opacity: smooth((h - 0.26) / 0.12),
      backgroundColor: th.accent,
    };
  });
  const hintLabel = useAnimatedStyle(() => ({ opacity: reduce ? 1 : smooth((hintOn.get() - 0.72) / 0.28) }));
  const hintStem = useAnimatedStyle(() => {
    const h = reduce ? 1 : hintOn.get();
    const len = Math.max(0, layout.tipY - (layout.hintY + layout.hintH));
    const grow = smooth((h - 0.1) / 0.3) * len;
    return { top: layout.tipY - grow, height: grow };
  });
  const hintTip = useAnimatedStyle(() => {
    const h = reduce ? 1 : hintOn.get();
    // The dot leaves before the lens lands on its spot and arrives as the stem starts to grow.
    return { opacity: smooth((h - 0.06) / 0.1), transform: [{ scale: 0.4 + 0.6 * smooth((h - 0.06) / 0.14) }] };
  });
  const chipWidth = Math.min(width - 32 * s, comp.chip.maxW * s * Math.max(1, fontScale));
  // One line unless even the longest label ("Solar noon · 12:31") cannot fit the chip's widest box at this type size.
  let longestLabel = 0;
  if (chipFont) {
    const advances = chipFont.getGlyphWidths(chipFont.getGlyphIDs(`${COPY.noon} · 00:00`));
    for (let i = 0; i < advances.length; i++) longestLabel += advances[i];
  }
  const chipOneLine = !layout.largeText || (longestLabel > 0 && longestLabel + (comp.chip.padding * 2 + 8) * s <= chipWidth);
  const chipHeight = Math.max(comp.chip.h * s, (sys.type.time.lineHeight * fontScale * (chipOneLine ? 1 : 2) + 8) * s);
  const text = { fontFamily: th.body, fontSize: sys.type.body.size * s, color: sys.ink.primary };
  const rows = [
    { key: ROW_KEYS[0], title: COPY.noon, m: rowT[0], color: ROW_ACCENTS[0], tickAt: tickAt0 },
    { key: ROW_KEYS[1], title: COPY.sunset, m: rowT[1], color: ROW_ACCENTS[1], tickAt: tickAt1 },
    { key: ROW_KEYS[2], title: COPY.night, m: rowT[2], color: ROW_ACCENTS[2], tickAt: tickAt2 },
  ];

  return (
    <View style={[styles.screen, { backgroundColor: th.page }]}>
      <StatusBar barStyle="light-content" />
      {ready ? (
        <>
          <View
            pointerEvents="none"
            style={StyleSheet.absoluteFill}
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
          >
            <ShaderCanvas
              uniforms={skyUniforms}
              style={{
                width: width / comp.sky.rasterScale,
                height: height / comp.sky.rasterScale,
                transformOrigin: 'top left',
                transform: [{ scale: comp.sky.rasterScale }],
              }}
            />
            <ShaderCanvas uniforms={uiUniforms} style={StyleSheet.absoluteFill} />
          </View>
          <View
            pointerEvents={shown ? 'box-none' : 'none'}
            accessibilityElementsHidden={!shown}
            importantForAccessibility={shown ? 'auto' : 'no-hide-descendants'}
            style={[StyleSheet.absoluteFill, { opacity: shown ? 1 : 0 }]}
          >
            <SunGrab gesture={sunPan} sunState={sunState} held={held} layout={layout} day={day} projection={projection} />
            <RulerAccessibility pan={pan} minutes={minutes} clock={clock} layout={layout} travelTo={travelTo} day={day} rest={rest} />
            <Animated.View
              pointerEvents={chipVisible ? 'auto' : 'none'}
              accessibilityElementsHidden={!chipVisible}
              importantForAccessibility={chipVisible ? 'auto' : 'no-hide-descendants'}
              style={[
                {
                  position: 'absolute',
                  top: chipY - (chipHeight - comp.chip.h * s),
                  left: (width - chipWidth) / 2,
                  width: chipWidth,
                  minHeight: comp.chip.target * s,
                },
                chipStyle,
              ]}
            >
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={COPY.return}
                onPress={() => travelTo(rest)}
                style={{ minHeight: comp.chip.target * s, justifyContent: 'flex-start', alignItems: 'center' }}
              >
                <AnimatedInput
                  editable={false}
                  caretHidden
                  multiline
                  scrollEnabled={false}
                  pointerEvents="none"
                  accessibilityElementsHidden
                  importantForAccessibility="no-hide-descendants"
                  animatedProps={chipProps}
                  style={[
                    {
                      color: sys.ink.primary,
                      fontFamily: th.time,
                      fontSize: sys.type.time.size * s,
                      textAlign: 'center',
                      paddingHorizontal: comp.chip.padding * s,
                      paddingVertical: 4 * s,
                      borderRadius: (comp.chip.h * s) / 2,
                      minWidth: comp.chip.minW * s,
                      maxWidth: chipWidth,
                      height: chipHeight,
                      fontVariant: ['tabular-nums'],
                    },
                    chipMetrics,
                  ]}
                />
              </Pressable>
            </Animated.View>
            <Animated.View
              pointerEvents="none"
              style={[
                {
                  position: 'absolute',
                  left: comp.schedule.x * s,
                  top: layout.scheduleY,
                  width: comp.schedule.w * s,
                  height: layout.scheduleH,
                  borderRadius: (comp.schedule.h / 8) * s,
                  backgroundColor: sys.surface.panel,
                },
                panelBackground,
              ]}
            />
            <View
              pointerEvents={scheduleOpen ? 'auto' : 'none'}
              accessibilityElementsHidden={!scheduleOpen}
              importantForAccessibility={scheduleOpen ? 'auto' : 'no-hide-descendants'}
              style={{
                position: 'absolute',
                left: (comp.schedule.x + comp.schedule.pad) * s,
                right: (comp.schedule.x + comp.schedule.pad) * s,
                top: layout.scheduleY,
                height: layout.scheduleH,
              }}
            >
              <ScrollView scrollEnabled={scheduleOpen} showsVerticalScrollIndicator={false}>
                {renderRows ? (
                  <Animated.View style={customRowsStyle}>{renderRows({ minutes })}</Animated.View>
                ) : (
                  rows.map((row, i) => (
                    <ScheduleRow
                      key={row.key}
                      index={i}
                      title={row.title}
                      clock={timeLabel(row.m, day, rest, false)}
                      color={row.color}
                      layout={layout}
                      wake={wake}
                      reveal={reveal}
                      clockNow={clock}
                      tickAt={row.tickAt}
                      th={th}
                      text={text}
                    />
                  ))
                )}
              </ScrollView>
            </View>
            {hintText !== null ? (
              <Animated.View
                pointerEvents={hintLive ? 'box-none' : 'none'}
                accessibilityElementsHidden={!hintLive}
                importantForAccessibility={hintLive ? 'auto' : 'no-hide-descendants'}
                style={[StyleSheet.absoluteFill, hintGroup]}
              >
                <Animated.View
                  pointerEvents="none"
                  style={[
                    {
                      position: 'absolute',
                      left: width / 2 - (comp.hint.stem * s) / 2,
                      width: comp.hint.stem * s,
                      backgroundColor: th.accent,
                    },
                    hintStem,
                  ]}
                />
                <Animated.View
                  pointerEvents="none"
                  style={[
                    {
                      position: 'absolute',
                      left: width / 2 - comp.hint.tipR * s,
                      top: layout.tipY - comp.hint.tipR * s,
                      width: comp.hint.tipR * 2 * s,
                      height: comp.hint.tipR * 2 * s,
                      borderRadius: comp.hint.tipR * s,
                      backgroundColor: th.accent,
                    },
                    hintTip,
                  ]}
                />
                <Animated.View pointerEvents="none" style={[{ position: 'absolute', overflow: 'hidden' }, hintPill]}>
                  <Animated.View
                    style={[
                      {
                        position: 'absolute',
                        left: '50%',
                        top: 0,
                        bottom: 0,
                        width: layout.hintW,
                        marginLeft: -layout.hintW / 2,
                        flexDirection: 'row',
                        alignItems: 'center',
                        paddingLeft: 10 * s,
                        paddingRight: 4 * s,
                      },
                      hintLabel,
                    ]}
                  >
                    <Text
                      style={{
                        fontFamily: th.label,
                        fontSize: sys.type.label.size * s,
                        lineHeight: sys.type.label.lineHeight * s,
                        color: sys.ink.onAccent,
                        flex: 1,
                      }}
                    >
                      {hintText}
                    </Text>
                    <View accessible={false} style={{ minWidth: 24 * s, alignItems: 'center', justifyContent: 'center' }}>
                      <Svg width={comp.hint.close * s} height={comp.hint.close * s} viewBox="0 0 16 16">
                        <Circle cx="8" cy="8" r="7" fill={sys.ink.onAccent} />
                        <Path d="M5.5 5.5L10.5 10.5M10.5 5.5L5.5 10.5" stroke={th.accent} strokeWidth="1.4" />
                      </Svg>
                    </View>
                  </Animated.View>
                </Animated.View>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="Dismiss time travel hint"
                  onPress={() => setHintDismissed(true)}
                  style={{
                    position: 'absolute',
                    width: 48,
                    height: 48,
                    left: width - layout.hintX - 16 * s - 24,
                    top: layout.hintY + layout.hintH / 2 - 24,
                  }}
                />
              </Animated.View>
            ) : null}
            <Control
              label="Preview sunset"
              layout={layout}
              x={comp.controls.left}
              onPress={() => travelTo(day.sunset)}
              icon="sun"
              reduce={reduce}
              tint={tint}
              th={th}
            />
            <Control
              label={`${COPY.city}. Return to now`}
              layout={layout}
              x={comp.controls.cityX}
              wide
              onPress={() => travelTo(rest)}
              icon="location"
              reduce={reduce}
              tint={tint}
              th={th}
            />
            <Control
              label={scheduleOpen ? 'Hide solar times' : 'Show solar times'}
              layout={layout}
              x={comp.controls.rightX}
              onPress={() => {
                setScheduleOpen(!scheduleOpen);
                cue('onset', '');
              }}
              icon="grid"
              reduce={reduce}
              tint={tint}
              th={th}
            />
          </View>
        </>
      ) : (
        <Text accessibilityRole="text" style={[text, { position: 'absolute', top: insets.top + 24, alignSelf: 'center' }]}>
          {fontError ? 'Unable to load typeface' : !EFFECT ? 'Sky renderer unavailable' : ''}
        </Text>
      )}
    </View>
  );
}

/** A transparent 48 pt handle riding the sun disc (or its grey marker at night); dragging it is the same clock as the ruler. */
function SunGrab({
  gesture,
  sunState,
  held,
  layout,
  day,
  projection,
}: {
  gesture: ReturnType<typeof Gesture.Pan>;
  sunState: SharedValue<{ elevation: number; x: number; day: number }>;
  held: SharedValue<number>;
  layout: StsLayout;
  day: Day;
  projection: number;
}) {
  const { s, horizon, arcCentre, arcAmplitude } = layout;
  const radius = GESTURE.grabRadius;
  const style = useAnimatedStyle(() => {
    const sun = sunState.get();
    const night = sun.elevation < -2;
    const y = night ? archY(sun.x, arcCentre / s, arcAmplitude / s) * s : horizon - sunHeight(sun.elevation, day, projection) * s;
    const hidden = night && held.get() < 0.3;
    return { transform: [{ translateX: hidden ? -1000 : sun.x * s - radius }, { translateY: y - radius }] };
  });
  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        accessible={false}
        importantForAccessibility="no"
        style={[{ position: 'absolute', left: 0, top: 0, width: radius * 2, height: radius * 2 }, style]}
      />
    </GestureDetector>
  );
}

/** One solar-time row: dim until the sky is scrubbed, then rising 40 ms after its neighbour; it ticks as the scrub crosses it. */
function ScheduleRow({
  index,
  title,
  clock,
  color,
  layout,
  wake,
  reveal,
  clockNow,
  tickAt,
  th,
  text,
}: {
  index: number;
  title: string;
  clock: string;
  color: string;
  layout: StsLayout;
  wake: SharedValue<number>;
  reveal: SharedValue<number>;
  clockNow: SharedValue<number>;
  tickAt: SharedValue<number>;
  th: StsResolvedTheme;
  text: { fontFamily: string; fontSize: number; color: string };
}) {
  const { s } = layout;
  const rowStyle = useAnimatedStyle(() => {
    const p = smooth((wake.get() - index * ROWS.stagger) / ROWS.fade);
    return { opacity: ROWS.rest + (1 - ROWS.rest) * Math.max(p, reveal.get()) };
  });
  const dim = rgbString(
    mix(0, parseInt(color.slice(1, 3), 16), 0.78),
    mix(0, parseInt(color.slice(3, 5), 16), 0.78),
    mix(0, parseInt(color.slice(5, 7), 16), 0.78),
  );
  const valueStyle = useAnimatedStyle(() => {
    const age = clockNow.get() - tickAt.get();
    const env = age >= 0 && age < ROWS.tick ? Math.exp(-age / 0.09) : 0;
    // The value alone swells, from its right edge, so the row never grows past the list's clip.
    return { color: interpolateColor(env, [0, 1], [dim, color]), transform: [{ scale: 1 + 1.5 * ROWS.tickScale * env }] };
  });
  return (
    <Animated.View
      style={[
        {
          minHeight: layout.rowH,
          flexDirection: layout.largeText ? 'column' : 'row',
          alignItems: layout.largeText ? 'flex-start' : 'center',
          justifyContent: 'space-between',
          gap: 8 * s,
        },
        rowStyle,
      ]}
    >
      <Text style={[text, { flexShrink: 1 }]}>{title}</Text>
      <Animated.Text
        style={[
          {
            fontFamily: th.value,
            fontSize: sys.type.value.size * s,
            fontVariant: ['tabular-nums'],
            flexShrink: 1,
            transformOrigin: 'right',
          },
          valueStyle,
        ]}
      >
        {clock}
      </Animated.Text>
    </Animated.View>
  );
}

/** Isolated accessibility value: no per-minute React updates reach the rendering screen. */
function RulerAccessibility({
  pan,
  minutes,
  clock,
  layout,
  travelTo,
  day,
  rest,
}: {
  pan: ReturnType<typeof Gesture.Pan>;
  minutes: SharedValue<number>;
  clock: SharedValue<number>;
  layout: StsLayout;
  travelTo: (m: number) => void;
  day: Day;
  rest: number;
}) {
  const [selection, setSelection] = useState('');
  const previous = useSharedValue({ minute: -1, at: -1 });
  useAnimatedReaction(
    () => ({ minute: Math.round(wrapMinutes(minutes.get())), now: clock.get() }),
    ({ minute, now }) => {
      const p = previous.get();
      if (minute !== p.minute && now - p.at >= GESTURE.accessibilityUpdate) {
        previous.set({ minute, at: now });
        scheduleOnRN(setSelection, timeLabel(minute, day, rest));
      }
    },
  );
  const { s, rulerY } = layout;
  return (
    <GestureDetector gesture={pan}>
      <View
        style={{ position: 'absolute', left: 20 * s, right: 20 * s, top: rulerY - 40 * s, height: 88 * s }}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={COPY.ruler}
        accessibilityHint={COPY.rulerHint}
        accessibilityValue={{ text: selection }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) =>
          travelTo(minutes.get() + (event.nativeEvent.actionName === 'increment' ? GESTURE.step : -GESTURE.step))
        }
      />
    </GestureDetector>
  );
}

function Control({
  label,
  layout,
  x,
  wide = false,
  onPress,
  icon,
  reduce,
  tint,
  th,
}: {
  label: string;
  layout: StsLayout;
  x: number;
  wide?: boolean;
  onPress: () => void;
  icon: 'sun' | 'location' | 'grid';
  reduce: boolean;
  tint: SharedValue<number[]>;
  th: StsResolvedTheme;
}) {
  const scale = useSharedValue(1);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: reduce ? 1 : scale.get() }], opacity: scale.get() < 1 ? 0.7 : 1 }));
  // The sky lights the UI: a 1 px rim along the top edge in the horizon's colour, as bright as the horizon is.
  const rimStyle = useAnimatedStyle(() => {
    const c = tint.get();
    return { borderTopColor: rgbString(c[3], c[4], c[5], comp.rim.alpha * luminance(c[3], c[4], c[5])) };
  });
  // The city arrow takes a fifth of the horizon's colour.
  const arrowProps = useAnimatedProps(() => {
    const c = tint.get();
    const k = comp.rim.arrow;
    return { fill: rgbString(mix(0x44, c[3], k), mix(0xc2, c[4], k), mix(0xf5, c[5], k)) };
  });
  const { s, controlsY } = layout;
  const radius = (comp.controls.h * s) / 2;
  return (
    <Animated.View
      style={[{ position: 'absolute', left: x * s, top: controlsY, width: (wide ? comp.controls.cityW : comp.controls.h) * s }, style]}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={onPress}
        onPressIn={() => scale.set(withSpring(comp.feedback.pressed, SPRINGS.snap))}
        onPressOut={() => scale.set(withSpring(1, SPRINGS.snap))}
        style={{
          minHeight: Math.max(48, wide ? layout.controlsH : comp.controls.h * s),
          borderRadius: radius,
          backgroundColor: sys.surface.control,
          borderWidth: StyleSheet.hairlineWidth,
          borderColor: sys.stroke.outline,
          flexDirection: wide && layout.largeText ? 'column' : 'row',
          gap: 8 * s,
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: 8 * s,
          paddingVertical: 8 * s,
        }}
      >
        <Svg width={comp.controls.icon * s} height={comp.controls.icon * s} viewBox="0 0 24 24">
          {icon === 'location' ? (
            <AnimatedPath d="M3 10.3L21 3L13.6 21L11.1 12.9Z" fill={sys.color.location} animatedProps={arrowProps} />
          ) : icon === 'grid' ? (
            <>
              <Rect x="3" y="4" width="10" height="7" rx="1" fill={sys.ink.secondary} />
              <Rect x="3" y="14" width="10" height="6" rx="1" fill={sys.ink.secondary} />
              <Rect x="16" y="4" width="5" height="16" rx="1" fill={sys.ink.secondary} />
            </>
          ) : (
            <>
              <Circle cx="12" cy="12" r="4" fill={sys.ink.secondary} />
              <Path
                d="M12 2V4M12 20V22M2 12H4M20 12H22M5 5L6.5 6.5M17.5 17.5L19 19M5 19L6.5 17.5M17.5 6.5L19 5"
                stroke={sys.ink.secondary}
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            </>
          )}
        </Svg>
        {wide ? (
          <Text
            style={{
              fontFamily: th.body,
              fontSize: sys.type.body.size * s,
              lineHeight: sys.type.body.lineHeight * s,
              color: sys.ink.primary,
              flexShrink: 1,
            }}
          >
            {COPY.city}
          </Text>
        ) : null}
      </Pressable>
      <Animated.View
        pointerEvents="none"
        style={[
          { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: radius, borderTopWidth: comp.rim.width },
          rimStyle,
        ]}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({ screen: { flex: 1, overflow: 'hidden' } });
