/**
 * One theme's room, built on the accepted replica: the generated wall, the bulb sprites, the stile and rail light,
 * then the upgrade's additions (warm light rising in the glass, the pull cord). Drawn twice; the light one is masked
 * by the reveal in PullCordTheme.
 */
import {
  Blur, BlurMask, Circle, ColorMatrix, Group, Image, LinearGradient, Path, RadialGradient, Rect, RoundedRect, Skia, vec,
  type SkImage, type SkPath, type SkPoint, type Transforms3d,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import type { Geometry } from './cord-sim';
import { HERO } from './motion';
import { CORD, DARK, LIGHT, LIQUID, REPLICA_ART as A } from './theme';

export type RoomValues = {
  bulb: DerivedValue<Transforms3d>; // about the pivot, origin at the socket's top centre
  center: DerivedValue<SkPoint>; // glass centre on screen
  level: SharedValue<number>; // warm light in the glass, 0..1
  neon: SharedValue<number>; // the blue filament, 0..1
  side: DerivedValue<number>; // −1 (left) … 1 (right): which stile the bulb lights more
  rope: DerivedValue<SkPath>;
  bead: DerivedValue<Transforms3d>;
  commit: SharedValue<number>; // s since the last theme commit
  clock: SharedValue<number>;
  light: SharedValue<number>; // the committed theme, 0 dark / 1 light
};

/** Tungsten warming from ember to warm white after the commit, with two quick real-bulb dips. */
function emberAt(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t / HERO.ember));
}
function flickerAt(t: number) {
  'worklet';
  const f = HERO.flickers;
  for (let i = 0; i < f.length; i += 2) if (t >= f[i] && t < f[i + 1]) return 1 - HERO.flickerDepth;
  return 1;
}

// Seeded dust motes for the warm light cone: [x 0..1, y 0..1, speed, phase, size].
const MOTES = Array.from({ length: HERO.motes }, (_, i) => {
  const r = (k: number) => {
    const v = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
    return v - Math.floor(v);
  };
  return [r(1), r(2), 0.4 + r(3) * 0.8, r(4) * Math.PI * 2, 0.8 + r(5) * 1.6];
});

export type RoomImages = {
  wall: SkImage | null; wallLight: SkImage | null; lit: SkImage | null; dim: SkImage | null; warm: SkImage | null;
  lightAdd: SkImage | null; lightMul: SkImage | null;
};

type Props = { light: boolean; g: Geometry; v: RoomValues; images: RoomImages };

export function RoomScene({ light, g, v, images }: Props) {
  const { w, h, s } = g;
  const p = light ? LIGHT : DARK;
  const px = s / 3;
  const sw = A.spriteW * A.spriteScale * s;
  const sh = A.spriteH * A.spriteScale * s;
  const sx = -A.anchorX * A.spriteScale * s;
  const sy = -A.anchorY * A.spriteScale * s;
  const glassTop = (A.anchorY + 60) * A.spriteScale * s; // the glass starts under the socket
  const glassBottom = 705 * A.spriteScale * s - A.anchorY * A.spriteScale * s;

  // Neon hum in the dark room: 0.92–1 over 3 s (a divisor of the loop, so the loop stays seamless).
  const hum = useDerivedValue(() => HERO.hum.low + (1 - HERO.hum.low) * (0.5 + 0.5 * Math.sin((2 * Math.PI * v.clock.value) / HERO.hum.period)));
  const on = useDerivedValue(() => (light ? 0.35 + 0.65 * v.level.value : v.neon.value * hum.value * (1 - 0.7 * v.level.value)));
  // Light room: the tungsten warms up and flickers right after the commit.
  const fresh = useDerivedValue(() => (v.light.value === 1 ? v.commit.value : 99));
  const warmTint = useDerivedValue(() => {
    const e = emberAt(fresh.value);
    // Ember (~1800 K): red kept, green halved, blue nearly gone; then the identity.
    return [1, 0, 0, 0, 0, 0, 0.5 + 0.5 * e, 0, 0, 0, 0, 0, 0.15 + 0.85 * e, 0, 0, 0, 0, 0, 1, 0];
  });
  const warmFlicker = useDerivedValue(() => flickerAt(fresh.value));
  // The glass highlight slides across as the bulb swings.
  const specX = useDerivedValue(() => -HERO.spec * s * v.side.value - 42 * s);
  // Dust motes drifting in the warm cone under the bulb.
  const motes = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const t = v.clock.value;
    const c = v.center.value;
    for (let i = 0; i < MOTES.length; i++) {
      const m = MOTES[i];
      const y = (m[1] + t * 0.02 * m[2]) % 1;
      const spread = 40 + y * 140;
      const x = c.x + (m[0] - 0.5) * 2 * spread * s + Math.sin(t * m[2] + m[3]) * 8 * s;
      b.addCircle(x, c.y + (40 + y * 380) * s, m[4] * s);
    }
    return b.build();
  });
  const motesO = useDerivedValue(() => (light ? 0.55 * (0.4 + 0.6 * v.level.value) : 0));
  const shadowT = useDerivedValue<Transforms3d>(() => [{ translateX: HERO.shadow.dx * s }, { translateY: HERO.shadow.dy * s }]);
  const off = useDerivedValue(() => 1 - on.value);
  const mapT = useDerivedValue<Transforms3d>(() => [{ translateX: v.center.value.x - w }]);
  const warmGlow = useDerivedValue(() => (light ? 0.5 + 0.5 * v.level.value : 0.8 * v.level.value));
  const leftLit = useDerivedValue(() => on.value * (0.65 - 0.35 * v.side.value));
  const rightLit = useDerivedValue(() => on.value * (0.65 + 0.35 * v.side.value));
  const glassMid = (glassTop + glassBottom) / 2;
  const levelY = useDerivedValue(() => glassBottom - v.level.value * (glassBottom - glassTop));
  // dstIn only masks where the sprite draws: keep the liquid inside the sprite's bounds.
  // Only the glass fills: down to the sprite's foot, the warm sprite's faint glow let a 6 pt strip of liquid show
  // under the bulb at rest, with a hard top edge.
  const liquidH = useDerivedValue(() => Math.max(0, glassBottom - levelY.value));

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h} color={light ? LIGHT.wallTop : '#0D0E10'} />
      <Image image={light ? images.wallLight : images.wall} x={A.wallX * s} y={0} width={A.wallW * s} height={h} fit="fill">
        {light ? null : <ColorMatrix matrix={A.wallTint} />}
      </Image>

      {/* The bulb's light on the wall: blue in the dark room (the replica), warm in the light one. */}
      {light ? null : (
        <Group opacity={on} blendMode="screen">
          <Rect x={0} y={0} width={w} height={h}>
            <RadialGradient c={v.center} r={w * A.ambientR} colors={A.ambient} />
          </Rect>
          <Rect x={0} y={0} width={w} height={h}>
            <RadialGradient c={v.center} r={w * A.spillR} colors={A.spillFall} positions={A.spillStops} />
          </Rect>
          <Circle c={v.center} r={A.halo * s}>
            <RadialGradient c={v.center} r={A.halo * s} colors={[A.haloColor, 'rgba(31, 125, 255, 0)']} />
          </Circle>
          <Rect x={0} y={A.railY[0] * px} width={w} height={(A.railY[1] - A.railY[0]) * px} color={A.railColor} />
        </Group>
      )}
      {light ? null : <Stile x0={A.stileLeft[0] * px} x1={A.stileLeft[1] * px} px={px} opacity={leftLit} />}
      {light ? null : <Stile x0={A.stileRight[0] * px} x1={A.stileRight[1] * px} px={px} opacity={rightLit} />}
      <Group opacity={warmGlow}>
        <Circle c={v.center} r={A.halo * 1.4 * s}>
          <RadialGradient c={v.center} r={A.halo * 1.4 * s} colors={[LIQUID.spill, LIQUID.spillFar]} />
        </Circle>
      </Group>

      <Group transform={v.bulb}>
        <Rect x={-A.wireWidth / 2} y={g.pivotY - g.socketTop} width={A.wireWidth} height={g.socketTop - g.pivotY + 4} color={light ? LIGHT.wire : A.wireColor} />
        {light ? (
          <>
            {/* Its soft cast shadow on the light wall moves with the swing. */}
            <Group transform={shadowT} opacity={HERO.shadow.alpha}>
              <Image image={images.warm} x={sx} y={sy} width={sw} height={sh} fit="fill">
                <ColorMatrix matrix={SHADOW} />
                <Blur blur={HERO.shadow.blur * s} />
              </Image>
            </Group>
            <Group opacity={warmFlicker}>
              <Image image={images.warm} x={sx} y={sy} width={sw} height={sh} fit="fill">
                <ColorMatrix matrix={warmTint} />
              </Image>
            </Group>
          </>
        ) : (
          <>
            {/* The replica's sprites are on black: a screen blend keeps only their light. */}
            <Group opacity={off}>
              <Image image={images.dim} x={sx} y={sy} width={sw} height={sh} fit="fill" blendMode="screen" />
            </Group>
            <Group opacity={on}>
              <Image image={images.lit} x={sx} y={sy} width={sw} height={sh} fit="fill" blendMode="screen">
                <ColorMatrix matrix={A.interior} />
              </Image>
              <Image image={images.lit} x={sx} y={sy} width={sw} height={sh} fit="fill" blendMode="screen" opacity={A.bloomOpacity}>
                <ColorMatrix matrix={A.brightPass} />
                <Blur blur={A.bloom} />
              </Image>
            </Group>
          </>
        )}
        {/* Upgrade: warm light rising inside the glass like a liquid, shaped by the warm sprite's own alpha. */}
        <Group layer opacity={light ? LIQUID.lightAlpha : 1}>
          <Rect x={sx} y={levelY} width={sw} height={liquidH}>
            <LinearGradient start={vec(0, glassTop)} end={vec(0, glassBottom)} colors={[LIQUID.top, LIQUID.bottom]} />
          </Rect>
          <Image image={images.warm} x={sx} y={sy} width={sw} height={sh} fit="fill" blendMode="dstIn" />
        </Group>
        <Group transform={[{ translateY: glassMid }]}>
          <RoundedRect x={specX} y={-40 * s} width={12 * s} height={52 * s} r={6 * s} color="rgba(255,255,255,0.32)" blendMode="screen">
            <BlurMask blur={5} style="normal" />
          </RoundedRect>
        </Group>
      </Group>

      {light ? (
        <Group opacity={motesO} blendMode="screen">
          <Path path={motes} color="#FFF3D6">
            <BlurMask blur={1.2} style="normal" />
          </Path>
        </Group>
      ) : null}

      {light ? null : (
        // The source's baked light field around the bulb (as in the replica), moving with it.
        <Group transform={mapT} opacity={on}>
          <Image image={images.lightMul} x={0} y={0} width={w * 2} height={h} fit="fill" blendMode="multiply" />
          <Image image={images.lightAdd} x={0} y={0} width={w * 2} height={h} fit="fill" blendMode="plus" opacity={0.25} />
        </Group>
      )}

      {/* Upgrade: the pull cord and its brass pull. */}
      <Path path={v.rope} style="stroke" strokeWidth={CORD.width * s} color={p.cord} strokeCap="round" strokeJoin="round" />
      <Group transform={v.bead}>
        <RoundedRect x={-CORD.crimpWidth / 2} y={-1} width={CORD.crimpWidth} height={CORD.crimpHeight} r={1.5}>
          <LinearGradient start={vec(-CORD.crimpWidth / 2, 0)} end={vec(CORD.crimpWidth / 2, 0)} colors={p.metal} positions={p.metalStops} />
        </RoundedRect>
        <RoundedRect x={-CORD.beadWidth / 2} y={CORD.crimpHeight - 1} width={CORD.beadWidth} height={CORD.beadHeight} r={CORD.beadRadius}>
          <LinearGradient start={vec(-CORD.beadWidth / 2, 0)} end={vec(CORD.beadWidth / 2, 0)} colors={p.brass} positions={p.brassStops} />
        </RoundedRect>
        <RoundedRect x={-CORD.beadWidth / 2} y={CORD.crimpHeight - 1} width={CORD.beadWidth} height={CORD.beadHeight} r={CORD.beadRadius} style="stroke" strokeWidth={1} color={p.cordLit}>
          <BlurMask blur={0.6} style="normal" />
        </RoundedRect>
      </Group>
    </Group>
  );
}

function Stile({ x0, x1, px, opacity }: { x0: number; x1: number; px: number; opacity: DerivedValue<number> }) {
  return (
    <Group opacity={opacity} blendMode="screen">
      <Rect x={x0} y={A.stileGlow[0] * px} width={x1 - x0} height={(A.stileGlow[2] - A.stileGlow[0]) * px}>
        <LinearGradient start={vec(0, A.stileGlow[0] * px)} end={vec(0, A.stileGlow[2] * px)} colors={['rgba(36,104,210,0)', A.stileColor, 'rgba(36,104,210,0)']} />
        <Blur blur={3} />
      </Rect>
    </Group>
  );
}

// Any sprite → a black silhouette keeping its alpha.
const SHADOW = [0, 0, 0, 0, 0.12, 0, 0, 0, 0, 0.08, 0, 0, 0, 0, 0.04, 0, 0, 0, 1, 0];
