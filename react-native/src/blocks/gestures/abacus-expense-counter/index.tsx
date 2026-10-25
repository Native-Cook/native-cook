import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { AbacusExpenseCounter } from './AbacusExpenseCounter';

export default function AbacusExpenseCounterDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16110d' }}>
      <StatusBar hidden />
      <AbacusExpenseCounter autoPlay />
    </View>
  );
}
