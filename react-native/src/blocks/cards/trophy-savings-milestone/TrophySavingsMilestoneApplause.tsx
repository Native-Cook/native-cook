import { BlurMask, Canvas, Group, ImageSVG, LinearGradient, Path, RadialGradient, Rect, Skia, vec } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import type { TrophyApplauseFrame } from './TrophySavingsMilestoneCelebration';
import { clamp, smooth } from './TrophySavingsMilestoneMotion';
import { TROPHY_APPLAUSE_IDS, TROPHY_PAPER_COLORS, TROPHY_REFERENCE_PAPER, trophyPaperFacePath, trophyPaperPieceSVG } from './TrophySavingsMilestoneReferencePaper';

type Props = { frame: SharedValue<TrophyApplauseFrame> };
// The first six wake entries are near paper; the remaining twelve are quieter mid-depth paper.
const TRAVEL = [
  [-24, -22, -1], [-27, -14, 1], [28, -22, -1], [18, 30, 1], [-30, 12, -1], [26, 30, 1],
  [-10, 10, 1], [12, -12, -1], [-6, -12, 1], [13, 6, -1], [12, 10, 1], [12, -8, -1],
  [9, -9, 1], [-13, 12, -1], [-7, 12, -1], [-12, 11, 1], [-9, -8, 1], [10, 8, -1],
];
// Readable front/reverse planes at the first full wrap; feed and recoil still tumble.
const CREST_FACING = [0.24, Math.PI - 0.3, -0.32, Math.PI + 0.24, 0.36, -0.24];
const PAPER = TROPHY_APPLAUSE_IDS.map((sourceID, index) => {
  const piece = TROPHY_REFERENCE_PAPER[sourceID];
  const color = TROPHY_PAPER_COLORS[piece[6]];
  const rgb = [1, 3, 5].map(offset => Number.parseInt(color.slice(offset, offset + 2), 16));
  const reverse = `rgb(${rgb.map(value => Math.round(value * (index < 6 ? 0.84 : 0.78))).join(',')})`;
  return {
    piece, near: index < 6, index, reverse, arc: piece[7] === 'arc',
    face: Skia.Path.MakeFromSVGString(trophyPaperFacePath(piece))!,
    // At rest this is the exact original SVG fragment, with identical opacity and fold compositing.
    source: Skia.SVG.MakeFromString(`<svg xmlns="http://www.w3.org/2000/svg" width="393" height="350" viewBox="0 0 393 350">${trophyPaperPieceSVG(piece)}</svg>`),
  };
});
const BOWL = Skia.PathBuilder.Make().moveTo(139, 120)
  .cubicTo(166, 105, 226, 105, 252, 120).cubicTo(252, 157, 240, 186, 214, 208)
  .cubicTo(208, 215, 202, 218, 195.5, 219).cubicTo(189, 218, 183, 216, 178, 211)
  .cubicTo(150, 190, 140, 156, 139, 120).close().detach();
const FACET = Skia.PathBuilder.Make().moveTo(159, 111).cubicTo(157, 151, 165, 178, 172, 192)
  .cubicTo(176, 201, 181, 209, 185, 214).cubicTo(176, 195, 172, 154, 174, 110).close().detach();
const GLINT = Skia.PathBuilder.Make().moveTo(174, 122).lineTo(176.5, 127.5).lineTo(183.5, 130)
  .lineTo(176.5, 132.5).lineTo(174, 138).lineTo(171.5, 132.5).lineTo(164.5, 130).lineTo(171.5, 127.5).close().detach();

function paperPose(scene: TrophyApplauseFrame, index: number, near: boolean) {
  'worklet';
  const start = scene.wakeStarts[index] ?? -1;
  const cadence = scene.cadence ?? 1;
  const age = (scene.clock - start) / cadence - (near ? 0 : 0.04);
  if (!scene.active || scene.reduced || start < 0 || age < 0) return { x: 0, y: 0, rotation: 0, sx: 1, sy: 1, lift: 0, reverse: 0 };
  const flutter = age <= 0.72 ? Math.sin(Math.PI * age / 0.72) * Math.exp(-1.7 * age) : 0;
  const held = smooth(scene.progress / 0.65) * smooth(age / 0.16);
  // A finite breeze continues through the gallery crest, then settles on the same clock.
  const breeze = smooth(age / 0.1) * (1 - smooth((age - 0.56) / 0.28));
  const afterContact = scene.contactStart >= 0 && scene.clock - scene.contactStart > 0.12 * cadence;
  const recoil = afterContact ? smooth((0.98 - scene.progress) / 0.25) * Math.sin(Math.PI * scene.progress) : 0;
  const wind = Math.max(breeze * (0.25 + 0.75 * smooth(scene.progress / 0.5)), 0.6 * recoil);
  const energy = clamp(0.8 * held + 0.3 * flutter);
  const [dx, dy, direction] = TRAVEL[index];
  const scale = 1 + (near ? 0.64 : 0.22) * energy;
  const rawFlip = direction * ((near ? 3.3 : 2.6) * wind * Math.sin(age * 7.5 + index * 0.81) + 1.8 * flutter);
  const crest = near ? smooth((scene.progress - 0.78) / 0.22) * smooth(age / 0.16) : 0;
  const flip = rawFlip * (1 - 0.95 * crest) + (CREST_FACING[index] ?? 0) * crest + 0.12 * wind * Math.sin(age * 6 + index) * crest;
  const facing = Math.cos(flip);
  return {
    x: dx * energy + (near ? 10 : 5.5) * wind * Math.sin(age * 9 + index * 0.81),
    y: dy * energy + (near ? 9 : 5) * wind * Math.cos(age * 7.5 + index * 0.67),
    rotation: direction * 0.55 * energy + (near ? 1.05 : 0.75) * wind * Math.sin(age * 8.5 + index * 0.73),
    sx: scale * Math.max(0.075, Math.abs(facing)) * (near ? 1 + 0.72 * energy : 1),
    sy: scale * (1 - 0.06 * Math.sin(flip) ** 2),
    lift: energy, reverse: smooth(-facing / 0.16),
  };
}

const ApplausePiece = memo(function ApplausePiece({ frame, index }: Props & { index: number }) {
  const paper = PAPER[index];
  const near = paper.near;
  const [x, y, , , angle] = paper.piece;
  const localAxis = (angle + (paper.arc ? 90 : 0)) * Math.PI / 180;
  const pose = useDerivedValue(() => paperPose(frame.get(), index, near));
  const transform = useDerivedValue(() => {
    const p = pose.get();
    if (near) {
      if (p.lift === 0) return [];
      // Perspective acts on the short paper axis, not on the scene's horizontal axis.
      return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rotation + localAxis }, { scaleX: p.sx }, { scaleY: p.sy }, { rotate: -localAxis }];
    }
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rotation }, { scaleX: p.sx }, { scaleY: p.sy }];
  });
  const shadow = useDerivedValue(() => pose.get().lift * (near ? 0.19 : 0.07));
  const reverse = useDerivedValue(() => pose.get().reverse);
  const edge = useDerivedValue(() => pose.get().lift * (near ? 0.45 : 0.2));
  const turnedLip = useDerivedValue(() => pose.get().lift * 0.28);
  return (
    <Group transform={transform} origin={vec(x, y)}>
      <Group transform={[{ translateX: x + 1 }, { translateY: y + (paper.near ? 3 : 1.5) }, { rotate: angle * Math.PI / 180 }]}>
        <Path path={paper.face} color="#66515A" opacity={shadow}>
          {paper.near && <BlurMask blur={1.5} style="normal" />}
        </Path>
      </Group>
      <ImageSVG svg={paper.source} width={393} height={350} />
      <Group transform={[{ translateX: x }, { translateY: y }, { rotate: angle * Math.PI / 180 }]}>
        <Path path={paper.face} color={paper.reverse} opacity={reverse} />
        {near && <Path path={paper.face} opacity={turnedLip}>
          <LinearGradient
            start={paper.arc ? vec(0, -paper.piece[3] / 2 - paper.piece[5]) : vec(-paper.piece[3] / 2, 0)}
            end={paper.arc ? vec(0, paper.piece[3] / 2 - paper.piece[5]) : vec(paper.piece[3] / 2, 0)}
            colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0)', 'rgba(255,249,237,0.8)', 'rgba(80,28,58,0.45)']}
            positions={[0, 0.72, 0.86, 1]}
          />
        </Path>}
        <Path path={paper.face} color="#FFF4DF" opacity={edge} style="stroke" strokeWidth={0.55} />
      </Group>
    </Group>
  );
});

function WarmPool({ frame }: Props) {
  const colors = useDerivedValue(() => {
    const scene = frame.get();
    const power = scene.active && !scene.reduced ? smooth((scene.progress - 0.08) / 0.74) : 0;
    return [new Float32Array([1, 216 / 255, 135 / 255, 0.35 * power]), new Float32Array([1, 227 / 255, 173 / 255, 0.24 * power]), new Float32Array([1, 240 / 255, 203 / 255, 0.055 * power]), new Float32Array([1, 240 / 255, 203 / 255, 0])];
  });
  return <Rect x={0} y={0} width={393} height={350}>
    <RadialGradient c={vec(196, 157)} r={190} colors={colors} positions={[0, 0.45, 0.82, 1]} />
  </Rect>;
}

export const TrophySavingsMilestoneRearApplause = memo(function TrophySavingsMilestoneRearApplause({ frame }: Props) {
  return <Canvas style={styles.canvas} pointerEvents="none" accessible={false}>
    <WarmPool frame={frame} />
    {PAPER.slice(6).map(paper => <ApplausePiece key={paper.index} frame={frame} index={paper.index} />)}
  </Canvas>;
});

export const TrophySavingsMilestoneFrontApplause = memo(function TrophySavingsMilestoneFrontApplause({ frame }: Props) {
  return <Canvas style={styles.canvas} pointerEvents="none" accessible={false}>
    {PAPER.slice(0, 6).map(paper => <ApplausePiece key={paper.index} frame={frame} index={paper.index} />)}
  </Canvas>;
});

export const TrophySavingsMilestoneCupLight = memo(function TrophySavingsMilestoneCupLight({ frame }: Props) {
  const flash = useDerivedValue(() => {
    const scene = frame.get();
    if (!scene.active || scene.reduced || scene.contactStart < 0) return 0;
    const age = (scene.clock - scene.contactStart) / (scene.cadence ?? 1);
    if (age < 0 || age >= 0.42) return 0;
    return smooth(age / 0.07) * (1 - smooth((age - 0.11) / 0.31));
  });
  const facet = useDerivedValue(() => 0.28 * flash.get());
  const glint = useDerivedValue(() => 0.65 * flash.get());
  return <Canvas style={styles.canvas} pointerEvents="none" accessible={false}>
    <Group clip={BOWL}>
      <Path path={FACET} opacity={facet}>
        <LinearGradient start={vec(158, 128)} end={vec(184, 199)} colors={['#FFFCE3', '#FFF4BB']} />
      </Path>
      <Path path={GLINT} color="#FFFFFF" opacity={glint} />
    </Group>
  </Canvas>;
});

const styles = StyleSheet.create({ canvas: { position: 'absolute', left: 0, top: 0, width: 393, height: 350 } });
