// The scene's fixed numbers, shared by the Filament stage, the motion and the panel. glTF space: y up, metres, the floor
// is y = 0 and the machine faces the viewer (+z). The Blender build (outside the repository) writes espresso.glb with
// these nodes at these places; its previews use the same camera.

export type Vec3 = [number, number, number];

/** The gauge's needle turns about z through this point; 0 bar is NEEDLE_ZERO_DEG counter-clockwise from straight up. */
export const NEEDLE_PIVOT: Vec3 = [0.058, 0.262, 0.1269];
export const NEEDLE_ZERO_DEG = 135;
export const NEEDLE_SPAN_DEG = 270;
export const GAUGE_MAX_BAR = 15;

/** The cup's foot on the grate (the Cup node), the coffee's base inside it, and the spout tips the streams hang from. */
export const CUP_REST: Vec3 = [0, 0.036, 0.0625];
export const COFFEE_BASE = 0.01;
export const SPOUT_TIP_Y = 0.122;
export const STREAM_TIPS: readonly Vec3[] = [
  [-0.0095, 0.122, 0.0625],
  [0.0095, 0.122, 0.0625],
];
/** Mesh heights the app scales: the coffee cylinder, the crema layer and a stream. */
export const COFFEE_REF = 0.01;
export const CREMA_REF = 0.005;
export const STREAM_REF = 0.01;
/** Liquid height in the cup for a single and a double (coffee and crema together), and the crema's share of it. */
export const DOSE_H = [0.0135, 0.027] as const;
export const CREMA_H = [0.0028, 0.0046] as const;
/** Yield in grams for a single and a double, and how long a shot takes on the machine's own clock (s). */
export const DOSE_G = [18, 36] as const;
export const SHOT_S = 28;
/** The cup is carried this far to the right (m) when it is changed: well out of frame. */
export const CUP_AWAY = 0.36;
export const CUP_LIFT = 0.006;
/** The crema turns this much (rad) while the shot pours. */
export const CREMA_SWIRL = 1.4;

/** Camera: it orbits its target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
export const TARGET_REST: Vec3 = [0, 0.18, 0.01];
export const TARGET_PEAK: Vec3 = [0, 0.18, 0.03];
export const DIST = 1.3;
export const AZ_REST = 18;
export const EL_REST = 9;
export const AZ_PEAK = 12;
export const EL_PEAK = 6;
export const ZOOM_PEAK = 0.02;
export const FRAME_W = 0.42;
export const FRAME_H = 0.42;
export const CAMERA_NEAR = 0.04;
export const CAMERA_FAR = 30;

/** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
export const IBL_INTENSITY = 30_000;
export const KEY_DIRECTION: Vec3 = [-0.45, -0.75, -0.5];
export const KEY_LUX = 68_000;
export const FILL_DIRECTION: Vec3 = [0.7, -0.3, -0.6];
export const FILL_LUX = 14_000;
export const EXPOSURE = 0;
/** The group head's light band (linear colour) and its emissive strength when fully on. */
export const GROUP_LIGHT_RGB: Vec3 = [1.0, 0.62, 0.3];
export const GROUP_LIGHT_ON = 6;

/** Palette (sRGB). */
export const BACKDROP = "#E8DFD0";
export const PANEL = "#FBF8F2";
export const CARD = "#FFFFFF";
export const INK = "#22262A";
export const MUTE = "#6E716F";
export const HAIR = "rgba(34,38,42,0.09)";
export const ACCENT = "#B5652B";
export const SLATE = "#3E5566";

/** Copy: an invented coffee machine app. */
export const APP = "Crema";
export const MACHINE = "Kitchen · Duo";
export const RECIPE = "Morning shot";
export const DOSES = ["Single", "Double"] as const;
