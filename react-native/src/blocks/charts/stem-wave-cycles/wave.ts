import { PaintStyle, type SkCanvas, Skia, StrokeCap } from '@shopify/react-native-skia';

/** A block of colour along a row: its width in points and its colour, or null for a gap. */
export type Block = [width: number, color: string | null];

const BLUE = '#2e86f5';
const LIME = '#e5ff00';
const LILAC = '#d08bff';
const GREEN = '#1f4d22';
const MINT = '#b8d4be';
const WHITE = '#fafafa';

// Each row repeats every 520 points, so it can scroll by one pattern and land where it started.
export const PATTERN = 520;
export const STRIP: Block[] = [
  [60, BLUE],
  [90, LILAC],
  [44, LIME],
  [70, WHITE],
  [56, MINT],
  [38, GREEN],
  [80, LILAC],
  [42, BLUE],
  [40, LIME],
];
/** The thinner rows that overlap the strip's top and bottom edges while the stems stand. */
export const ROW_ABOVE: Block[] = [
  [40, null],
  [78, GREEN],
  [26, BLUE],
  [120, null],
  [60, GREEN],
  [30, BLUE],
  [166, null],
];
export const ROW_BELOW: Block[] = [
  [90, null],
  [30, BLUE],
  [70, LIME],
  [16, MINT],
  [150, null],
  [24, BLUE],
  [80, LIME],
  [60, null],
];

/** autoPlay, in seconds from the start of a loop. */
export const LOOP = 9;
const ROLL = [0.8, 3];
/** Where a tap plays to: the zoomed curve, and back to the stems. */
export const RETRACT_START = 3;
export const CURVE_HELD = 6.1;
export const RETURN_START = 7.1;
export const STEMS_BACK = 8.65;
const ROW_SHIFT = 40;
const ROW_SHIFT_FOR = 0.9;
const ROW_STAGGER = 0.05;
const ROWS_OUT = [3, 3.4];
const RETRACT_AT = RETRACT_START;
const STEM_FOR = 0.4;
const RETRACT_STAGGER = 0.025;
const CURVE_DRAW = [3.2, 3.9];
const LINES_IN = [3.6, 4.3];
const ZOOM_IN = [4.6, CURVE_HELD];
const DOTS_OUT = [4.6, 4.9];
const MARKS_IN = [5.8, CURVE_HELD];
const MARKS_OUT = [7.1, 7.3];
const ZOOM_OUT = [7.1, 7.9];
const DOTS_IN = [7.7, 7.9];
const CURVE_UNDRAW = [7.9, 8.4];
const LINES_OUT = [7.9, 8.3];
const GROW_AT = 7.9;
const GROW_STAGGER = 0.015;
const ROWS_IN = [8.2, 8.6];
// The strip drifts one pattern to the left over the loop, still at rest.
const DRIFT = [0.8, STEMS_BACK];

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(value: number) {
  'worklet';
  const u = 1 - clamp01(value);
  return 1 - u * u * u;
}

/**
 * The scene at a time of the loop. `phase` turns the wave (radians), `zoom` goes from a period
 * of PERIOD_STEMS stems (0) to one period across the screen (1). `still` keeps the wave from
 * rolling and the zoom from gliding, and fades instead.
 */
export function timeline(t: number, still: boolean) {
  'worklet';
  const roll = smooth(t, ROLL[0], ROLL[1]);
  const zoomIn = smooth(t, ZOOM_IN[0], ZOOM_IN[1]);
  const zoomOut = smooth(t, ZOOM_OUT[0], ZOOM_OUT[1]);
  const zoomMid = (ZOOM_IN[0] + ZOOM_IN[1]) / 2;
  const zoomBackMid = (ZOOM_OUT[0] + ZOOM_OUT[1]) / 2;
  return {
    phase: still ? 0 : -roll * Math.PI * 2,
    zoom: still ? (t >= zoomMid && t < zoomBackMid ? 1 : 0) : zoomIn * (1 - zoomOut),
    // Under reduced motion the zoom is a cut, hidden in a dip of the wave's opacity.
    dip: still ? 1 - dipAt(t, zoomMid) - dipAt(t, zoomBackMid) : 1,
    rows: (1 - smooth(t, ROWS_OUT[0], ROWS_OUT[1])) + smooth(t, ROWS_IN[0], ROWS_IN[1]),
    rowShift: t < ROWS_OUT[1] ? t : -1,
    curve: still ? (t >= CURVE_DRAW[0] && t < CURVE_UNDRAW[1] ? 1 : 0) : smooth(t, CURVE_DRAW[0], CURVE_DRAW[1]) * (1 - smooth(t, CURVE_UNDRAW[0], CURVE_UNDRAW[1])),
    curveAlpha: still ? smooth(t, CURVE_DRAW[0], CURVE_DRAW[1]) * (1 - smooth(t, CURVE_UNDRAW[0], CURVE_UNDRAW[1])) : 1,
    lines: smooth(t, LINES_IN[0], LINES_IN[1]) * (1 - smooth(t, LINES_OUT[0], LINES_OUT[1])),
    dots: 1 - smooth(t, DOTS_OUT[0], DOTS_OUT[1]) + smooth(t, DOTS_IN[0], DOTS_IN[1]),
    marks: smooth(t, MARKS_IN[0], MARKS_IN[1]) * (1 - smooth(t, MARKS_OUT[0], MARKS_OUT[1])),
    drift: still ? 0 : smooth(t, DRIFT[0], DRIFT[1]) * PATTERN,
  };
}

/** A short dip to 0 and back around `at`, for cuts under reduced motion. */
function dipAt(t: number, at: number) {
  'worklet';
  return Math.sin(Math.PI * smooth(t, at - 0.2, at + 0.2));
}

/**
 * How much of stem `i` stands at a time: 1 standing, 0 gone. While the curve takes over, each
 * stem retracts into its dot, left to right; on the way back each grows out of the strip again.
 * The second number is 1 while the stem hangs from its dot (retracting), 0 while it grows.
 */
export function stem(i: number, t: number, still: boolean) {
  'worklet';
  if (still) {
    const away = smooth(t, RETRACT_AT, RETRACT_AT + STEM_FOR) * (1 - smooth(t, GROW_AT, GROW_AT + STEM_FOR));
    return [1 - away, 1];
  }
  if (t < GROW_AT) return [1 - smooth(t, RETRACT_AT + i * RETRACT_STAGGER, RETRACT_AT + i * RETRACT_STAGGER + STEM_FOR), 1];
  return [easeOut((t - GROW_AT - i * GROW_STAGGER) / STEM_FOR), 0];
}

/** The sideways shift of block `k` of a row while the stems roll: a wipe with a stagger. */
export function rowShift(k: number, t: number) {
  'worklet';
  if (t < 0) return 0;
  // Out and back within the roll, so the rows rest where they started.
  return ROW_SHIFT * Math.sin(Math.PI * smooth(t, ROLL[0] + k * ROW_STAGGER, ROLL[0] + k * ROW_STAGGER + ROW_SHIFT_FOR * 2));
}

export type Look = {
  width: number;
  height: number;
  /** The strip's centre line. */
  axis: number;
  amplitude: number;
  pitch: number;
  periodStems: number;
  strip: number;
  row: number;
  overlap: number;
  dot: number;
  ink: string;
  curveInk: string;
  lineInk: string;
};

function drawRow(
  canvas: SkCanvas,
  blocks: Block[],
  y: number,
  h: number,
  offset: number,
  shiftAt: number,
  direction: number,
  width: number,
) {
  'worklet';
  if (h <= 0.05) return;
  const paint = Skia.Paint();
  // Tile the pattern across the screen from the drifted start.
  let start = -(((offset % PATTERN) + PATTERN) % PATTERN);
  while (start < width) {
    let x = start;
    for (let k = 0; k < blocks.length; k++) {
      const [w, color] = blocks[k];
      if (color !== null) {
        const shift = direction * rowShift(k, shiftAt);
        if (x + shift < width && x + shift + w > 0) {
          paint.setColor(Skia.Color(color));
          canvas.drawRect(Skia.XYWHRect(x + shift, y, w, h), paint);
        }
      }
      x += w;
    }
    start += PATTERN;
  }
}

/**
 * The whole picture at one moment: hairlines, stems, the colour strip and its rows, the curve,
 * the dots and the crest and trough markers. Stems, dots and curve sample the same sine.
 */
export function drawWave(t: number, phase: number, zoom: number, still: boolean, look: Look) {
  'worklet';
  const s = timeline(t, still);
  const { width, axis, amplitude } = look;
  const cx = width / 2;
  const p0 = look.pitch * look.periodStems;
  // The period grows from p0 to the width; past either end the pinch keeps going a little.
  const period = p0 * Math.pow(width / p0, zoom);
  const spread = period / p0;
  const angle = phase;
  const yAt = (x: number) => {
    'worklet';
    return axis + amplitude * Math.sin(((x - cx) / period) * Math.PI * 2 + angle);
  };

  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, look.height));
  const stroke = Skia.Paint();
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeCap(StrokeCap.Round);
  const fill = Skia.Paint();

  // Hairlines at the full and the half amplitude, sliding in from the left.
  if (s.lines > 0) {
    stroke.setColor(Skia.Color(look.lineInk));
    stroke.setStrokeWidth(0.75);
    for (let k = -2; k <= 2; k++) {
      if (k === 0) continue;
      const y = axis + (k * amplitude) / 2;
      canvas.drawLine(0, y, width * s.lines, y, stroke);
    }
  }

  // The stems, from the strip to their dots.
  const count = Math.ceil(width / look.pitch) + 2;
  const dotsAlpha = clamp01(s.dots) * s.dip;
  stroke.setColor(Skia.Color(look.ink));
  stroke.setStrokeWidth(1);
  fill.setColor(Skia.Color(look.ink));
  for (let i = 0; i < count; i++) {
    const x = cx + (i - (count - 1) / 2) * look.pitch * spread;
    if (x < -10 || x > width + 10) continue;
    const y = yAt(x);
    const [stand, hanging] = stem(i, t, still);
    stroke.setAlphaf((still ? stand : 1) * s.dip);
    if (stand > 0.001) {
      if (hanging === 1) canvas.drawLine(x, y + (axis - y) * (still ? 1 : stand), x, y, stroke);
      else canvas.drawLine(x, axis, x, axis + (y - axis) * stand, stroke);
    }
    // While growing back, a dot rides the top of its stem.
    const dy = hanging === 1 ? y : axis + (y - axis) * stand;
    fill.setAlphaf(dotsAlpha);
    if (dotsAlpha > 0.001) canvas.drawCircle(x, dy, look.dot, fill);
  }

  // The strip, and the rows over its edges while the stems stand.
  const half = look.strip / 2;
  const rowHeight = look.row * clamp01(s.rows);
  drawRow(canvas, ROW_ABOVE, axis - half - rowHeight + look.overlap, rowHeight, s.drift, s.rowShift, 1, width);
  drawRow(canvas, ROW_BELOW, axis + half - look.overlap, rowHeight, s.drift, s.rowShift, -1, width);
  drawRow(canvas, STRIP, axis - half, look.strip, s.drift, -1, 0, width);

  // The curve, drawn from the left.
  if (s.curve > 0.001 && s.curveAlpha > 0.001) {
    const path = Skia.Path.Make();
    const end = width * s.curve;
    const step = 3;
    path.moveTo(0, yAt(0));
    for (let x = step; x < end; x += step) path.lineTo(x, yAt(x));
    path.lineTo(end, yAt(end));
    stroke.setColor(Skia.Color(look.curveInk));
    stroke.setStrokeWidth(1.25);
    stroke.setAlphaf(s.curveAlpha * s.dip);
    canvas.drawPath(path, stroke);
  }

  // Crest and trough of the period on screen, joined to the axis.
  if (s.marks > 0.001) {
    const shiftX = (-angle / (Math.PI * 2)) * period;
    let crest = cx - period / 4 + shiftX;
    while (crest < cx - period / 2) crest += period;
    while (crest > cx + period / 2) crest -= period;
    let trough = crest + period / 2;
    if (trough > cx + period / 2) trough -= period;
    stroke.setColor(Skia.Color(look.ink));
    stroke.setStrokeWidth(1);
    stroke.setAlphaf(s.marks * s.dip);
    fill.setAlphaf(s.marks * s.dip);
    const marks = [crest, trough];
    for (let m = 0; m < 2; m++) {
      const x = marks[m];
      const y = yAt(x);
      canvas.drawLine(x, axis, x, axis + (y - axis) * s.marks, stroke);
      canvas.drawCircle(x, y, look.dot, fill);
      canvas.drawCircle(x, axis, look.dot * 0.8, fill);
    }
  }
  return recorder.finishRecordingAsPicture();
}
