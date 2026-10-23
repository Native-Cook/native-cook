import { Image } from 'expo-image';
import { View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Defs, Ellipse, RadialGradient, Rect, Stop } from 'react-native-svg';

import { type Change, COIN, coinAt, coinFrame, SHEEN } from './motion';
import { sprite } from './theme';

/**
 * The two badges are Blender renders (the build script is not kept; the sheets are final): a silver
 * coin with a mint face and a chrome 3 (and, back to back, a sky-blue 7; a gold 1 for the second
 * sequence), and a frosted pastel gem. Both are sprite sheets: one canvas per frame, side by side.
 */
const COIN_SHEET = require('./assets/coin.webp');
const GEM_SHEET = require('./assets/gem.webp');

type Geo = { U: number; y: (v: number) => number };

/** Pixel digits of the streak badge: 6 × 7 cells of 6 ref px, 1 = ink. */
const PIXEL: Record<string, string[]> = {
  '3': ['111111', '000011', '000011', '011111', '000011', '000011', '111111'],
  '7': ['111111', '000011', '000110', '001100', '001100', '011000', '011000'],
};

function Sheet({ source, w, h, frames, U }: { source: number; w: number; h: number; frames: number; U: number }) {
  return <Image source={source} contentFit="fill" style={{ width: w * frames * U, height: h * U }} />;
}

export function Coin({ change, geo }: { change: SharedValue<Change>; geo: Geo }) {
  const { U, y } = geo;
  const s = sprite.coin;
  const body = useAnimatedStyle(() => {
    const c = coinAt(change.get());
    return {
      transform: [{ translateY: -COIN.lift * U * c.lift }, { scale: 1 + COIN.grow * c.lift }],
    };
  });
  const strip = useAnimatedStyle(() => {
    const c = coinAt(change.get());
    return { transform: [{ translateX: -coinFrame(c.angle, c.back) * s.w * U }] };
  });
  const shadow = useAnimatedStyle(() => {
    const c = coinAt(change.get());
    return { opacity: COIN.shadow * c.lift, transform: [{ scaleX: 1 - 0.25 * c.lift }] };
  });
  const sh = sprite.coinShadow;
  return (
    <>
      <Animated.View
        pointerEvents="none"
        style={[{ position: 'absolute', left: (sh.cx - sh.w / 2) * U, top: y(sh.cy - sh.h / 2), width: sh.w * U, height: sh.h * U }, shadow]}
      >
        <Svg width="100%" height="100%" viewBox="0 0 2 2" preserveAspectRatio="none">
          <Defs>
            <RadialGradient id="coinShadow" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor="#000" stopOpacity="1" />
              <Stop offset="1" stopColor="#000" stopOpacity="0" />
            </RadialGradient>
          </Defs>
          <Ellipse cx="1" cy="1" rx="1" ry="1" fill="url(#coinShadow)" />
        </Svg>
      </Animated.View>
      <Animated.View
        pointerEvents="none"
        style={[{ position: 'absolute', left: s.x * U, top: y(s.y), width: s.w * U, height: s.h * U, overflow: 'hidden' }, body]}
      >
        <Animated.View style={strip}>
          <Sheet source={COIN_SHEET} w={s.w} h={s.h} frames={s.frames} U={U} />
        </Animated.View>
      </Animated.View>
    </>
  );
}

/** The gem: two frames of its sheen crossfaded, then the pixel 37 on top. */
export function Gem({ change, geo }: { change: SharedValue<Change>; geo: Geo }) {
  const { U, y } = geo;
  const s = sprite.gem;
  const a = useAnimatedStyle(() => {
    const k = Math.floor(change.get().sheen) % SHEEN.frames;
    return { transform: [{ translateX: -k * s.w * U }] };
  });
  const b = useAnimatedStyle(() => {
    const p = change.get().sheen;
    const k = (Math.floor(p) + 1) % SHEEN.frames;
    return { opacity: p - Math.floor(p), transform: [{ translateX: -k * s.w * U }] };
  });
  const box = { position: 'absolute' as const, left: s.x * U, top: y(s.y), width: s.w * U, height: s.h * U, overflow: 'hidden' as const };
  const cell = 6;
  const digit = (ch: string, ox: number, oy: number) =>
    PIXEL[ch].flatMap((row, r) =>
      row.split('').map((v, c) => (v === '1' ? <Rect key={`${ch}${ox}${r}${c}`} x={ox + c * cell} y={oy + r * cell} width={cell} height={cell} fill="#000" /> : null)),
    );
  return (
    <>
      <View pointerEvents="none" style={box}>
        <Animated.View style={a}>
          <Sheet source={GEM_SHEET} w={s.w} h={s.h} frames={s.frames} U={U} />
        </Animated.View>
      </View>
      <View pointerEvents="none" style={box}>
        <Animated.View style={b}>
          <Sheet source={GEM_SHEET} w={s.w} h={s.h} frames={s.frames} U={U} />
        </Animated.View>
      </View>
      <View pointerEvents="none" style={box}>
        <Svg width={s.w * U} height={s.h * U} viewBox={`0 0 ${s.w} ${s.h}`}>
          {digit('3', 61, 63)}
          {digit('7', 110, 63)}
        </Svg>
      </View>
    </>
  );
}
