import { PaperPlaneSend } from "./PaperPlaneSend";

// The block has the whole screen: a 9-second film of a chat composer. A message types itself into the field, the
// send button is pressed, and the message leaves the field as a sheet of paper that folds into a dart (a Blender model
// of sixteen hinged panels, its fold baked into a glTF animation that Filament scrubs with the loop's time), flies up
// over the thread, banks, and shrinks into the place where the sent bubble pops in with "Delivered". The 3D view is a
// see-through layer over the whole chat. Tap the send button to send at once, or drag it up and let go to throw: a
// harder flick flies a bigger, faster arc.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament is native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patch in react-native/patches for react-native-filament (applied by patch-package), which adds
//   Engine.setACESLegacyColorGrading (filmic tone mapping).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function PaperPlaneSendDemo() {
  return <PaperPlaneSend autoPlay />;
}
