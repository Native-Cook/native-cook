import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CreditCardFlipForm } from './CreditCardFlipForm';

export default function CreditCardFlipFormDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0e12' }}>
      <StatusBar hidden />
      <CreditCardFlipForm autoPlay />
    </View>
  );
}
