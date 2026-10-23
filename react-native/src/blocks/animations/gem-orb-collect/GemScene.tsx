// A single retained canvas, with explicit back/front orb passes around the crystal.
import { Canvas, Group, useCanvasRef, useImage } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';
import { BackdropLayer } from './BackdropLayer';
import { CrystalPillLayer } from './CrystalPillLayer';
import { GemGlints } from './GemGlints';
import { GemLayer } from './GemLayer';
import { OrbsLayer } from './OrbsLayer';
import type { Layout } from './gem-contract';
import type { GemCollectController } from './use-gem-collect';

export function GemScene({ layout: l, controller: c, onReady }: { layout: Layout; controller: GemCollectController; onReady: (ready: boolean) => void }) {
  const ref = useCanvasRef();
  const atlas = useImage(require('./assets/orb-atlas.png'));
  const scale = c.capsule.scale;
  const miniTransform = useDerivedValue(() => [{ scale: scale.get() }]);
  useEffect(() => {
    if (!atlas) return;
    let cancelled = false, request = 0, retries = 0;
    // A successful native snapshot confirms that the retained tree can be drawn.
    // It is immediately disposed; this is a one-time readiness handshake, not animation.
    const check = () => {
      if (cancelled) return;
      const canvas = ref.current;
      if (!canvas) { request = requestAnimationFrame(check); return; }
      void canvas.makeImageSnapshotAsync().then(image => {
        image.dispose(); if (!cancelled) onReady(true);
      }).catch(() => {
        if (!cancelled && retries++ < 8) request = requestAnimationFrame(check);
      });
    };
    request = requestAnimationFrame(() => { request = requestAnimationFrame(check); });
    return () => { cancelled = true; cancelAnimationFrame(request); };
  }, [atlas, onReady, ref]);
  return <Canvas ref={ref} pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Group opacity={c.visible}>
      <BackdropLayer {...c.backdrop} />
      <OrbsLayer sprites={c.sprites} pass="back" />
      <GemLayer {...c.gem} />
      <GemGlints {...c.gem} />
      <OrbsLayer sprites={c.sprites} pass="front" />
      <CrystalPillLayer {...c.capsule} />
      <Group origin={{ x: l.capsule.x + l.capsule.width / 2, y: l.miniGemY }} transform={miniTransform}>
        <GemLayer {...c.mini} />
      </Group>
      <CrystalPillLayer {...c.bar} />
    </Group>
  </Canvas>;
}
