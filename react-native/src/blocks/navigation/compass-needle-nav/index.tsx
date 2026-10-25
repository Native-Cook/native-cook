import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CompassNeedleNav } from './CompassNeedleNav';

export default function CompassNeedleNavDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0F1C18' }}>
      <StatusBar hidden />
      <CompassNeedleNav autoPlay />
    </View>
  );
}
