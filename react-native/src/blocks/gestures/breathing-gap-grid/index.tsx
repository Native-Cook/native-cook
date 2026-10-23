import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BreathingGapGrid } from './BreathingGapGrid';

export default function BreathingGapGridDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <BreathingGapGrid autoPlay />
    </View>
  );
}
