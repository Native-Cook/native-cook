import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DominoChain } from './DominoChain';

export default function DominoChainDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#07090a' }}>
      <StatusBar hidden />
      <DominoChain autoPlay />
    </View>
  );
}
