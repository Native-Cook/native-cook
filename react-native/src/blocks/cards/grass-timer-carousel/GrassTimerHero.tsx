import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image as SkiaImage,
  LinearGradient,
  Path,
  Rect,
  RoundedRect,
  Shadow,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { GrassFrame } from './motion';

const GRASS = require('./assets/card-grass.jpg') as number;
const QUIET = require('./assets/card-quiet.jpg') as number;
const IMAGE_WIDTH = 696;
const IMAGE_HEIGHT = 981;

type Props = {
  frame: SharedValue<GrassFrame>;
  scale: number;
  safeTop: number;
  width: number;
  height: number;
};

function cardGeometry(frame: GrassFrame, index: number, scale: number, safeTop: number) {
  'worklet';
  const primary = frame.selectedCard === index;
  const source = primary
    ? {
        x: frame.aperture.x + frame.carouselOffset,
        y: frame.aperture.y,
        width: frame.aperture.width,
        height: frame.aperture.height,
        radius: frame.aperture.radius,
      }
    : { x: frame.neighborCardX, y: 0, width: 232, height: 327, radius: 36 };
  const rect = {
    x: source.x * scale,
    y: safeTop + (source.y - 62) * scale,
    width: source.width * scale,
    height: source.height * scale,
  };
  const fill = Math.max(rect.width / IMAGE_WIDTH, rect.height / IMAGE_HEIGHT);
  const imageWidth = IMAGE_WIDTH * fill;
  const imageHeight = IMAGE_HEIGHT * fill;
  return {
    primary,
    opacity: primary ? 1 : frame.secondCardOpacity,
    rect,
    radius: source.radius * scale,
    imageRect: {
      x: rect.x + (rect.width - imageWidth) / 2,
      y: rect.y + (rect.height - imageHeight) / 2 + (primary ? frame.imageParallax * scale : 0),
      width: imageWidth,
      height: imageHeight,
    },
    timerOpacity: primary ? frame.timerOpacity : 0,
  };
}

function CardSurface({
  index,
  frame,
  scale,
  safeTop,
  image,
}: {
  index: number;
  frame: SharedValue<GrassFrame>;
  scale: number;
  safeTop: number;
  image: ReturnType<typeof useImage>;
}) {
  const geometry = useDerivedValue(() => cardGeometry(frame.get(), index, scale, safeTop));
  const shape = useDerivedValue(() => {
    const value = geometry.get();
    return { rect: value.rect, rx: value.radius, ry: value.radius };
  });
  const imageRect = useDerivedValue(() => geometry.get().imageRect);
  const opacity = useDerivedValue(() => geometry.get().opacity);
  const primaryOpacity = useDerivedValue(() => geometry.get().primary ? 1 : 0);
  const shadeRect = useDerivedValue(() => geometry.get().rect);
  const shadeFrom = useDerivedValue(() => {
    const rect = geometry.get().rect;
    return vec(rect.x + rect.width / 2, rect.y);
  });
  const shadeTo = useDerivedValue(() => {
    const rect = geometry.get().rect;
    return vec(rect.x + rect.width / 2, rect.y + rect.height);
  });
  const shadeColors = useDerivedValue(() => {
    const timer = geometry.get().timerOpacity;
    return [`rgba(0,0,0,${0.16 * timer})`, `rgba(0,0,0,${0.12 + 0.16 * timer})`];
  });

  return (
    <Group opacity={opacity}>
      <Group opacity={primaryOpacity}>
        <RoundedRect rect={shape} color="#000000">
          <Shadow dx={0} dy={15 * scale} blur={22 * scale} color="rgba(0,0,0,0.26)" shadowOnly />
        </RoundedRect>
      </Group>
      <Group clip={shape}>
        <RoundedRect rect={shape} color="#26331C" />
        {image && <SkiaImage image={image} rect={imageRect} fit="fill" />}
        <Group opacity={primaryOpacity}>
          <Rect rect={shadeRect}>
            <LinearGradient start={shadeFrom} end={shadeTo} colors={shadeColors} />
          </Rect>
        </Group>
      </Group>
    </Group>
  );
}

export function GrassTimerHero({ frame, scale, safeTop, width, height }: Props) {
  const grass = useImage(GRASS);
  const quiet = useImage(QUIET);
  const ringPath = useDerivedValue(() => {
    const current = frame.get();
    const aperture = current.aperture;
    const x = (aperture.x + current.carouselOffset) * scale;
    const y = safeTop + (aperture.y - 62) * scale;
    const inset = 5 * scale;
    const path = Skia.Path.Make();
    path.addArc(
      { x: x + inset, y: y + inset, width: Math.max(0, aperture.width * scale - inset * 2), height: Math.max(0, aperture.height * scale - inset * 2) },
      -90,
      360,
    );
    return path;
  });
  const ringOpacity = useDerivedValue(() => frame.get().ringOpacity);
  const ringProgress = useDerivedValue(() => frame.get().ringProgress);
  const landingX = useDerivedValue(() => {
    const current = frame.get();
    return (current.aperture.x + current.carouselOffset + current.aperture.width / 2) * scale;
  });
  const landingY = useDerivedValue(() => {
    const current = frame.get();
    return safeTop + (current.aperture.y - 62) * scale + 6 * scale;
  });
  const landingOpacity = useDerivedValue(() => frame.get().landingOpacity);

  return (
    <Canvas style={[styles.canvas, { width, height }]} pointerEvents="none">
      <CardSurface index={0} frame={frame} scale={scale} safeTop={safeTop} image={grass} />
      <CardSurface index={1} frame={frame} scale={scale} safeTop={safeTop} image={quiet} />
      <Path path={ringPath} style="stroke" strokeWidth={2.5 * scale} color="rgba(223,255,138,0.42)" opacity={ringOpacity} />
      <Path path={ringPath} style="stroke" strokeWidth={2.5 * scale} strokeCap="round" color="#DFFF8A" start={0} end={ringProgress} opacity={ringOpacity} />
      <Circle cx={landingX} cy={landingY} r={6 * scale} color="rgba(223,255,138,0.74)" opacity={landingOpacity}>
        <BlurMask blur={5 * scale} style="normal" />
      </Circle>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});
