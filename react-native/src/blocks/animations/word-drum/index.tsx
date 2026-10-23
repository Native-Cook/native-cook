import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WordDrum } from './WordDrum';

export default function WordDrumDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <WordDrum autoPlay />
    </View>
  );
}
