import {
  Canvas,
  Circle,
  Group,
  Image as SkiaImage,
  LinearGradient,
  RadialGradient,
  Rect,
  useImage,
  vec,
  type SkImage,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import type { ArticleKey } from './content';
import { DEPTH, LAYER_DEPTHS, layerShift } from './motion';
import { colors } from './theme';

/** Everything the actor needs for one frame, in screen points. */
export type ActorFrame = {
  visible: number;
  x: number;
  y: number;
  w: number;
  h: number;
  r: number;
  scale: number;
  tiltX: number;
  tiltY: number;
  lift: number;
  sep: number;
};

export const LAYER_FILES: Record<ArticleKey, readonly [number, number, number]> = {
  morning: [
    require('./assets/ForYouInsights-morning-L0.jpg'),
    require('./assets/ForYouInsights-morning-L1.webp'),
    require('./assets/ForYouInsights-morning-L2.webp'),
  ],
  food: [
    require('./assets/ForYouInsights-food-L0.jpg'),
    require('./assets/ForYouInsights-food-L1.webp'),
    require('./assets/ForYouInsights-food-L2.webp'),
  ],
  run: [
    require('./assets/ForYouInsights-run-L0.jpg'),
    require('./assets/ForYouInsights-run-L1.webp'),
    require('./assets/ForYouInsights-run-L2.webp'),
  ],
};

/** Decodes every layer once, up front, so a tap never opens onto an empty card. */
export function useLayerImages(): Record<ArticleKey, (SkImage | null)[]> {
  const morning = [useImage(LAYER_FILES.morning[0]), useImage(LAYER_FILES.morning[1]), useImage(LAYER_FILES.morning[2])];
  const food = [useImage(LAYER_FILES.food[0]), useImage(LAYER_FILES.food[1]), useImage(LAYER_FILES.food[2])];
  const run = [useImage(LAYER_FILES.run[0]), useImage(LAYER_FILES.run[1]), useImage(LAYER_FILES.run[2])];
  return { morning, food, run };
}

const DEG = Math.PI / 180;

/**
 * The tapped article's illustration as a stack of depth layers on one Skia canvas: a shadow that
 * grows with the lift, the card tilted in perspective, each layer shifted by its depth, and a sheen
 * that follows the tilt. With `sep` = 0 and no tilt it draws the row's flat picture.
 */
/** Static gradient geometry: a unit square, placed per frame by its group's transform. */
const SHADOW_CENTER = vec(0, 0);
const SHADOW_COLORS = [colors.shadow, colors.shadow, 'rgba(27,21,48,0)'];
const SHADOW_POSITIONS = [0, 0.55, 1];
const SHEEN_START = vec(0, 0);
const SHEEN_END = vec(1, 1);
const SHEEN_COLORS = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)'];
const SHEEN_POSITIONS = [0.3, 0.5, 0.7];

/**
 * The tapped article's illustration as a stack of depth layers on one Skia canvas: a shadow that
 * grows with the lift, the card tilted in perspective, each layer shifted by its depth, and a sheen
 * that follows the tilt. With `sep` = 0 and no tilt it draws the row's flat picture.
 * Nothing Skia-side is allocated per frame: every animated prop is a number or a plain transform list.
 */
export function DepthActor({ images, actorIndex, warm, actor }: {
  images: Record<ArticleKey, (SkImage | null)[]>; actorIndex: SharedValue<number>; warm: SharedValue<number>; actor: SharedValue<ActorFrame>;
}) {
  const opacity = useDerivedValue(() => actor.get().visible);
  // A plain {rect, rx, ry} value (Skia reads it natively), not an SkRRect host object per frame.
  const clip = useDerivedValue(() => {
    const a = actor.get();
    return { rect: { x: a.x, y: a.y, width: a.w, height: a.h }, rx: a.r, ry: a.r };
  });
  const transform = useDerivedValue(() => {
    const a = actor.get();
    const cx = a.x + a.w / 2;
    const cy = a.y + a.h / 2;
    return [
      { translateX: cx },
      { translateY: cy },
      { perspective: DEPTH.perspective },
      { rotateX: a.tiltX * DEG },
      { rotateY: a.tiltY * DEG },
      { scale: a.scale },
      { translateX: -cx },
      { translateY: -cy },
    ];
  });
  // Soft contact shadow: a radial gradient in a unit circle, placed and stretched per frame by its
  // group's transform (no blur image filter, nothing allocated per frame).
  const shadowTransform = useDerivedValue(() => {
    const a = actor.get();
    const spread = 1 + 0.25 * a.lift;
    return [
      { translateX: a.x + a.w / 2 },
      { translateY: a.y + a.h / 2 + DEPTH.shadowOffsetMax * a.lift },
      { scaleX: (a.w / 2) * spread },
      { scaleY: (a.h / 2) * spread },
    ];
  });
  const shadowOpacity = useDerivedValue(() => DEPTH.shadowOpacity * actor.get().lift);
  const sheenTransform = useDerivedValue(() => {
    const a = actor.get();
    // The band's centre slides against the tilt; the unit square is stretched over the card.
    return [
      { translateX: a.x - a.w * (a.tiltY / 30) },
      { translateY: a.y },
      { scaleX: a.w },
      { scaleY: a.h },
    ];
  });
  const sheenOpacity = useDerivedValue(() => {
    const a = actor.get();
    return DEPTH.sheenOpacity * Math.min(1, (Math.abs(a.tiltY) / DEPTH.driftTiltY) * 0.7 + a.lift * 0.6) * a.sep;
  });

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Group opacity={opacity}>
        <Group transform={shadowTransform} opacity={shadowOpacity}>
          <Circle cx={0} cy={0} r={1}>
            <RadialGradient c={SHADOW_CENTER} r={1} colors={SHADOW_COLORS} positions={SHADOW_POSITIONS} />
          </Circle>
        </Group>
        <Group transform={transform}>
          <Group clip={clip}>
            {ARTICLE_KEYS.map((key, articleIndex) => (
              <ArticleLayers key={key} articleIndex={articleIndex} images={images[key]} depths={LAYER_DEPTHS[key]} actorIndex={actorIndex} warm={warm} actor={actor} />
            ))}
            <Group transform={sheenTransform} opacity={sheenOpacity}>
              <Rect x={-0.5} y={0} width={2} height={1}>
                <LinearGradient start={SHEEN_START} end={SHEEN_END} colors={SHEEN_COLORS} positions={SHEEN_POSITIONS} />
              </Rect>
            </Group>
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

const ARTICLE_KEYS: readonly ArticleKey[] = ['morning', 'food', 'run'];

/** Every article's stack stays in the canvas; only the flying one is opaque, so a tap rebuilds nothing. */
function ArticleLayers({ articleIndex, images, depths, actorIndex, warm, actor }: {
  articleIndex: number; images: (SkImage | null)[]; depths: readonly number[];
  actorIndex: SharedValue<number>; warm: SharedValue<number>; actor: SharedValue<ActorFrame>;
}) {
  // While warming, the other stacks draw at 0.4 % (below one 8-bit step) so every texture is in use.
  const opacity = useDerivedValue(() => (actorIndex.get() === articleIndex ? 1 : warm.get() === 1 ? 0.004 : 0));
  return (
    <Group opacity={opacity}>
      {depths.map((depth, index) => (
        <Layer key={index} image={images[index]} depth={depth} actor={actor} />
      ))}
    </Group>
  );
}

/**
 * Draws every layer once at 1 px off screen, so their GPU textures are uploaded at mount and the
 * first flight does not stall on the upload.
 */
export function WarmLayers({ images }: { images: Record<ArticleKey, (SkImage | null)[]> }) {
  const all = ARTICLE_KEYS.flatMap((key) => images[key]);
  return (
    <Canvas style={styles.warm} pointerEvents="none">
      {all.map((image, index) => (image ? <SkiaImage key={index} image={image} x={0} y={0} width={1} height={1} fit="fill" /> : null))}
    </Canvas>
  );
}

const styles = StyleSheet.create({ warm: { position: 'absolute', left: -8, top: -8, width: 2, height: 2 } });

function Layer({ image, depth, actor }: { image: SkImage | null; depth: number; actor: SharedValue<ActorFrame> }) {
  const box = useDerivedValue(() => {
    const a = actor.get();
    const side = Math.max(a.w, a.h) * (1 + DEPTH.overscan * a.sep);
    const shift = layerShift(depth, a.sep, a.tiltX, a.tiltY, a.w);
    const s = side * shift.scale;
    return { x: a.x + a.w / 2 - s / 2 + shift.dx, y: a.y + a.h / 2 - s / 2 + shift.dy, s };
  });
  const x = useDerivedValue(() => box.get().x);
  const y = useDerivedValue(() => box.get().y);
  const size = useDerivedValue(() => box.get().s);
  if (!image) return null;
  return <SkiaImage image={image} x={x} y={y} width={size} height={size} fit="fill" />;
}
