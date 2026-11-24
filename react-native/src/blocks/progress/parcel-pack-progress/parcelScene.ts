// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the
// floor is y = 0 and the viewer is at +z. The Blender build (outside the repository) writes parcel.glb with these
// nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The carton: width (x), depth (z), height (y), and the board's thickness. */
export const BOX_W = 0.3;
export const BOX_D = 0.22;
export const BOX_H = 0.19;
export const BOARD = 0.005;
/** The carton's turn on the floor (deg about y): a three-quarter view. */
export const BOX_YAW = -30;

/** A flap: its node, its hinge in the carton's frame, the hinge's axis, its angle when open (deg, signed) and how far
 * its hinge sinks as it closes (the short flaps lie under the long ones). The model is exported closed (0 deg). */
export type Flap = { node: string; pivot: Vec3; axis: Vec3; open: number; sink: number };
export const FLAPS: Flap[] = [
  { node: "FlapLeft", pivot: [-0.15, 0.19, 0], axis: [0, 0, 1], open: 128, sink: 0.0056 },
  { node: "FlapRight", pivot: [0.15, 0.19, 0], axis: [0, 0, 1], open: -136, sink: 0.0056 },
  { node: "FlapFront", pivot: [0, 0.19, 0.11], axis: [1, 0, 0], open: 146, sink: 0 },
  { node: "FlapBack", pivot: [0, 0.19, -0.11], axis: [1, 0, 0], open: -110, sink: 0 },
];

/** The tape, three pieces that grow from their origins: up the left wall (scale y), along the seam (scale x), down
 * the right wall (scale y). Their lengths (m) give the roll one speed over the three. */
export const TAPE_A: Vec3 = [-0.1503, 0.135, 0];
export const TAPE_TOP: Vec3 = [-0.1509, 0.1906, 0];
export const TAPE_B: Vec3 = [0.1503, 0.1912, 0];
export const TAPE_SIDE = 0.055;
export const TAPE_SEAM = 0.3;

/** The label: its centre on the lid, and its turn there (deg about y). */
export const LABEL: Vec3 = [0.058, 0.1908, 0.066];
export const LABEL_YAW = 4;

/** Camera: it orbits the target at DIST (az 0 = straight in front, + to the viewer's right; el above the floor).
 * The frame holds FRAME_W x FRAME_H metres around the target on every phone (see tanHalfFovY). */
export const TARGET: Vec3 = [0, 0.15, 0];
export const DIST = 1.25;
export const EL_REST = 30;
export const FRAME_W = 0.62;
export const FRAME_H = 0.52;
export const CAMERA_NEAR = 0.05;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [0.4, -0.85, -0.35];
export const KEY_LUX = 72_000;
export const FILL_DIRECTION: Vec3 = [-0.7, -0.3, -0.6];
export const FILL_LUX = 15_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BACKDROP = "#E4DCCE";
export const PANEL = "#FBFAF7";
export const CARD = "#FFFFFF";
export const INK = "#1F1D1A";
export const MUTE = "#7C766B";
export const HAIR = "rgba(31,29,26,0.08)";
export const GREEN = "#1F5C4A";
export const GREEN_SOFT = "#E3EEE9";
export const TRACK = "#DDD7CB";

/** Copy: an invented shop and one order. */
export const SHOP = "Carton & Co.";
export const ORDER = "Order 4821";
export const ARRIVES = "Arrives Thursday by 6 PM";
export const STEPS = [
  { title: "Packed", at: "9:41 AM" },
  { title: "Sealed", at: "9:44 AM" },
  { title: "Labelled", at: "9:47 AM" },
  { title: "On its way", at: "9:52 AM" },
] as const;
/** The headline for 0..4 finished steps. */
export const HEADLINES = ["Packing your order", "Sealing the box", "Adding the label", "Waiting for the courier", "On its way"] as const;
export const STEP_N = STEPS.length;
