import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PatternLockInput } from './PatternLockInput';

export default function PatternLockInputDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#05060a' }}>
      <StatusBar hidden />
      <PatternLockInput autoPlay />
    </View>
  );
}
