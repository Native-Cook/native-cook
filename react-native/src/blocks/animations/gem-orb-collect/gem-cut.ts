// A clipped heptagonal girdle, broad crown and two pavilion rings; all planes point outwards.
const SIDES = 7;
const SECTOR = (Math.PI * 2) / SIDES;
const APOTHEM = Math.cos(Math.PI / SIDES);
const CORNER = 0.935;
// Clipping the original vertices shortens the radius; renormalize the new vertices to exactly one.
const TANGENT = (CORNER - APOTHEM * Math.cos(Math.PI / SIDES)) / Math.sin(Math.PI / SIDES);
const CUT_RADIUS = Math.hypot(APOTHEM, TANGENT);

function makePlanes(): number[] {
  const planes: number[] = [];
  function plane(x: number, y: number, z: number, offset: number) {
    const magnitude = Math.hypot(x, y, z);
    planes.push(x / magnitude, y / magnitude, z / magnitude, offset / magnitude);
  }
  for (let i = 0; i < SIDES; i++) {
    const angle = Math.PI / 2 + (i + 0.5) * SECTOR;
    const x = Math.cos(angle);
    const y = Math.sin(angle);
    // The shallow crown and pavilion already imply the seven main girdle half-spaces.
    // Keep only the corner clips: the redundant main planes have no bounding vertices.
    plane(Math.cos(angle + SECTOR / 2), Math.sin(angle + SECTOR / 2), 0, CORNER / CUT_RADIUS);
    plane(x * 0.58, y * 0.58, 1, 0.28 + 0.50 * 0.58);
    plane(x, y, -0.80, 0.94);
    plane(Math.cos(angle + SECTOR / 2), Math.sin(angle + SECTOR / 2), -1.50, 1.32);
  }
  plane(0, 0, 1, 0.28);
  plane(0, 0, -1, 0.84);
  return planes;
}


// Build the cut's vertices/edges once. Project only silhouette edges on the UI thread;
// the shader then gets exact, stable screen-space coverage and a matching halo.
function makeTopology(planes: number[]): { vertices: number[]; edges: number[] } {
  const vertices: number[] = [];
  const edges: number[] = [];
  const count = planes.length / 4;
  for (let a = 0; a < count; a++) {
    for (let b = a + 1; b < count; b++) {
      for (let c = b + 1; c < count; c++) {
        const ai = a * 4, bi = b * 4, ci = c * 4;
        const ax = planes[ai], ay = planes[ai + 1], az = planes[ai + 2];
        const bx = planes[bi], by = planes[bi + 1], bz = planes[bi + 2];
        const cx = planes[ci], cy = planes[ci + 1], cz = planes[ci + 2];
        const ux = by * cz - bz * cy, uy = bz * cx - bx * cz, uz = bx * cy - by * cx;
        const determinant = ax * ux + ay * uy + az * uz;
        if (Math.abs(determinant) < 0.000001) continue;
        const ad = planes[ai + 3], bd = planes[bi + 3], cd = planes[ci + 3];
        const x = (ad * ux + bd * (cy * az - cz * ay) + cd * (ay * bz - az * by)) / determinant;
        const y = (ad * uy + bd * (cz * ax - cx * az) + cd * (az * bx - ax * bz)) / determinant;
        const z = (ad * uz + bd * (cx * ay - cy * ax) + cd * (ax * by - ay * bx)) / determinant;
        let valid = true;
        for (let p = 0; p < planes.length; p += 4) {
          if (planes[p] * x + planes[p + 1] * y + planes[p + 2] * z > planes[p + 3] + 0.000001) {
            valid = false;
            break;
          }
        }
        if (!valid) continue;
        for (let v = 0; v < vertices.length; v += 3) {
          if (Math.hypot(x - vertices[v], y - vertices[v + 1], z - vertices[v + 2]) < 0.00001) {
            valid = false;
            break;
          }
        }
        if (valid) vertices.push(x, y, z);
      }
    }
  }
  for (let a = 0; a < count; a++) {
    for (let b = a + 1; b < count; b++) {
      let first = -1;
      for (let v = 0; v < vertices.length; v += 3) {
        const x = vertices[v], y = vertices[v + 1], z = vertices[v + 2];
        const ai = a * 4, bi = b * 4;
        const onA = Math.abs(planes[ai] * x + planes[ai + 1] * y + planes[ai + 2] * z - planes[ai + 3]) < 0.00001;
        const onB = Math.abs(planes[bi] * x + planes[bi + 1] * y + planes[bi + 2] * z - planes[bi + 3]) < 0.00001;
        if (onA && onB) {
          if (first < 0) first = v;
          else { edges.push(first, v, a, b); break; }
        }
      }
    }
  }
  return { vertices, edges };
}

const rawPlanes = makePlanes();
const rawTopology = makeTopology(rawPlanes);
let radialExtent = 0;
for (let v = 0; v < rawTopology.vertices.length; v += 3) {
  radialExtent = Math.max(radialExtent, Math.hypot(rawTopology.vertices[v], rawTopology.vertices[v + 1]));
}
// Normalize the actual cut, including crown/pavilion intersections, to circumradius exactly one.
export const GEM_PLANES: number[] = [];
for (let p = 0; p < rawPlanes.length; p += 4) {
  const x = rawPlanes[p] * radialExtent, y = rawPlanes[p + 1] * radialExtent, z = rawPlanes[p + 2];
  const magnitude = Math.hypot(x, y, z);
  GEM_PLANES.push(x / magnitude, y / magnitude, z / magnitude, rawPlanes[p + 3] / magnitude);
}
const topology = makeTopology(GEM_PLANES);
export const GEM_OUTLINE_PLANES = 32;

// Real convex vertices and averaged adjacent normals, shared by the one-draw glint layer.
export const GEM_CORNERS: number[] = [];
for (let v = 0; v < topology.vertices.length; v += 3) {
  const x = topology.vertices[v], y = topology.vertices[v + 1], z = topology.vertices[v + 2];
  let nx = 0, ny = 0, nz = 0;
  for (let p = 0; p < GEM_PLANES.length; p += 4) {
    if (Math.abs(GEM_PLANES[p] * x + GEM_PLANES[p + 1] * y + GEM_PLANES[p + 2] * z - GEM_PLANES[p + 3]) < 0.00001) {
      nx += GEM_PLANES[p]; ny += GEM_PLANES[p + 1]; nz += GEM_PLANES[p + 2];
    }
  }
  const magnitude = Math.max(Math.hypot(nx, ny, nz), 0.00001);
  GEM_CORNERS.push(x, y, z, nx / magnitude, ny / magnitude, nz / magnitude);
}

export function makeOutlineScratch() {
  return {
    projected: new Array<number>(topology.vertices.length).fill(0),
    visible: new Array<boolean>(GEM_PLANES.length / 4).fill(false),
    result: new Array<number>(GEM_OUTLINE_PLANES * 3).fill(0),
  };
}

/** Project the convex cut's silhouette into the shader's unstretched view plane. */
export function gemOutline(row0: number[], row1: number[], row2: number[], camera: number, scratch?: ReturnType<typeof makeOutlineScratch>): number[] {
  'worklet';
  const projected = scratch ? scratch.projected : new Array<number>(topology.vertices.length);
  const visible = scratch ? scratch.visible : new Array<boolean>(GEM_PLANES.length / 4);
  const result = scratch ? scratch.result : new Array<number>(GEM_OUTLINE_PLANES * 3);
  let used = 0;
  for (let v = 0; v < topology.vertices.length; v += 3) {
    const x = topology.vertices[v], y = topology.vertices[v + 1], z = topology.vertices[v + 2];
    const viewZ = row2[0] * x + row2[1] * y + row2[2] * z;
    const projection = camera / Math.max(camera - viewZ, 0.001);
    projected[v] = (row0[0] * x + row0[1] * y + row0[2] * z) * projection;
    projected[v + 1] = (row1[0] * x + row1[1] * y + row1[2] * z) * projection;
    projected[v + 2] = 0;
  }
  for (let p = 0; p < GEM_PLANES.length; p += 4) {
    visible[p / 4] = camera * (row2[0] * GEM_PLANES[p] + row2[1] * GEM_PLANES[p + 1] + row2[2] * GEM_PLANES[p + 2]) > GEM_PLANES[p + 3];
  }
  for (let e = 0; e < topology.edges.length; e += 4) {
    if (visible[topology.edges[e + 2]] === visible[topology.edges[e + 3]]) continue;
    const a = topology.edges[e], b = topology.edges[e + 1];
    let nx = projected[b + 1] - projected[a + 1];
    let ny = projected[a] - projected[b];
    const magnitude = Math.hypot(nx, ny);
    if (magnitude < 0.000001) continue;
    nx /= magnitude;
    ny /= magnitude;
    const offset = nx * projected[a] + ny * projected[a + 1];
    const sign = offset < 0 ? -1 : 1;
    result[used++] = nx * sign; result[used++] = ny * sign; result[used++] = offset * sign;
  }
  for (let i = used; i < GEM_OUTLINE_PLANES * 3; i += 3) { result[i] = 0; result[i + 1] = 0; result[i + 2] = 10; }
  return result;
}
