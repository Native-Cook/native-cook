import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { dusk, evening, frameAt, lamp, LOOP, moonAngle, night, smooth, sunAngle, THEME_DOWN, THEME_FADE, THEME_UP, windows } from './crs-motion';
import { CRS_HERO_SKSL } from './crs-hero-shader';
import { CRS_ICON_SKSL } from './crs-icon-shader';
import { BAND_H, CLOCK, FONT, ICON_COL, makeLayout, PALETTE, ROW_H, ROWS, SCENE_H, SPACE, SWITCH, TILE, TRACK_ON } from './crs-theme';

/*
 Clay Relight Settings — the settings of a phone, every icon made of clay, relit by the evening.

 The screen: the title; the Appearance card, a clay house on a hill under the sun, with the time,
 Dark mode and its switch; the list (Notifications, Focus, Display & Brightness, Battery, Privacy &
 Security), each with a clay icon.

 Signature: Dark mode is not a theme switch, it is sunset. Turn it on and the sun goes down behind
 the far hill: every shadow on the screen (the trees' and the house's, each symbol's on its tile,
 each tile's on the list) turns and stretches with it, the light warms, the sky and the page go to
 night, the house's windows and the lamp post come on, and then each icon's lamp lights in turn —
 the bell's badge, the crescent, the bulb, the charging bolt, the keyhole — as the rows' values
 change to their night settings. One number, m, is the whole evening; drag the sky to scrub it, let
 go and it settles on day or night at the speed the finger left it. Tap the switch again mid-sunset
 and the same evening runs back from where it is.

 autoPlay: LOOP = 10 s (switch on: sunset and night; a finger pulls the sky back to dusk and lets go;
 switch off: sunrise); frame 10 s equals frame 0. A touch takes over; 2 s after the last one the
 loop eases back (bringing the day back first if it is night).
 Reduced motion: the switch changes the light in a short fade, the clouds and stars hold still; the
 sky can still be dragged.
*/

const hero = Skia.RuntimeEffect.Make(CRS_HERO_SKSL);
if (!hero) throw new Error('Clay Relight Settings: the scene shader failed to compile.');
const icons = Skia.RuntimeEffect.Make(CRS_ICON_SKSL);
if (!icons) throw new Error('Clay Relight Settings: the icon shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_EVENING = Easing.bezier(0.45, 0, 0.55, 1);
const NEVER = ReduceMotion.Never;
const AnimatedInput = Animated.createAnimatedComponent(TextInput);

function buzz(kind: 'light' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tone(key: keyof typeof PALETTE, m: number, theme: number) {
  'worklet';
  const c = PALETTE[key];
  const lit = interpolateColor(Math.min(m, 0.5), [0, 0.5], [c[0], c[1]]);
  return interpolateColor(theme, [0, 1], [lit, c[2]]);
}

function clockText(m: number) {
  'worklet';
  const raw = CLOCK.from + evening(m) * CLOCK.span;
  const mins = Math.round(raw / 5) * 5;
  const h24 = Math.floor(mins / 60) % 24;
  const mm = mins % 60;
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12;
  return `${h12}:${mm < 10 ? '0' : ''}${mm} ${h24 < 12 ? 'AM' : 'PM'}`;
}

type Props = { autoPlay?: boolean };

export function ClayRelightSettings({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [width, setWidth] = useState(402);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const L = useMemo(() => makeLayout(width, insets.top), [width, insets.top]);
  const [dark, setDark] = useState(false); // the status bar and the switch's state for VoiceOver

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const drift = useSharedValue(0); // the clouds' and stars' own clock, never reset
  const mode = useSharedValue(auto ? 0 : 1); // 0 the loop, 1 the finger
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0); // dev: keep the finger's state (no resume), for testing touches

  const m = useSharedValue(0); // the evening, 0 afternoon … 1 night
  const theme = useSharedValue(0); // the screen's chrome, 0 day … 1 night (a short fade once the sun is down)
  const themeOn = useSharedValue(0);
  const on = useSharedValue(0); // the switch
  const knob = useSharedValue(0);
  const press = useSharedValue(0);
  const grab = useSharedValue(0); // a finger on the sky
  const m0 = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const fingerX = useSharedValue(0);

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (!reduced) drift.set(drift.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      m.set(s.m);
      theme.set(s.theme);
      themeOn.set(s.theme > 0.5 ? 1 : 0);
      on.set(s.on);
      knob.set(s.knob);
      press.set(s.press);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      fingerX.set(s.fingerX);
      grab.set(s.fingerAt === 1 ? s.finger : 0);
      return;
    }
    // the evening still running after the last touch counts as activity: the 2 s start once it rests
    if (resumeAsked.get() === 0 && Math.abs(m.get() - on.get()) > 0.002) lastTouch.set(clock.get());
    if (auto && stay.get() === 0 && resumeAsked.get() === 0 && grab.get() === 0 && clock.get() - lastTouch.get() > 2) {
      resumeAsked.set(1);
      // the loop starts in the afternoon: bring the day back first
      if (on.get() === 1 || m.get() > 0.002) {
        on.set(0);
        knob.set(withTiming(0, { duration: 280, easing: EASE_OUT }));
        m.set(withTiming(0, { duration: 500 + 2500 * m.get(), easing: EASE_EVENING }));
      }
    } else if (resumeAsked.get() === 1 && m.get() < 0.002) {
      resumeAsked.set(0);
      m.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__crs = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), m: +m.get().toFixed(3), on: on.get(), knob: +knob.get().toFixed(2) }),
    };
  }, [pin, stay, mode, m, on, knob]);

  // under the finger the chrome steps to night once the sun is down, and back once it is up again
  useAnimatedReaction(
    () => m.get(),
    (k) => {
      if (mode.get() === 0) return;
      if (themeOn.get() === 0 && k > THEME_UP) {
        themeOn.set(1);
        theme.set(withTiming(1, { duration: THEME_FADE * 1000, easing: EASE_IN_OUT }));
      } else if (themeOn.get() === 1 && k < THEME_DOWN) {
        themeOn.set(0);
        theme.set(withTiming(0, { duration: THEME_FADE * 1000, easing: EASE_IN_OUT }));
      }
    },
  );
  // the status bar and VoiceOver's value follow the chrome
  useAnimatedReaction(
    () => (theme.get() > 0.5 ? 1 : 0),
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setDark, now === 1);
    },
  );
  // a tick as the sun touches the hill, under a finger only
  const onHorizon = useCallback(() => buzz('tick'), []);
  useAnimatedReaction(
    () => (m.get() > 0.47 ? 1 : 0),
    (now, before) => {
      if (before !== null && now !== before && mode.get() === 1 && !reduced) scheduleOnRN(onHorizon);
    },
  );

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      press.set(0);
      grab.set(0);
    }
  };

  // the switch: the evening runs at a walking pace, slowest as the sun meets the hill; tapped again
  // mid-way it turns round where it is. A drag of the sky lets go on a spring with the finger's speed.
  const setTarget = (target: number, velocity: number | null) => {
    'worklet';
    on.set(target);
    knob.set(withTiming(target, { duration: 260, easing: EASE_OUT, reduceMotion: NEVER }));
    if (reduced) m.set(withTiming(target, { duration: 400, reduceMotion: NEVER }));
    else if (velocity === null) m.set(withTiming(target, { duration: 500 + 2500 * Math.abs(target - m.get()), easing: EASE_EVENING, reduceMotion: NEVER }));
    else m.set(withSpring(target, { duration: 1800, dampingRatio: 1, overshootClamping: true, velocity, reduceMotion: NEVER }));
  };

  const onToggle = useCallback(() => buzz('light'), []);
  const toggle = Gesture.Tap()
    .maxDuration(800)
    .onBegin(() => {
      takeOver();
      press.set(withTiming(1, { duration: 100, easing: EASE_OUT }));
    })
    .onEnd(() => {
      setTarget(on.get() === 1 ? 0 : 1, null);
      scheduleOnRN(onToggle);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 150, easing: EASE_OUT }));
    });

  // the sky: drag the sun across it to scrub the evening
  const span = L.cardW * 0.85;
  const sky = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      m.set(m.get()); // catch the evening where it is
      m0.set(m.get());
      grab.set(withTiming(1, { duration: 150 }));
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let v = m0.get() + e.translationX / span;
      // a little give past noon and past midnight
      if (v < 0) v = -0.06 * (1 - Math.exp(v / 0.06));
      if (v > 1) v = 1 + 0.06 * (1 - Math.exp(-(v - 1) / 0.06));
      m.set(v);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      grab.set(withTiming(0, { duration: 250 }));
      const v = e.velocityX / span;
      const projected = m.get() + 0.25 * v;
      setTarget(projected > 0.5 ? 1 : 0, v);
    });

  const nudge = useCallback(
    (dir: 1 | -1) => {
      takeOver();
      setTarget(dir === 1 ? 1 : 0, null);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced],
  );

  /* ------------------------------------------------------------ drawing */
  const heroUniforms = useDerivedValue(() => {
    const k = m.get();
    return {
      uStage: [L.cardW, SCENE_H, pixelRatio, (2 * Math.PI * drift.get()) / LOOP],
      uSky: [k, sunAngle(k), moonAngle(k), night(k)],
      uLamps: [dusk(k), windows(k), lamp(k, 0), (2 * Math.PI * drift.get()) / LOOP],
      uLive: [0, 0, grab.get(), 0],
    };
  });
  const iconUniforms = useDerivedValue(() => {
    const k = m.get();
    return {
      uStage: [ICON_COL, ROWS.length * ROW_H, pixelRatio, ROW_H],
      uSky: [k, sunAngle(k), moonAngle(k), night(k)],
      uGlowA: [lamp(k, 0), lamp(k, 1), lamp(k, 2), lamp(k, 3)],
      uGlowB: lamp(k, 4),
      uDusk: dusk(k),
      uTile: TILE / 2,
    };
  });

  const pageStyle = useAnimatedStyle(() => ({ backgroundColor: tone('page', m.get(), theme.get()) }));
  const cardStyle = useAnimatedStyle(() => ({ backgroundColor: tone('card', m.get(), theme.get()) }));
  const inkStyle = useAnimatedStyle(() => ({ color: tone('ink', m.get(), theme.get()) }));
  const muteStyle = useAnimatedStyle(() => ({ color: tone('mute', m.get(), theme.get()) }));
  const lineStyle = useAnimatedStyle(() => ({ backgroundColor: tone('line', m.get(), theme.get()) }));
  const pillStyle = useAnimatedStyle(() => ({ backgroundColor: tone('pill', m.get(), theme.get()) }));
  const pillInk = useAnimatedStyle(() => ({ color: tone('pillInk', m.get(), theme.get()) }));
  const clockProps = useAnimatedProps(() => ({ text: clockText(m.get()), defaultValue: clockText(m.get()) }) as never);
  // the subtitle rolls over as night falls: the old line leaves upward, the new one rises in
  const subDay = useAnimatedStyle(() => {
    const k = smooth(0.56, 0.66, m.get());
    return { opacity: 1 - smooth(0, 0.6, k), transform: [{ translateY: -6 * k }] };
  });
  const subNight = useAnimatedStyle(() => {
    const k = smooth(0.56, 0.66, m.get());
    return { opacity: smooth(0.4, 1, k), transform: [{ translateY: 6 * (1 - k) }] };
  });

  const trackStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(knob.get(), [0, 1], [tone('trackOff', m.get(), theme.get()), TRACK_ON]),
    transform: [{ scale: reduced ? 1 : 1 - 0.04 * press.get() }],
  }));
  const knobStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: knob.get() * (SWITCH.w - SWITCH.knob - 4) }, { scaleX: 1 + 0.12 * press.get() }],
  }));

  const fingerStyle = useAnimatedStyle(() => {
    const onSky = fingerAt.get() === 1;
    const x = onSky ? L.side + L.cardW * (0.78 - 0.4 * fingerX.get()) : L.side + L.cardW - SPACE.l - SWITCH.w / 2;
    const y = onSky ? L.heroTop + SCENE_H * 0.36 : L.heroTop + SCENE_H + BAND_H / 2;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });

  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    setWidth((v) => (v === w ? v : w));
  }, []);

  if (!fontsLoaded) return <View style={[styles.root, { backgroundColor: PALETTE.page[0] }]} onLayout={onLayout} />;

  return (
    <Animated.View style={[styles.root, pageStyle]} onLayout={onLayout}>
      <StatusBar style={dark ? 'light' : 'dark'} />

      <Animated.Text style={[styles.title, inkStyle, { top: L.titleTop, left: L.side + SPACE.xs }]} accessibilityRole="header">
        Settings
      </Animated.Text>

      {/* the Appearance card */}
      <Animated.View style={[styles.card, cardStyle, { left: L.side, top: L.heroTop, width: L.cardW, height: L.heroH }]}>
        <GestureDetector gesture={sky}>
          <View
            style={{ width: L.cardW, height: SCENE_H }}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Appearance. A clay house on a hill; drag across the sky to move the sun"
            accessibilityValue={{ text: dark ? 'Night' : 'Day' }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(e) => nudge(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
          >
            {active ? (
              <Canvas style={{ width: L.cardW, height: SCENE_H }}>
                <Fill>
                  <Shader source={hero!} uniforms={heroUniforms} />
                </Fill>
              </Canvas>
            ) : null}
            <Animated.View style={[styles.timePill, pillStyle]} pointerEvents="none">
              <AnimatedInput
                editable={false}
                style={[styles.timeText, pillInk]}
                animatedProps={clockProps}
                allowFontScaling={false}
                accessible={false}
              />
            </Animated.View>
          </View>
        </GestureDetector>
        <View style={styles.band}>
          <View style={styles.bandText}>
            <Animated.Text style={[styles.bandTitle, inkStyle]}>Dark mode</Animated.Text>
            <View>
              <Animated.Text style={[styles.sub, muteStyle, subDay]}>Off · sunset at 6:50 PM</Animated.Text>
              <Animated.Text style={[styles.sub, styles.over, muteStyle, subNight]}>On until sunrise, 6:20 AM</Animated.Text>
            </View>
          </View>
          <GestureDetector gesture={toggle}>
            <Animated.View
              style={[styles.track, trackStyle]}
              hitSlop={12}
              accessible
              accessibilityRole="switch"
              accessibilityLabel="Dark mode"
              accessibilityState={{ checked: dark }}
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityAction={() => {
                takeOver();
                setTarget(on.get() === 1 ? 0 : 1, null);
              }}
            >
              <Animated.View style={[styles.knob, knobStyle]} />
            </Animated.View>
          </GestureDetector>
        </View>
      </Animated.View>

      {/* the list */}
      <Animated.Text style={[styles.section, muteStyle, { top: L.sectionTop, left: L.side + SPACE.l }]}>GENERAL</Animated.Text>
      <Animated.View style={[styles.card, cardStyle, { left: L.side, top: L.listTop, width: L.cardW, height: L.listH }]}>
        {active ? (
          <Canvas style={styles.iconCol} pointerEvents="none">
            <Fill>
              <Shader source={icons!} uniforms={iconUniforms} />
            </Fill>
          </Canvas>
        ) : null}
        {ROWS.map((r, i) => (
          <SettingsRow key={r.title} index={i} title={r.title} day={r.day} night={r.night} m={m} inkStyle={inkStyle} muteStyle={muteStyle} lineStyle={lineStyle} last={i === ROWS.length - 1} />
        ))}
      </Animated.View>

      {/* the loop's finger */}
      <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
    </Animated.View>
  );
}

type RowProps = {
  index: number;
  title: string;
  day: string;
  night: string;
  m: { get: () => number };
  inkStyle: object;
  muteStyle: object;
  lineStyle: object;
  last: boolean;
};

function SettingsRow({ index, title, day, night: nightValue, m, inkStyle, muteStyle, lineStyle, last }: RowProps) {
  // the value changes as the row's lamp comes on, crossfading so the text never jumps
  const dayStyle = useAnimatedStyle(() => {
    const k = lamp(m.get(), index);
    return { opacity: 1 - smooth(0, 0.6, k), transform: [{ translateY: -5 * k }] };
  });
  const nightStyle = useAnimatedStyle(() => {
    const k = lamp(m.get(), index);
    return { opacity: smooth(0.4, 1, k), transform: [{ translateY: 5 * (1 - k) }] };
  });
  return (
    <View style={[styles.row, { top: index * ROW_H }]} accessible accessibilityRole="button" accessibilityLabel={title}>
      <View style={styles.rowBody}>
        <Animated.Text style={[styles.rowTitle, inkStyle]} numberOfLines={1}>{title}</Animated.Text>
        <View style={styles.rowEnd}>
          {day ? (
            <View>
              {/* sized by the longer value; the two values crossfade over it */}
              <Text style={[styles.value, styles.hidden]} accessibilityElementsHidden importantForAccessibility="no">{day.length > nightValue.length ? day : nightValue}</Text>
              <Animated.Text style={[styles.value, styles.stack, muteStyle, dayStyle]} numberOfLines={1}>{day}</Animated.Text>
              <Animated.Text style={[styles.value, styles.stack, muteStyle, nightStyle]} numberOfLines={1}>{nightValue}</Animated.Text>
            </View>
          ) : null}
          <Svg width={8} height={13} viewBox="0 0 8 13">
            <SvgPath d="M1.5 1.5l5 5-5 5" stroke="#9AA0AE" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </View>
      </View>
      {last ? null : <Animated.View style={[styles.rowLine, lineStyle]} />}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  title: { position: 'absolute', fontFamily: FONT.semibold, fontSize: 34, lineHeight: 44, letterSpacing: -0.6 },
  card: { position: 'absolute', borderRadius: 28, overflow: 'hidden', borderCurve: 'continuous' },
  timePill: { position: 'absolute', left: SPACE.m, top: SPACE.m, height: 28, paddingHorizontal: 10, borderRadius: 14, justifyContent: 'center' },
  timeText: { fontFamily: FONT.semibold, fontSize: 13, padding: 0, minWidth: 60, fontVariant: ['tabular-nums'] },
  band: { height: BAND_H, flexDirection: 'row', alignItems: 'center', paddingHorizontal: SPACE.l + SPACE.xs, gap: SPACE.m },
  bandText: { flex: 1, gap: 2 },
  sub: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 18 },
  over: { position: 'absolute', left: 0, top: 0 },
  stack: { position: 'absolute', left: 0, right: 0, top: 0 },
  hidden: { opacity: 0 },
  bandTitle: { fontFamily: FONT.medium, fontSize: 16, lineHeight: 22 },
  track: { width: SWITCH.w, height: SWITCH.h, borderRadius: SWITCH.h / 2, padding: 2, justifyContent: 'center' },
  knob: { width: SWITCH.knob, height: SWITCH.knob, borderRadius: SWITCH.knob / 2, backgroundColor: '#FFFFFF', boxShadow: '0 2px 6px rgba(0,0,0,0.18)' },
  section: { position: 'absolute', fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, letterSpacing: 0.6 },
  iconCol: { position: 'absolute', left: 0, top: 0, width: ICON_COL, height: ROWS.length * ROW_H },
  row: { position: 'absolute', left: 0, right: 0, height: ROW_H },
  rowBody: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingLeft: ICON_COL + SPACE.xs, paddingRight: SPACE.l, gap: SPACE.m },
  rowTitle: { flex: 1, fontFamily: FONT.medium, fontSize: 16, lineHeight: 22 },
  rowEnd: { flexDirection: 'row', alignItems: 'center', gap: SPACE.s },
  value: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, textAlign: 'right' },
  rowLine: { position: 'absolute', left: ICON_COL + SPACE.xs, right: 0, bottom: 0, height: StyleSheet.hairlineWidth },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.4)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)', boxShadow: '0 1px 6px rgba(0,0,0,0.2)' },
});
