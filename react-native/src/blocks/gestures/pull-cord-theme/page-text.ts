/**
 * The page's type, laid out once with Skia paragraphs. Glyphs are drawn as a mask (black) and coloured by the page
 * shader, so every line changes colour exactly where the light's front crosses it.
 */
import { Skia, TextAlign, type SkParagraph, type SkTypefaceFontProvider } from '@shopify/react-native-skia';

import { BOOK, TYPE } from './theme';

export type Role = 'ink' | 'mute' | 'accent';
export type Line = { p: SkParagraph; x: number; y: number; w: number; role: Role };
export type Bar = { x: number; y: number; w: number; h: number; role: Role; alpha: number };
export type PageText = { lines: Line[]; bars: Bar[]; text: string };

const MASK = Skia.Color('black');

export function buildPage(fonts: SkTypefaceFontProvider, w: number, h: number, s: number): PageText {
  const m = TYPE.margin * s;
  const measure = w - 2 * m;
  const para = (text: string, size: number, line: number, opts: { tracking?: number; align?: TextAlign; maxLines?: number } = {}) => {
    // A forced strut fixes the line height the same way on device and in the offline renders.
    const strutStyle = { strutEnabled: true, forceStrutHeight: true, fontFamilies: [TYPE.family], fontSize: size * s, heightMultiplier: line / size, halfLeading: true };
    // The native parser reads every key it is given, so maxLines is only passed when set.
    const style = { textAlign: opts.align ?? TextAlign.Left, strutStyle, ...(opts.maxLines ? { maxLines: opts.maxLines } : {}) };
    const p = Skia.ParagraphBuilder.Make(style, fonts)
      .pushStyle({
        color: MASK,
        fontFamilies: [TYPE.family],
        fontSize: size * s,
        letterSpacing: (opts.tracking ?? 0) * s,
        heightMultiplier: line / size,
        halfLeading: true,
      })
      .addText(text)
      .build();
    p.layout(measure);
    return p;
  };

  const labelTop = h - (TYPE.footBottom + TYPE.footLine) * s;
  const barY = labelTop - (TYPE.barGap + TYPE.barHeight) * s;
  const bodyTop = TYPE.bodyTop * s;
  const bodyLines = Math.floor((barY - 24 * s - bodyTop) / (TYPE.bodyLine * s));
  // Book setting: the first paragraph after a heading is not indented, the rest are, with no space between.
  const body = BOOK.body.map((t, i) => (i === 0 ? t : ` ${t}`)).join('\n');
  const pageNo = `Page ${BOOK.page} of ${BOOK.pages}`;

  const lines: Line[] = [
    { p: para(BOOK.chapter, TYPE.eyebrowSize, TYPE.eyebrowLine, { tracking: TYPE.eyebrowTracking }), x: m, y: TYPE.eyebrowTop * s, w: measure, role: 'mute' },
    { p: para(BOOK.heading, TYPE.titleSize, TYPE.titleLine), x: m, y: TYPE.titleTop * s, w: measure, role: 'ink' },
    { p: para(body, TYPE.bodySize, TYPE.bodyLine, { maxLines: bodyLines }), x: m, y: bodyTop, w: measure, role: 'ink' },
    { p: para(pageNo, TYPE.footSize, TYPE.footLine), x: m, y: labelTop, w: measure, role: 'mute' },
    { p: para(BOOK.left, TYPE.footSize, TYPE.footLine, { align: TextAlign.Right }), x: m, y: labelTop, w: measure, role: 'mute' },
  ];
  const progress = BOOK.page / BOOK.pages;
  const bars: Bar[] = [
    { x: m, y: TYPE.ruleTop * s, w: TYPE.ruleWidth * s, h: TYPE.ruleHeight * s, role: 'accent', alpha: 1 },
    { x: m, y: barY, w: measure, h: TYPE.barHeight * s, role: 'mute', alpha: 0.3 },
    { x: m, y: barY, w: measure * progress, h: TYPE.barHeight * s, role: 'accent', alpha: 1 },
  ];
  // Read aloud as written: a full stop only where a line does not end in one.
  const text = [BOOK.chapter, BOOK.heading, ...BOOK.body, pageNo, BOOK.left]
    .map((t) => (/[.!?:]$/.test(t) ? t : `${t}.`))
    .join(' ');
  return { lines, bars, text };
}
