import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RainGaugeChart } from './RainGaugeChart';

export default function RainGaugeChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1D2D32' }}>
      <StatusBar hidden />
      <RainGaugeChart autoPlay />
    </View>
  );
}
