import { useCallback, useEffect, useState, type ComponentType } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, ReduceMotion, useAnimatedReaction, useAnimatedStyle,
  useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { MedalCanvas, type MedalCanvasProps } from './MedalCanvas';
import type { MedalGpuCanvasProps } from './gpu/MedalGpuCanvas';

type Props = MedalGpuCanvasProps & {
  present: SharedValue<number>;
  settled: SharedValue<boolean>;
  renderer: 'auto' | 'gpu' | 'skia';
  reduced: boolean;
  onRenderer: (gpu: boolean) => void;
  onPrepared: (ready: boolean) => void;
  gpuDriving: SharedValue<boolean>;
};

/** Skia owns every flight. GPU eligibility is latched before each opening, never halfway through it. */
export function MedalRenderer({ renderer, reduced, onRenderer, onPrepared, gpuDriving, present, settled, ...props }: Props) {
  const [Gpu, setGpu] = useState<ComponentType<MedalGpuCanvasProps> | null>(null);
  const [failed, setFailed] = useState(false);
  const ready = useSharedValue(false);
  const eligible = useSharedValue(false);
  const mix = useSharedValue(0);
  const { visible } = props;
  const attempt = renderer !== 'skia' && !reduced && !failed;
  useEffect(() => {
    if (!attempt) { ready.set(false); onPrepared(true); return; }
    onPrepared(false);
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const module = require('./gpu/MedalGpuCanvas') as typeof import('./gpu/MedalGpuCanvas');
        setGpu(() => module.MedalGpuCanvas);
      } catch { setFailed(true); }
    });
    return () => { live = false; };
  }, [attempt, ready, onPrepared]);
  useAnimatedReaction(() => ({ visible: visible.get(), ready: ready.get(), present: present.get(), settled: settled.get() }),
    (now, before) => {
      'worklet';
      if (now.visible === 0) eligible.set(false);
      else if (!before || before.visible === 0) eligible.set(now.ready);
      if (!now.ready || now.present < 1 || now.visible === 0) {
        gpuDriving.set(false);
        if (mix.get() !== 0) { mix.set(0); scheduleOnRN(onRenderer, false); }
      } else if (eligible.get() && now.settled && mix.get() === 0) {
        gpuDriving.set(true);
        mix.set(withTiming(1, { duration: 100, reduceMotion: ReduceMotion.Never }));
        scheduleOnRN(onRenderer, true);
      }
    }, [onRenderer]);
  const skiaStyle = useAnimatedStyle(() => ({ opacity: 1 - mix.get() }));
  const gpuStyle = useAnimatedStyle(() => ({ opacity: mix.get() }));
  const unavailable = useCallback(() => { setFailed(true); ready.set(false); }, [ready]);
  const gpuReady = useCallback((value: boolean) => { ready.set(value); onPrepared(value); }, [ready, onPrepared]);
  return <View style={{ width: props.width, height: props.height }} pointerEvents="none">
    <Animated.View style={skiaStyle}><MedalCanvas {...props as MedalCanvasProps} /></Animated.View>
    {attempt && Gpu && <Animated.View style={[StyleSheet.absoluteFill, gpuStyle]}>
      <Gpu {...props} onReady={gpuReady} onUnavailable={unavailable} />
    </Animated.View>}
  </View>;
}
