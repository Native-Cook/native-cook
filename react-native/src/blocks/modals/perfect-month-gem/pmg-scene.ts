// Perfect Month Gem — the month's day tiles and the faceted gem, drawn in Skia from one progress p
// (0 calendar … 1 gem). Helpers above callers.
import { type SkCanvas, type SkFont, Skia } from '@shopify/react-native-skia';

export const LOOP = 8;
/** October 2026 starts on a Thursday (Monday-first grid: three blank cells). Day 17 went over budget. */
export const OFFSET = 3;
export const DAYS = 31;
export const OVER = 17;
export const TILE = 18;
export const GAP = 4;

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeOutBack = (k: number) => {
  'worklet';
  const x = clamp01(k);
  const c1 = 1.6;
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};

/** The autoplay's progress at loop time t. */
export const scriptP = (t: number) => {
  'worklet';
  if (t < 0.6) return 0;
  if (t < 2.8) return easeInOut((t - 0.6) / 2.2);
  if (t < 6.6) return 1;
  if (t < 7.6) return 1 - easeInOut((t - 6.6) / 1.0);
  return 0;
};

/** The phases inside p: gather (tiles fly in) and forge (facets assemble). */
export const phases = (p: number) => {
  'worklet';
  // the first tiles to arrive already start the gem, so there is never an empty card
  return { g: clamp01(p / 0.6), f: clamp01((p - 0.3) / 0.62) };
};

// The gem: axis toward the viewer. Table octagon (r 0.5, z 0.36), girdle octagon (r 1, z 0), culet (z -0.95).
const NS = 8;
const TABLE_R = 0.52;
const TABLE_Z = 0.36;
const CULET_Z = -0.95;

type V3 = [number, number, number];
const rot = (v: V3, yaw: number, pitch: number): V3 => {
  'worklet';
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const x1 = cy * v[0] + sy * v[2];
  const z1 = -sy * v[0] + cy * v[2];
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  return [x1, cp * v[1] - sp * z1, sp * v[1] + cp * z1];
};

/** Draws the gem centred on (cx, cy), radius R, turned by yaw/pitch, facets assembled by `forge`. */
export const drawGem = (c: SkCanvas, cx: number, cy: number, R: number, yaw: number, pitch: number, forge: number) => {
  'worklet';
  if (forge <= 0.001) return;
  const table: V3[] = [];
  const girdle: V3[] = [];
  for (let i = 0; i < NS; i++) {
    const a = (i / NS) * Math.PI * 2 + Math.PI / 8;
    table.push([Math.cos(a) * TABLE_R, Math.sin(a) * TABLE_R, TABLE_Z]);
    girdle.push([Math.cos(a), Math.sin(a), 0]);
  }
  const culet: V3 = [0, 0, CULET_Z];
  // facets: [vertices…], kind (0 table, 1 crown, 2 pavilion), order index for the assembly
  const facets: { v: V3[]; kind: number; k: number }[] = [];
  facets.push({ v: table, kind: 0, k: 0 });
  for (let i = 0; i < NS; i++) {
    const j = (i + 1) % NS;
    facets.push({ v: [table[i], girdle[i], girdle[j], table[j]], kind: 1, k: 1 + i });
    facets.push({ v: [girdle[i], culet, girdle[j]], kind: 2, k: 9 + i });
  }
  const L: V3 = [-0.45, -0.6, 0.66];
  const drawn: { pts: number[]; depth: number; shade: number; kind: number; a: number; s: number; ccx: number; ccy: number }[] = [];
  for (let n = 0; n < facets.length; n++) {
    const fct = facets[n];
    const pv: V3[] = [];
    for (let m = 0; m < fct.v.length; m++) pv.push(rot(fct.v[m], yaw, pitch));
    // face normal (screen y down: flip y)
    const e1: V3 = [pv[1][0] - pv[0][0], pv[1][1] - pv[0][1], pv[1][2] - pv[0][2]];
    const e2: V3 = [pv[2][0] - pv[0][0], pv[2][1] - pv[0][1], pv[2][2] - pv[0][2]];
    let nx = e1[1] * e2[2] - e1[2] * e2[1];
    let ny = e1[2] * e2[0] - e1[0] * e2[2];
    let nz = e1[0] * e2[1] - e1[1] * e2[0];
    const nl = Math.hypot(nx, ny, nz) || 1;
    nx /= nl;
    ny /= nl;
    nz /= nl;
    // keep the normal pointing outward (away from the gem's centre)
    let mx = 0;
    let my = 0;
    let mz = 0;
    for (let m = 0; m < pv.length; m++) {
      mx += pv[m][0];
      my += pv[m][1];
      mz += pv[m][2];
    }
    mx /= pv.length;
    my /= pv.length;
    mz /= pv.length;
    if (nx * mx + ny * my + nz * mz < 0) {
      nx = -nx;
      ny = -ny;
      nz = -nz;
    }
    if (nz <= 0.02) continue; // facing away
    const dif = Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]);
    // a soft environment band that slides with the turn
    const band = Math.exp(-Math.pow((nx + yaw * 0.9 - 0.25) * 3.2, 2));
    const shade = clamp01(0.18 + 0.55 * dif + 0.5 * band * (fct.kind === 2 ? 0.6 : 1));
    const pts: number[] = [];
    for (let m = 0; m < pv.length; m++) pts.push(cx + pv[m][0] * R, cy + pv[m][1] * R);
    const a = clamp01((forge - fct.k * 0.03) / 0.3);
    drawn.push({ pts, depth: mz, shade, kind: fct.kind, a, s: easeOutBack((forge - fct.k * 0.03) / 0.3), ccx: cx + mx * R, ccy: cy + my * R });
  }
  // back to front (insertion sort: no callbacks inside a worklet)
  for (let i = 1; i < drawn.length; i++) {
    const cur = drawn[i];
    let j = i - 1;
    while (j >= 0 && drawn[j].depth > cur.depth) {
      drawn[j + 1] = drawn[j];
      j--;
    }
    drawn[j + 1] = cur;
  }
  for (let n = 0; n < drawn.length; n++) {
    const d = drawn[n];
    if (d.a <= 0.001) continue;
    const pb = Skia.PathBuilder.Make();
    for (let m = 0; m < d.pts.length; m += 2) {
      const x = d.ccx + (d.pts[m] - d.ccx) * d.s;
      const y = d.ccy + (d.pts[m + 1] - d.ccy) * d.s;
      if (m === 0) pb.moveTo(x, y);
      else pb.lineTo(x, y);
    }
    pb.close();
    const path = pb.build();
    // violet, from deep to lilac with the shade; the table is the brightest
    const k = d.kind === 0 ? 0.25 + 0.75 * d.shade : d.shade;
    const r = Math.round(mix(58, 214, k));
    const g = Math.round(mix(23, 196, k * k));
    const b = Math.round(mix(160, 255, k));
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color(`rgb(${r},${g},${b})`));
    p.setAlphaf(d.a);
    c.drawPath(path, p);
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(1);
    edge.setStrokeWidth(1.2);
    edge.setStrokeJoin(1);
    edge.setColor(Skia.Color(d.shade > 0.7 ? '#f3eeff' : '#3a178f'));
    edge.setAlphaf(0.55 * d.a);
    c.drawPath(path, edge);
  }
  // a sharp glint on the table that travels with the turn
  const gl = Skia.Paint();
  gl.setAntiAlias(true);
  gl.setColor(Skia.Color('#ffffff'));
  gl.setAlphaf(0.75 * clamp01((forge - 0.6) / 0.4));
  gl.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 2.5, true));
  c.drawOval(Skia.XYWHRect(cx - R * (0.28 - yaw * 0.4), cy - R * 0.3, R * 0.16, R * 0.08), gl);
};

/** Grid cell (centre, relative to the grid's centre) of day d (1-based). */
export const cellOf = (d: number) => {
  'worklet';
  const idx = d - 1 + OFFSET;
  const col = idx % 7;
  const row = Math.floor(idx / 7);
  const W = 7 * TILE + 6 * GAP;
  const H = 5 * TILE + 4 * GAP;
  return { x: -W / 2 + TILE / 2 + col * (TILE + GAP), y: -H / 2 + TILE / 2 + row * (TILE + GAP) };
};

/** The day tiles, flying from their cells to the centre along an inward spiral as `gather` runs. */
export const drawTiles = (c: SkCanvas, cx: number, cy: number, gather: number, font: SkFont | null) => {
  'worklet';
  for (let d = 1; d <= DAYS; d++) {
    const cell = cellOf(d);
    const k = easeInOut((gather - (d - 1) * 0.012) / 0.64);
    if (k >= 0.999) continue;
    const r0 = Math.hypot(cell.x, cell.y);
    const a0 = Math.atan2(cell.y, cell.x);
    const r = r0 * (1 - k);
    const a = a0 + k * Math.PI * 1.65;
    const x = cx + Math.cos(a) * r;
    const y = cy + Math.sin(a) * r;
    const s = mix(1, 0.25, k);
    const over = d === OVER;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    // tiles tint toward the gem's violet as they arrive
    const base = over ? [255, 180, 196] : [201, 184, 255];
    const vio = [124, 77, 255];
    p.setColor(Skia.Color(`rgb(${Math.round(mix(base[0], vio[0], k))},${Math.round(mix(base[1], vio[1], k))},${Math.round(mix(base[2], vio[2], k))})`));
    p.setAlphaf(1 - smooth((k - 0.8) / 0.2));
    c.save();
    c.translate(x, y);
    c.rotate(k * 220, 0, 0);
    c.scale(s, s);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-TILE / 2, -TILE / 2, TILE, TILE), 5, 5), p);
    if (font && k < 0.15) {
      const t = Skia.Paint();
      t.setAntiAlias(true);
      t.setColor(Skia.Color(over ? '#8a1a3a' : '#3b2380'));
      t.setAlphaf(1 - k / 0.15);
      const label = `${d}`;
      const ws = font.getGlyphWidths(font.getGlyphIDs(label));
      let tw = 0;
      for (let i = 0; i < ws.length; i++) tw += ws[i];
      c.drawText(label, -tw / 2, 3.2, t, font);
    }
    c.restore();
  }
};
