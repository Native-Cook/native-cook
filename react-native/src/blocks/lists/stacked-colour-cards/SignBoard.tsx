import { Group, Path, Skia, Text as SkiaText, type SkFont } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { type P2, facing, project, rectToQuad, shade } from './projection';
import type { Sign } from './signs';
import { SHADE, TYPE } from './theme';

// One board of the post: a thick block, arrow-pointed or square-ended, turned about the pole by
// its own heading plus the post's. Faces are culled by their normal, so it needs no sorting.

type Props = {
  sign: Sign;
  cx: number;
  heading: SharedValue<number>;
  nameFont: SkFont;
  kmFont: SkFont;
};

export function SignBoard({ sign, cx, heading, nameFont, kmFont }: Props) {
  // Opt out of the React Compiler: its memo cache would end up in the geometry worklet's
  // closure, which then aborts on the UI thread.
  'use no memo';
  const { L, tail, tip } = sign;
  const H = sign.h / 2;
  const D = sign.d / 2;
  const xT = -L * tail;
  const xP = L - L * tail;
  const xS = xP - tip;
  const faceW = xS - xT; // text area: the rectangle behind the point
  const tipLen = Math.hypot(tip, H);
  const ox = cx + sign.x;

  const colors = useMemo(
    () => ({
      face: sign.color,
      top: shade(sign.color, SHADE.top),
      end: shade(sign.color, SHADE.end),
      tipUp: shade(sign.color, 0.92),
      tipLow: shade(sign.color, 0.74),
    }),
    [sign.color],
  );

  const kmText = sign.km !== undefined ? `${sign.km} km` : '';
  const nameW = nameFont.measureText(sign.name).width;
  const kmW = kmText ? kmFont.measureText(kmText).width : 0;

  const geo = useDerivedValue(() => {
    const yaw = sign.heading + heading.get();
    const p = (x: number, y: number, z: number) => project(x, y, z, yaw, ox, sign.y);
    const poly = (pts: P2[]) => {
      const b = Skia.PathBuilder.Make();
      b.moveTo(pts[0].x, pts[0].y);
      for (let i = 1; i < pts.length; i++) b.lineTo(pts[i].x, pts[i].y);
      b.close();
      return b.build();
    };
    const empty = Skia.PathBuilder.Make().build();

    const front = facing(0, 0, 1, yaw) > 0;
    const z = front ? D : -D;
    const face = poly([p(xT, H, z), p(xS, H, z), p(xP, 0, z), p(xS, -H, z), p(xT, -H, z)]);
    const top = facing(0, 1, 0, yaw) > 0 ? poly([p(xT, H, -D), p(xS, H, -D), p(xS, H, D), p(xT, H, D)]) : empty;
    const end = facing(-1, 0, 0, yaw) > 0 ? poly([p(xT, H, -D), p(xT, H, D), p(xT, -H, D), p(xT, -H, -D)]) : empty;
    let tipUp = empty;
    let tipLow = empty;
    if (tip > 0) {
      if (facing(H / tipLen, tip / tipLen, 0, yaw) > 0) tipUp = poly([p(xS, H, -D), p(xP, 0, -D), p(xP, 0, D), p(xS, H, D)]);
      if (facing(H / tipLen, -tip / tipLen, 0, yaw) > 0) tipLow = poly([p(xS, -H, -D), p(xP, 0, -D), p(xP, 0, D), p(xS, -H, D)]);
    } else if (facing(1, 0, 0, yaw) > 0) {
      // a square-ended block shows its right end instead of a point
      tipLow = poly([p(xP, H, -D), p(xP, H, D), p(xP, -H, D), p(xP, -H, -D)]);
    }

    // Text plane: the visible face's rectangle, mapped so it always reads left to right
    // (the back face starts at the shoulder, so the point stays on the text's far side).
    const a = front ? xT : xS;
    const b2 = front ? xS : xT;
    const m = rectToQuad(faceW, H * 2, p(a, H, z), p(b2, H, z), p(b2, -H, z), p(a, -H, z));
    // fade the lettering out as the board turns edge-on, where it would smear
    const f = Math.abs(facing(0, 0, 1, yaw));
    const ink = Math.max(0, Math.min(1, (f - 0.18) / 0.22));
    return { face, top, end, tipUp, tipLow, m, ink, front };
  });

  const face = useDerivedValue(() => geo.get().face);
  const top = useDerivedValue(() => geo.get().top);
  const end = useDerivedValue(() => geo.get().end);
  const tipUp = useDerivedValue(() => geo.get().tipUp);
  const tipLow = useDerivedValue(() => geo.get().tipLow);
  const matrix = useDerivedValue(() => geo.get().m);
  const ink = useDerivedValue(() => geo.get().ink);
  // the distance sits by the point: right on the front face, left on the back
  const kmX = useDerivedValue(() => (geo.get().front ? faceW - TYPE.kmInset - kmW : TYPE.kmInset));

  const nameY = H + nameFont.getSize() * 0.36 - (kmText ? 2 : 0);
  return (
    <Group>
      <Path path={top} color={colors.top} />
      <Path path={end} color={colors.end} />
      <Path path={tipLow} color={tip > 0 ? colors.tipLow : colors.end} />
      <Path path={tipUp} color={colors.tipUp} />
      <Path path={face} color={colors.face} />
      <Group matrix={matrix} opacity={ink}>
        {sign.named === false ? null : <SkiaText x={(faceW - nameW) / 2 + (sign.nameDx ?? 0)} y={nameY} text={sign.name} font={nameFont} color={sign.ink} />}
        {kmText ? <SkiaText x={kmX} y={H * 2 - 6} text={kmText} font={kmFont} color={sign.ink} /> : null}
      </Group>
    </Group>
  );
}
