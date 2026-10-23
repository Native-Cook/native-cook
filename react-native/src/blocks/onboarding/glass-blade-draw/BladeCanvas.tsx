import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, View } from 'react-native';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import type { BladeCanvasProps } from './blade-contract';
import { bladeUniforms } from './blade-model';
import { BLADE_SKSL } from './blade-shader';

const effect = Skia.RuntimeEffect.Make(BLADE_SKSL);
if (!effect) throw new Error('Vane: the glass blade shader did not compile.');
const ATLAS_RECT = { x: 0, y: 0, width: 1536, height: 192 };

export function BladeCanvas({ layout, motion, onReady }: BladeCanvasProps) {
  const atlas = useImage(require('./assets/blade-atlas.png'));
  const [laidOut, setLaidOut] = useState(false);
  const notified = useRef(false);
  const { pos, charge, flare, press, ctaEnergy, intro, still } = motion;
  const clock = useSharedValue(0);
  const lastFrame = useSharedValue(-1);
  const pixelSize = 1 / PixelRatio.get();
  useFrameCallback(frame => {
    'worklet';
    // Timed and clamped exactly like the screen's loop clock, so the 5 s breath stays in step with the 10 s loop.
    const before = lastFrame.get();
    lastFrame.set(frame.timestamp);
    const dt = Math.min(100, Math.max(0, before < 0 ? 0 : frame.timestamp - before)) / 1000;
    if (still.get() < 0.5) clock.set(clock.get() + dt);
  });
  const uniforms = useDerivedValue(() => {
    'worklet';
    return bladeUniforms(layout, {
      pos: pos.get(), charge: charge.get(), flare: flare.get(), press: press.get(),
      ctaEnergy: ctaEnergy.get(), intro: intro.get(), still: still.get(),
    }, clock.get(), pixelSize);
  });
  useEffect(() => {
    if (!atlas || !laidOut || notified.current) return;
    // Allow the retained Canvas to submit its first image-backed frame before revealing the screen.
    let secondFrame = 0;
    const firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(() => {
        if (!notified.current) {
          notified.current = true;
          onReady();
        }
      });
    });
    return () => { cancelAnimationFrame(firstFrame); cancelAnimationFrame(secondFrame); };
  }, [atlas, laidOut, onReady]);
  return (
    <View pointerEvents="none" style={[styles.canvas, { width: layout.width, height: layout.height }]}
      onLayout={event => setLaidOut(event.nativeEvent.layout.width > 0 && event.nativeEvent.layout.height > 0)}>
      <Canvas style={styles.canvas} pointerEvents="none">
        {atlas && <Fill><Shader source={effect!} uniforms={uniforms}>
          <ImageShader image={atlas} fit="none" rect={ATLAS_RECT} />
        </Shader></Fill>}
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({ canvas: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 } });
