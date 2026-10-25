import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { MatchstickStrikeButton } from './MatchstickStrikeButton';

export default function MatchstickStrikeButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#150E0A' }}>
      <StatusBar hidden />
      <MatchstickStrikeButton autoPlay />
    </View>
  );
}
