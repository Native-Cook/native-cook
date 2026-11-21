// Text Effect Keys: every string is shaped once by Skia's paragraph engine (Geist's kerning kept), then drawn glyph
// by glyph with those positions, so a label at rest is the same as plain text and each glyph can still move alone.

import { FontStyle, Skia, type SkFont, type SkPath, type SkTypefaceFontProvider } from '@shopify/react-native-skia';

import { CHEVRON, MONO_O, PLUS, SEND_ARROW } from './tek-icons';
import { type Geo, makeGeo } from './tek-motion';
import { COPY, EFFECTS, FONT, G } from './tek-theme';

export type Run = {
  font: SkFont;
  ids: number[];
  xs: number[]; // pen x of each glyph
  ws: number[]; // advance of each glyph
  w: number; // advance of the run
};

/** Ink height of a glyph at 15 pt (Geist), for pivots and for standing a letter on a key top. */
const INK: Record<string, number> = { W: 10.65, h: 10.65, a: 8.16, t: 9.86 };

const makeRun = (mgr: SkTypefaceFontProvider, text: string, family: string, size: number): Run => {
  const tf = mgr.matchFamilyStyle(family, FontStyle.Normal);
  const font = Skia.Font(tf, size);
  const p = Skia.ParagraphBuilder.Make({}, mgr).pushStyle({ fontFamilies: [family], fontSize: size }).addText(text).pop().build();
  p.layout(100000);
  const ids = font.getGlyphIDs(text);
  const xs: number[] = [];
  const ws: number[] = [];
  let pen = 0;
  for (let i = 0; i < text.length; i++) {
    const r = p.getRectsForRange(i, i + 1);
    const x = r.length ? r[0].x : pen;
    const w = r.length ? r[0].width : 0;
    xs.push(x);
    ws.push(w);
    pen = x + w;
  }
  return { font, ids, xs, ws, w: pen };
};

export type Res = {
  stamp: Run;
  received: Run[];
  old: Run;
  seen: Run;
  name: Run;
  placeholder: Run;
  labels: Run[];
  fmt: Run[];
  heroR: Run; // the word in Regular (the field)
  heroM: Run; // the word in Medium (bubble, flight, stage)
  geo: Geo;
  icons: { plus: SkPath; arrow: SkPath; chevron: SkPath; mono: SkPath };
};

export const makeRes = (mgr: SkTypefaceFontProvider): Res => {
  const heroR = makeRun(mgr, COPY.word, FONT.regular, 15);
  const heroM = makeRun(mgr, COPY.word, FONT.medium, 15);
  const received = G.received.map((r) => makeRun(mgr, r.text, FONT.regular, 15));
  const old = makeRun(mgr, COPY.old, FONT.medium, 15);
  const pad = 12;
  const spans = [
    [G.receivedX, G.receivedX + received[0].w + 2 * pad, G.received[0].y],
    [G.receivedX, G.receivedX + received[1].w + 2 * pad, G.received[1].y],
    [G.bubbleRight - old.w - 2 * G.bubblePad, G.bubbleRight, G.oldY],
  ];
  const ink = COPY.word.split('').map((c) => INK[c] ?? 10.65);
  const geo = makeGeo(heroR.ws, heroM.ws, ink, spans);
  // the field's pen positions come from the shaped run (kerning), not from summed advances
  geo.fieldX = heroR.xs.map((x, i) => G.fieldTextX + x + heroR.ws[i] / 2);
  geo.bubbleX = heroM.xs.map((x, i) => geo.bubbleL + G.bubblePad + x + heroM.ws[i] / 2);
  return {
    stamp: makeRun(mgr, COPY.stamp, FONT.medium, 13),
    received,
    old,
    seen: makeRun(mgr, COPY.seen, FONT.semibold, 13),
    name: makeRun(mgr, COPY.name, FONT.semibold, 13),
    placeholder: makeRun(mgr, COPY.placeholder, FONT.regular, 15),
    labels: EFFECTS.map((e) => makeRun(mgr, e, FONT.medium, 17)),
    fmt: [
      makeRun(mgr, 'Aa', FONT.bold, 15),
      makeRun(mgr, 'Aa', FONT.italic, 15),
      makeRun(mgr, 'Aa', FONT.regular, 15),
      makeRun(mgr, 'Aa', FONT.regular, 15),
    ],
    heroR,
    heroM,
    geo,
    icons: {
      plus: Skia.Path.MakeFromSVGString(PLUS)!,
      arrow: Skia.Path.MakeFromSVGString(SEND_ARROW)!,
      chevron: Skia.Path.MakeFromSVGString(CHEVRON)!,
      mono: Skia.Path.MakeFromSVGString(MONO_O)!,
    },
  };
};
