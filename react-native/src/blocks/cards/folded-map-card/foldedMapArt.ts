import {
  AlphaType,
  ColorType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  Skia,
  StrokeCap,
  StrokeJoin,
} from '@shopify/react-native-skia';

/*
 Folded Map Card — the printed map, drawn once into one image (an atlas: the map's face on the left,
 the back of a panel on the right), plus the route and its elevation profile.

 Terrain: a height field (a ridge running up to the right, Kestrel Peak at its end, a basin with
 Alder Lake, and fbm noise for the folds of the ground). From it: a soft tint at one pixel per 2 pt (paper,
 hill shading from the upper left, forest where a second noise is high and the ground is low, bare
 rock up high), upscaled with linear filtering; contours by marching squares on a 3 pt grid (every
 fifth an index contour); a faint kilometre grid; the lake, a stream, symbols and labels; a
 title block; and paper grain at device resolution.
*/

export type MapArt = {
  image: SkImage;
  /** The route in map coordinates: x0, y0, x1, y1, … */
  route: number[];
  /** Elevation along the route, 0 (trailhead, 1 394 m) … 1 (summit, 2 214 m), PROFILE_N samples. */
  profile: number[];
};

export const PROFILE_N = 36;

function hash(x: number, y: number) {
  const s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return s - Math.floor(s);
}

function vnoise(x: number, y: number) {
  const ix = Math.floor(x);
  const iy = Math.floor(y);
  let fx = x - ix;
  let fy = y - iy;
  fx = fx * fx * (3 - 2 * fx);
  fy = fy * fy * (3 - 2 * fy);
  const a = hash(ix, iy);
  const b = hash(ix + 1, iy);
  const c = hash(ix, iy + 1);
  const d = hash(ix + 1, iy + 1);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

function fbm(x: number, y: number) {
  return vnoise(x, y) * 0.5 + vnoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * 0.27 + vnoise(x * 4.1 + 9.1, y * 4.1 - 3.7) * 0.15 + vnoise(x * 8.3 - 2.2, y * 8.3 + 7.7) * 0.08;
}

const PEAK = [0.86, 0.26];
const LAKE = [0.22, 0.68];

/** Terrain height at a normalised map point (0..1 across, 0..1 down). */
function terrain(nx: number, ny: number) {
  // Distance from the ridge line, running from the lower left toward the peak.
  const ax = 0.05;
  const ay = 0.95;
  const bx = PEAK[0];
  const by = PEAK[1];
  const vx = bx - ax;
  const vy = by - ay;
  const t = Math.max(0, Math.min(1, ((nx - ax) * vx + (ny - ay) * vy) / (vx * vx + vy * vy)));
  const dx = nx - (ax + vx * t);
  const dy = ny - (ay + vy * t);
  const ridge = Math.exp(-(dx * dx + dy * dy) / 0.03) * (0.25 + 0.45 * t);
  const peak = Math.exp(-((nx - PEAK[0]) ** 2 + (ny - PEAK[1]) ** 2) / 0.018) * 0.45;
  const lake = Math.exp(-((nx - LAKE[0]) ** 2 + ((ny - LAKE[1]) * 1.4) ** 2) / 0.012) * 0.3;
  return ridge + peak - lake + fbm(nx * 4.2, ny * 3.1) * 0.32;
}

const ROUTE_CTRL = [
  0.07, 0.88, 0.15, 0.8, 0.24, 0.82, 0.31, 0.71, 0.29, 0.6, 0.38, 0.55, 0.46, 0.59, 0.52, 0.47, 0.6, 0.43, 0.66, 0.47, 0.72, 0.38,
  0.78, 0.33, 0.83, 0.3, 0.86, 0.26,
];

function catmull(ctrl: number[], samples: number) {
  const n = ctrl.length / 2;
  const out: number[] = [];
  for (let s = 0; s <= samples; s += 1) {
    const f = (s / samples) * (n - 1);
    const i = Math.min(n - 2, Math.floor(f));
    const t = f - i;
    const p = (k: number, c: number) => ctrl[Math.max(0, Math.min(n - 1, k)) * 2 + c];
    for (let c = 0; c < 2; c += 1) {
      const p0 = p(i - 1, c);
      const p1 = p(i, c);
      const p2 = p(i + 1, c);
      const p3 = p(i + 2, c);
      const t2 = t * t;
      const t3 = t2 * t;
      out.push(0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3));
    }
  }
  return out;
}

function textWidth(font: SkFont, text: string) {
  let w = 0;
  for (const v of font.getGlyphWidths(font.getGlyphIDs(text))) w += v;
  return w;
}

function label(c: SkCanvas, text: string, font: SkFont, x: number, y: number, color: string, halo = true) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  if (halo) {
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(2.4);
    p.setStrokeJoin(StrokeJoin.Round);
    p.setColor(Skia.Color('rgba(240,232,212,0.85)'));
    c.drawText(text, x, y, p, font);
    p.setStyle(PaintStyle.Fill);
  }
  p.setColor(Skia.Color(color));
  c.drawText(text, x, y, p, font);
}

export function buildMap(MW: number, MH: number, pw: number, scale: number, fonts: { bold: SkFont; small: SkFont; italic: SkFont; title: SkFont }): MapArt {
  const AW = MW + pw;
  // 1. The tint, one pixel per TINT pt, over the whole atlas.
  const TINT = 2;
  const tw = Math.ceil(AW / TINT);
  const th = Math.ceil(MH / TINT);
  const tint = new Uint8Array(tw * th * 4);
  const e = 1 / Math.max(MW, MH);
  for (let y = 0; y < th; y += 1) {
    for (let x = 0; x < tw; x += 1) {
      const o = (y * tw + x) * 4;
      let r = 239;
      let g = 231;
      let b = 210;
      if (x * TINT < MW) {
        const nx = (x * TINT) / MW;
        const ny = (y * TINT) / MH;
        const h = terrain(nx, ny);
        const hx = terrain(nx + e, ny) - h;
        const hy = terrain(nx, ny + e) - h;
        // Hill shading: light from the upper left.
        const sh = Math.max(-1, Math.min(1, (-hx - hy) * 60));
        const lift = 1 + sh * 0.07;
        const forest = vnoise(nx * 7 + 3, ny * 5.5 - 1) * 0.72 + vnoise(nx * 13, ny * 11) * 0.28;
        const f = h < 0.55 ? Math.max(0, Math.min(1, (forest - 0.52) * 5)) * 0.75 * Math.min(1, (0.55 - h) * 8) : 0;
        r = r + (190 - r) * f;
        g = g + (210 - g) * f;
        b = b + (165 - b) * f;
        const rock = Math.max(0, Math.min(1, (h - 0.62) * 6));
        r = r + (226 - r) * rock * 0.6;
        g = g + (218 - g) * rock * 0.6;
        b = b + (204 - b) * rock * 0.6;
        r *= lift;
        g *= lift;
        b *= lift * 0.99;
      } else {
        r = 243;
        g = 236;
        b = 219;
      }
      tint[o] = Math.min(255, r);
      tint[o + 1] = Math.min(255, g);
      tint[o + 2] = Math.min(255, b);
      tint[o + 3] = 255;
    }
  }
  const tintData = Skia.Data.fromBytes(tint);
  const tintImage = Skia.Image.MakeImage({ width: tw, height: th, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul }, tintData, tw * 4);

  const surface = Skia.Surface.Make(Math.ceil(AW * scale), Math.ceil(MH * scale))!;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (tintImage) c.drawImageRectOptions(tintImage, Skia.XYWHRect(0, 0, tw, th), Skia.XYWHRect(0, 0, tw * TINT, th * TINT), FilterMode.Linear, MipmapMode.None, paint);

  c.save();
  c.clipRect(Skia.XYWHRect(0, 0, MW, MH), 1, false);
  // 2. Kilometre grid.
  const grid = Skia.Paint();
  grid.setAntiAlias(true);
  grid.setStyle(PaintStyle.Stroke);
  grid.setStrokeWidth(0.5);
  grid.setColor(Skia.Color('rgba(70,120,170,0.22)'));
  for (let x = 46; x < MW; x += 58) c.drawLine(x, 0, x, MH, grid);
  for (let y = 30; y < MH; y += 58) c.drawLine(0, y, MW, y, grid);

  // 3. Contours.
  const step = 3;
  const cols = Math.ceil(MW / step);
  const rows = Math.ceil(MH / step);
  const hs = new Float32Array((cols + 1) * (rows + 1));
  let lo = Infinity;
  let hi = -Infinity;
  for (let j = 0; j <= rows; j += 1)
    for (let i = 0; i <= cols; i += 1) {
      const v = terrain((i * step) / MW, (j * step) / MH);
      hs[j * (cols + 1) + i] = v;
      lo = Math.min(lo, v);
      hi = Math.max(hi, v);
    }
  const minor = Skia.PathBuilder.Make();
  const index = Skia.PathBuilder.Make();
  const LEVELS = 22;
  for (let l = 1; l < LEVELS; l += 1) {
    const L = lo + ((hi - lo) * l) / LEVELS;
    const path = l % 5 === 0 ? index : minor;
    for (let j = 0; j < rows; j += 1)
      for (let i = 0; i < cols; i += 1) {
        const a = hs[j * (cols + 1) + i];
        const b = hs[j * (cols + 1) + i + 1];
        const cc = hs[(j + 1) * (cols + 1) + i + 1];
        const d = hs[(j + 1) * (cols + 1) + i];
        const k = (a > L ? 8 : 0) | (b > L ? 4 : 0) | (cc > L ? 2 : 0) | (d > L ? 1 : 0);
        if (k === 0 || k === 15) continue;
        const x0 = i * step;
        const y0 = j * step;
        const top: [number, number] = [x0 + (step * (L - a)) / (b - a), y0];
        const right: [number, number] = [x0 + step, y0 + (step * (L - b)) / (cc - b)];
        const bottom: [number, number] = [x0 + (step * (L - d)) / (cc - d), y0 + step];
        const left: [number, number] = [x0, y0 + (step * (L - a)) / (d - a)];
        const segs: [number, number][][] = [];
        if (k === 1 || k === 14) segs.push([left, bottom]);
        else if (k === 2 || k === 13) segs.push([bottom, right]);
        else if (k === 3 || k === 12) segs.push([left, right]);
        else if (k === 4 || k === 11) segs.push([top, right]);
        else if (k === 6 || k === 9) segs.push([top, bottom]);
        else if (k === 7 || k === 8) segs.push([top, left]);
        else if (k === 5) segs.push([top, right], [left, bottom]);
        else if (k === 10) segs.push([top, left], [bottom, right]);
        for (const s of segs) {
          path.moveTo(s[0][0], s[0][1]);
          path.lineTo(s[1][0], s[1][1]);
        }
      }
  }
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeCap(StrokeCap.Round);
  line.setStrokeWidth(0.6);
  line.setColor(Skia.Color('rgba(160,104,58,0.5)'));
  c.drawPath(minor.build(), line);
  line.setStrokeWidth(1.1);
  line.setColor(Skia.Color('rgba(150,92,46,0.78)'));
  c.drawPath(index.build(), line);

  // 4. Lake and stream.
  const lake = Skia.PathBuilder.Make();
  for (let i = 0; i <= 48; i += 1) {
    const a = (i / 48) * Math.PI * 2;
    const r = 1 + 0.18 * Math.sin(a * 3 + 0.6) + 0.1 * Math.sin(a * 5 + 2);
    const x = LAKE[0] * MW + Math.cos(a) * 26 * r;
    const y = LAKE[1] * MH + Math.sin(a) * 14 * r;
    if (i === 0) lake.moveTo(x, y);
    else lake.lineTo(x, y);
  }
  lake.close();
  paint.setColor(Skia.Color('#A8CDD6'));
  c.drawPath(lake.build(), paint);
  line.setStrokeWidth(1);
  line.setColor(Skia.Color('#5F96AB'));
  c.drawPath(lake.build(), line);
  const stream = Skia.PathBuilder.Make();
  stream.moveTo(LAKE[0] * MW + 18, LAKE[1] * MH + 10);
  stream.cubicTo(LAKE[0] * MW + 40, LAKE[1] * MH + 30, LAKE[0] * MW + 8, MH - 30, LAKE[0] * MW + 34, MH + 4);
  line.setStrokeWidth(1.4);
  c.drawPath(stream.build(), line);

  // 5. Symbols and labels.
  const px = PEAK[0] * MW;
  const py = PEAK[1] * MH;
  const tri = Skia.PathBuilder.Make();
  tri.moveTo(px, py - 5);
  tri.lineTo(px + 4.5, py + 3);
  tri.lineTo(px - 4.5, py + 3);
  tri.close();
  paint.setColor(Skia.Color('#5A3A22'));
  c.drawPath(tri.build(), paint);
  label(c, 'Kestrel Peak', fonts.bold, px - textWidth(fonts.bold, 'Kestrel Peak') - 8, py - 6, '#4A2E1A');
  label(c, '2 214', fonts.small, px - textWidth(fonts.small, '2 214') - 8, py + 6, '#6A4A30');
  label(c, 'Alder Lake', fonts.italic, LAKE[0] * MW - 24, LAKE[1] * MH - 20, '#3F7F98');
  const hutX = 0.46 * MW;
  const hutY = 0.6 * MH;
  paint.setColor(Skia.Color('#7A2E22'));
  c.drawRect(Skia.XYWHRect(hutX - 3, hutY - 3, 6, 6), paint);
  label(c, 'Larch Hut', fonts.small, hutX + 6, hutY + 12, '#5A3A22');
  label(c, 'RIDGE LINE', fonts.small, 0.42 * MW, 0.36 * MH, 'rgba(90,58,34,0.7)');
  c.restore();

  // 6. The title block in the lower right (the face the folded map shows), and the creases' wear.
  const tbx = MW - 106;
  paint.setColor(Skia.Color('rgba(250,246,234,0.94)'));
  c.drawRect(Skia.XYWHRect(tbx, MH - 44, 98, 36), paint);
  line.setStrokeWidth(0.8);
  line.setColor(Skia.Color('rgba(60,40,24,0.7)'));
  c.drawRect(Skia.XYWHRect(tbx, MH - 44, 98, 36), line);
  label(c, 'RIDGE LINE', fonts.bold, tbx + 6, MH - 28, '#2E2016', false);
  label(c, 'Alder Valley · 1:25 000', fonts.small, tbx + 6, MH - 15, '#5A4632', false);
  paint.setColor(Skia.Color('rgba(255,252,240,0.35)'));
  c.drawRect(Skia.XYWHRect(pw - 0.6, 0, 1.2, MH), paint);
  c.drawRect(Skia.XYWHRect(pw * 2 - 0.6, 0, 1.2, MH), paint);

  // 7. The back of a panel: cover title and a legend.
  const bx = MW + 12;
  label(c, 'RIDGE LINE', fonts.title, bx, 40, '#2E2016', false);
  label(c, 'TRAIL', fonts.title, bx, 62, '#2E2016', false);
  label(c, 'Alder Valley · Sheet 7', fonts.small, bx, 80, '#6A5640', false);
  const legend: [string, string][] = [
    ['#B5442E', 'Route'],
    ['rgba(150,92,46,0.8)', 'Contour 20 m'],
    ['#5F96AB', 'Water'],
    ['#9DB386', 'Forest'],
  ];
  legend.forEach(([col, text], i) => {
    const y = 112 + i * 18;
    line.setColor(Skia.Color(col));
    line.setStrokeWidth(2);
    c.drawLine(bx, y - 3.5, bx + 14, y - 3.5, line);
    label(c, text, fonts.small, bx + 20, y, '#4A3826', false);
  });
  label(c, 'Printed Oct 2026', fonts.small, bx, MH - 16, '#8A7458', false);

  // 8. Paper grain at device resolution.
  const gw = Math.ceil(AW * scale);
  const gh = Math.ceil(MH * scale);
  const grain = new Uint8Array(gw * gh * 4);
  let seed = 1234567;
  for (let i = 0; i < gw * gh; i += 1) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    const v = seed / 0x7fffffff;
    const dark = v < 0.5;
    const a = Math.floor(Math.abs(v - 0.5) * 2 * 22);
    grain[i * 4] = dark ? 60 : 255;
    grain[i * 4 + 1] = dark ? 40 : 250;
    grain[i * 4 + 2] = dark ? 20 : 235;
    grain[i * 4 + 3] = a;
  }
  const grainImage = Skia.Image.MakeImage({ width: gw, height: gh, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul }, Skia.Data.fromBytes(grain), gw * 4);
  if (grainImage) {
    c.save();
    c.scale(1 / scale, 1 / scale);
    c.drawImage(grainImage, 0, 0);
    c.restore();
  }

  surface.flush();
  const image = surface.makeImageSnapshot();

  // Route and profile.
  const ctrl = ROUTE_CTRL.map((v, i) => (i % 2 === 0 ? v * MW : v * MH));
  const route = catmull(ctrl, 150);
  const heights: number[] = [];
  for (let s = 0; s < PROFILE_N; s += 1) {
    const k = Math.round((s / (PROFILE_N - 1)) * (route.length / 2 - 1));
    heights.push(terrain(route[k * 2] / MW, route[k * 2 + 1] / MH));
  }
  const h0 = heights[0];
  const h1 = heights[heights.length - 1];
  const profile = heights.map((h) => Math.max(-0.1, (h - h0) / (h1 - h0)));
  return { image, route, profile };
}
