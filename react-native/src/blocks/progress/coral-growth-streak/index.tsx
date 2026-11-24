// Coral Growth Streak's reef runs on react-native-webgpu, which needs a development build; in Expo Go
// it falls back to a drawn labyrinth in a Skia shader.
import { CoralGrowthStreak } from './CoralGrowthStreak';

export default function CoralGrowthStreakDemo() {
  return <CoralGrowthStreak autoPlay />;
}
