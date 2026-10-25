import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TapeMeasurePicker } from './TapeMeasurePicker';

export default function TapeMeasurePickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#efebe4' }}>
      <StatusBar hidden />
      <TapeMeasurePicker autoPlay />
    </View>
  );
}
