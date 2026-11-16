import {
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  TileMode,
  matchFont,
  rect,
  rrect,
  type SkCanvas,
  type SkFont,
  type SkPaint,
  type SkPicture,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Platform, StatusBar, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { runOnJS, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type FrameCallback, type SharedValue } from 'react-native-reanimated';

export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const INK = '#263C38';
const MUTED = '#7C8780';
const CORAL = '#EF735E';
const BACKGROUND = '#F3F1EB';
const STARTS = [2.01474, 2.20682, 2.42403];
const RADIANS = -12 * Math.PI / 180;

function segment(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

function ease(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function inverseEase(x: number) {
  'worklet';
  return x < 0.5 ? Math.pow(x / 4, 1 / 3) : 1 - Math.pow((1 - x) / 4, 1 / 3);
}

function paint(color: string, opacity = 1, strokeWidth = 0) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(opacity);
  if (strokeWidth > 0) {
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(strokeWidth);
    p.setStrokeCap(StrokeCap.Round);
  }
  return p;
}

function layerPaint(opacity: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAlphaf(opacity);
  return p;
}

function shadowPaint(dx: number, dy: number, radius: number, opacity: number, only = false) {
  'worklet';
  const p = Skia.Paint();
  const color = Skia.Color(INK);
  color[3] = opacity;
  p.setImageFilter(only
    ? Skia.ImageFilter.MakeDropShadowOnly(dx, dy, radius, radius, color)
    : Skia.ImageFilter.MakeDropShadow(dx, dy, radius, radius, color));
  return p;
}

function drawPicture(canvas: SkCanvas, picture: SkPicture, opacity = 1, filter?: SkPaint) {
  'worklet';
  const p = filter ?? layerPaint(opacity);
  if (filter) p.setAlphaf(opacity);
  canvas.saveLayer(p);
  canvas.drawPicture(picture);
  canvas.restore();
}

function makeFont(size: number, weight: '400' | '500' | '600' | '700' = '400', mono = false) {
  const font = matchFont({
    fontFamily: mono ? (Platform.OS === 'ios' ? 'Menlo' : 'monospace') : (Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif'),
    fontSize: size,
    fontWeight: weight,
  });
  return font;
}

function drawText(canvas: SkCanvas, value: string, x: number, y: number, font: SkFont, color = INK, anchor = 0, tracking = 0, opacity = 1) {
  const glyphs = font.getGlyphIDs(value);
  const widths = font.getGlyphWidths(glyphs);
  const width = widths.reduce((sum, item) => sum + item, 0) + tracking * Math.max(0, glyphs.length - 1);
  const metrics = font.getMetrics();
  let cursor = 0;
  const positions = widths.map((advance) => {
    const point = { x: cursor, y: 0 };
    cursor += advance + tracking;
    return point;
  });
  canvas.drawGlyphs(glyphs, positions, x - width * anchor, y - (metrics.ascent + metrics.descent) / 2, font, paint(color, opacity));
}

function paperPath() {
  const path = Skia.Path.Make();
  path.moveTo(-108, -147);
  for (let i = 0; i < 27; i++) {
    path.lineTo(-104 + i * 8, -144);
    path.lineTo(-100 + i * 8, -147);
  }
  path.lineTo(108, 147);
  for (let i = 0; i < 27; i++) {
    path.lineTo(104 - i * 8, 144);
    path.lineTo(100 - i * 8, 147);
  }
  path.close();
  return path;
}

function recordPicture(bounds: { x: number; y: number; width: number; height: number }, draw: (canvas: SkCanvas) => void) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(bounds);
  draw(canvas);
  return recorder.finishRecordingAsPicture();
}

function makeArtwork() {
  // Static glyph runs, paper fibers and gradients are recorded once, outside the frame callback.
  const mono = {
    small: makeFont(7, '400', true),
    metadata: makeFont(8, '400', true),
    footer: makeFont(9, '500', true),
    label: makeFont(11, '400', true),
    total: makeFont(12, '700', true),
    row: makeFont(14, '400', true),
    amount: makeFont(14, '700', true),
    brand: makeFont(15, '700', true),
  };
  const stage = recordPicture(rect(0, 0, WIDTH, HEIGHT), (c) => {
    drawText(c, 'Tallyleaf', 36, 87, makeFont(16, '600'), INK, 0, -0.3);
    const leaf = Skia.Path.Make();
    leaf.moveTo(338, 91); leaf.quadTo(331, 75, 349, 75); leaf.quadTo(354, 88, 338, 91); leaf.close();
    c.drawPath(leaf, paint(CORAL));
    drawText(c, 'IMPORT / 01', 36, 127, makeFont(10, '500'), MUTED, 0, 1.8);
    drawText(c, 'Import receipt', 36, 161, makeFont(30, '600'), INK, 0, -1);
    drawText(c, 'One receipt. Three clear details.', 36, 190, makeFont(13), MUTED);
    drawText(c, 'Drag down to lift the ink', 196.5, 704, makeFont(12, '500'), MUTED, 0.5);
    drawText(c, 'DEMO RECEIPT · ON DEVICE', 196.5, 729, mono.footer, MUTED, 0.5, 0, 0.75);
    c.translate(36.5, 228);
    for (const [x, y, dx, dy] of [[10, 28, 17, 17], [310, 28, -17, 17], [10, 392, 17, -17], [310, 392, -17, -17]]) {
      const corner = Skia.Path.Make();
      corner.moveTo(x, y + dy); corner.lineTo(x, y); corner.lineTo(x + dx, y);
      c.drawPath(corner, paint(MUTED, 0.35, 1.5));
    }
  });
  const shape = paperPath();
  // GraphicsContext opacity applies to each draw operation. Keep these four
  // primitives separate so the stacked paper fills retain the same peak density.
  const paperBounds = rect(-160, -190, 320, 420);
  const rim = recordPicture(paperBounds, (c) => {
    c.translate(0, 2);
    c.drawPath(shape, paint('#D5CCB5'));
  });
  const ambient = recordPicture(paperBounds, (c) => {
    c.saveLayer(shadowPaint(0, 16, 18, 0.13));
    c.drawPath(shape, paint('#F5F0DE'));
    c.restore();
  });
  const paperFill = recordPicture(paperBounds, (c) => {
    const fill = paint('#F7F1DF');
    fill.setShader(Skia.Shader.MakeLinearGradient({ x: -108, y: -147 }, { x: 108, y: 147 }, [Skia.Color('#FFFDF3'), Skia.Color('#F7F1DF'), Skia.Color('#EAE1CB')], null, TileMode.Clamp));
    c.saveLayer(shadowPaint(0, 2, 2, 0.17));
    c.drawPath(shape, fill);
    c.restore();
  });
  const paperStroke = recordPicture(paperBounds, (c) => {
    c.saveLayer(shadowPaint(0, 2, 2, 0.17));
    c.drawPath(shape, paint('#D5CCB5', 1, 0.6));
    c.restore();
  });
  const paper = [rim, ambient, paperFill, paperStroke];
  const print = recordPicture(rect(-120, -160, 240, 320), (c) => {
    for (let i = 0; i < 64; i++) {
      const x = ((i * 67 + 19) % 210) - 105;
      const y = ((i * 43 + 11) % 286) - 143;
      c.drawLine(x, y, x + 3 + (i % 5), y + 0.4, paint(INK, 0.045, 0.5));
    }
    const fold = paint('#FFFFFF');
    fold.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -9 }, { x: 0, y: 3 }, [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.3)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
    c.drawRect(rect(-107, -9, 214, 12), fold);
    drawText(c, 'MORROW', 0, -111, mono.brand, INK, 0.5);
    drawText(c, 'M A R K E T', 0, -94, mono.metadata, MUTED, 0.5);
    drawText(c, '08 NOV 26 · 12:41', 0, -74, mono.metadata, MUTED, 0.5);
    const rule = paint(MUTED, 0.4, 0.6);
    rule.setPathEffect(Skia.PathEffect.MakeDash([2, 3], 0));
    for (const y of [-59, 89]) c.drawLine(-88, y, 88, y, rule);
    drawText(c, 'ITEM', -88, -51, mono.small, MUTED);
    drawText(c, 'AMOUNT', 88, -51, mono.small, MUTED, 1);
    drawText(c, 'TOTAL', -88, 110, mono.total);
    drawText(c, '24.60', 88, 110, mono.amount, INK, 1);
    drawText(c, 'A little good, every day.', 0, 129, mono.small, MUTED, 0.5);
  });
  const rowNames = ['Morrow bowl', 'Citrus tea', 'Oat square'];
  const amounts = ['12.80', '4.60', '7.20'];
  const rows = rowNames.map((name, i) => recordPicture(rect(-100, -18, 200, 36), (c) => {
    drawText(c, name, -88, 0, mono.row);
    drawText(c, amounts[i], 88, 0, mono.row, INK, 1);
  }));
  const cards = rowNames.map((_, i) => recordPicture(rect(0, 105 + i * 49, 320, 90), (c) => {
    const targetY = 146 + i * 49;
    const bounds = rrect(rect(28, targetY - 21.5, 264, 43), 9, 9);
    c.saveLayer(shadowPaint(0, 3, 5, 0.08));
    c.drawRRect(bounds, paint('#F9F8F1'));
    c.drawRRect(bounds, paint(MUTED, 0.14, 0.7));
    c.restore();
  }));
  const checks = rowNames.map((_, i) => recordPicture(rect(260, 138 + i * 49, 18, 18), (c) => {
    const y = 146 + i * 49;
    c.drawCircle(268, y, 5, paint(CORAL));
    const check = Skia.Path.Make(); check.moveTo(265.5, y); check.lineTo(267.3, y + 1.6); check.lineTo(270.5, y - 2);
    c.drawPath(check, paint('#FFFFFF', 1, 1));
  }));
  const footer = recordPicture(rect(0, 370, 320, 50), (c) => {
    drawText(c, '3 details extracted', 160, 385, makeFont(12, '600'), INK, 0.5);
    drawText(c, '24.60 total', 160, 405, mono.label, MUTED, 0.5);
  });
  return { stage, paper, print, rows, cards, checks, footer };
}

function lightHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

type ReceiptInkExtractGestureState = {
  reducedMotion: boolean;
  scale: number;
  oy: number;
  clock: SharedValue<number>;
  scrub: SharedValue<number>;
  dragging: SharedValue<boolean>;
  lastTouch: SharedValue<number>;
  detent: SharedValue<number>;
  startFrames: () => void;
};

// The factory creates event worklets; none of these callbacks runs during React render.
function makeScanGesture({ reducedMotion, scale, oy, clock, scrub, dragging, lastTouch, detent, startFrames }: ReceiptInkExtractGestureState) {
  return Gesture.Pan().minDistance(0)
    .onBegin((event) => {
      'worklet';
      if (reducedMotion) return;
      const now = performance.now() / 1000;
      clock.set(now);
      scrub.set(Math.min(1, Math.max(0, ((event.y - oy) / scale - 228 - 60) / 290)));
      dragging.set(true);
      lastTouch.set(now);
      runOnJS(startFrames)();
    })
    .onUpdate((event) => {
      'worklet';
      if (reducedMotion) return;
      const now = performance.now() / 1000;
      clock.set(now);
      const progress = Math.min(1, Math.max(0, ((event.y - oy) / scale - 228 - 60) / 290));
      scrub.set(progress);
      lastTouch.set(now);
      const next = STARTS.filter((value) => 1 + 2.2 * inverseEase(progress) >= value).length;
      if (next > 0 && next !== detent.get()) {
        detent.set(next);
        runOnJS(lightHaptic)();
      }
    })
    .onFinalize(() => {
      'worklet';
      if (reducedMotion) return;
      const now = performance.now() / 1000;
      clock.set(now);
      dragging.set(false);
      lastTouch.set(now);
      detent.set(-1);
    });
}

/** Locally drawn receipt ink, lifted by a deterministic scanner or a vertical drag. */
export function ReceiptInkExtract({ autoPlay = true }: { autoPlay?: boolean }) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const systemReducedMotion = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(systemReducedMotion);
  const artwork = useMemo(() => makeArtwork(), []);
  const clock = useSharedValue(0);
  const epoch = useSharedValue(-1);
  const lastTouch = useSharedValue(-1);
  const scrub = useSharedValue(0);
  const dragging = useSharedValue(false);
  const detent = useSharedValue(-1);
  const callbackRef = useRef<FrameCallback | null>(null);
  const stopFrames = useCallback(() => callbackRef.current?.setActive(false), []);
  const frame = useFrameCallback(() => {
    const now = performance.now() / 1000;
    clock.value = now;
    if (epoch.value < 0) epoch.value = now;
    if (!dragging.value && lastTouch.value >= 0 && now - lastTouch.value >= 2.6) {
      lastTouch.value = -1;
      epoch.value = now;
      if (!autoPlay) runOnJS(stopFrames)();
    }
  }, false);
  const startFrames = useCallback(() => frame.setActive(true), [frame]);
  useEffect(() => {
    callbackRef.current = frame;
    frame.setActive(!reducedMotion && autoPlay);
    return () => frame.setActive(false);
  }, [autoPlay, frame, reducedMotion]);
  useEffect(() => {
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((value) => { if (alive) setReducedMotion(value); });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => { alive = false; subscription.remove(); };
  }, []);
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT);
  const ox = (size.width - WIDTH * scale) / 2;
  const oy = (size.height - HEIGHT * scale) / 2;
  const picture = useDerivedValue(() => {
    const age = lastTouch.value >= 0 ? Math.max(0, clock.value - lastTouch.value) : 100;
    const manual = lastTouch.value >= 0 && (dragging.value || age < 2.6);
    const returning = manual && !dragging.value && age > 2;
    const touchMix = returning ? 1 - ease(segment(age, 2, 2.6)) : 1;
    const t = manual ? 1 + 2.2 * inverseEase(scrub.value) : (autoPlay && epoch.value >= 0 ? Math.max(0, clock.value - epoch.value) % LOOP : 0);
    const q = STARTS.map((start, i) => reducedMotion ? 1 : easeOut(segment(t, start, start + 0.4)) * (1 - ease(segment(t, 5.2 + i * 0.06, 6.68 + i * 0.06))) * touchMix);
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(rect(0, 0, size.width, size.height));
    c.translate(ox, oy); c.scale(scale, scale);
    c.drawPicture(artwork.stage);
    c.translate(36.5, 228);
    c.save(); c.translate(160, 211); c.rotate(-12, 0, 0);
    for (const primitive of artwork.paper) drawPicture(c, primitive, 1 - 0.68 * q[2]);
    drawPicture(c, artwork.print, 1 - 0.78 * q[2]);
    c.restore();
    for (let i = 0; i < 3; i++) {
      const p = q[i];
      const localY = -38 + i * 50;
      const sourceX = 160 - Math.sin(RADIANS) * localY;
      const sourceY = 211 + Math.cos(RADIANS) * localY;
      const targetY = 146 + i * 49;
      drawPicture(c, artwork.cards[i], p * p);
      c.save();
      c.translate(sourceX + (160 - sourceX) * p, sourceY + (targetY - sourceY) * p - 8 * Math.sin(Math.PI * p));
      c.rotate(-12 * (1 - p), 0, 0);
      drawPicture(c, artwork.rows[i], 1, shadowPaint(0, 4 * Math.sin(Math.PI * p), 2 + 3 * p, 0.13 * Math.sin(Math.PI * p)));
      c.restore();
      drawPicture(c, artwork.checks[i], segment(p, 0.9, 1));
    }
    drawPicture(c, artwork.footer, q[2]);
    const scanY = 60 + 290 * ease(segment(t, 1, 3.2));
    const beamAlpha = reducedMotion ? 0 : segment(t, 1, 1.15) * (1 - segment(t, 3.05, 3.2)) * touchMix;
    if (beamAlpha > 0) {
      c.saveLayer(layerPaint(beamAlpha));
      const band = paint(CORAL);
      band.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: scanY - 28 }, { x: 0, y: scanY }, [Skia.Color('rgba(239,115,94,0)'), Skia.Color('rgba(239,115,94,0.12)')], null, TileMode.Clamp));
      c.drawRect(rect(18, scanY - 28, 284, 28), band);
      const glow = paint(CORAL, 0.6);
      glow.setImageFilter(Skia.ImageFilter.MakeBlur(5, 5, TileMode.Decal));
      c.drawRect(rect(18, scanY - 2, 284, 4), glow);
      c.drawRect(rect(18, scanY - 0.75, 284, 1.5), paint(CORAL));
      c.drawCircle(18, scanY, 1.5, paint(CORAL)); c.drawCircle(302, scanY, 1.5, paint(CORAL));
      c.restore();
    }
    return recorder.finishRecordingAsPicture();
  }, [artwork, autoPlay, reducedMotion, size.width, size.height, ox, oy, scale]);
  const gesture = useMemo(() => makeScanGesture({
    clock, detent, dragging, lastTouch, oy, reducedMotion, scale, scrub, startFrames,
  }), [clock, detent, dragging, lastTouch, oy, reducedMotion, scale, scrub, startFrames]);
  return (
    <View
      style={styles.screen}
      onLayout={({ nativeEvent }) => setSize({ width: nativeEvent.layout.width, height: nativeEvent.layout.height })}
      accessible
      accessibilityLabel="Tallyleaf. Import receipt. Morrow bowl 12.80, Citrus tea 4.60, Oat square 7.20. Total 24.60."
      accessibilityHint="Drag down to lift the receipt ink into three rows."
    >
      <StatusBar barStyle="dark-content" />
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BACKGROUND } });
