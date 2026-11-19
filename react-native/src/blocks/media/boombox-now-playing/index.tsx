import { VinylNowPlaying } from "./VinylNowPlaying";

// The block has the whole screen: a music player's now playing with nothing in the studio but a 12-inch record and
// its sleeve, floating upright over the paper with soft contact shadows. In a 15.8-second film the record slides out
// of the sleeve, the sleeve drifts back, play spins the record up, it turns a full 360° in the air (edge, B side,
// edge, A side), a finger turns it to scrub the song forward, the volume goes up and back, forward skips 15 s, pause
// runs it down, back returns to 0:00, and the record slides back into its sleeve. Turn the record with a finger to
// move through the song, tap it or the play button, scrub, skip, set the volume. The sliders and buttons are the
// system's (SwiftUI through @expo/ui) on iOS.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function VinylNowPlayingDemo() {
  return <VinylNowPlaying autoPlay />;
}
