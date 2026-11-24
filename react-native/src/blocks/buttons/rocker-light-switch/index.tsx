import { RockerLightSwitch } from "./RockerLightSwitch";

// The block has the whole screen: an 8-second film of a wall at dusk with a rocker switch and a brass sconce, both
// modelled in Blender and drawn by Filament. A finger presses the top half of the rocker: it follows the press, tips
// past the middle and snaps home on a spring with a click, and 120 ms later the filament warms up: the globe glows,
// the wall takes the warm pool of light and the plate throws its shadow. The camera eases a few degrees round the
// wall while the system slider dims the light and brings it back, then the bottom half is pressed and the room goes
// back to dusk. Press either half of the rocker, or drag it up and down to feel the tipping point; move the slider
// for the brightness. Two seconds after the last touch the light goes off and the film restarts.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function RockerLightSwitchDemo() {
  return <RockerLightSwitch autoPlay />;
}
