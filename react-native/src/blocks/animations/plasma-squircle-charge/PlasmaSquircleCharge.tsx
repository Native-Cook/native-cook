import { BlurMask, Canvas, Circle, Fill, Group, Image, LinearGradient, Rect, RoundedRect, Shader, Skia, useImage, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// An invented smart-home hub, Lumen, waking the house. A smoked-glass hub (generated artwork,
// assets/hub.png) rests on a dark violet stage inside the faint outline of a wide squircle.
// Holding it sends electric sparks crawling out from the middles of the outline's four sides
// until they meet at the corners; then the outline ignites into a crackling violet ring with a
// blue bloom, the hub's glass and status light glow, its reflection brightens, the status reads
// "Online" and the three room cards light up one after another. Letting go powers it down.
//
// Everything follows one value, `level` (0 rest, 0.6 the sparks have met, 1 ignited):
//   cover  = smooth(level / 0.6)          how much of the outline is lit, from the side middles
//   charge = 0.55 cover + 0.45 smooth((level - 0.6) / 0.4)   brightness
// The crackle is a sum of sines of the angle round the outline whose time terms turn a whole
// number of times per loop, so the loop is seamless.
//
// Layout (393 x 852 pt): headline "Wake the / home." Archivo Expanded Black 38/42 pt under a
// 34 pt top row; hub 232 x 146 pt centred at 52 % of the height, the ring a rounded box of
// half-size 156 x 116 pt, corners 74 pt, round it; status 150 pt below the centre (Archivo
// Expanded Black 22 pt) with a 14 pt line under it; three room cards (84 pt tall) at the bottom.
//
// autoPlay timeline (LOOP = 6 s), t in seconds:
//   0.0-0.8  rest: faint outline (12 %), "Hold to wake", rooms off
//   0.8-2.0  level 0 -> 0.6 (ease-in-out cubic): sparks crawl to the corners
//   2.0-2.4  level 0.6 -> 1 (ease-out cubic): ignition; "Online" fades in as level passes 0.75
//   2.45-3.06 the rooms light one after another (0.18 s apart, 0.25 s each)
//   2.4-4.4  hold, crackling
//   4.4-5.4  level 1 -> 0 (ease-in cubic): powers down; the rooms go off (4.4-4.9, 0.1 s apart)
//   5.4-6.0  rest, identical to t = 0
// Holding raises level by 1 per 1.4 s (rooms follow level past 0.9); after release it stays
// 2 s, then falls by 1 per second, and the loop restarts.

/** Seconds. */
export const LOOP = 6;
const HUB_W = 232;
const HUB_H = HUB_W * (340 / 540);
const RING_W = 156;
const RING_H = 116;
const CORNER = 74;
const RISE = 1.4;
const HOLD = 2;
const FALL = 1;

const C = {
  background: '#05040B',
  text: '#F4F2FF',
  dim: '#8A86A3',
  card: '#100E1A',
  cardLine: '#25213A',
  glow: '#A898FF',
  blue: '#5B5BFF',
};

const ROOMS = ['Living room', 'Kitchen', 'Studio'];

const BLACK = require('./assets/ArchivoExpanded-Black.ttf');
const MEDIUM = require('./assets/Archivo-Medium.ttf');
const SEMIBOLD = require('./assets/Archivo-SemiBold.ttf');
const BLACK_FAMILY = 'PlasmaSquircleCharge-ArchivoExpandedBlack';
const MEDIUM_FAMILY = 'PlasmaSquircleCharge-ArchivoMedium';
const SEMIBOLD_FAMILY = 'PlasmaSquircleCharge-ArchivoSemiBold';

// The whole background: the dark stage, the faint outline and, when charged, the electric ring.
// Skia treats RuntimeEffect output as Display P3 on iOS, so colours go through toP3 when p3 = 1.
const PLASMA_SKSL = `
uniform float2 centre;
uniform float2 halfSize;
uniform float corner;
uniform float cover;
uniform float charge;
uniform float phase;
uniform float p3;

const float TAU = 6.28318530718;

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float roundBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
  float2 p = xy - centre;
  float a = atan(p.y, p.x);
  float d = roundBox(p, halfSize, corner);
  float T = phase * TAU;

  // The wandering of the arc: three waves round the outline, each turning a whole number of
  // times per loop.
  float amp = 1.2 + 3.4 * charge;
  float wobble = amp * (0.5 * sin(a * 7.0 + T * 3.0) + 0.3 * sin(a * 13.0 - T * 5.0) + 0.2 * sin(a * 29.0 + T * 11.0));
  float e = d + wobble;

  // How much of the outline is lit: from the middles of the sides towards the corners.
  float da = abs(mod(a + 0.785398, 1.570796) - 0.785398);
  float reach = cover * 0.86;
  float lit = cover >= 0.999 ? 1.0 : smoothstep(reach + 0.05, reach - 0.05, da) * step(0.001, cover);

  float crackle = 0.72 + 0.28 * sin(a * 41.0 + T * 23.0) * sin(a * 17.0 - T * 17.0);

  float core = exp(-abs(e) / 1.2);
  float halo = exp(-abs(e) / 10.0);
  float bloom = 1.0 / (1.0 + (d / 40.0) * (d / 40.0));
  float rest = exp(-abs(d) / 0.9) * 0.14;
  // The stage: a violet pool of light under the hub, a little brighter when charged.
  float2 s = (p - float2(0.0, 40.0)) / float2(230.0, 150.0);
  float stage = exp(-dot(s, s) * 1.6);

  float3 white = float3(0.95, 0.93, 1.0);
  float3 violet = float3(0.60, 0.48, 1.0);
  float3 blue = float3(0.30, 0.28, 0.95);
  float3 bg = float3(0.020, 0.016, 0.043);

  float on = charge * lit * crackle;
  float3 col = bg + float3(0.10, 0.07, 0.24) * stage * (0.55 + 0.45 * charge);
  col += white * rest;
  col += violet * halo * on * 0.9;
  col += white * core * on * 1.3;
  col += blue * bloom * charge * charge * 0.38;
  col = 1.0 - exp(-col * 1.4);
  return half4(half3(p3 > 0.5 ? toP3(col) : col), 1.0);
}
`;
const plasmaEffect = Skia.RuntimeEffect.Make(PLASMA_SKSL)!;
const P3 = Platform.OS === 'ios' ? 1 : 0;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** `level`, `t` seconds into the loop. */
function levelAt(t: number) {
  'worklet';
  const build = 0.6 * easeInOutCubic(seg(t, 0.8, 2.0)) + 0.4 * (1 - Math.pow(1 - seg(t, 2.0, 2.4), 3));
  const down = Math.pow(seg(t, 4.4, 5.4), 3);
  return build * (1 - down);
}
/** Room k's light, `t` seconds into the loop. */
function roomAt(t: number, k: number) {
  'worklet';
  return smooth(seg(t, 2.45 + 0.18 * k, 2.7 + 0.18 * k)) * (1 - smooth(seg(t, 4.4 + 0.1 * k, 4.9 + 0.1 * k)));
}
function coverOf(level: number) {
  'worklet';
  return smooth(level / 0.6);
}
function chargeOf(level: number) {
  'worklet';
  return 0.55 * coverOf(level) + 0.45 * smooth((level - 0.6) / 0.4);
}

function RoomCard({ name, light }: { name: string; light: SharedValue<number> }) {
  const dot = useAnimatedStyle(() => ({
    backgroundColor: light.get() > 0.5 ? C.glow : '#3A3552',
    shadowOpacity: light.get(),
    transform: [{ scale: 1 + 0.25 * Math.sin(Math.PI * light.get()) }],
  }));
  const card = useAnimatedStyle(() => ({ borderColor: light.get() > 0.5 ? 'rgba(168,152,255,0.55)' : C.cardLine }));
  const off = useAnimatedStyle(() => ({ opacity: 1 - light.get() }));
  const on = useAnimatedStyle(() => ({ opacity: light.get() }));
  return (
    <Animated.View style={[styles.card, card]}>
      <Animated.View style={[styles.dot, dot]} />
      <Text style={styles.cardName} numberOfLines={1}>
        {name}
      </Text>
      <View style={styles.stateBox}>
        <Animated.Text style={[styles.state, off]}>Off</Animated.Text>
        <Animated.Text style={[styles.state, styles.stateOn, on]}>On</Animated.Text>
      </View>
    </Animated.View>
  );
}

type Props = {
  brand?: string;
  idle?: string;
  online?: string;
  detail?: string;
  /** Wakes the hub by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function PlasmaSquircleCharge({ brand = 'lumen', idle = 'Hold to wake', online = 'Online', detail = 'Lights, music and climate wake together.', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK_FAMILY]: BLACK, [MEDIUM_FAMILY]: MEDIUM, [SEMIBOLD_FAMILY]: SEMIBOLD });
  const hub = useImage(require('./assets/hub.png'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.52;
  const hubX = cx - HUB_W / 2;
  const hubY = cy - HUB_H / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const phase = useSharedValue(0);
  const level = useSharedValue(reduced ? 1 : 0);
  const touching = useSharedValue(false);
  const released = useSharedValue(-1);
  const manual = useSharedValue(false);
  const room0 = useSharedValue(reduced ? 1 : 0);
  const room1 = useSharedValue(reduced ? 1 : 0);
  const room2 = useSharedValue(reduced ? 1 : 0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    phase.set(((now - origin.get()) % LOOP) / LOOP);
    if (touching.get() || manual.get()) {
      if (touching.get()) level.set(Math.min(1, level.get() + dt / RISE));
      else if (now - released.get() > HOLD) level.set(Math.max(0, level.get() - dt / FALL));
      const l = level.get();
      room0.set(smooth((l - 0.9) / 0.04));
      room1.set(smooth((l - 0.94) / 0.04));
      room2.set(smooth((l - 0.98) / 0.02));
      if (!touching.get() && l <= 0) {
        manual.set(false);
        origin.set(now);
      }
      return;
    }
    const t = autoPlay ? (now - origin.get()) % LOOP : 0;
    level.set(autoPlay ? levelAt(t) : 0);
    room0.set(autoPlay ? roomAt(t, 0) : 0);
    room1.set(autoPlay ? roomAt(t, 1) : 0);
    room2.set(autoPlay ? roomAt(t, 2) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const uniforms = useDerivedValue(() => {
    const l = level.get();
    return {
      centre: [cx, cy],
      halfSize: [RING_W, RING_H],
      corner: CORNER,
      cover: coverOf(l),
      charge: chargeOf(l),
      phase: phase.get(),
      p3: P3,
    };
  });
  const charge = useDerivedValue(() => smooth((level.get() - 0.6) / 0.4));
  const hubGlow = useDerivedValue(() => 0.15 + 0.85 * charge.get());
  const sheen = useDerivedValue(() => 0.45 * charge.get());
  const statusLight = useDerivedValue(() => 0.25 + 0.75 * charge.get());
  const reflection = useDerivedValue(() => 0.12 + 0.16 * charge.get());
  const flip = useMemo(() => [{ translateY: hubY + HUB_H * 2 + 6 }, { scaleY: -1 }, { translateY: -hubY }], [hubY]);

  const idleStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth((level.get() - 0.75) / 0.25) }));
  const onlineStyle = useAnimatedStyle(() => ({ opacity: smooth((level.get() - 0.75) / 0.25) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const hold = Gesture.LongPress()
    .enabled(!reduced)
    .minDuration(0)
    .maxDistance(2000)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      scheduleOnRN(haptic);
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  const statusTop = cy + 150;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={hold}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${brand} hub. Hold to wake the home.`}>
            <Fill>
              <Shader source={plasmaEffect} uniforms={uniforms} />
            </Fill>
            {hub ? (
              <>
                {/* Its reflection on the stage, fading downwards. */}
                <Group opacity={reflection}>
                  <Group transform={flip}>
                    <Image image={hub} x={hubX} y={hubY} width={HUB_W} height={HUB_H} fit="fill" />
                  </Group>
                  <Rect x={hubX - 20} y={hubY + HUB_H + 6} width={HUB_W + 40} height={HUB_H}>
                    <LinearGradient start={vec(0, hubY + HUB_H)} end={vec(0, hubY + HUB_H * 1.8)} colors={['rgba(5,4,11,0)', 'rgba(5,4,11,1)']} />
                  </Rect>
                </Group>
                {/* A violet glow behind the hub. */}
                <Group opacity={hubGlow}>
                  <RoundedRect x={hubX + 10} y={hubY + 12} width={HUB_W - 20} height={HUB_H - 16} r={40} color={C.glow}>
                    <BlurMask blur={28} style="normal" />
                  </RoundedRect>
                </Group>
                <Image image={hub} x={hubX} y={hubY} width={HUB_W} height={HUB_H} fit="fill" />
                {/* Light caught in the glass top. */}
                <Group opacity={sheen} blendMode="screen">
                  <RoundedRect x={hubX + 18} y={hubY + 8} width={HUB_W - 36} height={HUB_H * 0.55} r={36}>
                    <LinearGradient start={vec(0, hubY)} end={vec(0, hubY + HUB_H * 0.6)} colors={['rgba(168,152,255,0.9)', 'rgba(91,91,255,0)']} />
                  </RoundedRect>
                </Group>
                <Group opacity={statusLight}>
                  <Circle cx={cx} cy={hubY + HUB_H * 0.865} r={6} color="#FFFFFF">
                    <BlurMask blur={5} style="normal" />
                  </Circle>
                  <Circle cx={cx} cy={hubY + HUB_H * 0.865} r={2.4} color="#FFFFFF" />
                </Group>
              </>
            ) : null}
          </Canvas>

          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>{brand}</Text>
              <Text style={styles.home}>Home · 3 rooms</Text>
            </View>
            <Text style={styles.headline}>{'Wake the\nhome.'}</Text>
          </View>

          <View style={[styles.status, { top: statusTop }]} pointerEvents="none">
            <Animated.Text style={[styles.statusText, idleStyle]}>{idle}</Animated.Text>
            <Animated.Text style={[styles.statusText, onlineStyle]}>{online}</Animated.Text>
          </View>
          <Text style={[styles.detail, { top: statusTop + 36 }]} pointerEvents="none">
            {detail}
          </Text>

          <View style={[styles.rooms, { bottom: insets.bottom + 20 }]} pointerEvents="none">
            <RoomCard name={ROOMS[0]} light={room0} />
            <RoomCard name={ROOMS[1]} light={room1} />
            <RoomCard name={ROOMS[2]} light={room2} />
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK_FAMILY, fontSize: 20, color: C.text, letterSpacing: -0.3 },
  home: { fontFamily: MEDIUM_FAMILY, fontSize: 13, color: C.dim },
  headline: { marginTop: 18, fontFamily: BLACK_FAMILY, fontSize: 38, lineHeight: 44, color: C.text, letterSpacing: -0.8 },
  status: { position: 'absolute', left: 0, right: 0, height: 30, alignItems: 'center' },
  statusText: { position: 'absolute', fontFamily: BLACK_FAMILY, fontSize: 22, lineHeight: 30, color: C.text, letterSpacing: -0.2 },
  detail: { position: 'absolute', left: 32, right: 32, textAlign: 'center', fontFamily: MEDIUM_FAMILY, fontSize: 14, lineHeight: 20, color: C.dim },
  rooms: { position: 'absolute', left: 16, right: 16, flexDirection: 'row', gap: 10 },
  card: { flex: 1, height: 88, borderRadius: 20, backgroundColor: C.card, borderWidth: 1, borderColor: C.cardLine, padding: 12, justifyContent: 'space-between' },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#3A3552', shadowColor: C.glow, shadowRadius: 8, shadowOffset: { width: 0, height: 0 } },
  cardName: { fontFamily: SEMIBOLD_FAMILY, fontSize: 13, color: C.text },
  stateBox: { height: 16 },
  state: { position: 'absolute', left: 0, fontFamily: MEDIUM_FAMILY, fontSize: 12, lineHeight: 16, color: C.dim },
  stateOn: { color: C.glow },
});
