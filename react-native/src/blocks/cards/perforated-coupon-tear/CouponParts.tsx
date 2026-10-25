import {
  Circle,
  Fill,
  DashPathEffect,
  Glyphs,
  Group,
  LinearGradient,
  Path,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  type SkFont,
  type SkPath,
  type SkPoint,
  type SkRuntimeEffect,
  vec,
} from '@shopify/react-native-skia';
import { type ReactNode, useMemo } from 'react';
import type { SharedValue } from 'react-native-reanimated';

import { barcodePath, cupHandlePath, cupPath, holesPath } from './couponArt';
import { CARD_H, CORNER } from './couponMotion';

/** The coupon's static pieces: palette, tracked labels, the stub's face, the stamp card row and the wallet. */

export const IOS = process.env.EXPO_OS === 'ios';
export const SANS = IOS ? 'Helvetica Neue' : 'sans-serif';
export const MONO = IOS ? 'Menlo' : 'monospace';

export const PAPER = ['#F6EFE3', '#EFE4D2', '#E6D5BE'];
export const BODY = ['#4E3122', '#3E271B', '#2F1D14'];
export const STUB = ['#D9A066', '#C98B4E', '#B37638'];
export const CREAM = '#F4E8D4';
export const ROAST = '#3A2418';
export const LEATHER = ['#2F5A49', '#24473A', '#1A352B'];
export const INK = [0.93, 0.38, 0.28, 0.95];

/** Glyphs for `text` with extra tracking, measured with the font's own widths. */
export function tracked(font: SkFont, text: string, x: number, y: number, tracking: number, align = 0) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const total = widths.reduce((a, b) => a + b, 0) + tracking * (ids.length - 1);
  let cx = x - total * align;
  return ids.map((id, i) => {
    const g = { id, pos: vec(cx, y) };
    cx += widths[i] + tracking;
    return g;
  });
}

export function labelWidth(font: SkFont, text: string, tracking: number) {
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  return ws.reduce((a, b) => a + b, 0) + tracking * (ws.length - 1);
}

export type Fonts = { free: SkFont; small: SkFont; label: SkFont; mono: SkFont; stamp: SkFont; tiny: SkFont };

/** The stub's face: caramel paper, its torn fibres, a barcode and the "tear" label. */
export function StubFace({
  path,
  fibres,
  px,
  cw,
  fonts,
  filed = false,
}: {
  path: SkPath | SharedValue<SkPath>;
  fibres: SkPath | SharedValue<SkPath>;
  px: number;
  cw: number;
  fonts: Fonts;
  /** In the wallet the stub's path already has its half holes cut out, and it casts a shadow. */
  filed?: boolean;
}) {
  const bars = useMemo(() => barcodePath(px + 46, 34, 48, 116), [px]);
  const holes = useMemo(() => holesPath(px), [px]);
  const label = useMemo(() => tracked(fonts.label, 'TEAR TO REDEEM', 0, 0, 2.2, 0.5), [fonts.label]);
  const no = useMemo(() => tracked(fonts.mono, 'No. 0925', px + 46, 176, 0.4), [fonts.mono, px]);
  return (
    <Group>
      <Path path={path}>
        <LinearGradient start={vec(px, 0)} end={vec(cw, CARD_H)} colors={STUB} />
        {filed ? <Shadow dx={0} dy={3} blur={6} color="rgba(8,18,14,0.5)" /> : null}
      </Path>
      <Path path={path} style="stroke" strokeWidth={1} color="rgba(255,240,215,0.35)" />
      <Path path={fibres} style="stroke" strokeWidth={0.9} strokeCap="round" color="rgba(255,246,230,0.85)" />
      <Path path={bars} color="rgba(58,36,24,0.82)" />
      <Group transform={[{ translateX: px + 26 }, { translateY: CARD_H / 2 }, { rotate: -Math.PI / 2 }]}>
        <Glyphs font={fonts.label} glyphs={label} color="rgba(58,36,24,0.78)" />
      </Group>
      <Glyphs font={fonts.mono} glyphs={no} color="rgba(58,36,24,0.7)" />
      {filed ? null : <Path path={holes} color={PAPER[1]} />}
    </Group>
  );
}

/** Ten filled stamps across the top of the card, each a coffee-brown disc with a cream cup. */
export function StampRow({ x, width, y }: { x: number; width: number; y: number }) {
  const stamps = useMemo(() => {
    const d = Math.min(28, (width - 9 * 7) / 10);
    return Array.from({ length: 10 }, (_, i) => ({ x: x + d / 2 + i * (d + (width - 10 * d) / 9), r: d / 2 }));
  }, [x, width]);
  const cups = useMemo(() => ({ body: cupPath(0, 0), handle: cupHandlePath(0, 0) }), []);
  return (
    <Group>
      {stamps.map((s, i) => (
        <Group key={i}>
          <Circle cx={s.x} cy={y} r={s.r}>
            <LinearGradient start={vec(s.x, y - s.r)} end={vec(s.x, y + s.r)} colors={['#6B4430', '#5A3826', '#47291B']} />
            <Shadow dx={0} dy={1} blur={1.5} color="rgba(60,34,20,0.3)" />
          </Circle>
          <Group transform={[{ translateX: s.x }, { translateY: y }, { scale: s.r / 14 + 0.05 }]}>
            <Path path={cups.body} color={CREAM} />
            <Path path={cups.handle} style="stroke" strokeWidth={1.3} color={CREAM} />
          </Group>
        </Group>
      ))}
    </Group>
  );
}

/**
 * The leather wallet: a dark back panel, then whatever is filed in it (children, clipped to the
 * wallet's bottom), then the stitched front pocket with its curved mouth.
 */
export function Wallet({
  left,
  top,
  width,
  bottom,
  front,
  grain,
  font,
  children,
}: {
  left: number;
  top: number;
  width: number;
  bottom: number;
  front: number;
  grain: SkRuntimeEffect;
  font: SkFont;
  children: ReactNode;
}) {
  const frontPath = useMemo(() => {
    const r = left + width;
    const b = Skia.PathBuilder.Make();
    b.moveTo(left, front + 8);
    b.quadTo(left + width / 2, front + 26, r, front + 8);
    b.lineTo(r, bottom - 16);
    b.quadTo(r, bottom, r - 16, bottom);
    b.lineTo(left + 16, bottom);
    b.quadTo(left, bottom, left, bottom - 16);
    b.close();
    return b.build();
  }, [left, width, front, bottom]);
  const label = useMemo(() => tracked(font, 'REWARDS WALLET', left + width / 2, bottom - 22, 2.4, 0.5), [font, left, width, bottom]);
  return (
    <Group>
      <RoundedRect x={left} y={top} width={width} height={bottom - top} r={18}>
        <LinearGradient start={vec(0, top)} end={vec(0, bottom)} colors={['#1F3F33', '#173126', '#10241C']} />
        <Shadow dx={0} dy={14} blur={28} color="rgba(40,26,14,0.22)" />
        <Shadow dx={0} dy={2} blur={3} color="rgba(40,26,14,0.3)" />
      </RoundedRect>
      {children}
      <Path path={frontPath}>
        <LinearGradient start={vec(0, front)} end={vec(left + width * 0.3, bottom)} colors={LEATHER} />
        <Shadow dx={0} dy={-3} blur={6} color="rgba(10,20,16,0.45)" />
      </Path>
      <Path path={frontPath} blendMode="softLight">
        <Shader source={grain} uniforms={{ amount: 0.32 }} />
      </Path>
      <Path path={frontPath} style="stroke" strokeWidth={1.2} color="rgba(214,190,140,0.55)">
        <DashPathEffect intervals={[5, 4]} />
      </Path>
      <Glyphs font={font} glyphs={label} color="rgba(214,190,140,0.75)" />
    </Group>
  );
}

/** The coupon's body: espresso card, faint bean pattern, grain, its copy and the perforation holes. */
export function CouponBody({
  body,
  fibres,
  px,
  fonts,
  glyphs,
  beans,
  holes,
  grain,
}: {
  body: SkPath | SharedValue<SkPath>;
  fibres: SkPath | SharedValue<SkPath>;
  px: number;
  fonts: Fonts;
  glyphs: Record<'reward' | 'exp' | 'free' | 'any' | 'code' | 'codeNo', { id: number; pos: SkPoint }[]>;
  beans: SkPath;
  holes: SkPath;
  grain: SkRuntimeEffect;
}) {
  const rule = useMemo(() => Skia.PathBuilder.Make().moveTo(22, 150).lineTo(px - 20, 150).build(), [px]);
  return (
    <Group>
      <Path path={body}>
        <LinearGradient start={vec(0, 0)} end={vec(px, CARD_H)} colors={BODY} />
        <Shadow dx={0} dy={16} blur={26} color="rgba(46,26,14,0.26)" />
        <Shadow dx={0} dy={1.5} blur={3} color="rgba(46,26,14,0.35)" />
      </Path>
      <Group clip={body}>
        <Path path={beans} style="stroke" strokeWidth={1} color="rgba(244,232,212,0.06)" />
        <Fill blendMode="softLight">
          <Shader source={grain} uniforms={{ amount: 0.55 }} />
        </Fill>
        <RoundedRect x={0.5} y={0.5} width={px + CORNER} height={CARD_H - 1} r={CORNER} style="stroke" strokeWidth={1} color="rgba(255,236,205,0.14)" />
      </Group>
      <Path path={fibres} style="stroke" strokeWidth={0.9} strokeCap="round" color="rgba(255,240,218,0.8)" />
      <Glyphs font={fonts.label} glyphs={glyphs.reward} color="rgba(244,232,212,0.6)" />
      <Glyphs font={fonts.label} glyphs={glyphs.exp} color="rgba(244,232,212,0.6)" />
      <Glyphs font={fonts.free} glyphs={glyphs.free} color={CREAM} />
      <Glyphs font={fonts.small} glyphs={glyphs.any} color="rgba(244,232,212,0.78)" />
      <Path path={rule} style="stroke" strokeWidth={1} color="rgba(244,232,212,0.22)">
        <DashPathEffect intervals={[3, 3]} />
      </Path>
      <Glyphs font={fonts.tiny} glyphs={glyphs.code} color="rgba(244,232,212,0.55)" />
      <Glyphs font={fonts.mono} glyphs={glyphs.codeNo} color={CREAM} />
      <Path path={holes} color={PAPER[1]} />
    </Group>
  );
}
