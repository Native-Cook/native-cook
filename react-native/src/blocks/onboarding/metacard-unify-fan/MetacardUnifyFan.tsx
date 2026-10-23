import { useFonts, useImage, type SkImage, type SkTypefaceFontProvider } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { MetacardCanvas } from './MetacardCanvas';
import { D, designFrame } from './metacard-layout';
import { makeParagraphs } from './MetacardText';
import { useMetacardMotion } from './use-metacard-motion';

function haptic(heavy: boolean) {
  void Haptics.impactAsync(heavy ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function ReadyScene({ width, height, autoPlay, faces, shadow, chip, provider }: {
  width: number; height: number; autoPlay: boolean; faces: SkImage[]; shadow: SkImage; chip: SkImage; provider: SkTypefaceFontProvider;
}) {
  const reduced = useReducedMotion(), [hero, setHero] = useState(false);
  const reportScene = useCallback((next: boolean) => setHero(next), []);
  const paragraphs = useMemo(() => makeParagraphs(provider), [provider]);
  const motion = useMetacardMotion({ autoPlay, reduced, width, height, onHaptic: haptic, onScene: reportScene });
  const frame = designFrame(width, height);
  return <GestureDetector gesture={motion.gesture}>
    <View collapsable={false} style={{ width, height }}>
      <MetacardCanvas width={width} height={height} faces={faces} chip={chip} shadow={shadow}
        paragraphs={paragraphs} values={motion} reduced={reduced} />
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Text accessibilityRole="header" style={[styles.semantic, {
          left: frame.x + 30 * frame.k, top: frame.y + (hero ? D.title2[0] - 21 : D.title1[0] - 21) * frame.k, width: 342 * frame.k, height: 75 * frame.k,
        }]}>{hero ? 'Meet the first Metacard' : 'Unify the limit of all your cards'}</Text>
        <View accessible accessibilityRole="button" accessibilityLabel={hero ? 'Split Metacard' : 'Unify cards'}
          accessibilityHint={hero ? 'Fans the eight cards out again' : 'Folds the eight cards into one Metacard'}
          accessibilityActions={[{ name: 'activate' }]} onAccessibilityTap={motion.activate}
          onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') motion.activate(); }}
          style={{ position: 'absolute', left: frame.x + (hero ? 40 : D.fanX - D.hubHitRadius) * frame.k,
            top: frame.y + (hero ? 398 : D.fanY - D.hubHitRadius) * frame.k, width: (hero ? 322 : D.hubHitRadius * 2) * frame.k, height: (hero ? 245 : D.hubHitRadius * 2) * frame.k }} />
      </View>
    </View>
  </GestureDetector>;
}
/** Fully self-contained: the ready subtree mounts only when all ten images, the font and layout exist. */
export function MetacardUnifyFan({ autoPlay = false }: { autoPlay?: boolean }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const provider = useFonts({ MetacardInter: [require('./assets/Inter-Bold.ttf')], MetacardInterRegular: [require('./assets/Inter-Regular.ttf')] });
  const c0 = useImage(require('./assets/card-0.png')), c1 = useImage(require('./assets/card-1.png'));
  const c2 = useImage(require('./assets/card-2.png')), c3 = useImage(require('./assets/card-3.png'));
  const c4 = useImage(require('./assets/card-4.png')), c5 = useImage(require('./assets/card-5.png'));
  const c6 = useImage(require('./assets/card-6.png')), c7 = useImage(require('./assets/card-7.png'));
  const shadow = useImage(require('./assets/card-shadow.png')), chip = useImage(require('./assets/hero-chip.png'));
  const onLayout = useCallback((event: { nativeEvent: { layout: { width: number; height: number } } }) => {
    const { width, height } = event.nativeEvent.layout;
    setSize(previous => previous.width === width && previous.height === height ? previous : { width, height });
  }, []);
  return <View style={styles.root} onLayout={onLayout}>
    {provider && c0 && c1 && c2 && c3 && c4 && c5 && c6 && c7 && shadow && chip && size.width > 0 && size.height > 0 &&
      <ReadyScene width={size.width} height={size.height} autoPlay={autoPlay} provider={provider}
        faces={[c0, c1, c2, c3, c4, c5, c6, c7]} shadow={shadow} chip={chip} />}
  </View>;
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000000' },
  semantic: { position: 'absolute', color: 'transparent', fontSize: 1 },
});
