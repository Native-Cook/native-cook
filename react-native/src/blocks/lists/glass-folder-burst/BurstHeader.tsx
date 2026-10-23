import { Group, Text, type SkFont } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { PALETTE } from './burst-contract';
import type { BurstLayout } from './burst-layout';
import { capBaseline } from './TrackRow';
import { PLAYLIST, playlistSubtitle, TRACKS } from './tracks';

/** The subtitle counts the rows this screen has room for, so the header never promises more than the list shows. */
export function headerLabels(font: SkFont, width: number, count: number): string[] {
  const labels = [playlistSubtitle(count)];
  for (let i = 0; i < count; i++) {
    const long = `Now playing · ${TRACKS[i].title}`;
    labels.push(font.measureText(long).width <= width ? long : TRACKS[i].title);
  }
  return labels;
}
function HeaderLine({ text, font, layout, dock, selection, index = -2 }: {
  text: string; font: SkFont; layout: BurstLayout; dock: SharedValue<number>;
  selection: SharedValue<number[]>; index?: number;
}) {
  const title = index === -2;
  const box = title ? layout.label.title : layout.label.subtitle;
  const target = title ? layout.header.title : layout.header.subtitle;
  const bounds = font.measureText(text);
  const restX = (layout.width - bounds.width) / 2 - bounds.x;
  const base = capBaseline(font, box.y, box.h);
  const end = capBaseline(font, target.y, target.h);
  const x = useDerivedValue(() => restX + (target.x - restX) * dock.get());
  const y = useDerivedValue(() => base + (end - base) * dock.get());
  const opacity = useDerivedValue(() => {
    if (index === -2) return 1;
    const mixes = selection.get();
    if (index >= 0) return mixes[index] ?? 0;
    let total = 0;
    for (let i = 0; i < mixes.length; i++) total += mixes[i];
    return Math.max(0, 1 - total);
  });
  return <Group opacity={opacity}><Text text={text} font={font} x={x} y={y} color={title ? PALETTE.ink : PALETTE.inkMute} /></Group>;
}
export function BurstHeader({ layout, title, subtitle, dock, selection }: {
  layout: BurstLayout; title: SkFont; subtitle: SkFont;
  dock: SharedValue<number>; selection: SharedValue<number[]>;
}) {
  const labels = headerLabels(subtitle, layout.header.subtitle.w, layout.n);
  return <Group>
    <HeaderLine text={PLAYLIST} font={title} layout={layout} dock={dock} selection={selection} />
    {labels.map((text, i) => <HeaderLine key={i} text={text} font={subtitle} layout={layout}
      dock={dock} selection={selection} index={i - 1} />)}
  </Group>;
}
