// The scene's fixed numbers, shared by the Filament stage, the Skia layers and the controls. glTF space: y up,
// metres, the camera in front (+z). The Blender build (outside the repository) writes ring.glb with these nodes at
// these rest positions (and scene.json with the same numbers); its previews use the same shots.

export type Vec3 = [number, number, number];
export type Shot = { eye: Vec3; target: Vec3 };

/** Box: 50 x 46 mm with round shoulders (15 mm corner radius), base 25 mm, a domed 17 mm lid hinged on the back
 * top edge. The base's bottom edge is rounded by BASE_R, so it meets the floor a little inside its outline. */
export const BOX_W = 0.05;
export const BOX_D = 0.046;
export const BOX_RV = 0.015;
export const BASE_R = 0.0032;
export const BASE_H = 0.025;
export const LID_HINGE: Vec3 = [0, 0.025, -0.023];
/** How far the lid snaps open (rad). */
export const LID_OPEN = (104 * Math.PI) / 180;
/** The ring's node rest position (the band's centre). */
export const RING_Y = 0.0238;
/** The stone: girdle centre (box space), girdle radius, crown height; its centre above the ring node. */
export const STONE_C: Vec3 = [0, 0.0384, 0];
export const STONE_R = 0.0037;
export const STONE_CROWN = 0.0012;
export const STONE_LOCAL_Y = 0.0146;

/** Metals and stones the pickers offer (sRGB colour; roughness for metals; emissive strength over the stone's
 * baked facet pattern for stones; price offsets). */
export const METALS = [
  { name: "Platinum", short: "Platinum", color: "#E5E4E2", rough: 0.08, price: 0 },
  { name: "Yellow gold", short: "Yellow", color: "#F0C77F", rough: 0.1, price: -350 },
  { name: "Rose gold", short: "Rose", color: "#EBB39B", rough: 0.1, price: -350 },
] as const;
export const STONES = [
  { name: "Diamond", color: "#F6F8FF", glow: 1.15, price: 0 },
  { name: "Sapphire", color: "#2F56D8", glow: 1.5, price: -2400 },
  { name: "Morganite", color: "#F3AE9C", glow: 1.2, price: -3900 },
] as const;
/** Carat range of the native slider; the model's stone is CARAT_MODEL. */
export const CARAT_MIN = 1;
export const CARAT_MAX = 2;
export const CARAT_STEP = 0.25;
export const CARAT_MODEL = 1.5;
/** Price of the setting in platinum with a 1.5 ct diamond, and per carat above or below it. */
export const PRICE_BASE = 9_800;
export const PRICE_PER_CT = 6_200;

/** Camera: horizontal field of view fixed. */
export const HFOV_DEG = 30;
export const CAMERA_NEAR = 0.004;
export const CAMERA_FAR = 5;

/** Shots, fitted with the Blender previews (preview_poster / _open / _ring / _macro) rendered at the stage's size
 * (393 x 451: the window between the header row and the options panel). The poster, the open box and the ring shot
 * keep the whole box in frame; only the macro on the stone goes inside it, with the lid's cream lining behind. */
export const SHOTS = {
  poster: { eye: [0.1, 0.114, 0.16], target: [0, 0.028, 0] } as Shot,
  open: { eye: [0.094, 0.118, 0.158], target: [0, 0.034, 0] } as Shot,
  ring: { eye: [0.03, 0.0663, 0.142], target: [0, 0.03, 0] } as Shot,
  macro: { eye: [0.015, 0.0487, 0.0461], target: [0, 0.0366, 0] } as Shot,
} as const;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 36_000;
export const KEY_DIRECTION: Vec3 = [0.35, -0.85, -0.4];
export const KEY_LUX = 80_000;
export const FILL_DIRECTION: Vec3 = [-0.7, -0.3, -0.6];
export const FILL_LUX = 16_000;
export const EXPOSURE = 0;
/** Where the glints look for light: the key, the fill and two studio softboxes (unit vectors towards the light). */
export const GLINT_LIGHTS: readonly (readonly number[])[] = [
  [-0.356, 0.865, 0.407, 1.0],
  [0.722, 0.31, 0.619, 0.45],
  [-0.2, 0.55, 0.81, 0.8],
  [0.55, 0.75, -0.37, 0.6],
];

/** Palette (sRGB): warm paper, deep navy velvet. */
export const BG_TOP = "#F3EFE8";
export const BG_BOTTOM = "#E6E0D6";
export const INK = "#1B1A18";
export const MUTE = "#6A645B";
export const NAVY = "#18213D";

/** Copy: an invented atelier and ring. */
export const ATELIER = "Atelier Mira";
export const RING_NAME = "Eloise Solitaire";
