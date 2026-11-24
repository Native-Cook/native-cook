/*
 Address Lookup Map — the data: the addresses the lookup knows and the invented town it draws.

 Everything is made up: street names, districts, postcodes. The town is laid out in "world units"
 (a 1200 × 1200 square); StreetMap.tsx draws it and the camera looks at it with a centre and a scale.
*/

export type Address = {
  /** House number and street, as typed and shown in the field. */
  street: string;
  /** Second line of a suggestion. */
  area: string;
  unit: string;
  postcode: string;
  city: string;
  /** Front door in world units. */
  x: number;
  y: number;
};

export const ADDRESSES: Address[] = [
  { street: '48 Larkspur Lane', area: 'Fernhill, Ashby', unit: 'Flat 3B', postcode: '2041', city: 'Ashby', x: 606, y: 541 },
  { street: '48 Larkfield Mews', area: 'Ashby North', unit: 'Unit 2', postcode: '2043', city: 'Ashby', x: 541, y: 742 },
  { street: '148 Larkin Street', area: 'Old Quay, Ashby', unit: 'Apt 12', postcode: '2038', city: 'Ashby', x: 428, y: 821 },
  { street: '48 Lark Rise', area: 'Wendle, Bramley', unit: 'House', postcode: '2050', city: 'Bramley', x: 806, y: 872 },
  { street: '4 Linton Parade', area: 'Fernhill, Ashby', unit: 'Shop 4', postcode: '2041', city: 'Ashby', x: 214, y: 681 },
  { street: '248 Lowther Road', area: 'Bramley', unit: 'Apt 7', postcode: '2046', city: 'Bramley', x: 978, y: 961 },
  { street: '40 Larch Close', area: 'Fernhill, Ashby', unit: 'Flat 1', postcode: '2041', city: 'Ashby', x: 921, y: 446 },
  { street: '84 Lantern Row', area: 'Old Quay, Ashby', unit: 'Loft 5', postcode: '2038', city: 'Ashby', x: 161, y: 604 },
];

export const MAX_ROWS = 4;

function houseNumber(street: string) {
  const n = parseInt(street, 10);
  return Number.isFinite(n) ? n : 1e6;
}

/**
 The suggestions for a query: addresses whose street starts with it first (by house number), then
 those that contain it, at most MAX_ROWS. Indices into `list`.
*/
export function rank(query: string, list: Address[] = ADDRESSES): number[] {
  const q = query.trim().toLowerCase();
  if (q.length === 0) return [];
  const starts: number[] = [];
  const contains: number[] = [];
  list.forEach((a, i) => {
    const s = a.street.toLowerCase();
    if (s.startsWith(q)) starts.push(i);
    else if (s.includes(q) || a.area.toLowerCase().includes(q)) contains.push(i);
  });
  starts.sort((a, b) => houseNumber(list[a].street) - houseNumber(list[b].street) || a - b);
  return [...starts, ...contains].slice(0, MAX_ROWS);
}

/** The stretch of `text` that matches the query, for the bold letters of a suggestion. */
export function matchRange(text: string, query: string): [number, number] | null {
  const q = query.trim().toLowerCase();
  if (q.length === 0) return null;
  const at = text.toLowerCase().indexOf(q);
  return at < 0 ? null : [at, at + q.length];
}

// ---- The town ----------------------------------------------------------------------------------

export const WORLD = 1200;

export type Road = { name: string; points: [number, number][]; label: [number, number]; angle: number };

/** Residential streets, 12 units wide. Labels sit on the road, rotated along it (degrees). */
export const STREETS: Road[] = [
  { name: 'Quay Road', points: [[40, 300], [1160, 300]], label: [430, 300], angle: 0 },
  { name: 'Ferry Street', points: [[60, 380], [1140, 380]], label: [430, 380], angle: 0 },
  { name: 'Larkspur Lane', points: [[60, 520], [1140, 520]], label: [430, 520], angle: 0 },
  { name: 'Linton Parade', points: [[60, 660], [1140, 660]], label: [430, 660], angle: 0 },
  { name: 'Larkin Street', points: [[60, 800], [1140, 800]], label: [620, 800], angle: 0 },
  { name: 'Lowther Road', points: [[60, 940], [1140, 940]], label: [620, 940], angle: 0 },
  { name: 'Mill Row', points: [[60, 1080], [1140, 1080]], label: [620, 1080], angle: 0 },
  { name: 'Lantern Row', points: [[140, 300], [140, 1160]], label: [140, 590], angle: -90 },
  { name: 'Wendle Way', points: [[330, 300], [330, 1160]], label: [330, 590], angle: -90 },
  { name: 'Larkfield Mews', points: [[520, 380], [520, 1160]], label: [520, 730], angle: -90 },
  { name: 'Orchard Walk', points: [[710, 300], [710, 1160]], label: [710, 450], angle: -90 },
  { name: 'Larch Close', points: [[900, 380], [900, 520]], label: [900, 450], angle: -90 },
  { name: 'Bell Street', points: [[1080, 300], [1080, 1160]], label: [1080, 590], angle: -90 },
];

/** The crescent of Lark Rise, a quadratic curve inside its block. */
export const CRESCENT = { from: [730, 830] as [number, number], ctrl: [805, 905] as [number, number], to: [880, 830] as [number, number] };

/** The one main road, wider and yellow, crossing the grid. */
export const MAIN_ROAD: Road = { name: 'Ashby Road', points: [[0, 1150], [1200, 470]], label: [880, 652], angle: -29.5 };

/** Blocks between streets that are parks (x0, y0, x1, y1 between street centres). */
export const PARKS: [number, number, number, number, string][] = [
  [710, 520, 900, 660, 'Fernhill Green'],
  [140, 940, 330, 1080, 'Mill Fields'],
];

export const DISTRICTS: { name: string; x: number; y: number }[] = [
  { name: 'Old Quay', x: 600, y: 112 },
  { name: 'Fernhill', x: 420, y: 600 },
  { name: 'Ashby', x: 880, y: 1010 },
  { name: 'Bramley', x: 236, y: 880 },
];

/** River banks: a band of water across the north of the town. */
export const RIVER = {
  north: [[-20, 196], [300, 236], [620, 168], [900, 214], [1220, 184]] as [number, number][],
  south: [[-20, 262], [300, 290], [620, 236], [900, 270], [1220, 246]] as [number, number][],
  label: [760, 228] as [number, number],
};

/** Seeded hash, 0 ..1. */
export function hash(n: number) {
  const s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

const XS = [140, 330, 520, 710, 900, 1080];
const YS = [300, 380, 520, 660, 800, 940, 1080];

/**
 House footprints: rows of small rectangles along the edges of every block, seeded so the town is
 the same each time. Returns [x, y, w, h] in world units.
*/
export function houses(): [number, number, number, number][] {
  const out: [number, number, number, number][] = [];
  const edge = 13;
  const depth = 26;
  let seed = 1;
  const along = (x0: number, y0: number, len: number, horizontal: boolean, inward: number) => {
    let p = 0;
    while (p < len - 16) {
      const w = Math.min(18 + hash(seed++) * 22, len - p);
      const d = depth - hash(seed++) * 7;
      if (hash(seed++) > 0.1) {
        if (horizontal) out.push([x0 + p, inward > 0 ? y0 : y0 - d, w, d]);
        else out.push([inward > 0 ? x0 : x0 - d, y0 + p, d, w]);
      }
      p += w + 3 + hash(seed++) * 3;
    }
  };
  const blockCols = [...XS.slice(0, -1).map((x, i) => [x, XS[i + 1]]), [40, 140], [1080, 1160]];
  for (const [xa, xb] of blockCols) {
    for (let j = 0; j < YS.length - 1; j += 1) {
      const ya = YS[j];
      const yb = YS[j + 1];
      if (xa === 900 && ya === 380) continue; // Larch Close splits this block; its houses are below.
      if (PARKS.some((p) => p[0] === xa && p[1] === ya)) continue;
      if (j === 0 && (xa === 40 || xa === 1080)) continue;
      const x0 = xa + edge;
      const x1 = xb - edge;
      const y0 = ya + edge;
      const y1 = yb - edge;
      if (x1 - x0 < 40 || y1 - y0 < 40) continue;
      const crescent = xa === 710 && ya === 800; // Lark Rise curves through this block's north half.
      if (!crescent) along(x0, y0, x1 - x0, true, 1);
      along(x0, y1, x1 - x0, true, -1);
      if (!crescent && y1 - y0 > depth * 2 + 20) {
        along(x0, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, 1);
        along(x1, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, -1);
      }
    }
  }
  // Larch Close: two short rows either side of the cul-de-sac.
  along(913, 393, 154, true, 1);
  along(913, 507, 154, true, -1);
  // Lark Rise: houses facing the crescent from its outer side.
  for (let k = 0; k < 6; k += 1) {
    const u = (k + 0.5) / 6;
    const a = (1 - u) * (1 - u);
    const b = 2 * u * (1 - u);
    const c = u * u;
    const x = a * CRESCENT.from[0] + b * CRESCENT.ctrl[0] + c * CRESCENT.to[0];
    const y = a * CRESCENT.from[1] + b * CRESCENT.ctrl[1] + c * CRESCENT.to[1];
    out.push([x - 10, y + 12, 20, 22 - hash(k + 300) * 5]);
  }
  // Old Quay: warehouses north of the river.
  for (let i = 0; i < 9; i += 1) {
    const x = 30 + i * 130 + hash(i + 400) * 20;
    out.push([x, 40 + hash(i + 410) * 30, 80 + hash(i + 420) * 30, 60 + hash(i + 430) * 40]);
  }
  return out;
}
