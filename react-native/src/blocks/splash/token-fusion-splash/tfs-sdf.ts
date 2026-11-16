/** Exact Euclidean distance transform, two separable lower-envelope passes (Felzenszwalb). */
export function distanceTransform(mask: Uint8Array, width: number, height: number, inside: boolean) {
  const grid = new Float64Array(width * height),
    tmp = new Float64Array(Math.max(width, height));
  const v = new Int32Array(tmp.length),
    z = new Float64Array(tmp.length + 1),
    dst = new Float64Array(tmp.length);
  function pass(length: number) {
    let k = 0;
    v[0] = 0;
    z[0] = -1e20;
    z[1] = 1e20;
    for (let q = 1; q < length; q++) {
      let s = (tmp[q] + q * q - (tmp[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
      while (s <= z[k] && k > 0) {
        k--;
        s = (tmp[q] + q * q - (tmp[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
      }
      k++;
      v[k] = q;
      z[k] = s;
      z[k + 1] = 1e20;
    }
    k = 0;
    for (let q = 0; q < length; q++) {
      while (z[k + 1] < q) k++;
      dst[q] = (q - v[k]) ** 2 + tmp[v[k]];
    }
  }
  for (let i = 0; i < grid.length; i++) grid[i] = mask[i] > 127 === inside ? 0 : 1e10;
  for (let x = 0; x < width; x++) {
    for (let y = 0; y < height; y++) tmp[y] = grid[y * width + x];
    pass(height);
    for (let y = 0; y < height; y++) grid[y * width + x] = dst[y];
  }
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) tmp[x] = grid[y * width + x];
    pass(width);
    for (let x = 0; x < width; x++) grid[y * width + x] = dst[x];
  }
  return grid;
}
export function sdfPixels(alpha: Uint8Array, width: number, height: number, spread = 16) {
  const toInside = distanceTransform(alpha, width, height, true),
    toOutside = distanceTransform(alpha, width, height, false);
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < alpha.length; i++) {
    const distance = alpha[i] > 127 ? -Math.sqrt(toOutside[i]) : Math.sqrt(toInside[i]);
    const value = Math.round(Math.max(0, Math.min(1, 0.5 + distance / (2 * spread))) * 255);
    rgba[i * 4] = value;
    rgba[i * 4 + 1] = value;
    rgba[i * 4 + 2] = value;
    rgba[i * 4 + 3] = 255;
  }
  return rgba;
}
