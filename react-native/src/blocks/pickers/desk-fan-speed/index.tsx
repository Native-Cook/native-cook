import { DeskFanSpeed } from "./DeskFanSpeed";

// The block has the whole screen: a 10-second film of a smart-home fan control. A real 3D retro desk fan (modelled in
// Blender: seafoam enamel body, chrome cage, four brass blades, four piano keys) stands on a studio sweep above a sheet
// with the system's segmented control. Each level spins the blades up or down with inertia, presses its key on the
// base and strengthens the airflow lines; the head oscillates. Drag sideways on the fan to aim it.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function DeskFanSpeedDemo() {
  return <DeskFanSpeed autoPlay />;
}
