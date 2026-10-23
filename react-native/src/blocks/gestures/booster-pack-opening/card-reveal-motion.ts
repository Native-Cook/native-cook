import { clamp, interpolateKeyframes } from './model';
import type { CardRarity } from './card-rarity';

export const CARD_COUNT = 5;
// At ~30° yaw, the reference video exposes ~2.5% of the projected card width
// between successive edges (about 19 px on a 755 px face).
export const CARD_STACK_DEPTH = 0.048;
export const CARD_EXIT_MS = 180;
export const RARE_REVEAL_MS = 2600;
export const RARE_INTERACTION_MS = 1650;
// Marks drawn under each card once it has settled: small ones for the first
// three cards, large ones for the two that turn.
export const CARD_MARKS = [1, 1, 3, 2, 2] as const;
export const CARD_HOLO = [0, 0, 0.3, 1, 1] as const;
export const PACK_CARD_RARITIES = [
  'common', 'common', 'rare', 'epic', 'epic',
] as const satisfies readonly CardRarity[];

export type CardPose = Readonly<{
  x: number;
  y: number;
  yaw: number;
  pitch: number;
  roll: number;
  scale: number;
}>;

/** The cards with full holo strength, the pack's two epic ones: they arrive with a turn. */
export function isRareCard(index: number) {
  'worklet';
  return CARD_HOLO[index] === 1;
}

/** Slow movement inspects the stack. Distance OR an intentional flick advances. */
export function shouldAdvanceCard(dx: number, dy: number, vx: number, cardWidth: number) {
  'worklet';
  if (Math.abs(dx) < Math.abs(dy) * 1.25) return false;
  const flick = Math.abs(dx) > cardWidth * 0.065 && Math.abs(vx) > 650 && dx * vx > 0;
  return Math.abs(dx) >= cardWidth * 0.38 || flick;
}

export function dragCardTilt(dx: number, dy: number, cardWidth: number) {
  'worklet';
  // The reference exposes ~30° of yaw and much less pitch. Soft resistance
  // lets the finger continue moving without flattening the card against an edge.
  return {
    yaw: 0.56 * Math.tanh(dx / (cardWidth * 0.24)),
    pitch: -0.16 * Math.tanh(dy / (cardWidth * 0.38)),
  };
}

// Measured at 30 fps over 1.66 s of the reference video. This is the
// automatic rare-card turn AFTER the previous card slides away, not a drag.
const TURN_TIMES = [0, 500, 566, 633, 733, 833, 933, 1033, 1200, 1450, 1650];
const TURN_ANGLES = [0, 0, 0.06, 0.52, 2.2, 3.75, 5.05, 5.72, 6.0, 6.2, Math.PI * 2];

/** Monotone Hermite interpolation keeps angular velocity continuous at samples. */
function turnAtTime(ms: number) {
  'worklet';
  if (ms <= TURN_TIMES[0]) return 0;
  if (ms >= RARE_INTERACTION_MS) return Math.PI * 2;
  const slope = (i: number) => {
    if (i === 0 || i === TURN_TIMES.length - 1) return 0;
    const a = (TURN_ANGLES[i] - TURN_ANGLES[i - 1]) / (TURN_TIMES[i] - TURN_TIMES[i - 1]);
    const b = (TURN_ANGLES[i + 1] - TURN_ANGLES[i]) / (TURN_TIMES[i + 1] - TURN_TIMES[i]);
    return a * b <= 0 ? 0 : 2 * a * b / (a + b);
  };
  for (let i = 1; i < TURN_TIMES.length; i++) {
    if (ms > TURN_TIMES[i]) continue;
    const duration = TURN_TIMES[i] - TURN_TIMES[i - 1];
    const t = (ms - TURN_TIMES[i - 1]) / duration;
    const t2 = t * t;
    const t3 = t2 * t;
    return (2 * t3 - 3 * t2 + 1) * TURN_ANGLES[i - 1] +
      (t3 - 2 * t2 + t) * duration * slope(i - 1) +
      (-2 * t3 + 3 * t2) * TURN_ANGLES[i] +
      (t3 - t2) * duration * slope(i);
  }
  return Math.PI * 2;
}

export function rareCardPose(ms: number, reducedMotion = false) {
  'worklet';
  const yaw = reducedMotion ? 0 : turnAtTime(ms);
  const light = reducedMotion ? 0 : interpolateKeyframes(ms,
    [0, 250, 600, 1800, 2200, 2600], [0, 0, 1, 1, 0.65, 0]);
  const flash = reducedMotion ? 0 : interpolateKeyframes(ms,
    [0, 1120, 1250, 1340, 1560, 1950, 2330], [0, 0, 0.65, 1, 0.48, 0.35, 0]);
  return {
    yaw,
    pitch: reducedMotion ? 0 : Math.sin(yaw) * 0.065,
    roll: reducedMotion ? 0 : Math.sin(yaw) * 0.018,
    scale: reducedMotion ? 1 : 1 + Math.sin(clamp((ms - 550) / 1100, 0, 1) * Math.PI) * 0.045,
    light,
    flash,
    sweep: reducedMotion ? 0 : clamp((ms - 1120) / 450, 0, 1),
  };
}

export const CARD_ARRIVAL_MS = 520;

/** A plain card arriving on top of the stack: a small pop and one glint. */
export function commonCardArrival(ms: number, reducedMotion = false) {
  'worklet';
  if (reducedMotion || ms >= CARD_ARRIVAL_MS) return { pop: 0, sweep: 0, flash: 0 };
  const t = clamp(ms / 360, 0, 1);
  return {
    // Peaks early, then settles without crossing below the resting scale.
    pop: Math.sin(t * Math.PI) * (1 - t * 0.55),
    sweep: clamp((ms - 60) / 420, 0, 1),
    flash: interpolateKeyframes(ms, [0, 60, 200, 380, CARD_ARRIVAL_MS], [0, 0, 0.85, 0.6, 0]),
  };
}

/** True perspective projection of a rigid card, in local centered coordinates.
 * A homography avoids affine triangle seams and works on both Skia renderers.
 * Reverse the back's U direction so its lettering is never mirrored.
 */
export function cardProjection(pose: CardPose, cardWidth: number, back = false): number[] {
  'worklet';
  const cy = Math.cos(pose.yaw);
  const sy = Math.sin(pose.yaw);
  const cx = Math.cos(pose.pitch);
  const sx = Math.sin(pose.pitch);
  const cz = Math.cos(pose.roll);
  const sz = Math.sin(pose.roll);
  const flip = back ? -1 : 1;
  const scale = pose.scale;
  const r00 = cy * cz * scale * flip;
  const r01 = (sx * sy * cz - cx * sz) * scale;
  const r10 = cy * sz * scale * flip;
  const r11 = (sx * sy * sz + cx * cz) * scale;
  const p0 = sy * scale * flip / (cardWidth * 2.7);
  const p1 = -cy * sx * scale / (cardWidth * 2.7);
  return [
    r00 + pose.x * p0, r01 + pose.x * p1, pose.x,
    r10 + pose.y * p0, r11 + pose.y * p1, pose.y,
    p0, p1, 1,
  ];
}
