import { ClipOp, matchFont, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { ATLAS_SCALE, DESIGNS, MONTHS, PERIOD_H, PERIOD_W, type Tile, TILES, TINTS } from './model';

/*
 One period of the wall (308 × 440 pt at 4 px / pt), drawn once into an offscreen surface (a port of
 FisheyeMemoryWallAtlas.swift). The lens shader samples it with repeat tiling, so the wall never ends.
*/

const GUTTER_COLOR = 0x151922;
const FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

function color(v: number, a: number) {
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

function paint(v: number, a: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color(v, a));
  return p;
}

/** Draws a line of text centred on (x, y), with tracking. */
function text(c: SkCanvas, s: string, size: number, weight: '700' | '800', v: number, tracking: number, x: number, y: number) {
  const font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  // Centre on the cap height, like the other stacks.
  const bounds = font.measureText('H');
  c.drawGlyphs(ids, positions, x - w / 2, y + bounds.height / 2, font, paint(v, 1));
}

function drawOp(op: number[], c: SkCanvas, tile: Tile, ox: number, oy: number, side: number) {
  const X = (u: number) => ox + u * side;
  const Y = (v: number) => oy + v * side;
  const r = Skia.XYWHRect(tile.x, tile.y, tile.width, tile.height);
  switch (op[0]) {
    case 0: {
      const p = paint(0, 1);
      p.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: tile.y },
          { x: 0, y: tile.y + tile.height },
          [color(op[1], 1), color(op[2], 1), color(op[3], 1)],
          [0, 0.55, 1],
          TileMode.Clamp,
        ),
      );
      c.drawRect(r, p);
      break;
    }
    case 1:
      c.drawCircle(X(op[1]), Y(op[2]), op[3] * side, paint(op[4], op[5]));
      break;
    case 2: {
      const rx = op[3] * side;
      const ry = op[4] * side;
      c.drawOval(Skia.XYWHRect(X(op[1]) - rx, Y(op[2]) - ry, rx * 2, ry * 2), paint(op[5], op[6]));
      break;
    }
    case 3: {
      const w = op[3] * side;
      const h = op[4] * side;
      const rad = Math.min(op[5] * side, w / 2, h / 2);
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(X(op[1]), Y(op[2]), w, h), rad, rad), paint(op[6], op[7]));
      break;
    }
    case 4: {
      const b = Skia.PathBuilder.Make();
      b.moveTo(X(op[3]), Y(op[4]));
      for (let i = 5; i + 1 < op.length; i += 2) b.lineTo(X(op[i]), Y(op[i + 1]));
      b.close();
      c.drawPath(b.detach(), paint(op[1], op[2]));
      break;
    }
    case 5: {
      const p = paint(op[6], op[7]);
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeWidth(op[5] * side);
      p.setStrokeCap(StrokeCap.Round);
      c.drawLine(X(op[1]), Y(op[2]), X(op[3]), Y(op[4]), p);
      break;
    }
    case 6: {
      const p = paint(0, 1);
      p.setShader(
        Skia.Shader.MakeRadialGradient(
          { x: X(op[1]), y: Y(op[2]) },
          op[3] * side,
          [color(op[4], op[5]), color(op[4], 0)],
          null,
          TileMode.Clamp,
        ),
      );
      c.drawCircle(X(op[1]), Y(op[2]), op[3] * side, p);
      break;
    }
    default: {
      // The tile's date: the month over the day, centred (not mirrored).
      const cx = tile.x + tile.width / 2;
      c.save();
      if (tile.flip) {
        c.translate(cx, 0);
        c.scale(-1, 1);
        c.translate(-cx, 0);
      }
      text(c, MONTHS[tile.month], 0.17 * side, '700', 0xd2553f, 0.02 * side, cx, Y(0.33));
      text(c, `${tile.day}`, 0.42 * side, '800', 0x1f2430, -0.02 * side, cx, Y(0.63));
      c.restore();
    }
  }
}

function drawTile(c: SkCanvas, tile: Tile) {
  const r = Skia.XYWHRect(tile.x, tile.y, tile.width, tile.height);
  const shape = Skia.RRectXY(r, tile.radius, tile.radius);
  c.save();
  c.clipRRect(shape, ClipOp.Intersect, true);
  // The design's unit square covers the tile, centred.
  const side = Math.max(tile.width, tile.height);
  const cx = tile.x + tile.width / 2;
  const ox = cx - side / 2;
  const oy = tile.y + tile.height / 2 - side / 2;
  c.save();
  if (tile.flip) {
    c.translate(cx, 0);
    c.scale(-1, 1);
    c.translate(-cx, 0);
  }
  const ops = DESIGNS[tile.design];
  for (let i = 0; i < ops.length; i++) drawOp(ops[i], c, tile, ox, oy, side);
  c.restore();
  if (tile.tint > 0) c.drawRect(r, paint(TINTS[tile.tint], 0.12));
  // A hairline of light along the edge.
  const edge = paint(0xffffff, 0.1);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  c.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(tile.x + 0.5, tile.y + 0.5, tile.width - 1, tile.height - 1), tile.radius - 0.5, tile.radius - 0.5),
    edge,
  );
  c.restore();
}

/** The wall's period as one image, ATLAS_SCALE pixels per point. */
export function bakeAtlas(): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(PERIOD_W * ATLAS_SCALE, PERIOD_H * ATLAS_SCALE);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(ATLAS_SCALE, ATLAS_SCALE);
  c.drawRect(Skia.XYWHRect(0, 0, PERIOD_W, PERIOD_H), paint(GUTTER_COLOR, 1));
  for (let i = 0; i < TILES.length; i++) drawTile(c, TILES[i]);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}
