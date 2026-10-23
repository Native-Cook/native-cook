/**
 * The split bar's geometry, as worklets: where each piece is for a (spring-driven, fractional) count. The pieces,
 * the HUD and the flying avatars all read it, so they agree to the point.
 *
 * A bar of BAR pt breaks into `count` equal pieces with `gap` pt seams. While the count springs from n to n+1 the
 * first n pieces narrow and the new one grows out of the bar's far end; the spring's overshoot past the target is
 * not drawn as a sliver of another piece but opens the seams a little wider (the snap), then they settle.
 */
import { SPLIT } from './motion';
import { LAYOUT } from './theme';

export const BAR = 370; // the ledger's full width: the shares hang on its edges like the rows
export const MONO = 28;
const COMPACT_MONO = 24;

export type PieceFrame = { x: number; w: number; vis: number; last: boolean };

/** The count the bar is drawn with (overshoot clamped to the target) and the extra seam the overshoot opens. */
export function structure(count: number, target: number, dir: number) {
  'worklet';
  let cs = count;
  let snap = 0;
  if (dir > 0 && count > target) {
    snap = count - target;
    cs = target;
  } else if (dir < 0 && count < target) {
    snap = target - count;
    cs = target;
  }
  return { cs: Math.max(cs, 1), snap };
}

/** Piece k's left edge (from the bar's left), width, visibility (0–1, the new piece's growth) and whether it ends the bar. */
export function pieceFrame(k: number, cs: number, gap: number, trail: number): PieceFrame {
  'worklet';
  const n0 = Math.floor(cs + 1e-4);
  const f = cs - n0 < 1e-4 ? 0 : cs - n0;
  const u = (BAR - (cs - 1) * gap) / cs;
  const lastIndex = f > 0 ? n0 : n0 - 1;
  const lean = cs > 1 ? (k / (cs - 1)) * trail : 0; // far pieces trail toward the finger
  if (k < n0) return { x: k * (u + gap) + Math.min(lean, trail), w: u, vis: 1, last: k === lastIndex };
  if (k === n0 && f > 0) return { x: n0 * (u + gap) - gap + f * gap + trail, w: f * u, vis: f, last: true };
  return { x: BAR, w: 0, vis: 0, last: false };
}

/** 0 for the wide layout (avatar, name, amount side by side, up to 3 people), 1 for the compact one (4–5). */
export function compactness(cs: number) {
  'worklet';
  return Math.min(Math.max(cs - 3, 0), 1);
}

/** The monogram's centre inside its piece, and its scale against MONO. */
export function avatarIn(w: number, compact: number) {
  'worklet';
  const wide = { x: 10 + MONO / 2, y: (LAYOUT.bar - MONO) / 2 + MONO / 2 };
  const tight = { x: w / 2, y: 8 + COMPACT_MONO / 2 };
  return {
    x: wide.x + (tight.x - wide.x) * compact,
    y: wide.y + (tight.y - wide.y) * compact,
    s: 1 + (COMPACT_MONO / MONO - 1) * compact,
  };
}

/** The seam between pieces while pulling: rest gap, stretched by the pull past the current detent. */
export function seam(pull: number, target: number) {
  'worklet';
  if (target < 2) return SPLIT.gapRest;
  const at = SPLIT.detentStart + (target - 2) * SPLIT.detentStep;
  return Math.min(SPLIT.gapRest + SPLIT.gapStretch * Math.max(pull - at, 0), SPLIT.gapMax);
}
