// Murmuration Flock's birds run on react-native-webgpu, which needs a development build; in Expo Go
// it falls back to a smaller flock drawn with Skia.
import { MurmurationFlock } from './MurmurationFlock';

export default function MurmurationFlockDemo() {
  return <MurmurationFlock autoPlay />;
}
