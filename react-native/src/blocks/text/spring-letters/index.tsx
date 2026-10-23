import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SpringLetters } from './SpringLetters';

export default function SpringLettersDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#efe8fb' }}>
      <StatusBar hidden />
      <SpringLetters autoPlay />
    </View>
  );
}
