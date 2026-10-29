import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
  type SkPoint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  bucket,
  type Driver,
  type Frame,
  LAYER_ALPHA,
  LAYER_SCALE,
  layerColor,
  look,
  type Look,
  makeDriver,
  makeFrame,
  mix,
  MOODS,
  radiusAt,
  rgb,
  type Rgb,
  touchDown,
  touchMove,
  touchUp,
  valley,
  WHITE,
  breathe,
  wobble,
} from './glassMoodModel';

/*
 Glass Mood Bloom — the mood check-in of Petalog, an invented journal app (a port of GlassMoodBloom.swift).

 Look: a soft colour wash, a two-line title, a layered glass flower in the middle, the mood word, a capsule slider
 from "Very unpleasant" to "Very pleasant" and a Next button. Everything is drawn in code with Skia.
 Hero: the flower is six stacked glass plates, each a closed shape whose radius is a function of angle. Petal count,
 valley depth, petal profile, the twist of the outer plate, the rotation and the hue are continuous functions of the
 mood value, so the morph is a real shape change: a calm blue circle (Neutral) becomes a green pentagon, a yellow
 star and an orange five-lobed flower, or a blue scalloped octagon and a violet eight-pointed star. Inner plates
 lead and outer plates follow 45 ms each, so a change blooms out of the core. Each plate has a radial fill, folded
 facets, an inner glow, a back edge for its thickness and a bright rim; a white core glows in the middle.
 Supporting: the background, the word (which slides when it changes), the slider and the Next button follow the hue.

 autoPlay: LOOP = 10 s (glassMoodModel.ts); rest at Neutral → Slightly Pleasant → Pleasant → Very Pleasant (hold)
 → Neutral → Slightly Unpleasant → Unpleasant → Very Unpleasant (hold) → Neutral (rest). Frame 10 s equals frame 0.
 Touch: drag the slider, or drag across the flower (160 pt = half the range). Release springs to the nearest mood.
 A light haptic when the word changes. 2 s after the last touch the flower eases back to Neutral and the loop restarts.
 Reduced motion: no loop, no breathing, no lag; Neutral rests and dragging sets the shape directly.
*/

type Props = { autoPlay?: boolean };

/** Glyphs laid out with tracking; widths from getGlyphWidths (measureText rounds per glyph). */
type Run = { ids: number[]; pos: SkPoint[]; w: number; mid: number; font: SkFont };

type Texts = {
  back: Run;
  app: Run;
  cancel: Run;
  title1: Run;
  title2: Run;
  moods: Run[];
  left: Run;
  right: Run;
  next: Run;
};

type Layout = {
  w: number;
  h: number;
  navY: number;
  titleY: number;
  nextY: number;
  sliderY: number;
  captionY: number;
  labelY: number;
  cx: number;
  cy: number;
  radius: number;
  trackX0: number;
  trackX1: number;
  knobX0: number;
  knobX1: number;
};

function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const nextY = h - bottom - 32;
  const sliderY = nextY - 101;
  const labelY = sliderY - 71;
  const titleBottom = top + 163;
  const labelTop = labelY - 18;
  return {
    w,
    h,
    navY: top + 29,
    titleY: top + 129,
    nextY,
    sliderY,
    captionY: sliderY + 35,
    labelY,
    cx: w / 2,
    cy: (titleBottom + labelTop) / 2,
    radius: Math.max(60, Math.min(148, (labelTop - titleBottom) / 2 - 40, (w - 48) / 2)),
    trackX0: 32,
    trackX1: w - 32,
    knobX0: 32 + 19,
    knobX1: w - 32 - 19,
  };
}

function run(font: SkFont, s: string, track: number): Run {
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + track;
  }
  const m = font.getMetrics();
  return { ids, pos, w: Math.max(0, x - (ids.length > 0 ? track : 0)), mid: -(m.ascent + m.descent) / 2, font };
}

function col(c: Rgb, a: number): SkColor {
  'worklet';
  return Float32Array.of(c.r, c.g, c.b, Math.min(1, Math.max(0, a)));
}

function fill(c: Rgb, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(c, a));
  return p;
}

function line(c: Rgb, a: number, width: number): SkPaint {
  'worklet';
  const p = fill(c, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function text(c: SkCanvas, r: Run, x: number, cy: number, color: Rgb, a: number) {
  'worklet';
  if (r.ids.length === 0 || a <= 0.001) return;
  c.drawGlyphs(r.ids, r.pos, x, cy + r.mid, r.font, fill(color, a));
}

function textCentered(c: SkCanvas, r: Run, cx: number, cy: number, color: Rgb, a: number) {
  'worklet';
  text(c, r, cx - r.w / 2, cy, color, a);
}

/** The closed outline of one layer: r(θ) = radius · radiusAt(θ − rot). */
function shape(cx: number, cy: number, radius: number, l: Look, rot: number, samples: number): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let s = 0; s < samples; s++) {
    const th = (s / samples) * 2 * Math.PI;
    const r = radius * radiusAt(th - rot, l);
    const x = cx + r * Math.cos(th);
    const y = cy + r * Math.sin(th);
    if (s === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.close();
  return p.detach();
}

function drawFlower(c: SkCanvas, f: Frame, cx: number, cy: number, R: number) {
  'worklet';
  const core = look(f.v);
  // Soft coloured glow under the plates
  const glow = R * 1.25;
  const gp = Skia.Paint();
  gp.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy },
      glow,
      [col(core.deep, 0.3), col(core.deep, 0.16), col(core.deep, 0)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(cx, cy, glow, gp);

  for (let i = 0; i < 6; i++) {
    const v = f.layerV[i];
    const l = look(v);
    const k = i / 5;
    const side = i % 2 === 0 ? 1 : -1;
    const wob = 1.6 * side * wobble(f.loopT, i) * f.breath;
    const rotDeg = 24 * v - (i === 0 ? l.twist : 0) + wob;
    const rot = -Math.PI / 2 + (rotDeg * Math.PI) / 180;
    const radius = R * l.size * LAYER_SCALE[i] * (1 + 0.012 * breathe(f.loopT, i) * f.breath + f.swell[i]);
    const path = shape(cx, cy, radius, l, rot, 240);
    const cl = layerColor(l, k);
    const a = LAYER_ALPHA[i];

    // Back edge: the plate's thickness, a thin darker outline just below and right
    c.save();
    c.translate(1.2, 2.0);
    c.drawPath(path, line(l.deep, 0.38, 1));
    c.restore();

    // Glass fill
    const fp = Skia.Paint();
    fp.setAntiAlias(true);
    fp.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx, y: cy },
        radius,
        [col(mix(cl, WHITE, 0.35), a), col(cl, Math.min(1, a * 1.15))],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawPath(path, fp);

    c.save();
    c.clipPath(path, ClipOp.Intersect, true);
    // Folded facets: each petal lit on one side of its ridge, shaded on the other
    const strength = (1 - valley(l)) * (0.3 + 0.7 * k);
    if (strength > 0.01) {
      const n = Math.round(l.n);
      const colors: SkColor[] = [];
      const stops: number[] = [];
      for (let j = 0; j < n; j++) {
        const s = j / n;
        const w = 1 / n;
        colors.push(col(l.deep, 0.3 * strength));
        stops.push(s + 0.0005);
        colors.push(col(WHITE, 0));
        stops.push(s + w * 0.5);
        colors.push(col(WHITE, 0.42 * strength));
        stops.push(s + w - 0.0005);
      }
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      sp.setShader(Skia.Shader.MakeSweepGradient(0, 0, colors, stops, TileMode.Clamp));
      c.save();
      c.translate(cx, cy);
      c.rotate((rot * 180) / Math.PI, 0, 0);
      c.drawCircle(0, 0, radius * 1.05, sp);
      c.restore();
    }
    // Inner glow along the rim
    c.drawPath(path, line(l.deep, 0.14 + 0.08 * k, 12));
    c.drawPath(path, line(l.deep, 0.1, 5));
    c.restore();

    // Bright rim, lit from the top left
    const b = path.computeTightBounds();
    const rp = Skia.Paint();
    rp.setAntiAlias(true);
    rp.setStyle(PaintStyle.Stroke);
    rp.setStrokeWidth(1.3);
    rp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: b.x, y: b.y },
        { x: b.x + b.width, y: b.y + b.height },
        [col(WHITE, 0.95), col(WHITE, 0.55), col(WHITE, 0.3)],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawPath(path, rp);
  }

  // Glowing white core in the same shape
  const cg = 26;
  const cp = Skia.Paint();
  cp.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy },
      cg,
      [col(WHITE, 0.85), col(WHITE, 0.25), col(WHITE, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawCircle(cx, cy, cg, cp);
  c.drawPath(shape(cx, cy, 6.5, core, -Math.PI / 2 + (24 * f.v * Math.PI) / 180, 80), fill(WHITE, 1));
}

function drawScreen(c: SkCanvas, f: Frame, lay: Layout, tx: Texts, held: boolean, nextDown: boolean) {
  'worklet';
  const l = look(f.v);
  const w = lay.w;
  const h = lay.h;
  const cx = lay.cx;
  const cy = lay.cy;
  const R = lay.radius;

  // Background wash and the halo behind the flower
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: h },
      [col(l.bgTop, 1), col(mix(l.bgTop, l.bgBottom, 0.55), 1), col(l.bgBottom, 1)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  const halo = R * 1.7;
  const hp = Skia.Paint();
  hp.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy },
      halo,
      [col(WHITE, 0.55), col(WHITE, 0.18), col(WHITE, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawCircle(cx, cy, halo, hp);

  drawFlower(c, f, cx, cy, R);

  // Slider
  const track = Skia.RRectXY(Skia.XYWHRect(lay.trackX0, lay.sliderY - 19, lay.trackX1 - lay.trackX0, 38), 19, 19);
  c.drawRRect(track, fill(WHITE, 0.34));
  c.drawRRect(track, fill(l.deep, 0.1));
  c.drawRRect(track, line(WHITE, 0.55, 0.6));
  const kx = lay.knobX0 + (lay.knobX1 - lay.knobX0) * ((Math.min(Math.max(f.v, -1), 1) + 1) / 2);
  const kr = held ? 18.4 : 17;
  const ks = fill(l.label, 0.18);
  ks.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.drawCircle(kx, lay.sliderY + 3, kr, ks);
  const kc = fill({ r: 0, g: 0, b: 0 }, 0.1);
  kc.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.8, true));
  c.drawCircle(kx, lay.sliderY + 1, kr, kc);
  c.drawCircle(kx, lay.sliderY, kr, fill(WHITE, 1));

  // Next button
  const s = nextDown ? 0.97 : 1;
  const bw = (lay.trackX1 - lay.trackX0) * s;
  const bh = 52 * s;
  const bx = cx - bw / 2;
  const by = lay.nextY - bh / 2;
  const btn = Skia.RRectXY(Skia.XYWHRect(bx, by, bw, bh), bh / 2, bh / 2);
  const bs = fill(l.button, 0.28);
  bs.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, by + 6, bw, bh), bh / 2, bh / 2), bs);
  c.drawRRect(btn, fill(l.button, 1));
  const hl = Skia.Paint();
  hl.setAntiAlias(true);
  hl.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: by }, { x: 0, y: by + bh / 2 }, [col(WHITE, 0.16), col(WHITE, 0)], null, TileMode.Clamp),
  );
  c.drawRRect(btn, hl);

  // Text
  const nav = mix(rgb(0x2a3038), l.label, 0.35);
  const chevron = Skia.PathBuilder.Make();
  chevron.moveTo(20.5, lay.navY - 8.5);
  chevron.lineTo(13, lay.navY);
  chevron.lineTo(20.5, lay.navY + 8.5);
  const cv = line(nav, 0.78, 2.2);
  cv.setStrokeCap(StrokeCap.Round);
  c.drawPath(chevron.detach(), cv);
  text(c, tx.back, 25, lay.navY, nav, 0.78);
  textCentered(c, tx.app, cx, lay.navY, nav, 1);
  text(c, tx.cancel, w - 16 - tx.cancel.w, lay.navY, nav, 0.78);

  const title = mix(rgb(0x23272e), l.label, 0.25);
  textCentered(c, tx.title1, cx, lay.titleY - 16.5, title, 1);
  textCentered(c, tx.title2, cx, lay.titleY + 16.5, title, 1);

  textCentered(c, tx.moods[f.labelPrev], cx, lay.labelY + f.prevDy, l.label, f.prevAlpha);
  textCentered(c, tx.moods[f.label], cx, lay.labelY + f.nowDy, l.label, f.nowAlpha);

  const cap = mix(rgb(0x3e4650), l.label, 0.3);
  text(c, tx.left, 36, lay.captionY, cap, 0.72);
  text(c, tx.right, w - 36 - tx.right.w, lay.captionY, cap, 0.72);

  c.save();
  c.translate(cx, lay.nextY);
  c.scale(s, s);
  textCentered(c, tx.next, 0, 0, WHITE, 1);
  c.restore();
}

export function GlassMoodBloom({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 1;
  const h = size?.height ?? 1;
  const lay = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);

  const texts = useMemo<Texts>(() => {
    const body = matchFont({ fontFamily: 'System', fontSize: 17, fontWeight: '400' });
    const bodyBold = matchFont({ fontFamily: 'System', fontSize: 17, fontWeight: '600' });
    const title = matchFont({ fontFamily: 'System', fontSize: 26, fontWeight: '600' });
    const mood = matchFont({ fontFamily: 'System', fontSize: 27, fontWeight: '700' });
    const caption = matchFont({ fontFamily: 'System', fontSize: 11, fontWeight: '600' });
    return {
      back: run(body, 'Back', 0),
      app: run(bodyBold, 'Petalog', 0),
      cancel: run(body, 'Cancel', 0),
      title1: run(title, 'Choose how you’re', 0),
      title2: run(title, 'feeling right now', 0),
      moods: MOODS.map((m) => run(mood, m.name, 0)),
      left: run(caption, 'VERY UNPLEASANT', 0.5),
      right: run(caption, 'VERY PLEASANT', 0.5),
      next: run(bodyBold, 'Next', 0),
    };
  }, []);

  const driver = useSharedValue<Driver>(makeDriver(auto, reduced));
  const frame = useSharedValue<Frame>(makeFrame(makeDriver(auto, reduced), 0));
  const now = useSharedValue(0);
  const kind = useSharedValue(0); // 0 none, 1 slider, 2 flower, 3 next
  const startV = useSharedValue(0);
  const lastBucket = useSharedValue(3);
  const held = useSharedValue(false);
  const nextDown = useSharedValue(false);

  useEffect(() => {
    driver.set(makeDriver(auto, reduced));
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(makeFrame(driver.get(), t));
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const d = driver.get();
      const slider = Math.abs(e.y - lay.sliderY) < 30 && e.x > lay.trackX0 - 12 && e.x < lay.trackX1 + 12;
      const flower = !slider && Math.hypot(e.x - lay.cx, e.y - lay.cy) < lay.radius * 1.15;
      const next = !slider && !flower && Math.abs(e.y - lay.nextY) < 30 && e.x > lay.trackX0 && e.x < lay.trackX1;
      if (next) {
        kind.set(3);
        nextDown.set(true);
        return;
      }
      if (!slider && !flower) {
        kind.set(0);
        return;
      }
      kind.set(slider ? 1 : 2);
      const v0 = touchDown(d, now.get());
      startV.set(v0);
      lastBucket.set(bucket(v0));
      held.set(true);
      if (slider) {
        const v = ((e.x - lay.knobX0) / Math.max(lay.knobX1 - lay.knobX0, 1)) * 2 - 1;
        touchMove(d, now.get(), v);
        const b = bucket(v);
        if (b !== lastBucket.get()) {
          lastBucket.set(b);
          scheduleOnRN(feel);
        }
      }
    })
    .onUpdate((e) => {
      const k = kind.get();
      if (k !== 1 && k !== 2) return;
      const v =
        k === 1
          ? ((e.x - lay.knobX0) / Math.max(lay.knobX1 - lay.knobX0, 1)) * 2 - 1
          : startV.get() + e.translationX / 160;
      const cl = Math.min(Math.max(v, -1), 1);
      touchMove(driver.get(), now.get(), cl);
      const b = bucket(cl);
      if (b !== lastBucket.get()) {
        lastBucket.set(b);
        scheduleOnRN(feel);
      }
    })
    .onFinalize(() => {
      const k = kind.get();
      kind.set(0);
      held.set(false);
      if (k === 3) {
        nextDown.set(false);
        scheduleOnRN(feel);
      } else if (k === 1 || k === 2) {
        touchUp(driver.get(), now.get());
      }
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, lay.w, lay.h));
    drawScreen(c, frame.get(), lay, texts, held.get(), nextDown.get());
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Choose how you're feeling right now"
        accessibilityHint="Drag the slider or the flower to change the mood."
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
  screen: { flex: 1, backgroundColor: '#e1e8ec' },
});
