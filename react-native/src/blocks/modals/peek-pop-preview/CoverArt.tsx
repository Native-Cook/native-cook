// An article's cover, drawn in code: a three-stop gradient and a few flat shapes. It is laid out for a box of
// `width` × `height` (the open page's hero); every smaller cover is this same drawing scaled to cover its box.
import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { StyleSheet, View } from 'react-native';

import type { Cover, Shape } from './data';

function ShapeView({ shape, cx, cy, unit }: { shape: Shape; cx: number; cy: number; unit: number }) {
  const x = cx + shape.x * unit;
  const y = cy + shape.y * unit;
  const opacity = shape.opacity ?? 1;
  if (shape.kind === 'bar') {
    const w = shape.width * unit;
    const h = shape.height * unit;
    return (
      <View
        style={{
          position: 'absolute',
          left: x - w / 2,
          top: y - h / 2,
          width: w,
          height: h,
          borderRadius: (shape.radius ?? 0) * unit,
          backgroundColor: shape.color,
          opacity,
          transform: [{ rotate: `${shape.turn ?? 0}deg` }],
        }}
      />
    );
  }
  const d = shape.size * unit;
  return (
    <View
      style={[
        { position: 'absolute', left: x - d / 2, top: y - d / 2, width: d, height: d, borderRadius: d / 2, opacity },
        shape.kind === 'ring' ? { borderWidth: shape.stroke * unit, borderColor: shape.color } : { backgroundColor: shape.color },
      ]}
    />
  );
}

export const CoverArt = memo(function CoverArt({ cover, width, height }: { cover: Cover; width: number; height: number }) {
  return (
    <View style={{ width, height, overflow: 'hidden' }}>
      <LinearGradient colors={cover.colors} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      {cover.shapes.map((shape, i) => (
        <ShapeView key={i} shape={shape} cx={width / 2} cy={height / 2} unit={height} />
      ))}
      {/* Light from the top left, a little weight at the bottom: printed card stock rather than a flat fill. */}
      <LinearGradient
        colors={['rgba(255,255,255,0.20)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.14)']}
        locations={[0, 0.45, 1]}
        start={{ x: 0.1, y: 0 }}
        end={{ x: 0.7, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
    </View>
  );
});

/** The same cover in a smaller box: scaled to cover it, centred, clipped. */
export const CoverThumb = memo(function CoverThumb({ cover, width, height, size, radius }: { cover: Cover; width: number; height: number; size: number; radius: number }) {
  const scale = Math.max(size / width, size / height);
  return (
    <View style={{ width: size, height: size, borderRadius: radius, borderCurve: 'continuous', overflow: 'hidden' }}>
      <View style={{ position: 'absolute', left: (size - width) / 2, top: (size - height) / 2, width, height, transform: [{ scale }] }}>
        <CoverArt cover={cover} width={width} height={height} />
      </View>
    </View>
  );
});
