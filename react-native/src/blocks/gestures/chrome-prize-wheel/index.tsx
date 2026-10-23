// Catalog demo — the self-playing wheel on a black, status-bar-free page.
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ChromePrizeWheel } from './ChromePrizeWheel';

export default function ChromePrizeWheelDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <ChromePrizeWheel autoPlay />
  </View>;
}
