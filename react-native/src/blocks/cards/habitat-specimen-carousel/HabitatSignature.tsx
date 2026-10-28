import { Canvas, Fill, Shader, type Uniforms } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { creaturePose, type CreatureMotion } from './HabitatCreature';
import { EXCHANGE_DRIFT, FX_S, wrapPosition } from './HabitatMotion';
import { LEAVES, leafUniforms } from './HabitatParticles';
import { bodyPoint, RIGS } from './HabitatRig';
import { SIGNATURE_EFFECT } from './HabitatShaders';

const ART = 512;
/**
 * Anatomy in source-art pixels: where each move leaves the body and where it aims.
 * Charizard: the base of the drawn tail flame (embers leave its tip). Blastoise: the centre of each
 * cannon's mouth, aimed down its barrel. Bulbasaur: the middle of the bulb's crown, and the bulb.
 */
const ANCHORS = [
  { a: [452, 402], b: [452, 402], aimA: [0, -1], aimB: [0, -1] },
  { a: [99, 154], b: [409, 196], aimA: [-0.67, -0.74], aimB: [1, -0.06] },
  { a: [377, 152], b: [352, 215], aimA: [0, -1], aimB: [0, -1] },
] as const;
const NO_LEAVES = new Array<number>(LEAVES * 4).fill(0);

/**
 * A signature move and everything it throws. `layer` 1 is in front of the creatures; layer 0 is
 * behind them, for the leaves that fall on the far side of Bulbasaur. Transparent and still
 * outside a move's effect clock.
 */
export const HabitatSignature = memo(function HabitatSignature({ motion, frontPhase, layer, gpuParticles, width, height, size, left, top, heroTop, screenHeight, scale, pad }: {
  motion: CreatureMotion; frontPhase: SharedValue<number>; layer: 0 | 1;
  /** True while the WebGPU particle world draws what the move throws; this layer then draws only what stays attached. */
  gpuParticles: SharedValue<boolean>;
  /** The effect layer spans the screen above the hero, so plumes and beams are never clipped by it. */
  width: number; height: number;
  /** The creature's art square (left on screen, top inside the hero), the hero's top and the screen. */
  size: number; left: number; top: number; heroTop: number; screenHeight: number; scale: number; pad: number;
}) {
  const { fx, powerIndex, position } = motion;
  const unit = size / 344;
  const drift = EXCHANGE_DRIFT * width;
  const uniforms = useDerivedValue<Uniforms>(() => {
    const secs = fx.get();
    const index = powerIndex.get();
    const idle = {
      resolution: [width, height], secs: 0, element: index, layer, anchorA: [0, 0], anchorB: [0, 0], aimA: [0, -1], aimB: [0, -1],
      emitA: [0, 0], emitB: [0, 0], floorY: 0, unit, fadeTop: heroTop * 0.55, screen: [width, screenHeight],
      front: 0, phase: 0, role: -1, incoming: 0, particles: gpuParticles.get() ? 0 : 1, leaf: NO_LEAVES, leafState: NO_LEAVES,
    };
    if (!(secs > 0 && secs < FX_S)) return idle;
    const anchor = ANCHORS[index] ?? ANCHORS[0];
    const { exchange, pose } = creaturePose(index, motion);
    const body = { index, size, pad, left, top, heroTop, width, screenHeight, scale, exchange, driftPx: drift, pose };
    // Attached effects follow the posed body; their aim turns with it.
    const a = bodyPoint(body, anchor.a[0], anchor.a[1]);
    const b = bodyPoint(body, anchor.b[0], anchor.b[1]);
    const ta = bodyPoint(body, anchor.a[0] + anchor.aimA[0] * 24, anchor.a[1] + anchor.aimA[1] * 24);
    const tb = bodyPoint(body, anchor.b[0] + anchor.aimB[0] * 24, anchor.b[1] + anchor.aimB[1] * 24);
    // Particles are born at the resting body and travel with its world.
    const k = size / ART;
    const shift = exchange.drift * drift;
    const emitA = [left + anchor.a[0] * k + shift, heroTop + top + anchor.a[1] * k];
    const emitB = [left + anchor.b[0] * k + shift, heroTop + top + anchor.b[1] * k];
    const floorY = heroTop + top + RIGS[index].feet * k;
    const leaves = index === 2 && !gpuParticles.get() ? leafUniforms(secs, emitA[0], emitA[1], floorY, unit) : { leaf: NO_LEAVES, leafState: NO_LEAVES };
    return {
      ...idle, secs, anchorA: a, anchorB: b, aimA: [ta[0] - a[0], ta[1] - a[1]], aimB: [tb[0] - b[0], tb[1] - b[1]],
      emitA, emitB, floorY, front: exchange.f, phase: frontPhase.get(), role: exchange.role,
      incoming: (Math.floor(wrapPosition(position.get())) + 1) % 3, leaf: leaves.leaf, leafState: leaves.leafState,
    };
  });
  const visibility = useAnimatedStyle(() => {
    const secs = fx.get();
    return { opacity: secs > 0 && secs < FX_S && (layer === 1 || (powerIndex.get() === 2 && !gpuParticles.get())) ? 1 : 0 };
  });
  if (!SIGNATURE_EFFECT) return null;
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, layer === 1 && styles.front, visibility]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Canvas style={{ width, height }} pointerEvents="none">
      <Fill><Shader source={SIGNATURE_EFFECT} uniforms={uniforms} /></Fill>
    </Canvas>
  </Animated.View>;
});

// Above every creature (their z-index follows carousel depth, up to 6).
const styles = StyleSheet.create({ front: { zIndex: 20 } });
