import { BLOB_COUNT, BLOB_DT, BLOB_MIN_A, BLOB_MIN_B, BLOB_PERIOD } from "./flare-blobs";

// One frame of a pill's flame: interpolates the fitted blob keys at time t
// (cyclic Catmull-Rom, the same curve the fit used) into the shader's uniform
// arrays. Runs in a worklet every frame; 32 blobs x 9 numbers is cheap.
export function blobFrame(keys: readonly number[], t: number) {
  "worklet";
  const N = BLOB_COUNT;
  const K = Math.round(BLOB_PERIOD / BLOB_DT);
  let x = (t / BLOB_DT) % K;
  if (x < 0) x += K;
  const i1 = Math.floor(x);
  const f = x - i1;
  const f2 = f * f;
  const f3 = f2 * f;
  const k0 = ((i1 - 1 + K) % K) * N * 9;
  const k1 = (i1 % K) * N * 9;
  const k2 = ((i1 + 1) % K) * N * 9;
  const k3 = ((i1 + 2) % K) * N * 9;
  const at = (n: number, c: number) => {
    const p0 = keys[k0 + n * 9 + c];
    const p1 = keys[k1 + n * 9 + c];
    const p2 = keys[k2 + n * 9 + c];
    const p3 = keys[k3 + n * 9 + c];
    return 0.5 * (2 * p1 + (p2 - p0) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (3 * p1 - p0 - 3 * p2 + p3) * f3);
  };
  const geo: number[] = new Array(N * 4);
  const col: number[] = new Array(N * 4);
  const rot: number[] = new Array(N * 2);
  for (let n = 0; n < N; n++) {
    geo[n * 4] = at(n, 0);
    geo[n * 4 + 1] = at(n, 1);
    geo[n * 4 + 2] = BLOB_MIN_A + Math.exp(at(n, 2));
    geo[n * 4 + 3] = BLOB_MIN_B + Math.exp(at(n, 3));
    const ang = at(n, 4);
    rot[n * 2] = Math.cos(ang);
    rot[n * 2 + 1] = Math.sin(ang);
    col[n * 4] = at(n, 6);
    col[n * 4 + 1] = at(n, 7);
    col[n * 4 + 2] = at(n, 8);
    col[n * 4 + 3] = 1 / (1 + Math.exp(-at(n, 5)));
  }
  return { geo, col, rot };
}
