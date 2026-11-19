import { SymbolView } from "expo-symbols";
import type { SymbolViewProps } from "expo-symbols";
import { memo } from "react";
import type { JSX } from "react";
import { StyleSheet, Text, View } from "react-native";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { DOOR_X, HOME, HUB } from "./courierCity";
import { screenCam, smooth01, toScreen } from "./courierMotion";

const INK = "#16181D";
const BLUE = "#2F6BFF";

type Props = {
  lay: number[];
  camX: SharedValue<number>;
  camY: SharedValue<number>;
  camZ: SharedValue<number>;
  camB: SharedValue<number>;
};

function Glyph({ name, size, color }: { name: SymbolViewProps["name"]; size: number; color: string }) {
  return <SymbolView fallback={<View style={{ width: size, height: size }} />} name={name} size={size} tintColor={color} weight="semibold" />;
}

/** Map pins that stay upright: Home (it gives way to the ring on the pavement once the camera is at the door) and the
 * courier's hub. They follow the map through the same camera maths as the Skia layers. */
export const CourierPins = memo(function CourierPins({ lay, camX, camY, camZ, camB }: Props): JSX.Element {
  const home = useAnimatedStyle(() => {
    const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    screenCam(camX.get(), camY.get(), camZ.get(), camB.get(), lay, cam);
    const q = [0, 0];
    toScreen(DOOR_X, (HOME.y0 + HOME.y1) / 2, cam, q, 0);
    const fade = 1 - smooth01((cam[2] - 1.2) / 1.1);
    return { opacity: fade, transform: [{ translateX: q[0] - 18 }, { translateY: q[1] - 44 }, { scale: 0.85 + 0.15 * fade }] };
  });
  const hub = useAnimatedStyle(() => {
    const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    screenCam(camX.get(), camY.get(), camZ.get(), camB.get(), lay, cam);
    const q = [0, 0];
    toScreen(HUB.x, HUB.y, cam, q, 0);
    const fade = 1 - smooth01((cam[2] - 0.5) / 0.6);
    return { opacity: fade, transform: [{ translateX: q[0] - 13 - 30 }, { translateY: q[1] - 13 + 14 }] };
  });
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.hub, hub]}>
        <View style={styles.hubDot}>
          <Glyph color="#FFFFFF" name="shippingbox.fill" size={13} />
        </View>
        <Text style={styles.hubLabel}>Hub</Text>
      </Animated.View>
      <Animated.View style={[styles.home, home]}>
        <View style={styles.homeHead}>
          <Glyph color="#FFFFFF" name="house.fill" size={16} />
        </View>
        <View style={styles.homeTip} />
        <Text style={styles.homeLabel}>Home</Text>
      </Animated.View>
    </View>
  );
});

const styles = StyleSheet.create({
  home: { position: "absolute", left: 0, top: 0, width: 36, alignItems: "center" },
  homeHead: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: BLUE,
    borderWidth: 3,
    borderColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#0A1A40",
    shadowOpacity: 0.25,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
  },
  homeTip: { width: 3, height: 7, backgroundColor: "#FFFFFF", borderBottomLeftRadius: 2, borderBottomRightRadius: 2 },
  homeLabel: {
    position: "absolute",
    top: 9,
    left: 42,
    width: 60,
    fontSize: 13,
    fontWeight: "700",
    color: INK,
    textShadowColor: "rgba(255,255,255,0.95)",
    textShadowRadius: 3,
  },
  hub: { position: "absolute", left: 0, top: 0, width: 26, height: 26 },
  hubDot: { width: 26, height: 26, borderRadius: 8, backgroundColor: INK, alignItems: "center", justifyContent: "center", borderWidth: 2, borderColor: "#FFFFFF" },
  hubLabel: {
    position: "absolute",
    top: 5,
    left: -32,
    textAlign: "right",
    width: 28,
    fontSize: 13,
    fontWeight: "600",
    color: INK,
    textShadowColor: "rgba(255,255,255,0.95)",
    textShadowRadius: 3,
  },
});
