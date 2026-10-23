/** The call buttons' handset glyphs (the alt reference's flat white glyphs), built once. */
import { Skia, type SkPath } from '@shopify/react-native-skia';

export type HandsetPaths = { glyph: SkPath; glyphDown: SkPath };

/** A small handset glyph for the buttons, 26 pt wide, centred on the origin, lying flat. */
function glyphPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-12, 1);
  b.cubicTo(-12, -6, -6, -8, 0, -8);
  b.cubicTo(6, -8, 12, -6, 12, 1);
  b.lineTo(12, 4);
  b.cubicTo(12, 5.5, 10.5, 6, 9.5, 5.6);
  b.lineTo(5.5, 4.2);
  b.cubicTo(4.6, 3.9, 4, 3, 4, 2);
  b.lineTo(4, -1.5);
  b.cubicTo(1.5, -2.4, -1.5, -2.4, -4, -1.5);
  b.lineTo(-4, 2);
  b.cubicTo(-4, 3, -4.6, 3.9, -5.5, 4.2);
  b.lineTo(-9.5, 5.6);
  b.cubicTo(-10.5, 6, -12, 5.5, -12, 4);
  b.close();
  return b.build();
}

export function makeHandsetPaths(): HandsetPaths {
  const glyph = glyphPath();
  // Accept shows the receiver lifted (turned 135°), decline the receiver down, as the alt source's buttons.
  const lifted = Skia.PathBuilder.Make();
  lifted.addPath(glyph);
  lifted.transform(Skia.Matrix().rotate((-3 * Math.PI) / 4));
  return { glyph: lifted.build(), glyphDown: glyph };
}
