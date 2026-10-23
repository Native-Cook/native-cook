/** Measured from the subdivided Cycles mesh. Offsets are points from tile centre.
 * Data strips: linear UNORM; glass RGB: sRGB with straight coverage alpha.
 * exposure stores raw-linear -> packed multipliers; restore is their reciprocal.
 * Preview formula uses normalized tiles directly (G * 0.9). The physical G:D ratio is optional.
 * Mini content is fitted to 52 pt inside its 64 pt tile. */
export const KEYCAP_LAYOUT = {
  "tile": 480,
  "pxPerPt": 3,
  "footprint": 134,
  "footprintHeight": 131.853621,
  "face": {
    "cx": -7.386787,
    "cy": -9.403995,
    "w": 100.469909,
    "h": 99.090455,
    "r": 22.381736
  },
  "press": {
    "x": 3.454838,
    "y": 4.397497
  },
  "orthoScaleMm": 21.589031,
  "heightProjection": {
    "x": -7.773385,
    "y": -9.894369
  },
  "baseCenter": [
    0.38661,
    0.490394
  ],
  "encoding": {
    "data": "linear-unorm",
    "glass": "srgb-straight-alpha"
  },
  "softAlphaSigma": 8,
  "bloomSigma": 22,
  "miniTile": 192,
  "miniFootprint": 52,
  "wellTile": 720,
  "tiles": {
    "opaque": [
      "D",
      "G",
      "U",
      "R",
      "L",
      "A",
      "S"
    ],
    "glassLight": [
      "U",
      "L",
      "A",
      "S"
    ],
    "well": [
      "lip",
      "bloom"
    ]
  },
  "gains": {
    "exposure": {
      "D": 0.078804,
      "G": 0.002952,
      "U": 0.103377,
      "R": 0.382578,
      "L": 0.356357,
      "glass": 0.179317,
      "glass-U": 0.177297,
      "glass-L": 0.56496,
      "lip": 67.737076,
      "bloom": 0.788565,
      "A": 1,
      "S": 1
    },
    "restore": {
      "D": 12.689654,
      "G": 338.753361,
      "U": 9.673305,
      "R": 2.613844,
      "L": 2.806178,
      "glass": 5.576716,
      "glass-U": 5.640256,
      "glass-L": 1.770038,
      "lip": 0.014763,
      "bloom": 1.268126,
      "A": 1.0,
      "S": 1.0
    },
    "glossRelativeToDiffuse": 26.695239,
    "preview": {
      "white": {
        "u": 0.8,
        "r": 0.02,
        "l": 0,
        "lip": 0.01,
        "bloom": 0.00025
      },
      "mood": {
        "u": 0,
        "r": 0.3,
        "l": 0.45,
        "lip": 0.14,
        "bloom": 0.09
      },
      "glass": {
        "u": 0.08,
        "r": 0,
        "l": 0.25,
        "lip": 0.08,
        "bloom": 0.04
      }
    }
  }
} as const;
