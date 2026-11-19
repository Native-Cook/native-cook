import { MeshGradientBreathe } from './MeshGradientBreathe';

// The block has the whole screen: a breathing onboarding whose mesh gradient inhales and exhales on a
// 4-4-4-4 box rhythm (one 16 s loop), with a glass orb that guides the breath. The film walks the
// three steps and turns the native length wheel; tap Continue, pick a rhythm or a length to take over
// (the film returns 2 s after the last touch), and Begin to put the orb in the middle.
// Native controls are @expo/ui SwiftUI views on iOS (a development build, as in the demo app);
// Android shows simple React Native stand-ins.
export default function MeshGradientBreatheDemo() {
  return <MeshGradientBreathe autoPlay />;
}
