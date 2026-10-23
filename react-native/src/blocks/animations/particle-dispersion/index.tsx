import { requireOptionalNativeModule } from 'expo';
import { lazy, Suspense } from 'react';
import { Platform } from 'react-native';

import { PlatformMessage } from './components/platform-message';

// VisionCamera's Nitro bindings must only be evaluated in a custom native binary.
// Keeping the camera behind a lazy import also lets Expo Go open every other block.
const CameraScreen = lazy(() => import('./screens/camera').then((screen) => ({ default: screen.CameraScreen })));

export default function ParticleDispersionDemo() {
  const renderer = Platform.OS === 'web' ? null : requireOptionalNativeModule<{ particleRendererVersion?: number }>('SubjectSegmentation');
  if (!renderer?.particleRendererVersion) {
    return <PlatformMessage title="Development build required" body="Particle Dispersion uses a native camera and GPU renderer. Run npx expo run:android or npx expo run:ios, then open this block on a camera device." />;
  }
  return <Suspense fallback={<PlatformMessage title="Opening camera…" body="Preparing the on-device particle effect." busy />}><CameraScreen /></Suspense>;
}
