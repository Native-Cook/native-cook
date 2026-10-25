import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { IronFilingsMagnet } from './IronFilingsMagnet';

export default function IronFilingsMagnetDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#2A1F18' }}>
      <StatusBar hidden />
      <IronFilingsMagnet autoPlay />
    </View>
  );
}
