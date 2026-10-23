# Particle Dispersion native module

This local Expo module prepares camera images, isolates the foreground subject,
and renders the background particles. iOS uses Apple Vision and Metal; Android
uses ML Kit subject segmentation and OpenGL ES 3. Captured images remain on device.
No sample photo or mask is bundled.

Copy the entire `particle-dispersion` block, including this directory. Add this to
the consuming app's `package.json` (adjust the path to the copied block):

```json
{
  "expo": {
    "autolinking": {
      "nativeModulesDir": "./src/blocks/animations/particle-dispersion/native"
    }
  }
}
```

If the app already has local Expo modules, copy `subject-segmentation` into that
existing native modules directory instead, then update the block's relative
TypeScript imports. Preserve every native module when configuring autolinking.

Install the dependencies listed in the block manifest using compatible versions
from the gallery's `package.json`. The camera requires VisionCamera 5, Nitro
Modules and Nitro Image. Add `android.permission.CAMERA` to the Expo Android
permissions, `NSCameraUsageDescription` to iOS infoPlist, and iOS deployment target
17.0 through `expo-build-properties`. The module's Android manifest supplies the
Play services model metadata and optional camera feature.

Rebuild with `npx expo run:android` or `npx expo run:ios`. A Metro reload cannot add
native code. Expo Go displays an informational fallback without importing
VisionCamera. Use a physical camera device for the full capture workflow;
iOS Simulator has no rear camera. Android requires Google Play services and may
need internet access to install the subject segmentation model on first use.
The app waits for that model before enabling capture.

The 3.9-second effect is triggered by capture or replay. It is not an automatic
recording loop. Reduce Motion uses a 260 ms crossfade. Retake and unmount cancel
requests and release temporary resources. Do not include personal camera images
when distributing the block or its screenshots.
