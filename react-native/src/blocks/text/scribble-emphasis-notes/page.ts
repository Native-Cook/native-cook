// Lays the paragraph out once with Skia and measures every word, so the marks sit on real text.
import { Skia, TextAlign, type SkParagraph } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

export type Band = { x0: number; x1: number; base: number };

export type Page = {
  paragraph: SkParagraph;
  x: number;
  y: number;
  width: number;
  height: number;
  fontSize: number;
  /** Per word: left edge, width, line index, sentence index. Canvas coordinates. */
  wordX: number[];
  wordW: number[];
  wordLine: number[];
  wordSentence: number[];
  /** Per sentence: first and last word. */
  sentenceFirst: number[];
  sentenceLast: number[];
  /** Per line: baseline, left and right edge of its ink. */
  lineBase: number[];
  lineLeft: number[];
  lineRight: number[];
  /** The bands (one per line) a character range covers. */
  bands: (start: number, end: number) => Band[];
};

export function makePage(text: string, x: number, y: number, width: number, fontSize: number, lineHeight: number, color: string): Page {
  const paragraph = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Left })
    .pushStyle({
      color: Skia.Color(color),
      fontFamilies: FONT,
      fontSize,
      fontStyle: { weight: 400 },
      heightMultiplier: lineHeight / fontSize,
      halfLeading: true,
      letterSpacing: -0.15,
    })
    .addText(text)
    .pop()
    .build();
  paragraph.layout(width);

  const metrics = paragraph.getLineMetrics();
  const lineBase = metrics.map((line) => y + line.baseline);
  const lineOf = (centre: number) => {
    let best = 0;
    let distance = Infinity;
    for (let i = 0; i < lineBase.length; i += 1) {
      // A glyph box's centre sits about a third of the font size above its baseline.
      const d = Math.abs(lineBase[i] - fontSize * 0.32 - centre);
      if (d < distance) {
        distance = d;
        best = i;
      }
    }
    return best;
  };

  const bands = (start: number, end: number): Band[] => {
    const out: Band[] = [];
    for (const rect of paragraph.getRectsForRange(start, end)) {
      if (rect.width < 0.5) continue;
      const line = lineOf(y + rect.y + rect.height / 2);
      const base = lineBase[line] ?? y + rect.y + rect.height;
      const last = out[out.length - 1];
      if (last && last.base === base) {
        last.x0 = Math.min(last.x0, x + rect.x);
        last.x1 = Math.max(last.x1, x + rect.x + rect.width);
      } else {
        out.push({ x0: x + rect.x, x1: x + rect.x + rect.width, base });
      }
    }
    return out;
  };

  const wordX: number[] = [];
  const wordW: number[] = [];
  const wordLine: number[] = [];
  const wordSentence: number[] = [];
  const sentenceFirst: number[] = [0];
  const sentenceLast: number[] = [];
  const lineLeft = lineBase.map(() => Infinity);
  const lineRight = lineBase.map(() => -Infinity);
  const words = /\S+/g;
  let match = words.exec(text);
  let sentence = 0;
  while (match) {
    const rect = paragraph.getRectsForRange(match.index, match.index + match[0].length)[0];
    if (rect) {
      const line = lineOf(y + rect.y + rect.height / 2);
      const index = wordX.length;
      wordX.push(x + rect.x);
      wordW.push(rect.width);
      wordLine.push(line);
      wordSentence.push(sentence);
      lineLeft[line] = Math.min(lineLeft[line], x + rect.x);
      lineRight[line] = Math.max(lineRight[line], x + rect.x + rect.width);
      if (/[.!?]$/.test(match[0])) {
        sentenceLast[sentence] = index;
        sentence += 1;
        sentenceFirst[sentence] = index + 1;
      }
    }
    match = words.exec(text);
  }
  if (sentenceLast.length < sentenceFirst.length) sentenceLast.push(wordX.length - 1);
  if (sentenceFirst[sentenceFirst.length - 1] >= wordX.length) {
    sentenceFirst.pop();
    sentenceLast.length = sentenceFirst.length;
  }

  return {
    paragraph,
    x,
    y,
    width,
    height: paragraph.getHeight(),
    fontSize,
    wordX,
    wordW,
    wordLine,
    wordSentence,
    sentenceFirst,
    sentenceLast,
    lineBase,
    lineLeft: lineLeft.map((value, i) => (Number.isFinite(value) ? value : x + (metrics[i]?.left ?? 0))),
    lineRight: lineRight.map((value, i) => (Number.isFinite(value) ? value : x + (metrics[i]?.left ?? 0) + (metrics[i]?.width ?? 0))),
    bands,
  };
}
