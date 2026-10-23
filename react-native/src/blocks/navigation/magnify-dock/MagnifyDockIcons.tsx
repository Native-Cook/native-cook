import { BlurMask, Group, LinearGradient, Path, RadialGradient, Skia, type SkPath, vec } from '@shopify/react-native-skia';

import { DOCK_SQUIRCLE, type DockApp, type DockLayer, type DockPaint } from './MagnifyDockArt';

/** Paths parsed once and shared by every draw of the icons. */
const PATHS = new Map<string, SkPath>();

function pathOf(d: string) {
  let path = PATHS.get(d);
  if (!path) {
    path = Skia.Path.MakeFromSVGString(d) ?? Skia.Path.Make();
    PATHS.set(d, path);
  }
  return path;
}

function Shader({ paint }: { paint: DockPaint }) {
  const from = paint.from ?? [0, 0];
  const to = paint.to ?? [0, 0];
  if (paint.kind === 'linear') return <LinearGradient start={vec(from[0], from[1])} end={vec(to[0], to[1])} colors={paint.colors} positions={paint.stops} />;
  if (paint.kind === 'radial') return <RadialGradient c={vec(from[0], from[1])} r={to[0]} colors={paint.colors} positions={paint.stops} />;
  return null;
}

function Layer({ layer }: { layer: DockLayer }) {
  const path = pathOf(layer.d);
  const blur = layer.blur ? <BlurMask blur={layer.blur} style="normal" /> : null;
  return (
    <>
      {layer.fill && (
        <Path path={path} color={layer.fill.kind === 'solid' ? layer.fill.colors[0] : 'black'}>
          <Shader paint={layer.fill} />
          {blur}
        </Path>
      )}
      {layer.stroke && (
        <Path
          path={path}
          style="stroke"
          strokeWidth={layer.width ?? 1}
          strokeCap="round"
          strokeJoin="round"
          color={layer.stroke.kind === 'solid' ? layer.stroke.colors[0] : 'black'}
        >
          <Shader paint={layer.stroke} />
          {blur}
        </Path>
      )}
    </>
  );
}

const SQUIRCLE = pathOf(DOCK_SQUIRCLE);

/**
 * One app icon in its 100 × 100 box: a soft contact shadow, then its layers clipped to the
 * squircle. The caller scales the box to the icon's size, so it stays sharp at any magnification.
 */
export function DockIconArt({ app }: { app: DockApp }) {
  return (
    <Group>
      <Group transform={[{ translateY: 3 }]}>
        <Path path={SQUIRCLE} color="rgba(6,10,22,0.42)">
          <BlurMask blur={4} style="normal" />
        </Path>
      </Group>
      <Group clip={SQUIRCLE}>
        {app.layers.map((layer, i) => (
          <Layer key={i} layer={layer} />
        ))}
      </Group>
    </Group>
  );
}
