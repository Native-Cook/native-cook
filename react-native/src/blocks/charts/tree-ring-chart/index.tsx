import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TreeRingChart } from './TreeRingChart';

export default function TreeRingChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1B130E' }}>
      <StatusBar hidden />
      <TreeRingChart autoPlay />
    </View>
  );
}
