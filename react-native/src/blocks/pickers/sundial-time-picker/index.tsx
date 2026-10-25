import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SundialTimePicker } from './SundialTimePicker';

export default function SundialTimePickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EEE8DF' }}>
      <StatusBar hidden />
      <SundialTimePicker autoPlay />
    </View>
  );
}
