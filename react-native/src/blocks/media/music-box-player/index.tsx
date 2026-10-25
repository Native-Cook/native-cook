import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MusicBoxPlayer } from './MusicBoxPlayer';

export default function MusicBoxPlayerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#161328' }}>
      <StatusBar hidden />
      <MusicBoxPlayer autoPlay />
    </View>
  );
}
