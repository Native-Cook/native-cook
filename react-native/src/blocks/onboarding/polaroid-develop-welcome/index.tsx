import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PolaroidDevelopWelcome } from './PolaroidDevelopWelcome';

export default function PolaroidDevelopWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1c1511' }}>
      <StatusBar hidden />
      <PolaroidDevelopWelcome autoPlay />
    </View>
  );
}
