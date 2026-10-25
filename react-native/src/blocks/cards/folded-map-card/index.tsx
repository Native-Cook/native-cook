import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FoldedMapCard } from './FoldedMapCard';

export default function FoldedMapCardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#13221A' }}>
      <StatusBar hidden />
      <FoldedMapCard autoPlay />
    </View>
  );
}
