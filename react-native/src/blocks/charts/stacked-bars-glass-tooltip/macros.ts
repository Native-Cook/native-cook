import { Skia, TileMode } from '@shopify/react-native-skia';

/** One column: its label under the axis, the tooltip's title, and grams of each macro. */
export type MacroDay = { label: string; title: string; carbs: number; fat: number; protein: number };

export type MacroRange = {
  key: string;
  /** The segmented control's label. */
  name: string;
  /** The period, under the screen's title. */
  period: string;
  /** Calories at the top of the axis. */
  max: number;
  /** The change against the period before, in percent. */
  change: number;
  columns: MacroDay[];
};

// Calories in a gram of each macro.
export const KCAL = { carbs: 4, fat: 9, protein: 4 };

export function calories(day: { carbs: number; fat: number; protein: number }) {
  'worklet';
  return day.carbs * KCAL.carbs + day.fat * KCAL.fat + day.protein * KCAL.protein;
}

const WEEK: MacroDay[] = [
  { label: 'Mar 23', title: 'Mar 23, 2020', carbs: 210, fat: 52, protein: 110 },
  { label: 'Mar 24', title: 'Mar 24, 2020', carbs: 160, fat: 46, protein: 128 },
  { label: 'Mar 25', title: 'Mar 25, 2020', carbs: 245, fat: 44, protein: 120 },
  { label: 'Mar 26', title: 'Mar 26, 2020', carbs: 128, fat: 62, protein: 165 },
  { label: 'Mar 27', title: 'Mar 27, 2020', carbs: 176, fat: 38, protein: 96 },
  { label: 'Mar 28', title: 'Mar 28, 2020', carbs: 150, fat: 74, protein: 150 },
  { label: 'Mar 29', title: 'Mar 29, 2020', carbs: 168, fat: 50, protein: 112 },
];

const DAY: MacroDay[] = [
  { label: '7 AM', title: 'Mar 29, 7 AM', carbs: 62, fat: 14, protein: 28 },
  { label: '10 AM', title: 'Mar 29, 10 AM', carbs: 24, fat: 9, protein: 12 },
  { label: '1 PM', title: 'Mar 29, 1 PM', carbs: 48, fat: 12, protein: 36 },
  { label: '4 PM', title: 'Mar 29, 4 PM', carbs: 18, fat: 6, protein: 6 },
  { label: '7 PM', title: 'Mar 29, 7 PM', carbs: 52, fat: 15, protein: 30 },
];

/** A fixed hash in [0, 1) of a day and a channel, so March looks the same on every run and stack. */
function hash(day: number, channel: number) {
  let h = (day * 374761393 + channel * 668265263) >>> 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

// March: the week above in its place, the other days drawn from the hash.
const MONTH: MacroDay[] = [];
for (let d = 1; d <= 31; d++) {
  const week = WEEK[d - 23];
  MONTH.push(
    week
      ? { ...week, label: d % 7 === 1 ? `Mar ${d}` : '' }
      : {
          label: d % 7 === 1 ? `Mar ${d}` : '',
          title: `Mar ${d}, 2020`,
          carbs: Math.round(120 + 120 * hash(d, 1)),
          fat: Math.round(32 + 40 * hash(d, 2)),
          protein: Math.round(80 + 80 * hash(d, 3)),
        },
  );
}

export const RANGES: MacroRange[] = [
  { key: 'day', name: 'Day', period: 'Sunday, Mar 29, 2020', max: 750, change: 4.1, columns: DAY },
  { key: 'week', name: 'Week', period: 'Mar 23 – Mar 29, 2020', max: 2500, change: 16.2, columns: WEEK },
  { key: 'month', name: 'Month', period: 'March 2020', max: 2500, change: 8.7, columns: MONTH },
];

/** A range as the UI thread draws it: per column, its calories of carbs, fat and protein. */
export type Bars = { kcal: number[][]; max: number };

export function toBars(range: MacroRange): Bars {
  return {
    kcal: range.columns.map((day) => [day.protein * KCAL.protein, day.fat * KCAL.fat, day.carbs * KCAL.carbs]),
    max: range.max,
  };
}

/** Where the columns sit in a chart `width` wide: the pitch, and each column's width. */
export function columnLayout(count: number, width: number, widest: number, share: number) {
  'worklet';
  const pitch = width / count;
  return [pitch, Math.min(widest, pitch * share)];
}

export type Paints = {
  /** Bottom to top: protein, fat, carbs; each a gradient top to bottom. */
  colors: string[][];
  gap: number;
  radius: number;
  lift: number;
  dim: number;
  /** The colour under the chart, which a dimmed column fades toward. */
  card: string;
  widest: number;
  share: number;
};

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/**
 * The stacked columns as a picture. During a range switch every column of the larger set is
 * drawn, and each starts (or ends) at the column of the smaller set it shares a place with, so
 * seven wide columns split into thirty-one slim ones and gather back. `focusX` is the hovered
 * column's centre; the others dim by `focus`, and the hovered one lifts.
 */
export function drawBars(
  from: Bars,
  to: Bars,
  morph: number,
  focusX: number,
  focus: number,
  width: number,
  height: number,
  paints: Paints,
) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const nFrom = from.kcal.length;
  const nTo = to.kcal.length;
  const many = Math.max(nFrom, nTo);
  const [pitchFrom, wideFrom] = columnLayout(nFrom, width, paints.widest, paints.share);
  const [pitchTo, wideTo] = columnLayout(nTo, width, paints.widest, paints.share);
  const pitchNow = lerp(pitchFrom, pitchTo, morph);
  const max = lerp(from.max, to.max, morph);
  const shaders = [];
  for (let s = 0; s < 3; s++) {
    const colors = [];
    for (let c = 0; c < paints.colors[s].length; c++) colors.push(Skia.Color(paints.colors[s][c]));
    shaders.push(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: height }, colors, null, TileMode.Clamp));
  }
  const paint = Skia.Paint();
  // Dimming washes a column toward the card's white, which reads the same as fading it.
  const wash = Skia.Paint();
  wash.setColor(Skia.Color(paints.card));
  for (let k = 0; k < many; k++) {
    // This column's partner in each set: itself in the larger one, the nearest in the smaller.
    const a = nFrom === many ? k : Math.round((k * (nFrom - 1)) / Math.max(1, many - 1));
    const b = nTo === many ? k : Math.round((k * (nTo - 1)) / Math.max(1, many - 1));
    const x = lerp((a + 0.5) * pitchFrom, (b + 0.5) * pitchTo, morph);
    const w = lerp(wideFrom, wideTo, morph);
    const near = Math.max(0, 1 - Math.abs(x - focusX) / pitchNow);
    const lift = paints.lift * near * focus;
    const dim = paints.dim * focus * (1 - near);
    wash.setAlphaf(dim);
    let base = height - lift;
    for (let s = 0; s < 3; s++) {
      const kcal = lerp(from.kcal[a][s], to.kcal[b][s], morph);
      const h = (kcal / max) * height - (s > 0 ? paints.gap : 0);
      if (h <= 0.5) continue;
      if (s > 0) base -= paints.gap;
      const top = base - h;
      const path = Skia.Path.Make();
      if (s === 2) {
        // Only the top of the stack is rounded.
        const r = Math.min(paints.radius, w / 2, h);
        path.moveTo(x - w / 2, base);
        path.lineTo(x - w / 2, top + r);
        path.arcToTangent(x - w / 2, top, x, top, r);
        path.arcToTangent(x + w / 2, top, x + w / 2, top + r, r);
        path.lineTo(x + w / 2, base);
        path.close();
      } else path.addRect(Skia.XYWHRect(x - w / 2, top, w, h));
      paint.setShader(shaders[s]);
      canvas.drawPath(path, paint);
      if (dim > 0.01) canvas.drawPath(path, wash);
      base = top;
    }
  }
  return recorder.finishRecordingAsPicture();
}

/** The top of the stack under `x`, eased between neighbouring columns, in points from the bottom. */
export function stackTop(bars: Bars, x: number, width: number, height: number) {
  'worklet';
  const n = bars.kcal.length;
  const f = Math.min(n - 1, Math.max(0, x / (width / n) - 0.5));
  const i = Math.floor(f);
  const j = Math.min(n - 1, i + 1);
  const total = (k: number) => {
    'worklet';
    return bars.kcal[k][0] + bars.kcal[k][1] + bars.kcal[k][2];
  };
  return (lerp(total(i), total(j), f - i) / bars.max) * height;
}
