import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FlipbookScrub } from './FlipbookScrub';

export default function FlipbookScrubDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#17130f' }}>
      <StatusBar hidden />
      <FlipbookScrub autoPlay />
    </View>
  );
}
