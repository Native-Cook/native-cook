import { RingBoxReveal } from "./RingBoxReveal";

// The block has the whole screen: an 11.5-second reveal film of a solitaire in a small navy velvet ring box, inside a
// jeweller's proposal planner. The lid presses down a hair, then snaps open on its hinge with a small overshoot; the
// stone throws a burst of glints (projected from forty crown facets in 3D), the camera comes round to the ring, the
// metal picker tries yellow and rose gold, the camera goes in to the stone while the stone picker tries sapphire and
// morganite and the carat slider tries 2 ct, then the camera pulls back, "Book a private viewing" is pressed and the
// lid closes. The box stands on a soft paper floor with a gentle reflection. Tap the box (or flick the lid up) to open
// it, tap again to go in to the stone, drag down to close it, drag sideways to turn it; every control is native.
// Two seconds after the last touch the box closes and the film restarts from the buyer's choices.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament and @expo/ui are native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function RingBoxRevealDemo() {
  return <RingBoxReveal autoPlay />;
}
