import { Canvas, Fill, FilterMode, ImageShader, MipmapMode, Shader, Skia, TileMode, useImage, type SkImage, type Uniforms } from '@shopify/react-native-skia';
import { memo, useMemo } from 'react';
import { type ImageSourcePropType } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { exchangePose, LOOP, signatureEnvelope, wrapPosition } from './HabitatMotion';
import { landEnvelope, lifePose, lifeUniforms } from './HabitatRig';
import { CREATURE_LIFE, NORMAL_BAKE } from './HabitatShaders';

/** Back light each habitat casts on the creature's silhouette (fire, water, grass). */
const RIM_LIGHT = [[1, 0.55, 0.22], [0.45, 0.85, 1], [0.75, 0.95, 0.45]] as const;
/** Margin around the art square, so a hop, a squash or a wing beat is never cut by the canvas. */
export const CREATURE_PAD = 0.14;
const ART = 512;

/**
 * A normal map baked once from the art's alpha: a wide blur gives the body its dome, a near one
 * keeps limbs and edges crisp. Any cut-out PNG gets a volume, with no extra asset.
 */
function bakeNormals(image: SkImage | null) {
  if (!image || !NORMAL_BAKE) return null;
  const blurred = (radius: number) => {
    const surface = Skia.Surface.MakeOffscreen(ART, ART);
    if (!surface) return null;
    const paint = Skia.Paint();
    paint.setImageFilter(Skia.ImageFilter.MakeBlur(radius, radius, TileMode.Decal, null));
    surface.getCanvas().drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, ART, ART), paint);
    surface.flush();
    return surface.makeImageSnapshot();
  };
  const wide = blurred(16);
  const near = blurred(5);
  const surface = Skia.Surface.MakeOffscreen(ART, ART);
  if (!wide || !near || !surface) return null;
  const sample = (source: SkImage, width = ART) => source.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.None, Skia.Matrix().scale(ART / width, ART / width));
  const paint = Skia.Paint();
  paint.setShader(NORMAL_BAKE.makeShaderWithChildren([7], [sample(wide), sample(near), sample(image, image.width())]));
  surface.getCanvas().drawPaint(paint);
  surface.flush();
  const baked = surface.makeImageSnapshot();
  return baked.makeNonTextureImage() ?? baked;
}

export type CreatureMotion = {
  position: SharedValue<number>;
  reduced: SharedValue<boolean>;
  time: SharedValue<number>;
  power: SharedValue<number>;
  powerIndex: SharedValue<number>;
  /** Seconds since the move began, past its end while its particles land; -1 when idle. */
  fx: SharedValue<number>;
  press: SharedValue<number>;
  look: SharedValue<number>;
  pet: SharedValue<number>;
  petClock: SharedValue<number>;
  landing: SharedValue<number>;
  landingIndex: SharedValue<number>;
};

/** One creature's place in the exchange and its pose this frame (shared with its signature effects). */
export function creaturePose(index: number, motion: CreatureMotion) {
  'worklet';
  const { position, reduced, time, power, powerIndex, fx, press, look, pet, petClock, landing, landingIndex } = motion;
  const reducedMotion = reduced.get();
  const exchange = exchangePose(index, position.get(), reducedMotion);
  const performing = powerIndex.get() === index;
  const pose = lifePose({
    t: time.get(), reduced: reducedMotion, role: exchange.role, f: exchange.f,
    press: press.get(), look: look.get(), pet: pet.get(), petClock: petClock.get(),
    land: !reducedMotion && landingIndex.get() === index ? landEnvelope(landing.get()) : 0,
    signature: performing ? power.get() : 0, fx: performing ? fx.get() : -1, index,
  });
  return { exchange, pose, performing };
}

export const HabitatCreature = memo(function HabitatCreature({ index, source, motion, frontPhase, light, size, left, top, width, heroTop, screenHeight, scale }: {
  index: number; source: ImageSourcePropType; motion: CreatureMotion; frontPhase: SharedValue<number>;
  /** Key light direction (x, y) and how much of the baked volume shows. */
  light: SharedValue<readonly [number, number, number]>;
  size: number; left: number; top: number; width: number; heroTop: number; screenHeight: number; scale: number;
}) {
  const image = useImage(source as number);
  const normals = useMemo(() => bakeNormals(image), [image]);
  const pad = Math.round(size * CREATURE_PAD);
  const view = size + pad * 2;
  const drift = 0.16 * width;
  const { position, reduced, power, time } = motion;
  // The exchange moves the whole canvas; the body's own pose is drawn inside it.
  const placement = useAnimatedStyle(() => {
    const exchange = exchangePose(index, position.get(), reduced.get());
    return { opacity: exchange.role < 0 ? 0 : 1, transform: [{ translateX: exchange.drift * drift }, { scale: exchange.scale }] };
  });
  const uniforms = useDerivedValue<Uniforms>(() => {
    const { exchange, pose, performing } = creaturePose(index, motion);
    return lifeUniforms({
      index, size, pad, left, top, heroTop, width, screenHeight, scale, exchange, driftPx: drift,
      phase: frontPhase.get(), element: (Math.floor(wrapPosition(position.get())) + 1) % 3, pose,
      light: normals ? light.get() : [0, 0, 0], tint: RIM_LIGHT[index],
      glow: performing ? signatureEnvelope(power.get()) : 0,
      seconds: time.get() * LOOP, reduced: reduced.get(),
    });
  });
  const rect = { x: 0, y: 0, width: size, height: size };
  return <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: left - pad, top: top - pad, width: view, height: view }, placement]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Canvas style={{ width: view, height: view }}>
      {image && CREATURE_LIFE && <Fill>
        <Shader source={CREATURE_LIFE} uniforms={uniforms}>
          <ImageShader image={image} fit="contain" rect={rect} tx="decal" ty="decal" />
          <ImageShader image={normals ?? image} fit="contain" rect={rect} tx="clamp" ty="clamp" />
        </Shader>
      </Fill>}
    </Canvas>
  </Animated.View>;
});
