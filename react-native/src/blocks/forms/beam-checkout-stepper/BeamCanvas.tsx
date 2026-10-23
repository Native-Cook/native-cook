import { Canvas, Group, Image, Rect, Shader, Text, useCanvasRef, useFont, useImage } from '@shopify/react-native-skia';
import type { SkFont, SkImage } from '@shopify/react-native-skia';
import { useEffect, useRef, useState } from 'react';
import { View } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { BEAM_EFFECTS } from './beam-shader';
import { F, G, nodeY, rowScale } from './beam-theme';
import type { BeamCanvasProps, BeamMotion } from './beam-theme';

function RowIcon({ index, icon, motion }: { index: number; icon: SkImage | null; motion: BeamMotion }) {
  const { frame } = motion;
  const opacity = useDerivedValue(() => {
    const f = frame.get();
    return f[F.PRESENT] * (0.38 + 0.22 * f[F.ROW_DONE + index] + 0.57 * f[F.ROW_ACTIVE + index]);
  });
  const transform = useDerivedValue(() => {
    const f = frame.get();
    return [{ scale: rowScale(f[F.ROW_RISE + index]) * (1 - 0.03 * f[F.ROW_PRESS + index]) }];
  });
  return (
    <Group opacity={opacity} origin={{ x: G.railX, y: nodeY(index) }} transform={transform}>
      <Image image={icon} x={G.rowIconX - 10} y={nodeY(index) - 10} width={20} height={20} fit="contain" />
    </Group>
  );
}

function Numeral({ index, font, motion }: { index: number; font: SkFont | null; motion: BeamMotion }) {
  const { frame } = motion;
  const opacity = useDerivedValue(() => {
    const f = frame.get();
    return (1 - f[F.NODE_DONE + index]) * (0.42 + 0.58 * Math.min(1, f[F.NODE_ON + index])) * f[F.PRESENT];
  });
  const transform = useDerivedValue(() => {
    const f = frame.get();
    return [
      { translateX: f[F.NODE_SHAKE + index] },
      { scale: (1 - 0.05 * f[F.NODE_PRESS + index]) * (1 - 0.35 * f[F.NODE_DONE + index]) },
    ];
  });
  const label = String(index + 1);
  const textWidth = font ? font.measureText(label).width : 10;
  return (
    <Group opacity={opacity} origin={{ x: G.railX, y: nodeY(index) }} transform={transform}>
      <Text text={label} x={G.railX - textWidth / 2} y={nodeY(index) + 6} font={font} color="#F5F8FF" />
    </Group>
  );
}

/** Paint the checkout's glass, light, icons and node numerals below the accessible form views. */
export function BeamCanvas({ width, height, k, ox, oy, motion, still, onReady }: BeamCanvasProps) {
  const mail = useImage(require('./assets/icon-mail.png'));
  const box = useImage(require('./assets/icon-box.png'));
  const card = useImage(require('./assets/icon-card.png'));
  const receipt = useImage(require('./assets/icon-receipt.png'));
  const seal = useImage(require('./assets/icon-seal.png'));
  const back = useImage(require('./assets/icon-back.png'));
  const font = useFont(require('./assets/Inter-Bold.ttf'), 17);
  const regular = useFont(require('./assets/Inter-Regular.ttf'), 16);
  const [laidOut, setLaidOut] = useState(false);
  const notified = useRef(false);
  const canvasRef = useCanvasRef();
  const [drawError, setDrawError] = useState<Error | null>(null);
  const { frame, clock } = motion;
  const uniforms = useDerivedValue(() => {
    const inputFrame = frame.get();
    const values: number[] = [];
    for (let i = 0; i < F.SIZE; i++) values.push(inputFrame[i]);
    return { f: values, clock: clock.get(), still: still ? 1 : 0 };
  });
  const backOpacity = useDerivedValue(() => {
    const f = frame.get();
    return f[F.CTA_SPLIT] * (1 - f[F.CTA_LENS]) * f[F.PRESENT];
  });
  const backTransform = useDerivedValue(() => [{ scale: 1 - 0.06 * frame.get()[F.BACK_PRESS] }]);
  const backSlide = useDerivedValue(() => [{ translateY: frame.get()[F.PANEL_H] }]);
  const ready = Boolean(mail && box && card && receipt && seal && back && font && regular && laidOut && width > 0 && height > 0);
  useEffect(() => {
    if (!ready || notified.current) return;
    // A tiny GPU-thread snapshot acknowledges a rendered display list, even on a busy host.
    let cancelled = false;
    const request = requestAnimationFrame(() => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      void canvas.makeImageSnapshotAsync({ x: 0, y: 0, width: 1, height: 1 }).then((snapshot) => {
        snapshot.dispose();
        if (!cancelled && !notified.current) {
          notified.current = true;
          onReady();
        }
      }).catch((error: unknown) => {
        if (!cancelled) setDrawError(new Error(`Beam Checkout first frame failed: ${String(error)}`));
      });
    });
    return () => { cancelled = true; cancelAnimationFrame(request); };
  }, [ready, onReady, canvasRef]);
  if (drawError) throw drawError;

  return (
    <View pointerEvents="none" onLayout={() => setLaidOut(true)} style={{ position: 'absolute', left: 0, top: 0, width, height, backgroundColor: '#000' }}>
      <Canvas ref={canvasRef} pointerEvents="none" style={{ width, height }}>
        <Group transform={[{ translateX: ox }, { translateY: oy }, { scale: k }]}>
          <Rect x={-ox / k} y={-oy / k} width={width / k} height={height / k}><Shader source={BEAM_EFFECTS.rays} uniforms={uniforms} /></Rect>
          <Rect x={-ox / k} y={96} width={width / k} height={778}><Shader source={BEAM_EFFECTS.glass} uniforms={uniforms} /></Rect>
          <Rect x={-ox / k} y={96} width={width / k} height={424}><Shader source={BEAM_EFFECTS.hero} uniforms={uniforms} /></Rect>
          <RowIcon index={0} icon={mail} motion={motion} />
          <RowIcon index={1} icon={box} motion={motion} />
          <RowIcon index={2} icon={card} motion={motion} />
          <RowIcon index={3} icon={receipt} motion={motion} />
          <RowIcon index={4} icon={seal} motion={motion} />
          <Numeral index={0} font={font} motion={motion} />
          <Numeral index={1} font={font} motion={motion} />
          <Numeral index={2} font={font} motion={motion} />
          <Numeral index={3} font={font} motion={motion} />
          <Numeral index={4} font={font} motion={motion} />
          <Group transform={backSlide}>
            <Group opacity={backOpacity} origin={{ x: G.panelX + 44, y: G.panelTop - 44 }} transform={backTransform}>
              <Image image={back} x={G.panelX + 34} y={G.panelTop - 54} width={20} height={20} fit="contain" />
            </Group>
          </Group>
        </Group>
      </Canvas>
    </View>
  );
}
