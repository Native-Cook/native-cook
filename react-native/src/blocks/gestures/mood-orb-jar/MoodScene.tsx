/**
 * Canvas: page, picker card and its orbs, the glass jar (back, orbs, front reflections) and its hinged lid.
 * The jar, lid and orbs are images generated from the reference; only the card and shadows are drawn. The replica
 * draws the source's jar, pile and caption traced from the frame (pile-plate.png) and the source's own orbs.
 */
import {
  BlurMask, Circle, Group, Image, LinearGradient, Path, Rect, RoundedRect, Skia, vec,
  type SkImage, type Transforms3d,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import { PILE_N, POOL, type Geometry } from './jar-sim';
import { JAR_KNOCK, RIPPLE, THROW, TRAIL } from './motion';
import { COLORS, LAYOUT, MOODS, REPLICA, REPLICA_PLATE } from './theme';

export type MoodValues = {
  orbs: SharedValue<number[]>; // per orb: x, y, spin, scale, opacity, mood
  slots: SharedValue<number[]>; // picker orb scales
  lid: DerivedValue<Transforms3d>;
  ghost: SharedValue<number[]>;
  ripple: SharedValue<number[]>;
  aim: SharedValue<number[]>;
  tilt: SharedValue<number>;
  cut: SharedValue<number>; // replica: 0 before the source's cut, 1 after
};

export type MoodImages = {
  jar: SkImage | null;
  front: SkImage | null;
  lid: SkImage | null;
  orbs: (SkImage | null)[];
  plate: SkImage | null; // replica: the source's jar and resting pile, traced from the frame
  plateB: SkImage | null; // ...and after its cut to the fuller jar
  jarPlate: SkImage | null; // shipped: the source's jar with its pile taken out
  pileAtlas: SkImage | null; // shipped: the source's 18 resting orbs, 144 px each, in REPLICA_PILE's order
};

type Props = { g: Geometry; v: MoodValues; images: MoodImages };

export function MoodScene({ g, v, images }: Props) {
  const { w, h, s } = g;
  const plateO = useDerivedValue(() => 1 - v.cut.value);
  const rippleC = useDerivedValue(() => vec(v.ripple.value[0], v.ripple.value[1]));
  const rippleR = useDerivedValue(() => (LAYOUT.orb + RIPPLE.radius * v.ripple.value[2]) * s);
  const rippleO = useDerivedValue(() => v.ripple.value[4] * (1 - v.ripple.value[2]) * 0.8);
  const jarShake = useDerivedValue<Transforms3d>(() => {
    const on = v.ripple.value[4] ?? 0;
    const t = (v.ripple.value[2] ?? 1) * RIPPLE.duration;
    return [{ translateX: on > 0 ? JAR_KNOCK.amp * s * Math.exp(-JAR_KNOCK.decay * t) * Math.sin(2 * Math.PI * JAR_KNOCK.hz * t) : 0 }];
  });
  const rippleColor = useDerivedValue(() => MOODS[v.ripple.value[3]]?.base ?? '#FFFFFF');
  const aimPath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const a = v.aim.value;
    for (let i = 0; i + 1 < a.length; i += 2) b.addCircle(a[i], a[i + 1], (2.2 - i * 0.04) * s);
    return b.build();
  });
  const cardY = g.top + LAYOUT.pickerTop * s;
  const cardX = LAYOUT.side * s;
  const jarX = g.cx - (LAYOUT.jarImageW * s) / 2;
  const jarY = g.mouthY - LAYOUT.jarImageMouthY * s;
  const jw = LAYOUT.jarImageW * s;
  const jh = LAYOUT.jarImageH * s;

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={[COLORS.bg, COLORS.bgBottom]} />
      </Rect>

      {/* Picker card (flat UI in the reference) with its five orbs. */}
      <RoundedRect x={cardX} y={cardY + 4 * s} width={w - cardX * 2} height={LAYOUT.pickerHeight * s} r={LAYOUT.cardRadius * s} color={COLORS.cardShadow}>
        <BlurMask blur={10} style="normal" />
      </RoundedRect>
      <RoundedRect x={cardX} y={cardY} width={w - cardX * 2} height={LAYOUT.pickerHeight * s} r={LAYOUT.cardRadius * s} color={COLORS.card} />
      {MOODS.map((m, i) => (
        <SlotOrb key={m.name} index={i} x={g.slotX[i]} y={g.slotY} r={LAYOUT.pickerOrb * s} slots={v.slots} image={images.orbs[i]} />
      ))}

      {/* A landing knocks the jar: it rocks a pixel or two and settles (glass has mass). */}
      <Group transform={jarShake}>
        {/* Jar: the glass as it looks empty (the source's, its pile taken out), then the orbs. */}
        {REPLICA ? (
          <Image image={images.jar} x={jarX} y={jarY} width={jw} height={jh} fit="fill" />
        ) : (
          <Image image={images.jarPlate} x={0} y={(REPLICA_PLATE.y / 1206) * g.w} width={g.w} height={(REPLICA_PLATE.h / 1206) * g.w} fit="fill" />
        )}
        {/* The replica draws the source's resting pile as the frame shows it; only orbs added later are sprites. */}
        {REPLICA ? (
          <>
            <Image image={images.plate} x={0} y={(REPLICA_PLATE.y / 1206) * g.w} width={g.w} height={(REPLICA_PLATE.h / 1206) * g.w} fit="fill" opacity={plateO} />
            <Image image={images.plateB} x={0} y={(REPLICA_PLATE.y / 1206) * g.w} width={g.w} height={(REPLICA_PLATE.h / 1206) * g.w} fit="fill" opacity={v.cut} />
          </>
        ) : null}
        {Array.from({ length: POOL }, (_, i) =>
          REPLICA && i < PILE_N ? null : (
            <PoolOrb key={i} index={i} r={LAYOUT.orb * s} orbs={v.orbs} images={images.orbs} atlas={i < PILE_N ? images.pileAtlas : null} />
          ),
        )}
        {/* Landing ripple in the mood's colour. */}
        <Circle c={rippleC} r={rippleR} style="stroke" strokeWidth={3 * s} color={rippleColor} opacity={rippleO} />
        {/* The glass's front highlights drift with phone tilt (refraction). */}
        {/* (The replica's plate carries the source's glass and the creator's "US?" caption, which never ships.) */}
        {/* Aim arc while an orb is held. */}
        <Path path={aimPath} color={COLORS.aim} />


        {/* Lid, hinged on its left edge. */}
        <Group transform={v.lid}>
          <Image image={images.lid} x={g.lidX} y={g.lidY} width={LAYOUT.lidWidth * s} height={LAYOUT.lidHeight * s} fit="fill" />
        </Group>
      </Group>
    </Group>
  );
}

function SlotOrb({ index, x, y, r, slots, image }: { index: number; x: number; y: number; r: number; slots: SharedValue<number[]>; image: SkImage | null }) {
  // Regrows from 0.9 with a fade, never from scale(0) (animate-expo never-ship).
  const t = useDerivedValue<Transforms3d>(() => [{ translateX: x }, { translateY: y }, { scale: 0.9 + 0.1 * (slots.value[index] ?? 1) }]);
  const o = useDerivedValue(() => slots.value[index] ?? 1);
  // The orb image is 144 px with a 132 px orb: draw it 12/11 of the orb's diameter.
  const d = r * 2 * (144 / 132);
  return (
    <Group transform={t} opacity={o}>
      <Image image={image} x={-d / 2} y={-d / 2} width={d} height={d} fit="fill" />
    </Group>
  );
}

/** One orb of the pool; it is reused, so its mood (the image) is read from the sim each frame. */
function PoolOrb({ index, r, orbs, images, atlas }: { index: number; r: number; orbs: SharedValue<number[]>; images: (SkImage | null)[]; atlas: SkImage | null }) {
  const k = index * 10;
  const t = useDerivedValue<Transforms3d>(() => {
    const o = orbs.value;
    const q = o[k + 6] ?? 0; // soft-body squash: wider and shorter, or the spring's stretch
    return [
      { translateX: o[k] ?? -100 }, { translateY: o[k + 1] ?? -100 }, { translateY: q * r }, { rotate: o[k + 2] ?? 0 },
      { scale: o[k + 3] ?? 1 }, { scaleX: 1 + q }, { scaleY: 1 - q },
    ];
  });
  const opacity = useDerivedValue(() => orbs.value[k + 4] ?? 0);

  const image = useDerivedValue(() => images[Math.max(0, Math.min(MOODS.length - 1, orbs.value[k + 5] ?? 0))] ?? null);
  const d = r * 2 * (144 / 132);
  return (
    <>
      {/* Motion trail while flying: copies where the orb was a few hundredths of a second ago. */}
      {TRAIL.alpha.map((a, n) => <TrailCopy key={n} orbs={orbs} k={k} tau={(n + 1) * TRAIL.gap} alpha={a} image={image} d={d} />)}
      <Group transform={t} opacity={opacity}>
        {atlas ? (
          // One of the source's own resting orbs (its face's turn and the scene's light are in the image).
          <Group clip={{ x: -d / 2, y: -d / 2, width: d, height: d }}>
            <Image image={atlas} x={-d / 2 - index * d} y={-d / 2} width={d * PILE_N} height={d} fit="fill" />
          </Group>
        ) : (
          <Image image={image} x={-d / 2} y={-d / 2} width={d} height={d} fit="fill" />
        )}
      </Group>
    </>
  );
}

function TrailCopy({ orbs, k, tau, alpha, image, d }: {
  orbs: SharedValue<number[]>; k: number; tau: number; alpha: number; image: DerivedValue<SkImage | null>; d: number;
}) {
  const t = useDerivedValue<Transforms3d>(() => {
    const o = orbs.value;
    const g = THROW.gravity * (d / 2 / LAYOUT.orb) / (144 / 132);
    return [
      { translateX: (o[k] ?? 0) - (o[k + 7] ?? 0) * tau },
      { translateY: (o[k + 1] ?? 0) - (o[k + 8] ?? 0) * tau + 0.5 * g * tau * tau },
      { scale: 1 - tau * 3 },
    ];
  });
  const op = useDerivedValue(() => ((orbs.value[k + 9] ?? 0) === 1 ? alpha : 0));
  return (
    <Group transform={t} opacity={op}>
      <Image image={image} x={-d / 2} y={-d / 2} width={d} height={d} fit="fill" />
    </Group>
  );
}
