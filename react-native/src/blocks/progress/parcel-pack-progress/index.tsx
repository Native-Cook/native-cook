import { ParcelPackProgress } from "./ParcelPackProgress";

// The block has the whole screen: a 10-second film of an order being packed. A real 3D cardboard carton (modelled in
// Blender: corrugated board, four hinged flaps, tape, a shipping label) sits on a studio floor above the order's
// sheet, and acts out each of its four steps: the flaps fall shut, the tape runs over the seam, the label drops on,
// and the carton slides off as a new open one slides in. The list below follows. Tap a step to see the parcel at
// that step; drag across the carton to look round it.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament is native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function ParcelPackProgressDemo() {
  return <ParcelPackProgress autoPlay />;
}
