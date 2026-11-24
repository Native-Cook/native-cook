// Heat Haze Weather — a scorching afternoon you can scrub to evening.
//
// Dayline's today screen: the scene behind it is one Skia runtime effect (hhw-shader.ts), a desert
// road drawn in code with heat haze rising from it. The hour slider moves the afternoon: the sun sinks,
// the haze calms, the colours cool, the temperature rolls and the UV chip follows the forecast.
//
// The reusable piece is this component: give it a place, a date, a forecast (an hourly temperature and
// UV index from 12 PM to 9 PM, and the sunset) and three stats.
//
// autoPlay: LOOP = 10 s. A ghost finger drags the hour from 1 PM to 3:30 PM (the haze builds and the
// temperature counts up), rests on the road's mirage to add a shimmer, drags on to 8 PM (it calms),
// then back to 1 PM; frame 10 s equals frame 0. The slider (with momentum and rubber-banding) or a
// touch on the scene (a local shimmer under the finger) takes over at once; IDLE seconds after the
// last touch the hour eases back to 1 PM and the loop restarts at t = 0. Reduced motion: 1 PM, still.
//
// Thread model: one useFrameCallback on the UI thread writes the hour, the flow phase and the touch;
// the shader's uniforms and every label are derived from them on the UI thread.

import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { PixelRatio, Platform, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import {
  FORECAST,
  type Forecast,
  H_MAX,
  H_MIN,
  H_REST,
  HAZE,
  IDLE,
  LOOP,
  RETURN,
  TOUCH_EASE,
  clamp01,
  duskAt,
  heatAt,
  hourAt,
  rolled,
  sample,
  sceneGhostAt,
  seg,
  sliderGhostAt,
  smooth,
  sunAt,
} from './hhw-motion';
import { HHW_SKSL } from './hhw-shader';
import { HhwHourSlider } from './HhwHourSlider';

export { LOOP };
export type { Forecast };

export type HeatHazeStat = { label: string; value: string };

type Props = {
  place?: string;
  date?: string;
  forecast?: Forecast;
  stats?: HeatHazeStat[];
  autoPlay?: boolean;
  /** the hour the slider rests on, whole or half (13 = 1 PM) */
  onHourChange?: (hour: number) => void;
};

const STATS: HeatHazeStat[] = [
  { label: 'Wind', value: 'NE 9 km/h' },
  { label: 'Humidity', value: '11%' },
  { label: 'Air', value: 'Good · 38' },
];

const EFFECT = Skia.RuntimeEffect.Make(HHW_SKSL);
const PIXEL_RATIO = PixelRatio.get();
const WIDE = Platform.OS === 'ios' ? 1 : 0;
const INK = '#FBF6EE';
const MUTE = 'rgba(251,246,238,0.74)';
const DIGIT = 104;
const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
const UV_NUMBERS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const UV_WORDS = ['Low', 'Moderate', 'High', 'Very high', 'Extreme'];
const CONDITIONS = ['Hot and hazy', 'Scorching', 'Cooling off'];
const GHOST = 46;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
/** after the return, the rest waits for the haze's flow to wrap, so the loop starts on its own phase */
const MODE_WAIT = 3;

function tick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

if (__DEV__ && !EFFECT) console.info('[Heat Haze Weather] the scene shader did not compile');

/** the UV index's band, 0 Low … 4 Extreme, crossing each boundary over a quarter of a point */
function uvBandAt(uv: number) {
  'worklet';
  return smooth(seg(uv, 2.25, 2.75)) + smooth(seg(uv, 5.25, 5.75)) + smooth(seg(uv, 7.25, 7.75)) + smooth(seg(uv, 10.25, 10.75));
}

/** a digit's place on its strip; the tens roll while the ones go from 9 to 10 */
function stripOf(v: number, tens: boolean) {
  'worklet';
  const ones = ((v % 10) + 10) % 10;
  return tens ? (Math.floor(v / 10) % 10) + Math.max(0, ones - 9) : ones;
}

/** how much of condition i shows at hour h */
function conditionAt(h: number, i: number) {
  'worklet';
  const scorch = smooth(seg(h, 14.3, 14.9)) * (1 - smooth(seg(h, 16.6, 17.2)));
  const cool = smooth(seg(h, 16.6, 17.2));
  return i === 0 ? clamp01(1 - scorch - cool) : i === 1 ? scorch : cool;
}

function Digit({ temp, tens }: { temp: SharedValue<number>; tens: boolean }) {
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -DIGIT * stripOf(rolled(temp.get()), tens) }] }));
  return (
    <View style={styles.digitBox}>
      <Animated.View style={strip}>
        {DIGITS.map((d, i) => (
          <View key={i} style={styles.digitCell}>
            <Text style={styles.digit}>{d}</Text>
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

function Fade({ value, index, label, style }: { value: SharedValue<number>; index: number; label: string; style: object }) {
  const fade = useAnimatedStyle(() => {
    const d = index - value.get();
    return { opacity: clamp01(1 - Math.abs(d)), transform: [{ translateY: 6 * d }] };
  });
  return <Animated.Text style={[style, fade]}>{label}</Animated.Text>;
}

/** the condition that shows most at hour h */
function conditionOf(h: number) {
  'worklet';
  let best = 0;
  for (let i = 1; i < 3; i += 1) if (conditionAt(h, i) > conditionAt(h, best)) best = i;
  return best;
}

type ConditionProps = { hour: SharedValue<number>; picked: SharedValue<number>; film: SharedValue<number>; index: number; label: string };

/** In the film the conditions cross-fade with the hour; under the finger the leading one fades in on its own, so a resting hour never leaves two half drawn. */
function Condition({ hour, picked, film, index, label }: ConditionProps) {
  const fade = useAnimatedStyle(() => {
    const f = film.get();
    const k = f * conditionAt(hour.get(), index) + (1 - f) * clamp01(1 - Math.abs(picked.get() - index));
    return { opacity: k, transform: [{ translateY: 6 * (1 - k) }] };
  });
  return <Animated.Text style={[styles.condition, fade]}>{label}</Animated.Text>;
}

export function HeatHazeWeather({ place = 'Saltbrush Flats', date = 'Thursday, 14 July', forecast = FORECAST, stats = STATS, autoPlay = true, onHourChange }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const compact = height < 740;
  const temps = forecast.temps;
  const uvs = forecast.uv;
  const sunset = forecast.sunset;

  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const hour = useSharedValue(H_REST);
  /** the hour under the finger (and its momentum after release) */
  const touchHour = useSharedValue(H_REST);
  const flow = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const returnAt = useSharedValue(0);
  const returnFrom = useSharedValue(H_REST);
  const grabHour = useSharedValue(H_REST);
  const trackW = useSharedValue(1);
  const sliderDown = useSharedValue(0);
  const sceneDown = useSharedValue(0);
  const touchX = useSharedValue(0);
  const touchY = useSharedValue(0);
  const touchOn = useSharedValue(0);
  const lastWhole = useSharedValue(H_REST);
  const sliderGhostA = useSharedValue(0);
  const sliderGhostP = useSharedValue(0);
  const sceneGhostA = useSharedValue(0);
  const sceneGhostX = useSharedValue(0);
  const sceneGhostY = useSharedValue(0);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();
    let h = hour.get();
    let aim = sceneDown.get();
    let tx = touchX.get();
    let ty = touchY.get();
    const m = mode.get();

    if (m === MODE_AUTO) {
      if (!auto) {
        autoStart.set(now);
      }
      const t = auto ? (now - autoStart.get()) % LOOP : 0;
      flow.set(t % HAZE.flow);
      h = hourAt(t);
      const sg = sliderGhostAt(t);
      sliderGhostA.set(sg[0]);
      sliderGhostP.set(sg[1]);
      const g = sceneGhostAt(t);
      tx = g[0] * width;
      ty = g[1] * height;
      aim = g[3];
      sceneGhostX.set(tx);
      sceneGhostY.set(ty);
      sceneGhostA.set(g[2]);
      // the ghost's shimmer is part of the film: it follows the script exactly
      touchOn.set(aim);
    } else {
      flow.set((flow.get() + dt) % HAZE.flow);
      if (m === MODE_TOUCH) {
        h = touchHour.get();
        if (auto && sliderDown.get() === 0 && sceneDown.get() === 0 && now - lastTouch.get() >= IDLE) {
          returnFrom.set(h);
          returnAt.set(now);
          mode.set(MODE_RETURN);
        }
      } else if (m === MODE_RETURN) {
        const u = seg(now - returnAt.get(), 0, RETURN);
        h = returnFrom.get() + (H_REST - returnFrom.get()) * smooth(u);
        if (u >= 1) mode.set(MODE_WAIT);
      } else {
        h = H_REST;
        // start the loop as the flow wraps, so the haze carries on without a jump
        if (flow.get() < dt * 1.5) {
          autoStart.set(now);
          flow.set(0);
          mode.set(MODE_AUTO);
        }
      }
      const on = touchOn.get();
      touchOn.set(on + (aim - on) * (1 - Math.exp(-dt * TOUCH_EASE)));
    }
    touchX.set(tx);
    touchY.set(ty);
    hour.set(h);

    // a light tick on each whole hour the finger passes
    const whole = Math.floor(h);
    if (whole !== lastWhole.get()) {
      lastWhole.set(whole);
      if (m === MODE_TOUCH && whole >= H_MIN && whole <= H_MAX) scheduleOnRN(tick);
    }
  }, false);

  useEffect(() => {
    ticker.setActive(!reduced);
    if (reduced) {
      hour.set(H_REST);
      touchOn.set(0);
      sliderGhostA.set(0);
      sceneGhostA.set(0);
    }
    return () => ticker.setActive(false);
  }, [ticker, reduced, hour, touchOn, sliderGhostA, sceneGhostA]);

  // the rounded hour for the host app, sent only when it changes
  const reported = useSharedValue(H_REST);
  // the same half hour for VoiceOver: the headline's digit strips and cross-faded labels are one element each
  const [spoken, setSpoken] = useState(H_REST);
  useDerivedValue(() => {
    const half = Math.round(hour.get() * 2) / 2;
    if (half !== reported.get()) {
      reported.set(half);
      // not while the film plays (it would re-render the screen every half hour of the loop); the loop rests at 1 PM
      if (mode.get() !== MODE_AUTO) scheduleOnRN(setSpoken, half);
      if (onHourChange) scheduleOnRN(onHourChange, half);
    }
  });
  const spokenUv = Math.floor(sample(uvs, spoken) + 0.5);
  const spokenBand = UV_WORDS[Math.min(UV_WORDS.length - 1, Math.max(0, Math.round(uvBandAt(sample(uvs, spoken)))))];
  const spokenCondition = CONDITIONS[[0, 1, 2].reduce((a, i) => (conditionAt(spoken, i) > conditionAt(spoken, a) ? i : a), 0)];

  // the condition under the finger: 1 = follow the film's hour-driven cross-fade, 0 = fade to the leading one
  const condFilm = useSharedValue(1);
  const condPicked = useSharedValue(0);
  useAnimatedReaction(
    () => (mode.get() === MODE_AUTO ? -1 : conditionOf(hour.get())),
    (now, before) => {
      if (now < 0) {
        condFilm.set(1);
        return;
      }
      if (before === null || before < 0) {
        condFilm.set(0);
        condPicked.set(now);
      } else if (now !== before) condPicked.set(withTiming(now, { duration: 220 }));
    },
  );

  const takeOver = () => {
    'worklet';
    if (mode.get() !== MODE_TOUCH) {
      touchHour.set(hour.get());
      sliderGhostA.set(withTiming(0, { duration: 150 }));
      sceneGhostA.set(withTiming(0, { duration: 150 }));
      mode.set(MODE_TOUCH);
    }
    lastTouch.set(clock.get());
  };

  const range = H_MAX - H_MIN;
  /** The throw lands where a 0.994 decay would (v × 0.166 s), on the nearest whole hour: a whole hour's forecast is whole numbers, so the counters never rest half rolled. */
  const settleHour = (v: number) => {
    'worklet';
    const land = Math.round(Math.min(Math.max(touchHour.get() + v * 0.166, H_MIN), H_MAX));
    touchHour.set(withSpring(land, { stiffness: 140, damping: 22, velocity: v }));
  };
  const slide = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      takeOver();
      cancelAnimation(touchHour);
      sliderDown.set(1);
      const w = Math.max(trackW.get(), 1);
      const thumbX = ((touchHour.get() - H_MIN) / range) * w;
      // a touch away from the thumb jumps it there
      if (Math.abs(e.x - thumbX) > 22) touchHour.set(H_MIN + clamp01(e.x / w) * range);
      grabHour.set(touchHour.get() - (e.x / w) * range);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const w = Math.max(trackW.get(), 1);
      const raw = grabHour.get() + (e.x / w) * range;
      // rubber band past either end
      const over = raw < H_MIN ? raw - H_MIN : raw > H_MAX ? raw - H_MAX : 0;
      const band = over / (1 + Math.abs(over) / 0.6);
      touchHour.set(over === 0 ? raw : (over < 0 ? H_MIN : H_MAX) + band * 0.55);
    })
    .onEnd((e) => {
      const w = Math.max(trackW.get(), 1);
      settleHour((e.velocityX / w) * range);
    })
    .onFinalize((_e, success) => {
      // a tap that jumped the thumb never becomes active, so it has no onEnd: settle it here
      if (!success) settleHour(0);
      sliderDown.set(0);
      lastTouch.set(clock.get());
    });

  const poke = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      takeOver();
      sceneDown.set(1);
      touchX.set(e.x);
      touchY.set(e.y);
      scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      touchX.set(e.x);
      touchY.set(e.y);
    })
    .onFinalize(() => {
      sceneDown.set(0);
      lastTouch.set(clock.get());
    });

  const temp = useDerivedValue(() => sample(temps, hour.get()));
  const uvAt = useDerivedValue(() => rolled(sample(uvs, hour.get()) + 0.5, 0.6));
  const band = useDerivedValue(() => uvBandAt(sample(uvs, hour.get())));

  const uniforms = useDerivedValue(() => {
    const h = hour.get();
    const dusk = duskAt(h, sunset);
    const heat = heatAt(sample(temps, h), dusk);
    const sun = sunAt(h, sunset, width, height);
    return {
      size: [width, height],
      sun,
      heat,
      dusk,
      phase: flow.get() / HAZE.flow,
      touch: [touchX.get(), touchY.get(), touchOn.get()],
      wide: WIDE,
      dpr: PIXEL_RATIO,
    };
  });

  const sceneGhost = useAnimatedStyle(() => ({
    opacity: sceneGhostA.get() * 0.9,
    transform: [{ translateX: sceneGhostX.get() - GHOST / 2 }, { translateY: sceneGhostY.get() - GHOST / 2 }],
  }));

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={poke}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          {EFFECT ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="box-none" style={[styles.page, { paddingTop: insets.top + (compact ? 6 : 12), paddingBottom: Math.max(insets.bottom, 12) + (compact ? 4 : 8) }]}>
        <View pointerEvents="none">
          <Text style={styles.place}>{place}</Text>
          <Text style={styles.date}>{date}</Text>
          <View style={[styles.tempRow, compact && styles.tempRowCompact]} accessible accessibilityRole="text" accessibilityLabel={`${Math.round(sample(temps, spoken))}°`}>
            <Digit temp={temp} tens />
            <Digit temp={temp} tens={false} />
            <Text style={styles.degree}>°</Text>
          </View>
          <View style={styles.conditionBox} accessible accessibilityRole="text" accessibilityLabel={spokenCondition}>
            {CONDITIONS.map((c, i) => (
              <Condition key={c} hour={hour} picked={condPicked} film={condFilm} index={i} label={c} />
            ))}
          </View>
          <View style={styles.chip} accessible accessibilityRole="text" accessibilityLabel={`UV ${spokenUv}, ${spokenBand}`}>
            <Text style={styles.chipLabel}>UV</Text>
            <View style={styles.uvNumber}>
              {UV_NUMBERS.map((n) => (
                <Fade key={n} value={uvAt} index={n} label={String(n)} style={styles.chipValue} />
              ))}
            </View>
            <View style={styles.chipDot} />
            <View style={styles.uvWord}>
              {UV_WORDS.map((w, i) => (
                <Fade key={w} value={band} index={i} label={w} style={styles.chipWord} />
              ))}
            </View>
          </View>
        </View>

        <View style={styles.card}>
          <BlurView intensity={28} tint="dark" style={StyleSheet.absoluteFill} />
          <View style={styles.cardShade} pointerEvents="none" />
          <View style={styles.cardBody}>
            <View style={styles.cardHead}>
              <Text style={styles.cardTitle}>Hourly</Text>
              <Text style={styles.cardNote}>Sunset {formatHour(sunset)}</Text>
            </View>
            <HhwHourSlider hour={hour} trackW={trackW} ghostA={sliderGhostA} ghostP={sliderGhostP} gesture={slide} spoken={spoken} />
            <View style={styles.stats}>
              {stats.map((s) => (
                <View key={s.label} style={styles.stat}>
                  <Text style={styles.statLabel}>{s.label}</Text>
                  <Text style={styles.statValue}>{s.value}</Text>
                </View>
              ))}
            </View>
          </View>
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.ghost, sceneGhost]}>
        <View style={styles.ghostCore} />
      </Animated.View>
    </View>
  );
}

function formatHour(h: number) {
  const whole = Math.floor(h);
  const min = Math.round((h - whole) * 60);
  return `${whole % 12 === 0 ? 12 : whole % 12}:${String(min).padStart(2, '0')} ${whole < 12 ? 'AM' : 'PM'}`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#2C69AE', overflow: 'hidden' },
  page: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, paddingHorizontal: 20, justifyContent: 'space-between' },
  place: { color: INK, fontSize: 22, fontWeight: '700', letterSpacing: -0.3, textShadowColor: 'rgba(20,30,60,0.25)', textShadowRadius: 8 },
  date: { color: MUTE, fontSize: 15, fontWeight: '500', marginTop: 2 },
  tempRow: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 4, height: DIGIT },
  tempRowCompact: { marginTop: 0 },
  digitBox: { height: DIGIT, overflow: 'hidden' },
  digitCell: { height: DIGIT, justifyContent: 'center' },
  digit: { color: INK, fontSize: 96, lineHeight: DIGIT, fontWeight: '700', letterSpacing: -3, fontVariant: ['tabular-nums'], textShadowColor: 'rgba(20,30,60,0.22)', textShadowRadius: 14 },
  degree: { color: INK, fontSize: 64, lineHeight: 84, fontWeight: '500', marginLeft: 2 },
  conditionBox: { height: 26, marginTop: -2 },
  condition: { position: 'absolute', left: 0, top: 0, color: INK, fontSize: 20, fontWeight: '600', letterSpacing: -0.2 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    height: 30,
    marginTop: 10,
    paddingHorizontal: 12,
    borderRadius: 15,
    backgroundColor: 'rgba(16,24,48,0.24)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(251,246,238,0.32)',
  },
  chipLabel: { color: MUTE, fontSize: 13, fontWeight: '700' },
  uvNumber: { width: 22, height: 30, marginLeft: 5, justifyContent: 'center' },
  chipValue: { position: 'absolute', left: 0, color: INK, fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'] },
  chipDot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: MUTE, marginRight: 8 },
  uvWord: { width: 74, height: 30, justifyContent: 'center' },
  chipWord: { position: 'absolute', left: 0, color: INK, fontSize: 14, fontWeight: '600' },
  card: {
    borderRadius: 26,
    overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(251,246,238,0.3)',
    boxShadow: '0 2px 4px rgba(20,12,8,0.18), 0 18px 40px rgba(20,12,8,0.22)',
  },
  cardShade: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: 'rgba(28,20,18,0.28)' },
  cardBody: { paddingHorizontal: 18, paddingTop: 16, paddingBottom: 16 },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 },
  cardTitle: { color: INK, fontSize: 17, fontWeight: '700' },
  cardNote: { color: MUTE, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  stats: { flexDirection: 'row', marginTop: 14, paddingTop: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(251,246,238,0.22)' },
  stat: { flex: 1 },
  statLabel: { color: MUTE, fontSize: 12, fontWeight: '500' },
  statValue: { color: INK, fontSize: 15, fontWeight: '600', marginTop: 2, fontVariant: ['tabular-nums'] },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(251,246,238,0.16)',
    borderWidth: 1.5,
    borderColor: 'rgba(251,246,238,0.62)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(251,246,238,0.5)' },
});
