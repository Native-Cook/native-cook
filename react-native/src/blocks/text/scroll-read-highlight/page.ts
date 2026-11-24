// Lays the article out once with Skia: one recorded picture per paragraph in each colour, and a table of every word.
import { ClipOp, Skia, TextAlign, type SkCanvas, type SkParagraph, type SkPicture } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import type { Article } from './data';

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

export type PageColors = { ink: string; dim: string; soft: string; coverTop: string; coverMid: string; coverLow: string; hill: string; hillNear: string; sun: string };

export type Block = { y: number; height: number; first: number; last: number; dim: SkPicture; ink: SkPicture };

export type Page = {
  head: SkPicture;
  headHeight: number;
  blocks: Block[];
  height: number;
  wordCount: number;
  firstBodyTop: number;
  lastBottom: number;
  wordX: number[];
  wordY: number[];
  wordW: number[];
  wordH: number[];
  lineTop: number[];
  lineHeight: number[];
  lineFirst: number[];
  lineCount: number[];
};

function paragraphOf(text: string, width: number, size: number, leading: number, color: string, weight: number, spacing: number): SkParagraph {
  const paragraph = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Left })
    .pushStyle({ color: Skia.Color(color), fontFamilies: FONT, fontSize: size, fontStyle: { weight }, heightMultiplier: leading / size, halfLeading: true, letterSpacing: spacing })
    .addText(text)
    .pop()
    .build();
  paragraph.layout(width);
  return paragraph;
}

function record(width: number, height: number, draw: (canvas: SkCanvas) => void) {
  const recorder = Skia.PictureRecorder();
  draw(recorder.beginRecording(Skia.XYWHRect(0, 0, width, height)));
  return recorder.finishRecordingAsPicture();
}

function hillPath(x: number, y: number, width: number, height: number, a: number, b: number) {
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(x, y + height);
  builder.lineTo(x, y + height * a);
  builder.cubicTo(x + width * 0.28, y + height * (a - 0.2), x + width * 0.52, y + height * (b + 0.16), x + width, y + height * b);
  builder.lineTo(x + width, y + height);
  builder.close();
  return builder.build();
}

/** A dusk over water, drawn: three-stop sky, a low sun, two headlands and a few strokes of light on the sea. */
function drawCover(canvas: SkCanvas, x: number, y: number, width: number, height: number, colors: PageColors) {
  const frame = Skia.RRectXY(Skia.XYWHRect(x, y, width, height), 18, 18);
  canvas.save();
  canvas.clipRRect(frame, ClipOp.Intersect, true);
  const sky = Skia.Paint();
  sky.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + height }, [Skia.Color(colors.coverTop), Skia.Color(colors.coverMid), Skia.Color(colors.coverLow)], [0, 0.56, 1], 0));
  canvas.drawRect(Skia.XYWHRect(x, y, width, height), sky);
  const sun = Skia.Paint();
  sun.setShader(Skia.Shader.MakeRadialGradient({ x: x + width * 0.66, y: y + height * 0.56 }, height * 0.5, [Skia.Color(colors.sun), Skia.Color('rgba(244,190,130,0)')], [0.08, 1], 0));
  canvas.drawRect(Skia.XYWHRect(x, y, width, height), sun);
  const far = Skia.Paint();
  far.setAntiAlias(true);
  far.setColor(Skia.Color(colors.hill));
  canvas.drawPath(hillPath(x, y + height * 0.4, width, height * 0.3, 0.72, 0.38), far);
  const near = Skia.Paint();
  near.setAntiAlias(true);
  near.setColor(Skia.Color(colors.hillNear));
  canvas.drawPath(hillPath(x, y + height * 0.52, width * 0.62, height * 0.2, 0.3, 1), near);
  const water = Skia.Paint();
  water.setColor(Skia.Color('#20262E'));
  canvas.drawRect(Skia.XYWHRect(x, y + height * 0.7, width, height * 0.3), water);
  const glint = Skia.Paint();
  glint.setAntiAlias(true);
  glint.setColor(Skia.Color('rgba(247,201,150,0.3)'));
  for (let i = 0; i < 6; i += 1) {
    const seed = Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1;
    const spread = (i + 1) * 3.4;
    const w = 10 + i * 5 + seed * 10;
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + width * 0.66 - w / 2 + (seed - 0.5) * spread * 2, y + height * 0.74 + i * height * 0.042, w, 1.2), 1, 1), glint);
  }
  canvas.restore();
}

export function makePage(article: Article, x: number, width: number, bodySize: number, bodyLeading: number, colors: PageColors): Page {
  const canvasWidth = x * 2 + width;
  const titleSize = bodySize >= 19 ? 34 : 30;
  const title = paragraphOf(article.title, width, titleSize, titleSize * 1.12, colors.ink, 700, -0.5);
  const byline = paragraphOf(article.byline, width, 15, 20, colors.soft, 500, 0);
  const titleY = 16;
  const bylineY = titleY + title.getHeight() + 8;
  const coverY = bylineY + 20 + 18;
  const coverHeight = Math.round(Math.min(width * 0.5, 210));
  const headHeight = coverY + coverHeight + 24;
  const head = record(canvasWidth, headHeight, (canvas) => {
    title.paint(canvas, x, titleY);
    byline.paint(canvas, x, bylineY);
    drawCover(canvas, x, coverY, width, coverHeight, colors);
  });

  const blocks: Block[] = [];
  const wordX: number[] = [];
  const wordY: number[] = [];
  const wordW: number[] = [];
  const wordH: number[] = [];
  const lineTop: number[] = [];
  const lineHeight: number[] = [];
  const lineFirst: number[] = [];
  const lineCount: number[] = [];
  let y = headHeight;

  for (const text of article.paragraphs) {
    const dim = paragraphOf(text, width, bodySize, bodyLeading, colors.dim, 400, -0.1);
    const ink = paragraphOf(text, width, bodySize, bodyLeading, colors.ink, 400, -0.1);
    const height = ink.getHeight();
    const first = wordX.length;
    const pattern = /\S+/g;
    let found = pattern.exec(text);
    let rowTop = -Infinity;
    while (found) {
      const rect = ink.getRectsForRange(found.index, found.index + found[0].length)[0];
      if (rect) {
        const top = y + rect.y;
        if (Math.abs(top - rowTop) > 2) {
          rowTop = top;
          lineTop.push(top);
          lineHeight.push(rect.height);
          lineFirst.push(wordX.length);
          lineCount.push(0);
        }
        lineCount[lineCount.length - 1] += 1;
        wordX.push(x + rect.x);
        wordY.push(top);
        wordW.push(rect.width);
        wordH.push(rect.height);
      }
      found = pattern.exec(text);
    }
    const blockY = y;
    blocks.push({
      y: blockY,
      height,
      first,
      last: wordX.length - 1,
      dim: record(canvasWidth, height, (canvas) => dim.paint(canvas, x, 0)),
      ink: record(canvasWidth, height, (canvas) => ink.paint(canvas, x, 0)),
    });
    y += height + Math.round(bodyLeading * 0.62);
  }

  const lastLine = lineTop.length - 1;
  return {
    head,
    headHeight,
    blocks,
    height: y + 40,
    wordCount: wordX.length,
    firstBodyTop: lineTop[0] ?? headHeight,
    lastBottom: lastLine >= 0 ? lineTop[lastLine] + lineHeight[lastLine] : y,
    wordX,
    wordY,
    wordW,
    wordH,
    lineTop,
    lineHeight,
    lineFirst,
    lineCount,
  };
}
