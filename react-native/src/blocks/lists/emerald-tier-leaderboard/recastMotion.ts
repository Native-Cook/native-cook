/** Canonical Rank Recast geometry. All coordinates are local to the393×335 arena. */
export const RECAST_STAGE_WIDTH = 393;
export const RECAST_STAGE_HEIGHT = 335;
export const RECAST_DURATION_MS = 1200;
/** The crown lands on the new leader here; the haptic and the plinth response follow it. */
export const RECAST_SEAT_PHASE = 0.88;
/** Arena y of the promotion boundary between the podium (top 3) and row 4. */
export const PROMOTION_LINE_Y = 281.5;
export type RecastActorId = 'ari' | 'milo' | 'juno' | 'theo';
export type RecastSurfaceId = RecastActorId;
/** Back-to-front order. Theo's source row is the foreground object throughout the recast. */
export const RECAST_SURFACE_ORDER: readonly RecastSurfaceId[] = ['juno', 'milo', 'ari', 'theo'];
export const RECAST_ACTOR_ORDER = RECAST_SURFACE_ORDER;

export type RecastSurface = {
  x: number; y: number; width: number; height: number; radius: number;
  /** The top plane extends UPWARD from y to y-depth. */
  depth: number; rank: number; rankOpacity: number; glow: number;
};
export type RecastActor = {
  cx: number; cy: number; size: number;
  /** Leading edge of a110pt name box; center-aligned on podium, left-aligned in row. */
  nameX: number;
  /** Top edge of the native18pt name box. */
  nameY: number;
  /** Center of the native score box; scoreY is its21pt score box's top edge. */
  scoreX: number; scoreY: number;
  rowMix: number; labelOpacity: number; score: number; rank: number;
};
export type RecastFrame = {
  phase: number; reduced: boolean;
  actors: Record<RecastActorId, RecastActor>;
  surfaces: Record<RecastSurfaceId, RecastSurface>;
  /** Crown has the existing34×30 footprint. It never leaves the first-place axis. */
  crown: { x: number; y: number; opacity: number; rotation: number; squash: number; sparkle: number };
  /** 0→1 while every plinth numeral rolls from its old rank to its new one. */
  roll: number;
  /** Canonical68×19 XP caption, above Theo with a3pt portrait gap. */
  gain: { x: number; y: number; opacity: number };
  light: { response: number; phase: number; seat: number };
  /** Renderer aliases for the single docking reflection. */
  response: number; responsePhase: number;
};

function clamp(value: number) { 'worklet'; return Math.max(0, Math.min(1, value)); }
function mix(from: number, to: number, p: number) { 'worklet'; return from + (to - from) * p; }
function segment(t: number, from: number, to: number) {
  'worklet';
  const p = clamp((t - from) / (to - from));
  return p * p * (3 - 2 * p);
}

/**
 * The crown stays on the throne's axis while the kings change: it pops off Ari, hovers with a
 * slow wobble while the podium rearranges, then drops onto Theo with gravity and a damped seat.
 */
function crownPose(t: number, reduced: boolean) {
  'worklet';
  if (reduced) return { x: 180, y: 14, opacity: 1, rotation: 0, squash: 0, sparkle: 0 };
  const pop = segment(t, 0.05, 0.17);
  const fall = clamp((t - 0.74) / (RECAST_SEAT_PHASE - 0.74));
  const hover = t > 0.17 && t < 0.74 ? Math.sin((t - 0.17) / 0.57 * Math.PI) : 0;
  const lifted = t < RECAST_SEAT_PHASE ? pop * (1 - fall * fall) : 0;
  // Normalised seat time, so the settle reaches the exact rest pose at the end of the event.
  const u = t >= RECAST_SEAT_PHASE ? clamp((t - RECAST_SEAT_PHASE) / (1 - RECAST_SEAT_PHASE)) : 0;
  const seat = t >= RECAST_SEAT_PHASE ? Math.exp(-u * 4) * Math.sin(u * Math.PI * 3) * (1 - u) : 0;
  return {
    x: 180,
    y: 14 - 20 * lifted - 2.5 * hover + 2.2 * seat,
    opacity: 1,
    rotation: lifted * (Math.sin(t * 19) * 7 - 4) + seat * -6,
    squash: t >= RECAST_SEAT_PHASE ? (1 - u) * (1 - u) : 0,
    sparkle: u,
  };
}

function actor(cx: number, cy: number, size: number, rowMix: number, surface: RecastSurface, opacity: number, score: number, labelRowMix = rowMix): RecastActor {
  'worklet';
  return {
    cx, cy, size, rowMix, score, rank: surface.rank, labelOpacity: opacity,
    nameX: mix(cx - 55, surface.x + 82, labelRowMix),
    nameY: mix(cy + size / 2 + 7, surface.y + 15, labelRowMix),
    scoreX: mix(cx, surface.x + surface.width - 59, labelRowMix),
    scoreY: mix(cy + size / 2 + 25, surface.y + 14, labelRowMix),
  };
}

/**
 * One deterministic and reversible event: lift0–.18, space-yield.14–.5,
 * reshape/travel.18–.8, dock.8–1. No integration, random values, or renderer objects.
 * Reduced motion samples one complete endpoint at the semantic halfway point.
 */
export function recastFrame(phase: number, reduced = false): RecastFrame {
  'worklet';
  const input = clamp(phase);
  const t = reduced ? (input >= 0.5 ? 1 : 0) : input;
  const lift = segment(t, 0, 0.18);
  const yieldSpace = segment(t, 0.14, 0.50);
  const reshape = segment(t, 0.18, 0.80);
  const junoShape = segment(t, 0.24, 0.80);
  const seated = segment(t, 0.80, 1);
  const responsePhase = clamp((t - 0.80) / 0.20);
  const response = reduced || t <= 0.80 || t >= 1 ? 0 : Math.sin(responsePhase * Math.PI) ** 2;
  const contact = reduced ? 0 : response * 1.4;
  // Each name is hidden only while its own portrait crosses another lane, then returns at its new seat.
  const podiumLabel = 1 - segment(t, 0.12, 0.18) + segment(t, 0.50, 0.58);
  const junoLabelOpacity = 1 - segment(t, 0.12, 0.18) + segment(t, 0.66, 0.74);
  const theoLabelOpacity = 1 - segment(t, 0.06, 0.12) + segment(t, 0.68, 0.76);
  // The XP gain rides beside Theo during the ascent only, clear of the crown's axis.
  const gainOpacity = reduced ? 0 : segment(t, 0.56, 0.64) * (1 - segment(t, 0.90, 0.98));
  // Plinth numerals stay with their plinths and roll to the new order as they settle; they only
  // dim while two plinths pass through each other, so two numerals never stack.
  const crossing = reduced ? 0 : Math.sin(Math.PI * segment(t, 0.14, 0.50));
  const podiumRank = 1 - 0.9 * crossing;
  const roll = reduced ? (t >= 0.5 ? 1 : 0) : segment(t, 0.46, 0.68);
  // Rank identities change only while every rank/name label is fully hidden.
  const newOrder = t >= 0.5;
  const surfaces: Record<RecastSurfaceId, RecastSurface> = {
    ari: { x: mix(135, 24, yieldSpace), y: mix(175, 202, yieldSpace), width: 123,
      height: mix(100, 73, yieldSpace), radius: 4, depth: 15,
      rank: newOrder ? 2 : 1, rankOpacity: podiumRank, glow: mix(0.76, 0.31, yieldSpace) },
    milo: { x: mix(24, 246, yieldSpace), y: mix(202, 214, yieldSpace), width: 123,
      height: mix(73, 61, yieldSpace), radius: 4, depth: 15,
      rank: newOrder ? 3 : 2, rankOpacity: podiumRank, glow: 0.31 },
    juno: { x: mix(246, 19, junoShape), y: mix(214, 288, junoShape),
      width: mix(123, 355, junoShape), height: mix(61, 47, junoShape),
      radius: mix(4, 13, junoShape), depth: mix(15, 0, junoShape),
      rank: newOrder ? 4 : 3, rankOpacity: reduced ? 1 : 1 - segment(t, 0.18, 0.32), glow: mix(0.31, 0.13, junoShape) },
    theo: { x: mix(19, 135, reshape), y: mix(288 - 18 * lift, 175, reshape) + contact,
      width: mix(355, 123, reshape), height: mix(47, 100, reshape) - contact,
      radius: mix(13, 4, reshape), depth: mix(0, 15, reshape),
      rank: newOrder ? 1 : 4, rankOpacity: reduced ? 1 : segment(t, 0.56, 0.74), glow: mix(0.13, 0.76, reshape) },
  };

  // Ari uses an upper lane; Milo vacates left downward before crossing beneath that lane.
  const ariUp = segment(t, 0.14, 0.30), ariLeft = segment(t, 0.30, 0.50);
  const ariX = mix(mix(197, 150, ariUp), 85, ariLeft);
  const ariY = mix(mix(70, 40, ariUp), 101, ariLeft);
  const ariSize = mix(70, 56, ariUp);
  const miloDown = segment(t, 0.14, 0.26), miloAcross = segment(t, 0.26, 0.43), miloUp = segment(t, 0.43, 0.50);
  const miloX = mix(85, 321, miloAcross);
  const miloY = mix(mix(101, 172, miloDown), 111, miloUp);

  // Juno takes the outer-right lane, then travels along the vacated row baseline.
  const junoDown = segment(t, 0.14, 0.28), junoRow = segment(t, 0.28, 0.43), junoAcross = segment(t, 0.43, 0.68);
  const junoX = mix(mix(321, 350, junoDown), 74, junoAcross);
  const junoY = mix(mix(111, 210, junoDown), 311.5, junoRow);
  const junoSize = mix(mix(56, 42, junoDown), 30, junoRow);

  // Theo waits below the crossing, then owns the now-clear center ascent.
  const theoCenter = segment(t, 0.34, 0.56), theoRise = segment(t, 0.56, 0.80);
  const theoX = mix(74, 197, theoCenter);
  const theoY = mix(mix(mix(311.5, 278, lift), 225, theoCenter), 70, theoRise) + contact;
  const theoSize = mix(mix(mix(30, 38, lift), 52, theoCenter), 70, theoRise);
  const actors: Record<RecastActorId, RecastActor> = {
    ari: actor(ariX, ariY, ariSize, 0, surfaces.ari, podiumLabel, 1000),
    milo: actor(miloX, miloY, 56, 0, surfaces.milo, podiumLabel, 950),
    juno: actor(junoX, junoY, junoSize, junoShape, surfaces.juno, junoLabelOpacity, 900),
    // Finish the hidden label-layout transfer before revealing Theo during the ascent.
    // Physical rowMix and all portrait/surface trajectories remain unchanged.
    // The score counts up while Theo climbs, so the gain is read as it happens.
    theo: actor(theoX, theoY, theoSize, 1 - reshape, surfaces.theo, theoLabelOpacity, 850 + Math.round(200 * (1 - Math.pow(1 - segment(t, 0.60, 0.88), 2))), 1 - segment(t, 0.18, 0.60)),
  };
  return {
    phase: t, reduced, actors, surfaces,
    crown: crownPose(t, reduced),
    gain: { x: theoX + theoSize / 2 + 6, y: theoY - 10, opacity: gainOpacity },
    roll,
    light: { response, phase: responsePhase, seat: seated }, response, responsePhase,
  };
}
