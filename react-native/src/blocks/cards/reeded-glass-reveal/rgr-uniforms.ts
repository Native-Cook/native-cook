// Reeded Glass Reveal — the one place that turns a Look into the glass shader's uniforms, so the app and the offline
// harness bind exactly the same numbers. Plain numbers and arrays only: this runs in a worklet every frame.
import type { Look } from './rgr-motion';
import { comp, RIB_SEEDS, RUN_RIBS } from './rgr-theme';

/** Per-layout constants (computed once with useMemo on the JS thread). */
export type GlassCtx = {
  /** image rect x, y, w, h (pt) */
  img: number[];
  /** the 23 flute edges (pt) */
  edges: number[];
  /** magnification centre (pt) */
  magC: number[];
  S: number;
  /** lens strength 0..1.2 (the `depth` prop) and glint strength 0..1 (the `glints` prop) */
  strength: number;
  glints: number;
};

export function glassUniforms(look: Look, c: GlassCtx, reduce: boolean) {
  'worklet';
  const g = comp.glass;
  return {
    uImg: c.img,
    uEdge: c.edges,
    uSeed: RIB_SEEDS,
    uDepth: look.depths,
    uMag: look.mean,
    uMagAmt: g.mag * (1 + look.breath),
    uMagC: c.magC,
    uS: c.S,
    uFlatMix: reduce ? Math.min(1, Math.max(0, 1 - look.mean)) : -1,
    uKappa: g.kappa * c.strength,
    uTau: g.tau,
    uSlice: g.slice * c.strength,
    uSpread: g.spread * c.strength,
    uSag: g.sag,
    uBlur: g.blur,
    uEdgeK: g.edgeK,
    uChroma: g.chroma,
    uGlintW: g.glintW,
    uSeam: g.seam,
    uLight: look.light,
    uSpec: reduce ? 0.6 * c.glints : c.glints,
    uRun: reduce ? -1 : look.run,
    uRunRib: RUN_RIBS as unknown as number[],
  };
}
