import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { HotAirBalloonWelcome } from './HotAirBalloonWelcome';

export default function HotAirBalloonWelcomeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1A1430' }}>
      <StatusBar hidden />
      <HotAirBalloonWelcome autoPlay />
    </View>
  );
}
