import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BarometerNeedleChart } from './BarometerNeedleChart';

export default function BarometerNeedleChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0F1C24' }}>
      <StatusBar hidden />
      <BarometerNeedleChart autoPlay />
    </View>
  );
}
