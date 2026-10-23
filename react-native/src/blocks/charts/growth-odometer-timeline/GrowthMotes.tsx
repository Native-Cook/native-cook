import {
  Atlas,
  ColorMatrix,
  Skia,
  useRSXformBuffer,
  type SkImage,
  type SkRSXform
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';
import { heroEnvelope, smooth, type GrowthState } from './growth-sim';
import { type Metrics } from './growth-theme';
import { haloFieldAt } from './growth-halo';
export const MOTE_COUNT = 26;
const MOTE_TEXTURE_PX = 48;
const MOTE_BAND_PT = 120;
const MOTE_BASE_SIZE_PT = 1.2;
const MOTE_SIZE_RANGE_PT = 1.8;
const MOTE_MIN_SPEED_PT_PER_SECOND = 6;
const MOTE_SPEED_RANGE_PT_PER_SECOND = 8;
const MOTE_SEED_STRIDE = 0.61803398875;
const MOTE_VERTICAL_STRIDE_PT = 83.13;
const MOTE_HERO_PUSH_PT = 15;
const MOTE_HERO_DECAY_SECONDS = 0.55;
const MOTE_OPACITY = 0.46;
const MOTE_VISIBLE_FIELD = 0.25;
const MOTE_FULL_SIZE_FIELD = 0.6;
// A singular RSXform can suppress the entire Atlas. This footprint rasterizes to zero pixels.
const MOTE_INVISIBLE_SIZE_PT = 0.0001;
const MOTE_FADE_START_LEVEL = 0.2;
const MOTE_FADE_END_LEVEL = 0.7;
const SUBSTEPS_PER_SECOND = 240;
const WRAP_HEIGHTS = 10;
const SPRITES = Array.from({
  length: MOTE_COUNT
}, () => Skia.XYWHRect(0, 0, MOTE_TEXTURE_PX, MOTE_TEXTURE_PX));
// Tint once through a constant colour filter; alpha remains the sprite's soft mask.
export const MOTE_TINT = [211 / 255, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 120 / 255, 0, 0, 0, 0, 0, 1, 0];
export function moteOpacity(wheel: GrowthState) {
  'worklet';
  return wheel.reduced ? 0 : MOTE_OPACITY * smooth(MOTE_FADE_START_LEVEL, MOTE_FADE_END_LEVEL, wheel.halo);
}

export function moveMote(out: SkRSXform, index: number, wheel: GrowthState, layout: Metrics) {
  'worklet';
  const time = wheel.auto && !wheel.paused ? wheel.loopStep / SUBSTEPS_PER_SECOND : wheel.clock;
  const seed = index * MOTE_SEED_STRIDE % 1;
  const run = time * (MOTE_MIN_SPEED_PT_PER_SECOND + seed * MOTE_SPEED_RANGE_PT_PER_SECOND);
  const push = heroEnvelope(wheel.clock - wheel.heroStart, MOTE_HERO_DECAY_SECONDS) * MOTE_HERO_PUSH_PT;
  const x = layout.width - (seed * MOTE_BAND_PT + run + push) % MOTE_BAND_PT;
  const y = (index * MOTE_VERTICAL_STRIDE_PT - run - push + layout.height * WRAP_HEIGHTS) % layout.height;
  // Atlas has no reliable per-sprite alpha here: darkness shrinks the sprite below pixel visibility.
  const localField = haloFieldAt(x, y, wheel, layout);
  const lightScale = wheel.reduced || wheel.halo === 0
    ? 0
    : smooth(MOTE_VISIBLE_FIELD, MOTE_FULL_SIZE_FIELD, localField);
  const size = Math.max(
    MOTE_INVISIBLE_SIZE_PT,
    (MOTE_BASE_SIZE_PT + seed * MOTE_SIZE_RANGE_PT) * lightScale,
  );
  out.set(size / MOTE_TEXTURE_PX, 0, x - size / 2, y - size / 2);
}

export function GrowthMotes({
  image,
  state,
  m: layout
}: {
  image: SkImage;
  state: SharedValue<GrowthState>;
  m: Metrics;
}) {
  // Reuse the native transform buffer; no new arrays, colours or transforms per frame.
  const transforms = useRSXformBuffer(MOTE_COUNT, (out, index) => {
    'worklet';
    moveMote(out, index, state.get(), layout);
  });
  const opacity = useDerivedValue(() => moteOpacity(state.get()));
  // blendMode is the destination paint mode. No per-sprite colour blending.
  return <Atlas
    image={image}
    sprites={SPRITES}
    transforms={transforms}
    opacity={opacity}
    blendMode="plus"
  >
    <ColorMatrix matrix={MOTE_TINT} />
  </Atlas>;
}
