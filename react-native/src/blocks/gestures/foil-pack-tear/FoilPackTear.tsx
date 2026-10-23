/** Measured, accessible shell. The scene and font must be ready before anything appears. */
import { useFonts } from 'expo-font';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import { TEAR_FONT, TearHint, type HintKind } from './TearHint';
import { TearScene } from './TearScene';
import { useTearMotion } from './use-tear-motion';

export type FoilPackTearProps = { autoPlay?: boolean };

export function FoilPackTear({ autoPlay = false }: FoilPackTearProps): React.JSX.Element {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [sceneReady, setSceneReady] = useState(false);
  const [fontReady] = useFonts(TEAR_FONT);
  const reduceMotion = useReducedMotion();
  const ready = sceneReady && fontReady && size.width > 0 && size.height > 0;
  const opacity = useSharedValue(0);
  const { values, gesture, phase, activate } = useTearMotion({ ...size, ready, autoPlay, reduceMotion });
  const onLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize(previous => previous.width === width && previous.height === height ? previous : { width, height });
  }, []);
  useEffect(() => {
    opacity.set(withTiming(ready ? 1 : 0, { duration: ready ? 220 : 0, reduceMotion: ReduceMotion.Never }));
  }, [opacity, ready]);
  const fade = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  const hint: HintKind = phase === 'sealed' || phase === 'tearing' ? 'tear'
    : phase === 'peek' ? 'peek' : phase === 'revealed' ? 'revealed' : 'quiet';
  const label = hint === 'tear' ? (phase === 'sealed' ? 'Sealed foil pack' : 'Partially torn foil pack')
    : phase === 'peek' ? 'Card peeking out of the pack' : phase === 'revealed' ? 'Solstice Koi, legendary card'
      : phase === 'collecting' ? 'Collecting Solstice Koi' : phase === 'opened' ? 'Opening foil pack' : 'Revealing Solstice Koi';
  const spokenHint = hint === 'tear' ? 'Double tap to tear it open' : hint === 'peek' ? 'Double tap to pull it out'
    : hint === 'revealed' ? 'Double tap to collect it' : 'Wait for the animation to finish';

  return <GestureHandlerRootView style={styles.root}>
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <Animated.View style={[styles.fill, fade]} collapsable={false} accessible={ready} accessibilityRole="button"
          accessibilityLabel={label} accessibilityHint={spokenHint}
          accessibilityState={{ busy: hint === 'quiet', disabled: !ready }}
          accessibilityActions={[{ name: 'activate' }]} onAccessibilityTap={activate}
          onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') activate(); }}>
          {size.width > 0 && size.height > 0 && <TearScene width={size.width} height={size.height} values={values}
            reduceMotion={reduceMotion} onReady={setSceneReady} />}
          {fontReady && ready && <TearHint width={size.width} height={size.height} kind={hint} reduceMotion={reduceMotion} />}
        </Animated.View>
      </GestureDetector>
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#010207' }, fill: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 } });
