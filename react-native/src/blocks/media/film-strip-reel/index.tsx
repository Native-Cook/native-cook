import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FilmStripReel } from './FilmStripReel';

export default function FilmStripReelDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0b0a09' }}>
      <StatusBar hidden />
      <FilmStripReel autoPlay />
    </View>
  );
}
