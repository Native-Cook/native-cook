import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BakeryFlapBoard } from './BakeryFlapBoard';

export default function BakeryFlapBoardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1a0f09' }}>
      <StatusBar hidden />
      <BakeryFlapBoard autoPlay />
    </View>
  );
}
