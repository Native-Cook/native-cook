import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { TasteProfile } from './TasteProfile';

export default function TasteProfileDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F6F6F6' }}>
      <StatusBar style="dark" />
      <TasteProfile autoPlay />
    </View>
  );
}
