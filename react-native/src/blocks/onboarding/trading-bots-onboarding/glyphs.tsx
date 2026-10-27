import { Canvas, Fill, Group, LinearGradient, Path, RoundedRect, Shader, Skia, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';

import { DOTS } from './shaders';
import { hex } from './theme';

// Small line glyphs and marks of the block, drawn with Skia from the same geometry as the
// SwiftUI shapes (`TradingBotsSeal`, `TradingBotsCandles`, `TradingBotsBellCurve`, …).

/** The scalloped "bot" seal: a soft nine-lobed blob. */
export function sealPath(size: number, lobes = 9, depth = 0.045) {
  const path = Skia.Path.Make();
  const steps = 216;
  for (let i = 0; i <= steps; i++) {
    const angle = (i / steps) * Math.PI * 2 - Math.PI / 2;
    const radius = (0.5 - depth + depth * Math.cos(angle * lobes)) * size;
    const x = size / 2 + Math.cos(angle) * radius;
    const y = size / 2 + Math.sin(angle) * radius;
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  path.close();
  return path;
}

type MarkStyle = 'blue' | 'white' | 'outline';

/** The seal with two eyes: blue next to headings, white on cards, outlined inside "Bots". */
export function Mark({ style = 'blue', size = 33, rotation = 0 }: { style?: MarkStyle; size?: number; rotation?: number }) {
  const seal = useMemo(() => sealPath(size), [size]);
  const eyeW = size * 0.075;
  const eyeH = size * 0.27;
  const gap = size * (style === 'outline' ? 0.1 : 0.12);
  const eyeColor = style === 'outline' ? '#fff' : hex('#141414');
  const eyeTilt = style === 'outline' ? (18 * Math.PI) / 180 : 0;
  return (
    <Canvas style={{ width: size, height: size, transform: [{ rotate: `${rotation}deg` }] }}>
      {style === 'blue' && (
        <Path path={seal}>
          <LinearGradient start={vec(0, 0)} end={vec(0, size)} colors={[hex('#2f62ff'), hex('#5d93ff'), hex('#87b6ff')]} />
        </Path>
      )}
      {style === 'white' && (
        <Path path={seal}>
          <LinearGradient start={vec(0, 0)} end={vec(0, size)} colors={['#ffffff', hex('#e6e6e6')]} />
        </Path>
      )}
      {style === 'outline' && (
        <>
          <Path path={seal} color={hex('#0f0f0f')} />
          <Path path={seal} color="#fff" style="stroke" strokeWidth={size * 0.09} />
        </>
      )}
      <Group transform={[{ rotate: eyeTilt }]} origin={vec(size / 2, size / 2)}>
        <RoundedRect x={size / 2 - gap / 2 - eyeW} y={size / 2 - eyeH / 2} width={eyeW} height={eyeH} r={eyeW / 2} color={eyeColor} />
        <RoundedRect x={size / 2 + gap / 2} y={size / 2 - eyeH / 2} width={eyeW} height={eyeH} r={eyeW / 2} color={eyeColor} />
      </Group>
    </Canvas>
  );
}

type GlyphProps = { width: number; height: number; color?: string; lineWidth?: number };

export function BackArrow({ width, height, color = '#fff', lineWidth = 1.7 }: GlyphProps) {
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    p.moveTo(width, height / 2);
    p.lineTo(0, height / 2);
    p.moveTo(height / 2, 0);
    p.lineTo(0, height / 2);
    p.lineTo(height / 2, height);
    return p;
  }, [width, height]);
  const pad = lineWidth;
  return (
    <Canvas style={{ width: width + pad * 2, height: height + pad * 2 }}>
      <Group transform={[{ translateX: pad }, { translateY: pad }]}>
        <Path path={path} color={color} style="stroke" strokeWidth={lineWidth} strokeCap="round" strokeJoin="round" />
      </Group>
    </Canvas>
  );
}

/** A candlestick chart: an L axis and two candles with wicks. */
export function Candles({ width, height, color = hex('#9a9a9a') }: GlyphProps) {
  const paths = useMemo(() => {
    const axis = Skia.Path.Make();
    axis.moveTo(0.7, 0);
    axis.lineTo(0.7, height - 0.7);
    axis.lineTo(width, height - 0.7);
    const wicks = Skia.Path.Make();
    const bodies = Skia.Path.Make();
    for (const [x, top, bottom] of [[0.38, 0.12, 0.62], [0.72, 0, 0.5]]) {
      wicks.moveTo(width * x, height * top);
      wicks.lineTo(width * x, height * bottom + 0.1 * height);
      bodies.addRRect(Skia.RRectXY(Skia.XYWHRect(width * x - width * 0.11, height * (top + 0.15), width * 0.22, height * 0.32), 1.2, 1.2));
    }
    return { axis, wicks, bodies };
  }, [width, height]);
  return (
    <Canvas style={{ width, height }}>
      <Path path={paths.axis} color={color} style="stroke" strokeWidth={1.3} strokeCap="round" strokeJoin="round" />
      <Path path={paths.wicks} color={color} style="stroke" strokeWidth={1.1} />
      <Path path={paths.bodies} color={color} style="stroke" strokeWidth={1.2} />
    </Canvas>
  );
}

export function BellCurve({ width, height, color = hex('#9a9a9a'), lineWidth = 1.3 }: GlyphProps) {
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    p.moveTo(0, 0);
    p.lineTo(0, height);
    p.lineTo(width, height);
    p.moveTo(1, height - 1);
    p.cubicTo(width * 0.35, height - 2, width / 2 - 2, height * 0.2, width / 2 + 1, height * 0.2);
    p.cubicTo(width / 2 + 4, height * 0.2, width - width * 0.25, height - 1, width, height - 1);
    return p;
  }, [width, height]);
  return (
    <Canvas style={{ width: width + 1, height: height + 1 }}>
      <Path path={path} color={color} style="stroke" strokeWidth={lineWidth} />
    </Canvas>
  );
}

export function Bars({ width, height, color = hex('#9a9a9a'), lineWidth = 1.3 }: GlyphProps) {
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    const w = width / 3;
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(0, height * 0.45, w, height * 0.55), 1, 1));
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(w, 0, w, height), 1, 1));
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(2 * w, height * 0.3, w, height * 0.7), 1, 1));
    return p;
  }, [width, height]);
  return (
    <Canvas style={{ width: width + lineWidth, height: height + lineWidth }}>
      <Group transform={[{ translateX: lineWidth / 2 }, { translateY: lineWidth / 2 }]}>
        <Path path={path} color={color} style="stroke" strokeWidth={lineWidth} />
      </Group>
    </Canvas>
  );
}

/** —o— : the Configure Bot glyph. */
export function SliderGlyph({ width, height, color = '#fff', lineWidth = 1.3 }: GlyphProps) {
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    const r = height / 2;
    p.moveTo(0, r);
    p.lineTo(width / 2 - r, r);
    p.addCircle(width / 2, r, r);
    p.moveTo(width / 2 + r, r);
    p.lineTo(width, r);
    return p;
  }, [width, height]);
  return (
    <Canvas style={{ width, height: height + lineWidth }}>
      <Group transform={[{ translateY: lineWidth / 2 }]}>
        <Path path={path} color={color} style="stroke" strokeWidth={lineWidth} />
      </Group>
    </Canvas>
  );
}

export function HexNut({ width, height, color = '#fff', lineWidth = 1.5 }: GlyphProps) {
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    for (let i = 0; i <= 6; i++) {
      const angle = (i / 6) * Math.PI * 2 - Math.PI / 2;
      const x = width / 2 + (Math.cos(angle) * width) / 2;
      const y = height / 2 + (Math.sin(angle) * height) / 2;
      if (i === 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    p.addCircle(width / 2, height / 2, width * 0.17);
    return p;
  }, [width, height]);
  const pad = lineWidth;
  return (
    <Canvas style={{ width: width + pad * 2, height: height + pad * 2 }}>
      <Group transform={[{ translateX: pad }, { translateY: pad }]}>
        <Path path={path} color={color} style="stroke" strokeWidth={lineWidth} />
      </Group>
    </Canvas>
  );
}

/** Faint 0.9 pt dot grid used inside the bot screens and the results tray. */
export function DotField({ spacing = 5, opacity = 0.05, style }: { spacing?: number; opacity?: number; style?: StyleProp<ViewStyle> }) {
  return (
    <View pointerEvents="none" style={style}>
      <Canvas style={{ flex: 1 }}>
        <Fill>
          <Shader source={DOTS} uniforms={{ spacing, opacity }} />
        </Fill>
      </Canvas>
    </View>
  );
}
