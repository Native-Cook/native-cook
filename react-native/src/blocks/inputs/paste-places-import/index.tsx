import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PastePlacesImport } from './PastePlacesImport';

export default function PastePlacesImportDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F6F6F6' }}>
      <StatusBar style="dark" />
      <PastePlacesImport autoPlay />
    </View>
  );
}
