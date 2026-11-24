// Ripple Pond Photo's water runs on react-native-webgpu, which needs a development build; in Expo Go
// it falls back to analytic rings in a Skia shader.
import { RipplePondPhoto } from './RipplePondPhoto';

export default function RipplePondPhotoDemo() {
  return <RipplePondPhoto autoPlay />;
}
