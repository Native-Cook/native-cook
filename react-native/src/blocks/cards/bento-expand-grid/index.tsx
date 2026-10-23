import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BentoExpandGrid } from './BentoExpandGrid';

export default function BentoExpandGridDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f1ebe0' }}>
      <StatusBar hidden />
      <BentoExpandGrid autoPlay />
    </View>
  );
}
