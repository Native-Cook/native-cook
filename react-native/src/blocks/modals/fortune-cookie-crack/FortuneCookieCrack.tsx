import {
  BlurMask,
  Canvas,
  Glyphs,
  Group,
  LinearGradient,
  Oval,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { Backdrop, COOKIE_SCALE, Chip, CookiePiece } from './FortuneParts';
import { COOKIE_SKSL, GRAIN_SKSL, makeCookie } from './fortuneArt';
import { CLOSE_AT, CRACK_AT, CRUMBS, LOOP, OPEN_AT, SQUEEZE_AT, TAP_FROM, cookieAt, crumbAt } from './fortuneMotion';

type Props = {
  /** Squeezes and cracks the cookie, unrolls the fortune, then folds it all back, every 9 s. */
  autoPlay?: boolean;
};

const IOS = process.env.EXPO_OS === 'ios';
const SERIF = IOS ? 'Georgia' : 'serif';
/** Seconds after the last touch before the loop starts again. */
const IDLE = 2;
/** A touch shorter than this is a tap: it cracks the cookie at once. */
const TAP = 0.22;
const FORTUNE = ['You will find joy in', 'an unplanned detour.'];
const LUCKY = [7, 18, 23, 31, 42, 56];

const LACQUER = '#A8322A';
const INK = '#3B1F17';
const MUTE = 'rgba(59,31,23,0.6)';

export function FortuneCookieCrack({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const looping = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const W = size.width;
  const H = size.height;

  // Layout (pt): the modal fills the screen under the app's dimmed header.
  const mx = 16;
  const mw = Math.max(240, W - 32);
  const mTop = insets.top + 64;
  const mBottom = H - insets.bottom - 20;
  const mH = mBottom - mTop;
  const cx = W / 2;
  const cy = mTop + mH * 0.4;
  const plateRx = Math.min(140, mw / 2 - 24);
  const slipY = mTop + mH * 0.655;
  const slipW = mw - 40;
  const SLIP_H = 84;

  const art = useMemo(() => makeCookie(), []);
  const cookieFx = useMemo(() => Skia.RuntimeEffect.Make(COOKIE_SKSL), []);
  const grain = useMemo(() => Skia.RuntimeEffect.Make(GRAIN_SKSL), []);
  const fortune = useMemo(() => {
    let fs = 18;
    let font = matchFont({ fontFamily: SERIF, fontSize: fs, fontStyle: 'italic', fontWeight: '400' });
    const widthOf = (s: string) => font.getGlyphWidths(font.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
    const widest = Math.max(...FORTUNE.map(widthOf));
    if (widest > slipW - 56) {
      fs = Math.floor((fs * (slipW - 56)) / widest);
      font = matchFont({ fontFamily: SERIF, fontSize: fs, fontStyle: 'italic', fontWeight: '400' });
    }
    const lines = FORTUNE.map((s, k) => {
      const ids = font.getGlyphIDs(s);
      const ws = font.getGlyphWidths(ids);
      let x = cx - ws.reduce((a, b) => a + b, 0) / 2;
      const y = slipY - 4 + (k - 0.5) * fs * 1.35 + fs * 0.36;
      return ids.map((id, j) => {
        const g = { id, pos: vec(x, y) };
        x += ws[j];
        return g;
      });
    });
    return { font, lines };
  }, [cx, slipY, slipW]);

  // One clock. A finger moves it: holding plays the squeeze, a tap when open plays the close.
  const clock = useSharedValue(0);
  const running = useSharedValue(looping);
  const user = useSharedValue(0);
  const pressing = useSharedValue(0);
  const pressFor = useSharedValue(0);
  const target = useSharedValue(-1);
  const idle = useSharedValue(0);

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (user.get() === 1) {
      if (pressing.get() === 1) {
        pressFor.set(pressFor.get() + dt);
        idle.set(0);
        const c = Math.max(clock.get(), SQUEEZE_AT) + dt;
        if (c >= CRACK_AT) {
          pressing.set(0);
          target.set(OPEN_AT);
        }
        clock.set(Math.min(c, CRACK_AT));
      } else if (target.get() >= 0) {
        let c = clock.get() + dt;
        if (c >= target.get()) {
          c = target.get() >= LOOP ? 0 : target.get();
          target.set(-1);
        }
        clock.set(c);
      } else {
        idle.set(idle.get() + dt);
        if (idle.get() > IDLE) {
          // Back to the loop: a cookie left open folds itself back up; a closed one starts over.
          user.set(0);
          const c = clock.get();
          clock.set(c >= OPEN_AT && c < CLOSE_AT ? CLOSE_AT : c < CRACK_AT ? 0 : c);
          running.set(looping || (c >= OPEN_AT && c < CLOSE_AT));
        }
      }
      return;
    }
    if (!running.get()) return;
    let t = clock.get() + dt;
    if (t >= LOOP) {
      t -= LOOP;
      if (!looping) {
        t = 0;
        running.set(false);
      }
    }
    clock.set(t);
  });

  // Haptics only for what a finger caused: the snap, and the slip lying open.
  useAnimatedReaction(
    () => (user.get() === 1 ? (clock.get() >= OPEN_AT - 0.01 ? 2 : clock.get() >= CRACK_AT ? 1 : 0) : -1),
    (now, before) => {
      if (before === null || before < 0 || now <= before) return;
      scheduleOnRN(feel, now === 1 ? 0 : 1);
    },
  );

  const pose = useDerivedValue(() => cookieAt(clock.get()));

  const cookieT = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: cx }, { translateY: cy + p.bob }, { rotate: p.tilt + p.shake }, { scaleX: p.sx * COOKIE_SCALE }, { scaleY: p.sy * COOKIE_SCALE }];
  });
  // Each half swings out about its outer lower tip and slides apart.
  const leftT = useDerivedValue(() => {
    const o = pose.get().open;
    const [ox, oy] = art.pivotL;
    return [{ translateX: -24 * o }, { translateY: 4 * o }, { translateX: ox }, { translateY: oy }, { rotate: -0.34 * o }, { translateX: -ox }, { translateY: -oy }];
  });
  const rightT = useDerivedValue(() => {
    const o = pose.get().open;
    const [ox, oy] = art.pivotR;
    return [{ translateX: 24 * o }, { translateY: 4 * o }, { translateX: ox }, { translateY: oy }, { rotate: 0.34 * o }, { translateX: -ox }, { translateY: -oy }];
  });
  const leftShadowT = useDerivedValue(() => [{ translateX: cx - 60 - 34 * pose.get().open }, { translateY: cy + 58 }]);
  const rightShadowT = useDerivedValue(() => [{ translateX: cx + 60 + 34 * pose.get().open }, { translateY: cy + 58 }]);
  const crackEnd = useDerivedValue(() => pose.get().crack);
  const crackOpacity = useDerivedValue(() => Math.max(0, 1 - pose.get().open * 2.5));
  const wholeOpacity = useDerivedValue(() => (pose.get().open > 0.001 ? 0 : 1));
  const halvesOpacity = useDerivedValue(() => (pose.get().open > 0.001 ? 1 : 0));

  const crumbs = useDerivedValue(() => {
    const p = pose.get();
    const b = Skia.PathBuilder.Make();
    if (p.crumbT >= 0) {
      for (let i = 0; i < CRUMBS; i += 1) {
        const c = crumbAt(i, p.crumbT, 58);
        if (c.on > 0) b.addCircle(c.x, c.y, c.r);
      }
    }
    return b.build();
  });
  const crumbsT = useDerivedValue(() => [{ translateX: cx }, { translateY: cy }]);
  const crumbOpacity = useDerivedValue(() => pose.get().crumbA);

  // The slip: a little roll that comes out of the gap, drops into place and unrolls both ways.
  const slipT = useDerivedValue(() => {
    const p = pose.get();
    const y = cy - 46 + (slipY - (cy - 46)) * p.drop;
    const s = 0.58 + 0.42 * p.drop;
    return [{ translateX: cx }, { translateY: y }, { scale: s }];
  });
  const slipOpacity = useDerivedValue(() => Math.min(1, pose.get().emerge * 3));
  const half = useDerivedValue(() => 7 + (slipW / 2 - 7) * pose.get().unroll);
  const paperRect = useDerivedValue(() => Skia.XYWHRect(-half.get(), -SLIP_H / 2, half.get() * 2, SLIP_H));
  const rollL = useDerivedValue(() => -half.get() - 7);
  const rollR = useDerivedValue(() => half.get() - 7);
  const textClip = useDerivedValue(() => Skia.XYWHRect(cx - half.get() + 6, slipY - SLIP_H, Math.max(0, half.get() * 2 - 12), SLIP_H * 2));
  const textOpacity = useDerivedValue(() => (pose.get().drop > 0.98 ? 1 : 0));

  const subA = useAnimatedStyle(() => {
    const s = pose.get().stage;
    return { opacity: 1 - s, transform: [{ translateY: -s * 18 }] };
  });
  const subB = useAnimatedStyle(() => {
    const s = pose.get().stage;
    return { opacity: s, transform: [{ translateY: (1 - s) * 18 }] };
  });

  function onCookie(x: number, y: number) {
    'worklet';
    return Math.abs(x - cx) < 140 && y > cy - 100 && y < cy + 90;
  }

  const touch = Gesture.Manual()
    .onTouchesDown((e, manager) => {
      const t0 = e.changedTouches[0];
      const c = clock.get();
      const closed = c < SQUEEZE_AT + 0.05 || c >= 8.4;
      const open = c >= OPEN_AT - 0.01 && c < CLOSE_AT;
      if (!t0 || !onCookie(t0.x, t0.y) || (!closed && !open)) {
        manager.fail();
        return;
      }
      manager.activate();
      user.set(1);
      running.set(false);
      idle.set(0);
      if (closed) {
        clock.set(SQUEEZE_AT);
        pressing.set(1);
        pressFor.set(0);
        target.set(-1);
      } else {
        // Open: a touch folds the fortune back into the cookie.
        target.set(LOOP);
        clock.set(Math.max(c, CLOSE_AT));
      }
    })
    .onTouchesUp((_e, manager) => {
      if (pressing.get() === 1) {
        pressing.set(0);
        if (pressFor.get() < TAP) {
          clock.set(Math.max(clock.get(), TAP_FROM));
          target.set(OPEN_AT);
        } else {
          // Let go before it snapped: the squeeze relaxes.
          clock.set(withTiming(SQUEEZE_AT, { duration: 280 }));
        }
      }
      idle.set(0);
      manager.end();
    })
    .onTouchesCancelled((_e, manager) => {
      if (pressing.get() === 1) {
        pressing.set(0);
        clock.set(withTiming(SQUEEZE_AT, { duration: 280 }));
      }
      manager.end();
    });

  const close = () => {
    const c = clock.get();
    if (c >= OPEN_AT - 0.01 && c < CLOSE_AT) {
      user.set(1);
      running.set(false);
      idle.set(0);
      clock.set(CLOSE_AT);
      target.set(LOOP);
    }
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const ready = W > 0 && cookieFx && grain;

  return (
    <View style={styles.root} onLayout={onLayout}>
      {ready ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Backdrop width={W} height={H} modal={{ x: mx, y: mTop, w: mw, h: mH }} plate={{ cx, cy: cy + 62, rx: plateRx }} grain={grain} />

          <Group transform={leftShadowT}>
            <Oval x={-62} y={-9} width={124} height={18} color="rgba(80,40,12,0.28)">
              <BlurMask blur={7} style="normal" />
            </Oval>
          </Group>
          <Group transform={rightShadowT}>
            <Oval x={-62} y={-9} width={124} height={18} color="rgba(80,40,12,0.28)">
              <BlurMask blur={7} style="normal" />
            </Oval>
          </Group>

          <Group transform={crumbsT} opacity={crumbOpacity}>
            <Path path={crumbs} color="#B9782F">
              <Shadow dx={0} dy={0.8} blur={0.8} color="rgba(80,40,10,0.45)" />
            </Path>
          </Group>

          <Group transform={cookieT}>
            {/* Whole until the snap (no seam at rest), then two halves that swing apart. */}
            <Group opacity={wholeOpacity}>
              <CookiePiece body={art.whole} lip={art.wholeLip} rim={art.rim} inside={art.inside} fx={cookieFx} />
            </Group>
            {[
              { t: leftT, body: art.left, lip: art.leftLip, inside: art.leftInside },
              { t: rightT, body: art.right, lip: art.rightLip, inside: art.rightInside },
            ].map((h, i) => (
              <Group key={i} transform={h.t} opacity={halvesOpacity}>
                <CookiePiece body={h.body} lip={h.lip} rim={art.rim} inside={h.inside} fx={cookieFx} />
                {/* The broken face shows the paler, airy inside of the wafer. */}
                <Group clip={h.body}>
                  <Path path={art.crack} style="stroke" strokeWidth={6} strokeJoin="round" color="rgba(252,226,168,0.7)" />
                  <Path path={art.crack} style="stroke" strokeWidth={1.2} strokeJoin="round" color="rgba(120,64,18,0.45)" />
                </Group>
              </Group>
            ))}
            <Group opacity={crackOpacity}>
              <Path path={art.crease} style="stroke" strokeWidth={2.2} strokeCap="round" color="rgba(110,58,16,0.4)">
                <BlurMask blur={1.4} style="normal" />
              </Path>
              <Path path={art.crack} style="stroke" strokeWidth={1.6} strokeJoin="round" color="rgba(70,32,8,0.85)" end={crackEnd} />
              <Path path={art.branches} style="stroke" strokeWidth={1.1} strokeJoin="round" color="rgba(70,32,8,0.7)" end={crackEnd} />
            </Group>
          </Group>

          <Group transform={slipT} opacity={slipOpacity}>
            <Rect rect={paperRect}>
              <LinearGradient start={vec(0, -SLIP_H / 2)} end={vec(0, SLIP_H / 2)} colors={['#FFFCF4', '#F8F1E2', '#EEE3CC']} />
              <Shadow dx={0} dy={6} blur={12} color="rgba(70,40,20,0.22)" />
              <Shadow dx={0} dy={1} blur={1.5} color="rgba(70,40,20,0.25)" />
            </Rect>
            <Rect rect={paperRect} blendMode="softLight">
              <Shader source={grain} uniforms={{ amount: 0.7 }} />
            </Rect>
            {[rollL, rollR].map((x, i) => (
              <RoundedRect key={i} x={x} y={-SLIP_H / 2 - 3} width={14} height={SLIP_H + 6} r={7}>
                <LinearGradient start={vec(-8, 0)} end={vec(8, 0)} colors={['#D9CCB1', '#FFFBF1', '#E6D9BF', '#BFAF91']} positions={[0, 0.35, 0.7, 1]} />
                <Shadow dx={0} dy={2} blur={3} color="rgba(70,40,20,0.3)" />
              </RoundedRect>
            ))}
          </Group>
          <Group clip={textClip} opacity={textOpacity}>
            {fortune.lines.map((g, k) => (
              <Glyphs key={k} font={fortune.font} glyphs={g} color={LACQUER} />
            ))}
          </Group>
        </Canvas>
      ) : null}

      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Fortune cookie. Tap or press and hold to crack it open." />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.appBar, { top: insets.top + 14 }]}>
        <Text style={styles.appName}>Golden Wok</Text>
        <Text style={styles.appMeta}>Order #GW-3318</Text>
      </View>
      <View pointerEvents="none" style={[styles.head, { top: mTop + 26 }]}>
        <View style={styles.badge}>
          <View style={styles.check}>
            <Svg width={8} height={8} viewBox="0 0 8 8">
              <SvgPath d="M1.2 4.2 L3.2 6 L6.8 2" stroke="#F2FBF3" strokeWidth={1.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
          </View>
          <Text style={styles.badgeText}>ORDER DELIVERED · 7:42 PM</Text>
        </View>
        <Text style={styles.title}>Your fortune is ready</Text>
        <View style={styles.subBox}>
          <Animated.Text style={[styles.sub, subA]}>Tap or squeeze the cookie to crack it</Animated.Text>
          <Animated.Text style={[styles.sub, styles.subOver, subB]}>Baked into every Golden Wok order</Animated.Text>
        </View>
      </View>
      <View pointerEvents="none" style={[styles.lucky, { top: slipY + SLIP_H / 2 + 20 }]}>
        <Text style={styles.luckyLabel}>LUCKY NUMBERS</Text>
        <View style={styles.chips}>
          {LUCKY.map((n, i) => (
            <Chip key={n} clock={clock} i={i} n={n} />
          ))}
        </View>
      </View>
      <View style={[styles.actions, { bottom: insets.bottom + 40, left: mx + 20, right: mx + 20 }]}>
        <Pressable style={({ pressed }) => [styles.button, styles.secondary, pressed && styles.pressed]} accessibilityRole="button">
          <Text style={[styles.buttonText, { color: INK }]}>Save</Text>
        </Pressable>
        <Pressable onPress={close} style={({ pressed }) => [styles.button, styles.primary, pressed && styles.pressed]} accessibilityRole="button">
          <Text style={[styles.buttonText, { color: '#FFF4E6' }]}>Close</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#1C0E0B' },
  appBar: { position: 'absolute', left: 24, right: 24, height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  appName: { color: 'rgba(255,236,214,0.55)', fontSize: 17, fontWeight: '700' },
  appMeta: { color: 'rgba(255,236,214,0.4)', fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  head: { position: 'absolute', left: 40, right: 40, alignItems: 'center', gap: 8 },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 24, paddingHorizontal: 10, borderRadius: 12, backgroundColor: 'rgba(62,122,78,0.12)' },
  check: { width: 14, height: 14, borderRadius: 7, backgroundColor: '#3E7A4E', alignItems: 'center', justifyContent: 'center' },
  badgeText: { color: '#356A43', fontSize: 11, fontWeight: '700', letterSpacing: 0.8, fontVariant: ['tabular-nums'] },
  title: { color: INK, fontSize: 26, fontWeight: '800', letterSpacing: -0.4, textAlign: 'center' },
  subBox: { height: 20, alignSelf: 'stretch', overflow: 'hidden' },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500', textAlign: 'center', lineHeight: 20 },
  subOver: { position: 'absolute', left: 0, right: 0, top: 0 },
  lucky: { position: 'absolute', left: 40, right: 40, alignItems: 'center', gap: 8 },
  luckyLabel: { color: MUTE, fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  chips: { flexDirection: 'row', gap: 8 },
  actions: { position: 'absolute', height: 52, flexDirection: 'row', gap: 12 },
  button: { flex: 1, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  secondary: { backgroundColor: 'rgba(59,31,23,0.07)', borderWidth: 1, borderColor: 'rgba(59,31,23,0.14)' },
  primary: { backgroundColor: LACQUER, boxShadow: '0 1px 2px rgba(80,20,10,0.4), 0 10px 22px rgba(120,30,20,0.25)' },
  pressed: { transform: [{ scale: 0.97 }] },
  buttonText: { fontSize: 17, fontWeight: '700' },
});
