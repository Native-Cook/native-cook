import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SoapBubblePop } from './SoapBubblePop';

export default function SoapBubblePopDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#86A9DC' }}>
      <StatusBar hidden />
      <SoapBubblePop autoPlay />
    </View>
  );
}
