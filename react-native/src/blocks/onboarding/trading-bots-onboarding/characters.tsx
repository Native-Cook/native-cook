import {
  BlurMask,
  Canvas,
  Circle,
  ColorMatrix,
  Fill,
  Group,
  Image,
  LinearGradient,
  Paint,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { View } from 'react-native';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import { ORB } from './shaders';
import { white } from './theme';

export type CharacterKind = 'orb' | 'green' | 'purple' | 'red' | 'avatar';

const IMAGES = {
  green: require('../../../../assets/trading-bots-onboarding/bot-green.png'),
  purple: require('../../../../assets/trading-bots-onboarding/bot-purple.png'),
  red: require('../../../../assets/trading-bots-onboarding/bot-red.png'),
  avatar: require('../../../../assets/trading-bots-onboarding/bot-avatar.png'),
};

export const WALLET_ICON = require('../../../../assets/trading-bots-onboarding/wallet-icon.png');
export const TOGGLE_TILE = require('../../../../assets/trading-bots-onboarding/toggle-tile.png');

/** Eye pills per bot, as fractions of the bot's size (`TradingBotsCharacter.eyes`). */
const EYES: Record<CharacterKind, { gap: number; left: [number, number, number]; right: [number, number, number]; offset: [number, number]; rightDrop?: number }> = {
  orb: { gap: 0.04, left: [0.135, 0.075, 4], right: [0.14, 0.075, -3], offset: [-0.065, -0.06] },
  purple: { gap: 0.06, left: [0.19, 0.085, 0], right: [0.19, 0.085, 0], offset: [0, 0] },
  green: { gap: 0.08, left: [0.085, 0.115, -4], right: [0.085, 0.115, 4], offset: [-0.025, -0.01] },
  red: { gap: 0.07, left: [0.1, 0.13, -8], right: [0.1, 0.13, 8], offset: [-0.02, 0.02], rightDrop: 0.03 },
  avatar: { gap: 0.09, left: [0.11, 0.15, 0], right: [0.11, 0.15, 0], offset: [-0.04, 0] },
};

type Look = { x: number; y: number };

type EyeProps = { size: number; cx: number; cy: number; width: number; height: number; tilt: number; look: SharedValue<Look>; blink: SharedValue<number> };

/** A glossy white capsule with a white rim, a soft glow and a faint drop shadow. */
function Eye({ size, cx, cy, width, height, tilt, look, blink }: EyeProps) {
  const rim = Math.max(0.8, size * 0.011);
  const rect = useDerivedValue(() => {
    const h = Math.max(1, height * (1 - 0.88 * blink.get()));
    const x = cx + look.get().x * size - width / 2;
    const y = cy + look.get().y * size - h / 2;
    const r = Math.min(width, h) / 2;
    return Skia.RRectXY(Skia.XYWHRect(x, y, width, h), r, r);
  });
  const inner = useDerivedValue(() => {
    const h = Math.max(1, height * (1 - 0.88 * blink.get()));
    const x = cx + look.get().x * size - width / 2 + rim / 2;
    const y = cy + look.get().y * size - h / 2 + rim / 2;
    const r = Math.max(0, Math.min(width, h) / 2 - rim / 2);
    return Skia.RRectXY(Skia.XYWHRect(x, y, width - rim, Math.max(0.5, h - rim)), r, r);
  });
  const top = useDerivedValue(() => vec(0, cy + look.get().y * size - height / 2));
  const bottom = useDerivedValue(() => vec(0, cy + look.get().y * size + height / 2));
  const origin = useDerivedValue(() => vec(cx + look.get().x * size, cy + look.get().y * size));
  return (
    <Group transform={[{ rotate: (tilt * Math.PI) / 180 }]} origin={origin}>
      <RoundedRect rect={rect}>
        <LinearGradient start={top} end={bottom} colors={['#ffffff', '#ededed', '#adadad']} />
        <Shadow dx={0} dy={0} blur={(size * 0.012) / 2} color={white(0.45)} />
        <Shadow dx={0} dy={size * 0.006} blur={(size * 0.01) / 2} color="rgba(0, 0, 0, 0.35)" />
      </RoundedRect>
      <RoundedRect rect={inner} color="#fff" style="stroke" strokeWidth={rim} />
    </Group>
  );
}

function Eyes({ kind, size, look, blink }: { kind: CharacterKind; size: number; look: SharedValue<Look>; blink: SharedValue<number> }) {
  const eyes = EYES[kind];
  const lw = eyes.left[0] * size;
  const rw = eyes.right[0] * size;
  const total = lw + eyes.gap * size + rw;
  const cx = size / 2 + eyes.offset[0] * size;
  const cy = size / 2 + eyes.offset[1] * size;
  return (
    <>
      <Eye size={size} cx={cx - total / 2 + lw / 2} cy={cy} width={lw} height={eyes.left[1] * size} tilt={eyes.left[2]} look={look} blink={blink} />
      <Eye size={size} cx={cx + total / 2 - rw / 2} cy={cy + (eyes.rightDrop ?? 0) * size} width={rw} height={eyes.right[1] * size} tilt={eyes.right[2]} look={look} blink={blink} />
    </>
  );
}

/** Saturation and brightness as a colour matrix (Rec. 709 weights, like SwiftUI's `.saturation`). */
function colorMatrix(saturation: number, brightness: number) {
  const s = saturation;
  const [lr, lg, lb] = [0.2126, 0.7152, 0.0722];
  return [
    lr * (1 - s) + s, lg * (1 - s), lb * (1 - s), 0, brightness,
    lr * (1 - s), lg * (1 - s) + s, lb * (1 - s), 0, brightness,
    lr * (1 - s), lg * (1 - s), lb * (1 - s) + s, 0, brightness,
    0, 0, 0, 1, 0,
  ];
}

type CharacterProps = {
  kind: CharacterKind;
  size: number;
  phase?: SharedValue<number>;
  look?: SharedValue<Look>;
  blink?: SharedValue<number>;
  saturation?: number;
  brightness?: number;
  /** Milky light over the upper left of the purple gummy (Pro screen). */
  frost?: number;
};

/**
 * The bots. The selector's orb is a live fragment shader; the other bodies are rendered
 * images. Eyes stay vector so they can look around and blink.
 */
export function Character({ kind, size, phase, look, blink, saturation = 1, brightness = 0, frost = 0 }: CharacterProps) {
  const still = useSharedValue<Look>({ x: 0, y: 0 });
  const open = useSharedValue(0);
  const zero = useSharedValue(0);
  const image = useImage(kind === 'orb' ? null : IMAGES[kind]);
  const turn = phase ?? zero;
  const uniforms = useDerivedValue(() => ({ size: [size, size], phase: turn.get() }));
  const graded = saturation !== 1 || brightness !== 0;
  return (
    <Canvas style={{ width: size, height: size }} pointerEvents="none">
      <Group layer={graded ? <Paint><ColorMatrix matrix={colorMatrix(saturation, brightness)} /></Paint> : undefined}>
        {kind === 'orb' ? (
          <Fill>
            <Shader source={ORB} uniforms={uniforms} />
          </Fill>
        ) : (
          image && <Image image={image} x={0} y={0} width={size} height={size} fit="contain" />
        )}
        {kind === 'green' && (
          <Circle cx={size / 2} cy={size / 2} r={size / 2 - size * 0.045} blendMode="screen">
            <LinearGradient start={vec(size * 0.045, size * 0.045)} end={vec(size * 0.62, size * 0.62)} colors={[white(0.6), white(0.18), white(0)]} />
          </Circle>
        )}
        <Eyes kind={kind} size={size} look={look ?? still} blink={blink ?? open} />
      </Group>
      {frost > 0 && (
        <RoundedRect x={size * 0.05} y={size * 0.05} width={size * 0.9} height={size * 0.9} r={size * 0.3} blendMode="screen">
          <LinearGradient start={vec(size * 0.05, size * 0.05)} end={vec(size * 0.05 + size * 0.9 * 0.7, size * 0.05 + size * 0.9 * 0.75)} colors={[white(frost), white(frost * 0.25), white(0)]} />
        </RoundedRect>
      )}
    </Canvas>
  );
}

/** Seconds since mount on the UI thread; stays at 0 under reduced motion. */
export function useClock(reduced: boolean) {
  const time = useSharedValue(0);
  const frame = useFrameCallback((info) => {
    time.set(time.get() + (info.timeSincePreviousFrame ?? 0) / 1000);
  }, false);
  useEffect(() => {
    time.set(0);
    frame.setActive(!reduced);
    return () => frame.setActive(false);
  }, [frame, reduced, time]);
  return time;
}

/** The selector's orb with its idle life: the shell's colour turns, the eyes wander and blink. */
export function LivingOrb({ reduced }: { reduced: boolean }) {
  const t = useClock(reduced);
  const phase = useDerivedValue(() => (0.7 - t.get() * 0.45) % (2 * Math.PI));
  const look = useDerivedValue(() => ({ x: 0.02 * Math.sin(t.get() * 0.9), y: 0.012 * Math.sin(t.get() * 0.7 + 1) }));
  const blink = useDerivedValue(() => {
    const cycle = (t.get() + 2.3) % 3.4;
    return cycle < 0.18 ? Math.sin((cycle / 0.18) * Math.PI) : 0;
  });
  return (
    <View style={{ width: 150, height: 150, alignItems: 'center', justifyContent: 'center' }}>
      <View style={{ position: 'absolute', width: 143, height: 143, borderRadius: 71.5, backgroundColor: 'rgba(0, 0, 0, 0.35)' }} />
      <Canvas style={{ position: 'absolute', width: 150, height: 150 }} pointerEvents="none">
        <Circle cx={75} cy={75} r={70} color={white(0.2)} style="stroke" strokeWidth={2}>
          <BlurMask blur={1.25} style="normal" />
        </Circle>
      </Canvas>
      <Character kind="orb" size={136} phase={phase} look={look} blink={blink} />
    </View>
  );
}
