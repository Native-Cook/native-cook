// Ink Wash Page Turn — the three pages, each drawn once into an image.
//
// A page is warm paper, a date, a title, a short poem set in the system font and a small ink-wash
// drawing under it. Nothing here runs per frame: the images are baked when the screen's size is known
// and the bleed (iwp-shader.ts) only samples them.

import { BlurStyle, PaintStyle, type SkCanvas, type SkImage, type SkPaint, Skia, StrokeCap, TextAlign, TileMode } from '@shopify/react-native-skia';
import { PixelRatio, Platform } from 'react-native';

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

export const PAPER = '#F3ECDD';
const PAPER_EDGE = '#E9DFCB';
const TEXT = '#26231E';
const MUTE = '#8C8576';
const WASH = '#2A2B36';
const SEAL = '#C2553C';
const OCHRE = '#D9A441';

export const POEMS = [
  {
    date: 'Saturday, 14 March',
    title: 'Low Tide',
    body: 'The harbour pulls its blanket back\nand shows us what it kept:\na rope, a shoe, a ring of keys,\nthe print where a heron stepped.\n\nWe walk out further than we should\non ground that was the sea.\nNothing out here is in a hurry.\nFor an hour, neither are we.',
  },
  {
    date: 'Sunday, 15 March',
    title: 'Kettle',
    body: 'Before the house is properly awake\nthe kettle starts on its complaint.\nSteam finds the glass, writes a line,\nand rubs it out again.\n\nI hold the cup with both my hands\nthe way you hold good news,\nnot drinking yet, just letting it\nbe warm, be mine, be morning.',
  },
  {
    date: 'Monday, 16 March',
    title: 'Last Bus',
    body: 'The last bus hums along the coast\nwith four of us inside,\neach window carrying a moon\nit did not ask to hold.\n\nNobody speaks. The driver knows\nthe road the way you know a song.\nWe are delivered, one by one,\nto porches with the lights left on.',
  },
] as const;

export type PageLayout = {
  width: number;
  height: number;
  /** safe-area insets, pt */
  top: number;
  bottom: number;
};

function paragraph(str: string, size: number, line: number, weight: number, color: string, width: number, spacing: number) {
  const builder = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Left });
  builder.pushStyle({ color: Skia.Color(color), fontFamilies: [FONT], fontSize: size, fontStyle: { weight }, heightMultiplier: line / size, halfLeading: true, letterSpacing: spacing });
  builder.addText(str);
  const built = builder.build();
  built.layout(width);
  return built;
}

function wash(color: string, alpha: number, blur: number): SkPaint {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  paint.setAlphaf(alpha);
  if (blur > 0) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return paint;
}

function brush(color: string, alpha: number, blur: number, width: number): SkPaint {
  const paint = wash(color, alpha, blur);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  paint.setStrokeCap(StrokeCap.Round);
  return paint;
}

/** Low Tide: a small red sun over four tide lines that thin out towards the shore */
function drawTide(c: SkCanvas, x: number, y: number, w: number, h: number) {
  c.drawCircle(x + w * 0.74, y + h * 0.22, h * 0.13, wash(SEAL, 0.78, 1.2));
  const rows = [
    [0.02, 0.98, 0.5, 11, 0.5],
    [0.14, 0.9, 0.66, 8, 0.36],
    [0.3, 0.78, 0.8, 6, 0.26],
    [0.44, 0.66, 0.92, 4, 0.18],
  ];
  for (const [x0, x1, at, width, alpha] of rows) {
    const path = Skia.PathBuilder.Make();
    path.moveTo(x + w * x0, y + h * at);
    path.cubicTo(x + w * (x0 + (x1 - x0) * 0.3), y + h * (at - 0.035), x + w * (x0 + (x1 - x0) * 0.68), y + h * (at + 0.035), x + w * x1, y + h * at);
    c.drawPath(path.build(), brush(WASH, alpha, 2.4, width));
  }
}

/** Kettle: a cup seen from the side and three threads of steam */
function drawSteam(c: SkCanvas, x: number, y: number, w: number, h: number) {
  const cx = x + w * 0.5;
  const rim = y + h * 0.66;
  const cup = Skia.PathBuilder.Make();
  cup.moveTo(cx - h * 0.2, rim);
  cup.cubicTo(cx - h * 0.19, rim + h * 0.26, cx - h * 0.1, rim + h * 0.3, cx, rim + h * 0.3);
  cup.cubicTo(cx + h * 0.1, rim + h * 0.3, cx + h * 0.19, rim + h * 0.26, cx + h * 0.2, rim);
  cup.close();
  c.drawPath(cup.build(), wash(WASH, 0.5, 2.2));
  c.drawOval(Skia.XYWHRect(cx - h * 0.2, rim - h * 0.028, h * 0.4, h * 0.056), wash(WASH, 0.62, 1));
  const handle = Skia.PathBuilder.Make();
  handle.moveTo(cx + h * 0.19, rim + h * 0.06);
  handle.cubicTo(cx + h * 0.3, rim + h * 0.04, cx + h * 0.3, rim + h * 0.2, cx + h * 0.16, rim + h * 0.2);
  c.drawPath(handle.build(), brush(WASH, 0.46, 1.6, 5));
  const threads = [
    [-0.09, 0.3, 5],
    [0.0, 0.42, 7],
    [0.09, 0.26, 4],
  ];
  for (const [dx, alpha, width] of threads) {
    const path = Skia.PathBuilder.Make();
    const sx = cx + h * dx;
    path.moveTo(sx, rim - h * 0.08);
    path.cubicTo(sx - h * 0.09, rim - h * 0.22, sx + h * 0.1, rim - h * 0.32, sx, rim - h * 0.46);
    path.cubicTo(sx - h * 0.07, rim - h * 0.54, sx + h * 0.05, rim - h * 0.6, sx + h * 0.01, rim - h * 0.66);
    c.drawPath(path.build(), brush(WASH, alpha, 3.2, width));
  }
  c.drawCircle(cx - h * 0.34, rim + h * 0.24, h * 0.035, wash(SEAL, 0.78, 0.8));
}

/** Last Bus: a pale moon, two headlands and four lit windows on the road between them */
function drawCoast(c: SkCanvas, x: number, y: number, w: number, h: number) {
  c.drawCircle(x + w * 0.3, y + h * 0.3, h * 0.2, wash(OCHRE, 0.5, 3));
  const far = Skia.PathBuilder.Make();
  far.moveTo(x, y + h * 0.78);
  far.cubicTo(x + w * 0.2, y + h * 0.5, x + w * 0.42, y + h * 0.56, x + w * 0.62, y + h * 0.72);
  far.cubicTo(x + w * 0.76, y + h * 0.82, x + w * 0.9, y + h * 0.7, x + w, y + h * 0.74);
  far.lineTo(x + w, y + h);
  far.lineTo(x, y + h);
  far.close();
  c.drawPath(far.build(), wash(WASH, 0.26, 4));
  const near = Skia.PathBuilder.Make();
  near.moveTo(x, y + h * 0.94);
  near.cubicTo(x + w * 0.3, y + h * 0.8, x + w * 0.56, y + h * 0.9, x + w * 0.74, y + h * 0.86);
  near.cubicTo(x + w * 0.86, y + h * 0.83, x + w * 0.94, y + h * 0.9, x + w, y + h * 0.9);
  near.lineTo(x + w, y + h);
  near.lineTo(x, y + h);
  near.close();
  c.drawPath(near.build(), wash(WASH, 0.52, 2.4));
  for (let i = 0; i < 4; i++) c.drawCircle(x + w * (0.6 + i * 0.03), y + h * (0.846 - i * 0.003), 2.2, wash(SEAL, 0.9, 0.5));
}

function drawPage(c: SkCanvas, index: number, l: PageLayout) {
  const { width, height } = l;
  const compact = height < 740;
  const margin = 32;
  // warm paper, a little darker towards the corners
  const paper = Skia.Paint();
  paper.setShader(
    Skia.Shader.MakeRadialGradient({ x: width / 2, y: height * 0.42 }, Math.hypot(width, height) * 0.62, [Skia.Color(PAPER), Skia.Color(PAPER), Skia.Color(PAPER_EDGE)], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, width, height), paper);

  const poem = POEMS[index];
  let y = l.top + (compact ? 58 : 84);
  const date = paragraph(poem.date, 13, 18, 500, MUTE, width - margin * 2, 0);
  date.paint(c, margin, y);
  y += 18 + (compact ? 4 : 8);
  const title = paragraph(poem.title, compact ? 28 : 34, compact ? 34 : 40, 700, TEXT, width - margin * 2, -0.6);
  title.paint(c, margin, y);
  y += title.getHeight() + (compact ? 12 : 20);
  const body = paragraph(poem.body, compact ? 16 : 17, compact ? 24 : 28, 400, TEXT, width - margin * 2, -0.1);
  body.paint(c, margin, y);
  y += body.getHeight();

  // the drawing fills what is left above the page dots
  const floor = height - Math.max(l.bottom, 12) - (compact ? 56 : 84);
  const room = Math.max(0, floor - y - (compact ? 14 : 28));
  const h = Math.min(room, compact ? 150 : 210);
  if (h < 70) return;
  const top = floor - h;
  const w = width - margin * 2;
  if (index === 0) drawTide(c, margin, top, w, h);
  else if (index === 1) drawSteam(c, margin, top, w, h);
  else drawCoast(c, margin, top, w, h);
}

/** The three pages as images, `scale` pixels per point; null if no surface could be made. */
export function bakePages(l: PageLayout): SkImage[] | null {
  const scale = Math.min(3, PixelRatio.get());
  const pw = Math.ceil(l.width * scale);
  const ph = Math.ceil(l.height * scale);
  const out: SkImage[] = [];
  for (let i = 0; i < POEMS.length; i++) {
    const surface = Skia.Surface.MakeOffscreen(pw, ph) ?? Skia.Surface.Make(pw, ph);
    if (!surface) return null;
    const c = surface.getCanvas();
    c.scale(scale, scale);
    drawPage(c, i, l);
    surface.flush();
    const image = surface.makeImageSnapshot();
    out.push(image.makeNonTextureImage() ?? image);
  }
  return out;
}
