import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CrayonBoxPicker } from './CrayonBoxPicker';

export default function CrayonBoxPickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#A9C9C2' }}>
      <StatusBar hidden />
      <CrayonBoxPicker autoPlay />
    </View>
  );
}
