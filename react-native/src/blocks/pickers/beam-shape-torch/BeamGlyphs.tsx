// Beam Control — the glyph under the lens, as a prop. A glyph draws in design pt inside its own 28 × 79 box (the lens is
// 18 pt above the box's top edge, on its vertical axis) with Skia nodes, and reads two shared values: `lit` 0 … 1 (0 =
// off, 45 % tone) and `hot` 0 … 1 (the lens heat: the glyph glows in its own beam's light). The default is the pin's
// torch; SpotGlyph is a stage spot on a stand, the same size.
import { type ReactNode } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';
import { BlurMask, Group, LinearGradient, Path, Skia, vec } from '@shopify/react-native-skia';

import { buildGlyphBody, buildGlyphCap } from './bst-geometry';
import { ref } from './bst-theme';

export type GlyphState = { lit: SharedValue<number>; hot: SharedValue<number> };
export type RenderGlyph = (p: GlyphState) => ReactNode;

const C = ref.color;
const TORCH_CAP = buildGlyphCap();
const TORCH_BODY = buildGlyphBody();

/** The pin's torch: a cap, a gap, a head that tapers into the handle. */
export function TorchGlyph({ lit, hot }: GlyphState) {
  const halo = useDerivedValue(() => 0.6 * Math.min(Math.max(hot.get(), 0), 1));
  return (
    <>
      {/* the torch glows in its own beam's light; off, it falls to 45 % */}
      <Group opacity={halo}>
        <Path path={TORCH_BODY} color={C.glyphLow}>
          <BlurMask blur={10} style="normal" />
        </Path>
        <Path path={TORCH_CAP} color={C.glyphTop}>
          <BlurMask blur={8} style="normal" />
        </Path>
      </Group>
      <Path path={TORCH_CAP} color={C.glyphOff} />
      <Path path={TORCH_BODY} color={C.glyphOff} />
      <Group opacity={lit}>
        <Path path={TORCH_CAP} color={C.glyphTop} />
        <Path path={TORCH_BODY}>
          <LinearGradient start={vec(0, 11)} end={vec(0, ref.glyph.h)} colors={[C.glyphTop, C.glyphLow]} />
        </Path>
      </Group>
    </>
  );
}

function buildSpot() {
  const g = ref.glyph;
  const lens = Skia.PathBuilder.Make(); // the lens ring: a bar across the head
  lens.moveTo(0, 7);
  lens.lineTo(0, 2.6);
  lens.quadTo(0, 0, 2.6, 0);
  lens.lineTo(g.w - 2.6, 0);
  lens.quadTo(g.w, 0, g.w, 2.6);
  lens.lineTo(g.w, 7);
  lens.close();
  const head = Skia.PathBuilder.Make(); // the barrel, narrowing away from the lens
  head.moveTo(0.4, 10);
  head.lineTo(g.w - 0.4, 10);
  head.lineTo(g.w - 4.6, 36);
  head.lineTo(4.6, 36);
  head.close();
  const stem = Skia.PathBuilder.Make(); // the stand: a stem and a foot
  stem.moveTo(11, 38.5);
  stem.lineTo(17, 38.5);
  stem.lineTo(17, 70);
  stem.lineTo(11, 70);
  stem.close();
  stem.moveTo(5, 73);
  stem.quadTo(5, 70, 8, 70);
  stem.lineTo(20, 70);
  stem.quadTo(23, 70, 23, 73);
  stem.quadTo(23, 79, 17.5, 79);
  stem.lineTo(10.5, 79);
  stem.quadTo(5, 79, 5, 73);
  stem.close();
  return { lens: lens.build(), head: head.build(), stem: stem.build() };
}

const SPOT = buildSpot();

/** A stage spot: a lens ring, a tapered barrel, a stem and a foot. */
export function SpotGlyph({ lit, hot }: GlyphState) {
  const halo = useDerivedValue(() => 0.5 * Math.min(Math.max(hot.get(), 0), 1));
  return (
    <>
      <Group opacity={halo}>
        <Path path={SPOT.head} color={C.glyphLow}>
          <BlurMask blur={10} style="normal" />
        </Path>
        <Path path={SPOT.lens} color={C.glyphTop}>
          <BlurMask blur={8} style="normal" />
        </Path>
      </Group>
      <Path path={SPOT.lens} color={C.glyphOff} />
      <Path path={SPOT.head} color={C.glyphOff} />
      <Path path={SPOT.stem} color={C.glyphOff} />
      <Group opacity={lit}>
        <Path path={SPOT.lens} color={C.glyphTop} />
        <Path path={SPOT.head}>
          <LinearGradient start={vec(0, 10)} end={vec(0, 36)} colors={[C.glyphTop, C.glyphLow]} />
        </Path>
        <Path path={SPOT.stem}>
          <LinearGradient start={vec(0, 38)} end={vec(0, ref.glyph.h)} colors={[C.glyphLow, C.glyphLow]} />
        </Path>
      </Group>
    </>
  );
}
