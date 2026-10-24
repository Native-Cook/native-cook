import { useEffect, useMemo } from 'react';
import { StyleSheet, Text } from 'react-native';
import { Blur, BlurMask, Circle, Group, Image, Paint, Path, RadialGradient, RoundedRect, Skia, useImage, type SkImage, type SkPath } from '@shopify/react-native-skia';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { SIGNATURE, signatureBadgeGate, signatureCubic, signaturePulse, signatureTravel } from './BuzzStreaksSignature';
import { heroDayOneBadge, heroRewardPull, heroRewardTime } from './BuzzStreaksHero';

export type BuzzStreaksCoinImage = 'coin-pink5' | 'coin-gold1' | 'coin-gold2';
type Sprite = { size: number[]; rect: number[] };
type Coin = { id: string; image: BuzzStreaksCoinImage; frames: number[][] };
type Stroke = { points: number[][]; opacity: number };
type Badge = { id: string; text: string; size: number[]; frames: number[][]; fontSize: number; fillRGB: number[]; fillOpacity: number; appearance: number[][]; strokes: Stroke[][] };
type Wave = { points: number[][]; opacity: number; closed: boolean };
type Effects = {
  fps: number;
  images: Record<BuzzStreaksCoinImage, Sprite>;
  coins: Coin[];
  badges: Badge[];
  waves: Wave[][];
  waveStyle: { width: number; alphaGain: number; blur: number };
  glow: { rgb: number[]; frames: number[][] };
  strokeStyle: { rgb: number[]; width: number; glowRadius: number; glowOpacity: number };
};
type CachedWave = { path: SkPath; opacity: number };

const EFFECTS: Effects = require('./assets/effects.json');
const FPS = EFFECTS.fps;
const COIN_ART = {
  'coin-pink5': require('./assets/coin-pink5.png'),
  'coin-gold1': require('./assets/coin-gold1.png'),
  'coin-gold2': require('./assets/coin-gold2.png'),
};

function sample(rows: number[][], time: number) {
  'worklet';
  const f = Math.max(0, Math.min(rows.length - 1, time * FPS));
  const i = Math.floor(f), j = Math.min(rows.length - 1, i + 1), p = f - i;
  return rows[i].map((a, n) => a + (rows[j][n] - a) * p);
}

function cacheWaves() {
  const frames = EFFECTS.waves.map(waves => waves.map(wave => ({
    path: Skia.Path.Polygon(wave.points.map(([x, y]) => ({ x, y })), wave.closed), opacity: wave.opacity,
  })));
  const slots = Math.max(0, ...frames.map(frame => frame.length));
  return Array.from({ length: slots }, (_, slot) => frames.map(frame => frame[slot] ?? null));
}

function RewardWave({ rows, frame }: { rows: (CachedWave | null)[]; frame: SharedValue<number> }) {
  const empty = useMemo(() => Skia.Path.Make(), []);
  const path = useDerivedValue(() => rows[frame.get()]?.path ?? empty);
  const { width, alphaGain } = EFFECTS.waveStyle;
  const opacity = useDerivedValue(() => Math.min(1, (rows[frame.get()]?.opacity ?? 0) * alphaGain));
  return <Path path={path} color="white" opacity={opacity} style="stroke" strokeWidth={width} strokeCap="round" strokeJoin="round"/>;
}

/** The warm field and every measured open or closed white wave stay behind the characters. */
export function BuzzStreaksRewardBackground({ clock, signatureEnabled = false }: { clock: SharedValue<number>; signatureEnabled?: boolean }) {
  const waves = useMemo(() => cacheWaves(), []);
  const frameCount = EFFECTS.waves.length, glowRows = EFFECTS.glow.frames;
  const frame = useDerivedValue(() => Math.max(0, Math.min(frameCount - 1, Math.floor(heroRewardTime(clock.get(), signatureEnabled) * FPS + 1e-7))));
  const glow = useDerivedValue(() => sample(glowRows, heroRewardTime(clock.get(), signatureEnabled)));
  const center = useDerivedValue(() => ({ x: glow.get()[0], y: glow.get()[1] }));
  const radius = useDerivedValue(() => Math.max(.01, glow.get()[2]));
  const opacity = useDerivedValue(() => Math.max(0, Math.min(1, glow.get()[3])));
  const rgb = EFFECTS.glow.rgb;
  const colors = [new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, 1]),
    new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, 0])];
  return <Group>
    <Circle c={center} r={radius} opacity={opacity}><RadialGradient c={center} r={radius} colors={colors}/></Circle>
    <Group layer={<Paint><Blur blur={EFFECTS.waveStyle.blur}/></Paint>}>
      {waves.map((rows, slot) => <RewardWave key={slot} rows={rows} frame={frame}/>)}
    </Group>
  </Group>;
}

function RewardCoin({ coin, image, clock, signatureEnabled, characterEnabled }: { coin: Coin; image: SkImage | null; clock: SharedValue<number>; signatureEnabled: boolean; characterEnabled: boolean }) {
  const rows = coin.frames, sprite = EFFECTS.images[coin.image];
  const lockIndex = SIGNATURE.rewardLock.ids.indexOf(coin.id);
  const portal = SIGNATURE.portal.find(item => item.id === coin.id);
  const value = useDerivedValue(() => {
    const time = clock.get(), v = sample(rows, heroRewardTime(time, signatureEnabled));
    if (!signatureEnabled) return v;
    if (lockIndex >= 0) {
      const pull = heroRewardPull(time, true);
      v[0] += (lockIndex === 0 ? -1 : 1) * (characterEnabled ? 8 : 18) * pull;
      v[1] -= (characterEnabled ? 10 : 12) * pull;
      // These 1254px coin masters are drawn directly by Skia; keep them upright
      // while the reward takes focus, then recover the measured radius exactly.
      v[2] *= 1 + (characterEnabled ? .10 : .18) * Math.max(0, pull);
    }
    if (portal && time >= portal.start && time <= portal.end) {
      const p = signatureTravel(time, portal);
      v[0] = signatureCubic(portal.points, p, 0);
      v[1] = signatureCubic(portal.points, p, 1);
    }
    // Source alpha and sprite crop stay measured; all transforms settle by3.68s.
    return v;
  });
  const opacity = useDerivedValue(() => {
    const v = value.get();
    return v[2] > .01 && v[3] > .001 ? Math.max(0, Math.min(1, v[3])) : 0;
  });
  const rect = useDerivedValue(() => {
    const v = value.get(), r = Math.max(.01, v[2]);
    const sx = 2 * r / sprite.rect[2], sy = 2 * r / sprite.rect[3];
    return { x: v[0] - r - sprite.rect[0] * sx, y: v[1] - r - sprite.rect[1] * sy,
      width: sprite.size[0] * sx, height: sprite.size[1] * sy };
  });
  const glossOpacity = useDerivedValue(() => {
    if (!signatureEnabled || lockIndex < 0) return 0;
    return opacity.get() * SIGNATURE.rewardLock.reflectionAlpha
      * signaturePulse(clock.get(), 3.04, 3.58);
  });
  const glossCenter = useDerivedValue(() => ({ x: value.get()[0], y: value.get()[1] - value.get()[2] * .42 }));
  const glossRadius = useDerivedValue(() => Math.max(.01, value.get()[2] * .36));
  const glossClip = useDerivedValue(() => {
    const v = value.get(), r = Math.max(.01, v[2]);
    return { rect: { x: v[0] - r, y: v[1] - r, width: 2 * r, height: 2 * r }, rx: r, ry: r };
  });
  return <Group>
    <Image image={image} rect={rect} fit="fill" opacity={opacity}/>
    {signatureEnabled && lockIndex >= 0 && <Group clip={glossClip} opacity={glossOpacity}>
      <Circle c={glossCenter} r={glossRadius}><RadialGradient c={glossCenter} r={glossRadius} colors={['white', '#ffffff00']}/></Circle>
    </Group>}
  </Group>;
}

function BadgeStroke({ rows, frame }: { rows: (CachedWave | null)[]; frame: SharedValue<number> }) {
  const empty = useMemo(() => Skia.Path.Make(), []);
  const path = useDerivedValue(() => rows[frame.get()]?.path ?? empty);
  const opacity = useDerivedValue(() => rows[frame.get()]?.opacity ?? 0);
  const { rgb, width, glowRadius, glowOpacity } = EFFECTS.strokeStyle;
  const glow = useDerivedValue(() => opacity.get() * glowOpacity);
  const color = new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, 1]);
  return <Group>
    <Path path={path} color={color} opacity={glow} style="stroke" strokeWidth={width} strokeCap="round" strokeJoin="round"><BlurMask blur={glowRadius} style="normal"/></Path>
    <Path path={path} color={color} opacity={opacity} style="stroke" strokeWidth={width} strokeCap="round" strokeJoin="round"/>
  </Group>;
}

function RewardBadgeGraphics({ badge, clock, signatureEnabled }: { badge: Badge; clock: SharedValue<number>; signatureEnabled: boolean }) {
  const rows = badge.frames, appearances = badge.appearance;
  const value = useDerivedValue(() => sample(rows, heroRewardTime(clock.get(), signatureEnabled)));
  const appearance = useDerivedValue(() => sample(appearances, heroRewardTime(clock.get(), signatureEnabled)));
  const rect = useDerivedValue(() => {
    const v = value.get(), a = appearance.get();
    return { rect: { x: v[0] - a[1] / 2, y: v[1] - a[2] / 2, width: a[1], height: a[2] }, rx: a[3], ry: a[3] };
  });
  const fillOpacity = badge.fillOpacity;
  const opacity = useDerivedValue(() => value.get()[2] > .001 ? Math.max(0, Math.min(1, value.get()[2])) * fillOpacity : 0);
  const rgb = badge.fillRGB, color = new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, 1]);
  const strokes = useMemo(() => {
    const frames = badge.strokes.map(frame => frame.map(stroke => ({
      path: Skia.Path.Polygon(stroke.points.map(([x, y]) => ({ x, y })), false), opacity: stroke.opacity,
    })));
    return Array.from({ length: Math.max(0, ...frames.map(frame => frame.length)) }, (_, slot) => frames.map(frame => frame[slot] ?? null));
  }, [badge]);
  const count = badge.strokes.length;
  const frame = useDerivedValue(() => Math.max(0, Math.min(count - 1, Math.floor(heroRewardTime(clock.get(), signatureEnabled) * FPS + 1e-7))));
  const gate = useDerivedValue(() => signatureBadgeGate(clock.get(), badge.id, signatureEnabled) * (badge.id === 'day1' ? heroDayOneBadge(clock.get(), signatureEnabled) : 1));
  return <Group opacity={gate}>
    <RoundedRect rect={rect} color={color} opacity={opacity}/>
    {!(signatureEnabled && badge.id === 'day1') && strokes.map((slot, i) => <BadgeStroke key={i} rows={slot} frame={frame}/>)}
  </Group>;
}

/** The measured coin array is also its draw order, including the separate duplicated flights. */
export function BuzzStreaksRewards({ clock, onReady, signatureEnabled = false, characterEnabled = false }: { clock: SharedValue<number>; onReady: (kind: BuzzStreaksCoinImage) => void; signatureEnabled?: boolean; characterEnabled?: boolean }) {
  const pink = useImage(COIN_ART['coin-pink5']);
  const gold1 = useImage(COIN_ART['coin-gold1']);
  const gold2 = useImage(COIN_ART['coin-gold2']);
  useEffect(() => {
    if (pink) onReady('coin-pink5');
    if (gold1) onReady('coin-gold1');
    if (gold2) onReady('coin-gold2');
  }, [pink, gold1, gold2, onReady]);
  const images = { 'coin-pink5': pink, 'coin-gold1': gold1, 'coin-gold2': gold2 };
  return <Group>{EFFECTS.coins.map(coin => <RewardCoin key={coin.id} coin={coin} image={images[coin.image]} clock={clock} signatureEnabled={signatureEnabled} characterEnabled={characterEnabled}/>)}
    {EFFECTS.badges.map(badge => <RewardBadgeGraphics key={badge.id} badge={badge} clock={clock} signatureEnabled={signatureEnabled}/>)}</Group>;
}

function RewardBadge({ badge, clock, signatureEnabled }: { badge: Badge; clock: SharedValue<number>; signatureEnabled: boolean }) {
  const rows = badge.frames, appearances = badge.appearance;
  const style = useAnimatedStyle(() => {
    const time = clock.get(), sourceTime = heroRewardTime(time, signatureEnabled);
    const v = sample(rows, sourceTime), a = sample(appearances, sourceTime);
    return { opacity: (v[2] > .001 ? Math.max(0, Math.min(1, v[2])) : 0) * signatureBadgeGate(time, badge.id, signatureEnabled) * (badge.id === 'day1' ? heroDayOneBadge(time, signatureEnabled) : 1),
      transform: [{ translateX: v[0] - 100 }, { translateY: v[1] - 10 }, { scale: a[0] }] };
  });
  return <Animated.View accessibilityElementsHidden pointerEvents="none" style={[s.badge, style]}><Text style={[s.label, { fontSize: badge.fontSize }]}>{badge.text}</Text></Animated.View>;
}

/** Native text uses the same loaded Gilroy face as the rest of the screen. */
export function BuzzStreaksRewardBadges({ clock, signatureEnabled = false }: { clock: SharedValue<number>; signatureEnabled?: boolean }) {
  return <>{EFFECTS.badges.map(badge => <RewardBadge key={badge.id} badge={badge} clock={clock} signatureEnabled={signatureEnabled}/>)}</>;
}

const s = StyleSheet.create({
  badge: { position: 'absolute', left: 0, top: 0, width: 200, height: 20, alignItems: 'center', justifyContent: 'center' },
  label: { fontFamily: 'BuzzExtraBold', lineHeight: 20, color: 'white', includeFontPadding: false },
});
