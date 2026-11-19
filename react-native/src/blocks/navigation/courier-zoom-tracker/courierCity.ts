// The invented city the courier drives through, in metres (x east, y south, like the screen). Everything here is
// built once on the JS thread: the street grid, the river, the parks, about 4,600 building footprints with heights,
// the courier's route (along the streets, corners rounded, on the right-hand lane) and the static map picture.
// Seeded, so every run and every port draws the same city.

import { PaintStyle, Skia, StrokeCap } from "@shopify/react-native-skia";
import type { SkPicture } from "@shopify/react-native-skia";

/** Block pitch: streets every 120 m east–west and every 90 m north–south. */
export const BX = 120;
export const BY = 90;
/** Block indices: i in [I0, I1) across, j in [J0, J1) down; block (i, j) spans x i*BX..(i+1)*BX, y j*BY..(j+1)*BY. */
export const I0 = -7;
export const I1 = 33;
export const J0 = -10;
export const J1 = 44;
export const NJT = J1 - J0;
/** The city's extent in metres. */
export const CITY = { x0: I0 * BX, y0: J0 * BY, x1: I1 * BX, y1: J1 * BY } as const;
/** The street grid carries on past the buildings into the outskirts, so the map never ends on a wide screen. */
const OI0 = -22;
const OI1 = 48;
const OJ0 = -24;
const OJ1 = 58;

export function blockIndex(i: number, j: number): number {
  "worklet";
  return (i - I0) * NJT + (j - J0);
}

/** Street widths (m). */
const MINOR = 14;
const AVENUE = 24;
/** The avenues: the street x = AVE_I * BX and the street y = AVE_J * BY. */
export const AVE_I = [7, 17];
export const AVE_J = [14, 33];
/** Streets that cross the river on a bridge. */
export const BRIDGES = [3, 7, 12, 17, 22];
export const RIVER_HALF = 72;

export const PALETTE = {
  land: "#F2EFE9",
  block: "#EDE9E1",
  park: "#CFE5C2",
  parkPath: "#E4F0DA",
  water: "#AFD3EC",
  waterEdge: "#9DC6E2",
  casing: "#DDD7CC",
  minor: "#FFFFFF",
  aveCasing: "#E6D6A5",
  avenue: "#FFF0C4",
  aveLine: "#E9D79E",
  footprint: "#E5E0D7",
  garden: "#D9EBCB",
  tree: "#B8D9A4",
  treeDark: "#A5CC8F",
  zebra: "#FFFFFF",
} as const;

export function hash(n: number): number {
  "worklet";
  const s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** The river's centre line, y at x. */
export function riverY(x: number): number {
  "worklet";
  return 2310 + 120 * Math.sin(x / 700) + 55 * Math.sin(x / 260 + 1);
}

function isAveI(i: number): boolean {
  return AVE_I.indexOf(i) >= 0;
}
function isAveJ(j: number): boolean {
  return AVE_J.indexOf(j) >= 0;
}

/** Blocks that are a park (no buildings): Fernhill Park, the squares and the river banks. */
export function parkKind(i: number, j: number): number {
  if (i >= 2 && i <= 5 && j >= 30 && j <= 35) return 1; // Fernhill Park
  // the squares; the one across Alder Street from the customer keeps the view of the door open
  if ((i === 10 && j === 20) || (i === 19 && j === 40) || (i === 14 && j === 5) || (i === 22 && j === 27) || (i === 18 && j === 11) || (i === 19 && j === 8)) return 1;
  const cx = (i + 0.5) * BX;
  const cy = (j + 0.5) * BY;
  const d = Math.abs(cy - riverY(cx));
  if (d < RIVER_HALF + 50) return 2; // under or right at the water: bank
  if (d < RIVER_HALF + 120) return 1; // embankment green
  return 0;
}

// ---------------------------------------------------------------- the courier's home stop

/** The customer's townhouse (x0, y0, x1, y1, height) on Alder Street (y = 8 * BY), its door facing the street. */
export const HOME = { x0: 2313, y0: 698, x1: 2325, y1: 710, h: 10 } as const;
export const DOOR_X = 2319;
/** Where the van stops: the north lane of Alder Street, in front of the door. */
export const STOP = { x: 2319, y: 716.5 } as const;
/** The courier's hub, where the route starts. */
export const HUB = { x: 843.5, y: 2700 } as const;

// ---------------------------------------------------------------- buildings

/** Footprints and heights, five numbers each: x0, y0, x1, y1, h. Grouped by block (blockIndex). */
export const BUILDINGS: number[] = [];
/** BLOCK_START[blockIndex(i, j)] .. BLOCK_START[blockIndex(i, j) + 1] index BUILDINGS / 5 for that block. */
export const BLOCK_START: number[] = [];
let homeIndex = -1;

function pushBuilding(x0: number, y0: number, x1: number, y1: number, h: number): void {
  BUILDINGS.push(x0, y0, x1, y1, h);
}

function heightAt(cx: number, cy: number, seed: number): number {
  // downtown by the river is taller; the north-east, where the customer lives, is low and leafy
  const dt = Math.hypot(cx - 1500, cy - 2050);
  const town = Math.max(0, 1 - dt / 1100);
  const r = hash(seed);
  const base = 7 + 8 * r;
  return Math.round(base + town * town * (16 + 30 * hash(seed + 3.1)));
}

/** The customer's neighbourhood: rows of townhouses with back gardens. */
export function isTerrace(i: number, j: number): boolean {
  return i >= 18 && i <= 20 && j >= 6 && j <= 8;
}
/** Depth of a terrace row (m). */
export const ROW_DEPTH = 12;

/** One row of attached townhouses between x0 and x1, from y0 to y1, 10–13 m wide each. */
function terraceRow(x0: number, x1: number, y0: number, y1: number, seed: number): void {
  let x = x0;
  let k = 0;
  while (x1 - x > 7) {
    let w = 10 + 3 * hash(seed + k * 1.7);
    if (x1 - x - w < 8) w = x1 - x;
    pushBuilding(x + 0.15, y0, x + w - 0.15, y1, Math.round((8.5 + 3 * hash(seed + k * 2.3 + 0.5)) * 2) / 2);
    x += w;
    k++;
  }
}

function terraceBlock(i: number, j: number, x0: number, y0: number, x1: number, y1: number): void {
  const seed = i * 53 + j * 11;
  // the north row faces the street above, the south row the street below
  terraceRow(x0, x1, y0, y0 + ROW_DEPTH, seed);
  if (i === 19 && j === 7) {
    // the customer's row on Alder Street, with their house where the van stops
    terraceRow(x0, HOME.x0, y1 - ROW_DEPTH, y1, seed + 40);
    homeIndex = BUILDINGS.length / 5;
    pushBuilding(HOME.x0 + 0.15, HOME.y0, HOME.x1 - 0.15, HOME.y1, HOME.h);
    terraceRow(HOME.x1, x1, y1 - ROW_DEPTH, y1, seed + 80);
  } else {
    terraceRow(x0, x1, y1 - ROW_DEPTH, y1, seed + 40);
  }
}

function buildBlocks(): void {
  for (let i = I0; i < I1; i++) {
    for (let j = J0; j < J1; j++) {
      BLOCK_START.push(BUILDINGS.length / 5);
      if (parkKind(i, j) > 0) continue;
      const hw = (s: boolean) => (s ? AVENUE / 2 : MINOR / 2) + 3;
      const x0 = i * BX + hw(isAveI(i));
      const x1 = (i + 1) * BX - hw(isAveI(i + 1));
      const y0 = j * BY + hw(isAveJ(j));
      const y1 = (j + 1) * BY - hw(isAveJ(j + 1));
      if (isTerrace(i, j)) {
        terraceBlock(i, j, x0, y0, x1, y1);
        continue;
      }
      const seed = i * 131 + j * 17;
      const layout = hash(seed + 0.7);
      const cols = layout < 0.55 ? 2 : layout < 0.8 ? 3 : 1;
      const rows = hash(seed + 1.9) < 0.7 ? 2 : 1;
      const cw = (x1 - x0) / cols;
      const ch = (y1 - y0) / rows;
      for (let a = 0; a < cols; a++) {
        for (let b = 0; b < rows; b++) {
          const k = seed * 7 + a * 3 + b * 5;
          if (hash(k + 4.4) < 0.07) continue; // a courtyard or a car park
          const g = 1.5;
          // set back from the street a little, more on the courtyard side
          const sx0 = x0 + a * cw + (a === 0 ? 0 : g) + 2 * hash(k + 1.1);
          const sx1 = x0 + (a + 1) * cw - (a === cols - 1 ? 0 : g) - 2 * hash(k + 2.2);
          const sy0 = y0 + b * ch + (b === 0 ? 0 : g) + (rows === 2 && b === 1 ? 3 * hash(k + 5.5) : 0);
          const sy1 = y0 + (b + 1) * ch - (b === rows - 1 ? 0 : g) - (rows === 2 && b === 0 ? 3 * hash(k + 6.6) : 0);
          pushBuilding(sx0, sy0, sx1, sy1, heightAt((sx0 + sx1) / 2, (sy0 + sy1) / 2, k));
        }
      }
    }
  }
  BLOCK_START.push(BUILDINGS.length / 5);
}
buildBlocks();
/** The home building's index in BUILDINGS / 5. */
export const HOME_INDEX = homeIndex;

// ---------------------------------------------------------------- the route

/** Street corners from the hub to Alder Street (centre lines). */
const WAYPOINTS = [
  [7 * BX, 30 * BY],
  [7 * BX, 14 * BY],
  [17 * BX, 14 * BY],
  [17 * BX, 10 * BY],
  [21 * BX, 10 * BY],
  [21 * BX, 8 * BY],
  [DOOR_X, 8 * BY],
];
const FILLET = 26;
const LANE = 3.5;
/** Spacing of the route samples (m). */
export const ROUTE_STEP = 3;

function buildRoute(): { x: number[]; y: number[]; len: number } {
  // a dense polyline with rounded corners
  const pts: number[][] = [];
  const add = (x: number, y: number) => pts.push([x, y]);
  add(WAYPOINTS[0][0], WAYPOINTS[0][1]);
  for (let k = 1; k < WAYPOINTS.length - 1; k++) {
    const [ax, ay] = WAYPOINTS[k - 1];
    const [bx, by] = WAYPOINTS[k];
    const [cx, cy] = WAYPOINTS[k + 1];
    const l1 = Math.hypot(bx - ax, by - ay);
    const l2 = Math.hypot(cx - bx, cy - by);
    const p0 = [bx - ((bx - ax) / l1) * FILLET, by - ((by - ay) / l1) * FILLET];
    const p2 = [bx + ((cx - bx) / l2) * FILLET, by + ((cy - by) / l2) * FILLET];
    for (let s = 0; s <= 12; s++) {
      const u = s / 12;
      // quadratic Bezier through the corner: close to a circular arc for a right angle
      const x = (1 - u) * (1 - u) * p0[0] + 2 * (1 - u) * u * bx + u * u * p2[0];
      const y = (1 - u) * (1 - u) * p0[1] + 2 * (1 - u) * u * by + u * u * p2[1];
      add(x, y);
    }
  }
  const last = WAYPOINTS[WAYPOINTS.length - 1];
  add(last[0], last[1]);
  // resample by arc length
  const cum = [0];
  for (let k = 1; k < pts.length; k++) cum.push(cum[k - 1] + Math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]));
  const total = cum[cum.length - 1];
  const n = Math.floor(total / ROUTE_STEP);
  const cx: number[] = [];
  const cy: number[] = [];
  let seg = 0;
  for (let s = 0; s <= n; s++) {
    const d = s === n ? total : s * ROUTE_STEP;
    while (seg < cum.length - 2 && cum[seg + 1] < d) seg++;
    const k = (d - cum[seg]) / Math.max(cum[seg + 1] - cum[seg], 1e-6);
    cx.push(pts[seg][0] + (pts[seg + 1][0] - pts[seg][0]) * k);
    cy.push(pts[seg][1] + (pts[seg + 1][1] - pts[seg][1]) * k);
  }
  // keep to the right-hand lane: offset along the normal on the right of travel
  const x: number[] = [];
  const y: number[] = [];
  for (let s = 0; s < cx.length; s++) {
    const a = Math.max(s - 2, 0);
    const b = Math.min(s + 2, cx.length - 1);
    const hx = cx[b] - cx[a];
    const hy = cy[b] - cy[a];
    const hl = Math.hypot(hx, hy) || 1;
    x.push(cx[s] + (-hy / hl) * LANE);
    y.push(cy[s] + (hx / hl) * LANE);
  }
  // the last sample is the stop itself
  x[x.length - 1] = STOP.x;
  y[y.length - 1] = STOP.y;
  return { x, y, len: total };
}

const ROUTE = buildRoute();
/** Route samples every ROUTE_STEP metres (the last one is the stop). */
export const ROUTE_X = ROUTE.x;
export const ROUTE_Y = ROUTE.y;
export const ROUTE_LEN = (ROUTE_X.length - 1) * ROUTE_STEP;

/** The route as a path in metres (for the blue line, trimmed by the travelled fraction). */
export function makeRoutePath() {
  const p = Skia.PathBuilder.Make();
  p.moveTo(ROUTE_X[0], ROUTE_Y[0]);
  for (let k = 1; k < ROUTE_X.length; k++) p.lineTo(ROUTE_X[k], ROUTE_Y[k]);
  return p.build();
}

// ---------------------------------------------------------------- the static map

export function streetInWater(x0: number, y0: number, x1: number, y1: number): boolean {
  for (let s = 0; s <= 4; s++) {
    const x = x0 + ((x1 - x0) * s) / 4;
    const y = y0 + ((y1 - y0) * s) / 4;
    if (Math.abs(y - riverY(x)) < RIVER_HALF + 4) return true;
  }
  return false;
}

/** Land, parks, river, streets (avenues on top, bridges over the water), trees and crossings near the customer, and
 * every building's footprint: one picture in metres, drawn under the camera's matrix. */
export function makeCityPicture(): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(OI0 * BX, OJ0 * BY, (OI1 - OI0) * BX, (OJ1 - OJ0) * BY));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(PALETTE.land));
  c.drawRect(Skia.XYWHRect(OI0 * BX - 3000, OJ0 * BY - 3000, (OI1 - OI0) * BX + 6000, (OJ1 - OJ0) * BY + 6000), paint);

  // parks
  paint.setColor(Skia.Color(PALETTE.park));
  for (let i = I0; i < I1; i++) {
    for (let j = J0; j < J1; j++) {
      if (parkKind(i, j) > 0) c.drawRect(Skia.XYWHRect(i * BX - 1, j * BY - 1, BX + 2, BY + 2), paint);
    }
  }
  // the pond and winding paths in Fernhill Park
  paint.setColor(Skia.Color(PALETTE.water));
  c.drawOval(Skia.XYWHRect(3 * BX + 10, 31 * BY + 20, 170, 110), paint);
  const paths = Skia.PathBuilder.Make();
  paths.moveTo(2 * BX, 33.6 * BY);
  paths.cubicTo(3 * BX, 32 * BY, 4.6 * BX, 36 * BY, 6 * BX, 33 * BY);
  paths.moveTo(4.4 * BX, 30 * BY);
  paths.cubicTo(4 * BX, 32 * BY, 5.4 * BX, 34 * BY, 4.6 * BX, 36 * BY);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(5);
  paint.setColor(Skia.Color(PALETTE.parkPath));
  c.drawPath(paths.build(), paint);
  paint.setStyle(PaintStyle.Fill);

  // the river: a band along its centre line
  const river = Skia.PathBuilder.Make();
  const xs: number[] = [];
  for (let x = OI0 * BX; x <= OI1 * BX; x += 20) xs.push(x);
  river.moveTo(xs[0], riverY(xs[0]) - RIVER_HALF);
  for (let k = 1; k < xs.length; k++) river.lineTo(xs[k], riverY(xs[k]) - RIVER_HALF);
  for (let k = xs.length - 1; k >= 0; k--) river.lineTo(xs[k], riverY(xs[k]) + RIVER_HALF);
  river.close();
  const riverPath = river.build();
  paint.setColor(Skia.Color(PALETTE.water));
  c.drawPath(riverPath, paint);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(3);
  paint.setColor(Skia.Color(PALETTE.waterEdge));
  c.drawPath(riverPath, paint);

  // streets: one path per kind, casings first
  const minor = Skia.PathBuilder.Make();
  const aves = Skia.PathBuilder.Make();
  for (let i = OI0; i <= OI1; i++) {
    for (let j = OJ0; j < OJ1; j++) {
      const x = i * BX;
      if (streetInWater(x, j * BY, x, (j + 1) * BY) && BRIDGES.indexOf(i) < 0) continue;
      const p = isAveI(i) ? aves : minor;
      p.moveTo(x, j * BY);
      p.lineTo(x, (j + 1) * BY);
    }
  }
  for (let j = OJ0; j <= OJ1; j++) {
    for (let i = OI0; i < OI1; i++) {
      const y = j * BY;
      if (streetInWater(i * BX, y, (i + 1) * BX, y)) continue;
      const p = isAveJ(j) ? aves : minor;
      p.moveTo(i * BX, y);
      p.lineTo((i + 1) * BX, y);
    }
  }
  const minorPath = minor.build();
  const avesPath = aves.build();
  paint.setStrokeCap(StrokeCap.Square);
  paint.setColor(Skia.Color(PALETTE.casing));
  paint.setStrokeWidth(MINOR + 2);
  c.drawPath(minorPath, paint);
  paint.setColor(Skia.Color(PALETTE.minor));
  paint.setStrokeWidth(MINOR);
  c.drawPath(minorPath, paint);
  paint.setColor(Skia.Color(PALETTE.aveCasing));
  paint.setStrokeWidth(AVENUE + 2.5);
  c.drawPath(avesPath, paint);
  paint.setColor(Skia.Color(PALETTE.avenue));
  paint.setStrokeWidth(AVENUE);
  c.drawPath(avesPath, paint);
  paint.setStrokeCap(StrokeCap.Butt);
  paint.setColor(Skia.Color(PALETTE.aveLine));
  paint.setStrokeWidth(0.7);
  // the centre line dashed once here: a dash path effect in the picture was re-run over the whole avenue network
  // (about 2,400 dashes) on every frame the map moved, most of all with the whole city in view
  const centre = Skia.Path.Dash(avesPath, 6, 7, 0);
  if (centre) c.drawPath(centre, paint);

  // zebra crossings at the corners round the customer's street
  paint.setStyle(PaintStyle.Fill);
  paint.setColor(Skia.Color(PALETTE.zebra));
  const zebraAt = (x: number, y: number, vertical: boolean) => {
    for (let s = -3; s <= 3; s++) {
      if (vertical) c.drawRect(Skia.XYWHRect(x + s * 1.8 - 0.45, y - 2.5, 0.9, 5), paint);
      else c.drawRect(Skia.XYWHRect(x - 2.5, y + s * 1.8 - 0.45, 5, 0.9), paint);
    }
  };
  const crossings: number[][] = [
    [19, 8],
    [20, 8],
    [21, 8],
    [21, 9],
    [20, 7],
    [19, 9],
  ];
  for (const [i, j] of crossings) {
    const x = i * BX;
    const y = j * BY;
    zebraAt(x - 12, y, false);
    zebraAt(x + 12, y, false);
    zebraAt(x, y - 12, true);
    zebraAt(x, y + 12, true);
  }

  // street trees round the customer's neighbourhood
  for (let i = 16; i <= 23; i++) {
    for (let j = 5; j <= 11; j++) {
      if (parkKind(i, j) > 0) continue;
      const x0 = i * BX;
      const y0 = j * BY;
      for (let s = 14; s < BX - 10; s += 13) {
        const k = i * 977 + j * 31 + s;
        const r = 1.7 + 0.7 * hash(k);
        const ty = y0 + MINOR / 2 + 1.3;
        paint.setColor(Skia.Color(PALETTE.treeDark));
        c.drawCircle(x0 + s + 0.6, ty + 0.6, r, paint);
        paint.setColor(Skia.Color(PALETTE.tree));
        c.drawCircle(x0 + s, ty, r, paint);
      }
    }
  }

  // back gardens behind the townhouses, with a tree here and there
  for (let i = I0; i < I1; i++) {
    for (let j = J0; j < J1; j++) {
      if (!isTerrace(i, j) || parkKind(i, j) > 0) continue;
      const gx0 = i * BX + MINOR / 2 + 3;
      const gy0 = j * BY + MINOR / 2 + 3 + ROW_DEPTH;
      const gw = BX - MINOR - 6;
      const gh = BY - MINOR - 6 - ROW_DEPTH * 2;
      paint.setColor(Skia.Color(PALETTE.garden));
      c.drawRect(Skia.XYWHRect(gx0, gy0, gw, gh), paint);
      for (let k = 0; k < 9; k++) {
        const s = i * 31 + j * 7 + k;
        const tx = gx0 + 5 + (gw - 10) * hash(s + 0.3);
        const ty = gy0 + 4 + (gh - 8) * hash(s + 0.9);
        const r = 2 + 1.5 * hash(s + 1.7);
        paint.setColor(Skia.Color(PALETTE.treeDark));
        c.drawCircle(tx + 0.7, ty + 0.7, r, paint);
        paint.setColor(Skia.Color(PALETTE.tree));
        c.drawCircle(tx, ty, r, paint);
      }
    }
  }
  // trees in the pocket park across from the customer
  for (let k = 0; k < 26; k++) {
    const tx = 19 * BX + 14 + (BX - 28) * hash(k * 3.1 + 0.2);
    const ty = 8 * BY + 14 + (BY - 28) * hash(k * 5.3 + 0.7);
    const r = 2.6 + 1.8 * hash(k * 7.7);
    paint.setColor(Skia.Color(PALETTE.treeDark));
    c.drawCircle(tx + 0.8, ty + 0.8, r, paint);
    paint.setColor(Skia.Color(PALETTE.tree));
    c.drawCircle(tx, ty, r, paint);
  }

  // footprints (seen flat from far away; up close the extruded buildings cover them)
  paint.setColor(Skia.Color(PALETTE.footprint));
  for (let k = 0; k < BUILDINGS.length; k += 5) {
    c.drawRect(Skia.XYWHRect(BUILDINGS[k], BUILDINGS[k + 1], BUILDINGS[k + 2] - BUILDINGS[k], BUILDINGS[k + 3] - BUILDINGS[k + 1]), paint);
  }
  return rec.finishRecordingAsPicture();
}
