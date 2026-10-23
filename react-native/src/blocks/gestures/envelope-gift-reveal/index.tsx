import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { EnvelopeGiftReveal } from './EnvelopeGiftReveal';
import { COLORS } from './theme';

export default function EnvelopeGiftRevealDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: COLORS.lime }}>
      <StatusBar style="dark" />
      <EnvelopeGiftReveal autoPlay />
    </View>
  );
}
