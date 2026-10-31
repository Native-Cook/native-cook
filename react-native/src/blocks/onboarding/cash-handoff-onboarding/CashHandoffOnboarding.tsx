import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawCash, drawHand } from './cho-art';
import { type Cue, useChoAudio } from './use-cho-audio';

/*
 Cash Handoff Onboarding — the "Receive" page of an invented money-transfer app's onboarding.

 Look (the pin, a still): a lilac panel with RECEIVE in heavy condensed capitals and a line of copy,
 an illustrated hand holding out a stack of banknotes to an open hand, a white bottom with a pager
 of three bars, a dark Get Started button and a Log in line.

 Motion (ours): the hands are drawn from strokes, so their fingers can bend. Drag the notes out of
 the giving hand (its fingers open) and over to the open palm: let go there and the receiving hand
 closes round them, sparkles pop and "+$50" floats up; let go anywhere else and the notes fly back
 into the giving hand, which closes on them again. Tap once they are received and both hands slide
 off and come back for the next one.

 autoPlay: LOOP = 8 s. 1.0-2.2 s the notes are carried across, 2.2 s caught; 4.6-5.6 s the hands
 slide out, 5.6-6.8 s back in. Frame 8 s equals frame 0. Reduced motion: no flight; the notes cross
 with a fade.
*/

let debugState = '';
const LOOP = 8;

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
const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};
const easeOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return 1 - Math.pow(1 - x, 3);
};
/** A closing hand: overshoots a little and settles. */
const closeSpring = (s: number) => {
  'worklet';
  if (s <= 0) return 0;
  return 1 - Math.exp(-s * 9) * Math.cos(s * 14);
};

type Props = { autoPlay?: boolean };

export function CashHandoffOnboarding({ autoPlay = false }: Props) {
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
    'CHO-Display': require('./assets/fonts/Anton-Regular.ttf'),
    'CHO-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'CHO-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 15);
  const plusFont = useFont(require('./assets/fonts/Inter-Bold.ttf'), 22);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const T = insets.top;
  const PH = Math.round(h * 0.64);
  const G = { x: 70, y: T + 300 }; // the giver's wrist
  const R = { x: w - 70, y: T + 372 }; // the receiver's wrist
  const HOLD = { x: G.x + 72, y: G.y + 4 }; // notes in the giving hand
  const PALM = { x: R.x - 34, y: R.y - 30 }; // notes in the open palm

  const play = useChoAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  // touch state: 0 held by the giver, 1 dragged, 2 flying back, 3 caught, 4 cycling (hands out and in)
  const st = useSharedValue({ phase: 0, at: 0, x: 0, y: 0, fx: 0, fy: 0, gx: 0, gy: 0 });
  const scene = useSharedValue({ cx: 0, cy: 0, rot: 0, fan: 0, gCurl: 0.75, rCurl: 0.02, gOff: 0, rOff: 0, caughtAt: -10, carry: 0 });
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __cashHandoff?: unknown };
    g.__cashHandoff = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__cashHandoff;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** The whole scene at loop time t (autoplay). */
  const scriptScene = (t: number) => {
    'worklet';
    const k = easeInOut((t - 1.0) / 1.2);
    const fly = t >= 1.0 && t < 2.2;
    const out = easeIn((t - 4.6) / 1.0);
    const back = easeOut((t - 5.6) / 1.2);
    const away = t >= 4.6 && t < 5.6 ? out : t >= 5.6 && t < 6.8 ? 1 - back : 0;
    const fresh = t >= 5.6; // a new stack in the giving hand
    let cx = mix(HOLD.x, PALM.x, k);
    let cy = mix(HOLD.y, PALM.y, k) - Math.sin(k * Math.PI) * 80;
    if (fresh) {
      cx = HOLD.x;
      cy = HOLD.y;
    }
    const caught = t >= 2.2 && !fresh;
    return {
      cx: cx + (fresh ? -1 : caught ? 1 : 0) * away * 320,
      cy,
      rot: fly ? Math.sin(k * Math.PI * 3) * 12 : caught ? -6 : 0,
      fan: fly ? Math.sin(k * Math.PI) * 0.7 : 0,
      gCurl: t < 1.0 || fresh ? 0.75 : mix(0.75, 0.15, smooth((t - 1.0) / 0.25)),
      rCurl: caught ? mix(0.02, 0.85, closeSpring(t - 2.2)) : 0.02,
      gOff: -away * 320,
      rOff: away * 320,
      caughtAt: caught ? 2.2 : -10,
      carry: 0,
    };
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const s = st.get();
    const sc = scene.get();
    let t = time.get();
    if (dbg >= 0 || dbg === -2) {
      if (dbg >= 0) {
        t = dbg;
        debugT.set(-2);
      }
      scene.set(scriptScene(t));
    } else if (auto && s.phase === 0 && now - last.get() >= 2) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
      scene.set(scriptScene(t));
    } else {
      const since = now - s.at;
      if (s.phase === 0) {
        sc.cx = HOLD.x;
        sc.cy = HOLD.y;
        sc.gCurl += (0.75 - sc.gCurl) * (1 - Math.exp(-dt * 12));
        sc.rCurl += (0.02 - sc.rCurl) * (1 - Math.exp(-dt * 12));
        sc.rot *= Math.exp(-dt * 10);
        sc.fan *= Math.exp(-dt * 10);
        sc.gOff = 0;
        sc.rOff = 0;
        sc.caughtAt = -10;
      } else if (s.phase === 1) {
        const vx = (s.x - sc.cx) / Math.max(dt, 0.001);
        sc.cx = s.x;
        sc.cy = s.y;
        sc.rot += (Math.max(-18, Math.min(18, vx * 0.02)) - sc.rot) * (1 - Math.exp(-dt * 12));
        sc.fan += (0.5 - sc.fan) * (1 - Math.exp(-dt * 8));
        sc.gCurl += (0.15 - sc.gCurl) * (1 - Math.exp(-dt * 14));
      } else if (s.phase === 2) {
        const k = easeOut(since / 0.35);
        sc.cx = mix(s.fx, HOLD.x, k);
        sc.cy = mix(s.fy, HOLD.y, k);
        sc.rot *= Math.exp(-dt * 10);
        sc.fan *= Math.exp(-dt * 10);
        if (since > 0.3) sc.gCurl += (0.75 - sc.gCurl) * (1 - Math.exp(-dt * 14));
        if (since > 0.6) s.phase = 0;
      } else if (s.phase === 3) {
        const k = easeOut(since / 0.25);
        sc.cx = mix(s.fx, PALM.x, k);
        sc.cy = mix(s.fy, PALM.y, k);
        sc.rot += (-6 - sc.rot) * (1 - Math.exp(-dt * 10));
        sc.fan *= Math.exp(-dt * 10);
        sc.rCurl = mix(0.02, 0.85, closeSpring(since));
        sc.caughtAt = now - since;
      } else if (s.phase === 4) {
        const out = easeIn(since / 0.9);
        const back = easeOut((since - 1.0) / 1.0);
        if (since < 1.0) {
          sc.gOff = -out * 320;
          sc.rOff = out * 320;
          sc.cx = PALM.x + out * 320;
        } else {
          sc.gOff = -(1 - back) * 320;
          sc.rOff = (1 - back) * 320;
          sc.cx = HOLD.x - (1 - back) * 320;
          sc.cy = HOLD.y;
          sc.rot = 0;
          sc.gCurl = 0.75;
          sc.rCurl = 0.02;
          sc.caughtAt = -10;
        }
        if (since > 2.05) s.phase = 0;
      }
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} phase=${s.phase}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, PH));
    const sc = scene.get();
    const now = debugT.get() === -2 || (auto && st.get().phase === 0 && clock.get() - last.get() >= 2) ? time.get() : clock.get();
    // the catch washes the panel with a soft green light rising from the palm (a send-money success)
    const sinceW = now - sc.caughtAt;
    if (sc.caughtAt > -5 && sinceW >= 0 && sinceW < 1.8) {
      const wash = Skia.Paint();
      const k = Math.min(1, sinceW / 0.15) * (1 - smooth((sinceW - 0.5) / 1.3));
      wash.setShader(
        Skia.Shader.MakeRadialGradient(
          { x: PALM.x + sc.rOff, y: PALM.y },
          120 + 260 * smooth(sinceW / 0.9),
          [Skia.Color('rgba(150,255,140,0.95)'), Skia.Color('rgba(110,235,150,0.45)'), Skia.Color('rgba(110,235,150,0)')],
          [0, 0.45, 1],
          0,
        ),
      );
      wash.setAlphaf(k);
      c.save();
      c.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, -40, w, PH + 40), 34, 34), 1, true);
      c.drawRect(Skia.XYWHRect(0, 0, w, PH), wash);
      c.restore();
    }
    // sparkle rings round the notes in the giving hand, like the pin's
    const bob = Math.sin((2 * Math.PI * 2 * time.get()) / LOOP) * 2;
    drawHand(c, G.x + sc.gOff, G.y + bob, 1, sc.gCurl, 0, 0);
    drawHand(c, R.x + sc.rOff, R.y - bob, -1, sc.rCurl, 1, 0);
    drawCash(c, sc.cx, sc.cy + (sc.caughtAt < 0 ? bob : -bob), sc.rot, sc.fan, bold);
    drawHand(c, G.x + sc.gOff, G.y + bob, 1, sc.gCurl, 0, 1);
    drawHand(c, R.x + sc.rOff, R.y - bob, -1, sc.rCurl, 1, 1);
    // the catch: sparkles and "+$50"
    const since = now - sc.caughtAt;
    if (sc.caughtAt > -5 && since >= 0 && since < 1.6) {
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(1);
      ring.setStrokeWidth(2);
      ring.setColor(Skia.Color('#ffffff'));
      for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2;
        const d = 30 + (1 - Math.exp(-since * 6)) * 46;
        const al = 1 - smooth((since - 0.4) / 0.6);
        ring.setAlphaf(al);
        const r = 3 + (i % 2) * 2;
        c.drawCircle(PALM.x + sc.rOff + Math.cos(a) * d, PALM.y + Math.sin(a) * d, r, ring);
      }
      if (plusFont) {
        const p = Skia.Paint();
        p.setAntiAlias(true);
        p.setColor(Skia.Color('#ffffff'));
        p.setAlphaf(Math.sin(clamp01(since / 1.6) * Math.PI));
        c.drawText('+$50', PALM.x + sc.rOff - 26, PALM.y - 60 - easeOut(since / 1.6) * 40, p, plusFont);
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, PH, bold, plusFont]);

  // ---------------------------------------------------------------- touch
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const s = st.get();
      const sc = scene.get();
      last.set(clock.get());
      if (s.phase === 3) {
        // received: send the hands off for the next one
        s.phase = 4;
        s.at = clock.get();
        return;
      }
      if (s.phase !== 0) return;
      if (Math.hypot(e.x - sc.cx, e.y - sc.cy) > 70) return;
      s.phase = 1;
      s.gx = e.x - sc.cx;
      s.gy = e.y - sc.cy;
      s.x = sc.cx;
      s.y = sc.cy;
      scheduleOnRN(cue, 'flutter');
      scheduleOnRN(buzz, 0);
    })
    .onUpdate((e) => {
      const s = st.get();
      if (s.phase !== 1) return;
      last.set(clock.get());
      s.x = e.x - s.gx;
      s.y = e.y - s.gy;
    })
    .onFinalize(() => {
      const s = st.get();
      if (s.phase !== 1) return;
      last.set(clock.get());
      s.fx = s.x;
      s.fy = s.y;
      s.at = clock.get();
      if (Math.hypot(s.x - PALM.x, s.y - PALM.y) < 90) {
        s.phase = 3;
        scheduleOnRN(cue, 'catch');
        scheduleOnRN(buzz, 2);
      } else {
        s.phase = 2;
        scheduleOnRN(cue, 'return');
        scheduleOnRN(buzz, 1);
      }
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={[styles.panel, { height: PH }]} />
      <View style={[styles.copy, { top: T + 40 }]} pointerEvents="none">
        <Text style={styles.big} allowFontScaling={false} accessibilityRole="header">
          RECEIVE
        </Text>
        <Text style={styles.body} allowFontScaling={false}>
          Accept money directly from friends or family{'\n'}without the usual bank delays.
        </Text>
      </View>
      <GestureDetector gesture={pan}>
        <View
          style={[styles.stage, { height: PH }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="A hand holding out banknotes to an open hand. Drag the notes across to send them."
        >
          {size ? (
            <Canvas style={{ width: w, height: PH }}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.pager, { top: PH + 34 }]} pointerEvents="none">
        <View style={styles.bar} />
        <View style={[styles.bar, styles.barOn]} />
        <View style={styles.bar} />
      </View>
      <View style={[styles.bottom, { bottom: insets.bottom + 16 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Get started" onPress={() => buzz(1)} style={({ pressed }) => [styles.cta, pressed && styles.pressed]}>
          <Text style={styles.ctaText} allowFontScaling={false}>
            Get Started
          </Text>
        </Pressable>
        <Text style={styles.login} allowFontScaling={false}>
          Already have an account? <Text style={styles.loginLink}>Log in</Text>
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ffffff' },
  panel: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: '#c391f4', borderBottomLeftRadius: 34, borderBottomRightRadius: 34 },
  copy: { position: 'absolute', left: 24, right: 24 },
  big: { color: '#ffffff', fontSize: 64, lineHeight: 72, fontFamily: 'CHO-Display', letterSpacing: 0.5 },
  body: { color: 'rgba(255,255,255,0.92)', fontSize: 14, lineHeight: 20, fontFamily: 'CHO-Medium', marginTop: 4 },
  stage: { position: 'absolute', left: 0, right: 0, top: 0 },
  pager: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 5 },
  bar: { width: 4, height: 16, borderRadius: 2, backgroundColor: '#c9c9cf' },
  barOn: { height: 26, backgroundColor: '#2a2a2e' },
  bottom: { position: 'absolute', left: 20, right: 20, gap: 16, alignItems: 'center' },
  cta: { alignSelf: 'stretch', height: 56, borderRadius: 28, backgroundColor: '#2c2c30', alignItems: 'center', justifyContent: 'center' },
  ctaText: { color: '#ffffff', fontSize: 16, fontFamily: 'CHO-SemiBold' },
  login: { color: '#3a3a40', fontSize: 14, fontFamily: 'CHO-Medium' },
  loginLink: { color: '#111111', fontFamily: 'CHO-SemiBold' },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
