// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the floor
// is y = 0 and the door faces the viewer (+z). The Blender build (outside the repository) writes safe.glb with these
// nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The door turns about y through its hinge (opening towards the viewer is a negative angle). */
export const DOOR_HINGE: Vec3 = [-0.235, 0.333, 0.188];
/** In the Door node's frame: the door's centre, the dial's and the handle's pivots (both turn about z). */
export const DOOR_CENTRE: Vec3 = [0.235, 0, 0];
export const DIAL_PIVOT: Vec3 = [0.235, 0.052, 0];
export const HANDLE_PIVOT: Vec3 = [0.235, -0.112, -0.001];
export const DOOR_RADIUS = 0.205;
export const BORE_RADIUS = 0.213;
/** The housing's front plane (world z) and the dial's face (world z). */
export const FRONT_Z = 0.17;
export const DIAL_FACE_Z = 0.194;
/** Eight bolts, each at this angle clockwise from straight up; they slide this far towards the centre. */
export const BOLT_COUNT = 8;
export const BOLT_FIRST_DEG = 22.5;
export const BOLT_STEP_DEG = 45;
export const BOLT_TRAVEL = 0.03;

/** Camera: it orbits the target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
export const TARGET: Vec3 = [0, 0.325, 0];
export const DIST = 2.1;
export const AZ_REST = 13;
export const EL_REST = 7;
export const AZ_OPEN = 19;
export const EL_OPEN = 6;
export const ZOOM_OPEN = 0.06;
export const FRAME_W = 0.74;
export const FRAME_H = 0.76;
export const CAMERA_NEAR = 0.05;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [-0.35, -0.7, -0.62];
export const KEY_LUX = 76_000;
export const FILL_DIRECTION: Vec3 = [0.7, -0.3, -0.6];
export const FILL_LUX = 14_000;
export const EXPOSURE = 0;

/** Palette (sRGB). */
export const BACKDROP = "#CFC5B2";
export const PANEL = "#FBF8F1";
export const CARD = "#FFFFFF";
export const INK = "#1D211E";
export const MUTE = "#747568";
export const HAIR = "rgba(29,33,30,0.09)";
export const BRASS = "#9A6F1E";
export const GREEN = "#1F4238";
export const RED = "#B33A2B";
export const GLOW = "#FFB95C";

/** Copy: an invented password vault. */
export const APP = "Keep";
export const VAULT = "Personal vault";
export const ROWS = [
  { label: "Passwords", value: "128" },
  { label: "Recovery codes", value: "16" },
  { label: "Secure notes", value: "9" },
] as const;
/** The demo's combination. */
export const DEMO_CODE: readonly [number, number, number] = [24, 8, 31];
