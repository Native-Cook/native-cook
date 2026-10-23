/**
 * The black shape: the island pill, a body (drop → sheet) and what joins them.
 * g = 0 rest, 0..1 the drop grows with the pull, 1..2 the drop becomes the sheet.
 * While the two overlap, concave shoulder fillets blend them; once apart, a neck thins and snaps.
 */
import { Skia, type SkPath } from '@shopify/react-native-skia';

import { DROP, LAYOUT, SHEET } from './theme';

export type Box = { x: number; y: number; w: number; h: number; rx: number; ry: number };

const mix = (a: number, b: number, t: number) => {
  'worklet';
  return a + (b - a) * t;
};
export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};
const easeOut = (t: number) => {
  'worklet';
  return 1 - (1 - t) * (1 - t);
};
const smooth = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c * c * (3 - 2 * c);
};

export function pillAt(g: number, width: number): Box {
  'worklet';
  // The pill gives its width up to the sheet in the first part of the opening, ending as the bare island.
  const t = smooth((g - 1) * 1.6);
  // While the drop grows the pill narrows to the drop's width (source f025), then gives way to the island.
  const narrow = g < 1 ? mix(LAYOUT.pillWidth, DROP.width, smooth(g)) : DROP.width;
  const w = mix(narrow, LAYOUT.islandWidth, t);
  const h = mix(LAYOUT.pillHeight, LAYOUT.islandHeight, t);
  const y = mix(LAYOUT.pillTop, LAYOUT.islandTop, t);
  return { x: (width - w) / 2, y, w, h, rx: h / 2, ry: h / 2 };
}

export function bodyAt(g: number, width: number, height: number): Box {
  'worklet';
  if (g <= 1) {
    const s = easeOut(clamp01(g));
    const w = mix(120, DROP.width, Math.sin((clamp01(g) * Math.PI) / 2));
    // The drop widens quickly but grows down in step with the pull (gate 2b, source frame a: 207 × 236 pt at g 0.66).
    const h = mix(40, DROP.height, clamp01(g));
    return { x: (width - w) / 2, y: mix(8, DROP.top, s), w, h, rx: w / 2, ry: h / 2 };
  }
  // Spring overshoot past 2 is allowed a little so the sheet lands with weight.
  const u = Math.min(g - 1, 1.08);
  const sheetW = width - SHEET.inset * 2;
  const sheetH = height - SHEET.top - SHEET.bottomGap;
  const w = mix(DROP.width, sheetW, u);
  const h = mix(DROP.height, sheetH, u);
  const y = mix(DROP.top, SHEET.top, clamp01(u));
  return {
    x: (width - w) / 2, y, w, h,
    rx: Math.max(SHEET.radius, mix(DROP.width / 2, SHEET.radius, clamp01(u))),
    ry: Math.max(SHEET.radius, mix(DROP.height / 2, SHEET.radius, clamp01(u))),
  };
}

/** Half width of a rounded box at height y, or -1 outside it. */
function halfAt(b: Box, y: number): number {
  'worklet';
  if (y < b.y || y > b.y + b.h) return -1;
  const flat = b.w / 2 - b.rx;
  let dy = 0;
  if (y < b.y + b.ry) dy = (b.y + b.ry - y) / b.ry;
  else if (y > b.y + b.h - b.ry) dy = (y - (b.y + b.h - b.ry)) / b.ry;
  return flat + b.rx * Math.sqrt(Math.max(0, 1 - dy * dy));
}

function addBox(p: ReturnType<typeof Skia.PathBuilder.Make>, b: Box) {
  'worklet';
  p.addRRect({ rect: { x: b.x, y: b.y, width: b.w, height: b.h }, rx: Math.min(b.rx, b.w / 2), ry: Math.min(b.ry, b.h / 2) }, false);
}

export function islandPath(g: number, width: number, height: number): SkPath {
  'worklet';
  const pill = pillAt(g, width);
  const body = bodyAt(g, width, height);
  const cx = width / 2;
  const p = Skia.PathBuilder.Make();
  addBox(p, pill);
  if (g > 0.001) addBox(p, body);
  const pillBottom = pill.y + pill.h;
  const gap = body.y - pillBottom;
  if (g > 0.001 && gap < 0) {
    // Shoulders: fill the concave corner where the drop leaves the pill's flat underside.
    const hx = halfAt(body, pillBottom);
    const flat = pill.w / 2 - pill.rx;
    const below = body.y + body.h - pillBottom;
    const f = Math.min(DROP.fillet, below * 0.6, flat - hx);
    if (hx > 0 && f > 0.5) {
      const yB = pillBottom + Math.min(f * 1.2, below * 0.9);
      const hB = Math.max(0, halfAt(body, yB));
      // Right side clockwise, left side mirrored and reversed so every piece winds the same way.
      p.moveTo(cx + hx, pillBottom).lineTo(cx + hx + f, pillBottom).quadTo(cx + hx, pillBottom, cx + hB, yB).close();
      p.moveTo(cx - hx, pillBottom).lineTo(cx - hB, yB).quadTo(cx - hx, pillBottom, cx - hx - f, pillBottom).close();
    }
  } else if (g > 0.001 && gap < DROP.neckBreak) {
    // Neck: attached a little inside each shape, its waist thinning to nothing as the gap opens.
    const k = 1 - gap / DROP.neckBreak;
    const yA = pillBottom - 4;
    const yB = body.y + 6;
    const a = Math.min(44, Math.max(0, halfAt(pill, yA))) * Math.sqrt(k);
    const b = Math.min(64, Math.max(0, halfAt(body, yB))) * Math.sqrt(k);
    const waist = Math.min(a, b) * k * 0.7;
    const mid = (yA + yB) / 2;
    p.moveTo(cx - a, yA).lineTo(cx + a, yA).quadTo(cx + waist, mid, cx + b, yB)
      .lineTo(cx - b, yB).quadTo(cx - waist, mid, cx - a, yA).close();
  }
  return p.build();
}

/** A rounded rect inside the body that RN content can be clipped to. */
export function contentClip(g: number, width: number, height: number) {
  'worklet';
  const b = bodyAt(g, width, height);
  // While the body is an oval, a stadium the same size pokes out a few points near the top; inset it.
  const inset = 6 * (1 - smooth((g - 1) * 2));
  const w = Math.max(0, b.w - inset * 2);
  const h = Math.max(0, b.h - inset * 2);
  return { x: b.x + inset, y: b.y + inset, w, h, r: Math.min(Math.max(b.rx, b.ry) - inset, w / 2, h / 2) };
}
