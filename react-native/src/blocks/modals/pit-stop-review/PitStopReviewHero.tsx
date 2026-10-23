import React, { memo, useMemo } from 'react';
import { BlurStyle, Canvas, Circle, ColorMatrix, Fill, Group, Image, Paint, RadialGradient, Rect, RoundedRect, Shader, Skia, TileMode, Blur, useImage, vec, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { carBlur, carStreaks, carX, disc, gantry, haze, lightAway, lightOn, packPuffs, pitch, speedLines, trail, worldBlur, worldX } from './motion';
import { SMOKE_SKSL } from './fx-shaders';
import { carGrade, colors, gantry as G, hero as H } from './theme';
import { WORLD_SKSL } from './world-shader';

/**
 * The hero, as a declarative Skia tree whose props are derived from the scene time (no Skia object is created per
 * frame by this code): the track shader, the Blender car (seven pitch frames crossfaded, a
 * cool grade, horizontal blur and light streaks while it races), the baked light trail screened over its rear
 * (the pin's light sits in front of the rear wing), additive brake-disc glows, the smoke shader and the
 * start-light gantry.
 */
const FRAMES = [
  require('./assets/car-pitch-m150.webp'),
  require('./assets/car-pitch-m075.webp'),
  require('./assets/car-pitch-000.webp'),
  require('./assets/car-pitch-p075.webp'),
  require('./assets/car-pitch-p150.webp'),
  require('./assets/car-pitch-p225.webp'),
  require('./assets/car-pitch-p300.webp'),
];
/** Pitch of FRAMES[0] and the step between frames (degrees). */
const PITCH_FIRST = -1.5, PITCH_STEP = 0.75;
const SHADOW = require('./assets/car-shadow.webp');
/** Wheel centres in the car canvas (design pt, the render's car.json) and how much of each disc the camera sees. */
const WHEELS: [number, number, number][] = [[217.2, 86.1, 1], [52.0, 86.1, 1], [210.9, 30.1, 0.35], [57.5, 31.9, 0.35]];
const DISC = { core: 7.5, bloom: 17, squash: 0.72 } as const;
/** Tyre contact points in the car canvas (design pt): x, y, seed, kind (0 front, 1 rear), side (+1 below, −1 above). */
const TYRES = [[215.2, 94.8, 11, 0, 1], [209.2, 39.5, 23, 0, -1], [54.1, 94.9, 37, 1, 1], [59.3, 41.3, 53, 1, -1]] as const;
const TYRES_ON_SCREEN = TYRES.map((t) => [H.car.x + t[0], H.car.y + t[1], t[2], t[3], t[4]] as const);
const WORLD = Skia.RuntimeEffect.Make(WORLD_SKSL);
/** The band the car can occupy (design pt): the car layer is clipped to it so its offscreen buffer stays small. */
const CAR_BAND = Skia.XYWHRect(0, H.car.y - 6, H.width, H.car.h + 12);
const SMOKE = Skia.RuntimeEffect.Make(SMOKE_SKSL);
const WARM_SMOKE = { puffs: [1, 1, 4, 0.5, ...new Array(31 * 4).fill(0)] };

/**
 * The pin's light trail at full strength (design pt, car at rest), calibrated on the pin's hero: red spill over
 * y 44–170 to x ≈ 150, a white-hot core over y 70–100 to x ≈ 110, fine streaks through it. Baked once (JS thread).
 */
const TRAIL_BOX = { x: -120, y: 0, w: 300, h: 200, scale: 2 } as const;
function paintTrail(canvas: SkCanvas) {
  const T = H.trail;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  for (const o of T.blooms) {
    p.setColor(Skia.Color(o.color));
    p.setAlphaf(o.alpha);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, o.blur, true));
    canvas.drawOval(Skia.XYWHRect(o.x, o.y, o.w, o.h), p);
  }
  const c = T.core;
  const core = Skia.Paint();
  core.setAntiAlias(true);
  core.setShader(Skia.Shader.MakeLinearGradient(vec(c.x, c.y + c.h / 2), vec(c.x + c.w, c.y + c.h / 2), [Skia.Color('rgba(255,255,255,1)'), Skia.Color('rgba(255,255,250,1)'), Skia.Color('rgba(255,236,170,0.95)'), Skia.Color('rgba(255,128,30,0.55)'), Skia.Color('rgba(255,60,20,0)')], [0, 0.45, 0.68, 0.86, 1], TileMode.Clamp));
  core.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, c.blur, true));
  canvas.drawOval(Skia.XYWHRect(c.x, c.y, c.w, c.h), core);
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStrokeCap(1);
  for (let i = 0; i < T.streaks; i++) {
    const y = T.streakTop + (((i * 37) % 100) / 100) * T.streakSpan;
    const x0 = -45 - ((i * 13) % 37);
    const x1 = 55 + ((i * 29) % 70);
    st.setStrokeWidth(i % 3 === 0 ? 1.6 : 0.8);
    st.setShader(Skia.Shader.MakeLinearGradient(vec(x0, y), vec(x1, y), [Skia.Color(i % 3 === 0 ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.5)'), Skia.Color('rgba(255,210,90,0.6)'), Skia.Color('rgba(255,90,30,0)')], [0, 0.6, 1], TileMode.Clamp));
    canvas.drawLine(x0, y, x1, y, st);
  }
}
function bakeTrail(): SkImage | null {
  const surface = Skia.Surface.Make(TRAIL_BOX.w * TRAIL_BOX.scale, TRAIL_BOX.h * TRAIL_BOX.scale);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(TRAIL_BOX.scale, TRAIL_BOX.scale);
  c.translate(-TRAIL_BOX.x, -TRAIL_BOX.y);
  paintTrail(c);
  surface.flush();
  return surface.makeImageSnapshot().makeNonTextureImage();
}

/** One lit start light: red bloom and a red lens with a white-hot centre (static gradients; its group fades it). */
function Lamp({ index, scene }: { index: number; scene: SharedValue<number> }) {
  const x = G.cx + (index - 2) * G.pitch;
  const on = useDerivedValue(() => lightOn(scene.get(), index) * (1 - lightAway(scene.get(), index)));
  return (
    <Group opacity={on}>
      <Circle cx={x} cy={G.cy} r={G.radius * 2.6}>
        <RadialGradient c={vec(x, G.cy)} r={G.radius * 2.6} colors={['rgba(255,48,32,0.55)', 'rgba(255,48,32,0)']} />
      </Circle>
      <Circle cx={x} cy={G.cy} r={G.radius}>
        <RadialGradient c={vec(x - G.radius * 0.15, G.cy - G.radius * 0.2)} r={G.radius} colors={[colors.lightCore, colors.light, '#C4170E']} positions={[0, 0.45, 1]} />
      </Circle>
    </Group>
  );
}

type Props = { width: number; scale: number; scene: SharedValue<number> };

export const PitStopReviewHero = memo(function PitStopReviewHero({ width, scale, scene }: Props) {
  const f0 = useImage(FRAMES[0]), f1 = useImage(FRAMES[1]), f2 = useImage(FRAMES[2]), f3 = useImage(FRAMES[3]), f4 = useImage(FRAMES[4]), f5 = useImage(FRAMES[5]), f6 = useImage(FRAMES[6]);
  const shadow = useImage(SHADOW);
  const trailImage = useMemo(() => bakeTrail(), []);
  const frames = useMemo(() => [f0, f1, f2, f3, f4, f5, f6], [f0, f1, f2, f3, f4, f5, f6]);

  const worldU = useDerivedValue(() => {
    const s = scene.get();
    return { res: [H.width, H.height], w0: [worldX(s), worldBlur(s), haze(s), speedLines(s)], w1: [H.car.x + carX(s) + 4, s, 1, 0] };
  });
  const carLeft = useDerivedValue(() => H.car.x + carX(scene.get()));
  const pose = useDerivedValue(() => Math.min(FRAMES.length - 1.0001, Math.max(0, (pitch(scene.get()) - PITCH_FIRST) / PITCH_STEP)));
  const imageA = useDerivedValue(() => frames[Math.floor(pose.get())] ?? null);
  const imageB = useDerivedValue(() => frames[Math.floor(pose.get()) + 1] ?? null);
  const mixB = useDerivedValue(() => pose.get() - Math.floor(pose.get()));
  const blur = useDerivedValue(() => ({ x: carBlur(scene.get()), y: 0.01 }));
  const streaks = useDerivedValue(() => carStreaks(scene.get()));
  const shadowOpacity = useDerivedValue(() => 0.9 * (1 - Math.min(1, carBlur(scene.get()) / 4)));
  const trailStrength = useDerivedValue(() => trail(scene.get()));
  const trailLen = useDerivedValue(() => Math.max(0.2, trailStrength.get()));
  /** Fades faster than it shortens, so a short trail never reads as a tall red smear. */
  const trailOpacity = useDerivedValue(() => trailStrength.get() * trailStrength.get());
  const trailX = useDerivedValue(() => H.car.x + carX(scene.get()) + (TRAIL_BOX.x - H.car.x) * trailLen.get());
  const trailW = useDerivedValue(() => TRAIL_BOX.w * trailLen.get());
  const discT = useDerivedValue(() => [{ translateX: carLeft.get() }, { translateY: H.car.y }]);
  const discOn = useDerivedValue(() => disc(scene.get()));
  const smoke = useDerivedValue(() => packPuffs(scene.get(), TYRES_ON_SCREEN));
  const smokeU = useDerivedValue(() => ({ puffs: smoke.get().data }));
  const smokeX = useDerivedValue(() => smoke.get().box[0]);
  const smokeY = useDerivedValue(() => smoke.get().box[1]);
  const smokeW = useDerivedValue(() => smoke.get().box[2] - smoke.get().box[0]);
  const smokeH = useDerivedValue(() => smoke.get().box[3] - smoke.get().box[1]);
  const gantryOn = useDerivedValue(() => gantry(scene.get()));
  const half = 2 * G.pitch + G.radius + G.housingPad;

  return (
    <>
    {/* Warm-up: draws every pitch frame, the shadow, the car layer's grade + blur and the smoke shader once on mount
        (2 pt, nearly transparent), so their textures and pipelines exist before the first loop needs them. */}
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width: 2, height: 2, opacity: 0.01 }} pointerEvents="none">
      <Group layer={<Paint><ColorMatrix matrix={carGrade} /><Blur blur={{ x: 2, y: 0.01 }} mode="decal" /></Paint>}>
        {frames.map((f, i) => (f ? <Image key={i} image={f} x={0} y={0} width={2} height={2} fit="fill" /> : null))}
        {shadow && <Image image={shadow} x={0} y={0} width={2} height={2} fit="fill" />}
      </Group>
      {SMOKE && <Rect x={0} y={0} width={1} height={1}><Shader source={SMOKE} uniforms={WARM_SMOKE} /></Rect>}
    </Canvas>
    <Canvas style={{ width, height: H.height * scale }} pointerEvents="none">
      <Group transform={[{ scale }]}>
        {WORLD && <Fill><Shader source={WORLD} uniforms={worldU} /></Fill>}
        {shadow && <Image image={shadow} x={carLeft} y={H.car.y} width={H.car.w} height={H.car.h} fit="fill" opacity={shadowOpacity} />}
        {/* One bounded layer for the car: grade and horizontal blur applied once to both pitch frames and their streaks. */}
        <Group clip={CAR_BAND} layer={<Paint><ColorMatrix matrix={carGrade} /><Blur blur={blur} mode="decal" /></Paint>}>
          <Image image={imageA} x={carLeft} y={H.car.y} width={H.car.w} height={H.car.h} fit="fill" />
          <Image image={imageB} x={carLeft} y={H.car.y} width={H.car.w} height={H.car.h} fit="fill" opacity={mixB} />
          {WORLD && <Rect x={carLeft} y={H.car.y} width={H.car.w} height={H.car.h} blendMode="srcATop" opacity={streaks}><Shader source={WORLD} uniforms={worldU} /></Rect>}
        </Group>
        {trailImage && <Image image={trailImage} x={trailX} y={TRAIL_BOX.y} width={trailW} height={TRAIL_BOX.h} fit="fill" opacity={trailOpacity} blendMode="screen" />}
        <Group transform={discT} opacity={discOn} blendMode="plus">
          {WHEELS.map(([x, y, k]) => (
            <Group key={`${x}`} transform={[{ translateX: x }, { translateY: y }, { scaleY: DISC.squash }]} opacity={k}>
              <Circle cx={0} cy={0} r={DISC.bloom} opacity={0.8}><RadialGradient c={vec(0, 0)} r={DISC.bloom} colors={[colors.discBloom, 'rgba(226,52,12,0)']} /></Circle>
              <Circle cx={0} cy={0} r={DISC.core}><RadialGradient c={vec(0, 0)} r={DISC.core} colors={['#FFE2A8', colors.discHot, 'rgba(255,106,26,0)']} positions={[0, 0.5, 1]} /></Circle>
            </Group>
          ))}
        </Group>
        {SMOKE && <Rect x={smokeX} y={smokeY} width={smokeW} height={smokeH}><Shader source={SMOKE} uniforms={smokeU} /></Rect>}
        <Group opacity={gantryOn}>
          <RoundedRect x={G.cx - half} y={G.cy - G.housingH / 2} width={half * 2} height={G.housingH} r={G.housingH / 2} color="rgba(12,11,13,0.92)" />
          <RoundedRect x={G.cx - half} y={G.cy - G.housingH / 2} width={half * 2} height={G.housingH} r={G.housingH / 2} color="rgba(255,255,255,0.16)" style="stroke" strokeWidth={0.6} />
          {[0, 1, 2, 3, 4].map((i) => <Circle key={i} cx={G.cx + (i - 2) * G.pitch} cy={G.cy} r={G.radius} color={colors.lightOff} />)}
          {[0, 1, 2, 3, 4].map((i) => <Lamp key={i} index={i} scene={scene} />)}
        </Group>
      </Group>
    </Canvas>
    </>
  );
});
