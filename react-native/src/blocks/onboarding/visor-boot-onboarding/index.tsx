import { VisorBootOnboarding } from "./VisorBootOnboarding";

// The block has the whole screen: an 18-second first-run film of an AR headset app. The helmet turns on its
// plinth through three onboarding steps, each with a module booting on it (a HUD on the visor, a radar round the
// ear sensor, a voice waveform), then Get started grows out of the pager. Drag the helmet to orbit it, swipe the
// copy to page, tap the arrow, a dot or Skip.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament is native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function VisorBootOnboardingDemo() {
  return <VisorBootOnboarding autoPlay />;
}
