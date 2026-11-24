// The scene's fixed numbers, shared by the Filament stage, the 2D layers and the motion. The Blender build (outside the
// repository) writes plane.glb and this hinge table from one script.

export type Vec3 = [number, number, number];

/** The sheet (metres): SHEET_L along the nose axis (+x), SHEET_W across (z), the top face up (+y). */
export const SHEET_L = 0.21;
export const SHEET_W = 0.148;

/** The fold, as the glTF animation "Scene" (index 0) bakes it: seconds within FOLD_S for each step; every hinge
 * turns from 0 to its angle with an ease-in-out cubic over its step. */
export const FOLD_S = 1.4;
export const FOLD_STEPS = {
  /** the two nose corners fold in to the centre line */
  corner: [0, 0.42],
  /** the slanted edges fold in to the centre line again */
  second: [0.32, 0.76],
  /** the halves close up about the centre line (86 deg each) */
  half: [0.68, 1.08],
  /** the wings fold back down about the creases that run from the nose (77 deg: 9 deg of dihedral) */
  wing: [0.98, 1.4],
} as const;
export type FoldStep = keyof typeof FOLD_STEPS;

/** Every hinged panel: its node, where its hinge sits in its parent (the node's translation, metres), the hinge's
 * axis in the parent's frame, and its angle when folded (deg). R / L are the two halves; K / W the keel's and the
 * wing's side of the wing crease. Parents: Keel > Wing, FlapBK > FlapC1K > FlapC2K; Wing > FlapBW > FlapC1W > FlapC2W. */
export const HINGES: { node: string; pivot: Vec3; axis: Vec3; deg: number; step: FoldStep }[] = [
  { node: "KeelR", pivot: [0, -0.0005, 0], axis: [1, 0, 0], deg: -86, step: "half" },
  { node: "WingR", pivot: [0.105, 0.00025, 0], axis: [-0.992049, 0, 0.125849], deg: -77, step: "wing" },
  { node: "FlapBKR", pivot: [0.105, 0.001, 0], axis: [-0.92388, 0, 0.382683], deg: 179, step: "second" },
  { node: "FlapBWR", pivot: [0, 0.00075, 0], axis: [-0.92388, 0, 0.382683], deg: 179, step: "second" },
  { node: "FlapC1KR", pivot: [0, -0.0005, 0], axis: [-0.707107, 0, 0.707107], deg: 179, step: "corner" },
  { node: "FlapC1WR", pivot: [0, -0.0005, 0], axis: [-0.707107, 0, 0.707107], deg: 179, step: "corner" },
  { node: "FlapC2KR", pivot: [0, -0.0005, 0], axis: [-0.382683, 0, 0.92388], deg: -179, step: "second" },
  { node: "FlapC2WR", pivot: [0, -0.0005, 0], axis: [-0.382683, 0, 0.92388], deg: -179, step: "second" },
  { node: "KeelL", pivot: [0, -0.0005, 0], axis: [1, 0, 0], deg: 86, step: "half" },
  { node: "WingL", pivot: [0.105, 0.00025, 0], axis: [-0.992049, 0, -0.125849], deg: 77, step: "wing" },
  { node: "FlapBKL", pivot: [0.105, 0.001, 0], axis: [-0.92388, 0, -0.382683], deg: -179, step: "second" },
  { node: "FlapBWL", pivot: [0, 0.00075, 0], axis: [-0.92388, 0, -0.382683], deg: -179, step: "second" },
  { node: "FlapC1KL", pivot: [0, -0.0005, 0], axis: [-0.707107, 0, -0.707107], deg: -179, step: "corner" },
  { node: "FlapC1WL", pivot: [0, -0.0005, 0], axis: [-0.707107, 0, -0.707107], deg: -179, step: "corner" },
  { node: "FlapC2KL", pivot: [0, -0.0005, 0], axis: [-0.382683, 0, -0.92388], deg: 179, step: "second" },
  { node: "FlapC2WL", pivot: [0, -0.0005, 0], axis: [-0.382683, 0, -0.92388], deg: 179, step: "second" },
];

/** How the fold is driven: "animator" scrubs the baked glTF animation (RNF's Animator); "hinges" sets every panel
 * from the table above (the same motion; use it if the animator misbehaves on a device). */
export const FOLD_DRIVER: "animator" | "hinges" = "animator";

/** Camera: straight at the plane z = 0, where one metre is the same number of points everywhere on screen, so the
 * flat sheet lines up with the 2D paper exactly. The sheet is SHEET_PT wide on screen (see sheetPt). */
export const VFOV_DEG = 24;
export const CAMERA_NEAR = 0.05;
export const CAMERA_FAR = 20;

/** Light (Filament units). The IBL is Poly Haven's photo_studio_loft_hall baked with cmgen. */
export const IBL_INTENSITY = 15_000;
export const KEY_DIRECTION: Vec3 = [0.42, -0.5, -0.76];
export const KEY_LUX = 70_000;
export const FILL_DIRECTION: Vec3 = [-0.6, 0.35, -0.72];
export const FILL_LUX = 9_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BG = "#191C22";
export const BG_TOP = "#20242C";
export const FIELD = "#272B34";
export const FIELD_EDGE = "rgba(255,255,255,0.12)";
export const BUBBLE_IN = "#2D313B";
export const BUBBLE_OUT = "#2E6BE6";
export const INK = "#F2F3F5";
export const MUTE = "#AEB3BE";
/** The 2D paper that hands over to the 3D sheet: the lit sheet's colour on screen. */
export const PAPER = "#EFECE3";
export const PAPER_INK = "#24262B";

/** Copy: an invented chat app and a short thread. */
export const APP = "Pidgin";
export const CONTACT = "Ines Varga";
export const CONTACT_INITIALS = "IV";
export const THREAD = [
  { out: false, text: "Are you boarding yet?" },
  { out: true, text: "Just sat down. Window seat" },
  { out: false, text: "Nice. What time do you land?" },
] as const;
export const MESSAGE = "Landing at 6:40, save me a seat";
export const DELIVERED = "Delivered";
