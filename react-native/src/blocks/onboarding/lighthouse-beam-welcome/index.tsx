import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LighthouseBeamWelcome } from './LighthouseBeamWelcome';

export default function LighthouseBeamWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0A1430' }}>
      <StatusBar hidden />
      <LighthouseBeamWelcome autoPlay />
    </View>
  );
}
