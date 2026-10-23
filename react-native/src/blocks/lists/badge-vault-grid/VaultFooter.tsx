import { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { MetalPill } from './MetalPill';
import { type VaultMotion } from './use-vault-motion';
import { type VaultLayout } from './vault-layout';
import { textStyles, tone } from './vault-theme';

function RollingCount({ count, reduced }: { count: number; reduced: boolean }) {
  const progress = useSharedValue(count === 15 || reduced ? 1 : 0);
  useEffect(() => { progress.set(withTiming(1, { duration: 260, easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: ReduceMotion.Never })); }, [progress]);
  const oldStyle = useAnimatedStyle(() => ({ opacity: 1 - progress.get(), transform: [{ translateY: -28 * progress.get() }] }));
  const newStyle = useAnimatedStyle(() => ({ opacity: progress.get(), transform: [{ translateY: 28 * (1 - progress.get()) }] }));
  return <View style={styles.countWindow} accessible accessibilityLabel={`${count} of 18 collected`}>
    <Animated.Text accessibilityElementsHidden style={[styles.count, textStyles.number, oldStyle]}>{Math.max(15, count - 1)} of 18</Animated.Text>
    <Animated.Text accessibilityElementsHidden style={[styles.count, textStyles.number, newStyle]}>{count} of 18</Animated.Text>
  </View>;
}
export function VaultFooter({ count, layout, motion }: { count: number; layout: VaultLayout; motion: VaultMotion }) {
  const remaining = 18 - count;
  return <View style={{ height: layout.footerHeight, width: layout.width, marginLeft: -16 }}>
    <View style={styles.hairline} />
    <View style={styles.row}>
      <View>
        <RollingCount key={count} count={count} reduced={motion.reduced} />
        <Text numberOfLines={1} style={styles.caption}>{remaining ? `collected · ${remaining} in progress` : 'collected · all done'}</Text>
      </View>
      <MetalPill width={132} height={48} label={remaining ? 'Log a day' : 'All collected'} disabled={!remaining}
        tint={[0.96, 0.95, 0.92]} labelColour={remaining ? '#0B0B0C' : tone.secondary}
        onPress={motion.log} press={motion.pillPress} onPressIn={motion.pillIn} onPressOut={motion.pillOut} />
    </View>
  </View>;
}
const styles = StyleSheet.create({
  hairline: { position: 'absolute', left: 16, right: 16, top: 0, height: StyleSheet.hairlineWidth, backgroundColor: tone.hairline },
  row: { marginTop: 24, marginHorizontal: 24, height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  countWindow: { height: 28, width: 142, overflow: 'hidden' },
  count: { position: 'absolute', color: tone.ink, fontSize: 22, fontWeight: '600', lineHeight: 28 },
  caption: { fontSize: 13, lineHeight: 18, color: tone.secondary },
});
