import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GooPullRefresh } from './GooPullRefresh';

export default function GooPullRefreshDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f4f2ee' }}>
      <StatusBar hidden />
      <GooPullRefresh autoPlay />
    </View>
  );
}
