import { Group, type SkImage } from '@shopify/react-native-skia';
import type { JSX } from 'react';
import { type SharedValue, useDerivedValue, useReducedMotion } from 'react-native-reanimated';

import type { FolderDrive } from './burst-contract';
import type { BurstLayout } from './burst-layout';
import { STATE } from './burst-sim';
import { rowPose } from './row-pose';
import { TrackRow, type RowFonts } from './TrackRow';

/** Parent loads with useImage(COVERS). Nine 256 px tiles in a 768 px square atlas. */
export const COVERS = require('./assets/covers.png');

export type BurstRowsProps = {
  layout: BurstLayout;
  /** 12 numbers per row; see STATE in burst-sim.ts. Publish sim.rows after each step. */
  rows: SharedValue<number[]>;
  folder: FolderDrive;
  selected: SharedValue<number>;
  pressed: SharedValue<number>;
  pressAmount: SharedValue<number>;
  selection: SharedValue<number[]>;
  clock: SharedValue<number>;
  fonts: RowFonts;
  covers: SkImage;
};

function AnimatedRow({ index, layout, rows, folder, selected, pressed, pressAmount, selection, fonts, covers, clock, reduced }: BurstRowsProps & {
  index: number; clock: SharedValue<number>; reduced: boolean;
}): JSX.Element {
  // Capture shared values individually, never the enclosing drive or props object.
  const { cx, cy, scale } = folder;
  const pose = useDerivedValue(() => {
    const state = rows.get();
    const o = index * STATE.stride;
    return rowPose(state[o], state[o + STATE.v], state[o + STATE.m], index, layout.n, layout,
      { cx: cx.get(), cy: cy.get(), scale: scale.get() }, state[o + STATE.reduced] === 1,
      state[o + STATE.fade], pressed.get() === index ? 1 - 0.03 * pressAmount.get() : 1, state[o + STATE.flash]);
  });
  return <TrackRow index={index} width={layout.slots[index].w} pose={pose} selected={selected}
    selection={selection} fonts={fonts} covers={covers} clock={clock} reduced={reduced} />;
}

/** Paint before the folder's front panel so newly ejected and swallowed strips are occluded. */
export function BurstRows(props: BurstRowsProps): JSX.Element {
  const reduced = useReducedMotion();
  const children: JSX.Element[] = [];
  // Top / earliest released rows paint last, in front. This loop runs only on React renders.
  for (let i = props.layout.n - 1; i >= 0; i--) {
    children.push(<AnimatedRow key={i} index={i} {...props} reduced={reduced} />);
  }
  return <Group>{children}</Group>;
}
