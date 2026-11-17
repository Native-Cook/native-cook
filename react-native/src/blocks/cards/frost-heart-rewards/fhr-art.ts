/** Generated alpha layers. Fit coordinates are pt; sprite rotations are degrees, slab radians. */
export const ART = {
  "bear": {
    "x": 63.5,
    "y": 126.0,
    "width": 230.0,
    "height": 198.0,
    "rotation": 2.0
  },
  "heart": {
    "x": 58.0,
    "y": 179.0,
    "width": 251.0,
    "height": 201.0,
    "rotation": -0.5
  },
  "paw": {
    "x": 44.0,
    "y": 290.0,
    "width": 80.0,
    "height": 89.0,
    "rotation": 6.5
  },
  "raisedPaw": {
    "x": 249,
    "y": 174,
    "width": 68,
    "height": 83,
    "rotation": 168
  },
  "slab": {
    "x": 116,
    "y": 250,
    "width": 143,
    "height": 82,
    "rotation": -0.36
  }
} as const;

/** Neutral-preserving hue rotation with chroma retained in the purple body. */
export const AMETHYST_MATRIX = [0.58, 0.42, 0, 0, 0, -0.16, 1.12, 0.04, 0, 0, 0.75, -0.25, 0.50, 0, 0, 0, 0, 0, 1, 0];

/** Small source-raster calibration shared by the native sprites and crystal backdrop. */
export const BEAR_MATRIX = [.956408, 0, 0, 0, .019412, 0, 1.032831, 0, 0, .024, 0, 0, 1.030621, 0, -.005808, 0, 0, 0, 1, 0];
