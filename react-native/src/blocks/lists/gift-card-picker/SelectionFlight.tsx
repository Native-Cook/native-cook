import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { BrandMark } from './Icons';
import { STOCKS, type Stock } from './stocks';
import { PORTFOLIO, STOCK } from './theme';

type Props = {
  width: number;
  sheetTop: number;
  sheetHeight: number;
  progress: SharedValue<number>;
  selected: number;
  active: boolean;
  reduceMotion: boolean;
};

const clamp = (value: number) => {
  'worklet';
  return Math.min(1, Math.max(0, value));
};
const easeOut = (value: number) => {
  'worklet';
  return 1 - (1 - clamp(value)) ** 3;
};

export function SelectionFlight({ width, sheetTop, sheetHeight, progress, selected, active, reduceMotion }: Props) {
  const card = useAnimatedStyle(() => {
    const raw = progress.get();
    const elapsed = raw * (reduceMotion ? 420 : 2540);
    const lift = easeOut(elapsed / 240);
    const flight = clamp((elapsed - 1500) / 620);
    const landing = clamp((elapsed - 2120) / 420);
    const startY = sheetTop + (STOCK.cardTop - 62) * (sheetHeight / 812);
    const targetY = sheetTop - 62 + PORTFOLIO.giftTop + (PORTFOLIO.giftH - STOCK.cardH * 0.3) / 2;
    const flightScale = 1.05 + (0.3 - 1.05) * easeOut(flight);
    if (reduceMotion) {
      return {
        opacity: raw <= 0 ? 0 : 1,
        transform: [{ translateX: 0 }, { translateY: 0 }, { rotateZ: '0deg' }, { scale: 1 }],
      };
    }
    return {
      opacity: raw <= 0 ? 0 : 1 - landing,
      transform: [
        { translateX: Math.sin(flight * Math.PI) * 34 },
        { translateY: -10 * lift + (targetY - (startY - 10)) * flight * flight },
        { rotateZ: `${-10 * flight}deg` },
        { scale: flightScale * (1 - 0.42 * landing) },
      ],
    };
  });
  const front = useAnimatedStyle(() => {
    const elapsed = progress.get() * (reduceMotion ? 420 : 2540);
    const flip = reduceMotion ? 0 : clamp((elapsed - 240) / 560);
    const mix = reduceMotion ? clamp(elapsed / 200) : flip >= 0.5 ? 1 : 0;
    return { opacity: 1 - mix, transform: [{ perspective: 760 }, { rotateY: `${flip * 180}deg` }] };
  });
  const back = useAnimatedStyle(() => {
    const elapsed = progress.get() * (reduceMotion ? 420 : 2540);
    const flip = reduceMotion ? 0 : clamp((elapsed - 240) / 560);
    const mix = reduceMotion ? clamp(elapsed / 200) : flip >= 0.5 ? 1 : 0;
    return { opacity: mix, transform: [{ perspective: 760 }, { rotateY: `${reduceMotion ? 0 : flip * 180 + 180}deg` }] };
  });
  const sheen = useAnimatedStyle(() => {
    if (reduceMotion) return { opacity: 0, transform: [{ translateX: 0 }, { rotateZ: '0deg' }] };
    const elapsed = progress.get() * 2540;
    const hold = clamp((elapsed - 800) / 700);
    return { opacity: hold > 0 && hold < 1 ? Math.sin(hold * Math.PI) * 0.42 : 0, transform: [{ translateX: -170 + hold * 340 }, { rotateZ: '-18deg' }] };
  });

  return (
    <Animated.View pointerEvents="none" style={[styles.wrap, { left: (width - STOCK.cardW) / 2, top: sheetTop + (STOCK.cardTop - 62) * (sheetHeight / 812) }, card]}
      accessible={active}
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'yes' : 'no-hide-descendants'}
      accessibilityLabel={`${STOCKS[selected]?.name ?? STOCKS[0].name} share certificate moving to portfolio`}>
      <View style={StyleSheet.absoluteFill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <FlightFaces stock={STOCKS[selected] ?? STOCKS[0]} front={front} back={back} sheen={sheen} />
      </View>
    </Animated.View>
  );
}

function FlightFaces({ stock, front, back, sheen }: { stock: Stock; front: object; back: object; sheen: object }) {
  return (
    <Animated.View style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.face, front]}>
        <Image source={stock.card} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
        <View style={[styles.logo, { backgroundColor: stock.markBg }]}><BrandMark kind={stock.mark} size={STOCK.logo} /></View>
        <Text style={styles.name}>{stock.name}</Text>
        <Text style={[styles.percent, { color: stock.change > 0 ? '#5ee06a' : '#ff4f86' }]}>{`${stock.change > 0 ? '↑' : '↓'} ${Math.abs(stock.change)}% past year`}</Text>
      </Animated.View>
      <Animated.View style={[styles.face, styles.certificate, { borderColor: stock.brand }, back]}>
        <Text style={styles.kicker}>SHARE CERTIFICATE</Text>
        <View style={[styles.certLogo, { backgroundColor: stock.markBg }]}><BrandMark kind={stock.mark} size={58} /></View>
        <Text style={styles.certName}>{stock.name}</Text>
        <Text style={styles.certLine}>USD 3 · ONE GIFT SHARE</Text>
        <Text style={styles.serial}>NO. 0003–2026</Text>
        <Animated.View style={[styles.sheen, sheen]} />
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', width: STOCK.cardW, height: STOCK.cardH, borderRadius: STOCK.cardRadius, shadowColor: '#000', shadowOpacity: 0.22, shadowRadius: 18, shadowOffset: { width: 0, height: 10 } },
  face: { position: 'absolute', inset: 0, borderRadius: STOCK.cardRadius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: '#101018', backfaceVisibility: 'hidden' },
  logo: { position: 'absolute', left: (STOCK.cardW - STOCK.logo) / 2, top: STOCK.logoY - STOCK.logo / 2, width: STOCK.logo, height: STOCK.logo, borderRadius: STOCK.logo / 2, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  name: { position: 'absolute', left: 6, right: 6, top: STOCK.nameY - 17, color: '#fff', fontSize: STOCK.nameSize, lineHeight: 34, fontWeight: '700', textAlign: 'center' },
  percent: { position: 'absolute', left: 0, right: 0, top: STOCK.pctY - 10, fontSize: STOCK.pctSize, lineHeight: 20, textAlign: 'center' },
  certificate: { backgroundColor: '#f7f1e6', borderWidth: 2, alignItems: 'center', paddingTop: 35 },
  kicker: { fontSize: 10, fontWeight: '600', letterSpacing: 1.6, color: '#282326' },
  certLogo: { width: 58, height: 58, borderRadius: 29, marginTop: 16, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  certName: { marginTop: 12, fontSize: 25, fontWeight: '700', color: '#282326' },
  certLine: { marginTop: 12, fontSize: 10, letterSpacing: 0.8, color: '#282326' },
  serial: { marginTop: 10, fontFamily: 'JetBrainsMono-Medium', fontSize: 9, color: '#282326' },
  sheen: { position: 'absolute', top: -30, bottom: -30, width: 48, backgroundColor: '#ffffff' },
});
