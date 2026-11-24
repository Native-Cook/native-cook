// Nebula Dust Swirl's dust runs on react-native-webgpu, which needs a development build; in Expo Go
// it falls back to a smaller nebula drawn with Skia.
import { NebulaDustSwirl } from './NebulaDustSwirl';

export default function NebulaDustSwirlDemo() {
  return <NebulaDustSwirl autoPlay />;
}
