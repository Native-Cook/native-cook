import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PaintMixerPicker } from './PaintMixerPicker';

export default function PaintMixerPickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EDE6DA' }}>
      <StatusBar hidden />
      <PaintMixerPicker autoPlay />
    </View>
  );
}
