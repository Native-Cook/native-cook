// App Icon Atelier — the shader's uniforms from the block's state, as one pure worklet so the screen,
// the loop and the offline stills all draw the same thing.

import { clamp01, FRONT_END, meniscus, REST, SLOT_POSE, sweepProgress } from './aia-motion';
import { CARD_H, GLASSES, type Layout, SELF, SLOT_S } from './aia-theme';

export type GlassState = {
  a: number;
  b: number;
  dir: number;
  front: number;
  lean: number;
  bob: number;
  press: number;
  peek: number;
  land: number;
  phase: number; // wobble phase of the front, rad
  applied: number; // the glass of the icon in use
  pop: number; // the slot's pop when an icon is set
};

export const CARD_R = 28;

function smooth(a: number, b: number, x: number) {
  'worklet';
  const k = clamp01((x - a) / (b - a));
  return k * k * (3 - 2 * k);
}

/** Where the flying pebble is at a peek value (a quadratic arc that swings out to the right). */
export function flight(L: Layout, peek: number) {
  'worklet';
  const x0 = L.heroX;
  const y0 = L.heroY;
  const x2 = L.appX[SELF];
  const y2 = L.appsY;
  const x1 = x0 + L.w * 0.3;
  const y1 = (y0 + y2) * 0.5;
  const t = peek;
  const u = 1 - t;
  return { x: u * u * x0 + 2 * u * t * x1 + t * t * x2, y: u * u * y0 + 2 * u * t * y1 + t * t * y2 };
}

export function makeUniforms(L: Layout, s: GlassState, pixelRatio: number) {
  'worklet';
  const A = GLASSES[s.a];
  const B = GLASSES[s.b];
  const S = GLASSES[s.applied];
  const pk = clamp01(s.peek);
  const at = flight(L, s.peek);
  const size = (L.heroS + (SLOT_S - L.heroS) * s.peek) * (1 - 0.04 * s.press);
  const cx = at.x;
  const cy = at.y - s.bob * size;
  const pose = [
    REST.yaw + s.lean + (SLOT_POSE.yaw - REST.yaw - s.lean) * pk,
    REST.pitch + (SLOT_POSE.pitch - REST.pitch) * pk,
    REST.roll + (SLOT_POSE.roll - REST.roll) * pk,
  ];
  const k = sweepProgress(s.front, s.dir);
  const tint = [A.tint[0] + (B.tint[0] - A.tint[0]) * k, A.tint[1] + (B.tint[1] - A.tint[1]) * k, A.tint[2] + (B.tint[2] - A.tint[2]) * k];
  // the shadow stays on the floor while the pebble floats; it follows the pebble when it flies
  const floorK = 1.32 + (1.14 - 1.32) * pk;
  return {
    uRes: [L.w, L.h],
    uCard: [L.cardX, L.cardY, L.cardW, CARD_H],
    uCardR: CARD_R,
    uC: [cx, cy],
    uS: size,
    uPose: pose,
    uSquash: [1 + s.land, 1 - s.land],
    uA0: [A.tint[0], A.tint[1], A.tint[2], A.dens],
    uA1: [A.frost, A.film, A.glow, 0],
    uB0: [B.tint[0], B.tint[1], B.tint[2], B.dens],
    uB1: [B.frost, B.film, B.glow, 0],
    uFront: s.front,
    uDir: s.dir,
    uMen: s.a === s.b ? meniscus(s.front) * 0.6 : meniscus(s.front),
    uPhase: s.phase,
    uFloor: [cx, at.y + size * floorK],
    uShadow: (1 - 0.3 * pk) * (1 - 3 * s.bob),
    uShadowTint: tint,
    uSC: [L.appX[SELF], L.appsY],
    uSS: SLOT_S,
    uSOn: 1 - smooth(0.7, 0.95, s.peek),
    uSPop: s.pop,
    uS0: [S.tint[0], S.tint[1], S.tint[2], S.dens],
    uS1: [S.frost, S.film, S.glow, 0],
    uPx: 1 / pixelRatio,
  };
}

export const REST_FRONT = FRONT_END;
