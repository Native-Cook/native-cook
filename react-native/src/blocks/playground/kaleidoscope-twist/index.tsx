import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { KaleidoscopeTwist } from './KaleidoscopeTwist';

export default function KaleidoscopeTwistDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0b090d' }}>
      <StatusBar hidden />
      <KaleidoscopeTwist autoPlay />
    </View>
  );
}
