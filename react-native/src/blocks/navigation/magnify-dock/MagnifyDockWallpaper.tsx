import { Circle, Group, LinearGradient, Path, RadialGradient, Rect, Skia, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';

/**
 * The wallpaper: a blue-hour mountain range drawn in four ridges that fade into the haze with
 * distance, under a sky that warms towards the horizon and a low sun behind the farthest ridge.
 * Each ridge is a fixed midpoint-displacement profile of 65 heights (0 to 1), so every stack
 * draws the same mountains.
 */

const RIDGES = [
  [
    0.0, 0.11, 0.193, 0.21, 0.233, 0.239, 0.283, 0.325, 0.282, 0.323, 0.28, 0.29, 0.241, 0.285, 0.293, 0.359, 0.457, 0.388, 0.365, 0.381, 0.433, 0.424,
    0.488, 0.534, 0.534, 0.514, 0.512, 0.603, 0.635, 0.689, 0.763, 0.918, 0.994, 1.0, 0.946, 0.922, 0.984, 0.869, 0.803, 0.826, 0.778, 0.758, 0.744,
    0.775, 0.723, 0.665, 0.625, 0.549, 0.546, 0.53, 0.492, 0.432, 0.325, 0.323, 0.362, 0.342, 0.392, 0.389, 0.414, 0.441, 0.389, 0.334, 0.235, 0.125,
    0.08,
  ],
  [
    0.174, 0.197, 0.186, 0.163, 0.126, 0.108, 0.097, 0.116, 0.112, 0.035, 0.009, 0.0, 0.042, 0.073, 0.132, 0.159, 0.135, 0.171, 0.255, 0.27, 0.347,
    0.387, 0.49, 0.501, 0.585, 0.593, 0.637, 0.651, 0.653, 0.801, 0.898, 0.936, 1.0, 0.904, 0.828, 0.746, 0.698, 0.688, 0.708, 0.696, 0.639, 0.655,
    0.664, 0.677, 0.728, 0.694, 0.723, 0.731, 0.695, 0.664, 0.601, 0.556, 0.459, 0.376, 0.336, 0.306, 0.269, 0.262, 0.188, 0.218, 0.233, 0.256, 0.208,
    0.173, 0.19,
  ],
  [
    0.512, 0.568, 0.594, 0.585, 0.496, 0.553, 0.696, 0.739, 0.767, 0.726, 0.781, 0.851, 0.95, 0.889, 0.894, 0.983, 1.0, 0.996, 0.947, 0.999, 0.974,
    0.907, 0.897, 0.907, 0.874, 0.854, 0.795, 0.718, 0.638, 0.692, 0.663, 0.625, 0.563, 0.535, 0.551, 0.554, 0.515, 0.537, 0.562, 0.531, 0.556, 0.619,
    0.658, 0.65, 0.732, 0.683, 0.644, 0.695, 0.734, 0.639, 0.642, 0.606, 0.54, 0.434, 0.409, 0.413, 0.387, 0.357, 0.358, 0.313, 0.362, 0.274, 0.087,
    0.001, 0.0,
  ],
  [
    0.804, 0.841, 0.8, 0.814, 0.901, 0.911, 0.95, 0.961, 0.951, 0.982, 0.995, 0.972, 1.0, 0.967, 0.871, 0.904, 0.88, 0.849, 0.851, 0.763, 0.733, 0.65,
    0.539, 0.507, 0.49, 0.501, 0.485, 0.425, 0.366, 0.302, 0.255, 0.108, 0.039, 0.0, 0.02, 0.052, 0.091, 0.149, 0.17, 0.169, 0.179, 0.226, 0.233, 0.226,
    0.213, 0.275, 0.275, 0.377, 0.454, 0.459, 0.492, 0.455, 0.477, 0.47, 0.534, 0.584, 0.709, 0.786, 0.804, 0.817, 0.77, 0.787, 0.763, 0.698, 0.697,
  ],
];

/** Each ridge: its foot and height as fractions of the screen's height, its colour at the crest and at the foot. */
const RIDGE_STYLE: { base: number; rise: number; crest: string; foot: string }[] = [
  { base: 0.53, rise: 0.14, crest: '#9aa6bb', foot: '#cdc6c6' },
  { base: 0.62, rise: 0.13, crest: '#6c7e9c', foot: '#a2a5b3' },
  { base: 0.72, rise: 0.12, crest: '#45577a', foot: '#747e98' },
  { base: 0.86, rise: 0.1, crest: '#222f47', foot: '#161f30' },
];

export const SKY_COLORS = ['#1d3150', '#3d5880', '#8b93a8', '#e5bd9c', '#f2cfae'];
export const SKY_STOPS = [0, 0.28, 0.42, 0.52, 0.6];
// The sun: centre and radius as fractions of the screen's width (x, r) and height (y).
const SUN = { x: 0.7, y: 0.455, r: 0.045, halo: 0.24 };

function ridgePath(heights: number[], base: number, rise: number, width: number, height: number) {
  const path = Skia.PathBuilder.Make();
  const n = heights.length - 1;
  for (let i = 0; i <= n; i += 1) {
    const x = -2 + ((width + 4) * i) / n;
    const y = (base - rise * heights[i]) * height;
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  path.lineTo(width + 2, height + 2);
  path.lineTo(-2, height + 2);
  path.close();
  return path.build();
}

/** The wallpaper for a screen `width` × `height`, drawn into the dock's canvas so its glass can blur it. */
export function MagnifyDockWallpaper({ width, height }: { width: number; height: number }) {
  const ridges = useMemo(
    () => RIDGES.map((heights, i) => ridgePath(heights, RIDGE_STYLE[i].base, RIDGE_STYLE[i].rise, width, height)),
    [width, height],
  );
  return (
    <Group>
      <Rect x={0} y={0} width={width} height={height}>
        <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={SKY_COLORS} positions={SKY_STOPS} />
      </Rect>
      <Circle cx={width * SUN.x} cy={height * SUN.y} r={width * SUN.halo}>
        <RadialGradient c={vec(width * SUN.x, height * SUN.y)} r={width * SUN.halo} colors={['rgba(255,228,196,0.55)', 'rgba(255,228,196,0)']} />
      </Circle>
      <Circle cx={width * SUN.x} cy={height * SUN.y} r={width * SUN.r} color="#fff2df" />
      {ridges.map((path, i) => {
        const s = RIDGE_STYLE[i];
        const top = (s.base - s.rise) * height;
        const bottom = i === RIDGES.length - 1 ? height : (s.base + 0.1) * height;
        return (
          <Path key={i} path={path}>
            <LinearGradient start={vec(0, top)} end={vec(0, bottom)} colors={[s.crest, s.foot]} />
          </Path>
        );
      })}
    </Group>
  );
}
