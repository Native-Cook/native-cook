import { Canvas, ClipOp, PaintStyle, Picture, Skia, type SkCanvas, type SkColor, type SkPaint, type SkShader, TileMode, vec } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { LOOP, PITCH, STEPS, offsetOf } from './motion';

export const CARD_WIDTH = 298;
export const CARD_HEIGHT = 452;
const CARD_RADIUS = 34;

/** The clouds are placed for a screen this tall and keep their distance from its bottom edge. */
const SKY_HEIGHT = 852;
const SKY_STOPS = [0, 0.55, 1];
const CLOUD_LIGHT = '241,244,249';
const CLOUD_SHADE = '167,181,204';
const VEIL = 'rgba(0,0,0,0.08)';
const EDGE = 'rgba(255,255,255,0.14)';

// How a puff fades from its middle to its rim: full for a third of the way, then a soft edge.
const PUFF_STOPS = [0, 0.3, 0.55, 0.78, 1];
const PUFF_EDGE = [1, 0.96, 0.7, 0.28, 0];
// Seen through glass a puff is GLASS_SPREAD larger all round, fades from its very middle, and is
// thinner the smaller it was: what a blur of that reach makes of it, without blurring anything.
const GLASS_STOPS = [0, 0.25, 0.5, 0.75, 1];
const GLASS_EDGE = [1, 0.8, 0.42, 0.13, 0];
const GLASS_SPREAD = 34;
const GLASS_GAIN = 2.4;

/**
 * The sky in three depths. A layer is one strip of clouds, `period` points long, that repeats
 * across the screen and moves left by its own length in a loop: the far strip is the shortest, so
 * it is the slowest. A puff is x, y, rx, ry, whether it is shaded (the belly of a cloud), alpha.
 */
const LAYERS = [
  {
    period: 131,
    puffs: [
      40, 34, 92, 30, 0, 0.22,
      66, 690, 50, 18, 1, 0.64,
      37, 666, 47, 39, 0, 0.8,
      62, 660, 61, 51, 0, 0.8,
      96, 666, 48, 40, 0, 0.8,
      79, 663, 38, 33, 0, 0.8,
      30, 851, 38, 9, 1, 0.8,
      8, 840, 21, 18, 0, 1,
      23, 839, 23, 19, 0, 1,
      49, 839, 24, 20, 0, 1,
      30, 837, 21, 18, 0, 1,
    ],
  },
  {
    period: 196.5,
    puffs: [
      110, 78, 130, 26, 0, 0.18,
      30, 196, 120, 46, 0, 0.12,
      70, 416, 55, 26, 1, 0.74,
      42, 391, 47, 39, 0, 0.92,
      65, 380, 73, 61, 0, 0.92,
      106, 383, 66, 55, 0, 0.92,
      59, 377, 51, 45, 0, 0.92,
      150, 844, 50, 12, 1, 0.8,
      124, 830, 27, 22, 0, 1,
      145, 829, 28, 24, 0, 1,
      179, 832, 21, 18, 0, 1,
      145, 825, 27, 23, 0, 1,
      36, 356, 28, 23, 0, 0.92,
      70, 330, 36, 30, 0, 0.92,
      108, 346, 30, 25, 0, 0.92,
      84, 364, 26, 20, 1, 0.45,
    ],
  },
  {
    period: 262,
    puffs: [
      150, 490, 75, 57, 1, 0.8,
      105, 426, 122, 102, 0, 1,
      139, 423, 129, 108, 0, 1,
      159, 412, 152, 127, 0, 1,
      193, 423, 128, 107, 0, 1,
      119, 402, 97, 85, 0, 1,
      157, 391, 124, 108, 0, 1,
      30, 776, 75, 27, 1, 0.72,
      -16, 752, 44, 37, 0, 0.9,
      20, 740, 70, 59, 0, 0.9,
      51, 731, 90, 75, 0, 0.9,
      76, 751, 46, 38, 0, 0.9,
      12, 734, 50, 44, 0, 0.9,
      42, 729, 59, 51, 0, 0.9,
      62, 356, 38, 30, 0, 1,
      104, 322, 44, 36, 0, 1,
      150, 298, 50, 42, 0, 1,
      198, 316, 44, 36, 0, 1,
      238, 348, 36, 30, 0, 1,
      128, 340, 34, 28, 1, 0.5,
      182, 352, 30, 24, 1, 0.45,
    ],
  },
];

/** A circle of radius 1 in one colour that fades outwards; a puff is this circle stretched. */
function puffShader(rgb: string, edge: number[], stops: number[]) {
  'worklet';
  const colors = edge.map((alpha) => Skia.Color(`rgba(${rgb},${alpha})`));
  return Skia.Shader.MakeRadialGradient(vec(0, 0), 1, colors, stops, TileMode.Clamp);
}

/**
 * Draws the clouds that reach into the box from `left` to `right` and `above` to `below`.
 * `turn` is how far through the loop the sky is, 0 to 1; `top` is where the clouds' own zero is.
 */
function drawClouds(
  canvas: SkCanvas,
  paint: SkPaint,
  light: SkShader,
  shade: SkShader,
  glass: boolean,
  turn: number,
  top: number,
  left: number,
  right: number,
  above: number,
  below: number,
) {
  'worklet';
  const spread = glass ? GLASS_SPREAD : 0;
  for (let l = 0; l < LAYERS.length; l++) {
    const { period, puffs } = LAYERS[l];
    for (let i = 0; i < puffs.length; i += 6) {
      const rx = puffs[i + 2] + spread;
      const ry = puffs[i + 3] + spread;
      const y = puffs[i + 1] + top;
      if (y + ry < above || y - ry > below) continue;
      const thin = glass ? Math.min(1, (GLASS_GAIN * puffs[i + 2] * puffs[i + 3]) / (rx * ry)) : 1;
      paint.setShader(puffs[i + 4] === 1 ? shade : light);
      paint.setAlphaf(puffs[i + 5] * thin);
      // The first copy that reaches the box, then one every period until they are past it.
      let x = puffs[i] - turn * period;
      x += Math.ceil((left - rx - x) / period) * period;
      for (; x - rx < right; x += period) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(rx, ry);
        canvas.drawCircle(0, 0, 1, paint);
        canvas.restore();
      }
    }
  }
}

type Props = {
  /** Seconds; the sky has come round once every LOOP of them. */
  clock: SharedValue<number>;
  /** Where the carousel is, in cards. */
  position: SharedValue<number>;
  /** Reduced motion: the sky does not move. */
  still: boolean;
  /** The screen in the block's own points, and how much those are scaled to fit it. */
  width: number;
  height: number;
  fit: number;
  /** The top edge of the cards. */
  cardTop: number;
  /** The sky from top to bottom. */
  colors: string[];
  /** What the glass adds over the blurred sky. */
  glassColor: string;
};

/**
 * A blue sky with clouds that drift, and the three panes of glass in front of it. Inside a pane
 * the same sky is drawn again as a blur would leave it, then tinted, so the clouds pass behind
 * the cards as slow shifts of light.
 */
export function Sky({ clock, position, still, width, height, fit, cardTop, colors, glassColor }: Props) {
  const picture = useDerivedValue(() => {
    const turn = still ? 0 : (clock.get() / LOOP) % 1;
    const at = position.get();
    const top = height - SKY_HEIGHT;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width * fit, height * fit));
    canvas.scale(fit, fit);
    const screen = Skia.XYWHRect(0, 0, width, height);

    // A loop, not `colors.map(...)` with a function written here: React Compiler moves a function
    // that reads nothing of the component out of it, and what it leaves in its place is no
    // worklet, so calling it on the UI thread throws and takes the app down.
    const tones: SkColor[] = [];
    for (let i = 0; i < colors.length; i++) tones.push(Skia.Color(colors[i]));
    const sky = Skia.Paint();
    sky.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, height), tones, colors.length === SKY_STOPS.length ? SKY_STOPS : null, TileMode.Clamp));
    const veil = Skia.Paint();
    veil.setColor(Skia.Color(VEIL));
    const tint = Skia.Paint();
    tint.setColor(Skia.Color(glassColor));
    const edge = Skia.Paint();
    edge.setColor(Skia.Color(EDGE));
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1);
    const puff = Skia.Paint();

    canvas.drawRect(screen, sky);
    drawClouds(canvas, puff, puffShader(CLOUD_LIGHT, PUFF_EDGE, PUFF_STOPS), puffShader(CLOUD_SHADE, PUFF_EDGE, PUFF_STOPS), false, turn, top, 0, width, 0, height);
    // A thin veil keeps white text readable over the clouds.
    canvas.drawRect(screen, veil);

    const light = puffShader(CLOUD_LIGHT, GLASS_EDGE, GLASS_STOPS);
    const shade = puffShader(CLOUD_SHADE, GLASS_EDGE, GLASS_STOPS);
    for (let step = 0; step < STEPS; step++) {
      const x = (width - CARD_WIDTH) / 2 + offsetOf(step, at) * PITCH;
      if (x + CARD_WIDTH <= 0 || x >= width) continue;
      canvas.save();
      canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x, cardTop, CARD_WIDTH, CARD_HEIGHT), CARD_RADIUS, CARD_RADIUS), ClipOp.Intersect, true);
      canvas.drawRect(screen, sky);
      drawClouds(canvas, puff, light, shade, true, turn, top, x, x + CARD_WIDTH, cardTop, cardTop + CARD_HEIGHT);
      canvas.drawRect(screen, veil);
      canvas.drawRect(screen, tint);
      canvas.restore();
      // A hairline of light just inside the pane's edge.
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, cardTop + 0.5, CARD_WIDTH - 1, CARD_HEIGHT - 1), CARD_RADIUS - 0.5, CARD_RADIUS - 0.5), edge);
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
