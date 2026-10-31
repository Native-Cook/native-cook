import { BlurStyle, Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
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

import { drawGem, drawTiles, easeOutBack, LOOP, mix, phases, scriptP, smooth } from './pmg-scene';
import { type Cue, usePmgAudio } from './use-pmg-audio';

/*
 Perfect Month Gem — the "New badge unlocked" screen of an invented budgeting app.

 Look (the pin, a still): a light page, soft pink rays, a violet gem in a pink disc with a "×3"
 tag, "Perfect Month", "New Badge unlocked!", a line of copy, a rating nudge card, a black View
 Badge button and Go to Home.

 Motion (ours): the badge is made of the month. October's calendar sits where the badge will be;
 its 30 good days (and the one that went over) lift off and spiral into the middle, tinting violet,
 and fuse into a faceted gem that assembles facet by facet from its table outward while the card
 behind turns into the pink disc and the rays open. Drag the gem down and it comes apart into the
 calendar again under the finger; let go and it springs to the nearer state. Tap toggles.
 Everything is one progress p (0 calendar … 1 gem), so the scrub and the autoplay are the same
 motion.

 autoPlay: LOOP = 8 s. 0.6-2.8 s forge, the gem turns slowly, 6.6-7.6 s back to the calendar.
 Frame 8 s equals frame 0. Reduced motion: the two states crossfade.
*/

let debugState = '';

type Props = { autoPlay?: boolean };

export function PerfectMonthGem({ autoPlay = false }: Props) {
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
    'PMG-Display': require('./assets/fonts/InterDisplay-Bold.ttf'),
    'PMG-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'PMG-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'PMG-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const dayFont = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 9);
  const tagFont = useFont(require('./assets/fonts/Inter-Bold.ttf'), 12);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const CY = insets.top + 196;
  const R = 46;

  const play = usePmgAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  // p: progress 0 calendar … 1 gem; v its velocity; target; held by a finger; start of the drag
  const st = useSharedValue({ p: 0, v: 0, target: 1, held: 0, p0: 0, forged: 0 });
  const debugT = useSharedValue(-1);
  const [gem, setGem] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __perfectGem?: unknown };
    g.__perfectGem = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__perfectGem;
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
    let t = time.get();
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
      s.p = scriptP(t);
    } else if (dbg === -2) {
      s.p = scriptP(t);
    } else if (auto && s.held === 0 && now - last.get() >= 2) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
      s.p = scriptP(t);
      s.v = 0;
      s.target = s.p > 0.5 ? 1 : 0;
    } else if (s.held === 0) {
      if (reduced) {
        s.p += (s.target - s.p) * (1 - Math.exp(-dt * 16));
      } else {
        const k = 170;
        s.v += (k * (s.target - s.p) - 2 * Math.sqrt(k) * 0.8 * s.v) * dt;
        s.p += s.v * dt;
      }
    }
    // the forge moment, by hand: chime and haptic once as the gem completes
    if (s.held === 1 || !(auto && now - last.get() >= 2)) {
      if (s.p > 0.92 && s.forged === 0) {
        s.forged = 1;
        if (dbg < 0 && dbg !== -2) {
          scheduleOnRN(cue, 'forge');
          scheduleOnRN(buzz, 2);
        }
      } else if (s.p < 0.6) s.forged = 0;
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} p=${s.p.toFixed(3)} held=${s.held}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  useAnimatedReaction(
    () => {
      tick.get();
      return st.get().p > 0.5;
    },
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setGem, n);
    },
  );

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = st.get();
    const p = reduced ? (s.p > 0.5 ? 1 : 0) : s.p;
    const ph = phases(p);
    const t = time.get();
    const turn = (2 * Math.PI * (auto && s.held === 0 ? t : clock.get())) / LOOP;
    // rays: soft pink wedges that open as the gem forms and turn slowly
    const rayA = smooth((p - 0.7) / 0.3);
    if (rayA > 0.01) {
      c.save();
      c.translate(cx, CY);
      c.rotate((turn * 180) / Math.PI / 4, 0, 0);
      for (let i = 0; i < 12; i++) {
        const a0 = (i / 12) * Math.PI * 2;
        const pb = Skia.PathBuilder.Make();
        pb.moveTo(0, 0);
        pb.lineTo(Math.cos(a0 - 0.11) * 230, Math.sin(a0 - 0.11) * 230);
        pb.lineTo(Math.cos(a0 + 0.11) * 230, Math.sin(a0 + 0.11) * 230);
        pb.close();
        const rp = Skia.Paint();
        rp.setAntiAlias(true);
        rp.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, 230, [Skia.Color('rgba(248,140,226,0.45)'), Skia.Color('rgba(248,140,226,0)')], null, 0));
        rp.setAlphaf(rayA * mix(0.6, 1, easeOutBack((p - 0.7) / 0.3)));
        c.drawPath(pb.build(), rp);
      }
      c.restore();
    }
    // the card behind: a white calendar card that becomes the pink disc
    const k = smooth((p - 0.3) / 0.55);
    const cw = mix(186, 128, k);
    const chh = mix(150, 128, k);
    const cr = mix(22, 64, k);
    const card = Skia.Paint();
    card.setAntiAlias(true);
    const top = [Math.round(mix(255, 255, k)), Math.round(mix(255, 143, k)), Math.round(mix(255, 230, k))];
    const bot = [Math.round(mix(255, 233, k)), Math.round(mix(255, 76, k)), Math.round(mix(255, 214, k))];
    card.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cx, y: CY - chh / 2 },
        { x: cx, y: CY + chh / 2 },
        [Skia.Color(`rgb(${top[0]},${top[1]},${top[2]})`), Skia.Color(`rgb(${bot[0]},${bot[1]},${bot[2]})`)],
        null,
        0,
      ),
    );
    const sh = Skia.Paint();
    sh.setColor(Skia.Color('#6a3fb5'));
    sh.setAlphaf(0.12);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - cw / 2, CY - chh / 2 + 8, cw, chh), cr, cr), sh);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - cw / 2, CY - chh / 2, cw, chh), cr, cr), card);
    // weekday letters on the calendar card, fading as it rounds
    if (dayFont && k < 0.6) {
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setColor(Skia.Color('#9a93a8'));
      tp.setAlphaf(1 - k / 0.6);
      const L = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
      for (let i = 0; i < 7; i++) c.drawText(L[i], cx - 66 + i * 22 - 3, CY - 60, tp, dayFont);
    }
    drawTiles(c, cx, CY + 6 * (1 - k), ph.g, dayFont);
    // the flash as the tiles fuse
    const flash = Math.exp(-Math.pow((p - 0.62) / 0.07, 2));
    if (flash > 0.02 && !reduced) {
      const fp = Skia.Paint();
      fp.setColor(Skia.Color('#ffffff'));
      fp.setAlphaf(0.85 * flash);
      fp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
      c.drawCircle(cx, CY, 40, fp);
    }
    const yaw = 0.42 * Math.sin(turn) * smooth((p - 0.9) / 0.1);
    drawGem(c, cx, CY, R, yaw, -0.18, reduced ? (p > 0.5 ? 1 : 0) : ph.f);
    // the ×3 tag
    if (tagFont) {
      const tk = easeOutBack((p - 0.82) / 0.18);
      if (tk > 0.01) {
        c.save();
        c.translate(cx + 50, CY - 46);
        c.scale(tk, tk);
        const tg = Skia.Paint();
        tg.setAntiAlias(true);
        tg.setColor(Skia.Color('#ffc93c'));
        c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-17, -11, 34, 22), 11, 11), tg);
        const tt = Skia.Paint();
        tt.setAntiAlias(true);
        tt.setColor(Skia.Color('#3a2a00'));
        c.drawText('×3', -10, 4.5, tt, tagFont);
        c.restore();
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, CY, dayFont, tagFont]);

  // ---------------------------------------------------------------- copy: the calendar's line and the badge's
  const calStyle = useAnimatedStyle(() => {
    tick.get();
    const k = 1 - smooth(st.get().p / 0.35);
    return { opacity: k, transform: [{ translateY: (1 - k) * -6 }] };
  });
  const badgeStyle = useAnimatedStyle(() => {
    tick.get();
    const k = smooth((st.get().p - 0.78) / 0.22);
    return { opacity: k, transform: [{ translateY: (1 - k) * 8 }] };
  });
  const restStyle = useAnimatedStyle(() => {
    tick.get();
    const k = smooth((st.get().p - 0.85) / 0.15);
    return { opacity: k, transform: [{ translateY: (1 - k) * 10 }] };
  });

  // ---------------------------------------------------------------- touch: pull the gem apart, tap to toggle
  const pan = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      last.set(clock.get());
    })
    .onStart(() => {
      const s = st.get();
      s.held = 1;
      s.p0 = s.p;
      s.v = 0;
      scheduleOnRN(cue, 'gather');
      scheduleOnRN(buzz, 1);
    })
    .onUpdate((e) => {
      const s = st.get();
      last.set(clock.get());
      let p = s.p0 - e.translationY / 180;
      if (p > 1) p = 1 + 0.08 * Math.tanh((p - 1) / 0.08);
      if (p < 0) p = -0.08 * Math.tanh(-p / 0.08);
      s.p = p;
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.held !== 1) return;
      last.set(clock.get());
      s.held = 0;
      s.v = -e.velocityY / 180;
      s.target = s.p + s.v * 0.15 > 0.5 ? 1 : 0;
    });
  const tap = Gesture.Tap().onEnd(() => {
    const s = st.get();
    last.set(clock.get());
    s.target = s.target > 0.5 && s.p > 0.5 ? 0 : 1;
    scheduleOnRN(cue, s.target ? 'gather' : 'tap');
    scheduleOnRN(buzz, 0);
  });
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const toggleUI = () => {
    'worklet';
    const s = st.get();
    last.set(clock.get());
    s.target = s.p > 0.5 ? 0 : 1;
    scheduleOnRN(buzz, 0);
  };
  const toggle = () => scheduleOnUI(toggleUI);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {size ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View
          style={[styles.stage, { top: CY - 110, left: cx - 120 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={gem ? 'Perfect Month badge, made of your October. Double-tap to see the days.' : 'October: 30 of 31 days on budget. Double-tap to forge the badge.'}
          onAccessibilityTap={toggle}
        />
      </GestureDetector>
      <View style={[styles.under, { top: CY + 84 }]} pointerEvents="none">
        <Animated.Text style={[styles.underText, calStyle]} allowFontScaling={false}>
          October · 30 of 31 days on budget
        </Animated.Text>
        <Animated.Text style={[styles.underText, styles.underBadge, badgeStyle]} allowFontScaling={false}>
          Perfect Month
        </Animated.Text>
      </View>
      <Animated.View style={[styles.copy, { top: CY + 150 }, restStyle]} pointerEvents="none">
        <Text style={styles.title} allowFontScaling={false} accessibilityRole="header">
          New{'\n'}Badge unlocked!
        </Text>
        <Text style={styles.body} allowFontScaling={false}>
          You stayed within your budget. Keep up the great work!
        </Text>
      </Animated.View>
      <View style={[styles.rate, { top: h * 0.645 }]}>
        <View style={styles.rateStar}>
          <Text style={styles.rateGlyph} allowFontScaling={false}>
            ✦
          </Text>
        </View>
        <View style={styles.rateText}>
          <Text style={styles.rateTitle} allowFontScaling={false}>
            Are you loving your experience with us so far?
          </Text>
          <Text style={styles.rateLink} allowFontScaling={false} accessibilityRole="link">
            Give us a rating
          </Text>
        </View>
      </View>
      <View style={[styles.buttons, { bottom: insets.bottom + 18 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="View badge" onPress={() => buzz(1)} style={({ pressed }) => [styles.primary, pressed && styles.pressed]}>
          <Text style={styles.primaryText} allowFontScaling={false}>
            View Badge
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Go to home" onPress={() => buzz(0)} style={({ pressed }) => [styles.secondary, pressed && styles.pressed]}>
          <Text style={styles.secondaryText} allowFontScaling={false}>
            Go to Home
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f2f2f4' },
  stage: { position: 'absolute', width: 240, height: 220 },
  under: { position: 'absolute', left: 0, right: 0, height: 20 },
  underText: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#5b5866', fontSize: 13, fontFamily: 'PMG-Medium' },
  underBadge: { color: '#2b2735' },
  copy: { position: 'absolute', left: 28, right: 28, alignItems: 'center' },
  title: { color: '#141218', fontSize: 27, lineHeight: 32, fontFamily: 'PMG-Display', textAlign: 'center', letterSpacing: -0.4 },
  body: { color: '#6d6a76', fontSize: 15, lineHeight: 21, fontFamily: 'PMG-Regular', textAlign: 'center', marginTop: 12 },
  rate: { position: 'absolute', left: 18, right: 18, height: 70, borderRadius: 16, backgroundColor: '#e7e7ea', flexDirection: 'row', alignItems: 'center', overflow: 'hidden' },
  rateStar: { width: 70, height: 70, borderRadius: 35, marginLeft: -22, marginRight: 8, backgroundColor: '#f6f6f8', alignItems: 'center', justifyContent: 'center' },
  rateGlyph: { color: '#141218', fontSize: 28, marginLeft: 18 },
  rateText: { flex: 1, paddingRight: 14 },
  rateTitle: { color: '#141218', fontSize: 14, lineHeight: 19, fontFamily: 'PMG-SemiBold' },
  rateLink: { color: '#4a4652', fontSize: 12.5, fontFamily: 'PMG-Medium', textDecorationLine: 'underline', marginTop: 4 },
  buttons: { position: 'absolute', left: 18, right: 18, gap: 6 },
  primary: { height: 54, borderRadius: 27, backgroundColor: '#1b1a1f', alignItems: 'center', justifyContent: 'center' },
  primaryText: { color: '#ffffff', fontSize: 16, fontFamily: 'PMG-SemiBold' },
  secondary: { height: 48, alignItems: 'center', justifyContent: 'center' },
  secondaryText: { color: '#1b1a1f', fontSize: 15, fontFamily: 'PMG-SemiBold' },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
