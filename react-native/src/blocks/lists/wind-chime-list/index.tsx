import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WindChimeList } from './WindChimeList';

export default function WindChimeListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0f1d20' }}>
      <StatusBar hidden />
      <WindChimeList autoPlay />
    </View>
  );
}
