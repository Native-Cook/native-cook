// The scene's fixed numbers, shared by the Filament stage, the Skia layers and the welcome screen. glTF space: y up,
// the outer arch's radius is 1, the arches stand on their feet at y = 0 round the vertical axis through the origin.
// The Blender build (outside the repository) writes arca.glb with the same arches, pearl and node names.

export type Vec3 = [number, number, number];

/** The three arches: radius of the centre line, half width (radial) and half depth of the rounded section. */
export const ARCHES = [
  { name: "ArchOuter", r: 1.0, a: 0.092, b: 0.115 },
  { name: "ArchMiddle", r: 0.74, a: 0.086, b: 0.108 },
  { name: "ArchInner", r: 0.49, a: 0.08, b: 0.1 },
] as const;
/** At rest the arches stand at these turns round y (degrees): a dome cage. Folded, all are 0 and face the lens. */
export const REST_YAW = [0, 60, -60] as const;

export const PEARL_R = 0.17;
/** Where the pearl floats inside the cage (its centre's height). */
export const PEARL_FLOAT = 0.62;

/** The progress track on the floor round the sculpture. */
export const TRACK_R = 1.16;

/** Camera: horizontal field of view fixed, so the sculpture keeps its width on every phone. */
export const HFOV_DEG = 26;
export const CAMERA_NEAR = 0.1;
export const CAMERA_FAR = 40;

export type Shot = { eye: Vec3; target: Vec3 };
/** poster: 17 deg above, the cage reads in depth; the arches span 7-93 % of the width and the cage with its track is
 * centred near 0.44 of the height (with the horizontal field fixed, the width is what limits the size: any closer and
 * the track leaves the screen). front: almost level with the mark, a little farther, so it reads flat at 80 % width. */
export const SHOTS = {
  poster: { eye: [0, 1.655, 5.246], target: [0, 0, 0] } as Shot,
  front: { eye: [0, 0.42, 5.9], target: [0, 0.3, 0] } as Shot,
} as const;

/** Light (Filament units; tuned against the 256 px photo_studio_loft_hall bake, as the sneaker block). */
export const IBL_INTENSITY = 32_000;
export const KEY_DIRECTION: Vec3 = [-0.45, -0.8, -0.4];
export const KEY_LUX = 70_000;
export const FILL_DIRECTION: Vec3 = [0.6, -0.25, -0.75];
export const FILL_LUX = 14_000;
/** Exposure stops for the filmic tone curve. */
export const EXPOSURE = 0.15;

/** Palette: warm paper, ink, and the mark's three colours (flat versions of the arches' materials). */
export const PAPER_TOP = "#F4F1EC";
export const PAPER_BOTTOM = "#E6E1D9";
export const INK = "#1D1C1A";
export const MUTE = "#6E6961";
export const MARK = {
  outer: "#1D1C1A",
  middle: "#C2A27B",
  inner: "#2B2926",
  pearl: "#CF4A2A",
} as const;
