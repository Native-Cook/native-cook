import { type ReactNode, useCallback } from 'react';
import { Share, StyleSheet, Text, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { MetalPill } from './MetalPill';
import { useVaultGesture } from './use-vault-gesture';
import { type VaultMotion } from './use-vault-motion';
import { type BadgeDef, UNLOCK_DATE } from './vault-contract';
import { type Box, type VaultLayout } from './vault-layout';
import { clamp, ease } from './vault-motion';
import { type HeroVisual } from './vault-state';
import { colour, enamelLabel, textStyles, tone } from './vault-theme';

function Reveal({ hero, at, reduced, children, box }: { hero: SharedValue<HeroVisual>; at: number;
  reduced: boolean; children: ReactNode; box: Box }) {
  const style = useAnimatedStyle(() => {
    const v = hero.get(); const t = v.age - (reduced ? 0 : at);
    return { opacity: clamp(t / (reduced ? 200 : 240)) * v.details,
      transform: [{ translateY: reduced ? 0 : 12 * (1 - ease(t / 420)) }] };
  });
  return <Animated.View style={[{ position: 'absolute', left: box.x, top: box.y, width: box.width, height: box.height }, style]}>{children}</Animated.View>;
}
const ignoreShareDismissal = () => {};
export function BadgeDetails({ badge, layout, motion }: { badge: BadgeDef; layout: VaultLayout; motion: VaultMotion }) {
  const { hero, reduced, close } = motion;
  const progress = motion.snapshot[badge.tile].have;
  const gesture = useVaultGesture(motion, layout);
  const share = useCallback(() => {
    void Share.share({ title: `${badge.name} · Badge Vault`, message: `${badge.name}\n${badge.blurb}\nEarned ${badge.earned ?? UNLOCK_DATE} · Badge Vault` }).catch(ignoreShareDismissal);
  }, [badge]);
  return <GestureDetector gesture={gesture}>
    <View style={StyleSheet.absoluteFill} accessibilityViewIsModal importantForAccessibility="yes">
      <Reveal hero={hero} reduced={reduced} at={520} box={layout.close}>
        <MetalPill width={36} height={36} label="Close" close onPress={close}
          tint={[0.09, 0.09, 0.10]} labelColour="rgba(255,255,255,0.92)" />
      </Reveal>
      <Reveal hero={hero} reduced={reduced} at={520} box={layout.chip}>
        <View style={styles.chipRow}>
          <View style={[styles.chip, { backgroundColor: colour(badge.a, 0.14), borderColor: colour(badge.a, 0.35) }]}>
            <Text numberOfLines={1} style={[styles.chipText, { color: colour(badge.a, 1, 0.35) }]}>{badge.rarity}% OF MEMBERS HOLD THIS</Text>
          </View>
        </View>
      </Reveal>
      <View accessible accessibilityRole="header" accessibilityLabel={badge.name} pointerEvents="none"
        style={{ position: 'absolute', left: layout.title.x, top: layout.title.y, width: layout.title.width, height: layout.title.height }} />
      <Reveal hero={hero} reduced={reduced} at={640} box={layout.earned}>
        <Text style={[styles.earned, textStyles.number]}>Earned {badge.earned ?? UNLOCK_DATE}</Text>
      </Reveal>
      <Reveal hero={hero} reduced={reduced} at={700} box={layout.blurb}>
        <Text numberOfLines={2} style={styles.blurb}>{badge.blurb}</Text>
      </Reveal>
      {badge.stats.map((stat, i) => <Reveal key={stat.label} hero={hero} reduced={reduced} at={760 + i * 40}
        box={layout.statColumns[i]}>
        <View style={[styles.stat, i > 0 && styles.statDivider]}>
          <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8} style={[styles.statValue, textStyles.number]}>
            {badge.goal && (badge.id === 'iron-month' || badge.id === 'crowned') && i < 2 ?
              String(i === 0 ? progress : Math.max(0, badge.goal.need - progress)) : stat.value}
          </Text>
          <Text numberOfLines={1} style={styles.statLabel}>{stat.label}</Text>
        </View>
      </Reveal>)}
      {layout.hint && <Reveal hero={hero} reduced={reduced} at={900} box={layout.hint}>
        <Text numberOfLines={1} style={styles.hint}>{reduced ? 'Swipe down to put it back' : 'Drag to turn  ·  Swipe down to put it back'}</Text>
      </Reveal>}
      <Reveal hero={hero} reduced={reduced} at={960} box={layout.share}>
        <MetalPill width={layout.share.width} height={56} label="Share badge" fontSize={17} onPress={share}
          tint={badge.a} labelColour={enamelLabel(badge.a)} />
      </Reveal>
    </View>
  </GestureDetector>;
}
const styles = StyleSheet.create({
  chipRow: { alignItems: 'center' },
  chip: { height: 28, borderRadius: 14, paddingHorizontal: 12, borderWidth: StyleSheet.hairlineWidth, justifyContent: 'center' },
  chipText: { fontVariant: ['tabular-nums'], fontSize: 11, fontWeight: '600', letterSpacing: 0.9 },
  earned: { color: tone.secondary, fontSize: 15, lineHeight: 20, textAlign: 'center' },
  blurb: { color: 'rgba(235,235,245,0.78)', fontSize: 15, lineHeight: 22, textAlign: 'center' },
  stat: { height: 44, marginTop: 6, alignItems: 'center', paddingHorizontal: 4 },
  statDivider: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: tone.hairline },
  statValue: { color: tone.ink, fontSize: 20, lineHeight: 26, fontWeight: '600' },
  statLabel: { color: tone.tertiary, fontSize: 12, lineHeight: 16 },
  hint: { color: tone.tertiary, fontSize: 12, lineHeight: 16, textAlign: 'center' },
});
