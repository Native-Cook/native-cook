import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SandHourglass } from './SandHourglass';

export default function SandHourglassDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ece3d5' }}>
      <StatusBar hidden />
      <SandHourglass autoPlay />
    </View>
  );
}
