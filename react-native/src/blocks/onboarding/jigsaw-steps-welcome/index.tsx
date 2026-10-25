import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { JigsawStepsWelcome } from './JigsawStepsWelcome';

export default function JigsawStepsWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F2E9DC' }}>
      <StatusBar hidden />
      <JigsawStepsWelcome autoPlay />
    </View>
  );
}
