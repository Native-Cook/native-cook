import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MoodCardShuffle } from './MoodCardShuffle';

export default function MoodCardShuffleDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#2a2820' }}>
      <StatusBar hidden />
      <MoodCardShuffle autoPlay />
    </View>
  );
}
