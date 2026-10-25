import { BlurMask, Canvas, Fill, Group, LinearGradient, Path, Picture, Rect, RoundedRect, Shader, Skia, matchFont, rrect, rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as ViewGradient } from 'expo-linear-gradient';
import { useEffect, useMemo } from 'react';
import { Platform, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { GOLD, INK, INK_SOFT, PAGE, recordPoster, recordSeats } from './velvetArt';
import {
  IDLE_BEFORE_RETURN,
  LOOP,
  LOOP_SEAT_AT,
  LOOP_SEATS,
  PELMET,
  PRICE,
  RETURN_FOR,
  SEAT_H,
  SEAT_W,
  SWAY_OMEGA,
  SWAY_ZETA,
  barAt,
  clamp,
  easeInOut,
  easeOut,
  hemAt,
  isTaken,
  loopSeat,
  popScale,
  pressAt,
  railAt,
  rowPitch,
  seatAt,
  seatIndexAt,
  seatName,
} from './velvetMotion';
import { CURTAIN_SKSL, PAGE_SKSL } from './velvetShader';

/*
 Velvet Curtain Sheet — "Odeon Lane", tonight's screening of "The Glass Orchard". The seat picker
 is a sheet whose cover is a theatre curtain: two red velvet halves that part from the middle,
 bunching their folds toward the sides, with a gold fringe at the hem. The loop is in
 velvetMotion.ts (LOOP = 9 s).

 Touch: drag the curtain sideways to open or close it (the finger's side of the centre sets the
 direction; rubber-banded past both ends; released, it springs to the nearer end with the finger's
 velocity, ω 10, ζ 0.85); the hem trails the rail on its own spring (ω 9, ζ 0.35), so the cloth
 swings. Drag the sheet up or down between its half and full detents (ω 14, ζ 0.86, the target
 chosen from the projected position). Tap the closed curtain to open it, tap a seat to pick it
 (a 0.35 s pop). Light haptic at a detent or when the curtain lands; a selection tick per seat.
 2 s after the last touch the curtain closes, the sheet returns to its half detent (0.8 s,
 ease-in-out), the picks clear behind the curtain and the loop restarts. Reduced motion: no loop,
 no sway, critically damped springs.
*/

type Props = {
  /** Plays the 9 s demo: open the curtain, pick C7 and C8, close it. */
  autoPlay?: boolean;
};

const P3 = Platform.OS === 'ios' ? 1 : 0;
const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const RADIUS = 28;
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

function feel(kind: number) {
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function VelvetCurtainSheet({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;

  const halfTop = Math.round(height - clamp(height * 0.56, 380, 480));
  const fullTop = insets.top + 12;
  const drawH = height - fullTop + 60;
  const pitch = rowPitch(height - halfTop);
  const pillY = (PELMET + (height - halfTop)) / 2 - 24;

  const curtainFx = useMemo(() => Skia.RuntimeEffect.Make(CURTAIN_SKSL), []);
  const pageFx = useMemo(() => Skia.RuntimeEffect.Make(PAGE_SKSL), []);
  const label = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: 10, fontWeight: '600' }), []);
  const poster = useMemo(() => recordPoster(width, halfTop + 60), [width, halfTop]);
  const seats = useMemo(() => recordSeats(width, pitch, label), [width, pitch, label]);

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const mode = useSharedValue(0); // 0 loop, 1 finger down, 2 released, 3 returning
  const axis = useSharedValue(0); // 1 sheet (vertical), 2 curtain (horizontal)
  const o = useSharedValue(0);
  const oV = useSharedValue(0);
  const oGoal = useSharedValue(0);
  const hem = useSharedValue(0);
  const hemV = useSharedValue(0);
  const top = useSharedValue(halfTop);
  const topV = useSharedValue(0);
  const topGoal = useSharedValue(halfTop);
  const bar = useSharedValue(0);
  const press = useSharedValue(1);
  const picked = useSharedValue<number[]>([]); // [seat, lit at, seat, lit at, …]
  const idle = useSharedValue(0);
  const ret = useSharedValue(0);
  const fromO = useSharedValue(0);
  const fromTop = useSharedValue(halfTop);
  const start = useSharedValue(0);
  const side = useSharedValue(1);

  useEffect(() => {
    clock.set(0);
    mode.set(0);
    top.set(halfTop);
  }, [auto, halfTop, clock, mode, top]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      top.set(halfTop);
      if (!auto) {
        o.set(0);
        hem.set(0);
        bar.set(0);
        press.set(1);
        return;
      }
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      o.set(railAt(t));
      hem.set(hemAt(t));
      bar.set(barAt(t));
      press.set(pressAt(t));
      return;
    }
    const zeta = reduced ? 1 : 0.85;
    if (!(m === 1 && axis.get() === 2) && m !== 3) {
      const a = -100 * (o.get() - oGoal.get()) - 2 * zeta * 10 * oV.get();
      oV.set(oV.get() + a * dt);
      o.set(o.get() + oV.get() * dt);
    }
    if (!(m === 1 && axis.get() === 1) && m !== 3) {
      const a = -196 * (top.get() - topGoal.get()) - 2 * (reduced ? 1 : 0.86) * 14 * topV.get();
      topV.set(topV.get() + a * dt);
      top.set(top.get() + topV.get() * dt);
    }
    if (m === 3) {
      const r = Math.min(1, ret.get() + dt / RETURN_FOR);
      ret.set(r);
      const e = easeInOut(r);
      o.set(fromO.get() * (1 - e));
      top.set(fromTop.get() + (halfTop - fromTop.get()) * e);
      if (r >= 1) {
        picked.set([]);
        mode.set(0);
        clock.set(0);
        o.set(0);
        hem.set(0);
        return;
      }
    }
    // The hem trails the rail.
    if (reduced) hem.set(o.get());
    else {
      const a = -SWAY_OMEGA * SWAY_OMEGA * (hem.get() - o.get()) - 2 * SWAY_ZETA * SWAY_OMEGA * hemV.get();
      hemV.set(hemV.get() + a * dt);
      hem.set(hem.get() + hemV.get() * dt);
      if (m === 3) hem.set(hem.get() + (o.get() - hem.get()) * easeInOut(ret.get()));
    }
    bar.set(bar.get() + ((o.get() > 0.6 && m !== 3 ? 1 : 0) - bar.get()) * Math.min(1, dt * 9));
    press.set(1);
    if (m === 2) {
      idle.set(idle.get() + dt);
      if (idle.get() > IDLE_BEFORE_RETURN) {
        mode.set(3);
        ret.set(0);
        fromO.set(o.get());
        fromTop.set(top.get());
      }
    }
  });

  /** Hands the screen from the loop to the finger, keeping what the loop shows. */
  function takeOver() {
    'worklet';
    if (mode.get() === 0) {
      oV.set(0);
      hemV.set(0);
      topV.set(0);
      topGoal.set(halfTop);
      oGoal.set(o.get() > 0.5 ? 1 : 0);
      const t = clock.get();
      const list: number[] = [];
      for (let k = 0; k < LOOP_SEATS.length; k += 1) if (auto && loopSeat(t, k) > 0.5) list.push(LOOP_SEATS[k], now.get() - 1);
      picked.set(list);
    }
    idle.set(0);
  }

  function rubber01(v: number) {
    'worklet';
    if (v < 0) return v / (1 - v * 3) * 0.3;
    if (v > 1) return 1 + ((v - 1) / (1 + (v - 1) * 3)) * 0.3;
    return v;
  }

  const sheetPan = Gesture.Pan()
    .activeOffsetY([-10, 10])
    .failOffsetX([-14, 14])
    .onStart(() => {
      takeOver();
      mode.set(1);
      axis.set(1);
      start.set(top.get());
    })
    .onUpdate((e) => {
      let y = start.get() + e.translationY;
      if (y < fullTop) y = fullTop - (fullTop - y) / (1 + (fullTop - y) / 50);
      if (y > halfTop) y = halfTop + (y - halfTop) / (1 + (y - halfTop) / 70);
      top.set(y);
      topV.set(e.velocityY);
    })
    .onEnd((e) => {
      const projected = top.get() + e.velocityY * 0.2;
      const goal = Math.abs(projected - fullTop) < Math.abs(projected - halfTop) ? fullTop : halfTop;
      if (goal !== topGoal.get()) scheduleOnRN(feel, 1);
      topGoal.set(goal);
    })
    .onFinalize(() => {
      mode.set(2);
      axis.set(0);
      idle.set(0);
    });

  const curtainPan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-14, 14])
    .onStart((e) => {
      takeOver();
      mode.set(1);
      axis.set(2);
      start.set(o.get());
      side.set(e.x < width / 2 ? -1 : 1);
    })
    .onUpdate((e) => {
      const span = width * 0.42;
      o.set(rubber01(start.get() + (side.get() * e.translationX) / span));
      oV.set((side.get() * e.velocityX) / span);
    })
    .onEnd(() => {
      const goal = o.get() + oV.get() * 0.2 > 0.5 ? 1 : 0;
      oGoal.set(goal);
      scheduleOnRN(feel, 1);
    })
    .onFinalize(() => {
      mode.set(2);
      axis.set(0);
      idle.set(0);
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, ok) => {
      if (!ok) return;
      takeOver();
      mode.set(2);
      if (o.get() < 0.5) {
        if (e.y > PELMET) {
          oGoal.set(1);
          scheduleOnRN(feel, 1);
        }
        return;
      }
      const i = seatIndexAt(e.x, e.y, width, pitch);
      if (i < 0 || isTaken(i)) return;
      const list = picked.get().slice();
      let found = -1;
      for (let k = 0; k < list.length; k += 2) if (list[k] === i) found = k;
      if (found >= 0) list.splice(found, 2);
      else list.push(i, now.get());
      picked.set(list);
      scheduleOnRN(feel, 0);
    });

  const gesture = Gesture.Race(sheetPan, curtainPan, tap);

  // Seats that are lit this frame: [seat, light 0..1, pop scale, …].
  const lit = useDerivedValue(() => {
    const out: number[] = [];
    if (mode.get() === 0) {
      if (!auto) return out;
      const t = clock.get();
      for (let k = 0; k < LOOP_SEATS.length; k += 1) {
        const a = loopSeat(t, k);
        if (a > 0.01) out.push(LOOP_SEATS[k], a, reduced ? 1 : popScale(t - LOOP_SEAT_AT[k]));
      }
      return out;
    }
    const list = picked.get();
    for (let k = 0; k < list.length; k += 2) {
      const e = now.get() - list[k + 1];
      out.push(list[k], clamp(e / 0.15, 0, 1), reduced ? 1 : popScale(e));
    }
    return out;
  });
  const litPath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const l = lit.get();
    for (let k = 0; k < l.length; k += 3) {
      const [x, y] = seatAt(l[k], width, pitch);
      const s = (0.86 + 0.14 * easeOut(l[k + 1])) * l[k + 2];
      const w = SEAT_W * s;
      const h = SEAT_H * s;
      b.addRRect(Skia.RRectXY(Skia.XYWHRect(x + SEAT_W / 2 - w / 2, y + SEAT_H / 2 - h / 2, w, h), 5 * s, 5 * s));
    }
    return b.build();
  });
  const sheetT = useDerivedValue(() => [{ translateY: top.get() }]);
  const uniforms = useDerivedValue(() => ({
    size: [width, drawH],
    curtain: [PELMET, height - top.get() - insets.bottom * 0.5 - 4, o.get(), hem.get()],
    p3: P3,
  }));
  const pageUniforms = useMemo(() => ({ size: [width, height], p3: P3 }), [width, height]);
  const clip = useMemo(() => rrect(rect(0, 0, width, drawH), RADIUS, RADIUS), [width, drawH]);

  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: top.get() }] }));
  const pillStyle = useAnimatedStyle(() => ({
    opacity: 1 - clamp(o.get() * 3, 0, 1),
    transform: [{ scale: press.get() }],
  }));
  // The page's title gives way as the sheet rises over it.
  const pageStyle = useAnimatedStyle(() => ({ opacity: clamp((top.get() - fullTop) / Math.max(1, halfTop - fullTop), 0, 1) }));
  const barStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - bar.get()) * 150 }], opacity: clamp(bar.get() * 2, 0, 1) }));
  const summaryProps = useAnimatedProps(() => {
    const l = lit.get();
    const names: string[] = [];
    for (let k = 0; k < l.length; k += 3) names.push(seatName(l[k]));
    names.sort();
    const text = names.length === 0 ? 'Tap a seat to pick it' : `${names.length} seat${names.length > 1 ? 's' : ''} · ${names.join(', ')}`;
    return { text, defaultValue: text } as unknown as Record<string, unknown>;
  });
  const priceProps = useAnimatedProps(() => {
    const text = `$${((lit.get().length / 3) * PRICE).toFixed(2)}`;
    return { text, defaultValue: text } as unknown as Record<string, unknown>;
  });

  return (
    <View style={styles.root}>
      <Canvas style={StyleSheet.absoluteFill}>
        {pageFx ? (
          <Fill>
            <Shader source={pageFx} uniforms={pageUniforms} />
          </Fill>
        ) : null}
        <Picture picture={poster} />
        <Group transform={sheetT}>
          {/* The sheet's shadow on the page above it. */}
          <Rect x={8} y={-6} width={width - 16} height={40} color="rgba(0,0,0,0.55)">
            <BlurMask blur={14} style="normal" />
          </Rect>
          <Group clip={clip}>
            <Rect x={0} y={0} width={width} height={drawH}>
              <LinearGradient start={vec(0, 0)} end={vec(0, drawH)} colors={['#22121A', '#170C12', '#10080C']} positions={[0, 0.4, 1]} />
            </Rect>
            <Picture picture={seats} />
            <Path path={litPath} color={GOLD} opacity={0.7}>
              <BlurMask blur={7} style="normal" />
            </Path>
            <Path path={litPath} color={GOLD} />
            {curtainFx ? (
              <Rect x={0} y={0} width={width} height={drawH}>
                <Shader source={curtainFx} uniforms={uniforms} />
              </Rect>
            ) : null}
            <RoundedRect x={width / 2 - 18} y={7} width={36} height={4} r={2} color="rgba(255,226,170,0.45)" />
          </Group>
        </Group>
      </Canvas>

      <Animated.View pointerEvents="none" style={[styles.page, { bottom: height - halfTop + 28 }, pageStyle]}>
        <Text style={styles.kicker}>ODEON LANE · SCREEN 3</Text>
        <Text style={styles.title}>The Glass Orchard</Text>
        <Text style={styles.meta}>Tonight 19:40 · 2 h 06 min · 12A</Text>
        <View style={styles.chips}>
          {['Drama', 'Mystery', 'Rated 4.6'].map((c) => (
            <View key={c} style={styles.chip}>
              <Text style={styles.chipText}>{c}</Text>
            </View>
          ))}
        </View>
      </Animated.View>

      <GestureDetector gesture={gesture}>
        <Animated.View
          style={[styles.sheet, { height: drawH }, sheetStyle]}
          accessible
          accessibilityLabel="Seat picker behind a curtain. Tap or drag the curtain sideways to open it, tap a seat to pick it."
        >
          <Animated.View pointerEvents="none" style={[styles.pillWrap, { top: pillY }, pillStyle]}>
            <ViewGradient colors={['#FFE3A0', '#F2C060', '#C98E2E']} locations={[0, 0.5, 1]} style={styles.pill}>
              <Text style={styles.pillText}>Choose seats</Text>
            </ViewGradient>
          </Animated.View>
        </Animated.View>
      </GestureDetector>

      <Animated.View pointerEvents="box-none" style={[styles.bar, { paddingBottom: insets.bottom + 12 }, barStyle]}>
        <View style={{ flex: 1 }}>
          <AnimatedTextInput editable={false} defaultValue="Tap a seat to pick it" animatedProps={summaryProps} style={styles.barTitle} underlineColorAndroid="transparent" />
          <Text style={styles.barSub}>Tonight 19:40 · Screen 3</Text>
        </View>
        <AnimatedTextInput editable={false} defaultValue="$0.00" animatedProps={priceProps} style={styles.barPrice} underlineColorAndroid="transparent" />
        <View style={styles.cta}>
          <Text style={styles.ctaText}>Continue</Text>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: PAGE },
  page: { position: 'absolute', left: 20, right: 20, gap: 6 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 1.6 },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '700', letterSpacing: -0.6 },
  meta: { color: INK_SOFT, fontSize: 15, fontWeight: '500', fontVariant: ['tabular-nums'] },
  chips: { flexDirection: 'row', gap: 8, marginTop: 6 },
  chip: { height: 28, paddingHorizontal: 12, borderRadius: 14, justifyContent: 'center', backgroundColor: 'rgba(243,233,228,0.08)', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(243,233,228,0.16)' },
  chipText: { color: INK, fontSize: 13, fontWeight: '500' },
  sheet: { position: 'absolute', left: 0, right: 0, top: 0 },
  pillWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  pill: {
    height: 48,
    paddingHorizontal: 28,
    borderRadius: 24,
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(40,10,10,0.5), 0 12px 28px rgba(20,4,8,0.55), inset 0 1px 0 rgba(255,250,230,0.6)',
  },
  pillText: { color: '#3A1A0C', fontSize: 16, fontWeight: '700', letterSpacing: 0.2 },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: 14,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: 'rgba(24,12,18,0.96)',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(242,192,96,0.25)',
  },
  barTitle: { color: INK, fontSize: 16, fontWeight: '600', padding: 0 },
  barSub: { color: INK_SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'], marginTop: 2 },
  barPrice: { color: INK, fontSize: 17, fontWeight: '700', fontVariant: ['tabular-nums'], padding: 0, minWidth: 64, textAlign: 'right' },
  cta: { height: 44, paddingHorizontal: 18, borderRadius: 22, justifyContent: 'center', backgroundColor: GOLD },
  ctaText: { color: '#3A1A0C', fontSize: 15, fontWeight: '700' },
});
