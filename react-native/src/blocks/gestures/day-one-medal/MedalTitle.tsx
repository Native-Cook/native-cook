import { Canvas, Text, useFont } from '@shopify/react-native-skia';

import { D, tone } from './medal-theme';

/** The simulation pauses in the blank interval until this copy commits with the face pair. */
export function MedalTitle({ width, scale, day }: { width: number; scale: number; day: number }) {
  const font = useFont(require('./assets/ArchivoExpanded-Black.ttf'), D.title.size * scale);
  if (!font) return null;
  const text = `DAY ${day}`;
  return <Canvas style={{ width, height: D.title.size * 1.7 * scale }} pointerEvents="none">
    <Text text={text} font={font} x={(width - font.getTextWidth(text)) / 2}
      y={D.title.size * 1.1 * scale} color={tone.ink} />
  </Canvas>;
}
