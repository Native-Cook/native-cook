/*
 Folded Map Card — the fold geometry and the autoPlay script.

 The map is three panels side by side, folded as an accordion. Panel 0 lies flat; panel 1 turns
 about its left edge by φ1 (0 flat → FOLD_MAX folded back over panel 0, toward the viewer);
 panel 2 turns about panel 1's far edge by −φ2, so folded it lies flat on top, face up. Points are
 placed in 3D (x across, z toward the viewer is negative) and projected with a perspective of
 PERSPECTIVE pt about the card's centre. Each panel is drawn as STRIPS vertical strips (textured
 vertices); its shading follows its normal against a light from the upper left, with darker
 creases near each hinge as it folds.

 LOOP = 8 s:
   0.0 – 1.4   rest: folded, the cover info beside it.
   1.4 – 3.2   unfold: panel 1 opens (ease-out back, 3 % overshoot), panel 2 follows 80 ms later;
               the info beside it fades out.
   3.2 – 4.4   the route draws on (ease-in-out), the summit pin drops (4.0 – 4.4, bounce) and the
               elevation profile rises (3.4 – 4.2, 40 ms per bar).
   4.4 – 6.0   hold.
   6.0 – 7.6   the route, pin and profile go (6.0 – 6.3), then refold (6.1 – 7.6, ease-in-out,
               panel 2 first, panel 1 60 ms later).
   7.6 – 8.0   rest.
*/

export const LOOP = 8;
export const PERSPECTIVE = 900;
/** Folded angle: a little short of flat so the stacked panels keep their paper thickness. */
export const FOLD_MAX = Math.PI - 0.05;
export const STRIPS = 6;
export const IDLE_BEFORE_RETURN = 2;
export const RETURN_FOR = 0.8;

export type Card = { x: number; y: number; w: number; h: number; pw: number };

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(x, 0, 1), 3);
}

/** Ease-out with a 3 % overshoot (back curve, s = 0.9). */
export function easeOutBack(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  const s = 0.9;
  return 1 + (s + 1) * Math.pow(c - 1, 3) + s * Math.pow(c - 1, 2);
}

/** The loop: [panel 1 unfold, panel 2 unfold, route drawn, pin, profile rise, cover info]. */
export function script(t: number): [number, number, number, number, number, number] {
  'worklet';
  const close1 = easeInOut(seg(t, 6.16, 7.6));
  const close2 = easeInOut(seg(t, 6.1, 7.54));
  const u1 = t < 6 ? (t < 1.4 ? 0 : easeOutBack(seg(t, 1.4, 3.1))) : 1 - close1;
  const u2 = t < 6 ? (t < 1.48 ? 0 : easeOutBack(seg(t, 1.48, 3.2))) : 1 - close2;
  const gone = 1 - easeOut(seg(t, 6.0, 6.3));
  const route = easeInOut(seg(t, 3.2, 4.2)) * gone;
  const pin = seg(t, 4.0, 4.4) * gone;
  const rise = seg(t, 3.4, 4.2) * gone;
  const info = 1 - easeOut(seg(t, 1.4, 1.9)) + easeOut(seg(t, 7.2, 7.8));
  return [u1, u2, route, pin, rise, clamp(info, 0, 1)];
}

/** The pin's drop: from 26 pt above, landing with a small bounce. */
export function pinDrop(p: number) {
  'worklet';
  if (p <= 0) return -1;
  const x = clamp(p, 0, 1);
  if (x < 0.55) {
    const f = x / 0.55;
    return 26 * (1 - f * f);
  }
  const b = (x - 0.55) / 0.45;
  return 5 * Math.sin(Math.PI * b) * (1 - b);
}

/** Panels' frames for an unfold pair: [origin x, origin z, direction angle] × 3 (flat x from 0). */
export function panels(card: Card, u1: number, u2: number): number[] {
  'worklet';
  const f1 = FOLD_MAX * (1 - u1);
  const f2 = FOLD_MAX * (1 - u2);
  const psi1 = f1;
  const psi2 = f1 - f2;
  const ox1 = card.pw;
  const ox2 = ox1 + card.pw * Math.cos(psi1);
  const oz2 = -card.pw * Math.sin(psi1);
  return [0, 0, 0, ox1, 0, psi1, ox2, oz2, psi2];
}

/** A point on the map (x across the flat map, y down) through the folds, projected: [sx, sy, z]. */
export function project(card: Card, frames: number[], mx: number, my: number): [number, number, number] {
  'worklet';
  const k = clamp(Math.floor(mx / card.pw), 0, 2);
  const lx = mx - k * card.pw;
  const ox = frames[k * 3];
  const oz = frames[k * 3 + 1];
  const psi = frames[k * 3 + 2];
  const X = ox + lx * Math.cos(psi);
  const Z = oz - lx * Math.sin(psi);
  const cx = card.w / 2;
  const cy = card.h / 2;
  const s = PERSPECTIVE / (PERSPECTIVE + Z);
  return [card.x + cx + (X - cx) * s, card.y + cy + (my - cy) * s, Z];
}

export function frontFacing(psi: number) {
  'worklet';
  return Math.cos(psi) > 0;
}

/** Brightness of a panel facing along psi (front or back), flat front = 1. */
export function shade(psi: number, front: boolean) {
  'worklet';
  let nx = -Math.sin(psi);
  let nz = -Math.cos(psi);
  if (!front) {
    nx = -nx;
    nz = -nz;
  }
  // Toward the light: upper left, in front.
  const lx = -0.5;
  const lz = -0.866;
  const d = Math.max(0, nx * lx + nz * lz);
  return clamp((0.55 + 0.45 * d) / (0.55 + 0.45 * 0.866), 0.35, 1.08);
}

/** The tint laid over a panel for a brightness b (1 = untouched): a warm shadow below 1, light above. */
export function tintOf(b: number) {
  'worklet';
  if (b >= 1) return `rgba(255,250,235,${clamp(b - 1, 0, 0.3).toFixed(3)})`;
  return `rgba(26,18,8,${clamp(1 - b, 0, 0.75).toFixed(3)})`;
}

export type Panel = {
  v: { x: number; y: number }[];
  t: { x: number; y: number }[];
  /** The panel's outline on screen, its left and right mid points, and the shading stops across it. */
  quad: number[];
  from: { x: number; y: number };
  to: { x: number; y: number };
  tints: string[];
};

/**
 * The three panels this frame: textured strips (positions and texture points in map points, the
 * image itself untouched) and, for each, the shading drawn over it as a gradient across the panel
 * (its face's brightness, darker toward a closing crease).
 */
export function foldMesh(card: Card, MW: number, u1: number, u2: number) {
  'worklet';
  const frames = panels(card, u1, u2);
  const out: Panel[] = [];
  const f1 = FOLD_MAX * (1 - u1);
  const f2 = FOLD_MAX * (1 - u2);
  const cx = card.w / 2;
  const cy = card.h / 2;
  for (let k = 0; k < 3; k += 1) {
    const ox = frames[k * 3];
    const oz = frames[k * 3 + 1];
    const psi = frames[k * 3 + 2];
    const front = frontFacing(psi);
    const base = shade(psi, front);
    const leftFold = k === 1 ? f1 : k === 2 ? f2 : 0;
    const rightFold = k === 0 ? f1 : k === 1 ? f2 : 0;
    const v: { x: number; y: number }[] = [];
    const t: { x: number; y: number }[] = [];
    for (let c = 0; c <= STRIPS; c += 1) {
      const lx = (card.pw * c) / STRIPS;
      const X = ox + lx * Math.cos(psi);
      const Z = oz - lx * Math.sin(psi);
      const s = PERSPECTIVE / (PERSPECTIVE + Z);
      for (let r = 0; r < 2; r += 1) {
        const my = r * card.h;
        v.push({ x: card.x + cx + (X - cx) * s, y: card.y + cy + (my - cy) * s });
        t.push(front ? { x: k * card.pw + lx, y: my } : { x: MW + card.pw - lx, y: my });
      }
    }
    const last = STRIPS * 2;
    const quad = [v[0].x, v[0].y, v[last].x, v[last].y, v[last + 1].x, v[last + 1].y, v[1].x, v[1].y];
    // Creases darken by up to 26 % as they close.
    const cl = 0.26 * Math.abs(Math.sin(leftFold / 2));
    const cr = 0.26 * Math.abs(Math.sin(rightFold / 2));
    const from = { x: (v[0].x + v[1].x) / 2, y: (v[0].y + v[1].y) / 2 };
    const to = { x: (v[last].x + v[last + 1].x) / 2, y: (v[last].y + v[last + 1].y) / 2 };
    // From and to follow the panel's own left and right, so on a back face the stops still sit on
    // the right creases.
    const tints = [tintOf(base * (1 - cl)), tintOf(base * (1 - cl * 0.25)), tintOf(base * (1 - cr * 0.25)), tintOf(base * (1 - cr))];
    out.push({ v, t, quad, from, to, tints });
  }
  return { panels: out, frames };
}

/** One panel's triangle list (STRIPS strips, two triangles each), the same every frame. */
export function meshIndices() {
  const out: number[] = [];
  for (let c = 0; c < STRIPS; c += 1) {
    const a = c * 2;
    out.push(a, a + 2, a + 1, a + 2, a + 3, a + 1);
  }
  return out;
}
