import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { StreamFadeAnswer } from './StreamFadeAnswer';

export default function StreamFadeAnswerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#f6f4ef' }}>
      <StatusBar style="dark" />
      <StreamFadeAnswer autoPlay />
    </View>
  );
}
