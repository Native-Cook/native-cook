import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { SavedPlacesMap } from './SavedPlacesMap';

export default function SavedPlacesMapDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#DDEEF8' }}>
      <StatusBar style="dark" />
      <SavedPlacesMap autoPlay />
    </View>
  );
}
