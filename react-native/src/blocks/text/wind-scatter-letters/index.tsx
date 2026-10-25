import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WindScatterLetters } from './WindScatterLetters';

export default function WindScatterLettersDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#25202a' }}>
      <StatusBar hidden />
      <WindScatterLetters autoPlay />
    </View>
  );
}
