import { BlurMask, Canvas, Glyphs, Group, Path, Picture, RoundedRect, Skia, rect, type SkFont, type SkPicture } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { COLORS, type Geo, LINE_H, PAPER_H, PAPER_W, TEXT_X, TOP_PAD, barTip } from './typewriterSignupArt';
import { BARS, type Pose, hash } from './typewriterSignupMotion';

/*
 Typewriter Sign Up — the one Canvas: desk and header, the sheet with its typed glyphs (three
 ink densities and a bleed on the newest), the carriage, the basket with its fan of typebars,
 the deck and the margin bell. Everything moving reads `pose`.
*/

/** Most glyphs a line of the sheet takes. */
export const MAX_COLS = 18;

type Props = {
  g: Geo;
  W: number;
  art: { background: SkPicture; paper: SkPicture; carriage: SkPicture; body: SkPicture; deck: SkPicture };
  fonts: { typed: SkFont };
  adv: number;
  pose: SharedValue<Pose>;
  mode: SharedValue<number>;
  uIds: SharedValue<number[][]>;
  uInk: SharedValue<number>;
  scriptIds: number[][];
  paperLeft: SharedValue<number>;
  paperTop: SharedValue<number>;
};

export function TypewriterCanvas({ g, W, art, fonts, adv, pose, mode, uIds, uInk, scriptIds, paperLeft, paperTop }: Props) {
  const paperTransform = useDerivedValue(() => {
    const f = pose.get().flex;
    return [
      { translateX: paperLeft.get() + PAPER_W / 2 },
      { translateY: paperTop.get() + PAPER_H },
      { rotate: (-1.6 * f * Math.PI) / 180 },
      { skewX: -0.03 * f },
      { scaleX: 1 - 0.025 * Math.abs(f) },
      { translateX: -PAPER_W / 2 },
      { translateY: -PAPER_H },
    ];
  });
  const carriageTransform = useDerivedValue(() => [{ translateX: paperLeft.get() }]);
  const paperClip = useMemo(() => rect(-200, -3000, W + 400, 3000 + g.platenY - 4), [W, g.platenY]);

  const ids = useDerivedValue(() => (mode.get() === 0 ? scriptIds : uIds.get()));
  /** Glyphs of the three lines in three ink densities, in sheet coordinates. */
  const buckets = useDerivedValue(() => {
    const p = pose.get();
    const lines = ids.get();
    const n = [p.n0, p.n1, p.n2];
    const out: { id: number; pos: { x: number; y: number } }[][] = [[], [], []];
    for (let l = 0; l < 3; l += 1) {
      const c = Math.min(n[l], MAX_COLS, lines[l]?.length ?? 0);
      for (let k = 0; k < c; k += 1) {
        const pos = {
          x: TEXT_X + k * adv + (hash(l * 97 + k * 3 + 1) - 0.5) * 0.8,
          y: TOP_PAD + l * LINE_H + (hash(l * 53 + k * 7 + 2) - 0.5) * 1.4,
        };
        out[Math.floor(hash(l * 11 + k * 5 + 3) * 3)].push({ id: lines[l][k], pos });
      }
    }
    return out;
  });
  const ink0 = useDerivedValue(() => buckets.get()[0]);
  const ink1 = useDerivedValue(() => buckets.get()[1]);
  const ink2 = useDerivedValue(() => buckets.get()[2]);
  /** The newest glyph, drawn again blurred while its ink spreads (0.3 s). */
  const fresh = useDerivedValue(() => {
    const p = pose.get();
    if (p.inkAge > 0.3 || p.inkAge < 0) return [];
    const l = Math.round(Math.min(2, Math.max(0, p.line)));
    const k = Math.min([p.n0, p.n1, p.n2][l], MAX_COLS) - 1;
    const line = ids.get()[l];
    if (k < 0 || !line || k >= line.length) return [];
    return [{ id: line[k], pos: { x: TEXT_X + k * adv, y: TOP_PAD + l * LINE_H } }];
  });
  const freshOpacity = useDerivedValue(() => 0.55 * (1 - Math.min(1, pose.get().inkAge / 0.3)));
  const inkOpacity = useDerivedValue(() => (mode.get() === 0 ? 1 : uInk.get()));

  const restBars = useDerivedValue(() => {
    const p = pose.get();
    const path = Skia.PathBuilder.Make();
    for (let i = 0; i < BARS; i += 1) {
      if (i === p.bar && p.strike > 0.001) continue;
      const b = barTip(g, i, 0);
      path.moveTo(b[0], b[1]);
      path.lineTo(b[2], b[3]);
    }
    return path.build();
  });
  const restHeads = useDerivedValue(() => {
    const p = pose.get();
    const path = Skia.PathBuilder.Make();
    for (let i = 0; i < BARS; i += 1) {
      if (i === p.bar && p.strike > 0.001) continue;
      const b = barTip(g, i, 0);
      path.addCircle(b[2], b[3], 2.6);
    }
    return path.build();
  });
  const activeBar = useDerivedValue(() => {
    const p = pose.get();
    const path = Skia.PathBuilder.Make();
    if (p.strike <= 0.001) return path.build();
    const b = barTip(g, p.bar, p.strike);
    path.moveTo(b[0], b[1]);
    path.lineTo(b[2], b[3]);
    return path.build();
  });
  const activeHead = useDerivedValue(() => {
    const p = pose.get();
    const path = Skia.PathBuilder.Make();
    if (p.strike <= 0.001) return path.build();
    const b = barTip(g, p.bar, p.strike);
    const s = 2.6 + p.strike * 1.6;
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(b[2] - s, b[3] - s * 0.8, s * 2, s * 1.6), 1, 1));
    return path.build();
  });

  const machineTransform = useMemo(() => [{ scale: g.scale }], [g.scale]);
  const bellX = g.W - 52;
  const bellY = g.deckY + 24;
  const bellTransform = useDerivedValue(() => [{ rotate: (pose.get().bell * 14 * Math.PI) / 180 }]);
  const bellGlowR = useDerivedValue(() => 12 + (1 - pose.get().bellGlow) * 22);
  const bellGlowOpacity = useDerivedValue(() => pose.get().bellGlow * 0.55);

  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Picture picture={art.background} />
      <Group transform={machineTransform}>
        <Group clip={paperClip}>
          <Group transform={paperTransform}>
            <RoundedRect x={4} y={10} width={PAPER_W - 8} height={PAPER_H} r={2} color="rgba(0,0,0,0.42)">
              <BlurMask blur={14} style="normal" />
            </RoundedRect>
            <Picture picture={art.paper} />
            <Group opacity={inkOpacity}>
              <Glyphs font={fonts.typed} glyphs={ink0} x={0} y={0} color="rgba(43,36,32,0.72)" />
              <Glyphs font={fonts.typed} glyphs={ink1} x={0} y={0} color="rgba(43,36,32,0.86)" />
              <Glyphs font={fonts.typed} glyphs={ink2} x={0.3} y={0.2} color="rgba(38,31,28,0.97)" />
              <Glyphs font={fonts.typed} glyphs={fresh} x={0} y={0} color="#2b2420" opacity={freshOpacity}>
                <BlurMask blur={1.6} style="normal" />
              </Glyphs>
            </Group>
          </Group>
        </Group>
        <Group transform={carriageTransform}>
          <Picture picture={art.carriage} />
        </Group>
        <Picture picture={art.body} />
        <Path path={restBars} style="stroke" strokeWidth={2.4} strokeCap="round" color="#8e959c" />
        <Path path={restHeads} color="#c3c9ce" />
        <Picture picture={art.deck} />
        <Path path={activeBar} style="stroke" strokeWidth={3} strokeCap="round" color="#d5dade" />
        <Path path={activeHead} color="#2f3336" />
        <Group transform={bellTransform} origin={{ x: bellX, y: bellY + 8 }}>
          <BellArt x={bellX} y={bellY} />
        </Group>
        <RingGlow x={bellX} y={bellY} r={bellGlowR} opacity={bellGlowOpacity} />
      </Group>
    </Canvas>
  );
}

/** The margin bell: a small chrome dome on a post. */
function BellArt({ x, y }: { x: number; y: number }) {
  const dome = useMemo(() => {
    return Skia.PathBuilder.Make()
      .moveTo(x - 11, y + 8)
      .cubicTo(x - 11, y - 6, x - 5, y - 11, x, y - 11)
      .cubicTo(x + 5, y - 11, x + 11, y - 6, x + 11, y + 8)
      .close()
      .build();
  }, [x, y]);
  return (
    <Group>
      <RoundedRect x={x - 13} y={y + 7} width={26} height={4} r={2} color="#3d4247" />
      <Path path={dome} color="#9aa1a8" />
      <Path path={dome} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.35)" />
      <RoundedRect x={x - 6} y={y - 8} width={4} height={10} r={2} color="rgba(255,255,255,0.7)" />
      <RoundedRect x={x - 1.5} y={y - 15} width={3} height={5} r={1.5} color="#c3c9ce" />
    </Group>
  );
}

function RingGlow({ x, y, r, opacity }: { x: number; y: number; r: SharedValue<number>; opacity: SharedValue<number> }) {
  const path = useDerivedValue(() => Skia.Path.Circle(x, y, r.get()));
  const o = useDerivedValue(() => opacity.get());
  return <Path path={path} style="stroke" strokeWidth={1.5} color={COLORS.brass} opacity={o} />;
}

