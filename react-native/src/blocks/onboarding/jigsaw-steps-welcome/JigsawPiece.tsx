import { BlurMask, Group, Image, Path, type SkImage, type SkPath } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

/*
 One jigsaw piece, drawn from the shared pose array (8 numbers per piece: x, y, rotation, scale,
 lift, edge light, groove, on board). A piece is: a soft ambient shadow and a tight contact
 shadow that both grow and soften with lift, the cardboard's edge (1.6 pt of brown under it while
 it is off the board), the photo clipped to its outline, a bevel light inside the edge, the dark
 groove of the cut while it sits in the board, and a warm light running round it as it snaps in.

 `index` is a fixed piece, or a SharedValue for an overlay slot that shows whichever piece is in
 flight (so the flying piece is always on top of the pile and the board).
 */

export const POSE = 8;

type Props = {
  index: number | SharedValue<number>;
  paths: SkPath[];
  homes: number[][];
  poses: SharedValue<number[]>;
  /** For a fixed piece: the overlay slots, so the piece hides while a slot draws it. */
  slots?: SharedValue<number[]>;
  photo: SkImage | null;
  board: { x: number; y: number; w: number; h: number };
};

export function JigsawPiece({ index, paths, homes, poses, slots, photo, board }: Props) {
  const idx = useDerivedValue(() => (typeof index === 'number' ? index : index.get()));
  const path = useDerivedValue(() => paths[Math.max(0, idx.get())]);
  const p = useDerivedValue(() => {
    const i = idx.get();
    if (i < 0) return [0, 0, 0, 1, 0, 0, 0, 1];
    return poses.get().slice(i * POSE, i * POSE + POSE);
  });
  const transform = useDerivedValue(() => {
    const v = p.get();
    const h = homes[Math.max(0, idx.get())];
    return [{ translateX: v[0] }, { translateY: v[1] }, { rotate: v[2] }, { scale: v[3] }, { translateX: -h[0] }, { translateY: -h[1] }];
  });
  const opacity = useDerivedValue(() => {
    const i = idx.get();
    if (i < 0) return 0;
    if (slots && typeof index === 'number' && slots.get().indexOf(index) >= 0) return 0;
    return 1;
  });
  const offBoard = useDerivedValue(() => 1 - p.get()[7]);
  const ambOpacity = useDerivedValue(() => (0.16 + 0.16 * p.get()[4]) * offBoard.get());
  const ambShift = useDerivedValue(() => [{ translateY: 3 + 14 * p.get()[4] }]);
  const ambBlur = useDerivedValue(() => 3 + 9 * p.get()[4]);
  const conOpacity = useDerivedValue(() => 0.32 * (1 - Math.min(1, p.get()[4] * 1.4)) * offBoard.get());
  const flash = useDerivedValue(() => p.get()[5]);
  const groove = useDerivedValue(() => p.get()[6] * 0.55);

  return (
    <Group transform={transform} opacity={opacity}>
      <Group transform={ambShift} opacity={ambOpacity}>
        <Path path={path} color="#3B2614">
          <BlurMask blur={ambBlur} style="normal" />
        </Path>
      </Group>
      <Group transform={[{ translateY: 1.2 }]} opacity={conOpacity}>
        <Path path={path} color="#2A1A0E">
          <BlurMask blur={1.4} style="normal" />
        </Path>
      </Group>
      <Group transform={[{ translateY: 1.6 }]} opacity={offBoard}>
        <Path path={path} color="#8C6F55" />
      </Group>
      <Group clip={path}>
        <Image image={photo} x={board.x} y={board.y} width={board.w} height={board.h} fit="cover" />
        <Path path={path} style="stroke" strokeWidth={2.6} color="rgba(255,250,240,0.32)" />
        <Path path={path} style="stroke" strokeWidth={1.8} color="rgba(64,40,22,1)" opacity={groove} />
        <Path path={path} style="stroke" strokeWidth={4} color="rgba(255,226,170,1)" opacity={flash} />
      </Group>
    </Group>
  );
}
