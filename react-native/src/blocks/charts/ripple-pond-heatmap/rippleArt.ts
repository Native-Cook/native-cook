import { BlurStyle, matchFont, PaintStyle, Skia, type SkCanvas, type SkImage, StrokeCap, TileMode } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

/*
 Stillpond's October: the data, and the lily pads baked once into a full-screen image.

 October 2026 starts on a Thursday; weeks start on Monday, so the grid is 7 × 5 and day d sits at
 index d + 2. Today is the 25th: the days after it are drawn as faint rings. A pad's colour
 deepens with the minutes practised (5 levels, pale sage to deep emerald); a day over an hour
 carries a small water-lily flower. Each pad has the classic notch cut toward its centre (at a
 seeded angle), fine radial veins, a lighter rim, and its day number.
*/

export const FIRST_COL = 3;
export const DAYS = 31;
export const TODAY = 25;
/** Minutes practised per day of October (up to today). */
export const MINUTES = [22, 35, 0, 48, 15, 30, 28, 0, 40, 62, 55, 12, 42, 38, 0, 25, 33, 55, 20, 44, 36, 50, 0, 65, 72];
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const FOCUS = ['Breath focus', 'Body scan', 'Open awareness', 'Walking', 'Loving kindness'];

export function sessionsOf(m: number) {
  'worklet';
  return m === 0 ? 0 : Math.max(1, Math.round(m / 16));
}
export function levelOf(m: number) {
  'worklet';
  if (m <= 0) return 0;
  if (m < 20) return 1;
  if (m < 35) return 2;
  if (m < 50) return 3;
  return 4;
}
export function cellOf(day: number) {
  'worklet';
  const i = day - 1 + FIRST_COL;
  return [i % 7, Math.floor(i / 7)];
}

function hm(min: number) {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}

const TOTAL = MINUTES.reduce((a, b) => a + b, 0);
const SESSIONS = MINUTES.reduce((a, b) => a + sessionsOf(b), 0);
let best = 0;
let run = 0;
for (const m of MINUTES) {
  run = m > 0 ? run + 1 : 0;
  best = Math.max(best, run);
}
export const SUMMARY = `${hm(TOTAL)} · ${SESSIONS} sessions · ${best}-day best streak`;
export const BEST_STREAK = best;

/** What the detail card says: -1 = the month, 1…31 = a day, 100 + row = a week. */
export function detailFor(id: number) {
  if (id >= 100) {
    const row = id - 100;
    let min = 0;
    let ses = 0;
    let first = 0;
    let last = 0;
    for (let d = 1; d <= TODAY; d += 1) {
      if (cellOf(d)[1] !== row) continue;
      if (!first) first = d;
      last = d;
      min += MINUTES[d - 1];
      ses += sessionsOf(MINUTES[d - 1]);
    }
    return { title: `Week ${row + 1} · ${hm(min)} · ${ses} sessions`, sub: `Oct ${first} – ${last} · ripples across the week` };
  }
  if (id >= 1) {
    const m = MINUTES[id - 1] ?? 0;
    const wd = WEEKDAYS[cellOf(id)[0]];
    if (id > TODAY) return { title: `${wd} ${id} · not yet`, sub: 'Plan a sit for this day' };
    if (m === 0) return { title: `${wd} ${id} · rest day`, sub: 'No sessions logged' };
    return { title: `${wd} ${id} · ${m} min · ${sessionsOf(m)} ${sessionsOf(m) === 1 ? 'session' : 'sessions'}`, sub: `Mostly ${FOCUS[id % FOCUS.length].toLowerCase()}` };
  }
  return { title: `October · ${hm(TOTAL)}`, sub: `${SESSIONS} sessions · ${best}-day best streak · tap a day` };
}

const PAD_COLORS = [
  ['#9DB3A2', '#7F978A'],
  ['#A9CF8E', '#86B06E'],
  ['#6FB06A', '#4F914F'],
  ['#3E8C4E', '#2D7040'],
  ['#1F6A3C', '#15502C'],
];

const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SCALE = 2;

function hash(n: number) {
  const x = Math.sin(n * 127.1) * 43758.5453;
  return x - Math.floor(x);
}

function drawPad(canvas: SkCanvas, cx: number, cy: number, r: number, day: number) {
  const m = day <= TODAY ? MINUTES[day - 1] : -1;
  const lvl = m < 0 ? 0 : levelOf(m);
  const future = m < 0;
  const size = future ? r * 0.82 : lvl === 0 ? r * 0.78 : r * (0.86 + 0.035 * lvl);
  const notch = hash(day * 3.7) * Math.PI * 2;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (future) {
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(1.2);
    paint.setColor(Skia.Color('rgba(220,240,228,0.32)'));
    canvas.drawCircle(cx, cy, size, paint);
  } else {
    // The pad: a disc with a narrow wedge cut to its centre.
    const half = 0.22;
    const b = Skia.PathBuilder.Make();
    b.moveTo(cx, cy);
    const steps = 48;
    for (let i = 0; i <= steps; i += 1) {
      const a = notch + half + (i / steps) * (Math.PI * 2 - 2 * half);
      b.lineTo(cx + Math.cos(a) * size, cy + Math.sin(a) * size);
    }
    b.close();
    const path = b.build();
    const [c0, c1] = PAD_COLORS[lvl];
    paint.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(cx - size * 0.3, cy - size * 0.35),
        size * 1.5,
        [Skia.Color(c0), Skia.Color(c1), Skia.Color(c1)],
        [0, 0.75, 1],
        TileMode.Clamp,
      ),
    );
    if (lvl === 0) paint.setAlphaf(0.62);
    canvas.drawPath(path, paint);
    // Veins.
    const vein = Skia.Paint();
    vein.setAntiAlias(true);
    vein.setStyle(PaintStyle.Stroke);
    vein.setStrokeWidth(0.7);
    vein.setStrokeCap(StrokeCap.Round);
    vein.setColor(Skia.Color(lvl >= 3 ? 'rgba(170,220,170,0.28)' : 'rgba(40,80,50,0.22)'));
    for (let i = 1; i < 9; i += 1) {
      const a = notch + 0.22 + (i / 9) * (Math.PI * 2 - 0.44);
      canvas.drawLine(cx + Math.cos(a) * size * 0.18, cy + Math.sin(a) * size * 0.18, cx + Math.cos(a) * size * 0.88, cy + Math.sin(a) * size * 0.88, vein);
    }
    // Rim: lighter on the upper left, darker below.
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1.2);
    rim.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(cx - size, cy - size),
        Skia.Point(cx + size, cy + size),
        [Skia.Color('rgba(235,255,225,0.55)'), Skia.Color('rgba(235,255,225,0.0)'), Skia.Color('rgba(10,40,20,0.35)')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawPath(path, rim);
    // A water lily on the best days.
    if (m >= 60) {
      const fx = cx + Math.cos(notch + Math.PI * 0.8) * size * 0.64;
      const fy = cy + Math.sin(notch + Math.PI * 0.8) * size * 0.64;
      const petal = Skia.Paint();
      petal.setAntiAlias(true);
      for (let i = 0; i < 7; i += 1) {
        const a = (i / 7) * Math.PI * 2 + day;
        petal.setColor(Skia.Color(i % 2 ? '#F6C9D6' : '#FBE3EA'));
        canvas.drawOval(Skia.XYWHRect(fx + Math.cos(a) * 3 - 2.5, fy + Math.sin(a) * 3 - 2.5, 5, 5), petal);
      }
      petal.setColor(Skia.Color('#F4C94E'));
      canvas.drawCircle(fx, fy, 1.8, petal);
    }
  }
  // The day number.
  const font = matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '600' });
  const text = `${day}`;
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let tw = 0;
  for (let i = 0; i < widths.length; i += 1) tw += widths[i];
  const label = Skia.Paint();
  label.setAntiAlias(true);
  label.setColor(Skia.Color(future ? 'rgba(220,240,228,0.5)' : lvl >= 3 ? 'rgba(236,250,236,0.95)' : 'rgba(18,48,30,0.85)'));
  canvas.drawText(text, cx - tw / 2, cy + 4.3, label, font);
  if (day === TODAY) {
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(1.6);
    ring.setColor(Skia.Color('rgba(250,240,200,0.9)'));
    ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
    canvas.drawCircle(cx, cy, size + 3.5, ring);
  }
}

/** The pads at their places, baked at 2× into a w × h image. */
export function bakePads(w: number, h: number, grid: number[]): SkImage | null {
  const pw = Math.max(1, Math.ceil(w * SCALE));
  const ph = Math.max(1, Math.ceil(h * SCALE));
  const surface = Skia.Surface.MakeOffscreen?.(pw, ph) ?? Skia.Surface.Make(pw, ph);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(SCALE, SCALE);
  const r = Math.min(grid[2], grid[3]) * 0.43;
  for (let d = 1; d <= DAYS; d += 1) {
    const [c, row] = cellOf(d);
    drawPad(canvas, grid[0] + (c + 0.5) * grid[2], grid[1] + (row + 0.5) * grid[3], r, d);
  }
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}
