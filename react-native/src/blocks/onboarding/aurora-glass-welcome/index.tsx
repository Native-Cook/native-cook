import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { AuroraGlassWelcome } from './AuroraGlassWelcome';

export default function AuroraGlassWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#040815' }}>
      <StatusBar hidden />
      <AuroraGlassWelcome autoPlay />
    </View>
  );
}
