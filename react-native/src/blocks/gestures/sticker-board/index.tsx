import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { StickerBoard } from './StickerBoard';

export default function StickerBoardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#8a6440' }}>
      <StatusBar hidden />
      <StickerBoard autoPlay />
    </View>
  );
}
