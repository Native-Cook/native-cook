import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ViewmasterReel } from './ViewmasterReel';

export default function ViewmasterReelDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#121C23' }}>
      <StatusBar hidden />
      <ViewmasterReel autoPlay />
    </View>
  );
}
