// Prize-to-hero handover, alpha-masked gloss, travelling badge and UI-thread chrome count.
import { Blur, ColorMatrix, Group, Image, ImageShader, LinearGradient, Rect, Shader, Skia,
  type SkFont, type SkImage, Text } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { WheelMotion } from './wheel-contract';
import { badgePose, HERO_SKSL, printedPrizeOpacity, prizePose, RAYS_SKSL, tone, type ScreenLayout } from './wheel-theme';

const raysEffect = Skia.RuntimeEffect.Make(RAYS_SKSL);
const heroEffect = Skia.RuntimeEffect.Make(HERO_SKSL);
if (!raysEffect || !heroEffect) throw new Error('Daily Spin: reveal shader did not compile.');
const blackAlpha = [0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0, 0,0,0,1,0];

type Props = { layout: ScreenLayout; motion: WheelMotion; clock: SharedValue<number>; reduced: boolean };

export function WheelRays({ layout: l, motion, clock, reduced }: Props) {
  const { rays } = motion;
  const uniforms = useDerivedValue(() => ({ centre: [l.heroX, l.heroY], radius: l.radius,
    presence: rays.get(), clock: reduced ? 0 : clock.get() }));
  return <Rect x={l.heroX - l.radius * 1.7} y={l.heroY - l.radius * 1.7}
    width={l.radius * 3.4} height={l.radius * 3.4} blendMode="plus">
    <Shader source={raysEffect!} uniforms={uniforms} />
  </Rect>;
}

export function WheelReveal({ layout: l, motion, clock, reduced, prizes, badges, font }: Props & {
  prizes: SkImage[]; badges: SkImage; font: SkFont;
}) {
  const { winner, theta, lift, wheelScale, swayA, swayB, tiltX, tiltY, hero, heroSquash,
    heroX, heroY, heroTilt, collect, winnerPrize, badge, caption, count, phase } = motion;
  const values = useDerivedValue(() => ({ winner: winner.get(), theta: theta.get(), lift: lift.get(),
    wheelScale: wheelScale.get(), swayA: swayA.get(), swayB: swayB.get(), tiltX: tiltX.get(), tiltY: tiltY.get(),
    hero: hero.get(), heroSquash: heroSquash.get(), heroX: heroX.get(), heroY: heroY.get(),
    heroTilt: heroTilt.get(), collect: collect.get(), time: clock.get(), reduced }));
  const pose = useDerivedValue(() => prizePose(l, values.get()));
  const image = useDerivedValue(() => prizes[Math.max(0, winner.get())]);
  const printedPrize = useDerivedValue(() => printedPrizeOpacity(phase.get(), heroSquash.get(), winnerPrize.get()));
  const presence = useDerivedValue(() => winner.get() < 0 ? 0 : (1 - printedPrize.get()) * pose.get().opacity);
  const transform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x }, { translateY: p.y + p.baseShift },
      { scaleX: p.sx }, { scaleY: p.sy }, { rotate: p.angle }, { scale: p.side }];
  });
  const shadowTransform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x + 6 * l.k }, { translateY: p.y + (8 + 12 * hero.get()) * l.k },
      { scaleX: p.sx }, { scaleY: p.sy }, { rotate: p.angle }, { scale: p.side * (1 + 0.04 * hero.get()) }];
  });
  const shadowOpacity = useDerivedValue(() => presence.get() * 0.3);
  const shadowBlur = useDerivedValue(() => (2 + 9 * hero.get()) * l.k / Math.max(1, pose.get().side));
  const gloss = useDerivedValue(() => ({ clock: clock.get(), gloss: reduced ? 0 : Math.max(0, hero.get()) }));
  const badgePlacement = useDerivedValue(() => badgePose(l, values.get(), badge.get()));
  const badgeTransform = useDerivedValue(() => {
    const p = badgePlacement.get();
    return [{ translateX: p.x }, { translateY: p.y }, { scaleX: p.sx }, { scaleY: p.sy },
      { rotate: p.angle }, { scale: p.size }];
  });
  const badgeX = useDerivedValue(() => -Math.max(0, winner.get()) - 0.5);
  const badgeOpacity = useDerivedValue(() => winner.get() < 0 ? 0
    : (1 - printedPrize.get()) * badgePlacement.get().opacity * (1 - collect.get()));
  const numeral = useDerivedValue(() => `×${Math.max(0, Math.round(count.get()))}`);
  const textX = useDerivedValue(() => {
    const widths = font.getGlyphWidths(font.getGlyphIDs(numeral.get()));
    let width = 0;
    for (let i = 0; i < widths.length; i++) width += widths[i];
    return l.cx - width / 2;
  });
  const baseline = l.captionTop + (l.numeralLine - l.numeralSize) / 2 - font.getMetrics().ascent;
  return <>
    <Group opacity={shadowOpacity} transform={shadowTransform}>
      <Image image={image} x={-0.5} y={-0.5} width={1} height={1} fit="fill">
        <ColorMatrix matrix={blackAlpha} /><Blur blur={shadowBlur} />
      </Image>
    </Group>
    <Group opacity={presence} transform={transform}>
      <Rect x={-0.5} y={-0.5} width={1} height={1}>
        <Shader source={heroEffect!} uniforms={gloss}><ImageShader image={image} fit="none" /></Shader>
      </Rect>
    </Group>
    <Group opacity={badgeOpacity} transform={badgeTransform} clip={{ x: -0.5, y: -0.5, width: 1, height: 1 }}>
      <Image image={badges} x={badgeX} y={-0.5} width={6} height={1} fit="fill" />
    </Group>
    <Group opacity={caption}>
      <Text text={numeral} x={textX} y={baseline} font={font} color="#F4F5FF" style="stroke" strokeWidth={0.65 * l.fontScale} />
      <Text text={numeral} x={textX} y={baseline} font={font}>
        <LinearGradient start={{ x: 0, y: l.captionTop + 8 * l.fontScale }}
          end={{ x: 0, y: l.captionTop + 56 * l.fontScale }} colors={tone.chrome} positions={tone.stops} />
      </Text>
    </Group>
  </>;
}
