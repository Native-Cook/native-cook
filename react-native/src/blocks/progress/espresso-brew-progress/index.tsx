import { EspressoBrewProgress } from "./EspressoBrewProgress";

// The block has the whole screen: a 10-second film of a coffee machine app pulling a shot. A real 3D espresso machine
// (modelled in Blender: slate enamel body, brushed steel front, chrome group head with a light band, walnut portafilter
// handle, a pressure gauge) stands on a studio sweep with a double-walled glass cup on the drip tray, above a sheet with
// the system's segmented control and button. Brew lights the group head, the needle climbs to 9 bar, two streams pour
// and the cup fills with crema on top; then the cup is changed for an empty one. Drag sideways to look round the machine.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function EspressoBrewProgressDemo() {
  return <EspressoBrewProgress autoPlay />;
}
