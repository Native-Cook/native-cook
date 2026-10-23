# Native Cook

The code of the blocks on [nativecook.dev](https://nativecook.dev): mobile UI components, each built up to three times, once per stack. Each stack has one demo app that lists the blocks by category and opens one at a time on the whole screen.

| Folder | Stack |
| --- | --- |
| `react-native/` | Expo SDK 57, React Native 0.86, Expo Router, Reanimated 4, Skia, WebGPU |
| `swift/` | SwiftUI, iOS 17+ |
| `kotlin/` | Jetpack Compose, Android 8+ |

Every block page on nativecook.dev links to its folder here, and its prompts link to the files your coding agent should read.

## Where a block lives

A block has a category and a slug, for example `buttons` and `hold-to-confirm`. The same two names place it in every app:

```
react-native/src/blocks/buttons/hold-to-confirm/index.tsx
swift/NativeCook/Blocks/Buttons/HoldToConfirm/HoldToConfirmDemo.swift
kotlin/app/src/main/java/com/ngocdevv/nativecook/blocks/buttons/holdtoconfirm/HoldToConfirmDemo.kt
```

The file named above is the block's demo: what the gallery opens. Everything else the block needs sits next to it, in the same folder. Helpers shared by several blocks are in `react-native/src/shared`, `swift/NativeCook/Shared` and the Kotlin app's `shared` package.

## Run the apps

**React Native**

```bash
cd react-native
npm install
npx expo start     # then press i or a to open it in Expo Go
```

`npm install` also applies the patches in `react-native/patches` (patch-package). Five blocks need a development build (`npm run ios` or `npm run android`) and do not open in Expo Go: Ember Charge Widget, Aura Wallet Widget, Horizon Upload and Glowknob Console use `expo-content-transition`, and Choose Your Ball draws with `react-native-filament`. `npm run typecheck` and `npm run lint` check the code.

**Swift**: open `swift/NativeCook.xcodeproj` in Xcode and run the `NativeCook` scheme.

**Kotlin**

```bash
cd kotlin
./gradlew :app:installDebug
```

or open the `kotlin` folder in Android Studio. `local.properties` must point at the Android SDK (`sdk.dir=…`), or `ANDROID_HOME` must be set. A block that draws with Filament wants an emulator with a real GPU (`-gpu host`).

Leave an open block with the system's back gesture or button; in the Swift app, swipe in from the left edge. To start an app on one block, without the gallery, pass its slug at launch:

```bash
xcrun simctl launch booted com.ngocdevv.nativecook.free.swift -block hold-to-confirm
adb shell am start -n com.ngocdevv.nativecook.free.kotlin/com.ngocdevv.nativecook.MainActivity --es block hold-to-confirm
xcrun simctl openurl booted exp://127.0.0.1:8081/--/hold-to-confirm   # React Native in Expo Go, with `npx expo start` running
```

| Tooling | Version |
| --- | --- |
| Node | 20.19 or newer |
| Xcode | 16 or newer |
| Gradle / Android Gradle Plugin | 9.8.1 (wrapper) / 9.3.3 |
| Kotlin / Compose BOM | 2.4.21 / 2026.09.00 |
| compileSdk / minSdk | 37 / 26 |

## About this repository

It is updated automatically each time blocks are added or changed, so pull requests here are not merged. To report a problem with a block, write to [support@nativecook.dev](mailto:support@nativecook.dev). If one of the blocks is based on your design and you want the credit corrected or the block removed, see [nativecook.dev/credits](https://nativecook.dev/credits/).
