// Crystal Trophy Reveal — the contract between the stage, the trophy and the screen.
//
// One full-screen Skia <Canvas> (owned by the screen) holds three layers, back to front:
//   <StageBack>   the back wall of blue velvet, the floor, the spotlight's shaft and pool, the prismatic flecks
//   <Trophy>      the marble plinth and the crystal standing in it (ray-traced on every frame)
//   <StageFront>  dust in the beam, then the two halves of the front curtain
// Title, copy and controls are React views above the canvas.
//
// TROPHY SPACE (the plinth and the crystal): x right, y up, z toward the viewer, in units.
// The origin is the centre of the plinth's top face, which is also the crystal's vertical axis.
// The crystal at rest spans y from -CRYSTAL.sunk to CRYSTAL.height; its number is engraved on its back.
// The whole trophy (plinth and crystal together, like a turntable) turns about the y axis by `yaw`:
// positive yaw brings the front face toward the viewer's right, the way a finger dragging right pushes it.
// Camera: a pinhole CAMERA.distance units from the origin, raised by CAMERA.elevation radians, looking at the
// origin (so the plinth's top face is seen as a thin octagon). `projectTrophy` is the one definition.
import type { SharedValue } from 'react-native-reanimated';

export const CAMERA = { distance: 11, elevation: 0.15 } as const;

/** Crystal proportions in units. The silhouette is a heptagon: apex, two shoulders, two waist points, two feet. */
export const CRYSTAL = {
  height: 2.9,
  /** How far the crystal goes on below the plinth's top at rest (hidden in the slot). */
  sunk: 0.28,
  /** Half-width at the waist (the widest point) and the height of the waist ridge. */
  waistHalf: 0.86,
  waistY: 1.34,
  /** Thickness front to back at the waist. */
  depth: 0.56,
  /** How far the crystal is lowered at rise = 0 (it is then wholly inside the plinth). */
  travel: 2.96,
} as const;

/** The plinth: an octagonal slab of white marble; a flat side faces the viewer at yaw 0. */
export const PLINTH = { apothem: 0.94, height: 0.3, chamfer: 0.04 } as const;

/** Direction from the trophy to the spot lamp, in VIEW space (it does not turn with the trophy). */
export const LAMP_DIR = [0.16, 0.9, 0.4] as const;

export const PALETTE = {
  page: '#02030B',
  /** Velvet, from its shadowed creases to its lit ridges. */
  velvetDeep: '#050A2E',
  velvet: '#1024A8',
  velvetLit: '#3A5BFF',
  velvetSheen: '#9DB4FF',
  /** The spot lamp: cool white with a breath of blue. */
  lamp: '#EAF1FF',
  lampHalo: '#6F8CFF',
  /** Crystal: the edge tint of thick glass, and the frosted engraving. */
  crystalEdge: '#8FD4FF',
  crystalBody: '#C9DCFF',
  frost: '#F4F8FF',
  marble: '#E9ECF2',
  marbleVein: '#7C8496',
  ink: '#FFFFFF',
  inkSoft: 'rgba(222, 229, 255, 0.78)',
  inkMute: 'rgba(222, 229, 255, 0.56)',
} as const;

export const COPY = {
  titleLines: ['PERFECT', 'MONTH'],
  date: 'Won on Oct 13, 2026',
  body: ['Thirty days, thirty sessions.', 'Not a single one missed.'],
  number: '30',
  share: 'SHARE',
  shared: 'LINK COPIED',
  hint: 'TAP TO REVEAL',
  closeLabel: 'Close',
  shareLabel: 'Share this trophy',
  trophyLabel: 'Crystal trophy engraved with 30. Drag to turn it, tap to make it flash.',
  curtainLabel: 'Closed curtain. Tap or pull it apart to reveal the trophy.',
} as const;

export const FONTS = {
  title: 'TrophyBarlow-ExtraBoldItalic',
  date: 'TrophyBarlow-SemiBoldItalic',
  label: 'TrophyBarlow-Bold',
  hint: 'TrophyBarlow-Medium',
} as const;

/** Everything is placed from this. Points, origin at the screen's top-left. */
export type TrophyFrame = {
  width: number;
  height: number;
  /** 1 on a 402 x 874 phone; smaller on short screens. Type and the trophy scale by it. */
  k: number;
  /** Where the trophy-space origin lands, and points per unit at the origin's depth. */
  originX: number;
  originY: number;
  unit: number;
  /** Title block: the two lines and the date are one group turned by `titleTilt` about (titleX, titleY). */
  titleX: number;
  titleY: number;
  titleSize: number;
  titleLine: number;
  titleTilt: number;
  dateY: number;
  dateSize: number;
  bodyTop: number;
  bodySize: number;
  bodyLine: number;
  share: { x: number; y: number; width: number; height: number; radius: number };
  close: { x: number; y: number; size: number };
};

/**
 * 402 x 874 with insets 62 / 34 gives: close 40 x 40 at (346, 66); title centre (201, 166), 68 pt on a 62 pt
 * line, tilted -8 degrees (rising to the right); date centred at y 250, 20 pt; crystal apex about y 298, trophy
 * origin (plinth top) at (201, 608), 108 pt per unit; body 16/22 from y 684; Share 354 x 56 at (24, 768).
 */
export function layout(width: number, height: number, insetTop: number, insetBottom: number): TrophyFrame {
  const usable = height - insetTop - insetBottom;
  const k = Math.min(1, Math.max(0.72, usable / 778));
  const shareWidth = Math.min(354, width - 48);
  const shareY = height - insetBottom - 16 - 56;
  const bodyLine = 22;
  const bodyTop = shareY - Math.round(40 * k) - bodyLine * 2;
  return {
    width,
    height,
    k,
    originX: width / 2,
    originY: bodyTop - Math.round(76 * k),
    unit: 108 * k,
    titleX: width / 2,
    titleY: insetTop + Math.round(104 * k),
    titleSize: Math.round(68 * k),
    titleLine: Math.round(62 * k),
    titleTilt: (-8 * Math.PI) / 180,
    dateY: insetTop + Math.round(188 * k),
    dateSize: Math.round(20 * k),
    bodyTop,
    bodySize: 16,
    bodyLine,
    share: { x: (width - shareWidth) / 2, y: shareY, width: shareWidth, height: 56, radius: 16 },
    close: { x: width - 16 - 40, y: insetTop + 4, size: 40 },
  };
}

/** Trophy-space point to canvas points. For a point on the crystal while it rises, lower y by (1 - rise) * CRYSTAL.travel first. */
export function projectTrophy(x: number, y: number, z: number, yaw: number, frame: TrophyFrame) {
  'worklet';
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const x1 = x * cy + z * sy;
  const z1 = -x * sy + z * cy;
  const ce = Math.cos(CAMERA.elevation);
  const se = Math.sin(CAMERA.elevation);
  const y2 = y * ce - z1 * se;
  const z2 = y * se + z1 * ce;
  const s = CAMERA.distance / (CAMERA.distance - z2);
  return { x: frame.originX + frame.unit * x1 * s, y: frame.originY - frame.unit * y2 * s, depth: z2 };
}

/**
 * The state every layer draws from. All of it lives on the UI thread; the screen's motion hook is the only
 * writer. Layers read with `.get()` inside one `useDerivedValue` worklet that builds their uniforms.
 */
export type StageValues = {
  /**
   * Seconds, only for slow idle life (dust drift, smoke, shimmer), which has a 15 s period. Interactive: time
   * since ready. Under autoPlay: the phase of the 15 s loop, so every pass looks the same.
   */
  time: SharedValue<number>;

  // --- front curtain -------------------------------------------------------------------------------------
  /** The curtain rail. 0: the two halves meet at the centre. 1: gathered at the sides. May pass 1 by 0.03. */
  open: SharedValue<number>;
  /** The same for the hem. It lags behind `open` and swings past it (about -0.06 .. 1.08): the cloth's weight. */
  hem: SharedValue<number>;
  /** 0..1: one ripple running down the closed curtain (the tug before it opens). 0 and 1 show nothing. */
  shiver: SharedValue<number>;
  /** 0..1: light leaking through the seam of the closed curtain and under its hem. */
  seam: SharedValue<number>;

  // --- light ---------------------------------------------------------------------------------------------
  /** Spot lamp intensity. 0 dark, 1 full; it flickers on the way up and may reach 1.25 during a flash. */
  lamp: SharedValue<number>;
  /** Where the spot is aimed. 0: high on the back wall, up and to the left. 1: on the trophy. */
  beam: SharedValue<number>;
  /** 0..1: light coming up out of the plinth's slot through the crystal (while it rises). */
  under: SharedValue<number>;

  // --- trophy --------------------------------------------------------------------------------------------
  /** 0: the crystal is wholly inside the plinth. 1: at rest. It overshoots to about 1.06. */
  rise: SharedValue<number>;
  /** Radians. Rest is 0. Drag takes it to about +-0.52 (30 degrees) and past that with resistance; a flick spins it. */
  yaw: SharedValue<number>;
  /** 0: bare crystal. 1: fully engraved. A point of light runs round the edge, then draws the number, then the mark. */
  etch: SharedValue<number>;
  /** 0..1: a finger resting on the crystal is a small light at (touchX, touchY). */
  touch: SharedValue<number>;
  /** 0..1 envelope of the tap flash (rises in about 60 ms, dies in about 500 ms), born at (touchX, touchY). */
  flash: SharedValue<number>;
  /** The finger on the crystal's front, in trophy units at yaw 0 (x right, y up, from the origin). */
  touchX: SharedValue<number>;
  touchY: SharedValue<number>;
};

export type LayerProps = {
  frame: TrophyFrame;
  values: StageValues;
  /** Under Reduce Motion: no idle drift, no shimmer; everything else is driven by `values` as usual. */
  still?: boolean;
  /** Called with true once the layer's textures are loaded and its first frame can draw. */
  onReady?: (ready: boolean) => void;
};

/** The reveal, in seconds from the trigger. The screen's timeline follows this; layers never read it. */
export const TIMING = {
  anticipation: 0.32,
  curtain: { start: 0.3, duration: 0.7 },
  lampStrike: 0.86,
  beam: { start: 0.92, duration: 0.66 },
  under: { start: 1.44, in: 0.2, out: { start: 2.0, duration: 0.6 } },
  rise: { start: 1.56 },
  settleFlash: 2.3,
  etch: { start: 2.36, duration: 1.15 },
  title: { start: 2.66, stagger: 0.038, letter: 0.42 },
  date: 3.12,
  body: 3.26,
  controls: 3.4,
  settled: 4.0,
} as const;
