import { SpinToLogoSplash } from "./SpinToLogoSplash";

// The block has the whole screen: a 14-second film of an onboarding splash for an invented money app, Arca. Its 3D
// mark (three nested arches and a terracotta pearl, built in Blender) stands as a sculpture on warm paper; a finger
// gives it a full turn with momentum while a track on the floor fills; once the circle closes the arches fold flat
// into the brand mark, the flat 2D logo takes over, rises, and the welcome screen fades up. Drag sideways to turn it
// yourself; Skip folds it at once.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament is native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function SpinToLogoSplashDemo() {
  return <SpinToLogoSplash autoPlay />;
}
