import { WatchCaseReveal } from "./WatchCaseReveal";

// The block has the whole screen: a 12-second reveal film of a watch in a lacquered walnut case. The clasp lets go,
// the lid swings open on a damped hinge, the suede pillow lifts the watch and tips its dial to the lens, the watch
// turns to catch the light while the camera pushes in to the ticking second hand, the dial picker tries every dial,
// "Reserve in a boutique" is pressed, then the pillow sinks and the lid closes. Drag up on the case to open it (and
// lift the pillow), down to close it, sideways to turn the watch; pick a dial in the native segmented picker. Two
// seconds after the last touch the case closes and the film restarts.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function WatchCaseRevealDemo() {
  return <WatchCaseReveal autoPlay />;
}
