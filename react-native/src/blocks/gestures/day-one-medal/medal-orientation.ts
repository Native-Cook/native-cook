// Linear reflectance: copper-gold, silver and a paler gold alloy.
export const MEDAL_METALS = [
  [0.86, 0.37, 0.085],
  [0.95, 0.93, 0.88],
  [0.92, 0.72, 0.34],
];

/** Object to world, column-major: yaw about y, then pitch about x, then roll about z. */
export function medalRotation(yaw: number, pitch: number, roll: number): number[] {
  'worklet';
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  const cr = Math.cos(roll);
  const sr = Math.sin(roll);
  return [
    cr * cy - sr * sp * sy,
    sr * cy + cr * sp * sy,
    -cp * sy,
    -sr * cp,
    cr * cp,
    sp,
    cr * sy + sr * sp * cy,
    sr * sy - cr * sp * cy,
    cp * cy,
  ];
}
