import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DiceRollPicker } from './DiceRollPicker';

export default function DiceRollPickerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1A1512' }}>
      <StatusBar hidden />
      <DiceRollPicker autoPlay />
    </View>
  );
}
