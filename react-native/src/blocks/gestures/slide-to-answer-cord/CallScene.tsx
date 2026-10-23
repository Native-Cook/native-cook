/** The whole canvas: horizon, grain, coiled cord, neon handset, ring pulse and the two call buttons. */
import {
  BackdropBlur, BlurMask, Circle, ColorMatrix, Fill, FractalNoise, Group, Image, LinearGradient, Path, RadialGradient, Rect, RoundedRect, Skia, vec,
  type SkImage, type SkPath, type SkRRect, type Transforms3d,
} from '@shopify/react-native-skia';
import { interpolateColor, useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import type { HandsetPaths } from './handset-paths';
import type { Geometry } from './call-sim';
import { CHEVRON, REPLICA_SWING } from './motion';
import { COLORS, CORD, GLASS, HANDSET, LAYOUT, REPLICA } from './theme';

export type CallValues = {
  coilBack: DerivedValue<SkPath>;
  coilFront: DerivedValue<SkPath>;
  handset: DerivedValue<Transforms3d>;
  face: DerivedValue<string>;
  faceDeep: DerivedValue<string>;
  ground: DerivedValue<string>;
  pulseR: SharedValue<number>;
  pulseO: SharedValue<number>;
  pulseX: SharedValue<number>;
  pulseY: SharedValue<number>;
  glowOn: SharedValue<number>; // neon strength: 0 when declined
  acceptGlow: SharedValue<number>;
  declineGlow: SharedValue<number>;
  conn: SharedValue<number>;
  off: SharedValue<number>;
  morph: SharedValue<number>; // Accept disc → control pill
  wash: SharedValue<number[]>; // x, y, progress
  chev: SharedValue<number>; // chevron phase 0..1, −1 hidden
  swing: SharedValue<number>; // time within the source's swing period
  src: SharedValue<number>; // how far the handset is on the source's swing (1 at rest)
  pressRed: SharedValue<number>; // press feedback on the discs: 1 → 0.95
  pressGreen: SharedValue<number>;
};

type Props = { g: Geometry; paths: HandsetPaths; v: CallValues; buttonsY: number; images: {
  pink: SkImage | null; green: SkImage | null; shell: SkImage | null; shellGreen: SkImage | null;
  srcA: SkImage | null; srcRest: SkImage | null; srcB: SkImage | null;
} };

// assets/handset-src-*.png: the traced takes in the handset's frame (pt, origin at the cord's end; 3 px per pt).
const SRC_BOX = { x: -100, y: -70, w: 260, h: 350 };
// The pink neon turned 160° in hue: the connected handset's green.
const GREEN = [-0.599, 1.142, 0.457, 0, 0, 0.462, 0.495, 0.043, 0, 0, 0.144, 1.631, -0.775, 0, 0, 0, 0, 0, 1, 0];
// The takes' times in the source's 4 s swing: [time, take] (0 a, 1 rest, 2 b), linear in between.
const SRC_KEYS = [[0, 0], [1.0, 1], [1.875, 2], [2.9, 1], [4, 0]] as const;
function swingWeight(t: number, take: number) {
  'worklet';
  for (let i = 0; i < SRC_KEYS.length - 1; i++) {
    const [t0, k0] = SRC_KEYS[i];
    const [t1, k1] = SRC_KEYS[i + 1];
    if (t >= t0 && t <= t1) {
      const u = (t - t0) / (t1 - t0);
      return (k0 === take ? 1 - u : 0) + (k1 === take ? u : 0);
    }
  }
  return take === 0 ? 1 : 0;
}

export function CallScene({ g, paths, v, buttonsY, images }: Props) {
  const { w, h, s } = g;
  // Replica: which traced take shows at this point of the swing (a: left end, rest: the middle, b: right end).
  // Off the source's swing (in the finger, in a call) the middle take stands for the handset.
  const srcAO = useDerivedValue(() => v.src.value * swingWeight(v.swing.value, 0));
  const srcRestO = useDerivedValue(() => v.src.value * swingWeight(v.swing.value, 1) + (1 - v.src.value));
  const srcBO = useDerivedValue(() => v.src.value * swingWeight(v.swing.value, 2));
  const half = (LAYOUT.button * s) / 2;
  const left = w / 2 - LAYOUT.buttonGap * s;
  const right = w / 2 + LAYOUT.buttonGap * s;

  const sky = useDerivedValue(() => [...COLORS.sky.slice(0, 4), v.ground.value, COLORS.sky[5]]);
  const lit = useDerivedValue(() => 1 - (1 - HANDSET.offOpacity) * v.off.value);
  const pinkO = useDerivedValue(() => (1 - v.conn.value) * lit.value);
  const handsetGreenO = useDerivedValue(() => v.conn.value * lit.value);
  const pinkA = useDerivedValue(() => srcAO.value * pinkO.value);
  const pinkR = useDerivedValue(() => srcRestO.value * pinkO.value);
  const pinkB = useDerivedValue(() => srcBO.value * pinkO.value);
  const greenA = useDerivedValue(() => srcAO.value * handsetGreenO.value);
  const greenR = useDerivedValue(() => srcRestO.value * handsetGreenO.value);
  const greenB = useDerivedValue(() => srcBO.value * handsetGreenO.value);
  // The red button slides to the centre and becomes End once the call is connected.
  const redX = useDerivedValue(() => left + (w / 2 - left) * v.conn.value);
  const redT = useDerivedValue<Transforms3d>(() => [{ translateX: redX.value }, { translateY: buttonsY }, { scale: v.pressRed.value }]);
  const redO = useDerivedValue(() => 1 - 0.55 * v.off.value);
  const declineHalo = useDerivedValue(() => 0.35 + 0.65 * v.declineGlow.value);
  const acceptRing = useDerivedValue(() => half + 6 + 8 * v.acceptGlow.value);
  const pulseC = useDerivedValue(() => vec(v.pulseX.value, v.pulseY.value));
  const redRect = useDerivedValue(() => ({ rect: { x: -half, y: -half, width: half * 2, height: half * 2 }, rx: half, ry: half }));
  // Accept disc (right) → control pill (above the End button), radius from round to pill.
  const pillRect = useDerivedValue(() => {
    const m = v.morph.value;
    const pw = (w - LAYOUT.side * 2 * s);
    const cx = right + (w / 2 - right) * m;
    const cy = buttonsY + (GLASS.pillY * s) * m;
    const ww = half * 2 + (pw - half * 2) * m;
    const hh = half * 2 + (GLASS.pillH * s - half * 2) * m;
    return { rect: { x: cx - ww / 2, y: cy - hh / 2, width: ww, height: hh }, rx: hh / 2, ry: hh / 2 };
  });
  const pillTint = useDerivedValue(() => interpolateColor(v.morph.value, [0, 1], [GLASS.accept, GLASS.neutral]) as string);
  const acceptO = useDerivedValue(() => 1 - 0.55 * v.off.value);
  const acceptPressT = useDerivedValue<Transforms3d>(() => [
    { translateX: right }, { translateY: buttonsY }, { scale: v.pressGreen.value }, { translateX: -right }, { translateY: -buttonsY },
  ]);
  const ringO = useDerivedValue(() => 0.45 * (1 - v.morph.value));
  const acceptHaloO = useDerivedValue(() => (0.35 + 0.65 * v.acceptGlow.value) * (1 - v.morph.value));
  const greenGlyphO = useDerivedValue(() => 1 - Math.min(1, v.morph.value * 3));
  const greenGlyphT = useDerivedValue<Transforms3d>(() => [{ translateX: right }, { translateY: buttonsY }, { scale: s }]);
  // Hue glow field behind the handset (pre-built gradient, never a live blur).
  const field = useDerivedValue(() => vec(v.pulseX.value, v.pulseY.value));
  const fieldColors = useDerivedValue(() => [v.face.value, 'rgba(0,0,0,0)']);
  // State wash: the new state's colour floods out from the handset.
  const washC = useDerivedValue(() => vec(v.wash.value[0], v.wash.value[1]));
  const washR = useDerivedValue(() => Math.max(1, v.wash.value[2] * Math.hypot(w, h)));
  const washO = useDerivedValue(() => (v.wash.value[2] > 0 && v.wash.value[2] < 1 ? 0.5 * (1 - v.wash.value[2]) : 0));
  const washColors = useDerivedValue(() => ['rgba(0,0,0,0)', v.face.value, 'rgba(0,0,0,0)']);
  // Lift chevrons drifting up above the handset while it rings.
  const chevrons = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    if (v.chev.value < 0) return b.build();
    for (let i = 0; i < 3; i++) {
      const u = (v.chev.value + i / 3) % 1;
      const y = v.pulseY.value - (HANDSET.comY + 26 + u * CHEVRON.rise * 2) * s;
      const x = v.pulseX.value;
      b.moveTo(x - 7 * s, y + 4 * s);
      b.lineTo(x, y - 2 * s);
      b.lineTo(x + 7 * s, y + 4 * s);
    }
    return b.build();
  });
  const chevO = useDerivedValue(() => (v.chev.value < 0 ? 0 : 0.7 * (1 - v.src.value)));
  // At rest the scene is the source's: the hue field and the chevrons come up once the handset leaves its swing.
  const fieldO = useDerivedValue(() => GLASS.fieldAlpha * (1 - v.src.value));

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={sky} positions={COLORS.skyStops} />
      </Rect>
      {REPLICA ? null : (
        <>
          <Rect x={0} y={0} width={w} height={h} opacity={fieldO}>
            <RadialGradient c={field} r={GLASS.field * s} colors={fieldColors} />
          </Rect>
          <Circle c={washC} r={washR} opacity={washO}>
            <RadialGradient c={washC} r={washR} colors={washColors} positions={[0, 0.85, 1]} />
          </Circle>
          <Path path={chevrons} style="stroke" strokeWidth={2.2 * s} strokeCap="round" strokeJoin="round" color="#FFFFFF" opacity={chevO} />
        </>
      )}
      <Rect x={0} y={0} width={w} height={h} opacity={COLORS.grain} blendMode="screen">
        <FractalNoise freqX={0.9} freqY={0.9} octaves={2} />
      </Rect>

      <Circle c={pulseC} r={v.pulseR} style="stroke" strokeWidth={2 * s} color={v.face} opacity={v.pulseO}>
        <BlurMask blur={3} style="solid" />
      </Circle>

      {/* The source's cord: a deep-blue tube with a pink rim on its right and a lit left side. */}
      <Group transform={[{ translateX: CORD.replicaRimDx * s }]}>
        <Path path={v.coilFront} style="stroke" strokeWidth={(CORD.replicaWidth - 1) * s} color={COLORS.replicaRim} strokeCap="round" />
      </Group>
      <Group transform={[{ translateX: -CORD.replicaRimDx * 0.8 * s }]}>
        <Path path={v.coilFront} style="stroke" strokeWidth={(CORD.replicaWidth - 1) * s} color={COLORS.replicaRimLeft} strokeCap="round" />
      </Group>
      <Path path={v.coilFront} style="stroke" strokeWidth={CORD.replicaWidth * s} color={COLORS.replicaCord} strokeCap="round" />

      {/* The source's handset, traced from its frames: light added over the backdrop, the three takes cross-faded
          along the swing (sharp at its ends, smeared by motion in the middle, as filmed); green once a call connects
          (the same light turned in hue), dimmed when the call is off. */}
      <Group transform={v.handset}>
        <Group transform={[{ translateX: -REPLICA_SWING.attach[0] }, { translateY: -REPLICA_SWING.attach[1] }]}>
          {[[images.srcA, pinkA], [images.srcRest, pinkR], [images.srcB, pinkB]].map(([im, o], i) => (
            <Image key={i} image={im as SkImage | null} x={SRC_BOX.x} y={SRC_BOX.y} width={SRC_BOX.w} height={SRC_BOX.h} fit="fill" blendMode="plus" opacity={o as typeof srcAO} />
          ))}
          {[[images.srcA, greenA], [images.srcRest, greenR], [images.srcB, greenB]].map(([im, o], i) => (
            <Image key={i} image={im as SkImage | null} x={SRC_BOX.x} y={SRC_BOX.y} width={SRC_BOX.w} height={SRC_BOX.h} fit="fill" blendMode="plus" opacity={o as typeof srcAO}>
              <ColorMatrix matrix={GREEN} />
            </Image>
          ))}
        </Group>
      </Group>

      {REPLICA ? null : (<>
      {/* Upgrade, Liquid Glass: frosted glass controls over the scene (fixed blur radius, never animated). */}
      <Group transform={redT} opacity={redO}>
        <Circle cx={0} cy={0} r={half + 6} color={COLORS.decline} opacity={declineHalo}>
          <BlurMask blur={16} style="normal" />
        </Circle>
        <GlassShape rect={redRect} tint={GLASS.decline} />
        <Group transform={[{ scale: s }]}>
          <Path path={paths.glyphDown} color={COLORS.glyph} />
        </Group>
      </Group>
      {/* Hero morph: the Accept disc grows into the in-call control pill (container transform). */}
      <Group opacity={acceptO} transform={acceptPressT}>
        <Circle cx={right} cy={buttonsY} r={acceptRing} style="stroke" strokeWidth={1.5} color={COLORS.accept} opacity={ringO} />
        <Circle cx={right} cy={buttonsY} r={half + 6} color={COLORS.accept} opacity={acceptHaloO}>
          <BlurMask blur={16} style="normal" />
        </Circle>
        <GlassShape rect={pillRect} tint={pillTint} />
        <Group transform={greenGlyphT} opacity={greenGlyphO}>
          <Path path={paths.glyph} color={COLORS.glyph} />
        </Group>
      </Group>
      </>)}
    </Group>
  );
}

/** A Liquid Glass surface: the scene behind blurred at a fixed radius, a tint, and a bright top edge. */
function GlassShape({ rect, tint }: { rect: DerivedValue<SkRRect>; tint: DerivedValue<string> | string }) {
  const clip = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    b.addRRect(rect.value);
    return b.build();
  });
  const top = useDerivedValue(() => vec(0, rect.value.rect.y));
  const bottom = useDerivedValue(() => vec(0, rect.value.rect.y + rect.value.rect.height));
  return (
    <Group>
      <BackdropBlur blur={GLASS.blur} clip={clip}>
        <Fill color={tint} />
      </BackdropBlur>
      <RoundedRect rect={rect} style="stroke" strokeWidth={1.2}>
        <LinearGradient start={top} end={bottom} colors={[GLASS.edgeTop, GLASS.edgeBottom]} />
      </RoundedRect>
    </Group>
  );
}
