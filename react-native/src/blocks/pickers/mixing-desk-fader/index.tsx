import { MixingDeskFader } from "./MixingDeskFader";

// The block has the whole screen: a 10-second film of a podcast recording app. A real 3D four-channel mixing desk
// (modelled in Blender: brushed aluminium deck on a charcoal wedge with walnut cheeks; per channel a long fader with a
// soft-touch cap, an eight-LED meter, a lit mute button and a gain knob) sits on a dark studio sweep above a sheet with
// the system's segmented control for presets. The film rides two faders (Host settles on 0 dB), mutes SFX, recalls the
// Interview preset (every fader and knob glides there) and then Intro again. Drag any fader; tap a mute button.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function MixingDeskFaderDemo() {
  return <MixingDeskFader autoPlay />;
}
