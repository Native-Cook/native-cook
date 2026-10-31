import { Canvas, Fill, Picture, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { bustFx } from './cmr-bust';
import { type Cue, useCmrAudio } from './use-cmr-audio';

/*
 Chrome Match Reveal — the "It's a match!" screen of an invented dating app.

 Look (the pin, a still of three screens; this is the middle one): a black page, two tilted glass
 cards overlapping, each holding a liquid-chrome bust with a pearl tiara (violet and magenta), a
 chrome four-point star where they touch, "It's a match!" in handwriting, YOU AND PURPLE LIKED EACH
 OTHER, a violet Send a message button and a dark Dislike.

 Motion (ours): the busts are real 3D, ray-marched in a shader. The cards slide in from the sides,
 meet with a little squash, the chrome star bursts out where they touch with a scatter of glints, and
 the two heads turn to look at each other. Touch the screen and both heads look at your finger. Dislike
 parts the cards; they come back together a moment later.

 autoPlay: LOOP = 8 s. 0.25-1.05 s the cards slide in, 0.95 s they meet, 1.2-1.9 s the copy comes in,
 6.6-7.5 s they part. Frame 8 s equals frame 0. Reduced motion: the cards fade in place, no turning.
*/

let debugState = '';
const LOOP = 8;
const CW = 176;
const CH = 236;

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
const easeOutBack = (k: number) => {
  'worklet';
  const x = clamp01(k);
  const c1 = 1.3;
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};

/** Seconds since the cards started coming in (a) and since they started parting (b, < 0 not yet). */
const channels = (a: number, b: number) => {
  'worklet';
  const inK = easeOutBack((a - 0.25) / 0.8);
  const outK = b >= 0 ? easeIn(b / 0.7) : 0;
  const meet = a - 0.95;
  const squash = meet >= 0 && meet < 0.5 ? Math.sin(meet * 26) * Math.exp(-meet * 10) * 0.06 : 0;
  const star = meet >= 0 ? easeOutBack(meet / 0.4) * (1 - smooth(b / 0.3)) : 0;
  const look = smooth((a - 1.2) / 0.6) * (1 - smooth(b / 0.4));
  const copy = smooth((a - 1.2) / 0.5) * (1 - smooth(b / 0.3));
  const buttons = smooth((a - 1.5) / 0.4) * (1 - smooth(b / 0.3));
  return { k: inK * (1 - outK), squash, star, look, copy, buttons, glint: meet };
};
const scriptAB = (t: number) => {
  'worklet';
  return { a: t, b: t >= 6.6 ? t - 6.6 : -1 };
};

type Props = { autoPlay?: boolean };

export function ChromeMatchReveal({ autoPlay = false }: Props) {
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
    'CMR-Hand': require('./assets/fonts/GochiHand-Regular.ttf'),
    'CMR-Brush': require('./assets/fonts/CaveatBrush-Regular.ttf'),
    'CMR-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const CY = insets.top + 210;

  const play = useCmrAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const seq = useSharedValue({ a0: -100, b0: -100, met: 0 }); // touch timeline (clock)
  const ch = useSharedValue(channels(10, -1));
  const gaze = useSharedValue({ on: 0, x: 0, y: 0, k: 0 });
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __chromeMatch?: unknown };
    g.__chromeMatch = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__chromeMatch;
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
    const sq = seq.get();
    const gz = gaze.get();
    let t = time.get();
    let a = 10;
    let b = -1;
    if (dbg >= 0 || dbg === -2) {
      if (dbg >= 0) {
        t = dbg;
        debugT.set(-2);
      }
      const ab = scriptAB(t);
      a = ab.a;
      b = ab.b;
    } else if (auto && gz.on === 0 && now - last.get() >= 2 && sq.a0 < -50) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
      const ab = scriptAB(t);
      a = ab.a;
      b = ab.b;
    } else {
      // touch: parted by Dislike, back together a moment later
      a = sq.a0 > -50 ? now - sq.a0 : 10;
      b = sq.b0 > -50 ? now - sq.b0 : -1;
      if (sq.b0 > -50 && b > 1.2) {
        sq.a0 = now;
        sq.b0 = -100;
      }
      if (sq.a0 > -50 && a > 3 && sq.b0 < -50) sq.a0 = -100;
    }
    const c = channels(a, b);
    if (reduced) {
      c.squash = 0;
      c.look = 0;
    }
    ch.set(c);
    // the clink when the cards meet
    if (c.glint >= 0 && c.glint < 0.1 && sq.met === 0) {
      sq.met = 1;
      if (!(auto && sq.a0 < -50)) {
        scheduleOnRN(cue, 'clink');
        scheduleOnRN(buzz, 2);
      }
    } else if (c.glint < 0) sq.met = 0;
    gz.k += ((gz.on ? 1 : 0) - gz.k) * (1 - Math.exp(-dt * 8));
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} k=${c.k.toFixed(2)} star=${c.star.toFixed(2)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ---------------------------------------------------------------- cards
  const cardStyle = (side: number) => {
    'worklet';
    const c = ch.get();
    const off = (1 - c.k) * (w * 0.75);
    const rot = side * 9 + (1 - c.k) * side * 18;
    return {
      opacity: reduced ? c.k : 1,
      transform: [
        { translateX: cx - CW / 2 + side * 62 + side * (reduced ? 0 : off) },
        { translateY: CY - CH / 2 + (side > 0 ? 26 : -6) },
        { rotate: `${rot}deg` },
        { scaleX: 1 - c.squash },
        { scaleY: 1 + c.squash },
      ],
    };
  };
  const leftStyle = useAnimatedStyle(() => {
    tick.get();
    return cardStyle(-1);
  });
  const rightStyle = useAnimatedStyle(() => {
    tick.get();
    return cardStyle(1);
  });
  const copyStyle = useAnimatedStyle(() => {
    tick.get();
    const k = ch.get().copy;
    return { opacity: k, transform: [{ translateY: (1 - k) * 12 }] };
  });
  const btnStyle = useAnimatedStyle(() => {
    tick.get();
    const k = ch.get().buttons;
    return { opacity: k, transform: [{ translateY: (1 - k) * 18 }] };
  });

  // the star where the cards meet, and its glints
  const star = useMemo(() => {
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i < 8; i++) {
      const a = (i * Math.PI) / 4 - Math.PI / 2;
      const r = i % 2 === 0 ? 1 : 0.22;
      if (i === 0) p.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else p.quadTo(0, 0, Math.cos(a) * r, Math.sin(a) * r);
    }
    p.quadTo(0, 0, Math.cos(-Math.PI / 2), Math.sin(-Math.PI / 2));
    p.close();
    return p.build();
  }, []);
  // light beams fanning up from behind the cards when they meet (a message app's "lasers" effect),
  // sweeping once and fading
  const beams = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const g = ch.get().glint;
    if (reduced || g < 0 || g > 1.6) return rec.finishRecordingAsPicture();
    const fade = Math.min(1, g / 0.12) * (1 - smooth((g - 0.9) / 0.7));
    const sx = cx + 4;
    const sy = CY + CH * 0.62;
    const cols = ['#8b6cf6', '#ff5ad8', '#7fe3ff', '#ffffff', '#b8ff6a', '#ff8a4a'];
    for (let i = 0; i < 6; i++) {
      const base = -Math.PI / 2 + (i - 2.5) * 0.32;
      const a = base + Math.sin(g * 3.4 + i * 1.3) * 0.38;
      const L = h;
      const ex = sx + Math.cos(a) * L;
      const ey = sy + Math.sin(a) * L;
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setStyle(1);
      p.setStrokeCap(1);
      p.setShader(Skia.Shader.MakeLinearGradient({ x: sx, y: sy }, { x: ex, y: ey }, [Skia.Color(cols[i]), Skia.Color('rgba(0,0,0,0)')], null, 0));
      p.setStrokeWidth(10);
      p.setAlphaf(0.35 * fade);
      p.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 8, true));
      c.drawLine(sx, sy, ex, ey, p);
      p.setMaskFilter(null);
      p.setStrokeWidth(2);
      p.setAlphaf(0.85 * fade);
      c.drawLine(sx, sy, ex, ey, p);
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, CY]);

  const overlay = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const k = ch.get();
    const sx = cx + 4;
    const sy = CY + 6;
    if (k.star > 0.01) {
      const R = 46 * k.star;
      c.save();
      c.translate(sx, sy);
      c.rotate(-12 + k.star * 12, 0, 0);
      c.scale(R, R);
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: -1, y: -1 },
          { x: 1, y: 1 },
          [Skia.Color('#ffffff'), Skia.Color('#b9c7ff'), Skia.Color('#7f8bb0'), Skia.Color('#e8f0ff'), Skia.Color('#9aa6d0')],
          [0, 0.3, 0.5, 0.75, 1],
          0,
        ),
      );
      c.drawPath(star, paint);
      c.restore();
    }
    // glints scattered from the meeting point
    const g = k.glint;
    if (g >= 0 && g < 0.9 && !reduced) {
      for (let i = 0; i < 10; i++) {
        const a = (i / 10) * Math.PI * 2 + 0.3;
        const d = (1 - Math.exp(-g * 5)) * (70 + (i % 3) * 30);
        const al = 1 - smooth((g - 0.35) / 0.55);
        c.save();
        c.translate(sx + Math.cos(a) * d, sy + Math.sin(a) * d);
        c.scale(6 + (i % 2) * 4, 6 + (i % 2) * 4);
        const p = Skia.Paint();
        p.setAntiAlias(true);
        p.setColor(Skia.Color(i % 2 ? '#d7c6ff' : '#ffffff'));
        p.setAlphaf(al);
        c.drawPath(star, p);
        c.restore();
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, CY, star]);

  // ---------------------------------------------------------------- touch: the heads look at the finger
  const look = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const gz = gaze.get();
      gz.on = 1;
      gz.x = e.x;
      gz.y = e.y;
      last.set(clock.get());
    })
    .onUpdate((e) => {
      const gz = gaze.get();
      gz.x = e.x;
      gz.y = e.y;
      last.set(clock.get());
    })
    .onFinalize(() => {
      gaze.get().on = 0;
      last.set(clock.get());
    });
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const dislikeUI = () => {
    'worklet';
    const sq = seq.get();
    const now = clock.get();
    last.set(now);
    if (sq.b0 > -50) return;
    if (sq.a0 < -50) sq.a0 = now - 10;
    sq.b0 = now;
    scheduleOnRN(cue, 'part');
    scheduleOnRN(buzz, 0);
  };
  const dislike = () => scheduleOnUI(dislikeUI);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={look}>
        <View style={StyleSheet.absoluteFill}>
          <Animated.Text style={[styles.match, { top: insets.top + 46 }, copyStyle]} allowFontScaling={false}>
            {"It's a match!"}
          </Animated.Text>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Picture picture={beams} />
            </Canvas>
          ) : null}
          <BustCard side={-1} tint={[0.52, 0.42, 1.0]} rim="#8b6cf6" style={leftStyle} ch={ch} gaze={gaze} tick={tick} cx={cx} cy={CY} reduced={!!reduced} />
          <BustCard side={1} tint={[1.0, 0.36, 0.86]} rim="#ff5ad8" style={rightStyle} ch={ch} gaze={gaze} tick={tick} cx={cx} cy={CY} reduced={!!reduced} />
          {size ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Picture picture={overlay} />
            </Canvas>
          ) : null}
          <Animated.View style={[styles.copy, { top: CY + CH / 2 + 56 }, copyStyle]} accessible accessibilityLabel="It's a match. You and Purple liked each other.">
            <Text style={styles.title} allowFontScaling={false}>
              <Text style={styles.violet}>YOU</Text> AND <Text style={styles.pink}>PURPLE</Text>
            </Text>
            <Text style={styles.title} allowFontScaling={false}>
              LIKED EACH OTHER
            </Text>
          </Animated.View>
        </View>
      </GestureDetector>
      <Animated.View style={[styles.buttons, { bottom: insets.bottom + 18 }, btnStyle]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Send a message" onPress={() => buzz(1)} style={({ pressed }) => [styles.primary, pressed && styles.pressed]}>
          <Text style={styles.primaryText} allowFontScaling={false}>
            Send a message
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Dislike" onPress={dislike} style={({ pressed }) => [styles.secondary, pressed && styles.pressed]}>
          <Text style={styles.secondaryText} allowFontScaling={false}>
            Dislike
          </Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

function BustCard({
  side,
  tint,
  rim,
  style,
  ch,
  gaze,
  tick,
  cx,
  cy,
  reduced,
}: {
  side: number;
  tint: [number, number, number];
  rim: string;
  style: object;
  ch: SharedValue<ReturnType<typeof channels>>;
  gaze: SharedValue<{ on: number; x: number; y: number; k: number }>;
  tick: SharedValue<number>;
  cx: number;
  cy: number;
  reduced: boolean;
}) {
  const uniforms = useDerivedValue(() => {
    tick.get();
    const k = ch.get();
    const gz = gaze.get();
    // facing the other card once they have met, or the finger while it is down
    const meet = -side * 0.55 * k.look;
    const fy = Math.max(-0.8, Math.min(0.8, (gz.x - (cx + side * 62)) / 180));
    const fp = Math.max(-0.35, Math.min(0.35, (gz.y - cy) / 420));
    const idle = 0.05 * Math.sin((2 * Math.PI * 2 * (k.glint + 0.95)) / LOOP + side);
    return {
      c: [CW / 2, CH / 2 + 18],
      S: 92,
      yaw: reduced ? 0 : mix(meet + idle, fy, gz.k),
      pitch: reduced ? 0 : mix(0, fp, gz.k),
      tint,
      t: 0,
    };
  });
  return (
    <Animated.View style={[styles.card, { borderColor: rim }, style]} pointerEvents="none">
      <LinearGradient colors={['rgba(255,255,255,0.10)', 'rgba(255,255,255,0.02)']} style={StyleSheet.absoluteFill} />
      {bustFx ? (
        <Canvas style={{ width: CW, height: CH }}>
          <Fill>
            <Shader source={bustFx} uniforms={uniforms} />
          </Fill>
        </Canvas>
      ) : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0b0a0d' },
  match: { position: 'absolute', right: 34, color: '#ffffff', fontSize: 26, fontFamily: 'CMR-Brush', transform: [{ rotate: '-4deg' }] },
  card: { position: 'absolute', left: 0, top: 0, width: CW, height: CH, borderRadius: 26, borderWidth: 1.5, backgroundColor: 'rgba(36,26,58,0.75)', overflow: 'hidden' },
  copy: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  title: { color: '#ffffff', fontSize: 34, lineHeight: 38, fontFamily: 'CMR-Hand', letterSpacing: 0.5 },
  violet: { color: '#a58bff' },
  pink: { color: '#ff6ad8' },
  buttons: { position: 'absolute', left: 22, right: 22, gap: 12 },
  primary: { height: 58, borderRadius: 29, backgroundColor: '#8b6cf6', alignItems: 'center', justifyContent: 'center' },
  primaryText: { color: '#ffffff', fontSize: 17, fontFamily: 'CMR-SemiBold' },
  secondary: { height: 58, borderRadius: 29, backgroundColor: '#232227', alignItems: 'center', justifyContent: 'center' },
  secondaryText: { color: '#ffffff', fontSize: 17, fontFamily: 'CMR-SemiBold' },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
