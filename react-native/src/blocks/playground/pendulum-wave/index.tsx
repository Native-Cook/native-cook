import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PendulumWave } from './PendulumWave';

export default function PendulumWaveDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0D0E10' }}>
      <StatusBar hidden />
      <PendulumWave autoPlay />
    </View>
  );
}
