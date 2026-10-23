import { BlurMask, Canvas, Path, PathOp, Shadow, Skia } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Extrapolation, interpolate, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { WHEEL } from './motion';
import type { StampItem } from './stamps';
import { fonts, layout, stampShape } from './theme';

const { width: W, height: H } = layout.stamp;
const PAD = 16; // room for the coloured shadow around the stamp

// The stamp's silhouette: a rectangle with round notches bitten out of every edge, evenly spaced
// and centred on each side (the corners stay solid, as in the pin).
function stampPath() {
  const r = stampShape.notch;
  const notches = Skia.PathBuilder.Make();
  const along = (len: number, n: number, place: (d: number) => void) => {
    const start = (len - (n - 1) * stampShape.pitch) / 2;
    for (let i = 0; i < n; i++) place(start + i * stampShape.pitch);
  };
  along(W, stampShape.notchesAcross, (d) => {
    notches.addCircle(PAD + d, PAD, r).addCircle(PAD + d, PAD + H, r);
  });
  along(H, stampShape.notchesDown, (d) => {
    notches.addCircle(PAD, PAD + d, r).addCircle(PAD + W, PAD + d, r);
  });
  const body = Skia.PathBuilder.Make().addRect(Skia.XYWHRect(PAD, PAD, W, H)).build();
  return Skia.Path.MakeFromOp(body, notches.build(), PathOp.Difference) ?? body;
}

function withAlpha(hex: string, a: number) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  return `rgba(${r},${g},${b},${a})`;
}

type Props = { item: StampItem; index: number; pos: SharedValue<number> };

export function Stamp({ item, index, pos }: Props) {
  const path = useMemo(() => stampPath(), []);
  const { tone } = item;

  const style = useAnimatedStyle(() => {
    const p = index - pos.get();
    const a = Math.abs(p);
    const side = p < 0 ? -1 : 1;
    const rise = interpolate(a, WHEEL.table, WHEEL.rise, Extrapolation.CLAMP);
    const tilt = interpolate(a, WHEEL.table, WHEEL.tilt, Extrapolation.CLAMP);
    const scale = interpolate(a, WHEEL.table, WHEEL.scale, Extrapolation.CLAMP);
    return {
      opacity: a > WHEEL.hideBeyond ? 0 : 1,
      zIndex: Math.round(100 - a * 10),
      transform: [
        { translateX: p * WHEEL.stepX },
        { translateY: -rise },
        // the stamp leans away from the centre, like a card on the rim of a bowl
        { rotate: `${-side * tilt}deg` },
        { scale },
      ],
    };
  });

  const panel = stampShape.panel;
  return (
    <Animated.View style={[styles.box, style]} pointerEvents="none" accessibilityLabel={item.label}>
      <Canvas style={styles.canvas}>
        <Path path={path} color={tone.tint}>
          {/* the pin's paper edge is soft, not a vector cut */}
          <BlurMask blur={stampShape.edgeSoftness} style="normal" />
          {/* a white halo around the paper, as the pin's stamp sits in its own light */}
          <Shadow dx={0} dy={0} blur={stampShape.haloBlur} color="#FFFFFF" />
          <Shadow dx={0} dy={stampShape.shadowDy} blur={stampShape.shadowBlur} color={tone.glow} />
          {/* the paper's edge shades the backing inside each notch, darkest at the cut */}
          <Shadow
            dx={0}
            dy={stampShape.edgeShadeDy}
            blur={stampShape.edgeShadeBlur}
            color={withAlpha(tone.shade, stampShape.edgeShadeAlpha)}
          />
        </Path>
      </Canvas>
      <View
        style={[
          styles.panel,
          {
            left: panel.left,
            top: panel.top,
            width: panel.width,
            height: panel.height,
            borderRadius: panel.radius,
            borderColor: tone.border,
          },
        ]}
      >
        <Image source={item.art} style={StyleSheet.absoluteFill} contentFit="cover" />
      </View>
      <Text style={[styles.label, { color: tone.label }]} numberOfLines={1}>
        {item.label}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  box: { position: 'absolute', width: W, height: H, left: -W / 2, top: -H / 2 },
  canvas: { position: 'absolute', left: -PAD, top: -PAD, width: W + 2 * PAD, height: H + 2 * PAD },
  panel: { position: 'absolute', overflow: 'hidden', borderWidth: stampShape.panel.border, borderCurve: 'continuous' },
  label: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: stampShape.labelCenterY - stampShape.labelSize * 0.7,
    textAlign: 'center',
    fontFamily: fonts.display,
    fontSize: stampShape.labelSize,
    lineHeight: stampShape.labelSize * 1.4,
    transform: [{ scaleX: stampShape.labelStretch }],
  },
});
