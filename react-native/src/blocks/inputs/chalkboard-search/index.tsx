import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ChalkboardSearch } from './ChalkboardSearch';

export default function ChalkboardSearchDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ece3d3' }}>
      <StatusBar hidden />
      <ChalkboardSearch autoPlay />
    </View>
  );
}
