import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// Gold leaf, mostly; a few flakes are in shadow.
const COLOURS = ['#EBC381', '#DDA85A', '#F6DCA3', '#CF9647', '#F2CD8C', '#3B3A40'];
const PAINTS = COLOURS.map((colour) => {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(colour));
  return paint;
});

const FLASH = Skia.Paint();
FLASH.setAntiAlias(true);
FLASH.setColor(Skia.Color('#FFF6DC'));

type Flake = {
  x: number;
  lead: number;
  speed: number;
  drift: number;
  driftRate: number;
  spin: number;
  spinRate: number;
  tumble: number;
  tumbleRate: number;
  width: number;
  height: number;
  colour: number;
  depth: number;
};

// A small fixed generator, so the same flakes fall in the same places on every run and in every
// build of the block.
function flakes(count: number): Flake[] {
  let seed = 20261013;
  const next = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
    return (seed >>> 0) / 4294967296;
  };
  return Array.from({ length: count }, () => {
    const depth = next();
    return {
      x: next(),
      lead: next(),
      speed: 62 + 88 * next(),
      drift: 6 + 14 * next(),
      driftRate: 0.6 + 1.3 * next(),
      spin: next() * Math.PI * 2,
      spinRate: (next() < 0.5 ? -1 : 1) * (1.2 + 3.4 * next()),
      tumble: next() * Math.PI * 2,
      tumbleRate: 2.2 + 4.6 * next(),
      width: 6 + 4 * next(),
      height: 3.6 + 2.2 * next(),
      colour: next() < 0.12 ? 5 : Math.floor(next() * 5),
      depth,
    };
  });
}

const FLAKES = flakes(110);

type Props = {
  width: number;
  height: number;
  /** Seconds since the strike; the clock pauses while the sheet is closed. */
  time: SharedValue<number>;
  /** Which side of the medal this layer falls on: the far flakes are smaller, slower and dimmer. */
  layer: 'far' | 'near';
  scale: number;
  /** Reduce Motion: the flakes hang where they would be once the rain has filled the sheet. */
  still: boolean;
};

/**
 * Gold flakes raining down the sheet. They start above its top edge, so the first second reads as
 * a burst, and wrap round from the bottom after that, so it never thins out.
 */
export function MedalConfetti({ width, height, time, layer, scale, still }: Props) {
  const picture = useDerivedValue(() => {
    'worklet';
    const t = still ? 20 : time.get();
    const fall = height + 60;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
        for (let i = 0; i < FLAKES.length; i++) {
          const f = FLAKES[i];
          const near = f.depth > 0.58;
          if (near !== (layer === 'near')) continue;
          const size = (0.62 + 0.62 * f.depth) * scale;
          const travelled = f.speed * (0.7 + 0.5 * f.depth) * t - f.lead * fall * 0.9;
          if (travelled < 0) continue;
          const y = (travelled % fall) - 30;
          const x = f.x * width + f.drift * Math.sin(f.driftRate * t + f.spin);
          const flip = Math.cos(f.tumble + f.tumbleRate * t);
          // Face-on to the light for an instant in each tumble, a flake of leaf flashes.
          const flash = f.colour < 5 && Math.pow(Math.abs(flip), 28) > 0.5;
          const paint = flash ? FLASH : PAINTS[f.colour];
          const alpha = flash ? 1 : (near ? 0.95 : 0.6) * (0.55 + 0.45 * Math.abs(flip));
          // All copy begins below the stage; neither diffuse leaf nor flashes may veil it.
          paint.setAlphaf(y > 480 * scale ? Math.min(0.35, alpha * 0.25) : alpha);
          canvas.save();
          canvas.translate(x, y);
          canvas.rotate(((f.spin + f.spinRate * t) * 180) / Math.PI, 0, 0);
          // Never quite edge-on: a flake that thins to a hair flickers.
          canvas.scale(size, size * (flip < 0 ? -1 : 1) * Math.max(Math.abs(flip), 0.28));
          const w = f.width;
          const h = f.height;
          canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), h / 2, h / 2), paint);
          canvas.restore();
        }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
