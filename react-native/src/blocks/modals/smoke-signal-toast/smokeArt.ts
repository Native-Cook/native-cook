import { BlurStyle, matchFont, Skia, type SkCanvas, type SkFont, type SkImage } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

/*
 The message the smoke forms, baked once into two full-screen masks: a sharp one and a blurred one
 (the shader blends from the blurred one while the words condense and to it again as they tear).
 Text is measured with getGlyphWidths (measureText rounds each glyph) and the message line is
 sized down until it fits the screen.
*/

export const SENDER = "KAI · LAKESIDE CREW";
export const MESSAGE = "Dinner's ready at the lake";

const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SCALE = 2;

export type MessageArt = { sharp: SkImage; soft: SkImage; box: { x: number; y: number; width: number; height: number } };

function widthOf(font: SkFont, text: string) {
  const ids = font.getGlyphIDs(text);
  const ws = font.getGlyphWidths(ids);
  let sum = 0;
  for (let i = 0; i < ws.length; i += 1) sum += ws[i];
  return sum;
}

function render(w: number, h: number, paint: (canvas: SkCanvas) => void): SkImage | null {
  const pw = Math.max(1, Math.ceil(w * SCALE));
  const ph = Math.max(1, Math.ceil(h * SCALE));
  const surface = Skia.Surface.MakeOffscreen?.(pw, ph) ?? Skia.Surface.Make(pw, ph);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(SCALE, SCALE);
  paint(canvas);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

export function bakeMessage(w: number, h: number, centerY: number): MessageArt | null {
  const small = matchFont({ fontFamily: FAMILY, fontSize: 13, fontWeight: '700' });
  let size = 27;
  let big = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '600' });
  const room = w - 56;
  while (widthOf(big, MESSAGE) > room && size > 18) {
    size -= 1;
    big = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '600' });
  }
  const bw = widthOf(big, MESSAGE);
  const sw = widthOf(small, SENDER);
  const top = centerY - (13 + 10 + size) / 2;
  const senderBase = top + 12;
  const messageBase = top + 13 + 10 + size * 0.8;
  const draw = (blur: number) => (canvas: SkCanvas) => {
    const paint = Skia.Paint();
    paint.setColor(Skia.Color('#ffffff'));
    if (blur > 0) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    canvas.drawText(SENDER, (w - sw) / 2, senderBase, paint, small);
    canvas.drawText(MESSAGE, (w - bw) / 2, messageBase, paint, big);
  };
  const sharp = render(w, h, draw(0.4));
  const soft = render(w, h, draw(7));
  if (!sharp || !soft) return null;
  return { sharp, soft, box: { x: (w - Math.max(bw, sw)) / 2 - 16, y: top - 16, width: Math.max(bw, sw) + 32, height: size + 23 + 32 } };
}
