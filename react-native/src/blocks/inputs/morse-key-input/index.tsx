import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MorseKeyInput } from './MorseKeyInput';

export default function MorseKeyInputDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#111D1A' }}>
      <StatusBar hidden />
      <MorseKeyInput autoPlay />
    </View>
  );
}
