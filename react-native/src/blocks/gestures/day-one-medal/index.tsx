import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DayOneMedal } from './DayOneMedal';

export default function DayOneMedalDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <DayOneMedal autoPlay />
    </View>
  );
}
