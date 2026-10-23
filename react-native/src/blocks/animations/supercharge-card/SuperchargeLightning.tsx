// Additive electrical passes. One path per weight feeds its three strokes (halo, body, core), and the
// embers are drawn as three heat buckets, so a frame builds at most eight paths however much is alive.
import { BlurMask, Group, Path, Skia } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import { LIGHTNING_PAINT, M } from './supercharge-constants';
import { cardTransform } from './supercharge-layout';
import type { SuperchargeValues } from './supercharge-types';

type WeightName = 'main' | 'branch' | 'twig' | 'ground';
const PASSES = [0, 1, 2];
const EMPTY = Skia.PathBuilder.Make().build();
// An ember cools from white through lemon to deep gold as its life (1 to 0) runs out.
const HEAT = [
  { from: 0.55, width: 1.4, opacity: 1, color: '#FFFFFF' },
  { from: 0.22, width: 1.0, opacity: 0.75, color: '#FFF27A' },
  { from: 0, width: 0.7, opacity: 0.4, color: '#FFC400' },
] as const;

function Weight({ values, weight }: { values: SuperchargeValues; weight: WeightName }) {
  const { lightning } = values;
  const paint = LIGHTNING_PAINT[weight === 'ground' ? 'branch' : weight];
  const fork = weight === 'branch' || weight === 'twig';
  const path = useDerivedValue(() => {
    const points = lightning.get()[weight];
    if (!points || points.length === 0) return EMPTY;
    const builder = Skia.PathBuilder.Make();
    for (let i = 0; i < points.length; i += 4) builder.moveTo(points[i], points[i + 1]).lineTo(points[i + 2], points[i + 3]);
    return builder.build();
  });
  const level = useDerivedValue(() => {
    const frame = lightning.get();
    return fork ? frame.forkLevel ?? frame.level : frame.level;
  });
  // Only the sky channel changes weight: its first pulse is the heaviest stroke of the loop.
  const impact = useDerivedValue(() => weight === 'main' && lightning.get().firstPulse === true);
  const halo = useDerivedValue(() => impact.get() ? LIGHTNING_PAINT.impact.widths[0] : paint.widths[0]);
  const body = useDerivedValue(() => impact.get() ? LIGHTNING_PAINT.impact.widths[1] : paint.widths[1]);
  const core = useDerivedValue(() => impact.get() ? LIGHTNING_PAINT.impact.widths[2] : paint.widths[2]);
  const widths = [halo, body, core];
  return <Group opacity={level}>
    {PASSES.map((pass) => <Path key={pass} path={path} style="stroke" strokeWidth={widths[pass]} blendMode="plus"
      opacity={paint.alpha[pass]} strokeCap="round" strokeJoin="round" color={LIGHTNING_PAINT.colors[pass]}>
      {paint.blurs[pass] > 0 && <BlurMask blur={paint.blurs[pass]} style="normal" />}
    </Path>)}
  </Group>;
}

/** Streaks of the embers whose life lies in one heat bucket (or, for the glow, above the coolest). */
function Embers({ values, bucket }: { values: SuperchargeValues; bucket: number }) {
  const { sparks } = values;
  const glow = bucket < 0;
  const from = glow ? HEAT[1].from : HEAT[bucket].from;
  const below = glow || bucket === 0 ? 2 : HEAT[bucket - 1].from;
  const path = useDerivedValue(() => {
    const points = sparks.get();
    if (points.length === 0) return EMPTY;
    const builder = Skia.PathBuilder.Make();
    for (let i = 0; i < points.length; i += 5) {
      const life = points[i + 4];
      if (life <= 0 || life < from || life >= below) continue;
      const speed = Math.max(1, Math.hypot(points[i + 2], points[i + 3]));
      const tail = Math.max(M.spark.minLength, speed * M.spark.streak) * life / speed;
      builder.moveTo(points[i], points[i + 1]).lineTo(points[i] - points[i + 2] * tail, points[i + 1] - points[i + 3] * tail);
    }
    return builder.build();
  });
  if (glow) {
    return <Path path={path} style="stroke" strokeWidth={3} strokeCap="round" blendMode="plus" opacity={0.5} color="#FFC400">
      <BlurMask blur={4} style="normal" />
    </Path>;
  }
  const heat = HEAT[bucket];
  return <Path path={path} style="stroke" strokeWidth={heat.width} strokeCap="round" blendMode="plus"
    opacity={heat.opacity} color={heat.color} />;
}

export function SuperchargeLightning({ values }: { values: SuperchargeValues }) {
  const { card, sink } = values;
  const transform = useDerivedValue(() => cardTransform(card.get()));
  const glass = useDerivedValue(() => [{ translateY: sink.get() }]);
  return <Group transform={transform}>
    <Group transform={glass}><Weight values={values} weight="main" /><Weight values={values} weight="branch" /><Weight values={values} weight="twig" /></Group>
    <Weight values={values} weight="ground" />
    <Embers values={values} bucket={-1} />
    <Embers values={values} bucket={0} />
    <Embers values={values} bucket={1} />
    <Embers values={values} bucket={2} />
  </Group>;
}
