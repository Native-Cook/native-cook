import { CombinationSafeDial } from "./CombinationSafeDial";

// The block has the whole screen: a 10-second film of opening a password vault. A real 3D wall safe (modelled in
// Blender: bottle-green housing, round brushed-steel door, numbered dial, T handle, eight bolts) stands on a studio
// sweep above the vault's sheet. The dial turns to three numbers; on the third the handle swings, the bolts draw in and
// the door opens towards the camera with warm light in the gap. Drag round the dial to turn it: the right code opens
// the safe, a wrong one shakes it.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function CombinationSafeDialDemo() {
  return <CombinationSafeDial autoPlay />;
}
