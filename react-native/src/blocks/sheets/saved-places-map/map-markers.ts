/**
 * Decorative map glyphs of the pin (saved flags, liked thumbs, stars): centres in ref px (1206 × 2622), measured
 * from the pin by colour detection. Data on purpose: the motion step moves these into clusters. The flags beside
 * each marker's label are drawn by the marker itself and are not listed here.
 */
import { CLUSTER } from './motion';
import { bubble, marker, markers } from './theme';

export type MapGlyphKind = 'bookmark' | 'thumb' | 'star';
export type MapGlyph = { x: number; y: number; kind: MapGlyphKind };

/** Glyph boxes in ref px (white outline included; the blue core is 22 × 26 for a flag, ≈ 30 × 34 for a thumb, 28 × 27 for a star). */
export const GLYPH_SIZE: Record<MapGlyphKind, { w: number; h: number }> = {
  bookmark: { w: 28, h: 32 },
  thumb: { w: 34, h: 38 },
  star: { w: 32, h: 31 },
};

export const MAP_GLYPHS: MapGlyph[] = [
  { x: 453, y: 646, kind: 'bookmark' },
  { x: 424, y: 649, kind: 'bookmark' },
  { x: 460, y: 745, kind: 'bookmark' },
  { x: 462, y: 759, kind: 'bookmark' },
  { x: 647, y: 810, kind: 'bookmark' },
  { x: 357, y: 835, kind: 'bookmark' },
  { x: 549, y: 850, kind: 'bookmark' },
  { x: 402, y: 872, kind: 'bookmark' },
  { x: 626, y: 883, kind: 'bookmark' },
  { x: 710, y: 934, kind: 'bookmark' },
  { x: 406, y: 1044, kind: 'bookmark' },
  { x: 608, y: 1052, kind: 'bookmark' },
  { x: 667, y: 1090, kind: 'bookmark' },
  { x: 633, y: 1151, kind: 'bookmark' },
  { x: 616, y: 1173, kind: 'bookmark' },
  { x: 399, y: 785, kind: 'thumb' },
  { x: 419, y: 786, kind: 'thumb' },
  { x: 608, y: 809, kind: 'thumb' },
  { x: 372, y: 813, kind: 'thumb' },
  { x: 437, y: 814, kind: 'thumb' },
  { x: 414, y: 815, kind: 'thumb' },
  { x: 396, y: 830, kind: 'thumb' },
  { x: 588, y: 841, kind: 'thumb' },
  { x: 418, y: 853, kind: 'thumb' },
  { x: 571, y: 861, kind: 'thumb' },
  { x: 647, y: 865, kind: 'thumb' },
  { x: 671, y: 913, kind: 'thumb' },
  { x: 775, y: 1005, kind: 'thumb' },
  { x: 251, y: 1129, kind: 'thumb' },
  { x: 206, y: 1135, kind: 'thumb' },
  { x: 171, y: 1186, kind: 'thumb' },
  { x: 113, y: 1192, kind: 'thumb' },
  { x: 223, y: 1195, kind: 'thumb' },
  { x: 580, y: 404, kind: 'star' },
  { x: 730, y: 563, kind: 'star' },
  { x: 722, y: 565, kind: 'star' },
  { x: 457, y: 815, kind: 'star' },
  { x: 859, y: 941, kind: 'star' },
];

/* ---- Clusters (motion) ---- */


export type Cluster = {
  key: string;
  /** Café disc centre and count-bubble centre, ref px. */
  cx: number;
  cy: number;
  bx: number;
  by: number;
  count: number;
  /** Start offsets (cluster progress) of the glyphs that land here. */
  delays: number[];
};

export type GlyphPath = MapGlyph & { tx: number; ty: number; delay: number; cluster: number };

const discs = markers.map((m) => ({ key: m.key, cx: m.x - marker.discDx, cy: m.base - marker.discDy }));

/** Each glyph flies to the bubble of its nearest café; nearer glyphs leave first. */
function buildPaths() {
  const near = MAP_GLYPHS.map((g) => {
    let best = 0;
    let bestD = Infinity;
    discs.forEach((c, k) => {
      const dd = Math.hypot(g.x - c.cx, g.y - c.cy);
      if (dd < bestD) {
        bestD = dd;
        best = k;
      }
    });
    return { k: best, dist: bestD };
  });
  const order = near.map((n, i) => ({ i, dist: n.dist })).sort((a, b) => a.dist - b.dist);
  const rank: number[] = [];
  order.forEach((o, r) => (rank[o.i] = r));
  const last = Math.max(1, MAP_GLYPHS.length - 1);
  const paths: GlyphPath[] = MAP_GLYPHS.map((g, i) => {
    const c = discs[near[i].k];
    return { ...g, tx: c.cx + bubble.dx, ty: c.cy + bubble.dy, delay: (CLUSTER.span * rank[i]) / last, cluster: near[i].k };
  });
  const clusters: Cluster[] = discs.map((c, k) => {
    const mine = paths.filter((p) => p.cluster === k);
    return { key: c.key, cx: c.cx, cy: c.cy, bx: c.cx + bubble.dx, by: c.cy + bubble.dy, count: mine.length, delays: mine.map((p) => p.delay) };
  });
  return { paths, clusters };
}

export const { paths: GLYPH_PATHS, clusters: CLUSTERS } = buildPaths();
