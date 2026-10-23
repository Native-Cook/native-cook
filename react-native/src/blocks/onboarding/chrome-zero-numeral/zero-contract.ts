// Chrome Zero Numeral — the contract between the shader canvas, the screen and the assets.
//
// Model space: x right, y up, z toward the viewer. The "0" is 2 units tall (y from -1 to 1),
// about 1.28 wide and 0.52 deep, centred on the origin. The "%" hangs off its right side.
// Orientation: a model point is scaled about the origin, turned about the model's Y axis by `yaw`,
// then about the screen's X axis by `pitch`. Positive yaw turns the front face toward the viewer's
// right (the way a finger dragging right would push it); positive pitch tips the top toward the viewer.
// Camera: a pinhole on the +z axis at CAMERA units from the origin, looking at the origin.
import type { SharedValue } from 'react-native-reanimated';

export const CAMERA = 7;

/** The pose the numeral rests in: its blue right wall and the underside of its bowl show. */
export const REST = { yaw: -0.33, pitch: -0.1 } as const;

/** Where the "%" is docked, in model units, and how tall it is. */
export const PERCENT = { x: 0.76, y: -0.1, z: 0.3, height: 0.62 } as const;

export type ZeroCanvasProps = {
  /** Canvas size in points. It spans the screen's width, from the screen's top edge down. */
  width: number;
  height: number;
  /** Where the model's origin lands on the canvas, in points. */
  centreX: number;
  centreY: number;
  /** Points per model unit at z = 0. */
  unit: number;
  /** Radians. */
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
  /** 1 at rest; the whole model about its origin. */
  scale: SharedValue<number>;
  /** 0: no bead has entered. 1: every bead is in its place. Beads fall in columns from above the canvas. */
  build: SharedValue<number>;
  /** 0: beads in place. 1: every bead has fallen out below the canvas. */
  drain: SharedValue<number>;
  /** 0: separate chrome beads. 1: one solid numeral. In between the beads swell and their seams close. */
  fuse: SharedValue<number>;
  /** 0..1: a ring of bent light travelling out from the centre of the face. 0 and 1 show nothing. */
  ripple: SharedValue<number>;
  /** Where on the front face the ripple starts, in model units (x right, y up). The centre is (0, 0). */
  rippleX: SharedValue<number>;
  rippleY: SharedValue<number>;
  /** -1..1: a band of key light crossing the faces from left to right. At -1 and 1 it is off the model. */
  sweep: SharedValue<number>;
  /** 0: the "%" is hidden behind the "0". 1: docked. In between it swings out, turning and growing. */
  percent: SharedValue<number>;
  /** 0..1: extra brightness of every highlight (the flash at the moment the beads fuse). */
  flash: SharedValue<number>;
  /** Called once the textures are loaded and the first frame can be drawn, and with false if they are lost. */
  onReady?: (ready: boolean) => void;
};

/** Model point to canvas points, for pinning 2D layers (glints, shadows, sparkles) to the numeral. */
export function projectZero(
  x: number, y: number, z: number,
  yaw: number, pitch: number, scale: number,
  centreX: number, centreY: number, unit: number,
) {
  'worklet';
  const sx = x * scale;
  const sy = y * scale;
  const sz = z * scale;
  const cy = Math.cos(yaw);
  const syaw = Math.sin(yaw);
  const x1 = sx * cy + sz * syaw;
  const z1 = -sx * syaw + sz * cy;
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  const y2 = sy * cp - z1 * sp;
  const z2 = sy * sp + z1 * cp;
  const k = CAMERA / (CAMERA - z2);
  return { x: centreX + unit * x1 * k, y: centreY - unit * y2 * k, depth: z2 };
}
