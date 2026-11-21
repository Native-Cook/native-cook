// Hour Scrub Sky: everything drawn over the sky, as worklets that record into one picture per frame. Text is laid out
// once as Skia paragraphs (white) and coloured at draw time: small elements through a src-in colour filter, the
// sentence and the numeral through a screen-space gradient that is light above the terminator and dark below it.

import { BlendMode, BlurStyle, ClipOp, Skia, TileMode, type SkCanvas, type SkParagraph, type SkTypefaceFontProvider } from '@shopify/react-native-skia';

import { ARROW, CLOUD, LEAF, MAP, MORE, NAV, PAUSE, PLAY, RESET, SLASH, SUN_DISC, SUN_RAYS, TROPHY, TROPHY_HANDLES } from './hs-icons';
import { type Frame, clamp01, conditionRoll, easeInOut, easeOut, mix, pillIndex, sunAt, tempAt, tickX } from './hs-motion';
import { COPY, FAMILY, INK, L } from './hs-theme';

// ---------------------------------------------------------------------------------------------------------------
// text, laid out once

type Run = { text: string; size: number; weight: number; track?: number; tnum?: boolean };
const para = (fonts: SkTypefaceFontProvider, r: Run): SkParagraph => {
  const b = Skia.ParagraphBuilder.Make({}, fonts);
  b.pushStyle({
    fontFamilies: [FAMILY],
    fontSize: r.size,
    fontStyle: { weight: r.weight },
    color: Skia.Color('#FFFFFF'),
    letterSpacing: (r.track ?? 0) * r.size,
    fontFeatures: r.tnum ? [{ name: 'tnum', value: 1 }] : [],
  });
  b.addText(r.text);
  b.pop();
  const p = b.build();
  p.layout(2000);
  return p;
};

/** Paragraph top for a cap top at y (Figtree: ascent 0.95 em, cap height 0.70 em). */
export const capTop = (y: number, size: number) => {
  'worklet';
  return y - 0.25 * size;
};

const SENT = 34;
const NUM = 106;
/** Pitch of the numeral's digit columns (tabular 0.62 em, tracking -0.04 em) and their roll window. */
export const DIGIT = { pitch: 0.62 * NUM - 0.04 * NUM, x0: L.textX - 0.05 * NUM, win: 0.7 * NUM + 20 };

export const makeTexts = (fonts: SkTypefaceFontProvider) => {
  const word = (t: string) => para(fonts, { text: t, size: SENT, weight: 500, track: -0.02 });
  const words = COPY.lines.flat().map(word);
  const space = para(fonts, { text: 'a a', size: SENT, weight: 500, track: -0.02 }).getLongestLine() - 2 * word('a').getLongestLine();
  // x of each word on its line
  const wordX: number[] = [];
  let j = 0;
  for (const line of COPY.lines) {
    let x = L.textX;
    for (let i = 0; i < line.length; i++) {
      wordX.push(x);
      x += words[j].getLongestLine() + space;
      j++;
    }
  }
  const pills: SkParagraph[] = [];
  for (let i = 0; i < 24; i++) {
    const clock = (12 + i) % 24;
    const hr = clock % 12 === 0 ? 12 : clock % 12;
    pills.push(para(fonts, { text: `${12 + i >= 24 ? 'Tom ' : ''}${hr} ${clock < 12 ? 'AM' : 'PM'}`, size: 13, weight: 600, tnum: true }));
  }
  return {
    words,
    wordX,
    lineOf: COPY.lines.flatMap((line, i) => line.map(() => i)),
    cond: COPY.conditions.map((c) => word(c)),
    digits: Array.from({ length: 10 }, (_, d) => para(fonts, { text: String(d), size: NUM, weight: 300, track: -0.04, tnum: true })),
    loc: COPY.conditions.map((c) => para(fonts, { text: `${COPY.city}  ·  ${c}`, size: 15, weight: 500 })),
    detail: para(fonts, { text: COPY.detail, size: 13, weight: 400, tnum: true }),
    pills,
    tabs: COPY.tabs.map((t) => para(fonts, { text: t, size: 13, weight: 500 })),
  };
};
export type Texts = ReturnType<typeof makeTexts>;

// ---------------------------------------------------------------------------------------------------------------
// colour helpers (sRGB 0..1 triples)

export type RGB = number[];
export const hex = (h: string): RGB => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
const DARK = hex(INK.dark);
const LIGHT = hex(INK.light);
const CREAM = hex(INK.cream);
const RED = hex(INK.red);
const WHITE = [1, 1, 1];
const PILL = hex(INK.pillDark);
const GLASS_N = hex(INK.glassNight);
const BLUE = hex(INK.blue);
const BLUE_N = hex(INK.blueNight);
const SEL_D = hex('#E4E8EE');
const SEL_N = hex('#2C3140');

const mixc = (a: RGB, b: RGB, k: number): RGB => {
  'worklet';
  return [mix(a[0], b[0], k), mix(a[1], b[1], k), mix(a[2], b[2], k)];
};
const col = (c: RGB, a = 1) => {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
};
const fill = (c: RGB, a = 1) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(c, a));
  return p;
};
const stroke = (c: RGB, w: number, a = 1) => {
  'worklet';
  const p = fill(c, a);
  p.setStyle(1);
  p.setStrokeWidth(w);
  p.setStrokeCap(1);
  p.setStrokeJoin(1);
  return p;
};
/** Draw `body` in a layer tinted with one colour (src-in). */
const tinted = (c: SkCanvas, rgb: RGB, a: number, body: () => void) => {
  'worklet';
  const lp = Skia.Paint();
  lp.setColorFilter(Skia.ColorFilter.MakeBlend(col(rgb, a), BlendMode.SrcIn));
  c.saveLayer(lp);
  body();
  c.restore();
};
/** Draw `body` in a layer coloured by a vertical gradient: `top` above y, `bottom` below, 24 pt soft edge. */
const terminated = (c: SkCanvas, top: RGB, bottom: RGB, y: number, ch: number, body: () => void) => {
  'worklet';
  c.saveLayer();
  body();
  const p = Skia.Paint();
  p.setBlendMode(BlendMode.SrcIn);
  const a = clamp01((y - 12) / ch);
  const b = clamp01((y + 12) / ch);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: ch }, [col(top), col(top), col(bottom), col(bottom)], [0, a, Math.max(b, a + 1e-4), 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, 402, ch), p);
  c.restore();
};

// ---------------------------------------------------------------------------------------------------------------
// the scene

export type Scene = {
  f: Frame;
  ch: number;
  yInk: number;
  ink: number[]; // per INK_I: 0 dark ink, 1 light ink
  sky84: RGB; // the sky behind the chip
  sunCol: RGB;
  unit: number; // 0 °C, 1 °F
  press: number[]; // chip, arrow, tab: press scale
  reduced: boolean;
};

/** The sentence: lines fold upward under a mask; "Mostly Cloudy" rolls up under line 1's; the words come back one by one. */
export const sentenceAt = (cs: number, rs: number) => {
  'worklet';
  const words = [0, 0, 0, 0, 0, 0, 0, 0];
  const lineDy = [0, 0, 0];
  const clip = [1, 1, 1];
  let cond = 1;
  if (rs > 0) {
    cond = -easeInOut(clamp01(rs / 0.22));
    for (let j = 0; j < 8; j++) words[j] = (1 - easeOut(clamp01((rs - 0.06 * j) / 0.24))) * 40;
  } else if (cs > 0) {
    const k0 = easeInOut(clamp01(cs / 0.22));
    for (let j = 0; j < 3; j++) words[j] = -46 * k0;
    cond = 1 - k0;
    for (let i = 1; i < 3; i++) {
      const k = easeInOut(clamp01((cs - 0.04 * i) / 0.22));
      lineDy[i] = -14 * k;
      clip[i] = 1 - k;
    }
  }
  return { words, lineDy, clip, cond };
};

/**
 * The numeral's roll position (in degrees) at hour h: the reading of the nearest half-hour detent, rolling to the next
 * one between detents (over the middle 40 % of the half hour), so a released scrub never rests half-rolled.
 */
const rollPos = (h: number, fahrenheit: boolean) => {
  'worklet';
  const read = (j: number) => {
    const c = tempAt(j / 2);
    return Math.round(fahrenheit ? c * 1.8 + 32 : c);
  };
  const q = h * 2;
  const j = Math.floor(q);
  const k = clamp01((q - j - 0.3) / 0.4);
  const a = read(j);
  return a + (read(j + 1) - a) * k * k * (3 - 2 * k);
};

/** Index of the condition the location row names (rolls with the big label: switches at the middle of its roll). */
const conditionIndexForRow = (h: number) => {
  'worklet';
  const r = conditionRoll(h);
  let best = 0;
  for (let i = 1; i < 3; i++) if (Math.abs(r[i]) < Math.abs(r[best])) best = i;
  return best;
};

/** A glass rim: white by day, the sun's colour on its sun-side arc from 17:30 to sunset, faint white at night. */
const drawRim = (c: SkCanvas, rr: ReturnType<typeof Skia.RRectXY>, cx: number, cy: number, h: number, sun: RGB, k: number) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(1);
  p.setStrokeWidth(1);
  const glow = clamp01((h - 5.5) / 0.3) * (1 - clamp01((h - 6.4) / 0.3));
  const base = col(WHITE, mix(0.6, 0.25, k));
  if (glow > 0.01) {
    const sp = sunAt(h);
    const dx = sp.x - cx;
    const dy = sp.y - cy;
    const l = Math.max(Math.hypot(dx, dy), 1);
    const r = 60;
    p.setStrokeWidth(1.5);
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cx + (dx / l) * r, y: cy + (dy / l) * r },
        { x: cx - (dx / l) * r, y: cy - (dy / l) * r },
        [col(sun, 0.8 * glow + 0.6 * (1 - glow)), base],
        null,
        TileMode.Clamp,
      ),
    );
  } else p.setColor(base);
  c.drawRRect(rr, p);
};

export const drawScene = (c: SkCanvas, s: Scene, tx: Texts | null, under: boolean) => {
  'worklet';
  const { f, ink } = s;
  const night = (i: number) => ink[i];
  // ---- under the glass: soft contact shadows (drawn before the tab bar's backdrop blur) ----
  if (under) {
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Float32Array.of(0.04, 0.06, 0.12, mix(0.14, 0.32, night(7))));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(L.tab.x, L.tab.y, L.tab.w, L.tab.h), 33, 33), ClipOp.Difference, true);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.tab.x + 2, L.tab.y + 8, L.tab.w - 4, L.tab.h), 33, 33), sh);
    c.restore();
    return;
  }
  if (!tx) return;
  const ch = s.ch;
  const lightInk = (i: number) => mixc(DARK, LIGHT, night(i));

  // ---- mode chip: sky-tinted glass, two 18 pt glyphs ----
  {
    const k = night(1);
    const sc = s.press[0];
    c.save();
    c.translate(L.chip.x + L.chip.w / 2, L.chip.y + L.chip.h / 2);
    c.scale(sc, sc);
    c.translate(-(L.chip.x + L.chip.w / 2), -(L.chip.y + L.chip.h / 2));
    const glass = mixc(s.sky84, WHITE, mix(0.45, 0.22, k));
    const rr = Skia.RRectXY(Skia.XYWHRect(L.chip.x, L.chip.y, L.chip.w, L.chip.h), 21, 21);
    c.drawRRect(rr, fill(glass, 0.85));
    c.drawRRect(rr, stroke(WHITE, 1, mix(0.45, 0.14, k)));
    const ic = lightInk(1);
    const g = 18 / 24;
    c.save();
    c.translate(L.chip.x + 13, L.chip.y + 12);
    c.scale(g, g);
    c.drawPath(LEAF, stroke(ic, 2.7));
    c.drawPath(SLASH, stroke(glass, 5.5));
    c.drawPath(SLASH, stroke(ic, 2.7));
    c.restore();
    c.save();
    c.translate(L.chip.x + 44, L.chip.y + 11);
    c.scale(g, g);
    c.drawPath(SUN_DISC, fill(ic));
    c.drawPath(SUN_RAYS, stroke(ic, 2.4));
    c.drawPath(CLOUD, stroke(glass, 3.4));
    c.drawPath(CLOUD, fill(ic));
    c.restore();
    c.restore();
  }

  // ---- the sentence / condition (gradient ink) ----
  const st = sentenceAt(f.cs, f.rs);
  terminated(c, LIGHT, DARK, s.yInk, ch, () => {
    for (let li = 0; li < 3; li++) {
      const top = L.sentenceTop + L.linePitch * li - 12;
      c.save();
      c.clipRect(Skia.XYWHRect(0, top + st.lineDy[li], 402, L.linePitch * st.clip[li]), ClipOp.Intersect, true);
      for (let j = 0; j < 8; j++) {
        if (tx.lineOf[j] !== li) continue;
        tx.words[j].paint(c, tx.wordX[j], capTop(L.sentenceTop + L.linePitch * li, SENT) + st.lineDy[li] + st.words[j]);
      }
      if (li === 0 && st.cond < 1) {
        const roll = conditionRoll(f.h);
        for (let k = 0; k < 3; k++) {
          if (roll[k] > 2) continue;
          const dy = (st.cond + roll[k]) * L.linePitch;
          if (Math.abs(dy) < L.linePitch) tx.cond[k].paint(c, L.textX, capTop(L.sentenceTop, SENT) + dy);
        }
      }
      c.restore();
    }
  });

  // ---- the numeral (gradient ink, cream above the terminator) ----
  const pos = mix(rollPos(f.h, false), rollPos(f.h, true), s.unit);
  const tens = Math.floor(pos / 10) + clamp01((pos % 10) - 9);
  const ones = pos % 10;
  terminated(c, CREAM, DARK, s.yInk, ch, () => {
    const winTop = L.tempTop - 10;
    const cols = [tens, ones];
    for (let ci = 0; ci < 2; ci++) {
      const x = DIGIT.x0 + DIGIT.pitch * ci;
      c.save();
      c.clipRect(Skia.XYWHRect(x - 6, winTop, DIGIT.pitch + 10, DIGIT.win), ClipOp.Intersect, true);
      const p = cols[ci];
      const base = Math.floor(p);
      for (let d = base - 1; d <= base + 1; d++) {
        const dy = (d - p) * DIGIT.win;
        if (Math.abs(dy) >= DIGIT.win) continue;
        tx.digits[((d % 10) + 10) % 10].paint(c, x, capTop(L.tempTop, NUM) + dy);
      }
      c.restore();
    }
    // the degree sign: a 22 pt ring, 4 pt stroke, on the cap top
    const rx = DIGIT.x0 + DIGIT.pitch * 2 + 0.02 * NUM + 11;
    c.drawCircle(rx, L.tempTop + 11, 9, stroke(WHITE, 4));
  });

  // ---- arrow pill ----
  {
    const k = night(2);
    const sc = s.press[1];
    const cx = L.arrow.x + L.arrow.w / 2;
    const cy = L.arrow.y + L.arrow.h / 2;
    c.save();
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    const rr = Skia.RRectXY(Skia.XYWHRect(L.arrow.x, L.arrow.y, L.arrow.w, L.arrow.h), 22, 22);
    const sh = fill([0.04, 0.06, 0.12], mix(0.1, 0.3, k));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
    c.save();
    c.translate(0, 2);
    c.drawRRect(rr, sh);
    c.restore();
    c.drawRRect(rr, fill(mixc(WHITE, GLASS_N, k), mix(0.9, 0.82, k)));
    drawRim(c, rr, cx, cy, f.h, s.sunCol, k);
    c.save();
    c.translate(cx, cy);
    c.drawPath(ARROW, stroke(lightInk(2), 2));
    c.restore();
    c.restore();
  }

  // ---- location and detail rows ----
  {
    const ic = lightInk(3);
    c.save();
    c.translate(L.textX, L.locY + 1);
    c.scale(0.55, 0.55);
    c.drawPath(NAV, fill(ic));
    c.restore();
    const ci = Math.min(Math.max(conditionIndexForRow(f.h), 0), 2);
    tinted(c, ic, 1, () => tx.loc[ci].paint(c, L.textX + 19, capTop(L.locY + 2, 15)));
    tinted(c, lightInk(4), 0.8, () => tx.detail.paint(c, L.textX, capTop(L.detailY + 1.5, 13)));
  }

  // ---- tick row and the playhead's red cluster ----
  const px = tickX(f.hp);
  {
    const k = night(6);
    const base = mixc(DARK, WHITE, k);
    const baseA = mix(0.32, 0.4, k);
    const pitch = (L.ticks.x1 - L.ticks.x0) / (L.ticks.n - 1);
    const show = clamp01(f.pill * 4);
    for (let i = 0; i < L.ticks.n; i++) {
      const x = L.ticks.x0 + pitch * i;
      const d = Math.abs(x - px) / (4 * pitch);
      const w = d < 1 ? (0.5 + 0.5 * Math.cos(Math.PI * d)) * show : 0;
      const hgt = mix(L.ticks.h, L.ticks.hPeak, w);
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 1, L.ticks.bottom - hgt, 2, hgt), 1, 1), fill(mixc(base, RED, w), mix(baseA, 1, w)));
    }
  }

  // ---- hour pill: grows out of the playhead tick ----
  if (f.pill > 0.001) {
    const k = f.pill;
    const idx = pillIndex(f.hp);
    const lab = tx.pills[idx];
    const wPill = Math.max(60, lab.getLongestLine() + 26);
    const x = mix(px - 1, Math.min(Math.max(px - wPill / 2, 10), 392 - wPill), k);
    // the lane's top never overshoots towards the detail row (the pop overshoot stays in the width)
    const y = mix(L.ticks.bottom - L.ticks.hPeak, L.pillCy - 15, Math.min(k, 1));
    const w = mix(2, wPill, k);
    const hh = mix(L.ticks.hPeak, 30, Math.min(k, 1));
    const r = Math.min(mix(1, 15, clamp01(k)), hh / 2, w / 2);
    const kn = night(5);
    const pillCol = mixc(PILL, WHITE, kn);
    const rr = Skia.RRectXY(Skia.XYWHRect(x, y, Math.max(w, 0.5), Math.max(hh, 0.5)), r, r);
    c.drawRRect(rr, fill(mixc(RED, pillCol, f.pillDark)));
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    tinted(c, mixc(WHITE, DARK, kn), 1, () => lab.paint(c, x + w / 2 - lab.getLongestLine() / 2, capTop(L.pillCy - 4.6, 13)));
    c.restore();
  }

  // ---- play / pause / reset disc ----
  {
    const k = night(6);
    const d = L.disc;
    c.drawCircle(d.cx, d.cy, d.r * f.disc, fill(mixc(PILL, WHITE, k)));
    const gc = mixc(WHITE, PILL, k);
    const glyph = (path: typeof PLAY, sc: number, rot: number, strokeIt: boolean) => {
      if (sc < 0.01) return;
      c.save();
      c.translate(d.cx, d.cy);
      c.rotate(rot, 0, 0);
      c.scale(sc * f.disc, sc * f.disc);
      c.drawPath(path, strokeIt ? stroke(gc, 2) : fill(gc));
      c.restore();
    };
    glyph(PLAY, f.gPlay, 0, false);
    glyph(PAUSE, f.gPause, 0, false);
    glyph(RESET, f.gReset, f.resetRot, true);
  }

  // ---- tab bar (its frosted fill is a backdrop blur drawn before this picture) ----
  {
    const k = night(7);
    const sc = s.press[2];
    const cx = L.tab.x + L.tab.w / 2;
    const cy = L.tab.y + L.tab.h / 2;
    c.save();
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    const rr = Skia.RRectXY(Skia.XYWHRect(L.tab.x, L.tab.y, L.tab.w, L.tab.h), 33, 33);
    drawRim(c, rr, cx, cy, f.h, s.sunCol, k);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.sel.x, L.sel.y, L.sel.w, L.sel.h), 27, 27), fill(mixc(SEL_D, SEL_N, k), mix(1, 0.9, k)));
    const icons = [NAV, MAP, TROPHY, MORE];
    for (let i = 0; i < 4; i++) {
      const tcx = L.tabCx[i];
      const ic = i === 0 ? mixc(BLUE, BLUE_N, k) : lightInk(7);
      c.save();
      c.translate(tcx - 12, 803);
      c.drawPath(icons[i], fill(ic));
      if (i === 2) c.drawPath(TROPHY_HANDLES, stroke(ic, 2));
      c.restore();
      const lab = tx.tabs[i];
      tinted(c, ic, 1, () => lab.paint(c, tcx - lab.getLongestLine() / 2, capTop(836.5, 13)));
    }
    c.restore();
  }
};
