/**
 * The poster: five painted depths (sky, sun, three ridges with a walker on the nearest) and an embossed gold-foil
 * title, all sliding against each other with the tilt; a glare swims against it, a foil band and a faint holographic
 * sheen run through the title. The card is a 4.5 pt mounted print whose cream edge shows past about 8°, and it turns
 * over to a back with the credits.
 */
import {
  Blur, Canvas, Circle, Group, Image, LinearGradient, RadialGradient, Rect, RoundedRect, Skia, Text, useFont, vec,
  type SkImage,
} from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { EDGE, FLIP, FLOAT, HOLO, PARALLAX, TILT } from './motion';
import { OVERSCAN } from './poster-art';
import { BACK, COLOR, COPY, LAYOUT, TYPE } from './theme';

type Props = {
  width: number;
  height: number;
  layers: SkImage[];
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  lift: SharedValue<number>;
  flip: SharedValue<number>;
  clock: SharedValue<number>;
  reduceMotion: boolean;
  margin: number;
};

/** One painted depth, sliding by `k` points per radian of tilt. */
function Layer({ image, i, width, height, tiltX, tiltY }: { image: SkImage; i: number; width: number; height: number; tiltX: SharedValue<number>; tiltY: SharedValue<number> }) {
  const w = width * OVERSCAN[i];
  const h = height * OVERSCAN[i];
  const k = PARALLAX.layers[i];
  const x = useDerivedValue(() => (width - w) / 2 + tiltX.get() * k);
  const y = useDerivedValue(() => (height - h) / 2 + tiltY.get() * k);
  return <Image image={image} x={x} y={y} width={w} height={h} fit="fill" />;
}

/**
 * The card turns in 3D inside Skia (not as a native view transform), so a tilted card never cuts into the views
 * around it. `margin` leaves room for the turn and the shadow.
 */
export function PosterCard({ width, height, layers, tiltX, tiltY, lift, flip, clock, reduceMotion, margin }: Props) {
  const serif = useFont(require('./assets/InstrumentSerif-Regular.ttf'), TYPE.posterTitle);
  const backSerif = useFont(require('./assets/InstrumentSerif-Regular.ttf'), TYPE.backTitle);
  const credit = useFont(require('./assets/Inter-Medium.ttf'), TYPE.credit);
  const creditStrong = useFont(require('./assets/Inter-SemiBold.ttf'), TYPE.credit);
  const clip = Skia.RRectXY(Skia.XYWHRect(0, 0, width, height), LAYOUT.cardRadius, LAYOUT.cardRadius);
  const lines = COPY.posterTitle;
  const lineW = lines.map((l) => serif?.measureText(l).width ?? 0);
  const titleW = Math.max(...lineW);
  const titleY = height * LAYOUT.titleY;
  const titleTransform = useDerivedValue(() => [
    { translateX: tiltX.get() * PARALLAX.title },
    { translateY: tiltY.get() * PARALLAX.title },
  ]);
  // The emboss: the light falls from the upper left, against the tilt, so the shadow side moves as the card turns.
  const shadowOff = useDerivedValue(() => [{ translateX: 1.2 - tiltX.get() * 4 }, { translateY: 1.8 - tiltY.get() * 4 }]);
  const lightOff = useDerivedValue(() => [{ translateX: -0.6 + tiltX.get() * 2 }, { translateY: -0.8 + tiltY.get() * 2 }]);
  const foilAt = useDerivedValue(() => 0.5 + ((tiltX.get() + tiltY.get()) / 2) * PARALLAX.foil);
  const foilStart = useDerivedValue(() => vec((width - titleW) / 2 + titleW * foilAt.get() - 46, titleY - 50));
  const foilEnd = useDerivedValue(() => vec((width - titleW) / 2 + titleW * foilAt.get() + 46, titleY + 50));
  const holoAt = useDerivedValue(() => (tiltX.get() * HOLO.travelX + tiltY.get() * HOLO.travelY + flip.get() * HOLO.flipTravel) * titleW);
  const holoStart = useDerivedValue(() => vec((width - titleW) / 2 + holoAt.get(), titleY - TYPE.posterTitle));
  const holoEnd = useDerivedValue(() => vec((width - titleW) / 2 + holoAt.get() + titleW * HOLO.period, titleY + TYPE.posterTitle));
  const holoOpacity = useDerivedValue(() => HOLO.rest + Math.min(HOLO.max - HOLO.rest, (Math.abs(tiltX.get()) + Math.abs(tiltY.get())) * HOLO.gain));
  // The glare swims against the tilt, like a lamp reflected in a glossy print.
  const glare = useDerivedValue(() => vec(width * (0.5 + tiltX.get() * PARALLAX.glare), height * (0.3 + tiltY.get() * PARALLAX.glare)));
  const glareOpacity = useDerivedValue(() => 0.1 + Math.min(0.3, (Math.abs(tiltX.get()) + Math.abs(tiltY.get())) * 0.9));

  // The flip adds half a turn about the vertical axis; under Reduce Motion the faces cross-fade instead.
  const angle = useDerivedValue(() => tiltX.get() + (reduceMotion ? 0 : flip.get() * Math.PI));
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
  // The card grows about its centre while it is held or turned over (it stays inside its slot's 24 pt gap, so nothing
  // below it moves); mid-flip it rises a touch more, and it floats.
  const grow = useDerivedValue(() => [
    { translateY: -((1 - Math.cos((clock.get() / FLOAT.periodMs) * Math.PI * 2)) / 2) * FLOAT.amplitude },
    { scale: lift.get() * (1 + Math.sin(flip.get() * Math.PI) * FLIP.rise) },
  ]);
  const centre = vec(margin + width / 2, margin + height / 2);
  const shadowShift = useDerivedValue(() => [
    { translateX: -tiltX.get() * 60 },
    { translateY: 22 - tiltY.get() * 40 },
    // The shadow narrows to a sliver while the card is edge-on, then widens again.
    { scaleX: 0.9 * Math.max(0.3, Math.abs(Math.cos(reduceMotion ? 0 : flip.get() * Math.PI))) },
    { scaleY: 0.92 },
  ]);
  const shadowOpacity = useDerivedValue(() => 0.6 + (lift.get() - 1) * 2);
  // The poster's warm light spilling onto the page: pre-blurred, only its opacity moves. Zero at rest.
  const glowOpacity = useDerivedValue(() => Math.min(1, ((lift.get() - 1) / (TILT.lift - 1)) * 0.6 + Math.max(0, Math.abs(tiltX.get()) + Math.abs(tiltY.get()) - 2 * (Math.PI / 180)) * 1.2));
  const glowShift = useDerivedValue(() => [{ translateX: tiltX.get() * 40 }, { translateY: 30 + tiltY.get() * 30 }]);
  // The back is drawn mirrored, so after half a turn it reads the right way round.
  const mirror = [{ scaleX: -1 }];
  const mid = vec(width / 2, height / 2);
  const backTitleW = backSerif?.measureText(COPY.title).width ?? 0;

  const creditLines = COPY.credits.map((c) => ({ ...c, w: (c.strong ? creditStrong : credit)?.measureText(c.text).width ?? 0 }));
  const posterCredit = COPY.posterCredit;
  const posterCreditW = credit?.measureText(posterCredit).width ?? 0;

  return <Canvas style={{ width: width + margin * 2, height: height * TILT.lift + margin * 2 }}>
    <Group transform={grow} origin={centre}>
    <Group transform={glowShift} origin={centre} opacity={glowOpacity}>
      <RoundedRect x={margin + width * 0.08} y={margin + height * 0.2} width={width * 0.84} height={height * 0.78} r={LAYOUT.cardRadius} color={COLOR.glow}>
        <Blur blur={40} />
      </RoundedRect>
    </Group>
    <Group transform={shadowShift} origin={centre} opacity={shadowOpacity}>
      <RoundedRect x={margin} y={margin} width={width} height={height} r={LAYOUT.cardRadius} color={COLOR.shadow}>
        <Blur blur={24} />
      </RoundedRect>
    </Group>
    {/* The print's edges: cream board, darker towards the back. */}
    <Group transform={rightT} opacity={rightOpacity}><Rect x={0} y={0} width={EDGE.depth} height={height}><LinearGradient start={vec(0, 0)} end={vec(EDGE.depth, 0)} colors={[...COLOR.edge]} /></Rect></Group>
    <Group transform={leftT} opacity={leftOpacity}><Rect x={0} y={0} width={EDGE.depth} height={height}><LinearGradient start={vec(0, 0)} end={vec(EDGE.depth, 0)} colors={[...COLOR.edge]} /></Rect></Group>
    <Group transform={topT} opacity={topOpacity}><Rect x={0} y={0} width={width} height={EDGE.depth}><LinearGradient start={vec(0, 0)} end={vec(0, EDGE.depth)} colors={[...COLOR.edge]} /></Rect></Group>
    <Group transform={bottomT} opacity={bottomOpacity}><Rect x={0} y={0} width={width} height={EDGE.depth}><LinearGradient start={vec(0, 0)} end={vec(0, EDGE.depth)} colors={[...COLOR.edge]} /></Rect></Group>
    <Group>
      <Group transform={frontT}>
        <Group clip={clip} opacity={frontOpacity}>
          {layers.map((img, i) => <Layer key={i} image={img} i={i} width={width} height={height} tiltX={tiltX} tiltY={tiltY} />)}
          {serif && <Group transform={titleTransform}>
            {lines.map((l, n) => {
              const x = (width - lineW[n]) / 2;
              const y = titleY + n * TYPE.posterLine;
              return <Group key={l}>
                {/* Pressed into the board: a soft dark edge on the far side, a light one on the near side. */}
                <Group transform={shadowOff}><Text x={x} y={y} text={l} font={serif} color={COLOR.embossDark} /></Group>
                <Group transform={lightOff}><Text x={x} y={y} text={l} font={serif} color={COLOR.embossLight} /></Group>
                <Text x={x} y={y} text={l} font={serif}>
                  <LinearGradient start={vec(x, y - 46)} end={vec(x + lineW[n], y + 8)} colors={[...COLOR.gold]} />
                </Text>
                <Text x={x} y={y} text={l} font={serif} blendMode="overlay" opacity={holoOpacity}>
                  <LinearGradient start={holoStart} end={holoEnd} colors={[...COLOR.holo]} mode="mirror" />
                </Text>
                <Text x={x} y={y} text={l} font={serif} blendMode="screen">
                  <LinearGradient start={foilStart} end={foilEnd} colors={['rgba(255,255,255,0)', COLOR.foil, 'rgba(255,255,255,0)']} />
                </Text>
              </Group>;
            })}
          </Group>}
          {credit && <Text x={(width - posterCreditW) / 2} y={height - 18} text={posterCredit} font={credit} color={COLOR.posterInk} />}
          <Circle c={glare} r={width * 0.9} opacity={glareOpacity} blendMode="screen">
            <RadialGradient c={glare} r={width * 0.9} colors={[COLOR.glare, 'rgba(255,240,220,0)']} />
          </Circle>
          <RoundedRect rect={clip} style="stroke" strokeWidth={1} color={COLOR.rim} />
        </Group>
      </Group>
      <Group transform={backT}>
        <Group opacity={backOpacity} transform={reduceMotion ? [] : mirror} origin={mid}>
          <Group clip={clip}>
            <RoundedRect rect={clip}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[...COLOR.back]} />
            </RoundedRect>
            {backSerif && <Text x={(width - backTitleW) / 2} y={height * BACK.titleY} text={COPY.title} font={backSerif}>
              <LinearGradient start={vec(0, height * BACK.titleY - 30)} end={vec(width, height * BACK.titleY)} colors={[...COLOR.gold]} />
            </Text>}
            <RoundedRect x={width / 2 - BACK.rule / 2} y={height * BACK.ruleY} width={BACK.rule} height={1} r={0.5} color={COLOR.backRule} />
            {credit && creditStrong && creditLines.map((l, i) => <Text key={l.text} x={(width - l.w) / 2} y={height * BACK.creditsY + i * BACK.lineStep + (l.gap ?? 0)}
              text={l.text} font={l.strong ? creditStrong : credit} color={l.strong ? COLOR.backInk : COLOR.backMute} />)}
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
