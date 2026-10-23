import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CurtainSplitWelcome } from './CurtainSplitWelcome';

export default function CurtainSplitWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1a1916' }}>
      <StatusBar hidden />
      <CurtainSplitWelcome autoPlay />
    </View>
  );
}
