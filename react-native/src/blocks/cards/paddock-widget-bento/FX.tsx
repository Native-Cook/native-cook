import React, { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { BlendMode, Canvas, ClipOp, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, useFont, type SkCanvas, type SkFont, type SkPaint } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { GOO, PEEL, PIT, SLOT_Y, TILE_COUNT, jiggle, pitTile, slapPose, smearAlpha, type Frame, type PeelState } from './motion';
import { FONT_FILES, alpha, colors, copy, layout, radius, space, type } from './theme';

/** Every pit-stop tile's resting rectangle (reference points), corner radius and smear colour, by TILE index. */
const TILES = (() => {
  const q = layout.quickCard;
  const a = layout.actionTile;
  const rows: { x: number; y: number; w: number; h: number; r: number; color: string }[] = [
    { x: q.x, y: SLOT_Y[0], w: q.width, h: q.height, r: radius.quickCard, color: colors.violet },
    { x: q.x, y: SLOT_Y[1], w: q.width, h: q.height, r: radius.quickCard, color: colors.orange },
    { x: q.x, y: SLOT_Y[2], w: q.width, h: q.height, r: radius.quickCard, color: colors.yellow },
    { x: layout.pitcast.left, y: layout.pitcast.top, w: layout.pitcast.width, h: layout.pitcast.height, r: radius.card, color: colors.cream },
    { x: layout.stats.left, y: layout.stats.top, w: layout.stats.width, h: layout.stats.height, r: radius.card, color: colors.statsTrail },
    { x: layout.glossary.left, y: layout.glossary.top, w: a.width, h: a.height, r: radius.quickCard, color: colors.green },
    { x: layout.education.left, y: layout.education.top, w: a.width, h: a.height, r: radius.quickCard, color: colors.blue },
    { x: layout.hero.left, y: layout.hero.top, w: layout.hero.width, h: layout.hero.height, r: radius.card, color: colors.red },
    { x: layout.guide.left, y: layout.guide.top, w: layout.guide.width, h: layout.guide.height, r: radius.card, color: colors.guideYellow },
    { x: layout.banner.left, y: layout.banner.top, w: layout.banner.width, h: layout.banner.height, r: radius.card, color: colors.bannerTrail },
  ];
  return rows;
})();
const CARD_COLORS = [colors.violet, colors.orange, colors.yellow];

/** A fill paint, made once on the JS thread (the frame worklets only change its alpha). */
function fill(color: string, alpha: number): SkPaint {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  paint.setAlphaf(Math.max(0, Math.min(1, alpha)));
  return paint;
}

/** The die-cut pair of a paint: itself, and a copy stroked PEEL.border wide (the sticker's margin). */
type Cut = { fill: SkPaint; stroke: SkPaint };
function cut(paint: SkPaint, s: number): Cut {
  const stroke = paint.copy();
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(2 * PEEL.border * s);
  stroke.setStrokeJoin(StrokeJoin.Round);
  return { fill: paint, stroke };
}

/**
 * The pit stop's motion blur: behind every tile in flight, a smear of its own colour from where it was 64 ms ago to
 * where it is, transparent at the tail, stronger with speed. One Skia picture per frame, under the tiles.
 */
export function PitTrails({ s, pit, frame }: { s: number; pit: SharedValue<number>; frame: SharedValue<Frame> }) {
  const width = layout.grid.width * s;
  const height = layout.grid.height * s;
  // Per tile: a paint holding a unit gradient (transparent at x 0, its colour at x 1); the frame stretches it.
  const smears = useMemo(
    () =>
      TILES.map((tile) => {
        const paint = Skia.Paint();
        paint.setAntiAlias(true);
        const color = Skia.Color(tile.color);
        const clear = Skia.Color(tile.color);
        clear[3] = 0;
        paint.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(1, 0), [clear, color], null, TileMode.Clamp));
        return paint;
      }),
    [],
  );
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const t = pit.get();
    if (t > 0) {
      const ys = frame.get().y;
      for (let i = 0; i < TILE_COUNT; i++) {
        const tile = TILES[i];
        const top = i < 3 ? ys[i] : tile.y;
        const head = pitTile(i, t);
        const alpha = smearAlpha(head.speed);
        if (alpha < 0.01) continue;
        const tail = pitTile(i, t - PIT.ghosts * PIT.ghostStep);
        const left = Math.min(head.x, tail.x);
        const right = Math.max(head.x, tail.x) + tile.w;
        const y = (head.y + tail.y) / 2;
        // The gradient runs from the tail's centre (clear) to the head's (its colour at `alpha`): map x so that
        // those two points are 0 and 1, and draw the smear's rectangle in that space (radius kept round).
        const x0 = (tile.x + tail.x + tile.w / 2) * s;
        const k = (head.x - tail.x) * s;
        if (Math.abs(k) < 0.5) continue;
        const paint = smears[i];
        paint.setAlphaf(Math.min(1, alpha));
        const a = ((tile.x + left) * s - x0) / k;
        const b = ((tile.x + right) * s - x0) / k;
        canvas.save();
        canvas.translate(x0, 0);
        canvas.scale(k, 1);
        const rect = Skia.XYWHRect(Math.min(a, b), (top + y) * s, Math.abs(b - a), tile.h * s);
        canvas.drawRRect(Skia.RRectXY(rect, (tile.r * s) / Math.abs(k), tile.r * s), paint);
        canvas.restore();
      }
    }
    return recorder.finishRecordingAsPicture();
  });
  return (
    <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0, width, height }}>
      <Picture picture={picture} />
    </Canvas>
  );
}

/**
 * Gooey neighbours: while the cards are being arranged, the three capsules are drawn again under the real cards,
 * blurred and alpha-thresholded as one layer, so where two come within a few points they join with a liquid bridge
 * of mixed colour that stretches and snaps as they part. Nothing shows at rest (the 12 pt gaps stay open).
 */
export function GooLayer({ s, frame, time }: { s: number; frame: SharedValue<Frame>; time: SharedValue<number> }) {
  const pad = GOO.pad;
  const q = layout.quickCard;
  const width = (q.width + 2 * pad) * s;
  const height = (SLOT_Y[2] - SLOT_Y[0] + q.height + 2 * pad) * s;
  const paints = useMemo(() => {
    const layer = Skia.Paint();
    const blur = Skia.ImageFilter.MakeBlur(GOO.blur * s, GOO.blur * s, TileMode.Decal, null);
    const threshold = Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, GOO.gain, -GOO.bias]);
    layer.setImageFilter(Skia.ImageFilter.MakeColorFilter(threshold, blur));
    return { layer, cards: CARD_COLORS.map((c) => fill(c, 1)) };
  }, [s]);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const f = frame.get();
    if (f.handles > 0.01) {
      const layer = paints.layer;
      layer.setAlphaf(Math.min(1, f.handles));
      canvas.saveLayer(layer);
      const t = time.get();
      // The held card last, so its colour leads where it overlaps.
      for (let pass = 0; pass < 2; pass++) {
        for (let id = 0; id < 3; id++) {
          const held = f.selected === id;
          if ((pass === 1) !== held) continue;
          const cx = (pad + q.width / 2) * s;
          const cy = (pad + f.y[id] - SLOT_Y[0] + q.height / 2) * s;
          canvas.save();
          canvas.translate(cx, cy);
          canvas.rotate(held ? f.rotation : jiggle(id, t, f.handles), 0, 0);
          if (held) canvas.scale(f.scale, f.scale);
          const rect = Skia.XYWHRect((-q.width / 2) * s, (-q.height / 2) * s, q.width * s, q.height * s);
          canvas.drawRRect(Skia.RRectXY(rect, radius.quickCard * s, radius.quickCard * s), paints.cards[id]);
          canvas.restore();
        }
      }
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });
  return (
    <Canvas pointerEvents="none" style={{ position: 'absolute', left: (q.x - pad) * s, top: (SLOT_Y[0] - pad) * s, width, height }}>
      <Picture picture={picture} />
    </Canvas>
  );
}

/** Clips to the half-plane through (mx, my) on the side `side`·(ux, uy) points to (no path: a rotated rectangle). */
function clipHalf(canvas: SkCanvas, mx: number, my: number, ux: number, uy: number, side: number) {
  'worklet';
  const L = 4000;
  const deg = (Math.atan2(side * uy, side * ux) * 180) / Math.PI;
  canvas.translate(mx, my);
  canvas.rotate(deg, 0, 0);
  canvas.clipRect(Skia.XYWHRect(0, -L, L, 2 * L), ClipOp.Intersect, true);
  canvas.rotate(-deg, 0, 0);
  canvas.translate(-mx, -my);
}

function letters(canvas: SkCanvas, font: SkFont, s: number, first: SkPaint, second: SkPaint) {
  'worklet';
  const b = layout.baseline;
  canvas.drawText(copy.hero.first, space.heroFirstX * s, b.heroFirst * s, first, font);
  canvas.drawText(copy.hero.second, space.heroSecondX * s, b.heroSecond * s, second, font);
}

/** The die-cut outline of the sticker: the lettering grown by PEEL.border on every side, counters filled. */
function silhouette(canvas: SkCanvas, font: SkFont, s: number, paint: Cut) {
  'worklet';
  letters(canvas, font, s, paint.stroke, paint.stroke);
  letters(canvas, font, s, paint.fill, paint.fill);
}

/** Reflects the canvas across the line through (mx, my) perpendicular to (ux, uy): the fold of the sticker. */
function reflect(canvas: SkCanvas, mx: number, my: number, ux: number, uy: number) {
  'worklet';
  const along = (Math.atan2(uy, ux) * 180) / Math.PI + 90;
  canvas.translate(mx, my);
  canvas.rotate(along, 0, 0);
  canvas.scale(1, -1);
  canvas.rotate(-along, 0, 0);
  canvas.translate(-mx, -my);
}

/**
 * "exclusive widgets" as a die-cut sticker drawn in Skia (the hero card keeps the accessible label). The corner is
 * pulled to `distance` along `angle`: letters past the fold are cut from the card (a faint glue ghost stays), and flip
 * over the fold as the sticker's paper back, shaded at the bend and casting a soft shadow. The slap after a peel
 * presses the lettering and throws impact lines (the card itself shakes).
 */
export function StickerWordmark({ s, peel }: { s: number; peel: SharedValue<PeelState> }) {
  const font = useFont(FONT_FILES['PaddockMontserrat-ExtraBold'], type.heroGlyph * s);
  const pad = GOO.pad;
  const width = (layout.hero.width + 2 * pad) * s;
  const height = (layout.hero.height + 2 * pad) * s;
  // Every paint of the sticker, made once; the frame only changes alphas.
  const P = useMemo(() => {
    const shadow = fill(colors.stickerShadow, alpha.stickerShadow);
    shadow.setImageFilter(Skia.ImageFilter.MakeBlur(PEEL.shadowBlur * s, PEEL.shadowBlur * s, TileMode.Decal, null));
    // The paper back: flat, with the fold's darker tone laid over it from the bend (x 0) to PEEL.foldShade (x 1).
    const fold = Skia.Color(colors.stickerFold);
    const foldClear = Skia.Color(colors.stickerFold);
    foldClear[3] = 0;
    const shade = Skia.Paint();
    shade.setAntiAlias(true);
    shade.setBlendMode(BlendMode.SrcATop);
    shade.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(1, 0), [fold, foldClear], null, TileMode.Clamp));
    const line = fill(colors.slapLine, 1);
    line.setStrokeWidth(3 * s);
    line.setStrokeCap(StrokeCap.Round);
    return {
      rose: fill(colors.rose, 1),
      white: fill(colors.white, 1),
      red: cut(fill(colors.red, 1), s),
      glue: cut(fill(colors.white, alpha.glueGhost), s),
      shadow: cut(shadow, s),
      back: cut(fill(colors.stickerBack, 1), s),
      shade,
      layer: Skia.Paint(),
      line,
    };
  }, [s]);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (font) {
      const state = peel.get();
      const slap = slapPose(state.slap);
      canvas.translate(pad * s, pad * s);
      const cx = (layout.hero.width / 2) * s;
      const cy = (layout.hero.height / 2) * s;
      canvas.save();
      canvas.translate(cx, cy);
      canvas.scale(slap.press, slap.press);
      canvas.translate(-cx, -cy);
      const rose = P.rose;
      const white = P.white;
      if (state.distance < 0.5) {
        letters(canvas, font, s, rose, white);
      } else {
        const ux = Math.cos((state.angle * Math.PI) / 180);
        const uy = Math.sin((state.angle * Math.PI) / 180);
        const kx = PEEL.cornerX * s;
        const ky = PEEL.cornerY * s;
        const mx = kx + (ux * state.distance * s) / 2;
        const my = ky + (uy * state.distance * s) / 2;
        // The part still stuck down: red vinyl (the card's own colour, so invisible at rest) printed with the letters.
        canvas.save();
        clipHalf(canvas, mx, my, ux, uy, 1);
        silhouette(canvas, font, s, P.red);
        letters(canvas, font, s, rose, white);
        canvas.restore();
        // Glue left where the flap came away.
        canvas.save();
        clipHalf(canvas, mx, my, ux, uy, -1);
        silhouette(canvas, font, s, P.glue);
        canvas.restore();
        // The flap's shadow on the card, a little further along the pull and down.
        canvas.save();
        canvas.translate(ux * PEEL.shadowOffset * s, (PEEL.shadowOffset * s) / 2 + uy * PEEL.shadowOffset * s);
        reflect(canvas, mx, my, ux, uy);
        clipHalf(canvas, mx, my, ux, uy, -1);
        silhouette(canvas, font, s, P.shadow);
        canvas.restore();
        // The flap: the sticker seen from the back (plain paper), darker at the bend.
        canvas.save();
        reflect(canvas, mx, my, ux, uy);
        clipHalf(canvas, mx, my, ux, uy, -1);
        canvas.saveLayer(P.layer);
        silhouette(canvas, font, s, P.back);
        // The bend's shade over the paper only (src-atop), along −u from the fold line.
        canvas.translate(mx, my);
        canvas.rotate((Math.atan2(-uy, -ux) * 180) / Math.PI, 0, 0);
        canvas.scale(PEEL.foldShade * s, 1);
        canvas.drawRect(Skia.XYWHRect(-1, -4000, 1e4, 8000), P.shade);
        canvas.restore();
        canvas.restore();
      }
      canvas.restore();
      if (slap.lines >= 0) {
        const e = slap.lines;
        const line = P.line;
        line.setAlphaf(Math.max(0, Math.min(1, 1 - e)));
        const ox = (PEEL.cornerX - 8) * s;
        const oy = (PEEL.cornerY - 30) * s;
        for (let i = 0; i < PEEL.lines; i++) {
          const a = ((-100 + (i * 120) / (PEEL.lines - 1)) * Math.PI) / 180;
          const inner = (PEEL.lineInner + PEEL.lineReach * e) * s;
          const outer = inner + PEEL.lineLength * (1 - e) * s;
          canvas.drawLine(ox + Math.cos(a) * inner, oy + Math.sin(a) * inner, ox + Math.cos(a) * outer, oy + Math.sin(a) * outer, line);
        }
      }
    }
    return recorder.finishRecordingAsPicture();
  });
  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { overflow: 'visible' }]}>
      <Canvas style={{ position: 'absolute', left: -pad * s, top: -pad * s, width, height }}>
        <Picture picture={picture} />
      </Canvas>
    </View>
  );
}
