import {
  BlurMask, Group, Image, LinearGradient, RoundedRect, Text,
  type SkFont, type SkImage,
} from '@shopify/react-native-skia';
import type { JSX } from 'react';
import { interpolateColor, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { PALETTE, ROW } from './burst-contract';
import type { RowPose } from './row-pose';
import { TRACKS, trackDuration } from './tracks';

export type RowFonts = { title: SkFont; artist: SkFont; time: SkFont };

/** Cap H gives the actual cap-height and bearing; ascent includes unused font leading. */
export function capBaseline(font: SkFont, top: number, height: number): number {
  const cap = font.measureText('H');
  return top + (height - cap.height) / 2 - cap.y;
}

export function fitRowText(font: SkFont, text: string, available: number): string {
  if (font.measureText(text).width <= available) return text;
  let end = text.length;
  while (end > 0 && font.measureText(`${text.slice(0, end)}…`).width > available) end--;
  return `${text.slice(0, end)}…`;
}

function EqualiserBar({ x, phase, clock, reduced }: {
  x: number; phase: number; clock: SharedValue<number>; reduced: boolean;
}): JSX.Element {
  const height = useDerivedValue(() => reduced ? 8 + phase * 2 : 6 + 10 * (0.5 + 0.5 * Math.sin(clock.get() * Math.PI * 2 / 5000 + phase * 1.7)));
  const y = useDerivedValue(() => 26 - height.get() / 2);
  return <RoundedRect x={x} y={y} width={3} height={height} r={1.5} color={PALETTE.inkOnGreen} />;
}

export function TrackRow({ index, width, pose, selection, fonts, covers, clock, reduced = false }: {
  index: number; width: number; pose: SharedValue<RowPose>; selected: SharedValue<number>; selection: SharedValue<number[]>;
  fonts: RowFonts; covers: SkImage; clock: SharedValue<number>; reduced?: boolean;
}): JSX.Element {
  const track = TRACKS[index];
  const matrix = useDerivedValue(() => pose.get().matrix);
  const opacity = useDerivedValue(() => pose.get().opacity);
  const face = useDerivedValue(() => pose.get().face);
  const bloom = useDerivedValue(() => pose.get().glow * (0.48 + 0.27 * (pose.get().glow - 0.12) / 0.88));
  const blur = useDerivedValue(() => 18 + 4 * (pose.get().glow - 0.12) / 0.88);
  const active = useDerivedValue(() => selection.get()[index] ?? 0);
  const gradient = useDerivedValue(() => [interpolateColor(active.get(), [0, 1], [PALETTE.rowDark, '#1B3A2A']),
    interpolateColor(active.get(), [0, 1], [PALETTE.rowDark, '#1B3A2A']), PALETTE.green]);
  const rim = useDerivedValue(() => interpolateColor(active.get(), [0, 1], [PALETTE.rowRim, PALETTE.greenBright]));
  const rimWidth = useDerivedValue(() => 1 + active.get() * 0.5);
  const landingRim = useDerivedValue(() => pose.get().landing * 0.6);
  const topHighlight = useDerivedValue(() => ['rgba(255,255,255,' + (0.14 * (1 + 0.6 * pose.get().landing)) + ')', 'rgba(255,255,255,0)']);
  const durationOpacity = useDerivedValue(() => 1 - active.get());
  const equaliserOpacity = useDerivedValue(() => active.get());
  const duration = trackDuration(track.seconds);
  const timeBounds = fonts.time.measureText(duration);
  const timeX = width - 16 - timeBounds.width - timeBounds.x;
  const textWidth = timeX - 12 - 56;
  const title = fitRowText(fonts.title, track.title, textWidth);
  const artist = fitRowText(fonts.artist, track.artist, textWidth);
  const tileX = (index % 3) * ROW.art;
  const tileY = Math.floor(index / 3) * ROW.art;
  const artClip = { rect: { x: 8, y: 8, width: 36, height: 36 }, rx: 8, ry: 8 };
  return (
    <Group matrix={matrix} opacity={opacity}>
      <RoundedRect x={0} y={0} width={width} height={ROW.h} r={ROW.radius}
        color={PALETTE.green} opacity={bloom} blendMode="plus">
        <BlurMask blur={blur} style="normal" />
      </RoundedRect>
      <RoundedRect x={0} y={0} width={width} height={ROW.h} r={ROW.radius}>
        <LinearGradient start={{ x: 0, y: 26 }} end={{ x: width, y: 26 }} colors={gradient} positions={[0, 0.28, 1]} />
      </RoundedRect>
      <RoundedRect x={0} y={0} width={width} height={ROW.h} r={ROW.radius}>
        <LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: 20.8 }} colors={topHighlight} />
      </RoundedRect>
      <RoundedRect x={0.75} y={0.75} width={width - 1.5} height={ROW.h - 1.5} r={ROW.radius - 0.75}
        style="stroke" strokeWidth={rimWidth} color={rim} />
      <RoundedRect x={0.75} y={0.75} width={width - 1.5} height={ROW.h - 1.5} r={ROW.radius - 0.75}
        style="stroke" strokeWidth={rimWidth} color={rim} opacity={landingRim} blendMode="plus" />
      <Group opacity={face}>
        <Group clip={artClip}>
          <Image image={covers} x={8 - tileX} y={8 - tileY} width={108} height={108} fit="fill" />
          <RoundedRect x={8.5} y={8.5} width={35} height={35} r={7.5}
            style="stroke" strokeWidth={1} color="rgba(255,255,255,0.12)" />
        </Group>
        <Text x={56} y={capBaseline(fonts.title, 9, 18)} text={title} font={fonts.title} color={PALETTE.ink} />
        <Text x={56} y={capBaseline(fonts.artist, 28, 14)} text={artist} font={fonts.artist} color={PALETTE.inkMute} />
        <Group opacity={durationOpacity}>
          <Text x={timeX} y={capBaseline(fonts.time, 0, 52)} text={duration} font={fonts.time} color={PALETTE.inkOnGreen} />
        </Group>
        <Group opacity={equaliserOpacity}>
          <EqualiserBar x={width - 37} phase={0} clock={clock} reduced={reduced} />
          <EqualiserBar x={width - 31} phase={1} clock={clock} reduced={reduced} />
          <EqualiserBar x={width - 25} phase={2} clock={clock} reduced={reduced} />
          <EqualiserBar x={width - 19} phase={3} clock={clock} reduced={reduced} />
        </Group>
      </Group>
    </Group>
  );
}
