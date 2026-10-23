import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ClothFlagMesh } from './ClothFlagMesh';

export default function ClothFlagMeshDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0f1626' }}>
      <StatusBar hidden />
      <ClothFlagMesh autoPlay />
    </View>
  );
}
