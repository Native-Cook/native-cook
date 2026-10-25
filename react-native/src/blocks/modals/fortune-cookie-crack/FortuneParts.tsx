import {
  BlurMask,
  Circle,
  Fill,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  RoundedRect,
  Shader,
  Shadow,
  type SkPath,
  type SkRuntimeEffect,
  vec,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, Text } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { chipAt, hash } from './fortuneMotion';

/** The cookie's pieces, the lucky-number chips and the static backdrop. */

const LACQUER = '#A8322A';

/** The cookie is drawn 232 pt wide; this sizes it on screen. */
export const COOKIE_SCALE = 1.12;
const BODY_SHADE = { shade: 1 };
const LIP_SHADE = { shade: 0.84 };

/** A piece of the cookie: its baked body, the folded flap in front, and the flap's lit edge. */
export function CookiePiece({ body, lip, rim, inside, fx }: { body: SkPath; lip: SkPath; rim: SkPath; inside: SkPath; fx: SkRuntimeEffect }) {
  return (
    <Group>
      <Path path={inside}>
        <LinearGradient start={vec(0, 14)} end={vec(0, 54)} colors={['#3E2008', '#6A3A12', '#8A5520']} />
      </Path>
      <Path path={body}>
        <Shader source={fx} uniforms={BODY_SHADE} />
      </Path>
      <Group clip={body}>
        <Path path={lip}>
          <Shader source={fx} uniforms={LIP_SHADE} />
          <Shadow dx={0} dy={-2.5} blur={3.5} color="rgba(96,48,10,0.45)" />
        </Path>
        <Path path={rim} style="stroke" strokeWidth={2.2} strokeCap="round" color="rgba(255,226,160,0.75)">
          <BlurMask blur={0.8} style="normal" />
        </Path>
      </Group>
    </Group>
  );
}

/** One lucky number: a red ball that pops in after the slip opens, in a stagger. */
export function Chip({ clock, i, n }: { clock: SharedValue<number>; i: number; n: number }) {
  const style = useAnimatedStyle(() => {
    const v = chipAt(clock.get(), i);
    return { opacity: Math.min(1, v * 1.4), transform: [{ translateY: (1 - v) * 10 }, { scale: 0.9 + 0.1 * v }] };
  });
  return (
    <Animated.View style={[styles.chip, style]}>
      <Text style={styles.chipText}>{n}</Text>
    </Animated.View>
  );
}

const MODAL = ['#FCF6EA', '#F7EDDA', '#F0E2C8'];

/**
 * Everything static behind the cookie: the dimmed order screen (warm dark, soft lantern lights,
 * a vignette), the modal's paper card and the porcelain plate. `plate.cy` is the plate's centre.
 */
export function Backdrop({
  width: W,
  height: H,
  modal,
  plate,
  grain,
}: {
  width: number;
  height: number;
  modal: { x: number; y: number; w: number; h: number };
  plate: { cx: number; cy: number; rx: number };
  grain: SkRuntimeEffect;
}) {
  const lanterns = useMemo(
    () => Array.from({ length: 7 }, (_, i) => ({ x: W * hash(i * 3.3), y: 20 + H * 0.9 * hash(i * 7.1), r: 26 + 40 * hash(i * 1.9), warm: i % 2 === 0 })),
    [W, H],
  );
  const { x: mx, y: mTop, w: mw, h: mH } = modal;
  const { cx, cy, rx } = plate;
  return (
    <Group>
      <Fill>
        <LinearGradient start={vec(0, 0)} end={vec(W * 0.3, H)} colors={['#3A1A13', '#24110D', '#160B09']} />
      </Fill>
      {lanterns.map((l, i) => (
        <Circle key={i} cx={l.x} cy={l.y} r={l.r} color={l.warm ? 'rgba(232,150,70,0.16)' : 'rgba(196,60,44,0.16)'}>
          <BlurMask blur={22} style="normal" />
        </Circle>
      ))}
      <Fill>
        <RadialGradient c={vec(W / 2, H * 0.45)} r={Math.max(W, H) * 0.75} colors={['rgba(0,0,0,0)', 'rgba(10,4,3,0.55)']} />
      </Fill>
      <RoundedRect x={mx} y={mTop} width={mw} height={mH} r={30}>
        <LinearGradient start={vec(0, mTop)} end={vec(W * 0.2, mTop + mH)} colors={MODAL} />
        <Shadow dx={0} dy={24} blur={44} color="rgba(0,0,0,0.42)" />
        <Shadow dx={0} dy={2} blur={4} color="rgba(0,0,0,0.3)" />
      </RoundedRect>
      <RoundedRect x={mx} y={mTop} width={mw} height={mH} r={30} blendMode="softLight">
        <Shader source={grain} uniforms={{ amount: 0.45 }} />
      </RoundedRect>
      <RoundedRect x={mx + 0.5} y={mTop + 0.5} width={mw - 1} height={mH - 1} r={29.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.7)" />
      {/* Porcelain plate with a red rim line. */}
      <Oval x={cx - rx} y={cy - 32} width={rx * 2} height={64}>
        <RadialGradient c={vec(cx, cy - 6)} r={rx} colors={['#FFFDF8', '#F2ECE2', '#DCD3C5']} />
        <Shadow dx={0} dy={10} blur={18} color="rgba(70,40,20,0.22)" />
        <Shadow dx={0} dy={1.5} blur={2} color="rgba(70,40,20,0.3)" />
      </Oval>
      <Oval x={cx - rx + 10} y={cy - 26} width={rx * 2 - 20} height={52} style="stroke" strokeWidth={1.6} color="rgba(168,50,42,0.55)" />
      <Oval x={cx - rx * 0.66} y={cy - 18} width={rx * 1.32} height={34} color="rgba(120,90,60,0.07)" />
    </Group>
  );
}

const styles = StyleSheet.create({
  chip: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: LACQUER,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: 'inset 0 1px 1px rgba(255,220,200,0.45), 0 1px 2px rgba(80,20,10,0.35), 0 6px 12px rgba(80,20,10,0.18)',
  },
  chipText: { color: '#FFF1E2', fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] },
});
