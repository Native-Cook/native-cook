/**
 * The lever as the source draws it — a track that turns about its left pivot, a white fill that steps from
 * dot to dot, nine dots, a grey knob — with the upgrade layered round it, all monochrome:
 * a cast shadow that turns with it, a tick arc that lights with the haptic ticks, a breathing halo while
 * loading, a comet that runs down the shaft (lighting it behind) and lands in a ring on the knob, and a
 * specular highlight on the knob that stays toward the light.
 */
import { LinearGradient } from 'expo-linear-gradient';
import Svg, { Circle, Defs, RadialGradient, Stop } from 'react-native-svg';
import { StyleSheet, View } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { LIGHT, REPLICA_ONLY } from './motion';
import { COLOR, LEVER } from './theme';

const T = LEVER.thickness;
const DOTS = Array.from({ length: LEVER.dots }, (_, i) => T / 2 + LEVER.pitch * (i + 1));
const KNOB_X = LEVER.length - T / 2; // knob centre along the lever
const RUN = KNOB_X - T / 2; // the comet's path, pivot to knob
const TRAIL = Array.from({ length: LIGHT.cometTrail }, (_, i) => i + 1);
const ARC = Array.from({ length: LIGHT.arc.dots }, (_, i) => {
  const a = ((LIGHT.arc.from + ((LIGHT.arc.to - LIGHT.arc.from) * i) / (LIGHT.arc.dots - 1)) * Math.PI) / 180;
  return { x: T / 2 + Math.cos(a) * LIGHT.arc.radius, y: T / 2 + Math.sin(a) * LIGHT.arc.radius };
});

export type LeverValues = {
  angle: SharedValue<number>; // degrees, positive turns the free end down
  fill: SharedValue<number>; // 0 = only the pivot cap, 1 = the whole lever
  lit: SharedValue<number>; // 1 white, 0 dimmed to the track colour
  bloom: SharedValue<number>; // the cap swells once when it lights again
  glow: SharedValue<number>; // 0..1, the cap's halo while loading
  breath: SharedValue<number>; // −1..1, the halo's breathing
  pulse: SharedValue<number>; // 0..1 along the shaft, <0 = none
  ripple: SharedValue<number>; // 0..1, the ring off the knob
  knobLit: SharedValue<number>; // 0..1, the knob flashes when the comet lands
  arcLit: SharedValue<number>; // 0..7 tick dots lit
};

/** The source's fill moves in whole dots (155, 185, 215 … 306 px), so the drawn width is quantised. */
export function fillWidth(fill: number): number {
  'worklet';
  if (fill >= 1) return LEVER.length;
  const dots = Math.floor(Math.max(fill, 0) * LEVER.dots + 1e-3);
  return T + dots * LEVER.pitch;
}

function Dots({ color }: { color: string }) {
  return DOTS.map((x) => (
    <View key={x} style={[styles.dot, { left: x - LEVER.dot / 2, backgroundColor: color }]} />
  ));
}

function ArcDot({ i, arcLit }: { i: number; arcLit: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const on = Math.min(Math.max(arcLit.get() - i, 0), 1);
    return { backgroundColor: interpolateColor(on, [0, 1], [COLOR.arcOff, COLOR.fill]), transform: [{ scale: 1 + 0.5 * on }] };
  });
  const p = ARC[i];
  const d = LIGHT.arc.size;
  return <Animated.View style={[styles.arcDot, { left: p.x - d / 2, top: p.y - d / 2 }, style]} />;
}

function TrailDot({ k, pulse }: { k: number; pulse: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const p = pulse.get() - (k * LIGHT.cometTrailPt) / LIGHT.cometTrail / RUN;
    const on = pulse.get() < 0 || p < 0 ? 0 : 1 - k / (LIGHT.cometTrail + 1);
    return { opacity: on, transform: [{ translateX: T / 2 + RUN * Math.max(p, 0) }, { scale: 1 - k / (LIGHT.cometTrail + 2) }] };
  });
  return <Animated.View style={[styles.trail, style]} />;
}

/** A disc with a soft 1 pt edge, as the source's touch disc (a hard edge reads sharper than the source). */
function SoftDisc({ color }: { color: string }) {
  const r = LEVER.knob / 2;
  const soft = LIGHT.knobSoft / r;
  return (
    <Svg width={LEVER.knob + 2 * LIGHT.knobSoft} height={LEVER.knob + 2 * LIGHT.knobSoft} style={styles.disc}>
      <Defs>
        <RadialGradient id="d" cx="50%" cy="50%" r="50%">
          <Stop offset={1 - 2 * soft / (1 + soft)} stopColor={color} stopOpacity={1} />
          <Stop offset={1} stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Circle cx={r + LIGHT.knobSoft} cy={r + LIGHT.knobSoft} r={r + LIGHT.knobSoft} fill="url(#d)" />
    </Svg>
  );
}

export function PullLever({ angle, fill, lit, bloom, glow, breath, pulse, ripple, knobLit, arcLit }: LeverValues) {
  const turn = useAnimatedStyle(() => ({ transform: [{ rotate: `${angle.get()}deg` }] }));
  // The shadow is the same shape, offset toward the bottom right of the screen whatever the angle.
  const shadow = useAnimatedStyle(() => ({
    transform: [{ translateX: LIGHT.shadow.x }, { translateY: LIGHT.shadow.y }, { rotate: `${angle.get()}deg` }],
  }));
  // Width, not scaleX, so the rounded end keeps its shape.
  const filled = useAnimatedStyle(() => ({
    width: fillWidth(fill.get()),
    backgroundColor: interpolateColor(lit.get(), [0, 1], [COLOR.dimFill, COLOR.fill]),
  }));
  const cap = useAnimatedStyle(() => ({ transform: [{ scale: bloom.get() }] }));
  const halo = useAnimatedStyle(() => {
    const b = (breath.get() + 1) / 2;
    return {
      opacity: glow.get() * (LIGHT.haloMin + (LIGHT.haloMax - LIGHT.haloMin) * b),
      transform: [{ scale: (0.7 + 0.3 * glow.get()) * (1 + 0.12 * b) }],
    };
  });
  const head = useAnimatedStyle(() => {
    const p = pulse.get();
    return { opacity: p < 0 ? 0 : 1, transform: [{ translateX: T / 2 + RUN * Math.max(p, 0) }] };
  });
  const shaft = useAnimatedStyle(() => {
    const p = pulse.get();
    return { opacity: p < 0 ? 0 : LIGHT.shaftLight, width: T / 2 + RUN * Math.max(p, 0) };
  });
  const ring = useAnimatedStyle(() => {
    const r = ripple.get();
    return { opacity: r <= 0 || r >= 1 ? 0 : 0.7 * (1 - r), transform: [{ scale: 1 + ((LIGHT.ripple / LEVER.knob) - 1) * r }] };
  });
  // The knob is the source's soft touch disc: its colour flashes when the comet lands.
  const knobFlash = useAnimatedStyle(() => ({ opacity: knobLit.get() }));
  // The light comes from the top left of the screen: undo the lever's turn so the highlight stays there.
  // The specular only shows while the lever is away from rest, so the rest pose stays the source's flat disc.
  const highlight = useAnimatedStyle(() => {
    const a = ((LIGHT.direction - angle.get()) * Math.PI) / 180;
    const r = LEVER.knob * LIGHT.highlightOffset;
    const away = Math.min(Math.abs(angle.get() - LIGHT.restAngle) / LIGHT.highlightRamp, 1);
    return { opacity: away, transform: [{ translateX: Math.cos(a) * r }, { translateY: Math.sin(a) * r }] };
  });
  return (
    <View style={styles.box} pointerEvents="none">
      {!REPLICA_ONLY && <Animated.View style={[styles.lever, styles.castShadow, shadow]}>
        <View style={styles.knob}><SoftDisc color={COLOR.shadow} /></View>
      </Animated.View>}
      {!REPLICA_ONLY && <Animated.View style={[styles.halo, halo]} />}
      {!REPLICA_ONLY && ARC.map((_, i) => <ArcDot key={i} i={i} arcLit={arcLit} />)}
      <Animated.View style={[styles.lever, turn]}>
        <Dots color={COLOR.trackDot} />
        <Animated.View style={[styles.shaft, shaft]}>
          <LinearGradient colors={COLOR.shaftLight} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Animated.View style={[styles.capOrigin, cap]}>
          <Animated.View style={[styles.fill, filled]}>
            <Dots color={COLOR.fillDot} />
          </Animated.View>
        </Animated.View>
        <View style={styles.pivot} />
        <Animated.View style={[styles.ring, ring]} />
        <View style={styles.knob}>
          <SoftDisc color={COLOR.finger} />
          <Animated.View style={[StyleSheet.absoluteFill, knobFlash]}>
            <SoftDisc color={COLOR.knobLit} />
          </Animated.View>
          {!REPLICA_ONLY && <Animated.View style={[styles.highlight, highlight]} />}
        </View>
        {TRAIL.map((k) => <TrailDot key={k} k={k} pulse={pulse} />)}
        <Animated.View style={[styles.head, head]} />
      </Animated.View>
    </View>
  );
}

const H = LIGHT.cometHead;

const styles = StyleSheet.create({
  box: { width: LEVER.length, height: T },
  lever: {
    position: 'absolute', left: 0, top: 0,
    width: LEVER.length,
    height: T,
    borderRadius: T / 2,
    backgroundColor: COLOR.track,
    // Turn about the pivot dot, not the centre.
    transformOrigin: [T / 2, T / 2, 0],
  },
  castShadow: {
    backgroundColor: COLOR.shadow, opacity: LIGHT.shadow.opacity,
    shadowColor: COLOR.shadow, shadowOpacity: 1, shadowRadius: LIGHT.shadow.blur, shadowOffset: { width: 0, height: 0 },
  },
  // A white disc with a wide white shadow: a pre-blurred glow, centred on the pivot.
  halo: {
    position: 'absolute', left: T / 2 - LIGHT.haloSize / 2, top: T / 2 - LIGHT.haloSize / 2,
    width: LIGHT.haloSize, height: LIGHT.haloSize, borderRadius: LIGHT.haloSize / 2,
    backgroundColor: COLOR.fill, shadowColor: COLOR.fill, shadowOpacity: 1, shadowRadius: LIGHT.haloBlur, shadowOffset: { width: 0, height: 0 },
  },
  arcDot: { position: 'absolute', width: LIGHT.arc.size, height: LIGHT.arc.size, borderRadius: LIGHT.arc.size / 2 },
  shaft: { position: 'absolute', left: 0, top: 0, height: T, borderRadius: T / 2, overflow: 'hidden' },
  // The bloom scales about the pivot, so the cap swells in place.
  capOrigin: { position: 'absolute', left: 0, top: 0, width: LEVER.length, height: T, transformOrigin: [T / 2, T / 2, 0] },
  fill: { position: 'absolute', left: 0, top: 0, height: T, borderRadius: T / 2, overflow: 'hidden' },
  dot: { position: 'absolute', top: (T - LEVER.dot) / 2, width: LEVER.dot, height: LEVER.dot, borderRadius: LEVER.dot / 2 },
  // The grey knob at the free end (the source's 60 px circle).
  knob: {
    position: 'absolute', left: KNOB_X - LEVER.knob / 2, top: T / 2 - LEVER.knob / 2,
    width: LEVER.knob, height: LEVER.knob, alignItems: 'center', justifyContent: 'center',
  },
  disc: { position: 'absolute', left: -LIGHT.knobSoft, top: -LIGHT.knobSoft },
  ring: {
    position: 'absolute', left: KNOB_X - LEVER.knob / 2, top: T / 2 - LEVER.knob / 2,
    width: LEVER.knob, height: LEVER.knob, borderRadius: LEVER.knob / 2, borderWidth: 1.5, borderColor: COLOR.fill,
  },
  highlight: {
    width: LEVER.knob * LIGHT.highlightSize, height: LEVER.knob * LIGHT.highlightSize, borderRadius: LEVER.knob,
    backgroundColor: COLOR.specular, shadowColor: COLOR.fill, shadowOpacity: 0.6, shadowRadius: 4, shadowOffset: { width: 0, height: 0 },
  },
  head: {
    position: 'absolute', left: -H / 2, top: T / 2 - H / 2, width: H, height: H, borderRadius: H / 2,
    backgroundColor: COLOR.fill, shadowColor: COLOR.fill, shadowOpacity: 1, shadowRadius: LIGHT.cometBlur, shadowOffset: { width: 0, height: 0 },
  },
  trail: {
    position: 'absolute', left: -H / 2, top: T / 2 - H / 2, width: H, height: H, borderRadius: H / 2,
    backgroundColor: COLOR.fill, shadowColor: COLOR.fill, shadowOpacity: 0.8, shadowRadius: 6, shadowOffset: { width: 0, height: 0 },
  },
  pivot: {
    position: 'absolute',
    left: (T - LEVER.pivotDot) / 2,
    top: (T - LEVER.pivotDot) / 2,
    width: LEVER.pivotDot,
    height: LEVER.pivotDot,
    borderRadius: LEVER.pivotDot / 2,
    backgroundColor: COLOR.pivot,
  },
});
