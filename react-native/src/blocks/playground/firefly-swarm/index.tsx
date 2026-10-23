import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FireflySwarm } from './FireflySwarm';

export default function FireflySwarmDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#070a1d' }}>
      <StatusBar hidden />
      <FireflySwarm autoPlay />
    </View>
  );
}
