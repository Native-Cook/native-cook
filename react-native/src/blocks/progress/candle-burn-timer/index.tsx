import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CandleBurnTimer } from './CandleBurnTimer';

export default function CandleBurnTimerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#140e0b' }}>
      <StatusBar hidden />
      <CandleBurnTimer autoPlay />
    </View>
  );
}
