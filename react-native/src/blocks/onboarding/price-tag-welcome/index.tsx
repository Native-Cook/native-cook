import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PriceTagWelcome } from './PriceTagWelcome';

export default function PriceTagWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#FCFBFC' }}>
      <StatusBar style="dark" />
      <PriceTagWelcome autoPlay />
    </View>
  );
}
