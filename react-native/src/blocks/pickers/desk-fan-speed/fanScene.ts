// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the floor
// is y = 0 and the fan faces the viewer (+z). The Blender build (outside the repository) writes fan.glb with these
// nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The head turns about y through this point; the blades about z through BLADES_PIVOT, in the head's frame. */
export const HEAD_PIVOT: Vec3 = [0, 0.285, 0];
export const BLADES_PIVOT: Vec3 = [0, 0, 0.056];
/** The four piano keys, at rest (Off, 1, 2, 3); a pressed key sinks KEY_TRAVEL. */
export const KEY_POSITIONS: readonly Vec3[] = [
  [-0.05175, 0.052, 0.082],
  [-0.01725, 0.052, 0.082],
  [0.01725, 0.052, 0.082],
  [0.05175, 0.052, 0.082],
];
export const KEY_TRAVEL = 0.0055;
/** The blur disc's colour (linear), its alpha set per frame. */
export const BLUR_RGB: Vec3 = [0.69, 0.46, 0.15];

/** Camera: it orbits the target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
export const TARGET: Vec3 = [0, 0.215, 0];
export const DIST = 1.35;
export const AZ_REST = -16;
export const AZ_PEAK = -10;
export const EL_REST = 9;
export const EL_PEAK = 8;
export const ZOOM_PEAK = 0.05;
export const FRAME_W = 0.4;
export const FRAME_H = 0.5;
export const CAMERA_NEAR = 0.04;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [-0.4, -0.8, -0.45];
export const KEY_LUX = 70_000;
export const FILL_DIRECTION: Vec3 = [0.7, -0.3, -0.6];
export const FILL_LUX = 14_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BACKDROP = "#E4D7BA";
export const PANEL = "#FCFAF4";
export const CARD = "#FFFFFF";
export const INK = "#1F2A26";
export const MUTE = "#6F7670";
export const HAIR = "rgba(31,42,38,0.09)";
export const GREEN = "#2F6657";

/** Copy: an invented smart-home app. */
export const APP = "Breeze";
export const ROOM = "Bedroom fan";
export const LEVELS = ["Off", "1", "2", "3"] as const;
/** What each level reads as on the panel: motor rpm and watts. */
export const RPM = [0, 620, 1040, 1480] as const;
export const WATTS = [0, 14, 26, 41] as const;
