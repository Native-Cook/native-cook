// The scene's fixed numbers, shared by the Filament stage, the Skia layer and the controls. glTF space: y up,
// metres, the camera in front (+z). The Blender build (outside the repository) writes watch.glb with these nodes at
// these rest positions; its previews use the same shots.

export type Vec3 = [number, number, number];
export type Shot = { eye: Vec3; target: Vec3 };

/** Case: 124 x 112 mm, base 52 mm tall, lid 30 mm; the lid turns about its hinge on the back top edge. */
export const CASE_W = 0.124;
export const CASE_H = 0.052;
export const LID_HINGE: Vec3 = [0, 0.052, -0.056];
/** How far the lid opens (rad): 104 deg, back past upright, resting on its stays. */
export const LID_OPEN = (104 * Math.PI) / 180;
/** The pillow's rest centre and how far it rises out of the tray. */
export const CUSHION_Y = 0.031;
export const LIFT = 0.018;
/** As it rises the pillow tips the dial towards the lens. */
export const TILT = (15 * Math.PI) / 180;
/** The watch head sits this far above the pillow's centre (cushion space); the hands turn about its y axis. */
export const WATCH_Y = 0.0184;
/** The dial's centre in cushion space (for the glint). */
export const DIAL_C: Vec3 = [0, WATCH_Y + 0.008, 0];

/** Dials the picker offers; the colour multiplies the greyscale sunburst texture. */
export const DIALS = [
  { name: "Blue", color: "#173764", metal: 0.55, rough: 0.3 },
  { name: "Green", color: "#1E4A3B", metal: 0.55, rough: 0.3 },
  { name: "Silver", color: "#C3C7CC", metal: 0.65, rough: 0.28 },
] as const;

/** Camera: horizontal field of view fixed, so the case keeps its width on every phone. */
export const HFOV_DEG = 30;
export const CAMERA_NEAR = 0.01;
export const CAMERA_FAR = 10;

/** Shots, fitted with the Blender previews (preview_poster / _open / _turn / _macro) rendered at the stage's size
 * (393 x 471: the window between the header row and the product panel); every key keeps the watch inside it. */
export const SHOTS = {
  poster: { eye: [0.12, 0.275, 0.325], target: [0, 0.028, 0] } as Shot,
  open: { eye: [0.101, 0.323, 0.336], target: [0, 0.06, 0] } as Shot,
  turn: { eye: [0.06, 0.202, 0.21], target: [0, 0.05, 0] } as Shot,
  macro: { eye: [0.03, 0.153, 0.12], target: [0, 0.065, 0.004] } as Shot,
} as const;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [0.4, -0.85, -0.35];
export const KEY_LUX = 72_000;
export const FILL_DIRECTION: Vec3 = [-0.7, -0.3, -0.6];
export const FILL_LUX = 15_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BG_TOP = "#F1EDE7";
export const BG_BOTTOM = "#E1DAD0";
export const INK = "#1A1917";
export const MUTE = "#68625A";

/** Copy: an invented house and watch. */
export const BRAND = "Halden";
export const MODEL = "Arden 39";
export const LINE = "Automatic, steel on calf leather";
export const PRICE = "$2,450";
export const SPECS = [
  { value: 39, unit: "mm", label: "Case" },
  { value: 70, unit: "h", label: "Power reserve" },
  { value: 100, unit: "m", label: "Water resistant" },
] as const;
