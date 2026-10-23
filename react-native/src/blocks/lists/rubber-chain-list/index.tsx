import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RubberChainList } from './RubberChainList';

export default function RubberChainListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0e0d12' }}>
      <StatusBar hidden />
      <RubberChainList autoPlay />
    </View>
  );
}
