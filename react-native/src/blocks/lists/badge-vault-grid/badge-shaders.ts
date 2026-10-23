/**
 * Atlas: 3840 × 1536, neutral RGB (R = G = B), five columns × four rows.
 * Each drawing occupies two adjacent 384² squares: wire distance, then pool ID.
 * Local xy spans [-1.2, 1.2], y up. Distance = grey × 0.16 object units.
 * Pool IDs are greys 32 / 96 / 160 / 224 for A / B / C / satin silver.
 * Tiles 0–17 follow the contract; tile 18 is the debossed hexagon/keyhole mark.
 * Separate greyscale planes survive iOS colour management without channel bleed.
 */
import { Skia } from '@shopify/react-native-skia';

import { FLOOD_SKSL, PILL_SKSL } from './badge-sksl-atmosphere';
import { BADGE_SKSL } from './badge-sksl-object';
import type { BadgePose, FloodPose, PillPose, Rgb } from './vault-contract';

export type UniformBag = Record<string, number | number[]>;

export const BADGE_ATLAS = require('./assets/badge-atlas.png');

function compile(source: string, name: string) {
  const effect = Skia.RuntimeEffect.Make(source);
  if (!effect) throw new Error(`Badge Vault Grid: the ${name} shader did not compile.`);
  return effect;
}

/** One child: the atlas, sampled linearly in image pixels with fit="none". */
export const badgeEffect = compile(BADGE_SKSL, 'badge');
export const floodEffect = compile(FLOOD_SKSL, 'flood');
export const pillEffect = compile(PILL_SKSL, 'pill');

export function badgeUniforms(tile: number, a: Rgb, b: Rgb, c: Rgb, pose: BadgePose): UniformBag {
  'worklet';
  // Trigonometry belongs once per badge, not once per fragment.
  const sx = Math.sin(-pose.tiltX);
  const cx = Math.cos(-pose.tiltX);
  const sy = Math.sin(pose.spin + pose.tiltY);
  const cy = Math.cos(pose.spin + pose.tiltY);
  const sz = Math.sin(-pose.roll);
  const cz = Math.cos(-pose.roll);
  return {
    uCentre: [pose.cx, pose.cy], uR: pose.r,
    uRot: [cz * cy, sz * cy, -sy,
      cz * sy * sx - sz * cx, sz * sy * sx + cz * cx, cy * sx,
      cz * sy * cx + sz * sx, sz * sy * cx - cz * sx, cy * cx],
    uA: [a[0], a[1], a[2]], uB: [b[0], b[1], b[2]], uC: [c[0], c[1], c[2]],
    uTile: tile, uEnv: [pose.envX, pose.envY], uSheen: pose.sheen,
    uFill: pose.fill, uWave: pose.wave, uTime: pose.time,
    uLift: pose.lift, uPresent: pose.present, uSocket: pose.socket,
  };
}

export function floodUniforms(colour: Rgb, pose: FloodPose): UniformBag {
  'worklet';
  return {
    uSize: [pose.w, pose.h], uOrigin: [pose.ox, pose.oy],
    uCentre: [pose.cx, pose.cy], uR: pose.r,
    uColour: [colour[0], colour[1], colour[2]], uSpread: pose.spread,
    uPower: pose.power, uRays: pose.rays, uSince: pose.since, uTime: pose.time,
  };
}

export function pillUniforms(pose: PillPose): UniformBag {
  'worklet';
  return { uSize: [pose.w, pose.h], uPress: pose.press, uSheen: pose.sheen, uDark: pose.dark,
    uTint: [pose.tint[0], pose.tint[1], pose.tint[2]] };
}
