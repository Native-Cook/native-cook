import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CrayonRubbingCard } from './CrayonRubbingCard';

export default function CrayonRubbingCardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#211D19' }}>
      <StatusBar hidden />
      <CrayonRubbingCard autoPlay />
    </View>
  );
}
