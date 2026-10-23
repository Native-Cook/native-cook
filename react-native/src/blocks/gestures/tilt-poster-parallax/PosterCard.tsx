/** The poster: plate, figure and gold title at three depths, a glare and a foil band, all from the tilt. */
import {
  Blur, Canvas, Circle, Group, Image, LinearGradient, matchFont, RadialGradient, RoundedRect, Skia, Text, vec,
  type SkImage,
} from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { EDGE, FLOAT, FLIP, HOLO, PARALLAX, TILT } from './motion';
import { BACK, COLOR, COPY, LAYOUT, SCRIPT_FONT, TYPE, UI_FONT } from './theme';

type Props = {
  width: number;
  height: number;
  plate: SkImage | null;
  figure: SkImage | null;
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  lift: SharedValue<number>;
  flip: SharedValue<number>;
  clock: SharedValue<number>;
  reduceMotion: boolean;
  margin: number;
  title: string;
  // Blender renders of the block's side faces (front edge on the left / top of each image).
  edgeSide: SkImage | null;
  edgeTop: SkImage | null;
};

const font = matchFont({ fontFamily: SCRIPT_FONT, fontSize: TYPE.posterTitle, fontWeight: '700' });
const backTitleFont = matchFont({ fontFamily: SCRIPT_FONT, fontSize: TYPE.backTitle, fontWeight: '700' });
const creditFont = matchFont({ fontFamily: UI_FONT, fontSize: TYPE.credit, fontWeight: '500' });
const creditStrongFont = matchFont({ fontFamily: UI_FONT, fontSize: TYPE.credit, fontWeight: '700' });

/**
 * The card turns in 3D inside Skia (not as a native view transform), so a tilted card never cuts into the
 * views around it. `margin` leaves room for the turn and the shadow.
 */
export function PosterCard({ width, height, plate, figure, tiltX, tiltY, lift, flip, clock, reduceMotion, margin, title, edgeSide, edgeTop }: Props) {
  const pw = width * LAYOUT.plateOverscan;
  const ph = height * LAYOUT.plateOverscan;
  const clip = Skia.RRectXY(Skia.XYWHRect(0, 0, width, height), LAYOUT.cardRadius, LAYOUT.cardRadius);
  const plateX = useDerivedValue(() => (width - pw) / 2 + tiltX.get() * PARALLAX.plate);
  const plateY = useDerivedValue(() => (height - ph) / 2 + tiltY.get() * PARALLAX.plate);
  const figureX = useDerivedValue(() => tiltX.get() * PARALLAX.figure);
  const figureY = useDerivedValue(() => tiltY.get() * PARALLAX.figure);
  const titleWidth = font.measureText(title).width;
  const titleBase = { x: (width - titleWidth) / 2, y: height * LAYOUT.titleY };
  const titleTransform = useDerivedValue(() => [
    { translateX: tiltX.get() * PARALLAX.title },
    { translateY: tiltY.get() * PARALLAX.title },
  ]);
  // The foil: a bright band that slides through the gold as the card turns.
  const foilStart = useDerivedValue(() => {
    const at = 0.5 + ((tiltX.get() + tiltY.get()) / 2) * PARALLAX.foil;
    return vec(titleBase.x + titleWidth * at - 40, titleBase.y - 40);
  });
  const foilEnd = useDerivedValue(() => {
    const at = 0.5 + ((tiltX.get() + tiltY.get()) / 2) * PARALLAX.foil;
    return vec(titleBase.x + titleWidth * at + 40, titleBase.y + 10);
  });
  // Holographic foil: a band of hues masked to the wordmark that slides (and so changes hue under each letter)
  // with the tilt angle; it brightens as the card turns. Shader position and opacity only.
  const holoAt = useDerivedValue(() => (tiltX.get() * HOLO.travelX + tiltY.get() * HOLO.travelY + flip.get() * HOLO.flipTravel) * titleWidth);
  const holoStart = useDerivedValue(() => vec(titleBase.x + holoAt.get(), titleBase.y - TYPE.posterTitle));
  const holoEnd = useDerivedValue(() => vec(titleBase.x + holoAt.get() + titleWidth * HOLO.period, titleBase.y + TYPE.posterTitle * 0.3));
  const holoOpacity = useDerivedValue(() => HOLO.rest + Math.min(HOLO.max - HOLO.rest, (Math.abs(tiltX.get()) + Math.abs(tiltY.get())) * HOLO.gain));
  // The glare swims against the tilt, like a lamp reflected in a glossy print.
  const glare = useDerivedValue(() => vec(width * (0.5 + tiltX.get() * PARALLAX.glare), height * (0.3 + tiltY.get() * PARALLAX.glare)));
  const glareOpacity = useDerivedValue(() => 0.18 + Math.min(0.32, (Math.abs(tiltX.get()) + Math.abs(tiltY.get())) * 0.9));

  // The flip adds half a turn about the vertical axis; under Reduce Motion the faces cross-fade instead.
  const angle = useDerivedValue(() => tiltX.get() + (reduceMotion ? 0 : flip.get() * Math.PI));
  // Every face of the block is placed with one 4 × 4 transform (Skia composes a transform array in 3D, but a
  // nested group would flatten it), turning about the card's centre. The block is EDGE.depth deep: the print at
  // +depth/2, its back at −depth/2, the side faces between them.
  const cx = margin + width / 2;
  const cy = margin + height / 2;
  const half = EDGE.depth / 2;
  const base = (a: number, r: number) => {
    'worklet';
    return [{ translateX: cx }, { translateY: cy }, { perspective: TILT.perspective }, { rotateX: r }, { rotateY: a }];
  };
  const depthAt = (z: number) => {
    'worklet';
    return { matrix: [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, z, 0, 0, 0, 1] as unknown as never };
  };
  const frontT = useDerivedValue(() => [...base(angle.get(), -tiltY.get()), depthAt(half), { translateX: -width / 2 }, { translateY: -height / 2 }]);
  const backT = useDerivedValue(() => [...base(angle.get(), -tiltY.get()), depthAt(-half), { translateX: -width / 2 }, { translateY: -height / 2 }]);
  const side = (a: number, r: number, x: number) => {
    'worklet';
    return [...base(a, r), { translateX: x }, { translateY: -height / 2 }, depthAt(half), { rotateY: Math.PI / 2 }];
  };
  const end = (a: number, r: number, y: number) => {
    'worklet';
    return [...base(a, r), { translateX: -width / 2 }, { translateY: y }, depthAt(half), { rotateX: -Math.PI / 2 }];
  };
  const rightT = useDerivedValue(() => side(angle.get(), -tiltY.get(), width / 2));
  const leftT = useDerivedValue(() => side(angle.get(), -tiltY.get(), -width / 2));
  const topT = useDerivedValue(() => end(angle.get(), -tiltY.get(), -height / 2));
  const bottomT = useDerivedValue(() => end(angle.get(), -tiltY.get(), height / 2));
  // A side shows once the block has turned past ~8° towards it (z of its outward normal, faded in).
  const show = (z: number) => {
    'worklet';
    return Math.min(1, Math.max(0, (z - Math.sin(EDGE.fromDeg)) / (Math.sin(EDGE.toDeg) - Math.sin(EDGE.fromDeg))));
  };
  const rightOpacity = useDerivedValue(() => show(-Math.cos(-tiltY.get()) * Math.sin(angle.get())));
  const leftOpacity = useDerivedValue(() => show(Math.cos(-tiltY.get()) * Math.sin(angle.get())));
  const topOpacity = useDerivedValue(() => show(-Math.sin(-tiltY.get())));
  const bottomOpacity = useDerivedValue(() => show(Math.sin(-tiltY.get())));
  const frontOpacity = useDerivedValue(() => (reduceMotion ? 1 - flip.get() : Math.cos(angle.get()) >= 0 ? 1 : 0));
  const backOpacity = useDerivedValue(() => 1 - frontOpacity.get());
  // As in the source, the card grows while it is turned, from its top edge (the text below moves down with it).
  // Mid-flip it rises a touch more, as a card does when it is picked up to be turned, and it floats at rest.
  const grow = useDerivedValue(() => [
    // (1 − cos) / 2: exactly at rest, and still, at loop 0.
    { translateY: -((1 - Math.cos((clock.get() / FLOAT.periodMs) * Math.PI * 2)) / 2) * FLOAT.amplitude },
    { scale: lift.get() * (1 + Math.sin(flip.get() * Math.PI) * FLIP.rise) },
  ]);
  const top = vec(margin + width / 2, margin);
  const centre = vec(margin + width / 2, margin + height / 2);
  const shadowShift = useDerivedValue(() => [
    { translateX: -tiltX.get() * 60 },
    { translateY: 18 - tiltY.get() * 40 },
    // The shadow narrows to a sliver while the card is edge-on, then widens again.
    { scaleX: 0.92 * Math.max(0.3, Math.abs(Math.cos(reduceMotion ? 0 : flip.get() * Math.PI))) },
    { scaleY: 0.92 },
  ]);
  const shadowOpacity = useDerivedValue(() => 0.55 + (lift.get() - 1) * 0.6);
  // The poster's warm light spilling onto the page: pre-blurred once, only its opacity moves. Zero at rest.
  // A 2° dead zone keeps the idle drift from lighting it.
  const glowOpacity = useDerivedValue(() => Math.min(1, ((lift.get() - 1) / (TILT.lift - 1)) * 0.7 + Math.max(0, Math.abs(tiltX.get()) + Math.abs(tiltY.get()) - 2 * (Math.PI / 180)) * 1.2));
  const glowShift = useDerivedValue(() => [{ translateX: tiltX.get() * 40 }, { translateY: 26 + tiltY.get() * 30 }]);
  // The back is drawn mirrored, so after half a turn it reads the right way round.
  const mirror = [{ scaleX: -1 }];
  const mid = vec(width / 2, height / 2);
  const backTitleWidth = backTitleFont.measureText(title).width;
  const lines = COPY.credits.map((c) => ({ ...c, w: (c.strong ? creditStrongFont : creditFont).measureText(c.text).width }));

  return <Canvas style={{ width: width + margin * 2, height: height * TILT.lift + margin * 2 }}>
    <Group transform={grow} origin={top}>
    <Group transform={glowShift} origin={centre} opacity={glowOpacity}>
      <RoundedRect x={margin + width * 0.06} y={margin + height * 0.1} width={width * 0.88} height={height * 0.86} r={LAYOUT.cardRadius} color={COLOR.glow}>
        <Blur blur={34} />
      </RoundedRect>
    </Group>
    <Group transform={shadowShift} origin={centre} opacity={shadowOpacity}>
      <RoundedRect x={margin} y={margin} width={width} height={height} r={LAYOUT.cardRadius} color={COLOR.shadow}>
        <Blur blur={22} />
      </RoundedRect>
    </Group>
    {edgeSide && <>
      <Group transform={rightT} opacity={rightOpacity}><Image image={edgeSide} x={0} y={0} width={EDGE.depth} height={height} fit="fill" /></Group>
      <Group transform={leftT} opacity={leftOpacity}><Image image={edgeSide} x={0} y={0} width={EDGE.depth} height={height} fit="fill" /></Group>
    </>}
    {edgeTop && <>
      <Group transform={topT} opacity={topOpacity}><Image image={edgeTop} x={0} y={0} width={width} height={EDGE.depth} fit="fill" /></Group>
      <Group transform={bottomT} opacity={bottomOpacity}><Image image={edgeTop} x={0} y={0} width={width} height={EDGE.depth} fit="fill" /></Group>
    </>}
    <Group>
      <Group transform={frontT}>
        <Group clip={clip} opacity={frontOpacity}>
          {plate && <Image image={plate} x={plateX} y={plateY} width={pw} height={ph} fit="cover" />}
          {figure && <Image image={figure} x={figureX} y={figureY} width={width} height={height} fit="cover" />}
          <Group transform={titleTransform}>
            <Group transform={[{ skewX: LAYOUT.titleSkew }]} origin={vec(titleBase.x + titleWidth / 2, titleBase.y)}>
              <Text x={titleBase.x + 1.5} y={titleBase.y + 2} text={title} font={font} color="rgba(40,16,0,0.55)" />
              <Text x={titleBase.x} y={titleBase.y} text={title} font={font}>
                <LinearGradient start={vec(titleBase.x, titleBase.y - 50)} end={vec(titleBase.x + titleWidth, titleBase.y + 10)} colors={[...COLOR.gold]} />
              </Text>
              <Text x={titleBase.x} y={titleBase.y} text={title} font={font} blendMode="overlay" opacity={holoOpacity}>
                <LinearGradient start={holoStart} end={holoEnd} colors={[...COLOR.holo]} mode="mirror" />
              </Text>
              <Text x={titleBase.x} y={titleBase.y} text={title} font={font} blendMode="screen">
                <LinearGradient start={foilStart} end={foilEnd} colors={['rgba(255,255,255,0)', COLOR.foil, 'rgba(255,255,255,0)']} />
              </Text>
            </Group>
          </Group>
          <Circle c={glare} r={width * 0.9} opacity={glareOpacity} blendMode="screen">
            <RadialGradient c={glare} r={width * 0.9} colors={[COLOR.glare, 'rgba(255,240,220,0)']} />
          </Circle>
          <RoundedRect rect={clip} style="stroke" strokeWidth={1} color={COLOR.rim} />
        </Group>
      </Group>
      <Group transform={backT}>
        <Group opacity={backOpacity} transform={reduceMotion ? [] : mirror} origin={mid}>
          <Group clip={clip}>
            {/* The back of a printed card: the plate, darkened and softened once, under the credits. */}
            {plate && <Image image={plate} x={(width - pw) / 2} y={(height - ph) / 2} width={pw} height={ph} fit="cover" opacity={BACK.plateOpacity}>
              <Blur blur={BACK.plateBlur} />
            </Image>}
            <RoundedRect rect={clip}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[...COLOR.back]} />
            </RoundedRect>
            <Text x={(width - backTitleWidth) / 2} y={height * BACK.titleY} text={title} font={backTitleFont}>
              <LinearGradient start={vec(0, height * BACK.titleY - 30)} end={vec(width, height * BACK.titleY)} colors={[...COLOR.gold]} />
            </Text>
            <RoundedRect x={width / 2 - BACK.rule / 2} y={height * BACK.ruleY} width={BACK.rule} height={1} r={0.5} color={COLOR.backRule} />
            {lines.map((l, i) => <Text key={l.text} x={(width - l.w) / 2} y={height * BACK.creditsY + i * BACK.lineStep + (l.gap ?? 0)}
              text={l.text} font={l.strong ? creditStrongFont : creditFont} color={l.strong ? COLOR.backInk : COLOR.backMute} />)}
            <Circle c={glare} r={width * 0.9} opacity={glareOpacity} blendMode="screen">
              <RadialGradient c={glare} r={width * 0.9} colors={[COLOR.glare, 'rgba(255,240,220,0)']} />
            </Circle>
            <RoundedRect rect={clip} style="stroke" strokeWidth={1} color={COLOR.rim} />
          </Group>
        </Group>
      </Group>
    </Group>
    </Group>
  </Canvas>;
}
