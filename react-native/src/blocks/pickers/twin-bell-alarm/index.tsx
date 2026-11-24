import { TwinBellAlarm } from "./TwinBellAlarm";

// The block has the whole screen: a 10-second film of setting an alarm. A real 3D twin-bell alarm clock (modelled in
// Blender: enamel drum, chrome bells, hammer, handle and legs, a dial with two hands) stands on a studio sweep above
// the alarm's sheet, which holds the system's time wheel and switch. Turning the wheel sweeps the clock's hands to
// the picked time by the shortest way round; switching the alarm on makes the clock ring: the hammer chatters between
// the bells, the bells shake, the clock hops and rocks on its legs while the camera moves in. Drag across the clock
// to look round it.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function TwinBellAlarmDemo() {
  return <TwinBellAlarm autoPlay />;
}
