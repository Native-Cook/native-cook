import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ConstellationStepsWelcome } from './ConstellationStepsWelcome';

export default function ConstellationStepsWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#070A1A' }}>
      <StatusBar hidden />
      <ConstellationStepsWelcome autoPlay />
    </View>
  );
}
