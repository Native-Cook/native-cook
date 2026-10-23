// One full-screen canvas and one recognizer, gated on shader assets and all four font sizes.
import { useFont } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { M } from './supercharge-constants';
import { CAPTION, PRINT, designFrame } from './supercharge-layout';
import { SuperchargeScene } from './SuperchargeScene';
import { SuperchargeLightning } from './SuperchargeLightning';
import { SuperchargePrint } from './SuperchargePrint';
import { useSupercharge } from './use-supercharge';
export function SuperchargeCard({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [ready, setReady] = useState(false);
  const title = useFont(require('./assets/Inter-Bold.ttf'), PRINT.title.size);
  const body = useFont(require('./assets/Inter-Regular.ttf'), PRINT.body.size);
  const mono = useFont(require('./assets/JetBrainsMono-Medium.ttf'), PRINT.readout.size);
  const caption = useFont(require('./assets/JetBrainsMono-Medium.ttf'), CAPTION.size);
  const opacity = useSharedValue(0), active = useSharedValue(false);
  const { values, gesture, activate, valueText } = useSupercharge({ reduced, autoPlay }, designFrame(width, height), active);
  const loaded = !!(ready && title && body && mono && caption);
  useEffect(() => {
    active.set(loaded);
    opacity.set(withTiming(loaded ? 1 : 0, { duration: M.fadeMs, reduceMotion: ReduceMotion.Never }));
  }, [active, loaded, opacity]);
  const fade = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return <View style={styles.root}>
    <Animated.View style={[{ width, height }, fade]}>
      <SuperchargeScene width={width} height={height} values={values} onReady={setReady}>
        <SuperchargeLightning values={values} />
        {title && body && mono && caption && <SuperchargePrint values={values} fonts={{ title, body, mono, caption }} />}
      </SuperchargeScene>
    </Animated.View>
    <GestureDetector gesture={gesture}>
      <View style={StyleSheet.absoluteFill} pointerEvents={loaded ? 'auto' : 'none'} accessible accessibilityRole="button"
        accessibilityLabel="Supercharge card" accessibilityHint="Press and hold to charge, then release"
        accessibilityValue={{ text: valueText }} accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') activate(); }} />
    </GestureDetector>
  </View>;
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#050506' } });
