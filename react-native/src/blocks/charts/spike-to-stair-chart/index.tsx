import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SpikeToStairChart } from './SpikeToStairChart';

export default function SpikeToStairChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <SpikeToStairChart autoPlay />
    </View>
  );
}
