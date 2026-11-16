import { BeamShapeTorch } from './BeamShapeTorch';

export { BeamControl } from './BeamControl';
export type { AngleRange, BeamAnchor, BeamControlProps, BeamTheme, BeamValue } from './BeamControl';
export { SpotGlyph, TorchGlyph } from './BeamGlyphs';
export type { GlyphState, RenderGlyph } from './BeamGlyphs';
export { LOOP } from './bst-motion';

export default function BeamShapeTorchDemo() {
  return <BeamShapeTorch autoPlay />;
}
