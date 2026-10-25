import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { LaundrySpinProgress } from './LaundrySpinProgress';

export default function LaundrySpinProgressDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ECEDEC' }}>
      <StatusBar hidden />
      <LaundrySpinProgress autoPlay />
    </View>
  );
}
