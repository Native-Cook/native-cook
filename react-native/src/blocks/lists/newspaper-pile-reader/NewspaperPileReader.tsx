import {
  BlurStyle,
  Canvas,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  StrokeJoin,
  TileMode,
  useFonts,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkParagraph,
  type SkPath,
  type SkTypefaceFontProvider,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { hex, makePaperImages, PAD, para, SERIF, SYSTEM } from './newspaperPileArt';
import {
  type Driver,
  driverChange,
  driverEnd,
  driverFrame,
  driverToggle,
  emptyFrame,
  type Frame,
  type Geo,
  lerp,
  makeDriver,
  makeGeo,
  PAPER_H,
  PAPER_W,
  PAPERS,
  type Sheet,
} from './newspaperPileModel';

/*
 Newspaper Pile Reader — the morning digest of an invented news app, as a pile of folded papers
 (a port of NewspaperPileReader.swift).

 Look: a warm white page, a date pill, and six invented papers (mastheads, headlines, columns, photos,
 grain, fold and a curled corner, all drawn in code once into bitmaps) piled down the screen, each a
 little rotated and offset, each lower one in front. Bottom: a dark tab pill and a pile / reader toggle.

 Hero (object, "lift"): a paper is picked out of the pile. It dips, lifts (its shadow grows), straightens
 and grows to reading size in the middle of the screen while the others are tossed out to the edges with
 a stagger; the paper's headline and summary fade in under it. Putting it back reverses it: the paper
 settles into its slot and the others slide in over it, re-forming the pile.

 autoPlay: LOOP = 9 s. Rest; a riffle wave runs down the pile (1.0 s); rest; the second paper is picked
 (2.6 s), read (3.5–6.0 s), put back (6.0–7.0 s); rest to 9 s. Every value is a pure function of the
 loop time and both ends of the loop are the pile at rest.
 Touch: tap a paper to open it; in the reader swipe sideways to page, swipe down or tap to put it back;
 drag the pile sideways to riffle it. 2 s after the last touch it goes back to the pile and the loop restarts.
 Reduced motion: no riffle and no flight; opening and closing crossfade; the loop is off.
*/

type Props = {
  /** Plays the 9 s pick-and-put-back loop until touched. */
  autoPlay?: boolean;
};

const BG = 0xf6f5f1;
const INK = 0x1c1b19;
const GREY = 0x9c9a92;
const IDLE = 0x8a8780;

type Caption = { kicker: SkParagraph; title: SkParagraph; summary: SkParagraph };

type Art = {
  papers: SkImage[];
  date: SkParagraph;
  update: SkParagraph;
  captions: Caption[];
};

// Drawing helpers (UI thread)

function fill(color: SkColor) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: SkColor, width: number) {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

/** SwiftUI's blur radius as a Skia sigma. */
function sigma(r: number) {
  'worklet';
  return r * 1.5 * 0.57735 + 0.5;
}

function blurred(color: SkColor, r: number) {
  'worklet';
  const p = fill(color);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(r), true));
  return p;
}

function rrect(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}

function mix(a: number, b: number, t: number): SkColor {
  'worklet';
  return Float32Array.of(
    lerp((a >> 16) & 0xff, (b >> 16) & 0xff, t) / 255,
    lerp((a >> 8) & 0xff, (b >> 8) & 0xff, t) / 255,
    lerp(a & 0xff, b & 0xff, t) / 255,
    1,
  );
}

function drawSheet(c: SkCanvas, s: Sheet, img: SkImage, alpha: number) {
  'worklet';
  const L = Math.min(Math.max(s.lift, 0), 1.2);
  const w = PAPER_W;
  const h = PAPER_H;
  // Ambient shadow: grows, drops and darkens a little as the paper lifts.
  c.save();
  c.translate(s.x, s.y + lerp(8, 26, L));
  c.rotate(s.rot, 0, 0);
  const ak = s.scale * lerp(1, 1.06, L);
  c.scale(ak, ak);
  c.drawPath(rrect(-w / 2, -h / 2, w, h, 8), blurred(Float32Array.of(0, 0, 0, alpha * lerp(0.12, 0.22, L)), 20));
  c.restore();
  // Contact shadow: tight, fades as the paper leaves the pile.
  c.save();
  c.translate(s.x, s.y + 1.5);
  c.rotate(s.rot, 0, 0);
  c.scale(s.scale, s.scale);
  c.drawPath(rrect(-w / 2, -h / 2, w, h, 3), blurred(Float32Array.of(0, 0, 0, alpha * lerp(0.22, 0.06, Math.min(L, 1))), 3));
  c.restore();
  // The paper.
  c.save();
  c.translate(s.x, s.y);
  c.rotate(s.rot, 0, 0);
  c.scale(s.scale, s.scale);
  const paint = Skia.Paint();
  paint.setAlphaf(alpha);
  c.drawImageRectOptions(
    img,
    Skia.XYWHRect(0, 0, img.width(), img.height()),
    Skia.XYWHRect(-w / 2 - PAD, -h / 2 - PAD, w + 2 * PAD, h + 2 * PAD),
    FilterMode.Linear,
    MipmapMode.None,
    paint,
  );
  c.restore();
}

function drawHeader(c: SkCanvas, g: Geo, art: Art) {
  'worklet';
  c.drawPath(rrect(16, g.top + 8, g.w - 32, 44, 22), fill(hex(0xeceae4, 1)));
  art.date.paint(c, 34, g.top + 30 - art.date.getHeight() / 2);
  const x = g.w - 42 - 9;
  const y = g.top + 8 + 22 - 8.5;
  const glyph = hex(0x6e6c66, 1);
  c.drawPath(rrect(x, y + 2, 18, 15, 3), stroke(glyph, 1.5));
  c.drawRect(Skia.XYWHRect(x, y + 6, 18, 1.5), fill(glyph));
  c.drawPath(rrect(x + 5 - 0.75, y, 1.5, 4.5, 0.75), fill(glyph));
  c.drawPath(rrect(x + 13 - 0.75, y, 1.5, 4.5, 0.75), fill(glyph));
  art.update.paint(c, g.cx - art.update.getLongestLine() / 2, g.top + 72 - art.update.getHeight() / 2);
}

function layerAlpha(c: SkCanvas, a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAlphaf(a);
  c.saveLayer(p);
}

function drawCaption(c: SkCanvas, g: Geo, f: Frame, art: Art) {
  'worklet';
  const cap = art.captions[f.caption];
  const y0 = g.captionTop - 8;
  const titleY = y0 + cap.kicker.getHeight() + 8;
  const summaryY = titleY + cap.title.getHeight() + 10;
  const ys = [y0, titleY, summaryY];
  const ps = [cap.kicker, cap.title, cap.summary];
  for (let k = 0; k < 3; k++) {
    const a = f.rows[k];
    if (a <= 0.001) continue;
    layerAlpha(c, a);
    ps[k].paint(c, 24, ys[k] + f.rise[k]);
    c.restore();
  }
}

/** The fog over the pile's foot, the page dashes and the bottom bar. */
function drawChrome(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const fogY = g.h - 210;
  const fog = Skia.Paint();
  fog.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, fogY),
      Skia.Point(0, g.h),
      [hex(BG, 0), hex(BG, 0.92), hex(BG, 1)],
      [0, 90 / 210, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, fogY, g.w, 210), fog);

  // Page dashes.
  if (f.rows[3] > 0.001) {
    layerAlpha(c, f.rows[3]);
    const n = PAPERS.length;
    const total = (n - 1) * 20 + 28;
    let x = g.cx - total / 2;
    for (let i = 0; i < n; i++) {
      const on = Math.max(0, 1 - Math.abs(i - f.page));
      const wd = 14 + 14 * on;
      c.drawPath(rrect(x, g.dashY + f.rise[3] - 1.5, wd, 3, 1.5), fill(mix(0xcfccc4, INK, on)));
      x += wd + 6;
    }
    c.restore();
  }

  // Tab pill.
  const y = g.barY;
  c.drawPath(rrect(20, y - 28, 150, 56, 28), fill(hex(0x161513, 1)));
  const white = hex(0xf4f3ee, 1);
  const stack = Skia.PathBuilder.Make()
    .addRRect(Skia.RRectXY(Skia.XYWHRect(58 - 9, y - 7, 18, 13), 2.5, 2.5))
    .moveTo(58 - 6, y - 10.5)
    .lineTo(58 + 6, y - 10.5)
    .detach();
  c.drawPath(stack, stroke(white, 1.6));
  c.drawCircle(58, y + 13.5, 1.5, fill(white));
  const mark = Skia.PathBuilder.Make()
    .moveTo(95 - 6, y - 9)
    .lineTo(95 + 6, y - 9)
    .lineTo(95 + 6, y + 9)
    .lineTo(95, y + 4)
    .lineTo(95 - 6, y + 9)
    .close()
    .detach();
  c.drawPath(mark, stroke(hex(IDLE, 1), 1.6));
  const sliders = Skia.PathBuilder.Make()
    .moveTo(132 - 8, y - 4)
    .lineTo(132 + 8, y - 4)
    .addCircle(132 - 4.5, y - 4, 2.5)
    .moveTo(132 - 8, y + 4)
    .lineTo(132 + 8, y + 4)
    .addCircle(132 + 4.5, y + 4, 2.5)
    .detach();
  c.drawPath(sliders, stroke(hex(IDLE, 1), 1.6));

  // Mode toggle: reader (three dots on a line) | pile (three lines).
  const mx = g.w - 20 - 108;
  c.drawPath(rrect(mx, y - 28 + 6, 108, 56, 28), blurred(Float32Array.of(0, 0, 0, 0.1), 9));
  c.drawPath(rrect(mx, y - 28, 108, 56, 28), fill(hex(0xffffff, 1)));
  const hx = lerp(mx + 78, mx + 30, f.toggle);
  c.drawCircle(hx, y, 22, fill(hex(0xeceae4, 1)));
  const readerCol = mix(IDLE, INK, f.toggle);
  const pileCol = mix(INK, IDLE, f.toggle);
  c.drawRect(Skia.XYWHRect(mx + 30 - 10, y - 0.6, 20, 1.2), fill(readerCol));
  for (let k = -1; k <= 1; k++) c.drawCircle(mx + 30 + k * 8, y, 3, fill(readerCol));
  for (let k = -1; k <= 1; k++) c.drawPath(rrect(mx + 78 - 9, y + k * 6 - 1, 18, 2, 1), fill(pileCol));
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, art: Art) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(BG, 1)));
  drawHeader(c, g, art);
  const base = f.papersAlpha;
  if (f.sheets.length === PAPERS.length && base > 0.001) {
    for (let i = 0; i < f.sheets.length; i++) drawSheet(c, f.sheets[i], art.papers[i], base);
    if (f.raised >= 0 && f.raisedAlpha > 0.001) {
      drawSheet(c, f.sheets[f.raised], art.papers[f.raised], f.raisedAlpha * base);
    }
  }
  drawChrome(c, g, f);
  drawCaption(c, g, f, art);
}

function makeArt(fonts: SkTypefaceFontProvider, w: number): Art {
  const scale = PixelRatio.get();
  const captionW = w - 48;
  const captions = PAPERS.map((p) => {
    const kicker = Skia.ParagraphBuilder.Make({ maxLines: 1, ellipsis: '…' }, fonts)
      .pushStyle({ color: hex(0x8f8c84, 1), fontFamilies: [SYSTEM], fontSize: 11, letterSpacing: 1.1, fontStyle: { weight: 600 } })
      .addText(p.kicker)
      .build();
    kicker.layout(captionW);
    const title = Skia.ParagraphBuilder.Make({ maxLines: 2, ellipsis: '…' }, fonts)
      .pushStyle({ color: hex(INK, 1), fontFamilies: [SERIF], fontSize: 26 })
      .addText(p.title)
      .build();
    title.layout(captionW);
    const summary = Skia.ParagraphBuilder.Make({ maxLines: 3, ellipsis: '…' }, fonts)
      .pushStyle({ color: hex(0x6b6960, 1), fontFamilies: [SYSTEM], fontSize: 15, heightMultiplier: 21 / 15 })
      .addText(p.summary)
      .build();
    summary.layout(captionW);
    return { kicker, title, summary };
  });
  return {
    papers: makePaperImages(fonts, scale),
    date: para(fonts, 'Tuesday, June 4', { family: SYSTEM, size: 22, color: hex(INK, 1), tracking: -0.3 }, 10000),
    update: para(fonts, 'Last update · Today 7:30 AM · 6 papers', { family: SYSTEM, size: 12, color: hex(GREY, 1) }, 10000),
    captions,
  };
}

export function NewspaperPileReader({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(
    () => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const fonts = useFonts({
    Unifraktur: [require('./assets/UnifrakturMaguntia-Book.ttf')],
    DMSerif: [require('./assets/DMSerifDisplay-Regular.ttf')],
  });
  const art = useMemo(() => (fonts && size ? makeArt(fonts, size.width) : null), [fonts, size]);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame());
  const now = useSharedValue(0);
  const startY = useSharedValue(0);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(driverFrame(driver.get(), t, auto, reduced, geo));
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      startY.set(e.y);
    })
    .onUpdate((e) => {
      driverChange(driver.get(), startY.get(), e.translationX, e.translationY, e.velocityX, now.get(), auto, reduced);
    })
    .onFinalize((e) => {
      const d = driver.get();
      if (driverEnd(d, e.x, e.y, e.translationX, e.translationY, e.velocityX, e.velocityY, now.get(), auto, geo)) {
        scheduleOnRN(feel);
      }
    });

  const toggle = () => {
    scheduleOnUI(() => {
      driverToggle(driver.get(), now.get(), auto);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (art) drawScreen(canvas, geo, frame.get(), art);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityLabel="Today's papers. Tap a paper to read it, swipe it down to put it back."
        accessibilityActions={[{ name: 'toggle', label: 'Open the next paper' }]}
        onAccessibilityAction={() => toggle()}
      >
        <StatusBar style="dark" />
        {size ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F6F5F1' },
});
