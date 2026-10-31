import { Canvas, Fill, Path, Picture, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { COPY, sceneFx, STATES, type Weather } from './scw-scene';
import { type Cue, useScwAudio } from './use-scw-audio';

/*
 Storm Cloud Weather — the today screen of an invented weather app.

 Look (the Dribbble shot, a still): a dark navy page, the city and the date, a big glossy clay storm
 cloud with a purple crescent moon behind it, an orange bolt and blue drops, Wind / Temp / Humidity,
 then "Today" with three orange hour cards carrying small 3D weather icons.

 Motion (ours): the icon is a real 3D scene, ray-marched in a shader: drag it sideways and it turns,
 showing the puffs' depth, and springs back. Tap an hour card and the weather morphs into it: the
 cloud swells rounder and lightens, the moon shrinks away as the sun grows, the bolt draws in, the
 rain stops, and the figures count to the new values. In the storm the bolt flickers and lights the
 cloud from below.

 autoPlay: LOOP = 9 s. Storm (flash at 1.2 s), 2.6 s showers, 5.6 s partly sunny, 8.0 s back to the
 storm; each morph 0.8 s. Frame 9 s equals frame 0. Reduced motion: no turning, no flicker; states
 crossfade quickly.
*/

let debugState = '';
const LOOP = 9;
const MORPH = 0.8;

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
/** The script: which state, which one before, how far the morph is, at loop time t. */
const scriptAt = (t: number) => {
  'worklet';
  if (t >= 8.0) return { from: 2, to: 0, m: easeInOut((t - 8.0) / MORPH) };
  if (t >= 5.6) return { from: 1, to: 2, m: easeInOut((t - 5.6) / MORPH) };
  if (t >= 2.6) return { from: 0, to: 1, m: easeInOut((t - 2.6) / MORPH) };
  return { from: 0, to: 0, m: 1 };
};
const blend = (a: Weather, b: Weather, k: number) => {
  'worklet';
  return {
    cloud: [mix(a.cloud[0], b.cloud[0], k), mix(a.cloud[1], b.cloud[1], k), mix(a.cloud[2], b.cloud[2], k)],
    moon: mix(a.moon, b.moon, k),
    sun: mix(a.sun, b.sun, k),
    bolt: mix(a.bolt, b.bolt, k),
    rain: mix(a.rain, b.rain, k),
    puff: mix(a.puff, b.puff, k),
  };
};

type Props = { autoPlay?: boolean };

export function StormCloudWeather({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [fontsOk] = useFonts({
    'SCW-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'SCW-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'SCW-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'SCW-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const SCN = { w: w, h: 350 };
  const SY = insets.top + 78; // the scene canvas' top
  const CARD_W = (w - 48 - 20) / 3;

  const play = useScwAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const st = useSharedValue({ from: 0, to: 0, at: -10, m: 1, flash: 0, nextFlash: 1.2 });
  const yaw = useSharedValue({ y: 0, v: 0, held: 0, start: 0 });
  const debugT = useSharedValue(-1);
  const [sel, setSel] = useState(0);
  const [nums, setNums] = useState({ temp: COPY[0].temp, wind: COPY[0].wind, humidity: COPY[0].humidity });

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __stormCloud?: unknown };
    g.__stormCloud = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__stormCloud;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const s = st.get();
    const yw = yaw.get();
    let t = time.get();
    const scripted = dbg >= 0 || (auto && yw.held === 0 && now - last.get() >= 2 && dbg !== -2);
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
    } else if (scripted) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    if (scripted || dbg === -2) {
      const sc = scriptAt(t);
      s.from = sc.from;
      s.to = sc.to;
      s.m = sc.m;
      s.flash = reduced ? 0 : Math.exp(-Math.pow((t - 1.2) / 0.06, 2)) + 0.6 * Math.exp(-Math.pow((t - 1.38) / 0.05, 2));
      if (yw.held === 0) yw.y = reduced ? 0 : 0.32 * Math.sin((2 * Math.PI * t) / LOOP);
    } else {
      s.m = reduced ? 1 : easeInOut((now - s.at) / MORPH);
      // the storm flickers now and then
      if (s.to === 0 && !reduced && now > s.nextFlash) {
        s.nextFlash = now + 3.2 + Math.random() * 2;
        s.flash = 1;
        scheduleOnRN(cue, 'thunder');
      }
      s.flash *= Math.exp(-dt * 9);
      if (yw.held === 0) {
        yw.v += (-120 * yw.y - 2 * Math.sqrt(120) * 0.35 * yw.v) * dt;
        yw.y += yw.v * dt;
      }
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} ${s.from}->${s.to} m=${s.m.toFixed(2)} yaw=${yw.y.toFixed(2)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // the figures count to the new values; the selected card follows the state being entered
  useAnimatedReaction(
    () => {
      tick.get();
      const s = st.get();
      const a = COPY[s.from];
      const b = COPY[s.to];
      return {
        temp: Math.round(mix(a.temp, b.temp, s.m)),
        wind: Math.round(mix(a.wind, b.wind, s.m)),
        humidity: Math.round(mix(a.humidity, b.humidity, s.m)),
        sel: s.to,
      };
    },
    (n, prev) => {
      if (!prev || n.temp !== prev.temp || n.wind !== prev.wind || n.humidity !== prev.humidity) scheduleOnRN(setNums, { temp: n.temp, wind: n.wind, humidity: n.humidity });
      if (!prev || n.sel !== prev.sel) scheduleOnRN(setSel, n.sel);
    },
  );

  const uniforms = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const k = blend(STATES[s.from], STATES[s.to], s.m);
    return {
      c: [w / 2, SCN.h * 0.47],
      S: Math.min(128, w * 0.32),
      yaw: yaw.get().y,
      t: time.get() + (auto ? 0 : clock.get()),
      cloud: k.cloud,
      moon: k.moon,
      sun: k.sun,
      bolt: k.bolt,
      rain: k.rain,
      flash: s.to === 0 ? s.flash : 0,
      puff: k.puff,
    };
  });
  // the sky behind everything follows the weather (as a weather app's background does): a deep
  // storm navy, a cooler slate for showers, a warm dusk blue with a sun glow for the sunny spell,
  // with soft cloud banks drifting across it
  const sky = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const k = blend(STATES[s.from], STATES[s.to], s.m);
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, 1000));
    const sunK = k.sun;
    const rainK = k.rain * (1 - k.bolt);
    const top = [mix(mix(0.05, 0.14, rainK), 0.16, sunK), mix(mix(0.1, 0.2, rainK), 0.3, sunK), mix(mix(0.16, 0.27, rainK), 0.48, sunK)];
    const bot = [mix(mix(0.03, 0.06, rainK), 0.05, sunK), mix(mix(0.06, 0.1, rainK), 0.11, sunK), mix(mix(0.1, 0.15, rainK), 0.2, sunK)];
    const g = Skia.Paint();
    g.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 700 }, [Skia.Color(`rgb(${Math.round(top[0] * 255)},${Math.round(top[1] * 255)},${Math.round(top[2] * 255)})`), Skia.Color(`rgb(${Math.round(bot[0] * 255)},${Math.round(bot[1] * 255)},${Math.round(bot[2] * 255)})`)], null, 0));
    c.drawRect(Skia.XYWHRect(0, 0, w, 1000), g);
    if (sunK > 0.01) {
      const glow = Skia.Paint();
      glow.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.75, y: 150 }, w * 0.8, [Skia.Color('rgba(255,170,80,0.42)'), Skia.Color('rgba(255,140,60,0)')], null, 0));
      glow.setAlphaf(sunK);
      c.drawRect(Skia.XYWHRect(0, 0, w, 1000), glow);
    }
    // cloud banks: soft blurred ovals drifting once a loop, denser in the storm
    const dens = mix(1, 0.35, sunK);
    const ph = (auto ? time.get() : clock.get()) / LOOP;
    const cp = Skia.Paint();
    cp.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 30, true));
    for (let i = 0; i < 5; i++) {
      const x = ((((i * 0.27 + ph * (0.6 + i * 0.1)) % 1) + 1) % 1) * (w + 300) - 150;
      const y = 120 + i * 70;
      cp.setColor(Skia.Color(i % 2 ? '#8fa6c4' : '#5f7591'));
      cp.setAlphaf(0.13 * dens);
      c.drawOval(Skia.XYWHRect(x - 130, y - 30, 260, 60), cp);
    }
    return rec.finishRecordingAsPicture();
  });

  const flashStyle = useAnimatedStyle(() => {
    tick.get();
    const s = st.get();
    return { opacity: s.to === 0 ? 0.08 * s.flash : 0 };
  });

  const turn = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      const yw = yaw.get();
      yw.held = 1;
      yw.start = yw.y;
      last.set(clock.get());
    })
    .onUpdate((e) => {
      const yw = yaw.get();
      last.set(clock.get());
      yw.y = Math.max(-0.9, Math.min(0.9, yw.start + e.translationX / 220));
    })
    .onFinalize((e) => {
      const yw = yaw.get();
      yw.held = 0;
      yw.v = e.velocityX / 220;
      last.set(clock.get());
    });

  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const pickUI = (i: number) => {
    'worklet';
    const s = st.get();
    last.set(clock.get());
    if (i === s.to) return;
    // morph from wherever it is now
    s.from = s.m >= 0.5 ? s.to : s.from;
    s.to = i;
    s.at = clock.get();
    s.m = 0;
    s.nextFlash = clock.get() + 1.2;
    scheduleOnRN(cue, 'morph');
    scheduleOnRN(buzz, 0);
  };
  const pick = (i: number) => scheduleOnUI(pickUI, i);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={sky} />
        </Canvas>
      ) : null}
      <Animated.View style={[StyleSheet.absoluteFill, styles.flash, flashStyle]} pointerEvents="none" />
      <View style={[styles.head, { top: insets.top + 14 }]}>
        <Text style={styles.city} allowFontScaling={false} accessibilityRole="header">
          Porto, Portugal
        </Text>
        <Text style={styles.date} allowFontScaling={false}>
          Today 31 Oct, 2026 · {COPY[sel].sky}
        </Text>
      </View>
      <GestureDetector gesture={turn}>
        <View style={[styles.scene, { top: SY, height: SCN.h }]} accessible accessibilityRole="image" accessibilityLabel={`${COPY[sel].sky}, ${nums.temp} degrees`}>
          {size && sceneFx ? (
            <Canvas style={{ width: SCN.w, height: SCN.h }}>
              <Fill>
                <Shader source={sceneFx} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.stats, { top: SY + SCN.h - 6 }]}>
        {[
          ['Wind', `${nums.wind}km/h`],
          ['Temp', `${nums.temp}°`],
          ['Humidity', `${nums.humidity}%`],
        ].map(([k, v]) => (
          <View key={k} style={styles.stat}>
            <Text style={styles.statLabel} allowFontScaling={false}>
              {k}
            </Text>
            <Text style={styles.statValue} allowFontScaling={false}>
              {v}
            </Text>
          </View>
        ))}
      </View>
      <View style={[styles.panel, { top: SY + SCN.h + 80 }]}>
        <View style={styles.panelHead}>
          <Text style={styles.today} allowFontScaling={false}>
            Today
          </Text>
          <Text style={styles.next} allowFontScaling={false}>
            Next Week ⌄
          </Text>
        </View>
        <View style={styles.cards}>
          {COPY.map((cp, i) => (
            <Pressable
              key={cp.label}
              accessibilityRole="button"
              accessibilityState={{ selected: sel === i }}
              accessibilityLabel={`${cp.label}, ${cp.temp} degrees, ${cp.sky}`}
              onPress={() => pick(i)}
              style={({ pressed }) => [styles.hour, { width: CARD_W }, sel === i && styles.hourOn, pressed && styles.pressed]}
            >
              <Text style={styles.hourTime} allowFontScaling={false}>
                {cp.label}
              </Text>
              <Text style={styles.hourTemp} allowFontScaling={false}>
                {cp.temp}°
              </Text>
              {sceneFx ? <HourIcon i={i} /> : null}
            </Pressable>
          ))}
        </View>
      </View>
      <View style={[styles.tabs, { bottom: insets.bottom + 6 }]}>
        {TABS.map((d, i) => (
          <TabIcon key={i} d={d} on={i === 0} />
        ))}
      </View>
    </View>
  );
}

const TABS = [
  'M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z',
  'M11 4a7 7 0 1 0 0.01 0 M16 16l4.5 4.5',
  'M12 12a4 4 0 1 0 0.01 0 M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5',
  'M12 9a3 3 0 1 0 0.01 0 M12 2.5v3 M12 18.5v3 M2.5 12h3 M18.5 12h3 M5.3 5.3l2.1 2.1 M16.6 16.6l2.1 2.1 M5.3 18.7l2.1-2.1 M16.6 7.4l2.1-2.1',
];
function TabIcon({ d, on }: { d: string; on: boolean }) {
  const path = useDerivedValue(() => Skia.Path.MakeFromSVGString(d) ?? Skia.PathBuilder.Make().build());
  return (
    <Canvas style={styles.tabIcon}>
      <Path path={path} style="stroke" strokeWidth={1.9} strokeCap="round" strokeJoin="round" color={on ? '#ffffff' : 'rgba(255,255,255,0.45)'} transform={[{ scale: 26 / 24 }]} />
    </Canvas>
  );
}

/** The hour card's small icon: the same scene, rendered once for that hour's weather. */
function HourIcon({ i }: { i: number }) {
  const k = STATES[i];
  const pic = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, 56, 56));
    if (sceneFx) {
      const p = Skia.Paint();
      p.setShader(sceneFx.makeShader([28, 30, 21, -0.2, 0.6, k.cloud[0], k.cloud[1], k.cloud[2], k.moon, k.sun, k.bolt, k.rain, 0, k.puff]));
      c.drawRect(Skia.XYWHRect(0, 0, 56, 56), p);
    }
    return rec.finishRecordingAsPicture();
  });
  return (
    <Canvas style={styles.icon} pointerEvents="none">
      <Picture picture={pic} />
    </Canvas>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0d1b28' },
  flash: { backgroundColor: '#dbe6ff' },
  head: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 6 },
  city: { color: '#ffffff', fontSize: 27, fontFamily: 'SCW-Title' },
  date: { color: 'rgba(255,255,255,0.75)', fontSize: 14, fontFamily: 'SCW-Regular' },
  scene: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  stats: { position: 'absolute', left: 30, right: 30, flexDirection: 'row', justifyContent: 'space-between' },
  stat: { alignItems: 'center', gap: 4, minWidth: 90 },
  statLabel: { color: 'rgba(255,255,255,0.72)', fontSize: 15, fontFamily: 'SCW-Regular' },
  statValue: { color: '#ffffff', fontSize: 25, fontFamily: 'SCW-SemiBold', fontVariant: ['tabular-nums'] },
  panel: { position: 'absolute', left: 0, right: 0, bottom: 0, borderTopLeftRadius: 34, borderTopRightRadius: 34, backgroundColor: '#0a1621', paddingHorizontal: 24, paddingTop: 20 },
  panelHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  today: { color: '#ffffff', fontSize: 17, fontFamily: 'SCW-SemiBold' },
  next: { color: 'rgba(255,255,255,0.5)', fontSize: 14, fontFamily: 'SCW-Regular' },
  cards: { flexDirection: 'row', gap: 10, marginTop: 16 },
  hour: { height: 104, borderRadius: 18, backgroundColor: '#e8861a', padding: 12, justifyContent: 'flex-end', opacity: 0.82 },
  hourOn: { backgroundColor: '#f5a21f', opacity: 1, transform: [{ translateY: -6 }], borderWidth: 2, borderColor: 'rgba(255,255,255,0.75)' },
  hourTime: { color: '#ffffff', fontSize: 15, fontFamily: 'SCW-Regular' },
  hourTemp: { color: '#ffffff', fontSize: 24, fontFamily: 'SCW-SemiBold' },
  icon: { position: 'absolute', right: 6, top: 6, width: 56, height: 56 },
  pressed: { transform: [{ scale: 0.97 }] },
  tabs: { position: 'absolute', left: 20, right: 20, height: 60, borderRadius: 24, backgroundColor: '#13263a', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around' },
  tabIcon: { width: 28, height: 28 },
});

export { LOOP };
