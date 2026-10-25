import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TideGaugeChart } from './TideGaugeChart';

export default function TideGaugeChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#081822' }}>
      <StatusBar hidden />
      <TideGaugeChart autoPlay />
    </View>
  );
}
