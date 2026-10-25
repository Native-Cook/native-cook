import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TableclothPullTrick } from './TableclothPullTrick';

export default function TableclothPullTrickDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1E1513' }}>
      <StatusBar hidden />
      <TableclothPullTrick autoPlay />
    </View>
  );
}
