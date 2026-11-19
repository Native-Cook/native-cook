import { Canvas, createPicture, PaintStyle, Picture, Skia, TileMode } from "@shopify/react-native-skia";
import type { SkCanvas, SkPaint } from "@shopify/react-native-skia";
import { SymbolView } from "expo-symbols";
import type { SymbolViewProps } from "expo-symbols";
import { memo } from "react";
import type { JSX } from "react";
import { StyleSheet, Text, View } from "react-native";
import Animated, { useAnimatedStyle, useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { clamp01, filmFinger } from "./courierMotion";

/** A pill over the map: "Sent · “Coming down now”" after a quick message, "Calling Marco…" after Call. */
export const CourierToast = memo(function CourierToast({
  toast,
  text,
  icon,
  tint,
  bottom,
}: {
  toast: SharedValue<number>;
  text: string;
  icon: SymbolViewProps["name"];
  tint: string;
  bottom: number;
}): JSX.Element {
  const style = useAnimatedStyle(() => {
    const k = clamp01(toast.get());
    return { opacity: k, transform: [{ translateY: 10 * (1 - k) }, { scale: 0.96 + 0.04 * k }] };
  });
  return (
    <View pointerEvents="none" style={[styles.toastWrap, { bottom }]}>
      <Animated.View style={[styles.toast, style]}>
        <SymbolView fallback={<View style={[styles.tick, { backgroundColor: tint }]} />} name={icon} size={18} tintColor={tint} weight="semibold" />
        <Text numberOfLines={1} style={styles.toastText}>
          {text}
        </Text>
      </Animated.View>
    </View>
  );
});

/** The usual touch indicator: a soft shadow, a translucent white body, a thin rim; pressed, it sits lower. */
function finger(canvas: SkCanvas, paint: SkPaint, x: number, y: number, vis: number, press: number): void {
  "worklet";
  if (vis < 0.004) return;
  const lift = 1 - press;
  const r = 21 * (1 + 0.12 * lift);
  paint.setStyle(PaintStyle.Fill);
  paint.setShader(
    Skia.Shader.MakeRadialGradient(
      { x, y: y + 3 + 4 * lift },
      r * 1.5,
      [Skia.Color(`rgba(0,0,0,${0.22 * vis})`), Skia.Color("rgba(0,0,0,0)")],
      [0.4, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x, y + 3 + 4 * lift, r * 1.5, paint);
  paint.setShader(null);
  paint.setColor(Skia.Color(`rgba(255,255,255,${0.6 * vis})`));
  canvas.drawCircle(x, y, r, paint);
  paint.setColor(Skia.Color(`rgba(255,255,255,${0.35 * vis})`));
  canvas.drawCircle(x, y, r * 0.6, paint);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1.2);
  paint.setColor(Skia.Color(`rgba(150,160,180,${0.9 * vis})`));
  canvas.drawCircle(x, y, r, paint);
  paint.setStyle(PaintStyle.Fill);
}

/** The film's ghost finger: the drag across the map, then taps on locate, Call and Message. */
export const CourierFingers = memo(function CourierFingers({
  t,
  live,
  lay,
  buttons,
  width,
  height,
}: {
  t: SharedValue<number>;
  live: SharedValue<number>;
  lay: number[];
  buttons: number[];
  width: number;
  height: number;
}): JSX.Element {
  const picture = useDerivedValue(() => {
    const f = [0, 0, 0, 0, 0];
    filmFinger(t.get(), lay, buttons, f);
    const vis = f[1] * clamp01(live.get());
    return createPicture((canvas) => {
      if (f[0] === 0 || vis < 0.004) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      finger(canvas, paint, f[3], f[4], vis, f[2]);
    });
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width, height }]}>
      <Picture picture={picture} />
    </Canvas>
  );
});

const styles = StyleSheet.create({
  toastWrap: { position: "absolute", left: 0, right: 0, alignItems: "center" },
  toast: {
    height: 40,
    paddingHorizontal: 16,
    borderRadius: 20,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    shadowColor: "#0A1A40",
    shadowOpacity: 0.16,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
    maxWidth: 260,
  },
  tick: { width: 18, height: 18, borderRadius: 9, backgroundColor: "#1F9D55" },
  toastText: { fontSize: 15, fontWeight: "600", color: "#16181D", flexShrink: 1 },
});
